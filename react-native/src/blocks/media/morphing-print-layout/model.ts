/*
 The layouts of Morphing Print Layout and how one becomes the next (a port of
 MorphingPrintLayoutModel.swift; the numbers are the same). All functions are worklets.

 Five film formats, in design points (scaled by the screen width / 393, at most 430 / 393). A frame is
 a white card; its wells are square photo windows placed from the frame's top-left corner:
   0 Single   270 × 322   one 242 well at (14, 14)
   1 Double   322 × 196   two 141 wells, 12 apart
   2 Triple   322 × 150   three 94 wells, 6 apart
   3 Four     290 × 316   2 × 2 wells of 125, 12 apart
   4 Six      322 × 242   3 × 2 wells of 94, 6 apart
 Every layout lists six wells: those it does not use sit on its last real well, under it, so a growing
 layout splits a photo out from under its neighbour and a shrinking one tucks it back.

 The layout at a fractional page p is the linear blend of its two neighbours (extrapolated past the
 ends). Between Triple and Four the prints come loose: with λ = sin(π (p − 2)), each of the four wells
 grows its own white border (8 pt, 22 pt at the bottom), tilts and spreads out, and the big frame fades
 to 1 − 1.6 λ (gone at λ = 0.625).

 autoPlay timeline (LOOP = 7 s):
   0.0–0.8   rest on Single.
   0.8       → Double, spring (1 − e^(−9u)(cos 12u + 0.75 sin 12u), ~9 % overshoot, settled by 0.6 s).
   1.9       → Triple, same spring.
   3.0–4.1   → Four, ease in-out over 1.1 s, the prints scattering and gathering on the way.
   4.7       → Six, spring.
   5.8       back to Single in one sweep, critically damped (1 − (1 + 9u) e^(−9u)).
   6.7–7.0   rest on Single, the same frame as t = 0.
 */

export const LOOP = 7;

export type Well = { x: number; y: number; s: number };
export type Format = { name: string; caption: string; w: number; h: number; wells: Well[]; count: number };

function make(name: string, caption: string, w: number, h: number, real: number[][]): Format {
  const wells = real.map(([x, y, s]) => ({ x, y, s }));
  while (wells.length < 6) wells.push(wells[real.length - 1]);
  return { name, caption, w, h, wells, count: real.length };
}

export const FORMATS: Format[] = [
  make('Single', 'One photo, a classic square', 270, 322, [[14, 14, 242]]),
  make('Double', 'Two photos side by side', 322, 196, [
    [14, 14, 141],
    [167, 14, 141],
  ]),
  make('Triple', 'Three photos in a strip', 322, 150, [
    [14, 14, 94],
    [114, 14, 94],
    [214, 14, 94],
  ]),
  make('Four', 'Four photos in a grid', 290, 316, [
    [14, 14, 125],
    [151, 14, 125],
    [14, 151, 125],
    [151, 151, 125],
  ]),
  make('Six', 'Six photos, a contact sheet', 322, 242, [
    [14, 14, 94],
    [114, 14, 94],
    [214, 14, 94],
    [14, 114, 94],
    [114, 114, 94],
    [214, 114, 94],
  ]),
];

/** Where the loose prints go between Triple and Four: offset (pt) and tilt (degrees) at λ = 1. */
export const SCATTER = [
  [-24, -20, -10],
  [22, -14, 8],
  [-18, 18, 7],
  [26, 22, -9],
];

export type Shape = { w: number; h: number; wells: Well[]; shown: number; loose: number };

export function shapeAt(p: number, reduce: boolean): Shape {
  'worklet';
  const n = FORMATS.length;
  const a = Math.min(n - 2, Math.max(0, Math.floor(p)));
  const f = p - a;
  const A = FORMATS[a];
  const B = FORMATS[a + 1];
  const mix = (u: number, v: number) => u + (v - u) * f;
  const wells: Well[] = [];
  for (let i = 0; i < 6; i++) {
    wells.push({ x: mix(A.wells[i].x, B.wells[i].x), y: mix(A.wells[i].y, B.wells[i].y), s: mix(A.wells[i].s, B.wells[i].s) });
  }
  const loose = !reduce && p > 2 && p < 3 ? Math.sin(Math.PI * (p - 2)) : 0;
  const shown = f <= 0.001 ? A.count : f >= 0.999 ? B.count : Math.max(A.count, B.count);
  return { w: mix(A.w, B.w), h: mix(A.h, B.h), wells, shown, loose };
}

export function spring(u: number) {
  'worklet';
  return u <= 0 ? 0 : 1 - Math.exp(-9 * u) * (Math.cos(12 * u) + 0.75 * Math.sin(12 * u));
}
export function critical(u: number) {
  'worklet';
  return u <= 0 ? 0 : 1 - (1 + 9 * u) * Math.exp(-9 * u);
}
function easeInOut(x: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, x));
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** The page at loop time t. */
export function pageAt(t: number) {
  'worklet';
  if (t < 0.8) return 0;
  if (t < 1.9) return spring(t - 0.8);
  if (t < 3.0) return 1 + spring(t - 1.9);
  if (t < 4.7) return 2 + easeInOut((t - 3.0) / 1.1);
  if (t < 5.8) return 3 + spring(t - 4.7);
  return 4 - 4 * critical(t - 5.8);
}

/** The x of a name's centre at page p, from the names' centres laid out in a row. */
export function centerAt(p: number, xs: number[]) {
  'worklet';
  const n = xs.length;
  const a = Math.min(n - 2, Math.max(0, Math.floor(p)));
  return xs[a] + (xs[a + 1] - xs[a]) * (p - a);
}
