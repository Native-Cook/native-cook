#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// A port of the React Native SkSL (brushInkShader.ts).
//
// In React Native the strokes are a triangle mesh whose texture coordinates are stroke
// coordinates: s = distance along the shared paint clock (pt), v = the position across the stroke
// in half-widths. SwiftUI has no mesh with custom texture coordinates, so brushInkSplashInk finds
// them itself: each stroke is drawn over its own bounds, and every pixel projects onto the nearest
// segments of the stroke's centreline (only the chunks of 8 segments whose bounding circle holds
// it). Among the segments that reach it, the painted one closest to the middle wins. From s and v
// on, the shading is the SkSL's, step for step: a rounded wet tip, ragged edges, dry bristle
// streaks toward a stroke's end, pigment pooling at the rim, and bleeding into the fibres.
//
// brushInkSplashPaper is the rice paper. SwiftUI's shader colours are sRGB already, so the SkSL's
// P3 conversion is left out. GLSL's smoothstep is reimplemented so it behaves the same for any
// edges.

namespace brushInkSplashShader {

static inline float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

static float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

static float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), u.x),
               mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), u.x), u.y);
}

} // namespace brushInkSplashShader

using namespace brushInkSplashShader;

// pts: x, y, half-width, s per centreline point (screen points). chunks: centre x, centre y,
// radius, first point. stroke: s0, length, dryness, index. caps: start cap, end cap, margin, head.
// ink: bleed, alpha.
[[ stitchable ]] half4 brushInkSplashInk(
    float2 position, half4 color, float2 origin, device const float *pts, int ptsCount,
    device const float *chunks, int chunksCount, float4 stroke, float4 caps, float2 ink
) {
    float alpha = ink.y;
    if (alpha < 0.002 || color.a < 0.002h) return half4(0.0h);
    float2 q = position + origin;
    float head = caps.w;
    float margin = caps.z;
    int n = ptsCount / 4;

    bool found = false;
    float bestScore = 1e9;
    float s = 0.0;
    float v = 0.0;
    int nc = chunksCount / 4;
    for (int c = 0; c < nc; c++) {
        float2 cc = float2(chunks[c * 4], chunks[c * 4 + 1]);
        if (distance(q, cc) > chunks[c * 4 + 2]) continue;
        int i0 = int(chunks[c * 4 + 3]);
        int i1 = min(i0 + 8, n - 1);
        for (int i = i0; i < i1; i++) {
            float2 a = float2(pts[i * 4], pts[i * 4 + 1]);
            float2 b = float2(pts[i * 4 + 4], pts[i * 4 + 5]);
            float2 d = b - a;
            float L2 = dot(d, d);
            if (L2 < 1e-8) continue;
            float t = clamp(dot(q - a, d) / L2, 0.0, 1.0);
            float hw = mix(pts[i * 4 + 2], pts[i * 4 + 6], t);
            float2 rel = q - (a + d * t);
            float2 dir = d * rsqrt(L2);
            // The mesh's −margin edge is on the side (−dir.y, dir.x).
            float side = dot(rel, float2(-dir.y, dir.x));
            float vv = -(side < 0.0 ? -1.0 : 1.0) * length(rel) / max(hw, 0.01);
            if (abs(vv) > margin) continue;
            float ss = mix(pts[i * 4 + 3], pts[i * 4 + 7], t);
            float score = abs(vv) + (head - ss > 0.0 ? 0.0 : 100.0);
            if (score < bestScore) {
                bestScore = score;
                s = ss;
                v = vv;
                found = true;
            }
        }
    }
    if (!found) return half4(0.0h);

    float k = stroke.w;
    float s0 = stroke.x;
    float len = stroke.y;
    float dr = stroke.z;
    float cap = caps.x;
    float endCap = caps.y;
    float bleed = ink.x;

    float behind = head - s;
    if (behind <= 0.0) return half4(0.0h);
    float u = clamp((s - s0) / len, 0.0, 1.0);
    float av = abs(v);

    // The wet tip: the stroke is rounded off just behind the brush.
    float tip = sqrt(clamp(behind / 5.0, 0.0, 1.0));
    // Round caps before the start and past the end of the stroke.
    float before = max(s0 - s, 0.0) / cap;
    float after = max(s - s0 - len, 0.0) / endCap;
    float beyond = max(before, after);
    tip *= sqrt(max(0.0, 1.0 - beyond * beyond));
    // Ragged edges, and the fibres the ink creeps along as it bleeds.
    float rag = (vnoise(float2(s * 0.3, k * 5.0 + (v > 0.0 ? 0.0 : 9.0))) - 0.5) * 0.22;
    float fibre = vnoise(float2(s * 0.9, v * 3.0 + k * 11.0)) * vnoise(float2(s * 0.12 + 3.0, v * 9.0));
    float edge = tip + rag;
    float cover = 1.0 - sstep(edge - 0.07, edge + 0.04, av);

    // Dry brush: bristle streaks along the stroke open up toward its end.
    float dryness = dr * sstep(0.4, 1.0, u);
    float bristle = vnoise(float2(v * 7.5 + k * 3.1, s * 0.035)) * 0.7 + vnoise(float2(v * 19.0, s * 0.12)) * 0.3;
    float streak = sstep(dryness * 0.75 - 0.08, dryness * 0.75 + 0.08, bristle);
    cover *= mix(1.0, streak, step(0.001, dryness));
    // Pigment pools at the rim; the middle is a touch lighter; dry ink is greyer.
    float rim = sstep(edge - 0.4, edge - 0.04, av);
    float3 inkCol = mix(float3(0.16, 0.15, 0.17), float3(0.075, 0.07, 0.085), rim);
    inkCol = mix(inkCol, float3(0.3, 0.29, 0.3), dryness * 0.45);
    inkCol += (vnoise(float2(s * 0.08, v * 2.0)) - 0.5) * 0.04;

    // Bleeding: a soft grey halo creeping out along the fibres past the edge.
    float reach = edge + bleed * (0.25 + 0.45 * fibre);
    float halo = bleed * (1.0 - sstep(edge, reach, av)) * step(edge - 0.02, av) * (0.12 + 0.4 * fibre);
    float a = max(cover, halo) * alpha;
    float3 col = cover >= halo ? inkCol : mix(float3(0.26, 0.25, 0.27), inkCol, 0.55);
    col = clamp(col, 0.0, 1.0);
    // `color.a` carries the opacity of the views around (the sheet fades back in over the home
    // screen); a colour effect replaces the colour, so it has to be applied here.
    return half4(half3(col * a), half(a)) * color.a;
}

[[ stitchable ]] half4 brushInkSplashPaper(float2 p, half4 color, float2 size) {
    float2 uv = p / size;
    float3 col = mix(float3(0.957, 0.937, 0.894), float3(0.93, 0.902, 0.85), uv.y);
    // Rice paper: long wavy kozo fibres in two layers, soft clouds, fine grain.
    float cloud = vnoise(p * 0.02) * 0.6 + vnoise(p * 0.05) * 0.4;
    col *= 0.975 + cloud * 0.04;
    for (int k = 0; k < 2; k++) {
        float a = float(k) * 1.7 + 0.3;
        float2 d = float2(cos(a), sin(a));
        float2 q = float2(dot(p, d), dot(p, float2(-d.y, d.x)));
        q.y += sin(q.x * 0.03 + float(k)) * 6.0;
        float f = sstep(0.84, 0.95, vnoise(q * float2(0.025, 0.7) + float(k) * 5.0));
        col += f * 0.025;
        col -= sstep(0.86, 0.96, vnoise(q * float2(0.04, 1.1) + 13.0)) * 0.02;
    }
    col += (hash21(floor(p * 2.0)) - 0.5) * 0.02;
    float2 v = uv - 0.5;
    col *= 1.0 - dot(v, v) * 0.25;
    // Keep the opacity of the views around (the fresh sheet fades in over the home screen).
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0h) * color.a;
}
