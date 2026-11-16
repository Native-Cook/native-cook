// Coin Pile Collect — the state machine around the solver. One flat number[] holds the coins (STRIDE each) and, after
// them, a control block; every function is a worklet that mutates it in place, so it runs on the UI thread in the
// frame callback and in node for offline review with the same numbers.
//
// Modes: LIVE (the solver runs; the thumb works the valve and the pile), RETURN (coins that are missing rain in from
// above in stacking order, coins that lie elsewhere glide back to their place), FADE (reduced motion: groups of coins
// fade out and in from the pill outward; no flow).

import {
  AX,
  AY,
  ALIVE,
  CNT,
  DRAIN,
  DUR,
  EV_COLLECTED,
  EV_CREDIT,
  EV_FLOW,
  EV_LASTSPIN,
  EV_SIZE,
  FACE,
  GLOW,
  HS,
  IN_FINGER,
  IN_FLICKON,
  IN_FR,
  IN_GX,
  IN_RATE,
  IN_SIZE,
  PHI,
  PX,
  PY,
  R0AX,
  R0AY,
  R0PHI,
  R0X,
  R0Y,
  R0Z,
  RKIND,
  RT0,
  STRIDE,
  VAL,
  X,
  Y,
  Z,
  coinValues,
  loadHeap,
  rnd,
  stepSim,
  type Cfg,
} from './cpc-physics';
import { DT, EMPTY, LOOP, PILL, RAIN, ROLL, VALVE, clamp01, mix, scriptAt, smooth } from './cpc-motion';

export { DT, EV_SIZE, IN_SIZE };

// control block, after the coins
export const NMAX = 32;
export const CTL = NMAX * STRIDE;
export const C_STEP = 0; // fixed steps run since the clock was zero
export const C_MODE = 1; // 0 live, 1 return, 2 fade out, 3 fade in
export const C_MT = 2; // seconds in the current mode
export const C_VALVE = 3;
export const C_THR = 4;
export const C_LATCH = 5;
export const C_EMPTYT = 6; // seconds since the tile emptied, -1 while it holds coins
export const C_PRESS = 7; // the pill's pressed look, 0..1
export const C_BULGE = 8; // the pill's scaleY bulge, offset from 1
export const C_BULGEV = 9;
export const C_LIT = 10; // the amount's lightness and the rim line while coins pass, 0..1
export const C_PILLA = 11; // the pill's opacity
export const C_CENTS = 12; // the amount shown (cents, chases C_TARGET)
export const C_TARGET = 13; // the amount the coins stand for (cents)
export const C_KICKAT = 14; // clock time of the last empty, -1 if none this cycle
export const C_HOLDPREV = 15;
export const C_RETEND = 16; // when the current return ends, s into the mode
export const C_LANDED = 17; // coins landed in the current return
export const C_RET_ALL = 18; // 1 if the current return is a full rain
export const C_XTH0 = 19; // exchange coin easing after a restore: start angle, end angle, speed
export const C_XTH1 = 20;
export const C_XW = 21;
export const C_FLOW = 22; // 0..1 rate share, for the rim line
export const C_TOTAL = 23; // total cents of the tile
export const C_N = 24;
export const C_TAP = 25; // seconds the current press has lasted
export const C_FRESH = 26; // 1 when the press began on a latched pour (it pauses and does nothing else)
export const C_CREDIT = 27; // the solver's emission credit and smoothed flow, carried between steps
export const C_SFLOW = 28;
export const CTL_SIZE = 32;
export const STATE_SIZE = CTL + CTL_SIZE;

// inputs beyond the solver's own (IN_*): the pill and the commands
export const IN_HOLD = 8; // 1 while the pill is held
export const IN_SLIDE = 9; // pt from the pill's centre while held
export const IN_REDUCED = 10;
export const IN_CMD = 11; // 1 pour everything, 2 stop, 3 refill, 4 restore to rest: consumed by the step
export const IN_UP = 12; // 1 on the step the pill is released
export const IN_FLICKT = 13; // seconds since a finger last touched the pile (the engine keeps it)
export const INPUT_SIZE = 16;

export const MODE_LIVE = 0;
export const MODE_RETURN = 1;
export const MODE_FADE_OUT = 2;
export const MODE_FADE_IN = 3;

// extra events
export const EVX_EMPTY = 12; // 1 on the step the tile emptied
export const EVX_LANDED = 13; // coins that landed in this step
export const EVX_REFILLED = 14; // 1 on the step the tile is full again
export const EVX_LATCH = 15; // +1 the pour latched open, -1 a press paused it
export const EVENT_SIZE = 16;

export function makeState() {
  return new Array<number>(STATE_SIZE).fill(0);
}
export function makeInputs() {
  const inp = new Array<number>(INPUT_SIZE).fill(0);
  inp[IN_FLICKT] = 99;
  return inp;
}
export function makeEvents() {
  return new Array<number>(EVENT_SIZE).fill(0);
}

/** values of the coins in cents, written into the state, summing to total */
export function setValues(cfg: Cfg, A: number[], totalCents: number) {
  'worklet';
  const vals = [0];
  vals.length = cfg.n;
  coinValues(cfg.n, totalCents, cfg.seed, vals);
  for (let i = 0; i < cfg.n; i++) A[i * STRIDE + VAL] = vals[i];
  A[CTL + C_TOTAL] = totalCents;
  A[CTL + C_N] = cfg.n;
}

/** the resting state: the full heap, valve shut, pill bright, the clock at zero */
export function initState(cfg: Cfg, A: number[], totalCents: number) {
  'worklet';
  loadHeap(cfg, A);
  setValues(cfg, A, totalCents);
  A[CTL + C_STEP] = 0;
  A[CTL + C_MODE] = MODE_LIVE;
  A[CTL + C_MT] = 0;
  A[CTL + C_VALVE] = 0;
  A[CTL + C_THR] = 1;
  A[CTL + C_LATCH] = 0;
  A[CTL + C_EMPTYT] = -1;
  A[CTL + C_PRESS] = 0;
  A[CTL + C_BULGE] = 0;
  A[CTL + C_BULGEV] = 0;
  A[CTL + C_LIT] = 0;
  A[CTL + C_PILLA] = 1;
  A[CTL + C_CENTS] = totalCents;
  A[CTL + C_TARGET] = totalCents;
  A[CTL + C_KICKAT] = -1;
  A[CTL + C_HOLDPREV] = 0;
  A[CTL + C_RETEND] = 0;
  A[CTL + C_LANDED] = 0;
  A[CTL + C_RET_ALL] = 0;
  A[CTL + C_FLOW] = 0;
  A[CTL + C_TAP] = 0;
  A[CTL + C_FRESH] = 0;
  A[CTL + C_CREDIT] = 0;
  A[CTL + C_SFLOW] = 0;
}

export function clockOf(A: number[]) {
  'worklet';
  return A[CTL + C_STEP] * DT;
}

export function countCents(cfg: Cfg, A: number[]) {
  'worklet';
  let sum = 0;
  for (let i = 0; i < cfg.n; i++) {
    const o = i * STRIDE;
    if (A[o + CNT] > 0.5) sum += A[o + VAL];
  }
  return sum;
}

/** where a returning coin starts from above the tile (seeded) */
function spawnPose(cfg: Cfg, A: number[], i: number, order: number) {
  'worklet';
  const o = i * STRIDE;
  const h = i * HS;
  const rx = cfg.heap[h];
  const ry = cfg.heap[h + 1];
  const u = rnd(cfg.seed, i, 41);
  const v = rnd(cfg.seed, i, 42);
  const x = Math.min(Math.max(rx + (u - 0.5) * 60, cfg.r * 0.8), cfg.w - cfg.r * 0.8);
  const y = -cfg.r - RAIN.spawnY - v * RAIN.spawnSpread - (order % 3) * 14;
  A[o + R0X] = x;
  A[o + R0Y] = y;
  A[o + R0Z] = cfg.heap[h + 2] + 70;
  const a = rnd(cfg.seed, i, 43) * 6.2831853;
  const m = 0.55 + 0.4 * rnd(cfg.seed, i, 44);
  A[o + R0AX] = Math.cos(a) * m;
  A[o + R0AY] = Math.sin(a) * m;
  A[o + R0PHI] = rnd(cfg.seed, i, 45) * 6.2831853;
  // seconds of flight: a free fall from y to the coin's place
  const d = Math.max(ry - y, 40);
  A[o + DUR] = Math.sqrt((2 * d) / RAIN.gravity);
}

/**
 * Start a return: coins that are missing fall in from above in stacking order, coins that lie elsewhere glide back
 * to their place. `rain` (a full refill) staggers every coin by RAIN.stagger; a restore after a touch uses a shorter one.
 */
export function beginReturn(cfg: Cfg, A: number[], rain: boolean) {
  'worklet';
  const n = cfg.n;
  const stagger = rain ? RAIN.stagger : RAIN.glideStagger * 2;
  let k = 0;
  let end = 0;
  for (let q = 0; q < n; q++) {
    const i = cfg.order[q];
    const o = i * STRIDE;
    const h = i * HS;
    const present = A[o + ALIVE] > 0.5 && A[o + CNT] > 0.5 && A[o + DRAIN] === 0 && !rain;
    if (present) {
      A[o + RKIND] = 0;
      A[o + R0X] = A[o + X];
      A[o + R0Y] = A[o + Y];
      A[o + R0Z] = A[o + Z];
      A[o + R0AX] = A[o + AX];
      A[o + R0AY] = A[o + AY];
      A[o + R0PHI] = A[o + PHI];
      A[o + RT0] = 0;
      A[o + DUR] = RAIN.glide;
      end = Math.max(end, RAIN.glide);
    } else {
      A[o + RKIND] = 1;
      A[o + CNT] = 0;
      A[o + ALIVE] = 0;
      A[o + DRAIN] = 0;
      A[o + GLOW] = 0;
      spawnPose(cfg, A, i, k);
      A[o + RT0] = k * stagger;
      end = Math.max(end, k * stagger + A[o + DUR] + RAIN.bounce);
      k++;
    }
    A[o + FACE] = cfg.heap[h + 6];
  }
  A[CTL + C_MODE] = MODE_RETURN;
  A[CTL + C_MT] = 0;
  A[CTL + C_RETEND] = end + 0.05;
  A[CTL + C_LANDED] = 0;
  A[CTL + C_RET_ALL] = rain ? 1 : 0;
  A[CTL + C_LATCH] = 0;
  A[CTL + C_VALVE] = 0;
  A[CTL + C_LIT] = 0;
  A[CTL + C_CREDIT] = 0;
  A[CTL + C_SFLOW] = 0;
  A[CTL + C_TARGET] = countCents(cfg, A);
}

/** a coin's pose at s seconds into a return (kind 1: a fall, kind 0: a glide); writes into the state */
function returnPose(cfg: Cfg, A: number[], i: number, mt: number, ev: number[]) {
  'worklet';
  const o = i * STRIDE;
  const h = i * HS;
  const s = mt - A[o + RT0];
  const rx = cfg.heap[h];
  const ry = cfg.heap[h + 1];
  const rz = cfg.heap[h + 2];
  const rax = cfg.heap[h + 3];
  const ray = cfg.heap[h + 4];
  const rphi = cfg.heap[h + 5];
  if (A[o + RKIND] < 0.5) {
    // glide back: an ease with no overshoot, so nothing crosses a neighbour on the way
    const k = smooth(0, 1, s / A[o + DUR]);
    A[o + X] = mix(A[o + R0X], rx, k);
    A[o + Y] = mix(A[o + R0Y], ry, k);
    A[o + Z] = mix(A[o + R0Z], rz, k);
    A[o + AX] = mix(A[o + R0AX], rax, k);
    A[o + AY] = mix(A[o + R0AY], ray, k);
    A[o + PHI] = rphi;
    A[o + PX] = A[o + X];
    A[o + PY] = A[o + Y];
    return;
  }
  if (s < 0) {
    A[o + ALIVE] = 0;
    return;
  }
  const tau = A[o + DUR];
  A[o + ALIVE] = 1;
  if (s < tau) {
    const k = s / tau;
    A[o + Y] = A[o + R0Y] + (ry - A[o + R0Y]) * k * k;
    const dx = smooth(0, 1, k);
    A[o + X] = mix(A[o + R0X], rx, dx);
    // it tumbles: the tilt settles over the second half of the fall, the emblem keeps turning
    const tk = smooth(0.25, 1, k);
    A[o + AX] = mix(A[o + R0AX], rax, tk);
    A[o + AY] = mix(A[o + R0AY], ray, tk);
    A[o + PHI] = rphi + (A[o + R0PHI] - rphi) * (1 - k) + (1 - k) * 9.4;
    A[o + Z] = rz + (A[o + R0Z] - rz) * (1 - k);
    A[o + CNT] = 0;
  } else {
    const u = s - tau;
    if (A[o + CNT] < 0.5) {
      A[o + CNT] = 1;
      ev[EVX_LANDED] += 1;
      A[CTL + C_LANDED] += 1;
      A[CTL + C_TARGET] += A[o + VAL];
    }
    const k = clamp01(u / RAIN.bounce);
    // a little rebound and a rock that settle: z rises a few points and falls back, the tilt wobbles out
    const decay = Math.exp(-u * 9);
    A[o + Y] = ry;
    A[o + X] = rx;
    A[o + Z] = rz + RAIN.bounceZ * decay * Math.abs(Math.sin(u * 17));
    const wob = decay * Math.sin(u * 22) * 0.12 * (1 - k);
    A[o + AX] = rax + wob;
    A[o + AY] = ray - wob * 0.6;
    A[o + PHI] = rphi;
  }
  A[o + PX] = A[o + X];
  A[o + PY] = A[o + Y];
}

function countAlive(cfg: Cfg, A: number[]) {
  'worklet';
  let c = 0;
  for (let i = 0; i < cfg.n; i++) {
    const o = i * STRIDE;
    if (A[o + ALIVE] > 0.5 && A[o + CNT] > 0.5 && A[o + DRAIN] === 0) c++;
  }
  return c;
}
function countDraining(cfg: Cfg, A: number[]) {
  'worklet';
  let c = 0;
  for (let i = 0; i < cfg.n; i++) {
    const o = i * STRIDE;
    if (A[o + ALIVE] > 0.5 && A[o + DRAIN] > 0) c++;
  }
  return c;
}

/** reduced motion: groups of coins fade out and in from the pill outward in steps; the amount steps with them */
function stepFade(cfg: Cfg, A: number[], inp: number[], ev: number[]) {
  'worklet';
  A[CTL + C_MT] += DT;
  const mt = A[CTL + C_MT];
  const n = cfg.n;
  const out = A[CTL + C_MODE] === MODE_FADE_OUT;
  const group = 0.12;
  // three groups by distance from the mouth
  let landed = 0;
  for (let i = 0; i < n; i++) {
    const o = i * STRIDE;
    const dx = cfg.mx - A[o + X];
    const dy = cfg.my - A[o + Y];
    const d = Math.sqrt(dx * dx + dy * dy);
    const g = d < 90 ? 0 : d < 150 ? 1 : 2;
    const gi = out ? g : 2 - g;
    const k = clamp01((mt - gi * group) / group);
    const a = out ? 1 - k : k;
    A[o + ALIVE] = a;
    A[o + CNT] = a > 0.5 ? 1 : 0;
    A[o + GLOW] = 0;
    if (a > 0.5) landed += 1;
  }
  A[CTL + C_TARGET] = countCents(cfg, A);
  A[CTL + C_CENTS] = A[CTL + C_TARGET]; // the amount steps, it does not roll
  if (mt >= group * 3 + 0.02) {
    if (out) {
      A[CTL + C_MODE] = MODE_LIVE;
      A[CTL + C_EMPTYT] = 0;
      A[CTL + C_KICKAT] = clockOf(A);
      A[CTL + C_PILLA] = EMPTY.pillAlpha;
      A[CTL + C_MT] = 0;
      ev[EVX_EMPTY] = 1;
    } else {
      loadHeap(cfg, A);
      A[CTL + C_MODE] = MODE_LIVE;
      A[CTL + C_EMPTYT] = -1;
      A[CTL + C_TARGET] = A[CTL + C_TOTAL];
      A[CTL + C_CENTS] = A[CTL + C_TOTAL];
      A[CTL + C_PILLA] = 1;
      ev[EVX_REFILLED] = 1;
    }
  }
  if (landed < 0) ev[EVX_LANDED] = 0;
}

/** one fixed step of the whole machine */
export function engineStep(cfg: Cfg, A: number[], inp: number[], ev: number[]) {
  'worklet';
  const dt = DT;
  let mode = A[CTL + C_MODE];
  const n = cfg.n;
  for (let q = 0; q < EVENT_SIZE; q++) ev[q] = 0;
  A[CTL + C_STEP] += 1;
  const cmd = inp[IN_CMD];
  inp[IN_CMD] = 0;

  // the pill's pressed look and the press clock
  const hold = inp[IN_HOLD] > 0.5 ? 1 : 0;
  const prevHold = A[CTL + C_HOLDPREV];
  const latched = A[CTL + C_LATCH] > 0.5;
  let held = hold;
  if (hold > 0.5 && prevHold < 0.5) {
    A[CTL + C_TAP] = 0;
    A[CTL + C_FRESH] = latched ? 1 : 0;
    if (latched) {
      // pressing during a latched pour pauses it
      A[CTL + C_LATCH] = 0;
      ev[EVX_LATCH] = -1;
    }
  }
  if (hold > 0.5) A[CTL + C_TAP] += dt;
  if (hold < 0.5 && prevHold > 0.5) {
    // released: a short press that began on an empty valve latches it open
    if (A[CTL + C_TAP] < 0.18 && A[CTL + C_FRESH] < 0.5 && mode === MODE_LIVE && A[CTL + C_EMPTYT] < 0) {
      A[CTL + C_LATCH] = 1;
      ev[EVX_LATCH] = 1;
    }
    A[CTL + C_FRESH] = 0;
  }
  if (A[CTL + C_FRESH] > 0.5) held = 0; // the pause press does not hold the valve open
  A[CTL + C_HOLDPREV] = hold;
  const press = A[CTL + C_PRESS];
  const pressTarget = hold > 0.5 && mode === MODE_LIVE ? 1 : 0;
  A[CTL + C_PRESS] = press + (pressTarget - press) * Math.min(dt / PILL.pressTau, 1);

  if (cmd === 1) A[CTL + C_LATCH] = mode === MODE_LIVE && A[CTL + C_EMPTYT] < 0 ? 1 : A[CTL + C_LATCH];
  if (cmd === 2) A[CTL + C_LATCH] = 0;
  if (cmd === 3 && mode === MODE_LIVE) {
    // refill: what is missing rains in from above; what is there stays
    beginReturn(cfg, A, A[CTL + C_TARGET] === 0);
    mode = MODE_RETURN;
  }

  if (mode === MODE_LIVE) {
    const alive = countAlive(cfg, A);
    const lat = A[CTL + C_LATCH] > 0.5;
    const want = (held > 0.5 || lat) && alive > 0 && A[CTL + C_EMPTYT] < 0 && inp[IN_REDUCED] < 0.5;
    const v = A[CTL + C_VALVE];
    // the first coin leaves quickly after the valve opens: preload most of its credit
    if (want && v < 0.001 && A[CTL + C_CREDIT] < 0.7) A[CTL + C_CREDIT] = 0.7;
    const nv = v + ((want ? 1 : 0) - v) * Math.min(dt / (want ? VALVE.attack : VALVE.release), 1);
    A[CTL + C_VALVE] = nv < 0.0005 ? 0 : nv;
    // the thumb sliding away from the pill throttles the flow; a latched pour has no thumb
    const slide = held > 0.5 && !lat ? inp[IN_SLIDE] : 0;
    const thrTarget = 1 - (1 - VALVE.throttleMin) * smooth(0, VALVE.throttleSlide, slide);
    A[CTL + C_THR] += (thrTarget - A[CTL + C_THR]) * Math.min(dt / VALVE.throttleLag, 1);
    const rate = VALVE.rate * A[CTL + C_VALVE] * A[CTL + C_THR];
    inp[IN_RATE] = rate;
    inp[IN_FLICKT] = inp[IN_FINGER] > 0.5 ? 0 : inp[IN_FLICKT] + dt;
    inp[IN_FLICKON] = inp[IN_FLICKT] < 0.6 ? 1 : 0;
    ev[EV_CREDIT] = A[CTL + C_CREDIT];
    ev[EV_FLOW] = A[CTL + C_SFLOW];
    stepSim(cfg, A, dt, inp, ev);
    A[CTL + C_CREDIT] = ev[EV_CREDIT];
    A[CTL + C_SFLOW] = ev[EV_FLOW];
    A[CTL + C_FLOW] = clamp01(rate / VALVE.rate);

    // collected coins: the pill bulges, the amount follows the coins
    const coll = ev[EV_COLLECTED];
    if (coll > 0) A[CTL + C_BULGEV] += coll * 1.8; // about 3.5 % of scaleY per coin at the spring's peak
    A[CTL + C_TARGET] = countCents(cfg, A);

    // the tile empties: dim the pill, count the beat, then rain
    const now = countAlive(cfg, A);
    const draining = countDraining(cfg, A);
    if (now === 0 && draining === 0 && ev[EV_LASTSPIN] <= 0 && A[CTL + C_EMPTYT] < 0 && A[CTL + C_TARGET] === 0) {
      A[CTL + C_EMPTYT] = 0;
      A[CTL + C_KICKAT] = clockOf(A);
      A[CTL + C_LATCH] = 0;
      ev[EVX_EMPTY] = 1;
    } else if (A[CTL + C_EMPTYT] >= 0) {
      A[CTL + C_EMPTYT] += dt;
      if (A[CTL + C_EMPTYT] >= EMPTY.rainDelay && inp[IN_REDUCED] < 0.5) beginReturn(cfg, A, true);
    }
    const pillTarget = A[CTL + C_EMPTYT] >= 0 ? EMPTY.pillAlpha : 1;
    A[CTL + C_PILLA] += (pillTarget - A[CTL + C_PILLA]) * Math.min(dt / PILL.alphaTau, 1);
    const litTarget = A[CTL + C_FLOW] > 0.05 ? 1 : 0;
    A[CTL + C_LIT] += (litTarget - A[CTL + C_LIT]) * Math.min(dt / PILL.litTau, 1);
    // the cyan line follows the rate, not just on/off
  } else if (mode === MODE_RETURN) {
    A[CTL + C_MT] += dt;
    const mt = A[CTL + C_MT];
    for (let i = 0; i < n; i++) returnPose(cfg, A, i, mt, ev);
    // the pill brightens with the amount that has landed
    const total = A[CTL + C_TOTAL];
    const frac = total > 0 ? A[CTL + C_TARGET] / total : 1;
    const full = A[CTL + C_RET_ALL] > 0.5;
    const pillTarget = full ? EMPTY.pillAlpha + (1 - EMPTY.pillAlpha) * clamp01(frac) : 1;
    A[CTL + C_PILLA] += (pillTarget - A[CTL + C_PILLA]) * Math.min(dt / PILL.alphaTau, 1);
    A[CTL + C_LIT] += (0 - A[CTL + C_LIT]) * Math.min(dt / PILL.litTau, 1);
    A[CTL + C_FLOW] = 0;
    A[CTL + C_VALVE] = 0;
    if (mt >= A[CTL + C_RETEND]) {
      loadHeap(cfg, A);
      A[CTL + C_MODE] = MODE_LIVE;
      A[CTL + C_EMPTYT] = -1;
      A[CTL + C_KICKAT] = full ? A[CTL + C_KICKAT] : -1;
      A[CTL + C_TARGET] = A[CTL + C_TOTAL];
      A[CTL + C_PILLA] = 1;
      A[CTL + C_LATCH] = 0;
      ev[EVX_REFILLED] = 1;
    }
  } else {
    stepFade(cfg, A, inp, ev);
  }

  // the pill's bulge spring and the amount's roll are fixed-step integrators too, so a replay reproduces them
  const bw = (2 * Math.PI) / PILL.bulgeSpring.response;
  const zeta = PILL.bulgeSpring.zeta;
  let b = A[CTL + C_BULGE];
  let bv = A[CTL + C_BULGEV];
  bv += (-bw * bw * b - 2 * zeta * bw * bv) * dt;
  b += bv * dt;
  A[CTL + C_BULGE] = b;
  A[CTL + C_BULGEV] = bv;
  const cur = A[CTL + C_CENTS];
  const tgt = A[CTL + C_TARGET];
  const nc = cur + (tgt - cur) * Math.min(dt / ROLL.tau, 1);
  A[CTL + C_CENTS] = Math.abs(nc - tgt) < 0.004 ? tgt : nc;
}

/** reduced motion: Collect fades the coins out and, after a beat, in again */
export function reducedStep(cfg: Cfg, A: number[], inp: number[], ev: number[]) {
  'worklet';
  const mode = A[CTL + C_MODE];
  A[CTL + C_STEP] += 1;
  for (let q = 0; q < EVENT_SIZE; q++) ev[q] = 0;
  const hold = inp[IN_HOLD] > 0.5;
  const prev = A[CTL + C_HOLDPREV] > 0.5;
  A[CTL + C_HOLDPREV] = hold ? 1 : 0;
  const cmd = inp[IN_CMD];
  inp[IN_CMD] = 0;
  if (mode === MODE_LIVE) {
    if (A[CTL + C_EMPTYT] < 0) {
      if ((hold && !prev) || cmd === 1) {
        A[CTL + C_MODE] = MODE_FADE_OUT;
        A[CTL + C_MT] = 0;
        A[CTL + C_PRESS] = 1;
      }
    } else {
      A[CTL + C_EMPTYT] += DT;
      A[CTL + C_PRESS] = 0;
      if (A[CTL + C_EMPTYT] >= 1.2) {
        A[CTL + C_MODE] = MODE_FADE_IN;
        A[CTL + C_MT] = 0;
      }
    }
    if (hold === false) A[CTL + C_PRESS] = 0;
  } else {
    stepFade(cfg, A, inp, ev);
  }
}

/** the autoplay thumb for the step about to run, then the step itself */
export function autoStep(cfg: Cfg, A: number[], inp: number[], ev: number[]) {
  'worklet';
  const t = clockOf(A);
  // the first 600 ms: the two coins on top of the heap rock about 2° and stop (a function of the loop clock, from the heap's own tilt)
  if (t < 0.62) {
    for (let k = 0; k < 2; k++) {
      const i = cfg.order[cfg.n - 1 - k];
      const h = i * HS;
      const w = t < 0.6 ? 0.035 * Math.exp(-t * 5.5) * Math.sin(t * 24 + k * 1.7) : 0;
      A[i * STRIDE + AX] = cfg.heap[h + 3] + w;
      A[i * STRIDE + AY] = cfg.heap[h + 4] - w * 0.6;
    }
  }
  scriptAt(t, inp, IN_HOLD);
  inp[IN_FINGER] = 0;
  engineStep(cfg, A, inp, ev);
}

/** replay autoplay from the start to loop time t (a seek, as the dev hook does) */
export function replayTo(cfg: Cfg, A: number[], inp: number[], ev: number[], t: number, total: number) {
  'worklet';
  initState(cfg, A, total);
  for (let q = 0; q < EVENT_SIZE; q++) ev[q] = 0;
  const steps = Math.floor(Math.max(0, Math.min(t, LOOP)) / DT + 1e-6);
  for (let s = 0; s < steps; s++) autoStep(cfg, A, inp, ev);
}

export function resetInputs(inp: number[]) {
  'worklet';
  for (let q = 0; q < INPUT_SIZE; q++) inp[q] = 0;
  inp[IN_FLICKT] = 99;
  inp[IN_FR] = 0;
  inp[IN_GX] = 0;
}
