#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The ticket's shaders, ported from the React Native version's Skia runtime effect.

static float scratchTicketHash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

static float scratchTicketNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = scratchTicketHash(i);
    float b = scratchTicketHash(i + float2(1.0, 0.0));
    float c = scratchTicketHash(i + float2(0.0, 1.0));
    float d = scratchTicketHash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

/// Holographic scratch foil. Brushed silver with a diamond guilloché pressed into it, rainbow bands
/// that slide with `tilt` and `time`, a soft specular sweep and a few twinkling sparkles.
/// `dissolve` (0…1) eats the foil away along a noise field with a hot golden edge. `time` is a
/// phase in radians and only enters through sin(time · n), so it loops. `origin` is where the
/// foil's (0, 0) sits in the canvas.
[[ stitchable ]] half4 scratchTicketFoil(float2 position, float2 origin, float2 size, float time, float2 tilt, float dissolve) {
    float2 xy = position - origin;
    float2 uv = xy / size;
    float n = scratchTicketNoise(xy * 0.022) * 0.65 + scratchTicketNoise(xy * 0.07) * 0.35;
    // Brushed metal: fine streaks running across the foil.
    float brushed = scratchTicketNoise(float2(xy.x * 0.015, xy.y * 0.9)) * 0.6 + scratchTicketNoise(float2(xy.x * 0.03, xy.y * 1.7)) * 0.4;

    // A fine diamond guilloché, pressed in so its lines catch a little light.
    float2 g = float2(xy.x + xy.y, xy.x - xy.y) / 13.0;
    float2 d = abs(fract(g) - 0.5);
    float line = 1.0 - smoothstep(0.0, 0.06, min(d.x, d.y));

    float band = uv.x * 0.7 + uv.y * 0.5 + n * 0.35 + tilt.x * 0.35 + tilt.y * 0.25 + sin(time) * 0.1;
    float3 rainbow = 0.72 + 0.28 * cos(6.28318 * (band + float3(0.0, 0.33, 0.67)));
    float3 silver = float3(0.76, 0.77, 0.80) + (brushed - 0.5) * 0.14 + (n - 0.5) * 0.08;
    float3 col = silver * mix(float3(1.0), rainbow, 0.45);
    col += line * 0.045;

    // A soft band of light that drifts with the time and follows the tilt.
    float lightAt = 0.55 + 0.5 * sin(time) + tilt.x * 0.4;
    float s = uv.x * 0.8 + uv.y * 0.45 - lightAt;
    col += exp(-s * s * 22.0) * float3(0.26, 0.26, 0.28);
    // Darker towards the edges, like foil that is not quite flat.
    col *= 1.0 - 0.12 * length(uv - 0.5);

    // Sparkles: a few cells hold a tiny four-point star that twinkles.
    float cellSize = 11.0;
    float2 cell = floor(xy / cellSize);
    float h = scratchTicketHash(cell);
    float2 at = (cell + 0.25 + 0.5 * float2(scratchTicketHash(cell + 7.1), scratchTicketHash(cell + 3.7))) * cellSize;
    float2 q = abs(xy - at);
    float star = max(0.0, 1.0 - length(q) / 1.8) + max(0.0, 1.0 - q.x / 0.7) * max(0.0, 1.0 - q.y / 4.5) + max(0.0, 1.0 - q.y / 0.7) * max(0.0, 1.0 - q.x / 4.5);
    float twinkle = step(0.965, h) * pow(0.5 + 0.5 * sin(time * 3.0 + h * 60.0), 4.0);
    col += min(star, 1.2) * twinkle * 0.9;

    float dn = scratchTicketNoise(xy * 0.05) * 0.7 + scratchTicketNoise(xy * 0.16) * 0.3;
    float edgeAt = dissolve * 1.3 - 0.15;
    float a = smoothstep(edgeAt - 0.02, edgeAt + 0.02, dn);
    float glow = (1.0 - smoothstep(0.0, 0.07, abs(dn - edgeAt))) * step(0.001, dissolve) * (1.0 - dissolve * 0.3);
    col = mix(col, float3(1.0, 0.9, 0.58), glow * 0.85);
    a = max(a, glow);
    return half4(half3(col * a), half(a));
}

/// Grain: two octaves of value noise at `frequency` (per point), as a grey `base + amount · n`.
[[ stitchable ]] half4 scratchTicketGrain(float2 position, float frequency, float base, float amount) {
    float2 p = position * frequency;
    float n = scratchTicketNoise(p) * 0.67 + scratchTicketNoise(p * 2.0 + 17.3) * 0.33;
    half v = half(base + amount * n);
    return half4(v, v, v, 1.0);
}
