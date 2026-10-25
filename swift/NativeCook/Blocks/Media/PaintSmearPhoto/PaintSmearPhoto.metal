#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The photo as wet oil paint, in one pass over the photo card (the layer this effect is applied
// to). A step-for-step port of the React Native SkSL (paintSmearShader.ts); see there for the full
// description. Colours are sRGB, so the SkSL's P3 conversions are left out.
//
// The smear is a displacement field: each trail point (x, y, and the drag it carries, dx, dy in
// pt) adds its drag weighted by a Gaussian of the brush's radius; a pixel shows the paint from
// upstream (p − D), averaged with more taps along D so the colours pull into streaks. Bristle lines
// run along the stroke, and the moved paint's ridges are lit from the upper left (impasto).
// `flow` scales the whole field (1 → 0 lets the paint flow back). Rounded corners are cut here.

namespace paintSmearPhotoShader {

struct Trail {
    float4 p[24];
    int n;
    float sigma;
    float flow;
};

static inline float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

static inline float hash1(float x) {
    return fract(sin(x * 12.9898) * 43758.5453);
}

static inline float noise1(float x) {
    float i = floor(x);
    float f = fract(x);
    float u = f * f * (3.0 - 2.0 * f);
    return mix(hash1(i), hash1(i + 1.0), u);
}

static float2 field(float2 p, thread const Trail &tr) {
    float2 d = float2(0.0);
    float k = -0.5 / (tr.sigma * tr.sigma);
    for (int i = 0; i < tr.n; i++) {
        float4 t = tr.p[i];
        if (abs(t.z) + abs(t.w) > 0.01) {
            float2 q = p - t.xy;
            d += t.zw * exp(dot(q, q) * k);
        }
    }
    d *= tr.flow;
    float len = length(d);
    // Long drags saturate (toward 130 pt), as paint can only be pulled so far.
    return len > 0.001 ? d / len * (130.0 * len / (130.0 + len)) : float2(0.0);
}

static float bristles(float2 p, float2 d) {
    float len = length(d);
    if (len < 0.01) return 0.5;
    float2 across = float2(-d.y, d.x) / len;
    float x = dot(p, across);
    return noise1(x * 0.16) * 0.65 + noise1(x * 0.47 + 11.0) * 0.35;
}

static inline float3 paint(SwiftUI::Layer photo, float2 q, float2 size) {
    half4 c = photo.sample(clamp(q, float2(0.5), size - 0.5));
    return c.a > 0.001h ? float3(c.rgb / c.a) : float3(0.0);
}

static inline float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

} // namespace paintSmearPhotoShader

using namespace paintSmearPhotoShader;

[[ stitchable ]] half4 paintSmearPhoto(
    float2 p, SwiftUI::Layer photo,
    float2 size, device const float *trail, int trailCount, float sigma, float dry, float flow, float radius
) {
    float sd = sdRoundRect(p - size * 0.5, size * 0.5, radius);
    float alpha = clamp(0.5 - sd, 0.0, 1.0);
    if (alpha <= 0.0) return half4(0.0h);

    Trail tr;
    tr.n = min(trailCount / 4, 24);
    for (int i = 0; i < tr.n; i++) {
        tr.p[i] = float4(trail[i * 4], trail[i * 4 + 1], trail[i * 4 + 2], trail[i * 4 + 3]);
    }
    tr.sigma = sigma;
    tr.flow = flow;

    float2 d = field(p, tr);
    float len = length(d);
    float3 col;
    if (len < 0.05) {
        col = paint(photo, p, size);
    } else {
        float b = bristles(p, d);
        float streak = mix(0.25, 0.7, dry);
        float2 dd = d * mix(1.0, 0.72, dry) * (1.0 - streak * 0.5 + streak * b);
        col = paint(photo, p - dd, size) * 0.4 + paint(photo, p - dd * 0.6, size) * 0.25
            + paint(photo, p - dd * 1.3, size) * 0.2 + paint(photo, p - dd * 0.25, size) * 0.15;
        // Impasto: height from the drag and the bristles; forward differences for the normal.
        float2 dx = field(p + float2(1.5, 0.0), tr);
        float2 dy = field(p + float2(0.0, 1.5), tr);
        float h0 = len * (0.75 + 0.35 * b);
        float hx = length(dx) * (0.75 + 0.35 * bristles(p + float2(1.5, 0.0), dx));
        float hy = length(dy) * (0.75 + 0.35 * bristles(p + float2(0.0, 1.5), dy));
        float3 n = normalize(float3(-(hx - h0) / 1.5 * 0.35, -(hy - h0) / 1.5 * 0.35, 1.0));
        float3 L = normalize(float3(-0.5, -0.7, 0.6));
        float m = sstep(0.5, 8.0, len);
        col *= 1.0 + (dot(n, L) - L.z) * 0.8 * m;
        float3 hv = normalize(L + float3(0.0, 0.0, 1.0));
        col += float3(1.0, 0.97, 0.9) * pow(max(dot(n, hv), 0.0), 36.0) * 0.22 * m * (1.0 - dry * 0.6);
    }

    // A thin lit top edge and a darker bottom edge on the card.
    float rim = sstep(1.6, 0.0, abs(sd + 1.0));
    float vy = p.y / size.y;
    col += float3(1.0) * rim * 0.12 * (1.0 - vy);
    col *= 1.0 - rim * 0.25 * vy;

    col = clamp(col, 0.0, 1.0);
    return half4(half3(col) * half(alpha), half(alpha));
}
