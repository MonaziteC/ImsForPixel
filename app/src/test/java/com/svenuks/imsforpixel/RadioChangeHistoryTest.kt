package com.svenuks.imsforpixel

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class RadioChangeHistoryTest {
    private fun sample(at: Long, bands: String) = JSONObject().put("sampled_at", at)
        .put("reported_bandwidths_khz", bands).put("nr_rsrp", -97).put("nr_sinr", 11)

    @Test fun reports190To60MHzWithoutTreatingListOrderAsACarrierChange() {
        val history = RadioChangeHistory()
        history.record(sample(1000, "60000,100000,30000"))
        history.record(sample(2000, "30000,60000,100000"))
        assertEquals(1, history.json().length())
        history.record(sample(3000, "60000"))
        assertEquals(2, history.json().length())
        val change = RadioChangeHistory.lastBandwidthChange(history.json())!!
        assertEquals(190000, change.beforeKHz); assertEquals(60000, change.afterKHz); assertEquals(3000L, change.at)
        assertEquals("30 + 60 + 100 MHz (Σ 190 MHz)", RadioChangeHistory.bandwidthLabel("60000,100000,30000"))
    }
    @Test fun unknownAndInvalidBandwidthsDoNotInventZeroOrHugeCapacity() {
        for (value in listOf(null, "", "null", "0", "-1", "2147483647", "60000,bad")) {
            assertNull(RadioChangeHistory.bandwidthLabel(value))
        }
        assertEquals(listOf(100000, 100000), RadioChangeHistory.bandwidths("100000,100000"))
    }
    @Test fun boundedHistoryOwnsCopiesAndRetainsMajorSignalAndModeChanges() {
        val history = RadioChangeHistory(3)
        val original = sample(1000, "60000")
        history.record(original); original.put("reported_bandwidths_khz", "999999")
        assertEquals("60000", history.json().getJSONObject(0).getString("reported_bandwidths_khz"))
        for (i in 1..5) history.record(sample(i * 2000L, "60000").put("nr_sinr", 11 - i * 5))
        assertEquals(3, history.json().length())
        val copy = history.json(); copy.getJSONObject(0).put("nr_sinr", 999)
        assertNotEquals(999, history.json().getJSONObject(0).getInt("nr_sinr"))
    }
    @Test fun cellComparisonTokensAreSessionScopedAndNeverRawCellIds() {
        val a = RadioChangeHistory.cellToken("first-session", "nr", 12345678901, 500000)!!
        assertTrue(a.matches(Regex("[0-9a-f]{16}")))
        assertEquals(a, RadioChangeHistory.cellToken("first-session", "nr", 12345678901, 500000))
        assertNotEquals(a, RadioChangeHistory.cellToken("second-session", "nr", 12345678901, 500000))
        assertNotEquals(a, RadioChangeHistory.cellToken("first-session", "nr", 12345678902, 500000))
        assertNull(RadioChangeHistory.cellToken("session", "nr", Long.MAX_VALUE, 500000))
        assertNull(RadioChangeHistory.cellToken("session", "lte", Int.MAX_VALUE.toLong(), 100))
    }
}
