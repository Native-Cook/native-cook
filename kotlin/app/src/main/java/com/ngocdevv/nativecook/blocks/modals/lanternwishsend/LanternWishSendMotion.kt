package com.ngocdevv.nativecook.blocks.modals.lanternwishsend

import kotlin.math.*

internal object LanternWishSendMotion {
    const val LOOP = 9.0
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun smoother(k: Double): Double {
        val x = clamp(k)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
    data class Frame(
        val fold: Double = 0.0, val light: Double = 0.0, val rise: Double = 0.0,
        val sending: Boolean = false, val t: Double = 0.0, val flick: Double = 0.0,
        val back: Double = 1.0,
    ) { val count get() = if (rise > 0.9 && sending) 1249 else 1248 }
    fun sendAt(u: Double) = Frame(smoother(u / 0.8), smoother((u - 0.8) / 0.8),
        if (u > 1.6) clamp((u - 1.6) / 3.8) else 0.0)
    fun at(time: Double): Frame {
        val t = time % LOOP
        val sending = t >= 1 && t < 6.8
        return sendAt(t - 1).copy(sending = sending, t = time,
            back = if (sending) 0.0 else if (t < 1) 1.0 else smoother((t - 6.8) / 1.6))
    }
}

internal class LanternWishSendDriver {
    var now = 0.0
    var at = -100.0
    var flick = 0.0
    var lastTouch = -100.0
    var scripted = false
    var loopT = 0.0
    fun step(wall: Double, auto: Boolean): LanternWishSendMotion.Frame {
        now += wall
        flick *= exp(-3 * min(wall, 0.05))
        if (auto && now - at >= 6.2 && now - lastTouch > 3.5) {
            if (!scripted) { scripted = true; loopT = 0.0 }
            loopT = (loopT + wall) % LanternWishSendMotion.LOOP
            return LanternWishSendMotion.at(loopT).copy(t = now)
        }
        scripted = false
        val u = now - at
        val sending = u >= 0 && u < 6
        return LanternWishSendMotion.sendAt(u * if (u > 1.6) 1 + flick * 0.6 else 1.0)
            .copy(sending = sending, t = now, flick = flick,
                back = if (sending) 0.0 else LanternWishSendMotion.smoother(u - 6))
    }
    fun send() {
        if (now - at < 6.2) return
        at = now; scripted = false; lastTouch = now
    }
    fun flickUp(velocity: Float) {
        if (velocity < -400) { flick = 1.0; lastTouch = now }
    }
}
