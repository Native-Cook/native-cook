// Murmuration Flock — the birds on Metal: one compute pass and one instanced draw a frame.
//
// murmurationFlockStep works out each bird's place from pose(), a closed form of its four seeds and
// the loop time, and integrates the one thing that is state: how far a finger has pushed it (disp,
// vel), on a spring back to zero. murmurationFlockBirdVertex / Fragment then draw one small dart per
// bird along its heading, with its wings beating; the darts overlap into the dark, folding cloud.
// The same maths as the React Native block's WGSL, number for number.

#include <metal_stdlib>
using namespace metal;

struct MurmurationFlockFrame {
    float4 sim;    // t, dt, clear (1 = forget the finger), hold (how much of the push is shown)
    float4 view;   // width, height, centre x, centre y (pt)
    float4 look;   // pose unit (pt), -, -, -
    float4 touch;  // x, y (pt), strength 0…1, -
};

struct MurmurationFlockBird {
    float2 pos;
    float2 head;
    float2 disp;
    float2 vel;
};

constant uint MF_COUNT = 20000;
constant float MF_LOOP = 12.0;
constant float MF_TAU = 6.28318531;
constant float MF_PI = 3.14159265;

// the ribbon the flock rests in
constant float MF_ALONG = 0.92;
constant float MF_SWAY = 0.14;
constant float MF_SWAY_FREQ = 2.2;
constant float MF_RISE = 0.3;
constant float MF_RISE_FREQ = 2.7;
constant float MF_RIPPLE = 0.13;
constant float MF_RIPPLE_FREQ = 5.1;
constant float MF_WIDTH = 0.5;
constant float MF_NARROW = 0.55;
constant float MF_WIDTH_MIN = 0.04;
constant float MF_SWELL = 0.4;
constant float MF_SWELL_FREQ = 1.7;
constant float MF_TWIST = 2.4;
constant float MF_TWIST_WAVE = 1.2;
constant float MF_TWIST_WAVE_FREQ = 3.0;
constant float MF_TILT = 0.72;
constant float MF_ORBIT = 0.018;
constant float MF_ORBIT_ADD = 0.07;
// the inhale's circle
constant float MF_RING = 0.78;
constant float MF_RING_INNER = 0.62;
constant float MF_RING_SPREAD = 0.05;
constant float MF_RING_GROW = 0.82;
// the morph's arc and the exhale's gust
constant float MF_ARC = 0.12;
constant float MF_ARC_ADD = 0.22;
constant float MF_GUST = 0.1;
constant float MF_GUST_ADD = 0.26;
// the phases
constant float MF_INHALE_A = 1.4;
constant float MF_INHALE_B = 5.4;
constant float MF_EXHALE_A = 6.4;
constant float MF_EXHALE_B = 10.4;
constant float MF_STAGGER = 1.1;
// the finger
constant float MF_TOUCH_RADIUS = 74.0;
constant float MF_FLEE = 2600.0;
constant float MF_SWIRL = 1500.0;
constant float MF_SPRING = 26.0;
constant float MF_DAMP = 6.5;
// a bird
constant float MF_HEAD_BACK = 0.03;
constant float MF_LENGTH = 1.7;
constant float MF_LENGTH_ADD = 1.5;
constant float MF_BIRD_WIDTH = 0.62;
constant float MF_BIRD_WIDTH_ADD = 0.5;
constant float MF_BEATS = 18.0;
constant float MF_BEATS_RANGE = 12.0;
constant float MF_ALPHA = 0.62;

static uint mfPcg(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}

static float4 mfSeeds(uint i) {
    float k = 4294967296.0;
    return float4(float(mfPcg(i * 4u)) / k, float(mfPcg(i * 4u + 1u)) / k, float(mfPcg(i * 4u + 2u)) / k, float(mfPcg(i * 4u + 3u)) / k);
}

static float mfSeg(float t, float a, float b) { return clamp((t - a) / (b - a), 0.0, 1.0); }
static float mfSmoother(float v) { return v * v * v * (v * (v * 6.0 - 15.0) + 10.0); }

static float2 mfPose(float4 s, float t) {
    float w = MF_TAU * t / MF_LOOP;
    float u = 2.0 * s.x - 1.0;
    float v0 = 2.0 * s.y - 1.0;
    float v = v0 * (0.55 + 0.45 * abs(v0));
    float width = (MF_WIDTH * (1.0 - MF_NARROW * u * u) + MF_WIDTH_MIN) * (1.0 + MF_SWELL * sin(MF_SWELL_FREQ * u + w));
    float phi = MF_TWIST * u + w + MF_TWIST_WAVE * sin(2.0 * w + MF_TWIST_WAVE_FREQ * u);
    float k = 1.0 + floor(s.z * 3.0);
    float orbit = MF_TAU * s.w + k * w;
    float rj = MF_ORBIT + MF_ORBIT_ADD * s.z * s.z;
    float2 cloud = float2(
        MF_ALONG * u + MF_SWAY * sin(MF_SWAY_FREQ * u + w) - sin(phi) * width * v + rj * cos(orbit),
        MF_RISE * sin(MF_RISE_FREQ * u + w) + MF_RIPPLE * sin(MF_RIPPLE_FREQ * u - 2.0 * w) + cos(phi) * width * v * MF_TILT + rj * sin(orbit)
    );

    float late = s.w * MF_STAGGER;
    float m = mfSmoother(mfSeg(t, MF_INHALE_A + late, MF_INHALE_B)) - mfSmoother(mfSeg(t, MF_EXHALE_A + late, MF_EXHALE_B));
    float spin = MF_TAU * s.x + w;
    float r = (MF_RING * (MF_RING_INNER + (1.0 - MF_RING_INNER) * sqrt(s.y)) + MF_RING_SPREAD * (s.z - 0.5)) * (MF_RING_GROW + (1.0 - MF_RING_GROW) * m);
    float2 ring = float2(r * cos(spin), r * sin(spin));

    float2 d = ring - cloud;
    float bend = sin(MF_PI * m) * (MF_ARC + MF_ARC_ADD * (2.0 * s.z - 1.0));
    float2 p = cloud + d * m + float2(-d.y, d.x) * bend;
    float gs = sin(MF_PI * mfSeg(t, MF_EXHALE_A, MF_EXHALE_B));
    float g = gs * gs;
    if (g > 0.0) {
        p += p / (length(p) + 0.001) * g * (MF_GUST + MF_GUST_ADD * s.z);
    }
    return p;
}

kernel void murmurationFlockStep(constant MurmurationFlockFrame &F [[buffer(0)]],
                                 device MurmurationFlockBird *birds [[buffer(1)]],
                                 uint i [[thread_position_in_grid]]) {
    if (i >= MF_COUNT) { return; }
    MurmurationFlockBird b = birds[i];
    if (F.sim.z > 0.5) {
        b.disp = float2(0.0);
        b.vel = float2(0.0);
    }
    float4 s = mfSeeds(i);
    float unit = F.look.x;
    float2 now = mfPose(s, F.sim.x);
    float2 before = mfPose(s, F.sim.x - MF_HEAD_BACK);
    float2 home = F.view.zw + now * unit;
    float dt = F.sim.y;

    float2 to = home + b.disp - F.touch.xy;
    float r2 = dot(to, to);
    float fall = F.touch.z * exp(-r2 / (MF_TOUCH_RADIUS * MF_TOUCH_RADIUS));
    float2 out = to / sqrt(r2 + 1.0);
    float2 side = float2(-out.y, out.x) * (s.z > 0.35 ? 1.0 : -1.0);
    float2 force = (out * MF_FLEE + side * MF_SWIRL) * fall;
    b.vel += (force - MF_SPRING * b.disp - MF_DAMP * b.vel) * dt;
    b.disp += b.vel * dt;

    b.pos = home + b.disp * F.sim.w;
    b.head = (now - before) * unit + b.vel * MF_HEAD_BACK * F.sim.w;
    birds[i] = b;
}

struct MurmurationFlockVaryings {
    float4 position [[position]];
    float2 uv;
    float4 ink;
};

constant float2 MF_CORNERS[6] = { float2(-1.0, -1.0), float2(1.0, -1.0), float2(1.0, 1.0), float2(-1.0, -1.0), float2(1.0, 1.0), float2(-1.0, 1.0) };

vertex MurmurationFlockVaryings murmurationFlockBirdVertex(uint vi [[vertex_id]], uint ii [[instance_id]],
                                                          constant MurmurationFlockFrame &F [[buffer(0)]],
                                                          const device MurmurationFlockBird *birds [[buffer(1)]]) {
    float2 c = MF_CORNERS[vi];
    MurmurationFlockBird b = birds[ii];
    float4 s = mfSeeds(ii);
    float speed = length(b.head);
    float2 along = speed > 0.0001 ? b.head / speed : float2(1.0, 0.0);
    float2 across = float2(-along.y, along.x);
    float beats = MF_BEATS + floor(s.z * MF_BEATS_RANGE);
    float flap = 0.55 + 0.45 * abs(sin(MF_TAU * beats * F.sim.x / MF_LOOP + MF_TAU * s.w));
    float len = MF_LENGTH + MF_LENGTH_ADD * s.z;
    float wid = (MF_BIRD_WIDTH + MF_BIRD_WIDTH_ADD * s.z) * flap;
    float2 p = b.pos + along * c.x * len + across * c.y * wid;
    MurmurationFlockVaryings o;
    o.position = float4(p.x / F.view.x * 2.0 - 1.0, 1.0 - p.y / F.view.y * 2.0, 0.0, 1.0);
    o.uv = c;
    // near birds are darker; the flock warms a little towards the horizon
    float low = clamp(b.pos.y / F.view.y, 0.0, 1.0);
    float3 tone = mix(float3(0.075, 0.066, 0.145), float3(0.150, 0.082, 0.135), low);
    o.ink = float4(tone * (1.18 - 0.36 * s.z), MF_ALPHA * (0.72 + 0.28 * s.z));
    return o;
}

fragment float4 murmurationFlockBirdFragment(MurmurationFlockVaryings v [[stage_in]]) {
    float d = abs(v.uv.x) + abs(v.uv.y);
    float a = v.ink.a * (1.0 - smoothstep(0.55, 1.0, d));
    return float4(v.ink.rgb * a, a);
}
