package com.svenuks.imsforpixel

import org.junit.Assert.*
import org.junit.Test

class UsagePreferenceExperimentTest {
    private class Memory : UsagePreferenceExperiment.Journal {
        val originals = mutableMapOf<String, Int>()
        var failSave = false
        override fun original(identity: String) = originals[identity]
        override fun save(identity: String, value: Int) {
            check(!failSave)
            originals.putIfAbsent(identity, value)
        }
        override fun remove(identity: String) { originals.remove(identity) }
    }
    private class Radio(val store: Memory, var value: Int = 0) : UsagePreferenceExperiment.Port {
        var supported = true
        var failRead = false
        var failAfterWrite = false
        var ignoreWrite = false
        val writes = mutableListOf<Int>()
        override fun supported() = supported
        override fun read(): Int { check(!failRead); return value }
        override fun write(value: Int) {
            assertNotNull("Capture original before any mutation", store.original("sim"))
            writes += value
            if (!ignoreWrite) this.value = value
            check(!failAfterWrite)
        }
    }
    @Test fun receiptPrecedesWriteAndUndoNeedsSeparateSettledReads() {
        val journal = Memory(); val radio = Radio(journal)
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(true)
        assertEquals("waiting_idle", test.tick(0) { false }.state)
        assertTrue(radio.writes.isEmpty())
        assertEquals("apply_pending", test.tick(1) { true }.state)
        assertEquals(0, journal.original("sim"))
        assertEquals("preference_readback", test.tick(2) { true }.state)
        test.request(false)
        assertEquals("restore_waiting", test.tick(3) { false }.state)
        test.tick(4) { true }
        assertEquals(listOf(2, 0), radio.writes)
        test.tick(500) { true }
        assertNotNull(journal.original("sim"))
        test.tick(4000) { true }
        assertNotNull(journal.original("sim"))
        assertEquals("restored", test.tick(5100) { true }.state)
        assertNull(journal.original("sim"))
    }
    @Test fun restartRecoversPendingOriginalInsteadOfTreatingDataAsOriginal() {
        val journal = Memory().apply { save("sim", 1) }
        val radio = Radio(journal, 2)
        val restarted = UsagePreferenceExperiment(radio, journal, "sim")
        restarted.tick(0) { true }; restarted.tick(4000) { true }
        assertEquals("restored", restarted.tick(5100) { true }.state)
        assertEquals(listOf(1), radio.writes)
    }
    @Test fun alreadyDataCentricIsNotClaimedAsAnAppliedExperiment() {
        val journal = Memory(); val radio = Radio(journal, 2)
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(true)
        assertEquals("already_data", test.tick(0) { true }.state)
        test.request(false); test.tick(5000) { true }
        assertTrue(journal.originals.isEmpty()); assertTrue(radio.writes.isEmpty())
        assertEquals(2, radio.value)
    }
    @Test fun unsupportedOrUnknownPreferenceNeverMutatesTheSubscription() {
        val journal = Memory(); val radio = Radio(journal).apply { supported = false }
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(true)
        assertEquals("unsupported", test.tick(0) { true }.state)
        assertFalse(test.requested)
        radio.supported = true; radio.value = -1; test.request(true)
        assertEquals("unknown_setting", test.tick(1) { true }.state)
        assertTrue(radio.writes.isEmpty()); assertTrue(journal.originals.isEmpty())
    }
    @Test fun externalChangesWinOverTheExperimentAndItsUndo() {
        val journal = Memory(); val radio = Radio(journal)
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(true); test.tick(0) { true }; test.tick(4000) { true }
        radio.value = 1
        assertEquals("external_change", test.tick(5000) { true }.state)
        assertFalse(test.requested); assertTrue(journal.originals.isEmpty())
        assertEquals(listOf(2), radio.writes)
        test.tick(6000) { true }
        assertEquals(1, radio.value)
    }
    @Test fun journalFailurePreventsMutationAndIdentityFailurePreservesReceipt() {
        val journal = Memory().apply { failSave = true }; val radio = Radio(journal)
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(true)
        assertEquals("apply_failed", test.tick(0) { true }.state)
        assertTrue(radio.writes.isEmpty())
        journal.failSave = false; test.request(true); test.tick(1000) { true }
        radio.failRead = true; test.request(false)
        assertEquals("restore_failed", test.tick(5000) { true }.state)
        assertNotNull(journal.original("sim")); assertEquals(listOf(2), radio.writes)
    }
    @Test fun partialFailureStillLeavesAnUndoableDurableReceipt() {
        val journal = Memory(); val radio = Radio(journal).apply { failAfterWrite = true }
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(true)
        assertEquals("apply_failed", test.tick(0) { true }.state)
        assertEquals(2, radio.value); assertEquals(0, journal.original("sim"))
        radio.failAfterWrite = false; test.request(false)
        test.tick(4000) { true }; test.tick(8000) { true }; test.tick(9100) { true }
        assertEquals(0, radio.value); assertNull(journal.original("sim"))
    }
    @Test fun ignoredWritesDoNotBecomeSuccessOrAnUnboundedWriteLoop() {
        val journal = Memory(); val radio = Radio(journal).apply { ignoreWrite = true }
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(true); test.tick(0) { true }
        for (t in 1..20) test.tick(t * 1000L) { true }
        assertFalse(test.requested); assertEquals(listOf(2), radio.writes)
        assertEquals(0, radio.value)
    }
    @Test fun repeatedComparisonCyclesCaptureAndRestoreTheSameBaseline() {
        val journal = Memory(); val radio = Radio(journal)
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        for (cycle in 0..2) {
            val start = cycle * 20_000L
            test.request(true); test.tick(start) { true }
            assertEquals("preference_readback", test.tick(start + 1000) { true }.state)
            test.request(false); test.tick(start + 4000) { true }
            test.tick(start + 8000) { true }
            assertEquals("restored", test.tick(start + 9100) { true }.state)
            assertEquals("restored", test.tick(start + 10_200) { true }.state)
            assertEquals(0, radio.value)
            assertTrue(journal.originals.isEmpty())
        }
        assertEquals(listOf(2, 0, 2, 0, 2, 0), radio.writes)
    }
}
