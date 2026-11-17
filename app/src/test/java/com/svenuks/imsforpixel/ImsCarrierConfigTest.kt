package com.svenuks.imsforpixel

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

internal fun testImsOptions() = JSONObject("""{
    "volte":true,"vonr":true,"vowifi":true,"cross_sim":false,
    "wfc_roaming":true,"ss_ut":true,"show_ims":true,"allow_apn":false,
    "nr":true,"nr_plus":true
}""")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ImsCarrierConfigTest {
    @Test fun fullBundleIncludesCoreImsAndTheExistingNrSettings() {
        val b = ImsCarrierConfig.build(testImsOptions())
        assertEquals(29, b.size())
        assertTrue(b.getBoolean("carrier_volte_available_bool"))
        assertTrue(b.getBoolean("carrier_wfc_ims_available_bool"))
        assertTrue(b.getBoolean("carrier_default_wfc_ims_roaming_enabled_bool"))
        assertTrue(b.getBoolean("carrier_supports_ss_over_ut_bool"))
        assertTrue(b.getBoolean("show_ims_registration_status_bool"))
        assertFalse(b.getBoolean("carrier_cross_sim_ims_available_bool", true))
        assertFalse(b.getBoolean("allow_adding_apns_bool", true))
        assertArrayEquals(intArrayOf(1, 2), b.getIntArray("carrier_nr_availabilities_int_array"))
        assertEquals(110000, b.getInt("nr_advanced_threshold_bandwidth_khz_int"))
        assertFalse(b.containsKey("ims.sa_disable_policy_int"))
        assertFalse(b.containsKey("allowed_network_types"))
    }

    @Test fun explicitDisabledOptionsAreIncludedAndOnlyUnselectedNrTweaksAreOmitted() {
        val options = testImsOptions()
        for (key in options.keys().asSequence().toList()) options.put(key, false)
        val b = ImsCarrierConfig.build(options)
        assertEquals(23, b.size())
        assertFalse(b.getBoolean("carrier_volte_available_bool", true))
        assertTrue(b.getBoolean("hide_enhanced_4g_lte_bool"))
        assertFalse(b.getBoolean("vonr_enabled_bool", true))
        assertFalse(b.getBoolean("carrier_wfc_ims_available_bool", true))
        assertFalse(b.getBoolean("show_ims_registration_status_bool", true))
        assertFalse(b.containsKey("carrier_nr_availabilities_int_array"))
        assertFalse(b.containsKey("ims.sa_disable_policy_int"))
    }

    @Test fun snapshotsArePerSlotAndUnaffectedByLaterPreferenceEdits() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("snapshot-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("vowifi_slot_0", false).putBoolean("vowifi_slot_1", true).commit()
        val frozen = ImsCarrierConfig.snapshot(prefs, 0)
        prefs.edit().putBoolean("vowifi_slot_0", true).commit()
        assertFalse(ImsCarrierConfig.build(frozen).getBoolean("carrier_wfc_ims_available_bool"))
        assertTrue(ImsCarrierConfig.build(ImsCarrierConfig.snapshot(prefs, 1)).getBoolean("carrier_wfc_ims_available_bool"))
        assertThrows(IllegalArgumentException::class.java) { ImsCarrierConfig.snapshot(prefs, 2) }
    }

    @Test fun legacyKeepSaPreferenceIsIgnoredByTheEntireConfigFactory() {
        val b = ImsCarrierConfig.build(testImsOptions().put("keep_sa", true))
        assertFalse(b.containsKey("ims.sa_disable_policy_int"))
    }

    @Test fun incompleteRequestsFailInsteadOfEnablingMissingOptions() {
        val options = testImsOptions().apply { remove("volte") }
        assertThrows(org.json.JSONException::class.java) { ImsCarrierConfig.build(options) }
    }
}
