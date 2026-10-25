package com.ngocdevv.nativecook.blocks.media.paintsmearphoto

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// The paint's drag field (paintSmearMotion.ts): up to TRAIL points (card dp), each carrying a drag
// vector (dp). In autoPlay the points are rebuilt every frame from the loop time (three curved
// brush strokes, then the paint flowing back); under a finger new points are added and, when the
// trail is full, the two oldest merge into one so nothing already painted pops away.

/** The autoPlay period (s), equal to the manifest's recording.loop. */
const val LOOP = 9.0
internal const val PAINT_TRAIL = 24
/** Brush radius (Gaussian sigma, dp) for the S / M / L chips. */
internal val PaintBrushes = floatArrayOf(18f, 26f, 36f)

private class Stroke(val a: Double, val b: Double, val from: DoubleArray, val via: DoubleArray, val to: DoubleArray)

private val Strokes = arrayOf(
    Stroke(1.4, 2.4, doubleArrayOf(0.12, 0.3), doubleArrayOf(0.5, 0.08), doubleArrayOf(0.9, 0.36)),
    Stroke(2.6, 3.6, doubleArrayOf(0.88, 0.6), doubleArrayOf(0.46, 0.78), doubleArrayOf(0.1, 0.5)),
    Stroke(3.8, 4.8, doubleArrayOf(0.28, 0.94), doubleArrayOf(0.56, 0.6), doubleArrayOf(0.86, 0.86)),
)
private val PointsPerStroke = PAINT_TRAIL / Strokes.size
private const val SCRIPT_DRAG = 48.0
private const val PICKUP = 0.3
private const val FLOW_BACK_A = 6.2
private const val FLOW_BACK_B = 8.2
internal const val PAINT_STEP = 16.0
private const val DRAG_PER_PT = 1.1
private const val DRAG_MAX = 34.0
private const val MERGED_MAX = 70.0
internal const val PAINT_IDLE_BEFORE_BACK = 2.0
private const val RESET_FOR = 0.85

internal const val PAINT_AUTO = 0
internal const val PAINT_LIVE = 1

internal class PaintSmearState(auto: Boolean) {
    var mode = if (auto) PAINT_AUTO else PAINT_LIVE
    var t = 0.0
    var now = 0.0
    var lastTouch = -100.0
    /** The trail, packed x, y, ax, ay; `count` points are in use. */
    val trail = DoubleArray(PAINT_TRAIL * 4)
    var count = 0
    var flow = 1.0
    var resetAt = -1.0
    var resume = false
    var painting = false
    var lx = 0.0
    var ly = 0.0

    fun clear() {
        count = 0
    }

    fun push(x: Double, y: Double, ax: Double, ay: Double) {
        val i = count * 4
        trail[i] = x; trail[i + 1] = y; trail[i + 2] = ax; trail[i + 3] = ay
        count += 1
    }
}

private fun seg(t: Double, a: Double, b: Double) = min(max((t - a) / (b - a), 0.0), 1.0)
private fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun easeOut(x: Double) = 1 - (1 - x).pow(3)

internal fun PaintSmearState.scriptFrame(t: Double, w: Double, h: Double) {
    clear()
    for (st in Strokes) {
        for (j in 0 until PointsPerStroke) {
            // Points are laid at even times; the brush eases out of the press and into the lift.
            val tau = j.toDouble() / (PointsPerStroke - 1)
            val born = st.a + (st.b - st.a) * tau
            if (t < born) continue
            val u = easeInOut(tau)
            val v = 1 - u
            val x = (v * v * st.from[0] + 2 * v * u * st.via[0] + u * u * st.to[0]) * w
            val y = (v * v * st.from[1] + 2 * v * u * st.via[1] + u * u * st.to[1]) * h
            val tx = (2 * v * (st.via[0] - st.from[0]) + 2 * u * (st.to[0] - st.via[0])) * w
            val ty = (2 * v * (st.via[1] - st.from[1]) + 2 * u * (st.to[1] - st.via[1])) * h
            val l = max(hypot(tx, ty), 0.0001)
            val a = SCRIPT_DRAG * easeOut(seg(t, born, born + PICKUP))
            push(x, y, tx / l * a, ty / l * a)
        }
    }
    flow = 1 - easeInOut(seg(t, FLOW_BACK_A, FLOW_BACK_B))
}

/** A finger took over: keep the field exactly as it looks, with the flow baked into the drags. */
internal fun PaintSmearState.takeOver() {
    for (i in 0 until count) {
        trail[i * 4 + 2] *= flow
        trail[i * 4 + 3] *= flow
    }
    flow = 1.0
    resetAt = -1.0
    mode = PAINT_LIVE
}

/** Lays a point between the last one and (x, y), dragging the paint along the travel. */
internal fun PaintSmearState.lay(x: Double, y: Double) {
    val dx = x - lx
    val dy = y - ly
    val d = hypot(dx, dy)
    if (d < 0.5) return
    if (count >= PAINT_TRAIL) {
        // Merge the two oldest: a drag-weighted position, the summed drag (capped).
        val w0 = hypot(trail[2], trail[3]) + 0.001
        val w1 = hypot(trail[6], trail[7]) + 0.001
        val mx = (trail[0] * w0 + trail[4] * w1) / (w0 + w1)
        val my = (trail[1] * w0 + trail[5] * w1) / (w0 + w1)
        var ax = trail[2] + trail[6]
        var ay = trail[3] + trail[7]
        val m = hypot(ax, ay)
        if (m > MERGED_MAX) {
            ax *= MERGED_MAX / m
            ay *= MERGED_MAX / m
        }
        trail[0] = mx; trail[1] = my; trail[2] = ax; trail[3] = ay
        System.arraycopy(trail, 8, trail, 4, (count - 2) * 4)
        count -= 1
    }
    val a = min(d * DRAG_PER_PT, DRAG_MAX)
    push((x + lx) / 2, (y + ly) / 2, dx / d * a, dy / d * a)
    lx = x
    ly = y
}

internal fun PaintSmearState.startReset(resume: Boolean) {
    if (resetAt >= 0) return
    resetAt = now
    this.resume = resume
}

/** The flow-back of a reset; true when it has finished. */
internal fun PaintSmearState.resetFrame(): Boolean {
    if (resetAt < 0) return false
    val k = easeInOut(min((now - resetAt) / RESET_FOR, 1.0))
    flow = 1 - k
    if (k >= 1) {
        clear()
        flow = 1.0
        resetAt = -1.0
        return true
    }
    return false
}
