// Nebula Dust Swirl — the dust on Metal, a port of the React Native WGSL (gpu/nds-wgsl.ts).
//
// nebulaDustSwirlStep (compute, one dispatch a frame) works out each grain's place from pose(), the
// closed form of its seeds and the loop time, and integrates the one thing that is state: how far a
// finger has stirred it (disp, vel), on a spring back to zero. nebulaDustSwirlGrainVertex /
// nebulaDustSwirlGrainFragment then draw one soft round grain per particle, added together (one + one
// blending): the warm core burns where thousands overlap, the arms glow thinner.

#include <metal_stdlib>
using namespace metal;

namespace nebulaDustSwirl {

struct FrameU {
    float4 sim;    // t, dt, clear (1 = forget the finger), hold (how much of the stir is shown)
    float4 view;   // width, height, centre x, centre y (pt)
    float4 look;   // pose unit (pt), -, -, -
    float4 touch;  // x, y (pt), strength 0…1, -
    float4 push;   // the finger's velocity x, y (pt/s), -, -
};

struct Grain {
    float2 pos;
    float2 disp;
    float2 vel;
    float2 look;
};

constant uint COUNT = 40000u;
constant float LOOP_S = 10.0;
constant float TAU_ = 6.28319;
constant float PI_ = 3.14159;

// CORE, SPIRAL, DUST, TONE and TOUCH of nds-motion.ts
constant float CORE_SHARE = 0.15;
constant float CORE_RADIUS = 0.17;
constant float CORE_FLAT = 0.8;
constant float R_MIN = 0.06;
constant float R_SPAN = 0.94;
constant float DENSITY = 0.85;
constant float WIND = 3.1;
constant float SPREAD = 0.34;
constant float SPREAD_OUT = 0.5;
constant float THICK = 0.09;
constant float BREATH = 0.26;
constant float ORBIT = 0.008;
constant float ORBIT_ADD = 0.026;
constant float CAMERA = 3.4;
constant float SIZE = 0.5;
constant float SIZE_ADD = 0.8;
constant float STAR = 1.7;
constant float STAR_SHARE = 0.006;
constant float TWINKLE = 2.0;
constant float TWINKLE_RANGE = 4.0;
constant float3 TONE_CORE = float3(1.0, 0.84, 0.62);
constant float3 TONE_WARM = float3(0.95, 0.6, 0.44);
constant float3 TONE_COOL = float3(0.46, 0.62, 0.88);
constant float3 TONE_TEAL = float3(0.52, 0.78, 0.78);
constant float3 TONE_ROSE = float3(0.8, 0.5, 0.62);
constant float3 TONE_STAR = float3(1.0, 0.97, 0.92);
constant float ROSE_SHARE = 0.14;
constant float CORE_GAIN = 0.11;
constant float ARM_GAIN = 0.17;
constant float STAR_GAIN = 0.6;
constant float T_RADIUS = 82.0;
constant float T_SWIRL = 520.0;
constant float T_DRAG = 3.2;
constant float T_SPRING = 6.0;
constant float T_DAMP = 3.0;

uint pcg(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}

float unitOf(uint v) { return float(pcg(v)) / 4294967296.0; }

// seeds 0…3 and 4…7 of a pair: radius, spread, size, phase | height, spread', kind, tone
float4 seedA(uint pair) { return float4(unitOf(pair * 8u), unitOf(pair * 8u + 1u), unitOf(pair * 8u + 2u), unitOf(pair * 8u + 3u)); }
float4 seedB(uint pair) { return float4(unitOf(pair * 8u + 4u), unitOf(pair * 8u + 5u), unitOf(pair * 8u + 6u), unitOf(pair * 8u + 7u)); }

// x, y in pose units around the centre, perspective scale, radius in the disc
float4 pose(float4 a, float4 b, float arm, float t) {
    float w = TAU_ * t / LOOP_S;
    float x, y, h, r;
    if (b.z < CORE_SHARE) {
        r = CORE_RADIUS * sqrt(-2.0 * log(1.0 - 0.97 * a.x)) * 0.62;
        float ang = TAU_ * a.y + arm * PI_;
        x = r * cos(ang);
        y = r * sin(ang);
        h = CORE_FLAT * CORE_RADIUS * (b.x + b.y - 1.0);
    } else {
        r = R_MIN + R_SPAN * pow(a.x, DENSITY);
        r = r * (1.0 + 0.08 * (b.y - 0.5));
        float tri = a.y + b.y - 1.0;
        float ang = arm * PI_ + WIND * log2(1.0 + 6.0 * r) + tri * (SPREAD + SPREAD_OUT * r);
        x = r * cos(ang);
        y = r * sin(ang);
        h = THICK * (b.x - 0.5) * (1.3 - r);
    }
    float inner = 1.0 - min(r, 1.0);
    float rot = PI_ * t / LOOP_S + BREATH * inner * inner * sin(w + 1.3);
    float cr = cos(rot);
    float sr = sin(rot);
    float dx = x * cr - y * sr;
    float dy = x * sr + y * cr;
    float k = 1.0 + floor(a.z * 3.0);
    float rj = ORBIT + ORBIT_ADD * a.z * a.z;
    dx += rj * cos(TAU_ * a.w + k * w);
    dy += rj * sin(TAU_ * a.w + k * w);
    float ct = cos(0.9);
    float st = sin(0.9);
    float sy = dy * ct - h * st;
    float z = dy * st + h * ct;
    float p = CAMERA / (CAMERA - z);
    return float4(dx * p, sy * p, p, min(r, 1.0));
}

struct GrainOut {
    float4 pos [[position]];
    float2 uv;
    float4 ink;
};

} // namespace nebulaDustSwirl

using namespace nebulaDustSwirl;

kernel void nebulaDustSwirlStep(constant FrameU &F [[buffer(0)]],
                                device Grain *grains [[buffer(1)]],
                                uint i [[thread_position_in_grid]]) {
    if (i >= COUNT) { return; }
    Grain g = grains[i];
    if (F.sim.z > 0.5) {
        g.disp = float2(0.0);
        g.vel = float2(0.0);
    }
    uint pair = i >> 1u;
    float4 q = pose(seedA(pair), seedB(pair), float(i & 1u), F.sim.x);
    float2 home = F.view.zw + q.xy * F.look.x;
    float dt = F.sim.y;

    float2 to = home + g.disp - F.touch.xy;
    float r2 = dot(to, to);
    float fall = F.touch.z * exp(-r2 / (T_RADIUS * T_RADIUS));
    float2 side = float2(-to.y, to.x) / sqrt(r2 + 1.0);
    float2 force = side * T_SWIRL * fall + (F.push.xy - g.vel) * T_DRAG * fall;
    g.vel += (force - T_SPRING * g.disp - T_DAMP * g.vel) * dt;
    g.disp += g.vel * dt;

    g.pos = home + g.disp * F.sim.w;
    g.look = q.zw;
    grains[i] = g;
}

vertex GrainOut nebulaDustSwirlGrainVertex(uint vi [[vertex_id]],
                                            uint ii [[instance_id]],
                                            constant FrameU &F [[buffer(0)]],
                                            const device Grain *grains [[buffer(1)]]) {
    const float2 corners[6] = { float2(-1.0, -1.0), float2(1.0, -1.0), float2(1.0, 1.0), float2(-1.0, -1.0), float2(1.0, 1.0), float2(-1.0, 1.0) };
    float2 c = corners[vi];
    Grain g = grains[ii];
    uint pair = ii >> 1u;
    float4 a = seedA(pair);
    float4 b = seedB(pair);
    float p = g.look.x;
    float r = g.look.y;
    bool core = b.z < CORE_SHARE;
    bool star = !core && b.w > 1.0 - STAR_SHARE;

    float rad = (SIZE + SIZE_ADD * a.z * a.z) * p;
    float3 col = mix(TONE_CORE, TONE_WARM, smoothstep(0.0, 0.3, r));
    col = mix(col, mix(TONE_COOL, TONE_TEAL, b.w), smoothstep(0.22, 0.72, r));
    float gain = core ? CORE_GAIN : ARM_GAIN;
    if (!core && b.w < ROSE_SHARE) {
        col = mix(col, TONE_ROSE, 0.8);
    }
    if (star) {
        rad = STAR * p;
        col = TONE_STAR;
        gain = STAR_GAIN;
    }
    float beats = TWINKLE + floor(a.y * TWINKLE_RANGE);
    float twinkle = 0.72 + 0.28 * sin(TAU_ * beats * F.sim.x / LOOP_S + TAU_ * a.w);
    // nearer grains are a little brighter; the rim fades out into the dark
    float light = gain * twinkle * p * p * (1.0 - smoothstep(0.8, 1.02, r));

    float2 at = g.pos + c * rad * 1.6;
    GrainOut o;
    o.pos = float4(at.x / F.view.x * 2.0 - 1.0, 1.0 - at.y / F.view.y * 2.0, 0.0, 1.0);
    o.uv = c;
    o.ink = float4(col * light, light);
    return o;
}

fragment float4 nebulaDustSwirlGrainFragment(GrainOut v [[stage_in]]) {
    float a = exp(-dot(v.uv, v.uv) * 3.0);
    return float4(v.ink.rgb * a, v.ink.a * a * 0.5);
}
