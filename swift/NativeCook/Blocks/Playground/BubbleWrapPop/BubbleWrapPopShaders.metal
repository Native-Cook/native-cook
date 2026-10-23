#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The backdrop: four pastel pools of light on a warm white, drifting with `phase`, with a fine
// grain. `lens` (centre x, centre y, magnification) samples it magnified about a point, which is
// how a dome shows what is behind it; (0, 0, 1) draws it as it is.
[[ stitchable ]] half4 bubbleWrapPopBackdrop(float2 position, float2 res, float phase, float3 lens) {
    float2 p = lens.xy + (position - lens.xy) / lens.z;
    float2 uv = p / res.y;
    float ax = res.x / res.y;
    float3 col = float3(0.99, 0.958, 0.93);
    float2 d;
    d = uv - float2(ax * (0.16 + 0.08 * sin(phase)), 0.2 + 0.05 * cos(phase));
    col = mix(col, float3(1.0, 0.75, 0.62), 0.9 * exp(-dot(d, d) / (0.3 * 0.3)));
    d = uv - float2(ax * (0.88 + 0.06 * cos(phase)), 0.4 + 0.06 * sin(phase));
    col = mix(col, float3(0.98, 0.62, 0.76), 0.8 * exp(-dot(d, d) / (0.29 * 0.29)));
    d = uv - float2(ax * (0.28 + 0.07 * cos(phase)), 0.68 + 0.05 * sin(phase));
    col = mix(col, float3(0.72, 0.66, 1.0), 0.85 * exp(-dot(d, d) / (0.34 * 0.34)));
    d = uv - float2(ax * (0.82 + 0.05 * sin(phase)), 0.88 + 0.04 * cos(phase));
    col = mix(col, float3(0.6, 0.83, 1.0), 0.8 * exp(-dot(d, d) / (0.3 * 0.3)));
    float g = fract(sin(dot(floor(p * 2.0), float2(12.9898, 78.233))) * 43758.5453) - 0.5;
    return half4(half3(col + g * 0.02), 1.0);
}

static float bubbleWrapPopHash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

static float bubbleWrapPopNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(bubbleWrapPopHash(i), bubbleWrapPopHash(i + float2(1.0, 0.0)), u.x),
        mix(bubbleWrapPopHash(i + float2(0.0, 1.0)), bubbleWrapPopHash(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}

// Crinkles in the film: long, thin crease highlights from stretched value noise, pinned at `origin`.
[[ stitchable ]] half4 bubbleWrapPopCrinkle(float2 position, float strength, float2 origin) {
    float2 p = position - origin;
    float2 q = float2(p.x * 0.82 + p.y * 0.57, -p.x * 0.57 + p.y * 0.82);
    float n = bubbleWrapPopNoise(q * float2(0.06, 0.008)) * 0.7 + bubbleWrapPopNoise(q * float2(0.02, 0.03) + 7.0) * 0.3;
    float ridge = pow(1.0 - abs(n * 2.0 - 1.0), 14.0);
    float a = ridge * strength;
    return half4(a, a, a, a);
}
