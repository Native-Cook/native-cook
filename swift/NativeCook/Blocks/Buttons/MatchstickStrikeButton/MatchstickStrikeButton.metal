#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

/*
 Matchstick Strike Button — the match's fire, drawn over a rectangle around the head and its smoke:
 a teardrop flame (an uneven capsule, y up from the head) bent by `lean` along the square of its
 height and stirred by looping noise; a blue cup at its base, a white-yellow core, amber body and
 an orange tip; a fireball at the strike (`flare`) with a wide bloom; and two smoke columns (the
 sulphur puff after the strike and the curl after it is blown out), each fbm drifting up from where
 it started. Output is premultiplied sRGB. The same maths as the React Native SkSL.
*/

// smoothstep that also takes reversed edges (Metal's is undefined there).
static float MatchstickStrikeButtonSstep(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

static float MatchstickStrikeButtonHash(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

static float MatchstickStrikeButtonNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(MatchstickStrikeButtonHash(i), MatchstickStrikeButtonHash(i + float2(1.0, 0.0)), u.x),
               mix(MatchstickStrikeButtonHash(i + float2(0.0, 1.0)), MatchstickStrikeButtonHash(i + float2(1.0, 1.0)), u.x), u.y);
}

static float MatchstickStrikeButtonFbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * MatchstickStrikeButtonNoise(p);
        p = p * 2.07 + float2(5.3, 1.7);
        a *= 0.5;
    }
    return v;
}

static float MatchstickStrikeButtonCapsule(float2 p, float r1, float r2, float h) {
    p.x = abs(p.x);
    float b = (r1 - r2) / h;
    float a = sqrt(1.0 - b * b);
    float k = dot(p, float2(-b, a));
    if (k < 0.0) return length(p) - r1;
    if (k > a * h) return length(p - float2(0.0, h)) - r2;
    return dot(p, float2(a, b)) - r1;
}

// A column of smoke rising from s.xy, s.z seconds old, at strength s.w: rgb premultiplied, alpha.
static float4 MatchstickStrikeButtonSmoke(float2 p, float4 s) {
    if (s.w < 0.01 || s.z < 0.0) return float4(0.0);
    float sy = s.y - p.y;
    float reach = s.z * 78.0;
    if (sy < -4.0 || sy > reach + 40.0) return float4(0.0);
    float sway = sin(sy * 0.05 - s.z * 2.4) * (3.0 + sy * 0.13) + (MatchstickStrikeButtonFbm(float2(sy * 0.022, s.z * 0.7)) - 0.5) * sy * 0.3;
    float w = 2.5 + sy * 0.1;
    float sx = p.x - s.x - sway;
    float dens = exp(-sx * sx / (2.0 * w * w)) * MatchstickStrikeButtonSstep(reach + 40.0, reach - 16.0, sy);
    dens *= 0.5 + 0.7 * MatchstickStrikeButtonFbm(float2(p.x * 0.05, sy * 0.035 - s.z * 0.9));
    dens *= MatchstickStrikeButtonSstep(-4.0, 8.0, sy) * MatchstickStrikeButtonSstep(300.0, 60.0, sy);
    float a = clamp(dens * s.w * 0.5, 0.0, 0.8);
    return float4(float3(0.66, 0.63, 0.6) * a, a);
}

/// flame = (head x, head y, lit, flare); shape = (lean, phase·7, phase·11, 0);
/// puff, curl = (x, y, age, strength); origin = the view's top-left in screen points.
[[ stitchable ]] half4 matchstickStrikeButtonFire(float2 position, half4 color, float4 flame, float4 shape, float4 puff, float4 curl, float2 origin) {
    float2 p = position + origin;
    float lit = flame.z;
    float flare = flame.w;
    float lean = shape.x;
    float2 base = flame.xy;

    float4 acc = MatchstickStrikeButtonSmoke(p, puff);
    float4 c2 = MatchstickStrikeButtonSmoke(p, curl);
    acc = c2 + acc * (1.0 - c2.a);

    // Flame, y up from the head.
    if (lit > 0.01) {
        float fy = base.y - p.y + 4.0;
        float Hf = 58.0 * lit * (1.0 + 0.06 * sin(shape.y * 5.0) + 0.04 * sin(shape.z * 9.0));
        float Wf = 9.5 * sqrt(lit);
        float hn = clamp(fy / max(Hf, 1.0), 0.0, 1.25);
        float n1 = MatchstickStrikeButtonNoise(float2(fy * 0.08 + cos(shape.y) * 2.0, sin(shape.y) * 2.0 + p.x * 0.03));
        float fx = p.x - base.x - lean * 30.0 * hn * hn - (n1 - 0.5) * 6.0 * hn;
        float d = MatchstickStrikeButtonCapsule(float2(fx, fy + Wf * 0.25), Wf, 0.7, max(Hf - Wf, 1.0));
        float outer = MatchstickStrikeButtonSstep(2.5, -3.0, d);
        float3 fc = mix(float3(1.0, 0.85, 0.48), float3(1.0, 0.6, 0.22), MatchstickStrikeButtonSstep(0.3, 0.75, hn));
        fc = mix(fc, float3(1.0, 0.42, 0.13), MatchstickStrikeButtonSstep(0.7, 1.0, hn));
        float core = MatchstickStrikeButtonSstep(-0.8, -Wf * 0.55, d) * MatchstickStrikeButtonSstep(0.06, 0.28, hn) * MatchstickStrikeButtonSstep(0.85, 0.4, hn);
        fc = mix(fc, float3(1.0, 0.97, 0.88), core);
        float cup = 1.0 - MatchstickStrikeButtonSstep(0.0, Wf * 1.4, fy + Wf * 0.3);
        fc = mix(fc, float3(0.32, 0.46, 1.0), cup * 0.7);
        float a = outer * MatchstickStrikeButtonSstep(1.2, 0.85, hn) * clamp(lit * 1.4, 0.0, 1.0) * (1.0 - cup * 0.4);
        acc = float4(fc * a, a) + acc * (1.0 - a);
    }

    // Fireball and bloom at the strike, and the steady glow around the flame (added light).
    float2 q = p - base - float2(lean * 6.0, -14.0 * lit);
    float r = length(q * float2(1.0, 0.85));
    float3 add = float3(1.0, 0.62, 0.28) * exp(-(r * r) / (34.0 * 34.0)) * 0.42 * lit;
    if (flare > 0.01) {
        float2 fq = p - base + float2(0.0, 6.0 * flare);
        float fr = length(fq);
        float ball = MatchstickStrikeButtonSstep(18.0 * flare + 2.0, 6.0 * flare, fr + (MatchstickStrikeButtonNoise(fq * 0.25 + shape.yz * 3.0) - 0.5) * 8.0 * flare);
        float3 bc = mix(float3(1.0, 0.7, 0.3), float3(1.0, 0.98, 0.9), MatchstickStrikeButtonSstep(12.0 * flare, 0.0, fr));
        float ba = ball * flare;
        acc = float4(bc * ba, ba) + acc * (1.0 - ba);
        add += float3(1.0, 0.72, 0.38) * exp(-(fr * fr) / (40.0 * 40.0)) * 0.95 * flare;
    }
    float3 rgb = acc.rgb + add;
    return half4(half3(rgb), half(clamp(acc.a, 0.0, 1.0)));
}
