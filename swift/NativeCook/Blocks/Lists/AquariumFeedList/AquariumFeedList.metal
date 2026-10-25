#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

/*
 Aquarium Feed List — the light in the water, laid over the whole screen (a colorEffect on a
 full-screen rectangle).

 Caustics: two Voronoi edge networks (thin cyan cells), their points circling a whole number of
 times per loop phase `ph` = 2πt/loop so they repeat exactly. Stirring the water (`stir`) speeds
 the warp and brightens the network; a slow large noise breaks it into drifting patches; it fades
 with depth and is held at 40 % over the cards so their text stays clear; scrolling shifts it a
 little. God rays fall from the surface and fade with depth. The output is light only:
 premultiplied with its brightness as alpha, transparent where none falls.
*/

static float2 AquariumFeedListHash2(float2 p) {
    float3 q = fract(float3(p.xyx) * float3(0.1031, 0.103, 0.0973));
    q += dot(q, q.yzx + 33.33);
    return fract((q.xx + q.yz) * q.zy);
}

static float AquariumFeedListNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(AquariumFeedListHash2(i).x, AquariumFeedListHash2(i + float2(1.0, 0.0)).x, f.x),
               mix(AquariumFeedListHash2(i + float2(0.0, 1.0)).x, AquariumFeedListHash2(i + float2(1.0, 1.0)).x, f.x), f.y);
}

static float AquariumFeedListStep(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// 1 inside a card's rounded rectangle (16 pt from each side, card height tall, one per row), soft at the edge.
static float AquariumFeedListOnCard(float2 p, float2 size, float scroll, float listTop, float row, float cardH, float cards) {
    float y = p.y - (listTop + 16.0 - scroll);
    float k = floor(y / row);
    if (k < 0.0 || k >= cards) return 0.0;
    float2 c = float2(size.x * 0.5, k * row + cardH * 0.5);
    float2 h = float2(size.x * 0.5 - 16.0, cardH * 0.5);
    float2 q = abs(float2(p.x, y) - c) - h + 22.0;
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 22.0;
    return 1.0 - AquariumFeedListStep(-3.0, 3.0, d);
}

// A caustic net: the edges of a Voronoi diagram whose points circle their cells (a whole number
// of turns a loop), thin where cells meet. The domain is warped a little so the cells bulge.
static float AquariumFeedListNet(float2 uv, float a) {
    uv += 0.12 * float2(sin(uv.y * 3.1 + a), cos(uv.x * 2.7 - a * 2.0));
    float2 i = floor(uv);
    float2 f = fract(uv);
    float d1 = 8.0;
    float d2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float2 g = float2(float(x), float(y));
            float2 h = AquariumFeedListHash2(i + g);
            float k = floor(h.x * 2.0) + 1.0;
            float2 o = 0.5 + 0.38 * float2(sin(a * k + h.y * 6.2831), cos(a * k + h.x * 6.2831));
            float d = length(g + o - f);
            if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) { d2 = d; }
        }
    }
    return 1.0 - AquariumFeedListStep(0.0, 0.07, d2 - d1);
}

[[ stitchable ]] half4 AquariumFeedListLight(
    float2 p, half4 color,
    float2 size, float ph, float stir, float scroll, float listTop, float row, float cardH, float cards
) {
    float2 uv = float2(p.x / 260.0, (p.y + scroll * 0.35) / 260.0);
    float a = ph + stir * 0.6 * sin(ph * 3.0);
    float depth = clamp(p.y / size.y, 0.0, 1.0);
    // Two nets, the finer one drifting the other way; where both cross the light pools brightest.
    float c = AquariumFeedListNet(uv * 1.6, a) * 0.75 + AquariumFeedListNet(uv * 2.7 + float2(3.7, 1.3), -a) * 0.45;
    // Patches of light drift across the net: a large, slow noise sampled on a circle (loops).
    float n = AquariumFeedListNoise(p / 230.0 + 0.7 * float2(cos(ph), sin(ph)) + float2(0.0, scroll / 600.0));
    float drift = 0.2 + 0.95 * AquariumFeedListStep(0.3, 0.78, n);
    // Strongest near the surface, fading with depth; dimmed to 40 % on the cards' glass.
    float fall = mix(1.15, 0.22, AquariumFeedListStep(0.0, 0.9, depth));
    float light = clamp(c, 0.0, 1.2) * (0.42 + 0.22 * stir) * drift * fall
        * (1.0 - 0.6 * AquariumFeedListOnCard(p, size, scroll, listTop, row, cardH, cards));

    // God rays: slanted soft bands from the surface, swaying with the loop.
    float x = p.x + p.y * 0.32;
    float rays = 0.5 + 0.5 * sin(x * 0.021 + 0.8 * sin(ph)) * sin(x * 0.047 - ph * 2.0 + 1.3);
    rays = rays * rays * rays * exp(-depth * 2.4) * 0.16;

    // Light only: premultiplied, its alpha its brightness, so the cards and water below show
    // through and gain the light.
    float3 col = float3(0.62, 1.0, 0.96) * light + float3(0.7, 0.95, 1.0) * rays;
    float alpha = clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0);
    return half4(half3(min(col, float3(alpha))), half(alpha));
}
