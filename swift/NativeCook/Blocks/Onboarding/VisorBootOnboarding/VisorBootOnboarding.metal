#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The studio behind the see-through SceneKit view (React Native's VisorBackdrop, one Skia shader pass): a blue-grey
// sweep lit from above the helmet, a soft pool of light round the plinth, the plinth's contact shadow on the floor
// (projected with the stage camera), a vignette and grain. The React Native shader converts its sRGB result to
// Display P3 before Skia reads it as P3, so what shows there is the sRGB colour itself: the SwiftUI layer is
// non-linear sRGB, so the colour is written as is.

namespace visorBoot {
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
}

[[ stitchable ]] half4 visorBootStudio(float2 p, half4 color, float2 res, float2 c, float2 rad, float sweep) {
  float2 uv = p / res;
  float2 d = (p - float2(res.x * 0.5, res.y * 0.3)) / res.y;
  float r = length(d * float2(1.25, 1.0));
  float3 centre = float3(0.157, 0.188, 0.227);
  float3 mid = float3(0.086, 0.106, 0.129);
  float3 edge = float3(0.035, 0.043, 0.055);
  float3 col = mix(centre, mid, smoothstep(0.0, 0.42, r));
  col = mix(col, edge, smoothstep(0.38, 0.95, r));
  col += float3(0.012, 0.009, 0.006) * smoothstep(0.75, 0.35, abs(uv.y - 0.62) * 2.0);
  float2 e = (p - c) / max(rad * 2.6, float2(1.0));
  float pool = exp(-dot(e, e) * 1.6);
  col += float3(0.05, 0.056, 0.064) * pool * (1.0 + 0.8 * sweep);
  float2 s = (p - c) / max(rad, float2(1.0));
  float sr = length(s);
  float shadow = 0.82 * (1.0 - smoothstep(0.85, 1.45, sr)) + 0.25 * exp(-sr * sr * 0.45);
  col *= 1.0 - clamp(shadow, 0.0, 0.9);
  float v = length((uv - 0.5) * float2(1.0, 0.85));
  col *= 1.0 - 0.38 * smoothstep(0.35, 0.85, v);
  // Skia's p is in canvas pixels (3x on the reference phone); the grain is per device pixel there too
  col += (visorBoot::hash(floor(p * 3.0) + 0.5) - 0.5) * 0.018;
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}
