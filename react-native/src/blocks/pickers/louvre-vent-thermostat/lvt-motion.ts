// Louvre Vent Thermostat: the autoplay timeline (spec §4 with the Pro addendum) as a pure function of the loop time,
// so every frame is reproducible and the Swift and Kotlin ports can match it frame for frame. Times in seconds,
// temperatures in °C, distances in pt, blade tilts in degrees (0 = shut flat, + = face turned up toward the light).

import { G, T_MAX, T_MIN, T_REST } from './lvt-theme';

export const LOOP = 9;
/** The poster frame: 30.0 seated, blades open about 50°, white-hot gaps. */
export const POSTER = 2.5;
/** Blades kept per frame (the vent shows 22.5 at a time). */
export const NB = 26;

const PITCH = G.pitch;
const PER_C = G.perC;
const LINE_Y = G.lineY;
const VENT_TOP = G.vent.y;
const VENT_BOT = G.vent.y + G.vent.h;

export const wrap = (t: number) => {
  'worklet';
  return ((t % LOOP) + LOOP) % LOOP;
};
export const seg = (t: number, a: number, b: number) => {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
};
export const clamp = (v: number, a: number, b: number) => {
  'worklet';
  return Math.min(Math.max(v, a), b);
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const smooth = (a: number, b: number, v: number) => {
  'worklet';
  const k = Math.min(Math.max((v - a) / (b - a), 0), 1);
  return k * k * (3 - 2 * k);
};
export const easeOut = (k: number) => {
  'worklet';
  return 1 - (1 - k) * (1 - k) * (1 - k);
};
export const easeInOut = (k: number) => {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
};

// ---- the value ----

const FL = 6; // the fling's decay rate (1/s)
const FL_D = 1.2;
const FL_N = 1 - Math.exp(-FL * FL_D) * (1 + FL * FL_D);

/**
 * The set temperature at loop time t: rest 24.0, a drag down to 30.0 (0.62–2.1 s), one pull up to 17.5 (3.2–4.5 s),
 * a flick back that decays onto 24.0 (5.7–6.9 s).
 */
export const tAt = (tIn: number) => {
  'worklet';
  const t = wrap(tIn);
  if (t < 0.62) return 24;
  if (t < 2.1) return 24 + 6 * easeInOut(seg(t, 0.62, 2.1));
  if (t < 3.2) return 30;
  if (t < 4.5) return 30 - 12.5 * easeInOut(seg(t, 3.2, 4.5));
  if (t < 5.7) return 17.5;
  if (t < 6.9) {
    const x = FL * (t - 5.7);
    return 17.5 + (6.5 * (1 - Math.exp(-x) * (1 + x))) / FL_N;
  }
  return 24;
};

/** Content speed (pt/s, + = blades moving down = warming). */
export const vAt = (t: number) => {
  'worklet';
  const h = 0.004;
  return ((tAt(t + h) - tAt(t - h)) / (2 * h)) * PER_C;
};

/** The chamber's heat lags the value by a few hundred ms (it is a fire, not a number). */
export const heatAt = (t: number) => {
  'worklet';
  let s = 0;
  for (let i = 0; i < 8; i++) s += tAt(t - 0.06 * i);
  return s / 8;
};

// ---- blades ----

/** Resting tilt for a value plus the tilt the drag adds (addendum upgrade 2). */
export const thetaOf = (T: number, v: number) => {
  'worklet';
  const th = 4 + (52 * (clamp(T, T_MIN, T_MAX) - 16)) / 16 + 22 * Math.tanh(v / 260);
  return clamp(th, -68, 68);
};

/** Turn a blade toward shut by `a` degrees (a < 0 opens it); it never passes through flat. */
export const shut = (th: number, a: number) => {
  'worklet';
  const s = th < 0 ? -1 : 1;
  return s * Math.max(Math.abs(th) - a, 0);
};

/** The clack: overshoot 14° toward shut, then seat (closed-form spring, ω 30, ζ 0.6). */
export const clackDip = (tau: number) => {
  'worklet';
  if (tau <= 0 || tau > 0.7) return 0;
  return 35.25 * Math.exp(-18 * tau) * Math.sin(24 * tau);
};

/** Order in which the clack reaches a blade at screen y (12 ms apart). */
export const clackDelay = (y: number, dir: number) => {
  'worklet';
  const o = dir > 0 ? (y - VENT_TOP) / PITCH : (VENT_BOT - y) / PITCH;
  return 0.012 * clamp(o, 0, 23);
};

/** Thumb press: blades under the finger turn 12° toward shut (gaussian, σ 1.5 blades). */
export const pressAt = (y: number, thumbY: number, press: number) => {
  'worklet';
  if (press <= 0) return 0;
  const d = (y - thumbY) / PITCH;
  return 12 * press * Math.exp(-(d * d) / (2 * 1.5 * 1.5));
};

const CLACKS: [number, number][] = [
  [2.1, 1],
  [4.5, -1],
  [6.9, 1],
];

/** The latest clack of the loop: seconds since it started (9 = none yet) and its direction (+1 top → bottom). */
export const clackStateAt = (t: number) => {
  'worklet';
  let clackT = 9;
  let clackDir = 1;
  for (let i = 0; i < CLACKS.length; i++) {
    if (t >= CLACKS[i][0]) {
      clackT = t - CLACKS[i][0];
      clackDir = CLACKS[i][1];
    }
  }
  return { clackT, clackDir };
};

/** The virtual thumb of the demo: where it is and how hard it presses. */
export const touchAt = (t: number) => {
  'worklet';
  if (t >= 0.5 && t < 2.2) {
    const pr = t < 0.62 ? easeOut(seg(t, 0.5, 0.62)) : 1 - seg(t, 2.1, 2.18);
    return { y: 430 + (tAt(t) - 24) * PER_C, pr };
  }
  if (t >= 3.08 && t < 4.6) {
    const y = 690 + (tAt(t) - 30) * PER_C;
    const pr = (t < 3.2 ? easeOut(seg(t, 3.08, 3.2)) : 1 - seg(t, 4.5, 4.58)) * smooth(250, 320, y);
    return { y, pr };
  }
  if (t >= 5.58 && t < 6.0) {
    const pr = t < 5.7 ? easeOut(seg(t, 5.58, 5.7)) : 1 - seg(t, 5.84, 5.94);
    return { y: 400 + (tAt(t) - 17.5) * PER_C, pr };
  }
  return { y: LINE_Y, pr: 0 };
};

/** Time at which tAt crosses `level` inside [a, b] (monotonic there). */
const crossTime = (level: number, a: number, b: number) => {
  'worklet';
  let lo = a;
  let hi = b;
  const up = tAt(b) > tAt(a);
  for (let i = 0; i < 14; i++) {
    const m = (lo + hi) / 2;
    const v = tAt(m);
    if (up ? v >= level : v <= level) hi = m;
    else lo = m;
  }
  return hi;
};

/** The selection glint of blade k (value L) for 80 ms after it crosses the line. */
const selGlint = (L: number, t: number, T1: number, T0: number) => {
  'worklet';
  const up = T1 > T0;
  if (T1 === T0) return 0;
  if (up ? L > T0 && L <= T1 : L < T0 && L >= T1) {
    const tc = crossTime(L, t - 0.08, t);
    return clamp(1 - (t - tc) / 0.08, 0, 1);
  }
  return 0;
};

/** Triangle nudge (pt, toward the left): the anticipations, and a tick per whole degree crossed slowly. */
const nudgeAt = (t: number) => {
  'worklet';
  let n = 0;
  const ants = [0.5, 3.08, 5.58];
  for (let i = 0; i < 3; i++) {
    const a = ants[i];
    n = Math.max(n, 2 * easeOut(seg(t, a, a + 0.12)) * (1 - easeInOut(seg(t, a + 0.12, a + 0.32))));
  }
  const T1 = tAt(t);
  const T0 = tAt(t - 0.15);
  if (T1 !== T0) {
    const lev = T1 > T0 ? Math.floor(T1) : Math.ceil(T1);
    if (T1 > T0 ? lev > T0 : lev < T0) {
      const tc = crossTime(lev, t - 0.15, t);
      const k = 1 - (t - tc) / 0.15;
      n = Math.max(n, 2 * k * k * (1 - smooth(150, 420, Math.abs(vAt(tc)))));
    }
  }
  return n;
};

// ---- convection in the dot fields (pure and loop-safe: a whole number of waves per loop) ----

/** Waves per second up the dot fields (+ up), from the chamber's heat: 60 → 220 pt/s hot, sinking 80 pt/s cold. */
export const convRate = (h: number) => {
  'worklet';
  return h >= 24 ? mix(60, 220, clamp((h - 24) / 6, 0, 1)) / 90 : mix(60, -80, clamp((24 - h) / 6.5, 0, 1)) / 90;
};
const CONV_N = 450;
const CONV: number[] = (() => {
  const a = [0];
  let acc = 0;
  for (let i = 0; i < CONV_N; i++) {
    acc += (convRate(heatAt(((i + 0.5) * LOOP) / CONV_N)) * LOOP) / CONV_N;
    a.push(acc);
  }
  const whole = Math.max(1, Math.round(acc));
  return a.map((v) => (v * whole) / acc);
})();
export const convAt = (t: number) => {
  'worklet';
  const u = (wrap(t) / LOOP) * CONV_N;
  const i = Math.min(Math.floor(u), CONV_N - 1);
  return mix(CONV[i], CONV[i + 1], u - i);
};

/** Everything the screen draws for one moment. */
export type Scene = {
  T: number; // value at the line
  v: number; // content speed (pt/s)
  s0: number; // 2(T − 16): blade coordinate at the line
  k0: number; // index of the first blade in `bl`
  bl: number[]; // NB × (tilt deg, glint 0..1)
  heat: number; // chamber heat (°C, lagging)
  conv: number; // convection phase (waves)
  nudge: number; // triangle nudge (pt)
  breath: number; // glow height offset (pt)
  time: number; // loop-safe clock for the chamber's noise (s, 0..LOOP)
  drum: [number, number, number]; // rolling digit positions: tenths, units, tens
};

/** Positions of the three digit strips for value T (they roll as the blade crosses the line, not before). */
export const drumOf = (T: number) => {
  'worklet';
  const q = 2 * (T - 16);
  const n = Math.floor(q);
  const e = smooth(0.4, 0.6, q - n);
  const D = n + e;
  const tenths = D - 2 * Math.floor(D / 2); // 0..2 on the strip [0, 5]
  const m = Math.floor(D);
  const units = 16 + Math.floor(m / 2) + (((m % 2) + 2) % 2 === 1 ? D - m : 0);
  const u10 = Math.floor(units);
  const tens = Math.floor(units / 10) + (u10 % 10 === 9 ? units - u10 : 0);
  return { tenths, units, tens };
};

/** Blade tilts and glints, shared by the loop and the live picker. */
export const bladeRow = (
  T: number,
  thetaAtDelay: (dk: number) => number,
  glintOf: (k: number, L: number) => number,
  thumbY: number,
  press: number,
  clackT: number,
  clackDir: number,
  slam: number,
) => {
  'worklet';
  const s0 = 2 * (T - 16);
  const k0 = Math.floor(s0 - (VENT_BOT - LINE_Y) / PITCH) - 1;
  const bl: number[] = [];
  for (let i = 0; i < NB; i++) {
    const k = k0 + i;
    const yk = LINE_Y - (k - s0) * PITCH;
    let th = thetaAtDelay(Math.abs(k - s0));
    if (slam > 0) th = mix(th, 68, slam);
    else if (slam < 0) th = mix(th, 0, -slam);
    th = shut(th, pressAt(yk, thumbY, press));
    const tau = clackT - clackDelay(yk, clackDir);
    th = shut(th, clackDip(tau));
    const cg = tau > 0 && tau < 0.4 ? 0.9 * Math.exp(-tau / 0.06) : 0;
    bl.push(th, Math.max(cg, glintOf(k, 16 + k / 2)));
  }
  return { s0, k0, bl };
};

export const sceneAt = (tIn: number): Scene => {
  'worklet';
  const t = wrap(tIn);
  const T = tAt(t);
  const touch = touchAt(t);
  const { clackT, clackDir } = clackStateAt(t);
  const T1 = T;
  const T0 = tAt(t - 0.08);
  const row = bladeRow(
    T,
    (dk) => {
      'worklet';
      const td = t - 0.016 * dk;
      return thetaOf(tAt(td), vAt(td));
    },
    (_k, L) => {
      'worklet';
      return selGlint(L, t, T1, T0);
    },
    touch.y,
    touch.pr,
    clackT,
    clackDir,
    0,
  );
  const breath = 3 * Math.sin((2 * Math.PI * 3 * t) / LOOP) + (t >= 2.5 && t < 3.2 ? 8 * Math.sin(2 * Math.PI * seg(t, 2.5, 3.2)) : 0);
  // one value for all three drums: a digit stagger would show a stale value at speed (gate 8)
  const d0 = drumOf(T);
  return {
    T,
    v: vAt(t),
    s0: row.s0,
    k0: row.k0,
    bl: row.bl,
    heat: heatAt(t),
    conv: convAt(t),
    nudge: nudgeAt(t),
    breath,
    time: t,
    drum: [d0.tenths, d0.units, d0.tens],
  };
};

export const REST_T = T_REST;
