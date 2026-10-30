package com.ngocdevv.nativecook.blocks.cards.sunuvbuddy

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.REST_HOUR
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.LOOP
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.RESUME
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.loopState
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.uvAt
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.sizeFor

/** Touch and frame callbacks both own this driver on the UI thread. */
internal class SunUvBuddyDriver(var t: Double = 0.0) {
    var mode = 0
    var last = -10.0
    var amb = t
    var hour = REST_HOUR
    var hourV = 0.0
    var hourAim = REST_HOUR
    var dragging = 0
    var from = 2
    var to = 2
    var at = -10.0
    var size = 1.0
    var sizeV = 0.0
    var squishAt = -1.0
    var lookX = 0.0
    fun step(dt: Double, now: Double, auto: Boolean): Double {
        amb = (amb + dt) % LOOP
        var base = now
        if (mode == 0) {
            if (auto) t = (t + dt) % LOOP
            val s = loopState(t)
            hourV = if (dt > 0) hourV + ((s.hour - hour) / dt - hourV) * 0.5 else 0.0
            hour = s.hour; from = s.from; to = s.to; at = s.at
            base = t
        } else {
            if (dragging == 0) {
                val k = 120.0
                val c = 2 * 0.8 * sqrt(k)
                hourV += (-k * (hour - hourAim) - c * hourV) * dt
                hour += hourV * dt
            }
            if (auto && dragging == 0 && now - last >= RESUME) {
                if (to != 2) { from = to; to = 2; at = now }
                hourAim = REST_HOUR
                if (abs(hour - REST_HOUR) < 0.01 && now - at > 0.9) { mode = 0; t = 7.6 }
            }
        }
        val uv = uvAt(hour, from, to, base - at)
        val k = 170.0
        val c = 2 * 0.32 * sqrt(k)
        sizeV += (-k * (size - sizeFor(uv)) - c * sizeV) * dt
        size += sizeV * dt
        return base
    }
    fun takeOver(now: Double) {
        last = now
        if (mode == 2) return
        mode = 2
        if (at > -9) at = now - (t - at)
        hourAim = floor(hour + 0.5)
        hourV = 0.0
    }
    fun pickDay(day: Int, now: Double): Boolean {
        if (day == to) return false
        from = to; to = day; at = now
        return true
    }
}
