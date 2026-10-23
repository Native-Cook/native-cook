#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

static float smokeHash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

static float smokeNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(smokeHash(i), smokeHash(i + float2(1.0, 0.0)), u.x),
        mix(smokeHash(i + float2(0.0, 1.0)), smokeHash(i + float2(1.0, 1.0)), u.x),
        u.y
    );
}

static float smokeFbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * smokeNoise(p);
        p = float2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 2.03 + 11.7;
        a *= 0.46;
    }
    return v;
}

// Domain-warped fbm smoke that thickens towards the right, plus a few drifting dust motes.
// `boost` (0..1, driven by press) lets the smoke spill across the whole capsule.
// Returns premultiplied white.
[[ stitchable ]] half4 smoke(float2 position, float2 origin, float2 size, float time, float boost) {
    float2 l = position - origin;
    float2 n = l / size;
    float2 p = l / size.y;
    float t = time;

    float2 q = float2(
        smokeFbm(p * 1.3 + float2(-0.10 * t, 0.04 * t)),
        smokeFbm(p * 1.3 + float2(4.1 + 0.07 * t, 2.7 - 0.09 * t))
    );
    float f = smokeFbm(p * 1.7 + 2.4 * q + float2(-0.16 * t, 0.0));
    float wisps = smoothstep(0.3, 0.8, f);
    wisps = wisps * (0.4 + 1.1 * q.x * q.x);

    float mask = smoothstep(mix(0.14, -0.6, boost), 0.96, n.x);
    float haze = 0.26 * mask * q.y;
    float smoke = wisps * mask * (0.35 + 0.65 * mask) * (1.7 + 0.5 * boost) + haze;
    smoke = min(smoke, 0.9);

    float dust = 0.0;
    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        float2 base = float2(smokeHash(float2(fi, 1.3)), smokeHash(float2(fi, 7.9)));
        base = float2(0.2 + 0.74 * base.x, 0.16 + 0.68 * base.y);
        float2 drift = float2(sin(t * 0.31 + fi * 2.1) * 0.03, cos(t * 0.23 + fi * 1.3) * 0.07);
        float d = distance(l, (base + drift) * size);
        float twinkle = 0.5 + 0.5 * sin(t * 1.1 + fi * 4.0);
        dust += (0.25 + 0.75 * twinkle) * (smoothstep(1.4, 0.5, d) + 0.22 * smoothstep(4.5, 0.0, d));
    }

    float v = clamp(smoke + dust, 0.0, 1.0);
    return half4(half3(v), v);
}
