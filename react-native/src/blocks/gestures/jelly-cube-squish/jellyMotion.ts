import { type Deform, HALF, REST, RIGHT, clamp } from './jellyMath';

/*
 Jelly Cube Squish — the autoPlay timeline (LOOP = 8 s). Every wobble is a damped sine windowed
 to reach exactly zero before the loop ends, so t = 8 is t = 0.

   0.0 – 1.2   rest (a faint breathing squash, whole-loop sine).
   1.1 – 1.2   a finger appears on the front face; 1.2 – 1.45 it pokes in (dent 30 pt, ease-out,
               squash 7 %); 1.45 it lets go: the dent springs back (e^−7t, 20 rad/s) and the cube
               wobbles (squash e^−4.5t at 15 rad/s, lean e^−3.5t at 11 rad/s), gone by 2.6.
   2.6 – 3.6   flavour strawberry → lime: the new colour fills from the plate up with a wavy,
               gooey front (ease-in-out), a small jiggle; the name rolls (2.6 – 2.9).
   3.9 – 5.4   a finger takes the top front corner (3.9 – 4.0), pulls it up and right 40 pt
               (4.0 – 5.0, ease-in-out), holds; 5.4 it lets go: the stretch springs back
               (e^−3.8t, 12 rad/s) with lean and squash wobbles, settled by 7.4.
   6.8 – 7.8   back to strawberry (same fill), the name rolls back (6.8 – 7.1).
   7.8 – 8.0   rest.
 */

export const LOOP = 8;

export type Flavour = { name: string; rgb: number[] };

export const FLAVOURS: Flavour[] = [
  { name: 'Strawberry', rgb: [0.93, 0.2, 0.3] },
  { name: 'Lime', rgb: [0.55, 0.82, 0.22] },
  { name: 'Mango', rgb: [0.97, 0.62, 0.2] },
];

/** The poke point (front face) and the grab point (top front corner), world. */
export const POKE = { x: 18, y: -HALF, z: HALF * 0.95 };
export const GRAB = { x: HALF * 0.45, y: -HALF * 0.55, z: HALF * 2 };
const PULL = { right: 30, up: 26 };

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** A damped sine after time s ≥ 0, windowed to zero at `span`. */
function ring(s: number, decay: number, w: number, span: number, cos = false) {
  'worklet';
  if (s < 0 || s >= span) return 0;
  const win = Math.pow(1 - s / span, 2);
  return Math.exp(-decay * s) * (cos ? Math.cos(w * s) : Math.sin(w * s)) * win;
}

/** The deformation at loop time t. */
export function autoDeform(t: number): Deform {
  'worklet';
  const TAU = 2 * Math.PI;
  const d: Deform = { ...REST };
  d.sq = 0.008 * Math.sin((TAU * 2 * t) / LOOP);
  // Poke.
  if (t >= 1.2 && t < 2.8) {
    d.px = POKE.x;
    d.py = POKE.y;
    d.pz = POKE.z;
    d.nx = 0;
    d.ny = 1;
    d.nz = 0;
    if (t < 1.45) {
      const e = easeOut(seg(t, 1.2, 1.45));
      d.dent = 30 * e;
      d.sq += 0.07 * e;
      d.ly = 3 * e;
    } else {
      const s = t - 1.45;
      d.dent = 30 * ring(s, 7, 20, 1.0, true);
      d.sq += 0.07 * ring(s, 4.5, 15, 1.15, true);
      d.ly = 3 * ring(s, 3.5, 11, 1.15, true) + 9 * ring(s, 3.5, 11, 1.15);
    }
  }
  // Jiggles when the colour changes.
  d.sq += 0.035 * ring(t - 2.6, 5, 16, 0.9) + 0.03 * ring(t - 6.8, 5, 16, 0.9);
  // Drag-stretch.
  if (t >= 3.9 && t < 7.6) {
    d.px = GRAB.x;
    d.py = GRAB.y;
    d.pz = GRAB.z;
    let k: number;
    let lean = 0;
    if (t < 5.4) {
      // A 4 % dip the other way first (anticipation), then the pull.
      const u = seg(t, 4.0, 5.0);
      k = easeInOut(u) - 0.04 * Math.sin(Math.PI * seg(t, 4.0, 4.25));
      d.sq += -0.04 * easeInOut(u);
    } else {
      const s = t - 5.4;
      k = ring(s, 3.8, 12, 2.0, true);
      lean = ring(s, 3, 10, 2.0);
      d.sq += 0.06 * ring(s, 4, 13, 2.0);
    }
    d.sx = (RIGHT[0] * PULL.right) * k;
    d.sy = (RIGHT[1] * PULL.right) * k;
    d.sz = PULL.up * k;
    d.lx += -14 * lean * RIGHT[0];
    d.ly += -14 * lean * RIGHT[1];
  }
  return d;
}

/** Colour state at loop time t: [from, to, fill 0 … 1, name roll 0 … 1]. */
export function autoColour(t: number) {
  'worklet';
  if (t < 2.6) return [0, 0, 0, 0];
  if (t < 6.8) return [0, 1, easeInOut(seg(t, 2.6, 3.6)), easeInOut(seg(t, 2.6, 2.9))];
  return [1, 0, easeInOut(seg(t, 6.8, 7.8)), easeInOut(seg(t, 6.8, 7.1))];
}

/** The demo finger: [world x, y, z, alpha]. */
export function autoFinger(t: number, d: Deform) {
  'worklet';
  if (t >= 1.1 && t < 1.6) {
    const a = seg(t, 1.1, 1.2) * (1 - seg(t, 1.45, 1.6));
    return [POKE.x, POKE.y + d.dent, POKE.z, a];
  }
  if (t >= 3.9 && t < 5.55) {
    const a = seg(t, 3.9, 4.0) * (1 - seg(t, 5.4, 5.55));
    return [GRAB.x + d.sx, GRAB.y + d.sy, GRAB.z + d.sz, a];
  }
  return [0, 0, 0, 0];
}
