import { Skia } from '@shopify/react-native-skia';

/*
 Whisk Meringue Gesture — the loop's script, the bowl's geometry and the whisk's wires, as pure
 functions of the loop time (frame at t = LOOP equals frame at 0). Every helper is a module-level
 named worklet declared above its callers.

 A pose is [foam, peaks, swirl, agitation, headX, headY, lift, liftPeak, liftX, liftY]:
   foam      0 = clear egg whites … 1 = stiff glossy meringue (bubbles pop cell by cell going down)
   peaks     how tall the whisked ridges stand
   swirl     how far the bowl's contents have turned (radians; only the foam shows it)
   agitation ripples in the liquid around the whisk
   head      the whisk's head in bowl units (bowl centre = 0, rim radius = 1)
   lift      0 = in the bowl … 1 = lifted towards the camera
   liftPeak  the stiff peak the lifted whisk pulls up, at (liftX, liftY)

 autoPlay script (LOOP = 9 s):
   0.0 – 1.2   rest: clear whites, the whisk resting against the right of the bowl.
   1.2 – 5.6   circles: the whisk speeds up to 2.3 rev/s (1.2 – 2.0, ease-in), settles to a steady
               1.7 rev/s, then slows to a stop (5.4 – 6.2); the whites go foamy, soft, then firm
               (ease-out), the contents swirl behind the whisk.
   5.6 – 6.4   peaks: the foam turns glossy and stiff, the swirl's ridges stand up.
   6.4 – 7.2   the whisk lifts (ease-out) and pulls a stiff peak up with it.
   7.2 – 8.4   reset: the whisk settles back to its rest (ease-in-out) while the meringue's bubbles
               pop cell by cell back into fresh clear whites (7.3 – 8.4).
   8.4 – 9.0   rest.
 */

export const LOOP = 9;
export const F_FOAM = 0;
export const F_PEAK = 1;
export const F_SWIRL = 2;
export const F_AGIT = 3;
export const F_HX = 4;
export const F_HY = 5;
export const F_LIFT = 6;
export const F_LPEAK = 7;
export const F_LX = 8;
export const F_LY = 9;

/** The camera's tilt squashes the round bowl vertically by this much. */
export const SQUASH = 0.85;
/** The radius (bowl units) the whisk circles on, and where it rests. */
const CIRCLE_R = 0.44;
const REST_HEAD = [0.5, 0.14];
/** Where the hand holds the handle (bowl units), off the lower right of the bowl. */
export const GRIP = [1.32, 1.12];

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

export function mixPose(a: number[], b: number[], k: number): number[] {
  'worklet';
  const out = new Array<number>(a.length);
  for (let i = 0; i < a.length; i += 1) out[i] = a[i] + (b[i] - a[i]) * k;
  return out;
}

export function restPose(): number[] {
  'worklet';
  return [0, 0, 0, 0, REST_HEAD[0], REST_HEAD[1], 0, 0, 0, 0];
}

/** The meringue held still (reduced motion): stiff, glossy, a peak standing. */
export function stiffPose(): number[] {
  'worklet';
  return [1, 1, 2.2, 0, REST_HEAD[0], REST_HEAD[1], 0, 1, -0.12, -0.05];
}

/** Turns after the 0.8 s spin-up: a steady 1.7 rev/s plus the extra speed of the start dying away. */
function steadyTurns(x: number) {
  'worklet';
  return 0.92 + 1.7 * x + 0.6 * 0.5 * (1 - Math.exp(-x / 0.5));
}

/** Whisk turns (revolutions) since it started circling at 1.2 s. */
export function turnsAt(t: number) {
  'worklet';
  if (t < 1.2) return 0;
  if (t < 2.0) {
    const u = (t - 1.2) / 0.8;
    return 0.92 * u * u;
  }
  if (t < 5.4) return steadyTurns(t - 2.0);
  const s0 = 1.7 + 0.6 * Math.exp(-3.4 / 0.5);
  const base = steadyTurns(3.4);
  const x = Math.min(t, 6.2) - 5.4;
  return base + s0 * x - (s0 * x * x) / (2 * 0.8);
}

/** The whisk's circling speed (rev/s) at t, for the ripples. */
export function whiskSpeedAt(t: number) {
  'worklet';
  if (t < 1.2 || t >= 6.2) return 0;
  if (t < 2.0) return (2 * 0.92 * (t - 1.2)) / 0.64;
  if (t < 5.4) return 1.7 + 0.6 * Math.exp(-(t - 2.0) / 0.5);
  return (1.7 + 0.6 * Math.exp(-3.4 / 0.5)) * (1 - (t - 5.4) / 0.8);
}

export function poseAt(t: number): number[] {
  'worklet';
  const p = restPose();
  const ang = turnsAt(t) * Math.PI * 2;
  // foam: foamy → soft → firm while whisking, stiff at the peaks, popping back to whites at the reset
  const reset = easeInOut(seg(t, 7.3, 8.4));
  const u = seg(t, 1.4, 5.6);
  const foam = 0.86 * u * (2 - u) + 0.14 * easeInOut(seg(t, 5.6, 6.4));
  p[F_FOAM] = foam * (1 - reset);
  p[F_PEAK] = (0.35 * easeInOut(seg(t, 3.4, 5.6)) + 0.65 * easeInOut(seg(t, 5.6, 6.4))) * (1 - reset);
  p[F_SWIRL] = t < 8.4 ? 0.62 * ang : 0;
  p[F_AGIT] = Math.min(1, whiskSpeedAt(t) / 2.3);
  // the whisk: from rest onto its circle, round, then lifted, then back to rest
  const cx = CIRCLE_R * Math.cos(ang);
  const cy = CIRCLE_R * Math.sin(ang);
  const enter = easeInOut(seg(t, 1.2, 1.55));
  let hx = REST_HEAD[0] + (cx - REST_HEAD[0]) * enter;
  let hy = REST_HEAD[1] + (cy - REST_HEAD[1]) * enter;
  const endAng = turnsAt(6.2) * Math.PI * 2;
  const ex = CIRCLE_R * Math.cos(endAng);
  const ey = CIRCLE_R * Math.sin(endAng);
  const lift = easeOut(seg(t, 6.4, 7.2));
  const back = easeInOut(seg(t, 7.2, 8.4));
  if (t >= 6.2) {
    hx = ex + (REST_HEAD[0] - ex) * back;
    hy = ey - 0.08 * lift + (REST_HEAD[1] - ey + 0.08) * back;
  }
  p[F_HX] = hx;
  p[F_HY] = hy;
  p[F_LIFT] = lift * (1 - back);
  p[F_LPEAK] = easeOut(seg(t, 6.45, 7.05)) * (1 - reset);
  p[F_LX] = ex;
  p[F_LY] = ey;
  return p;
}

/** Stage of the meringue for a foam amount: index into STAGES. */
export const STAGES = ['Clear whites', 'Foamy', 'Soft peaks', 'Firm peaks', 'Stiff peaks'];

export function stageOf(foam: number) {
  'worklet';
  if (foam < 0.06) return 0;
  if (foam < 0.36) return 1;
  if (foam < 0.66) return 2;
  if (foam < 0.92) return 3;
  return 4;
}

/** The screen's geometry. */
export type BowlGeo = { w: number; h: number; unit: number; cx: number; cy: number; R: number };

export function makeGeo(w: number, h: number, top: number, bottom: number): BowlGeo {
  'worklet';
  const unit = Math.max(0.74, Math.min(1.15, Math.min(w / 393, h / 852)));
  const R = Math.min(w * 0.43, 172 * unit);
  // the bowl sits between the header and the progress card, a little above their middle
  const cy = top + (bottom - top) * 0.47;
  return { w, h, unit, cx: w / 2, cy, R };
}

/** A point in bowl units → screen. */
export function toScreen(g: BowlGeo, x: number, y: number): [number, number] {
  'worklet';
  return [g.cx + x * g.R, g.cy + y * g.R * SQUASH];
}

/**
 The whisk seen from above: six wire loops that share the tip and gather into the ferrule, then
 the handle up to the hand. `lift` brings it towards the camera (bigger). Returns
 [wires, handle] paths in screen points.
 */
export function whiskPaths(g: BowlGeo, hx: number, hy: number, lift: number, dx: number, dy: number) {
  'worklet';
  const [x0, y0] = toScreen(g, hx, hy);
  const [gx, gy] = toScreen(g, GRIP[0], GRIP[1]);
  let ax = gx - x0;
  let ay = gy - y0;
  const len = Math.max(1, Math.hypot(ax, ay));
  ax /= len;
  ay /= len;
  const s = g.unit * (1 + 0.2 * lift);
  const L = 124 * s;
  const W = 40 * s;
  // the head's tip sits a little beyond the point that touches the bowl
  const tx = x0 - ax * L * 0.32 + dx;
  const ty = y0 - ay * L * 0.32 + dy;
  const nx = -ay;
  const ny = ax;
  const wires = Skia.PathBuilder.Make();
  for (let i = 0; i < 6; i += 1) {
    const phi = (i / 6) * Math.PI + 0.26;
    const lateral = W * Math.cos(phi);
    for (let k = 0; k <= 24; k += 1) {
      const sArc = (k / 24) * Math.PI * 2;
      // teardrop loop: wide near the tip, gathered at the ferrule
      const along = (L * (1 - Math.cos(sArc / 2))) / 2;
      const swell = Math.sin(sArc / 2) * Math.pow(1 - along / L, 0.35);
      const side = Math.sin(sArc) >= 0 ? 1 : -1;
      const off = lateral * swell * side;
      const px = tx + ax * along + nx * off;
      const py = ty + ay * along + ny * off;
      if (k === 0) wires.moveTo(px, py);
      else wires.lineTo(px, py);
    }
  }
  const fx = tx + ax * L;
  const fy = ty + ay * L;
  const handle = Skia.PathBuilder.Make()
    .moveTo(fx, fy)
    .lineTo(fx + ax * 190 * s, fy + ay * 190 * s)
    .build();
  return [wires.build(), handle];
}
