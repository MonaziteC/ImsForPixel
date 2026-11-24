package com.svenuks.imsforpixel.ui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class RadioStatusFormatTest {
    @Test fun bandwidthUsesMHzAndRetainsEqualWidthCarriers() {
        val widths = RadioStatusFormat.bandwidths("60000,100000,30000")!!
        assertEquals("190", RadioStatusFormat.mhz(widths.sum()))
        assertEquals(listOf(60000, 60000), RadioStatusFormat.bandwidths("60000,60000"))
        assertEquals("1.4", RadioStatusFormat.mhz(1400))
    }
    @Test fun unknownInvalidAndMissingReportsCannotLookLikeZeroCapacityOrSignal() {
        for (raw in listOf(null, "", "null", "0", "-1", "2147483647", "60000,bad"))
            assertNull(RadioStatusFormat.bandwidths(raw))
        assertEquals("—", RadioStatusFormat.number(null, "rsrp", -160..-20))
        assertEquals("—", RadioStatusFormat.number(JSONObject().put("rsrp", Int.MAX_VALUE), "rsrp", -160..-20))
        assertEquals("—", RadioStatusFormat.number(JSONObject().put("rsrp", 0), "rsrp", -160..-20))
        assertEquals("-95", RadioStatusFormat.number(JSONObject().put("rsrp", -95), "rsrp", -160..-20))
    }
    @Test fun advertised5gDoesNotBecomeAnActiveNsaConnection() {
        val row = JSONObject().put("data_rat", 13).put("override_type", 3).put("nr_state", 2)
        assertNull(RadioStatusFormat.network(row))
        row.put("nr_state", 3)
        assertEquals("5G NSA", RadioStatusFormat.network(row))
        row.put("data_rat", 20)
        assertEquals("5G SA", RadioStatusFormat.network(row))
    }
}
