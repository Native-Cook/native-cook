/*
 Floating Letters Stir — where every letter is, as a pure function of the loop time, plus the
 small live simulation a finger drives. Lens: field (letters floating on water).

 LOOP = 10 s
   0.00 – 0.80   rest: the phrase readable, letters bobbing 1–2 pt on the ambient swell.
   0.80 – 1.20   the ghost finger lands left of the phrase (fade 0.4 s, scale 1.25 → 1).
   1.20 – 4.80   the stir: the finger runs 1.6 clockwise turns of an elliptical spiral around the
                 phrase (radius 1.02 → 0.60 of the phrase box, ease-in-out sine). A ring leaves the
                 finger every 0.5 s (8 rings, each alive 2.8 s, front at 150 pt/s). Each letter is
                 carried by what the finger did near it: the Gaussian-weighted integral of the
                 finger's velocity (sigma 70 pt), saturating at 130 pt, plus 16–48 pt outward from the
                 middle of the phrase, eased out over 1.6 s from
                 0.35 s before the finger's weighted closest pass. While adrift the offset keeps
                 turning with the vortex (0.10–0.22 rad/s), circles a small eddy and the letter
                 spins toward 0.3–1.1 rad.
   4.80 – 5.10   the finger lifts (fade 0.3 s). Letters keep drifting.
   6.30 – 8.35   home: each letter returns on an underdamped spring (zeta 0.62, omega 6.5 rad/s,
                 about 8 % overshoot), 60 ms apart in reading order, the offset turning by 0.5 rad
                 as it shrinks so the way back curves.
   8.35 – 10.0   rest.

 Everything periodic (swell, caustics) uses whole turns per LOOP, so t = LOOP equals t = 0.
*/

export const LOOP = 10;

export const GHOST_IN = 0.8;
export const STIR_A = 1.2;
export const STIR_B = 4.8;
export const GHOST_OUT = 5.1;
export const STIR_TURNS = 1.6;
const STIR_PHI0 = Math.PI * 1.1;
const STIR_R0 = 1.02;
const STIR_R1 = 0.6;

export const PUSH_SIGMA = 70;
export const PUSH_GAIN = 0.55;
export const PUSH_MAX = 130;
export const PUSH_SPREAD = 16;
const PUSH_LEAD = 0.35;
const PUSH_RISE = 1.6;

export const RETURN_AT = 6.3;
export const RETURN_STAGGER = 0.06;
const ZETA = 0.62;
const OMEGA = 6.5;
const SETTLED = 2.0;

export const RIPPLES = 8;
export const RIPPLE_T0 = 1.25;
export const RIPPLE_EVERY = 0.5;
export const RIPPLE_LIFE = 2.8;
export const RIPPLE_SPEED = 150;
export const RIPPLE_LAMBDA = 34;
export const RIPPLE_WIDTH = 30;
const RIPPLE_K = (2 * Math.PI) / RIPPLE_LAMBDA;

/** Letters ride the rings: lift in pt per unit height, tilt in rad per unit slope. */
export const BOB_LIFT = 3;
export const BOB_TILT = 0.05;

const TAU = Math.PI * 2;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeInOutSine(x: number) {
  'worklet';
  return 0.5 - 0.5 * Math.cos(Math.PI * x);
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** Unit step response of the return spring (0 → 1, a little past 1 on the way). */
export function springStep(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  if (tau >= SETTLED) return 1;
  const root = Math.sqrt(1 - ZETA * ZETA);
  const wd = OMEGA * root;
  const e = Math.exp(-ZETA * OMEGA * tau);
  return 1 - e * (Math.cos(wd * tau) + (ZETA / root) * Math.sin(wd * tau));
}

/** The phrase box the stir circles: centre and half extents, and the screen. */
export type Geo = { cx: number; cy: number; hw: number; hh: number; W: number; H: number };

/** A letter's resting centre. */
export type Home = { x: number; y: number };

/** Where the ghost finger is at loop time t: [x, y]. Before the stir it waits at the start, after it at the end. */
export function stirPos(t: number, geo: Geo): number[] {
  'worklet';
  const u = easeInOutSine(seg(t, STIR_A, STIR_B));
  const phi = STIR_PHI0 + TAU * STIR_TURNS * u;
  const r = STIR_R0 + (STIR_R1 - STIR_R0) * u;
  return [geo.cx + geo.hw * r * Math.cos(phi), geo.cy + geo.hh * r * Math.sin(phi)];
}

/** How visible the ghost finger is (0…1). */
export function ghostAt(t: number) {
  'worklet';
  return seg(t, GHOST_IN, STIR_A) * (1 - seg(t, STIR_B, GHOST_OUT));
}

/** What the scripted stir does to one letter: its carry (pt), when the finger passed, and its spin target (rad). */
export type Push = { px: number; py: number; tc: number; spin: number };

const PUSH_SAMPLES = 180;

/** Integrates the scripted finger's velocity near each home. Runs once per layout, on the JS thread. */
export function makePushes(homes: Home[], geo: Geo): Push[] {
  const out: Push[] = [];
  const step = (STIR_B - STIR_A) / PUSH_SAMPLES;
  for (let i = 0; i < homes.length; i += 1) {
    let sx = 0;
    let sy = 0;
    let sw = 0;
    let st = 0;
    for (let s = 0; s < PUSH_SAMPLES; s += 1) {
      const t0 = STIR_A + step * s;
      const a = stirPos(t0, geo);
      const b = stirPos(t0 + step, geo);
      const mx = (a[0] + b[0]) / 2 - homes[i].x;
      const my = (a[1] + b[1]) / 2 - homes[i].y;
      const w = Math.exp(-(mx * mx + my * my) / (2 * PUSH_SIGMA * PUSH_SIGMA));
      const dx = b[0] - a[0];
      const dy = b[1] - a[1];
      sx += dx * w;
      sy += dy * w;
      const len = Math.hypot(dx, dy) * w;
      sw += len;
      st += len * (t0 + step / 2);
    }
    const raw = Math.hypot(sx, sy) * PUSH_GAIN;
    const mag = PUSH_MAX * (1 - Math.exp(-raw / PUSH_MAX));
    const k = raw > 0.001 ? mag / (raw / PUSH_GAIN) : 0;
    const side = hash(i * 5.31 + 2) > 0.22 ? 1 : -0.45;
    // Water spreads what it carries: a little outward from the middle of the phrase, so letters part.
    const ox = homes[i].x - geo.cx;
    const oy = homes[i].y - geo.cy;
    const ol = Math.max(1, Math.hypot(ox, oy));
    const spread = (PUSH_SPREAD + PUSH_SPREAD * 2 * hash(i * 3.9 + 6)) * Math.min(1, mag / 50);
    out.push({
      px: sx * k + (ox / ol) * spread,
      py: sy * k + (oy / ol) * spread * 0.8,
      tc: sw > 0.001 ? st / sw : (STIR_A + STIR_B) / 2,
      spin: side * (0.3 + 0.8 * hash(i * 7.77 + 3)) * Math.min(1, mag / 60),
    });
  }
  return out;
}

/** [dx, dy, rotation (rad), scale] of letter i at loop time t, relative to its home. */
export function autoOffset(t: number, i: number, push: Push): number[] {
  'worklet';
  const start = push.tc - PUSH_LEAD;
  if (t <= start) return [0, 0, 0, 1];
  const h1 = hash(i * 3.17 + 1);
  const h2 = hash(i * 9.13 + 4);
  const ret = RETURN_AT + RETURN_STAGGER * i;
  const tau = Math.min(t, ret) - start;
  const e = easeOutCubic(clamp01(tau / PUSH_RISE));
  // The carry keeps turning with the vortex; a small eddy rides on it.
  const turn = (0.1 + 0.12 * h1) * tau;
  const c = Math.cos(turn);
  const s = Math.sin(turn);
  const eddyR = 5 + 7 * h2;
  const eddyA = h1 * TAU + (0.9 + 0.7 * h2) * tau;
  const ox = e * (push.px * c - push.py * s) + e * eddyR * (Math.cos(eddyA) - Math.cos(h1 * TAU));
  const oy = e * (push.px * s + push.py * c) + e * eddyR * (Math.sin(eddyA) - Math.sin(h1 * TAU));
  const rot = push.spin * (1 - Math.exp(-tau / 1.4)) + 0.06 * e * Math.sin(1.7 * tau + i);
  const sc = 1 + 0.03 * e * Math.sin(2.1 * tau + i * 1.3);
  if (t < ret) return [ox, oy, rot, sc];

  const k = 1 - springStep(t - ret);
  const a = 0.5 * k;
  const ca = Math.cos(a);
  const sa = Math.sin(a);
  return [k * (ox * ca - oy * sa), k * (ox * sa + oy * ca), k * rot, 1 + k * (sc - 1)];
}

/** The ambient swell under letter i at ambient time a (period LOOP): [dy (pt), tilt (rad)]. */
export function swellAt(a: number, i: number): number[] {
  'worklet';
  const p = (TAU * a) / LOOP;
  const f1 = hash(i * 2.11 + 9) * TAU;
  const f2 = hash(i * 4.57 + 11) * TAU;
  return [1.3 * Math.sin(p + f1) + 0.7 * Math.sin(2 * p + f2), 0.012 * Math.sin(p + f2)];
}

/**
 Rings on the water, as a flat list of RIPPLES × [x, y, age (s), strength]; strength 0 = unused.
 The scripted ones leave the ghost finger every RIPPLE_EVERY seconds.
*/
export function autoRipples(t: number, geo: Geo, on: number): number[] {
  'worklet';
  const out: number[] = [];
  for (let k = 0; k < RIPPLES; k += 1) {
    const born = RIPPLE_T0 + RIPPLE_EVERY * k;
    const age = t - born;
    if (on > 0 && age > 0 && age < RIPPLE_LIFE) {
      const p = stirPos(born, geo);
      out.push(p[0], p[1], age, 0.6 + 0.4 * hash(k * 6.3 + 17));
    } else {
      out.push(0, 0, 0, 0);
    }
  }
  return out;
}

/** Height and slope of the rings at a point: [h (−1…1), dh/dx, dh/dy] with the slope divided by the wave number. */
export function ripplesAt(x: number, y: number, rips: number[]): number[] {
  'worklet';
  let h = 0;
  let gx = 0;
  let gy = 0;
  for (let k = 0; k < RIPPLES; k += 1) {
    const amp = rips[k * 4 + 3];
    if (amp <= 0) continue;
    const age = rips[k * 4 + 2];
    const dx = x - rips[k * 4];
    const dy = y - rips[k * 4 + 1];
    const d = Math.max(0.001, Math.hypot(dx, dy));
    const off = d - RIPPLE_SPEED * age;
    if (off > RIPPLE_WIDTH * 2.5 || off < -RIPPLE_WIDTH * 2.5) continue;
    const env = amp * seg(age, 0, 0.15) * Math.pow(1 - clamp01(age / RIPPLE_LIFE), 1.5);
    const g = Math.exp(-(off * off) / (RIPPLE_WIDTH * RIPPLE_WIDTH));
    const sn = Math.sin(off * RIPPLE_K);
    const cs = Math.cos(off * RIPPLE_K);
    const dh = env * g * (cs - ((2 * off) / (RIPPLE_WIDTH * RIPPLE_WIDTH * RIPPLE_K)) * sn);
    h += env * g * sn;
    gx += (dh * dx) / d;
    gy += (dh * dy) / d;
  }
  return [h, gx, gy];
}

// ---- Under a real finger: a small live simulation --------------------------------------------

/** Per letter: offset, velocity, rotation and its speed. */
export type Sim = { x: number[]; y: number[]; vx: number[]; vy: number[]; r: number[]; vr: number[] };

export function createSim(n: number): Sim {
  'worklet';
  const s: Sim = { x: [], y: [], vx: [], vy: [], r: [], vr: [] };
  for (let i = 0; i < n; i += 1) {
    s.x.push(0);
    s.y.push(0);
    s.vx.push(0);
    s.vy.push(0);
    s.r.push(0);
    s.vr.push(0);
  }
  return s;
}

/** The finger: where it is, how fast it moves (pt/s), and whether it is down. */
export type Finger = { x: number; y: number; vx: number; vy: number; on: number };

const FINGER_SIGMA = 95;
const FINGER_MAX = 1800;
const COUPLE = 9;
const DRAG = 2.2;
const SOFT_K = 1.2;
const FIRM_K = 42;
const EDGE = 28;
const EDGE_K = 30;

/**
 One step of the live water. While stirring the letters follow the water the finger moves
 (velocity coupling, Gaussian of 95 pt), drift against drag and feel only a faint pull home;
 with `settle` = 1 the pull is a critically damped spring. Past the screen's margins a spring
 pushes back (the rubber band).
*/
export function stepSim(s: Sim, homes: Home[], finger: Finger, geo: Geo, dt: number, settle: number) {
  'worklet';
  const k = settle > 0 ? FIRM_K : SOFT_K;
  const c = settle > 0 ? 2 * Math.sqrt(FIRM_K) : DRAG;
  const sp = Math.hypot(finger.vx, finger.vy);
  const cap = sp > FINGER_MAX ? FINGER_MAX / sp : 1;
  const fvx = finger.vx * cap;
  const fvy = finger.vy * cap;
  for (let i = 0; i < homes.length; i += 1) {
    const X = homes[i].x + s.x[i];
    const Y = homes[i].y + s.y[i];
    let ax = -k * s.x[i] - c * s.vx[i];
    let ay = -k * s.y[i] - c * s.vy[i];
    let ar = -(settle > 0 ? FIRM_K : 0.4) * s.r[i] - (settle > 0 ? 2 * Math.sqrt(FIRM_K) : 1.6) * s.vr[i];
    if (finger.on > 0 && sp > 20) {
      const dx = X - finger.x;
      const dy = Y - finger.y;
      const near = Math.exp(-(dx * dx + dy * dy) / (2 * FINGER_SIGMA * FINGER_SIGMA));
      const pull = COUPLE * (0.04 + near);
      ax += (fvx - s.vx[i]) * pull;
      ay += (fvy - s.vy[i]) * pull;
      // The water turns around the finger's path: letters beside it are spun by its curl.
      ar += ((dx * fvy - dy * fvx) / 5200) * near;
    }
    if (settle === 0) {
      if (X < EDGE) ax += (EDGE - X) * EDGE_K;
      if (X > geo.W - EDGE) ax -= (X - (geo.W - EDGE)) * EDGE_K;
      if (Y < EDGE * 2) ay += (EDGE * 2 - Y) * EDGE_K;
      if (Y > geo.H - EDGE * 2) ay -= (Y - (geo.H - EDGE * 2)) * EDGE_K;
    }
    s.vx[i] += ax * dt;
    s.vy[i] += ay * dt;
    s.vr[i] += ar * dt;
    s.x[i] += s.vx[i] * dt;
    s.y[i] += s.vy[i] * dt;
    s.r[i] += s.vr[i] * dt;
  }
}

/** How far from rest the live water is (pt-like units). */
export function simEnergy(s: Sim) {
  'worklet';
  let e = 0;
  for (let i = 0; i < s.x.length; i += 1) {
    const v = Math.abs(s.x[i]) + Math.abs(s.y[i]) + (Math.abs(s.vx[i]) + Math.abs(s.vy[i])) * 0.05 + Math.abs(s.r[i]) * 30;
    if (v > e) e = v;
  }
  return e;
}
