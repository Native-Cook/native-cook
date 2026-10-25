#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The whole hero in one shader: the bronze plaque, the thin paper laid over it, the wax the crayon
// leaves on the paper, and the sheet peeling away. The layer carries two fields: red is the
// relief's height (white = raised), green the rubbed field (how much the crayon has worked a spot).
//
// Frottage: wax only reaches the paper where something pushes it up. The deposit is rub times the
// relief's height above a threshold (lowered a little by the paper's tooth), plus a faint film on
// the flat paper, streaked along the stroke. Before the crayon passes, the relief only shows as a
// faint emboss. The peel is a flat fold of the top-right corner travelling toward the bottom-left.
// Outside the plaque the output is its soft shadow (premultiplied). Colours are sRGB.

static float crayonRubbingCardHash(float2 p) {
    float3 q = fract(float3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

static float crayonRubbingCardNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(crayonRubbingCardHash(i), crayonRubbingCardHash(i + float2(1.0, 0.0)), u.x),
               mix(crayonRubbingCardHash(i + float2(0.0, 1.0)), crayonRubbingCardHash(i + float2(1.0, 1.0)), u.x), u.y);
}

static float crayonRubbingCardFbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * crayonRubbingCardNoise(p);
        p = p * 2.07 + float2(13.1, 7.7);
        a *= 0.5;
    }
    return v;
}

static float crayonRubbingCardBox(float2 p, float4 r) {
    float2 c = r.xy + r.zw * 0.5;
    float2 d = abs(p - c) - r.zw * 0.5;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}

static bool crayonRubbingCardInside(float2 p, float4 r) {
    return p.x >= r.x && p.y >= r.y && p.x <= r.x + r.z && p.y <= r.y + r.w;
}

static float crayonRubbingCardHeight(SwiftUI::Layer layer, float2 p) {
    return layer.sample(p).r;
}

// The paper at canvas point p: its fibres, the relief's faint emboss and, if rubbed, the wax.
static float3 crayonRubbingCardPaper(SwiftUI::Layer layer, float2 p, float rubAmount, float3 wax, float grainAngle) {
    float h = crayonRubbingCardHeight(layer, p);
    float hx = crayonRubbingCardHeight(layer, p + float2(1.2, 0.0)) - crayonRubbingCardHeight(layer, p - float2(1.2, 0.0));
    float hy = crayonRubbingCardHeight(layer, p + float2(0.0, 1.2)) - crayonRubbingCardHeight(layer, p - float2(0.0, 1.2));
    float3 n = normalize(float3(-hx * 2.2, -hy * 2.2, 1.0));
    float lit = dot(n, normalize(float3(-0.5, -0.7, 0.8)));
    float fibre = crayonRubbingCardFbm(float2(p.x * 0.3, p.y * 0.75));
    float3 base = float3(0.937, 0.910, 0.858) * (0.965 + 0.05 * fibre);
    float bend = clamp(length(float2(hx, hy)) * 3.2, 0.0, 1.0);
    base *= (0.9 + 0.15 * lit) * (1.0 - 0.13 * bend);
    float tooth = crayonRubbingCardNoise(p * 1.9) * 0.6 + crayonRubbingCardNoise(p * 4.3) * 0.4;
    float ca = cos(grainAngle);
    float sa = sin(grainAngle);
    float2 q = float2(ca * p.x + sa * p.y, -sa * p.x + ca * p.y);
    float streak = crayonRubbingCardNoise(float2(q.x * 0.07, q.y * 1.3)) * 0.6 + crayonRubbingCardNoise(float2(q.x * 0.15, q.y * 2.9)) * 0.4;
    float raised = smoothstep(0.26, 0.58, h + (tooth - 0.5) * 0.34 + rubAmount * 0.1);
    float deposit = rubAmount * (0.14 * streak + 0.86 * raised);
    deposit *= 0.72 + 0.28 * smoothstep(0.2, 0.55, streak);
    deposit *= 0.78 + 0.22 * smoothstep(0.15, 0.45, tooth);
    deposit = clamp(deposit * 1.12, 0.0, 1.0);
    float3 waxCol = wax * (0.86 + 0.22 * lit) + 0.05 * deposit * deposit;
    return mix(base, waxCol, deposit * 0.94);
}

static float3 crayonRubbingCardBronze(float2 p, float4 plaque) {
    float d = -crayonRubbingCardBox(p, plaque);
    float bevel = 1.0 - smoothstep(0.0, 7.0, d);
    float patina = crayonRubbingCardFbm(p * 0.05);
    float speck = smoothstep(0.62, 0.8, crayonRubbingCardFbm(p * 0.21 + 4.0));
    float3 c = mix(float3(0.42, 0.27, 0.13), float3(0.64, 0.45, 0.24), smoothstep(0.2, 0.8, patina));
    c = mix(c, float3(0.24, 0.42, 0.36), speck * 0.55);
    float2 g = float2(p.x - (plaque.x + plaque.z * 0.5), p.y - (plaque.y + plaque.w * 0.5));
    float side = -(g.x / plaque.z + g.y / plaque.w);
    c *= 1.0 + bevel * side * 0.9;
    c += float3(0.35, 0.28, 0.16) * bevel * max(side, 0.0) * 0.6;
    return c;
}

// Everything but the flap: the plaque, the old sheet or the fresh one, the shadows. Premultiplied.
static half4 crayonRubbingCardSheet(SwiftUI::Layer layer, float2 p, float4 plaque, float4 pa, float s, float crease,
                                    float f, float peel, float3 wax, float grainAngle) {
    if (crayonRubbingCardInside(p, plaque)) {
        float3 col;
        if (crayonRubbingCardInside(p, pa)) {
            if (s >= crease) {
                col = crayonRubbingCardPaper(layer, p, layer.sample(p).g, wax, grainAngle);
                float edge = s - f;
                if (peel > 0.0 && edge > 0.0 && edge < 14.0) col *= 1.0 - 0.22 * (1.0 - edge / 14.0) * (1.0 - smoothstep(0.78, 1.0, peel));
            } else {
                col = crayonRubbingCardPaper(layer, p, 0.0, wax, grainAngle);
                float lift = crease - s;
                col *= 1.0 - 0.18 * exp(-lift / 18.0) * (1.0 - smoothstep(0.78, 1.0, peel));
            }
            float e = -crayonRubbingCardBox(p, pa);
            col *= 0.9 + 0.1 * smoothstep(0.0, 1.5, e);
        } else {
            col = crayonRubbingCardBronze(p, plaque);
            float e = crayonRubbingCardBox(p - float2(1.0, 2.5), pa);
            col *= 1.0 - 0.35 * (1.0 - smoothstep(0.0, 6.0, e));
        }
        return half4(half3(col), 1.0h);
    }
    float d = crayonRubbingCardBox(p - float2(0.0, 7.0), plaque);
    float a = 0.5 * (1.0 - smoothstep(0.0, 22.0, d));
    return half4(0.0h, 0.0h, 0.0h, half(a));
}

[[ stitchable ]] half4 crayonRubbingCardRub(float2 p, SwiftUI::Layer layer, float4 plaque, float4 paper, float peel,
                                            float3 wax, float grainAngle) {
    float4 pa = paper;
    float2 corner = float2(pa.x + pa.z, pa.y);
    float2 v = normalize(float2(-pa.z, pa.w));
    float L = length(pa.zw);
    float f = peel * 2.05 * L;
    float crease = f * 0.5;
    float s = dot(p - corner, v);
    half4 under = crayonRubbingCardSheet(layer, p, plaque, pa, s, crease, f, peel, wax, grainAngle);

    // The flap: the old sheet folded over, seen from its back, fading as it leaves.
    float fadeOut = 1.0 - smoothstep(0.78, 1.0, peel);
    if (peel > 0.0 && fadeOut > 0.0 && s >= crease && s <= f) {
        float2 src = p - 2.0 * (s - crease) * v;
        if (crayonRubbingCardInside(src, pa)) {
            float r = layer.sample(src).g;
            float3 back = float3(0.905, 0.878, 0.826) * (0.96 + 0.04 * crayonRubbingCardFbm(src * 0.5));
            back = mix(back, wax, r * 0.12);
            float k = clamp((s - crease) / 26.0, 0.0, 1.0);
            back *= 0.88 + 0.16 * smoothstep(0.0, 1.0, k) - 0.06 * k * k;
            back = mix(back * 0.82, back, smoothstep(0.0, 3.0, s - crease));
            return half4(half3(back) * half(fadeOut), half(fadeOut)) + under * half(1.0 - fadeOut);
        }
    }
    return under;
}
