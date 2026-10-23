package com.ngocdevv.nativecook.blocks.cards.grasstimercarousel

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class GrassTimerPhase { Idle, Pressing, Morphing, Active, Returning }

internal enum class GrassTimerCadence { Stopped, Animation, Seconds }

internal data class GrassTimerRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val cornerRadius: Double,
) {
    fun interpolate(to: GrassTimerRect, progress: Double): GrassTimerRect {
        val p = GrassTimerMath.clamp(progress)
        return GrassTimerRect(
            x = x + (to.x - x) * p,
            y = y + (to.y - y) * p,
            width = width + (to.width - width) * p,
            height = height + (to.height - height) * p,
            cornerRadius = cornerRadius + (to.cornerRadius - cornerRadius) * p,
        )
    }
}

internal data class GrassTimerFrame(
    val phase: GrassTimerPhase,
    val selectedCard: Int,
    val aperture: GrassTimerRect,
    val morph: Double,
    val pressProgress: Double,
    val chipScale: Double,
    val imageParallax: Double,
    val secondCardOffset: Double,
    val secondCardOpacity: Double,
    val copyOpacity: Double,
    val timerOpacity: Double,
    val ringOpacity: Double,
    val ringProgress: Double,
    val landingHighlightOpacity: Double,
    val remainingSeconds: Int,
    val activationSequence: Int,
    val carouselOffset: Double,
    val neighborCardX: Double,
    val selectionSequence: Int,
)

internal object GrassTimerTiming {
    const val Loop = 8.0
    const val QuickTapDuration = 0.16
    const val HoldDuration = 0.65
    const val MorphDuration = 0.80
    const val ReturnDuration = 0.75
    const val ReducedFadeDuration = 0.15
    const val GrassDuration = 8 * 60 * 60
    const val QuietDuration = 24 * 60 * 60
    const val CarouselSpacing = 249.0
    const val CarouselSettleDuration = 0.65

    val RestingCard = GrassTimerRect(14.0, 0.0, 232.0, 327.0, 36.0)
    val TimerAperture = GrassTimerRect(50.0, 50.0, 302.0, 302.0, 151.0)
}

internal object GrassTimerMath {
    fun clamp(value: Double): Double = value.coerceIn(0.0, 1.0)

    fun clamp(value: Double, lower: Double, upper: Double): Double = value.coerceIn(lower, upper)

    fun cubicBezier(
        progress: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ): Double {
        val x = clamp(progress)
        var low = 0.0
        var high = 1.0
        repeat(18) {
            val t = (low + high) * 0.5
            if (bezier(t, x1, x2) < x) low = t else high = t
        }
        return bezier((low + high) * 0.5, y1, y2)
    }

    fun strongOut(progress: Double): Double = cubicBezier(progress, 0.23, 1.0, 0.32, 1.0)

    fun strongInOut(progress: Double): Double = cubicBezier(progress, 0.77, 0.0, 0.175, 1.0)

    fun springBack(elapsed: Double, initial: Double): Double {
        if (elapsed <= 0) return initial
        val omega = 15.7
        val damping = 0.8
        val damped = omega * sqrt(1 - damping * damping)
        return max(
            0.0,
            initial * exp(-damping * omega * elapsed) *
                (cos(damped * elapsed) + damping * omega / damped * sin(damped * elapsed)),
        )
    }

    private fun bezier(t: Double, p1: Double, p2: Double): Double {
        val inverse = 1 - t
        return 3 * inverse * inverse * t * p1 + 3 * inverse * t * t * p2 + t * t * t
    }
}
