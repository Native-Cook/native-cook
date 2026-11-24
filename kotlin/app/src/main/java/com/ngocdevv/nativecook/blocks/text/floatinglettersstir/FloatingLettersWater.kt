package com.ngocdevv.nativecook.blocks.text.floatinglettersstir

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi

// The water under Floating Letters Stir: one AGSL pass over the screen, the SkSL of the React Native
// block line for line.
//
// A pale, shallow pool seen from above: a depth gradient (lighter at the top, a deeper teal toward the
// bottom and the corners), two layers of caustics (the bright net sunlight draws on a pool floor, the
// second larger, dimmer and slower for depth), and the rings a finger leaves. Each ring is a travelling
// wave packet: a sine of wavelength 34 under a Gaussian of width 30 whose centre runs outward at 150 dp/s.
// Its slope refracts the caustics and lights the side that faces the light, top left. The same ring
// maths, in FloatingLettersMotion.kt, bobs the letters.
//
// The canvas is scaled to dp before the fill, so `p` arrives in dp; `dpr` turns it back into pixels
// for the dither and the grain. Time enters only as `phase` = 2*pi * ambient / LOOP with whole
// multipliers, so the water at LOOP is the water at 0.

private const val WATER_AGSL = """
uniform float2 size;
uniform float2 focus;      // centre of the phrase, dp
uniform float phase;       // 0..2*pi over one LOOP
uniform float4 rip[8];     // x, y, age (s), strength (0 = unused)
uniform float3 touch;      // finger x, y, presence 0..1
uniform float dpr;

const float TAU = 6.2831853;
const float SPEED = 150.0;
const float LIFE = 2.80;
const float WIDTH = 30.0;
const float KW = 0.18480;

float3 lin(float3 c) { return pow(c, float3(2.2)); }

float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }

// Height (x) and slope / wave number (yz) of every ring at p.
float3 rings(float2 p) {
  float3 acc = float3(0.0);
  for (int k = 0; k < 8; k++) {
    float4 r = rip[k];
    float2 dv = p - r.xy;
    float d = max(length(dv), 0.001);
    float off = d - SPEED * r.z;
    float env = r.w * smoothstep(0.0, 0.15, r.z) * pow(1.0 - clamp(r.z / LIFE, 0.0, 1.0), 1.5);
    float g = exp(-(off * off) / (WIDTH * WIDTH));
    float sn = sin(off * KW);
    float cs = cos(off * KW);
    float dh = env * g * (cs - (2.0 * off / (WIDTH * WIDTH * KW)) * sn);
    acc += float3(env * g * sn, dh * dv / d);
  }
  return acc;
}

// The caustic net: a few rounds of a domain warp, bright where the warped coordinates bunch up.
// uv tiles every 1.0; turn is the animation angle and takes whole multipliers so it loops.
float caustic(float2 uv, float turn) {
  float2 p = mod(uv * TAU, TAU) - 250.0;
  float2 i = p;
  float c = 1.0;
  const float inten = 0.005;
  for (int n = 0; n < 4; n++) {
    float m = n == 0 ? -2.0 : (n == 1 ? -1.0 : (n == 2 ? 1.0 : 2.0));
    float t = turn * m + float(n) * 1.7;
    i = p + float2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
    float2 q = float2(p.x / (sin(i.x + t) / inten), p.y / (cos(i.y + t) / inten));
    c += 1.0 / max(length(q), 0.0001);
  }
  c /= 4.0;
  c = 1.17 - pow(c, 1.4);
  return pow(abs(c), 8.0);
}

float3 encode(float3 l) {
  l = clamp(l, 0.0, 1.0);
  float3 lo = l * 12.92;
  float3 hi = 1.055 * pow(l, float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), l));
}

half4 main(float2 p) {
  float2 uv = p / size;

  // Depth: three stops down the screen, a lighter pool around the phrase, deeper corners.
  float3 top = lin(float3(0.914, 0.957, 0.945));
  float3 mid = lin(float3(0.772, 0.890, 0.882));
  float3 low = lin(float3(0.604, 0.800, 0.812));
  float3 col = mix(top, mid, smoothstep(0.0, 0.55, uv.y));
  col = mix(col, low, smoothstep(0.45, 1.0, uv.y));
  float2 fd = (p - focus) / size.x;
  col += lin(float3(0.93, 0.98, 0.96)) * 0.10 * exp(-dot(fd, fd) * 3.2);
  float2 vd = (uv - 0.5) * float2(0.72, 1.0);
  col = mix(col, lin(float3(0.42, 0.66, 0.70)), 0.36 * smoothstep(0.30, 0.95, dot(vd, vd) * 2.4));

  // Rings, and the dimple under the finger.
  float3 rg = rings(p);
  float2 td = p - touch.xy;
  float dimple = touch.z * exp(-dot(td, td) / (2.0 * 24.0 * 24.0));
  float2 slope = rg.yz + td / 24.0 * dimple * 0.6;

  // Caustics on the floor, seen through the surface: the slope shifts where we look.
  float2 fq = p + slope * 12.0;
  float aspect = 300.0;
  float c1 = caustic(fq / aspect + float2(0.13, 0.31), phase);
  float c2 = caustic(fq / (aspect * 1.9) + float2(0.57, 0.08), -phase);
  float depth = mix(0.75, 1.15, uv.y);
  col += lin(float3(1.0, 1.0, 0.94)) * (c1 * 0.15 + c2 * 0.07) * depth;

  // Light from the top left catches the near side of every ring; the far side falls into shade.
  float2 L = normalize(float2(-0.55, -0.83));
  float facing = dot(slope, -L);
  col += float3(1.0) * pow(max(facing, 0.0), 2.0) * 0.26;
  col *= 1.0 - 0.13 * max(-facing, 0.0);
  col *= 1.0 - 0.05 * dimple;
  col += float3(1.0) * 0.035 * rg.x;

  float3 rgb = encode(col);
  float2 px = p * dpr;
  float n = ign(px) + ign(px + float2(41.0, 17.0)) - 1.0;
  float g = h21(floor(px)) - 0.5;
  rgb += n / 255.0 + g * 0.018;
  return half4(half3(clamp(rgb, 0.0, 1.0)), 1.0);
}
"""

/** The pool. Draws into a canvas already scaled to dp. */
internal interface Water {
    fun draw(canvas: Canvas, geo: Geo, phase: Float, rips: FloatArray, touchX: Float, touchY: Float, touchOn: Float, dpr: Float)
}

@RequiresApi(33)
internal class ShaderWater : Water {
    private val shader = RuntimeShader(WATER_AGSL)
    private val paint = Paint()

    override fun draw(canvas: Canvas, geo: Geo, phase: Float, rips: FloatArray, touchX: Float, touchY: Float, touchOn: Float, dpr: Float) {
        shader.setFloatUniform("size", geo.w, geo.h)
        shader.setFloatUniform("focus", geo.cx, geo.cy)
        shader.setFloatUniform("phase", phase)
        shader.setFloatUniform("rip", rips)
        shader.setFloatUniform("touch", touchX, touchY, touchOn)
        shader.setFloatUniform("dpr", dpr)
        paint.shader = shader
        canvas.drawRect(0f, 0f, geo.w, geo.h, paint)
    }
}

/**
 Below API 33 there is no AGSL: the same depth gradient, pool and corners from three gradients
 (no caustics, no grain), and every ring as a light crest with a shaded trough behind it.
*/
internal class GradientWater : Water {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var depth: Shader? = null
    private var pool: Shader? = null
    private var corners: Shader? = null
    private var builtFor: Geo? = null

    override fun draw(canvas: Canvas, geo: Geo, phase: Float, rips: FloatArray, touchX: Float, touchY: Float, touchOn: Float, dpr: Float) {
        if (builtFor !== geo) {
            builtFor = geo
            depth = LinearGradient(
                0f, 0f, 0f, geo.h,
                intArrayOf(0xFFE9F4F1.toInt(), 0xFFC5E3E1.toInt(), 0xFF9ACCCF.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            pool = RadialGradient(
                geo.cx, geo.cy, geo.w * 0.9f,
                intArrayOf(Color.argb(70, 240, 251, 247), Color.argb(26, 240, 251, 247), Color.argb(0, 240, 251, 247)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            corners = RadialGradient(
                geo.w / 2, geo.h / 2, geo.h * 0.72f,
                intArrayOf(Color.argb(0, 107, 168, 179), Color.argb(0, 107, 168, 179), Color.argb(92, 107, 168, 179)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        // A paint keeps the alpha of its last colour: make it opaque before each gradient.
        fill.color = Color.BLACK
        fill.shader = depth
        canvas.drawRect(0f, 0f, geo.w, geo.h, fill)
        fill.shader = pool
        canvas.drawRect(0f, 0f, geo.w, geo.h, fill)
        fill.shader = corners
        canvas.drawRect(0f, 0f, geo.w, geo.h, fill)
        fill.shader = null

        for (k in 0 until RIPPLES) {
            val strength = rips[k * 4 + 3]
            if (strength <= 0f) continue
            val age = rips[k * 4 + 2]
            val env = rippleEnvelope(age, strength)
            val r = RIPPLE_SPEED * age
            stroke.strokeWidth = 7f
            stroke.color = Color.argb((110 * env).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawCircle(rips[k * 4], rips[k * 4 + 1], r, stroke)
            if (r > 12f) {
                stroke.strokeWidth = 6f
                stroke.color = Color.argb((46 * env).toInt().coerceIn(0, 255), 20, 74, 86)
                canvas.drawCircle(rips[k * 4], rips[k * 4 + 1], r - 12f, stroke)
            }
        }
        if (touchOn > 0.01f) {
            fill.color = Color.argb((18 * touchOn).toInt().coerceIn(0, 255), 12, 52, 62)
            canvas.drawCircle(touchX, touchY, 30f, fill)
        }
    }
}
