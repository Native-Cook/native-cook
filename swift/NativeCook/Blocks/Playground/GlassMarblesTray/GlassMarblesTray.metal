#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Wood: growth rings warped by value noise, stretched along the grain; fixed loop-free maths. The
// React Native SkSL line for line; `across` turns the grain a quarter turn, as React Native's local
// matrix does. Used once, when the tray's top is baked.

static float glassMarblesTrayHash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

static float glassMarblesTrayNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(glassMarblesTrayHash(i), glassMarblesTrayHash(i + float2(1.0, 0.0)), u.x),
               mix(glassMarblesTrayHash(i + float2(0.0, 1.0)), glassMarblesTrayHash(i + float2(1.0, 1.0)), u.x), u.y);
}

[[ stitchable ]] half4 glassMarblesTrayWood(float2 position, float2 stretch, float3 light, float3 dark, float seed, float across) {
    float2 p = across > 0.5 ? float2(position.y, -position.x) : position;
    float2 q = p * stretch + float2(seed, seed * 0.37);
    float n = glassMarblesTrayNoise(q * float2(0.5, 3.0)) * 0.6
        + glassMarblesTrayNoise(q * float2(1.1, 8.0)) * 0.3
        + glassMarblesTrayNoise(q * float2(2.7, 19.0)) * 0.1;
    float rings = fract(q.y * 1.4 + n * 2.4);
    float grain = smoothstep(0.0, 0.8, rings) * (1.0 - smoothstep(0.8, 1.0, rings));
    float fleck = glassMarblesTrayNoise(q * float2(30.0, 2.0));
    float t = clamp(0.2 + 0.55 * grain + 0.25 * n + 0.1 * (fleck - 0.5), 0.0, 1.0);
    return half4(half3(mix(dark, light, t)), 1.0);
}
