// Filament Bulb Dimmer — the WebGPU scene, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data: the frame block and the dust's records are `d.struct` schemas, written from
// JS with typed `buffer.write()`, and `tgpu.resolve` injects their WGSL definitions into the template
// (the names FrameU, Mote and Spark are replaced by the resolved structs).
//
// One frame, in HDR (rgba16float):
//  1. compute: 2,600 motes of dust, each a pure function of the loop's time and of where the bulb was
//     over the last 48 frames (so a swing stirs the air behind it and the dust settles back);
//  2. the scene, one full-screen pass: the page lit by the bulb (inverse-square pool and the glow of
//     the air round it), the cord, and the bulb ray-traced in 3D — a brass socket and threaded cap
//     (sphere-traced), the glass envelope (Fresnel reflection of the room and the UI, the room seen
//     through it, the filament mirrored in its back wall) and inside it an Edison squirrel cage of
//     16 glowing strands, 16 support wires and a glass stem, all found analytically ray against
//     segment;
//  3. the motes, additive, as soft discs that grow out of focus away from the bulb's plane;
//  4. bloom: six half-size levels down (soft threshold), tent filters back up;
//  5. tone and output: linear below 0.75 so the page keeps the pin's colours, a shoulder above,
//     white-hot cores, sRGB, and Display P3 when the surface wants it.

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  view: d.vec4f, // width, height (pt), pixel ratio, time in the loop (s)
  anchor: d.vec4f, // cord's anchor x, y (pt), R (pt), depth of the bulb in front of the page (pt)
  bulb: d.vec4f, // socket top x, y (pt), tilt, twist (rad)
  glow: d.vec4f, // cord sag (pt), heat 0…1, light output 0…1, filament radiance
  light: d.vec4f, // the light's linear colour (max 1), the switch's on-ness
  accent: d.vec4f, // the band's linear colour (for reflections), P3 output flag
  arc: d.vec4f, // dial centre x, y, outer and inner radius (pt)
  card: d.vec4f, // card x, y, w, h (pt)
  chips: d.vec4f, // left chip x, right chip x, chips y, chip w (pt)
  misc: d.vec4f, // chip h, dial disc radius, band leg (pt), dust gain
});

export const HISTORY = 48;
export const HistU = d.arrayOf(d.vec4f, HISTORY);

export const Mote = d.struct({
  home: d.vec4f, // x, y, z (pt), core size (pt)
  seed: d.vec4f, // phases and frequencies
});
export const Spark = d.struct({
  at: d.vec4f, // x, y (pt), radius (pt), brightness
  tint: d.vec4f, // rgb, circle of confusion (pt)
});

export const MOTES = 2600;

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;
@group(0) @binding(1) var<uniform> hist: array<vec4f, ${HISTORY}>;
@group(0) @binding(2) var<storage, read> motes: array<Mote>;
@group(0) @binding(3) var<storage, read_write> sparksOut: array<Spark>;
@group(0) @binding(4) var<storage, read> sparks: array<Spark>;
@group(0) @binding(5) var srcTex: texture_2d<f32>;
@group(0) @binding(6) var lowTex: texture_2d<f32>;
@group(0) @binding(7) var samp: sampler;

const TAU = 6.2831853;
const PI = 3.14159265;
// the bulb, in R, down from the socket's top
const Y_SOCKET = 0.5;
const Y_NECK = 1.12;
const Y_GLOBE = 2.74;
const Y_TOP = 2.16;     // the cage's top ring of hooks
const Y_BOT = 2.98;     // its bottom ring
const R_TOP = 0.27;
const R_BOT = 0.43;
const R_FIL = 0.012;
const R_WIRE = 0.0065;
const GLASS = 0.022;    // the envelope's thickness

fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }

fn seg2(p: vec2f, a: vec2f, b: vec2f) -> f32 {
  let pa = p - a; let ba = b - a;
  return length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0));
}
fn smin(a: f32, b: f32, k: f32) -> f32 {
  let h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
  return mix(b, a, h) - k * h * (1.0 - h);
}
fn sdRound(p: vec2f, r: vec4f, rad: f32) -> f32 {
  let c = r.xy + r.zw * 0.5;
  let q = abs(p - c) - r.zw * 0.5 + vec2f(rad);
  return length(max(q, vec2f(0.0))) + min(max(q.x, q.y), 0.0) - rad;
}

/* ------------------------------------------------------------------ the page and the light on it */

fn pageBase(p: vec2f) -> vec3f {
  // the pin's page, #262630, a touch warmer towards the top left
  var c = vec3f(0.149, 0.149, 0.188);
  let ty = 1.0 - smoothstep(0.0, 0.5, p.y / F.view.y);
  let tx = 1.0 - smoothstep(0.0, 0.8, p.x / F.view.x);
  c = mix(c, vec3f(0.173, 0.149, 0.188), ty * tx * 0.8);
  return lin(c);
}

fn filamentAt() -> vec2f {
  let R = F.anchor.z;
  return F.bulb.xy + vec2f(sin(F.bulb.z), cos(F.bulb.z)) * 2.55 * R;
}

// the light falling on the page (and on anything flat on it) at p
fn irradiance(p: vec2f) -> f32 {
  let R = F.anchor.z;
  let zl = F.anchor.w;
  let d = p - filamentAt();
  let d2 = dot(d, d);
  let e = zl * zl * zl / pow(d2 + zl * zl, 1.5);
  // the socket shades the light upwards a little
  let up = smoothstep(0.5, -1.0, d.y / max(sqrt(d2), 1.0));
  return e * (1.0 - 0.3 * up) + 0.012 * exp(-sqrt(d2) / (5.0 * R));
}

fn pageLit(p: vec2f) -> vec3f {
  let L = F.light.xyz * F.glow.z;
  // and a soft cone falling from the bulb onto the dial below it
  let R = F.anchor.z;
  let d = p - filamentAt();
  let down = d.y / R;
  let cone = smoothstep(0.6, 1.6, down) * (1.0 - smoothstep(2.6, 5.6, down)) * (1.0 - smoothstep(0.9 + 0.35 * down, 1.5 + 0.55 * down, abs(d.x) / R));
  return pageBase(p) + L * (0.032 * irradiance(p) + 0.006 * cone);
}

// what the glass and the brass reflect when they look back at the page: the page and the app on it
fn pageSeen(p: vec2f) -> vec3f {
  var c = pageLit(p);
  let L = F.light.xyz * F.glow.z * irradiance(p);
  if (sdRound(p, F.card, 24.0) < 0.0) { c = lin(vec3f(0.239, 0.22, 0.247)) * (1.0 + 2.0 * L); }
  let chip = vec4f(0.0, F.chips.z, F.chips.w, F.misc.x);
  if (sdRound(p, vec4f(F.chips.x, chip.yzw), 16.0) < 0.0 || sdRound(p, vec4f(F.chips.y, chip.yzw), 16.0) < 0.0) {
    c = lin(vec3f(0.169, 0.165, 0.196)) * (1.0 + 2.0 * L);
  }
  // the dial's band glows in the accent colour, its disc is white
  let q = p - F.arc.xy;
  let rm = (F.arc.z + F.arc.w) * 0.5;
  let bw = (F.arc.z - F.arc.w) * 0.5;
  var band = 1e5;
  if (q.y < 0.0) { band = abs(length(q) - rm) - bw; } else { band = length(vec2f(abs(abs(q.x) - rm), max(q.y - F.misc.z, 0.0))) - bw; }
  c += F.accent.xyz * F.light.w * 1.6 * (1.0 - smoothstep(-1.0, 1.0, band)) + F.accent.xyz * F.light.w * 0.25 * exp(-max(band, 0.0) / 14.0);
  if (length(q - vec2f(0.0, -12.0)) < F.misc.y) { c = vec3f(0.85); }
  return c;
}

// the room in front of the screen: dark, a tall soft window up on the left, a cool fill on the right
fn roomFront(r: vec3f, hi: f32) -> vec3f {
  // a dim warm room: brighter overhead, a soft floor bounce
  var c = mix(lin(vec3f(0.1, 0.095, 0.11)), lin(vec3f(0.34, 0.31, 0.32)), smoothstep(0.3, -0.8, r.y));
  let k = normalize(vec3f(-0.46, -0.62, 0.64));
  let u = normalize(cross(k, vec3f(0.0, 1.0, 0.0)));
  let v = cross(u, k);
  let a = vec2f(dot(r, u), dot(r, v)) / max(dot(r, k), 0.05);
  let win = (1.0 - smoothstep(0.15, 0.2, abs(a.x))) * (1.0 - smoothstep(0.27, 0.33, abs(a.y))) * step(0.0, dot(r, k));
  // a tall window in two panes
  c += vec3f(1.0, 0.97, 0.93) * 16.0 * hi * win * smoothstep(0.012, 0.03, abs(a.x));
  // a broad soft glow round it, and a cool fill on the right
  c += lin(vec3f(0.5, 0.48, 0.5)) * 0.6 * smoothstep(0.7, 0.97, dot(r, k));
  let f = normalize(vec3f(0.85, -0.05, 0.52));
  c += lin(vec3f(0.45, 0.5, 0.62)) * 1.2 * smoothstep(0.88, 0.99, dot(r, f));
  return c;
}

fn env(o: vec3f, r: vec3f, hi: f32) -> vec3f {
  if (r.z < -0.02) {
    let t = -o.z / r.z;
    return pageSeen(o.xy + r.xy * t);
  }
  return roomFront(r, hi);
}

/* ------------------------------------------------------------------ the cord */

fn cordDist(p: vec2f) -> f32 {
  let a = F.anchor.xy;
  let b = F.bulb.xy;
  let c = (a + b) * 0.5 + vec2f(0.0, F.glow.x * 2.0);
  var dmin = 1e5;
  var prev = a;
  for (var i = 1; i <= 10; i++) {
    let t = f32(i) / 10.0;
    let q = mix(mix(a, c, t), mix(c, b, t), t);
    dmin = min(dmin, seg2(p, prev, q));
    prev = q;
  }
  return dmin;
}

/* ------------------------------------------------------------------ the bulb, in its own frame (R) */

fn glassProfile(r: f32, y: f32) -> f32 {
  let sph = length(vec2f(r, y - Y_GLOBE)) - 1.0;
  let neck = seg2(vec2f(r, y), vec2f(0.0, Y_NECK), vec2f(0.0, Y_GLOBE - 0.5)) - 0.3;
  return max(smin(sph, neck, 0.45), Y_NECK - 0.02 - y);
}
fn glassSdf(p: vec3f) -> f32 { return glassProfile(length(p.xz), p.y); }
fn glassNormal(p: vec3f) -> vec3f {
  let r = max(length(p.xz), 1e-4);
  let e = 0.002;
  let gr = glassProfile(r + e, p.y) - glassProfile(r - e, p.y);
  let gy = glassProfile(r, p.y + e) - glassProfile(r, p.y - e);
  return normalize(vec3f(gr * p.x / r, gy, gr * p.z / r));
}

// the socket (0) and the threaded cap (1): x distance, y material
fn metalSdf(p: vec3f) -> vec2f {
  let r = length(p.xz);
  let a = atan2(p.z, p.x);
  var rs = 0.4;
  rs += 0.007 * smoothstep(0.09, 0.1, p.y) * (1.0 - smoothstep(0.19, 0.2, p.y)) * abs(sin(a * 28.0));
  rs -= 0.014 * (exp(-sq((p.y - 0.33) / 0.012)) + exp(-sq((p.y - 0.42) / 0.012)));
  let qs = vec2f(r - (rs - 0.06), abs(p.y - 0.25) - 0.19);
  let socket = length(max(qs, vec2f(0.0))) + min(max(qs.x, qs.y), 0.0) - 0.06;
  let th = 1.0 - abs(fract((p.y - Y_SOCKET) * 9.0 + a / TAU) * 2.0 - 1.0);
  let rc = 0.322 + 0.026 * smoothstep(0.12, 0.88, th);
  let qc = vec2f(r - rc, abs(p.y - 0.81) - 0.31);
  let cap = length(max(qc, vec2f(0.0))) + min(max(qc.x, qc.y), 0.0);
  if (socket < cap) { return vec2f(socket, 0.0); }
  return vec2f(cap, 1.0);
}
fn metalNormal(p: vec3f) -> vec3f {
  let e = 0.0025;
  let k1 = vec3f(1.0, -1.0, -1.0); let k2 = vec3f(-1.0, -1.0, 1.0); let k3 = vec3f(-1.0, 1.0, -1.0); let k4 = vec3f(1.0, 1.0, 1.0);
  return normalize(k1 * metalSdf(p + k1 * e).x + k2 * metalSdf(p + k2 * e).x + k3 * metalSdf(p + k3 * e).x + k4 * metalSdf(p + k4 * e).x);
}

// the closest approach of a ray (o + t d, |d| = 1) to the segment a-b: (distance, t)
fn raySeg(o: vec3f, dir: vec3f, a: vec3f, b: vec3f) -> vec2f {
  let ba = b - a; let oa = o - a;
  let dd = dot(dir, ba); let bb = dot(ba, ba);
  let od = dot(oa, dir); let ob = dot(oa, ba);
  let u = clamp((ob - od * dd) / max(bb - dd * dd, 1e-6), 0.0, 1.0);
  let t = u * dd - od;
  return vec2f(length(oa + dir * t - ba * u), t);
}
fn hookTop(i: i32) -> vec3f { let a = f32(i) * TAU / 8.0; return vec3f(R_TOP * cos(a), Y_TOP, R_TOP * sin(a)); }
fn hookBot(i: i32) -> vec3f { let a = (f32(i) + 0.5) * TAU / 8.0; return vec3f(R_BOT * cos(a), Y_BOT, R_BOT * sin(a)); }
fn strandA(k: i32) -> vec3f { if (k % 2 == 0) { return hookTop(k / 2); } return hookBot(k / 2); }
fn strandB(k: i32) -> vec3f { if (k % 2 == 0) { return hookBot(k / 2); } return hookTop((k / 2 + 1) % 8); }

struct Inside { col: vec3f, hit: f32 }

// what a ray sees between t0 and t1 inside the globe: strands, wires, stem, and the glow round them
fn inside(o: vec3f, dir: vec3f, t0: f32, t1: f32, E: f32, Lc: vec3f, lum: f32) -> Inside {
  var tHit = t1;
  var kind = 0;
  var glow = 0.0;
  var near = 1e5;
  for (var k = 0; k < 16; k++) {
    let h = raySeg(o, dir, strandA(k), strandB(k));
    if (h.y > t0 && h.y < t1) {
      glow += exp(-h.x / 0.035);
      if (h.x < R_FIL && h.y < tHit) { tHit = h.y; kind = 1; near = h.x; }
    }
  }
  for (var i = 0; i < 8; i++) {
    let a = f32(i) * TAU / 8.0;
    let b = (f32(i) + 0.5) * TAU / 8.0;
    let w1 = raySeg(o, dir, vec3f(0.05 * cos(a), Y_TOP - 0.03, 0.05 * sin(a)), hookTop(i));
    if (w1.x < R_WIRE && w1.y > t0 && w1.y < tHit) { tHit = w1.y; kind = 2; }
    let w2 = raySeg(o, dir, vec3f(0.05 * cos(b), Y_BOT + 0.02, 0.05 * sin(b)), hookBot(i));
    if (w2.x < R_WIRE && w2.y > t0 && w2.y < tHit) { tHit = w2.y; kind = 2; }
  }
  var col = vec3f(0.0);
  if (kind == 1) {
    // the strand's core: white-hot in its middle when hot
    let core = 1.0 - near / R_FIL;
    col = mix(Lc, vec3f(1.0, 0.96, 0.9), 0.45 * F.glow.y * core) * E * (0.55 + 0.45 * core);
  } else if (kind == 2) {
    let pw = o + dir * tHit;
    let hot = exp(-abs(pw.y - (Y_TOP + Y_BOT) * 0.5) * 1.5);
    col = lin(vec3f(0.22, 0.2, 0.19)) * 0.25 + Lc * (0.4 * lum + 0.05 * E) * hot;
  }
  // the glass stem in the middle, from the neck to the bottom ring: bright along its edges
  let st = raySeg(o, dir, vec3f(0.0, 1.32, 0.0), vec3f(0.0, Y_BOT + 0.04, 0.0));
  if (st.x < 0.045 && st.y > t0 && st.y < tHit) {
    let c = sqrt(max(1.0 - sq(st.x / 0.045), 0.0));
    let fr = 0.04 + 0.96 * pow(1.0 - c, 5.0);
    col = col * (1.0 - 0.3 * fr) + (Lc * (0.06 * E + 0.6 * lum) + vec3f(0.2)) * fr;
  }
  col += Lc * E * glow * 0.012;
  var r: Inside;
  r.col = col;
  r.hit = select(0.0, 1.0, kind != 0);
  return r;
}

// the filament mirrored in the inner face of the glass
fn mirrored(o: vec3f, dir: vec3f, E: f32, Lc: vec3f) -> vec3f {
  var md = 1e5;
  for (var k = 0; k < 16; k++) {
    let h = raySeg(o, dir, strandA(k), strandB(k));
    if (h.y > 0.01) { md = min(md, h.x); }
  }
  return Lc * E * (0.7 * (1.0 - smoothstep(R_FIL * 0.6, R_FIL * 2.0, md)) + 0.05 * exp(-md / 0.05));
}

struct Hit { col: vec3f, a: f32 }

fn bulb(p: vec2f) -> Hit {
  var out: Hit;
  out.col = vec3f(0.0);
  out.a = 0.0;
  let R = F.anchor.z;
  let zl = F.anchor.w;
  let tilt = F.bulb.z;
  let ct = cos(tilt); let st = sin(tilt);
  let axis2 = vec2f(st, ct);
  let side2 = vec2f(ct, -st);
  let lp = p - F.bulb.xy;
  let lx = dot(lp, side2) / R;
  let ly = dot(lp, axis2) / R;
  if (abs(lx) > 1.12 || ly < -0.05 || ly > 3.82) { return out; }

  // world: x right, y down, z towards the viewer; the bulb hangs zl in front of the page
  let cb = cos(F.bulb.w); let sb = sin(F.bulb.w);
  let axis = vec3f(axis2, 0.0);
  let side = vec3f(side2, 0.0) * cb + vec3f(0.0, 0.0, 1.0) * sb;
  let fwd = vec3f(side2, 0.0) * -sb + vec3f(0.0, 0.0, 1.0) * cb;
  let H = vec3f(F.bulb.xy, zl);
  let centre = H + axis * Y_GLOBE * R;
  let D = 14.0 * R;
  let row = vec3f(centre.xy, centre.z + D);
  let rdw = normalize(vec3f(p - centre.xy, -D));
  let toL = row - H;
  let o = vec3f(dot(toL, side), dot(toL, axis), dot(toL, fwd)) / R;
  let dir = normalize(vec3f(dot(rdw, side), dot(rdw, axis), dot(rdw, fwd)));
  // the bounding cylinder round the axis
  let qa = dir.x * dir.x + dir.z * dir.z;
  let qb = 2.0 * (o.x * dir.x + o.z * dir.z);
  let qc = o.x * o.x + o.z * o.z - 1.1 * 1.1;
  let disc = qb * qb - 4.0 * qa * qc;
  if (disc < 0.0) { return out; }
  let sd = sqrt(disc);
  var t = max((-qb - sd) / (2.0 * qa), 0.0);
  let tEnd = (-qb + sd) / (2.0 * qa);
  var mat = -1.0;
  var md = 0.0;
  for (var i = 0; i < 72; i++) {
    let q = o + dir * t;
    let m = metalSdf(q);
    let g = glassSdf(q);
    let dist = min(m.x, g);
    if (dist < 0.0012) { if (m.x < g) { mat = m.y; } else { mat = 2.0; } md = dist; break; }
    t += max(dist * 0.8, 0.0015);
    if (t > tEnd) { break; }
  }
  if (mat < 0.0) { return out; }

  let toWorld = mat3x3f(side, axis, fwd);
  let q = o + dir * t;
  let qw = H + toWorld * (q * R);
  let Lc = F.light.xyz;
  let lum = F.glow.z;
  let E = F.glow.w;
  let fil = vec3f(0.0, 2.55, 0.0);
  out.a = 1.0;
  if (mat < 1.5) {
    // brass: the cap bright and new, the socket aged; lit by the filament below through the neck
    let n = metalNormal(q);
    let nw = toWorld * n;
    let v = -rdw;
    let F0 = select(lin(vec3f(0.62, 0.46, 0.3)), lin(vec3f(0.98, 0.8, 0.5)), mat > 0.5);
    let fr = F0 + (vec3f(1.0) - F0) * pow(1.0 - max(dot(nw, v), 0.0), 5.0);
    let rw = reflect(rdw, nw);
    var c = env(qw, rw, 1.0) * fr;
    let toF = fil - q;
    let lf = normalize(toF);
    let att = 1.0 / (1.0 + sq(length(toF) * 0.9));
    let diff = max(dot(n, lf), 0.0);
    let spec = pow(max(dot(reflect(-lf, n), normalize(vec3f(dot(v, side), dot(v, axis), dot(v, fwd)))), 0.0), 30.0);
    c += F0 * Lc * (0.03 * E + 0.6 * lum) * diff * att + Lc * (0.04 * E + 0.6 * lum) * spec * att;
    // the threads' grooves stay dark
    c *= 0.72 + 0.28 * smoothstep(0.0, 0.02, metalSdf(q + n * 0.02).x);
    out.col = c;
    return out;
  }

  // glass: reflect the room, see in, see the far side
  let n1 = glassNormal(q);
  let n1w = toWorld * n1;
  let cos1 = abs(dot(n1w, rdw));
  let F1 = 0.04 + 0.96 * pow(1.0 - cos1, 5.0);
  let refl = env(qw, reflect(rdw, n1w), 3.0);
  // through the shell into the bulb, then out through its far wall
  var t2 = t + GLASS * 2.0;
  for (var i = 0; i < 40; i++) {
    let d2 = -(glassSdf(o + dir * t2) + GLASS);
    if (d2 < 0.001) { break; }
    t2 += max(d2 * 0.9, 0.002);
    if (t2 > tEnd + 1.0) { break; }
  }
  let q2 = o + dir * t2;
  let inn = inside(o, dir, t, t2, E, Lc, lum);
  let tint = vec3f(0.985, 0.965, 0.93);
  var c = inn.col;
  if (inn.hit < 0.5) {
    let n2 = glassNormal(q2);
    let F2 = 0.04 + 0.96 * pow(1.0 - abs(dot(n2, dir)), 5.0);
    let back = mirrored(q2, reflect(dir, n2), E, Lc);
    let q2w = H + toWorld * (q2 * R);
    let S = q2w.xy + rdw.xy * (q2w.z / -rdw.z);
    var behind = pageLit(S) * tint;
    // looking up the neck: the dark cement under the cap
    behind = mix(behind, lin(vec3f(0.12, 0.09, 0.07)) * (0.2 + Lc * (0.5 * lum)), smoothstep(Y_NECK + 0.35, Y_NECK + 0.05, q2.y));
    c += F2 * back + (1.0 - F2) * behind;
  }
  let shell = Lc * lum * (0.012 + 0.16 * pow(1.0 - cos1, 3.0)) + Lc * E * 0.0015;
  out.col = F1 * refl + (1.0 - F1) * tint * c + shell;
  // the cap's rim where it meets the glass catches the light
  return out;
}

/* ------------------------------------------------------------------ passes */

struct VOut { @builtin(position) pos: vec4f, @location(0) uv: vec2f }

@vertex fn fullVs(@builtin(vertex_index) vi: u32) -> VOut {
  let uv = vec2f(f32((vi << 1u) & 2u), f32(vi & 2u));
  var o: VOut;
  o.pos = vec4f(uv * vec2f(2.0, -2.0) + vec2f(-1.0, 1.0), 0.0, 1.0);
  o.uv = uv;
  return o;
}

@fragment fn sceneFs(v: VOut) -> @location(0) vec4f {
  let p = v.pos.xy / F.view.z;
  var c = pageLit(p);
  let R = F.anchor.z;
  let lum = F.glow.z;
  let Lc = F.light.xyz;
  // the cord
  let cd = cordDist(p);
  let px = 1.0 / F.view.z;
  c = mix(c, lin(vec3f(0.36, 0.345, 0.38)) + Lc * lum * 0.04, (1.0 - smoothstep(0.62 - px, 0.62 + px, cd)) * 0.92);
  let b = bulb(p);
  c = mix(c, b.col, b.a);
  // the air round the bulb glows
  let h = length(p - filamentAt());
  c += Lc * lum * 0.014 * R / (h + 0.32 * R) * exp(-h / (4.0 * R));
  return vec4f(c, 1.0);
}

@compute @workgroup_size(64) fn dustCs(@builtin(global_invocation_id) gid: vec3u) {
  let i = gid.x;
  if (i >= arrayLength(&motes)) { return; }
  let m = motes[i];
  let t = F.view.w;
  let R = F.anchor.z;
  let zl = F.anchor.w;
  var p = m.home.xyz;
  // a slow wander, whole cycles per loop
  let n1 = 1.0 + floor(m.seed.z * 2.99);
  let n2 = 1.0 + floor(m.seed.w * 2.99);
  p.x += 12.0 * sin(TAU * (n1 * t / 10.0 + m.seed.x)) + 5.0 * sin(TAU * (2.0 * t / 10.0 + m.seed.y));
  p.y += 9.0 * sin(TAU * (n2 * t / 10.0 + m.seed.y)) - 4.0 * cos(TAU * (t / 10.0 + m.seed.z));
  p.z += 0.35 * R * sin(TAU * (t / 10.0 + m.seed.w));
  // the air the bulb pushed: its velocity over the last frames, nearer and newer counting more,
  // with a curl round its path
  var off = vec2f(0.0);
  for (var k = 0; k < ${HISTORY - 1}; k++) {
    let c0 = hist[k].xy;
    let vel = (c0 - hist[k + 1].xy) * 60.0;
    let rel = p.xy - c0;
    let g = exp(-dot(rel, rel) / sq(1.7 * R)) * exp(-sq((p.z - zl) / (2.4 * R)));
    let w = exp(-f32(k) / 16.0) / 60.0;
    let curl = sign(rel.x * vel.y - rel.y * vel.x);
    off += (vel + vec2f(-vel.y, vel.x) * curl * 0.5) * w * g;
  }
  p = vec3f(p.xy + off * 1.15, p.z);
  // the light it scatters: forward-scattered towards the eye (Henyey-Greenstein, g 0.55)
  let fil = vec3f(hist[0].xy, zl);
  let tp = p - fil;
  let dist = max(length(tp), 1.0);
  let ct = tp.z / dist;
  let gg = 0.55;
  let hg = (1.0 - gg * gg) / pow(1.0 + gg * gg - 2.0 * gg * ct, 1.5) * (1.0 - gg) * (1.0 - gg) / (1.0 + gg);
  var bright = F.glow.z * (0.18 + 1.6 * hg) / (1.0 + sq(dist / (2.3 * R)));
  // none inside the glass
  if (dist < 1.08 * R) { bright = 0.0; }
  let glint = pow(0.5 + 0.5 * sin(TAU * ((1.0 + floor(m.seed.x * 3.99)) * t / 10.0 + m.seed.w * 7.0)), 24.0);
  let coc = abs(p.z - zl) / R * 1.5;
  let size = m.home.w;
  let radius = size + coc;
  let energy = sq(size / radius);
  var s: Spark;
  s.at = vec4f(p.xy, radius, bright * (1.0 + 2.5 * glint) * energy * F.misc.w);
  s.tint = vec4f(F.light.xyz, coc);
  sparksOut[i] = s;
}

struct DOut { @builtin(position) pos: vec4f, @location(0) uv: vec2f, @location(1) col: vec3f, @location(2) soft: f32 }

@vertex fn dustVs(@builtin(vertex_index) vi: u32, @builtin(instance_index) ii: u32) -> DOut {
  let s = sparks[ii];
  var corner = vec2f(-1.0, -1.0);
  if (vi == 1u || vi == 4u) { corner = vec2f(1.0, -1.0); }
  if (vi == 2u || vi == 3u) { corner = vec2f(-1.0, 1.0); }
  if (vi == 5u) { corner = vec2f(1.0, 1.0); }
  let pad = 1.6;
  var r = s.at.z * pad;
  if (s.at.w < 0.0004) { r = 0.0; }
  let at = s.at.xy + corner * r;
  var o: DOut;
  o.pos = vec4f(at.x / F.view.x * 2.0 - 1.0, 1.0 - at.y / F.view.y * 2.0, 0.0, 1.0);
  o.uv = corner * pad;
  o.col = s.tint.xyz * s.at.w;
  o.soft = clamp(s.tint.w / 1.5, 0.0, 1.0);
  return o;
}

@fragment fn dustFs(v: DOut) -> @location(0) vec4f {
  let r = length(v.uv);
  // in focus a point, out of focus a bokeh disc with a slightly brighter rim
  let point = exp(-r * r * 2.2);
  let disc = (1.0 - smoothstep(0.82, 1.0, r)) * (0.75 + 0.25 * smoothstep(0.5, 0.95, r)) * 0.5;
  let shape = mix(point, disc, v.soft);
  return vec4f(v.col * shape, 0.0);
}

// bloom: down with a soft threshold first, then plain halving
fn down4(uv: vec2f) -> vec3f {
  let tx = 0.5 / vec2f(textureDimensions(srcTex));
  var c = vec3f(0.0);
  c += textureSampleLevel(srcTex, samp, uv + vec2f(-tx.x, -tx.y), 0.0).rgb;
  c += textureSampleLevel(srcTex, samp, uv + vec2f(tx.x, -tx.y), 0.0).rgb;
  c += textureSampleLevel(srcTex, samp, uv + vec2f(-tx.x, tx.y), 0.0).rgb;
  c += textureSampleLevel(srcTex, samp, uv + vec2f(tx.x, tx.y), 0.0).rgb;
  return c * 0.25;
}
@fragment fn downFirstFs(v: VOut) -> @location(0) vec4f {
  let c = down4(v.uv);
  let peak = max(c.r, max(c.g, c.b));
  let knee = sq(clamp(peak - 0.35, 0.0, 0.7)) / 1.4;
  let gain = max(peak - 0.7, knee) / max(peak, 1e-4);
  return vec4f(c * gain, 1.0);
}
@fragment fn downFs(v: VOut) -> @location(0) vec4f { return vec4f(down4(v.uv), 1.0); }
@fragment fn upFs(v: VOut) -> @location(0) vec4f {
  let tx = 1.0 / vec2f(textureDimensions(lowTex));
  var low = vec3f(0.0);
  for (var y = -1; y <= 1; y++) {
    for (var x = -1; x <= 1; x++) {
      let w = f32((2 - abs(x)) * (2 - abs(y))) / 16.0;
      low += textureSampleLevel(lowTex, samp, v.uv + vec2f(f32(x), f32(y)) * tx, 0.0).rgb * w;
    }
  }
  return vec4f(textureSampleLevel(srcTex, samp, v.uv, 0.0).rgb + low, 1.0);
}

@fragment fn outFs(v: VOut) -> @location(0) vec4f {
  let tx = 1.0 / vec2f(textureDimensions(lowTex));
  var bloom = vec3f(0.0);
  for (var y = -1; y <= 1; y++) {
    for (var x = -1; x <= 1; x++) {
      let w = f32((2 - abs(x)) * (2 - abs(y))) / 16.0;
      bloom += textureSampleLevel(lowTex, samp, v.uv + vec2f(f32(x), f32(y)) * tx, 0.0).rgb * w;
    }
  }
  var c = textureSampleLevel(srcTex, samp, v.uv, 0.0).rgb + bloom * 0.11;
  // linear up to 0.75 (the page and the app keep the pin's colours), then a shoulder; the hottest
  // light turns white as a camera sees it
  let m = max(c.r, max(c.g, c.b));
  if (m > 0.75) {
    let m2 = 0.75 + 0.25 * (1.0 - exp(-(m - 0.75) / 0.25));
    c = c * (m2 / m);
    c = mix(c, vec3f(m2), clamp((m - 1.0) / 6.0, 0.0, 1.0) * 0.8);
  }
  c = max(c, vec3f(0.0));
  if (F.accent.w > 0.5) {
    c = vec3f(dot(c, vec3f(0.8225, 0.1774, 0.0)), dot(c, vec3f(0.0332, 0.9669, 0.0)), dot(c, vec3f(0.0171, 0.0724, 0.9108)));
  }
  var o = pow(c, vec3f(1.0 / 2.2));
  o += (fract(sin(dot(v.pos.xy, vec2f(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
  return vec4f(o, 1.0);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU, Mote, Spark } });
}

/** the motes' homes: most in the air round the bulb, the rest anywhere between the card and the dial */
export function seedMotes(w: number, top: number, bottom: number, cx: number, cy: number, R: number, zl: number) {
  let s = 1234567;
  const rnd = () => {
    s = (s * 1103515245 + 12345) & 0x7fffffff;
    return s / 0x7fffffff;
  };
  const gauss = () => Math.sqrt(-2 * Math.log(Math.max(1e-6, rnd()))) * Math.cos(2 * Math.PI * rnd());
  const out = new Float32Array(MOTES * 8);
  for (let i = 0; i < MOTES; i++) {
    let x: number;
    let y: number;
    if (i % 10 < 7) {
      x = cx + gauss() * 2.4 * R;
      y = cy + gauss() * 2.2 * R;
    } else {
      x = rnd() * w;
      y = top + rnd() * (bottom - top);
    }
    const z = zl + R * (-2.2 + 7.5 * Math.pow(rnd(), 1.6));
    const size = 0.35 + 0.85 * Math.pow(rnd(), 3);
    out.set([x, y, z, size, rnd(), rnd(), rnd(), rnd()], i * 8);
  }
  return out;
}
