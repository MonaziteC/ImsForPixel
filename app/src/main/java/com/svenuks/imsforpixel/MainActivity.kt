package com.svenuks.imsforpixel

import org.lsposed.hiddenapibypass.HiddenApiBypass
import android.Manifest
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.svenuks.imsforpixel.ui.PixelImsApp
import com.svenuks.imsforpixel.ui.PixelImsTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import okio.Path.Companion.toPath
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import android.os.Build

class MainActivity : ComponentActivity() {

    companion object {
        @JvmStatic
        var pairingPort: Int? = null
        var onAuthStatusChanged: (() -> Unit)? = null
        // Callback: notified when BrokerInstrumentation finishes (activate or restore)
        var onInstrumentDone: ((isActivate: Boolean, success: Boolean) -> Unit)? = null
    }

    fun showImsStatusNotification(isActivate: Boolean) {
        val channelId = "ims_status_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, getString(R.string.notification_channel_ims_status), NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        // Read actual IMS status from files
        val slot0 = try { java.io.File(filesDir, "ims_status_0.txt").readText().trim().toBoolean() } catch (e: Exception) { false }
        val slot1 = try { java.io.File(filesDir, "ims_status_1.txt").readText().trim().toBoolean() } catch (e: Exception) { false }
        val anyRegistered = slot0 || slot1
        val configFailed = (0..1).any { slot ->
            runCatching { java.io.File(filesDir, "config_result_$slot.txt").readText().startsWith("ERROR:") }.getOrDefault(false)
        }
        val title = if (configFailed) getString(R.string.result_failed) else if (isActivate) {
            if (anyRegistered) getString(R.string.notification_title_volte_success) else getString(R.string.notification_title_volte_done)
        } else {
            getString(R.string.notification_title_config_restored)
        }
        val body = if (configFailed) getString(R.string.result_failed_details) else if (isActivate) {
            buildString {
                if (slot0) append(getString(R.string.notification_sim1_registered)) else append(getString(R.string.notification_sim1_not_registered))
                if (slot1) append(getString(R.string.notification_sim2_registered)) else append(getString(R.string.notification_sim2_not_registered))
            }
        } else {
            getString(R.string.notification_config_cleared)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(203, notification)
    }

    private var initialPage by mutableStateOf("status")

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialPage = intent.getStringExtra("pixel_ims_page") ?: "status"
    }

    private var pairingReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            HiddenApiBypass.addHiddenApiExemptions("L")
            Log.d("IMSForSven", "Successfully applied HiddenApiBypass exemptions")
        } catch (e: Exception) {
            Log.e("IMSForSven", "Failed to apply HiddenApiBypass exemptions", e)
        }

        // Initialize KadbCert key store
        try {
            val privateKeyFile = java.io.File(filesDir, "kadb_private_key.pem")
            val store = OkioFilePrivateKeyStore(
                privateKeyFile.absolutePath.toPath()
            )
            KadbCert.configure(
                store = store,
                policy = KadbCertPolicy(),
                additionalPrivateKeysPem = emptyList()
            )
            KadbCert.ensureReady()
            Log.d("IMSForSven", "KadbCert configured successfully")
        } catch (e: Exception) {
            Log.e("IMSForSven", "Failed to configure KadbCert", e)
        }

        // Initialize defaults: enable everything except APN editing and Cross-SIM Calling, both of which are hidden and disabled by default.
        val prefs = getSharedPreferences("volte_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("runtime_only_migration_v4", false)) {
            prefs.edit().remove("persistent_slot_0").remove("persistent_slot_1")
                .putBoolean("runtime_only_migration_v4", true).commit()
            for (slot in 0..1) java.io.File(filesDir, "config_result_$slot.txt").delete()
        }
        prefs.edit().remove("keep_sa_slot_0").remove("keep_sa_slot_1").apply()
        val initialized = prefs.getBoolean("initialized_defaults_v3", false)
        if (!initialized) {
            val editor = prefs.edit()
            for (slot in 0..1) {
                editor.putBoolean("volte_slot_$slot", true)
                editor.putBoolean("vonr_slot_$slot", true)
                editor.putBoolean("vowifi_slot_$slot", true)
                editor.putBoolean("cross_sim_slot_$slot", false) // Hidden and default false
                editor.putBoolean("wfc_roaming_slot_$slot", true)
                editor.putBoolean("ss_ut_slot_$slot", true)
                editor.putBoolean("show_ims_slot_$slot", true)
                editor.putBoolean("allow_apn_slot_$slot", false) // Hidden and default false
            }
            editor.putBoolean("initialized_defaults_v3", true)
            editor.apply()
        }

        // Setup dynamic broadcast receiver for notification pairing code input
        val filter = IntentFilter("com.svenuks.imsforpixel.ACTION_PAIR")
        pairingReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.svenuks.imsforpixel.ACTION_PAIR") {
                    val remoteInput = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
                    if (remoteInput != null) {
                        val code = remoteInput.getCharSequence("extra_pairing_code")?.toString()?.trim()
                        if (!code.isNullOrEmpty()) {
                            handleNotificationPairing(code)
                        }
                    }
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this, pairingReceiver, filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        initialPage = intent?.getStringExtra("pixel_ims_page") ?: "status"
        enableEdgeToEdge()
        setContent {
            PixelImsTheme { PixelImsApp(initialPage) }
        }
    }

    fun requestNotificationPermissionAndShow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            } else {
                showPairingNotification()
            }
        } else {
            showPairingNotification()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showPairingNotification()
            } else {
                Toast.makeText(this, getString(R.string.notification_permission_required), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pairing_channel",
                getString(R.string.notification_channel_pairing),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_pairing_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showPairingNotification() {
        createNotificationChannel()
        
        val replyLabel = getString(R.string.notification_pairing_reply_label)
        val remoteInput = androidx.core.app.RemoteInput.Builder("extra_pairing_code")
            .setLabel(replyLabel)
            .build()
            
        val intent = Intent("com.svenuks.imsforpixel.ACTION_PAIR").apply {
            `package` = packageName
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            flags
        )
        
        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            getString(R.string.notification_pairing_send_action),
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()
            
        val notification = NotificationCompat.Builder(this, "pairing_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.notification_channel_pairing))
            .setContentText(getString(R.string.notification_pairing_prompt))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(action)
            .build()
            
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(202, notification)
    }

    private fun showPairingStatusNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, "pairing_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.notification_channel_pairing))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(202, notification)
    }

    private fun handleNotificationPairing(code: String) {
        val port = pairingPort
        if (port == null) {
            Toast.makeText(this, getString(R.string.toast_pairing_port_missing), Toast.LENGTH_LONG).show()
            showPairingStatusNotification(getString(R.string.notification_pairing_failed_port))
            return
        }
        
        Toast.makeText(this, getString(R.string.toast_pairing_in_progress), Toast.LENGTH_SHORT).show()
        showPairingStatusNotification(getString(R.string.notification_pairing_port_progress, port))

        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        scope.launch {
            val result = try {
                // The fourth argument is the name shown in Android's paired-device list,
                // not a key-storage directory. Key persistence is configured in KadbCert.
                Kadb.pair("127.0.0.1", port, code, name = "Pixel IMS")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
            
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        Toast.makeText(this@MainActivity, getString(R.string.toast_pairing_success), Toast.LENGTH_LONG).show()
                        showPairingStatusNotification(getString(R.string.notification_pairing_success))
                        onAuthStatusChanged?.invoke()
                    },
                    onFailure = { error ->
                        Toast.makeText(this@MainActivity, getString(R.string.toast_pairing_failed, error.message ?: ""), Toast.LENGTH_LONG).show()
                        showPairingStatusNotification(getString(R.string.notification_pairing_failed, error.message ?: ""))
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pairingReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {}
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(202)
    }
}
