package com.svenuks.imsforpixel.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.svenuks.imsforpixel.R
import org.json.JSONObject

@Composable
internal fun SimStatusCard(sim: JSONObject, stale: Boolean, busy: Boolean, onUsage: (Int, Int) -> Unit) {
    val present = sim.optBoolean("present")
    val config = when {
        !present -> R.string.ui_sim_unavailable
        stale || sim.isNull("config_matches") -> R.string.ui_unknown
        sim.optBoolean("config_matches") -> R.string.ui_config_ok
        else -> R.string.ui_repairing
    }
    val registered = when {
        stale || !present || sim.isNull("ims_registered") -> R.string.ui_unknown
        sim.optBoolean("ims_registered") -> R.string.ui_registered
        else -> R.string.ui_unregistered
    }
    OutlinedCard(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(stringResource(R.string.ui_sim, sim.optInt("slot") + 1), Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge)
                }
                Text(sim.optString("name"), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(stringResource(config), present && !stale && sim.optBoolean("config_matches"))
                StatusBadge("IMS · ${stringResource(registered)}", present && !stale && sim.optBoolean("ims_registered"))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            RadioStatusPanel(sim.optJSONObject("radio"), stale || !present)
            UsagePreferenceField(sim, stale, busy, onUsage)
        }
    }
}

@Composable
private fun StatusBadge(text: String, healthy: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = if (healthy) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (healthy) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsagePreferenceField(sim: JSONObject, stale: Boolean, busy: Boolean, onUsage: (Int, Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = if (stale || sim.isNull("usage_current")) -1 else sim.optInt("usage_current", -1)
    val desired = if (stale || sim.isNull("usage_requested")) current else sim.optInt("usage_requested", -1)
    val state = sim.optString("usage_state")
    val pending = state in listOf("apply_pending", "restore_pending", "waiting_idle", "restore_waiting")
    val enabled = !busy && !stale && sim.optBoolean("present") && !pending
    val problem = state in listOf("apply_failed", "restore_failed")
    val message = when (state) {
        "apply_pending" -> R.string.usage_applying
        "restore_pending" -> R.string.usage_restoring
        "waiting_idle", "restore_waiting" -> R.string.usage_waiting
        "unsupported", "unavailable", "unknown_setting" -> R.string.usage_unavailable
        "external_change" -> R.string.usage_external
        "apply_failed", "restore_failed" -> R.string.usage_failed
        else -> null
    }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(value = stringResource(usageModeLabel(desired)), onValueChange = {}, readOnly = true,
            enabled = enabled, singleLine = true, isError = problem,
            label = { Text(stringResource(R.string.usage_title)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            supportingText = message?.let { { Text(stringResource(it)) } },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth().testTag("usage-choice-${sim.optInt("slot")}"))
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            for (mode in 0..2) DropdownMenuItem(text = { Text(stringResource(usageModeLabel(mode))) },
                leadingIcon = { if (mode == desired) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp)) },
                onClick = { expanded = false; onUsage(sim.optInt("slot"), mode) })
        }
    }
}

private fun usageModeLabel(mode: Int) = when (mode) {
    0 -> R.string.usage_default
    1 -> R.string.usage_voice
    2 -> R.string.usage_data
    else -> R.string.ui_unknown
}

@Composable
internal fun RadioStatusPanel(radio: JSONObject?, unavailable: Boolean) {
    val now = SystemClock.elapsedRealtime()
    val valid = !unavailable && radio != null && now - radio.optLong("sampled_elapsed", -1) in 0..120_000L
    val data = if (valid) radio else null
    val bandwidths = RadioStatusFormat.bandwidths(data?.optString("bandwidths_khz"))
    val label = RadioStatusFormat.network(data) ?: stringResource(if (data?.optInt("data_rat") == 13)
        R.string.radio_lte_nr_available else R.string.ui_unknown)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            val bands = data?.optString("bands")?.takeUnless { it.isBlank() || it == "null" } ?: "—"
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(bands, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioMetric("${data?.optString("signal_rat")?.takeIf { it in listOf("NR", "LTE") } ?: "NR"} RSRP",
                RadioStatusFormat.number(data, "rsrp", -160..-20), "dBm", Modifier.weight(1f))
            RadioMetric(if (data?.optString("signal_rat") == "LTE") "RSSNR" else "SINR",
                RadioStatusFormat.number(data, "sinr", -40..50), "dB", Modifier.weight(1f))
            RadioMetric(stringResource(R.string.radio_bandwidth), bandwidths?.sum()?.let { RadioStatusFormat.mhz(it) } ?: "—",
                "MHz", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (bandwidths != null && bandwidths.size > 1)
                bandwidths.joinToString(" + ") { RadioStatusFormat.mhz(it) } + " MHz" else stringResource(R.string.radio_reported),
                Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val measured = data?.takeUnless { it.isNull("measurement_age_ms") }?.optLong("measurement_age_ms", -1)
            val age = measured?.takeIf { it >= 0 }?.plus(now - (data?.optLong("sampled_elapsed") ?: now))
            Text(when {
                age == null -> stringResource(R.string.radio_cached)
                age >= 60_000 -> stringResource(R.string.radio_minutes_ago, age / 60_000)
                else -> stringResource(R.string.radio_seconds_ago, age / 1000)
            }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RadioMetric(label: String, value: String, unit: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal object RadioStatusFormat {
    fun bandwidths(raw: String?): List<Int>? {
        if (raw.isNullOrBlank()) return null
        return raw.split(',').map { it.trim().toIntOrNull() ?: return null }
            .takeIf { list -> list.all { it in 1..1_000_000 } }?.sorted()
    }
    fun mhz(value: Int) = if (value % 1000 == 0) (value / 1000).toString() else (value / 1000.0).toString()
    fun number(row: JSONObject?, key: String, range: IntRange): String {
        val value = row?.takeUnless { it.isNull(key) }?.optInt(key, Int.MAX_VALUE)
        return value?.takeIf { it in range }?.toString() ?: "—"
    }
    fun network(row: JSONObject?): String? = when (row?.optInt("data_rat")) {
        20 -> "5G SA"
        13 -> when { row.optInt("nr_state") == 3 -> "5G NSA"; row.optInt("override_type") in listOf(3, 5) -> null
            row.optInt("override_type") == 1 -> "4G+"; else -> "4G" }
        19 -> "4G+"
        3, 8, 9, 10, 15, 17 -> "3G"
        1, 2, 16 -> "2G"
        else -> null
    }
}
