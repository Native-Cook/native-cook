package com.ngocdevv.nativecook.blocks.gestures.chromeprizewheel

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.PaintCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.ngocdevv.nativecook.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal class ChromePrizeWheelView(
    context: Context,
    autoPlay: Boolean,
) : View(context), Choreographer.FrameCallback {
    private val config = ChromePrizeWheelConfig(
        reduced = !context.motionEnabledForChromePrizeWheel(),
        autoPlay = autoPlay,
    )
    private val engine = ChromePrizeWheelEngine(config)
    private val assets = ChromePrizeWheelAssets(context)
    private val wheelRenderer = ChromePrizeWheelSurface(assets)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val source = Rect()
    private val destination = RectF()
    private val matrix = Matrix()
    private var lastFrameNanos = 0L
    private var clockSeconds = 0.0
    private var lastPhase = ChromePrizeWheelPhase.Idle
    private var lastWinner = -1
    private var velocity: VelocityTracker? = null
    private var interaction = ChromePrizeWheelInteraction.None
    private var downX = 0f
    private var downY = 0f
    private var scheduled = false
    private lateinit var sceneLayout: ChromePrizeWheelLayout

    init {
        setBackgroundColor(Color.rgb(3, 3, 14))
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Prize wheel. Swipe to spin. Double tap to spin."
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!scheduled) {
            scheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        scheduled = false
        lastFrameNanos = 0L
        velocity?.recycle()
        velocity = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        sceneLayout = ChromePrizeWheelLayout(width.toFloat(), height.toFloat())
        config.cx = sceneLayout.cx.toDouble()
        config.cy = sceneLayout.cy.toDouble()
        config.radius = sceneLayout.radius.toDouble()
        config.buttonX = sceneLayout.buttonX.toDouble()
        config.buttonY = sceneLayout.buttonY.toDouble()
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        val frameClockNanos = SystemClock.elapsedRealtimeNanos()
        if (!isAttachedToWindow || !isShown || windowVisibility != VISIBLE) {
            lastFrameNanos = 0L
            return
        }
        if (lastFrameNanos != 0L && ::sceneLayout.isInitialized) {
            val elapsed = (frameClockNanos - lastFrameNanos) / 1_000_000_000.0
            config.reduced = !context.motionEnabledForChromePrizeWheel()
            engine.advance(elapsed)
            if (!config.reduced) clockSeconds += elapsed
            processEvents()
            publishAccessibilityState()
            invalidate()
        }
        lastFrameNanos = frameClockNanos
        scheduled = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun processEvents() {
        val events = engine.events
        when {
            events and ChromePrizeWheelEvent.Success != 0 -> performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            events and ChromePrizeWheelEvent.Medium != 0 -> performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            events and ChromePrizeWheelEvent.Rigid != 0 -> performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            events and ChromePrizeWheelEvent.Light != 0 -> performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
        }
    }

    private fun publishAccessibilityState() {
        val phase = value(ChromePrizeWheelValue.Phase).toInt()
        val winner = value(ChromePrizeWheelValue.Winner).toInt()
        if (phase == lastPhase && winner == lastWinner) return
        if (phase == ChromePrizeWheelPhase.Reveal && winner in ChromePrizeWheelPrizes.indices) {
            val prize = ChromePrizeWheelPrizes[winner]
            announceForAccessibility("You won ${prize.count}. ${prize.label}.")
            contentDescription = "Collect. You won ${prize.count}, ${prize.label}."
        } else if (phase == ChromePrizeWheelPhase.Collecting) {
            contentDescription = "Collecting prize"
        } else {
            contentDescription = "Prize wheel. Swipe to spin. Double tap to spin."
        }
        lastPhase = phase
        lastWinner = winner
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
        info.isClickable = true
        val phase = value(ChromePrizeWheelValue.Phase).toInt()
        AccessibilityNodeInfoCompat.wrap(info).stateDescription = when (phase) {
            ChromePrizeWheelPhase.Reveal -> "Prize ready to collect"
            ChromePrizeWheelPhase.Spinning, ChromePrizeWheelPhase.Landed -> "Spinning"
            else -> "Ready"
        }
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (value(ChromePrizeWheelValue.Phase).toInt() == ChromePrizeWheelPhase.Reveal &&
            value(ChromePrizeWheelValue.Button) > 0.9) {
            engine.collect()
        } else {
            engine.hub(Math.random())
        }
        processEvents()
        invalidate()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!::sceneLayout.isInitialized) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                interaction = when {
                    hitButton(event.x, event.y) -> {
                        engine.buttonDown()
                        ChromePrizeWheelInteraction.Button
                    }
                    hitHero(event.x, event.y) -> {
                        engine.heroDown()
                        ChromePrizeWheelInteraction.Hero
                    }
                    engine.down(event.x.toDouble(), event.y.toDouble()) -> ChromePrizeWheelInteraction.Wheel
                    else -> ChromePrizeWheelInteraction.None
                }
                processEvents()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                when (interaction) {
                    ChromePrizeWheelInteraction.Wheel -> engine.move(event.x.toDouble(), event.y.toDouble())
                    ChromePrizeWheelInteraction.Hero -> {
                        velocity?.computeCurrentVelocity(1000)
                        engine.heroMove(
                            (event.x - downX).toDouble(),
                            (event.y - downY).toDouble(),
                            (velocity?.xVelocity ?: 0f).toDouble(),
                        )
                    }
                    else -> Unit
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocity?.addMovement(event)
                velocity?.computeCurrentVelocity(1000)
                val cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL
                when (interaction) {
                    ChromePrizeWheelInteraction.Wheel -> engine.release(
                        event.x.toDouble(),
                        event.y.toDouble(),
                        (velocity?.xVelocity ?: 0f).toDouble(),
                        (velocity?.yVelocity ?: 0f).toDouble(),
                        cancelled,
                        Math.random(),
                    )
                    ChromePrizeWheelInteraction.Hero -> engine.heroRelease()
                    ChromePrizeWheelInteraction.Button -> {
                        engine.buttonUp()
                        if (!cancelled && hitButton(event.x, event.y)) engine.collect()
                    }
                    ChromePrizeWheelInteraction.None -> Unit
                }
                velocity?.recycle()
                velocity = null
                interaction = ChromePrizeWheelInteraction.None
                parent?.requestDisallowInterceptTouchEvent(false)
                processEvents()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitButton(x: Float, y: Float): Boolean {
        if (value(ChromePrizeWheelValue.Phase).toInt() != ChromePrizeWheelPhase.Reveal || value(ChromePrizeWheelValue.Button) < 0.9) return false
        return x >= sceneLayout.buttonX - sceneLayout.buttonWidth / 2f && x <= sceneLayout.buttonX + sceneLayout.buttonWidth / 2f &&
            y >= sceneLayout.buttonY - sceneLayout.buttonHeight / 2f && y <= sceneLayout.buttonY + sceneLayout.buttonHeight / 2f
    }

    private fun hitHero(x: Float, y: Float): Boolean {
        if (value(ChromePrizeWheelValue.Phase).toInt() != ChromePrizeWheelPhase.Reveal || value(ChromePrizeWheelValue.Hero) < 0.9) return false
        val centreX = sceneLayout.heroX + value(ChromePrizeWheelValue.HeroX).toFloat()
        val centreY = sceneLayout.heroY + value(ChromePrizeWheelValue.HeroY).toFloat()
        return abs(x - centreX) <= sceneLayout.heroSize / 2f && abs(y - centreY) <= sceneLayout.heroSize / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::sceneLayout.isInitialized) return
        drawBackdrop(canvas)
        drawSky(canvas)
        canvas.save()
        canvas.scale(
            value(ChromePrizeWheelValue.WheelScale).toFloat(),
            value(ChromePrizeWheelValue.WheelScale).toFloat(),
            sceneLayout.cx,
            sceneLayout.cy,
        )
        drawWheel(canvas)
        drawPointer(canvas)
        canvas.restore()
        paint.alpha = 255
        drawRays(canvas)
        drawParticles(canvas, near = false)
        drawHero(canvas)
        drawParticles(canvas, near = true)
        drawCopy(canvas)
        drawTouch(canvas)
    }

    private fun value(key: ChromePrizeWheelValue): Double = engine.values[key]

    private fun drawBackdrop(canvas: Canvas) {
        destination.set(sceneLayout.backdropLeft, sceneLayout.backdropTop, sceneLayout.backdropRight, sceneLayout.backdropBottom)
        paint.alpha = 255
        paint.shader = null
        canvas.drawBitmap(assets.backdrop, null, destination, paint)
    }

    private fun drawSky(canvas: Canvas) {
        val swirl = value(ChromePrizeWheelValue.Swirl)
        val burst = value(ChromePrizeWheelValue.Burst)
        val tiltX = value(ChromePrizeWheelValue.TiltX)
        val tiltY = value(ChromePrizeWheelValue.TiltY)
        val reduced = config.reduced
        for (index in 0 until ChromePrizeWheelSkyStars.size + ChromePrizeWheelSkyDots.size) {
            val star = index < ChromePrizeWheelSkyStars.size
            val item = if (star) ChromePrizeWheelSkyStars[index] else ChromePrizeWheelSkyDots[index - ChromePrizeWheelSkyStars.size]
            val dx = item[0] * sceneLayout.k
            val dy = item[1] * sceneLayout.k
            val radius = sqrt(dx * dx + dy * dy)
            val falloff = min(1f, sceneLayout.radius * sceneLayout.radius / (radius * radius))
            val turn = if (reduced) 0f else (swirl * 0.35).coerceIn(-0.35, 0.35).toFloat() * falloff
            val kick = if (reduced || burst < 0.0) 0f else
                14f * sceneLayout.k * exp((-3.5 * burst)).toFloat() * sin(burst * 13.0).toFloat() * falloff
            var x = sceneLayout.cx + (dx * cos(turn) - dy * sin(turn)) * (1f + kick / radius)
            var y = sceneLayout.cy + (dx * sin(turn) + dy * cos(turn)) * (1f + kick / radius)
            if (!reduced) {
                x += tiltX.toFloat() * 28f * falloff
                y += sin(clockSeconds * (0.45 + index * 0.018) + index).toFloat() * 2f * sceneLayout.k + tiltY.toFloat() * 28f * falloff
            }
            val size = item[2] * sceneLayout.k
            val titleBottom = sceneLayout.subtitleTop + sceneLayout.bodyLine + 12f * sceneLayout.k
            val clearTop = clamp01((y - size / 2f - titleBottom) / (12f * sceneLayout.k))
            val clearBottom = clamp01((sceneLayout.buttonTop - 16f - y - size / 2f) / (12f * sceneLayout.k))
            val inCaption = y + size / 2f > sceneLayout.captionTop - 8f && y - size / 2f < sceneLayout.labelTop + sceneLayout.bodyLine + 8f
            val clearCaption = if (inCaption && abs(x - sceneLayout.cx) < 116f * sceneLayout.fontScale + size / 2f) 0f else 1f
            val alpha = clearTop * clearBottom * clearCaption * if (star) 0.72f else
                0.3f + 0.18f * sin(clockSeconds * 1.3 + index).toFloat()
            val tile = if (star) item.getOrElse(4) { 0f }.toInt() else if (index % 4 == 0) 2 else 3
            val angle = (if (star) item[3] else index.toFloat()) + if (reduced) 0f else 0.06f * sin(clockSeconds * 0.4 + index).toFloat()
            drawAtlasTile(canvas, tile, x, y, size, angle, alpha)
        }
    }

    private fun drawWheel(canvas: Canvas) {
        paint.shader = null
        paint.maskFilter = null
        val wheelOpacity = value(ChromePrizeWheelValue.WheelOpacity).coerceIn(0.0, 1.0)
        val bloomOpacity = 0.4 * (1.0 - 0.7 * value(ChromePrizeWheelValue.Dim)) * wheelOpacity
        paint.alpha = (255 * bloomOpacity.coerceIn(0.0, 1.0)).roundToInt()
        val bloomRadius = sceneLayout.radius * 1.35f
        paint.shader = RadialGradient(
            sceneLayout.cx,
            sceneLayout.cy,
            bloomRadius,
            intArrayOf(Color.rgb(36, 56, 224), Color.rgb(36, 56, 224), Color.TRANSPARENT),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(sceneLayout.cx, sceneLayout.cy, bloomRadius, paint)
        paint.alpha = (255 * wheelOpacity).roundToInt()
        paint.shader = RadialGradient(
            sceneLayout.cx + sceneLayout.radius * 0.045f,
            sceneLayout.cy + sceneLayout.radius * 0.075f,
            sceneLayout.radius,
            intArrayOf(Color.argb(230, 0, 0, 6), Color.argb(180, 0, 0, 6), Color.TRANSPARENT),
            floatArrayOf(0.72f, 0.94f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(sceneLayout.cx + sceneLayout.radius * 0.045f, sceneLayout.cy + sceneLayout.radius * 0.075f, sceneLayout.radius * 1.05f, paint)
        paint.shader = null
        wheelRenderer.draw(
            canvas = canvas,
            paint = paint,
            sceneLayout = sceneLayout,
            values = engine.values,
        )
    }

    private fun drawPointer(canvas: Canvas) {
        val opacity = 1f - 0.6f * value(ChromePrizeWheelValue.Dim).toFloat()
        val angle = Math.toDegrees(value(ChromePrizeWheelValue.Swing)).toFloat()
        canvas.save()
        canvas.translate(sceneLayout.pointerX + 3f * sceneLayout.k, sceneLayout.pointerY + 4f * sceneLayout.k)
        canvas.rotate(angle)
        destination.set(-20f * sceneLayout.k, -36f * sceneLayout.k, 68f * sceneLayout.k, 36f * sceneLayout.k)
        paint.alpha = (255 * opacity).roundToInt()
        canvas.drawBitmap(assets.pointerShadow, null, destination, paint)
        canvas.restore()
        canvas.save()
        canvas.translate(sceneLayout.pointerX, sceneLayout.pointerY)
        canvas.rotate(angle)
        paint.alpha = (255 * opacity).roundToInt()
        canvas.drawBitmap(assets.pointer, null, destination, paint)
        val light = 0.025f + if (config.reduced) 0f else 0.014f * sin(clockSeconds * 1.5).toFloat() +
            value(ChromePrizeWheelValue.Tick).toFloat() * 0.65f
        paint.alpha = (255 * light.coerceIn(0f, 1f)).roundToInt()
        paint.shader = RadialGradient(
            20f * sceneLayout.k,
            0f,
            25f * sceneLayout.k,
            intArrayOf(Color.rgb(255, 213, 252), Color.rgb(248, 52, 218), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(20f * sceneLayout.k, 0f, 25f * sceneLayout.k, paint)
        val tick = value(ChromePrizeWheelValue.Tick).toFloat()
        if (tick > 0.01f) {
            paint.shader = null
            paint.color = Color.rgb(255, 242, 255)
            paint.alpha = (255 * tick.coerceIn(0f, 0.9f)).roundToInt()
            canvas.save()
            canvas.translate(40f * sceneLayout.k, 0f)
            canvas.rotate((value(ChromePrizeWheelValue.Strikes) * 137.50776).toFloat())
            stroke.color = paint.color
            stroke.alpha = paint.alpha
            stroke.strokeWidth = 2f * sceneLayout.k
            canvas.drawLine(-12f * sceneLayout.k, 0f, 12f * sceneLayout.k, 0f, stroke)
            canvas.drawLine(0f, -12f * sceneLayout.k, 0f, 12f * sceneLayout.k, stroke)
            canvas.restore()
        }
        canvas.restore()
        paint.alpha = 255
        paint.shader = null
    }

    private fun drawRays(canvas: Canvas) {
        val presence = value(ChromePrizeWheelValue.Rays).toFloat()
        if (presence <= 0.001f) return
        canvas.save()
        canvas.translate(sceneLayout.heroX, sceneLayout.heroY)
        val radius = sceneLayout.radius * (0.6f + 0.4f * presence)
        PaintCompat.setBlendMode(paint, BlendModeCompat.PLUS)
        for (index in 0 until 24) {
            val angle = index * 2.399963f + sin(index * 17.3f) * 0.19f + if (config.reduced) 0f else (clockSeconds * 0.2).toFloat()
            val half = (0.012f + 0.018f * (0.5f + 0.5f * sin(index * 7.7f)))
            path.reset()
            path.moveTo(0f, 0f)
            path.lineTo(cos(angle - half) * radius * 1.7f, sin(angle - half) * radius * 1.7f)
            path.lineTo(cos(angle + half) * radius * 1.7f, sin(angle + half) * radius * 1.7f)
            path.close()
            paint.shader = RadialGradient(
                0f,
                0f,
                radius * 1.7f,
                Color.argb((7f * presence).roundToInt(), 230, 219, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(path, paint)
        }
        PaintCompat.setBlendMode(paint, null)
        paint.shader = null
        canvas.restore()
    }

    private fun drawParticles(canvas: Canvas, near: Boolean) {
        val age = value(ChromePrizeWheelValue.Burst)
        val power = value(ChromePrizeWheelValue.BurstPower)
        if (config.reduced || age < 0.0) return
        for (index in 0 until 88) {
            val particle = particlePose(index, age, power)
            if (particle.near != near || particle.opacity <= 0f) continue
            drawAtlasTile(canvas, particle.tile, particle.x, particle.y, particle.size, particle.angle, particle.opacity)
        }
    }

    private fun particlePose(index: Int, age: Double, power: Double): ChromePrizeWheelParticle {
        val wave = index >= 64
        val n = index % 64
        val seed = sin(index * 127.1 + 19.7) * 43758.5453
        val random = seed - floor(seed)
        val near = n % 3 != 0
        val time = age - if (wave) 0.22 else 0.0 - random * 0.025
        val life = 1.2 + random
        val active = age >= 0.0 && time > 0.0 && time < life && n < floor(64.0 * power) && (!wave || power >= 0.99 && n < 24)
        val angle = n * 2.399963 + if (wave) 0.7 else 0.0
        val speed = (210.0 + random * 350.0) * (if (wave) 0.7 else 1.0) * power
        val travel = speed * (1.0 - exp(-2.1 * max(0.0, time))) / 2.1
        val depth = if (near) 1.0 + max(0.0, time) * (0.6 + random * 2.2) else 0.7
        val progress = max(0.0, time) / life
        val x = sceneLayout.heroX + (cos(angle) * travel * sceneLayout.k * depth).toFloat()
        val y = sceneLayout.heroY + (((sin(angle) - 0.25) * travel * depth + 90.0 * time * time) * sceneLayout.k).toFloat()
        val size = ((8.0 + random * 17.0) * sceneLayout.k * if (near) depth * depth else 0.65).toFloat()
        val half = size * 0.4f
        val topClear = clamp01((y - half - sceneLayout.subtitleTop - sceneLayout.bodyLine - 12f) / (24f * sceneLayout.k))
        val bottomClear = clamp01((sceneLayout.buttonTop - 12f - y - half) / (24f * sceneLayout.k))
        val captionDistance = max(
            sceneLayout.captionTop - 8f - y - half,
            max(y - half - sceneLayout.labelTop - sceneLayout.bodyLine - 8f, abs(x - sceneLayout.cx) - 116f * sceneLayout.fontScale - half),
        )
        val clear = topClear * bottomClear * clamp01(captionDistance / (16f * sceneLayout.k))
        val opacity = if (active) {
            (clear * (if (near) 0.92f else 0.43f) * clamp01((time / 0.045).toFloat()) *
                (1.0 - clamp01(progress.toFloat())).pow(1.2)).toFloat()
        } else {
            0f
        }
        return ChromePrizeWheelParticle(
            near = near,
            x = x,
            y = y,
            size = size,
            angle = (angle + time * (random - 0.5) * 8.0).toFloat(),
            tile = if (n % 7 < 4) n % 2 else if (n % 7 == 4) 2 else 3,
            opacity = opacity,
        )
    }

    private fun drawHero(canvas: Canvas) {
        val winner = value(ChromePrizeWheelValue.Winner).toInt()
        if (winner !in assets.prizes.indices) return
        val printedPrize = if (value(ChromePrizeWheelValue.Phase).toInt() == ChromePrizeWheelPhase.Reveal &&
            value(ChromePrizeWheelValue.HeroSquash) < -0.00001) 0.0 else value(ChromePrizeWheelValue.WinnerPrize)
        val pose = prizePose(winner)
        val presence = ((1.0 - printedPrize) * pose.opacity).toFloat().coerceIn(0f, 1f)
        if (presence <= 0.001f) return
        val bitmap = assets.prizes[winner]
        canvas.save()
        canvas.translate(pose.x + 6f * sceneLayout.k, pose.y + (8f + 12f * value(ChromePrizeWheelValue.Hero).toFloat()) * sceneLayout.k)
        canvas.rotate(Math.toDegrees(pose.angle.toDouble()).toFloat())
        canvas.scale(pose.side * pose.sx * (1f + 0.04f * value(ChromePrizeWheelValue.Hero).toFloat()), pose.side * pose.sy * (1f + 0.04f * value(ChromePrizeWheelValue.Hero).toFloat()))
        paint.colorFilter = android.graphics.PorterDuffColorFilter(Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN)
        paint.alpha = (255 * presence * 0.30f).roundToInt()
        paint.maskFilter = BlurMaskFilter((2f + 9f * value(ChromePrizeWheelValue.Hero).toFloat()) * sceneLayout.k / max(1f, pose.side), BlurMaskFilter.Blur.NORMAL)
        destination.set(-0.5f, -0.5f, 0.5f, 0.5f)
        canvas.drawBitmap(bitmap, null, destination, paint)
        paint.maskFilter = null
        paint.colorFilter = null
        canvas.restore()
        canvas.save()
        canvas.translate(pose.x, pose.y + pose.baseShift)
        canvas.rotate(Math.toDegrees(pose.angle.toDouble()).toFloat())
        canvas.scale(pose.side * pose.sx, pose.side * pose.sy)
        paint.alpha = (255 * presence).roundToInt()
        canvas.drawBitmap(bitmap, null, destination, paint)
        canvas.restore()
        drawBadge(canvas, winner, pose, printedPrize)
        paint.alpha = 255
    }

    private fun drawBadge(canvas: Canvas, winner: Int, pose: ChromePrizeWheelPrizePose, printedPrize: Double) {
        val progress = clamp01(value(ChromePrizeWheelValue.Badge).toFloat())
        val dx = (0.74f - 0.5f) * pose.tile
        val dy = (0.76f - 0.5f) * pose.tile
        val startX = pose.startX + (dx * cos(pose.sway) - dy * sin(pose.sway)) * pose.foreshortenX
        val startY = pose.startY + (dx * sin(pose.sway) + dy * cos(pose.sway)) * pose.foreshortenY
        val open = clamp01((progress - 0.67f) / 0.33f)
        val x = mix(startX, sceneLayout.cx, progress)
        val y = mix(startY, sceneLayout.captionY, progress) - 24f * sceneLayout.k * sin(progress * PI).toFloat()
        val size = mix(pose.tile * 0.27f / 0.92f, 76f * sceneLayout.fontScale, progress) * (1f + open * 0.38f)
        val opacity = ((1.0 - printedPrize) * (1f - open) * (1.0 - value(ChromePrizeWheelValue.Collect))).toFloat().coerceIn(0f, 1f)
        if (opacity <= 0.001f) return
        source.set(winner * 128, 0, winner * 128 + 128, 128)
        destination.set(-size / 2f, -size / 2f, size / 2f, size / 2f)
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(Math.toDegrees((pose.sway * (1f - progress)).toDouble()).toFloat())
        canvas.scale(mix(pose.foreshortenX, 1f, progress), mix(pose.foreshortenY, 1f, progress))
        paint.alpha = (255 * opacity).roundToInt()
        canvas.drawBitmap(assets.badges, source, destination, paint)
        canvas.restore()
    }

    private fun prizePose(winner: Int): ChromePrizeWheelPrizePose {
        val heroRaw = value(ChromePrizeWheelValue.Hero).toFloat()
        val hero = if (config.reduced) 1f else heroRaw
        val clampedHero = clamp01(hero)
        val theta = value(ChromePrizeWheelValue.Theta).toFloat()
        val lift = value(ChromePrizeWheelValue.Lift).toFloat()
        val wheelScale = value(ChromePrizeWheelValue.WheelScale).toFloat()
        val swayWeights = ChromePrizeWheelSway[winner]
        val sway = value(ChromePrizeWheelValue.SwayA).toFloat() * swayWeights[0] +
            value(ChromePrizeWheelValue.SwayB).toFloat() * swayWeights[1]
        val angle = winner * (PI / 3.0).toFloat() + theta
        val tile = 0.46f * sceneLayout.radius * (1f + 0.07f * lift) * wheelScale
        val orbit = 0.575f * sceneLayout.radius * (1f + 0.07f * lift) * wheelScale
        val foreshortenX = cos(value(ChromePrizeWheelValue.TiltX) + 0.008 * sin(value(ChromePrizeWheelValue.Theta))).toFloat()
        val foreshortenY = cos(value(ChromePrizeWheelValue.TiltY) + 0.008 * cos(value(ChromePrizeWheelValue.Theta))).toFloat()
        val startX = sceneLayout.cx + (cos(angle) * orbit - sin(sway) * tile / 2f) * foreshortenX
        val startY = sceneLayout.cy + (sin(angle) * orbit + (cos(sway) - 1f) * tile / 2f) * foreshortenY
        val floating = if (config.reduced) 0f else sin(clockSeconds * PI * 2.0 / 2.4).toFloat()
        val x = mix(startX, sceneLayout.heroX + value(ChromePrizeWheelValue.HeroX).toFloat(), hero)
        val y = mix(startY, sceneLayout.heroY + value(ChromePrizeWheelValue.HeroY).toFloat() + floating * 4f * sceneLayout.k, hero) -
            42f * sceneLayout.k * sin(PI * clampedHero).toFloat()
        val side = tile * (sceneLayout.heroSize / tile).pow(hero)
        val squash = if (config.reduced) 0f else value(ChromePrizeWheelValue.HeroSquash).toFloat()
        val collect = if (config.reduced) 0f else value(ChromePrizeWheelValue.Collect).toFloat()
        val heroTilt = value(ChromePrizeWheelValue.HeroTilt).toFloat()
        val rotation = mix(sway, heroTilt + floating * (PI / 120.0).toFloat(), clampedHero) +
            (if (config.reduced) 0f else 0.11f * sin(clampedHero * PI).toFloat()) - collect * 0.35f
        return ChromePrizeWheelPrizePose(
            x = mix(x, sceneLayout.buttonX, collect) + sin(collect * PI).toFloat() * 42f * sceneLayout.k,
            y = mix(y, sceneLayout.buttonY, collect) - sin(collect * PI).toFloat() * 38f * sceneLayout.k,
            side = side * 0.18f.pow(collect),
            angle = rotation,
            sx = (1f - squash * 0.18f) * mix(foreshortenX, 1f, clampedHero),
            sy = (1f + squash * 0.22f) * mix(foreshortenY, 1f, clampedHero),
            baseShift = -side * squash * 0.11f,
            opacity = if (config.reduced) clamp01(heroRaw) * (1f - value(ChromePrizeWheelValue.Collect).toFloat()) else
                1f - clamp01((collect - 0.75f) * 4f),
            startX = startX,
            startY = startY,
            tile = tile,
            sway = sway,
            foreshortenX = foreshortenX,
            foreshortenY = foreshortenY,
        )
    }

    private fun drawCopy(canvas: Canvas) {
        paint.shader = null
        paint.maskFilter = null
        paint.typeface = assets.blackTypeface
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.rgb(228, 230, 255)
        paint.alpha = 255
        paint.textSize = sceneLayout.titleSize
        canvas.drawText("Daily Spin", sceneLayout.cx, baseline(sceneLayout.titleTop, sceneLayout.titleLine, paint), paint)

        val copySwap = value(ChromePrizeWheelValue.CopySwap).toFloat()
        paint.typeface = assets.regularTypeface
        paint.textSize = sceneLayout.bodySize
        paint.color = Color.rgb(205, 209, 255)
        paint.alpha = (255f * 0.72f * (1f - copySwap)).roundToInt()
        val idleY = sceneLayout.subtitleTop - 8f * copySwap
        canvas.drawText("Swipe the wheel. Every slice wins.", sceneLayout.cx, baseline(idleY, sceneLayout.bodyLine, paint), paint)
        val winner = value(ChromePrizeWheelValue.Winner).toInt().coerceIn(0, ChromePrizeWheelPrizes.lastIndex)
        paint.alpha = (255f * 0.72f * copySwap).roundToInt()
        val wonY = sceneLayout.subtitleTop + 8f * (1f - copySwap)
        val wonCopy = if (winner == 1) "Jackpot. The whole chest is yours." else "Nice spin. This one is yours."
        canvas.drawText(wonCopy, sceneLayout.cx, baseline(wonY, sceneLayout.bodyLine, paint), paint)

        val caption = value(ChromePrizeWheelValue.Caption).toFloat()
        if (caption > 0.001f) {
            val numeral = "×${max(0, value(ChromePrizeWheelValue.Count).roundToInt())}"
            paint.typeface = assets.blackTypeface
            paint.textSize = sceneLayout.numeralSize
            paint.alpha = (255 * caption).roundToInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.65f * sceneLayout.fontScale
            paint.color = Color.rgb(244, 245, 255)
            val numeralBaseline = baseline(sceneLayout.captionTop, sceneLayout.numeralLine, paint)
            canvas.drawText(numeral, sceneLayout.cx, numeralBaseline, paint)
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                0f,
                sceneLayout.captionTop + 8f * sceneLayout.fontScale,
                0f,
                sceneLayout.captionTop + 56f * sceneLayout.fontScale,
                intArrayOf(
                    Color.rgb(244, 245, 255), Color.rgb(174, 181, 236), Color.rgb(244, 245, 255),
                    Color.rgb(58, 65, 144), Color.rgb(174, 181, 236), Color.rgb(244, 245, 255),
                ),
                floatArrayOf(0f, 0.42f, 0.5f, 0.54f, 0.78f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawText(numeral, sceneLayout.cx, numeralBaseline, paint)
            paint.shader = null
            paint.typeface = assets.regularTypeface
            paint.textSize = sceneLayout.bodySize
            paint.color = Color.rgb(205, 209, 255)
            canvas.drawText(
                ChromePrizeWheelPrizes[winner].label,
                sceneLayout.cx,
                baseline(sceneLayout.labelTop + 8f * (1f - caption), sceneLayout.bodyLine, paint),
                paint,
            )
        }
        drawButton(canvas)
        paint.alpha = 255
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawButton(canvas: Canvas) {
        val progress = clamp01(value(ChromePrizeWheelValue.Button).toFloat())
        val press = value(ChromePrizeWheelValue.ButtonPress).toFloat()
        val nudge = value(ChromePrizeWheelValue.HintNudge).toFloat()
        val width = mix(sceneLayout.hintWidth, sceneLayout.buttonWidth, progress)
        val height = mix(sceneLayout.hintHeight, sceneLayout.buttonHeight, progress)
        val scale = if (press >= 0f) 1f - press * 0.03f else 1f - press * 0.04f
        val left = sceneLayout.cx - width / 2f + 6f * nudge
        val top = sceneLayout.buttonY - height / 2f
        destination.set(left, top, left + width, top + height)
        canvas.save()
        canvas.scale(scale, scale, destination.centerX(), destination.centerY())
        if (progress > 0.001f) {
            paint.shader = RadialGradient(
                destination.centerX(), destination.centerY() + 6f * sceneLayout.k, width * 0.65f,
                Color.argb((107f * progress).roundToInt(), 111, 125, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )
            destination.inset(-18f * sceneLayout.k * progress, -18f * sceneLayout.k * progress)
            canvas.drawRoundRect(destination, height / 2f + 18f * sceneLayout.k, height / 2f + 18f * sceneLayout.k, paint)
            destination.inset(18f * sceneLayout.k * progress, 18f * sceneLayout.k * progress)
        }
        if (progress < 0.999f) {
            paint.shader = null
            paint.color = Color.argb((184f * (1f - progress)).roundToInt(), 18, 22, 84)
            canvas.drawRoundRect(destination, height / 2f, height / 2f, paint)
        }
        if (progress > 0.001f) {
            paint.alpha = (255 * progress).roundToInt()
            paint.shader = LinearGradient(
                0f, top, 0f, top + height,
                intArrayOf(Color.rgb(238, 240, 255), Color.WHITE, Color.rgb(195, 203, 255), Color.rgb(169, 180, 255)),
                floatArrayOf(0f, 0.35f, 0.53f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(destination, height / 2f, height / 2f, paint)
        }
        paint.shader = null
        stroke.color = Color.argb(71, 190, 198, 255)
        stroke.strokeWidth = sceneLayout.k
        stroke.alpha = 255
        canvas.drawRoundRect(destination, height / 2f, height / 2f, stroke)
        paint.color = Color.argb(107, 244, 245, 255)
        paint.alpha = 255
        canvas.drawRect(left + 20f * sceneLayout.k, top, left + width - 20f * sceneLayout.k, top + max(1f, sceneLayout.k), paint)

        if (progress < 0.999f) {
            val hintOpacity = value(ChromePrizeWheelValue.Hint).toFloat() * (1f - progress)
            val glyphSize = 24f * sceneLayout.k
            val contentWidth = glyphSize + 8f * sceneLayout.k + textWidth("Swipe to spin", 15f * sceneLayout.fontScale, assets.regularTypeface)
            val glyphLeft = sceneLayout.cx - contentWidth / 2f + 6f * nudge
            destination.set(glyphLeft, sceneLayout.buttonY - glyphSize / 2f - 6f * progress, glyphLeft + glyphSize, sceneLayout.buttonY + glyphSize / 2f - 6f * progress)
            paint.alpha = (255 * hintOpacity).roundToInt()
            canvas.drawBitmap(assets.swipe, null, destination, paint)
            paint.typeface = assets.regularTypeface
            paint.textSize = 15f * sceneLayout.fontScale
            paint.color = Color.rgb(228, 230, 255)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Swipe to spin", destination.right + 8f * sceneLayout.k, textBaselineCentered(sceneLayout.buttonY - 6f * progress, paint), paint)
        }
        if (progress > 0.001f) {
            paint.typeface = assets.regularTypeface
            paint.textSize = 17f * sceneLayout.fontScale
            paint.color = Color.rgb(11, 14, 63)
            paint.alpha = (255 * progress).roundToInt()
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Collect", sceneLayout.cx + 6f * nudge, textBaselineCentered(sceneLayout.buttonY + 6f * (1f - progress), paint), paint)
        }
        canvas.restore()
        paint.textAlign = Paint.Align.LEFT
        paint.alpha = 255
    }

    private fun drawTouch(canvas: Canvas) {
        val alpha = value(ChromePrizeWheelValue.TouchOn).toFloat().coerceIn(0f, 1f)
        if (alpha <= 0.001f) return
        val x = value(ChromePrizeWheelValue.TouchX).toFloat()
        val y = value(ChromePrizeWheelValue.TouchY).toFloat()
        paint.shader = null
        paint.color = Color.argb((36 * alpha).roundToInt(), 255, 255, 255)
        canvas.drawCircle(x, y, 20f * sceneLayout.k, paint)
        stroke.color = Color.argb((115 * alpha).roundToInt(), 255, 255, 255)
        stroke.strokeWidth = max(1f, sceneLayout.k)
        canvas.drawCircle(x, y, 20f * sceneLayout.k, stroke)
    }

    private fun drawAtlasTile(canvas: Canvas, tile: Int, x: Float, y: Float, size: Float, angle: Float, alpha: Float) {
        if (alpha <= 0.001f || size <= 0f) return
        source.set(tile * 192, 0, tile * 192 + 192, 192)
        destination.set(-size / 2f, -size / 2f, size / 2f, size / 2f)
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat())
        paint.alpha = (255 * alpha.coerceIn(0f, 1f)).roundToInt()
        paint.shader = null
        canvas.drawBitmap(assets.particles, source, destination, paint)
        canvas.restore()
        paint.alpha = 255
    }

    private fun baseline(top: Float, lineHeight: Float, textPaint: Paint): Float {
        val metrics = textPaint.fontMetrics
        return top + (lineHeight - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
    }

    private fun textBaselineCentered(centerY: Float, textPaint: Paint): Float {
        val metrics = textPaint.fontMetrics
        return centerY - (metrics.ascent + metrics.descent) / 2f
    }

    private fun textWidth(text: String, size: Float, typeface: Typeface): Float {
        paint.textSize = size
        paint.typeface = typeface
        return paint.measureText(text)
    }

    private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)
    private fun mix(a: Float, b: Float, amount: Float): Float = a + (b - a) * amount
}

private enum class ChromePrizeWheelInteraction { None, Wheel, Hero, Button }

private data class ChromePrizeWheelParticle(
    val near: Boolean,
    val x: Float,
    val y: Float,
    val size: Float,
    val angle: Float,
    val tile: Int,
    val opacity: Float,
)

private data class ChromePrizeWheelPrizePose(
    val x: Float,
    val y: Float,
    val side: Float,
    val angle: Float,
    val sx: Float,
    val sy: Float,
    val baseShift: Float,
    val opacity: Float,
    val startX: Float,
    val startY: Float,
    val tile: Float,
    val sway: Float,
    val foreshortenX: Float,
    val foreshortenY: Float,
)

private class ChromePrizeWheelLayout(width: Float, height: Float) {
    val k = min(width / 402f, height / 874f)
    val fontScale = max(k, 0.82f)
    val cx = width / 2f
    val cy = if (abs(width / k - 402f) < 0.5f && abs(height / k - 874f) < 0.5f) 424f * k else 62f * k + (height - 96f * k) * 0.465f
    val radius = 164f * k
    val titleTop = 78f * k
    val titleSize = 34f * fontScale
    val titleLine = 40f * fontScale
    val subtitleTop = 126f * k
    val bodySize = 17f * fontScale
    val bodyLine = 24f * fontScale
    val pointerX = cx - 1.12f * radius
    val pointerY = cy
    val heroX = cx
    val heroY = cy - 20f * k
    val heroSize = 216f * k
    val buttonHeight = (if (height / k < 700f) 48f else 56f) * k
    val buttonY = height - 34f * k - 24f * k - buttonHeight / 2f
    val buttonTop = buttonY - buttonHeight / 2f
    val captionHeight = 88f * fontScale + 4f * k
    val captionTop = if (abs(width / k - 402f) < 0.5f && abs(height / k - 874f) < 0.5f) 628f * k else
        (cy + radius + buttonTop - captionHeight) / 2f
    val numeralSize = 56f * fontScale
    val numeralLine = 64f * fontScale
    val labelTop = captionTop + 64f * fontScale + 4f * k
    val captionY = captionTop + 32f * fontScale
    val buttonX = cx
    val buttonWidth = width - 48f * k
    val hintWidth = 196f * max(k, 0.9f)
    val hintHeight = 48f * k
    private val backdropScale = max(max(width / 804f, height / 1748f), max(cy / 848f, (height - cy) / 900f))
    val backdropLeft = (width - 804f * backdropScale) / 2f
    val backdropTop = cy - 848f * backdropScale
    val backdropRight = backdropLeft + 804f * backdropScale
    val backdropBottom = backdropTop + 1748f * backdropScale
}

private class ChromePrizeWheelAssets(context: Context) {
    val backdrop = context.bitmap(R.drawable.chromeprizewheel_wheel_backdrop)
    val env = context.bitmap(R.drawable.chromeprizewheel_wheel_env)
    val envSoft = context.bitmap(R.drawable.chromeprizewheel_wheel_env_soft)
    val wheelPrizes = context.bitmap(R.drawable.chromeprizewheel_wheel_prizes)
    val particles = context.bitmap(R.drawable.chromeprizewheel_wheel_particles)
    val pointer = context.bitmap(R.drawable.chromeprizewheel_wheel_pointer)
    val pointerShadow = context.bitmap(R.drawable.chromeprizewheel_wheel_pointer_shadow)
    val badges = context.bitmap(R.drawable.chromeprizewheel_wheel_badges)
    val swipe = context.bitmap(R.drawable.chromeprizewheel_wheel_swipe)
    val prizes = listOf(
        context.bitmap(R.drawable.chromeprizewheel_prize_plate),
        context.bitmap(R.drawable.chromeprizewheel_prize_chest),
        context.bitmap(R.drawable.chromeprizewheel_prize_tray),
        context.bitmap(R.drawable.chromeprizewheel_prize_box),
        context.bitmap(R.drawable.chromeprizewheel_prize_coffee),
        context.bitmap(R.drawable.chromeprizewheel_prize_stack),
    )
    val blackTypeface: Typeface = ResourcesCompat.getFont(context, R.font.chromeprizewheel_archivo_expanded_black) ?: Typeface.DEFAULT_BOLD
    val regularTypeface: Typeface = ResourcesCompat.getFont(context, R.font.chromeprizewheel_archivo_expanded_regular) ?: Typeface.DEFAULT
}

private class ChromePrizeWheelSurface(private val assets: ChromePrizeWheelAssets) {
    private val gpu = if (Build.VERSION.SDK_INT >= 33) runCatching { ChromePrizeWheelGpuSurface(assets) }.getOrNull() else null
    private val fallback = ChromePrizeWheelFallbackSurface(assets)

    fun draw(canvas: Canvas, paint: Paint, sceneLayout: ChromePrizeWheelLayout, values: ChromePrizeWheelValues) {
        if (gpu != null && Build.VERSION.SDK_INT >= 33) gpu.draw(canvas, paint, sceneLayout, values)
        else fallback.draw(canvas, paint, sceneLayout, values)
    }
}

@TargetApi(33)
private class ChromePrizeWheelGpuSurface(assets: ChromePrizeWheelAssets) {
    private val shader = RuntimeShader(ChromePrizeWheelAgsl)

    init {
        shader.setInputShader("uEnv", BitmapShader(assets.env, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP))
        shader.setInputShader("uEnvSoft", BitmapShader(assets.envSoft, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP))
        shader.setInputShader("uPrizes", BitmapShader(assets.wheelPrizes, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
    }

    fun draw(canvas: Canvas, paint: Paint, sceneLayout: ChromePrizeWheelLayout, values: ChromePrizeWheelValues) {
        shader.setFloatUniform("uCentre", sceneLayout.cx, sceneLayout.cy)
        shader.setFloatUniform("uRadius", sceneLayout.radius)
        shader.setFloatUniform("uPixelRatio", 1f)
        shader.setFloatUniform("uTheta", (values[ChromePrizeWheelValue.Theta] % ChromePrizeWheelTau).toFloat())
        shader.setFloatUniform("uSpan", (values[ChromePrizeWheelValue.Omega] / 72.0).coerceIn(-0.3, 0.3).toFloat())
        shader.setFloatUniform("uTilt", values[ChromePrizeWheelValue.TiltX].toFloat(), values[ChromePrizeWheelValue.TiltY].toFloat())
        shader.setFloatUniform("uEnvTurn", values[ChromePrizeWheelValue.EnvTurn].toFloat())
        shader.setFloatUniform("uSwayA", values[ChromePrizeWheelValue.SwayA].toFloat())
        shader.setFloatUniform("uSwayB", values[ChromePrizeWheelValue.SwayB].toFloat())
        shader.setFloatUniform("uWinner", values[ChromePrizeWheelValue.Winner].toFloat())
        shader.setFloatUniform("uLift", values[ChromePrizeWheelValue.Lift].toFloat())
        shader.setFloatUniform("uGlow", values[ChromePrizeWheelValue.Glow].toFloat())
        shader.setFloatUniform("uWinnerPrize", values[ChromePrizeWheelValue.WinnerPrize].toFloat())
        shader.setFloatUniform("uWinnerPrizeScale", values[ChromePrizeWheelValue.WinnerPrizeScale].toFloat())
        shader.setFloatUniform("uDim", values[ChromePrizeWheelValue.Dim].toFloat())
        shader.setFloatUniform("uComet", values[ChromePrizeWheelValue.Comet].toFloat())
        shader.setFloatUniform("uHubPress", values[ChromePrizeWheelValue.HubPress].toFloat())
        paint.shader = shader
        paint.alpha = (255 * values[ChromePrizeWheelValue.WheelOpacity].coerceIn(0.0, 1.0)).roundToInt()
        canvas.drawRect(
            sceneLayout.cx - sceneLayout.radius - 2f,
            sceneLayout.cy - sceneLayout.radius - 2f,
            sceneLayout.cx + sceneLayout.radius + 2f,
            sceneLayout.cy + sceneLayout.radius + 2f,
            paint,
        )
        paint.shader = null
        paint.alpha = 255
    }
}

private class ChromePrizeWheelFallbackSurface(private val assets: ChromePrizeWheelAssets) {
    private val source = Rect()
    private val destination = RectF()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val wedge = Path()

    fun draw(canvas: Canvas, paint: Paint, sceneLayout: ChromePrizeWheelLayout, values: ChromePrizeWheelValues) {
        val theta = values[ChromePrizeWheelValue.Theta].toFloat()
        val dim = values[ChromePrizeWheelValue.Dim].toFloat()
        paint.shader = RadialGradient(
            sceneLayout.cx, sceneLayout.cy, sceneLayout.radius,
            intArrayOf(Color.rgb(13, 20, 70), Color.rgb(4, 5, 28)), null, Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(sceneLayout.cx, sceneLayout.cy, sceneLayout.radius, paint)
        for (index in 0 until 6) {
            val centre = theta + index * (PI / 3.0).toFloat()
            val start = Math.toDegrees((centre - PI.toFloat() / 6f).toDouble()).toFloat()
            paint.shader = LinearGradient(
                sceneLayout.cx, sceneLayout.cy - sceneLayout.radius, sceneLayout.cx, sceneLayout.cy + sceneLayout.radius,
                Color.rgb(40, 124, 255), Color.rgb(20, 47, 175), Shader.TileMode.CLAMP,
            )
            paint.alpha = (255 * (1f - dim * 0.62f)).roundToInt()
            canvas.drawArc(
                sceneLayout.cx - sceneLayout.radius * 0.825f, sceneLayout.cy - sceneLayout.radius * 0.825f,
                sceneLayout.cx + sceneLayout.radius * 0.825f, sceneLayout.cy + sceneLayout.radius * 0.825f,
                start, 60f, true, paint,
            )
            source.set(index % 3 * 384, index / 3 * 384, index % 3 * 384 + 384, index / 3 * 384 + 384)
            val prizeRadius = sceneLayout.radius * 0.575f
            val x = sceneLayout.cx + cos(centre) * prizeRadius
            val y = sceneLayout.cy + sin(centre) * prizeRadius
            val side = sceneLayout.radius * 0.46f
            destination.set(x - side / 2f, y - side / 2f, x + side / 2f, y + side / 2f)
            canvas.drawBitmap(assets.wheelPrizes, source, destination, paint)
        }
        stroke.strokeWidth = sceneLayout.radius * 0.1f
        stroke.shader = LinearGradient(
            sceneLayout.cx - sceneLayout.radius, sceneLayout.cy - sceneLayout.radius, sceneLayout.cx + sceneLayout.radius, sceneLayout.cy + sceneLayout.radius,
            intArrayOf(Color.rgb(110, 126, 242), Color.WHITE, Color.rgb(18, 30, 101), Color.rgb(205, 211, 255)), null,
            Shader.TileMode.MIRROR,
        )
        stroke.alpha = 255
        canvas.drawCircle(sceneLayout.cx, sceneLayout.cy, sceneLayout.radius * 0.905f, stroke)
        stroke.shader = null
        stroke.strokeWidth = sceneLayout.radius * 0.025f
        stroke.color = Color.rgb(148, 160, 225)
        canvas.drawCircle(sceneLayout.cx, sceneLayout.cy, sceneLayout.radius * 0.985f, stroke)
        paint.shader = RadialGradient(
            sceneLayout.cx - sceneLayout.radius * 0.04f, sceneLayout.cy - sceneLayout.radius * 0.05f, sceneLayout.radius * 0.22f,
            intArrayOf(Color.WHITE, Color.rgb(70, 77, 153), Color.rgb(7, 9, 44)), null, Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(sceneLayout.cx, sceneLayout.cy, sceneLayout.radius * 0.215f, paint)
        paint.shader = null
        paint.alpha = 255
    }
}

private fun Context.bitmap(id: Int): Bitmap = BitmapFactory.decodeResource(resources, id)

private fun Context.motionEnabledForChromePrizeWheel(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

private val ChromePrizeWheelSway = arrayOf(
    floatArrayOf(1f, 0.25f),
    floatArrayOf(0.7f, -0.6f),
    floatArrayOf(0.85f, 0.5f),
    floatArrayOf(1.05f, -0.2f),
    floatArrayOf(0.6f, 0.75f),
    floatArrayOf(0.9f, -0.45f),
)

private val ChromePrizeWheelSkyStars = arrayOf(
    floatArrayOf(-143f, -217f, 44f, -0.3f, 0f), floatArrayOf(111f, -217f, 35f, 0.23f, 0f),
    floatArrayOf(-48f, -244f, 16f, 0.28f, 1f), floatArrayOf(32f, -207f, 19f, -0.2f, 1f),
    floatArrayOf(-170f, -129f, 23f, 0.38f, 0f), floatArrayOf(174f, -113f, 16f, -0.3f, 1f),
    floatArrayOf(183f, -42f, 13f, 0.32f, 0f), floatArrayOf(179f, 102f, 13f, -0.3f, 1f),
    floatArrayOf(-175f, 139f, 15f, 0.1f, 1f), floatArrayOf(-147f, 225f, 40f, -0.25f, 1f),
    floatArrayOf(143f, 244f, 32f, 0.2f, 0f), floatArrayOf(-126f, 300f, 16f, 0.3f, 1f),
    floatArrayOf(165f, 188f, 17f, -0.14f, 0f), floatArrayOf(-68f, 183f, 15f, 0.2f, 1f),
)

private val ChromePrizeWheelSkyDots = arrayOf(
    floatArrayOf(-113f, -243f, 13f), floatArrayOf(-64f, -205f, 7f), floatArrayOf(147f, -255f, 11f),
    floatArrayOf(61f, -247f, 6f), floatArrayOf(5f, -229f, 10f), floatArrayOf(-169f, -178f, 7f),
    floatArrayOf(160f, -171f, 9f), floatArrayOf(-187f, -79f, 10f), floatArrayOf(191f, 26f, 6f),
    floatArrayOf(-187f, 69f, 6f), floatArrayOf(-181f, 197f, 8f), floatArrayOf(-106f, 211f, 11f),
    floatArrayOf(176f, 288f, 7f), floatArrayOf(88f, 301f, 8f), floatArrayOf(-30f, 301f, 5f),
    floatArrayOf(65f, 179f, 9f), floatArrayOf(159f, 140f, 7f), floatArrayOf(-162f, 270f, 5f),
)
