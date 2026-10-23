package com.ngocdevv.nativecook.blocks.onboarding.polaroiddevelopwelcome

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The camera, front on, in dp (the whole hero shrinks on short screens).
internal const val CAMERA_WIDTH = 236f
internal const val CAMERA_HEIGHT = 170f
internal const val CAMERA_RADIUS = 36f
internal const val LENS = 50f
// The print: an instant-film card with a square photo and a deep bottom border.
internal const val PRINT_WIDTH = 152f
internal const val PRINT_HEIGHT = 184f
internal const val PHOTO_INSET = 10f
internal const val PHOTO_SIZE = PRINT_WIDTH - PHOTO_INSET * 2f
internal const val CAPTION_SIZE = 17f
internal const val HERO_HEIGHT = CAMERA_HEIGHT + 22f + PRINT_HEIGHT + 18f

// A snap: the shutter sinks, the flash fires, and after EJECT_DELAY the motor pushes the print out
// of the slot over EJECT_TIME. Before it is all the way out it starts to settle onto the pile below
// on a spring, so the two moves blend into one.
internal const val EJECT_DELAY = 0.18f
internal const val EJECT_TIME = 1.05f
internal val EjectEase = CubicBezierEasing(0.33f, 0f, 0.25f, 1f)
internal const val EJECT_TUCK = 14f // dp still in the slot when the motor stops
internal const val SETTLE_AT = EJECT_DELAY + EJECT_TIME * 0.72f
internal const val SETTLE_TIME = 0.95f
internal const val SETTLE_RATIO = 0.78f
internal const val SETTLE_LIFT = 0.045f
internal const val SHUDDER = 0.6f // dp
internal const val SNAP_GAP = 1.6f // s

// Flash: the window lights up and a warm bloom spreads from it.
internal const val FLASH_IN = 0.05f
internal const val FLASH_OUT = 0.65f
internal const val BLOOM_PEAK = 0.38f

// Developing: from a dark blank through a pale, washed-out ghost to full colour, over DEVELOP_TIME;
// shaking the held print speeds it up to SHAKE_BOOST + 1 times.
internal const val DEVELOP_TIME = 4.5f
internal const val SHAKE_BOOST = 4f
internal const val SHAKE_SPEED = 320f // dp/s of finger speed per unit of boost
internal const val DEVELOP_BLUR = 5f

// The pile: each print under the top one sits 3 dp higher, a little smaller and darker; the fourth
// fades out.
internal const val DEPTH_TIME = 0.52f
internal const val DEPTH_RATIO = 0.85f
internal const val DEPTH_SCALE = 0.025f
internal const val DEPTH_DIM = 0.07f
internal const val MAX_PRINTS = 5
// Holding the top print: it lifts, leans into its motion and resists being pushed up into the
// camera. A throw sends it under the pile.
internal const val HOLD_LIFT = 0.05f
internal const val TOSS_DISTANCE = 96f
internal const val TOSS_SPEED = 700f
internal const val TOSS_OUT = 0.43f // screen widths
internal const val TOSS_OUT_TIME = 0.38f
internal const val TOSS_OUT_RATIO = 1f
internal const val TOSS_BACK_TIME = 0.64f
internal const val TOSS_BACK_RATIO = 0.82f
internal const val RETURN_TIME = 0.52f
internal const val RETURN_RATIO = 0.72f
internal const val PAN_SLOP = 4f
internal const val TAP_MS = 500L
// Clearing the table: the prints fade and sink, the top one first.
internal const val CLEAR_TIME = 0.7f
internal const val CLEAR_STAGGER = 0.12f
internal val ClearEase = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

// Arrival on screen: the camera and each line of copy rise ENTER_RISE dp and fade in.
internal const val ENTER_TIME = 0.7f
internal const val ENTER_RISE = 14f
internal const val ENTER_STAGGER = 0.09f
internal const val INTRO_SNAP = 1f // s, the first snap when not on autoPlay

// autoPlay, in seconds of a LOOP that starts and ends on an empty table.
internal const val LOOP = 14f
internal val AUTO_SNAPS = floatArrayOf(0.6f, 4.6f)
internal const val AUTO_SHAKE_FROM = 7.2f
internal const val AUTO_SHAKE_TO = 8.4f
internal const val AUTO_TOSS = 9.6f
internal const val AUTO_CLEAR = 12f
internal const val USER_HOLD = 4f
internal val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Where each print comes to rest on the pile: a tilt (degrees) and an offset (dp). */
internal class Landing(val tilt: Float, val x: Float, val y: Float)

internal val Landings = listOf(
    Landing(-6f, -8f, 4f), Landing(5f, 10f, -2f), Landing(-2f, -2f, 8f), Landing(7.5f, 13f, 2f),
    Landing(-8.5f, -13f, -1f), Landing(3f, 5f, 6f), Landing(-4f, -6f, -4f), Landing(6f, 8f, 3f),
)

internal fun clamp01(x: Float) = x.coerceIn(0f, 1f)

internal fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

internal fun smooth(x: Float): Float {
    val t = clamp01(x)
    return t * t * (3f - 2f * t)
}

internal fun crossed(before: Float, now: Float, at: Float) = before < at && now >= at

/** Pushing a print up past the top of the pile meets more and more resistance (60 dp at most). */
internal fun resistUp(dy: Float) = if (dy >= 0f) dy else -60f * (1f - exp(dy / 60f))

internal fun inOutQuad(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f

/**
 * The colour of a developing print as a colour matrix (rows of five, offsets in 0…1), `d` from 0
 * (just out) to 1 (done). It starts as a flat dark brown with no picture, turns into a pale,
 * low-contrast, slightly cyan ghost of the photo at 42%, and then gains contrast and colour.
 */
internal fun developMatrix(d: Float): FloatArray {
    // The ghost: a quarter of the colour, half the contrast, lifted towards a cool grey.
    val s = 0.25f
    val c = 0.5f
    val lr = 0.2126f * (1 - s)
    val lg = 0.7152f * (1 - s)
    val lb = 0.0722f * (1 - s)
    val ghost = floatArrayOf(
        c * (lr + s), c * lg, c * lb, 0f, 0.4f,
        c * lr, c * (lg + s), c * lb, 0f, 0.43f,
        c * lr, c * lg, c * (lb + s), 0f, 0.46f,
        0f, 0f, 0f, 1f, 0f,
    )
    val blank = floatArrayOf(0f, 0f, 0f, 0f, 0.2f, 0f, 0f, 0f, 0f, 0.17f, 0f, 0f, 0f, 0f, 0.14f, 0f, 0f, 0f, 1f, 0f)
    val full = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    return if (d < 0.42f) {
        val t = smooth(d / 0.42f)
        FloatArray(20) { blank[it] + (ghost[it] - blank[it]) * t }
    } else {
        val t = smooth((d - 0.42f) / 0.58f)
        FloatArray(20) { ghost[it] + (full[it] - ghost[it]) * t }
    }
}

/** One step of a queued move: a wait (`withDelay`) or a timed move (`withTiming`). */
internal sealed class Step {
    class Wait(val duration: Float) : Step()
    class Timing(val to: Float, val duration: Float, val curve: (Float) -> Float = ::inOutQuad) : Step()
}

/**
 * A number driven by one animation at a time, like a shared value: a spring with a duration and a
 * damping ratio the way Reanimated solves it, or a queue of waits and timed moves, with a callback
 * when it finishes.
 */
internal class Driven(initial: Float) {
    var value = initial
    private var springing = false
    var springTarget = 0f
        private set
    private var velocity = 0.0
    private var zeta = 0.0
    private var omega0 = 0.0
    private var omega1 = 0.0
    private var stiffness = 0.0
    private var initialEnergy = 0.0
    private val steps = ArrayDeque<Step>()
    private var stepFrom = 0f
    private var stepTime = 0f
    private var pending: FloatArray? = null // target, duration, ratio
    private var done: (() -> Unit)? = null

    val moving get() = springing || steps.isNotEmpty() || pending != null
    val headingTo: Float? get() = if (springing) springTarget else pending?.get(0)

    fun set(next: Float) {
        springing = false
        steps.clear()
        pending = null
        done = null
        velocity = 0.0
        value = next
    }

    fun springTo(to: Float, duration: Float, dampingRatio: Float, speed: Float = 0f, then: (() -> Unit)? = null) {
        val carried = if (springing) velocity else 0.0
        steps.clear()
        pending = null
        done = then
        startSpring(to, duration, dampingRatio, speed, carried)
    }

    private fun startSpring(to: Float, duration: Float, dampingRatio: Float, speed: Float, carried: Double) {
        var v = carried + speed
        // Speed that would carry the first frame away from the target is dropped.
        if ((to > value && v < 0) || (to < value && v > 0)) v = 0.0
        val x0 = (value - to).toDouble()
        springing = true
        springTarget = to
        velocity = v
        zeta = dampingRatio.toDouble()
        stiffness = stiffnessFor(x0, v, duration.toDouble(), zeta)
        omega0 = sqrt(stiffness)
        omega1 = if (zeta < 1) omega0 * sqrt(1 - zeta * zeta) else 0.0
        initialEnergy = energy(x0, v)
    }

    /** Timed moves one after another, starting now. */
    fun sequence(next: List<Step>, then: (() -> Unit)? = null) {
        springing = false
        pending = null
        steps.clear()
        steps.addAll(next)
        stepFrom = value
        stepTime = 0f
        done = then
    }

    fun timeTo(to: Float, duration: Float, curve: (Float) -> Float = ::inOutQuad, then: (() -> Unit)? = null) =
        sequence(listOf(Step.Timing(to, duration, curve)), then)

    /** A spring that starts after a wait (`withDelay(withSpring)`). */
    fun delayedSpring(delay: Float, to: Float, duration: Float, dampingRatio: Float, then: (() -> Unit)? = null) {
        sequence(listOf(Step.Wait(delay)), then)
        pending = floatArrayOf(to, duration, dampingRatio)
    }

    /** Moves the value on by `dt`; returns the finished moves' callback, for the caller to run. */
    fun step(dt: Float): (() -> Unit)? {
        if (springing) {
            stepSpring(dt)
            return if (springing) null else finish()
        }
        var left = dt
        while (left > 0f && steps.isNotEmpty()) {
            when (val first = steps.first()) {
                is Step.Wait -> {
                    val use = min(left, first.duration - stepTime)
                    stepTime += use
                    left -= use
                    if (stepTime >= first.duration - 1e-6f) {
                        steps.removeFirst()
                        stepFrom = value
                        stepTime = 0f
                    } else {
                        return null
                    }
                }
                is Step.Timing -> {
                    stepTime += left
                    left = 0f
                    val p = clamp01(stepTime / max(first.duration, 0.001f))
                    value = stepFrom + (first.to - stepFrom) * first.curve(p)
                    if (p >= 1f) {
                        steps.removeFirst()
                        stepFrom = value
                        stepTime = 0f
                    }
                }
            }
        }
        if (steps.isEmpty()) {
            val next = pending
            if (next != null) {
                pending = null
                startSpring(next[0], next[1], next[2], 0f, 0.0)
            } else if (done != null) {
                return finish()
            }
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
            value = springTarget
            velocity = 0.0
            springing = false
            return
        }
        val target = springTarget.toDouble()
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

/**
 * One print: it slides out of the camera's slot and, still moving, settles onto the pile and
 * develops; it sinks a level each time a newer print lands on it.
 */
internal class PrintState(val key: Int, val shot: Int, val byUser: Boolean, reduced: Boolean) {
    val eject = Driven(if (reduced) 1f else 0f)
    val settle = Driven(if (reduced) 1f else 0f)
    val appear = Driven(if (reduced) 0f else 1f)
    val level = Driven(0f)
    var develop = 0f
    var landed = false

    init {
        if (reduced) {
            appear.timeTo(1f, 0.42f)
        } else {
            eject.sequence(listOf(Step.Wait(EJECT_DELAY), Step.Timing(1f, EJECT_TIME, EjectEase::transform)))
            settle.delayedSpring(SETTLE_AT, 1f, SETTLE_TIME, SETTLE_RATIO)
        }
    }

    val resting get() = settle.value > 0.94f && appear.value > 0.9f
}

/** Where a print sits this frame, in hero coordinates. */
internal class Pose(val x: Float, val y: Float, val tilt: Float, val lift: Float, val alpha: Float, val dim: Float)

/**
 * Where everything sits, in dp. Hero coordinates (camera and pile) are unscaled; the hero scales by
 * `scale` about the camera's top centre.
 */
internal class Stage(val width: Float, val height: Float, top: Float, bottom: Float) {
    // From the bottom up: the secondary action, the primary one, the sub line and the headline.
    private val secondaryTop = height - max(bottom, 12f) - 6f - 44f
    val primaryTop = secondaryTop - 8f - 56f
    val copyTop = primaryTop - 24f - 44f - 10f - 76f
    val cameraY = top + 56f
    private val room = copyTop - 18f - cameraY
    val scale = (room / HERO_HEIGHT).coerceIn(0.5f, 1f)
    private val spare = max(0f, room / scale - HERO_HEIGHT) / 2f
    val cameraX = (width - CAMERA_WIDTH) / 2f
    val slotY = cameraY + CAMERA_HEIGHT - 9f
    val pileX = width / 2f
    val pileY = cameraY + CAMERA_HEIGHT + 22f + spare + PRINT_HEIGHT / 2f
    val headerTop = top + 8f

    fun heroX(x: Float) = width / 2f + (x - width / 2f) / scale
    fun heroY(y: Float) = cameraY + (y - cameraY) / scale
}

/** The haptics the table plays. */
internal interface Feel {
    fun snapped()
    fun landed()
    fun thrown()
}

/** Everything the frame loop, the gestures and the picture share, kept off Compose state. */
internal class Table(private val shots: Int) {
    val prints = ArrayList<PrintState>()
    private var taken = 0
    private var nextKey = 0

    var heldKey = -1
        private set
    val heldX = Driven(0f)
    val heldY = Driven(0f)
    val heldTilt = Driven(0f)
    val holding = Driven(0f)
    val boost = Driven(0f)
    var tossKey = -1
        private set
    val tossX = Driven(0f)
    val tossY = Driven(0f)
    val tossTilt = Driven(0f)
    val tossFade = Driven(0f)
    val clearing = Driven(0f)
    val shutter = Driven(0f)
    val flash = Driven(0f)
    val cameraPress = Driven(0f)
    var readyGlow = 0f
        private set

    var time = 0f
        private set
    private var lastSnap = -10f
    var clock = 0f
    var looping = false
        set(value) {
            field = value
            if (value) introDone = true
        }
    var hold = 0f
    private var introDone = false
    private var autoShaking = false
    var reduced = false
    var stage: Stage? = null
    var feel: Feel? = null

    private var grabbing = false
    private var grabX = 0f
    private var grabY = 0f

    private val values = listOf(heldX, heldY, heldTilt, holding, boost, tossX, tossY, tossTilt, tossFade, clearing, shutter, flash, cameraPress)

    /** 0…1, how far an element that starts arriving `delay` s after the screen opened has come. */
    fun entered(delay: Float) = EaseOut.transform(clamp01((time - delay) / ENTER_TIME))

    /** Key of the top print once it has settled on the pile (it can be picked up), else -1. */
    val readyKey: Int get() = prints.lastOrNull()?.takeIf { it.resting }?.key ?: -1

    fun frame(dt: Float) {
        val finished = ArrayList<() -> Unit>()
        for (value in values) value.step(dt)?.let { finished.add(it) }
        for (print in prints) {
            print.eject.step(dt)
            print.settle.step(dt)
            print.appear.step(dt)
            print.level.step(dt)
            // Developing runs once the print starts to show; shaking the held one speeds it up.
            if (print.develop < 1f && print.eject.value >= 0.2f) {
                val rate = 1f + if (heldKey == print.key) boost.value else 0f
                print.develop = min(1f, print.develop + dt / DEVELOP_TIME * rate)
            }
            // A print the user took lands with a light haptic.
            val down = print.resting
            if (down && !print.landed && print.byUser) feel?.landed()
            print.landed = down
        }
        finished.forEach { it() }
        frameCallback(dt)
    }

    private fun frameCallback(dt: Float) {
        time += dt
        val ready = if (time - lastSnap >= SNAP_GAP && clearing.value == 0f) 1f else 0f
        readyGlow += (ready - readyGlow) * (1f - exp(-dt * 4f))
        if (!introDone && time >= INTRO_SNAP) {
            introDone = true
            snap(byUser = false)
        }
        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f && looping) {
                // Start over from an empty table: clear what the user left first.
                if (prints.isNotEmpty()) {
                    clock = AUTO_CLEAR
                    clear()
                } else {
                    clock = 0f
                }
            }
            return
        }
        if (!looping) return
        val before = clock
        val now = before + dt
        for (at in AUTO_SNAPS) if (crossed(before, now, at)) snap(byUser = false)
        // Shaking the top print: it is held and wiggled, which develops it faster.
        if (crossed(before, now, AUTO_SHAKE_FROM) && readyKey >= 0 && tossKey < 0) {
            autoShaking = true
            heldKey = readyKey
            holding.timeTo(1f, 0.14f)
        }
        if (autoShaking && now < AUTO_SHAKE_TO) {
            val s = (now - AUTO_SHAKE_FROM) / (AUTO_SHAKE_TO - AUTO_SHAKE_FROM)
            val envelope = sin(PI.toFloat() * clamp01(s))
            heldX.set(sin(now * PI.toFloat() * 2f * 3.2f) * 12f * envelope)
            heldY.set(-6f * envelope)
            heldTilt.set(cos(now * PI.toFloat() * 2f * 3.2f) * 5f * envelope)
            boost.set(SHAKE_BOOST * envelope)
        }
        if (crossed(before, now, AUTO_SHAKE_TO) && autoShaking) {
            autoShaking = false
            heldX.springTo(0f, RETURN_TIME, RETURN_RATIO)
            heldY.springTo(0f, RETURN_TIME, RETURN_RATIO)
            heldTilt.springTo(0f, RETURN_TIME, RETURN_RATIO)
            holding.timeTo(0f, 0.22f)
            boost.timeTo(0f, 0.6f)
        }
        if (crossed(before, now, AUTO_TOSS) && readyKey >= 0 && prints.size > 1 && tossKey < 0) {
            toss(readyKey, 1f, 900f)
        }
        if (crossed(before, now, AUTO_CLEAR)) clear()
        clock = if (now >= LOOP) now - LOOP else now
    }

    /** Every print springs to its level in the pile (0 on top). */
    private fun relevel() {
        prints.forEachIndexed { rank, print ->
            val depth = (prints.size - 1 - rank).toFloat()
            if (print.level.headingTo == depth || (!print.level.moving && print.level.value == depth)) return@forEachIndexed
            if (reduced) print.level.timeTo(depth, 0.26f) else print.level.springTo(depth, DEPTH_TIME, DEPTH_RATIO)
        }
    }

    /** Fires the shutter: flash, a jolt, and a new print on its way out of the camera. */
    fun snap(byUser: Boolean) {
        if (time - lastSnap < SNAP_GAP || clearing.value > 0f) return
        lastSnap = time
        shutter.sequence(listOf(Step.Timing(1f, 0.07f), Step.Timing(0f, 0.2f)))
        val peak = if (reduced) 0.35f else 1f
        flash.sequence(listOf(Step.Timing(peak, FLASH_IN), Step.Timing(0f, FLASH_OUT, EaseOut::transform)))
        if (byUser) feel?.snapped()
        nextKey += 1
        prints.add(PrintState(nextKey, taken % shots, byUser, reduced))
        taken += 1
        while (prints.size > MAX_PRINTS) prints.removeAt(0)
        relevel()
    }

    private fun clear() {
        if (prints.isEmpty()) return
        clearing.timeTo(1f, if (reduced) 0.4f else CLEAR_TIME, ClearEase::transform) {
            prints.clear()
            taken = 0
            clearing.set(0f)
        }
    }

    /** Throws the top print aside (`direction` ±1), from where it slides back under the pile. */
    private fun toss(key: Int, direction: Float, velocity: Float) {
        tossKey = key
        tossX.set(if (heldKey == key) heldX.value else 0f)
        tossY.set(if (heldKey == key) resistUp(heldY.value) else 0f)
        tossTilt.set(if (heldKey == key) heldTilt.value else 0f)
        tossFade.set(0f)
        heldKey = -1
        heldX.set(0f)
        heldY.set(0f)
        heldTilt.set(0f)
        holding.set(0f)
        if (reduced) {
            tossFade.timeTo(1f, 0.2f) { tossedOut(key) }
            return
        }
        val width = stage?.width ?: 400f
        tossY.springTo(22f, TOSS_OUT_TIME, TOSS_OUT_RATIO)
        tossTilt.springTo(direction * 14f, TOSS_OUT_TIME, TOSS_OUT_RATIO)
        tossX.springTo(direction * width * TOSS_OUT, TOSS_OUT_TIME, TOSS_OUT_RATIO, velocity) { tossedOut(key) }
    }

    /** The thrown print is out to the side: it goes under the pile and slides back in. */
    private fun tossedOut(key: Int) {
        val index = prints.indexOfFirst { it.key == key }
        if (index >= 0) {
            val thrown = prints.removeAt(index)
            prints.add(0, thrown)
            relevel()
        }
        val back = { tossKey = -1 }
        if (reduced) {
            tossX.set(0f)
            tossY.set(0f)
            tossTilt.set(0f)
            tossFade.sequence(listOf(Step.Wait(0.06f), Step.Timing(0f, 0.22f)), back)
            return
        }
        tossX.delayedSpring(0.06f, 0f, TOSS_BACK_TIME, TOSS_BACK_RATIO, back)
        tossY.delayedSpring(0.06f, 0f, TOSS_BACK_TIME, TOSS_BACK_RATIO)
        tossTilt.delayedSpring(0.06f, 0f, TOSS_BACK_TIME, TOSS_BACK_RATIO)
    }

    fun pose(print: PrintState): Pose {
        val s = stage ?: return Pose(0f, 0f, 0f, 0f, 0f, 0f)
        val landing = Landings[print.shot % Landings.size]
        val lv = print.level.value
        // Its place on the pile, a little higher and smaller for every print on top of it.
        val restX = s.pileX + landing.x
        val restY = s.pileY + landing.y - lv * 3f
        // Coming out of the slot, pushed by the motor with a faint shudder.
        val e = print.eject.value
        val shudder = if (e > 0f && e < 1f) sin(e * 80f) * SHUDDER else 0f
        val outEnd = s.slotY + PRINT_HEIGHT / 2f - EJECT_TUCK
        val outY = lerp(s.slotY - PRINT_HEIGHT / 2f, outEnd, e)
        val st = print.settle.value
        var x = lerp(s.pileX + shudder, restX, st)
        var y = outY + (restY - outEnd) * st
        var tilt = landing.tilt * st
        var lift = sin(PI.toFloat() * clamp01(st)) * SETTLE_LIFT - lv * DEPTH_SCALE
        var alpha = clamp01(3.6f - lv)
        if (heldKey == print.key) {
            x += heldX.value
            y += resistUp(heldY.value)
            tilt += heldTilt.value
            lift += holding.value * HOLD_LIFT
        }
        if (tossKey == print.key) {
            x += tossX.value
            y += tossY.value
            tilt += tossTilt.value
            alpha *= 1f - tossFade.value
        }
        // Clearing the table: fade and sink, the top print first.
        val c = smooth(clearing.value * (1f + CLEAR_STAGGER * 4f) - lv * CLEAR_STAGGER)
        y += c * 18f
        lift -= c * 0.04f
        alpha *= (1f - c) * print.appear.value
        return Pose(x, y, tilt, lift, alpha, min(0.3f, max(0f, lv) * DEPTH_DIM))
    }

    fun onCamera(x: Float, y: Float): Boolean {
        val s = stage ?: return false
        val hx = s.heroX(x)
        val hy = s.heroY(y)
        return hx > s.cameraX - 8f && hx < s.cameraX + CAMERA_WIDTH + 8f && hy > s.cameraY - 8f && hy < s.cameraY + CAMERA_HEIGHT + 8f
    }

    private fun onPile(x: Float, y: Float): Boolean {
        val s = stage ?: return false
        return abs(s.heroX(x) - s.pileX) < PRINT_WIDTH * 0.62f && abs(s.heroY(y) - s.pileY) < PRINT_HEIGHT * 0.6f
    }

    /** A finger is down: the camera gives a little if it is on it. */
    fun touchDown(x: Float, y: Float) {
        hold = USER_HOLD
        if (onCamera(x, y)) cameraPress.timeTo(1f, 0.09f)
    }

    /** The touch moved past the slop at (x, y) with (tx, ty) of travel: maybe pick up the top print. */
    fun panStart(x: Float, y: Float, tx: Float, ty: Float) {
        hold = USER_HOLD
        cameraPress.timeTo(0f, 0.2f)
        val top = readyKey
        if (top < 0 || tossKey >= 0 || clearing.value > 0f || !onPile(x, y)) return
        val scale = stage?.scale ?: 1f
        autoShaking = false
        if (heldKey != top) {
            heldX.set(0f)
            heldY.set(0f)
            heldTilt.set(0f)
        }
        heldKey = top
        grabbing = true
        grabX = heldX.value - tx / scale
        grabY = heldY.value - ty / scale
        holding.timeTo(1f, 0.14f)
    }

    fun panUpdate(tx: Float, ty: Float, vx: Float, vy: Float) {
        hold = USER_HOLD
        if (!grabbing || heldKey < 0 || heldKey == tossKey) return
        val scale = stage?.scale ?: 1f
        heldX.set(grabX + tx / scale)
        heldY.set(grabY + ty / scale)
        // The print leans into the way it is moving, a little like a card held by its edge.
        heldTilt.set((heldX.value * 0.05f + vx * 0.006f).coerceIn(-16f, 16f))
        // Shaking speeds up developing.
        val speed = sqrt(vx * vx + vy * vy)
        boost.set(boost.value + (min(SHAKE_BOOST, speed / SHAKE_SPEED) - boost.value) * 0.2f)
    }

    fun panEnd(vx: Float, vy: Float) {
        hold = USER_HOLD
        if (!grabbing || heldKey < 0) return
        val scale = stage?.scale ?: 1f
        val throwIt = prints.size > 1 && (abs(heldX.value) > TOSS_DISTANCE || abs(vx) > TOSS_SPEED)
        if (throwIt) {
            val direction = if ((if (abs(vx) > TOSS_SPEED) vx else heldX.value) > 0f) 1f else -1f
            toss(heldKey, direction, vx / scale)
            feel?.thrown()
            return
        }
        // Back to its place, carrying the finger's speed.
        heldX.springTo(0f, RETURN_TIME, RETURN_RATIO, vx / scale)
        heldY.springTo(0f, RETURN_TIME, RETURN_RATIO, vy / scale)
        heldTilt.springTo(0f, RETURN_TIME, RETURN_RATIO)
    }

    /** A tap that never became a pan: on the camera it takes a photo. */
    fun tapped(x: Float, y: Float) {
        hold = USER_HOLD
        if (onCamera(x, y)) snap(byUser = true)
    }

    fun finalize() {
        grabbing = false
        cameraPress.timeTo(0f, 0.2f)
        if (!autoShaking) holding.timeTo(0f, 0.22f)
        boost.timeTo(0f, 0.6f)
    }
}

/**
 * Fractal noise like Skia's (an SVG `feTurbulence` of type fractalNoise) with three octaves, one
 * channel each for red, green, blue and alpha, as premultiplied ARGB pixels.
 */
internal object Grain {
    private val permutation: IntArray = run {
        val table = IntArray(256) { it }
        var seed = 2_463_534_242L
        for (i in 255 downTo 1) {
            seed = seed xor (seed shl 13 and 0xFFFFFFFFL)
            seed = seed xor (seed ushr 17)
            seed = seed xor (seed shl 5 and 0xFFFFFFFFL)
            val j = (seed % (i + 1)).toInt()
            val t = table[i]
            table[i] = table[j]
            table[j] = t
        }
        IntArray(512) { table[it and 255] }
    }

    private fun fade(t: Double) = t * t * t * (t * (t * 6 - 15) + 10)

    private fun gradient(hash: Int, x: Double, y: Double) = when (hash and 7) {
        0 -> x + y
        1 -> x - y
        2 -> -x + y
        3 -> -x - y
        4 -> x
        5 -> -x
        6 -> y
        else -> -y
    }

    /** Gradient noise in about -1…1. */
    private fun noise(x: Double, y: Double): Double {
        val p = permutation
        val xf = floor(x)
        val yf = floor(y)
        val xi = xf.toInt() and 255
        val yi = yf.toInt() and 255
        val dx = x - xf
        val dy = y - yf
        val u = fade(dx)
        val v = fade(dy)
        val aa = p[p[xi] + yi]
        val ab = p[p[xi] + yi + 1]
        val ba = p[p[xi + 1] + yi]
        val bb = p[p[xi + 1] + yi + 1]
        val x1 = gradient(aa, dx, dy) + (gradient(ba, dx - 1, dy) - gradient(aa, dx, dy)) * u
        val x2 = gradient(ab, dx, dy - 1) + (gradient(bb, dx - 1, dy - 1) - gradient(ab, dx, dy - 1)) * u
        return x1 + (x2 - x1) * v
    }

    /** `columns` × `rows` pixels, at `across` and `down` cycles of the base octave per pixel. */
    fun pixels(columns: Int, rows: Int, across: Double, down: Double): IntArray {
        val offsets = arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(37.1, 11.3), doubleArrayOf(71.9, 53.7), doubleArrayOf(19.4, 97.2))
        val out = IntArray(columns * rows)
        val channel = DoubleArray(4)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                for (k in 0 until 4) {
                    val x = column * across + offsets[k][0]
                    val y = row * down + offsets[k][1]
                    val sum = noise(x, y) + noise(x * 2, y * 2) / 2 + noise(x * 4, y * 4) / 4
                    channel[k] = ((sum + 1) / 2).coerceIn(0.0, 1.0)
                }
                val a = channel[3]
                out[row * columns + column] = ((a * 255).toInt() shl 24) or
                    ((channel[0] * a * 255).toInt() shl 16) or
                    ((channel[1] * a * 255).toInt() shl 8) or
                    (channel[2] * a * 255).toInt()
            }
        }
        return out
    }
}
