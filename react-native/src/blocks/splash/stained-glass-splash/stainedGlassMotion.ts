// Stained Glass Splash — the loop's script, as pure worklets.
//
// LOOP = 9 s:
//   0.0 – 0.8  rest: the window at a quarter glow in its stone, the wordmark at 55 %.
//   0.8 – 4.4  the sun crosses: panes light by their key (left to right, outer rings first, the
//              gold rose of the hub last), the beam (the brightest band) moves with it, the floor
//              fills with colour (ease-out).
//   4.4 – 5.6  the projection peaks: the beam settles in the middle, the floor at its brightest.
//   5.6 – 6.4  flood: warm light pours out of the window into cream (ease-in-out); the home screen
//              rises in over 5.8 – 6.25 (ease-out, 12 pt), so its content arrives with the cream.
//              Under the cream the panes go dark again and the beam returns to the left.
//   6.4 – 7.6  home.
//   7.8 – 9.0  home fades (8.0 – 8.5) as the cream drains back into the window (7.8 – 8.8) and the
//              wordmark returns (8.4 – 9.0): the frame at 9 s is the frame at 0 s.

export const LOOP = 9;
/** The lit range of keys at rest (nothing lit). */
export const DARK = -0.12;

export type Layout = { cx: number; cy: number; R: number };

export function makeLayout(width: number, height: number, insetTop: number): Layout {
  const R = Math.min(width * 0.42, height * 0.2);
  return { cx: width / 2, cy: insetTop + 70 + R, R };
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

export type Scene = {
  lo: number;
  hi: number;
  beam: number;
  keyMix: number;
  flood: number;
  /** The home screen: 0 hidden … 1 shown, and its exit (0 … 1). */
  home: number;
  homeOut: number;
  /** The wordmark's opacity. */
  mark: number;
};

export function makeScene(): Scene {
  'worklet';
  return { lo: DARK, hi: DARK, beam: -0.2, keyMix: 1, flood: 0, home: 0, homeOut: 0, mark: 0.55 };
}

export function loopScene(t: number, s: Scene) {
  'worklet';
  // Ease-out: the first panes catch the sun within half a second of 0.8 s.
  const cross = 1 - Math.pow(1 - seg(t, 0.8, 4.4), 1.6);
  s.lo = DARK;
  s.hi = t < 6.4 ? DARK + (1.25 - DARK) * cross : DARK;
  // The beam crosses with the light, settles in the middle, and goes back to the left edge under
  // the cream (6.4 – 7.8), so the window it drains back to is the one the loop starts with.
  s.beam = t < 4.4 ? -0.15 + 1.1 * cross : t < 6.4 ? 0.95 - 0.45 * easeInOut(seg(t, 4.4, 5.4)) : 0.5 - 0.65 * easeInOut(seg(t, 6.4, 7.8));
  s.keyMix = 1;
  s.flood = easeInOut(seg(t, 5.6, 6.4)) * (1 - easeInOut(seg(t, 7.8, 8.8)));
  // The home's content arrives with the cream, not after it.
  s.home = easeOut(seg(t, 5.8, 6.25));
  s.homeOut = easeInOut(seg(t, 8.0, 8.5));
  const lit = clamp01((s.hi - s.lo) / 1.3);
  s.mark = t < 6.4 ? 0.55 + 0.45 * lit : 0.55 * easeOut(seg(t, 8.4, 9.0));
}

export function blendScene(a: Scene, b: Scene, e: number, s: Scene) {
  'worklet';
  s.lo = a.lo + (b.lo - a.lo) * e;
  s.hi = a.hi + (b.hi - a.hi) * e;
  s.beam = a.beam + (b.beam - a.beam) * e;
  s.keyMix = a.keyMix + (b.keyMix - a.keyMix) * e;
  s.flood = a.flood + (b.flood - a.flood) * e;
  s.home = a.home + (b.home - a.home) * e;
  s.homeOut = a.homeOut + (b.homeOut - a.homeOut) * e;
  s.mark = a.mark + (b.mark - a.mark) * e;
}

export function copyScene(a: Scene): Scene {
  'worklet';
  return { lo: a.lo, hi: a.hi, beam: a.beam, keyMix: a.keyMix, flood: a.flood, home: a.home, homeOut: a.homeOut, mark: a.mark };
}
