package com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Layout, the crayon's scripted path and the rubbed-field grid of the Crayon Rubbing Card
 * (crayonRubbingMotion.ts). The grid is GX × GY cells over the paper, 0 (untouched) … 1 (rubbed).
 */
internal object CrayonRubbingCardMotion {
    /** Loop period in seconds. */
    const val LOOP = 9f

    const val DESIGN_W = 393f
    const val DESIGN_H = 759f

    /** The hero canvas (design dp) and, inside it, the bronze plaque and the paper over it. */
    const val CANVAS_Y = 104f
    const val CANVAS_W = DESIGN_W
    const val CANVAS_H = 500f
    const val PLAQUE_X = 24f
    const val PLAQUE_Y = 8f
    const val PLAQUE_W = 345f
    const val PLAQUE_H = 432f
    const val PAPER_X = 34f
    const val PAPER_Y = 16f
    const val PAPER_W = 325f
    const val PAPER_H = 416f

    /** Where the crayon lies when nobody rubs (canvas dp: its tip, and its angle in degrees). */
    const val REST_X = 176f
    const val REST_Y = 474f
    const val REST_ANGLE = -8f

    const val CELL = 8f
    val GX = ceil(PAPER_W / CELL).toInt()
    val GY = ceil(PAPER_H / CELL).toInt()
    const val REACH = 17f
    private const val STROKES = 14f
    const val PASSES = 4

    fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    fun easeInOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

    private val Pi = PI.toFloat()

    /** The demo's scrubbing hand in paper dp at a time inside the scrub (0.35–4.0 s). */
    fun scrubAt(t: Float, out: FloatArray) {
        if (t < 3.4f) {
            val s = seg(t, 0.35f, 3.4f)
            val y = 22f + (PAPER_H - 44f) * s
            val x = PAPER_W / 2f + (PAPER_W / 2f - 24f) * sin(Pi * STROKES * s - Pi / 2f)
            out[0] = x
            out[1] = y + 6f * sin(Pi * 2f * STROKES * s)
            return
        }
        val u = seg(t, 3.4f, 4.0f)
        val cx = 176f - 30f * (1f - u)
        val cy = 190f + 150f * (1f - u) * (1f - u)
        out[0] = cx + 34f * cos(Pi * 6f * u + Pi)
        out[1] = cy + 22f * sin(Pi * 6f * u)
    }

    /** The crayon's tip, angle and contact at loop time t, in canvas dp: [x, y, angle, contact]. */
    fun crayonAt(t: Float, out: FloatArray) {
        val tmp = FloatArray(2)
        if (t < 0.35f) {
            scrubAt(0.35f, tmp)
            val sx = PAPER_X + tmp[0]
            val sy = PAPER_Y + tmp[1]
            val u = easeInOutCubic(seg(t, 0f, 0.35f))
            val lift = sin(Pi * u) * 26f
            out[0] = REST_X + (sx - REST_X) * u
            out[1] = REST_Y + (sy - REST_Y) * u - lift
            out[2] = REST_ANGLE + (-34f - REST_ANGLE) * u
            out[3] = 0f
            return
        }
        if (t < 4.0f) {
            scrubAt(t, tmp)
            val sway = 6f * sin(Pi * STROKES * seg(t, 0.35f, 3.4f))
            out[0] = PAPER_X + tmp[0]
            out[1] = PAPER_Y + tmp[1]
            out[2] = -34f + sway
            out[3] = 1f
            return
        }
        scrubAt(4.0f, tmp)
        val ex = PAPER_X + tmp[0]
        val ey = PAPER_Y + tmp[1]
        val u = easeInOutCubic(seg(t, 4.0f, 4.55f))
        val lift = sin(Pi * u) * 26f
        out[0] = ex + (REST_X - ex) * u
        out[1] = ey + (REST_Y - ey) * u - lift
        out[2] = -34f + (REST_ANGLE + 34f) * u
        out[3] = 0f
    }

    /** The rubbed paper peels off the plaque 7.3–8.75 s, leaving a fresh sheet. */
    fun autoPeel(t: Float) = easeInOutCubic(seg(t, 7.3f, 8.75f))

    /** The "Collected" badge: in 4.0–4.6 s (a spring-like pop), out as the sheet peels. */
    fun autoBadge(t: Float): Float {
        val u = seg(t, 4.0f, 4.6f)
        val pop = if (u >= 1f) 1f else 1f - exp(-u * 6f) * cos(u * 9f)
        return pop * (1f - seg(t, 7.3f, 7.7f))
    }

    // When the demo's crayon passes over each cell, worked out once by following the path at 240 Hz.
    val passTimes: FloatArray
    val passAmounts: FloatArray

    init {
        val n = GX * GY
        val times = FloatArray(n * PASSES) { -1f }
        val amounts = FloatArray(n * PASSES)
        val last = FloatArray(n) { -10f }
        val r = ceil(REACH / CELL).toInt()
        val p = FloatArray(2)
        var step = 0
        while (true) {
            val t = 0.35f + step / 240f
            if (t > 4.0f) break
            step++
            scrubAt(t, p)
            val cx = p[0] / CELL
            val cy = p[1] / CELL
            for (gy in floor(cy - r).toInt()..ceil(cy + r).toInt()) {
                for (gx in floor(cx - r).toInt()..ceil(cx + r).toInt()) {
                    if (gx < 0 || gy < 0 || gx >= GX || gy >= GY) continue
                    val d = hypot((gx + 0.5f) * CELL - p[0], (gy + 0.5f) * CELL - p[1])
                    if (d > REACH) continue
                    val i = gy * GX + gx
                    if (t - last[i] > 0.12f) {
                        for (k in 0 until PASSES) {
                            if (times[i * PASSES + k] < 0f) {
                                times[i * PASSES + k] = t
                                amounts[i * PASSES + k] = (0.74f + 0.16f * sin(i * 1.7f + k * 2.3f)) * (1f - 0.35f * (d / REACH))
                                break
                            }
                        }
                    }
                    last[i] = t
                }
            }
        }
        passTimes = times
        passAmounts = amounts
    }

    /** Fills [grid] with the demo's rubbed field at loop time t (all clear once the sheet is gone). */
    fun autoGrid(t: Float, grid: FloatArray) {
        val fresh = t >= 8.75f
        for (i in 0 until GX * GY) {
            var v = 0f
            if (!fresh) {
                for (k in 0 until PASSES) {
                    val at = passTimes[i * PASSES + k]
                    if (at < 0f) break
                    v += passAmounts[i * PASSES + k] * clamp01((t - at) / 0.09f)
                }
            }
            grid[i] = min(1f, v)
        }
    }

    /** Rubs along a finger's segment (paper dp): cells under the crayon's side darken with travel. */
    fun rubSegment(grid: FloatArray, x0: Float, y0: Float, x1: Float, y1: Float) {
        val len = hypot(x1 - x0, y1 - y0)
        val steps = max(1, ceil(len / 4f).toInt())
        val r = ceil(REACH / CELL).toInt()
        for (s in 1..steps) {
            val px = x0 + (x1 - x0) * s / steps
            val py = y0 + (y1 - y0) * s / steps
            val cx = px / CELL
            val cy = py / CELL
            for (gy in floor(cy - r).toInt()..ceil(cy + r).toInt()) {
                for (gx in floor(cx - r).toInt()..ceil(cx + r).toInt()) {
                    if (gx < 0 || gy < 0 || gx >= GX || gy >= GY) continue
                    val d = hypot((gx + 0.5f) * CELL - px, (gy + 0.5f) * CELL - py)
                    if (d > REACH) continue
                    val i = gy * GX + gx
                    grid[i] = min(1f, grid[i] + 0.05f * (1f - 0.35f * (d / REACH)))
                }
            }
        }
    }

    /** Share of the relief revealed: the rubbed field weighted by how much relief each cell holds. */
    fun revealed(grid: FloatArray, weights: FloatArray, total: Float): Float {
        if (total <= 0f) return 0f
        var sum = 0f
        for (i in 0 until GX * GY) sum += grid[i] * weights[i]
        return sum / total
    }

    /** The field as opaque grey pixels (for the shader) and as black with alpha (for the fallback's mask). */
    fun pack(grid: FloatArray, grey: IntArray, alpha: IntArray) {
        for (i in 0 until GX * GY) {
            val v = (clamp01(grid[i]) * 255f).roundToInt()
            grey[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            alpha[i] = v shl 24
        }
    }
}
