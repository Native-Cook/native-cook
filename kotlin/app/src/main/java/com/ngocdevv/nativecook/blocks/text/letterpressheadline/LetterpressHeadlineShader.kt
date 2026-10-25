package com.ngocdevv.nativecook.blocks.text.letterpressheadline

// The React Native letterpressShader.ts in AGSL (API 33+), without the Display P3 conversion.
// LP_PAPER_AGSL is baked once into a bitmap: thick cotton paper, warm white, with fibres, cloudy
// formation and a soft vignette. LP_PRESS_AGSL is drawn every frame over the headline only and
// returns a premultiplied overlay: each letter's deboss (the paper pushed in, its walls lit and
// shaded by the lamp), the vermilion ink with uneven density, and a faint squeeze halo.
//
// The letters come from a mask bitmap: red = crisp glyphs, green / blue = line 1 / 2 blurred (the
// height maps). Each pixel takes its depth from the nearest letter's box.

private const val COMMON = """
float hash21(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), u.x),
             mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    v += a * vnoise(p);
    p = p * 2.03 + float2(17.1, 9.2);
    a *= 0.5;
  }
  return v;
}
"""

internal const val LP_PAPER_AGSL = """
uniform float2 size;
$COMMON

half4 main(float2 p) {
  float2 uv = p / size;
  float3 col = mix(float3(0.957, 0.937, 0.898), float3(0.925, 0.898, 0.848), uv.y);
  // Formation: the cloudy texture of thick cotton paper.
  col *= 0.97 + 0.05 * fbm(p * 0.03);
  // Fibres in a few directions.
  for (int k = 0; k < 3; k++) {
    float a = float(k) * 2.3 + 0.7;
    float2 d = float2(cos(a), sin(a));
    float2 q = float2(dot(p, d), dot(p, float2(-d.y, d.x)));
    col -= smoothstep(0.8, 0.93, vnoise(q * float2(0.05, 0.85) + float(k) * 7.0)) * 0.018;
  }
  col += (hash21(floor(p * 2.0)) - 0.5) * 0.022;
  float2 v = uv - 0.5;
  col *= 1.0 - dot(v, v) * 0.22;
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""

internal const val LP_PRESS_AGSL = """
uniform shader mask;
uniform float2 origin;
uniform float scale;
uniform float4 boxes[12];
uniform float depth[12];
uniform float count;
uniform float split;
uniform float light;
uniform float elevation;
$COMMON

float heightAt(float2 q, float d1, float d2) {
  float4 m = float4(mask.eval(q));
  return m.g * d1 + m.b * d2;
}

half4 main(float2 p) {
  float2 local = p - origin;
  // The nearest letter's depth on each line.
  float best1 = 1e9;
  float best2 = 1e9;
  float d1 = 0.0;
  float d2 = 0.0;
  for (int i = 0; i < 12; i++) {
    if (float(i) >= count) break;
    float4 b = boxes[i];
    float2 o = abs(local - (b.xy + b.zw) * 0.5) - (b.zw - b.xy) * 0.5;
    float dist = length(max(o, 0.0)) + min(max(o.x, o.y), 0.0);
    if (b.y < split - 0.5) {
      if (dist < best1) { best1 = dist; d1 = depth[i]; }
    } else if (dist < best2) {
      best2 = dist;
      d2 = depth[i];
    }
  }
  float d = local.y < split ? d1 : d2;
  if (max(d1, d2) < 0.002) return half4(0.0);

  float2 q = local * scale;
  float4 m = float4(mask.eval(q));
  float crisp = m.r;
  float g = m.g * d1 + m.b * d2;
  float e = scale;
  float hx = heightAt(q + float2(e, 0.0), d1, d2) - heightAt(q - float2(e, 0.0), d1, d2);
  float hy = heightAt(q + float2(0.0, e), d1, d2) - heightAt(q - float2(0.0, e), d1, d2);
  // The paper is pushed in: height = -1.8 dp × depth × mask; taps are one dp apart.
  float relief = -1.8;
  float3 n = normalize(float3(-hx * relief * 0.5, -hy * relief * 0.5, 1.0));
  float3 L = normalize(float3(cos(light) * cos(elevation), sin(light) * cos(elevation), sin(elevation)));
  float shade = dot(n, L) - L.z;

  // Ink: vermilion, denser in some places than others, pooling a little at the walls.
  float density = 0.78 + 0.22 * fbm(p * 0.22) - 0.1 * smoothstep(0.6, 0.9, vnoise(p * 1.3));
  float wall = clamp(length(float2(hx, hy)) * 2.5, 0.0, 1.0);
  float3 ink = float3(0.761, 0.255, 0.176) * (density + wall * 0.12);
  ink *= 1.0 + shade * 1.4;
  float inkA = crisp * clamp(d, 0.0, 1.0) * clamp(density + 0.15, 0.0, 1.0);

  // The bare paper round the letters: lit and shaded walls, and a faint squeeze of ink.
  float lit = max(shade, 0.0);
  float dark = max(-shade, 0.0);
  float aLit = clamp(lit * 1.3, 0.0, 0.8);
  float aDark = clamp(dark * 1.5, 0.0, 0.7);
  float3 col = mix(float3(0.3, 0.22, 0.15), float3(1.0, 0.99, 0.96), aLit / max(aLit + aDark, 0.0001));
  float alpha = max(aLit, aDark);
  float halo = clamp(g - crisp * d, 0.0, 1.0) * 0.16;
  col = mix(col, float3(0.76, 0.3, 0.22), halo / max(alpha + halo, 0.0001));
  alpha = max(alpha, halo);

  col = mix(col, ink, inkA);
  alpha = mix(alpha, 1.0, inkA);
  return half4(half3(clamp(col, 0.0, 1.0)) * alpha, alpha);
}
"""
