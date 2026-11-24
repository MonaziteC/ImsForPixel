package com.svenuks.imsforpixel

/** Subscription preference read-back is not proof of modem acceptance or VoNR call use. */
internal class UsagePreferenceExperiment(
    private val port: Port, private val journal: Journal, private val identity: String
) {
    interface Port {
        fun supported(): Boolean
        fun read(): Int
        fun write(value: Int)
    }
    interface Journal {
        fun original(identity: String): Int?
        fun save(identity: String, value: Int)
        fun remove(identity: String)
    }
    data class Result(val state: String, val requested: Boolean, val current: Int?, val pending: Boolean)
    var requested = false
        private set
    private var attempted = false
    private var observedData = false
    private var stableOriginalAt: Long? = null
    private var settleUntil = 0L
    private var failed = false
    private var idleState = "off"

    fun request(enabled: Boolean) {
        requested = enabled
        attempted = false
        failed = false
        observedData = false
        idleState = "off"
        stableOriginalAt = null
    }

    fun tick(now: Long, canWrite: () -> Boolean): Result {
        fun result(state: String, current: Int? = null, pending: Boolean = false) =
            Result(state, requested, current, pending)
        try {
            val original = journal.original(identity)
            if (!requested && original == null) return result(idleState, runCatching { port.read() }.getOrNull())
            val current = port.read()
            if (current !in 0..2) return result("unknown_setting", current, original != null)
            if (requested) {
                if (!port.supported()) {
                    requested = false
                    idleState = "unsupported"
                    return result("unsupported", current, original != null)
                }
                if (current == 2) {
                    observedData = true
                    return result(if (original == null) "already_data" else "preference_readback", current)
                }
                // An external change wins; this experiment does not fight carrier/user policy.
                if (observedData) {
                    requested = false
                    journal.remove(identity)
                    idleState = "external_change"
                    return result("external_change", current)
                }
                if (failed) return result("apply_failed", current, original != null)
                if (attempted) {
                    if (now >= settleUntil + 10_000) {
                        requested = false
                        attempted = false
                        failed = true
                        return result("apply_failed", current, original != null)
                    }
                    return result("apply_pending", current, true)
                }
                if (!canWrite()) return result("waiting_idle", current)
                // This durable receipt must precede the system-database mutation.
                if (original == null) journal.save(identity, current)
                attempted = true
                settleUntil = now + 3_000
                port.write(2)
                return result("apply_pending", current, true)
            }
            if (original == null) return result("off", current)
            if (current == original) {
                // Use separate, settled reads; a stale read after an asynchronous write is not undo.
                if (now < settleUntil) return result("restore_pending", current, true)
                val since = stableOriginalAt
                if (since == null) stableOriginalAt = now
                else if (now - since >= 1_000) {
                    journal.remove(identity)
                    idleState = "restored"
                    return result("restored", current)
                }
                return result("restore_pending", current, true)
            }
            stableOriginalAt = null
            if (current != 2 && now >= settleUntil) {
                journal.remove(identity)
                idleState = "external_change"
                return result("external_change", current)
            }
            if (failed) return result("restore_failed", current, true)
            if (!canWrite()) return result("restore_waiting", current, true)
            if (attempted) {
                if (now >= settleUntil + 10_000) {
                    failed = true
                    return result("restore_failed", current, true)
                }
                return result("restore_pending", current, true)
            }
            attempted = true
            settleUntil = now + 3_000
            port.write(original)
            return result("restore_pending", current, true)
        } catch (_: Exception) {
            failed = true
            return result(if (requested) "apply_failed" else "restore_failed", pending = true)
        }
    }
}
