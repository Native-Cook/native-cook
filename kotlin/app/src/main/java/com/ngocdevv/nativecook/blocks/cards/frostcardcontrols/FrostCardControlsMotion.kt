package com.ngocdevv.nativecook.blocks.cards.frostcardcontrols

import kotlin.math.*

/** Pure event timeline and touch driver, in seconds, matching fcc-motion.ts. */
internal object FrostCardControlsMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    const val GROW = 1.25
    const val MELT = 1.15
    const val DRIPS = 1.3
    const val SHRED = 1.15
    const val REISSUE = 0.9
    data class Events(
        val freeze: Double = -99.0,
        val cx: Double = 46.0,
        val cy: Double = 186.0,
        val melt: Double = -99.0,
        val shred: Double = -99.0,
        val reissue: Double = -99.0,
        val out: Double = -99.0,
    )
    val AUTO = Events(1.12, 46.0, 186.0, 3.72, 5.45, 6.75, -99.0)
    val PRESSES = arrayOf(doubleArrayOf(1.0, 1.12, 0.0), doubleArrayOf(3.6, 3.72, 0.0), doubleArrayOf(5.3, 5.42, 2.0))
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p
    fun easeOut(x: Double): Double {
        val u = 1 - clamp01(x)
        return 1 - u * u * u * u * u
    }
    fun easeIn(x: Double): Double {
        val p = clamp01(x)
        return p * p
    }
    fun easeInOut(x: Double): Double {
        val p = clamp01(x)
        return if (p < 0.5) 16 * p * p * p * p * p else 1 - (-2 * p + 2).pow(5) / 2
    }
    fun smooth(x: Double): Double {
        val p = clamp01(x)
        return p * p * (3 - 2 * p)
    }
    fun spring(tau: Double, response: Double, damping: Double): Double {
        if (tau <= 0) return 0.0
        val w = 2 * PI / response
        val zw = damping * w
        val wd = w * sqrt(1 - damping * damping)
        return 1 - exp(-zw * tau) * (cos(wd * tau) + (zw / wd) * sin(wd * tau))
    }
    data class Frame(
        val frost: Double,
        val melt: Double,
        val drips: Double,
        val cx: Double,
        val cy: Double,
        val frozenLabel: Double,
        val spin: Double,
        val shred: Double,
        val card: Double,
        val drop: Double,
        val shine: Double,
        var out: Double,
        val press: DoubleArray,
    )
    fun frameFor(t: Double, e: Events, presses: DoubleArray): Frame {
        val fz = t - e.freeze
        val ml = t - e.melt
        val meltOn = e.melt > e.freeze && ml >= 0
        val frozen = fz >= 0 && !(meltOn && ml >= MELT)
        val frost = if (fz >= 0) 1.2 * easeOut(fz / GROW) else 0.0
        val melt = if (meltOn) easeInOut(ml / MELT) else 0.0
        val drips = if (meltOn && ml > 0.15 && ml < 0.15 + DRIPS) (ml - 0.15) / DRIPS else -1.0
        val sh = t - e.shred
        val shred = if (e.shred > -98 && sh >= 0 && sh < SHRED) sh / SHRED else -1.0
        val re = t - e.reissue
        val reOn = e.reissue > -98 && re >= 0
        val gone = e.shred > -98 && sh >= SHRED && !(reOn && e.reissue > e.shred)
        val flyOut = if (e.out > -98) easeIn(seg(t, e.out, e.out + 0.4)) else 0.0
        val shine = if (reOn && re > 0.55 && re < 1.15) (re - 0.55) / 0.6 else -1.0
        return Frame(
            frost = if (frozen || (meltOn && ml < MELT)) frost else 0.0,
            melt = melt,
            drips = drips,
            cx = e.cx,
            cy = e.cy,
            frozenLabel = if (frozen) {
                smooth(seg(fz, 0.2, 0.5)) * (if (meltOn) 1 - smooth(ml / 0.3) else 1.0)
            } else 0.0,
            spin = if (fz >= 0) {
                360 * easeOut(fz / 0.8) - (if (meltOn) 360 * easeOut(ml / 0.8) else 0.0)
            } else 0.0,
            shred = shred,
            card = if (gone || (reOn && re < 0)) 0.0 else 1.0,
            drop = if (reOn) spring(re, 0.55, 0.7) else 1.0,
            shine = shine,
            out = flyOut,
            press = presses,
        )
    }
    fun frameAt(t: Double): Frame {
        val press = DoubleArray(3)
        for (p in PRESSES) {
            val k = seg(t, p[0], p[1] + 0.14)
            if (k > 0 && k < 1) press[p[2].toInt()] = sin(PI * k)
        }
        return frameFor(t, AUTO, press)
    }
    class Driver(var t: Double = 0.0) {
        var mode = 0
        var last = -10.0
        var amb = t
        var ev = Events()
        var frozen = 0
        var busy = -1.0
        var press = DoubleArray(3)
        var pressAim = DoubleArray(3)
        var tiltX = 0.0
        var tiltY = 0.0
        var aimX = 0.0
        var aimY = 0.0
        private fun follow(cur: Double, aim: Double, k: Double, dt: Double) = cur + (aim - cur) * (1 - exp(-dt * k))
        fun stepDriver(dt: Double, now: Double, auto: Boolean) {
            amb = (amb + dt) % LOOP
            for (i in 0..2) press[i] = follow(press[i], pressAim[i], if (pressAim[i] > 0) 40.0 else 16.0, dt)
            tiltX = follow(tiltX, aimX, 10.0, dt)
            tiltY = follow(tiltY, aimY, 10.0, dt)
            if (mode == 0) {
                if (auto) t = (t + dt) % LOOP
                return
            }
            if (auto && now - last >= RESUME && now > busy) {
                mode = 0
                t = if (frozen != 0) 2.6 else 8.2
            }
        }
        fun takeOver(now: Double) {
            last = now
            if (mode == 2) return
            mode = 2
            val off = now - t
            frozen = if (t >= AUTO.freeze && t < AUTO.melt + MELT * 0.5) 1 else 0
            ev = Events(freeze = if (frozen != 0) min(AUTO.freeze, t - GROW) + off else -99.0)
            if (t >= AUTO.shred && t < AUTO.reissue + REISSUE + 0.4) {
                ev = ev.copy(shred = AUTO.shred + off, reissue = AUTO.reissue + off)
                busy = ev.reissue + REISSUE + 0.4
            }
        }
        fun freezeAt(now: Double, cx: Double, cy: Double) {
            ev = ev.copy(freeze = now, cx = cx, cy = cy, melt = -99.0)
            frozen = 1
        }
        fun meltNow(now: Double) {
            ev = ev.copy(melt = now)
            frozen = 0
        }
        fun shredNow(now: Double) {
            ev = ev.copy(shred = now, reissue = now + SHRED + 0.2, out = -99.0, freeze = -99.0, melt = -99.0)
            frozen = 0
            busy = now + SHRED + 0.2 + REISSUE + 0.4
        }
        fun reissueNow(now: Double) {
            ev = ev.copy(out = now, reissue = now + 0.42, shred = -99.0, freeze = -99.0, melt = -99.0)
            frozen = 0
            busy = now + 0.42 + REISSUE + 0.4
        }
        fun liveFrame(now: Double): Frame {
            if (mode == 0) {
                val f = frameAt(t)
                for (i in 0..2) f.press[i] = max(f.press[i], press[i])
                return f
            }
            val f = frameFor(now, ev, press.copyOf())
            if (ev.out > -98 && now >= ev.reissue) f.out = 0.0
            return f
        }
    }
}
