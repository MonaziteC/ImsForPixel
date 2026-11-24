package com.svenuks.imsforpixel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore

/** Lives only in :keeper. A restarted plain service cannot recreate instrumentation authority. */
internal object GuardianRuntime {
    @Volatile var id: String? = null
    @Volatile var closing = false
    @Volatile var serviceFailure: Exception? = null
    var ready = CountDownLatch(1)
    val wake = Semaphore(0)
    val usageCommands = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()
    val refreshProbe = java.util.concurrent.atomic.AtomicBoolean(true)
    @Volatile var monitorUntil = 0L
    fun signal() { if (wake.availablePermits() == 0) wake.release() }
}

class GuardianService : Service() {
    companion object {
        const val NOTIFICATION_ID = 215
        fun notification(context: Context, phase: String = "starting"): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("nr_guardian",
                context.getString(R.string.guard_title), NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(context, 215, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getBroadcast(context, 215,
                Intent(context, GuardianStopReceiver::class.java).putExtra("guardian_id", GuardianRuntime.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return Notification.Builder(context, "nr_guardian")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.guard_title))
                .setContentText(context.getString(guardianPhaseLabel(phase)))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(null, context.getString(R.string.guard_stop), stop).build())
                .build()
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (GuardianRuntime.id == null || intent?.getStringExtra("guardian_id") != GuardianRuntime.id) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(NOTIFICATION_ID, notification(this))
        } catch (e: Exception) {
            GuardianRuntime.serviceFailure = e
            stopSelf()
        } finally { GuardianRuntime.ready.countDown() }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        if (!GuardianRuntime.closing) GuardianRuntime.id?.let { GuardianStore.stop(this, it) }
        GuardianRuntime.signal()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

class GuardianStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("guardian_id") ?: return
        if (GuardianStore.validId(id)) {
            val action = intent.getStringExtra("action") ?: "stop"
            if (action == "monitor") {
                if (id != GuardianRuntime.id) return
                val now = android.os.SystemClock.elapsedRealtime()
                val wasExpired = now >= GuardianRuntime.monitorUntil
                val duration = intent.getIntExtra("duration_ms", 15_000).coerceIn(15_000, 120_000)
                GuardianRuntime.monitorUntil = maxOf(GuardianRuntime.monitorUntil, now + duration)
                if (wasExpired) { GuardianRuntime.refreshProbe.set(true); GuardianRuntime.signal() }
                return
            }
            if (action in listOf("refresh", "usage")) {
                if (id != GuardianRuntime.id) return
                GuardianRuntime.refreshProbe.set(true)
                if (action == "usage") {
                    val slot = intent.getIntExtra("slot", -1)
                    if (slot in 0..1) GuardianRuntime.usageCommands[slot] = intent.getBooleanExtra("enabled", false)
                }
                GuardianRuntime.signal()
                return
            }
            if (action !in listOf("stop", "edit", "restore")) return
            GuardianStore.stop(context, id, action)
            GuardianRuntime.signal()
        }
    }
}

internal fun guardianPhaseLabel(phase: String): Int = when (phase) {
    "starting" -> R.string.guard_starting
    "waiting_debug_off", "settling" -> R.string.guard_wait_off
    "matching", "verified" -> R.string.guard_matching
    "write_sent", "waiting_readback" -> R.string.guard_repairing
    "call_active" -> R.string.guard_call
    "target_missing" -> R.string.guard_sim_missing
    "permission_unavailable", "write_denied", "needs_authorization" -> R.string.guard_reauthorize
    "cooldown", "rate_limited", "readback_timeout", "write_failed" -> R.string.guard_retry
    "stopped" -> R.string.guard_stopped
    "restoring" -> R.string.ui_restoring
    "restored" -> R.string.ui_restored
    "restore_sent" -> R.string.ui_restore_sent
    "restore_failed" -> R.string.ui_restore_failed
    "failed" -> R.string.guard_failed
    else -> R.string.guard_unreadable
}
