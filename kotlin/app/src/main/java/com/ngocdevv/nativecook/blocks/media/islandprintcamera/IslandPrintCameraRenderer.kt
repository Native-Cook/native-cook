package com.ngocdevv.nativecook.blocks.media.islandprintcamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*

/** The RN layer order and coordinates, drawn in canvas points; no per-frame composition. */
internal class IslandPrintCameraRenderer(context: Context, private val assets: IslandPrintCameraAssets) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = assets.inter }
    private val shaders = if (Build.VERSION.SDK_INT >= 33) IslandPrintCameraShaders(assets.plate) else null
    private val fallback = if (Build.VERSION.SDK_INT < 33) IslandPrintCameraFallback(assets.plate) else null
    private val blur = if (Build.VERSION.SDK_INT >= 31) IslandPrintCameraBlur() else null
    private val legacyBlur = if (Build.VERSION.SDK_INT < 31) IslandPrintCameraLegacyBlur(context, assets.plate) else null
    private val emptyPaths = arrayOf(
        "M5 49V8h31v41M8 53V11h31v42", "M15 19h23v25H15z",
        "m26.5 27.5 1.55 3.3 3.45.45-2.5 2.4.65 3.35-3.15-1.75-3.15 1.75.65-3.35-2.5-2.4 3.45-.45z",
        "m33.5 23.2.55 1.2 1.25.15-.9.85.25 1.25-1.15-.65-1.15.65.25-1.25-.9-.85 1.25-.15z",
    ).map { requireNotNull(PathParser.createPathFromPathData(it)) }
    private val flipPaths = arrayOf(
        "M18 8.2A7.3 7.3 0 0 0 5.15 5.1L3.7 6.8", "M3.7 3.4v3.8h3.8",
        "M4 13.8a7.3 7.3 0 0 0 12.85 3.1l1.45-1.7", "M18.3 18.6v-3.8h-3.8",
    ).map { requireNotNull(PathParser.createPathFromPathData(it)) }
    private val flashPaths = arrayOf("M9.15 1.8 4.4 8.25h3.35l-.9 5.95 4.75-6.45H8.25z", "m2.1 2.1 11.8 11.8")
        .map { requireNotNull(PathParser.createPathFromPathData(it)) }
    private val glint = LinearGradient(-12f, 0f, 12f, 0f, intArrayOf(0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
    private val sheen = LinearGradient(-20f, 0f, 20f, 0f, intArrayOf(0x00FFFFFF, 0x24FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
    // These gradients deliberately retain the RN shader coordinates (absolute y=0..8 / y=0..10).
    private val lensShade = LinearGradient(0f, 0f, 0f, 8f, 0x8C000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
    private val slotShade = LinearGradient(0f, 0f, 0f, 10f, 0x59000000, 0x00000000, Shader.TileMode.CLAMP)

    fun close() { legacyBlur?.close() }

    fun draw(canvas: Canvas, width: Float, pixelsPerPoint: Float, islandTop: Float, engine: IslandPrintCameraEngine) {
        val f = engine.frame()
        val s = width / 402
        val body = body(f, islandTop / s)
        val radius = IslandPrintCameraMotion.mix(18.5, 64.0, min(max(f.p, 0.0), 1.0)).toFloat()
        canvas.drawColor(SHELL)
        val save = canvas.save()
        canvas.scale(pixelsPerPoint, pixelsPerPoint)
        if(Build.VERSION.SDK_INT>=33)shaders?.shell(canvas,canvas.height/pixelsPerPoint)
        val header = canvas.save()
        canvas.clipRect(0f, 0f, 402f, 430f)
        drawBody(canvas, body, radius, f, pixelsPerPoint)
        drawLens(canvas, body, radius, f, engine.swayTime(), pixelsPerPoint)
        for (i in 0..2) {
            if (Build.VERSION.SDK_INT >= 33 && shaders != null) shaders.dome(canvas, i, f, engine.flashOn)
            else fallback?.dome(canvas, i, f, engine.flashOn, pixelsPerPoint)
        }
        canvas.restoreToCount(header)

        drawIcons(canvas, f, engine.flashOn, s)
        drawPanel(canvas, f, s)
        drawWordmark(canvas, f)
        drawPrint(canvas, f, body, radius, islandTop / s + 37f + f.slotDip.toFloat(), pixelsPerPoint)

        val stripY = (825 + 346 * max(f.p, 0.0)).toFloat()
        canvas.drawRect(0f, stripY, 402f, stripY + 49 + 60 / s, solid(SHELL))
        canvas.restoreToCount(save)
    }

    private fun body(f: IslandPrintCameraFrame, top: Float): RectF {
        val w0 = IslandPrintCameraMotion.mix(126.0, 230.0, f.slot) + 6 * f.antic
        val h0 = 37 - 2 * f.antic
        val p = max(f.p, 0.0)
        val w = IslandPrintCameraMotion.mix(w0, 260.0, IslandPrintCameraMotion.easeOut(min(p / .7, 1.0))).toFloat()
        val h = IslandPrintCameraMotion.mix(h0, 260.0, min(p, 1.02).pow(1.15)).toFloat()
        return RectF(201 - w / 2, top, 201 + w / 2, top + h)
    }

    private fun drawBody(c: Canvas, b: RectF, radius: Float, f: IslandPrintCameraFrame, scale: Float) {
        if (Build.VERSION.SDK_INT >= 33 && shaders != null) shaders.body(c, b, radius, f, 430f)
        else fallback?.body(c, b, radius, f, scale)
    }

    private fun drawLens(c: Canvas, b: RectF, radius: Float, f: IslandPrintCameraFrame, time: Double, scale: Float) {
        val alpha = IslandPrintCameraMotion.seg(f.p, .35, 1.0)
        if (alpha <= 0) return
        val inset = (35 * f.p.coerceIn(0.0, 1.0)).toFloat()
        val lens = RectF(b.left + inset, b.top + inset, b.left + inset + max(b.width() - 2 * inset, 1f), b.top + inset + max(b.height() - 2 * inset, 1f))
        val r = max(radius - inset, 4f)
        val path = Path().apply { addRoundRect(lens, r, r, Path.Direction.CW) }
        val save = c.saveLayerAlpha(lens, (alpha * 255).roundToInt())
        c.clipPath(path)
        val size = (190 * 1.35 * f.focus).toFloat()
        val sx = (5 * sin(2 * PI * 3 * time / 11) + 2 * sin(2 * PI * 5 * time / 11)).toFloat()
        val sy = (4 * sin(2 * PI * 2 * time / 11 + 1.3)).toFloat()
        val plate = RectF(b.centerX() - size / 2 + sx, b.centerY() - size / 2 + sy, b.centerX() + size / 2 + sx, b.centerY() + size / 2 + sy)
        if (Build.VERSION.SDK_INT >= 31 && blur != null) blur.draw(c, assets.plate, plate, f.blur, scale)
        else legacyBlur?.draw(c, plate, f.blur)
        c.drawRect(lens, solid(Color.BLACK, IslandPrintCameraMotion.clamp01(1 - f.exposure) * alpha))
        c.drawRect(lens, solid(Color.WHITE, IslandPrintCameraMotion.clamp01(f.exposure - 1) * 1.6))
        paint.shader = lensShade; paint.alpha = 128
        c.drawRect(lens, paint)
        paint.shader = null
        if (f.glint > 0 && f.glint < 1) {
            val g = c.save()
            c.translate(lens.left + IslandPrintCameraMotion.mix(-40.0, lens.width() + 40.0, f.glint).toFloat(), lens.centerY())
            c.rotate(20f)
            paint.shader = glint; paint.alpha = 255
            c.drawRect(-12f, -200f, 12f, 200f, paint)
            paint.shader = null
            c.restoreToCount(g)
        }
        c.drawRect(lens, solid(Color.BLACK, f.black))
        c.drawRect(lens, solid(Color.WHITE, f.flash))
        c.restoreToCount(save)
        solid(Color.WHITE, .12 * alpha).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
        c.drawRoundRect(lens, r, r, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawIcons(c: Canvas, f: IslandPrintCameraFrame, flashOn: Boolean, s: Float) {
        val flipAlpha = IslandPrintCameraMotion.clamp01(f.flood[2] * 1.2) * f.dim
        val ledAlpha = if (flashOn) 0.0 else IslandPrintCameraMotion.clamp01(f.flood[0] * 1.2) * if (f.led == 0) 1.0 else .35
        val flip = c.save()
        c.translate(318f, (351 - 3 * IslandPrintCameraMotion.clamp01(f.rise[2]) / s).toFloat())
        stroke(0xFFB81F1C.toInt(), 1.5f, flipAlpha)
        flipPaths.forEach { c.drawPath(it, paint) }
        c.restoreToCount(flip)
        val led = c.save()
        c.translate(65f, (354 - 3 * IslandPrintCameraMotion.clamp01(f.rise[0]) / s).toFloat())
        stroke(0xFF1E240D.toInt(), 1.25f, ledAlpha * .7)
        flashPaths.forEach { c.drawPath(it, paint) }
        c.restoreToCount(led)
        paint.style = Paint.Style.FILL
    }

    private fun drawPanel(c: Canvas, f: IslandPrintCameraFrame, s: Float) {
        val shift = (-4 * f.antic + 346 * max(f.p, 0.0)).toFloat()
        val panel = RectF(16f, 165 + shift, 386f, 825 + shift)
        c.drawRoundRect(panel, 6f, 6f, solid(0xFF2C1C26.toInt()))
        if (f.empty <= 0) return
        val path = Path().apply { addRoundRect(panel, 6f, 6f, Path.Direction.CW) }
        val save = c.save()
        c.clipPath(path)
        c.translate(177f, 418 + shift)
        stroke(0xFF6E5F68.toInt(), 1.5f, f.empty)
        c.drawPath(emptyPaths[0], paint)
        c.drawRoundRect(11f, 15f, 42f, 58f, 1f, 1f, paint)
        emptyPaths.drop(1).forEach { c.drawPath(it, paint) }
        c.translate(-177f, 0f)
        // Inter's hhea metrics match the RN/iOS default line box: ascent 1984, descent 494, em 2048.
        val line1 = 17f * 2478 / 2048
        drawText(c, "No pics yet", 201f, 80 + 17f * 1984 / 2048, 17f, 0xFFD9CFD5.toInt(), f.empty, -.22f / s)
        val subtitleTop = 80 + line1 + 6
        val subtitleBaseline = subtitleTop + (18 - 12f * 2478 / 2048) / 2 + 12f * 1984 / 2048
        drawText(c, "Tap logo or drag down", 201f, subtitleBaseline, 12f, 0xFF8A7A84.toInt(), f.empty)
        drawText(c, "to open cam", 201f, subtitleBaseline + 18, 12f, 0xFF8A7A84.toInt(), f.empty)
        c.restoreToCount(save)
        paint.style = Paint.Style.FILL
    }

    private fun drawText(c: Canvas, text: String, x: Float, baseline: Float, size: Float, color: Int, alpha: Double, spacing: Float = 0f) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        val advances = FloatArray(text.length)
        val width = textPaint.getTextRunAdvances(text.toCharArray(), 0, text.length, 0, text.length, false, advances, 0) + spacing * (text.length - 1)
        var left = x - width / 2
        for (i in text.indices) {
            c.drawText(text, i, i + 1, left, baseline, textPaint)
            left += advances[i] + spacing
        }
    }

    private fun drawWordmark(c: Canvas, f: IslandPrintCameraFrame) {
        val p = max(f.p, 0.0)
        val y = (IslandPrintCameraMotion.mix(113.0, 484.0, p) - 4 * f.antic).toFloat()
        val scale = IslandPrintCameraMotion.mix(1.0, .62, min(p, 1.0).pow(.8)).toFloat()
        val save = c.save()
        c.translate(201f, y)
        c.scale(scale, scale)
        c.drawBitmap(assets.wordmark, null, RectF(-39.5f, -22.5f, 39.5f, 22.5f), solid(Color.WHITE))
        c.restoreToCount(save)
    }

    private fun drawPrint(c: Canvas, f: IslandPrintCameraFrame, b: RectF, radius: Float, slotBottom: Float, scale: Float) {
        val slot = f.slot > .001
        if (f.printTop < 880 && f.f > 0) {
            val save = c.save()
            val clipTop = if (f.printOut) -100f else slotBottom
            c.clipRect(0f, clipTop, 402f, clipTop + 1200f)
            val paper = c.save()
            c.concat(printMatrix(f, slotBottom.toDouble()))
            val shadowBlur = IslandPrintCameraMotion.mix(12.0, 20.0, f.printShadow).toFloat()
            val shadowAlpha = IslandPrintCameraMotion.mix(.14, .22, f.printShadow).toFloat()
            if (Build.VERSION.SDK_INT >= 31 && blur != null) blur.shadow(c, shadowBlur, shadowAlpha, scale)
            else legacyBlur?.shadow(c, shadowBlur, shadowAlpha)
            c.drawRoundRect(0f, 0f, 206f, 247f, 2f, 2f, solid(0xFFFCFBFA.toInt()))
            c.drawRect(0f, 0f, 206f, 1f, solid(Color.WHITE, .9))
            c.drawRect(0f, 246.25f, 206f, 247f, solid(0xFFE9E6E2.toInt()))
            if (Build.VERSION.SDK_INT >= 33 && shaders != null) shaders.photo(c, f.d) else fallback?.photo(c, f.d, scale)
            if (f.sheen > 0 && f.sheen < 1) {
                val highlight = c.save()
                c.clipRect(13f, 22f, 193f, 202f)
                c.translate(13 + IslandPrintCameraMotion.mix(-30.0, 210.0, f.sheen).toFloat(), 112f)
                c.rotate(25f)
                solid(Color.WHITE); paint.shader = sheen
                c.drawRect(-20f, -200f, 20f, 200f, paint)
                paint.shader = null
                c.restoreToCount(highlight)
            }
            c.restoreToCount(paper)
            if (slot) {
                solid(Color.BLACK); paint.shader = slotShade
                c.drawRect(0f, slotBottom, 402f, slotBottom + 10, paint)
                paint.shader = null
            }
            c.restoreToCount(save)
        }
        if (slot) {
            val save = c.save()
            c.clipRect(0f, 0f, 402f, 120f)
            drawBody(c, b, radius, f, scale)
            c.restoreToCount(save)
        }
    }

    private fun printMatrix(f: IslandPrintCameraFrame, bottom: Double): Matrix {
        val bottomMoved=bottom+f.dragY; val center=201+f.dragX
        val r = f.printRot * PI / 180
        val a = -f.printTilt * PI / 180
        val cx = cos(r); val sx = sin(r); val cy = cos(a); val sy = sin(a)
        val k = f.printScaleY
        val y0 = f.printTop - bottom + 123.5 - sx * 103 - cx * k * 123.5
        val x0 = -cx * 103 + sx * k * 123.5
        val wx = -sy * sx / 600
        val wy = -sy * cx * k / 600
        val w0 = 1 - sy * y0 / 600
        return Matrix().apply { setValues(floatArrayOf(
            (cx + center * wx).toFloat(), (-sx * k + center * wy).toFloat(), (x0 + center * w0).toFloat(),
            (cy * sx + bottomMoved * wx).toFloat(), (cy * cx * k + bottomMoved * wy).toFloat(), (cy * y0 + bottomMoved * w0).toFloat(),
            wx.toFloat(), wy.toFloat(), w0.toFloat(),
        )) }
    }

    private fun solid(color: Int, alpha: Double = 1.0): Paint {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = (Color.alpha(color) * alpha.coerceIn(0.0, 1.0)).roundToInt()
        return paint
    }
    private fun stroke(color: Int, width: Float, alpha: Double) {
        solid(color, alpha)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }

    private companion object { const val SHELL = 0xFFE82822.toInt() }
}
