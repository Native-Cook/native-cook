package com.ngocdevv.nativecook.blocks.onboarding.wavyframeauction

import kotlin.math.*

internal class WavyFrameAuctionDriver {
    var t = 0.0
    var now = 0.0
    var last = -10.0
    var s = 0.0
    var v = 0.0
    var target = 0.0
    var held = false
    var lift = 0.0
    var seconds = 82354
    private var nextSecond: Double? = null
    val moving get() = held || abs(s - target) > 0.00001 || abs(v) > 0.0001 || lift > 0.0001
    fun step(wall: Double, auto: Boolean, reduced: Boolean) {
        now += wall
        val dt = min(wall, 0.05)
        if (auto && !held && now - last >= 2) {
            t = (t + wall) % WavyFrameAuctionMotion.LOOP
            s = WavyFrameAuctionMotion.script(t); target = if (s > 0.5) 1.0 else 0.0; v = 0.0
        } else if (!held) {
            val k = if (reduced) 600.0 else 170.0
            v += (k * (target - s) - 2 * sqrt(k) * 0.72 * v) * dt
            s += v * dt
        }
        if (held) lift += (1 - lift) * (1 - exp(-dt * 10))
        else if (!reduced && auto && now - last >= 2) lift = WavyFrameAuctionMotion.lift(t)
        else lift += -lift * (1 - exp(-dt * 10))
        if (s > 0.6) {
            if (nextSecond == null) nextSecond = now + 1
            val tick = nextSecond!!
            if (now >= tick) { seconds = max(0, seconds - 1); nextSecond = tick + 1 }
        } else nextSecond = null
    }
}
