package com.ngocdevv.nativecook.blocks.forms.dialslider.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Arc description of a progress ring: start angle and signed sweep in degrees. */
public data class CircularArc(
    /** Start angle in degrees, where -90 is 12 o'clock. */
    val startAngleDegrees: Double,
    /** Signed sweep in degrees: positive sweeps clockwise, negative counter-clockwise. */
    val sweepAngleDegrees: Double,
)

/**
 * Progress away from the neutral value. Neutral is zero when it is in range,
 * otherwise the nearest range boundary.
 */
public fun getBipolarProgress(value: Double, minValue: Double, maxValue: Double): Double {
    if (!value.isFinite() || !minValue.isFinite() || !maxValue.isFinite()) {
        return 0.0
    }

    val min = min(minValue, maxValue)
    val max = max(minValue, maxValue)
    val clampedValue = min(max, max(min, value))
    val neutral = min(max, max(min, 0.0))

    if (clampedValue >= neutral) {
        val positiveRange = max - neutral
        return if (positiveRange <= 0) {
            0.0
        } else {
            min(max((clampedValue - neutral) / positiveRange, 0.0), 1.0)
        }
    }
    val negativeRange = neutral - min
    return if (negativeRange <= 0) {
        0.0
    } else {
        min(max((neutral - clampedValue) / negativeRange, 0.0), 1.0)
    }
}

public fun <T> getSignedProgressColor(value: Double, positiveColor: T, negativeColor: T): T =
    if (value < 0) negativeColor else positiveColor

private data class Point(val x: Double, val y: Double)

private fun polarPoint(centerX: Double, centerY: Double, radius: Double, angleDegrees: Double): Point {
    val angleRadians = angleDegrees * PI / 180
    return Point(
        x = centerX + radius * cos(angleRadians),
        y = centerY + radius * sin(angleRadians),
    )
}

/**
 * Describes a continuous arc from 12 o'clock. Positive direction sweeps
 * clockwise; negative sweeps counter-clockwise. Returns null when nothing
 * should be drawn.
 */
public fun createCircularArc(radius: Double, progress: Double, direction: Int): CircularArc? {
    if (!radius.isFinite() || !progress.isFinite() || radius <= 0 || progress <= 0) {
        return null
    }
    val clampedProgress = min(progress, 1.0)
    // A 360° arc has identical endpoints; keep it just short of a full circle.
    val sweepDegrees = min(clampedProgress * 360, 359.999)
    return CircularArc(
        startAngleDegrees = -90.0,
        sweepAngleDegrees = if (direction > 0) sweepDegrees else -sweepDegrees,
    )
}

/**
 * Builds a continuous SVG arc from 12 o'clock, mirroring the React Native
 * implementation. Useful for tests and SVG export; Compose rendering uses
 * [createCircularArc].
 */
public fun createCircularArcPath(
    centerX: Double,
    centerY: Double,
    radius: Double,
    progress: Double,
    direction: Int,
): String? {
    if (!centerX.isFinite() || !centerY.isFinite() || !radius.isFinite() ||
        !progress.isFinite() || radius <= 0 || progress <= 0
    ) {
        return null
    }

    val clampedProgress = min(progress, 1.0)
    val sweepDegrees = min(clampedProgress * 360, 359.999)
    val startAngle = -90.0
    val endAngle = startAngle + direction * sweepDegrees
    val start = polarPoint(centerX, centerY, radius, startAngle)
    val end = polarPoint(centerX, centerY, radius, endAngle)
    val largeArcFlag = if (sweepDegrees > 180) 1 else 0
    val sweepFlag = if (direction > 0) 1 else 0

    return "M ${formatSvgNumber(start.x)} ${formatSvgNumber(start.y)} " +
        "A ${formatSvgNumber(radius)} ${formatSvgNumber(radius)} 0 $largeArcFlag $sweepFlag " +
        "${formatSvgNumber(end.x)} ${formatSvgNumber(end.y)}"
}

/** Formats like JavaScript number-to-string for integral values (`25` not `25.0`). */
internal fun formatSvgNumber(value: Double): String {
    if (!value.isFinite()) return value.toString()
    val asLong = value.toLong()
    return if (asLong.toDouble() == value) asLong.toString() else value.toString()
}
