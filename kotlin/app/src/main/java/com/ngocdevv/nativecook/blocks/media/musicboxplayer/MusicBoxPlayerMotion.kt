package com.ngocdevv.nativecook.blocks.media.musicboxplayer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Music Box Player — the motion, ported from musicBoxMotion.ts. The cylinder's angle is the only
 clock: pins ride on it, a pin that passes the comb's tips plucks its tooth (the tip bends back as
 the pin nears, then rings, decaying), and each pluck lets a note float up. The crank turns 4 times
 per turn of the cylinder, the governor's fan 40 times.

 autoPlay (LOOP = 8 s):
   0.0 – 1.2   rest: pins at the start of the song, playhead at 0:00.
   1.2 – 6.4   the crank turns: speeds up in 0.4 s, steady, then slows to a stop over the last 1 s
               (the cylinder makes exactly one turn, so the song plays once: 8 bars of 3/4).
   6.15        the last chord; it rings until 7.0.
   7.0 – 7.6   the playhead rolls back to 0:00. 8.0 is the same frame as 0.0.
*/

internal const val LOOP = 8.0
internal const val MUSICBOX_TEETH = 18
/** Beats in one turn of the cylinder. */
private const val SLOTS = 24.5
/** Pin angle where the comb's tips are (0 faces the viewer, +90° is the bottom silhouette). */
internal const val MUSICBOX_CONTACT = 55 * PI / 180
internal const val MUSICBOX_CRANK_RATIO = 4.0
internal const val MUSICBOX_SONG_SECONDS = 52
private const val PUSH = 0.2
/** A note floats this long. */
internal const val MUSICBOX_NOTE_LIFE = 1.7

internal class MusicBoxGeo(
    val w: Double,
    val h: Double,
    /** Cylinder: left, right, centre y, radius. Comb tips y, base y. Crank hub, arm. */
    val cylX0: Double,
    val cylX1: Double,
    val cylY: Double,
    val cylR: Double,
    val tipY: Double,
    val combY: Double,
    val hubX: Double,
    val hubY: Double,
    val arm: Double,
)

/** A frame: cylinder, crank and fan angles; per tooth its tip offset (dp) and glow; floating notes (x, y, scale, alpha, tilt); the playhead. */
internal class MusicBoxPose {
    var angle = 0.0
    var crank = 0.0
    var fan = 0.0
    val tips = DoubleArray(MUSICBOX_TEETH)
    val glow = DoubleArray(MUSICBOX_TEETH)
    val notes = DoubleArray(64 * 5)
    var noteCount = 0
    var progress = 0.0
}

internal object MusicBoxMotion {
    /** Beat and tooth of every pin. */
    val PINS: IntArray = run {
        val melody = intArrayOf(9, 11, 13, 14, 13, 11, 12, 10, 9, 11, 9, 8, 9, 11, 13, 16, 14, 13, 12, 11, 10, 9)
        val bass = intArrayOf(2, 4, 0, 3, 2, 5, 1, 2)
        val pins = ArrayList<Int>()
        for (b in melody.indices) {
            pins.add(b)
            pins.add(melody[b])
        }
        for (k in bass.indices) {
            pins.add(k * 3)
            pins.add(bass[k])
        }
        // The final chord, late in the turn, while the crank slows.
        pins.addAll(listOf(23, 9, 23, 14, 23, 2))
        pins.toIntArray()
    }
    val PIN_COUNT = PINS.size / 2

    /** A pin's angle on the cylinder at rest (the cylinder at angle 0). */
    fun pinAngle(p: Int) = MUSICBOX_CONTACT - (2 * PI * (PINS[p * 2] + 0.5)) / SLOTS

    private fun smooth(x: Double): Double {
        val k = min(1.0, max(0.0, x))
        return k * k * (3 - 2 * k)
    }

    /** The crank's share of one turn done at loop time t, tabulated (0.01 s). */
    private val TURNS: DoubleArray = run {
        val n = 801
        val v = DoubleArray(n) {
            val t = it.toDouble() / (n - 1) * LOOP
            smooth((t - 1.2) / 0.4) * (1 - smooth((t - 5.4) / 1.0))
        }
        val s = DoubleArray(n)
        for (i in 1 until n) s[i] = s[i - 1] + (v[i] + v[i - 1]) / 2
        val total = s[n - 1]
        DoubleArray(n) { s[it] / total }
    }

    fun turnsAt(t: Double): Double {
        val f = min(LOOP, max(0.0, t)) / LOOP * (TURNS.size - 1)
        val i = min(TURNS.size - 2, floor(f).toInt())
        return TURNS[i] + (TURNS[i + 1] - TURNS[i]) * (f - i)
    }

    /** When each pin plucks in the loop (the inverse of turnsAt). */
    val PLUCKS: DoubleArray = DoubleArray(PIN_COUNT) { p ->
        val want = (PINS[p * 2] + 0.5) / SLOTS
        var i = 0
        while (i < TURNS.size - 1 && TURNS[i + 1] < want) i += 1
        val f = if (TURNS[i + 1] - TURNS[i] > 1e-9) (want - TURNS[i]) / (TURNS[i + 1] - TURNS[i]) else 0.0
        (i + f) / (TURNS.size - 1) * LOOP
    }

    fun toothX(g: MusicBoxGeo, j: Int): Double {
        val m = 12.0
        return g.cylX0 + m + (g.cylX1 - g.cylX0 - m * 2) * (j + 0.5) / MUSICBOX_TEETH
    }

    private fun seg01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x

    fun hash(i: Double): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /** How far (rad) pin p still has to go to reach the comb at cylinder angle a; in 0..2π. */
    private fun toGo(p: Int, a: Double): Double {
        val d = MUSICBOX_CONTACT - (pinAngle(p) + a)
        val k = 2 * PI
        return ((d % k) + k) % k
    }

    /** Tooth j rings e seconds after its pluck. */
    private fun ring(j: Int, e: Double): Double {
        if (e < 0 || e > 1.8) return 0.0
        val f = 7 + (MUSICBOX_TEETH - j) * 0.35
        return 2.6 * exp(-e / 0.42) * cos(2 * PI * f * e)
    }

    /** One floating note, e seconds after the pluck of a tooth at x. */
    private fun pushNote(out: MusicBoxPose, g: MusicBoxGeo, x: Double, e: Double, seed: Double) {
        if (e < 0 || e > MUSICBOX_NOTE_LIFE || out.noteCount * 5 >= out.notes.size) return
        val y = g.tipY - 14 - 118 * e + 8 * e * e
        val sway = 10 * sin(2 * PI * (0.7 * e + hash(seed))) * min(1.0, e * 2)
        val a = min(1.0, e / 0.12) * (1 - max(0.0, (e - 1.1) / 0.6))
        val o = out.noteCount * 5
        out.notes[o] = x + sway + (hash(seed + 3) - 0.5) * 30 * e
        out.notes[o + 1] = y
        out.notes[o + 2] = 0.75 + 0.35 * min(1.0, e / 0.4)
        out.notes[o + 3] = a
        out.notes[o + 4] = (hash(seed + 5) - 0.5) * 30
        out.noteCount += 1
    }

    /**
     * A frame from the cylinder angle `a` (rad, unwrapped), the crank angle, the time, each tooth's
     * last pluck (`last[j]`, -1 for none) and the recent plucks as (tooth, time) pairs.
     */
    fun poseFrom(g: MusicBoxGeo, a: Double, crank: Double, now: Double, last: DoubleArray, recent: DoubleArray, recentCount: Int, progress: Double, out: MusicBoxPose): MusicBoxPose {
        for (j in 0 until MUSICBOX_TEETH) {
            val e = if (last[j] >= 0) now - last[j] else -1.0
            var off = ring(j, e)
            // A pin closing in pushes the tip away from the cylinder before it lets go.
            for (p in 0 until PIN_COUNT) {
                if (PINS[p * 2 + 1] != j) continue
                val d = toGo(p, a)
                if (d < PUSH) off += 3 * (1 - d / PUSH)
            }
            out.tips[j] = off
            out.glow[j] = if (e >= 0 && e < 1.5) exp(-e / 0.5) else 0.0
        }
        out.noteCount = 0
        for (i in 0 until recentCount) {
            val j = recent[i * 2].toInt()
            val tp = recent[i * 2 + 1]
            pushNote(out, g, toothX(g, j), now - tp, j * 7 + tp * 3.1)
        }
        out.angle = a
        out.crank = crank
        out.fan = a * 40
        out.progress = progress
        return out
    }

    private val loopLast = DoubleArray(MUSICBOX_TEETH)
    private val loopRecent = DoubleArray(PIN_COUNT * 2)

    fun loopPose(g: MusicBoxGeo, t: Double, out: MusicBoxPose): MusicBoxPose {
        val turns = turnsAt(t)
        val a = turns * 2 * PI
        java.util.Arrays.fill(loopLast, -1.0)
        var n = 0
        for (p in 0 until PIN_COUNT) {
            val tp = PLUCKS[p]
            if (tp <= t) {
                val j = PINS[p * 2 + 1]
                if (tp > loopLast[j]) loopLast[j] = tp
                if (t - tp < MUSICBOX_NOTE_LIFE) {
                    loopRecent[n * 2] = j.toDouble()
                    loopRecent[n * 2 + 1] = tp
                    n += 1
                }
            }
        }
        val back = seg01((t - 7.0) / 0.6)
        val progress = turns * (1 - back * back * (3 - 2 * back))
        return poseFrom(g, a, a * MUSICBOX_CRANK_RATIO, t, loopLast, loopRecent, n, progress, out)
    }

    /** Pins that pass the comb between cylinder angles a0 and a1 (a1 > a0): calls [hit] with each. */
    inline fun passed(a0: Double, a1: Double, hit: (Int) -> Unit) {
        for (p in 0 until PIN_COUNT) {
            val d = toGoPublic(p, a0)
            if (d > 0 && d <= a1 - a0) hit(p)
        }
    }

    fun toGoPublic(p: Int, a: Double) = toGo(p, a)
}
