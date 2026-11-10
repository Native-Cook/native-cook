// Perfume Ink Bloom — the bottle and its mist on WebGPU, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout (the frame block and the mist's records are `d.struct` schemas), and
// `tgpu.resolve` injects their WGSL into the template. A transparent canvas over the white page:
//  1. compute: 6,144 droplets of mist. Each is a pure function of the time since the spray: shot out
//     of the atomiser in a cone, slowed by the air, carried by a swirl, growing as it spreads; then
//     drawn, staggered, onto its own point of the perfume's notes (spelled out in type, sampled on the
//     JS side), and lifted away again as the notes evaporate;
//  2. one full-screen pass: the bottle, seen leaning and a little from above. Its body is a glass
//     ellipsoid traced analytically, full of perfume cut by a surface that sloshes; the light through
//     it is the page's white, absorbed along the ray's path through the liquid (Beer–Lambert), with
//     the room in its faces. The atomiser's collar, cap and ring are sphere-traced chrome; a blue
//     glass disc tops the cap. A soft blue shadow on the page;
//  3. the mist and the notes, as soft dots.

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  view: d.vec4f, // width, height (pt), pixel ratio, time (s)
  bottle: d.vec4f, // the body's centre x, y (pt), scale, the lean (rad, clockwise, added to the rest lean)
  liquid: d.vec4f, // slosh (rad), press 0…1, the rest lean (rad), -
  mist: d.vec4f, // age (s since the spray, −1 none), condense 0…1, evaporate 0…1, -
});

export const DROPS = 6144;
export const Drop = d.struct({
  at: d.vec4f, // x, y (pt), radius (pt), alpha
  tint: d.vec4f, // 0 mist … 1 a note, -, -, -
});

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;
@group(0) @binding(1) var<storage, read_write> dropsOut: array<Drop>;
@group(0) @binding(2) var<storage, read> drops: array<Drop>;
@group(0) @binding(3) var<storage, read> notes: array<vec2f>;

const PI = 3.14159265;
const TAU = 6.2831853;
fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn hash1(n: f32) -> f32 { return fract(sin(n * 91.3458) * 47453.5453); }
fn ease(x: f32) -> f32 { let c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }
fn rot(a: f32) -> mat2x2f { let c = cos(a); let s = sin(a); return mat2x2f(c, s, -s, c); }

/* ------------------------------------------------------------------ the bottle's frame
 world: pt, origin at the body's centre, x right, y up, z towards the eye. local: the bottle's own,
 y along its axis; the bottle leans in the screen's plane and is seen a little from above. */

const PITCH = 0.28;
fn lean() -> f32 { return F.liquid.z + F.bottle.w; }
fn toLocal(v: vec3f) -> vec3f {
  let xy = rot(lean()) * v.xy;
  var q = vec3f(xy, v.z);
  let yz = rot(-PITCH) * q.yz;
  return vec3f(q.x, yz.x, yz.y);
}
fn toWorld(v: vec3f) -> vec3f {
  let yz = rot(PITCH) * v.yz;
  var q = vec3f(v.x, yz.x, yz.y);
  let xy = rot(-lean()) * q.xy;
  return vec3f(xy, q.z);
}

/* ------------------------------------------------------------------ the room (world directions) */

fn studio(r: vec3f) -> vec3f {
  // a white studio: a big soft box overhead, a tall strip to each side with a dark gap, a grey floor
  var c = mix(lin(vec3f(0.78, 0.8, 0.86)), lin(vec3f(0.98, 0.99, 1.0)), smoothstep(-0.6, 0.5, r.y));
  c += vec3f(1.0) * 2.4 * smoothstep(0.55, 0.85, r.y) * (1.0 - smoothstep(0.4, 0.8, abs(r.x)));
  let side = abs(r.x);
  c += vec3f(1.0) * 1.8 * smoothstep(0.62, 0.72, side) * (1.0 - smoothstep(0.9, 0.97, side)) * smoothstep(-0.3, 0.2, r.z);
  c *= 1.0 - 0.8 * exp(-sq((r.x - 0.16) / 0.05)) * smoothstep(-0.2, 0.3, r.z);
  c *= 1.0 - 0.45 * exp(-sq((r.y + 0.35) / 0.12));
  return c;
}

/* ------------------------------------------------------------------ the body: analytic ellipsoids */

const BODY = vec3f(100.0, 90.0, 90.0);
const WALL = 5.0;
// t of the nearest and farthest hits of a ray with an ellipsoid of radii e (local), or (-1, -1)
fn ellipsoid(ro: vec3f, rd: vec3f, e: vec3f) -> vec2f {
  let o = ro / e; let dd = rd / e;
  let a = dot(dd, dd); let b = dot(o, dd); let c = dot(o, o) - 1.0;
  let h = b * b - a * c;
  if (h < 0.0) { return vec2f(-1.0); }
  let s = sqrt(h);
  return vec2f((-b - s) / a, (-b + s) / a);
}
fn ellNormal(p: vec3f, e: vec3f) -> vec3f { return normalize(p / (e * e)); }

/* ------------------------------------------------------------------ the atomiser: sphere-traced chrome (local) */

fn sdRoundBox(p: vec3f, b: vec3f, r: f32) -> f32 { let q = abs(p) - b + r; return length(max(q, vec3f(0.0))) + min(max(q.x, max(q.y, q.z)), 0.0) - r; }
fn sdHex(p: vec3f, r: f32, h: f32) -> f32 {
  // a hexagonal prism along y
  let k = vec3f(-0.8660254, 0.5, 0.57735);
  var q = abs(p.xz);
  q -= 2.0 * min(dot(k.xy, q), 0.0) * k.xy;
  let dd = vec2f(length(q - vec2f(clamp(q.x, -k.z * r, k.z * r), r)) * sign(q.y - r), abs(p.y) - h);
  return min(max(dd.x, dd.y), 0.0) + length(max(dd, vec2f(0.0)));
}
fn sdTorus(p: vec3f, R: f32, r: f32) -> f32 { let q = vec2f(length(p.xz) - R, p.y); return length(q) - r; }
// the ring's centre and axis in the bottle's frame: at rest it hangs right of the cap, facing the eye
const RING_C = vec3f(80.0, 118.0, 0.0);
const RING_N = vec3f(0.68, 0.27, 0.68);
fn ringP(p: vec3f) -> vec3f {
  // a frame whose y is the ring's axis
  let n = normalize(RING_N);
  let u = normalize(cross(n, vec3f(0.0, 1.0, 0.0)));
  let w = cross(u, n);
  let q = p - RING_C;
  return vec3f(dot(q, u), dot(q, n), dot(q, w));
}
// 1 chrome collar and cap, 2 ring, 3 the blue disc on top
fn sdAtom(p0: vec3f) -> vec2f {
  let dip = F.liquid.y * 7.0;
  let p = p0;
  // the collar: a faceted crown of two hex prisms, the lower one wider
  var dd = sdHex(p - vec3f(0.0, 80.0, 0.0), 50.0, 10.0) - 4.0;
  dd = min(dd, sdHex(p - vec3f(0.0, 96.0, 0.0), 40.0, 8.0) - 2.5);
  // the cap, pushed down by a press
  // a square prism with softly rounded edges, turned on its axis so two faces show
  var cp = p - vec3f(0.0, 134.0 - dip, 0.0);
  let cxz = rot(0.62) * cp.xz;
  cp = vec3f(cxz.x, cp.y, cxz.y);
  dd = min(dd, sdRoundBox(cp, vec3f(35.0, 42.0, 35.0), 12.0));
  var m = 1.0;
  var dp = p - vec3f(0.0, 176.5 - dip, 0.0);
  let dxz = rot(0.62) * dp.xz;
  dp = vec3f(dxz.x, dp.y, dxz.y);
  let disc = sdRoundBox(dp, vec3f(25.0, 2.6, 25.0), 2.4);
  if (disc < dd) { dd = disc; m = 3.0; }
  let ring = sdTorus(ringP(p), 40.0, 2.8);
  if (ring < dd) { dd = ring; m = 2.0; }
  return vec2f(dd, m);
}
fn atomN(p: vec3f) -> vec3f {
  let e = 0.15;
  let k1 = vec3f(1.0, -1.0, -1.0); let k2 = vec3f(-1.0, -1.0, 1.0); let k3 = vec3f(-1.0, 1.0, -1.0); let k4 = vec3f(1.0, 1.0, 1.0);
  return normalize(k1 * sdAtom(p + k1 * e).x + k2 * sdAtom(p + k2 * e).x + k3 * sdAtom(p + k3 * e).x + k4 * sdAtom(p + k4 * e).x);
}

/* ------------------------------------------------------------------ the liquid */

// the surface: a plane across the body, level with the room's gravity, rocked by the slosh
fn surfaceN() -> vec3f {
  let up = toLocal(vec3f(0.0, 1.0, 0.0));
  let s = F.liquid.x;
  // rock it about the axis across the screen, so it tips left and right
  let side = toLocal(vec3f(1.0, 0.0, 0.0));
  return normalize(up * cos(s) + side * sin(s));
}
const LEVEL = 58.0;
// the length of a ray's path through the liquid inside the inner wall
fn liquidPath(ro: vec3f, rd: vec3f) -> f32 {
  let ti = ellipsoid(ro, rd, BODY - vec3f(WALL));
  if (ti.y <= 0.0) { return 0.0; }
  var a = max(ti.x, 0.0);
  var b = ti.y;
  let n = surfaceN();
  let dn = dot(rd, n);
  let h0 = dot(ro, n) - LEVEL;
  if (abs(dn) < 1e-4) {
    if (h0 > 0.0) { return 0.0; }
    return b - a;
  }
  let tp = -h0 / dn;
  if (dn > 0.0) { b = min(b, tp); } else { a = max(a, tp); }
  return max(b - a, 0.0);
}

struct Px { col: vec3f, a: f32 }

fn bottlePx(s: vec2f) -> Px {
  var o: Px;
  o.col = vec3f(0.0);
  o.a = 0.0;
  let sc = F.bottle.z;
  let w = vec3f((s.x - F.bottle.x) / sc, (F.bottle.y - s.y) / sc, 0.0);
  // the shadow on the page: soft, blue, under the body
  let sp = (w.xy - vec2f(14.0, -98.0)) / vec2f(118.0, 30.0);
  let sh = exp(-dot(sp, sp) * 1.6) * 0.42;
  o.col = lin(vec3f(0.6, 0.8, 0.95)) * sh;
  o.a = sh;
  let ro = toLocal(vec3f(w.xy, 400.0));
  let rd = toLocal(vec3f(0.0, 0.0, -1.0));
  // the body
  let tb = ellipsoid(ro, rd, BODY);
  var tBody = 1e9;
  if (tb.x > 0.0) { tBody = tb.x; }
  // the atomiser, traced only near it
  var tA = 1e9;
  var mA = 0.0;
  if (length(w.xy - toWorld(vec3f(0.0, 150.0, 0.0)).xy) < 150.0) {
    var t = 300.0;
    for (var i = 0; i < 80; i++) {
      let h = sdAtom(ro + rd * t);
      if (h.x < 0.05) { tA = t; mA = h.y; break; }
      t += h.x * 0.9;
      if (t > 520.0) { break; }
    }
  }
  if (tBody > 1e8 && tA > 1e8) { return o; }
  let V = vec3f(0.0, 0.0, 1.0);
  if (tA < tBody) {
    let p = ro + rd * tA;
    let nl = atomN(p);
    let n = toWorld(nl);
    let r = reflect(vec3f(0.0, 0.0, -1.0), n);
    var c = vec3f(0.0);
    if (mA < 2.5) {
      // chrome: the room in it, a little cool
      let fr = 0.8 + 0.2 * pow(1.0 - max(n.z, 0.0), 5.0);
      c = studio(r) * lin(vec3f(0.95, 0.96, 0.98)) * fr;
    } else {
      // the blue glass disc on the cap
      let fr = 0.04 + 0.96 * pow(1.0 - max(n.z, 0.0), 5.0);
      c = lin(vec3f(0.18, 0.72, 0.95)) * (1.0 - fr) + studio(r) * fr;
    }
    o.col = c;
    o.a = 1.0;
    return o;
  }
  let p = ro + rd * tBody;
  let nl = ellNormal(p, BODY);
  let n = toWorld(nl);
  let cosv = max(n.z, 0.0);
  let fr = 0.04 + 0.96 * pow(1.0 - cosv, 5.0);
  let r = reflect(vec3f(0.0, 0.0, -1.0), n);
  // into the glass and the liquid, bent a little
  let rdIn = refract(rd, nl, 1.0 / 1.36);
  let L = liquidPath(p + rdIn * 0.5, rdIn);
  let sigma = vec3f(1.6, 0.28, 0.03) / 100.0;
  let back = vec3f(1.0) * (0.98 + 0.08 * clamp(toWorld(rdIn).y + 0.5, 0.0, 1.0));
  var through = exp(-sigma * L);
  // the light leaving the back: the page, with the room's floor where the ray bends far

  // where the ray only crosses glass above the liquid, the glass greys the page faintly
  if (L <= 0.0) { through = vec3f(0.93, 0.95, 0.97); }
  var c = back * through * (1.0 - fr) + studio(toWorld(r)) * fr;
  // the wall at the rim: a bright edge
  c += vec3f(0.85, 0.95, 1.0) * pow(1.0 - cosv, 4.0) * 0.12;
  // the surface's meniscus, a bright thin line where it meets the wall
  let n2 = surfaceN();
  let hS = dot(p, n2) - LEVEL;
  c += vec3f(0.9, 0.98, 1.0) * exp(-sq(hS / 1.6)) * 0.25 * step(0.0, 1.0 - abs(hS) / 6.0);
  o.col = c;
  o.a = 1.0;
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

@fragment fn bottleFs(v: VOut) -> @location(0) vec4f {
  let px = bottlePx(v.pos.xy / F.view.z);
  if (px.a <= 0.0001) { return vec4f(0.0); }
  if (px.a < 0.999) { return vec4f(encode(px.col / px.a) * px.a, px.a); }
  return vec4f(encode(px.col), 1.0);
}

/* ------------------------------------------------------------------ the mist */

// where the atomiser sprays from, on the screen, and which way
fn nozzle() -> vec4f {
  let dip = F.liquid.y * 7.0;
  let local = vec3f(30.0, 164.0 - dip, 20.0);
  let wq = toWorld(local);
  let at = vec2f(F.bottle.x + wq.x * F.bottle.z, F.bottle.y - wq.y * F.bottle.z);
  let dir = toWorld(vec3f(0.55, 0.84, 0.0));
  return vec4f(at, normalize(vec2f(dir.x, -dir.y)));
}

@compute @workgroup_size(64) fn mistCs(@builtin(global_invocation_id) gid: vec3u) {
  let i = gid.x;
  if (i >= ${DROPS}u) { return; }
  let fi = f32(i);
  var o: Drop;
  let age = F.mist.x;
  let h1 = hash1(fi + 1.0); let h2 = hash1(fi + 2.0); let h3 = hash1(fi + 3.0); let h4 = hash1(fi + 4.0); let h5 = hash1(fi + 5.0);
  let tau = age - h1 * 0.42;
  if (age < 0.0 || tau < 0.0) {
    o.at = vec4f(0.0);
    o.tint = vec4f(0.0);
    dropsOut[i] = o;
    return;
  }
  let nz = nozzle();
  let sc = F.bottle.z;
  // a cone out of the nozzle, slowed by the air
  let a = (h2 - 0.5) * 0.7 + (h3 - 0.5) * 0.3;
  let dir = rot(a) * nz.zw;
  let speed = (130.0 + 150.0 * h3) * sc;
  let k = 2.6;
  var p = nz.xy + dir * speed * (1.0 - exp(-k * tau)) / k;
  // carried by a slow swirl and rising
  let sw = min(tau, 2.0);
  p += vec2f(sin(tau * (1.3 + h4) + h5 * TAU), cos(tau * (1.1 + h5) + h4 * TAU)) * 16.0 * sw * sc;
  p += vec2f(0.0, -10.0 * tau) * sc;
  var r = (1.2 + 3.2 * min(tau / 1.2, 1.0) * (0.5 + h4)) * sc;
  var alpha = smoothstep(0.0, 0.05, tau) * (1.0 - 0.55 * smoothstep(0.4, 2.0, tau));
  // drawn onto the notes, one droplet to a point, staggered
  let c = ease((F.mist.y - h1 * 0.35) / 0.65);
  let goal = notes[i];
  p = mix(p, goal, c);
  r = mix(r, 0.95 * sc, c);
  alpha = mix(alpha, 1.0, c);
  // and lifted away
  let e = ease((F.mist.z - h2 * 0.4) / 0.6);
  p += (vec2f(sin(h4 * TAU) * 50.0, -90.0 * (0.6 + h3)) * e + vec2f(sin(e * 3.0 + h5 * TAU), 0.0) * 12.0 * e) * sc;
  r += 1.6 * e * sc;
  alpha *= (1.0 - e) * (1.0 - e);
  o.at = vec4f(p, r, alpha);
  o.tint = vec4f(c * (1.0 - e), 0.0, 0.0, 0.0);
  dropsOut[i] = o;
}

struct DOut { @builtin(position) pos: vec4f, @location(0) uv: vec2f, @location(1) a: f32, @location(2) tint: f32 }

@vertex fn dropVs(@builtin(vertex_index) vi: u32, @builtin(instance_index) ii: u32) -> DOut {
  let p = drops[ii];
  var corner = vec2f(-1.0, -1.0);
  if (vi == 1u || vi == 4u) { corner = vec2f(1.0, -1.0); }
  if (vi == 2u || vi == 3u) { corner = vec2f(-1.0, 1.0); }
  if (vi == 5u) { corner = vec2f(1.0, 1.0); }
  var r = p.at.z + 0.6;
  if (p.at.w <= 0.002) { r = 0.0; }
  let q = p.at.xy + corner * r;
  var o: DOut;
  o.pos = vec4f(q.x / F.view.x * 2.0 - 1.0, 1.0 - q.y / F.view.y * 2.0, 0.0, 1.0);
  o.uv = corner * r / max(p.at.z, 0.1);
  o.a = p.at.w;
  o.tint = p.tint.x;
  return o;
}

@fragment fn dropFs(v: DOut) -> @location(0) vec4f {
  let r = length(v.uv);
  // mist: a soft cloud of pale blue; a note: crisp ink-blue dots
  let soft = exp(-r * r * 2.2) * (1.0 - smoothstep(0.9, 1.4, r));
  let crisp = 1.0 - smoothstep(0.7, 1.15, r);
  let k = mix(soft * 0.16, crisp * 0.85, v.tint) * v.a;
  let c = mix(vec3f(0.55, 0.76, 0.98), vec3f(0.11, 0.39, 0.95), v.tint);
  return vec4f(c * k, k);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU, Drop } });
}
