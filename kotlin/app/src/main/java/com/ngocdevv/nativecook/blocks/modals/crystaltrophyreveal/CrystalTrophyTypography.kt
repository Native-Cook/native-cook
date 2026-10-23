package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

import android.content.Context
import android.graphics.*
import com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal.CrystalTrophyTimeline.enter
import com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal.CrystalTrophyTimeline.uiProgress
import kotlin.math.roundToInt

internal class CrystalTrophyTypography(context: Context) {
    private val title = face(context, "ExtraBoldItalic")
    private val date = face(context, "SemiBoldItalic")
    private val label = face(context, "Bold")
    private val hint = face(context, "Medium")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bounds = Rect()
    private val checkPath = Path()
    private val shadows = mutableMapOf<Pair<Char, Float>, Bitmap>()

    // A software mask keeps shadow opacity coupled to each glyph on Android hardware canvases.
    private fun shadow(letter: Char, size: Float, advance: Float): Bitmap {
        return shadows.getOrPut(letter to size) {
            val bitmap = Bitmap.createBitmap(((advance + 72) * 2).roundToInt(), ((size + 72) * 2).roundToInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(2f, 2f)
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = title
                textSize = size
                color = Color.rgb(0, 5, 35)
                maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawText(letter.toString(), 36f, size + 36f, ink)
            bitmap
        }
    }

    private fun face(context: Context, weight: String): Typeface {
        return Typeface.createFromAsset(context.assets, "crystaltrophyreveal/crystaltrophyreveal_BarlowCondensed-$weight.ttf")
    }

    private fun prepare(font: Typeface, size: Double, color: Int, alpha: Double) {
        paint.typeface = font
        paint.textSize = size.toFloat()
        paint.color = color
        paint.alpha = (Color.alpha(color) * alpha.coerceIn(0.0, 1.0)).roundToInt()
        paint.shader = null
    }

    private fun baseline(center: Double): Float {
        paint.getTextBounds("H", 0, 1, bounds)
        return (center - bounds.top - bounds.height() / 2.0).toFloat()
    }

    private fun tracked(canvas: Canvas, text: String, center: Double, y: Float, spacing: Float) {
        val widths = FloatArray(text.length)
        paint.getTextWidths(text, widths)
        var x = center.toFloat() - (widths.sum() + (text.length - 1) * spacing) / 2
        for (i in text.indices) {
            canvas.drawText(text, i, i + 1, x, y, paint)
            x += widths[i] + spacing
        }
    }

    fun draw(canvas: Canvas, f: CrystalTrophyFrame, s: CrystalTrophyState, renderer: CrystalTrophyRenderer?) {
        canvas.save()
        canvas.rotate(-8f, f.originX.toFloat(), f.titleY.toFloat())
        var index = 0
        for ((line, text) in listOf("PERFECT", "MONTH").withIndex()) {
            prepare(title, f.titleSize, Color.WHITE, 1.0)
            val widths = FloatArray(text.length)
            paint.getTextWidths(text, widths)
            var x = f.originX.toFloat() - (widths.sum() + (text.length - 1) * 0.5f) / 2
            val base = (f.titleY - 7 * f.k + line * f.titleLine).toFloat()
            paint.shader = LinearGradient(0f, base - f.titleSize.toFloat() * 0.72f, 0f, base,
                Color.WHITE, Color.rgb(220, 230, 255), Shader.TileMode.CLAMP)
            for (i in text.indices) {
                val age = (s.titleTime - 2.66 - index * 0.038) / 0.42
                val alpha = if (s.reduced) s.ui else enter(age / 0.45) * s.ui
                val dy = if (s.reduced) 0.0 else -26 * f.k * (1 - enter(age)) + 8 * (1 - s.ui)
                val gradient = paint.shader
                paint.shader = null
                paint.alpha = (alpha.coerceIn(0.0, 1.0) * 115).roundToInt()
                val shadow = shadow(text[i], f.titleSize.toFloat(), widths[i])
                val shadowTop = base + dy.toFloat() - f.titleSize.toFloat() - 36f + 6f
                canvas.drawBitmap(shadow, null, RectF(x - 36f, shadowTop,
                    x - 36f + shadow.width / 2f, shadowTop + shadow.height / 2f), paint)
                paint.shader = gradient
                paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
                canvas.drawText(text, i, i + 1, x, base + dy.toFloat(), paint)
                x += widths[i] + 0.5f
                index++
            }
        }
        val landed = 2.66 + 11 * 0.038 + 0.42
        if (!s.reduced && s.titleTime >= landed) {
            val first = s.titleTime < landed + 0.52
            val age = if (first) s.titleTime - landed else s.flashAge
            val alpha = if (first) 0.65 * s.ui else if (s.flashAge < 0.52) 0.34 * s.ui else 0.0
            prepare(title, f.titleSize, Color.WHITE, alpha)
            val x = (f.originX - f.titleSize * 2.8 + age / 0.52 * f.titleSize * 5.6).toFloat()
            paint.shader = LinearGradient(x, (f.titleY - f.titleSize).toFloat(), x + (45 * f.k).toFloat(),
                (f.titleY + f.titleSize).toFloat(), intArrayOf(0x00ffffff, 0x66bcdcff, -1, 0x66bcdcff, 0x00ffffff),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
            tracked(canvas, "PERFECT", f.originX, (f.titleY - 7 * f.k).toFloat(), 0.5f)
            tracked(canvas, "MONTH", f.originX, (f.titleY - 7 * f.k + f.titleLine).toFloat(), 0.5f)
        }
        val dateProgress = uiProgress(s, 3.12)
        prepare(date, f.dateSize, Color.rgb(229, 235, 255), dateProgress)
        tracked(canvas, "Won on Oct 13, 2026", f.originX,
            baseline(f.dateY + if (s.reduced) 0.0 else 8 * (1 - dateProgress)), 0f)
        canvas.restore()
        val bodyProgress = uiProgress(s, 3.26)
        prepare(Typeface.create("sans-serif", Typeface.NORMAL), 16.0, Color.argb(199, 222, 229, 255), bodyProgress)
        for ((line, text) in listOf("Thirty days, thirty sessions.", "Not a single one missed.").withIndex()) {
            val y = f.bodyTop + 11 + line * 22 + if (s.reduced) 0.0 else 8 * (1 - bodyProgress)
            val base = y - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
            canvas.drawText(text, (f.originX - paint.measureText(text) / 2).toFloat(), base.toFloat(), paint)
        }
        val alpha = uiProgress(s, 3.4, 0.42)
        canvas.save()
        canvas.translate(0f, (if (s.reduced) 0.0 else 16 * (1 - alpha)).toFloat())
        val scale = (1 - 0.03 * s.sharePress).toFloat()
        canvas.scale(scale, scale, f.originX.toFloat(), (f.shareY + 28).toFloat())
        if (renderer != null) renderer.control(canvas, f, s, false, alpha)
        else {
            prepare(label, 22.0, Color.rgb(30, 40, 78), alpha)
            canvas.drawRoundRect(f.shareX.toFloat(), f.shareY.toFloat(), (f.shareX + f.shareWidth).toFloat(), (f.shareY + 56).toFloat(), 16f, 16f, paint)
        }
        prepare(label, 22.0, Color.rgb(245, 248, 255), alpha * (1 - s.copied))
        tracked(canvas, "SHARE", f.originX, baseline(f.shareY + 28 - if (s.reduced) 0.0 else 10 * s.copied), 3.5f)
        prepare(label, 22.0, Color.rgb(245, 248, 255), alpha * s.copied)
        val copiedY = if (s.reduced) 0.0 else 10 * (1 - s.copied)
        tracked(canvas, "LINK COPIED", f.originX + 10, baseline(f.shareY + 28 + copiedY), 3.5f)
        val x = f.originX - (paint.measureText("LINK COPIED") + 10 * 3.5) / 2 - 13
        checkPath.reset()
        checkPath.moveTo(x.toFloat(), (f.shareY + 28 + copiedY).toFloat())
        checkPath.lineTo((x + 4).toFloat(), (f.shareY + 32 + copiedY).toFloat())
        checkPath.lineTo((x + 12).toFloat(), (f.shareY + 23 + copiedY).toFloat())
        val measure = PathMeasure(checkPath, false)
        val visible = Path()
        measure.getSegment(0f, measure.length * s.check.toFloat(), visible, true)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.7f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(visible, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
        canvas.save()
        val closeScale = ((if (s.reduced) 1.0 else 0.9 + 0.1 * alpha) * (1 - 0.03 * s.closePress)).toFloat()
        canvas.scale(closeScale, closeScale, (f.closeX + 20).toFloat(), (f.closeY + 20).toFloat())
        if (renderer != null) renderer.control(canvas, f, s, true, alpha)
        else {
            prepare(label, 24.0, Color.WHITE, alpha)
            tracked(canvas, "×", f.closeX + 20, baseline(f.closeY + 20), 0f)
        }
        canvas.restore()
        if (s.phase == "closed") {
            prepare(hint, 13.0, Color.argb(143, 222, 229, 255), s.hint)
            tracked(canvas, "TAP TO REVEAL", f.originX, (f.shareY + 28).toFloat(), 3f)
        }
        paint.shader = null
        paint.color = Color.BLACK
        paint.alpha = (s.veil.coerceIn(0.0, 1.0) * 255).roundToInt()
        canvas.drawRect(0f, 0f, f.width.toFloat(), f.height.toFloat(), paint)
    }
}
