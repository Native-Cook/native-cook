// Eyewear Lens Tint — the lenses on WebGPU, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout (the frame block is a `d.struct` schema), and `tgpu.resolve` injects
// its WGSL into the template. A transparent canvas over the portrait draws only the two lenses: the
// face behind each, seen through it a little smaller (a minus lens), the light kept by the tint
// (darker towards the top, as a gradient lens is), the studio's window as a curved highlight, a soft
// glow of the tint's own colour in its lower half, a glare that sweeps across with the finger, and a
// bright rim. A new tint pours in from where its droplet was let go: its front spreads out and runs
// down under gravity with a wavering, bright edge, and crosses the bridge into the other lens.

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  view: d.vec4f, // width, height (pt), pixel ratio, time (s)
  lensA: d.vec4f, // the left lens's centre x, y, radius (pt), -
  lensB: d.vec4f, // the right lens's centre x, y, radius (pt), -
  tint: d.vec4f, // from, to (0 midnight, 1 rose, 2 amber, 3 sea), pour 0…1, the lens it was let go on
  glare: d.vec4f, // the glare's sweep −1…1, -, -, -
  photo: d.vec4f, // where the portrait lies: left, top, width, height (pt)
});

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;
@group(0) @binding(1) var photo: texture_2d<f32>;
@group(0) @binding(2) var samp: sampler;

const PI = 3.14159265;
fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn ease(x: f32) -> f32 { let c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }

// the tints (keep in step with TINTS in elt-theme.ts): the light each keeps, and its own glow
fn keepOf(i: f32) -> vec3f {
  if (i < 0.5) { return vec3f(0.05, 0.07, 0.11); }
  if (i < 1.5) { return vec3f(0.75, 0.32, 0.38); }
  if (i < 2.5) { return vec3f(0.72, 0.42, 0.1); }
  return vec3f(0.12, 0.42, 0.4);
}
fn glowOf(i: f32) -> vec3f {
  if (i < 0.5) { return vec3f(0.25, 0.55, 0.6); }
  if (i < 1.5) { return vec3f(1.0, 0.75, 0.8); }
  if (i < 2.5) { return vec3f(1.0, 0.85, 0.5); }
  return vec3f(0.5, 0.95, 0.9);
}

// the portrait, placed so its glasses sit where the reference's do
fn photoAt(s: vec2f) -> vec3f {
  let uv = (s - F.photo.xy) / F.photo.zw;
  return lin(textureSampleLevel(photo, samp, clamp(uv, vec2f(0.001), vec2f(0.999)), 0.0).rgb);
}

// how far the new tint has reached at a point of a lens (q: in lens radii from its centre). The pour
// starts on the lens it was let go on, high on its outer side, and reaches the other one through the
// bridge, a little later, from its inner side
fn pourMask(q: vec2f, which: f32) -> vec2f {
  let k = F.tint.z;
  if (k <= 0.0) { return vec2f(0.0); }
  let first = abs(which - F.tint.w) < 0.5;
  var start = vec2f(0.0);
  var t0 = 0.0;
  // the side away from the nose: the right lens's outer side is +x, the left's is −x
  let outer = select(-1.0, 1.0, which > 0.5);
  if (first) { start = vec2f(0.45 * outer, -0.55); t0 = 0.0; } else { start = vec2f(-0.85 * outer, -0.2); t0 = 0.3; }
  let u = clamp((k - t0) / (1.0 - t0), 0.0, 1.0);
  let p = q - start;
  // it runs down faster than it spreads up
  let dd = length(p * vec2f(1.0, 0.8)) - max(p.y, 0.0) * 0.45;
  let wob = sin(atan2(p.y, p.x) * 5.0 + F.view.w * 4.0) * 0.05 + sin(atan2(p.y, p.x) * 9.0 - F.view.w * 6.0) * 0.025;
  let front = u * u * (3.0 - 2.0 * u) * 2.4;
  let m = 1.0 - smoothstep(front - 0.08, front + 0.02, dd + wob);
  let edge = exp(-sq((dd + wob - front + 0.03) / 0.03)) * step(0.001, u) * (1.0 - step(0.999, u));
  return vec2f(m, edge);
}

struct Px { col: vec3f, a: f32 }

fn lensPx(s: vec2f, c: vec2f, r: f32, which: f32) -> Px {
  var o: Px;
  o.col = vec3f(0.0);
  o.a = 0.0;
  let q = (s - c) / r;
  let rr = length(q);
  if (rr > 1.0) { return o; }
  // the face behind, a little smaller through a minus lens
  let behind = photoAt(c + (s - c) * (0.93 - 0.04 * rr * rr));
  let pm = pourMask(q, which);
  let keep = mix(keepOf(F.tint.x), keepOf(F.tint.y), pm.x);
  let glw = mix(glowOf(F.tint.x), glowOf(F.tint.y), pm.x);
  // a gradient lens: darker at the top
  let grad = mix(0.55, 1.15, ease(q.y * 0.6 + 0.55));
  var col = behind * keep * grad;
  // the tint's own glow low in the lens, as the reference's teal
  col += lin(glw) * 0.16 * ease((q.y - 0.05) / 0.7) * (1.0 - smoothstep(0.75, 1.0, rr));
  // the studio's window: a soft curved highlight on the upper left of the lens, faint enough to read as
  // a reflection on dark glass (a full bright arc read as an eyelid)
  let hl = length(q - vec2f(-0.05, 0.05)) - 0.78;
  let win = exp(-sq(hl / 0.07)) * smoothstep(-0.25, -0.65, q.y) * smoothstep(-0.95, -0.45, q.x) * smoothstep(0.3, -0.15, q.x);
  col += vec3f(1.0) * win * 0.36;
  // the glare: a soft diagonal streak that sweeps with the finger
  let g = F.glare.x;
  let gd = dot(q, normalize(vec2f(1.0, -0.7))) - g * 0.9;
  col += vec3f(1.0, 0.98, 0.95) * 0.35 * exp(-sq(gd / 0.12)) * (0.12 + 0.88 * abs(g));
  // the new tint's wavering front, catching the light
  col += lin(glw) * 0.5 * pm.y;
  // a brighter rim where the lens meets its frame
  col += vec3f(0.9, 0.95, 1.0) * 0.08 * smoothstep(0.85, 1.0, rr);
  o.col = col;
  o.a = 1.0 - smoothstep(0.97, 1.0, rr);
  return o;
}

struct VOut { @builtin(position) pos: vec4f, @location(0) uv: vec2f }

@vertex fn fullVs(@builtin(vertex_index) vi: u32) -> VOut {
  let uv = vec2f(f32((vi << 1u) & 2u), f32(vi & 2u));
  var o: VOut;
  o.pos = vec4f(uv * vec2f(2.0, -2.0) + vec2f(-1.0, 1.0), 0.0, 1.0);
  o.uv = uv;
  return o;
}

fn encode(c: vec3f) -> vec3f { return pow(clamp(c / (vec3f(1.0) + max(c - vec3f(0.85), vec3f(0.0)) * 0.6), vec3f(0.0), vec3f(1.0)), vec3f(1.0 / 2.2)); }

@fragment fn lensFs(v: VOut) -> @location(0) vec4f {
  let s = v.pos.xy / F.view.z;
  var px = lensPx(s, F.lensA.xy, F.lensA.z, 0.0);
  if (px.a <= 0.0) { px = lensPx(s, F.lensB.xy, F.lensB.z, 1.0); }
  if (px.a <= 0.0) { return vec4f(0.0); }
  return vec4f(encode(px.col) * px.a, px.a);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU } });
}
