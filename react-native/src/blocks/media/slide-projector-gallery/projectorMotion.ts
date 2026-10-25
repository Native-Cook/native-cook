/*
 Slide Projector Gallery — the timeline of one slide change and of the autoPlay loop. Pure
 functions of time; the finger's changes use the same `changePose`, timed from the swipe.

 One change (u = 0 … 1 over CHANGE s, or FAST s for the quick ones):
   0.00 – 0.18  the lamp dims to dark (ease-in-out) while the lamp flickers.
   0.12 – 0.42  the tray turns one slot (ease-out-back, 6 % overshoot: the clunk).
   0.30         the next slide is in the gate.
   0.33 – 0.62  it drops in: the picture slides up from 34 pt below its place (ease-out-back),
                the lamp comes back (ease-out).
   0.33 – 0.80  focus pull, blurry to sharp (ease-out), then 0.72 – 0.95 a small breath past
                focus and back (the operator's hand); the picture is 3 % larger while soft.

 autoPlay (LOOP = 9 s): rest on slide 1 0 – 1.4; 1 → 2 at 1.4 – 2.6; hold; 2 → 3 at 4.2 – 5.4;
 hold; back to the first in two quick changes, 3 → 2 at 6.6 – 7.5 and 2 → 1 at 7.5 – 8.4; rest.
 Dust motes drift up the beam once per loop.
 */

export const LOOP = 9;
export const CHANGE = 1.2;
export const FAST = 0.9;
export const DROP = 34;
export const TRAY_SLOTS = 24;

const AUTO: [number, number, number, number][] = [
  [1.4, CHANGE, 0, 1],
  [4.2, CHANGE, 1, 2],
  [6.6, FAST, 2, 1],
  [7.5, FAST, 1, 0],
];

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const u = clamp01((x - a) / (b - a));
  return u * u * (3 - 2 * u);
}

function easeOutCubic(u: number) {
  'worklet';
  const v = 1 - u;
  return 1 - v * v * v;
}

function easeInOutCubic(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

function easeOutBack(u: number, c1: number) {
  'worklet';
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2);
}

export type ProjPose = {
  /** Slide in the gate (0…4). */
  slide: number;
  /** Tray position in slots (continuous). */
  tray: number;
  bright: number;
  drop: number;
  focus: number;
  flicker: number;
  scale: number;
};

/** One change from slide `from` to `to` at progress u (0…1). */
export function changePose(u: number, from: number, to: number): ProjPose {
  'worklet';
  const dim = easeInOutCubic(seg(u, 0, 0.18));
  const back = easeOutCubic(seg(u, 0.33, 0.62));
  const bright = 1 - dim + dim * back;
  const tray = from + (to - from) * easeOutBack(seg(u, 0.12, 0.42), 1.3);
  const drop = u < 0.33 ? DROP : DROP * (1 - easeOutBack(seg(u, 0.33, 0.62), 1.1));
  const pull = 1 - easeOutCubic(seg(u, 0.33, 0.8));
  const breath = 0.22 * Math.sin(Math.PI * seg(u, 0.72, 0.95));
  const focus = u < 0.33 ? 0.6 * easeInOutCubic(seg(u, 0.02, 0.2)) : pull + breath;
  // A lamp that stutters while the slide is pulled and pushed (a fixed pattern, not random).
  const w = seg(u, 0.04, 0.5);
  const flick = w > 0 && w < 1 ? 0.18 * Math.max(0, Math.sin(37 * w) * Math.sin(23 * w + 1.3)) : 0;
  return { slide: u < 0.3 ? from : to, tray, bright, drop, focus, flicker: 1 - flick, scale: 1 + 0.03 * Math.min(1, focus) };
}

export function restPose(slide: number): ProjPose {
  'worklet';
  return { slide, tray: slide, bright: 1, drop: 0, focus: 0, flicker: 1, scale: 1 };
}

export function autoPose(t: number): ProjPose {
  'worklet';
  for (let i = 0; i < AUTO.length; i += 1) {
    const [a, len, from, to] = AUTO[i];
    if (t >= a && t < a + len) return changePose((t - a) / len, from, to);
  }
  let slide = 0;
  for (let i = 0; i < AUTO.length; i += 1) if (t >= AUTO[i][0] + AUTO[i][1]) slide = AUTO[i][3];
  return restPose(slide);
}

function fractHash(x: number) {
  'worklet';
  const v = Math.sin(x * 12.9898) * 43758.5453;
  return v - Math.floor(v);
}

/** Dust mote i at loop time t: [x, y, radius, brightness] given the beam (lens, slide bottom). */
export function mote(i: number, t: number, lensX: number, lensY: number, topY: number, halfTop: number): number[] {
  'worklet';
  const h1 = fractHash(i * 1.37 + 0.11);
  const h2 = fractHash(i * 2.91 + 0.53);
  const h3 = fractHash(i * 4.17 + 0.29);
  const k = 1 + Math.floor(h3 * 2);
  const s = (h1 + (k * t) / LOOP) % 1;
  const sway = 0.1 * Math.sin(2 * Math.PI * ((2 * t) / LOOP + h2));
  const lateral = (h2 * 2 - 1) * 0.92 + sway;
  const y = lensY - s * (lensY - topY);
  const bw = 10 + (halfTop - 10) * s;
  const x = lensX + lateral * bw;
  const fade = smoothstep(0, 0.12, s) * (1 - smoothstep(0.85, 1, s));
  const tw = 0.6 + 0.4 * Math.sin(2 * Math.PI * ((3 * t) / LOOP + h1 * 7));
  const r = 0.6 + h3 * 1.5;
  return [x, y, r, fade * tw * (1 - 0.7 * Math.abs(lateral))];
}
