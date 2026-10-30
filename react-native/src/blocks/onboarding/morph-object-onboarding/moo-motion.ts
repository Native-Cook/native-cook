// Morph Object Onboarding — the timeline and the pager.
// Everything follows one number, the page position p (0 … 2): the object's Blender frame, the
// text, the dots and the button. autoPlay drives p from the loop time; a finger drives it directly.

export const LOOP = 9.0;
export const RESUME = 2;

/** autoPlay: hold, morph, hold, morph, hold, rewind. [start, end, from page, to page]. */
export const T = {
  moves: [
    [1.5, 2.6, 0, 1],
    [4.6, 5.7, 1, 2],
    [7.6, 8.7, 2, 0],
  ] as const,
  /** where each page's hold starts, to resume the loop from the page the finger left */
  holdAt: [0, 2.6, 5.7],
};

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
/** On-screen movement, close to cubic-bezier(0.77, 0, 0.175, 1). */
export function easeInOut(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 16 * p * p * p * p * p : 1 - Math.pow(-2 * p + 2, 5) / 2;
}
/** A softer in-out (cubic) for the morph, so the middle frames are not rushed past. */
export function easeInOutCubic(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}
export function wobble(tau: number, period: number, decay: number) {
  'worklet';
  if (tau <= 0) return 0;
  return Math.exp(-tau / decay) * Math.cos((2 * Math.PI * tau) / period);
}

/** The page position at loop time t. */
export function pageAt(t: number) {
  'worklet';
  for (let i = 0; i < T.moves.length; i++) {
    const m = T.moves[i];
    if (t < m[0]) return i === 0 ? 0 : T.moves[i - 1][3];
    if (t < m[1]) return mix(m[2], m[3], easeInOutCubic(seg(t, m[0], m[1])));
  }
  return 0;
}

/** Arrival moments in the loop (sparkle burst + card bump): page and time. */
export function arrivalAt(t: number) {
  'worklet';
  for (let i = 0; i < 2; i++) {
    const tau = t - T.moves[i][1];
    if (tau >= 0 && tau < 1) return tau;
  }
  return -1;
}

// ------------------------------------------------------------------ the driver

export type Driver = {
  t: number;
  mode: 0 | 2; // 0 autoPlay, 2 the finger (or its spring) has the pager
  last: number;
  amb: number; // ambient clock (float, twinkle), wraps at LOOP
  p: number; // page position under the finger
  v: number; // its velocity, pages per second
  aim: number; // page the spring is heading for (−1 while the finger holds it)
  dragFrom: number; // p when the drag began
  arriveAt: number; // clock time of the last arrival (−1 none)
  hopAt: number; // clock time of a tap on the object (−1 none)
  press: number; // the button under a finger, 0 … 1
  pressAim: number;
  skipping: number; // 1 while Skip runs through to the last page
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return { t, mode: 0, last: -10, amb: t, p: pageAt(t), v: 0, aim: -1, dragFrom: 0, arriveAt: -1, hopAt: -1, press: 0, pressAim: 0, skipping: 0 };
}

/** Page position with a rubber band past the first and last page. */
export function band(p: number) {
  'worklet';
  if (p < 0) return -(1 - 1 / (1 - p * 1.6)) / 1.6;
  if (p > 2) return 2 + (1 - 1 / (1 + (p - 2) * 1.6)) / 1.6;
  return p;
}

/** Advance; returns the page position to draw. `arrived` is set when the spring settles on a page. */
export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  d.press += (d.pressAim - d.press) * (1 - Math.exp(-dt * (d.pressAim > 0 ? 40 : 16)));
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    d.p = pageAt(d.t);
    return { p: d.p, arrived: -1 };
  }
  let arrived = -1;
  if (d.aim >= 0) {
    // a spring to the chosen page that keeps the finger's speed (response ~0.42 s, a hint of bounce)
    const k = d.skipping ? 160 : 110;
    const c = 2 * (d.skipping ? 0.95 : 0.78) * Math.sqrt(k);
    const n = 2;
    const h = dt / n;
    for (let i = 0; i < n; i++) {
      d.v += (-k * (d.p - d.aim) - c * d.v) * h;
      d.p += d.v * h;
    }
    if (Math.abs(d.p - d.aim) < 0.002 && Math.abs(d.v) < 0.02) {
      d.p = d.aim;
      d.v = 0;
      arrived = d.aim;
      d.aim = -1;
      d.skipping = 0;
    }
  }
  if (auto && d.aim < 0 && now - d.last >= RESUME && Math.abs(d.p - Math.round(d.p)) < 0.001) {
    // hand the pager back to the loop at the hold of the page it rests on
    const pg = Math.round(d.p);
    d.t = T.holdAt[pg] + 0.001;
    d.mode = 0;
  }
  return { p: d.p, arrived };
}

/** A finger came down on the pager: it takes the page position from wherever it is. */
export function grab(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 0) {
    d.p = pageAt(d.t);
    d.v = 0;
  }
  d.mode = 2;
  d.aim = -1;
  d.dragFrom = d.p;
}

/** Let go: the page the flick points at (velocity in pages/s), at most one page away. */
export function release(d: Driver, vPages: number) {
  'worklet';
  const from = Math.round(d.dragFrom);
  let target = Math.round(d.p + vPages * 0.18);
  target = Math.max(from - 1, Math.min(from + 1, target));
  d.aim = Math.max(0, Math.min(2, target));
  d.v = vPages;
}

/** Go to a page (Continue, Skip, dots): a spring from where it is. */
export function goTo(d: Driver, page: number, now: number, skip = false) {
  'worklet';
  if (d.mode === 0) {
    d.p = pageAt(d.t);
    d.v = 0;
  }
  d.mode = 2;
  d.last = now;
  d.aim = page;
  d.skipping = skip ? 1 : 0;
}
