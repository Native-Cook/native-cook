// Sun UV Buddy — the timeline and the driver. The hour under the stem (a float, 0 = 09:00) and the
// day choose the UV; the sun's size follows it on a soft spring (a jelly that wobbles), its face
// and props follow the UV directly.

import { DAYS } from './suv-theme';

export const LOOP = 9.0;
export const RESUME = 2;
export const REST = { day: 2, hour: 3 }; // Thu, 12:00 (the pin)

export const T = {
  toEvening: [1.0, 2.4], // the stem slides to 18:00 …
  toNoon: [3.0, 3.9], // … and back to 12:00
  today: 4.4, // the day switches to Today, the hour to 13:00
  hourUp: [4.45, 5.0],
  thu: 6.6, // back to Thursday, 12:00
  hourDown: [6.6, 7.15],
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
export function easeInOut(x: number) {
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

/** The loop: hour (float), the day the bars come from and go to, and when they changed. */
export function loopState(t: number) {
  'worklet';
  let hour = REST.hour;
  if (t < T.toNoon[0]) hour = mix(3, 9, easeInOut(seg(t, T.toEvening[0], T.toEvening[1])));
  else if (t < T.today) hour = mix(9, 3, easeInOut(seg(t, T.toNoon[0], T.toNoon[1])));
  else if (t < T.thu) hour = mix(3, 4, easeInOut(seg(t, T.hourUp[0], T.hourUp[1])));
  else hour = mix(4, 3, easeInOut(seg(t, T.hourDown[0], T.hourDown[1])));
  if (t >= T.today && t < T.thu) return { hour, from: 2, to: 0, at: T.today };
  if (t >= T.thu) return { hour, from: 0, to: 2, at: T.thu };
  return { hour, from: 2, to: 2, at: -10 };
}

/** Bar i's value while the bars move from one day to another `tau` seconds in (staggered springs). */
export function barValue(i: number, from: number, to: number, tau: number) {
  'worklet';
  if (from === to) return DAYS[to][i];
  const k = spring(tau - i * 0.035, 0.42, 0.62);
  return mix(DAYS[from][i], DAYS[to][i], k);
}

/** UV at a float hour under the bars as they are now. */
export function uvAt(hour: number, from: number, to: number, tau: number) {
  'worklet';
  const h = Math.max(0, Math.min(10, hour));
  const i = Math.floor(h);
  const j = Math.min(10, i + 1);
  return mix(barValue(i, from, to, tau), barValue(j, from, to, tau), h - i);
}

/** The sun's size for a UV (× its pin radius). */
export function sizeFor(uv: number) {
  'worklet';
  if (uv <= 3) return mix(0.62, 1, smooth(uv / 3) * 0.6 + (uv / 3) * 0.4);
  return 1 + 0.06 * Math.min(2, uv - 3);
}

// ------------------------------------------------------------------ the driver

export type Driver = {
  t: number;
  mode: 0 | 2;
  last: number;
  amb: number;
  hour: number;
  hourV: number;
  hourAim: number;
  dragging: number;
  from: number; // bars moving from this day …
  to: number; // … to this one
  at: number; // since (clock or loop time)
  size: number; // the sun's size spring
  sizeV: number;
  squishAt: number; // a tap on the sun (clock, −1 none)
  lookX: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return { t, mode: 0, last: -10, amb: t, hour: REST.hour, hourV: 0, hourAim: REST.hour, dragging: 0, from: 2, to: 2, at: -10, size: 1, sizeV: 0, squishAt: -1, lookX: 0 };
}

/** Advance; returns the time base the bars' change is measured on (loop time or clock). */
export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  let base = now;
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    const s = loopState(d.t);
    // the loop's stem speed, so it bends and the bars jump as they do under a finger
    d.hourV = dt > 0 ? mix(d.hourV, (s.hour - d.hour) / dt, 0.5) : 0;
    d.hour = s.hour;
    d.from = s.from;
    d.to = s.to;
    d.at = s.at;
    base = d.t;
  } else {
    if (d.dragging === 0) {
      const k = 120;
      const c = 2 * 0.8 * Math.sqrt(k);
      d.hourV += (-k * (d.hour - d.hourAim) - c * d.hourV) * dt;
      d.hour += d.hourV * dt;
    }
    if (auto && d.dragging === 0 && now - d.last >= RESUME) {
      // drift home to Thursday noon, then hand back to the loop
      if (d.to !== 2) {
        d.from = d.to;
        d.to = 2;
        d.at = now;
      }
      d.hourAim = REST.hour;
      if (Math.abs(d.hour - REST.hour) < 0.01 && now - d.at > 0.9) {
        d.mode = 0;
        d.t = 7.6;
      }
    }
  }
  // the sun's size: a soft, slightly wobbly spring toward what the UV asks for
  const uv = uvAt(d.hour, d.from, d.to, base - d.at);
  const k = 170;
  const c = 2 * 0.32 * Math.sqrt(k);
  d.sizeV += (-k * (d.size - sizeFor(uv)) - c * d.sizeV) * dt;
  d.size += d.sizeV * dt;
  return base;
}

export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 2) return;
  d.mode = 2;
  // carry the loop's bar change over to the clock
  if (d.at > -9) d.at = now - (d.t - d.at);
  d.hourAim = Math.round(d.hour);
  d.hourV = 0;
}

export function pickDay(d: Driver, day: number, now: number) {
  'worklet';
  if (day === d.to) return false;
  d.from = d.to;
  d.to = day;
  d.at = now;
  return true;
}
