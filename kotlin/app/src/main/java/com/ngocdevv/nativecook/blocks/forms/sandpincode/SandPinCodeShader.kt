package com.ngocdevv.nativecook.blocks.forms.sandpincode

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Sand PIN Code — the zen tray, one full-screen pass (the React Native SkSL in AGSL, without the
 Display P3 conversion). A height field lit from the upper left: parallel rake lines (8 dp pitch)
 that wander with a low value noise and curve into rings around a basalt stone, six shallow slot
 depressions, and while unlocking a ring packet travelling out from the slots' centre. `bright`
 lifts the exposure for the unlock. Uniforms are in canvas dp.

 Below Android 13 `sandRenderRest` evaluates the same field on the CPU once, off the main thread,
 at one pixel per dp, for the resting tray (no ripple); the unlock's light and rings are drawn
 over it as gradients.
*/

internal const val SAND_AGSL = """
uniform float2 res;
uniform float4 slots;
uniform float3 stone;
uniform float3 ripple;
uniform float bright;

float hash21(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vnoise(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  float a = hash21(i);
  float b = hash21(i + float2(1.0, 0.0));
  float c = hash21(i + float2(0.0, 1.0));
  float d = hash21(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float slotDepth(float2 p) {
  float i = clamp(floor((p.x - slots.x) / slots.z + 0.5), 0.0, 5.0);
  float2 c = float2(slots.x + i * slots.z, slots.y);
  float2 hb = float2(slots.z * 0.36, slots.w);
  float2 d = abs(p - c) - hb + 10.0;
  float sd = length(max(d, float2(0.0))) + min(max(d.x, d.y), 0.0) - 10.0;
  return 1.0 - smoothstep(-6.0, 3.0, sd);
}

float height(float2 p) {
  float2 d = p - stone.xy;
  float r = length(d);
  float wander = vnoise(p * 0.011) * 16.0 + 5.0 * sin(p.x * 0.017);
  float par = p.y + wander;
  float around = smoothstep(stone.z * 3.0, stone.z * 1.45, r);
  float phase = mix(par, r * 1.02, around);
  float h = 0.62 * sin(phase * 0.785398);
  float s = slotDepth(p);
  h = mix(h, -1.6, s);
  if (ripple.z >= 0.0) {
    float rr = length(p - ripple.xy);
    float front = ripple.z * 300.0;
    float env = exp(-pow((rr - front) / 46.0, 2.0)) * exp(-ripple.z * 1.4);
    h += 1.5 * sin((rr - front) * 0.24) * env;
  }
  return h;
}

half4 main(float2 p) {
  float e = 1.0;
  float hc = height(p);
  float hx = height(p + float2(e, 0.0));
  float hy = height(p + float2(0.0, e));
  float3 n = normalize(float3(-(hx - hc) * 1.6, -(hy - hc) * 1.6, 1.0));
  float3 L = normalize(float3(-0.55, -0.62, 0.56));
  float diff = clamp(dot(n, L), 0.0, 1.0);
  float3 H = normalize(L + float3(0.0, 0.0, 1.0));
  float spec = pow(clamp(dot(n, H), 0.0, 1.0), 26.0) * 0.12;

  float tone = vnoise(p * 0.006 + 3.1);
  float3 sandA = float3(0.874, 0.788, 0.651);
  float3 sandB = float3(0.812, 0.706, 0.553);
  float3 base = mix(sandA, sandB, tone);
  float grain = hash21(floor(p * 1.4)) - 0.5;
  base *= 1.0 + grain * 0.07;
  base *= 1.0 - 0.06 * slotDepth(p);

  float3 col = base * (0.58 + 0.52 * diff) + spec;

  float r = length(p - stone.xy);
  float sh = smoothstep(stone.z * 1.5, stone.z * 0.9, length(p - stone.xy - float2(6.0, 8.0)));
  col *= 1.0 - 0.32 * sh;
  if (r < stone.z + 1.0) {
    float2 q = (p - stone.xy) / stone.z;
    float z = sqrt(max(0.0, 1.0 - dot(q, q)));
    float3 sn = normalize(float3(q, z));
    float sd = clamp(dot(sn, L), 0.0, 1.0);
    float ss = pow(clamp(dot(sn, H), 0.0, 1.0), 40.0);
    float speck = vnoise(p * 0.35) * 0.08;
    float3 rock = float3(0.20, 0.205, 0.21) * (0.45 + 0.75 * sd) + ss * 0.22 + speck;
    col = mix(col, rock, smoothstep(stone.z + 1.0, stone.z - 1.0, r));
  }

  float2 uv = p / res;
  float v = smoothstep(1.15, 0.35, length((uv - float2(0.5, 0.45)) * float2(1.0, 1.25)));
  col *= mix(0.78, 1.0, v);
  col = mix(col, col * 1.1 + float3(0.06, 0.045, 0.02), bright);

  col = clamp(col, 0.0, 1.0);
  return half4(half3(col), 1.0);
}
"""

// ---- CPU copy of the field, for the fallback --------------------------------------------------

private fun fract(x: Double) = x - floor(x)
private fun hash21(x: Double, y: Double) = fract(sin(x * 127.1 + y * 311.7) * 43758.5453)
private fun smooth(e0: Double, e1: Double, x: Double): Double {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}
private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t

private fun vnoise(x: Double, y: Double): Double {
    val ix = floor(x)
    val iy = floor(y)
    var fx = x - ix
    var fy = y - iy
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    val a = hash21(ix, iy)
    val b = hash21(ix + 1, iy)
    val c = hash21(ix, iy + 1)
    val d = hash21(ix + 1, iy + 1)
    return mix(mix(a, b, fx), mix(c, d, fx), fy)
}

private class SandField(val sx: Double, val sy: Double, val gap: Double, val halfH: Double, val stX: Double, val stY: Double, val stR: Double) {
    fun slotDepth(px: Double, py: Double): Double {
        val i = floor((px - sx) / gap + 0.5).coerceIn(0.0, 5.0)
        val cx = sx + i * gap
        val dx = abs(px - cx) - gap * 0.36 + 10
        val dy = abs(py - sy) - halfH + 10
        val sd = sqrt(max(dx, 0.0).pow(2) + max(dy, 0.0).pow(2)) + min(max(dx, dy), 0.0) - 10
        return 1 - smooth(-6.0, 3.0, sd)
    }

    fun height(px: Double, py: Double): Double {
        val r = sqrt((px - stX).pow(2) + (py - stY).pow(2))
        val wander = vnoise(px * 0.011, py * 0.011) * 16 + 5 * sin(px * 0.017)
        val par = py + wander
        val around = smooth(stR * 3, stR * 1.45, r)
        val phase = mix(par, r * 1.02, around)
        val h = 0.62 * sin(phase * 0.785398)
        return mix(h, -1.6, slotDepth(px, py))
    }
}

/** The resting tray, one pixel per dp, over `w × h` canvas dp. */
internal fun sandRenderRest(w: Int, h: Int, slots: FloatArray, stone: FloatArray): Bitmap {
    val f = SandField(slots[0].toDouble(), slots[1].toDouble(), slots[2].toDouble(), slots[3].toDouble(), stone[0].toDouble(), stone[1].toDouble(), stone[2].toDouble())
    val lx = -0.55; val ly = -0.62; val lz = 0.56
    val ll = sqrt(lx * lx + ly * ly + lz * lz)
    val Lx = lx / ll; val Ly = ly / ll; val Lz = lz / ll
    val hl = sqrt(Lx * Lx + Ly * Ly + (Lz + 1) * (Lz + 1))
    val Hx = Lx / hl; val Hy = Ly / hl; val Hz = (Lz + 1) / hl
    val px = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val p0 = x + 0.5
            val p1 = y + 0.5
            val hc = f.height(p0, p1)
            val hx = f.height(p0 + 1, p1)
            val hy = f.height(p0, p1 + 1)
            var nx = -(hx - hc) * 1.6
            var ny = -(hy - hc) * 1.6
            var nz = 1.0
            val nl = sqrt(nx * nx + ny * ny + nz * nz)
            nx /= nl; ny /= nl; nz /= nl
            val diff = (nx * Lx + ny * Ly + nz * Lz).coerceIn(0.0, 1.0)
            val spec = (nx * Hx + ny * Hy + nz * Hz).coerceIn(0.0, 1.0).pow(26.0) * 0.12
            val tone = vnoise(p0 * 0.006 + 3.1, p1 * 0.006 + 3.1)
            var r = mix(0.874, 0.812, tone)
            var g = mix(0.788, 0.706, tone)
            var b = mix(0.651, 0.553, tone)
            val grain = hash21(floor(p0 * 1.4), floor(p1 * 1.4)) - 0.5
            val k = (1 + grain * 0.07) * (1 - 0.06 * f.slotDepth(p0, p1))
            val lit = 0.58 + 0.52 * diff
            r = r * k * lit + spec; g = g * k * lit + spec; b = b * k * lit + spec
            val sr = sqrt((p0 - f.stX - 6).pow(2) + (p1 - f.stY - 8).pow(2))
            val sh = 1 - 0.32 * smooth(f.stR * 1.5, f.stR * 0.9, sr)
            r *= sh; g *= sh; b *= sh
            val rr = sqrt((p0 - f.stX).pow(2) + (p1 - f.stY).pow(2))
            if (rr < f.stR + 1) {
                val qx = (p0 - f.stX) / f.stR
                val qy = (p1 - f.stY) / f.stR
                val z = sqrt(max(0.0, 1 - qx * qx - qy * qy))
                val sl = sqrt(qx * qx + qy * qy + z * z).coerceAtLeast(1e-6)
                val sd = ((qx * Lx + qy * Ly + z * Lz) / sl).coerceIn(0.0, 1.0)
                val ss = ((qx * Hx + qy * Hy + z * Hz) / sl).coerceIn(0.0, 1.0).pow(40.0)
                val speck = vnoise(p0 * 0.35, p1 * 0.35) * 0.08
                val m = smooth(f.stR + 1, f.stR - 1, rr)
                r = mix(r, 0.20 * (0.45 + 0.75 * sd) + ss * 0.22 + speck, m)
                g = mix(g, 0.205 * (0.45 + 0.75 * sd) + ss * 0.22 + speck, m)
                b = mix(b, 0.21 * (0.45 + 0.75 * sd) + ss * 0.22 + speck, m)
            }
            val ux = (p0 / w - 0.5)
            val uy = (p1 / h - 0.45) * 1.25
            val v = mix(0.78, 1.0, smooth(1.15, 0.35, sqrt(ux * ux + uy * uy)))
            val ri = (r * v).coerceIn(0.0, 1.0) * 255 + 0.5
            val gi = (g * v).coerceIn(0.0, 1.0) * 255 + 0.5
            val bi = (b * v).coerceIn(0.0, 1.0) * 255 + 0.5
            px[y * w + x] = (0xFF shl 24) or (ri.toInt() shl 16) or (gi.toInt() shl 8) or bi.toInt()
        }
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}
