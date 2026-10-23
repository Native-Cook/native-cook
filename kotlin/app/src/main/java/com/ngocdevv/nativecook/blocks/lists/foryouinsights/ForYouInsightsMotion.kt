package com.ngocdevv.nativecook.blocks.lists.foryouinsights

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class ForYouInsightsSpring(
    val response: Double,
    val damping: Double,
    val settle: Double = response * 4.0,
)

data class ForYouInsightsDurationSpring(
    val durationMillis: Int,
    val dampingRatio: Double,
    private val response: Double,
    private val settleSeconds: Double,
) {
    val oscillator = ForYouInsightsSpring(
        response = response,
        damping = dampingRatio,
        settle = settleSeconds,
    )
}

data class ForYouInsightsTipPose(
    val ghost: Double = 1.0,
    val stream: Double = 1.0,
    val caret: Double = 0.0,
    val shimmer: Double = -1.0,
    val active: Boolean = false,
)

data class ForYouInsightsOpenPose(
    val press: Double = 0.0,
    val progress: Double = 0.0,
    val opening: Boolean = true,
    val driftTime: Double = 0.0,
    val drift: Double = 0.0,
)

data class ForYouInsightsLayerShift(
    val dx: Double,
    val dy: Double,
    val scale: Double,
)

object ForYouInsightsMotion {
    const val LOOP = 9.0
    const val RESUME_AFTER = 2.0
    const val PRESS_SCALE = 0.97
    const val PRESS_IN_MS = 120
    const val PRESS_IN_SECONDS = PRESS_IN_MS / 1_000.0

    object Timeline {
        const val DIM_START = 0.5
        const val DIM_END = 0.8
        const val STREAM_START = 0.8
        const val STREAM_END = 2.3
        const val CARET_IN = 0.08
        const val CARET_OUT_START = 2.3
        const val CARET_OUT_END = 2.6
        const val SHIMMER_START = 2.25
        const val SHIMMER_END = 2.9
        const val PRESS_START = 2.95
        const val PRESS_DURATION = 0.12
        const val OPEN_START = 3.07
        const val OPEN_DURATION = 0.85
        const val CLOSE_START = 6.35
        const val CLOSE_DURATION = 0.7
    }

    object Springs {
        val open = ForYouInsightsSpring(response = 0.5, damping = 0.82)
        val close = ForYouInsightsSpring(response = 0.42, damping = 1.0)

        // Reanimated duration springs converted to the same zero-velocity oscillator.
        val touchOpen = ForYouInsightsDurationSpring(
            durationMillis = 650,
            dampingRatio = 0.82,
            response = 0.4091038637449126,
            settleSeconds = 0.975,
        )
        val touchClose = ForYouInsightsDurationSpring(
            durationMillis = 560,
            dampingRatio = 1.0,
            response = 0.4267429458117079,
            settleSeconds = 0.84,
        )
        val tiltBack = ForYouInsightsDurationSpring(
            durationMillis = 500,
            dampingRatio = 0.7,
            response = 0.2698843052331667,
            settleSeconds = 0.75,
        )
        val dragBack = ForYouInsightsDurationSpring(
            durationMillis = 420,
            dampingRatio = 0.8,
            response = 0.2580988056444297,
            settleSeconds = 0.63,
        )
        val lens = ForYouInsightsDurationSpring(
            durationMillis = 460,
            dampingRatio = 0.72,
            response = 0.2551957282933489,
            settleSeconds = 0.69,
        )
    }

    object Stream {
        const val GHOST = 0.16
        const val TOKENS = 18.0
        const val TOKEN_MOVE = 0.72
        const val HEAD = 56.0
        const val CARET_WIDTH = 2.5
        const val CARET_INSET = 4.0
        const val SHIMMER_WIDTH = 140.0
        const val SHIMMER_SKEW = 18.0
    }

    object Depth {
        const val PERSPECTIVE = 900.0
        const val LIFT_SCALE = 0.05
        const val FLIGHT_TILT_X = 9.0
        const val FLIGHT_TILT_Y = -11.0
        const val DRIFT_PERIOD = 2.6
        const val DRIFT_TILT_Y = 7.0
        const val DRIFT_TILT_X = 4.0
        const val DRIFT_RAMP_IN = 0.55
        const val DRIFT_RAMP_OUT = 0.6
        const val PARALLAX = 0.05
        const val DEPTH_SCALE = 0.03
        const val DEPTH_LIFT = 5.0
        const val OVERSCAN = 0.1
        const val HERO_SHADOW_LEVEL = 0.35
        const val SHADOW_OPACITY = 0.26
        const val SHADOW_OFFSET_MAX = 20.0
        const val SHEEN_OPACITY = 0.22
        const val TOUCH_TILT_Y = 12.0
        const val TOUCH_TILT_X = 9.0
        const val TOUCH_TILT_RANGE = 140.0
    }

    object Reader {
        val BACKDROP = 0.08 to 0.7
        const val FEED_SCALE = 0.94
        val ROW_TITLE_OUT = 0.0 to 0.3
        const val ROW_TITLE_RISE = 10.0
        val TITLE_IN = 0.42 to 0.95
        const val TITLE_RISE = 28.0
        const val TITLE_SCALE_FROM = 0.96
        val BODY_IN = 0.45 to 0.73
        const val BODY_STAGGER = 0.05
        const val BODY_RISE = 22.0
        val CLOSE_IN = 0.7 to 1.0
        const val CLOSE_SCALE_FROM = 0.86
        val TAB_OUT = 0.0 to 0.55
        const val TAB_DROP = 120.0
        const val TAB_HIDDEN = 0.6
        const val HAND_OFF = 0.03
    }

    object Dismiss {
        const val RESISTANCE = 0.55
        const val SOFTNESS = 420.0
        const val DISTANCE = 120.0
        const val VELOCITY = 900.0
        const val SCALE_LOSS = 0.12
    }

    fun clamp01(value: Double): Double = min(1.0, max(0.0, value))

    fun seg(time: Double, start: Double, end: Double): Double {
        return clamp01((time - start) / max(0.0001, end - start))
    }

    fun lerp(start: Double, end: Double, progress: Double): Double {
        return start + (end - start) * progress
    }

    fun smooth(progress: Double): Double {
        val value = clamp01(progress)
        return value * value * (3.0 - 2.0 * value)
    }

    private fun bezier(time: Double, point1: Double, point2: Double): Double {
        val inverse = 1.0 - time
        return 3.0 * inverse * inverse * time * point1 +
            3.0 * inverse * time * time * point2 +
            time * time * time
    }

    private fun cubic(
        progress: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ): Double {
        val value = clamp01(progress)
        if (value == 0.0 || value == 1.0) {
            return value
        }
        var low = 0.0
        var high = 1.0
        repeat(20) {
            val middle = (low + high) / 2.0
            if (bezier(middle, x1, x2) < value) {
                low = middle
            } else {
                high = middle
            }
        }
        return bezier((low + high) / 2.0, y1, y2)
    }

    fun easeOut(progress: Double): Double = cubic(progress, 0.23, 1.0, 0.32, 1.0)

    fun easeInOut(progress: Double): Double = cubic(progress, 0.77, 0.0, 0.175, 1.0)

    fun inOutQuad(progress: Double): Double {
        val value = clamp01(progress)
        return if (value < 0.5) {
            2.0 * value * value
        } else {
            1.0 - 2.0 * (1.0 - value) * (1.0 - value)
        }
    }

    fun spring(elapsed: Double, spring: ForYouInsightsSpring): Double {
        if (elapsed <= 0.0) {
            return 0.0
        }
        val angularFrequency = 2.0 * PI / spring.response
        val damping = spring.damping
        if (damping >= 1.0) {
            return 1.0 - exp(-angularFrequency * elapsed) * (1.0 + angularFrequency * elapsed)
        }
        val dampedFrequency = angularFrequency * sqrt(1.0 - damping * damping)
        return 1.0 - exp(-damping * angularFrequency * elapsed) *
            (cos(dampedFrequency * elapsed) +
                (damping * angularFrequency / dampedFrequency) * sin(dampedFrequency * elapsed))
    }

    fun streamProgress(progress: Double): Double {
        val value = clamp01(progress)
        if (value >= 1.0) {
            return 1.0
        }
        val token = floor(value * Stream.TOKENS)
        val withinToken = value * Stream.TOKENS - token
        return (token + smooth(withinToken / Stream.TOKEN_MOVE)) / Stream.TOKENS
    }

    fun streamHeads(progress: Double, widths: List<Double>): List<Double> {
        val total = widths.sum() + Stream.HEAD
        val position = clamp01(progress) * total
        var start = 0.0
        return widths.map { width ->
            val head = min(width + Stream.HEAD, max(0.0, position - start))
            start += width
            head
        }
    }

    fun tipPose(elapsed: Double): ForYouInsightsTipPose {
        val dimLength = Timeline.DIM_END - Timeline.DIM_START
        val streamLength = Timeline.STREAM_END - Timeline.STREAM_START
        if (elapsed < 0.0 || elapsed >= Timeline.SHIMMER_END - Timeline.DIM_START) {
            return ForYouInsightsTipPose()
        }
        val streaming = elapsed >= dimLength
        val ghost = if (elapsed >= Timeline.STREAM_END - Timeline.DIM_START) {
            1.0
        } else {
            lerp(1.0, Stream.GHOST, easeInOut(elapsed / dimLength))
        }
        val stream = if (streaming) {
            streamProgress((elapsed - dimLength) / streamLength)
        } else {
            0.0
        }
        val caretIn = seg(elapsed, dimLength - Timeline.CARET_IN, dimLength)
        val caretOut = 1.0 - seg(
            elapsed,
            Timeline.CARET_OUT_START - Timeline.DIM_START,
            Timeline.CARET_OUT_END - Timeline.DIM_START,
        )
        val shimmer = seg(
            elapsed,
            Timeline.SHIMMER_START - Timeline.DIM_START,
            Timeline.SHIMMER_END - Timeline.DIM_START,
        )
        return ForYouInsightsTipPose(
            ghost = ghost,
            stream = stream,
            caret = smooth(caretIn) * smooth(caretOut),
            shimmer = if (shimmer > 0.0 && shimmer < 1.0) easeInOut(shimmer) else -1.0,
            active = true,
        )
    }

    fun openPose(time: Double): ForYouInsightsOpenPose {
        val press = if (time >= Timeline.PRESS_START && time < Timeline.OPEN_START) {
            easeOut(seg(time, Timeline.PRESS_START, Timeline.PRESS_START + Timeline.PRESS_DURATION))
        } else {
            0.0
        }
        var progress = 0.0
        var opening = true
        when {
            time >= Timeline.CLOSE_START + Timeline.CLOSE_DURATION -> progress = 0.0
            time >= Timeline.CLOSE_START -> {
                progress = 1.0 - spring(time - Timeline.CLOSE_START, Springs.close)
                opening = false
            }
            time >= Timeline.OPEN_START + Timeline.OPEN_DURATION -> progress = 1.0
            time >= Timeline.OPEN_START -> progress = spring(time - Timeline.OPEN_START, Springs.open)
        }
        val holdStart = Timeline.OPEN_START + Timeline.OPEN_DURATION * 0.55
        val hold = Timeline.CLOSE_START - holdStart
        val elapsed = time - holdStart
        val drift = if (elapsed > 0.0 && elapsed < hold) {
            smooth(elapsed / Depth.DRIFT_RAMP_IN) *
                (1.0 - smooth((elapsed - (hold - Depth.DRIFT_RAMP_OUT)) / Depth.DRIFT_RAMP_OUT))
        } else {
            0.0
        }
        return ForYouInsightsOpenPose(
            press = press,
            progress = max(0.0, progress),
            opening = opening,
            driftTime = max(0.0, elapsed),
            drift = drift,
        )
    }

    fun liftOf(progress: Double): Double {
        val value = clamp01(progress)
        return sin(PI * min(1.0, value)) * (1.0 - Depth.HERO_SHADOW_LEVEL) +
            value * Depth.HERO_SHADOW_LEVEL
    }

    fun tiltOf(progress: Double, driftTime: Double, drift: Double): Pair<Double, Double> {
        val flight = sin(PI * clamp01(progress))
        val phase = 2.0 * PI * driftTime / Depth.DRIFT_PERIOD
        val x = flight * Depth.FLIGHT_TILT_X + sin(phase * 2.0) * Depth.DRIFT_TILT_X * drift
        val y = flight * Depth.FLIGHT_TILT_Y + sin(phase) * Depth.DRIFT_TILT_Y * drift
        return x to y
    }

    fun separationOf(progress: Double): Double = smooth(seg(progress, 0.12, 0.85))

    fun layerShift(
        depth: Double,
        separation: Double,
        tiltX: Double,
        tiltY: Double,
        width: Double,
    ): ForYouInsightsLayerShift {
        val normalizedY = tiltY / Depth.DRIFT_TILT_Y
        val normalizedX = tiltX / Depth.DRIFT_TILT_X
        return ForYouInsightsLayerShift(
            dx = -depth * normalizedY * width * Depth.PARALLAX * 0.5 * separation,
            dy = depth * normalizedX * width * Depth.PARALLAX * 0.35 * separation -
                depth * Depth.DEPTH_LIFT * separation,
            scale = 1.0 + depth * Depth.DEPTH_SCALE * separation,
        )
    }

    fun rubber(distance: Double): Double {
        val down = max(0.0, distance)
        return down * Dismiss.RESISTANCE / (1.0 + down / Dismiss.SOFTNESS)
    }

    fun pose(time: Double): Pair<ForYouInsightsTipPose, ForYouInsightsOpenPose> {
        val remainder = time % LOOP
        val loopTime = if (remainder >= 0.0) remainder else remainder + LOOP
        return tipPose(loopTime - Timeline.DIM_START) to openPose(loopTime)
    }
}
