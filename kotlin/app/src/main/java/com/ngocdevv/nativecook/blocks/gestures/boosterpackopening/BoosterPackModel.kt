package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.graphics.Path
import kotlin.math.max
import kotlin.math.min

/** A position in the scene, in dp. The whole scene is computed in dp and scaled once when drawn. */
internal class Point(
    @JvmField val x: Double,
    @JvmField val y: Double,
)

internal class Rect(
    @JvmField val x: Double,
    @JvmField val y: Double,
    @JvmField val width: Double,
    @JvmField val height: Double,
)

/** Rest positions of a mesh and the texture coordinate of each vertex, in the same order. */
internal class Mesh(
    @JvmField val vertices: List<Point>,
    @JvmField val textures: List<Point>,
)

/** Builds an `android.graphics.Path` from the scene's double coordinates. */
internal class PathBuilder {
    private val path = Path()

    fun moveTo(x: Double, y: Double): PathBuilder {
        path.moveTo(x.toFloat(), y.toFloat())
        return this
    }

    fun lineTo(x: Double, y: Double): PathBuilder {
        path.lineTo(x.toFloat(), y.toFloat())
        return this
    }

    fun quadTo(x1: Double, y1: Double, x2: Double, y2: Double): PathBuilder {
        path.quadTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())
        return this
    }

    fun cubicTo(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double): PathBuilder {
        path.cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x3.toFloat(), y3.toFloat())
        return this
    }

    fun close(): PathBuilder {
        path.close()
        return this
    }

    fun build(): Path = path
}

internal const val VIDEO_FPS = 60.0
internal const val FLASH_FRAME = 124.0
internal const val FINAL_REFERENCE_FRAME = 184.0
internal const val POST_CUT_DURATION_MS = (FINAL_REFERENCE_FRAME - FLASH_FRAME) / VIDEO_FPS * 1000.0

// Live sessions continue beyond the reference video through card centering.
// Background music runs independently.
internal const val OPENING_DURATION_MS = 2000.0

/** Measurements are normalized to the visible phone viewport in the reference video. */
internal object ReferenceLayout {
    const val WIDTH = 750.0
    const val HEIGHT = 1660.0

    object Pack {
        const val CENTER_X = 0.5
        const val TOP = 0.472
        const val WIDTH = 0.737
        const val SOURCE_ASPECT = 938.0 / 496.0
        const val VIEWPORT_HEIGHT_SCALE = 0.9834
        const val SETTLED_BODY_WIDTH = 0.735
    }

    const val SEAM_Y = 0.572

    object Card {
        const val CENTER_X = 0.5
        const val WIDTH = 0.736
        const val HEIGHT = 0.482
        const val FINAL_TOP = 0.448
    }

    object Trace {
        const val START_X = 0.125
        const val START_Y = 0.568
        const val END_X = 0.765
        const val END_Y = 0.544
        const val TOUCH_RADIUS = 0.067
    }
}

/** Frame of the reference video against the fraction of the pack the finger has crossed. */
internal val TRACE_PROGRESS_KEYFRAMES: Array<DoubleArray> =
    arrayOf(
        doubleArrayOf(13.0, 0.0),
        doubleArrayOf(28.0, 0.06),
        doubleArrayOf(36.0, 0.29),
        doubleArrayOf(42.0, 0.44),
        doubleArrayOf(48.0, 0.52),
        doubleArrayOf(54.0, 0.62),
        doubleArrayOf(60.0, 0.72),
        doubleArrayOf(66.0, 0.81),
        doubleArrayOf(72.0, 0.88),
        doubleArrayOf(78.0, 0.95),
        doubleArrayOf(84.0, 0.98),
        doubleArrayOf(90.0, 1.0),
        doubleArrayOf(123.0, 1.0),
    )

internal class FoilKeyframe(
    @JvmField val ms: Double,
    @JvmField val x: Double,
    @JvmField val y: Double,
    @JvmField val scaleX: Double,
    @JvmField val scaleY: Double,
    @JvmField val rotation: Double,
)

// Values measured from the video. Time is relative to the frame-124 cut flash.
internal val FOIL_KEYFRAMES =
    listOf(
        FoilKeyframe(0.0, 0.5, 0.522, 1.0, 1.0, 0.0),
        FoilKeyframe(66.667, 0.518, 0.508, 0.98, 0.92, 7.6),
        FoilKeyframe(116.667, 0.63, 0.46, 0.846, 0.821, 23.3),
        FoilKeyframe(233.333, 0.697, 0.443, 0.797, 0.686, 23.1),
        FoilKeyframe(333.333, 0.712, 0.444, 0.8, 0.57, 26.7),
        FoilKeyframe(433.333, 0.732, 0.463, 0.77, 0.42, 25.5),
        FoilKeyframe(533.333, 0.752, 0.482, 0.73, 0.36, 24.5),
        FoilKeyframe(633.333, 0.779, 0.515, 0.67, 0.31, 9.2),
        FoilKeyframe(733.333, 0.82, 0.61, 0.46, 0.46, -5.0),
        FoilKeyframe(766.667, 0.84, 0.64, 0.44, 0.5, -5.0),
        FoilKeyframe(833.333, 0.84, 0.69, 0.4, 0.52, -12.0),
        FoilKeyframe(900.0, 0.765, 0.729, 0.36, 0.82, -24.0),
        FoilKeyframe(1000.0, 1.06, 1.16, 0.18, 0.05, -35.0),
    )

internal class BodyKeyframe(
    @JvmField val ms: Double,
    @JvmField val seamY: Double,
    @JvmField val scale: Double,
)

internal val BODY_KEYFRAMES =
    listOf(
        BodyKeyframe(0.0, 0.572, 1.0),
        BodyKeyframe(133.333, 0.573, 1.002),
        BodyKeyframe(333.333, 0.575, 1.019),
        BodyKeyframe(533.333, 0.582, 1.047),
        BodyKeyframe(733.333, 0.595, 1.081),
        BodyKeyframe(833.333, 0.642, 1.1),
        BodyKeyframe(900.0, 0.728, 1.115),
        BodyKeyframe(933.333, 0.8, 1.122),
        BodyKeyframe(966.667, 0.892, 1.136),
        BodyKeyframe(1000.0, 1.0, 1.14),
    )

internal class CardKeyframe(
    @JvmField val ms: Double,
    @JvmField val top: Double,
    @JvmField val opacity: Double,
)

internal val CARD_KEYFRAMES =
    listOf(
        CardKeyframe(833.333, 0.65, 0.0),
        CardKeyframe(850.0, 0.624, 1.0),
        CardKeyframe(866.667, 0.603, 1.0),
        CardKeyframe(883.333, 0.581, 1.0),
        CardKeyframe(900.0, 0.556, 1.0),
        CardKeyframe(916.667, 0.535, 1.0),
        CardKeyframe(933.333, 0.514, 1.0),
        CardKeyframe(950.0, 0.494, 1.0),
        CardKeyframe(966.667, 0.477, 1.0),
        CardKeyframe(983.333, 0.462, 1.0),
        CardKeyframe(1000.0, 0.448, 1.0),
    )

internal fun clamp(value: Double, min: Double, max: Double): Double = min(max, max(min, value))

internal fun interpolateKeyframes(value: Double, input: DoubleArray, output: DoubleArray): Double {
    if (value <= input[0]) return output[0]
    val last = input.size - 1
    if (value >= input[last]) return output[last]

    for (index in 1 until input.size) {
        if (value <= input[index]) {
            val segment = (value - input[index - 1]) / (input[index] - input[index - 1])
            return output[index - 1] + segment * (output[index] - output[index - 1])
        }
    }
    return output[last]
}

private val TRACE_FRAMES = DoubleArray(TRACE_PROGRESS_KEYFRAMES.size) { TRACE_PROGRESS_KEYFRAMES[it][0] }
private val TRACE_PROGRESS = DoubleArray(TRACE_PROGRESS_KEYFRAMES.size) { TRACE_PROGRESS_KEYFRAMES[it][1] }

internal fun traceProgressAtFrame(frame: Double): Double = interpolateKeyframes(frame, TRACE_FRAMES, TRACE_PROGRESS)
