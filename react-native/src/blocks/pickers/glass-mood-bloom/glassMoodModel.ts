/*
 Glass Mood Bloom — the numbers (a port of GlassMoodBloomModel.swift): the seven moods, the loop's value V(t),
 the layer parameters and the touch driver. Everything the screen draws is a function of the mood value
 v ∈ [-1, 1] (−1 Very Unpleasant … 0 Neutral … 1 Very Pleasant), and v is a function of time: the loop's timeline,
 or the finger's samples, a detent spring and the ease back to Neutral after a touch.
 Every function here is a worklet; helpers are declared above their callers.
*/

export const LOOP = 10;

/** r, g, b in 0…1. */
export type Rgb = { r: number; g: number; b: number };

export function rgb(hex: number): Rgb {
  'worklet';
  return { r: ((hex >> 16) & 0xff) / 255, g: ((hex >> 8) & 0xff) / 255, b: (hex & 0xff) / 255 };
}

export function mix(a: Rgb, b: Rgb, t: number): Rgb {
  'worklet';
  return { r: a.r + (b.r - a.r) * t, g: a.g + (b.g - a.g) * t, b: a.b + (b.b - a.b) * t };
}

export const WHITE: Rgb = { r: 1, g: 1, b: 1 };

type Mood = {
  name: string;
  rv: number; // valley radius / tip radius of the lobed outline
  p: number; // petal profile exponent (< 1 round lobes, > 1 thin spikes)
  q: number; // 0 lobed outline, 1 polygon outline
  e: number; // polygon side exponent (0 circle, 1 straight sides, > 1 concave star sides)
  k: number; // polygon corner sharpness (1 sharp, lower rounder)
  twist: number; // degrees the outer plate turns against the others
  size: number;
  deep: number;
  mid: number;
  pale: number;
  bgTop: number;
  bgBottom: number;
  button: number;
  label: number;
};

export const MOODS: Mood[] = [
  { name: 'Very Unpleasant', rv: 0.5, p: 1.6, q: 0.5, e: 7, k: 0.975, twist: 12, size: 1.04,
    deep: 0x7b45e6, mid: 0xae92f2, pale: 0xece6fc, bgTop: 0xd6cbee, bgBottom: 0xb5a3de, button: 0x2c2382, label: 0x2b1f66 },
  { name: 'Unpleasant', rv: 0.65, p: 1.2, q: 0.6, e: 4, k: 0.94, twist: 8, size: 1.0,
    deep: 0x4e6aee, mid: 0x95a6f4, pale: 0xe4e8fc, bgTop: 0xd3d6f2, bgBottom: 0xb7bce8, button: 0x1b3dbe, label: 0x1e2a6e },
  { name: 'Slightly Unpleasant', rv: 0.87, p: 0.8, q: 0, e: 0, k: 0.9, twist: 4, size: 0.97,
    deep: 0x3b8ef0, mid: 0x8cc0f7, pale: 0xe2eefc, bgTop: 0xd7e0f2, bgBottom: 0xbccbea, button: 0x0e58c8, label: 0x18346a },
  { name: 'Neutral', rv: 1.0, p: 1.0, q: 0, e: 0, k: 0.9, twist: 0, size: 0.95,
    deep: 0x47b4e4, mid: 0x9dd7ef, pale: 0xe9f6fb, bgTop: 0xe1e8ec, bgBottom: 0xc8d6dd, button: 0x3591b8, label: 0x22404e },
  { name: 'Slightly Pleasant', rv: 1, p: 1, q: 1, e: 1, k: 0.88, twist: 3, size: 0.98,
    deep: 0x5ccb24, mid: 0xa7e36c, pale: 0xe8f6d2, bgTop: 0xd6e8be, bgBottom: 0xb3d595, button: 0x2c8a0b, label: 0x27461a },
  { name: 'Pleasant', rv: 1, p: 1, q: 1, e: 3.2, k: 0.85, twist: 5, size: 1.0,
    deep: 0xf0be16, mid: 0xf8dc66, pale: 0xfff4c8, bgTop: 0xf1e5b6, bgBottom: 0xe6cb82, button: 0xd08f08, label: 0x4c3a0c },
  { name: 'Very Pleasant', rv: 0.55, p: 0.45, q: 0, e: 0, k: 0.85, twist: 10, size: 1.04,
    deep: 0xff6a2a, mid: 0xffaa6a, pale: 0xffe8d2, bgTop: 0xfce3ca, bgBottom: 0xf6c298, button: 0xfa6a2c, label: 0x5a2a12 },
];

/** The mood's parameters at v, interpolated between the two neighbouring anchors. */
export type Look = {
  n: number;
  rv: number;
  p: number;
  q: number;
  e: number;
  k: number;
  twist: number;
  size: number;
  deep: Rgb;
  mid: Rgb;
  pale: Rgb;
  bgTop: Rgb;
  bgBottom: Rgb;
  button: Rgb;
  label: Rgb;
};

function lerp(a: number, b: number, f: number) {
  'worklet';
  return a + (b - a) * f;
}

export function look(v: number): Look {
  'worklet';
  const x = (Math.min(Math.max(v, -1), 1) + 1) * 3;
  const i = Math.min(Math.floor(x), 5);
  const f = x - i;
  const a = MOODS[i];
  const b = MOODS[i + 1];
  return {
    n: v > 0 ? 5 : 8,
    rv: lerp(a.rv, b.rv, f),
    p: lerp(a.p, b.p, f),
    q: lerp(a.q, b.q, f),
    e: lerp(a.e, b.e, f),
    k: lerp(a.k, b.k, f),
    twist: lerp(a.twist, b.twist, f),
    size: lerp(a.size, b.size, f),
    deep: mix(rgb(a.deep), rgb(b.deep), f),
    mid: mix(rgb(a.mid), rgb(b.mid), f),
    pale: mix(rgb(a.pale), rgb(b.pale), f),
    bgTop: mix(rgb(a.bgTop), rgb(b.bgTop), f),
    bgBottom: mix(rgb(a.bgBottom), rgb(b.bgBottom), f),
    button: mix(rgb(a.button), rgb(b.button), f),
    label: mix(rgb(a.label), rgb(b.label), f),
  };
}

/** 0 … 6 into MOODS. */
export function bucket(v: number): number {
  'worklet';
  return Math.round(Math.min(Math.max(v, -1), 1) * 3) + 3;
}

// Layers

export const LAYER_SCALE = [1.0, 0.91, 0.64, 0.52, 0.4, 0.27];
export const LAYER_ALPHA = [0.16, 0.22, 0.42, 0.55, 0.68, 0.8];
export const LAYER_LAG = 0.045;

function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = Math.min(Math.max((x - a) / (b - a), 0), 1);
  return t * t * (3 - 2 * t);
}

export function layerColor(l: Look, k: number): Rgb {
  'worklet';
  return mix(mix(l.pale, l.mid, smooth(0, 0.5, k)), l.deep, smooth(0.4, 1, k));
}

/**
 * Radius (tip = 1) at angle a from a petal tip: a mix of a lobed outline (rv + (1 − rv) · ((1 + cos na) / 2)^p)
 * and a rounded polygon / star outline ((cos ψt / cos ψ)^e, ψ = acos(−k cos na) / n).
 */
export function radiusAt(a: number, l: Look): number {
  'worklet';
  const c = Math.cos(l.n * a);
  const lobe = l.rv + (1 - l.rv) * Math.pow(Math.max((1 + c) / 2, 0), l.p);
  const psiT = Math.acos(-l.k) / l.n;
  const psi = Math.acos(-l.k * c) / l.n;
  const poly = Math.pow(Math.cos(psiT) / Math.cos(psi), l.e);
  return lobe + (poly - lobe) * l.q;
}

/** The smallest radius of the outline (tip = 1). */
export function valley(l: Look): number {
  'worklet';
  const poly = Math.pow(Math.cos(Math.acos(-l.k) / l.n), l.e);
  return l.rv + (poly - l.rv) * l.q;
}

// Loop timeline

export function easeInOut(x: number): number {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOut(x: number): number {
  'worklet';
  return 1 - Math.pow(1 - Math.min(Math.max(x, 0), 1), 3);
}

/** start, end, from, to (flat); between moves the value holds. */
const MOVES = [
  1.2, 1.75, 0, 1 / 3,
  2.15, 2.7, 1 / 3, 2 / 3,
  3.1, 3.65, 2 / 3, 1,
  4.5, 5.3, 1, 0,
  5.5, 6.0, 0, -1 / 3,
  6.3, 6.8, -1 / 3, -2 / 3,
  7.1, 7.6, -2 / 3, -1,
  8.4, 9.3, -1, 0,
];

export function loopValue(time: number): number {
  'worklet';
  let t = time % LOOP;
  if (t < 0) t += LOOP;
  let v = 0;
  for (let i = 0; i < MOVES.length; i += 4) {
    const a = MOVES[i];
    const b = MOVES[i + 1];
    if (t < a) return v;
    if (t < b) return MOVES[i + 2] + (MOVES[i + 3] - MOVES[i + 2]) * easeInOut((t - a) / (b - a));
    v = MOVES[i + 3];
  }
  return v;
}

export function breathe(t: number, i: number): number {
  'worklet';
  return Math.sin((2 * Math.PI * 3 * t) / LOOP + 0.7 * i) - Math.sin(0.7 * i);
}

export function wobble(t: number, i: number): number {
  'worklet';
  return Math.sin((2 * Math.PI * t) / LOOP + 0.9 * i) - Math.sin(0.9 * i);
}

// Spring (release to the nearest mood)

const OMEGA = (2 * Math.PI) / 0.42;
const ZETA = 0.8;

export function spring(dt: number, x0: number, v0: number, target: number): number {
  'worklet';
  const a = x0 - target;
  const wd = OMEGA * Math.sqrt(1 - ZETA * ZETA);
  const b = (v0 + ZETA * OMEGA * a) / wd;
  return target + Math.exp(-ZETA * OMEGA * dt) * (a * Math.cos(wd * dt) + b * Math.sin(wd * dt));
}

const RETURN_DELAY = 2.0;
const RETURN_TIME = 0.9;

// Driver: holds what a touch changed; every value is still read as a function of time.

export type Driver = {
  auto: boolean;
  reduced: boolean;
  hasEpoch: boolean;
  epoch: number;
  ts: number[];
  vs: number[];
  touching: boolean;
  touchStart: number;
  hasRelease: boolean;
  relT: number;
  relX: number;
  relVel: number;
  relTarget: number;
  rest: number;
};

export function makeDriver(auto: boolean, reduced: boolean): Driver {
  'worklet';
  return {
    auto,
    reduced,
    hasEpoch: false,
    epoch: 0,
    ts: [],
    vs: [],
    touching: false,
    touchStart: Infinity,
    hasRelease: false,
    relT: 0,
    relX: 0,
    relVel: 0,
    relTarget: 0,
    rest: 0,
  };
}

/** Call once per frame with the frame's time, before reading values. */
function begin(d: Driver, now: number) {
  'worklet';
  if (!d.hasEpoch) {
    d.hasEpoch = true;
    d.epoch = now;
  }
  if (d.auto && !d.touching && d.hasRelease && now > d.relT + RETURN_DELAY + RETURN_TIME + 0.5) {
    d.epoch = d.relT + RETURN_DELAY + RETURN_TIME;
    d.hasRelease = false;
    d.ts = [];
    d.vs = [];
    d.touchStart = Infinity;
  }
}

function loopTime(d: Driver, t: number): number {
  'worklet';
  const e = d.hasEpoch ? d.epoch : t;
  if (d.auto && d.hasRelease && t >= d.relT + RETURN_DELAY + RETURN_TIME) return t - (d.relT + RETURN_DELAY + RETURN_TIME);
  return t - e;
}

function breatheWeight(d: Driver, t: number): number {
  'worklet';
  if (!d.auto || d.reduced) return 0;
  if (d.hasRelease && !d.touching) {
    const dt = t - d.relT - RETURN_DELAY;
    if (dt >= 0 && dt < RETURN_TIME) return 1 - easeInOut(dt / RETURN_TIME);
  }
  return 1;
}

export function valueAt(d: Driver, t: number): number {
  'worklet';
  if (t < d.touchStart || d.ts.length === 0) {
    if (!d.auto || d.reduced) return d.rest;
    return loopValue(t - (d.hasEpoch ? d.epoch : t));
  }
  if (d.touching || !d.hasRelease || t < d.relT) {
    const ts = d.ts;
    const vs = d.vs;
    if (t <= ts[0]) return vs[0];
    for (let k = ts.length - 1; k >= 0; k--) {
      if (ts[k] <= t) {
        if (k === ts.length - 1) return vs[k];
        const f = (t - ts[k]) / Math.max(ts[k + 1] - ts[k], 1e-6);
        return vs[k] + (vs[k + 1] - vs[k]) * f;
      }
    }
    return vs[0];
  }
  const dt = t - d.relT;
  if (d.reduced) return d.relTarget;
  if (!d.auto || dt < RETURN_DELAY) return spring(dt, d.relX, d.relVel, d.relTarget);
  if (dt < RETURN_DELAY + RETURN_TIME) {
    const a = spring(RETURN_DELAY, d.relX, d.relVel, d.relTarget);
    return a * (1 - easeInOut((dt - RETURN_DELAY) / RETURN_TIME));
  }
  return loopValue(dt - RETURN_DELAY - RETURN_TIME);
}

/** True while something still moves after a touch. */
export function busy(d: Driver, now: number): boolean {
  'worklet';
  return d.touching || (d.hasRelease && now < d.relT + RETURN_DELAY + RETURN_TIME + 1);
}

/** The finger went down: keep the last 0.3 s of the value as history so the lagging layers stay continuous. */
export function touchDown(d: Driver, now: number): number {
  'worklet';
  const current = valueAt(d, now);
  const preT: number[] = [];
  const preV: number[] = [];
  for (let k = 0; k <= 8; k++) {
    const t = now - 0.3 + k * 0.0375;
    preT.push(t);
    preV.push(valueAt(d, t));
  }
  d.ts = preT;
  d.vs = preV;
  d.touchStart = preT[0];
  d.touching = true;
  d.hasRelease = false;
  return current;
}

export function touchMove(d: Driver, now: number, v: number) {
  'worklet';
  const ts = d.ts.slice();
  const vs = d.vs.slice();
  ts.push(now);
  vs.push(Math.min(Math.max(v, -1), 1));
  if (ts.length > 240) {
    ts.splice(0, ts.length - 240);
    vs.splice(0, vs.length - 240);
  }
  d.ts = ts;
  d.vs = vs;
}

export function touchUp(d: Driver, now: number) {
  'worklet';
  d.touching = false;
  const n = d.vs.length;
  const last = n > 0 ? d.vs[n - 1] : 0;
  let vel = 0;
  let k = d.ts.length - 1;
  while (k >= 0 && d.ts[k] > now - 0.06) k--;
  if (k >= 0 && n > 0 && d.ts[n - 1] > d.ts[k]) vel = (d.vs[n - 1] - d.vs[k]) / Math.max(now - d.ts[k], 0.016);
  vel = Math.min(Math.max(vel, -6), 6);
  const target = (bucket(last) - 3) / 3;
  d.ts = d.ts.concat([now]);
  d.vs = d.vs.concat([last]);
  d.hasRelease = true;
  d.relT = now;
  d.relX = last;
  d.relVel = d.reduced ? 0 : vel;
  d.relTarget = target;
  if (!d.auto) d.rest = target;
}

// Frame: everything a frame needs, from time.

export type Frame = {
  v: number;
  layerV: number[];
  swell: number[];
  loopT: number;
  breath: number;
  label: number;
  labelPrev: number;
  nowAlpha: number;
  nowDy: number;
  prevAlpha: number;
  prevDy: number;
};

export function makeFrame(d: Driver, now: number): Frame {
  'worklet';
  begin(d, now);
  const v = valueAt(d, now);
  const layerV: number[] = [];
  const swell: number[] = [];
  for (let i = 0; i < 6; i++) {
    const t = now - (5 - i) * LAYER_LAG;
    if (d.reduced) {
      layerV.push(v);
      swell.push(0);
    } else {
      layerV.push(valueAt(d, t));
      // Plates swell a little while they change and settle when they arrive
      swell.push(Math.min(0.06, 0.5 * Math.abs(valueAt(d, t) - valueAt(d, t - 0.1))));
    }
  }
  const b = bucket(v);
  let changed = false;
  let change = 0;
  let prev = b;
  for (let k = 1; k <= 18; k++) {
    const t = now - k / 60;
    const bb = bucket(valueAt(d, t));
    if (bb !== b) {
      changed = true;
      change = t + 1 / 120;
      prev = bb;
      break;
    }
  }
  let pa = 0;
  let pd = 0;
  let na = 1;
  let nd = 0;
  if (changed) {
    const e = now - change;
    const out = Math.min(Math.max(e / 0.14, 0), 1);
    pa = 1 - out * out;
    pd = -10 * out * out;
    const inn = easeOut((e - 0.06) / 0.24);
    na = inn;
    nd = 10 * (1 - inn);
  }
  return {
    v,
    layerV,
    swell,
    loopT: loopTime(d, now),
    breath: breatheWeight(d, now),
    label: b,
    labelPrev: prev,
    nowAlpha: na,
    nowDy: nd,
    prevAlpha: pa,
    prevDy: pd,
  };
}
