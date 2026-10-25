package com.ngocdevv.nativecook.blocks.splash.brushinksplash

// The React Native brushInkShader.ts in AGSL (API 33+), without the Display P3 conversion.
//
// INK_AGSL paints brush strokes. It is the paint of a vertex mesh (Canvas.drawVertices) whose
// texture coordinates are stroke coordinates, not screen ones: x = distance along the shared paint
// clock (dp), y = the position across the stroke in half-widths (−1.7 … 1.7) plus 4 × the stroke's
// index. So one shader knows, for every pixel, how far behind the brush it is, how far from the
// stroke's middle, and how far along its own stroke: enough for a rounded wet tip, ragged edges,
// dry bristle streaks toward a stroke's end, pigment pooling at the rim, and bleeding into fibres.
//
// INK_PAPER_AGSL bakes the rice paper once.

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
"""

internal const val INK_AGSL = """
uniform float head;
uniform float bleed;
uniform float alpha;
uniform float starts[8];
uniform float lengths[8];
uniform float dry[8];
uniform float caps[8];
uniform float ends[8];
$COMMON

half4 main(float2 tc) {
  if (alpha < 0.002) return half4(0.0);
  float k = floor((tc.y + 2.0) / 4.0);
  float v = tc.y - 4.0 * k;
  float s = tc.x;
  float s0 = 0.0;
  float len = 1.0;
  float dr = 0.0;
  float cap = 1.0;
  float endCap = 1.0;
  for (int i = 0; i < 8; i++) {
    if (float(i) == k) {
      s0 = starts[i];
      len = lengths[i];
      dr = dry[i];
      cap = caps[i];
      endCap = ends[i];
    }
  }
  float behind = head - s;
  if (behind <= 0.0) return half4(0.0);
  float u = clamp((s - s0) / len, 0.0, 1.0);
  float av = abs(v);

  // The wet tip: the stroke is rounded off just behind the brush.
  float tip = sqrt(clamp(behind / 5.0, 0.0, 1.0));
  // Round caps before the start and past the end of the stroke.
  float before = max(s0 - s, 0.0) / cap;
  float after = max(s - s0 - len, 0.0) / endCap;
  float beyond = max(before, after);
  tip *= sqrt(max(0.0, 1.0 - beyond * beyond));
  // Ragged edges, and the fibres the ink creeps along as it bleeds.
  float rag = (vnoise(float2(s * 0.3, k * 5.0 + (v > 0.0 ? 0.0 : 9.0))) - 0.5) * 0.22;
  float fibre = vnoise(float2(s * 0.9, v * 3.0 + k * 11.0)) * vnoise(float2(s * 0.12 + 3.0, v * 9.0));
  float edge = tip + rag;
  float cover = 1.0 - smoothstep(edge - 0.07, edge + 0.04, av);

  // Dry brush: bristle streaks along the stroke open up toward its end.
  float dryness = dr * smoothstep(0.4, 1.0, u);
  float bristle = vnoise(float2(v * 7.5 + k * 3.1, s * 0.035)) * 0.7 + vnoise(float2(v * 19.0, s * 0.12)) * 0.3;
  float streak = smoothstep(dryness * 0.75 - 0.08, dryness * 0.75 + 0.08, bristle);
  cover *= mix(1.0, streak, step(0.001, dryness));
  // Pigment pools at the rim; the middle is a touch lighter; dry ink is greyer.
  float rim = smoothstep(edge - 0.4, edge - 0.04, av);
  float3 ink = mix(float3(0.16, 0.15, 0.17), float3(0.075, 0.07, 0.085), rim);
  ink = mix(ink, float3(0.3, 0.29, 0.3), dryness * 0.45);
  ink += (vnoise(float2(s * 0.08, v * 2.0)) - 0.5) * 0.04;

  // Bleeding: a soft grey halo creeping out along the fibres past the edge.
  float reach = edge + bleed * (0.25 + 0.45 * fibre);
  float halo = bleed * (1.0 - smoothstep(edge, reach, av)) * step(edge - 0.02, av) * (0.12 + 0.4 * fibre);
  float a = max(cover, halo) * alpha;
  float3 col = cover >= halo ? ink : mix(float3(0.26, 0.25, 0.27), ink, 0.55);
  return half4(half3(clamp(col, 0.0, 1.0)) * a, a);
}
"""

internal const val INK_PAPER_AGSL = """
uniform float2 size;
$COMMON

half4 main(float2 p) {
  float2 uv = p / size;
  float3 col = mix(float3(0.957, 0.937, 0.894), float3(0.93, 0.902, 0.85), uv.y);
  // Rice paper: long wavy kozo fibres in two layers, soft clouds, fine grain.
  float cloud = vnoise(p * 0.02) * 0.6 + vnoise(p * 0.05) * 0.4;
  col *= 0.975 + cloud * 0.04;
  for (int k = 0; k < 2; k++) {
    float a = float(k) * 1.7 + 0.3;
    float2 d = float2(cos(a), sin(a));
    float2 q = float2(dot(p, d), dot(p, float2(-d.y, d.x)));
    q.y += sin(q.x * 0.03 + float(k)) * 6.0;
    float f = smoothstep(0.84, 0.95, vnoise(q * float2(0.025, 0.7) + float(k) * 5.0));
    col += f * 0.025;
    col -= smoothstep(0.86, 0.96, vnoise(q * float2(0.04, 1.1) + 13.0)) * 0.02;
  }
  col += (hash21(floor(p * 2.0)) - 0.5) * 0.02;
  float2 v = uv - 0.5;
  col *= 1.0 - dot(v, v) * 0.25;
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
"""
