package com.ngocdevv.nativecook.blocks.buttons.onairtoggle

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint as NativePaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Coordinates are dp; the host applies its density transform once around the whole scene. */
internal class OnAirToggleArt(private val density: Float, fontScale: Float = 1f) {
    private val lens = NativePaint(NativePaint.ANTI_ALIAS_FLAG or NativePaint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textSize = 10f * fontScale
    }
    private val timer = NativePaint(NativePaint.ANTI_ALIAS_FLAG or NativePaint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.create("monospace", Typeface.NORMAL)
        textSize = 30f * fontScale
    }
    private val bitmapPaint = NativePaint(NativePaint.ANTI_ALIAS_FLAG or NativePaint.FILTER_BITMAP_FLAG)
    private val ambient = shadow(284f, 166f, 74f, 22f)
    private val contact = shadow(296f, 166f, 74f, 3f)
    private val inner = shadow(282f, 130f, 65f, 6f, stroke = 14f)
    private var knobPress = Float.NaN
    private val knobShadow = shadow(104f, 96f, 0f, 9f, oval = true)
    private val lensGlow = textMask("ON AIR", lens, 1.4f, 3f)

    fun draw(
        scope: DrawScope,
        width: Float,
        height: Float,
        frame: OnAirToggleFrame,
        cross: Double,
        time: Double,
        reduced: Boolean,
    ) = with(scope) {
        val cx = width / 2f
        val cy = height * 0.48f
        val x0 = cx - 150f
        val y0 = cy - 74f
        val room = frame.room.toFloat().coerceIn(0f, 1f)
        val g = frame.glow.toFloat().coerceIn(0f, 1f)
        val kx = cx - 76f + 152f * frame.pos.toFloat()
        val chan = onAirArtPath(cx - 134f, cy - 58f, 268f, 116f, 58f)

        drawRect(onAirArtRadial(cx, cy - 80f, max(width, height) * 0.9f,
            onAirArtStops(intArrayOf(0xFBFAF8, 0xEEEBE6, 0xDCD7D0), intArrayOf(0x2A1A1C, 0x120C0E, 0x060405), room),
            floatArrayOf(0f, 0.55f, 1f)), size = Size(width, height))
        if (g > 0.001f) {
            drawRect(onAirArtRadial(kx, cy + 30f, 300f,
                listOf(onAirArtCol(214, 28, 36, 0.3f * g * room), onAirArtCol(214, 28, 36, 0.1f * g * room), onAirArtCol(214, 28, 36, 0f)),
                floatArrayOf(0f, 0.45f, 1f)), size = Size(width, height))
        }
        for (o in floatArrayOf(56f, 128f, 214f)) {
            val color = if (room < 0.5f) onAirArtCol(40, 32, 30, 0.2f * (1f - room * 2f))
                else onAirArtCol(255, 235, 230, 0.15f * (room * 2f - 1f))
            onAirArtRound(color, x0 - o, y0 - o * 0.82f, 300f + 2f * o, 166f + 1.64f * o, 74f + o, Stroke(1f))
        }
        drawMask(scope, ambient, x0 + 8f, y0 + 22f, onAirArtCol(40, 28, 26, 0.22f + 0.25f * room))
        drawMask(scope, contact, x0 + 2f, y0 + 3f, onAirArtCol(20, 14, 12, 0.35f))

        onAirArtRound(onAirArtHorizontal(x0, x0 + 300f,
            onAirArtStops(intArrayOf(0x8D8F92, 0xC4C6C8, 0xE1E2E3, 0xB9BBBE, 0x808285), intArrayOf(0x121112, 0x2A2627, 0x3A3436, 0x2A2627, 0x121112), room),
            floatArrayOf(0f, 0.18f, 0.5f, 0.82f, 1f)), x0, y0, 300f, 166f, 74f)
        onAirArtRound(onAirArtVertical(y0 + 74f, y0 + 166f, listOf(onAirArtCol(0, 0, 0, 0f), onAirArtCol(0, 0, 0, 0.3f))), x0, y0, 300f, 166f, 74f)
        onAirArtRound(onAirArtVertical(y0, y0 + 148f,
            onAirArtStops(intArrayOf(0xFDFDFC, 0xE3E4E5, 0xC3C5C8, 0xEDEEEF, 0xA6A8AB), intArrayOf(0x4A4446, 0x2B2728, 0x1A1718, 0x2E2829, 0x141213), room),
            floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f)), x0, y0, 300f, 148f, 74f)
        clipPath(chan, ClipOp.Difference) {
            onAirArtRound(Brush.sweepGradient(listOf(
                onAirArtCol(255, 255, 255, 0f), onAirArtCol(255, 255, 255, 0.35f), onAirArtCol(255, 255, 255, 0f),
                onAirArtCol(0, 0, 0, 0.12f), onAirArtCol(255, 255, 255, 0f), onAirArtCol(255, 255, 255, 0.3f),
                onAirArtCol(255, 255, 255, 0f), onAirArtCol(0, 0, 0, 0.12f), onAirArtCol(255, 255, 255, 0f)), Offset(cx, cy)),
                x0, y0, 300f, 148f, 74f, alpha = 1f - 0.5f * room)
        }
        repeat(2) { i ->
            val inset = 5f + 5f * i
            onAirArtRound(onAirArtCol(0, 0, 0, 0.16f + 0.2f * room), x0 + inset, y0 + inset, 300f - 2f * inset, 148f - 2f * inset, 74f - inset, Stroke(1f))
            onAirArtRound(onAirArtCol(255, 255, 255, 0.55f - 0.43f * room), x0 + inset, y0 + inset + 0.8f, 300f - 2f * inset, 148f - 2f * inset, 74f - inset, Stroke(1f))
        }
        onAirArtRound(onAirArtVertical(y0, y0 + 74f, listOf(onAirArtCol(255, 255, 255, 0.95f - 0.25f * room), onAirArtCol(255, 255, 255, 0f))),
            x0 + 0.75f, y0 + 0.75f, 298.5f, 146.5f, 73.25f, Stroke(1.5f))
        if (g > 0.001f) {
            val rim = onAirArtRadial(kx, cy, 210f, listOf(onAirArtCol(255, 70, 50, 0.85f * g * (0.4f + 0.6f * room)), onAirArtCol(255, 70, 50, 0f)))
            onAirArtRound(rim, x0 + 1f, y0 + 1f, 298f, 146f, 73f, Stroke(2f), blend = BlendMode.Plus)
            clipRect(x0 - 4f, y0 + 148f, x0 + 304f, y0 + 170f) {
                onAirArtRound(rim, x0 + 1f, y0 + 1f, 298f, 164f, 73f, Stroke(2f), blend = BlendMode.Plus)
            }
        }

        onAirArtRound(onAirArtVertical(cy - 58f, cy + 58f,
            onAirArtStops(intArrayOf(0x2E2F32, 0x3A3B3F, 0x5A5C60), intArrayOf(0x070607, 0x0D0B0C, 0x1F1A1B), room), floatArrayOf(0f, 0.5f, 1f)),
            cx - 134f, cy - 58f, 268f, 116f, 58f)
        clipPath(chan) {
            drawLine(onAirArtCol(0, 0, 0, 0.35f), Offset(cx - 76f, cy + 2f), Offset(cx + 76f, cy + 2f), 4f, StrokeCap.Round)
            drawLine(onAirArtCol(255, 255, 255, 0.08f), Offset(cx - 76f, cy + 4.5f), Offset(cx + 76f, cy + 4.5f), 1f)
            if (g > 0.001f) {
                onAirArtRound(onAirArtRadial(kx, cy, 120f, listOf(onAirArtCol(255, 50, 40, 0.38f * g), onAirArtCol(255, 50, 40, 0f))),
                    cx - 134f, cy - 58f, 268f, 116f, 58f, blend = BlendMode.Plus)
            }
            if (reduced) {
                val u = cross.toFloat()
                if (u < 0.999f) knob(this, cx - 76f, cy, 0f, room, 0f, 1f - u)
                if (u > 0.001f) knob(this, cx + 76f, cy, 1f, room, 0f, u)
            } else knob(this, kx, cy, frame.glow.toFloat(), room, frame.press.toFloat(), 1f)
            drawMask(this, inner, cx - 141f, cy - 60f, onAirArtCol(0, 0, 0, 0.6f))
        }
        onAirArtRound(onAirArtVertical(cy - 58f, cy + 58f,
            listOf(onAirArtCol(255, 255, 255, 0f), onAirArtCol(255, 255, 255, 0f), onAirArtCol(255, 255, 255, 0.5f - 0.3f * room)), floatArrayOf(0f, 0.55f, 1f)),
            cx - 133.4f, cy - 57.4f, 266.8f, 114.8f, 57.4f, Stroke(1.2f))

        if (g > 0.001f) {
            val lx = if (reduced) cx - 76f + 152f * cross.toFloat() else kx
            val ly = cy - 3f
            drawCircle(onAirArtRadial(lx, ly, 130f,
                listOf(onAirArtCol(255, 64, 48, 0.5f), onAirArtCol(255, 40, 40, 0.2f), onAirArtCol(255, 30, 30, 0.05f), onAirArtCol(255, 30, 30, 0f)), floatArrayOf(0f, 0.35f, 0.7f, 1f)),
                130f, Offset(lx, ly), alpha = (frame.glow.toFloat() * (0.35f + 0.65f * room)).coerceIn(0f, 1f), blendMode = BlendMode.Plus)
            drawCircle(onAirArtRadial(lx, ly, 50f,
                listOf(onAirArtCol(255, 140, 100, 0.55f * g), onAirArtCol(255, 120, 90, 0.35f * g), onAirArtCol(255, 100, 80, 0f)), floatArrayOf(0f, 0.66f, 1f)),
                50f, Offset(lx, ly), blendMode = BlendMode.Plus)
        }
        val ink = if (room < 0.5f) intArrayOf(29, 26, 26) else intArrayOf(243, 238, 234)
        val inkAlpha = if (room < 0.5f) 1f - room * 2f else room * 2f - 1f
        val timerColor = onAirArtCol(onAirArtMix(ink[0].toFloat(), 255f, room * g), onAirArtMix(ink[1].toFloat(), 75f, room * g), onAirArtMix(ink[2].toFloat(), 58f, room * g),
            (0.35f + 0.65f * room) * (0.4f + 0.6f * inkAlpha))
        tracked(drawContext.canvas.nativeCanvas, timer, onAirArtTimerText(frame.timer), cx, cy + 196f, 1f, timerColor)
        repeat(32) { i ->
            val level = onAirToggleBarLevel(i, if (reduced) 0.0 else time).toFloat()
            val h = 3f + 27f * level * room
            onAirArtRound(onAirArtCol(onAirArtMix(ink[0].toFloat(), 255f, room), onAirArtMix(ink[1].toFloat(), 75f, room), onAirArtMix(ink[2].toFloat(), 58f, room),
                onAirArtMix(0.2f, 0.55f + 0.45f * level, room) * (0.5f + 0.5f * inkAlpha)), cx - 110f + i * 7f, cy + 248f - h / 2f, 3f, h, 1.5f)
        }
    }

    private fun knob(scope: DrawScope, kx: Float, cy: Float, glow: Float, room: Float, press: Float, alpha: Float) = with(scope) {
        val g = glow.coerceIn(0f, 1f)
        val side = 7f * (1f - 0.55f * press)
        val ky = cy - 3f + 3f * press
        val canvas = drawContext.canvas
        if (alpha < 1f) canvas.saveLayer(Rect(kx - 100f, ky - 100f, kx + 110f, ky + 110f), Paint().apply { this.alpha = alpha }) else canvas.save()
        if (knobPress != press) {
            updateKnobShadow(press)
            knobPress = press
        }
        drawMask(scope, knobShadow, kx - 46f, ky - 38f - 4f * press, onAirArtCol(0, 0, 0, 0.55f - 0.15f * press))
        drawCircle(onAirArtHorizontal(kx - 52f, kx + 52f,
            onAirArtStops(intArrayOf(0x7C7E81, 0xC9CBCD, 0x8A8C8F), intArrayOf(0x141213, 0x3A3335, 0x161415), room), floatArrayOf(0f, 0.45f, 1f)), 52f, Offset(kx, ky + side))
        drawCircle(Brush.sweepGradient(onAirArtStops(
            intArrayOf(0xF4F4F2, 0xA3A5A8, 0xEDEEEE, 0x8D8F92, 0xF4F4F2, 0xA9ABAE, 0xE3E4E5, 0x909295, 0xF4F4F2),
            intArrayOf(0x5A5254, 0x262223, 0x4B4446, 0x1E1B1C, 0x5A5254, 0x2A2526, 0x463F41, 0x1F1C1D, 0x5A5254), room), Offset(kx, ky)), 52f, Offset(kx, ky))
        drawCircle(onAirArtVertical(ky - 52f, ky + 52f,
            listOf(onAirArtCol(255, 255, 255, 0.35f), onAirArtCol(255, 255, 255, 0f), onAirArtCol(0, 0, 0, 0.18f)), floatArrayOf(0f, 0.5f, 1f)), 52f, Offset(kx, ky))
        drawCircle(onAirArtVertical(ky - 52f, ky, listOf(onAirArtCol(255, 255, 255, 0.9f - 0.2f * room), onAirArtCol(255, 255, 255, 0f))), 51.25f, Offset(kx, ky), style = Stroke(1.5f))
        if (g > 0.001f) drawCircle(onAirArtRadial(kx, ky, 52f,
            listOf(onAirArtCol(255, 60, 40, 0f), onAirArtCol(255, 60, 40, 0.55f * g), onAirArtCol(255, 60, 40, 0.08f * g)), floatArrayOf(0.62f, 0.7f, 1f)), 52f, Offset(kx, ky), blendMode = BlendMode.Plus)
        drawCircle(onAirArtCol(0, 0, 0, 0.28f), 39f, Offset(kx, ky - 0.5f), style = Stroke(2f))
        drawCircle(onAirArtCol(255, 255, 255, 0.55f - 0.35f * room), 39.5f, Offset(kx, ky + 1f), style = Stroke(1.2f))
        drawCircle(onAirArtCol(18, 7, 8), 36f, Offset(kx, ky))
        drawCircle(onAirArtRadial(kx - 6f, ky - 8f, 42f,
            onAirArtStops(intArrayOf(0x7A2224, 0x4E1013, 0x250709), intArrayOf(0x6A1A1C, 0x3E0B0E, 0x1A0405), room), floatArrayOf(0f, 0.5f, 1f)), 34f, Offset(kx, ky))
        if (g > 0.001f) drawCircle(onAirArtRadial(kx, ky - 2f, 36f,
            listOf(onAirArtCol(255, 138, 92), onAirArtCol(255, 59, 43), onAirArtCol(211, 15, 28), onAirArtCol(126, 7, 16)), floatArrayOf(0f, 0.42f, 0.78f, 1f)), 34f, Offset(kx, ky), alpha = g)
        repeat(4) { i ->
            val r = 9f + 7f * i
            drawCircle(onAirArtCol(255, 200, 190, 0.1f + 0.16f * g), r, Offset(kx, ky), style = Stroke(1f))
            drawCircle(onAirArtCol(40, 0, 4, 0.18f), r + 1f, Offset(kx, ky), style = Stroke(1f))
        }
        if (g > 0.01f) drawMask(scope, lensGlow, kx - lensGlow.contentWidth / 2f, ky + 3.6f - lensGlow.baseline, onAirArtCol(255, 120, 90, 0.9f * g))
        tracked(canvas.nativeCanvas, lens, "ON AIR", kx, ky + 3.6f, 1.4f,
            onAirArtCol(255, onAirArtMix(170f, 246f, g), onAirArtMix(160f, 232f, g), onAirArtMix(0.22f, 0.96f, g)))
        drawCircle(onAirArtRadial(kx + 8f, ky + 16f, 18f, listOf(onAirArtCol(255, 120, 100, 0.28f + 0.3f * g), onAirArtCol(255, 120, 100, 0f))), 34f, Offset(kx, ky), blendMode = BlendMode.Plus)
        withTransform({ translate(kx - 9f, ky - 15f); rotate(-25f, Offset.Zero) }) {
            drawOval(onAirArtVertical(-8f, 8f, listOf(onAirArtCol(255, 255, 255, 0.5f - 0.15f * g), onAirArtCol(255, 255, 255, 0f))), Offset(-17f, -8f), Size(34f, 16f))
        }
        drawCircle(onAirArtCol(255, 255, 255, 0.85f), 2.5f, Offset(kx - 15f, ky - 15f))
        canvas.restore()
    }

    private class Mask(val bitmap: Bitmap, val pad: Float, val contentWidth: Float, val baseline: Float = 0f)

    // Android's radius is converted internally to sigma = radius * 0.57735 + 0.5.
    // Rasterizing on a software canvas makes MaskFilter work on API 26 hardware canvases too.
    private fun shadow(width: Float, height: Float, radius: Float, sigma: Float, stroke: Float = 0f, oval: Boolean = false): Mask {
        val pad = ceil(sigma * 3f + stroke / 2f + 2f)
        val bitmap = Bitmap.createBitmap(ceil((width + 2f * pad) * density).toInt(), ceil((height + 2f * pad) * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = NativeCanvas(bitmap)
        canvas.scale(density, density)
        val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = if (stroke > 0f) NativePaint.Style.STROKE else NativePaint.Style.FILL
            strokeWidth = stroke
            maskFilter = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f), BlurMaskFilter.Blur.SOLID)
        }
        val rect = RectF(pad, pad, pad + width, pad + height)
        if (oval) canvas.drawOval(rect, paint) else canvas.drawRoundRect(rect, radius, radius, paint)
        return Mask(bitmap, pad, width)
    }

    private fun updateKnobShadow(press: Float) {
        knobShadow.bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        val canvas = NativeCanvas(knobShadow.bitmap)
        canvas.scale(density, density)
        val sigma = 9f - 3f * press
        val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            maskFilter = BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.SOLID)
        }
        val p = knobShadow.pad
        canvas.drawOval(RectF(p, p, p + 104f, p + 96f), paint)
    }

    private fun textMask(text: String, font: NativePaint, tracking: Float, sigma: Float): Mask {
        val width = trackedWidth(font, text, tracking)
        val baseline = -font.fontMetrics.ascent
        val height = font.fontMetrics.descent - font.fontMetrics.ascent
        val pad = ceil(sigma * 3f + 2f)
        val bitmap = Bitmap.createBitmap(ceil((width + 2f * pad) * density).toInt(), ceil((height + 2f * pad) * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = NativeCanvas(bitmap)
        canvas.scale(density, density)
        val copy = NativePaint(font).apply { maskFilter = BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.SOLID) }
        tracked(canvas, copy, text, pad + width / 2f, pad + baseline, tracking, Color.White)
        return Mask(bitmap, pad, width, baseline)
    }

    private fun drawMask(scope: DrawScope, mask: Mask, x: Float, y: Float, color: Color) {
        bitmapPaint.alpha = 255
        bitmapPaint.shader = null
        bitmapPaint.colorFilter = PorterDuffColorFilter(color.toArgb(), PorterDuff.Mode.SRC_IN)
        val w = mask.bitmap.width / density
        val h = mask.bitmap.height / density
        scope.drawContext.canvas.nativeCanvas.drawBitmap(mask.bitmap, null, RectF(x - mask.pad, y - mask.pad, x - mask.pad + w, y - mask.pad + h), bitmapPaint)
    }

    private fun trackedWidth(paint: NativePaint, text: String, tracking: Float): Float {
        val widths = FloatArray(text.length)
        paint.getTextWidths(text, widths)
        return widths.sum() + tracking * (text.length - 1)
    }

    private fun tracked(canvas: NativeCanvas, paint: NativePaint, text: String, cx: Float, baseline: Float, tracking: Float, color: Color) {
        val widths = FloatArray(text.length)
        paint.getTextWidths(text, widths)
        var x = cx - (widths.sum() + tracking * (text.length - 1)) / 2f
        paint.color = color.toArgb()
        for (i in text.indices) {
            canvas.drawText(text, i, i + 1, x, baseline, paint)
            x += widths[i] + tracking
        }
    }
}

private fun onAirArtMix(a: Float, b: Float, u: Float) = a + (b - a) * u
private fun onAirArtCol(r: Number, g: Number, b: Number, alpha: Float = 1f) = Color(r.toFloat() / 255f, g.toFloat() / 255f, b.toFloat() / 255f, alpha.coerceIn(0f, 1f))
private fun onAirArtStops(light: IntArray, dark: IntArray, u: Float) = light.indices.map { i ->
    onAirArtCol(onAirArtMix(((light[i] shr 16) and 255).toFloat(), ((dark[i] shr 16) and 255).toFloat(), u),
        onAirArtMix(((light[i] shr 8) and 255).toFloat(), ((dark[i] shr 8) and 255).toFloat(), u),
        onAirArtMix((light[i] and 255).toFloat(), (dark[i] and 255).toFloat(), u))
}
private fun onAirArtPairs(colors: List<Color>, stops: FloatArray?) = colors.indices.map { (stops?.get(it) ?: (it.toFloat() / (colors.size - 1))) to colors[it] }.toTypedArray()
private fun onAirArtVertical(y0: Float, y1: Float, colors: List<Color>, stops: FloatArray? = null) = Brush.linearGradient(*onAirArtPairs(colors, stops), start = Offset(0f, y0), end = Offset(0f, y1))
private fun onAirArtHorizontal(x0: Float, x1: Float, colors: List<Color>, stops: FloatArray? = null) = Brush.linearGradient(*onAirArtPairs(colors, stops), start = Offset(x0, 0f), end = Offset(x1, 0f))
private fun onAirArtRadial(x: Float, y: Float, radius: Float, colors: List<Color>, stops: FloatArray? = null) = Brush.radialGradient(*onAirArtPairs(colors, stops), center = Offset(x, y), radius = radius)
private fun onAirArtPath(x: Float, y: Float, width: Float, height: Float, radius: Float) = Path().apply { addRoundRect(RoundRect(Rect(x, y, x + width, y + height), CornerRadius(radius))) }
private fun DrawScope.onAirArtRound(color: Color, x: Float, y: Float, width: Float, height: Float, radius: Float, style: DrawStyle = Fill) = drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(radius), style = style)
private fun DrawScope.onAirArtRound(brush: Brush, x: Float, y: Float, width: Float, height: Float, radius: Float, style: DrawStyle = Fill, alpha: Float = 1f, blend: BlendMode = BlendMode.SrcOver) = drawRoundRect(brush, Offset(x, y), Size(width, height), CornerRadius(radius), alpha = alpha, style = style, blendMode = blend)
private fun onAirArtTimerText(seconds: Double): String {
    val cs = floor(seconds * 100.0).toLong()
    fun pad(value: Long) = if (value < 10L) "0$value" else value.toString()
    return "${pad(cs / 6000L)}:${pad((cs / 100L) % 60L)}.${pad(cs % 100L)}"
}
