/*
 * Adapted from ryfineZ/carrier-ims-for-pixel, ImsModifier.buildBundle.
 * Licensed under the Apache License, Version 2.0; see assets/LICENSE-carrier-ims.txt.
 * Modified: isolated NR options for the standalone ImsForPixel instrumentation broker.
 */
package com.svenuks.imsforpixel

import android.os.PersistableBundle

internal object NrCarrierConfig {
    // The writer must clear prior overrides before applying this fresh bundle.
    // Android merges non-null override bundles; omission alone does NOT remove a key.
    fun apply(bundle: PersistableBundle, enableNr: Boolean, enablePlusIcon: Boolean) {
        if (!enableNr) return
        // CarrierConfigManager: NSA = 1, SA = 2. Literal keys also support older SDKs.
        bundle.putIntArray("carrier_nr_availabilities_int_array", intArrayOf(1, 2))
        if (!enablePlusIcon) return
        bundle.putInt("nr_advanced_threshold_bandwidth_khz_int", 110_000)
        bundle.putBoolean("include_lte_for_nr_advanced_threshold_bandwidth_bool", false)
        bundle.putIntArray("additional_nr_advanced_bands_int_array", intArrayOf(1, 3, 8, 28, 41, 78, 79))
        bundle.putString("5g_icon_configuration_string",
            "connected_mmwave:5G_Plus,connected:5G,connected_rrc_idle:5G,not_restricted_rrc_idle:5G,not_restricted_rrc_con:5G")
        bundle.putInt("nr_advanced_capable_pco_id_int", 0)
    }
}
