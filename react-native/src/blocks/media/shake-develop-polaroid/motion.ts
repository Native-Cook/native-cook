/*
 The motion of Shake Develop Polaroid, as functions of time (a port of ShakeDevelopPolaroidModel.swift;
 the numbers are the same). All functions are worklets.

 Development d (0 = just ejected, 1 = developed) drives the photo's look:
   colour     a 4 × 5 matrix: saturation 0.1 → 1, contrast 0.25 → 1 about mid-grey, a milky lift of
              0.18 and a cool cast (−0.02, +0.01, +0.03) that both fade out; all with e = smoothstep(d).
   haze       a milky #D7DDDA veil over the photo that lifts from the bottom up: at height v (0 at the
              bottom, 1 at the top) its opacity is 0.9 · smoothstep(f − 0.25, f + 0.25, v), f = 1.4 d − 0.2.

 The shake, as the print feels it in the hand: two bursts, each a sine of frequency f and amplitude A
 (breathing ±20 % at 0.9 Hz), that fades in over 0.15 s and dies out as e^(−7 s) after the burst:
   x = A·env·sin(2π f τ),  rotation = 6°·env·sin(2π f τ − 0.5),  y = 3·env·sin(4π f τ).

 autoPlay timeline (LOOP = 8 s):
   0.00–0.70  rest: an undeveloped print, "Shake to develop", an empty bar.
   0.70–2.30  burst 1 (A 13 pt, 3.2 Hz); d 0 → 0.42 (ease in-out). 2.30–2.80 the print settles; d creeps to 0.46.
   2.80–4.60  burst 2 (A 17 pt, 3.5 Hz); d 0.46 → 1 by 4.70 (ease-out).
   4.70–5.00  the label turns to "Developed" with a check; 4.75–5.45 a glint sweeps the photo.
   5.90–6.55  the print drops into the roll thumbnail at the bottom (ease-in: scale → 0.11, fades in its
              last 30 %); the bar fades 5.90–6.20; 6.50–6.95 the thumbnail bumps (+16 %).
   6.70–7.50  a new undeveloped print slides down from 110 pt above (ease-out-back, fade 0.15 s,
              scale 0.96 → 1); the label turns back 6.80–7.10; the bar returns 7.00–7.30.
   7.50–8.00  rest, identical to t = 0.
 */

export const LOOP = 8;

export type Scene = {
  x: number;
  y: number;
  /** Degrees. */
  rotation: number;
  scale: number;
  alpha: number;
  /** Moves the print from its place to the roll thumbnail (0 … 1). */
  toRoll: number;
  develop: number;
  /** Glint progress 0 … 1, or −1 when none. */
  glint: number;
  /** 0: "Shake to develop", 1: "Developed". */
  label: number;
  bar: number;
  bump: number;
};

export function restScene(): Scene {
  'worklet';
  return { x: 0, y: 0, rotation: 0, scale: 1, alpha: 1, toRoll: 0, develop: 0, glint: -1, label: 0, bar: 1, bump: 0 };
}

const BURSTS = [
  { start: 0.7, end: 2.3, amp: 13, freq: 3.2 },
  { start: 2.8, end: 4.6, amp: 17, freq: 3.5 },
];

export function clamp(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}
export function easeInOut(x: number) {
  'worklet';
  const t = clamp(x);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}
function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp(x), 3);
}
function easeIn(x: number) {
  'worklet';
  const t = clamp(x);
  return t * t * t;
}
function easeOutBack(x: number) {
  'worklet';
  const t = clamp(x);
  const c1 = 1.70158;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
}

/** The shake's offset at loop time t: x, y in points and rotation in degrees. */
export function shake(t: number) {
  'worklet';
  let x = 0;
  let y = 0;
  let r = 0;
  for (const b of BURSTS) {
    if (t < b.start) continue;
    const tau = t - b.start;
    const dur = b.end - b.start;
    const env = tau < dur ? smoothstep(0, 0.15, tau) : Math.exp(-7 * (tau - dur));
    if (env < 0.002) continue;
    const a = b.amp * (0.8 + 0.2 * Math.sin(2 * Math.PI * 0.9 * tau));
    const ph = 2 * Math.PI * b.freq * tau;
    x += a * env * Math.sin(ph);
    r += 6 * env * Math.sin(ph - 0.5);
    y += 3 * env * Math.sin(2 * ph);
  }
  return { x, y, r };
}

export function developAt(t: number) {
  'worklet';
  if (t < 0.7) return 0;
  if (t < 2.3) return 0.42 * easeInOut((t - 0.7) / 1.6);
  if (t < 2.8) return 0.42 + 0.04 * smoothstep(2.3, 2.8, t);
  if (t < 6.7) return 0.46 + 0.54 * easeOut((t - 2.8) / 1.9);
  return 0;
}

export function autoScene(t: number): Scene {
  'worklet';
  const s = restScene();
  const sh = shake(t);
  s.x = sh.x;
  s.y = sh.y;
  s.rotation = sh.r;
  s.develop = developAt(t);
  if (t >= 4.75 && t < 5.45) s.glint = (t - 4.75) / 0.7;
  s.label = smoothstep(4.7, 5.0, t) * (1 - smoothstep(6.8, 7.1, t));
  s.bar = 1 - smoothstep(5.9, 6.2, t) + smoothstep(7.0, 7.3, t);
  if (t >= 5.9 && t < 6.7) {
    const q = easeIn((t - 5.9) / 0.65);
    s.toRoll = q;
    s.scale = 1 - 0.89 * q;
    s.alpha = 1 - smoothstep(0.7, 1, q);
  }
  if (t >= 6.5 && t < 6.95) s.bump = 0.16 * Math.sin((Math.PI * (t - 6.5)) / 0.45);
  if (t >= 6.7) {
    const u = (t - 6.7) / 0.8;
    s.toRoll = 0;
    s.y = -110 * (1 - easeOutBack(u));
    s.alpha = smoothstep(6.7, 6.85, t);
    s.scale = 0.96 + 0.04 * easeOut(u);
  }
  return s;
}

/** The photo's colour matrix at development d (4 × 5, offsets in 0 … 1). */
export function colorMatrix(d: number): number[] {
  'worklet';
  const e = smoothstep(0, 1, d);
  const sat = 0.1 + 0.9 * e;
  const con = 0.25 + 0.75 * e;
  const lift = 0.18 * (1 - e);
  const cast = [-0.02 * (1 - e), 0.01 * (1 - e), 0.03 * (1 - e)];
  const lw = [0.2126, 0.7152, 0.0722];
  const m = new Array<number>(20).fill(0);
  for (let row = 0; row < 3; row++) {
    for (let col = 0; col < 3; col++) {
      const s = (1 - sat) * lw[col] + (row === col ? sat : 0);
      m[row * 5 + col] = con * s;
    }
    m[row * 5 + 4] = 0.5 * (1 - con) + lift + cast[row];
  }
  m[18] = 1;
  return m;
}

/** The haze's opacity at height v (0 at the bottom of the photo, 1 at the top). */
export function haze(d: number, v: number) {
  'worklet';
  const f = 1.4 * d - 0.2;
  return 0.9 * smoothstep(f - 0.25, f + 0.25, v);
}
