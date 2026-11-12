package com.ngocdevv.nativecook.blocks.cards.glasspaymorph

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.*

/** Measured Outfit glyph runs and vector contact lines, all in the RN reference's point space. */
internal class GlassPayMorphFace(assets: AssetManager) {
    private val motion = GlassPayMorphMotion
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        fontFeatureSettings = "'kern' 0, 'liga' 0"
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val light = Typeface.createFromAsset(assets, "glasspaymorph/glasspaymorph_Outfit-Light.ttf")
    private val regular = Typeface.createFromAsset(assets, "glasspaymorph/glasspaymorph_Outfit-Regular.ttf")
    private val medium = Typeface.createFromAsset(assets, "glasspaymorph/glasspaymorph_Outfit-Medium.ttf")
    private val semi = Typeface.createFromAsset(assets, "glasspaymorph/glasspaymorph_Outfit-SemiBold.ttf")
    private class Line(val text: String, val widths: FloatArray, val x: FloatArray, val width: Float)
    private val number = arrayOf("8104", "2267", "5390", "4417").map { line(it, light, 18.4f) }
    private val numberX = FloatArray(4)
    private val name = line("NOA LINDGREN", regular, 12f, 0.4f)
    private val brand = line("solune", semi, 18f, -0.2f)
    private val last4 = line("•••• 4417", regular, 14f, 0.6f)
    private val captions = listOf(
        line("Hold near reader", medium, 17f), line("or tap the card", regular, 14f),
        line("Paid $24.80", medium, 17f), line("Juniper & Rye · 09:41", regular, 14f),
    )
    private val chipOutline = Path().apply { addRoundRect(RectF(43.5f, 23.5f, 73f, 53f), 8.5f, 8.5f, Path.Direction.CW) }
    private val chipLines = arrayOf(
        doubleArrayOf(0.05, 0.24, 0.4, 0.24, 0.4, 1.0),
        doubleArrayOf(0.05, 0.47, 0.22, 0.47),
        doubleArrayOf(0.05, 0.75, 0.4, 0.75),
        doubleArrayOf(0.61, 0.0, 0.61, 0.58, 0.63, 0.65, 0.68, 0.69, 0.75, 0.7, 1.0, 0.7),
        doubleArrayOf(0.61, 0.24, 1.0, 0.24),
        doubleArrayOf(0.61, 0.47, 1.0, 0.47),
    ).map { points -> DoubleArray(points.size) { i -> (if (i % 2 == 0) 43.5 else 23.5) + points[i] * 29.5 } }
    // Tiny retained software canvases: hardware Canvas omits mask-filter text blur. Only the
    // four condensing glyph runs are re-rasterized, at the exact reference sigma 2*k.
    private var rasterScale = 0f
    private var numberBitmaps = emptyList<Bitmap>()
    private var numberCanvases = emptyList<Canvas>()
    private val numberBlur = DoubleArray(4) { Double.NaN }
    private val numberPaint = Paint(fill)

    init {
        var nx = 37.5f
        for (i in number.indices) {
            numberX[i] = nx
            nx += number[i].width + 8.4f
        }
    }

    private fun ensureRasterScale(scale: Float) {
        if (rasterScale == scale) return
        rasterScale = scale
        numberBitmaps = number.map { run ->
            Bitmap.createBitmap(ceil((run.width + 16f) * scale).toInt(), ceil(40f * scale).toInt(), Bitmap.Config.ARGB_8888)
        }
        numberCanvases = numberBitmaps.map { Canvas(it).apply { scale(scale, scale) } }
        numberBlur.fill(Double.NaN)
    }

    private fun blurredNumber(index: Int, k: Double): Bitmap {
        if (numberBlur[index] != k) {
            val bitmap = numberBitmaps[index]
            bitmap.eraseColor(Color.TRANSPARENT)
            numberPaint.color = Color.WHITE
            numberPaint.alpha = 255
            val sigma = (2 * k).toFloat()
            numberPaint.maskFilter = if (k <= 0.01) null else BlurMaskFilter(
                ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL,
            )
            glyphs(numberCanvases[index], number[index], 8f, 24f, light, 18.4f, numberPaint)
            numberPaint.maskFilter = null
            numberBlur[index] = k
        }
        return numberBitmaps[index]
    }

    private fun line(text: String, face: Typeface, size: Float, tracking: Float = 0f): Line {
        fill.typeface = face
        fill.textSize = size
        // RN uses raw glyph IDs/advances; measuring one glyph avoids Minikin pair shaping.
        val widths = FloatArray(text.length) { fill.measureText(text, it, it + 1) }
        val positions = FloatArray(widths.size)
        var x = 0f
        for (i in widths.indices) {
            positions[i] = x
            x += widths[i] + if (i < widths.lastIndex) tracking else 0f
        }
        return Line(text, widths, positions, x)
    }

    private fun glyphs(c: Canvas, run: Line, x: Float, y: Float, face: Typeface, size: Float, paint: Paint = fill) {
        paint.typeface = face
        paint.textSize = size
        for (i in run.text.indices) c.drawText(run.text, i, i + 1, x + run.x[i], y, paint)
    }

    private fun alpha(value: Double) = (255 * value.coerceIn(0.0, 1.0)).roundToInt()

    private fun trimmed(points: DoubleArray, trim: Double): Path {
        var total = 0.0
        for (i in 2 until points.size step 2) total += hypot(points[i] - points[i - 2], points[i + 1] - points[i - 1])
        var left = total * trim
        val path = Path()
        path.moveTo(points[0].toFloat(), points[1].toFloat())
        for (i in 2 until points.size step 2) {
            if (left <= 0) break
            val length = hypot(points[i] - points[i - 2], points[i + 1] - points[i - 1])
            val k = min(1.0, left / length)
            path.lineTo((points[i - 2] + (points[i] - points[i - 2]) * k).toFloat(),
                (points[i - 1] + (points[i + 1] - points[i - 1]) * k).toFloat())
            left -= length
        }
        return path
    }

    private fun lensOnCard(s: GlassPayMorphMotion.Scene): DoubleArray {
        val m = motion.cardInverse(s)
        val x = 184.0
        val y = 408.0
        val z = m[6] * x + m[7] * y + m[8]
        val lx = (m[0] * x + m[1] * y + m[2]) / z + s.w / 2
        val ly = (m[3] * x + m[4] * y + m[5]) / z + s.h / 2
        return doubleArrayOf(lx * 282 / s.w, ly * 168 / s.h)
    }

    private fun drawNumber(c: Canvas, s: GlassPayMorphMotion.Scene, scale: Float) {
        val t = s.t
        val condensing = t >= 1.04 && t < motion.EXPAND0
        val rolling = t >= 3.31 && t < motion.EXPAND1
        val lens = if (condensing) lensOnCard(s) else doubleArrayOf(0.0, 0.0)
        if (condensing) ensureRasterScale(scale)
        for (i in number.indices) {
            val run = number[i]
            val gx = numberX[i]
            val cx = gx + run.width / 2
            val cy = 110 - 18.4 * 0.36
            if (condensing) {
                val d0 = 1.04 + i * 0.025 + if (i == 3) 0.05 else 0.0
                val k = motion.easeInOut(motion.seg(t, d0, d0 + 0.13))
                val travel = motion.easeInOut(motion.seg(t, d0 + 0.13 * 0.75, d0 + 0.13 * 0.75 + 0.12))
                if (k < 1) {
                    c.save()
                    c.translate(cx, cy.toFloat())
                    c.scale((1 - 0.92 * k).toFloat(), (1 - 0.55 * k).toFloat())
                    c.translate(-cx, -cy.toFloat())
                    fill.color = Color.WHITE
                    fill.alpha = alpha(0.92 * s.face * (1 - 0.5 * k))
                    val bitmap = blurredNumber(i, k)
                    c.drawBitmap(bitmap, null, RectF(gx - 8, 86f,
                        gx - 8 + bitmap.width / rasterScale, 86f + bitmap.height / rasterScale), fill)
                    c.restore()
                }
                if (k > 0.45 && travel < 1) {
                    val a = motion.smooth(0.0, 1.0, (k - 0.45) / 0.3) * (1 - travel.pow(3))
                    fill.alpha = alpha(0.92 * s.face * a)
                    c.drawCircle((cx + (lens[0] - cx) * travel).toFloat(),
                        (cy + (lens[1] - cy) * travel).toFloat(), (1.8 * (1 - 0.4 * travel)).toFloat(), fill)
                }
            } else if (rolling) {
                c.save()
                c.clipRect(gx - 2, (110 - 18.4 * 0.95).toFloat(), gx + run.width + 2, (110 + 18.4 * 0.25).toFloat())
                fill.typeface = light
                fill.textSize = 18.4f
                fill.alpha = alpha(0.92 * s.face)
                for (j in run.text.indices) {
                    val d0 = 3.31 + (i * 4 + j) * 0.008
                    val e = motion.easeOut(motion.seg(t, d0, d0 + 0.13))
                    c.drawText(run.text, j, j + 1, gx + run.x[j], (110 + (1 - e) * 18.4 * 1.05).toFloat(), fill)
                }
                c.restore()
            } else if (s.textNumber * s.face > 0.003) {
                fill.alpha = alpha(0.92 * s.textNumber * s.face)
                glyphs(c, run, gx, 110f, light, 18.4f)
            }
        }
    }

    fun draw(c: Canvas, s: GlassPayMorphMotion.Scene, scale: Float) {
        stroke.color = Color.WHITE
        stroke.strokeWidth = 1.3f
        if (s.contactless * s.face > 0.003) {
            stroke.alpha = alpha(0.92 * s.contactless * s.face)
            val sweep = 2 * 0.86 * 180 / PI
            for (radius in doubleArrayOf(4.2, 7.7, 11.2)) {
                c.drawArc(RectF((29 - radius).toFloat(), (38 - radius).toFloat(),
                    (29 + radius).toFloat(), (38 + radius).toFloat()),
                    (180 - sweep / 2).toFloat(), sweep.toFloat(), false, stroke)
            }
        }
        if (s.textChip * s.face > 0.003) {
            stroke.alpha = alpha(0.92 * s.textChip * s.face)
            c.drawPath(chipOutline, stroke)
            if (s.chipLines > 0.001) for (points in chipLines) c.drawPath(trimmed(points, s.chipLines), stroke)
            if (s.rings > 0.001) {
                c.drawArc(RectF(213.5f, 23f, 244.5f, 54f), -60f, (359.9 * s.rings).toFloat(), false, stroke)
                c.drawArc(RectF(201.4f, 22.3f, 222.6f, 43.5f), 120f, (359.9 * s.rings).toFloat(), false, stroke)
            }
        }
        fill.color = Color.WHITE
        drawNumber(c, s, scale)
        if (s.textName * s.face > 0.003) {
            fill.alpha = alpha(0.92 * s.textName * s.face)
            glyphs(c, name, 37.5f, 135f, regular, 12f)
        }
    }

    fun drawPulse(c: Canvas, s: GlassPayMorphMotion.Scene) {
        if (s.pulse < 0 || s.pulse > 0.75) return
        stroke.strokeWidth = 1.5f
        stroke.color = Color.WHITE
        for (i in 0..2) {
            val u = (s.pulse - i * 0.09) / 0.5
            if (u <= 0 || u >= 1) continue
            val e = motion.easeOut(u)
            val radius = 100 + 52 * e
            stroke.alpha = alpha(0.5 * (1 - e) * motion.clamp01(u * 8))
            val rect = RectF((s.cx - radius).toFloat(), (s.cy - radius).toFloat(),
                (s.cx + radius).toFloat(), (s.cy + radius).toFloat())
            c.drawArc(rect, -22f, 44f, false, stroke)
            c.drawArc(rect, 158f, 44f, false, stroke)
        }
    }

    fun drawChrome(c: Canvas, s: GlassPayMorphMotion.Scene, top: Float) {
        val base = top + 22
        val mx = 30f
        val my = base - 6
        fill.alpha = 255
        fill.color = Color.rgb(238, 166, 53)
        c.drawCircle(mx + 1.4f, my + 0.6f, 6f, fill)
        fill.color = Color.rgb(229, 40, 79)
        fill.alpha = alpha(0.92)
        c.drawCircle(mx - 2.6f, my - 1.9f, 6 * 0.68f, fill)
        fill.color = Color.WHITE
        fill.alpha = alpha(0.92)
        glyphs(c, brand, 44f, base, semi, 18f)
        // RN setColor resets the alpha to its rgba(white, .5) value here.
        fill.alpha = alpha(0.5)
        glyphs(c, last4, 402 - 24 - last4.width, base, regular, 14f)
        val k = s.caption
        drawCaption(c, 0, 642 - 6 * k, 1 - k)
        drawCaption(c, 1, 666 - 6 * k, 1 - k)
        drawCaption(c, 2, 642 + 6 * (1 - k), k)
        drawCaption(c, 3, 666 + 6 * (1 - k), k)
    }

    private fun drawCaption(c: Canvas, index: Int, y: Double, opacity: Double) {
        if (opacity <= 0.003) return
        val isTitle = index % 2 == 0
        fill.color = Color.WHITE
        fill.alpha = alpha(opacity * if (isTitle) 0.92 else 0.5)
        val line = captions[index]
        glyphs(c, line, 201 - line.width / 2, y.toFloat(), if (isTitle) medium else regular, if (isTitle) 17f else 14f)
    }
}
