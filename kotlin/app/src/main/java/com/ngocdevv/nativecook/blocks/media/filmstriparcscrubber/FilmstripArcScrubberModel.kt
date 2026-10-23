package com.ngocdevv.nativecook.blocks.media.filmstriparcscrubber

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 The numbers behind Filmstrip Arc Scrubber (a port of FilmstripArcScrubberModel.swift; the numbers
 are the same): the one-minute "video" (a night drive drawn as a function of its time), the
 filmstrip's geometry, the layout and the autoPlay timeline.
 */

internal class FasRGB(val r: Double, val g: Double, val b: Double) {
    fun color(a: Double = 1.0) = Color(
        r.toFloat().coerceIn(0f, 1f), g.toFloat().coerceIn(0f, 1f), b.toFloat().coerceIn(0f, 1f), a.toFloat().coerceIn(0f, 1f),
    )

    companion object {
        fun mix(a: FasRGB, b: FasRGB, t: Double) = FasRGB(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t)
    }
}

/** What the screen shows at a moment. */
internal class FasShown(
    /** The video's time, in seconds (0 – 60). */
    val time: Double,
    /** How far the filmstrip has lifted out of the bar (0 – 1). */
    val lift: Double,
    /** The selected frame flying up into the video (0 – 1, 0 when none) and its time. */
    val fly: Double,
    val flyTime: Double,
    /** How fast the time is being scrubbed (seconds per second), for the motion blur. */
    val speed: Double,
    val playing: Boolean,
)

internal object FasModel {
    const val DURATION = 60.0
    /** One frame of the strip every 2.5 s of video. */
    const val FRAME_STEP = 2.5
    const val LOOP = 10.0
    /** Where the loop starts and ends: 0:18 into the video, playing. */
    const val REST_TIME = 18.0

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
        val c1 = 1.5
        val c3 = c1 + 1
        return 1 + c3 * (u - 1).pow(3) + c1 * (u - 1).pow(2)
    }
    fun wrap(t: Double): Double {
        var x = t % DURATION
        if (x < 0) x += DURATION
        return x
    }

    // MARK: autoPlay

    /**
     * The video's time at a moment of the loop: plays 0:18 → 0:20, is held and dragged forward ten
     * frames into the night (0:45), back to 0:15, released at 7.0 s and plays on to 0:18 at 10 s.
     */
    fun time(t: Double): Double = when {
        t < 2 -> REST_TIME + t
        t < 2.7 -> 20.0
        t < 4.5 -> 20 + 25 * easeInOut((t - 2.7) / 1.8)
        t < 4.6 -> 45.0
        t < 6.6 -> 45 - 30 * easeInOut((t - 4.6) / 2.0)
        t < 7 -> 15.0
        else -> 15 + (t - 7)
    }

    /**
     * autoPlay (LOOP = 10 s):
     *   0.0 – 2.0   playing from 0:18
     *   2.0         a finger presses the thumb: playback holds, the strip lifts (0.5 s, ease out back)
     *   2.7 – 4.5   dragged forward to 0:45 (night);  4.6 – 6.6 back to 0:15 (easeInOut)
     *   7.0         released: the 0:15 frame flies up into the video (0.6 s) and playback resumes
     *   7.2 – 7.7   the strip folds back into the bar
     *   10.0        0:18 again, playing: the same frame as 0
     */
    fun shown(time0: Double): FasShown {
        var t = time0 % LOOP
        if (t < 0) t += LOOP
        val lift = if (t >= 2 && t < 7.2) easeOutBack((t - 2) / 0.5) else if (t >= 7.2) 1 - easeInOut((t - 7.2) / 0.5) else 0.0
        val speed = (time(t + 0.02) - time(max(0.0, t - 0.02))) / 0.04
        val fly = if (t >= 7 && t < 7.6) easeInOut((t - 7) / 0.6) else 0.0
        return FasShown(time(t), lift, fly, 15.0, speed, t < 2 || t >= 7)
    }

    // MARK: Layout

    class Layout(val w: Double, val h: Double, val top: Double, val bottom: Double) {
        val video = Rect(12f, (top + 8).toFloat(), (w - 12).toFloat(), (h * 0.6).toFloat())
        val titleY = video.bottom + 20.0
        val barY = h * 0.785
        val barX = 24.0
        val barW = w - 48
        val transportY = h - max(bottom, 16.0) - 50
        /** The strip's centre line when lifted. */
        val stripY = barY - 98
    }

    // MARK: The film strip

    /** A strip cell: the film (64 × 82) with sprocket holes, its picture 56 × 60 at (4, 11). */
    const val CELL_W = 64.0
    const val CELL_H = 82.0
    const val PIC_X = 4.0
    const val PIC_Y = 11.0
    const val PIC_W = 56.0
    const val PIC_H = 60.0
    /** The strip wraps a cylinder of this radius (dp), seen in perspective (focal 520). */
    const val RADIUS = 230.0
    const val FOCAL = 520.0

    // MARK: The night drive

    /** A small integer hash in 0…1, the same in every language. */
    fun hash(a: Int, b: Int, c: Int): Double {
        var x = (a * 73_856_093) xor (b * 19_349_663) xor (c * 83_492_791)
        x = x xor (x ushr 13)
        x *= 1_274_126_177
        x = x xor (x ushr 16)
        return (x and 0xFFFF) / 65535.0
    }

    private fun c(r: Double, g: Double, b: Double) = FasRGB(r, g, b)

    /** The light over the minute: golden hour, dusk, night, deep night (sky top, horizon, far hills, near hills). */
    private val keyTimes = doubleArrayOf(0.0, 22.0, 40.0, 60.0)
    private val keys = listOf(
        listOf(c(0.98, 0.60, 0.36), c(1.0, 0.86, 0.58), c(0.62, 0.36, 0.40), c(0.34, 0.18, 0.26)),
        listOf(c(0.42, 0.20, 0.52), c(1.0, 0.50, 0.42), c(0.36, 0.18, 0.36), c(0.18, 0.09, 0.20)),
        listOf(c(0.08, 0.08, 0.24), c(0.36, 0.22, 0.46), c(0.14, 0.10, 0.24), c(0.07, 0.05, 0.13)),
        listOf(c(0.02, 0.03, 0.10), c(0.12, 0.12, 0.28), c(0.07, 0.07, 0.15), c(0.03, 0.03, 0.07)),
    )

    fun palette(t: Double): List<FasRGB> {
        for (k in 0 until keys.size - 1) {
            if (t <= keyTimes[k + 1]) {
                val u = smoothstep(keyTimes[k], keyTimes[k + 1], t)
                return (0 until 4).map { FasRGB.mix(keys[k][it], keys[k + 1][it], u) }
            }
        }
        return keys[keys.size - 1]
    }

    const val HORIZON = 0.56

    /** Draws the video at time t into a rect (dp); [lite] leaves out the stars and the lamp posts (strip thumbnails). */
    fun drawScene(d: DrawScope, r: Rect, t: Double, lite: Boolean) {
        val w = r.width.toDouble()
        val h = r.height.toDouble()
        fun P(u: Double, v: Double) = Offset((r.left + u * w).toFloat(), (r.top + v * h).toFloat())
        val pal = palette(t)
        val vh = HORIZON
        // Sky and ground.
        d.drawRect(pal[3].color(), r.topLeft, r.size)
        d.drawRect(
            Brush.verticalGradient(listOf(pal[0].color(), pal[1].color()), startY = r.top, endY = P(0.0, vh).y),
            r.topLeft, Size(r.width, (vh * h + 1).toFloat()),
        )
        // Stars come out as it darkens.
        val starA = smoothstep(26.0, 44.0, t)
        if (!lite && starA > 0.01) {
            for (i in 0 until 36) {
                val tw = 0.75 + 0.25 * sin(t * 3 + i)
                val s = 1.2 + 0.8 * hash(i, 2, 5)
                d.drawCircle(
                    Color.White.copy(alpha = (starA * (0.5 + 0.5 * hash(i, 4, 5)) * tw).toFloat().coerceIn(0f, 1f)),
                    (s / 2).toFloat(), P(hash(i, 1, 5), 0.48 * hash(i, 3, 5)),
                )
            }
        }
        // The sun sets by 0:30; the moon rises after 0:34.
        val sunA = 1 - smoothstep(20.0, 34.0, t)
        if (sunA > 0.01) {
            val sp = P(0.62, 0.30 + 0.34 * t / 30)
            val gr = (0.36 * w).toFloat()
            d.drawCircle(Brush.radialGradient(listOf(Color(1f, 0.75f, 0.45f, (0.8 * sunA).toFloat()), Color.Transparent), center = sp, radius = gr), gr, sp)
            d.drawCircle(Color(1f, 0.92f, 0.72f), (0.07 * w).toFloat(), sp)
        }
        if (t > 34) {
            val mp = P(0.24, 0.62 - 0.4 * smoothstep(34.0, 60.0, t))
            val mr = (0.045 * w).toFloat()
            d.drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = mp, radius = mr * 3), mr * 3, mp)
            d.drawCircle(Color(0.96f, 0.94f, 0.86f), mr, mp)
        }
        // Far mountains, then near hills, drifting past at two speeds.
        val step = if (lite) 0.1 else 0.05
        fun ridge(base: Double, a1: Double, f1: Double, a2: Double, f2: Double, shift: Double, phase: Double): Path {
            val p = Path()
            P(-0.05, 1.01).let { p.moveTo(it.x, it.y) }
            var u = -0.05
            while (u <= 1.05 + 1e-9) {
                val q = P(u, base - a1 * sin((u + shift) * f1 + phase) - a2 * sin((u + shift) * f2 + phase * 2 + 1))
                p.lineTo(q.x, q.y)
                u += step
            }
            P(1.05, 1.01).let { p.lineTo(it.x, it.y) }
            p.close()
            return p
        }
        d.drawPath(ridge(vh - 0.10, 0.06, 7.1, 0.04, 13.7, t * 0.004, 0.0), pal[2].color())
        d.drawPath(ridge(vh - 0.02, 0.035, 5.3, 0.02, 11.0, t * 0.012, 2.0), pal[3].color())
        // The road to the vanishing point.
        val road = Path().apply {
            P(0.49, vh).let { moveTo(it.x, it.y) }
            P(0.51, vh).let { lineTo(it.x, it.y) }
            P(1.15, 1.0).let { lineTo(it.x, it.y) }
            P(-0.15, 1.0).let { lineTo(it.x, it.y) }
            close()
        }
        d.drawPath(road, FasRGB.mix(pal[3], c(0.10, 0.09, 0.12), 0.6).color())
        val edge = Color(0.85f, 0.8f, 0.7f, 0.4f)
        val ew = if (lite) 0.8f else 1.5f
        d.drawLine(edge, P(0.49, vh), P(-0.15, 1.0), ew)
        d.drawLine(edge, P(0.51, vh), P(1.15, 1.0), ew)
        // Lane dashes rushing towards the camera.
        for (k in 0 until 8) {
            var z = (k / 8.0 + t * 0.35) % 1.0
            if (z < 0) z += 1
            val d0 = z.pow(2.2)
            val d1 = min(1.0, z + 0.045).pow(2.2)
            val v0 = vh + (1 - vh) * d0
            val v1 = vh + (1 - vh) * d1
            val w0 = 0.002 + 0.016 * d0
            val w1 = 0.002 + 0.016 * d1
            val dash = Path().apply {
                P(0.5 - w0, v0).let { moveTo(it.x, it.y) }
                P(0.5 + w0, v0).let { lineTo(it.x, it.y) }
                P(0.5 + w1, v1).let { lineTo(it.x, it.y) }
                P(0.5 - w1, v1).let { lineTo(it.x, it.y) }
                close()
            }
            d.drawPath(dash, Color(0.95f, 0.88f, 0.6f, (0.85 * smoothstep(0.0, 0.15, z)).toFloat()))
        }
        // Lamp posts along the right verge, lit as it gets dark.
        val lampA = smoothstep(16.0, 30.0, t)
        if (!lite) {
            for (k in 0 until 5) {
                var z = (k / 5.0 + t * 0.18) % 1.0
                if (z < 0) z += 1
                val dd = z.pow(2.2)
                val base = P(0.5 + 0.08 + 0.62 * dd, vh + (1 - vh) * dd)
                val top = Offset(base.x, (base.y - (0.02 + 0.5 * dd) * h).toFloat())
                d.drawLine(Color(0.06f, 0.05f, 0.08f, smoothstep(0.0, 0.1, z).toFloat()), base, top, (0.6 + 3 * dd).toFloat())
                if (lampA > 0.01) {
                    val gr = ((0.01 + 0.08 * dd) * w).toFloat()
                    d.drawCircle(
                        Brush.radialGradient(listOf(Color(1f, 0.8f, 0.5f, (0.85 * lampA * smoothstep(0.0, 0.1, z)).toFloat()), Color.Transparent), center = top, radius = gr),
                        gr, top,
                    )
                }
            }
        }
        // A car's tail lights ahead.
        val carA = 0.25 + 0.75 * smoothstep(10.0, 26.0, t)
        val cv = vh + 0.04 + 0.02 * sin(t * 0.4)
        for (dx in doubleArrayOf(-0.014, 0.014)) {
            val cp = P(0.5 + dx, cv)
            val gr = (0.022 * w).toFloat()
            d.drawCircle(Brush.radialGradient(listOf(Color(1f, 0.15f, 0.1f, (0.9 * carA).toFloat()), Color.Transparent), center = cp, radius = gr), gr, cp)
            d.drawCircle(Color(1f, 0.5f, 0.45f, carA.toFloat()), 1.2f, cp)
        }
        // Vignette.
        val inner = (min(w, h) * 0.35).toFloat()
        val outer = (max(w, h) * 0.75).toFloat()
        d.drawRect(
            Brush.radialGradient(
                (inner / outer) to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.4f), center = P(0.5, 0.5), radius = outer,
            ),
            r.topLeft, r.size,
        )
    }

    fun clock(t: Double): String {
        val s = t.toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    /** Timecode with frames at 24 per second: 00:18:04. */
    fun timecode(t: Double): String {
        val s = t.toInt()
        return "%02d:%02d:%02d".format(s / 60, s % 60, ((t - s) * 24).toInt())
    }
}
