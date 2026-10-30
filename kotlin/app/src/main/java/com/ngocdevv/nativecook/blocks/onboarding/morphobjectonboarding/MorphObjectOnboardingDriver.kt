package com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.LOOP
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.RESUME
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.pageAt
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.pageRound
import com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding.MorphObjectOnboardingMotion.holdAt

/** The RN pager's numerical integrator, including retained release velocity and two substeps. */
internal class MorphObjectOnboardingDriver(var t: Double = 0.0) {
    var mode = 0
    var last = -10.0
    var amb = t
    var p = pageAt(t)
    var v = 0.0
    var aim = -1.0
    var dragFrom = 0.0
    var arriveAt = -1.0
    var hopAt = -1.0
    var press = 0.0
    var pressAim = 0.0
    var skipping = 0
    fun step(dt: Double, now: Double, auto: Boolean): Double {
        amb = (amb + dt) % LOOP
        press += (pressAim - press) * (1 - exp(-dt * if (pressAim > 0) 40 else 16))
        if (mode == 0) {
            if (auto) t = (t + dt) % LOOP
            p = pageAt(t)
            return -1.0
        }
        var arrived = -1.0
        if (aim >= 0) {
            val k = if (skipping != 0) 160.0 else 110.0
            val c = 2 * (if (skipping != 0) 0.95 else 0.78) * sqrt(k)
            val h = dt / 2
            repeat(2) { v += (-k * (p - aim) - c * v) * h; p += v * h }
            if (abs(p - aim) < 0.002 && abs(v) < 0.02) {
                p = aim; v = 0.0; arrived = aim; aim = -1.0; skipping = 0
            }
        }
        if (auto && aim < 0 && now - last >= RESUME && abs(p - pageRound(p)) < 0.001) {
            t = holdAt[pageRound(p).toInt()] + 0.001; mode = 0
        }
        return arrived
    }
    fun grab(now: Double) {
        last = now
        if (mode == 0) { p = pageAt(t); v = 0.0 }
        mode = 2; aim = -1.0; dragFrom = p
    }
    fun release(vPages: Double) {
        val from = pageRound(dragFrom)
        aim = pageRound(p + vPages * 0.18).coerceIn(from - 1, from + 1).coerceIn(0.0, 2.0)
        v = vPages
    }
    fun goTo(page: Double, now: Double, skip: Boolean = false) {
        if (mode == 0) { p = pageAt(t); v = 0.0 }
        mode = 2; last = now; aim = page; skipping = if (skip) 1 else 0
    }
}
