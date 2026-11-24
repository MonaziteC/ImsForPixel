package com.svenuks.imsforpixel

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class DebugSettingsStateTest {
    private fun shell(dev: String = "0", usb: String = "0", wifi: String = "0", uid: String = "2000", began: Long = 1000) =
        DebugSettingsState.fromShell("pixel_ims_debug_v1\n$uid\n$dev\n$usb\n$wifi\nend\n", began, 12345)

    @Test fun enabledMasterWithoutEitherDebugTransportIsStillEnabled() {
        val sample = shell(dev = "1")
        assertTrue(sample.trusted); assertTrue(sample.anyOn)
        assertEquals(1, sample.developer); assertEquals(0, sample.usb)
        assertFalse(sample.offForDisplay(1000) == true)
    }
    @Test fun eachEnabledFlagIsDisplayedAndExpiredSamplesAreUnknown() {
        for (sample in listOf(shell(dev = "1"), shell(usb = "1"), shell(wifi = "1")))
            assertFalse(sample.offForDisplay(1000) == true)
        val off = shell()
        assertTrue(off.offForDisplay(1000) == true); assertTrue(off.offForDisplay(4000) == true)
        assertFalse(off.offForDisplay(999) == true); assertNull(off.offForDisplay(7001))
        assertTrue(off.fresh(6000)); assertFalse(off.fresh(7001))
    }
    @Test fun anAppUidReturningThreeZerosCannotBeDisplayedAsOff() {
        val redacted = shell(uid = "10345")
        assertFalse(redacted.trusted); assertNull(redacted.developer)
        assertFalse(redacted.offForDisplay(1000) == true)
    }
    @Test fun missingDeniedOrInvalidValuesAreNotOffEvenFromAShellReader() {
        for (value in listOf("null", "", "SecurityException", "2", "-1")) {
            val invalid = shell(dev = value)
            assertNull(invalid.developer); assertFalse(invalid.allKnown)
            assertFalse(invalid.offForDisplay(1000) == true)
        }
    }
    @Test fun incompleteUnexpectedOrNonNumericOutputCannotLookLikeOff() {
        for (output in listOf("0\n0\n0", "pixel_ims_debug_v1\n2000\n0\n0\n0",
            "pixel_ims_debug_v1\n2000\n0\n0\n0\nend\nextra")) {
            assertFalse(DebugSettingsState.fromShell(output, 1000, 0).trusted)
        }
        assertFalse(shell(uid = "-1").trusted)
        assertFalse(shell(uid = "uid=2000(shell)").trusted)
    }
    @Test fun oldUncachedProviderZeroSnapshotsRemainUntrustedAfterUpgrade() {
        val old = JSONObject().put("developer", 0).put("usb", 0).put("wireless", 0)
            .put("sampled_elapsed", 1000).put("source", "settings_provider_uncached")
        val sample = DebugSettingsState.fromJson(old)
        assertTrue(sample.allOff); assertFalse(sample.trusted)
        assertFalse(sample.fresh(1000)); assertFalse(sample.offForDisplay(1000) == true)
    }
    @Test fun slowShellReadKeepsItsStartTimestampInsteadOfClaimingFreshAtCompletion() {
        val sample = shell(began = 1000)
        assertNull(sample.offForDisplay(8000))
        assertEquals(1000L, sample.elapsed)
    }
    @Test fun copiedSnapshotsPreserveActualReaderUidSourceAndSampleTime() {
        val sample = shell(dev = "1")
        val restored = DebugSettingsState.fromJson(JSONObject(sample.json().toString()))
        assertEquals(sample, restored)
        assertEquals(2000, restored.readerUid)
        assertEquals(DebugSettingsState.SHELL_SOURCE, restored.source)
    }
}
