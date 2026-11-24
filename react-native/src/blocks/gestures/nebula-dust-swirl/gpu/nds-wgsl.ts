// Nebula Dust Swirl — the dust on WebGPU, in plain WGSL.
//
// dustCs (compute, one dispatch a frame) works out each grain's place from pose(), the same closed form
// as nds-motion.ts, and integrates the one thing that is state: how far a finger has stirred it (disp,
// vel), on a spring back to zero. grainVs / grainFs then draw one soft round grain per particle, added
// together (additive light): the warm core burns where thousands overlap, the arms glow thinner.

import { CORE, DUST, LOOP, SPIRAL, TAU, TONE, TOUCH } from '../nds-motion';

const n = (v: number) => {
  const s = v.toFixed(5);
  return s.includes('.') ? s : `${s}.0`;
};
const v3 = (c: readonly number[]) => `vec3f(${n(c[0])}, ${n(c[1])}, ${n(c[2])})`;

/** FrameU in floats */
export const FRAME_FLOATS = 20;
/** Grain in bytes: pos, disp, vel, look */
export const GRAIN_BYTES = 32;

export const NDS_WGSL = /* wgsl */ `
struct FrameU {
  sim: vec4f,    // t, dt, clear (1 = forget the finger), hold (how much of the stir is shown)
  view: vec4f,   // width, height, centre x, centre y (pt)
  look: vec4f,   // pose unit (pt), -, -, -
  touch: vec4f,  // x, y (pt), strength 0…1, -
  push: vec4f,   // the finger's velocity x, y (pt/s), -, -
};
struct Grain { pos: vec2f, disp: vec2f, vel: vec2f, look: vec2f };

@group(0) @binding(0) var<uniform> F: FrameU;
@group(0) @binding(1) var<storage, read_write> grainsOut: array<Grain>;
@group(0) @binding(2) var<storage, read> grains: array<Grain>;

const LOOP_S = ${n(LOOP)};
const TAU_ = ${n(TAU)};
const PI_ = ${n(Math.PI)};

fn pcg(v: u32) -> u32 {
  let s = v * 747796405u + 2891336453u;
  let w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
  return (w >> 22u) ^ w;
}

fn unitOf(v: u32) -> f32 { return f32(pcg(v)) / 4294967296.0; }

// seeds 0…3 and 4…7 of a pair: radius, spread, size, phase | height, spread', kind, tone
fn seedA(pair: u32) -> vec4f { return vec4f(unitOf(pair * 8u), unitOf(pair * 8u + 1u), unitOf(pair * 8u + 2u), unitOf(pair * 8u + 3u)); }
fn seedB(pair: u32) -> vec4f { return vec4f(unitOf(pair * 8u + 4u), unitOf(pair * 8u + 5u), unitOf(pair * 8u + 6u), unitOf(pair * 8u + 7u)); }

// x, y in pose units around the centre, perspective scale, radius in the disc
fn pose(a: vec4f, b: vec4f, arm: f32, t: f32) -> vec4f {
  let w = TAU_ * t / LOOP_S;
  var x: f32;
  var y: f32;
  var h: f32;
  var r: f32;
  if (b.z < ${n(CORE.share)}) {
    r = ${n(CORE.radius)} * sqrt(-2.0 * log(1.0 - 0.97 * a.x)) * 0.62;
    let ang = TAU_ * a.y + arm * PI_;
    x = r * cos(ang);
    y = r * sin(ang);
    h = ${n(CORE.flat * CORE.radius)} * (b.x + b.y - 1.0);
  } else {
    r = ${n(SPIRAL.rMin)} + ${n(SPIRAL.rMax - SPIRAL.rMin)} * pow(a.x, ${n(SPIRAL.density)});
    r = r * (1.0 + ${n(SPIRAL.jitter)} * (b.y - 0.5));
    let tri = a.y + b.y - 1.0;
    let ang = arm * PI_ + ${n(SPIRAL.wind)} * log2(1.0 + 6.0 * r) + tri * (${n(SPIRAL.spread)} + ${n(SPIRAL.spreadOut)} * r);
    x = r * cos(ang);
    y = r * sin(ang);
    h = ${n(SPIRAL.thick)} * (b.x - 0.5) * (1.3 - r);
  }
  let inner = 1.0 - min(r, 1.0);
  let rot = PI_ * t / LOOP_S + ${n(SPIRAL.breath)} * inner * inner * sin(w + 1.3);
  let cr = cos(rot);
  let sr = sin(rot);
  var dx = x * cr - y * sr;
  var dy = x * sr + y * cr;
  let k = 1.0 + floor(a.z * 3.0);
  let rj = ${n(SPIRAL.orbit)} + ${n(SPIRAL.orbitAdd)} * a.z * a.z;
  dx += rj * cos(TAU_ * a.w + k * w);
  dy += rj * sin(TAU_ * a.w + k * w);
  let ct = ${n(Math.cos(SPIRAL.tilt))};
  let st = ${n(Math.sin(SPIRAL.tilt))};
  let sy = dy * ct - h * st;
  let z = dy * st + h * ct;
  let p = ${n(SPIRAL.camera)} / (${n(SPIRAL.camera)} - z);
  return vec4f(dx * p, sy * p, p, min(r, 1.0));
}

@compute @workgroup_size(${DUST.group}) fn dustCs(@builtin(global_invocation_id) gid: vec3u) {
  let i = gid.x;
  if (i >= ${DUST.count}u) { return; }
  var g = grainsOut[i];
  if (F.sim.z > 0.5) {
    g.disp = vec2f(0.0);
    g.vel = vec2f(0.0);
  }
  let pair = i >> 1u;
  let q = pose(seedA(pair), seedB(pair), f32(i & 1u), F.sim.x);
  let home = F.view.zw + q.xy * F.look.x;
  let dt = F.sim.y;

  let to = home + g.disp - F.touch.xy;
  let r2 = dot(to, to);
  let fall = F.touch.z * exp(-r2 / ${n(TOUCH.radius * TOUCH.radius)});
  let side = vec2f(-to.y, to.x) / sqrt(r2 + 1.0);
  let force = side * ${n(TOUCH.swirl)} * fall + (F.push.xy - g.vel) * ${n(TOUCH.drag)} * fall;
  g.vel += (force - ${n(TOUCH.spring)} * g.disp - ${n(TOUCH.damp)} * g.vel) * dt;
  g.disp += g.vel * dt;

  g.pos = home + g.disp * F.sim.w;
  g.look = q.zw;
  grainsOut[i] = g;
}

struct GrainOut {
  @builtin(position) pos: vec4f,
  @location(0) uv: vec2f,
  @location(1) ink: vec4f,
};

@vertex fn grainVs(@builtin(vertex_index) vi: u32, @builtin(instance_index) ii: u32) -> GrainOut {
  var corners = array<vec2f, 6>(vec2f(-1.0, -1.0), vec2f(1.0, -1.0), vec2f(1.0, 1.0), vec2f(-1.0, -1.0), vec2f(1.0, 1.0), vec2f(-1.0, 1.0));
  let c = corners[vi];
  let g = grains[ii];
  let pair = ii >> 1u;
  let a = seedA(pair);
  let b = seedB(pair);
  let p = g.look.x;
  let r = g.look.y;
  let core = b.z < ${n(CORE.share)};
  let star = !core && b.w > ${n(1 - DUST.starShare)};

  var rad = (${n(DUST.size)} + ${n(DUST.sizeAdd)} * a.z * a.z) * p;
  var col = mix(${v3(TONE.core)}, ${v3(TONE.warm)}, smoothstep(0.0, 0.3, r));
  col = mix(col, mix(${v3(TONE.cool)}, ${v3(TONE.teal)}, b.w), smoothstep(0.22, 0.72, r));
  var gain = select(${n(TONE.armGain)}, ${n(TONE.coreGain)}, core);
  if (!core && b.w < ${n(TONE.roseShare)}) {
    col = mix(col, ${v3(TONE.rose)}, 0.8);
  }
  if (star) {
    rad = ${n(DUST.star)} * p;
    col = ${v3(TONE.star)};
    gain = ${n(TONE.starGain)};
  }
  let beats = ${n(DUST.twinkle)} + floor(a.y * ${n(DUST.twinkleRange)});
  let twinkle = 0.72 + 0.28 * sin(TAU_ * beats * F.sim.x / LOOP_S + TAU_ * a.w);
  // nearer grains are a little brighter; the rim fades out into the dark
  let light = gain * twinkle * p * p * (1.0 - smoothstep(0.8, 1.02, r));

  let at = g.pos + c * rad * 1.6;
  var o: GrainOut;
  o.pos = vec4f(at.x / F.view.x * 2.0 - 1.0, 1.0 - at.y / F.view.y * 2.0, 0.0, 1.0);
  o.uv = c;
  o.ink = vec4f(col * light, light);
  return o;
}

@fragment fn grainFs(v: GrainOut) -> @location(0) vec4f {
  let a = exp(-dot(v.uv, v.uv) * 3.0);
  return vec4f(v.ink.rgb * a, v.ink.a * a * 0.5);
}
`;
