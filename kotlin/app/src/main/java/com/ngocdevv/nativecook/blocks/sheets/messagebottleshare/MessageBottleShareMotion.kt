package com.ngocdevv.nativecook.blocks.sheets.messagebottleshare

import kotlin.math.*

internal object MessageBottleShareMotion {
    const val LOOP = 10.0
    const val REST_ANGLE = -0.42
    const val FLIGHT = 1.1
    const val DRIFT = 2.4
    data class Layout(val w: Double, val h: Double, val top: Double, val bottom: Double) {
        val hintTop = h - max(bottom, 16.0) - 20
        val ctaTop = hintTop - 8 - 56
        val titleTop = top + 44 + 8
        val horizon = h * 0.42
        val scale = w * 0.3
        val homeX = w * 0.47
        val homeY = ctaTop - 32 - scale * 0.55
        val landX = w * 0.66
        val landY = horizon + 18
    }
    data class Bottle(val x: Double = 0.0, val y: Double = 0.0, val angle: Double = REST_ANGLE,
                      val scale: Double = 1.0, val floating: Double = 1.0, val alpha: Double = 1.0)
    data class Frame(val bottle: Bottle, val splash: Double = -1.0, val sent: Boolean = false,
                     val finger: Double = 0.0, val fingerX: Double = 0.0, val fingerY: Double = 0.0,
                     val fingerPress: Double = 0.0)
    fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0, 1.0)
    fun easeInOut(x: Double) = if (x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun bob(t: Double): Pair<Double, Double> {
        val ph = t/2.5*PI*2
        return Pair(4*sin(ph), 0.05*sin(ph+0.9))
    }
    fun flightAt(l: Layout, x: Double, y: Double, a: Double, p: Double): Bottle {
        val k = easeOut(p); val u = 1-k
        val apex = l.horizon-l.scale*1.2
        val mx = (x+l.landX)/2
        return Bottle(u*u*x+2*u*k*mx+k*k*l.landX, u*u*y+2*u*k*apex+k*k*l.landY,
                      a-1.4*PI*k, l.scale*(1-0.9*k.pow(0.8)), 0.0, 1.0)
    }
    fun driftAt(l: Layout, tau: Double): Bottle {
        val k = seg(tau, 0.0, DRIFT)
        return Bottle(l.landX+18*k, l.landY-3*k, REST_ANGLE*0.3,
                      l.scale*0.1*(1-0.4*k), 0.0, 1-easeInOut(k))
    }
    fun riseAt(l: Layout, t: Double, k: Double): Bottle {
        val (dy, da) = bob(t); val e = easeOut(k)
        return Bottle(l.homeX, l.homeY+dy+(1-e)*l.scale*0.6, REST_ANGLE+da+(1-e)*0.3,
                      l.scale, 1.0, e)
    }
    fun frameAt(t: Double, l: Layout): Frame {
        val (dy, da) = bob(t)
        val pull = easeInOut(seg(t, 1.25, 1.7))
        val x = l.homeX-24*pull; val y = l.homeY+dy-l.scale*0.35*pull
        val a = REST_ANGLE+da-0.25*pull
        var splash = -1.0
        val bt = when {
            t < 1.7 -> Bottle(x, y, a, l.scale, 1-pull, 1.0)
            t < 1.7+FLIGHT -> flightAt(l, x, y, a, (t-1.7)/FLIGHT)
            t < 8 -> { splash = t-1.7-FLIGHT; driftAt(l, splash) }
            else -> riseAt(l, t, seg(t, 8.0, 9.4))
        }
        val onBottle = easeOut(seg(t, 1.05, 1.2))*(1-easeOut(seg(t, 1.7, 1.82)))
        val onCta = easeOut(seg(t, 7.6, 7.75))*(1-easeOut(seg(t, 7.95, 8.12)))
        return Frame(bt, splash, t >= 2.4 && t < 7.8, max(onBottle, onCta),
            if (onCta > 0) l.w/2 else bt.x-l.scale*0.2,
            if (onCta > 0) l.ctaTop+28 else bt.y,
            if (onCta > 0) easeOut(seg(t, 7.75, 7.84))*(1-easeOut(seg(t, 7.9, 8.02))) else onBottle)
    }
    fun cssEase(x: Double): Double {
        val target = x.coerceIn(0.0, 1.0)
        var low = 0.0; var high = 1.0
        repeat(24) {
            val u = (low+high)/2; val v = 1-u
            if (3*v*v*u*0.23+3*v*u*u*0.32+u*u*u < target) low = u else high = u
        }
        return 1-(1-(low+high)/2).pow(3)
    }
}
