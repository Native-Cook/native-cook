#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The cancellation's ink over the stamp's corner of the card. The layer is the rubber face (white
// where it carries ink). Where it touches, ink transfers by pressure: a seeded low-frequency noise
// field plus a tilt across the face, so one side prints first and a few patches stay pale; as
// `press` grows the threshold falls and the ink spreads into the rest. The paper's tooth leaves
// speckles, and ink pools at the edges of every stroke (the mask minus its neighbourhood). Output
// is premultiplied (transparent elsewhere). Colours are sRGB, as SwiftUI expects.

static float postcardStampCancelHash(float2 p) {
    float3 q = fract(float3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

static float postcardStampCancelNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = postcardStampCancelHash(i);
    float b = postcardStampCancelHash(i + float2(1.0, 0.0));
    float c = postcardStampCancelHash(i + float2(0.0, 1.0));
    float d = postcardStampCancelHash(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

static float postcardStampCancelFbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * postcardStampCancelNoise(p);
        p = p * 2.03 + float2(17.0, 9.0);
        a *= 0.5;
    }
    return v;
}

[[ stitchable ]] half4 postcardStampCancelInk(float2 p, SwiftUI::Layer mask, float2 size, float press,
                                              float fade, float2 tilt, float seed) {
    float m = mask.sample(p).a;
    if (m < 0.004 || fade < 0.001) return half4(0.0);
    float o = 1.6;
    float n = (mask.sample(p + float2(o, 0.0)).a + mask.sample(p - float2(o, 0.0)).a +
               mask.sample(p + float2(0.0, o)).a + mask.sample(p - float2(0.0, o)).a) * 0.25;
    float edge = clamp((m - n) * 3.0, 0.0, 1.0);
    // Pressure: uneven rubber and an uneven hand.
    float2 q = p / size - 0.5;
    float pr = 0.62 * postcardStampCancelFbm(p * 0.045 + seed) + 0.38 + dot(q, tilt) * 0.55;
    float tooth = postcardStampCancelNoise(p * 1.7 + seed * 3.0) * 0.5 + postcardStampCancelNoise(p * 3.9 - seed) * 0.5;
    float threshold = 1.22 - press * 1.0;
    float cover = smoothstep(threshold - 0.14, threshold + 0.1, pr - (tooth - 0.5) * 0.32);
    // A full press still leaves the paper's tooth showing in specks, and light pressure prints pale.
    cover *= 0.68 + 0.32 * smoothstep(0.16, 0.46, tooth);
    cover *= 0.72 + 0.28 * smoothstep(0.35, 0.95, pr);
    float a = m * cover * (0.8 + 0.2 * edge) * fade;
    float3 inkCol = mix(float3(0.16, 0.22, 0.47), float3(0.08, 0.11, 0.29), edge * 0.8 + (1.0 - cover) * 0.2);
    return half4(half3(inkCol * a), half(a));
}
