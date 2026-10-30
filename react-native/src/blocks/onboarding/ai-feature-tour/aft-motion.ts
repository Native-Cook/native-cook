// AI Feature Tour — the loop and the touch driver. Each row's icon has its own little job (a 1.3 s
// act that starts and ends at its resting look); Continue sends the icons into the button.

export const LOOP = 9.0;
export const RESUME = 2;
export const ICONS = 5; // the sixth row sits under the button
export const ACT = 1.3;

export const T = {
  act0: 0.3, // row i acts from act0 + i · ACT
  press: 7.0, // Continue is pressed …
  fly: 7.1, // … the icons fly into it (FLY_GAP apart, FLY_DUR each) …
  sparkle: 7.68, // … and become one sparkle
  back: 8.05, // the icons come back to their places (BACK_GAP apart)
} as const;
export const FLY_GAP = 0.05;
export const FLY_DUR = 0.55;
export const BACK_GAP = 0.07;

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
export function easeIn(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * p;
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
/** 0 → 1 → 0 over `dur` seconds from `at`. */
export function bump(t: number, at: number, dur: number) {
  'worklet';
  const p = (t - at) / dur;
  return p <= 0 || p >= 1 ? 0 : Math.sin(Math.PI * p);
}

export type Frame = {
  amb: number;
  act: number[]; // per icon: its act's progress 0…1, or −1 at rest
  fly: number; // seconds since Continue sent the icons off (−1 none)
  press: number; // 0…1 the button pressed in
};

export function frameAt(t: number): Frame {
  'worklet';
  const act: number[] = [];
  for (let i = 0; i < ICONS; i++) {
    const p = (t - (T.act0 + i * ACT)) / ACT;
    act.push(p >= 0 && p < 1 ? p : -1);
  }
  return { amb: t, act, fly: t >= T.press ? t - T.press : -1, press: bump(t, T.press - 0.02, 0.22) };
}

/** Where icon i is in a flight τ seconds after the press: 0 home … 1 in the button; then back. */
export function flight(i: number, tau: number) {
  'worklet';
  const out = easeIn(seg(tau, T.fly - T.press + i * FLY_GAP, T.fly - T.press + i * FLY_GAP + FLY_DUR));
  const backAt = T.back - T.press + i * BACK_GAP;
  const back = tau >= backAt ? spring(tau - backAt, 0.5, 0.6) : 0;
  return { out, back, gone: tau >= T.fly - T.press + i * FLY_GAP + FLY_DUR && tau < backAt };
}

export type Driver = {
  t: number;
  mode: 0 | 2;
  last: number;
  amb: number;
  actAt: number[];
  flyAt: number;
  pressAt: number;
  pressing: number;
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return { t, mode: 0, last: -10, amb: t, actAt: [-10, -10, -10, -10, -10], flyAt: -10, pressAt: -10, pressing: 0 };
}

export function takeOver(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode === 2) return;
  d.mode = 2;
  const f = frameAt(d.t);
  for (let i = 0; i < ICONS; i++) d.actAt[i] = f.act[i] >= 0 ? now - f.act[i] * ACT : -10;
  d.flyAt = f.fly >= 0 ? now - f.fly : -10;
}

export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  d.amb = (d.amb + dt) % LOOP;
  if (d.mode === 0) {
    if (auto) d.t = (d.t + dt) % LOOP;
    return;
  }
  if (!auto || now - d.last < RESUME || d.pressing === 1) return;
  // back to the loop only from a still screen
  for (let i = 0; i < ICONS; i++) if (now - d.actAt[i] < ACT) return;
  if (now - d.flyAt < LOOP - T.press) return;
  d.mode = 0;
  d.t = 0;
}

export function frameOf(d: Driver, now: number): Frame {
  'worklet';
  if (d.mode === 0) return frameAt(d.t);
  const act: number[] = [];
  for (let i = 0; i < ICONS; i++) {
    const p = (now - d.actAt[i]) / ACT;
    act.push(p >= 0 && p < 1 ? p : -1);
  }
  const fly = now - d.flyAt < LOOP - T.press ? now - d.flyAt : -1;
  const press = d.pressing === 1 ? smooth((now - d.pressAt) / 0.1) : bump(now - d.flyAt, -0.02, 0.22);
  return { amb: d.amb, act, fly, press };
}
