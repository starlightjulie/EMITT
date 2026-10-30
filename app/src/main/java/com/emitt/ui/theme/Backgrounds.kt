package com.emitt.ui.theme

import android.graphics.Typeface
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Seconds of animation time, advanced only while the app is on screen. */
@Composable
fun rememberClock(speed: Float, running: Boolean): MutableFloatState {
    val t = remember { mutableFloatStateOf(3f) }
    val lifecycle = LocalLifecycleOwner.current
    val s by rememberUpdatedState(speed)
    if (running) {
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var last = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (last != 0L) t.floatValue += ((now - last) / 1e9f).coerceAtMost(0.05f) * s
                        last = now
                    }
                }
            }
        }
    }
    return t
}

@Composable
fun reduceMotion(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Full-screen animated backdrop. Each frame is a redraw only: no recomposition or layout. */
@Composable
fun EmittBackground(modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val cfg = look.config
    val style = remember(cfg.background) {
        if (cfg.background == BgStyle.RANDOM) BgStyle.entries.filter { it != BgStyle.NONE && it != BgStyle.RANDOM }.random() else cfg.background
    }
    val still = reduceMotion()
    val clock = rememberClock(cfg.bgSpeed, style != BgStyle.NONE && !still)
    val scene = remember(style) { Scene(style) }
    Spacer(
        modifier.fillMaxSize().drawBehind {
            drawRect(look.bg)
            if (style == BgStyle.NONE) return@drawBehind
            val t = clock.floatValue
            val (c1, c2) = when (cfg.bgColour) {
                BgColour.DUO -> look.accent to look.signal
                BgColour.ACCENT -> look.accent to look.accent
                BgColour.SIGNAL -> look.signal to look.signal
                BgColour.INK -> look.ink to look.ink
                BgColour.CYCLE -> Color.hsv((t * 14f) % 360f, 0.75f, 1f) to Color.hsv((t * 14f + 70f) % 360f, 0.75f, 1f)
            }
            val a = cfg.bgIntensity.coerceIn(0f, 1f) * if (look.dark) 1f else 0.8f
            with(scene) { draw(t, c1, c2, look.bg, look.ink, a, cfg.bgDensity.coerceIn(0.25f, 2f)) }
        },
    )
}

/** Scanlines, vignette and flicker drawn over the whole UI. Touches pass straight through. */
@Composable
fun CrtOverlay(modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val cfg = look.config
    if (!cfg.scanlines && !cfg.vignette && !cfg.flicker) return
    val clock = rememberClock(1f, cfg.flicker && !reduceMotion())
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val pitch = max(3, 3.dp.roundToPx())
            val lines: ImageBitmap = android.graphics.Bitmap.createBitmap(1, pitch, android.graphics.Bitmap.Config.ARGB_8888).apply {
                val shade = if (look.dark) android.graphics.Color.BLACK else look.ink.toArgb()
                setPixel(0, pitch - 1, shade)
                setPixel(0, pitch - 2, androidx.core.graphics.ColorUtils.setAlphaComponent(shade, 90))
            }.asImageBitmap()
            val scan = ShaderBrush(ImageShader(lines, TileMode.Repeated, TileMode.Repeated))
            val vig = Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = if (look.dark) 0.6f else 0.18f),
                center = Offset(size.width / 2, size.height / 2),
                radius = hypot(size.width, size.height) / 2,
            )
            onDrawBehind {
                if (cfg.scanlines) drawRect(scan, alpha = cfg.scanlineStrength.coerceIn(0f, 1f) * if (look.dark) 0.7f else 0.25f)
                if (cfg.vignette) drawRect(vig)
                if (cfg.flicker) {
                    val t = clock.floatValue
                    val f = (sin(t * 47f) * sin(t * 13f) + 1f) / 2f
                    drawRect(look.bg, alpha = 0.035f * f)
                }
            }
        },
    )
}

/** Seeded per-style data in flat arrays, so drawing allocates nothing per frame. */
private class Scene(val style: BgStyle) {
    val n = 400
    private val rnd = Random(style.ordinal * 97 + 13)
    val x = FloatArray(n) { rnd.nextFloat() }
    val y = FloatArray(n) { rnd.nextFloat() }
    val sp = FloatArray(n) { 0.4f + rnd.nextFloat() * 0.8f }
    val ph = FloatArray(n) { rnd.nextFloat() }
    val path = Path()
    val ridge = Path()
    val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    val glyphs = "0123456789ABCDEF<>/[]{}#*+=".toCharArray()

    // Circuit traces: rebuilt when the size or density changes
    var traceKey = Triple(0f, 0f, 0f)
    var traces: List<FloatArray> = emptyList()
    var traceLen: FloatArray = FloatArray(0)

    fun DrawScope.draw(t: Float, c1: Color, c2: Color, bg: Color, ink: Color, a: Float, d: Float) {
        val w = size.width
        val h = size.height
        when (style) {
            BgStyle.COMETS -> comets(w, h, t, c1, c2, ink, a, d)
            BgStyle.SIGNAL -> signal(w, h, t, c1, c2, a, d)
            BgStyle.CODE_RAIN -> codeRain(w, h, t, c1, ink, a, d)
            BgStyle.SCOPE -> scope(w, h, t, c1, c2, a, d)
            BgStyle.RADAR -> radar(w, h, t, c1, c2, a, d)
            BgStyle.DOT_MATRIX -> dotMatrix(w, h, t, c1, c2, a, d)
            BgStyle.CIRCUIT -> circuit(w, h, t, c1, c2, a, d)
            BgStyle.WARP -> warp(w, h, t, c1, c2, a, d)
            BgStyle.SPECTRUM -> spectrum(w, h, t, c1, c2, a, d)
            BgStyle.PULSAR -> pulsar(w, h, t, c1, c2, bg, a, d)
            BgStyle.NONE, BgStyle.RANDOM -> Unit
        }
    }

    /** Same as TwinnDroid's meteors: a twinkling starfield with comets streaking across now and then. */
    private fun DrawScope.comets(w: Float, h: Float, t: Float, c1: Color, c2: Color, ink: Color, a: Float, d: Float) {
        val dp = 1.dp.toPx()
        val stars = (140 * d).toInt().coerceAtMost(n)
        for (i in 0 until stars) {
            val r = (0.4f + ph[i] * 0.9f) * dp
            val tw = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * (0.5f + sp[i]) + ph[i] * 6.28f))
            drawCircle(if (i % 3 == 0) c1 else ink, r, Offset(x[i] * w, y[i] * h), alpha = tw * 0.7f * a)
        }
        val count = (4 * d).toInt().coerceIn(2, 8)
        for (slot in 0 until count) comet(w, h, t, slot, period = 4.5f + slot * 1.3f, c2, ink, a, dp)
    }

    private fun DrawScope.comet(w: Float, h: Float, t: Float, slot: Int, period: Float, tail: Color, head: Color, a: Float, dp: Float) {
        fun hash(cycle: Float, salt: Float): Float { val v = sin(slot * 12.9898f + cycle * 78.233f + salt * 37.719f) * 43758.547f; return v - floor(v) }
        val shifted = t + slot * period * 0.37f
        val cyc = floor(shifted / period)
        val local = shifted - cyc * period
        val dur = 1.1f
        if (local > dur) return
        val pr = local / dur
        // Heads down and to the left or right, at a shallow angle
        val left = hash(cyc, 7f) > 0.5f
        val ang = ((if (left) 140f else 25f) + hash(cyc, 5f) * 15f) * (PI.toFloat() / 180f)
        val dx = cos(ang)
        val dy = sin(ang)
        val sx = (if (left) 0.45f + hash(cyc, 3f) * 0.55f else hash(cyc, 3f) * 0.55f) * w
        val sy = hash(cyc, 4f) * h * 0.4f
        val travel = (260f + hash(cyc, 6f) * 160f) * dp
        val hx = sx + dx * travel * pr
        val hy = sy + dy * travel * pr
        val len = 120f * dp * (1f - abs(pr - 0.5f))
        val tx = hx - dx * len
        val ty = hy - dy * len
        val fade = sin(pr * PI.toFloat())
        drawLine(
            Brush.linearGradient(listOf(Color.Transparent, lerp(head, tail, 0.35f).copy(alpha = 0.9f * fade * a)), Offset(tx, ty), Offset(hx, hy)),
            Offset(tx, ty), Offset(hx, hy), 1.6f * dp, androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawCircle(head, 1.6f * dp, Offset(hx, hy), alpha = fade * a)
    }

    private fun DrawScope.brackets(cx: Float, cy: Float, s: Float, color: Color, width: Float) {
        val arm = s * 0.38f
        path.reset()
        path.moveTo(cx - s, cy - s + arm); path.lineTo(cx - s, cy - s); path.lineTo(cx - s + arm, cy - s)
        path.moveTo(cx + s - arm, cy - s); path.lineTo(cx + s, cy - s); path.lineTo(cx + s, cy - s + arm)
        path.moveTo(cx + s, cy + s - arm); path.lineTo(cx + s, cy + s); path.lineTo(cx + s - arm, cy + s)
        path.moveTo(cx - s + arm, cy + s); path.lineTo(cx - s, cy + s); path.lineTo(cx - s, cy + s - arm)
        drawPath(path, color, style = Stroke(width))
    }

    private fun DrawScope.signal(w: Float, h: Float, t: Float, c1: Color, c2: Color, a: Float, d: Float) {
        val count = (4 * d).toInt().coerceIn(1, 10)
        val maxR = min(w, h) * 0.42f
        for (i in 0 until count) {
            val cx = (0.12f + x[i] * 0.76f) * w
            val cy = (0.08f + y[i] * 0.84f) * h
            val col = if (i % 2 == 0) c1 else c2
            for (k in 0 until 3) {
                val p = ((t * 0.22f * sp[i] + ph[i] + k / 3f) % 1f)
                val s = 8.dp.toPx() + p * maxR * (0.6f + sp[i] * 0.4f)
                brackets(cx, cy, s, col.copy(alpha = a * (1 - p).pow(1.6f) * 0.8f), 2.dp.toPx() * (1.4f - p))
            }
            val core = 5.dp.toPx()
            drawRect(col.copy(alpha = a), Offset(cx - core, cy - core), Size(core * 2, core * 2))
        }
    }

    private fun DrawScope.codeRain(w: Float, h: Float, t: Float, c1: Color, ink: Color, a: Float, d: Float) {
        val cw = 16.dp.toPx() / d.coerceAtLeast(0.5f)
        val lh = 16.dp.toPx()
        text.textSize = 13.dp.toPx()
        val cols = (w / cw).toInt() + 1
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (i in 0 until cols) {
                val k = i % n
                val len = 8 + (ph[k] * 16).toInt()
                val span = h + len * lh
                val head = (t * sp[k] * h * 0.16f + ph[(k + 7) % n] * span) % span
                val cx = i * cw + 2.dp.toPx()
                for (j in 0 until len) {
                    val yy = head - j * lh
                    if (yy < -lh || yy > h + lh) continue
                    val alpha = a * (1f - j.toFloat() / len)
                    if (alpha < 0.03f) continue
                    val g = glyphs[abs((i * 31 + floor(yy / lh).toInt() * 17 + floor(t * 5f + j * 0.37f).toInt()) % glyphs.size)]
                    text.color = (if (j == 0) ink else c1).copy(alpha = if (j == 0) a else alpha * 0.8f).toArgb()
                    nc.drawText(charArrayOf(g), 0, 1, cx, yy, text)
                }
            }
        }
    }

    private fun DrawScope.scope(w: Float, h: Float, t: Float, c1: Color, c2: Color, a: Float, d: Float) {
        val grid = c2.copy(alpha = a * 0.12f)
        for (i in 1 until 10) drawLine(grid, Offset(w * i / 10f, 0f), Offset(w * i / 10f, h), 1f)
        for (i in 1 until 16) drawLine(grid, Offset(0f, h * i / 16f), Offset(w, h * i / 16f), 1f)
        drawLine(c2.copy(alpha = a * 0.25f), Offset(w / 2, 0f), Offset(w / 2, h), 1.5f)
        val waves = (3 * d).toInt().coerceIn(1, 6)
        for (i in 0 until waves) {
            val cy = h * (0.18f + 0.64f * ((i + 0.5f) / waves))
            val amp = h * 0.05f * (1 + i * 0.25f)
            val k = 2 * PI.toFloat() * (1.2f + i * 0.6f) / w
            val om = 1.1f + i * 0.45f
            val env = 0.6f + 0.4f * sin(t * 0.6f + i)
            path.reset()
            val steps = 120
            for (s in 0..steps) {
                val xx = w * s / steps
                val yy = cy + amp * env * (sin(k * xx + t * om + ph[i] * 6f) + 0.3f * sin(k * 3.1f * xx - t * om * 1.7f))
                if (s == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
            }
            val col = if (i % 2 == 0) c1 else c2
            drawPath(path, col.copy(alpha = a * 0.18f), style = Stroke(7.dp.toPx()))
            drawPath(path, col.copy(alpha = a * 0.9f), style = Stroke(1.6.dp.toPx()))
        }
    }

    private fun DrawScope.radar(w: Float, h: Float, t: Float, c1: Color, c2: Color, a: Float, d: Float) {
        val c = Offset(w / 2, h * 0.52f)
        val r = min(w, h) * 0.46f
        for (i in 1..4) drawCircle(c2.copy(alpha = a * 0.22f), r * i / 4, c, style = Stroke(1.2.dp.toPx()))
        drawLine(c2.copy(alpha = a * 0.18f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
        drawLine(c2.copy(alpha = a * 0.18f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
        val sweep = (t * 55f) % 360f
        for (k in 0 until 28) {
            drawArc(
                c1.copy(alpha = a * 0.32f * (1f - k / 28f)), sweep - (k + 1) * 2.4f, 2.5f, true,
                Offset(c.x - r, c.y - r), Size(r * 2, r * 2),
            )
        }
        val rad = sweep * PI.toFloat() / 180f
        drawLine(c1.copy(alpha = a), c, Offset(c.x + cos(rad) * r, c.y + sin(rad) * r), 2.dp.toPx())
        val blips = (12 * d).toInt()
        for (i in 0 until blips) {
            val ang = ph[i] * 360f
            val age = ((sweep - ang + 360f) % 360f) / 360f
            val b = (1f - age).pow(3f)
            if (b < 0.02f) continue
            val rr = r * (0.15f + 0.8f * x[i])
            val ar = ang * PI.toFloat() / 180f
            val p = Offset(c.x + cos(ar) * rr, c.y + sin(ar) * rr)
            drawCircle(c1.copy(alpha = a * b * 0.3f), 9.dp.toPx(), p)
            drawCircle(c1.copy(alpha = a * b), 3.dp.toPx(), p)
        }
    }

    private fun DrawScope.dotMatrix(w: Float, h: Float, t: Float, c1: Color, c2: Color, a: Float, d: Float) {
        val sp = max(10.dp.toPx(), 22.dp.toPx() / d)
        val dot = 2.6.dp.toPx()
        val cols = (w / sp).toInt() + 1
        val rows = (h / sp).toInt() + 1
        for (j in 0 until rows) for (i in 0 until cols) {
            val wave = sin(i * 0.22f + j * 0.13f - t * 1.4f).coerceAtLeast(0f).pow(6f)
            val hsh = ((i * 73856093) xor (j * 19349663)) and 0xFFFF
            val tw = ((hsh / 65535f + t * 0.08f * (1 + (hsh and 3))) % 1f).let { if (it < 0.035f) 1f - it / 0.035f else 0f }
            val alpha = a * (0.07f + wave * 0.55f + tw * 0.7f)
            val col = lerp(c2, c1, wave)
            drawRect(col.copy(alpha = alpha.coerceAtMost(1f)), Offset(i * sp, j * sp), Size(dot, dot))
        }
    }

    private fun DrawScope.circuit(w: Float, h: Float, t: Float, c1: Color, c2: Color, a: Float, d: Float) {
        val key = Triple(w, h, d)
        if (key != traceKey) {
            traceKey = key
            val r = Random(77)
            val grid = 24.dp.toPx()
            traces = List((14 * d).toInt().coerceAtLeast(4)) {
                val pts = ArrayList<Float>()
                var px = floor(r.nextFloat() * w / grid) * grid
                var py = floor(r.nextFloat() * h / grid) * grid
                pts += px; pts += py
                var dir = r.nextInt(4)
                repeat(4 + r.nextInt(5)) {
                    val len = grid * (2 + r.nextInt(6))
                    when (dir) { 0 -> px += len; 1 -> py += len; 2 -> px -= len; else -> py -= len }
                    pts += px; pts += py
                    dir = (dir + if (r.nextBoolean()) 1 else 3) % 4
                }
                pts.toFloatArray()
            }
            traceLen = FloatArray(traces.size) { i ->
                val p = traces[i]
                var l = 0f
                for (k in 2 until p.size step 2) l += hypot(p[k] - p[k - 2], p[k + 1] - p[k - 1])
                l
            }
        }
        val stroke = 1.4.dp.toPx()
        val pad = 3.dp.toPx()
        traces.forEachIndexed { i, p ->
            path.reset()
            path.moveTo(p[0], p[1])
            for (k in 2 until p.size step 2) path.lineTo(p[k], p[k + 1])
            drawPath(path, c2.copy(alpha = a * 0.22f), style = Stroke(stroke))
            drawRect(c2.copy(alpha = a * 0.5f), Offset(p[0] - pad, p[1] - pad), Size(pad * 2, pad * 2))
            drawCircle(c2.copy(alpha = a * 0.5f), pad, Offset(p[p.size - 2], p[p.size - 1]), style = Stroke(stroke))
            // A pulse travelling along the trace
            var dist = ((t * 90.dp.toPx() * sp[i] + ph[i] * traceLen[i]) % traceLen[i])
            for (k in 2 until p.size step 2) {
                val seg = hypot(p[k] - p[k - 2], p[k + 1] - p[k - 1])
                if (dist <= seg) {
                    val f = if (seg == 0f) 0f else dist / seg
                    val q = Offset(p[k - 2] + (p[k] - p[k - 2]) * f, p[k - 1] + (p[k + 1] - p[k - 1]) * f)
                    drawCircle(c1.copy(alpha = a * 0.25f), 7.dp.toPx(), q)
                    drawCircle(c1.copy(alpha = a), 2.6.dp.toPx(), q)
                    break
                }
                dist -= seg
            }
        }
    }

    private fun DrawScope.warp(w: Float, h: Float, t: Float, c1: Color, c2: Color, a: Float, d: Float) {
        val c = Offset(w / 2, h / 2)
        val maxR = hypot(w, h) / 2
        val stars = (150 * d).toInt().coerceAtMost(n)
        for (i in 0 until stars) {
            val p = (t * 0.18f * sp[i] + ph[i]) % 1f
            val ang = x[i] * 2 * PI.toFloat()
            val r1 = maxR * p.pow(2.2f)
            val r0 = maxR * (p - 0.04f).coerceAtLeast(0f).pow(2.2f)
            val dx = cos(ang); val dy = sin(ang)
            drawLine(
                (if (i % 3 == 0) c1 else c2).copy(alpha = a * p),
                Offset(c.x + dx * r0, c.y + dy * r0), Offset(c.x + dx * r1, c.y + dy * r1),
                (0.6f + 2.2f * p) * 1.dp.toPx(),
            )
        }
    }

    private fun DrawScope.spectrum(w: Float, h: Float, t: Float, c1: Color, c2: Color, a: Float, d: Float) {
        val bars = (28 * d).toInt().coerceIn(8, 64)
        val bw = w / bars
        val seg = 6.dp.toPx()
        val gap = 2.dp.toPx()
        for (i in 0 until bars) {
            val v = (0.45f + 0.3f * sin(t * (2f + sp[i] * 3f) + ph[i] * 6f) + 0.25f * sin(t * (0.7f + sp[i]) + i * 0.45f)).coerceIn(0f, 1f)
            val height = h * 0.34f * v.pow(1.3f)
            var yy = 0f
            while (yy < height) {
                val f = yy / (h * 0.34f)
                val col = lerp(c2, c1, f)
                drawRect(col.copy(alpha = a * 0.75f), Offset(i * bw + gap, h - yy - seg), Size(bw - gap * 2, seg - gap))
                drawRect(col.copy(alpha = a * 0.2f), Offset(i * bw + gap, yy), Size(bw - gap * 2, seg - gap))
                yy += seg
            }
        }
    }

    /**
     * Stacked ridgelines that swell in the middle, like a pulsar's radio trace. The area under each
     * ridge is filled with the background, so nearer ridges hide the ones behind them.
     */
    private fun DrawScope.pulsar(w: Float, h: Float, t: Float, c1: Color, c2: Color, bg: Color, a: Float, d: Float) {
        val rows = (24 * d).toInt().coerceIn(10, 44)
        val steps = 72
        val left = w * 0.08f
        val span = w * 0.84f
        val top = h * 0.2f
        val gap = h * 0.62f / rows
        val amp = gap * 4.2f
        val stroke = 1.4.dp.toPx()
        for (r in 0 until rows) {
            val base = top + r * gap
            path.reset()
            ridge.reset()
            for (s in 0..steps) {
                val u = s.toFloat() / steps
                // Bell-shaped envelope, so the peaks bunch up in the centre
                val env = kotlin.math.exp(-((u - 0.5f) * 4.2f).pow(2))
                val wave = 0.55f * sin(u * 23f + t * (0.9f + sp[r] * 0.6f) + ph[r] * 6.28f) +
                    0.3f * sin(u * 51f - t * 1.7f + r * 0.7f) +
                    0.15f * sin(u * 97f + t * 2.9f + ph[r + 50] * 6.28f)
                val yy = base - env * amp * (0.55f + 0.45f * wave).coerceAtLeast(0f)
                if (s == 0) { path.moveTo(left, yy); ridge.moveTo(left, yy) } else { path.lineTo(left + u * span, yy); ridge.lineTo(left + u * span, yy) }
            }
            path.lineTo(left + span, base + gap)
            path.lineTo(left, base + gap)
            path.close()
            drawPath(path, bg)
            val f = r.toFloat() / (rows - 1)
            drawPath(ridge, lerp(c2, c1, f).copy(alpha = a * (0.35f + 0.55f * f)), style = Stroke(stroke))
        }
    }
}
