// Star Coupon Tear — the pull, the ticket, its flight and the autoPlay loop as pure functions of time.
// Helpers first: worklets capture what is above them.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function smooth(a: number, b: number, x: number) {
  'worklet';
  const u = clamp01((x - a) / (b - a));
  return u * u * (3 - 2 * u);
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
export function backOut(x: number) {
  'worklet';
  const c1 = 1.6;
  const u = clamp01(x) - 1;
  return 1 + (c1 + 1) * u * u * u + c1 * u * u;
}

export const PULL_PT = 150; // pt of pull for the whole tear
export const CLAIM_AT = 0.6; // past this share of the pull, letting go claims it
/** The shape at a pull: the star holds a little, then becomes the ticket. */
export function tearOf(pull: number) {
  'worklet';
  return smooth(0.12, 1, pull);
}

export const LOOP = 9;
export const T = {
  grab: 1.0,
  pull1: [1.1, 2.0] as const,
  pull2: [2.4, 2.8] as const,
  release: 2.9,
  snap: [2.9, 3.3] as const,
  fly: [3.8, 4.5] as const,
  pop: [5.2, 5.8] as const,
} as const;

export type Frame = {
  pull: number; // the finger's pull, 0 … 1 (stretches it)
  tear: number; // star 0 … ticket 1
  fly: number; // 0 … 1 into the coupons chip
  pop: number; // the new star growing in, as progress (drawn through backOut), 1 = there
  chip: number; // the chip's pulse, as progress (1 = rest)
  finger: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  let pull = 0;
  if (t >= T.pull1[0] && t < T.release) pull = 0.5 * easeInOut(seg(t, T.pull1[0], T.pull1[1])) + 0.35 * easeInOut(seg(t, T.pull2[0], T.pull2[1]));
  // after the release the stretch springs back while the tear completes
  const after = t >= T.release && t < T.fly[1] ? 0.85 * (1 - easeOut(seg(t, T.snap[0], T.snap[1]))) : 0;
  const tear = t < T.release ? tearOf(pull) : t < T.fly[1] ? tearOf(0.85) + (1 - tearOf(0.85)) * easeOut(seg(t, T.snap[0], T.snap[1])) : 0;
  const fly = t < T.fly[1] ? easeInOut(seg(t, T.fly[0], T.fly[1])) : 0;
  const pop = t < T.fly[1] ? 1 : seg(t, T.pop[0], T.pop[1]);
  const near = easeOut(seg(t, T.grab - 0.25, T.grab)) * (1 - easeOut(seg(t, T.release, T.release + 0.2)));
  return {
    pull: t < T.release ? pull : after,
    tear,
    fly,
    pop,
    chip: t < T.fly[1] ? 1 : seg(t, T.fly[1], T.fly[1] + 0.8),
    finger: near,
  };
}
