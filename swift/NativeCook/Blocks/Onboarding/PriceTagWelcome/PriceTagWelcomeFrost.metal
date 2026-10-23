#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Price Tag Welcome - the frost, ported from frost-shader.ts (two SkSL effects). Every pixel is a pure
// function of the uniforms (seeded hashes, no state), so autoPlay frames repeat. Lengths are points;
// `k` = screen width / 402 scales every feature size. Colours are sRGB (SwiftUI's layer space), so
// the Display P3 conversion the Skia build needs is not applied here.

constant float3 priceTagWelcomeIceHi = float3(236.0, 245.0, 252.0) / 255.0;
constant float3 priceTagWelcomeIceMid = float3(186.0, 214.0, 238.0) / 255.0;
constant float3 priceTagWelcomeIceDeep = float3(74.0, 122.0, 168.0) / 255.0;
constant float3 priceTagWelcomeCoolTint = float3(200.0, 224.0, 255.0) / 255.0;

struct PriceTagWelcomeFrostParams {
    float2 org;
    float grow;
    float span;
    float melt;
    float clock;
    float k;
    float fade;
    float seed;
};

static float priceTagWelcomeH1(float2 q, float seed) {
    return fract(sin(dot(q, float2(127.1, 311.7)) + seed) * 43758.5453);
}

static float2 priceTagWelcomeH2(float2 q, float seed) {
    return fract(sin(float2(dot(q, float2(127.1, 311.7)), dot(q, float2(269.5, 183.3))) + seed) * 43758.5453);
}

static float priceTagWelcomeSat(float v) { return clamp(v, 0.0, 1.0); }

static float priceTagWelcomeNoise(float2 q, float seed) {
    float2 i = floor(q);
    float2 f = fract(q);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = priceTagWelcomeH1(i, seed);
    float b = priceTagWelcomeH1(i + float2(1.0, 0.0), seed);
    float c = priceTagWelcomeH1(i + float2(0.0, 1.0), seed);
    float d = priceTagWelcomeH1(i + float2(1.0, 1.0), seed);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// One ice cell: float4(ice, border, sparkle, fresh); needles and wet through the references.
static float4 priceTagWelcomeIceCell(float2 p, float cs, PriceTagWelcomeFrostParams u,
                                     thread float &needles, thread float &wet) {
    float2 g = p / cs;
    float2 ip = floor(g);
    float2 fp = fract(g);
    float d1 = 8.0;
    float d2 = 8.0;
    float2 id = float2(0.0);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            float2 o = float2(float(i), float(j));
            float2 r = o + priceTagWelcomeH2(ip + o, u.seed) * 0.85 + 0.075 - fp;
            float dd = dot(r, r);
            if (dd < d1) { d2 = d1; d1 = dd; id = ip + o; }
            else if (dd < d2) { d2 = dd; }
        }
    }
    float edge = sqrt(d2) - sqrt(d1);
    float2 centre = (id + priceTagWelcomeH2(id, u.seed) * 0.85 + 0.075) * cs;
    float jit = (priceTagWelcomeH1(id + 3.7, u.seed) - 0.5) * 10.0 * u.k;
    float reach = length(centre - u.org) + jit;

    // Growth: a whole cell crystallises as the front passes it (a 2.5 pt ramp).
    float ice = priceTagWelcomeSat((u.grow - reach) / (2.5 * u.k));
    float fresh = ice * exp(-max(u.grow - reach, 0.0) / (9.0 * u.k));
    // Thaw: a soft, ragged waterline recedes from the outside in; the tag goes last.
    float wl = length(p - u.org) + (priceTagWelcomeNoise(p / (6.0 * u.k), u.seed) - 0.5) * 18.0 * u.k;
    float order = 0.04 + 0.70 * (1.0 - priceTagWelcomeSat(wl / u.span));
    float gone = smoothstep(order, order + 0.10, u.melt);
    wet = gone * (1.0 - gone) * 4.0 * ice;
    ice *= 1.0 - gone;

    // Needles: fine parallel striations in two directions 60 degrees apart, oriented per cell.
    float a = priceTagWelcomeH1(id + 7.3, u.seed) * 6.2832;
    float2 lp = (p - centre) / u.k;
    float n1 = abs(sin(dot(lp, float2(cos(a), sin(a))) * 1.9));
    float n2 = abs(sin(dot(lp, float2(cos(a + 1.047), sin(a + 1.047))) * 1.3));
    needles = smoothstep(0.9, 1.0, 1.0 - n1) + 0.55 * smoothstep(0.93, 1.0, 1.0 - n2);

    float border = 1.0 - smoothstep(0.0, 0.07, edge);

    // A quarter of the cells carry a facet that twinkles: a small four-point star.
    float hs = priceTagWelcomeH1(id + 1.3, u.seed);
    float tw = pow(0.5 + 0.5 * sin(u.clock * (2.2 + 2.6 * hs) + hs * 41.0), 18.0);
    float2 sp = (p - centre) / u.k;
    float star = exp(-dot(sp, sp) / 0.7)
        + 0.55 * exp(-abs(sp.x) / 0.32 - abs(sp.y) / 3.2)
        + 0.55 * exp(-abs(sp.y) / 0.32 - abs(sp.x) / 3.2);
    float sparkle = hs > 0.74 ? star * tw : 0.0;
    return float4(ice, border, sparkle, fresh);
}

// Drawn over a product sticker (the layer is the sticker's render, padded by the rime margin).
[[ stitchable ]] half4 priceTagWelcomeStickerFrost(
    float2 p, SwiftUI::Layer layer,
    float2 org, float grow, float span, float melt, float clock, float k, float fade, float seed,
    float2 box, float margin, float cell
) {
    if (fade <= 0.0) return half4(0.0);
    PriceTagWelcomeFrostParams u = { org, grow, span, melt, clock, k, fade, seed };
    float4 src = float4(layer.sample(p));
    float needles = 0.0;
    float wet = 0.0;
    float4 c = priceTagWelcomeIceCell(p, cell * k, u, needles, wet);
    float ice = c.x;
    float4 outc = float4(0.0);

    if (src.a > 0.003) {
        float3 rgb = src.rgb / src.a;
        float lum = dot(rgb, float3(0.2126, 0.7152, 0.0722));
        float3 cool = lum * priceTagWelcomeCoolTint + float3(0.02, 0.05, 0.09);
        float3 base = mix(rgb, cool, 0.86);
        float veil = 0.40 + 0.20 * needles;
        float3 col = mix(base, priceTagWelcomeIceHi, veil);
        col = mix(col, priceTagWelcomeIceDeep, 0.10 * c.y);
        col = mix(col, priceTagWelcomeIceMid, 0.10 * (1.0 - c.y) * needles);
        col = mix(col, float3(1.0), 0.65 * c.w);
        col += float3(1.0) * c.z * 0.9;
        float a = src.a * priceTagWelcomeSat(ice + c.z * ice);
        // Wet sheen while a cell melts: the product shows through a film of water.
        float sheen = wet * (0.35 + 0.65 * smoothstep(0.2, 1.0, needles));
        float wa = src.a * wet * 0.45;
        float3 wcol = mix(rgb, priceTagWelcomeIceMid, 0.35) + 0.25 * sheen;
        outc = float4(col * a, a);
        outc = outc + float4(wcol * wa, wa) * (1.0 - outc.a);

        // Drops run down the product while it thaws.
        float lift = 0.0;
        float3 dcol = float3(0.0);
        for (int i = 0; i < 6; i++) {
            float fi = float(i);
            float t0 = 0.08 + 0.17 * priceTagWelcomeH1(float2(fi, 9.1), seed);
            float life = melt - t0;
            if (life > 0.0 && life < 0.75) {
                float dx = margin + (0.2 + 0.6 * priceTagWelcomeH1(float2(fi, 5.3), seed)) * box.x;
                float y0 = margin + (0.18 + 0.32 * priceTagWelcomeH1(float2(fi, 2.9), seed)) * box.y;
                float yy = y0 + pow(life / 0.75, 1.7) * box.y * 0.62;
                float rd = (2.2 + 1.2 * priceTagWelcomeH1(float2(fi, 4.4), seed)) * k;
                float vis = smoothstep(0.0, 0.08, life) * (1.0 - smoothstep(0.55, 0.75, life));
                float2 q = (p - float2(dx, yy)) / rd;
                float body = 1.0 - smoothstep(0.85, 1.0, length(q * float2(1.0, 0.88)));
                float trail = (1.0 - smoothstep(0.18, 0.32, abs(p.x - dx) / rd / 2.0))
                    * step(y0, p.y) * step(p.y, yy) * smoothstep(y0, yy, p.y) * 0.5;
                float rim = body * smoothstep(0.35, 1.0, length(q));
                float spec = exp(-dot(q - float2(-0.32, -0.38), q - float2(-0.32, -0.38)) / 0.05);
                float m = max(body, trail) * vis * step(0.5, src.a);
                lift = max(lift, m * (0.30 + 0.45 * rim));
                dcol += float3(1.0) * spec * vis * body;
            }
        }
        outc = outc + float4(priceTagWelcomeIceDeep * lift, lift) * (1.0 - outc.a);
        outc.rgb += dcol * 0.95;
        outc.a = max(outc.a, priceTagWelcomeSat(length(dcol)));
    }

    // Rime halo: condensation on the page around a frozen sticker.
    float halo = 0.0;
    float rr = 9.0 * k;
    for (int i = 0; i < 8; i++) {
        float an = float(i) * 0.7854;
        halo += float(layer.sample(p + float2(cos(an), sin(an)) * rr).a);
        halo += float(layer.sample(p + float2(cos(an + 0.39), sin(an + 0.39)) * rr * 2.0).a) * 0.6;
    }
    halo = priceTagWelcomeSat(halo / 9.0) * (1.0 - src.a);
    float coverage = priceTagWelcomeSat(grow / span) * (1.0 - smoothstep(0.0, 0.7, melt));
    float grain = priceTagWelcomeNoise(p / (5.0 * k), seed) * 0.6 + priceTagWelcomeNoise(p / (2.2 * k), seed) * 0.4;
    float ha = halo * coverage * smoothstep(0.25, 0.85, grain) * 0.55;
    outc = outc + float4(priceTagWelcomeIceHi * ha, ha) * (1.0 - outc.a);
    return half4(outc * fade);
}

// The same ice over a price tag (a pill), milky enough that the price ghosts through.
[[ stitchable ]] half4 priceTagWelcomeTagFrost(
    float2 p, SwiftUI::Layer layer,
    float2 org, float grow, float span, float melt, float clock, float k, float fade, float seed,
    float2 size, float cell, float tagAlpha
) {
    if (fade <= 0.0) return half4(0.0);
    PriceTagWelcomeFrostParams u = { org, grow, span, melt, clock, k, fade, seed };
    float r = size.y * 0.5;
    float2 q = abs(p - size * 0.5) - (size * 0.5 - r);
    float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    float inside = 1.0 - smoothstep(-0.6, 0.6, sd);
    if (inside <= 0.0) return half4(0.0);
    float needles = 0.0;
    float wet = 0.0;
    float4 c = priceTagWelcomeIceCell(p, cell * k, u, needles, wet);
    float3 col = mix(priceTagWelcomeIceHi, priceTagWelcomeIceMid, 0.30 + 0.25 * needles);
    col = mix(col, priceTagWelcomeIceDeep, 0.07 * c.y);
    // Thicker ice towards the rim of the pill.
    float rimIce = 1.0 - smoothstep(-2.4 * k, -0.2 * k, sd);
    col = mix(col, priceTagWelcomeIceMid, 0.35 * (1.0 - rimIce));
    col = mix(col, float3(1.0), 0.65 * c.w);
    col += float3(1.0) * c.z * 0.9;
    float a = inside * priceTagWelcomeSat(c.x * tagAlpha + c.z * c.x);
    float wa = inside * wet * 0.35;
    float4 outc = float4(col * a, a);
    outc = outc + float4(mix(priceTagWelcomeIceHi, float3(1.0), 0.5) * wa, wa) * (1.0 - outc.a);
    return half4(outc * fade);
}
