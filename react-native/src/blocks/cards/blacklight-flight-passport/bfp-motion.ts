// Blacklight Flight Passport — the timeline (analytic, frame LOOP equals frame 0) and the driver.

import { ROUTES } from './bfp-theme';

export const LOOP = 9.0;
export const RESUME = 2;

export const T = {
  fade: [0.5, 0.85], // the routes lift off the map …
  draw: [0.9, 3.2], // … and fly again one after another, the counters rolling up
  press: [3.55, 3.72], // Blacklight is pressed …
  uvOn: [3.72, 4.55], // … and its light spreads out from the button over everything
  glow: [4.4, 5.2], // the hidden ink wakes up
  pressOff: [6.75, 6.9],
  uvOff: [6.9, 7.6], // the light falls back into the button
} as const;

/** UV switched on fully (where a tap on the button plays to) and fully off again. */
export const UV_PEAK = T.glow[1];

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

const N = ROUTES.length;
const ROUTE_TIME = 0.55;

/** Route r's drawn share at loop time t (1 at rest: the map shows every route). */
export function routeAt(r: number, t: number) {
  'worklet';
  if (t < T.fade[0] || t >= T.draw[1] + 0.2) return 1;
  if (t < T.draw[0]) return 1; // the old routes fade out whole (routeFade)
  const start = T.draw[0] + (r / (N - 1)) * (T.draw[1] - T.draw[0] - ROUTE_TIME);
  return easeInOut(seg(t, start, start + ROUTE_TIME));
}
/** While re-drawing, the old routes fade rather than un-draw. */
export function routeFade(t: number) {
  'worklet';
  if (t >= T.fade[0] && t < T.draw[0]) return 1 - smooth(seg(t, T.fade[0], T.fade[1]));
  return 1;
}

export type Frame = {
  t: number;
  routes: number[];
  fade: number;
  count: number; // counters 0 … 1
  press: number;
  uv: number; // the blacklight's reach 0 … 1 (spreads from the button)
  glow: number; // hidden ink 0 … 1
};

export function frameAt(t: number): Frame {
  'worklet';
  const routes: number[] = [];
  for (let r = 0; r < N; r++) routes.push(routeAt(r, t));
  const counting = t >= T.fade[0] && t < T.draw[1] + 0.3;
  const count = counting ? (t < T.draw[0] ? 1 - smooth(seg(t, T.fade[0], T.fade[1])) : easeOutCubic(seg(t, T.draw[0], T.draw[1]))) : 1;
  const pOn = seg(t, T.press[0], T.press[1]);
  const pOff = seg(t, T.pressOff[0], T.pressOff[1]);
  const press = pOn > 0 && pOn < 1 ? Math.sin(Math.PI * pOn) : pOff > 0 && pOff < 1 ? Math.sin(Math.PI * pOff) : 0;
  const uv = t < T.uvOff[0] ? easeInOut(seg(t, T.uvOn[0], T.uvOn[1])) : 1 - easeInOut(seg(t, T.uvOff[0], T.uvOff[1]));
  const glow = t < T.uvOff[0] ? smooth(seg(t, T.glow[0], T.glow[1])) : 1 - smooth(seg(t, T.uvOff[0], T.uvOff[0] + 0.3));
  return { t, routes, fade: routeFade(t), count, press, uv, glow };
}

// ------------------------------------------------------------------ the driver

export type Driver = {
  t: number;
  mode: 0 | 1 | 2;
  stop: number;
  rate: number;
  last: number;
  amb: number;
  torch: number; // the finger's blacklight spot 0 … 1 (follows torchAim)
  torchAim: number;
  tx: number; // where the spot is (pt)
  ty: number;
  pressAim: number;
  pressP: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return { t, mode: 0, stop: 0, rate: 1, last: -10, amb: t, torch: 0, torchAim: 0, tx: 200, ty: 400, pressAim: 0, pressP: 0 };
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
    if (auto && d.torchAim === 0 && now - d.last >= RESUME) d.mode = 0;
  } else if (auto) d.t = (d.t + dt) % LOOP;
  d.amb = (d.amb + dt) % LOOP;
  d.torch = follow(d.torch, d.torchAim, d.torchAim > 0 ? 14 : 9, dt);
  d.pressP = follow(d.pressP, d.pressAim, d.pressAim > 0 ? 40 : 16, dt);
  return d.t;
}

export function isUv(t: number) {
  'worklet';
  return t >= T.uvOn[0] + 0.3 && t < T.uvOff[0];
}

export function touched(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode !== 0) return;
  d.rate = 1;
  if (d.t >= T.press[0] && d.t < UV_PEAK) {
    d.mode = 1;
    d.stop = UV_PEAK;
  } else if (d.t >= T.pressOff[0] && d.t < T.uvOff[1]) {
    d.mode = 1;
    d.stop = LOOP;
  } else d.mode = 2;
}

/** The button: switch the blacklight on (from daylight) or off (under it). */
export function toggleUv(d: Driver) {
  'worklet';
  if (isUv(d.t) || (d.t >= UV_PEAK && d.t < T.uvOff[0])) {
    d.t = T.pressOff[0];
    d.stop = LOOP;
    d.rate = 1.25;
  } else {
    d.t = d.t >= T.draw[1] + 0.3 || d.t < T.fade[0] ? T.press[0] : d.t;
    d.stop = UV_PEAK;
    d.rate = 1.25;
  }
  d.mode = 1;
  return isUv(d.stop - 0.01);
}
