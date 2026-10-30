package com.emitt.ui

import androidx.compose.ui.res.stringResource
import com.emitt.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.emitt.ui.theme.LocalLook

/** A colour swatch that opens the picker when tapped. */
@Composable
fun ColorRow(label: String, argb: Long, onPick: (Long) -> Unit, hint: String? = null) {
    val look = LocalLook.current
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).background(Color(argb), look.controlShape).border(1.dp, look.dim, look.controlShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Dim(hint)
        }
        Text("#%06X".format(argb and 0xFFFFFF), style = MaterialTheme.typography.labelMedium, color = look.dim)
    }
    if (open) ColorPickerDialog(argb, onDismiss = { open = false }) { onPick(it); open = false }
}

private val quick = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFF12081F, 0xFFFF3DCB, 0xFF3DF0FF, 0xFFFFB000, 0xFF3DFF8F, 0xFF4DF3FF,
    0xFFB47CFF, 0xFFFF3B3B, 0xFF1B4DFF, 0xFF0E0A04, 0xFF7DF9FF, 0xFFFF9DE6, 0xFFEDE9DE, 0xFF2A1F0F,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val look = LocalLook.current
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    var hex by remember { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }
    val current = Color.hsv(h, s, v)
    fun sync() { hex = "%06X".format(Color.hsv(h, s, v).toArgb() and 0xFFFFFF) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = look.panel.copy(alpha = 1f),
        shape = look.panelShape,
        title = { Text(stringResource(R.string.pick_colour).uppercase(), style = MaterialTheme.typography.titleMedium, color = look.accent) },
        text = {
            Column {
                // Saturation (x) / value (y) square
                Box(
                    Modifier.fillMaxWidth().height(170.dp)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                s = (it.x / size.width).coerceIn(0f, 1f); v = 1f - (it.y / size.height).coerceIn(0f, 1f); sync()
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                s = (change.position.x / size.width).coerceIn(0f, 1f)
                                v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                sync()
                            }
                        },
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(h, 1f, 1f))))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        drawCircle(Color.White, 9.dp.toPx(), Offset(s * size.width, (1 - v) * size.height), style = Stroke(2.dp.toPx()))
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Hue strip
                Box(
                    Modifier.fillMaxWidth().height(28.dp)
                        .pointerInput(Unit) { detectTapGestures { h = (it.x / size.width).coerceIn(0f, 1f) * 360f; sync() } }
                        .pointerInput(Unit) { detectDragGestures { c, _ -> h = (c.position.x / size.width).coerceIn(0f, 1f) * 360f; sync() } },
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                        val x = h / 360f * size.width
                        drawRect(Color.White, Offset(x - 2.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quick.forEach { c ->
                        Box(
                            Modifier.size(26.dp).background(Color(c)).border(1.dp, look.dim)
                                .clickable {
                                    android.graphics.Color.colorToHSV(c.toInt(), hsv); h = hsv[0]; s = hsv[1]; v = hsv[2]; sync()
                                },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(current).border(1.dp, look.dim))
                    Spacer(Modifier.width(10.dp))
                    TermField(
                        value = hex,
                        onValueChange = { t ->
                            hex = t.removePrefix("#").take(6).uppercase()
                            hex.toLongOrNull(16)?.takeIf { hex.length == 6 }?.let {
                                android.graphics.Color.colorToHSV((0xFF000000 or it).toInt(), hsv); h = hsv[0]; s = hsv[1]; v = hsv[2]
                            }
                        },
                        label = stringResource(R.string.hex),
                        mono = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(current.toArgb().toLong() and 0xFFFFFFFF) }) { Text(stringResource(R.string.use).uppercase(), color = look.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel).uppercase(), color = look.dim) } },
    )
}
