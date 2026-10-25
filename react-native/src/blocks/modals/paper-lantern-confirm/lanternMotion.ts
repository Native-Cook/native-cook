// Paper Lantern Confirm — the loop's script and layout, as pure worklets.
//
// LOOP = 9 s:
//   0.0 – 1.2  rest: the lantern stands folded in the sheet with the wish written on it.
//   1.1 – 1.3  "Light and send" is pressed (scale 0.97); its label rolls to "Lighting the wick…".
//   1.2 – 2.0  the wick lights: a spark (to 50 % by 1.35), a dip, then full (ease-out); the paper
//              glows from inside.
//   2.0 – 3.0  it fills: the folds smooth out (ease-out), with a 3 % swell and settle.
//   3.0 – 6.4  it lifts off (a 3 pt dip first) and rises into the sky (ease-in-out), shrinking to a
//              third with distance, swaying 14 pt.
//   5.6 – 6.8  the "Wish sent" toast drops in from the top and leaves; the label reads "Wish sent".
//   6.4 – 8.6  it drifts on, smaller, and fades among the far lanterns (7.6 – 8.6).
//   6.8 – 8.2  a new lantern unfolds in the sheet (from flat, ease-out with a small overshoot);
//              8.2 the label rolls back to "Light and send".
//   8.2 – 9.0  rest: the frame at 9 s is the frame at 0 s.

export const LOOP = 9;
/** How folded a lantern stands at rest (1 flat … 0 full). */
export const REST_FOLD = 0.72;
/** The sheet's height above the bottom inset. */
export const SHEET_H = 446;
/** The lantern's seat: its centre below the sheet's top, and its scale there. */
export const SEAT_Y = 190;
export const SEAT_SCALE = 0.8;

export type Layout = { sheetTop: number; seatX: number; seatY: number; skyY: number; width: number };

export function makeLayout(width: number, height: number, insetBottom: number): Layout {
  const sheetTop = height - insetBottom - SHEET_H;
  return { sheetTop, seatX: width / 2, seatY: sheetTop + SEAT_Y, skyY: Math.max(110, sheetTop * 0.42), width };
}

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOut(x: number) {
  'worklet';
  const c = clamp01(x);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  const c = clamp01(x);
  return 1 - Math.pow(1 - c, 3);
}

/** One lantern on screen: centre, scale (x, y), opacity, fold, glow. */
export type Lamp = { x: number; y: number; sx: number; sy: number; o: number; fold: number; glow: number };

export function makeLamp(): Lamp {
  'worklet';
  return { x: 0, y: 0, sx: SEAT_SCALE, sy: SEAT_SCALE, o: 0, fold: REST_FOLD, glow: 0 };
}

export type Scene = {
  seat: Lamp;
  fly: Lamp;
  /** Button press (0 … 1), the label's index (0 Light and send, 1 Lighting, 2 Wish sent), the toast. */
  press: number;
  label: number;
  toast: number;
};

export function makeScene(): Scene {
  'worklet';
  return { seat: makeLamp(), fly: makeLamp(), press: 0, label: 0, toast: 0 };
}

/** The wick catching: a spark, a dip, then full. */
function ignite(t: number) {
  'worklet';
  if (t < 1.2) return 0;
  if (t < 1.35) return 0.5 * easeOut(seg(t, 1.2, 1.35));
  if (t < 1.45) return 0.5 - 0.2 * Math.sin(Math.PI * seg(t, 1.35, 1.45) * 0.5);
  return 0.3 + 0.7 * easeOut(seg(t, 1.45, 2.0));
}

export function loopScene(t: number, L: Layout, s: Scene) {
  'worklet';
  const seat = s.seat;
  const fly = s.fly;
  seat.x = L.seatX;
  seat.y = L.seatY;
  seat.sx = SEAT_SCALE;
  seat.sy = SEAT_SCALE;
  if (t < 3.0) {
    // A, in the sheet: lit, then filled with a small swell.
    const fill = seg(t, 2.0, 3.0);
    const swell = 1 + 0.03 * Math.sin(Math.PI * fill);
    seat.o = 1;
    seat.glow = ignite(t);
    seat.fold = REST_FOLD * (1 - easeOut(fill));
    seat.sx = SEAT_SCALE * swell;
    seat.sy = SEAT_SCALE * swell;
  } else if (t < 6.8) {
    seat.o = 0;
  } else {
    // B unfolds from flat.
    const u = seg(t, 6.8, 8.0);
    const grow = u < 0.75 ? 0.15 + 0.89 * easeOut(u / 0.75) : 1.04 - 0.04 * easeOut((u - 0.75) / 0.25);
    seat.o = easeOut(seg(t, 6.8, 7.1));
    seat.fold = REST_FOLD + (1 - REST_FOLD) * (1 - easeOut(seg(t, 6.9, 8.2)));
    seat.glow = 0;
    seat.sy = SEAT_SCALE * grow;
  }
  // The flyer: A after lift-off.
  fly.glow = 1;
  fly.fold = 0;
  if (t < 3.0 || t >= 8.6) {
    fly.o = 0;
    fly.x = L.seatX;
    fly.y = L.seatY;
  } else {
    const dip = 3 * Math.sin(Math.PI * seg(t, 3.0, 3.15));
    const e = easeInOut(seg(t, 3.0, 6.4));
    const drift = seg(t, 6.4, 8.6);
    fly.y = L.seatY + (L.skyY - L.seatY) * e + dip - 40 * Math.sin(drift * Math.PI * 0.5);
    fly.x = L.seatX + 14 * Math.sin(Math.PI * e * 1.5) - 10 * drift;
    const k = SEAT_SCALE * (1 - 0.66 * easeOut(seg(t, 3.0, 6.6))) * (1 - 0.35 * drift);
    fly.sx = k;
    fly.sy = k;
    fly.o = 1 - easeInOut(seg(t, 7.6, 8.6));
  }
  s.press = Math.sin(Math.PI * seg(t, 1.1, 1.3));
  s.label = t < 1.25 ? 0 : t < 5.6 ? 1 : t < 8.2 ? 2 : 3;
  s.toast = easeOut(seg(t, 5.6, 5.95)) * (1 - easeInOut(seg(t, 6.5, 6.8)));
}

export function copyLamp(a: Lamp, b: Lamp) {
  'worklet';
  b.x = a.x;
  b.y = a.y;
  b.sx = a.sx;
  b.sy = a.sy;
  b.o = a.o;
  b.fold = a.fold;
  b.glow = a.glow;
}

export function blendLamp(a: Lamp, b: Lamp, e: number, out: Lamp) {
  'worklet';
  out.x = a.x + (b.x - a.x) * e;
  out.y = a.y + (b.y - a.y) * e;
  out.sx = a.sx + (b.sx - a.sx) * e;
  out.sy = a.sy + (b.sy - a.sy) * e;
  out.o = a.o + (b.o - a.o) * e;
  out.fold = a.fold + (b.fold - a.fold) * e;
  out.glow = a.glow + (b.glow - a.glow) * e;
}

/** The next label index at or after `from` whose label (index mod 3) is `state`: labels roll forward. */
export function nextLabel(from: number, state: number) {
  'worklet';
  const base = Math.floor(from + 1e-3);
  for (let i = 0; i < 3; i += 1) if ((((base + i) % 3) + 3) % 3 === state) return base + i;
  return base;
}
