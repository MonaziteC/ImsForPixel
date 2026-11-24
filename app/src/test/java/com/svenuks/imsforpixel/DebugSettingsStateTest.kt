package com.svenuks.imsforpixel

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class DebugSettingsStateTest {
    private val provider = object : ContentProvider() {
        val values = DebugSettingsState.KEYS.associateWith { "0" }.toMutableMap()
        var denied: String? = null
        var queries = 0
        var advanceMs = 0L
        val fallback = mutableMapOf<String, String>()
        override fun call(method: String, arg: String?, extras: android.os.Bundle?): android.os.Bundle? =
            fallback[arg]?.let { value -> android.os.Bundle().apply { putString("value", value) } }
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            queries++
            if (advanceMs > 0) org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(advanceMs))
            val name = uri.lastPathSegment
            if (name == denied) throw SecurityException("Test read denied")
            return MatrixCursor(arrayOf("value")).also { cursor -> values[name]?.let { cursor.addRow(arrayOf(it)) } }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }
    @Before fun setup() {
        provider.attachInfo(RuntimeEnvironment.getApplication(),
            android.content.pm.ProviderInfo().apply { authority = "settings" })
        ShadowContentResolver.registerProviderInternal("settings", provider)
    }

    @Test fun eachReadQueriesFreshValuesRatherThanReusingAnOffSnapshot() {
        val context = RuntimeEnvironment.getApplication()
        val first = DebugSettingsState.read(context)
        assertTrue(first.allOff)
        provider.values[Settings.Global.DEVELOPMENT_SETTINGS_ENABLED] = "1"
        val second = DebugSettingsState.read(context)
        assertEquals(1, second.developer); assertFalse(second.allOff)
        provider.values[Settings.Global.DEVELOPMENT_SETTINGS_ENABLED] = "0"
        assertTrue(DebugSettingsState.read(context).allOff)
        assertEquals(9, provider.queries)
    }
    @Test fun missingDeniedAndMalformedFlagsCannotMeanAllOff() {
        val context = RuntimeEnvironment.getApplication()
        provider.values.remove("adb_wifi_enabled")
        assertNull(DebugSettingsState.read(context).wireless)
        provider.values["adb_wifi_enabled"] = "bad"
        assertFalse(DebugSettingsState.read(context).allOff)
        provider.values["adb_wifi_enabled"] = "0"
        provider.denied = Settings.Global.DEVELOPMENT_SETTINGS_ENABLED
        val denied = DebugSettingsState.read(context)
        assertNull(denied.developer); assertFalse(denied.allKnown); assertFalse(denied.allOff)
    }
    @Test fun anyEnabledFlagBlocksRecoveryAndFutureOrStaleSamplesCannotAuthorizeWrites() {
        for (flags in listOf(listOf(1, 0, 0), listOf(0, 1, 0), listOf(0, 0, 1))) {
            val sample = DebugSettingsState(flags[0], flags[1], flags[2], 1000, 0)
            assertTrue(sample.anyOn); assertFalse(sample.allowsWrites(1000))
        }
        val off = DebugSettingsState(0, 0, 0, 1000, 0)
        assertTrue(off.allowsWrites(1000)); assertFalse(off.allowsWrites(999)); assertFalse(off.allowsWrites(4001))
        assertFalse(DebugSettingsState(0, null, 0, 1000, 0).allowsWrites(1000))
    }
    @Test fun directProviderGetCanRecoverAnUnavailableQueryWithoutCachingOrDefaultZero() {
        val context = RuntimeEnvironment.getApplication()
        provider.values.remove("adb_wifi_enabled")
        provider.fallback["adb_wifi_enabled"] = "1"
        assertEquals(1, DebugSettingsState.read(context).wireless)
        provider.fallback["adb_wifi_enabled"] = "0"
        assertTrue(DebugSettingsState.read(context).allOff)
        provider.fallback["adb_wifi_enabled"] = "invalid"
        assertNull(DebugSettingsState.read(context).wireless)
    }
    @Test fun slowProviderReadsDoNotBecomeArtificiallyFreshAtCompletion() {
        provider.advanceMs = 4000
        val sample = DebugSettingsState.read(RuntimeEnvironment.getApplication())
        assertTrue(sample.allOff)
        assertFalse(sample.allowsWrites(android.os.SystemClock.elapsedRealtime()))
    }
}
