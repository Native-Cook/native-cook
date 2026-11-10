// Serum Dropper Routine — the oil on WebGPU, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout (the frame block is a `d.struct` schema), and `tgpu.resolve` injects
// its WGSL into the template. A transparent canvas over the page draws the oil, as 2D signed distances
// lit as if they had depth: each drop swells at the pipette's tip and hangs from it by a neck, the neck
// thins and lets go, the drop falls (stretching as it gathers speed), lands with a squash and a ring of
// tiny splashes, and spreads into a flat pool on the card's floor (seen low, so a wide ellipse); the
// pools of the drops merge, gleam at their meniscus, and soak in.

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  view: d.vec4f, // width, height (pt), pixel ratio, time (s)
  tip: d.vec4f, // the pipette's tip x, y (pt), the card's floor y (pt), soak 0…1
  ages: d.vec4f, // the three drops' ages (s, −1 not yet), the scale
  pool: d.vec4f, // where the drops land along the floor x (pt), -, -, -
});

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;

const GROW = 0.55;
const NECK = 0.16;
const G = 1500.0;
fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn ease(x: f32) -> f32 { let c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }
fn smin(a: f32, b: f32, k: f32) -> f32 { let h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }
fn sdSeg(p: vec2f, a: vec2f, b: vec2f) -> f32 { let pa = p - a; let ba = b - a; let h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
fn sdEll(p: vec2f, r: vec2f) -> f32 { let k = length(p / r); return (k - 1.0) * min(r.x, r.y); }

// where each drop lands along the floor, so the pools do not stack
fn landX(i: i32) -> f32 { return F.pool.x + array<f32, 3>(0.0, 7.0, -6.0)[i] * F.ages.w; }

struct Oil { airborne: f32, pool: f32 }

fn oilAt(s: vec2f) -> Oil {
  var o: Oil;
  o.airborne = 1e9;
  o.pool = 1e9;
  let sc = F.ages.w;
  let tip = F.tip.xy;
  let floorY = F.tip.z;
  for (var i = 0; i < 3; i++) {
    let a = F.ages[i];
    if (a < 0.0) { continue; }
    let r = 4.8 * sc;
    if (a < GROW + NECK) {
      // hanging: it swells, sags and necks
      let g = ease(a / GROW);
      let u = clamp((a - GROW) / NECK, 0.0, 1.0);
      let rr = r * (0.35 + 0.65 * g);
      let c = tip + vec2f(0.0, rr * 0.95 + 3.5 * sc * u);
      let drop = sdEll(s - c, vec2f(rr, rr * (1.0 + 0.18 * g + 0.25 * u)));
      let neck = sdSeg(s, tip, c) - (1.3 * sc) * (1.0 - u * 0.92);
      o.airborne = min(o.airborne, smin(drop, neck, 2.2 * sc * (1.0 - u * 0.7)));
      continue;
    }
    // falling: it stretches with its speed, then lands
    let tau = a - GROW - NECK;
    let y0 = tip.y + r + 3.5 * sc;
    let drop = y0 + 0.5 * G * sc * tau * tau;
    let tLand = sqrt(max(2.0 * (floorY - r * 0.5 - y0) / (G * sc), 0.0));
    let x = mix(tip.x, landX(i), clamp(tau / max(tLand, 0.01), 0.0, 1.0));
    if (tau < tLand) {
      let v = G * sc * tau;
      let stretch = 1.0 + min(v / (900.0 * sc), 0.7);
      o.airborne = min(o.airborne, sdEll(s - vec2f(x, drop), vec2f(r / sqrt(stretch), r * stretch)));
      continue;
    }
    // landed: a squash, a ring of tiny splashes, and a pool that spreads
    let tp = tau - tLand;
    let c = vec2f(landX(i), floorY);
    let R = (6.0 + 12.0 * (1.0 - exp(-4.5 * tp))) * sc * (1.0 + 0.25 * F.tip.w);
    let squash = exp(-tp * 14.0);
    var pool = sdEll(s - c, vec2f(R, R * 0.3));
    if (squash > 0.04) {
      let bead = sdEll(s - c - vec2f(0.0, -r * 0.6 * squash), vec2f(r * (1.0 + squash * 0.6), max(r * 0.7 * squash, 0.8 * sc)));
      pool = smin(pool, bead, 3.0 * sc);
    }
    if (tp < 0.35) {
      for (var k = 0; k < 6; k++) {
        let ang = f32(k) / 6.0 * 6.2831853 + f32(i);
        let out = vec2f(cos(ang), sin(ang) * 0.3) * (6.0 + 46.0 * tp) * sc;
        let up = (60.0 * tp - 190.0 * tp * tp) * sc;
        let sp = length(s - c - out + vec2f(0.0, up)) - 1.2 * sc * (1.0 - tp / 0.35);
        o.airborne = min(o.airborne, sp);
      }
    }
    o.pool = smin(o.pool, pool, 6.0 * sc);
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

fn encode(c: vec3f) -> vec3f { return pow(clamp(c, vec3f(0.0), vec3f(1.0)), vec3f(1.0 / 2.2)); }

@fragment fn oilFs(v: VOut) -> @location(0) vec4f {
  let s = v.pos.xy / F.view.z;
  let sc = F.ages.w;
  // only near the drops' way and the floor
  if (abs(s.x - F.pool.x) > 70.0 * sc || s.y < F.tip.y - 4.0 || s.y > F.tip.z + 20.0 * sc) { return vec4f(0.0); }
  let o = oilAt(s);
  var col = vec3f(0.0);
  var a = 0.0;
  // the pool: a thin amber film, its meniscus bright, a gleam across it, soaking away
  let soak = F.tip.w;
  if (o.pool < 1.0) {
    let inside = 1.0 - smoothstep(-0.6, 0.6, o.pool);
    let film = lin(vec3f(0.92, 0.56, 0.15));
    let edge = exp(-sq((o.pool + 1.2) / 1.0));
    var c = film * 0.85 + vec3f(1.0, 0.95, 0.85) * 0.7 * edge;
    let gleam = exp(-sq((s.y - F.tip.z + 1.5 * sc) / (1.2 * sc))) * exp(-sq((s.x - F.pool.x + 6.0 * sc) / (9.0 * sc)));
    c += vec3f(1.0) * 0.6 * gleam;
    let pa = inside * (0.5 + 0.35 * edge + 0.3 * gleam) * (1.0 - soak);
    col = c * pa;
    a = pa;
  }
  // the drops: amber glass beads, darker at their rim, a hot highlight up and left
  if (o.airborne < 1.0) {
    let inside = 1.0 - smoothstep(-0.6, 0.6, o.airborne);
    let depth = clamp(-o.airborne / (3.5 * sc), 0.0, 1.0);
    var c = lin(vec3f(0.98, 0.72, 0.3)) * (0.55 + 0.45 * depth) + lin(vec3f(0.55, 0.28, 0.05)) * (1.0 - depth) * 0.6;
    // its slope from the distance field: a bead's surface tilts away towards its rim
    let e = 0.5;
    let gx = oilAt(s + vec2f(e, 0.0)).airborne - o.airborne;
    let gy = oilAt(s + vec2f(0.0, e)).airborne - o.airborne;
    let n = normalize(vec3f(gx / e, gy / e, 0.35 + 1.6 * depth));
    let L = normalize(vec3f(-0.5, -0.65, 0.6));
    c += vec3f(1.0) * pow(max(dot(reflect(-L, n), vec3f(0.0, 0.0, 1.0)), 0.0), 18.0) * 1.2;
    c += lin(vec3f(1.0, 0.85, 0.5)) * 0.35 * smoothstep(0.2, 0.9, -n.y) * depth;
    let da = inside * (0.7 + 0.25 * (1.0 - depth));
    col = c * da + col * (1.0 - da);
    a = da + a * (1.0 - da);
  }
  if (a <= 0.001) { return vec4f(0.0); }
  return vec4f(encode(col / a) * a, a);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU } });
}
