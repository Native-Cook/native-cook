package com.ngocdevv.nativecook.blocks.cards.postcardstampcancel

/**
 * The cancellation's ink, one AGSL shader over the stamp's corner of the card (postcardShader.ts,
 * without the Display P3 conversion). `mask` is the rubber face (white where it carries ink),
 * evaluated in px; `scale` is px per dp so the noise has the same size as on iOS. Ink transfers by
 * pressure, a seeded fbm plus a tilt across the face: as `press` grows the threshold falls and the
 * ink spreads; paper tooth leaves specks and ink pools darker at the stroke edges. Premultiplied.
 */
internal const val POSTCARD_INK_AGSL = """
uniform shader mask;
uniform float2 size;
uniform float press;
uniform float fade;
uniform float2 tilt;
uniform float seed;
uniform float scale;

float hash21(float2 p) {
  float3 q = fract(float3(p.xyx) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}

float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  float a = hash21(i);
  float b = hash21(i + float2(1.0, 0.0));
  float c = hash21(i + float2(0.0, 1.0));
  float d = hash21(i + float2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 3; i++) {
    v += a * vnoise(p);
    p = p * 2.03 + float2(17.0, 9.0);
    a *= 0.5;
  }
  return v;
}

half4 main(float2 px) {
  float m = mask.eval(px).a;
  if (m < 0.004 || fade < 0.001) return half4(0.0);
  float2 p = px / scale;
  float o = 1.6 * scale;
  float n = (mask.eval(px + float2(o, 0.0)).a + mask.eval(px - float2(o, 0.0)).a +
             mask.eval(px + float2(0.0, o)).a + mask.eval(px - float2(0.0, o)).a) * 0.25;
  float edge = clamp((m - n) * 3.0, 0.0, 1.0);
  float2 q = p / size - 0.5;
  float pr = 0.62 * fbm(p * 0.045 + seed) + 0.38 + dot(q, tilt) * 0.55;
  float tooth = vnoise(p * 1.7 + seed * 3.0) * 0.5 + vnoise(p * 3.9 - seed) * 0.5;
  float threshold = 1.22 - press * 1.0;
  float cover = smoothstep(threshold - 0.14, threshold + 0.1, pr - (tooth - 0.5) * 0.32);
  cover *= 0.68 + 0.32 * smoothstep(0.16, 0.46, tooth);
  cover *= 0.72 + 0.28 * smoothstep(0.35, 0.95, pr);
  float a = m * cover * (0.8 + 0.2 * edge) * fade;
  float3 inkCol = mix(float3(0.16, 0.22, 0.47), float3(0.08, 0.11, 0.29), edge * 0.8 + (1.0 - cover) * 0.2);
  return half4(half3(inkCol * a), half(a));
}
"""
