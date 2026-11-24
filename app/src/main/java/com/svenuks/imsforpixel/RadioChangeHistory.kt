package com.svenuks.imsforpixel

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Bounded changes in sampled framework state, not an over-the-air trace or CA verdict. */
internal class RadioChangeHistory(private val limit: Int = 64) {
    private val rows = ArrayDeque<JSONObject>()
    private var previous: String? = null
    fun record(row: JSONObject) {
        fun value(key: String) = if (row.isNull(key) || !row.has(key)) "?" else row.get(key).toString()
        fun bucket(key: String) = if (row.isNull(key) || !row.has(key)) "?" else Math.floorDiv(row.getInt(key), 5).toString()
        val signature = listOf(value("data_rat"), value("call_state"), value("vonr_enabled"), value("ims_technology"),
            value("serving_cell_token"), value("subscription_usage_preference"), bucket("nr_rsrp"), bucket("nr_sinr"),
            bandwidths(row.optString("reported_bandwidths_khz"))?.joinToString(",") ?: "?").joinToString("|")
        if (signature == previous) return
        previous = signature
        rows.addLast(JSONObject(row.toString()))
        while (rows.size > limit) rows.removeFirst()
    }
    fun json() = JSONArray().also { array -> rows.forEach { array.put(JSONObject(it.toString())) } }

    companion object {
        fun bandwidths(raw: String?): List<Int>? {
            if (raw.isNullOrBlank()) return null
            val values = raw.split(',').map { it.trim().toIntOrNull() ?: return null }
            return values.takeIf { list -> list.all { it in 1..1_000_000 } }?.sorted()
        }
        fun bandwidthLabel(raw: String?): String? = bandwidths(raw)?.let { list ->
            fun mhz(khz: Int) = if (khz % 1000 == 0) (khz / 1000).toString() else (khz / 1000.0).toString()
            list.joinToString(" + ", transform = ::mhz) + " MHz (Σ " + mhz(list.sum()) + " MHz)"
        }
        data class Change(val beforeKHz: Int, val afterKHz: Int, val at: Long)
        fun lastBandwidthChange(history: JSONArray): Change? {
            var previous: List<Int>? = null
            var change: Change? = null
            for (i in 0 until history.length()) {
                val row = history.getJSONObject(i)
                val next = bandwidths(row.optString("reported_bandwidths_khz")) ?: continue
                previous?.let { if (it != next) change = Change(it.sum(), next.sum(), row.optLong("sampled_at")) }
                previous = next
            }
            return change
        }
        /** Session-salted comparison only. Raw NCI/CI/ARFCN and the salt are never exported. */
        fun cellToken(salt: String, type: String, cell: Long, channel: Int): String? {
            if (cell < 0 || cell == Long.MAX_VALUE || (type == "lte" && cell == Int.MAX_VALUE.toLong()) || channel < 0 || channel == Int.MAX_VALUE) return null
            return MessageDigest.getInstance("SHA-256").digest("$salt|$type|$cell|$channel".toByteArray())
                .take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
