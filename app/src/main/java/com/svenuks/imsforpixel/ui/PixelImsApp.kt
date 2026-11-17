package com.svenuks.imsforpixel.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.svenuks.imsforpixel.*
import com.svenuks.imsforpixel.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** All visual colors and typography come from MaterialTheme; backend state never doubles as busy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelImsApp(initialPage: String = "status") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val actionFailedMessage = stringResource(R.string.ui_action_failed)
    val prefs = remember { context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE) }
    var page by rememberSaveable { mutableStateOf(if (initialPage == "config") "config" else "status") }
    var snapshot by remember { mutableStateOf(GuardianStore.latest(context)) }
    var active by remember { mutableStateOf(GuardianStore.active(context, snapshot)) }
    var launching by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var restoreDialog by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val autoGuard = remember(revision) { GuardianController.enabled(context) }
    val state = snapshot?.optString("state").orEmpty()
    val busy = launching || (active && state in listOf("starting", "restoring"))
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                val latest = GuardianStore.latest(context)
                latest to GuardianStore.active(context, latest)
            }.let { snapshot = it.first; active = it.second }
            delay(1000)
        }
    }
    fun request(action: String) {
        scope.launch {
            try { withContext(Dispatchers.IO) { GuardianController.request(context, action) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { snack.showSnackbar(actionFailedMessage) }
        }
    }
    fun apply() {
        if (active) request("edit") else sheet = "apply"
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.ui_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (active && !busy) DropdownMenuItem(text = { Text(stringResource(R.string.ui_pause)) }, onClick = { menu = false; request("stop") })
                    DropdownMenuItem(text = { Text(stringResource(R.string.ui_connection)) }, onClick = { menu = false; sheet = "connect" })
                    DropdownMenuItem(text = { Text(stringResource(R.string.guard_battery)) }, onClick = {
                        menu = false
                        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.ui_help)) }, onClick = { menu = false; help = true })
                }
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = page == "status", onClick = { page = "status" },
                    icon = { Icon(Icons.Default.Home, null) }, label = { Text(stringResource(R.string.ui_status)) })
                NavigationBarItem(selected = page == "config", onClick = { page = "config" },
                    icon = { Icon(Icons.Default.Settings, null) }, label = { Text(stringResource(R.string.ui_config)) })
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { insets ->
        if (page == "status") StatusPage(Modifier.padding(insets), snapshot, active, busy, autoGuard,
            onApply = { apply() }, onView = { page = "config" }, onRestore = { restoreDialog = true })
        else ConfigPage(Modifier.padding(insets), prefs, revision, active || busy, busy, autoGuard,
            onApply = { apply() }, onAutoGuard = { prefs.edit().putBoolean("guardian_enabled", it).commit() })
    }
    if (restoreDialog) AlertDialog(onDismissRequest = { restoreDialog = false },
        icon = { Icon(Icons.Default.Refresh, null) },
        title = { Text(stringResource(R.string.ui_restore)) },
        text = { Text(stringResource(R.string.ui_restore_confirm)) },
        confirmButton = { TextButton(onClick = {
            restoreDialog = false
            if (active && state == "running") request("restore") else sheet = "restore"
        }) { Text(stringResource(R.string.ui_confirm_restore)) } },
        dismissButton = { TextButton(onClick = { restoreDialog = false }) { Text(stringResource(android.R.string.cancel)) } })
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(stringResource(R.string.ui_about)) },
        text = { Text(stringResource(R.string.ui_help_text, BuildConfig.VERSION_NAME)) },
        confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(android.R.string.ok)) } })
    sheet?.let { action ->
        AuthorizationSheet(onDismiss = { sheet = null }, guardianActive = active,
            action = action, autoGuard = autoGuard, onReady = { port ->
                sheet = null
                if (action != "connect") {
                    launching = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                when {
                                    action == "restore" -> GuardianController.applyOnce(context, port, true)
                                    autoGuard -> GuardianController.activateAndStart(context, port)
                                    else -> GuardianController.applyOnce(context, port, false)
                                }
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            snack.showSnackbar(actionFailedMessage)
                        } finally { launching = false }
                    }
                }
            })
    }
}

@Composable
internal fun StatusPage(modifier: Modifier, snapshot: JSONObject?, active: Boolean, busy: Boolean,
    autoGuard: Boolean, onApply: () -> Unit, onView: () -> Unit, onRestore: () -> Unit) {
    val phase = snapshot?.optString("phase").orEmpty()
    val stale = active && SystemClock.elapsedRealtime() - (snapshot?.optLong("elapsed") ?: 0) > 120_000
    val healthy = active && !stale && phase in listOf("matching", "verified")
    val title = when {
        busy -> if (phase == "restoring" || snapshot?.optString("state") == "restoring") R.string.ui_restoring else R.string.ui_activating
        stale -> R.string.guard_pending
        healthy -> R.string.ui_guard_healthy
        active -> guardianPhaseLabel(phase)
        phase in listOf("restored", "restore_sent", "restore_failed") -> guardianPhaseLabel(phase)
        phase == "applied" -> R.string.ui_applied
        phase in listOf("needs_authorization", "failed") -> R.string.guard_reauthorize
        else -> R.string.ui_guard_idle
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (healthy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Icon(if (healthy) Icons.Default.CheckCircle else Icons.Default.Info, null)
                        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                    }
                    Text(stringResource(when {
                        active && snapshot?.optBoolean("debug_off") == true -> R.string.ui_debug_closed
                        active && !busy -> R.string.ui_can_close_debug
                        else -> R.string.ui_guard_intro
                    }), style = MaterialTheme.typography.bodyMedium)
                    if (active && !busy) Text(stringResource(R.string.guard_count, snapshot?.optInt("repairs") ?: 0), style = MaterialTheme.typography.labelLarge)
                    Button(onClick = onApply, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (active) R.string.ui_edit else if (autoGuard) R.string.ui_apply_guard else R.string.ui_apply))
                    }
                    if (active) TextButton(onClick = onView, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.ui_view_config)) }
                }
            }
        }
        val rows = snapshot?.optJSONArray("sim_status")
        if (active && rows != null) {
            items(rows.length()) { index ->
                val sim = rows.getJSONObject(index)
                OutlinedCard {
                    ListItem(headlineContent = { Text(stringResource(R.string.ui_sim, sim.optInt("slot") + 1)) },
                        supportingContent = { Text(sim.optString("name")) },
                        trailingContent = { Text(stringResource(when {
                            !sim.optBoolean("present") -> R.string.ui_sim_unavailable
                            stale || sim.isNull("config_matches") -> R.string.ui_unknown
                            sim.optBoolean("config_matches") -> R.string.ui_config_ok
                            else -> R.string.ui_repairing
                        })) })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ListItem(headlineContent = { Text(stringResource(R.string.ui_ims)) },
                        trailingContent = { Text(stringResource(when {
                            stale || !sim.optBoolean("present") || sim.isNull("ims_registered") -> R.string.ui_unknown
                            sim.optBoolean("ims_registered") -> R.string.ui_registered
                            else -> R.string.ui_unregistered
                        })) })
                }
            }
        }
        item {
            TextButton(onClick = onView, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ui_open_config))
            }
            OutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ui_restore))
            }
        }
    }
}

private data class Option(val key: String, val title: Int, val help: Int)
private val calls = listOf(
    Option("volte", R.string.ui_volte, R.string.ui_volte_help),
    Option("vonr", R.string.ui_vonr, R.string.ui_vonr_help),
    Option("vowifi", R.string.ui_wifi_call, R.string.ui_wifi_help))
private val nr = listOf(Option("nr", R.string.ui_nr, R.string.ui_nr_help), Option("nr_plus", R.string.ui_plus, R.string.ui_plus_help))
private val advanced = listOf(
    Option("wfc_roaming", R.string.ui_wifi_roam, R.string.ui_wifi_roam_help),
    Option("ss_ut", R.string.ui_ut, R.string.ui_ut_help),
    Option("show_ims", R.string.ui_show_ims, R.string.ui_show_ims_help),
    Option("cross_sim", R.string.ui_cross, R.string.ui_cross_help),
    Option("allow_apn", R.string.ui_apn, R.string.ui_apn_help))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigPage(modifier: Modifier, prefs: SharedPreferences, revision: Int, locked: Boolean, busy: Boolean,
    autoGuard: Boolean, onApply: () -> Unit, onAutoGuard: (Boolean) -> Unit) {
    var slot by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var info by remember { mutableStateOf<Option?>(null) }
    val options = remember(slot, revision) { ImsCarrierConfig.snapshot(prefs, slot) }
    @Composable fun group(title: Int, items: List<Option>) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        OutlinedCard {
            items.forEachIndexed { index, option ->
                val dependent = option.key != "nr_plus" || options.getBoolean("nr")
                val checked = options.getBoolean(option.key) && dependent
                ListItem(headlineContent = { Text(stringResource(option.title)) },
                    modifier = if (!locked && dependent) Modifier.clickable {
                        prefs.edit().putBoolean("${option.key}_slot_$slot", !checked).commit()
                    } else Modifier,
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { info = option }) { Icon(Icons.Default.Info, stringResource(R.string.ui_explain)) }
                            // Read-only choices retain their selected colors while guarding.
                            Switch(checked = checked, onCheckedChange = if (!locked && dependent) ({ value ->
                                prefs.edit().putBoolean("${option.key}_slot_$slot", value).commit(); Unit
                            }) else null, enabled = dependent)
                        }
                    })
                if (index < items.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                for (index in 0..1) SegmentedButton(selected = slot == index, onClick = { slot = index },
                    shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(stringResource(R.string.ui_sim, index + 1)) }
            }
        }
        if (locked) item { Text(stringResource(R.string.ui_read_only), style = MaterialTheme.typography.bodyMedium) }
        item { group(R.string.ui_calls_group, calls) }
        item { group(R.string.ui_nr_group, nr) }
        item {
            ListItem(headlineContent = { Text(stringResource(R.string.ui_advanced)) },
                modifier = Modifier.clickable { expanded = !expanded },
                trailingContent = { Text(stringResource(if (expanded) R.string.ui_collapse else R.string.ui_expand)) })
            if (expanded) group(R.string.ui_advanced, advanced)
        }
        item {
            OutlinedCard {
                ListItem(headlineContent = { Text(stringResource(R.string.guard_auto)) },
                    supportingContent = { Text(stringResource(R.string.ui_auto_help)) },
                    trailingContent = { Switch(checked = autoGuard, onCheckedChange = if (!locked) onAutoGuard else null) })
            }
        }
        item {
            Button(onClick = onApply, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (locked) R.string.ui_edit else if (autoGuard) R.string.ui_apply_guard else R.string.ui_apply))
            }
        }
    }
    info?.let { option -> AlertDialog(onDismissRequest = { info = null },
        title = { Text(stringResource(option.title)) }, text = { Text(stringResource(option.help)) },
        confirmButton = { TextButton(onClick = { info = null }) { Text(stringResource(android.R.string.ok)) } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthorizationSheet(onDismiss: () -> Unit, guardianActive: Boolean, action: String, autoGuard: Boolean, onReady: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connection = remember { AdbConnection(context, scope) }
    var manual by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    DisposableEffect(connection) { connection.start(); onDispose { connection.close() } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.ui_connection), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(if (guardianActive) R.string.ui_connection_optional else R.string.ui_connection_help), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (connection.phase in listOf("connecting", "pairing")) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(if (connection.connected) Icons.Default.CheckCircle else Icons.Default.Info, null)
                Text(stringResource(when (connection.phase) {
                    "connected" -> R.string.ui_connected
                    "connecting" -> R.string.ui_connecting
                    "pairing" -> R.string.ui_pairing
                    "paired" -> R.string.ui_paired
                    "failed" -> R.string.ui_connection_failed
                    else -> R.string.ui_connection_idle
                }))
            }
            if (!connection.connected) {
                OutlinedButton(onClick = {
                    (context as? MainActivity)?.requestNotificationPermissionAndShow()
                    runCatching { context.startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")) }
                        .onFailure { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_open_debug)) }
                TextButton(onClick = { manual = !manual }) { Text(stringResource(R.string.ui_manual)) }
                if (manual) {
                    OutlinedTextField(connection.port, { connection.port = it.filter(Char::isDigit).take(5); connection.connected = false },
                        label = { Text(stringResource(R.string.label_adb_port)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { connection.check() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_connect)) }
                    OutlinedTextField(connection.pairingPort, { connection.pairingPort = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.ui_pair_port)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.ui_pair_code)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { connection.pair(code) }, enabled = code.length == 6 && connection.pairingPort.toIntOrNull()?.let { it in 1..65535 } == true,
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_pair)) }
                }
            }
            Button(onClick = { connection.port.toIntOrNull()?.let(onReady) }, enabled = connection.connected,
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(when {
                    action == "restore" -> R.string.ui_confirm_restore
                    action == "connect" -> R.string.ui_done
                    autoGuard -> R.string.ui_apply_guard
                    else -> R.string.ui_apply
                }))
            }
        }
    }
}
