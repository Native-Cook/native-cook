package com.ngocdevv.nativecook.blocks.animations.prismspheregenerating

import kotlin.math.*

internal data class PrismPose(val rotation: DoubleArray, val fan: Double) {
    companion object {
        private val keys = doubleArrayOf(
            0.29652, -0.71587, 0.24191, 0.58403, 0.21481, -0.19785, 0.51165, 0.80804,
            0.28132, -0.02138, 0.83629, 0.47013, 0.49844, -0.04916, 0.86135, -0.08496,
            0.61271, 0.25379, 0.69147, -0.28642, 0.66980, 0.14530, 0.61236, -0.39404,
            0.69015, -0.15393, 0.37917, -0.59685, 0.86123, -0.20013, 0.43839, -0.16136,
            0.73369, -0.22904, 0.62698, -0.12700, 0.74419, -0.21550, 0.63197, 0.01908,
        )
        private val fans = doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.35, 1.975, 1.0, 1.0, 1.0)

        private fun spline(a: Double, b: Double, c: Double, d: Double, u: Double) =
            b + 0.5 * u * (c - a + u * (2 * a - 5 * b + 4 * c - d + u * (3 * (b - c) + d - a)))

        fun at(phase: Double): PrismPose {
            val at = (phase - floor(phase)) * 10
            val index = min(9, floor(at).toInt())
            val u = at - index
            val i0 = (index + 9) % 10
            val i2 = (index + 1) % 10
            val i3 = (index + 2) % 10
            val q = DoubleArray(4) { j -> spline(keys[i0 * 4 + j], keys[index * 4 + j], keys[i2 * 4 + j], keys[i3 * 4 + j], u) }
            val n = 1 / sqrt(q.sumOf { it * it })
            val x = q[0] * n
            val y = q[1] * n
            val z = q[2] * n
            val w = q[3] * n
            val fan = PI * spline(fans[i0], fans[index], fans[i2], fans[i3], u).coerceIn(1.0, 1.975)
            val turned = doubleArrayOf(
                1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
                2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
                2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y),
            )
            return PrismPose(PrismMath.multiply(turned, PrismMath.axisAngle(0.0, 1.0, 0.0, (fan - PI) / 2)), fan)
        }
    }
}
