#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The felt of Domino Chain, ported line for line from the React Native SkSL.

static float dominoChainHash(float2 p) {
    float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

static float dominoChainNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(dominoChainHash(i), dominoChainHash(i + float2(1.0, 0.0)), f.x),
        mix(dominoChainHash(i + float2(0.0, 1.0)), dominoChainHash(i + float2(1.0, 1.0)), f.x),
        f.y
    );
}

[[ stitchable ]] half4 dominoChainFelt(float2 p, float2 size, float2 lamp, float reach, float rail) {
    // The lamp's pool of light, squashed by the table's tilt.
    float2 d = (p - lamp) / reach * float2(1.0, 1.45);
    float pool = exp(-dot(d, d) * 1.25);
    // Felt: a fine tooth, soft mottling and a faint nap.
    float tooth = dominoChainHash(floor(p * 1.6)) - 0.5;
    float mottle = dominoChainNoise(p * 0.045) - 0.5;
    float nap = dominoChainNoise(float2(p.x * 0.5, p.y * 0.12)) - 0.5;
    float3 base = float3(0.06, 0.25, 0.18);
    float3 lit = float3(0.13, 0.43, 0.31);
    float3 c = mix(base * 0.55, lit, pool);
    c *= 1.0 + tooth * 0.12 + mottle * 0.16 + nap * 0.07;
    // Darker towards the far rail.
    float far = clamp((p.y - rail) / (size.y * 0.35), 0.0, 1.0);
    c *= 0.62 + 0.38 * far;
    return half4(half3(c), 1.0h);
}
