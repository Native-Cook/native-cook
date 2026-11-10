// Gold Emblem Membership — the card's metal on WebGPU, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout (the frame block is a `d.struct` schema), and `tgpu.resolve` injects
// its WGSL into the template. One full-screen pass over a canvas a little larger than the card: the
// card is a height field (a bevelled plate, a brushed grain, two soft swells, the medal and its crown
// in relief, a ribbon) shaded as metal in a studio. A change of tier casts it again: a heat front
// runs across from the side it was swiped from, the metal glows from red to white-hot and goes
// liquid (travelling ripples, a slow churn, drips running off its lower edge), changes metal while
// molten, then cools back the other way and is stamped with its new medal.

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  view: d.vec4f, // canvas width, height (pt), pixel ratio, time (s)
  card: d.vec4f, // the card's left, top, width, height (pt, in the canvas)
  tier: d.vec4f, // from, to (0 gold, 1 platinum, 2 black), cast 0…1, side (1 heat from the right, −1 left)
  light: d.vec4f, // the light's sweep −1…1 (with the drag), -, -, -
});

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;

const PI = 3.14159265;
const TAU = 6.2831853;
fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn hash2(p: vec2f) -> f32 { return fract(sin(dot(p, vec2f(127.1, 311.7))) * 43758.5453); }
fn noise2(x: vec2f) -> f32 {
  let i = floor(x); let f = fract(x); let u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash2(i), hash2(i + vec2f(1.0, 0.0)), u.x), mix(hash2(i + vec2f(0.0, 1.0)), hash2(i + vec2f(1.0, 1.0)), u.x), u.y);
}
fn ease(x: f32) -> f32 { let c = clamp(x, 0.0, 1.0); return c * c * (3.0 - 2.0 * c); }
fn sdRound(p: vec2f, b: vec2f, r: f32) -> f32 { let q = abs(p) - b + r; return length(max(q, vec2f(0.0))) + min(max(q.x, q.y), 0.0) - r; }
fn smin(a: f32, b: f32, k: f32) -> f32 { let h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }

/* ------------------------------------------------------------------ the metals: base, highlight, the medal's face, the ribbon's two colours */

struct Metal { base: vec3f, hi: vec3f, medal: vec3f, ribA: vec3f, ribB: vec3f }
fn metal(i: f32) -> Metal {
  var m: Metal;
  if (i < 0.5) {
    m.base = lin(vec3f(0.82, 0.69, 0.47)); m.hi = lin(vec3f(0.98, 0.9, 0.72)); m.medal = lin(vec3f(0.93, 0.84, 0.66));
    m.ribA = lin(vec3f(0.98, 0.66, 0.16)); m.ribB = lin(vec3f(0.93, 0.33, 0.2));
  } else if (i < 1.5) {
    m.base = lin(vec3f(0.74, 0.79, 0.81)); m.hi = lin(vec3f(0.96, 0.98, 0.99)); m.medal = lin(vec3f(0.88, 0.91, 0.93));
    m.ribA = lin(vec3f(0.36, 0.62, 0.7)); m.ribB = lin(vec3f(0.85, 0.88, 0.9));
  } else {
    m.base = lin(vec3f(0.17, 0.17, 0.19)); m.hi = lin(vec3f(0.46, 0.46, 0.5)); m.medal = lin(vec3f(0.86, 0.7, 0.42));
    m.ribA = lin(vec3f(0.12, 0.12, 0.13)); m.ribB = lin(vec3f(0.88, 0.72, 0.42));
  }
  return m;
}

/* ------------------------------------------------------------------ the cast's state at a point of the card (u: 0 … 1 across) */

struct Cast { heat: f32, melt: f32, newMetal: f32, stamp: f32 }
fn castAt(u: f32) -> Cast {
  var c: Cast;
  let k = F.tier.z;
  // across from the swiped side: 0 at the side the heat comes from
  var x = u;
  if (F.tier.w > 0.0) { x = 1.0 - u; }
  // the heat front runs across in the first third; the cooling front runs back in the last
  let hot = ease((k * 3.2 - x * 0.9) / 0.35);
  let cool = ease(((k - 0.58) * 3.0 - (1.0 - x) * 0.8) / 0.3);
  c.heat = hot * (1.0 - cool);
  c.melt = c.heat;
  c.newMetal = ease((k - 0.42) / 0.14);
  if (k <= 0.0) { c.heat = 0.0; c.melt = 0.0; c.newMetal = 0.0; }
  // the new medal is pressed in once the plate has cooled
  let s = clamp((k - 0.84) / 0.16, 0.0, 1.0);
  c.stamp = select(1.0, s * s * (3.0 - 2.0 * s) * (1.0 + 0.25 * sin(s * PI)), k > 0.0);
  return c;
}

/* ------------------------------------------------------------------ the plate's height, in card points */

fn crown(q: vec2f) -> f32 {
  // a little crown: a band and three points
  let band = sdRound(q - vec2f(0.0, 4.0), vec2f(9.0, 2.2), 0.8);
  var pts = 1e9;
  for (var i = -1; i <= 1; i++) {
    let x = f32(i) * 7.5;
    let pq = q - vec2f(x, -1.5);
    // a triangle pointing up
    let tri = max(abs(pq.x) * 0.9 + pq.y * 0.45 - 2.8, -pq.y - 4.5);
    pts = min(pts, tri);
  }
  return min(band, pts);
}

fn height(p: vec2f, c: Cast) -> f32 {
  let W = F.card.z; let H = F.card.w;
  let ctr = vec2f(W, H) * 0.5;
  let sd = sdRound(p - ctr, ctr, 10.0);
  // the bevel
  var h = clamp(-sd / 3.0, 0.0, 1.0) * 0.8;
  // brushed grain along the diagonal, and two broad swells at the lower right, as in the reference
  let g = vec2f(dot(p, vec2f(0.94, 0.34)), dot(p, vec2f(-0.34, 0.94)));
  h += (noise2(vec2f(g.x * 0.04, g.y * 1.6)) - 0.5) * 0.06;
  h += 0.35 * exp(-sq((length(p - vec2f(W * 0.95, H * 1.35)) - H * 0.95) / 22.0));
  h += 0.25 * exp(-sq((length(p - vec2f(W * 0.62, H * 1.6)) - H * 0.95) / 26.0));
  // the medal: a raised disc with a rim and a crown, pressed in by the stamp
  let mp = p - vec2f(313.0, 62.0) * vec2f(W / 367.0, H / 185.0);
  let mr = length(mp);
  let disc = (1.0 - smoothstep(22.0, 24.5, mr)) * 1.6 + exp(-sq((mr - 19.0) / 1.3)) * 0.7 - (1.0 - smoothstep(16.0, 17.5, mr)) * 0.5;
  let cr = (1.0 - smoothstep(-0.6, 0.6, crown(mp * 1.15))) * 0.7;
  h += (disc + cr) * c.stamp;
  // molten: ripples running from the swiped side, a slow churn, all fading as it cools
  let t = F.view.w;
  var xs = p.x;
  if (F.tier.w > 0.0) { xs = W - p.x; }
  let rip = sin(xs * 0.11 - t * 9.0) * exp(-xs / W) * 0.6 + sin(length(p - vec2f(W * 0.4, H * 0.5)) * 0.18 - t * 6.0) * 0.35;
  // a slow churn of smooth swells (value noise would show its grid in the liquid's highlights)
  let churn = sin(p.x * 0.045 + t * 1.1) * cos(p.y * 0.06 - t * 0.8) * 0.9 + sin((p.x + p.y * 1.3) * 0.08 + t * 1.6) * 0.45 + sin((p.x * 1.7 - p.y) * 0.05 - t * 0.9) * 0.4;
  h = mix(h, 0.9 + rip * 0.8 + churn, c.melt);
  return h;
}

/* ------------------------------------------------------------------ the shape: a rounded plate that sags and drips while molten */

fn shapeD(p: vec2f) -> f32 {
  let W = F.card.z; let H = F.card.w;
  let ctr = vec2f(W, H) * 0.5;
  let u = clamp(p.x / W, 0.0, 1.0);
  let c = castAt(u);
  // the lower edge sags under the heat, and drips grow from it
  let sag = c.melt * 5.0 * sin(u * PI);
  var dd = sdRound(p - ctr - vec2f(0.0, sag * 0.5), ctr + vec2f(0.0, sag * 0.5), 10.0);
  let k = F.tier.z;
  for (var i = 0; i < 6; i++) {
    let fi = f32(i);
    let dx = (0.12 + 0.15 * fi + 0.04 * sin(fi * 7.3)) * W;
    let ci = castAt(dx / W);
    let len = ci.melt * (6.0 + 12.0 * fract(sin(fi * 12.7) * 437.5)) * (1.0 + 0.5 * sin(k * PI));
    if (len > 0.3) {
      let a = vec2f(dx, H - 2.0 + sag);
      let b = a + vec2f(0.0, len);
      let pa = p - a; let ba = b - a;
      let hh = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
      let r = 3.2 + 1.2 * hh;
      dd = smin(dd, length(pa - ba * hh) - r, 5.0);
    }
  }
  return dd;
}

/* ------------------------------------------------------------------ shading */

fn studio(r: vec3f) -> vec3f {
  // a warm room: a soft box overhead swept by the drag, a window to the left, a darker floor
  let sw = F.light.x;
  var c = mix(lin(vec3f(0.22, 0.2, 0.2)), lin(vec3f(0.78, 0.76, 0.74)), smoothstep(-0.4, 0.7, r.y));
  c += vec3f(1.0) * 1.6 * smoothstep(0.45, 0.8, r.y) * (1.0 - smoothstep(0.25, 0.6, abs(r.x - 0.25 * sw)));
  c += vec3f(1.0, 0.97, 0.92) * 0.9 * smoothstep(0.6, 0.9, -r.x) * smoothstep(-0.2, 0.3, r.y);
  return c;
}
// the colour of something at a temperature 0 … 1 (dull red, orange, yellow-white)
fn glow(k: f32) -> vec3f {
  return lin(vec3f(1.0, 0.25, 0.05)) * smoothstep(0.0, 0.4, k) + lin(vec3f(1.0, 0.62, 0.2)) * smoothstep(0.3, 0.75, k) * 1.4 + vec3f(1.0, 0.92, 0.75) * smoothstep(0.7, 1.0, k) * 1.6;
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
  let p = s - F.card.xy;
  let W = F.card.z; let H = F.card.w;
  let dd = shapeD(p);
  // a soft shadow under the card on the dark page
  if (dd > 0.0) {
    let sh = exp(-sq(max(sdRound(p - vec2f(W * 0.5, H * 0.5 + 8.0), vec2f(W * 0.5 - 6.0, H * 0.5), 14.0), 0.0) / 10.0)) * 0.35;
    let a = sh * (1.0 - smoothstep(0.0, 0.8, -dd + 0.8));
    return vec4f(0.0, 0.0, 0.0, a);
  }
  let u = clamp(p.x / W, 0.0, 1.0);
  let c = castAt(u);
  // the surface's slope from the height field
  let e = 0.75;
  let h0 = height(p, c);
  let hx = height(p + vec2f(e, 0.0), c) - h0;
  let hy = height(p + vec2f(0.0, e), c) - h0;
  let n = normalize(vec3f(-hx / e * 2.2, hy / e * 2.2, 1.0));
  let m0 = metal(F.tier.x);
  let m1 = metal(F.tier.y);
  let base = mix(m0.base, m1.base, c.newMetal);
  let hi = mix(m0.hi, m1.hi, c.newMetal);
  let r = reflect(vec3f(0.0, 0.0, -1.0), n);
  let env = studio(r);
  // brushed metal: a broad diffuse sheen in its own colour, the room's light in its highlight colour
  // brushed metal: its colour in the room's light, the room's bright parts in its highlight colour, and a
  // soft streak across the grain that follows the drag
  let g2 = vec2f(dot(p, vec2f(0.94, 0.34)), dot(p, vec2f(-0.34, 0.94)));
  let streak = exp(-sq((g2.x / W - 0.35 - 0.25 * F.light.x) / 0.22)) * (0.85 + 0.15 * noise2(vec2f(g2.x * 0.02, g2.y * 0.9)));
  var col = base * (0.62 + 0.38 * n.z) * (0.8 + 0.2 * env) + hi * (env * 0.18 + vec3f(streak * 0.35));
  // the gentle diagonal tone of the reference: lighter at the top left
  col *= 1.06 - 0.16 * (p.x / W * 0.6 + p.y / H * 0.4);
  // the medal: its own face colour, and the ribbon above it
  let mp = p - vec2f(313.0, 62.0) * vec2f(W / 367.0, H / 185.0);
  let mr = length(mp);
  let medal = mix(m0.medal, m1.medal, c.newMetal);
  let onDisc = (1.0 - smoothstep(22.0, 24.0, mr)) * c.stamp;
  col = mix(col, medal * (0.55 + 0.55 * n.z) + hi * pow(max(env.r - 0.6, 0.0), 1.0) * 0.8, onDisc * 0.85);
  let rq = mp - vec2f(0.0, -36.0);
  let ribbon = (1.0 - smoothstep(-0.5, 0.5, max(abs(rq.x) - 24.0 + rq.y * 0.25, abs(rq.y) - 11.0))) * c.stamp;
  if (ribbon > 0.0 && mr > 21.5) {
    let stripe = fract((rq.x + 24.0) / 16.0);
    let ra = mix(m0.ribA, m1.ribA, c.newMetal);
    let rb = mix(m0.ribB, m1.ribB, c.newMetal);
    var rc = mix(ra, rb, step(0.33, stripe) * step(stripe, 0.66));
    rc *= 0.8 + 0.3 * (1.0 - (rq.y + 11.0) / 22.0);
    col = mix(col, rc, ribbon);
  }
  // molten: the metal's own glow, white-hot where the heat is strongest, liquid highlights on it
  if (c.heat > 0.0) {
    // liquid metal: the glow dimmer where the surface turns from the eye, the room bright in its ripples
    let k = c.heat * (0.7 + 0.1 * sin(p.x * 0.05 + F.view.w * 1.3) * cos(p.y * 0.07 - F.view.w));
    let molten = glow(k) * (0.45 + 0.55 * n.z) + pow(env, vec3f(1.5)) * 0.55 * lin(vec3f(1.0, 0.85, 0.7));
    col = mix(col, molten, smoothstep(0.0, 0.5, c.heat));
  }
  let a = 1.0 - smoothstep(-0.8, 0.0, dd);
  return vec4f(encode(col) * a, a);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU } });
}
