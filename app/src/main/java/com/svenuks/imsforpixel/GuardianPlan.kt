package com.svenuks.imsforpixel

import android.os.PersistableBundle
import org.json.JSONObject

internal object GuardianPlan {
    data class Target(val slot: Int, val subId: Int, val expected: PersistableBundle)
    fun create(request: JSONObject, id: String, active: List<Pair<Int, Int>>): List<Target> {
        require(request.getString("guardian_id") == id && request.getInt("version") == 2)
        val bundles = (0..1).associateWith { ImsCarrierConfig.build(request.getJSONObject("slot_$it")) }
        val valid = active.filter { it.first in 0..1 && it.second >= 0 }
        require(valid.isNotEmpty()) { "No active SIM" }
        require(valid.map { it.first }.distinct().size == valid.size) { "Ambiguous SIM slot" }
        return valid.sortedBy { it.first }.map { (slot, sub) -> Target(slot, sub, bundles.getValue(slot)) }
    }

    fun phase(phases: List<String>): String {
        val priority = listOf("write_denied", "permission_unavailable", "target_missing", "call_active",
            "config_unreadable", "write_failed", "readback_timeout", "write_sent", "waiting_readback",
            "rate_limited", "cooldown", "waiting_debug_off", "settling", "verified", "matching")
        return priority.firstOrNull { it in phases } ?: "config_unreadable"
    }
}
