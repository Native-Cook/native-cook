#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Pit Stop Review: the hero's track, the tyre smoke and the start-light lamps. Ports of world-shader.ts and
// fx-shaders.ts (SkSL); same equations and uniforms, in the hero's design points (402 x 220) or window points.
// SwiftUI treats shader output as sRGB, so the toP3 conversions the Skia build needs are left out.

static float psrHs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }
static float psrH1(float n) { return fract(sin(n * 91.345 + 47.853) * 43758.5453); }
static float psrVn(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    float a = psrHs(i);
    float b = psrHs(i + float2(1.0, 0.0));
    float c = psrHs(i + float2(0.0, 1.0));
    float d = psrHs(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
// One painted / light band: centre y, half width, strength. Soft at speed, a crisp paint edge in the box.
static float psrBand(float y, float c, float hw, float k, float soft) {
    float e = abs(y - c) - hw;
    return k * (1.0 - smoothstep(-soft, soft + 0.6, e));
}
// Box-filtered vertical paint bar [x0, x0 + w] seen through a blur window of length L ending at wx.
static float psrBar(float wx, float L, float x0, float w) {
    float lo = max(wx - L, x0);
    float hi = min(wx, x0 + w);
    return max(0.0, hi - lo) / max(L, 1.0);
}

// Coverage (premultiplied white light) of the world at hero point `xy`.
//   w0 = (world offset, blur length, haze, speed lines)   w1 = (haze centre x, scene time, box paint, unused)
static float psrWorldAlpha(float2 xy, float4 w0, float4 w1) {
    float worldOff = w0.x;
    float L = max(w0.y, 0.0);
    float hz = w0.z;
    float lines = w0.w;
    float hazeX = w1.x;
    float t = w1.y;
    float paint = w1.z;
    float speedy = clamp(L / 40.0, 0.0, 1.0);

    // Heat haze: displace the lookup in a lens behind the rear wing.
    float2 p = xy;
    float2 hc = float2(hazeX - 34.0, 82.0);
    float2 hd = (p - hc) / float2(78.0, 38.0);
    float mask = (1.0 - smoothstep(0.35, 1.0, length(hd))) * hz;
    if (mask > 0.001) {
        float nx = psrVn(float2(p.x * 0.11 + t * 2.0, p.y * 0.16 - t * 9.0)) - 0.5;
        float ny = psrVn(float2(p.x * 0.13 - 7.3, p.y * 0.12 - t * 7.0)) - 0.5;
        p += float2(nx, ny) * 7.0 * mask;
    }
    float wx = p.x + worldOff;
    float y = p.y;

    // Asphalt grain: sharp in the box, smeared into y-only streaks at speed (x-invariant at full speed).
    float sharpGrain = psrVn(float2(wx * 1.1, y * 1.1)) * 0.55 + psrVn(float2(wx * 0.28, y * 0.28)) * 0.45;
    float streakGrain = psrVn(float2(3.1, y * 0.62)) * 0.55 + psrVn(float2(7.7, y * 0.17)) * 0.45;
    float grain = mix(sharpGrain, streakGrain, speedy);
    float lum = 0.012 + (grain - 0.5) * mix(0.085, 0.025, speedy);

    // The pin's light bands; in the box they read as the pit lane's painted lines.
    float soft = mix(0.35, 1.6, speedy);
    float lanes = psrBand(y, 28.0, 2.6, 0.33, soft) + psrBand(y, 58.0, 0.6, 0.04, soft) + psrBand(y, 64.0, 0.8, 0.07, soft)
                + psrBand(y, 117.5, 2.2, 0.15, soft) + psrBand(y, 127.5, 1.8, 0.13, soft) + psrBand(y, 140.0, 0.8, 0.11, soft)
                + psrBand(y, 151.0, 3.2, 0.14, soft) + psrBand(y, 160.0, 0.6, 0.08, soft);
    lum += lanes * mix(1.35, 1.0, speedy);

    // Pit box: two transverse bars and a stop line, around world x = -3000 + [50, 364].
    float boxRear = psrBar(wx, L, -2950.0, 3.0) * step(31.0, y) * (1.0 - step(149.0, y));
    float boxFront = psrBar(wx, L, -2636.0, 3.0) * step(31.0, y) * (1.0 - step(149.0, y));
    float stopT = psrBar(wx, L, -2650.0, 18.0) * psrBand(y, 82.0, 1.2, 1.0, mix(0.3, 2.0, speedy));
    lum += (boxRear + boxFront) * 0.30 * paint + stopT * 0.18 * paint;

    // Speed lines: sparse bright 1 pt lines in 1.6 pt rows, rushing left.
    if (lines > 0.001) {
        float row = floor(y / 1.6);
        float pick = step(0.9, psrH1(row));
        float seg = psrVn(float2(xy.x * 0.0045 + t * 16.0 * (0.7 + psrH1(row + 3.0)), row * 3.7));
        lum += pick * smoothstep(0.55, 0.8, seg) * lines * (0.25 + 0.45 * psrH1(row + 9.0));
    }

    // Fade into the page above and below the hero.
    float env = smoothstep(0.0, 16.0, xy.y) * (1.0 - smoothstep(172.0, 214.0, xy.y));
    return clamp(lum, 0.0, 1.0) * env;
}

// The track itself: white light over the page's charcoal gradient.
[[ stitchable ]] half4 pitStopReviewWorld(float2 xy, half4 current, float4 w0, float4 w1) {
    half a = half(psrWorldAlpha(xy, w0, w1));
    return half4(a, a, a, a);
}

// Light streaks over the car body (the world's coverage drawn source-atop the car, scaled by `streaks`);
// `origin` is where the car layer's top left sits in the hero.
[[ stitchable ]] half4 pitStopReviewCarStreaks(float2 xy, half4 car, float2 origin, float4 w0, float4 w1, float streaks) {
    float a = psrWorldAlpha(xy + origin, w0, w1) * streaks;
    half srcA = half(a);
    half3 rgb = half3(srcA) * car.a + car.rgb * (1.0h - srcA);
    return half4(rgb, car.a);
}

// Cool, darker grade of the rendered car and its horizontal motion blur, in one pass on the car layer.
// sigma = blur (pt, Gaussian); the grade is 4 x 5 gain / offset per channel on straight colour.
[[ stitchable ]] half4 pitStopReviewCarGrade(float2 xy, SwiftUI::Layer layer, float2 size, float sigma) {
    half4 c;
    if (sigma < 0.3) {
        c = layer.sample(xy);
    } else {
        const int taps = 33;
        float reach = sigma * 3.0;
        float4 sum = float4(0.0);
        float norm = 0.0;
        for (int i = 0; i < taps; i++) {
            float t = (float(i) / float(taps - 1) * 2.0 - 1.0) * reach;
            float w = exp(-0.5 * t * t / (sigma * sigma));
            float x = xy.x + t;
            if (x >= 0.0 && x <= size.x) sum += float4(layer.sample(float2(x, xy.y))) * w;
            norm += w;
        }
        c = half4(sum / norm);
    }
    half3 straight = c.a > 0.0001h ? c.rgb / c.a : half3(0.0h);
    half3 graded = clamp(straight * half3(1.12h, 1.14h, 1.19h) - half3(0.17h, 0.17h, 0.16h), 0.0h, 1.0h);
    return half4(graded * c.a, c.a);
}

// Tyre smoke: up to 32 puffs (x, y, radius, alpha) as soft discs with a wispy edge, one light-grey layer.
[[ stitchable ]] half4 pitStopReviewSmoke(float2 p, half4 current, device const float *puffs, int count) {
    float sum = 0.0;
    int n = min(count / 4, 32);
    for (int i = 0; i < n; i++) {
        float4 q = float4(puffs[i * 4], puffs[i * 4 + 1], puffs[i * 4 + 2], puffs[i * 4 + 3]);
        if (q.w > 0.002) {
            float d = length(p - q.xy) / max(q.z, 0.5);
            if (d < 1.0) {
                float wisp = 0.75 + 0.5 * psrVn(p * 0.09 + q.xy * 0.013);
                float f = 1.0 - d;
                sum += q.w * f * f * (3.0 - 2.0 * f) * wisp;
            }
        }
    }
    float a = 1.0 - exp(-sum * 1.6);
    return half4(half3(0.91, 0.90, 0.93) * half(a), half(a));
}

static float psrStarDist(float2 p, float r, float rf) {
    const float2 k1 = float2(0.809016994375, -0.587785252292);
    const float2 k2 = float2(-0.809016994375, -0.587785252292);
    p.x = abs(p.x);
    p -= 2.0 * max(dot(k1, p), 0.0) * k1;
    p -= 2.0 * max(dot(k2, p), 0.0) * k2;
    p.x = abs(p.x);
    p.y -= r;
    float2 ba = rf * float2(-k1.y, k1.x) - float2(0.0, 1.0);
    float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, r);
    return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
}

// Start-light lamps: up to 20 sprites lamp[i] = (x, y, radius, morph), look[i] = (alpha, glow, stroke px, 0).
// morph 0 = a lit red start light (bloom, red lens, white-hot centre); morph 1 = a white outline five-point star;
// between them the shape is a signed-distance blend of circle and star. `origin` = the rect's top left in the window.
[[ stitchable ]] half4 pitStopReviewLamps(float2 pos, half4 current, float2 origin,
                                           device const float *lamps, int lampCount, device const float *looks, int lookCount) {
    float2 xy = pos + origin;
    float3 col = float3(0.0);
    float alpha = 0.0;
    int n = min(lampCount / 4, 20);
    for (int i = 0; i < n; i++) {
        float4 L = float4(lamps[i * 4], lamps[i * 4 + 1], lamps[i * 4 + 2], lamps[i * 4 + 3]);
        float4 K = float4(looks[i * 4], looks[i * 4 + 1], looks[i * 4 + 2], looks[i * 4 + 3]);
        if (K.x > 0.002) {
            float2 p = xy - L.xy;
            float r = L.z;
            float reach = r * 3.2;
            if (abs(p.x) < reach && abs(p.y) < reach) {
                float m = L.w;
                float dc = length(p) - r;
                float ds = psrStarDist(float2(p.x, -p.y), r, 0.42);
                float d = mix(dc, ds, m);
                // Red lamp: lens + white-hot core + bloom, fading out as it becomes a star.
                float lens = 1.0 - smoothstep(-0.8, 0.8, d);
                float core = exp(-dot(p, p) / (r * r * 0.18));
                float bloom = exp(-max(d, 0.0) / (r * 0.9)) * K.y;
                float lampA = (1.0 - m) * K.x;
                float3 lampC = mix(float3(0.77, 0.09, 0.05), float3(1.0, 0.23, 0.19), core) + float3(0.0, 0.6, 0.55) * core * core;
                // White outline star.
                float stroke = (1.0 - smoothstep(K.z * 0.5 - 0.6, K.z * 0.5 + 0.6, abs(d))) * m * K.x;
                float a1 = clamp(lens * lampA, 0.0, 1.0);
                float a2 = clamp(bloom * lampA * 0.55, 0.0, 1.0) * (1.0 - lens);
                col = col * (1.0 - a1) + lampC * a1;
                alpha = alpha + a1 * (1.0 - alpha);
                col = col + float3(1.0, 0.19, 0.12) * a2;
                alpha = alpha + a2 * (1.0 - alpha);
                col = col * (1.0 - stroke) + float3(1.0) * stroke;
                alpha = alpha + stroke * (1.0 - alpha);
            }
        }
    }
    float3 c = alpha > 0.0001 ? col / alpha : float3(0.0);
    return half4(half3(min(c, float3(1.0))) * half(alpha), half(alpha));
}
