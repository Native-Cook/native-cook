#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Folded Map Card — the screen behind the map: a forest-dark gradient in three stops with a soft
// light where the map lies, a vignette and grain. A port of BACKDROP_SKSL (foldedMapExtras.ts);
// SwiftUI colours are sRGB, so the P3 conversion of the Skia version is left out.

namespace foldedMapCardShader {

float h2(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

}

[[ stitchable ]] half4 foldedMapCardBackdrop(float2 p, half4 color, float2 size) {
    using namespace foldedMapCardShader;
    float2 uv = p / size;
    float3 a = float3(0.105, 0.170, 0.135);
    float3 b = float3(0.080, 0.140, 0.108);
    float3 c0 = float3(0.050, 0.090, 0.070);
    float3 c = mix(a, b, sstep(0.0, 0.45, uv.y));
    c = mix(c, c0, sstep(0.45, 1.0, uv.y));
    float2 g = (uv - float2(0.5, 0.4)) * float2(1.0, size.y / size.x);
    c += float3(0.05, 0.06, 0.04) * exp(-dot(g, g) * 4.0);
    float2 v = uv - 0.5;
    c *= 1.0 - 0.4 * sstep(0.3, 0.9, length(v * float2(1.15, 0.9)));
    c += (h2(floor(p)) - 0.5) * 0.04;
    return half4(half3(c), 1.0h);
}
