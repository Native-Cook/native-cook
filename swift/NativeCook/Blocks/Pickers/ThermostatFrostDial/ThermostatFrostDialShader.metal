#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Thermostat Frost Dial in one full-screen pass: a slatted wall, a round thermostat on it — a matte
// ceramic ring that turns with the set point around a fixed face of tick marks — and the weather the
// set point brings (frost when colder, melt beads, amber glow and heat shimmer when warmer). A
// step-for-step port of the React Native SkSL (thermostatShader.ts); see there for the full
// description. SwiftUI shaders work in sRGB, so the P3 conversion is left out. GLSL's
// `smoothstep` is reimplemented (several calls have reversed edges, which Metal leaves undefined)
// and squares are products.

namespace thermostatFrostDial {

constant float TAU = 6.2831853;
constant float START = 2.3561945;   // 135°: where 10° sits
constant float ARC = 4.712389;      // 270°

struct Scene {
    float2 size;
    float4 dial;   // cx, cy, outer radius, inner radius
    float4 ring;   // ring turn (rad), set point angle (rad), frost 0..1, wet 0..1
    float4 env;    // cold 0..1, warm 0..1, loop phase 0..1, 0
    float4 lit;    // lit ticks from angle, to angle (rad), heating 0/1, 0
};

static inline float sstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

static inline float sq(float x) { return x * x; }

static inline float hsh(float2 q) { return fract(precise::sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

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

// The wall: vertical oak slats with dark grooves, warm grey at rest, steel blue when cold,
// amber-lit from below when warm.
static float3 wallAt(float2 p, thread const Scene &s) {
    float2 uv = p / s.size;
    float slat = 26.0;
    float sx = fract(p.x / slat);
    float groove = sstep(0.0, 0.06, sx) * sstep(1.0, 0.94, sx);
    float id = floor(p.x / slat);
    float grain = vnoise(float2(id * 7.0 + p.x * 0.15, p.y * 0.012)) * 0.6 + vnoise(float2(id * 3.0, p.y * 0.08)) * 0.4;
    float3 neutral = float3(0.235, 0.215, 0.196);
    float3 coldC = float3(0.105, 0.155, 0.225);
    float3 warmC = float3(0.30, 0.18, 0.10);
    float3 base = mix(neutral, coldC, s.env.x);
    base = mix(base, warmC, s.env.y);
    float3 col = base * (0.86 + 0.22 * grain) * (0.55 + 0.45 * groove);
    col *= 1.0 + 0.08 * (hsh(float2(id, 3.0)) - 0.5);
    float2 d = (p - s.dial.xy) / (s.dial.z * 2.4);
    col += mix(float3(0.06, 0.055, 0.05), float3(0.05, 0.08, 0.12), s.env.x) * exp(-dot(d, d) * 1.5);
    float glow = exp(-sq((uv.y - 1.05) / 0.55)) + 0.6 * exp(-dot(d, d) * 0.9);
    col += float3(0.42, 0.18, 0.04) * glow * s.env.y * 0.55;
    col += float3(0.04, 0.07, 0.12) * (1.0 - uv.y) * s.env.x;
    col *= 1.0 - 0.45 * dot(uv - 0.5, uv - 0.5);
    return col;
}

static float3 scene(float2 pos, thread const Scene &s) {
    float2 p = pos;
    float2 rel = p - s.dial.xy;
    float r = length(rel);
    float Ro = s.dial.z;
    float Ri = s.dial.w;

    // Heat shimmer: above the dial the air wobbles, more the warmer it is, rising.
    float above = (1.0 - sstep(s.dial.y - Ro * 0.2, s.dial.y + Ro * 0.2, p.y)) * exp(-sq(rel.x / (Ro * 1.25)));
    float ph = s.env.z * TAU;
    float wob = sin(p.y * 0.085 + ph * 6.0 + 2.0 * sin(p.x * 0.045 + ph * 2.0)) + 0.5 * sin(p.y * 0.21 + ph * 9.0 + p.x * 0.03);
    float2 sp = p + float2(wob * 3.2, wob * 1.2) * s.env.y * above;
    float3 col = wallAt(sp, s);

    // Shadows of the thermostat on the wall: a tight contact one and a soft ambient one, low.
    float sd = length(rel - float2(0.0, 10.0)) - Ro;
    col *= 1.0 - 0.45 * (1.0 - sstep(-4.0, 34.0, sd));
    float cd = r - Ro;
    col *= 1.0 - 0.35 * (1.0 - sstep(-1.0, 4.0, cd));

    float ang = atan2(rel.y, rel.x);
    float3 light = normalize(float3(-0.45, -0.7, 0.55));

    if (r < Ro + 1.0) {
        float aa = 1.0 - sstep(Ro - 0.8, Ro + 0.8, r);
        float3 c;
        if (r > Ri - 0.5) {
            // The ring: matte ceramic, a rounded profile (lit like a torus), fine grip grooves.
            float t = (r - Ri) / (Ro - Ri);
            float bulge = (t - 0.5) * 2.0;
            float3 n = normalize(float3(rel / r * bulge * 0.9, sqrt(max(1.0 - bulge * bulge * 0.81, 0.05))));
            float diff = max(dot(n, light), 0.0);
            float ra = ang - s.ring.x;
            float groove = 0.5 + 0.5 * cos(ra * 90.0);
            float3 ceramic = mix(float3(0.86, 0.84, 0.80), float3(0.93, 0.92, 0.89), diff);
            ceramic *= 0.55 + 0.5 * diff;
            ceramic *= 1.0 - 0.05 * groove * sstep(0.25, 0.55, t) * sstep(0.95, 0.7, t);
            ceramic += float3(0.08) * pow(max(dot(reflect(-light, n), float3(0.0, 0.0, 1.0)), 0.0), 18.0);
            ceramic *= 1.0 + (hsh(floor(p * 2.0)) - 0.5) * 0.04;
            // The notch that marks the set point.
            float2 np = s.dial.xy + float2(cos(s.ring.y), sin(s.ring.y)) * (Ri + (Ro - Ri) * 0.5);
            float nd = length(p - np);
            float notch = 1.0 - sstep(3.4, 4.6, nd);
            ceramic = mix(ceramic, float3(0.30, 0.29, 0.28), notch);
            float2 nh = p - np + float2(1.2, 1.6);
            ceramic += float3(0.25) * exp(-dot(nh, nh) / 1.2) * notch;

            // Frost, from the outer edge inward along a ragged front, in the ring's frame.
            float2 fq = float2(cos(ra), sin(ra)) * r;
            float front = s.ring.z * (Ro - Ri + 26.0) * (0.62 + 0.38 * fbm3(float2(ra * 5.0, 1.7)));
            float inFrost = sstep(front + 2.0, front - 6.0, Ro + 2.0 - r) * step(0.001, s.ring.z);
            float feather = 1.0 - abs(2.0 * fbm3(fq / 9.0 + float2(4.0, 1.0)) - 1.0);
            float needles = pow(1.0 - abs(2.0 * vnoise(float2(ra * 70.0, r / 6.0)) - 1.0), 6.0);
            float rimward = sstep(Ri, Ro, r);
            float dens = inFrost * (0.18 + 0.22 * rimward + 0.7 * pow(feather, 5.0) + 0.45 * needles * feather);
            float3 ice = mix(float3(0.74, 0.84, 0.93), float3(0.97, 0.99, 1.0), pow(feather, 3.0));
            float sparkle = step(0.985, hsh(floor(fq / 2.5))) * inFrost * (0.6 + 0.4 * sin(s.env.z * TAU * 4.0 + hsh(floor(fq / 2.5) + 3.0) * 6.0));
            c = mix(ceramic, ice, clamp(dens, 0.0, 1.0) * 0.92);
            c += float3(1.0) * sparkle * 0.7;
            c = mix(c, float3(0.62, 0.76, 0.9), sstep(front - 8.0, front, Ro + 2.0 - r) * inFrost * 0.25);

            // Melt water: beads on the ring where the frost was.
            if (s.ring.w > 0.001) {
                float2 cell = floor(fq / 9.0);
                float2 local = fq - (cell + 0.5) * 9.0;
                float h = hsh(cell + 11.0);
                float present = step(h, s.ring.w * 0.85);
                float br = (1.4 + 2.6 * hsh(cell + 5.0)) * (0.55 + 0.45 * s.ring.w);
                float2 off = (float2(hsh(cell + 2.0), hsh(cell + 9.0)) - 0.5) * 3.0;
                float2 bq = local - off;
                float bd = length(bq);
                float bead = (1.0 - sstep(br - 0.6, br + 0.4, bd)) * present * sstep(Ri, Ri + 4.0, r) * sstep(Ro, Ro - 3.0, r);
                float3 beadC = c * 1.06;
                beadC = mix(beadC, c * 0.55, sstep(br * 0.3, br, length(bq - float2(0.0, -br * 0.3))) * step(0.0, bq.y) * 0.8);
                float2 bh = bq + br * float2(0.35, 0.4);
                beadC += float3(1.0) * exp(-dot(bh, bh) / max(br * br * 0.06, 0.15)) * 0.8;
                c = mix(c, beadC, bead);
            }
            // A dark seam between ring and face.
            c *= 1.0 - 0.5 * exp(-sq((r - Ri) / 1.2));
        } else {
            // The face: a shallow dish of warm grey, ticks around its edge.
            float dish = r / Ri;
            float3 n = normalize(float3(-rel / Ri * 0.25, 1.0));
            float diff = max(dot(n, light), 0.0);
            c = mix(float3(0.17, 0.165, 0.16), float3(0.24, 0.235, 0.225), diff);
            c = mix(c, c * 0.7, sstep(0.82, 1.0, dish));
            c += mix(float3(0.0), float3(0.05, 0.08, 0.12), s.env.x) * (1.0 - dish);
            c += float3(0.08, 0.04, 0.01) * s.env.y * (1.0 - dish);
            // Ticks.
            float a = ang - START;
            a = a - TAU * floor(a / TAU);
            float idx = floor(a / (ARC / 40.0) + 0.5);
            if (idx <= 40.0) {
                float ta = START + idx * (ARC / 40.0);
                float2 dir = float2(cos(ta), sin(ta));
                float across = abs(dot(rel, float2(-dir.y, dir.x)));
                float along = dot(rel, dir);
                bool major = (idx - 10.0 * floor(idx / 10.0)) < 0.5;
                float outer = Ri - 10.0;
                float len = major ? 16.0 : 9.0;
                float w = major ? 1.3 : 0.8;
                float tick = (1.0 - sstep(w - 0.6, w + 0.6, across)) * sstep(outer - len - 0.8, outer - len + 0.8, along) * (1.0 - sstep(outer - 0.8, outer + 0.8, along));
                float lo = min(s.lit.x, s.lit.y);
                float hi = max(s.lit.x, s.lit.y);
                float on = step(lo - 0.01, ta) * step(ta, hi + 0.01);
                float3 tickC = major ? float3(0.78, 0.76, 0.72) : float3(0.52, 0.50, 0.47);
                float3 hot = mix(float3(0.55, 0.80, 1.0), float3(1.0, 0.62, 0.25), s.lit.z);
                tickC = mix(tickC, hot, on);
                c = mix(c, tickC, tick);
                c += hot * on * exp(-across * across / 9.0) * sstep(outer - len - 6.0, outer - len, along) * (1.0 - sstep(outer, outer + 6.0, along)) * 0.18;
            }
        }
        col = mix(col, c, aa);
    }

    // Frost reaching past the ring onto the wall when it is coldest.
    float spill = sstep(0.55, 1.0, s.ring.z);
    if (spill > 0.0 && r > Ro && r < Ro + 26.0) {
        float ra = ang - s.ring.x;
        float2 fq = float2(cos(ra), sin(ra)) * r;
        float reach = spill * 22.0 * (0.4 + 0.6 * fbm3(float2(ra * 7.0, 3.3)));
        float f = sstep(reach, reach - 6.0, r - Ro) * (1.0 - abs(2.0 * fbm3(fq / 7.0) - 1.0));
        col = mix(col, float3(0.86, 0.92, 0.97), clamp(f, 0.0, 1.0) * 0.75);
    }

    col += (hsh(pos) - 0.5) / 255.0;
    return clamp(col, 0.0, 1.0);
}

} // namespace thermostatFrostDial

[[ stitchable ]] half4 ThermostatFrostDialScene(
    float2 position,
    half4 color,
    float2 size,
    float4 dial,
    float4 ring,
    float4 env,
    float4 lit
) {
    thermostatFrostDial::Scene s;
    s.size = size;
    s.dial = dial;
    s.ring = ring;
    s.env = env;
    s.lit = lit;
    return half4(half3(thermostatFrostDial::scene(position, s)), 1.0h);
}
