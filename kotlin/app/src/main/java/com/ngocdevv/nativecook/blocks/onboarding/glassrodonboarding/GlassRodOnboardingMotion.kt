package com.ngocdevv.nativecook.blocks.onboarding.glassrodonboarding

import androidx.compose.animation.core.CubicBezierEasing
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

// The rod: a glass cylinder lying across the screen, ROD_RADIUS dp either side of its centre line.
// At rest a PEEK dp sliver of it waits at the right edge, breathing by PEEK_BREATH dp.
internal const val ROD_RADIUS = 46f
internal const val PEEK = 18f
internal const val PEEK_BREATH = 4f
internal const val PEEK_PERIOD = 2.6f // s
// It leans with its own speed (rad per page/s) and with the finger's vertical travel (rad per
// screen height), never past MAX_TILT; its colour fringes widen with speed.
internal const val LEAN_PER_SPEED = 0.07f
internal const val TILT_PER_HEIGHT = 0.55f
internal const val MAX_TILT = 0.2f
internal const val TILT_FOLLOW = 10f // 1/s
internal const val CHROMA_REST = 0.035f
internal const val CHROMA_FAST = 0.09f

// Paging: the photo behind the rod slides PHOTO_SHIFT and the text TEXT_SHIFT screen widths, and the
// incoming photo settles from INCOMING_SCALE.
internal const val PHOTO_SHIFT = 0.24f
internal const val TEXT_SHIFT = 0.12f
internal const val INCOMING_SCALE = 1.08f

// The finger: a page per rod travel, RUBBER pages at most past either end, and a release that adds
// PROJECT s of the finger's speed.
internal const val RUBBER = 0.18f
internal const val PROJECT = 0.22f
internal const val SETTLE_TIME = 0.62f
internal const val SETTLE_RATIO = 0.86f
internal const val TILT_TIME = 0.52f
internal const val TILT_RATIO = 0.7f
internal val AutoEase = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
internal const val AUTO_PAGE_TIME = 1.4f
internal const val SKIP_TIME = 0.9f
internal const val REDUCED_TIME = 0.32f
internal const val ACTIVE_OFFSET = 10f // dp
internal const val FAIL_OFFSET = 24f // dp
internal const val TAP_MS = 400L

// autoPlay, in seconds of a LOOP that rests on the first page: it pages forward at each of AUTO_GO,
// the last one wrapping from the third page round to the first, and presses the button.
internal const val LOOP = 12f
internal val AUTO_GO = floatArrayOf(0.8f, 4.4f, 8.4f)
internal const val AUTO_PRESS = 7.4f
// Where the clock picks up when a touch's pause ends, by page.
internal val RESUME_AT = floatArrayOf(0f, 2.4f, 5.9f)
internal const val USER_HOLD = 3.5f

// Dots: 8 dp, the current one stretched to 30 dp.
internal const val DOT = 8f
internal const val DOT_ACTIVE = 30f
internal const val DOT_GAP = 7f

internal fun clamp(x: Float, lo: Float, hi: Float) = min(hi, max(lo, x))

internal fun smooth(x: Float): Float {
    val t = clamp(x, 0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun inOutQuad(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f

/** How far page `i` is from progress `p`, going round (the autoPlay wrap joins the ends). */
internal fun ringDistance(p: Float, i: Int, count: Int): Float {
    val n = count.toFloat()
    val d = abs((((p - i) % n) + n) % n)
    return min(d, n - d)
}

/** Resistance past either end: at most RUBBER pages, however far the finger goes. */
internal fun rubber(raw: Float, count: Int): Float {
    if (raw < 0f) return -RUBBER * (1f - exp(raw / RUBBER))
    val over = raw - (count - 1)
    if (over > 0f) return (count - 1) + RUBBER * (1f - exp(-over / RUBBER))
    return raw
}

/** Which pages the rod is between: `a` on its left, `b` on its right, and how far it has come. */
internal class Segment(val a: Int, val b: Int, val f: Float)

/**
 * Past the last page `b` is the first page when the autoPlay wraps round, else the last page again
 * (a rubber band); before the first page, both are it.
 */
internal fun segment(p: Float, wrap: Boolean, count: Int): Segment {
    val n = floor(p).toInt()
    val f = p - n
    val a = if (n < 0) 0 else n % count
    val b = when {
        n < 0 -> 0
        n + 1 >= count -> if (wrap) (n + 1) % count else count - 1
        else -> n + 1
    }
    return Segment(a, b, f)
}

/**
 * A number driven by one animation at a time, like a shared value: a spring with a duration and a
 * damping ratio the way Reanimated solves it, or a timed move, with a callback when it finishes.
 */
internal class Driven(initial: Float) {
    var value = initial
    private var springing = false
    private var target = 0.0
    var velocity = 0.0
        private set
    private var zeta = 0.0
    private var omega0 = 0.0
    private var omega1 = 0.0
    private var stiffness = 0.0
    private var initialEnergy = 0.0
    private var timingTo: Float? = null
    private var timingFrom = 0f
    private var timingTime = 0f
    private var timingDuration = 1f
    private var curve: (Float) -> Float = ::inOutQuad
    private var done: (() -> Unit)? = null

    fun set(next: Float) {
        springing = false
        timingTo = null
        done = null
        velocity = 0.0
        value = next
    }

    fun springTo(to: Float, duration: Float, dampingRatio: Float, speed: Float = 0f, then: (() -> Unit)? = null) {
        val carried = if (springing) velocity else 0.0
        timingTo = null
        done = then
        var v = carried + speed
        // Speed that would carry the first frame away from the target is dropped.
        if ((to > value && v < 0) || (to < value && v > 0)) v = 0.0
        val x0 = (value - to).toDouble()
        springing = true
        target = to.toDouble()
        velocity = v
        zeta = dampingRatio.toDouble()
        stiffness = stiffnessFor(x0, v, duration.toDouble(), zeta)
        omega0 = sqrt(stiffness)
        omega1 = if (zeta < 1) omega0 * sqrt(1 - zeta * zeta) else 0.0
        initialEnergy = energy(x0, v)
    }

    fun timeTo(to: Float, duration: Float, curve: (Float) -> Float = ::inOutQuad, then: (() -> Unit)? = null) {
        springing = false
        timingTo = to
        timingFrom = value
        timingTime = 0f
        timingDuration = max(duration, 0.001f)
        this.curve = curve
        done = then
    }

    /** Moves the value on by `dt`; returns the finished move's callback, for the caller to run. */
    fun step(dt: Float): (() -> Unit)? {
        if (springing) {
            stepSpring(dt)
            if (!springing) return finish()
            return null
        }
        val to = timingTo ?: return null
        timingTime += dt
        val p = clamp(timingTime / timingDuration, 0f, 1f)
        value = timingFrom + (to - timingFrom) * curve(p)
        if (p >= 1f) {
            timingTo = null
            return finish()
        }
        return null
    }

    private fun finish(): (() -> Unit)? {
        val callback = done
        done = null
        return callback
    }

    private fun stepSpring(dt: Float) {
        if (initialEnergy == 0.0 || !initialEnergy.isFinite()) {
            value = target.toFloat()
            velocity = 0.0
            springing = false
            return
        }
        val x0 = value - target
        val v0 = velocity
        val t = dt.toDouble()
        var next: Double
        if (zeta < 1) {
            val sin1 = sin(omega1 * t)
            val cos1 = cos(omega1 * t)
            val envelope = exp(-zeta * omega0 * t)
            val frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            next = target + frag
            velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            val envelope = exp(-omega0 * t)
            next = target + envelope * (x0 + (v0 + omega0 * x0) * t)
            velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
        }
        if (!next.isFinite() || energy(target - next, velocity) / initialEnergy <= ENERGY_THRESHOLD) {
            next = target
            velocity = 0.0
            springing = false
        }
        value = next.toFloat()
    }

    private fun energy(x: Double, v: Double) = 0.5 * stiffness * x * x + 0.5 * v * v

    private companion object {
        const val ENERGY_THRESHOLD = 6e-9

        /** The stiffness Reanimated solves for so a spring of mass 1 settles in 1.5 × the duration. */
        fun stiffnessFor(x0: Double, v0: Double, duration: Double, zeta: Double): Double {
            if (x0 == 0.0 && v0 == 0.0) return 170.0
            val settling = duration * 1.5
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
            return if (current.isFinite() && current > 0) current else 170.0
        }
    }
}

/** What the screen needs to react to. */
internal interface Feel {
    fun changed()
    fun finished()
}

/** The paging state, stepped once a frame like the RN frame callback, and the gestures that move it. */
internal class Pager(val count: Int) {
    val progress = Driven(0f)
    private val fingerTilt = Driven(0f)
    private val pivot = Driven(0f)
    private val press = Driven(0f)
    var wrap = false
        private set
    private var peek = 1f
    private var time = 0f
    private var speed = 0f
    private var lastProgress = 0f
    private var lean = 0f
    var chroma = CHROMA_REST
        private set
    private var dragFrom = 0f
    private var dragging = false
    var clock = 0f
    var looping = false
    var hold = 0f
    var reduced = false
    var feel: Feel? = null
    var width = 1f
        private set
    var height = 1f
        private set
    private var placed = false

    val span get() = width + ROD_RADIUS * 2 - PEEK
    val pressValue get() = press.value
    val pivotY get() = pivot.value
    val angle get() = clamp(lean + fingerTilt.value, -MAX_TILT, MAX_TILT)

    /** The rod's centre line where it crosses the pivot height, in dp. */
    val rodX: Float
        get() {
            val f = segment(progress.value, wrap, count).f
            val sliver = (PEEK + PEEK_BREATH * sin(time * Math.PI.toFloat() * 2f / PEEK_PERIOD)) * peek
            if (reduced) return width + ROD_RADIUS * 4
            return width + ROD_RADIUS - sliver - f * (width + ROD_RADIUS * 2 - sliver)
        }

    fun resize(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        width = w
        height = h
        if (!placed) {
            placed = true
            pivot.set(h / 2f)
            peek = if (reduced) 0f else 1f
        }
    }

    fun frame(dt: Float) {
        val finished = listOfNotNull(progress.step(dt), fingerTilt.step(dt), pivot.step(dt), press.step(dt))
        finished.forEach { it() }
        frameCallback(dt)
    }

    private fun frameCallback(dt: Float) {
        time += dt
        // The rod's own speed, smoothed: it leans into its travel and its fringes widen.
        val p = progress.value
        val v = (p - lastProgress) / dt
        lastProgress = p
        val follow = 1f - exp(-dt * TILT_FOLLOW)
        speed += (v - speed) * follow
        lean += (clamp(speed * LEAN_PER_SPEED, -MAX_TILT, MAX_TILT) - lean) * follow
        chroma = CHROMA_REST + (CHROMA_FAST - CHROMA_REST) * clamp(abs(speed) / 2.5f, 0f, 1f)
        // The sliver waits at the right edge while there is a page to go to.
        val resting = !dragging && abs(p - p.roundToInt()) < 0.002f && p.roundToInt() < count - 1
        val want = if (reduced) 0f else if (resting) 1f else 0f
        peek += (want - peek) * (1f - exp(-dt * 6f))

        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f && looping) {
                // Pick the script up from the page the finger left it on.
                val page = clamp(progress.value.roundToInt().toFloat(), 0f, (count - 1).toFloat()).toInt()
                if (abs(progress.value - page) > 0.001f) goTo(page.toFloat(), 0.5f)
                clock = RESUME_AT[min(page, RESUME_AT.size - 1)]
            }
            return
        }
        if (!looping) return
        val before = clock
        val now = before + dt
        for (at in AUTO_GO) {
            if (before < at && now >= at) {
                val from = progress.value.roundToInt()
                if (from >= count - 1) wrap = true
                progress.timeTo((from + 1).toFloat(), if (reduced) REDUCED_TIME else AUTO_PAGE_TIME, AutoEase::transform) {
                    // Round the wrap back to the first page: the same picture, so nothing moves.
                    if (progress.value >= count - 0.0001f) {
                        progress.set(progress.value - count)
                        lastProgress = progress.value
                        wrap = false
                    }
                }
            }
        }
        if (before < AUTO_PRESS && now >= AUTO_PRESS) {
            press.timeTo(1f, 0.11f) { press.timeTo(0f, 0.26f) }
        }
        clock = if (now >= LOOP) now - LOOP else now
    }

    private fun goTo(target: Float, duration: Float) {
        progress.timeTo(target, if (reduced) REDUCED_TIME else duration, AutoEase::transform)
    }

    /** Brings a progress the autoPlay left past the last page (the wrap) back into range. */
    private fun settleWrap(): Boolean {
        // Only the autoPlay's wrap: a finger's rubber band past the last page is a different thing.
        val p = progress.value
        if (wrap) {
            // Seen as the step from before the first page to it: the same picture.
            val q = if (p > count - 1) p - count else p
            wrap = false
            progress.set(q)
            lastProgress = q
            progress.springTo(max(0f, q.roundToInt().toFloat()), SETTLE_TIME, SETTLE_RATIO)
            return true
        }
        return false
    }

    /** Springs a page on or back; true when the page changed. */
    fun step(direction: Int): Boolean {
        hold = USER_HOLD
        if (settleWrap()) return false
        val from = progress.value.roundToInt()
        val target = (from + direction).coerceIn(0, count - 1)
        if (target == from) return false
        progress.springTo(target.toFloat(), SETTLE_TIME, SETTLE_RATIO)
        feel?.changed()
        return true
    }

    val onLastPage get() = progress.value.roundToInt() >= count - 1 && !wrap

    fun buttonTapped() {
        hold = USER_HOLD
        if (onLastPage) feel?.finished() else step(1)
    }

    fun skip() {
        hold = USER_HOLD
        if (onLastPage) return
        wrap = false
        goTo((count - 1).toFloat(), SKIP_TIME)
        feel?.changed()
    }

    /** A horizontal drag has begun at `y` (dp); false when it should be ignored. */
    fun panStart(y: Float): Boolean {
        hold = USER_HOLD
        if (settleWrap()) return false
        progress.set(progress.value)
        dragFrom = progress.value
        dragging = true
        pivot.springTo(clamp(y, height * 0.15f, height * 0.85f), 0.26f, 1f)
        return true
    }

    fun panUpdate(tx: Float, ty: Float) {
        hold = USER_HOLD
        if (!dragging) return
        progress.set(rubber(dragFrom - tx / span, count))
        fingerTilt.set(clamp(ty / height * TILT_PER_HEIGHT, -MAX_TILT, MAX_TILT))
    }

    fun panEnd(vx: Float) {
        hold = USER_HOLD
        if (!dragging) return
        val from = dragFrom.roundToInt()
        val velocity = -vx / span
        val projected = progress.value + velocity * PROJECT
        val target = clamp(projected, (from - 1).toFloat(), (from + 1).toFloat()).roundToInt().coerceIn(0, count - 1)
        progress.springTo(target.toFloat(), SETTLE_TIME, SETTLE_RATIO, velocity)
        if (target != from) feel?.changed()
    }

    fun finalize() {
        dragging = false
        fingerTilt.springTo(0f, TILT_TIME, TILT_RATIO)
        pivot.springTo(height / 2f, TILT_TIME, TILT_RATIO)
    }
}
