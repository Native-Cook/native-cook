package com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

import com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding.MicCordOnboardingPhysics.LOOP
import com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding.MicCordOnboardingPhysics.N
import com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding.MicCordOnboardingPhysics.NOTES
import com.ngocdevv.nativecook.blocks.onboarding.miccordonboarding.MicCordOnboardingPhysics.Rope

/** Native Canvas artwork for the mic, cable, contours, notes, copy, and CTA. */
internal class MicCordOnboardingArt(context: Context) {
    private val titleTypeface = Typeface.createFromAsset(
        context.assets,
        "miccordonboarding/miccordonboarding_Montserrat-Bold.ttf",
    )
    private val buttonTypeface = Typeface.createFromAsset(
        context.assets,
        "miccordonboarding/miccordonboarding_Inter-SemiBold.ttf",
    )

    private val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val cableShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
        strokeJoin = Paint.Join.ROUND
    }
    private val cablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.6f
        strokeJoin = Paint.Join.ROUND
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -19f,
            0f,
            19f,
            0f,
            intArrayOf(0xFF7A1270.toInt(), 0xFFE43FCF.toInt(), 0xFFB0219E.toInt(), 0xFF5E0D56.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    private val collarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -30f,
            0f,
            30f,
            0f,
            intArrayOf(0xFF5A0C52.toInt(), 0xFFFF7BE6.toInt(), 0xFFC22EB0.toInt(), 0xFF4D0A46.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -33f,
            0f,
            33f,
            0f,
            intArrayOf(
                0xFF8F1683.toInt(),
                0xFFFF6BE3.toInt(),
                0xFFFF4FD8.toInt(),
                0xFFA81D98.toInt(),
                0xFF5A0C52.toInt(),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        color = 0xA63C0637.toInt()
    }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -6f,
            0f,
            6f,
            0f,
            intArrayOf(0xFF6A0F60.toInt(), 0xFFFF9BEC.toInt(), 0xFF6A0F60.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    private val micHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59FFFFFF
        maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
    }
    private val noteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val noteStemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeCap = Paint.Cap.ROUND
    }
    private val noteBeamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = titleTypeface
        textSize = 30f
        isLinearText = true
    }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = buttonTypeface
        textSize = 17f
        isLinearText = true
        color = 0xFFFFFFFF.toInt()
    }
    private val buttonTextWidth = buttonTextPaint.measureText("Start Listening")
    private val buttonFontMetrics = buttonTextPaint.fontMetrics
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val micHandlePath = Path().apply {
        moveTo(-13f, 0f)
        lineTo(13f, 0f)
        lineTo(19f, 70f)
        lineTo(-19f, 70f)
        close()
    }
    private val noteColors = intArrayOf(0xFFFF7BE6.toInt(), 0xFFB9A2FF.toInt(), 0xFF8FF0D0.toInt())
    private val firstTitleSpans = listOf(
        Span("A Quick & ", 0xFFFFFFFF.toInt()),
        Span("Easy Way", 0xFFFF63DD.toInt()),
    )
    private val secondTitleSpans = listOf(Span("to Make Your Own", 0xFFFFFFFF.toInt()))
    private val thirdTitleSpans = listOf(Span("Podcast", 0xFFFFFFFF.toInt()))
    private val firstTitleWidth by lazy { trackedWidth(titlePaint, firstTitleSpans, -0.3f) }
    private val secondTitleWidth by lazy { trackedWidth(titlePaint, secondTitleSpans, -0.3f) }
    private val thirdTitleWidth by lazy { trackedWidth(titlePaint, thirdTitleSpans, -0.3f) }

    private var buttonLayoutWidth = -1f
    private var buttonLayoutTop = -1f
    private var buttonLayoutLeft = -1f

    private val cablePath = Path()
    private val coilPath = Path()
    private val contourPath = Path()
    private val noteFlagPath = Path()
    private val position = FloatArray(2)
    private val tangent = FloatArray(2)
    private val pathMeasure = PathMeasure()

    fun drawScene(
        canvas: Canvas,
        width: Float,
        height: Float,
        topInset: Float,
        bottomInset: Float,
        rope: Rope,
        time: Double,
        buttonPressed: Boolean,
    ) {
        canvas.drawColor(0xFF0C0B0F.toInt())
        ensureButtonLayout(width, height, bottomInset)
        drawContours(canvas, width, height, topInset, time)
        drawCable(canvas, rope)
        drawMic(canvas, rope)
        drawNotes(canvas, rope)
        drawCopy(canvas, width, height)
        drawButton(canvas, width, height, bottomInset, buttonPressed)
    }

    private fun drawContours(canvas: Canvas, width: Float, height: Float, topInset: Float, time: Double) {
        val phase = (2.0 * PI * time / LOOP).toFloat()
        for (side in 0..1) {
            for (i in 0 until 16) {
                contourPath.reset()
                val base = if (side == 0) topInset + 40f + i * 7f else height - 210f + i * 7f
                for (k in 0..40) {
                    val x = if (side == 0) -20f + k * 7f else width - 260f + k * 7f
                    val y = base + 26f * sin(x * 0.018f + i * 0.16f + phase + side * 2f) +
                        if (side == 0) -k * 1.4f else k * 0.6f
                    if (k == 0) contourPath.moveTo(x, y) else contourPath.lineTo(x, y)
                }
                contourPaint.color = if (i % 2 == 0) 0xFFFF4FD8.toInt() else 0xFF8A5CFF.toInt()
                contourPaint.alpha = ((0.22 + 0.25 * (1.0 - i / 16.0)) * 255).toInt().coerceIn(0, 255)
                canvas.drawPath(contourPath, contourPaint)
            }
        }
    }

    private fun drawCable(canvas: Canvas, rope: Rope) {
        cablePath.reset()
        cablePath.moveTo(rope.x[0].toFloat(), rope.y[0].toFloat())
        for (i in 1 until N - 1) {
            cablePath.quadTo(
                rope.x[i].toFloat(),
                rope.y[i].toFloat(),
                ((rope.x[i] + rope.x[i + 1]) * 0.5).toFloat(),
                ((rope.y[i] + rope.y[i + 1]) * 0.5).toFloat(),
            )
        }
        cablePath.lineTo(rope.x[N - 1].toFloat(), rope.y[N - 1].toFloat())
        pathMeasure.setPath(cablePath, false)
        val length = pathMeasure.length
        coilPath.reset()
        var first = true
        var distance = 0f
        while (distance <= length) {
            if (!pathMeasure.getPosTan(distance, position, tangent)) break
            val normalX = -tangent[1]
            val normalY = tangent[0]
            val edge = min(1f, min((length - distance) / 20f, distance / 20f + 0.2f))
            val offset = 5.5f * sin((distance / 8.5f) * (2f * PI.toFloat())) * edge
            val x = position[0] + normalX * offset
            val y = position[1] + normalY * offset
            if (first) {
                coilPath.moveTo(x, y)
                first = false
            } else {
                coilPath.lineTo(x, y)
            }
            distance += 1.6f
        }
        cableShadowPaint.color = 0xFF7D1172.toInt()
        cablePaint.color = 0xFFFF5AD8.toInt()
        canvas.drawPath(coilPath, cableShadowPaint)
        canvas.drawPath(coilPath, cablePaint)
    }

    private fun drawMic(canvas: Canvas, rope: Rope) {
        val angle = kotlin.math.atan2(
            rope.y[N - 1] - rope.y[N - 3],
            rope.x[N - 1] - rope.x[N - 3],
        )
        canvas.save()
        canvas.translate(rope.x[N - 1].toFloat(), rope.y[N - 1].toFloat())
        canvas.rotate((angle * 180.0 / PI - 90.0).toFloat())
        canvas.scale(1.4f, 1.4f)

        canvas.drawPath(micHandlePath, handlePaint)
        canvas.drawRoundRect(-30f, 66f, 30f, 79f, 4f, 4f, collarPaint)
        canvas.drawRoundRect(-33f, 78f, 33f, 174f, 33f, 33f, headPaint)
        for (i in 0 until 9) {
            val y = 96f + i * 8.5f
            val half = 26f * sqrt(max(0f, 1f - ((y - 126f) / 52f) * ((y - 126f) / 52f)))
            canvas.drawLine(-half, y, half, y, slotPaint)
        }
        canvas.drawRect(-5f, 80f, 5f, 172f, bandPaint)
        canvas.drawOval(-24f, 92f, -12f, 146f, micHighlightPaint)
        canvas.restore()
    }

    private fun drawNotes(canvas: Canvas, rope: Rope) {
        for (i in 0 until NOTES) {
            val o = i * 6
            val age = rope.notes[o + 4]
            if (age > 1.6) continue
            val alpha = min(1.0, age / 0.15) * (1.0 - smooth((age - 0.9) / 0.7))
            val color = noteColors[i % 3]
            drawNote(
                canvas,
                rope.notes[o].toFloat(),
                rope.notes[o + 1].toFloat(),
                rope.notes[o + 5].toInt(),
                alpha.toFloat(),
                (sin(age * 4.0 + i) * 18.0).toFloat(),
                color,
            )
        }
    }

    private fun smooth(value: Double): Double {
        val x = value.coerceIn(0.0, 1.0)
        return x * x * (3.0 - 2.0 * x)
    }

    private fun drawNote(canvas: Canvas, x: Float, y: Float, kind: Int, alpha: Float, rotation: Float, color: Int) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation)
        val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
        noteFillPaint.color = color
        noteFillPaint.alpha = a
        noteStemPaint.color = color
        noteStemPaint.alpha = a
        noteBeamPaint.color = color
        noteBeamPaint.alpha = a

        canvas.save()
        canvas.rotate(-20f)
        canvas.drawOval(-7f, -5f, 7f, 5f, noteFillPaint)
        canvas.restore()
        canvas.drawLine(6f, -2f, 6f, -26f, noteStemPaint)
        if (kind == 1) {
            canvas.save()
            canvas.translate(16f, -4f)
            canvas.rotate(-20f)
            canvas.drawOval(-7f, -5f, 7f, 5f, noteFillPaint)
            canvas.restore()
            canvas.drawLine(22f, -6f, 22f, -30f, noteStemPaint)
            canvas.drawLine(6f, -25f, 22f, -29f, noteBeamPaint)
        } else if (kind == 0) {
            noteFlagPath.reset()
            noteFlagPath.moveTo(6f, -26f)
            noteFlagPath.cubicTo(14f, -22f, 16f, -16f, 12f, -10f)
            canvas.drawPath(noteFlagPath, noteStemPaint)
        }
        canvas.restore()
    }

    private data class Span(val text: String, val color: Int)

    private fun trackedWidth(paint: Paint, spans: List<Span>, tracking: Float): Float {
        var width = 0f
        spans.forEach { span ->
            span.text.forEachIndexed { index, _ ->
                width += paint.measureText(span.text, index, index + 1) + tracking
            }
        }
        return width - tracking
    }

    private fun drawTrackedCentered(canvas: Canvas, centerX: Float, baseline: Float, spans: List<Span>, width: Float, tracking: Float) {
        var cursor = centerX - width * 0.5f
        spans.forEach { span ->
            titlePaint.color = span.color
            span.text.forEachIndexed { index, _ ->
                canvas.drawText(span.text, index, index + 1, cursor, baseline, titlePaint)
                cursor += titlePaint.measureText(span.text, index, index + 1) + tracking
            }
        }
    }

    private fun drawCopy(canvas: Canvas, width: Float, height: Float) {
        val top = height * 0.56f
        drawTrackedCentered(
            canvas,
            width * 0.5f,
            top + 31f,
            firstTitleSpans,
            firstTitleWidth,
            -0.3f,
        )
        drawTrackedCentered(canvas, width * 0.5f, top + 69f, secondTitleSpans, secondTitleWidth, -0.3f)
        drawTrackedCentered(canvas, width * 0.5f, top + 107f, thirdTitleSpans, thirdTitleWidth, -0.3f)

        val dotCenterY = top + 147f
        val total = 18f + 8f + 6f + 8f + 6f
        var x = width * 0.5f - total * 0.5f
        dotPaint.color = 0xFFFF63DD.toInt()
        canvas.drawRoundRect(x, dotCenterY - 3f, x + 18f, dotCenterY + 3f, 3f, 3f, dotPaint)
        x += 26f
        dotPaint.color = 0x4DFFFFFF
        canvas.drawRoundRect(x, dotCenterY - 3f, x + 6f, dotCenterY + 3f, 3f, 3f, dotPaint)
        x += 14f
        canvas.drawRoundRect(x, dotCenterY - 3f, x + 6f, dotCenterY + 3f, 3f, 3f, dotPaint)
    }

    private fun drawButton(canvas: Canvas, width: Float, height: Float, bottomInset: Float, pressed: Boolean) {
        val buttonWidth = 240f
        val buttonHeight = 56f
        val left = buttonLayoutLeft
        val top = buttonLayoutTop
        canvas.save()
        if (pressed) {
            canvas.translate(width * 0.5f, top + buttonHeight * 0.5f)
            canvas.scale(0.97f, 0.97f)
            canvas.translate(-width * 0.5f, -(top + buttonHeight * 0.5f))
        }
        canvas.drawRoundRect(left, top, left + buttonWidth, top + buttonHeight, 28f, 28f, buttonFillPaint)
        buttonTextPaint.color = 0xFFFFFFFF.toInt()
        val baseline = top + buttonHeight * 0.5f - (buttonFontMetrics.ascent + buttonFontMetrics.descent) * 0.5f
        canvas.drawText("Start Listening", left + (buttonWidth - buttonTextWidth) * 0.5f, baseline, buttonTextPaint)
        canvas.restore()
    }

    private fun ensureButtonLayout(width: Float, height: Float, bottomInset: Float) {
        val top = height - bottomInset - 34f - 56f
        val left = (width - 240f) * 0.5f
        if (width == buttonLayoutWidth && top == buttonLayoutTop && left == buttonLayoutLeft) return
        buttonLayoutWidth = width
        buttonLayoutTop = top
        buttonLayoutLeft = left
        buttonFillPaint.shader = LinearGradient(
            left,
            top,
            left + 240f,
            top + 56f,
            intArrayOf(0xFFFFA3D7.toInt(), 0xFFFF4FD8.toInt(), 0xFFC46BFF.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
    }
}
