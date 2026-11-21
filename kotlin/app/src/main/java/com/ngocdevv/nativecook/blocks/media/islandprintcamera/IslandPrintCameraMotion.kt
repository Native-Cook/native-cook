package com.ngocdevv.nativecook.blocks.media.islandprintcamera

import kotlin.math.*

/** Exact port of ipc-motion.ts; autoplay springs have mass 1. */
internal object IslandPrintCameraMotion {
    const val LOOP = 11.0
    fun seg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
    fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
    fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
    fun easeOut(k: Double) = 1 - (1 - k).pow(3)
    fun easeIn(k: Double) = k * k * k
    fun easeInOut(k: Double) = if (k < .5) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2

    fun springAt(t: Double, from: Double, to: Double, v0: Double, d: Double, k: Double): Double {
        if (t <= 0) return from
        val x0 = from - to
        val w0 = sqrt(k)
        val z = d / (2 * w0)
        if (z < 1) {
            val wd = w0 * sqrt(1 - z * z)
            return to + exp(-z * w0 * t) * (x0 * cos(wd * t) + ((v0 + z * w0 * x0) / wd) * sin(wd * t))
        }
        if (z == 1.0) return to + exp(-w0 * t) * (x0 + (v0 + w0 * x0) * t)
        val s = sqrt(z * z - 1)
        val r1 = -w0 * (z - s)
        val r2 = -w0 * (z + s)
        val c2 = (v0 - r1 * x0) / (r2 - r1)
        return to + (x0 - c2) * exp(r1 * t) + c2 * exp(r2 * t)
    }

    private fun domeRise(p: Double, a: Double, b: Double): Double {
        val k = seg(p, a, b)
        val over = if (k > .85) sin(((k - .85) / .15) * PI) * .05 else 0.0
        return easeOut(k) + over
    }

    fun feedAt(t: Double): Double {
        val u = seg(t, 4.85, 6.65)
        return u + (.12 / (2 * PI * 14)) * sin(2 * PI * 14 * u)
    }

    fun pAt(t: Double): Double {
        if (t < .7) return 0.0
        if (t < 1.45) return .46 * easeInOut(seg(t, .7, 1.45))
        if (t < 1.7) return .46
        if (t < 4) return min(springAt(t - 1.7, .46, 1.0, 2.4, 22.1, 157.9), 1.02)
        if (t < 4.7) return 1 - easeInOut(seg(t, 4.0, 4.7))
        return 0.0
    }

    fun frameAt(tIn: Double): IslandPrintCameraFrame {
        val t = ((tIn % LOOP) + LOOP) % LOOP
        val p = pAt(t)
        val slotOn = t >= 4 && t < 6.7
        val slot = when {
            t < 4 -> 0.0
            t < 6.7 -> easeInOut(seg(t, 4.0, 4.7))
            else -> max(0.0, springAt(t - 6.7, 1.0, 0.0, 0.0, 30.5, 322.3))
        }
        val antic = easeOut(seg(t, .6, .7)) * (1 - seg(p, 0.0, .15)) * (if (t < 2) 1 else 0)
        val slotDip = if (slotOn) 2 * easeOut(seg(t, 4.7, 4.85)) * (1 - seg(t, 6.6, 6.7)) +
            (if (t > 4.7 && t < 4.85) .5 * sin(t * 2 * PI * 30) else 0.0) else 0.0
        val pd = if (t >= 4 && t < 4.7) max(0.0, (p - .45) / .55) else p
        val rise = doubleArrayOf(domeRise(pd, .45, .85), domeRise(pd, .5, .9), domeRise(pd, .55, .95))
        val flood = doubleArrayOf(seg(pd, .69, .85), seg(pd, .74, .9), seg(pd, .79, .95))
        val depth = when {
            t >= 2.9 && t < 3.5 -> min(springAt(t - 2.9, 0.0, .45, 0.0, 30.5, 322.3), .5)
            t >= 3.5 && t < 3.62 -> mix(.45, 1.0, easeIn(seg(t, 3.5, 3.58)))
            t >= 3.62 && t < 4 -> max(0.0, springAt(t - 3.62, 1.0, 0.0, 0.0, 19.0, 195.0))
            else -> 0.0
        }
        val exposure=if(t<.9)0.0 else if(t<1.9)mix(0.0,1.15,easeOut(seg(t,.9,1.9))) else if(t<2.3)mix(1.15,1.0,easeInOut(seg(t,1.9,2.3))) else 1.0
        val blur=if(t<1.5)12.0 else if(t<2.3)mix(12.0,1.5,easeOut(seg(t,1.5,2.3))) else if(t<2.9)1.5 else if(t<3.4)mix(1.5,0.0,easeInOut(seg(t,2.9,3.4))) else 0.0
        val focus = 1 + .03 * sin(PI * easeInOut(seg(t, 2.9, 3.4)))
        val glint = if (t >= 1.9 && t < 2.5) easeInOut(seg(t, 1.9, 2.5)) else 0.0
        val black = if (t >= 3.5 && t < 3.56) 1.0 else 0.0
        val flash = if (t >= 3.56 && t < 3.8) .9 * (1 - easeOut(seg(t, 3.56, 3.8))) else 0.0
        val bloom = if (t >= 3.56 && t < 3.8) .25 * (1 - easeOut(seg(t, 3.56, 3.8))) else 0.0
        val dim = if (t >= 3.56 && t < 4.1) 1 - .18 * sin(PI * seg(t, 3.56, 4.1)) else 1.0
        val led = if (t >= 3.5 && t < 7.1) 2 else if (t >= 3.4 && t < 3.5) 1 else 0
        val ledHalo = if (led == 1) .18 else if (led == 2) .18 * seg(t, 3.5, 3.65) * (1 - seg(t, 7.0, 7.1)) else 0.0
        val f = if (t < 4.85) 0.0 else if (t < 6.65) feedAt(t) else 1.0
        var printTop = 48 - 247 + 247 * f
        var printRot = 0.0
        var printScaleY = 1.0
        var printOut = false
        var printShadow = 0.0
        if (t >= 6.65 && t < 6.91) {
            printTop = mix(48.0, 152.0, seg(t, 6.65, 6.91).pow(2))
            printOut = true
            printShadow = 1.0
        } else if (t >= 6.91 && t < 9.2) {
            printTop = 152.0
            printOut = true
            printScaleY = min(1.0, springAt(t - 6.91, .985, 1.0, 0.0, 19.0, 195.0))
            printRot = springAt(t - 6.91, 2.5, 0.0, -40.0, 19.0, 195.0)
            printShadow = 1 - seg(t, 6.91, 7.3)
        } else if (t >= 9.2 && t < 9.8) {
            val k = easeIn(seg(t, 9.2, 9.8))
            printTop = mix(152.0, 890.0, k)
            printRot = 6 * k
            printOut = true
        } else if (t >= 9.8) {
            printTop = 890.0
            printOut = true
        }
        val d=if(t<5.9)0.0 else if(t<8.8)seg(t,5.9,8.8) else if(t<9.8)1.0 else 0.0
        val sheen = if (t >= 8.6 && t < 9.1) easeInOut(seg(t, 8.6, 9.1)) else 0.0
        val empty = if (t < 6.65) 1.0 else if (t < 6.9) 1 - seg(t, 6.65, 6.9) else if (t < 9.5) 0.0 else easeOut(seg(t, 9.5, 10.0))
        return IslandPrintCameraFrame(p, slot, antic, slotDip, rise, flood, depth, exposure, blur, focus, glint,
            black, flash, bloom, dim, led, ledHalo, f, printOut, printTop, printRot, printScaleY,
            if (t < 6.65) 38 * (1 - f) else 0.0, d, sheen, empty, printShadow)
    }

    fun liveFrameAt(p: Double, depth: Double, focusLock: Double): IslandPrintCameraFrame {
        val q = p.coerceIn(0.0, 1.02)
        val open = seg(q, .35, 1.0)
        return frameAt(10.5).copy(
            p = q,
            rise = doubleArrayOf(domeRise(q, .45, .85), domeRise(q, .5, .9), domeRise(q, .55, .95)),
            flood = doubleArrayOf(seg(q, .69, .85), seg(q, .74, .9), seg(q, .79, .95)),
            depth = depth,
            exposure = open,
            blur = mix(mix(12.0, 1.5, open), 0.0, focusLock),
            focus = 1 + .03 * sin(PI * min(focusLock, 1.0)),
            led = if (focusLock > .95) 1 else 0,
            ledHalo = if (focusLock > .95) .18 else 0.0,
        )
    }
}
