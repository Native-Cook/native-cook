#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Soap Bubble Pop in one full-screen pass: a pastel tiled bathroom wall, a bubble wand holding a
// film of soap, up to three bubbles, and the droplets of the ones that burst. A step-for-step port
// of the React Native SkSL (bubbleShader.ts); see there for the full description. SwiftUI shaders
// work in sRGB, so the Display P3 conversion the Skia version needs is left out; `smoothstep` is
// reimplemented and squares are products (Metal leaves pow of a negative base undefined).

namespace soapBubblePop {

constant float TAU = 6.2831853;

struct Scene {
    float2 size;
    float film;
};

static inline float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

static inline float sq(float x) { return x * x; }

static inline float hsh(float2 q) { return fract(precise::sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }
static inline float hsh1(float x) { return fract(precise::sin(x * 91.345) * 47453.5453); }

static float vnoise(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hsh(i), hsh(i + float2(1.0, 0.0)), u.x),
               mix(hsh(i + float2(0.0, 1.0)), hsh(i + float2(1.0, 1.0)), u.x), u.y);
}

static float fbm3(float2 q) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        s += a * vnoise(q);
        q = float2(q.x * 1.7 - q.y * 1.1, q.x * 1.1 + q.y * 1.7) + 2.3;
        a *= 0.5;
    }
    return s / 0.875;
}

// The wall: pastel aqua to lilac, rounded tiles with soft grout, light from the upper left.
static float3 wall(float2 p, float2 size) {
    float2 uv = p / size;
    float3 top = float3(0.47, 0.76, 0.85);
    float3 mid = float3(0.55, 0.67, 0.91);
    float3 low = float3(0.73, 0.62, 0.89);
    float3 col = mix(top, mid, sstep(0.0, 0.5, uv.y));
    col = mix(col, low, sstep(0.45, 1.0, uv.y));
    float tile = 46.0;
    float2 cell = fract((p + float2(9.0, 14.0)) / tile) * tile;
    float2 e = min(cell, tile - cell);
    float grout = 1.0 - sstep(0.6, 2.2, min(e.x, e.y));
    float bevel = sstep(2.0, 7.0, cell.x) * sstep(2.0, 7.0, cell.y);
    float glaze = 1.0 - sstep(0.0, 1.0, 1.0 - bevel);
    col *= 1.0 - 0.05 * grout;
    col += float3(0.035) * (1.0 - sstep(1.5, 5.0, min(cell.x, cell.y))) * (1.0 - grout);
    col += float3(0.02) * glaze * (0.5 - uv.y);
    float2 lq = uv - float2(0.15, 0.08);
    col += float3(0.07, 0.06, 0.04) * exp(-dot(lq, lq) * 4.0);
    col *= 1.0 + (hsh(floor(p * 1.5)) - 0.5) * 0.03;
    return col;
}

// Thin-film reflectance of soap (n = 1.33) d nm thick, seen at cos(view angle) cv.
static float3 thinFilm(float d, float cv) {
    float ct = sqrt(1.0 - (1.0 - cv * cv) / 1.769);
    float opd = 2.0 * 1.33 * d * ct;
    float3 s = sin(3.14159265 * opd / float3(650.0, 532.0, 450.0));
    return s * s;
}

static float3 bubble(float3 col, float2 p, float4 b, float4 k, float seed, thread const Scene &s) {
    if (b.w <= 0.001 || b.z <= 1.0) return col;
    float film = s.film;
    float2 rel = p - b.xy;
    float reach = b.z * 1.25;
    if (dot(rel, rel) > reach * reach) return col;
    float ang = atan2(rel.y, rel.x);
    float wob = k.w * (cos(2.0 * ang + film * TAU * 2.0 + seed * 5.0) + 0.6 * cos(3.0 * ang - film * TAU * 3.0 + seed * 2.0));
    float R = b.z * (1.0 + wob);
    float2 q = rel / R;
    float d2 = dot(q, q);
    float edge = 1.0 - sstep(1.0 - 1.4 / R, 1.0 + 0.4 / R, sqrt(d2));
    if (edge <= 0.0) return col;
    float cv = sqrt(max(1.0 - d2, 0.0));
    float hole = 0.0;
    float rimGlow = 0.0;
    if (k.x >= 0.0) {
        float2 h = rel - k.yz;
        float grow = 1.0 - sq(1.0 - clamp(k.x / 0.2, 0.0, 1.0));
        float hr = (b.z * 2.3) * grow;
        float ha = atan2(h.y, h.x);
        float rag = hr * (1.0 + 0.16 * (vnoise(float2(ha * 3.0 + seed * 9.0, k.x * 6.0)) - 0.5) + 0.06 * sin(ha * 11.0 + seed));
        float hd = length(h) - rag;
        hole = 1.0 - sstep(-1.0, 1.0, hd);
        rimGlow = exp(-hd * hd / 6.0) * (1.0 - grow * grow * 0.5);
        if (hr > b.z * 2.25) return col;
    }
    float spin = film * TAU + seed * 3.0;
    float2 sqv = float2(q.x * cos(spin) - q.y * sin(spin), q.x * sin(spin) + q.y * cos(spin));
    float2 loopOff = float2(cos(film * TAU), sin(film * TAU)) * 0.6;
    float swirl = fbm3(sqv * 1.6 + loopOff + seed * 7.0);
    float bands = 0.5 + 0.5 * sin(q.y * 5.0 + swirl * 7.0 + film * TAU);
    float thick = 380.0 + 260.0 * q.y + 230.0 * (bands - 0.5) + 160.0 * (swirl - 0.5);
    thick = max(thick, 40.0);
    float3 irid = thinFilm(thick, cv);
    float fres = 0.07 + 0.93 * pow(1.0 - cv, 2.6);
    float3 behind = wall(p - rel * 0.06 * (1.0 - d2), s.size);
    float3 c = behind * (1.0 - 0.25 * fres) + (irid * 1.2 - 0.1) * fres * 0.9;
    float2 wq = (q - float2(-0.42, -0.46)) / float2(0.2, 0.15);
    float win = exp(-pow(dot(wq * wq, wq * wq), 1.2));
    float bars = 1.0 - 0.6 * exp(-sq(wq.x / 0.12)) - 0.6 * exp(-sq(wq.y / 0.14));
    c += float3(1.0, 1.0, 0.98) * win * bars * 0.85;
    float2 sq2 = (q - float2(0.5, 0.56)) / float2(0.1, 0.06);
    c += float3(1.0, 0.98, 0.95) * exp(-dot(sq2, sq2)) * 0.35;
    float rim = exp(-sq((sqrt(d2) - 0.985) / 0.02));
    c += float3(0.95, 0.97, 1.0) * rim * 0.22;
    c = mix(c, float3(1.0, 0.99, 0.97), clamp(rimGlow, 0.0, 1.0) * 0.8);
    float a = edge * (1.0 - hole) * b.w;
    a = max(a, clamp(rimGlow, 0.0, 1.0) * b.w * edge * 0.9);
    return mix(col, c, a);
}

// The droplets a popped bubble sheds: thrown out from its outline, then falling.
static float3 droplets(float3 col, float2 p, float4 b, float4 k, float seed) {
    if (k.x < 0.0 || k.x > 1.25) return col;
    float2 rel = p - b.xy;
    float reach = b.z * 1.4 + 90.0;
    if (abs(rel.x) > reach || rel.y < -reach || rel.y > reach + 320.0) return col;
    float tapAng = atan2(k.z, k.y);
    for (int i = 0; i < 22; i++) {
        float fi = float(i);
        float h1 = hsh1(fi + seed * 17.0);
        float h2 = hsh1(fi * 1.7 + seed * 5.0 + 3.0);
        float ang = (fi + h1 * 0.8) * TAU / 22.0;
        float away = abs(atan2(sin(ang - tapAng), cos(ang - tapAng))) / 3.14159;
        float t = k.x - (0.02 + 0.1 * away);
        if (t < 0.0 || t > 0.95) continue;
        float2 dir = float2(cos(ang), sin(ang));
        float speed = 70.0 + 110.0 * h2;
        float2 pos = dir * b.z * 0.98 + dir * speed * t + float2(0.0, 560.0 * t * t * 0.5 - 30.0 * t);
        float r = 1.8 + 2.2 * h1;
        float2 dq = rel - pos;
        float dd = length(dq);
        float life = 1.0 - sstep(0.55, 0.95, t);
        float bead = 1.0 - sstep(r - 0.7, r + 0.7, dd);
        float3 dc = mix(float3(0.86, 0.93, 0.98), float3(0.36, 0.48, 0.62), sstep(r * 0.35, r, dd));
        float2 sp = dq + r * 0.35;
        dc += float3(1.0) * exp(-dot(sp, sp) / max(r * r * 0.09, 0.2)) * 0.9;
        col = mix(col, dc, bead * life * 0.95);
    }
    return col;
}

} // namespace soapBubblePop

[[ stitchable ]] half4 SoapBubblePopScene(
    float2 position,
    half4 color,
    float2 size,
    float4 b0,
    float4 b1,
    float4 b2,
    float4 k0,
    float4 k1,
    float4 k2,
    float4 wand,
    float4 tap,
    float film
) {
    using namespace soapBubblePop;
    Scene s;
    s.size = size;
    s.film = film;
    float2 p = position;
    float3 col = wall(p, size);

    // The wand: a glossy coral ring on a handle, holding a film of soap.
    float2 wr = p - wand.xy;
    float wd = length(wr);
    float tube = 4.6;
    float2 hdir = normalize(float2(0.42, 1.0));
    float2 hs = wand.xy + hdir * (wand.z + 2.0);
    float along = dot(p - hs, hdir);
    float side = dot(p - hs, float2(-hdir.y, hdir.x));
    float across = abs(side);
    if (along > 0.0 && along < 160.0) {
        float hw = 4.2;
        float hk = clamp(across / hw, 0.0, 1.0);
        float3 plastic = mix(float3(0.98, 0.52, 0.43), float3(0.78, 0.30, 0.26), hk * hk);
        plastic += float3(0.25) * exp(-sq((side + 1.5) / 1.0));
        col = mix(col, col * 0.86, (1.0 - sstep(hw + 1.0, hw + 7.0, across + 3.0)) * 0.6);
        col = mix(col, plastic, 1.0 - sstep(hw - 0.6, hw + 0.6, across));
    }
    if (wd < wand.z - tube * 0.5 && wand.w > 0.0) {
        float2 fq = wr / wand.z;
        float3 irid = thinFilm(300.0 + 500.0 * fbm3(fq * 2.0 + float2(cos(film * TAU), sin(film * TAU)) * 0.5), 0.92);
        col = mix(col, col * 0.92 + irid * 0.5, 0.55 * wand.w);
        float2 gq = (fq - float2(-0.35, -0.35)) / 0.22;
        col += float3(1.0) * exp(-dot(gq, gq)) * 0.4 * wand.w;
    }
    float ringD = abs(wd - wand.z);
    if (ringD < tube + 6.0) {
        col = mix(col, col * 0.85, (1.0 - sstep(tube, tube + 5.0, ringD)) * 0.4);
        float k = clamp(ringD / tube, 0.0, 1.0);
        float lit = dot(normalize(wr + 0.001), normalize(float2(-0.6, -0.8)));
        float3 plastic = mix(float3(0.99, 0.55, 0.45), float3(0.80, 0.31, 0.27), k * k);
        plastic += float3(0.32) * exp(-sq((wd - wand.z + 1.4 * sign(lit)) / 1.1)) * abs(lit);
        col = mix(col, plastic, 1.0 - sstep(tube - 0.6, tube + 0.6, ringD));
    }

    col = bubble(col, p, b2, k2, 2.7, s);
    col = bubble(col, p, b1, k1, 1.3, s);
    col = bubble(col, p, b0, k0, 0.4, s);
    col = droplets(col, p, b0, k0, 0.4);
    col = droplets(col, p, b1, k1, 1.3);
    col = droplets(col, p, b2, k2, 2.7);

    // Where a finger tapped: a soft ring that spreads and fades.
    if (tap.z >= 0.0 && tap.z < 0.45) {
        float u = 1.0 - tap.z / 0.45;
        float tr = 12.0 + 26.0 * (1.0 - u * u * u);
        float td = abs(length(p - tap.xy) - tr);
        col = mix(col, float3(1.0), exp(-td * td / 5.0) * u * 0.55);
    }

    float2 uv = position / size - 0.5;
    col *= 1.0 - 0.12 * dot(uv, uv);
    col += (hsh(position) - 0.5) / 255.0;
    return half4(half3(clamp(col, 0.0, 1.0)), 1.0h);
}
