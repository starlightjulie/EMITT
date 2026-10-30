package com.emitt.ui

import androidx.compose.ui.res.stringResource
import com.emitt.R
import com.emitt.data.UiText
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.emitt.data.SavedItem
import com.emitt.data.summary
import com.emitt.flipper.FlipperNfcFile
import com.emitt.nfc.NdefCodec
import com.emitt.nfc.TagReport
import com.emitt.nfc.TagResult
import com.emitt.nfc.toHex
import com.emitt.ui.theme.LocalLook

@Composable
fun TagsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    var enabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { enabled = adapter?.isEnabled == true } }
    var confirm by remember { mutableStateOf<NfcOp?>(null) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.readUri(uri)
        val raw = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "imported"
        val name = FlipperNfcFile.safeName(raw.removeSuffix(".nfc")) + ".nfc"
        runCatching { FlipperNfcFile.parse(text ?: error("unreadable")) }
            .onSuccess { vm.imported = ImportedFile(name, text!!, it) }
            .onFailure { vm.toast(R.string.not_flipper_file) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TermPanel(title = "NFC") {
            when {
                adapter == null -> StatusLine(Status.FAIL, stringResource(R.string.no_nfc))
                !enabled -> {
                    StatusLine(Status.WARN, stringResource(R.string.nfc_off))
                    ButtonRow { TermButton(stringResource(R.string.turn_on_nfc), { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }, Modifier.weight(1f), primary = true) }
                }
                else -> StatusLine(Status.OK, stringResource(R.string.nfc_ready))
            }
            vm.lastResult?.let { r ->
                when (r) {
                    is TagResult.Written -> StatusLine(Status.OK, r.capacity?.let { stringResource(R.string.wrote_of, r.bytes, it) } ?: stringResource(R.string.wrote, r.bytes))
                    TagResult.Erased -> StatusLine(Status.OK, stringResource(R.string.tag_erased))
                    TagResult.Locked -> StatusLine(Status.OK, stringResource(R.string.tag_locked))
                    is TagResult.Failed -> StatusLine(Status.FAIL, r.reason.text())
                    is TagResult.Read -> Unit
                }
            }
        }

        TermPanel(title = stringResource(R.string.tools)) {
            ButtonRow {
                TermButton(stringResource(R.string.read), { vm.nfcOp = null; vm.toast(R.string.tap_to_read) }, Modifier.weight(1f), Icons.Rounded.Search)
                TermButton(stringResource(R.string.copy), { vm.nfcOp = NfcOp.CopyRead }, Modifier.weight(1f), Icons.Rounded.ContentCopy)
            }
            ButtonRow {
                TermButton(stringResource(R.string.erase), { confirm = NfcOp.Erase }, Modifier.weight(1f), Icons.Rounded.DeleteSweep)
                TermButton(stringResource(R.string.lock), { confirm = NfcOp.Lock }, Modifier.weight(1f), Icons.Rounded.Lock, danger = true)
            }
            ButtonRow {
                TermButton(stringResource(R.string.import_nfc), { importFile.launch(arrayOf("*/*")) }, Modifier.weight(1f), Icons.Rounded.FileOpen)
            }
        }

        vm.imported?.let { ImportedPanel(vm, it) }
        vm.lastReport?.let { ReportPanel(vm, it) }
    }

    confirm?.let { op ->
        val lock = op == NfcOp.Lock
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(if (lock) R.string.lock_q else R.string.erase_q).uppercase(), style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    stringResource(if (lock) R.string.lock_warn
                    else R.string.erase_warn),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.nfcOp = op; vm.lastResult = null; confirm = null }) {
                    Text(stringResource(if (lock) R.string.lock_forever else R.string.erase).uppercase(), color = LocalLook.current.fail)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel).uppercase()) } },
        )
    }
}

@Composable
private fun ReportPanel(vm: MainViewModel, r: TagReport) {
    val context = LocalContext.current
    val look = LocalLook.current
    var raw by rememberSaveable { mutableStateOf(false) }
    val payload = remember(r) { r.message?.let(NdefCodec::toPayload) }
    TermPanel(title = stringResource(R.string.tag_title, r.chip)) {
        KvRow("uid", r.uid.toHex(":")) { context.copyText("UID", r.uid.toHex(":")); vm.toast(R.string.uid_copied) }
        KvRow("tech", r.techs.joinToString(", "))
        r.atqa?.let { KvRow("atqa", it.toHex()) }
        r.sak?.let { KvRow("sak", "%02X".format(it)) }
        r.ndefType?.let { KvRow("ndef", it) }
        r.maxSize?.let { KvRow(stringResource(R.string.memory), "${r.usedBytes ?: 0} / $it B") }
        r.writable?.let { KvRow(stringResource(R.string.writable), stringResource(if (it) R.string.yes else R.string.no_readonly)) }
        r.canLock?.let { KvRow(stringResource(R.string.lockable), stringResource(if (it) R.string.yes else R.string.no)) }

        SectionLabel(stringResource(R.string.records))
        val msg = r.message
        if (msg == null) Dim(stringResource(R.string.no_ndef_data)) else NdefCodec.describe(msg).forEach { KvRow(it.type, it.value) }

        if (payload != null) {
            ButtonRow {
                TermButton(stringResource(R.string.edit), { vm.newDraft(payload); vm.tab = Tab.CREATE }, Modifier.weight(1f), Icons.Rounded.Edit)
                TermButton(stringResource(R.string.save), {
                    vm.library.upsert(SavedItem(name = payload.summary().take(40).ifBlank { "Tag ${r.uid.toHex("")}" }, payload = payload))
                    vm.toast(R.string.lib_saved)
                }, Modifier.weight(1f), Icons.Rounded.Bookmark)
            }
        }

        SectionLabel(stringResource(R.string.tab_flipper))
        val flipper = remember(r) { runCatching { FlipperNfcFile.fromReport(r) } }
        flipper.fold(
            onSuccess = { text ->
                val name = "Tag_${r.uid.toHex("")}.nfc"
                if (r.dump == null) Dim(stringResource(R.string.uid_only))
                ButtonRow {
                    TermButton(stringResource(R.string.send), { vm.sendToFlipper(name, text) }, Modifier.weight(1f), Icons.AutoMirrored.Rounded.Send, primary = true, enabled = vm.flipperProgress == null)
                    TermButton(stringResource(R.string.share), { context.shareText(name, "application/octet-stream", text) }, Modifier.weight(1f), Icons.Rounded.Share)
                }
            },
            onFailure = { StatusLine(Status.INFO, stringResource(R.string.cant_convert)) },
        )

        r.dump?.let { d ->
            Row(Modifier.clickable { raw = !raw }.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (raw) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = look.signal)
                Text(stringResource(R.string.n_pages, d.pages.size), style = MaterialTheme.typography.labelMedium, color = look.signal)
            }
            AnimatedVisibility(raw) {
                Text(
                    d.pages.mapIndexed { i, p -> "%03d  %s".format(i, p.toHex()) }.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = look.mono),
                    color = look.dim,
                )
            }
        }
    }
}

@Composable
private fun ImportedPanel(vm: MainViewModel, imp: ImportedFile) {
    val f = imp.file
    val msg = remember(f) { f.ndef() }
    val payload = remember(msg) { msg?.let(NdefCodec::toPayload) }
    TermPanel(title = stringResource(R.string.file_title, imp.name), trailing = {
        Text("✕", color = LocalLook.current.dim, modifier = Modifier.clickable { vm.imported = null }.padding(4.dp))
    }) {
        KvRow(stringResource(R.string.device), f.deviceType)
        f.ultralight?.let { KvRow(stringResource(R.string.chip), it.flipperName) }
        KvRow("uid", f.uid.toHex(":"))
        f.pages?.let { KvRow(stringResource(R.string.pages), it.size.toString()) }
        SectionLabel(stringResource(R.string.records))
        if (msg == null) Dim(stringResource(R.string.no_ndef_in_file)) else NdefCodec.describe(msg).forEach { KvRow(it.type, it.value) }
        Spacer(Modifier.height(4.dp))
        if (msg != null) {
            ButtonRow {
                TermButton(stringResource(R.string.write_to_tag), { vm.startWrite(msg, UiText(R.string.file_data)) }, Modifier.weight(1f), Icons.Rounded.Nfc, primary = true)
                if (payload != null) TermButton(stringResource(R.string.edit), { vm.newDraft(payload); vm.tab = Tab.CREATE }, Modifier.weight(1f), Icons.Rounded.Edit)
            }
            ButtonRow {
                TermButton(stringResource(R.string.be_the_tag), { vm.emulate(msg, imp.name.removeSuffix(".nfc")) }, Modifier.weight(1f), Icons.Rounded.Smartphone)
            }
        }
        Dim(stringResource(R.string.uid_not_copied), Modifier.padding(top = 6.dp))
        ButtonRow {
            TermButton(stringResource(R.string.send_to_flipper), { vm.sendToFlipper(imp.name, imp.text) }, Modifier.weight(1f), Icons.AutoMirrored.Rounded.Send, enabled = vm.flipperProgress == null)
        }
    }
}
