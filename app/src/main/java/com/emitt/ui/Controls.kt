package com.emitt.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.emitt.ui.theme.ControlStyle
import com.emitt.ui.theme.LocalLook
import com.emitt.ui.theme.reduceMotion
import kotlin.math.roundToInt

/*
 * Switches, sliders and progress bars in the user's chosen control style:
 *  - Classic: Material 3.
 *  - Blade: a long hexagon with a hatched half that slides across (sketched by a friend of EMITT).
 *  - Segment: LED segments that light up in sequence, like a level meter.
 *  - Bracket: terminal brackets with a block cursor.
 */

@Composable
fun StyledSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val look = LocalLook.current
    val style = look.config.controls
    if (style == ControlStyle.CLASSIC) {
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
        return
    }
    val still = reduceMotion()
    val pos by animateFloatAsState(
        if (checked) 1f else 0f,
        when {
            still -> snap()
            style == ControlStyle.SEGMENT -> tween(260)
            else -> spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "switch",
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val on = look.accent
    val off = look.dim
    Canvas(
        Modifier.size(width = if (style == ControlStyle.BLADE) 68.dp else 54.dp, height = 26.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
    ) {
        mirrored(rtl) {
            val colour = lerp(off, on, pos.coerceIn(0f, 1f))
            when (style) {
                ControlStyle.BLADE -> bladeSwitch(pos, colour, off)
                ControlStyle.SEGMENT -> segmentSwitch(pos, on, off)
                else -> bracketSwitch(pos, colour, off)
            }
        }
    }
}

/** A slider in the user's control style. [onFinished] fires when the finger lifts. */
@Composable
fun StyledSlider(
    value: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
) {
    val look = LocalLook.current
    val style = look.config.controls
    if (style == ControlStyle.CLASSIC) {
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
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
        return
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val span = range.endInclusive - range.start
    fun snapValue(v: Float): Float {
        val c = v.coerceIn(range.start, range.endInclusive)
        if (steps <= 0) return c
        val k = ((c - range.start) / span * (steps + 1)).roundToInt()
        return range.start + span * k / (steps + 1)
    }
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinished)
    var width by remember { mutableFloatStateOf(1f) }
    var x by remember { mutableFloatStateOf(0f) }
    fun valueAt(px: Float): Float {
        val f = (px / width).coerceIn(0f, 1f)
        return snapValue(range.start + span * (if (rtl) 1f - f else f))
    }
    val frac = ((value - range.start) / span).coerceIn(0f, 1f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps)
                setProgress { v -> change(snapValue(v)); finish(); true }
            }
            .pointerInput(rtl, range, steps) { detectTapGestures { change(valueAt(it.x)); finish() } }
            .draggable(
                rememberDraggableState { d -> x += d; change(valueAt(x)) },
                Orientation.Horizontal,
                onDragStarted = { x = it.x; change(valueAt(x)) },
                onDragStopped = { finish() },
            ),
    ) {
        width = size.width
        mirrored(rtl) { styledBar(style, frac, look.accent, look.dim, thumb = true) }
    }
}

/** A determinate progress bar (Flipper transfers) in the user's control style. */
@Composable
fun StyledProgress(progress: Float, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val style = look.config.controls
    if (style == ControlStyle.CLASSIC) {
        LinearProgressIndicator(progress = { progress }, modifier.fillMaxWidth(), color = look.accent)
        return
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.fillMaxWidth().height(22.dp)) {
        mirrored(rtl) { styledBar(style, progress.coerceIn(0f, 1f), look.accent, look.dim, thumb = false) }
    }
}

private inline fun DrawScope.mirrored(rtl: Boolean, block: DrawScope.() -> Unit) {
    if (!rtl) block() else withTransform({ scale(-1f, 1f, Offset(size.width / 2, size.height / 2)) }, block)
}

// ---- Shared geometry ----

/** A long hexagon: flat top and bottom, pointed ends. */
private fun hexagon(left: Float, top: Float, w: Float, h: Float): Path {
    val c = h * 0.55f
    return Path().apply {
        moveTo(left + c, top); lineTo(left + w - c, top); lineTo(left + w, top + h / 2)
        lineTo(left + w - c, top + h); lineTo(left + c, top + h); lineTo(left, top + h / 2); close()
    }
}

/** Diagonal hatching across [x0, x1], like pencil shading. */
private fun DrawScope.hatch(x0: Float, x1: Float, top: Float, h: Float, colour: Color) {
    val gap = 4.5.dp.toPx()
    val stroke = 1.3.dp.toPx()
    var x = x0 - h
    while (x < x1) {
        drawLine(colour, Offset(x, top + h), Offset(x + h, top), stroke)
        x += gap
    }
}

private fun DrawScope.styledBar(style: ControlStyle, frac: Float, on: Color, off: Color, thumb: Boolean) {
    when (style) {
        ControlStyle.BLADE -> bladeBar(frac, on, off, thumb)
        ControlStyle.SEGMENT -> segmentBar(frac, on, off, thumb)
        else -> bracketBar(frac, on, off, thumb)
    }
}

// ---- Blade ----

private fun DrawScope.bladeSwitch(pos: Float, colour: Color, off: Color) {
    val stroke = 1.6.dp.toPx()
    val inset = stroke
    val w = size.width - inset * 2
    val h = size.height - inset * 2
    val shape = hexagon(inset, inset, w, h)
    val half = w / 2
    val x0 = inset + half * pos
    clipPath(shape) {
        drawRect(colour.copy(alpha = 0.14f), Offset(x0, 0f), Size(half, size.height))
        clipRect(x0, 0f, x0 + half, size.height) { hatch(x0, x0 + half, inset, h, colour) }
    }
    drawPath(shape, lerp(off, colour, 0.6f), style = Stroke(stroke))
    // The divider: crosses the bar with a little overshoot, like the pencil line it came from
    val over = 3.dp.toPx()
    drawLine(colour, Offset(inset + half, inset - over), Offset(inset + half, inset + h + over), stroke * 1.2f)
}

private fun DrawScope.bladeBar(frac: Float, on: Color, off: Color, thumb: Boolean) {
    val stroke = 1.4.dp.toPx()
    val h = (if (thumb) 16 else 14).dp.toPx()
    val top = (size.height - h) / 2
    val pad = 4.dp.toPx()
    val w = size.width - pad * 2
    val shape = hexagon(pad, top, w, h)
    val fx = pad + w * frac
    clipPath(shape) {
        drawRect(on.copy(alpha = 0.16f), Offset(pad, top), Size(fx - pad, h))
        clipRect(pad, top, fx, top + h) { hatch(pad, fx, top, h, on) }
    }
    drawPath(shape, off.copy(alpha = 0.7f), style = Stroke(stroke))
    if (thumb) {
        val over = 5.dp.toPx()
        drawLine(on, Offset(fx, top - over), Offset(fx, top + h + over), 2.2.dp.toPx())
    }
}

// ---- Segment ----

private fun DrawScope.segmentSwitch(pos: Float, on: Color, off: Color) {
    val n = 4
    val gap = 3.dp.toPx()
    val segW = (size.width - gap * (n - 1)) / n
    val h = size.height * 0.72f
    val top = (size.height - h) / 2
    for (i in 0 until n) {
        val x = i * (segW + gap)
        // Each segment lights a moment after the one before it
        val lit = ((pos * (n + 1)) - i).coerceIn(0f, 1f)
        drawRect(off.copy(alpha = 0.18f), Offset(x, top), Size(segW, h))
        if (lit > 0f) drawRect(on.copy(alpha = lit), Offset(x, top), Size(segW, h))
        drawRect(lerp(off, on, lit).copy(alpha = 0.8f), Offset(x, top), Size(segW, h), style = Stroke(1.dp.toPx()))
    }
    if (pos < 0.02f) drawRect(off, Offset(0f, top), Size(segW, h)) // "off" marker in the first slot
}

private fun DrawScope.segmentBar(frac: Float, on: Color, off: Color, thumb: Boolean) {
    val segW = 5.dp.toPx()
    val gap = 2.5.dp.toPx()
    val n = ((size.width + gap) / (segW + gap)).toInt().coerceAtLeast(4)
    val step = (size.width - segW) / (n - 1)
    val h = 14.dp.toPx()
    val top = (size.height - h) / 2
    val lit = (frac * n).roundToInt()
    for (i in 0 until n) {
        val x = i * step
        val last = thumb && i == lit - 1
        val grow = if (last) 4.dp.toPx() else 0f
        val c = if (i < lit) on.copy(alpha = 0.55f + 0.45f * (i + 1f) / n) else off.copy(alpha = 0.22f)
        drawRect(if (last) on else c, Offset(x, top - grow), Size(segW, h + grow * 2))
    }
}

// ---- Bracket ----

private fun DrawScope.brackets(colour: Color, top: Float, h: Float, stroke: Float) {
    val arm = 5.dp.toPx()
    val l = stroke / 2
    val r = size.width - stroke / 2
    drawLine(colour, Offset(l, top), Offset(l, top + h), stroke)
    drawLine(colour, Offset(l, top), Offset(l + arm, top), stroke)
    drawLine(colour, Offset(l, top + h), Offset(l + arm, top + h), stroke)
    drawLine(colour, Offset(r, top), Offset(r, top + h), stroke)
    drawLine(colour, Offset(r, top), Offset(r - arm, top), stroke)
    drawLine(colour, Offset(r, top + h), Offset(r - arm, top + h), stroke)
}

private fun DrawScope.bracketSwitch(pos: Float, colour: Color, off: Color) {
    val stroke = 1.8.dp.toPx()
    brackets(colour, 0f, size.height, stroke)
    val pad = 5.dp.toPx()
    val blockW = 16.dp.toPx()
    val blockH = size.height - pad * 2
    // Dotted track the cursor runs along
    val dot = 1.5.dp.toPx()
    var x = pad + blockW / 2
    while (x < size.width - pad - blockW / 2) {
        drawCircle(off.copy(alpha = 0.45f), dot / 2, Offset(x, size.height / 2))
        x += 5.dp.toPx()
    }
    val bx = pad + (size.width - pad * 2 - blockW) * pos
    drawRect(colour, Offset(bx, pad), Size(blockW, blockH))
}

private fun DrawScope.bracketBar(frac: Float, on: Color, off: Color, thumb: Boolean) {
    val stroke = 1.6.dp.toPx()
    val h = 18.dp.toPx()
    val top = (size.height - h) / 2
    brackets(off.copy(alpha = 0.8f), top, h, stroke)
    val pad = 8.dp.toPx()
    val w = size.width - pad * 2
    val fx = pad + w * frac
    val cy = size.height / 2
    // Filled part solid, the rest dashed
    drawLine(on, Offset(pad, cy), Offset(fx, cy), 3.dp.toPx())
    var x = fx + 3.dp.toPx()
    while (x < pad + w) {
        drawLine(off.copy(alpha = 0.45f), Offset(x, cy), Offset((x + 4.dp.toPx()).coerceAtMost(pad + w), cy), 1.5.dp.toPx())
        x += 7.dp.toPx()
    }
    if (thumb) {
        val bw = 8.dp.toPx()
        drawRect(on, Offset((fx - bw / 2).coerceIn(pad - bw / 2, pad + w - bw / 2), top + 3.dp.toPx()), Size(bw, h - 6.dp.toPx()))
    }
}
