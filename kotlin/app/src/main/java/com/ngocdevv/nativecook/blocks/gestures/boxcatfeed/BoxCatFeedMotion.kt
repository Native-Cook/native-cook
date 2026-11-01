package com.ngocdevv.nativecook.blocks.gestures.boxcatfeed

import kotlin.math.*

internal object BoxCatFeedMotion {
    const val LOOP = 9.0
    const val FLY = 0.7
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    data class Cat(val lift: Double = 0.0, val stretch: Double = 0.0, val munch: Double = 0.0, val caught: Boolean = false, val hearts: Double = -1.0)
    data class Frame(val u: Double = -10.0, val blink: Boolean = false, val duck: Double = 0.0, val count: Int = 0, val dip: Double = 0.0)
    fun script(time: Double): Frame {
        val t = time % LOOP
        val at = if (t >= 5) 5.0 else if (t >= 1) 1.0 else -10.0
        val x = clamp((t-8.25)/0.5)
        return Frame(if (at > 0) t-at else -10.0, count = if (t >= 8.5) 0 else if (t >= 5.7) 2 else if (t >= 1.7) 1 else 0, dip = sin(x*PI))
    }
    fun treatShown(u: Double): Double = when {
        u < 0 -> 1.0
        u < 0.12 -> 1 - u/0.12
        u < 1.25 -> 0.0
        else -> min(1.0, (u-1.25)/0.15)
    }
    fun cat(u: Double): Cat {
        if (u < 0 || u > 2.6) return Cat()
        val up = clamp((u-(FLY-0.32))/0.32)
        val down = clamp((u-FLY-0.12)/0.35)
        val stretch = if (up > 0 && up < 1) 0.18*sin(PI*up) else 0.0
        val land = if (u > FLY+0.47 && u < FLY+0.75) -0.14*sin((u-FLY-0.47)/0.28*PI) else 0.0
        val munch = if (u > FLY+0.4 && u < FLY+1.6) abs(sin((u-FLY)*14)) else 0.0
        return Cat(sin(PI/2*up)*(1-down*down), stretch+land, munch, u >= FLY, if (u >= FLY+0.3) u-FLY-0.3 else -1.0)
    }
}
internal class BoxCatFeedDriver {
    var now = 0.0
    var at = -10.0
    var count = 0
    var lastTouch = -100.0
    var scripted = false
    var loopT = 0.0
    var duck = 0.0
    var duckV = 0.0
    fun step(wall: Double, auto: Boolean): BoxCatFeedMotion.Frame {
        now += wall
        val dt = min(wall, 0.05)
        duckV += (-160*duck-14*duckV)*dt
        duck += duckV*dt
        if (auto && now-lastTouch > 3.5 && (scripted || now-at > 2.8)) {
            if (!scripted) { scripted = true; loopT = 0.0 }
            loopT = (loopT+wall)%9
            return BoxCatFeedMotion.script(loopT).copy(blink = now%3.4 < 0.14, duck = duck)
        }
        scripted = false
        return BoxCatFeedMotion.Frame(now-at, now%3.4 < 0.14, duck, if (now-at >= 0.7) count else max(0,count-1))
    }
    fun pet() { duckV += 4; lastTouch = now }
    fun flick(): Boolean {
        if (now-at < 1.6) return false
        at = now; count = count%3+1; scripted = false; lastTouch = now
        return true
    }
}
