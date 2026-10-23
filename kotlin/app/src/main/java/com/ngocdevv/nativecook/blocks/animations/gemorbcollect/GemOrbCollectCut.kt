package com.ngocdevv.nativecook.blocks.animations.gemorbcollect

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** The same clipped heptagonal crown, girdle and pavilion as gem-cut.ts. */
internal class GemOrbCollectCut {
    val planes: FloatArray
    private val vertices: List<DoubleArray>

    init {
        val sector = 2.0 * PI / 7.0
        val apothem = cos(PI / 7.0)
        val tangent = (0.935 - apothem * cos(PI / 7.0)) / sin(PI / 7.0)
        val cutRadius = hypot(apothem, tangent)
        val raw = mutableListOf<DoubleArray>()
        fun add(x: Double, y: Double, z: Double, offset: Double) {
            val magnitude = sqrt(x * x + y * y + z * z)
            raw += doubleArrayOf(x / magnitude, y / magnitude, z / magnitude, offset / magnitude)
        }
        for (index in 0 until 7) {
            val angle = PI / 2.0 + (index + 0.5) * sector
            val x = cos(angle)
            val y = sin(angle)
            add(cos(angle + sector / 2.0), sin(angle + sector / 2.0), 0.0, 0.935 / cutRadius)
            add(x * 0.58, y * 0.58, 1.0, 0.28 + 0.50 * 0.58)
            add(x, y, -0.80, 0.94)
            add(cos(angle + sector / 2.0), sin(angle + sector / 2.0), -1.50, 1.32)
        }
        add(0.0, 0.0, 1.0, 0.28)
        add(0.0, 0.0, -1.0, 0.84)
        val extent = intersections(raw).maxOf { hypot(it[0], it[1]) }
        val normalized = raw.map { plane ->
            val x = plane[0] * extent
            val y = plane[1] * extent
            val magnitude = sqrt(x * x + y * y + plane[2] * plane[2])
            doubleArrayOf(x / magnitude, y / magnitude, plane[2] / magnitude, plane[3] / magnitude)
        }
        planes = FloatArray(120) { normalized[it / 4][it % 4].toFloat() }
        vertices = intersections(normalized)
    }

    private fun intersections(cut: List<DoubleArray>): List<DoubleArray> {
        val result = mutableListOf<DoubleArray>()
        for (a in cut.indices) for (b in a + 1 until cut.size) for (c in b + 1 until cut.size) {
            val p = cut[a]
            val q = cut[b]
            val r = cut[c]
            val ux = q[1] * r[2] - q[2] * r[1]
            val uy = q[2] * r[0] - q[0] * r[2]
            val uz = q[0] * r[1] - q[1] * r[0]
            val determinant = p[0] * ux + p[1] * uy + p[2] * uz
            if (abs(determinant) < 0.000001) continue
            val x = (p[3] * ux + q[3] * (r[1] * p[2] - r[2] * p[1]) + r[3] * (p[1] * q[2] - p[2] * q[1])) / determinant
            val y = (p[3] * uy + q[3] * (r[2] * p[0] - r[0] * p[2]) + r[3] * (p[2] * q[0] - p[0] * q[2])) / determinant
            val z = (p[3] * uz + q[3] * (r[0] * p[1] - r[1] * p[0]) + r[3] * (p[0] * q[1] - p[1] * q[0])) / determinant
            if (cut.any { it[0] * x + it[1] * y + it[2] * z > it[3] + 0.000001 }) continue
            if (result.any { hypot(hypot(x - it[0], y - it[1]), z - it[2]) < 0.00001 }) continue
            result += doubleArrayOf(x, y, z)
        }
        return result
    }

    fun attitude(yaw: Float, pitch: Float, roll: Float = 0.22f): Array<FloatArray> {
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val cr = cos(roll)
        val sr = sin(roll)
        return arrayOf(
            floatArrayOf(cy * cr, -cy * sr, sy),
            floatArrayOf(cp * sr + sp * sy * cr, cp * cr - sp * sy * sr, -sp * cy),
            floatArrayOf(sp * sr - cp * sy * cr, sp * cr + cp * sy * sr, cp * cy),
        )
    }

    /** A convex hull of projected cut vertices is the exact projected silhouette. */
    fun outline(rows: Array<FloatArray>): FloatArray {
        val projected = vertices.map { vertex ->
            val depth = dot(rows[2], vertex)
            val perspective = 7.0 / max(7.0 - depth, 0.001)
            doubleArrayOf(dot(rows[0], vertex) * perspective, dot(rows[1], vertex) * perspective)
        }.sortedWith(compareBy<DoubleArray> { it[0] }.thenBy { it[1] })
        fun cross(a: DoubleArray, b: DoubleArray, c: DoubleArray) =
            (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
        val lower = mutableListOf<DoubleArray>()
        for (point in projected) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), point) <= 0) lower.removeAt(lower.lastIndex)
            lower += point
        }
        val upper = mutableListOf<DoubleArray>()
        for (point in projected.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), point) <= 0) upper.removeAt(upper.lastIndex)
            upper += point
        }
        val hull = lower.dropLast(1) + upper.dropLast(1)
        val result = FloatArray(32 * 3)
        for (index in 0 until 32) {
            result[index * 3 + 2] = 10f
        }
        for (index in 0 until minOf(hull.size, 32)) {
            val a = hull[index]
            val b = hull[(index + 1) % hull.size]
            val dx = b[0] - a[0]
            val dy = b[1] - a[1]
            val length = hypot(dx, dy)
            if (length < 0.000001) continue
            val nx = dy / length
            val ny = -dx / length
            result[index * 3] = nx.toFloat()
            result[index * 3 + 1] = ny.toFloat()
            result[index * 3 + 2] = (nx * a[0] + ny * a[1]).toFloat()
        }
        return result
    }

    private fun dot(row: FloatArray, point: DoubleArray): Double =
        row[0] * point[0] + row[1] * point[1] + row[2] * point[2]
}
