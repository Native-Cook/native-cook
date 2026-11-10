package com.ngocdevv.nativecook.blocks.charts.rainwindowforecast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.hypot
import kotlin.math.pow

internal class RainWindowForecastDriver(private val auto: Boolean) {
    private val m = RainWindowForecastMotion
    var now = 0.0
    var rain = 0.0
    var clearing by mutableStateOf(false)
    private var manual = !auto
    private var points = MutableList(m.POINTS) { RainWindowForecastMotion.Point() }
    private var latest = RainWindowForecastMotion.Frame(points.toList())
    private var began = false
    private var lastTouch = -100.0
    private var fingerAt = -100.0
    private var fingerFrom = 0.0
    fun step(dt: Double, reduced: Boolean) {
        now += dt
        val previous = rain
        if (!reduced) rain = (rain + dt) % m.LOOP
        if (manual && auto && now - lastTouch > m.REGROW && rain < previous) {
            points = MutableList(m.POINTS) { RainWindowForecastMotion.Point() }
            manual = false
        }
        if (clearing && !began && now - lastTouch > 6) clearing = false
    }
    fun frame(w: Double, h: Double): RainWindowForecastMotion.Frame {
        if (!manual) latest = m.frame(rain, w, h)
        else {
            val p = m.seg(now - fingerAt, 0.0, 0.15)
            val q = if (p < 0.5) 2 * p * p else 1 - (-2 * p + 2).pow(2) / 2
            latest = latest.copy(wipe = points.map { it.copy(age = if (it.age < 0) -1.0 else now - it.age) }, finger = fingerFrom * (1 - q), press = 0.0)
        }
        return latest
    }
    private fun push(p: RainWindowForecastMotion.Point) {
        points.removeAt(0)
        points.add(p)
    }
    fun begin(x: Double, y: Double) {
        began = true
        lastTouch = now
        if (!manual) {
            points = latest.wipe.map { it.copy(age = if (it.age < 0) -1.0 else now - it.age) }.toMutableList()
            manual = true
            fingerFrom = latest.finger
            fingerAt = now
        }
        push(RainWindowForecastMotion.Point())
        push(RainWindowForecastMotion.Point(x, y, now))
        clearing = true
    }
    fun move(x: Double, y: Double) {
        lastTouch = now
        val p = points.last()
        if (hypot(x - p.x, y - p.y) >= 16) push(RainWindowForecastMotion.Point(x, y, now))
    }
    fun end() { began = false; lastTouch = now }
}
