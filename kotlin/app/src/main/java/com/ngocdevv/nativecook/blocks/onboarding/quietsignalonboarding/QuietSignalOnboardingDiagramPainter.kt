package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The five diagrams of `QuietSignalDiagrams.tsx`, drawn on a 300-unit square. Each stage is the
 * Skia drawing of the React Native file, one function per stage, with the same numbers: the lines
 * that move are rebuilt from the loop time given to [draw].
 *
 * One painter belongs to one diagram. It owns the paints, scratch paths and gradient shaders, so
 * a frame allocates nothing. A paint is configured completely every time it is used (a reused
 * paint would keep the alpha of its previous colour).
 */
internal class QuietSignalOnboardingDiagramPainter {
    private val shapes = QuietSignalOnboardingShapes
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val trimmed = Path()
    private val measure = PathMeasure()

    // Shaders that never change.
    private val attentionGlow by lazy {
        RadialGradient(205f, 150f, 120f, intArrayOf(white(0x30), white(0x14), white(0x00)), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
    }
    private val headLight by lazy {
        LinearGradient(
            150f, 0f, 258f, 0f,
            intArrayOf(Color.argb(0, 0, 0, 0), white(0x10), white(0xD8)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val rippleLeft by lazy { rippleShader(46f) }
    private val rippleRight by lazy { rippleShader(254f) }
    private val pulseRings by lazy {
        Array(5) { i ->
            val radius = (70 - i * 12).toFloat()
            RadialGradient(
                0f, 0f, radius,
                intArrayOf(white(0x02), white(0x22), white(0x0C)),
                floatArrayOf(0.3f, 0.88f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }

    /** Draws diagram [stage] at loop time [t] (ignored when [reduced]) into a canvas scaled to 300 units. */
    fun draw(canvas: Canvas, stage: Int, t: Double, reduced: Boolean) {
        when (stage) {
            0 -> busyMind(canvas, t, reduced)
            1 -> attention(canvas, t, reduced)
            2 -> anywhere(canvas, t, reduced)
            3 -> rhythm(canvas, t, reduced)
            else -> emblem(canvas, t, reduced)
        }
    }

    /** Stage 0, a busy mind: moons swing through the head, a notification hums, a page keeps filling. */
    private fun busyMind(canvas: Canvas, t: Double, reduced: Boolean) {
        val v = if (reduced) 2.0 else t
        canvas.drawPath(shapes.HEAD, stroke(INK, 0.9, 1.5f))
        canvas.drawPath(shapes.PILL, stroke(DIM, 1.0, 1.3f))

        path.rewind()
        shapes.addWave(path, 54.0, 122.0, 64.0, 3.2, 12.0, -v * 7.0)
        canvas.drawPath(path, stroke(INK, 0.85, 1.3f))

        canvas.drawPath(shapes.DOC, fill(Color.rgb(5, 5, 7), 1.0))
        canvas.drawPath(shapes.DOC, stroke(INK, 0.9, 1.4f))

        path.rewind()
        for (i in 0 until 5) {
            val grow = if (reduced) 1.0 else 0.5 + 0.5 * sin(v * 1.3 - i * 0.7)
            val y = 192f + i * 13f
            path.moveTo(52f, y)
            path.lineTo((52.0 + LINE_LENGTHS[i] * (0.35 + 0.65 * grow)).toFloat(), y)
        }
        canvas.drawPath(path, stroke(DIM, 1.0, 1.2f, Paint.Cap.ROUND))

        path.rewind()
        for (i in 0 until 3) {
            val a = v * (0.5 + i * 0.18) + i * 2
            shapes.addArc(path, 160.0, 150.0, 34.0 + i * 12, a, a + 1.6 + i * 0.4)
        }
        canvas.drawPath(path, stroke(DIM, 0.7, 1f))

        path.rewind()
        shapes.addCrescent(path, 168.0, 116.0, 50.0, 0.3 + sin(v * 0.9) * 0.5, 24.0)
        shapes.addCrescent(path, 160.0, 152.0, 36.0, 3.5 + sin(v * 0.7 + 1.2) * 0.55, 14.0)
        canvas.drawPath(path, stroke(INK, 1.0, 1.5f, join = Paint.Join.ROUND))

        // The three dots light one after another, like someone about to reply.
        for (i in 0 until 3) {
            val lit = if (reduced) {
                if (i == 0) 1.0 else 0.0
            } else {
                if ((t * 0.8 + 1 - i / 3.0) % 1.0 < 0.33) 1.0 else 0.0
            }
            val cx = 202f + i * 13f
            canvas.drawCircle(cx, 146f, 4.2f, stroke(INK, 0.85, 1.3f))
            if (lit > 0.0) canvas.drawCircle(cx, 146f, 3.6f, fill(INK, lit))
        }
    }

    /** Stage 1, attention: a lit profile, two waves crossing in a slow figure of eight, a voice between them. */
    private fun attention(canvas: Canvas, t: Double, reduced: Boolean) {
        val v = if (reduced) 1.5 else t
        canvas.drawCircle(205f, 150f, 120f, shaded(attentionGlow, 1.0))

        // Group opacity 0.22: the grid is composited as one layer.
        val layer = canvas.saveLayerAlpha(0f, 0f, 300f, 300f, alpha255(0.22))
        canvas.drawPath(shapes.CROSS, stroke(DIM, 1.0, 0.8f))
        for (i in 0 until 49) {
            canvas.drawCircle(30f + (i % 7) * 40f, 30f + (i / 7) * 40f, 1.1f, fill(INK, 1.0))
        }
        canvas.restoreToCount(layer)

        canvas.drawPath(shapes.HEAD_FILL, shaded(headLight, 1.0))
        canvas.drawPath(shapes.HEAD, stroke(INK, 0.75, 1.4f))

        // Two long sines in antiphase cross twice across the frame, slowly breathing.
        val amp = 64.0 + sin(v * 0.8) * 8.0
        path.rewind()
        shapes.addWave(path, 14.0, 286.0, 150.0, amp, 248.0, PI / 2 + v * 0.5, tapered = false)
        shapes.addWave(path, 14.0, 286.0, 150.0, amp, 248.0, -PI / 2 + v * 0.5, tapered = false)
        canvas.drawPath(path, stroke(INK, 0.9, 1.5f))

        path.rewind()
        shapes.addBars(path, v, 120.0, 13, 5.0, 150.0, 30.0, 0.0)
        canvas.drawPath(path, stroke(INK, 1.0, 2f, Paint.Cap.ROUND))
    }

    /** Stage 2, anywhere: a phone carries the waves; sound ripples out on both sides. */
    private fun anywhere(canvas: Canvas, t: Double, reduced: Boolean) {
        val v = if (reduced) 1.0 else t
        for (i in 0 until 3) ripple(canvas, -1, i, t, reduced)
        for (i in 0 until 3) ripple(canvas, 1, i, t, reduced)

        canvas.drawPath(shapes.PHONE, fill(Color.rgb(6, 6, 8), 1.0))
        canvas.drawPath(shapes.PHONE, stroke(INK, 0.9, 1.6f))

        val saved = canvas.save()
        canvas.clipPath(shapes.PHONE_CLIP)
        canvas.drawPath(shapes.FAINT, stroke(INK, 0.16, 1f))
        path.rewind()
        for (i in 0 until 3) {
            // The swell slowly trades shallow ripples for a broad S, as on the reference.
            val broad = 0.5 + 0.5 * sin(v * 0.55 + i * 0.9)
            shapes.addWave(path, 84.0, 216.0, 150.0, 18.0 + 10.0 * broad, 66.0 + 30.0 * broad, v * 1.2 + i * (QuietSignalOnboardingShapes.TAU / 3), tapered = false)
        }
        canvas.drawPath(path, stroke(INK, 0.85, 1.4f))
        canvas.restoreToCount(saved)

        path.rewind()
        shapes.addBars(path, v, 28.0, 8, 5.0, 150.0, 26.0, 1.0)
        canvas.drawPath(path, stroke(INK, 1.0, 2f, Paint.Cap.ROUND))
        path.rewind()
        shapes.addBars(path, v, 236.0, 8, 5.0, 150.0, 26.0, 4.0)
        canvas.drawPath(path, stroke(INK, 1.0, 2f, Paint.Cap.ROUND))
    }

    /** One soft ring of light swelling out of a speaker and fading, clipped to its own 46-unit strip. */
    private fun ripple(canvas: Canvas, side: Int, index: Int, t: Double, reduced: Boolean) {
        val k = if (reduced) 0.3 + index * 0.25 else (t * 0.5 + index / 3.0) % 1.0
        val radius = (16.0 + k * 40.0).toFloat()
        val opacity = if (reduced) 0.35 else 0.75 * sin(min(1.0, k * 1.1) * PI)
        val cx = if (side < 0) 46f else 254f
        val saved = canvas.save()
        canvas.clipRect(if (side < 0) 0f else 254f, 0f, if (side < 0) 46f else 300f, 300f)
        canvas.drawCircle(cx, 150f, radius, shaded(if (side < 0) rippleLeft else rippleRight, opacity))
        canvas.restoreToCount(saved)
    }

    /** Stage 3, rhythm: a soft pulse of rings in the mind, and streams of light passing through. */
    private fun rhythm(canvas: Canvas, t: Double, reduced: Boolean) {
        val v = if (reduced) 2.0 else t

        var saved = canvas.save()
        canvas.translate(132f, 158f)
        val pulse = if (reduced) 1.0 else 1.0 + sin(t * 1.6) * 0.06
        canvas.scale(pulse.toFloat(), pulse.toFloat())
        for (i in 0 until 5) {
            canvas.drawCircle(0f, 0f, (70 - i * 12).toFloat(), shaded(pulseRings[i], 0.4 + i * 0.12))
        }
        canvas.restoreToCount(saved)

        saved = canvas.save()
        canvas.translate(132f, 158f)
        canvas.drawCircle(0f, 0f, 11f, stroke(INK, 1.0, 1.4f))
        canvas.drawPath(shapes.KEY, stroke(INK, 1.0, 1.4f, Paint.Cap.ROUND))
        canvas.restoreToCount(saved)

        path.rewind()
        for (i in 0 until 4) {
            val y = 60.0 + i * 30
            val sway = sin(v * 0.7 + i) * 8
            path.moveTo(20f, (y + 70).toFloat())
            path.cubicTo(80f, (y + 20 + sway).toFloat(), 150f, (y + 10 - sway).toFloat(), 230f, (y - 30 + sway).toFloat())
        }
        // Arcs that wrap the back of the head, as on the reference.
        for (i in 0 until 4) {
            val a = -2.2 + sin(v * 0.4 + i) * 0.1
            shapes.addArc(path, 150.0, 150.0, 96.0 + i * 12, a, a + 1.5 - i * 0.2)
        }
        canvas.drawPath(path, stroke(INK, 0.16, 1f))

        // The travelling highlight: each contour trimmed to the same stretch (`start`/`end`). The
        // Skia `Blur blur={0.4}` on it is under half a pixel and is not drawn.
        val flow = if (reduced) 0.0 else (t * 0.18) % 1.0
        val flowEnd = min(1.0, flow + 0.45)
        trim(path, flow, flowEnd)
        canvas.drawPath(trimmed, stroke(INK, 0.5, 1.2f))

        canvas.drawPath(shapes.HEAD, stroke(INK, 0.9, 1.5f))
        canvas.drawPath(shapes.EYE, stroke(INK, 1.0, 1.3f))
    }

    /** Stage 4, welcome: an arc sweeps round, closes into a ring, and the signal line draws itself inside. */
    private fun emblem(canvas: Canvas, t: Double, reduced: Boolean) {
        val timeline = QuietSignalOnboardingTimeline
        val arcAlpha = if (reduced || t < ARC_KNOTS[0] || t > 29.5) 0.0 else min(1.0, timeline.segment(t, ARC_KNOTS[0], 28.6) * 1.6)
        val ringAlpha = if (reduced) 1.0 else timeline.ease(timeline.segment(t, 29.88, 30.167))
        val draw = if (reduced) 1.0 else timeline.ease(timeline.segment(t, 30.5, 31.8))

        if (arcAlpha > 0.0) {
            path.rewind()
            val from = knot(t, ARC_FROM) * PI / 180
            val to = knot(t, ARC_TO) * PI / 180
            shapes.addArc(path, 150.0, 150.0, 84.0, from, to)
            canvas.drawPath(path, stroke(INK, arcAlpha, 6f, Paint.Cap.ROUND))
        }
        if (ringAlpha > 0.0) canvas.drawPath(shapes.RING, stroke(INK, ringAlpha, 6f))
        if (draw > 0.001) {
            trim(shapes.EMBLEM, 0.0, draw)
            canvas.drawPath(trimmed, stroke(INK, 1.0, 6f, Paint.Cap.ROUND, Paint.Join.ROUND))
        }
    }

    /** `knot`: a piecewise-linear track through [values], one value per [ARC_KNOTS] time. */
    private fun knot(t: Double, values: DoubleArray): Double {
        var i = 0
        while (i < ARC_KNOTS.size - 2 && t > ARC_KNOTS[i + 1]) i++
        val k = QuietSignalOnboardingTimeline.clamp((t - ARC_KNOTS[i]) / (ARC_KNOTS[i + 1] - ARC_KNOTS[i]))
        return values[i] + (values[i + 1] - values[i]) * k
    }

    /**
     * Skia's `start`/`end` on a path (`SkTrimPathEffect`): every contour is cut to the same
     * fractions of its own length. The result is in [trimmed].
     */
    private fun trim(source: Path, start: Double, end: Double) {
        trimmed.rewind()
        measure.setPath(source, false)
        do {
            val length = measure.length
            val from = (length * start).toFloat()
            val to = (length * end).toFloat()
            if (from < to) measure.getSegment(from, to, trimmed, true)
        } while (measure.nextContour())
    }

    private fun rippleShader(cx: Float) = RadialGradient(
        cx, 150f, 60f,
        intArrayOf(white(0x00), white(0x24), white(0x00)),
        floatArrayOf(0.25f, 0.62f, 1f),
        Shader.TileMode.CLAMP,
    )

    private fun stroke(
        color: Int,
        opacity: Double,
        width: Float,
        cap: Paint.Cap = Paint.Cap.BUTT,
        join: Paint.Join = Paint.Join.MITER,
    ): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = cap
        paint.strokeJoin = join
        paint.color = color
        paint.alpha = alpha255(opacity)
        return paint
    }

    private fun fill(color: Int, opacity: Double): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = alpha255(opacity)
        return paint
    }

    /** A fill by [shader], faded by [opacity]; the colour is made opaque first so only [opacity] scales it. */
    private fun shaded(shader: Shader, opacity: Double): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.shader = shader
        paint.alpha = alpha255(opacity)
        return paint
    }

    private fun alpha255(opacity: Double): Int = (opacity * 255.0).roundToInt().coerceIn(0, 255)

    private fun white(alpha: Int): Int = Color.argb(alpha, 255, 255, 255)

    private companion object {
        /** `INK` and `DIM`. */
        val INK = Color.rgb(0xF2, 0xF2, 0xF7)
        val DIM = Color.rgb(0x8E, 0x8E, 0x99)

        /** The lengths of the five lines of text on the document, before they grow and shrink. */
        val LINE_LENGTHS = doubleArrayOf(86.0, 74.0, 86.0, 58.0, 70.0)

        /** `ARC_KNOTS`: the times of the emblem's travelling arc, and its start and end angles in degrees at each. */
        val ARC_KNOTS = doubleArrayOf(28.333, 28.5, 28.667, 28.833, 29.0, 29.167, 29.333, 29.45)
        val ARC_FROM = doubleArrayOf(180.0, 215.0, 280.0, 325.0, 405.0, 500.0, 595.0, 650.0)
        val ARC_TO = doubleArrayOf(180.1, 325.0, 415.0, 490.0, 560.0, 605.0, 630.0, 650.1)
    }
}
