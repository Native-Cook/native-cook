// Clay Reaction Morph — the autoPlay loop as pure functions of time. Helpers first: worklets capture
// what is above them.

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
/** Ease-out with a small overshoot (about 6 %), landing exactly on 1. */
export function backOut(x: number) {
  'worklet';
  const c1 = 1.25;
  const u = clamp01(x) - 1;
  return 1 + (c1 + 1) * u * u * u + c1 * u * u;
}
/** The squash of a landing: starts and ends at 0, one deep wobble that dies away. */
export function landSquash(u: number) {
  'worklet';
  if (u <= 0 || u >= 0.9) return 0;
  return 0.85 * Math.exp(-u * 8) * Math.sin(u * 24);
}

/** The reactions, in the tray's order. */
export const REACTIONS = ['Like', 'Love', 'Haha', 'Wow'] as const;
export const REACTION_TINT = ['#3880FF', '#FF2B4D', '#FFCC33', '#FF9E24'] as const;
export const START_REACTION = 1; // the bubble starts with Love

export const LOOP = 11;

// The loop: two holds. The first lifts Love out and slides Love → Haha → Wow → Like → Haha, then lets
// go; the second lifts Haha and slides back to Love. Frame 11 s equals frame 0.
export const T = {
  touch1: 0.7, // the finger lands on the bubble
  lift1: 1.0, // the long press: the reaction lifts out
  release1: 6.55,
  touch2: 7.75,
  lift2: 8.05,
  release2: 9.6,
} as const;
export const LIFT_S = 0.6;
export const FLY_S = 0.6;
const SLIDES: [number, number, number, number][] = [
  // from s, to s, from r, to r
  [1.75, 2.35, 1, 2],
  [2.75, 3.35, 2, 3],
  [3.75, 5.0, 3, 0],
  [5.4, 6.2, 0, 2],
  [8.75, 9.35, 2, 1],
];

export function rAt(t: number) {
  'worklet';
  let r: number = START_REACTION;
  for (let i = 0; i < SLIDES.length; i++) {
    const s = SLIDES[i];
    if (t >= s[0]) r = s[2] + (s[3] - s[2]) * easeInOut(seg(t, s[0], s[1]));
  }
  return r;
}

function liftOf(t: number, lift: number, release: number) {
  'worklet';
  if (t < lift) return 0;
  if (t < release) return backOut(seg(t, lift, lift + LIFT_S));
  return 1 - easeInOut(seg(t, release, release + FLY_S));
}

export type Frame = {
  lift: number; // 0 on the bubble … 1 above it (a little over while it pops)
  r: number; // the reaction, 0 … 3
  hold: number; // the dim behind
  press: number; // the bubble pressed
  squash: number;
  lean: number; // with the finger's speed, −1 … 1
  finger: number; // the scripted finger's presence
  fingerDx: number; // how far it has slid, in reactions
};

function baseAt(t: number): Frame {
  'worklet';
  const lift = liftOf(t, T.lift1, T.release1) + liftOf(t, T.lift2, T.release2);
  const r = rAt(t);
  const dr = (rAt(t + 0.03) - rAt(t - 0.03)) / 0.06;
  const lean = Math.max(-1, Math.min(1, dr * 0.32));
  const up = (a: number, b: number) => easeInOut(seg(t, a, a + 0.2)) * (1 - easeInOut(seg(t, b, b + 0.3)));
  const hold = up(T.lift1, T.release1) + up(T.lift2, T.release2);
  const pr = (a: number, b: number) => easeOut(seg(t, a, a + 0.12)) * (1 - easeOut(seg(t, b, b + 0.25)));
  const press = pr(T.touch1, T.lift1) + pr(T.touch2, T.lift2);
  // a stretch as it pops out, a squash as it lands
  const pop = (a: number) => -0.35 * Math.sin(Math.PI * seg(t, a, a + 0.35));
  const squash = pop(T.lift1) + pop(T.lift2) + landSquash(t - T.release1 - FLY_S) + landSquash(t - T.release2 - FLY_S);
  const near = (a: number, b: number) => easeOut(seg(t, a - 0.25, a)) * (1 - easeOut(seg(t, b, b + 0.2)));
  const finger = near(T.touch1, T.release1) + near(T.touch2, T.release2);
  const fingerDx = t < T.touch2 - 0.3 ? r - START_REACTION : r - 2;
  return { lift, r, hold, press, squash, lean, finger, fingerDx };
}

// The loop opens with the heart already up (the X feed's first frame should be the clay, not an idle
// chat): loop time 0 is PHASE s into the story, and the story's rest at 0 is loop time REST_T, where a
// touch-free screen picks the loop up again.
export const PHASE = 1.6;
export const REST_T = LOOP - PHASE;

export function frameAt(t: number): Frame {
  'worklet';
  return baseAt((t + PHASE) % LOOP);
}
