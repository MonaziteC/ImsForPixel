package com.svenuks.imsforpixel

import org.junit.Assert.*
import org.junit.Test

class UsagePreferenceExperimentTest {
    @Test fun allThreeChoicesAreExplicitAndSwitchingDoesNotReplaceTheFirstOriginal() {
        val journal = Memory(); val radio = Radio(journal, 1)
        val session = UsagePreferenceExperiment(radio, journal, "sim")
        for ((index, mode) in listOf(0, 2, 1, 2, 0).withIndex()) {
            val at = index * 5000L
            session.request(mode)
            assertEquals("apply_pending", session.tick(at) { true }.state)
            assertEquals("preference_readback", session.tick(at + 1000) { true }.state)
            assertEquals(mode, radio.value)
            assertEquals(1, journal.original("sim"))
        }
        session.request(null)
        session.tick(30_000) { true }; session.tick(34_000) { true }
        assertEquals("restored", session.tick(35_100) { true }.state)
        assertEquals(1, radio.value)
        assertEquals(listOf(0, 2, 1, 2, 0, 1), radio.writes)
        assertNull(journal.original("sim"))
    }
    @Test fun interruptedSwitchCanRestoreThePreviouslyAppliedModeToo() {
        val journal = Memory(); val radio = Radio(journal)
        val session = UsagePreferenceExperiment(radio, journal, "sim")
        session.request(2); session.tick(0) { true }; session.tick(1000) { true }
        radio.failBeforeWrite = true; session.request(1)
        assertEquals("apply_failed", session.tick(5000) { true }.state)
        assertEquals(2, radio.value)
        assertEquals(0, journal.original("sim"))
        radio.failBeforeWrite = false
        val restarted = UsagePreferenceExperiment(radio, journal, "sim")
        restarted.tick(10_000) { true }; restarted.tick(14_000) { true }
        assertEquals("restored", restarted.tick(15_100) { true }.state)
        assertEquals(0, radio.value)
    }
    @Test fun modeSpecificSupportDoesNotAuthorizeAnUnsupportedChoice() {
        val journal = Memory(); val radio = Radio(journal).apply { unsupportedModes.add(1) }
        val session = UsagePreferenceExperiment(radio, journal, "sim")
        session.request(1)
        assertEquals("unsupported", session.tick(0) { true }.state)
        assertTrue(radio.writes.isEmpty())
        session.request(2)
        assertEquals("apply_pending", session.tick(5000) { true }.state)
        assertEquals(listOf(2), radio.writes)
    }
    @Test fun invalidChoiceCannotBeDispatched() {
        val session = UsagePreferenceExperiment(Radio(Memory()), Memory(), "sim")
        assertThrows(IllegalArgumentException::class.java) { session.request(-1) }
        assertThrows(IllegalArgumentException::class.java) { session.request(3) }
    }
    private class Memory : UsagePreferenceExperiment.Journal {
        val originals = mutableMapOf<String, Int>()
        val owned = mutableMapOf<String, MutableSet<Int>>()
        var failSave = false
        override fun original(identity: String) = originals[identity]
        override fun save(identity: String, value: Int, target: Int) {
            check(!failSave)
            originals.putIfAbsent(identity, value)
            owned.getOrPut(identity) { mutableSetOf() }.add(target)
        }
        override fun owns(identity: String, value: Int) = value in owned[identity].orEmpty()
        override fun remove(identity: String) { originals.remove(identity); owned.remove(identity) }
    }
    private class Radio(val store: Memory, var value: Int = 0) : UsagePreferenceExperiment.Port {
        var supported = true
        var failRead = false
        var failAfterWrite = false
        var failBeforeWrite = false
        val unsupportedModes = mutableSetOf<Int>()
        var ignoreWrite = false
        val writes = mutableListOf<Int>()
        override fun supported(value: Int) = supported && value !in unsupportedModes
        override fun read(): Int { check(!failRead); return value }
        override fun write(value: Int) {
            assertNotNull("Capture original before any mutation", store.original("sim"))
            check(!failBeforeWrite)
            writes += value
            if (!ignoreWrite) this.value = value
            check(!failAfterWrite)
        }
    }
    @Test fun receiptPrecedesWriteAndUndoNeedsSeparateSettledReads() {
        val journal = Memory(); val radio = Radio(journal)
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(2)
        assertEquals("waiting_idle", test.tick(0) { false }.state)
        assertTrue(radio.writes.isEmpty())
        assertEquals("apply_pending", test.tick(1) { true }.state)
        assertEquals(0, journal.original("sim"))
        assertEquals("preference_readback", test.tick(2) { true }.state)
        test.request(null)
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
        val journal = Memory().apply { save("sim", 1, 2) }
        val radio = Radio(journal, 2)
        val restarted = UsagePreferenceExperiment(radio, journal, "sim")
        restarted.tick(0) { true }; restarted.tick(4000) { true }
        assertEquals("restored", restarted.tick(5100) { true }.state)
        assertEquals(listOf(1), radio.writes)
    }
    @Test fun alreadyDataCentricIsNotClaimedAsAnAppliedExperiment() {
        val journal = Memory(); val radio = Radio(journal, 2)
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(2)
        assertEquals("already_selected", test.tick(0) { true }.state)
        test.request(null); test.tick(5000) { true }
        assertTrue(journal.originals.isEmpty()); assertTrue(radio.writes.isEmpty())
        assertEquals(2, radio.value)
    }
    @Test fun unsupportedOrUnknownPreferenceNeverMutatesTheSubscription() {
        val journal = Memory(); val radio = Radio(journal).apply { supported = false }
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(2)
        assertEquals("unsupported", test.tick(0) { true }.state)
        assertNull(test.requested)
        radio.supported = true; radio.value = -1; test.request(2)
        assertEquals("unknown_setting", test.tick(1) { true }.state)
        assertTrue(radio.writes.isEmpty()); assertTrue(journal.originals.isEmpty())
    }
    @Test fun externalChangesWinOverTheExperimentAndItsUndo() {
        val journal = Memory(); val radio = Radio(journal)
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(2); test.tick(0) { true }; test.tick(4000) { true }
        radio.value = 1
        assertEquals("external_change", test.tick(5000) { true }.state)
        assertNull(test.requested); assertTrue(journal.originals.isEmpty())
        assertEquals(listOf(2), radio.writes)
        test.tick(6000) { true }
        assertEquals(1, radio.value)
    }
    @Test fun journalFailurePreventsMutationAndIdentityFailurePreservesReceipt() {
        val journal = Memory().apply { failSave = true }; val radio = Radio(journal)
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(2)
        assertEquals("apply_failed", test.tick(0) { true }.state)
        assertTrue(radio.writes.isEmpty())
        journal.failSave = false; test.request(2); test.tick(1000) { true }
        radio.failRead = true; test.request(null)
        assertEquals("restore_failed", test.tick(5000) { true }.state)
        assertNotNull(journal.original("sim")); assertEquals(listOf(2), radio.writes)
    }
    @Test fun partialFailureStillLeavesAnUndoableDurableReceipt() {
        val journal = Memory(); val radio = Radio(journal).apply { failAfterWrite = true }
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(2)
        assertEquals("apply_failed", test.tick(0) { true }.state)
        assertEquals(2, radio.value); assertEquals(0, journal.original("sim"))
        radio.failAfterWrite = false; test.request(null)
        test.tick(4000) { true }; test.tick(8000) { true }; test.tick(9100) { true }
        assertEquals(0, radio.value); assertNull(journal.original("sim"))
    }
    @Test fun ignoredWritesDoNotBecomeSuccessOrAnUnboundedWriteLoop() {
        val journal = Memory(); val radio = Radio(journal).apply { ignoreWrite = true }
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        test.request(2); test.tick(0) { true }
        for (t in 1..20) test.tick(t * 1000L) { true }
        assertNull(test.requested); assertEquals(listOf(2), radio.writes)
        assertEquals(0, radio.value)
    }
    @Test fun repeatedComparisonCyclesCaptureAndRestoreTheSameBaseline() {
        val journal = Memory(); val radio = Radio(journal)
        val test = UsagePreferenceExperiment(radio, journal, "sim")
        for (cycle in 0..2) {
            val start = cycle * 20_000L
            test.request(2); test.tick(start) { true }
            assertEquals("preference_readback", test.tick(start + 1000) { true }.state)
            test.request(null); test.tick(start + 4000) { true }
            test.tick(start + 8000) { true }
            assertEquals("restored", test.tick(start + 9100) { true }.state)
            assertEquals("restored", test.tick(start + 10_200) { true }.state)
            assertEquals(0, radio.value)
            assertTrue(journal.originals.isEmpty())
        }
        assertEquals(listOf(2, 0, 2, 0, 2, 0), radio.writes)
    }
}
