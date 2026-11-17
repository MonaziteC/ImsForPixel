package com.svenuks.imsforpixel

import android.os.PersistableBundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class RetainedConfigRecoveryTest {
    private fun defaults() = PersistableBundle().apply {
        putIntArray("carrier_nr_availabilities_int_array", intArrayOf(1, 2))
        putBoolean("vonr_enabled_bool", false)
        putInt("external_unrelated_setting", 42)
    }
    private val expected get() = ImsCarrierConfig.build(testImsOptions())

    @Test fun writesOnlyAfterDebugOffStabilisesAndVerifiesOnANewRead() {
        val guard = RetainedConfigRecovery(expected)
        val actual = defaults()
        var calls = 0
        fun tick(now: Long, off: Boolean) = guard.tick(now, off, true, actual, { off }) {
            calls++
            actual.putAll(it)
        }
        assertEquals("waiting_debug_off", tick(0, false).status)
        assertEquals("settling", tick(5000, true).status)
        val sent = tick(10000, true)
        assertEquals("write_sent", sent.status)
        assertEquals(0, sent.verified)
        assertEquals("verified", tick(15000, true).status)
        assertEquals(1, calls)
        repeat(20) { assertTrue(tick(20000L + it * 5000, true).matched) }
        assertEquals(1, calls)
        assertEquals(42, actual.getInt("external_unrelated_setting"))
    }

    @Test fun unknownOrOnFlagsAndAChangeImmediatelyBeforeDispatchPreventWriting() {
        assertTrue(RetainedConfigRecovery.allOff(0, 0, 0))
        for (flag in listOf(-1, 1)) {
            assertFalse(RetainedConfigRecovery.allOff(flag, 0, 0))
            assertFalse(RetainedConfigRecovery.allOff(0, flag, 0))
            assertFalse(RetainedConfigRecovery.allOff(0, 0, flag))
        }
        val guard = RetainedConfigRecovery(expected)
        guard.tick(0, true, true, defaults(), { true }) { fail("too soon") }
        val r = guard.tick(5000, true, true, defaults(), { false }) { fail("debugging changed") }
        assertEquals("waiting_debug_off", r.status)
        assertEquals(0, r.writes)
    }

    @Test fun noPermissionUnreadableConfigAndAlreadyMatchingValuesDoNotWrite() {
        val guard = RetainedConfigRecovery(expected)
        guard.tick(0, true, false, defaults(), { true }) { fail() }
        assertEquals("permission_unavailable", guard.tick(5000, true, false, defaults(), { true }) { fail() }.status)
        assertEquals("config_unreadable", guard.tick(10000, true, true, null, { true }) { fail() }.status)
        val matched = guard.tick(15000, true, true, expected, { true }) { fail() }
        assertEquals("matching", matched.status)
        assertEquals(0, matched.verified)
        assertEquals(0, matched.writes)
    }

    @Test fun renewedLossIsRepairedWithCooldownAndNoUnnecessaryWrites() {
        val guard = RetainedConfigRecovery(expected)
        var actual = defaults()
        var calls = 0
        fun tick(t: Long) = guard.tick(t, true, true, actual, { true }) { calls++; actual.putAll(it) }
        tick(0); tick(5000); tick(6000)
        actual = defaults()
        assertEquals("cooldown", tick(7000).status)
        assertEquals("write_sent", tick(10000).status)
        assertEquals("verified", tick(12000).status)
        assertEquals(2, calls)
    }

    @Test fun nonEffectiveWritesAreRetriedBeyondTheOldLifetimeLimitWithRollingRateControl() {
        val guard = RetainedConfigRecovery(expected)
        val calls = mutableListOf<Long>()
        var last: RetainedConfigRecovery.Result? = null
        for (t in 0L..1_200_000L step 1000) {
            last = guard.tick(t, true, true, defaults(), { true }) { calls += t }
        }
        assertTrue(calls.size > 6)
        for (at in calls) assertTrue(calls.count { it > at - RetainedConfigRecovery.WRITE_WINDOW_MS && it <= at }
            <= RetainedConfigRecovery.MAX_WRITES_PER_WINDOW)
        assertEquals(0, last!!.verified)
        assertTrue(last.failed > 6)
    }

    @Test fun repeatedLossIsRateLimitedTemporarilyAndResumesAfterTheWindow() {
        val guard = RetainedConfigRecovery(expected)
        val calls = mutableListOf<Long>()
        var actual = defaults()
        var rateLimited = false
        for (t in 0L..400_000L step 1000) {
            val result = guard.tick(t, true, true, actual, { true }) { calls += t; actual.putAll(it) }
            if (result.status == "verified") actual = defaults()
            if (result.status == "rate_limited") rateLimited = true
        }
        assertTrue(rateLimited)
        assertTrue(calls.size > 6)
        for (at in calls) assertTrue(calls.count { it > at - RetainedConfigRecovery.WRITE_WINDOW_MS && it <= at } <= 6)
    }

    @Test fun matchingConfigDoesNotHideRevokedPermission() {
        val r = RetainedConfigRecovery(expected).tick(0, true, false, expected, { true }) { fail() }
        assertEquals("permission_unavailable", r.status)
        assertEquals(0, r.writes)
    }

    @Test fun persistentPermissionRejectionDoesNotLoopOrFallBackToShell() {
        val guard = RetainedConfigRecovery(expected)
        var calls = 0
        for (t in 0L..180_000L step 5000) {
            guard.tick(t, true, true, defaults(), { true }) {
                calls++
                throw java.lang.reflect.InvocationTargetException(SecurityException("denied"))
            }
        }
        assertEquals(1, calls)
    }

    @Test fun readbackAfterDebuggingIsReenabledDoesNotCountAsVerifiedOff() {
        val guard = RetainedConfigRecovery(expected)
        val actual = defaults()
        guard.tick(0, true, true, actual, { true }) {}
        guard.tick(5000, true, true, actual, { true }) { actual.putAll(it) }
        val on = guard.tick(10000, false, true, actual, { false }) { fail() }
        assertEquals(0, on.verified)
        assertEquals("waiting_readback", on.status)
        val off = guard.tick(15000, true, true, actual, { true }) { fail() }
        assertEquals(1, off.verified)
    }

    @Test fun lossOfOnlyWifiCallingSettingsTriggersFullCustomConfigRecovery() {
        val full = expected
        val actual = android.os.PersistableBundle(full).apply {
            putBoolean("carrier_wfc_ims_available_bool", false)
            putInt("external_unrelated_setting", 42)
        }
        val guard = RetainedConfigRecovery(full)
        guard.tick(0, true, true, actual, { true }) { fail("wait for stable off") }
        val sent = guard.tick(2000, true, true, actual, { true }) { actual.putAll(it) }
        assertEquals("write_sent", sent.status)
        val checked = guard.tick(4000, true, true, actual, { true }) { fail("already applied") }
        assertEquals("verified", checked.status)
        assertTrue(actual.getBoolean("carrier_wfc_ims_available_bool"))
        assertEquals(42, actual.getInt("external_unrelated_setting"))
    }
}
