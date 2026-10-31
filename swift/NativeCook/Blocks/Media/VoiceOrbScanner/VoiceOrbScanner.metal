#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;
float voiceOrbScannerHash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float voiceOrbScannerVnoise(float2 p) {
  float2 i = floor(p); float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(voiceOrbScannerHash(i), voiceOrbScannerHash(i + float2(1, 0)), u.x), mix(voiceOrbScannerHash(i + float2(0, 1)), voiceOrbScannerHash(i + float2(1, 1)), u.x), u.y);
}
float voiceOrbScannerFbm(float2 p) {
  float s = 0.0; float a = 0.5;
  for (int i = 0; i < 4; i++) { s += a * voiceOrbScannerVnoise(p); p = p * 2.03 + float2(1.7, 9.2); a *= 0.5; }
  return s;
}
float voiceOrbScannerBall(float2 p, float4 b) {
  float2 q = p - b.xy;
  return b.z * b.z / max(dot(q, q), 1.0);
}
float voiceOrbScannerField(float2 p, float t, float4 orb, float4 d0, float4 d1, float4 d2) {
  float2 q = p - orb.xy;
  float a = atan2(q.y, q.x);
  float w = orb.w * (0.05 * sin(5.0 * a + t * 10.0) + 0.035 * sin(9.0 * a - t * 15.0) + 0.025 * sin(3.0 * a + t * 6.0));
  float r = orb.z * (1.0 + w);
  float f = r * r / max(dot(q, q), 1.0);
  return f + voiceOrbScannerBall(p, d0) + voiceOrbScannerBall(p, d1) + voiceOrbScannerBall(p, d2);
}

// iOS Skia RuntimeEffect writes untagged values into its Display-P3 surface.
// Adapt only that surface boundary to SwiftUI's sRGB working surface; the field and palette above stay unchanged.
float3 voiceOrbScannerDecode(float3 c) {
    return select(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), c > 0.04045);
}
float3 voiceOrbScannerEncode(float3 c) {
    return select(c * 12.92, 1.055 * pow(max(c, 0.0), float3(1.0 / 2.4)) - 0.055, c > 0.0031308);
}
half4 voiceOrbScannerSurface(float3 premultiplied, float a) {
    if (a <= 0.0) return half4(0.0);
    float3 p = voiceOrbScannerDecode(premultiplied / a);
    float3 rgb = float3(1.224745 * p.r - 0.224904 * p.g,
                       -0.042058 * p.r + 1.042081 * p.g,
                       -0.019642 * p.r - 0.078655 * p.g + 1.098537 * p.b);
    return half4(half3(voiceOrbScannerEncode(rgb) * a), half(a));
}

// Skia gradients interpolate unpremultiplied stops (SwiftUI gradients premultiply them).
[[ stitchable ]] half4 voiceOrbScannerWarm(float2 p, half4 input, float width, float oy) {
    float k = clamp(distance(p, float2(width * 0.5, oy + 40.0)) / (width * 0.85), 0.0, 1.0);
    float4 a = float4(120.0/255.0,46.0/255.0,92.0/255.0,140.0/255.0);
    float4 b = float4(60.0/255.0,24.0/255.0,70.0/255.0,63.0/255.0);
    float4 c = float4(23.0/255.0,18.0/255.0,31.0/255.0,0.0);
    float4 color = k < 0.5 ? mix(a,b,k*2.0) : mix(b,c,(k-0.5)*2.0);
    return half4(half3(color.rgb * color.a),half(color.a));
}

[[ stitchable ]] half4 voiceOrbScannerOrb(float2 p, half4 input, float t, float4 orb, float4 d0, float4 d1, float4 d2, float glow) {
  float f = voiceOrbScannerField(p, t, orb, d0, d1, d2);
  float aa = 1.6 / max(orb.z, 1.0) * 6.0;
  float inside = smoothstep(1.0 - aa * 0.5, 1.0 + aa * 0.5, f);
  // bloom outside the surface
  float3 halo = float3(0.66, 0.36, 0.9);
  float ha = smoothstep(0.25, 1.0, f) * (0.3 + 0.2 * glow);
  if (inside <= 0.0) return voiceOrbScannerSurface(halo * ha, ha);

  // pseudo height and its normal
  float e = 1.5;
  float hx = (1.0 - 1.0 / max(voiceOrbScannerField(p + float2(e, 0.0), t, orb, d0, d1, d2), 1e-3)) - (1.0 - 1.0 / max(voiceOrbScannerField(p - float2(e, 0.0), t, orb, d0, d1, d2), 1e-3));
  float hy = (1.0 - 1.0 / max(voiceOrbScannerField(p + float2(0.0, e), t, orb, d0, d1, d2), 1e-3)) - (1.0 - 1.0 / max(voiceOrbScannerField(p - float2(0.0, e), t, orb, d0, d1, d2), 1e-3));
  float3 n = normalize(float3(-hx * orb.z * 0.18, -hy * orb.z * 0.18, 1.0));

  // the flame, swirling inside
  float2 uv = (p - orb.xy) / orb.z;
  float sw = voiceOrbScannerFbm(uv * 1.15 + 0.7 * float2(cos(t), sin(t)));
  float fl = voiceOrbScannerFbm(uv * 1.9 + float2(sw * 1.4, sw * 1.1) + 0.55 * float2(sin(2.0 * t), cos(t)));
  // violet at the top warming to magenta, flame tongues licking up from an orange-white core
  float g = clamp(uv.y * 0.55 + 0.5 + (fl - 0.5) * 0.7, 0.0, 1.0);
  float3 col = mix(float3(0.24, 0.08, 0.42), float3(0.92, 0.27, 0.66), smoothstep(0.1, 0.75, g));
  float core = exp(-dot(uv - float2(0.0, 0.28), uv - float2(0.0, 0.28)) * 2.4);
  col = mix(col, float3(1.0, 0.56, 0.24), smoothstep(0.25, 0.85, core * (0.75 + 0.5 * fl)));
  col += float3(1.0, 0.93, 0.78) * pow(core, 3.0) * (0.5 + 0.4 * glow);
  // a darker band just inside the glass, then the rim
  float edge = smoothstep(0.55, 0.95, 1.0 - n.z);
  col *= 1.0 - 0.35 * edge;
  // glass: a lilac fresnel rim and a soft highlight from the upper left
  float fr = pow(1.0 - n.z, 1.6);
  col = mix(col, float3(1.0, 0.8, 0.95), fr * 0.85);
  float3 L = normalize(float3(-0.5, -0.6, 0.65));
  float sp = pow(max(dot(reflect(-L, n), float3(0.0, 0.0, 1.0)), 0.0), 28.0);
  col += sp * 0.8;
  col *= 1.0 + 0.18 * glow;
  col = min(col, 1.0);
  float a = inside + ha * (1.0 - inside);
  return voiceOrbScannerSurface(col * inside + halo * ha * (1.0 - inside), a);
}
