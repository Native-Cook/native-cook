package com.ngocdevv.nativecook.blocks.media.spatialorbitmixer

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** The five sources, their orbits, the camera, and the self-playing loop, as closed-form functions of time. */
internal object SpatialOrbitMixerModel {
    /**
     * A source: orbit radius (dp on the floor), start angle (degrees; 0 is straight ahead of the
     * listener, 90 to the right), whole turns per loop, pulses and bobs a second, and where it sits on
     * the line in Stereo (x on the floor).
     */
    class Source(val name: String, val color: Long, val ring: Double, val start: Double, val turns: Double, val beat: Double, val bob: Double, val pan: Double)

    val sources = listOf(
        Source("Vocals", 0xB6F23A, 72.0, 0.0, 1.0, 2.0, 0.5, 0.0),
        Source("Keys", 0xA98BFF, 112.0, 110.0, 2.0, 3.0, 0.3, 120.0),
        Source("Guitar", 0xFFC247, 112.0, 250.0, 1.0, 1.5, 0.4, -120.0),
        Source("Drums", 0xFF6A5C, 152.0, 300.0, 0.0, 4.0, 0.2, -60.0),
        Source("Bass", 0x35D6FF, 152.0, 140.0, -1.0, 1.0, 0.1, 60.0),
    )
    val rings = doubleArrayOf(72.0, 112.0, 152.0)
    val modes = listOf("Stereo", "Spatial", "Fixed")
    /** The stereo line runs this far in front of the listener. */
    private const val STEREO_Z = 60.0

    const val LOOP = 10.0
    /** Camera: the floor is seen from 40° above (the rings' height is 64 % of their width); a vertical drag tilts it 24°–56°. */
    const val ELEVATION = 40.0

    // Curves

    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val u = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return u * u * (3 - 2 * u)
    }

    fun easeInOut(x: Double): Double {
        val u = x.coerceIn(0.0, 1.0)
        return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
    }

    private fun easeOutBack(x: Double): Double {
        val u = x.coerceIn(0.0, 1.0)
        val c1 = 1.6
        val c3 = c1 + 1
        return 1 + c3 * (u - 1).pow(3) + c1 * (u - 1).pow(2)
    }

    /** The shortest turn from a to b, in degrees. */
    fun turn(a: Double, b: Double): Double {
        var d = (b - a) % 360
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    fun posmod(x: Double, m: Double): Double {
        val r = x % m
        return if (r < 0) r + m else r
    }

    // Orbits

    /** The drums sway ±12° instead of orbiting. */
    private fun sway(t: Double) = 12 * sin(2 * PI * t / LOOP)

    /** A source's idle angle (degrees) on the loop clock t; it repeats every loop. */
    fun idleAngle(i: Int, t: Double): Double {
        val s = sources[i]
        return s.start + 360 * s.turns * t / LOOP + if (i == 3) sway(t) else 0.0
    }

    /** A pulse 0…1 on each beat (a quick rise, an exponential fall), and how far the beat has gone. */
    fun beat(i: Int, t: Double): Pair<Double, Double> {
        val q = posmod(t * sources[i].beat, 1.0)
        return exp(-5 * q) * smoothstep(0.0, 0.04, q) to q
    }

    private fun height(i: Int, t: Double) = 6 * sin(2 * PI * sources[i].bob * t)

    // A scene

    class Orb(
        /** On the floor (x right, z towards the viewer) and its height. */
        var x: Double = 0.0, var z: Double = 0.0, var h: Double = 0.0,
        /** 1, or up to 1.3 while held; how bright its cone is (0…1). */
        var grow: Double = 1.0, var lit: Double = 0.0,
        /** Its spatial place before the stereo mix: radius and angle (degrees). */
        var r: Double = 0.0, var a: Double = 0.0,
    )

    class Scene {
        var yaw = 0.0
        var elevation = ELEVATION
        /** 0 spatial, 1 stereo (may overshoot); the segment indicator's place (0 Stereo … 2 Fixed). */
        var stereo = 0.0
        var segment = 1.0
        val orbs = Array(5) { Orb() }
        /** The source being dragged, its readout's opacity, and a scripted finger's opacity. */
        var held = -1
        var chip = 0.0
        var finger = 0.0
        /** Whether head tracking reads on. */
        var tracking = true
    }

    fun polar(r: Double, a: Double): Pair<Double, Double> {
        val rad = a * PI / 180
        return r * sin(rad) to -r * cos(rad)
    }

    fun unpolar(x: Double, z: Double): Pair<Double, Double> = hypot(x, z) to atan2(x, -z) * 180 / PI

    /** Mixes the spatial place of a source with its place on the stereo line. */
    private fun place(i: Int, r: Double, a: Double, t: Double, m: Double): Orb {
        val (x, z) = polar(r, a)
        val s = sources[i]
        return Orb(
            x = x + (s.pan - x) * m,
            z = z + (STEREO_Z - z) * m,
            h = height(i, t) * (1 - m.coerceIn(0.0, 1.0)),
            r = r,
            a = a,
        )
    }

    /** The loop at time t (0 ≤ t < LOOP). */
    fun auto(t: Double): Scene {
        val sc = Scene()
        // The view turns 25° and back, as if the phone turned.
        if (t >= 4 && t < 5.6) sc.yaw = 25 * sin(PI * (t - 4) / 1.6).pow(2)
        // Stereo at 6.0 (0.6 s), back to spatial at 7.4 (0.6 s, springy).
        var m = 0.0
        if (t >= 6 && t < 7.4) m = easeOutBack((t - 6) / 0.6)
        if (t >= 7.4) m = 1 - easeOutBack((t - 7.4) / 0.6)
        sc.stereo = m
        sc.segment = 1 - m.coerceIn(0.0, 1.0)
        for (i in 0 until 5) {
            var r = sources[i].ring
            var a = idleAngle(i, t)
            if (i == 3 && t >= 1.3 && t < 6.6) {
                // A finger drags the drums from the outer ring to the inner ring on the left (240°).
                val from = idleAngle(3, 1.3)
                val u = easeInOut((t - 1.3) / 1.6)
                r = 152 + (72 - 152) * u
                a = from + turn(from, 240.0) * u
                if (t >= 2.9) a = 240 + sway(t) - sway(2.9)
            }
            val o = place(i, r, a, t, m)
            if (i == 3) {
                val held = smoothstep(1.2, 1.4, t) * (1 - smoothstep(3.3, 3.5, t))
                o.grow = 1 + 0.3 * held
                o.lit = held
                sc.chip = smoothstep(1.3, 1.5, t) * (1 - smoothstep(3.3, 3.6, t))
                sc.finger = smoothstep(1.0, 1.25, t) * (1 - smoothstep(3.3, 3.6, t))
                if (held > 0.01) sc.held = 3
            }
            sc.orbs[i] = o
        }
        return sc
    }

    /** Mixes two scenes (u 0…1) field by field. */
    fun mix(a: Scene, b: Scene, u: Double): Scene {
        fun l(x: Double, y: Double) = x + (y - x) * u
        val s = Scene()
        s.yaw = l(a.yaw, b.yaw)
        s.elevation = l(a.elevation, b.elevation)
        s.stereo = l(a.stereo, b.stereo)
        s.segment = l(a.segment, b.segment)
        for (i in 0 until 5) {
            val p = a.orbs[i]
            val q = b.orbs[i]
            s.orbs[i] = Orb(l(p.x, q.x), l(p.z, q.z), l(p.h, q.h), l(p.grow, q.grow), l(p.lit, q.lit), l(p.r, q.r), p.a + turn(p.a, q.a) * u)
        }
        s.held = if (u < 0.5) a.held else b.held
        s.chip = l(a.chip, b.chip)
        s.finger = l(a.finger, b.finger)
        s.tracking = if (u < 0.5) a.tracking else b.tracking
        return s
    }

    // Touch

    /** The scene under a finger, on the block's clock (seconds since it appeared). */
    class Manual {
        /** The clock time where the idle orbits' phase is 0 (the loop's start they continue from). */
        var anchor = 0.0
        /** Each source's radius and its angle offset from its idle orbit; after a drop the radius eases from snapFrom (0.25 s) to its ring. */
        val radius = DoubleArray(5) { sources[it].ring }
        val offset = DoubleArray(5)
        val snapFrom = DoubleArray(5) { sources[it].ring }
        val snapAt = DoubleArray(5) { -100.0 }
        var yaw = 0.0
        var yawFrom = 0.0
        var yawAt = -100.0
        var elevation = ELEVATION
        /** The mode (0 Stereo, 1 Spatial, 2 Fixed) and the eased change to it. */
        var mode = 1
        var stereoFrom = 0.0
        var stereoTo = 0.0
        var segmentFrom = 1.0
        var segmentTo = 1.0
        var modeAt = -100.0
        /** The source under the finger (−1: none), where it is on the floor, and when it was taken and dropped. */
        var held = -1
        var heldX = 0.0
        var heldZ = 0.0
        var heldAt = -100.0
        var dropped = -1
        var droppedAt = -100.0
    }

    fun scene(m: Manual, t: Double): Scene {
        val sc = Scene()
        val tl = t - m.anchor
        val u = (t - m.modeAt) / 0.6
        sc.stereo = m.stereoFrom + (m.stereoTo - m.stereoFrom) * easeOutBack(u)
        sc.segment = m.segmentFrom + (m.segmentTo - m.segmentFrom) * easeInOut(u)
        sc.yaw = m.yaw + (m.yawFrom - m.yaw) * (1 - easeInOut((t - m.yawAt) / 0.6))
        sc.elevation = m.elevation
        sc.tracking = m.mode != 2
        for (i in 0 until 5) {
            val snap = 1 - (1 - ((t - m.snapAt[i]) / 0.25).coerceIn(0.0, 1.0)).pow(3)
            var r = m.radius[i] + (m.snapFrom[i] - m.radius[i]) * (1 - snap)
            var a = idleAngle(i, tl) + m.offset[i]
            if (m.held == i) {
                val p = unpolar(m.heldX, m.heldZ)
                r = p.first
                a = p.second
            }
            val o = place(i, r, a, tl, sc.stereo)
            var on = 0.0
            if (m.held == i) on = smoothstep(0.0, 0.2, t - m.heldAt)
            if (m.dropped == i) on = 1 - smoothstep(0.0, 0.25, t - m.droppedAt)
            o.grow = 1 + 0.3 * on
            o.lit = on
            sc.orbs[i] = o
        }
        if (m.held >= 0) {
            sc.held = m.held
            sc.chip = smoothstep(0.0, 0.2, t - m.heldAt)
        } else if (m.dropped >= 0 && t - m.droppedAt < 0.3) {
            sc.held = m.dropped
            sc.chip = 1 - smoothstep(0.0, 0.3, t - m.droppedAt)
        }
        return sc
    }

    // Projection

    class View(val cx: Double, val cy: Double, val scale: Double, val yaw: Double, val elevation: Double)

    /** A floor point (x, z) at height h on the screen: X, Y and its depth (positive: towards the viewer). */
    fun project(v: View, x: Double, z: Double, h: Double): DoubleArray {
        val y = v.yaw * PI / 180
        val e = v.elevation * PI / 180
        val xr = x * cos(y) - z * sin(y)
        val zr = x * sin(y) + z * cos(y)
        return doubleArrayOf(v.cx + v.scale * xr, v.cy + v.scale * (zr * sin(e) - h * cos(e)), zr)
    }

    /** A screen point back on the floor (height 0). */
    fun unproject(v: View, sx: Double, sy: Double): Pair<Double, Double> {
        val y = v.yaw * PI / 180
        val e = v.elevation * PI / 180
        val xr = (sx - v.cx) / v.scale
        val zr = (sy - v.cy) / v.scale / sin(e)
        return (xr * cos(y) + zr * sin(y)) to (-xr * sin(y) + zr * cos(y))
    }

    /** The readout for a source at (x, z): its level (0 dB on the inner ring) and its direction. */
    fun readout(x: Double, z: Double): String {
        val r = max(hypot(x, z), 1.0)
        val db = 20 * log10(72 / r)
        val az = atan2(x, -z) * 180 / PI
        val deg = abs(az).roundToInt()
        val side = if (deg == 0 || deg == 180) "" else if (az < 0) " L" else " R"
        return String.format(Locale.US, "%s%.1f dB · %d°%s", if (db < 0) "−" else "+", abs(db), deg, side)
    }

    fun ring(near: Double): Double {
        val best = rings.minByOrNull { abs(it - near) } ?: near
        return if (abs(best - near) <= 12) best else near
    }
}
