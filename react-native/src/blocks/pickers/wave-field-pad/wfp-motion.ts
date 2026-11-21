// Wave Field Pad: the autoplay timeline (creative spec §4) as a pure function of the loop time, so every frame of
// the loop is reproducible and the three stacks can match it frame for frame. Times are in seconds; the puck lives
// in pad UV (0..1 each, u = Sounds → Waves, v = Easy → Intense) and is drawn in pad-local points.

import { G, SPRING } from './wfp-theme';

export const LOOP = 9;
/** The thumbnail: the first Chladni figure fully formed, the ember sweep on its way out. */
export const POSTER = 2.9;

const R = G.puckR;
const PW = G.pad.w;
const PH = G.pad.h;
/** Travel of the puck's centre inside the pad (pt). */
export const RANGE_X = PW - 2 * R;
export const RANGE_Y = PH - 2 * R;

export const seg = (t: number, a: number, b: number) => {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
};
export const clamp01 = (v: number) => {
  'worklet';
  return Math.min(Math.max(v, 0), 1);
};
export const mix = (a: number, b: number, k: number) => {
  'worklet';
  return a + (b - a) * k;
};
export const smooth = (a: number, b: number, x: number) => {
  'worklet';
  const k = clamp01((x - a) / (b - a));
  return k * k * (3 - 2 * k);
};

/** CSS cubic-bezier(x1, y1, x2, y2) at progress k (Newton on x, then y). */
export const bezier = (x1: number, y1: number, x2: number, y2: number, k: number) => {
  'worklet';
  if (k <= 0) return 0;
  if (k >= 1) return 1;
  let s = k;
  for (let i = 0; i < 6; i++) {
    const x = 3 * (1 - s) * (1 - s) * s * x1 + 3 * (1 - s) * s * s * x2 + s * s * s - k;
    const dx = 3 * (1 - s) * (1 - s) * x1 + 6 * (1 - s) * s * (x2 - x1) + 3 * s * s * (1 - x2);
    if (Math.abs(dx) < 1e-6) break;
    s = Math.min(Math.max(s - x / dx, 0), 1);
  }
  return 3 * (1 - s) * (1 - s) * s * y1 + 3 * (1 - s) * s * s * y2 + s * s * s;
};
// design-system curves
export const eOut = (k: number) => {
  'worklet';
  return bezier(0.2, 0.8, 0.2, 1, k);
};
export const eIn = (k: number) => {
  'worklet';
  return bezier(0.32, 0, 0.67, 0, k);
};
export const eInOut = (k: number) => {
  'worklet';
  return bezier(0.65, 0, 0.35, 1, k);
};
export const eEmph = (k: number) => {
  'worklet';
  return bezier(0.05, 0.7, 0.1, 1, k);
};
/** The sand's contraction: spread over its whole 900 ms (eEmph put 80 % of it in the first 200 ms). */
export const eSettle = (k: number) => {
  'worklet';
  return bezier(0.3, 0.35, 0.25, 1, k);
};

/**
 * A damped spring (mass 1) released at time 0 from `from` towards `to` with initial velocity v0 (units/s): its
 * position at time t. Closed form, so the loop stays a pure function of time.
 */
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
  if (z === 1) {
    const e = Math.exp(-w0 * t);
    return to + e * (x0 + (v0 + w0 * x0) * t);
  }
  const s = Math.sqrt(z * z - 1);
  const r1 = -w0 * (z - s);
  const r2 = -w0 * (z + s);
  const c2 = (v0 - r1 * x0) / (r2 - r1);
  const c1 = x0 - c2;
  return to + c1 * Math.exp(r1 * t) + c2 * Math.exp(r2 * t);
};

/** Maximum displayed distance past each wall, in design points at 402 × 874. */
export const MAX_OVERSHOOT = 14;
/** C2 at the wall: slope 1, curvature 0; asymptotically approaches 14 pt without a clamp. */
export const rubber = (over: number) => {
  'worklet';
  return MAX_OVERSHOOT * Math.tanh(over / MAX_OVERSHOOT);
};
/** A raw UV coordinate (may leave 0..1) → the displayed one, rubber-banded past the walls. */
export const bandUV = (raw: number, range: number) => {
  'worklet';
  if (raw > 1) return 1 + rubber((raw - 1) * range) / range;
  if (raw < 0) return -rubber(-raw * range) / range;
  return raw;
};

/** Displayed UV → raw UV, for capture. The epsilon handles only floating-point saturation. */
export const unbandUV = (shown: number, range: number) => {
  'worklet';
  const over = shown > 1 ? (shown - 1) * range : shown < 0 ? -shown * range : 0;
  if (over === 0) return shown;
  const q = Math.min(over / MAX_OVERSHOOT, 1 - 1e-12);
  const rawOver = MAX_OVERSHOOT * 0.5 * (Math.log1p(q) - Math.log1p(-q)) / range;
  return shown > 1 ? 1 + rawOver : -rawOver;
};

/** Pad UV → pad-local points (origin at the pad's top-left corner, y down). */
export const uvToPad = (u: number, v: number) => {
  'worklet';
  return { x: R + u * RANGE_X, y: R + (1 - v) * RANGE_Y };
};

/** The read-out the pad drives: a continuous Hz value (the label shows it rounded). */
export const hzAt = (u: number, v: number) => {
  'worklet';
  const a = (clamp01(u) + clamp01(v)) / 2;
  return Math.min(Math.max(1 + 6.9 * (a - 0.5), 0.5), 4.2);
};
/** Wave amplitude follows intensity (v), the strands' spread follows the mix (u). */
export const waveAmp = (v: number) => {
  'worklet';
  const k = clamp01(v);
  return k >= 0.5 ? 14 + 78.6 * (k - 0.5) : 14 + 23 * (k - 0.5);
};
export const waveSpread = (u: number) => {
  'worklet';
  const k = clamp01(u);
  return k >= 0.5 ? 6 + 26.7 * (k - 0.5) : 6 + 14.3 * (k - 0.5);
};


// ---- the puck's path (Pro addendum timing: seam rest 600 ms, holds ≤ 300 ms, a third figure at the centre) ----
const A0 = { u: 0.5, v: 0.5 };
const A1 = { u: 0.8, v: 0.78 };
const A2 = { u: 0.22, v: 0.24 };
const FLING_PEAK = { u: 1.06, v: 0.55 };
const REST2 = { u: 0.9, v: 0.56 };
const T_MOVE1 = 0.42;
const T_STOP1 = 1.9;
const T_MOVE2 = 3.3;
const T_STOP2 = 4.6;
const T_FLING = 5.4;
const T_PEAK = 5.75;
export const T_HOME = 6.8;
const T_FIG3 = 7.7;
const T_SCATTER = 8.4;
/** A ring ripple lives this long (s). */
export const RIP_LIFE = 0.6;

const quad = (a: number, c: number, b: number, s: number) => {
  'worklet';
  return (1 - s) * (1 - s) * a + 2 * (1 - s) * s * c + s * s * b;
};

/** Raw puck UV at loop time t (before the rubber band). */
const puckRaw = (t: number) => {
  'worklet';
  if (t < T_MOVE1) return A0;
  if (t < T_STOP1) {
    const s = eInOut(seg(t, T_MOVE1, T_STOP1));
    return { u: quad(A0.u, 0.58, A1.u, s), v: quad(A0.v, 0.8, A1.v, s) };
  }
  if (t < T_MOVE2) return A1;
  if (t < T_STOP2) {
    const s = eInOut(seg(t, T_MOVE2, T_STOP2));
    return { u: quad(A1.u, 0.6, A2.u, s), v: quad(A1.v, 0.36, A2.v, s) };
  }
  if (t < T_FLING) return A2;
  if (t < T_PEAK) {
    // thrown: fast off the finger, decaying into the right wall
    const s = 1 - Math.pow(1 - seg(t, T_FLING, T_PEAK), 3);
    return { u: mix(A2.u, FLING_PEAK.u, s), v: mix(A2.v, FLING_PEAK.v, s) };
  }
  // Stay in raw space through the spring. puckAt applies the band exactly once.
  const pk = FLING_PEAK;
  if (t < T_HOME) {
    const s = t - T_PEAK;
    return { u: springAt(s, pk.u, REST2.u, 0, SPRING.weighty.d, SPRING.weighty.k), v: springAt(s, pk.v, REST2.v, 0, SPRING.weighty.d, SPRING.weighty.k) };
  }
  // the double tap: home with a pop (what is left of the weighty spring is carried into the pop's start)
  const s0 = T_HOME - T_PEAK;
  const fu = springAt(s0, pk.u, REST2.u, 0, SPRING.weighty.d, SPRING.weighty.k);
  const fv = springAt(s0, pk.v, REST2.v, 0, SPRING.weighty.d, SPRING.weighty.k);
  const s = t - T_HOME;
  return { u: springAt(s, fu, A0.u, 0, SPRING.pop.d, SPRING.pop.k), v: springAt(s, fv, A0.v, 0, SPRING.pop.d, SPRING.pop.k) };
};

/** Displayed puck UV at loop time t (rubber band applied). */
export const puckAt = (tIn: number) => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  // the pop home is within 1e-6 of the centre by 8.2 s: pin it so frame LOOP equals frame 0 exactly
  if (t > 8.2) return A0;
  const p = puckRaw(t);
  return { u: bandUV(p.u, RANGE_X), v: bandUV(p.v, RANGE_Y) };
};

/** The first moment the fling reaches the right wall (u = 1): the ripple starts there. */
const T_WALL = (() => {
  let lo = T_FLING;
  let hi = T_PEAK;
  for (let i = 0; i < 30; i++) {
    const m = (lo + hi) / 2;
    if (puckRaw(m).u >= 1) hi = m;
    else lo = m;
  }
  return hi;
})();
const WALL_V = puckRaw(T_WALL).v;

// ---- tables built once (pure functions of t, sampled): the sheet's spring-lagged dent and the integrated beat ----
const STEP = 0.005;
const N = Math.round(LOOP / STEP);

/** The dent centre follows the puck through the `sheet` spring; simulated over two loops so the table is periodic. */
const DENT: number[][] = (() => {
  const u: number[] = [];
  const v: number[] = [];
  let x = 0.5;
  let y = 0.5;
  let vx = 0;
  let vy = 0;
  const h = 0.001;
  const sub = Math.round(STEP / h);
  for (let loop = 0; loop < 2; loop++) {
    for (let i = 0; i <= N; i++) {
      if (loop === 1) {
        u.push(x);
        v.push(y);
      }
      if (i === N) break;
      for (let j = 0; j < sub; j++) {
        const p = puckAt(i * STEP + j * h);
        vx += (-SPRING.sheet.k * (x - p.u) - SPRING.sheet.d * vx) * h;
        vy += (-SPRING.sheet.k * (y - p.v) - SPRING.sheet.d * vy) * h;
        x += vx * h;
        y += vy * h;
      }
    }
  }
  return [u, v];
})();
const DENT_U = DENT[0];
const DENT_V = DENT[1];

const BEAT: number[] = (() => {
  const out = [0];
  let acc = 0;
  for (let i = 0; i < N; i++) {
    const p = puckAt((i + 0.5) * STEP);
    acc += hzAt(p.u, p.v) * STEP;
    out.push(acc);
  }
  const whole = 2 * Math.round(acc / 2);
  return out.map((x) => (x * whole) / acc);
})();

const sample = (tab: number[], tIn: number) => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const f = t / STEP;
  const i = Math.min(Math.floor(f), N - 1);
  const k = f - i;
  return tab[i] + (tab[i + 1] - tab[i]) * k;
};
/** Beat phase in cycles at loop time t (whole and even at t = LOOP, so the wave closes the loop). */
export const beatAt = (t: number) => {
  'worklet';
  return sample(BEAT, t);
};

/** Everything the screen draws, for one moment. */
export type Frame = {
  u: number; // puck UV (displayed)
  v: number;
  du: number; // the sheet's dent centre (lags the puck, overshoots on a stop)
  dv: number;
  depth: number; // dent depth (pt)
  scale: number; // puck scale
  halo: number; // halo alpha at the rim
  settle: number; // Chladni figure 0..1
  ring: number; // payoff pulse ring progress 0..1 (0 = none)
  lock: number; // the wave locked into a standing wave 0..1
  flash: number; // the standing wave's nodes flash 0..1
  rip0: [number, number, number, number]; // x, y (pad pt), age (s), amplitude (pt)
  rip1: [number, number, number, number];
  wallX: number; // the wall's bulge (pt, + right / − left)
  wallY: number; // (+ down / − up)
  beat: number; // beat phase (cycles)
  hz: number; // continuous read-out
  scatterX: number; // grain shift opposite to the puck's travel (pt)
  scatterY: number;
  velX: number; // puck velocity (pt/s, pad space, y down): the sand's wake
  velY: number;
  jit: number; // grain shimmer step (30 Hz, wraps with the loop)
};

const NO_RIP: [number, number, number, number] = [0, 0, 9, 0];

/** 0..1: a finger pressing the puck (anticipation squash, deeper dent, brighter halo). */
const pressAt = (t: number) => {
  'worklet';
  if (t < 0.3) return 0;
  if (t < T_MOVE1) return eOut(seg(t, 0.3, T_MOVE1));
  if (t < T_STOP1) return 1;
  if (t < 2.2) return 1 - eOut(seg(t, T_STOP1, 2.2));
  if (t < 3.18) return 0;
  if (t < T_MOVE2) return eOut(seg(t, 3.18, T_MOVE2));
  if (t < T_STOP2) return 1;
  if (t < 4.9) return 1 - eOut(seg(t, T_STOP2, 4.9));
  if (t < 5.28) return 0;
  if (t < T_FLING) return eOut(seg(t, 5.28, T_FLING));
  if (t < 5.52) return 1 - eOut(seg(t, T_FLING, 5.52));
  return 0;
};

/** The hum while the plate sings: 3 Hz, faded in and out so it starts and ends at rest. */
const humAt = (t: number) => {
  'worklet';
  const w1 = t > T_STOP1 && t < 3.3 ? Math.sin(Math.PI * seg(t, T_STOP1, 3.3)) : 0;
  const w2 = t > T_STOP2 && t < 5.4 ? Math.sin(Math.PI * seg(t, T_STOP2, 5.4)) : 0;
  const w3 = t > T_FIG3 && t < 8.6 ? Math.sin(Math.PI * seg(t, T_FIG3, 8.6)) : 0;
  const w = Math.max(w1, w2, w3);
  return { s: 0.012 * w * Math.sin(2 * Math.PI * 3 * t), glow: w };
};

export const settleAt = (t: number) => {
  'worklet';
  if (t < T_STOP1) return 0;
  if (t < 2.8) return eSettle(seg(t, T_STOP1, 2.8));
  if (t < T_MOVE2) return 1;
  if (t < 3.6) return 1 - eIn(seg(t, T_MOVE2, 3.6));
  if (t < T_STOP2) return 0;
  if (t < 5.3) return eSettle(seg(t, T_STOP2, 5.3));
  if (t < T_FLING) return 1;
  if (t < 5.55) return 1 - eIn(seg(t, T_FLING, 5.55));
  if (t < T_FIG3) return 0;
  if (t < T_SCATTER) return eSettle(seg(t, T_FIG3, T_SCATTER));
  if (t < 8.7) return 1 - eIn(seg(t, T_SCATTER, 8.7));
  return 0;
};

const lockAt = (t: number) => {
  'worklet';
  if (t >= 2.8 && t < 3.6) return eOut(seg(t, 2.8, 2.88)) * (1 - eIn(seg(t, T_MOVE2, 3.45)));
  if (t >= 5.2 && t < 5.6) return eOut(seg(t, 5.2, 5.3)) * (1 - eIn(seg(t, T_FLING, 5.5)));
  if (t >= 8.2 && t < 8.7) return eOut(seg(t, 8.2, 8.3)) * (1 - eIn(seg(t, T_SCATTER, 8.55)));
  return 0;
};

/** Grain shifts against the puck's travel (0.3 × its offset from the centre): the sheet's surface slides. */
export const scatterOf = (u: number, v: number) => {
  'worklet';
  return { x: -0.3 * (u - 0.5) * RANGE_X, y: 0.3 * (v - 0.5) * RANGE_Y };
};

/** The halo breathes at rest: α 0.38 ↔ 0.50, period 1.5 s (six whole breaths a loop). */
export const breathAt = (t: number) => {
  'worklet';
  return 0.06 * (1 - Math.cos((2 * Math.PI * 6 * t) / LOOP));
};

/** The frame at loop time t. */
export const frameAt = (tIn: number): Frame => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const p = puckAt(t);
  const pb = puckAt(t - 1 / 60);
  const press = pressAt(t);
  const hum = humAt(t);
  // the double tap's blip: 1 → 1.08 → 1 over 0.36 s
  const blip = t >= T_HOME && t < T_HOME + 0.36 ? 0.08 * Math.sin(Math.PI * seg(t, T_HOME, T_HOME + 0.36)) : 0;
  const scale = 1 - 0.06 * press + blip + hum.s;
  const halo = 0.38 + 0.17 * press + 0.07 * hum.glow + breathAt(t) * (1 - press);
  const depth = 16 + 6 * press;
  const settle = settleAt(t);
  const ring = t >= 2.8 && t < 3.3 ? seg(t, 2.8, 3.3) : 0;
  const lock = lockAt(t);
  const flash = t >= 2.8 && t < 3.3 ? Math.sin(Math.PI * seg(t, 2.8, 3.3)) : 0;
  // ripple 0: from the right wall where the fling hit it; ripple 1: from where the double tap took the puck home,
  // then from the centre when the third figure is scattered
  const a1 = t - T_WALL;
  const wallPt = uvToPad(1, WALL_V);
  const rip0: [number, number, number, number] = a1 >= 0 && a1 < RIP_LIFE ? [G.pad.w, wallPt.y, a1, 10 * (1 - eOut(a1 / RIP_LIFE))] : NO_RIP;
  const a2 = t - T_HOME;
  const homePt = uvToPad(REST2.u, REST2.v);
  const a3 = t - T_SCATTER;
  const midPt = uvToPad(0.5, 0.5);
  let rip1: [number, number, number, number] = NO_RIP;
  if (a2 >= 0 && a2 < RIP_LIFE) rip1 = [homePt.x, homePt.y, a2, 8 * (1 - eOut(a2 / RIP_LIFE))];
  else if (a3 >= 0 && a3 < RIP_LIFE) rip1 = [midPt.x, midPt.y, a3, 8 * (1 - eOut(a3 / RIP_LIFE))];
  const overX = Math.max(0, p.u - 1) * RANGE_X - Math.max(0, -p.u) * RANGE_X;
  const overY = Math.max(0, -p.v) * RANGE_Y - Math.max(0, p.v - 1) * RANGE_Y;
  const sc = scatterOf(p.u, p.v);
  return {
    u: p.u,
    v: p.v,
    du: sample(DENT_U, t),
    dv: sample(DENT_V, t),
    depth,
    scale,
    halo,
    settle,
    ring,
    lock,
    flash,
    rip0,
    rip1,
    wallX: overX * 0.8,
    wallY: overY * 0.8,
    beat: beatAt(t),
    hz: hzAt(p.u, p.v),
    scatterX: sc.x,
    scatterY: sc.y,
    velX: (p.u - pb.u) * RANGE_X * 60,
    velY: -(p.v - pb.v) * RANGE_Y * 60,
    jit: Math.floor(t * 30) % Math.round(LOOP * 30),
  };
};
