package com.ngocdevv.nativecook.blocks.charts.isometricscoregauge

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeMotion.Frame
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeTheme.Gauge
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeTheme.UI
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeTheme.columns

internal class IsometricScoreGaugeDriver(initialTime: Double = 0.0) {
    var t = initialTime
    var mode = 0
    var last = -10.0
    var amb = initialTime
    var scroll = IsometricScoreGaugeMotion.frameAt(t).scroll
    var scrollV = 0.0
    var dragging = 0
    var aim = 0.0
    var sel = -1
    var selW = 0.0
    var score = IsometricScoreGaugeMotion.frameAt(t).score
    var scoreV = 0.0
    var goal = score
    private val maxScroll = UI.pitch
    private val minScroll = -UI.pitch * 1.6
    fun frame() = Frame(scroll, sel, selW, score)
    fun stepDriver(dt: Double, now: Double, auto: Boolean) {
        amb = (amb + dt) % IsometricScoreGaugeMotion.LOOP
        if (mode == 0) {
            if (auto) t = (t + dt) % IsometricScoreGaugeMotion.LOOP
            val frame = IsometricScoreGaugeMotion.frameAt(t)
            scroll = frame.scroll; sel = frame.sel; selW = frame.selW; score = frame.score; goal = frame.score
            return
        }
        if (dragging == 0) {
            val k = 90.0
            val c = 2 * 0.86 * sqrt(k)
            scrollV += (-k * (scroll - aim) - c * scrollV) * dt
            scroll += scrollV * dt
        }
        val k2 = 60.0
        val c2 = 2 * 0.72 * sqrt(k2)
        scoreV += (-k2 * (score - goal) - c2 * scoreV) * dt
        score += scoreV * dt
        selW += ((if (sel >= 0) 1 else 0) - selW) * (1 - exp(-dt * 14))
        if (auto && dragging == 0 && now - last >= IsometricScoreGaugeMotion.RESUME && sel < 0 && abs(score - Gauge.score) < 0.5) {
            aim = 0.0
            if (abs(scroll) < 0.5) { mode = 0; t = 8.2 }
        }
    }
    fun takeOver(now: Double) {
        last = now
        if (mode == 2) return
        mode = 2
        scrollV = 0.0
        aim = floor(scroll / UI.pitch + 0.5) * UI.pitch
        goal = if (sel >= 0) Gauge.score + columns[sel / 2][sel % 2].delta else Gauge.score
    }
    fun release(velocity: Double) {
        dragging = 0
        scrollV = velocity
        // floor(x + .5) preserves JavaScript Math.round for negative half detents.
        aim = (floor((scroll + velocity * 0.2) / UI.pitch + 0.5) * UI.pitch).coerceIn(minScroll, maxScroll)
    }
    fun dragTo(from: Double, dx: Double) {
        var value = from + dx
        if (value > maxScroll) value = maxScroll + (value - maxScroll) * 0.3
        if (value < minScroll) value = minScroll + (value - minScroll) * 0.3
        scroll = value
        dragging = 1
    }
    fun choose(index: Int): Int {
        sel = if (sel == index) -1 else index
        val delta = if (sel >= 0) columns[sel / 2][sel % 2].delta else 0
        goal = Gauge.score + delta
        return delta
    }
}
