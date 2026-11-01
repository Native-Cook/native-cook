package com.ngocdevv.nativecook.blocks.cards.cashbackcloudcard

import kotlin.math.*

internal class CashbackCloudCardDriver {
    var now = 0.0
    var hold = false
    private var squeeze = 0.0
    private var velocity = 0.0
    private var rainAt = -100.0
    private var lastTouch = -100.0
    private var scripted = false
    private var loopT = 0.0
    private var landed = 0
    fun touch(down: Boolean) {
        hold = down
        lastTouch = now
        if (down) scripted = false
    }
    fun step(wall: Double, auto: Boolean, buzz: (Boolean) -> Unit): CashbackCloudCardMotion.Frame {
        now += wall
        if (auto && !hold && now - lastTouch > 3.5 && (scripted || now - rainAt > 9)) {
            if (!scripted) { scripted = true; loopT = 0.0 }
            loopT = (loopT + wall) % 9
            return CashbackCloudCardMotion.at(loopT)
        }
        scripted = false
        val dt = min(wall, 0.05)
        velocity += (300 * ((if (hold) 1.0 else 0.0) - squeeze) - 12 * velocity) * dt
        squeeze += velocity * dt
        if (hold && squeeze > 0.8 && now - rainAt > 9) rainAt = now
        val rainU = if (now - rainAt < 9) now - rainAt else -1.0
        val n = (0 until 6).count { rainU >= 0 && CashbackCloudCardMotion.coinK(rainU, it) >= 1 }
        if (n != landed) { landed = n; if (n > 0) buzz(n == 6) }
        return CashbackCloudCardMotion.Frame(
            max(0.0, squeeze), rainU = if (rainU >= 8) -1.0 else rainU,
            refill = if (rainU >= 7) CashbackCloudCardMotion.smoother(rainU - 7) else 0.0)
    }
    fun moving() = hold || now - rainAt < 9 || abs(squeeze) > 0.00001 || abs(velocity) > 0.00001
}
