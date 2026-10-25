/*
 Lighthouse Beam Welcome — timing and the beam's geometry. Every frame of the loop is a pure
 function of the loop time t, so t = LOOP draws the same frame as t = 0.

 The lantern turns about a vertical axis: azimuth φ = 0 points right (across the screen), φ = π/2
 towards the viewer, π left, 3π/2 away. Seen from a little above, the beam's direction on the
 screen is (cos φ, E·sin φ + TILT): it is longest pointing sideways, short and wide (a flare)
 pointing at the viewer, short and faint pointing away.

 autoPlay timeline (LOOP = 10 s):
   0.0 – 1.8   rest: the beam lies across page 1's text (φ = 0), fog drifting, sea moving.
   1.8 – 10.0  two whole turns (φ 0 → 4π). The lantern speeds up over 0.7 s, turns at a constant
               rate (3.75 s a turn, linear: a real rotation), and slows over 0.7 s into the rest.
               Text is revealed only where the cone passes and glows on for 0.8 s after it.
   3.73 – 4.33 page 1 → 2 (ease-in-out cubic), centred on φ = π, while the beam is behind the tower.
   7.48 – 8.08 page 2 → 1, centred on φ = 3π; the last sweep reveals page 1 and comes to rest on it.
 */

export const LOOP = 10;
/** Viewer elevation (how much "towards the viewer" drops on screen) and the beam's downward tilt. */
export const E = 0.3;
export const TILT = 0.12;
const TURN_START = 1.8;
const TURN_END = 10;
const RAMP = 0.7;
const TURNS = 2;
/** Times (s) the trail looks back, for the 0.8 s afterglow. */
export const TRAIL = [0.1, 0.22, 0.36, 0.55, 0.8];
export const PAGES = 3;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** 0 … 1 over [a, b] with a constant speed in the middle and smooth ramps of `ramp` s at both ends. */
export function trapezoid(t: number, a: number, b: number, ramp: number) {
  'worklet';
  const span = b - a;
  const vmax = 1 / (span - ramp);
  const x = t - a;
  if (x <= 0) return 0;
  if (x >= span) return 1;
  if (x < ramp) {
    const u = x / ramp;
    return vmax * ramp * (u * u * u - (u * u * u * u) / 2);
  }
  if (x < span - ramp) return vmax * (ramp * 0.5 + (x - ramp));
  const u = (span - x) / ramp;
  return 1 - vmax * ramp * (u * u * u - (u * u * u * u) / 2);
}

/** The lantern's azimuth at loop time t, unwrapped (0 … 4π). */
export function phiAt(t: number) {
  'worklet';
  return 2 * Math.PI * TURNS * trapezoid(t, TURN_START, TURN_END, RAMP);
}

/** φ at t − back, continuous across the loop's seam (one loop earlier is 4π less). */
export function phiBefore(t: number, back: number) {
  'worklet';
  const s = t - back;
  return s >= 0 ? phiAt(s) : phiAt(s + LOOP) - 2 * Math.PI * TURNS;
}

// The page changes sit where the beam points away from the text (φ = π and 3π), found once.
function timeOfPhi(target: number) {
  let lo = TURN_START;
  let hi = TURN_END;
  for (let i = 0; i < 40; i += 1) {
    const mid = (lo + hi) / 2;
    if (phiAt(mid) < target) lo = mid;
    else hi = mid;
  }
  return (lo + hi) / 2;
}
export const PAGE_OUT = timeOfPhi(Math.PI);
export const PAGE_BACK = timeOfPhi(3 * Math.PI);
const SLIDE = 0.6;

/** The page shown, as a float (0 = page 1, 1 = page 2). */
export function pageAt(t: number, pageOut: number, pageBack: number) {
  'worklet';
  const out = easeInOutCubic(seg(t, pageOut - SLIDE / 2, pageOut + SLIDE / 2));
  const back = easeInOutCubic(seg(t, pageBack - SLIDE / 2, pageBack + SLIDE / 2));
  return out - back;
}

/** The lantern azimuth that points the beam at a screen point (relative to the lamp), nearest `near`. */
export function aimAt(dx: number, dy: number, near: number) {
  'worklet';
  // Solve (dx, dy)·m = (cos φ, E sin φ + TILT) for m > 0, then read φ.
  const a = dx * dx + (dy * dy) / (E * E);
  const b = (-2 * dy * TILT) / (E * E);
  const c = (TILT * TILT) / (E * E) - 1;
  const disc = Math.max(0, b * b - 4 * a * c);
  const m = a > 1e-6 ? (-b + Math.sqrt(disc)) / (2 * a) : 0;
  const cosP = dx * m;
  const sinP = Math.max(-1, Math.min(1, (dy * m - TILT) / E));
  const phi = Math.atan2(sinP, cosP);
  const turns = Math.round((near - phi) / (2 * Math.PI));
  return phi + turns * 2 * Math.PI;
}

/** How much the beam faces the viewer (0 … 1): drives the lamp's flare. */
export function facing(phi: number) {
  'worklet';
  const s = Math.sin(phi);
  return s > 0 ? Math.pow(s, 4) : 0;
}

export type Geo = {
  w: number;
  h: number;
  u: number;
  horizon: number;
  lampX: number;
  lampY: number;
  /** The tower: base y, half widths at base and gallery, gallery y. */
  baseY: number;
  galleryY: number;
  /** Text block: left, top, width, height (the content image covers [contentY, contentY + contentH]). */
  textX: number;
  textY: number;
  textW: number;
  contentY: number;
  contentH: number;
};

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const u = Math.min(w / 393, (h - top - bottom) / 759);
  const horizon = Math.round(top + (h - top - bottom) * 0.6);
  const lampX = Math.round(w * 0.16);
  const lampY = Math.round(top + (h - top - bottom) * 0.27);
  const galleryY = lampY + 16 * u;
  const baseY = horizon + 30 * u;
  const textX = Math.round(Math.max(lampX + 96 * u, w * 0.4));
  const textW = w - textX - 24;
  // The text block's middle sits on the beam's axis at rest, a little right of its left edge.
  const mid = lampY + TILT * (textX + textW * 0.3 - lampX);
  const textY = Math.round(mid - 80 * u);
  return {
    w,
    h,
    u,
    horizon,
    lampX,
    lampY,
    baseY,
    galleryY,
    textX,
    textY,
    textW,
    contentY: textY - 40,
    contentH: Math.round(180 * u) + 80,
  };
}
