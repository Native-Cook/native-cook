// Bell Permission Primer — the bell on WebGPU, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout (the frame block and the sparks' records are `d.struct` schemas), and
// `tgpu.resolve` injects their WGSL into the template. A transparent canvas over the app, each frame:
//  1. compute: 260 sparks thrown out round the Dynamic Island as it swallows the bell, each a pure
//     function of the swallow (born on the island's rim, flying out and fading);
//  2. one full-screen pass: the pin's faint shapes floating round the page (bells, blobs and dots,
//     nudged by every ring), the rings themselves, and the bell and the Dynamic Island as one field,
//     sphere-traced: a porcelain bell with a gold lip and crown, hollow, a pink clapper on its rod;
//     the island a slab of black glass drawn exactly over the real one. Drawn up towards it the bell
//     stretches into a bridge of goo (a smooth minimum whose reach grows as they near), is swallowed
//     (shrinking into the island, its colour turning to the island's black), and the island opens
//     into the notification;
//  3. the sparks, as small four-point stars.

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  view: d.vec4f, // width, height (pt), pixel ratio, time (s)
  hero: d.vec4f, // pivot x, y (pt), unit (pt), sway (pt)
  pose: d.vec4f, // swing, clapper (rad), swallowed 0…1, opened 0…1
  lift: d.vec4f, // the bell's offset from its rest x, y (pt), -, -
  island: d.vec4f, // the Dynamic Island at rest: x, y, w, h (pt)
  slab: d.vec4f, // the notification it opens into: x, y, w, h (pt)
  rings: d.vec4f, // three rings' progress 0…1 (0 = none), -
  look: d.vec4f, // the view's yaw, pitch (rad), -, -
});

export const SPARKS = 260;
export const Spark = d.struct({
  at: d.vec4f, // x, y (pt), size (pt), alpha
});

const DECO = [
  [34, 91, 14, 1],
  [367, 88, 12, 0],
  [218, 148, 12, 0],
  [122, 110, 6, 2],
  [327, 150, 7, 2],
  [25, 170, 5, 2],
  [110, 232, 10, 1],
  [330, 258, 13, 1],
  [32, 293, 7, 2],
  [346, 344, 6, 2],
  [301, 511, 13, 1],
  [370, 583, 9, 1],
  [323, 724, 11, 1],
  [180, 635, 6, 2],
  [70, 740, 5, 2],
];

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;
@group(0) @binding(1) var<storage, read_write> sparksOut: array<Spark>;
@group(0) @binding(2) var<storage, read> sparks: array<Spark>;

const TAU = 6.2831853;
const DECO = array<vec4f, ${DECO.length}>(${DECO.map((v) => `vec4f(${v.map((n) => n.toFixed(1)).join(', ')})`).join(', ')});

fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn hash1(n: f32) -> f32 { return fract(sin(n * 91.3458) * 47453.5453); }
fn smin(a: f32, b: f32, k: f32) -> f32 { let h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }
fn smax2(a: f32, b: f32, k: f32) -> f32 { let h = clamp(0.5 - 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) + k * h * (1.0 - h); }
fn sdTorusXY(p: vec3f, R: f32, r: f32) -> f32 { let q = vec2f(length(p.xy) - R, p.z); return length(q) - r; }
fn sdTorusXZ(p: vec3f, R: f32, r: f32) -> f32 { let q = vec2f(length(p.xz) - R, p.y); return length(q) - r; }
fn sdRoundCone(p: vec3f, a: vec3f, b: vec3f, r1: f32, r2: f32) -> f32 {
  let ba = b - a; let l2 = dot(ba, ba); let rr = r1 - r2; let a2 = l2 - rr * rr; let il2 = 1.0 / l2;
  let pa = p - a; let y = dot(pa, ba); let z = y - l2;
  let xv = pa * l2 - ba * y; let x2 = dot(xv, xv);
  let y2 = y * y * l2; let z2 = z * z * l2;
  let k = sign(rr) * rr * rr * x2;
  if (sign(z) * a2 * z2 > k) { return sqrt(x2 + z2) * il2 - r2; }
  if (sign(y) * a2 * y2 < k) { return sqrt(x2 + y2) * il2 - r1; }
  return (sqrt(x2 * a2 * il2) + y * rr) * il2 - r1;
}
fn seg3(p: vec3f, a: vec3f, b: vec3f) -> f32 { let pa = p - a; let ba = b - a; return length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0)); }

/* ------------------------------------------------------------------ the bell, in its own frame (units, y up, pivot at 0) */

// x distance, y material: 0 porcelain, 1 gold, 2 the clapper, 3 the gold inside
fn sdBell(p: vec3f) -> vec2f {
  let outer = smin(length(p - vec3f(0.0, -0.55, 0.0)) - 0.34, sdRoundCone(p, vec3f(0.0, -0.58, 0.0), vec3f(0.0, -1.2, 0.0), 0.33, 0.6), 0.16);
  let inner = sdRoundCone(p, vec3f(0.0, -0.66, 0.0), vec3f(0.0, -1.34, 0.0), 0.27, 0.56);
  var body = smax2(outer, -inner, 0.02);
  body = max(body, -(p.y + 1.27));
  var d = vec2f(body, 0.0);
  if (abs(inner) < abs(outer) && p.y < -0.6) { d.y = 3.0; }
  let lip = sdTorusXZ(p - vec3f(0.0, -1.26, 0.0), 0.585, 0.045);
  if (lip < d.x) { d = vec2f(lip, 1.0); }
  let crown = sdTorusXY(p - vec3f(0.0, -0.11, 0.0), 0.1, 0.035);
  if (crown < d.x) { d = vec2f(crown, 1.0); }
  // the clapper swings on its own angle under the shoulder
  let rel = F.pose.y - F.pose.x;
  let cs = cos(rel); let sn = sin(rel);
  let tip = vec3f(0.0, -0.62, 0.0) + vec3f(sn * 0.7, -cs * 0.7, 0.0);
  let rod = seg3(p, vec3f(0.0, -0.62, 0.0), tip) - 0.018;
  let ball = length(p - tip) - 0.11;
  let cl = smin(rod, ball, 0.03);
  if (cl < d.x) { d = vec2f(cl, 2.0); }
  return d;
}

// the island (or the notification it opens into): black glass, a rounded slab in world units
fn islandRect() -> vec4f {
  let e = F.pose.w;
  return mix(F.island, F.slab, e);
}
fn sdIsland(pw: vec2f, z: f32) -> f32 {
  let U = F.hero.z;
  let r = islandRect();
  let c = (r.xy + r.zw * 0.5) / U;
  let half = r.zw * 0.5 / U;
  let rad = min(half.y, mix(half.y, 34.0 / U, F.pose.w));
  let q2 = abs(pw - c) - half + vec2f(rad);
  let d2 = length(max(q2, vec2f(0.0))) + min(max(q2.x, q2.y), 0.0) - rad;
  let w = vec2f(d2, abs(z) - 0.05);
  return min(max(w.x, w.y), 0.0) + length(max(w, vec2f(0.0)));
}

// where the bell's middle is on the screen (pt): under its pivot, drawn up by the finger, then pulled
// into the island
fn bellCentre() -> vec2f {
  let U = F.hero.z;
  let rest = F.hero.xy + vec2f(0.0, 0.85 * U + F.hero.w) + F.lift.xy;
  let isl = F.island.xy + F.island.zw * 0.5;
  return mix(rest, isl, F.pose.z);
}
fn bellScale() -> f32 { return mix(1.0, 0.03, F.pose.z); }

struct Obj { d: f32, m: f32, isl: f32 }

// the bell and the island as one field, in world units (y down, x right: the screen's axes / U)
fn sdObj(pw: vec3f) -> Obj {
  let U = F.hero.z;
  let bc = bellCentre() / U;
  let sc = bellScale();
  // the bell's own frame: y up from its middle, the swing about the pivot 0.85 above it
  var q = vec3f(pw.x - bc.x, -(pw.y - bc.y), pw.z) / sc;
  // seen a little from the side and above (squarely once swallowed)
  let ya = F.look.x * (1.0 - F.pose.z); let pa = F.look.y * (1.0 - F.pose.z);
  let cy = cos(ya); let sy = sin(ya); let cp = cos(pa); let sp = sin(pa);
  q = transpose(mat3x3f(vec3f(cy, sp * sy, -cp * sy), vec3f(0.0, cp, sp), vec3f(sy, -sp * cy, cp * cy))) * q;
  q += vec3f(0.0, -0.85, 0.0);
  let sw = F.pose.x * (1.0 - F.pose.z);
  let cs = cos(sw); let sn = sin(sw);
  q = vec3f(cs * q.x + sn * q.y, -sn * q.x + cs * q.y, q.z);
  let b = sdBell(q);
  let db = b.x * sc;
  let di = sdIsland(pw.xy, pw.z);
  // the nearer they come, the further the goo reaches between them
  // (how far the bell's crown is from the island's underside, in units)
  let crown = (bellCentre().y - 0.95 * U * sc) / U;
  let gap = max(crown - (F.island.y + F.island.w) / U, 0.0);
  let k = (0.55 * smoothstep(1.6, 0.15, gap) + 0.4 * F.pose.z) * (1.0 - F.pose.w);
  var o: Obj;
  if (k < 0.002) {
    o.d = min(db, di);
    o.isl = select(0.0, 1.0, di < db);
  } else {
    let h = clamp(0.5 + 0.5 * (di - db) / k, 0.0, 1.0);
    o.d = mix(di, db, h) - k * h * (1.0 - h);
    o.isl = 1.0 - h;
  }
  o.m = b.y;
  return o;
}

fn normalAt(p: vec3f) -> vec3f {
  let e = 0.0015;
  let k1 = vec3f(1.0, -1.0, -1.0); let k2 = vec3f(-1.0, -1.0, 1.0); let k3 = vec3f(-1.0, 1.0, -1.0); let k4 = vec3f(1.0, 1.0, 1.0);
  return normalize(k1 * sdObj(p + k1 * e).d + k2 * sdObj(p + k2 * e).d + k3 * sdObj(p + k3 * e).d + k4 * sdObj(p + k4 * e).d);
}

/* ------------------------------------------------------------------ the world */

fn studio(r: vec3f) -> vec3f {
  var c = mix(lin(vec3f(0.42, 0.62, 0.98)), lin(vec3f(0.97, 0.98, 1.0)), smoothstep(-0.5, 0.7, r.y));
  let k = normalize(vec3f(-0.5, 0.78, 0.4));
  c += vec3f(1.0, 0.99, 0.97) * 4.0 * smoothstep(0.88, 0.97, dot(r, k));
  let f = normalize(vec3f(0.85, 0.2, 0.5));
  c += vec3f(0.9, 0.95, 1.0) * 1.0 * smoothstep(0.86, 0.98, dot(r, f));
  // the blue disc below
  c = mix(c, lin(vec3f(0.25, 0.42, 0.98)), smoothstep(0.0, -0.7, r.y) * 0.7);
  return c;
}

struct Px { col: vec3f, a: f32 }

fn over(base: Px, c: vec3f, a: f32) -> Px {
  var o: Px;
  o.col = c * a + base.col * (1.0 - a);
  o.a = a + base.a * (1.0 - a);
  return o;
}

// a little bell glyph for the faint shapes
fn sdBellGlyph(p: vec2f) -> f32 {
  let body = max(length(p * vec2f(1.0, 0.85) - vec2f(0.0, -0.05)) - 0.55, p.y - 0.42);
  let lip = length(max(abs(p - vec2f(0.0, 0.45)) - vec2f(0.62, 0.06), vec2f(0.0))) - 0.04;
  let knob = length(p - vec2f(0.0, -0.66)) - 0.1;
  return min(min(body, lip), knob);
}

fn shade(s: vec2f) -> Px {
  var o: Px;
  o.col = vec3f(0.0);
  o.a = 0.0;
  let t = F.view.w;
  let lipW = F.hero.xy + vec2f(0.0, 1.27 * F.hero.z);
  // the faint shapes, floating, pushed out by each ring as it passes
  for (var i = 0; i < ${DECO.length}; i++) {
    let dd = DECO[i];
    let fi = f32(i);
    var c = dd.xy + vec2f(sin(TAU * (t / 10.0 + hash1(fi))) * 3.0, sin(TAU * (2.0 * t / 10.0 + hash1(fi + 3.0))) * 4.0);
    for (var k = 0; k < 3; k++) {
      let rp = F.rings[k];
      if (rp > 0.0) {
        let dv = c - lipW;
        let dist = length(dv);
        let front = rp * 420.0;
        c += normalize(dv + vec2f(0.001)) * 9.0 * exp(-sq((dist - front) / 26.0)) * (1.0 - rp);
      }
    }
    let q = (s - c) / dd.z;
    var g = 1e5;
    if (dd.w < 0.5) { g = sdBellGlyph(q); }
    else if (dd.w < 1.5) { g = length(q * vec2f(1.0, 1.15)) - 0.62 + 0.08 * sin(atan2(q.y, q.x) * 3.0 + fi); }
    else { g = length(q) - 0.5; }
    let a = (1.0 - smoothstep(-0.04, 0.04, g)) * 0.55;
    if (a > 0.0) { o = over(o, lin(vec3f(0.75, 0.86, 1.0)), a); }
  }
  // the rings: a soft bright line running out from the lip
  for (var k = 0; k < 3; k++) {
    let rp = F.rings[k];
    if (rp > 0.0) {
      let front = rp * 420.0;
      let dist = length(s - lipW);
      let a = exp(-sq((dist - front) / (3.0 + 10.0 * rp))) * (1.0 - rp) * 0.55;
      o = over(o, lin(vec3f(0.55, 0.75, 1.0)), a);
    }
  }
  // the bell and the island: a box round both, then a near-orthographic camera, the bell seen a
  // little from the side and above (the island squarely)
  let U = F.hero.z;
  let bc = bellCentre();
  let r = islandRect();
  let lo = min(bc - vec2f(1.0, 1.25) * U * bellScale(), r.xy) - vec2f(U * 0.6);
  let hi = max(bc + vec2f(1.0, 0.75) * U * bellScale(), r.xy + r.zw) + vec2f(U * 0.6);
  if (s.x < lo.x || s.y < lo.y || s.x > hi.x || s.y > hi.y) { return o; }
  // an orthographic camera in front of the screen (world: the screen's axes / U, z towards the eye)
  let ro = vec3f(s / U, 12.0);
  let rd = vec3f(0.0, 0.0, -1.0);
  var tt = 10.0;
  var hit = false;
  for (var i = 0; i < 96; i++) {
    let dd = sdObj(ro + rd * tt).d;
    if (dd < 0.0008) { hit = true; break; }
    tt += dd * 0.85;
    if (tt > 14.5) { break; }
  }
  // the bell's soft shadow on the page
  let shp = vec3f(s.x / U - 0.02, s.y / U - 0.12, 0.0);
  let shd = sdObj(shp);
  let sh = 0.18 * (1.0 - smoothstep(-0.05, 0.4, shd.d)) * (1.0 - shd.isl) * (1.0 - F.pose.z);
  if (!hit) {
    if (sh > 0.002) { o = over(o, lin(vec3f(0.36, 0.45, 0.62)), sh); }
    return o;
  }
  let p = ro + rd * tt;
  let ob = sdObj(p);
  let nw = normalAt(p);
  // into the view's frame for lighting (y up, z towards the eye)
  let nv = vec3f(nw.x, -nw.y, nw.z);
  let rdv = vec3f(0.0, 0.0, -1.0);
  let vv = vec3f(0.0, 0.0, 1.0);
  let L = normalize(vec3f(-0.5, 0.75, 0.55));
  let cosv = max(dot(nv, vv), 0.0);
  let refl = studio(reflect(rdv, nv));
  let spec = pow(max(dot(reflect(-L, nv), vv), 0.0), 60.0);
  let wrap = clamp((dot(nv, L) + 0.4) / 1.4, 0.0, 1.0);
  var col = vec3f(0.0);
  // the goo between them is porcelain turning black, never the gold of the crown
  var mm = ob.m;
  if (ob.isl > 0.04 && mm > 0.5) { mm = 0.0; }
  if (mm < 0.5) {
    col = lin(vec3f(0.97, 0.975, 0.99)) * (0.3 + 0.72 * wrap);
    col = mix(col, col * lin(vec3f(0.62, 0.74, 1.0)), smoothstep(0.2, -0.7, dot(nv, L)) * 0.85);
    col += refl * 0.08 + vec3f(1.0) * spec * 0.6;
  } else if (mm < 1.5 || mm > 2.5) {
    let gold = lin(vec3f(0.96, 0.76, 0.26));
    let inside = select(1.0, 0.55, mm > 2.5);
    col = gold * (0.25 + 0.45 * wrap) * inside + refl * gold * 0.85 + vec3f(1.0, 0.95, 0.8) * spec * 0.8;
  } else {
    col = lin(vec3f(0.95, 0.48, 0.63)) * (0.4 + 0.65 * wrap) + refl * 0.06 + vec3f(1.0) * spec * 0.7;
  }
  // the island is black glass, as the real one; only the goo between them catches the light
  let sheen = F.pose.z * (1.0 - F.pose.w) * (1.0 - F.pose.w);
  let black = (refl * 0.05 + vec3f(1.0) * spec * 0.25) * sheen;
  col = mix(col, black, clamp(ob.isl + F.pose.z * 0.6, 0.0, 1.0));
  o = over(o, col, 1.0);
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

@fragment fn heroFs(v: VOut) -> @location(0) vec4f {
  let px = shade(v.pos.xy / F.view.z);
  if (px.a <= 0.0) { return vec4f(0.0); }
  return vec4f(encode(px.col / px.a) * px.a, px.a);
}

/* ------------------------------------------------------------------ the sparks shed on the flight */

@compute @workgroup_size(64) fn sparksCs(@builtin(global_invocation_id) gid: vec3u) {
  let i = gid.x;
  if (i >= ${SPARKS}u) { return; }
  let fi = f32(i);
  // thrown out round the island's rim as it closes on the bell, then fading as it opens
  let m = F.pose.z * (1.0 - F.pose.w) + F.pose.w * step(F.pose.w, 0.999) * 1.0;
  let age = clamp((F.pose.z - 0.55 - 0.3 * hash1(fi + 9.0)) / 0.45, 0.0, 1.0) * 0.5 + F.pose.w * 0.5;
  var s: Spark;
  if (age <= 0.0 || age >= 1.0) {
    s.at = vec4f(0.0);
    sparksOut[i] = s;
    return;
  }
  let r = F.island;
  let c = r.xy + r.zw * 0.5;
  let ang = hash1(fi + 2.0) * TAU;
  let dir = vec2f(cos(ang), sin(ang) * 0.6);
  let rim = c + dir * vec2f(r.z * 0.5, r.w * 0.8);
  let p = rim + dir * (8.0 + 60.0 * hash1(fi + 3.0)) * sqrt(age) + vec2f(0.0, 30.0 * age * age);
  s.at = vec4f(p, (1.2 + 2.6 * hash1(fi + 4.0)) * (1.0 - 0.5 * age), (1.0 - age) * (1.0 - age) * step(0.001, m));
  sparksOut[i] = s;
}

struct SOut { @builtin(position) pos: vec4f, @location(0) uv: vec2f, @location(1) a: f32 }

@vertex fn sparkVs(@builtin(vertex_index) vi: u32, @builtin(instance_index) ii: u32) -> SOut {
  let s = sparks[ii];
  var corner = vec2f(-1.0, -1.0);
  if (vi == 1u || vi == 4u) { corner = vec2f(1.0, -1.0); }
  if (vi == 2u || vi == 3u) { corner = vec2f(-1.0, 1.0); }
  if (vi == 5u) { corner = vec2f(1.0, 1.0); }
  var r = s.at.z * 2.0;
  if (s.at.w <= 0.001) { r = 0.0; }
  let at = s.at.xy + corner * r;
  var o: SOut;
  o.pos = vec4f(at.x / F.view.x * 2.0 - 1.0, 1.0 - at.y / F.view.y * 2.0, 0.0, 1.0);
  o.uv = corner;
  o.a = s.at.w;
  return o;
}

@fragment fn sparkFs(v: SOut) -> @location(0) vec4f {
  let d = abs(v.uv);
  let star = max(exp(-d.x * 10.0) * exp(-d.y * 2.5), exp(-d.y * 10.0) * exp(-d.x * 2.5));
  let a = clamp(max(star, exp(-dot(d, d) * 9.0)) * v.a, 0.0, 1.0);
  let c = vec3f(0.55, 0.74, 1.0);
  return vec4f(c * a, a);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU, Spark } });
}
