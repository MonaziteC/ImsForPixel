package com.svenuks.imsforpixel

import android.app.UiAutomation
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Only the existing retained UiAutomation owner executes these fixed, read-only commands.
 * Permission adoption does not change the app Binder UID or defeat provider redaction.
 * One bounded worker; UI refreshes never run config checks or queue duplicate shell jobs.
 */
internal class DebugSettingsReader(private val context: Context, private val session: String,
    private val automation: UiAutomation) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor { job -> Thread(job, "ims-debug-reader").apply { isDaemon = true } }
    private var inFlight: Future<DebugSettingsState>? = null
    @Volatile private var closed = false
    private val generation = java.util.concurrent.atomic.AtomicLong()
    @Volatile var latest = DebugSettingsState.unknown("authorization_not_ready")
        private set

    @Synchronized private fun schedule(force: Boolean): Future<DebugSettingsState>? {
        if (closed) return null
        inFlight?.takeUnless { it.isDone }?.let { return it }
        if (!force && SystemClock.elapsedRealtime() - latest.elapsed in 0..1_500L && latest.trusted) return null
        return worker.submit<DebugSettingsState> {
            val expectedGeneration = generation.get()
            val began = SystemClock.elapsedRealtime()
            val wall = System.currentTimeMillis()
            val observed = runCatching { DebugSettingsState.fromShell(query(), began, wall) }
                .getOrElse { DebugSettingsState.unknown("shell_query_failed_or_timed_out") }
            val result = if (generation.get() == expectedGeneration) observed
                else DebugSettingsState.unknown("settings_changed_during_read")
            if (!closed) {
                val previous = latest
                latest = result
                runCatching { DebugSettingsStore.write(context, session, result) }
                if (listOf(previous.developer, previous.usb, previous.wireless) !=
                    listOf(result.developer, result.usb, result.wireless)) GuardianRuntime.signal()
            }
            result
        }.also { inFlight = it }
    }
    fun request() { schedule(false) }
    fun settingsChanged() {
        generation.incrementAndGet()
        latest = DebugSettingsState.unknown("settings_changed")
        schedule(true)
    }
    fun readFresh(force: Boolean = false): DebugSettingsState {
        val job = schedule(force)
        return if (job == null) latest else runCatching { job.get(1_800, TimeUnit.MILLISECONDS) }
            .getOrElse { DebugSettingsState.unknown("shell_query_failed_or_timed_out") }
    }

    private fun query(): String {
        // Fixed commands only: no settings put/delete/reset and no user text interpolation.
        if (Build.VERSION.SDK_INT >= 31) {
            val pipes = automation.executeShellCommandRw("/system/bin/sh")
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use { it.write(SCRIPT.toByteArray(Charsets.UTF_8)) }
                return readBounded(pipes[0])
            } finally { pipes.forEach { runCatching { it.close() } } }
        }
        val uid = readBounded(automation.executeShellCommand("/system/bin/id -u")).trim()
        val flags = DebugSettingsState.KEYS.map { key ->
            readBounded(automation.executeShellCommand("/system/bin/settings get global $key")).trim()
        }
        return (listOf("pixel_ims_debug_v1", uid) + flags + "end").joinToString("\n")
    }

    private fun readBounded(descriptor: ParcelFileDescriptor): String =
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val deadline = SystemClock.elapsedRealtime() + 1_200L
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(256)
            while (true) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                check(remaining > 0) { "Read deadline" }
                val poll = StructPollfd().apply { fd = input.fd; events = OsConstants.POLLIN.toShort() }
                check(Os.poll(arrayOf(poll), remaining.toInt()) > 0) { "Read deadline" }
                val count = input.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
                check(out.size() <= 1_024) { "Unexpected shell output" }
            }
            out.toString("UTF-8")
        }

    @Synchronized override fun close() {
        closed = true
        worker.shutdownNow()
    }

    companion object {
        private val SCRIPT = "printf 'pixel_ims_debug_v1\\n'\n/system/bin/id -u\n" +
            DebugSettingsState.KEYS.joinToString("\n", postfix = "\nprintf 'end\\n'\nexit\n") {
                "/system/bin/settings get global $it"
            }
    }
}

/** Separate from the full guardian/radio snapshot: UI debug reads don't wake radio work. */
internal object DebugSettingsStore {
    private fun file(context: Context) = AtomicFile(File(GuardianStore.root(context), "debug-state.json"))
    fun write(context: Context, session: String, value: DebugSettingsState) {
        val row = value.json().put("guardian_id", session).put("boot", GuardianStore.boot(context))
        val file = file(context)
        val out = file.startWrite()
        try { out.write(row.toString().toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun read(context: Context, current: JSONObject?): DebugSettingsState = runCatching {
        val row = JSONObject(file(context).readFully().toString(Charsets.UTF_8))
        check(current != null && GuardianStore.active(context, current))
        check(row.getString("guardian_id") == current.getString("guardian_id"))
        check(row.getInt("boot") == GuardianStore.boot(context))
        DebugSettingsState.fromJson(row)
    }.getOrElse { DebugSettingsState.unknown("authorized_reader_unavailable") }
}
