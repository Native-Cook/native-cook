package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

import kotlin.math.*

/** CPU projection work shared by all fragments; the optical solid stays in the shader. */
internal object CrystalTrophyOptics {
    fun rim(yaw: Double, rise: Double, f: CrystalTrophyFrame): FloatArray {
        val drop = (1 - rise) * 2.96
        val points = mutableListOf<DoubleArray>()
        val vertices = CrystalTrophyMesh.hullVertices
        for (v in vertices) {
            if (v[1] >= drop) points.add(f.project(v[0], v[1] - drop, v[2], yaw))
        }
        for (edge in CrystalTrophyMesh.hullEdges) {
            val a = vertices[edge[0].toInt()]
            val b = vertices[edge[1].toInt()]
            if ((a[1] - drop) * (b[1] - drop) < 0) {
                val t = (drop - a[1]) / (b[1] - a[1])
                points.add(f.project(a[0] + (b[0] - a[0]) * t, 0.0, a[2] + (b[2] - a[2]) * t, yaw))
            }
        }
        points.sortWith(compareBy<DoubleArray> { it[0] }.thenBy { it[1] })
        val hull = mutableListOf<DoubleArray>()
        repeat(2) { side ->
            val start = hull.size
            for (i in points.indices) {
                val p = points[if (side == 0) i else points.size - 1 - i]
                while (hull.size >= start + 2) {
                    val a = hull[hull.size - 2]
                    val b = hull.last()
                    if ((b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) > 1e-7) break
                    hull.removeAt(hull.lastIndex)
                }
                hull.add(p)
            }
            if (hull.isNotEmpty()) hull.removeAt(hull.lastIndex)
        }
        return FloatArray(128) { index ->
            val i = index / 4
            if (i >= hull.size) -10000f else hull[(i + index % 4 / 2) % hull.size][index % 2].toFloat()
        }
    }

    private val sites = arrayOf(
        doubleArrayOf(0.05, 2.90, 0.17, 0.0), doubleArrayOf(-0.74, 2.52, 0.15, -0.045),
        doubleArrayOf(0.86, 1.36, 0.04, 0.035), doubleArrayOf(-0.86, 1.34, 0.04, 0.075),
        doubleArrayOf(0.329, 0.967, 0.209, -0.025), doubleArrayOf(-0.329, 0.967, 0.209, 0.015),
        doubleArrayOf(0.0, 1.40, 0.285, -0.075), doubleArrayOf(0.0, 0.60, 0.1442, 0.055),
    )

    fun sparkles(s: CrystalTrophyState, f: CrystalTrophyFrame): FloatArray {
        val result = FloatArray(32)
        val drop = (1 - s.rise) * 2.96
        for ((i, site) in sites.withIndex()) {
            val alignment = cos(s.yaw) * cos(site[3]) + sin(s.yaw) * sin(site[3])
            var gain = max(0.0, alignment).pow(if (i == 0) 95 else 2600) * (if (i == 0) 1.1 else 3.0)
            if (i == 0) gain += s.flash * 1.8
            val p = f.project(site[0], site[1] - drop, site[2], s.yaw)
            result[i * 4] = p[0].toFloat()
            result[i * 4 + 1] = p[1].toFloat()
            result[i * 4 + 2] = (if (site[1] - drop > 0.01) gain else 0.0).toFloat()
            result[i * 4 + 3] = ((if (i == 0) 17 * (1 + s.flash) else 5.2) * f.unit / 108).toFloat()
        }
        return result
    }

    fun flecks(f: CrystalTrophyFrame, yaw: Double): FloatArray {
        val result = FloatArray(48)
        val normLamp = sqrt(0.16 * 0.16 + 0.9 * 0.9 + 0.4 * 0.4)
        val lx = 0.16 / normLamp
        val ly = 0.9 / normLamp
        val lz = 0.4 / normLamp
        for (i in 0 until 12) {
            val floor = i >= 8
            val a = i * 2.399963 + 0.4
            val nx0 = sin(a) * (if (floor) 0.91 else 0.56)
            val nz0 = cos(a) * (if (floor) 0.91 else 0.56)
            val ny0 = if (floor) -0.42 else 0.83
            val norm = sqrt(nx0 * nx0 + ny0 * ny0 + nz0 * nz0)
            val nx = (nx0 * cos(yaw) + nz0 * sin(yaw)) / norm
            val nz = (-nx0 * sin(yaw) + nz0 * cos(yaw)) / norm
            val ny = ny0 / norm
            val incidence = lx * nx + ly * ny + lz * nz
            var rx = -lx + 2 * incidence * nx
            var ry = -ly + 2 * incidence * ny
            var rz = -lz + 2 * incidence * nz
            if (floor) {
                val eta = 1 / 1.46
                val c = abs(incidence)
                val bend = eta * c - sqrt(max(0.0, 1 - eta * eta * (1 - c * c)))
                rx = -eta * lx + bend * nx
                ry = -abs(-eta * ly + bend * ny)
                rz = abs(-eta * lz + bend * nz)
            }
            val sourceX = sin(a) * 0.32
            val sourceY = if (floor) 0.72 else 0.65 + (i % 3) * 0.21
            var px: Double
            val py: Double
            var energy: Double
            if (floor) {
                val t = sourceY / max(0.12, -ry)
                val side = if (i % 2 == 0) -1 else 1
                px = f.originX + (side * (0.94 + 0.23) + rx * t * 0.15) * f.unit
                py = f.originY + 32 * f.k + rz * t * f.unit * 0.08
                energy = 0.48 + 0.18 * abs(incidence)
            } else {
                val t = 1.15 / max(0.12, -rz)
                px = f.originX + (sourceX + rx * t) * f.unit
                py = f.originY - (sourceY + ry * t) * f.unit
                energy = ((-rz - 0.03) * 4).coerceIn(0.0, 1.0) * max(0.0, incidence)
                energy *= ((py - f.height * 0.32) / 55).coerceIn(0.0, 1.0)
                energy *= ((f.originY - py) / 25).coerceIn(0.0, 1.0)
                if (i == 4) px -= 10 * f.k
                if (i == 6) px += 10 * f.k
            }
            val turn = min(1.0, sin(yaw) * sin(yaw) * 12)
            if (i != 4 && i != 6 && i != 9 && i != 10) energy *= turn
            energy *= 1 + 0.35 * turn
            val sideFade = (min(px, f.width - px) / 38).coerceIn(0.0, 1.0)
            result[i * 4] = px.toFloat()
            result[i * 4 + 1] = py.toFloat()
            result[i * 4 + 2] = (a + atan2(rx, max(0.1, abs(ry))) * 0.4).toFloat()
            result[i * 4 + 3] = (energy * sideFade).toFloat()
        }
        return result
    }
}
