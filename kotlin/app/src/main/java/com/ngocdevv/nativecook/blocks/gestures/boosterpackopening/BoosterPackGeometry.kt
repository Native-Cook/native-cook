package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal const val MESH_COLUMNS = 28
internal const val MOUTH_MESH_COLUMNS = 56
internal val MOUTH_MESH_INDICES: ShortArray =
    ShortArray((MOUTH_MESH_COLUMNS - 1) * 6) { index ->
        val column = index / 6
        val a = column * 2
        intArrayOf(a, a + 1, a + 2, a + 2, a + 1, a + 3)[index % 6].toShort()
    }
internal const val MOUTH_WALL_ROWS = 5
internal val MOUTH_WALL_INDICES: ShortArray =
    ShortArray((MOUTH_MESH_COLUMNS - 1) * (MOUTH_WALL_ROWS - 1) * 6) { flatIndex ->
        val index = flatIndex / 6
        val column = index / (MOUTH_WALL_ROWS - 1)
        val row = index % (MOUTH_WALL_ROWS - 1)
        val a = column * MOUTH_WALL_ROWS + row
        val b = a + MOUTH_WALL_ROWS
        intArrayOf(a, a + 1, b, b, a + 1, b + 1)[flatIndex % 6].toShort()
    }
internal const val PREVIEW_MESH_COLUMNS = 64
internal const val PREVIEW_MESH_ROWS = 9

// Concentrate vertices at both curling ends while retaining a symmetric mesh
// for either swipe direction. The detached strip has its own coarser topology.
internal fun previewColumnU(column: Int): Double =
    (1.0 - cos(PI * column.toDouble() / (PREVIEW_MESH_COLUMNS - 1).toDouble())) * 0.5

internal fun makeEmptyPath(): Path = Path()

internal fun makeSmoothPath(points: List<Point>): Path {
    val builder = PathBuilder()
    if (points.isEmpty()) return builder.build()

    builder.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return builder.build()

    for (index in 1 until points.size - 1) {
        val current = points[index]
        val next = points[index + 1]
        builder.quadTo(
            current.x,
            current.y,
            (current.x + next.x) / 2.0,
            (current.y + next.y) / 2.0,
        )
    }

    val beforeLast = points[points.size - 2]
    val last = points[points.size - 1]
    builder.quadTo(beforeLast.x, beforeLast.y, last.x, last.y)
    return builder.build()
}

internal fun trimTraceEnd(
    points: List<Point>,
    trimDistance: Double,
): List<Point> {
    if (points.size < 2 || trimDistance <= 0.0) return ArrayList(points)

    val trimmed = ArrayList(points)
    var remaining = trimDistance

    while (trimmed.size > 1) {
        val endIndex = trimmed.size - 1
        val end = trimmed[endIndex]
        val previous = trimmed[endIndex - 1]
        val dx = end.x - previous.x
        val dy = end.y - previous.y
        val length = sqrt(dx * dx + dy * dy)

        if (length > remaining && length > 0.0001) {
            val retained = (length - remaining) / length
            trimmed[endIndex] =
                Point(
                    previous.x + dx * retained,
                    previous.y + dy * retained,
                )
            return trimmed
        }

        remaining -= length
        trimmed.removeAt(endIndex)
    }

    // A just-started gesture can be shorter than the cap radius. Keep a tiny
    // segment so Skia can still draw a stable clipped onset instead of a dot.
    val first = points[0]
    val last = points[points.size - 1]
    val dx = last.x - first.x
    val dy = last.y - first.y
    val length = sqrt(dx * dx + dy * dy)
    if (length <= 0.0001) return listOf(first)
    val retained = min(0.5 / length, 1.0)
    return listOf(
        first,
        Point(
            first.x + dx * retained,
            first.y + dy * retained,
        ),
    )
}

internal fun completeCutPoints(
    inputPoints: List<Point>,
    pack: Rect,
    direction: Int,
): List<Point> {
    if (inputPoints.isEmpty()) return emptyList()

    val ordered = if (direction == 1) ArrayList(inputPoints) else inputPoints.reversed()
    val first = ordered[0]
    val last = ordered[ordered.size - 1]
    val left = pack.x
    val right = pack.x + pack.width

    val completed = ArrayList<Point>(ordered.size + 6)

    // The gesture arms before the finger reaches the opposite edge. Preserve the
    // measured exit tangent through that final gap, then ease it flat at the pack
    // boundary. A single horizontal append made every rim layer kink apart here.
    if (direction == -1 && first.x > left) {
        val next = ordered[min(1, ordered.size - 1)]
        val gap = first.x - left
        val slope =
            clamp(
                (next.y - first.y) / max(next.x - first.x, 0.0001),
                -0.35,
                0.35,
            )
        val endY = first.y - slope * gap * 0.35
        for (t in doubleArrayOf(1.0, 2.0 / 3.0, 1.0 / 3.0)) {
            val y =
                (2.0 * t * t * t - 3.0 * t * t + 1.0) * first.y +
                    (t * t * t - 2.0 * t * t + t) * -gap * slope +
                    (-2.0 * t * t * t + 3.0 * t * t) * endY +
                    (t * t * t - t * t) * -gap * slope * 0.75
            completed.add(Point(first.x - gap * t, y))
        }
    } else if (first.x > left) {
        completed.add(Point(left, first.y))
    }

    completed.addAll(ordered)

    if (direction == 1 && last.x < right) {
        val previous = ordered[max(0, ordered.size - 2)]
        val gap = right - last.x
        val slope =
            clamp(
                (last.y - previous.y) / max(last.x - previous.x, 0.0001),
                -0.35,
                0.35,
            )
        val endY = last.y + slope * gap * 0.35
        for (t in doubleArrayOf(1.0 / 3.0, 2.0 / 3.0, 1.0)) {
            val y =
                (2.0 * t * t * t - 3.0 * t * t + 1.0) * last.y +
                    (t * t * t - 2.0 * t * t + t) * gap * slope +
                    (-2.0 * t * t * t + 3.0 * t * t) * endY +
                    (t * t * t - t * t) * gap * slope * 0.75
            completed.add(Point(last.x + gap * t, y))
        }
    } else if (last.x < right) {
        completed.add(Point(right, last.y))
    }
    return completed
}

internal fun completePreviewCutPoints(
    inputPoints: List<Point>,
    pack: Rect,
    direction: Int,
    seamY: Double,
): List<Point> {
    if (inputPoints.isEmpty()) return emptyList()

    val ordered = if (direction == 1) ArrayList(inputPoints) else inputPoints.reversed()
    val left = pack.x
    val right = pack.x + pack.width
    val transitionWidth = pack.width * 0.045

    if (direction == 1) {
        val first = ordered[0]
        val head = ordered[ordered.size - 1]
        val completed = ArrayList<Point>(ordered.size + 3)
        if (first.x > left) completed.add(Point(left, first.y))
        completed.addAll(ordered)
        if (head.x < right) {
            val transitionX = min(right, head.x + transitionWidth)
            if (transitionX > head.x + 0.5) {
                completed.add(Point(transitionX, seamY))
            }
            if (transitionX < right) completed.add(Point(right, seamY))
        }
        return completed
    }

    val head = ordered[0]
    val completed = ArrayList<Point>(ordered.size + 3)
    completed.add(Point(left, seamY))
    val transitionX = max(left, head.x - transitionWidth)
    if (transitionX > left + 0.5) {
        completed.add(Point(transitionX, seamY))
    }
    completed.addAll(ordered)
    val last = ordered[ordered.size - 1]
    if (last.x < right) completed.add(Point(right, last.y))
    return completed
}

internal fun makeBottomMask(points: List<Point>, pack: Rect): Path {
    val builder = PathBuilder()
    if (points.size < 2) return builder.build()

    builder.moveTo(points[0].x, points[0].y)
    for (index in 1 until points.size - 1) {
        val current = points[index]
        val next = points[index + 1]
        builder.quadTo(
            current.x,
            current.y,
            (current.x + next.x) / 2.0,
            (current.y + next.y) / 2.0,
        )
    }
    val beforeLast = points[points.size - 2]
    val last = points[points.size - 1]
    builder.quadTo(beforeLast.x, beforeLast.y, last.x, last.y)
    builder.lineTo(pack.x + pack.width, pack.y + pack.height)
    builder.lineTo(pack.x, pack.y + pack.height)
    builder.close()
    return builder.build()
}

private fun yAtX(points: List<Point>, x: Double): Double {
    if (points.isEmpty()) return 0.0
    if (x <= points[0].x) return points[0].y
    val last = points.size - 1
    if (x >= points[last].x) return points[last].y

    for (index in 1 until points.size) {
        val right = points[index]
        if (x <= right.x) {
            val left = points[index - 1]
            val width = max(0.0001, right.x - left.x)
            val progress = clamp((x - left.x) / width, 0.0, 1.0)
            return left.y + (right.y - left.y) * progress
        }
    }
    return points[last].y
}

internal fun makeFoilMesh(
    points: List<Point>,
    pack: Rect,
): Mesh {
    val vertices = ArrayList<Point>(MESH_COLUMNS * 2)
    val textures = ArrayList<Point>(MESH_COLUMNS * 2)

    for (column in 0 until MESH_COLUMNS) {
        val u = column.toDouble() / (MESH_COLUMNS - 1).toDouble()
        val x = pack.x + u * pack.width
        val bottomY = yAtX(points, x)
        val top = Point(x, pack.y)
        val bottom = Point(x, bottomY)
        vertices.add(top)
        vertices.add(bottom)
        textures.add(top)
        textures.add(bottom)
    }
    return Mesh(vertices, textures)
}

internal fun makePreviewFoilMesh(
    points: List<Point>,
    pack: Rect,
): Mesh {
    val vertices = ArrayList<Point>(PREVIEW_MESH_COLUMNS * PREVIEW_MESH_ROWS)
    val textures = ArrayList<Point>(PREVIEW_MESH_COLUMNS * PREVIEW_MESH_ROWS)

    for (column in 0 until PREVIEW_MESH_COLUMNS) {
        val u = previewColumnU(column)
        val x = pack.x + u * pack.width
        val bottomY = yAtX(points, x)

        for (row in 0 until PREVIEW_MESH_ROWS) {
            val v = row.toDouble() / (PREVIEW_MESH_ROWS - 1).toDouble()
            val y = pack.y + (bottomY - pack.y) * v
            val point = Point(x, y)
            vertices.add(point)
            textures.add(point)
        }
    }
    return Mesh(vertices, textures)
}

internal fun makeCollapsedFoilMesh(pack: Rect): Mesh {
    val vertices = ArrayList<Point>(MESH_COLUMNS * 2)
    val textures = ArrayList<Point>(MESH_COLUMNS * 2)
    repeat(MESH_COLUMNS * 2) {
        vertices.add(Point(pack.x, pack.y))
        textures.add(Point(pack.x, pack.y))
    }
    return Mesh(vertices, textures)
}

internal fun makeCollapsedPreviewFoilMesh(pack: Rect): Mesh {
    val vertices = ArrayList<Point>(PREVIEW_MESH_COLUMNS * PREVIEW_MESH_ROWS)
    val textures = ArrayList<Point>(PREVIEW_MESH_COLUMNS * PREVIEW_MESH_ROWS)
    repeat(PREVIEW_MESH_COLUMNS * PREVIEW_MESH_ROWS) {
        vertices.add(Point(pack.x, pack.y))
        textures.add(Point(pack.x, pack.y))
    }
    return Mesh(vertices, textures)
}

private fun cavityDepthAt(
    point: Point,
    pack: Rect,
    depth: Double,
    direction: Int,
    exponent: Double,
): Double {
    val u = clamp((point.x - pack.x) / max(pack.width, 0.0001), 0.0, 1.0)
    val directionalU = if (direction == 1) u else 1.0 - u
    // The physical tear has a shallow entry cuff and opens continuously toward
    // the swipe exit. This profile fits the measured frame-150/162/170 section
    // depths and mirrors automatically for a right-to-left swipe.
    val entryBump = 0.09 * (1.0 - directionalU).pow(4.0)
    return depth *
        min(
            1.0,
            0.18 + 0.82 * directionalU.pow(exponent) + entryBump,
        )
}

private fun frontLipDepthAt(
    point: Point,
    pack: Rect,
    depth: Double,
    direction: Int,
): Double {
    val u = clamp((point.x - pack.x) / max(pack.width, 0.0001), 0.0, 1.0)
    val directionalU = if (direction == 1) u else 1.0 - u
    // The laminated front lip stays narrow at the tear entry and fans out into
    // the exit cuff. Keeping this on the same directional coordinate makes the
    // full cross-section mirror cleanly for right-to-left gestures.
    return depth * (0.56 + 0.44 * directionalU)
}

private fun liftFrontLipPoints(
    points: List<Point>,
    pack: Rect,
    lift: Double,
    direction: Int,
    middleLift: Double = 0.0,
): List<Point> {
    if (lift <= 0.0) return ArrayList(points)

    return points.map { point ->
        val u =
            clamp(
                (point.x - pack.x) / max(pack.width, 0.0001),
                0.0,
                1.0,
            )
        val directionalU = if (direction == 1) u else 1.0 - u
        // The reference cuff rises as one laminate near the swipe exit.  A
        // smoothstep keeps the measured center section untouched and gives every
        // front layer the same tangent instead of stacking disconnected patches.
        val t = clamp((directionalU - 0.89) / 0.11, 0.0, 1.0)
        val eased = t * t * (3.0 - 2.0 * t)
        // The last few percent curl more sharply than the long approach. Keeping
        // this as a high-order terminal term preserves the measured center line,
        // while the very end rises as one bonded laminate instead of closing as a
        // flat capsule.
        val exitCurl = 0.78 * t.pow(6.0)
        var middleProfile = 0.0
        if (directionalU >= 0.25 && directionalU < 0.43) {
            val middleT = clamp((directionalU - 0.25) / 0.18, 0.0, 1.0)
            middleProfile = middleT * middleT * (3.0 - 2.0 * middleT)
        } else if (directionalU >= 0.43 && directionalU < 0.58) {
            val middleT = clamp((directionalU - 0.43) / 0.15, 0.0, 1.0)
            middleProfile = 1.0 - middleT * 0.25
        } else if (directionalU >= 0.58 && directionalU < 0.66) {
            val middleT = clamp((directionalU - 0.58) / 0.08, 0.0, 1.0)
            val middleEased = middleT * middleT * (3.0 - 2.0 * middleT)
            middleProfile = 0.75 * (1.0 - middleEased)
        }
        Point(
            point.x,
            point.y -
                lift * (eased + exitCurl) -
                middleLift * middleProfile,
        )
    }
}

private fun normalAt(points: List<Point>, index: Int): Point {
    val point = points[index]
    var previousIndex = max(0, index - 1)
    var nextIndex = min(points.size - 1, index + 1)
    while (
        previousIndex > 0 &&
        abs(points[previousIndex].x - point.x) +
        abs(points[previousIndex].y - point.y) <
        0.0001
    ) {
        previousIndex -= 1
    }
    while (
        nextIndex < points.size - 1 &&
        abs(points[nextIndex].x - point.x) +
        abs(points[nextIndex].y - point.y) <
        0.0001
    ) {
        nextIndex += 1
    }
    val previous = points[previousIndex]
    val next = points[nextIndex]
    val dx = next.x - previous.x
    val dy = next.y - previous.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < 0.0001) return Point(0.0, -1.0)
    return Point(dy / length, -dx / length)
}

private fun monotoneSlopeAt(previous: Point, point: Point, next: Point): Double {
    val previousDelta = point.x - previous.x
    val nextDelta = next.x - point.x
    val previousWidth = abs(previousDelta)
    val nextWidth = abs(nextDelta)
    if (previousWidth < 0.0001 && nextWidth < 0.0001) return 0.0
    if (previousWidth < 0.0001) {
        return if (nextWidth < 0.0001) 0.0 else (next.y - point.y) / nextDelta
    }
    if (nextWidth < 0.0001) {
        return (point.y - previous.y) / previousDelta
    }

    val previousSlope = (point.y - previous.y) / previousDelta
    val nextSlope = (next.y - point.y) / nextDelta
    if (previousSlope * nextSlope <= 0.0) return 0.0

    val previousWeight = 2.0 * nextWidth + previousWidth
    val nextWeight = nextWidth + 2.0 * previousWidth
    val denominator =
        previousWeight / previousSlope + nextWeight / nextSlope
    if (abs(denominator) < 0.0001) return 0.0
    return (previousWeight + nextWeight) / denominator
}

private fun appendCubicSpline(
    builder: PathBuilder,
    points: List<Point>,
) {
    for (index in 0 until points.size - 1) {
        val p0 = points[max(0, index - 1)]
        val p1 = points[index]
        val p2 = points[index + 1]
        val p3 = points[min(points.size - 1, index + 2)]
        val segmentWidth = p2.x - p1.x
        if (abs(segmentWidth) < 0.0001) {
            builder.lineTo(p2.x, p2.y)
            continue
        }
        val startSlope = monotoneSlopeAt(p0, p1, p2)
        val endSlope = monotoneSlopeAt(p1, p2, p3)
        builder.cubicTo(
            p1.x + segmentWidth / 3.0,
            p1.y + (startSlope * segmentWidth) / 3.0,
            p2.x - segmentWidth / 3.0,
            p2.y - (endSlope * segmentWidth) / 3.0,
            p2.x,
            p2.y,
        )
    }
}

private fun makeCubicSplinePath(points: List<Point>): Path {
    val builder = PathBuilder()
    if (points.isEmpty()) return builder.build()
    builder.moveTo(points[0].x, points[0].y)
    appendCubicSpline(builder, points)
    return builder.build()
}

internal fun relaxMouthPoints(
    points: List<Point>,
    pack: Rect,
    entryOffset: Double,
    exitOffset: Double,
    exponent: Double,
    centerLift: Double,
    direction: Int,
): List<Point> =
    points.map { point ->
        val u = clamp((point.x - pack.x) / max(pack.width, 0.0001), 0.0, 1.0)
        val directionalU = if (direction == 1) u else 1.0 - u
        val spatialOffset =
            entryOffset +
                (exitOffset - entryOffset) * directionalU.pow(exponent) +
                centerLift *
                max(0.0, sin(PI * directionalU)).pow(1.4)
        Point(point.x, point.y + spatialOffset)
    }

internal fun makeCavityFrontEdge(points: List<Point>): Path = makeCubicSplinePath(points)

private fun mouthYAtX(points: List<Point>, x: Double): Double {
    if (points.size < 2 || x <= points[0].x || x >= points[points.size - 1].x) {
        return yAtX(points, x)
    }
    for (i in 1 until points.size) {
        if (x > points[i].x) continue
        val left = points[i - 1]
        val right = points[i]
        val dx = max(right.x - left.x, 0.0001)
        val t = clamp((x - left.x) / dx, 0.0, 1.0)
        val a = monotoneSlopeAt(points[max(0, i - 2)], left, right)
        val b = monotoneSlopeAt(left, right, points[min(points.size - 1, i + 1)])
        return (2.0 * t * t * t - 3.0 * t * t + 1.0) * left.y + (t * t * t - 2.0 * t * t + t) * dx * a +
            (-2.0 * t * t * t + 3.0 * t * t) * right.y + (t * t * t - t * t) * dx * b
    }
    return points[points.size - 1].y
}

// Resample before curling so even a sparse gesture has a smooth terminal
// bend. This same front contour drives the body mask, laminate and wall.
internal fun curlMouthPoints(
    points: List<Point>,
    pack: Rect,
    lift: Double,
    direction: Int,
): List<Point> {
    if (points.size < 2) return ArrayList(points)
    return List(MOUTH_MESH_COLUMNS) { index ->
        val u = index.toDouble() / (MOUTH_MESH_COLUMNS - 1).toDouble()
        val x = pack.x + u * pack.width
        // A broad turn ends in a rising crease, as the foil does in frames
        // 162–178. A smoothstep here flattens at the tip into a rounded lip.
        val t = clamp(((if (direction == 1) u else 1.0 - u) - 0.8) / 0.2, 0.0, 1.0)
        Point(x, mouthYAtX(points, x) - lift * t.pow(1.7))
    }
}

private fun mouthColumnU(index: Int): Double {
    if (index < 12) return 0.06 * (1.0 - cos(index.toDouble() / 11.0 * PI / 2.0))
    if (index >= MOUTH_MESH_COLUMNS - 12) {
        return 1.0 - 0.06 * (1.0 - cos((MOUTH_MESH_COLUMNS - 1 - index).toDouble() / 11.0 * PI / 2.0))
    }
    return 0.06 + (index - 11).toDouble() / (MOUTH_MESH_COLUMNS - 23).toDouble() * 0.88
}

// Material coordinates remain attached to the curved laminate as its opening
// and the user's tear path change. Both surfaces use the foil strip topology.
internal fun makeMouthMaterialMesh(
    points: List<Point>,
    pack: Rect,
    depth: Double,
    direction: Int,
    exponent: Double,
    frontLip: Boolean = false,
    exitLift: Double = 0.0,
    backPoints: List<Point> = points,
): Mesh {
    if (points.size < 2) {
        return Mesh(
            vertices = List(MOUTH_MESH_COLUMNS * 2) { Point(pack.x, pack.y) },
            textures = List(MOUTH_MESH_COLUMNS * 2) { Point(0.0, 0.0) },
        )
    }
    val samples =
        List(MOUTH_MESH_COLUMNS) { index ->
            val u = if (frontLip) mouthColumnU(index) else index.toDouble() / (MOUTH_MESH_COLUMNS - 1).toDouble()
            val x = pack.x + u * pack.width
            Point(x, mouthYAtX(points, x))
        }
    val backSamples: List<Point> =
        if (frontLip) {
            emptyList()
        } else {
            samples.map { point ->
                Point(point.x, mouthYAtX(backPoints, point.x))
            }
        }
    val front =
        if (frontLip) {
            liftFrontLipPoints(samples, pack, exitLift, direction).map { point ->
                Point(point.x, point.y - depth * 0.15)
            }
        } else {
            samples
        }
    val outer =
        if (frontLip) {
            front.mapIndexed { index, point ->
                val normal = normalAt(samples, index)
                val d = frontLipDepthAt(point, pack, depth, direction)
                Point(point.x - normal.x * d, point.y - normal.y * d)
            }
        } else {
            backSamples.mapIndexed { index, point ->
                val normal = normalAt(backSamples, index)
                val d = cavityDepthAt(point, pack, depth, direction, exponent)
                Point(point.x + normal.x * d, point.y + normal.y * d)
            }
        }
    val vertices = ArrayList<Point>(MOUTH_MESH_COLUMNS * 2)
    val textures = ArrayList<Point>(MOUTH_MESH_COLUMNS * 2)
    for (index in 0 until MOUTH_MESH_COLUMNS) {
        val u = (samples[index].x - pack.x) / pack.width
        vertices.add(if (frontLip) front[index] else outer[index])
        vertices.add(if (frontLip) outer[index] else front[index])
        textures.add(Point(if (direction == 1) u else 1.0 - u, 0.0))
        textures.add(Point(if (direction == 1) u else 1.0 - u, 1.0))
    }
    return Mesh(vertices, textures)
}

internal fun makeMouthWallMesh(
    frontPoints: List<Point>,
    backPoints: List<Point>,
    pack: Rect,
    depth: Double,
    direction: Int,
    exponent: Double,
): Mesh {
    val vertices = ArrayList<Point>(MOUTH_MESH_COLUMNS * MOUTH_WALL_ROWS)
    val textures = ArrayList<Point>(MOUTH_MESH_COLUMNS * MOUTH_WALL_ROWS)
    val valid = frontPoints.size > 1 && backPoints.size > 1
    for (column in 0 until MOUTH_MESH_COLUMNS) {
        val u = mouthColumnU(column)
        val directionalU = if (direction == 1) u else 1.0 - u
        val x = pack.x + u * pack.width
        val entry = clamp(1.0 - directionalU / 0.07, 0.0, 1.0).pow(2.0)
        val exit = clamp(1.0 - (1.0 - directionalU) / 0.07, 0.0, 1.0).pow(2.0)
        val inset =
            direction.toDouble() * (
                min(depth * 0.22, pack.width * 0.025) * entry -
                    min(depth * 0.14, pack.width * 0.014) * exit
            )
        val rearX = x + inset
        val frontY = mouthYAtX(frontPoints, x)
        val rearPoint = Point(x, mouthYAtX(backPoints, rearX))
        val rearY = rearPoint.y - cavityDepthAt(rearPoint, pack, depth, direction, exponent)
        for (row in 0 until MOUTH_WALL_ROWS) {
            val v = row.toDouble() / (MOUTH_WALL_ROWS - 1).toDouble()
            // The rear sheet turns outward toward the front silhouette. A cubic
            // side return replaces the former vertical, cut-off end of the strip.
            val turn = 1.0 - (1.0 - v).pow(3.0)
            vertices.add(
                if (valid) {
                    Point(rearX + (x - rearX) * turn, rearY + (frontY - rearY) * v)
                } else {
                    Point(pack.x, pack.y)
                },
            )
            textures.add(Point(directionalU, v))
        }
    }
    return Mesh(vertices, textures)
}

// Skia's triangle silhouette does not antialias like a Path. Draw a small
// overscan behind a shared cubic clip so metallic edges stay clean at 1x too.
internal fun expandMouthMesh(vertices: List<Point>, rows: Int, amount: Double): List<Point> {
    val columns = vertices.size / rows
    return vertices.mapIndexed { index, point ->
        val column = index / rows
        val row = index % rows
        Point(
            point.x + (if (column == 0) -amount else if (column == columns - 1) amount else 0.0),
            point.y + (if (row == 0) -amount else if (row == rows - 1) amount else 0.0),
        )
    }
}

internal fun makeMouthMeshOutline(vertices: List<Point>, rows: Int, tipRadius: Double = 0.0): Path {
    val builder = PathBuilder()
    val columns = vertices.size / rows
    if (columns < 2) return builder.build()
    val top = List(columns) { i -> vertices[i * rows] }
    val bottom = List(columns) { i -> vertices[i * rows + rows - 1] }
    builder.moveTo(top[0].x, top[0].y)
    appendCubicSpline(builder, top)
    val rightTop = top[columns - 1]
    val rightBottom = bottom[columns - 1]
    if (tipRadius > 0.0) {
        builder.cubicTo(
            rightTop.x + tipRadius,
            rightTop.y,
            rightBottom.x + tipRadius,
            rightBottom.y,
            rightBottom.x,
            rightBottom.y,
        )
    } else {
        builder.cubicTo(
            rightBottom.x,
            rightTop.y + (rightBottom.y - rightTop.y) / 3.0,
            rightBottom.x,
            rightTop.y + (rightBottom.y - rightTop.y) * 2.0 / 3.0,
            rightBottom.x,
            rightBottom.y,
        )
    }
    appendCubicSpline(builder, bottom.reversed())
    val leftTop = top[0]
    val leftBottom = bottom[0]
    if (tipRadius > 0.0) {
        builder.cubicTo(
            leftBottom.x - tipRadius,
            leftBottom.y,
            leftTop.x - tipRadius,
            leftTop.y,
            leftTop.x,
            leftTop.y,
        )
    } else {
        builder.cubicTo(
            leftBottom.x,
            leftTop.y + (leftBottom.y - leftTop.y) * 2.0 / 3.0,
            leftBottom.x,
            leftTop.y + (leftBottom.y - leftTop.y) / 3.0,
            leftTop.x,
            leftTop.y,
        )
    }
    builder.close()
    return builder.build()
}

private val BODY_ROWS = doubleArrayOf(0.0, 0.025, 0.06, 0.12, 0.22, 0.38, 0.6, 0.8, 1.0)
internal val BODY_MESH_INDICES: ShortArray =
    ShortArray((MESH_COLUMNS - 1) * (BODY_ROWS.size - 1) * 6) { flatIndex ->
        val index = flatIndex / 6
        val column = index / (BODY_ROWS.size - 1)
        val row = index % (BODY_ROWS.size - 1)
        val a = column * BODY_ROWS.size + row
        val b = a + BODY_ROWS.size
        intArrayOf(a, a + 1, b, b, a + 1, b + 1)[flatIndex % 6].toShort()
    }

internal fun makeBodyMaterialMesh(
    points: List<Point>,
    pack: Rect,
    sourceTop: Double,
    warp: Double,
    textureOffset: Double,
): Mesh {
    val vertices = ArrayList<Point>(MESH_COLUMNS * BODY_ROWS.size)
    val textures = ArrayList<Point>(MESH_COLUMNS * BODY_ROWS.size)
    for (col in 0 until MESH_COLUMNS) {
        val x = pack.x + col.toDouble() / (MESH_COLUMNS - 1).toDouble() * pack.width
        val top = if (points.size > 1) yAtX(points, x) else sourceTop
        for (v in BODY_ROWS) {
            val y = top + (pack.y + pack.height - top) * v
            // Only substrate below the manufactured seal belongs to the opened
            // front panel. Warp its UVs onto the user's curved cut; never expose the
            // baked straight magenta seal as a second, intersecting mouth edge.
            val delta = sourceTop - top - textureOffset
            val bodyHeight = max(pack.y + pack.height - top, 0.0001)
            val reach = min(bodyHeight, max(pack.height * 0.11, max(0.0, delta) * 3.1))
            // Confine seal removal to the first few rows. Extending it over the
            // whole front panel bends the character artwork as the mouth relaxes.
            // The reach/derivative bound also keeps texture rows in their order.
            val power = if (delta > 0.0 && reach < delta * 3.1) 1.0 else 3.0
            val local = clamp(1.0 - (y - top) / reach, 0.0, 1.0)
            val offset = delta * local.pow(power) * warp
            vertices.add(Point(x, y))
            textures.add(Point(x, y + textureOffset + offset))
        }
    }
    return Mesh(vertices, textures)
}

internal fun makeCavityBottomMask(
    points: List<Point>,
    pack: Rect,
): Path {
    val builder = PathBuilder()
    if (points.size < 2) return builder.build()
    builder.moveTo(points[0].x, points[0].y)
    appendCubicSpline(builder, points)
    builder.lineTo(pack.x + pack.width, pack.y + pack.height)
    builder.lineTo(pack.x, pack.y + pack.height)
    builder.close()
    return builder.build()
}

internal fun makeEntryBodyWedge(
    points: List<Point>,
    pack: Rect,
    direction: Int,
): Path {
    val builder = PathBuilder()
    if (points.size < 2) return builder.build()

    val entry = if (direction == 1) points[0] else points[points.size - 1]
    val inward = direction.toDouble()
    builder.moveTo(
        entry.x + inward * pack.width * 0.003,
        entry.y + pack.width * 0.008,
    )
    builder.cubicTo(
        entry.x + inward * pack.width * 0.007,
        entry.y + pack.width * 0.035,
        entry.x + inward * pack.width * 0.014,
        entry.y + pack.width * 0.085,
        entry.x + inward * pack.width * 0.022,
        entry.y + pack.width * 0.13,
    )
    builder.lineTo(
        entry.x + inward * pack.width * 0.075,
        entry.y + pack.width * 0.13,
    )
    builder.lineTo(
        entry.x + inward * pack.width * 0.075,
        entry.y + pack.width * 0.008,
    )
    builder.close()
    return builder.build()
}

internal val FOIL_MESH_INDICES: ShortArray =
    ShortArray((MESH_COLUMNS - 1) * 6) { index ->
        val cell = index / 6
        val offset = index % 6
        val topLeft = cell * 2
        val bottomLeft = topLeft + 1
        val topRight = topLeft + 2
        val bottomRight = topLeft + 3
        intArrayOf(topLeft, bottomLeft, topRight, topRight, bottomLeft, bottomRight)[offset].toShort()
    }

internal val PREVIEW_FOIL_MESH_INDICES: ShortArray =
    ShortArray((PREVIEW_MESH_COLUMNS - 1) * (PREVIEW_MESH_ROWS - 1) * 6) { index ->
        val triangleOffset = index % 6
        val cell = index / 6
        val column = cell / (PREVIEW_MESH_ROWS - 1)
        val row = cell % (PREVIEW_MESH_ROWS - 1)
        val topLeft = column * PREVIEW_MESH_ROWS + row
        val bottomLeft = topLeft + 1
        val topRight = topLeft + PREVIEW_MESH_ROWS
        val bottomRight = topRight + 1
        intArrayOf(topLeft, bottomLeft, topRight, topRight, bottomLeft, bottomRight)[triangleOffset].toShort()
    }

internal fun referenceTracePoints(
    progress: Double,
    width: Double,
    height: Double,
): List<Point> {
    val p0 = Point(0.125 * width, 0.568 * height)
    val p1 = Point(0.31 * width, 0.576 * height)
    val p2 = Point(0.58 * width, 0.565 * height)
    val p3 = Point(0.765 * width, 0.544 * height)
    val samples = max(2, ceil(32.0 * clamp(progress, 0.0, 1.0)).toInt())
    val result = ArrayList<Point>(samples)

    for (index in 0 until samples) {
        val t = (index.toDouble() / (samples - 1).toDouble()) * clamp(progress, 0.0, 1.0)
        val inverse = 1.0 - t
        result.add(
            Point(
                inverse * inverse * inverse * p0.x +
                    3.0 * inverse * inverse * t * p1.x +
                    3.0 * inverse * t * t * p2.x +
                    t * t * t * p3.x,
                inverse * inverse * inverse * p0.y +
                    3.0 * inverse * inverse * t * p1.y +
                    3.0 * inverse * t * t * p2.y +
                    t * t * t * p3.y,
            ),
        )
    }
    return result
}
