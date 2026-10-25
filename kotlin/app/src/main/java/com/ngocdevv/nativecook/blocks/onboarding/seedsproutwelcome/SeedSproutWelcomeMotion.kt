package com.ngocdevv.nativecook.blocks.onboarding.seedsproutwelcome

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The block's clock and plant geometry, ported from seedMotion.ts. Every value is a pure function
 * of the loop time t (or of the growth the finger's watering has given the plant), so the frame at
 * t = LOOP equals the frame at 0. Coordinates are local to the pot: (0, 0) is the middle of the
 * soil, y down, dp (= pt).
 */
internal object SeedSproutWelcomeMotion {
    const val LOOP = 10.0

    /** Stem height at full growth, and the share of growth that is the sprout (the rest is bloom). */
    const val STEM = 205.0
    private const val SPROUT = 0.55

    /** The can's resting place and where it pours from (its body's centre), and its pouring tilt. */
    const val PARK_X = 120.0
    const val PARK_Y = -266.0
    private const val POUR_X = 112.0
    private const val POUR_Y = -196.0
    const val TILT = -0.55

    /** The spout's rose, in the can's own coordinates. */
    private const val TIP_X = -97.0
    private const val TIP_Y = -24.0

    const val DROPS = 64
    private const val FALL = 0.68
    private const val GRAVITY = 900.0
    const val PETALS = 7

    fun clamp01(x: Double) = min(1.0, max(0.0, x))
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeOutBack(x: Double): Double {
        val c = 1.4
        return 1 + (c + 1) * (x - 1).pow(3) + c * (x - 1).pow(2)
    }

    fun hash(i: Double): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    class Scene(
        /** Growth 0 (seed) … 1 (full bloom). */
        val g: Double,
        /** Can position, tilt, and how hard it pours 0…1. */
        val cx: Double,
        val cy: Double,
        val tilt: Double,
        val pour: Double,
        /** Wet soil 0…1, petal fall 0…1 (each petal staggers inside it). */
        val wet: Double,
        val fall: Double,
        /** Page 0 … 2 for the copy and the dots. */
        val page: Double,
        val t: Double,
    )

    /** Page from growth: seed, sprout, bloom. */
    fun pageOf(g: Double) = easeInOut(seg(g, 0.14, 0.32)) + easeInOut(seg(g, 0.64, 0.82))

    private fun tipAt(t: Double) = easeInOut(seg(t, 1.8, 2.1)) * (1 - easeInOut(seg(t, 2.8, 3.05)))

    /** How hard the loop's can pours at t (the drops ask this for the moment they left the rose). */
    fun pourAt(t: Double) = clamp01((tipAt(t) - 0.6) / 0.4)

    /** The whole loop. */
    fun sceneAt(t: Double): Scene {
        val go = easeInOut(seg(t, 1.4, 2.0))
        val tip = tipAt(t)
        val home = easeInOut(seg(t, 3.0, 3.55))
        val k = go * (1 - home)
        val g = SPROUT * easeInOut(seg(t, 2.6, 4.6)) + (1 - SPROUT) * easeInOut(seg(t, 5.0, 6.8)) - easeInOut(seg(t, 8.2, 9.6))
        val gc = clamp01(g)
        return Scene(
            g = gc,
            cx = PARK_X + (POUR_X - PARK_X) * k,
            cy = PARK_Y + (POUR_Y - PARK_Y) * k,
            tilt = TILT * tip,
            pour = clamp01((tip - 0.6) / 0.4),
            wet = easeOut(seg(t, 2.05, 2.7)) * (1 - easeInOut(seg(t, 4.2, 6.4))),
            fall = seg(t, 8.0, 9.4),
            page = pageOf(gc),
            t = t,
        )
    }

    /** The spout's rose in pot coordinates for a can at (cx, cy) tilted by [tilt]: x, y. */
    fun spoutX(cx: Double, tilt: Double) = cx + TIP_X * cos(tilt) - TIP_Y * sin(tilt)
    fun spoutY(cy: Double, tilt: Double) = cy + TIP_X * sin(tilt) + TIP_Y * cos(tilt)

    /**
     * Drop i of the shower at time [wt] (a free-running clock): released from the rose `life`
     * seconds ago with a seeded spread, it falls until it reaches the soil. Writes x, y, r, life
     * into [out] and returns false when the drop is not in the air.
     */
    fun dropAt(i: Int, wt: Double, x0: Double, y0: Double, tilt: Double, out: DoubleArray): Boolean {
        val life = (((wt + hash(i * 1.7) * FALL) % FALL) + FALL) % FALL
        // Out along the spout (pointing left and a little up in the can's frame), with a seeded spread.
        val c = cos(tilt)
        val s = sin(tilt)
        val dir = atan2(-0.96 * s - 0.27 * c, -0.96 * c + 0.27 * s) + (hash(i * 5.9) - 0.5) * 0.8
        val speed = 60 + 60 * hash(i * 3.1)
        val x = x0 + cos(dir) * speed * life
        val y = y0 + sin(dir) * speed * life + 0.5 * GRAVITY * life * life
        if (y > -2 + 6 * hash(i * 7.3)) return false
        out[0] = x
        out[1] = y
        out[2] = 1.5 + 1.6 * hash(i * 2.2)
        out[3] = life
        return true
    }

    /** The stem as a cubic from the soil up to STEM; [sway] bends the top sideways. Writes x, y, angle. */
    fun stemPoint(u: Double, sway: Double, out: DoubleArray) {
        val p1x = -20.0
        val p1y = -STEM * 0.36
        val p2x = 24 + sway * 0.4
        val p2y = -STEM * 0.7
        val p3x = sway
        val p3y = -STEM
        val b = 3 * (1 - u) * (1 - u) * u
        val c = 3 * (1 - u) * u * u
        val d = u * u * u
        out[0] = b * p1x + c * p2x + d * p3x
        out[1] = b * p1y + c * p2y + d * p3y
        val tx = 3 * (1 - u) * (1 - u) * p1x + 6 * (1 - u) * u * (p2x - p1x) + 3 * u * u * (p3x - p2x)
        val ty = 3 * (1 - u) * (1 - u) * p1y + 6 * (1 - u) * u * (p2y - p1y) + 3 * u * u * (p3y - p2y)
        out[2] = atan2(ty, tx)
    }

    /** How far up the stem has grown (0…1). */
    fun stemOf(g: Double) = clamp01(g / SPROUT)

    /** How open the bloom is (0…1). */
    fun bloomOf(g: Double) = clamp01((g - SPROUT - 0.04) / (1 - SPROUT - 0.04))

    /** Leaves: where on the stem (0…1), which side, length. */
    val leafU = doubleArrayOf(0.24, 0.38, 0.55, 0.7)
    val leafSide = doubleArrayOf(-1.0, 1.0, -1.0, 1.0)
    val leafLen = doubleArrayOf(50.0, 56.0, 48.0, 40.0)

    /** Leaf k's length (0 folded … 1 open, with a springy overshoot as it unfurls). */
    fun leafLength(stem: Double, k: Int): Double {
        val g = clamp01((stem - leafU[k] - 0.04) / 0.26)
        return if (g <= 0) 0.0 else easeOutBack(g)
    }

    /** Leaf k's width 0…1. */
    fun leafWide(stem: Double, k: Int): Double {
        val g = clamp01((stem - leafU[k] - 0.04) / 0.26)
        return easeOut(clamp01((g - 0.2) / 0.8))
    }

    /** The stem's sway (dp) at loop time t for growth g. */
    fun sway(t: Double, g: Double) = 7 * sin(2 * PI * 2 * t / LOOP) * g

    /** Mixes two 0xRRGGBB colours channel by channel, rounded like the RN version. */
    fun mixRgb(a: Int, b: Int, t: Double): Int {
        fun ch(v: Int, s: Int) = (v shr s) and 255
        val r = Math.round(ch(a, 16) * (1 - t) + ch(b, 16) * t).toInt()
        val g = Math.round(ch(a, 8) * (1 - t) + ch(b, 8) * t).toInt()
        val bl = Math.round(ch(a, 0) * (1 - t) + ch(b, 0) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
