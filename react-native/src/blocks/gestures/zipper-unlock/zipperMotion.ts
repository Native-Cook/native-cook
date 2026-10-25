/*
 Zipper Unlock — geometry and the autoPlay script, shared by the screen, the shader and the teeth.

 LOOP = 9 s:
   0.0 – 1.6   rest: closed, the pull tab sways gently on its pivot (whole-k sines).
   1.6 – 2.0   grab: the tab lifts toward the viewer (scale 1 → 1.06, its shadow grows).
   2.0 – 4.8   unzip to 100 % with a slow start and a quick finish (p = x²(1.6 − 0.6x)).
   4.8         the tab is let go and swings (damped, 3.2 /s, 7 rad/s).
   4.8 – 5.4   the halves swing open past the screen edges (ease-out cubic); the journal's
               header and three note cards come in from 5.0, 40 ms apart (0.36 s ease-out).
   6.2 – 6.7   the halves come back (ease-in-out); 6.4 – 6.6 the tab is grabbed again.
   6.6 – 8.4   zipped back up (ease-in-out); 8.4 the tab is let go and swings out by 9.0.
   8.4 – 9.0   rest.
*/

export const LOOP = 9;

/** Teeth: spacing along the tape and size (pt). */
export const TOOTH_PITCH = 7.2;
export const TOOTH_W = 7.4;
export const TOOTH_H = 4.2;
/** The V: largest half-gap (share of the width), how far above the slider it reaches it, and the
 * length over which the opening closes again at the top stop (pt). */
export const GAP_SHARE = 0.21;
export const GAP_REACH = 300;
export const GAP_TOP = 64;
/** Half-gap right at the slider's mouth (inside the slider body). */
export const GAP_MOUTH = 5;
/** Release past this share of the travel and the zipper opens fully. */
export const OPEN_AT = 0.7;
/** Pull tab: pivot below the slider's centre, length to the ring's centre (pt). */
export const TAB_PIVOT = 13;
export const TAB_LEN = 50;

export const IDLE_BEFORE_RETURN = 2;

export type Zip = {
  width: number;
  cx: number;
  /** Top stop, closed slider centre, open slider centre, bottom stop (pt). */
  yTop: number;
  ys0: number;
  ys1: number;
  yEnd: number;
  gapMax: number;
};

export function clamp(v: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, v));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function smooth(x: number) {
  'worklet';
  const c = clamp(x, 0, 1);
  return c * c * (3 - 2 * c);
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp(x, 0, 1), 3);
}

export function easeInOut(x: number) {
  'worklet';
  const c = clamp(x, 0, 1);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}

export function makeZip(width: number, height: number, top: number, bottom: number): Zip {
  const ys0 = top + 40;
  const ys1 = height - bottom - 110;
  return { width, cx: width / 2, yTop: ys0 - 18, ys0, ys1, yEnd: ys1 + 22, gapMax: width * GAP_SHARE };
}

export function sliderY(z: Zip, p: number) {
  'worklet';
  return z.ys0 + (z.ys1 - z.ys0) * p;
}

/** Half-width of the opening at y for slider y `ys` and full-open amount `o`, already eased (same
 * in the shader). */
export function gapAt(z: Zip, y: number, ys: number, o: number) {
  'worklet';
  let v = 0;
  if (y < ys && y > z.yTop) v = GAP_MOUTH + z.gapMax * smooth((ys - y) / GAP_REACH) * smooth((y - z.yTop) / GAP_TOP);
  const full = z.width / 2 + 70;
  return v + (full - v) * clamp(o, 0, 1);
}

/** The loop's state at t: [zip progress, full open, tab lift, tab angle (rad), seconds since open]. */
export function script(t: number): [number, number, number, number, number] {
  'worklet';
  // Unzip: slow start, quick finish; zip up: ease-in-out.
  const x = seg(t, 2.0, 4.8);
  const down = x * x * (1.6 - 0.6 * x);
  const up = easeInOut(seg(t, 6.6, 8.4));
  const p = t < 6.6 ? down : 1 - up;
  const o = easeOut(seg(t, 4.8, 5.4)) * (1 - easeInOut(seg(t, 6.2, 6.7)));
  const lift = easeOut(seg(t, 1.6, 2.0)) * (1 - easeOut(seg(t, 4.8, 5.0))) + easeOut(seg(t, 6.4, 6.6)) * (1 - easeOut(seg(t, 8.4, 8.6)));
  // Sway at rest (2 and 3 cycles a loop), held still while the tab is held, plus the swing after
  // each let-go, faded out before the loop closes.
  const sway = 0.05 * Math.sin((2 * Math.PI * 2 * t) / LOOP) + 0.02 * Math.sin((2 * Math.PI * 3 * t) / LOOP);
  let swing = 0;
  if (t > 4.8) swing += 0.26 * Math.exp(-3.2 * (t - 4.8)) * Math.sin(7 * (t - 4.8));
  if (t > 8.4) swing -= 0.22 * Math.exp(-3.2 * (t - 8.4)) * Math.sin(7 * (t - 8.4));
  swing *= 1 - smooth(seg(t, 8.7, 9.0));
  const angle = sway * (1 - lift) + swing;
  const since = t >= 4.8 && t < 6.7 ? t - 4.8 : -1;
  return [p, o, lift, angle, since];
}

/** Entrance of the journal's i-th element, from seconds since the halves started to open. */
export function revealOf(since: number, i: number) {
  'worklet';
  if (since < 0) return 0;
  return easeOut((since - 0.2 - i * 0.04) / 0.36);
}
