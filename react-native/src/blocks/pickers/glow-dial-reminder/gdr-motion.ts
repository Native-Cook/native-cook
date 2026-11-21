// Glow Dial Reminder: the autoplay timeline (creative spec §4) as a pure function of the loop time, the dot numeral's
// migrations, the shear of the light and the gesture constants. Times are in seconds, angles in degrees.

import { DIGITS, EASE, G, RANGE, SEGMENTS, SPRING } from './gdr-theme';

export const LOOP = 10;

// ---------------------------------------------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------------------------------------------

/** 0 before a, 1 after b. */
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

/** A cubic-bezier easing (x1, y1, x2, y2) at u (Newton steps on x). */
export const bezier = (c: readonly number[], u: number) => {
  'worklet';
  if (u <= 0) return 0;
  if (u >= 1) return 1;
  const cx = 3 * c[0];
  const bx = 3 * (c[2] - c[0]) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * c[1];
  const by = 3 * (c[3] - c[1]) - cy;
  const ay = 1 - cy - by;
  let s = u;
  for (let i = 0; i < 8; i++) {
    const x = ((ax * s + bx) * s + cx) * s - u;
    const dx = (3 * ax * s + 2 * bx) * s + cx;
    if (Math.abs(x) < 1e-7 || Math.abs(dx) < 1e-6) break;
    s = Math.min(1, Math.max(0, s - x / dx));
  }
  return ((ay * s + by) * s + cy) * s;
};

/**
 * A damped spring (mass 1) released at time 0 from `from` towards `to` with velocity v0: position at time t.
 * Closed form, so the loop stays a pure function of time.
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
  const s = Math.sqrt(Math.max(z * z - 1, 1e-6));
  const r1 = -w0 * (z - s);
  const r2 = -w0 * (z + s);
  const c2 = (v0 - r1 * x0) / (r2 - r1);
  const c1 = x0 - c2;
  return to + c1 * Math.exp(r1 * t) + c2 * Math.exp(r2 * t);
};

/** An underdamped spring's position and velocity at time t (state handed on from x0, v0 towards `to`). */
const springPV = (t: number, x0: number, v0: number, to: number, d: number, k: number, out: number[], o: number) => {
  'worklet';
  const w0 = Math.sqrt(k);
  const z = d / (2 * w0);
  const wd = w0 * Math.sqrt(1 - z * z);
  const A = x0 - to;
  const B = (v0 + z * w0 * A) / wd;
  const e = Math.exp(-z * w0 * Math.max(t, 0));
  const c = Math.cos(wd * Math.max(t, 0));
  const s = Math.sin(wd * Math.max(t, 0));
  out[o] = to + e * (A * c + B * s);
  out[o + 1] = e * ((-z * w0 * A + wd * B) * c + (-z * w0 * B - wd * A) * s);
};
/** The `snap` spring's position and velocity at time t: for the dots, whose state is handed on. */
const snapPV = (t: number, x0: number, v0: number, to: number, out: number[], o: number) => {
  'worklet';
  springPV(t, x0, v0, to, SPRING.snap.d, SPRING.snap.k, out, o);
};

// ---------------------------------------------------------------------------------------------------------------
// the value and the knob
// ---------------------------------------------------------------------------------------------------------------

/**
 * The phases of the loop (§4), re-timed in v2: no hold is longer than ~0.4 s, the turn up is longer and overshoots,
 * and the drain (the gasp) runs 4.45 to 5.65 s through the vein.
 */
export const T = {
  anticA: 0.1,
  turnA: 0.25,
  turnPeak: 1.95,
  turnB: 2.4,
  ringA: 1.95,
  ringB: 2.6,
  backA: 2.6,
  backB: 3.9,
  thumbPress: 4.3,
  drainA: 4.45,
  drainLag: 0.05,
  drainDur: 1.2,
  offA: 5.0,
  bloomA: 6.0,
  bloomLag: 0.05,
  dotsBackA: 6.4,
  returnA: 6.8,
  returnPeak: 8.55,
  returnB: 9.5,
  ring2A: 9.35,
  ring2B: 9.95,
} as const;

/** Degrees of knob turn per hour, hours per 15-minute detent, ruler points per hour. */
export const DEG_PER_HOUR = 30;
export const RULER_PER_HOUR = G.ruler.perHour;
/** Release projection (s), rubber band past the ends (degrees), the glow's lag behind the knob (s). */
export const PROJECT = 0.12;
export const RUBBER = 40;
export const GLOW_LAG = 0.2;
/** The light's shear: ω low-passed with τ 120 ms, integrated with a 450 ms leak, soft-clamped at ±1.6 rad. */
export const OMEGA_TAU = 0.12;
export const SHEAR_TAU = 0.45;
export const SHEAR_MAX = 1.6;
/** The outer dots of the numeral and the rim of the pool answer the shear this much later (s). */
export const OUTER_LAG = 0.04;

/** The knob's main angle (degrees from the 8 h rest), anticipation excluded. */
const hermite = (u: number, a: number, b: number, va: number, vb: number, duration: number) => {
  'worklet';
  const p = clamp01(u);
  return (2 * p * p * p - 3 * p * p + 1) * a + (p * p * p - 2 * p * p + p) * duration * va + (-2 * p * p * p + 3 * p * p) * b + (p * p * p - p * p) * duration * vb;
};
/** Time, angle (deg) and slope (deg/s) knots of the two turns that are not a plain ease; Hermite between them. */
const UP_KNOTS: readonly (readonly [number, number, number])[] = [
  [T.turnA, 0, 0],
  [0.75, 30, 80], // 9 h
  [1.17, 60, 86], // 10 h
  [1.65, 90, 30], // 11 h
  [T.turnPeak, 93, 0], // the flick carries a little past 11 h (not into the next detent) ...
  [T.turnB, 90, 0], // ... and settles back on it
];
const RET_KNOTS: readonly (readonly [number, number, number])[] = [
  [T.returnA, -60, 0],
  [T.returnPeak, 22.5, 0], // a flick past 8 h (to 8.75 h) ...
  [T.returnB, 0, 0], // ... and the second, small turn back home
];
const knotsAt = (k: readonly (readonly [number, number, number])[], t: number) => {
  'worklet';
  for (let i = 0; i < k.length - 1; i++) {
    if (t < k[i + 1][0]) return hermite((t - k[i][0]) / (k[i + 1][0] - k[i][0]), k[i][1], k[i + 1][1], k[i][2], k[i + 1][2], k[i + 1][0] - k[i][0]);
  }
  return k[k.length - 1][1];
};
export const thMainAt = (t: number) => {
  'worklet';
  if (t < T.turnA) return 0;
  if (t < T.turnB) return knotsAt(UP_KNOTS, t);
  if (t < T.backA) return 90;
  if (t < T.backB) return 90 - 150 * bezier(EASE.inOut, seg(t, T.backA, T.backB));
  if (t < T.returnA) return -60;
  if (t < T.returnB) return knotsAt(RET_KNOTS, t);
  return 0;
};

/** The dip before the twist: −4° over 150 ms (`in`), taken back as the twist starts. */
export const anticAt = (t: number) => {
  'worklet';
  if (t < T.anticA || t >= T.turnB) return 0;
  if (t < T.turnA) return -4 * bezier(EASE.in, seg(t, T.anticA, T.turnA));
  return -4 * (1 - bezier(EASE.emph, seg(t, T.turnA, T.turnA + 0.5)));
};

/** Hours shown for a knob angle. */
export const vOfTh = (th: number) => {
  'worklet';
  return RANGE.rest + th / DEG_PER_HOUR;
};
/** The glow's radius scale for a value: 4 h → 0.62, 8 h → 1, 12 h → 1.38. */
export const gOf = (v: number) => {
  'worklet';
  const c = Math.min(Math.max(v, RANGE.min), RANGE.max);
  return 0.62 + (0.38 * (c - 4)) / 4;
};
/** The whole hour the numeral shows (a 0.02 h guard so a spring settling from below does not flicker). */
export const numOf = (v: number) => {
  'worklet';
  return Math.floor(Math.min(Math.max(v, RANGE.min), RANGE.max) + 0.02);
};

// the shear table: the light is stirred by the knob's angular velocity (precomputed once, so the loop is exact)
const SHEAR_N = LOOP * 240;
const SHEAR_DT = LOOP / SHEAR_N;
const thKnobAt = (t: number) => {
  'worklet';
  return thMainAt(t) + anticAt(t);
};
const buildShear = () => {
  const raw = new Array<number>(SHEAR_N).fill(0);
  const om = new Array<number>(SHEAR_N).fill(0);
  let w = 0;
  let s = 0;
  const aw = 1 - Math.exp(-SHEAR_DT / OMEGA_TAU);
  // two loops so the recorded one is periodic
  for (let pass = 0; pass < 2; pass++) {
    for (let i = 0; i < SHEAR_N; i++) {
      const t = i * SHEAR_DT;
      const wr = (((thKnobAt(t + SHEAR_DT * 0.5) - thKnobAt(t - SHEAR_DT * 0.5)) / SHEAR_DT) * Math.PI) / 180;
      w += (wr - w) * aw;
      s += (w - s / SHEAR_TAU) * SHEAR_DT;
      if (pass === 1) {
        raw[i] = s;
        om[i] = w;
      }
    }
  }
  // scale so the first twist peaks at +1.1 rad after the soft clamp
  let peak = 0;
  for (let i = Math.floor(T.turnA / SHEAR_DT); i < Math.floor(T.backA / SHEAR_DT); i++) peak = Math.max(peak, raw[i]);
  const k = (SHEAR_MAX * Math.atanh(1.1 / SHEAR_MAX)) / Math.max(peak, 1e-6);
  return { s: raw.map((x) => x * k), w: om, k };
};
const SHEAR = buildShear();
const SHEAR_S = SHEAR.s;
const SHEAR_W = SHEAR.w;
/** Gain from ω (rad/s) to the raw shear, shared by the live gesture. */
export const SHEAR_K = SHEAR.k;

/** Soft clamp of the raw shear to ±1.6 rad. */
export const shearOut = (raw: number) => {
  'worklet';
  return SHEAR_MAX * Math.tanh(raw / SHEAR_MAX);
};
/**
 * The windows in which the knob is stirring the light: the shear is closed to zero in 1.2 s after each (and before the
 * loop seam), eased in just before the next. Continuous everywhere, so it never jumps.
 */
const STIR: readonly (readonly [number, number])[] = [
  [T.anticA, T.turnB],
  [T.backA - 0.12, T.backB],
  [T.returnA, T.returnB],
];
const stirEnvelope = (u: number) => {
  'worklet';
  let e = 0;
  for (let i = 0; i < STIR.length; i++) {
    const a = STIR[i][0];
    const b = STIR[i][1];
    let w = 1;
    if (u < a) {
      const k = seg(u, a - Math.min(0.15, a), a);
      w = k * k * (3 - 2 * k);
    } else if (u > b) {
      w = 1 - bezier(EASE.out, seg(u, b, b + Math.min(1.2, LOOP - 0.05 - b)));
    }
    e = Math.max(e, w);
  }
  return e;
};
/** Raw shear and filtered ω at loop time t. */
export const shearAt = (t: number) => {
  'worklet';
  const u = ((t % LOOP) + LOOP) % LOOP;
  const f = u / SHEAR_DT;
  const i = Math.floor(f);
  const a = i % SHEAR_N;
  const b = (i + 1) % SHEAR_N;
  const k = f - i;
  return { raw: mix(SHEAR_S[a], SHEAR_S[b], k) * stirEnvelope(u), w: mix(SHEAR_W[a], SHEAR_W[b], k) };
};

// ---------------------------------------------------------------------------------------------------------------
// the dot numeral
// ---------------------------------------------------------------------------------------------------------------

export const POOL = 48;
export const STRIDE = 16;
// slot fields
export const X = 0;
export const Y = 1;
export const VX = 2;
export const VY = 3;
export const SZ = 4;
export const VS = 5;
export const TX = 6;
export const TY = 7;
export const TS = 8;
export const IDX = 9;
export const NTX = 10;
export const NTY = 11;
export const NTS = 12;
export const NDEL = 13;
export const KX = 14;
export const KY = 15;
/** Index of the numeral the slots are heading for. */
export const TGT = POOL * STRIDE;

const NUM_N = RANGE.max - RANGE.min + 1;
export const NUM_CX = G.glow.cx;
export const NUM_CY = G.numeral.top + 3 * G.numeral.pitch + G.numeral.dot / 2;

/** Dot centres (canvas pt) of a whole number, one or two digits centred on the glow. */
const pointsOf = (n: number) => {
  const p = G.numeral.pitch;
  const r = G.numeral.dot / 2;
  const cellW = 4 * p + G.numeral.dot;
  const digits = String(n).split('').map(Number);
  const total = digits.length === 1 ? cellW : G.numeral.pairGap + cellW;
  const x0 = G.glow.cx - total / 2;
  const out: number[] = [];
  digits.forEach((d, i) => {
    // a 1 stands on its column: slide it against its neighbour so the pair keeps the spacing of any other pair
    const cx = x0 + i * G.numeral.pairGap + (d === 1 && digits.length > 1 ? (i === 0 ? 1 : -1) * p : 0);
    for (const sName of DIGITS[d]) {
      for (const [gx, gy] of SEGMENTS[sName]) out.push(cx + r + gx * p, G.numeral.top + r + gy * p);
    }
  });
  // Centre the lit glyph, including narrow 1s, rather than the invisible five-column cell boxes.
  let left = Infinity, right = -Infinity;
  for (let i = 0; i < out.length; i += 2) { left = Math.min(left, out[i]); right = Math.max(right, out[i]); }
  const optical = G.glow.cx - (left + right) / 2;
  for (let i = 0; i < out.length; i += 2) out[i] += optical;
  return out;
};
const NUM_PTS: number[][] = Array.from({ length: NUM_N }, (_, i) => pointsOf(RANGE.min + i));

/** Pairing of every glyph to every other: migration is capped at 22 pt (the 1.4x grid); other dots shrink or grow in place. */
const PAIR_CAP = 22;
const buildPairs = () => {
  const out: number[][] = [];
  for (let a = 0; a < NUM_N; a++) {
    for (let b = 0; b < NUM_N; b++) {
      const pa = NUM_PTS[a];
      const pb = NUM_PTS[b];
      const na = pa.length / 2;
      const nb = pb.length / 2;
      const cand: [number, number, number][] = [];
      for (let i = 0; i < na; i++) {
        for (let j = 0; j < nb; j++) {
          const d = Math.hypot(pa[2 * i] - pb[2 * j], pa[2 * i + 1] - pb[2 * j + 1]);
          if (d <= PAIR_CAP) cand.push([d, i, j]);
        }
      }
      cand.sort((p, q) => p[0] - q[0] || p[1] - q[1] || p[2] - q[2]);
      const map = new Array<number>(na).fill(-1);
      const used = new Array<boolean>(nb).fill(false);
      for (const [, i, j] of cand) {
        if (map[i] >= 0 || used[j]) continue;
        map[i] = j;
        used[j] = true;
      }
      out.push(map);
    }
  }
  return out;
};
const PAIRS = buildPairs();

/** Migration stagger per pitch of distance from the knob (s): 12 ms, 8 ms on a fast turn. */
export const staggerUnit = (hoursPerSec: number) => {
  'worklet';
  return Math.abs(hoursPerSec) > 2.5 ? 0.008 : 0.012;
};
/** The swirl given to a migrating dot, about the numeral's centre in the turn's direction (1/s). */
const KICK = 3;

/** A fresh state: `num` lit and settled, every other slot hidden at the centre. */
export const makeDots = (num: number) => {
  'worklet';
  const st = new Array<number>(TGT + 1).fill(0);
  const pts = NUM_PTS[num - RANGE.min];
  for (let j = 0; j < POOL; j++) {
    const o = j * STRIDE;
    const k = j < pts.length / 2 ? j : -1;
    const x = k >= 0 ? pts[2 * k] : NUM_CX;
    const y = k >= 0 ? pts[2 * k + 1] : NUM_CY;
    st[o + X] = x;
    st[o + Y] = y;
    st[o + TX] = x;
    st[o + TY] = y;
    st[o + SZ] = k >= 0 ? 1 : 0;
    st[o + TS] = k >= 0 ? 1 : 0;
    st[o + IDX] = k;
    st[o + NDEL] = -1;
  }
  st[TGT] = num;
  return st;
};

/**
 * Send the slots to a new number: each dot of the old glyph goes to its pair (pending for its stagger, with a swirl)
 * or shrinks where it is; points left over are filled by hidden slots, which grow from Ø 0 at the point. A slot still
 * visible is never teleported. `plain` (Reduce Motion) pairs nothing: old dots fade, new ones appear.
 */
export const retarget = (st: number[], to: number, dir: number, unit: number, plain: boolean) => {
  'worklet';
  const from = st[TGT];
  if (from === to) return;
  const map = PAIRS[(from - RANGE.min) * NUM_N + (to - RANGE.min)];
  const pts = NUM_PTS[to - RANGE.min];
  const n = pts.length / 2;
  const filled = new Array<boolean>(n).fill(false);
  let dmin = 1e9;
  for (let k = 0; k < n; k++) dmin = Math.min(dmin, Math.hypot(pts[2 * k] - G.knob.cx, pts[2 * k + 1] - G.knob.cy));
  for (let j = 0; j < POOL; j++) {
    const o = j * STRIDE;
    const idx = st[o + IDX];
    if (idx < 0) continue;
    const k = plain ? -1 : map[idx];
    if (k >= 0) {
      const px = pts[2 * k];
      const py = pts[2 * k + 1];
      filled[k] = true;
      st[o + IDX] = k;
      st[o + NTX] = px;
      st[o + NTY] = py;
      st[o + NTS] = 1;
      st[o + NDEL] = (unit * (Math.hypot(px - G.knob.cx, py - G.knob.cy) - dmin)) / G.numeral.pitch;
      // the swirl: perpendicular to the radius from the numeral's centre, in the knob's direction
      st[o + KX] = -dir * KICK * (st[o + Y] - NUM_CY);
      st[o + KY] = dir * KICK * (st[o + X] - NUM_CX);
    } else {
      st[o + IDX] = -1;
      st[o + NTX] = st[o + TX];
      st[o + NTY] = st[o + TY];
      st[o + NTS] = 0;
      st[o + NDEL] = 0;
      st[o + KX] = 0;
      st[o + KY] = 0;
    }
  }
  for (let k = 0; k < n; k++) {
    if (filled[k]) continue;
    // the free slot that shows least
    let best = -1;
    let bestS = 1e9;
    for (let j = 0; j < POOL; j++) {
      const o = j * STRIDE;
      if (st[o + IDX] >= 0) continue;
      const s = st[o + SZ];
      if (s < bestS) {
        bestS = s;
        best = j;
      }
    }
    if (best < 0) break;
    const o = best * STRIDE;
    const px = pts[2 * k];
    const py = pts[2 * k + 1];
    if (plain || st[o + SZ] < 0.05) {
      st[o + X] = px;
      st[o + Y] = py;
      st[o + VX] = 0;
      st[o + VY] = 0;
      if (plain) st[o + SZ] = 0;
    }
    st[o + IDX] = k;
    st[o + NTX] = px;
    st[o + NTY] = py;
    st[o + NTS] = 1;
    st[o + NDEL] = 0;
    st[o + KX] = 0;
    st[o + KY] = 0;
  }
  st[TGT] = to;
};

/** Apply a slot's pending target (after its stagger): new target and the swirl. */
const applyPending = (st: number[], o: number) => {
  'worklet';
  st[o + TX] = st[o + NTX];
  st[o + TY] = st[o + NTY];
  st[o + TS] = st[o + NTS];
  st[o + VX] += st[o + KX];
  st[o + VY] += st[o + KY];
  st[o + KX] = 0;
  st[o + KY] = 0;
  st[o + NDEL] = -1;
};

/**
 * One frame of the live dots: the `snap` spring stepped with semi-implicit Euler in two sub-steps. Under Reduce
 * Motion a dot does not travel: it fades in or out over 200 ms where it stands.
 */
export const stepDots = (st: number[], dt: number, plain: boolean) => {
  'worklet';
  const k = SPRING.snap.k;
  const d = SPRING.snap.d;
  for (let j = 0; j < POOL; j++) {
    const o = j * STRIDE;
    if (st[o + NDEL] >= 0) {
      st[o + NDEL] -= dt;
      if (st[o + NDEL] < 0) applyPending(st, o);
    }
    if (plain) {
      st[o + X] = st[o + TX];
      st[o + Y] = st[o + TY];
      const ds = st[o + TS] - st[o + SZ];
      st[o + SZ] += Math.sign(ds) * Math.min(Math.abs(ds), dt * 5);
      continue;
    }
    const h = dt / 2;
    for (let i = 0; i < 2; i++) {
      st[o + VX] += (-k * (st[o + X] - st[o + TX]) - d * st[o + VX]) * h;
      st[o + VY] += (-k * (st[o + Y] - st[o + TY]) - d * st[o + VY]) * h;
      st[o + VS] += (-k * (st[o + SZ] - st[o + TS]) - d * st[o + VS]) * h;
      st[o + X] += st[o + VX] * h;
      st[o + Y] += st[o + VY] * h;
      st[o + SZ] += st[o + VS] * h;
    }
  }
};

// The autoplay's migrations, in closed form: the hour crossings of the loop are found once, and each slot's spring
// segments (start time, state, target) are recorded, so any loop time is evaluated without stepping.
const REC = 10; // t0, x, y, vx, vy, s, vs, tx, ty, ts
const buildDots = () => {
  const events: { t: number; num: number; dir: number; unit: number }[] = [];
  let prev = numOf(vOfTh(thMainAt(0)));
  const dt = 0.001;
  for (let i = 1; i < LOOP / dt; i++) {
    const t = i * dt;
    const n = numOf(vOfTh(thMainAt(t)));
    if (n !== prev) {
      const rate = (vOfTh(thMainAt(t + dt)) - vOfTh(thMainAt(t - dt))) / (2 * dt);
      events.push({ t, num: n, dir: n > prev ? 1 : -1, unit: staggerUnit(rate) });
      prev = n;
    }
  }
  const st = makeDots(RANGE.rest);
  // segment records, event-major: rec[e][slot]; e = 0 is the rest state at the start of the loop
  const recs: number[] = [];
  const idxs: number[] = [];
  const pushRec = (t0: number, j: number, s: number[]) => {
    const o = j * STRIDE;
    recs.push(t0, s[o + X], s[o + Y], s[o + VX], s[o + VY], s[o + SZ], s[o + VS], s[o + TX], s[o + TY], s[o + TS]);
  };
  for (let j = 0; j < POOL; j++) pushRec(-1, j, st);
  for (let j = 0; j < POOL; j++) idxs.push(st[j * STRIDE + IDX]);
  const tmp = new Array<number>(2);
  const evalRec = (base: number, t: number, out: number[], o: number) => {
    const t0 = recs[base];
    const u = t - t0;
    snapPV(u, recs[base + 1], recs[base + 3], recs[base + 7], tmp, 0);
    out[o + X] = tmp[0];
    out[o + VX] = tmp[1];
    snapPV(u, recs[base + 2], recs[base + 4], recs[base + 8], tmp, 0);
    out[o + Y] = tmp[0];
    out[o + VY] = tmp[1];
    snapPV(u, recs[base + 5], recs[base + 6], recs[base + 9], tmp, 0);
    out[o + SZ] = tmp[0];
    out[o + VS] = tmp[1];
    out[o + TX] = recs[base + 7];
    out[o + TY] = recs[base + 8];
    out[o + TS] = recs[base + 9];
  };
  events.forEach((ev, e) => {
    const prevBase = e * POOL * REC;
    // every slot's state at the crossing
    for (let j = 0; j < POOL; j++) evalRec(prevBase + j * REC, ev.t, st, j * STRIDE);
    for (let j = 0; j < POOL; j++) st[j * STRIDE + NDEL] = -1;
    retarget(st, ev.num, ev.dir, ev.unit, false);
    for (let j = 0; j < POOL; j++) {
      const o = j * STRIDE;
      if (st[o + NDEL] < 0) {
        // untouched: the old segment carries on
        for (let q = 0; q < REC; q++) recs.push(recs[prevBase + j * REC + q]);
      } else {
        const t0 = ev.t + st[o + NDEL];
        const teleported = st[o + VX] === 0 && st[o + VY] === 0 && st[o + SZ] < 0.05;
        if (!teleported) evalRec(prevBase + j * REC, t0, st, o);
        applyPending(st, o);
        pushRec(t0, j, st);
      }
      idxs.push(st[o + IDX]);
    }
  });
  return { events, recs, idxs, tgts: [RANGE.rest, ...events.map((e) => e.num)] };
};
const DOTS = buildDots();
const EV_T = DOTS.events.map((e) => e.t);
const EV_N = EV_T.length;
const RECS = DOTS.recs;
const IDXS = DOTS.idxs;
const TGTS = DOTS.tgts;

/** The autoplay's dots at loop time t, written into `out` as the live state (positions, velocities, targets). */
export const dotsAt = (t: number, out: number[]) => {
  'worklet';
  let e = 0;
  for (let i = EV_N - 1; i >= 0; i--) {
    if (EV_T[i] <= t) {
      e = i + 1;
      break;
    }
  }
  const tmp = [0, 0];
  for (let j = 0; j < POOL; j++) {
    const o = j * STRIDE;
    // the newest record of this slot that has started (a stagger can leave it on the previous one)
    let r = e;
    while (r > 0 && RECS[(r * POOL + j) * REC] > t) r--;
    const b = (r * POOL + j) * REC;
    const u = t - RECS[b];
    snapPV(u, RECS[b + 1], RECS[b + 3], RECS[b + 7], tmp, 0);
    out[o + X] = tmp[0];
    out[o + VX] = tmp[1];
    snapPV(u, RECS[b + 2], RECS[b + 4], RECS[b + 8], tmp, 0);
    out[o + Y] = tmp[0];
    out[o + VY] = tmp[1];
    snapPV(u, RECS[b + 5], RECS[b + 6], RECS[b + 9], tmp, 0);
    out[o + SZ] = tmp[0];
    out[o + VS] = tmp[1];
    // targets of the crossing in force (a pending stagger is applied at once on hand-off)
    const be = (e * POOL + j) * REC;
    out[o + TX] = RECS[be + 7];
    out[o + TY] = RECS[be + 8];
    out[o + TS] = RECS[be + 9];
    out[o + IDX] = IDXS[e * POOL + j];
    out[o + NDEL] = -1;
    out[o + KX] = 0;
    out[o + KY] = 0;
  }
  out[TGT] = TGTS[e];
};

/** Time since the latest hour crossing of the loop (for the marker's pulse); large when none. */
const lastCrossing = (t: number) => {
  'worklet';
  for (let i = EV_N - 1; i >= 0; i--) if (EV_T[i] <= t) return t - EV_T[i];
  return 99;
};
/** The marker's pulse after a whole hour: a quick bump up to 1 and back (peak at 60 ms). */
export const pulseOf = (age: number) => {
  'worklet';
  if (age <= 0 || age > 0.6) return 0;
  const k = age / 0.06;
  return k * Math.exp(1 - k);
};

// ---------------------------------------------------------------------------------------------------------------
// the vein: the knob pumps the pool's light along it; the drain and the bloom run through it
// ---------------------------------------------------------------------------------------------------------------

/** Quadratic Bézier point. */
export const quad = (ax: number, ay: number, bx: number, by: number, cx: number, cy: number, u: number) => {
  'worklet';
  const m = 1 - u;
  return { x: m * m * ax + 2 * m * u * bx + u * u * cx, y: m * m * ay + 2 * m * u * by + u * u * cy };
};
const VEIN_N = 96;
const buildVein = () => {
  const arc = new Array<number>(VEIN_N + 1).fill(0);
  const px = new Array<number>(VEIN_N + 1).fill(0);
  const py = new Array<number>(VEIN_N + 1).fill(0);
  const V = G.vein;
  for (let i = 0; i <= VEIN_N; i++) {
    const p = quad(V.x0, V.y0, V.cx, V.cy, V.x1, V.y1, i / VEIN_N);
    px[i] = p.x;
    py[i] = p.y;
    if (i > 0) arc[i] = arc[i - 1] + Math.hypot(p.x - px[i - 1], p.y - py[i - 1]);
  }
  return { arc, px, py };
};
const VEIN = buildVein();
const VEIN_ARC = VEIN.arc;
const VEIN_X = VEIN.px;
const VEIN_Y = VEIN.py;
/** Length of the vein (pt) and the time a bead takes along it (s). */
export const VEIN_LEN = VEIN_ARC[VEIN_N];
export const BEAD_TRAVEL = VEIN_LEN / G.vein.beadSpeed;
/** The point of the vein s pt from the knob. */
const veinPointAt = (s: number, out: number[], o: number) => {
  'worklet';
  const c = Math.min(Math.max(s, 0), VEIN_LEN);
  let lo = 0;
  let hi = VEIN_N;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (VEIN_ARC[mid] <= c) lo = mid;
    else hi = mid;
  }
  const k = (c - VEIN_ARC[lo]) / Math.max(VEIN_ARC[hi] - VEIN_ARC[lo], 1e-6);
  out[o] = VEIN_X[lo] + (VEIN_X[hi] - VEIN_X[lo]) * k;
  out[o + 1] = VEIN_Y[lo] + (VEIN_Y[hi] - VEIN_Y[lo]) * k;
};
export const BEADS = 6;
/** Radius bump of the pool when a bead arrives (fraction) and how long it takes to die away (s). */
const GULP = 0.03;
const GULP_TIME = 0.18;
/**
 * The beads in flight at time t, from a list of detent events [t0, dir, t0, dir, …] (dir +1 turning up: knob to pool, −1
 * turning down: pool to knob). Six slots of [x, y, alpha], and the pool's gulp from the beads that just arrived.
 */
export const beadsOf = (list: number[], n: number, t: number) => {
  'worklet';
  const out = new Array<number>(BEADS * 3).fill(0);
  const p = [0, 0];
  let c = 0;
  let gulp = 0;
  for (let i = n - 1; i >= 0; i--) {
    const age = t - list[2 * i];
    if (age < 0) continue;
    const dir = list[2 * i + 1];
    if (dir > 0 && age >= BEAD_TRAVEL && age - BEAD_TRAVEL < GULP_TIME) {
      const k = 1 - (age - BEAD_TRAVEL) / GULP_TIME;
      gulp += GULP * k * k;
    }
    if (age >= BEAD_TRAVEL || c >= BEADS) continue;
    const s = age * G.vein.beadSpeed;
    veinPointAt(dir > 0 ? s : VEIN_LEN - s, p, 0);
    out[3 * c] = p[0];
    out[3 * c + 1] = p[1];
    out[3 * c + 2] = Math.min(1, s / 28, (VEIN_LEN - s) / 28);
    c++;
  }
  return { beads: out, gulp };
};
/**
 * The pour, for its progress P (0 = all in the pool, 1 = all in the knob; time-linear over the 1.2 s pour). Three beats
 * in one stream: the pigment's front runs down the vein to the knob (P 0–0.21), the pool empties into it at a steady
 * flow, its radius following the area it has left (P 0–0.71, so it collapses at the end), and then the last of the light
 * in the vein is drawn into the knob (P 0.71–1). The stream stays joined to the pool until the pool is gone: no blob ever
 * travels on its own. `head`/`tail` are the stream's ends along u (0 knob .. 1 pool); `vol` is the pool's share drained.
 */
const POUR_HEAD = 0.21;
const POUR_POOL = 0.71;
const VOL_EASE = [0.3, 0.3, 0.55, 1] as const;
export const pourOf = (P: number) => {
  'worklet';
  const p = clamp01(P);
  const head = 1 - bezier(EASE.out, p / POUR_HEAD);
  const vol = bezier(VOL_EASE, p / POUR_POOL);
  const tail = 1 - bezier(EASE.in, (p - POUR_POOL) / (1 - POUR_POOL));
  return { head, tail, vol };
};
/** The pool's radius factor while it drains: the square root of what is left. */
export const poolOfP = (P: number) => {
  'worklet';
  return Math.sqrt(Math.max(0, 1 - pourOf(P).vol));
};
/** The cap's ember, the halo and the stain follow the pour: from when the front reaches the knob to when the last is in. */
export const emberOf = (P: number) => {
  'worklet';
  const k = clamp01((P - 0.17) / 0.8);
  return k * k * (3 - 2 * k);
};

// ---------------------------------------------------------------------------------------------------------------
// detent events of the loop: beads, the gear train, the pump
// ---------------------------------------------------------------------------------------------------------------

const detentOf = (th: number) => {
  'worklet';
  return Math.round(vOfTh(th) * 4);
};
/** Hour index of the bedtime (06:00 − v) for a detent index: floor(6 − k / 4). */
export const hourIdxOf = (k: number) => {
  'worklet';
  return Math.floor(6 - k / 4);
};
const buildDetents = () => {
  const beads: number[] = [];
  const kEv: number[] = []; // t, ±1
  const hEv: number[] = [];
  let k = detentOf(thMainAt(0));
  const K0 = k;
  const dt = 0.0005;
  for (let i = 1; i < LOOP / dt; i++) {
    const t = i * dt;
    const n = detentOf(thMainAt(t));
    while (n !== k) {
      const dir = n > k ? 1 : -1;
      const next = k + dir;
      beads.push(t, dir);
      kEv.push(t, dir);
      const dh = hourIdxOf(next) - hourIdxOf(k);
      if (dh !== 0) hEv.push(t, dh);
      k = next;
    }
  }
  return { beads, kEv, hEv, K0 };
};
const DET = buildDetents();
export const BEAD_EVENTS = DET.beads;
const BEAD_N = DET.beads.length / 2;
const K_EV = DET.kEv;
const H_EV = DET.hEv;
const K_N = K_EV.length / 2;
const H_N = H_EV.length / 2;
const K0 = DET.K0;
const H0 = hourIdxOf(DET.K0);

/** The vein's pump alpha: 0.5 · min(1, |ω| / 2.5 rad/s), low-passed over 300 ms (a periodic table). */
const PUMP_TAU = 0.3;
const buildPump = () => {
  const out = new Array<number>(SHEAR_N).fill(0);
  const a = 1 - Math.exp(-SHEAR_DT / PUMP_TAU);
  let y = 0;
  for (let pass = 0; pass < 2; pass++) {
    for (let i = 0; i < SHEAR_N; i++) {
      const t = i * SHEAR_DT;
      const w = Math.abs(((thMainAt(t + SHEAR_DT * 0.5) - thMainAt(t - SHEAR_DT * 0.5)) / SHEAR_DT) * Math.PI) / 180;
      y += (0.5 * Math.min(1, w / 2.5) - y) * a;
      if (pass === 1) out[i] = y;
    }
  }
  return out;
};
const PUMP = buildPump();
export const pumpAt = (t: number) => {
  'worklet';
  const f = (((t % LOOP) + LOOP) % LOOP) / SHEAR_DT;
  const i = Math.floor(f);
  return mix(PUMP[i % SHEAR_N], PUMP[(i + 1) % SHEAR_N], f - i);
};

// ---------------------------------------------------------------------------------------------------------------
// the gear train (bedtime = 06:00 − v): odometer labels on `snap`, hands on `detent`
// ---------------------------------------------------------------------------------------------------------------

/** Gear state: [hour roll, velocity, minute roll, velocity, hand, velocity]. */
export const GEAR_LEN = 6;
/** The gear train at loop time t: every detent of the loop is a step answered by a closed-form spring; positions and velocities. */
export const gearAt = (t: number, out: number[]) => {
  'worklet';
  const unit = [0, 0];
  let h = H0;
  let hv = 0;
  let k = K0;
  let kv = 0;
  let a = K0;
  let av = 0;
  for (let i = 0; i < H_N; i++) {
    const u = t - H_EV[2 * i];
    if (u <= 0) continue;
    springPV(u, 0, 0, 1, SPRING.snap.d, SPRING.snap.k, unit, 0);
    h += H_EV[2 * i + 1] * unit[0];
    hv += H_EV[2 * i + 1] * unit[1];
  }
  for (let i = 0; i < K_N; i++) {
    const u = t - K_EV[2 * i];
    if (u <= 0) continue;
    springPV(u, 0, 0, 1, SPRING.snap.d, SPRING.snap.k, unit, 0);
    k += K_EV[2 * i + 1] * unit[0];
    kv += K_EV[2 * i + 1] * unit[1];
    springPV(u, 0, 0, 1, SPRING.detent.d, SPRING.detent.k, unit, 0);
    a += K_EV[2 * i + 1] * unit[0];
    av += K_EV[2 * i + 1] * unit[1];
  }
  out[0] = h;
  out[1] = hv;
  out[2] = k;
  out[3] = kv;
  out[4] = a;
  out[5] = av;
};
/** The live gear train: targets from the detent the knob is at, the same springs integrated in two sub-steps. */
export const stepGear = (g: number[], dt: number, v: number) => {
  'worklet';
  const kT = Math.round(Math.min(Math.max(v, RANGE.min), RANGE.max) * 4);
  const hT = hourIdxOf(kT);
  const h = dt / 2;
  for (let i = 0; i < 2; i++) {
    g[1] += (-SPRING.snap.k * (g[0] - hT) - SPRING.snap.d * g[1]) * h;
    g[3] += (-SPRING.snap.k * (g[2] - kT) - SPRING.snap.d * g[3]) * h;
    g[5] += (-SPRING.detent.k * (g[4] - kT) - SPRING.detent.d * g[5]) * h;
    g[0] += g[1] * h;
    g[2] += g[3] * h;
    g[4] += g[5] * h;
  }
};
/** The label of a bedtime hour index and of a detent index (the odometer columns). */
export const HOUR_LABELS = Array.from({ length: 36 }, (_, j) => String(j % 12 || 12));
export const MINUTE_LABELS = Array.from({ length: 49 }, (_, k) => String((60 - (k % 4) * 15) % 60).padStart(2, '0'));
/** Odometer column offsets: hour index j = idx + 24 (idx −6..2 for 4–12 h), minute index = detent index. */
export const HOUR_COL_BASE = 24;

// ---------------------------------------------------------------------------------------------------------------
// the frame
// ---------------------------------------------------------------------------------------------------------------

/** Everything the screen draws (but the dots), for one moment. */
export type Frame = {
  th: number; // knob angle incl. anticipation (deg); the ruler follows it 1 : 1
  g: number; // glow radius scale from the value (lagged), before drain and bloom
  breath: number; // 1 ± 0.03
  gulp: number; // the pool's bump when a bead arrives
  shear: number; // rad (soft-clamped)
  shearOuter: number; // the same, 40 ms later (outer dots of the numeral)
  ringU: number; // brightness ring front (fraction of the pool), ringA its strength
  ringA: number;
  d: number; // the pool's absence 0..1 (hours label ink)
  pool: number; // the pool's radius factor (drain: 1 − d; bloom: a `pop` spring)
  plug: number; // progress of the pour through the vein 0..1 (0 = in the pool, 1 = in the knob)
  vein: number; // the pump's alpha (0 at rest)
  beads: number[]; // 6 × [x, y, alpha]
  ember: number; // the knob holds the light 0..1 (breathing included in `emberB`)
  emberB: number;
  lit: number; // knob ticks that have taken the light (0..36)
  stain: number; // alpha of the stain where the pool was
  hRoll: number; // gear train
  kRoll: number;
  kHand: number;
  pieCold: number;
  f: number; // funnel into the CTA 0..1
  bloomR: number;
  amt: number; // glow colour amount 0..1
  thumb: number; // 0 on .. 1 off
  thumbPress: number;
  off: number; // dots outline, 0 filled .. 1 rings
  offMarker: number;
  offTicks: number;
  halo: number; // 0 rest .. 1 holding the light
  cap: number; // knob press 0..1
  cta: number; // CTA fill / check 0..1
  ctaPress: number;
  pulse: number; // marker pulse 0..1
  fx: number; // a finger on the pool (live only): position, press 0..1, the dent and the ring from the finger
  fy: number;
  fa: number;
  ringFx: number;
  ringFy: number;
  ringFr: number;
  ringFa: number;
};

/**
 * The pour's progress through the vein: drain 4.5–5.7 s, bloom 6.05–7.25 s (the exact reverse; once the stream has
 * reached the pool it opens on a `pop` spring, 6 % over).
 */
const DA = T.drainA + T.drainLag;
const BA = T.bloomA + T.bloomLag;
export const plugAt = (t: number) => {
  'worklet';
  if (t < DA) return 0;
  if (t < BA) return seg(t, DA, DA + T.drainDur);
  return 1 - seg(t, BA, BA + T.drainDur);
};
/** When the stream has run back up into the pool's centre during the bloom. */
const BLOOM_POOL = BA + (1 - POUR_POOL) * T.drainDur;
export const poolAt = (t: number) => {
  'worklet';
  if (t < DA) return 1;
  if (t < BA) return poolOfP(plugAt(t));
  return springAt(t - BLOOM_POOL, 0, 1, 0, SPRING.pop.d, SPRING.pop.k);
};
/** The last light has entered the knob (the Medium haptic of a live toggle lands here). */
export const ENTER_T = DA + 0.99 * T.drainDur;
/** The knob's 36 ticks take the light clockwise, 12 ms apart, while the last of the light runs in. */
const TICK_STEP = 0.012;
const TICK_T0 = DA + POUR_POOL * T.drainDur;

export const frameAt = (tIn: number): Frame => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const th = thKnobAt(t);
  const beadsNow = beadsOf(BEAD_EVENTS, BEAD_N, t);
  const g = gOf(vOfTh(thMainAt(t - GLOW_LAG)));
  const breath = 1 + 0.03 * Math.sin((2 * Math.PI * t) / LOOP);
  const shear = shearOut(shearAt(t).raw);
  const shearOuter = shearOut(shearAt(t - OUTER_LAG).raw);
  // the brightness ring after each turn: up at 2.0 s, home at 9.0 s
  const ringStart = t < 5 ? T.ringA : T.ring2A;
  const ringEnd = t < 5 ? T.ringB : T.ring2B;
  const ringOn = t > ringStart && t < ringEnd;
  const ringU = ringOn ? springAt(t - ringStart, 0, 1.15, 0, SPRING.drift.d, SPRING.drift.k) : 0;
  const ringA = ringOn ? Math.sin(Math.PI * seg(t, ringStart, ringEnd)) : 0;

  // the pour: the pool runs down the vein into the knob, and back
  const plug = plugAt(t);
  const pool = poolAt(t);
  const gone = (u: number) => 1 - clamp01(poolAt(u));
  const d = gone(t);
  const ember = emberOf(plug);
  const emberB = ember * (1 + 0.06 * Math.sin(2 * Math.PI * t));
  const lit = t < BA ? 36 * clamp01((t - TICK_T0) / (TICK_STEP * 36)) : 36 * (1 - clamp01((t - BA) / 0.3));
  let stain = 0;
  if (t >= DA && t < BA) {
    const vol = pourOf(plug).vol;
    stain = 0.22 * clamp01((vol - 0.6) / 0.4) * (1 - 0.55 * seg(t, DA + T.drainDur, BA));
  } else if (t >= BA) {
    const k = clamp01(pool / 0.8);
    stain = 0.1 * (1 - k * k * (3 - 2 * k));
  }
  const gear = [0, 0, 0, 0, 0, 0];
  gearAt(t, gear);
  const pieCold = gone(t - 0.2);

  // the toggle
  const thumb = t < T.drainA ? 0 : t < T.bloomA ? springAt(t - T.drainA, 0, 1, 0, SPRING.snap.d, SPRING.snap.k) : springAt(t - T.bloomA, 1, 0, 0, SPRING.snap.d, SPRING.snap.k);
  const pressIn = (a: number) => bezier(EASE.in, seg(t, a, a + 0.15));
  const pressOut = (a: number) => 1 - bezier(EASE.out, seg(t, a, a + 0.3));
  let thumbPress = 0;
  if (t >= T.thumbPress && t < T.drainA + 0.5) thumbPress = Math.min(pressIn(T.thumbPress), pressOut(T.drainA + 0.15));
  else if (t >= T.bloomA - 0.15 && t < T.bloomA + 0.6) thumbPress = Math.min(pressIn(T.bloomA - 0.15), pressOut(T.bloomA + 0.3));

  // the cold state of the dots, the marker and the ticks (stagger 20 ms from the top), back with the bloom
  const coldAt = (a: number) => {
    if (t < T.offA + a) return 0;
    if (t < T.dotsBackA + a) return bezier(EASE.out, seg(t, T.offA + a, T.offA + a + 0.4));
    return clamp01(1 - springAt(t - T.dotsBackA - a, 0, 1, 0, SPRING.snap.d, SPRING.snap.k));
  };
  const off = coldAt(0);
  const offMarker = coldAt(0.02);
  const offTicks = coldAt(0.04);

  // the finger on the knob: pressed through each turn
  let cap = 0;
  const holds: [number, number][] = [
    [T.anticA, T.turnB],
    [T.backA - 0.12, T.backB],
    [T.returnA - 0.12, T.returnB],
  ];
  for (const [a, b] of holds) {
    if (t >= a && t < b) cap = bezier(EASE.in, seg(t, a, a + 0.12));
    else if (t >= b && t < b + 0.5) cap = clamp01(springAt(t - b, 1, 0, 0, SPRING.snap.d, SPRING.snap.k));
  }

  return {
    th,
    g,
    breath,
    gulp: beadsNow.gulp,
    shear,
    shearOuter,
    ringU,
    ringA,
    d,
    pool,
    plug,
    vein: pumpAt(t),
    beads: beadsNow.beads,
    ember,
    emberB,
    lit,
    stain,
    hRoll: gear[0],
    kRoll: gear[2],
    kHand: gear[4],
    pieCold,
    f: 0,
    bloomR: 1,
    amt: 1,
    thumb,
    thumbPress,
    off,
    offMarker,
    offTicks,
    halo: ember,
    cap,
    cta: 0,
    ctaPress: 0,
    pulse: pulseOf(lastCrossing(t)),
    fx: 0,
    fy: 0,
    fa: 0,
    ringFx: 0,
    ringFy: 0,
    ringFr: 0,
    ringFa: 0,
  };
};
