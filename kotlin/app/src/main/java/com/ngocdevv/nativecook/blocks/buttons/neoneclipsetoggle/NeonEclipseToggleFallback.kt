package com.ngocdevv.nativecook.blocks.buttons.neoneclipsetoggle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The same sRGB field as the AGSL shader, sampled at 2 dp / 30 fps below API 33. */
internal class NeonEclipseToggleFallback {
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bounds = RectF()
    private var renderedAt = 0L
    private var renderedWidth = -1f
    private var renderedX = Float.NaN
    private var renderedY = Float.NaN
    private var renderedPos = Float.NaN
    private var renderedFill = Float.NaN
    private var renderedDark = Float.NaN
    private var renderedPhase = Float.NaN

    // Reused scalar output registers keep the inner sampling loop allocation-free.
    private var red = 0f
    private var green = 0f
    private var blue = 0f
    private var alpha = 0f
    private var paletteRed = 0f
    private var paletteGreen = 0f
    private var paletteBlue = 0f
    private var boxX = 0f
    private var boxY = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var knobX = 0f
    private var fieldFill = 0f
    private var fieldDark = 0f
    private var fieldPhase = 0f
    private var floodFront = 0f
    private var floodAmount = 0f

    fun draw(
        canvas: Canvas,
        width: Float,
        x0: Float,
        y0: Float,
        pos: Float,
        fill: Float,
        dark: Float,
        phase: Float,
    ) {
        if (width <= 0f) return
        val pixelWidth = ceil(width / 2f).toInt().coerceAtLeast(1)
        val pixelHeight = 234 // (108 + 360) / 2
        val resized = bitmap?.width != pixelWidth || bitmap?.height != pixelHeight
        if (resized) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888).apply {
                density = Bitmap.DENSITY_NONE
            }
            pixels = IntArray(pixelWidth * pixelHeight)
        }
        val now = System.nanoTime()
        val geometryChanged = width != renderedWidth || x0 != renderedX || y0 != renderedY
        val stateChanged = pos != renderedPos || fill != renderedFill || dark != renderedDark
        // In the completely unlit state the shader is independent of its colour phase.
        val phaseChanged = phase != renderedPhase && (fill != 0f || dark != 0f)
        if (resized || geometryChanged ||
            ((stateChanged || phaseChanged) && now - renderedAt >= 33_333_333L)
        ) {
            boxX = x0
            boxY = y0
            centerX = x0 + 118f
            centerY = y0 + 54f
            knobX = x0 + 54f + 128f * pos
            fieldFill = fill
            fieldDark = dark
            fieldPhase = phase
            floodFront = knobX - fill * 296f
            floodAmount = smooth(0f, 0.15f, fill)
            val stepX = width / pixelWidth
            val floorY = y0 + 134f
            var index = 0
            for (row in 0 until pixelHeight) {
                val py = y0 - 160f + (row + 0.5f) * 2f
                val reflected = py >= floorY
                val sampleY = if (reflected) 2f * floorY - py else py
                val attenuation = if (reflected) 0.22f * exp(-(py - floorY) / 40f) else 1f
                for (column in 0 until pixelWidth) {
                    scene((column + 0.5f) * stepX, sampleY)
                    val r = (red * attenuation).coerceIn(0f, 1f)
                    val g = (green * attenuation).coerceIn(0f, 1f)
                    val b = (blue * attenuation).coerceIn(0f, 1f)
                    val a = max(alpha * attenuation, max(r, max(g, b))).coerceIn(0f, 1f)
                    // setPixels accepts straight colour; the shader emits premultiplied colour.
                    pixels[index++] = if (a <= 0f) {
                        0
                    } else {
                        val inverseAlpha = 255f / a
                        ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or
                            ((r * inverseAlpha).roundToInt().coerceIn(0, 255) shl 16) or
                            ((g * inverseAlpha).roundToInt().coerceIn(0, 255) shl 8) or
                            (b * inverseAlpha).roundToInt().coerceIn(0, 255)
                    }
                }
            }
            bitmap!!.setPixels(pixels, 0, pixelWidth, 0, 0, pixelWidth, pixelHeight)
            renderedAt = now
            renderedWidth = width
            renderedX = x0
            renderedY = y0
            renderedPos = pos
            renderedFill = fill
            renderedDark = dark
            renderedPhase = phase
        }
        bounds.set(0f, y0 - 160f, width, y0 + 308f)
        canvas.drawBitmap(bitmap!!, null, bounds, paint)
    }

    private fun scene(px: Float, py: Float) {
        val qbx = abs(px - centerX) - 64f
        val qby = abs(py - centerY)
        val ox = max(qbx, 0f)
        val oy = max(qby, 0f)
        val distanceBox = sqrt(ox * ox + oy * oy) + min(max(qbx, qby), 0f) - 54f
        val dx = px - knobX
        val dy = py - centerY
        val distanceKnobCenter = sqrt(dx * dx + dy * dy)
        val distanceKnob = distanceKnobCenter - 46f
        val inside = 1f - smooth(-0.75f, 0.75f, distanceBox)
        val rim = (1f - smooth(0f, 1.6f, abs(distanceBox + 0.8f))) * (1f - 0.6f * fieldFill)
        var r = 0.1f * inside + 0.36f * rim
        var g = r
        var b = r
        var a = max(inside, rim * 0.8f)

        if (fieldFill != 0f) {
            val qx = px - boxX
            val qy = py - boxY
            val h = 0.35f * sin(qx * 0.022f + fieldPhase) +
                0.25f * sin(qy * 0.05f - 2f * fieldPhase + qx * 0.012f) +
                0.2f * sin(distanceKnobCenter * 0.045f - 3f * fieldPhase)
            palette(h + fieldPhase / 6.2831853f)
            val plasmaR = paletteRed
            val plasmaG = paletteGreen
            val plasmaB = paletteBlue
            val band = smooth(
                0.15f,
                0.85f,
                0.5f + 0.5f * sin(qx * 0.045f - 2f * fieldPhase + 1.6f * sin(qy * 0.07f + fieldPhase)),
            )
            val liquidMix = 0.25f + 0.75f * band
            val liquidR = mix(0.06f, plasmaR * 1.15f, liquidMix)
            val liquidG = mix(0.03f, plasmaG * 1.15f, liquidMix)
            val liquidB = mix(0.08f, plasmaB * 1.15f, liquidMix)
            val flood = smooth(floodFront - 26f, floodFront + 4f, px) * floodAmount
            val glow = exp(-max(distanceBox, 0f) / 13f) * fieldFill * (1f - inside) * 0.6f
            r = mix(0.1f, liquidR, flood) * inside + 0.36f * rim + plasmaR * glow
            g = mix(0.1f, liquidG, flood) * inside + 0.36f * rim + plasmaG * glow
            b = mix(0.1f, liquidB, flood) * inside + 0.36f * rim + plasmaB * glow
            a = max(a, glow)
        }

        val knobInside = 1f - smooth(-0.75f, 0.75f, distanceKnob)
        val nx = dx / 46f
        val ny = dy / 46f
        val shade = 0.9f + 0.1f * (-ny * 0.8f - nx * 0.4f).coerceIn(-1f, 1f) -
            0.12f * (nx * nx + ny * ny)
        val knob = mix(shade, 0.015f, fieldDark)
        r = mix(r, knob, knobInside)
        g = mix(g, knob, knobInside)
        b = mix(b, knob, knobInside)
        a = mix(a, 1f, knobInside)

        if (fieldDark != 0f) {
            palette(atan2(dy, dx) / 6.2831853f + fieldPhase / 6.2831853f * 2f)
            val ring = exp(-abs(distanceKnob) / 2.2f) * fieldDark
            val halo = exp(-max(distanceKnob, 0f) / 9f) * 0.5f * fieldDark * (1f - knobInside)
            val corona = (ring + halo) * 1.25f
            r += paletteRed * corona
            g += paletteGreen * corona
            b += paletteBlue * corona
            a = max(a, min(1f, ring + halo))
        }
        red = r
        green = g
        blue = b
        alpha = a
    }

    private fun palette(hue: Float) {
        val x = (hue - floor(hue)) * 4f
        when {
            x < 1f -> {
                val t = smooth(0f, 1f, x)
                paletteRed = 1f
                paletteGreen = mix(0.12f, 0.5f, t)
                paletteBlue = mix(0.62f, 0.08f, t)
            }
            x < 2f -> {
                val t = smooth(1f, 2f, x)
                paletteRed = 1f
                paletteGreen = mix(0.5f, 0.9f, t)
                paletteBlue = mix(0.08f, 0.18f, t)
            }
            x < 3f -> {
                val t = smooth(2f, 3f, x)
                paletteRed = mix(1f, 0.52f, t)
                paletteGreen = mix(0.9f, 0.22f, t)
                paletteBlue = mix(0.18f, 1f, t)
            }
            else -> {
                val t = smooth(3f, 4f, x)
                paletteRed = mix(0.52f, 1f, t)
                paletteGreen = mix(0.22f, 0.12f, t)
                paletteBlue = mix(1f, 0.62f, t)
            }
        }
    }

    private fun smooth(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
}
