package com.ngocdevv.nativecook.blocks.charts.spiketostairchart

import kotlin.math.*

internal data class ChartPose(
    val x: Double,
    val y: Double,
    val sy: Double,
    val angle: Double,
    val alpha: Double,
)

private val motivationTimes =
    doubleArrayOf(0.0, .7, .87, 1.04, 1.2, 1.37, 1.54, 1.7, 1.87, 2.04, 2.2)
private val motivationUs = doubleArrayOf(0.0, .27, .31, .38, .52, .62, .78, .85, .92, .945, .957)

internal fun motivationU(s: Double): Double {
    val times = motivationTimes
    val us = motivationUs
    var i = 0
    while (i < times.size - 2 && s > times[i + 1]) i++
    val dt = times[i + 1] - times[i]
    val p = clamp((s - times[i]) / dt)
    val p2 = p * p
    val p3 = p2 * p
    val slope = (us[i + 1] - us[i]) / dt
    val before = if (i > 0) min(slope, (us[i] - us[i - 1]) / (times[i] - times[i - 1])) else slope
    val after =
        if (i < times.size - 2) min(slope, (us[i + 2] - us[i + 1]) / (times[i + 2] - times[i + 1]))
        else 0.0
    return (2 * p3 - 3 * p2 + 1) * us[i] +
        (p3 - 2 * p2 + p) * dt * before +
        (-2 * p3 + 3 * p2) * us[i + 1] +
        (p3 - p2) * dt * after
}

internal fun piecePose(l: Double, i: Int): ChartPose {
    val p = clamp((l - 7.05 - (5 - i) * .045) / .3)
    val bounce = if (p > .85) sin((p - .85) / .15 * PI) * .06 else 0.0
    val settle = smooth((p - .7) / .3)
    val flatten = mix(1.0, .03, settle)
    val piece = ChartGeometry.pieces[i]
    val angle = (if (i % 2 == 1) 1 else -1) * PI / 30 * sin(PI * p)
    val bottomOffset =
        (piece.bottom - piece.cy) * flatten * cos(angle) +
            (piece.right - piece.left) / 2 * abs(sin(angle))
    return ChartPose(
        piece.cx,
        min(mix(piece.cy, 510.0, p * p - bounce), 510 - bottomOffset),
        flatten,
        angle,
        mix(1.0, .6, settle),
    )
}

internal fun shardPose(l: Double, i: Int, still: Boolean = false, life: Double = l): ChartPose {
    val rest = ChartGeometry.shards[i]
    var p = 1.0
    var alpha = 1.0
    if (l >= 3.6 && l < 9.41) {
        p = 1 - smooth((l - 3.6) / .35)
        alpha = p
    }
    if (l >= 9.41) {
        p = 1 - exp(-(l - 9.41) / .22)
        alpha = smooth((l - 9.41) / .08)
    }
    if (l > 11.4) p = mix(p, 1.0, smooth((l - 11.4) / .6))
    val bob = if (still) 0.0 else sin(life * PI / 2 + i * 1.7) * 2
    return ChartPose(
        mix(330.0, rest[0] + bob, p),
        mix(250.0, rest[1] + (if (still) 0.0 else cos(life * PI / 2 + i) * 2), p),
        1.0,
        (rest[3] + (1 - p) * (40 + 16 * i) + (if (still) 0.0 else 3 * sin(life * PI / 2 + i))) *
            PI / 180,
        alpha * rest[4],
    )
}

internal fun demoU(l: Double): Double =
    when {
        l < 1.05 -> .8
        l < 2.3 -> mix(.8, .16, filmEase((l - 1.05) / 1.15))
        l < 3.05 -> mix(.16, .62, filmEase((l - 2.3) / .75))
        else -> mix(.62, nearestTread(.62), springProgress(l - 3.05, 22.0, 240.0))
    }

internal data class ChartFilm(
    val l: Double,
    var life: Double,
    val d: Int,
    val end: Double,
    val headU: Double,
    val hx: Double,
    val hy: Double,
    val angle: Double,
    val area: Double,
    val broken: Double,
    val heat: Double,
    val heatEnd: Double,
    val gather: Double,
    val label: Double,
    val grid: Double,
    val thumb: Double,
    val r: Double,
    val g: Double,
    val b: Double,
    val coreR: Double,
    val coreG: Double,
    val coreB: Double,
    val axisLight: Double,
    val heatStart: Double,
    val comet: Double,
    val gatherTime: Double,
    val gatherFlash: Double,
    var glow: Double,
    val pool: Double,
    val jolt: Double,
    val dip: Double,
    val flash: Double,
    val glitch: Double,
    var packet: Double,
    var packetStart: Double,
    var packetEnd: Double,
    val markerU: Double,
    val markerAlpha: Double,
    val arrow: Double,
)

internal fun film(time: Double, still: Boolean = false): ChartFilm {
    val l = ((time % 12) + 12) % 12
    var d = 1
    var end = 1.0
    var mood = 2.0
    var area = 1.0
    var broken = 0.0
    var heat = 0.0
    var heatEnd = 0.0
    var gather = 0.0
    var label = 1.0
    var grid = 1.0
    var thumb = 1.0
    if (l >= 3.6 && l < 4.15) {
        end = 1 - filmEase((l - 3.6) / .5)
        mood =
            if (l < 4.1) mix(2.0, 1.0, smooth((l - 3.6) / .5))
            else mix(1.0, 2.0, smooth((l - 4.1) / .05))
        label = 1 - smooth((l - 3.7) / .45)
        grid = 1 - smooth((l - 3.6) / .55)
        thumb = 1 - springProgress(l - 3.6, 20.0, 260.0)
    }
    if (l >= 4.15 && l < 8.07) {
        d = 0
        label = 0.0
        grid = 0.0
        thumb = 0.0
        val s = l - 4.15
        end = atU(0, motivationU(s)).a
        mood =
            when {
                s < .8 -> 2.0
                s < 1 -> 2 * (1 - smooth((s - .8) / .2))
                s < 1.12 -> 0.0
                s < 1.3 -> 2 * smooth((s - 1.12) / .18)
                s < 1.62 -> 2.0
                s < 1.86 -> 2 * (1 - smooth((s - 1.62) / .24))
                else -> 0.0
            }
        if (l >= 6.95) thumb = springProgress(l - 6.95, 20.0, 260.0)
        if (l >= 7.05) {
            broken = smooth((l - 7.05) / .04)
            area = 1 - smooth((l - 7.05) / .18)
        }
        label = smooth((l - 7.35) / .65)
        grid = smooth((l - 7.35) / .6)
        if (l >= 7.57) {
            heat = 1.0
            heatEnd = 1 - filmEase((l - 7.57) / .42)
            end = heatEnd
            broken = 1 - smooth((l - 7.57) / .04)
        }
        if (l >= 7.99) {
            heat = 1.0
            heatEnd = 0.0
            gather = sin(PI * clamp((l - 7.99) / .08))
        }
    }
    if (l >= 8.07 && l < 9.59) {
        mood =
            if (l < 8.55) 0.0
            else if (l < 8.72) smooth((l - 8.55) / .17) else 1 + smooth((l - 8.72) / .23)
        end =
            if (l < 9.23) {
                val t = l - 8.07
                val distance = if (t < .15) .6 * t + .4 * (t * t / (2 * .15)) else t - .03
                ChartGeometry.stairEnd * clamp(distance / 1.13)
            } else {
                val t = clamp((l - 9.23) / .36)
                mix(ChartGeometry.stairEnd, 1.0, (t + t * t) / 2)
            }
    }
    val head = atArc(d, end)
    var hx = head.x
    var hy = head.y
    var angle = head.angle
    if (l >= 7.05 && l < 7.57) {
        val p = piecePose(l, 5)
        val dx = head.x - ChartGeometry.pieces[5].cx
        val dy = (head.y - ChartGeometry.pieces[5].cy) * p.sy
        hx = p.x + dx * cos(p.angle) - dy * sin(p.angle)
        hy = p.y + dx * sin(p.angle) + dy * cos(p.angle)
        angle = mix(head.angle, PI, smooth((l - 7.05) / .3))
    }
    if (l >= 7.57 && l < 8.07) {
        hx = 64 + 274 * .957 * heatEnd
        hy = 510.0
        angle = mix(PI, 2 * PI + atArc(1, 0.0).angle, smooth((l - 7.99) / .08))
    }
    val impact = pulse(l - 9.41, .025, .3)
    val crash = pulse(l - 6.35, .02, .4)
    fun channel(red: Double, amber: Double, green: Double) =
        if (mood < 1) mix(red, amber, clamp(mood)) else mix(amber, green, clamp(mood - 1))
    val r = channel(255.0, 230.0, 40.0)
    val g = channel(45.0, 198.0, 224.0)
    val b = channel(61.0, 90.0, 122.0)
    val coreR = mix(r + (255 - r) * .55, 182.0, impact * .6)
    val coreG = mix(g + (255 - g) * .55, 255.0, impact * .6)
    val coreB = mix(b + (255 - b) * .55, 228.0, impact * .6)
    var heatStart = 1.0
    for (i in 0..5) {
        val landed = smooth((l - (7.05 + (5 - i) * .045 + .27)) / .025)
        val p = ChartGeometry.pieces[i]
        heatStart -= (p.right - p.left) / (274 * .957) * landed
    }
    heatStart = clamp(heatStart)
    val axisLight = smooth((l - 7.32) / .025) * (1 - smooth((l - 8.07) / .08))
    if (l >= 7.32 && l < 7.57) {
        heat = axisLight
        heatEnd = 1.0
    }
    val comet = smooth((l - 7.32) / .25) * (1 - smooth((l - 8.07) / .08))
    val gatherTime = clamp((l - 7.99) / .16)
    val gatherFlash = smooth((l - 7.99) / .008) * (1 - gatherTime)
    val idle = (1 - smooth((l - 3.5) / .1)) * d + smooth((l - 10) / .45) * d
    val breathe = if (still) 1.0 else 1 + .06 * sin(l * PI / 2) * idle
    val ember =
        if (l >= 6.35 && l < 7.05)
            sin((l - 6.35) * 53) *
                sin((l - 6.35) * 19) *
                .1 *
                smooth((l - 6.35) / .1) *
                (1 - smooth((l - 6.95) / .1))
        else 0.0
    val jolt =
        if (still || l < 9.41) 0.0
        else (1 - springProgress(l - 9.41, 14.0, 320.0)) * smooth((l - 9.41) / .02)
    val dip = if (!still && l >= 6.35 && l < 7.05) 3 * pulse(l - 6.35, .035, .28) else 0.0
    val phase = (l % 4) / 1.6
    val packet = if (still) 0.0 else idle * smooth(phase / .08) * (1 - smooth((phase - .92) / .08))
    return ChartFilm(
        l,
        l,
        d,
        end,
        head.u,
        hx,
        hy,
        angle,
        area * (1 + 1.2 * pulse(l - 9.41, .025, .35)),
        broken,
        heat,
        heatEnd,
        gather,
        label,
        grid,
        thumb,
        r,
        g,
        b,
        coreR,
        coreG,
        coreB,
        axisLight,
        heatStart,
        comet,
        gatherTime,
        gatherFlash,
        breathe + ember,
        max(1 + 1.5 * max(impact, crash), 1 + axisLight),
        jolt,
        dip,
        impact,
        pulse(l - 9.41, .02, .16),
        packet,
        clamp(phase - .06),
        clamp(phase),
        demoU(l),
        if (still) 0.0 else smooth((l - .9) / .15) * (1 - smooth((l - 3.3) / .2)),
        if (d == 1) 1 - smooth((end - .985) / .015) else 1.0,
    )
}

internal object ChartColumnEvents {
    val times =
        ChartGeometry.risers.map { u ->
            var lo = 8.07
            var hi = 9.59
            repeat(24) {
                val t = (lo + hi) / 2
                if (film(t, true).headU < u) lo = t else hi = t
            }
            var lo1 = 1.05
            var hi1 = 2.2
            repeat(24) {
                val t = (lo1 + hi1) / 2
                if (demoU(t) > u) lo1 = t else hi1 = t
            }
            var lo2 = 2.3
            var hi2 = 3.05
            repeat(24) {
                val t = (lo2 + hi2) / 2
                if (demoU(t) < u) lo2 = t else hi2 = t
            }
            doubleArrayOf(
                (lo + hi) / 2,
                if (u in 0.16..0.8) (lo1 + hi1) / 2 else -100.0,
                if (u in 0.16..0.62) (lo2 + hi2) / 2 else -100.0,
            )
        }
}

internal fun columnFlash(l: Double, i: Int) =
    ChartColumnEvents.times[i].maxOf { pulse(l - it, .12, .4) }

internal fun sparkPoint(l: Double, i: Int): Pair<Double, Double> {
    var t = l - 6.35
    var x = 64 + .957 * 274
    var active = i < 12
    if (l >= 7.05) {
        val piece = i / 5
        t = l - (7.05 + (5 - piece) * .045 + .3)
        x = ChartGeometry.pieces[piece].cx + ((i * 29) % 13 - 6) * 3
        active = i % 5 < 3
    }
    val vertical = sqrt(2.0 * 1400 * (10 + (i * 17) % 31))
    val horizontal = ((i * 37) % 17 - 8) * 7
    if (!active || t < 0 || t > 2 * vertical / 1400) return -100.0 to -100.0
    return x + horizontal * t to 510 - vertical * t + .5 * 1400 * t * t
}
