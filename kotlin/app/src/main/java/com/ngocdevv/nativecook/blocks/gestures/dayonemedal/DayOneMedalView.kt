package com.ngocdevv.nativecook.blocks.gestures.dayonemedal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.R
import org.json.JSONObject
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.asin
import kotlin.math.tanh

/**
 * A single-clock native rendering of the forged medal, its sheet, ribbon, particles and gestures.
 * Shipping motion is Kotlin; the source block is never executed at runtime.
 */
internal class DayOneMedalView(
    context: Context,
    private val autoPlay: Boolean,
) : View(context), Choreographer.FrameCallback {
    private val regular = ResourcesCompat.getFont(context, R.font.dayonemedal_archivo_expanded_regular)
        ?: Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val black = ResourcesCompat.getFont(context, R.font.dayonemedal_archivo_expanded_black)
        ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val fronts = listOf(
        alphaStrip(R.raw.dayonemedal_front_1),
        alphaStrip(R.raw.dayonemedal_front_7),
        alphaStrip(R.raw.dayonemedal_front_30),
    )
    private val back = alphaStrip(R.raw.dayonemedal_back)
    private val medal = DayOneMedalShader(fronts, back)
    private val icons = JSONObject(resources.openRawResource(R.raw.dayonemedal_icons).bufferedReader().use { it.readText() })
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val aura = RadialGradient(0f, 0f, 1f,
        intArrayOf(0xC9E3A860.toInt(), 0xA7DDA154.toInt(), 0x40B46A31, Color.TRANSPARENT),
        floatArrayOf(0f, 0.48f, 0.73f, 1f), Shader.TileMode.CLAMP)
    private val auraMatrix = Matrix()
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = aura }
    private val sheetPath = Path()
    private val ribbon = DayOneMedalRibbon()
    private val leaves = DayOneMedalLeaves()
    private val sparks = run {
        var seed = 7919
        fun next(): Float {
            seed = seed * 1664525 + 1013904223
            return (seed.toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
        }
        List(72) {
            val angle = next() * TwoPi -
                if (next() < 0.45f) PI.toFloat() / 2f - (next() - 0.5f) * 1.6f else 0f
            Spark(
                angle = angle,
                speed = 420f + 980f * next() * next(),
                life = 0.45f + 0.8f * next(),
                width = 1f + 1.5f * next(),
                delay = 0.06f * next(),
            )
        }
    }

    private var running = false
    private var scheduled = false
    private var lastFrame = 0L
    private val timeline = DayOneMedalTimeline()
    private var reducedMotion = false
    private var motionSettingCheckedAt = 0L
    private var manualUntil = 0L
    private var clock = 0f
    private var present = 0f
    private var presentTarget = 0f
    private var forged = false
    private var holding = false
    private var holdTime = 0f
    private var holdStart = 0f
    private var charge = 0f
    private var chargeVelocity = 0f
    private var press = 0f
    private var pressTarget = 0f
    private var stamp = 0f
    private var heat = 0f
    private var struckAt = -10f
    private var recoil = 0f
    private var recoilVelocity = 0f
    private var yaw = 0f
    private var yawVelocity = 0f
    private var yawTarget = 0f
    private var turnMode = TurnRest
    private var turnStiffness = 110f
    private var turnDamping = 13f
    private var wobbleTime = 0f
    private var wobblePhase = 0f
    private var restTime = 0f
    private var sway = 0f
    private var swayPhase = 0f
    private var pitch = Pitch
    private var roll = 0f
    private var sweep = SweepTo
    private var sweepTime = SweepDuration
    private var lightX = 0f
    private var lightY = 0f
    private var lightVelocityX = 0f
    private var lightVelocityY = 0f
    private var lightTargetX = 0f
    private var lightTargetY = 0f
    private var worn = 0f
    private var wearing = false
    private var wearStart = 0f
    private var wearTime = 0f
    private var caught = false
    private var theta = 0f
    private var thetaVelocity = 0f
    private var pullStart = 0f
    private var drop = 0f
    private var dropVelocity = 0f
    private var nudge = 0f
    private var nudgeVelocity = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private var day = 0
    private var fromDay = 0
    private var toDay = 0
    private var morph = 0f
    private var morphTime = 0f
    private var engraving = 0f
    private var engraveTime = 0.0
    private var draggingSheet = false
    private var sheetDrag = 0f
    private var down = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var lastMoveAt = 0L
    private var velocityX = 0f
    private var velocityY = 0f
    private var travelX = 0f
    private var travelY = 0f

    init {
        setBackgroundColor(Color.BLACK)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        resetMotion()
    }

    private fun alphaStrip(resource: Int): Bitmap {
        val bytes = resources.openRawResource(resource).use { it.readBytes() }
        require(bytes.size == 3072 * 956)
        return Bitmap.createBitmap(3072, 956, Bitmap.Config.ALPHA_8).apply {
            copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        }
    }

    fun start() {
        reducedMotion = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
        if (!running) {
            running = true
            lastFrame = 0L
            if (autoPlay && !reducedMotion) timeline.start(clock)
            scheduleFrame()
        }
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        scheduled = false
        lastFrame = 0L
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!running || !isAttachedToWindow) return
        val elapsed = if (lastFrame == 0L) 0f else
            ((frameTimeNanos - lastFrame) / 1_000_000_000f).coerceIn(0f, MaxCatchUp)
        lastFrame = frameTimeNanos
        var remaining = elapsed
        while (remaining > 0.000001f) {
            val chunk = min(remaining, MaxFrame)
            step(chunk)
            remaining -= chunk
        }
        invalidate()
        scheduleFrame()
    }

    private fun scheduleFrame() {
        if (!scheduled && running) {
            scheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun step(frameSeconds: Float) {
        val now = SystemClock.uptimeMillis()
        if (now - motionSettingCheckedAt > 500L) {
            motionSettingCheckedAt = now
            val nextReduced = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
            if (nextReduced != reducedMotion) {
                reducedMotion = nextReduced
                resetMotion()
                if (autoPlay && !reducedMotion) timeline.start(clock) else timeline.stop()
            }
        }
        var remaining = frameSeconds
        while (remaining > 0.000001f) {
            val dt = min(1f / 120f, remaining)
            remaining -= dt
            clock += dt
            if (autoPlay && !reducedMotion && !down) stepScript()
            val oldPresent = present
            val oldStruck = struckAt
            val oldHeat = heat
            val oldTurn = turnMode
            val oldEngraving = engraving
            val oldWorn = worn
            val oldMorph = morph
            stepPresentation(dt)
            stepTurn(dt)
            stepForge(dt)
            stepWear(dt)
            stepMorph(dt)
            stepEngrave(dt)
            press = moveTowards(press, pressTarget, dt / if (pressTarget > press) 0.12f else 0.18f)
            if (!reducedMotion) leaves.step(dt, 215f, 273f, yawVelocity, yaw)
            val nextLightX = DayOneMedalPhysics.critical(lightX, lightVelocityX, lightTargetX, dt)
            val nextLightY = DayOneMedalPhysics.critical(lightY, lightVelocityY, lightTargetY, dt)
            lightX = nextLightX.position
            lightVelocityX = nextLightX.velocity
            lightY = nextLightY.position
            lightVelocityY = nextLightY.velocity
            sweepTime = min(SweepDuration, sweepTime + dt)
            sweep = mix(SweepFrom, SweepTo, smooth(sweepTime / SweepDuration))
            if (autoPlay && !reducedMotion && !down) {
                if (oldPresent < 1f && present >= 1f) timeline.signal(DayOneMedalTimeline.Event.ARRIVED, clock)
                if (oldStruck != struckAt && struckAt >= 0f) timeline.signal(DayOneMedalTimeline.Event.STRUCK, clock)
                if (forged && oldHeat > 0f && heat <= 0f) timeline.signal(DayOneMedalTimeline.Event.COOLED, clock)
                if (oldTurn != TurnRest && turnMode == TurnRest) {
                    timeline.signal(DayOneMedalTimeline.Event.LANDED, clock)
                    timeline.signal(if (abs((yaw / PI.toFloat()).roundToInt() % 2) == 1) DayOneMedalTimeline.Event.BACK else DayOneMedalTimeline.Event.FRONT, clock)
                }
                if (oldEngraving < 1f && engraving >= 1f) timeline.signal(DayOneMedalTimeline.Event.ENGRAVED, clock)
                if (oldWorn < 0.55f && worn >= 0.55f) timeline.signal(DayOneMedalTimeline.Event.CAUGHT, clock)
                if (oldWorn > 0f && worn <= 0f) timeline.signal(DayOneMedalTimeline.Event.UNWORN, clock)
                if (oldMorph > 0f && morph == 0f) timeline.signal(DayOneMedalTimeline.Event.MILESTONE, clock)
                if (oldPresent > 0f && present <= 0f) timeline.signal(DayOneMedalTimeline.Event.CLOSED, clock)
            }
        }
    }

    private fun stepScript() {
        val now = SystemClock.uptimeMillis()
        if (now < manualUntil) return
        if (manualUntil != 0L) {
            manualUntil = 0L
            resetMotion()
            timeline.start(clock)
        }
        when (timeline.advance(clock)) {
            DayOneMedalTimeline.Command.RESET -> {
                resetMotion()
                timeline.signal(DayOneMedalTimeline.Event.RESET, clock)
            }
            DayOneMedalTimeline.Command.OPEN -> presentTarget = 1f
            DayOneMedalTimeline.Command.FORGE -> {
                holding = true
                holdTime = 0f
                holdStart = charge
            }
            DayOneMedalTimeline.Command.TURN -> flip(drift = true)
            DayOneMedalTimeline.Command.FLICK -> flick()
            DayOneMedalTimeline.Command.WEAR -> toggleWear()
            DayOneMedalTimeline.Command.NEXT -> changeDay(1)
            DayOneMedalTimeline.Command.CLOSE -> presentTarget = 0f
            null -> Unit
        }
    }

    private fun stepPresentation(dt: Float) {
        val duration = if (reducedMotion) 0.16f else if (presentTarget > present) 0.5f else 0.38f
        val amount = dt / duration
        present = if (presentTarget > present) min(presentTarget, present + amount) else max(presentTarget, present - amount)
    }

    private fun stepTurn(dt: Float) {
        pitch = Pitch
        roll = 0f
        when (turnMode) {
            TurnCoast, TurnCoin -> {
                val tau = if (turnMode == TurnCoin) 1.6f else 0.85f
                yawVelocity *= exp(-dt / tau)
                yaw += yawVelocity * dt
                val handoff = if (turnMode == TurnCoin) 420f * Rad else 260f * Rad
                if (abs(yawVelocity) < handoff) {
                    yawTarget = nearestFace(yaw + if (turnMode == TurnCoin) 0f else yawVelocity * 0.22f)
                    turnStiffness = 110f
                    turnDamping = 13f
                    wobbleTime = 0f
                    wobblePhase = 0f
                    turnMode = if (turnMode == TurnCoin) TurnWobble else TurnSettle
                }
            }
            TurnSettle, TurnWobble -> {
                yawVelocity += (-turnStiffness * (yaw - yawTarget) - turnDamping * yawVelocity) * dt
                yaw += yawVelocity * dt
                if (turnMode == TurnWobble) {
                    wobbleTime += dt
                    val progress = (wobbleTime / 1.3f).coerceIn(0f, 1f)
                    val frequency = 2.5f + 6.5f * progress
                    wobblePhase += TwoPi * frequency * dt
                    val taper = 1f - smooth(((progress - 0.8f) / 0.2f).coerceIn(0f, 1f))
                    val onset = smooth((wobbleTime / 0.15f).coerceIn(0f, 1f))
                    val amplitude = onset * 14f * Rad * (2.5f / frequency).pow(2) * taper
                    pitch += amplitude * sin(wobblePhase)
                    roll += amplitude * cos(wobblePhase)
                }
                if ((turnMode == TurnWobble && wobbleTime >= 1.3f) ||
                    (turnMode == TurnSettle && abs(yawVelocity) < Rad && abs(yaw - yawTarget) < 0.08f * Rad)) {
                    yaw = yawTarget
                    yawVelocity = 0f
                    turnMode = TurnRest
                    restTime = 0f
                    pitch = Pitch
                    roll = 0f
                    sweepTime = 0f
                }
            }
            TurnDrag -> Unit
            else -> {
                if (!down && !holding && !reducedMotion) {
                    restTime += dt
                    val goal = if (restTime > 0.9f) 1f else 0f
                    sway += (goal - sway) * (1f - exp(-dt / 1.1f))
                    swayPhase += dt * TwoPi / 5.2f
                    pitch = Pitch + 0.03f * sway * cos(swayPhase * 2f)
                }
            }
        }
    }

    private fun stepForge(dt: Float) {
        if (holding && !forged) {
            holdTime += dt
            val progress = (holdTime / 1.4f).coerceIn(0f, 1f)
            charge = holdStart + (1f - holdStart) * (1f - (1f - progress).pow(1.6f))
            if (holdTime >= 1.4f) strike()
        } else if (!forged) {
            val cool = DayOneMedalPhysics.oscillator(charge, chargeVelocity, 0f, 150f, 18f, dt)
            charge = max(0f, cool.position)
            chargeVelocity = if (charge == 0f) 0f else cool.velocity
        }
        if (forged) {
            val age = clock - struckAt
            stamp = if (reducedMotion) 1f else easeOut((age / 0.26f).coerceIn(0f, 1f))
            heat = if (reducedMotion) 1f - (age / 0.2f).coerceIn(0f, 1f) else if (age < 0.11f) {
                1f - (1f - age / 0.11f).pow(2)
            } else {
                (1f - ((age - 0.11f) / 1.5f).coerceIn(0f, 1f)).pow(3)
            }
        }
        val recoilStep = DayOneMedalPhysics.oscillator(recoil, recoilVelocity, 0f, 110f, 13f, dt)
        recoil = if (reducedMotion) 0f else recoilStep.position
        recoilVelocity = if (reducedMotion) 0f else recoilStep.velocity
        pitch += recoil
    }

    private fun stepWear(dt: Float) {
        val target = if (wearing) 1f else 0f
        val duration = if (reducedMotion) 0.2f else if (wearing) 0.7f else 0.45f
        wearTime += dt
        worn = wearStart + (target - wearStart) * (wearTime / duration).coerceIn(0f, 1f)
        if (wearing && !caught && worn >= 0.55f) {
            caught = true
            if (!reducedMotion) thetaVelocity += 0.34f
        }
        val nextDrop = DayOneMedalPhysics.oscillator(drop, dropVelocity, if (caught) 10f else 0f, 110f, 13f, dt)
        drop = if (reducedMotion) 0f else nextDrop.position
        dropVelocity = if (reducedMotion) 0f else nextDrop.velocity
        if (reducedMotion) {
            theta = 0f
            thetaVelocity = 0f
        } else if (!caught || !down) {
            val swing = DayOneMedalPhysics.oscillator(theta, thetaVelocity, 0f,
                if (caught) 980f / pendulumLength() else 110f,
                if (caught) 0.5f else 13f, dt)
            val limit = pendulumLimit()
            theta = swing.position.coerceIn(-limit, limit)
            thetaVelocity = if (abs(swing.position) > limit) 0f else swing.velocity
        }
        val length = pendulumLength() + drop
        offsetX = length * sin(theta)
        val nudgeStep = DayOneMedalPhysics.oscillator(nudge, nudgeVelocity, 0f, 110f, 13f, dt)
        nudge = nudgeStep.position
        nudgeVelocity = nudgeStep.velocity
        offsetY = drop + length * (cos(theta) - 1f) + if (reducedMotion) 0f else nudge
        if (!reducedMotion && charge > 0f) {
            val fast = clock * TwoPi * 31f
            val slow = clock * TwoPi * 19f
            val shakeX = 0.7f * sin(fast) + 0.3f * sin(slow)
            val shakeY = cos(fast) * sin(slow)
            val magnitude = max(1f, sqrt(shakeX * shakeX + shakeY * shakeY))
            offsetX += 1.5f * charge * shakeX / magnitude
            offsetY += 1.5f * charge * shakeY / magnitude
            roll += 0.6f * Rad * charge * shakeX
        }
        val medalX = 215f + offsetX
        val medalY = 273f + offsetY
        ribbon.step(dt, worn, 215f, -40f, medalX, medalY - 177.5f * 0.93f, reducedMotion)
    }

    private fun pendulumLength() = max(80f, 273f - (-40f))

    private fun pendulumLimit(): Float {
        val reach = max(0f, 215f - 177.5f * (190f / 355f) - 24f)
        return min(28f * Rad, asin((reach / (pendulumLength() + 177.5f + 10f)).coerceIn(0f, 1f)))
    }

    private fun stepMorph(dt: Float) {
        if (morph <= 0f) return
        val duration = if (reducedMotion) 0.25f else 0.9f
        morphTime += dt
        val progress = (morphTime / duration).coerceIn(0f, 1f)
        morph = if (reducedMotion) if (progress < 0.5f) 0.001f else 1f else smooth(progress)
        if (progress >= 0.5f) day = toDay
        if (!reducedMotion) heat = max(heat, 0.55f * sin(PI.toFloat() * progress))
        if (progress >= 1f) {
            fromDay = toDay
            day = toDay
            morph = 0f
        }
    }

    private fun stepEngrave(dt: Float) {
        val backVisible = abs((yaw / PI.toFloat()).roundToInt() % 2) == 1
        if (!forged || engraving >= 1f || !backVisible || turnMode != TurnRest) return
        engraveTime += dt.toDouble()
        engraving = if (reducedMotion) (engraveTime / 0.4).toFloat().coerceIn(0f, 1f)
            else DayOneMedalPhysics.engravingAt(engraveTime).progress
    }

    private fun strike() {
        forged = true
        holding = false
        charge = 0f
        chargeVelocity = 0f
        press = 0f
        pressTarget = 0f
        holdTime = 0f
        holdStart = 0f
        struckAt = clock
        recoil = if (reducedMotion) 0f else -9f * Rad
        recoilVelocity = 0f
        leaves.strike()
        performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.VIRTUAL_KEY,
            )
        announceForAccessibility("Day 1 medal forged")
    }

    private fun flip(drift: Boolean = false) {
        if (!forged) return
        yawTarget = nearestFace(yaw) + PI.toFloat()
        turnStiffness = if (drift) 26f else 110f
        turnDamping = if (drift) 8f else 13f
        if (reducedMotion) {
            yaw = yawTarget
            turnMode = TurnRest
        } else {
            yawVelocity += if (drift) 0f else 220f * Rad
            turnMode = TurnSettle
            if (caught) thetaVelocity += 0.14f
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun flick() {
        if (!forged) return
        if (wearing && worn > 0.54f) {
            thetaVelocity += 0.68f
        } else if (!reducedMotion) {
            yawVelocity = 1600f * Rad
            turnMode = TurnCoin
        }
    }

    private fun toggleWear() {
        if (!forged) return
        wearing = !wearing
        wearStart = worn
        wearTime = 0f
        if (!wearing) caught = false
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        announceForAccessibility(if (wearing) "Medal worn" else "Medal removed")
    }

    private fun changeDay(delta: Int) {
        if (!forged || morph > 0f) return
        val target = (day + delta).coerceIn(0, 2)
        if (target == day) {
            if (!reducedMotion) nudgeVelocity = -delta * 5f * sqrt(110f)
            performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS,
            )
            return
        }
        fromDay = day
        toDay = target
        morphTime = 0f
        morph = 0.001f
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun resetMotion() {
        present = 0f
        presentTarget = 0f
        forged = false
        holding = false
        charge = 0f
        stamp = 0f
        heat = 0f
        struckAt = -10f
        recoil = 0f
        recoilVelocity = 0f
        yaw = 0f
        yawVelocity = 0f
        yawTarget = 0f
        turnMode = TurnRest
        turnStiffness = 110f
        turnDamping = 13f
        wobbleTime = 0f
        wobblePhase = 0f
        restTime = 0f
        sway = 0f
        swayPhase = 0f
        pitch = Pitch
        roll = 0f
        sweep = SweepTo
        sweepTime = SweepDuration
        worn = 0f
        wearing = false
        wearStart = 0f
        wearTime = 0f
        caught = false
        theta = 0f
        thetaVelocity = 0f
        pullStart = 0f
        drop = 0f
        dropVelocity = 0f
        nudge = 0f
        nudgeVelocity = 0f
        offsetX = 0f
        offsetY = 0f
        day = 0
        fromDay = 0
        toDay = 0
        morph = 0f
        morphTime = 0f
        engraving = 0f
        engraveTime = 0.0
        sheetDrag = 0f
        lightVelocityX = 0f
        lightVelocityY = 0f
        ribbon.reset()
        leaves.reset()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val screenScale = min(width / 402f, height / 874f)
        val logicalWidth = width / screenScale
        val logicalHeight = height / screenScale
        val gridScale = min(1f, min(logicalWidth / 430f, (logicalHeight - 62f - 34f - 116f) / 684f))
        val sheetScale = min(1f, min(logicalWidth / 430f, (logicalHeight - 81f) / 878f))
        canvas.save()
        canvas.scale(screenScale, screenScale)
        canvas.save()
        canvas.translate((logicalWidth - 430f * gridScale) / 2f, 62f)
        canvas.scale(gridScale, gridScale)
        drawGrid(canvas, (logicalHeight - 62f) / gridScale)
        canvas.restore()
        if (present > 0f) {
            canvas.save()
            canvas.translate((logicalWidth - 430f * sheetScale) / 2f, 81f)
            canvas.scale(sheetScale, sheetScale)
            drawAchievement(canvas, (logicalHeight - 81f) / sheetScale)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawGrid(canvas: Canvas, logicalHeight: Float) {
        paint.color = Color.BLACK
        canvas.drawRect(0f, 0f, 430f, logicalHeight, paint)
        val backgroundScale = 1f - 0.08f * present
        canvas.save()
        canvas.scale(backgroundScale, backgroundScale, 215f, logicalHeight / 2f)
        paint.color = Color.WHITE
        canvas.drawCircle(28f, 26f, 12f, paint)
        drawIcon(canvas, "close", 28f, 26f, 14f, Color.BLACK)
        text(canvas, "Collectibles", 215f, 32f, 17f, Color.WHITE, Typeface.DEFAULT, Paint.Align.CENTER)
        var y = 87f
        val shelves = listOf(
            "Goals" to listOf("calendar", "flame"),
            "Special" to listOf("laurel", "earned"),
            "Cities" to listOf("pagoda", "tower", "bridge", "lantern", "gate", "skyline", "lighthouse"),
        )
        for ((title, names) in shelves) {
            text(canvas, title, 16f, y, 17f, Color.WHITE, regular, Paint.Align.LEFT)
            y += 17f
            for (i in names.indices) {
                val col = i % 4
                val row = i / 4
                val left = 16f + col * 105f
                val top = y + row * 146f
                val rect = RectF(left, top, left + 86f, top + 127f)
                paint.color = 0xFF111013.toInt()
                canvas.drawRoundRect(rect, 43f, 43f, paint)
                stroke.strokeWidth = 0.8f
                stroke.color = if (title == "Special" && i == 1) 0xFF3A3526.toInt() else 0xFF26252A.toInt()
                canvas.drawRoundRect(rect, 43f, 43f, stroke)
                if (names[i] == "earned") {
                    if (present <= 0f) drawMedal(canvas, rect.centerX(), rect.centerY(), 54f,
                        if (forged) 1f else 0f, alpha = 1f)
                } else {
                    drawIcon(canvas, names[i], rect.centerX(), rect.centerY(), 22f, 0xFF4A4950.toInt())
                }
            }
            y += if (names.size > 4) 327f else 162f
        }
        text(canvas, if (forged) "Tap your medal." else "Tap the medal to begin.", 215f,
            logicalHeight - 56f, 13f,
            0xFF77767D.toInt(), Typeface.DEFAULT, Paint.Align.CENTER)
        canvas.restore()
    }

    private fun drawAchievement(canvas: Canvas, logicalHeight: Float) {
        val eased = cubicSheet(present)
        paint.color = Color.argb((128f * eased).roundToInt(), 0, 0, 0)
        canvas.drawRect(0f, 0f, 430f, logicalHeight, paint)
        val top = mix(logicalHeight, 0f, eased) + sheetDrag
        val bottom = logicalHeight + 80f
        sheetPath.reset()
        sheetPath.addRoundRect(RectF(-20f, top, 450f, bottom), 38f, 38f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(sheetPath)
        paint.color = 0xFF0E0D10.toInt()
        canvas.drawRect(-20f, top, 450f, bottom, paint)

        val stageY = top + 273f
        val slotX = 161.3f
        val slotY = 339.6f
        val centerX = mix(slotX, 215f + offsetX, eased)
        val centerY = mix(slotY, stageY + offsetY, eased) -
            sin(PI.toFloat() * eased) * 48f
        val half = mix(54f, 177.5f, eased)

        drawAura(canvas, centerX, centerY, half)
        if (!reducedMotion) drawStrikeRing(canvas, centerX, centerY)
        if (forged && !reducedMotion) leaves.draw(canvas, centerX, centerY, near = false)
        if (worn > 0.001f) drawRibbon(canvas, top, centerX, centerY, half)
        drawMedal(canvas, centerX, centerY, half * (1f - 0.03f * press), stamp, 1f)
        if (forged && !reducedMotion) leaves.draw(canvas, centerX, centerY, near = true)
        if (clock - struckAt in 0f..1.25f && !reducedMotion) drawStrike(canvas, centerX, centerY)

        val alpha = eased
        text(canvas, "Achievement", 215f, top + 39f, 17f,
            withAlpha(0xFF5F5E64.toInt(), alpha * (1f - worn)), regular, Paint.Align.CENTER)
        text(canvas, "Done", 408f, top + 39f, 17f, withAlpha(Color.WHITE, alpha), Typeface.DEFAULT_BOLD, Paint.Align.RIGHT)
        roundedRect(canvas, 371f, top + 81f, 37f, 37f, 9f, withAlpha(0xFF2B2A2F.toInt(), alpha))
        drawIcon(canvas, "capsule", 389.5f, top + 99.5f, 20f, withAlpha(Color.WHITE, alpha))

        val shownDay = Days[day]
        text(canvas, "DAY $shownDay", 215f, top + 528f, 41f, withAlpha(Color.WHITE, alpha), black, Paint.Align.CENTER)
        val copy = if (!forged) listOf("Hold the medal to forge it.") else Copy[day]
        copy.forEachIndexed { index, line ->
            text(canvas, line, 215f, top + 579f + index * 20f, 15f, withAlpha(Color.WHITE, alpha), Typeface.DEFAULT, Paint.Align.CENTER)
        }
        for (i in Days.indices) {
            paint.color = withAlpha(if (i == day) 0xFFDDA85A.toInt() else 0xFF4A4950.toInt(), alpha)
            canvas.drawCircle(210f + (i - 1) * 9f, top + 622f, 2f, paint)
        }
        val hint = when {
            !forged -> "Hold to forge"
            wearing -> "Drag to swing"
            day == 2 -> "Swipe down for the previous milestone"
            else -> "Swipe up for the next milestone"
        }
        text(canvas, hint, 215f, top + 649f, 13f, withAlpha(0xFF77767D.toInt(), alpha), Typeface.DEFAULT, Paint.Align.CENTER)
        button(canvas, top + 657f, if (wearing) "Take It Off" else "Wear Your Medal", quiet = true, alpha = alpha)
        button(canvas, top + 732f, "Share", quiet = false, alpha = alpha)
        canvas.restore()
    }

    private fun drawMedal(canvas: Canvas, x: Float, y: Float, half: Float, visibleStamp: Float, alpha: Float) {
        val drawn = medal.draw(
            canvas = canvas,
            centreX = x,
            centreY = y,
            half = half,
            yaw = yaw + if (turnMode == TurnRest && !reducedMotion) sway * 8f * Rad * sin(swayPhase) else 0f,
            pitch = pitch,
            roll = roll + theta,
            lightX = lightX,
            lightY = lightY,
            heat = heat,
            charge = charge,
            stamp = visibleStamp,
            morph = morph,
            engrave = engraving,
            sweep = sweep,
            from = fromDay,
            to = toDay,
            alpha = alpha,
        )
        if (!drawn) drawMedalFallback(canvas, x, y, half, visibleStamp, alpha)
    }

    private fun drawAura(canvas: Canvas, x: Float, y: Float, half: Float) {
        val strength = max(if (forged) 0.55f else 0f, max(charge * 0.7f, heat))
        if (strength <= 0f) return
        auraMatrix.setScale(half * 1.28f, half * 1.76f)
        auraMatrix.postTranslate(x, y)
        aura.setLocalMatrix(auraMatrix)
        auraPaint.alpha = (strength.coerceIn(0f, 1f) * 255f).roundToInt()
        canvas.drawOval(RectF(x - half * 1.28f, y - half * 1.76f,
            x + half * 1.28f, y + half * 1.76f), auraPaint)
    }

    private fun drawMedalFallback(canvas: Canvas, x: Float, y: Float, half: Float, visibleStamp: Float, alpha: Float) {
        val width = half * 1.0704f
        paint.color = withAlpha(0xFF242326.toInt(), alpha)
        canvas.drawRoundRect(RectF(x - width / 2f, y - half, x + width / 2f, y + half), width / 2f, width / 2f, paint)
        stroke.color = withAlpha(0xFF5C4430.toInt(), alpha)
        stroke.strokeWidth = 2f
        canvas.drawRoundRect(RectF(x - width * 0.45f, y - half * 0.92f, x + width * 0.45f, y + half * 0.92f), width * 0.4f, width * 0.4f, stroke)
        if (visibleStamp > 0.02f) {
            text(canvas, if (cos(yaw) >= 0f) Days[day].toString() else "NC", x, y + half * 0.27f, half * 0.92f, withAlpha(0xFFDDA85A.toInt(), alpha * visibleStamp), black, Paint.Align.CENTER)
        }
    }

    private fun drawStrikeRing(canvas: Canvas, x: Float, y: Float) {
        val age = clock - struckAt
        if (age !in 0f..0.75f) return
        val progress = age / 0.75f
        val expansion = 1f - (1f - progress).pow(3)
        val radius = 70f + 250f * expansion
        val energy = (1f - progress).pow(2)
        ringPaint.color = Color.argb((38f * energy).roundToInt(), 255, 166, 83)
        ringPaint.strokeWidth = 32f - 23f * progress
        canvas.drawCircle(x, y, radius, ringPaint)
        ringPaint.color = Color.argb((105f * energy).roundToInt(), 255, 216, 151)
        ringPaint.strokeWidth = 16f - 11f * progress
        canvas.drawCircle(x, y, radius, ringPaint)
        ringPaint.color = Color.argb((198f * energy).roundToInt(), 255, 243, 214)
        ringPaint.strokeWidth = 8f - 5f * progress
        canvas.drawCircle(x, y, radius, ringPaint)
    }

    private fun drawStrike(canvas: Canvas, x: Float, y: Float) {
        val age = clock - struckAt
        for (spark in sparks) {
            val t = age - spark.delay
            if (t <= 0f || t > spark.life) continue
            val k = t / spark.life
            val run = spark.speed * (1f - exp(-3.4f * t)) / 3.4f
            val left = spark.speed * exp(-3.4f * t)
            val sx = x + cos(spark.angle) * run
            val sy = y + sin(spark.angle) * run + 260f * t * t
            val colour = when {
                k < 0.25f -> 0xFFFFF3D6.toInt()
                k < 0.6f -> 0xFFFF8A2A.toInt()
                else -> 0xFFC2410C.toInt()
            }
            val alpha = ((1f - k).pow(2) * 255f).roundToInt()
            val width = spark.width * (1f - 0.5f * k)
            val tailX = sx - cos(spark.angle) * left * 0.028f
            val tailY = sy - (sin(spark.angle) * left + 520f * t) * 0.028f
            stroke.color = colour
            stroke.alpha = (alpha * 0.16f).roundToInt()
            stroke.strokeWidth = width * 4f
            canvas.drawLine(sx, sy, tailX, tailY, stroke)
            stroke.alpha = alpha
            stroke.strokeWidth = width
            canvas.drawLine(sx, sy, tailX, tailY, stroke)
        }
        stroke.alpha = 255
    }

    private fun drawRibbon(canvas: Canvas, top: Float, medalX: Float, medalY: Float, half: Float) {
        ribbon.draw(canvas)
        roundedRect(canvas, medalX - 13f, medalY - half * 0.93f - 19f, 26f, 7f, 2f, 0xFF222329.toInt())
    }

    private fun button(canvas: Canvas, y: Float, label: String, quiet: Boolean, alpha: Float) {
        val colour = if (quiet) 0xFF1C1B1F.toInt() else Color.WHITE
        roundedRect(canvas, 22f, y, 386f, 55f, 9f, withAlpha(colour, alpha * if (forged) 1f else 0.35f))
        if (quiet) drawIcon(canvas, "medal", 135f, y + 27.5f, 26f, withAlpha(Color.WHITE, alpha))
        text(canvas, label, if (quiet) 158f else 215f, y + 35f, 17f,
            withAlpha(if (quiet) Color.WHITE else Color.BLACK, alpha), Typeface.DEFAULT_BOLD,
            if (quiet) Paint.Align.LEFT else Paint.Align.CENTER)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val screenScale = min(width / 402f, height / 874f)
        val logicalWidth = width / screenScale
        val logicalHeight = height / screenScale
        val sheetScale = min(1f, min(logicalWidth / 430f, (logicalHeight - 81f) / 878f))
        val gridScale = min(1f, min(logicalWidth / 430f, (logicalHeight - 62f - 34f - 116f) / 684f))
        val x = (event.x / screenScale - (logicalWidth - 430f * sheetScale) / 2f) / sheetScale
        val y = (event.y / screenScale - 81f) / sheetScale
        val gridX = (event.x / screenScale - (logicalWidth - 430f * gridScale) / 2f) / gridScale
        val gridY = (event.y / screenScale - 62f) / gridScale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                manualUntil = SystemClock.uptimeMillis() + 3500L
                down = true
                downX = x
                downY = y
                lastX = x
                lastY = y
                downAt = event.eventTime
                pullStart = theta
                lastMoveAt = event.eventTime
                travelX = 0f
                travelY = 0f
                velocityX = 0f
                velocityY = 0f
                val sheetTop = 0f
                draggingSheet = present > 0.5f && y < sheetTop + 65f && x < 350f
                if (!draggingSheet && present > 0.5f && y in (sheetTop + 90f)..(sheetTop + 500f)) {
                    turnMode = TurnDrag
                    holding = !forged
                    holdTime = 0f
                    holdStart = charge
                    pressTarget = 1f
                }
                lightTargetX = ((x - 215f) / 180f).coerceIn(-1f, 1f)
                lightTargetY = ((292f - y) / 180f).coerceIn(-1f, 1f)
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!down) return false
                val elapsed = max(1L, event.eventTime - lastMoveAt) / 1000f
                val dx = x - lastX
                val dy = y - lastY
                velocityX = dx / elapsed
                velocityY = dy / elapsed
                travelX += dx
                travelY += dy
                if (draggingSheet) {
                    sheetDrag = max(0f, y - downY)
                } else if (forged && caught && !reducedMotion) {
                    val limit = pendulumLimit()
                    val angle = pullStart + travelX / pendulumLength()
                    theta = limit * tanh(angle / max(0.000001f, limit))
                } else if (forged && turnMode == TurnDrag) {
                    yaw += dx * 0.6f * Rad
                } else if (!forged && abs(travelX) + abs(travelY) > 8f) {
                    holding = false
                }
                lightTargetX = ((x - 215f) / 180f).coerceIn(-1f, 1f)
                lightTargetY = ((292f - y) / 180f).coerceIn(-1f, 1f)
                lastX = x
                lastY = y
                lastMoveAt = event.eventTime
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!down) return false
                val cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL
                val duration = (event.eventTime - downAt) / 1000f
                down = false
                holding = false
                pressTarget = 0f
                lightTargetX = 0f
                lightTargetY = 0f
                if (draggingSheet) {
                    if (!cancelled && (sheetDrag > 240f || velocityY > 700f)) presentTarget = 0f
                    sheetDrag = 0f
                    draggingSheet = false
                    return true
                }
                val sheetTop = 0f
                if (!cancelled && abs(travelX) + abs(travelY) < 8f) {
                    when {
                        present < 0.1f && gridX in 121f..207f && gridY in 283f..410f -> presentTarget = 1f
                        present > 0.5f && x > 350f && y < sheetTop + 68f -> presentTarget = 0f
                        present > 0.5f && x > 355f && y in (sheetTop + 75f)..(sheetTop + 135f) -> flip()
                        present > 0.5f && y in (sheetTop + 657f)..(sheetTop + 712f) -> toggleWear()
                        present > 0.5f && y in (sheetTop + 732f)..(sheetTop + 787f) -> {
                            yawTarget = nearestFace(yaw) + TwoPi
                            yawVelocity += 520f * Rad
                            turnMode = TurnSettle
                        }
                        present > 0.5f && forged && duration < 0.35f -> flip()
                    }
                } else if (!cancelled && forged) {
                    val vertical = abs(travelY) > abs(travelX) || abs(velocityY) > max(500f, abs(velocityX))
                    if (vertical && (abs(travelY) > 36f || abs(velocityY) > 500f)) {
                        changeDay(if ((if (abs(travelY) > 36f) travelY else velocityY) < 0f) 1 else -1)
                        turnMode = TurnSettle
                        yawTarget = nearestFace(yaw)
                    } else if (wearing) {
                        val frequency = sqrt(980f / pendulumLength())
                        val limit = pendulumLimit()
                        val remaining = frequency * sqrt(max(0f, limit * limit - theta * theta))
                        thetaVelocity = (velocityX / pendulumLength()).coerceIn(-remaining, remaining)
                    } else {
                        yawVelocity = (velocityX * 0.6f * Rad).coerceIn(-1800f * Rad, 1800f * Rad)
                        turnMode = if (abs(yawVelocity) > 1400f * Rad) TurnCoin else TurnCoast
                    }
                } else if (!forged && charge >= 0.85f) {
                    strike()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.contentDescription = if (present < 0.5f) {
            "Day ${Days[day]} medal, ${if (forged) "forged" else "ready to forge"}. Opens the achievement."
        } else {
            "Day ${Days[day]} medal, ${if (forged) "forged" else "not forged"}, ${if (wearing) "worn" else "not worn"}."
        }
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(WearAction, if (wearing) "Take it off" else "Wear your medal"))
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(DoneAction, "Done"))
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        manualUntil = SystemClock.uptimeMillis() + 3500L
        return when (action) {
            AccessibilityNodeInfo.ACTION_CLICK -> {
                if (present < 0.5f) presentTarget = 1f else if (forged) flip() else {
                    holding = true
                    charge = 1f
                    strike()
                }
                true
            }
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> { changeDay(1); true }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> { changeDay(-1); true }
            WearAction -> { toggleWear(); true }
            DoneAction -> { presentTarget = 0f; true }
            else -> super.performAccessibilityAction(action, arguments)
        }
    }

    private fun text(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, colour: Int, face: Typeface, align: Paint.Align) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.typeface = face
        paint.textSize = size
        paint.textAlign = align
        paint.color = colour
        paint.alpha = Color.alpha(colour)
        canvas.drawText(value, x, baseline, paint)
        paint.alpha = 255
    }

    private fun roundedRect(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, radius: Float, colour: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = colour
        canvas.drawRoundRect(RectF(x, y, x + width, y + height), radius, radius, paint)
    }

    private fun drawIcon(canvas: Canvas, name: String, centreX: Float, centreY: Float, size: Float, colour: Int) {
        val paths = icons.optJSONArray(name) ?: return
        canvas.save()
        canvas.translate(centreX - size / 2f, centreY - size / 2f)
        canvas.scale(size / 24f, size / 24f)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = colour
        for (i in 0 until paths.length()) {
            val path = PathParser.createPathFromPathData(paths.getString(i)) ?: continue
            path.fillType = Path.FillType.EVEN_ODD
            canvas.drawPath(path, paint)
        }
        canvas.restore()
    }

    private data class Spark(
        val angle: Float,
        val speed: Float,
        val life: Float,
        val width: Float,
        val delay: Float,
    )

    private companion object {
        const val Rad = (PI / 180.0).toFloat()
        const val TwoPi = (PI * 2.0).toFloat()
        const val Pitch = 0.035f
        const val MaxFrame = 0.034f
        const val MaxCatchUp = 1f
        const val SweepFrom = -1.15f
        const val SweepTo = 1.15f
        const val SweepDuration = 0.62f
        const val TurnRest = 0
        const val TurnDrag = 1
        const val TurnCoast = 2
        const val TurnSettle = 3
        const val TurnCoin = 4
        const val TurnWobble = 5
        const val WearAction = 0x01020001
        const val DoneAction = 0x01020002
        val Days = intArrayOf(1, 7, 30)
        val Copy = listOf(
            listOf("Starting is the hardest part. Let's get going.", "Day 1!"),
            listOf("A full week. The habit is taking shape.", "Day 7!"),
            listOf("Thirty days. This is who you are now.", "Day 30!"),
        )

        fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
        fun smooth(value: Float): Float {
            val t = value.coerceIn(0f, 1f)
            return if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f
        }
        fun easeOut(value: Float) = 1f - (1f - value.coerceIn(0f, 1f)).pow(3)
        fun moveTowards(value: Float, target: Float, distance: Float): Float {
            return if (value < target) min(target, value + distance) else max(target, value - distance)
        }
        fun nearestFace(angle: Float) = (angle / PI.toFloat()).roundToInt() * PI.toFloat()
        fun cubicSheet(value: Float): Float {
            val t = value.coerceIn(0f, 1f)
            return 1f - (1f - t).pow(3)
        }
        fun withAlpha(colour: Int, alpha: Float): Int {
            return Color.argb((Color.alpha(colour) * alpha).roundToInt().coerceIn(0, 255), Color.red(colour), Color.green(colour), Color.blue(colour))
        }
    }
}
