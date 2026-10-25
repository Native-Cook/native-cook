/*
 Roller Blind Sheet — geometry and the loop's script, as pure functions of the loop time (frame at
 t = LOOP equals frame at 0). Every helper is a module-level named worklet declared above its
 callers.

 The blind hangs from a roller across the top of the window. Its hem (a wooden bar) sits at hemY;
 the fabric between the roller and the hem is unrolled, so the roll's radius shrinks as hemY
 grows. A pull cord with a bead hangs from the middle of the hem and swings like a pendulum.

 Detents for the hem: UP (rolled up, just under the roll), HALF (the window's sill), FULL (the
 sheet covers the room's controls).

 autoPlay script (LOOP = 9 s):
   0.0 – 1.2   rest: the blind half down at the sill, the scenes printed on it, the room a little dim.
   1.0 – 1.2   a finger lands on the bead.
   1.2 – 3.2   pull down to FULL (ease-in-out, a 3 % lift first), 14 pt past it.
   3.2 – 3.6   detent bounce (zero at both ends); the finger lets go, the cord swings 10°.
   3.6 – 5.6   hold.
   5.6 – 5.9   tug: the bead 20 pt down (ease-out), let go.
   5.9 – 7.0   the spring roller winds the blind up (ease-out), the morning light returns;
               7.0 – 7.2 the hem knocks the roll (4 pt), the cord swings.
   7.2 – 7.5   a finger takes the bead again.
   7.5 – 8.3   pull down to the sill (ease-in-out), 8 pt past it; 8.3 – 8.6 settle (bounce).
   8.6 – 9.0   rest.
 */

export const LOOP = 9;

export type BlindGeo = {
  w: number;
  h: number;
  unit: number;
  /** The blind's fabric spans x0 … x1; the roller's axis is at rollerY. */
  x0: number;
  x1: number;
  rollerY: number;
  /** The window opening. */
  winX: number;
  winY: number;
  winW: number;
  winH: number;
  /** Hem detents. */
  up: number;
  half: number;
  full: number;
  /** Where the room's controls start (under the window). */
  controlsY: number;
};

export function makeGeo(w: number, h: number, top: number, bottom: number): BlindGeo {
  'worklet';
  const unit = Math.max(0.74, Math.min(1.15, Math.min(w / 393, h / 852)));
  const rollerY = top + 116 * unit;
  const winX = 30;
  const winY = rollerY + 16 * unit;
  const winW = w - 60;
  const winH = Math.min(330 * unit, h * 0.39);
  const up = rollerY + 22 * unit;
  const half = winY + winH + 8 * unit;
  const full = h - bottom - 112 * unit;
  return { w, h, unit, x0: 20, x1: w - 20, rollerY, winX, winY, winW, winH, up, half, full, controlsY: winY + winH + 22 * unit };
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

/** The hem's y at loop time t. */
export function hemAt(g: BlindGeo, t: number) {
  'worklet';
  const over = 14 * g.unit;
  if (t < 1.2) return g.half;
  if (t < 3.2) {
    const u = seg(t, 1.2, 3.2);
    const span = g.full - g.half;
    // a small lift first (the hand takes the weight), then the long pull
    const lift = -0.03 * (g.full - g.up) * Math.sin(Math.PI * seg(u, 0, 0.16));
    return g.half + (span + over) * easeInOut(u) + lift;
  }
  if (t < 3.6) {
    const u = seg(t, 3.2, 3.6);
    return g.full + over * Math.cos(Math.PI * 3 * u) * (1 - u) * (1 - u);
  }
  if (t < 5.6) return g.full;
  const tug = 20 * g.unit;
  if (t < 5.9) return g.full + tug * easeOut(seg(t, 5.6, 5.9));
  if (t < 7.0) return g.up + (g.full + tug - g.up) * (1 - easeOut(seg(t, 5.9, 7.0)));
  if (t < 7.2) return g.up + 4 * g.unit * Math.sin(Math.PI * seg(t, 7.0, 7.2));
  if (t < 7.5) return g.up;
  const small = 8 * g.unit;
  if (t < 8.3) return g.up + (g.half + small - g.up) * easeInOut(seg(t, 7.5, 8.3));
  if (t < 8.6) {
    const u = seg(t, 8.3, 8.6);
    return g.half + small * Math.cos(Math.PI * 3 * u) * (1 - u) * (1 - u);
  }
  return g.half;
}

/** One swing of the cord (degrees): amplitude a, decay tau, starting at t0. */
function swingFrom(t: number, t0: number, a: number, tau: number) {
  'worklet';
  if (t < t0) return 0;
  const x = t - t0;
  return a * Math.exp(-x / tau) * Math.sin(Math.PI * 2 * 1.4 * x);
}

/** The cord's angle from straight down (degrees) at loop time t (held straight while pulled). */
export function cordAt(t: number) {
  'worklet';
  if (t >= 1.2 && t < 3.2) return 0;
  if (t >= 5.6 && t < 5.9) return 0;
  if (t >= 7.5 && t < 8.3) return 0;
  let a = swingFrom(t, 3.2, 10, 0.5) * (1 - seg(t, 5.3, 5.6));
  a += swingFrom(t, 5.9, -8, 0.45);
  a += swingFrom(t, 7.0, 12, 0.35) * (1 - easeInOut(seg(t, 7.2, 7.5)));
  a += swingFrom(t, 8.3, 6, 0.3) * (1 - easeInOut(seg(t, 8.6, 9.0)));
  return a;
}

/** The finger on the bead at loop time t: [alpha, extra pull of the cord (pt)]. */
export function fingerAt(t: number): [number, number] {
  'worklet';
  if (t >= 1.0 && t < 3.4) return [seg(t, 1.0, 1.2) * (1 - seg(t, 3.2, 3.4)), 0];
  if (t >= 5.4 && t < 6.1) return [seg(t, 5.4, 5.6) * (1 - seg(t, 5.9, 6.1)), 0];
  if (t >= 7.2 && t < 8.5) return [seg(t, 7.2, 7.4) * (1 - seg(t, 8.3, 8.5)), 0];
  return [0, 0];
}

/** How much the fabric blocks the window (0 … 1): the room's dimming. */
export function coverOf(g: BlindGeo, hem: number) {
  'worklet';
  return clamp01((hem - g.winY) / g.winH);
}

/** The roll's radius (pt) with the hem at hemY: thick when rolled up, thin when unrolled. */
export function rollRadius(g: BlindGeo, hem: number) {
  'worklet';
  const core = 7.5 * g.unit;
  const thick = 13.5 * g.unit;
  const left = clamp01(1 - (hem - g.up) / (g.full + 40 * g.unit - g.up));
  return Math.sqrt(core * core + (thick * thick - core * core) * left);
}

/** Where the hem may be dragged to (rubber band past UP and FULL). */
export function rubberHem(g: BlindGeo, y: number) {
  'worklet';
  if (y > g.full) {
    const o = y - g.full;
    return g.full + o / (1 + o / (60 * g.unit));
  }
  if (y < g.up) {
    const o = g.up - y;
    return g.up - o / (1 + o / (16 * g.unit));
  }
  return y;
}

/** The detent a release lands on, from where the hem is and how fast it moves (projected 0.2 s). */
export function detentFor(g: BlindGeo, hem: number, v: number) {
  'worklet';
  const aim = hem + v * 0.2;
  const stops = [g.up, g.half, g.full];
  let best = stops[0];
  for (let i = 1; i < stops.length; i += 1) if (Math.abs(stops[i] - aim) < Math.abs(best - aim)) best = stops[i];
  return best;
}
