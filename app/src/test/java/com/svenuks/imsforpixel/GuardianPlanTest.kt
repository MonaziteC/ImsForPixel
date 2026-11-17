package com.svenuks.imsforpixel

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class GuardianPlanTest {
    private fun request() = JSONObject().put("guardian_id", "test-123").put("version", 2)
        .put("slot_0", testImsOptions().put("vowifi", false))
        .put("slot_1", testImsOptions().put("vowifi", true))

    @Test fun bothActiveSimsKeepIndependentDesiredValuesAndFrozenSubscriptionIds() {
        val plan = GuardianPlan.create(request(), "test-123", listOf(1 to 7, 0 to 3))
        assertEquals(listOf(0, 1), plan.map { it.slot })
        assertEquals(listOf(3, 7), plan.map { it.subId })
        assertFalse(plan[0].expected.getBoolean("carrier_wfc_ims_available_bool"))
        assertTrue(plan[1].expected.getBoolean("carrier_wfc_ims_available_bool"))
        assertEquals(listOf(3), GuardianPlan.create(request(), "test-123", listOf(0 to 3)).map { it.subId })
    }

    @Test fun noActiveSimOrAmbiguousTargetsCannotAccidentallyProtectAnotherProfile() {
        for (targets in listOf(emptyList(), listOf(0 to -1), listOf(0 to 3, 0 to 7))) {
            assertThrows(IllegalArgumentException::class.java) { GuardianPlan.create(request(), "test-123", targets) }
        }
        assertThrows(IllegalArgumentException::class.java) { GuardianPlan.create(request(), "other", listOf(0 to 3)) }
    }

    @Test fun oneHealthySimCannotHideAnUnhealthyOrMissingSim() {
        assertEquals("write_sent", GuardianPlan.phase(listOf("matching", "write_sent")))
        assertEquals("target_missing", GuardianPlan.phase(listOf("matching", "target_missing")))
        assertEquals("permission_unavailable", GuardianPlan.phase(listOf("verified", "permission_unavailable")))
        assertEquals("matching", GuardianPlan.phase(listOf("matching", "matching")))
    }
}
