// Trail Parallax Tracker: the autoplay timeline (spec §4 with the Pro addendum) as a pure function of the loop time,
// so every frame is reproducible and the three stacks can match it frame for frame. Times in seconds, distance in
// miles walked (`d`, 0–150), velocity in miles per second.

import { G, MI, SPRING, WAYPOINTS } from './tpt-theme';

export const LOOP = 12;
/** The poster: the tree crossing, trunk's trailing edge at x ≈ 150, grove and shafts on the right. */
export const POSTER = 4.45;

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
  const k = Math.min(Math.max((x - a) / (b - a), 0), 1);
  return k * k * (3 - 2 * k);
};
export const easeOut = (k: number) => {
  'worklet';
  return 1 - (1 - k) * (1 - k) * (1 - k);
};
export const easeIn = (k: number) => {
  'worklet';
  return k * k * k;
};
export const easeInOut = (k: number) => {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
};

/** A damped spring (mass 1) released at time 0 from `from` towards `to` with velocity v0: position at time t. */
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

// ---- distance: piecewise-linear velocity from 0 to 8.3 s, then a trapezoid rewind, then the seam rest ----

/**
 * Velocity keys (t, v). Walk 1 crosses the canyon/granite border at ≈ 1.9 s, strolls under the spires (never stops:
 * ≥ 1.2 mi/s), walks through the tree at 24.4 mi/s (d 49.7–67.7, ≥ 0.7 s; poster at 4.45 s, d ≈ 64), brakes into the
 * grove, flings to 148.5 at 7.4 s and strolls to 150 while the light turns to blue hour.
 */
const VK: [number, number][] = [
  [0, 0],
  [0.06, -3.33],
  [0.12, 0],
  [1.2, 21],
  [1.95, 22],
  [2.55, 3],
  [2.75, 1.3],
  [3.0, 1.3],
  [3.6, 24.4],
  [4.75, 24.4],
  [5.2, 1.4],
  [5.7, 1.2],
  [5.95, 108.4],
  [6.5, 40],
  [7.0, 14],
  [7.3, 3],
  [7.4, 1.6],
  [8.0, 2.3],
  [8.3, 0],
];
const VK_T = VK.map((k) => k[0]);
const VK_V = VK.map((k) => k[1]);
const VK_D: number[] = [0];
for (let i = 1; i < VK.length; i++) VK_D.push(VK_D[i - 1] + ((VK_V[i - 1] + VK_V[i]) / 2) * (VK_T[i] - VK_T[i - 1]));
/** Miles at the end of the forward run (≈ 150). */
export const D_END = VK_D[VK_D.length - 1];
const FWD_END = 8.3;
const REW_A = 8.3;
const REW_B = 11.4;
const REW_RAMP = 0.514;
/** Rewind cruise speed, so that the trapezoid lands exactly on 0. */
const REW_V = D_END / (REW_B - REW_A - REW_RAMP);

const fwd = (t: number) => {
  'worklet';
  let i = 0;
  while (i < VK_T.length - 2 && t > VK_T[i + 1]) i++;
  const t0 = VK_T[i];
  const dt = Math.min(Math.max(t - t0, 0), VK_T[i + 1] - t0);
  const a = (VK_V[i + 1] - VK_V[i]) / (VK_T[i + 1] - t0);
  return { d: VK_D[i] + VK_V[i] * dt + 0.5 * a * dt * dt, v: VK_V[i] + a * dt };
};

/** Smoothstep-ramp trapezoid: distance covered after time s of a ramp of length r at full speed 1. */
const rampDist = (s: number, r: number) => {
  'worklet';
  const k = Math.min(Math.max(s / r, 0), 1);
  // ∫ smoothstep = r (k³ − k⁴/2)
  return r * (k * k * k - (k * k * k * k) / 2);
};

export const dAt = (t: number): { d: number; v: number } => {
  'worklet';
  if (t <= FWD_END) return fwd(t);
  if (t >= REW_B) return { d: 0, v: 0 };
  const s = t - REW_A;
  const T = REW_B - REW_A;
  let dist = 0;
  let sp = 0;
  if (s < REW_RAMP) {
    dist = rampDist(s, REW_RAMP);
    sp = smooth(0, REW_RAMP, s);
  } else if (s < T - REW_RAMP) {
    dist = REW_RAMP / 2 + (s - REW_RAMP);
    sp = 1;
  } else {
    const u = T - s;
    dist = T - REW_RAMP - rampDist(u, REW_RAMP);
    sp = smooth(0, REW_RAMP, u);
  }
  return { d: D_END - REW_V * dist, v: -REW_V * sp };
};

// ---- the walk cycle ----

/** Running: above 30 mi/s he breaks into a run, fully by 45 (walks through the tree at 24.4). */
export const runOf = (v: number) => {
  'worklet';
  return smooth(30, 45, Math.abs(v));
};
/** Pt of hiker travel per walk cycle (8 frames, 2 steps) at the drawn size. */
export const STRIDE = 52 * (G.hikerH / 48);
/** Cycles per second: tied to distance (feet planted) up to a brisk walk, then capped so the legs never strobe. */
export const cadenceOf = (v: number, run: number) => {
  'worklet';
  const walk = Math.min((Math.abs(v) * MI) / STRIDE, 2.4);
  return mix(walk, 3.1, run);
};

const PH_HZ = 100;
const PHASE: number[] = (() => {
  const out: number[] = [0];
  let p = 0;
  const n = LOOP * PH_HZ;
  for (let i = 1; i <= n; i++) {
    const t = (i - 0.5) / PH_HZ;
    const { v } = dAt(t);
    p += cadenceOf(v, runOf(v)) / PH_HZ;
    out.push(p);
  }
  return out;
})();
export const phaseAt = (t: number) => {
  'worklet';
  const x = Math.min(Math.max(t, 0), LOOP) * PH_HZ;
  const i = Math.min(Math.floor(x), PHASE.length - 2);
  return mix(PHASE[i], PHASE[i + 1], x - i);
};

/** Sprite cell: walk 0–7, run 8–13, idle 14–15, wave 16–18. */
export const poseOf = (phase: number, v: number, run: number, wave: number) => {
  'worklet';
  if (wave >= 0) return wave;
  if (Math.abs(v) < 0.3) return 14;
  const f = phase - Math.floor(phase);
  if (run > 0.5) return 8 + (Math.floor(f * 6) % 6);
  return Math.floor(f * 8) % 8;
};

// ---- events along the loop: waypoint crossings and brake skids ----

const WP_AT = WAYPOINTS.map((w) => w.at);
export const waypointIdx = (d: number) => {
  'worklet';
  let i = 0;
  for (let k = 1; k < WP_AT.length; k++) if (d >= WP_AT[k]) i = k;
  return i;
};

type Ev = { t: number; from: number; to: number; dir: number };
const CARD_EV: Ev[] = [];
const SKID_EV: number[] = [];
(() => {
  const step = 0.002;
  let prevW = waypointIdx(0);
  let prevV = 0;
  for (let t = step; t <= LOOP; t += step) {
    const { d, v } = dAt(t);
    const w = waypointIdx(d);
    if (w !== prevW) {
      CARD_EV.push({ t, from: prevW, to: w, dir: w > prevW ? 1 : -1 });
      prevW = w;
    }
    if (Math.abs(prevV) >= 14 && Math.abs(v) < 14) SKID_EV.push(t);
    prevV = v;
  }
})();
const CE_T = CARD_EV.map((e) => e.t);
const CE_FROM = CARD_EV.map((e) => e.from);
const CE_TO = CARD_EV.map((e) => e.to);
const CE_DIR = CARD_EV.map((e) => e.dir);

/** Place card swap: the masked slide's progress (snap spring) from its age. */
export const cardP = (age: number) => {
  'worklet';
  // the snap spring creeps the last 2 %: call it landed there, so no sliver of the old text lingers
  return clamp01(springAt(age, 0, 1, 0, SPRING.snap.d, SPRING.snap.k) / 0.98);
};

/** Day shown for d: 310 − ⌊d / 2.66⌋, rolled over the last fifth of each day so the digits turn, never jump. */
export const dayValue = (d: number) => {
  'worklet';
  const q = Math.max(d, 0) / 2.66;
  const f = q - Math.floor(q);
  return 310 - (Math.floor(q) + smooth(0.8, 1, f));
};

/** Everything the screen draws, for one moment. */
export type Frame = {
  d: number;
  v: number;
  run: number;
  phase: number;
  pose: number;
  face: number; // 1 right, −1 left (the hop-turn flips it at the apex)
  hop: number; // 0..1 hop-turn arc
  lean: number; // degrees, forward positive
  squash: number; // scaleY multiplier (crouch, brace, hop, breath)
  bubS: number; // bubble scale 0..1
  bubT: number; // 0 over his head … 1 inside the pack
  skidAge: number; // seconds since a brake skid began, −1 none
  cardFrom: number;
  cardTo: number;
  cardDir: number;
  cardAge: number;
};

const hopK = (t: number, a: number) => {
  'worklet';
  const k = seg(t, a, a + 0.16);
  return k > 0 && k < 1 ? Math.sin(Math.PI * k) : 0;
};

/** Lean forward with speed (the run cells already lean 10°), back on a skid. */
export const leanOf = (v: number, run: number, skidAge: number) => {
  'worklet';
  const walk = Math.min(Math.abs(v) * 0.3, 8);
  const lean = mix(walk, 4, run);
  const skid = skidAge >= 0 && skidAge < 0.4 ? Math.sin(Math.PI * Math.min(skidAge / 0.4, 1)) : 0;
  return lean * (1 - skid) - 10 * skid;
};

export const frameAt = (tIn: number): Frame => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const { d, v } = dAt(t);
  const run = runOf(v);
  const phase = phaseAt(t);
  // facing: right, turns at 8.30 (hop 8.22–8.38) to run back, turns again at 11.48 (hop 11.40–11.56)
  const face = t >= 8.3 && t < 11.48 ? -1 : 1;
  const hop = Math.max(hopK(t, 8.22), hopK(t, 11.4));
  // skid: the last brake that began before t (wrapping)
  let skidAge = -1;
  for (let i = 0; i < SKID_EV.length; i++) if (SKID_EV[i] <= t) skidAge = t - SKID_EV[i];
  // anticipation: the land shifts the wrong way and he leans back 4°
  const antic = t < 0.12 ? Math.sin(Math.PI * seg(t, 0, 0.12)) : 0;
  const lean = leanOf(v, run, skidAge) - 4 * antic;
  // crouch before the fling, the hop-turn's squash, idle breath (period 2 s)
  const crouch = t >= 5.6 && t < 5.7 ? easeIn(seg(t, 5.6, 5.7)) : t >= 5.7 ? Math.max(0, springAt(t - 5.7, 1, 0, 0, SPRING.pop.d, SPRING.pop.k)) : 0;
  const breath = 0.015 * Math.sin((2 * Math.PI * 6 * t) / LOOP);
  const squash = 1 - 0.08 * crouch - 0.1 * hop + breath;
  // the "Today" bubble: shrinks along its tether into the pack as he sets off, pops back out of it at the end
  let bubS = 1;
  let bubT = 0;
  if (t >= 0.2 && t < 11.55) {
    const k = seg(t, 0.2, 0.48);
    bubS = 1 - easeIn(k);
    bubT = easeInOut(k);
  } else if (t >= 11.55) {
    bubS = mix(Math.max(0, springAt(t - 11.55, 0, 1, 0, SPRING.pop.d, SPRING.pop.k)), 1, seg(t, 11.85, 12));
    bubT = 1 - easeOut(seg(t, 11.55, 11.8));
  }
  // place card: the last crossing at or before t (the loop's last one before the first)
  let e = CE_T.length - 1;
  let age = t + LOOP - CE_T[e];
  for (let i = 0; i < CE_T.length; i++) {
    if (CE_T[i] <= t) {
      e = i;
      age = t - CE_T[i];
    }
  }
  return {
    d,
    v,
    run,
    phase,
    pose: poseOf(phase, v, run, -1),
    face,
    hop,
    lean,
    squash,
    bubS,
    bubT,
    skidAge,
    cardFrom: CE_FROM[e],
    cardTo: CE_TO[e],
    cardDir: CE_DIR[e],
    cardAge: age,
  };
};
