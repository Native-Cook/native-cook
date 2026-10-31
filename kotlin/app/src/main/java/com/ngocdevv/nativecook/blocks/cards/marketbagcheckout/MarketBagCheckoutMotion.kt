package com.ngocdevv.nativecook.blocks.cards.marketbagcheckout

import kotlin.math.*

/** Deterministic checkout motion: durations and timestamps are seconds; STRETCH is dp. */
internal object MarketBagCheckoutMotion {
    const val LOOP = 10.0
    const val FLY = 0.55
    const val HOLD = 1.25
    const val STRETCH = 34.0

    /** Artwork size is dp, rotation is radians, and slots are fractions of bag width and depth. */
    data class Item(
        val key: String,
        val name: String,
        val meta: String,
        val price: Double,
        val weight: Double,
        val slotX: Double,
        val slotY: Double,
        val size: Double,
        val rotation: Double,
    )

    val items =
        listOf(
            Item("avocado", "Hass avocado", "1 pc · ripe today", 1.8, 0.22, -0.3, 0.62, 66.0, -0.3),
            Item("milk", "Whole milk", "500 ml · glass bottle", 2.4, 0.62, 0.24, 0.5, 78.0, 0.06),
            Item("lemons", "Lemons", "2 pc · unwaxed", 1.1, 0.24, -0.12, 0.74, 70.0, 0.15),
            Item(
                "tomatoes",
                "Vine tomatoes",
                "3 pc · on the vine",
                2.9,
                0.36,
                0.12,
                0.3,
                72.0,
                -0.1,
            ),
            Item(
                "baguette",
                "Country baguette",
                "1 · baked at 6 am",
                3.2,
                0.26,
                -0.18,
                0.06,
                112.0,
                -0.55,
            ),
        )

    /** Flight and hold are 0..1; land, leave, paid and bag use seconds (-1 means absent). */
    data class Pose(
        val fly: DoubleArray = DoubleArray(5),
        val land: DoubleArray = DoubleArray(5) { -1.0 },
        val leave: DoubleArray = DoubleArray(5) { -1.0 },
        var hold: Double = 0.0,
        var paid: Double = -1.0,
        var reset: Double = 0.0,
        var bag: Double = 0.0,
    )

    /** Clamped progress between two timestamps or normalized boundaries. */
    fun seg(value: Double, start: Double, end: Double) =
        ((value - start) / (end - start)).coerceIn(0.0, 1.0)

    fun smooth(start: Double, end: Double, value: Double): Double {
        val progress = seg(value, start, end)
        return progress * progress * (3 - 2 * progress)
    }

    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    /** Unit step response with time and natural period in seconds and a damping ratio. */
    fun springStep(t: Double, duration: Double, ratio: Double): Double {
        if (t <= 0) return 0.0
        val angularFrequency = 2 * PI / duration
        val dampedFrequency = angularFrequency * sqrt(1 - ratio * ratio)
        return 1 -
            exp(-ratio * angularFrequency * t) *
                (cos(dampedFrequency * t) +
                    (ratio * angularFrequency / dampedFrequency) * sin(dampedFrequency * t))
    }

    /** Weighted landing and removal responses, in fractions of STRETCH. */
    fun bagStretch(pose: Pose): Double {
        val totalWeight = items.sumOf { it.weight }
        var stretch = 0.0
        for (itemIndex in 0..4) {
            if (pose.land[itemIndex] < 0 || pose.bag < pose.land[itemIndex]) continue
            val releaseResponse =
                if (pose.leave[itemIndex] >= 0 && pose.bag > pose.leave[itemIndex])
                    springStep(pose.bag - pose.leave[itemIndex], 0.42 * 1.3, 0.55)
                else 0.0
            stretch +=
                items[itemIndex].weight / totalWeight *
                    (springStep(pose.bag - pose.land[itemIndex], 0.42, 0.32) - releaseResponse)
        }
        return stretch
    }

    /**
     * Pose at loop time t in seconds: pack from 1.0 every 0.32; hold 3.0–4.25; paid until 8.55;
     * unpack every 0.12; rest from 9.5 through the 10-second boundary.
     */
    fun script(t: Double): Pose {
        val pose = Pose(bag = t)
        val rest = t >= 9.5
        for (itemIndex in 0..4) {
            val packAt = 1 + itemIndex * 0.32
            val unpackAt = 8.55 + (4 - itemIndex) * 0.12
            if (rest) continue
            if (t < unpackAt) {
                pose.fly[itemIndex] = easeInOut(seg(t, packAt, packAt + FLY))
                pose.land[itemIndex] = if (t >= packAt + FLY) packAt + FLY else -1.0
            } else {
                pose.fly[itemIndex] = 1 - easeInOut(seg(t, unpackAt, unpackAt + FLY * 0.8))
                pose.land[itemIndex] = packAt + FLY
                pose.leave[itemIndex] = unpackAt
            }
        }
        val reset = seg(t, 8.55, 8.9)
        pose.hold = if (rest) 0.0 else if (t < 8.55) smooth(3.0, 4.25, t) else 1 - reset
        pose.paid = if (!rest && t >= 4.25) t - 4.25 else -1.0
        pose.reset = if (rest) 0.0 else reset
        return pose
    }

    /** Half-width in dp at normalized depth v (0 at the rim, 1 at the bottom). */
    fun halfWidth(v: Double, rim: Double, belly: Double): Double {
        if (v < 0.5) return rim + (belly - rim) * sin(PI / 2 * (v / 0.5))
        val lowerDepth = (v - 0.5) / 0.5
        return belly * sqrt(max(0.0, 1 - lowerDepth * lowerDepth * 0.92))
    }
}
