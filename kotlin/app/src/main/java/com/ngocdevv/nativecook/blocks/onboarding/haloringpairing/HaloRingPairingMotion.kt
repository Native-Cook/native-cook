package com.ngocdevv.nativecook.blocks.onboarding.haloringpairing

import kotlin.math.*

internal data class HaloRingPairingFrame(
    var condense: Double = 0.0, var search: Double = 1.0,
    var yaw: Double = 0.3, var scale: Double = 1.0, var size: Int = 9,
    var fly: Double = 0.0, var paired: Double = 0.0, var found: Double = 0.0,
    var sizeBump: Double = 0.0, var pressPair: Double = 0.0, var finger: Double = 0.0,
    var fingerAt: Int = 0, var fingerDx: Double = 0.0,
    var bumpProgress: Double = 1.0, var phase: Double = 0.0,
) {
    val step: Int get() = if (fly > 0.5) 2 else if (condense > 0.5) 1 else 0
}

internal object HaloRingPairingMotion {
    const val LOOP = 11.0
    val SIZES = 7..12
    const val START_SIZE = 9
    const val SEARCH_S = 1.8
    const val YAW0 = 0.3
    fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t-a)/(b-a))
    fun smooth(a: Double, b: Double, x: Double): Double {
        val u = seg(x,a,b); return u*u*(3-2*u)
    }
    fun easeInOut(x: Double) = if (x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun glide(x: Double) = (1-exp(-4.5*clamp01(x)))/(1-exp(-4.5))
    fun bump(u: Double) = if (u <= 0 || u >= 0.7) 0.0 else exp(-u*7)*sin(u*20)
    fun scaleOf(size: Int) = 1+(size-START_SIZE)*0.025
    fun frameAt(t: Double): HaloRingPairingFrame {
        val f = HaloRingPairingFrame()
        f.condense = easeInOut(seg(t,1.8,3.0))*(1-easeInOut(seg(t,9.9,10.7)))
        f.search = clamp01(1-smooth(1.8,2.4,t)+smooth(10.2,LOOP,t))
        val dragU = seg(t,3.6,4.0)
        val yaw = YAW0+2*PI*(0.2*dragU+0.8*glide(seg(t,4.0,5.2)))
        f.yaw = if (t < 3.6) YAW0 else if (t > 9.2) YAW0+2*PI else yaw
        f.size = if (t >= 5.4 && t < 9.2) 10 else START_SIZE
        f.scale = scaleOf(f.size)
        f.fly = easeInOut(seg(t,6.5,7.3))*(1-easeInOut(seg(t,9.2,9.9)))
        f.paired = smooth(6.8,7.5,t)*(1-smooth(9.1,9.5,t))
        f.found = smooth(2.5,3.2,t)*(1-smooth(6.4,6.65,t))
        fun tap(a: Double) = easeOut(seg(t,a-0.08,a))*(1-easeOut(seg(t,a+0.06,a+0.2)))
        fun near(a: Double,b: Double) = easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.2)))
        val onRing = near(3.6,4.05); val onSize = near(5.35,5.55); val onPair = near(6.25,6.45)
        f.sizeBump = bump(t-5.4)
        f.bumpProgress = if (t > 5.4) min(1.0,(t-5.4)/0.7) else 1.0
        f.pressPair = tap(6.3); f.finger = clamp01(onRing+onSize+onPair)
        f.fingerAt = if (onRing > 0.001) 0 else if (onSize > 0.001) 1 else 2
        f.fingerDx = 160*easeInOut(dragU); f.phase = 2*PI*t/LOOP
        return f
    }
    fun defaultEase(u: Double): Double {
        val x = clamp01(u); return if (x < 0.5) 2*x*x else 1-(-2*x+2).pow(2)/2
    }
    // Reanimated 4 duration spring: mass 4, energy threshold 6e-9, 1.5× perceptual duration.
    fun springOmega(x0: Double,v0: Double,duration: Double): Double {
        if (abs(x0) < 1e-12 && abs(v0) < 1e-12) return 30.069499574943045
        var lo = Math.ulp(1.0); var hi = 8000.0; var stiffness = (lo+hi)/2
        val settling = duration*1.5
        for (i in 0 until 100) {
            stiffness = (lo+hi)/2
            val omega = sqrt(stiffness/4)*0.6
            val envelope = exp(-omega*settling)
            val xt = (x0+(v0+x0*omega)*settling)*envelope
            val vt = xt * -omega+(v0+x0*omega)*envelope
            val fraction = (stiffness*xt*xt+4*vt*vt)/(stiffness*x0*x0+4*v0*v0)
            val diff = fraction-6e-9
            if (abs(diff) <= 6e-12) break
            if (diff > 0) lo = stiffness else hi = stiffness
        }
        return sqrt(stiffness/4)
    }
    fun bezier(u: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val x = clamp01(u)
        fun coordinate(t: Double,a: Double,b: Double) = 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
        var lo = 0.0; var hi = 1.0
        repeat(24) {
            val mid = (lo+hi)/2
            if (coordinate(mid,x1,x2) < x) lo = mid else hi = mid
        }
        return coordinate((lo+hi)/2,y1,y2)
    }
}
