// Pin Drop Location — the map, the globe and the pin on WebGPU, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout (the frame block and the sparks' records are `d.struct` schemas), and
// `tgpu.resolve` injects their WGSL into the template. A transparent canvas over the page, each frame:
//  1. compute: 240 sparks thrown out round the pin's tip as it lands, each a pure function of the time
//     since (out along the map, slowing, fading);
//  2. one full-screen pass: the night map in its disc — sea and land from a noise field, a lit main
//     road, streets, the city's lights coming on in a ring that runs out from the pin — which, as it
//     bends, wraps onto a globe (the flat map's coordinates blended into longitude and latitude on a
//     turning sphere, its shading from flat to round, an atmosphere at its rim); rings from the pin's
//     tip; the places that rise when allowed; and the pin itself, sphere-traced pink glass with a hole
//     through its head, a soft shadow, falling and squashing as it lands;
//  3. the sparks.

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  view: d.vec4f, // width, height (pt), pixel ratio, time (s)
  disc: d.vec4f, // centre x, y, radius (pt), bend 0…1
  spin: d.vec4f, // longitude, latitude (rad), zoom 0…1, places 0…1
  pin: d.vec4f, // fall 0…1 (1 on the map), hover 0…1, seconds since it landed (−1 none), the city's lights 0…1
});

export const SPARKS = 240;
export const Spark = d.struct({
  at: d.vec4f, // x, y (pt), size (pt), alpha
});

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;
@group(0) @binding(1) var<storage, read_write> sparksOut: array<Spark>;
@group(0) @binding(2) var<storage, read> sparks: array<Spark>;

const TAU = 6.2831853;
const PI = 3.14159265;
fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn hash1(n: f32) -> f32 { return fract(sin(n * 91.3458) * 47453.5453); }
fn hash2(p: vec2f) -> f32 { return fract(sin(dot(p, vec2f(127.1, 311.7))) * 43758.5453); }
fn noise2(x: vec2f) -> f32 {
  let i = floor(x); let f = fract(x); let u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash2(i), hash2(i + vec2f(1.0, 0.0)), u.x), mix(hash2(i + vec2f(0.0, 1.0)), hash2(i + vec2f(1.0, 1.0)), u.x), u.y);
}
fn fbm(p: vec2f) -> f32 { return 0.55 * noise2(p) + 0.28 * noise2(p * 2.1 + 5.3) + 0.17 * noise2(p * 4.3 + 1.7); }

// the pin's tip, in disc units (y up), and where it stands on the map
const TIP = vec2f(0.0, -0.49);

/* ------------------------------------------------------------------ the map (uv in radians, y up) */

fn mapAt(uv: vec2f, pinUv: vec2f) -> vec3f {
  // land and sea; at home the land lies right of the main road, as in the reference
  let home = exp(-dot(uv, uv) * 1.1);
  let field = (fbm(uv * 1.25 + vec2f(3.7, 1.1)) - 0.47) * (1.0 - 0.85 * home) + clamp((uv.x - uv.y + 0.03) * 4.0, -0.9, 0.9) * home;
  let land = smoothstep(-0.02, 0.02, field);
  var c = mix(lin(vec3f(0.16, 0.13, 0.27)), lin(vec3f(0.29, 0.27, 0.57)), land);
  // lighter towards the land's lower right, like the light falling on it
  c = mix(c, lin(vec3f(0.37, 0.35, 0.68)), land * smoothstep(-0.2, 0.9, uv.x - uv.y) * 0.6);
  // streets on the land: two faint grids at an angle, broken into blocks
  let g1 = vec2f(dot(uv, vec2f(0.87, 0.5)), dot(uv, vec2f(-0.5, 0.87))) * 9.0;
  let gl = abs(fract(g1) - 0.5);
  let blockOn = step(0.35, hash2(floor(g1)));
  let streets = (1.0 - smoothstep(0.015, 0.04, min(gl.x, gl.y))) * land * blockOn * 0.1 * (1.0 - home * 0.95);
  c += lin(vec3f(0.6, 0.55, 0.9)) * streets;
  // the main road along the coast, bright
  // at home it runs parallel to the coast, a little way in, as in the reference; elsewhere along the coast
  let homeRoad = abs(uv.x - uv.y - 0.36 - 0.07 * sin((uv.x + uv.y) * 2.4)) * 0.7071;
  let coastRoad = abs(field + 0.02) * 0.6;
  let road = mix(coastRoad, homeRoad, smoothstep(0.08, 0.28, home));
  c = mix(c, lin(vec3f(0.79, 0.76, 0.95)), (1.0 - smoothstep(0.012, 0.026, road)) * 0.95);
  // the city's lights, coming on in a ring that runs out from the pin
  let cell = floor(uv * 26.0);
  let h = hash2(cell);
  if (h > 0.8 && land > 0.5) {
    let pp = (cell + 0.5 + (vec2f(hash2(cell + 3.1), hash2(cell + 7.7)) - 0.5) * 0.6) / 26.0;
    let dd = length(uv - pp) * 26.0;
    let since = F.pin.z;
    var on = 0.0;
    if (since >= 0.0) { on = F.pin.w * smoothstep(0.0, 0.15, since * 0.8 - length(pp - pinUv)); }
    let tw = 0.75 + 0.25 * sin(F.view.w * TAU * (0.2 + 0.2 * h) + h * 40.0);
    c += lin(vec3f(1.0, 0.72, 0.85)) * exp(-dd * dd * 18.0) * on * tw * 1.3;
  }
  return c;
}

/* ------------------------------------------------------------------ the pin (units of its own height, y up, tip at 0) */

// the classic map pin, revolved about its axis and pressed thin front to back: a head of radius 0.36
// centred 0.62 above the tip, its sides tangent to the head, and a hole through it
fn sdTear(q: vec2f) -> f32 {
  let cy = 0.62; let r = 0.36;
  let sa = r / cy; let ca = sqrt(1.0 - sa * sa);
  let len = sqrt(cy * cy - r * r);
  let along = dot(q, vec2f(sa, ca));
  if (along < 0.0) { return length(q); }
  if (along < len) { return dot(q, vec2f(ca, -sa)); }
  return length(q - vec2f(0.0, cy)) - r;
}
fn sdPin(p0: vec3f) -> f32 {
  let p = vec3f(p0.x, p0.y, p0.z * 1.9);
  let body = sdTear(vec2f(length(p.xz), p.y)) / 1.9;
  let hole = length(p0.xy - vec2f(0.0, 0.62)) - 0.135;
  return max(body, -hole);
}
fn pinNormal(p: vec3f) -> vec3f {
  let e = 0.002;
  let k1 = vec3f(1.0, -1.0, -1.0); let k2 = vec3f(-1.0, -1.0, 1.0); let k3 = vec3f(-1.0, 1.0, -1.0); let k4 = vec3f(1.0, 1.0, 1.0);
  return normalize(k1 * sdPin(p + k1 * e) + k2 * sdPin(p + k2 * e) + k3 * sdPin(p + k3 * e) + k4 * sdPin(p + k4 * e));
}

struct Px { col: vec3f, a: f32 }
fn over(base: Px, c: vec3f, a: f32) -> Px {
  var o: Px;
  o.col = c * a + base.col * (1.0 - a);
  o.a = a + base.a * (1.0 - a);
  return o;
}

fn pinScreen() -> vec3f {
  // the pin's tip on the screen (pt) and its height (pt)
  let R = F.disc.z;
  let tip = F.disc.xy + vec2f(TIP.x, -TIP.y) * R;
  let drop = (1.0 - F.pin.x) * (tip.y + 120.0) + F.pin.y * 16.0 * F.pin.x;
  return vec3f(tip.x, tip.y - drop, R * 1.23);
}

fn shade(s: vec2f) -> Px {
  var o: Px;
  o.col = vec3f(0.0);
  o.a = 0.0;
  let R = F.disc.z;
  let bend = F.disc.w;
  let q = (s - F.disc.xy) / (R * (1.0 + 0.08 * bend));
  let r = length(q);
  // the glow behind the disc
  // an atmosphere round the globe as it bends
  let glow = exp(-sq(max(r - 1.0, 0.0) / 0.12)) * 0.55 * bend * step(1.0, r);
  o = over(o, lin(vec3f(0.45, 0.36, 0.95)), glow);
  // the map in its disc, flat or wrapped on the globe
  let zoomK = 1.0 / (1.0 + 0.7 * F.spin.z);
  if (r < 1.0) {
    let z = sqrt(max(1.0 - r * r, 0.0));
    let ys = vec3f(q.x, -q.y, z);
    // turn the sphere by the latitude, then read its longitude
    let cl = cos(F.spin.y); let sl = sin(F.spin.y);
    let pr = vec3f(ys.x, cl * ys.y + sl * ys.z, -sl * ys.y + cl * ys.z);
    let sphereUv = vec2f(atan2(pr.x, pr.z) + F.spin.x, asin(clamp(pr.y, -1.0, 1.0)));
    let flatUv = vec2f(q.x, -q.y) + vec2f(F.spin.x, F.spin.y);
    let tipUv = TIP + vec2f(F.spin.x, F.spin.y);
    var uv = mix(flatUv, sphereUv, bend);
    uv = tipUv + (uv - tipUv) * zoomK;
    var c = mapAt(uv, tipUv);
    // flat it is lit evenly; round it turns from the light and glows at its rim
    let n = normalize(mix(vec3f(0.0, 0.0, 1.0), ys, bend));
    let L = normalize(vec3f(-0.5, 0.6, 0.65));
    c *= mix(1.0, 0.35 + 0.85 * max(dot(n, L), 0.0), bend);
    c += lin(vec3f(0.55, 0.45, 1.0)) * pow(1.0 - z, 3.0) * 0.9 * bend;
    c += vec3f(1.0) * pow(max(dot(reflect(-L, n), vec3f(0.0, 0.0, 1.0)), 0.0), 60.0) * 0.0 * bend;
    // rings running out from where the pin landed
    let since = F.pin.z;
    if (since >= 0.0 && since < 1.6) {
      let tip = F.disc.xy + vec2f(TIP.x, -TIP.y) * R;
      let dr = length((s - tip) * vec2f(1.0, 1.6));
      for (var k = 0; k < 2; k++) {
        let tt = since - f32(k) * 0.22;
        if (tt > 0.0) {
          let front = tt * 120.0;
          c += lin(vec3f(1.0, 0.55, 0.75)) * exp(-sq((dr - front) / (2.0 + 6.0 * tt))) * (1.0 - tt / 1.6) * 0.8;
        }
      }
    }
    // the places that rise round the pin once allowed
    if (F.spin.w > 0.0) {
      for (var k = 0; k < 3; k++) {
        let pl = array<vec2f, 3>(vec2f(-0.58, -0.3), vec2f(0.6, 0.25), vec2f(0.35, -0.72))[k];
        let ps = F.disc.xy + vec2f(pl.x, -pl.y) * R;
        let up = clamp(F.spin.w * 1.6 - f32(k) * 0.25, 0.0, 1.0);
        let pd = length(s - ps - vec2f(0.0, -10.0 * up));
        c = mix(c, lin(vec3f(0.98, 0.95, 1.0)), (1.0 - smoothstep(4.5, 5.5, pd)) * up);
        c += lin(vec3f(0.75, 0.6, 1.0)) * exp(-pd * pd / 120.0) * up * 0.8;
        c = mix(c, c * 0.7, (1.0 - smoothstep(3.0, 6.0, length((s - ps) * vec2f(1.0, 2.5))) * up));
      }
    }
    let a = 1.0 - smoothstep(0.985, 1.0, r);
    o = over(o, c, a);
  }
  // the pin: its soft shadow on the map, then the pin itself
  let ps = pinScreen();
  let H = ps.z;
  let tipRest = F.disc.xy + vec2f(TIP.x, -TIP.y) * R;
  let height = max(tipRest.y - ps.y, 0.0);
  let shd = length((s - tipRest - vec2f(height * 0.15, 0.0)) / vec2f(18.0 + height * 0.12, 6.0 + height * 0.04));
  let sh = 0.5 * exp(-shd * shd * 1.5) / (1.0 + height / 90.0) * step(r, 1.0);
  o = over(o, vec3f(0.0), sh * o.a);
  let since = F.pin.z;
  var sq2 = 0.0;
  if (since >= 0.0) { sq2 = 0.13 * exp(-since * 9.0) * cos(since * 26.0) * smoothstep(0.0, 0.03, since) * select(0.0, 1.0, since < 0.5); }
  let local = vec2f((s.x - ps.x) / (H * (1.0 + sq2)), (ps.y - s.y) / (H * (1.0 - sq2)));
  if (local.x > -0.45 && local.x < 0.45 && local.y > -0.05 && local.y < 1.05) {
    let ro = vec3f(local, 3.0);
    let rd = vec3f(0.0, 0.0, -1.0);
    var t = 2.0;
    var hit = false;
    for (var i = 0; i < 80; i++) {
      let dd = sdPin(ro + rd * t);
      if (dd < 0.0008) { hit = true; break; }
      t += dd * 0.8;
      if (t > 4.0) { break; }
    }
    if (hit) {
      let p = ro + rd * t;
      let n = pinNormal(p);
      let L = normalize(vec3f(-0.5, 0.6, 0.65));
      let base = mix(lin(vec3f(1.0, 0.24, 0.37)), lin(vec3f(1.0, 0.42, 0.6)), smoothstep(0.1, 0.95, p.y));
      let wrap = clamp((dot(n, L) + 0.3) / 1.3, 0.0, 1.0);
      var c = base * (0.45 + 0.6 * wrap);
      // glassy: a bright rim and a hot highlight
      c += lin(vec3f(1.0, 0.7, 0.8)) * pow(1.0 - max(n.z, 0.0), 2.5) * 0.5;
      c += vec3f(1.0) * pow(max(dot(reflect(-L, n), vec3f(0.0, 0.0, 1.0)), 0.0), 50.0) * 0.6;
      o = over(o, c, 1.0);
    }
  }
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

@fragment fn mapFs(v: VOut) -> @location(0) vec4f {
  let px = shade(v.pos.xy / F.view.z);
  if (px.a <= 0.0) { return vec4f(0.0); }
  return vec4f(encode(px.col / px.a) * px.a, px.a);
}

/* ------------------------------------------------------------------ the sparks of the landing */

@compute @workgroup_size(64) fn sparksCs(@builtin(global_invocation_id) gid: vec3u) {
  let i = gid.x;
  if (i >= ${SPARKS}u) { return; }
  let fi = f32(i);
  let since = F.pin.z;
  var s: Spark;
  let life = 0.35 + 0.3 * hash1(fi + 1.0);
  if (since < 0.0 || since > life) {
    s.at = vec4f(0.0);
    sparksOut[i] = s;
    return;
  }
  let R = F.disc.z;
  let tip = F.disc.xy + vec2f(TIP.x, -TIP.y) * R;
  let a = hash1(fi + 2.0) * TAU;
  let speed = 120.0 + 260.0 * hash1(fi + 3.0);
  let k = 1.0 - exp(-since * 6.0);
  let p = tip + vec2f(cos(a), sin(a) * 0.5) * speed * k / 6.0 + vec2f(0.0, -26.0 * k * hash1(fi + 4.0));
  let x = since / life;
  s.at = vec4f(p, 0.7 + 0.9 * hash1(fi + 5.0), (1.0 - x) * (1.0 - x) * smoothstep(0.0, 0.04, since));
  sparksOut[i] = s;
}

struct SOut { @builtin(position) pos: vec4f, @location(0) uv: vec2f, @location(1) a: f32 }

@vertex fn sparkVs(@builtin(vertex_index) vi: u32, @builtin(instance_index) ii: u32) -> SOut {
  let s = sparks[ii];
  var corner = vec2f(-1.0, -1.0);
  if (vi == 1u || vi == 4u) { corner = vec2f(1.0, -1.0); }
  if (vi == 2u || vi == 3u) { corner = vec2f(-1.0, 1.0); }
  if (vi == 5u) { corner = vec2f(1.0, 1.0); }
  var r = s.at.z * 2.5;
  if (s.at.w <= 0.001) { r = 0.0; }
  let at = s.at.xy + corner * r;
  var o: SOut;
  o.pos = vec4f(at.x / F.view.x * 2.0 - 1.0, 1.0 - at.y / F.view.y * 2.0, 0.0, 1.0);
  o.uv = corner;
  o.a = s.at.w;
  return o;
}

@fragment fn sparkFs(v: SOut) -> @location(0) vec4f {
  let r2 = dot(v.uv, v.uv);
  let a = clamp(exp(-r2 * 7.0) * v.a, 0.0, 1.0);
  let c = vec3f(1.0, 0.8, 0.9);
  return vec4f(c * a, a);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU, Spark } });
}
