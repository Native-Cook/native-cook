// Savings Future Sim — the loop and the touch driver. `sim` morphs the gauge's arc into the growth
// curve (and back); `year` is where the scrubber stands on it; `rate` is the gauge's setting.

import { PLAN } from './sfs-theme';

export const LOOP = 9.0;
export const RESUME = 2;
export const MORPH = 1.0;

export const T = {
  press: 1.0, // Simulate My Future is pressed …
  open: 1.15, // … the arc uncurls into the curve …
  scrub: [2.4, 5.4], // … the scrubber runs out to 30 years …
  back: [5.7, 6.5], // … and back to 10 …
  close: 7.0, // … then the curve curls back into the gauge
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
export function bump(t: number, at: number, dur: number) {
  'worklet';
  const p = (t - at) / dur;
  return p <= 0 || p >= 1 ? 0 : Math.sin(Math.PI * p);
}

/** The balance after `years` at `rate` % a year: the start compounded plus the deposits. */
export function balance(years: number, rate: number) {
  'worklet';
  const r = rate / 100;
  const g = Math.pow(1 + r, years);
  return PLAN.start * g + PLAN.monthly * 12 * ((g - 1) / r);
}

export type Frame = {
  amb: number;
  morph: number; // 0 gauge … 1 curve (the caller staggers it along the arc)
  open: boolean;
  morphAt: number; // seconds since it last turned
  year: number;
  rate: number;
  press: number;
  knob: number; // the gauge's knob held (0…1)
};

export function frameAt(t: number): Frame {
  'worklet';
  const open = t >= T.open && t < T.close;
  const at = t >= T.close ? T.close : t >= T.open ? T.open : -10;
  const morph = open ? seg(t, T.open, T.open + MORPH) : t >= T.close ? 1 - seg(t, T.close, T.close + MORPH) : 0;
  let year = 1;
  if (t >= T.scrub[0]) year = mix(1, 30, easeInOut(seg(t, T.scrub[0], T.scrub[1])));
  if (t >= T.back[0]) year = mix(30, 10, easeInOut(seg(t, T.back[0], T.back[1])));
  return { amb: t, morph, open, morphAt: t - at, year, rate: PLAN.rate, press: bump(t, T.press - 0.02, 0.3), knob: 0 };
}

export type Driver = {
  t: number;
  mode: 0 | 2;
  last: number;
  amb: number;
  open: boolean;
  morphAt: number;
  morphFrom: number;
  year: number;
  rate: number;
  pressing: number; // 1 button, 2 scrubbing, 3 the knob
  pressAt: number;
  releaseAt: number;
  closeAt: number; // a release after scrubbing curls the curve back here
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return { t, mode: 0, last: -10, amb: t, open: false, morphAt: -10, morphFrom: 0, year: 1, rate: PLAN.rate, pressing: 0, pressAt: -10, releaseAt: -10, closeAt: -10 };
}

export function morphOf(d: Driver, now: number) {
  'worklet';
  const k = seg(now, d.morphAt, d.morphAt + MORPH);
  return d.open ? mix(d.morphFrom, 1, k) : mix(d.morphFrom, 0, k);
}

export function setOpen(d: Driver, open: boolean, now: number) {
  'worklet';
  if (d.open === open) return;
  d.morphFrom = morphOf(d, now);
  d.open = open;
  d.morphAt = now;
}

export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 2) return;
  const f = frameAt(d.t);
  d.mode = 2;
  d.open = f.open;
  d.morphFrom = f.open ? 0 : 1;
  d.morphAt = now - f.morphAt;
  if (!f.open && f.morph <= 0) {
    d.morphFrom = 0;
    d.morphAt = now - 10;
  }
  d.year = f.year;
  d.rate = f.rate;
  d.closeAt = -10;
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    return;
  }
  if (d.closeAt > 0 && now >= d.closeAt && d.pressing === 0) {
    d.closeAt = -10;
    setOpen(d, false, now);
  }
  if (!auto || d.pressing > 0 || now - d.last < RESUME) return;
  // home: the gauge at 6,00 %, the scrubber at a year
  if (d.open) setOpen(d, false, now);
  d.rate += (PLAN.rate - d.rate) * Math.min(1, dt * 6);
  if (Math.abs(d.rate - PLAN.rate) < 0.005) d.rate = PLAN.rate;
  if (!d.open && morphOf(d, now) <= 0 && d.rate === PLAN.rate) {
    d.mode = 0;
    d.t = 0;
  }
  d.year = 1;
}

export function frameOf(d: Driver, now: number): Frame {
  'worklet';
  if (d.mode === 0) return frameAt(d.t);
  const press = d.pressing === 1 ? smooth((now - d.pressAt) / 0.15) : 1 - smooth((now - d.releaseAt) / 0.15);
  return { amb: d.amb, morph: morphOf(d, now), open: d.open, morphAt: now - d.morphAt, year: d.year, rate: d.rate, press: d.pressing === 1 || now - d.releaseAt < 0.15 ? press : 0, knob: d.pressing === 3 ? 1 : 0 };
}
