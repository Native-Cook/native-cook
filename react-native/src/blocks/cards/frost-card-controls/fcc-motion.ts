// Frost Card Controls — the card's story as events (freeze, melt, shred, reissue) and the frame they
// give at any moment. autoPlay places the events on a 9 s loop (frame 9 s equals frame 0); a finger
// places them on the clock instead.

import { UI } from './fcc-theme';

export const LOOP = 9.0;
export const RESUME = 2;

export const GROW = 1.25; // frost spreading over the card
export const MELT = 1.15; // ice receding
export const DRIPS = 1.3;
export const SHRED = 1.15; // cut into strips and falling
export const REISSUE = 0.9; // a new card dropping in

/** Events: when each happened (seconds on the timeline in use, −99 never) and where the frost started. */
export type Events = { freeze: number; cx: number; cy: number; melt: number; shred: number; reissue: number; out: number };

/** The loop's events: freeze 1.12, melt 3.72, shred 5.45, reissue 6.75. */
export const AUTO: Events = { freeze: 1.12, cx: 46, cy: 186, melt: 3.72, shred: 5.45, reissue: 6.75, out: -99 };
export const PRESSES = [
  [1.0, 1.12, 0],
  [3.6, 3.72, 0],
  [5.3, 5.42, 2],
] as const;

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
  return p * p;
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
export function spring(tau: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const zw = damping * w;
  const wd = w * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-zw * tau) * (Math.cos(wd * tau) + (zw / wd) * Math.sin(wd * tau));
}

export type Frame = {
  frost: number; // spread 0 … 1.2 (past 1 the whole card is iced)
  melt: number; // 0 … 1
  drips: number; // −1 none, else 0 … 1
  cx: number; // where the frost started (card space)
  cy: number;
  frozenLabel: number; // tile shows "Unfreeze" 0 … 1
  spin: number; // snowflake turn, degrees
  shred: number; // −1 none, else 0 … 1
  card: number; // the card present 0 … 1 (0 while gone between shred and reissue)
  drop: number; // new card arriving (spring, 0 … 1, 1 settled)
  shine: number; // −1 none
  out: number; // card flown off to the right (reissue tile) 0 … 1
  press: number[]; // tile presses
};

/** The frame at time `t` on a timeline whose events are `e`. */
export function frameFor(t: number, e: Events, presses: number[]): Frame {
  'worklet';
  const fz = t - e.freeze;
  const ml = t - e.melt;
  const meltOn = e.melt > e.freeze && ml >= 0;
  const frozen = fz >= 0 && !(meltOn && ml >= MELT);
  const frost = fz >= 0 ? 1.2 * easeOut(fz / GROW) : 0;
  const melt = meltOn ? easeInOut(ml / MELT) : 0;
  const drips = meltOn && ml > 0.15 && ml < 0.15 + DRIPS ? (ml - 0.15) / DRIPS : -1;
  const sh = t - e.shred;
  const shred = e.shred > -98 && sh >= 0 && sh < SHRED ? sh / SHRED : -1;
  const re = t - e.reissue;
  const reOn = e.reissue > -98 && re >= 0;
  const gone = e.shred > -98 && sh >= SHRED && !(reOn && e.reissue > e.shred);
  const flyOut = e.out > -98 ? easeIn(seg(t, e.out, e.out + 0.4)) : 0;
  const shine = reOn && re > 0.55 && re < 1.15 ? (re - 0.55) / 0.6 : -1;
  return {
    frost: frozen || (meltOn && ml < MELT) ? frost : 0,
    melt,
    drips,
    cx: e.cx,
    cy: e.cy,
    frozenLabel: frozen ? smooth(seg(fz, 0.2, 0.5)) * (meltOn ? 1 - smooth(ml / 0.3) : 1) : 0,
    spin: fz >= 0 ? 360 * easeOut(fz / 0.8) - (meltOn ? 360 * easeOut(ml / 0.8) : 0) : 0,
    shred,
    card: gone || (reOn && re < 0) ? 0 : 1,
    drop: reOn ? spring(re, 0.55, 0.7) : 1,
    shine,
    out: flyOut,
    press: presses,
  };
}

/** The loop's frame at loop time t. */
export function frameAt(t: number): Frame {
  'worklet';
  const press = [0, 0, 0];
  for (let i = 0; i < PRESSES.length; i++) {
    const p = PRESSES[i];
    const k = seg(t, p[0], p[1] + 0.14);
    if (k > 0 && k < 1) press[p[2]] = Math.sin(Math.PI * k);
  }
  return frameFor(t, AUTO, press);
}

// ------------------------------------------------------------------ the driver

export type Driver = {
  t: number; // loop time (autoPlay)
  mode: 0 | 2; // autoPlay, or the clock (a finger has set the events)
  last: number;
  amb: number;
  ev: Events; // events on the clock (mode 2)
  frozen: number; // 1 while iced (mode 2)
  busy: number; // clock time until which a shred/reissue is still playing
  press: number[];
  pressAim: number[];
  tiltX: number;
  tiltY: number;
  aimX: number;
  aimY: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return {
    t,
    mode: 0,
    last: -10,
    amb: t,
    ev: { freeze: -99, cx: 46, cy: 186, melt: -99, shred: -99, reissue: -99, out: -99 },
    frozen: 0,
    busy: -1,
    press: [0, 0, 0],
    pressAim: [0, 0, 0],
    tiltX: 0,
    tiltY: 0,
    aimX: 0,
    aimY: 0,
  };
}

function follow(cur: number, aim: number, k: number, dt: number) {
  'worklet';
  return cur + (aim - cur) * (1 - Math.exp(-dt * k));
}

/** Where the loop stands (to hand over to the clock): iced or not. */
function loopFrozen(t: number) {
  'worklet';
  return t >= AUTO.freeze && t < AUTO.melt + MELT * 0.5;
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  for (let i = 0; i < 3; i++) d.press[i] = follow(d.press[i], d.pressAim[i], d.pressAim[i] > 0 ? 40 : 16, dt);
  d.tiltX = follow(d.tiltX, d.aimX, 10, dt);
  d.tiltY = follow(d.tiltY, d.aimY, 10, dt);
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    return;
  }
  // hand back to the loop once idle and settled, at the point of the loop that looks the same
  if (auto && now - d.last >= RESUME && now > d.busy) {
    d.mode = 0;
    d.t = d.frozen ? 2.6 : 8.2;
  }
}

/** A finger came down: from here the events run on the clock, starting from what the loop shows. */
export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 2) return;
  const t = d.t;
  d.mode = 2;
  const off = now - t;
  d.frozen = loopFrozen(t) ? 1 : 0;
  d.ev = {
    freeze: d.frozen ? Math.min(AUTO.freeze, t - GROW) + off : -99,
    cx: AUTO.cx,
    cy: AUTO.cy,
    melt: -99,
    shred: -99,
    reissue: -99,
    out: -99,
  };
  // a shred or reissue in progress finishes on the clock
  if (t >= AUTO.shred && t < AUTO.reissue + REISSUE + 0.4) {
    d.ev.shred = AUTO.shred + off;
    d.ev.reissue = AUTO.reissue + off;
    d.busy = d.ev.reissue + REISSUE + 0.4;
  }
}

export function freezeAt(d: Driver, now: number, cx: number, cy: number) {
  'worklet';
  d.ev = { ...d.ev, freeze: now, cx, cy, melt: -99 };
  d.frozen = 1;
}
export function meltNow(d: Driver, now: number) {
  'worklet';
  d.ev = { ...d.ev, melt: now };
  d.frozen = 0;
}
export function shredNow(d: Driver, now: number) {
  'worklet';
  d.ev = { ...d.ev, shred: now, reissue: now + SHRED + 0.2, out: -99, freeze: -99, melt: -99 };
  d.frozen = 0;
  d.busy = now + SHRED + 0.2 + REISSUE + 0.4;
}
export function reissueNow(d: Driver, now: number) {
  'worklet';
  d.ev = { ...d.ev, out: now, reissue: now + 0.42, shred: -99, freeze: -99, melt: -99 };
  d.frozen = 0;
  d.busy = now + 0.42 + REISSUE + 0.4;
}

/** The frame the driver shows now. */
export function liveFrame(d: Driver, now: number): Frame {
  'worklet';
  if (d.mode === 0) {
    const f = frameAt(d.t);
    for (let i = 0; i < 3; i++) f.press[i] = Math.max(f.press[i], d.press[i]);
    return f;
  }
  const f = frameFor(now, d.ev, [d.press[0], d.press[1], d.press[2]]);
  // a new card after "out" replaces the old one
  if (d.ev.out > -98 && now >= d.ev.reissue) f.out = 0;
  return f;
}

export const CARD_BOX = UI.card;
