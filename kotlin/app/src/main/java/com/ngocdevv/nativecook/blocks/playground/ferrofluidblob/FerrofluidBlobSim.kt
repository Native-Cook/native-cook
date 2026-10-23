package com.ngocdevv.nativecook.blocks.playground.ferrofluidblob

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// The ferrofluid's magnets: up to two of them, each following a finger (or the autoPlay script)
// and carrying the springs the shader reads: how far the fluid has heaped up toward it, how tall
// its crown of spikes stands, and which way the spikes lean. Stepped in fixed substeps, a line-for-
// line port of the React Native simulation.
//
// Units: dp and seconds. Positions are on the table ("ground"), not on the screen: the dish is seen
// from above at an angle, so a screen point maps to the ground through the camera in
// FerrofluidBlob.kt. The dish's centre is the ground origin.

/** How many magnets (fingers) can raise a crown at once. */
internal const val MAGNETS = 2

// Fixed substep and the most frame time one frame may consume (a long hitch is cut short).
private const val SUBSTEP = 1.0 / 120
internal const val MAX_FRAME = 1.0 / 30

// The magnet glides after its finger on a nearly critical spring (rad/s, damping ratio), so the
// crown trails a fast swipe a little and catches up without overshoot.
private const val FOLLOW_W = 15.0
private const val FOLLOW_Z = 0.86
// Field: how far the fluid heaps up under the magnet (0…1). It rises over ~0.3 s.
private const val FIELD_W = 8.0
private const val FIELD_Z = 0.62
// Spikes: their height (0…1, a little more during a pulse). Growing is well damped; once the field
// is gone they melt back underdamped, so they wobble and briefly dimple below the surface.
private const val SPIKE_W = 11.0
private const val SPIKE_RISE_Z = 0.5
private const val SPIKE_FALL_Z = 0.2
private const val SPIKE_MIN = -0.35
private const val SPIKE_MAX = 1.7
// The spikes lean against the magnet's motion, as if dragged through the fluid: LEAN_PER_SPEED dp
// of tip offset per dp/s, at most LEAN_MAX dp, eased at LEAN_RATE per second.
private const val LEAN_PER_SPEED = 0.03
private const val LEAN_MAX = 9.0
private const val LEAN_RATE = 12.0
// A tap pulses the magnet: the spikes get a kick of PULSE_SPIKE spike-heights per second and the
// field a smaller one, and the magnet stays on for TAP_LINGER s so the flinch can be seen.
private const val PULSE_SPIKE = 7.5
private const val PULSE_FIELD = 2.2
private const val TAP_LINGER = 0.42
// A touch shorter than TAP_TIME s that moves less than TAP_SLOP dp is a tap.
private const val TAP_TIME = 0.24
private const val TAP_SLOP = 12.0

// Events for haptics, read and cleared by the composable each frame.
internal const val EVENT_RISE = 1
internal const val EVENT_PULSE = 2

// Negative, so it can never meet a real pointer id (Compose's grow from 0 for the app's life).
internal const val SCRIPT_ID = -1000L

/** Seconds the script takes before it starts over. */
internal const val LOOP = 12.0

internal class Magnet {
    /** Touch id holding it, or -1. Scripted fingers use ids from SCRIPT_ID up. */
    var touch = -1L
    var scripted = false
    /** Where its finger is, and how strong it wants to be (0…1). */
    var tx = 0.0
    var ty = 0.0
    var power = 0.0
    /** Seconds it stays on after its finger lifts (after a tap). */
    var linger = 0.0
    var downAt = 0.0
    var travel = 0.0
    var lastX = 0.0
    var lastY = 0.0
    /** The magnet itself, gliding after the finger. */
    var x = 0.0
    var y = 0.0
    var vx = 0.0
    var vy = 0.0
    var field = 0.0
    var fieldV = 0.0
    var spike = 0.0
    var spikeV = 0.0
    var leanX = 0.0
    var leanY = 0.0
}

private class Key(val t: Double, val x: Double, val y: Double, val on: Boolean, val power: Double)

/** The first magnet sweeps an arc over the far side, flinches, glides to the near right and lifts. */
private val KeysA = listOf(
    Key(0.0, -0.46, 0.06, false, 0.0),
    Key(0.9, -0.46, 0.06, true, 0.85),
    Key(1.6, -0.42, -0.08, true, 1.0),
    Key(2.5, -0.18, -0.3, true, 1.0),
    Key(3.4, 0.16, -0.3, true, 1.0),
    Key(4.3, 0.38, -0.06, true, 1.0),
    Key(5.6, 0.38, -0.06, true, 1.0),
    Key(6.8, 0.3, 0.22, true, 1.0),
    Key(8.4, 0.22, 0.16, true, 1.0),
    Key(9.6, 0.22, 0.16, false, 0.0),
)

/** The second comes down on the left while the first is still there: two crowns. */
private val KeysB = listOf(
    Key(0.0, -0.34, 0.1, false, 0.0),
    Key(6.6, -0.34, 0.1, true, 0.9),
    Key(7.6, -0.3, -0.02, true, 1.0),
    Key(8.6, -0.22, 0.12, true, 1.0),
    Key(10.3, -0.22, 0.12, false, 0.0),
)

/** Taps: time and finger. */
private val Pulses = listOf(5.0 to 0, 8.9 to 0, 9.1 to 1)

private fun smooth01(p: Double): Double {
    val t = min(1.0, max(0.0, p))
    return t * t * (3 - 2 * t)
}

/** True when the loop's clock went past `at` between `before` and `now` (wrapping at LOOP). */
private fun passed(before: Double, now: Double, at: Double) =
    if (now >= before) before < at && now >= at else at > before || at <= now

internal class FerroSim(val reduced: Boolean) {
    val m = List(MAGNETS) { Magnet() }
    var time = 0.0
    var events = 0
    var eventsByUser = false

    // One step of a damped spring toward `target`; the result lands in springValue/springVelocity.
    private var springValue = 0.0
    private var springVelocity = 0.0

    private fun springStep(value: Double, velocity: Double, target: Double, w: Double, z: Double, h: Double) {
        val a = w * w * (target - value) - 2 * z * w * velocity
        val v = velocity + a * h
        springValue = value + v * h
        springVelocity = v
    }

    /** The magnet a new finger takes: a free one, preferring the one whose crown has melted most. */
    private fun freeMagnet(): Int {
        var best = -1
        var bestLeft = Double.POSITIVE_INFINITY
        for (i in m.indices) {
            val g = m[i]
            if (g.touch != -1L) continue
            val left = abs(g.field) + abs(g.spike) + (if (g.linger > 0) 2 else 0)
            if (left < bestLeft) {
                bestLeft = left
                best = i
            }
        }
        return best
    }

    /** Whether a real finger rests on the dish. */
    val userHolding get() = m.any { it.touch != -1L && !it.scripted }

    fun touchDown(id: Long, x: Double, y: Double, scripted: Boolean, power: Double) {
        val i = freeMagnet()
        if (i < 0) return
        val g = m[i]
        // A magnet whose crown has melted starts right under the finger instead of gliding over.
        if (abs(g.field) + abs(g.spike) < 0.08 && g.linger <= 0) {
            g.x = x
            g.y = y
            g.vx = 0.0
            g.vy = 0.0
            g.leanX = 0.0
            g.leanY = 0.0
        }
        g.touch = id
        g.scripted = scripted
        g.tx = x
        g.ty = y
        g.power = power
        g.linger = 0.0
        g.downAt = time
        g.travel = 0.0
        g.lastX = x
        g.lastY = y
        if (!scripted) {
            events = events or EVENT_RISE
            eventsByUser = true
        }
    }

    fun touchMove(id: Long, x: Double, y: Double, power: Double) {
        for (g in m) {
            if (g.touch != id) continue
            g.travel += hypot(x - g.lastX, y - g.lastY)
            g.lastX = x
            g.lastY = y
            g.tx = x
            g.ty = y
            g.power = power
        }
    }

    /** Kicks one magnet's crown up: the flinch of a tap. */
    private fun pulse(g: Magnet, byUser: Boolean) {
        g.spikeV += PULSE_SPIKE * (if (reduced) 0.4 else 1.0)
        g.fieldV += PULSE_FIELD
        g.linger = max(g.linger, TAP_LINGER)
        events = events or EVENT_PULSE
        if (byUser) eventsByUser = true
    }

    fun touchUp(id: Long) {
        for (g in m) {
            if (g.touch != id) continue
            val tap = time - g.downAt < TAP_TIME && g.travel < TAP_SLOP
            g.touch = -1L
            if (tap) pulse(g, !g.scripted)
        }
    }

    /** A scripted tap on whichever magnet a scripted finger holds. */
    private fun pulseScripted(id: Long) {
        for (g in m) if (g.touch == id) pulse(g, false)
    }

    /** Lets go of every magnet a real finger still holds (a gesture cut off without its pointers lifting). */
    fun releaseUsers() {
        for (g in m) if (!g.scripted && g.touch != -1L) g.touch = -1L
    }

    /** A real finger takes over: the script's fingers let go where they are. */
    fun cancelScripted() {
        for (g in m) if (g.scripted && g.touch != -1L) g.touch = -1L
    }

    fun step(frame: Double) {
        val dt = min(frame, MAX_FRAME)
        val n = max(1, ceil(dt / SUBSTEP - 1e-6).toInt())
        val h = dt / n
        val calm = reduced
        repeat(n) {
            time += h
            for (g in m) {
                if (g.linger > 0) g.linger = max(0.0, g.linger - h)
                val on = g.touch != -1L || g.linger > 0
                val power = if (on) g.power else 0.0
                if (g.touch != -1L) {
                    if (calm) {
                        g.x = g.tx
                        g.y = g.ty
                        g.vx = 0.0
                        g.vy = 0.0
                    } else {
                        springStep(g.x, g.vx, g.tx, FOLLOW_W, FOLLOW_Z, h)
                        g.x = springValue
                        g.vx = springVelocity
                        springStep(g.y, g.vy, g.ty, FOLLOW_W, FOLLOW_Z, h)
                        g.y = springValue
                        g.vy = springVelocity
                    }
                } else {
                    // Let go, the magnet coasts to a stop where it was.
                    val keep = exp(-8 * h)
                    g.vx *= keep
                    g.vy *= keep
                    g.x += g.vx * h
                    g.y += g.vy * h
                }
                springStep(g.field, g.fieldV, power, FIELD_W, if (calm) 1.0 else FIELD_Z, h)
                g.field = springValue
                g.fieldV = springVelocity
                val z = if (calm) 1.0 else if (power > g.spike) SPIKE_RISE_Z else SPIKE_FALL_Z
                springStep(g.spike, g.spikeV, power, SPIKE_W, z, h)
                val sp = springValue
                g.spike = min(SPIKE_MAX, max(SPIKE_MIN, sp))
                g.spikeV = if (sp == g.spike) springVelocity else 0.0
                // Lean against the motion, eased.
                var lx = -g.vx * LEAN_PER_SPEED
                var ly = -g.vy * LEAN_PER_SPEED
                val len = hypot(lx, ly)
                if (len > LEAN_MAX) {
                    lx *= LEAN_MAX / len
                    ly *= LEAN_MAX / len
                }
                val e = 1 - exp(-LEAN_RATE * h)
                g.leanX += (lx - g.leanX) * e
                g.leanY += (ly - g.leanY) * e
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // autoPlay. Two scripted fingers, placed in units of the dish's radius from its centre (ground
    // coordinates; negative y is the far side). A key holds the finger's place from its time on and
    // eases (smoothstep) into the next key; `on` keys put the finger down, off keys lift it.

    /**
     * Drives the scripted fingers from the loop's clock, which moved from `before` to `now`.
     * `radius` is the dish's radius in dp.
     */
    fun script(before: Double, now: Double, radius: Double) {
        val tracks = listOf(KeysA, KeysB)
        for (f in tracks.indices) {
            val keys = tracks[f]
            var i = 0
            while (i + 1 < keys.size && keys[i + 1].t <= now) i += 1
            val a = keys[i]
            var x = a.x
            var y = a.y
            var power = a.power
            if (i + 1 < keys.size) {
                val b = keys[i + 1]
                val e = smooth01((now - a.t) / (b.t - a.t))
                x = a.x + (b.x - a.x) * e
                y = a.y + (b.y - a.y) * e
                power = a.power + (b.power - a.power) * e
            }
            val id = SCRIPT_ID + f
            val held = m.any { it.touch == id }
            val px = x * radius
            val py = y * radius
            if (a.on && !held) {
                touchDown(id, px, py, true, power)
            } else if (a.on) {
                touchMove(id, px, py, power)
            } else if (held) {
                touchMove(id, px, py, power)
                // A scripted lift is never a tap; taps are listed in Pulses.
                for (g in m) if (g.touch == id) g.touch = -1L
            }
        }
        for ((at, finger) in Pulses) {
            if (passed(before, now, at)) pulseScripted(SCRIPT_ID + finger)
        }
    }
}
