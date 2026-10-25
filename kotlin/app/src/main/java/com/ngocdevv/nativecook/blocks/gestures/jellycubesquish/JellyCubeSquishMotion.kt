package com.ngocdevv.nativecook.blocks.gestures.jellycubesquish

import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.HALF
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.RIGHT
import com.ngocdevv.nativecook.blocks.gestures.jellycubesquish.JellyMath.clamp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/*
 Jelly Cube Squish — the autoPlay timeline (LOOP = 8 s). Every wobble is a damped sine windowed
 to reach exactly zero before the loop ends, so t = 8 is t = 0.

   0.0 – 1.2   rest (a faint breathing squash, whole-loop sine).
   1.1 – 1.2   a finger appears on the front face; 1.2 – 1.45 it pokes in (dent 30 pt, ease-out,
               squash 7 %); 1.45 it lets go: the dent springs back and the cube wobbles, gone by 2.6.
   2.6 – 3.6   flavour strawberry → lime: the new colour fills from the plate up with a wavy,
               gooey front (ease-in-out), a small jiggle; the name rolls (2.6 – 2.9).
   3.9 – 5.4   a finger takes the top front corner, pulls it up and right 40 pt (4.0 – 5.0,
               ease-in-out), holds; 5.4 it lets go: the stretch springs back with lean and squash
               wobbles, settled by 7.4.
   6.8 – 7.8   back to strawberry (same fill), the name rolls back (6.8 – 7.1).
   7.8 – 8.0   rest.
 */

/** The autoPlay loop, seconds (the manifest's `recording.loop`). */
const val LOOP = 8.0

internal class JellyFlavour(val name: String, val r: Double, val g: Double, val b: Double)

internal val JellyFlavours = arrayOf(
    JellyFlavour("Strawberry", 0.93, 0.2, 0.3),
    JellyFlavour("Lime", 0.55, 0.82, 0.22),
    JellyFlavour("Mango", 0.97, 0.62, 0.2),
)

/** Colour state: from, to (flavour indices), fill 0 … 1, name roll 0 … 1. */
internal class JellyColour {
    var from = 0
    var to = 0
    var fill = 0.0
    var roll = 0.0

    fun set(from: Int, to: Int, fill: Double, roll: Double): JellyColour {
        this.from = from
        this.to = to
        this.fill = fill
        this.roll = roll
        return this
    }
}

internal object JellyMotion {
    /** The poke point (front face) and the grab point (top front corner), world. */
    const val POKE_X = 18.0
    const val POKE_Y = -HALF
    const val POKE_Z = HALF * 0.95
    const val GRAB_X = HALF * 0.45
    const val GRAB_Y = -HALF * 0.55
    const val GRAB_Z = HALF * 2
    private const val PULL_RIGHT = 30.0
    private const val PULL_UP = 26.0

    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a), 0.0, 1.0)
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    /** A damped sine (or cosine) after time s ≥ 0, windowed to zero at `span`. */
    private fun ring(s: Double, decay: Double, w: Double, span: Double, cosine: Boolean = false): Double {
        if (s < 0 || s >= span) return 0.0
        val win = (1 - s / span).pow(2)
        return exp(-decay * s) * (if (cosine) cos(w * s) else sin(w * s)) * win
    }

    /** The deformation at loop time t, into d. */
    fun autoDeform(t: Double, d: JellyDeform): JellyDeform {
        val tau = 2 * PI
        d.rest()
        d.sq = 0.008 * sin(tau * 2 * t / LOOP)
        // Poke.
        if (t >= 1.2 && t < 2.8) {
            d.px = POKE_X
            d.py = POKE_Y
            d.pz = POKE_Z
            d.nx = 0.0
            d.ny = 1.0
            d.nz = 0.0
            if (t < 1.45) {
                val e = easeOut(seg(t, 1.2, 1.45))
                d.dent = 30 * e
                d.sq += 0.07 * e
                d.ly = 3 * e
            } else {
                val s = t - 1.45
                d.dent = 30 * ring(s, 7.0, 20.0, 1.0, true)
                d.sq += 0.07 * ring(s, 4.5, 15.0, 1.15, true)
                d.ly = 3 * ring(s, 3.5, 11.0, 1.15, true) + 9 * ring(s, 3.5, 11.0, 1.15)
            }
        }
        // Jiggles when the colour changes.
        d.sq += 0.035 * ring(t - 2.6, 5.0, 16.0, 0.9) + 0.03 * ring(t - 6.8, 5.0, 16.0, 0.9)
        // Drag-stretch.
        if (t >= 3.9 && t < 7.6) {
            d.px = GRAB_X
            d.py = GRAB_Y
            d.pz = GRAB_Z
            val k: Double
            var lean = 0.0
            if (t < 5.4) {
                // A 4 % dip the other way first (anticipation), then the pull.
                val u = seg(t, 4.0, 5.0)
                k = easeInOut(u) - 0.04 * sin(PI * seg(t, 4.0, 4.25))
                d.sq += -0.04 * easeInOut(u)
            } else {
                val s = t - 5.4
                k = ring(s, 3.8, 12.0, 2.0, true)
                lean = ring(s, 3.0, 10.0, 2.0)
                d.sq += 0.06 * ring(s, 4.0, 13.0, 2.0)
            }
            d.sx = RIGHT[0] * PULL_RIGHT * k
            d.sy = RIGHT[1] * PULL_RIGHT * k
            d.sz = PULL_UP * k
            d.lx += -14 * lean * RIGHT[0]
            d.ly += -14 * lean * RIGHT[1]
        }
        return d
    }

    /** Colour state at loop time t. */
    fun autoColour(t: Double, c: JellyColour): JellyColour = when {
        t < 2.6 -> c.set(0, 0, 0.0, 0.0)
        t < 6.8 -> c.set(0, 1, easeInOut(seg(t, 2.6, 3.6)), easeInOut(seg(t, 2.6, 2.9)))
        else -> c.set(1, 0, easeInOut(seg(t, 6.8, 7.8)), easeInOut(seg(t, 6.8, 7.1)))
    }

    /** The demo finger: [world x, y, z, alpha] into out. */
    fun autoFinger(t: Double, d: JellyDeform, out: DoubleArray) {
        out.fill(0.0)
        if (t >= 1.1 && t < 1.6) {
            out[0] = POKE_X
            out[1] = POKE_Y + d.dent
            out[2] = POKE_Z
            out[3] = seg(t, 1.1, 1.2) * (1 - seg(t, 1.45, 1.6))
        } else if (t >= 3.9 && t < 5.55) {
            out[0] = GRAB_X + d.sx
            out[1] = GRAB_Y + d.sy
            out[2] = GRAB_Z + d.sz
            out[3] = seg(t, 3.9, 4.0) * (1 - seg(t, 5.4, 5.55))
        }
    }
}
