package com.ngocdevv.nativecook.blocks.media.shakedeveloppolaroid

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 The motion of Shake Develop Polaroid, as functions of time (a port of ShakeDevelopPolaroidModel.swift;
 the numbers are the same).

 Development d (0 = just ejected, 1 = developed) drives the photo's look:
   colour     a 4 × 5 matrix: saturation 0.1 → 1, contrast 0.25 → 1 about mid-grey, a milky lift of
              0.18 and a cool cast (−0.02, +0.01, +0.03) that both fade out; all with e = smoothstep(d).
   haze       a milky #D7DDDA veil over the photo that lifts from the bottom up: at height v (0 at the
              bottom, 1 at the top) its opacity is 0.9 · smoothstep(f − 0.25, f + 0.25, v), f = 1.4 d − 0.2.

 The shake, as the print feels it in the hand: two bursts, each a sine of frequency f and amplitude A
 (breathing ±20 % at 0.9 Hz), that fades in over 0.15 s and dies out as e^(−7 s) after the burst:
   x = A·env·sin(2π f τ),  rotation = 6°·env·sin(2π f τ − 0.5),  y = 3·env·sin(4π f τ).

 autoPlay timeline (LOOP = 8 s):
   0.00–0.70  rest: an undeveloped print, "Shake to develop", an empty bar.
   0.70–2.30  burst 1 (A 13 dp, 3.2 Hz); d 0 → 0.42 (ease in-out). 2.30–2.80 the print settles; d creeps to 0.46.
   2.80–4.60  burst 2 (A 17 dp, 3.5 Hz); d 0.46 → 1 by 4.70 (ease-out).
   4.70–5.00  the label turns to "Developed" with a check; 4.75–5.45 a glint sweeps the photo.
   5.90–6.55  the print drops into the roll thumbnail at the bottom (ease-in: scale → 0.11, fades in its
              last 30 %); the bar fades 5.90–6.20; 6.50–6.95 the thumbnail bumps (+16 %).
   6.70–7.50  a new undeveloped print slides down from 110 dp above (ease-out-back, fade 0.15 s,
              scale 0.96 → 1); the label turns back 6.80–7.10; the bar returns 7.00–7.30.
   7.50–8.00  rest, identical to t = 0.
 */

internal class ShakeDevelopPolaroidScene {
    var x = 0.0
    var y = 0.0
    var rotation = 0.0
    var scale = 1.0
    var alpha = 1.0
    /** Moves the print from its place to the roll thumbnail (0 … 1). */
    var toRoll = 0.0
    var develop = 0.0
    /** Glint progress 0 … 1, or −1 when none. */
    var glint = -1.0
    /** 0: "Shake to develop", 1: "Developed". */
    var label = 0.0
    var bar = 1.0
    var bump = 0.0
}

internal object ShakeDevelopPolaroidMotion {
    const val LOOP = 8.0

    private class Burst(val start: Double, val end: Double, val amp: Double, val freq: Double)
    private val bursts = listOf(Burst(0.70, 2.30, 13.0, 3.2), Burst(2.80, 4.60, 17.0, 3.5))

    fun clamp(x: Double) = min(1.0, max(0.0, x))
    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    fun inOut(x: Double): Double {
        val t = clamp(x)
        return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    }
    fun out(x: Double) = 1 - (1 - clamp(x)).pow(3)
    fun easeIn(x: Double): Double { val t = clamp(x); return t * t * t }
    fun outBack(x: Double): Double {
        val t = clamp(x)
        val c1 = 1.70158
        val c3 = c1 + 1
        return 1 + c3 * (t - 1).pow(3) + c1 * (t - 1).pow(2)
    }

    /** The shake's offset at loop time t: x, y in dp and rotation in degrees. */
    fun shake(t: Double): DoubleArray {
        var x = 0.0
        var y = 0.0
        var r = 0.0
        for (b in bursts) {
            if (t < b.start) continue
            val tau = t - b.start
            val dur = b.end - b.start
            val env = if (tau < dur) smoothstep(0.0, 0.15, tau) else exp(-7 * (tau - dur))
            if (env < 0.002) continue
            val a = b.amp * (0.8 + 0.2 * sin(2 * PI * 0.9 * tau))
            val ph = 2 * PI * b.freq * tau
            x += a * env * sin(ph)
            r += 6 * env * sin(ph - 0.5)
            y += 3 * env * sin(2 * ph)
        }
        return doubleArrayOf(x, y, r)
    }

    fun develop(t: Double): Double = when {
        t < 0.7 -> 0.0
        t < 2.3 -> 0.42 * inOut((t - 0.7) / 1.6)
        t < 2.8 -> 0.42 + 0.04 * smoothstep(2.3, 2.8, t)
        t < 6.7 -> 0.46 + 0.54 * out((t - 2.8) / 1.9)
        else -> 0.0
    }

    fun scene(t: Double): ShakeDevelopPolaroidScene {
        val s = ShakeDevelopPolaroidScene()
        val sh = shake(t)
        s.x = sh[0]; s.y = sh[1]; s.rotation = sh[2]
        s.develop = develop(t)
        if (t >= 4.75 && t < 5.45) s.glint = (t - 4.75) / 0.7
        s.label = smoothstep(4.7, 5.0, t) * (1 - smoothstep(6.8, 7.1, t))
        s.bar = 1 - smoothstep(5.9, 6.2, t) + smoothstep(7.0, 7.3, t)
        if (t >= 5.9 && t < 6.7) {
            val q = easeIn((t - 5.9) / 0.65)
            s.toRoll = q
            s.scale = 1 - 0.89 * q
            s.alpha = 1 - smoothstep(0.7, 1.0, q)
        }
        if (t >= 6.5 && t < 6.95) s.bump = 0.16 * sin(PI * (t - 6.5) / 0.45)
        if (t >= 6.7) {
            val u = (t - 6.7) / 0.8
            s.toRoll = 0.0
            s.y = -110 * (1 - outBack(u))
            s.alpha = smoothstep(6.7, 6.85, t)
            s.scale = 0.96 + 0.04 * out(u)
        }
        return s
    }

    /** The photo's colour matrix at development d (4 × 5, offsets in 0 … 1). */
    fun matrix(d: Double): DoubleArray {
        val e = smoothstep(0.0, 1.0, d)
        val sat = 0.1 + 0.9 * e
        val con = 0.25 + 0.75 * e
        val lift = 0.18 * (1 - e)
        val cast = doubleArrayOf(-0.02, 0.01, 0.03).map { it * (1 - e) }
        val lw = doubleArrayOf(0.2126, 0.7152, 0.0722)
        val m = DoubleArray(20)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                val s = (1 - sat) * lw[col] + (if (row == col) sat else 0.0)
                m[row * 5 + col] = con * s
            }
            m[row * 5 + 4] = 0.5 * (1 - con) + lift + cast[row]
        }
        m[18] = 1.0
        return m
    }

    /** The haze's opacity at height v (0 at the bottom of the photo, 1 at the top). */
    fun haze(d: Double, v: Double): Double {
        val f = 1.4 * d - 0.2
        return 0.9 * smoothstep(f - 0.25, f + 0.25, v)
    }
}
