package com.ngocdevv.nativecook.blocks.pickers.dicerollpicker

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Dice Roll Picker — 3D math and the analytic tumble. World: x right, y away from the player across
 the table, z up; the felt is the plane z = 0, the table's centre the origin, units in points.
 A die is a rounded cube (edge EDGE, corner radius ROUND): an inner cube of half size HALF − ROUND
 dilated by ROUND. Its orientation is a row-major 3×3 matrix whose columns are its local axes.

 Faces: +Z 1, −Z 6, +X 3, −X 4, +Y 2, −Y 5 (opposite faces add up to 7).

 A throw is closed-form (no simulation): the path runs through a first landing, a bounce off the
 far rim and on to the resting spot; the height is three parabolic hops; the orientation is
 R(s) = Rw(w, 2πk·(1 − e)) · R1 · Rl(a, φ0·(1 − e)), e = ease-out of the time. Whole turns are the
 identity, so s = 0 is R0 and s = end is R1 exactly.
 */

internal object DiceMath {
    const val EDGE = 66.0
    const val HALF = EDGE / 2
    const val ROUND = EDGE * 0.15
    const val INNER = HALF - ROUND

    /** Camera: elevation above the table and distance (pt, also the focal length). */
    private val ELEV = 60 * PI / 180
    private val SIN_E = sin(ELEV)
    private val COS_E = cos(ELEV)
    const val CAM_D = 1100.0
    /** Unit vector from a point to the light (up, left, far). */
    val TO_LIGHT = doubleArrayOf(-0.33, 0.42, 0.845)

    const val TX0 = -170.0
    const val TX1 = 170.0
    const val TY0 = -130.0
    const val TY1 = 175.0
    const val RIM = 16.0
    const val WALL = 16.0

    fun clamp(v: Double, a: Double, b: Double) = min(max(v, a), b)

    /** A seeded value in [0, 1). */
    fun hash(i: Double): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a), 0.0, 1.0)
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    fun mul(a: DoubleArray, b: DoubleArray): DoubleArray {
        val r = DoubleArray(9)
        for (i in 0 until 3) for (j in 0 until 3) r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
        return r
    }

    fun transpose(a: DoubleArray) = doubleArrayOf(a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8])

    /** Rotation about a unit axis by an angle (radians). */
    fun axisAngle(x: Double, y: Double, z: Double, ang: Double): DoubleArray {
        val c = cos(ang)
        val s = sin(ang)
        val t = 1 - c
        return doubleArrayOf(
            t * x * x + c, t * x * y - s * z, t * x * z + s * y,
            t * x * y + s * z, t * y * y + c, t * y * z - s * x,
            t * x * z - s * y, t * y * z + s * x, t * z * z + c,
        )
    }

    fun axisAngle(ax: DoubleArray, ang: Double) = axisAngle(ax[0], ax[1], ax[2], ang)

    /** The axis (first three) and angle (fourth) of a rotation matrix, robust near 0 and π. */
    fun toAxisAngle(m: DoubleArray): DoubleArray {
        val c = clamp((m[0] + m[4] + m[8] - 1) / 2, -1.0, 1.0)
        val ang = acos(c)
        if (ang < 1e-4) return doubleArrayOf(0.0, 0.0, 1.0, 0.0)
        if (PI - ang < 1e-3) {
            val xx = sqrt(max(0.0, (m[0] + 1) / 2))
            val yy = sqrt(max(0.0, (m[4] + 1) / 2))
            val zz = sqrt(max(0.0, (m[8] + 1) / 2))
            if (xx >= yy && xx >= zz) return doubleArrayOf(xx, m[1] / (2 * xx), m[2] / (2 * xx), ang)
            if (yy >= zz) return doubleArrayOf(m[1] / (2 * yy), yy, m[5] / (2 * yy), ang)
            return doubleArrayOf(m[2] / (2 * zz), m[5] / (2 * zz), zz, ang)
        }
        val s = 2 * sin(ang)
        return doubleArrayOf((m[7] - m[5]) / s, (m[2] - m[6]) / s, (m[3] - m[1]) / s, ang)
    }

    /** Orientation showing `face` on top, turned by `yaw` (radians) about the vertical. */
    fun faceUp(face: Int, yaw: Double): DoubleArray {
        val h = PI / 2
        val base = when (face) {
            1 -> doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
            6 -> axisAngle(1.0, 0.0, 0.0, PI)
            3 -> axisAngle(0.0, 1.0, 0.0, -h)
            4 -> axisAngle(0.0, 1.0, 0.0, h)
            2 -> axisAngle(1.0, 0.0, 0.0, h)
            else -> axisAngle(1.0, 0.0, 0.0, -h)
        }
        return mul(axisAngle(0.0, 0.0, 1.0, yaw), base)
    }

    /** m · v into out. */
    fun apply(m: DoubleArray, x: Double, y: Double, z: Double, out: DoubleArray) {
        out[0] = m[0] * x + m[1] * y + m[2] * z
        out[1] = m[3] * x + m[4] * y + m[5] * z
        out[2] = m[6] * x + m[7] * y + m[8] * z
    }

    /** The face on top of an orientation. */
    fun topFace(m: DoubleArray): Int {
        val faces = intArrayOf(3, 2, 1)
        val opposite = intArrayOf(4, 5, 6)
        var best = 0.0
        var face = 1
        for (k in 0 until 3) {
            val z = m[6 + k]
            if (abs(z) > best) {
                best = abs(z)
                face = if (z > 0) faces[k] else opposite[k]
            }
        }
        return face
    }

    /** The orientation nearest to `m` with a face flat on the table. */
    fun snapFlat(m: DoubleArray): DoubleArray {
        var k = 0
        for (i in 1 until 3) if (abs(m[6 + i]) > abs(m[6 + k])) k = i
        val s = if (m[6 + k] > 0) 1.0 else -1.0
        val n0 = m[k] * s
        val n1 = m[3 + k] * s
        val n2 = m[6 + k] * s
        val len = hypot(n1, n0)
        if (len < 1e-5) return m
        val ang = acos(clamp(n2, -1.0, 1.0))
        return mul(axisAngle(n1 / len, -n0 / len, 0.0, ang), m)
    }

    /** Height of the centre above the felt when the die rests on its lowest point. */
    fun support(m: DoubleArray) = (abs(m[6]) + abs(m[7]) + abs(m[8])) * INNER + ROUND

    /** Screen point of a world point into out: [x, y, perspective scale]. */
    fun project(cx: Double, cy: Double, x: Double, y: Double, z: Double, out: DoubleArray) {
        val yc = y * SIN_E + z * COS_E
        val dc = CAM_D + y * COS_E - z * SIN_E
        val s = CAM_D / dc
        out[0] = cx + x * s
        out[1] = cy - yc * s
        out[2] = s
    }

    /** Table-plane point under a screen point at height z (inverse of project). */
    fun unproject(cx: Double, cy: Double, sx: Double, sy: Double, z: Double): DoubleArray {
        val k = (cy - sy) / CAM_D
        val y = (k * (CAM_D - z * SIN_E) - z * COS_E) / (SIN_E - k * COS_E)
        val dc = CAM_D + y * COS_E - z * SIN_E
        return doubleArrayOf((sx - cx) * dc / CAM_D, y)
    }

    /** Direction from a world point toward the camera, into out. */
    fun toCamera(x: Double, y: Double, z: Double, out: DoubleArray) {
        val dx = -x
        val dy = -CAM_D * COS_E - y
        val dz = CAM_D * SIN_E - z
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        out[0] = dx / l
        out[1] = dy / l
        out[2] = dz / l
    }

    const val THROW_END = 2.2

    fun makeThrow(r0: DoubleArray, r1: DoubleArray, p0x: Double, p0y: Double, p1x: Double, p1y: Double, h0: Double, k: Int, rimY: Double): DiceThrow {
        val aa = toAxisAngle(mul(transpose(r1), r0))
        val dx = p1x - p0x
        val dy = rimY - p0y
        val l = hypot(dx, dy).takeIf { it != 0.0 } ?: 1.0
        // Rolls forward toward the rim: spin axis −(z × direction), turned the other way by the decay.
        return DiceThrow(r0, r1, p0x, p0y, p1x, p1y, h0, k, doubleArrayOf(dy / l, -dx / l, 0.0), doubleArrayOf(aa[0], aa[1], aa[2]), aa[3], rimY)
    }

    private fun hopArc(u: Double, a: Double, b: Double, h0: Double, peak: Double): Double {
        val x = seg(u, a, b)
        return h0 * (1 - x) + 4 * peak * x * (1 - x)
    }

    fun throwPose(th: DiceThrow, s: Double): DicePose {
        // Path: start → first landing (0.55 s) → far rim (0.85 s) → rest (1.8 s, easing out).
        val side = if (th.p1x >= 0) 1 else -1
        val landX = mix(th.p0x, th.p1x, 0.55) + side * 10
        val landY = mix(th.p0y, th.rimY, 0.7)
        val rimX = mix(th.p0x, th.p1x, 0.8) + side * 20
        val rimY = th.rimY
        val x: Double
        val y: Double
        if (s < 0.55) {
            val u = s / 0.55
            x = mix(th.p0x, landX, u)
            y = mix(th.p0y, landY, u)
        } else if (s < 0.85) {
            val u = (s - 0.55) / 0.3
            x = mix(landX, rimX, u)
            y = mix(landY, rimY, u)
        } else {
            val u = easeOut(seg(s, 0.85, 1.8))
            x = mix(rimX, th.p1x, u)
            y = mix(rimY, th.p1y, u)
        }
        val hop = when {
            s < 0.55 -> hopArc(s, 0.0, 0.55, th.h0, 96.0)
            s < 1.0 -> hopArc(s, 0.55, 1.0, 0.0, 34.0)
            s < 1.28 -> hopArc(s, 1.0, 1.28, 0.0, 10.0)
            else -> 0.0
        }
        val e = easeOut(seg(s, 0.0, 1.85))
        var m = mul(mul(axisAngle(th.w, 2 * PI * th.k * (1 - e)), th.r1), axisAngle(th.a, th.phi * (1 - e)))
        if (s > 1.85) {
            // Rock on the face after the spin: two damped wobbles about the roll axis.
            val r = s - 1.85
            val rock = 0.11 * sin(2 * PI * r / 0.2) * (1 - seg(r, 0.0, THROW_END - 1.85)).pow(2)
            m = mul(axisAngle(th.w, rock), m)
        }
        return DicePose(m, x, y, hop)
    }

    /** A slide home from (R1, p1) to (R0, p0): tips over as it turns, small hop. */
    fun slidePose(r1: DoubleArray, r0: DoubleArray, p1x: Double, p1y: Double, p0x: Double, p0y: Double, u: Double): DicePose {
        val e = easeInOut(clamp(u, 0.0, 1.0))
        val b = toAxisAngle(mul(transpose(r0), r1))
        return DicePose(mul(r0, axisAngle(b[0], b[1], b[2], b[3] * (1 - e))), mix(p1x, p0x, e), mix(p1y, p0y, e), 8 * sin(PI * e))
    }
}

/** A die's pose: orientation, table position, hop height (above its support). */
internal class DicePose(val m: DoubleArray, val x: Double, val y: Double, val hop: Double)

/** A throw from (R0, p0, starting hop h0) to (R1, p1), `k` whole turns, the far rim at rimY. */
internal class DiceThrow(
    val r0: DoubleArray,
    val r1: DoubleArray,
    val p0x: Double,
    val p0y: Double,
    val p1x: Double,
    val p1y: Double,
    val h0: Double,
    val k: Int,
    val w: DoubleArray,
    val a: DoubleArray,
    val phi: Double,
    val rimY: Double,
)
