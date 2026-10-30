package com.ngocdevv.nativecook.blocks.sheets.goldticketreward

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.LOOP
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.RESUME
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.PEAK
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.COIN_FRAMES
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.SCRUB_DIST
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.SCRUB_END
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.COMMIT
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.T
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.inRestA
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.inRewardB


/** Mutable touch state is owned by Compose's UI thread, as in the RN worklet driver. */
internal class GoldTicketRewardDriver(var t: Double = 0.0) {
    var mode = 0
    var stop = 0.0
    var rate = 1.0
    var last = - 10.0
    var ctaAt = - 1.0
    var ctaAim = 0.0
    var ctaP = 0.0
    var tiltX = 0.0
    var tiltY = 0.0
    var aimX = 0.0
    var aimY = 0.0
    var lift = 0.0
    var liftAim = 0.0
    var spinPos = 0.0
    var spinVel = 0.0
    var settling = 0
    var spinTarget = 0.0
    var hopAt = - 1.0
    var hopAmp = 0.0

    private fun follow(cur: Double, aim: Double, k: Double, dt: Double) = cur + (aim - cur) * (1 - exp(-dt * k))

    fun step(dt: Double, now: Double, auto: Boolean): Double {
        if (mode == 1) {
            t += dt * rate
            if (rate >= 0 && t >= stop) {
                t = if (stop >= LOOP) 0.0 else stop
                mode = 2
            } else if (rate < 0 && t <= stop) {
                t = 0.0
                mode = 2
                rate = 1.0
            }
        } else if (mode == 2) {
            if (auto && now - last >= RESUME) {
                mode = 0
                rate = 1.0
            }
        } else if (mode == 0 && auto) t = (t + dt)%LOOP
        tiltX = follow(tiltX, aimX, 12.0, dt)
        tiltY = follow(tiltY, aimY, 12.0, dt)
        lift = follow(lift, liftAim, if (mode == 3) 30.0 else 9.0, dt)
        ctaP = follow(ctaP, ctaAim, if (ctaAim > 0) 40.0 else 18.0, dt)
        if (settling != 0 || spinVel != 0.0) repeat(2) {
            val h = dt / 2
            if (settling == 0) {
                spinPos += spinVel * h
                spinVel *= exp(-2.1 * h)
                if (abs(spinVel) < 26) {
                    settling = 1
                    spinTarget = (if (spinVel >= 0) ceil(spinPos / COIN_FRAMES) else floor(spinPos / COIN_FRAMES)) * COIN_FRAMES
                }
            } else {
                val k = 230.0
                val c = 2 * 0.32 * sqrt(k)
                spinVel += (-k * (spinPos - spinTarget) - c * spinVel) * h
                spinPos += spinVel * h
                if (abs(spinPos - spinTarget) < 0.03 && abs(spinVel) < 0.4) {
                    spinPos = 0.0
                    spinVel = 0.0
                    settling = 0
                }
            }
        }
        return t
    }

    fun touched(now: Double) {
        last = now
        if (mode != 0) return
        rate = 1.0
        if (t >= T.press[0] && t < PEAK) {
            mode = 1
            stop = PEAK
        } else if (t >= T.hold[1] && t < T.dividerIn[1]) {
            mode = 1
            stop = LOOP
        } else mode = 2
    }

    fun pressA(): Boolean {
        if (!inRestA(t)) return false
        t = T.press[0]
        mode = 1
        rate = 1.0
        stop = T.commit - 0.001
        return true
    }

    fun scrubShare(dy: Double): Double {
        val raw = - dy / SCRUB_DIST
        if (raw <= 0) return 0.0
        return if (raw <= 1) raw else 1 + (raw - 1) / (1 + (raw - 1) * 4) * 0.25
    }

    fun scrubTo(dy: Double): Double {
        val p = scrubShare(dy)
        mode = 3
        t = T.commit + min(p, 1.0) * (SCRUB_END - T.commit)
        liftAim = - min(max(-dy, 0.0), SCRUB_DIST * 1.4) * 0.12
        return p
    }

    fun scrubEnd(dy: Double, vy: Double): Boolean {
        val p = scrubShare(dy)
        liftAim = 0.0
        mode = 1
        if (p >= COMMIT || vy < -600) {
            rate = 1 + min(0.6, max(0.0, - vy) / 3000)
            stop = PEAK
            return true
        }
        rate = - 2.2
        stop = T.press[0]
        return false
    }

    fun releaseA(onTarget: Boolean) {
        if (onTarget) {
            mode = 1
            rate = 1.0
            stop = PEAK
        } else {
            t = 0.0
            mode = 2
        }
    }

    fun closeB(): Boolean {
        if (!inRewardB(t)) return false
        t = T.hold[1]
        mode = 1
        rate = 1.6
        stop = LOOP
        return true
    }

    fun flickCoin(vx: Double, now: Double) {
        val v = if (abs(vx) < 60) 110.0 else (vx * 0.14).coerceIn(-300.0, 300.0)
        spinVel = if (settling == 1) v else spinVel + v
        settling = 0
        hopAt = now
        hopAmp = min(16.0, 6 + abs(v) / 25)
    }

    fun hop(now: Double): Double {
        val p = if (hopAt < 0) 1.0 else (now - hopAt) / 0.55
        return if (p < 1) hopAmp * sin(PI * p) else 0.0
    }

    fun twinkle(now: Double): Double {
        if (ctaAt < 0) return 0.0
        val p = (now - ctaAt) / 0.6
        if (p >= 1) {
            ctaAt = - 1.0
            return 0.0
        }
        return p
    }
}
