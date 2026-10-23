package com.ngocdevv.nativecook.blocks.inputs.morphotpinput

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal const val LENGTH = 6

// ── Cells (dp) ───────────────────────────────────────────────────────────────────────────────────
internal const val CELL_MAX_W = 48f
internal const val CELL_H = 64f
internal const val CELL_GAP = 9f
internal const val CELL_RADIUS = 15f
// Hairline borders are drawn half a stroke inside the cell so they land on whole device pixels.
internal const val BORDER = 1f
// The focused cell lights its border mint, and a mint copy of that outline collapses into this
// caret, which glows softly and breathes. A cell that loses focus lets its caret fade and shrink
// in CARET_LEAVE ms; it never grows back into an outline.
internal const val CARET_W = 2.5f
internal const val CARET_H = 28f
internal const val CARET_BREATH = 1.1f // s per breath
internal const val CARET_GLOW = 6f // blur radius of the caret's glow, dp
// Critically damped so the caret settles without a wobble; the outline only lights faintly on its
// way, so each keystroke reads as a quiet hand-off rather than a flash.
private const val FOCUS_MS = 420.0
private const val FOCUS_RATIO = 1.0
private const val FOCUS_IN = 180.0 // ms for the mint outline to light up
private const val CARET_LEAVE = 200.0 // ms

// ── Digits: a digit settles into its cell, rising ROLL_FROM dp while it fades in out of the faintest
// blur, on a soft ease-out with no overshoot, so typing feels light; cleared, it sinks ROLL_OUT dp
// and fades. Every digit stays clipped to its own cell ───────────────────────────────────────────
internal const val DIGIT_SIZE = 30f
internal const val ROLL_FROM = 10f
internal const val ROLL_OUT = 10f
internal const val ROLL_BLUR = 1.2f // pixels
private const val ROLL_IN_MS = 340.0
private const val ROLL_OUT_MS = 220.0
private const val CHECK_DELAY = 0.28f // s after the sixth digit before the code is checked

// ── Wrong code: a damped shake, red, then the digits drop out ────────────────────────────────────
private const val SHAKE_AMPLITUDE = 16f // dp
private const val SHAKE_FREQUENCY = 6.5f // Hz
private const val SHAKE_DECAY = 0.16f // s, time constant of the envelope
private const val SHAKE_LENGTH = 0.75f // s
private const val ERROR_HOLD = 0.75f // s red before the code clears
private const val CLEAR_STAGGER = 0.03f // s between digits dropping out, last first

// ── Right code: merge into a pill, then a circle, then the check and its ring ─────────────────────
private const val MERGE_TIME = 0.46f
private const val CIRCLE_TIME = 0.38f
internal const val MERGED_SPACING = 0.62f // × the cell width between the merged cells' centres
private const val CHECK_AT = MERGE_TIME + CIRCLE_TIME - 0.04f
private const val CHECK_TIME = 0.38f
// The burst: a crisp ring grows from the circle to RING_REACH × its radius, a second, fainter one
// follows RING_LAG of the way behind, and SPARKS short dashes shoot out. Everything stays inside
// BURST_MAX dp of the centre so it never reaches the status line below.
private const val BURST_TIME = 0.62f
internal const val RING_REACH = 1.8f
internal const val RING_LAG = 0.14f
internal const val SPARKS = 12
internal const val BURST_MAX = 64f
private const val UNMERGE_TIME = 0.7f

// autoPlay (seconds): a wrong code, the shake, the right code, the check, back to empty.
private const val LOOP = 10f
private const val WRONG_AT = 0.7f
private const val RIGHT_AT = 4.0f
private const val TYPE_STEP = 0.26f // s between typed digits
private const val RESET_AT = 8.6f
private const val USER_HOLD = 4f // s a touch pauses the script (and longer while the keyboard is up)
internal const val RESEND_SECONDS = 30

internal val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

internal fun clamp01(x: Float) = x.coerceIn(0f, 1f)

/** 1 until [from], then down to 0 at 1 on a smoothstep: a fade that holds, then goes. */
internal fun fadeAfter(t: Float, from: Float): Float {
    val k = clamp01((t - from) / (1f - from))
    return 1f - k * k * (3f - 2f * k)
}

internal enum class Phase { Idle, Error, Success }

/** Reanimated's default curve for `withTiming`. */
private fun quadInOut(t: Double) = if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2

/**
 * A number moved by springs and timings the way Reanimated moves a shared value: a delayed
 * animation lets the one before it run on during the delay, a sequence runs its steps one after
 * another, and a spring restarted towards the same target carries on.
 */
internal class Driven(initial: Float) {
    var value = initial.toDouble()
        private set
    val float get() = value.toFloat()

    private sealed class Plan {
        class Spring(val target: Double, val durationMs: Double, val ratio: Double) : Plan()
        class Timing(val target: Double, val duration: Double, val ease: (Double) -> Double) : Plan()
    }

    private sealed class Running {
        class Spring(val spring: Motion) : Running()
        class Timing(val target: Double, val from: Double, var elapsed: Double, val duration: Double, val ease: (Double) -> Double) : Running()
        class Delay(var remaining: Double, val next: Plan, var previous: Running?) : Running()
    }

    private var running: Running? = null
    private val queue = ArrayDeque<Plan>()

    fun set(next: Float) {
        value = next.toDouble()
        running = null
        queue.clear()
    }

    fun spring(target: Float, durationMs: Double, ratio: Double) {
        running = start(Plan.Spring(target.toDouble(), durationMs, ratio), running)
        queue.clear()
    }

    fun timing(target: Float, durationMs: Double, ease: (Double) -> Double = ::quadInOut) {
        running = start(Plan.Timing(target.toDouble(), durationMs / 1000.0, ease), running)
        queue.clear()
    }

    /** Timings run one after another, each from where the last one stopped. */
    fun sequence(vararg steps: Triple<Float, Double, (Double) -> Double>) {
        val plans = steps.map { Plan.Timing(it.first.toDouble(), it.second / 1000.0, it.third) }
        running = start(plans.first(), running)
        queue.clear()
        queue.addAll(plans.drop(1))
    }

    /** A timing that starts after `delayMs`; until then whatever moved the value keeps moving it. */
    fun delayedTiming(delayMs: Double, target: Float, durationMs: Double, ease: (Double) -> Double = ::quadInOut) {
        running = Running.Delay(delayMs / 1000.0, Plan.Timing(target.toDouble(), durationMs / 1000.0, ease), running)
        queue.clear()
    }

    private fun start(plan: Plan, previous: Running?): Running = when (plan) {
        is Plan.Spring -> {
            val old = (previous as? Running.Spring)?.spring
            if (old != null && old.target == plan.target && old.durationMs == plan.durationMs && old.ratio == plan.ratio) {
                previous as Running
            } else {
                Running.Spring(Motion(value, plan.target, old?.velocity ?: 0.0, plan.durationMs, plan.ratio))
            }
        }
        is Plan.Timing -> {
            val old = previous as? Running.Timing
            if (old != null && old.target == plan.target) {
                Running.Timing(plan.target, old.from, old.elapsed, plan.duration, plan.ease)
            } else {
                Running.Timing(plan.target, value, 0.0, plan.duration, plan.ease)
            }
        }
    }

    fun step(dt: Double) {
        val current = running ?: return
        val (next, finished) = advance(current, dt)
        running = if (finished) queue.removeFirstOrNull()?.let { start(it, null) } else next
    }

    private fun advance(r: Running, dt: Double): Pair<Running?, Boolean> = when (r) {
        is Running.Spring -> {
            val done = r.spring.step(dt)
            value = r.spring.value
            r to done
        }
        is Running.Timing -> {
            r.elapsed += dt
            if (r.elapsed >= r.duration || r.duration <= 0) {
                value = r.target
                null to true
            } else {
                value = r.from + (r.target - r.from) * r.ease(r.elapsed / r.duration)
                r to false
            }
        }
        is Running.Delay -> {
            r.remaining -= dt
            if (r.remaining <= 0) {
                start(r.next, r.previous) to false
            } else {
                r.previous?.let { p ->
                    val (moved, done) = advance(p, dt)
                    r.previous = if (done) null else moved
                }
                r to false
            }
        }
    }

    /** Reanimated's `withSpring({ duration, dampingRatio })`, stepped along its exact solution. */
    class Motion(start: Double, val target: Double, speed: Double, val durationMs: Double, val ratio: Double) {
        var value = start
            private set
        var velocity: Double
            private set
        private val omega0: Double
        private val omega1: Double
        private val stiffness: Double
        private val initialEnergy: Double

        init {
            var v = speed
            if ((target > start && v < 0) || (target < start && v > 0)) v = 0.0
            velocity = v
            val x0 = start - target
            val zeta = ratio
            val settling = durationMs / 1000.0 * 1.5
            fun energy(x: Double, v: Double, k: Double) = 0.5 * k * x * x + 0.5 * v * v
            fun difference(k: Double): Double {
                val w = sqrt(k) * zeta
                val xt = (x0 + (v + x0 * w) * settling) * exp(-w * settling)
                val vt = (x0 + (v + x0 * w) * settling) * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
                return energy(xt, vt, k) / energy(x0, v, k) - THRESHOLD
            }
            // Bisection, as Reanimated solves it (its mass of 4 only scales the stiffness it finds).
            var low = Math.ulp(1.0)
            var high = 2e3
            val rising = difference(high) >= difference(low)
            var k = (low + high) / 2
            var left = 100
            while (abs(difference(k)) > THRESHOLD * 1e-3 && left > 0) {
                left -= 1
                if ((difference(k) < 0) == rising) low = k else high = k
                k = (low + high) / 2
            }
            if (!k.isFinite()) k = 170.0
            stiffness = k
            omega0 = sqrt(k)
            omega1 = if (zeta < 1) omega0 * sqrt(1 - zeta * zeta) else 0.0
            // Reanimated measures the starting energy with no speed of its own (none is configured).
            initialEnergy = energy(x0, 0.0, k)
        }

        fun step(dt: Double): Boolean {
            if (initialEnergy == 0.0) {
                value = target
                return true
            }
            val x0 = value - target
            val v0 = velocity
            val zeta = ratio
            if (zeta < 1) {
                val sin1 = sin(omega1 * dt)
                val cos1 = cos(omega1 * dt)
                val envelope = exp(-zeta * omega0 * dt)
                val frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
                value = target + frag
                velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
            } else {
                val envelope = exp(-omega0 * dt)
                value = target + envelope * (x0 + (v0 + omega0 * x0) * dt)
                velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
            }
            val now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
            if (now / initialEnergy <= THRESHOLD) {
                value = target
                velocity = 0.0
                return true
            }
            return false
        }

        private companion object {
            const val THRESHOLD = 6e-9
        }
    }
}

/**
 * One cell's moving parts: its focus outline collapsing into the caret ([focus]), the mint outline
 * lighting up ([lit]), the caret fading once the cell lets it go ([leave]), its fill, and its digit
 * rolling in or out ([roll]: 0 below, 1 in place, 2 dropped out).
 */
internal class Cell {
    val focus = Driven(0f)
    val lit = Driven(0f)
    val leave = Driven(1f)
    val fill = Driven(0f)
    val roll = Driven(2f)
    /** The last digit shown, kept while it drops out of an emptied cell. */
    var glyph = " "
    var active = false
    var filled = false
}

/** The controller: typing, checking, the error and success sequences and the script. */
internal class OtpEngine(source: String) {
    /** The six digits that can actually match, whatever was passed in. */
    val code = (source.filter { it.isDigit() } + "000000").take(LENGTH)

    /** Bumped every frame; the scene reads it, so it is laid out and drawn again each time. */
    val frame = mutableLongStateOf(0L)
    /** Bumped to ask the hidden field to take (positive) or drop (negative) the keyboard. */
    val focusRequest = mutableIntStateOf(0)
    private var requests = 0

    var reduced = false
    var onVerified: (() -> Unit)? = null
    var onTap: () -> Unit = {}
    var onError: () -> Unit = {}
    var onSuccess: () -> Unit = {}

    var now = 0.0
        private set
    var value = ""
        private set
    val shown = Array(LENGTH) { "" }
    var phase = Phase.Idle
        private set
    var resendFrom = 0.0
        private set
    var shake = 0f
        private set
    private var shakeAt = -1.0

    val merge = Driven(0f)
    val check = Driven(0f)
    val burst = Driven(0f)
    val errorAmt = Driven(0f)
    // How much each status line shows. Each fades on its own, so going from idle straight to
    // success (or back) never passes through the error line.
    val idleShown = Driven(1f)
    val errorShown = Driven(0f)
    val successShown = Driven(0f)
    val cells = List(LENGTH) { Cell() }

    private var playing = false
    // The digits in the row were typed by the demo, not by the user.
    private var byScript = false
    // The hidden field holds focus, and the keyboard is on screen (back can hide it and keep focus).
    var fieldFocused = false
    var imeVisible = false
    private val timers = ArrayList<Pair<Double, () -> Unit>>()
    private val script = ArrayList<Pair<Double, () -> Unit>>()

    fun step(dt: Double, autoPlay: Boolean) {
        if (autoPlay != playing) {
            playing = autoPlay
            if (autoPlay) runScript() else script.clear()
        }
        now += dt
        // A shake asked for between frames starts on this one.
        if (shakeAt == -2.0) shakeAt = now
        if (shakeAt >= 0) {
            val k = (now - shakeAt).toFloat()
            if (k > SHAKE_LENGTH) {
                shakeAt = -1.0
                shake = 0f
            } else {
                shake = SHAKE_AMPLITUDE * exp(-k / SHAKE_DECAY) * sin(2f * PI.toFloat() * SHAKE_FREQUENCY * k)
            }
        }
        for (v in listOf(merge, check, burst, errorAmt, idleShown, errorShown, successShown)) v.step(dt)
        for (c in cells) {
            c.focus.step(dt)
            c.lit.step(dt)
            c.leave.step(dt)
            c.fill.step(dt)
            c.roll.step(dt)
        }
        // Timers fire after the values moved, so what they start is first drawn at rest.
        fire(timers)
        fire(script)
        frame.longValue += 1
    }

    private fun fire(list: ArrayList<Pair<Double, () -> Unit>>) {
        while (true) {
            val next = list.minByOrNull { it.first } ?: return
            if (next.first > now) return
            list.remove(next)
            next.second()
        }
    }

    private fun later(seconds: Float, run: () -> Unit) {
        timers.add(now + seconds to run)
    }

    /** Mirrors the cells to the code and phase, the way the React tree re-renders. */
    private fun sync() {
        val activeIndex = min(value.length, LENGTH - 1)
        cells.forEachIndexed { i, cell ->
            val digit = shown[i]
            val filled = digit.isNotEmpty()
            val active = phase == Phase.Idle && i == activeIndex && value.length < LENGTH
            if (active != cell.active) {
                cell.active = active
                if (active) {
                    // Always start from the full outline, so focus arrives the same way every time.
                    cell.focus.set(0f)
                    cell.leave.set(0f)
                    cell.lit.timing(1f, if (reduced) 160.0 else FOCUS_IN)
                    if (reduced) cell.focus.set(1f) else cell.focus.spring(1f, FOCUS_MS, FOCUS_RATIO)
                } else {
                    cell.lit.timing(0f, CARET_LEAVE)
                    cell.leave.timing(1f, CARET_LEAVE) { EaseOut.transform(it.toFloat()).toDouble() }
                }
            }
            if (filled && digit != cell.glyph) cell.glyph = digit
            if (filled != cell.filled) {
                cell.filled = filled
                cell.fill.timing(if (filled) 1f else 0f, 180.0)
                if (filled) {
                    cell.roll.set(0f)
                    cell.roll.timing(1f, if (reduced) 200.0 else ROLL_IN_MS) { EaseOut.transform(it.toFloat()).toDouble() }
                } else {
                    cell.roll.timing(2f, ROLL_OUT_MS) { EaseOut.transform(it.toFloat()).toDouble() }
                }
            }
        }
    }

    private fun setPhase(p: Phase) {
        phase = p
        idleShown.timing(if (p == Phase.Idle) 1f else 0f, 220.0)
        errorShown.timing(if (p == Phase.Error) 1f else 0f, 220.0)
        successShown.timing(if (p == Phase.Success) 1f else 0f, 220.0)
        sync()
    }

    private fun fail(user: Boolean) {
        setPhase(Phase.Error)
        errorAmt.timing(1f, 120.0)
        if (!reduced) shakeAt = -2.0
        if (user) onError()
        later(ERROR_HOLD) {
            // Digits drop out last to first while the red drains away.
            value = ""
            errorAmt.timing(0f, 320.0)
            sync()
            for (i in LENGTH - 1 downTo 0) {
                later((LENGTH - 1 - i) * CLEAR_STAGGER) {
                    shown[i] = ""
                    sync()
                }
            }
            later(LENGTH * CLEAR_STAGGER + 0.12f) { setPhase(Phase.Idle) }
        }
    }

    private fun succeed(user: Boolean) {
        setPhase(Phase.Success)
        requests += 1
        focusRequest.intValue = -requests
        val inOut: (Double) -> Double = { EaseInOut.transform(it.toFloat()).toDouble() }
        if (reduced) {
            merge.timing(2f, 320.0)
            check.delayedTiming(240.0, 1f, 260.0)
        } else {
            merge.sequence(Triple(1f, MERGE_TIME * 1000.0, inOut), Triple(2f, CIRCLE_TIME * 1000.0, inOut))
            check.delayedTiming(CHECK_AT * 1000.0, 1f, CHECK_TIME * 1000.0) { EaseOut.transform(it.toFloat()).toDouble() }
            burst.set(0f)
            burst.delayedTiming(CHECK_AT * 1000.0, 1f, BURST_TIME * 1000.0) { it }
        }
        // The success haptic lands with the check, as the ring leaves.
        later(if (reduced) 0.24f else CHECK_AT) {
            if (user) onSuccess()
            onVerified?.invoke()
        }
    }

    private fun verify(user: Boolean) = if (value == code) succeed(user) else fail(user)

    /** Takes the field's text (typed, pasted or filled from a message) as the new code. */
    private fun enter(text: String, user: Boolean) {
        if (phase != Phase.Idle) return
        val next = text.filter { it in '0'..'9' }.take(LENGTH)
        if (next == value) return
        value = next
        byScript = !user
        for (i in 0 until LENGTH) shown[i] = next.getOrNull(i)?.toString() ?: ""
        sync()
        if (next.length == LENGTH) later(CHECK_DELAY) { verify(user) }
    }

    /** Back to an empty row: the circle opens back out into six cells. */
    private fun reset() {
        timers.clear()
        value = ""
        shown.fill("")
        errorAmt.timing(0f, 200.0)
        check.timing(0f, 160.0)
        burst.set(0f)
        merge.timing(0f, if (reduced) 300.0 else UNMERGE_TIME * 1000.0) { EaseInOut.transform(it.toFloat()).toDouble() }
        resendFrom = now
        setPhase(Phase.Idle)
    }

    private fun typeCode(digits: String, at: Float) {
        digits.forEachIndexed { i, digit -> script.add(now + at + i * TYPE_STEP to { enter(value + digit, false) }) }
    }

    private fun runScript() {
        script.clear()
        // A wrong code that differs from the right one in two places.
        val wrong = if (code.length == LENGTH) code.substring(0, 3) + code[4] + code[3] + code[5] else code
        typeCode(if (wrong == code) code.reversed() else wrong, WRONG_AT)
        typeCode(code, RIGHT_AT)
        script.add(now + RESET_AT to { reset() })
        script.add(now + LOOP to { runScript() })
    }

    /** A touch pauses the script; it returns whether it cleared a code the demo was half way through. */
    private fun touched(): Boolean {
        if (!playing) return false
        script.clear()
        // The demo's half-typed code is not the user's: clear it so their own digits start fresh.
        var cleared = false
        if (byScript && phase == Phase.Idle && value.isNotEmpty()) {
            timers.clear()
            value = ""
            byScript = false
            shown.fill("")
            sync()
            cleared = true
        }
        script.add(now + USER_HOLD to { resume() })
        return cleared
    }

    /**
     * The demo waits while the keyboard is up, so someone reading a code off another screen keeps the
     * digits they have typed so far.
     */
    private fun resume() {
        if (fieldFocused && imeVisible) {
            script.add(now + USER_HOLD to { resume() })
            return
        }
        reset()
        script.add(now + UNMERGE_TIME + 0.3f to { runScript() })
    }

    /** The hidden field's text changed. */
    fun type(text: String) {
        val before = value
        // If the touch cleared the demo's digits, only what the user just typed counts.
        enter(if (touched()) text.drop(before.length) else text, true)
    }

    fun tapRow() {
        touched()
        if (phase == Phase.Success) {
            reset()
            return
        }
        onTap()
        requests += 1
        focusRequest.intValue = requests
    }

    fun resend() {
        touched()
        onTap()
        resendFrom = now
    }

    /** One cell's box at merge `m`: [centre x from the row's centre, width, corner radius]. */
    fun box(index: Int, m: Float, cellW: Float): FloatArray {
        val p1 = clamp01(m)
        val p2 = clamp01(m - 1f)
        fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
        val spacing = mix(mix(cellW + CELL_GAP, cellW * MERGED_SPACING, p1), 0f, p2)
        // Merged cells are stadiums wide enough to overlap their neighbours, so the union is one pill.
        val width = mix(mix(cellW, cellW * MERGED_SPACING + CELL_H, p1), CELL_H, p2)
        val radius = mix(CELL_RADIUS, CELL_H / 2f, p1)
        return floatArrayOf((index - (LENGTH - 1) / 2f) * spacing, width, radius)
    }

    /** Seconds left before the code can be sent again. */
    /**
     * A fading line's opacity, read inside a graphicsLayer block: reading `frame` there makes the layer
     * run again every frame (the values themselves are plain fields, which Compose does not watch).
     */
    fun shown(value: Driven): Float {
        frame.longValue
        return value.float
    }

    fun resendLeft(): Int = maxOf(0, RESEND_SECONDS - kotlin.math.floor(maxOf(0.0, now - resendFrom)).toInt())
}
