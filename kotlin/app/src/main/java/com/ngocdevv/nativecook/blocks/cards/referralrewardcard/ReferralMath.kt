package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Trail
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Double precision retains fractional milliseconds throughout the path and spring calculations.
internal object ReferralMath {
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)

    // This ramp has zero slope at both ends, so neighboring stops meet without a corner.
    fun smooth(x: Double): Double {
        val t = clamp(x)
        return t * t * (3 - 2 * t)
    }

    fun quart(x: Double): Double {
        val t = 1 - clamp(x)
        return 1 - t * t * t * t
    }

    fun cubic(x: Double): Double {
        val t = clamp(x)
        return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    }

    fun wrap(
        x: Double,
        count: Double = 7.0,
    ) = ((x % count) + count) % count

    fun index(x: Int) = ((x % 7) + 7) % 7

    fun nearest(x: Double) = floor(x + 0.5).toInt()

    // Position in friends relative to the lens, wrapped into [-3.5, 3.5).
    fun offset(
        index: Int,
        scroll: Double,
    ) = wrap(index - scroll + 3.5) - 3.5

    // Count the completed thresholds; the remaining fraction belongs to the next tier.
    fun tier(count: Int) = ReferralPalette.tiers.count { count >= it }

    fun arc(count: Int): Double {
        val level = tier(count)
        if (level >= ReferralPalette.tiers.size) return 1.0
        val from = if (level == 0) 0 else ReferralPalette.tiers[level - 1]
        return (count - from) / (ReferralPalette.tiers[level] - from).toDouble()
    }

    fun ramp(
        x: Double,
        values: DoubleArray,
    ): Double {
        val position = x.coerceIn(0.0, values.lastIndex.toDouble())
        val index = min(floor(position).toInt(), values.lastIndex - 1)
        return values[index] + (values[index + 1] - values[index]) * smooth(position - index)
    }

    // Invert the cubic's x coordinate, then evaluate y. Same curves as ui.tsx.
    fun bezier(
        t: Double,
        inOut: Boolean = false,
    ): Double {
        val x = clamp(t)
        if (x == 0.0 || x == 1.0) return x
        val x1 = if (inOut) 0.77 else 0.23
        val x2 = if (inOut) 0.175 else 0.32
        var lower = 0.0
        var upper = 1.0
        var u = x
        repeat(26) {
            val m = 1 - u
            val v = 3 * m * m * u * x1 + 3 * m * u * u * x2 + u * u * u
            if (v < x) lower = u else upper = u
            u = (lower + upper) / 2
        }
        val m = 1 - u
        return if (inOut) 3 * m * u * u + u * u * u else 3 * m * m * u + 3 * m * u * u + u * u * u
    }

    // A tail grows from a point at launch and narrows again as each sample enters the target.
    fun ribbonWidth(
        k: Int,
        u: Double,
        t: Double,
        initialRadius: Double,
    ): Double {
        val reach = min(Trail.samples.toDouble(), max(t / Trail.gap, 1.0))
        val taper = max(1 - k / reach, 0.0).pow(1.3)
        val born = min(t / 0.06, 1.0)
        val taken = if (u > 0.9) (1 - u) / 0.1 else 1.0
        return (initialRadius * (1 - born) + Trail.size * born) * taper * taken
    }
}
