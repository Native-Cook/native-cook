package com.ngocdevv.nativecook.blocks.cards.eyewearlenstint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal class EyewearLensTintArt(context: Context) {
    companion object {
        const val PAGE = 0xFFE9EAEE.toInt()
        const val CARD = 0xFFFFFFFF.toInt()
        const val INK = 0xFF1C1C1E.toInt()
        const val BODY = 0xFF5E5E64.toInt()
        const val MUTE = 0xFFA2A2A8.toInt()
        const val CTA = 0xFF1E1E20.toInt()
        const val CTA_INK = 0xFFFFFFFF.toInt()
        val names = arrayOf("Midnight", "Rose", "Amber", "Sea")
        val swatches = intArrayOf(0xFF1B2433.toInt(), 0xFFD97C8A.toInt(), 0xFFD69A3A.toInt(), 0xFF3E8F8A.toInt())
        val keep = arrayOf(
            floatArrayOf(0.05f, 0.07f, 0.11f),
            floatArrayOf(0.75f, 0.32f, 0.38f),
            floatArrayOf(0.72f, 0.42f, 0.10f),
            floatArrayOf(0.12f, 0.42f, 0.40f),
        )
        val glow = arrayOf(
            floatArrayOf(0.25f, 0.55f, 0.60f),
            floatArrayOf(1.00f, 0.75f, 0.80f),
            floatArrayOf(1.00f, 0.85f, 0.50f),
            floatArrayOf(0.50f, 0.95f, 0.90f),
        )
        const val TITLE = "SUNGLASSES"
        const val PRICE = "\$220"
        const val RATING = "4.5"
        const val REVIEWS = "18 REVIEWS"
        const val BODY_COPY = "Round titanium frames with lenses tinted by hand. Drag a tint onto a lens to try it on."
        const val BAG = "ADD TO BAG"
        const val WISH = "ADD TO WISHLIST"
    }

    private val portrait: Bitmap =
        context.assets.open("eyewearlenstint/eyewearlenstint_elt-portrait.jpg").use {
            requireNotNull(BitmapFactory.decodeStream(it)) { "Eyewear Lens Tint portrait could not be decoded." }
        }
    private val regular = Typeface.createFromAsset(context.assets, "eyewearlenstint/eyewearlenstint_PTSans-Regular.ttf")
    private val bold = Typeface.createFromAsset(context.assets, "eyewearlenstint/eyewearlenstint_PTSans-Bold.ttf")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bodyText = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val wishGlyphWidths = FloatArray(WISH.length)
    private val heartPath = Path().apply {
        val sx = 14.0 / 22.0
        val sy = 13.0 / 20.0
        moveTo((11.0 * sx).toFloat(), (18.2 * sy).toFloat())
        cubicTo((4.0 * sx).toFloat(), (13.4 * sy).toFloat(), (1.6 * sx).toFloat(), (10.2 * sy).toFloat(), (1.6 * sx).toFloat(), (6.6 * sy).toFloat())
        cubicTo((1.6 * sx).toFloat(), (3.0 * sy).toFloat(), (6.4 * sx).toFloat(), (1.8 * sy).toFloat(), (11.0 * sx).toFloat(), (4.6 * sy).toFloat())
        cubicTo((15.6 * sx).toFloat(), (1.8 * sy).toFloat(), (20.4 * sx).toFloat(), (3.0 * sy).toFloat(), (20.4 * sx).toFloat(), (6.6 * sy).toFloat())
        cubicTo((20.4 * sx).toFloat(), (10.2 * sy).toFloat(), (18.0 * sx).toFloat(), (13.4 * sy).toFloat(), (11.0 * sx).toFloat(), (18.2 * sy).toFloat())
        close()
    }
    private var layout: EyewearLensTintLayout? = null
    private var runtime: EyewearLensTintRuntime? = null
    private var bodyLayout: StaticLayout? = null
    private var dropGradients: Array<RadialGradient>? = null
    private var cardGradient: LinearGradient? = null
    private var starPaths: Array<Path>? = null
    private var wishWidth = 0.0

    init {
        text.typeface = regular
        text.textSize = 10f
        text.getTextWidths(WISH, wishGlyphWidths)
        wishWidth = wishGlyphWidths.sum().toDouble() + 0.4 * (WISH.length - 1)
    }

    internal val wishlistWidth: Double
        get() = wishWidth

    fun draw(canvas: Canvas, l: EyewearLensTintLayout, f: EyewearLensTintMotion.Frame, density: Float) {
        if (layout != l) {
            layout = l
            bodyLayout = makeBodyLayout(l)
            runtime = if (Build.VERSION.SDK_INT >= 33) {
                runCatching { EyewearLensTintRuntime(portrait) }.getOrNull()
            } else {
                null
            }
            runtime?.setLayout(l)
            dropGradients = Array(swatches.size) { i ->
                val x = l.dropX
                val y = l.dropY(i)
                val radius = 13.0 * l.sx
                RadialGradient(
                    (x - 4).toFloat(),
                    (y - 4).toFloat(),
                    (radius * 1.4).toFloat(),
                    intArrayOf(Color.WHITE, swatches[i], swatches[i]),
                    floatArrayOf(0f, 0.35f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            cardGradient = LinearGradient(
                0f,
                (l.cardTop - 90.0).toFloat(),
                0f,
                l.cardTop.toFloat(),
                0x00FFFFFF,
                0xBFFFFFFF.toInt(),
                Shader.TileMode.CLAMP,
            )
            starPaths = Array(5) { i ->
                val x = 56.0 * l.sx + i * 16.0
                starPath(x + 7.5, (l.y(649.0) - 8.0) + 7.5, 7.5)
            }
        }
        canvas.save()
        canvas.scale(density, density)
        fill(PAGE)
        canvas.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), paint)

        paint.shader = null
        paint.alpha = 255
        canvas.drawBitmap(portrait, null, RectF(l.photoX.toFloat(), l.photoY.toFloat(), (l.photoX + l.photoW).toFloat(), (l.photoY + l.photoH).toFloat()), paint)
        if (Build.VERSION.SDK_INT >= 33 && runtime != null) {
            runtime?.draw(canvas, l, f)
        } else {
            drawFlatLenses(canvas, l, f)
        }

        drawDroplets(canvas, l, f)
        drawHeaderIcons(canvas, l)
        drawCard(canvas, l)
        drawTextAndActions(canvas, l)
        drawFinger(canvas, l, f)
        canvas.restore()
    }

    private fun makeBodyLayout(l: EyewearLensTintLayout): StaticLayout {
        bodyText.typeface = regular
        bodyText.textSize = 12.5f
        bodyText.color = BODY
        val lineHeight = bodyText.fontMetrics.descent - bodyText.fontMetrics.ascent
        return StaticLayout.Builder.obtain(BODY_COPY, 0, BODY_COPY.length, bodyText, l.bodyW.roundToInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing((21.0f - lineHeight).coerceAtLeast(0f), 1.0f)
            .build()
    }

    private fun drawFlatLenses(canvas: Canvas, l: EyewearLensTintLayout, f: EyewearLensTintMotion.Frame) {
        val tint = if (f.pour > 0.5) f.to else f.from
        fill(Color.argb((0.88f * 255f).roundToInt(), Color.red(swatches[tint]), Color.green(swatches[tint]), Color.blue(swatches[tint])))
        canvas.drawCircle(l.lensAX.toFloat(), l.lensAY.toFloat(), l.lensR.toFloat(), paint)
        canvas.drawCircle(l.lensBX.toFloat(), l.lensBY.toFloat(), l.lensR.toFloat(), paint)
    }

    private fun drawDroplets(canvas: Canvas, l: EyewearLensTintLayout, f: EyewearLensTintMotion.Frame) {
        for (i in swatches.indices) {
            val x = l.dropX
            val y = l.dropY(i)
            val radius = 13.0 * l.sx
            paint.shader = dropGradients?.getOrNull(i)
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f
            val ringColor = if (i == if (f.pour > 0.5) f.to else f.from) 0xCC1E1E20.toInt() else 0xB2FFFFFF.toInt()
            paint.color = ringColor
            paint.alpha = Color.alpha(ringColor)
            canvas.drawCircle(x.toFloat(), y.toFloat(), (radius + 3).toFloat(), paint)
        }

        val carry = carryPosition(l, f)
        if (carry != null && carry.second > 0.001) {
            val (position, alpha) = carry
            val radius = 13.0 * l.sx * (1.0 + 0.25 * alpha) * alpha
            val tint = if (f.carry >= 0.0) f.carryTint else f.manualCarryTint
            fill(swatches[tint])
            canvas.save()
            canvas.translate(position.first.toFloat(), position.second.toFloat())
            canvas.rotate((f.heading * 180.0 / PI).toFloat())
            val k = (1.0 + f.stretch).toFloat()
            canvas.scale(k, 1f / k)
            canvas.drawCircle(0f, 0f, radius.toFloat(), paint)
            canvas.restore()
            fill(Color.argb((255.0 * 0.75 * alpha).roundToInt(), 255, 255, 255))
            canvas.drawCircle(
                (position.first - radius * 0.35).toFloat(),
                (position.second - radius * 0.35).toFloat(),
                (radius * 0.28).toFloat(),
                paint,
            )
        }
    }

    private fun carryPosition(l: EyewearLensTintLayout, f: EyewearLensTintMotion.Frame): Pair<Pair<Double, Double>, Double>? {
        if (f.carry >= 0.0) {
            val startX = l.dropX
            val startY = l.dropY(f.carryTint)
            val endX = l.pourX(f.lens)
            val endY = l.pourY(f.lens)
            val u = f.carry
            return Pair(
                Pair(
                    startX + (endX - startX) * u,
                    startY + (endY - startY) * u - sin(PI * u) * 60.0,
                ),
                1.0,
            )
        }
        if (f.carryOn > 0.0) return Pair(Pair(f.carryX, f.carryY), f.carryOn)
        return null
    }

    private fun drawHeaderIcons(canvas: Canvas, l: EyewearLensTintLayout) {
        fill(INK)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 1.8f
        val menuX = 32.0 * l.sx
        canvas.drawLine((menuX - 10.0).toFloat(), (l.y(90.0) - 6.5).toFloat(), (menuX + 10.0).toFloat(), (l.y(90.0) - 6.5).toFloat(), paint)
        canvas.drawLine((menuX - 10.0).toFloat(), l.y(90.0).toFloat(), (menuX + 3.0).toFloat(), l.y(90.0).toFloat(), paint)
        canvas.drawLine((menuX - 10.0).toFloat(), (l.y(90.0) + 6.5).toFloat(), (menuX + 10.0).toFloat(), (l.y(90.0) + 6.5).toFloat(), paint)

        val searchX = 372.0 * l.sx
        canvas.drawCircle((searchX - 1.5).toFloat(), (l.y(90.0) - 1.5).toFloat(), 6.5f, paint)
        canvas.drawLine((searchX + 3.5).toFloat(), (l.y(90.0) + 3.5).toFloat(), (searchX + 8.5).toFloat(), (l.y(90.0) + 8.5).toFloat(), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawCard(canvas: Canvas, l: EyewearLensTintLayout) {
        fill(Color.WHITE)
        paint.setShadowLayer(12f, 0f, 4f, 0x14000000)
        canvas.drawRect(l.cardX.toFloat(), l.cardTop.toFloat(), (l.cardX + l.cardW).toFloat(), (l.h - l.safeBottom + 6.0).toFloat(), paint)
        paint.clearShadowLayer()
        paint.shader = cardGradient
        paint.alpha = 255
        canvas.drawRect(0f, (l.cardTop - 90.0).toFloat(), l.w.toFloat(), l.cardTop.toFloat(), paint)
        paint.shader = null

        fill(CTA)
        canvas.drawCircle(l.downX.toFloat(), l.downY.toFloat(), l.downR.toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.strokeCap = Paint.Cap.ROUND
        val down = Path().apply {
            moveTo((l.downX - 5.5).toFloat(), (l.downY - 3.0).toFloat())
            lineTo(l.downX.toFloat(), (l.downY + 2.5).toFloat())
            lineTo((l.downX + 5.5).toFloat(), (l.downY - 3.0).toFloat())
        }
        paint.color = Color.WHITE
        paint.alpha = 255
        canvas.drawPath(down, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawTextAndActions(canvas: Canvas, l: EyewearLensTintLayout) {
        drawTracked(canvas, TITLE, l.titleX, l.y(622.0) - 16.0, 25f, bold, INK, 0.5f)
        drawTopRight(canvas, PRICE, l.priceRight, l.y(622.0) - 14.0, 22f, regular, INK)

        val starsY = l.y(649.0) - 8.0
        for (i in 0 until 5) {
            val star = starPaths?.getOrNull(i) ?: starPath(56.0 * l.sx + i * 16.0 + 7.5, starsY + 7.5, 7.5)
            drawStar(canvas, star, 56.0 * l.sx + i * 16.0, starsY, i < 4)
        }
        drawTracked(canvas, "  $RATING   |   $REVIEWS", 56.0 * l.sx + 5 * 16.0 + 4.0, starsY + 2.5, 10f, regular, MUTE, 0.4f)

        bodyLayout?.let {
            canvas.save()
            canvas.translate(l.bodyX.toFloat(), (l.y(681.0) - 10.0).toFloat())
            it.draw(canvas)
            canvas.restore()
        }

        fill(CTA)
        canvas.drawRect(l.bagX.toFloat(), l.y(748.0).toFloat(), (l.bagX + l.bagW).toFloat(), (l.y(748.0) + l.bagH).toFloat(), paint)
        drawCentered(canvas, BAG, l.bagX + l.bagW / 2.0, l.y(748.0) + l.bagH / 2.0, 15f, bold, CTA_INK, 0.6f)

        text.typeface = regular
        text.textSize = 10f
        text.color = MUTE
        val heartX = l.wishRight - (14.0 + 6.0 + wishWidth)
        val heartY = l.y(768.0) - 8.0
        fill(0xFFC9C9CE.toInt())
        canvas.save()
        canvas.translate(heartX.toFloat(), heartY.toFloat())
        canvas.drawPath(heartPath, paint)
        canvas.restore()
        drawGlyphsWithWidths(canvas, WISH, heartX + 20.0, (heartY - 1.0 - text.fontMetrics.ascent).toDouble(), 0.4f, wishGlyphWidths)
    }

    private fun drawFinger(canvas: Canvas, l: EyewearLensTintLayout, f: EyewearLensTintMotion.Frame) {
        if (f.finger <= 0.001) return
        val carry = carryPosition(l, f)
        val position = carry?.first ?: Pair(l.pourX(f.lens), l.pourY(f.lens))
        val x = position.first
        val y = position.second + 28.0
        val radius = 19.8
        fill(Color.argb((0.06 * f.finger * 255).roundToInt(), 20, 20, 20))
        canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.35f
        val borderColor = Color.argb((0.35 * f.finger * 255).roundToInt(), 20, 20, 20)
        paint.color = borderColor
        paint.alpha = Color.alpha(borderColor)
        canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawStar(canvas: Canvas, path: Path, x: Double, y: Double, full: Boolean) {
        val size = 15.0
        if (!full) {
            fill(0xFFD6D6DB.toInt())
            canvas.drawPath(path, paint)
            canvas.save()
            canvas.clipRect(x.toFloat(), y.toFloat(), (x + size / 2.0).toFloat(), (y + size).toFloat())
            fill(INK)
            canvas.drawPath(path, paint)
            canvas.restore()
        } else {
            fill(INK)
            canvas.drawPath(path, paint)
        }
    }

    private fun starPath(cx: Double, cy: Double, radius: Double): Path {
        val path = Path()
        for (i in 0 until 10) {
            val angle = -PI / 2.0 + i * PI / 5.0
            val r = if (i % 2 == 0) radius else radius * 0.45
            val x = cx + cos(angle) * r
            val y = cy + sin(angle) * r
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return path
    }

    private fun fill(color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.color = color
        paint.clearShadowLayer()
    }

    private fun drawTop(canvas: Canvas, value: String, x: Double, top: Double, size: Float, font: Typeface, color: Int, spacing: Float = 0f) {
        text.typeface = font
        text.textSize = size
        text.color = color
        val baseline = top - text.fontMetrics.ascent
        drawGlyphs(canvas, value, x, baseline.toDouble(), spacing, centered = false)
    }

    private fun drawTopRight(canvas: Canvas, value: String, right: Double, top: Double, size: Float, font: Typeface, color: Int) {
        text.typeface = font
        text.textSize = size
        text.color = color
        val widths = FloatArray(value.length)
        text.getTextWidths(value, widths)
        canvas.drawText(value, 0, value.length, (right - widths.sum()).toFloat(), (top - text.fontMetrics.ascent).toFloat(), text)
    }

    private fun drawCentered(canvas: Canvas, value: String, cx: Double, cy: Double, size: Float, font: Typeface, color: Int, spacing: Float = 0f) {
        text.typeface = font
        text.textSize = size
        text.color = color
        val total = trackedWidth(value, spacing)
        val baseline = cy - (text.fontMetrics.ascent + text.fontMetrics.descent) / 2.0
        drawGlyphs(canvas, value, cx - total / 2.0, baseline, spacing, centered = false)
    }

    private fun drawTracked(canvas: Canvas, value: String, x: Double, top: Double, size: Float, font: Typeface, color: Int, spacing: Float = 0f) {
        text.typeface = font
        text.textSize = size
        text.color = color
        drawGlyphs(canvas, value, x, (top - text.fontMetrics.ascent).toDouble(), spacing, centered = false)
    }

    private fun trackedWidth(value: String, spacing: Float): Double {
        val widths = FloatArray(value.length)
        text.getTextWidths(value, widths)
        return widths.sum().toDouble() + spacing * (value.length - 1).coerceAtLeast(0)
    }

    private fun drawGlyphs(canvas: Canvas, value: String, x: Double, baseline: Double, spacing: Float, centered: Boolean) {
        var px = if (centered) x - trackedWidth(value, spacing) / 2.0 else x
        val widths = FloatArray(value.length)
        text.getTextWidths(value, widths)
        value.forEachIndexed { index, _ ->
            canvas.drawText(value, index, index + 1, px.toFloat(), baseline.toFloat(), text)
            px += widths[index] + spacing
        }
    }

    private fun drawGlyphsWithWidths(
        canvas: Canvas,
        value: String,
        x: Double,
        baseline: Double,
        spacing: Float,
        widths: FloatArray,
    ) {
        var px = x
        value.forEachIndexed { index, _ ->
            canvas.drawText(value, index, index + 1, px.toFloat(), baseline.toFloat(), text)
            px += widths[index] + spacing
        }
    }
}

@RequiresApi(33)
internal class EyewearLensTintRuntime(private val portrait: Bitmap) {
    private val shader = RuntimeShader(EyewearLensTintShader.SOURCE)
    private val photo = BitmapShader(portrait, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        photo.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        shader.setInputShader("uPhoto", photo)
        paint.shader = shader
        paint.alpha = 255
    }

    fun setLayout(l: EyewearLensTintLayout) {
        // BitmapShader coordinates are bitmap pixels; this matrix maps them into the screen rect
        // used by the AGSL pass (the inverse matrix samples the wrong part of the portrait).
        val scaleX = l.photoW / portrait.width
        val scaleY = l.photoH / portrait.height
        matrix.reset()
        matrix.setScale(scaleX.toFloat(), scaleY.toFloat())
        matrix.postTranslate(l.photoX.toFloat(), l.photoY.toFloat())
        photo.setLocalMatrix(matrix)
    }

    fun draw(canvas: Canvas, l: EyewearLensTintLayout, f: EyewearLensTintMotion.Frame) {
        shader.setFloatUniform("uLensA", l.lensAX.toFloat(), l.lensAY.toFloat(), l.lensR.toFloat(), 0f)
        shader.setFloatUniform("uLensB", l.lensBX.toFloat(), l.lensBY.toFloat(), l.lensR.toFloat(), 0f)
        shader.setFloatUniform("uTint", f.from.toFloat(), f.to.toFloat(), f.pour.toFloat(), f.lens.toFloat())
        shader.setFloatUniform("uGlare", f.glare.toFloat(), 0f, 0f, 0f)
        shader.setFloatUniform("uTime", f.time.toFloat())
        paint.shader = shader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, l.w.toFloat(), l.h.toFloat(), paint)
    }
}
