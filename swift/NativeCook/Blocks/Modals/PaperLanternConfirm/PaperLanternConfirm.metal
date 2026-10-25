#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Paper Lantern Confirm — two colour effects, step-for-step ports of the React Native SkSL
// (lanternShader.ts).
//
// paperLanternConfirmSky (full screen, behind the sheet): a night over a river valley. A three-stop
// sky from deep navy through plum to a warm glow on the horizon, seeded stars that twinkle with whole
// multiples of the loop, a moon with a soft halo, two ridges of hills, and a drift of distant
// lanterns rising from the horizon (each a pure function of phase, so they repeat with the loop).
//
// paperLanternConfirmLantern (a 180 × 240 pt view around one lantern; `origin` is the lantern's
// middle in the view): a washi-paper sky lantern. The bag is widest near the top and narrows to a
// bamboo ring at the bottom, with a fuel cell on a cross wire. `fold` (0 … 1) shows it creased and
// sagging, `glow` (0 … 1) lights the wick: a flickering flame and warm light through the paper, and a
// halo around the bag. The output is premultiplied and multiplied by the view's own alpha (the fill's
// opacity carries the lantern's opacity), transparent where there is no lantern.
//
// SwiftUI colours are sRGB, so there is no P3 conversion here.

namespace paperLanternConfirmShader {

constant float TAU = 6.2831853;

inline float sstep(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

inline float g2(float x) {
    return exp(-x * x);
}

inline float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

inline float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// One distant lantern: a warm core and a glow, rising once a loop.
inline float3 farLantern(float2 p, float i, float2 size, float horizon, float phase) {
    float h = hash(float2(i, 1.7));
    float depth = 0.35 + 0.65 * hash(float2(i, 4.1));
    float k = 1.0;
    float v = fract(phase * k + h);
    float x = (0.06 + 0.88 * hash(float2(i, 8.3))) * size.x + sin(v * 7.0 + i) * 10.0 * depth;
    float y = horizon - 10.0 - v * (horizon + 40.0) * (0.55 + 0.45 * depth);
    float s = 2.2 + 4.6 * depth * (1.0 - 0.45 * v);
    float2 q = (p - float2(x, y)) / float2(s, s * 1.35);
    float core = 1.0 - sstep(0.7, 1.0, length(q));
    float halo = exp(-length(q) * 0.9) * 0.35;
    float fade = sstep(0.0, 0.08, v) * (1.0 - sstep(0.75, 1.0, v));
    float flick = 0.85 + 0.15 * sin(phase * TAU * (3.0 + floor(h * 4.0)) + i);
    return float3(1.0, 0.62, 0.28) * (core * 0.9 + halo) * fade * flick * (0.45 + 0.55 * depth);
}

// The bag's half-width at height y (top -100, ring +78), creased and sagging while folded.
inline float bagHalf(float y, float fold) {
    float top = -100.0 + 16.0 * fold;
    float s = clamp((y - top) / (78.0 - top), 0.0, 1.0);
    float dome = pow(max(sin(min(s / 0.2, 1.0) * 1.5708), 0.0), 0.6);
    float w = mix(64.0, 40.0, sstep(0.22, 1.0, s)) * dome;
    // Folded: the top sags in and the sides pinch at each fold.
    w *= mix(1.0, 0.8 + 0.2 * s, fold);
    w *= 1.0 - fold * 0.05 * (0.5 + 0.5 * cos(y * 0.28));
    return y < top ? 0.0 : w;
}

} // namespace paperLanternConfirmShader

[[ stitchable ]] half4 paperLanternConfirmSky(
    float2 p,
    half4 color,
    float2 size,
    float horizon,
    float phase
) {
    using namespace paperLanternConfirmShader;
    float y = p.y / max(horizon, 1.0);
    float3 top = float3(0.03, 0.045, 0.13);
    float3 mid = float3(0.16, 0.09, 0.24);
    float3 low = float3(0.52, 0.27, 0.24);
    float3 col = y < 0.62 ? mix(top, mid, sstep(0.0, 0.62, y)) : mix(mid, low, sstep(0.62, 1.0, y));

    // Stars, denser and brighter high up, twinkling.
    float2 cell = floor(p / 9.0);
    float sh = hash(cell);
    if (sh > 0.93) {
        float2 sp = (cell + 0.2 + 0.6 * float2(hash(cell + 1.3), hash(cell + 2.9))) * 9.0;
        float tw = 0.6 + 0.4 * sin(phase * TAU * (1.0 + floor(sh * 40.0 - 37.0)) + sh * 50.0);
        float star = exp(-length(p - sp) * 1.6) * tw * (1.0 - sstep(0.2, 0.85, y));
        col += float3(0.9, 0.92, 1.0) * star * (0.5 + 0.5 * hash(cell + 7.0));
    }

    // The moon, high on the right, and its halo.
    float2 mc = float2(size.x * 0.8, horizon * 0.2);
    float md = length(p - mc);
    col += float3(0.6, 0.6, 0.75) * 0.25 * exp(-md / 60.0);
    float disc = 1.0 - sstep(15.0, 16.5, md);
    float craters = 0.9 + 0.1 * noise((p - mc) * 0.25);
    col = mix(col, float3(0.95, 0.92, 0.84) * craters, disc);

    // Distant lanterns.
    for (int i = 0; i < 18; i++) {
        col += farLantern(p, float(i), size, horizon, phase);
    }

    // Two ridges of hills on the horizon, the far one hazier.
    float far = horizon - 34.0 - 26.0 * noise(float2(p.x * 0.012, 3.0)) - 10.0 * noise(float2(p.x * 0.04, 1.0));
    if (p.y > far) col = mix(col, float3(0.14, 0.08, 0.16), 0.85);
    float near = horizon - 12.0 - 22.0 * noise(float2(p.x * 0.008 + 9.0, 5.0)) - 6.0 * noise(float2(p.x * 0.05, 2.0));
    if (p.y > near) col = float3(0.06, 0.04, 0.08) + float3(0.2, 0.1, 0.06) * 0.25 * sstep(near + 40.0, near, p.y);

    // Vignette and grain.
    float2 vc = (p / size - 0.5);
    col *= 1.0 - 0.5 * dot(vc, vc);
    col += (hash(floor(p * 2.0)) - 0.5) * 0.025;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}

[[ stitchable ]] half4 paperLanternConfirmLantern(
    float2 position,
    half4 color,
    float2 origin,
    float fold,
    float glow,
    float flicker,
    float seed
) {
    using namespace paperLanternConfirmShader;
    float2 p = position - origin;
    float w = bagHalf(p.y, fold);
    float top = -100.0 + 16.0 * fold;
    float inside = (p.y < 78.0 && w > 0.5) ? clamp(w - abs(p.x) + 0.5, 0.0, 1.0) : 0.0;
    float u = clamp(p.x / max(w, 1.0), -1.0, 1.0);
    float v = clamp((p.y - top) / (78.0 - top), 0.0, 1.0);
    float3 col = float3(0.0);
    float a = 0.0;

    // The flame: a small teardrop above the fuel cell.
    float2 fp = p - float2(sin(flicker * TAU * 3.0) * 0.8, 70.0);
    float flameH = 14.0 * glow * (0.9 + 0.1 * sin(flicker * TAU * 5.0));
    float2 fq = float2(fp.x / (3.4 + 1.2 * glow), (fp.y + flameH * 0.5) / max(flameH, 0.1));
    float flame = (1.0 - sstep(0.75, 1.0, length(float2(fq.x / (1.0 - clamp(-fq.y, 0.0, 1.0) * 0.7 + 0.001), fq.y)))) * step(0.02, glow);

    if (inside > 0.0) {
        // Washi: long fibres, a soft mottle, the two glued seams.
        float fib = noise(float2(p.x * 0.5 + seed, p.y * 0.06)) * 0.6 + noise(float2(p.x * 1.3, p.y * 0.12 + seed)) * 0.4;
        float mottle = noise(p * 0.08 + seed);
        float seam = g2((abs(u) - 0.55) / 0.02) * 0.6;
        // Lit by the night (cool, from above) when dark.
        float3 paper = float3(0.97, 0.94, 0.88) * (0.9 + 0.07 * mottle + 0.03 * fib);
        float side = 1.0 - pow(abs(u), 2.5) * 0.32;
        float3 dark = paper * side * (0.98 - 0.1 * v) * (1.0 - 0.12 * seam);
        // Lit from inside: warm, brightest low and in the middle, the fibres show as darker veins.
        float through = (0.5 + 0.5 * v) * (1.0 - 0.6 * u * u);
        float3 warm = mix(float3(0.98, 0.46, 0.13), float3(1.0, 0.93, 0.68), sstep(0.15, 0.95, through));
        warm *= (0.82 + 0.4 * through) * (0.92 + 0.08 * (1.0 - fib)) * (1.0 - 0.25 * seam);
        warm *= 0.92 + 0.08 * sin(flicker * TAU * 4.0);
        col = mix(dark, warm, glow);
        // Creases: horizontal folds, light above and shadow below each, gone once it fills.
        float fz = sin(p.y * 0.28 + 0.6 * noise(float2(p.x * 0.05, 2.0)));
        col *= 1.0 + fold * (0.07 * fz - 0.06 * sstep(0.7, 1.0, -fz));
        col *= 1.0 - fold * 0.1 * g2((abs(u) - 0.3) / 0.05);
        // The top rim of the bag catches the moon when dark.
        col += float3(0.25, 0.26, 0.32) * (1.0 - glow) * sstep(0.08, 0.0, v) * 0.6;
        a = inside;
    }

    // The bamboo ring at the bottom, the cross wire and the fuel cell.
    float ringW = bagHalf(77.0, fold);
    float2 rq = float2(p.x / max(ringW, 1.0), (p.y - 78.0) / 4.0);
    float ring = 1.0 - sstep(0.8, 1.0, abs(length(rq) - 1.0) * ringW / 2.2);
    float wire = (1.0 - sstep(0.5, 1.0, abs(p.y - 78.0))) * step(abs(p.x), ringW);
    float2 cq = p - float2(0.0, 79.0);
    float cell = 1.0 - sstep(0.0, 1.0, max(abs(cq.x) - 6.0, abs(cq.y) - 3.0));
    float hard = max(max(ring, wire * 0.8), cell);
    float3 bamboo = mix(float3(0.42, 0.3, 0.17), float3(0.75, 0.55, 0.3), 0.5 + 0.5 * rq.y);
    bamboo += float3(1.0, 0.6, 0.25) * glow * 0.5;
    col = mix(col, cell > 0.5 ? float3(0.18, 0.15, 0.13) + float3(0.6, 0.3, 0.1) * glow : bamboo, hard);
    a = max(a, hard);

    // The flame and the halo of light round the lantern.
    float3 flameC = mix(float3(1.0, 0.45, 0.1), float3(1.0, 0.95, 0.75), sstep(0.0, 1.0, 1.0 - length(fq)));
    col = mix(col, flameC, flame);
    a = max(a, flame);
    float2 hq = float2(p.x / 70.0, (p.y + 6.0) / 100.0);
    float halo = exp(-max(length(hq) - 0.72, 0.0) * 7.0) * glow * (1.0 - inside) * 0.55;
    float3 premul = clamp(col, 0.0, 1.0) * a + float3(1.0, 0.6, 0.28) * halo * (1.0 - a);
    float alpha = a + halo * (1.0 - a);
    float o = float(color.a);
    return half4(half3(premul * o), half(alpha * o));
}
