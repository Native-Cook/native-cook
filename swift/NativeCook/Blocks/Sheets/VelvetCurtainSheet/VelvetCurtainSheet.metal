#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Velvet Curtain Sheet — the curtain, its pelmet and the page behind the sheet. A step-for-step
// port of velvetShader.ts (CURTAIN_SKSL and PAGE_SKSL); see there for the full description.
// SwiftUI colours are sRGB, so the P3 conversion is left out. GLSL's smoothstep is reimplemented
// (sstep) because the Skia version calls it with reversed edges, which Metal leaves undefined.
// The output is premultiplied, as SwiftUI expects.

namespace velvetCurtainSheetShader {

constant float TAU = 6.2831853;

float h1(float n) { return fract(sin(n * 12.9898) * 43758.5453); }
float h2(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float3 velvet(float lam, float sheen, float valley) {
    float3 deep = float3(0.200, 0.020, 0.050);
    float3 mid = float3(0.520, 0.050, 0.110);
    float3 lit = float3(0.760, 0.150, 0.210);
    float3 c = mix(deep, mid, sstep(0.05, 0.6, lam));
    c = mix(c, lit, sstep(0.6, 1.0, lam) * 0.7);
    c += float3(0.95, 0.42, 0.50) * sheen * 0.42;
    return c * (1.0 - 0.55 * valley);
}

float3 gold(float l) {
    float3 a = float3(0.36, 0.22, 0.06);
    float3 b = float3(0.80, 0.58, 0.22);
    float3 c = float3(0.98, 0.86, 0.52);
    return l < 0.5 ? mix(a, b, l * 2.0) : mix(b, c, l * 2.0 - 1.0);
}

// One half at local x (0 at its outer side), its edge at e: rgb and coverage.
float4 drape(float x, float y, float e, float o, float side, float hem) {
    if (x > e + 0.8) return float4(0.0);
    float s = clamp(x / max(e, 1.0), 0.0, 1.0);
    float k = 1.0 / (1.0 + 1.7 * o);
    float u = pow(max(s, 0.0005), k);
    float N = 9.0;
    float wob = 0.012 * sin(u * 23.0 + side * 1.7) + 0.008 * sin(u * 57.0 + 2.0);
    float ph = (u + wob) * N * TAU;
    float dudx = k * pow(max(s, 0.02), k - 1.0) / max(e, 1.0);
    float depth = 4.0 + 10.0 * o;
    float slope = cos(ph) * N * TAU * dudx * depth * side;
    float inv = 1.0 / sqrt(1.0 + slope * slope);
    // Light from the upper left, toward the viewer.
    float lam = clamp((0.42 * slope + 1.0) * inv / 1.08, 0.0, 1.0);
    float sheen = pow(1.0 - inv, 1.6);
    float valley = sstep(0.2, 1.0, -sin(ph)) * (0.35 + 0.65 * o);
    float3 c = velvet(lam, sheen, valley);
    // Pile: fine vertical fibres and grain.
    c *= 0.94 + 0.08 * h1(floor(u * 900.0)) + 0.04 * (h2(floor(float2(x, y) * 1.5)) - 0.5);
    // The leading edge: a turned hem, slightly darker.
    c *= 1.0 - 0.25 * sstep(5.0, 0.0, e - x);
    // Fringe at the hem: braid, then strands that gather with the cloth.
    float strand = floor(u * 150.0);
    float len = 12.0 + 6.0 * h1(strand + side * 31.0);
    float braidTop = hem - 22.0;
    float alpha = sstep(e + 0.8, e - 0.4, x);
    if (y > braidTop && y < braidTop + 5.0) {
        float tw = sin((u * 420.0 + (y - braidTop) * 0.9) * 1.0);
        c = gold(0.35 + 0.45 * (0.5 + 0.5 * tw) * lam);
    } else if (y >= braidTop + 5.0) {
        float f = fract(u * 150.0);
        float on = sstep(0.15, 0.3, f) * sstep(0.85, 0.7, f);
        float dl = y - (braidTop + 5.0);
        float tip = sstep(len, len - 3.0, dl);
        c = gold(0.25 + 0.6 * lam * (1.0 - dl / 24.0));
        alpha *= on * tip;
    }
    return float4(c, alpha);
}

}

/// size: sheet width, drawn height. curtain: top (under the pelmet), hem y, rail opening, hem opening.
[[ stitchable ]] half4 velvetCurtainSheetCurtain(float2 p, half4 color, float2 size, float4 curtain) {
    using namespace velvetCurtainSheetShader;
    float W = size.x;
    float top = curtain.x;
    float hem = curtain.y;
    // Pelmet: velvet swags with a scalloped edge, gold trim and tassels.
    float scW = W / 5.0;
    float sc = abs(sin(p.x / scW * 3.1415926));
    float edge = top - 6.0 + 10.0 * sc;
    if (p.y < edge + 3.0) {
        float yy = p.y / max(edge, 1.0);
        float swag = sin(yy * 3.1415926 * 2.2 + sc * 1.4);
        float lam = 0.45 + 0.35 * swag;
        float3 c = velvet(lam, pow(abs(swag), 6.0) * 0.4, 0.2 * (1.0 - sc));
        c *= 0.82 + 0.18 * sc;
        float trim = sstep(4.0, 1.5, edge - p.y) * sstep(-1.0, 0.5, edge - p.y);
        c = mix(c, gold(0.4 + 0.5 * sc), trim);
        float tass = sstep(-0.5, 0.5, edge + 3.0 - p.y);
        c *= 0.94 + 0.06 * h2(floor(p * 1.5));
        float a = p.y < edge ? 1.0 : tass * step(0.6, fract(p.x / 4.0)) * (sc > 0.98 ? 1.0 : 0.0);
        // The pelmet's own shadow line under the top edge.
        c *= 1.0 - 0.3 * sstep(8.0, 0.0, p.y);
        return half4(half3(c * a), half(a));
    }
    if (p.y < top || p.y > hem + 1.0) return half4(0.0h);

    float drop = clamp((p.y - top) / max(hem - top, 1.0), 0.0, 1.0);
    float o = mix(curtain.z, curtain.w, pow(drop, 1.5));
    float e = W * 0.5 * (1.0 - 0.84 * clamp(o, -0.05, 1.05));
    float side = p.x < W * 0.5 ? 1.0 : -1.0;
    float lx = side > 0.0 ? p.x : W - p.x;
    float4 cl = drape(lx, p.y, e, clamp(curtain.z, 0.0, 1.0), side, hem);

    // Light falls off down the drop and under the pelmet.
    float3 c = cl.rgb * (1.02 - 0.28 * drop) * (1.0 - 0.35 * sstep(18.0, 0.0, p.y - top));
    float a = cl.a;
    // The curtain's shadow on the seat map, just beside its leading edge.
    float gap = lx - e;
    float sh = gap > 0.0 ? 0.55 * exp(-gap / 16.0) * sstep(0.0, 0.05, curtain.z) : 0.0;
    float outA = a + sh * (1.0 - a);
    return half4(half3(c * a), half(outA));
}

[[ stitchable ]] half4 velvetCurtainSheetPage(float2 p, half4 color, float2 size) {
    using namespace velvetCurtainSheetShader;
    float2 uv = p / size;
    float3 a = float3(0.090, 0.050, 0.065);
    float3 b = float3(0.070, 0.038, 0.050);
    float3 c0 = float3(0.045, 0.024, 0.032);
    float3 c = mix(a, b, sstep(0.0, 0.5, uv.y));
    c = mix(c, c0, sstep(0.5, 1.0, uv.y));
    float2 v = uv - 0.5;
    c *= 1.0 - 0.4 * sstep(0.3, 0.9, length(v * float2(1.2, 0.9)));
    c += (h2(floor(p)) - 0.5) * 0.035;
    return half4(half3(c), 1.0h);
}
