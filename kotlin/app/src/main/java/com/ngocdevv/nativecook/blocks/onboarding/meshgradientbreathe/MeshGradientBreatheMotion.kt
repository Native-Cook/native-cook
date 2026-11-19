package com.ngocdevv.nativecook.blocks.onboarding.meshgradientbreathe

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Breathe Mesh: the breath, the two lights it moves between, and the onboarding film, as pure functions of time.
// A port of the React Native breatheMotion.ts (and the Swift MeshGradientBreatheMotion.swift).

internal object MgbMotion {
    /** Seconds: one box breath, and the onboarding's three steps. */
    const val LOOP = 16.0

    class Rhythm(val label: String, val detail: String, val phases: DoubleArray)

    /** in, hold, out, hold (seconds). Box is the film's: 16 s, one loop. */
    val rhythms = listOf(
        Rhythm("Box", "Box breathing", doubleArrayOf(4.0, 4.0, 4.0, 4.0)),
        Rhythm("4-7-8", "4-7-8 breathing", doubleArrayOf(4.0, 7.0, 8.0, 0.0)),
        Rhythm("Even", "Even breathing", doubleArrayOf(5.0, 0.0, 5.0, 0.0)),
    )

    val lengths = intArrayOf(1, 3, 5, 10)

    // ------------------------------------------------------------ the film

    val toStep2 = doubleArrayOf(5.0, 5.7) // Welcome -> Your rhythm
    const val PICK_LENGTH = 7.4 // the length wheel turns from 3 to 5 min
    val toStep3 = doubleArrayOf(9.6, 10.3) // -> Your first session
    const val RESET_LENGTH = 12.4 // the hidden wheel goes back to 3 min
    val toStep1 = doubleArrayOf(15.2, 16.0) // -> Welcome again

    // ------------------------------------------------------------ easing

    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeInOutSine(x: Double) = 0.5 - 0.5 * cos(PI * x)
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInQuad(x: Double) = x * x

    /** Easing.bezier(0.2, 0.8, 0.2, 1). */
    val easeCurve = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    fun ease(x: Double) = easeCurve.transform(clamp01(x).toFloat()).toDouble()

    // ------------------------------------------------------------ colour

    private fun hexToLab(hex: Int, out: FloatArray, at: Int) {
        fun lin(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = lin((hex shr 16) and 255)
        val g = lin((hex shr 8) and 255)
        val b = lin(hex and 255)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        out[at] = (0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s).toFloat()
        out[at + 1] = (1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s).toFloat()
        out[at + 2] = (0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s).toFloat()
    }

    private fun palette(vararg hex: Int): FloatArray {
        val out = FloatArray(hex.size * 3)
        hex.forEachIndexed { i, h -> hexToLab(h, out, i * 3) }
        return out
    }

    // Exhaled: cool and quiet (mist blue, lilac, sage). Inhaled: warm and open (peach, apricot, rose).
    // 5 x 5, rows from the top of the screen.
    private val EXHALED = palette(
        0xC9D3EC, 0xCFD6EE, 0xD8D8EE, 0xDED6EC, 0xE2D5E9,
        0xC3D4E8, 0xB9CBEA, 0xC8C6EA, 0xD9CBE8, 0xE4D3E6,
        0xCFE0E2, 0xBFD5E3, 0xB6C3E6, 0xC9C2E4, 0xDCCFE3,
        0xDCE8DE, 0xCBE0DA, 0xC2D3E2, 0xCCCBE3, 0xDFD6E5,
        0xE8EFE3, 0xDDEAE0, 0xD5E2E6, 0xDADAEA, 0xE6E0EC,
    )
    private val INHALED = palette(
        0xF6D9CF, 0xF8D4C6, 0xF7CFC4, 0xF2CDCB, 0xEDCED4,
        0xF7E0C8, 0xF9CDB4, 0xF6BFAE, 0xEEBFBF, 0xE8C6D2,
        0xF9EBCB, 0xFAD9B2, 0xF8C6A2, 0xF0BCB0, 0xE9C3CC,
        0xF8EFD6, 0xF9E3C0, 0xF7D3B0, 0xF1CCBC, 0xECCFD4,
        0xF7F1E2, 0xF8EAD2, 0xF6DDC4, 0xF2D6CC, 0xEED8DB,
    )

    /**
     * The light's 75 OKLab numbers at fullness [b]: the two grids blended with the chroma kept (a straight Lab
     * blend of a cool and a warm colour passes through grey, so each a,b is rescaled to the blended chroma).
     */
    fun light(b: Double, out: FloatArray) {
        val a = EXHALED
        val c = INHALED
        val f = b.toFloat()
        for (i in 0 until 25) {
            val k = i * 3
            val ca = sqrt(a[k + 1] * a[k + 1] + a[k + 2] * a[k + 2])
            val cb = sqrt(c[k + 1] * c[k + 1] + c[k + 2] * c[k + 2])
            val l = a[k] + (c[k] - a[k]) * f
            val x = a[k + 1] + (c[k + 1] - a[k + 1]) * f
            val y = a[k + 2] + (c[k + 2] - a[k + 2]) * f
            val cc = sqrt(x * x + y * y)
            val s = if (cc > 0.0001f) (ca + (cb - ca) * f) / cc else 1f
            out[k] = l
            out[k + 1] = x * s
            out[k + 2] = y * s
        }
    }

    /**
     * Control-point offsets (50 numbers) at fullness [b] and ambient time [amb] (seconds). The inner points swell
     * away from the orb on the inhale and gather back on the exhale; all of them drift on whole-number
     * frequencies of the loop. Corners stay; edge points slide along their edge. ([cx], [cy]) = the orb's centre
     * in screen fractions.
     */
    fun offsets(b: Double, amb: Double, cx: Double, cy: Double, out: FloatArray) {
        val tau = amb / LOOP * PI * 2
        for (j in 0 until 5) for (i in 0 until 5) {
            val k = (j * 5 + i) * 2
            val gx = i / 4.0
            val gy = j / 4.0
            val s = (i * 5 + j * 11) * 0.41
            var dx = 0.035 * sin(tau * (1 + ((i + j) % 2)) + s)
            var dy = 0.025 * cos(tau * (1 + (j % 2)) + s * 1.3)
            val rx = gx - cx
            val ry = (gy - cy) * 2.1 // the screen is about twice as tall as wide
            val dist = sqrt(rx * rx + ry * ry) + 0.0001
            val near = exp(-dist * 2.2)
            dx += rx / dist * 0.09 * b * near
            dy += ry / dist * 0.09 * b * near / 2.1
            if (i == 0 || i == 4) dx = 0.0
            if (j == 0 || j == 4) dy = 0.0
            out[k] = dx.toFloat()
            out[k + 1] = dy.toFloat()
        }
    }

    // ------------------------------------------------------------ the breath

    class Breath(val full: Double, val phase: Int, val left: Double, val length: Double)

    /**
     * The breath at time [s] (seconds, any range) for rhythm [r]: fullness 0..1 (eased in and out of each hold),
     * the phase index 0..3 and the seconds left in it.
     */
    fun breath(s: Double, r: Int): Breath {
        val p = rhythms[r].phases
        val per = p[0] + p[1] + p[2] + p[3]
        var x = ((s % per) + per) % per
        var k = 0
        while (k < 3 && (p[k] == 0.0 || x >= p[k])) {
            x -= p[k]
            k++
        }
        val f = if (p[k] > 0) x / p[k] else 0.0
        val full = when (k) {
            0 -> easeInOutSine(f)
            1 -> 1.0
            2 -> 1 - easeInOutSine(f)
            else -> 0.0
        }
        return Breath(full, k, p[k] - x, p[k])
    }

    /**
     * Visibility of step [k] (0..2) at loop time [t]. A change is sequential, never a crossfade: the old step
     * fades out over the first 45 % of the transition (ease-in) and the new one fades in over the last 55 %
     * (ease-out cubic), so two texts are never on screen together.
     */
    fun stepVis(k: Int, t: Double): Double {
        val tr = arrayOf(toStep2, toStep3, toStep1)
        val from = intArrayOf(0, 1, 2)
        val to = intArrayOf(1, 2, 0)
        var v = if (k == 0) 1.0 else 0.0
        for (j in 0 until 3) {
            val a = tr[j][0]
            val b = tr[j][1]
            val mid = a + (b - a) * 0.45
            val o = seg(t, a, mid)
            val inn = easeOutCubic(seg(t, mid, b))
            if (from[j] == k && t >= a) v = 1 - o * o
            if (to[j] == k && t >= mid) v = inn
        }
        return v
    }

    /** Which step the film is on (the one whose content is interactive). */
    fun filmStep(t: Double): Int = when {
        t < toStep2[0] + 0.35 -> 0
        t < toStep3[0] + 0.35 -> 1
        t < toStep1[0] + 0.4 -> 2
        else -> 0
    }

    fun filmLength(t: Double): Int = if (t >= PICK_LENGTH && t < RESET_LENGTH) 5 else 3
}
