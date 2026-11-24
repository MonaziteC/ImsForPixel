package com.svenuks.imsforpixel

/** Subscription preference read-back is not proof of modem acceptance or VoNR call use. */
internal class UsagePreferenceExperiment(
    private val port: Port, private val journal: Journal, private val identity: String
) {
    interface Port {
        fun supported(value: Int): Boolean
        fun read(): Int
        fun write(value: Int)
    }
    interface Journal {
        fun original(identity: String): Int?
        fun save(identity: String, value: Int, target: Int)
        fun owns(identity: String, value: Int): Boolean
        fun remove(identity: String)
    }
    data class Result(val state: String, val requested: Int?, val current: Int?, val pending: Boolean)
    var requested: Int? = null
        private set
    private var attempted = false
    private var observedTarget = false
    private var stableOriginalAt: Long? = null
    private var settleUntil = 0L
    private var failed = false
    private var idleState = "off"

    fun request(value: Int?) {
        require(value == null || value in 0..2)
        requested = value
        attempted = false
        failed = false
        observedTarget = false
        idleState = "off"
        stableOriginalAt = null
    }

    fun tick(now: Long, canWrite: () -> Boolean): Result {
        fun result(state: String, current: Int? = null, pending: Boolean = false) =
            Result(state, requested, current, pending)
        try {
            val original = journal.original(identity)
            val target = requested
            if (target == null && original == null) return result(idleState, runCatching { port.read() }.getOrNull())
            val current = port.read()
            if (current !in 0..2) return result("unknown_setting", current, original != null)
            if (target != null) {
                if (!port.supported(target)) {
                    requested = null
                    idleState = "unsupported"
                    return result("unsupported", current, original != null)
                }
                if (current == target) {
                    observedTarget = true
                    // A new choice that already matches an external setting needs no mutation.
                    if (original != null && current != original && !journal.owns(identity, current)) journal.remove(identity)
                    return result(if (journal.original(identity) == null) "already_selected" else "preference_readback", current)
                }
                // An external change wins; this experiment does not fight carrier/user policy.
                if (observedTarget) {
                    requested = null
                    journal.remove(identity)
                    idleState = "external_change"
                    return result("external_change", current)
                }
                if (failed) return result("apply_failed", current, original != null)
                if (attempted) {
                    if (now >= settleUntil + 10_000) {
                        requested = null
                        attempted = false
                        failed = true
                        return result("apply_failed", current, original != null)
                    }
                    return result("apply_pending", current, true)
                }
                if (!canWrite()) return result("waiting_idle", current)
                // This durable receipt must precede the system-database mutation.
                journal.save(identity, original ?: current, target)
                attempted = true
                settleUntil = now + 3_000
                port.write(target)
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
            if (!journal.owns(identity, current) && now >= settleUntil) {
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
            return result(if (requested != null) "apply_failed" else "restore_failed", pending = true)
        }
    }
}
