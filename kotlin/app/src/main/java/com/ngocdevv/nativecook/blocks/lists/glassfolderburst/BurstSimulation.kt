package com.ngocdevv.nativecook.blocks.lists.glassfolderburst

import kotlin.math.*

/** Fixed-step row dynamics. Commands preserve progress and velocity during reversals. */
internal class BurstSimulation(val n: Int, val reduced: Boolean) {
    class Row {
        var p = 0.0
        var v = 0.0
        var m = 0.0
        var trigger = 0.0
        var active = false
        var landed = false
        var ejected = false
        var fade = 0.0
        var modeFrom = 0.0
        var modeAt = 0.0
        var flash = 0.0
    }
    val rows = List(n) { Row() }
    var time = 0.0
    var accumulator = 0.0
    var target = 0
    var completed = true
    var flow = 0.0
    var bloomX = 0.0
    var bloomY = 0.0
    var bloomStrength = 0.0

    fun command(open: Boolean) {
        val goal = if (open) 1 else 0
        if (target == goal) return
        target = goal
        completed = false
        rows.forEachIndexed { i, r ->
            r.trigger = time + accumulator + (if (reduced) .030 else if (open) .045 else .050) * (if (open) i else n - 1 - i)
            r.active = false
            r.landed = r.p >= 1
            r.ejected = r.p >= .06
        }
    }
    fun step(dt: Double, layout: BurstLayout, folder: BurstPose.Folder): Int {
        accumulator += dt.coerceIn(0.0, .05)
        var events = 0
        val h = 1.0 / 240
        while (accumulator + 1e-10 >= h) {
            accumulator = max(0.0, accumulator - h)
            time += h
            var near = 0
            var flying = 0
            var weight = 0.0
            var sumX = 0.0
            var sumY = 0.0
            var allOut = true
            var allIn = true
            rows.forEachIndexed { i, r ->
                var p = r.p
                var v = r.v
                val rate = 1 / (1 / .70 + (1 / .98 - 1 / .70) * i / 8)
                r.flash = max(0.0, r.flash - h / .140)
                if (!completed && time + 1e-10 >= r.trigger) {
                    if (!r.active) {
                        r.active = true
                        r.modeFrom = r.m
                        r.modeAt = time
                        if (!reduced && target == 1 && p <= 0) v = max(v, .8 * rate)
                    }
                    val blend = BurstPose.smooth((time - r.modeAt) / .180)
                    r.m = r.modeFrom + (1 - target - r.modeFrom) * blend
                    if (reduced) {
                        p = target.toDouble()
                        v = 0.0
                        r.fade = if (target == 1) min(1.0, r.fade + h / .200) else max(0.0, r.fade - h / .200)
                        if (target == 1 && !r.landed) {
                            events = events or LAND
                            r.landed = true
                        }
                        if (target == 0 && r.p > 0) events = events or GULP
                    } else if (target == 1) {
                        v += (38 * rate * rate * (1 - p) - 10 * rate * v) * h
                        p += v * h
                        if (p < 0) {
                            p = 0.0
                            v = .8
                        }
                        if (!r.ejected && p >= .06) {
                            events = events or EJECT
                            r.ejected = true
                        }
                        if (!r.landed && p >= 1) {
                            events = events or LAND
                            r.landed = true
                            r.flash = 1.0
                        }
                        if (abs(p - 1) < .003 && abs(v) < .02) {
                            p = 1.0
                            v = 0.0
                        }
                    } else if (p > 0 || v > 0) {
                        v += (-6.3 / (max(0.0, p) + .14) - 1.2 * v) * h
                        p += v * h
                        if (p <= 0) {
                            p = 0.0
                            v = 0.0
                            events = events or GULP
                        }
                    }
                }
                r.p = p
                r.v = v
                if (abs(p - 1) >= .003 || abs(v) >= .02 || (reduced && r.fade < 1)) allOut = false
                if (p != 0.0 || v != 0.0 || (reduced && r.fade > 0)) allIn = false
                if (!reduced && p > 0 && p < .22 && abs(v) > .02) near++
                if (!reduced && p > .015 && (abs(p - 1) > .003 || abs(v) > .02)) {
                    flying++
                    val brightness = .12 + sin(PI * min(1.0, p)) * .88
                    weight += brightness
                    sumX += BurstPose.centerX(p, r.m, i, layout, folder.cx, folder.scale) * brightness
                    sumY += BurstPose.centerY(p, r.m, i, layout, folder.cy, folder.scale) * brightness
                }
            }
            flow += (min(1.0, near / 3.0) - flow) * (1 - exp(-18 * h))
            bloomStrength += (flying.toDouble() / n - bloomStrength) * (1 - exp(-12 * h))
            if (weight > 0) {
                bloomX = sumX / weight
                bloomY = sumY / weight
            }
            if (!completed && ((target == 1 && allOut) || (target == 0 && allIn))) {
                events = events or if (target == 1) ALL_OUT else ALL_IN
                completed = true
            }
        }
        return events
    }
    companion object {
        const val EJECT = 1
        const val LAND = 2
        const val GULP = 4
        const val ALL_OUT = 8
        const val ALL_IN = 16
    }
}
