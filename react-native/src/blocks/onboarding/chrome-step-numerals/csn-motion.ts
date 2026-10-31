// Chrome Step Numerals — the step, the balloon's turn and the autoPlay loop as pure functions of time.
// Helpers first: worklets capture what is above them.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export const STEPS = 3;
export const YAW0 = 0.3;
/** The balloon turns once for every step it moves (so it faces you again at each numeral). */
export function yawOf(r: number) {
  'worklet';
  return YAW0 + 2 * Math.PI * r;
}

export const LOOP = 9;
// tap Next (1 → 2); drag the page to 3, holding half-way; let go; swipe back to 1
export const T = {
  next: 1.2,
  go1: [1.3, 2.5] as const,
  drag: [3.4, 4.4] as const,
  more: [4.9, 5.4] as const,
  settle: [5.4, 5.9] as const,
  back: [7.0, 8.6] as const,
} as const;
const HALF = 1.5;
const LET_GO = 1.8;

export function rAt(t: number) {
  'worklet';
  if (t < T.go1[0]) return 0;
  if (t < T.drag[0]) return easeInOut(seg(t, T.go1[0], T.go1[1]));
  if (t < T.more[0]) return 1 + (HALF - 1) * easeInOut(seg(t, T.drag[0], T.drag[1]));
  if (t < T.settle[0]) return HALF + (LET_GO - HALF) * easeInOut(seg(t, T.more[0], T.more[1]));
  if (t < T.back[0]) return LET_GO + (2 - LET_GO) * easeOut(seg(t, T.settle[0], T.settle[1]));
  return 2 - 2 * easeInOut(seg(t, T.back[0], T.back[1]));
}

export type Frame = { r: number; bob: number; press: number; finger: number; fingerX: number; fingerAt: number };

export function frameAt(t: number): Frame {
  'worklet';
  const r = rAt(t);
  const tap = (a: number) => easeOut(seg(t, a - 0.08, a)) * (1 - easeOut(seg(t, a + 0.06, a + 0.2)));
  const near = (a: number, b: number) => easeOut(seg(t, a - 0.25, a)) * (1 - easeOut(seg(t, b, b + 0.2)));
  const onNext = near(T.next - 0.05, T.next + 0.15);
  const onDrag = near(T.drag[0], T.more[1]);
  const onBack = near(T.back[0], T.back[1]);
  const finger = clamp01(onNext + onDrag + onBack);
  // the finger on the page: right to left for the drag (in screen widths), left to right going back
  const fingerX = onBack > 0.001 ? 0.2 + 0.6 * easeInOut(seg(t, T.back[0], T.back[1])) : 0.75 - (r - 1) * 0.6;
  return {
    r,
    bob: 0.04 * Math.sin((2 * Math.PI * 2 * t) / LOOP),
    press: tap(T.next),
    finger,
    fingerX,
    fingerAt: onNext > 0.001 ? 0 : 1,
  };
}
