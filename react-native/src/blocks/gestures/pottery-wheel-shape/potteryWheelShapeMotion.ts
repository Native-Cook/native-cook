/*
 Pottery Wheel Shape — the loop's script and the clay's geometry, as pure functions of the loop
 time (every frame at t = LOOP equals the frame at t = 0). Shared by the component (UI thread)
 and the shader's uniforms. Every helper is a module-level named worklet declared above its
 callers.

 The clay is a surface of revolution. Its profile is a height H and eight radii r0…r7 spaced
 evenly from the foot (r0) to the top (r7), blended with a cubic B-spline (the shader uses the
 same formula), plus `open` (0 = a closed, domed lump; 1 = a vessel with an open top).

 Units: points at the 393 × 852 design size (multiplied by `unit` on other screens), seconds,
 revolutions per second.

 autoPlay script (LOOP = 9 s):
   0.0 – 1.4   rest: a squat lump, a little off centre, turning slowly (0.6 rev/s).
   1.4 – 2.4   centring: both hands press in, the wheel speeds up to 2.2 rev/s, the wobble dies,
               the lump becomes a cone; 2.3 – 2.9 the thumb opens the top.
   2.4 – 4.4   pull up: one finger rides the wall from the foot to the lip, the walls rise to a
               cylinder (ease-in-out), slip flies off the wall.
   4.4 – 5.6   neck in: both hands squeeze the upper third, the belly swells.
   5.6 – 6.2   lip flare: a finger inside the lip turns it out.
   6.2 – 7.2   hold: the finished vase spins.
   7.2 – 8.6   collapse: the walls sag (overshoot down, then back), the top closes, the lump
               re-centres off-axis while the wheel slows to 0.6 rev/s.
   8.6 – 9.0   rest (the same frame as 0).
 */

export const LOOP = 9;
/** Index of the fields in a pose array. */
export const P_H = 0;
export const P_R = 1; // r0 … r7 at 1 … 8
export const P_OPEN = 9;
export const P_WOB = 10;
export const POSE_LEN = 11;

/** Wheel speeds (rev/s) at rest and while throwing. */
export const SLOW = 0.6;
export const FAST = 2.2;
/** pt per cm for the readout. */
export const PT_PER_CM = 13.3;
/** The camera's elevation (16°). The shader uses the same numbers. */
export const SIN_E = 0.2756;
export const COS_E = 0.9613;

const LUMP = [104, 80, 82, 81, 78, 73, 66, 58, 52, 0, 3.5];
const CONE = [130, 64, 64, 62, 58, 53, 47, 41, 37, 0, 0];
const CYL = [226, 56, 55, 52, 49, 46, 44, 42, 41, 1, 0];
const NECK = [236, 58, 66, 71, 64, 47, 31, 27, 30, 1, 0];
const FLARE = [240, 58, 66, 71, 64, 47, 31, 31, 44, 1, 0];

/** The resting lump (the poster), and the pose the reduced-motion screen shows. */
export function restPose(): number[] {
  'worklet';
  return LUMP.slice();
}

export function vasePose(): number[] {
  'worklet';
  return FLARE.slice();
}

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** a + (b − a) · k, field by field. */
export function mixPose(a: number[], b: number[], k: number): number[] {
  'worklet';
  const out = new Array<number>(a.length);
  for (let i = 0; i < a.length; i += 1) out[i] = a[i] + (b[i] - a[i]) * k;
  return out;
}

/** The cubic B-spline kernel, as in the shader. */
function bspline(d: number) {
  'worklet';
  const x = Math.abs(d);
  if (x >= 2) return 0;
  if (x >= 1) return ((2 - x) * (2 - x) * (2 - x)) / 6;
  return (4 - 6 * x * x + 3 * x * x * x) / 6;
}

/** The clay's radius (pt, before `unit`) at height y above the wheel head. */
export function radiusAt(p: number[], y: number) {
  'worklet';
  const H = p[P_H];
  const u = clamp01(y / H) * 7;
  let sum = 0;
  let wsum = 0;
  for (let i = 0; i < 8; i += 1) {
    const w = bspline(u - i);
    sum += w * p[P_R + i];
    wsum += w;
  }
  let r = sum / Math.max(wsum, 1e-3);
  const cap = Math.sqrt(clamp01((H - y) / (0.42 * H)));
  r *= cap + (1 - cap) * p[P_OPEN];
  return r + 5 * Math.exp(-Math.max(y, 0) / 5);
}

/** The widest diameter of the profile (pt). */
export function widest(p: number[]) {
  'worklet';
  let m = 0;
  for (let k = 0; k <= 16; k += 1) m = Math.max(m, radiusAt(p, (p[P_H] * k) / 16));
  return 2 * m;
}

/** The clay's pose at loop time t. */
export function poseAt(t: number): number[] {
  'worklet';
  if (t < 1.4) return LUMP.slice();
  if (t < 2.4) {
    const p = mixPose(LUMP, CONE, easeInOut(seg(t, 1.4, 2.4)));
    return p;
  }
  if (t < 4.4) {
    const p = mixPose(CONE, CYL, easeInOut(seg(t, 2.4, 4.4)));
    p[P_OPEN] = easeInOut(seg(t, 2.3, 2.9));
    return p;
  }
  if (t < 5.6) return mixPose(CYL, NECK, easeInOut(seg(t, 4.4, 5.6)));
  if (t < 7.2) return mixPose(NECK, FLARE, easeInOut(seg(t, 5.6, 6.2)));
  if (t < 8.6) {
    const u = seg(t, 7.2, 8.6);
    // The walls sag past the lump and come back: an overshoot that is zero at both ends.
    const k = easeInOut(u) + 0.22 * Math.sin(Math.PI * 2 * u) * (1 - u) * (u < 0.5 ? 1 : 0.6);
    const p = mixPose(FLARE, LUMP, k);
    p[P_OPEN] = 1 - easeInOut(seg(t, 7.3, 8.1));
    p[P_WOB] = LUMP[P_WOB] * easeOut(seg(t, 7.8, 8.6));
    return p;
  }
  return LUMP.slice();
}

/** Integral of the throwing ramp (0 at rest speed, 1 at full speed), in seconds. */
function rampArea(t: number) {
  'worklet';
  // ∫ smoothstep = u³ − u⁴/2 over each ramp.
  let a = 0;
  const up = seg(t, 1.4, 2.4);
  a += 1.0 * (up * up * up - (up * up * up * up) / 2);
  a += Math.max(0, Math.min(t, 7.2) - 2.4);
  const dn = seg(t, 7.2, 8.6);
  a += 1.4 * (dn - (dn * dn * dn - (dn * dn * dn * dn) / 2));
  return a;
}

/** The wheel's speed (rev/s) at loop time t. */
export function speedAt(t: number) {
  'worklet';
  const up = seg(t, 1.4, 2.4);
  const dn = seg(t, 7.2, 8.6);
  const s = up * up * (3 - 2 * up) - dn * dn * (3 - 2 * dn);
  return SLOW + (FAST - SLOW) * s;
}

/** The wheel's angle (radians) at loop time t: exactly 15 whole turns per loop. */
export function spinAt(t: number) {
  'worklet';
  const revs = SLOW * t + (FAST - SLOW) * rampArea(t);
  // SLOW · 9 + 1.6 · (0.5 + 4.8 + 0.7) = 15 turns: the seam is exact.
  return revs * Math.PI * 2;
}

/**
 Where the hands touch the clay at loop time t, as [height, side, alpha, press] for each of two
 fingers: height in pt above the wheel head, side −1 (left) or 1 (right), press = how far the pad
 sits inside the wall's outline (pt; negative = inside the vessel).
 */
export function fingersAt(t: number, p: number[]): number[] {
  'worklet';
  const H = p[P_H];
  const out = [0, 1, 0, 0, 0, -1, 0, 0];
  if (t >= 1.3 && t < 2.55) {
    const a = seg(t, 1.3, 1.5) * (1 - seg(t, 2.35, 2.55));
    const press = 4 + 3 * Math.sin(seg(t, 1.4, 2.4) * Math.PI);
    out[0] = H * 0.42;
    out[2] = a;
    out[3] = press;
    out[4] = H * 0.42;
    out[6] = a;
    out[7] = press;
  } else if (t >= 2.45 && t < 4.6) {
    out[0] = H * (0.08 + 0.86 * easeInOut(seg(t, 2.5, 4.4)));
    out[2] = seg(t, 2.45, 2.65) * (1 - seg(t, 4.4, 4.6));
    out[3] = 5;
  } else if (t >= 4.4 && t < 5.75) {
    const a = seg(t, 4.4, 4.6) * (1 - seg(t, 5.5, 5.75));
    out[0] = H * 0.74;
    out[2] = a;
    out[3] = 6;
    out[4] = H * 0.74;
    out[6] = a;
    out[7] = 6;
  } else if (t >= 5.5 && t < 6.45) {
    out[0] = H - 10;
    out[2] = seg(t, 5.5, 5.7) * (1 - seg(t, 6.2, 6.45));
    // inside the lip: the pad sits within the wall, pushing out
    out[3] = 30;
  }
  return out;
}

/** How hard slip flies off the wall (0 … 1) at loop time t, and from which height (share of H). */
export function sprayAt(t: number): [number, number] {
  'worklet';
  if (t >= 1.5 && t < 2.4) return [0.35, 0.42];
  if (t >= 2.6 && t < 4.4) return [1, 0.08 + 0.86 * easeInOut(seg(t, 2.5, 4.4))];
  if (t >= 4.6 && t < 5.5) return [0.55, 0.74];
  if (t >= 5.7 && t < 6.2) return [0.5, 0.97];
  return [0, 0.5];
}

/** The phase label shown at loop time t (index into PHASES). */
export const PHASES = ['Wedged and ready', 'Centring the clay', 'Pulling the walls up', 'Collaring the neck', 'Flaring the lip', 'Looking good', 'Back to a ball'];

export function phaseAt(t: number) {
  'worklet';
  if (t < 1.4) return 0;
  if (t < 2.4) return 1;
  if (t < 4.4) return 2;
  if (t < 5.6) return 3;
  if (t < 6.2) return 4;
  if (t < 7.2) return 5;
  if (t < 8.6) return 6;
  return 0;
}

/** Slip drops: SPRAY_N drops on a SPRAY_PERIOD cycle (whole cycles per loop: 9 / 0.6 = 15). */
export const SPRAY_N = 38;
export const SPRAY_PERIOD = 0.6;

/** The screen's geometry: the wheel head's centre and the scale of the hero. */
export type PotteryGeo = { w: number; h: number; unit: number; cx: number; baseY: number };

export function makeGeo(w: number, h: number): PotteryGeo {
  'worklet';
  const unit = Math.max(0.74, Math.min(1.15, Math.min(w / 393, h / 852)));
  return { w, h, unit, cx: w / 2, baseY: h * 0.69 };
}

/** A finger (height, side, alpha, press) from fingersAt → screen (x, y, alpha, radius). */
export function fingerOnScreen(g: PotteryGeo, p: number[], f: number[], o: number, wob: number): number[] {
  'worklet';
  const r = radiusAt(p, f[o]) * g.unit;
  const pad = 15 * g.unit;
  return [g.cx + wob + f[o + 1] * (r + pad - f[o + 3] * g.unit), g.baseY - f[o] * g.unit * COS_E, f[o + 2], pad];
}

/** The shader's uniforms for a pose. Fingers are screen pads [x, y, alpha, radius] × 2. */
export function uniformsOf(g: PotteryGeo, p: number[], spin: number, speed: number, wob: number, pads: number[]) {
  'worklet';
  const u = g.unit;
  return {
    size: [g.w, g.h],
    base: [g.cx, g.baseY],
    unit: u,
    profA: [p[P_R] * u, p[P_R + 1] * u, p[P_R + 2] * u, p[P_R + 3] * u],
    profB: [p[P_R + 4] * u, p[P_R + 5] * u, p[P_R + 6] * u, p[P_R + 7] * u],
    shape: [p[P_H] * u, p[P_OPEN], wob, speed],
    spin: spin % (Math.PI * 2),
    fingerA: [pads[0], pads[1], pads[2], pads[3]],
    fingerB: [pads[4], pads[5], pads[6], pads[7]],
  };
}

/** The wobble's x offset (pt) for a pose at a wheel angle. */
export function wobbleX(p: number[], spin: number, unit: number) {
  'worklet';
  return p[P_WOB] * unit * Math.sin(spin + 0.6);
}

/** Limits of a shape made by hand (pt): past them the clay resists (rubber band). */
const R_MIN = 16;
const R_MAX = 104;
const H_MIN = 70;
const H_MAX = 268;

function rubber(v: number, lo: number, hi: number) {
  'worklet';
  if (v > hi) return hi + (v - hi) / (1 + (v - hi) / 30);
  if (v < lo) return lo - (lo - v) / (1 + (lo - v) / 30);
  return v;
}

/**
 A finger working the wall: a drag of (dx, dy) pt at screen (x, y). Pressing towards the axis
 squeezes the radii near that height (Gaussian influence over the control points) and pushes the
 clay up (volume is kept roughly constant); dragging up along the wall pulls it taller and
 thinner, dragging down squats it. Returns a new pose.
 */
export function shapeByFinger(g: PotteryGeo, p: number[], x: number, y: number, dx: number, dy: number): number[] {
  'worklet';
  const out = p.slice();
  const u = g.unit;
  const H = p[P_H];
  const h = Math.min(H, Math.max(0, (g.baseY - y) / (u * COS_E)));
  const side = x >= g.cx ? 1 : -1;
  const reach = radiusAt(p, h) * u;
  const nearWall = Math.abs(Math.abs(x - g.cx) - reach) < 70 * u;
  if (!nearWall) return out;
  const inward = (-side * dx) / u;
  const k = (h / H) * 7;
  let squeezed = 0;
  for (let i = 0; i < 8; i += 1) {
    const w = Math.exp(-((i - k) * (i - k)) / (2 * 1.25 * 1.25));
    const r = out[P_R + i] - inward * w * 0.85;
    out[P_R + i] = rubber(r, R_MIN, R_MAX);
    squeezed += inward * w;
  }
  // Squeezing pushes clay up, widening lets it settle; dragging up pulls it taller.
  const up = -dy / u;
  const Hn = rubber(H + up * 0.9 + squeezed * 0.35, H_MIN, H_MAX);
  const keep = Math.sqrt(H / Hn);
  for (let i = 0; i < 8; i += 1) out[P_R + i] = rubber(out[P_R + i] * keep, R_MIN, R_MAX);
  out[P_H] = Hn;
  // A vessel once it is tall enough to have been opened.
  out[P_OPEN] = Math.min(1, Math.max(out[P_OPEN], (Hn - 120) / 40));
  out[P_WOB] = out[P_WOB] * 0.96;
  return out;
}

/** Brings a hand-made pose back inside the limits (after the finger lifts). */
export function settlePose(p: number[], k: number): number[] {
  'worklet';
  const out = p.slice();
  out[P_H] += (Math.min(H_MAX, Math.max(H_MIN, p[P_H])) - p[P_H]) * k;
  for (let i = 0; i < 8; i += 1) {
    const r = p[P_R + i];
    out[P_R + i] += (Math.min(R_MAX, Math.max(R_MIN, r)) - r) * k;
  }
  return out;
}
