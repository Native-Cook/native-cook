#include <metal_stdlib>
using namespace metal;

// The habitat's particle world, translated from the WGSL of gpu/HabitatParticleShader.ts: one
// stateful world of 520 particles, stepped by a compute pass each frame and drawn as instanced
// quads in two layers, behind the creatures (depth < 0) and in front of them.
//
// - Ambient motes belong to whichever habitat they float in: embers in fire, bubbles in water,
//   pollen in grass. When the habitat front passes one, it morphs into the new habitat's mote.
// - A finger stirs everything it passes, a tap sends a ring of air out, a landing kicks a gust
//   along the floor, and tilting the phone turns which way is up for everything buoyant.
// - A signature move spawns its own particles from the posed body at the moment of their birth.
//
// Units are screen points; the canvas covers the screen from its top to below the stage.

struct HabitatSCParticleFrame {
    float4 canvas;   // width, height (points), pixel scale, dt (seconds)
    float4 clock;    // ambient seconds, carousel position, frame number, seam phase
    float4 stage;    // screen height, stage top, stage bottom, exchange drift (points)
    float4 touch;    // finger x, y, velocity x, y
    float4 press;    // contact 0..1, petting 0..1, reach (points), live (0 = frozen)
    float4 tap;      // x, y, seconds since, unused
    float4 land;     // feet x, y, seconds since, unused
    float4 gravity;  // which way is down (x, y), unused, unused
    float4 fx;       // effect clock last frame, this frame, performer, unit
    float4 emitA;    // posed point, aim
    float4 emitB;    // posed point, aim
    float4 tip;      // flame tip x, y, half width, floor y
};
struct HabitatSCParticle {
    float2 pos; float2 vel;
    float life, span, kind, look, prev, morph;
    float depth, seed, angle, turn, phase, ground, heat, size;
};

namespace HabitatSCP {

constant uint AMBIENT = 120u;
constant uint COUNT = 520u;
// Signature slots, counted from AMBIENT.
constant uint EMBERS = 96u;
constant uint DROPS = 256u;
constant uint LEAVES = 280u;
constant float MOVE = 1.8;
// A leaf lives until it has settled and faded; this is only its upper bound.
constant float LEAF_SPAN = 9.0;
constant float TAU = 6.28318530718;

inline uint pcg(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}
inline float rand(uint a, uint b) { return float(pcg(a * 1973u + pcg(b + 17u))) / 4294967295.0; }
// Per-slot values that never change, for a move's schedule.
inline float fixedValue(uint slot, uint k) { return rand(slot + 7919u, k * 104729u); }
// Fresh values for a respawn.
inline float fresh(constant HabitatSCParticleFrame& frame, uint i, uint k) { return rand(i, k + uint(frame.clock.z) * 31u); }

inline float seamLine(float y, float f, float phase, float arriving) {
    float bow = sin(3.14159265 * f);
    float seam = 1.08 - 1.16 * f + bow * (0.040 * sin(y * 5.5 + phase) + 0.018 * sin(y * 14.0 - phase * 2.0 + 1.3));
    if (arriving > 1.5) { seam -= 0.011 * abs(sin(y * 46.0 + phase)) * bow; }
    return seam;
}
inline float wrap3(float x) { return x - 3.0 * floor(x / 3.0); }
// The habitat at a point: the leaving one on its side of the front, the arriving one beyond.
inline float elementAt(constant HabitatSCParticleFrame& frame, float2 p) {
    float q = wrap3(frame.clock.y);
    float leaving = floor(q);
    float f = q - leaving;
    if (f < 0.0005) { return leaving; }
    float arriving = wrap3(leaving + 1.0);
    float seam = seamLine(p.y / frame.stage.x, f, frame.clock.w, arriving);
    return p.x < seam * frame.canvas.x ? leaving : arriving;
}
// Which side of the front a world is on: 1 shown, 0 wiped; and its parallax drift.
inline float2 worldSide(constant HabitatSCParticleFrame& frame, float world, float2 p) {
    float q = wrap3(frame.clock.y);
    float leaving = floor(q);
    float f = q - leaving;
    if (f < 0.0005) { return float2(abs(world - leaving) < 0.5 ? 1.0 : 0.0, 0.0); }
    float arriving = wrap3(leaving + 1.0);
    float edge = (p.x - seamLine(p.y / frame.stage.x, f, frame.clock.w, arriving) * frame.canvas.x);
    float side = smoothstep(-1.0, 1.0, edge);
    if (abs(world - leaving) < 0.5) { return float2(1.0 - side, -0.5 * f * f * frame.stage.w); }
    if (abs(world - arriving) < 0.5) { return float2(side, (1.0 - f) * (1.0 - f) * frame.stage.w); }
    return float2(0.0, 0.0);
}
inline float stream(float2 p, float t) {
    return 38.0 * sin(p.x * 0.031 + t * 0.9) * cos(p.y * 0.027 - t * 0.7) + 17.0 * sin(p.x * 0.067 - t * 1.3 + p.y * 0.041);
}
// A slow divergence-free current: the curl of a moving stream function.
inline float2 curl(float2 p, float t) {
    float e = 1.5;
    float a = stream(p + float2(0.0, e), t) - stream(p - float2(0.0, e), t);
    float b = stream(p + float2(e, 0.0), t) - stream(p - float2(e, 0.0), t);
    return float2(a, -b) / (2.0 * e);
}
// The finger, the tap ring and the landing gust, as an acceleration, plus how close the finger is.
inline float3 stir(constant HabitatSCParticleFrame& frame, HabitatSCParticle q) {
    float2 acc = float2(0.0);
    float reach = frame.press.z;
    float2 d = q.pos - frame.touch.xy;
    float r2 = dot(d, d);
    float near = exp(-r2 / (reach * reach)) * frame.press.x;
    float dist = sqrt(r2) + 0.001;
    float2 away = d / dist;
    // A wake: drawn along at the finger's velocity; a swirl around it; a soft push out of its way.
    acc += (frame.touch.zw - q.vel) * near * 7.0;
    acc += float2(-away.y, away.x) * min(length(frame.touch.zw), 900.0) * near * 1.6;
    acc += away * 260.0 * near;
    // Petting gathers the motes into a slow orbit round the finger.
    float held = frame.press.y * exp(-r2 / (reach * reach * 4.0));
    acc += (float2(-away.y, away.x) * 70.0 - away * 50.0 * smoothstep(0.0, reach, dist)) * held;
    if (frame.tap.z >= 0.0 && frame.tap.z < 0.6) {
        float2 t = frame.tap.xy;
        float2 dd = q.pos - t;
        float l = length(dd) + 0.001;
        float x = (l - frame.tap.z * 520.0) / 26.0;
        float ring = exp(-x * x) * (1.0 - frame.tap.z / 0.6);
        acc += dd / l * 2400.0 * ring;
    }
    if (frame.land.z >= 0.0 && frame.land.z < 0.5) {
        float2 dd = q.pos - frame.land.xy;
        float l = length(dd) + 0.001;
        float x = (l - frame.land.z * 380.0) / 30.0;
        float ring = exp(-x * x) * (1.0 - frame.land.z / 0.5) * exp(-abs(dd.y) / 60.0);
        acc += (dd / l + float2(0.0, -0.6)) * 1500.0 * ring;
    }
    return float3(acc, near + held * 0.5);
}

inline float ambientSize(float element, float seed) {
    if (element < 0.5) { return 1.0 + 1.3 * seed; }
    if (element < 1.5) { return 1.8 + 3.4 * seed; }
    return 0.85 + 1.0 * seed;
}
inline HabitatSCParticle spawnAmbient(constant HabitatSCParticleFrame& frame, uint i) {
    HabitatSCParticle q;
    float2 p = float2(fresh(frame, i, 1u) * frame.canvas.x, mix(frame.stage.y, frame.stage.z, fresh(frame, i, 2u)));
    float element = elementAt(frame, p);
    float top = frame.stage.y;
    float bottom = frame.stage.z;
    if (element < 0.5) { p.y = bottom - fresh(frame, i, 3u) * (bottom - top) * 0.35; }
    else if (element < 1.5) { p.y = bottom - fresh(frame, i, 3u) * (bottom - top) * 0.3; }
    q.pos = p;
    q.vel = float2(0.0, element < 0.5 ? -40.0 : 0.0);
    q.life = 0.0;
    q.span = element < 0.5 ? 1.3 + 1.6 * fresh(frame, i, 4u) : (element < 1.5 ? 3.0 + 3.0 * fresh(frame, i, 4u) : 4.0 + 4.0 * fresh(frame, i, 4u));
    q.kind = 0.0;
    q.look = element;
    q.prev = element;
    q.morph = 1.0;
    q.depth = fresh(frame, i, 5u) * 2.0 - 1.0;
    q.seed = fresh(frame, i, 6u);
    q.angle = 0.0; q.turn = 0.0; q.phase = fresh(frame, i, 7u) * TAU; q.ground = 0.0; q.heat = 0.0;
    q.size = 0.0;
    return q;
}
inline HabitatSCParticle ambientStep(constant HabitatSCParticleFrame& frame, uint i, HabitatSCParticle q0, float dt) {
    HabitatSCParticle q = q0;
    bool out = q.pos.x < -30.0 || q.pos.x > frame.canvas.x + 30.0 || q.pos.y < frame.stage.y - 90.0 || q.pos.y > frame.stage.z + 30.0;
    if (q.span <= 0.0 || q.life > q.span || out) { return spawnAmbient(frame, i); }
    // The front passing over a mote morphs it into the new habitat's mote.
    float element = elementAt(frame, q.pos);
    if (abs(element - q.look) > 0.5) {
        q.prev = q.morph < 0.5 ? q.prev : q.look;
        q.look = element;
        q.morph = 0.0;
        q.span = max(q.span, q.life + (element < 0.5 ? 1.6 : 3.0));
    }
    q.morph = min(1.0, q.morph + dt / 0.45);
    float2 up = -frame.gravity.xy;
    float t = frame.clock.x;
    float2 acc = float2(0.0);
    float drag = 0.0;
    for (int k = 0; k < 2; k++) {
        float e = k == 1 ? q.look : q.prev;
        float w = k == 1 ? q.morph : 1.0 - q.morph;
        if (e < 0.5) {
            acc += (up * 120.0 + curl(q.pos, t) * 2.2) * w;
            drag += 1.4 * w;
        } else if (e < 1.5) {
            acc += (up * 95.0 + float2(sin(t * 4.5 + q.seed * 20.0) * 38.0, 0.0)) * w;
            drag += 2.4 * w;
        } else {
            acc += (float2(34.0, sin(t * 1.7 + q.seed * 9.0) * 14.0) + up * 6.0 + curl(q.pos, t * 0.6) * 0.8) * w;
            drag += 1.7 * w;
        }
    }
    float3 s = stir(frame, q);
    float depth = 0.7 + 0.3 * (q.depth * 0.5 + 0.5);
    q.vel += (acc + s.xy) * dt * depth;
    q.vel *= exp(-drag * dt);
    q.pos += q.vel * dt;
    q.heat = max(0.0, q.heat - dt * 1.8) + s.z * dt * 6.0;
    q.life += dt;
    return q;
}

// A signature slot's birth time on the effect clock, and the habitat it belongs to.
inline float2 birth(uint s) {
    if (s < EMBERS) { return float2((0.12 + 0.52 * fixedValue(s, 1u)) * MOVE, 0.0); }
    if (s < DROPS) { return float2((0.15 + 0.40 * fixedValue(s, 1u)) * MOVE + 0.012 + 0.09 * fixedValue(s, 2u), 1.0); }
    if (s < LEAVES) { return float2(0.56 + 0.022 * float(s - DROPS) + 0.02 * fixedValue(s, 1u), 2.0); }
    return float2((0.36 + 0.30 * fixedValue(s, 1u)) * MOVE, 2.0);
}
inline HabitatSCParticle spawnSignature(constant HabitatSCParticleFrame& frame, uint s, float world) {
    HabitatSCParticle q;
    float u = frame.fx.w;
    q.life = 0.0; q.look = world; q.prev = world; q.morph = 1.0; q.seed = fixedValue(s, 3u);
    q.angle = 0.0; q.turn = 0.0; q.phase = fixedValue(s, 4u) * TAU; q.ground = 0.0; q.heat = 0.0;
    q.depth = fixedValue(s, 5u) * 2.0 - 1.0;
    if (s < EMBERS) {
        q.kind = 1.0;
        q.pos = frame.tip.xy + float2((fixedValue(s, 6u) - 0.5) * frame.tip.z, 0.0);
        q.vel = float2((fixedValue(s, 7u) - 0.62) * 95.0, -(110.0 + 150.0 * fixedValue(s, 8u))) * u;
        q.span = 0.5 + 0.4 * fixedValue(s, 9u);
        q.size = (1.1 + 1.5 * fixedValue(s, 10u)) * u;
        q.depth = abs(q.depth);
    } else if (s < DROPS) {
        // Torn off the jet near the muzzle: part of its speed, sideways spread, then gravity.
        q.kind = 2.0;
        bool fromB = (s & 1u) == 1u;
        float4 emit = fromB ? frame.emitB : frame.emitA;
        float2 aim = normalize(emit.zw);
        float speed = 640.0 * u;
        float shed = 0.012 + 0.09 * fixedValue(s, 2u);
        float g = 1500.0 * u;
        float2 jet = aim * speed + float2(0.0, g * shed);
        float spread = (fixedValue(s, 6u) - 0.5) * 1.2;
        float cs = cos(spread); float sn = sin(spread);
        q.pos = emit.xy + aim * speed * shed + float2(0.0, 0.5 * g * shed * shed);
        q.vel = float2(jet.x * cs - jet.y * sn, jet.x * sn + jet.y * cs) * (0.06 + 0.34 * fixedValue(s, 7u));
        q.span = 3.0;
        q.size = (1.8 + 2.4 * fixedValue(s, 8u)) * u;
    } else if (s < LEAVES) {
        q.kind = 4.0;
        float k = float(s - DROPS);
        float launch = -1.5708 + ((k + 0.5) / 24.0 - 0.5) * 2.6 + (fixedValue(s, 6u) - 0.5) * 0.24;
        q.pos = frame.emitA.xy;
        q.vel = float2(cos(launch), sin(launch)) * (220.0 + 90.0 * fixedValue(s, 7u)) * u;
        q.span = LEAF_SPAN;
        q.size = u;
        q.angle = fixedValue(s, 8u) * TAU;
        q.turn = (fixedValue(s, 9u) - 0.5) * 24.0;
    } else {
        q.kind = 5.0;
        float angle = -1.5708 + (fixedValue(s, 6u) - 0.5) * 2.5;
        q.pos = frame.emitA.xy;
        q.vel = float2(cos(angle), sin(angle)) * (120.0 + 220.0 * fixedValue(s, 7u)) * u;
        q.span = 1.45;
        q.size = (1.3 + 1.8 * fixedValue(s, 8u)) * u;
    }
    return q;
}
// Where a particle meets the floor: in front of the feet or, for the far ones, behind them.
inline float floorAt(constant HabitatSCParticleFrame& frame, HabitatSCParticle q) {
    float u = frame.fx.w;
    return frame.tip.w + (q.depth >= 0.0 ? 3.0 + 18.0 * q.depth : -(4.0 + 14.0 * abs(q.depth))) * u;
}
inline HabitatSCParticle signatureStep(constant HabitatSCParticleFrame& frame, uint i, HabitatSCParticle q0, float dt) {
    HabitatSCParticle q = q0;
    uint s = i - AMBIENT;
    float2 b = birth(s);
    if (abs(frame.fx.z - b.y) < 0.5 && frame.fx.x < b.x && b.x <= frame.fx.y) { return spawnSignature(frame, s, b.y); }
    if (q.span <= 0.0 || q.life > q.span) { q.span = 0.0; return q; }
    float u = frame.fx.w;
    float2 down = frame.gravity.xy;
    float3 st = stir(frame, q);
    q.life += dt;
    if (q.kind < 1.5) {
        // Embers rise on the flame's buoyancy, curl and burn out.
        q.vel += (-down * 120.0 * u + curl(q.pos, frame.clock.x) * 1.5 + st.xy) * dt;
        q.vel *= exp(-0.6 * dt);
        q.pos += q.vel * dt;
        q.heat = st.z;
    } else if (q.kind < 2.5) {
        q.vel += (down * 1500.0 * u + st.xy * 0.6) * dt;
        q.pos += q.vel * dt;
        float floorY = floorAt(frame, q);
        if (q.pos.y >= floorY) { q.pos.y = floorY; q.kind = 3.0; q.life = 0.0; q.span = 0.34; q.vel = float2(0.0); }
        if (q.pos.x < -40.0 || q.pos.x > frame.canvas.x + 40.0) { q.span = 0.0; }
    } else if (q.kind < 3.5) {
        // A splash ring spreads on the floor and fades.
    } else if (q.kind < 4.5) {
        float floorY = floorAt(frame, q);
        if (q.ground > 0.0) {
            q.ground += dt;
            // A finger sweeping through can lift a settled leaf again.
            if (st.z > 0.35) {
                q.ground = 0.0;
                float2 kick = frame.touch.zw * 0.35 + float2(0.0, -160.0 * u);
                q.vel = kick * min(1.0, 420.0 * u / max(length(kick), 1.0));
                q.life = 0.21;
            }
            if (q.ground > 1.05) { q.span = 0.0; }
        } else {
            // Drag pulls the burst towards a steady fall (under a metre a second) on the breeze.
            float drag = 2.3 + 0.8 * q.seed;
            float fall = (210.0 + 70.0 * fixedValue(s, 11u)) * u;
            float2 goal = down * fall + float2(14.0 + 14.0 * fixedValue(s, 12u), 0.0) * u;
            q.vel += ((goal - q.vel) * drag + st.xy) * dt;
            q.pos += q.vel * dt;
            q.turn *= exp(-drag * dt);
            q.angle += q.turn * dt;
            q.phase += TAU / (0.85 + 0.45 * fixedValue(s, 13u)) * dt;
            if (q.pos.y >= floorY && q.life > 0.2) { q.pos.y = floorY; q.ground = 0.001; q.vel = float2(0.0); }
        }
    } else {
        q.vel += (float2(26.0, 10.0) * u + st.xy) * dt;
        q.vel *= exp(-2.6 * dt);
        q.pos += q.vel * dt;
    }
    return q;
}

}  // namespace HabitatSCP

kernel void habitatSCParticleSimulate(constant HabitatSCParticleFrame& frame [[buffer(0)]],
                                      device HabitatSCParticle* particles [[buffer(1)]],
                                      uint i [[thread_position_in_grid]]) {
    using namespace HabitatSCP;
    if (i >= COUNT) { return; }
    float dt = frame.canvas.w * frame.press.w;
    if (i < AMBIENT) { particles[i] = ambientStep(frame, i, particles[i], dt); }
    else if (frame.press.w > 0.5) { particles[i] = signatureStep(frame, i, particles[i], dt); }
}

// ---- Drawing ----

struct HabitatSCMote {
    float4 position [[position]];
    float2 local;
    float4 look [[flat]];   // kind, element, previous element, morph
    float4 shape [[flat]];  // radius, life fraction, heat, seed
    float4 pose [[flat]];   // roll, seconds settled, world, depth
};

vertex HabitatSCMote habitatSCMoteVertex(uint v [[vertex_id]], uint i [[instance_id]],
                                         constant HabitatSCParticleFrame& frame [[buffer(0)]],
                                         constant float4& layer [[buffer(1)]],
                                         const device HabitatSCParticle* motes [[buffer(2)]]) {
    using namespace HabitatSCP;
    HabitatSCMote out;
    out.position = float4(2.0, 2.0, 0.0, 1.0);
    out.local = float2(0.0);
    out.look = float4(0.0); out.shape = float4(0.0); out.pose = float4(0.0);
    HabitatSCParticle q = motes[i];
    bool back = q.depth < 0.0;
    if (q.span <= 0.0 || back != (layer.x < 0.5)) { return out; }
    const float2 corners[6] = { float2(-1.0, -1.0), float2(1.0, -1.0), float2(-1.0, 1.0), float2(-1.0, 1.0), float2(1.0, -1.0), float2(1.0, 1.0) };
    float2 c = corners[v];
    float lf = q.life / q.span;
    float depthScale = back ? 0.86 : 1.0;
    float2 center = q.pos;
    float half_ = 1.0;
    float stretch = 1.0;
    float2 axis = float2(1.0, 0.0);
    float radius = 1.0;
    float roll = 0.0;
    float speed = length(q.vel);
    if (speed > 1.0) { axis = q.vel / speed; }
    if (q.kind < 0.5) {
        radius = mix(ambientSize(q.prev, q.seed), ambientSize(q.look, q.seed), q.morph) * depthScale * (1.0 + q.heat * 0.4);
        // Fast embers streak along their path; bubbles and pollen stay round.
        float fiery = (q.prev < 0.5 ? 1.0 - q.morph : 0.0) + (q.look < 0.5 ? q.morph : 0.0);
        stretch = 1.0 + min(speed, 400.0) * 0.004 * fiery;
        half_ = radius * 2.4;
    } else if (q.kind < 1.5 || q.kind > 4.5) {
        radius = q.size * depthScale;
        stretch = 1.0 + min(speed, 400.0) * 0.003;
        half_ = radius * 6.0;
    } else if (q.kind < 2.5) {
        radius = q.size;
        stretch = 1.0 + min(speed, 900.0) * 0.0012;
        half_ = radius * 3.0;
    } else if (q.kind < 3.5) {
        radius = q.size * (1.6 + 4.0 * lf);
        axis = float2(1.0, 0.0);
        half_ = radius * 2.2;
    } else {
        // A leaf: physics carries its centre; its flutter swings it as a pendulum round that path.
        float u = frame.fx.w;
        float rest = q.ground > 0.0 ? 1.0 - pow(1.0 - min(1.0, q.ground / 0.26), 3.0) : 0.0;
        float flutter = pow(1.0 - exp(-2.5 * q.life), 2.0) * (1.0 - rest);
        float swing = (11.0 + 11.0 * q.seed) * u;
        center += float2(swing * flutter * sin(q.phase), swing * 0.22 * flutter * cos(2.0 * q.phase));
        float level = round(q.angle / 3.14159265) * 3.14159265;
        float a = mix(q.angle, level, max(flutter, rest)) + 0.55 * flutter * cos(q.phase) + 0.14 * sin(rest * TAU) * (1.0 - rest);
        axis = float2(cos(a), sin(a));
        float rolling = (q.seed - 0.5) * 18.0 * (1.0 - exp(-2.3 * q.life)) / 2.3 + 0.75 * flutter * sin(q.phase);
        roll = mix(rolling, cos(rolling) > 0.0 ? 1.05 : 3.14159265 - 1.05, rest);
        radius = u * (1.0 + q.depth * 20.0 / 260.0) * (0.62 + 0.38 * smoothstep(0.0, 0.14, q.life));
        half_ = 22.0 * radius;
    }
    float2 side = worldSide(frame, q.look, center);
    if (q.kind > 0.5) { center.x += side.y; }
    float along = c.x * half_ * stretch;
    float across = c.y * half_;
    float2 p = center + axis * along + float2(-axis.y, axis.x) * across;
    out.position = float4(p.x / frame.canvas.x * 2.0 - 1.0, 1.0 - p.y / frame.canvas.y * 2.0, 0.0, 1.0);
    // The shape is drawn round in its own frame; the stretched quad turns it into a streak.
    out.local = float2(along / stretch, across);
    out.look = float4(q.kind, q.look, q.prev, q.morph);
    out.shape = float4(radius, lf, q.heat, q.seed);
    out.pose = float4(roll, q.ground, q.look, q.depth);
    if (q.kind > 0.5 && side.x <= 0.0) { out.position = float4(2.0, 2.0, 0.0, 1.0); }
    return out;
}

namespace HabitatSCP {

inline float4 ambientMote(float e, float2 l, float r, float lf, float heat, float seed) {
    float life = smoothstep(0.0, 0.14, lf) * (1.0 - smoothstep(0.76, 1.0, lf));
    if (e < 0.5) {
        float2 p = l / float2(r * 0.6, r);
        float f = max(0.0, 1.0 - dot(p, p) * 0.2);
        float glow = f * f * f * life * (0.30 + 0.24 * seed) * (1.0 + heat * 1.6);
        return float4(float3(1.0, 0.37 + 0.32 * (1.0 - lf), 0.095) * glow, glow);
    }
    if (e < 1.5) {
        float ring = 1.0 - smoothstep(0.55, 1.1, abs(length(l) - r));
        float2 g = l + float2(r * 0.35);
        float fall = max(0.0, 1.0 - dot(g, g) * 0.4);
        float bubble = (ring * 0.17 + fall * fall * fall * 0.22) * life * (1.0 + heat * 0.8);
        return float4(float3(0.57, 0.87, 0.94) * bubble, bubble);
    }
    float2 p = l / r;
    float f = max(0.0, 1.0 - dot(p, p) * 0.20);
    float pollen = f * f * f * life * 0.38 * (1.0 + heat * 1.2);
    return float4(float3(0.85, 0.91, 0.47) * pollen, pollen);
}
inline float4 leafMote(float2 l, float r, float roll, bool back) {
    float2 local = l / r;
    float face = cos(roll);
    float width = max(abs(face), 0.12);
    float along = local.x / 17.0;
    float halfWidth = 6.6 * width * max(0.0, 1.0 - along * along) * (1.0 + 0.16 * along);
    float body = (1.0 - smoothstep(halfWidth - 0.7, halfWidth + 0.3, abs(local.y))) * step(abs(along), 1.0);
    float vein = (1.0 - smoothstep(0.1, 0.5, abs(local.y) / max(width, 0.35))) * step(abs(along), 0.9);
    float3 top = mix(float3(0.22, 0.50, 0.16), float3(0.52, 0.80, 0.30), clamp(0.5 + local.y / (halfWidth + 0.01) * 0.45, 0.0, 1.0));
    float3 under = mix(float3(0.50, 0.68, 0.30), float3(0.70, 0.84, 0.44), clamp(0.5 - local.y / (halfWidth + 0.01) * 0.45, 0.0, 1.0));
    float3 green = (face >= 0.0 ? top : under) * (0.62 + 0.38 * abs(face)) + float3(0.10, 0.14, 0.05) * vein;
    if (back) { green *= 0.74; }
    return float4(green * body, body);
}

}  // namespace HabitatSCP

fragment float4 habitatSCMoteFragment(HabitatSCMote in [[stage_in]],
                                      constant HabitatSCParticleFrame& frame [[buffer(0)]]) {
    using namespace HabitatSCP;
    float kind = in.look.x;
    float r = in.shape.x;
    float lf = in.shape.y;
    float4 c = float4(0.0);
    if (kind < 0.5) {
        c = ambientMote(in.look.z, in.local, r, lf, in.shape.z, in.shape.w) * (1.0 - in.look.w)
          + ambientMote(in.look.y, in.local, r, lf, in.shape.z, in.shape.w) * in.look.w;
        if (in.pose.w < 0.0) { c *= 0.75; }
        // The motes live on the stage: they thin out above it and at the screen's sides.
        float2 p = in.position.xy / frame.canvas.z;
        c *= smoothstep(frame.stage.y - 50.0, frame.stage.y + 40.0, p.y) * (1.0 - smoothstep(frame.stage.z - 10.0, frame.stage.z + 20.0, p.y))
           * smoothstep(0.0, 24.0, p.x) * (1.0 - smoothstep(frame.canvas.x - 24.0, frame.canvas.x, p.x));
    } else if (kind < 1.5) {
        float life = 1.0 - lf;
        float d2 = dot(in.local, in.local) / (r * r);
        float glow = (exp(-d2) + exp(-d2 / 9.0) * 0.25) * life * (1.0 + in.shape.z);
        c = float4(float3(1.0, 0.58 + 0.3 * life, 0.18) * glow, min(glow * 0.8, 1.0));
    } else if (kind < 2.5) {
        float d = length(in.local);
        float drop = (1.0 - smoothstep(r * 0.5, r, d)) * smoothstep(0.0, 0.04, in.shape.y * 3.0);
        float2 g = in.local + float2(r * 0.35);
        float sheen = exp(-dot(g, g) / (r * r * 0.25)) * drop;
        c = float4(float3(0.6, 0.88, 0.98) * drop * 0.8 + float3(1.0) * sheen * 0.6, drop * 0.75 + sheen * 0.3);
    } else if (kind < 3.5) {
        float2 q = in.local / float2(r, r * 0.3);
        float d = length(q);
        float x = (d - 1.0) / 0.25;
        float ring = exp(-x * x) * (1.0 - lf) * (1.0 - lf);
        float flash = exp(-d * d * 4.0) * (1.0 - smoothstep(0.0, 0.3, lf));
        c = float4(float3(0.75, 0.95, 1.0) * (ring * 0.75 + flash * 0.5), ring * 0.6 + flash * 0.4);
    } else if (kind < 4.5) {
        // Settled on the floor it lies still, then fades; a contact shadow grows under it as it settles.
        float ground = in.pose.y;
        float rest = ground > 0.0 ? 1.0 - pow(1.0 - min(1.0, ground / 0.26), 3.0) : 0.0;
        float birthFade = smoothstep(0.0, 0.06, lf * LEAF_SPAN);
        c = leafMote(in.local, r, in.pose.x, in.pose.w < 0.0);
        float2 sq = in.local / (r * float2(16.0, 3.0));
        float shadow = exp(-dot(sq, sq) * 1.6) * 0.38 * rest;
        c = (c + float4(0.0, 0.0, 0.0, shadow) * (1.0 - c.a)) * birthFade * (1.0 - smoothstep(0.55, 1.05, ground));
    } else {
        float d2 = dot(in.local, in.local) / (r * r);
        float age = lf * 1.45;
        float life = smoothstep(0.0, 0.08, age) * (1.0 - smoothstep(1.05, 1.45, age));
        float twinkle = 0.7 + 0.3 * sin(age * 18.0 + in.shape.w * 40.0);
        float mote = (exp(-d2) + exp(-d2 / 7.0) * 0.25) * life * twinkle * (1.0 + in.shape.z);
        c = float4(float3(1.0, 0.95, 0.62) * mote, min(mote * 0.8, 1.0));
    }
    if (kind > 0.5) {
        // Wiped by the habitat front together with the world it belongs to.
        float2 p = in.position.xy / frame.canvas.z;
        c *= worldSide(frame, in.pose.z, p).x;
    }
    return c;
}
