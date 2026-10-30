package com.emitt.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emitt.ui.theme.LocalLook

/** Glow for titles, when the theme has it switched on. */
@Composable
fun glowing(style: TextStyle, color: Color = LocalLook.current.accent): TextStyle {
    val look = LocalLook.current
    return if (look.config.glow && look.dark) style.copy(shadow = Shadow(color.copy(alpha = 0.7f), blurRadius = 14f)) else style
}

/** A framed panel with an optional "▍TITLE" header. The workhorse container of every screen. */
@Composable
fun TermPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = LocalLook.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(look.panelShape)
            .background(look.panel)
            .then(if (look.config.outlines) Modifier.border(1.dp, look.accent.copy(alpha = 0.45f), look.panelShape) else Modifier)
            .padding(padding),
    ) {
        if (title != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 3.dp, height = 14.dp).background(look.accent))
                Spacer(Modifier.width(8.dp))
                Text(
                    title.uppercase(),
                    style = glowing(MaterialTheme.typography.labelLarge),
                    color = look.accent,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                trailing()
            }
        }
        content()
    }
}

/** "// LABEL" separator inside panels. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Text(
        "// " + text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = look.signal,
        modifier = modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
fun TermButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val look = LocalLook.current
    val tone = if (danger) look.fail else look.accent
    val bg = if (primary) tone else Color.Transparent
    val fg = if (primary) (if (look.dark) look.bg else Color.White) else tone
    Row(
        modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(look.controlShape)
            .background(bg)
            .border(BorderStroke(1.dp, tone), look.controlShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

@Composable
fun TermChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    Box(
        modifier
            .clip(look.controlShape)
            .background(if (selected) look.accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, if (selected) look.accent else look.dim.copy(alpha = 0.5f), look.controlShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            if (selected) "▸ ${label.uppercase()}" else label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) look.accent else look.dim,
        )
    }
}

/** A wrapping row of single-choice chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipGroup(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { TermChip(label(it), it == selected, { onSelect(it) }) }
    }
}

/** A single horizontally scrolling row of chips, for long option lists. */
@Composable
fun <T> ChipStrip(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { TermChip(label(it), it == selected, { onSelect(it) }) }
    }
}

@Composable
fun TermField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    mono: Boolean = false,
    supporting: String? = null,
    isError: Boolean = false,
) {
    val look = LocalLook.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label.uppercase(), style = MaterialTheme.typography.labelMedium) },
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        supportingText = supporting?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        textStyle = if (mono) MaterialTheme.typography.bodyLarge.copy(fontFamily = look.mono) else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = look.controlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = look.accent,
            unfocusedBorderColor = look.dim.copy(alpha = 0.5f),
            focusedLabelColor = look.accent,
            cursorColor = look.accent,
            focusedContainerColor = look.bg.copy(alpha = 0.35f),
            unfocusedContainerColor = look.bg.copy(alpha = 0.25f),
        ),
    )
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    val look = LocalLook.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = look.dim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = look.bg,
                checkedTrackColor = look.accent,
                uncheckedThumbColor = look.dim,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = look.dim,
            ),
        )
    }
}

/**
 * [applyOnRelease] keeps the drag local and only reports the value when the finger lifts. Use it
 * for settings that relayout the whole screen (like text size), which would otherwise move the
 * slider out from under the finger mid-drag.
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    format: (Float) -> String = { "%.2f".format(it) },
    steps: Int = 0,
    applyOnRelease: Boolean = false,
) {
    val look = LocalLook.current
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(format(shown), style = MaterialTheme.typography.labelMedium, color = look.accent)
        }
        Slider(
            value = shown,
            onValueChange = { if (applyOnRelease) dragging = it else onChange(it) },
            onValueChangeFinished = { dragging?.let(onChange); dragging = null },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = look.accent,
                activeTrackColor = look.accent,
                inactiveTrackColor = look.dim.copy(alpha = 0.3f),
                activeTickColor = look.bg,
                inactiveTickColor = look.dim,
            ),
        )
    }
}

enum class Status { OK, WARN, FAIL, INFO }

/** "[ OK ] message" log-style status line. */
@Composable
fun StatusLine(status: Status, text: String, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val (tag, color) = when (status) {
        Status.OK -> " OK " to look.ok
        Status.WARN -> "WARN" to look.warn
        Status.FAIL -> "FAIL" to look.fail
        Status.INFO -> "INFO" to look.signal
    }
    Row(modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text("[$tag]", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = look.ink)
    }
}

/** A key: value row in mono, used by the tag inspector and device info. */
@Composable
fun KvRow(key: String, value: String, keepCase: Boolean = false, onClick: (() -> Unit)? = null) {
    val look = LocalLook.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 3.dp),
    ) {
        Text(if (keepCase) key else key.uppercase(), style = MaterialTheme.typography.labelMedium, color = look.dim, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = look.mono), color = look.ink, modifier = Modifier.weight(1f))
    }
}

/** Blinking block cursor, for screen titles. */
@Composable
fun BlinkCursor(color: Color = LocalLook.current.accent) {
    if (!LocalLook.current.config.cursor) return
    val t = rememberInfiniteTransition(label = "cursor")
    val a by t.animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "blink")
    Box(Modifier.padding(start = 4.dp).size(width = 10.dp, height = 18.dp).alpha(if (a > 0.5f) 1f else 0f).background(color))
}

/** Row of buttons that share the width. */
@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun Dim(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalLook.current.dim, modifier = modifier)
}

@Composable
fun Tinted(color: Color, content: @Composable () -> Unit) = CompositionLocalProvider(LocalContentColor provides color, content = content)
