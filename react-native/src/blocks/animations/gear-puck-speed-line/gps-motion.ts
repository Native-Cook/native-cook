// Gear Puck Speed Line: the autoplay timeline (creative spec §4 with the Pro addendum) as a pure function of the
// loop time, so every frame of the loop is reproducible and the three stacks can match it frame for frame. Seconds.

import { BANDS, G, SPRING, gearX } from './gps-theme';

export const LOOP = 10;

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
  const k = clamp((v - a) / (b - a), 0, 1);
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

/** A damped spring (mass 1) released at time 0 from `from` towards `to` with initial velocity v0: position at t. */
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

/** cubic-bezier(x1, y1, x2, y2) at x. */
export const bezier = (x: number, x1: number, y1: number, x2: number, y2: number) => {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  const bx = (u: number) => 3 * (1 - u) * (1 - u) * u * x1 + 3 * (1 - u) * u * u * x2 + u * u * u;
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 22; i++) {
    const m = (lo + hi) / 2;
    if (bx(m) < x) lo = m;
    else hi = m;
  }
  const u = (lo + hi) / 2;
  return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u;
};

/**
 * The climb: a car pulling away (the reference counts 0, 2, 9, 29, 43, 76 … 132 a second apart): slow off the line,
 * hard through the middle, tapering into the gear's cap. The spec's `emph` (0.05, 0.7, 0.1, 1) jumped to 60 mph in
 * 120 ms, so the numbers and the hump teleported.
 */
export const climb = (x: number) => {
  'worklet';
  return bezier(x, 0.42, 0.04, 0.22, 1);
};

/** A roll between two gears that leaves with zero velocity and arrives with a little (handed to the detent). */
const M_ARRIVE = 0.12;
const rollEase = (k: number) => {
  'worklet';
  return (1 - M_ARRIVE) * k * k * (3 - 2 * k) + M_ARRIVE * k * k * k;
};
const rollAt = (t: number, a: number, b: number, g0: number, g1: number) => {
  'worklet';
  if (t < b) return mix(g0, g1, rollEase(seg(t, a, b)));
  const v = ((3 * M_ARRIVE * (g1 - g0)) / (b - a));
  return springAt(t - b, g1, g1, v, SPRING.detent.d, SPRING.detent.k);
};

// ---- the timeline (s) ----
export const TL = {
  press1: 0.4,
  roll1: [0.52, 1.36] as const,
  infl1: 1.5,
  num1: 1.6,
  speed1: [1.68, 3.88] as const,
  press2: 4.17,
  roll2: [4.25, 4.68] as const,
  gapDrop: [4.25, 4.4] as const,
  gapFlat: [4.27, 4.52] as const,
  infl2: 4.7,
  num2: 4.74,
  speed2: [4.78, 6.38] as const,
  decel: [6.85, 8.15] as const,
  press3: 8.05,
  roll3: [8.15, 8.85] as const,
};

/** The speed (mph) at loop time t. */
export const speedAt = (t: number) => {
  'worklet';
  if (t < TL.speed1[0]) return 0;
  if (t < TL.speed1[1]) return 132 * climb(seg(t, TL.speed1[0], TL.speed1[1]));
  if (t < TL.speed2[0]) return 132;
  if (t < TL.speed2[1]) return 132 + 65 * climb(seg(t, TL.speed2[0], TL.speed2[1]));
  if (t < TL.decel[0]) return 197;
  if (t < TL.decel[1]) return 197 * (1 - easeInOut(seg(t, TL.decel[0], TL.decel[1])));
  return 0;
};

export const bandOf = (speed: number) => {
  'worklet';
  const s = Math.abs(speed);
  for (let i = 0; i < BANDS.length; i++) if (s < BANDS[i].upTo) return i;
  return BANDS.length - 1;
};

export const humpX = (speed: number) => {
  'worklet';
  return G.hump.x + (G.hump.span * Math.min(Math.abs(speed), G.hump.maxSpeed)) / G.hump.maxSpeed;
};
export const humpA = (speed: number, infl: number) => {
  'worklet';
  return infl * (G.hump.a0 + G.hump.aPerMph * Math.abs(speed));
};

// ---- tables built once (plain JS), captured by the worklets ----
const N = 2000;
/** Cell and strand phase (pt): creep 6 pt/s plus 1.2 pt/s per mph, scaled so the loop ends on a whole 24 pt period. */
const PHASE: number[] = (() => {
  const out = [0];
  let acc = 0;
  const dt = LOOP / N;
  for (let i = 1; i <= N; i++) {
    const tm = (i - 0.5) * dt;
    acc += (6 + 1.2 * speedAt(tm)) * dt;
    out.push(acc);
  }
  const k = (Math.round(acc / 24) * 24) / acc;
  return out.map((v) => v * k);
})();

type Crossing = { t: number; from: number; to: number; x: number };
/** Band changes of the autoplay speed curve: each is a front spreading from the crest at 700 pt/s. */
const CROSS: Crossing[] = (() => {
  const out: Crossing[] = [];
  let prev = bandOf(speedAt(0));
  for (let i = 1; i <= 10000; i++) {
    const tm = (i * LOOP) / 10000;
    const b = bandOf(speedAt(tm));
    if (b !== prev) {
      out.push({ t: tm, from: prev, to: b, x: humpX(speedAt(tm)) });
      prev = b;
    }
  }
  return out;
})();
export const CROSSINGS = CROSS;

export const FRONT_SPEED = 700;

const phaseAt = (t: number) => {
  'worklet';
  const f = (t / LOOP) * N;
  const i = Math.min(Math.floor(f), N - 1);
  return mix(PHASE[i], PHASE[i + 1], f - i);
};

/** The time at which the decel passes 50 mph: the numeral heads home while the hump still stands. */
const NUM_HOME = TL.decel[0] + (TL.decel[1] - TL.decel[0]) * 0.6;

export type Frame = {
  g: number; // gear position 0..4 (P R D S M)
  press: number; // ball pressed 0..1
  flash: number; // ring flash 0..1
  speed: number; // mph
  rate: number; // mph/s
  A: number; // hump amplitude (pt)
  x0: number; // hump centre (pt)
  lean: number; // -1..1, + while accelerating (the crest trails)
  jit: number; // crest jitter (pt)
  phase: number; // cell / strand phase (pt)
  curt: number; // curtain length beyond its 40 pt base (pt)
  whip: number; // hem whip amplitude (pt)
  sway: number; // hem sway (pt)
  cols: [number, number, number]; // band before the previous front, the previous front's, the last front's
  fPrev: [number, number]; // previous front: x, radius
  fLast: [number, number]; // last front: x, radius
  packetX: number;
  packetGain: number;
  pluckX: number;
  pluckA: number; // the line's pluck displacement now (pt)
  pluckLag: number; // the curtain's hem follows it a little later
  numK: number; // numeral parked 0 → in the hump 1
  drop: number; // torque gap: numeral under the line 0..1
  time: number; // seconds, for the whip
};

const flashAt = (age: number) => {
  'worklet';
  if (age < 0) return 0;
  if (age < 0.03) return age / 0.03;
  return Math.exp(-(age - 0.03) / 0.09);
};

const pressAt = (t: number, a: number, land: number) => {
  'worklet';
  if (t < a) return 0;
  if (t < land) return easeOut(seg(t, a, a + 0.12));
  return Math.max(springAt(t - land, 1, 0, 0, SPRING.pop.d, SPRING.pop.k), -0.08);
};

export const frontsAt = (t: number) => {
  'worklet';
  // latest crossing at or before t; -1 and below wrap into the previous loop
  let li = -1;
  for (let i = 0; i < CROSS.length; i++) if (CROSS[i].t <= t) li = i;
  const n = CROSS.length;
  const at = (i: number) => {
    const j = ((i % n) + n) % n;
    const c = CROSS[j];
    return { t: i < 0 ? c.t - LOOP : c.t, from: c.from, to: c.to, x: c.x };
  };
  const last = at(li);
  const prev = at(li - 1);
  return {
    cols: [prev.from, prev.to, last.to] as [number, number, number],
    fPrev: [prev.x, FRONT_SPEED * (t - prev.t)] as [number, number],
    fLast: [last.x, FRONT_SPEED * (t - last.t)] as [number, number],
  };
};

export const frameAt = (tIn: number): Frame => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const speed = speedAt(t);
  const rate = (speedAt(t + 0.01) - speedAt(Math.max(t - 0.01, 0))) / 0.02;
  const lean = clamp((speed - speedAt(Math.max(t - 0.25, 0))) / 0.25 / 70, -1, 1);

  // the ball
  let g = 0;
  if (t >= TL.roll1[0] && t < TL.roll2[0]) g = rollAt(t, TL.roll1[0], TL.roll1[1], 0, 2);
  else if (t >= TL.roll2[0] && t < TL.roll3[0]) g = rollAt(t, TL.roll2[0], TL.roll2[1], 2, 3);
  else if (t >= TL.roll3[0]) g = rollAt(t, TL.roll3[0], TL.roll3[1], 3, 0);
  let press = 0;
  if (t < TL.press2) press = pressAt(t, TL.press1, TL.roll1[1]);
  else if (t < TL.press3) press = pressAt(t, TL.press2, TL.roll2[1]);
  else press = pressAt(t, TL.press3, TL.roll3[1]);
  const flash = Math.max(flashAt(t - TL.roll1[1]), flashAt(t - TL.roll2[1]), 0.7 * flashAt(t - TL.roll3[1]));

  // the hump: inflates after the packet lands, collapses in the torque gap, deflates with the speed
  let infl = 0;
  if (t >= TL.infl1 && t < TL.gapFlat[0]) infl = springAt(t - TL.infl1, 0, 1, 0, SPRING.pop.d, SPRING.pop.k);
  else if (t >= TL.gapFlat[0] && t < TL.gapFlat[1]) infl = 1 - easeIn(seg(t, TL.gapFlat[0], TL.gapFlat[1]));
  else if (t >= TL.infl2 && t < TL.decel[0]) infl = springAt(t - TL.infl2, 0, 1, 0, SPRING.pop.d, SPRING.pop.k);
  else if (t >= TL.decel[0] && t < TL.decel[1]) infl = smooth(0, 30, speed);

  // the light packet: from the ball's seat along the line to the hump, 900 pt/s
  let packetX = 0;
  let packetGain = 0;
  const pk = (land: number, from: number, to: number) => {
    const dur = Math.max(Math.abs(to - from) / 900, 0.06);
    const k = (t - land) / dur;
    if (k >= 0 && k < 1.25) {
      packetX = mix(from, to, Math.min(k, 1));
      packetGain = k < 1 ? 1 : 1 - (k - 1) / 0.25;
    }
  };
  pk(TL.roll1[1], gearX(2), humpX(0));
  pk(TL.roll2[1], gearX(3), humpX(132));

  // the numeral
  let numK = 0;
  if (t >= TL.num1 && t < NUM_HOME) numK = springAt(t - TL.num1, 0, 1, 0, SPRING.glide.d, SPRING.glide.k);
  else if (t >= NUM_HOME) numK = springAt(t - NUM_HOME, 1, 0, 0, SPRING.glide.d, SPRING.glide.k);
  let drop = 0;
  if (t >= TL.gapDrop[0] && t < TL.num2) drop = easeOut(seg(t, TL.gapDrop[0], TL.gapDrop[1]));
  else if (t >= TL.num2 && t < TL.decel[0]) drop = Math.max(springAt(t - TL.num2, 1, 0, 0, SPRING.glide.d, SPRING.glide.k), 0);

  const hold = smooth(TL.speed2[1], TL.speed2[1] + 0.08, t) * (1 - smooth(TL.decel[0] - 0.08, TL.decel[0], t));
  const fr = frontsAt(t);
  return {
    g,
    press,
    flash,
    speed,
    rate,
    A: humpA(speed, infl),
    x0: humpX(speed),
    lean,
    jit: hold * Math.sin(2 * Math.PI * 24 * t),
    phase: phaseAt(t),
    curt: speed * mix(0.55, 1, Math.min(infl, 1)),
    whip: 10 * smooth(180, 190, speed),
    sway: 3 * Math.sin((2 * Math.PI * 4 * t) / LOOP),
    cols: fr.cols,
    fPrev: fr.fPrev,
    fLast: fr.fLast,
    packetX,
    packetGain,
    pluckX: 0,
    pluckA: 0,
    pluckLag: 0,
    numK,
    drop,
    time: t,
  };
};

/** The poster: 80 ms after the amber front starts on the way up. */
export const POSTER_T = (() => {
  const c = CROSS.find((x) => x.to === 2 && x.from === 1);
  return c ? c.t + 0.08 : 3;
})();

/** The line's height at x for a frame (pt, y grows down). */
export const curveY = (f: Frame, x: number) => {
  'worklet';
  const dx = x - f.x0;
  // the lean only ever widens one flank (the rear one while accelerating, the front one while braking), so the
  // crest keeps room for three 76 pt digits
  const wide = 1 + G.hump.lean * Math.abs(f.lean);
  const s = G.hump.sigma * ((dx < 0) === f.lean > 0 ? wide : 1);
  const h = Math.exp((-dx * dx) / (2 * s * s));
  const p = f.pluckA * Math.exp(-((x - f.pluckX) * (x - f.pluckX)) / (2 * 50 * 50));
  return G.rest - (f.A + f.jit) * h + p;
};

/** Where the numeral sits (centre x, baseline y, scale) and its width in cells. */
export const numeralLayout = (f: Frame, cells: number) => {
  'worklet';
  const N0 = G.numeral;
  const w = cells * N0.cell;
  const hx = clamp(f.x0, 14 + w / 2, 402 - 14 - w / 2);
  const parkedCx = G.parked.x + (w * G.parked.scale) / 2;
  const k = f.numK;
  let cx = mix(parkedCx, hx, k);
  let base = mix(G.parked.base, N0.humpBase, k);
  let scale = mix(G.parked.scale, 1, k);
  const d = f.drop;
  cx = mix(cx, hx, d);
  base = mix(base, G.dropped.base, d);
  scale = mix(scale, G.dropped.scale, d);
  return { cx, base, scale, w };
};
