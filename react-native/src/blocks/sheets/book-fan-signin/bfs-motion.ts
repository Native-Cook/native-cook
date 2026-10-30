// Book Fan Sign-in — the timeline (analytic, frame LOOP equals frame 0) and the touch driver.

import { B, WALL } from './bfs-theme';

export const LOOP = 9.0;
export const RESUME = 2;
/** The sheet is up and settled: where a tap on Sign in plays to. */
export const PEAK = 3.4;

export const T = {
  press: [1.3, 1.45], // Sign in sinks
  open: 1.45, // the page blurs, the covers fly, the sheet rises
  blur: [1.45, 2.05],
  sheet: 1.55, // spring
  flight: 1.5, // first cover leaves; the others follow FLIGHT_STAGGER apart
  icon: 1.95,
  logo: [2.05, 2.55], // the icon's mark draws itself
  tag: [2.1, 2.45],
  title: [2.15, 2.55],
  title2: [2.25, 2.65],
  sub: [2.35, 2.75],
  cta: [2.4, 2.85],
  shimmer: [3.0, 3.7],
  close: 6.6, // the way home
  closeEnd: 7.5,
} as const;

export const FLIGHTS = 6;
const FLIGHT_STAGGER = 0.05;
const FLIGHT = 0.68;
const BACK_STAGGER = 0.04;
const BACK = 0.55;
const SHEET_DROP = 0.45;

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
export function easeOut(x: number) {
  'worklet';
  const u = 1 - clamp01(x);
  return 1 - u * u * u * u * u;
}
export function easeIn(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * p;
}
export function easeInOutCubic(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}
export function smooth(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * (3 - 2 * p);
}
export function spring(tau: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const zw = damping * w;
  const wd = w * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-zw * tau) * (Math.cos(wd * tau) + (zw / wd) * Math.sin(wd * tau));
}

// ------------------------------------------------------------------ the wall of covers

const RAD = (WALL.angle * Math.PI) / 180;
const PX = WALL.w + WALL.gapX;
const PY = WALL.h + WALL.gapY;
/** Along a row (right and up) and down a column, in screen pt. */
export const U = { x: Math.cos(RAD) * PX, y: Math.sin(RAD) * PX };
export const V = { x: -Math.sin(RAD) * PY, y: Math.cos(RAD) * PY };

/** The wall drifts one cover up its columns per loop (so the loop closes); `extra` is the finger's scroll (pt). */
export function wallShift(amb: number, extra: number) {
  'worklet';
  const s = -(amb / LOOP) * PY + extra;
  return ((s % PY) + PY) % PY - PY;
}

/** Centre of wall cell (i, j) for a given shift along the columns. */
export function cellAt(i: number, j: number, shift: number) {
  'worklet';
  const ci = i - (WALL.cols - 1) / 2;
  const cj = j - (WALL.rows - 1) / 2;
  const k = shift / PY;
  return { x: WALL.cx + ci * U.x + (cj + k) * V.x, y: WALL.cy + ci * U.y + (cj + k) * V.y };
}

/** The cells whose covers fly into the sheet (left to right), and which cover each one shows. */
export const FLYING = [
  { i: 1, j: 3 },
  { i: 2, j: 2 },
  { i: 3, j: 3 },
  { i: 4, j: 2 },
  { i: 5, j: 3 },
  { i: 6, j: 2 },
];
export function coverOf(i: number, j: number) {
  'worklet';
  return (i * 3 + j * 7) % 10;
}

/** Strip slot k in the sheet's header: centre and turn. */
export function slot(k: number) {
  'worklet';
  return { x: B.strip.x0 + k * B.strip.pitch, y: B.strip.y + 30 - k * 5, angle: B.strip.angle };
}

/** Flight k's progress at loop time t: 0 on the wall … 1 in the sheet (−1 not moving, use rest). */
export function flightAt(k: number, t: number) {
  'worklet';
  if (t < T.close) return easeInOutCubic(seg(t, T.flight + k * FLIGHT_STAGGER, T.flight + k * FLIGHT_STAGGER + FLIGHT));
  const s = T.close + (FLIGHTS - 1 - k) * BACK_STAGGER;
  return 1 - easeInOutCubic(seg(t, s, s + BACK));
}

// ------------------------------------------------------------------ the frame

export type Frame = {
  t: number;
  press: number;
  blur: number; // page blur 0 … 1 (it sweeps down the page)
  dim: number;
  sheet: number; // 0 down (off screen) … 1 up (springs a little past)
  flights: number[];
  icon: number;
  logo: number;
  tag: number;
  title: number;
  title2: number;
  sub: number;
  cta: number;
  shimmer: number; // −1 none
};

export function frameAt(t: number): Frame {
  'worklet';
  const fwd = t < T.close;
  const pIn = seg(t, T.press[0], T.press[0] + 0.1);
  const pOut = seg(t, T.press[1], T.press[1] + 0.14);
  const press = t >= T.press[0] && t < T.press[1] + 0.14 ? easeOut(pIn) * (1 - easeOut(pOut)) : 0;
  const back = seg(t, T.close, T.closeEnd);
  const out = (x: number) => {
    'worklet';
    return fwd ? x : x * (1 - easeOut(back * 2));
  };
  const flights: number[] = [];
  for (let k = 0; k < FLIGHTS; k++) flights.push(flightAt(k, t));
  const sh = seg(t, T.shimmer[0], T.shimmer[1]);
  return {
    t,
    press,
    blur: fwd ? smooth(seg(t, T.blur[0], T.blur[1])) : 1 - smooth(seg(t, T.close + 0.15, T.closeEnd)),
    dim: fwd ? smooth(seg(t, T.blur[0], T.blur[1])) : 1 - smooth(seg(t, T.close + 0.1, T.closeEnd - 0.1)),
    sheet: fwd ? spring(t - T.sheet, 0.55, 0.82) : 1 - easeIn(seg(t, T.close, T.close + SHEET_DROP)),
    flights,
    icon: out(spring(t - T.icon, 0.45, 0.6)),
    logo: out(easeInOutCubic(seg(t, T.logo[0], T.logo[1]))),
    tag: out(easeOut(seg(t, T.tag[0], T.tag[1]))),
    title: out(easeOut(seg(t, T.title[0], T.title[1]))),
    title2: out(easeOut(seg(t, T.title2[0], T.title2[1]))),
    sub: out(easeOut(seg(t, T.sub[0], T.sub[1]))),
    cta: out(easeOut(seg(t, T.cta[0], T.cta[1]))),
    shimmer: sh > 0 && sh < 1 ? sh : -1,
  };
}

/** Rest on the home page covers the start and the end of the loop. */
export function inHome(t: number) {
  'worklet';
  return t < T.press[0] || t >= T.closeEnd;
}
export function inSheet(t: number) {
  'worklet';
  return t >= PEAK - 0.6 && t < T.close;
}

// ------------------------------------------------------------------ the driver

export type Driver = {
  t: number;
  mode: 0 | 1 | 2; // autoPlay, playing to `stop`, parked
  stop: number;
  rate: number;
  last: number;
  amb: number;
  scroll: number; // the finger's scroll of the wall (pt) …
  scrollV: number; // … and its glide
  drag: number; // the sheet pulled down by the finger (pt)
  dragV: number;
  dragging: number;
  ctaAim: number;
  ctaP: number;
  pressAim: number; // Sign in under a finger
  pressP: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return { t, mode: 0, stop: 0, rate: 1, last: -10, amb: t, scroll: 0, scrollV: 0, drag: 0, dragV: 0, dragging: 0, ctaAim: 0, ctaP: 0, pressAim: 0, pressP: 0 };
}

function follow(cur: number, aim: number, k: number, dt: number) {
  'worklet';
  return cur + (aim - cur) * (1 - Math.exp(-dt * k));
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  if (d.mode === 1) {
    d.t += dt * d.rate;
    if (d.t >= d.stop) {
      d.t = d.stop >= LOOP ? 0 : d.stop;
      d.mode = 2;
      d.rate = 1;
      d.last = now;
    }
  } else if (d.mode === 2) {
    if (auto && d.dragging === 0 && now - d.last >= RESUME) d.mode = 0;
  } else if (auto) d.t = (d.t + dt) % LOOP;
  d.amb = (d.amb + dt) % LOOP;
  // the wall glides on after a flick, slowing down
  if (d.dragging === 0) {
    d.scroll += d.scrollV * dt;
    d.scrollV *= Math.exp(-dt * 2.4);
  }
  // the sheet pulled down springs back when let go (unless it was dismissed)
  if (d.dragging === 0 && d.drag !== 0) {
    const k = 260;
    const c = 2 * 0.9 * Math.sqrt(k);
    d.dragV += (-k * d.drag - c * d.dragV) * dt;
    d.drag += d.dragV * dt;
    if (Math.abs(d.drag) < 0.05 && Math.abs(d.dragV) < 1) {
      d.drag = 0;
      d.dragV = 0;
    }
  }
  d.ctaP = follow(d.ctaP, d.ctaAim, d.ctaAim > 0 ? 40 : 16, dt);
  d.pressP = follow(d.pressP, d.pressAim, d.pressAim > 0 ? 40 : 16, dt);
  return d.t;
}

export function touched(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode !== 0) return;
  d.rate = 1;
  if (d.t >= T.press[0] && d.t < PEAK) {
    d.mode = 1;
    d.stop = PEAK;
  } else if (d.t >= T.close && d.t < T.closeEnd) {
    d.mode = 1;
    d.stop = LOOP;
  } else d.mode = 2;
}

export function openSheet(d: Driver) {
  'worklet';
  d.t = T.open;
  d.mode = 1;
  d.rate = 1;
  d.stop = PEAK;
}

/** Dismiss: the way home, 1.4× faster than the demo, starting from wherever the finger left the sheet. */
export function closeSheet(d: Driver) {
  'worklet';
  if (!inSheet(d.t) && !(d.t >= PEAK && d.t < T.close)) return false;
  d.t = T.close;
  d.mode = 1;
  d.rate = 1.4;
  d.stop = LOOP;
  return true;
}
