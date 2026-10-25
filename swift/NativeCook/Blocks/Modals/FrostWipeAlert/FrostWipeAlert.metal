#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The frosted window pane over the whole screen. A step-for-step port of the React Native SkSL
// (frostShader.ts); see there for the full description. Colours are sRGB, so the SkSL's P3
// conversions are left out, and GLSL's `smoothstep` is reimplemented so reversed edges behave the
// same.
//
// frostWipeAlertBake is a compute kernel run once per screen size (FrostWipeAlertArt.swift) into
// two grey images: mode 0 the ice (thin frost feathers rooted along the four edges, growing
// inward, plus sparkles), mode 1 the haze density, which also shapes the regrowth front.
//
// Two passes run every frame: frostWipeAlert on the blurred app (frosted glass = the app pulled
// toward a cold white by the haze), then frostWipeAlertIce on the ice image, its needles on top. Wiped glass is transparent, so the real views show sharp. The
// wipe is a trail of up to 32 points (x, y, brush radius, regrow progress q; a negative radius
// starts a new stroke); droplets (x, head y, trail length, radius) are clear lenses with a bright
// highlight and caustic over a wet, half-cleared track.

namespace frostWipeAlertShader {

static inline float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

static inline float hash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

static float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

static float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.07 + float2(13.1, 7.7);
        a *= 0.5;
    }
    return v;
}

static inline float segLine(float2 p, float2 a, float2 d, float len) {
    float2 q = p - a;
    return length(q - d * clamp(dot(q, d), 0.0, len));
}

// Distance to one frost feather: a spine from s along d, len pt long; side branches every 11 pt
// on both sides at about 62° toward the tip, shorter toward the tip; fine barbs every 2.6 pt
// along the nearest branch.
static float feather(float2 p, float2 s, float2 d, float len) {
    float2 q = p - s;
    float t = dot(q, d);
    float best = segLine(p, s, d, len);
    float reach = len * 0.3 + 4.0;
    float2 n = float2(-d.y, d.x);
    if (t < -reach || t > len + 4.0 || abs(dot(q, n)) > reach) return best;
    float side = dot(q, n) >= 0.0 ? 1.0 : -1.0;
    float2 b = normalize(d * 0.47 + n * side * 0.88);
    float sp = 11.0;
    float k0 = floor(t / sp);
    float bestB = 10000.0;
    float2 bo = s;
    float bl = 0.0;
    for (int j = 0; j < 3; j++) {
        float k = k0 - float(j);
        if (k >= 1.0 && k * sp < len) {
            float2 o = s + d * (k * sp);
            float l = (len - k * sp) * 0.3 + 3.0;
            float dist = segLine(p, o, b, l);
            if (dist < bestB) {
                bestB = dist;
                bo = o;
                bl = l;
            }
        }
    }
    best = min(best, bestB);
    if (bl > 0.0 && bestB < 6.0) {
        // Barbs off that branch, both sides, angled toward its tip.
        float2 bq = p - bo;
        float tb = dot(bq, b);
        float2 bn = float2(-b.y, b.x);
        float bs = dot(bq, bn) >= 0.0 ? 1.0 : -1.0;
        float2 c = normalize(b * 0.7 + bn * bs * 0.71);
        float m0 = floor(tb / 2.6);
        for (int j = 0; j < 2; j++) {
            float m = m0 - float(j);
            if (m >= 1.0 && m * 2.6 < bl) {
                best = min(best, segLine(p, bo + b * (m * 2.6), c, min(3.6, (bl - m * 2.6) * 0.5 + 0.8)));
            }
        }
    }
    return best;
}

// Feathers rooted along one edge (origin o, direction along it tan, inward normal inw), a root
// every 17 pt with a little jitter; longer near the corners, pointing inward and leaning away from
// the nearest corner.
static float edgeFrost(float2 p, float2 o, float2 tan, float2 inw, float edgeLen, float seed) {
    float along = dot(p - o, tan);
    float depth = dot(p - o, inw);
    if (depth > 210.0) return 10000.0;
    float spacing = 17.0;
    float k0 = floor(along / spacing);
    float best = 10000.0;
    for (int j = -7; j <= 7; j++) {
        float k = k0 + float(j);
        float x = (k + hash(float2(k, seed))) * spacing;
        if (x >= -10.0 && x <= edgeLen + 10.0) {
            float h1 = hash(float2(k, seed + 1.0));
            float h2 = hash(float2(k, seed + 2.0));
            float cornerness = 1.0 - sstep(0.0, 90.0, min(x, edgeLen - x));
            float len = mix(55.0, 150.0, h1 * h1) + cornerness * 60.0;
            float lean = (h2 - 0.5) * 1.1 + cornerness * (x < edgeLen * 0.5 ? 0.7 : -0.7);
            float2 dir = normalize(inw * cos(lean) + tan * sin(lean));
            float2 root = o + tan * x - inw * 2.0;
            best = min(best, feather(p, root, dir, len));
        }
    }
    return best;
}

static float bake(float2 p, float2 size, float mode) {
    float2 e = min(p, size - p);
    float near = min(e.x, e.y);
    float edgeN = near / min(size.x, size.y);
    if (mode > 0.5) {
        float body = fbm(p * 0.012 + fbm(p * 0.02) * 1.6);
        float edge = 1.0 - sstep(0.0, 0.3, edgeN);
        return clamp(0.2 + body * 0.45 + edge * 0.4, 0.0, 1.0);
    }
    float d = 10000.0;
    if (e.x < 210.0) {
        if (p.x < size.x * 0.5) d = min(d, edgeFrost(p, float2(0.0, 0.0), float2(0.0, 1.0), float2(1.0, 0.0), size.y, 3.0));
        else d = min(d, edgeFrost(p, float2(size.x, 0.0), float2(0.0, 1.0), float2(-1.0, 0.0), size.y, 7.0));
    }
    if (e.y < 210.0) {
        if (p.y < size.y * 0.5) d = min(d, edgeFrost(p, float2(0.0, 0.0), float2(1.0, 0.0), float2(0.0, 1.0), size.x, 11.0));
        else d = min(d, edgeFrost(p, float2(0.0, size.y), float2(1.0, 0.0), float2(0.0, -1.0), size.x, 13.0));
    }
    // Thin crisp lines (about 1 pt), stronger toward the edges, gone in the middle third.
    float fade = 1.0 - sstep(0.16, 0.34, edgeN);
    float ice = sstep(0.6, 0.1, d) * (0.55 + 0.45 * (1.0 - sstep(0.0, 0.2, edgeN))) * fade;
    // Sparkles: tiny points, more of them near the edges.
    float2 gc = floor(p / 5.0);
    float2 go = (gc + float2(hash(gc + 21.0), hash(gc + 22.0))) * 5.0;
    float spark = step(1.0 - (0.012 + 0.03 * fade), hash(gc + 23.0)) * sstep(0.8, 0.0, length(p - go));
    return clamp(max(ice, spark), 0.0, 1.0);
}

static inline float segDist(float2 p, float2 a, float2 b) {
    float2 ab = b - a;
    float h = clamp(dot(p - a, ab) / max(dot(ab, ab), 0.0001), 0.0, 1.0);
    return length(p - a - ab * h);
}

static inline float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

static inline float hazeAt(texture2d<half> hazeTex, float2 p, float2 size) {
    constexpr sampler s(filter::linear, address::clamp_to_edge);
    return float(hazeTex.sample(s, p / size).r);
}

// The blurred app (the frost pass's layer); before its snapshot lands, its colours: a night
// gradient and the light card. Samples stay inside the layer, so they are never transparent.
static float3 behind(float2 p, SwiftUI::Layer content, float2 size, float ready, float4 card) {
    if (ready > 0.5) {
        half4 c = content.sample(clamp(p, float2(0.5), size - 0.5));
        return c.a > 0.01h ? clamp(float3(c.rgb / c.a), 0.0, 1.0) : float3(0.1, 0.165, 0.227);
    }
    float3 sky = mix(float3(0.03, 0.06, 0.1), float3(0.1, 0.17, 0.24), p.y / size.y);
    float c = 1.0 - sstep(-14.0, 14.0, sdRoundRect(p - card.xy - card.zw * 0.5, card.zw * 0.5, 28.0));
    return mix(sky, float3(0.9, 0.93, 0.95), c * step(1.0, card.z));
}

// Clearness: the best of every wiped capsule, each refreezing as its q rises.
static float wipeClear(float2 p, float grow, device const float *wipe, int wipeCount) {
    float clear = 0.0;
    float4 prev = float4(0.0);
    int nw = min(wipeCount / 4, 32);
    for (int i = 0; i < nw; i++) {
        float4 w = float4(wipe[i * 4], wipe[i * 4 + 1], wipe[i * 4 + 2], wipe[i * 4 + 3]);
        float r = abs(w.z);
        float4 before = prev;
        prev = w;
        if (r > 0.5) {
            float d = length(p - w.xy);
            if (w.z > 0.0 && abs(before.z) > 0.5) d = min(d, segDist(p, before.xy, w.xy));
            float cover = 1.0 - sstep(r * 0.55, r, d);
            if (cover > 0.0) {
                float c = sstep(w.w - 0.08, w.w + 0.08, cover * 0.9 + grow - 0.3);
                clear = max(clear, c * sstep(0.0, 0.35, cover));
            }
        }
    }
    return clear;
}

struct Drops {
    float dropClear;
    float light;
    float wet;
    float lensA;
    float2 lensAt;   // where the lens looks into the scene
    float lensR;     // its normalised radius there
};

// Droplets: a wet track behind each, and the clear drop with its light. The lens is the drop
// with the largest weight here; the frost pass shades it.
static Drops dropsAt(float2 p, device const float *drops, int dropCount) {
    Drops o = { 0.0, 0.0, 0.0, 0.0, float2(0.0), 0.0 };
    int nd = min(dropCount / 4, 8);
    for (int k = 0; k < nd; k++) {
        float4 dr = float4(drops[k * 4], drops[k * 4 + 1], drops[k * 4 + 2], drops[k * 4 + 3]);
        if (dr.w > 0.3) {
            float2 head = dr.xy;
            float track = segDist(p, head - float2(0.0, dr.z), head);
            // The track is wet glass: less haze and no ice, still frosted and bright, never cleared.
            float tw = 1.0 - sstep(dr.w * 0.25, dr.w * 0.75, track);
            o.wet = max(o.wet, tw);
            o.light = max(o.light, sstep(1.2, 0.2, abs(p.x - head.x + dr.w * 0.35)) * tw * 0.18 * step(p.y, head.y));
            float2 q = (p - head) / float2(dr.w, dr.w * 1.2);
            float r = length(q);
            if (r < 1.15) {
                o.dropClear = max(o.dropClear, sstep(1.0, 0.8, r));
                float glint = sstep(0.32, 0.04, length(q - float2(-0.3, -0.4)));
                float caustic = sstep(0.25, 0.0, abs(r - 0.72)) * sstep(0.1, 0.7, q.y);
                float rim = sstep(0.75, 0.95, r) * sstep(1.15, 0.98, r);
                o.light = max(o.light, glint * 0.95 + caustic * 0.55 + rim * 0.3);
                float m = sstep(1.0, 0.85, r) * 0.6;
                if (m > o.lensA) {
                    o.lensA = m;
                    o.lensAt = head - q * dr.w * 0.9;
                    o.lensR = r;
                }
            }
        }
    }
    return o;
}

} // namespace frostWipeAlertShader

using namespace frostWipeAlertShader;

/// Bakes the ice (mode 0) or the haze (mode 1) for a screen of `size` points at `scale` pixels per
/// point, as grey.
kernel void frostWipeAlertBake(
    texture2d<half, access::write> out [[texture(0)]],
    constant float4 &params [[buffer(0)]],
    uint2 gid [[thread_position_in_grid]]
) {
    if (gid.x >= out.get_width() || gid.y >= out.get_height()) return;
    float2 size = params.xy;
    float scale = params.z;
    float2 p = (float2(gid) + 0.5) / scale;
    half v = half(bake(p, size, params.w));
    out.write(half4(v, v, v, 1.0h), gid);
}

/// The frost pass, applied to the blurred app: frosted glass without the ice, the wet edges, the
/// droplets' lenses and light. Wiped glass is transparent, so the real views show sharp.
[[ stitchable ]] half4 frostWipeAlert(
    float2 p, SwiftUI::Layer content,
    texture2d<half> hazeTex, float2 size, float ready, float4 card,
    device const float *wipe, int wipeCount, device const float *drops, int dropCount
) {
    float haze = hazeAt(hazeTex, p, size);
    // The regrowth front follows the smooth haze only (the thin ice lines would cut clear lines).
    float clear = wipeClear(p, haze * 0.8, wipe, wipeCount);
    Drops d = dropsAt(p, drops, dropCount);

    float frost = 1.0 - clear;
    float3 col = mix(behind(p, content, size, ready, card), float3(0.86, 0.91, 0.96), (0.46 + 0.32 * haze) * (1.0 - d.wet * 0.25));
    // The wet edge of a wipe: a darker film and a thin bright line just inside the frost.
    float edge = clear * frost * 4.0;
    col = col * (1.0 - edge * 0.25) + float3(0.95, 0.98, 1.0) * pow(edge, 4.0) * 0.35;
    col = clamp(col, 0.0, 1.0);

    float alpha = frost * (1.0 - d.dropClear);
    float3 pre = col * alpha;
    if (d.lensA > 0.0) {
        // The drop is a small lens: the scene behind it flipped and brightened, with a cool tint
        // toward its edge. It only ever brightens: never darker than the frost around it.
        float3 seen = clamp(behind(d.lensAt, content, size, ready, card) * 1.15 + 0.06, 0.0, 1.0);
        seen = mix(seen, float3(0.62, 0.74, 0.88), sstep(0.5, 0.95, d.lensR) * 0.45);
        seen = max(seen, float3(0.55, 0.62, 0.7));
        pre = pre * (1.0 - d.lensA) + seen * d.lensA;
        alpha = alpha * (1.0 - d.lensA) + d.lensA;
    }
    // Light from the droplets, added over whatever shows through them.
    pre += float3(0.97, 0.99, 1.0) * d.light;
    alpha = clamp(alpha + d.light, 0.0, 1.0);
    float3 outc = alpha > 0.0 ? clamp(pre / alpha, 0.0, 1.0) : float3(0.0);
    return half4(half3(outc * alpha), half(alpha));
}

/// The ice pass, applied to the ice image: its bright needles where the glass is still frosted,
/// none on wet tracks or under drops. The view adds it to the frost below (plus lighter), so the
/// needles saturate to white like the SkSL's `col += ice`.
[[ stitchable ]] half4 frostWipeAlertIce(
    float2 p, SwiftUI::Layer ice,
    texture2d<half> hazeTex, float2 size,
    device const float *wipe, int wipeCount, device const float *drops, int dropCount
) {
    half4 s = ice.sample(p);
    float v = s.a > 0.001h ? float(s.r / s.a) : 0.0;
    if (v < 0.002) return half4(0.0h);
    float haze = hazeAt(hazeTex, p, size);
    float clear = wipeClear(p, haze * 0.8, wipe, wipeCount);
    Drops d = dropsAt(p, drops, dropCount);
    // Added to the frost as in the SkSL (col += ice), through the view's plus-lighter blend.
    float a = clamp(v * 0.85 * (1.0 - d.wet), 0.0, 1.0) * (1.0 - clear) * (1.0 - d.dropClear) * (1.0 - d.lensA);
    return half4(half3(0.93, 0.97, 1.0) * half(a), half(a));
}
