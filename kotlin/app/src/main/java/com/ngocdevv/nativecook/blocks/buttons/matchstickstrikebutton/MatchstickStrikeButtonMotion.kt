package com.ngocdevv.nativecook.blocks.buttons.matchstickstrikebutton

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Matchstick Strike Button — timing and the match's physics, ported from matchstickMotion.ts. Every
 frame of the self-playing loop is a pure function of the loop time t; under a finger a small
 simulation (MatchSim) is stepped once per frame and read through the same Pose.

 LOOP = 8 s:
   0.0 – 1.3   rest (the poster: a fresh match lying above the box, "Strike to start" printed in it)
   1.3 – 1.7   the match lifts and moves to the right end of the striker (it pulls back 8 dp at 1.62)
   1.7 – 2.2   strike: the head drags right → left across the strip, sparks fly back and fall
   2.2 – 2.6   flare: a fireball, a sulphur puff, then a steady teardrop flame
   2.6 – 2.9   the match swings down under the CTA      2.9 – 4.4  it glides under the letters,
               which ignite left → right as the flame passes (white flash, then a gold glow)
   4.4 – 5.8   hold: the flame flickers, the whole screen warms
   5.8 – 6.15  a gust: the flame leans hard and goes out; smoke curls up from 5.95
   6.0 – 6.9   the letters cool back to print       6.8 – 7.2  the burnt match goes back to rest
   7.2 – 7.6   it drops behind the box                7.6 – 8.0  a fresh one rises from the box
*/
internal object MatchstickMotion {
    const val LOOP = 8.0
    const val IDLE_BEFORE_BACK = 2.0
    const val BACK_FOR = 0.9
    /** A swipe on the strip lights the match above this speed (dp/s) once it has run this far (dp). */
    const val STRIKE_SPEED = 650f
    const val STRIKE_RUN = 56f
    const val MAX_SPARKS = 32
    private const val SPARKS_AUTO = 26

    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeOut(x: Double): Double {
        val u = 1 - clamp01(x)
        return 1 - u * u * u
    }
    fun easeInOut(x: Double): Double {
        val v = clamp01(x)
        return if (v < 0.5) 4 * v * v * v else 1 - (-2 * v + 2).pow(3) / 2
    }
    fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
    fun hash(n: Double): Double {
        val s = sin(n * 12.9898 + 4.1414) * 43758.5453
        return s - floor(s)
    }

    class Head(val x: Double, val y: Double, val angle: Double, val lift: Double)

    /** Where the head is during the loop (no lean or flame, just the path). */
    fun headAt(t: Double, g: MatchGeo): Head {
        if (t < 1.3) return Head(g.restX, g.restY, 0.0, 0.0)
        if (t < 1.7) {
            // Lift to the strip's right end, with a short pull-back before the strike (anticipation).
            val k = easeInOut(seg(t, 1.3, 1.62))
            val back = sin(PI * seg(t, 1.56, 1.7)) * 8
            return Head(mix(g.restX, g.strikeFromX, k) + back, mix(g.restY, g.strikeFromY, k) - sin(PI * k) * 26, mix(0.0, -22.0, k), k)
        }
        if (t < 2.2) {
            val k = easeInOut(seg(t, 1.7, 2.2))
            return Head(mix(g.strikeFromX, g.strikeToX, k), g.strikeFromY + sin(PI * k) * 1.5, -22.0, 1.0)
        }
        if (t < 2.6) {
            // The flare pulls the match up off the strip.
            val k = easeOut(seg(t, 2.2, 2.6))
            return Head(g.strikeToX, g.strikeToY - 14 * k, -22 - 6 * k, 1.0)
        }
        if (t < 2.9) {
            val k = easeInOut(seg(t, 2.6, 2.9))
            return Head(mix(g.strikeToX, g.glideFromX, k), mix(g.strikeToY - 14, g.glideFromY, k), mix(-28.0, 28.0, k), 1.0)
        }
        if (t < 6.8) {
            val k = easeInOut(seg(t, 2.9, 4.4))
            return Head(mix(g.glideFromX, g.glideToX, k), g.glideFromY - sin(PI * k) * 4, 28.0, 1.0)
        }
        if (t < 7.2) {
            val k = easeInOut(seg(t, 6.8, 7.2))
            return Head(mix(g.glideToX, g.restX, k), mix(g.glideToY, g.restY, k) - sin(PI * k) * 30, mix(28.0, 0.0, k), 1 - k)
        }
        if (t < 7.6) {
            val k = easeInOut(seg(t, 7.2, 7.6))
            return Head(g.restX, g.restY + 74 * k, 0.0, 0.0)
        }
        val k = easeOut(seg(t, 7.6, 8.0))
        return Head(g.restX, g.restY + 74 * (1 - k), 0.0, 0.0)
    }

    /** When each letter catches in the loop: the head passes under its centre. */
    fun igniteTimes(g: MatchGeo): DoubleArray = DoubleArray(g.letters.size) { n ->
        val x = g.letters[n]
        var lo = 2.9
        var hi = 4.4
        repeat(24) {
            val m = (lo + hi) / 2
            if (headAt(m, g).x < x - 4) lo = m else hi = m
        }
        hi
    }

    /** A letter's heat from the moment it caught (and, when cooling, the moment it started to). */
    fun heatOf(age: Double, coolAge: Double): Double {
        if (age < 0) return 0.0
        val h = easeOut(age / 0.22) + exp(-age * 7) * 0.6 * clamp01(age / 0.04)
        return if (coolAge < 0) h else h * (1 - easeInOut(coolAge / 0.8))
    }

    /** The sparks the loop's strike throws: each a pure function of t and its index. */
    private fun loopSparks(t: Double, g: MatchGeo, out: MatchSparks) {
        if (t < 1.7 || t > 3.0) return
        for (i in 0 until SPARKS_AUTO) {
            val born = 1.78 + 0.4 * i / SPARKS_AUTO
            val life = 0.28 + 0.3 * hash(i * 3.1)
            val age = t - born
            if (age < 0 || age > life) continue
            val at = headAt(born, g)
            // Thrown back against the stroke (to the right) and up, then falling.
            val ang = (-150 + 120 * hash(i + 7.7)) * (PI / 180)
            val sp = 140 + 300 * hash(i * 1.9 + 2.2)
            out.add(at.x - 6, at.y, -cos(ang) * sp, sin(ang) * sp, age, life)
        }
    }

    /** The pose of the self-playing loop at loop time t. */
    fun loopPose(t: Double, g: MatchGeo, ignite: DoubleArray): MatchPose {
        val p = MatchPose(ignite.size)
        val h = headAt(t, g)
        // Lean against the motion: a finite difference of the path (it is smooth).
        val h2 = headAt(max(0.0, t - 0.04), g)
        var lean = max(-1.0, min(1.0, -(h.x - h2.x) / 0.04 / 520))
        val flare = if (t > 2.12 && t < 2.8) exp(-((t - 2.26) / 0.13).pow(2)) else 0.0
        var lit = easeOut(seg(t, 2.14, 2.5))
        val out = seg(t, 5.8, 6.15)
        lit *= 1 - out * out
        lean = lean * (1 - out) - out * 0.9
        if (t >= 6.15) lit = 0.0
        for (i in ignite.indices) p.heat[i] = heatOf(t - ignite[i], t - (6.0 + 0.03 * i))
        loopSparks(t, g, p.sparks)
        val m = p.match
        m.x = h.x
        m.y = h.y
        m.angle = h.angle
        m.lift = h.lift
        m.burnt = if (t < 7.6) easeOut(seg(t, 2.2, 2.7)) else 0.0
        m.char = if (t < 7.6) seg(t, 2.6, 6.0) else 0.0
        m.alpha = 1.0
        m.front = if (t >= 7.2 && t < 8) 0.0 else 1.0
        p.lit = lit
        p.flare = flare
        p.lean = lean
        p.light = lit * 0.85 + flare * 0.9
        p.puff.set(g.strikeToX - 2, g.strikeToY - 6, t - 2.2, if (t > 2.2) 1 - seg(t, 2.6, 3.8) else 0.0)
        p.curl.set(g.glideToX, g.glideToY - 8, t - 5.95, if (t > 5.95) 1 - seg(t, 7.0, 7.8) else 0.0)
        p.scrape.set(g.strikeToX, g.strikeFromX, if (t > 1.75 && t < 7.6) 0.45 * seg(t, 1.75, 2.2) * (1 - seg(t, 6.4, 7.6)) else 0.0)
        p.phase = 2 * PI * t / LOOP
        return p
    }

    /** The pose under (or after) a finger at clock time [now]. */
    fun simPose(s: MatchSim, now: Double, g: MatchGeo): MatchPose {
        val p = MatchPose(s.ignite.size)
        var lit = 0.0
        var flare = 0.0
        if (s.litAt >= 0) {
            val a = now - s.litAt
            lit = easeOut(a / 0.36)
            flare = exp(-((a - 0.12) / 0.13).pow(2))
        }
        var lean = s.lean
        if (s.outAt >= 0) {
            val o = seg(now, s.outAt, s.outAt + 0.3)
            lit *= 1 - o * o
            lean = lean * (1 - o) - 0.9 * o
            flare = 0.0
        }
        for (i in s.ignite.indices) p.heat[i] = if (s.ignite[i] < 0) 0.0 else heatOf(now - s.ignite[i], if (s.cool < 0) -1.0 else now - s.cool)
        for (i in 0 until s.sparks.count) {
            val o = i * 6
            val v = s.sparks.v
            val age = now - v[o + 4]
            if (age >= 0 && age < v[o + 5]) p.sparks.add(v[o], v[o + 1], v[o + 2], v[o + 3], age, v[o + 5])
        }
        val m = p.match
        m.x = s.x
        m.y = s.y
        m.angle = s.angle
        m.lift = s.lift
        m.burnt = s.burnt
        m.char = if (s.litAt >= 0) clamp01((now - s.litAt) / 3.4) else 0.0
        m.alpha = 1.0
        m.front = 1.0
        val back = s.back
        if (s.backAt >= 0 && back != null) {
            // Easing back: the used match drifts toward rest and fades, a fresh one fades in there.
            val k = seg(now, s.backAt, s.backAt + BACK_FOR)
            val e = easeInOut(seg(now, s.backAt + 0.2, s.backAt + 0.7))
            m.copy(back)
            m.x = mix(back.x, g.restX, e)
            m.y = mix(back.y, g.restY, e)
            m.angle = mix(back.angle, 0.0, e)
            m.lift = back.lift * (1 - e)
            m.alpha = 1 - e
            p.spare = easeInOut(seg(k, 0.5, 1.0))
        }
        val sa = if (s.litAt >= 0) now - s.litAt else -1.0
        val oa = if (s.outAt >= 0) now - s.outAt else -1.0
        p.lit = lit
        p.flare = flare
        p.lean = lean
        p.light = lit * 0.85 + flare * 0.9
        p.puff.set(s.px, s.py, sa, if (sa >= 0) 1 - seg(sa, 0.4, 1.6) else 0.0)
        p.curl.set(back?.x ?: m.x, (back?.y ?: m.y) - 8, oa, if (oa >= 0) 1 - seg(oa, 0.9, 1.6) else 0.0)
        p.scrape.set(s.scrape[0], s.scrape[1], if (s.scrape[2] < -50) 0.0 else 0.55 * (1 - seg(now, s.scrape[2] + 0.5, s.scrape[2] + 3)))
        p.phase = s.phase
        return p
    }
}

/** Layout the motion needs, in dp. */
internal class MatchGeo(
    val restX: Double, val restY: Double,
    val strikeFromX: Double, val strikeFromY: Double,
    val strikeToX: Double, val strikeToY: Double,
    val glideFromX: Double, val glideFromY: Double,
    val glideToX: Double, val glideToY: Double,
    val strikerTop: Double, val strikerBottom: Double, val strikerLeft: Double, val strikerRight: Double,
    val boxTop: Double,
    /** Letter centres (x) and the CTA's baseline. */
    val letters: DoubleArray,
    val baseline: Double,
    val matchLength: Double,
)

internal class MatchState {
    var x = 0.0
    var y = 0.0
    /** Direction from the head to the tail, degrees (0 = tail to the right). */
    var angle = 0.0
    /** 0 lying on the table … 1 held up (bigger, softer shadow). */
    var lift = 0.0
    /** 0 fresh red head … 1 charred head. */
    var burnt = 0.0
    /** How far the stick has charred below the head, 0 … 1. */
    var char = 0.0
    var alpha = 1.0
    /** 1 = drawn over the box, 0 = behind it. */
    var front = 1.0

    fun copy(o: MatchState) {
        x = o.x; y = o.y; angle = o.angle; lift = o.lift; burnt = o.burnt; char = o.char; alpha = o.alpha; front = o.front
    }
}

/** Four numbers (a smoke column: x, y, age, strength; or a scrape: x from, x to, darkness). */
internal class MatchQuad {
    val v = DoubleArray(4)
    operator fun get(i: Int) = v[i]
    fun set(a: Double, b: Double, c: Double, d: Double = 0.0) {
        v[0] = a; v[1] = b; v[2] = c; v[3] = d
    }
}

/** Sparks, 6 numbers each: x, y, vx, vy, age (or birth time), life. */
internal class MatchSparks(capacity: Int = 40) {
    var v = DoubleArray(capacity * 6)
    var count = 0
    fun add(x: Double, y: Double, vx: Double, vy: Double, a: Double, life: Double) {
        if ((count + 1) * 6 > v.size) v = v.copyOf(v.size * 2)
        val o = count * 6
        v[o] = x; v[o + 1] = y; v[o + 2] = vx; v[o + 3] = vy; v[o + 4] = a; v[o + 5] = life
        count += 1
    }
    fun dropFirst() {
        if (count == 0) return
        System.arraycopy(v, 6, v, 0, (count - 1) * 6)
        count -= 1
    }
}

internal class MatchPose(letters: Int) {
    val match = MatchState()
    /** A fresh match fading in at rest (only while easing back after a touch). */
    var spare = 0.0
    /** Flame: size 0 … 1, flare 0 … 1, lean -1 … 1. */
    var lit = 0.0
    var flare = 0.0
    var lean = 0.0
    /** Warm light over the scene. */
    var light = 0.0
    val sparks = MatchSparks()
    val puff = MatchQuad()
    val curl = MatchQuad()
    /** Heat per letter: 0 printed, 1 glowing gold, above 1 the white flash of catching. */
    val heat = DoubleArray(letters)
    val scrape = MatchQuad()
    /** Flicker phase (radians). */
    var phase = 0.0
}

/** The finger's simulation, stepped once per frame. */
internal class MatchSim(letters: Int) {
    var x = 0.0
    var y = 0.0
    var angle = 0.0
    var lift = 0.0
    var burnt = 0.0
    var litAt = -1.0
    var outAt = -1.0
    var lean = 0.0
    var vx = 0.0
    var run = 0.0
    val sparks = MatchSparks()
    val scrape = doubleArrayOf(0.0, 0.0, -99.0)
    val ignite = DoubleArray(letters) { -1.0 }
    var cool = -1.0
    var backAt = -1.0
    var back: MatchState? = null
    var phase = 0.0
    /** Where the match caught (the sulphur puff rises from there). */
    var px = 0.0
    var py = 0.0

    fun addSpark(x: Double, y: Double, dir: Double, at: Double) {
        val n = sparks.count.toDouble()
        val ang = (-150 + 120 * MatchstickMotion.hash(n + at * 7.3)) * (PI / 180)
        val sp = 140 + 300 * MatchstickMotion.hash(n * 1.9 + at)
        sparks.add(x, y, -dir * cos(ang) * sp, sin(ang) * sp, at, 0.28 + 0.3 * MatchstickMotion.hash(n * 3.1 + at))
        if (sparks.count > MatchstickMotion.MAX_SPARKS) sparks.dropFirst()
    }

    /** One frame of the held match: it rises, turns, and its flame leans against the motion. */
    fun step(at: Double, dt: Double, held: Boolean) {
        val lit = litAt >= 0 && outAt < 0
        // Held, the match rises off the table and turns: tail up while striking, down once lit.
        val target = if (held || lift > 0.01) 1.0 else 0.0
        lift += (target - lift) * (1 - exp(-dt * 10))
        val a = if (lit) 28.0 else -22.0
        angle += (a - angle) * (1 - exp(-dt * 6))
        vx *= exp(-dt * 6)
        val l = max(-1.0, min(1.0, -vx / 520))
        lean += (l - lean) * (1 - exp(-dt * 12))
        if (litAt >= 0) burnt = MatchstickMotion.easeOut((at - litAt) / 0.4)
        phase = 2 * PI * at / MatchstickMotion.LOOP
    }
}
