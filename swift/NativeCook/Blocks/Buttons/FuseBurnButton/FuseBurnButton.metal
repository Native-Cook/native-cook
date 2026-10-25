#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The night backdrop, a port of the React Native SkSL (fuseBurnShader.ts): a navy gradient with a
// cool lift at the top, a warm hint near the button, a vignette and fine grain. Drawn once, under
// everything. SwiftUI's shader colours are sRGB already, so the SkSL's P3 conversion is left out.

namespace fuseBurnButtonShader {

static float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

} // namespace fuseBurnButtonShader

[[ stitchable ]] half4 fuseBurnButtonBackdrop(float2 p, half4 color, float2 size, float2 warm) {
    float2 uv = p / size;
    float3 a = float3(0.075, 0.098, 0.2);
    float3 b = float3(0.051, 0.071, 0.149);
    float3 c = float3(0.035, 0.047, 0.106);
    float3 col = uv.y < 0.45 ? mix(a, b, uv.y / 0.45) : mix(b, c, (uv.y - 0.45) / 0.55);
    // A faint warm pool round the button, as if lit by what is about to happen.
    float d = length((p - warm) / float2(size.x * 0.7, 120.0));
    col += float3(0.09, 0.04, 0.02) * exp(-d * d * 2.0);
    float2 v = uv - float2(0.5, 0.45);
    col *= 1.0 - dot(v, v) * 0.9;
    col += (fuseBurnButtonShader::hash21(floor(p * 2.0)) - 0.5) * 0.018;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0h);
}
