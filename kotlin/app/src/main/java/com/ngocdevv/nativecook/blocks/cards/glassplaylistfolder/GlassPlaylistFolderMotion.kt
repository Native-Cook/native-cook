package com.ngocdevv.nativecook.blocks.cards.glassplaylistfolder

import kotlin.math.*

internal object GlassPlaylistFolderMotion {
    const val LOOP = 8.0
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun ease(k: Double): Double { val x = clamp(k); return if (x < .5) 4*x*x*x else 1-(-2*x+2).pow(3)/2 }
    class Springs(initial: Double = 0.0) {
        var open = initial; var ov = 0.0
        val covers = DoubleArray(4) { initial }; val cv = DoubleArray(4)
        var target = initial; var changedAt = -10.0
        var lift = 0.0; var lv = 0.0; var liftTarget = 0.0; var hovered = 1
        fun target(v: Double, now: Double) { if (target == v) return; target = v; changedAt = now }
        fun step(dt: Double, now: Double) {
            val n = max(1, ceil(dt / (1.0/240)).toInt()); val h = dt/n
            repeat(n) {
                ov += ((target-open)*190-ov*19)*h; open += ov*h
                repeat(4) { i ->
                    val order = if (target == 1.0) i else 3-i
                    val live = if (now-changedAt > .05+order*.06) target else 1-target
                    cv[i] += ((live-covers[i])*150-cv[i]*13.5)*h; covers[i] += cv[i]*h
                }
                lv += ((liftTarget-lift)*230-lv*20)*h; lift += lv*h
            }
        }
    }
    class Driver(reduced: Boolean) {
        val s = Springs(if (reduced) 1.0 else 0.0)
        var t = 0.0; var now = 0.0; var last = -10.0; var drag = -1.0
        var press = 0.0; private var pressStart = 0.0; private var pressAim = 0.0
        private var pressAt = 0.0; private var pressStiffness = 0.0; private var pressInitialEnergy = 0.0; private var pressDamping = 1.0
        private var pressVelocity = 0.0; private var pressInitialVelocity = 0.0
        var playAt = 0.0
        fun press(aim: Double, duration: Double, damping: Double) {
            pressStart = press; pressAim = aim; pressAt = now
            pressDamping = damping; pressInitialVelocity = pressVelocity
            val x = pressStart-aim; val v = pressInitialVelocity; val settling = duration*1.5
            fun fraction(k: Double): Double {
                val w = sqrt(k/4)*damping; val e = exp(-w*settling)
                val p = (x+(v+x*w)*settling)*e; val velocity = -w*p+(v+x*w)*e
                val initial = .5*k*x*x+2*v*v
                return if (initial == 0.0) 0.0 else (.5*k*p*p+2*velocity*velocity)/initial-6e-9
            }
            var low = Math.ulp(1.0); var high = 8000.0
            val direction = if (fraction(high) >= fraction(low)) 1 else -1
            var mid = (low+high)/2; var count = 100
            while (abs(fraction(mid)) > 6e-12 && count > 0) {
                count--; if (fraction(mid)*direction < 0) low = mid else high = mid
                mid = (low+high)/2
            }
            pressStiffness = mid; pressInitialEnergy = .5*mid*x*x
        }
        fun step(dt: Double, auto: Boolean, reduced: Boolean) {
            now += dt
            if (!reduced) {
                if (auto && drag < 0 && now-last >= 2) {
                    t += dt; if (t >= LOOP) t -= LOOP
                    s.target(if (t >= 2 && t < 5.35) 1.0 else 0.0, now)
                    s.liftTarget = if (t >= 3.4 && t < 4.7) 1.0 else 0.0; s.hovered = 1
                }
                if (drag >= 0) { s.open = drag; s.ov = 0.0 }
                s.step(dt, now)
            }
            val z = pressDamping; val w = sqrt(pressStiffness/4)
            val time = max(0.0, now-pressAt); val x = pressStart-pressAim
            if (pressStiffness == 0.0) return
            if (z < 1) {
                val wd = w*sqrt(1-z*z); val b = (pressInitialVelocity+z*w*x)/wd; val e = exp(-z*w*time)
                press = pressAim+e*(x*cos(wd*time)+b*sin(wd*time))
                pressVelocity = e*((-z*w*x+b*wd)*cos(wd*time)+(-z*w*b-x*wd)*sin(wd*time))
            } else {
                val b = pressInitialVelocity+w*x; val e = exp(-w*time)
                press = pressAim+(x+b*time)*e; pressVelocity = (b-w*(x+b*time))*e
            }
            val energy = .5*pressStiffness*(press-pressAim).pow(2)+2*pressVelocity*pressVelocity
            if (pressInitialEnergy == 0.0 || energy/pressInitialEnergy <= 6e-9) {
                press = pressAim; pressVelocity = 0.0; pressStiffness = 0.0
            }
        }
    }
}
