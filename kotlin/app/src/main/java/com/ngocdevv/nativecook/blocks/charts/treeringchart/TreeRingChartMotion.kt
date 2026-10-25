package com.ngocdevv.nativecook.blocks.charts.treeringchart

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Tree Ring Chart — data and the autoPlay script, as in treeRingMotion.ts. Every value on screen is
// a closed-form function of the loop time t (seconds), so the frame at t = LOOP equals t = 0.

internal const val LOOP = 9f

/** One ring per year of saving; a ring's width is that year's savings. */
internal val TreeYears = intArrayOf(2019, 2020, 2021, 2022, 2023, 2024, 2025)
internal val TreeAmounts = floatArrayOf(3120f, 4860f, 6240f, 5480f, 8420f, 7150f, 9380f)
internal const val RING_COUNT = 7
internal val TreeTotal = TreeAmounts.sum() // 44,650

/** The ring selected at rest (the poster frame): 2021. */
internal const val REST_SEL = 2

// Script (seconds).
private const val SHRINK_AT = 1.4f
private const val SHRINK_STAGGER = 0.08f
private const val SHRINK_FOR = 0.5f
private const val GROW_AT = 2.5f
private const val GROW_STEP = 3.9f / 7f
private const val GROW_FOR = 0.62f
private const val PICK_AT = 6.4f
private const val PICK_FOR = 1.0f

internal fun treeClamp01(x: Float) = min(max(x, 0f), 1f)

private fun seg(t: Float, a: Float, b: Float) = treeClamp01((t - a) / (b - a))

internal fun treeEaseInOut(x: Float): Float {
    val t = treeClamp01(x)
    return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
}

/** Back-out: rises past 1 (about 7 %) and settles at exactly 1. */
private fun backOut(x: Float): Float {
    val t = treeClamp01(x)
    val c = 1.2f
    return 1f + (c + 1f) * (t - 1f).pow(3) + c * (t - 1f).pow(2)
}

/** The pose the shader and the labels read: ring growth, the (continuous) selected ring, the glow. */
internal class TreePose {
    val g = FloatArray(RING_COUNT)
    var sel = REST_SEL.toFloat()
    var glow = 1f
}

internal fun treeLoopPose(t: Float, out: TreePose): TreePose {
    for (i in 0 until RING_COUNT) {
        out.g[i] = if (t < GROW_AT) {
            val a = SHRINK_AT + (RING_COUNT - 1 - i) * SHRINK_STAGGER
            1f - treeEaseInOut(seg(t, a, a + SHRINK_FOR))
        } else {
            val a = GROW_AT + i * GROW_STEP
            backOut(seg(t, a, a + GROW_FOR))
        }
    }
    var sel = REST_SEL.toFloat()
    if (t >= SHRINK_AT && t < GROW_AT) {
        sel = REST_SEL * (1f - treeEaseInOut(seg(t, SHRINK_AT, SHRINK_AT + 0.8f)))
    } else if (t >= GROW_AT && t < PICK_AT) {
        sel = 0f
        for (i in 1 until RING_COUNT) {
            val a = GROW_AT + i * GROW_STEP + 0.05f
            sel += treeEaseInOut(seg(t, a, a + 0.32f))
        }
    } else if (t >= PICK_AT) {
        sel = RING_COUNT - 1 - (RING_COUNT - 1 - REST_SEL) * treeEaseInOut(seg(t, PICK_AT, PICK_AT + PICK_FOR))
    }
    // Full glow at rest; it dims while the rings shrink and regrow, and comes back as 2021 is picked.
    val dim = treeEaseInOut(seg(t, SHRINK_AT, SHRINK_AT + 0.4f)) * (1f - treeEaseInOut(seg(t, PICK_AT, PICK_AT + 0.5f)))
    out.sel = sel
    out.glow = 1f - 0.45f * dim
    return out
}

/** The finger's pose: s = how far out the slice has grown (0 = pith only, 7 = all rings). */
internal fun treeTouchPose(s: Float, sel: Float, out: TreePose): TreePose {
    for (i in 0 until RING_COUNT) {
        val u = treeClamp01(s - i)
        out.g[i] = u * (1f + 0.07f * sin(PI.toFloat() * u))
    }
    out.sel = sel
    out.glow = 1f
    return out
}

/** How grown the slice is, as the finger's s (used when a finger takes over the loop). */
internal fun treeGrownOf(p: TreePose): Float {
    var s = 0f
    for (i in 0 until RING_COUNT) s += treeClamp01(p.g[i])
    return s
}

/** The savings shown by the rolling total: the grown share of every ring. */
internal fun treeTotalOf(p: TreePose): Float {
    var v = 0f
    for (i in 0 until RING_COUNT) v += TreeAmounts[i] * treeClamp01(p.g[i])
    return v
}

/** Every ring's outer radius for a pose. */
internal fun treeRingRadii(p: TreePose, pith: Float, wood: Float, out: FloatArray): FloatArray {
    var r = pith
    for (i in 0 until RING_COUNT) {
        r += (wood - pith) * TreeAmounts[i] * max(0f, p.g[i]) / TreeTotal
        out[i] = r
    }
    return out
}

/** Full-grown outer radius of each ring (for the guide circles and for mapping the finger). */
internal fun treeFullRadii(pith: Float, wood: Float): FloatArray {
    val out = FloatArray(RING_COUNT)
    var r = pith
    for (i in 0 until RING_COUNT) {
        r += (wood - pith) * TreeAmounts[i] / TreeTotal
        out[i] = r
    }
    return out
}

/** The finger's distance from the pith, as s (0 at the pith, i + fraction inside ring i). */
internal fun treeSOfRadius(r: Float, pith: Float, wood: Float): Float {
    val full = treeFullRadii(pith, wood)
    var prev = pith
    for (i in 0 until RING_COUNT) {
        if (r < full[i]) return i + treeClamp01((r - prev) / (full[i] - prev))
        prev = full[i]
    }
    // Past the bark: rubber-band a little beyond 7.
    val over = (r - full[RING_COUNT - 1]) / 40f
    return RING_COUNT + 0.25f * over / (1f + over)
}

/** Middle radius of the (continuous) selected ring, from the pose's ring radii. */
internal fun treeSelectedMid(radii: FloatArray, pith: Float, sel: Float): Float {
    val i0 = max(0, min(RING_COUNT - 1, floor(sel).toInt()))
    val i1 = min(RING_COUNT - 1, i0 + 1)
    val f = treeClamp01(sel - i0)
    val m0 = ((if (i0 == 0) pith else radii[i0 - 1]) + radii[i0]) / 2f
    val m1 = ((if (i1 == 0) pith else radii[i1 - 1]) + radii[i1]) / 2f
    return m0 + (m1 - m0) * f
}

private fun money(v: Float) = "$" + String.format(Locale.US, "%,d", v.roundToInt())

internal val TreeLabels = List(RING_COUNT) { i -> "${TreeYears[i]} · ${money(TreeAmounts[i])}" }
internal val TreeDeltas = List(RING_COUNT) { i ->
    if (i == 0) {
        "First ring · the account opens"
    } else {
        val d = ((TreeAmounts[i] - TreeAmounts[i - 1]) / TreeAmounts[i - 1] * 100f).roundToInt()
        "${if (d >= 0) "+" else "−"}${abs(d)}% on ${TreeYears[i - 1]}"
    }
}

/** Hero geometry in dp: the hero's rect (ox, oy, w, h) and the slice inside it. */
internal class TreeGeo(val ox: Float, val oy: Float, val w: Float, val h: Float) {
    val slice = max(60f, min(w * 0.41f, h / 2f - 30f))
    val bark = slice * 0.068f
    val cx = ox + w / 2f
    val cy = oy + h / 2f - 10f
    val pith = max(3f, slice * 0.025f)
    val wood = slice - bark
    val unit = slice / 161f
    val full = treeFullRadii(pith, wood)
}
