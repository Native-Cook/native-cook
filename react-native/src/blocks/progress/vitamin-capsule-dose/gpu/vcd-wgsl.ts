// Vitamin Capsule Dose — the hero on WebGPU, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout (the frame block and the beads' records are `d.struct` schemas,
// written with typed `buffer.write()`), and `tgpu.resolve` injects their WGSL into the template.
//
// A transparent canvas over the app. Each frame:
//  1. compute: 900 beads. Each is born on the surface of the object as it stands (a point projected
//     onto the signed distance field), leaves when the dissolve passes over it, and flies a curved
//     path to the pill's dot in the list;
//  2. one full-screen pass draws the object, sphere-traced: a capsule (white porcelain half, blue
//     glass half with vitamin beads inside), a white chalk tablet with its score line, or a drop of
//     clear liquid; any two of them blended into each other while it morphs. Glass and liquid refract
//     the hero's disc behind them (absorbing with the path length), everything reflects a soft studio,
//     and a soft shadow falls on the disc. Taking it dissolves it from a noise field with a bright
//     edge;
//  3. the beads, as small lit spheres.

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  view: d.vec4f, // width, height (pt), pixel ratio, time (s)
  hero: d.vec4f, // centre x, y (pt), unit (pt), how far below the centre its shadow lies (pt)
  shape: d.vec4f, // -, form a, form b, morph k
  rot0: d.vec4f, // object → view rotation, columns
  rot1: d.vec4f,
  rot2: d.vec4f,
  fx: d.vec4f, // take 0…1, reform 0…1 (scale), hold 0…1, -
  goal: d.vec4f, // the check x, y (pt), -, -
  open: d.vec4f, // the capsule: cap pulled off (units), cap twist (rad), inner beads hidden, its tilt (rad)
  phys: d.vec4f, // the spilled pearls: dt (s), mode (0 none, 1 spawn, 2 live, 3 back into the capsule, 4 to the check), finger x, y (pt)
  floor: d.vec4f, // the sheet they land on: y, x min, x max (pt), radius (pt)
  aux: d.vec4f, // finger dx, dy this frame (pt), finger down, -
});

export const SPILL = 40;

export const BEADS = 900;
export const Bead = d.struct({
  at: d.vec4f, // x, y (pt), radius (pt), alpha
  tint: d.vec4f, // rgb (linear), lift
});

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;
@group(0) @binding(1) var<storage, read_write> beadsOut: array<Bead>;
@group(0) @binding(2) var<storage, read> beads: array<Bead>;
@group(0) @binding(3) var<storage, read_write> pearls: array<vec4f, ${SPILL * 2}>;

const TAU = 6.2831853;
fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn hash3(p: vec3f) -> f32 { return fract(sin(dot(p, vec3f(127.1, 311.7, 74.7))) * 43758.5453); }
fn hash1(n: f32) -> f32 { return fract(sin(n * 91.3458) * 47453.5453); }
fn noise3(x: vec3f) -> f32 {
  let i = floor(x); let f = fract(x); let u = f * f * (3.0 - 2.0 * f);
  return mix(mix(mix(hash3(i), hash3(i + vec3f(1, 0, 0)), u.x), mix(hash3(i + vec3f(0, 1, 0)), hash3(i + vec3f(1, 1, 0)), u.x), u.y),
             mix(mix(hash3(i + vec3f(0, 0, 1)), hash3(i + vec3f(1, 0, 1)), u.x), mix(hash3(i + vec3f(0, 1, 1)), hash3(i + vec3f(1, 1, 1)), u.x), u.y), u.z);
}
fn fbm(p: vec3f) -> f32 { return 0.6 * noise3(p) + 0.3 * noise3(p * 2.03 + 7.1) + 0.1 * noise3(p * 4.1 + 3.3); }

/* ------------------------------------------------------------------ the three forms (units of U, y up) */

fn capsuleField(p: vec3f) -> f32 { return length(p - vec3f(clamp(p.x, -0.56, 0.56), 0.0, 0.0)) - 0.44; }
// the blue half (x < 0) stays; the white cap is pulled off along the axis and twisted about it; once
// apart, both are shells, so the blue cup shows its inside
fn sdHalves(p: vec3f) -> vec2f {
  let sep = F.open.x;
  let shell = smoothstep(0.22, 0.4, sep);
  var db = max(capsuleField(p), p.x);
  db = mix(db, max(abs(capsuleField(p)) - 0.035, p.x), shell);
  var q = p - vec3f(sep, 0.0, 0.0);
  let c = cos(F.open.y); let sn = sin(F.open.y);
  q = vec3f(q.x, c * q.y - sn * q.z, sn * q.y + c * q.z);
  var dw = max(capsuleField(q), -q.x);
  dw = mix(dw, max(abs(capsuleField(q)) - 0.035, -q.x), shell);
  dw += 0.01 * exp(-sq(q.x / 0.012)) * (1.0 - shell);
  return vec2f(min(db, dw), select(1.0, 0.0, dw < db));
}
fn sdCapsule(p: vec3f) -> f32 { return sdHalves(p).x; }
fn sdTablet(p: vec3f) -> f32 {
  let q = vec2f(length(p.xz) - 0.64, abs(p.y) - 0.07);
  var d = length(max(q, vec2f(0.0))) + min(max(q.x, q.y), 0.0) - 0.16;
  d += 0.034 * exp(-sq(p.x / 0.03)) * smoothstep(0.12, 0.2, p.y);   // the score line on top
  return d;
}
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
fn sdDrop(p: vec3f) -> f32 {
  let w = 0.012 * sin(p.y * 7.0 + F.view.w * 3.0) * (1.0 - F.shape.w * (1.0 - F.shape.w) * 0.0);
  return sdRoundCone(p, vec3f(0.0, -0.22, 0.0), vec3f(0.0, 0.72, 0.0), 0.6, 0.05) + w;
}
// a form's own pose inside the morph: the capsule tilted 40°, the tablet tipped to show its face
fn pose(p: vec3f, s: f32) -> vec3f {
  var q = p;
  if (s < 0.5) {
    let c = cos(-F.open.w); let sn = sin(-F.open.w);
    q = vec3f(c * p.x - sn * p.y, sn * p.x + c * p.y, p.z);
  } else if (s < 1.5) {
    let c = cos(-1.05); let sn = sin(-1.05);
    q = vec3f(p.x, c * p.y - sn * p.z, sn * p.y + c * p.z);
    let c2 = cos(0.35); let s2 = sin(0.35);
    q = vec3f(c2 * q.x - s2 * q.z, q.y, s2 * q.x + c2 * q.z);
  }
  return q;
}
fn sdForm(p: vec3f, s: f32) -> f32 {
  let q = pose(p, s);
  if (s < 0.5) { return sdCapsule(q); }
  if (s < 1.5) { return sdTablet(q); }
  return sdDrop(q);
}
fn sdObj(p: vec3f) -> f32 {
  let sc = max(F.fx.y, 0.02);
  let q = p / sc;
  let k = F.shape.w;
  // mid-morph it swells a little, like something soft changing its mind
  let d = mix(sdForm(q, F.shape.y), sdForm(q, F.shape.z), k) - 0.05 * sin(k * 3.14159);
  return d * sc;
}
fn normalAt(p: vec3f) -> vec3f {
  let e = 0.0015;
  let k1 = vec3f(1.0, -1.0, -1.0); let k2 = vec3f(-1.0, -1.0, 1.0); let k3 = vec3f(-1.0, 1.0, -1.0); let k4 = vec3f(1.0, 1.0, 1.0);
  return normalize(k1 * sdObj(p + k1 * e) + k2 * sdObj(p + k2 * e) + k3 * sdObj(p + k3 * e) + k4 * sdObj(p + k4 * e));
}
// what each form is made of at p: (blue glass, clear liquid) weights; the rest is white
fn material(p: vec3f) -> vec2f {
  let sc = max(F.fx.y, 0.02);
  let q = p / sc;
  var w = vec2f(0.0);
  for (var i = 0; i < 2; i++) {
    let s = select(F.shape.y, F.shape.z, i == 1);
    let wt = select(1.0 - F.shape.w, F.shape.w, i == 1);
    var m = vec2f(0.0);
    if (s < 0.5) { m = vec2f(sdHalves(pose(q, 0.0)).y, 0.0); }
    else if (s > 1.5) { m = vec2f(0.0, 1.0); }
    w += m * wt;
  }
  return w;
}

/* ------------------------------------------------------------------ the world round it (view space, y up) */

fn toScreen(v: vec2f) -> vec2f { return F.hero.xy + vec2f(v.x, -v.y) * F.hero.z; }
fn toView(s: vec2f) -> vec2f { return vec2f(s.x - F.hero.x, F.hero.y - s.y) / F.hero.z; }

// the white sheet behind the object, and the soft shadow it casts on it
fn groundShadow(s: vec2f) -> f32 {
  let c = F.hero.xy + vec2f(4.0, F.hero.w);
  let q = (s - c) / (vec2f(0.95, 0.075) * F.hero.z * max(F.fx.y, 0.02));
  return 0.42 * exp(-dot(q, q) * 1.6) * (1.0 - F.fx.x);
}
fn behind(s: vec2f) -> vec3f {
  return mix(vec3f(1.0), lin(vec3f(0.66, 0.79, 0.86)), groundShadow(s));
}
// a bright studio: a big soft light above and to the left, the blue room round it
fn studio(r: vec3f) -> vec3f {
  var c = mix(lin(vec3f(0.5, 0.66, 0.92)), lin(vec3f(0.92, 0.95, 1.0)), smoothstep(-0.6, 0.8, r.y));
  let k = normalize(vec3f(-0.5, 0.75, 0.45));
  c += vec3f(1.0, 0.99, 0.97) * 5.0 * smoothstep(0.9, 0.97, dot(r, k));
  let f = normalize(vec3f(0.8, 0.1, 0.6));
  c += vec3f(0.9, 0.95, 1.0) * 1.2 * smoothstep(0.88, 0.98, dot(r, f));
  // the disc below bounces blue
  c = mix(c, lin(vec3f(0.36, 0.58, 0.96)), smoothstep(-0.2, -0.8, r.y) * 0.6);
  return c;
}

struct Px { col: vec3f, a: f32 }

fn shade(sxy: vec2f) -> Px {
  var o: Px;
  o.col = vec3f(0.0);
  o.a = 0.0;
  let v = toView(sxy);
  if (dot(v, v) > 2.4 * 2.4) { return o; }
  let M = mat3x3f(F.rot0.xyz, F.rot1.xyz, F.rot2.xyz);   // object → view
  let Mi = transpose(M);
  // a near-orthographic camera in front of the screen
  let ro = Mi * vec3f(0.0, 0.0, 12.0);
  let rd = Mi * normalize(vec3f(v, -12.0));
  var t = 10.2;
  var hit = false;
  for (var i = 0; i < 80; i++) {
    let dd = sdObj(ro + rd * t);
    if (dd < 0.0008) { hit = true; break; }
    t += dd * 0.85;
    if (t > 14.0) { break; }
  }
  // the soft shadow on the sheet under the object
  let sh = groundShadow(sxy);
  o.col = lin(vec3f(0.66, 0.79, 0.86)) * sh;
  o.a = sh;
  if (!hit) { return o; }
  let p = ro + rd * t;
  // the dissolve: a noise field on the surface, burnt away from its lowest values
  let nz = fbm(p * 3.2);
  let edge = F.fx.x * 1.6 - 0.15;
  if (F.fx.x > 0.0 && nz < edge) { return o; }
  let n = normalAt(p);
  let nv = M * n;
  let vv = vec3f(0.0, 0.0, 1.0);
  let rdv = M * rd;
  let L = normalize(vec3f(-0.5, 0.75, 0.55));
  let mat = material(p);
  let cosv = max(dot(nv, vv), 0.0);
  let fr = 0.04 + 0.96 * pow(1.0 - cosv, 5.0);
  let refl = studio(reflect(rdv, nv));
  let spec = pow(max(dot(reflect(-L, nv), vv), 0.0), 70.0);
  // white: porcelain on the capsule, chalk on the tablet (rougher, speckled)
  let wrap = clamp((dot(nv, L) + 0.35) / 1.35, 0.0, 1.0);
  let chalk = select(0.0, 1.0, F.shape.z > 0.5 && F.shape.z < 1.5) * F.shape.w + select(0.0, 1.0, F.shape.y > 0.5 && F.shape.y < 1.5) * (1.0 - F.shape.w);
  let speck = 1.0 - 0.06 * chalk * hash3(floor(p * 140.0));
  var white = lin(mix(vec3f(0.97, 0.975, 0.985), vec3f(0.74, 0.9, 0.81), chalk)) * (0.42 + 0.62 * wrap) * speck;
  white = mix(white, white * lin(vec3f(0.8, 0.88, 1.0)), smoothstep(0.1, -0.6, dot(nv, L)) * 0.6);
  white += refl * mix(0.07, 0.03, chalk) + vec3f(1.0) * spec * mix(0.55, 0.08, chalk);
  // glass and liquid: through the object to its far side, absorbing on the way
  var tin = 0.0;
  let pin = p - n * 0.003;
  let rin = refract(rd, n, 1.0 / mix(1.5, 1.33, mat.y));
  for (var i = 0; i < 40; i++) {
    let dd = -sdObj(pin + rin * tin);
    if (dd < 0.001) { break; }
    tin += max(dd * 0.9, 0.004);
    if (tin > 3.0) { break; }
  }
  let pex = pin + rin * tin;
  let nex = normalAt(pex);
  var rex = refract(rin, -nex, mix(1.5, 1.33, mat.y));
  if (dot(rex, rex) < 0.01) { rex = reflect(rin, -nex); }
  let exv = M * pex;
  let rexv = M * rex;
  let seen = behind(toScreen(exv.xy + rexv.xy * (0.6 + exv.z) / max(-rexv.z, 0.25)));
  let blueAbs = exp(-vec3f(2.2, 0.75, 0.04) * tin * 1.7);
  let clearAbs = exp(-vec3f(1.1, 0.42, 0.06) * tin * 1.3);
  var through = seen * mix(blueAbs, clearAbs, mat.y);
  // an open, hollow half is thin glass: give it the capsule's blue all the same
  let hollow = smoothstep(0.22, 0.4, F.open.x) * mat.x;
  through = mix(through, through * lin(vec3f(0.3, 0.6, 1.0)) * 1.15, hollow);
  // vitamin beads in the blue half: small glossy spheres
  if (mat.x > 0.5 && F.open.z < 0.5) {
    var bt = 1e5;
    var bn = vec3f(0.0);
    let sc = max(F.fx.y, 0.02);
    for (var i = 0; i < 9; i++) {
      let fi = f32(i);
      // inside the blue half, in the capsule's own frame
      let lq = vec3f(-0.1 - 0.55 * hash1(fi + 1.0), (hash1(fi + 2.0) - 0.5) * 0.52, (hash1(fi + 3.0) - 0.5) * 0.52);
      let c = sin(F.open.w); let cs = cos(F.open.w);
      let bc = vec3f(cs * lq.x - c * lq.y, c * lq.x + cs * lq.y, lq.z) * sc;
      let br = (0.045 + 0.025 * hash1(fi + 4.0)) * sc;
      let oc = pin - bc;
      let bb = dot(oc, rin); let cc = dot(oc, oc) - br * br; let h = bb * bb - cc;
      if (h > 0.0) {
        let tt = -bb - sqrt(h);
        if (tt > 0.0 && tt < tin && tt < bt) { bt = tt; bn = normalize(pin + rin * tt - bc); }
      }
    }
    if (bt < 1e4) {
      let bnv = M * bn;
      let bl = lin(vec3f(1.0, 0.8, 0.32)) * (0.5 + 0.7 * clamp(dot(bnv, L), 0.0, 1.0)) + vec3f(1.0) * pow(max(dot(reflect(-L, bnv), vv), 0.0), 40.0) * 0.9;
      through = mix(through, bl * mix(vec3f(1.0), blueAbs, 0.35), 0.95);
    }
  }
  // the drop gathers light into a bright caustic low in its body
  let caustic = lin(vec3f(0.85, 0.95, 1.0)) * mat.y * 0.5 * pow(max(dot(nv, normalize(vec3f(0.4, -0.7, 0.6))), 0.0), 5.0);
  var glass = fr * refl + (1.0 - fr) * (through + caustic) + vec3f(1.0) * spec * 1.4;
  glass += vec3f(0.6, 0.8, 1.0) * pow(1.0 - cosv, 3.0) * 0.12;
  // the liquid's edge darkens like a lens, so the drop reads on white
  glass = mix(glass, glass * lin(vec3f(0.55, 0.72, 0.92)), mat.y * pow(1.0 - cosv, 1.6) * 0.8);
  let gw = clamp(mat.x + mat.y, 0.0, 1.0);
  var col = mix(white, glass, gw);
  // the burning edge of the dissolve, and the held object warming as the ring fills
  if (F.fx.x > 0.0) { col += vec3f(0.7, 0.9, 1.0) * (1.0 - smoothstep(0.0, 0.06, nz - edge)) * 1.6; }
  col += vec3f(0.5, 0.75, 1.0) * F.fx.z * 0.08 * (1.0 - cosv);
  o.col = col;
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

@fragment fn heroFs(v: VOut) -> @location(0) vec4f {
  let s = v.pos.xy / F.view.z;
  let px = shade(s);
  if (px.a <= 0.0) { return vec4f(0.0); }
  // premultiplied: colour already carries its coverage
  return vec4f(encode(px.col / px.a) * px.a, px.a);
}

/* ------------------------------------------------------------------ the beads */

@compute @workgroup_size(64) fn beadsCs(@builtin(global_invocation_id) gid: vec3u) {
  let i = gid.x;
  if (i >= ${BEADS - SPILL}u) { return; }
  let fi = f32(i);
  let M = mat3x3f(F.rot0.xyz, F.rot1.xyz, F.rot2.xyz);
  // a point on the object's surface: start in its box, then walk onto the field
  var p = (vec3f(hash1(fi + 1.0), hash1(fi + 2.0), hash1(fi + 3.0)) - 0.5) * vec3f(2.2, 1.6, 1.4);
  for (var k = 0; k < 6; k++) { p -= normalAt(p) * sdObj(p); }
  let nz = fbm(p * 3.2);
  // it leaves when the dissolve reaches it
  let leave = nz + 0.15;
  let fly = clamp((F.fx.x * 1.6 - leave) / 0.55, 0.0, 1.0);
  var b: Bead;
  if (F.fx.x <= 0.0 || fly <= 0.0 || fly >= 1.0) {
    b.at = vec4f(0.0, 0.0, 0.0, 0.0);
    b.tint = vec4f(0.0);
    beadsOut[i] = b;
    return;
  }
  let pv = M * p;
  let a = toScreen(pv.xy);
  let tgt = F.goal.xy + (vec2f(hash1(fi + 7.0), hash1(fi + 8.0)) - 0.5) * 6.0;
  let side = (hash1(fi + 5.0) - 0.5) * 140.0;
  let ctrl = mix(a, tgt, 0.45) + vec2f(-60.0 + side * 0.4, -30.0 + side * 0.3);
  let e = fly * fly * (3.0 - 2.0 * fly);
  let q = mix(mix(a, ctrl, e), mix(ctrl, tgt, e), e);
  let mat = material(p);
  let tint = mix(lin(vec3f(0.97, 0.98, 1.0)), lin(vec3f(0.15, 0.5, 1.0)), clamp(mat.x + mat.y * 0.6, 0.0, 1.0));
  let r = (1.3 + 1.6 * hash1(fi + 9.0)) * (1.0 - 0.6 * e);
  let alpha = smoothstep(0.0, 0.06, fly) * (1.0 - smoothstep(0.9, 1.0, fly));
  b.at = vec4f(q, r, alpha);
  b.tint = vec4f(tint, 0.0);
  beadsOut[i] = b;
}

/* ------------------------------------------------------------------ the spilled pearls: rigid balls, one workgroup */

var<workgroup> wp: array<vec2f, 64>;

// where the cup's mouth is on the screen, and the way it faces
fn mouth() -> vec4f {
  let ax = vec2f(cos(F.open.w), -sin(F.open.w));
  return vec4f(F.hero.xy + ax * 0.08 * F.hero.z, ax);
}

@compute @workgroup_size(64) fn pearlsCs(@builtin(local_invocation_index) li: u32) {
  let i = li;
  let n = ${SPILL}u;
  let live = i < n;
  let fi = f32(i);
  let r = F.floor.w;
  let mode = F.phys.y;
  var p = vec2f(0.0);
  var v = vec2f(0.0);
  var st = vec4f(0.0); // alive, age, seed, -
  if (live) {
    let a = pearls[i * 2u];
    p = a.xy; v = a.zw;
    st = pearls[i * 2u + 1u];
    if (mode < 0.5) { st.x = 0.0; }
    if (mode > 0.5 && mode < 1.5) {
      // poured from the cup one after another
      let m = mouth();
      p = m.xy + (vec2f(hash1(fi + 1.0), hash1(fi + 2.0)) - 0.5) * 10.0;
      v = m.zw * (120.0 + 120.0 * hash1(fi + 3.0)) + vec2f((hash1(fi + 4.0) - 0.5) * 90.0, -60.0 * hash1(fi + 5.0));
      st = vec4f(1.0, -0.6 * hash1(fi + 6.0), hash1(fi + 7.0), 0.0);
    }
  }
  let dt = F.phys.x / 3.0;
  for (var k = 0; k < 3; k++) {
    if (live && st.x > 0.5) {
      st.y += dt;
      if (st.y < 0.0) {
        // still in the cup: waiting its turn at the mouth
        p = mouth().xy;
      } else if (mode > 2.5) {
        // drawn home: back into the cup, or to the check
        let goal = select(mouth().xy, F.goal.xy, mode > 3.5);
        let late = st.z * 0.5;
        if (st.w < late) { st.w += dt; v += vec2f(0.0, -260.0) * dt; }
        else {
          let to = goal - p;
          v += (to * 90.0 - v * 13.0) * dt;
          if (length(to) < 7.0) { st.x = 0.0; }
        }
      } else {
        v.y += 1500.0 * dt;
        v *= 0.999;
        // the finger stirs them
        if (F.aux.z > 0.5) {
          let fd = p - F.phys.zw;
          let fl = length(fd);
          if (fl < 30.0) { p += normalize(fd + vec2f(0.001)) * (30.0 - fl) * 0.5; v += F.aux.xy / max(F.phys.x, 0.004) * 0.25 * (1.0 - fl / 30.0); }
        }
      }
      p += v * dt;
      if (mode < 2.5) {
        // the sheet, and the edges of the screen
        if (p.y > F.floor.x - r) { p.y = F.floor.x - r; if (v.y > 0.0) { v.y = -v.y * 0.32; } v.x *= 0.96; }
        if (p.x < F.floor.y + r) { p.x = F.floor.y + r; v.x = abs(v.x) * 0.4; }
        if (p.x > F.floor.z - r) { p.x = F.floor.z - r; v.x = -abs(v.x) * 0.4; }
      }
    }
    wp[i] = select(vec2f(-1e4, -1e4 - fi * 40.0), p, live && st.x > 0.5 && st.y >= 0.0 && mode < 2.5);
    workgroupBarrier();
    if (live && st.x > 0.5 && st.y >= 0.0 && mode < 2.5) {
      for (var j = 0u; j < n; j++) {
        if (j == i) { continue; }
        let d = p - wp[j];
        let l = length(d);
        if (l < 2.0 * r && l > 0.0001) {
          let nn = d / l;
          p += nn * (2.0 * r - l) * 0.5;
          let vn = dot(v, nn);
          if (vn < 0.0) { v -= nn * vn * 0.6; }
        }
      }
    }
    workgroupBarrier();
  }
  if (live) {
    pearls[i * 2u] = vec4f(p, v);
    pearls[i * 2u + 1u] = st;
    var b: Bead;
    let shown = st.x > 0.5 && st.y >= 0.0;
    b.at = vec4f(p, r, select(0.0, 1.0, shown));
    b.tint = vec4f(lin(vec3f(1.0, 0.8, 0.32)), 1.0);
    beadsOut[${BEADS - SPILL}u + i] = b;
  }
}

struct BOut { @builtin(position) pos: vec4f, @location(0) uv: vec2f, @location(1) col: vec3f, @location(2) a: f32 }

@vertex fn beadVs(@builtin(vertex_index) vi: u32, @builtin(instance_index) ii: u32) -> BOut {
  let b = beads[ii];
  var corner = vec2f(-1.0, -1.0);
  if (vi == 1u || vi == 4u) { corner = vec2f(1.0, -1.0); }
  if (vi == 2u || vi == 3u) { corner = vec2f(-1.0, 1.0); }
  if (vi == 5u) { corner = vec2f(1.0, 1.0); }
  var r = b.at.z * 1.15;
  if (b.at.w <= 0.001) { r = 0.0; }
  let at = b.at.xy + corner * r;
  var o: BOut;
  o.pos = vec4f(at.x / F.view.x * 2.0 - 1.0, 1.0 - at.y / F.view.y * 2.0, 0.0, 1.0);
  o.uv = corner * 1.15;
  o.col = b.tint.xyz;
  o.a = b.at.w;
  return o;
}

@fragment fn beadFs(v: BOut) -> @location(0) vec4f {
  let r2 = dot(v.uv, v.uv);
  let cov = 1.0 - smoothstep(0.8, 1.0, r2);
  if (cov <= 0.0) { return vec4f(0.0); }
  let n = vec3f(v.uv, sqrt(max(1.0 - r2, 0.0)));
  let L = normalize(vec3f(-0.5, 0.75, 0.55));
  // a little translucent: the far side glows with the colour
  let rim = pow(1.0 - n.z, 2.0);
  let c = v.col * (0.5 + 0.6 * max(dot(n, L), 0.0)) + v.col * rim * 0.5 + vec3f(1.0) * pow(max(dot(reflect(-L, n), vec3f(0.0, 0.0, 1.0)), 0.0), 40.0) * 0.9;
  let a = cov * v.a;
  return vec4f(encode(c) * a, a);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU, Bead } });
}
