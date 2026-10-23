package com.ngocdevv.nativecook.blocks.forms.dialslider.camerazoom

import com.ngocdevv.nativecook.blocks.forms.dialslider.core.jsRound
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

public data class CameraZoomRange(val min: Double, val max: Double)

/**
 * Six-value affine matrix `[a, b, c, d, tx, ty]` in SVG order, equivalent to
 * `android.graphics.Matrix` values `[a, c, tx, b, d, ty, 0, 0, 1]`.
 */
public typealias CameraZoomRotationMatrix = DoubleArray

public object CameraZoomDefaults {
    public const val MIN: Double = 0.5
    public const val MAX: Double = 10.0
    public const val STEP: Double = 0.1
    public const val DEGREES_PER_OCTAVE: Double = 20.0
    public const val MAX_TICK_COUNT: Int = 240
}

private const val MAX_ZOOM_FACTOR = 1_000.0
private const val FLOAT_PRECISION = 1_000_000.0

private fun clampFinitePositive(value: Double, fallback: Double): Double {
    val resolved = if (value.isFinite() && value > 0) value else fallback
    val clamped = min(MAX_ZOOM_FACTOR, max(1 / FLOAT_PRECISION, resolved))
    return jsRound(clamped * FLOAT_PRECISION) / FLOAT_PRECISION
}

public fun normalizeCameraZoomRange(
    minZoom: Double = CameraZoomDefaults.MIN,
    maxZoom: Double = CameraZoomDefaults.MAX,
): CameraZoomRange {
    val first = clampFinitePositive(minZoom, CameraZoomDefaults.MIN)
    val second = clampFinitePositive(maxZoom, CameraZoomDefaults.MAX)
    val min = min(first, second)
    val max = max(first, second)

    if (min == max) {
        return CameraZoomRange(min = CameraZoomDefaults.MIN, max = CameraZoomDefaults.MAX)
    }
    return CameraZoomRange(min = min, max = max)
}

public fun getSafeCameraZoomStep(step: Double): Double =
    clampFinitePositive(step, CameraZoomDefaults.STEP)

private fun getCameraZoomBounds(minZoom: Double, maxZoom: Double): CameraZoomRange {
    val first = clampFinitePositive(minZoom, CameraZoomDefaults.MIN)
    val second = clampFinitePositive(maxZoom, CameraZoomDefaults.MAX)
    return CameraZoomRange(min = min(first, second), max = max(first, second))
}

public fun clampCameraZoom(zoom: Double, minZoom: Double, maxZoom: Double): Double {
    val (min, max) = getCameraZoomBounds(minZoom, maxZoom)
    val safeZoom = if (zoom.isFinite() && zoom > 0) zoom else min
    return min(max, max(min, safeZoom))
}

public fun roundCameraZoom(zoom: Double, step: Double, minZoom: Double, maxZoom: Double): Double {
    val safeStep = getSafeCameraZoomStep(step)
    val (min, max) = getCameraZoomBounds(minZoom, maxZoom)
    val clamped = clampCameraZoom(zoom, minZoom, maxZoom)
    val rounded = if (clamped == min || clamped == max) {
        clamped
    } else {
        jsRound(clamped / safeStep) * safeStep
    }
    return jsRound(clampCameraZoom(rounded, minZoom, maxZoom) * FLOAT_PRECISION) / FLOAT_PRECISION
}

public fun cameraZoomToLogPosition(zoom: Double): Double {
    val safeZoom = if (zoom.isFinite() && zoom > 0) zoom else CameraZoomDefaults.MIN
    return log2(safeZoom)
}

public fun cameraLogPositionToZoom(position: Double): Double =
    2.0.pow(if (position.isFinite()) position else 0.0)

/**
 * The reference video spaces each doubling (0.5→1→2) by approximately 20°.
 */
public fun cameraZoomToAngle(
    zoom: Double,
    selectedZoom: Double,
    degreesPerOctave: Double = CameraZoomDefaults.DEGREES_PER_OCTAVE,
): Double {
    val safeDegrees = if (degreesPerOctave.isFinite() && degreesPerOctave > 0) {
        degreesPerOctave
    } else {
        CameraZoomDefaults.DEGREES_PER_OCTAVE
    }
    return (cameraZoomToLogPosition(zoom) - cameraZoomToLogPosition(selectedZoom)) * safeDegrees
}

/** Builds the SVG-style affine matrix for a rotation around a fixed point. */
public fun cameraZoomRotationMatrix(
    angleDegrees: Double,
    centerX: Double,
    centerY: Double = centerX,
): CameraZoomRotationMatrix {
    val safeAngle = if (angleDegrees.isFinite()) angleDegrees else 0.0
    val safeCenterX = if (centerX.isFinite()) centerX else 0.0
    val safeCenterY = if (centerY.isFinite()) centerY else 0.0
    val radians = safeAngle * PI / 180
    val cosine = cos(radians)
    val sine = sin(radians)

    return doubleArrayOf(
        cosine,
        sine,
        -sine,
        cosine,
        safeCenterX - safeCenterX * cosine + safeCenterY * sine,
        safeCenterY - safeCenterX * sine - safeCenterY * cosine,
    )
}

/** Returns one half of an isosceles triangle's base for its tip angle. */
public fun getCameraZoomTriangleHalfWidth(height: Double, apexAngleDegrees: Double): Double {
    val safeHeight = if (height.isFinite()) max(0.0, height) else 0.0
    val safeAngle = if (apexAngleDegrees.isFinite() && apexAngleDegrees > 0 && apexAngleDegrees < 180) {
        apexAngleDegrees
    } else {
        90.0
    }
    return safeHeight * tan(safeAngle * PI / 360)
}

/** Receives path commands so one geometry routine can feed SVG strings and Compose paths. */
public interface CameraZoomPathSink {
    public fun moveTo(x: Double, y: Double)
    public fun lineTo(x: Double, y: Double)
    public fun quadraticTo(controlX: Double, controlY: Double, x: Double, y: Double)
    public fun close()
}

/** Emits a closed triangle with a quadratic curve at every corner into [sink]. */
public fun buildCameraZoomRoundedTriangle(
    centerX: Double,
    topY: Double,
    halfWidth: Double,
    apexY: Double,
    cornerRadius: Double,
    sink: CameraZoomPathSink,
) {
    val safeCenterX = if (centerX.isFinite()) centerX else 0.0
    val safeTopY = if (topY.isFinite()) topY else 0.0
    val safeHalfWidth = if (halfWidth.isFinite()) max(0.0, halfWidth) else 0.0
    val safeApexY = if (apexY.isFinite()) max(safeTopY, apexY) else safeTopY
    val height = safeApexY - safeTopY
    val leftX = safeCenterX - safeHalfWidth
    val rightX = safeCenterX + safeHalfWidth
    val requestedRadius = if (cornerRadius.isFinite()) max(0.0, cornerRadius) else 0.0

    fun sharp() {
        sink.moveTo(leftX, safeTopY)
        sink.lineTo(rightX, safeTopY)
        sink.lineTo(safeCenterX, safeApexY)
        sink.close()
    }

    if (requestedRadius == 0.0 || safeHalfWidth == 0.0 || height == 0.0) {
        sharp()
        return
    }

    val sideLength = hypot(safeHalfWidth, height)
    val baseAngle = atan2(height, safeHalfWidth)
    val apexAngle = PI - 2 * baseAngle
    val baseTangent = tan(baseAngle / 2)
    val apexTangent = tan(apexAngle / 2)

    if (baseTangent <= 0 || apexTangent <= 0) {
        sharp()
        return
    }

    val maxBaseRadius = safeHalfWidth * baseTangent
    val maxSideRadius = sideLength / (1 / baseTangent + 1 / apexTangent)
    val radius = min(requestedRadius, min(maxBaseRadius, maxSideRadius))
    val baseOffset = radius / baseTangent
    val apexOffset = radius / apexTangent
    val sideUnitX = safeHalfWidth / sideLength
    val sideUnitY = height / sideLength
    val baseSideX = sideUnitX * baseOffset
    val baseSideY = sideUnitY * baseOffset
    val apexSideX = sideUnitX * apexOffset
    val apexSideY = sideUnitY * apexOffset

    sink.moveTo(leftX + baseOffset, safeTopY)
    sink.lineTo(rightX - baseOffset, safeTopY)
    sink.quadraticTo(rightX, safeTopY, rightX - baseSideX, safeTopY + baseSideY)
    sink.lineTo(safeCenterX + apexSideX, safeApexY - apexSideY)
    sink.quadraticTo(safeCenterX, safeApexY, safeCenterX - apexSideX, safeApexY - apexSideY)
    sink.lineTo(leftX + baseSideX, safeTopY + baseSideY)
    sink.quadraticTo(leftX, safeTopY, leftX + baseOffset, safeTopY)
    sink.close()
}

/** Builds a closed triangular SVG path with a quadratic curve at every corner. */
public fun getCameraZoomRoundedTrianglePath(
    centerX: Double,
    topY: Double,
    halfWidth: Double,
    apexY: Double,
    cornerRadius: Double,
): String {
    val commands = ArrayList<String>()
    val sink = object : CameraZoomPathSink {
        override fun moveTo(x: Double, y: Double) {
            commands.add("M ${svgNumber(x)} ${svgNumber(y)}")
        }

        override fun lineTo(x: Double, y: Double) {
            commands.add("L ${svgNumber(x)} ${svgNumber(y)}")
        }

        override fun quadraticTo(controlX: Double, controlY: Double, x: Double, y: Double) {
            commands.add(
                "Q ${svgNumber(controlX)} ${svgNumber(controlY)} ${svgNumber(x)} ${svgNumber(y)}",
            )
        }

        override fun close() {
            commands.add("Z")
        }
    }
    buildCameraZoomRoundedTriangle(centerX, topY, halfWidth, apexY, cornerRadius, sink)
    return commands.joinToString(" ")
}

private fun svgNumber(value: Double): String {
    if (!value.isFinite()) return value.toString()
    val asLong = value.toLong()
    return if (asLong.toDouble() == value) asLong.toString() else value.toString()
}

/** Maps a horizontal drag to the tangential rotation of the circular scale. */
public fun cameraZoomFromTranslation(
    startZoom: Double,
    translationX: Double,
    radius: Double,
    minZoom: Double,
    maxZoom: Double,
    degreesPerOctave: Double = CameraZoomDefaults.DEGREES_PER_OCTAVE,
): Double {
    val safeRadius = if (radius.isFinite() && radius > 0) radius else 1.0
    val safeDegrees = if (degreesPerOctave.isFinite() && degreesPerOctave > 0) {
        degreesPerOctave
    } else {
        CameraZoomDefaults.DEGREES_PER_OCTAVE
    }
    val pointsPerOctave = safeRadius * (safeDegrees * PI / 180)
    val octaveDelta = -(if (translationX.isFinite()) translationX else 0.0) / max(1.0, pointsPerOctave)
    return clampCameraZoom(startZoom * 2.0.pow(octaveDelta), minZoom, maxZoom)
}

public fun buildCameraZoomTicks(
    minZoom: Double,
    maxZoom: Double,
    step: Double,
    maxTickCount: Int = CameraZoomDefaults.MAX_TICK_COUNT,
): List<Double> {
    val (min, max) = normalizeCameraZoomRange(minZoom, maxZoom)
    val baseStep = getSafeCameraZoomStep(step)
    val safeMaxCount = max(2, maxTickCount)
    val estimatedCount = floor((max - min) / baseStep) + 1
    val tickStep = baseStep * max(1.0, ceil(estimatedCount / safeMaxCount))
    val firstAligned = ceil(min / tickStep) * tickStep
    val epsilon = tickStep / FLOAT_PRECISION
    val ticks = ArrayList<Double>()
    ticks.add(min)

    var value = firstAligned
    while (value < max - epsilon) {
        val rounded = jsRound(value * FLOAT_PRECISION) / FLOAT_PRECISION
        if (rounded > min + epsilon) ticks.add(rounded)
        if (ticks.size >= safeMaxCount - 1) break
        value += tickStep
    }

    if (abs(ticks[ticks.size - 1] - max) > epsilon) ticks.add(max)
    return ticks
}

public fun isCameraZoomMajorTick(
    zoom: Double,
    stops: List<CameraZoomStopValue>,
    step: Double,
    minZoom: Double,
    maxZoom: Double,
): Boolean {
    val tolerance = max(getSafeCameraZoomStep(step) / 100, 1e-6)
    return abs(zoom - minZoom) <= tolerance ||
        abs(zoom - maxZoom) <= tolerance ||
        abs(zoom - jsRound(zoom)) <= tolerance ||
        stops.any { stop -> abs(stop.value - zoom) <= tolerance }
}

@Suppress("UNCHECKED_CAST")
public fun <T : CameraZoomStopValue> normalizeCameraZoomStops(
    stops: List<T>,
    minZoom: Double,
    maxZoom: Double,
    step: Double? = null,
): List<T> {
    val (min, max) = normalizeCameraZoomRange(minZoom, maxZoom)
    val seen = HashSet<Long>()
    return stops
        .filter { stop -> stop.value.isFinite() && stop.value >= min && stop.value <= max }
        .map { stop ->
            if (step == null) return@map stop
            val value = roundCameraZoom(stop.value, step, min, max)
            if (value == stop.value) stop else stop.withValue(value) as T
        }
        .filter { stop ->
            val key = jsRound(stop.value * FLOAT_PRECISION).toLong()
            seen.add(key)
        }
        .sortedBy { it.value }
}

public fun <T : CameraZoomStopValue> findCameraZoomStop(zoom: Double, stops: List<T>, step: Double): T? {
    val tolerance = max(getSafeCameraZoomStep(step) / 2, 1e-6)
    return stops.firstOrNull { stop -> abs(stop.value - zoom) <= tolerance }
}

public fun <T : CameraZoomStopValue> findNearestCameraZoomStop(zoom: Double, stops: List<T>): T? {
    val validStops = stops.filter { stop -> stop.value.isFinite() && stop.value > 0 }
    if (validStops.isEmpty()) return null

    val zoomPosition = cameraZoomToLogPosition(zoom)
    return validStops.reduce { nearest, candidate ->
        val nearestDistance = abs(cameraZoomToLogPosition(nearest.value) - zoomPosition)
        val candidateDistance = abs(cameraZoomToLogPosition(candidate.value) - zoomPosition)
        if (candidateDistance < nearestDistance) candidate else nearest
    }
}

/** Mirrors JavaScript `Math.abs(value).toFixed(6).replace(/0+$/, '')` decimal counting. */
private fun getCameraZoomPrecision(value: Double): Int {
    if (!value.isFinite()) return 0
    val source = BigDecimal(abs(value))
        .setScale(6, RoundingMode.HALF_UP)
        .toPlainString()
        .trimEnd('0')
    val decimalIndex = source.indexOf('.')
    return if (decimalIndex == -1) 0 else source.length - decimalIndex - 1
}

/** Mirrors JavaScript `Number(zoom.toFixed(precision)).toString()`. */
public fun formatCameraZoomNumber(zoom: Double, step: Double): String {
    val precision = max(
        getCameraZoomPrecision(getSafeCameraZoomStep(step)),
        getCameraZoomPrecision(zoom),
    )
    if (!zoom.isFinite()) return zoom.toString()
    val rounded = BigDecimal(zoom).setScale(precision, RoundingMode.HALF_UP).stripTrailingZeros()
    val text = rounded.toPlainString()
    return if (text == "-0") "0" else text
}

public fun formatCameraZoomCompactNumber(zoom: Double, step: Double): String {
    val text = formatCameraZoomNumber(zoom, step)
    return if (text.startsWith("0.")) text.substring(1) else text
}

public fun formatCameraZoomValue(zoom: Double, step: Double): String =
    "${formatCameraZoomNumber(zoom, step)}x"
