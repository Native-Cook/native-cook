/*
 Cross Stitch Input — the autoPlay script. Every value is a pure function of the loop time t, so
 the frame at t = LOOP is the frame at 0.

   0.0 – 1.2   rest: "Hi" worked in indigo, the needle parked in the cloth at the lower right.
   1.0 – 1.3   the needle comes up to the last stitch; 1.2 – 2.2 it unpicks "Hi" in reverse,
               legs pulling out one after the other. The field is erased (1.35, 1.5).
   1.95        the Madder chip is chosen; "June" is typed (1.95 – 2.45).
   2.0 – 5.6   the needle works "June", stitch by stitch: bottom-left to top-right, then bottom-right
               to top-left, rows worked back and forth.
   5.6 – 5.85  a knot: the needle loops once round the last hole; 5.85 – 6.35 it goes back to rest.
   6.2 – 7.2   hold.
   7.2 – 8.5   back to the start: "June" pulls out (7.2 – 7.9) while "Hi" is worked again in
               indigo in a quick time-lapse (7.6 – 8.5), the field reads "Hi" again.
   8.5 – 9.0   rest.
 */

export const LOOP = 9;
export const REST_TEXT = 'Hi';
export const SECOND_TEXT = 'June';
export const REST_THREAD = 1;
export const SECOND_THREAD = 0;

/** The field's text over the loop: [from t, text]. */
export const TEXT_SCRIPT: [number, string][] = [
  [0, 'Hi'],
  [1.35, 'H'],
  [1.5, ''],
  [1.95, 'J'],
  [2.12, 'Ju'],
  [2.29, 'Jun'],
  [2.46, 'June'],
  [7.2, 'Jun'],
  [7.3, 'Ju'],
  [7.4, 'J'],
  [7.5, ''],
  [7.62, 'H'],
  [7.76, 'Hi'],
];

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - a) / (b - a)));
}

export function easeInOut(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

export function easeOut(u: number) {
  'worklet';
  return 1 - Math.pow(1 - u, 3);
}

/** Ease-out with a small overshoot (about 4 %), for the chip ring. */
export function easeOutBack(u: number) {
  'worklet';
  const c1 = 0.9;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2);
}

/** Near-constant stitching pace that still starts and ends softly. */
function pace(u: number) {
  'worklet';
  return 0.6 * u + 0.4 * easeInOut(u);
}

export function scriptIndex(t: number) {
  'worklet';
  let i = 0;
  for (let k = 0; k < TEXT_SCRIPT.length; k += 1) if (t >= TEXT_SCRIPT[k][0]) i = k;
  return i;
}

/** How many of "Hi"'s n stitches are on the cloth (fractional: the one being worked or pulled). */
export function restPresent(t: number, n: number) {
  'worklet';
  if (t < 1.2) return n;
  if (t < 2.2) return n * (1 - easeInOut(seg(t, 1.2, 2.2)));
  if (t < 7.6) return 0;
  if (t < 8.5) return n * pace(seg(t, 7.6, 8.5));
  return n;
}

export function secondPresent(t: number, n: number) {
  'worklet';
  if (t < 2.0) return 0;
  if (t < 5.6) return n * pace(seg(t, 2.0, 5.6));
  if (t < 7.2) return n;
  if (t < 7.9) return n * (1 - easeInOut(seg(t, 7.2, 7.9)));
  return 0;
}

/** The chip ring's position (thread index, fractional while it moves). */
export function chipPosition(t: number) {
  'worklet';
  const a = easeOutBack(seg(t, 1.95, 2.3));
  const b = easeOutBack(seg(t, 7.45, 7.8));
  return REST_THREAD + (SECOND_THREAD - REST_THREAD) * a + (REST_THREAD - SECOND_THREAD) * b;
}

/** 0 → the counter's total is "Hi"'s, 1 → "June"'s. */
export function totalBlend(t: number) {
  'worklet';
  return easeInOut(seg(t, 1.95, 2.35)) * (1 - easeInOut(seg(t, 7.45, 7.85)));
}

/** 0 → indigo, 1 → madder (the name's thread in the loop's two halves). */
export function threadBlend(t: number) {
  'worklet';
  return easeInOut(seg(t, 1.9, 2.1)) * (1 - easeInOut(seg(t, 7.4, 7.6)));
}

/**
 * The needle's script as blend weights: [park, follow "Hi", follow "June", knot phase 0..1,
 * lift]. The component turns the weights into a position from the stitch data.
 */
export function needleScript(t: number): number[] {
  'worklet';
  const toWork = easeInOut(seg(t, 1.0, 1.3));
  const handOff = easeInOut(seg(t, 1.95, 2.2));
  const home = easeInOut(seg(t, 5.85, 6.35));
  const knot = seg(t, 5.6, 5.85);
  const park = 1 - toWork + toWork * home;
  const work = toWork * (1 - home);
  // Lifted while it travels to and from the work; resting stuck in the cloth otherwise.
  const lift = Math.sin(Math.PI * seg(t, 1.0, 1.3)) * 0.6 + Math.sin(Math.PI * seg(t, 5.85, 6.35)) * 0.8;
  return [park, work * (1 - handOff), work * handOff, knot, lift];
}
