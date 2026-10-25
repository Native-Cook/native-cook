#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Zipper Unlock — the denim cover, one full-screen pass over the journal. A step-for-step port of
// DENIM_SKSL (zipperShader.ts); see there for the full description.
//
// Each half is a sheet of indigo twill anchored to its zipper edge at cx ∓ gap(y): the weave, the
// washes and the stitching travel with the half. Near the edge the cloth rolls back over a
// cylinder of radius R; the zipper tape runs along each edge with two rows of gold stitches, and
// another row along the screen's border. Inside the gap the output is transparent but for the
// halves' contact shadow on the page beneath.
//
// SwiftUI colours are sRGB, so the P3 conversion is left out. GLSL's smoothstep is reimplemented
// (sstep) because the Skia version calls it with reversed edges, which Metal leaves undefined;
// pow of a possibly negative base is written as a square. The output is premultiplied.

namespace zipperUnlockShader {

constant float TAU = 6.2831853;

float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float sm(float x) { float c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }

float h1(float n) { return fract(sin(n * 12.9898) * 43758.5453); }
float h2(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vn(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h2(i), h2(i + float2(1.0, 0.0)), f.x), mix(h2(i + float2(0.0, 1.0)), h2(i + float2(1.0, 1.0)), f.x), f.y);
}

float fbm(float2 q) {
    return vn(q) * 0.55 + vn(q * 2.1 + 13.7) * 0.3 + vn(q * 4.3 - 7.1) * 0.15;
}

// Indigo twill at cloth point q (pt).
float3 denim(float2 q) {
    float warp = floor(q.x / 1.6);
    float tone = h1(warp * 1.37 + 0.5);
    float slub = vn(float2(warp * 3.1, q.y * 0.045));
    float ridge = sin((q.x * 0.85 + q.y) * TAU / 4.2);
    float r = sstep(-0.2, 0.9, ridge);
    float3 deep = float3(0.090, 0.145, 0.255);
    float3 mid = float3(0.150, 0.235, 0.385);
    float3 lit = float3(0.290, 0.380, 0.540);
    float3 c = mix(deep, mid, r);
    c = mix(c, lit, sstep(0.75, 1.0, ridge) * 0.55);
    // White weft peeking through at the ridge tops.
    c = mix(c, float3(0.62, 0.68, 0.76), sstep(0.92, 1.0, ridge) * (0.25 + 0.35 * slub));
    c *= 0.86 + 0.22 * tone;
    // Washes and soft creases.
    float wash = fbm(q * 0.006 + float2(3.0, 1.0));
    c = mix(c, c * 1.45 + float3(0.02, 0.03, 0.05), sstep(0.55, 0.85, wash) * 0.5);
    float crease = vn(float2(q.x * 0.02, q.y * 0.004) + 5.0);
    c *= 0.9 + 0.18 * crease;
    return c;
}

float stitch(float along, float across, float at) {
    float onLine = sstep(1.0, 0.25, abs(across - at));
    float dash = sstep(0.08, 0.16, fract(along / 6.5)) * sstep(0.78, 0.7, fract(along / 6.5));
    return onLine * dash;
}

}

/// size: screen. zip: yTop, slider y, yEnd, gapMax. open: full-open amount (0..1, eased), curl radius R.
[[ stitchable ]] half4 zipperUnlockDenim(float2 p, half4 color, float2 size, float4 zip, float2 open) {
    using namespace zipperUnlockShader;
    float cx = size.x * 0.5;
    // The opening's half-width here (gapAt in ZipperUnlockMotion.swift).
    float v = 0.0;
    if (p.y < zip.y && p.y > zip.x) v = 5.0 + zip.w * sm((zip.y - p.y) / 300.0) * sm((p.y - zip.x) / 64.0);
    float g = v + (size.x * 0.5 + 70.0 - v) * clamp(open.x, 0.0, 1.0);
    float side = p.x < cx ? -1.0 : 1.0;
    float edge = cx + side * g;
    // Distance from the edge into the cloth (> 0 on the cloth).
    float d = (p.x - edge) * side;
    if (d < -0.5) {
        // The page beneath, darkened by the halves on both sides.
        float dl = g - abs(p.x - cx);
        float shade = 0.42 * exp(-dl / 12.0) * sstep(0.0, 6.0, g);
        float depth = 0.18 * (1.0 - sstep(0.0, 40.0, g)) * step(0.5, g);
        float a = clamp(shade + depth, 0.0, 0.6) * (1.0 - open.x * 0.85);
        return half4(half3(float3(0.03, 0.04, 0.08) * a), half(a));
    }

    // The rolled edge: R grows with the opening; closed teeth lie flat.
    float R = open.y * sm(g / 34.0);
    float cloth;
    float facing = 1.0;
    if (R > 0.5 && d < R) {
        float th = acos(clamp(1.0 - d / R, -1.0, 1.0));
        cloth = R * th;
        facing = sin(th);
    } else {
        cloth = d + R * (1.5707963 - 1.0);
    }
    // Cloth coordinates move with the half: x measured from its edge, mirrored per side.
    float2 q = float2(side < 0.0 ? 400.0 - cloth : 900.0 + cloth, p.y);
    float3 c = denim(q);

    // Zipper tape along the edge: dark woven band with fine ribs.
    float tape = sstep(11.5, 10.5, cloth);
    float3 tapeC = float3(0.075, 0.090, 0.130) * (0.85 + 0.25 * step(0.5, fract(p.y / 1.4)));
    c = mix(c, tapeC, tape);
    // Two rows of gold stitching beside the tape, and one along the screen border.
    float3 gold = float3(0.86, 0.60, 0.28);
    float st = max(stitch(p.y, cloth, 15.0), stitch(p.y + 3.2, cloth, 19.0));
    // The border stitches are in cloth space too, so they leave with their half.
    float bx = cx - cloth;
    st = max(st, stitch(p.y, bx, 13.0));
    st = max(st, stitch(q.x, p.y, 14.0));
    st = max(st, stitch(q.x + 2.1, size.y - p.y, 14.0));
    c = mix(c, gold * (0.9 + 0.2 * h2(floor(p / 6.5))), st * 0.92);

    // Roll shading: dark where the cloth turns away, a soft sheen near the top of the roll.
    c *= 0.42 + 0.58 * facing;
    float fz = (facing - 0.72) / 0.12;
    float sheen = exp(-fz * fz) * step(0.5, R) * (d < R ? 1.0 : 0.0);
    c += float3(0.16, 0.2, 0.28) * sheen * (side < 0.0 ? 0.6 : 1.0);
    // Contact shadow along a closed seam, and the fold shadow cast on the cloth by the roll.
    c *= 1.0 - 0.35 * exp(-d / 3.0) * (1.0 - sm(g / 10.0));

    // Light from the top left, vignette, grain.
    float2 uv = p / size;
    c *= 1.08 - 0.22 * uv.y - 0.08 * uv.x;
    float2 vv = uv - 0.5;
    c *= 1.0 - 0.45 * sstep(0.3, 0.9, length(vv * float2(1.15, 0.9)));
    c += (h2(floor(p * 1.5)) - 0.5) * 0.035;

    float a = sstep(-0.5, 0.6, d);
    return half4(half3(c * a), half(a));
}
