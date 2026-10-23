package com.ngocdevv.nativecook.blocks.inputs.patternlockinput

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// A "Z": along the top, down the diagonal, along the bottom.
internal val PatternZ = listOf(0, 1, 2, 4, 6, 7, 8)
// The wrong guess autoPlay draws first: an "L".
private val PatternL = listOf(0, 3, 6, 7, 8)
private const val MIN_DOTS = 4

// Grid (dp): dots SPACING_SHARE of the width apart, at most SPACING_MAX; the grid's centre at
// GRID_AT of the height.
private const val SPACING_SHARE = 0.27f
private const val SPACING_MAX = 110f
private const val GRID_AT = 0.53f
// A finger within HIT of a dot's centre (share of the spacing) picks it up.
private const val HIT = 0.34f

// The open end follows the finger on a spring (stiffness, damping ratio), so it lags and
// stretches; a dot it snaps onto springs from where the finger was to its centre, overshooting.
private const val TAIL_STIFFNESS = 650f
private const val TAIL_DAMPING = 0.72f
private const val SNAP_STIFFNESS = 420f
private const val SNAP_DAMPING = 0.42f

// A wrong pattern: the grid shakes sideways like a struck spring, SHAKE dp at SHAKE_HZ, dying away
// at SHAKE_DECAY (1/s) over SHAKE_TIME s; the red path holds for WRONG_HOLD s and fades over FADE_TIME s.
private const val SHAKE = 18f
private const val SHAKE_HZ = 6f
private const val SHAKE_DECAY = 4f
private const val SHAKE_TIME = 1.1f
private const val WRONG_HOLD = 0.65f
private const val FADE_TIME = 0.3f
// The right pattern: the path pulses, the dots collapse into the centre, the padlock forms and
// springs open with a check; RESET_AFTER s after release the grid comes back.
private const val PULSE_IN = 0.14f
private const val PULSE_OUT = 0.34f
private const val COLLAPSE_AT = 0.38f
private const val LOCK_AT = 0.76f
private const val OPEN_AT = 1.38f
private const val CHECK_AT = 1.48f
private const val CHECK_TIME = 0.34f
private const val RESET_AFTER = 3f
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
internal fun easeOut(t: Float) = EaseOut.transform(t)

// autoPlay loop (seconds): the "L" is drawn and rejected, then the "Z" unlocks and the grid comes
// back, at rest, before the loop starts over. Each stroke between dots takes SEGMENT s.
private const val LOOP = 10f
private const val WRONG_AT = 0.6f
private const val RIGHT_AT = 3.7f
private const val SEGMENT = 0.27f
private const val LIFT_AFTER = 0.12f
// A touch pauses the loop this long; then it starts over from the empty grid.
private const val USER_HOLD = 4f

// NaN comes out as `lo`: kotlin.math.min/max pass NaN through, and a NaN reaching roundToInt throws.
internal fun clamp(x: Float, lo: Float, hi: Float) = if (x.isNaN()) lo else min(hi, max(lo, x))

/** Reanimated's default timing curve, ease in and out (quadratic). */
internal fun inOutQuad(t: Float) = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2

private fun passed(before: Float, now: Float, at: Float) =
    if (now >= before) before < at && now >= at else at > before || at <= now

/** The dot halfway between two dots, if the line between them crosses one, else −1. */
private fun between(a: Int, b: Int): Int {
    val ra = a / 3
    val ca = a % 3
    val rb = b / 3
    val cb = b % 3
    if ((ra + rb) % 2 != 0 || (ca + cb) % 2 != 0) return -1
    return (ra + rb) / 2 * 3 + (ca + cb) / 2
}

/** A spring step for a 2D point (semi-implicit Euler), in place on [s] = x, y, vx, vy. */
private fun springStep(s: FloatArray, tx: Float, ty: Float, k: Float, ratio: Float, dt: Float) {
    val c = 2f * sqrt(k) * ratio
    s[2] += (k * (tx - s[0]) - c * s[2]) * dt
    s[3] += (k * (ty - s[1]) - c * s[3]) * dt
    s[0] += s[2] * dt
    s[1] += s[3] * dt
}

/** Where the grid sits on this screen (dp). */
internal class PatternGrid(val width: Float, val height: Float) {
    val spacing = min(SPACING_MAX, width * SPACING_SHARE)
    val cx = width / 2f
    val cy = height * GRID_AT
    val xs = FloatArray(9) { cx + (it % 3 - 1) * spacing }
    val ys = FloatArray(9) { cy + (it / 3 - 1) * spacing }

    /** Where the virtual finger is at [u] (0…1) along a pattern: each stroke eased into its dot. */
    fun along(pattern: List<Int>, u: Float): Pair<Float, Float> {
        // A pattern of fewer than two dots, or one off the grid, has no strokes to follow.
        if (pattern.size < 2 || pattern.any { it !in 0..8 }) return cx to cy
        val strokes = pattern.size - 1
        val at = clamp(u, 0f, 1f) * strokes
        val k = min(strokes - 1, floor(at).toInt())
        val f = at - k
        val eased = 0.45f * f + 0.55f * f * f * (3 - 2 * f)
        val a = pattern[k]
        val b = pattern[k + 1]
        return (xs[a] + (xs[b] - xs[a]) * eased) to (ys[a] + (ys[b] - ys[a]) * eased)
    }
}

/**
 * A number driven like a Reanimated shared value: set outright, or animated by timings and
 * duration springs, each optionally delayed. As with `withDelay`, whatever was moving the value
 * keeps moving it until the delay is up; [Entry.done] runs when an animation finishes unreplaced.
 */
internal class PatternValue(var value: Float) {
    sealed interface Step
    class Timing(val to: Float, val seconds: Float, val curve: (Float) -> Float = ::inOutQuad) : Step
    class Spring(val to: Float, val ms: Float, val ratio: Float) : Step

    private class Entry(val start: Float, val steps: ArrayDeque<Step>, val done: (() -> Unit)?)

    private var current: Entry? = null
    private val pending = ArrayList<Entry>()
    private var mode = 0 // 0 still, 1 spring, 2 timing
    private var to = 0f
    private var velocity = 0f
    private var zeta = 0f
    private var omega0 = 0f
    private var omega1 = 0f
    private var stiffness = 0f
    private var initialEnergy = 0f
    private var from = 0f
    private var time = 0f
    private var duration = 0f
    private var easing: (Float) -> Float = { it }

    fun set(next: Float) {
        value = next
        mode = 0
        current = null
        pending.clear()
    }

    /** Starts [steps] (a sequence) [delay] s after [now]; [done] runs if they finish unreplaced. */
    fun animate(vararg steps: Step, delay: Float = 0f, now: Float, done: (() -> Unit)? = null) {
        val start = now + delay
        // Animations due later than this one are replaced by it; earlier ones run until it starts.
        pending.removeAll { it.start >= start }
        val entry = Entry(start, ArrayDeque(steps.toList()), done)
        if (delay <= 0f) begin(entry) else pending += entry
    }

    private fun begin(entry: Entry) {
        pending.removeAll { it.start <= entry.start }
        current = entry
        next()
    }

    private fun next() {
        val step = current?.steps?.removeFirstOrNull()
        when (step) {
            null -> mode = 0
            is Timing -> {
                mode = 2
                from = value
                to = step.to
                time = 0f
                duration = step.seconds
                easing = step.curve
                velocity = 0f
            }
            is Spring -> {
                val x0 = value - step.to
                mode = 1
                to = step.to
                velocity = 0f
                zeta = step.ratio
                stiffness = stiffnessFor(x0, 0f, step.ms, step.ratio)
                omega0 = sqrt(stiffness)
                omega1 = if (zeta < 1f) omega0 * sqrt(1f - zeta * zeta) else 0f
                initialEnergy = energy(x0, 0f)
            }
        }
    }

    /** Advances to [now] by [dt] s; returns the callback of an animation that just finished. */
    fun step(now: Float, dt: Float): (() -> Unit)? {
        val due = pending.minByOrNull { it.start }
        if (due != null && now >= due.start) begin(due)
        var finished = false
        when (mode) {
            0 -> return null
            1 -> {
                val x0 = value - to
                val v0 = velocity
                if (zeta < 1f) {
                    val sin1 = sin(omega1 * dt)
                    val cos1 = cos(omega1 * dt)
                    val envelope = exp(-zeta * omega0 * dt)
                    val frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
                    value = to + frag
                    velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
                } else {
                    val envelope = exp(-omega0 * dt)
                    value = to + envelope * (x0 + (v0 + omega0 * x0) * dt)
                    velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
                }
                if (initialEnergy == 0f || !initialEnergy.isFinite() || energy(to - value, velocity) / initialEnergy <= ENERGY_THRESHOLD) {
                    value = to
                    velocity = 0f
                    finished = true
                }
            }
            else -> {
                time += dt
                val p = if (duration > 0f) clamp(time / duration, 0f, 1f) else 1f
                value = from + (to - from) * easing(p)
                finished = p >= 1f
            }
        }
        if (!finished) return null
        val entry = current
        if (entry != null && entry.steps.isNotEmpty()) {
            next()
            return null
        }
        mode = 0
        current = null
        return entry?.done
    }

    private fun energy(x: Float, v: Float) = 0.5f * stiffness * x * x + 0.5f * v * v

    companion object {
        const val ENERGY_THRESHOLD = 6e-9f

        /** The stiffness Reanimated solves for so a spring of mass 1 settles in 1.5 × the duration. */
        fun stiffnessFor(x0: Float, v0: Float, durationMs: Float, zeta: Float): Float {
            if (x0 == 0f && v0 == 0f) return 0f
            val settling = durationMs * 1.5 / 1000.0
            fun diff(k: Double): Double {
                val omega = sqrt(k) * zeta
                val a = x0 + (v0 + x0 * omega) * settling
                val e = exp(-omega * settling)
                val xt = a * e
                val vt = a * e * -omega + (v0 + x0 * omega) * e
                val e0 = 0.5 * k * x0 * x0 + 0.5 * v0 * v0
                val et = 0.5 * k * xt * xt + 0.5 * vt * vt
                return et / e0 - ENERGY_THRESHOLD
            }
            var lo = Math.ulp(1.0)
            var hi = 8e3
            val direction = if (diff(hi) >= diff(lo)) 1 else -1
            var current = (lo + hi) / 2
            var left = 100
            while (abs(diff(current)) > ENERGY_THRESHOLD * 1e-3 && left > 0) {
                left -= 1
                if (diff(current) * direction < 0) lo = current else hi = current
                current = (lo + hi) / 2
            }
            return current.toFloat()
        }
    }
}

internal enum class PatternStatus(val copy: String) {
    Idle("Connect at least four dots"),
    Wrong("Wrong pattern. Try again."),
    Right("Unlocked"),
}

/** What the lock asks of the screen around it: haptics and a callback. */
internal interface PatternHost {
    fun selection()
    fun wrong()
    fun right()
    fun unlocked()
}

/** The lock's state, stepped once a frame like the RN frame callback, and the gesture that drives it. */
internal class PatternEngine(private val host: PatternHost) {
    var reducedMotion = false
    var pattern = PatternZ
    var grid = PatternGrid(400f, 800f)

    var now = 0f
        private set
    val selected = ArrayList<Int>()
    /** When each dot was hit (s, on [now]), or NaN if it is not in the pattern. */
    val hits = FloatArray(9) { Float.NaN }
    // 0: taking input, 1: showing a wrong pattern, 2: unlocking.
    var stage = 0
        private set
    var drawing = false
        private set
    var fingerX = 0f
        private set
    var fingerY = 0f
        private set
    val tail = FloatArray(4)
    val snap = FloatArray(4)
    val wrong = PatternValue(0f)
    val right = PatternValue(0f)
    var shake = 0f
        private set
    private var shakeAt = -10f
    val lineAlpha = PatternValue(1f)
    val pulse = PatternValue(0f)
    val collapse = PatternValue(0f)
    val lock = PatternValue(0f)
    val open = PatternValue(0f)
    val check = PatternValue(0f)
    val glow = PatternValue(0f)
    val ghostAlpha = PatternValue(0f)
    private var ghost = -1
    private var clock = 0f
    private var looping = false
    private var hold = 0f

    // The status line, and the one fading out under it.
    var status = PatternStatus.Idle
        private set
    var statusAt = -10f
        private set
    var oldStatus: PatternStatus? = null
        private set
    var oldStatusAt = -10f
        private set

    fun setAutoPlay(on: Boolean) {
        looping = on
        clock = 0f
        hold = 0f
    }

    fun step(dt: Float) {
        if (dt <= 0f) return
        now += dt
        val done = listOf(wrong, right, lineAlpha, pulse, collapse, lock, open, check, glow, ghostAlpha).mapNotNull { it.step(now, dt) }
        done.forEach { it() }

        // autoPlay: a fixed script on a clock, paused for a while by any touch.
        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f && looping) clock = if (stage == 0) 0f else RIGHT_AT + (PatternZ.size - 1) * SEGMENT + 1f
        } else if (looping) {
            val before = clock
            val t = (before + dt) % LOOP
            clock = t
            val strokes = listOf(WRONG_AT to PatternL, RIGHT_AT to pattern)
            strokes.forEachIndexed { s, (start, dots) ->
                if (dots.size < 2) return@forEachIndexed
                val length = (dots.size - 1) * SEGMENT
                if (passed(before, t, start) && stage == 0) {
                    ghost = s
                    ghostAlpha.animate(PatternValue.Timing(1f, 0.14f), now = now)
                    val (x, y) = grid.along(dots, 0f)
                    begin(x, y, false)
                }
                if (ghost == s) {
                    val (x, y) = grid.along(dots, (t - start) / length)
                    move(x, y, false)
                    if (t >= start + length + LIFT_AFTER) {
                        ghost = -1
                        ghostAlpha.animate(PatternValue.Timing(0f, 0.18f), now = now)
                        release(false)
                    }
                }
            }
        }

        // The wrong-pattern shake: a struck spring dying away.
        val since = now - shakeAt
        shake = if (since >= 0f && since < SHAKE_TIME) SHAKE * exp(-since * SHAKE_DECAY) * sin(since * SHAKE_HZ * PI.toFloat() * 2f) else 0f

        // The open end chases the finger (or, once let go, falls back onto the last dot).
        val last = selected.lastOrNull() ?: -1
        val goalX = if (drawing || last < 0) fingerX else grid.xs[last]
        val goalY = if (drawing || last < 0) fingerY else grid.ys[last]
        if (reducedMotion) {
            tail[0] = goalX
            tail[1] = goalY
            tail[2] = 0f
            tail[3] = 0f
        } else {
            springStep(tail, goalX, goalY, TAIL_STIFFNESS, TAIL_DAMPING, dt)
        }
        if (snap.any { it != 0f }) {
            springStep(snap, 0f, 0f, SNAP_STIFFNESS, SNAP_DAMPING, dt)
            if (abs(snap[0]) + abs(snap[1]) < 0.05f && abs(snap[2]) + abs(snap[3]) < 1f) snap.fill(0f)
        }
    }

    private fun setStatus(next: PatternStatus) {
        if (next == status) return
        oldStatus = status
        oldStatusAt = now
        status = next
        statusAt = now
    }

    private fun clearPattern() {
        selected.clear()
        hits.fill(Float.NaN)
        lineAlpha.set(1f)
        glow.animate(PatternValue.Timing(0f, 0.4f), now = now)
    }

    /** Adds a dot to the pattern, with the dot it jumped over first if it skipped one. */
    private fun pick(k: Int, user: Boolean) {
        if (k in selected || k !in 0..8) return
        val last = selected.lastOrNull() ?: -1
        val middle = if (last >= 0) between(last, k) else -1
        if (middle >= 0 && middle !in selected) {
            selected += middle
            hits[middle] = now - 0.04f
        }
        selected += k
        hits[k] = now
        // The line's end snaps from where the finger is onto the dot, and springs home.
        if (reducedMotion) {
            snap.fill(0f)
        } else {
            snap[0] = fingerX - grid.xs[k]
            snap[1] = fingerY - grid.ys[k]
            snap[2] = 0f
            snap[3] = 0f
        }
        glow.animate(PatternValue.Timing(selected.size / 9f, 0.26f, ::easeOut), now = now)
        if (user) host.selection()
    }

    /** Picks up any dot the finger passes over between two points. */
    private fun sweep(x0: Float, y0: Float, x1: Float, y1: Float, user: Boolean) {
        val distance = hypot(x1 - x0, y1 - y0)
        // A point that is not a number (never from a finger) picks nothing; a huge jump is capped.
        if (!distance.isFinite()) return
        val steps = max(1, min(ceil(distance / 6f), 2000f).toInt())
        val reach = grid.spacing * HIT
        for (s in 1..steps) {
            val x = x0 + (x1 - x0) * s / steps
            val y = y0 + (y1 - y0) * s / steps
            for (k in 0 until 9) if (hypot(x - grid.xs[k], y - grid.ys[k]) < reach) pick(k, user)
        }
    }

    private fun fail(user: Boolean) {
        stage = 1
        wrong.animate(PatternValue.Timing(1f, 0.16f), now = now)
        if (!reducedMotion) shakeAt = now
        lineAlpha.animate(PatternValue.Timing(0f, FADE_TIME), delay = WRONG_HOLD, now = now) {
            clearPattern()
            wrong.animate(PatternValue.Timing(0f, 0.24f), now = now)
            stage = 0
            setStatus(PatternStatus.Idle)
        }
        setStatus(PatternStatus.Wrong)
        if (user) host.wrong()
    }

    private fun succeed(user: Boolean) {
        stage = 2
        right.animate(PatternValue.Timing(1f, 0.2f), now = now)
        pulse.animate(PatternValue.Timing(1f, PULSE_IN, ::easeOut), PatternValue.Timing(0f, PULSE_OUT), now = now)
        if (reducedMotion) {
            collapse.animate(PatternValue.Timing(1f, 0.3f), delay = COLLAPSE_AT, now = now)
            lock.animate(PatternValue.Timing(1f, 0.3f), delay = LOCK_AT, now = now)
            check.animate(PatternValue.Timing(1f, CHECK_TIME), delay = CHECK_AT, now = now)
        } else {
            collapse.animate(PatternValue.Spring(1f, 620f, 0.82f), delay = COLLAPSE_AT, now = now)
            lock.animate(PatternValue.Spring(1f, 700f, 0.68f), delay = LOCK_AT, now = now)
            open.animate(PatternValue.Spring(1f, 520f, 0.5f), delay = OPEN_AT, now = now)
            check.animate(PatternValue.Timing(1f, CHECK_TIME, ::easeOut), delay = CHECK_AT, now = now)
        }
        // Then everything goes back the way it came, and the grid takes input again.
        open.animate(PatternValue.Timing(0f, 0.2f), delay = RESET_AFTER, now = now)
        check.animate(PatternValue.Timing(0f, 0.2f), delay = RESET_AFTER, now = now)
        lock.animate(PatternValue.Timing(0f, 0.28f), delay = RESET_AFTER, now = now) {
            clearPattern()
            right.animate(PatternValue.Timing(0f, 0.3f), now = now)
            if (reducedMotion) {
                collapse.animate(PatternValue.Timing(0f, 0.3f), now = now)
            } else {
                collapse.animate(PatternValue.Spring(0f, 700f, 0.8f), now = now)
            }
            stage = 0
            setStatus(PatternStatus.Idle)
        }
        setStatus(PatternStatus.Right)
        host.unlocked()
        if (user) host.right()
    }

    private fun release(user: Boolean) {
        if (!drawing) return
        drawing = false
        if (selected.isEmpty()) return
        if (selected.size >= MIN_DOTS && selected == pattern) succeed(user) else fail(user)
    }

    private fun begin(x: Float, y: Float, user: Boolean) {
        if (stage != 0) return
        drawing = true
        fingerX = x
        fingerY = y
        tail[0] = x
        tail[1] = y
        tail[2] = 0f
        tail[3] = 0f
        sweep(x, y, x, y, user)
    }

    private fun move(x: Float, y: Float, user: Boolean) {
        if (!drawing) return
        val fx = fingerX
        val fy = fingerY
        fingerX = x
        fingerY = y
        sweep(fx, fy, x, y, user)
    }

    // Gesture (dp)

    fun touchBegan(x: Float, y: Float) {
        hold = USER_HOLD
        if (ghost >= 0) {
            // A virtual stroke in progress is dropped.
            ghost = -1
            ghostAlpha.animate(PatternValue.Timing(0f, 0.15f), now = now)
            if (drawing) {
                drawing = false
                clearPattern()
            }
        }
        begin(x, y, true)
    }

    fun touchMoved(x: Float, y: Float) = move(x, y, true)

    fun touchEnded() = release(true)
}
