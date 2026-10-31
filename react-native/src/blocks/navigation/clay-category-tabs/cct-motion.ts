// Clay Category Tabs — the autoPlay loop as pure functions of time. Helpers first: worklets capture
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
/** The squash of a landing: starts and ends at 0, one wobble that dies away. */
export function landSquash(u: number) {
  'worklet';
  if (u <= 0 || u >= 0.9) return 0;
  return 0.6 * Math.exp(-u * 8) * Math.sin(u * 22);
}
/** The bell's ring: the knob goes down and comes back, the dome rocks. */
export function ringKnob(u: number) {
  'worklet';
  if (u <= 0 || u >= 0.3) return 0;
  return Math.sin((Math.PI * u) / 0.3);
}
export function ringWobble(u: number) {
  'worklet';
  if (u <= 0 || u >= 1.2) return 0;
  return 0.16 * Math.exp(-u * 4.5) * Math.sin(u * 30);
}

export const LOOP = 9;

// tap Experiences, drag the page to Services (holding half-way), tap Homes; frame 9 s equals frame 0
export const T = {
  tap1: 1.0,
  go1: [1.05, 2.15] as const,
  drag0: 3.2,
  half: [3.2, 3.9] as const,
  more: [4.3, 4.7] as const,
  release: 4.7,
  land2: 5.2,
  tap3: 6.6,
  go3: [6.65, 8.15] as const,
} as const;
const HALF = 1.5;
const MORE = 1.82;

export function rAt(t: number) {
  'worklet';
  if (t < T.go1[0]) return 0;
  if (t < T.drag0) return easeInOut(seg(t, T.go1[0], T.go1[1]));
  if (t < T.more[0]) return 1 + (HALF - 1) * easeInOut(seg(t, T.half[0], T.half[1]));
  if (t < T.release) return HALF + (MORE - HALF) * easeInOut(seg(t, T.more[0], T.more[1]));
  if (t < T.go3[0]) return MORE + (2 - MORE) * easeOut(seg(t, T.release, T.land2));
  return 2 - 2 * easeInOut(seg(t, T.go3[0], T.go3[1]));
}

export type Frame = {
  r: number;
  squash: number;
  knob: number;
  wobble: number;
  finger: number; // the scripted finger's presence
  onPage: number; // 0 on a tab, 1 on the page
  tab: number; // the tab under it (an index)
  dragX: number; // along the drag, 0 … 1
  press: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  const r = rAt(t);
  const squash = landSquash(t - T.go1[1]) + landSquash(t - T.land2) + landSquash(t - T.go3[1]);
  const near = (a: number, b: number) => easeOut(seg(t, a - 0.25, a)) * (1 - easeOut(seg(t, b, b + 0.25)));
  const tapPress = (a: number) => easeOut(seg(t, a - 0.08, a)) * (1 - easeOut(seg(t, a + 0.06, a + 0.2)));
  const onTap = near(T.tap1 - 0.05, T.tap1 + 0.2) + near(T.tap3 - 0.05, T.tap3 + 0.2);
  const onDrag = near(T.drag0, T.release);
  return {
    r,
    squash,
    knob: ringKnob(t - T.land2),
    wobble: ringWobble(t - T.land2),
    finger: clamp01(onTap + onDrag),
    onPage: onDrag > 0.001 ? 1 : 0,
    tab: t < 4 ? 1 : 0,
    dragX: (rAt(t) - 1) / (MORE - 1),
    press: clamp01(tapPress(T.tap1) + tapPress(T.tap3)),
  };
}
