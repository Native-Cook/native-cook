// Clay Prompt — the glowing dust, written in TypeScript and compiled to WGSL by TypeGPU.
//
// 1,536 particles live in one storage buffer, in six slots of 256. A burst claims the next slot:
// either a word's dust (born at the word in the prompt box, thrown out sideways, then pulled by a
// damped spring onto the clay piece it becomes, fading as it lands) or the swirl round the blob
// (born on a ring round it, carried round by a tangential force, held on a ring that tightens while
// the clay melts and flings outwards when the picture opens). A compute pass integrates them every
// frame; a render pass draws each as an instanced quad with a hot core and a soft halo in its
// clay's colour, blended additively over the Skia canvas below.
//
// The functions marked 'use gpu' are TypeScript that unplugin-typegpu (Babel) hands to TypeGPU,
// which writes the WGSL; `tgpu.resolve` turns each pass into one self-contained module.
import { d, std, tgpu } from 'typegpu';

export const PER = 256;
export const SLOTS = 6;
export const COUNT = PER * SLOTS;
export const WORKGROUP = 64;

export const Particle = d.struct({
  pos: d.vec2f, // pt
  vel: d.vec2f, // pt/s
  goal: d.vec2f, // where a word's dust is going, pt
  life: d.f32, // 0 dead … 1
  age: d.f32, // s since birth
  hue: d.f32, // 0 cat, 1 sofa, 2 moon, 3 plant
  size: d.f32, // core radius, pt
  mode: d.f32, // 0 a word's dust, 1 the swirl
  seed: d.f32,
});

export const Params = d.struct({
  view: d.vec4f, // width pt, height pt, pixel ratio, time s
  step: d.vec4f, // dt s, slot to spawn (−1 none), kind (−1 swirl), generation g
  path: d.vec4f, // from x, y, to x, y (pt)
  blob: d.vec4f, // blob centre x, y (pt), blob radius pt, wipe 0…1
});

const simulateLayout = tgpu.bindGroupLayout({
  params: { uniform: Params },
  particles: { storage: d.arrayOf(Particle, COUNT), access: 'mutable' },
});
const drawLayout = tgpu.bindGroupLayout({
  params: { uniform: Params },
  particles: { storage: d.arrayOf(Particle, COUNT) },
});

const hash = (x: number, y: number) => {
  'use gpu';
  return std.fract(std.sin(x * 127.1 + y * 311.7) * 43758.5453);
};

const simulate = tgpu.computeFn({ in: { gid: d.builtin.globalInvocationId }, workgroupSize: [WORKGROUP] })((input) => {
  'use gpu';
  const i = input.gid.x;
  if (i >= COUNT) {
    return;
  }
  const P = simulateLayout.$.params;
  const p = Particle(simulateLayout.$.particles[i]);
  const dt = P.step.x;
  const fi = d.f32(i);
  const slot = std.floor(fi / PER);
  if (P.step.y >= 0 && std.abs(slot - P.step.y) < 0.5) {
    // born now
    const r1 = hash(fi, P.view.w);
    const r2 = hash(fi + 17.0, P.view.w * 1.3);
    const r3 = hash(fi + 41.0, P.view.w * 0.7);
    const a = r1 * 6.2831853;
    const dir = d.vec2f(std.cos(a), std.sin(a));
    const disk = std.mul(dir, std.sqrt(r2));
    p.age = 0;
    p.life = 1;
    p.seed = r3;
    p.size = 1.2 + r3 * r3 * 2.6;
    if (P.step.z >= 0) {
      p.mode = 0;
      p.hue = P.step.z;
      p.pos = std.add(P.path.xy, std.mul(disk, 12));
      p.goal = std.add(P.path.zw, std.mul(disk, 14));
      const along = std.normalize(std.add(std.sub(P.path.zw, P.path.xy), d.vec2f(0.001, 0.001)));
      const side = d.vec2f(-along.y, along.x);
      p.vel = std.add(std.add(std.mul(along, 120 + r2 * 220), std.mul(side, (r1 - 0.5) * 520)), d.vec2f(0, -140 - r3 * 160));
    } else {
      p.mode = 1;
      p.hue = std.floor(r3 * 3.999);
      const ring = P.blob.z * (1.25 + r2 * 0.9) + 18;
      p.pos = std.add(P.blob.xy, std.mul(dir, ring));
      p.goal = P.blob.xy;
      p.vel = std.mul(d.vec2f(-dir.y, dir.x), 260 + r1 * 200);
    }
  } else if (p.life > 0) {
    p.age = p.age + dt;
    if (p.mode < 0.5) {
      // a damped spring onto the clay, with a little wander so the stream is not a straight line
      const to = std.sub(p.goal, p.pos);
      const wander = std.mul(d.vec2f(std.sin(p.age * 9.0 + p.seed * 20.0), std.cos(p.age * 7.0 + p.seed * 13.0)), 260);
      const pull = std.smoothstep(0.0, 0.35, p.age) * 30.0;
      const acc = std.add(std.sub(std.mul(to, pull), std.mul(p.vel, 2.2 + pull * 0.18)), std.mul(wander, 1.0 - std.smoothstep(0.1, 0.5, p.age)));
      p.vel = std.add(p.vel, std.mul(acc, dt));
      const near = std.smoothstep(26.0, 4.0, std.length(to));
      p.life = std.clamp(p.life - dt * (0.45 + 4.0 * near * std.smoothstep(0.25, 0.4, p.age)), 0, 1);
    } else {
      // round the blob: carried round, held on a ring, flung out when the picture opens
      const g = P.step.w;
      const off = std.sub(p.pos, P.blob.xy);
      const r = std.max(std.length(off), 0.001);
      const dir = std.div(off, r);
      const open = std.smoothstep(0.48, 0.62, g);
      const ringR = std.mix(std.max(P.blob.z, 24.0) * (1.35 + 0.5 * p.seed), 520.0 * (0.6 + p.seed), open);
      const tangent = d.vec2f(-dir.y, dir.x);
      const swirl = std.mul(tangent, (520 * (1.0 - open * 0.7)) / (1.0 + r / 140.0));
      const hold = std.mul(dir, (ringR - r) * (8.0 + open * 10.0));
      const acc = std.sub(std.add(swirl, hold), std.mul(p.vel, 2.4));
      p.vel = std.add(p.vel, std.mul(acc, dt));
      p.life = std.clamp(p.life - dt * (0.35 + 1.4 * open), 0, 1);
    }
    p.pos = std.add(p.pos, std.mul(p.vel, dt));
    p.life = p.life * (1.0 - P.blob.w);
  }
  simulateLayout.$.particles[i] = Particle(p);
});

const DrawOut = {
  position: d.builtin.position,
  uv: d.vec2f,
  colour: d.vec3f,
  alpha: d.f32,
  halo: d.f32,
};

const tint = (hue: number) => {
  'use gpu';
  let c = d.vec3f(1.0, 0.63, 0.35);
  if (hue > 0.5) {
    c = d.vec3f(0.44, 0.64, 1.0);
  }
  if (hue > 1.5) {
    c = d.vec3f(1.0, 0.85, 0.42);
  }
  if (hue > 2.5) {
    c = d.vec3f(0.45, 0.85, 0.51);
  }
  return c;
};

const vert = tgpu.vertexFn({ in: { vi: d.builtin.vertexIndex, ii: d.builtin.instanceIndex }, out: DrawOut })((input) => {
  'use gpu';
  const P = drawLayout.$.params;
  const p = Particle(drawLayout.$.particles[input.ii]);
  // two triangles: (0,0) (1,0) (0,1) / (0,1) (1,0) (1,1)
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
  const alive = std.step(0.004, p.life);
  const halo = d.f32(4);
  const radius = p.size * (0.55 + 0.45 * p.life) * halo * alive;
  const at = std.add(p.pos, std.mul(corner, radius));
  const ndc = d.vec2f((at.x / P.view.x) * 2 - 1, 1 - (at.y / P.view.y) * 2);
  return {
    position: d.vec4f(ndc, 0, 1),
    uv: std.mul(corner, halo),
    colour: tint(p.hue),
    alpha: p.life,
    halo: halo,
  };
});

const frag = tgpu.fragmentFn({ in: { uv: d.vec2f, colour: d.vec3f, alpha: d.f32, halo: d.f32 }, out: d.vec4f })((input) => {
  'use gpu';
  const r2 = std.dot(input.uv, input.uv);
  const core = std.exp(-r2 * 1.6);
  const glow = std.exp(-r2 * 0.22) * 0.32;
  const hot = std.mix(input.colour, d.vec3f(1, 1, 1), core * 0.65);
  const a = (core + glow) * input.alpha;
  return d.vec4f(std.mul(hot, a), a * 0.6);
});

/** WGSL for the compute pass (group 0: params, particles read-write). */
export function simulateWGSL() {
  return tgpu.resolve([simulate]);
}
/** WGSL for the draw pass (group 0: params, particles read-only). */
export function drawWGSL() {
  return tgpu.resolve([vert, frag]);
}
/** Entry names in the resolved WGSL. */
export function entryName(code: string, stage: 'compute' | 'vertex' | 'fragment') {
  const m = code.match(new RegExp(`@${stage}[^{]*?fn\\s+(\\w+)`));
  if (!m) throw new Error(`Clay dust: no @${stage} entry in the generated WGSL.`);
  return m[1];
}
