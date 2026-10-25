#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// The night scene in one pass: a dusk sky with stars, drifting fog, a sea of layered sine waves,
// the lighthouse beam lighting the fog, and the onboarding text that exists only where the beam
// falls (plus a 0.8 s afterglow, from five earlier beam angles). A step-for-step port of the React
// Native SkSL (lighthouseShader.ts), minus its Display P3 conversion.
//
// It is a layer effect: the layer is the text of the three pages, already slid to the current page
// by SwiftUI, and only its alpha is read. Metal's pow is undefined for a negative base, so squares
// are written as x * x.

namespace lighthouseBeamWelcomeShader {

constant float TAU = 6.2831853;
constant float E = 0.3;
constant float TILT = 0.12;
constant float TAN_BETA = 0.56;

struct Scene {
    float2 size;
    float unit;
    float2 lamp;
    float horizon;
};

static inline float sq(float x) { return x * x; }

static inline float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

static inline float h21(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

static float vnoise(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    float a = h21(i);
    float b = h21(i + float2(1.0, 0.0));
    float c = h21(i + float2(0.0, 1.0));
    float d = h21(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

static float fbm(float2 q) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(q);
        q = q * 2.03 + float2(17.1, 9.3);
        a *= 0.5;
    }
    return v;
}

// The beam's light at p for azimuth phi: x = the cone (0..1, soft edge), y = with falloff and
// facing, for the glow in the fog.
static float2 beamAt(float2 p, float phi, thread const Scene &s) {
    float c = cos(phi);
    float sn = sin(phi);
    float2 dir = float2(c, E * sn + TILT);
    float lam = length(dir);
    dir /= lam;
    float2 d = p - s.lamp;
    float along = dot(d, dir);
    float across = abs(d.x * dir.y - d.y * dir.x);
    float spreadK = TAN_BETA / max(lam, 0.28);
    float halfW = 14.0 * s.unit + max(along, 0.0) * spreadK;
    float k = across / halfW;
    float cone = (1.0 - sstep(0.5, 1.08, k)) * sstep(-6.0 * s.unit, 10.0 * s.unit, along);
    float face = sn > 0.0 ? 1.0 + 1.3 * pow(sn, 6.0) : 1.0 + 0.5 * sn;
    float reach = exp(-max(along, 0.0) / (560.0 * s.unit * max(lam, 0.32)));
    return float2(cone * reach * clamp(face, 0.5, 1.0), cone * reach * face);
}

static float3 sky(float2 p, thread const Scene &s) {
    float y = clamp(p.y / s.horizon, 0.0, 1.0);
    float3 top = float3(0.039, 0.078, 0.188);
    float3 mid = float3(0.137, 0.192, 0.369);
    float3 low = float3(0.325, 0.251, 0.478);
    float3 col = y < 0.62 ? mix(top, mid, y / 0.62) : mix(mid, low, (y - 0.62) / 0.38);
    // A thin warm band where the sun went down.
    col += float3(0.30, 0.16, 0.20) * exp(-sq((s.horizon - p.y) / (18.0 * s.unit))) * 0.45;
    return col;
}

} // namespace lighthouseBeamWelcomeShader

using namespace lighthouseBeamWelcomeShader;

[[ stitchable ]] half4 lighthouseBeamWelcome(
    float2 p,
    SwiftUI::Layer layer,
    float2 size,
    float unit,
    float2 lamp,
    float horizon,
    float4 phiA,    // φ now, 0.1 s, 0.22 s, 0.36 s ago
    float2 phiB,    // φ 0.55 s and 0.8 s ago
    float2 clk      // ambient clock, loop length
) {
    Scene s = { size, unit, lamp, horizon };
    float u = unit;
    float w = TAU * clk.x / clk.y;
    float2 orbit = float2(cos(w), sin(w));
    float3 col;
    float fogBase;

    float2 now = beamAt(p, phiA.x, s);

    if (p.y < horizon) {
        col = sky(p, s);
        // Stars in the upper sky, twinkling at whole multiples of the loop.
        float2 cellId = floor(p / (26.0 * u));
        float hs = h21(cellId);
        if (hs > 0.78) {
            float2 sp = (cellId + 0.2 + 0.6 * float2(h21(cellId + 4.0), h21(cellId + 9.0))) * 26.0 * u;
            float2 ds = p - sp;
            float tw = 0.55 + 0.45 * sin(w * (2.0 + floor(hs * 5.0)) + hs * 40.0);
            float fadeLow = 1.0 - sstep(horizon * 0.35, horizon * 0.85, p.y);
            col += float3(0.85, 0.88, 1.0) * exp(-dot(ds, ds) / (2.0 * sq(0.7 * u))) * tw * fadeLow * 0.9;
        }
        fogBase = 0.35 + 0.65 * sstep(horizon * 0.15, horizon, p.y);
    } else {
        // The sea: darker with depth, wave crests in layers, finer near the horizon.
        float depth = (p.y - horizon) / (size.y - horizon);
        col = mix(float3(0.165, 0.161, 0.329), float3(0.031, 0.055, 0.137), pow(max(depth, 0.0), 0.6));
        float persp = 0.04 + depth;
        float crest = 0.0;
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            float freq = (0.045 + 0.02 * fi) / persp;
            float wave = sin(p.x * freq * 0.12 / unit + w * (2.0 + fi) + fi * 1.7) * 0.5
                       + sin(p.x * freq * 0.05 / unit - w * (1.0 + fi) + fi * 4.1) * 0.5;
            float rows = p.y / (persp * 22.0 * u) + wave * 0.6 + fi * 0.25;
            float breakUp = sstep(0.35, 0.75, vnoise(float2(p.x / (persp * 40.0 * u), rows * 0.5) + orbit * (1.0 + fi)));
            crest += pow(abs(sin(rows * 3.14159)), 14.0) * (0.30 - 0.05 * fi) * breakUp;
        }
        col += float3(0.42, 0.40, 0.66) * crest * (0.35 + 0.65 * (1.0 - depth));
        // The beam's reflection: glitter under where the beam lies over the water.
        float2 mirror = float2(p.x, 2.0 * horizon - p.y);
        float2 refl = beamAt(mirror, phiA.x, s);
        float glint = pow(vnoise(float2(p.x * 0.35 / u, p.y * 1.4 / u) + orbit * 3.0), 6.0);
        col += float3(1.0, 0.86, 0.58) * refl.y * (0.10 + glint * 2.2) * (1.0 - depth * 0.7) * crest * 2.0;
        col += float3(1.0, 0.86, 0.58) * refl.y * 0.05 * (1.0 - depth);
        // Foam where the rocks meet the sea.
        float2 rock = (p - float2(lamp.x, horizon + 24.0 * u)) / float2(70.0 * u, 9.0 * u);
        float ring = abs(length(rock) - 1.0);
        float foam = exp(-ring * 6.0) * sstep(0.35, 0.75, vnoise(p * 0.2 / u + orbit * 2.0));
        col += float3(0.55, 0.56, 0.75) * foam * 0.35;
        fogBase = 0.55 * (1.0 - sstep(0.0, 0.35, depth));
    }

    // Fog: looping noise, densest near the horizon.
    float2 q = p / (150.0 * u) + orbit * 0.7;
    float fog = fbm(q + float2(fbm(q * 0.7 + 3.1), 0.0) * 0.8);
    float density = clamp(fogBase * sstep(0.15, 0.95, fog) * 1.2, 0.0, 1.0);
    col = mix(col, float3(0.30, 0.31, 0.50), density * 0.30);

    // The beam in the fog.
    float3 warm = float3(1.0, 0.90, 0.68);
    float lit = now.y * (0.30 + 0.70 * density);
    col += warm * lit * 0.5;

    // The text: faint in the fog, lit by the beam and its afterglow.
    // The layer carries a 1/255 alpha floor everywhere (so the effect covers the whole screen);
    // only the alpha above it is ink.
    float ink = clamp((float(layer.sample(p).a) - 0.0045) / (1.0 - 0.0045), 0.0, 1.0);
    if (ink > 0.002) {
        float trail = beamAt(p, phiA.y, s).x * 0.9;
        trail = max(trail, beamAt(p, phiA.z, s).x * 0.66);
        trail = max(trail, beamAt(p, phiA.w, s).x * 0.46);
        trail = max(trail, beamAt(p, phiB.x, s).x * 0.26);
        trail = max(trail, beamAt(p, phiB.y, s).x * 0.10);
        float reveal = sstep(0.0, 0.55, max(now.x, trail));
        float3 inkCol = mix(float3(0.62, 0.64, 0.82), float3(1.0, 0.95, 0.84), reveal);
        col = mix(col, inkCol, ink * (0.035 + 0.965 * reveal));
    }

    // Vignette, grain, display.
    float2 vg = p / size - 0.5;
    col *= 1.0 - 0.6 * dot(vg * float2(1.0, 0.8), vg * float2(1.0, 0.8));
    col += (h21(floor(p * 1.7)) - 0.5) * 0.03;
    col = clamp(col, 0.0, 1.0);
    return half4(half3(col), 1.0h);
}
