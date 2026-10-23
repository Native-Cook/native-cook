package com.ngocdevv.nativecook.blocks.cards.walletfirstdeposit

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow

/** Every animated scalar is derived from the loop time; no frame accumulates visual state. */
internal const val LOOP = 10f
internal const val FUNDED_AT = 6.2f
internal const val RETURN_AT = 8.4f
internal const val FILL_AT = 1.4f
internal const val LAND_AT = 3.22f
internal const val RESUME_AFTER = 2f

internal const val STAGGER_PILLS = 0.06f
internal const val STAGGER_ROWS = 0.05f
internal const val STAGGER_TILES = 0.04f
internal const val STAGGER_ORBS = 0.06f
internal const val STAGGER_DIGITS = 0.03f
internal const val RETURN_TILE_STAGGER = 0.03f

internal data class SpringSpec(val omega: Float, val zeta: Float)

internal val FlightSpring = SpringSpec(11.4f, 0.72f)
internal val UiSpring = SpringSpec(15.7f, 0.8f)
internal val PopSpring = SpringSpec(12.6f, 0.55f)

internal data class WalletFrame(
    val t: Float,
    val funded: Float,
    val fill: Float,
    val banner: Float,
    val copy: Float,
    val flight: Float,
    val lift: Float,
    val tilt: Float,
    val squeeze: Float,
    val sheen: Float,
    val dot: Float,
    val balance: Float,
    val reflow: Float,
    val reflow2: Float,
    val pill0: Float,
    val pill1: Float,
    val transactions: Float,
    val txHead: Float,
    val txCard: Float,
    val txRow0: Float,
    val txRow1: Float,
    val green: Float,
    val actionsOut: Float,
    val card0: Float,
    val card1: Float,
    val card2: Float,
    val card3: Float,
    val orb0: Float,
    val orb1: Float,
    val orb2: Float,
    val orb3: Float,
    val names: Float,
    val plot: Float,
    val plotA: Float,
    val line: Float,
    val forecast: Float,
    val spentValue: Float,
    val label: Float,
)

internal fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

internal fun raw(value: Float, from: Float, to: Float): Float = clamp01((value - from) / (to - from))

internal fun lerp(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

/** CSS cubic-bezier y for x. The fixed 18-step binary solver matches the native references. */
internal fun cubic(xValue: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val target = clamp01(xValue).toDouble()

    fun sample(t: Double, a: Double, b: Double): Double {
        val q = 1.0 - t
        return 3.0 * q * q * t * a + 3.0 * q * t * t * b + t * t * t
    }

    if (target == 0.0 || target == 1.0) return target.toFloat()
    var lo = 0.0
    var hi = 1.0
    repeat(18) {
        val mid = (lo + hi) / 2.0
        if (sample(mid, x1.toDouble(), x2.toDouble()) < target) lo = mid else hi = mid
    }
    val t = (lo + hi) / 2.0
    return sample(t, y1.toDouble(), y2.toDouble()).toFloat()
}

internal fun easeOut(value: Float): Float = cubic(value, 0.23f, 1f, 0.32f, 1f)

internal fun easeInOut(value: Float): Float = cubic(value, 0.77f, 0f, 0.175f, 1f)

internal fun between(value: Float, from: Float, to: Float, inOut: Boolean = false): Float {
    val p = raw(value, from, to)
    return if (inOut) easeInOut(p) else easeOut(p)
}

internal fun spring(seconds: Float, spec: SpringSpec): Float {
    if (seconds <= 0f) return 0f
    val omega = spec.omega.toDouble()
    val zeta = spec.zeta.toDouble()
    val damped = omega * sqrt(max(0.0001, 1.0 - zeta * zeta))
    val envelope = exp(-zeta * omega * seconds)
    val value = 1.0 - envelope * (cos(damped * seconds) + zeta * omega / damped * sin(damped * seconds))
    return if (envelope < 0.0001) 1f else value.toFloat()
}

private fun forward(t: Float): WalletFrame {
    val flightLinear = raw(t, 2.75f, 3.3f)
    fun pills(k: Int) = spring(t - 3.6f - k * STAGGER_PILLS, UiSpring)
    fun rows(k: Int) = easeOut(raw(t, 3.9f + k * STAGGER_ROWS, 4.35f + k * STAGGER_ROWS))
    fun tiles(k: Int) = easeInOut(raw(t, 3.35f + k * STAGGER_TILES, 4.3f + k * STAGGER_TILES))
    fun orbs(k: Int) = spring(t - 4.2f - k * STAGGER_ORBS, PopSpring)
    return WalletFrame(
        t = t,
        fill = easeInOut(raw(t, FILL_AT, 2.6f)),
        copy = easeInOut(raw(t, 2.4f, 2.7f)),
        lift = sin(Math.PI.toFloat() * flightLinear),
        squeeze = 0.04f * easeOut(raw(t, 2.6f, 2.75f)) * (1f - easeOut(raw(t, 2.75f, 2.95f))),
        banner = easeOut(raw(t, 2.9f, 3.4f)),
        sheen = raw(t, 3.45f, 3.95f),
        dot = easeOut(raw(t, 3.3f, 3.55f)),
        balance = raw(t, 3.3f, 4.3f),
        reflow = easeInOut(raw(t, 3.25f, 4f)),
        reflow2 = easeInOut(raw(t, 3.35f, 4.1f)),
        pill0 = pills(0),
        pill1 = pills(1),
        transactions = rows(1),
        txHead = rows(0),
        txCard = rows(1),
        txRow0 = rows(2),
        txRow1 = rows(3),
        green = easeInOut(raw(t, 4.5f, 4.9f)),
        actionsOut = easeOut(raw(t, 3.2f, 3.45f)),
        card0 = tiles(0),
        card1 = tiles(1),
        card2 = tiles(2),
        card3 = easeOut(raw(t, 4.1f, 4.6f)),
        orb0 = orbs(0),
        orb1 = orbs(1),
        orb2 = orbs(2),
        orb3 = orbs(3),
        names = easeOut(raw(t, 4.35f, 4.85f)),
        plot = easeInOut(raw(t, 4.6f, 5.4f)),
        plotA = easeOut(raw(t, 4.6f, 5f)),
        line = easeInOut(raw(t, 4.85f, 6f)),
        forecast = easeOut(raw(t, 5.3f, 6f)),
        spentValue = raw(t, 4.8f, 5.6f),
        label = easeInOut(raw(t, 5.3f, 5.7f)),
        funded = easeOut(raw(t, 3.3f, 3.55f)),
        flight = spring(t - 2.75f, FlightSpring),
        tilt = -8f * sin(Math.PI.toFloat() * flightLinear.pow(0.75f)),
    )
}

private fun back(t: Float): WalletFrame {
    fun out(from: Float, to: Float) = 1f - easeOut(raw(t, from, to))
    fun card(k: Int) = 1f - easeInOut(raw(t, 8.7f + k * RETURN_TILE_STAGGER, 9.35f + k * RETURN_TILE_STAGGER))
    val glide = 1f - easeInOut(raw(t, 9f, 9.6f))
    return WalletFrame(
        t = t,
        fill = if (t < 9.1f) 1f else 0f,
        copy = if (t < 9.1f) 1f else 0f,
        squeeze = 0f,
        sheen = 0f,
        dot = out(9.6f, 9.9f),
        flight = glide,
        lift = 0.45f * sin(Math.PI.toFloat() * raw(t, 9f, 9.6f)),
        tilt = 4f * sin(Math.PI.toFloat() * raw(t, 9f, 9.6f)),
        banner = 1f - easeOut(raw(t, 9.25f, 9.7f)),
        balance = 1f - easeInOut(raw(t, 8.5f, 9.1f)),
        reflow = 1f - easeInOut(raw(t, 8.75f, 9.35f)),
        reflow2 = 1f - easeInOut(raw(t, 8.8f, 9.4f)),
        pill0 = out(8.5f, 8.75f),
        pill1 = out(8.46f, 8.71f),
        transactions = out(8.5f, 8.75f),
        txHead = out(8.55f, 8.8f),
        txCard = out(8.5f, 8.75f),
        txRow0 = out(8.4f, 8.62f),
        txRow1 = out(8.43f, 8.65f),
        green = out(8.4f, 8.6f),
        actionsOut = 1f - easeOut(raw(t, 9.4f, 9.85f)),
        card0 = card(0),
        card1 = card(1),
        card2 = card(2),
        card3 = out(8.45f, 8.75f),
        orb0 = out(8.4f, 8.65f),
        orb1 = out(8.43f, 8.68f),
        orb2 = out(8.46f, 8.71f),
        orb3 = out(8.4f, 8.6f),
        names = out(8.4f, 8.6f),
        plot = 1f - easeInOut(raw(t, 8.6f, 9.2f)),
        plotA = 1f - easeOut(raw(t, 9f, 9.5f)),
        line = 1f - easeInOut(raw(t, 8.4f, 8.8f)),
        forecast = out(8.4f, 8.7f),
        spentValue = 1f - raw(t, 8.5f, 9f),
        label = 1f - easeInOut(raw(t, 8.6f, 8.9f)),
        funded = out(9.6f, 9.9f),
    )
}

internal fun frameAt(time: Float): WalletFrame {
    val phase = ((time % LOOP) + LOOP) % LOOP
    return if (phase < RETURN_AT) forward(phase) else back(phase)
}

/** A tiny state owner keeps the animation clock out of the paint helpers. */
internal class WalletFirstDepositMotion(initial: Float, autoPlay: Boolean) {
    var phase = initial
    var frame = frameAt(initial)
    var mode = if (autoPlay && initial < RETURN_AT) MotionMode.AUTO else MotionMode.IDLE
    var stopAt = FUNDED_AT
    var speed = 1f
    var thenReturn = false
    var byHand = false
    var holding = false
    var lastTouchNanos = Long.MIN_VALUE
    var lastNanos = -1L
    var reducedOpacity = 1f
    var reducedDirection = 0
    var reducedTarget = initial
    var bannerPress = 0f
    var cardPress = 0f
    var landed = false
    private var lifecycleActive = true

    private var bannerPressStart = 0f
    private var bannerPressTarget = 0f
    private var bannerPressStartedNanos = -1L
    private var bannerPressDurationNanos = 1L
    private var cardPressStart = 0f
    private var cardPressTarget = 0f
    private var cardPressStartedNanos = -1L
    private var cardPressDurationNanos = 1L

    fun tick(nowNanos: Long, autoPlay: Boolean, reduced: Boolean): Boolean {
        if (!lifecycleActive) {
            lastNanos = -1L
            return false
        }
        if (!autoPlay && mode == MotionMode.AUTO) mode = MotionMode.IDLE
        val dt = if (lastNanos < 0L || holding) 0f else ((nowNanos - lastNanos).coerceAtLeast(0L) / 1_000_000_000f)
        lastNanos = nowNanos
        val pressMoved = if (reduced) false else updatePress(nowNanos)
        var moved = pressMoved
        if (reduced) {
            if (reducedDirection != 0) {
                val next = reducedOpacity + reducedDirection * dt / 0.1f
                reducedOpacity = next.coerceIn(0f, 1f)
                if (reducedDirection < 0 && reducedOpacity <= 0f) {
                    phase = reducedTarget
                    reducedDirection = 1
                    reducedOpacity = 0f
                } else if (reducedDirection > 0 && reducedOpacity >= 1f) {
                    reducedDirection = 0
                    reducedOpacity = 1f
                }
                moved = true
            }
            frame = frameAt(phase)
            return moved
        }
        when (mode) {
            MotionMode.AUTO -> {
                if (!holding) {
                    phase = (phase + dt) % LOOP
                    moved = dt > 0f
                }
            }
            MotionMode.PLAY -> {
                val previous = phase
                phase += dt * speed
                if (byHand && !landed && previous < LAND_AT && phase >= LAND_AT) landed = true
                if (phase >= stopAt) {
                    phase = stopAt
                    if (thenReturn) {
                        thenReturn = false
                        phase = RETURN_AT
                        stopAt = LOOP
                        speed = 1f
                    } else {
                        if (phase >= LOOP) phase = 0f
                        mode = MotionMode.IDLE
                    }
                }
                moved = dt > 0f
            }
            MotionMode.IDLE -> {
                val idleFor = if (lastTouchNanos == Long.MIN_VALUE) Long.MAX_VALUE else nowNanos - lastTouchNanos
                if (autoPlay && !holding && idleFor >= 2_000_000_000L) {
                    mode = MotionMode.AUTO
                }
            }
        }
        frame = frameAt(phase)
        return moved || mode == MotionMode.AUTO || bannerPress > 0f || cardPress > 0f
    }

    fun resetClock() {
        lastNanos = -1L
    }

    /** Whether the stage should keep a frame clock alive. Static stages can suspend completely. */
    fun shouldTick(autoPlay: Boolean, reduced: Boolean): Boolean {
        if (!lifecycleActive) return false
        if (reduced) return reducedDirection != 0
        // An AUTO mode left behind after autoPlay is turned off gets one tick to normalize to IDLE.
        if (!autoPlay && mode == MotionMode.AUTO) return true
        return mode == MotionMode.AUTO || mode == MotionMode.PLAY ||
            bannerPressStartedNanos >= 0L || cardPressStartedNanos >= 0L
    }

    /** How long an idle autoplay controller may sleep before it must enter AUTO. */
    fun nextWakeDelayNanos(nowNanos: Long, autoPlay: Boolean, reduced: Boolean): Long? {
        if (!lifecycleActive || reduced || !autoPlay || holding || mode != MotionMode.IDLE) return null
        if (lastTouchNanos == Long.MIN_VALUE) return 0L
        return (2_000_000_000L - (nowNanos - lastTouchNanos)).coerceAtLeast(0L)
    }

    fun setLifecycleActive(active: Boolean) {
        if (!active && holding) touchCancel(System.nanoTime(), reduced = false)
        lifecycleActive = active
        resetClock()
    }

    fun touchDown(nowNanos: Long, target: Int, reduced: Boolean) {
        holding = true
        if (mode == MotionMode.AUTO) mode = MotionMode.IDLE
        lastTouchNanos = nowNanos
        if (!reduced) {
            if (target == 1) setPress(1, 1f, nowNanos, 120_000_000L)
            if (target == 2) setPress(2, 1f, nowNanos, 120_000_000L)
        }
    }

    fun touchUp(nowNanos: Long, target: Int, reduced: Boolean) {
        holding = false
        lastTouchNanos = nowNanos
        if (!reduced) releasePress(nowNanos)
        if (reduced) {
            if (target != 0) {
                reducedTarget = if (target == 1) FUNDED_AT else 0f
                reducedDirection = -1
                reducedOpacity = 1f
            }
            return
        }
        when (target) {
            1 -> {
                phase = if (phase >= 9.7f) FILL_AT else max(phase, FILL_AT)
                stopAt = FUNDED_AT
                speed = 1f
                thenReturn = false
                byHand = true
                landed = false
                mode = MotionMode.PLAY
            }
            2 -> {
                if (phase < FUNDED_AT) {
                    stopAt = FUNDED_AT
                    speed = 2.5f
                    thenReturn = true
                } else {
                    phase = max(phase, RETURN_AT)
                    stopAt = LOOP
                    speed = 1f
                    thenReturn = false
                }
                byHand = false
                landed = false
                mode = MotionMode.PLAY
            }
            else -> if (mode == MotionMode.AUTO) {
                when {
                    phase > FILL_AT && phase < FUNDED_AT -> {
                        stopAt = FUNDED_AT
                        speed = 1f
                        mode = MotionMode.PLAY
                    }
                    phase > RETURN_AT -> {
                        stopAt = LOOP
                        speed = 1f
                        mode = MotionMode.PLAY
                    }
                    else -> mode = MotionMode.IDLE
                }
                thenReturn = false
                byHand = false
            }
        }
    }

    fun touchCancel(nowNanos: Long, reduced: Boolean) {
        holding = false
        lastTouchNanos = nowNanos
        if (!reduced) releasePress(nowNanos)
    }

    private fun setPress(which: Int, target: Float, nowNanos: Long, durationNanos: Long) {
        updatePress(nowNanos)
        if (which == 1) {
            bannerPressStart = bannerPress
            bannerPressTarget = target
            bannerPressStartedNanos = nowNanos
            bannerPressDurationNanos = durationNanos
        } else {
            cardPressStart = cardPress
            cardPressTarget = target
            cardPressStartedNanos = nowNanos
            cardPressDurationNanos = durationNanos
        }
    }

    private fun releasePress(nowNanos: Long) {
        setPress(1, 0f, nowNanos, 100_000_000L)
        setPress(2, 0f, nowNanos, 100_000_000L)
    }

    private fun updatePress(nowNanos: Long): Boolean {
        fun value(start: Float, target: Float, started: Long, duration: Long): Float {
            if (started < 0L) return target
            val p = ((nowNanos - started).coerceIn(0L, duration)).toFloat() / duration
            return lerp(start, target, easeOut(p))
        }
        val nextBanner = value(bannerPressStart, bannerPressTarget, bannerPressStartedNanos, bannerPressDurationNanos)
        val nextCard = value(cardPressStart, cardPressTarget, cardPressStartedNanos, cardPressDurationNanos)
        val moved = abs(nextBanner - bannerPress) > 0.0001f || abs(nextCard - cardPress) > 0.0001f
        bannerPress = nextBanner
        cardPress = nextCard
        if (bannerPressStartedNanos >= 0L && nowNanos - bannerPressStartedNanos >= bannerPressDurationNanos) bannerPressStartedNanos = -1L
        if (cardPressStartedNanos >= 0L && nowNanos - cardPressStartedNanos >= cardPressDurationNanos) cardPressStartedNanos = -1L
        return moved
    }
}

internal enum class MotionMode { IDLE, PLAY, AUTO }
