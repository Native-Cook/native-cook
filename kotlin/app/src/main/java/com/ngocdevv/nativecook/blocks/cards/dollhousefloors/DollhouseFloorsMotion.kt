package com.ngocdevv.nativecook.blocks.cards.dollhousefloors

import kotlin.math.*

internal object DollhouseFloorsMotion {
    const val LOOP = 9.0
    fun smoother(k: Double): Double {
        val x = k.coerceIn(0.0, 1.0)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    data class Frame(val e: Double = 0.0, val sel: Int = -1, val selK: List<Double> = listOf(0.0, 0.0, 0.0))
    fun script(time: Double): Frame {
        val t = time % LOOP
        val e = when {
            t >= 1.0 && t < 2.2 -> smoother((t - 1.0) / 1.2)
            t >= 2.2 && t < 6.3 -> 1.0
            t >= 6.3 && t < 7.5 -> 1 - smoother((t - 6.3) / 1.2)
            else -> 0.0
        }
        return Frame(e, if (t >= 2.7 && t < 4.1) 0 else if (t >= 4.1 && t < 5.5) 1 else -1)
    }
    fun lifts(e: Double, reduced: Boolean): Pair<Double, Double> {
        val a = if (reduced) 0.0 else 1.3 * smoother((e - 0.15) / 0.85)
        return a to if (reduced) 0.0 else 1.3 * smoother(e / 0.8) + a
    }
}

internal class DollhouseFloorsDriver {
    var e = 0.0
    var v = 0.0
    var target = 0.0
    var e0 = 0.0
    var drag = false
    var scripted = false
    var sel = -1
    var selK = DoubleArray(3)
    var lastTouch = -100.0
    var loopT = 0.0
    var now = 0.0
    fun step(wall: Double, auto: Boolean): DollhouseFloorsMotion.Frame {
        val dt = min(wall, 0.05)
        now += wall
        if (auto && !drag && now - lastTouch > 3.5 && (scripted || (e < 0.001 && sel == -1))) {
            if (!scripted) { scripted = true; loopT = 0.0 }
            loopT = (loopT + wall) % DollhouseFloorsMotion.LOOP
            val f = DollhouseFloorsMotion.script(loopT)
            e = f.e; sel = f.sel; target = if (e > 0.5) 1.0 else 0.0
        } else {
            scripted = false
            if (!drag) {
                val k = 140.0
                val c = 2 * 0.72 * sqrt(k)
                v += (k * (target - e) - c * v) * dt
                e += v * dt
            }
        }
        for (i in 0..2) {
            val want = if (sel >= 0 && sel != i) 1.0 else 0.0
            selK[i] += (want - selK[i]) * (1 - exp(-10 * dt))
        }
        return DollhouseFloorsMotion.Frame(e, sel, selK.toList())
    }
    fun pick(i: Int) {
        scripted = false; lastTouch = now
        if (i < 0) { target = if (target > 0.5) 0.0 else 1.0; sel = -1 }
        else { target = 1.0; sel = if (sel == i) -1 else i }
    }
    fun panStart() { drag = true; scripted = false; e0 = e; v = 0.0 }
    fun pan(y: Double) {
        val raw = e0 - y / 220
        e = if (raw < 0) raw * 0.2 else if (raw > 1) 1 + (raw - 1) * 0.25 else raw
        lastTouch = now
    }
    fun panEnd(vy: Double) {
        if (!drag) return
        drag = false
        target = if (vy < -400) 1.0 else if (vy > 400) 0.0 else if (e > 0.5) 1.0 else 0.0
        if (target == 0.0) sel = -1
        v = -vy / 220; lastTouch = now
    }
    fun tap(y: Double, width: Double, height: Double): Boolean {
        lastTouch = now; scripted = false
        if (e < 0.5) { target = 1.0; return false }
        val (l1, l2) = DollhouseFloorsMotion.lifts(e, false)
        val zs = listOf(0.8, 2.65 + l1 + 0.8, 5.3 + l2 + 0.6)
        val s = round(width * 0.074)
        val base = round(height * 0.63)
        val best = zs.indices.minBy { abs(y - (base - 3.5 * s * 0.5 - zs[it] * s)) }
        sel = if (sel == best) -1 else best
        return true
    }
    fun settled() = !drag && abs(e - target) < 0.000001 && abs(v) < 0.000001 && (0..2).all { abs(selK[it] - (if (sel >= 0 && sel != it) 1.0 else 0.0)) < 0.000001 }
}
