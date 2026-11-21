// Race Globe Calendar: camera maths and the autoplay timeline (spec §4 with the Pro addendum: flight 3 cut, LOOP
// 10.5 s) as a pure function of the loop time, so every frame is reproducible and the ports can match it. Times are
// seconds, angles degrees unless a name says otherwise.

import { LAT, LON, SUN, W, vecOf } from './rgc-data';
import { G, SPRING } from './rgc-theme';

export const LOOP = 10.5;
const DEG = Math.PI / 180;

// ---- scalar helpers (declared before every caller: worklet closures are captured where they are defined) ----

export const clamp01 = (v: number) => {
  'worklet';
  return Math.min(Math.max(v, 0), 1);
};
export const seg = (t: number, a: number, b: number) => {
  'worklet';
  return clamp01((t - a) / (b - a));
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const smooth = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x * x * (3 - 2 * x);
};
export const smoothstep = (a: number, b: number, v: number) => {
  'worklet';
  return smooth((v - a) / (b - a));
};
export const easeOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return 1 - (1 - x) * (1 - x) * (1 - x);
};
export const easeInOut = (k: number) => {
  'worklet';
  const x = clamp01(k);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
};
export const sineInOut = (k: number) => {
  'worklet';
  return (1 - Math.cos(Math.PI * clamp01(k))) / 2;
};

/** A damped spring (mass 1) released at t = 0 from `from` towards `to` with velocity v0: closed form. */
export const springAt = (t: number, from: number, to: number, v0: number, d: number, k: number) => {
  'worklet';
  if (t <= 0) return from;
  const x0 = from - to;
  const w0 = Math.sqrt(k);
  const z = d / (2 * w0);
  if (z < 1) {
    const wd = w0 * Math.sqrt(1 - z * z);
    const e = Math.exp(-z * w0 * t);
    return to + e * (x0 * Math.cos(wd * t) + ((v0 + z * w0 * x0) / wd) * Math.sin(wd * t));
  }
  const e = Math.exp(-w0 * t);
  return to + e * (x0 + (v0 + w0 * x0) * t);
};

// ---- vectors ----

export const dot3 = (a: number[], b: number[]) => {
  'worklet';
  return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
};
export const norm3 = (a: number[]) => {
  'worklet';
  const l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]) || 1;
  return [a[0] / l, a[1] / l, a[2] / l];
};
/** Great-circle interpolation between two unit vectors. */
export const slerp3 = (a: number[], b: number[], k: number) => {
  'worklet';
  const c = Math.min(Math.max(dot3(a, b), -1), 1);
  const om = Math.acos(c);
  if (om < 1e-5) return [a[0], a[1], a[2]];
  const s = Math.sin(om);
  const ka = Math.sin((1 - k) * om) / s;
  const kb = Math.sin(k * om) / s;
  return [a[0] * ka + b[0] * kb, a[1] * ka + b[1] * kb, a[2] * ka + b[2] * kb];
};
export const angle3 = (a: number[], b: number[]) => {
  'worklet';
  return Math.acos(Math.min(Math.max(dot3(a, b), -1), 1)) / DEG;
};
export const latOf = (v: number[]) => {
  'worklet';
  return Math.asin(Math.min(Math.max(v[1], -1), 1)) / DEG;
};
export const lonOf = (v: number[]) => {
  'worklet';
  return Math.atan2(v[0], v[2]) / DEG;
};
/** Wrap degrees into (−180, 180]. */
export const wrap180 = (d: number) => {
  'worklet';
  return d - 360 * Math.floor((d + 180) / 360);
};

// ---- camera ----

/** Weak-perspective camera: the eye is `camDist` radii from the centre; F = focal length that puts the limb at R. */
export const D = G.camDist;
export const focalOf = (R: number) => {
  'worklet';
  return R * Math.sqrt(D * D - 1);
};
/** View angle above the axis at which the selected point sits, so it lands at y = pinY at rest zoom. */
const solveBeta = () => {
  const F = focalOf(G.globe.r);
  const want = (G.globe.cy - G.pinY) / F;
  let b = 0.1;
  for (let i = 0; i < 30; i++) {
    const f = Math.sin(b) / (D - Math.cos(b)) - want;
    const df = (Math.cos(b) * (D - Math.cos(b)) - Math.sin(b) * Math.sin(b)) / ((D - Math.cos(b)) * (D - Math.cos(b)));
    b -= f / df;
  }
  return b;
};
export const BETA = solveBeta();

/**
 * World → view rotation (rows) for a north-up camera whose selected point (lat, lon) sits BETA above the view axis.
 * M = Rx(lat − BETA) · Ry(−lon).
 */
export const camRows = (latDeg: number, lonDeg: number) => {
  'worklet';
  const l = lonDeg * DEG;
  const b = latDeg * DEG - BETA;
  const cl = Math.cos(l);
  const sl = Math.sin(l);
  const cb = Math.cos(b);
  const sb = Math.sin(b);
  return [cl, 0, -sl, -sb * sl, cb, -sb * cl, cb * sl, sb, cb * cl];
};
/** The world point on the view axis (seam reference for the shader): row 2 of M. */
export const axisLon = (m: number[]) => {
  'worklet';
  return Math.atan2(m[6], m[8]);
};

export type Proj = { x: number; y: number; z: number; vis: number };

/** Project a world point (may be lifted above the surface) for camera rows m, centre (cx, cy) and limb radius R. */
export const project = (m: number[], p: number[], cx: number, cy: number, R: number): Proj => {
  'worklet';
  const vx = m[0] * p[0] + m[1] * p[1] + m[2] * p[2];
  const vy = m[3] * p[0] + m[4] * p[1] + m[5] * p[2];
  const vz = m[6] * p[0] + m[7] * p[1] + m[8] * p[2];
  const F = focalOf(R);
  const w = D - vz;
  // occlusion: does the eye → point segment cross the unit sphere first?
  const dx = vx;
  const dy = vy;
  const dz = vz - D;
  const a = dx * dx + dy * dy + dz * dz;
  const bq = 2 * D * dz;
  const c = D * D - 1;
  const disc = bq * bq - 4 * a * c;
  let vis = 1;
  if (disc > 0) {
    const t1 = (-bq - Math.sqrt(disc)) / (2 * a);
    if (t1 > 0 && t1 < 0.9999) vis = 0;
  }
  return { x: cx + (F * vx) / w, y: cy - (F * vy) / w, z: vz, vis };
};

/** How far a flight zooms out, by hop length. */
export const hopDip = (hopDeg: number) => {
  'worklet';
  return smoothstep(20, 110, hopDeg);
};
export const zoomFor = (e: number, hopDeg: number) => {
  'worklet';
  return 1 - 0.4 * Math.sin(Math.PI * clamp01(e)) * hopDip(hopDeg);
};

// ---- landing (shared by autoplay and touch) ----

export type Landing = { drop: number; scale: number; ripple: number; callout: number; circuit: number };

/** A pin's landing, `l` seconds after the arc's head arrives (negative: not yet). */
export const landingAt = (l: number): Landing => {
  'worklet';
  if (l < 0) return { drop: 0, scale: 0, ripple: -1, callout: 0, circuit: 0 };
  const p = springAt(l, 0, 1, 0, SPRING.pop.d, SPRING.pop.k);
  const rip = l < 0.5 ? l / 0.5 : -1;
  const c = l < 0.12 ? 0 : springAt(l - 0.12, 0, 1, 0, SPRING.pop.d, SPRING.pop.k);
  return { drop: -10 * (1 - p), scale: mix(0.6, 1, p), ripple: rip, callout: c, circuit: easeOut(seg(l, 0.22, 0.6)) };
};

// ---- the autoplay timeline ----

/** Cards shown by the autoplay, slot by slot: R15, R16, R17, then the shuffle riffles back to R15. */
export const AUTO_TRACK = [14, 15, 16, 21, 8, 2, 18, 10, 14];

export type Frame = {
  lat: number;
  lon: number;
  zoom: number;
  sun: number[];
  /** camera longitude speed, deg/s (spin blur) */
  spin: number;
  cardPos: number;
  /** 0: AUTO_TRACK slots, 1: one slot per round */
  track: number;
  press: number;
  /** route progress in round indices: arc j (round j → j + 1) is drawn by clamp(arcPos − j) */
  arcPos: number;
  comet: number;
  selF: number;
  numbered: number;
  from: number;
  leave: number;
  to: number;
  land: number;
  calloutOut: number;
  sheen: number;
  sheenSlot: number;
  hour: number;
  shuffle: number;
  shuffleRot: number;
  /** seconds since a tap on the callout re-stroked the circuit, −1 none */
  pulse: number;
  t: number;
};

// flight 1 leans late: still west of the terminator at the poster (t 1.9), then it falls into the night
const F1 = { a: 0.7, b: 2.75, land: 2.8, lean: 1.45 };
const F2 = { a: 4.3, b: 5.6, land: 5.7, lean: 1.18 };
const SPIN = { a: 7.1, dur: 1.6, land: 8.62 };
const HOP1 = angle3(W[14], W[15]);
const HOP2 = angle3(W[15], W[16]);

/** Spin progress and speed (fraction of the turn per second) τ seconds into the shuffle: in 0.4, linear 0.5, out 0.7. */
export const SPIN_V = 1 / (0.2 + 0.5 + 0.7 / 3);
export const spinAt = (tau: number) => {
  'worklet';
  const V = SPIN_V;
  if (tau <= 0) return { p: 0, v: 0 };
  if (tau < 0.4) return { p: (V * tau * tau) / 0.8, v: (V * tau) / 0.4 };
  if (tau < 0.9) return { p: V * 0.2 + V * (tau - 0.4), v: V };
  if (tau < 1.6) {
    const s = (tau - 0.9) / 0.7;
    return { p: V * 0.7 + (V * 0.7 * (1 - (1 - s) * (1 - s) * (1 - s))) / 3, v: V * (1 - s) * (1 - s) };
  }
  return { p: 1, v: 0 };
};
/** Westward, a full turn plus the short way from Baku back to Zandvoort. */
const SPIN_DLON = LON[14] - 360 - LON[16];

/** The flight's path progress: slow out of the pin, a long glide in, and a weighty settle ≤ 1.5°. */
const flightBase = (t: number, a: number, b: number, lean: number) => {
  'worklet';
  return sineInOut(Math.pow(seg(t, a, b), lean));
};
const flightE = (t: number, a: number, b: number, hop: number, lean: number) => {
  'worklet';
  const base = flightBase(t, a, b, lean);
  const s = seg(t, b - 0.3, b + 0.25);
  return base + (1.4 / hop) * Math.sin(Math.PI * s) * (1 - s);
};

/** The comet's head runs a little ahead of the camera and arrives with it. */
const arcDraw = (t: number, a: number, b: number, lean: number) => {
  'worklet';
  const e = flightBase(t, a, b, lean);
  return clamp01(e + 0.14 * Math.sin(Math.PI * e));
};

/** The card row's drag then release: the finger pulls 45 % of a page in 0.5 s and lets go into `glide`. */
const pageAt = (t: number, a: number) => {
  'worklet';
  const k = seg(t, a, a + 0.5);
  if (t < a + 0.5) return 0.45 * k * k;
  return springAt(t - a - 0.5, 0.45, 1, 1.8, SPRING.glide.d, SPRING.glide.k);
};

export const frameAt = (time: number): Frame => {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  const drift = 1.2 * Math.sin((2 * Math.PI * 3 * t) / LOOP);
  let lat = LAT[14];
  let lon = LON[14];
  let zoom = 1;
  let sun = SUN[14];
  let spin = 0;
  let cardPos = 0;
  let arcPos = 14;
  let comet = 0;
  let selF = 14;
  let numbered = 1;
  let from = 14;
  let to = 14;
  let leave = 0;
  let land = 9;
  let calloutOut = 0;
  let sheen = -1;
  let sheenSlot = 0;
  let press = 0;
  let shuffle = 0;
  let shuffleRot = 0;

  if (t < F2.a) {
    // R15 → R16: the long hop east into the night
    const e = flightE(t, F1.a, F1.b, HOP1, F1.lean);
    const c = slerp3(W[14], W[15], e);
    lat = latOf(c);
    lon = lonOf(c);
    zoom = zoomFor(e, HOP1);
    sun = slerp3(SUN[14], SUN[15], easeOut(seg(t, F1.a, 2.5)));
    cardPos = t < F1.a ? 0 : pageAt(t, F1.a);
    arcPos = 14 + arcDraw(t, F1.a, F1.b, F1.lean);
    comet = 1;
    selF = 14 + smooth(seg(t, 0.8, 2.8));
    from = 14;
    to = t < F1.a ? 14 : 15;
    leave = seg(t, F1.a, F1.a + 0.3);
    land = t < F1.a ? 9 : t - F1.land;
    calloutOut = t < F1.a ? seg(t, 0.6, 0.7) : seg(t, F2.a - 0.1, F2.a);
    press = t < 1.2 ? seg(t, 0.6, 0.7) : 1 - seg(t, 1.2, 1.32);
    press = Math.max(press, seg(t, F2.a - 0.1, F2.a));
    sheen = t >= 2.9 ? easeInOut(seg(t, 2.9, 3.6)) : -1;
    sheenSlot = 1;
  } else if (t < SPIN.a) {
    // R16 → R17: a medium hop back west, low sun over Baku
    const e = flightE(t, F2.a, F2.b, HOP2, F2.lean);
    const c = slerp3(W[15], W[16], e);
    lat = latOf(c);
    lon = lonOf(c);
    zoom = zoomFor(e, HOP2);
    sun = slerp3(SUN[15], SUN[16], seg(t, F2.a, F2.b));
    cardPos = 1 + pageAt(t, F2.a);
    arcPos = 15 + arcDraw(t, F2.a, F2.b, F2.lean);
    comet = 1;
    selF = 15 + smooth(seg(t, F2.a + 0.1, F2.land));
    from = 15;
    to = 16;
    leave = seg(t, F2.a, F2.a + 0.3);
    land = t - F2.land;
    calloutOut = seg(t, 7.0, 7.1);
    press = t < 4.8 ? 1 : 1 - seg(t, 4.8, 4.92);
    sheen = t >= 5.8 ? easeInOut(seg(t, 5.8, 6.5)) : -1;
    sheenSlot = 2;
    shuffle = seg(t, 7.0, 7.06);
    shuffleRot = 360 * clamp01(springAt(t - 7.0, 0, 1, 0, SPRING.snap.d, SPRING.snap.k));
  } else {
    // shuffle: more than a full turn west, braking on R15
    const tau = t - SPIN.a;
    const sp = spinAt(tau);
    lat = mix(LAT[16], LAT[14], smooth(sp.p));
    lon = LON[16] + SPIN_DLON * sp.p;
    spin = SPIN_DLON * sp.v;
    zoom = 1 - 0.3 * (sp.v / SPIN_V);
    sun = slerp3(SUN[16], SUN[14], sp.p);
    const sc = spinAt(Math.min(SPIN.dur, (tau * SPIN.dur) / 1.4));
    cardPos = 2 + 6 * sc.p;
    arcPos = 16 - 2 * smooth(seg(t, 7.15, 7.6));
    comet = 0;
    selF = t < 7.3 ? 16 : 14;
    numbered = t < 8.5 ? 1 - seg(t, 7.1, 7.3) : seg(t, 8.5, 8.8);
    from = 16;
    to = 14;
    leave = seg(t, 7.05, 7.3);
    land = t - SPIN.land;
    shuffle = 1 - seg(t, 7.1, 7.25);
    shuffleRot = 360 * clamp01(springAt(t - 7.0, 0, 1, 0, SPRING.snap.d, SPRING.snap.k));
  }
  return {
    lat,
    lon: lon + drift,
    zoom,
    sun,
    spin,
    cardPos,
    track: 0,
    press,
    arcPos,
    comet,
    selF,
    numbered,
    from,
    leave,
    to,
    land,
    calloutOut,
    sheen,
    sheenSlot,
    hour: 0,
    shuffle,
    shuffleRot,
    pulse: -1,
    t,
  };
};

/** A unit vector for the camera's selected point (live mode keeps cameras as points). */
export const camVec = (latDeg: number, lonDeg: number) => {
  'worklet';
  return vecOf(latDeg, lonDeg);
};
export { norm3 as normalize3 };
