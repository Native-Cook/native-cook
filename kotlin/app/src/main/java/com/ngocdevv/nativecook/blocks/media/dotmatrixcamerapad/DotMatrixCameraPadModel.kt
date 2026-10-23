package com.ngocdevv.nativecook.blocks.media.dotmatrixcamerapad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 The numbers behind Dot Matrix Camera Pad (a port of DotMatrixCameraPadModel.swift; the numbers
 are the same): the controls' state, the autoPlay timeline, the layout and the night street the
 viewfinder shows.
 */

/** Everything the screen shows at a moment. */
internal data class DmcpState(
    /** Exposure compensation in stops. */
    val ev: Double = 0.0,
    /** White balance in kelvin. */
    val warmth: Double = 5200.0,
    /** Left module: the A/M knob (0 = A, 1 = M) and the wheel-to-pad morph (0 = wheel, 1 = pad). */
    val knob: Double = 0.0,
    val morph: Double = 0.0,
    /** Right module's A/M knob. */
    val knobR: Double = 0.0,
    /** The pad's hot spot (0…1, x = shutter from slow to fast, y = ISO from low to high) and its glow. */
    val px: Double = DmcpModel.REST_X,
    val py: Double = DmcpModel.REST_Y,
    val glow: Double = 0.0,
    /** Viewfinder zoom (0.72, 1, 1.7 for the three lenses). */
    val zoom: Double = 1.0,
    /** Shutter press (0…1), the capture blink (0…1) and the gallery thumbnail's scale. */
    val press: Double = 0.0,
    val flash: Double = 0.0,
    val pop: Double = 1.0,
) {
    companion object {
        fun mix(a: DmcpState, b: DmcpState, u: Double): DmcpState {
            fun l(x: Double, y: Double) = x + (y - x) * u
            return DmcpState(
                l(a.ev, b.ev), l(a.warmth, b.warmth), l(a.knob, b.knob), l(a.morph, b.morph), l(a.knobR, b.knobR),
                l(a.px, b.px), l(a.py, b.py), l(a.glow, b.glow), l(a.zoom, b.zoom), l(a.press, b.press),
                l(a.flash, b.flash), l(a.pop, b.pop),
            )
        }
    }
}

internal object DmcpModel {
    const val LOOP = 10.0
    val shutterLabels = listOf("1/4", "1/8", "1/15", "1/30", "1/60", "1/125", "1/250", "1/500", "1/1000", "1/2000", "1/4000")
    val isoLabels = listOf("100", "200", "400", "800", "1600", "3200", "6400", "12800")
    const val COLS = 11
    const val ROWS = 8
    /** 1/60 s and ISO 400. */
    const val REST_X = 0.4
    const val REST_Y = 2.0 / 7
    val lensLabels = listOf("13", "24", "77")
    val lensZooms = doubleArrayOf(0.72, 1.0, 1.7)

    /** Shutter time in seconds along the pad's x (log-spaced: 1/4 s at 0, 1/4000 s at 1). */
    fun shutter(x: Double) = 0.25 * 2.0.pow(-x * 10)
    fun iso(y: Double) = 100 * 2.0.pow(y * 7)
    private fun log2(x: Double) = ln(x) / ln(2.0)
    /** The picture's brightness in stops relative to the resting settings. */
    fun exposure(s: DmcpState) = s.ev + log2(iso(s.py) / 400) + log2(shutter(s.px) * 60)

    fun evLabel(ev: Double): String {
        val r = (ev * 10).roundToInt() / 10.0
        return (if (r < 0) "-" else "+") + "%.1f".format(abs(r))
    }

    // MARK: Easing

    fun clamp01(x: Double) = min(1.0, max(0.0, x))
    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    fun easeInOut(x: Double): Double {
        val u = clamp01(x)
        return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
    }
    fun easeOutBack(x: Double): Double {
        val u = clamp01(x)
        val c1 = 1.4
        val c3 = c1 + 1
        return 1 + c3 * (u - 1).pow(3) + c1 * (u - 1).pow(2)
    }
    private fun seg(t: Double, a: Double, b: Double) = easeInOut((t - a) / (b - a))

    // MARK: autoPlay

    /** The finger's path across the pad: (time, x, y), smoothed between keys. */
    private val path = listOf(
        doubleArrayOf(3.4, REST_X, REST_Y), doubleArrayOf(4.6, 0.1, 0.0), doubleArrayOf(5.0, 0.1, 0.0),
        doubleArrayOf(6.4, 0.9, 6.0 / 7), doubleArrayOf(7.2, REST_X, REST_Y),
    )

    fun state(time: Double): DmcpState {
        var t = time % LOOP
        if (t < 0) t += LOOP
        var px = REST_X
        var py = REST_Y
        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            if (t >= a[0] && t < b[0]) {
                val u = easeInOut((t - a[0]) / (b[0] - a[0]))
                // The long diagonal bows a little, like a thumb's arc.
                val bow = if (i == 2) 0.12 * sin(PI * u) else 0.0
                px = clamp01(a[1] + (b[1] - a[1]) * u + bow)
                py = clamp01(a[2] + (b[2] - a[2]) * u - bow)
            }
        }
        // The shot at 9.2 s.
        val d = t - 9.2
        return DmcpState(
            ev = 0.6 * seg(t, 0.6, 1.8) - 0.6 * seg(t, 8.2, 9.0),
            knob = seg(t, 2.3, 2.55) - seg(t, 7.5, 7.75),
            morph = seg(t, 2.4, 3.0) - seg(t, 7.6, 8.2),
            glow = seg(t, 3.1, 3.4) - seg(t, 7.2, 7.6),
            px = px,
            py = py,
            press = if (d >= 0 && d < 0.25) sin(PI * d / 0.25) else 0.0,
            flash = if (d >= 0.04 && d < 0.4) 0.85 * (1 - (d - 0.04) / 0.36).pow(2) else 0.0,
            pop = if (d >= 0.05 && d < 0.5) 0.82 + 0.18 * easeOutBack((d - 0.05) / 0.4) else 1.0,
        )
    }

    // MARK: Layout

    class Layout(val w: Double, val h: Double, val top: Double, val bottom: Double) {
        val viewfinder = Rect(16f, (top + 44).toFloat(), (w - 16).toFloat(), (h * 0.62).toFloat())
        val modulesY = viewfinder.bottom + 70.0

        /**
         * The left capsule grows from a 48 dp wheel to a 120 dp pad; each module (capsule, 10 dp gap,
         * 26 dp switch) stays centred in its half of the screen.
         */
        fun leftCapsule(morph: Double): Rect {
            val cw = 48 + 72 * clamp01(morph)
            val x = w / 4 - (cw + 36) / 2
            return Rect(x.toFloat(), modulesY.toFloat(), (x + cw).toFloat(), (modulesY + MODULE_HEIGHT).toFloat())
        }
        fun leftToggle(morph: Double): Rect {
            val x = leftCapsule(morph).right + 10
            return Rect(x, (modulesY + 14).toFloat(), x + 26, (modulesY + 72).toFloat())
        }
        val rightCapsule = Rect((w * 0.75 - 42).toFloat(), modulesY.toFloat(), (w * 0.75 + 6).toFloat(), (modulesY + MODULE_HEIGHT).toFloat())
        val rightToggle = Rect(rightCapsule.right + 10, (modulesY + 14).toFloat(), rightCapsule.right + 36, (modulesY + 72).toFloat())
        val readoutY = viewfinder.bottom + 22.0
        val shutterCenter = Offset((w / 2).toFloat(), (h - max(bottom, 16.0) - 24 - 32).toFloat())
        val gallery = Rect(24f, shutterCenter.y - 24, 72f, shutterCenter.y + 24)
        val chevron = Rect((w - 24 - 44).toFloat(), shutterCenter.y - 22, (w - 24).toFloat(), shutterCenter.y + 22)
        val chipsY = viewfinder.bottom - 38.0
        fun chipX(i: Int) = w / 2 + (i - 1.5) * 58

        companion object {
            const val MODULE_HEIGHT = 86.0
        }
    }

    // MARK: The night street

    /** A small integer hash in 0…1, the same in every language. */
    fun hash(a: Int, b: Int, c: Int): Double {
        var x = (a * 73_856_093) xor (b * 19_349_663) xor (c * 83_492_791)
        x = x xor (x ushr 13)
        x *= 1_274_126_177
        x = x xor (x ushr 16)
        return (x and 0xFFFF) / 65535.0
    }

    /** Building silhouettes: left edge, width, height above the horizon (fractions of the frame). */
    val buildings = listOf(
        doubleArrayOf(-0.02, 0.13, 0.30), doubleArrayOf(0.10, 0.10, 0.44), doubleArrayOf(0.19, 0.14, 0.26),
        doubleArrayOf(0.32, 0.09, 0.52), doubleArrayOf(0.40, 0.16, 0.34), doubleArrayOf(0.55, 0.11, 0.47),
        doubleArrayOf(0.65, 0.15, 0.30), doubleArrayOf(0.79, 0.10, 0.41), doubleArrayOf(0.88, 0.15, 0.28),
    )
    const val HORIZON = 0.66
    val lamps = listOf(doubleArrayOf(0.17, 0.56), doubleArrayOf(0.52, 0.53), doubleArrayOf(0.86, 0.57))
    /** Cars: lane (0 = far, heading right with headlights; 1 = near, heading left), crossings per loop, phase. */
    val cars = listOf(doubleArrayOf(0.0, 2.0, 0.0), doubleArrayOf(0.0, 3.0, 0.5), doubleArrayOf(1.0, 2.0, 0.25), doubleArrayOf(1.0, 3.0, 0.8))
    val laneY = doubleArrayOf(0.765, 0.855)
}
