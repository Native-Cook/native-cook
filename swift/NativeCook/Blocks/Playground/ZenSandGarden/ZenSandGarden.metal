#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Lights the height map like sand under a low sun (with ridge shadows), and draws the stones and
// their moss analytically, so the stones' long soft shadows fall across the grooves.

static float zsgHash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

static float zsgNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(zsgHash(i), zsgHash(i + float2(1.0, 0.0)), u.x),
               mix(zsgHash(i + float2(0.0, 1.0)), zsgHash(i + float2(1.0, 1.0)), u.x), u.y);
}

static float zsgFbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * zsgNoise(p);
        p = p * 2.07 + 13.1;
        a *= 0.5;
    }
    return v;
}

// Metal leaves smoothstep with reversed edges undefined; this one works either way, like Skia's.
static float zsgSmooth(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

struct ZsgStone {
    float4 pos;   // centre x, y, radius, height
    float4 shape; // a2, p2, a3, p3
    float2 shape2; // a5, p5
    float4 tint;  // rgb, moss
};

static ZsgStone zsgStone(device const float *s, int k) {
    device const float *v = s + k * 14;
    ZsgStone o;
    o.pos = float4(v[0], v[1], v[2], v[3]);
    o.shape = float4(v[4], v[5], v[6], v[7]);
    o.shape2 = float2(v[8], v[9]);
    o.tint = float4(v[10], v[11], v[12], v[13]);
    return o;
}

// The height at a point in pt, sampled linearly (the map is 2 px per pt).
static float zsgSandH(texture2d<half> tex, float2 size, float2 p) {
    constexpr sampler s(coord::normalized, address::clamp_to_edge, filter::linear);
    return float(tex.sample(s, p / size).r);
}

// Distance from a stone's centre as a share of its outline at that bearing (1 on the outline).
static float zsgStoneD(float2 p, ZsgStone st) {
    float2 q = p - st.pos.xy;
    float l = length(q);
    if (l > st.pos.z * 1.7) return 9.0;
    float th = atan2(q.y, q.x);
    float r = st.pos.z * (1.0 + st.shape.x * cos(2.0 * th + st.shape.y) + st.shape.z * cos(3.0 * th + st.shape.w)
                          + st.shape2.x * cos(5.0 * th + st.shape2.y));
    return l / r;
}

static float zsgDomeH(float d, float h) {
    return h * pow(clamp(1.0 - d * d, 0.0, 1.0), 0.45);
}

static float zsgStonesH(float2 p, ZsgStone a, ZsgStone b, ZsgStone c) {
    float ha = zsgDomeH(zsgStoneD(p, a), a.pos.w);
    float hb = zsgDomeH(zsgStoneD(p, b), b.pos.w);
    float hc = zsgDomeH(zsgStoneD(p, c), c.pos.w);
    return max(ha, max(hb, hc));
}

static float3 zsgStoneColor(float2 p, float d, ZsgStone st, float3 sun, float3 sunColor, float3 ambient) {
    float e = 0.8;
    float hx0 = zsgDomeH(zsgStoneD(p - float2(e, 0.0), st), st.pos.w);
    float hx1 = zsgDomeH(zsgStoneD(p + float2(e, 0.0), st), st.pos.w);
    float hy0 = zsgDomeH(zsgStoneD(p - float2(0.0, e), st), st.pos.w);
    float hy1 = zsgDomeH(zsgStoneD(p + float2(0.0, e), st), st.pos.w);
    float3 n = normalize(float3((hx0 - hx1) / (2.0 * e), (hy0 - hy1) / (2.0 * e), 1.0));
    float grain = zsgFbm(p * 0.08 + st.pos.xy * 0.013);
    float vein = zsgSmooth(0.46, 0.5, zsgFbm(p * float2(0.05, 0.16) + st.pos.yx * 0.02)) * 0.1;
    float3 alb = st.tint.rgb * (0.8 + 0.4 * grain) * (1.0 - vein);
    float speck = zsgHash(floor(p * 1.6) + st.pos.xy);
    alb *= 1.0 - 0.35 * step(0.92, speck);
    alb = mix(alb, min(alb * 1.55, float3(1.0)), step(0.975, zsgHash(floor(p * 1.6) + st.pos.yx)));
    float wrapL = clamp((dot(n, sun) + 0.25) / 1.25, 0.0, 1.0);
    float3 hv = normalize(sun + float3(0.0, 0.0, 1.0));
    float spec = pow(max(dot(n, hv), 0.0), 30.0) * 0.2;
    float ao = 0.5 + 0.5 * zsgSmooth(1.0, 0.72, d);
    return alb * (ambient * (0.45 + 0.55 * n.z) * ao + sunColor * wrapL * 1.05) + sunColor * spec;
}

// Moss at a stone's foot: an irregular fringe a little up the stone and out over the sand, in
// patches, with a fuzzy edge.
static float zsgMossMask(float2 p, float d, float amount, float seed) {
    if (amount <= 0.0 || d > 1.7) return 0.0;
    float reach = 1.0 + amount * (0.02 + 0.24 * zsgFbm(p * 0.06 + seed));
    float patches = zsgSmooth(0.3, 0.52, zsgFbm(p * 0.05 + seed * 1.7));
    float foot = zsgSmooth(0.82, 0.94, d);
    float fuzz = zsgNoise(p * 0.9 + seed) - 0.5;
    return clamp((reach - d) * 14.0 + fuzz * 1.8, 0.0, 1.0) * patches * foot;
}

static float3 zsgMossColor(float2 p, float shade, float m, float3 sun, float3 sunColor, float3 ambient) {
    float c = zsgFbm(p * 0.45);
    float3 alb = mix(float3(0.11, 0.15, 0.07), float3(0.34, 0.38, 0.15), c);
    // Drier, browner tips where the moss thins out.
    alb = mix(float3(0.36, 0.33, 0.2), alb, zsgSmooth(0.0, 0.7, m));
    float b0 = zsgNoise(p * 1.4);
    float bx = zsgNoise(p * 1.4 + float2(0.6, 0.0)) - b0;
    float by = zsgNoise(p * 1.4 + float2(0.0, 0.6)) - b0;
    float3 n = normalize(float3(-bx * 2.0, -by * 2.0, 1.0));
    float lit = max(dot(n, sun), 0.0);
    return alb * (ambient * 0.75 + sunColor * lit * (0.25 + 0.75 * shade) * 1.5);
}

[[ stitchable ]] half4 zenSandGardenLight(
    float2 p,
    texture2d<half> heightMap,
    float2 size,
    float3 sun,
    float3 sunColor,
    float3 ambient,
    device const float *stones, int stonesCount,
    float time
) {
    ZsgStone a = zsgStone(stones, 0);
    ZsgStone b = zsgStone(stones, 1);
    ZsgStone c = zsgStone(stones, 2);
    float dA = zsgStoneD(p, a);
    float dB = zsgStoneD(p, b);
    float dC = zsgStoneD(p, c);
    float dMin = min(dA, min(dB, dC));
    float2 toSun = normalize(sun.xy);
    float tanE = sun.z / max(length(sun.xy), 0.001);

    float stoneShadow = 1.0;
    float3 col = float3(0.0);
    if (dMin > 0.96) {
        // Sand: the normal from the height map, roughened by its grain.
        float D = 5.0;
        float h = zsgSandH(heightMap, size, p);
        float hl = zsgSandH(heightMap, size, p - float2(1.0, 0.0));
        float hr = zsgSandH(heightMap, size, p + float2(1.0, 0.0));
        float hu = zsgSandH(heightMap, size, p - float2(0.0, 1.0));
        float hd = zsgSandH(heightMap, size, p + float2(0.0, 1.0));
        float3 n = normalize(float3((hl - hr) * D * 0.5, (hu - hd) * D * 0.5, 1.0));
        float g = zsgHash(floor(p * 2.0));
        float g2 = zsgHash(floor(p * 2.0) + 17.0);
        n = normalize(n + float3(g - 0.5, g2 - 0.5, 0.0) * 0.14);
        float lit = max(dot(n, sun), 0.0);
        // Ridges shade the grooves behind them.
        float occl = 0.0;
        for (int i = 1; i <= 5; i++) {
            float s = float(i) * 1.3;
            occl = max(occl, (zsgSandH(heightMap, size, p + toSun * s) - h) * D - s * tanE);
        }
        float sandShadow = 1.0 - zsgSmooth(0.0, 0.8, occl);
        // The stones' long shadows, softer the further they fall.
        for (int i = 1; i <= 12; i++) {
            float s = float(i) * 8.0;
            float pen = 1.5 + s * 0.14;
            stoneShadow = min(stoneShadow, zsgSmooth(-pen, pen, s * tanE - zsgStonesH(p + toSun * s, a, b, c)));
        }
        float contact = 1.0 - 0.4 * (1.0 - zsgSmooth(1.0, 1.3, dMin));
        float3 alb = float3(0.9, 0.82, 0.68) * (0.94 + 0.12 * zsgFbm(p * 0.011));
        alb *= 0.93 + 0.07 * g;
        alb *= 0.84 + 0.16 * zsgSmooth(0.12, 0.55, h);
        float shade = min(sandShadow, stoneShadow);
        col = alb * (ambient * (0.5 + 0.5 * n.z) * contact + sunColor * lit * shade * 1.15 * contact);
        float twinkle = 0.5 + 0.5 * sin(time * 2.3 + g2 * 40.0);
        col += sunColor * step(0.994, g) * lit * shade * 0.6 * twinkle;
    }
    // Each stone over the sand, with a one-point soft edge.
    if (dA < 1.0) col = mix(col, zsgStoneColor(p, dA, a, sun, sunColor, ambient), clamp((1.0 - dA) * a.pos.z / 0.7, 0.0, 1.0));
    if (dB < 1.0) col = mix(col, zsgStoneColor(p, dB, b, sun, sunColor, ambient), clamp((1.0 - dB) * b.pos.z / 0.7, 0.0, 1.0));
    if (dC < 1.0) col = mix(col, zsgStoneColor(p, dC, c, sun, sunColor, ambient), clamp((1.0 - dC) * c.pos.z / 0.7, 0.0, 1.0));
    float moss = max(zsgMossMask(p, dA, a.tint.a, 3.1), max(zsgMossMask(p, dB, b.tint.a, 7.7), zsgMossMask(p, dC, c.tint.a, 12.4)));
    if (moss > 0.0) col = mix(col, zsgMossColor(p, dMin < 1.0 ? 1.0 : stoneShadow, moss, sun, sunColor, ambient), moss * 0.94);

    // Soft highlight roll-off, then a warm vignette.
    col = (1.0 - exp(-col * 1.7)) * 1.1;
    float2 v = (p / size - 0.5) * float2(1.15, 0.85);
    col *= 1.0 - 0.22 * zsgSmooth(0.3, 0.75, length(v));
    return half4(half3(col), 1.0h);
}
