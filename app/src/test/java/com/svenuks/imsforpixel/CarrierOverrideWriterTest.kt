package com.svenuks.imsforpixel

import android.os.PersistableBundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class CarrierOverrideWriterTest {
    // Models CarrierConfigLoader's documented merging and two override layers.
    private class Loader {
        val runtime = mutableMapOf<Int, PersistableBundle>()
        val disk = mutableMapOf<Int, PersistableBundle>()
        val writer = CarrierOverrideWriter { sub, values, persistent ->
            fun merge(layer: MutableMap<Int, PersistableBundle>) {
                if (values == null) layer.remove(sub)
                else layer.getOrPut(sub) { PersistableBundle() }.putAll(values)
            }
            merge(runtime)
            if (persistent) merge(disk)
        }
        fun effective(sub: Int) = PersistableBundle().apply {
            disk[sub]?.let { putAll(it) }
            runtime[sub]?.let { putAll(it) }
        }
    }
    private fun nr(plus: Boolean) = PersistableBundle().apply {
        NrCarrierConfig.apply(this, true, plus)
    }

    @Test fun applyAndRestoreWorkWhenAllPersistentCallsAreDenied() {
        val runtime = PersistableBundle()
        val flags = mutableListOf<Boolean>()
        val writer = CarrierOverrideWriter { _, values, persistent ->
            flags.add(persistent)
            if (persistent) throw SecurityException("persistent=true requires system app")
            if (values == null) runtime.clear() else runtime.putAll(values)
        }
        writer.replace(7, nr(true))
        assertTrue(CarrierOverrideWriter.matches(nr(true), runtime))
        writer.replace(7, nr(false))
        assertEquals(1, runtime.size())
        writer.replace(7, null)
        assertTrue(runtime.isEmpty)
        assertEquals(listOf(false, false, false, false, false), flags)
    }

    @Test fun temporaryReplacementDoesNotTouchOtherSimOrExistingPersistentLayer() {
        val loader = Loader()
        loader.disk[7] = PersistableBundle().apply { putInt("external_setting", 42) }
        loader.writer.replace(7, nr(true))
        loader.writer.replace(9, nr(true))
        loader.writer.replace(7, nr(false))
        assertEquals(2, loader.effective(7).size())
        assertEquals(6, loader.effective(9).size())
        loader.writer.replace(7, null)
        assertEquals(42, loader.effective(7).getInt("external_setting"))
        assertEquals(6, loader.effective(9).size())
    }

    @Test fun deniedTemporaryClearIsReportedAndDoesNotContinue() {
        var calls = 0
        val writer = CarrierOverrideWriter { _, _, _ ->
            calls++
            throw SecurityException("temporary permission denied too")
        }
        assertThrows(SecurityException::class.java) { writer.replace(7, nr(true)) }
        assertEquals(1, calls)
    }

    @Test fun readbackChecksArrayContentsAndRejectsMissingOrWrongValues() {
        assertTrue(CarrierOverrideWriter.matches(nr(true), nr(true)))
        assertFalse(CarrierOverrideWriter.matches(nr(true), nr(false)))
        assertFalse(CarrierOverrideWriter.matches(nr(true), null))
        val wrong = nr(true).apply { putIntArray("carrier_nr_availabilities_int_array", intArrayOf(1)) }
        assertFalse(CarrierOverrideWriter.matches(nr(true), wrong))
    }


}
