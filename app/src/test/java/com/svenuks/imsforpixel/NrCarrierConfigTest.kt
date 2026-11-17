package com.svenuks.imsforpixel

import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class NrCarrierConfigTest {
    private fun config(nr: Boolean, plus: Boolean) = PersistableBundle().also {
        NrCarrierConfig.apply(it, nr, plus)
    }

    @Test fun disabledNrDoesNotOverrideCarrierDefaultsEvenWithSavedIconPreference() {
        assertTrue(config(false, false).isEmpty)
        assertTrue(config(false, true).isEmpty)
    }

    @Test fun nrOnlyEnablesBothModesWithoutChangingIconPolicy() {
        val values = config(true, false)
        assertEquals(1, values.size())
        assertArrayEquals(intArrayOf(CarrierConfigManager.CARRIER_NR_AVAILABILITY_NSA,
            CarrierConfigManager.CARRIER_NR_AVAILABILITY_SA),
            values.getIntArray(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY))
    }

    @Test fun plusMatchesDonorThresholdBandsAndIconMapping() {
        val values = config(true, true)
        assertEquals(6, values.size())
        assertEquals(110_000, values.getInt("nr_advanced_threshold_bandwidth_khz_int"))
        assertFalse(values.getBoolean("include_lte_for_nr_advanced_threshold_bandwidth_bool"))
        assertArrayEquals(intArrayOf(1, 3, 8, 28, 41, 78, 79),
            values.getIntArray("additional_nr_advanced_bands_int_array"))
        assertEquals(0, values.getInt("nr_advanced_capable_pco_id_int"))
        assertEquals("connected_mmwave:5G_Plus,connected:5G,connected_rrc_idle:5G,not_restricted_rrc_idle:5G,not_restricted_rrc_con:5G",
            values.getString("5g_icon_configuration_string"))
    }

    @Test fun freshBundlesAreIndependentAndOmitDisabledOptions() {
        val simOne = config(true, true)
        val simTwo = config(false, false)
        assertEquals(6, simOne.size())
        assertTrue(simTwo.isEmpty)
        assertEquals(1, config(true, false).size())
        assertTrue(config(false, false).isEmpty)
    }
}
