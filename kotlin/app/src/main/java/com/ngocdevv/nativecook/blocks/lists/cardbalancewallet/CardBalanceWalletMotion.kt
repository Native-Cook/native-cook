package com.ngocdevv.nativecook.blocks.lists.cardbalancewallet

import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal object CardBalanceWalletMotion {
    const val LOOP_MS = 7_000L
    const val LoopSeconds = 7.0
    const val ResumeAfterMs = 3_500L

    const val TiltMax = 6f
    const val Refraction = 0.06f
    const val FollowInMs = 120f
    const val LiftScale = 0.025f
    const val PressLift = -0.6f
    const val PressMs = 180f
    const val PickupResponse = 0.5f
    const val PickupDamping = 0.75f
    const val ReleaseResponse = 0.45f
    const val ReleaseDamping = 0.7f
    const val Specular = 0.35f
    const val SpecularTravel = 0.4f
    const val Shadow = 0.55f
    const val ShadowDrop = 4f
    const val DotStretch = 4f
    const val PressScale = 0.97f

    private const val TouchMs = 600f
    private const val PickupMs = 780f
    private const val TraceFromMs = 800f
    private const val TraceToMs = 5_200f
    private const val PathX = 0.34f
    private const val PathY = 0.22f
    private const val GlowInMs = 1_000f
    private const val GlowOutMs = 3_000f
    private const val GlowMs = 600f
    private const val Glow = 0.25f

    data class Pose(
        val rx: Float = 0f,
        val ry: Float = 0f,
        val lift: Float = 0f,
        val glow: Float = 0f,
    )

    class AnimatedValues {
        val rx = mutableFloatStateOf(0f)
        val ry = mutableFloatStateOf(0f)
        val lift = mutableFloatStateOf(0f)
        val glow = mutableFloatStateOf(0f)

        fun set(pose: Pose) {
            rx.floatValue = pose.rx
            ry.floatValue = pose.ry
            lift.floatValue = pose.lift
            glow.floatValue = pose.glow
        }

        fun pose(): Pose {
            return Pose(rx.floatValue, ry.floatValue, lift.floatValue, glow.floatValue)
        }

        fun reset() {
            set(Pose())
        }
    }

    fun pose(milliseconds: Float, reduced: Boolean): Pose {
        val loop = LOOP_MS.toFloat()
        val time = ((milliseconds % loop) + loop) % loop
        if (reduced) {
            val fadeIn = bezier(0.77f, 0f, 0.175f, 1f, ramp(time, GlowInMs, GlowMs))
            val fadeOut = bezier(0.77f, 0f, 0.175f, 1f, ramp(time, GlowOutMs, GlowMs))
            return Pose(glow = Glow * (fadeIn - fadeOut))
        }
        if (time < TouchMs) return Pose()
        if (time <= TraceToMs) {
            val finger = finger(time)
            val tilt = tilt(finger.first, finger.second)
            return Pose(tilt.first, tilt.second, heldLift(time))
        }
        val elapsed = (time - TraceToMs) / 1_000f
        val end = tiltAtFinger(TraceToMs)
        val before = tiltAtFinger(TraceToMs - 1f)
        val lift = heldLift(TraceToMs)
        return Pose(
            spring(elapsed, end.first, (end.first - before.first) * 1_000f, ReleaseResponse, ReleaseDamping),
            spring(elapsed, end.second, (end.second - before.second) * 1_000f, ReleaseResponse, ReleaseDamping),
            spring(elapsed, lift, 0f, ReleaseResponse, ReleaseDamping),
        )
    }

    fun pickupLift(milliseconds: Float, initial: Float = 0f): Float {
        if (milliseconds < PressMs) {
            val progress = easeOut(milliseconds / PressMs)
            return initial + (PressLift - initial) * progress
        }
        return 1f + spring(
            (milliseconds - PressMs) / 1_000f,
            PressLift - 1f,
            0f,
            PickupResponse,
            PickupDamping,
        )
    }

    fun releasedPose(
        milliseconds: Float,
        initial: Pose,
        velocityX: Float,
        velocityY: Float,
    ): Pose {
        val elapsed = max(0f, milliseconds) / 1_000f
        return Pose(
            spring(elapsed, initial.rx, velocityX, ReleaseResponse, ReleaseDamping),
            spring(elapsed, initial.ry, velocityY, ReleaseResponse, ReleaseDamping),
            spring(elapsed, initial.lift, 0f, ReleaseResponse, ReleaseDamping),
        )
    }

    fun tilt(x: Float, y: Float): Pair<Float, Float> {
        return Pair(-y * 2f * TiltMax, x * 2f * TiltMax)
    }

    fun easeOut(value: Float): Float {
        return bezier(0.23f, 1f, 0.32f, 1f, value)
    }

    fun spring(
        elapsed: Float,
        initial: Float,
        velocity: Float,
        response: Float,
        damping: Float,
    ): Float {
        if (elapsed <= 0f) return initial
        val frequency = (2.0 * PI / response).toFloat()
        val decay = exp(-damping * frequency * elapsed)
        if (damping >= 1f) {
            return decay * (initial + (velocity + frequency * initial) * elapsed)
        }
        val damped = frequency * sqrt(1f - damping * damping)
        return decay * (
            initial * cos(damped * elapsed)
                + ((velocity + damping * frequency * initial) / damped) * sin(damped * elapsed)
            )
    }

    fun bezier(x1: Float, y1: Float, x2: Float, y2: Float, x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var time = x
        repeat(8) {
            val oneMinus = 1f - time
            val error = 3f * x1 * time * oneMinus * oneMinus
                + 3f * x2 * time * time * oneMinus
                + time * time * time - x
            val derivative = 3f * x1 * oneMinus * (1f - 3f * time)
                + 3f * x2 * time * (2f - 3f * time)
                + 3f * time * time
            if (abs(derivative) < 0.000001f) return@repeat
            time = min(1f, max(0f, time - error / derivative))
        }
        val oneMinus = 1f - time
        return 3f * y1 * time * oneMinus * oneMinus
            + 3f * y2 * time * time * oneMinus
            + time * time * time
    }

    private fun ramp(time: Float, start: Float, duration: Float): Float {
        return min(1f, max(0f, (time - start) / duration))
    }

    private fun finger(milliseconds: Float): Pair<Float, Float> {
        if (milliseconds < TraceFromMs) {
            val progress = bezier(0.23f, 1f, 0.32f, 1f, (milliseconds - TouchMs) / PressMs)
            return Pair(-PathX * progress, 0f)
        }
        val progress = (min(milliseconds, TraceToMs) - TraceFromMs) / (TraceToMs - TraceFromMs)
        return Pair(
            -PathX * cos((2.0 * PI * progress).toFloat()),
            -PathY * sin((4.0 * PI * progress).toFloat()),
        )
    }

    private fun tiltAtFinger(milliseconds: Float): Pair<Float, Float> {
        val finger = finger(milliseconds)
        return tilt(finger.first, finger.second)
    }

    private fun heldLift(milliseconds: Float): Float {
        if (milliseconds < PickupMs) {
            return PressLift * bezier(0.23f, 1f, 0.32f, 1f, (milliseconds - TouchMs) / PressMs)
        }
        return 1f + spring(
            (milliseconds - PickupMs) / 1_000f,
            PressLift - 1f,
            0f,
            PickupResponse,
            PickupDamping,
        )
    }
}
