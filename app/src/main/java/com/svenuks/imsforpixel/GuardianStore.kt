package com.svenuks.imsforpixel

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** Small cross-process state, not an unbounded diagnostic history. Only the keeper writes live state. */
internal object GuardianStore {
    const val PROCESS = "com.svenuks.imsforpixel:keeper"
    const val COMPONENT = "com.svenuks.imsforpixel/com.svenuks.imsforpixel.GuardianInstrumentation"
    fun root(context: Context) = File(context.filesDir, "guardian").apply { mkdirs() }
    fun validId(id: String) = id.matches(Regex("[a-zA-Z0-9-]{1,64}"))
    fun launcher(id: String): String {
        require(validId(id))
        // Wait for ordinary activation (including its permission release) before the keeper.
        // Every interpolated value is fixed or validated above; no preference text enters the shell.
        val activation = "am instrument -w -e clear false com.svenuks.imsforpixel/com.svenuks.imsforpixel.BrokerInstrumentation; "
        return detached(activation + captureAndReopen("am instrument -w -e guardian_id $id $COMPONENT"))
    }
    fun brokerLauncher(id: String, clear: Boolean): String {
        require(validId(id))
        return detached(captureAndReopen("am instrument -w -e clear $clear -e ui_request_id $id " +
            "com.svenuks.imsforpixel/com.svenuks.imsforpixel.BrokerInstrumentation"))
    }
    private fun detached(script: String) = "nohup setsid sh -c '$script' </dev/null >/dev/null 2>&1 &"
    private fun captureAndReopen(command: String) =
        "ims_result=\"\$($command)\"; case \"\$ims_result\" in " +
            "*\"INSTRUMENTATION_RESULT: ui_action=config\"*) am start -n com.svenuks.imsforpixel/.MainActivity --es pixel_ims_page config ;; " +
            "*\"INSTRUMENTATION_RESULT: ui_action=status\"*) am start -n com.svenuks.imsforpixel/.MainActivity --es pixel_ims_page status ;; esac"
    fun latest(context: Context): JSONObject? = runCatching {
        JSONObject(AtomicFile(File(root(context), "state.json")).readFully().toString(Charsets.UTF_8))
    }.getOrNull()
    fun write(context: Context, value: JSONObject) {
        val file = AtomicFile(File(root(context), "state.json"))
        val out = file.startWrite()
        try {
            out.write(value.toString().toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun boot(context: Context) = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun keeperAlive(context: Context, status: JSONObject?): Boolean = runCatching {
        context.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty().any {
            it.processName == PROCESS && it.pid == status?.optInt("pid", -1)
        }
    }.getOrDefault(false)
    fun active(context: Context, status: JSONObject? = latest(context)): Boolean {
        if (status == null || status.optInt("boot", -2) != boot(context)) return false
        val state = status.optString("state")
        if (state in listOf("running", "restoring")) return keeperAlive(context, status)
        // An initial launcher marker is only provisional, not evidence of an authorized session.
        return state == "starting" && SystemClock.elapsedRealtime() - status.optLong("elapsed", -1) in 0..120_000L
    }
    fun stop(context: Context, id: String, action: String = "stop") {
        require(validId(id))
        require(action in listOf("stop", "edit", "restore"))
        val file = AtomicFile(File(root(context), "stop-$id"))
        val out = file.startWrite()
        try { out.write(action.toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun stopRequested(context: Context, id: String) = File(root(context), "stop-$id").exists()
    fun command(context: Context, id: String): String? = runCatching {
        require(validId(id))
        AtomicFile(File(root(context), "stop-$id")).readFully().toString(Charsets.UTF_8)
            .takeIf { it in listOf("stop", "edit", "restore") }
    }.getOrNull()
}
