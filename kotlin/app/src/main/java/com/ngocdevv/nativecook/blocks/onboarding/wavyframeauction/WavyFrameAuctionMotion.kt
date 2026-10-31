package com.ngocdevv.nativecook.blocks.onboarding.wavyframeauction

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.*

internal object WavyFrameAuctionMotion {
    const val LOOP = 9.0
    fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
    fun smooth(x: Double): Double { val k = x.coerceIn(0.0, 1.0); return k * k * (3 - 2 * k) }
    fun ease(x: Double): Double { val k = x.coerceIn(0.0, 1.0); return if (k < 0.5) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2 }
    fun script(t: Double): Double = when {
        t < 1.2 -> 0.0
        t < 2.2 -> 0.42 * ease(t - 1.2)
        t < 5.8 -> { val x = ((t - 2.2) / 0.7).coerceIn(0.0, 1.0); mix(0.42, 1.0, 1 - (1 - x).pow(3) + sin(x * PI) * 0.06) }
        t < 6.8 -> 1 - ease(t - 5.8)
        else -> 0.0
    }
    fun lift(t: Double): Double {
        var l = 0.0
        for ((a, b) in listOf(1.2 to 2.2, 5.8 to 6.8)) {
            if (t >= a && t < b) l = max(l, 1 - exp(-(t - a) * 10))
            else if (t >= b) l = max(l, (1 - exp(-(b - a) * 10)) * exp(-(t - b) * 10))
        }
        return l
    }
    fun frame(s: Double, w: Double, h: Double, top: Double): RectF {
        val k = smooth(s); val cw = mix(250.0, w - 32, k); val ch = mix(300.0, h * 0.56, k)
        val cy = mix(top + 230, top + 64 + ch / 2, k)
        return RectF((w / 2 - cw / 2).toFloat(), (cy - ch / 2).toFloat(), (w / 2 + cw / 2).toFloat(), (cy + ch / 2).toFloat())
    }
    fun wavy(f: RectF, r: Double, a: Double, ph: Double): Path {
        val x = f.left.toDouble(); val y = f.top.toDouble(); val w = f.width().toDouble(); val h = f.height().toDouble()
        val top = w - 2 * r; val side = h - 2 * r; val arc = PI / 2 * r
        val segments = doubleArrayOf(top, arc, side, arc, top, arc, side, arc)
        val per = 2 * (w + h - 4 * r) + 2 * PI * r
        return Path().apply {
            for (i in 0..220) {
                var d = i / 220.0 * per; var j = 0
                while (j < 7 && d >= segments[j]) { d -= segments[j]; j++ }
                var nx = 0.0; var ny = 0.0; var wave = -1.0
                val p: Pair<Double, Double> = when (j) {
                    0 -> { ny = -1.0; wave = d / top; x + r + d to y }
                    2 -> { nx = 1.0; wave = d / side; x + w to y + r + d }
                    4 -> { ny = 1.0; wave = d / top; x + w - r - d to y + h }
                    6 -> { nx = -1.0; wave = d / side; x to y + h - r - d }
                    else -> {
                        val an = when (j) { 1 -> -PI / 2 + d / r; 3 -> d / r; 5 -> PI / 2 + d / r; else -> PI + min(d, arc) / r }
                        nx = cos(an); ny = sin(an)
                        val cx = if (j == 1 || j == 3) x + w - r else x + r
                        val cy = if (j == 1 || j == 7) y + r else y + h - r
                        cx + nx * r to cy + ny * r
                    }
                }
                val o = if (wave >= 0) a * sin(wave * 5 * 2 * PI + ph) * sin(wave * PI) else 0.0
                val px = (p.first + nx * o).toFloat(); val py = (p.second + ny * o).toFloat()
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
    }
    fun tab(w: Double, h: Double, bottom: Double, s: Double, ph: Double): Pair<Path, Double> {
        val k = smooth(s); val tw = mix(150.0, w - 40, k); val th = mix(76.0, 58.0, k)
        val tx = w / 2 - tw / 2; val ty = h - bottom - mix(4.0, 22.0, k) - th
        val a = mix(10.0, 0.0, k); val rr = mix(18.0, th / 2, k)
        val path = Path()
        fun line(x: Double, y: Double) = path.lineTo(x.toFloat(), y.toFloat())
        fun quad(cx: Double, cy: Double, x: Double, y: Double) = path.quadTo(cx.toFloat(), cy.toFloat(), x.toFloat(), y.toFloat())
        path.moveTo(tx.toFloat(), (ty + rr).toFloat()); quad(tx, ty, tx + rr, ty)
        for (i in 0..30) {
            val u = i / 30.0
            line(tx + rr + u * (tw - 2 * rr), ty - a * sin(u * PI) * (0.6 + 0.4 * cos(u * PI * 4 + ph * 3)))
        }
        quad(tx + tw, ty, tx + tw, ty + rr); line(tx + tw, ty + th - rr * k)
        if (k > 0.01) quad(tx + tw, ty + th, tx + tw - rr * k, ty + th) else line(tx + tw, ty + th + 40)
        line(tx + rr * k, ty + th + if (k > 0.01) 0 else 40)
        if (k > 0.01) quad(tx, ty + th, tx, ty + th - rr * k)
        path.close(); return path to ty
    }
}
