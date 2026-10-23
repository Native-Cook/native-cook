package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal class PackLightingPose(
    @JvmField val originX: Double,
    @JvmField val originY: Double,
    @JvmField val translateX: Double,
    @JvmField val translateY: Double,
    @JvmField val scale: Double,
    /** Rotation in radians, around origin before translation. */
    @JvmField val rotation: Double,
)

internal class MouthLightingInput(
    @JvmField val vertices: List<Point>,
    @JvmField val textures: List<Point>,
    @JvmField val pose: PackLightingPose,
    @JvmField val opening: Double,
    @JvmField val width: Double,
    @JvmField val height: Double,
    @JvmField val reducedMotion: Boolean = false,
)

/** The uniforms of the mouth material, named as the shader names them. */
internal class MouthLighting(
    @JvmField val center: Double,
    @JvmField val secondCenter: Double,
    @JvmField val strength: Double,
    @JvmField val opening: Double,
    @JvmField val travel: Double,
    @JvmField val liningCenter: Double,
    @JvmField val liningLight: Double,
    @JvmField val sideLight: Double,
    @JvmField val liningTilt: Double,
    @JvmField val liningTint: DoubleArray,
    @JvmField val liningSurface: DoubleArray,
)

internal const val MOUTH_LIGHT_SAMPLES = 9

private fun finite(value: Double, fallback: Double = 0.0): Double = if (value.isFinite()) value else fallback

private fun bounded(value: Double, low: Double, high: Double): Double = max(low, min(high, finite(value, low)))

private fun smooth(low: Double, high: Double, value: Double): Double {
    val t = bounded((value - low) / (high - low), 0.0, 1.0)
    return t * t * (3 - 2 * t)
}

private fun unit3(x: Double, y: Double, z: Double): DoubleArray {
    val length = max(sqrt(x * x + y * y + z * z), 0.000001)
    return doubleArrayOf(x / length, y / length, z / length)
}

private fun transform(point: Point, pose: PackLightingPose): Point {
    val ox = finite(pose.originX)
    val oy = finite(pose.originY)
    val x = finite(point.x, ox) - ox
    val y = finite(point.y, oy) - oy
    val rotation = finite(pose.rotation)
    val scale = bounded(pose.scale, 0.0, 100.0)
    val c = cos(rotation)
    val s = sin(rotation)
    return Point(
        ox + scale * (c * x - s * y) + finite(pose.translateX),
        oy + scale * (s * x + c * y) + finite(pose.translateY),
    )
}

// Columns are not evenly spaced: the geometry reserves many columns for
// folded ends. Interpolate using the supplied UVs, including reversed tears.
private fun sampleRow(input: MouthLightingInput, u: Double, row: Int): Point {
    val count = min(input.vertices.size, input.textures.size) / 5
    val fallback = Point(finite(input.pose.originX), finite(input.pose.originY))
    if (count == 0) return fallback
    var nearest = 0
    var nearestDistance = Double.POSITIVE_INFINITY
    for (column in 0 until count) {
        val uv = finite(input.textures[column * 5].x)
        val distance = abs(u - uv)
        if (distance < nearestDistance) {
            nearest = column
            nearestDistance = distance
        }
        if (column + 1 == count) continue
        val nextUV = finite(input.textures[(column + 1) * 5].x)
        if (abs(nextUV - uv) < 0.000001 || u < min(uv, nextUV) || u > max(uv, nextUV)) continue
        val t = (u - uv) / (nextUV - uv)
        val a = input.vertices[column * 5 + row]
        val b = input.vertices[(column + 1) * 5 + row]
        return Point(
            finite(a.x, fallback.x) + (finite(b.x, fallback.x) - finite(a.x, fallback.x)) * t,
            finite(a.y, fallback.y) + (finite(b.y, fallback.y) - finite(a.y, fallback.y)) * t,
        )
    }
    val point = input.vertices[nearest * 5 + row]
    return Point(finite(point.x, fallback.x), finite(point.y, fallback.y))
}

private val MUTED_TINT = doubleArrayOf(0.82, 0.80, 0.93)

/**
 * Deterministic lighting from the raw 56×5 wall and its rendered body pose.
 * The mesh is a 2D projection: opening supplies a calibrated virtual sheet
 * depth to estimate incidence. This is a 2.5D material, not recovered 3D.
 */
internal fun deriveMouthLighting(input: MouthLightingInput): MouthLighting {
    val width = max(finite(input.width, 1.0), 0.001)
    val height = max(finite(input.height, width), 0.001)
    val aspect = height / width
    val aperture = bounded(finite(input.opening) / width, 0.0, 0.25)
    val centers = ArrayList<Point>(MOUTH_LIGHT_SAMPLES)
    val depths = ArrayList<Point>(MOUTH_LIGHT_SAMPLES)
    var localCenterY = 0.0
    for (index in 0 until MOUTH_LIGHT_SAMPLES) {
        val u = index.toDouble() / (MOUTH_LIGHT_SAMPLES - 1)
        val middle = sampleRow(input, u, 2)
        val rear = transform(sampleRow(input, u, 0), input.pose)
        val front = transform(sampleRow(input, u, 4), input.pose)
        centers.add(transform(middle, input.pose))
        depths.add(Point(front.x - rear.x, front.y - rear.y))
        localCenterY += middle.y / 9
    }
    val localStart = sampleRow(input, 0.125, 2)
    val localEnd = sampleRow(input, 0.875, 2)
    val direction = if (localEnd.x >= localStart.x) 1 else -1
    val dx = (centers[7].x - centers[1].x) * direction
    val dy = (centers[7].y - centers[1].y) * direction
    val chordLength = hypot(dx, dy)
    val roll = if (chordLength > width * 0.000001) atan2(dy, dx) else finite(input.pose.rotation)
    val tx = cos(roll)
    val ty = sin(roll)
    val projectedWidth = max(chordLength / 0.75, width * 0.000001)
    val normalDepths = DoubleArray(MOUTH_LIGHT_SAMPLES)
    var meanDepth = 0.0
    var meanShear = 0.0
    var worldX = 0.0
    var worldY = 0.0
    for (index in 0 until MOUTH_LIGHT_SAMPLES) {
        val depth = depths[index]
        val h = bounded((-depth.x * ty + depth.y * tx) / projectedWidth, -0.25, 0.25)
        normalDepths[index] = h
        meanDepth += h / 9
        meanShear += (depth.x * tx + depth.y * ty) / projectedWidth / 9
        worldX += centers[index].x / width / 9
        worldY += centers[index].y / width / 9
    }
    val bowX = centers[4].x - (centers[1].x + centers[7].x) * 0.5
    val bowY = centers[4].y - (centers[1].y + centers[7].y) * 0.5
    val bow = bounded((-bowX * ty + bowY * tx) / projectedWidth, -0.2, 0.2)
    val response = if (input.reducedMotion) 0.55 else 1.0
    val liningSurface = DoubleArray(MOUTH_LIGHT_SAMPLES * 2)
    for (index in 0 until MOUTH_LIGHT_SAMPLES) {
        val a = centers[max(0, index - 1)]
        val b = centers[min(MOUTH_LIGHT_SAMPLES - 1, index + 1)]
        val x = (b.x - a.x) * direction
        val y = (b.y - a.y) * direction
        val angle = if (hypot(x, y) > width * 0.000001) atan2(tx * y - ty * x, tx * x + ty * y) else 0.0
        // Angular displacement is signed in material-U coordinates. Depth is a
        // dimensionless departure from the mean, so uniform scaling cancels out.
        liningSurface[index * 2] = bounded(angle, -0.6, 0.6) * direction * response
        liningSurface[index * 2 + 1] = bounded(normalDepths[index] - meanDepth, -0.12, 0.12) * response
    }

    // Fixed finite camera and softbox positions, in viewport-width units.
    // Translation changes their incident directions; no lamp follows the pack.
    val lightX = 0.50 - worldX
    val lightY = aspect * 0.38 - worldY
    val lightZ = 1.45
    val light = unit3(lightX, lightY, lightZ)
    val view = unit3(0.50 - worldX, aspect * 0.46 - worldY, 2.8)
    val half = unit3(light[0] + view[0], light[1] + view[1], light[2] + view[2])
    val lightPitch = atan2(light[1], light[2])
    val viewPitch = atan2(view[1], view[2])
    val referenceLightPitch = atan2(aspect * (0.38 - 0.58), lightZ)
    val referenceViewPitch = atan2(aspect * (0.46 - 0.58), 2.8)
    val openingAngle = atan2(aperture, 0.058)
    val incidence =
        openingAngle + 0.22 * (lightPitch - referenceLightPitch) +
            0.26 * (viewPitch - referenceViewPitch) + 0.10 * roll + 0.15 * bow
    val normal = unit3(-ty * cos(openingAngle), tx * cos(openingAngle), sin(openingAngle))
    val facing = bounded(normal[0] * half[0] + normal[1] * half[1] + normal[2] * half[2], 0.0, 1.0)
    val referenceDistance = lightZ * lightZ + (aspect * (0.38 - 0.58)).pow(2)
    val attenuation =
        bounded(referenceDistance / (lightX * lightX + lightY * lightY + lightZ * lightZ), 0.22, 1.12)
    val exposed = smooth(0.008, 0.031, aperture)
    val exposure = exposed * bounded(0.75 + facing * 0.50, 0.0, 1.0) * attenuation
    val physicalCenter = bounded(0.5 + 0.45 * atan2(half[0], half[2]) - 0.18 * roll - 0.25 * bow, 0.12, 0.88)
    val liningCenter = if (direction == 1) physicalCenter else 1 - physicalCenter
    val white = smooth(atan2(0.036, 0.058), atan2(0.049, 0.058), incidence)
    val violet = smooth(atan2(0.048, 0.058), atan2(0.055, 0.058), incidence)
    val tint = doubleArrayOf(0.86 + 0.14 * white - 0.08 * violet, 1 - 0.40 * violet, 1.0)
    val liningTint =
        DoubleArray(3) { if (input.reducedMotion) tint[it] * 0.75 + MUTED_TINT[it] * 0.25 else tint[it] }
    val sideAngle = (incidence - atan2(0.040, 0.058)) / 0.068
    val sideLight = exp(-sideAngle * sideAngle) * exposed * attenuation * response
    val secondPhysical = bounded(0.79 + 0.35 * atan2(light[0], light[2]) - 0.10 * roll, 0.08, 0.92)
    val strength = bounded(exposure * (if (input.reducedMotion) 0.65 else 1.0), 0.0, 1.0)
    return MouthLighting(
        center = liningCenter,
        secondCenter = if (direction == 1) secondPhysical else 1 - secondPhysical,
        strength = strength,
        opening = aperture,
        travel = bounded((worldY * width - localCenterY) / height, -2.0, 2.0),
        liningCenter = liningCenter,
        liningLight = strength,
        sideLight = bounded(sideLight, 0.0, 1.0),
        liningTilt =
            bounded(
                (0.025 + 0.25 * roll + 0.35 * meanShear + 0.035 * (viewPitch - referenceViewPitch)) *
                    direction * response,
                -0.12,
                0.12,
            ),
        liningTint = liningTint,
        liningSurface = liningSurface,
    )
}
