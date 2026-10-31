package com.ngocdevv.nativecook.blocks.cards.tapridepass

import kotlin.math.*

/**
 * Shared motion equations and deterministic 9 s autoplay script. Distances use screen points
 * (Swift) or dp (Compose); all timeline and spring durations use seconds.
 */
internal object TapRidePassMotion {
    const val LOOP = 9.0

    /** Clamps a value to a phase interval; reversing the interval reverses the ramp. */
    fun segment(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)

    /** Cubic smoothstep across the supplied interval, with zero slope at both ends. */
    fun smooth(a: Double, b: Double, x: Double): Double {
        val t = segment(x, a, b)
        return t * t * (3 - 2 * t)
    }

    /** Symmetric cubic easing for a normalized phase in 0…1. */
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    /** Cubic deceleration for a normalized phase in 0…1. */
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)

    /**
     * Closed-form spring response from rest at 0 toward 1. Duration is seconds; ratio is damping.
     */
    fun springStep(t: Double, duration: Double, ratio: Double): Double {
        if (t <= 0) return 0.0
        val w = 2 * PI / duration
        if (ratio >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(1 - ratio * ratio)
        return 1 - exp(-ratio * w * t) * (cos(wd * t) + ratio * w / wd * sin(wd * t))
    }

    /**
     * One semi-implicit spring step. Position is points/dp, velocity is points/dp per second. The
     * final argument is the integration step in seconds; duration and damping set the response.
     */
    fun spring(
        x: Double,
        v: Double,
        target: Double,
        duration: Double,
        ratio: Double,
        h: Double,
    ): Pair<Double, Double> {
        val w = 2 * PI / duration
        val nv = v + (w * w * (target - x) - 2 * ratio * w * v) * h
        return Pair(x + nv * h, nv)
    }

    /** Cubic path coordinate at normalized progress u; control coordinates share the same units. */
    fun bezier(a: Double, b: Double, c: Double, d: Double, u: Double): Double {
        val k = 1 - u
        return k * k * k * a + 3 * k * k * u * b + 3 * k * u * u * c + u * u * u * d
    }

    /** Seconds since the read: print 0.58–1.33 to 0.36, then rise 1.33–2.03 to 1. */
    fun ticketAfter(t: Double) =
        easeOut(segment(t, 0.58, 1.33)) * 0.36 + easeInOut(segment(t, 1.33, 2.03)) * 0.64

    /** Balance roll progress over 2.23–2.83 s after the read. */
    fun rollAfter(t: Double) = easeInOut(segment(t, 2.23, 2.83))

    /** Scripted card centre, normalized lift/ticket/roll progress, and seconds since the read. */
    data class Pose(
        val cardX: Double,
        val cardY: Double,
        val lift: Double = 0.0,
        val ticketProgress: Double = 0.0,
        val roll: Double = 0.0,
        val tapAge: Double = -10.0,
    )

    /**
     * Autoplay beats in seconds: rest to 1.2, lift to 1.45, approach to 2.8, read at 3.12. The card
     * returns as paper prints at 3.7–4.45, rises at 4.45–5.15 and rolls at 5.35–5.95. Close at
     * 7.05–7.8; from 8.3 onward the pose exactly equals the opening rest.
     */
    fun script(loopTime: Double, layout: TapRidePassGeometry): Pose {
        if (loopTime >= 8.3 || loopTime < 1.2) return Pose(layout.homeX, layout.homeY)
        val approachX = layout.readerX + 26
        val approachY = layout.readerY + 88
        var cardX = layout.homeX
        var cardY = layout.homeY
        if (loopTime >= 1.45 && loopTime < 2.8) {
            val u = easeInOut(segment(loopTime, 1.45, 2.8))
            cardX = bezier(layout.homeX, layout.homeX - 40, approachX + 60, approachX, u)
            cardY = bezier(layout.homeY, layout.homeY - 110, approachY + 90, approachY, u)
        } else if (loopTime >= 2.8) {
            val k = springStep(loopTime - 2.8, 0.3, 0.66)
            val dockedX = approachX + (layout.readerX - approachX) * k
            val dockedY = approachY + (layout.readerY - approachY) * k
            val returnProgress = springStep(loopTime - 3.12 - 0.43, 0.5, 0.8)
            cardX = dockedX + (layout.homeX - dockedX) * returnProgress
            cardY = dockedY + (layout.homeY - dockedY) * returnProgress
        }
        val lift =
            easeOut(segment(loopTime, 1.2, 1.45)) * (1 - easeInOut(segment(loopTime, 2.8, 3.1)))
        val timeAfterTap = loopTime - 3.12
        return Pose(
            cardX,
            cardY,
            lift,
            ticketAfter(timeAfterTap) * (1 - easeInOut(segment(loopTime, 7.05, 7.8))),
            rollAfter(timeAfterTap) * (1 - segment(loopTime, 7.75, 7.8)),
            timeAfterTap,
        )
    }
}

/**
 * Screen geometry in points/dp, including safe-area insets. Validator feature coordinates originate
 * in its 697 × 1440 px artwork; card art is 1600 × 1009 px. The printed ticket starts at slot
 * width, then expands toward its full-size 430-point/dp height.
 */
internal data class TapRidePassGeometry(
    val width: Double,
    val height: Double,
    val safeTop: Double,
    val safeBottom: Double,
) {
    val validatorScale
        get() = min(width * 0.78, 330.0) / 694

    val validatorWidth
        get() = 697 * validatorScale

    val validatorHeight
        get() = 1440 * validatorScale

    val validatorX
        get() = (width - validatorWidth) / 2

    val validatorY
        get() = height * 0.3 - 2 * validatorScale

    val readerX
        get() = validatorX + 346 * validatorScale

    val readerEllipseY
        get() = validatorY + 206 * validatorScale

    val readerY
        get() = readerEllipseY - 2

    val readerRadiusX
        get() = 166 * validatorScale

    val readerRadiusY
        get() = 105 * validatorScale

    val slotY
        get() = validatorY + 380 * validatorScale

    val slotWidth
        get() = 399 * validatorScale

    val cardWidth
        get() = min(width - 32, 372.0)

    val cardHeight
        get() = cardWidth * 1009 / 1600

    val homeX
        get() = width / 2

    val homeY
        get() = height - safeBottom - 18 - cardHeight / 2

    val dockScale
        get() = readerRadiusX * 2 * 1.22 / cardWidth

    val homeDistance
        get() = hypot(homeX - readerX, homeY - readerY)

    val ticketWidth
        get() = min(width - 48, 340.0)

    val ticketHeight = 430.0
    val ticketY
        get() = safeTop + 355

    val printedWidth
        get() = slotWidth * 0.86

    val printedHeight
        get() = printedWidth * ticketHeight / ticketWidth

    val printedExposure
        get() = printedHeight * 0.42
}
