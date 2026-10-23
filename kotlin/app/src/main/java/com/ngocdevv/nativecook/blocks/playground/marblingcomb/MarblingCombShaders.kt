package com.ngocdevv.nativecook.blocks.playground.marblingcomb

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The two small shaders of the block, the React Native SkSL unchanged: AGSL is the same dialect
// (API 33+). Both return premultiplied colours: white light where the value is positive, black shade
// where it is negative, drawn over the paint. Coordinates are dp: the canvas is scaled by the density
// before they are drawn.

private const val HASH = """
float hash(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
"""

/** Light on the size: a broad sheen drifting slowly, pigment grain, and a soft vignette. */
private const val WATER_AGSL = """
uniform float2 size;
uniform float phase;
$HASH
half4 main(float2 p) {
  float2 uv = p / size;
  float band = uv.x * 0.62 + uv.y * 0.42 - 0.36 - 0.05 * sin(phase);
  float sheen = exp(-band * band / 0.02) * 0.075 + exp(-band * band / 0.0012) * 0.035;
  float grain = (hash(floor(p * 2.0)) - 0.5) * 0.05;
  float2 c = (uv - 0.5) * float2(1.0, size.y / size.x * 0.62);
  float shade = smoothstep(0.22, 0.86, length(c)) * 0.46;
  float light = sheen + grain;
  float w = max(light, 0.0);
  float k = max(-light, 0.0) + shade;
  return half4(w, w, w, w + (1.0 - w) * min(k, 1.0));
}
"""

/** The paper's fine tooth plus long, faint fibres. */
private const val PAPER_AGSL = """
uniform float strength;
$HASH
float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  float a = hash(i);
  float b = hash(i + float2(1.0, 0.0));
  float c = hash(i + float2(0.0, 1.0));
  float d = hash(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
half4 main(float2 p) {
  float tooth = hash(floor(p * 2.0)) - 0.5;
  float fibre = noise(float2(p.x * 0.06, p.y * 0.9)) - 0.5;
  float cloud = noise(p * 0.035) - 0.5;
  float v = (tooth * 0.5 + fibre * 0.7 + cloud * 0.9) * strength;
  float w = max(v, 0.0);
  float k = max(-v, 0.0);
  return half4(w, w, w, w + (1.0 - w) * k);
}
"""

/** The paper's grain strength. */
internal const val PAPER_GRAIN = 0.09f

/** The water and paper paints: runtime shaders on Android 13+, gradients and a noise tile below. */
internal class MarbleShaders {
    val water = Paint()
    val paper = Paint()
    private val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    private var waterShader: Any? = null
    private var fallback: MarbleFallback? = null

    init {
        if (modern) setUpRuntime() else fallback = MarbleFallback()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setUpRuntime() {
        val w = RuntimeShader(WATER_AGSL)
        waterShader = w
        water.shader = w
        val p = RuntimeShader(PAPER_AGSL)
        p.setFloatUniform("strength", PAPER_GRAIN)
        paper.shader = p
    }

    /** Points the water at a tray `width` × `height` dp and its sheen at `phase`. */
    fun update(width: Float, height: Float, phase: Float) {
        if (modern) {
            updateRuntime(width, height, phase)
        } else {
            fallback?.updateWater(water, width, height, phase)
            if (paper.shader == null) paper.shader = fallback?.paperShader()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun updateRuntime(width: Float, height: Float, phase: Float) {
        val w = waterShader as RuntimeShader
        w.setFloatUniform("size", max(1f, width), max(1f, height))
        w.setFloatUniform("phase", phase)
        water.shader = w
    }
}

// ─── Below Android 13 ────────────────────────────────────────────────────────────────────────────
// No runtime shaders: the sheen is a linear gradient sampled from the shader's own band profile, the
// vignette an elliptical radial gradient sampled from its smoothstep, and the grain and the paper's
// tooth and fibres are tiles computed once on the CPU with the shader's own hash and noise.

private fun fract(v: Double) = v - floor(v)

private fun hash(x: Double, y: Double): Double {
    var qx = fract(x * 0.1031)
    var qy = fract(y * 0.1031)
    var qz = fract(x * 0.1031)
    val d = qx * (qy + 33.33) + qy * (qz + 33.33) + qz * (qx + 33.33)
    qx += d
    qy += d
    qz += d
    return fract((qx + qy) * qz)
}

private fun noise(x: Double, y: Double): Double {
    val ix = floor(x)
    val iy = floor(y)
    var fx = x - ix
    var fy = y - iy
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    val a = hash(ix, iy)
    val b = hash(ix + 1, iy)
    val c = hash(ix, iy + 1)
    val d = hash(ix + 1, iy + 1)
    val top = a + (b - a) * fx
    val bottom = c + (d - c) * fx
    return top + (bottom - top) * fy
}

/** A signed value as the shaders' premultiplied light (white) or shade (black). */
private fun signed(v: Double): Int {
    val a = (min(1.0, kotlin.math.abs(v)) * 255).toInt()
    return if (v >= 0) Color.argb(a, 255, 255, 255) else Color.argb(a, 0, 0, 0)
}

private class MarbleFallback {
    // Tiles of TILE dp at two texels per dp, as the shaders' grain cells are half a dp.
    private val tile = 256
    private val grain: Bitmap = Bitmap.createBitmap(tile * 2, tile * 2, Bitmap.Config.ARGB_8888).apply {
        val px = IntArray(width * height)
        for (j in 0 until height) for (i in 0 until width) px[j * width + i] = signed((hash(i.toDouble(), j.toDouble()) - 0.5) * 0.05)
        setPixels(px, 0, width, 0, 0, width, height)
    }
    private val grainShader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(Matrix().apply { setScale(0.5f, 0.5f) })
    }
    private var key = Float.NaN
    private var sizeKey = 0f

    fun paperShader(): Shader {
        val bitmap = Bitmap.createBitmap(tile * 2, tile * 2, Bitmap.Config.ARGB_8888)
        val px = IntArray(bitmap.width * bitmap.height)
        for (j in 0 until bitmap.height) {
            for (i in 0 until bitmap.width) {
                val x = i * 0.5
                val y = j * 0.5
                val tooth = hash(i.toDouble(), j.toDouble()) - 0.5
                val fibre = noise(x * 0.06, y * 0.9) - 0.5
                val cloud = noise(x * 0.035, y * 0.035) - 0.5
                px[j * bitmap.width + i] = signed((tooth * 0.5 + fibre * 0.7 + cloud * 0.9) * PAPER_GRAIN)
            }
        }
        bitmap.setPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(0.5f, 0.5f) })
        }
    }

    fun updateWater(paint: Paint, width: Float, height: Float, phase: Float) {
        // The band only moves a little each frame; rebuild it when it has moved a visible amount.
        val c = 0.36 + 0.05 * sin(phase.toDouble())
        if (kotlin.math.abs(c - key) < 0.0005 && sizeKey == width + height * 7) return
        key = c.toFloat()
        sizeKey = width + height * 7
        val w = max(1f, width).toDouble()
        val h = max(1f, height).toDouble()
        // band(p) = g·p − c with g = (0.62 / w, 0.42 / h): a linear gradient across it.
        val gx = 0.62 / w
        val gy = 0.42 / h
        val g2 = gx * gx + gy * gy
        val reach = 0.4
        val steps = 25
        val colors = IntArray(steps)
        val stops = FloatArray(steps)
        for (s in 0 until steps) {
            val t = s.toDouble() / (steps - 1)
            val band = -reach + 2 * reach * t
            val sheen = exp(-band * band / 0.02) * 0.075 + exp(-band * band / 0.0012) * 0.035
            colors[s] = signed(sheen)
            stops[s] = t.toFloat()
        }
        val x0 = gx * (c - reach) / g2
        val y0 = gy * (c - reach) / g2
        val x1 = gx * (c + reach) / g2
        val y1 = gy * (c + reach) / g2
        val sheen = LinearGradient(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), colors, stops, Shader.TileMode.CLAMP)
        // The vignette: smoothstep(0.22, 0.86, |c|) · 0.46, c = ((x − w/2) / w, (y − h/2) / w · 0.62).
        val vColors = IntArray(steps)
        val vStops = FloatArray(steps)
        for (s in 0 until steps) {
            val t = s.toDouble() / (steps - 1)
            // |c| runs from 0 at the centre to 0.86 at the gradient's radius.
            val e = min(1.0, max(0.0, (t * 0.86 - 0.22) / (0.86 - 0.22)))
            vColors[s] = signed(-(e * e * (3 - 2 * e) * 0.46))
            vStops[s] = t.toFloat()
        }
        val vignette = RadialGradient(0f, 0f, (0.86 * w).toFloat(), vColors, vStops, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(
                Matrix().apply {
                    setScale(1f, (1 / 0.62).toFloat())
                    postTranslate((w / 2).toFloat(), (h / 2).toFloat())
                },
            )
        }
        paint.shader = ComposeShader(ComposeShader(vignette, sheen, PorterDuff.Mode.SRC_OVER), grainShader, PorterDuff.Mode.SRC_OVER)
    }
}

/** Darkens a colour for an outline's rim, as the RN `shadeHexWorklet`. */
internal fun marbleShade(argb: Int, k: Double): Int {
    val r = Math.round(((argb shr 16) and 255) * k).toInt()
    val g = Math.round(((argb shr 8) and 255) * k).toInt()
    val b = Math.round((argb and 255) * k).toInt()
    return Color.rgb(r, g, b)
}
