package com.ngocdevv.nativecook.blocks.media.wavescrubslider

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** The numbers of the Wave Scrub Slider: the autoPlay script, the wave's shape and the photo's grade. */
internal object WaveScrubSliderModel {
    /** The self-playing loop, in seconds. */
    const val LOOP = 8.0
    /** How many times a second the wave's crests travel to the right (a whole number per loop). */
    const val DRIFT = 1.0

    /** The scripted finger: (time, value) keys, eased in and out between them. */
    private val keys = arrayOf(
        0.0 to 0.0, 0.6 to 0.0, 2.2 to 0.62, 2.9 to 0.62, 3.9 to 1.0, 5.0 to 1.0, 6.6 to 0.0, 8.0 to 0.0,
    )

    fun scripted(t: Double): Double {
        for (i in 1 until keys.size) {
            if (t <= keys[i].first) {
                val (t0, v0) = keys[i - 1]
                val (t1, v1) = keys[i]
                return v0 + (v1 - v0) * easeInOut((t - t0) / (t1 - t0))
            }
        }
        return 0.0
    }

    fun easeInOut(x: Double): Double {
        val u = x.coerceIn(0.0, 1.0)
        return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2
    }

    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val u = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return u * u * (3 - 2 * u)
    }

    /** Cycles of the wave from the left end of the track to u (0…1). */
    fun cycles(u: Double, v: Double): Double = (0.6 + 0.4 * v) * (2.0 * u + 6.5 * u * u)

    /** The wave's height above the centre line at u, for a thumb at v and a drift phase (in cycles). */
    fun height(u: Double, v: Double, phase: Double, width: Double): Double {
        val x = u * width
        val thumb = v * width
        val amp = if (x <= thumb) 15 * v * smoothstep(0.0, 0.3, u) else 1.2 + 5 * v
        val taper = smoothstep(3.0, 18.0, abs(x - thumb)) * smoothstep(0.0, 10.0, width - x) * smoothstep(0.0, 6.0, x)
        return amp * taper * sin(2 * PI * (cycles(u, v) - phase))
    }

    /**
     * The photo's grade for a value: contrast 1 → 1.45 around mid grey, saturation 1 → 1.3. A 4 × 5
     * colour matrix, row by row; the last column is an offset in 0…1 (Android's wants 0…255).
     */
    fun grade(v: Double): FloatArray {
        val c = 1 + 0.45 * v
        val s = 1 + 0.3 * v
        val lr = 0.2126 * (1 - s)
        val lg = 0.7152 * (1 - s)
        val lb = 0.0722 * (1 - s)
        val o = 0.5 * (1 - c)
        return doubleArrayOf(
            c * (lr + s), c * lg, c * lb, 0.0, o,
            c * lr, c * (lg + s), c * lb, 0.0, o,
            c * lr, c * lg, c * (lb + s), 0.0, o,
            0.0, 0.0, 0.0, 1.0, 0.0,
        ).map { it.toFloat() }.toFloatArray()
    }

    /** The value as the panel reads it: 0, then +1 … +100. */
    fun label(v: Double): String {
        val n = (v * 100).roundToInt()
        return if (n == 0) "0" else "+$n"
    }
}

internal fun rgb(hex: Long, a: Float = 1f) = Color(0xFF000000 or hex).copy(alpha = a)

/** The photo being edited (a lake at dusk, drawn in code) and the panel's line icons. All in dp. */
internal object WaveScrubSliderArt {
    private val FAR = arrayOf(doubleArrayOf(0.05, 1.3, 0.4), doubleArrayOf(0.03, 3.1, 1.2), doubleArrayOf(0.012, 7.7, 0.3))
    private val MID = arrayOf(doubleArrayOf(0.06, 0.9, 2.2), doubleArrayOf(0.025, 2.3, 0.6), doubleArrayOf(0.01, 5.9, 1.9))

    private fun ridge(x: Double, w: Double, h: Double, base: Double, waves: Array<DoubleArray>): Double {
        var y = base * h
        for (wv in waves) y -= wv[0] * h * sin(x / w * 2 * PI * wv[1] + wv[2])
        return y
    }

    /** A pine: a stack of five tiers that widen downwards, on a thin trunk. */
    private fun pine(x: Double, base: Double, height: Double): Path {
        val p = Path()
        val top = base - height
        p.moveTo(x.toFloat(), top.toFloat())
        val right = mutableListOf<Offset>()
        for (i in 1..5) {
            val y = top + height * 0.86 * i / 5
            val wi = height * 0.2 * i / 5 + 1.5
            right.add(Offset((x + wi).toFloat(), y.toFloat()))
            if (i < 5) right.add(Offset((x + wi * 0.35).toFloat(), (y - height * 0.02).toFloat()))
        }
        for (pt in right) p.lineTo(pt.x, pt.y)
        val trunkTop = (top + height * 0.86).toFloat()
        p.lineTo((x + 1.2).toFloat(), trunkTop)
        p.lineTo((x + 1.2).toFloat(), base.toFloat())
        p.lineTo((x - 1.2).toFloat(), base.toFloat())
        p.lineTo((x - 1.2).toFloat(), trunkTop)
        for (pt in right.reversed()) p.lineTo((2 * x - pt.x).toFloat(), pt.y)
        p.close()
        return p
    }

    private fun DrawScope.capsule(x: Double, y: Double, w: Double, h: Double, color: Color) =
        drawRoundRect(color, Offset(x.toFloat(), y.toFloat()), Size(w.toFloat(), h.toFloat()), CornerRadius((h / 2).toFloat()))

    /** Draws the photo into a w × h rectangle at the origin (the scope is in dp). */
    fun photo(d: DrawScope, w: Double, h: Double) = with(d) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val full = Size(wf, hf)
        val horizon = 0.74 * h
        // Sky.
        drawRect(
            Brush.verticalGradient(
                0f to rgb(0x16213F), 0.38f to rgb(0x4B3A6B), 0.56f to rgb(0xC4607A), 0.68f to rgb(0xF4A261), 0.74f to rgb(0xFFD59E),
                startY = 0f, endY = hf,
            ),
            size = full,
        )
        // Stars in the upper sky, fixed places (a hash of the index).
        for (i in 0 until 28) {
            val fx = (i * 0.618034) % 1.0
            val fy = (i * 0.381966 + 0.13 * (i % 3)) % 1.0
            val r = if (i % 5 == 0) 1.3f else 0.8f
            val a = (0.35 + 0.4 * (i % 4) / 3.0 - 0.3 * fy).coerceIn(0.0, 1.0).toFloat()
            drawCircle(Color.White.copy(alpha = a), r, Offset((fx * w).toFloat(), ((0.08 + 0.3 * fy) * h).toFloat()))
        }
        // Sun and its glow.
        val sun = Offset((0.64 * w).toFloat(), (0.665 * h).toFloat())
        drawRect(Brush.radialGradient(listOf(rgb(0xFFC27A, 0.85f), rgb(0xFFC27A, 0f)), center = sun, radius = (0.55 * w).toFloat()), size = full)
        drawCircle(rgb(0xFFF3D6), (0.075 * w).toFloat(), sun)
        // Streaks of cloud.
        val clouds = arrayOf(
            doubleArrayOf(0.06, 0.47, 0.42, 0.35), doubleArrayOf(0.3, 0.52, 0.5, 0.28),
            doubleArrayOf(0.62, 0.43, 0.3, 0.22), doubleArrayOf(0.7, 0.58, 0.26, 0.3),
        )
        for (c in clouds) capsule(c[0] * w, c[1] * h, c[2] * w, 5.0, rgb(0xF7B48A, c[3].toFloat()))
        // Far and middle ridges.
        fun ridgePath(waves: Array<DoubleArray>, base: Double): Path {
            val p = Path()
            p.moveTo(0f, horizon.toFloat())
            var x = 0.0
            while (x <= w + 4) {
                p.lineTo(x.toFloat(), ridge(x, w, h, base, waves).toFloat())
                x += 4
            }
            p.lineTo((w + 4).toFloat(), horizon.toFloat())
            p.close()
            return p
        }
        drawPath(ridgePath(FAR, 0.66), rgb(0xA45A7C))
        val midRidge = ridgePath(MID, 0.70)
        drawPath(midRidge, rgb(0x5D3160))
        // Lake.
        drawRect(
            Brush.verticalGradient(
                0f to rgb(0xE59A6F), 0.55f to rgb(0x3D2447), 1f to rgb(0x1A1022),
                startY = horizon.toFloat(), endY = hf,
            ),
            topLeft = Offset(0f, horizon.toFloat()),
            size = Size(wf, (h - horizon).toFloat()),
        )
        // The middle ridge mirrored in the water.
        clipRect(0f, horizon.toFloat(), wf, hf) {
            withTransform({ scale(1f, -1f, pivot = Offset(0f, horizon.toFloat())) }) {
                drawPath(midRidge, rgb(0x5D3160, 0.45f))
            }
        }
        // The sun's path on the water.
        var y = horizon + 5
        var i = 0
        while (y < 0.93 * h) {
            val k = (y - horizon) / (0.93 * h - horizon)
            val lw = (0.16 - 0.12 * k) * w * (if (i % 2 == 0) 1.0 else 0.7)
            capsule(sun.x - lw / 2, y, lw, 2.4, rgb(0xFFE2B0, (0.8 - 0.6 * k).toFloat()))
            y += 7
            i += 1
        }
        // The near shore with its pines.
        val ink = rgb(0x120A18)
        val shore = Path().apply {
            moveTo(0f, (0.83 * h).toFloat())
            cubicTo((0.22 * w).toFloat(), (0.84 * h).toFloat(), (0.36 * w).toFloat(), (0.9 * h).toFloat(), (0.46 * w).toFloat(), hf)
            lineTo(0f, hf)
            close()
        }
        drawPath(shore, ink)
        val right = Path().apply {
            moveTo(wf, (0.86 * h).toFloat())
            cubicTo((0.9 * w).toFloat(), (0.87 * h).toFloat(), (0.82 * w).toFloat(), (0.93 * h).toFloat(), (0.78 * w).toFloat(), hf)
            lineTo(wf, hf)
            close()
        }
        drawPath(right, ink)
        val pines = arrayOf(
            doubleArrayOf(0.04, 0.84, 0.3), doubleArrayOf(0.1, 0.845, 0.4), doubleArrayOf(0.165, 0.85, 0.24),
            doubleArrayOf(0.235, 0.86, 0.33), doubleArrayOf(0.3, 0.875, 0.2), doubleArrayOf(0.885, 0.875, 0.26),
            doubleArrayOf(0.95, 0.87, 0.34),
        )
        for (p in pines) drawPath(pine(p[0] * w, p[1] * h + 6, p[2] * h), ink)
        // Vignette: clear inside 0.3 h of the centre, 45 % black at 0.85 h.
        drawRect(
            Brush.radialGradient(
                (0.3f / 0.85f) to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.45f),
                center = Offset(wf / 2, (h * 0.55).toFloat()), radius = (0.85 * h).toFloat(),
            ),
            size = full,
        )
    }

    enum class Icon { Back, Undo, Redo, More, Auto, Light, Contrast, Color, Vignette, Grain, Sharpen, Crop, Brush, Dial, Layers, Sky }

    /** An icon drawn in a 24 × 24 box: what to stroke (1.6 wide, round) and what to fill. */
    private fun icon(icon: Icon): Pair<Path, Path> {
        val s = Path()
        val f = Path()
        fun line(vararg pts: Float, close: Boolean = false) {
            s.moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) {
                s.lineTo(pts[i], pts[i + 1]); i += 2
            }
            if (close) s.close()
        }
        fun circle(x: Float, y: Float, r: Float, fill: Boolean = false) =
            (if (fill) f else s).addOval(Rect(x - r, y - r, x + r, y + r))
        fun star(x: Float, y: Float, r: Float) {
            val k = r * 0.24f
            f.moveTo(x, y - r)
            f.quadraticTo(x + k, y - k, x + r, y)
            f.quadraticTo(x + k, y + k, x, y + r)
            f.quadraticTo(x - k, y + k, x - r, y)
            f.quadraticTo(x - k, y - k, x, y - r)
            f.close()
        }
        when (icon) {
            Icon.Back -> line(15f, 5f, 8f, 12f, 15f, 19f)
            Icon.Undo -> {
                line(9f, 5f, 5f, 9f, 9f, 13f)
                s.moveTo(5f, 9f)
                s.lineTo(14f, 9f)
                s.arcTo(Rect(9f, 9f, 19f, 19f), -90f, 180f, false)
                s.lineTo(10f, 19f)
            }
            Icon.Redo -> {
                line(15f, 5f, 19f, 9f, 15f, 13f)
                s.moveTo(19f, 9f)
                s.lineTo(10f, 9f)
                s.arcTo(Rect(5f, 9f, 15f, 19f), -90f, -180f, false)
                s.lineTo(14f, 19f)
            }
            Icon.More -> {
                circle(12f, 12f, 9f)
                for (x in floatArrayOf(8f, 12f, 16f)) circle(x, 12f, 1.3f, fill = true)
            }
            Icon.Auto -> {
                star(10.5f, 13.5f, 7f)
                star(18f, 5.5f, 3f)
            }
            Icon.Light -> {
                circle(12f, 12f, 4f)
                for (k in 0 until 8) {
                    val a = k * PI / 4
                    line((12 + 7 * cos(a)).toFloat(), (12 + 7 * sin(a)).toFloat(), (12 + 9.5 * cos(a)).toFloat(), (12 + 9.5 * sin(a)).toFloat())
                }
            }
            Icon.Contrast -> {
                circle(12f, 12f, 8f)
                f.moveTo(12f, 4f)
                f.arcTo(Rect(4f, 4f, 20f, 20f), -90f, -180f, false)
                f.close()
            }
            Icon.Color -> {
                circle(9f, 10f, 5f); circle(15f, 10f, 5f); circle(12f, 15f, 5f)
            }
            Icon.Vignette -> {
                s.addRoundRect(RoundRect(4f, 4f, 20f, 20f, CornerRadius(4f)))
                s.addOval(Rect(8f, 8.5f, 16f, 15.5f))
            }
            Icon.Grain -> for (x in floatArrayOf(7f, 12f, 17f)) for (y in floatArrayOf(7f, 12f, 17f)) circle(x, y, 1.4f, fill = true)
            Icon.Sharpen -> line(12f, 4f, 20f, 19f, 4f, 19f, close = true)
            Icon.Crop -> {
                line(7f, 3f, 7f, 17f, 21f, 17f)
                line(3f, 7f, 17f, 7f, 17f, 21f)
            }
            Icon.Brush -> {
                line(5f, 15f, 14f, 6f, 19f, 11f, 10f, 20f, close = true)
                line(9.5f, 10.5f, 14.5f, 15.5f)
                line(10f, 20f, 20f, 20f)
            }
            Icon.Dial -> {
                circle(12f, 13f, 8f)
                line(12f, 13f, 16f, 9f)
                circle(12f, 13f, 1.6f, fill = true)
                line(12f, 2.5f, 12f, 4f)
            }
            Icon.Layers -> {
                s.addRoundRect(RoundRect(4f, 8f, 16f, 20f, CornerRadius(2f)))
                s.addRoundRect(RoundRect(8f, 4f, 20f, 16f, CornerRadius(2f)))
            }
            Icon.Sky -> {
                s.moveTo(7f, 15f)
                s.arcTo(Rect(7f, 10f, 17f, 20f), 180f, 180f, false)
                line(3f, 15f, 21f, 15f)
                line(12f, 5f, 12f, 7f); line(5.6f, 8.6f, 7f, 10f); line(18.4f, 8.6f, 17f, 10f)
                line(6f, 19f, 18f, 19f)
            }
        }
        return s to f
    }

    private val cache = mutableMapOf<Icon, Pair<Path, Path>>()
    private val stroke = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    /** Draws an icon centred at a point (dp), [size] dp square. */
    fun draw(d: DrawScope, icon: Icon, cx: Float, cy: Float, color: Color, size: Float = 24f) = with(d) {
        val (s, f) = cache.getOrPut(icon) { icon(icon) }
        translate(cx - size / 2, cy - size / 2) {
            withTransform({ scale(size / 24f, size / 24f, pivot = Offset.Zero) }) {
                drawPath(s, color, style = stroke)
                drawPath(f, color)
            }
        }
    }

    /** Cycle index of the wave at the thumb, for the haptic ticks. */
    fun tick(v: Double): Int = floor(WaveScrubSliderModel.cycles(v, v)).toInt()

    fun clamp01(x: Double) = max(0.0, min(1.0, x))
}
