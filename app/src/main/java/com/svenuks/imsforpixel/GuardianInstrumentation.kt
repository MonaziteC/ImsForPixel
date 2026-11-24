package com.svenuks.imsforpixel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Instrumentation
import android.app.NotificationManager
import android.app.UiAutomation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Keeps the authorized session in :keeper, independent of ordinary UI recreation. */
class GuardianInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val id = arguments?.getString("guardian_id").orEmpty()
        Thread({ runGuardian(id) }, "ims-guardian").start()
    }

    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun runGuardian(id: String) {
        val ctx = targetContext
        var automation: UiAutomation? = null
        var delegated = false
        var listener: CarrierConfigManager.CarrierConfigChangeListener? = null
        var receiver: BroadcastReceiver? = null
        var debugObserver: ContentObserver? = null
        var debugReader: DebugSettingsReader? = null
        val executor = Executors.newSingleThreadExecutor()
        var terminal = "failed"
        val last = JSONObject().put("guardian_id", id)
        val began = SystemClock.elapsedRealtime()
        var phaseBefore = ""
        var reopenPage: String? = null
        var radio: RadioProbe? = null
        var mayRestoreUsage: (() -> Boolean)? = null
        val usageAgents = mutableMapOf<Int, UsagePreferenceExperiment>()
        val usageResults = mutableMapOf<Int, UsagePreferenceExperiment.Result>()
        val changes = java.util.concurrent.ConcurrentHashMap<Int, java.util.concurrent.atomic.AtomicInteger>()
        val cm = ctx.getSystemService(CarrierConfigManager::class.java)
        fun permission() = ctx.checkSelfPermission(Manifest.permission.MODIFY_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        fun publish(state: String, phase: String) {
            last.put("guardian_id", id).put("state", state).put("phase", phase)
                .put("elapsed", SystemClock.elapsedRealtime()).put("started", began)
                .put("boot", GuardianStore.boot(ctx)).put("pid", Process.myPid())
            last.put("usage_pending_count", UsagePreferenceStore(ctx).pendingCount())
            val debug = debugReader?.latest ?: DebugSettingsState.unknown("authorization_not_ready")
            last.put("debug_flags", debug.json()).put("debug_off", debug.offForDisplay(SystemClock.elapsedRealtime()) ?: JSONObject.NULL)
            GuardianStore.write(ctx, last)
            // Always submit the system-required FGS notification. POST_NOTIFICATIONS controls
            // drawer visibility; no zero-ID, invalid-channel, or notification-cancellation trick.
            if (phase != phaseBefore && state == "running") {
                runCatching { ctx.getSystemService(NotificationManager::class.java)
                    .notify(GuardianService.NOTIFICATION_ID, GuardianService.notification(ctx, phase)) }
                phaseBefore = phase
            }
        }
        try {
            require(GuardianStore.validId(id))
            val request = JSONObject(File(GuardianStore.root(ctx), "$id-request.json").readText())
            publish("starting", "starting")
            HiddenApiBypass.addHiddenApiExemptions("L")
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                ?: error("Permission session unavailable")
            automation.adoptShellPermissionIdentity(Manifest.permission.READ_PHONE_STATE,
                "android.permission.READ_PRIVILEGED_PHONE_STATE", Manifest.permission.MODIFY_PHONE_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            delegated = true
            debugReader = runCatching { DebugSettingsReader(ctx, id, automation) }.getOrNull()
            GuardianRuntime.debugReader = debugReader
            debugReader?.request()
            debugObserver = object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    debugReader?.settingsChanged()
                }
            }
            runCatching {
                DebugSettingsState.KEYS.forEach { key ->
                    ctx.contentResolver.registerContentObserver(Settings.Global.getUriFor(key), false, debugObserver!!)
                }
            }
            val sm = ctx.getSystemService(SubscriptionManager::class.java)
            val plan = GuardianPlan.create(request, id, sm.activeSubscriptionInfoList.orEmpty()
                .map { it.simSlotIndex to it.subscriptionId })
            val guards = plan.associate { it.slot to RetainedConfigRecovery(it.expected) }
            val results = mutableMapOf<Int, RetainedConfigRecovery.Result>()
            last.put("slots", JSONArray(plan.map { it.slot + 1 }))
            fun active() = sm.activeSubscriptionInfoList.orEmpty()
            fun present(target: GuardianPlan.Target) = active().any {
                it.subscriptionId == target.subId && it.simSlotIndex == target.slot
            }
            // A call on either SIM pauses every write, avoiding cross-SIM radio disruption.
            fun callsIdle() = active().all { info ->
                ctx.getSystemService(TelephonyManager::class.java)
                    .createForSubscriptionId(info.subscriptionId).callState == TelephonyManager.CALL_STATE_IDLE
            }
            GuardianRuntime.id = id
            mayRestoreUsage = { permission() && callsIdle() }
            AndroidUsagePreference.restorePending(ctx, mayRestoreUsage!!)
            radio = RadioProbe(ctx, plan.map { it.subId }, executor)
            GuardianRuntime.usageCommands.clear()
            GuardianRuntime.refreshProbe.set(true)
            GuardianRuntime.monitorUntil = 0L
            GuardianRuntime.closing = false
            GuardianRuntime.serviceFailure = null
            ContextCompat.startForegroundService(ctx, Intent(ctx, GuardianService::class.java).putExtra("guardian_id", id))
            check(GuardianRuntime.ready.await(10, TimeUnit.SECONDS)) { "Foreground service did not start" }
            GuardianRuntime.serviceFailure?.let { throw it }
            if (Build.VERSION.SDK_INT >= 34) {
                listener = CarrierConfigManager.CarrierConfigChangeListener { slot, subId, _, _ ->
                    if (plan.any { it.slot == slot || it.subId == subId }) {
                        changes.getOrPut(subId) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet()
                        GuardianRuntime.signal()
                    }
                }
                cm.registerCarrierConfigChangeListener(executor, listener!!)
            } else {
                receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val sub = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
                        changes.getOrPut(sub) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet()
                        GuardianRuntime.signal()
                    }
                }
                ContextCompat.registerReceiver(ctx, receiver,
                    IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED), ContextCompat.RECEIVER_EXPORTED)
            }
            runCatching { ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            val lock = ctx.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ImsForPixel:config-check")
            while (!GuardianStore.stopRequested(ctx, id)) {
                var phase = "config_unreadable"
                lock.acquire(10_000)
                try {
                    val info = active()
                    val refreshProbe = GuardianRuntime.refreshProbe.getAndSet(false) || SystemClock.elapsedRealtime() < GuardianRuntime.monitorUntil
                    for (target in plan) {
                        if (present(target) && target.slot !in usageAgents && UsagePreferenceStore(ctx).pendingCount() > 0) {
                            runCatching {
                                val port = AndroidUsagePreference(ctx, target.subId)
                                if (UsagePreferenceStore(ctx).original(port.identity) != null) {
                                    usageAgents[target.slot] = UsagePreferenceExperiment(port, UsagePreferenceStore(ctx), port.identity)
                                }
                            }
                        }
                        val command = GuardianRuntime.usageCommands.remove(target.slot)
                        if (command != null && present(target)) {
                            runCatching {
                                val agent = usageAgents.getOrPut(target.slot) {
                                    val port = AndroidUsagePreference(ctx, target.subId)
                                    UsagePreferenceExperiment(port, UsagePreferenceStore(ctx), port.identity)
                                }
                                agent.request(command)
                            }.onFailure {
                                usageResults[target.slot] = UsagePreferenceExperiment.Result("unavailable", false, null, false)
                            }
                        }
                        usageAgents[target.slot]?.let { agent ->
                            usageResults[target.slot] = agent.tick(SystemClock.elapsedRealtime()) {
                                permission() && callsIdle() && present(target)
                            }
                        }
                    }
                    last.put("sim_status", JSONArray(plan.map { target ->
                        val sim = info.firstOrNull { it.subscriptionId == target.subId && it.simSlotIndex == target.slot }
                        JSONObject().put("slot", target.slot).put("present", sim != null)
                            .put("name", sim?.displayName?.toString().orEmpty())
                            .put("config_matches", results[target.slot]?.matched ?: JSONObject.NULL)
                            .put("ims_registered", if (sim != null) ImsRegistrationReader.read(target.subId) ?: JSONObject.NULL else JSONObject.NULL)
                            .put("radio", if (sim != null) radio?.snapshot(target.subId, refreshProbe) else JSONObject.NULL)
                            .put("radio_history", radio?.history(target.subId) ?: JSONArray())
                            .put("usage_state", usageResults[target.slot]?.state ?: "off")
                            .put("usage_requested", usageResults[target.slot]?.requested ?: false)
                            .put("usage_current", usageResults[target.slot]?.current ?: JSONObject.NULL)
                    }))
                    val granted = permission()
                    last.put("permission", granted)
                    if (!granted) {
                        terminal = "needs_authorization"
                        publish(terminal, terminal)
                        break
                    }
                    if (!callsIdle()) {
                        phase = "call_active"
                    } else {
                        val phases = mutableListOf<String>()
                        for (target in plan) {
                            if (!present(target)) {
                                phases += "target_missing"
                                continue
                            }
                            try {
                                val result = guards.getValue(target.slot).tick(SystemClock.elapsedRealtime(),
                                    granted, cm.getConfigForSubId(target.subId),
                                    { permission() && callsIdle() && present(target) }) { values ->
                                    // Recovery merges only app-owned keys. It never clears all overrides,
                                    // resets IMS, changes user IMS preferences, or requests persistence.
                                    val method = cm.javaClass.getMethod("overrideConfig", Int::class.javaPrimitiveType,
                                        android.os.PersistableBundle::class.java, Boolean::class.javaPrimitiveType)
                                    method.isAccessible = true
                                    method.invoke(cm, target.subId, values, false)
                                }
                                results[target.slot] = result
                                val rows = last.optJSONArray("sim_status")
                                for (index in 0 until (rows?.length() ?: 0)) {
                                    val row = rows!!.getJSONObject(index)
                                    if (row.optInt("slot") == target.slot) row.put("config_matches", result.matched)
                                }
                                phases += result.status
                                File(ctx.filesDir, "config_applied_${target.slot}.txt").writeText(result.matched.toString())
                                if (result.status == "verified") last.put("last_repair", System.currentTimeMillis())
                            } catch (_: SecurityException) {
                                phases += "permission_unavailable"
                            } catch (_: Exception) {
                                phases += "config_unreadable"
                            }
                        }
                        phase = GuardianPlan.phase(phases)
                        last.put("repairs", results.values.sumOf { it.verified })
                            .put("attempts", results.values.sumOf { it.writes })
                            .put("failures", results.values.sumOf { it.failed })
                        if (phase in listOf("write_denied", "permission_unavailable")) {
                            terminal = "needs_authorization"
                            publish(terminal, terminal)
                            break
                        }
                    }
                } catch (_: SecurityException) {
                    terminal = "needs_authorization"
                    publish(terminal, terminal)
                    break
                } catch (_: Exception) { phase = "config_unreadable" }
                finally { if (lock.isHeld) lock.release() }
                publish("running", phase)
                val waitMs = minOf(if (SystemClock.elapsedRealtime() < GuardianRuntime.monitorUntil) 5_000L else 60_000L, when (phase) {
                    "matching" -> if (usageResults.values.any { it.pending }) 2_000L
                        else if (usageResults.values.any { it.requested }) 10_000L else 60_000L
                    "verified", "write_sent", "waiting_readback", "write_deferred" -> 2_000L
                    "cooldown", "write_failed", "readback_timeout" -> 5_000L
                    "rate_limited" -> 30_000L
                    else -> 15_000L
                })
                if (GuardianRuntime.wake.tryAcquire(waitMs, TimeUnit.MILLISECONDS)) {
                    Thread.sleep(1_200)
                    GuardianRuntime.wake.drainPermits()
                }
            }
            val command = GuardianStore.command(ctx, id)
            reopenPage = if (command == "edit") "config" else if (command != null) "status" else null
            if (command == "restore") {
                publish("restoring", "restoring")
                check(permission()) { "Permission session ended" }
                // Wait for a call to finish rather than resetting carrier settings mid-call.
                while (!callsIdle() && GuardianStore.command(ctx, id) == "restore") {
                    publish("restoring", "call_active")
                    GuardianRuntime.wake.tryAcquire(3, TimeUnit.SECONDS)
                }
                if (GuardianStore.command(ctx, id) == "restore") {
                    var confirmed = true
                    for (target in plan.filter { present(it) }) {
                        val before = changes[target.subId]?.get() ?: 0
                        val method = cm.javaClass.getMethod("overrideConfig", Int::class.javaPrimitiveType,
                            android.os.PersistableBundle::class.java, Boolean::class.javaPrimitiveType)
                        method.isAccessible = true
                        method.invoke(cm, target.subId, null, false)
                        val deadline = SystemClock.elapsedRealtime() + 5_000
                        while ((changes[target.subId]?.get() ?: 0) <= before && SystemClock.elapsedRealtime() < deadline) {
                            Thread.sleep(100)
                        }
                        confirmed = confirmed && (changes[target.subId]?.get() ?: 0) > before
                        File(ctx.filesDir, "config_applied_${target.slot}.txt").writeText("false")
                        File(ctx.filesDir, "config_result_${target.slot}.txt").writeText(ctx.getString(R.string.result_cleared))
                    }
                    terminal = if (confirmed) "restored" else "restore_sent"
                } else terminal = "stopped"
            } else if (command != null) terminal = "stopped"
        } catch (e: Exception) {
            android.util.Log.e("ImsGuardian", "Guardian session could not continue", e)
            terminal = if (GuardianStore.command(ctx, id) == "restore") "restore_failed" else "failed"
        } finally {
            // Recover before releasing authority. On process death or unavailable authority,
            // durable receipts remain for the next explicit authorization, never silently erased.
            if (delegated) runCatching { mayRestoreUsage?.let { AndroidUsagePreference.restorePending(ctx, it) } }
            if (GuardianStore.validId(id) && GuardianStore.command(ctx, id) == "restore" && UsagePreferenceStore(ctx).pendingCount() != 0) {
                terminal = "restore_failed"
            }
            radio?.close()
            debugObserver?.let { runCatching { ctx.contentResolver.unregisterContentObserver(it) } }
            debugReader?.close()
            GuardianRuntime.debugReader = null
            if (Build.VERSION.SDK_INT >= 34) listener?.let { runCatching { cm.unregisterCarrierConfigChangeListener(it) } }
            receiver?.let { runCatching { ctx.unregisterReceiver(it) } }
            executor.shutdownNow()
            GuardianRuntime.closing = true
            runCatching { ctx.stopService(Intent(ctx, GuardianService::class.java)) }
            if (delegated) runCatching { automation?.dropShellPermissionIdentity() }
            GuardianRuntime.id = null
            runCatching { publish(terminal, terminal) }
            if (GuardianStore.validId(id)) File(GuardianStore.root(ctx), "$id-request.json").delete()
            finish(0, Bundle().apply { reopenPage?.let { putString("ui_action", it) } })
        }
    }
}
