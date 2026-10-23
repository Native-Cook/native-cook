#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Grain on paper, mottling in clay: per-half-point noise plus soft value noise, as light and dark
// specks whose strength is built into the colour. `origin` is where the pattern is pinned, so the
// clay's grain rides with the body.

static float clayPressBlobHash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

static float clayPressBlobNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(clayPressBlobHash(i), clayPressBlobHash(i + float2(1.0, 0.0)), u.x),
        mix(clayPressBlobHash(i + float2(0.0, 1.0)), clayPressBlobHash(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}

[[ stitchable ]] half4 clayPressBlobGrain(float2 position, float strength, float scale, float mottle, float2 origin) {
    float2 p = position - origin;
    float v = (clayPressBlobHash(floor(p * 2.0)) - 0.5) * 0.7 + (clayPressBlobNoise(p / scale) - 0.5) * mottle;
    float a = clamp(abs(v) * strength, 0.0, 1.0);
    return v > 0.0 ? half4(a, a, a, a) : half4(0.0, 0.0, 0.0, a);
}
