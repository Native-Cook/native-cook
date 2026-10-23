#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The watercolour orb: domain-warped value noise painted with a three-colour palette, inside a
// circle. The same code is in WatercolorVoiceOrbs.kt (AGSL) and orb-shader.ts (SkSL).

static float watercolorVoiceOrbsHash(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

static float watercolorVoiceOrbsNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(watercolorVoiceOrbsHash(i), watercolorVoiceOrbsHash(i + float2(1.0, 0.0)), u.x),
        mix(watercolorVoiceOrbsHash(i + float2(0.0, 1.0)), watercolorVoiceOrbsHash(i + float2(1.0, 1.0)), u.x),
        u.y);
}

static float watercolorVoiceOrbsFbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * watercolorVoiceOrbsNoise(p);
        p = float2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 2.02 + 17.1;
        a *= 0.5;
    }
    return v;
}

static float3 watercolorVoiceOrbsPaint(float v, float qx, float3 deep, float3 mid, float3 light) {
    float3 col = mix(deep, mid, smoothstep(0.22, 0.58, v));
    return mix(col, light, smoothstep(0.52, 0.9, v + 0.18 * (qx - 0.5)));
}

// center/radius: the orb in the drawing's coordinates. tau: 2π · loop time / loop, so every drift
// below comes back to itself at the end of the loop. deep/mid/light A and B: the palettes of the two
// voices the picker is between, and mixAB how far it is (0…1): the second palette seeps in through
// the clouds rather than fading. band: the direction the bright side faces. offset/swirl: where in
// the noise the voice sits and how much its middle is twisted. Returns premultiplied colour,
// transparent outside the circle.
[[ stitchable ]] half4 watercolorVoiceOrbs(float2 position, float2 center, float radius, float tau,
                                           float3 deepA, float3 midA, float3 lightA,
                                           float3 deepB, float3 midB, float3 lightB, float mixAB,
                                           float2 band, float2 offset, float swirl) {
    float2 uv = (position - center) / radius;
    float r = length(uv);
    float ang = swirl * (1.0 - r * r) + 0.25 * sin(tau);
    float c = cos(ang), s = sin(ang);
    float2 p = float2(c * uv.x - s * uv.y, s * uv.x + c * uv.y) * 1.2 + offset;
    float2 q = float2(
        watercolorVoiceOrbsFbm(p + 0.7 * float2(cos(tau), sin(tau))),
        watercolorVoiceOrbsFbm(p + float2(5.2, 1.3) + 0.7 * float2(sin(tau), -cos(tau))));
    float f = watercolorVoiceOrbsFbm(p + 2.4 * q + 0.5 * float2(cos(2.0 * tau + 1.0), sin(2.0 * tau + 1.0)));
    float v = f + 0.42 * dot(uv, band);
    float3 colA = watercolorVoiceOrbsPaint(v, q.x, deepA, midA, lightA);
    float3 colB = watercolorVoiceOrbsPaint(v, q.x, deepB, midB, lightB);
    // The seep: where the warp field is low the new colours arrive first.
    float seep = q.y + 0.25 * (f - 0.5);
    float3 col = mix(colA, colB, smoothstep(seep - 0.12, seep + 0.12, mixAB * 1.5 - 0.25));
    // A little depth: the rim darkens, a soft light sits upper left.
    col *= 1.0 - 0.16 * smoothstep(0.62, 1.0, r);
    col += 0.08 * (1.0 - smoothstep(0.0, 0.9, length(uv - float2(-0.35, -0.45))));
    float edge = 1.0 / radius;
    float alpha = 1.0 - smoothstep(1.0 - edge, 1.0 + edge, r);
    return half4(half3(min(col, float3(1.0)) * alpha), half(alpha));
}
