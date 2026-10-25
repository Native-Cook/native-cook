#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// A step-for-step port of the React Native SkSL (waxSealShader.ts).
// waxSealInviteWax draws the seal (a layer effect over the monogram's mask image, green = blurred
// height; the pieces reuse it under a clip): a puddle of glossy oxblood wax with an irregular edge
// and a rounded lip, the stamp's well pressed into its middle with the raised monogram, lit by a
// key light with a moving gleam. waxSealInvitePaper colours the envelope's paper and liner; it
// keeps the shape's own coverage, so its edges stay antialiased.
//
// SwiftUI's shader colours are sRGB already, so the SkSL's P3 conversion is left out. GLSL's
// smoothstep is reimplemented so it behaves the same for any edges.

namespace waxSealInviteShader {

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

static float edgeR(float a, float R) {
    return R * (1.0 + 0.05 * sin(3.0 * a + 1.3) + 0.035 * sin(5.0 * a + 0.4) + 0.02 * sin(9.0 * a + 2.1));
}

// The wax's height (in units of 6 pt) at a point relative to the seal's centre.
static float waxH(SwiftUI::Layer mask, float2 rel, float R, float pad) {
    float r = length(rel);
    float re = edgeR(atan2(rel.y, rel.x), R);
    float x = r / re;
    if (x >= 1.0) return 0.0;
    float rim = x > 0.8 ? sqrt(max(0.0, 1.0 - ((x - 0.8) / 0.2) * ((x - 0.8) / 0.2))) : 1.0;
    float well = mix(0.6, 1.0, sstep(0.66, 0.8, x));
    float mono = float(mask.sample(rel + float2(R + pad)).g);
    return min(rim, well) + mono * 0.3 * (1.0 - sstep(0.62, 0.7, x));
}

} // namespace waxSealInviteShader

using namespace waxSealInviteShader;

// `p` is in the mask's own points (its top-left = 0); the seal's centre is at R + pad.
[[ stitchable ]] half4 waxSealInviteWax(float2 p, SwiftUI::Layer mask, float R, float pad, float gleam) {
    float2 rel = p - float2(R + pad);
    float r = length(rel);
    float re = edgeR(atan2(rel.y, rel.x), R);
    float cover = 1.0 - sstep(re - 0.9, re + 0.4, r);
    if (cover < 0.001) return half4(0.0h);

    float e = 0.75;
    float hx = waxH(mask, rel + float2(e, 0.0), R, pad) - waxH(mask, rel - float2(e, 0.0), R, pad);
    float hy = waxH(mask, rel + float2(0.0, e), R, pad) - waxH(mask, rel - float2(0.0, e), R, pad);
    float3 n = normalize(float3(-hx * 6.0 / (2.0 * e), -hy * 6.0 / (2.0 * e), 1.0));

    float3 L = normalize(float3(-0.45, -0.65, 0.62));
    float3 V = float3(0.0, 0.0, 1.0);
    float ndl = max(dot(n, L), 0.0);
    // Oxblood: dark in the well and the shadows, brighter and warmer where the wax is thin.
    float3 deep = float3(0.27, 0.035, 0.05);
    float3 base = float3(0.56, 0.1, 0.13);
    float3 warm = float3(0.78, 0.22, 0.2);
    float x = r / re;
    float3 col = mix(deep, base, 0.35 + 0.65 * ndl);
    col = mix(col, warm, sstep(0.86, 1.0, x) * 0.35 * ndl);
    // React Native samples this noise in the seal's own coordinates (centre = 0).
    col *= 0.86 + 0.14 * vnoise(rel * 0.5);
    // Gloss: a tight highlight and a broad sheen.
    float3 H = normalize(L + V);
    float spec = pow(max(dot(n, H), 0.0), 70.0) * 0.9 + pow(max(dot(n, H), 0.0), 12.0) * 0.12;
    col += float3(1.0, 0.86, 0.82) * spec;
    // The gleam: a soft band of light that slides across the seal once.
    float along = rel.x / R * 0.8 + rel.y / R * 0.45 + n.x * 0.35;
    float gd = (along - gleam) / 0.16;
    col += float3(1.0, 0.8, 0.76) * exp(-gd * gd) * 0.35 * (0.4 + 0.6 * n.z);
    // A thin dark line where the wax meets the paper.
    col *= 1.0 - sstep(0.97, 1.0, x) * 0.35;
    col = clamp(col, 0.0, 1.0);
    return half4(half3(col * cover), half(cover));
}

[[ stitchable ]] half4 waxSealInvitePaper(float2 p, half4 color, float3 tint, float liner) {
    float3 col = tint;
    if (liner > 0.5) {
        // A deep red liner with a fine gold lattice.
        float2 d = float2(p.x + p.y, p.x - p.y) * 0.7071;
        float2 g = abs(fract(d / 14.0) - 0.5);
        float line = 1.0 - sstep(0.0, 0.05, min(g.x, g.y));
        col = mix(col, float3(0.78, 0.6, 0.34), line * 0.32);
        col *= 0.94 + 0.08 * vnoise(p * 0.08);
    } else {
        // Laid paper: faint lines across, soft cloudiness, fibres and grain.
        float laid = 0.5 + 0.5 * sin(p.y * 1.6);
        col *= 0.985 + laid * 0.008;
        col *= 0.975 + 0.04 * vnoise(p * 0.04);
        col -= sstep(0.82, 0.95, vnoise(float2(p.x * 0.05, p.y * 0.8) + 3.0)) * 0.02;
    }
    col += (hash21(floor(p * 2.0)) - 0.5) * 0.02;
    col = clamp(col, 0.0, 1.0);
    return half4(half3(col), 1.0h) * color.a;
}
