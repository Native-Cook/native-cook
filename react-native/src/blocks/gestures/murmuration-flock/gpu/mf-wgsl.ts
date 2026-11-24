// Murmuration Flock — the birds on WebGPU, in plain WGSL.
//
// flockCs (compute, one dispatch a frame) works out each bird's place from pose(), the same closed
// form as mf-motion.ts, and integrates the one thing that is state: how far a finger has pushed it
// (disp, vel), on a spring back to zero. birdVs / birdFs then draw one small dart per bird along its
// heading, with its wings beating; the darts overlap into the dark, folding cloud.

import { AIR, CLOUD, FLOCK, LOOP, RING, T, TAU, TOUCH } from '../mf-motion';

const n = (v: number) => {
  const s = v.toFixed(5);
  return s.includes('.') ? s : `${s}.0`;
};

/** FrameU in floats */
export const FRAME_FLOATS = 16;
/** Bird in bytes: pos, head, disp, vel */
export const BIRD_BYTES = 32;

export const MF_WGSL = /* wgsl */ `
struct FrameU {
  sim: vec4f,    // t, dt, clear (1 = forget the finger), hold (how much of the push is shown)
  view: vec4f,   // width, height, centre x, centre y (pt)
  look: vec4f,   // pose unit (pt), -, -, -
  touch: vec4f,  // x, y (pt), strength 0…1, -
};
struct Bird { pos: vec2f, head: vec2f, disp: vec2f, vel: vec2f };

@group(0) @binding(0) var<uniform> F: FrameU;
@group(0) @binding(1) var<storage, read_write> birdsOut: array<Bird>;
@group(0) @binding(2) var<storage, read> birds: array<Bird>;

const LOOP_S = ${n(LOOP)};
const TAU_ = ${n(TAU)};
const PI_ = ${n(Math.PI)};

fn pcg(v: u32) -> u32 {
  let s = v * 747796405u + 2891336453u;
  let w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
  return (w >> 22u) ^ w;
}

fn seedsOf(i: u32) -> vec4f {
  let k = 4294967296.0;
  return vec4f(f32(pcg(i * 4u)) / k, f32(pcg(i * 4u + 1u)) / k, f32(pcg(i * 4u + 2u)) / k, f32(pcg(i * 4u + 3u)) / k);
}

fn seg(t: f32, a: f32, b: f32) -> f32 { return clamp((t - a) / (b - a), 0.0, 1.0); }
fn smoother(v: f32) -> f32 { return v * v * v * (v * (v * 6.0 - 15.0) + 10.0); }

fn pose(s: vec4f, t: f32) -> vec2f {
  let w = TAU_ * t / LOOP_S;
  let u = 2.0 * s.x - 1.0;
  let v0 = 2.0 * s.y - 1.0;
  let v = v0 * (0.55 + 0.45 * abs(v0));
  let width = (${n(CLOUD.width)} * (1.0 - ${n(CLOUD.narrow)} * u * u) + ${n(CLOUD.widthMin)}) * (1.0 + ${n(CLOUD.swell)} * sin(${n(CLOUD.swellFreq)} * u + w));
  let phi = ${n(CLOUD.twist)} * u + w + ${n(CLOUD.twistWave)} * sin(2.0 * w + ${n(CLOUD.twistWaveFreq)} * u);
  let k = 1.0 + floor(s.z * 3.0);
  let orbit = TAU_ * s.w + k * w;
  let rj = ${n(CLOUD.orbit)} + ${n(CLOUD.orbitAdd)} * s.z * s.z;
  let cloud = vec2f(
    ${n(CLOUD.along)} * u + ${n(CLOUD.sway)} * sin(${n(CLOUD.swayFreq)} * u + w) - sin(phi) * width * v + rj * cos(orbit),
    ${n(CLOUD.rise)} * sin(${n(CLOUD.riseFreq)} * u + w) + ${n(CLOUD.ripple)} * sin(${n(CLOUD.rippleFreq)} * u - 2.0 * w) + cos(phi) * width * v * ${n(CLOUD.tilt)} + rj * sin(orbit)
  );

  let late = s.w * ${n(T.stagger)};
  let m = smoother(seg(t, ${n(T.inhale[0])} + late, ${n(T.inhale[1])})) - smoother(seg(t, ${n(T.exhale[0])} + late, ${n(T.exhale[1])}));
  let spin = TAU_ * s.x + w;
  let r = (${n(RING.radius)} * (${n(RING.inner)} + ${n(1 - RING.inner)} * sqrt(s.y)) + ${n(RING.spread)} * (s.z - 0.5)) * (${n(RING.grow)} + ${n(1 - RING.grow)} * m);
  let ring = vec2f(r * cos(spin), r * sin(spin));

  let d = ring - cloud;
  let bend = sin(PI_ * m) * (${n(AIR.arc)} + ${n(AIR.arcAdd)} * (2.0 * s.z - 1.0));
  var p = cloud + d * m + vec2f(-d.y, d.x) * bend;
  let gs = sin(PI_ * seg(t, ${n(T.exhale[0])}, ${n(T.exhale[1])}));
  let g = gs * gs;
  if (g > 0.0) {
    p += p / (length(p) + 0.001) * g * (${n(AIR.gust)} + ${n(AIR.gustAdd)} * s.z);
  }
  return p;
}

@compute @workgroup_size(${FLOCK.group}) fn flockCs(@builtin(global_invocation_id) gid: vec3u) {
  let i = gid.x;
  if (i >= ${FLOCK.count}u) { return; }
  var b = birdsOut[i];
  if (F.sim.z > 0.5) {
    b.disp = vec2f(0.0);
    b.vel = vec2f(0.0);
  }
  let s = seedsOf(i);
  let unit = F.look.x;
  let now = pose(s, F.sim.x);
  let before = pose(s, F.sim.x - ${n(FLOCK.headBack)});
  let home = F.view.zw + now * unit;
  let dt = F.sim.y;

  let to = home + b.disp - F.touch.xy;
  let r2 = dot(to, to);
  let fall = F.touch.z * exp(-r2 / ${n(TOUCH.radius * TOUCH.radius)});
  let out = to / sqrt(r2 + 1.0);
  let side = vec2f(-out.y, out.x) * select(-1.0, 1.0, s.z > 0.35);
  let force = (out * ${n(TOUCH.flee)} + side * ${n(TOUCH.swirl)}) * fall;
  b.vel += (force - ${n(TOUCH.spring)} * b.disp - ${n(TOUCH.damp)} * b.vel) * dt;
  b.disp += b.vel * dt;

  b.pos = home + b.disp * F.sim.w;
  b.head = (now - before) * unit + b.vel * ${n(FLOCK.headBack)} * F.sim.w;
  birdsOut[i] = b;
}

struct BirdOut {
  @builtin(position) pos: vec4f,
  @location(0) uv: vec2f,
  @location(1) ink: vec4f,
};

@vertex fn birdVs(@builtin(vertex_index) vi: u32, @builtin(instance_index) ii: u32) -> BirdOut {
  var corners = array<vec2f, 6>(vec2f(-1.0, -1.0), vec2f(1.0, -1.0), vec2f(1.0, 1.0), vec2f(-1.0, -1.0), vec2f(1.0, 1.0), vec2f(-1.0, 1.0));
  let c = corners[vi];
  let b = birds[ii];
  let s = seedsOf(ii);
  let speed = length(b.head);
  let along = select(vec2f(1.0, 0.0), b.head / speed, speed > 0.0001);
  let across = vec2f(-along.y, along.x);
  let beats = ${n(FLOCK.beats)} + floor(s.z * ${n(FLOCK.beatsRange)});
  let flap = 0.55 + 0.45 * abs(sin(TAU_ * beats * F.sim.x / LOOP_S + TAU_ * s.w));
  let len = ${n(FLOCK.length)} + ${n(FLOCK.lengthAdd)} * s.z;
  let wid = (${n(FLOCK.width)} + ${n(FLOCK.widthAdd)} * s.z) * flap;
  let p = b.pos + along * c.x * len + across * c.y * wid;
  var o: BirdOut;
  o.pos = vec4f(p.x / F.view.x * 2.0 - 1.0, 1.0 - p.y / F.view.y * 2.0, 0.0, 1.0);
  o.uv = c;
  // near birds are darker; the flock warms a little towards the horizon
  let low = clamp(b.pos.y / F.view.y, 0.0, 1.0);
  let tone = mix(vec3f(0.075, 0.066, 0.145), vec3f(0.150, 0.082, 0.135), low);
  o.ink = vec4f(tone * (1.18 - 0.36 * s.z), ${n(FLOCK.alpha)} * (0.72 + 0.28 * s.z));
  return o;
}

@fragment fn birdFs(v: BirdOut) -> @location(0) vec4f {
  let d = abs(v.uv.x) + abs(v.uv.y);
  let a = v.ink.a * (1.0 - smoothstep(0.55, 1.0, d));
  return vec4f(v.ink.rgb * a, a);
}
`;
