// Isometric Score Gauge — the timeline (analytic, frame LOOP equals frame 0) and the driver.

import { COLUMNS, GAUGE, UI } from './isg-theme';

export const LOOP = 9.0;
export const RESUME = 2;

/** autoPlay's story: scroll one column, pick "pay off" (+26), swap to "new home loan" (−22), back. */
export const T = {
  scroll: [1.0, 1.9],
  pick1: 2.1, // column 0 row 0
  roll1: [2.2, 3.0],
  drop1: 3.9,
  pick2: 4.1, // column 1 row 1
  roll2: [4.15, 5.0],
  drop2: 5.9,
  rollBack: [6.0, 6.8],
  scrollBack: [7.0, 8.0],
} as const;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function mix(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}
export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - clamp01(x);
  return 1 - u * u * u;
}
export function easeInOut(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 16 * p * p * p * p * p : 1 - Math.pow(-2 * p + 2, 5) / 2;
}
export function smooth(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * (3 - 2 * p);
}

export type Frame = {
  scroll: number; // columns scrolled right (pt): + brings the left columns in
  sel: number; // selected card (column · 2 + row), −1 none
  selW: number; // its lift 0 … 1
  score: number;
  base: number; // the score without a choice
};

export function frameAt(t: number): Frame {
  'worklet';
  const P = UI.grid.pitch;
  const scroll = t < T.scrollBack[0] ? P * easeInOut(seg(t, T.scroll[0], T.scroll[1])) : P * (1 - easeInOut(seg(t, T.scrollBack[0], T.scrollBack[1])));
  let sel = -1;
  let selW = 0;
  if (t >= T.pick1 && t < T.drop1 + 0.25) {
    sel = 0;
    selW = t < T.drop1 ? easeOutCubic(seg(t, T.pick1, T.pick1 + 0.3)) : 1 - smooth(seg(t, T.drop1, T.drop1 + 0.25));
  } else if (t >= T.pick2 && t < T.drop2 + 0.25) {
    sel = 3;
    selW = t < T.drop2 ? easeOutCubic(seg(t, T.pick2, T.pick2 + 0.3)) : 1 - smooth(seg(t, T.drop2, T.drop2 + 0.25));
  }
  const s0 = GAUGE.score;
  const a = s0 + COLUMNS[0][0].delta;
  const b = s0 + COLUMNS[1][1].delta;
  let score = s0;
  if (t >= T.roll1[0] && t < T.roll2[0]) score = mix(s0, a, easeOutCubic(seg(t, T.roll1[0], T.roll1[1])));
  else if (t >= T.roll2[0] && t < T.rollBack[0]) score = mix(a, b, easeOutCubic(seg(t, T.roll2[0], T.roll2[1])));
  else if (t >= T.rollBack[0]) score = mix(b, s0, easeOutCubic(seg(t, T.rollBack[0], T.rollBack[1])));
  return { scroll, sel, selW, score, base: s0 };
}

// ------------------------------------------------------------------ the driver

export type Driver = {
  t: number;
  mode: 0 | 2;
  last: number;
  amb: number;
  scroll: number;
  scrollV: number;
  dragging: number;
  aim: number; // the column offset the scroll snaps to (pt)
  sel: number;
  selW: number;
  score: number;
  scoreV: number;
  goal: number; // score the spring heads for
};

export function makeDriver(t = 0): Driver {
  'worklet';
  const f = frameAt(t);
  return { t, mode: 0, last: -10, amb: t, scroll: f.scroll, scrollV: 0, dragging: 0, aim: 0, sel: -1, selW: 0, score: f.score, scoreV: 0, goal: f.score };
}

const MAX_SCROLL = UI.grid.pitch; // the first column can come to the front …
const MIN_SCROLL = -UI.grid.pitch * 1.6; // … and the last one too

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    const f = frameAt(d.t);
    d.scroll = f.scroll;
    d.sel = f.sel;
    d.selW = f.selW;
    d.score = f.score;
    d.goal = f.score;
    return;
  }
  // the carousel glides and snaps to a column
  if (d.dragging === 0) {
    const k = 90;
    const c = 2 * 0.86 * Math.sqrt(k);
    d.scrollV += (-k * (d.scroll - d.aim) - c * d.scrollV) * dt;
    d.scroll += d.scrollV * dt;
  }
  // the score rolls toward its goal on a spring (a touch of overshoot, like a needle)
  const k2 = 60;
  const c2 = 2 * 0.72 * Math.sqrt(k2);
  d.scoreV += (-k2 * (d.score - d.goal) - c2 * d.scoreV) * dt;
  d.score += d.scoreV * dt;
  d.selW += ((d.sel >= 0 ? 1 : 0) - d.selW) * (1 - Math.exp(-dt * 14));
  if (auto && d.dragging === 0 && now - d.last >= RESUME && d.sel < 0 && Math.abs(d.score - GAUGE.score) < 0.5) {
    // back to the loop, at its rest (the scroll eases home first)
    d.aim = 0;
    if (Math.abs(d.scroll) < 0.5) {
      d.mode = 0;
      d.t = 8.2;
    }
  }
}

export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 2) return;
  d.mode = 2;
  d.scrollV = 0;
  d.aim = Math.round(d.scroll / UI.grid.pitch) * UI.grid.pitch;
  d.goal = d.sel >= 0 ? GAUGE.score + COLUMNS[Math.floor(d.sel / 2)][d.sel % 2].delta : GAUGE.score;
}

/** Let go of a drag: snap to the column the flick points at. */
export function release(d: Driver, v: number) {
  'worklet';
  d.dragging = 0;
  d.scrollV = v;
  const P = UI.grid.pitch;
  const target = Math.round((d.scroll + v * 0.2) / P) * P;
  d.aim = Math.max(MIN_SCROLL, Math.min(MAX_SCROLL, target));
}

export function dragTo(d: Driver, from: number, dx: number) {
  'worklet';
  let s = from + dx;
  // rubber band past the ends
  if (s > MAX_SCROLL) s = MAX_SCROLL + (s - MAX_SCROLL) * 0.3;
  if (s < MIN_SCROLL) s = MIN_SCROLL + (s - MIN_SCROLL) * 0.3;
  d.scroll = s;
  d.dragging = 1;
}

/** Tap a card: choose it (the score previews the outcome) or let go of it. */
export function choose(d: Driver, index: number) {
  'worklet';
  d.sel = d.sel === index ? -1 : index;
  d.goal = d.sel >= 0 ? GAUGE.score + COLUMNS[Math.floor(d.sel / 2)][d.sel % 2].delta : GAUGE.score;
  return d.sel >= 0 ? COLUMNS[Math.floor(d.sel / 2)][d.sel % 2].delta : 0;
}

/** Where a column sits: its x centre, turn (degrees) and depth scale, after scrolling. */
export function columnPose(i: number, scroll: number) {
  'worklet';
  const x = UI.grid.x0 + i * UI.grid.pitch + scroll;
  // flat near the middle, turning away and shrinking toward the edges (like a cylinder seen from inside)
  const off = x - 227.5;
  const edge = clamp01((Math.abs(off) - 95) / 110);
  const ang = (off < 0 ? 1 : -1) * 62 * smooth(edge);
  return { x, ang, k: 1 - 0.12 * edge };
}
