package com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge

import com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge.FlipFocusBadgeTheme as Theme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The badge's 3D bookkeeping: rotations, the ring's frame, the digits' layout and the uniform block the shader reads. Port of RN `ffb-geometry.ts`.
 * World: x right, y up, z toward the viewer, origin = the plate's centre (design pt). Matrices are row-major 3x3 [DoubleArray].
 */
internal object FlipFocusBadgeGeometry {
    const val D2R = PI / 180

    fun rotX(a: Double): DoubleArray {
        val c = cos(a)
        val s = sin(a)
        return doubleArrayOf(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c)
    }

    fun rotY(a: Double): DoubleArray {
        val c = cos(a)
        val s = sin(a)
        return doubleArrayOf(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c)
    }

    fun rotZ(a: Double): DoubleArray {
        val c = cos(a)
        val s = sin(a)
        return doubleArrayOf(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0)
    }

    fun mul(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[0] * b[0] + a[1] * b[3] + a[2] * b[6], a[0] * b[1] + a[1] * b[4] + a[2] * b[7], a[0] * b[2] + a[1] * b[5] + a[2] * b[8],
        a[3] * b[0] + a[4] * b[3] + a[5] * b[6], a[3] * b[1] + a[4] * b[4] + a[5] * b[7], a[3] * b[2] + a[4] * b[5] + a[5] * b[8],
        a[6] * b[0] + a[7] * b[3] + a[8] * b[6], a[6] * b[1] + a[7] * b[4] + a[8] * b[7], a[6] * b[2] + a[7] * b[5] + a[8] * b[8],
    )

    fun mulV(m: DoubleArray, x: Double, y: Double, z: Double) =
        doubleArrayOf(m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z)

    fun transpose(m: DoubleArray) = doubleArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8])

    private fun row(m: DoubleArray, i: Int) = floatArrayOf(m[i * 3].toFloat(), m[i * 3 + 1].toFloat(), m[i * 3 + 2].toFloat())

    // the ring's arc: tail and head angles in the fitted frame, the arc's centre, and the shift that puts the centre on +y
    private const val TAIL = Theme.RING_TAIL_DEG
    private const val HEAD = Theme.RING_HEAD_DEG + 360
    private const val ARC_CENTRE = (TAIL + HEAD) / 2
    private const val ARC_SHIFT = 90 - ARC_CENTRE

    /** the ring's local frame matrices; `wr` ring -> world, `lr` local' -> plate frame. Angles in degrees. */
    private class RingBasis(val wr: DoubleArray, val lr: DoubleArray)

    private fun ringBasis(yawRing: Double, pitch: Double): RingBasis {
        val extra = -0.28 * pitch * D2R
        val wr = mul(rotX(pitch * D2R), mul(rotY(yawRing * D2R), rotX(extra)))
        val lr = mul(rotZ(Theme.RING_ROLL * D2R), mul(rotX(Theme.RING_ALPHA * D2R), rotZ(-ARC_SHIFT * D2R)))
        return RingBasis(wr, lr)
    }

    /** world position of the ring's point at local angle `tDeg` */
    fun ringPoint(tDeg: Double, yawRing: Double, pitch: Double): DoubleArray {
        val rb = ringBasis(yawRing, pitch)
        val t = tDeg * D2R
        val p = mulV(rb.lr, Theme.RING_R * cos(t + ARC_SHIFT * D2R), Theme.RING_R * sin(t + ARC_SHIFT * D2R), 0.0)
        return mulV(rb.wr, p[0] + Theme.RING_CX, p[1] + Theme.RING_CY, p[2])
    }

    /** the front numeral's cells: tens, ones (-1 = none), their origins x, glyph scale, baseline */
    fun frontLayout(value: Double): DoubleArray {
        val v = max(0, min(99, Math.round(value).toInt()))
        val tens = v / 10
        val ones = v % 10
        val adv = Theme.NUM_ADVANCE
        val fit = 124.0
        if (v < 10) {
            val w = Theme.INK_R[ones] - Theme.INK_L[ones]
            val ns = min(1.0, fit / w)
            val x = (-w * ns) / 2 + Theme.NUM_PITCH_LEFT - Theme.INK_L[ones] * ns
            return doubleArrayOf(-1.0, ones.toDouble(), 0.0, x, ns, Theme.NUM_BASE_Y + ((1 - ns) * Theme.NUM_CAP) / 2)
        }
        val wt = Theme.INK_R[tens] - Theme.INK_L[tens]
        val wo = Theme.INK_R[ones] - Theme.INK_L[ones]
        val total = wt * adv + wo
        val ns = min(1.0, fit / total)
        val left = (-total * ns) / 2 + Theme.NUM_PITCH_LEFT
        val xt = left - Theme.INK_L[tens] * ns
        val xo = left + wt * adv * ns - Theme.INK_L[ones] * ns
        return doubleArrayOf(tens.toDouble(), ones.toDouble(), xt, xo, ns, Theme.NUM_BASE_Y + ((1 - ns) * Theme.NUM_CAP) / 2)
    }

    const val CLOCK_SCALE = 0.38
    private const val CLOCK_ADV = 0.92

    /** the back's clock "mm:ss": four cells, their origins, the colon's x last */
    fun backLayout(secs: Double): DoubleArray {
        val total = max(0, min(5999, floor(secs).toInt()))
        val m = total / 60
        val s = total % 60
        val cells = intArrayOf(m / 10, m % 10, s / 10, s % 10)
        val adv = CLOCK_ADV
        val k = CLOCK_SCALE
        val widths = DoubleArray(4) { (Theme.INK_R[cells[it]] - Theme.INK_L[cells[it]]) * k }
        val colon = 8.0
        val total2 = widths[0] * adv + widths[1] * adv + colon + widths[2] * adv + widths[3]
        var x = -total2 / 2
        val xs = DoubleArray(4)
        xs[0] = x - Theme.INK_L[cells[0]] * k
        x += widths[0] * adv
        xs[1] = x - Theme.INK_L[cells[1]] * k
        x += widths[1] * adv
        val colonX = x + colon / 2
        x += colon
        xs[2] = x - Theme.INK_L[cells[2]] * k
        x += widths[2] * adv
        xs[3] = x - Theme.INK_L[cells[3]] * k
        return doubleArrayOf(cells[0].toDouble(), cells[1].toDouble(), cells[2].toDouble(), cells[3].toDouble(), xs[0], xs[1], xs[2], xs[3], colonX)
    }

    /** the badge's state (RN `BadgeState`) */
    class BadgeState {
        var yaw = 0.0
        var pitch = 0.0
        var ringYaw = 0.0
        var depth = 1.0
        var value = 10.0
        var clockSecs = 0.0
        var sat = 0.4
        var glintPlus = 0.0
        var glintHead = 0.0
        var glintSat = 0.0
        var envYaw = 0.0
        var scale = 1.0
        var popPlus = 1.0
        var popHr = 1.0

        fun fromLook(l: FlipFocusBadgeLook): BadgeState {
            yaw = l.yaw; pitch = l.pitch; ringYaw = l.ringYaw; depth = l.depth; value = l.value; clockSecs = l.clock; sat = l.sat
            glintPlus = l.glintPlus; glintHead = l.glintHead; glintSat = l.glintSat; envYaw = l.env; scale = l.press; popPlus = l.popPlus; popHr = l.popHr
            return this
        }
    }

    /** The uniforms the badge shader reads, except the canvas's own (uC, uS, uPx, uAux) and the atlas child. */
    class Uniforms {
        var uBx = FloatArray(3); var uBy = FloatArray(3); var uBz = FloatArray(3)
        var uRx = FloatArray(3); var uRy = FloatArray(3); var uRz = FloatArray(3)
        var uRc = FloatArray(3); var uSpH = FloatArray(3); var uSpS = FloatArray(3)
        var uFront = FloatArray(4); var uNum = FloatArray(2); var uBack = FloatArray(4); var uBackX = FloatArray(4)
        var uGlint = FloatArray(4); var uMat = FloatArray(4); var uPop = FloatArray(2)
    }

    fun uniforms(b: BadgeState, o: Uniforms = Uniforms()): Uniforms {
        // plate: world -> body = Ry(-yaw) . Rx(-pitch)
        val wb = mul(rotX(b.pitch * D2R), rotY(b.yaw * D2R))
        val toBody = transpose(wb)
        // ring: local' = M . (pre . world - c); the shader subtracts uRc = M . c after the product
        val rb = ringBasis(b.ringYaw, b.pitch)
        val pre = transpose(rb.wr)
        val inv = transpose(rb.lr)
        val toRing = mul(inv, pre)
        val c = mulV(inv, Theme.RING_CX, Theme.RING_CY, 0.0)
        val sparkHead = ringPoint(Theme.RING_HEAD_DEG, b.ringYaw, b.pitch)
        val satDeg = Theme.RING_SAT_REST_DEG + 360 * (b.sat - 0.4)
        val sparkSat = ringPoint(satDeg, b.ringYaw, b.pitch)
        val front = frontLayout(b.value)
        val back = backLayout(b.clockSecs)
        o.uBx = row(toBody, 0); o.uBy = row(toBody, 1); o.uBz = row(toBody, 2)
        o.uRx = row(toRing, 0); o.uRy = row(toRing, 1); o.uRz = row(toRing, 2)
        o.uRc = floatArrayOf(c[0].toFloat(), c[1].toFloat(), c[2].toFloat())
        o.uSpH = floatArrayOf(sparkHead[0].toFloat(), sparkHead[1].toFloat(), sparkHead[2].toFloat())
        o.uSpS = floatArrayOf(sparkSat[0].toFloat(), sparkSat[1].toFloat(), sparkSat[2].toFloat())
        o.uFront = floatArrayOf(front[0].toFloat(), front[1].toFloat(), front[2].toFloat(), front[3].toFloat())
        o.uNum = floatArrayOf(front[4].toFloat(), front[5].toFloat())
        o.uBack = floatArrayOf(back[0].toFloat(), back[1].toFloat(), back[2].toFloat(), back[3].toFloat())
        o.uBackX = floatArrayOf(back[4].toFloat(), back[5].toFloat(), back[6].toFloat(), back[7].toFloat())
        // the 4th carries the colon's x
        o.uGlint = floatArrayOf(b.glintPlus.toFloat(), b.glintHead.toFloat(), b.glintSat.toFloat(), back[8].toFloat())
        o.uMat = floatArrayOf(b.depth.toFloat(), 0.22f, b.envYaw.toFloat(), b.scale.toFloat())
        o.uPop = floatArrayOf(b.popPlus.toFloat(), b.popHr.toFloat())
        return o
    }
}
