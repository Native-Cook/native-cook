package com.ngocdevv.nativecook.blocks.cards.glasspaymorph

import kotlin.math.*

/** Touch-only integrated springs; automatic frames remain pure sceneAt(loopTime). */
internal class GlassPayMorphDriver {
    companion object {
        const val SCRUB_TRAVEL = 150.0
        const val AXIS_LOCK = 10.0
        const val YAW_PER_PT = 1 / 2.2
        const val YAW_MAX = 28.0
        const val PRESS_IN = 0.97
    }

    var mode = 0
    var t = 0.0
    var gain = 1.0
    var tau = 0.0
    var tauV = 0.0
    var play = 0
    var tauTarget = 0.0
    var playEnd = 0.0
    var holdAfter = 0
    var nu = 1.0
    var yaw = 0.0
    var yawV = 0.0
    var press = 1.0
    var pressV = 0.0
    var pressTarget = 1.0
    var band = 0.0
    var bandV = 0.0
    val aOff = DoubleArray(4)
    val rOff = DoubleArray(4)
    var held = 0
    var last = 0.0

    private val motion = GlassPayMorphMotion
    private val yawK = (2 * PI / 0.5).pow(2)
    private val yawC = 2 * 0.55 * sqrt(yawK)
    private val pressK = (2 * PI / 0.22).pow(2)
    private val pressC = 2 * 0.85 * sqrt(pressK)
    private val lightK = (2 * PI / 0.55).pow(2)
    private val lightC = 2 * 0.5 * sqrt(lightK)

    private fun spring(x: Double, v: Double, target: Double, k: Double, c: Double, dt: Double): DoubleArray {
        var xx = x
        var vv = v
        val h = dt / 4
        repeat(4) {
            vv += (-k * (xx - target) - c * vv) * h
            xx += vv * h
        }
        return doubleArrayOf(xx, vv)
    }

    fun isRest(time: Double): Boolean {
        val t = motion.wrap(time)
        return t <= motion.COLLAPSE0 + 1e-4 || t >= motion.EXPAND1 - 1e-4
    }

    fun takeOver(now: Double) {
        if (mode == 0) {
            mode = 1
            tau = t
            tauV = 0.0
            play = 0
            tauTarget = t
            holdAfter = 0
            val tw = motion.wrap(t)
            if (tw > motion.COLLAPSE0 && tw < motion.PAID_HOLD) {
                play = 1
                playEnd = motion.PAID_HOLD
            } else if (tw > motion.EXPAND0 && tw < motion.EXPAND1) {
                play = 1
                playEnd = motion.EXPAND1
            }
        }
        last = now
    }

    fun toggle() {
        val tw = motion.wrap(tau)
        if (isRest(tw)) {
            tau = motion.COLLAPSE0
            play = 1
            playEnd = motion.PAID_HOLD
        } else if (tw >= motion.COLLAPSE1 && tw < motion.EXPAND0) {
            tau = motion.EXPAND0
            play = 1
            playEnd = motion.EXPAND1
        } else if (tw > motion.COLLAPSE0 && tw < motion.COLLAPSE1) {
            play = 0
            tauTarget = motion.COLLAPSE0
            tauV = 0.0
        }
    }

    private fun rubber(over: Double): Double {
        if (over <= 0) return 0.0
        return 0.12 * over / (over + 0.12 / 0.55)
    }

    fun scrubTo(p0: Double, dy: Double): Double {
        var p = p0 - dy / SCRUB_TRAVEL
        if (p < 0) p = -rubber(-p)
        else if (p > 1) p = 1 + rubber(p - 1)
        play = 0
        tau = motion.COLLAPSE0 + (motion.COLLAPSE1 - motion.COLLAPSE0) * p.coerceIn(0.0, 1.0)
        tauTarget = tau
        band = if (p < 0) p else if (p > 1) p - 1 else 0.0
        bandV = 0.0
        return p
    }

    fun scrubEnd(p: Double, vy: Double) {
        val vp = -vy / SCRUB_TRAVEL
        val toPill = if (abs(vy) > 600) vp > 0 else p > 0.5
        play = 0
        tauTarget = if (toPill) motion.COLLAPSE1 else motion.COLLAPSE0
        tauV = vp * (motion.COLLAPSE1 - motion.COLLAPSE0)
        holdAfter = if (toPill) 1 else 0
    }

    /** 0 none, 1 docked, 2 pay pulse, 3 crossed the scrub midpoint. */
    fun step(dt: Double, now: Double, auto: Boolean): Int {
        if (mode == 0) {
            if (auto) t = motion.wrap(t + dt)
            gain = min(1.0, gain + dt / 0.8)
            nu = 1.0
            return 0
        }
        val prev = tau
        if (held != 1) {
            if (play == 1) {
                val k = ((tau - 0.42) / 0.2).coerceIn(0.0, 1.0)
                tau = min(playEnd, tau + dt * (1 + 1.2 * (1 - k * k * (3 - 2 * k))))
                if (tau >= playEnd) {
                    play = 0
                    tauTarget = playEnd
                    tauV = 0.0
                }
            } else {
                val length = motion.COLLAPSE1 - motion.COLLAPSE0
                val r = spring(tau / length, tauV / length, tauTarget / length, 170.0, 20.0, dt)
                tau = r[0] * length
                tauV = r[1] * length
                if (holdAfter == 1 && abs(tau - motion.COLLAPSE1) < 0.004 && abs(tauV) < 0.05) {
                    holdAfter = 0
                    tau = motion.COLLAPSE1
                    play = 1
                    playEnd = motion.PAID_HOLD
                }
            }
        }
        val speed = if (dt > 0) (tau - prev) / dt else 0.0
        nu += (speed.coerceIn(-2.5, 2.5) - nu) * min(1.0, dt * 30)
        if (abs(nu) < 0.02) nu = 0.0
        if (held != 2) {
            val r = spring(yaw, yawV, 0.0, yawK, yawC, dt)
            yaw = r[0]
            yawV = r[1]
        }
        if (held != 1) {
            val r = spring(band, bandV, 0.0, pressK, pressC, dt)
            band = r[0]
            bandV = r[1]
        }
        val pr = spring(press, pressV, pressTarget, pressK, pressC, dt)
        press = pr[0]
        pressV = pr[1]
        if (held != 3) {
            for (i in 0..1) {
                val r = spring(aOff[i], aOff[i + 2], 0.0, lightK, lightC, dt)
                aOff[i] = r[0]
                aOff[i + 2] = r[1]
            }
        }
        if (held != 4) {
            for (i in 0..1) {
                val r = spring(rOff[i], rOff[i + 2], 0.0, lightK, lightC, dt)
                rOff[i] = r[0]
                rOff[i + 2] = r[1]
            }
        }
        gain = max(0.0, gain - dt / 0.35)
        if (held == 0 && now - last > 2) {
            val tw = motion.wrap(tau)
            if (play == 0 && tw > motion.COLLAPSE0 + 1e-4 && tw < motion.COLLAPSE1 && holdAfter == 0) {
                tauTarget = motion.COLLAPSE0
            } else if (play == 0 && tw >= motion.COLLAPSE1 && tw < motion.EXPAND0) {
                tau = motion.EXPAND0
                play = 1
                playEnd = motion.EXPAND1
            }
            val settled = isRest(tau) && play == 0 && abs(tauV) < 0.02 && abs(yaw) < 0.05 &&
                abs(press - 1) < 0.002 && abs(band) < 0.002 &&
                sqrt(aOff[0].pow(2) + aOff[1].pow(2) + rOff[0].pow(2) + rOff[1].pow(2)) < 0.2
            if (settled) {
                mode = 0
                t = 0.0
                gain = 0.0
                yaw = 0.0
                aOff.fill(0.0)
                rOff.fill(0.0)
                press = 1.0
                nu = 1.0
            }
        }
        val a = motion.wrap(prev)
        val b = motion.wrap(tau)
        if (a < motion.COLLAPSE1 && b >= motion.COLLAPSE1 && b < motion.EXPAND0) return 1
        if (a < motion.PULSE && b >= motion.PULSE && b < motion.EXPAND0) return 2
        if (held == 1 && (motion.progressOf(a) - 0.5) * (motion.progressOf(b) - 0.5) < 0) return 3
        return 0
    }

    fun mods() = GlassPayMorphMotion.Mods(
        nu = if (mode == 0) 1.0 else nu,
        driftGain = gain, yaw = yaw, press = press * (1 - 0.45 * band),
        parallax = -0.12 * (yaw / YAW_PER_PT), aOff = aOff, rOff = rOff,
    )

    fun time() = if (mode == 0) t else tau
}
