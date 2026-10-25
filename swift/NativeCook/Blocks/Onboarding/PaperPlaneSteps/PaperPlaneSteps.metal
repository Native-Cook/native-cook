#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Paper Plane Steps — the screen behind the paper: a dusk-blue gradient in three stops with a warm
// glow at the upper right where the planes leave, a vignette and grain. A port of BACKDROP_SKSL
// (planeShader.ts); SwiftUI colours are sRGB, so the P3 conversion is left out.

namespace paperPlaneStepsShader {

float h2(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

}

[[ stitchable ]] half4 paperPlaneStepsBackdrop(float2 p, half4 color, float2 size) {
    using namespace paperPlaneStepsShader;
    float2 uv = p / size;
    float3 a = float3(0.082, 0.106, 0.188);
    float3 b = float3(0.118, 0.137, 0.224);
    float3 c0 = float3(0.165, 0.149, 0.212);
    float3 c = mix(a, b, sstep(0.0, 0.5, uv.y));
    c = mix(c, c0, sstep(0.5, 1.0, uv.y));
    float2 g = (uv - float2(0.95, 0.08)) * float2(1.0, size.y / size.x);
    c += float3(0.16, 0.08, 0.05) * exp(-dot(g, g) * 3.0);
    float2 v = uv - 0.5;
    c *= 1.0 - 0.38 * sstep(0.3, 0.9, length(v * float2(1.1, 0.9)));
    c += (h2(floor(p)) - 0.5) * 0.04;
    return half4(half3(c), 1.0h);
}
