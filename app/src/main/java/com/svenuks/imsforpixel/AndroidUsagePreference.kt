package com.svenuks.imsforpixel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.IBinder
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import java.security.MessageDigest

@SuppressLint("MissingPermission", "DiscouragedApi")
internal class AndroidUsagePreference(private val context: Context, private val subId: Int) : UsagePreferenceExperiment.Port {
    private val manager = context.getSystemService(SubscriptionManager::class.java)
    private fun info(): SubscriptionInfo = manager.getActiveSubscriptionInfo(subId) ?: error("SIM unavailable")
    private fun token(info: SubscriptionInfo): String {
        val icc = info.iccId
        require(icc.isNotBlank()) { "SIM identity unavailable" }
        return MessageDigest.getInstance("SHA-256").digest(icc.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    val identity = token(info()) // Only an opaque hash is stored, never the ICCID itself.
    private fun checkedInfo() = info().also { check(token(it) == identity) { "SIM profile changed" } }
    override fun supported(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val id = context.resources.getIdentifier("config_supported_cellular_usage_settings", "array", "android")
        return id != 0 && context.resources.getIntArray(id).contains(2)
    }
    override fun read(): Int = checkedInfo().javaClass.getMethod("getUsageSetting").invoke(checkedInfo()) as Int
    override fun write(value: Int) {
        checkedInfo()
        // Unlike a carrier-config key, this call lets us explicitly restore the captured value.
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "isub") as IBinder
        val service = Class.forName("com.android.internal.telephony.ISub\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val method = service.javaClass.getMethod("setUsageSetting", Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, String::class.java).apply { isAccessible = true }
        check((method.invoke(service, value, subId, context.opPackageName) as Int) > 0) {
            "Usage preference was not accepted"
        }
    }

    companion object {
        /** Bounded best-effort recovery before authorization is released. Receipts remain on failure. */
        fun restorePending(context: Context, canWrite: () -> Boolean) {
            val store = UsagePreferenceStore(context)
            if (store.pendingCount() == 0 || Build.VERSION.SDK_INT < 33) return
            val sm = context.getSystemService(SubscriptionManager::class.java)
            val agents = sm.activeSubscriptionInfoList.orEmpty().mapNotNull { sim ->
                runCatching {
                    val port = AndroidUsagePreference(context, sim.subscriptionId)
                    if (store.original(port.identity) == null) null
                    else UsagePreferenceExperiment(port, store, port.identity)
                }.getOrNull()
            }
            val end = SystemClock.elapsedRealtime() + 7_000
            if (agents.isEmpty()) return
            do {
                if (!canWrite()) return
                val results = agents.map { it.tick(SystemClock.elapsedRealtime(), canWrite) }
                if (results.all { !it.pending }) return
                if (store.pendingCount() == 0) return
                Thread.sleep(1_100)
            } while (SystemClock.elapsedRealtime() < end)
        }
    }
}
