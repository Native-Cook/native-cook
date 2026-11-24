package com.ngocdevv.nativecook.blocks.lists.stickysectionheaders

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

/*
 * Sticky Section Headers — everything is a function of one number, the list's scroll offset s (content dp), ported one
 * to one from stickyMotion.ts. The autoplay loop only scripts s (scrollAt); the sticky header, its pill, the rolling
 * total and the month index all read s, so a real scroll drives exactly what the loop shows.
 */

/** Fixed row geometry (dp): every section's top in the content is known without measuring. */
internal const val HEADER_H = 56f
internal const val ROW_H = 68f
internal const val FOOTER_H = 420f

/** Loop length (s). */
const val LOOP = 10f

/** Script: rest, glide 1, hold, glide 2, hold, eased return, rest. */
internal val GLIDE1 = floatArrayOf(1.2f, 3.6f)
internal val GLIDE2 = floatArrayOf(4.4f, 6.4f)
internal val BACK = floatArrayOf(7.0f, 8.8f)

/** Stuck header → pill. */
internal const val SHRINK = 52f
internal const val PILL_TOP = 8f
internal const val PILL_H = 36f
internal const val PILL_W = 236f

/** A row adds to the running total while its middle passes this far under the pill. */
internal const val ROLL_LINE = PILL_TOP + PILL_H
internal const val ROLL_SPAN = 30f

/** Touch: idle before returning to the top, and how long the return takes. */
internal const val IDLE = 2f
internal const val RETURN_LEN = 1.2f

/** Everything the draw and the clock need about the data, as plain arrays (built once per data set). */
internal class Geometry(
    /** Content y of each section header, plus the end of the last section. */
    val tops: FloatArray,
    /** Per section: running "spent" after 0, 1, 2, … rows (cents), flattened; runOffsets index into it. */
    val running: IntArray,
    val runOffsets: IntArray,
    val rowCounts: IntArray,
    val sectionMonth: IntArray,
    /** Content y where each month's first section starts. */
    val monthTops: FloatArray,
    val stop1: Float,
    val stop2: Float,
    val contentH: Float,
)

internal fun spentOf(cents: Int) = if (cents < 0) -cents else 0

/**
 * [sectionTops] are the section header tops as laid out (dp, from the list's whole-pixel item heights, so the sticky
 * copy lines up with the in-list header to the pixel); [endTop] is the end of the last section.
 */
internal fun buildGeometry(sections: List<LedgerSection>, months: Int, sectionTops: FloatArray, endTop: Float, footerH: Float): Geometry {
    val tops = FloatArray(sections.size + 1)
    val running = ArrayList<Int>()
    val runOffsets = IntArray(sections.size)
    sections.forEachIndexed { i, s ->
        tops[i] = sectionTops[i]
        runOffsets[i] = running.size
        var acc = 0
        running.add(0)
        for (t in s.txns) {
            acc += spentOf(t.cents)
            running.add(acc)
        }
    }
    tops[sections.size] = endTop
    val monthTops = FloatArray(months) { m ->
        val i = sections.indexOfFirst { it.month == m }
        if (i < 0) endTop else tops[i]
    }
    fun at(i: Int) = tops[min(i, sections.size - 1)] + HEADER_H + ROW_H * 1.6f
    return Geometry(
        tops = tops,
        running = running.toIntArray(),
        runOffsets = runOffsets,
        rowCounts = IntArray(sections.size) { sections[it].txns.size },
        sectionMonth = IntArray(sections.size) { sections[it].month },
        monthTops = monthTops,
        stop1 = at(1),
        stop2 = at(4),
        contentH = endTop + footerH,
    )
}

internal fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

internal fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))

internal fun easeInOutCubic(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

internal fun easeOutCubic(x: Float): Float = 1f - (1f - x).pow(3)

internal fun mix(a: Float, b: Float, u: Float) = a + (b - a) * u

/** The scripted scroll offset at loop time t. */
internal fun scrollAt(t: Float, g: Geometry): Float {
    val g1 = easeInOutCubic(seg(t, GLIDE1[0], GLIDE1[1]))
    val g2 = easeInOutCubic(seg(t, GLIDE2[0], GLIDE2[1]))
    val back = easeInOutCubic(seg(t, BACK[0], BACK[1]))
    val y = g.stop1 * g1 + (g.stop2 - g.stop1) * g2
    return y * (1f - back)
}

private val GLIDES = floatArrayOf(GLIDE1[0], GLIDE1[1], GLIDE2[0], GLIDE2[1], BACK[0], BACK[1])

/** Ghost finger for the scripted scroll: opacity. */
internal fun ghostOpacity(t: Float): Float {
    var i = 0
    while (i < 6) {
        val a = GLIDES[i]
        val b = GLIDES[i + 1]
        if (t > a - 0.3f && t < b + 0.3f) {
            return easeOutCubic(seg(t, a - 0.3f, a)) * (1f - easeOutCubic(seg(t, b - 0.15f, b + 0.3f)))
        }
        i += 2
    }
    return 0f
}

/** Ghost finger for the scripted scroll: y as a fraction of the list height. */
internal fun ghostY(t: Float): Float {
    var i = 0
    while (i < 6) {
        val a = GLIDES[i]
        val b = GLIDES[i + 1]
        if (t > a - 0.3f && t < b + 0.3f) {
            val u = easeInOutCubic(seg(t, a, b))
            return if (i < 4) mix(0.78f, 0.3f, u) else mix(0.3f, 0.78f, u)
        }
        i += 2
    }
    return 0.78f
}

/** Index of the section whose header has reached the top (−1 above the first). */
internal fun sectionAt(s: Float, g: Geometry): Int {
    var k = -1
    for (i in 0 until g.tops.size - 1) {
        if (g.tops[i] <= s) k = i
    }
    return k
}

/** 0 = full header, 1 = pill. */
internal fun pillAmount(s: Float, k: Int, g: Geometry): Float {
    if (k < 0) return 0f
    return easeInOutCubic(clamp01((s - g.tops[k]) / SHRINK))
}

/** Vertical offset of the stuck header: 0 while stuck, negative while the next header pushes it up. */
internal fun pushOffset(s: Float, k: Int, p: Float, g: Geometry): Float {
    if (k < 0) return 0f
    val occupied = mix(HEADER_H, PILL_TOP + PILL_H + 8f, p)
    return min(0f, g.tops[k + 1] - s - occupied)
}

/**
 * The running total of section k at offset s: from cents, to cents, u (written into [out]). Each row adds its amount
 * while its middle slides under the pill, eased; only one row is ever mid-way (ROLL_SPAN < ROW_H).
 */
internal fun runningAt(s: Float, k: Int, g: Geometry, out: FloatArray) {
    if (k < 0) {
        out[0] = 0f; out[1] = 0f; out[2] = 0f
        return
    }
    val base = g.runOffsets[k]
    val n = g.rowCounts[k]
    val line = s + ROLL_LINE
    var done = 0
    for (j in 0 until n) {
        val mid = g.tops[k] + HEADER_H + j * ROW_H + ROW_H * 0.5f
        val u = clamp01((line - mid) / ROLL_SPAN)
        if (u >= 1f) {
            done = j + 1
        } else if (u > 0f) {
            out[0] = g.running[base + j].toFloat()
            out[1] = g.running[base + j + 1].toFloat()
            out[2] = easeInOutCubic(u)
            return
        }
    }
    out[0] = g.running[base + done].toFloat()
    out[1] = out[0]
    out[2] = 0f
}

/**
 * Position of one odometer wheel (0..19, two strips of 0–9) for a place value in cents.
 * Rolls forward through the digits between, like a counter, and back when scrolling back.
 */
internal fun wheelAt(from: Float, to: Float, u: Float, place: Int): Float {
    val d0 = floor(from / place) % 10f
    var d1 = floor(to / place) % 10f
    if (to > from && d1 < d0) d1 += 10f
    return mix(d0, d1, u)
}

/** Month index position (fractional between two months while their boundary passes the top). */
internal fun monthAt(s: Float, g: Geometry): Float {
    val k = sectionAt(s, g)
    if (k < 0) return 0f
    val m = g.sectionMonth[k]
    if (m + 1 >= g.monthTops.size) return m.toFloat()
    val next = g.monthTops[m + 1]
    return m + easeInOutCubic(clamp01((s - (next - HEADER_H * 2f)) / (HEADER_H * 2f)))
}
