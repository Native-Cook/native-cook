#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

static float gdrHash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
static float gdrNoise(float2 p) {
  float2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(gdrHash(i), gdrHash(i + float2(1, 0)), f.x), mix(gdrHash(i + float2(0, 1)), gdrHash(i + float2(1, 1)), f.x), f.y);
}
static float3 gdrMixRamp(float k) {
  float3 a = float3(0.275, 0.047, 0.196), b = float3(0.541, 0.118, 0.310), c = float3(0.757, 0.184, 0.431);
  float3 d = float3(0.831, 0.345, 0.620), e = float3(0.800, 0.530, 0.706), surface = float3(0.784, 0.631, 0.753);
  if (k < 0.28) return mix(a, b, smoothstep(0.0, 0.28, k));
  if (k < 0.45) return mix(b, c, smoothstep(0.28, 0.45, k));
  if (k < 0.62) return mix(c, d, smoothstep(0.45, 0.62, k));
  if (k < 0.82) return mix(d, e, smoothstep(0.62, 0.82, k));
  return mix(e, surface, smoothstep(0.82, 1.0, k));
}

/// Material field for the lilac card.  It shares the RN geometry: elliptical falloff, a shear-driven polar swirl,
/// settling ring, drain/bloom attenuation and an ember held at the dial end of the curve.
[[ stitchable ]] half4 glowDialReminderField(float2 p, half4 colour, float2 size, float2 centre, float radius,
                                             float shear, float ring, float off, float plug, float ember) {
  float2 q = (p - centre) / float2(170.0, 236.0) / max(radius, 0.001);
  float r0 = length(q);
  q *= mix(float2(0.794, 0.87), float2(1.0), smoothstep(0.25, 0.75, r0));
  float r = length(q), angle = atan2(q.y, q.x);
  float turn = shear * 3.2 * exp(-r * 1.25);
  float n = (gdrNoise(q * 3.4 + float2(8.1, 3.2)) - 0.5) * 0.075 + (gdrNoise(q * 7.1) - 0.5) * 0.035;
  float arms = 0.19 * abs(shear) * sin(2.0 * (angle - turn) + r * 3.0) * smoothstep(0.12, 0.4, r);
  float field = r * (1.0 + n + 0.035 * sin(angle * 3.0) + arms);
  float3 out = gdrMixRamp(field);
  float settle = exp(-pow((field - 0.83) / 0.065, 2.0)) * ring * 0.10;
  out = mix(out, float3(0.831, 0.345, 0.620), settle);
  float2 vein = p - float2(306.0, 537.0);
  float veinLight = (1.0 - smoothstep(9.0, 30.0, abs(vein.x + vein.y * 0.31))) * smoothstep(0.0, 1.0, plug);
  out = mix(out, float3(0.757, 0.184, 0.431), veinLight * 0.48);
  float emberLight = (1.0 - smoothstep(18.0, 58.0, length(p - float2(306.0, 537.0)))) * ember;
  out = mix(out, float3(0.88, 0.10, 0.43), emberLight * 0.72);
  out = mix(out, float3(0.38, 0.18, 0.33), off * 0.14);
  return half4(half3(clamp(out, 0.0, 1.0)), colour.a);
}
