package com.svenuks.imsforpixel

import android.content.SharedPreferences
import android.os.PersistableBundle
import org.json.JSONObject

/** One definition of all app-owned CarrierConfig values, shared by activation and recovery. */
internal object ImsCarrierConfig {
    private val defaults = linkedMapOf(
        "volte" to true, "vonr" to true, "vowifi" to true,
        "cross_sim" to false, "wfc_roaming" to true, "ss_ut" to true,
        "show_ims" to true, "allow_apn" to false,
        "nr" to false, "nr_plus" to false
    )
    fun snapshot(prefs: SharedPreferences, slot: Int) = JSONObject().apply {
        require(slot in 0..1)
        for ((key, value) in defaults) put(key, prefs.getBoolean("${key}_slot_$slot", value))
    }
    fun build(options: JSONObject) = PersistableBundle().apply {
        // Require every option; a corrupt frozen request must not silently change user intent.
        for (key in defaults.keys) options.getBoolean(key)
        val volte = options.getBoolean("volte")
        putBoolean("carrier_volte_available_bool", volte)
        putBoolean("carrier_volte_roaming_available_bool", volte)
        putBoolean("carrier_default_volte_roaming_enabled_bool", volte)
        putBoolean("enhanced_4g_lte_on_by_default_bool", volte)
        putBoolean("hide_enhanced_4g_lte_bool", !volte)
        putBoolean("editable_enhanced_4g_lte_bool", volte)
        putBoolean("carrier_volte_provisioned_bool", volte)
        putBoolean("carrier_volte_provisioning_required_bool", false)
        putInt("lte_plus_threshold_bandwidth_khz_int", 0)
        NrCarrierConfig.apply(this, options.getBoolean("nr"), options.getBoolean("nr_plus"))
        val vonr = options.getBoolean("vonr")
        putBoolean("vonr_enabled_bool", vonr)
        putBoolean("vonr_setting_visibility_bool", vonr)
        val wifi = options.getBoolean("vowifi")
        putBoolean("carrier_wfc_ims_available_bool", wifi)
        putBoolean("carrier_wfc_ims_roaming_available_bool", wifi)
        putBoolean("carrier_default_wfc_ims_enabled_bool", wifi)
        putBoolean("carrier_wfc_ims_provisioned_bool", wifi)
        putBoolean("editable_wfc_mode_bool", wifi)
        putBoolean("editable_wfc_roaming_mode_bool", wifi)
        putBoolean("carrier_default_wfc_ims_roaming_enabled_bool", options.getBoolean("wfc_roaming"))
        val cross = options.getBoolean("cross_sim")
        putBoolean("carrier_cross_sim_ims_available_bool", cross)
        putBoolean("enable_cross_sim_calling_on_opportunistic_data_bool", cross)
        putBoolean("carrier_supports_ss_over_ut_bool", options.getBoolean("ss_ut"))
        putBoolean("show_ims_registration_status_bool", options.getBoolean("show_ims"))
        putBoolean("allow_adding_apns_bool", options.getBoolean("allow_apn"))
    }
}
