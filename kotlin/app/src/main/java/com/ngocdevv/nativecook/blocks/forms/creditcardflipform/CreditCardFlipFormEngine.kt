package com.ngocdevv.nativecook.blocks.forms.creditcardflipform

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Seconds on the monotonic clock the frame clock also uses. */
internal fun nowSeconds() = System.nanoTime() / 1e9

// Curves.
/** Reanimated's default timing curve, `Easing.inOut(Easing.quad)`. */
internal val InOutQuad = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
internal val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
internal val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
/** A reel catching on its detent: overshoots by a hair and settles. */
internal val Spin = CubicBezierEasing(0.16f, 0.9f, 0.3f, 1.06f)

internal fun CubicBezierEasing.at(k: Double) = transform(k.coerceIn(0.0, 1.0).toFloat()).toDouble()

private fun outQuad(k: Double) = 1 - (1 - k) * (1 - k)

/** A number moving to a target on a curve, sampled against the clock. */
internal class Tween(value: Double) {
    private var from = value
    var target = value
        private set
    private var start = 0.0
    private var duration = 0.0
    private var curve: CubicBezierEasing = InOutQuad

    val end get() = start + duration

    fun value(at: Double): Double {
        if (duration <= 0) return target
        return from + (target - from) * curve.at((at - start) / duration)
    }

    fun animate(to: Double, at: Double, duration: Double, curve: CubicBezierEasing = InOutQuad) {
        from = value(at)
        target = to
        start = at
        this.duration = duration
        this.curve = curve
    }

    fun set(value: Double) {
        from = value
        target = value
        duration = 0.0
    }
}

/**
 * Reanimated's `withSpring({ duration, dampingRatio })` in closed form: the stiffness is solved
 * (mass 1) so the energy falls to Reanimated's threshold after 1.5 × the duration; a new target
 * starts from the current value and velocity, as Reanimated hands velocity on.
 */
internal class Spring(value: Double, duration: Double, private val zeta: Double) {
    private var from = value
    private var velocity = 0.0
    var target = value
        private set
    private var start = -1e9
    private val omega = sqrt(stiffness(duration, zeta))
    private val settle = duration * 1.5

    val end get() = start + settle

    private fun state(at: Double): Pair<Double, Double> {
        val t = at - start
        if (t <= 0) return from to velocity
        if (t >= settle) return target to 0.0
        val x0 = from - target
        val v0 = velocity
        val envelope = exp(-zeta * omega * t)
        if (zeta < 1) {
            val wd = omega * sqrt(1 - zeta * zeta)
            val a = (v0 + zeta * omega * x0) / wd
            val x = envelope * (x0 * cos(wd * t) + a * sin(wd * t))
            val v = -zeta * omega * x + envelope * (-x0 * wd * sin(wd * t) + a * wd * cos(wd * t))
            return target + x to v
        }
        val b = v0 + omega * x0
        val x = envelope * (x0 + b * t)
        return target + x to -omega * x + envelope * b
    }

    fun value(at: Double) = state(at).first

    fun animate(to: Double, at: Double) {
        val (x, v) = state(at)
        from = x
        velocity = v
        // Velocity that would carry the first frame away from the target is dropped.
        if ((to > from && velocity < 0) || (to < from && velocity > 0)) velocity = 0.0
        target = to
        start = at
    }

    fun set(value: Double) {
        from = value
        target = value
        velocity = 0.0
        start = -1e9
    }

    /** Lets go at [value] moving at [speed] (per s), springing to [to]; the speed is kept as given. */
    fun release(value: Double, speed: Double, to: Double, at: Double) {
        from = value
        velocity = speed
        target = to
        start = at
    }

    companion object {
        fun stiffness(duration: Double, zeta: Double): Double {
            val threshold = 6e-9
            val settling = duration * 1.5
            fun difference(k: Double): Double {
                val w = sqrt(k) * zeta
                val xt = (1 + w * settling) * exp(-w * settling)
                val vt = xt * -w + w * exp(-w * settling)
                return (0.5 * k * xt * xt + 0.5 * vt * vt) / (0.5 * k) - threshold
            }
            var low = Math.ulp(1.0)
            var high = 8e3
            val rising = difference(high) >= difference(low)
            var k = (low + high) / 2
            var left = 100
            while (abs(difference(k)) > threshold * 1e-3 && left > 0) {
                left -= 1
                if ((difference(k) < 0) == rising) low = k else high = k
                k = (low + high) / 2
            }
            return if (k.isFinite()) k else 170.0
        }
    }
}

/**
 * One character on the card, on a reel of its placeholder and the digits 0–9 twice. A typed digit
 * spins down through the first run of digits to its place in the second (11 + digit), so even a 0
 * rolls a full turn; deleting it spins back up to the placeholder. While it spins it dims.
 */
internal class Reel {
    private class Leg(val from: Double, val to: Double, val start: Double, val duration: Double, val speedFrom: Double) {
        fun roll(at: Double): Double {
            if (duration <= 0) return to
            return from + (to - from) * Spin.at((at - start) / duration)
        }

        fun speed(at: Double): Double {
            if (duration <= 0) return 0.0
            val e = at - start
            if (e <= 0) return speedFrom
            if (e < 0.04) return speedFrom + (1 - speedFrom) * InOutQuad.at(e / 0.04)
            return 1 - outQuad(((e - 0.04) / (duration * 0.8)).coerceAtMost(1.0))
        }
    }

    private var old: Leg? = null
    private var leg = Leg(0.0, 0.0, -1e9, 0.0, 0.0)

    val end get() = leg.start + leg.duration

    fun roll(at: Double): Double {
        val o = old
        return if (o != null && at < leg.start) o.roll(at) else leg.roll(at)
    }

    fun speed(at: Double): Double {
        val o = old
        return if (o != null && at < leg.start) o.speed(at) else leg.speed(at)
    }

    /** Spins to [char]; a reel going back waits [delay] first, its last spin still running meanwhile. */
    fun show(char: Char?, at: Double, delay: Double, still: Boolean) {
        val target = stop(char)
        if (still) {
            old = null
            leg = Leg(target, target, -1e9, 0.0, 0.0)
            return
        }
        val current = roll(at)
        if (target == current) return
        val back = target < current
        val start = at + if (back) delay else 0.0
        val next = Leg(roll(start), target, start, if (back) BACK_DURATION else SPIN_DURATION, speed(start))
        old = leg
        leg = next
    }

    companion object {
        const val SPIN_DURATION = 0.56
        const val BACK_DURATION = 0.42
        /** The reel dims to this at full speed. */
        const val MOVING_ALPHA = 0.55f

        fun stop(char: Char?): Double = if (char != null && char.isDigit()) 11.0 + (char - '0') else 0.0
    }
}

/** Invented card networks, told apart by the first digit of the number. */
internal enum class Network(val title: String, val palette: List<Long>) {
    None("", listOf(0xFF25262D, 0xFF454852, 0xFF9EA3B5)),
    Kestrel("Kestrel", listOf(0xFF0B4F4A, 0xFF13A77C, 0xFF7CF0C4)),
    Vela("Vela", listOf(0xFF1C2580, 0xFF6338E6, 0xFFA993FF)),
    Orrery("Orrery", listOf(0xFFB8330F, 0xFFB0124F, 0xFFFFAD5C)),
    Nimbus("Nimbus", listOf(0xFF0A3D63, 0xFF0B98D6, 0xFF8FDCFF));

    fun color(i: Int) = Color(palette[i])

    companion object {
        fun of(digits: String) = when (digits.firstOrNull()) {
            '3' -> Kestrel
            '4' -> Vela
            '5' -> Orrery
            '6' -> Nimbus
            else -> None
        }
    }
}

/** Reanimated's `interpolateColor`, which mixes in gamma-2.2 RGB. */
internal fun mixColor(a: Color, b: Color, k: Double): Color {
    fun mix(x: Float, y: Float): Float {
        val p = x.toDouble().pow(2.2)
        val q = y.toDouble().pow(2.2)
        return (p + (q - p) * k).pow(1 / 2.2).toFloat()
    }
    return Color(mix(a.red, b.red), mix(a.green, b.green), mix(a.blue, b.blue), 1f)
}

internal enum class Field { Number, Name, Expiry, Cvv }

internal enum class Stage { Idle, Paying, Paid }

/** Everything on the card and the pay button that moves, as values of time. */
internal class CardEngine {
    val numberReels = List(16) { Reel() }
    val expiryReels = List(4) { Reel() }
    val cvvReels = List(3) { Reel() }

    // The palette the card had, the one it turns to, and how far the turn has spread.
    var fromNetwork = Network.None
        private set
    var toNetwork = Network.None
        private set
    val mix = Tween(1.0)
    val shine = Tween(0.0)

    val flip = Spring(0.0, 0.82, 0.74)
    val flipFade = Tween(0.0)
    // The card's focus outline and the form's focus ring glide together on one spring; the CVV's
    // outline on the back ([ring]) only fades.
    val outline = List(4) { Spring(0.0, FOCUS_DURATION, FOCUS_RATIO) }
    val outlineOn = Tween(0.0)
    val formRing = List(4) { Spring(0.0, FOCUS_DURATION, FOCUS_RATIO) }
    val formRingOn = Tween(0.0)
    val ring = Tween(0.0)
    /** The pay button lights up once the card is complete. */
    val ready = Tween(0.0)

    // The card's tilt (°): set by a dragging finger, sprung flat on release, swung by a new network.
    val tiltX = Spring(0.0, TILT_DURATION, TILT_RATIO)
    val tiltY = Spring(0.0, TILT_DURATION, TILT_RATIO)
    private var nudgeAt: Double? = null

    // The network's mark: the one shown and when it came, and the ones leaving.
    var mark = Network.None
        private set
    var markAt = -1e9
        private set
    val leavingMarks = mutableListOf<Pair<Network, Double>>()
    val markScale = Spring(1.0, 0.52, 0.62)

    // The name: when each letter came, and the letters on their way out.
    class Ghost(val text: String, val leaving: Set<Int>, val at: Double)
    var name = ""
        private set
    var letterTimes = listOf<Double>()
        private set
    val ghosts = mutableListOf<Ghost>()
    var placeholderAt = -1e9
        private set
    var placeholderLeftAt: Double? = null
        private set

    // Paying.
    val fold = Tween(0.0)
    val check = Tween(0.0)
    var spinStart: Double? = null

    var settledAt = 0.0
        private set

    private fun kick(t: Double) {
        if (t > settledAt) settledAt = t
    }

    fun showNumber(digits: String, at: Double, still: Boolean) {
        for (i in 0 until 16) {
            numberReels[i].show(digits.getOrNull(i), at, (15 - i) * 0.022, still)
            kick(numberReels[i].end)
        }
    }

    fun showExpiry(digits: String, at: Double, still: Boolean) {
        for (i in 0 until 4) {
            expiryReels[i].show(digits.getOrNull(i), at, (3 - i) * 0.03, still)
            kick(expiryReels[i].end)
        }
    }

    fun showCvv(digits: String, at: Double, still: Boolean) {
        for (i in 0 until 3) {
            cvvReels[i].show(digits.getOrNull(i), at, (2 - i) * 0.04, still)
            kick(cvvReels[i].end)
        }
    }

    /** Letters rise in where the name grew and fade up where it shrank, keyed by their place. */
    fun showName(next: String, at: Double) {
        if (next == name) return
        val leaving = name.indices.filter { it >= next.length || name[it] != next[it] }.toSet()
        if (leaving.isNotEmpty()) ghosts.add(Ghost(name, leaving, at))
        letterTimes = next.indices.map { i -> if (i < name.length && name[i] == next[i]) letterTimes[i] else at }
        if (name.isEmpty() && next.isNotEmpty()) placeholderLeftAt = at
        if (name.isNotEmpty() && next.isEmpty()) {
            placeholderAt = at
            placeholderLeftAt = null
        }
        name = next
        kick(at + 0.32)
    }

    fun prune(at: Double) {
        ghosts.removeAll { at - it.at > 0.2 }
        leavingMarks.removeAll { at - it.second > 0.22 }
    }

    /** A new network: its colours spread from the mark over the old ones while a light crosses. */
    fun turnTo(network: Network, at: Double, still: Boolean) {
        if (network == toNetwork) return
        fromNetwork = toNetwork
        toNetwork = network
        mix.set(0.0)
        mix.animate(1.0, at, if (still) 0.3 else MORPH_DURATION, EaseInOut)
        shine.set(0.0)
        if (!still) shine.animate(1.0, at, SHINE_DURATION, EaseInOut)
        // The card swings a little so the light runs over its surface.
        if (!still && network != Network.None) {
            nudgeAt = at
            tiltY.set(NUDGE)
            tiltY.animate(0.0, at + NUDGE_TIME)
            kick(tiltY.end)
        }
        if (network != mark) {
            leavingMarks.add(mark to at)
            mark = network
            markAt = at
            markScale.set(0.7)
            markScale.animate(1.0, at)
        }
        kick(at + SHINE_DURATION)
    }

    fun crossLight(at: Double, still: Boolean) {
        shine.set(0.0)
        if (!still) {
            shine.animate(1.0, at, SHINE_DURATION, EaseInOut)
            kick(shine.end)
        }
    }

    /** The tilt about the horizontal (x) and upright (y) axes at [at]. */
    fun tiltAt(at: Double): Pair<Double, Double> {
        var y = tiltY.value(at)
        val n = nudgeAt
        if (n != null) {
            val t = at - n
            if (t >= 0 && t < NUDGE_TIME) y = NUDGE * EaseOut.at(t / NUDGE_TIME)
        }
        return tiltX.value(at) to y
    }

    /** A finger holds the card at this tilt. */
    fun hold(x: Double, y: Double) {
        nudgeAt = null
        tiltX.set(x)
        tiltY.set(y)
    }

    /** The finger lets go: the card springs flat, carrying the finger's speed (° per s). */
    fun letGo(speedX: Double, speedY: Double, at: Double) {
        val (x, y) = tiltAt(at)
        nudgeAt = null
        tiltX.release(x, speedX, 0.0, at)
        tiltY.release(y, speedY, 0.0, at)
        kick(tiltX.end)
    }

    /**
     * The card turns over for the CVV and back for anything else; the outline glides to the part
     * being edited on the front (fading when none is) and the form's ring to the field being edited.
     * A newly shown outline or ring appears in place.
     */
    fun focus(field: Field?, rects: Map<Field, Rect>, fields: Map<Field, Rect>, at: Double, still: Boolean) {
        val back = if (field == Field.Cvv) 1.0 else 0.0
        if (still) {
            flipFade.animate(back, at, 0.26)
            kick(flipFade.end)
        } else if (flip.target != back) {
            flip.animate(back, at)
            kick(flip.end)
        }
        val r = if (field != null && field != Field.Cvv) rects[field] else null
        if (r != null) {
            glide(outline, r, still || outlineOn.value(at) < 0.05, at)
            if (outlineOn.target != 1.0) outlineOn.animate(1.0, at, 0.2)
        } else if (outlineOn.target != 0.0) {
            outlineOn.animate(0.0, at, 0.2)
        }
        val f = if (field != null) fields[field] else null
        if (f != null) {
            glide(formRing, f, still || formRingOn.value(at) < 0.05, at)
            if (formRingOn.target != 1.0) formRingOn.animate(1.0, at, 0.2)
        } else if (formRingOn.target != 0.0) {
            formRingOn.animate(0.0, at, 0.2)
        }
        val ringOn = if (field == Field.Cvv) 1.0 else 0.0
        if (ring.target != ringOn) ring.animate(ringOn, at, 0.22)
        kick(at + 0.26)
    }

    /** The name grew or the layout changed: the outline follows the part being edited. */
    fun reframe(field: Field?, rects: Map<Field, Rect>, at: Double, still: Boolean) {
        val r = if (field != null && field != Field.Cvv) rects[field] else null
        if (r != null) glide(outline, r, still || outlineOn.value(at) < 0.05, at)
    }

    private fun glide(springs: List<Spring>, r: Rect, jump: Boolean, at: Double) {
        val values = listOf(r.left, r.top, r.width, r.height).map { it.toDouble() }
        for (i in 0 until 4) {
            if (jump) {
                springs[i].set(values[i])
            } else if (springs[i].target != values[i]) {
                springs[i].animate(values[i], at)
                kick(springs[i].end)
            }
        }
    }

    fun setReady(on: Boolean, at: Double) {
        val target = if (on) 1.0 else 0.0
        if (ready.target == target) return
        ready.animate(target, at, 0.24)
        kick(ready.end)
    }

    fun placeOutline(r: Rect) {
        listOf(r.left, r.top, r.width, r.height).forEachIndexed { i, v -> outline[i].set(v.toDouble()) }
    }

    /** 0 front … 1 back. */
    fun turn(at: Double, still: Boolean) = if (still) flipFade.value(at) else flip.value(at)

    fun pay(stage: Stage, at: Double) {
        when (stage) {
            Stage.Idle -> {
                fold.animate(0.0, at, PAY_FOLD, EaseInOut)
                check.animate(0.0, at, 0.16)
                spinStart = null
                kick(fold.end)
            }
            Stage.Paying -> {
                fold.animate(1.0, at, PAY_FOLD, EaseInOut)
                spinStart = at
                kick(at + PAY_FOLD + PAY_DURATION + 0.1)
            }
            Stage.Paid -> {
                check.animate(1.0, at, CHECK_DURATION, EaseOut)
                kick(check.end)
            }
        }
    }

    fun spin(at: Double): Double {
        val start = spinStart ?: return 0.0
        return ((at - start) % SPIN_TURN) / SPIN_TURN
    }

    /** Whether anything still moves (the comet runs round an outline and the spinner turns for as long as they show). */
    fun moving(at: Double) = at < settledAt || ring.target > 0 || ring.value(at) > 0 || outlineOn.target > 0 || outlineOn.value(at) > 0 ||
        spinStart != null

    companion object {
        const val MORPH_DURATION = 0.9
        const val SHINE_DURATION = 1.1
        const val PAY_FOLD = 0.38
        const val PAY_DURATION = 1.5
        const val CHECK_DURATION = 0.38
        const val SPIN_TURN = 0.82
        const val FOCUS_DURATION = 0.52
        const val FOCUS_RATIO = 0.86
        const val TILT_DURATION = 0.7
        const val TILT_RATIO = 0.55
        const val NUDGE = 7.0
        const val NUDGE_TIME = 0.26
    }
}
