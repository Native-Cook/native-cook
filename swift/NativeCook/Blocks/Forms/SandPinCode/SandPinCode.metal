#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Sand PIN Code — the zen tray, one full-screen pass. A step-for-step port of the React Native
// SkSL (sandPinCodeShader.ts).
//
// A height field lit from the upper left: parallel rake lines (8 pt pitch) that wander with a low
// value noise and curve into rings around a basalt stone, six shallow slot depressions for the
// PIN, and, while unlocking, a ring packet travelling out from the slots' centre. The normal comes
// from central differences of the height; colour is two sand tones mixed by a coarse noise, a
// per-point grain, a soft specular on the ridges and a warm vignette. `bright` lifts the exposure
// for the unlock. SwiftUI colours are sRGB, so no Display P3 conversion is needed here.
//
// GLSL's `smoothstep` is reimplemented so it behaves the same with reversed edges, and the hash
// uses the precise sine so its large arguments give the same noise as on the GPU in Skia.

namespace sandPinCodeShader {

struct Tray {
    float2 res;
    float4 slots;  // first slot centre x, y, spacing, half height
    float3 stone;  // centre x, y, radius
    float3 ripple; // centre x, y, seconds since it started (< 0: none)
};

float sstep(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float hash21(float2 q) { return fract(precise::sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vnoise(float2 q) {
    float2 i = floor(q);
    float2 f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float slotDepth(float2 p, Tray t) {
    float i = clamp(floor((p.x - t.slots.x) / t.slots.z + 0.5), 0.0, 5.0);
    float2 c = float2(t.slots.x + i * t.slots.z, t.slots.y);
    float2 hb = float2(t.slots.z * 0.36, t.slots.w);
    float2 d = abs(p - c) - hb + 10.0;
    float sd = length(max(d, float2(0.0))) + min(max(d.x, d.y), 0.0) - 10.0;
    return 1.0 - sstep(-6.0, 3.0, sd);
}

float height(float2 p, Tray t) {
    float2 d = p - t.stone.xy;
    float r = length(d);
    float wander = vnoise(p * 0.011) * 16.0 + 5.0 * sin(p.x * 0.017);
    float par = p.y + wander;
    float around = sstep(t.stone.z * 3.0, t.stone.z * 1.45, r);
    float phase = mix(par, r * 1.02, around);
    float h = 0.62 * sin(phase * 0.785398);
    // The slots are raked flat and pressed in.
    float s = slotDepth(p, t);
    h = mix(h, -1.6, s);
    if (t.ripple.z >= 0.0) {
        float rr = length(p - t.ripple.xy);
        float front = t.ripple.z * 300.0;
        float k = (rr - front) / 46.0;
        float env = exp(-(k * k)) * exp(-t.ripple.z * 1.4);
        h += 1.5 * sin((rr - front) * 0.24) * env;
    }
    return h;
}

} // namespace sandPinCodeShader

using namespace sandPinCodeShader;

[[ stitchable ]] half4 sandPinCodeTray(float2 p, half4 color, float2 res, float4 slots, float3 stone, float3 ripple, float bright) {
    Tray t = { res, slots, stone, ripple };
    float e = 1.0;
    float hc = height(p, t);
    float hx = height(p + float2(e, 0.0), t);
    float hy = height(p + float2(0.0, e), t);
    float3 n = normalize(float3(-(hx - hc) * 1.6, -(hy - hc) * 1.6, 1.0));
    float3 L = normalize(float3(-0.55, -0.62, 0.56));
    float diff = clamp(dot(n, L), 0.0, 1.0);
    float3 H = normalize(L + float3(0.0, 0.0, 1.0));
    float spec = pow(clamp(dot(n, H), 0.0, 1.0), 26.0) * 0.12;

    float tone = vnoise(p * 0.006 + 3.1);
    float3 sandA = float3(0.874, 0.788, 0.651);
    float3 sandB = float3(0.812, 0.706, 0.553);
    float3 base = mix(sandA, sandB, tone);
    float grain = hash21(floor(p * 1.4)) - 0.5;
    base *= 1.0 + grain * 0.07;
    base *= 1.0 - 0.06 * slotDepth(p, t);

    float3 col = base * (0.58 + 0.52 * diff) + spec;

    // The stone: dark basalt with a lit crown and a contact shadow on the sand.
    float r = length(p - stone.xy);
    float sh = sstep(stone.z * 1.5, stone.z * 0.9, length(p - stone.xy - float2(6.0, 8.0)));
    col *= 1.0 - 0.32 * sh;
    if (r < stone.z + 1.0) {
        float2 q = (p - stone.xy) / stone.z;
        float z = sqrt(max(0.0, 1.0 - dot(q, q)));
        float3 sn = normalize(float3(q, z));
        float sd = clamp(dot(sn, L), 0.0, 1.0);
        float ss = pow(clamp(dot(sn, H), 0.0, 1.0), 40.0);
        float speck = vnoise(p * 0.35) * 0.08;
        float3 rock = float3(0.20, 0.205, 0.21) * (0.45 + 0.75 * sd) + ss * 0.22 + speck;
        col = mix(col, rock, sstep(stone.z + 1.0, stone.z - 1.0, r));
    }

    // Warm vignette.
    float2 uv = p / res;
    float v = sstep(1.15, 0.35, length((uv - float2(0.5, 0.45)) * float2(1.0, 1.25)));
    col *= mix(0.78, 1.0, v);
    col = mix(col, col * 1.1 + float3(0.06, 0.045, 0.02), bright);

    col = clamp(col, 0.0, 1.0);
    return half4(half3(col), 1.0h);
}
