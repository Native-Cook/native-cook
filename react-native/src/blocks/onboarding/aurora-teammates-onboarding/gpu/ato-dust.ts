// Aurora Teammates Onboarding — the light dust, written in TypeScript and compiled to WGSL by TypeGPU.
//
// The dust carries the page change. 1,536 motes in three families, all stateless: each mote's place is
// a pure function of the loop time and the page change's progress, so the loop has no seam and a
// finger scrubbing a page change scrubs the dust with it.
//   - 768 ambient motes rise out of the notch along the rays, 2-4 lives a loop each.
//   - 384 "leaving" motes, 64 per object of the page that goes: they peel off the object while it folds
//     and stream into the light on a curve, accelerating, so the object seems to be drawn in as light.
//   - 384 "arriving" motes, 64 per object of the page that comes: they leave the light, arc out and
//     settle into the object while it unfolds, so it seems to be made of them; they fade as it lands.
// The finale (page 3's Next) borrows the two stream families: the "leaving" motes peel off the six
// tiles and stream down into the button instead of the light (`sink`), and the "arriving" ones burst
// out of the button in a fountain; neither is masked by the sheet then. The phone's tilt moves the
// ambient motes against the glass (nearer, brighter motes further).
// A compute pass writes every mote into a storage buffer; a draw pass renders each as an instanced quad
// with a hot core and a soft halo, blended additively over the tgfx canvas below. Motes never show
// below the sheet's edge (the notch's profile is evaluated per mote).
//
// The functions marked 'use gpu' are TypeScript that unplugin-typegpu (Babel) hands to TypeGPU, which
// writes the WGSL; the data layout is TypeGPU schemas; `tgpu.resolve` turns each pass into a module.
import { d, std, tgpu } from 'typegpu';

export const AMBIENT = 768;
export const PER_OBJECT = 64;
export const OBJECTS = 6;
export const STREAM = PER_OBJECT * OBJECTS;
export const COUNT = AMBIENT + 2 * STREAM;
export const WORKGROUP = 64;
export const LOOP_S = 9;

/** One object a stream leaves or reaches: centre x, y (px), radius (px), its own progress 0..1. */
export const Params = d.struct({
  view: d.vec4f, // canvas width, height (px), px per pt, loop time (s)
  core: d.vec4f, // light x, y (px), flare 0..1, page change progress u
  pal: d.vec4f, // page weights w0, w1, w2, sheet top (px)
  notch: d.vec4f, // notch half width (px), notch dip (px), tilt x, y (px)
  sink: d.vec4f, // finale: the button's centre x, y (px), 1 while the light streams there, -
  leaving: d.arrayOf(d.vec4f, OBJECTS),
  arriving: d.arrayOf(d.vec4f, OBJECTS),
});
export const PARAM_FLOATS = d.sizeOf(Params) / 4;

export const Mote = d.struct({
  place: d.vec4f, // x, y (px), size (px), alpha
  tint: d.vec4f, // colour, -
});
export const MOTE_BYTES = d.sizeOf(Mote);

const simulateLayout = tgpu.bindGroupLayout({
  params: { uniform: Params },
  motes: { storage: d.arrayOf(Mote, COUNT), access: 'mutable' },
});
const drawLayout = tgpu.bindGroupLayout({
  params: { uniform: Params },
  motes: { storage: d.arrayOf(Mote, COUNT) },
});

const hash = (x: number, y: number) => {
  'use gpu';
  return std.fract(std.sin(x * 127.1 + y * 311.7 + 1.7) * 43758.5453);
};

/** Left is always cool blue; the right follows the page: mint, lilac, ice. */
const tintOf = (side: number, w: d.v3f) => {
  'use gpu';
  const right = std.add(
    std.add(std.mul(d.vec3f(0.62, 1.0, 0.78), w.x), std.mul(d.vec3f(0.86, 0.66, 1.0), w.y)),
    std.mul(d.vec3f(0.62, 0.9, 1.0), w.z),
  );
  return std.mix(d.vec3f(0.64, 0.72, 1.0), right, std.clamp(side, 0.0, 1.0));
};

/** A quadratic Bézier from a through c to b. */
const bezier = (a: d.v2f, c: d.v2f, b: d.v2f, t: number) => {
  'use gpu';
  const s = 1.0 - t;
  return std.add(std.add(std.mul(a, s * s), std.mul(c, 2.0 * s * t)), std.mul(b, t * t));
};

/** How far below the sheet's edge a point is (px, > 0 = hidden): the notch's profile, close to its two cubic halves. */
const belowSheet = (p: d.v2f, core: d.v2f, top: number, half: number, dip: number) => {
  'use gpu';
  const xn = std.abs(p.x - core.x) / std.max(half, 1.0);
  let edge = top;
  if (xn < 1.0) {
    edge = top + dip * (1.0 - std.smoothstep(0.2, 1.0, xn));
  }
  return p.y - edge;
};

const simulate = tgpu.computeFn({ in: { gid: d.builtin.globalInvocationId }, workgroupSize: [WORKGROUP] })((input) => {
  'use gpu';
  const i = input.gid.x;
  if (i >= COUNT) {
    return;
  }
  const P = simulateLayout.$.params;
  const fi = d.f32(i);
  const px = P.view.z;
  const core = P.core.xy;
  const flare = P.core.z;
  const w = P.pal.xyz;
  const sinkOn = P.sink.z;
  const home = std.mix(core, P.sink.xy, sinkOn);
  let masked = d.f32(1);
  let pos = d.vec2f(core.x, core.y);
  let size = d.f32(0);
  let alpha = d.f32(0);
  let colour = d.vec3f(1, 1, 1);

  if (fi < AMBIENT) {
    // rising out of the notch along the rays, a whole number of lives per loop
    const k = 2.0 + std.floor(hash(fi, 3.17) * 3.0);
    const life = LOOP_S / k;
    const s = P.view.w / life + hash(fi, 1.31);
    const c = std.floor(s);
    const ph = std.fract(s);
    const cycle = c - k * std.floor(c / k);
    const r1 = hash(fi, cycle + 0.5);
    const r2 = hash(fi + 17.0, cycle);
    const r3 = hash(fi + 41.0, cycle);
    const r4 = hash(fi + 73.0, cycle);
    const x0 = r1 * 2.0 - 1.0;
    const theta = x0 * 1.1 + (r2 - 0.5) * 0.4;
    const dir = d.vec2f(std.sin(theta), -std.cos(theta));
    const age = ph * life;
    const speed = (18.0 + 46.0 * r3) * px * (1.0 + 0.8 * flare);
    const start = std.add(core, d.vec2f(x0 * P.notch.x * 0.85, 6.0 * px));
    pos = std.add(std.add(start, std.mul(dir, speed * age)), d.vec2f(std.sin(age * 1.7 + fi) * 5.0 * px, 0.0));
    pos = std.sub(pos, std.mul(P.notch.zw, 0.35 + 0.9 * r4));
    alpha = std.smoothstep(0.0, 0.14, ph) * (1.0 - std.smoothstep(0.35, 0.9, ph)) * (0.06 + 0.34 * r4 * r4) * (1.0 + 1.6 * flare);
    size = (0.45 + 1.1 * r4 * r4 * r4) * px;
    colour = std.mix(d.vec3f(0.94, 0.98, 1.0), tintOf(x0 * 0.5 + 0.5, w), 0.6);
  } else if (fi < AMBIENT + STREAM) {
    // peeling off a leaving object and drawn into the light
    const j = fi - AMBIENT;
    const obj = std.floor(j / PER_OBJECT);
    const src = P.leaving[d.u32(obj)];
    const e = hash(j, 5.3);
    const t = std.clamp((src.w - e * 0.45) / 0.55, 0.0, 1.0);
    if (src.z > 0.0 && t > 0.0 && t < 1.0) {
      const a1 = hash(j, 7.1) * 6.2831853;
      const rr = std.sqrt(hash(j, 9.7));
      const start = std.add(src.xy, std.mul(d.vec2f(std.cos(a1), std.sin(a1) * 0.6), src.z * 0.9 * rr));
      const mid = std.mul(std.add(start, home), 0.5);
      const side = (hash(j, 11.3) - 0.5) * 0.9 * std.length(std.sub(home, start));
      const ctrl = std.add(mid, d.vec2f(side, -40.0 * px * (1.0 - sinkOn)));
      pos = bezier(start, ctrl, home, t * t);
      masked = 1.0 - sinkOn;
      alpha = std.pow(std.sin(3.14159 * t), 0.7) * (0.35 + 0.55 * hash(j, 13.1)) * (1.0 + 0.7 * sinkOn);
      size = (0.6 + 1.5 * hash(j, 15.7)) * px * (1.0 + 0.6 * sinkOn);
      colour = std.mix(d.vec3f(1.0, 1.0, 1.0), tintOf(hash(j, 2.2), w), 0.4);
    }
  } else {
    // out of the light, settling into an arriving object as it unfolds
    const j = fi - AMBIENT - STREAM;
    const obj = std.floor(j / PER_OBJECT);
    const dst = P.arriving[d.u32(obj)];
    const e = hash(j, 21.9);
    const t = std.clamp((dst.w * 1.35 - e * 0.35) / 1.0, 0.0, 1.0);
    if (dst.z > 0.0 && t > 0.0 && t < 1.0) {
      const a1 = hash(j, 23.3) * 6.2831853;
      const rr = std.sqrt(hash(j, 25.1));
      const goal = std.add(dst.xy, std.mul(d.vec2f(std.cos(a1), std.sin(a1) * 0.6), dst.z * 0.85 * rr));
      const mid = std.mul(std.add(home, goal), 0.5);
      const side = (hash(j, 27.7) - 0.5) * 0.8 * std.length(std.sub(goal, home));
      const ctrl = std.add(mid, d.vec2f(side, -60.0 * px));
      const ease = 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t);
      pos = bezier(home, ctrl, goal, ease);
      masked = 1.0 - sinkOn;
      alpha = std.pow(std.sin(3.14159 * t), 0.8) * (0.4 + 0.6 * hash(j, 29.3));
      size = (0.6 + 1.7 * hash(j, 31.9)) * px;
      colour = std.mix(d.vec3f(1.0, 1.0, 1.0), tintOf(hash(j, 4.4), w), 0.35);
    }
  }
  // never below the sheet's edge
  const below = belowSheet(pos, core, P.pal.w, P.notch.x, P.notch.y);
  alpha = alpha * (1.0 - masked * std.smoothstep(-6.0 * px, 0.0, below));
  simulateLayout.$.motes[i] = Mote({
    place: d.vec4f(pos.x, pos.y, size, alpha),
    tint: d.vec4f(colour, 0),
  });
});

const DrawOut = {
  position: d.builtin.position,
  uv: d.vec2f,
  colour: d.vec3f,
  alpha: d.f32,
};

const vert = tgpu.vertexFn({ in: { vi: d.builtin.vertexIndex, ii: d.builtin.instanceIndex }, out: DrawOut })((input) => {
  'use gpu';
  const P = drawLayout.$.params;
  const m = Mote(drawLayout.$.motes[input.ii]);
  const v = input.vi;
  let corner = d.vec2f(-1, -1);
  if (v === 1 || v === 4) {
    corner = d.vec2f(1, -1);
  }
  if (v === 2 || v === 3) {
    corner = d.vec2f(-1, 1);
  }
  if (v === 5) {
    corner = d.vec2f(1, 1);
  }
  const reach = std.max(m.place.z, 0.0) * 3.2;
  const at = std.add(m.place.xy, std.mul(corner, reach));
  const ndc = d.vec2f((at.x / P.view.x) * 2 - 1, 1 - (at.y / P.view.y) * 2);
  return { position: d.vec4f(ndc, 0, 1), uv: corner, colour: m.tint.xyz, alpha: m.place.w };
});

const frag = tgpu.fragmentFn({ in: { uv: d.vec2f, colour: d.vec3f, alpha: d.f32 }, out: d.vec4f })((input) => {
  'use gpu';
  const r2 = std.dot(input.uv, input.uv);
  const i = (std.exp(-r2 * 16.0) + 0.3 * std.exp(-r2 * 3.0)) * input.alpha;
  return d.vec4f(std.mul(input.colour, i), i * 0.55);
});

/** WGSL for the compute pass (group 0: params, motes read-write). */
export function simulateWGSL() {
  return tgpu.resolve([simulate]);
}
/** WGSL for the draw pass (group 0: params, motes read-only). */
export function drawWGSL() {
  return tgpu.resolve([vert, frag]);
}
/** Entry names in the resolved WGSL. */
export function entryName(code: string, stage: 'compute' | 'vertex' | 'fragment') {
  const m = code.match(new RegExp(`@${stage}[^{]*?fn\\s+(\\w+)`));
  if (!m) throw new Error(`ATO dust: no @${stage} entry in the generated WGSL.`);
  return m[1];
}
