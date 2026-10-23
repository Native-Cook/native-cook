#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The card's ink: three colour clouds stirred in water, with a sprayed, grainy edge between them.
// The same code is in InkGradientSongCard.kt (AGSL) and ink-shader.ts (SkSL).

static float inkGradientSongCardHash(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

static float inkGradientSongCardNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(inkGradientSongCardHash(i), inkGradientSongCardHash(i + float2(1.0, 0.0)), u.x),
        mix(inkGradientSongCardHash(i + float2(0.0, 1.0)), inkGradientSongCardHash(i + float2(1.0, 1.0)), u.x),
        u.y);
}

static float inkGradientSongCardFbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * inkGradientSongCardNoise(p);
        p = float2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 2.03 + 11.3;
        a *= 0.5;
    }
    return v;
}

// position: points from the card's top left. size: the card in points. grainScale: points per
// grain cell (0.5). tau: 2π · loop time / loop. stir: where the ink is being stirred (points) and
// how hard (radians at the centre of the swirl).
[[ stitchable ]] half4 inkGradientSongCard(float2 position, float2 size, float grainScale, float tau,
                                           float2 stir, float strength) {
    float2 uv = position / size.y;
    float2 s = stir / size.y;
    // The stir: a swirl around the stirring point, fading over about a third of the card.
    float2 d = uv - s;
    float ang = strength * exp(-dot(d, d) / 0.045);
    float c = cos(ang), sn = sin(ang);
    float2 w = s + float2(c * d.x - sn * d.y, sn * d.x + c * d.y);
    float2 p = w * 2.2;
    float2 q = float2(
        inkGradientSongCardFbm(p + 0.8 * float2(cos(tau), sin(tau))),
        inkGradientSongCardFbm(p + float2(5.2, 1.3) + 0.8 * float2(sin(tau), -cos(tau))));
    float f = inkGradientSongCardFbm(p + 2.2 * q + 0.6 * float2(cos(tau + 1.0), sin(2.0 * tau)));

    // Spray: each grain cell nudges the thresholds, so the edges between clouds stipple.
    float2 cell = floor(position / grainScale);
    float g1 = inkGradientSongCardHash(cell) - 0.5;
    float g2 = inkGradientSongCardHash(cell + float2(37.0, 17.0)) - 0.5;

    float3 burgundy = float3(0.420, 0.063, 0.125);
    float3 red = float3(0.878, 0.145, 0.122);
    float3 lilac = float3(0.788, 0.545, 0.910);
    float redAmount = smoothstep(0.55, 0.61, q.x + 0.22 * (uv.y - 0.5) + 0.05 * sin(tau) + 0.16 * g1);
    float lilacAmount = smoothstep(0.57, 0.63, f + 0.04 * cos(tau) + 0.16 * g2);
    float3 col = mix(burgundy, red, redAmount);
    col = mix(col, lilac, lilacAmount);
    // Film grain over everything.
    col *= 0.94 + 0.12 * inkGradientSongCardHash(cell + float2(91.0, 7.0));
    return half4(half3(col), 1.0h);
}
