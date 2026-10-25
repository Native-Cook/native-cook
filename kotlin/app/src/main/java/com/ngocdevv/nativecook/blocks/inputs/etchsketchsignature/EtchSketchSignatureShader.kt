package com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature

import android.graphics.Bitmap
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

// The powder screen and the scraped line: the React Native SkSL (etchArt.ts) as AGSL (API 33+),
// without its Display P3 conversion. Positions are dp, in the toy's own (unrotated) coordinates.

/**
 * Aluminium powder on glass: a cool brushed grey with a fine grain, flakes that glint as the toy
 * tilts, a soft inner shadow under the bezel, and a brighter band where the powder resettles.
 */
internal const val ETCH_SCREEN_AGSL = """
uniform float4 rect;
uniform float tilt;
uniform float sweep;
uniform float band;
float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
float box(float2 p, float2 c, float2 e, float r) {
  float2 q = abs(p - c) - e + r;
  return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}
half4 main(float2 p) {
  float2 uv = (p - rect.xy) / rect.zw;
  float3 top = float3(0.89, 0.9, 0.91);
  float3 mid = float3(0.78, 0.79, 0.81);
  float3 bot = float3(0.62, 0.635, 0.665);
  float3 c = uv.y < 0.5 ? mix(top, mid, uv.y * 2.0) : mix(mid, bot, (uv.y - 0.5) * 2.0);
  c *= float3(0.985, 0.995, 1.02);
  float sh = (uv.x * 0.8 + uv.y * 0.6 - 0.38 - tilt * 4.0) / 0.13;
  c += 0.07 * exp(-sh * sh);
  float g = h(floor(p * 2.0));
  float g2 = h(floor(p * 0.6) + 7.0);
  c *= 0.92 + 0.08 * g + 0.05 * (g2 - 0.5);
  float flake = step(0.9, h(floor(p * 1.4) + 13.0));
  float ang = h(floor(p * 1.4) + 29.0) * 6.2831;
  c += 0.32 * flake * pow(max(0.0, cos(ang - tilt * 45.0 - 0.8)), 10.0);
  c += 0.045 * step(0.985, h(floor(p * 2.0) + 41.0));
  float d = box(p, rect.xy + rect.zw * 0.5, rect.zw * 0.5, 14.0);
  c *= 1.0 - 0.4 * smoothstep(-18.0, 0.0, d);
  c *= 1.0 - 0.12 * smoothstep(0.06, 0.0, uv.y);
  float k = (p.y - sweep) / 22.0;
  c += 0.05 * band * exp(-k * k) * (0.4 + g);
  return half4(half3(clamp(c, 0.0, 1.0)), 1.0);
}
"""

/**
 * The scraped line (drawn twice: a dark graphite core and a faint light lip just below it, so it
 * reads as a groove), a little uneven, and erased above a ragged front as the powder resettles.
 * The ink is a plain float4 (straight alpha), set with setFloatUniform.
 */
internal const val ETCH_LINE_AGSL = """
uniform float sweep;
uniform float4 ink;
float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 p) {
  float g = h(floor(p * 2.0));
  float n = h(floor(p * 0.25) + 3.0);
  float edge = sweep + (n - 0.5) * 26.0 + (g - 0.5) * 8.0;
  float keep = smoothstep(edge - 2.0, edge + 2.0, p.y);
  float a = ink.a * (0.82 + 0.18 * g) * keep;
  return half4(half3(ink.rgb) * a, a);
}
"""

/** The shaders' hash on the CPU (for the baked grain and the < API 33 screen). */
internal fun etchHash(x: Float, y: Float): Float {
    val s = sin(x * 12.9898 + y * 78.233) * 43758.5453
    return (s - floor(s)).toFloat()
}

private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/**
 * Below API 33: the screen shader evaluated once on the CPU at rest (no tilt, no sweep), at
 * [density] pixels per dp over the screen rect. Returns the bitmap (its top-left is the rect's).
 */
internal fun etchBakeScreen(x0: Float, y0: Float, w: Float, h: Float, density: Float): Bitmap {
    val pw = max(1, ceil(w * density).toInt())
    val ph = max(1, ceil(h * density).toInt())
    val px = IntArray(pw * ph)
    val cx = x0 + w / 2
    val cy = y0 + h / 2
    for (j in 0 until ph) {
        val py = y0 + (j + 0.5f) / density
        val v = (py - y0) / h
        for (i in 0 until pw) {
            val pxx = x0 + (i + 0.5f) / density
            val u = (pxx - x0) / w
            var r: Float
            var g: Float
            var b: Float
            if (v < 0.5f) {
                val m = v * 2
                r = 0.89f + (0.78f - 0.89f) * m; g = 0.9f + (0.79f - 0.9f) * m; b = 0.91f + (0.81f - 0.91f) * m
            } else {
                val m = (v - 0.5f) * 2
                r = 0.78f + (0.62f - 0.78f) * m; g = 0.79f + (0.635f - 0.79f) * m; b = 0.81f + (0.665f - 0.81f) * m
            }
            r *= 0.985f; g *= 0.995f; b *= 1.02f
            val sh = (u * 0.8f + v * 0.6f - 0.38f) / 0.13f
            val sheen = 0.07f * exp(-sh * sh)
            r += sheen; g += sheen; b += sheen
            val gr = etchHash(floor(pxx * 2), floor(py * 2))
            val g2 = etchHash(floor(pxx * 0.6f) + 7, floor(py * 0.6f) + 7)
            val m1 = 0.92f + 0.08f * gr + 0.05f * (g2 - 0.5f)
            r *= m1; g *= m1; b *= m1
            val fx = floor(pxx * 1.4f)
            val fy = floor(py * 1.4f)
            if (etchHash(fx + 13, fy + 13) >= 0.9f) {
                val ang = etchHash(fx + 29, fy + 29) * 6.2831f
                val add = 0.32f * max(0f, cos(ang - 0.8f)).pow(10)
                r += add; g += add; b += add
            }
            if (etchHash(floor(pxx * 2) + 41, floor(py * 2) + 41) >= 0.985f) {
                r += 0.045f; g += 0.045f; b += 0.045f
            }
            // The rounded box's distance, for the inner shadow under the bezel.
            val qx = abs(pxx - cx) - w / 2 + 14
            val qy = abs(py - cy) - h / 2 + 14
            val d = sqrt(max(qx, 0f).pow(2) + max(qy, 0f).pow(2)) + min(max(qx, qy), 0f) - 14
            val m2 = (1 - 0.4f * smooth(-18f, 0f, d)) * (1 - 0.12f * smooth(0.06f, 0f, v))
            r *= m2; g *= m2; b *= m2
            px[j * pw + i] = (0xFF shl 24) or
                ((r.coerceIn(0f, 1f) * 255 + 0.5f).toInt() shl 16) or
                ((g.coerceIn(0f, 1f) * 255 + 0.5f).toInt() shl 8) or
                (b.coerceIn(0f, 1f) * 255 + 0.5f).toInt()
        }
    }
    return Bitmap.createBitmap(px, pw, ph, Bitmap.Config.ARGB_8888)
}

/** A whole turn in radians. */
private const val TAU = (2 * PI).toFloat()

/** A knob's angle (degrees) for a stylus offset of [d] dp from rest. */
internal fun etchKnobDegrees(d: Float) = d / EtchSketchSignatureMotion.PT_PER_TURN * TAU * (180f / PI.toFloat())
