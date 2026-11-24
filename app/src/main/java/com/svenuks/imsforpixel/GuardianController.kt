package com.svenuks.imsforpixel

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.flyfishxu.kadb.Kadb
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal object GuardianController {
    fun selectUsage(context: Context, slot: Int, value: Int) {
        require(slot in 0..1 && value in 0..2)
        val status = GuardianStore.latest(context) ?: error("Guardian is not running")
        check(GuardianStore.active(context, status) && status.optString("state") == "running")
        check(context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE).edit()
            .putInt("usage_mode_slot_$slot", value).commit())
        context.sendBroadcast(android.content.Intent(context, GuardianStopReceiver::class.java)
            .putExtra("guardian_id", status.getString("guardian_id")).putExtra("action", "usage")
            .putExtra("slot", slot).putExtra("usage_mode", value))
    }
    fun enabled(context: Context) = context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE)
        .getBoolean("guardian_enabled", true)

    /** Call on an IO thread. Normal activation finishes before the retained keeper is launched. */
    fun activateAndStart(context: Context, port: Int) {
        require(port in 1..65535)
        check(!GuardianStore.active(context)) { "Guardian already active" }
        val prefs = context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("clear_slot_0", false).putBoolean("clear_slot_1", false).commit()
        val id = UUID.randomUUID().toString()
        val request = JSONObject().put("guardian_id", id).put("version", 2)
            .put("slot_0", ImsCarrierConfig.snapshot(prefs, 0))
            .put("slot_1", ImsCarrierConfig.snapshot(prefs, 1))
        Kadb.create("127.0.0.1", port, 10_000, 10_000).use { adb ->
            val available = adb.shell("command -v nohup && command -v setsid")
            check(available.exitCode == 0) { "Session launcher unavailable" }
            // Android 13+ permits an FGS with POST_NOTIFICATIONS denied. Keep the mandatory
            // service notification internally, but suppress this app's notification drawer entry.
            // This changes only our own notification permission, at the user's explicit request.
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                val userId = Process.myUid() / 100_000 // Android's per-user UID range.
                runCatching { adb.shell("pm revoke --user $userId com.svenuks.imsforpixel android.permission.POST_NOTIFICATIONS") }
            }
            context.getSystemService(NotificationManager::class.java).cancelAll()
            File(GuardianStore.root(context), "$id-request.json").writeText(request.toString())
            GuardianStore.write(context, JSONObject().put("guardian_id", id)
                .put("state", "starting").put("phase", "starting")
                .put("boot", GuardianStore.boot(context)).put("elapsed", SystemClock.elapsedRealtime()))
            val reply = adb.shell(GuardianStore.launcher(id))
            check(reply.exitCode == 0) { "Guardian launch failed" }
        }
    }
    fun applyOnce(context: Context, port: Int, restore: Boolean) {
        require(port in 1..65535)
        check(!GuardianStore.active(context))
        val id = UUID.randomUUID().toString()
        context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("clear_slot_0", restore).putBoolean("clear_slot_1", restore).commit()
        Kadb.create("127.0.0.1", port, 10_000, 10_000).use { adb ->
            GuardianStore.write(context, JSONObject().put("guardian_id", id)
                .put("state", "starting").put("phase", if (restore) "restoring" else "starting")
                .put("boot", GuardianStore.boot(context)).put("elapsed", SystemClock.elapsedRealtime()))
            check(adb.shell(GuardianStore.brokerLauncher(id, restore)).exitCode == 0)
        }
    }
    fun request(context: Context, action: String) {
        val status = GuardianStore.latest(context) ?: error("Guardian is not running")
        val id = status.getString("guardian_id")
        check(GuardianStore.active(context, status) && status.optString("state") == "running")
        GuardianStore.stop(context, id, action)
        context.sendBroadcast(android.content.Intent(context, GuardianStopReceiver::class.java)
            .putExtra("guardian_id", id).putExtra("action", action))
    }

}
