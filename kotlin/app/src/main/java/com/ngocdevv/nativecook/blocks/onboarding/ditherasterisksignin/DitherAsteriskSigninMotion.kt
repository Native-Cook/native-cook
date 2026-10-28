package com.ngocdevv.nativecook.blocks.onboarding.ditherasterisksignin

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/*
 Dither Asterisk Sign In — the pixel mark, the autoPlay script and the touch springs (a port of
 DitherAsteriskSigninMotion.swift, the reference).

 The mark is a 31 × 31 grid of cells (an asterisk of four bars). Its 31 columns are grouped into
 8 vertical slices. A frame is, per column, an offset in cells (drawn rounded to whole cells) and
 an energy 0…1 that thins the column's cells into a glyph dither (full → ■ → 1 → 0 → + → empty),
 plus a sideways shift for three row bands. Cells between a slice's old and new place are drawn as
 a fading streak of glyphs.

 autoPlay: LOOP = 7 s
   0.00 – 0.80  rest, the whole mark
   0.80 – 1.98  slices slide out one after another, right to left, and dither (build); while out
                they drift by a cell or so (3 sine periods per loop)
   2.70 – 4.05  slices jump to a second set of offsets, row bands shear sideways (peak scramble)
   4.05 – 5.55  slices spring home left to right with a one-cell overshoot, cells fill back in
   5.10 – 6.10  a faint glow behind the mark as it is whole again
   5.55 – 7.00  rest (the frame at 7 is the frame at 0)
*/
object DitherAsteriskSigninMotion {
    const val LOOP = 7.0
    const val N = 31
    const val PITCH = 22.0 / 3.0
    const val CELL = 22.0 / 3.0 - 1.0
    const val ROW_MIN = -8
    const val ROW_MAX = 38
    const val ROWS = ROW_MAX - ROW_MIN + 1
    const val IDLE_BEFORE_BACK = 2.0

    val widths = intArrayOf(4, 3, 5, 2, 4, 3, 6, 4)
    val build = doubleArrayOf(0.0, -2.0, 1.0, -5.0, 2.0, 6.0, 3.0, -5.0)
    val peak = doubleArrayOf(1.0, 3.0, -2.0, 2.0, -6.0, -4.0, 5.0, 2.0)
    val energy = doubleArrayOf(0.0, 0.3, 0.0, 1.0, 0.5, 1.0, 0.2, 0.8)

    /** Row bands that shear sideways at the peak: first row, last row, shift in cells. */
    val bandFirst = intArrayOf(6, 14, 21)
    val bandLast = intArrayOf(9, 16, 24)
    val bandShift = doubleArrayOf(2.0, -3.0, 4.0)
    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /** The slice each column belongs to. */
    val sliceOf: IntArray = IntArray(N).also { out ->
        var c = 0
        for (i in widths.indices) repeat(widths[i]) { out[c++] = i }
    }

    /** The asterisk, row-major, N × N. */
    private val mask: BooleanArray = BooleanArray(N * N).also { out ->
        val k = 1.0 / sqrt(2.0)
        for (r in 0 until N) for (c in 0 until N) {
            val x = (c - 15).toDouble()
            val y = (r - 15).toDouble()
            var on = abs(y) <= 3.2 && abs(x) <= 15.5
            on = on || (abs(x) <= 2.6 && abs(y) <= 15.5)
            for (s in doubleArrayOf(1.0, -1.0)) {
                val along = (x + s * y) * k
                val across = (y - s * x) * k
                if (abs(across) <= 2.3 && abs(along) <= 14.4) on = true
                val ex = 14.4 * k
                val ey = 14.4 * k * s
                if ((x - ex) * (x - ex) + (y - ey) * (y - ey) <= 2.3 * 2.3) on = true
                if ((x + ex) * (x + ex) + (y + ey) * (y + ey) <= 2.3 * 2.3) on = true
            }
            out[r * N + c] = on
        }
    }

    fun inMask(c: Int, r: Int): Boolean = c in 0 until N && r in 0 until N && mask[r * N + c]

    fun seg(t: Double, a: Double, b: Double): Double = if (t <= a) 0.0 else if (t >= b) 1.0 else (t - a) / (b - a)
    fun easeOut(x: Double): Double = 1 - (1 - x).pow(3)
    fun easeInOut(x: Double): Double = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    /** Whole cells, rounding halves up in every stack. */
    fun cells(x: Double): Int = floor(x + 0.5).toInt()

    /** Step response of a spring from 1 to 0 (response 0.5 s, damping 0.55). */
    fun springHome(tau: Double): Double {
        if (tau <= 0) return 1.0
        val w0 = 2 * PI / 0.5
        val z = 0.55
        val wd = w0 * sqrt(1 - z * z)
        return exp(-z * w0 * tau) * (cos(wd * tau) + z * w0 / wd * sin(wd * tau))
    }

    fun buildStart(i: Int): Double = 0.8 + 0.09 * (7 - i)
    fun peakStart(i: Int): Double = 2.7 + 0.06 * ((5 * i) % 8)
    fun homeStart(i: Int): Double = 4.05 + 0.1 * i

    /** The autoPlay frame at loop time t. */
    fun autoFrame(t: Double): DitherAsteriskSigninFrame {
        val f = DitherAsteriskSigninFrame()
        val sliceOffset = DoubleArray(widths.size)
        val sliceEnergy = DoubleArray(widths.size)
        for (i in widths.indices) {
            val s = buildStart(i)
            val p = peakStart(i)
            val h = homeStart(i)
            val a = easeInOut(seg(t, s, s + 0.55))
            val b = easeInOut(seg(t, p, p + 0.45))
            val rise = easeOut(seg(t, s, s + 0.6))
            val fall = easeInOut(seg(t, h + 0.1, h + 0.8))
            // A slow drift while the slices are out (3 periods per loop), ticking by whole cells.
            val drift = 1.3 * sin(2 * PI * 3 * t / LOOP + 1.7 * i) * rise * (1 - fall)
            sliceOffset[i] = (build[i] * a * (1 - b) + peak[i] * b) * springHome(t - h) + drift
            sliceEnergy[i] = energy[i] * rise * (1 - fall)
        }
        for (c in 0 until N) {
            f.offset[c] = sliceOffset[sliceOf[c]]
            f.energy[c] = sliceEnergy[sliceOf[c]]
        }
        val shear = easeInOut(seg(t, 2.75, 3.05)) * (1 - easeInOut(seg(t, 3.75, 4.05)))
        for (k in bandShift.indices) f.shift[k] = bandShift[k] * shear
        val up = easeOut(seg(t, 5.1, 5.4))
        val down = easeInOut(seg(t, 5.4, 6.1))
        f.glow = 0.07 * up * (1 - down)
        return f
    }

    /** Glyph level 0…5 from a density and the cell's Bayer threshold. */
    fun level(d: Double, c: Int, r: Int): Int {
        val b = (bayer[((r % 4) + 4) % 4 * 4 + ((c % 4) + 4) % 4] + 0.5) / 16
        val q = d - b
        return when {
            q > 0 -> 5
            q > -0.15 -> 4
            q > -0.30 -> 3
            q > -0.45 -> 2
            q > -0.60 -> 1
            else -> 0
        }
    }

    /** The level of every drawn cell, ROWS × N, into `out`. */
    fun levels(f: DitherAsteriskSigninFrame, out: IntArray) {
        val rowShift = IntArray(ROWS)
        for (k in bandShift.indices) {
            val s = cells(f.shift[k])
            for (r in bandFirst[k]..bandLast[k]) rowShift[r - ROW_MIN] = s
        }
        for (c in 0 until N) {
            val d = cells(f.offset[c])
            val e = f.energy[c]
            val body = 1 - e
            val span = abs(d)
            val dir = if (d > 0) 1 else -1
            for (ri in 0 until ROWS) {
                val r = ri + ROW_MIN
                val sc = c - rowShift[ri]
                val sr = r - d
                var lv = 0
                if (inMask(sc, sr)) {
                    lv = level(body, c, r)
                } else if (span > 0) {
                    for (j in 1..span) {
                        if (inMask(sc, sr + dir * j)) {
                            val dens = 0.8 * e * (1 - (j - 1).toDouble() / span)
                            lv = min(4, level(dens, c, r))
                            break
                        }
                    }
                }
                out[ri * N + c] = lv
            }
        }
    }

    private fun addGlyph(p: Path, lv: Int, x: Double, y: Double, s: Double) {
        fun r(u0: Double, v0: Double, u1: Double, v1: Double) {
            p.addRect(Rect((x + u0 * s).toFloat(), (y + v0 * s).toFloat(), (x + u1 * s).toFloat(), (y + v1 * s).toFloat()))
        }
        when (lv) {
            5 -> r(0.0, 0.0, 1.0, 1.0)
            4 -> r(0.19, 0.19, 0.81, 0.81)
            3 -> {
                r(0.42, 0.12, 0.58, 0.88)
                r(0.26, 0.12, 0.42, 0.27)
            }
            2 -> {
                r(0.24, 0.12, 0.76, 0.26)
                r(0.24, 0.74, 0.76, 0.88)
                r(0.24, 0.26, 0.38, 0.74)
                r(0.62, 0.26, 0.76, 0.74)
            }
            1 -> {
                r(0.18, 0.43, 0.82, 0.57)
                r(0.43, 0.18, 0.57, 0.43)
                r(0.43, 0.57, 0.57, 0.82)
            }
        }
    }

    /** The whole mark into `p` (reset first), its grid's top-left at (left, top), in dp. */
    fun markPath(f: DitherAsteriskSigninFrame, left: Double, top: Double, p: Path, scratch: IntArray) {
        p.reset()
        levels(f, scratch)
        for (ri in 0 until ROWS) {
            val y = top + (ri + ROW_MIN) * PITCH
            for (c in 0 until N) {
                val l = scratch[ri * N + c]
                if (l > 0) addGlyph(p, l, left + c * PITCH, y, CELL)
            }
        }
    }
}

/** Everything one frame of the mark needs. */
class DitherAsteriskSigninFrame {
    val offset = DoubleArray(DitherAsteriskSigninMotion.N)
    val energy = DoubleArray(DitherAsteriskSigninMotion.N)
    val shift = DoubleArray(DitherAsteriskSigninMotion.bandShift.size)
    var glow = 0.0
}

/** The loop clock, and per-column springs while a finger drives the mark. */
class DitherAsteriskSigninSim {
    private val m = DitherAsteriskSigninMotion
    private var epoch = Double.NaN
    private var lastTime = Double.NaN
    private var lastAuto = DitherAsteriskSigninFrame()
    var live = false
        private set
    private val x = DoubleArray(m.N)
    private val v = DoubleArray(m.N)
    private val e = DoubleArray(m.N)
    private val shift = DoubleArray(m.bandShift.size)
    var down = false
        private set
    private var fingerCol = 0.0
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastTouch = Double.NaN
    private var releasedAt = Double.NaN
    private var feltSlice = false
    private var feltWhole = true
    var onFeel: (() -> Unit)? = null

    private val k = (2 * PI / 0.55).pow(2)
    private val damp = 2 * 0.5 * (2 * PI / 0.55)

    /** The frame at `now` (seconds). */
    fun frame(now: Double, auto: Boolean): DitherAsteriskSigninFrame {
        if (epoch.isNaN()) epoch = now
        if (!live) {
            val t = if (auto) ((now - epoch) % m.LOOP) else 0.0
            lastAuto = m.autoFrame(t)
            lastTime = now
            return lastAuto
        }
        val dt = min(max(now - (if (lastTime.isNaN()) now else lastTime), 0.0), 1.0 / 20)
        lastTime = now
        step(dt)
        val f = DitherAsteriskSigninFrame()
        var whole = true
        for (c in 0 until m.N) {
            f.offset[c] = 9 * tanh(x[c] / 9)
            f.energy[c] = min(1.0, e[c])
            if (m.cells(f.offset[c]) != 0 || e[c] > 0.04) whole = false
        }
        for (i in shift.indices) {
            f.shift[i] = shift[i]
            if (m.cells(shift[i]) != 0) whole = false
        }
        if (!down) {
            if (whole && !feltWhole) {
                feltWhole = true
                onFeel?.invoke()
            }
            if (whole && !releasedAt.isNaN() && now - releasedAt >= m.IDLE_BEFORE_BACK) {
                live = false
                epoch = now
                lastAuto = m.autoFrame(0.0)
                return lastAuto
            }
        }
        return f
    }

    private fun step(dt: Double) {
        val steps = max(1, ceil(dt * 240).toInt())
        val h = dt / steps
        repeat(steps) {
            for (c in 0 until m.N) {
                // The spring of the columns under the finger lets go a little while it is down.
                val w = if (down) exp(-((c - fingerCol) / 1.4).pow(2)) else 0.0
                val a = -k * (1 - 0.65 * w) * x[c] - damp * v[c]
                v[c] += a * h
                x[c] += v[c] * h
                e[c] *= exp(-h / 0.6)
            }
            for (i in shift.indices) shift[i] *= exp(-h / 0.12)
        }
    }

    /**
     * The finger at (px, py) dp, over column `col` (fractional, column centres at whole numbers).
     * Columns near it are dragged by the finger's movement since the last event: vertical motion
     * pulls them along, horizontal motion slices them up or down by their slice's sign; their
     * dither follows the finger's speed.
     */
    fun touch(px: Double, py: Double, col: Double, now: Double) {
        if (!live) {
            live = true
            for (c in 0 until m.N) {
                x[c] = lastAuto.offset[c]
                e[c] = lastAuto.energy[c]
                v[c] = 0.0
            }
            for (i in shift.indices) shift[i] = lastAuto.shift[i]
            lastTime = now
        }
        if (!down) {
            feltSlice = false
            lastX = px
            lastY = py
            lastTouch = now
        }
        down = true
        feltWhole = false
        fingerCol = col
        val dx = px - lastX
        val dy = py - lastY
        val dt = max(now - (if (lastTouch.isNaN()) now else lastTouch), 1.0 / 240)
        val speed = sqrt(dx * dx + dy * dy) / dt
        for (c in 0 until m.N) {
            val w = exp(-((c - col) / 1.4).pow(2))
            val sign = if (m.sliceOf[c] % 2 == 0) 1.0 else -1.0
            x[c] = min(max(x[c] + w * (dy + sign * abs(dx) * 0.8) / m.PITCH, -14.0), 14.0)
            e[c] = max(e[c], w * min(1.0, speed / 900))
        }
        lastX = px
        lastY = py
        lastTouch = now
        if (!feltSlice && x.any { abs(it) > 3 }) {
            feltSlice = true
            onFeel?.invoke()
        }
    }

    fun release(now: Double) {
        if (!down) return
        down = false
        releasedAt = now
    }
}
