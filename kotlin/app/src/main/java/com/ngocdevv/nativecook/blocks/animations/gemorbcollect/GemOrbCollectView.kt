package com.ngocdevv.nativecook.blocks.animations.gemorbcollect

import android.content.Context
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.provider.Settings
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.graphics.Rect as AndroidRect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ngocdevv.nativecook.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal class GemOrbCollectView(context: Context) : View(context), Choreographer.FrameCallback {
    var autoPlay: Boolean
        get() = simulation.autoPlay
        set(value) {
            simulation.autoPlay = value
        }

    private val simulation = GemOrbCollectSimulation()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val boldTypeface = ResourcesCompat.getFont(context, R.font.gemorbcollect_inter_bold)
        ?: Typeface.create("sans-serif", Typeface.BOLD)
    private val regularTypeface = ResourcesCompat.getFont(context, R.font.gemorbcollect_inter_regular)
        ?: Typeface.create("sans-serif", Typeface.NORMAL)
    private val orbAtlas: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.gemorbcollect_orb_atlas)
    private val sourceRect = Rect()
    private val gemPath = Path()
    private val innerPath = Path()
    private val starPath = Path()
    private val scratch = FloatArray(5)
    private val additiveBlend = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val gemShader = GemOrbCollectGemShader()
    private val materials = GemOrbCollectMaterials()
    private val shaderLights = FloatArray(24)
    private val lightPoint = FloatArray(5)
    private lateinit var scene: GemOrbCollectLayout
    private var lastFrameNanos = 0L
    private var scheduled = false
    private var touchTarget = TouchTarget.NONE
    private var lastTouchNanos = 0L
    private var lastHits = 0
    private var lastPhase = GemOrbCollectSimulation.Phase.BEFORE
    private var motionCheckFrame = 0
    private val density = resources.displayMetrics.density
    private val accessibility = ControlsAccessibility()

    init {
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        simulation.reducedMotion = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleFrame()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        scheduled = false
        lastFrameNanos = 0L
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        val insets = ViewCompat.getRootWindowInsets(this)?.getInsets(WindowInsetsCompat.Type.systemBars())
        scene = GemOrbCollectLayout.create(
            width.toFloat(),
            height.toFloat(),
            insets?.top?.toFloat() ?: 0f,
            insets?.bottom?.toFloat() ?: 0f,
            density,
        )
        simulation.resize(scene)
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!isAttachedToWindow || visibility != VISIBLE) return
        if (lastFrameNanos != 0L) {
            simulation.step(((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).toFloat())
        }
        if (++motionCheckFrame >= 60) {
            motionCheckFrame = 0
            simulation.reducedMotion = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }
        lastFrameNanos = frameTimeNanos
        invalidate()
        scheduleFrame()
    }

    override fun onDraw(canvas: Canvas) {
        if (!::scene.isInitialized) return
        val frame = simulation.frame()
        drawBackdrop(canvas, frame)
        drawOrbs(canvas, frame, front = false)
        drawGem(canvas, frame, scene.gemX, scene.gemY, scene.gemRadius * frame.scale, miniature = false)
        drawOrbs(canvas, frame, front = true)
        drawTally(canvas, frame)
        drawCopy(canvas, frame)
        drawCapsule(canvas, frame)
        drawButton(canvas, frame)
        updateFeedback(frame)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val now = event.eventTime * 1_000_000L
        val dt = if (lastTouchNanos == 0L) 1f / 60f else ((now - lastTouchNanos) / 1_000_000_000.0).toFloat()
        lastTouchNanos = now
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchTarget = when {
                    simulation.onButtonDown(event.x, event.y) -> TouchTarget.BUTTON
                    simulation.onGemDown(event.x, event.y) -> TouchTarget.GEM
                    else -> TouchTarget.NONE
                }
                if (touchTarget != TouchTarget.NONE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                return touchTarget != TouchTarget.NONE
            }
            MotionEvent.ACTION_MOVE -> if (touchTarget == TouchTarget.GEM) {
                simulation.onGemMove(event.x, event.y, dt)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (touchTarget == TouchTarget.GEM) simulation.onGemUp()
                if (touchTarget == TouchTarget.BUTTON) {
                    simulation.onButtonUp(event.x, event.y)
                    performClick()
                }
                touchTarget = TouchTarget.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                simulation.cancelTouch()
                touchTarget = TouchTarget.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = accessibility

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibility.hover(event) || super.dispatchHoverEvent(event)

    private fun scheduleFrame() {
        if (scheduled) return
        scheduled = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun drawBackdrop(canvas: Canvas, frame: GemOrbCollectSimulation.Frame) {
        if (materials.backdrop(canvas, paint, scene, frame)) return
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            scene.height * 0.64f,
            intArrayOf(Color.rgb(23, 23, 94), Color.rgb(10, 10, 48), Color.BLACK),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, scene.width, scene.height, paint)
        paint.shader = null

        val bloomRadius = scene.gemRadius * (2.55f - 0.3f * frame.hush)
        paint.shader = RadialGradient(
            scene.gemX,
            scene.gemY,
            bloomRadius,
            intArrayOf(
                ColorUtils.setAlphaComponent(Color.rgb(42, 44, 224), (78 + frame.charge * 36).roundToInt()),
                ColorUtils.setAlphaComponent(Color.rgb(27, 18, 120), 36),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(scene.gemX, scene.gemY, bloomRadius, paint)
        paint.shader = null

        drawDust(canvas, frame)
        if (frame.shock > 0f) {
            val radius = scene.gemRadius * (0.9f + 5.2f * frame.shock)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2f, scene.scale)
            paint.color = ColorUtils.setAlphaComponent(Color.rgb(120, 180, 255), ((1f - frame.shock) * 100f).roundToInt())
            canvas.drawCircle(scene.gemX, scene.gemY, radius, paint)
            paint.style = Paint.Style.FILL
        }
        if (frame.beams > 0f) drawBeams(canvas, frame)
    }

    private fun drawDust(canvas: Canvas, frame: GemOrbCollectSimulation.Frame) {
        for (index in 0 until 40) {
            val column = index % 5
            val row = index / 5
            val seedX = GemOrbCollectMath.hash(index + 7)
            val seedY = GemOrbCollectMath.hash(index + 29)
            var x = (column + 0.36f + seedX * 0.28f) * scene.width / 5f
            var y = (row + 0.36f + seedY * 0.28f) * scene.height / 8f
            if (!simulation.reducedMotion) {
                x += sin(frame.clock * 0.52f + seedX * 6.28f) * 5f * scene.scale
                y += cos(frame.clock * 1.04f + seedY * 6.28f) * 5f * scene.scale
            }
            val near = index % 9 < 3 && index % 3 == 2
            val radius = (if (near) 5f else 1.4f + index % 3) * scene.scale
            val alpha = if (near) 20 else 45 + (25f * sin(frame.clock + index)).roundToInt()
            paint.color = ColorUtils.setAlphaComponent(
                if (index % 3 == 0) Color.rgb(108, 104, 255) else Color.rgb(142, 105, 255),
                alpha.coerceIn(8, 80),
            )
            if (near) paint.maskFilter = BlurMaskFilter(radius * 0.8f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(x, y, radius, paint)
            paint.maskFilter = null
        }
    }

    private fun drawBeams(canvas: Canvas, frame: GemOrbCollectSimulation.Frame) {
        paint.style = Paint.Style.STROKE
        for (index in 0 until 7) {
            val angle = frame.yaw + index * (PI.toFloat() * 2f / 7f)
            val length = hypot(scene.width, scene.height) * (0.32f + 0.035f * (index % 3))
            paint.strokeWidth = scene.scale * (4f + index % 3 * 3f)
            paint.color = ColorUtils.setAlphaComponent(
                if (index % 2 == 0) Color.rgb(69, 225, 255) else Color.rgb(255, 88, 213),
                (frame.beams * 50f).roundToInt(),
            )
            paint.maskFilter = BlurMaskFilter(paint.strokeWidth * 1.7f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawLine(
                scene.gemX + cos(angle) * scene.gemRadius,
                scene.gemY + sin(angle) * scene.gemRadius,
                scene.gemX + cos(angle) * length,
                scene.gemY + sin(angle) * length,
                paint,
            )
        }
        paint.maskFilter = null
        paint.style = Paint.Style.FILL
    }

    private fun drawOrbs(canvas: Canvas, frame: GemOrbCollectSimulation.Frame, front: Boolean) {
        simulation.orbs().forEachIndexed { index, orb ->
            simulation.orbPosition(index, orb, frame, scratch)
            val z = scratch[2]
            if ((z >= 0f) != front || scratch[3] <= 0.005f) return@forEachIndexed
            val perspective = 7f / (7f - z)
            val x = scene.gemX + scratch[0] * scene.gemRadius * perspective
            val y = scene.gemY - scratch[1] * scene.gemRadius * perspective
            val core = orb.coreSize * scene.unit * perspective
            val focus = if (z > 1.6f) 2 else if (z < 0f || abs(z) >= 0.6f) 1 else 0
            drawOrbSprite(canvas, x, y, core, orb.tint, focus, scratch[3] * if (z < 0f) 0.66f else 1f)
            if (scratch[4] > 1.15f) {
                val angle = atan2(scene.gemY - y, scene.gemX - x)
                drawComet(canvas, x, y, core, orb.tint, angle, scratch[4], scratch[3])
            }
            if (frame.phase == GemOrbCollectSimulation.Phase.COLLECTING && scratch[3] > 0.92f && index % 4 == 0) {
                drawGlint(canvas, x, y, core * 2.5f, scratch[3])
            }
        }
    }

    private fun drawOrbSprite(canvas: Canvas, x: Float, y: Float, core: Float, tint: Int, focus: Int, alpha: Float) {
        sourceRect.set(focus * 128, tint * 128, focus * 128 + 128, tint * 128 + 128)
        val extent = core * 2f
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        paint.xfermode = additiveBlend
        canvas.drawBitmap(orbAtlas, sourceRect, RectF(x - extent, y - extent, x + extent, y + extent), paint)
        paint.xfermode = null
        paint.alpha = 255
    }

    private fun drawComet(canvas: Canvas, x: Float, y: Float, core: Float, tint: Int, angle: Float, stretch: Float, alpha: Float) {
        sourceRect.set(384, tint * 128, 640, tint * 128 + 128)
        val scale = (0.55f + 0.17f * stretch).coerceIn(0.7f, 1.3f)
        val length = core * 8f * scale
        val halfHeight = core * 2f * scale
        paint.alpha = (alpha.coerceIn(0f, 1f) * 140f).roundToInt()
        paint.xfermode = additiveBlend
        canvas.save()
        canvas.rotate(angle * 180f / PI.toFloat(), x, y)
        canvas.drawBitmap(orbAtlas, sourceRect, RectF(x - length * 0.82f, y - halfHeight, x + length * 0.18f, y + halfHeight), paint)
        canvas.restore()
        paint.xfermode = null
        paint.alpha = 255
    }

    private fun drawGem(
        canvas: Canvas,
        frame: GemOrbCollectSimulation.Frame,
        centerX: Float,
        centerY: Float,
        radius: Float,
        miniature: Boolean,
    ) {
        val shaderFlare = if (miniature) 0f else frame.flare
        simulation.shaderLights(frame, shaderLights, lightPoint)
        if (gemShader.draw(canvas, paint, centerX, centerY, radius, frame.yaw, frame.pitch, frame.stretch, frame.charge, shaderFlare, frame.clock, shaderLights, miniature)) {
            if (!miniature) drawGemGlints(canvas, frame, centerX, centerY, radius)
            return
        }
        val horizontal = 0.88f + 0.12f * abs(cos(frame.yaw))
        buildPolygon(gemPath, centerX, centerY, radius, horizontal, frame.yaw * 0.08f + 0.22f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.065f
        paint.color = ColorUtils.setAlphaComponent(Color.rgb(73, 32, 230), if (miniature) 80 else 130)
        paint.maskFilter = BlurMaskFilter(radius * 0.18f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(gemPath, paint)
        paint.maskFilter = null
        paint.style = Paint.Style.FILL

        canvas.save()
        canvas.clipPath(gemPath)
        paint.shader = RadialGradient(
            centerX - radius * 0.42f,
            centerY - radius * 0.52f,
            radius * 1.55f,
            intArrayOf(Color.rgb(100, 92, 255), Color.rgb(42, 43, 216), Color.rgb(16, 14, 110)),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(centerX - radius, centerY - radius, centerX + radius, centerY + radius, paint)
        paint.shader = null
        drawFacets(canvas, frame, centerX, centerY, radius, horizontal)
        canvas.restore()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, radius * 0.018f)
        paint.shader = SweepGradient(
            centerX,
            centerY,
            intArrayOf(Color.rgb(69, 246, 226), Color.rgb(57, 238, 255), Color.rgb(255, 98, 235), Color.rgb(69, 246, 226)),
            null,
        )
        canvas.drawPath(gemPath, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL

        drawStar(canvas, centerX, centerY, radius * (0.43f + frame.charge * 0.04f), frame.flare, miniature)
        if (!miniature) drawGemGlints(canvas, frame, centerX, centerY, radius)
    }

    private fun drawFacets(canvas: Canvas, frame: GemOrbCollectSimulation.Frame, cx: Float, cy: Float, radius: Float, horizontal: Float) {
        val innerRadius = radius * 0.48f
        for (index in 0 until 7) {
            val a0 = -PI.toFloat() / 2f + index * PI.toFloat() * 2f / 7f + 0.22f
            val a1 = -PI.toFloat() / 2f + (index + 1) * PI.toFloat() * 2f / 7f + 0.22f
            innerPath.reset()
            innerPath.moveTo(cx + cos(a0) * innerRadius * horizontal, cy + sin(a0) * innerRadius)
            innerPath.lineTo(cx + cos(a0) * radius * horizontal, cy + sin(a0) * radius)
            innerPath.lineTo(cx + cos(a1) * radius * horizontal, cy + sin(a1) * radius)
            innerPath.lineTo(cx + cos(a1) * innerRadius * horizontal, cy + sin(a1) * innerRadius)
            innerPath.close()
            val light = 0.5f + 0.5f * cos(a0 - frame.yaw)
            val color = when (index) {
                0, 1 -> blend(Color.rgb(91, 70, 248), Color.rgb(206, 117, 255), light)
                2, 3 -> blend(Color.rgb(56, 30, 185), Color.rgb(237, 102, 240), light * 0.75f)
                else -> blend(Color.rgb(24, 24, 153), Color.rgb(86, 70, 241), light)
            }
            paint.color = color
            canvas.drawPath(innerPath, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, radius * 0.012f)
            paint.color = ColorUtils.setAlphaComponent(Color.rgb(201, 179, 255), 88)
            canvas.drawPath(innerPath, paint)
            paint.style = Paint.Style.FILL
        }
        paint.shader = LinearGradient(
            cx - radius,
            cy - radius,
            cx + radius * 0.2f,
            cy + radius * 0.4f,
            ColorUtils.setAlphaComponent(Color.WHITE, 115),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(gemPath, paint)
        paint.shader = null
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, flare: Float, miniature: Boolean) {
        if (!miniature) {
            paint.color = ColorUtils.setAlphaComponent(Color.rgb(240, 184, 255), (80 + 100f * flare).roundToInt())
            paint.maskFilter = BlurMaskFilter(radius * (0.22f + 0.25f * flare), BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(cx, cy, radius * (0.72f + flare * 0.35f), paint)
            paint.maskFilter = null
        }
        val vertical = radius * (1f + flare * 0.45f)
        val horizontal = radius * (0.78f + flare * 0.35f)
        starPath.reset()
        starPath.moveTo(cx, cy - vertical)
        starPath.cubicTo(cx + horizontal * 0.12f, cy - vertical * 0.24f, cx + horizontal * 0.32f, cy - horizontal * 0.14f, cx + horizontal, cy)
        starPath.cubicTo(cx + horizontal * 0.32f, cy + horizontal * 0.14f, cx + horizontal * 0.12f, cy + vertical * 0.24f, cx, cy + vertical)
        starPath.cubicTo(cx - horizontal * 0.12f, cy + vertical * 0.24f, cx - horizontal * 0.32f, cy + horizontal * 0.14f, cx - horizontal, cy)
        starPath.cubicTo(cx - horizontal * 0.32f, cy - horizontal * 0.14f, cx - horizontal * 0.12f, cy - vertical * 0.24f, cx, cy - vertical)
        starPath.close()
        paint.shader = LinearGradient(
            cx - horizontal,
            cy + vertical,
            cx + horizontal,
            cy - vertical,
            intArrayOf(Color.rgb(255, 103, 236), Color.WHITE, Color.rgb(255, 224, 255)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(starPath, paint)
        paint.shader = null
    }

    private fun drawGemGlints(canvas: Canvas, frame: GemOrbCollectSimulation.Frame, cx: Float, cy: Float, radius: Float) {
        val count = if (frame.flare > 0.05f) 6 else if (frame.charge > 0.4f) 4 else 3
        repeat(count) { index ->
            val angle = frame.yaw * (1.4f + index * 0.08f) + index * 2.399963f
            val distance = radius * (0.78f + 0.14f * sin(index * 1.7f))
            val strength = 0.35f + 0.5f * abs(sin(frame.clock * 0.8f + index * 2.1f))
            drawGlint(canvas, cx + cos(angle) * distance, cy + sin(angle) * distance, radius * (0.13f + frame.flare * 0.1f), strength)
        }
    }

    private fun drawGlint(canvas: Canvas, x: Float, y: Float, radius: Float, alpha: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, radius * 0.06f)
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, (alpha.coerceIn(0f, 1f) * 235f).roundToInt())
        paint.maskFilter = BlurMaskFilter(radius * 0.08f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawLine(x - radius, y, x + radius, y, paint)
        canvas.drawLine(x, y - radius, x, y + radius, paint)
        paint.maskFilter = null
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, max(1.5f, radius * 0.09f), paint)
    }

    private fun drawTally(canvas: Canvas, frame: GemOrbCollectSimulation.Frame) {
        if (frame.tallyAlpha <= 0f) return
        textPaint.typeface = boldTypeface
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 22f * scene.typeScale
        textPaint.color = ColorUtils.setAlphaComponent(Color.rgb(233, 228, 255), (frame.tallyAlpha * 255f).roundToInt())
        val y = scene.tallyY - (1f - GemOrbCollectMath.ease(frame.tallyAlpha)) * 6f * scene.scale
        canvas.drawText("+${frame.tally}", scene.width / 2f, centeredBaseline(y, textPaint), textPaint)
    }

    private fun drawCopy(canvas: Canvas, frame: GemOrbCollectSimulation.Frame) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = boldTypeface
        textPaint.textSize = 28f * scene.typeScale
        textPaint.letterSpacing = -0.014f
        textPaint.color = Color.rgb(242, 241, 255)
        drawCrossfadeText(canvas, "200 Lumens ready", "200 Lumens collected", scene.titleTop, 34f * scene.typeScale, frame.copy)

        textPaint.typeface = regularTypeface
        textPaint.textSize = 16f * scene.typeScale
        textPaint.letterSpacing = 0f
        textPaint.color = ColorUtils.setAlphaComponent(Color.rgb(214, 212, 255), 168)
        drawCrossfadeText(
            canvas,
            "Hold the crystal to draw them in.",
            "Keep collecting to unlock new crystals.",
            scene.bodyTop,
            24f * scene.typeScale,
            GemOrbCollectMath.clamp((frame.copy - 0.08f) / 0.92f),
        )
    }

    private fun drawCrossfadeText(canvas: Canvas, before: String, after: String, top: Float, line: Float, progress: Float) {
        val baseline = top + (line - (textPaint.descent() + textPaint.ascent())) / 2f
        canvas.save()
        canvas.clipRect(0f, top, scene.width, top + line)
        textPaint.alpha = ((1f - progress) * 255f).roundToInt()
        canvas.drawText(before, scene.width / 2f, baseline - progress * line, textPaint)
        textPaint.alpha = (progress * 255f).roundToInt()
        canvas.drawText(after, scene.width / 2f, baseline + (1f - progress) * line, textPaint)
        textPaint.alpha = 255
        canvas.restore()
    }

    private fun drawCapsule(canvas: Canvas, frame: GemOrbCollectSimulation.Frame) {
        drawCrystalPill(
            canvas,
            frame,
            scene.capsuleLeft,
            scene.capsuleTop,
            scene.capsuleWidth,
            scene.capsuleHeight,
            capsule = true,
            pressed = false,
            glow = frame.capsuleGlow,
            enabled = true,
        )
        drawGem(canvas, frame, scene.miniGemX, scene.miniGemY, 12f * scene.unit, miniature = true)
        textPaint.typeface = boldTypeface
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 20f * scene.unit
        textPaint.color = Color.WHITE
        textPaint.alpha = 255
        val label = String.format("%,.0f", frame.balance)
        canvas.drawText(label, scene.capsuleLeft + 44f * scene.unit, centeredBaseline(scene.miniGemY, textPaint), textPaint)
    }

    private fun drawButton(canvas: Canvas, frame: GemOrbCollectSimulation.Frame) {
        val pressedScale = if (frame.buttonPressed) 0.97f else 1f
        canvas.save()
        canvas.scale(pressedScale, pressedScale, scene.width / 2f, scene.buttonTop + scene.buttonHeight / 2f)
        drawCrystalPill(
            canvas,
            frame,
            scene.buttonLeft,
            scene.buttonTop,
            scene.buttonWidth,
            scene.buttonHeight,
            capsule = false,
            pressed = frame.buttonPressed,
            glow = if (frame.phase == GemOrbCollectSimulation.Phase.AFTER) 0.45f else 0f,
            enabled = frame.buttonEnabled,
        )
        textPaint.typeface = boldTypeface
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 17f * scene.unit
        textPaint.color = Color.WHITE
        textPaint.alpha = if (frame.buttonEnabled) 255 else 115
        drawCrossfadeButtonLabel(canvas, frame)
        canvas.restore()
        textPaint.alpha = 255
    }

    private fun drawCrystalPill(
        canvas: Canvas,
        frame: GemOrbCollectSimulation.Frame,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        capsule: Boolean,
        pressed: Boolean,
        glow: Float,
        enabled: Boolean,
    ) {
        val sheen = if (frame.phase == GemOrbCollectSimulation.Phase.AFTER) -1f + 3f * GemOrbCollectMath.clamp(frame.sequenceAge / 0.9f) else -1f
        if (materials.pill(canvas, paint, left, top, width, height, capsule, if (pressed) 1f else 0f, glow, sheen, if (enabled) 1f else 0.45f, 1f, frame.clock, scene.unit)) return
        val radius = height / 2f
        if (!capsule) {
            paint.color = ColorUtils.setAlphaComponent(Color.rgb(74, 30, 220), if (enabled) 95 else 42)
            paint.maskFilter = BlurMaskFilter(height * (0.23f + 0.12f * glow), BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(left, top + height * 0.08f, left + width, top + height * 1.08f, radius, radius, paint)
            paint.maskFilter = null
        }
        paint.shader = LinearGradient(
            left,
            top,
            left,
            top + height,
            if (capsule) ColorUtils.setAlphaComponent(Color.rgb(94, 90, 220), 72) else Color.rgb(94, 72, 238),
            if (capsule) ColorUtils.setAlphaComponent(Color.rgb(32, 26, 105), 95) else Color.rgb(32, 20, 126),
            Shader.TileMode.CLAMP,
        )
        paint.alpha = if (enabled) 255 else 130
        canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, paint)
        paint.alpha = 255
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, scene.unit * 0.75f)
        paint.shader = LinearGradient(
            left,
            top,
            left + width,
            top + height,
            intArrayOf(Color.rgb(72, 238, 218), Color.rgb(155, 104, 255), Color.rgb(238, 136, 255)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, paint)
        paint.shader = null
        paint.color = ColorUtils.setAlphaComponent(Color.WHITE, if (pressed) 30 else 74)
        canvas.drawLine(left + radius, top + height * 0.18f, left + width - radius, top + height * 0.18f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawCrossfadeButtonLabel(canvas: Canvas, frame: GemOrbCollectSimulation.Frame) {
        val progress = frame.buttonCopy
        val centerY = scene.buttonTop + scene.buttonHeight / 2f
        val baseline = centeredBaseline(centerY, textPaint)
        canvas.save()
        canvas.clipRect(scene.buttonLeft, scene.buttonTop, scene.buttonLeft + scene.buttonWidth, scene.buttonTop + scene.buttonHeight)
        textPaint.alpha = ((1f - progress) * if (frame.buttonEnabled) 255f else 115f).roundToInt()
        canvas.drawText("Collect", scene.width / 2f, baseline - progress * 24f * scene.unit, textPaint)
        textPaint.alpha = (progress * 255f).roundToInt()
        canvas.drawText("Replay", scene.width / 2f, baseline + (1f - progress) * 24f * scene.unit, textPaint)
        canvas.restore()
    }

    private fun buildPolygon(path: Path, cx: Float, cy: Float, radius: Float, horizontal: Float, rotation: Float) {
        path.reset()
        repeat(7) { index ->
            val angle = -PI.toFloat() / 2f + index * PI.toFloat() * 2f / 7f + rotation
            val x = cx + cos(angle) * radius * horizontal
            val y = cy + sin(angle) * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun centeredBaseline(centerY: Float, target: Paint): Float =
        centerY - (target.ascent() + target.descent()) / 2f

    private fun blend(from: Int, to: Int, amount: Float): Int = ColorUtils.blendARGB(from, to, amount.coerceIn(0f, 1f))

    private fun updateFeedback(frame: GemOrbCollectSimulation.Frame) {
        if (frame.tally > lastHits && frame.tally > 0) {
            performHapticFeedback(if (frame.tally > 80) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (frame.phase != lastPhase) {
            when (frame.phase) {
                GemOrbCollectSimulation.Phase.BURST -> performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                GemOrbCollectSimulation.Phase.AFTER -> performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.VIRTUAL_KEY,
                )
                GemOrbCollectSimulation.Phase.REWIND -> performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                else -> Unit
            }
        }
        lastHits = frame.tally
        lastPhase = frame.phase
    }

    private enum class TouchTarget { NONE, GEM, BUTTON }

    private inner class ControlsAccessibility : AccessibilityNodeProvider() {
        private var hovered = -1
        private var focused = -1

        private fun targetAt(x: Float, y: Float): Int {
            if (!::scene.isInitialized) return -1
            if (x in scene.buttonLeft..(scene.buttonLeft + scene.buttonWidth) && y in scene.buttonTop..(scene.buttonTop + scene.buttonHeight)) return 2
            if (hypot(x - scene.gemX, y - scene.gemY) <= scene.gemRadius * 1.25f) return 1
            return -1
        }

        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            if (virtualViewId == View.NO_ID) {
                val host = super@GemOrbCollectView.createAccessibilityNodeInfo()
                if (::scene.isInitialized) {
                    host.addChild(this@GemOrbCollectView, 1)
                    host.addChild(this@GemOrbCollectView, 2)
                }
                return host
            }
            if (!::scene.isInitialized || virtualViewId !in 1..2) return null
            val node = AccessibilityNodeInfo.obtain(this@GemOrbCollectView, virtualViewId)
            node.packageName = context.packageName
            node.className = "android.widget.Button"
            node.isClickable = true
            node.isEnabled = virtualViewId == 1 || simulation.frame().buttonEnabled
            node.isFocusable = true
            node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            if (focused == virtualViewId) node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
            else node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
            val bounds = if (virtualViewId == 1) {
                val radius = scene.gemRadius * 1.25f
                node.contentDescription = "Crystal. Double tap to collect Lumens. Touch and hold to collect or drag to turn."
                AndroidRect((scene.gemX - radius).toInt(), (scene.gemY - radius).toInt(), (scene.gemX + radius).toInt(), (scene.gemY + radius).toInt())
            } else {
                node.contentDescription = if (simulation.frame().phase == GemOrbCollectSimulation.Phase.AFTER) "Replay. Balance 1,600 Lumens." else "Collect. Balance 1,400 Lumens."
                AndroidRect(scene.buttonLeft.toInt(), scene.buttonTop.toInt(), (scene.buttonLeft + scene.buttonWidth).toInt(), (scene.buttonTop + scene.buttonHeight).toInt())
            }
            node.setBoundsInParent(bounds)
            val position = IntArray(2)
            getLocationOnScreen(position)
            bounds.offset(position[0], position[1])
            node.setBoundsInScreen(bounds)
            node.setParent(this@GemOrbCollectView)
            return node
        }

        override fun performAction(virtualViewId: Int, action: Int, arguments: android.os.Bundle?): Boolean {
            if (virtualViewId !in 1..2) return false
            when (action) {
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                    focused = virtualViewId
                    sendEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                    focused = -1
                    sendEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                }
                AccessibilityNodeInfo.ACTION_CLICK -> {
                    if (virtualViewId == 1) simulation.accessibilityCollect() else simulation.accessibilityButton()
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    sendEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                    invalidate()
                }
                else -> return false
            }
            return true
        }

        fun hover(event: MotionEvent): Boolean {
            val target = if (event.actionMasked == MotionEvent.ACTION_HOVER_EXIT) -1 else targetAt(event.x, event.y)
            if (target == hovered) return target != -1
            if (hovered != -1) sendEvent(hovered, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
            hovered = target
            if (target != -1) sendEvent(target, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
            return target != -1
        }

        private fun sendEvent(id: Int, type: Int) {
            val item = AccessibilityEvent.obtain(type)
            item.setSource(this@GemOrbCollectView, id)
            item.packageName = context.packageName
            item.className = "android.widget.Button"
            parent?.requestSendAccessibilityEvent(this@GemOrbCollectView, item)
        }
    }
}
