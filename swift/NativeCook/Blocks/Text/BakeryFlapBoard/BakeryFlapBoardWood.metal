#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Dark walnut with long, slightly wavy grain and a fine fibre on top, for the board's case.

static float bakeryFlapBoardHash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

static float bakeryFlapBoardNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(bakeryFlapBoardHash(i), bakeryFlapBoardHash(i + float2(1.0, 0.0)), u.x),
        mix(bakeryFlapBoardHash(i + float2(0.0, 1.0)), bakeryFlapBoardHash(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}

[[ stitchable ]] half4 bakeryFlapBoardWood(float2 xy, half4 color, float2 size) {
    float2 p = xy / size.x;
    float wave = bakeryFlapBoardNoise(float2(p.x * 2.2, p.y * 9.0)) * 0.9 + bakeryFlapBoardNoise(float2(p.x * 5.0, p.y * 30.0)) * 0.35;
    float rings = 0.5 + 0.5 * sin(p.y * 70.0 + wave * 7.0);
    float fibre = bakeryFlapBoardNoise(float2(p.x * 40.0, p.y * 700.0));
    float3 dark = float3(0.105, 0.062, 0.038);
    float3 light = float3(0.235, 0.137, 0.078);
    float3 col = mix(dark, light, rings * 0.55 + wave * 0.3);
    col *= 0.86 + fibre * 0.22;
    // A soft light from the top left, falling off towards the bottom.
    col *= 1.08 - 0.32 * clamp(xy.y / size.y, 0.0, 1.0);
    return half4(half3(col), 1.0);
}
