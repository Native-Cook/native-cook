package com.ngocdevv.nativecook.blocks.pickers.islandsleepdial

import kotlin.math.*

internal object IslandSleepDialMotion {
    const val LOOP = 9.0
    data class Frame(val h: Double = 6.0, val cloud: Double = 0.0, val t: Double = 0.0, val drag: Double = 0.0)
    fun smoother(k: Double): Double {
        val x = k.coerceIn(0.0, 1.0)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    fun at(time: Double): Frame {
        val t = time % LOOP
        val h = when {
            t >= 1 && t < 3 -> 6 + 3 * smoother((t - 1) / 2)
            t >= 3 && t < 3.6 -> 9.0
            t >= 3.6 && t < 7.6 -> 9 - smoother(t - 3.6)
            t >= 7.6 && t < 8.8 -> 8 - 2 * smoother((t - 7.6) / 1.2)
            else -> 6.0
        }
        val cloud = if (t >= 5.6 && t < 7.6) smoother((t - 5.6) / 0.8) * (1 - smoother((t - 7) / 0.6)) else 0.0
        return Frame(h, cloud, time)
    }
    fun quarter(h: Double) = floor(h * 4 + 0.5).toInt()
    fun duration(h: Double): String {
        val m = quarter(h) * 15
        return "%dh %02dm".format(java.util.Locale.US, m / 60, m % 60)
    }
    fun wake(h: Double): String {
        val m = (23 * 60 + quarter(h) * 15) % (24 * 60)
        val hh = m / 60
        return "%d:%02d %s".format(java.util.Locale.US, if (hh == 0) 12 else if (hh > 12) hh - 12 else hh, m % 60, if (hh < 12) "AM" else "PM")
    }
}

internal class IslandSleepDialDriver(private val buzz: (Boolean) -> Unit) {
    var now = 0.0
    var h = 6.0
    var cloud = 0.0
    var drag = 0.0
    var dragK = 0.0
    private var a0 = 0.0
    private var h0 = 6.0
    private var lastTouch = -100.0
    private var scripted = false
    private var loopT = 0.0
    private var lastStep = 24
    private var setAt = -10.0
    fun step(wall: Double, auto: Boolean): IslandSleepDialMotion.Frame {
        now += wall
        dragK += (drag - dragK) * (1 - exp(-12 * min(wall, 0.05)))
        if (auto && drag == 0.0 && now - lastTouch > 3.5 && (scripted || (abs(h - 6) < 0.01 && cloud < 0.001))) {
            if (!scripted) { scripted = true; loopT = 0.0 }
            loopT = (loopT + wall) % IslandSleepDialMotion.LOOP
            val f = IslandSleepDialMotion.at(loopT)
            h = f.h; cloud = f.cloud
        } else {
            scripted = false
            val u = now - setAt
            cloud = if (u >= 0 && u < 2) IslandSleepDialMotion.smoother(u / 0.8) * (1 - IslandSleepDialMotion.smoother((u - 1.4) / 0.6)) else 0.0
            val step = IslandSleepDialMotion.quarter(h)
            if (step != lastStep) { lastStep = step; buzz(false) }
        }
        return IslandSleepDialMotion.Frame(h, cloud, now, dragK)
    }
    fun start(x: Double, y: Double, cx: Double, cy: Double) {
        drag = 1.0; scripted = false; a0 = atan2(y - cy, x - cx); h0 = h
    }
    fun move(x: Double, y: Double, cx: Double, cy: Double) {
        val a = atan2(y - cy, x - cx)
        var d = a - a0
        if (d > PI) d -= PI * 2
        if (d < -PI) d += PI * 2
        a0 = a
        h0 = (h0 + d / (PI * 2) * 12).coerceIn(3.7, 11.3)
        h = (IslandSleepDialMotion.quarter(h0) / 4.0).coerceIn(4.0, 11.0)
        lastTouch = now
    }
    fun end() { drag = 0.0; lastTouch = now }
    fun setAlarm() { buzz(true); setAt = now; scripted = false; lastTouch = now }
    fun adjust(delta: Double) {
        h = (h + delta).coerceIn(4.0, 11.0); scripted = false; lastTouch = now
        lastStep = IslandSleepDialMotion.quarter(h); buzz(false)
    }
}
