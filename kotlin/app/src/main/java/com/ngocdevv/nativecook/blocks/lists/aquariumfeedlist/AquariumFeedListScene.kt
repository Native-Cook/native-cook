package com.ngocdevv.nativecook.blocks.lists.aquariumfeedlist

import android.graphics.Path
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Aquarium Feed List — the loop and the fish, as pure functions of time (the React Native
 aquariumScene.ts).

 Six fish cruise on closed Lissajous paths whose frequencies are whole multiples of the loop, so
 every frame at t = LOOP equals the frame at 0. Two values stir them:
 - `stir` (0–1): how hard the water is being churned by scrolling. Fish scatter outward and away
   from the scroll direction, tails beat wider, bubbles stream up.
 - `feed` (0–1): a card was tapped; fish leave their paths and circle it.
 Under autoPlay both come from the script; under touch from the scroll's speed and the last tap.
*/

internal const val LOOP = 9.0
/** Card rows in the list: a 104 dp card and a 12 dp gap. */
internal const val AQ_CARD_H = 104f
internal const val AQ_CARD_GAP = 12f
internal const val AQ_ROW = AQ_CARD_H + AQ_CARD_GAP
/** The scripted scroll: down to AQ_SCROLL_TO over 1.4–4.4 s, back to 0 over 6.6–8.2 s. */
private const val AQ_SCROLL_TO = 420.0
/** The card the loop taps at 5.8 s. */
internal const val AQ_TAP_CARD = 4
internal const val AQ_TAP_AT = 5.8

internal class AqFish(
    /** Body length and depth (dp). */
    val len: Float,
    val dep: Float,
    val colors: IntArray,
    /** White bands (as fractions of the body from tail to nose), drawn over the body. */
    val bands: FloatArray,
    /** Path centre and reach as fractions of the swimming area; whole-number frequencies. */
    val cx: Double,
    val cy: Double,
    val ax: Double,
    val ay: Double,
    val kx: Int,
    val ky: Int,
    val phase: Double,
    /** Tail beats per loop (whole). */
    val beats: Int,
    /** Depth: far fish are smaller and hazier. */
    val near: Float,
)

private fun c3(a: Long, b: Long, c: Long) = intArrayOf(a.toInt(), b.toInt(), c.toInt())

internal val AqFishes = arrayOf(
    AqFish(75f, 44f, c3(0xFFE39A1F, 0xFFF6C33F, 0xFFFCE59A), floatArrayOf(), 0.5, 0.3, 0.36, 0.12, 1, 2, 0.2, 24, 1f),
    AqFish(49f, 22f, c3(0xFF1B4FB0, 0xFF2F86E4, 0xFF9ED4FF), floatArrayOf(), 0.42, 0.62, 0.32, 0.16, 1, 3, 2.1, 33, 0.8f),
    AqFish(58f, 29f, c3(0xFFD24A15, 0xFFFF7A33, 0xFFFFC08F), floatArrayOf(0.32f, 0.66f), 0.6, 0.48, 0.3, 0.14, -1, 2, 4.0, 30, 0.92f),
    AqFish(70f, 25f, c3(0xFF5A36B8, 0xFF9A63E6, 0xFFE6C6FF), floatArrayOf(), 0.46, 0.82, 0.38, 0.08, 1, 2, 5.2, 27, 0.95f),
    AqFish(38f, 16f, c3(0xFF5E9A2E, 0xFF9CCF55, 0xFFE3F5B4), floatArrayOf(), 0.3, 0.18, 0.22, 0.08, -2, 3, 1.1, 36, 0.66f),
    AqFish(44f, 16f, c3(0xFF6F8897, 0xFFB9CCD7, 0xFFF1F7FA), floatArrayOf(), 0.68, 0.72, 0.26, 0.12, 2, -3, 3.3, 36, 0.72f),
)

internal class AqEntry(val title: String, val detail: String, val time: String, val tag: String, val icon: ImageVector, val tint: Long)

internal val AqEntries = listOf(
    AqEntry("Fed", "2 cubes mysis, 1 pinch of pellets", "08:12", "Feed", Icons.Filled.SetMeal, 0xFFF6C33F),
    AqEntry("Water test", "pH 8.2 · dKH 8.4 · NO₃ 5 ppm", "07:40", "Test", Icons.Filled.Science, 0xFF7FD6E8),
    AqEntry("Frag added", "Green star polyp, left rock", "Yesterday", "Coral", Icons.Filled.Eco, 0xFF9CCF55),
    AqEntry("Water change", "15 % · 33 L fresh saltwater", "Yesterday", "Care", Icons.Filled.WaterDrop, 0xFF5FB2F0),
    AqEntry("Glass scraped", "Front pane, 4 minutes", "Tue", "Care", Icons.Filled.CleaningServices, 0xFFC9A7F5),
    AqEntry("Light schedule", "Sunrise 09:00 · 70 % blue", "Tue", "Lights", Icons.Filled.WbTwilight, 0xFFFFB36B),
    AqEntry("Fed", "Nori clip for the tang", "Mon", "Feed", Icons.Filled.SetMeal, 0xFFF6C33F),
    AqEntry("Temperature dip", "25.1 °C overnight, heater checked", "Mon", "Alert", Icons.Filled.Thermostat, 0xFFFF8A73),
    AqEntry("Skimmer cleaned", "Collection cup, two days early", "Sun", "Care", Icons.Filled.Air, 0xFF9FD0DA),
    AqEntry("Snail count", "11 nerites, all accounted for", "Sat", "Census", Icons.Filled.Checklist, 0xFFE8D29A),
    AqEntry("Top-off", "1.2 L RO water", "Sat", "Care", Icons.Filled.WaterDrop, 0xFF5FB2F0),
    AqEntry("New arrival", "Lavender wrasse, quarantine 3 weeks", "Fri", "Fish", Icons.Filled.SetMeal, 0xFFB07CF0),
)

internal fun aqClamp01(v: Double) = if (v < 0) 0.0 else if (v > 1) 1.0 else v

internal fun aqEaseInOutCubic(v: Double): Double {
    val x = aqClamp01(v)
    return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
}

internal fun aqEaseOutCubic(v: Double) = 1 - (1 - aqClamp01(v)).pow(3)

/** The list's scroll offset in the loop (dp). */
internal fun aqAutoScroll(t: Double) =
    AQ_SCROLL_TO * (aqEaseInOutCubic((t - 1.4) / 3.0) - aqEaseInOutCubic((t - 6.6) / 1.6))

/** The loop's stir: a long churn for the first scroll, a shorter one for the way back. */
internal fun aqAutoStirA(t: Double) = aqEaseOutCubic((t - 1.5) / 0.7) * (1 - aqEaseInOutCubic((t - 4.0) / 1.5))

internal fun aqAutoStirB(t: Double) = 0.75 * aqEaseOutCubic((t - 6.7) / 0.6) * (1 - aqEaseInOutCubic((t - 7.8) / 0.9))

internal fun aqAutoStir(t: Double) = max(aqAutoStirA(t), aqAutoStirB(t))

/** The stir's direction: +1 = content moving up. */
internal fun aqAutoDir(t: Double) = if (aqAutoStirA(t) >= aqAutoStirB(t)) 1.0 else -1.0

/** The loop's feeding: fish gather round the tapped card from 5.8 s and leave as the list scrolls back. */
internal fun aqAutoFeed(t: Double) = aqEaseInOutCubic((t - AQ_TAP_AT) / 0.8) * (1 - aqEaseInOutCubic((t - 6.7) / 0.9))

internal class AqArea(val x: Double, val y: Double, val w: Double, val h: Double)

/** Fish i's centre at fish-time [tau], stirred by [stir] in direction [dir], drawn to (tx, ty) by [feed]. Writes [x, y] into out. */
internal fun aqFishAt(i: Int, tau: Double, area: AqArea, stir: Double, dir: Double, feed: Double, tx: Double, ty: Double, out: DoubleArray) {
    val f = AqFishes[i]
    val w = 2 * PI * tau / LOOP
    var x = area.x + area.w * (f.cx + f.ax * sin(f.kx * w + f.phase))
    var y = area.y + area.h * (f.cy + f.ay * sin(f.ky * w + f.phase * 1.7))
    // Scatter: away from the middle of the tank and against the scroll.
    val mx = x - (area.x + area.w / 2)
    val my = y - (area.y + area.h / 2)
    val d = sqrt(mx * mx + my * my) + 30
    x += stir * (mx / d) * 70
    y += stir * ((my / d) * 50 - dir * 60)
    // Feeding: each fish takes its own place on a slow ring round the card (two laps a loop).
    val ang = f.phase * 2.3 + 2 * w * (if (i % 2 == 0) 1 else -1)
    val gx = tx + cos(ang) * (110 + 18 * (i % 3))
    val gy = ty + sin(ang) * (46 + 8 * (i % 2))
    out[0] = x + (gx - x) * feed
    out[1] = y + (gy - y) * feed
}

private fun halfWidth(u: Float, h: Float): Float =
    if (u < 0.6f) {
        h / 2 * (0.16f + 0.84f * sin((PI / 2).toFloat() * (u / 0.6f)))
    } else {
        val q = (u - 0.6f) / 0.4f
        h / 2 * sqrt(max(0f, 1 - q * q))
    }

private const val SEGS = 14

/** Scratch for one body outline. */
internal class AqBodyScratch {
    val xs = FloatArray(SEGS + 1)
    val top = FloatArray(SEGS + 1)
    val bot = FloatArray(SEGS + 1)
}

/**
 * A fish's body in its own frame (nose toward +x, centred), its tail and fins swinging with
 * [phase]; [amp] is the swing as a share of the body's depth. Written into [b] (rewound first).
 */
internal fun aqFishBody(f: AqFish, phase: Float, amp: Float, b: Path, s: AqBodyScratch) {
    b.rewind()
    val len = f.len
    val h = f.dep
    val n = SEGS
    for (k in 0..n) {
        val u = k / n.toFloat()
        val x = -len / 2 + u * len
        val bend = amp * h * sin(phase - u * 4.5f) * (1 - u) * (1 - u)
        val w = halfWidth(u, h)
        s.xs[k] = x
        s.top[k] = bend - w
        s.bot[k] = bend + w
    }
    b.moveTo(s.xs[0], s.top[0])
    for (k in 1..n) b.lineTo(s.xs[k], s.top[k])
    for (k in n downTo 0) b.lineTo(s.xs[k], s.bot[k])
    b.close()
    // Tail: a forked fan that trails the body's swing.
    val tx = s.xs[0]
    val ty = (s.top[0] + s.bot[0]) / 2
    val swing = amp * h * 1.1f * sin(phase + 0.9f)
    b.moveTo(tx + 2, ty - h * 0.08f)
    b.quadTo(tx - len * 0.18f, ty + swing * 0.6f - h * 0.2f, tx - len * 0.3f, ty + swing - h * 0.42f)
    b.quadTo(tx - len * 0.2f, ty + swing * 0.85f, tx - len * 0.3f, ty + swing + h * 0.42f)
    b.quadTo(tx - len * 0.18f, ty + swing * 0.6f + h * 0.2f, tx + 2, ty + h * 0.08f)
    b.close()
    // Dorsal fin: a soft sail over the back.
    val a = 5
    val c = 10
    b.moveTo(s.xs[a], s.top[a] + 1)
    b.quadTo(s.xs[a + 1], s.top[a + 1] - h * 0.42f, s.xs[c], s.top[c] + 1)
    b.close()
}

/** The white bands of a banded fish, following the same swing. */
internal fun aqFishBands(f: AqFish, phase: Float, amp: Float, b: Path) {
    b.rewind()
    val len = f.len
    val h = f.dep
    for (u0 in f.bands) {
        for (k in 0..3) {
            val u = u0 + k / 3f * 0.09f
            val bend = amp * h * sin(phase - u * 4.5f) * (1 - u) * (1 - u)
            val w = halfWidth(u, h)
            val x = -len / 2 + u * len
            if (k == 0) b.moveTo(x, bend - w * 0.98f) else b.lineTo(x, bend - w * 0.98f)
        }
        for (k in 3 downTo 0) {
            val u = u0 + k / 3f * 0.09f
            val bend = amp * h * sin(phase - u * 4.5f) * (1 - u) * (1 - u)
            val w = halfWidth(u, h)
            b.lineTo(-len / 2 + u * len, bend + w * 0.98f)
        }
        b.close()
    }
}

/** Seeded 0–1. */
private fun hash(n: Double): Double {
    val v = sin(n * 127.1 + 311.7) * 43758.5453
    return v - floor(v)
}

/** Bubbles: rings and their glints, a steady thin stream from the airstone plus many more while stirred. */
internal fun aqBubbles(t: Double, stir: Double, w: Float, top: Float, bottom: Float, rings: Path, glints: Path) {
    rings.rewind()
    glints.rewind()
    val n = 54
    val span = bottom - top
    for (j in 0 until n) {
        // Periods 2.25, 3 or 4.5 s: whole divisions of the loop.
        val period = if (j % 3 == 0) 2.25 else if (j % 3 == 1) 3.0 else 4.5
        val life = (t / period + hash(j * 1.7)) % 1
        val air = j < 10
        val on = if (air) 0.85 else aqClamp01(stir * 1.6 - hash(j * 3.1) * 0.6)
        if (on <= 0.02) continue
        val r = (if (air) 2 + hash(j.toDouble()) * 2.2 else 2 + hash(j * 5.3) * 4) * on * min(1.0, min(life * 6, (1 - life) * 8))
        if (r < 0.4) continue
        val x0 = if (air) w - 34 + hash(j * 2.9) * 10 else 24 + hash(j * 4.4) * (w - 48)
        val x = x0 + sin(2 * PI * (life * 3 + hash(j * 6.1))) * (3 + r)
        val y = bottom - life * span * (if (air) 1.0 else 0.85)
        rings.addCircle(x.toFloat(), y.toFloat(), r.toFloat(), Path.Direction.CW)
        glints.addCircle((x - r * 0.35).toFloat(), (y - r * 0.35).toFloat(), (r * 0.28).toFloat(), Path.Direction.CW)
    }
}
