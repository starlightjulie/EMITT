package com.emitt.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.emitt.flipper.FlipperLink
import com.emitt.flipper.FlipperNfcFile
import com.emitt.flipper.FlipperProto
import com.emitt.flipper.FlipperState
import com.emitt.ui.theme.LocalLook
import kotlinx.coroutines.launch

private val blePermissions =
    if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

@Composable
fun FlipperScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by FlipperLink.state.collectAsState()
    val found by FlipperLink.found.collectAsState()
    val scanning by FlipperLink.scanning.collectAsState()
    fun hasPerms() = blePermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var granted by remember { mutableStateOf(hasPerms()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasPerms() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TermPanel(title = "Link") {
            when (val s = state) {
                FlipperState.Idle -> StatusLine(Status.INFO, "Not connected")
                is FlipperState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 10.dp), color = LocalLook.current.accent, strokeWidth = 2.dp)
                    Text("${s.name}: ${s.step}…", style = MaterialTheme.typography.bodyMedium)
                }
                is FlipperState.Ready -> StatusLine(Status.OK, "Connected to ${s.name}")
                is FlipperState.Failed -> StatusLine(Status.FAIL, s.reason)
            }
            if (!granted) {
                Dim("EMITT needs Bluetooth access to find and talk to your Flipper.", Modifier.padding(top = 6.dp))
                ButtonRow { TermButton("Allow Bluetooth", { ask.launch(blePermissions) }, Modifier.weight(1f), Icons.Rounded.Bluetooth, primary = true) }
            } else if (state !is FlipperState.Ready && state !is FlipperState.Connecting) {
                val last = vm.settings.flipperAddress
                ButtonRow {
                    if (last != null) TermButton("Reconnect", {
                        scope.launch { FlipperLink.connect(context, last, vm.settings.flipperName ?: "Flipper") }
                    }, Modifier.weight(1f), Icons.Rounded.Bluetooth, primary = true)
                    TermButton(if (scanning) "Scanning…" else "Scan", { FlipperLink.startScan(context) }, Modifier.weight(1f), Icons.Rounded.BluetoothSearching, enabled = !scanning)
                }
            }
        }

        if (granted && state !is FlipperState.Ready) {
            TermPanel(title = "Nearby") {
                if (found.isEmpty()) {
                    Dim(if (scanning) "Looking for Flippers…" else "Tap Scan. On the Flipper: Settings → Bluetooth → ON.")
                }
                found.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = state !is FlipperState.Connecting) {
                            scope.launch { FlipperLink.connect(context, f.address, f.name) }
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Bluetooth, null, tint = LocalLook.current.signal)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.titleSmall)
                            Dim(f.address + if (f.bonded) " · paired" else "")
                        }
                        f.rssi?.let { Dim("$it dBm") }
                    }
                }
                SectionLabel("First time?")
                Dim("Pick your Flipper above. Android will ask for a PIN: type the 6 digits shown on the Flipper's screen.")
            }
        }

        (state as? FlipperState.Ready)?.let { ReadyPanels(vm, it) }
    }
}

@Composable
private fun ReadyPanels(vm: MainViewModel, s: FlipperState.Ready) {
    val scope = rememberCoroutineScope()
    val look = LocalLook.current
    var files by remember { mutableStateOf<List<FlipperProto.StorageFile>?>(null) }
    var sd by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var allInfo by rememberSaveable { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            busy = true
            runCatching {
                sd = FlipperLink.sdInfo()
                files = FlipperLink.list(FlipperNfcFile.NFC_DIR).filter { !it.isDir }.sortedBy { it.name.lowercase() }
            }.onFailure { vm.toast("Couldn't list files: ${it.message}"); files = emptyList() }
            busy = false
        }
    }
    LaunchedEffect(s.address) { refresh() }

    TermPanel(title = s.name) {
        val i = s.info
        i["firmware_version"]?.let { KvRow("firmware", it + (i["firmware_branch"]?.let { b -> " ($b)" } ?: "")) }
        i["firmware_build_date"]?.let { KvRow("built", it) }
        i["hardware_model"]?.let { KvRow("model", it) }
        i["hardware_region"]?.let { KvRow("region", it) }
        sd?.let { (total, free) -> if (total > 0) KvRow("sd card", "%.1f / %.1f GB free".format(free / 1e9, total / 1e9)) }
        Row(Modifier.clickable { allInfo = !allInfo }.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (allInfo) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = look.signal)
            Text("All device info", style = MaterialTheme.typography.labelMedium, color = look.signal)
        }
        AnimatedVisibility(allInfo) {
            Column { i.toSortedMap().forEach { (k, v) -> KvRow(k.replace('_', ' '), v) } }
        }
        ButtonRow {
            TermButton("Beep", { scope.launch { runCatching { FlipperLink.alert() }.onFailure { vm.toast(it.message ?: "Failed") } } }, Modifier.weight(1f), Icons.Rounded.Campaign)
            TermButton("Disconnect", { FlipperLink.disconnect() }, Modifier.weight(1f), Icons.Rounded.LinkOff)
        }
    }

    TermPanel(title = FlipperNfcFile.NFC_DIR, trailing = {
        Icon(Icons.Rounded.Refresh, "Refresh", Modifier.clickable(enabled = !busy) { refresh() }, tint = look.accent)
    }) {
        vm.flipperProgress?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth().padding(bottom = 8.dp), color = look.accent) }
        val list = files
        when {
            list == null || busy -> Dim("Reading…")
            list.isEmpty() -> Dim("No NFC files yet. Make one in Create → Flipper.")
            else -> list.forEach { f -> KvRow(f.name, "${f.size} B") }
        }
        Dim("Files sent from EMITT land here. Open them on the Flipper under NFC → Saved.", Modifier.padding(top = 8.dp))
    }
}
