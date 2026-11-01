package com.ngocdevv.nativecook.blocks.cards.questmapunroll

import kotlin.math.*

internal object QuestMapUnrollMotion {
    const val LOOP = 9.0
    val names = listOf("Intro to spells", "Stone bridge", "Pumpkin patch", "Crystal cave", "Giant mushroom", "Wizard tower")
    val stars = listOf(3, 3, 2, 0, 0, 0)
    data class Frame(val u: Double = 0.0, val wisp: Double = 2.0, val t: Double = 0.0, val card: Double = 0.0, val level: Int = 3)
    fun clamp(v: Double) = v.coerceIn(0.0, 1.0)
    fun smoother(k: Double): Double {
        val x = clamp(k)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    fun backOut(k: Double): Double {
        val x = clamp(k) - 1
        return 1 + 3.2 * x * x * x + 2.2 * x * x
    }
    fun at(time: Double): Frame {
        val t = time % LOOP
        val u = when {
            t >= 0.6 && t < 2.4 -> smoother((t - 0.6) / 1.8)
            t >= 2.4 && t < 6.6 -> 1.0
            t >= 6.6 && t < 8.2 -> 1 - smoother((t - 6.6) / 1.6)
            else -> 0.0
        }
        val wisp = if (t >= 3 && t < 6.6) 2 + smoother((t - 3) / 1.2) else 2.0
        val card = if (t >= 4.3 && t < 6) smoother((t - 4.3) / 0.4) * (1 - smoother((t - 5.6) / 0.4)) else 0.0
        return Frame(u, wisp, time, card)
    }
}

internal class QuestMapUnrollGeometry(val width: Double, val height: Double, val top: Double, val bottom: Double) {
    val mw = width - 40
    val mh = floor(mw * 1300 / 900 + 0.5)
    val scale = min(1.0, (height - top - 96 - bottom - 120) / mh)
    val w = floor(mw * scale + 0.5)
    val h = floor(mh * scale + 0.5)
    val x = floor((width - w) / 2 + 0.5)
    val y = top + 96
    val nodes = listOf(316.0 to 1160.0, 599.0 to 945.0, 319.0 to 750.0, 606.0 to 595.0, 318.0 to 412.0, 555.0 to 261.0)
        .map { (nx, ny) -> x + nx / 900 * w to y + ny / 1300 * h }
    fun along(f: Double): Pair<Double, Double> {
        val i = floor(f).toInt().coerceIn(0, 4)
        val k = QuestMapUnrollMotion.clamp(f - i)
        val a = nodes[i]; val b = nodes[i + 1]
        val bend = sin(k * PI) * 18 * if (i % 2 == 1) 1 else -1
        return a.first + (b.first - a.first) * k + bend to a.second + (b.second - a.second) * k
    }
    fun contains(xp: Double, yp: Double) = xp >= x && xp <= x + w && yp >= y - 20 && yp <= y + h + 20
}
