// Coral Growth Streak — the reef, in two passes over one triangle.
//
//  1. coralGrowthStreakStep, on a 256 × 256 rgba16Float texture, ping-ponged: r is u (the food), g is
//     v (the coral). One Gray-Scott step on the 9-point kernel:
//       u' = u + ∇²u − u v² + F (1 − u)
//       v' = v + 0.5 ∇²v + u v² − (F + k) v
//     with the neighbours clamped at the border. Outside the open radius k is raised, so the coral
//     fills the room it has been given and stops. Up to eight seeds are stamped in.
//  2. coralGrowthStreakView, on the round canvas: v becomes a height, lit from the upper left, with a
//     short shadow on the sea bed. `level` dissolves the coral from the rim inwards and `bloom` sends
//     one band of light out. With level 0 the result is the bare sea bed whatever the field holds,
//     which is what makes the loop's cut invisible.
//
// coralGrowthStreakStill is the reduced-motion reef: no simulation, a labyrinth of seven plane waves
// with the same colours and light.

#include <metal_stdlib>
using namespace metal;

struct CoralGrowthStreakStepU {
    float4 cfg;       // feed, kill, grid, seed radius (cells)
    float4 wall;      // open radius (cells), wall kill, wall half width (cells), -
    float4 seeds[8];  // x, y (0…1), strength (0 = unused), -
};

struct CoralGrowthStreakViewU {
    float4 canvas;    // width, height (px), grid, pixel ratio
    float4 anim;      // level 0…1, bloom 0…1, phase 0…1 (one turn a loop), open radius (still only)
};

struct CoralGrowthStreakVaryings {
    float4 position [[position]];
};

vertex CoralGrowthStreakVaryings coralGrowthStreakVertex(uint i [[vertex_id]]) {
    float2 p = float2(float((i << 1u) & 2u), float(i & 2u));
    CoralGrowthStreakVaryings out;
    out.position = float4(p * 2.0 - 1.0, 0.0, 1.0);
    return out;
}

static float2 cgsCell(texture2d<float> field, int2 p, int2 hi) {
    return field.read(uint2(clamp(p, int2(0), hi))).rg;
}

fragment float4 coralGrowthStreakStep(CoralGrowthStreakVaryings in [[stage_in]],
                                      constant CoralGrowthStreakStepU &S [[buffer(0)]],
                                      texture2d<float> field [[texture(0)]]) {
    int2 size = int2(int(S.cfg.z));
    int2 hi = size - int2(1);
    int2 p = clamp(int2(in.position.xy), int2(0), hi);
    float2 c = cgsCell(field, p, hi);
    float2 edges = cgsCell(field, p + int2(-1, 0), hi) + cgsCell(field, p + int2(1, 0), hi) + cgsCell(field, p + int2(0, -1), hi) + cgsCell(field, p + int2(0, 1), hi);
    float2 corners = cgsCell(field, p + int2(-1, -1), hi) + cgsCell(field, p + int2(1, -1), hi) + cgsCell(field, p + int2(-1, 1), hi) + cgsCell(field, p + int2(1, 1), hi);
    float2 lap = 0.2 * edges + 0.05 * corners - c;
    float2 centre = float2(p) + float2(0.5);
    float far = length(centre - float2(S.cfg.z * 0.5));
    float kill = S.cfg.y + S.wall.y * smoothstep(S.wall.x - S.wall.z, S.wall.x + S.wall.z, far);
    float uvv = c.x * c.y * c.y;
    float u = c.x + 1.0 * lap.x - uvv + S.cfg.x * (1.0 - c.x);
    float v = c.y + 0.5 * lap.y + uvv - (S.cfg.x + kill) * c.y;
    for (int i = 0; i < 8; i++) {
        float4 seed = S.seeds[i];
        if (seed.z != 0.0) {
            float2 q = centre - seed.xy * S.cfg.z;
            float e = seed.z * exp(-dot(q, q) / (S.cfg.w * S.cfg.w));
            v += e;
            u -= 0.5 * e;
        }
    }
    return float4(clamp(u, 0.0, 1.0), clamp(v, 0.0, 1.0), 0.0, 1.0);
}

static float3 cgsLinear(float3 c) { return pow(c, float3(2.2)); }
static float cgsGrain(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
static float cgsHeight(texture2d<float> field, sampler samp, float2 uv) {
    return smoothstep(0.06, 0.32, field.sample(samp, uv, level(0.0)).g);
}

/// the sea bed, the coral, the bloom and the window, from the height and its light
static float4 cgsShade(float2 d, float r, float hc, float2 slope, float shadowHeight, float grain, float4 anim, float width) {
    float3 L = normalize(float3(-0.45, -0.6, 0.66));
    float3 H = normalize(L + float3(0.0, 0.0, 1.0));
    float3 n = normalize(float3(-slope * 2.4, 1.0));
    float dif = max(dot(n, L), 0.0);
    float spec = pow(max(dot(n, H), 0.0), 40.0);
    float shadow = shadowHeight * (1.0 - hc);

    // the sea bed: deep teal, lighter at the centre, a slow caustic and a few grains of sand
    float ph = anim.z * 6.2831853;
    float2 q = d * 3.2;
    float c1 = sin(q.x * 1.7 + sin(q.y * 2.3 + ph) * 1.2 + ph);
    float c2 = sin(q.y * 2.1 - sin(q.x * 1.9 - ph) * 1.3 + 2.0 * ph);
    float caustic = pow(0.5 + 0.5 * c1 * c2, 3.0);
    float3 bed = mix(cgsLinear(float3(0.075, 0.300, 0.340)), cgsLinear(float3(0.030, 0.170, 0.215)), smoothstep(0.0, 1.0, r));
    bed += caustic * 0.022 * float3(0.5, 1.0, 0.95);
    bed += step(0.965, grain) * 0.02 * float3(1.0, 0.9, 0.7);
    bed *= 1.0 - 0.55 * shadow;

    // the coral: rose at the centre, orange at the rim, plum on the flanks and sand on the ridges
    float3 body = mix(cgsLinear(float3(0.900, 0.300, 0.370)), cgsLinear(float3(1.000, 0.530, 0.360)), smoothstep(0.1, 1.0, r));
    body = mix(body * float3(0.34, 0.22, 0.42), body, smoothstep(0.15, 0.75, hc));
    body = mix(body, cgsLinear(float3(1.000, 0.880, 0.720)), smoothstep(0.82, 1.0, hc) * 0.3);
    float3 coral = body * (0.42 + 0.74 * dif) + spec * 0.3 * float3(1.0, 0.95, 0.85);
    float cover = smoothstep(0.08, 0.3, hc);
    float3 col = mix(bed, coral, cover);

    // day seven: one band of light from the centre to the rim
    float off = (r - (anim.y * 1.5 - 0.25)) / 0.17;
    float band = exp(-off * off) * sin(3.14159265 * anim.y);
    col += band * (cover * float3(1.0, 0.62, 0.45) * 0.5 + float3(0.01, 0.035, 0.035));

    // the window: darker towards the rim, cut to a circle
    float rim = smoothstep(0.5, 1.0, r);
    col *= 1.0 - 0.4 * rim * rim;
    float a = clamp((1.0 - r) * width * 0.5, 0.0, 1.0);
    return float4(pow(clamp(col, float3(0.0), float3(1.0)), float3(1.0 / 2.2)) * a, a);
}

fragment float4 coralGrowthStreakView(CoralGrowthStreakVaryings in [[stage_in]],
                                      constant CoralGrowthStreakViewU &V [[buffer(0)]],
                                      texture2d<float> field [[texture(0)]],
                                      sampler samp [[sampler(0)]]) {
    float2 uv = in.position.xy / V.canvas.xy;
    float2 d = (uv - float2(0.5)) * 2.0;
    float r = length(d);
    float cell = 1.0 / V.canvas.z;
    // the dissolve: gone at the rim first, the centre last
    float gate = smoothstep(r - 0.3, r, V.anim.x * 1.6 - 0.3);
    float hc = cgsHeight(field, samp, uv) * gate;
    float hl = cgsHeight(field, samp, uv - float2(cell, 0.0)) * gate;
    float hr = cgsHeight(field, samp, uv + float2(cell, 0.0)) * gate;
    float hu = cgsHeight(field, samp, uv - float2(0.0, cell)) * gate;
    float hd = cgsHeight(field, samp, uv + float2(0.0, cell)) * gate;
    float2 slope = float2(hr - hl, hd - hu) * 0.5;
    float shadowHeight = cgsHeight(field, samp, uv + float2(-2.0, -3.0) * cell) * gate;
    float grain = cgsGrain(floor(in.position.xy / (V.canvas.w * 1.5)));
    return cgsShade(d, r, hc, slope, shadowHeight, grain, V.anim, V.canvas.x);
}

static float cgsLabyrinth(float2 d) {
    float2 p = d + 0.03 * float2(sin(d.y * 5.0), sin(d.x * 4.3));
    float sum = 0.0;
    for (int i = 0; i < 7; i++) {
        float a = float(i) * 2.399 + 0.5;
        sum += cos(38.0 * dot(p, float2(cos(a), sin(a))) + float(i) * 1.7);
    }
    return smoothstep(-0.1, 0.22, sum / 7.0);
}

static float cgsStillHeight(float2 d, float4 anim) {
    float r = length(d) + 0.05 * sin(d.x * 9.0 + 1.3) * sin(d.y * 8.0 - 0.7);
    float m = 1.0 - smoothstep(anim.w - 0.14, anim.w, r);
    float far = length(d);
    return cgsLabyrinth(d) * m * smoothstep(far - 0.3, far, anim.x * 1.6 - 0.3);
}

fragment float4 coralGrowthStreakStill(CoralGrowthStreakVaryings in [[stage_in]],
                                       constant CoralGrowthStreakViewU &V [[buffer(0)]]) {
    float2 uv = in.position.xy / V.canvas.xy;
    float2 d = (uv - float2(0.5)) * 2.0;
    float e = 2.0 / 256.0;
    float hc = cgsStillHeight(d, V.anim);
    float2 slope = float2(cgsStillHeight(d + float2(e, 0.0), V.anim) - hc, cgsStillHeight(d + float2(0.0, e), V.anim) - hc);
    float shadowHeight = cgsStillHeight(d + float2(-2.0, -3.0) * e, V.anim);
    float grain = cgsGrain(floor(in.position.xy / (V.canvas.w * 1.5)));
    return cgsShade(d, length(d), hc, slope, shadowHeight, grain, V.anim, V.canvas.x);
}
