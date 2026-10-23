package com.ngocdevv.nativecook.blocks.animations.prismspheregenerating

import kotlin.math.*

internal object PrismMath {
    fun identity() = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

    fun multiply(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i ->
        val row = i / 3 * 3
        val col = i % 3
        a[row] * b[col] + a[row + 1] * b[col + 3] + a[row + 2] * b[col + 6]
    }

    fun axisAngle(x: Double, y: Double, z: Double, angle: Double): DoubleArray {
        val n = sqrt(x * x + y * y + z * z)
        if (n < 1e-9 || angle == 0.0) return identity()
        val ax = x / n
        val ay = y / n
        val az = z / n
        val c = cos(angle)
        val s = sin(angle)
        val t = 1 - c
        return doubleArrayOf(
            t * ax * ax + c, t * ax * ay - s * az, t * ax * az + s * ay,
            t * ax * ay + s * az, t * ay * ay + c, t * ay * az - s * ax,
            t * ax * az - s * ay, t * ay * az + s * ax, t * az * az + c,
        )
    }

    fun orthonormalise(m: DoubleArray): DoubleArray {
        val n = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]).takeIf { it != 0.0 } ?: 1.0
        val ax = m[0] / n
        val ay = m[1] / n
        val az = m[2] / n
        val d = ax * m[3] + ay * m[4] + az * m[5]
        var bx = m[3] - d * ax
        var by = m[4] - d * ay
        var bz = m[5] - d * az
        val bn = sqrt(bx * bx + by * by + bz * bz).takeIf { it != 0.0 } ?: 1.0
        bx /= bn
        by /= bn
        bz /= bn
        return doubleArrayOf(ax, ay, az, bx, by, bz, ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
    }

    fun trackball(offset: DoubleArray, dx: Double, dy: Double, radius: Double) =
        orthonormalise(multiply(axisAngle(-dy, dx, 0.0, hypot(dx, dy) / radius), offset))

    fun transpose(m: DoubleArray) = DoubleArray(9) { m[it % 3 * 3 + it / 3] }

    fun returnToScript(offset: DoubleArray, dt: Double): DoubleArray {
        val angle = acos(((offset[0] + offset[4] + offset[8] - 1) / 2).coerceIn(-1.0, 1.0))
        val next = angle * exp(-dt / 0.35)
        if (next < 0.001) return identity()
        var x = offset[7] - offset[5]
        var y = offset[2] - offset[6]
        var z = offset[3] - offset[1]
        if (PI - angle < 0.0001) {
            if (offset[0] >= offset[4] && offset[0] >= offset[8]) {
                x = sqrt(max(0.0, (offset[0] + 1) / 2))
                y = (offset[1] + offset[3]) / (4 * x)
                z = (offset[2] + offset[6]) / (4 * x)
            } else if (offset[4] >= offset[8]) {
                y = sqrt(max(0.0, (offset[4] + 1) / 2))
                x = (offset[1] + offset[3]) / (4 * y)
                z = (offset[5] + offset[7]) / (4 * y)
            } else {
                z = sqrt(max(0.0, (offset[8] + 1) / 2))
                x = (offset[2] + offset[6]) / (4 * z)
                y = (offset[5] + offset[7]) / (4 * z)
            }
        }
        return axisAngle(x, y, z, next)
    }
}
