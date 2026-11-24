package com.svenuks.imsforpixel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.telephony.CellInfoNr
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import android.telephony.ims.ImsMmTelManager
import android.telephony.ims.ImsManager
import android.telephony.ims.ImsReasonInfo
import android.telephony.ims.ImsRegistrationAttributes
import android.telephony.ims.RegistrationManager
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** Read-only cached observations. No scans, speed tests, cell IDs or modem resets. */
@SuppressLint("MissingPermission", "NewApi")
internal class RadioProbe(context: Context, subIds: List<Int>, executor: Executor) : AutoCloseable {
    private val tm = context.getSystemService(TelephonyManager::class.java)
    private val technologies = ConcurrentHashMap<Int, Int>()
    private val cache = mutableMapOf<Int, Pair<Long, JSONObject>>()
    private val registrations = mutableListOf<Pair<ImsMmTelManager, RegistrationManager.RegistrationCallback>>()
    init {
        if (Build.VERSION.SDK_INT >= 31) subIds.forEach { sub ->
            runCatching {
                val manager = context.getSystemService(ImsManager::class.java).getImsMmTelManager(sub)
                val callback = object : RegistrationManager.RegistrationCallback() {
                    override fun onRegistered(attributes: ImsRegistrationAttributes) {
                        val tech = runCatching {
                            attributes.javaClass.getMethod("getRegistrationTechnology").invoke(attributes) as Int
                        }.getOrNull()
                        if (tech != null) technologies[sub] = tech else technologies.remove(sub)
                    }
                    override fun onUnregistered(info: ImsReasonInfo) { technologies.remove(sub) }
                }
                manager.registerImsRegistrationCallback(executor, callback)
                registrations += manager to callback
            }
        }
    }
    fun snapshot(subId: Int, force: Boolean = false): JSONObject {
        val now = SystemClock.elapsedRealtime()
        cache[subId]?.let { if (!force && now - it.first < 10_000) return it.second }
        val phone = tm.createForSubscriptionId(subId)
        val row = JSONObject().put("sampled_at", System.currentTimeMillis())
        fun field(key: String, value: () -> Any?) {
            row.put(key, runCatching(value).getOrNull() ?: JSONObject.NULL)
        }
        field("data_rat") { phone.dataNetworkType }
        field("call_state") { phone.callState }
        field("vonr_enabled") { phone.javaClass.getMethod("isVoNrEnabled").invoke(phone) as Boolean }
        field("ims_technology") { technologies[subId] }
        val signal = runCatching { phone.signalStrength?.getCellSignalStrengths(CellSignalStrengthNr::class.java)?.firstOrNull() }.getOrNull()
        field("nr_rsrp") { signal?.ssRsrp?.takeUnless { it == Int.MAX_VALUE } }
        field("nr_sinr") { signal?.ssSinr?.takeUnless { it == Int.MAX_VALUE } }
        field("serving_nr_bands") {
            if (Build.VERSION.SDK_INT < 30) null else (phone.allCellInfo?.filterIsInstance<CellInfoNr>()
                ?.firstOrNull { it.isRegistered }?.cellIdentity as? CellIdentityNr)?.bands?.joinToString(",")
        }
        field("reported_bandwidths_khz") {
            phone.serviceState?.let { state ->
                (state.javaClass.getMethod("getCellBandwidths").invoke(state) as IntArray).joinToString(",")
            }
        }
        cache[subId] = now to row
        return row
    }
    override fun close() {
        registrations.forEach { (manager, callback) -> runCatching { manager.unregisterImsRegistrationCallback(callback) } }
    }
}
