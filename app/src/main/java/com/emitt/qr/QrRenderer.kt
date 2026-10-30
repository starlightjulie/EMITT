package com.emitt.qr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import kotlin.math.max
import kotlin.math.roundToInt

/** A rendered code plus what we learned while drawing it. */
class RenderedQr(val bitmap: Bitmap, val version: Int, val modules: Int)

object QrRenderer {

    /** The effective error correction: a centre badge covers modules, so it forces at least Q. */
    fun effectiveEc(style: QrStyle): Ec =
        if (style.badge != Badge.NONE && style.ec.ordinal < Ec.Q.ordinal) Ec.Q else style.ec

    fun encode(text: String, ec: Ec): Pair<ByteMatrix, Int> {
        val level = when (ec) {
            Ec.L -> ErrorCorrectionLevel.L
            Ec.M -> ErrorCorrectionLevel.M
            Ec.Q -> ErrorCorrectionLevel.Q
            Ec.H -> ErrorCorrectionLevel.H
        }
        val code = Encoder.encode(text, level, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
        return code.matrix to code.version.versionNumber
    }

    /** Draws [text] as a [sizePx]-square bitmap, or throws [WriterException] if it can't fit in a QR code. */
    @Throws(WriterException::class)
    fun render(text: String, style: QrStyle, sizePx: Int): RenderedQr {
        val (m, version) = encode(text, effectiveEc(style))
        val n = m.width
        val total = n + style.quietZone * 2
        val cell = sizePx.toFloat() / total
        val origin = style.quietZone * cell

        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (!style.transparentBg) c.drawColor(style.bg.toInt())

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.fg.toInt()
            shader = when (style.gradient) {
                Gradient.NONE -> null
                Gradient.LINEAR -> LinearGradient(origin, origin, origin + n * cell, origin + n * cell, style.fg.toInt(), style.fg2.toInt(), Shader.TileMode.CLAMP)
                Gradient.RADIAL -> RadialGradient(sizePx / 2f, sizePx / 2f, n * cell * 0.7f, style.fg.toInt(), style.fg2.toInt(), Shader.TileMode.CLAMP)
            }
        }

        // Modules hidden under the badge (with a one-module margin)
        val hole = if (style.badge != Badge.NONE) {
            var b = (n * style.badgeSize.coerceIn(0.12f, 0.3f)).roundToInt()
            if (b % 2 == 0) b++
            val start = (n - b) / 2
            start - 1..start + b
        } else {
            IntRange.EMPTY
        }

        fun dark(x: Int, y: Int) = x in 0 until n && y in 0 until n && m[x, y].toInt() == 1 && !isFinder(x, y, n)

        val s = style.moduleScale.coerceIn(0.6f, 1f)
        val inset = cell * (1 - s) / 2
        val path = Path()
        val r = RectF()
        for (y in 0 until n) for (x in 0 until n) {
            if (!dark(x, y) || (x in hole && y in hole)) continue
            val left = origin + x * cell
            val top = origin + y * cell
            r.set(left + inset, top + inset, left + cell - inset, top + cell - inset)
            when (style.modules) {
                ModuleShape.SQUARE -> path.addRect(RectF(left - 0.01f, top - 0.01f, left + cell + 0.01f, top + cell + 0.01f).takeIf { s == 1f } ?: r, Path.Direction.CW)
                ModuleShape.ROUNDED -> path.addRoundRect(r, cell * 0.3f * s, cell * 0.3f * s, Path.Direction.CW)
                ModuleShape.DOTS -> path.addCircle(r.centerX(), r.centerY(), r.width() / 2, Path.Direction.CW)
                ModuleShape.DIAMOND -> {
                    path.moveTo(r.centerX(), r.top); path.lineTo(r.right, r.centerY())
                    path.lineTo(r.centerX(), r.bottom); path.lineTo(r.left, r.centerY()); path.close()
                }
                ModuleShape.FLUID, ModuleShape.BARS -> {
                    path.addCircle(r.centerX(), r.centerY(), r.width() / 2, Path.Direction.CW)
                    val half = r.width() / 2
                    if (dark(x, y + 1) && !(x in hole && y + 1 in hole)) {
                        path.addRect(r.left, r.centerY(), r.right, r.centerY() + cell, Path.Direction.CW)
                    }
                    if (style.modules == ModuleShape.FLUID && dark(x + 1, y) && !(x + 1 in hole && y in hole)) {
                        path.addRect(r.centerX(), r.centerY() - half, r.centerX() + cell, r.centerY() + half, Path.Direction.CW)
                    }
                }
            }
        }
        c.drawPath(path, fill)

        // Finder eyes
        val outer = Paint(fill).apply { style.eyeOuter?.let { shader = null; color = it.toInt() } }
        val inner = Paint(fill).apply { style.eyeInner?.let { shader = null; color = it.toInt() } }
        listOf(0 to 0, n - 7 to 0, 0 to n - 7).forEachIndexed { i, (ex, ey) ->
            drawEye(c, style.eyes, origin + ex * cell, origin + ey * cell, cell, i, outer, inner)
        }

        if (style.badge != Badge.NONE) drawBadge(c, style, sizePx / 2f, sizePx / 2f, (hole.last - hole.first - 1) * cell, fill)
        return RenderedQr(bmp, version, n)
    }

    private fun isFinder(x: Int, y: Int, n: Int) =
        (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)

    private fun drawEye(c: Canvas, shape: EyeShape, left: Float, top: Float, cell: Float, corner: Int, outer: Paint, inner: Paint) {
        val o = RectF(left, top, left + 7 * cell, top + 7 * cell)
        val h = RectF(left + cell, top + cell, left + 6 * cell, top + 6 * cell)
        val i = RectF(left + 2 * cell, top + 2 * cell, left + 5 * cell, top + 5 * cell)
        val ring = Path().apply { fillType = Path.FillType.EVEN_ODD }
        val dot = Path()
        when (shape) {
            EyeShape.SQUARE -> {
                ring.addRect(o, Path.Direction.CW); ring.addRect(h, Path.Direction.CW)
                dot.addRect(i, Path.Direction.CW)
            }
            EyeShape.ROUNDED -> {
                ring.addRoundRect(o, cell * 2, cell * 2, Path.Direction.CW); ring.addRoundRect(h, cell * 1.3f, cell * 1.3f, Path.Direction.CW)
                dot.addRoundRect(i, cell * 0.8f, cell * 0.8f, Path.Direction.CW)
            }
            EyeShape.CIRCLE -> {
                ring.addOval(o, Path.Direction.CW); ring.addOval(h, Path.Direction.CW)
                dot.addOval(i, Path.Direction.CW)
            }
            EyeShape.LEAF -> {
                // Round the two corners that point away from the code's centre diagonal
                fun radii(big: Float) = when (corner) {
                    1 -> floatArrayOf(0f, 0f, big, big, 0f, 0f, big, big)
                    else -> floatArrayOf(big, big, 0f, 0f, big, big, 0f, 0f)
                }
                ring.addRoundRect(o, radii(cell * 3f), Path.Direction.CW); ring.addRoundRect(h, radii(cell * 2.2f), Path.Direction.CW)
                dot.addRoundRect(i, radii(cell * 1.4f), Path.Direction.CW)
            }
        }
        c.drawPath(ring, outer)
        c.drawPath(dot, inner)
    }

    private fun drawBadge(c: Canvas, style: QrStyle, cx: Float, cy: Float, size: Float, ink: Paint) {
        val half = size / 2
        val box = RectF(cx - half, cy - half, cx + half, cy + half)
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (style.transparentBg) Color.WHITE else style.bg.toInt() }
        c.drawRoundRect(box, size * 0.18f, size * 0.18f, plate)
        when (style.badge) {
            Badge.GLYPH -> drawGlyph(c, box, ink)
            Badge.TEXT -> {
                val text = style.badgeText.take(6).ifBlank { "EMITT" }
                val p = Paint(ink).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER }
                p.textSize = size * 0.8f
                p.textSize = size * 0.8f * minOf(1f, size * 0.86f / max(1f, p.measureText(text)))
                val fm = p.fontMetrics
                c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2, p)
            }
            Badge.IMAGE -> style.badgeImage?.let { BitmapFactory.decodeFile(it) }?.let { img ->
                val pad = size * 0.08f
                val dst = RectF(box.left + pad, box.top + pad, box.right - pad, box.bottom - pad)
                val sc = minOf(dst.width() / img.width, dst.height() / img.height)
                val w = img.width * sc
                val hgt = img.height * sc
                c.drawBitmap(img, null, RectF(cx - w / 2, cy - hgt / 2, cx + w / 2, cy + hgt / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            Badge.NONE -> Unit
        }
    }

    /** The EMITT bracketed-emitter mark, scaled into [box]. */
    fun drawGlyph(c: Canvas, box: RectF, ink: Paint) {
        val k = box.width() / 84f // the mark spans 22..98 of a 120 grid, plus padding
        fun x(v: Float) = box.left + (v - 18f) * k
        fun y(v: Float) = box.top + (v - 18f) * k
        c.drawRect(x(50f), y(50f), x(70f), y(70f), ink)
        val stroke = Paint(ink).apply { style = Paint.Style.STROKE; strokeWidth = 6f * k; strokeCap = Paint.Cap.SQUARE }
        val rings = listOf(
            floatArrayOf(42f, 48f, 42f, 42f, 48f, 42f) to 255,
            floatArrayOf(32f, 42f, 32f, 32f, 42f, 32f) to 150,
            floatArrayOf(22f, 36f, 22f, 22f, 36f, 22f) to 80,
        )
        for ((pts, alpha) in rings) {
            stroke.alpha = alpha
            val p = Path()
            for (rot in 0 until 4) {
                fun pt(px: Float, py: Float): Pair<Float, Float> = when (rot) {
                    0 -> px to py
                    1 -> 120 - py to px
                    2 -> 120 - px to 120 - py
                    else -> py to 120 - px
                }
                val a = pt(pts[0], pts[1]); val b = pt(pts[2], pts[3]); val d = pt(pts[4], pts[5])
                p.moveTo(x(a.first), y(a.second)); p.lineTo(x(b.first), y(b.second)); p.lineTo(x(d.first), y(d.second))
            }
            c.drawPath(p, stroke)
        }
    }

    /** WCAG contrast ratio between two ARGB colours, 1..21. */
    fun contrast(a: Long, b: Long): Double = ColorUtils.calculateContrast(
        ColorUtils.setAlphaComponent(a.toInt(), 255),
        ColorUtils.setAlphaComponent(b.toInt(), 255),
    )

    fun luminance(c: Long): Double = ColorUtils.calculateLuminance(c.toInt() or 0xFF000000.toInt())
}
