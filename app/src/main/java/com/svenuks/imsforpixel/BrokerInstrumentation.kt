package com.svenuks.imsforpixel

import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import android.os.Build
import android.app.UiAutomation
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager

class BrokerInstrumentation : Instrumentation() {
    companion object {
        private const val TAG = "VoLTEBrokerInst"
    }

    private fun findMethod(obj: Any, name: String): Method? {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                val method = clazz.declaredMethods.firstOrNull { it.name == name }
                if (method != null) {
                    method.isAccessible = true
                    return method
                }
            } catch (e: Exception) {
                // Ignore
            }
            clazz = clazz.superclass
        }
        
        for (iface in obj.javaClass.interfaces) {
            try {
                val method = iface.declaredMethods.firstOrNull { it.name == name }
                if (method != null) {
                    method.isAccessible = true
                    return method
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        Log.d(TAG, "BrokerInstrumentation starting...")
        
        val clearArg = arguments?.getString("clear") == "true" || arguments?.getBoolean("clear") == true
        Log.d(TAG, "clearArg: $clearArg")

        Thread {
            var completed = false
            try {
                HiddenApiBypass.addHiddenApiExemptions("L")
                Log.d(TAG, "Successfully applied HiddenApiBypass exemptions in instrumentation")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply HiddenApiBypass exemptions in instrumentation", e)
            }
            
            try {
                var uiAutomation: UiAutomation? = null
                var retries = 5
                while (retries > 0) {
                    try {
                        uiAutomation = getUiAutomation()
                        if (uiAutomation != null) {
                            Log.d(TAG, "Successfully connected UiAutomation")
                            break
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to connect UiAutomation, retries left: ${retries - 1}", e)
                        retries--
                        if (retries > 0) {
                            try { Thread.sleep(500) } catch (ignored: Exception) {}
                        } else {
                            throw e
                        }
                    }
                }

                if (uiAutomation != null) {
                    uiAutomation.adoptShellPermissionIdentity()
                    Log.d(TAG, "Successfully adopted shell permission identity via UiAutomation")
                } else {
                    error("UiAutomation is null, cannot adopt shell permission identity")
                }
                
                try {
                    patchAllSimsAndPoll(arguments)
                    completed = true
                    showImsStatusNotification(!clearArg)
                } finally {
                    if (uiAutomation != null) {
                        try {
                            uiAutomation.dropShellPermissionIdentity()
                            Log.d(TAG, "Released shell permission identity via UiAutomation")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to drop shell permission identity", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run instrumentation patch", e)
            } finally {
                val uiId = arguments?.getString("ui_request_id")
                if (uiId != null && GuardianStore.validId(uiId)) {
                    val failed = !completed || (0..1).any { slot ->
                        runCatching { java.io.File(context.filesDir, "config_result_$slot.txt").readText().startsWith("ERROR:") }.getOrDefault(false)
                    }
                    val state = if (failed) (if (clearArg) "restore_failed" else "failed") else if (clearArg) "restored" else "applied"
                    runCatching { GuardianStore.write(context, org.json.JSONObject().put("guardian_id", uiId)
                        .put("state", state).put("phase", state).put("boot", GuardianStore.boot(context))
                        .put("elapsed", android.os.SystemClock.elapsedRealtime())) }
                }
                finish(0, Bundle().apply { if (uiId != null) putString("ui_action", "status") })
            }
        }.start()
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        Log.d(TAG, "Instrumentation finish() called")
        try {
            super.finish(resultCode, results)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Instrumentation.finish() ignored safely", e)
        }
    }

    // Called only inside the adopted UiAutomation shell identity, which supplies
    // READ_PHONE_STATE independently of this app's manifest permissions.
    @android.annotation.SuppressLint("MissingPermission")
    private fun getActiveSimTargets(subManager: SubscriptionManager): List<Pair<Int, Int>> {
        val targets = mutableListOf<Pair<Int, Int>>() // List of (slotIndex, subId)
        
        // Method 1: Check slot indices 0 and 1 directly via SubscriptionManager
        for (slot in 0..1) {
            try {
                val info = subManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
                if (info != null && info.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    targets.add(Pair(slot, info.subscriptionId))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get active sub info for slot $slot", e)
            }
        }

        // Method 2: Inspect activeSubscriptionInfoList for eSIM profiles that report slotIndex < 0
        try {
            val list = subManager.activeSubscriptionInfoList ?: emptyList()
            for (info in list) {
                val subId = info.subscriptionId
                if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) continue
                
                var slotIndex = info.simSlotIndex
                if (slotIndex < 0) {
                    // eSIM profile with -1 slot index: map to an unused slot index (prefer slot 1 if 0 is taken, or vice-versa)
                    val existingSlots = targets.map { it.first }.toSet()
                    slotIndex = if (!existingSlots.contains(1)) 1 else if (!existingSlots.contains(0)) 0 else 1
                }
                
                if (targets.none { it.first == slotIndex || it.second == subId }) {
                    targets.add(Pair(slotIndex, subId))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect activeSubscriptionInfoList", e)
        }

        Log.d(TAG, "Resolved SIM targets: $targets")
        return targets
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun patchAllSimsAndPoll(arguments: Bundle?) {
        val sharedPrefs = context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE)
        val subManager = context.getSystemService(SubscriptionManager::class.java) ?: return
        val carrierConfigManager = context.getSystemService(CarrierConfigManager::class.java) ?: return
        val telephonyManager = context.getSystemService(TelephonyManager::class.java) ?: return

        val writer = CarrierOverrideWriter { subId, values, persistent ->
            val method = carrierConfigManager.javaClass.getMethod(
                "overrideConfig", Int::class.javaPrimitiveType,
                PersistableBundle::class.java, Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(carrierConfigManager, subId, values, persistent)
        }
        val simTargets = getActiveSimTargets(subManager)
        for (slot in 0..1) java.io.File(context.filesDir, "config_result_$slot.txt").delete()
        Log.d(TAG, "Processing ${simTargets.size} active SIM targets")

        val hasClearArg = arguments?.containsKey("clear") == true
        val clearArg = arguments?.getString("clear") == "true" || arguments?.getBoolean("clear") == true

        // Phase 1: Apply overrides or Clear overrides, and trigger IMS reset
        for ((slotIndex, subId) in simTargets) {
            Log.d(TAG, "Processing SIM slot $slotIndex (SubID $subId)")

            val clear = if (hasClearArg) {
                clearArg
            } else {
                sharedPrefs.getBoolean("clear_slot_$slotIndex", false)
            }

            val appliedFile = java.io.File(context.filesDir, "config_applied_$slotIndex.txt")
            val resultFile = java.io.File(context.filesDir, "config_result_$slotIndex.txt")
            appliedFile.writeText("false")
            if (clear) {
                try {
                    writer.replace(subId, null)
                    resultFile.writeText(context.getString(R.string.result_cleared))
                } catch (e: Exception) {
                    resultFile.writeText("ERROR: " + (e.cause?.message ?: e.message))
                    Log.e(TAG, "Failed to clear runtime config for $subId", e)
                    continue
                }
            } else {
                val bundle = ImsCarrierConfig.build(ImsCarrierConfig.snapshot(sharedPrefs, slotIndex))
                Log.d(TAG, "Applying custom IMS config for slot $slotIndex (subId $subId)")

                try {
                    writer.replace(subId, bundle)
                    // The loader posts updates to its handler. Wait for effective values,
                    // rather than treating reflection returning as proof of application.
                    var verified = false
                    for (attempt in 0 until 20) {
                        val actual = carrierConfigManager.getConfigForSubId(subId)
                        if (CarrierOverrideWriter.matches(bundle, actual)) {
                            verified = true
                            break
                        }
                        Thread.sleep(250)
                    }
                    check(verified) { "Carrier config read-back timed out" }
                    appliedFile.writeText("true")
                    resultFile.writeText(context.getString(
                        R.string.result_temporary
                    ))
                    Log.d(TAG, "Applied and read back config for subId $subId; persistent=false")
                } catch (e: Exception) {
                    resultFile.writeText("ERROR: " + (e.cause?.message ?: e.message))
                    Log.e(TAG, "Failed to apply/read back carrier config for $subId", e)
                    continue
                }
            }

            // Reset IMS registration for subId using sub-bound TelephonyManager instance
            try {
                val subTelephonyManager = telephonyManager.createForSubscriptionId(subId)
                val resetImsMethod = findMethod(subTelephonyManager, "resetIms")
                if (resetImsMethod != null) {
                    if (resetImsMethod.parameterTypes.isNotEmpty()) {
                        resetImsMethod.invoke(subTelephonyManager, slotIndex)
                    } else {
                        resetImsMethod.invoke(subTelephonyManager)
                    }
                    Log.d(TAG, "IMS reset sent for slot $slotIndex subId $subId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset IMS reflectively for subId $subId", e)
            }
        }

        // Phase 2: Poll and update status for up to 30 seconds
        Log.d(TAG, "Entering status polling loop...")
        var pollsLeft = 30
        val totalPolls = 30
        while (pollsLeft > 0) {
            var allRegistered = true
            for ((slotIndex, subId) in simTargets) {
                val isImsRegistered = (ImsRegistrationReader.read(subId) == true)
                Log.d(TAG, "Poll $pollsLeft: SIM slot $slotIndex (subId $subId) IMS Registered: $isImsRegistered")
                
                sharedPrefs.edit().putBoolean("ims_registered_slot_$slotIndex", isImsRegistered).commit()
                try {
                    val statusFile = java.io.File(context.filesDir, "ims_status_$slotIndex.txt")
                    statusFile.writeText(isImsRegistered.toString())
                } catch (e: Exception) {}

                if (!isImsRegistered) {
                    allRegistered = false
                }
            }

            val secondsElapsed = totalPolls - pollsLeft
            if (hasClearArg && clearArg) {
                if (!allRegistered) {
                    Log.d(TAG, "IMS successfully unregistered. Stopping poll.")
                    break
                }
            } else {
                if (allRegistered && secondsElapsed >= 5) {
                    Log.d(TAG, "IMS successfully registered. Stopping poll.")
                    break
                }
            }

            try {
                Thread.sleep(1000)
            } catch (ignored: Exception) {}
            pollsLeft--
        }
    }

    private fun showImsStatusNotification(isActivate: Boolean) {
        val channelId = "ims_status_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, context.getString(R.string.notification_channel_ims_status), NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(ch)
        }
        
        val slot0 = try { java.io.File(context.filesDir, "ims_status_0.txt").readText().trim().toBoolean() } catch (e: Exception) { false }
        val slot1 = try { java.io.File(context.filesDir, "ims_status_1.txt").readText().trim().toBoolean() } catch (e: Exception) { false }
        val anyRegistered = slot0 || slot1
        val configFailed = (0..1).any { slot ->
            runCatching { java.io.File(context.filesDir, "config_result_$slot.txt").readText().startsWith("ERROR:") }.getOrDefault(false)
        }
        val title = if (configFailed) context.getString(R.string.result_failed) else if (isActivate) {
            if (anyRegistered) context.getString(R.string.notification_title_volte_success) else context.getString(R.string.notification_title_volte_done)
        } else {
            context.getString(R.string.notification_title_config_restored)
        }
        val body = if (configFailed) context.getString(R.string.result_failed_details) else if (isActivate) {
            buildString {
                if (slot0) append(context.getString(R.string.notification_sim1_registered)) else append(context.getString(R.string.notification_sim1_not_registered))
                if (slot1) append(context.getString(R.string.notification_sim2_registered)) else append(context.getString(R.string.notification_sim2_not_registered))
            }
        } else {
            context.getString(R.string.notification_config_cleared)
        }
        
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(203, notification)
    }
}
