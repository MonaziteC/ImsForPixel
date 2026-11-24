package com.svenuks.imsforpixel

import android.os.PersistableBundle

/** Session-local recovery with rolling rate limits; no lifetime write or duration cap. */
internal class RetainedConfigRecovery(private val expected: PersistableBundle) {
    companion object {
        const val COOLDOWN_MS = 5_000L
        const val READBACK_TIMEOUT_MS = 30_000L
        const val WRITE_WINDOW_MS = 5 * 60_000L
        const val MAX_WRITES_PER_WINDOW = 6
    }
    data class Result(val status: String, val matched: Boolean, val writes: Int,
                      val verified: Int, val failed: Int, val error: String?)
    private var lastWrite: Long? = null
    private val recentWrites = ArrayDeque<Long>()
    private var retryAt = 0L
    private var consecutiveFailures = 0
    private var pendingSince: Long? = null
    private var writes = 0
    private var verified = 0
    private var failed = 0
    private var denied = false
    private var error: String? = null

    fun tick(now: Long, permission: Boolean, actual: PersistableBundle?,
             canWrite: () -> Boolean, write: (PersistableBundle) -> Unit): Result {
        fun result(status: String, matched: Boolean = false) =
            Result(status, matched, writes, verified, failed, error)
        fun failure() {
            failed++
            consecutiveFailures++
            retryAt = now + minOf(60_000L, 5_000L shl minOf(consecutiveFailures - 1, 4))
        }
        val readable = actual?.containsKey("carrier_nr_availabilities_int_array") == true
        val matched = readable && CarrierOverrideWriter.matches(expected, actual)
        // A matching cached read must not conceal the loss of the permission session.
        if (!permission) return result("permission_unavailable", matched)
        pendingSince?.let { started ->
            if (matched) {
                pendingSince = null
                verified++
                consecutiveFailures = 0
                retryAt = 0
                error = null
                return result("verified", true)
            }
            if (now - started >= READBACK_TIMEOUT_MS) {
                pendingSince = null
                failure()
                error = "Configuration read-back did not match"
                return result("readback_timeout", matched)
            }
            return result("waiting_readback", matched)
        }
        if (!readable) return result("config_unreadable")
        if (matched) return result("matching", true)
        if (denied) return result("write_denied")
        while (recentWrites.isNotEmpty() && now - recentWrites.first() >= WRITE_WINDOW_MS) recentWrites.removeFirst()
        if (recentWrites.size >= MAX_WRITES_PER_WINDOW) return result("rate_limited")
        if (now < retryAt) return result("cooldown")
        if (lastWrite != null && now - lastWrite!! < COOLDOWN_MS) return result("cooldown")
        if (!canWrite()) return result("write_deferred")
        writes++
        recentWrites.addLast(now)
        lastWrite = now
        try {
            write(PersistableBundle(expected))
            pendingSince = now
            error = null
            return result("write_sent")
        } catch (e: Exception) {
            failure()
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
            error = "${cause.javaClass.simpleName}: ${cause.message}"
            denied = cause is SecurityException
            return result(if (denied) "write_denied" else "write_failed")
        }
    }
}
