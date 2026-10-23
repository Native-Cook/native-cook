package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private val SHEAR_PROGRESS = doubleArrayOf(0.06, 0.29, 0.44, 0.52, 0.72, 0.95, 1.0)
private val SHEAR_AMOUNT = doubleArrayOf(0.0, 0.003, 0.0062, 0.0062, 0.0101, 0.0148, 0.0148)

/** Gesture-driven projection of the measured curved fold; the cut stays fixed. */
internal fun deformPreviewFoil(
    base: List<Point>,
    pack: Rect,
    progress: Double,
    direction: Int,
): List<Point> {
    if (base.isEmpty()) return emptyList()
    val p = clamp(progress, 0.0, 1.0)
    if (p <= 0.06) return base
    var upper = 1
    while (upper < FOLD_PROFILES.size - 1 && FOLD_PROFILES[upper].progress < p) upper++
    val a = FOLD_PROFILES[upper - 1]
    val b = FOLD_PROFILES[upper]
    val mix = clamp((p - a.progress) / (b.progress - a.progress), 0.0, 1.0)
    val tipShift = (a.tipShift + (b.tipShift - a.tipShift) * mix) * pack.width
    val shear = pack.width * interpolateKeyframes(p, SHEAR_PROGRESS, SHEAR_AMOUNT)
    val positions = ArrayList<Point>(base.size)
    val exponent = 1.7
    val firstFalloff = (1 - 1.0 / (PREVIEW_MESH_ROWS - 1)).pow(exponent)
    var columnStart = 0
    while (columnStart < base.size) {
        val column = columnStart / PREVIEW_MESH_ROWS
        val columnEnd = min(columnStart + PREVIEW_MESH_ROWS, base.size)
        val profileColumn = if (direction == 1) column else PREVIEW_MESH_COLUMNS - 1 - column
        val bottom = base.getOrNull(columnStart + PREVIEW_MESH_ROWS - 1)
        val height = if (bottom != null) max(bottom.y - pack.y, 0.0) else 0.0
        if (bottom == null || profileColumn < 0 || profileColumn >= PREVIEW_MESH_COLUMNS || height < 0.001) {
            for (index in columnStart until columnEnd) positions.add(base[index])
            columnStart += PREVIEW_MESH_ROWS
            continue
        }

        // The depth solution depends on the column's cut height, not its row.
        val edge = (a.edge[profileColumn] + (b.edge[profileColumn] - a.edge[profileColumn]) * mix) * pack.width
        val firstHeight = height / (PREVIEW_MESH_ROWS - 1)
        val alphaFraction = clamp(FOLD_ALPHA_TOP[column] * pack.height / firstHeight, 0.0, 1.0)
        val minRowHeight = height * 0.0005
        // Solve for the underlying vertex: the source's rounded alpha corner
        // lies inside the first row, while measurements describe the visible edge.
        var depth = (edge - alphaFraction * firstHeight) / (1 + alphaFraction * (firstFalloff - 1))
        if (firstHeight + depth * (firstFalloff - 1) < minRowHeight) {
            depth = edge - alphaFraction * minRowHeight
        }
        depth = clamp(depth, 0.0, height * 0.92)
        val projectedHeight = max(height - depth, 0.001)

        for (index in columnStart until columnEnd) {
            val point = base[index]
            val row = index - columnStart
            if (row == PREVIEW_MESH_ROWS - 1) {
                positions.add(point)
                continue
            }
            val u = clamp((point.x - pack.x) / pack.width, 0.0, 1.0)
            val fromStart = if (direction == 1) u else 1 - u
            val v = row.toDouble() / (PREVIEW_MESH_ROWS - 1)
            val desiredY = point.y + depth * (1 - v).pow(exponent)
            val previousY = if (row == 0) pack.y + depth else positions[index - 1].y + minRowHeight
            val y = min(bottom.y, max(previousY, desiredY))
            val projectedV = clamp((y - pack.y - depth) / projectedHeight, 0.0, 1.0)
            // The curled side stays almost vertical; return to the fixed tear edge
            // close to the seam instead of drawing the side inward along its full height.
            val curveShift =
                tipShift * clamp(1 - fromStart / 0.6, 0.0, 1.0).pow(2) * (1 - projectedV.pow(3))
            val printShear =
                -shear * exp(-((fromStart - 0.25) / 0.085).pow(2)) * sin(PI * projectedV)
            positions.add(Point(point.x + direction * (curveShift + printShear), y))
        }
        columnStart += PREVIEW_MESH_ROWS
    }
    return positions
}
