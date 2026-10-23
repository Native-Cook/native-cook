package com.ngocdevv.nativecook.blocks.gestures.dragmultiselect

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class DragMultiSelectEasing {
    LINEAR,
    QUAD_IN_OUT,
    EASE_OUT,
    EASE_IN_OUT,
}

/** A frame-clock driven value so drawing layers can observe animation without owning coroutines. */
internal class DragMultiSelectAnimatedFloat(initialValue: Float) : State<Float> {
    private val snapshot = mutableFloatStateOf(initialValue)
    private var kind = Kind.IDLE
    private var start = initialValue
    private var target = initialValue
    private var velocity = 0f
    private var elapsed = 0f
    private var duration = 0f
    private var delay = 0f
    private var easing = DragMultiSelectEasing.QUAD_IN_OUT
    private var damping = 0f
    private var stiffness = 0f
    private var mass = 1f
    private var springStartEnergy = 0.0

    override val value: Float
        get() = snapshot.floatValue

    val isRunning: Boolean
        get() = kind != Kind.IDLE || delay > 0f

    fun snapTo(next: Float) {
        snapshot.floatValue = next
        start = next
        target = next
        velocity = 0f
        elapsed = 0f
        duration = 0f
        delay = 0f
        kind = Kind.IDLE
    }

    fun timingTo(
        next: Float,
        durationMillis: Long,
        easing: DragMultiSelectEasing = DragMultiSelectEasing.QUAD_IN_OUT,
        delayMillis: Long = 0L,
    ) {
        start = value
        target = next
        velocity = 0f
        elapsed = 0f
        duration = durationMillis / 1_000f
        delay = delayMillis / 1_000f
        this.easing = easing
        kind = Kind.TIMING
        if (duration <= 0f && delay <= 0f) {
            snapTo(next)
        }
    }

    fun springTo(
        next: Float,
        spring: DragMultiSelectTokens.SpringSpec,
        initialVelocity: Float = velocity,
        delayMillis: Long = 0L,
    ) {
        target = next
        velocity = initialVelocity
        elapsed = 0f
        delay = delayMillis / 1_000f
        damping = spring.damping
        stiffness = spring.stiffness
        mass = spring.mass
        val displacement = (value - next).toDouble()
        springStartEnergy = 0.5 * mass * initialVelocity * initialVelocity +
            0.5 * stiffness * displacement * displacement
        kind = Kind.SPRING
        if (springStartEnergy == 0.0 && delay <= 0f) {
            snapTo(next)
        }
    }

    fun step(deltaSeconds: Float) {
        var delta = deltaSeconds
        if (delay > 0f) {
            val used = minOf(delay, delta)
            delay -= used
            delta -= used
            if (delta <= 0f) {
                return
            }
        }
        when (kind) {
            Kind.IDLE -> Unit
            Kind.TIMING -> stepTiming(delta)
            Kind.SPRING -> stepSpring(delta)
        }
    }

    private fun stepTiming(deltaSeconds: Float) {
        elapsed += deltaSeconds
        val progress = if (duration <= 0f) {
            1f
        } else {
            (elapsed / duration).coerceIn(0f, 1f)
        }
        snapshot.floatValue = start + (target - start) * transform(progress, easing)
        if (progress >= 1f) {
            snapshot.floatValue = target
            kind = Kind.IDLE
        }
    }

    private fun stepSpring(deltaSeconds: Float) {
        val seconds = deltaSeconds.toDouble().coerceAtMost(MAX_SPRING_FRAME_SECONDS)
        val displacement = (value - target).toDouble()
        val speed = velocity.toDouble()
        val massValue = mass.toDouble()
        val dampingValue = damping.toDouble()
        val stiffnessValue = stiffness.toDouble()
        val discriminant = dampingValue * dampingValue - 4.0 * massValue * stiffnessValue
        val next: Double
        val nextVelocity: Double
        if (abs(discriminant) < CRITICAL_EPSILON) {
            val root = -dampingValue / (2.0 * massValue)
            val second = speed - root * displacement
            val decay = exp(root * seconds)
            next = decay * (displacement + second * seconds)
            nextVelocity = decay * (speed + root * second * seconds)
        } else if (discriminant < 0.0) {
            val alpha = dampingValue / (2.0 * massValue)
            val omega = sqrt(stiffnessValue / massValue - alpha * alpha)
            val a = displacement
            val b = (speed + alpha * displacement) / omega
            val sine = sin(omega * seconds)
            val cosine = cos(omega * seconds)
            val decay = exp(-alpha * seconds)
            next = decay * (a * cosine + b * sine)
            nextVelocity = decay * (
                (-alpha * a + b * omega) * cosine +
                    (-alpha * b - a * omega) * sine
                )
        } else {
            val root = sqrt(discriminant)
            val firstRoot = (-dampingValue + root) / (2.0 * massValue)
            val secondRoot = (-dampingValue - root) / (2.0 * massValue)
            val first = (speed - secondRoot * displacement) / (firstRoot - secondRoot)
            val second = displacement - first
            val firstDecay = exp(firstRoot * seconds)
            val secondDecay = exp(secondRoot * seconds)
            next = first * firstDecay + second * secondDecay
            nextVelocity = firstRoot * first * firstDecay + secondRoot * second * secondDecay
        }
        snapshot.floatValue = (target + next).toFloat()
        velocity = nextVelocity.toFloat()
        val energy = 0.5 * massValue * nextVelocity * nextVelocity +
            0.5 * stiffnessValue * next * next
        if (springStartEnergy == 0.0 || energy / springStartEnergy <= SPRING_ENERGY_THRESHOLD) {
            snapshot.floatValue = target
            velocity = 0f
            kind = Kind.IDLE
        }
    }

    private enum class Kind {
        IDLE,
        TIMING,
        SPRING,
    }

    private companion object {
        const val MAX_SPRING_FRAME_SECONDS = 0.064
        const val CRITICAL_EPSILON = 0.0000001
        const val SPRING_ENERGY_THRESHOLD = 0.000000006
    }
}

private fun transform(
    progress: Float,
    easing: DragMultiSelectEasing,
): Float {
    return when (easing) {
        DragMultiSelectEasing.LINEAR -> progress
        DragMultiSelectEasing.QUAD_IN_OUT -> if (progress < 0.5f) {
            2f * progress * progress
        } else {
            1f - (-2f * progress + 2f).pow(2) / 2f
        }
        DragMultiSelectEasing.EASE_OUT -> DragMultiSelectMotion.ease.out.transform(progress)
        DragMultiSelectEasing.EASE_IN_OUT -> DragMultiSelectMotion.ease.inOut.transform(progress)
    }
}
