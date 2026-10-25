#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

/*
 Film Negative Card — the negative strip in one pass, in the strip's own coordinates
 (top-left = 0, 0). Ported from the React Native Skia shader (filmNegativeShader.ts) with the
 same maths. The layer is the strip-sized scene: each frame's crop of the photo already drawn in
 its frame rect (FilmNegativeCardScene), so `scene()` samples the layer instead of the photo.

 transmission = orange mask × (0.07 + 0.93 × (1 − scene)^1.15). Off the light: 55 % of it plus
 a gloss band. Over the light: the positive, 1 − transmission / mask through an S-curve, with a
 warm halo on the turning line. The loupe magnifies 1.8× with a slight barrel and a colour split.

   strip  (0, 0, width, height)     frame (x, y, w, h) of frame 7     gap  between frames
   light  lit panel rect (strip space)   loupe (cx, cy, radius, opacity)
   perf   (pitch, hole w, hole h, x phase)   rows (top hole centre y, bottom hole centre y)
   misc   (sheen = strip's screen y, roll flag)
 */

static float FilmNegativeCardSdBox(float2 p, float2 c, float2 b, float r) {
    float2 q = abs(p - c) - b + r;
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

static float FilmNegativeCardHash(float2 q) {
    return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453);
}

static float FilmNegativeCardMod(float x, float y) {
    return x - y * floor(x / y);
}

static float3 FilmNegativeCardNegative(float2 p, float3 base, SwiftUI::Layer layer, float4 frame, float gap) {
    float pitchF = frame.z + gap;
    float i = floor((p.x - frame.x + gap * 0.5) / pitchF);
    i = clamp(i, -1.0, 1.0);
    float2 o = float2(frame.x + i * pitchF, frame.y);
    float2 l = p - o;
    float inFrame = 1.0 - smoothstep(-0.6, 0.6, FilmNegativeCardSdBox(l, frame.zw * 0.5, frame.zw * 0.5, 2.5));
    // The frame's scene, clamped inside the frame (half a point in, so the gap never bleeds in).
    float2 lq = clamp(l, float2(0.5), frame.zw - 0.5);
    float3 s = float3(layer.sample(o + lq).rgb);
    float3 dens = pow(clamp(1.0 - s, 0.0, 1.0), float3(1.15));
    float3 exposed = base * (0.07 + 0.93 * dens);
    float3 clearBase = base * (0.95 + 0.04 * FilmNegativeCardHash(floor(p * 0.5)));
    return mix(clearBase, exposed, inFrame);
}

static float3 FilmNegativeCardPositive(float3 neg, float3 base) {
    float3 c = clamp(1.0 - neg / base, 0.0, 1.0);
    c = clamp((c - 0.03) / 0.95, 0.0, 1.0);
    c = mix(c, c * c * (3.0 - 2.0 * c), 0.4);
    return c * float3(1.03, 1.0, 0.965);
}

[[ stitchable ]] half4 FilmNegativeCardStrip(
    float2 p,
    SwiftUI::Layer layer,
    float4 strip,
    float4 frame,
    float gap,
    float4 light,
    float4 loupe,
    float4 perf,
    float2 rows,
    float2 misc
) {
    float sheen = misc.x;
    float2 sc = strip.zw * 0.5;
    float a = 1.0 - smoothstep(-0.6, 0.6, FilmNegativeCardSdBox(p, sc, sc, 4.0));
    if (a <= 0.0) return half4(0.0);
    // Perforations: rounded holes, two rows, cut right through.
    float hx = FilmNegativeCardMod(p.x - perf.w, perf.x) - perf.x * 0.5;
    float hTop = FilmNegativeCardSdBox(float2(hx, p.y), float2(0.0, rows.x), perf.yz * 0.5, 2.4);
    float hBot = FilmNegativeCardSdBox(float2(hx, p.y), float2(0.0, rows.y), perf.yz * 0.5, 2.4);
    float hole = min(hTop, hBot);
    a *= smoothstep(-0.6, 0.6, hole);
    if (a <= 0.0) return half4(0.0);

    float3 base = float3(0.90, 0.54, 0.30);
    float3 neg = FilmNegativeCardNegative(p, base, layer, frame, gap);

    // Off the light: the room's light on the film, plus a gloss band that slides with the strip.
    float band = p.x * 0.55 + p.y - (sheen * 0.9 - 120.0);
    float gloss = exp(-band * band / 1800.0) * 0.16 + exp(-band * band / 160.0) * 0.07;
    float3 off = neg * 0.55 + float3(1.0, 0.94, 0.86) * gloss;
    off *= 0.86 + 0.14 * smoothstep(0.0, 2.5, hole);

    // On the light: the inverted positive, brighter in the middle of the panel.
    float2 lc = light.xy + light.zw * 0.5;
    float sd = FilmNegativeCardSdBox(p, lc, light.zw * 0.5, 8.0);
    float lit = 1.0 - smoothstep(-18.0, 6.0, sd);
    float2 rel = (p - lc) / (light.zw * 0.5);
    float fall = 0.9 + 0.1 * (1.0 - clamp(dot(rel, rel) * 0.5, 0.0, 1.0));
    float3 pos = FilmNegativeCardPositive(neg, base) * fall;

    float3 col = mix(off, pos, lit);
    col += float3(1.0, 0.72, 0.42) * (lit * (1.0 - lit) * 4.0) * 0.20;

    // The loupe: a magnified positive.
    if (loupe.w > 0.001) {
        float2 d = p - loupe.xy;
        float r = length(d) / loupe.z;
        if (r < 1.0) {
            float2 q = loupe.xy + d * (1.0 + 0.14 * r * r) / 1.8;
            float2 e = d / max(length(d), 0.001) * r * r * 1.2;
            float3 cr = FilmNegativeCardPositive(FilmNegativeCardNegative(q + e, base, layer, frame, gap), base);
            float3 cg = FilmNegativeCardPositive(FilmNegativeCardNegative(q, base, layer, frame, gap), base);
            float3 cb = FilmNegativeCardPositive(FilmNegativeCardNegative(q - e, base, layer, frame, gap), base);
            float3 mag = float3(cr.r, cg.g, cb.b) * 1.04;
            mag *= 1.0 - 0.32 * smoothstep(0.72, 1.0, r);
            float spec = smoothstep(0.82, 0.95, r) * (1.0 - smoothstep(0.95, 1.0, r)) * clamp(-d.y / max(length(d), 0.001), 0.0, 1.0);
            mag += float3(spec * 0.25);
            float edge = 1.0 - smoothstep(0.97, 1.0, r);
            col = mix(col, mag, loupe.w * edge);
        }
    }
    return half4(half3(col * a), half(a));
}
