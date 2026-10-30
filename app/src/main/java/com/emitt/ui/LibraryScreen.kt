package com.emitt.ui

import androidx.compose.ui.res.stringResource
import com.emitt.R
import com.emitt.data.UiText
import com.emitt.data.UiTextException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emitt.data.Encoding
import com.emitt.data.SavedItem
import com.emitt.data.summary
import com.emitt.ui.theme.LocalLook
import com.emitt.widget.CodeTileService
import com.emitt.widget.CodeWidget

@Composable
fun LibraryScreen(vm: MainViewModel) {
    val items by vm.library.items.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var starred by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = items.filter { (!starred || it.favourite) && (query.isBlank() || it.name.contains(query, true) || it.payload.summary().contains(query, true)) }
        .sortedWith(compareByDescending<SavedItem> { it.favourite }.thenByDescending { it.created })

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TermPanel(title = stringResource(R.string.library_count, items.size)) {
            TermField(query, { query = it }, stringResource(R.string.search))
            ChipGroup(listOf(false, true), starred, { if (it) "★ " + stringResource(R.string.starred) else stringResource(R.string.all) }, { starred = it }, Modifier.padding(top = 4.dp))
        }
        if (shown.isEmpty()) {
            TermPanel {
                Dim(stringResource(if (items.isEmpty()) R.string.lib_empty else R.string.no_matches))
            }
        }
        shown.forEach { item ->
            LibraryRow(vm, item, open == item.id) { open = if (open == item.id) null else item.id }
        }
    }
}

@Composable
private fun LibraryRow(vm: MainViewModel, item: SavedItem, expanded: Boolean, onToggle: () -> Unit) {
    val look = LocalLook.current
    val context = LocalContext.current
    val text = remember(item.payload) { Encoding.toQrText(item.payload) }
    val thumb = rememberQr(text, item.style, 240)
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val pinned = vm.settings.tileItemId == item.id

    TermPanel(padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(look.bg.copy(alpha = 0.4f), look.controlShape), contentAlignment = Alignment.Center) {
                thumb?.bitmap?.let { Image(it.asImageBitmap(), null, Modifier.size(52.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${stringResource(item.payload.kind.label)}${if (pinned) " · " + stringResource(R.string.tile) else ""} · ${item.payload.summary()}",
                    style = MaterialTheme.typography.bodySmall, color = look.dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                if (item.favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder, stringResource(R.string.star),
                Modifier.clickable { vm.library.upsert(item.copy(favourite = !item.favourite)) }.padding(6.dp),
                tint = if (item.favourite) look.accent else look.dim,
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                ButtonRow {
                    TermButton(stringResource(R.string.show), { vm.fullscreen = text to item.style }, Modifier.weight(1f), Icons.Rounded.Fullscreen, primary = true)
                    TermButton(stringResource(R.string.edit), { vm.edit(item) }, Modifier.weight(1f), Icons.Rounded.Edit)
                }
                ButtonRow {
                    TermButton(stringResource(R.string.write_to_tag), { vm.startWrite(item.payload) }, Modifier.weight(1f), Icons.Rounded.Nfc)
                    TermButton(stringResource(R.string.tab_flipper), {
                        vm.flipperFile(item.payload, item.name).onSuccess { (n, t) -> vm.sendToFlipper(n, t) }.onFailure { vm.toast((it as? UiTextException)?.text ?: UiText(R.string.cant_build_file)) }
                    }, Modifier.weight(1f), Icons.AutoMirrored.Rounded.Send, enabled = vm.flipperProgress == null)
                }
                ButtonRow {
                    TermButton(stringResource(if (pinned) R.string.unpin_tile else R.string.pin_tile), {
                        vm.settings.tileItemId = if (pinned) null else item.id
                        CodeTileService.refresh(context)
                        CodeWidget.refreshAll(context)
                        if (pinned) vm.toast(R.string.tile_removed) else vm.toast(R.string.tile_now_shows, item.name)
                    }, Modifier.weight(1f), Icons.Rounded.PushPin)
                    TermButton(stringResource(R.string.rename), { rename = true }, Modifier.weight(1f), Icons.Rounded.DriveFileRenameOutline)
                }
                ButtonRow {
                    TermButton(stringResource(R.string.be_the_tag), { vm.emulate(item.payload, item.name) }, Modifier.weight(1f), Icons.Rounded.Smartphone)
                    TermButton(stringResource(R.string.delete), { delete = true }, Modifier.weight(1f), Icons.Rounded.Delete, danger = true)
                }
            }
        }
    }

    if (rename) {
        var name by remember { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(stringResource(R.string.rename).uppercase(), style = MaterialTheme.typography.titleMedium) },
            text = { TermField(name, { name = it }, stringResource(R.string.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.library.upsert(item.copy(name = name.trim().ifEmpty { item.name })); rename = false
                    CodeWidget.refreshAll(context)
                }) { Text(stringResource(R.string.save).uppercase(), color = look.accent) }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(stringResource(R.string.cancel).uppercase()) } },
        )
    }
    if (delete) {
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text(stringResource(R.string.delete_q).uppercase(), style = MaterialTheme.typography.titleMedium) },
            text = { Text(stringResource(R.string.delete_confirm, item.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.library.delete(item.id)
                    if (vm.settings.tileItemId == item.id) vm.settings.tileItemId = null
                    delete = false
                    CodeWidget.refreshAll(context)
                }) { Text(stringResource(R.string.delete).uppercase(), color = look.fail) }
            },
            dismissButton = { TextButton(onClick = { delete = false }) { Text(stringResource(R.string.cancel).uppercase()) } },
        )
    }
}
