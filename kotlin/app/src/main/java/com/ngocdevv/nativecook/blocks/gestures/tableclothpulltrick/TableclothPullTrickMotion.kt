package com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Tablecloth Pull Trick — the autoPlay timeline (LOOP = 9 s) as one pose per time.

   0.0 – 1.4   rest: the cloth lies on the table, its hem hanging over the front edge in soft folds,
               swaying a little (whole-loop sines).
   1.4 – 1.75  grab: an anticipation tug (the hem pulled 10 pt, eased back to 2 pt).
   1.75 – 2.3  the yank (0.55 s): the pull runs as u^1.6 until the whole cloth is over the edge; a
               26 pt wave travels through it toward the front, the hanging part whips down and
               toward the viewer, ghost copies and speed streaks trail it. As its back edge passes
               under each dish, that dish hops 8–12 pt and then jiggles (a damped 5 Hz sine).
   2.6 – 5.0   a "Clean pull" badge; the counter rolls 3 → 4 (2.6 – 2.9).
   5.0 – 7.4   the cloth is thrown back on: it billows down from 480 pt above the table, drawn over
               the dishes while it is above them; the hem swings down over the edge, the air under
               it deflates; the dishes ease home (5.2 – 7.0); the counter rolls back (7.4 – 7.7).
   7.4 – 9.0   rest.
 */

/** The autoPlay loop, seconds (the manifest's `recording.loop`). */
const val LOOP = 9.0

/** One pose of the whole scene. Mutable so a frame reuses it. */
internal class ClothPose {
    /** Pull (pt), its speed (pt/s), ripple amplitude (pt), stream of the hanging part (0 … 1). */
    var d = 0.0
    var speed = 0.0
    var wave = 0.0
    var fling = 0.0
    /** Throw-back: height above the table, shift toward the back, air under the middle, hem angle (1 = hanging). */
    var lift = 0.0
    var back = 0.0
    var air = 0.0
    var hang = 1.0
    /** Common dish drift toward the viewer (pt); the yank's age (s, < 0 = none), where it started, strength. */
    var slide = 0.0
    var yankAge = -1.0
    var yankFrom = 0.0
    var jig = 0.0
    /** Common glass lean (degrees), counter value (rolls), badge (0 … 1), loop time for idle sways. */
    var tip = 0.0
    var count = 3.0
    var badge = 0.0
    var time = 0.0

    fun rest(time: Double, count: Double): ClothPose {
        d = 0.0; speed = 0.0; wave = 0.0; fling = 0.0
        lift = 0.0; back = 0.0; air = 0.0; hang = 1.0
        slide = 0.0; yankAge = -1.0; yankFrom = 0.0; jig = 0.0
        tip = 0.0; this.count = count; badge = 0.0; this.time = time
        return this
    }

    fun copyFrom(o: ClothPose): ClothPose {
        d = o.d; speed = o.speed; wave = o.wave; fling = o.fling
        lift = o.lift; back = o.back; air = o.air; hang = o.hang
        slide = o.slide; yankAge = o.yankAge; yankFrom = o.yankFrom; jig = o.jig
        tip = o.tip; count = o.count; badge = o.badge; time = o.time
        return this
    }
}

internal object ClothMotion {
    /** Table depth (world pt), the hem's drop below the edge, and the cloth's whole length. */
    const val DEPTH = 320.0
    const val DROP = 92.0
    const val CLOTH_LEN = DEPTH + DROP
    /** Pull at which the whole cloth has left the screen; the yank's length (s). */
    const val OUT = CLOTH_LEN + 300
    const val YANK = 0.55
    /** Length of a throw-back (s). */
    const val THROW_BACK = 2.4

    fun clamp(v: Double, a: Double, b: Double) = min(max(v, a), b)
    fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a), 0.0, 1.0)
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    fun hash(i: Double): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /** A bell 0 → 1 → 0 over [a, b]. */
    private fun bell(t: Double, a: Double, b: Double) = sin(PI * seg(t, a, b))

    /** A damped sine after s ≥ 0, windowed to exactly zero at `span`. */
    private fun ring(s: Double, decay: Double, w: Double, span: Double): Double {
        if (s <= 0 || s >= span) return 0.0
        return exp(-decay * s) * sin(w * s) * (1 - s / span).pow(2)
    }

    /** The pull during a yank that started at `from`, `age` seconds ago. */
    fun yankD(from: Double, age: Double): Double {
        val u = clamp(age / YANK, 0.0, 1.0)
        return from + (OUT - from) * u.pow(1.6)
    }

    fun yankSpeed(from: Double, age: Double): Double {
        val u = clamp(age / YANK, 0.0, 1.0)
        return if (u > 0 && u < 1) (OUT - from) * 1.6 * u.pow(0.6) / YANK else 0.0
    }

    /** Seconds into a yank (from `from`) when the cloth's back edge passes under table depth y. */
    fun passTime(y: Double, from: Double): Double {
        val need = clamp((DEPTH - y + 24 - from) / (OUT - from), 0.0, 1.0)
        return YANK * need.pow(1 / 1.6)
    }

    /** The cloth thrown back on, r seconds into THROW_BACK, written into p. */
    fun throwBack(r: Double, p: ClothPose) {
        // Falls in, quickest in the middle, cushioned by the air under it at the end.
        val fall = easeInOut(seg(r, 0.0, 1.4))
        p.air = 42 * (0.55 + 0.45 * sin(PI * seg(r, 0.0, 0.9))) * (1 - seg(r, 1.0, 1.55)) + 10 * ring(r - 1.4, 4.0, 11.0, 1.0)
        // In the air the front edge curls down like a canopy; it swings fully down on landing.
        p.hang = 0.38 + 0.62 * easeOut(seg(r, 1.1, 1.7)) + 0.1 * ring(r - 1.7, 5.0, 12.0, 0.7)
        p.lift = 480 * (1 - fall)
        p.back = 150 * (1 - fall)
        p.wave = 16 * sin(PI * seg(r, 0.0, 1.4))
    }

    fun autoPose(t: Double, p: ClothPose): ClothPose {
        p.rest(t, 3.0)
        if (t >= 1.4 && t < 1.75) {
            p.d = 10 * easeOut(seg(t, 1.4, 1.62)) - 8 * easeInOut(seg(t, 1.62, 1.75))
        } else if (t >= 1.75 && t < 5.0) {
            p.d = yankD(2.0, t - 1.75)
            p.speed = yankSpeed(2.0, t - 1.75)
        } else if (t >= 5.0 && t < 5.0 + THROW_BACK) {
            throwBack(t - 5.0, p)
        }
        if (t >= 1.75 && t < 2.6) {
            p.wave = 26 * bell(t, 1.78, 2.45)
            p.fling = 0.85 * bell(t, 1.85, 2.6)
        }
        if (t >= 1.75 && t < 4.0) {
            p.yankAge = t - 1.75
            p.yankFrom = 2.0
            p.jig = 1.0
        }
        p.slide = 4 * easeOut(seg(t, 1.8, 2.2)) * (1 - easeInOut(seg(t, 5.2, 7.0)))
        p.count = 3 + easeInOut(seg(t, 2.6, 2.9)) - easeInOut(seg(t, 7.4, 7.7))
        p.badge = easeOut(seg(t, 2.6, 2.9)) * (1 - easeInOut(seg(t, 4.6, 5.0)))
        return p
    }

    /** A dish's yank response at table depth y into out: [dx, dy, spin (rad), lean (deg), hop (pt)]. */
    fun jiggle(p: ClothPose, i: Int, y: Double, out: DoubleArray) {
        out.fill(0.0)
        if (p.yankAge < 0 || p.jig <= 0) return
        val a = p.yankAge - passTime(y, p.yankFrom)
        if (a <= 0 || a >= 1.4) return
        val h1 = hash(i + 1.0)
        val h2 = hash(i + 7.0)
        val env = exp(-3.2 * a) * (1 - a / 1.4).pow(2) * p.jig
        val w = 2 * PI * (4.6 + h1 * 1.2) * a
        out[0] = 2.4 * (h1 - 0.5) * env * sin(w + h2 * 6)
        out[1] = -3 * h2 * env * abs(sin(w * 0.5))
        out[2] = 0.12 * (h2 - 0.5) * env * sin(w * 0.8 + 1)
        out[3] = 9 * (0.6 + h1 * 0.4) * env * sin(w + h2 * 3)
        out[4] = (8 + 4 * h2) * sin(PI * clamp(a / 0.22, 0.0, 1.0)) * min(p.jig, 1.4)
    }
}
