package com.ngocdevv.nativecook.blocks.progress.pressedstampcard

import kotlin.math.*

/** Source timeline in seconds: the die owns the impact, and the card swaps faces exactly edge-on. */
internal object PressedStampCardMotion {
    const val LOOP = 10.0
    val rotations = doubleArrayOf(-6.0, 4.0, -3.0, 7.0, -5.0, 2.0, -7.0, 5.0)
    private val offsetsX = doubleArrayOf(.6, -.8, .3, -.5, 1.0, -.3, .7, -1.0)
    private val offsetsY = doubleArrayOf(-.4, .5, .9, -.7, .2, -1.0, .6, .3)
    fun wellX(i: Int) = 60.0 + 77 * (i % 4)
    fun wellY(i: Int) = 114.0 + 94 * (i / 4)
    fun sealX(i: Int) = wellX(i) + offsetsX[i]
    fun sealY(i: Int) = wellY(i) + offsetsY[i]
    fun segment(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)
    fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p
    private fun bezier(p: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        if (p <= 0 || p >= 1) return p.coerceIn(0.0, 1.0)
        var lo = 0.0; var hi = 1.0; var u = p
        repeat(16) {
            u = (lo + hi) / 2; val v = 1 - u
            if (3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u < p) lo = u else hi = u
        }
        val v = 1 - u
        return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u
    }
    fun out(p: Double) = bezier(p, .2, .8, .2, 1.0)
    private fun inOut(p: Double) = bezier(p, .65, 0.0, .35, 1.0)
    private fun into(p: Double) = bezier(p, .32, 0.0, .67, 0.0)
    fun spring(t: Double, from: Double, to: Double, velocity: Double, d: Double, k: Double): Double {
        if (t <= 0) return from
        val w = sqrt(k); val z = d / (2 * w); val x = from - to
        if (z >= 1) return to + exp(-w * t) * (x + (velocity + w * x) * t)
        val wd = w * sqrt(1 - z * z)
        return to + exp(-z * w * t) * (x * cos(wd * t) + (velocity + z * w * x) / wd * sin(wd * t))
    }
    private fun impulse(t: Double, depth: Double) = if (t >= 0 && t < .7) spring(t, 0.0, 0.0, depth / .0447, 15.0, 93.0) else 0.0
    fun rest(count: Int, light: Double = 315.0): PressedStampCardFrame {
        return PressedStampCardFrame(List(8) { i -> PressedStampCardSeal(sealX(i), sealY(i), 34.0, if (i < count) 1.0 else 0.0, rotations[i], if (count == 8) 1.0 else 0.0) }, count, light).apply {
            gold = if (count == 8) 1.0 else 0.0; buttonGold = gold; buttonRoll = gold
        }
    }
    fun stamp(f: PressedStampCardFrame, elapsed: Double, target: Int, reduced: Boolean = false): PressedStampCardFrame {
        val eighth = target == 7; val impact = if (eighth) 1.14 else .96
        val drop = impact - .14; val cx = sealX(target); val cy = sealY(target)
        val age = elapsed - impact; val landed = age >= -1e-9
        f.target = target; f.event = elapsed
        f.orders = target + spring(elapsed, 0.0, 1.0, 0.0, 22.1, 157.9).coerceIn(0.0, 1.0)
        if (reduced) {
            f.seals[target].alpha = segment(elapsed, 0.0, .2); f.count = target + 1; f.oldCount = target
            f.roll = segment(elapsed, 0.0, .2)
            if (eighth) { f.gold = segment(elapsed, .2, .4); f.buttonGold = f.gold; f.buttonRoll = f.gold; f.seals.forEach { it.gold = f.gold } }
            return f
        }
        if (elapsed >= .25 && !landed) {
            val p = inOut(segment(elapsed, .25, .7)); val fall = into(segment(elapsed, drop, impact))
            f.fly = PressedStampCardSeal(mix(420.0, cx, p), mix(536.4, cy, p) - 120 * p * (1 - p), mix(52.0, 37.0, fall), 1.0, 8 * (1 - fall), lift = 1 - fall)
            f.dieTilt = 8 * (1 - fall)
            if (eighth) { f.dieGlow = .7 * segment(elapsed, .7, 1.0); f.push = 1 + .05 * segment(elapsed, .7, 1.0) }
        }
        if (!landed) return f
        val dt = max(0.0, age)
        f.count = target + 1; f.oldCount = target; f.roll = spring(dt - .04, 0.0, 1.0, 0.0, 30.5, 322.3).coerceIn(0.0, 1.0)
        f.seals[target].apply { alpha = 1.0; radius = 34.0; lift = 1 - segment(dt, 0.0, 1.2); flash = (1 - out(segment(dt, 0.0, .44))) * .55 }
        f.dip = impulse(dt, if (eighth) 9.0 else 6.0); f.tilt = f.dip / (if (eighth) 9 else 6) * 1.6
        f.dent = 1.4 * (1 - out(segment(dt, 0.0, .38)))
        val rp = out(segment(dt, 0.0, .44)); f.ripple = doubleArrayOf(cx, cy, 180 * rp, .6 * (1 - rp))
        for (i in 0 until target) {
            val distance = hypot(sealX(i) - cx, sealY(i) - cy); val hit = dt - distance / 900
            if (hit >= 0 && hit < .6) {
                val bounce = spring(hit, 0.0, 0.0, -65.0, 12.0, 260.0)
                f.seals[i].y += bounce; f.seals[i].rotation += bounce / 2.5 * if (i % 2 == 1) 1.5 else -1.5
            }
            val edge = (distance - f.ripple[2]) / 14
            f.seals[i].flash += exp(-edge * edge) * f.ripple[3] * .6
        }
        val dwell = if (eighth) .06 else .15; val lift = out(segment(dt, dwell, dwell + .32))
        if (dt < dwell + .32) {
            val s = segment(dt, dwell, dwell + .32); val e = s.pow(1.85)
            f.fly = PressedStampCardSeal(mix(cx, 420.0, if (eighth) e else lift), cy - if (eighth) 120 * e else 92 * lift,
                if (dt < .06) 35.5 else mix(37.0, 55.0, s.pow(.6929)), 1.0, 8 * lift, lift = lift)
            f.dieTilt = 8 * lift; f.dieGlow = if (eighth) .7 * (1 - s * s) else 0.0
        } else f.fly = PressedStampCardSeal()
        if (eighth) {
            if (dt < .6) f.push = 1 + .05 * spring(dt, 1.0, 0.0, 0.0, 24.0, 520.0)
            f.gold = segment(dt, 0.0, 1.2); f.sweep = segment(dt, .08, .78)
            for (i in 0 until 8) {
                val distance = hypot(sealX(i) - cx, sealY(i) - cy)
                f.seals[i].gold = out(segment(dt - distance / 500, 0.0, .11))
                f.seals[i].flash += sin(PI * segment(dt - distance / 77 * .06, 0.0, .5)) * .4
            }
            f.buttonGold = out(segment(dt, 1.26, 1.76)); f.buttonRoll = spring(dt - 1.26, 0.0, 1.0, 0.0, 30.5, 322.3).coerceIn(0.0, 1.0)
        }
        return f
    }
    fun flip(elapsed: Double, reduced: Boolean = false): PressedStampCardFrame {
        val p = if (reduced || elapsed >= .7) 1.0 else spring(elapsed, 0.0, 1.0, 0.0, 16.0, 140.0).coerceIn(0.0, 1.0)
        val swapped = p >= .5; val f = rest(if (swapped) 0 else 8)
        f.flip = 180 * p - if (swapped) 180 else 0
        f.flipFlash = if (swapped) 0.0 else exp(-((p * 180 - 60) / 10).pow(2))
        f.orders = mix(8.0, 5.0, out(segment(elapsed, 0.0, .7)))
        f.buttonGold = 1 - out(segment(elapsed, 0.0, .5)); f.buttonRoll = 1 - spring(elapsed, 0.0, 1.0, 0.0, 30.5, 322.3).coerceIn(0.0, 1.0)
        f.dip = impulse(elapsed - .48, 3.0)
        return f
    }
    fun frameAt(seconds: Double): PressedStampCardFrame {
        val t = ((seconds % LOOP) + LOOP) % LOOP
        val light = 315 + 12 * sin(2 * PI * t / LOOP)
        var f = rest(5, light); f.phase = t
        doubleArrayOf(.6, 2.2, 3.9).forEachIndexed { i, start -> if (t >= start) stamp(f, t - start, 5 + i) }
        if (t in 6.9..<7.4) f.sweep = segment(t, 6.9, 7.4)
        if (t in 7.6..<8.1) f.sweep = segment(t, 7.6, 8.1)
        if (t in 8.05..<8.29) f.redeemRing = segment(t, 8.05, 8.29)
        if (t >= 8.2) {
            f = flip(t - 8.2); f.light = light; f.phase = t
            if (t in 8.9..<8.98) {
                val p = out(segment(t, 8.9, 8.98)); f.fly = PressedStampCardSeal(mix(420.0, sealX(0), p), sealY(0) - 14 * sin(PI * p), mix(52.0, 37.0, p), 1.0, lift = 1 - p)
            }
            if (t >= 8.98) {
                val index = min(4, floor((t - 8.98) / .13).toInt()); val age = t - 8.98 - index * .13
                f = rest(index + 1, light); f.phase = t; f.orders = 5.0
                f.dip = if (age < .13) 1.5 * sin(PI * age / .13) else 0.0
                f.oldCount = index; f.roll = out(segment(age, 0.0, .06))
                if (t < 9.6) {
                    val p = (age / if (index == 4) .1 else .13).coerceIn(0.0, 1.0); val hop = 14 * sin(PI * p); val next = min(4, index + 1)
                    f.fly = PressedStampCardSeal(mix(sealX(index), if (index == 4) 420.0 else sealX(next), if (index == 4) out(p) else p), mix(sealY(index), sealY(next), p) - hop, 37 + hop * .28, 1.0, hop * .15, lift = hop / 14)
                }
            }
        }
        if (t >= 9.6) { f = rest(5, light); f.phase = t }
        return f
    }
}
