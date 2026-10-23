package com.ngocdevv.nativecook.blocks.charts.glasspeakchart

import android.annotation.TargetApi
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.ngocdevv.nativecook.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal class GlassPeakChartView(
    context: Context,
    autoPlay: Boolean,
    reducedMotion: Boolean,
) : View(context), Choreographer.FrameCallback {
    private val simulation = GlassPeakSimulation(autoPlay, reducedMotion)
    private val material = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GlassPeakGpuMaterial() else null
    private val materialPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val regularTypeface = ResourcesCompat.getFont(context, R.font.glasspeakchart_inter_regular)
    private val boldTypeface = ResourcesCompat.getFont(context, R.font.glasspeakchart_inter_bold)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = regularTypeface
    }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = boldTypeface
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val perspective = Matrix()
    private var lastFrame = 0L
    private var scheduled = false
    private var reduced = reducedMotion

    var onStateChanged: ((period: Int, value: Int, busy: Boolean) -> Unit)? = null

    init {
        setBackgroundColor(Color.BLACK)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        simulation.onHaptic = { kind ->
            val constant = when (kind) {
                GlassPeakHaptic.Light -> HapticFeedbackConstants.CONTEXT_CLICK
                GlassPeakHaptic.Selection -> HapticFeedbackConstants.CLOCK_TICK
                GlassPeakHaptic.Medium -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
                GlassPeakHaptic.Heavy -> HapticFeedbackConstants.LONG_PRESS
            }
            performHapticFeedback(constant)
        }
        simulation.onAccessibilityValue = { announceForAccessibility(it) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        schedule()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        scheduled = false
        lastFrame = 0L
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!isAttachedToWindow || !isShown || windowVisibility != VISIBLE) {
            lastFrame = 0L
            return
        }
        if (lastFrame != 0L) {
            simulation.step((frameTimeNanos - lastFrame) / 1_000_000_000.0)
            onStateChanged?.invoke(simulation.period, simulation.value, simulation.busy)
        }
        lastFrame = frameTimeNanos
        invalidate()
        schedule()
    }

    fun setReducedMotion(value: Boolean) {
        reduced = value
    }

    fun contact(down: Boolean) = simulation.contact(down)

    fun press(action: GlassPeakAction, down: Boolean, user: Boolean = true) = simulation.press(action, down, user)

    fun request(action: GlassPeakAction, user: Boolean = true) = simulation.request(action, user)

    fun fingerDown(x: Float, y: Float) = simulation.fingerDown(x, y, true)

    fun fingerMove(x: Float, y: Float, velocityX: Float) = simulation.fingerMove(x, y, velocityX, true)

    fun fingerUp(velocityX: Float) = simulation.fingerUp(velocityX, true)

    fun accessibilityAdjust(direction: Int) = simulation.accessibilityAdjust(direction)

    private fun schedule() {
        if (!scheduled && isAttachedToWindow) {
            scheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / GlassPeakDesignWidth, height / GlassPeakDesignHeight)
        val left = (width - GlassPeakDesignWidth * scale) / 2f
        val top = (height - GlassPeakDesignHeight * scale) / 2f
        val frame = simulation.frame
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        setPerspective(frame)
        canvas.concat(perspective)
        if (material != null) {
            material.setFrame(frame, scale, reduced)
            materialPaint.shader = material.shader
            canvas.drawRect(-80f, -80f, 482f, 954f, materialPaint)
            materialPaint.shader = null
        } else {
            drawFallback(canvas, frame)
        }
        drawType(canvas, frame)
        canvas.restore()
    }

    private fun setPerspective(frame: GlassPeakFrame) {
        val sinX = sin(frame.tiltX)
        val cosX = cos(frame.tiltX)
        val sinY = sin(frame.tiltY)
        val cosY = cos(frame.tiltY)
        val scale = frame.cardScale
        val a = cosY * scale
        val b = sinY * sinX * scale
        val e = cosX * scale
        val g = sinY * scale / 900f
        val h = -cosY * sinX * scale / 900f
        perspective.setValues(
            floatArrayOf(
                a + 201f * g,
                b + 201f * h,
                201f - (a + 201f * g) * 201f - (b + 201f * h) * 416f,
                416f * g,
                e + 416f * h,
                416f - 416f * g * 201f - (e + 416f * h) * 416f,
                g,
                h,
                1f - g * 201f - h * 416f,
            ),
        )
    }

    private fun drawFallback(canvas: Canvas, frame: GlassPeakFrame) {
        materialPaint.style = Paint.Style.FILL
        materialPaint.shader = LinearGradient(
            201f,
            GlassPeakCard.y,
            201f,
            GlassPeakCard.y + GlassPeakCard.height,
            intArrayOf(Color.rgb(14, 24, 18), Color.rgb(3, 18, 8), Color.rgb(8, 245, 48)),
            floatArrayOf(0f, .62f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(GlassPeakCard.x, GlassPeakCard.y, GlassPeakCard.x + GlassPeakCard.width, GlassPeakCard.y + GlassPeakCard.height, 64f, 64f, materialPaint)
        materialPaint.shader = null
        materialPaint.style = Paint.Style.STROKE
        materialPaint.strokeWidth = 2f
        materialPaint.color = Color.rgb(183, 255, 200)
        canvas.drawRoundRect(GlassPeakCard.x, GlassPeakCard.y, GlassPeakCard.x + GlassPeakCard.width, GlassPeakCard.y + GlassPeakCard.height, 64f, 64f, materialPaint)
        path.reset()
        for (index in 0 until GlassPeakSamples) {
            val u = index.toFloat() / (GlassPeakSamples - 1)
            val x = glassPeakX(u)
            val y = glassPeakY(frame.ys[index])
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        materialPaint.strokeWidth = 3f
        materialPaint.color = Color.rgb(25, 255, 64)
        materialPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path, materialPaint)
        materialPaint.style = Paint.Style.FILL
        canvas.drawCircle(frame.orbX, frame.orbY, frame.orbRadius, materialPaint)
        materialPaint.color = Color.rgb(215, 255, 220)
        canvas.drawCircle(frame.orbX - frame.orbRadius * .25f, frame.orbY - frame.orbRadius * .3f, frame.orbRadius * .3f, materialPaint)
        for (index in 0 until 4) canvas.drawCircle(frame.nodeX[index], frame.nodeY[index], 4.5f, materialPaint)
    }

    private fun drawType(canvas: Canvas, frame: GlassPeakFrame) {
        val alpha = (255f * frame.presented).roundToInt().coerceIn(0, 255)
        textPaint.typeface = regularTypeface
        textPaint.textSize = 12f
        textPaint.color = Color.WHITE
        textPaint.alpha = (alpha * .62f).roundToInt()
        val statusOld = if (frame.statusPrevious == 1) "Updating…" else "Data updated just now"
        val statusNew = if (frame.status == 1) "Updating…" else "Data updated just now"
        drawRollingLabel(canvas, statusOld, statusNew, 72f, 236f, frame.statusMix, 6f, textPaint)

        textPaint.textSize = 13f
        textPaint.alpha = (alpha * .88f).roundToInt()
        textPaint.textAlign = Paint.Align.CENTER
        val period = GlassPeakPeriods[frame.period]
        val oldPeriod = GlassPeakPeriods[frame.periodPrevious]
        canvas.save()
        canvas.clipRect(250f, 218f, 321f, 245f)
        canvas.scale(1f - .03f * frame.pillPress, 1f - .03f * frame.pillPress, 296f, 232f)
        drawRollingLabel(canvas, oldPeriod.label, period.label, 288f, 237f, frame.periodMix, 8f, textPaint)
        canvas.restore()

        textPaint.textSize = 14f
        textPaint.alpha = (alpha * .78f).roundToInt()
        canvas.drawText("Conversion rate", 201f, 512f, textPaint)

        boldPaint.typeface = boldTypeface
        boldPaint.textSize = 68f
        boldPaint.alpha = (alpha * frame.numberAlpha).roundToInt().coerceIn(0, 255)
        boldPaint.shader = LinearGradient(201f, 521f, 201f, 576f, Color.rgb(246, 255, 248), Color.rgb(207, 255, 217), Shader.TileMode.CLAMP)
        val number = "${frame.value.roundToInt().coerceIn(0, 999)}%"
        if (frame.numberGlow > .01f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            boldPaint.shader = null
            boldPaint.color = Color.rgb(43, 255, 90)
            boldPaint.alpha = (alpha * frame.numberGlow * .46f).roundToInt().coerceIn(0, 255)
            boldPaint.maskFilter = BlurMaskFilter(9f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawText(number, 201f, 576f, boldPaint)
            boldPaint.maskFilter = null
            boldPaint.shader = LinearGradient(201f, 521f, 201f, 576f, Color.rgb(246, 255, 248), Color.rgb(207, 255, 217), Shader.TileMode.CLAMP)
            boldPaint.alpha = (alpha * frame.numberAlpha).roundToInt().coerceIn(0, 255)
        }
        canvas.save()
        canvas.scale(frame.numberScale, frame.numberScale, 201f, 552f)
        canvas.drawText(number, 201f, 576f, boldPaint)
        canvas.restore()
        boldPaint.shader = null

        textPaint.typeface = regularTypeface
        textPaint.textSize = 12f
        textPaint.color = Color.WHITE
        textPaint.alpha = (alpha * .86f).roundToInt()
        val oldSubAlpha = 1f - glassPeakSmoothRange(frame.periodMix, 0f, .5f)
        val newSubAlpha = glassPeakSmoothRange(frame.periodMix, .5f, 1f)
        textPaint.alpha = (alpha * .86f * oldSubAlpha).roundToInt().coerceIn(0, 255)
        canvas.drawText(oldPeriod.sub, 201f, 602f, textPaint)
        textPaint.alpha = (alpha * .86f * newSubAlpha).roundToInt().coerceIn(0, 255)
        canvas.drawText(period.sub, 201f, 602f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawRollingLabel(
        canvas: Canvas,
        old: String,
        new: String,
        x: Float,
        baseline: Float,
        mix: Float,
        travel: Float,
        paint: Paint,
    ) {
        val baseAlpha = paint.alpha
        val oldAlpha = 1f - glassPeakSmoothRange(mix, 0f, .6f)
        val newAlpha = glassPeakSmoothRange(mix, .4f, 1f)
        paint.alpha = (baseAlpha * oldAlpha).roundToInt().coerceIn(0, 255)
        canvas.drawText(old, x, baseline - travel * mix, paint)
        paint.alpha = (baseAlpha * newAlpha).roundToInt().coerceIn(0, 255)
        canvas.drawText(new, x, baseline + travel * (1f - mix), paint)
        paint.alpha = baseAlpha
    }

    @TargetApi(33)
    private class GlassPeakGpuMaterial {
        val shader = RuntimeShader(GlassPeakShaderSource)

        fun setFrame(frame: GlassPeakFrame, scale: Float, still: Boolean) {
            shader.setFloatUniform("px", max(1f, scale))
            shader.setFloatUniform("clock", if (still) 0f else frame.time)
            shader.setFloatUniform("presented", frame.presented)
            shader.setFloatUniform("ys", frame.ys)
            shader.setFloatUniform("trace", frame.lit, frame.tip, frame.tube)
            shader.setFloatUniform("beam", frame.beam, frame.beamReach)
            shader.setFloatUniform("orb", frame.orbX, frame.orbY, frame.orbRadius, frame.orbGain)
            shader.setFloatUniform("nodeX", frame.nodeX)
            shader.setFloatUniform("nodeY", frame.nodeY)
            shader.setFloatUniform("nodeLit", frame.nodeLit)
            shader.setFloatUniform("nodeScale", frame.nodeScale)
            shader.setFloatUniform("drops", frame.drops)
            shader.setFloatUniform("nodeBottom", frame.nodeBottom)
            shader.setFloatUniform("pool", frame.poolLevel, frame.poolHumpX, frame.poolHump, frame.poolSlope)
            shader.setFloatUniform("gain", frame.poolGain)
            shader.setFloatUniform("stir", frame.poolStir)
            shader.setFloatUniform("splashX", frame.splashX)
            shader.setFloatUniform("splashY", frame.splashY)
            shader.setFloatUniform("splashR", frame.splashRadius)
            shader.setFloatUniform("ring", frame.ringX, frame.ringY, frame.ringRadius, frame.ringAlpha)
            shader.setFloatUniform("flash", frame.flash)
            shader.setFloatUniform("glint", frame.rimGlint)
            shader.setFloatUniform("tilt", frame.tiltX, frame.tiltY)
            shader.setFloatUniform("touch", frame.touchX, frame.touchY, frame.touchAlpha)
            shader.setFloatUniform("control", frame.pillPress, frame.refreshPress, frame.chevron, frame.spin)
        }
    }
}
