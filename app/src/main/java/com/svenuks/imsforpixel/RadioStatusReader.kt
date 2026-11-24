package com.svenuks.imsforpixel

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.telephony.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** Cached framework observations only: no scans, cell identifiers, radio writes or history. */
@SuppressLint("MissingPermission", "NewApi")
internal class RadioStatusReader(context: Context, subIds: List<Int>, executor: Executor) : AutoCloseable {
    private val manager = context.getSystemService(TelephonyManager::class.java)
    private val overrides = ConcurrentHashMap<Int, Int>()
    private val callbacks = mutableListOf<Pair<TelephonyManager, TelephonyCallback>>()
    private val cache = mutableMapOf<Int, Pair<Long, JSONObject>>()
    init {
        if (Build.VERSION.SDK_INT >= 31) subIds.forEach { sub -> runCatching {
            val phone = manager.createForSubscriptionId(sub)
            val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) { overrides[sub] = info.overrideNetworkType }
            }
            phone.registerTelephonyCallback(executor, callback)
            callbacks += phone to callback
        } }
    }
    fun snapshot(subId: Int): JSONObject {
        val now = SystemClock.elapsedRealtime()
        cache[subId]?.let { if (now - it.first in 0..4_999) return it.second }
        val phone = manager.createForSubscriptionId(subId)
        val result = JSONObject().put("sampled_elapsed", now)
        fun field(key: String, get: () -> Any?) { result.put(key, runCatching(get).getOrNull() ?: JSONObject.NULL) }
        val rat = runCatching { phone.dataNetworkType }.getOrDefault(0)
        val state = runCatching { phone.serviceState }.getOrNull()
        val nrState = runCatching { state?.javaClass?.getMethod("getNrState")?.invoke(state) as? Int }.getOrNull()
        result.put("data_rat", rat).put("nr_state", nrState ?: JSONObject.NULL)
            .put("override_type", overrides[subId] ?: JSONObject.NULL)
        val cells = runCatching { phone.allCellInfo?.filter { it.isRegistered } }.getOrNull().orEmpty()
        fun <T : CellInfo> primary(list: List<T>): T? = list.firstOrNull { it.cellConnectionStatus == 1 } ?: list.firstOrNull()
        val nr = primary(cells.filterIsInstance<CellInfoNr>())
        val lte = primary(cells.filterIsInstance<CellInfoLte>())
        val nsa = rat == TelephonyManager.NETWORK_TYPE_LTE && nrState == 3
        val useNr = rat == TelephonyManager.NETWORK_TYPE_NR || nsa
        val useLte = rat == TelephonyManager.NETWORK_TYPE_LTE || rat == 19
        val selected: CellInfo? = if (useNr) nr else if (useLte) lte else null
        val signal = runCatching { phone.signalStrength }.getOrNull()
        val strength = when (selected) { is CellInfoNr -> selected.cellSignalStrength; is CellInfoLte -> selected.cellSignalStrength; else -> null } ?: if (useNr)
            signal?.getCellSignalStrengths(CellSignalStrengthNr::class.java)?.firstOrNull()
            else if (useLte) signal?.getCellSignalStrengths(CellSignalStrengthLte::class.java)?.firstOrNull() else null
        val ages = mutableListOf<Long>()
        fun age(stamp: Long?) { if (stamp != null && stamp in 1..now) ages += now - stamp }
        age(runCatching { if (Build.VERSION.SDK_INT >= 30) selected?.timestampMillis else selected?.timeStamp?.div(1_000_000) }.getOrNull())
        if (nsa) age(runCatching { if (Build.VERSION.SDK_INT >= 30) lte?.timestampMillis else lte?.timeStamp?.div(1_000_000) }.getOrNull())
        if (selected == null) age(runCatching { if (Build.VERSION.SDK_INT >= 30) signal?.timestampMillis else null }.getOrNull())
        field("bands") {
            val parts = mutableListOf<String>()
            if (useNr && Build.VERSION.SDK_INT >= 30) {
                (nr?.cellIdentity as? CellIdentityNr)?.bands?.filter { it in 1..1024 }?.distinct()?.forEach { parts += "n$it" }
            }
            if (useLte && Build.VERSION.SDK_INT >= 30) {
                lte?.cellIdentity?.bands?.filter { it in 1..1024 }?.distinct()?.forEach { parts += "B$it" }
            }
            parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
        fun valid(value: Int?) = value?.takeUnless { it == Int.MAX_VALUE }
        result.put("signal_rat", if (useNr) "NR" else if (useLte) "LTE" else JSONObject.NULL)
        field("rsrp") { valid(when (strength) { is CellSignalStrengthNr -> strength.ssRsrp; is CellSignalStrengthLte -> strength.rsrp; else -> null }) }
        field("sinr") { valid(when (strength) { is CellSignalStrengthNr -> strength.ssSinr; is CellSignalStrengthLte -> strength.rssnr; else -> null }) }
        field("bandwidths_khz") { state?.let { (it.javaClass.getMethod("getCellBandwidths").invoke(it) as IntArray).joinToString(",") } }
        result.put("measurement_age_ms", ages.maxOrNull() ?: JSONObject.NULL)
        cache[subId] = now to result
        return result
    }
    override fun close() {
        if (Build.VERSION.SDK_INT >= 31) callbacks.forEach { (phone, callback) -> runCatching { phone.unregisterTelephonyCallback(callback) } }
    }
}
