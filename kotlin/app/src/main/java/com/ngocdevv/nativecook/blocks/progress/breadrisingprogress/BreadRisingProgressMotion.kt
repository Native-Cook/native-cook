package com.ngocdevv.nativecook.blocks.progress.breadrisingprogress

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Clock and shapes of the proofing loaf, ported from breadMotion.ts. Every value is a pure
 * function of the timeline t (seconds in [0, LOOP)); the finger scrubs the same timeline.
 * Coordinates are local to the hero: (0, 0) is the middle of the bowl's rim line, y down, dp.
 */
internal object BreadRisingProgressMotion {
    const val LOOP = 9.0

    // Beats (s).
    const val PROOF_START = 1.3
    const val PROOF_END = 4.0
    const val SHAPE_END = 4.8
    const val BAKE_END = 7.0
    private const val READY_END = 7.8
    private const val RESET_END = 8.6
    /** Where a released scrub settles: raw, risen, shaped, baked. */
    val STOPS = doubleArrayOf(PROOF_START, PROOF_END, SHAPE_END, BAKE_END)

    // The glass bowl: rim line at y = RIM_Y, a half-ellipse below it.
    const val RIM_Y = 0f
    const val BOWL_RX = 128f
    const val BOWL_RY = 146f
    private const val WALL = 6f
    private const val RAW_LEVEL = 66f
    private const val RAW_DOME = 22f
    private const val RISEN_DOME = 72f
    // The boule on the stone.
    const val BASE_Y = 140f
    private const val BOULE_RX = 118f
    private const val BOULE_TOP = 112f
    private const val BOULE_FOOT = 18f
    const val BOULE_CY = BASE_Y - BOULE_FOOT
    /** Points on each half of the dough's outline (the bottom and the dome). */
    const val HALF = 22
    const val BUBBLES = 12

    fun clamp01(x: Double) = min(1.0, max(0.0, x))
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    /** Ease-out that passes the target by about 6 % and settles: the dough "pops" into a boule. */
    fun easeOutBack(x: Double): Double {
        val c = 1.3
        return 1 + (c + 1) * (x - 1).pow(3) + c * (x - 1).pow(2)
    }

    fun hash(i: Double): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    class Bread(
        val rise: Double, val shape: Double, val bake: Double, val ready: Double,
        val bowlY: Double, val bowlA: Double, val stone: Double, val oven: Double, val steam: Double,
        val timer: Double, val stage: Double, val status: Double, val progress: Double, val t: Double,
    )

    /** The timer alone (the odometer wheels read it twice a frame). */
    fun timerAt(t: Double): Double = when {
        t >= PROOF_START && t < PROOF_END -> 5400 * (1 - easeInOut(seg(t, PROOF_START, PROOF_END)))
        t >= PROOF_END && t < SHAPE_END -> 2700 * easeInOut(seg(t, PROOF_END + 0.1, SHAPE_END))
        t >= SHAPE_END && t < READY_END -> 2700 * (1 - easeInOut(seg(t, SHAPE_END + 0.1, BAKE_END)))
        t >= READY_END -> 5400 * easeInOut(seg(t, READY_END, RESET_END))
        else -> 5400.0
    }

    fun stageAt(t: Double) =
        easeInOut(seg(t, PROOF_END, PROOF_END + 0.3)) + easeInOut(seg(t, SHAPE_END, SHAPE_END + 0.3)) + easeInOut(seg(t, READY_END, READY_END + 0.4))

    fun statusAt(t: Double) = stageAt(t) + easeInOut(seg(t, BAKE_END, BAKE_END + 0.3))

    fun progressAt(t: Double): Double {
        val back = easeInOut(seg(t, READY_END, RESET_END))
        return (0.5 * easeInOut(seg(t, PROOF_START, PROOF_END)) + 0.1 * seg(t, PROOF_END, SHAPE_END) + 0.4 * easeInOut(seg(t, SHAPE_END, BAKE_END))) * (1 - back)
    }

    /** The whole loop, or a scrubbed position on it. */
    fun breadAt(t: Double): Bread {
        val back = easeInOut(seg(t, READY_END, RESET_END))
        val rise = easeInOut(seg(t, PROOF_START, PROOF_END)) * (1 - back)
        val shape = (if (t < SHAPE_END) easeOutBack(seg(t, PROOF_END, SHAPE_END)) else 1.0) * (1 - back)
        val bake = easeInOut(seg(t, SHAPE_END + 0.1, BAKE_END)) * (1 - back)
        val leave = easeInOut(seg(t, PROOF_END, PROOF_END + 0.5))
        val come = easeOut(seg(t, READY_END + 0.3, RESET_END + 0.2))
        val bowlGone = leave * (1 - come)
        val oven = easeInOut(seg(t, 4.6, 5.3)) * (1 - easeInOut(seg(t, READY_END, READY_END + 0.6)))
        val steam = easeInOut(seg(t, 5.4, 6.3)) * (1 - easeInOut(seg(t, 7.3, 8.0)))
        val ready = easeOutBack(seg(t, BAKE_END, BAKE_END + 0.38)) * (1 - easeInOut(seg(t, READY_END, READY_END + 0.25)))
        return Bread(
            rise = rise, shape = shape, bake = bake, ready = ready,
            bowlY = 90 * bowlGone, bowlA = 1 - bowlGone,
            stone = min(1.0, leave) * (1 - easeInOut(seg(t, READY_END + 0.2, RESET_END))),
            oven = oven, steam = steam,
            timer = timerAt(t), stage = stageAt(t), status = statusAt(t), progress = progressAt(t), t = t,
        )
    }

    /** The bowl's inner wall: half-width at height y (0 above the bottom). */
    private fun innerHalf(y: Float): Float {
        val ry = BOWL_RY - WALL
        val k = (y - RIM_Y) / ry
        return (BOWL_RX - WALL) * sqrt(max(0f, 1 - k * k))
    }

    /**
     * The dough's outline as 2·HALF points (x, y interleaved): the bottom from left to right, then
     * the dome from right to left. In the bowl it follows the glass; as a boule it is a flat-footed
     * dome. [shape] blends the two point for point.
     */
    fun doughPoints(rise: Double, shape: Double, out: FloatArray): FloatArray {
        val r = rise.toFloat()
        val sh = shape.toFloat()
        val level = RIM_Y + RAW_LEVEL * (1 - r)
        val dome = RAW_DOME + (RISEN_DOME - RAW_DOME) * r * r
        val xl = max(innerHalf(level), 20f) * 0.98f
        val ry = BOWL_RY - WALL
        var n = 0
        for (k in 0 until HALF) {
            val u = k / (HALF - 1f)
            val bx = -xl + 2 * xl * u
            val bk = bx / (BOWL_RX - WALL)
            val by = RIM_Y + ry * sqrt(max(0f, 1 - bk * bk))
            val a = PI + PI * u
            val s = abs(sin(a)).toFloat()
            val px = BOULE_RX * cos(a).toFloat()
            val py = BOULE_CY + BOULE_FOOT * s.pow(0.3f)
            out[n] = bx + (px - bx) * sh
            out[n + 1] = by + (py - by) * sh
            n += 2
        }
        for (k in 0 until HALF) {
            val a = PI * (k + 0.5) / HALF
            val s = sin(a).toFloat()
            val bx = xl * cos(a).toFloat()
            val by = level - dome * s.pow(0.75f)
            val px = BOULE_RX * cos(a).toFloat()
            val py = BOULE_CY - BOULE_TOP * s.pow(0.85f)
            out[n] = bx + (px - bx) * sh
            out[n + 1] = by + (py - by) * sh
            n += 2
        }
        return out
    }

    /** Top of the dough (smallest y), for gradients and the steam. */
    fun doughTop(rise: Double, shape: Double): Float {
        val r = rise.toFloat()
        val level = RIM_Y + RAW_LEVEL * (1 - r)
        val dome = RAW_DOME + (RISEN_DOME - RAW_DOME) * r * r
        val bowl = level - dome
        val boule = BOULE_CY - BOULE_TOP
        return bowl + (boule - bowl) * shape.toFloat()
    }

    /**
     * Bubble i on the rising dome: where it sits, its radius, and its pop ring (radius and alpha)
     * as it bursts, written into [out] as x, y, r, ring, ringA. Bubbles live only during the proof.
     */
    fun bubbleAt(i: Int, t: Double, rise: Double, out: FloatArray) {
        val born = PROOF_START + 0.3 + 2.1 * hash(i * 3.3)
        val life = 0.55 + 0.35 * hash(i * 7.7)
        val g = (t - born) / life
        val a = PI * (0.18 + 0.64 * hash(i * 5.1))
        val r = rise.toFloat()
        val level = RIM_Y + RAW_LEVEL * (1 - r)
        val dome = RAW_DOME + (RISEN_DOME - RAW_DOME) * r * r
        val xl = max(innerHalf(level), 20f) * 0.98f
        out[0] = xl * cos(a).toFloat() * 0.96f
        out[1] = level - dome * sin(a).toFloat().pow(0.75f) + 2.5f
        val rmax = 3.2 + 4.2 * hash(i * 9.1)
        if (g <= 0 || g >= 1 || t > PROOF_END) {
            out[2] = 0f; out[3] = 0f; out[4] = 0f
            return
        }
        out[2] = if (g < 0.82) (rmax * easeOut(g / 0.82)).toFloat() else 0f
        val p = (g - 0.82) / 0.18
        out[3] = if (g >= 0.82) (rmax * (1 + 1.4 * p)).toFloat() else 0f
        out[4] = if (g >= 0.82) (1 - p).toFloat() else 0f
    }

    /** One odometer column: unit in seconds, base 10 or 6. Rests on whole digits at whole seconds. */
    fun column(v: Double, unit: Double, base: Int): Double {
        val whole = floor(v / unit)
        val within = v - whole * unit
        val roll = if (unit == 1.0) within else clamp01(within - (unit - 1))
        val pos = whole + roll
        return pos - floor(pos / base) * base
    }
}
