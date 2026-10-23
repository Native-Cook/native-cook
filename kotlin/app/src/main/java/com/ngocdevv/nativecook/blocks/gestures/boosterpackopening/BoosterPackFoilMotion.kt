package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private val TIMES = DoubleArray(FOIL_KEYFRAMES.size) { FOIL_KEYFRAMES[it].ms }
private val X = DoubleArray(FOIL_KEYFRAMES.size) { FOIL_KEYFRAMES[it].x }
private val Y = DoubleArray(FOIL_KEYFRAMES.size) { FOIL_KEYFRAMES[it].y }
private val SCALE_X = DoubleArray(FOIL_KEYFRAMES.size) { FOIL_KEYFRAMES[it].scaleX }
private val SCALE_Y = DoubleArray(FOIL_KEYFRAMES.size) { FOIL_KEYFRAMES[it].scaleY }
private val ROTATION = DoubleArray(FOIL_KEYFRAMES.size) { FOIL_KEYFRAMES[it].rotation }

private val EDGE_ON_TIMES = doubleArrayOf(433.333, 633.333, 733.333, 1000.0)
private val EDGE_ON_POSE = doubleArrayOf(0.0, 1.0, 0.0, 0.0)
private val LEADING_SHEAR_TIMES = doubleArrayOf(0.0, 66.667, 116.667, 150.0, 333.333, 433.333, 533.333, 633.333, 1000.0)
private val LEADING_SHEAR = doubleArrayOf(0.0, 0.0, 0.045, 0.045, 0.06, 0.07, 0.04, 0.0, 0.0)
private val LEADING_LIFT_TIMES = doubleArrayOf(0.0, 150.0, 333.333, 433.333, 533.333, 633.333, 1000.0)
private val LEADING_LIFT = doubleArrayOf(0.0, 0.0, 0.007, 0.012, 0.008, 0.0, 0.0)

/** Deform the actual cut mesh, so a live swipe keeps its own torn edge. */
internal fun deformFlyingFoil(
    base: List<Point>,
    pack: Rect,
    width: Double,
    height: Double,
    time: Double,
): List<Point> {
    if (base.isEmpty()) return emptyList()

    val targetX = interpolateKeyframes(time, TIMES, X) * width
    val targetY = interpolateKeyframes(time, TIMES, Y) * height
    val scaleX = interpolateKeyframes(time, TIMES, SCALE_X)
    val scaleY = interpolateKeyframes(time, TIMES, SCALE_Y)
    val rotation = interpolateKeyframes(time, TIMES, ROTATION) * PI / 180
    val cosine = cos(rotation)
    val sine = sin(rotation)
    val originX = width * 0.5
    val originY = height * 0.522
    val flight = clamp((time - 58) / 575, 0.0, 1.0)
    val curlStrength = clamp((flight - 0.18) / 0.55, 0.0, 1.0)
    val rollProgress = clamp((time - 433.333) / 200, 0.0, 1.0)
    val lateRoll = rollProgress * rollProgress * (3 - 2 * rollProgress)
    val edgeOnPose = interpolateKeyframes(time, EDGE_ON_TIMES, EDGE_ON_POSE)

    // At f133 the leading top corner is 17 px farther right than an affine
    // ribbon; by f150 it is 25 px farther right. The torn lower corner stays put.
    val leadingShear = width * interpolateKeyframes(time, LEADING_SHEAR_TIMES, LEADING_SHEAR)
    val leadingLift = height * interpolateKeyframes(time, LEADING_LIFT_TIMES, LEADING_LIFT)

    val result = ArrayList<Point>(base.size)
    for (index in base.indices) {
        val point = base[index]
        val u = clamp((point.x - pack.x) / pack.width, 0.0, 1.0)
        val isBottom = index % 2 == 1
        val column = index / 2
        val top = base[column * 2]
        val bottom = base[column * 2 + 1]
        val centerY = (top.y + bottom.y) * 0.5
        val halfHeight = max((bottom.y - top.y) * 0.5, 0.0)
        val edgeSign = if (isBottom) 1.0 else -1.0
        val wave = sin(u * PI) * height * 0.013 * flight
        val curlPhase = PI * (u - 0.04)
        val earlyCurl = 1 + curlStrength * (cos(curlPhase) - 1)
        // f162 has a thin leading edge and a broad trailing face. A cosine across
        // the full width puts the broad face in the middle and leaves a hooked tip.
        val trailingCurl = -(0.035 + 0.965 * sin(PI * 0.5 * u * u)) * (1 + edgeOnPose * 0.3)
        val curlScale = earlyCurl + (trailingCurl - earlyCurl) * lateRoll
        val lateTailFan = 1 + clamp((time - 650) / 150, 0.0, 1.0) * u.pow(8) * 1.55
        val ribbonWave = sin(u * PI * 2 + flight * PI * 0.35) * height * 0.0055 * curlStrength
        val crossShear =
            edgeSign * halfHeight * scaleY * sin(curlPhase) * curlStrength * 0.28 * (1 - lateRoll)
        val twist = (u - 0.5) * height * 0.018 * flight * (if (isBottom) 1.0 else -0.25) * (1 - lateRoll)
        val leadingInfluence = (1 - u).pow(2)
        val localX =
            (point.x - originX) * scaleX + crossShear + (if (isBottom) 0.0 else leadingShear * leadingInfluence)
        val localY =
            (centerY - originY) * scaleY +
                edgeSign * halfHeight * scaleY * curlScale * lateTailFan +
                wave + ribbonWave + twist
        val leadingRollU = clamp(u / 0.6, 0.0, 1.0)
        val leadingRollTilt = 1 - leadingRollU * leadingRollU * (3 - 2 * leadingRollU)
        // The nearly edge-on f162 pose slopes down from the raised leading tip;
        // lifting this section separately retains the lower sag of the broad tail.
        val edgeOnLift = height * edgeOnPose * (0.0023 + 0.0082 * leadingRollTilt)
        result.add(
            Point(
                targetX + localX * cosine - localY * sine,
                targetY + localX * sine + localY * cosine - leadingLift * leadingInfluence - edgeOnLift,
            ),
        )
    }
    return result
}
