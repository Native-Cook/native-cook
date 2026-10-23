package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The stamp's matrix (stampMatrix in StampDiaryCalendar.tsx) as a 3x3 homography from the stamp's
 * own centred points to reference points: scale, spin, tilt with perspective, shear, translate.
 */
internal class StampDiaryCalendarProjection(
    cx: Double,
    cy: Double,
    skew: Double,
    rotX: Double,
    rotY: Double,
    rotZ: Double,
    sx: Double,
    sy: Double,
    depth: Double,
) {
    /** Row-major 3x3 mapping (x, y, 1) of the stamp to (X, Y, W) in reference points. */
    private val m: DoubleArray

    init {
        val d2r = PI / 180
        val ax = rotX * d2r
        val ay = rotY * d2r
        val az = rotZ * d2r
        val cX = cos(ax)
        val sX = sin(ax)
        val cY = cos(ay)
        val sY = sin(ay)
        val cZ = cos(az)
        val sZ = sin(az)
        // R = Rx . Ry . Rz applied to (x, y, 0); then perspective; then shear; then translate.
        val r00 = cY * cZ
        val r01 = -cY * sZ
        val r10 = sX * sY * cZ + cX * sZ
        val r11 = -sX * sY * sZ + cX * cZ
        val r20 = -cX * sY * cZ + sX * sZ
        val r21 = cX * sY * sZ + sX * cZ
        val a = r00 * sx
        val b = r01 * sy
        val c = r10 * sx
        val e = r11 * sy
        val g = r20 * sx
        val h = r21 * sy
        // Perspective: w = 1 - z / depth (z towards the viewer is +).
        val pz0 = -g / depth
        val pz1 = -h / depth
        m = doubleArrayOf(
            a + cx * pz0, b + cx * pz1, cx,
            c + skew * a + cy * pz0, e + skew * b + cy * pz1, cy,
            pz0, pz1, 1.0,
        )
    }

    fun point(x: Double, y: Double): DoubleArray {
        val w = m[6] * x + m[7] * y + m[8]
        return doubleArrayOf((m[0] * x + m[1] * y + m[2]) / w, (m[3] * x + m[4] * y + m[5]) / w)
    }

    /** Rows of the inverse (reference -> stamp), nine floats row-major. */
    fun inverse(): FloatArray {
        val a = m[0]
        val b = m[1]
        val c = m[2]
        val d = m[3]
        val e = m[4]
        val f = m[5]
        val g = m[6]
        val h = m[7]
        val i = m[8]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        val s = if (abs(det) < 1e-12) 0.0 else 1 / det
        return floatArrayOf(
            ((e * i - f * h) * s).toFloat(), ((c * h - b * i) * s).toFloat(), ((b * f - c * e) * s).toFloat(),
            ((f * g - d * i) * s).toFloat(), ((a * i - c * g) * s).toFloat(), ((c * d - a * f) * s).toFloat(),
            ((d * h - e * g) * s).toFloat(), ((b * g - a * h) * s).toFloat(), ((a * e - b * d) * s).toFloat(),
        )
    }

    /** Bounding box [minX, minY, maxX, maxY] in reference points of a stamp-space rectangle centred on the origin. */
    fun bounds(halfWidth: Double, halfHeight: Double): DoubleArray {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for ((sx, sy) in listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0)) {
            val p = point(sx * halfWidth, sy * halfHeight)
            minX = min(minX, p[0])
            maxX = max(maxX, p[0])
            minY = min(minY, p[1])
            maxY = max(maxY, p[1])
        }
        return doubleArrayOf(minX, minY, maxX, maxY)
    }

    /** The four corners of the stamp rectangle in reference points, clockwise from the top left. */
    fun corners(halfWidth: Double, halfHeight: Double): List<DoubleArray> =
        listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0).map { (sx, sy) -> point(sx * halfWidth, sy * halfHeight) }

    companion object {
        fun quad(q: StampDiaryCalendarQuad) = StampDiaryCalendarProjection(
            q.x + q.w / 2, q.y + q.h / 2 + q.dy / 2, q.dy / q.w, 0.0, 0.0, 0.0, 1.0, 1.0, 1e6,
        )
    }
}
