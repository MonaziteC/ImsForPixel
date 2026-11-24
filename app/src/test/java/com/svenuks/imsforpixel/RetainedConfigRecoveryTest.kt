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

    @Test fun missingConfigIsWrittenImmediatelyAndOnlyANewReadConfirmsRecovery() {
        val guard = RetainedConfigRecovery(expected)
        val actual = defaults()
        var calls = 0
        fun tick(now: Long) = guard.tick(now, true, actual, { true }) { calls++; actual.putAll(it) }
        val sent = tick(0)
        assertEquals("write_sent", sent.status); assertEquals(0, sent.verified)
        assertEquals("verified", tick(1000).status)
        repeat(20) { assertTrue(tick(2000L + it * 5000).matched) }
        assertEquals(1, calls); assertEquals(42, actual.getInt("external_unrelated_setting"))
    }
    @Test fun changedCallSimOrPermissionConditionImmediatelyBeforeDispatchDefersTheWrite() {
        val guard = RetainedConfigRecovery(expected)
        val deferred = guard.tick(0, true, defaults(), { false }) { fail("Condition changed") }
        assertEquals("write_deferred", deferred.status); assertEquals(0, deferred.writes)
        val sent = guard.tick(2000, true, defaults(), { true }) {}
        assertEquals("write_sent", sent.status)
    }
    @Test fun noPermissionUnreadableConfigAndAlreadyMatchingValuesDoNotWrite() {
        val guard = RetainedConfigRecovery(expected)
        assertEquals("permission_unavailable", guard.tick(0, false, defaults(), { true }) { fail() }.status)
        assertEquals("config_unreadable", guard.tick(5000, true, null, { true }) { fail() }.status)
        val matched = guard.tick(10000, true, expected, { true }) { fail() }
        assertEquals("matching", matched.status); assertEquals(0, matched.verified); assertEquals(0, matched.writes)
    }
    @Test fun renewedLossIsRepairedWithCooldownAndNoUnnecessaryWrites() {
        val guard = RetainedConfigRecovery(expected)
        var actual = defaults()
        var calls = 0
        fun tick(t: Long) = guard.tick(t, true, actual, { true }) { calls++; actual.putAll(it) }
        tick(0); tick(1000)
        actual = defaults()
        assertEquals("cooldown", tick(2000).status)
        assertEquals("write_sent", tick(5000).status)
        assertEquals("verified", tick(6000).status); assertEquals(2, calls)
    }
    @Test fun nonEffectiveWritesAreRetriedBeyondTheOldLifetimeLimitWithRollingRateControl() {
        val guard = RetainedConfigRecovery(expected)
        val calls = mutableListOf<Long>()
        var last: RetainedConfigRecovery.Result? = null
        for (t in 0L..1_200_000L step 1000) {
            last = guard.tick(t, true, defaults(), { true }) { calls += t }
        }
        assertTrue(calls.size > 6)
        for (at in calls) assertTrue(calls.count { it > at - RetainedConfigRecovery.WRITE_WINDOW_MS && it <= at }
            <= RetainedConfigRecovery.MAX_WRITES_PER_WINDOW)
        assertEquals(0, last!!.verified); assertTrue(last.failed > 6)
    }
    @Test fun repeatedLossIsRateLimitedTemporarilyAndResumesAfterTheWindow() {
        val guard = RetainedConfigRecovery(expected)
        val calls = mutableListOf<Long>()
        var actual = defaults()
        var rateLimited = false
        for (t in 0L..400_000L step 1000) {
            val result = guard.tick(t, true, actual, { true }) { calls += t; actual.putAll(it) }
            if (result.status == "verified") actual = defaults()
            if (result.status == "rate_limited") rateLimited = true
        }
        assertTrue(rateLimited); assertTrue(calls.size > 6)
        for (at in calls) assertTrue(calls.count { it > at - RetainedConfigRecovery.WRITE_WINDOW_MS && it <= at } <= 6)
    }
    @Test fun matchingConfigDoesNotHideRevokedPermission() {
        val r = RetainedConfigRecovery(expected).tick(0, false, expected, { true }) { fail() }
        assertEquals("permission_unavailable", r.status); assertEquals(0, r.writes)
    }
    @Test fun persistentPermissionRejectionDoesNotLoopOrFallBackToShell() {
        val guard = RetainedConfigRecovery(expected)
        var calls = 0
        for (t in 0L..180_000L step 5000) {
            guard.tick(t, true, defaults(), { true }) {
                calls++
                throw java.lang.reflect.InvocationTargetException(SecurityException("denied"))
            }
        }
        assertEquals(1, calls)
    }
    @Test fun revokedPermissionBeforeReadbackCannotCountAsVerified() {
        val guard = RetainedConfigRecovery(expected)
        val actual = defaults()
        guard.tick(0, true, actual, { true }) { actual.putAll(it) }
        val revoked = guard.tick(1000, false, actual, { true }) { fail() }
        assertEquals("permission_unavailable", revoked.status); assertEquals(0, revoked.verified)
        assertEquals(1, guard.tick(2000, true, actual, { true }) { fail() }.verified)
    }
    @Test fun lossOfOnlyWifiCallingSettingsTriggersFullCustomConfigRecovery() {
        val full = expected
        val actual = PersistableBundle(full).apply {
            putBoolean("carrier_wfc_ims_available_bool", false)
            putInt("external_unrelated_setting", 42)
        }
        val guard = RetainedConfigRecovery(full)
        val sent = guard.tick(0, true, actual, { true }) { actual.putAll(it) }
        assertEquals("write_sent", sent.status)
        val checked = guard.tick(2000, true, actual, { true }) { fail("already applied") }
        assertEquals("verified", checked.status)
        assertTrue(actual.getBoolean("carrier_wfc_ims_available_bool")); assertEquals(42, actual.getInt("external_unrelated_setting"))
    }
}
