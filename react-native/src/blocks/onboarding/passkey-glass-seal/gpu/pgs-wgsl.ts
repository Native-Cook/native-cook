// Passkey Glass Seal — the print, the key and the scan line on WebGPU, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout (the frame block and the thread's points are `d.struct` schemas), and
// `tgpu.resolve` injects their WGSL into the template. A transparent canvas the size of the card:
//  1. one full-screen pass: the scan line's glow; the glass key, sphere-traced from an extruded 2D
//     outline (bow, shaft and teeth, with rounded edges), tilted, turned about its shaft and lit by a
//     soft studio; a warm bloom round the lit print;
//  2. the thread: 6,144 points, one quad each, on the print's ridges or on the key's outline or on a
//     swirl between the two, lit by the glow that runs out from the core under the finger.

import { d, tgpu } from 'typegpu';

import { N } from '../pgs-print';

export const FrameU = d.struct({
  view: d.vec4f, // card width, height (pt), pixel ratio, time (s)
  print: d.vec4f, // the print's centre x, y (pt, in the card), its scale, the scan line's y (pt)
  glow: d.vec4f, // light 0…1, the scan line's weight, -, -
  key: d.vec4f, // unwind 0…1, glass 0…1, turn 0…1, -
});

export const Point = d.struct({
  ab: d.vec4f, // on the print (x, y), on the key (x, y), pt from the print's centre
  m: d.vec4f, // ridge 0…1, distance from the core 0…1, seed, -
});

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;
@group(0) @binding(1) var<storage, read> pts: array<Point>;

const PI = 3.14159265;
fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn ease(x: f32) -> f32 { let c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }
fn rot(a: f32) -> mat2x2f { let c = cos(a); let s = sin(a); return mat2x2f(c, s, -s, c); }

/* ------------------------------------------------------------------ the key (pt, y down, from the print's centre) */

fn sdBox(p: vec2f, b: vec2f) -> f32 { let q = abs(p) - b; return length(max(q, vec2f(0.0))) + min(max(q.x, q.y), 0.0); }
fn sdKey2(p: vec2f) -> f32 {
  let ring = abs(length(p - vec2f(-50.0, 0.0)) - 17.5) - 6.5;
  var dd = min(ring, sdBox(p - vec2f(20.0, 0.0), vec2f(46.0, 6.5)));
  dd = min(dd, sdBox(p - vec2f(58.0, 11.0), vec2f(4.0, 5.0)));
  dd = min(dd, sdBox(p - vec2f(44.0, 9.5), vec2f(4.0, 3.5)));
  dd = min(dd, sdBox(p - vec2f(30.5, 12.0), vec2f(3.5, 6.0)));
  return dd;
}
fn sdKey(p: vec3f) -> f32 {
  let d2 = sdKey2(p.xy) + 1.6;
  let w = vec2f(d2, abs(p.z) - 2.4);
  return min(max(w.x, w.y), 0.0) + length(max(w, vec2f(0.0))) - 1.6;
}
// the key in the card's frame: tilted for depth, turned about its shaft (its x axis) by the lock
fn keyLocal(p: vec3f) -> vec3f {
  let turn = F.key.z * PI * 0.3;
  var q = p;
  let xz = rot(0.32) * q.xz;
  q = vec3f(xz.x, q.y, xz.y);
  let yz = rot(-turn) * q.yz;
  q = vec3f(q.x, yz.x, yz.y);
  return q;
}
fn keyD(p: vec3f) -> f32 { return sdKey(keyLocal(p)); }
fn keyN(p: vec3f) -> vec3f {
  let e = 0.08;
  let k1 = vec3f(1.0, -1.0, -1.0); let k2 = vec3f(-1.0, -1.0, 1.0); let k3 = vec3f(-1.0, 1.0, -1.0); let k4 = vec3f(1.0, 1.0, 1.0);
  return normalize(k1 * keyD(p + k1 * e) + k2 * keyD(p + k2 * e) + k3 * keyD(p + k3 * e) + k4 * keyD(p + k4 * e));
}
fn studio(r: vec3f) -> vec3f {
  // a warm room: the page's rose below, a wide soft box overhead, a hot rim light from the right
  var c = mix(lin(vec3f(0.36, 0.2, 0.32)), lin(vec3f(0.95, 0.72, 0.8)), smoothstep(-0.6, 0.7, -r.y));
  c += vec3f(1.0, 0.95, 0.9) * 2.6 * smoothstep(0.55, 0.8, -r.y) * smoothstep(-0.9, -0.2, r.z) * (1.0 - smoothstep(0.5, 0.9, abs(r.x)));
  c += vec3f(1.0, 0.62, 0.42) * 2.0 * smoothstep(0.78, 0.95, dot(r, normalize(vec3f(0.85, 0.1, 0.5))));
  return c;
}

struct Px { col: vec3f, a: f32 }

fn keyPx(s: vec2f) -> Px {
  var o: Px;
  o.col = vec3f(0.0);
  o.a = 0.0;
  let g = F.key.y;
  if (g <= 0.0) { return o; }
  let sc = F.print.z;
  let p2 = (s - F.print.xy) / sc;
  if (abs(p2.x) > 85.0 || abs(p2.y) > 40.0) { return o; }
  let ro = vec3f(p2, 30.0);
  let rd = vec3f(0.0, 0.0, -1.0);
  var t = 0.0;
  var hit = false;
  for (var i = 0; i < 64; i++) {
    let dd = keyD(ro + rd * t);
    if (dd < 0.02) { hit = true; break; }
    t += dd * 0.85;
    if (t > 60.0) { break; }
  }
  if (!hit) { return o; }
  let p = ro + rd * t;
  let n = keyN(p);
  let v = vec3f(n.x, -n.y, n.z); // y up for the room
  let cosv = clamp(n.z, 0.0, 1.0);
  let fr = 0.05 + 0.95 * pow(1.0 - cosv, 4.0);
  let refl = studio(reflect(vec3f(0.0, 0.0, -1.0), vec3f(v.x, v.y, v.z)));
  // rose-gold glass: the body tinted where it is thick, the room seen in its faces
  let body = lin(vec3f(1.0, 0.7, 0.6)) * (0.16 + 0.22 * cosv);
  var c = body * (1.0 - fr) + refl * fr * 1.3;
  // a streak of the soft box across its faces
  c += vec3f(1.0, 0.96, 0.92) * 1.4 * pow(max(dot(reflect(normalize(vec3f(0.3, -0.8, -0.5)), vec3f(v.x, v.y, v.z)), vec3f(0.0, 0.0, 1.0)), 0.0), 24.0);
  c += lin(vec3f(1.0, 0.85, 0.75)) * pow(1.0 - cosv, 2.0) * 0.5;
  o.col = c;
  o.a = g * (0.82 + 0.18 * fr);
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

@fragment fn cardFs(v: VOut) -> @location(0) vec4f {
  let s = v.pos.xy / F.view.z;
  var col = vec3f(0.0);
  var a = 0.0;
  // the scan line: a hot core, a soft orange glow, fading at its ends
  let sw = F.glow.y;
  if (sw > 0.0) {
    let dy = s.y - F.print.w;
    let ends = smoothstep(18.0, 40.0, s.x) * (1.0 - smoothstep(F.view.x - 40.0, F.view.x - 18.0, s.x));
    let core = exp(-sq(dy / 1.3)) * ends;
    let glow = exp(-sq(dy / 4.0)) * ends * 0.6;
    let c = lin(vec3f(1.0, 0.5, 0.3)) * glow + lin(vec3f(1.0, 0.66, 0.46)) * core * 1.2;
    let k = (glow + core) * sw;
    col += c * sw;
    a += clamp(k, 0.0, 1.0) * 0.9;
  }
  // a warm bloom round the lit print, and round the key
  let lit = F.glow.x * (1.0 - F.key.x * 0.6);
  if (lit > 0.0) {
    let q = (s - F.print.xy - vec2f(0.0, -2.0) * F.print.z) / (F.print.z * 70.0);
    let b = exp(-dot(q, q) * 1.6) * lit * 0.3;
    col += lin(vec3f(1.0, 0.6, 0.45)) * b;
    a += b * 0.6;
  }
  let k = keyPx(s);
  col = k.col * k.a + col * (1.0 - k.a);
  a = k.a + a * (1.0 - k.a);
  if (a <= 0.0001) { return vec4f(0.0); }
  return vec4f(encode(col / a) * a, a);
}

/* ------------------------------------------------------------------ the thread */

struct TOut { @builtin(position) pos: vec4f, @location(0) uv: vec2f, @location(1) col: vec3f, @location(2) a: f32 }

@vertex fn threadVs(@builtin(vertex_index) vi: u32, @builtin(instance_index) ii: u32) -> TOut {
  let p = pts[ii];
  let sc = F.print.z;
  // the outer ridges come off first; each swirls round the core on its way
  let stagger = (1.0 - p.m.x) * 0.45 + p.m.z * 0.03;
  let e = ease((F.key.x - stagger) / 0.47);
  let ab = mix(p.ab.xy, p.ab.zw, e);
  let swirl = rot((1.0 - e) * e * 4.0 * (0.9 + 0.5 * p.m.x)) * (ab - vec2f(0.0, -2.0)) + vec2f(0.0, -2.0);
  let at = F.print.xy + swirl * sc;
  // the glow runs out from the core along the ridges; a hot front, then a warm settled light
  let front = F.glow.x * 1.25;
  let dd = p.m.y;
  let lit = clamp((front - dd) / 0.18, 0.0, 1.0);
  let flash = exp(-sq((front - dd) / 0.06)) * step(0.001, F.glow.x) * (1.0 - step(1.2, front));
  var c = mix(vec3f(1.0), vec3f(1.0, 0.86, 0.72), lit) * (0.75 + 0.5 * lit) + vec3f(1.0, 0.55, 0.32) * flash * 1.6;
  // in flight, a warm thread; on the key, its bright edge, fading as the glass fills it
  c = mix(c, vec3f(1.0, 0.8, 0.66) * 1.3, (1.0 - e) * e * 4.0);
  var a = (0.62 + 0.38 * lit) * (1.0 - F.key.y);
  var r = (1.05 + 0.5 * flash + 0.25 * (1.0 - e) * e * 4.0) * sc;
  var corner = vec2f(-1.0, -1.0);
  if (vi == 1u || vi == 4u) { corner = vec2f(1.0, -1.0); }
  if (vi == 2u || vi == 3u) { corner = vec2f(-1.0, 1.0); }
  if (vi == 5u) { corner = vec2f(1.0, 1.0); }
  let q = at + corner * (r + 0.8);
  var o: TOut;
  o.pos = vec4f(q.x / F.view.x * 2.0 - 1.0, 1.0 - q.y / F.view.y * 2.0, 0.0, 1.0);
  o.uv = corner * (r + 0.8) / r;
  o.col = c;
  o.a = a;
  return o;
}

@fragment fn threadFs(v: TOut) -> @location(0) vec4f {
  let r = length(v.uv);
  let a = (1.0 - smoothstep(0.55, 1.05, r)) * v.a * 0.55;
  return vec4f(encode(lin(min(v.col, vec3f(1.6)))) * a, a);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU, Point } });
}
export { N };
