package com.ngocdevv.nativecook.blocks.gestures.parkassistscrub

import kotlin.math.*

internal data class ParkAssistScrubPose(val x: Double, val y: Double, val heading: Double)
internal data class ParkAssistScrubFrame(
    val s: Double = 0.0, val assist: Double = 0.0, val brake: Double = 0.0,
    val hazard: Double = 0.0, val parked: Double = 0.0, val holding: Double = 0.0,
    val out: Double = 0.0, val finger: Double = 0.0,
)
internal object ParkAssistScrubMotion {
    const val LOOP = 10.0
    const val WALL_Y = -3.3
    const val HALF_LENGTH = 2.2
    const val SPEED = 0.26
    const val HOLD1 = 0.8
    const val GO1 = 0.95
    const val RELEASE1 = 3.5
    const val HOLD2 = 4.3
    const val GO2 = 4.4
    const val PARKED = 6.6
    const val RELEASE2 = 6.7
    const val FLASH = 6.9
    const val OUT_START = 8.0
    const val OUT_END = 9.8
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp((t-a)/(b-a))
    fun smooth(a: Double, b: Double, x: Double): Double { val u = seg(x,a,b); return u*u*(3-2*u) }
    fun easeInOut(x: Double) = if (x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    fun poseAt(s: Double): ParkAssistScrubPose {
        val t = clamp(s); val u = 1-t
        val x = u*u*u*2.7+3*u*u*t*1.3
        val y = u*u*u*5.1+3*u*u*t*4.7+3*u*t*t*2.6-t*t*t*0.8
        val dx = 3*u*u*(1.3-2.7)+6*u*t*(-1.3)
        val dy = 3*u*u*(4.7-5.1)+6*u*t*(2.6-4.7)+3*t*t*(-0.8-2.6)
        return ParkAssistScrubPose(x,y,atan2(dx,-dy))
    }
    fun steerAt(s: Double) = ((poseAt(s+0.01).heading-poseAt(s-0.01).heading)/0.02*0.35).coerceIn(-0.5,0.5)
    fun gapAt(s: Double): Double {
        val p = poseAt(s)
        return max(0.0,p.y-cos(p.heading)*HALF_LENGTH-abs(sin(p.heading))*0.9-WALL_Y)
    }
    fun sAt(t: Double): Double {
        if (t < GO1) return 0.0
        if (t < RELEASE1+0.25) {
            val u = (t-GO1)/(RELEASE1+0.25-GO1); val a = 0.15; val b = 0.1
            val d = if (u < a) u*u/(2*a) else if (u < 1-b) a/2+(u-a)
                else a/2+(1-a-b)+(b-(1-u)*(1-u)/b)/2
            return 0.6*d/(1-a/2-b/2)
        }
        if (t < GO2) return 0.6
        if (t < OUT_START) return 0.6+0.4*easeOut(seg(t,GO2,PARKED))
        return 1-easeInOut(seg(t,OUT_START,OUT_END))
    }
    fun frameAt(t: Double): ParkAssistScrubFrame {
        val assist = smooth(GO1-0.1,GO1+0.2,t)*(1-smooth(OUT_START,OUT_START+0.3,t))
        val brake = smooth(RELEASE1+0.15,RELEASE1+0.3,t)*(1-smooth(HOLD2,HOLD2+0.1,t))+
            smooth(PARKED,PARKED+0.15,t)*(1-smooth(OUT_START-0.2,OUT_START,t))
        fun blink(a: Double) = sin(PI*seg(t,a,a+0.28))
        fun press(a: Double,b: Double) = easeOut(seg(t,a,a+0.1))*(1-easeOut(seg(t,b,b+0.15)))
        fun near(a: Double,b: Double) = easeOut(seg(t,a-0.25,a))*(1-easeOut(seg(t,b,b+0.2)))
        return ParkAssistScrubFrame(sAt(t),assist,clamp(brake),blink(FLASH)+blink(FLASH+0.35),
            smooth(PARKED,PARKED+0.3,t)*(1-smooth(OUT_START,OUT_START+0.3,t)),
            press(HOLD1,RELEASE1)+press(HOLD2,RELEASE2),
            smooth(OUT_START,OUT_START+0.2,t)*(1-smooth(OUT_END-0.2,OUT_END,t)),
            clamp(near(HOLD1,RELEASE1)+near(HOLD2,RELEASE2)))
    }
    fun driveEase(x: Double): Double {
        var a = 0.0; var b = 1.0
        repeat(30) {
            val u = (a+b)/2; val v = 1-u
            if (3*v*v*u*0.77+3*v*u*u*0.175+u*u*u < x) a = u else b = u
        }
        val u = (a+b)/2; return 3*(1-u)*u*u+u*u*u
    }
    fun timing(x: Double) = if (x < 0.5) 2*x*x else 1-(-2*x+2).pow(2)/2
}
