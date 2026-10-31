#include <metal_stdlib>
using namespace metal;

      // card centre
     // art size

  // pt
      // slow drift, radians
float liquidArtDeckHash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float liquidArtDeckNoise(float2 p) {
  float2 i = floor(p); float2 f = fract(p); float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(liquidArtDeckHash(i), liquidArtDeckHash(i + float2(1, 0)), u.x), mix(liquidArtDeckHash(i + float2(0, 1)), liquidArtDeckHash(i + float2(1, 1)), u.x), u.y);
}
float liquidArtDeckFbm(float2 p) { float s = 0.0; float a = 0.5; for (int i = 0; i < 4; i++) { s += a * liquidArtDeckNoise(p); p = p * 2.03 + 1.7; a *= 0.5; } return s; }
float3 liquidArtDeckPalette(float t) {
  return 0.5 + 0.5 * cos(6.2831 * (t + float3(0.0, 0.33, 0.67)));
}
[[ stitchable ]] half4 liquidArtDeckArt(float2 pos, half4 color, float2 sz, float seed, float2 smear, float ph, float2 offset, float dim) {
  float2 xy = pos + offset;
  float2 o = offset + sz * 0.5;
  float2 q = (xy - o) / sz.y;
  // the smear: pixels near the middle are dragged back along the motion, like thick paint
  float2 sm = smear / sz.y;
  float fall = exp(-dot(q, q) * 2.2);
  q -= sm * fall * 2.2;
  q += float2(-sm.y, sm.x) * fall * 0.6 * sin(q.y * 9.0);
  float2 d = float2(cos(ph), sin(ph)) * 0.35;
  float2 w = float2(liquidArtDeckFbm(q * 2.2 + seed * 10.0 + d), liquidArtDeckFbm(q * 2.2 + 3.7 - d));
  float f = liquidArtDeckFbm(q * 2.6 + w * 2.2 + seed * 4.0);
  // chrome: bright bands where the field's slope is steep, tinted by a seed palette
  float band = pow(abs(sin(f * 14.0 + seed * 9.0)), 10.0);
  float rim = pow(abs(sin(f * 7.0 + 1.3 + seed * 5.0)), 3.0);
  float3 base = mix(float3(0.05, 0.05, 0.07), liquidArtDeckPalette(seed + f * 0.28), 0.66);
  float3 col = base * (0.2 + 0.85 * f) * (0.55 + 0.45 * rim) + band * mix(float3(1.0), liquidArtDeckPalette(seed + 0.12 + f * 0.2), 0.3) * 1.05;
  // a soft vignette inside the card
  col *= 1.0 - 0.35 * smoothstep(0.35, 0.75, length(q));
  col += (liquidArtDeckHash(floor(xy)) - 0.5) * 0.04;
  col = mix(clamp(col, 0.0, 1.0), float3(0.05882353, 0.05882353, 0.069707), dim);
  // RN's Skia surface interprets RuntimeEffect RGB in Display P3. Convert that
  // surface colour once to SwiftUI's sRGB working space (the field maths above is unchanged).
  col = clamp(col, 0.0, 1.0);
  float3 lin = select(pow((col + 0.055) / 1.055, float3(2.4)), col / 12.92, col <= 0.04045);
  lin = float3(1.224745 * lin.r - 0.224904 * lin.g,
               -0.042058 * lin.r + 1.042081 * lin.g,
               -0.019642 * lin.r - 0.078655 * lin.g + 1.098537 * lin.b);
  lin = clamp(lin, 0.0, 1.0);
  float3 out = select(1.055 * pow(lin, float3(1.0/2.4)) - 0.055, lin * 12.92, lin <= 0.0031308);
  return half4(half3(out), 1.0);
}