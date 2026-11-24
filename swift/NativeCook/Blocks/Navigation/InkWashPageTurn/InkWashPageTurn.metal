// Ink Wash Page Turn — the bleed, one fragment shader over the three baked pages.
//
// The front is a distance field in points:
//   f(p) = |p − origin| + (blot − ½)·150 + (fibre − ½)·26 + (fine − ½)·5
// Its radius runs from −100 to reach + 130 with progress, and s = radius − f is how far behind the
// front a point is: the new page fades in over 22 pt, with a dark lip on the front, pooled ink behind
// it, a soak ahead of it, wet paper and a thin sheen. Every term is scaled by `ends`, 0 at progress 0
// and 1, so a finished turn is exactly the new page and the loop swaps pages without a mark.

#include <metal_stdlib>
using namespace metal;

struct InkWashPageTurnU {
    float4 view;    // width, height (pt), pixels per pt, reach (pt)
    float4 turn;    // page shown, page arriving, progress, -
    float4 origin;  // x, y (pt), -, -
};

struct InkWashPageTurnVaryings {
    float4 position [[position]];
};

vertex InkWashPageTurnVaryings inkWashPageTurnVertex(uint i [[vertex_id]]) {
    float2 p = float2(float((i << 1u) & 2u), float(i & 2u));
    InkWashPageTurnVaryings out;
    out.position = float4(p * 2.0 - 1.0, 0.0, 1.0);
    return out;
}

static float iwpHash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

static float iwpNoise(float2 p) {
    float2 i = floor(p);
    float2 u = fract(p);
    u = u * u * (3.0 - 2.0 * u);
    return mix(mix(iwpHash(i), iwpHash(i + float2(1.0, 0.0)), u.x), mix(iwpHash(i + float2(0.0, 1.0)), iwpHash(i + float2(1.0, 1.0)), u.x), u.y);
}

// 3 octaves, each turned 37° and doubled
static float iwpFbm(float2 p) {
    float sum = 0.5 * iwpNoise(p);
    p = float2(0.8 * p.x - 0.6 * p.y, 0.6 * p.x + 0.8 * p.y) * 2.03;
    sum += 0.25 * iwpNoise(p);
    p = float2(0.8 * p.x - 0.6 * p.y, 0.6 * p.x + 0.8 * p.y) * 2.03;
    sum += 0.125 * iwpNoise(p);
    return sum / 0.875;
}

static float iwpFront(float2 p, float2 origin) {
    float2 q = p / 90.0;
    float2 w = float2(iwpFbm(q + float2(3.1, 0.0)), iwpFbm(q + float2(7.7, 1.3)));
    float blot = iwpFbm(q + 1.6 * w);
    float2 a = float2(dot(p, float2(0.966, 0.259)), dot(p, float2(-0.259, 0.966)));
    float2 b = float2(dot(p, float2(0.940, -0.342)), dot(p, float2(0.342, 0.940)));
    float fibre = 0.6 * iwpNoise(a * float2(0.012, 0.17)) + 0.4 * iwpNoise(b * float2(0.016, 0.21));
    float fine = iwpNoise(p * 0.9);
    return length(p - origin) + (blot - 0.5) * 150.0 + (fibre - 0.5) * 26.0 + (fine - 0.5) * 5.0;
}

static float3 iwpPage(float i, float2 p, float2 res, texture2d<float> a, texture2d<float> b, texture2d<float> c, sampler s) {
    float2 uv = clamp(p, float2(0.5), res - float2(0.5)) / res;
    if (i < 0.5) { return a.sample(s, uv, level(0.0)).rgb; }
    if (i < 1.5) { return b.sample(s, uv, level(0.0)).rgb; }
    return c.sample(s, uv, level(0.0)).rgb;
}

fragment float4 inkWashPageTurnBleed(InkWashPageTurnVaryings in [[stage_in]],
                                     constant InkWashPageTurnU &U [[buffer(0)]],
                                     texture2d<float> page0 [[texture(0)]],
                                     texture2d<float> page1 [[texture(1)]],
                                     texture2d<float> page2 [[texture(2)]],
                                     sampler samp [[sampler(0)]]) {
    float2 res = U.view.xy;
    float2 p = in.position.xy / U.view.z;
    float progress = U.turn.z;
    // at rest the picture is one page, and the noise need not run
    if (progress <= 0.0) { return float4(iwpPage(U.turn.x, p, res, page0, page1, page2, samp), 1.0); }
    if (progress >= 1.0) { return float4(iwpPage(U.turn.y, p, res, page0, page1, page2, samp), 1.0); }

    float ends = smoothstep(0.0, 0.03, progress) * (1.0 - smoothstep(0.97, 1.0, progress));
    float radius = mix(-100.0, U.view.w + 130.0, progress);
    float s = radius - iwpFront(p, U.origin.xy);
    float behind = step(0.0, s);

    // wet paper swells: what is printed on it wobbles near the front
    float near = smoothstep(-27.0, 0.0, s) * (1.0 - smoothstep(0.0, 60.0, s)) * ends;
    float2 wob = (float2(iwpNoise(p * 0.11 + float2(5.0, 0.0)), iwpNoise(p * 0.11 + float2(0.0, 9.0))) - 0.5) * 9.0 * near;
    float3 old = iwpPage(U.turn.x, p + wob, res, page0, page1, page2, samp);
    float3 next = iwpPage(U.turn.y, p + wob * 0.6, res, page0, page1, page2, samp);
    float3 col = mix(old, next, smoothstep(0.0, 22.0, s));

    // the paper's tooth, and the wet paper behind the front
    float tooth = 0.5 * iwpNoise(p * 1.7) + 0.5 * iwpNoise(float2(p.x * 0.09, p.y * 1.3));
    float wet = behind * exp(-max(s, 0.0) / 120.0) * ends;
    col *= 1.0 - 0.07 * wet * (0.8 + 0.4 * tooth);

    // the ink: a dark lip, pooled pigment behind it, a soak ahead of it
    float lip = exp(-(s * s) / (4.5 * 4.5));
    float pool = behind * exp(-max(s, 0.0) / 36.0) * (0.8 + 0.4 * tooth);
    float soak = (1.0 - behind) * exp(min(s, 0.0) / 9.0);
    float density = (0.8 * lip + 0.8 * pool + 0.5 * soak) * ends;
    col = mix(col, float3(0.086, 0.094, 0.157), 1.0 - exp(-density * 2.2));

    // the wet sheen just behind the lip
    float band = (s - 7.0) / 4.0;
    col += float3(0.92, 0.96, 1.0) * 0.1 * exp(-band * band) * (0.4 + 0.6 * iwpNoise(p * 0.25)) * ends;
    return float4(clamp(col, 0.0, 1.0), 1.0);
}
