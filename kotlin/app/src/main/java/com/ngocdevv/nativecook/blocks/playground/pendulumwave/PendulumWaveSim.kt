package com.ngocdevv.nativecook.blocks.playground.pendulumwave

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Fifteen pendulums tuned so that in one cycle of CYCLE seconds the longest swings BASE_SWINGS
// times and each shorter one swings once more than its neighbour. Released together they drift out
// of phase into travelling waves, snakes and apparent chaos, and come back into line exactly at the
// end of the cycle. A line-for-line port of the React Native simulation.
//
// World units: the table is y = 0, y is up, the row of pendulums runs along z, and they swing along
// x. A pendulum's state is its bob's sideways offset x and its speed; a free pendulum is a harmonic
// oscillator advanced exactly (a rotation in phase space by ω·dt), so the phases do not drift
// however long it runs. While the comb holds them the bobs are pulled onto it by a stiff spring,
// stepped in fixed substeps.

/** Seconds the autoPlay script takes before it starts over. */
internal const val LOOP = 36.0
/** Pendulums in the row. */
internal const val COUNT = 15
/** Seconds after which every pendulum is back in line. */
internal const val CYCLE = 30.0
/** Swings of the longest pendulum in one cycle; pendulum k makes BASE_SWINGS + k. */
private const val BASE_SWINGS = 24.0
/** The beam's height, the longest pendulum's length, the spacing along the row (world units). */
internal const val BEAM_Y = 12.0
private const val LONGEST = 10.0
internal const val SPACING = 1.0
/** Half the spread of each pendulum's V of string along the beam. */
internal const val V_HALF = 0.42
/** The bob's radius. */
internal const val BOB_R = 0.36
/** The furthest the comb takes the bobs aside, and its soft give past that. */
private const val AMPLITUDE_MAX = 2.0
private const val GIVE = 0.6
// The comb's grip: a stiff spring (1/s²) and damping (1/s).
private const val GRIP_K = 520.0
private const val GRIP_DAMP = 41.0
private const val SUBSTEP = 1.0 / 240
internal const val MAX_FRAME = 1.0 / 30
/** Light trails: one sample every TRAIL_STEP s, TRAIL samples long. */
internal const val TRAIL = 24
private const val TRAIL_STEP = 1.0 / 60
/** The comb fades in over COMB_IN s and away over COMB_OUT s. */
private const val COMB_IN = 0.22
private const val COMB_OUT = 0.35

internal const val EVENT_GRAB = 1
internal const val EVENT_RELEASE = 2

/** The row position of pendulum k. */
internal fun rowZ(k: Int) = (k - (COUNT - 1) / 2.0) * SPACING

/** A sideways offset limited to AMPLITUDE_MAX with a soft give. */
internal fun rubber(x: Double): Double {
    val a = abs(x)
    if (a <= AMPLITUDE_MAX) return x
    return (if (x > 0) 1.0 else -1.0) * (AMPLITUDE_MAX + GIVE * (1 - exp(-(a - AMPLITUDE_MAX) / GIVE)))
}

private fun easeInOut(p: Double): Double {
    val t = min(1.0, max(0.0, p))
    return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
}

// Views and the camera. A view is an orbit around the scene's middle: yaw (degrees around the
// vertical, 0 = looking down the row from its end), pitch (degrees above the horizon), distance.
internal const val VIEW_THREE_QUARTER = 0
internal const val VIEW_TOP = 1
internal const val VIEW_SIDE = 2
internal val ViewNames = listOf("3/4", "Top", "Side")

internal class Camera(val yaw: Double, val pitch: Double, val dist: Double)

private val Views = listOf(Camera(36.0, 20.0, 34.0), Camera(0.0, 76.0, 29.0), Camera(20.0, 6.0, 31.0))
/** In the 3/4 view the camera sways round by ORBIT degrees over each LOOP. */
private const val ORBIT = 16.0
/** A change of view takes MORPH s on an ease-in-out curve. */
internal const val MORPH = 1.5

/** Where view `index` puts the camera at loop time t. */
internal fun viewCamera(index: Int, t: Double): Camera {
    val v = Views[index]
    val sway = if (index == VIEW_THREE_QUARTER) ORBIT * sin(2 * PI * t / LOOP) else 0.0
    return Camera(v.yaw + sway, v.pitch, v.dist)
}

/** The camera partway (`p`, 0…1) from one view to another, eased; a morph arcs out a little. */
internal fun blendCamera(a: Camera, b: Camera, p: Double): Camera {
    val e = easeInOut(p)
    val lift = sin(PI * e) * 3
    return Camera(a.yaw + (b.yaw - a.yaw) * e, a.pitch + (b.pitch - a.pitch) * e, a.dist + (b.dist - a.dist) * e + lift)
}

// autoPlay: the comb pulls the bobs aside and lets go, the camera visits each view while one whole
// cycle plays, and when the bobs come back into line the comb catches them and brings them home.
private const val AMPLITUDE = 1.7
private const val GRAB_AT = 0.6
private const val PULLED_AT = 2.2
private const val RELEASE_AT = 2.4
private const val CATCH_AT = RELEASE_AT + CYCLE
private const val HOME_AT = CATCH_AT + 2.1
private const val SET_DOWN_AT = HOME_AT + 0.4
/** The view the camera turns to at each time. */
private val ViewScript = listOf(9.5 to VIEW_TOP, 17.5 to VIEW_SIDE, 25.5 to VIEW_THREE_QUARTER)

/** True when the loop's clock went past `at` between `before` and `now` (wrapping at LOOP). */
private fun passed(before: Double, now: Double, at: Double) =
    if (now >= before) before < at && now >= at else at > before || at <= now

internal class PendulumSim {
    val x = DoubleArray(COUNT)
    val v = DoubleArray(COUNT)
    /** Angular frequency (rad/s) and length of each pendulum. */
    val w = DoubleArray(COUNT) { k -> 2 * PI * (BASE_SWINGS + k) / CYCLE }
    // Length goes as the period squared: L = g / ω², scaled so the longest is LONGEST.
    val length = DoubleArray(COUNT) { k -> LONGEST * (BASE_SWINGS / (BASE_SWINGS + k)) * (BASE_SWINGS / (BASE_SWINGS + k)) }
    /** Trail ring buffer: TRAIL past x per pendulum, newest at `trailHead`. */
    val trail = Array(COUNT) { DoubleArray(TRAIL) }
    var trailHead = 0
    var trailClock = 0.0
    /** The comb: held or not, where it is, how visible. */
    var held = false
    var combX = 0.0
    var combShown = 0.0
    /** Seconds since the last release, for the cycle readout (-1 before the first). */
    var sinceRelease = -1.0
    var time = 0.0
    var events = 0
    var eventsByUser = false

    fun grab(at: Double, byUser: Boolean) {
        held = true
        combX = at
        events = events or EVENT_GRAB
        if (byUser) eventsByUser = true
    }

    fun move(to: Double) {
        if (held) combX = rubber(to)
    }

    /** Lets the bobs go; `timed` starts the cycle readout (a comb set down at rest does not). */
    fun release(byUser: Boolean, timed: Boolean) {
        if (!held) return
        held = false
        sinceRelease = if (timed) 0.0 else -1.0
        events = events or EVENT_RELEASE
        if (byUser) eventsByUser = true
    }

    fun step(frame: Double) {
        val dt = min(frame, MAX_FRAME)
        time += dt
        if (sinceRelease >= 0 && !held) sinceRelease += dt
        if (held) {
            combShown = min(1.0, combShown + dt / COMB_IN)
            val n = max(1, ceil(dt / SUBSTEP - 1e-6).toInt())
            val h = dt / n
            repeat(n) {
                for (k in 0 until COUNT) {
                    val a = GRIP_K * (combX - x[k]) - GRIP_DAMP * v[k]
                    v[k] += a * h
                    x[k] += v[k] * h
                }
            }
        } else {
            combShown = max(0.0, combShown - dt / COMB_OUT)
            // Free: each pendulum turns through ω·dt in phase space, exactly.
            for (k in 0 until COUNT) {
                val wk = w[k]
                val c = cos(wk * dt)
                val sn = sin(wk * dt)
                val xk = x[k]
                val u = v[k] / wk
                x[k] = xk * c + u * sn
                v[k] = (u * c - xk * sn) * wk
            }
        }
        // Trails, sampled on a fixed clock.
        trailClock += dt
        while (trailClock >= TRAIL_STEP) {
            trailClock -= TRAIL_STEP
            trailHead = (trailHead + 1) % TRAIL
            for (k in 0 until COUNT) trail[k][trailHead] = x[k]
        }
    }

    /** The height of pendulum k's bob at sideways offset x (its x is that offset, clamped; its z is rowZ). */
    fun bobY(k: Int, at: Double): Double {
        val l = length[k]
        val off = bobX(k, at)
        return BEAM_Y - sqrt(l * l - off * off)
    }

    /** Pendulum k's bob's sideways offset for a wish of `at`, kept short of horizontal. */
    fun bobX(k: Int, at: Double): Double {
        val l = length[k]
        return max(-l * 0.95, min(l * 0.95, at))
    }

    /** Drives the comb from the loop's clock; returns the view the script wants now. */
    fun script(before: Double, now: Double): Int {
        if (passed(before, now, GRAB_AT)) grab(0.0, false)
        if (now >= GRAB_AT && now < RELEASE_AT && held) {
            move(AMPLITUDE * easeInOut((now - GRAB_AT) / (PULLED_AT - GRAB_AT)))
        }
        if (passed(before, now, RELEASE_AT)) release(byUser = false, timed = true)
        // The bobs are back in line at CATCH_AT: the comb meets them there.
        if (passed(before, now, CATCH_AT)) {
            grab(AMPLITUDE, false)
            sinceRelease = -1.0
        }
        if (now >= CATCH_AT && held) move(AMPLITUDE * (1 - easeInOut((now - CATCH_AT) / (HOME_AT - CATCH_AT))))
        // Home and still: the comb is lifted away, so the loop starts from bare rest.
        if (passed(before, now, SET_DOWN_AT)) release(byUser = false, timed = false)
        var view = VIEW_THREE_QUARTER
        for ((at, index) in ViewScript) if (now >= at) view = index
        return view
    }
}
