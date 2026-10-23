package com.ngocdevv.nativecook.blocks.lists.glassfolderburst

import kotlin.math.*

internal object BurstPose {
    data class Folder(
        val cx: Double, val cy: Double, val scale: Double,
        val open: Double, val press: Double, val squash: Double, val flow: Double,
    ) {
        val squeeze = squash.coerceIn(-1.0, 1.0) * .06
        val size = max(.001, scale) * (1 - .03 * press)
        val x = cx
        val y = cy + 52 * squeeze * size
        val sx = size * (1 + squeeze)
        val sy = size * (1 - squeeze)
    }
    data class Orb(val x: Double, val y: Double, val r: Double, val glow: Double, val mark: Double, val beat: Double)
    data class Row(val matrix: FloatArray, val opacity: Double, val glow: Double, val face: Double, val landing: Double)

    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun smooth(x: Double): Double {
        val t = clamp(x)
        return t * t * (3 - 2 * t)
    }
    fun bezier(t: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val x = clamp(t)
        var lo = 0.0
        var hi = 1.0
        var u = x
        repeat(14) {
            val q = 1 - u
            val at = 3 * q * q * u * x1 + 3 * q * u * u * x2 + u * u * u
            if (at < x) lo = u else hi = u
            u = (lo + hi) / 2
        }
        return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u
    }
    fun folder(s: BurstScene, l: BurstLayout): Folder {
        return Folder(l.width / 2, l.restY + (l.dockY - l.restY) * s.dock + s.springs[BurstScene.KICK] +
            (if (s.reduced) 0.0 else 2 * sin(s.idleTime * PI * 2 / 5) * (1 - s.dock) * s.idleGain),
            1.5 - .5 * s.dock, s.springs[BurstScene.LEAN], s.springs[BurstScene.FOLDER_PRESS],
            s.springs[BurstScene.RECOIL],
            if (!s.expanded && !s.home && !(s.sim.completed && s.sim.target == 0)) max(.5, s.sim.flow) else s.sim.flow)
    }
    fun orb(s: BurstScene, l: BurstLayout): Orb {
        val f = folder(s, l)
        val p = clamp(s.springs[BurstScene.DISC])
        val rise = s.springs[BurstScene.RISE]
        val storedY = f.cy + (2 - rise) * f.scale
        val rail = smooth(p / .65)
        val bow = if (p < .65) sin(PI * p / .65) else 0.0
        return Orb(f.cx + (44 - f.cx) * rail - 36 * bow,
            storedY + (l.headerTop + 20 - storedY) * p - 60 * sin(PI * p),
            24 * f.scale + (20 - 24 * f.scale) * p,
            if (s.reduced) .55 else if (p > 0 && p < .98) 1.0 else if (p >= .98) .7
            else .56 + .06 * sin(s.idleTime * PI * 2 / 5) * s.idleGain,
            max(smooth((p - .05) / .30), smooth(rise / 16)),
            if (s.reduced) 0.0 else if (s.selected >= 0) 1.0 else .3)
    }
    fun centerX(p: Double, m: Double, i: Int, l: BurstLayout, cx: Double, scale: Double): Double {
        val t = clamp(p)
        val split = .45 - m * .05
        val jet = min(1.0, t / split)
        val lean = 80 * min(1.0, l.width / 402)
        val jitter = (((i * 7 + 3) % 11) / 10.0 - .5) * 52
        val x = cx + lean * smooth(jet) + jitter * scale
        val peel = smooth((t - split) / (.82 - split))
        return x + (l.slots[i].x + l.slots[i].w / 2 - x) * peel
    }
    fun centerY(p: Double, m: Double, i: Int, l: BurstLayout, cy: Double, scale: Double): Double {
        val t = clamp(p)
        val split = .45 - m * .05
        val start = cy + 10 * scale
        val jetTop = cy - 22 * scale - 200
        val end = l.slots[i].y + 26
        if (t <= split) return start + (jetTop - start) * (t / split)
        val clearance = (if (i == 0) 12.0 else 24.0 + i * 2) * (1 - m)
        val peak = end - clearance
        if (t < .85) return jetTop + (peak - jetTop) * smooth((t - split) / (.85 - split))
        return peak + clearance * smooth((t - .85) / .15) + max(0.0, p - 1) * 90
    }
    private fun matrix(cx: Double, cy: Double, w: Double, rx: Double, ry: Double, rz: Double, sx: Double, sy: Double): FloatArray {
        val ax = cos(rx)
        val bx = sin(rx)
        val ay = cos(ry)
        val by = sin(ry)
        val az = cos(rz)
        val bz = sin(rz)
        val r00 = az * ay * sx
        val r01 = (az * by * bx - bz * ax) * sy
        val r10 = bz * ay * sx
        val r11 = (bz * by * bx + az * ax) * sy
        val q0 = by * sx / 900
        val q1 = -ay * bx * sy / 900
        val a = r00 + cx * q0
        val b = r01 + cx * q1
        val c = r10 + cy * q0
        val d = r11 + cy * q1
        return doubleArrayOf(a, b, cx - a * w / 2 - b * 26, c, d, cy - c * w / 2 - d * 26,
            q0, q1, 1 - q0 * w / 2 - q1 * 26).map { it.toFloat() }.toFloatArray()
    }
    fun row(r: BurstSimulation.Row, i: Int, l: BurstLayout, f: Folder, reduced: Boolean, press: Double): Row {
        val slot = l.slots[i]
        if (reduced) return Row(matrix(slot.x + slot.w / 2, slot.y + 26, slot.w, 0.0, 0.0, 0.0, press, press), r.fade, .12, 1.0, 0.0)
        val t = clamp(r.p)
        val turn = when (i) { 2 -> 62; 6 -> 78; 4 -> -85; else -> -55 - (i % 4) * 7 }
        val unwind = if (t < .7) 1 - .64 * smooth((t - .32) / .38) else .36 * (1 - smooth((t - .7) / .10))
        val flash = when {
            t < .20 -> 78 * (1 - smooth(t / .20))
            t < .38 -> 0.0
            t < .58 -> -22 * smooth((t - .38) / .20)
            else -> -22 * (1 - smooth((t - .58) / .22))
        }
        val fold = 1 - smooth(t / .20)
        val rx = (flash + (78 * fold - flash) * r.m) * PI / 180
        val ry = (if (i % 2 == 1) -35 else 35) * (1 - smooth((t - .35) / .45)) * PI / 180
        val rz = turn * unwind * PI / 180
        val scale = if (t < .60) .42 + .22 * smooth(t / .45) else .64 + .36 * smooth((t - .60) / .40)
        val stretch = r.m * min(1.0, abs(r.v) / 8) * sin(PI * t)
        return Row(matrix(centerX(r.p, r.m, i, l, f.cx, f.scale), centerY(r.p, r.m, i, l, f.cy, f.scale), slot.w,
            rx, ry, rz, scale * press * (1 + .06 * stretch), scale * press * (1 - .08 * stretch)),
            smooth((r.p - .015) / .055), .12 + .88 * (1 - smooth((t - .55) / .45)),
            smooth((abs(cos(rx) * cos(ry)) - .12) / .65), r.flash)
    }
}
