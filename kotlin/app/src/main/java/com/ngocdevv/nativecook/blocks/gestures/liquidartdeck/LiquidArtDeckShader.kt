package com.ngocdevv.nativecook.blocks.gestures.liquidartdeck

internal object LiquidArtDeckShader {
    const val SOURCE = """
uniform float2 o;      // card centre
uniform float2 sz;     // art size
uniform float seed;
uniform float2 smear;  // pt
uniform float ph;      // slow drift, radians
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float vnoise(float2 p) {
  float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1, 0)), u.x), mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);
}
float fbm(float2 p) { float s = 0.0; float a = 0.5; for (int i = 0; i < 4; i++) { s += a * vnoise(p); p = p * 2.03 + 1.7; a *= 0.5; } return s; }
float3 pal(float t) {
  return 0.5 + 0.5 * cos(6.2831 * (t + float3(0.0, 0.33, 0.67)));
}
half4 main(float2 xy) {
  float2 q = (xy - o) / sz.y;
  // the smear: pixels near the middle are dragged back along the motion, like thick paint
  float2 sm = smear / sz.y;
  float fall = exp(-dot(q, q) * 2.2);
  q -= sm * fall * 2.2;
  q += float2(-sm.y, sm.x) * fall * 0.6 * sin(q.y * 9.0);
  float2 d = float2(cos(ph), sin(ph)) * 0.35;
  float2 w = float2(fbm(q * 2.2 + seed * 10.0 + d), fbm(q * 2.2 + 3.7 - d));
  float f = fbm(q * 2.6 + w * 2.2 + seed * 4.0);
  // chrome: bright bands where the field's slope is steep, tinted by a seed palette
  float band = pow(abs(sin(f * 14.0 + seed * 9.0)), 10.0);
  float rim = pow(abs(sin(f * 7.0 + 1.3 + seed * 5.0)), 3.0);
  float3 base = mix(float3(0.05, 0.05, 0.07), pal(seed + f * 0.28), 0.66);
  float3 col = base * (0.2 + 0.85 * f) * (0.55 + 0.45 * rim) + band * mix(float3(1.0), pal(seed + 0.12 + f * 0.2), 0.3) * 1.05;
  // a soft vignette inside the card
  col *= 1.0 - 0.35 * smoothstep(0.35, 0.75, length(q));
  col += (hash(floor(xy)) - 0.5) * 0.04;
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}"""
}
