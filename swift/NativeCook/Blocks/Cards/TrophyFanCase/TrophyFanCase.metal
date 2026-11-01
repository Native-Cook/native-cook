#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

float trophyFanCaseHash(float2 p) {
  return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

// Skia's iOS runtime shader writes into Display P3. SwiftUI's shader result is sRGB,
// so reproduce that P3 interpretation before returning the colour.
float3 trophyFanCaseP3ToSRGB(float3 c) {
  float3 linear = select(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), c > 0.04045);
  float3 rgb = float3(
    dot(linear, float3(1.2249402, -0.2249402, 0.0)),
    dot(linear, float3(-0.04205695, 1.04205695, 0.0)),
    dot(linear, float3(-0.01963755, -0.07863605, 1.0982736)));
  rgb = max(rgb, 0.0);
  return select(rgb * 12.92, 1.055 * pow(rgb, float3(1.0 / 2.4)) - 0.055, rgb > 0.0031308);
}
[[ stitchable ]] half4 trophyFanCaseBackground(float2 xy, float2 size) {
  float2 uv = xy / size;
  float glow = exp(-pow(length((uv - float2(0.62, 1.08)) * float2(1.1, 1.6)), 2.0) * 2.4);
  float3 c = mix(float3(0.0), float3(0.22, 0.05, 0.01), smoothstep(0.3, 0.75, uv.y));
  c = mix(c, float3(0.86, 0.33, 0.07), glow * 0.9);
  c += float3(1.0, 0.75, 0.45) * pow(glow, 6.0) * 0.5;
  float g = trophyFanCaseHash(floor(xy * 1.0)) - 0.5;
  c += g * (0.025 + 0.05 * glow);
  return half4(half3(trophyFanCaseP3ToSRGB(max(c, 0.0))), 1.0);
}
