package com.ngocdevv.nativecook.blocks.cards.zodiacjellydish

import kotlin.math.*

internal object ZodiacJellyDishMotion {
    const val LOOP = 8.0
    fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0, 1.0)
    fun smooth(k: Double): Double { val x = k.coerceIn(0.0,1.0); return x*x*(3-2*x) }
    fun ease(k: Double): Double { val x = k.coerceIn(0.0,1.0); return if (x < 0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2 }
    class Jelly {
        var px = 0.0; var py = 0.0; var vx = 0.0; var vy = 0.0
        var sq = 0.0; var sv = 0.0; var gx = 92.0; var gy = 150.0
        var hold = false; var tx = 0.0; var ty = 0.0
        fun step(dt: Double) {
            val n = max(1,ceil(dt/(1.0/240)).toInt()); val h = dt/n
            repeat(n) {
                val k = if (hold) 420.0 else 210.0; val c = if (hold) 40.0 else 4.2
                vx += (((if (hold) tx else 0.0)-px)*k-vx*c)*h
                vy += (((if (hold) ty else 0.0)-py)*k-vy*c)*h
                px += vx*h; py += vy*h
                sv += (-sq*260-sv*6)*h; sq += sv*h
            }
        }
        fun script(before: Double, t: Double) {
            if (t >= 1.1 && t < 1.75) {
                val k = ease(seg(t,1.1,1.75))
                hold = true; gx = 92.0; gy = 150.0; tx = 26*k; ty = 34*k
            } else if (before < 1.75 && t >= 1.75) hold = false
            if (before < 4.95 && t >= 4.95) sv += 1.6
        }
    }
    class Driver {
        var t = 0.0; var clock = 0.0; var last = -10.0; var addedAt = -10.0
        var held = false; val jelly = Jelly(); val buy = Press(); val save = Press()
        fun step(dt: Double, auto: Boolean, reduced: Boolean) {
            if (reduced) return
            clock += dt
            if (auto && !held && clock-last >= 2) {
                val before = t; t = (t+dt)%LOOP; jelly.script(before,t)
            }
            jelly.step(dt)
        }
        val swirl get() = ease(seg(t,2.9,4.5))
        val lx get() = -0.55+sin(swirl*PI)*1.3+jelly.px/70
        val ly get() = -0.8+jelly.py/90
        val pulse: Double get() {
            val a = if (t >= 4.95) exp(-(t-4.95)*6)*smooth(seg(t,4.95,5.0)) else 0.0
            val dt = clock-addedAt
            return max(a,if (dt >= 0 && dt < 1) exp(-dt*6) else 0.0)
        }
        val added: Double get() {
            val a = smooth(seg(t,5.0,5.25))*(1-smooth(seg(t,6.45,6.7)))
            val dt = clock-addedAt
            return max(a,if (dt >= 0 && dt < 1.8) smooth(dt/0.25)*(1-smooth((dt-1.55)/0.25)) else 0.0)
        }
        fun buyScale(now: Double): Float {
            val a = if (t >= 4.75 && t < 5.15) sin(seg(t,4.75,5.15)*PI) else 0.0
            return (1-max(a,buy.value(now))*0.04).toFloat()
        }
    }
    // Same energy-based duration spring as Reanimated 4, mass 4 and threshold 6e-9.
    class Press {
        private var from = 0.0; private var velocity = 0.0; private var aim = 0.0
        private var at = 0.0; private var omega = 1.0; private var zeta = 1.0; private var initialEnergy = 0.0
        private fun sample(now: Double): Pair<Double,Double> {
            val t = max(0.0,now-at); val x = from-aim; val e = exp(-zeta*omega*t)
            val p: Double; val v: Double
            if (zeta >= 1) {
                val b = velocity+omega*x
                p = (x+b*t)*e; v = (velocity-omega*b*t)*e
            } else {
                val wd = omega*sqrt(1-zeta*zeta); val b = (velocity+zeta*omega*x)/wd
                val d = x*cos(wd*t)+b*sin(wd*t)
                p = e*d; v = e*(-x*wd*sin(wd*t)+b*wd*cos(wd*t)-zeta*omega*d)
            }
            return if (initialEnergy == 0.0 || (2*omega*omega*p*p+2*v*v)/initialEnergy <= 6e-9) aim to 0.0 else aim+p to v
        }
        fun value(now: Double) = sample(now).first
        fun set(target: Double, now: Double, reduced: Boolean) {
            val current = sample(now)
            from = current.first; velocity = current.second; aim = target; at = now
            if (reduced) { from = target; velocity = 0.0; initialEnergy = 0.0; return }
            zeta = if (target == 1.0) 1.0 else 0.7
            val duration = (if (target == 1.0) 0.150 else 0.320)*1.5
            if ((target > from && velocity < 0) || (target < from && velocity > 0)) velocity = 0.0
            val x = from-target
            if (x == 0.0 && velocity == 0.0) { initialEnergy = 0.0; return }
            fun difference(k: Double): Double {
                val w = sqrt(k/4)*zeta; val e = exp(-w*duration)
                val xt = (x+(velocity+x*w)*duration)*e; val vt = -w*xt+(velocity+x*w)*e
                return (0.5*k*xt*xt+2*vt*vt)/(0.5*k*x*x+2*velocity*velocity)-6e-9
            }
            var low = Math.ulp(1.0); var high = 8000.0; var k = (low+high)/2
            val direction = if (difference(high) >= difference(low)) 1 else -1
            var n = 0
            while (abs(difference(k)) > 6e-12 && n++ < 100) {
                if (difference(k)*direction < 0) low = k else high = k
                k = (low+high)/2
            }
            omega = sqrt(k/4); initialEnergy = 0.5*k*x*x
        }
    }
}
