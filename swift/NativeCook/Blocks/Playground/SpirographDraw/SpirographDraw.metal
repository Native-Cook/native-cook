#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

static float spirographDrawHash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

static float spirographDrawNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(spirographDrawHash(i), spirographDrawHash(i + float2(1.0, 0.0)), u.x),
        mix(spirographDrawHash(i + float2(0.0, 1.0)), spirographDrawHash(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}

// Cotton drawing paper: warm white with a soft mottle and fine fibres.
[[ stitchable ]] half4 spirographDrawPaper(float2 p) {
    float3 col = float3(0.965, 0.95, 0.912);
    float m = spirographDrawNoise(p * 0.02) * 0.6 + spirographDrawNoise(p * 0.07 + 5.0) * 0.4;
    col *= 0.985 + 0.025 * m;
    float fibre = spirographDrawNoise(float2(p.x * 0.9 + p.y * 0.3, p.y * 0.08 - p.x * 0.05));
    col -= 0.018 * smoothstep(0.72, 0.95, fibre);
    col += (spirographDrawHash(floor(p * 2.0)) - 0.5) * 0.012;
    return half4(half3(col), 1.0);
}
