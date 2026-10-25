#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Ripple Pond Heatmap in one full-screen pass: a still pond seen from above, its floor of sand and
// pebbles lit by drifting caustics, and the month's lily pads floating on it. A step-for-step port
// of the React Native SkSL (rippleShader.ts); see there for the full description.
//
// The pads are the effect's layer (an image baked once, RipplePondHeatmapArt.swift), sampled
// around each cell's centre, lifted and shifted by the water there. SwiftUI shaders work in sRGB,
// so the P3 conversion is left out; `smoothstep` is reimplemented (one call has reversed edges).

namespace ripplePondHeatmap {

constant float TAU = 6.2831853;
constant float SPEED = 140.0;
constant float K = 0.25;      // 2π / 25 pt

struct Scene {
    float2 size;
    float4 grid;    // left, top, cell width, row height
    float4 src[8];  // ripple: x, y, age (s), amplitude
    float4 sel;     // selected pad x, y, glow 0..1, 0
    float phase;    // loop clock 0..1
};

static inline float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

static inline float hsh(float2 q) { return fract(precise::sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

static float vnoise(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hsh(i), hsh(i + float2(1.0, 0.0)), u.x),
               mix(hsh(i + float2(0.0, 1.0)), hsh(i + float2(1.0, 1.0)), u.x), u.y);
}

// One source's height (pt) and slope at p: (h, dh/dx, dh/dy).
static float3 wave(float2 p, float4 s) {
    if (s.w <= 0.001 || s.z < 0.0 || s.z > 6.0) return float3(0.0);
    float2 d = p - s.xy;
    float r = length(d) + 0.001;
    float front = SPEED * s.z;
    float width = 22.0 + 26.0 * s.z;
    float x = r - front;
    if (abs(x) > width * 3.0) return float3(0.0);
    float env = exp(-(x * x) / (width * width));
    float fade = exp(-s.z / 1.3) * s.w * 7.0 / sqrt(1.0 + r / 30.0);
    float ph = K * x + 1.2;
    float h = fade * env * sin(ph);
    float dhdr = fade * env * (K * cos(ph) - 2.0 * x / (width * width) * sin(ph));
    return float3(h, d / r * dhdr);
}

static float3 waves(float2 p, thread const Scene &s) {
    float3 w = float3(0.0);
    for (int i = 0; i < 8; i++) w += wave(p, s.src[i]);
    return w;
}

// The pond floor: dark teal deepening downward, sand and pebbles, slow caustics.
static float3 floorAt(float2 p, float focus, thread const Scene &s) {
    float2 uv = p / s.size;
    float3 col = mix(float3(0.10, 0.27, 0.28), float3(0.035, 0.12, 0.15), sstep(0.0, 1.0, uv.y));
    col = mix(col, float3(0.05, 0.16, 0.19), sstep(0.3, 0.9, uv.x) * 0.4);
    float sand = vnoise(p / 5.0) * 0.5 + vnoise(p / 23.0) * 0.5;
    col *= 0.86 + 0.24 * sand;
    float2 cell = floor(p / 38.0);
    float2 pc = (cell + 0.3 + 0.4 * float2(hsh(cell), hsh(cell + 4.0))) * 38.0;
    float pr = 4.0 + 8.0 * hsh(cell + 9.0);
    float pd = length(p - pc);
    if (hsh(cell + 2.0) > 0.72) {
        float pebble = 1.0 - sstep(pr - 2.5, pr + 1.0, pd);
        col = mix(col, col * (1.12 + 0.2 * hsh(cell + 6.0)), pebble * 0.5);
    }
    float a = s.phase * TAU;
    float2 q = p / 46.0;
    float n1 = vnoise(q + float2(cos(a), sin(a)) * 0.8);
    float n2 = vnoise(q * 1.3 + 5.0 + float2(sin(a), -cos(a)) * 0.8);
    float net = abs(sin((q.x + n1 * 2.2) * 3.1)) * abs(sin((q.y + n2 * 2.2) * 2.7));
    float caustic = pow(1.0 - net, 12.0);
    col += float3(0.30, 0.45, 0.38) * caustic * (0.12 + 0.7 * max(focus, 0.0));
    return col;
}

static float3 scene(float2 pos, SwiftUI::Layer pads, thread const Scene &s) {
    float2 p = pos;
    float3 w = waves(p, s);
    float2 slope = w.yz;
    float3 col = floorAt(p - slope * 34.0, w.x * 0.18 - (slope.x + slope.y) * 0.4, s);
    float2 uv = p / s.size;
    float3 skyC = float3(0.62, 0.78, 0.80);
    float fres = 0.05 + 0.18 * sstep(1.0, 0.0, uv.y) + clamp(length(slope) * 1.2, 0.0, 0.35);
    col = mix(col, skyC, fres * 0.55);
    float glint = pow(clamp(dot(normalize(float3(-slope, 1.0)), normalize(float3(-0.35, -0.6, 0.72))), 0.0, 1.0), 220.0);
    col += float3(1.0, 0.98, 0.92) * glint * 0.8 * clamp(length(slope) * 6.0, 0.0, 1.0);

    // The pads: each sampled around its own cell's centre, lifted and tilted by the water there.
    float2 gq = (p - s.grid.xy) / s.grid.zw;
    float2 cell = floor(gq);
    if (cell.x >= -0.5 && cell.x < 6.5 && cell.y >= -0.5 && cell.y < 4.5) {
        float2 c = s.grid.xy + (cell + 0.5) * s.grid.zw;
        float3 wc = waves(c, s);
        float lift = 1.0 + wc.x * 0.012;
        float2 shift = float2(wc.y, wc.z) * 6.0 + float2(0.0, -wc.x * 0.35);
        float2 sp = c + (p - c - shift) / lift;
        float sh = float(pads.sample(sp - float2(3.0, 5.0)).a);
        col *= 1.0 - 0.38 * sh;
        float4 pad = float4(pads.sample(sp));
        if (pad.a > 0.001) {
            float3 pc = pad.rgb / pad.a;
            pc += float3(0.8, 0.95, 0.8) * clamp(dot(float2(wc.y, wc.z), float2(-0.5, -0.7)) * 0.9, 0.0, 0.22);
            pc *= 1.0 - clamp(dot(float2(wc.y, wc.z), float2(0.5, 0.7)) * 0.7, 0.0, 0.18);
            col = mix(col, pc, pad.a);
        }
    }
    // The selected day: a soft ring of light around its pad.
    if (s.sel.z > 0.001) {
        float rd = abs(length(p - s.sel.xy) - s.grid.z * 0.5);
        col += float3(0.85, 1.0, 0.82) * exp(-rd * rd / 4.0) * s.sel.z * 0.6;
        col += float3(0.5, 0.8, 0.55) * exp(-rd * rd / 60.0) * s.sel.z * 0.12;
    }

    col *= 1.0 - 0.3 * dot(uv - 0.5, uv - 0.5);
    col += (hsh(pos) - 0.5) / 255.0;
    return clamp(col, 0.0, 1.0);
}

} // namespace ripplePondHeatmap

[[ stitchable ]] half4 RipplePondHeatmapScene(
    float2 position,
    SwiftUI::Layer layer,
    float2 size,
    float4 grid,
    float4 s0,
    float4 s1,
    float4 s2,
    float4 s3,
    float4 s4,
    float4 s5,
    float4 s6,
    float4 s7,
    float4 sel,
    float phase
) {
    ripplePondHeatmap::Scene s;
    s.size = size;
    s.grid = grid;
    s.src[0] = s0;
    s.src[1] = s1;
    s.src[2] = s2;
    s.src[3] = s3;
    s.src[4] = s4;
    s.src[5] = s5;
    s.src[6] = s6;
    s.src[7] = s7;
    s.sel = sel;
    s.phase = phase;
    return half4(half3(ripplePondHeatmap::scene(position, layer, s)), 1.0h);
}
