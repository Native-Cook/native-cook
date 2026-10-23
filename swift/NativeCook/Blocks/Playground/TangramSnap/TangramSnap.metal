#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Lacquered wood: grain rings from value noise, darkening the tint. The React Native SkSL line for
// line; the piece's own frame (moved to `origin`, turned by `angle`) stands in for its local matrix.

static float tangramSnapHash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

static float tangramSnapNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(tangramSnapHash(i), tangramSnapHash(i + float2(1.0, 0.0)), u.x),
               mix(tangramSnapHash(i + float2(0.0, 1.0)), tangramSnapHash(i + float2(1.0, 1.0)), u.x), u.y);
}

[[ stitchable ]] half4 tangramSnapLacquer(float2 position, float2 origin, float angle, float3 tint, float seed, float grain) {
    // Into the piece's frame: the inverse of translate(origin) · rotate(angle).
    float2 d = position - origin;
    float c = cos(angle);
    float s = sin(angle);
    float2 p = float2(d.x * c + d.y * s, -d.x * s + d.y * c);
    float2 q = p * float2(0.035, 0.008) + float2(seed, seed * 0.37);
    float n = tangramSnapNoise(q * float2(3.0, 0.6)) * 0.65 + tangramSnapNoise(q * float2(9.0, 1.4)) * 0.35;
    float rings = fract(q.x * 2.6 + n * 1.9);
    float g = smoothstep(0.0, 0.75, rings) * (1.0 - smoothstep(0.75, 1.0, rings));
    float shade = mix(1.0, 0.9 + 0.14 * g + 0.08 * (n - 0.5), grain);
    return half4(half3(tint * shade), 1.0);
}
