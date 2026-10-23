package com.ngocdevv.nativecook.blocks.progress.healthagedial

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal const val LOOP: Double = HEALTH_AGE_DIAL_LOOP
internal const val RESUME_AFTER: Double = 2.0
internal const val RETURN_DURATION: Double = 0.8

internal data class HealthAgeDialSpring(
    val omega: Double,
    val zeta: Double,
)

internal data class HealthAgeDialPop(
    val scale: Double,
    val opacity: Double,
)

internal data class HealthAgeDialLoopFrame(
    val scroll: Double,
    val years: Double,
    val velocity: Double,
    val reveal: Double,
    val revealOpacity: Double,
)

internal object HealthAgeDialMotion {
    const val loop: Double = LOOP
    const val resumeAfter: Double = RESUME_AFTER
    const val returnDuration: Double = RETURN_DURATION

    object Time {
        const val retractStart: Double = 1.6
        const val retractEnd: Double = 2.15
        const val rewindStart: Double = 2.35
        const val scrollDownStart: Double = 3.9
        const val scrollDownEnd: Double = 5.3
        const val revealStart: Double = 5.0
        const val scrollUpStart: Double = 7.2
        const val scrollUpEnd: Double = 8.6
        const val revealFadeStart: Double = 8.1
        const val revealFadeEnd: Double = 8.5
    }

    object Spring {
        val dot = HealthAgeDialSpring(omega = 11.0, zeta = 0.85)
        val pop = HealthAgeDialSpring(omega = 16.0, zeta = 0.55)
    }

    object Springs {
        val dot = Spring.dot
        val pop = Spring.pop
    }

    object Trail {
        const val seconds: Double = 0.14
        const val maximum: Double = 64.0
        const val max: Double = maximum
        const val fade: Double = 0.47
    }

    object Reveal {
        const val line: Double = 0.9
        const val guidesAt: Double = 0.65
        const val guides: Double = 0.3
        val countAt = listOf(0.25, 0.31)
        const val count: Double = 0.8
        val badgeAt = listOf(0.25, 0.29)
        const val fade: Double = 0.25
    }

    object Land {
        const val amplitude: Double = 0.1
        const val amp: Double = amplitude
        const val decay: Double = 6.0
        const val frequency: Double = 14.0
        const val freq: Double = frequency
        const val peak: Double = 0.5577
    }

    object Flight {
        const val numberFadeEnd: Double = 0.32
        const val crossfadeStart: Double = 0.86
    }

    object Roll {
        const val window: Double = 0.36
        const val travel: Double = 0.55
        const val falloff: Double = 1.9
    }

    object Sign {
        const val from: Double = 0.03
        const val to: Double = 0.06
    }

    object Press {
        const val scale: Double = 0.97
        const val pressIn: Double = 0.1
        const val release: Double = 0.2
    }

    val T get() = Time
    val SPRINGS get() = Springs
    val TRAIL get() = Trail
    val REVEAL get() = Reveal
    val LAND get() = Land
    val FLIGHT get() = Flight
    val ROLL get() = Roll
    val SIGN get() = Sign
    val PRESS get() = Press

    fun clamp01(value: Double): Double {
        return when {
            value < 0.0 -> 0.0
            value > 1.0 -> 1.0
            else -> value
        }
    }

    fun seg(time: Double, from: Double, to: Double): Double {
        return clamp01((time - from) / (to - from))
    }

    fun lerp(from: Double, to: Double, progress: Double): Double {
        return from + (to - from) * progress
    }

    fun smoothstep(progress: Double): Double {
        val value = clamp01(progress)
        return value * value * (3.0 - 2.0 * value)
    }

    /** Column position with a crisp step: integer plus an eased fraction. */
    fun rollStep(value: Double): Double {
        val integer = kotlin.math.floor(value)
        val fraction = value - integer
        return integer + smoothstep((fraction - 0.5 + Roll.window / 2.0) / Roll.window)
    }

    /** Solve a cubic-bezier easing for x, then sample y with twenty bisection iterations. */
    fun bezier(progress: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val x = clamp01(progress)
        if (x == 0.0 || x == 1.0) {
            return x
        }
        var low = 0.0
        var high = 1.0
        for (iteration in 0 until 20) {
            val parameter = (low + high) / 2.0
            val inverse = 1.0 - parameter
            val at = 3.0 * inverse * inverse * parameter * x1 +
                3.0 * inverse * parameter * parameter * x2 +
                parameter * parameter * parameter
            if (at < x) {
                low = parameter
            } else {
                high = parameter
            }
        }
        val parameter = (low + high) / 2.0
        val inverse = 1.0 - parameter
        return 3.0 * inverse * inverse * parameter * y1 +
            3.0 * inverse * parameter * parameter * y2 +
            parameter * parameter * parameter
    }

    fun easeOut(progress: Double): Double {
        return bezier(progress, 0.23, 1.0, 0.32, 1.0)
    }

    fun easeInOut(progress: Double): Double {
        return bezier(progress, 0.77, 0.0, 0.175, 1.0)
    }

    /** Damped displacement released at [displacement] with [velocity], after [tau] seconds. */
    fun springDisplacement(
        tau: Double,
        displacement: Double,
        velocity: Double,
        spring: HealthAgeDialSpring,
    ): Double {
        if (tau <= 0.0) {
            return displacement
        }
        val damped = spring.omega * sqrt(1.0 - spring.zeta * spring.zeta)
        val envelope = exp(-spring.zeta * spring.omega * tau)
        if (envelope < 1e-5) {
            return 0.0
        }
        val sine = (velocity + spring.zeta * spring.omega * displacement) / damped
        return envelope * (displacement * cos(damped * tau) + sine * sin(damped * tau))
    }

    /** A spring from zero to one that starts at rest. */
    fun spring(tau: Double, spring: HealthAgeDialSpring): Double {
        if (tau <= 0.0) {
            return 0.0
        }
        return 1.0 - springDisplacement(tau, 1.0, 0.0, spring)
    }

    /** Badge pop: scale 0.85 to 1 on a playful spring, opacity in 150 ms. */
    fun pop(tau: Double): HealthAgeDialPop {
        if (tau <= 0.0) {
            return HealthAgeDialPop(scale = 0.85, opacity = 0.0)
        }
        return HealthAgeDialPop(
            scale = 0.85 + 0.15 * spring(tau, Spring.pop),
            opacity = easeOut(tau / 0.15),
        )
    }

    /** The chip ring's bump when the dial lands: zero to one to zero. */
    fun landBump(tau: Double): Double {
        if (tau <= 0.0 || tau > 1.2) {
            return 0.0
        }
        return exp(-Land.decay * tau) * sin(Land.frequency * tau) / Land.peak
    }

    /** Scroll offset of the loop towards [historyOffset]. */
    fun scrollAt(time: Double, historyOffset: Double): Double {
        if (time < Time.scrollDownStart) {
            return 0.0
        }
        if (time < Time.scrollDownEnd) {
            return historyOffset * easeInOut(seg(time, Time.scrollDownStart, Time.scrollDownEnd))
        }
        if (time < Time.scrollUpStart) {
            return historyOffset
        }
        if (time < Time.scrollUpEnd) {
            return historyOffset * (1.0 - easeInOut(seg(time, Time.scrollUpStart, Time.scrollUpEnd)))
        }
        return 0.0
    }

    /** Years shown by the dial at loop time [time], where zero is the actual-age tick. */
    fun yearsAt(time: Double, rest: Double): Double {
        if (time < Time.retractStart) {
            return rest
        }
        if (time < Time.rewindStart) {
            return rest * (1.0 - easeInOut(seg(time, Time.retractStart, Time.retractEnd)))
        }
        return rest * spring(time - Time.rewindStart, Spring.dot)
    }

    /** Comet trail length in degrees; a positive trail runs clockwise from the dot. */
    fun trailFor(velocityDegreesPerSecond: Double): Double {
        val length = min(Trail.maximum, abs(velocityDegreesPerSecond) * Trail.seconds)
        return if (velocityDegreesPerSecond > 0.0) -length else length
    }

    /** Loop time at which the scroll crosses [offset] on the way down. */
    fun landingTime(offset: Double, historyOffset: Double): Double {
        var low = Time.scrollDownStart
        var high = Time.scrollDownEnd
        for (iteration in 0 until 40) {
            val middle = (low + high) / 2.0
            if (scrollAt(middle, historyOffset) < offset) {
                low = middle
            } else {
                high = middle
            }
        }
        return (low + high) / 2.0
    }

    fun loopFrame(
        time: Double,
        rest: Double = REST_YEARS,
        degreesPerYear: Double = HealthAgeDialLayout.Dial.degreesPerYear.toDouble(),
        historyOffset: Double = HealthAgeDialLayout.historyOffset.toDouble(),
    ): HealthAgeDialLoopFrame {
        val delta = 1.0 / 240.0
        val years = yearsAt(time, rest)
        val previous = yearsAt(max(0.0, time - delta), rest)
        val velocity = ((years - previous) / delta) * degreesPerYear
        val reveal = if (time >= Time.revealStart && time < Time.scrollUpEnd) {
            time - Time.revealStart
        } else {
            -1.0
        }
        val revealOpacity = if (time < Time.scrollUpEnd) {
            1.0 - seg(time, Time.revealFadeStart, Time.revealFadeEnd)
        } else {
            1.0
        }
        return HealthAgeDialLoopFrame(
            scroll = scrollAt(time, historyOffset),
            years = years,
            velocity = velocity,
            reveal = reveal,
            revealOpacity = revealOpacity,
        )
    }
}

internal fun clamp01(value: Double): Double = HealthAgeDialMotion.clamp01(value)

internal fun seg(time: Double, from: Double, to: Double): Double = HealthAgeDialMotion.seg(time, from, to)

internal fun lerp(from: Double, to: Double, progress: Double): Double = HealthAgeDialMotion.lerp(from, to, progress)

internal fun smoothstep(progress: Double): Double = HealthAgeDialMotion.smoothstep(progress)

internal fun easeOut(progress: Double): Double = HealthAgeDialMotion.easeOut(progress)

internal fun easeInOut(progress: Double): Double = HealthAgeDialMotion.easeInOut(progress)

internal fun springDisplacement(
    tau: Double,
    displacement: Double,
    velocity: Double,
    spring: HealthAgeDialSpring,
): Double = HealthAgeDialMotion.springDisplacement(tau, displacement, velocity, spring)

internal fun trailFor(velocityDegreesPerSecond: Double): Double = HealthAgeDialMotion.trailFor(velocityDegreesPerSecond)

internal fun landingTime(offset: Double, historyOffset: Double): Double = HealthAgeDialMotion.landingTime(offset, historyOffset)

internal fun landBump(tau: Double): Double = HealthAgeDialMotion.landBump(tau)

internal fun pop(tau: Double): HealthAgeDialPop = HealthAgeDialMotion.pop(tau)

internal fun rollStep(value: Double): Double = HealthAgeDialMotion.rollStep(value)
