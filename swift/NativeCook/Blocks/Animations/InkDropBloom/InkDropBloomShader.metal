#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The whole scene of Ink Drop Bloom in one full-screen pass: a warm studio wall and table, a tall
// glass tumbler of clear water standing on it, and the ink inside. A step-for-step port of the
// React Native SkSL (inkDropShader.ts); see there for the full description. SwiftUI shaders work
// in sRGB, so the Display P3 conversion the Skia version needs is left out. `smoothstep` is
// reimplemented (sstep) and squares are written as products, since Metal leaves reversed edges
// and negative bases to pow undefined.

namespace inkDropBloom {

struct Scene {
    float2 size;
    float4 glass;   // cx, top, bottom, half width at the top
    float4 water;   // surface y, inner floor y, half width at the bottom, wall thickness
    float4 d[4];    // drop: x, y (entry), age (s, < 0 = none), seed
    float3 c[4];    // drop colours (sRGB, full strength)
    float4 falling; // a drop in the air: x, y, radius, alpha
    float3 fallCol;
    float4 press;   // disc y (< 0 = none), rod alpha, squeeze 0..1, ink amount 0..1
    float4 stir;    // x, y, angle (rad), radius
    float flow;
    float3 tint;
};

static inline float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

static inline float sq(float x) { return x * x; }

static inline float hsh(float2 q) {
    return fract(precise::sin(dot(q, float2(127.1, 311.7))) * 43758.5453);
}

static float vnoise(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hsh(i);
    float b = hsh(i + float2(1.0, 0.0));
    float c = hsh(i + float2(0.0, 1.0));
    float d = hsh(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

static float fbm4(float2 q) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * vnoise(q);
        q = float2(q.x * 1.6 - q.y * 1.2, q.x * 1.2 + q.y * 1.6) + 3.1;
        a *= 0.5;
    }
    return s / 0.9375;
}

static float fbm3(float2 q) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        s += a * vnoise(q);
        q = float2(q.x * 1.6 - q.y * 1.2, q.x * 1.2 + q.y * 1.6) + 1.7;
        a *= 0.5;
    }
    return s / 0.875;
}

// Half width of the glass's outer wall at height y.
static float halfAt(float y, thread const Scene &s) {
    float k = clamp((y - s.glass.y) / (s.glass.z - s.glass.y), 0.0, 1.0);
    return mix(s.glass.w, s.water.z + s.water.w, k);
}

// The room behind the glass.
static float3 backdrop(float2 p, thread const Scene &s) {
    float horizon = s.glass.z - 34.0;
    float2 uv = p / s.size;
    float3 wallTop = float3(0.925, 0.900, 0.862);
    float3 wallMid = float3(0.895, 0.862, 0.818);
    float3 wallLow = float3(0.858, 0.818, 0.770);
    float3 col = mix(wallTop, wallMid, sstep(0.0, 0.5, uv.y));
    col = mix(col, wallLow, sstep(0.4, 0.75, p.y / horizon));
    col += float3(0.05, 0.04, 0.03) * exp(-dot(uv - float2(0.2, 0.15), uv - float2(0.2, 0.15)) * 6.0);
    if (p.y > horizon) {
        float k = clamp((p.y - horizon) / (s.size.y - horizon), 0.0, 1.0);
        float3 tableNear = float3(0.905, 0.875, 0.835);
        float3 tableFar = float3(0.835, 0.795, 0.748);
        col = mix(tableFar, tableNear, sstep(0.0, 0.6, k));
        col -= float3(0.05, 0.05, 0.05) * (1.0 - sstep(0.0, 6.0, p.y - horizon));
        float2 sh = (p - float2(s.glass.x + s.glass.w * 0.55, s.glass.z + 4.0)) / float2(s.glass.w * 1.25, 16.0);
        col *= 1.0 - 0.32 * exp(-dot(sh, sh) * 1.6);
        float2 cc = (p - float2(s.glass.x + s.glass.w * 0.9, s.glass.z + 10.0)) / float2(s.glass.w * 0.6, 7.0);
        float caustic = exp(-dot(cc, cc) * 2.0);
        col += mix(float3(0.10, 0.09, 0.07), s.tint * 0.22, 0.6) * caustic;
    }
    col *= 1.0 + (hsh(floor(p * 1.5)) - 0.5) * 0.035;
    return col;
}

// A vortex-ring cap of radius r at q (relative to its centre).
static float ring(float2 q, float r) {
    float side = abs(q.x) / (r * 1.3);
    float capY = q.y + r * 0.38 * sstep(0.15, 0.9, side);
    float cap = 1.0 - length(float2(q.x / (r * 1.3), capY / (r * 0.7)));
    float lobe = exp(-sq((side - 0.66) / 0.3));
    float band = clamp(1.0 - abs(capY + r * 0.1) / (r * 0.85), -1.0, 1.0);
    return max(cap, mix(-1.0, band * 0.55, lobe));
}

// One drop's plume density at p (already twisted by the stir).
static float plume(float2 p, float4 d, float2 warp, thread const Scene &s) {
    float a = d.z;
    if (a <= 0.0) return 0.0;
    float room = max(s.water.y - d.y, 1.0);
    float r = 9.0 + 30.0 * sqrt(a);
    float depth = room * 0.62 * (1.0 - exp(-a / 2.0)) + 10.0 * a / (1.0 + a);
    float2 head = float2(d.x, d.y + depth + 6.0);
    float2 q = p - head;
    if (abs(q.x) > r * 2.0 + 40.0 || q.y > r * 2.2 + 40.0 || p.y < d.y - 16.0) return 0.0;
    float bill = 10.0 + 9.0 * a;
    float2 w = (warp - 0.5) * bill;
    float2 qw = q + w;
    float shape = ring(qw, r);
    float kid = sstep(0.9, 2.6, a);
    float rk = r * 0.48;
    float drop = r * (0.55 + 0.75 * kid);
    shape = max(shape, mix(-1.0, ring(qw - float2(-r * 0.62, drop), rk), kid));
    shape = max(shape, mix(-1.0, ring(qw - float2(r * 0.58, drop * 0.9), rk), kid));
    float kid2 = sstep(2.2, 4.4, a);
    float rg = r * 0.26;
    shape = max(shape, mix(-1.0, ring(qw - float2(-r * 0.9, drop + r * 0.75 * kid2), rg), kid2));
    shape = max(shape, mix(-1.0, ring(qw - float2(r * 0.95, drop * 0.9 + r * 0.7 * kid2), rg), kid2));
    float t = clamp((p.y - d.y) / max(depth + 6.0, 1.0), 0.0, 1.0);
    float sw = 2.0 + r * 0.13 * t;
    float alongStem = step(d.y - 2.0, p.y) * step(p.y, head.y + 4.0);
    float stem = mix(-1.0, (1.0 - abs(qw.x) / sw) * (0.45 + 0.4 * t), alongStem);
    shape = max(shape, stem);
    float2 nq = (q + w * 1.4) / 20.0 + float2(d.w * 37.0, -a * 0.3);
    float n = fbm4(nq);
    float soft = sstep(0.08, 0.7, shape + (n - 0.5) * 0.8);
    float fil = 1.0 - abs(2.0 * fbm3(nq * 1.7 + 4.0) - 1.0);
    float fil2 = fil * fil;
    fil = fil2 * fil2 * fil2 * sstep(0.0, 0.3, soft);
    float rim = sstep(0.05, 0.3, soft) * (1.0 - sstep(0.3, 0.75, soft));
    float conc = 1.9 / (1.0 + 0.36 * a);
    float2 hq = q / float2(r * 2.0, r * 2.8);
    float haze = 0.22 * sstep(0.6, 4.0, a) * exp(-dot(hq, hq));
    return (soft * soft * 0.8 + rim * 0.45 + fil * 0.6) * conc + haze;
}

static float3 absorbOf(float3 c) {
    return -log(max(c, float3(0.02)));
}

static float3 scene(float2 pos, thread const Scene &s) {
    float2 p = pos;
    float3 col = backdrop(p, s);

    float cx = s.glass.x;
    float top = s.glass.y;
    float bot = s.glass.z;
    float hw = halfAt(p.y, s);
    float dx = p.x - cx;
    float rimRy = 7.0;
    float rimK = sqrt(max(0.0, 1.0 - sq(dx / s.glass.w)));
    float rimYBack = top - rimRy * rimK;
    float corner = 16.0;
    float u = abs(dx) - (hw - corner);
    float v = p.y - (bot - corner);
    float outer = (u > 0.0 && v > 0.0) ? length(float2(u, v)) - corner : max(abs(dx) - hw, p.y - bot);
    float inGlass = (1.0 - sstep(-0.8, 0.8, outer)) * sstep(rimYBack - 0.8, rimYBack + 0.8, p.y);

    if (inGlass > 0.0) {
        float wall = s.water.w;
        float ihw = hw - wall;
        float floorY = s.water.y;
        float inner = max(abs(dx) - ihw, p.y - floorY);
        float inside = 1.0 - sstep(-0.8, 0.8, inner);
        float surfK = sqrt(max(0.0, 1.0 - sq(dx / ihw)));
        float surfY = s.water.x + 6.0 * surfK;
        float inWater = inside * sstep(surfY - 0.8, surfY + 0.8, p.y);
        float xn = clamp(dx / hw, -1.0, 1.0);
        float x2 = xn * xn;
        float edge = x2 * x2 * x2;

        float squeeze = mix(0.97, 0.66, inWater);
        float2 sp = float2(cx + dx * squeeze, p.y + inWater * 10.0);
        float3 seen = backdrop(sp, s);
        seen = mix(seen, seen * float3(0.95, 0.99, 1.0) * (1.0 + 0.05 * (1.0 - abs(xn))), inWater);

        if (inWater > 0.0) {
            float2 q = p;
            float2 sd = q - s.stir.xy;
            float sf = s.stir.z * exp(-dot(sd, sd) / max(s.stir.w * s.stir.w, 1.0));
            float cs = cos(sf);
            float sn = sin(sf);
            q = s.stir.xy + float2(cs * sd.x - sn * sd.y, sn * sd.x + cs * sd.y);
            float keep = 1.0;
            if (s.press.x > 0.0) {
                float pd = max(s.press.x, s.water.x);
                keep = sstep(pd - 1.0, pd + 6.0, q.y);
                float span = max(floorY - pd, 1.0);
                q.y = s.water.x + (q.y - pd) * (floorY - s.water.x) / span;
                keep *= 1.0 - s.press.z * s.press.z;
            }
            float2 warp = float2(fbm3(q / 70.0 + float2(s.flow * 0.07, 0.0)),
                                 fbm3(q / 70.0 + float2(5.2, -s.flow * 0.09)));
            float3 absorb = float3(0.0);
            for (int i = 0; i < 4; i++) {
                absorb += plume(q, s.d[i], warp, s) * absorbOf(s.c[i]);
            }
            seen *= exp(-absorb * keep * inWater * 0.9 * s.press.w);
        }

        float men = exp(-sq((p.y - surfY) / 1.1)) * inside;
        float menBack = exp(-sq((p.y - (s.water.x - 6.0 * surfK)) / 1.0)) * inside;
        seen = mix(seen, float3(0.99, 0.99, 0.985), men * 0.75);
        seen = mix(seen, float3(0.70, 0.72, 0.73), menBack * 0.35);
        float underSurf = sstep(0.0, 14.0, p.y - surfY) * (1.0 - sstep(14.0, 40.0, p.y - surfY));
        seen *= 1.0 - 0.04 * underSurf * inside;

        float inWall = 1.0 - inside;
        float3 glassTint = float3(0.80, 0.86, 0.85);
        seen = mix(seen, seen * glassTint, inWall * (0.35 + 0.5 * edge));
        seen = mix(seen, float3(0.40, 0.45, 0.45), edge * 0.35);
        float streakA = exp(-sq((xn + 0.62) / 0.06)) * sstep(top, top + 40.0, p.y) * (1.0 - sstep(bot - 60.0, bot, p.y));
        float streakB = exp(-sq((xn - 0.78) / 0.018)) * sstep(top + 20.0, top + 80.0, p.y) * (1.0 - sstep(bot - 90.0, bot - 30.0, p.y));
        seen += float3(1.0, 1.0, 0.99) * (streakA * 0.28 + streakB * 0.55);
        float inBase = sstep(floorY - 1.0, floorY + 1.0, p.y);
        float arc = exp(-sq((p.y - (floorY + 8.0 + 5.0 * xn * xn)) / 1.6)) * (1.0 - edge);
        seen = mix(seen, seen * float3(0.86, 0.92, 0.91), inBase * 0.5);
        seen += mix(float3(0.25, 0.25, 0.23), s.tint * 0.5, 0.5) * arc * inBase;
        seen *= 1.0 - 0.35 * exp(-sq((p.y - (bot - 1.5)) / 1.2));

        col = mix(col, seen, inGlass);
    }

    // The rim: a thin bright ellipse, its back half dimmer.
    float rr = (p.y - top) / rimRy;
    float re = abs(length(float2(dx / s.glass.w, rr)) - 1.0) * s.glass.w;
    float rim = exp(-re * re / 1.4);
    col = mix(col, float3(0.98, 0.99, 0.985), rim * (p.y > top ? 0.8 : 0.45));

    // The disc of the press and its rod.
    if (s.press.x > 0.0) {
        float pyy = s.press.x;
        float phw = halfAt(pyy, s) - s.water.w - 1.0;
        float rodHw = 3.2;
        float rodTop = s.glass.y - 44.0;
        if (abs(dx) < rodHw + 1.0 && p.y < pyy && p.y > rodTop) {
            float k = abs(dx) / rodHw;
            float3 steel = mix(float3(0.80, 0.81, 0.82), float3(0.42, 0.44, 0.46), k * k);
            steel += float3(0.18) * exp(-sq((dx + 1.2) / 0.7));
            float a = (1.0 - sstep(rodHw - 0.6, rodHw + 0.6, abs(dx))) * s.press.y * sstep(rodTop, rodTop + 36.0, p.y);
            col = mix(col, steel, a);
        }
        float e = length(float2(dx / phw, (p.y - pyy) / 6.0));
        if (e < 1.08) {
            float mesh = 0.5 + 0.5 * sin(dx * 2.2) * sin((p.y - pyy) * 5.0);
            float3 discCol = mix(float3(0.55, 0.57, 0.58), float3(0.74, 0.75, 0.76), mesh);
            discCol = mix(discCol, float3(0.95, 0.95, 0.94), exp(-sq((e - 0.96) / 0.05)));
            float a = 1.0 - sstep(1.0, 1.06, e);
            col = mix(col, discCol, a * 0.85 * s.press.y);
        }
    }

    // A drop in the air: a bead with a highlight, stretched by its speed.
    if (s.falling.w > 0.0) {
        float2 fq = (p - s.falling.xy) / float2(s.falling.z, s.falling.z * 1.35);
        fq.y += 0.25;
        float fd = length(fq);
        float a = (1.0 - sstep(0.9, 1.05, fd)) * s.falling.w;
        float3 bead = s.fallCol * (0.75 + 0.25 * (1.0 - fd));
        float2 hl = fq - float2(-0.35, -0.35);
        bead += float3(0.9) * exp(-dot(hl, hl) * 18.0);
        col = mix(col, bead, a);
    }

    // A soft vignette on the warm room, then dither.
    float2 uv = pos / s.size - 0.5;
    col *= 1.0 - 0.16 * dot(uv, uv);
    col += (hsh(pos) - 0.5) / 255.0;
    return clamp(col, 0.0, 1.0);
}

} // namespace inkDropBloom

[[ stitchable ]] half4 InkDropBloomScene(
    float2 position,
    half4 color,
    float2 size,
    float4 glass,
    float4 water,
    float4 d0,
    float4 d1,
    float4 d2,
    float4 d3,
    float3 c0,
    float3 c1,
    float3 c2,
    float3 c3,
    float4 falling,
    float3 fallCol,
    float4 press,
    float4 stir,
    float flow,
    float3 tint
) {
    inkDropBloom::Scene s;
    s.size = size;
    s.glass = glass;
    s.water = water;
    s.d[0] = d0;
    s.d[1] = d1;
    s.d[2] = d2;
    s.d[3] = d3;
    s.c[0] = c0;
    s.c[1] = c1;
    s.c[2] = c2;
    s.c[3] = c3;
    s.falling = falling;
    s.fallCol = fallCol;
    s.press = press;
    s.stir = stir;
    s.flow = flow;
    s.tint = tint;
    return half4(half3(inkDropBloom::scene(position, s)), 1.0h);
}
