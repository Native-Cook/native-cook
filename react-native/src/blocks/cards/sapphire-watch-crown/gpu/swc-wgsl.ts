// Sapphire Watch Crown — the watch on WebGPU, authored as TypeGPU-resolved WGSL.
//
// TypeGPU owns the data layout (the frame block is a `d.struct` schema), and `tgpu.resolve` injects
// its WGSL into the template. One full-screen pass: the watch sphere-traced as a whole (a round case
// with a rolled bezel, four lugs, a knurled crown, a leather strap curving away), lying at an angle
// on its disc and turned over about its 12–6 axis by the flip. Its faces are drawn in their own
// plane: the dial (mother-of-pearl, a date ring, applied indices, a moon-phase window, lance hands
// that follow the time) under a domed crystal; the back, a sapphire window on the movement (a plate
// with perlage, bridges with Geneva stripes, five gears turning, jewels, a balance wheel swinging four
// times a second, the rotor going round). The case is metal from a small studio: dark, rose gold,
// yellow gold or white gold, blended while it changes.

import { d, tgpu } from 'typegpu';

export const FrameU = d.struct({
  view: d.vec4f, // width, height (pt), pixel ratio, time (s)
  watch: d.vec4f, // the case's centre x, y (pt), its radius (pt), the flip 0…1
  hands: d.vec4f, // hours 0…12 (fractional), the crown's turn (rad), the metal from, to
  metal: d.vec4f, // the metal blend 0…1, the loop's time for the movement (s), -, -
});

const TEMPLATE = /* wgsl */ `
@group(0) @binding(0) var<uniform> F: FrameU;

const PI = 3.14159265;
const TAU = 6.2831853;
fn sq(v: f32) -> f32 { return v * v; }
fn lin(c: vec3f) -> vec3f { return pow(c, vec3f(2.2)); }
fn rot(a: f32) -> mat2x2f { let c = cos(a); let s = sin(a); return mat2x2f(c, s, -s, c); }
fn hash2(p: vec2f) -> f32 { return fract(sin(dot(p, vec2f(127.1, 311.7))) * 43758.5453); }
fn sdBox2(p: vec2f, b: vec2f) -> f32 { let q = abs(p) - b; return length(max(q, vec2f(0.0))) + min(max(q.x, q.y), 0.0); }
fn sdSeg(p: vec2f, a: vec2f, b: vec2f) -> f32 { let pa = p - a; let ba = b - a; let h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
fn smin(a: f32, b: f32, k: f32) -> f32 { let h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }

/* ------------------------------------------------------------------ the watch's frame
 world: pt, origin at the case's centre, x right, y up, z to the eye. local: the watch's own, 12 o'clock
 at +y, the dial facing +z. It lies turned on its disc (12 o'clock to the upper left), tipped a little
 towards the lower right so its side shows, and the flip turns it about its 12–6 axis. */

const LAY = 0.72;
const TIP = 0.32;
fn toLocal(v: vec3f) -> vec3f {
  var q = v;
  let xy = rot(-LAY) * q.xy; q = vec3f(xy, q.z);
  // tip about the watch's 12–6 axis a little, so the case's side shows at 3 o'clock
  let xz = rot(TIP + F.watch.w * PI) * q.xz; q = vec3f(xz.x, q.y, xz.y);
  let yz = rot(0.22) * q.yz; q = vec3f(q.x, yz.x, yz.y);
  return q;
}
fn toWorld(v: vec3f) -> vec3f {
  var q = v;
  let yz = rot(-0.22) * q.yz; q = vec3f(q.x, yz.x, yz.y);
  let xz = rot(-(TIP + F.watch.w * PI)) * q.xz; q = vec3f(xz.x, q.y, xz.y);
  let xy = rot(LAY) * q.xy; q = vec3f(xy, q.z);
  return q;
}

/* ------------------------------------------------------------------ the shape (local, pt) */

const RC = 84.0;
const THICK = 6.0;
// 1 case, 2 crown, 3 strap, 4 crystal/dial face, 5 back
fn sdWatch(p: vec3f) -> vec2f {
  // the case: a flat drum with a rolled edge
  let r = length(p.xy);
  let dc = vec2f(r - (RC - 5.0), abs(p.z) - (THICK - 5.0));
  var dd = min(max(dc.x, dc.y), 0.0) + length(max(dc, vec2f(0.0))) - 5.0;
  var m = 1.0;
  // the lugs: four horns at 11, 1, 5 and 7, joined smoothly
  for (var i = 0; i < 4; i++) {
    let sx = select(-1.0, 1.0, i % 2 == 1);
    let sy = select(-1.0, 1.0, i / 2 == 1);
    let lp = p - vec3f(sx * 30.0, sy * 82.0, -1.0);
    let lug = length(max(abs(lp) - vec3f(4.0, 12.0, 3.0), vec3f(0.0))) - 2.5;
    dd = smin(dd, lug, 4.0);
  }
  // the crown at 3 o'clock, knurled
  let cp = p - vec3f(RC + 4.0, 0.0, 0.0);
  let ang = atan2(cp.z, cp.y) + F.hands.y;
  let knurl = 0.45 * sin(ang * 18.0);
  let crown = max(length(cp.yz) - 7.0 - knurl, abs(cp.x) - 5.0);
  if (crown < dd) { dd = crown; m = 2.0; }
  // the strap: two bands from the lugs, curving away behind
  let ay = abs(p.y);
  if (ay > 70.0) {
    let bend = sq(max(ay - 88.0, 0.0)) * 0.012;
    let sp = vec3f(p.x, ay - 150.0, p.z + 2.0 + bend);
    let strap = length(max(abs(sp) - vec3f(25.0, 62.0, 2.2), vec3f(0.0))) - 1.6;
    if (strap < dd) { dd = strap; m = 3.0; }
  }
  return vec2f(dd, m);
}
fn watchN(p: vec3f) -> vec3f {
  let e = 0.12;
  let k1 = vec3f(1.0, -1.0, -1.0); let k2 = vec3f(-1.0, -1.0, 1.0); let k3 = vec3f(-1.0, 1.0, -1.0); let k4 = vec3f(1.0, 1.0, 1.0);
  return normalize(k1 * sdWatch(p + k1 * e).x + k2 * sdWatch(p + k2 * e).x + k3 * sdWatch(p + k3 * e).x + k4 * sdWatch(p + k4 * e).x);
}

/* ------------------------------------------------------------------ light and metals */

fn studio(r: vec3f) -> vec3f {
  // a product studio on black: a big soft box above left, a strip to the right, black elsewhere
  var c = lin(vec3f(0.03));
  c += vec3f(1.0, 0.98, 0.95) * 3.0 * smoothstep(0.75, 0.95, dot(r, normalize(vec3f(-0.55, 0.65, 0.52))));
  c += vec3f(1.0) * 1.2 * smoothstep(0.9, 0.98, dot(r, normalize(vec3f(0.85, -0.1, 0.5))));
  c += vec3f(0.6, 0.62, 0.66) * 0.35 * smoothstep(-0.2, 0.8, r.y) * smoothstep(-0.3, 0.6, r.z);
  return c;
}
fn metalColor(i: f32) -> vec3f {
  if (i < 0.5) { return lin(vec3f(0.2, 0.2, 0.22)); }
  if (i < 1.5) { return lin(vec3f(0.93, 0.7, 0.58)); }
  if (i < 2.5) { return lin(vec3f(0.98, 0.79, 0.42)); }
  return lin(vec3f(0.9, 0.91, 0.93));
}
fn caseMetal() -> vec3f { return mix(metalColor(F.hands.z), metalColor(F.hands.w), F.metal.x); }

/* ------------------------------------------------------------------ the dial (local xy, pt) */

fn hand(q: vec2f, ang: f32, len: f32, w: f32) -> f32 {
  // a lance: widest a third of the way out, a point at the tip, a short tail
  // the dial's y points to 12 and its angles run clockwise, so turning the hand onto +y is a turn by +ang
  let p = rot(ang) * q;
  let t = clamp(p.y / len, -0.18, 1.0);
  let half = w * select(smoothstep(-0.18, 0.0, t) * 0.6, (1.0 - t) * 1.3 * smoothstep(0.0, 0.3, t) + 0.25 * (1.0 - t), t > 0.0);
  return max(abs(p.x) - half, max(p.y - len, -p.y - len * 0.18));
}
// a seven-segment figure 3.2 × 5.2 pt centred on p: segments a…g are bits 0…6 of a digit's mask
fn seg7(p: vec2f, n: u32) -> f32 {
  let masks = array<u32, 10>(0x3Fu, 0x06u, 0x5Bu, 0x4Fu, 0x66u, 0x6Du, 0x7Du, 0x07u, 0x7Fu, 0x6Fu);
  let m = masks[n];
  let w = 1.3;
  let h = 2.3;
  let g = 0.35;
  var d = 1e9;
  if ((m & 1u) != 0u) { d = min(d, sdSeg(p, vec2f(-w + g, h), vec2f(w - g, h))); }
  if ((m & 2u) != 0u) { d = min(d, sdSeg(p, vec2f(w, h - g), vec2f(w, g))); }
  if ((m & 4u) != 0u) { d = min(d, sdSeg(p, vec2f(w, -g), vec2f(w, -h + g))); }
  if ((m & 8u) != 0u) { d = min(d, sdSeg(p, vec2f(-w + g, -h), vec2f(w - g, -h))); }
  if ((m & 16u) != 0u) { d = min(d, sdSeg(p, vec2f(-w, -g), vec2f(-w, -h + g))); }
  if ((m & 32u) != 0u) { d = min(d, sdSeg(p, vec2f(-w, h - g), vec2f(-w, g))); }
  if ((m & 64u) != 0u) { d = min(d, sdSeg(p, vec2f(-w + g, 0.0), vec2f(w - g, 0.0))); }
  return d - 0.42;
}
fn dial(q: vec2f) -> vec3f {
  let r = length(q);
  let th = atan2(q.x, q.y); // clockwise from 12
  // mother-of-pearl: white with a faint shifting shimmer
  var c = lin(vec3f(0.94, 0.93, 0.9)) * (0.9 + 0.1 * sin(q.x * 0.12 + sin(q.y * 0.09) * 2.0));
  c = mix(c, c * lin(vec3f(0.98, 0.95, 1.0)), 0.5 + 0.5 * sin(th * 3.0 + r * 0.05));
  // the date ring: 31 ticks, every other one longer, and a thin circle
  let n = 31.0;
  let a = th / TAU * n;
  let f = abs(fract(a + 0.5) - 0.5) / n * TAU * r;
  let tick = (1.0 - smoothstep(0.35, 0.8, f)) * step(60.0, r) * step(r, select(66.0, 69.0, fract(floor(a + 0.5) * 0.5) < 0.25));
  c = mix(c, lin(vec3f(0.18, 0.18, 0.2)), tick * 0.85);
  c = mix(c, lin(vec3f(0.5, 0.42, 0.36)), exp(-sq((r - 58.0) / 0.5)) * 0.8);
  // applied indices: twelve small gold studs
  let ia = th / TAU * 12.0;
  let ip = rot(-floor(ia + 0.5) * TAU / 12.0) * q;
  let stud = length(ip - vec2f(0.0, 50.0)) - 2.3;
  c = mix(c, caseMetal() * 1.2, 1.0 - smoothstep(-0.5, 0.5, stud));
  // the date windows below 12 and the moon at 4–5
  let win = sdBox2(q - vec2f(0.0, 26.0), vec2f(13.0, 4.2));
  c = mix(c, lin(vec3f(0.98)), (1.0 - smoothstep(-0.5, 0.5, win)) * 0.9);
  // the date in it, 14, as two small seven-segment figures
  for (var k = 0; k < 2; k++) {
    let dq = q - vec2f(-2.6 + f32(k) * 5.2, 26.0);
    let digit = seg7(dq, select(4u, 1u, k == 0));
    c = mix(c, lin(vec3f(0.15)), 1.0 - smoothstep(0.15, 0.55, digit));
  }
  c = mix(c, lin(vec3f(0.55, 0.5, 0.45)), exp(-sq(win / 0.5)) * 0.8);
  let mq = q - vec2f(22.0, -26.0);
  let moonWin = length(mq) - 13.0;
  if (moonWin < 0.0) {
    var sky = lin(vec3f(0.08, 0.12, 0.3));
    let st = hash2(floor(mq * 0.8));
    sky += vec3f(1.0) * step(0.93, st) * 0.6;
    let moon = length(mq - vec2f(-3.0, 1.0)) - 6.5;
    sky = mix(sky, lin(vec3f(0.95, 0.85, 0.6)), 1.0 - smoothstep(-0.5, 0.5, moon));
    c = mix(c, sky, 1.0 - smoothstep(-0.6, 0.0, moonWin));
  }
  c = mix(c, caseMetal(), exp(-sq(moonWin / 0.7)) * 0.9);
  // the maker's mark: a small crowned A above the windows
  let mk = q - vec2f(0.0, 38.0);
  let mark = min(min(sdSeg(mk, vec2f(-3.0, -3.0), vec2f(0.0, 3.5)), sdSeg(mk, vec2f(3.0, -3.0), vec2f(0.0, 3.5))), sdSeg(mk, vec2f(-1.6, -0.6), vec2f(1.6, -0.6)));
  c = mix(c, lin(vec3f(0.2, 0.2, 0.25)), 1.0 - smoothstep(0.4, 0.9, mark));
  // the hands, blued steel, with their shadows on the dial
  let hrs = F.hands.x;
  let ha = hrs / 12.0 * TAU;
  let ma = fract(hrs) * TAU;
  let sh = vec2f(1.6, -1.6);
  let shadow = min(hand(q - sh, ha, 34.0, 3.2), hand(q - sh, ma, 50.0, 2.6));
  c *= 1.0 - 0.25 * (1.0 - smoothstep(-1.0, 2.0, shadow));
  let hd = min(hand(q, ha, 34.0, 3.2), hand(q, ma, 50.0, 2.6));
  let blue = lin(vec3f(0.12, 0.2, 0.5)) * (0.7 + 0.6 * smoothstep(-3.0, 0.0, hd));
  c = mix(c, blue, 1.0 - smoothstep(-0.5, 0.5, hd));
  // the pin
  c = mix(c, caseMetal() * 1.1, 1.0 - smoothstep(2.2, 3.0, r));
  return c;
}

/* ------------------------------------------------------------------ the movement (local xy, pt, seen from behind) */

fn gear(q: vec2f, c: vec2f, rr: f32, teeth: f32, ang: f32) -> f32 {
  let p = q - c;
  let th = atan2(p.y, p.x) + ang;
  let toothed = length(p) - rr - 1.3 * smoothstep(-0.35, 0.35, sin(th * teeth));
  // spokes: a gear is cut away between five arms
  let inner = length(p) - rr * 0.72;
  let arms = abs(sin(th * 2.5)) * length(p) - 2.2;
  let cut = max(inner, arms);
  return max(toothed, -max(cut, -(length(p) - rr * 0.25)));
}
fn movement(q0: vec2f) -> vec3f {
  let q = vec2f(-q0.x, q0.y); // seen from behind: mirrored
  let tt = F.metal.y;
  let r = length(q);
  // the main plate: grey with perlage (overlapping circular grains)
  let cell = floor(q / 5.0);
  let fq = fract(q / 5.0) - 0.5;
  var c = lin(vec3f(0.3, 0.31, 0.34)) * (0.8 + 0.2 * smoothstep(0.0, 0.5, length(fq)) + 0.05 * hash2(cell));
  // bridges with Geneva stripes, and their bevels lit
  let br1 = sdBox2(rot(0.5) * (q - vec2f(18.0, 18.0)), vec2f(34.0, 12.0)) - 4.0;
  let br2 = sdBox2(rot(-0.8) * (q - vec2f(-16.0, 24.0)), vec2f(20.0, 9.0)) - 4.0;
  let bridges = min(br1, br2);
  if (bridges < 0.0) {
    let stripe = 0.85 + 0.15 * sin(dot(q, vec2f(0.7, 0.7)) * 0.8);
    c = lin(vec3f(0.82, 0.83, 0.85)) * stripe;
  }
  c += vec3f(1.0) * 0.35 * exp(-sq(bridges / 0.9));
  // the gear train: five wheels, each a whole number of turns per ten seconds
  let g1 = gear(q, vec2f(-6.0, -4.0), 21.0, 48.0, tt * TAU * 0.1);
  let g2 = gear(q, vec2f(27.0, -16.0), 13.0, 30.0, -tt * TAU * 0.2);
  let g3 = gear(q, vec2f(38.0, 14.0), 10.0, 24.0, tt * TAU * 0.5);
  let g4 = gear(q, vec2f(10.0, 38.0), 9.0, 20.0, -tt * TAU * 0.3);
  let g5 = gear(q, vec2f(-34.0, 18.0), 12.0, 28.0, tt * TAU * 0.2);
  let gears = min(min(g1, g2), min(min(g3, g4), g5));
  c = mix(c, lin(vec3f(0.92, 0.74, 0.45)) * (0.75 + 0.25 * sin(r * 0.6)), 1.0 - smoothstep(-0.4, 0.4, gears));
  c += vec3f(1.0, 0.9, 0.7) * 0.3 * exp(-sq(gears / 0.5));
  // jewels at the arbors
  for (var i = 0; i < 5; i++) {
    let jc = array<vec2f, 5>(vec2f(-6.0, -4.0), vec2f(27.0, -16.0), vec2f(38.0, 14.0), vec2f(10.0, 38.0), vec2f(-34.0, 18.0))[i];
    let j = length(q - jc);
    c = mix(c, lin(vec3f(0.75, 0.05, 0.12)) * (0.6 + 0.8 * exp(-sq(j - 0.6))), 1.0 - smoothstep(1.6, 2.2, j));
  }
  // the balance: a brass wheel with three spokes and timing screws round its rim, swinging four
  // times a second over a blued hairspring that breathes with it, a jewel at its staff
  let bc = q - vec2f(-30.0, -36.0);
  let swing = sin(tt * TAU * 4.0) * 3.2;
  let hsr = length(bc);
  let spiral = abs(fract((hsr - atan2(bc.y, bc.x) / TAU * 1.4 + swing * 0.05) / 1.4) - 0.5);
  c = mix(c, lin(vec3f(0.22, 0.32, 0.62)), (1.0 - smoothstep(0.1, 0.2, spiral)) * step(hsr, 13.0) * step(2.5, hsr) * 0.9);
  let bp = rot(swing) * bc;
  let br = length(bp);
  let ba = atan2(bp.y, bp.x);
  let ring = abs(br - 16.0) - 1.5;
  let spoke = abs(fract(ba / TAU * 3.0 + 0.25) - 0.5) * TAU / 3.0 * br;
  let arms = max(spoke - 0.9, br - 16.0);
  let sk = round(ba / (TAU / 10.0)) * (TAU / 10.0);
  let screws = length(bp - 16.6 * vec2f(cos(sk), sin(sk))) - 1.5;
  let bal = min(min(ring, arms), screws);
  let brass = lin(vec3f(0.93, 0.78, 0.48)) * (0.8 + 0.3 * smoothstep(-1.5, 1.5, bp.x - bp.y));
  c = mix(c, brass, 1.0 - smoothstep(-0.4, 0.4, bal));
  c += vec3f(1.0, 0.92, 0.75) * 0.25 * exp(-sq(bal / 0.5));
  c = mix(c, lin(vec3f(0.75, 0.05, 0.12)), 1.0 - smoothstep(1.4, 2.0, br));
  // the rotor: half a disc of gold, going round once in ten seconds, engraved at its rim
  let ra = atan2(q.y, q.x) - tt * TAU * 0.1;
  let rotorA = cos(ra) > 0.0;
  if (rotorA && r > 44.0 && r < 62.0) {
    let eng = 0.9 + 0.1 * sin(r * 2.0);
    c = mix(c, caseMetal() * eng * 1.1, 0.92);
  }
  c += vec3f(1.0) * 0.25 * exp(-sq((r - 44.0) / 0.6)) * select(0.0, 1.0, rotorA);
  return c;
}

struct Px { col: vec3f, a: f32 }

fn watchPx(s: vec2f) -> Px {
  var o: Px;
  o.col = vec3f(0.0);
  o.a = 0.0;
  let w = vec3f(s.x - F.watch.x, F.watch.y - s.y, 0.0) * (RC / F.watch.z);
  if (length(w.xy) > 230.0) { return o; }
  let ro = toLocal(vec3f(w.xy, 300.0));
  let rd = toLocal(vec3f(0.0, 0.0, -1.0));
  var t = 160.0;
  var hit = false;
  var m = 0.0;
  for (var i = 0; i < 90; i++) {
    let h = sdWatch(ro + rd * t);
    if (h.x < 0.04) { hit = true; m = h.y; break; }
    t += h.x * 0.9;
    if (t > 460.0) { break; }
  }
  // a soft shadow of the watch on its disc: the case narrowing as it turns over, and the strap
  if (!hit) {
    let q = rot(-LAY) * (w.xy + vec2f(-10.0, 12.0));
    let wide = RC * max(abs(cos((TIP + F.watch.w * PI))), 0.18) + 4.0;
    let caseS = length(q / vec2f(wide / RC, 1.0)) - RC;
    let strapS = sdBox2(q, vec2f(25.0 * max(abs(cos((TIP + F.watch.w * PI))), 0.3), 200.0));
    let sd = min(caseS, strapS);
    o.col = vec3f(0.0);
    o.a = (1.0 - smoothstep(-8.0, 26.0, sd)) * 0.55;
    return o;
  }
  let p = ro + rd * t;
  let nl = watchN(p);
  let n = toWorld(nl);
  let refl = studio(reflect(vec3f(0.0, 0.0, -1.0), n));
  var c = vec3f(0.0);
  let r = length(p.xy);
  if (m < 1.5 && nl.z > 0.85 && r < RC - 9.0) {
    // the dial under a domed crystal: the crystal's reflection over it, brightest at its rim
    let fr = 0.04 + 0.5 * pow(r / (RC - 9.0), 4.0);
    c = dial(p.xy) * (0.85 + 0.15 * n.z) * (1.0 - fr) + refl * fr * 0.6;
    c += vec3f(1.0) * 0.35 * exp(-sq((r - (RC - 10.0)) / 0.8));
  } else if (m < 1.5 && nl.z < -0.85 && r < RC - 14.0) {
    // the sapphire back on the movement
    let fr = 0.04 + 0.4 * pow(r / (RC - 14.0), 5.0);
    c = movement(p.xy) * (1.0 - fr) + refl * fr * 0.7;
    c += vec3f(1.0) * 0.3 * exp(-sq((r - (RC - 15.0)) / 0.8));
  } else if (m > 2.5) {
    // leather: dark, a faint grain, a soft sheen
    let grain = hash2(floor(p.xy * 1.4)) * 0.06;
    c = lin(vec3f(0.07, 0.07, 0.075)) * (0.6 + 0.4 * max(n.z, 0.0) + grain) + refl * 0.06;
    // stitches along its edges
    let st = abs(abs(p.x) - 21.5);
    c += vec3f(0.15) * (1.0 - smoothstep(0.3, 0.7, st)) * step(0.5, fract(abs(p.y) * 0.25));
  } else {
    // polished metal: the case, its bezel, lugs and crown
    let base = caseMetal();
    c = base * refl * 0.95 + base * 0.06;
    if (nl.z < -0.85) { c *= 0.85; }
  }
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

@fragment fn watchFs(v: VOut) -> @location(0) vec4f {
  let px = watchPx(v.pos.xy / F.view.z);
  if (px.a <= 0.0001) { return vec4f(0.0); }
  if (px.a < 0.999) { return vec4f(px.col * px.a, px.a); }
  return vec4f(encode(px.col), 1.0);
}
`;

export function resolveShaders() {
  return tgpu.resolve({ template: TEMPLATE, externals: { FrameU } });
}
