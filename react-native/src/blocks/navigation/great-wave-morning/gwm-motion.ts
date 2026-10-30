// Great Wave Morning — the loop and the scroll driver. Everything follows one number: how far the
// content is scrolled (px of the pin; below 0 it is pulled down past the top).

import { COLLAPSE, MAX_SCROLL } from './gwm-theme';

export const LOOP = 9.0;
export const RESUME = 2;
export const PULL = 150;

export const T = {
  pull: [1.2, 1.9], // pulled down: the sea rises …
  release: 2.1, // … let go: it springs back and the crests throw foam
  collapse: [3.0, 4.1], // scrolled up: the sea folds into one wave under the title
  unfold: [5.6, 6.8], // back to the top: it unfolds, layer after layer
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
export function smooth(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * (3 - 2 * p);
}
export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), 3);
}
export function easeInOut(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}
export function spring(tau: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const zw = damping * w;
  const wd = w * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-zw * tau) * (Math.cos(wd * tau) + (zw / wd) * Math.sin(wd * tau));
}
/** iOS-like rubber band for a pull of d px. */
export function rubber(d: number) {
  'worklet';
  const dim = 900;
  return (1 - 1 / ((d * 0.55) / dim + 1)) * dim;
}

export function loopScroll(t: number) {
  'worklet';
  if (t < T.release) return -PULL * easeOut(seg(t, T.pull[0], T.pull[1]));
  if (t < T.collapse[0]) return -PULL * (1 - spring(t - T.release, 0.55, 0.62));
  if (t < T.unfold[0]) return (COLLAPSE + 40) * easeInOut(seg(t, T.collapse[0], T.collapse[1]));
  return (COLLAPSE + 40) * (1 - easeInOut(seg(t, T.unfold[0], T.unfold[1])));
}

export type Frame = {
  amb: number;
  scroll: number;
  foam: number; // seconds since the sea was let go (−1 none) …
  foamPow: number; // … and how hard (0…1)
  done: number[]; // 0…1 per row
  tab: number; // the selected tab's position (a float while it moves)
  pressRow: number;
  pressK: number;
  tiltX?: number; // the phone's tilt around where it is held, −1 … 1 (the layers shift in depth)
  tiltY?: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  const foam = t >= T.release && t < T.release + 1.6 ? t - T.release : -1;
  return { amb: t, scroll: loopScroll(t), foam, foamPow: 1, done: [0, 0, 0, 0, 0], tab: 0, pressRow: -1, pressK: 0 };
}

export type Driver = {
  t: number;
  mode: 0 | 2;
  last: number;
  amb: number;
  s: number; // scroll (px)
  v: number; // its velocity (px/s)
  dragging: number;
  raw: number; // the finger's unrubbered scroll
  releaseAt: number;
  foamPow: number;
  done: boolean[];
  doneAt: number[];
  tab: number;
  tabPos: number;
  tabV: number;
  pressRow: number;
  pressAt: number;
  releaseRowAt: number;
  pressLast: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return {
    t,
    mode: 0,
    last: -10,
    amb: t,
    s: 0,
    v: 0,
    dragging: 0,
    raw: 0,
    releaseAt: -10,
    foamPow: 0,
    done: [false, false, false, false, false],
    doneAt: [-10, -10, -10, -10, -10],
    tab: 0,
    tabPos: 0,
    tabV: 0,
    pressRow: -1,
    pressAt: -10,
    releaseRowAt: -10,
    pressLast: 0,
  };
}

export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 2) return;
  d.mode = 2;
  d.s = loopScroll(d.t);
  d.v = 0;
  const f = frameAt(d.t);
  if (f.foam >= 0) {
    d.releaseAt = now - f.foam;
    d.foamPow = 1;
  }
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    return;
  }
  const idle = now - d.last >= RESUME && d.dragging === 0 && d.pressRow < 0;
  if (auto && idle) {
    // ease home: to the top, rows as they were, Home selected
    for (let i = 0; i < 5; i++) {
      if (d.done[i]) {
        d.done[i] = false;
        d.doneAt[i] = now;
      }
    }
    d.tab = 0;
  }
  if (d.dragging === 0) {
    if (d.s < 0 || d.s > MAX_SCROLL || (auto && idle)) {
      // spring back inside (or home)
      const target = auto && idle ? 0 : d.s < 0 ? 0 : MAX_SCROLL;
      const k = 170;
      const c = 2 * 0.86 * Math.sqrt(k);
      d.v += (-k * (d.s - target) - c * d.v) * dt;
      d.s += d.v * dt;
    } else {
      // momentum with friction
      d.s += d.v * dt;
      d.v *= Math.exp(-dt * 3.2);
      if (Math.abs(d.v) < 4) d.v = 0;
    }
  }
  // the tab tile springs to its tab
  {
    const k = 260;
    const c = 2 * 0.8 * Math.sqrt(k);
    d.tabV += (-k * (d.tabPos - d.tab) - c * d.tabV) * dt;
    d.tabPos += d.tabV * dt;
  }
  let lastDone = -10;
  for (let i = 0; i < 5; i++) lastDone = Math.max(lastDone, d.doneAt[i]);
  if (auto && idle && Math.abs(d.s) < 0.5 && Math.abs(d.v) < 5 && Math.abs(d.tabPos) < 0.01 && now - lastDone > 0.4) {
    d.mode = 0;
    d.t = 0;
  }
}

export function frameOf(d: Driver, now: number): Frame {
  'worklet';
  if (d.mode === 0) return frameAt(d.t);
  const done: number[] = [];
  for (let i = 0; i < 5; i++) {
    const k = smooth((now - d.doneAt[i]) / 0.35);
    done.push(d.done[i] ? k : 1 - k);
  }
  const pressK = d.pressRow >= 0 ? smooth((now - d.pressAt) / 0.1) : 1 - smooth((now - d.releaseRowAt) / 0.18);
  const foam = now - d.releaseAt < 1.6 ? now - d.releaseAt : -1;
  return { amb: d.amb, scroll: d.s, foam, foamPow: d.foamPow, done, tab: d.tabPos, pressRow: d.pressRow >= 0 ? d.pressRow : d.pressLast, pressK };
}
