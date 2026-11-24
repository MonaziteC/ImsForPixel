package com.svenuks.imsforpixel

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONObject

/** A fresh provider observation, never an old keeper-file boolean or a cached getInt default. */
internal data class DebugSettingsState(
    val developer: Int?, val usb: Int?, val wireless: Int?,
    val elapsed: Long, val sampledAt: Long
) {
    val allKnown get() = developer != null && usb != null && wireless != null
    val allOff get() = developer == 0 && usb == 0 && wireless == 0
    val anyOn get() = developer == 1 || usb == 1 || wireless == 1
    fun fresh(now: Long) = now - elapsed in 0..3_000L
    fun allowsWrites(now: Long) = allOff && fresh(now)
    fun json() = JSONObject().put("developer", developer ?: JSONObject.NULL)
        .put("usb", usb ?: JSONObject.NULL).put("wireless", wireless ?: JSONObject.NULL)
        .put("sampled_elapsed", elapsed).put("sampled_at", sampledAt)
        .put("source", "settings_provider_uncached")

    companion object {
        val KEYS = listOf(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, Settings.Global.ADB_ENABLED, "adb_wifi_enabled")
        fun parse(value: String?): Int? = when (value?.trim()) { "0" -> 0; "1" -> 1; else -> null }
        fun read(context: Context): DebugSettingsState {
            val began = SystemClock.elapsedRealtime()
            val sampledAt = System.currentTimeMillis()
            // Query the named URI directly to bypass Settings.NameValueCache. Missing, denied,
            // malformed and failed reads remain unknown, and cannot authorize a recovery write.
            fun flag(key: String): Int? {
                val queried = runCatching { context.contentResolver.query(Settings.Global.getUriFor(key), arrayOf("value"),
                    null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) parse(cursor.getString(0)) else null
                } }.getOrNull()
                if (queried != null) return queried
                // Direct SettingsProvider GET, bypassing NameValueCache and default-zero fallbacks.
                return runCatching { parse(context.contentResolver.call(Settings.Global.CONTENT_URI,
                    "GET_global", key, null)?.getString("value")) }.getOrNull()
            }
            val values = KEYS.map(::flag)
            return DebugSettingsState(values[0], values[1], values[2], began, sampledAt)
        }
    }
}
