package com.ngocdevv.nativecook.blocks.charts.isometricscoregauge

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeTheme.Gauge
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeTheme.UI
import com.ngocdevv.nativecook.blocks.charts.isometricscoregauge.IsometricScoreGaugeTheme.columns

/** Double precision mirrors the RN worklet timeline and its semi-implicit touch springs. */
internal object IsometricScoreGaugeMotion {
    const val LOOP = 9.0
    const val RESUME = 2.0
    object T {
        val scroll = doubleArrayOf(1.0, 1.9)
        const val pick1 = 2.1
        val roll1 = doubleArrayOf(2.2, 3.0)
        const val drop1 = 3.9
        const val pick2 = 4.1
        val roll2 = doubleArrayOf(4.15, 5.0)
        const val drop2 = 5.9
        val rollBack = doubleArrayOf(6.0, 6.8)
        val scrollBack = doubleArrayOf(7.0, 8.0)
    }
    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p
    fun easeOutCubic(x: Double): Double { val u = 1 - clamp01(x); return 1 - u * u * u }
    fun easeInOut(x: Double): Double { val p = clamp01(x); return if (p < 0.5) 16 * p * p * p * p * p else 1 - (-2 * p + 2).pow(5) / 2 }
    fun smooth(x: Double): Double { val p = clamp01(x); return p * p * (3 - 2 * p) }
    data class Frame(val scroll: Double = 0.0, val sel: Int = -1, val selW: Double = 0.0, val score: Double = Gauge.score, val base: Double = Gauge.score)
    data class Pose(val x: Double, val ang: Double, val k: Double)
    fun frameAt(t: Double): Frame {
        val scroll = if (t < T.scrollBack[0]) UI.pitch * easeInOut(seg(t, T.scroll[0], T.scroll[1])) else UI.pitch * (1 - easeInOut(seg(t, T.scrollBack[0], T.scrollBack[1])))
        var sel = -1
        var weight = 0.0
        if (t >= T.pick1 && t < T.drop1 + 0.25) {
            sel = 0
            weight = if (t < T.drop1) easeOutCubic(seg(t, T.pick1, T.pick1 + 0.3)) else 1 - smooth(seg(t, T.drop1, T.drop1 + 0.25))
        } else if (t >= T.pick2 && t < T.drop2 + 0.25) {
            sel = 3
            weight = if (t < T.drop2) easeOutCubic(seg(t, T.pick2, T.pick2 + 0.3)) else 1 - smooth(seg(t, T.drop2, T.drop2 + 0.25))
        }
        val a = Gauge.score + columns[0][0].delta
        val b = Gauge.score + columns[1][1].delta
        val score = when {
            t >= T.roll1[0] && t < T.roll2[0] -> mix(Gauge.score, a, easeOutCubic(seg(t, T.roll1[0], T.roll1[1])))
            t >= T.roll2[0] && t < T.rollBack[0] -> mix(a, b, easeOutCubic(seg(t, T.roll2[0], T.roll2[1])))
            t >= T.rollBack[0] -> mix(b, Gauge.score, easeOutCubic(seg(t, T.rollBack[0], T.rollBack[1])))
            else -> Gauge.score
        }
        return Frame(scroll, sel, weight, score)
    }
    fun columnPose(index: Int, scroll: Double): Pose {
        val x = UI.x0 + index * UI.pitch + scroll
        val off = x - 227.5
        val edge = clamp01((abs(off) - 95) / 110)
        return Pose(x, (if (off < 0) 1 else -1) * 62 * smooth(edge), 1 - 0.12 * edge)
    }
    fun cardAt(x: Double, y: Double, scroll: Double): Int {
        for (index in columns.indices) {
            val pose = columnPose(index, scroll)
            if (abs(pose.ang) > 30 || abs(x - pose.x) > UI.cardW / 2) continue
            for (row in 0..1) if (y > UI.rows[row] && y < UI.rows[row] + UI.cardH) return index * 2 + row
        }
        return -1
    }
}
