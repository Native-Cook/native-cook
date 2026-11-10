// Wind Chime Tones — the speaker and its sound on WebGPU, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout (the frame block, with its array of rings, is a `d.struct` schema), and
// `tgpu.resolve` injects its WGSL into the template. A canvas behind the page's cards:
//  - the room: the page's grey with a soft pool of light behind the speaker, lit up a little by every
//    chime;
//  - the rings: each chime sends one out from the speaker, a thin bright front with a faint wake,
//    fading as it spreads;
//  - the speaker: a disc of woven fabric on a slim brass yoke and stand, its cone pushing the fabric
//    out with each chime (the weave's highlight sliding over the dome, a ripple running out across it),
//    a brushed rim, a soft shadow on the floor. Switched off, the fabric dulls.

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  view: d.vec4f, // width, height (pt), pixel ratio, time (s)
  speaker: d.vec4f, // centre x, y, radius (pt), the cone's push −1…1
  state: d.vec4f, // power 0…1, the chimes' glow 0…1, -, -
  rings: d.arrayOf(d.vec4f, 4), // eight rings as (age s, loudness) pairs, age −1 for none
});

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;

const PI = 3.14159265;
const SPEED = 420.0;
fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn sdSeg(p: vec2f, a: vec2f, b: vec2f) -> f32 { let pa = p - a; let ba = b - a; let h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }

fn ring(i: i32) -> vec2f {
  let v = F.rings[i / 2];
  if (i % 2 == 0) { return v.xy; }
  return v.zw;
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

@fragment fn roomFs(v: VOut) -> @location(0) vec4f {
  let s = v.pos.xy / F.view.z;
  let c = F.speaker.xy;
  let R = F.speaker.z;
  let power = F.state.x;
  let p = s - c;
  let r = length(p);
  // the room: grey, a pool of light behind the speaker that breathes with the chimes
  var col = lin(vec3f(0.165));
  col += lin(vec3f(0.3, 0.29, 0.28)) * exp(-sq(r / (R * 1.9))) * (0.55 + 0.35 * F.state.y) * (0.5 + 0.5 * power);
  // the rings
  for (var i = 0; i < 8; i++) {
    let g = ring(i);
    if (g.x < 0.0) { continue; }
    let front = R + g.x * SPEED;
    let fade = g.y * exp(-g.x * 1.1) * power;
    let dd = r - front;
    col += lin(vec3f(0.98, 0.95, 0.9)) * (exp(-sq(dd / 1.6)) * 0.35 + exp(-sq((dd + 14.0) / 12.0)) * 0.05) * fade;
  }
  // the stand: a slim brass yoke round the lower half, a post and a round foot, and their shadow
  let yoke = abs(r - R * 1.1) - 1.6;
  let lower = smoothstep(-0.15, 0.1, p.y / R);
  let post = sdSeg(s, c + vec2f(0.0, R * 1.1), c + vec2f(0.0, R * 1.78)) - 2.2;
  let foot = length((s - c - vec2f(0.0, R * 1.8)) * vec2f(1.0, 3.6)) - R * 0.3;
  let brass = lin(vec3f(0.78, 0.6, 0.38));
  let floorSh = exp(-sq(length((s - c - vec2f(0.0, R * 1.86)) * vec2f(0.8, 3.0)) / (R * 0.55)));
  col *= 1.0 - 0.45 * floorSh;
  var stand = 1e9;
  stand = min(stand, select(1e9, yoke, lower > 0.5));
  stand = min(stand, post);
  if (foot < 0.0) {
    col = brass * (0.35 + 0.4 * smoothstep(R * 0.3, -R * 0.3, s.x - c.x)) * (0.7 + 0.3 * smoothstep(0.0, -6.0, foot));
  }
  if (stand < 0.0) {
    let lit = 0.55 + 0.45 * smoothstep(1.6, -1.6, stand);
    col = brass * lit * (0.6 + 0.6 * smoothstep(R, -R, s.x - c.x + p.y * 0.4));
  }
  // the speaker's shadow on the wall behind
  col *= 1.0 - 0.35 * exp(-sq(max(length(p - vec2f(10.0, 14.0)) - R, 0.0) / 14.0)) * step(R, r);
  // the speaker
  if (r < R + 1.5) {
    let q = p / R;
    let rr = length(q);
    // the cone pushes the fabric out into a dome, and a ripple runs out across it
    let push = F.speaker.w * power;
    let dome = 0.12 + 0.45 * push;
    let h = sqrt(max(1.0 - rr * rr, 0.0));
    var n = normalize(vec3f(q * dome / max(h, 0.15), 1.0));
    n = normalize(n + vec3f(q / max(rr, 0.01) * sin(rr * 18.0 - F.view.w * 30.0) * 0.06 * abs(push), 0.0));
    let L = normalize(vec3f(-0.55, -0.6, 0.6));
    let diff = max(dot(n, L), 0.0);
    // the weave: two sets of fine threads, over and under
    let wv = s * 0.95;
    let tx = 0.5 + 0.5 * sin(wv.x * PI) * sign(sin(wv.y * PI * 0.5));
    let ty = 0.5 + 0.5 * sin(wv.y * PI) * sign(sin(wv.x * PI * 0.5 + PI * 0.5));
    let weave = 0.72 + 0.28 * max(tx, ty);
    var fab = lin(vec3f(0.095, 0.095, 0.1)) * weave * (0.55 + 0.9 * diff) * (0.55 + 0.45 * power);
    fab += vec3f(1.0) * 0.035 * pow(max(dot(reflect(-L, n), vec3f(0.0, 0.0, 1.0)), 0.0), 4.0) * weave * (0.4 + 0.6 * power);
    // the rim: a thin brushed ring, lit at its top left
    let rim = exp(-sq((rr - 0.985) / 0.012));
    let rimLit = 0.25 + 0.75 * smoothstep(0.4, -0.8, q.x + q.y);
    fab = mix(fab, lin(vec3f(0.55)) * rimLit, rim * 0.9);
    fab *= 1.0 - 0.35 * smoothstep(0.9, 1.0, rr) * (1.0 - rim);
    let a = 1.0 - smoothstep(1.0, 1.0 + 1.2 / R, rr);
    col = mix(col, fab, a);
  }
  return vec4f(encode(col), 1.0);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU } });
}
