package com.emitt.ui

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
import androidx.compose.material.icons.rounded.Send
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
        runCatching { FlipperNfcFile.parse(text ?: error("Couldn't read the file")) }
            .onSuccess { vm.imported = ImportedFile(name, text!!, it) }
            .onFailure { vm.toast(it.message ?: "Not a Flipper NFC file") }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TermPanel(title = "NFC") {
            when {
                adapter == null -> StatusLine(Status.FAIL, "This phone doesn't have NFC. You can still make QR codes and Flipper files.")
                !enabled -> {
                    StatusLine(Status.WARN, "NFC is off.")
                    ButtonRow { TermButton("Turn on NFC", { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }, Modifier.weight(1f), primary = true) }
                }
                else -> StatusLine(Status.OK, "Ready. Tap any tag on the back of the phone to inspect it.")
            }
            vm.lastResult?.let { r ->
                when (r) {
                    is TagResult.Written -> StatusLine(Status.OK, "Wrote ${r.bytes} bytes" + (r.capacity?.let { " of $it" } ?: ""))
                    TagResult.Erased -> StatusLine(Status.OK, "Tag erased")
                    TagResult.Locked -> StatusLine(Status.OK, "Tag locked. It's read-only now.")
                    is TagResult.Failed -> StatusLine(Status.FAIL, r.reason)
                    is TagResult.Read -> Unit
                }
            }
        }

        TermPanel(title = "Tools") {
            ButtonRow {
                TermButton("Read", { vm.nfcOp = null; vm.toast("Tap a tag to read it") }, Modifier.weight(1f), Icons.Rounded.Search)
                TermButton("Copy", { vm.nfcOp = NfcOp.CopyRead }, Modifier.weight(1f), Icons.Rounded.ContentCopy)
            }
            ButtonRow {
                TermButton("Erase", { confirm = NfcOp.Erase }, Modifier.weight(1f), Icons.Rounded.DeleteSweep)
                TermButton("Lock", { confirm = NfcOp.Lock }, Modifier.weight(1f), Icons.Rounded.Lock, danger = true)
            }
            ButtonRow {
                TermButton("Import .nfc", { importFile.launch(arrayOf("*/*")) }, Modifier.weight(1f), Icons.Rounded.FileOpen)
            }
        }

        vm.imported?.let { ImportedPanel(vm, it) }
        vm.lastReport?.let { ReportPanel(vm, it) }
    }

    confirm?.let { op ->
        val lock = op == NfcOp.Lock
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (lock) "LOCK TAG?" else "ERASE TAG?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    if (lock) "Locking makes the tag permanently read-only. Nobody, including you, can change it afterwards."
                    else "This clears the tag's content. The tag stays writable.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.nfcOp = op; vm.lastResult = null; confirm = null }) {
                    Text(if (lock) "LOCK FOREVER" else "ERASE", color = LocalLook.current.fail)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("CANCEL") } },
        )
    }
}

@Composable
private fun ReportPanel(vm: MainViewModel, r: TagReport) {
    val context = LocalContext.current
    val look = LocalLook.current
    var raw by rememberSaveable { mutableStateOf(false) }
    val payload = remember(r) { r.message?.let(NdefCodec::toPayload) }
    TermPanel(title = "Tag · ${r.chip}") {
        KvRow("uid", r.uid.toHex(":")) { context.copyText("UID", r.uid.toHex(":")); vm.toast("UID copied") }
        KvRow("tech", r.techs.joinToString(", "))
        r.atqa?.let { KvRow("atqa", it.toHex()) }
        r.sak?.let { KvRow("sak", "%02X".format(it)) }
        r.ndefType?.let { KvRow("ndef", it) }
        r.maxSize?.let { KvRow("memory", "${r.usedBytes ?: 0} / $it bytes") }
        r.writable?.let { KvRow("writable", if (it) "yes" else "no (read-only)") }
        r.canLock?.let { KvRow("lockable", if (it) "yes" else "no") }

        SectionLabel("Records")
        val msg = r.message
        if (msg == null) Dim("No NDEF data") else NdefCodec.describe(msg).forEach { KvRow(it.type, it.value) }

        if (payload != null) {
            ButtonRow {
                TermButton("Edit", { vm.newDraft(payload); vm.tab = Tab.CREATE }, Modifier.weight(1f), Icons.Rounded.Edit)
                TermButton("Save", {
                    vm.library.upsert(SavedItem(name = payload.summary().take(40).ifBlank { "Tag ${r.uid.toHex("")}" }, payload = payload))
                    vm.toast("Saved to library")
                }, Modifier.weight(1f), Icons.Rounded.Bookmark)
            }
        }

        SectionLabel("Flipper")
        val flipper = remember(r) { runCatching { FlipperNfcFile.fromReport(r) } }
        flipper.fold(
            onSuccess = { text ->
                val name = "Tag_${r.uid.toHex("")}.nfc"
                if (r.dump == null) Dim("Only UID, ATQA and SAK can be saved for this chip type.")
                ButtonRow {
                    TermButton("Send", { vm.sendToFlipper(name, text) }, Modifier.weight(1f), Icons.Rounded.Send, primary = true, enabled = vm.flipperProgress == null)
                    TermButton("Share", { context.shareText(name, "application/octet-stream", text) }, Modifier.weight(1f), Icons.Rounded.Share)
                }
            },
            onFailure = { StatusLine(Status.INFO, it.message ?: "Can't convert this tag") },
        )

        r.dump?.let { d ->
            Row(Modifier.clickable { raw = !raw }.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (raw) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = look.signal)
                Text("${d.pages.size} pages", style = MaterialTheme.typography.labelMedium, color = look.signal)
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
    TermPanel(title = "File · ${imp.name}", trailing = {
        Text("✕", color = LocalLook.current.dim, modifier = Modifier.clickable { vm.imported = null }.padding(4.dp))
    }) {
        KvRow("device", f.deviceType)
        f.ultralight?.let { KvRow("chip", it.flipperName) }
        KvRow("uid", f.uid.toHex(":"))
        f.pages?.let { KvRow("pages", it.size.toString()) }
        SectionLabel("Records")
        if (msg == null) Dim("No NDEF message found in this file.") else NdefCodec.describe(msg).forEach { KvRow(it.type, it.value) }
        Spacer(Modifier.height(4.dp))
        if (msg != null) {
            ButtonRow {
                TermButton("Write to tag", { vm.startWrite(msg, "the file's data") }, Modifier.weight(1f), Icons.Rounded.Nfc, primary = true)
                if (payload != null) TermButton("Edit", { vm.newDraft(payload); vm.tab = Tab.CREATE }, Modifier.weight(1f), Icons.Rounded.Edit)
            }
            ButtonRow {
                TermButton("Be the tag", { vm.emulate(msg, imp.name.removeSuffix(".nfc")) }, Modifier.weight(1f), Icons.Rounded.Smartphone)
            }
        }
        Dim("Phones can't copy a tag's UID; writing copies the data only.", Modifier.padding(top = 6.dp))
        ButtonRow {
            TermButton("Send to Flipper", { vm.sendToFlipper(imp.name, imp.text) }, Modifier.weight(1f), Icons.Rounded.Send, enabled = vm.flipperProgress == null)
        }
    }
}
