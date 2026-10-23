package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal const val LOOP = 8.0

internal enum class EventPassPhase { Front, Dragging, Settling, TurningBack, Back, TurningFront }

internal enum class EventPassCadence { Stopped, Animation }

internal data class EventPassFrame(
    val phase: EventPassPhase,
    val flipAngle: Double,
    val tiltX: Double,
    val tiltY: Double,
    val pressScale: Double,
    val frontOpacity: Double,
    val backOpacity: Double,
    val quietCrossfade: Boolean,
    val copyConfirmed: Boolean,
) {
    companion object {
        val Resting = EventPassFrame(
            phase = EventPassPhase.Front,
            flipAngle = 0.0,
            tiltX = 0.0,
            tiltY = 0.0,
            pressScale = 1.0,
            frontOpacity = 1.0,
            backOpacity = 0.0,
            quietCrossfade = false,
            copyConfirmed = false,
        )
    }
}

internal object EventPassTiming {
    const val Loop = LOOP
    const val FlipDuration = 0.75
    const val QuietFadeDuration = 0.15
    const val PressDuration = 0.09
    const val SettleDuration = 0.70
    const val ResumeDelay = 2.0
    const val CopyConfirmationDuration = 1.5
}

internal object EventPassSheetDismiss {
    const val ActiveOffset = 8.0
    const val CommitShare = 0.25
    const val CommitVelocity = 800.0
    const val Rubber = 0.55
    const val RubberFalloff = 120.0
    const val CloseDurationMillis = 300
    const val CloseDampingRatio = 1.0
    const val BackDurationMillis = 400
    const val BackDampingRatio = 0.8
    const val Overshoot = 72.0
}

internal fun eventPassShareRubberBand(over: Double): Double =
    (over * EventPassSheetDismiss.Rubber) / (1 + over / EventPassSheetDismiss.RubberFalloff)

/** The accepted React Native autoplay table, in seconds and degrees. */
internal object EventPassDemo {
    const val FoilStart = 0.8
    const val FoilEnd = 3.0
    const val FoilTiltY = 11.0
    const val FoilTiltX = -5.0
    const val TurnBackStart = 3.3
    const val TurnDuration = 0.75
    const val TurnFrontStart = 5.4
    const val NodStart = 6.15
    const val NodEnd = 6.9
    const val NodTiltY = -4.5
    const val NodTiltX = 1.5
}

internal object EventPassMath {
    fun clamp(value: Double, lower: Double = 0.0, upper: Double = 1.0): Double =
        min(upper, max(lower, value))

    fun lerp(from: Double, to: Double, progress: Double): Double =
        from + (to - from) * clamp(progress)

    fun strongOut(progress: Double): Double = cubicBezier(progress, 0.23, 1.0, 0.32, 1.0)

    fun strongInOut(progress: Double): Double = cubicBezier(progress, 0.77, 0.0, 0.175, 1.0)

    fun spring(
        elapsed: Double,
        position: Double,
        velocity: Double,
        omega: Double = 18.0,
        damping: Double = 0.82,
    ): Double {
        if (elapsed <= 0) return position
        val damped = omega * sqrt(1 - damping * damping)
        val coefficient = (velocity + damping * omega * position) / damped
        return exp(-damping * omega * elapsed) *
            (position * cos(damped * elapsed) + coefficient * sin(damped * elapsed))
    }

    fun pressRelease(elapsed: Double, depth: Double): Double =
        max(0.0, spring(elapsed, depth, 0.0, omega = 17.0, damping = 0.82))

    private fun cubicBezier(
        progress: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ): Double {
        val x = clamp(progress)
        if (x == 0.0 || x == 1.0) return x
        var low = 0.0
        var high = 1.0
        repeat(18) {
            val t = (low + high) * 0.5
            if (bezier(t, x1, x2) < x) low = t else high = t
        }
        return bezier((low + high) * 0.5, y1, y2)
    }

    private fun bezier(t: Double, p1: Double, p2: Double): Double {
        val inverse = 1 - t
        return 3 * inverse * inverse * t * p1 + 3 * inverse * t * t * p2 + t * t * t
    }
}
