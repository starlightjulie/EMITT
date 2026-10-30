package com.emitt.ui

import androidx.compose.ui.res.stringResource
import com.emitt.R
import com.emitt.data.UiText
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
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
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
        TermPanel(title = stringResource(R.string.link)) {
            when (val s = state) {
                FlipperState.Idle -> StatusLine(Status.INFO, stringResource(R.string.fl_not_connected))
                is FlipperState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 10.dp), color = LocalLook.current.accent, strokeWidth = 2.dp)
                    Text("${s.name}: ${s.step.text()}…", style = MaterialTheme.typography.bodyMedium)
                }
                is FlipperState.Ready -> StatusLine(Status.OK, stringResource(R.string.connected_to, s.name))
                is FlipperState.Failed -> StatusLine(Status.FAIL, s.reason.text())
            }
            if (!granted) {
                Dim(stringResource(R.string.bt_why), Modifier.padding(top = 6.dp))
                ButtonRow { TermButton(stringResource(R.string.allow_bt), { ask.launch(blePermissions) }, Modifier.weight(1f), Icons.Rounded.Bluetooth, primary = true) }
            } else if (state !is FlipperState.Ready && state !is FlipperState.Connecting) {
                val last = vm.settings.flipperAddress
                ButtonRow {
                    if (last != null) TermButton(stringResource(R.string.reconnect), {
                        scope.launch { FlipperLink.connect(context, last, vm.settings.flipperName ?: "Flipper") }
                    }, Modifier.weight(1f), Icons.Rounded.Bluetooth, primary = true)
                    TermButton(stringResource(if (scanning) R.string.scanning else R.string.tab_scan), { FlipperLink.startScan(context) }, Modifier.weight(1f), Icons.AutoMirrored.Rounded.BluetoothSearching, enabled = !scanning)
                }
            }
        }

        if (granted && state !is FlipperState.Ready) {
            TermPanel(title = stringResource(R.string.nearby)) {
                if (found.isEmpty()) {
                    Dim(stringResource(if (scanning) R.string.looking_flippers else R.string.tap_scan))
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
                            Dim(f.address + (if (f.bonded) " · " + stringResource(R.string.paired) else "") + if (f.rssi == null) " · " + stringResource(R.string.not_in_range) else "")
                        }
                        f.rssi?.let { Dim("$it dBm") }
                    }
                }
                SectionLabel(stringResource(R.string.first_time))
                Dim(stringResource(R.string.pair_hint))
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
            }.onFailure { vm.toast(R.string.list_failed); files = emptyList() }
            busy = false
        }
    }
    LaunchedEffect(s.address) { refresh() }

    TermPanel(title = s.name) {
        val i = s.info
        i["firmware_version"]?.let { KvRow(stringResource(R.string.firmware), it + (i["firmware_branch"]?.let { b -> " ($b)" } ?: "")) }
        i["firmware_build_date"]?.let { KvRow(stringResource(R.string.built), it) }
        i["hardware_model"]?.let { KvRow(stringResource(R.string.model), it) }
        i["hardware_region"]?.let { KvRow(stringResource(R.string.region), it) }
        sd?.let { (total, free) -> if (total > 0) KvRow(stringResource(R.string.sd_card), stringResource(R.string.gb_free, free / 1e9, total / 1e9)) }
        Row(Modifier.clickable { allInfo = !allInfo }.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (allInfo) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = look.signal)
            Text(stringResource(R.string.all_device_info), style = MaterialTheme.typography.labelMedium, color = look.signal)
        }
        AnimatedVisibility(allInfo) {
            Column { i.toSortedMap().forEach { (k, v) -> KvRow(k.replace('_', ' '), v) } }
        }
        ButtonRow {
            TermButton(stringResource(R.string.beep), { scope.launch { runCatching { FlipperLink.alert() }.onFailure { vm.toast((it as? com.emitt.flipper.FlipperException)?.text ?: UiText(R.string.fl_failed)) } } }, Modifier.weight(1f), Icons.Rounded.Campaign)
            TermButton(stringResource(R.string.disconnect), { FlipperLink.disconnect() }, Modifier.weight(1f), Icons.Rounded.LinkOff)
        }
    }

    TermPanel(title = FlipperNfcFile.NFC_DIR, trailing = {
        Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh), Modifier.clickable(enabled = !busy) { refresh() }, tint = look.accent)
    }) {
        vm.flipperProgress?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth().padding(bottom = 8.dp), color = look.accent) }
        val list = files
        when {
            list == null || busy -> Dim(stringResource(R.string.reading))
            list.isEmpty() -> Dim(stringResource(R.string.no_nfc_files))
            else -> list.forEach { f -> KvRow(f.name, "${f.size} B", keepCase = true) }
        }
        Dim(stringResource(R.string.files_land_here), Modifier.padding(top = 8.dp))
    }
}
