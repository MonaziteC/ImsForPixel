package com.svenuks.imsforpixel.ui

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flyfishxu.kadb.Kadb
import com.svenuks.imsforpixel.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

/** Discovery and ADB connections exist only while the user opens the authorization sheet. */
internal class AdbConnection(private val context: Context, private val scope: CoroutineScope) {
    var port by mutableStateOf("")
    var pairingPort by mutableStateOf("")
    var connected by mutableStateOf(false)
    var phase by mutableStateOf("idle")
    private var job: Job? = null
    @Volatile private var closed = false
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    fun start() {
        MainActivity.onAuthStatusChanged = { check() }
        for ((service, pair) in listOf("_adb-tls-connect._tcp." to false, "_adb-tls-pairing._tcp." to true)) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(type: String?, code: Int) = Unit
                override fun onStopDiscoveryFailed(type: String?, code: Int) = Unit
                override fun onDiscoveryStarted(type: String?) = Unit
                override fun onDiscoveryStopped(type: String?) = Unit
                override fun onServiceLost(info: NsdServiceInfo?) = Unit
                override fun onServiceFound(info: NsdServiceInfo) {
                    if (closed) return
                    runCatching { nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(service: NsdServiceInfo?, code: Int) = Unit
                        override fun onServiceResolved(service: NsdServiceInfo) {
                            val host = service.host ?: return
                            val local = host.isLoopbackAddress || runCatching {
                                NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                                    .any { it.address.contentEquals(host.address) }
                            }.getOrDefault(false)
                            if (!local || closed) return
                            scope.launch {
                                if (!closed && service.port in 1..65535) {
                                    if (pair) {
                                        pairingPort = service.port.toString()
                                        MainActivity.pairingPort = service.port
                                    } else { port = service.port.toString(); check() }
                                }
                            }
                        }
                    }) }
                }
            }
            listeners += listener
            runCatching { nsd.discoverServices(service, NsdManager.PROTOCOL_DNS_SD, listener) }
        }
    }
    fun check() {
        val selected = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: return
        job?.cancel()
        job = scope.launch {
            phase = "connecting"
            connected = withContext(Dispatchers.IO) {
                runCatching { Kadb.create("127.0.0.1", selected, 3_000, 3_000).use {
                    it.shell("echo 1").exitCode == 0
                } }.getOrDefault(false)
            }
            phase = if (connected) "connected" else "failed"
        }
    }
    fun pair(code: String) {
        val selected = pairingPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: return
        if (!code.matches(Regex("[0-9]{6}"))) return
        job?.cancel()
        job = scope.launch {
            phase = "pairing"
            val ok = withContext(Dispatchers.IO) { runCatching {
                Kadb.pair("127.0.0.1", selected, code, name = "Pixel IMS")
            }.isSuccess }
            if (ok) { phase = "paired"; check() } else phase = "failed"
        }
    }
    fun close() {
        closed = true
        job?.cancel()
        MainActivity.onAuthStatusChanged = null
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
    }
}
