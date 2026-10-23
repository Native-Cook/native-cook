// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.max

/**
 * Bridges from react-native-skia semantics to android.graphics (which is
 * Skia underneath, so blend modes, rrects, sweep angles and mask blurs match):
 *
 * - `BlurMask blur={σ}` is a sigma in local units (respectCTM). Android's
 *   BlurMaskFilter takes a "radius" it converts with
 *   σ = 0.57735·r + 0.5 (uirenderer::Blur), also in local units, so the
 *   radius passed is (σ − 0.5) / 0.57735.
 * - Skia gradients interpolate unpremultiplied colors; Android gradients
 *   interpolate premultiplied (Shader.cpp sets kInterpolateColorsInPremul).
 *   Where a gradient's stops differ in alpha, the segment is subdivided into
 *   unpremultiplied sub-stops so the ramp matches (e.g. color → #00000000).
 * - `interpolateColors` is per-channel linear interpolation of the 0…1 sRGB
 *   components with clamped extrapolation.
 */
object SkiaCompat {
    fun blurRadius(sigma: Float): Float = max(0f, (sigma - 0.5f) / 0.57735f)

    fun blur(sigma: Float) = BlurMaskFilter(blurRadius(sigma).coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL)

    /** Image-filter blur radius for RenderEffect / Compose BlurEffect (same conversion). */
    fun renderEffectRadius(sigmaPx: Float): Float = blurRadius(sigmaPx)

    class C(val r: Float, val g: Float, val b: Float, val a: Float) {
        fun toLong(): Long = Color.pack(r, g, b, a)
        fun withAlpha(k: Float) = C(r, g, b, a * k)
    }

    fun c(argb: Long): C = C(
        ((argb shr 16) and 0xFF) / 255f,
        ((argb shr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f,
        ((argb shr 24) and 0xFF) / 255f,
    )

    /** CSS #RRGGBBAA as used in the RN source (alpha last). */
    fun rgba(hex: String): C {
        val h = hex.removePrefix("#")
        val r = h.substring(0, 2).toInt(16) / 255f
        val g = h.substring(2, 4).toInt(16) / 255f
        val b = h.substring(4, 6).toInt(16) / 255f
        val a = if (h.length >= 8) h.substring(6, 8).toInt(16) / 255f else 1f
        return C(r, g, b, a)
    }

    private fun interp(value: Double, input: DoubleArray, output: FloatArray): Float {
        if (value <= input[0]) return output[0]
        if (value >= input.last()) return output.last()
        var i = 1
        while (i < input.size - 1 && value > input[i]) i++
        val t = (value - input[i - 1]) / (input[i] - input[i - 1])
        return (output[i - 1] + (output[i] - output[i - 1]) * t).toFloat()
    }

    fun interpolateColors(value: Double, input: DoubleArray, colors: List<C>): C = C(
        interp(value, input, FloatArray(colors.size) { colors[it].r }),
        interp(value, input, FloatArray(colors.size) { colors[it].g }),
        interp(value, input, FloatArray(colors.size) { colors[it].b }),
        interp(value, input, FloatArray(colors.size) { colors[it].a }),
    )

    private const val SUBSTEPS = 16

    /** Stops with unpremultiplied interpolation baked in. */
    fun stops(colors: List<C>, positions: FloatArray?): Pair<LongArray, FloatArray> {
        val pos = positions ?: FloatArray(colors.size) { if (colors.size == 1) 0f else it / (colors.size - 1f) }
        val outC = ArrayList<Long>()
        val outP = ArrayList<Float>()
        for (i in colors.indices) {
            if (i > 0 && colors[i].a != colors[i - 1].a) {
                val a = colors[i - 1]
                val b = colors[i]
                for (s in 1 until SUBSTEPS) {
                    val t = s / SUBSTEPS.toFloat()
                    outC.add(
                        C(
                            a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t,
                            a.b + (b.b - a.b) * t, a.a + (b.a - a.a) * t,
                        ).toLong(),
                    )
                    outP.add(pos[i - 1] + (pos[i] - pos[i - 1]) * t)
                }
            }
            outC.add(colors[i].toLong())
            outP.add(pos[i])
        }
        return outC.toLongArray() to outP.toFloatArray()
    }

    fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: List<C>, positions: FloatArray? = null): Shader {
        val (cs, ps) = stops(colors, positions)
        return LinearGradient(x0, y0, x1, y1, cs, ps, Shader.TileMode.CLAMP)
    }

    fun radial(cx: Float, cy: Float, r: Float, colors: List<C>, positions: FloatArray? = null): Shader {
        val (cs, ps) = stops(colors, positions)
        return RadialGradient(cx, cy, r.coerceAtLeast(0.001f), cs, ps, Shader.TileMode.CLAMP)
    }

    fun sweep(cx: Float, cy: Float, colors: List<C>, positions: FloatArray? = null): Shader {
        val (cs, ps) = stops(colors, positions)
        return SweepGradient(cx, cy, cs, ps)
    }

    fun colors(vararg argb: Long) = argb.map { c(it) }
}

/**
 * RN Skia `font.measureText(text).width`: the width of the glyph ink bounds
 * (SkFont::measureText's bounds), not the advance — a space measures 0.
 */
fun android.graphics.Paint.skiaMeasureWidth(text: String): Float {
    val path = android.graphics.Path()
    getTextPath(text, 0, text.length, 0f, 0f, path)
    val r = android.graphics.RectF()
    path.computeBounds(r, true)
    return if (r.isEmpty) 0f else r.width()
}
