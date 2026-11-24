package com.svenuks.imsforpixel

import android.os.SystemClock
import android.provider.Settings
import org.json.JSONObject

/** App-UID SettingsProvider zeros are redacted on Android 17, not evidence of OFF. */
internal data class DebugSettingsState(
    val developer: Int?, val usb: Int?, val wireless: Int?,
    val elapsed: Long, val sampledAt: Long,
    val source: String = "untrusted", val readerUid: Int? = null, val reason: String? = null
) {
    val trusted get() = source == SHELL_SOURCE && readerUid?.let { it >= 0 && it % 100_000 in listOf(0, 2000) } == true
    val allKnown get() = developer != null && usb != null && wireless != null
    val allOff get() = developer == 0 && usb == 0 && wireless == 0
    val anyOn get() = developer == 1 || usb == 1 || wireless == 1
    fun fresh(now: Long) = trusted && now - elapsed in 0..6_000L
    fun allowsWrites(now: Long) = allOff && fresh(now) && now - elapsed <= 3_000L
    fun json() = JSONObject().put("developer", developer ?: JSONObject.NULL)
        .put("usb", usb ?: JSONObject.NULL).put("wireless", wireless ?: JSONObject.NULL)
        .put("sampled_elapsed", elapsed).put("sampled_at", sampledAt)
        .put("source", source).put("reader_uid", readerUid ?: JSONObject.NULL)
        .put("reason", reason ?: JSONObject.NULL)

    companion object {
        const val SHELL_SOURCE = "retained_ui_automation_shell"
        val KEYS = listOf(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, Settings.Global.ADB_ENABLED, "adb_wifi_enabled")
        fun parse(value: String?): Int? = when (value?.trim()) { "0" -> 0; "1" -> 1; else -> null }
        fun unknown(reason: String) = DebugSettingsState(null, null, null,
            SystemClock.elapsedRealtime(), System.currentTimeMillis(), reason = reason)

        /** Fixed framing and an actual shell/root UID are required; never interpret an error as 0. */
        fun fromShell(output: String, began: Long, sampledAt: Long): DebugSettingsState {
            val lines = output.trimEnd('\r', '\n').lines().map { it.trim() }
            if (lines.size != 6 || lines.first() != "pixel_ims_debug_v1" || lines.last() != "end")
                return unknown("invalid_shell_output")
            val uid = lines[1].toIntOrNull()
            val sample = DebugSettingsState(parse(lines[2]), parse(lines[3]), parse(lines[4]),
                began, sampledAt, SHELL_SOURCE, uid)
            return if (sample.trusted) sample else unknown("unexpected_reader_uid")
        }

        fun fromJson(row: JSONObject): DebugSettingsState = DebugSettingsState(
            parse(row.opt("developer")?.toString()), parse(row.opt("usb")?.toString()),
            parse(row.opt("wireless")?.toString()), row.optLong("sampled_elapsed", -1),
            row.optLong("sampled_at", 0), row.optString("source"),
            if (row.isNull("reader_uid")) null else row.optInt("reader_uid", -1),
            if (row.isNull("reason")) null else row.optString("reason"))
    }
}
