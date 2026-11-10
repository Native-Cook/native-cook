// Smoked Cloche Reveal — the cloche and its smoke on WebGPU, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout (the frame block and the smoke's records are `d.struct` schemas), and
// `tgpu.resolve` injects their WGSL into the template. A transparent canvas over the dish, each frame:
//  1. compute: 3,072 puffs of applewood smoke with state. Held, they swirl in a curl-noise flow inside
//     the cloche, pushed back by its rim; spilling, the rim is gone and they run outwards, swelling
//     and thinning as they rise towards the eye; refilled, they are born inside it again; a finger
//     drags them along and curls them round itself;
//  2. one full-screen pass: the glass cloche seen from above, a dome that magnifies the dish under it
//     (the dish's own photo, sampled through the lens), with a rim, window reflections and a glass
//     knob; lifted, it grows, blurs and fades as it rises towards the eye;
//  3. the smoke, as soft overlapping puffs.

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  view: d.vec4f, // width, height (pt), pixel ratio, time (s)
  bowl: d.vec4f, // the dish's centre x, y, the photo's half size (pt), the cloche's radius (pt)
  pose: d.vec4f, // lift 0…1, dish sent to the cart 0…1, smoke mode (0 held, 1 spilling, 2 refilled, 3 spawn now), dt (s)
  finger: d.vec4f, // x, y (pt), its move this frame dx, dy (pt)
  aux: d.vec4f, // finger down, -, -, -
});

export const PUFFS = 3072;
export const Puff = d.struct({
  at: d.vec4f, // x, y, vx, vy (pt, pt/s)
  st: d.vec4f, // alpha, size (pt), seed, age (s)
});

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;
@group(0) @binding(1) var<storage, read_write> puffsOut: array<Puff>;
@group(0) @binding(2) var<storage, read> puffs: array<Puff>;
@group(0) @binding(3) var dish: texture_2d<f32>;
@group(0) @binding(4) var samp: sampler;

const TAU = 6.2831853;
fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn hash1(n: f32) -> f32 { return fract(sin(n * 91.3458) * 47453.5453); }
fn hash2(p: vec2f) -> f32 { return fract(sin(dot(p, vec2f(127.1, 311.7))) * 43758.5453); }
fn noise2(x: vec2f) -> f32 {
  let i = floor(x); let f = fract(x); let u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash2(i), hash2(i + vec2f(1.0, 0.0)), u.x), mix(hash2(i + vec2f(0.0, 1.0)), hash2(i + vec2f(1.0, 1.0)), u.x), u.y);
}
// a divergence-free swirl: the curl of a slowly moving noise field
fn curl(p: vec2f, t: f32) -> vec2f {
  let e = 0.5;
  let q = p + vec2f(t * 0.6, -t * 0.4);
  let n1 = noise2(q + vec2f(0.0, e)) - noise2(q - vec2f(0.0, e));
  let n2 = noise2(q + vec2f(e, 0.0)) - noise2(q - vec2f(e, 0.0));
  let q2 = p * 2.1 + vec2f(-t * 0.9, t * 0.7) + 17.0;
  let m1 = noise2(q2 + vec2f(0.0, e)) - noise2(q2 - vec2f(0.0, e));
  let m2 = noise2(q2 + vec2f(e, 0.0)) - noise2(q2 - vec2f(e, 0.0));
  return (vec2f(n1, -n2) + 0.5 * vec2f(m1, -m2)) / (2.0 * e);
}

/* ------------------------------------------------------------------ the smoke */

@compute @workgroup_size(64) fn smokeCs(@builtin(global_invocation_id) gid: vec3u) {
  let i = gid.x;
  if (i >= ${PUFFS}u) { return; }
  let fi = f32(i);
  var p = puffsOut[i];
  let c = F.bowl.xy;
  let R = F.bowl.w;
  let dt = min(F.pose.w, 1.0 / 30.0);
  let mode = F.pose.z;
  if (mode > 2.5 || p.st.z == 0.0) {
    // born inside the cloche, fading in
    let a = hash1(fi + 1.0) * TAU;
    let r = sqrt(hash1(fi + 2.0)) * R * 0.88;
    p.at = vec4f(c + vec2f(cos(a), sin(a)) * r, 0.0, 0.0);
    p.st = vec4f(0.0, 14.0 + 16.0 * hash1(fi + 3.0), 0.001 + hash1(fi + 4.0), 0.0);
  }
  p.st.w += dt;
  var pos = p.at.xy;
  var vel = p.at.zw;
  // the swirl, and a slow turn about the cloche's middle
  let rel = pos - c;
  let flow = curl(pos * 0.012, F.view.w * 0.25 + p.st.z * 3.0) * 26.0 + vec2f(-rel.y, rel.x) / max(R, 1.0) * 14.0;
  vel += (flow - vel) * min(1.0, dt * 2.5);
  if (mode < 0.5 || mode > 1.5) {
    // held by the rim
    let rl = length(rel);
    if (rl > R * 0.9) { vel -= rel / max(rl, 1.0) * (rl - R * 0.9) * 12.0 * dt * 10.0; }
    p.st.x = min(1.0, p.st.x + dt / 0.9);
  } else {
    // the rim lifts: out from under it, swelling and thinning as the smoke rises to the eye
    let lift = F.pose.x;
    let rl = max(length(rel), 1.0);
    vel += rel / rl * (40.0 + 200.0 * lift) * smoothstep(0.45, 1.0, rl / R) * dt * 3.0;
    p.st.y += dt * (6.0 + 12.0 * lift);
    // thinning as it rises, and fast once it is out past the rim
    p.st.x = max(0.0, p.st.x - dt * ((0.14 + 0.22 * lift) * (0.6 + 0.8 * p.st.z) + 2.2 * smoothstep(R, 1.35 * R, rl)));
  }
  // the finger drags it along and curls it round itself
  if (F.aux.x > 0.5) {
    let fd = pos - F.finger.xy;
    let fl = length(fd);
    if (fl < 60.0) {
      let k = 1.0 - fl / 60.0;
      vel += F.finger.zw / max(dt, 0.004) * 0.6 * k * k * dt * 8.0;
      vel += vec2f(-fd.y, fd.x) / max(fl, 1.0) * 60.0 * k;
    }
  }
  pos += vel * dt;
  p.at = vec4f(pos, vel);
  puffsOut[i] = p;
}

struct SOut { @builtin(position) pos: vec4f, @location(0) uv: vec2f, @location(1) a: f32 }

@vertex fn smokeVs(@builtin(vertex_index) vi: u32, @builtin(instance_index) ii: u32) -> SOut {
  let p = puffs[ii];
  var corner = vec2f(-1.0, -1.0);
  if (vi == 1u || vi == 4u) { corner = vec2f(1.0, -1.0); }
  if (vi == 2u || vi == 3u) { corner = vec2f(-1.0, 1.0); }
  if (vi == 5u) { corner = vec2f(1.0, 1.0); }
  var r = p.st.y;
  if (p.st.x <= 0.002) { r = 0.0; }
  let at = p.at.xy + corner * r;
  var o: SOut;
  o.pos = vec4f(at.x / F.view.x * 2.0 - 1.0, 1.0 - at.y / F.view.y * 2.0, 0.0, 1.0);
  o.uv = corner;
  o.a = p.st.x;
  return o;
}

@fragment fn smokeFs(v: SOut) -> @location(0) vec4f {
  let r2 = dot(v.uv, v.uv);
  let a = exp(-r2 * 3.2) * (1.0 - smoothstep(0.8, 1.0, r2)) * v.a * 0.05;
  let c = vec3f(0.95, 0.93, 0.9);
  return vec4f(pow(c, vec3f(1.0 / 2.2)) * a, a);
}

/* ------------------------------------------------------------------ the cloche */

struct VOut { @builtin(position) pos: vec4f, @location(0) uv: vec2f }

@vertex fn fullVs(@builtin(vertex_index) vi: u32) -> VOut {
  let uv = vec2f(f32((vi << 1u) & 2u), f32(vi & 2u));
  var o: VOut;
  o.pos = vec4f(uv * vec2f(2.0, -2.0) + vec2f(-1.0, 1.0), 0.0, 1.0);
  o.uv = uv;
  return o;
}

fn dishAt(s: vec2f) -> vec4f {
  let uv = (s - F.bowl.xy) / (2.0 * F.bowl.z) + 0.5;
  if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) { return vec4f(0.0); }
  return textureSampleLevel(dish, samp, uv, 0.0);
}

fn studio(r: vec3f) -> vec3f {
  var c = mix(lin(vec3f(0.55, 0.52, 0.5)), lin(vec3f(0.95, 0.94, 0.93)), smoothstep(-0.4, 0.8, r.y));
  let k = normalize(vec3f(-0.5, 0.62, 0.6));
  let u = normalize(cross(k, vec3f(0.0, 0.0, 1.0)));
  let w = cross(u, k);
  let a = vec2f(dot(r, u), dot(r, w)) / max(dot(r, k), 0.05);
  c += vec3f(1.0, 0.99, 0.97) * 3.0 * (1.0 - smoothstep(0.16, 0.22, abs(a.x))) * (1.0 - smoothstep(0.3, 0.36, abs(a.y))) * step(0.0, dot(r, k)) * smoothstep(0.012, 0.03, abs(a.x));
  let f = normalize(vec3f(0.7, -0.35, 0.6));
  c += vec3f(1.0, 0.92, 0.82) * 0.7 * smoothstep(0.9, 0.98, dot(r, f));
  return c;
}

fn encode(c: vec3f) -> vec3f { return pow(clamp(c / (vec3f(1.0) + max(c - vec3f(0.85), vec3f(0.0)) * 0.6), vec3f(0.0), vec3f(1.0)), vec3f(1.0 / 2.2)); }

@fragment fn clocheFs(v: VOut) -> @location(0) vec4f {
  let s = v.pos.xy / F.view.z;
  let lift = F.pose.x;
  let fade = 1.0 - smoothstep(0.5, 0.98, lift);
  if (fade <= 0.0) { return vec4f(0.0); }
  // rising towards the eye: larger, softer
  let scale = 1.0 + 0.35 * lift * lift;
  let R = F.bowl.w * scale;
  let c = F.bowl.xy + vec2f(0.0, -18.0 * lift);
  let q = (s - c) / R;
  let r = length(q);
  if (r > 1.02) { return vec4f(0.0); }
  let h = sqrt(max(1.0 - r * r, 0.0));
  let n = normalize(vec3f(q.x, -q.y, h + 0.0001));
  // the dish through the dome: magnified in the middle, pulled in towards the rim, a little hazy
  let mag = 1.0 - 0.08 * h - 0.06 * (1.0 - h);
  let src = F.bowl.xy + (s - F.bowl.xy) * mag - vec2f(q.x, q.y) * 10.0 * (1.0 - h) * (1.0 - h);
  // softer as it rises: eight taps on a ring turned per pixel, so the blur has no pattern
  let blur = 0.6 + 6.0 * lift;
  let rot = fract(sin(dot(s, vec2f(12.9898, 78.233))) * 43758.5453) * TAU;
  var seen = vec4f(0.0);
  for (var k = 0; k < 8; k++) {
    let a = rot + f32(k) * TAU / 8.0;
    seen += dishAt(src + vec2f(cos(a), sin(a)) * blur * (0.5 + 0.5 * f32(k % 2)));
  }
  seen *= 0.125;
  // where the dish's photo is transparent (past the bowl) the dome shows the table, warm grey
  let table = vec4f(pow(vec3f(0.86, 0.83, 0.8), vec3f(2.2)), 1.0);
  let under = vec4f(pow(seen.rgb, vec3f(2.2)), seen.a) * seen.a + table * (1.0 - seen.a);
  let cosv = h;
  let fr = 0.04 + 0.96 * pow(1.0 - cosv, 5.0);
  let refl = studio(reflect(vec3f(0.0, 0.0, -1.0), n));
  var col = under.rgb * (0.94 + 0.06 * h) * (1.0 - fr) + refl * fr;
  // the rim: a thick rolled edge of glass, bright on its inner line
  let rim = smoothstep(0.9, 0.97, r) * (1.0 - smoothstep(0.985, 1.02, r));
  col = mix(col, col * 0.82 + refl * 0.25, rim);
  col += vec3f(1.0) * 0.35 * exp(-sq((r - 0.965) / 0.008));
  // the knob: a small glass ball on top
  let kq = (s - c) / (0.11 * R);
  let kr = length(kq);
  if (kr < 1.0) {
    let kh = sqrt(1.0 - kr * kr);
    let kn = normalize(vec3f(kq.x, -kq.y, kh));
    let kf = 0.04 + 0.96 * pow(1.0 - kh, 5.0);
    let kref = studio(reflect(vec3f(0.0, 0.0, -1.0), kn));
    let kunder = dishAt(c + (s - c) * -0.6).rgb;
    col = mix(col, pow(kunder, vec3f(2.2)) * 0.85 * (1.0 - kf) + kref * kf + vec3f(1.0) * pow(max(dot(reflect(-normalize(vec3f(-0.5, 0.62, 0.6)), kn), vec3f(0.0, 0.0, 1.0)), 0.0), 60.0), 1.0 - smoothstep(0.92, 1.0, kr));
  } else {
    // the knob's soft shadow on the glass
    col *= 1.0 - 0.18 * exp(-sq((kr - 1.0) / 0.5)) * smoothstep(-0.2, 0.6, dot(kq, vec2f(0.6, 0.8)));
  }
  let a = (1.0 - smoothstep(0.995, 1.02, r)) * fade;
  return vec4f(encode(col) * a, a);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU, Puff } });
}
