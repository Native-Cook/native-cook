// Elastic String Quiz: the timeline. Everything the screen shows is `compose(events, t)`: a record of the moments
// things happened (a bead grabbed, released, a pulse fired, an answer accepted, a cord snapped, a string plucked) and
// pure functions of the time since each. Autoplay builds that record from its script for any loop time
// (`autoEventsAt`), so the loop is a pure function of t; a finger writes the same record live, so a touch can take
// over the loop with no jump. Times are in seconds, distances in pt on the 402 × 874 canvas.

import { G, LREST, PULSE_SPEED, SPRING } from './esq-theme';

export const LOOP = 12;
export const NEG = -1e6;
export const POS = 1e6;
export const MAXL = 10; // longest prompt word
export const PULL = 120; // full visual tension (pt)
export const ARM = 44; // pt of pull that counts as an answer

/**
 * The loop reset. The three strings spring back up their own paths into the knot, which swallows them in one squash
 * and a soft ring; the next word rolls in under a mask while the knot relaxes, then the strings shoot out again.
 * All times in seconds after the transition starts.
 */
export const TR = {
  st: [0, 0.06, 0.12], // the ends are 60 ms apart
  d: 14,
  k: 180,
  squash: 0.26,
  ring: 0.5,
  rel: 0.62, // the knot lets go of its swallowed size
  nx: 0.7, // the next word starts to roll in
  out: 1.2, // the strings shoot out
  settle: 2.05, // the springs' last whisper is blended to rest between settle and end
  end: 2.2,
  over: 0.6, // the overshoot past the anchor is scaled down to this
  mask: 273, // bottom edge of the word's mask (canvas pt)
  roll: 64, // how far under the mask a letter waits
} as const;

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

/** A damped spring (mass 1) released at time 0 from `from` towards `to` with velocity v0: its position at t. */
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

/** When a string's end, released from its anchor, first reaches the knot. */
export const CROSS = (() => {
  let lo = 0;
  let hi = 0.6;
  for (let n = 0; n < 40; n++) {
    const mid = (lo + hi) / 2;
    if (springAt(mid, 1, 0, 0, TR.d, TR.k) > 0) lo = mid;
    else hi = mid;
  }
  return hi;
})();
/** The moment the last end is in the knot: the squash, the ring and the selection haptic start here. */
export const U_SQ = TR.st[2] + CROSS;

/**
 * How far along its path (1 = anchored, 0 = in the knot) a string's end is at u seconds into the transition: the end
 * springs up to the knot and stays there, then shoots out again with the same spring (and a little overshoot).
 */
export const endAt = (u: number, i: number, recap: number) => {
  'worklet';
  const q = u - TR.st[i];
  if (u < TR.out) {
    if (recap === 1 || q >= CROSS) return 0;
    return q <= 0 ? 1 : Math.max(springAt(q, 1, 0, 0, TR.d, TR.k), 0);
  }
  const r = u - TR.out - TR.st[i];
  return r <= 0 ? 0 : springAt(r, 0, 1, 0, TR.d, TR.k);
};

/** The knot's squash while it swallows the strings: ×1.18 → 0.92 → 1. */
export const squashAt = (u: number) => {
  'worklet';
  const q = (u - U_SQ) / TR.squash;
  if (q <= 0 || q >= 1) return 1;
  if (q < 0.4) return mix(1, 1.18, easeOut(q / 0.4));
  if (q < 0.75) return mix(1.18, 0.92, easeInOut((q - 0.4) / 0.35));
  return mix(0.92, 1, easeInOut((q - 0.75) / 0.25));
};

/** The finger's travel → bead travel: 96 pt of pull needs 240 pt of finger; upwards a rubber band of 8 pt. */
export const elastic = (dy: number) => {
  'worklet';
  if (dy >= 0) return dy / (1 + dy / 160);
  return -8 * (1 - Math.exp(dy / 30));
};
/** Inverse of `elastic` for s ≥ 0 (grabbing a bead mid-recoil). */
export const elasticInv = (s: number) => {
  'worklet';
  if (s >= 0) return (160 * Math.min(s, 150)) / (160 - Math.min(s, 150));
  return 30 * Math.log(Math.max(1 + s / 8, 0.01));
};
export const elasticSlope = (dy: number) => {
  'worklet';
  if (dy < 0) return 0.2;
  const q = 1 + dy / 160;
  return 1 / (q * q);
};

/** Unit vectors from the hub to each cord's shoulder: the hub gives towards the pull. */
const dg = Math.hypot(G.bx[2] - G.hubX, G.shoulderY - G.hubY);
const DIRX = [(G.bx[0] - G.hubX) / dg, 0, (G.bx[2] - G.hubX) / dg];
const DIRY = [(G.shoulderY - G.hubY) / dg, 1, (G.shoulderY - G.hubY) / dg];
const VERT = G.beadY - G.shoulderY;

/** Content and layout the timeline needs (glyph centres of every prompt word, the screen's top in canvas pt). */
export type Script = {
  n: number;
  answers: number[]; // the answer index of each prompt
  lens: number[];
  cx: number[]; // [word * MAXL + k] glyph centre x
  cA: number; // autoplay: first (right) answer
  cB: number; // autoplay: second answer
  cW: number; // autoplay: the wrong pull
  score: number;
  top: number;
  arm: number;
};

/** The event record (all arrays per cord 0..2). */
export type Ev = {
  rm: number;
  held: number[];
  hs: number[];
  hb: number[];
  relT: number[];
  relS: number[];
  relV: number[];
  relB: number[];
  tapT: number[];
  tapS0: number[];
  haloOn: number[];
  haloOff: number[];
  pulseT: number[];
  pulseK: number[]; // 1 answer, 2 twang
  pend: number[]; // 1 while an answer pulse has not reached the hub
  snapT: number[];
  snapS: number[];
  snapB: number[];
  dimT: number[];
  pl: number[]; // plucks: (cord * 3 + slot) * 5 → a1, a2, a3, t0, tau
  pi: number[];
  pop: number[]; // two hub pops: t0, t1 (t1 = the ball shrinks back)
  popI: number;
  rejT: number;
  wA: number;
  swT: number;
  wB: number;
  nxT: number;
  shownT: number;
  sF: number;
  sTo: number;
  sT0: number;
  sDur: number;
  bonusT: number;
  transT: number;
  trRecap: number;
  trAt: number;
  recapT: number;
  recapOffT: number;
};

export const initEv = (score: number, rm = 0): Ev => {
  'worklet';
  const n3 = (v: number) => [v, v, v];
  return {
    rm,
    held: n3(0),
    hs: n3(0),
    hb: n3(0),
    relT: n3(NEG),
    relS: n3(0),
    relV: n3(0),
    relB: n3(0),
    tapT: n3(NEG),
    tapS0: n3(0),
    haloOn: n3(POS),
    haloOff: n3(NEG),
    pulseT: n3(NEG),
    pulseK: n3(0),
    pend: n3(0),
    snapT: n3(POS),
    snapS: n3(0),
    snapB: n3(0),
    dimT: n3(NEG),
    pl: [0, 0, 0, NEG, 1, 0, 0, 0, NEG, 1, 0, 0, 0, NEG, 1, 0, 0, 0, NEG, 1, 0, 0, 0, NEG, 1, 0, 0, 0, NEG, 1, 0, 0, 0, NEG, 1, 0, 0, 0, NEG, 1, 0, 0, 0, NEG, 1],
    pi: n3(0),
    pop: [NEG, NEG, NEG, NEG],
    popI: 0,
    rejT: NEG,
    wA: 0,
    swT: NEG,
    wB: 0,
    nxT: NEG,
    shownT: NEG,
    sF: score,
    sTo: score,
    sT0: NEG,
    sDur: 0,
    bonusT: NEG,
    transT: NEG,
    trRecap: 0,
    trAt: POS,
    recapT: POS,
    recapOffT: NEG,
  };
};

export const cloneEv = (e: Ev): Ev => {
  'worklet';
  return {
    ...e,
    held: e.held.slice(),
    hs: e.hs.slice(),
    hb: e.hb.slice(),
    relT: e.relT.slice(),
    relS: e.relS.slice(),
    relV: e.relV.slice(),
    relB: e.relB.slice(),
    tapT: e.tapT.slice(),
    tapS0: e.tapS0.slice(),
    haloOn: e.haloOn.slice(),
    haloOff: e.haloOff.slice(),
    pulseT: e.pulseT.slice(),
    pulseK: e.pulseK.slice(),
    pend: e.pend.slice(),
    snapT: e.snapT.slice(),
    snapS: e.snapS.slice(),
    snapB: e.snapB.slice(),
    dimT: e.dimT.slice(),
    pl: e.pl.slice(),
    pi: e.pi.slice(),
    pop: e.pop.slice(),
  };
};

// ---------------------------------------------------------------------------------------------------------------
// Per-cord state at time t

export const sAt = (e: Ev, i: number, t: number) => {
  'worklet';
  if (e.held[i] === 1) return e.hs[i];
  if (e.tapT[i] > NEG / 2 && t >= e.tapT[i]) return mix(e.tapS0[i], 52, easeInOut(seg(t, e.tapT[i], e.tapT[i] + 0.14)));
  const q = t - e.relT[i];
  if (q < 0 || q > 3) return 0;
  const raw = springAt(q, e.relS[i], 0, e.relV[i], SPRING.cord.d, SPRING.cord.k);
  // the cord cannot push: past its rest the bead is caught softly (≈ 18 pt of overshoot from a full pull)
  return raw < 0 ? -18 * Math.tanh(-raw / 18) : raw;
};

export const bendAt = (e: Ev, i: number, t: number) => {
  'worklet';
  if (e.held[i] === 1) return e.hb[i];
  const q = t - e.relT[i];
  if (q < 0 || q > 3) return 0;
  return springAt(q, e.relB[i], 0, 0, SPRING.cord.d, SPRING.cord.k);
};

const haloAt = (e: Ev, i: number, t: number) => {
  'worklet';
  const on = t >= e.haloOn[i] ? easeOut(seg(t, e.haloOn[i], e.haloOn[i] + (e.rm === 1 ? 0.2 : 0.1))) : 0;
  const off = e.haloOff[i] > e.haloOn[i] && t >= e.haloOff[i] ? 1 - easeOut(seg(t, e.haloOff[i], e.haloOff[i] + 0.3)) : 1;
  return on * off;
};

export const haloLevel = haloAt;

// ---------------------------------------------------------------------------------------------------------------
// Writing events (used by the autoplay script and by the finger alike)

export const addPluck = (e: Ev, i: number, a1: number, a2: number, a3: number, t0: number, tau: number) => {
  'worklet';
  const slot = e.pi[i];
  const b = (i * 3 + slot) * 5;
  e.pl[b] = a1;
  e.pl[b + 1] = a2;
  e.pl[b + 2] = a3;
  e.pl[b + 3] = t0;
  e.pl[b + 4] = tau;
  e.pi[i] = (slot + 1) % 3;
};

export const releaseCord = (e: Ev, i: number, t: number, s: number, v: number, b: number) => {
  'worklet';
  e.held[i] = 0;
  e.tapT[i] = NEG;
  e.relT[i] = t;
  e.relS[i] = s;
  e.relV[i] = v;
  e.relB[i] = b;
};

export const firePulse = (e: Ev, i: number, t: number, kind: number) => {
  'worklet';
  e.pulseT[i] = t;
  e.pulseK[i] = kind;
  e.pend[i] = kind === 1 ? 1 : 0;
};

const pushPop = (e: Ev, t0: number, t1: number) => {
  'worklet';
  const s = e.popI;
  e.pop[s * 2] = t0;
  e.pop[s * 2 + 1] = t1;
  e.popI = 1 - s;
};

/** The word on screen at time t. */
export const wordAt = (e: Ev, t: number) => {
  'worklet';
  return t >= e.nxT ? e.wB : e.wA;
};

/**
 * An answer pulse reached the hub at ta. Correct: the ball swallows the word, the score rolls and the next word comes
 * out (or the round ends: `mode` 1 is autoplay's transition, the live round shows the recap). Wrong: the hub rejects
 * it and the cord snaps.
 */
export const arrive = (e: Ev, i: number, ta: number, correct: boolean, sc: Script, mode: number) => {
  'worklet';
  const cur = wordAt(e, ta);
  e.pend[i] = 0;
  const rm = e.rm === 1;
  if (correct) {
    const last = mode === 1 || cur >= sc.n - 1;
    const quick = ta - e.shownT < 4.5;
    e.wA = cur;
    e.swT = ta + (rm ? 0 : 0.07);
    e.sF = e.sTo;
    e.sTo = e.sF + 250 + (quick ? 15 : 0);
    e.sT0 = ta + 0.07;
    e.sDur = rm ? 0 : 0.6;
    if (quick) e.bonusT = ta + 0.17;
    e.haloOff[i] = ta + (rm ? 0.6 : 0.1);
    let t1 = ta + 0.82;
    if (mode === 1) {
      e.trAt = ta + 0.53;
      t1 = e.trAt;
      e.nxT = POS;
    } else if (last) {
      e.recapT = ta + 0.9;
      e.nxT = POS;
    } else {
      e.wB = cur + 1;
      e.nxT = ta + (rm ? 0.2 : 0.82);
      e.shownT = e.nxT;
    }
    if (!rm) pushPop(e, ta, t1);
  } else if (rm) {
    e.dimT[i] = ta;
    e.haloOff[i] = ta + 0.3;
  } else {
    e.rejT = ta;
    const st = ta + 0.05;
    e.snapT[i] = st;
    e.snapS[i] = sAt(e, i, st);
    e.snapB[i] = bendAt(e, i, st);
    e.haloOff[i] = ta;
    for (let j = 0; j < 3; j++) if (j !== i) addPluck(e, j, 3, 0, -0.33, st, 0.25);
  }
};

/** The round transition: the strings retract into the knot, the next word rolls in, the strings shoot back out. */
export const startTransition = (e: Ev, T0: number, sc: Script, fromRecap: number) => {
  'worklet';
  e.transT = T0;
  e.trRecap = fromRecap;
  e.trAt = POS;
  if (e.rm !== 1) {
    // the knot keeps the size it was popped to while it swallows, then lets go
    const li = e.popI ^ 1;
    if (e.pop[li * 2] > NEG / 2 && Math.abs(e.pop[li * 2 + 1] - T0) < 1e-9) e.pop[li * 2 + 1] = T0 + TR.rel;
    else pushPop(e, T0, T0 + TR.rel);
  }
  const cur = wordAt(e, T0);
  e.wA = cur;
  if (!(e.swT >= e.shownT && e.swT <= T0 + 0.01)) e.swT = T0;
  e.wB = 0;
  e.nxT = T0 + TR.nx;
  e.shownT = e.nxT;
  e.sF = e.sTo;
  e.sTo = sc.score;
  e.sT0 = T0 + 0.3;
  e.sDur = 0;
  e.bonusT = NEG;
  for (let i = 0; i < 3; i++) {
    e.held[i] = 0;
    e.tapT[i] = NEG;
    if (e.haloOn[i] < T0 && e.haloOff[i] < e.haloOn[i]) e.haloOff[i] = T0;
  }
};

// ---------------------------------------------------------------------------------------------------------------
// The autoplay script

const pull = (e: Ev, i: number, t: number, a: number, b: number, rel: number, to: number) => {
  'worklet';
  if (t >= a && t < rel) {
    e.held[i] = 1;
    e.hs[i] = to * easeInOut(seg(t, a, b));
  }
  if (t >= rel) {
    releaseCord(e, i, rel, to, 0, 0);
    firePulse(e, i, rel, 1);
  }
};

/** Every event of the loop that has happened by time t (and what each one already scheduled). */
export const autoEventsAt = (t: number, sc: Script): Ev => {
  'worklet';
  const e = initEv(sc.score);
  e.shownT = -1.1; // the first word came out of the previous loop's transition
  const A = sc.cA;
  const B = sc.cB;
  const Wc = sc.cW;
  // 1 — the right answer, pulled and released
  if (t >= 0.5) e.haloOn[A] = 0.5;
  pull(e, A, t, 0.6, 1.5, 1.7, PULL);
  const a1 = 1.7 + LREST[A] / PULSE_SPEED;
  if (t >= a1) arrive(e, A, a1, true, sc, 0);
  // 2 — a wrong pull: the cord snaps
  if (t >= 3.8) {
    e.haloOn[Wc] = 3.8;
    e.haloOff[Wc] = NEG;
  }
  pull(e, Wc, t, 3.9, 4.8, 4.8, PULL);
  const a2 = 4.8 + LREST[Wc] / PULSE_SPEED;
  if (t >= a2) arrive(e, Wc, a2, false, sc, 0);
  // 3 — a sweep across the strings
  if (t >= 6.8) addPluck(e, 0, 7, 2.5, 1, 6.8, 0.9);
  if (t >= 6.95) addPluck(e, 1, 7, 2.5, 1, 6.95, 0.9);
  if (t >= 7.1) addPluck(e, 2, 7, 2.5, 1, 7.1, 0.9);
  // 4 — the second word's answer, then the round transition
  if (t >= 7.9) {
    e.haloOn[B] = 7.9;
    e.haloOff[B] = NEG;
  }
  pull(e, B, t, 8.0, 8.7, 8.7, PULL);
  const a3 = 8.7 + LREST[B] / PULSE_SPEED;
  if (t >= a3) arrive(e, B, a3, true, sc, 1);
  if (t >= e.trAt) startTransition(e, e.trAt, sc, 0);
  return e;
};

// ---------------------------------------------------------------------------------------------------------------
// The frame

export type Frame = {
  t: number;
  e: Ev;
  s: number[];
  bend: number[];
  hx: number;
  hy: number;
  bx: number[];
  by: number[];
  beadSc: number[];
  beadA: number[];
  tip: number; // 1: beads ride the tips of growing cords
  halo: number[];
  labelA: number[];
  labelSc: number[];
  labelDy: number[];
  labelBlur: number[];
  heat: number[];
  heatHot: number[];
  width: number[];
  pulseFrac: number[];
  pulseAmp: number[];
  pulseK: number[];
  draw: number[];
  cordA: number[];
  bodyW: number[];
  ropeTau: number[];
  mode: number; // 0 tree, 6 the strings retract into the knot and shoot out again
  u: number;
  ballR: number;
  arcA: number;
  arcSweep: number;
  arcRot: number;
  hubA: number;
  shockR: number;
  shockA: number;
  fx: number;
  fy: number;
  gain: number;
  cool: number;
  w: number;
  lx: number[];
  ly: number[];
  ls: number[];
  la: number[];
  sF: number;
  sTo: number;
  sT0: number;
  sDur: number;
  bonusA: number;
  hudA: number;
  scoreA: number;
  roll: number; // 1 while the word rolls in under its mask
  recapA: number;
  waveFrom: number;
};

const BIG_POP = 25.5;

export const compose = (e: Ev, t: number, sc: Script): Frame => {
  'worklet';
  const rm = e.rm === 1;
  const s = [0, 0, 0];
  const bend = [0, 0, 0];
  for (let i = 0; i < 3; i++) {
    s[i] = sAt(e, i, t);
    bend[i] = bendAt(e, i, t);
  }
  // the hub gives 6 % of each pull towards it; a rejected answer shakes it
  let hx = G.hubX;
  let hy = G.hubY;
  for (let i = 0; i < 3; i++) {
    const p = Math.max(s[i], 0) * 0.06;
    hx += p * DIRX[i];
    hy += p * DIRY[i];
  }
  if (t >= e.rejT && t < e.rejT + 0.24) {
    const k = (t - e.rejT) / 0.24;
    hx += 4 * Math.sin(2 * Math.PI * 25 * (t - e.rejT)) * (1 - k) * (1 - k);
  }

  const bx = [0, 0, 0];
  const by = [0, 0, 0];
  const beadSc = [1, 1, 1];
  const beadA = [1, 1, 1];
  const halo = [0, 0, 0];
  const labelA = [1, 1, 1];
  const labelSc = [1, 1, 1];
  const labelDy = [0, 0, 0];
  const labelBlur = [0, 0, 0];
  const heat = [0, 0, 0];
  const heatHot = [0, 0, 0];
  const width = [2, 2, 2];
  const pulseFrac = [-1, -1, -1];
  const pulseAmp = [0, 0, 0];
  const pulseK = [0, 0, 0];
  const draw = [1, 1, 1];
  const cordA = [1, 1, 1];
  const bodyW = [8, 8, 8];
  const ropeTau = [-1, -1, -1];

  for (let i = 0; i < 3; i++) {
    let slack = 0;
    for (let j = 0; j < 3; j++) if (j !== i) slack += 0.02 * Math.max(s[j], 0);
    bx[i] = G.bx[i] + bend[i];
    by[i] = G.beadY + s[i] - slack;
    halo[i] = haloAt(e, i, t);
    if (rm && t >= e.dimT[i] && t < e.dimT[i] + 0.7) beadA[i] = 1 - 0.6 * Math.sin(Math.PI * seg(t, e.dimT[i], e.dimT[i] + 0.7));
    // the label follows the bead; on release it follows through on a stiffer spring
    const q = t - e.relT[i];
    if (e.held[i] === 1 || (e.tapT[i] > NEG / 2 && t >= e.tapT[i])) labelDy[i] = 0.85 * s[i];
    else if (q >= 0 && q < 3) labelDy[i] = 0.85 * springAt(q, e.relS[i], 0, e.relV[i], SPRING.snap.d, SPRING.snap.k);
    const sp = Math.max(s[i], 0);
    heat[i] = clamp01(sp / PULL);
    if (rm) heat[i] = Math.max(heat[i], 0.55 * halo[i]);
    // a plucked string flashes towards the heat colour and cools with its amplitude
    for (let slot = 0; slot < 3; slot++) {
      const b = (i * 3 + slot) * 5;
      const q = t - e.pl[b + 3];
      if (q >= 0 && q < 4) heat[i] = Math.max(heat[i], 0.7 * clamp01(Math.abs(e.pl[b]) / 7) * Math.exp(-q / e.pl[b + 4]));
    }
    heatHot[i] = heat[i];
    width[i] = 2; // the core remains readable even at full tension
    // the pulse runs from the bead to the hub
    if (e.pulseK[i] > 0 && t >= e.pulseT[i]) {
      const dist = (t - e.pulseT[i]) * PULSE_SPEED;
      const hr = clamp01(e.relS[i] / PULL);
      if (e.pulseK[i] === 1 && dist < LREST[i]) {
        pulseFrac[i] = dist / LREST[i];
        pulseAmp[i] = seg(dist, 0, 18);
        pulseK[i] = 1;
        heatHot[i] = Math.max(heat[i], hr * (1 - 0.25 * pulseFrac[i]));
      } else if (e.pulseK[i] === 2) {
        const lv = i === 1 ? 0.55 * LREST[1] : VERT;
        if (dist < lv) {
          pulseFrac[i] = dist / LREST[i];
          pulseAmp[i] = 0.6 * (1 - dist / lv) * seg(dist, 0, 18);
          pulseK[i] = 2;
          heatHot[i] = Math.max(heat[i], hr * 0.5 * (1 - dist / lv));
        }
      }
    }
    // a snapped cord: a free rope falls with its bead, then the hub casts a new line
    if (t >= e.snapT[i]) {
      const qs = t - e.snapT[i];
      pulseFrac[i] = -1;
      if (qs < 0.725) {
        ropeTau[i] = qs;
        labelA[i] = 1 - seg(qs, 0, 0.42);
        halo[i] = 0;
      } else {
        draw[i] = easeOut(seg(qs, 0.72, 1.07));
        beadSc[i] = qs < 1.07 ? 0 : Math.max(0, springAt(qs - 1.07, 0, 1, 0, SPRING.pop.d, SPRING.pop.k));
        labelA[i] = 1;
        labelSc[i] = qs < 1.13 ? 0 : Math.max(0, springAt(qs - 1.13, 0, 1, 0, SPRING.pop.d, SPRING.pop.k));
        labelDy[i] = 0;
      }
    }
  }

  // the field's light leans towards the strongest pull and flares with the hub
  let fx = G.fieldX;
  let fy = G.fieldY;
  let gain = 1;
  let best = -1;
  let bs = 0;
  for (let i = 0; i < 3; i++) {
    if (s[i] > bs) {
      bs = s[i];
      best = i;
    }
  }
  if (best >= 0) {
    const k = Math.min(bs / PULL, 1);
    fx += 0.45 * k * (bx[best] - G.fieldX);
    fy += 0.45 * k * (by[best] - G.fieldY);
    gain += 0.35 * k;
  }

  // the hub: dot → ball (pops), arcs
  let ballR = 2.5;
  let arcA = 0;
  let arcSweep = 0;
  let arcRot = 0;
  let latest = NEG;
  let shockR = 0;
  let shockA = 0;
  for (let p = 0; p < 2; p++) {
    const t0 = e.pop[p * 2];
    const t1 = e.pop[p * 2 + 1];
    if (t0 < NEG / 2 || t < t0) continue;
    let g = springAt(t - t0, 0, 1, 0, SPRING.pop.d, SPRING.pop.k);
    if (t >= t1) g *= springAt(t - t1, 1, 0, 0, SPRING.snap.d, SPRING.snap.k);
    ballR += BIG_POP * Math.max(g, 0);
    gain += 0.2 * (1 - easeOut(seg(t, t0, t0 + 0.4)));
    if (t0 > latest) {
      latest = t0;
      arcSweep = easeOut(seg(t, t0, t0 + 0.2));
      arcA = arcSweep * (1 - seg(t, t1, t1 + 0.2));
      arcRot = (t - t0) * Math.PI;
    }
  }
  // One 40 pt shockwave on arrival, including a rejected pulse. No loop residual.
  const arrival = Math.max(latest, e.rejT);
  if (!rm && t >= arrival && t < arrival + 0.32) {
    const q = seg(t, arrival, arrival + 0.32);
    shockR = 40 * easeOut(q);
    shockA = 0.85 * (1 - q) * (1 - q);
  }
  // Subtle ambient life bridges the reading holds; fully at rest at both ends.
  if (!rm) {
    const life = seg(t, 0, 0.25) * (1 - seg(t, 11.55, 11.95));
    gain += 0.025 * Math.sin(2 * Math.PI * t / 3) * life;
    fy += 3 * Math.sin(2 * Math.PI * t / 4) * life;
  }
  let cool = 0;
  if (t >= e.rejT) cool = seg(t, e.rejT, e.rejT + 0.12) * (1 - easeInOut(seg(t, e.rejT + 0.77, e.rejT + 1.27)));
  gain -= 0.15 * cool;

  // the word: letters fall into the ball one by one, the next word rises out of it
  const showB = t >= e.nxT;
  const w = showB ? e.wB : e.wA;
  const n = sc.lens[w] ?? 0;
  const stg = n >= 6 ? 0.06 : 0.07;
  const rollIn = !rm && showB && e.transT > NEG / 2 && e.nxT === e.transT + TR.nx;
  const lx: number[] = [];
  const ly: number[] = [];
  const ls: number[] = [];
  const la: number[] = [];
  for (let k = 0; k < MAXL; k++) {
    let x = 0;
    let y = 0;
    let sc1 = 1;
    let a = k < n ? 1 : 0;
    const cx = sc.cx[w * MAXL + k] ?? G.hubX;
    if (k < n && !showB && t >= e.swT) {
      if (rm) a = 1 - seg(t, e.swT, e.swT + 0.2);
      else {
        const land = e.swT + stg * k;
        const p = easeIn(seg(t, land, land + 0.26));
        y = 38 * p;
        x = (G.hubX - cx) * 0.55 * p;
        sc1 = 1 - 0.7 * p;
        a = 1 - seg(p, 0.45, 1);
        // the ball swells by 2 pt as each letter lands, and springs back
        if (t >= land + 0.26 && t < land + 1.6) ballR += Math.max(0, springAt(t - land - 0.26, 0, 0, 52, SPRING.pop.d, SPRING.pop.k));
      }
    } else if (k < n && showB && t < e.nxT + 2) {
      if (rm) a = seg(t, e.nxT, e.nxT + 0.2);
      else if (rollIn) {
        // under the mask: each letter rolls up from below its edge, no fade
        const tk = t - e.nxT - 0.05 * k;
        const pr = tk <= 0 ? 0 : tk > 0.9 ? 1 : mix(springAt(tk, 0, 1, 0, SPRING.pop.d, SPRING.pop.k), 1, easeOut(seg(tk, 0.6, 0.9)));
        y = TR.roll * (1 - pr);
      } else {
        const tk = t - e.nxT - 0.05 * k;
        const pr = tk <= 0 ? 0 : tk > 0.72 ? 1 : mix(springAt(tk, 0, 1, 0, SPRING.pop.d, SPRING.pop.k), 1, easeOut(seg(tk, 0.5, 0.72)));
        y = (G.hubY - G.wordY) * (1 - pr);
        x = (G.hubX - cx) * (1 - pr);
        sc1 = pr;
        a = tk > 0 ? 1 : 0;
      }
    }
    lx.push(x);
    ly.push(y);
    ls.push(sc1);
    la.push(a);
  }

  const bonusA = t >= e.bonusT ? 0.7 * seg(t, e.bonusT, e.bonusT + 0.2) * (1 - seg(t, e.bonusT + 1.0, e.bonusT + 1.2)) : 0;
  let hudA = 1;
  let recapA = t >= e.recapT ? easeOut(seg(t, e.recapT, e.recapT + 0.3)) : 0;
  if (e.recapOffT > e.recapT && t >= e.recapOffT) recapA *= 1 - seg(t, e.recapOffT, e.recapOffT + 0.25);
  const treeA = 1 - recapA;
  for (let i = 0; i < 3; i++) {
    cordA[i] *= treeA;
    beadA[i] *= treeA;
    labelA[i] *= treeA;
  }

  // the round transition: the strings spring up into the knot, which swallows them; the word rolls in; they shoot out
  let mode = 0;
  let tip = 0;
  let waveFrom = NEG;
  const u = t - e.transT;
  if (u >= 0 && u < TR.end && !rm) {
    mode = 6;
    tip = 1;
    waveFrom = e.transT + TR.out - 0.1;
    const rec = e.trRecap;
    const fin = easeOut(seg(u, TR.settle, TR.end - 0.05));
    for (let i = 0; i < 3; i++) {
      const raw = mix(endAt(u, i, rec), 1, fin);
      // how fast the end is travelling: the rubber is thin and hot while it is stretched, and thickens as it rests
      const v = clamp01(Math.abs(mix(endAt(u + 0.01, i, rec), 1, fin) - mix(endAt(u - 0.01, i, rec), 1, fin)) / 0.02 / 7);
      const over = Math.max(raw - 1, 0) * TR.over;
      const p = Math.min(raw, 1);
      draw[i] = p;
      by[i] += over * LREST[i];
      // the bead and its label ride the end and shrink into the knot, or grow out of it
      beadSc[i] = p <= 0.0005 ? 0 : mix(0.25, 1, easeOut(seg(p, 0.04, 0.35)));
      labelSc[i] = easeOut(seg(p, 0, 0.6));
      labelA[i] = treeA;
      labelDy[i] = 0;
      labelBlur[i] = 0;
      bodyW[i] = mix(8, 3.5, easeOut(v));
      heat[i] = Math.max(heat[i] * (1 - seg(u, 0, 0.2)), 0.55 * v);
      heatHot[i] = heat[i];
      pulseFrac[i] = -1;
      halo[i] *= 1 - seg(u, 0, 0.15);
    }
    // the knot: one squash as the last end goes in, one soft ring, a nudge of light
    ballR *= squashAt(u);
    const qr = seg(u, U_SQ, U_SQ + TR.ring);
    if (u >= U_SQ && qr < 1) {
      shockR = mix(20, 60, easeOut(qr));
      shockA = 0.35 * (1 - qr);
      gain += 0.14 * Math.sin(Math.PI * seg(u, U_SQ, U_SQ + 0.5));
    } else {
      shockR = 0;
      shockA = 0;
    }
  } else if (e.transT > NEG / 2 && u >= TR.end) {
    waveFrom = e.transT + TR.out - 0.1;
  }
  const roll = !rm && showB && e.transT > NEG / 2 && e.nxT === e.transT + TR.nx && t < e.nxT + 1.3 ? 1 : 0;

  return {
    t,
    e,
    s,
    bend,
    hx,
    hy,
    bx,
    by,
    beadSc,
    beadA,
    tip,
    halo,
    labelA,
    labelSc,
    labelDy,
    labelBlur,
    heat,
    heatHot,
    width,
    pulseFrac,
    pulseAmp,
    pulseK,
    draw,
    cordA,
    bodyW,
    ropeTau,
    mode,
    u,
    ballR,
    arcA,
    arcSweep,
    arcRot,
    hubA: treeA,
    shockR,
    shockA,
    fx,
    fy,
    gain,
    cool,
    w,
    lx,
    ly,
    ls,
    la,
    sF: e.sF,
    sTo: e.sTo,
    sT0: e.sT0,
    sDur: e.sDur,
    bonusA: bonusA * hudA,
    hudA,
    scoreA: hudA * (1 - recapA),
    roll,
    recapA,
    waveFrom,
  };
};

/** The autoplay frame at loop time t. */
export const frameAt = (tIn: number, sc: Script): Frame => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  return compose(autoEventsAt(t, sc), t, sc);
};

// ---------------------------------------------------------------------------------------------------------------
// Cord geometry: hub → rounded shoulder → bead, as a polyline with its arc lengths

export type Pts = { x: number[]; y: number[]; s: number[]; total: number };

/** Standing waves on the vertical run (u 0 at the shoulder, 1 at the bead): three modes per pluck. */
const waveAt = (pl: number[], i: number, u: number, t: number, from: number) => {
  'worklet';
  let x = 0;
  if (pl.length < 45) return 0;
  for (let slot = 0; slot < 3; slot++) {
    const b = (i * 3 + slot) * 5;
    const t0 = pl[b + 3];
    if (t0 < from || t < t0) continue;
    const q = t - t0;
    if (q > 6) continue;
    const env = Math.exp(-q / pl[b + 4]);
    const w = 2 * Math.PI * 7 * q;
    x += env * (pl[b] * Math.sin(Math.PI * u) * Math.sin(w) + pl[b + 1] * Math.sin(2 * Math.PI * u) * Math.sin(2 * w) + pl[b + 2] * Math.sin(3 * Math.PI * u) * Math.sin(3 * w));
  }
  return x;
};

export const cordPoints = (i: number, hx: number, hy: number, beadY: number, bend: number, pl: number[], t: number, from: number): Pts => {
  'worklet';
  const xs: number[] = [];
  const ys: number[] = [];
  const vx = G.bx[i];
  let vTop = hy;
  if (i !== 1) {
    const dx = vx - hx;
    const dy = G.shoulderY - hy;
    const len = Math.hypot(dx, dy);
    const R = G.corner;
    const cx0 = vx - (dx / len) * R;
    const cy0 = G.shoulderY - (dy / len) * R;
    const ND = 9;
    for (let k = 0; k < ND; k++) {
      const q = k / ND;
      xs.push(hx + (cx0 - hx) * q);
      ys.push(hy + (cy0 - hy) * q);
    }
    const NC = 6;
    for (let k = 0; k <= NC; k++) {
      const q = k / NC;
      const a = (1 - q) * (1 - q);
      const b = 2 * q * (1 - q);
      const c = q * q;
      xs.push(a * cx0 + b * vx + c * vx);
      ys.push(a * cy0 + b * G.shoulderY + c * (G.shoulderY + R));
    }
    vTop = G.shoulderY + R;
  } else {
    xs.push(hx);
    ys.push(hy);
  }
  const NV = i === 1 ? 68 : 60;
  const vlen = beadY - vTop;
  for (let k = 1; k <= NV; k++) {
    const u = k / NV;
    let x = i === 1 ? hx + (vx - hx) * u : vx;
    x += bend * u * u * u + waveAt(pl, i, u, t, from);
    xs.push(x);
    ys.push(vTop + vlen * u);
  }
  const ss: number[] = [0];
  for (let k = 1; k < xs.length; k++) ss.push(ss[k - 1] + Math.hypot(xs[k] - xs[k - 1], ys[k] - ys[k - 1]));
  return { x: xs, y: ys, s: ss, total: ss[ss.length - 1] };
};

/** Push a gaussian bump (the travelling pulse) into the polyline, along its normal. */
export const addBump = (p: Pts, pos: number, amp: number) => {
  'worklet';
  if (amp <= 0) return;
  const n = p.x.length;
  const nx: number[] = [];
  const ny: number[] = [];
  for (let k = 0; k < n; k++) {
    const a = Math.max(k - 1, 0);
    const b = Math.min(k + 1, n - 1);
    const tx = p.x[b] - p.x[a];
    const ty = p.y[b] - p.y[a];
    const l = Math.hypot(tx, ty) || 1;
    nx.push(-ty / l);
    ny.push(tx / l);
  }
  for (let k = 0; k < n; k++) {
    const d = (p.s[k] - pos) / 12;
    if (d > 4 || d < -4) continue;
    const g = Math.exp(-d * d) * 12 * amp;
    p.x[k] += nx[k] * g;
    p.y[k] += ny[k] * g;
  }
};

/** The point at arc length a. */
export const pointAt = (p: Pts, a: number) => {
  'worklet';
  const n = p.x.length;
  if (a <= 0) return { x: p.x[0], y: p.y[0] };
  for (let k = 1; k < n; k++) {
    if (p.s[k] >= a) {
      const q = (a - p.s[k - 1]) / Math.max(p.s[k] - p.s[k - 1], 1e-6);
      return { x: mix(p.x[k - 1], p.x[k], q), y: mix(p.y[k - 1], p.y[k], q) };
    }
  }
  return { x: p.x[n - 1], y: p.y[n - 1] };
};

// ---------------------------------------------------------------------------------------------------------------
// The snapped cord: a free rope of 12 nodes fixed to its falling bead, simulated once (Verlet, 1/120 s, 4
// constraint passes) from the rest pose so playback is a pure function of time.

export const ROPE_N = 12;
export const ROPE_STEPS = 87;
const GRAV = 2400;

const bakeRope = (i: number) => {
  const p = cordPoints(i, G.hubX, G.hubY, G.beadY, 0, [], 0, POS);
  const px: number[] = [];
  const py: number[] = [];
  for (let k = 0; k < ROPE_N; k++) {
    const q = pointAt(p, (p.total * k) / (ROPE_N - 1));
    px.push(q.x);
    py.push(q.y);
  }
  const rest = p.total / (ROPE_N - 1);
  const dt = 1 / 120;
  // released at the hub, the stretched cord recoils towards its bead: the free end is thrown down the path
  const ox: number[] = [];
  const oy: number[] = [];
  for (let k = 0; k < ROPE_N; k++) {
    const a = Math.max(k - 1, 0);
    const b = Math.min(k + 1, ROPE_N - 1);
    const tx = px[b] - px[a];
    const ty = py[b] - py[a];
    const l = Math.hypot(tx, ty) || 1;
    const v = 760 * (1 - k / (ROPE_N - 1));
    ox.push(px[k] - (tx / l) * v * dt);
    oy.push(py[k] - (ty / l) * v * dt);
  }
  const out: number[] = [];
  const save = () => {
    for (let k = 0; k < ROPE_N; k++) out.push(px[k], py[k]);
  };
  save();
  const bx0 = px[ROPE_N - 1];
  const by0 = py[ROPE_N - 1];
  for (let step = 1; step <= ROPE_STEPS; step++) {
    const tt = step * dt;
    for (let k = 0; k < ROPE_N - 1; k++) {
      const vx = (px[k] - ox[k]) * 0.996;
      const vy = (py[k] - oy[k]) * 0.996;
      ox[k] = px[k];
      oy[k] = py[k];
      px[k] += vx;
      py[k] += vy + GRAV * dt * dt;
    }
    px[ROPE_N - 1] = bx0;
    py[ROPE_N - 1] = by0 + 0.5 * GRAV * tt * tt;
    ox[ROPE_N - 1] = px[ROPE_N - 1];
    oy[ROPE_N - 1] = py[ROPE_N - 1];
    for (let it = 0; it < 4; it++) {
      for (let k = ROPE_N - 2; k >= 0; k--) {
        const dx = px[k + 1] - px[k];
        const dy = py[k + 1] - py[k];
        const d = Math.hypot(dx, dy) || 1;
        const diff = (d - rest) / d;
        if (k + 1 === ROPE_N - 1) {
          px[k] += dx * diff;
          py[k] += dy * diff;
        } else {
          px[k] += dx * diff * 0.5;
          py[k] += dy * diff * 0.5;
          px[k + 1] -= dx * diff * 0.5;
          py[k + 1] -= dy * diff * 0.5;
        }
      }
    }
    save();
  }
  return out;
};

export const ROPE: number[][] = [bakeRope(0), bakeRope(1), bakeRope(2)];

/** The rope's nodes at time q after the snap; the bead's offset at the snap is blended out over 150 ms. */
export const ropeAt = (rope: number[], q: number, s0: number, b0: number) => {
  'worklet';
  const fi = Math.min(Math.max(q * 120, 0), ROPE_STEPS);
  const f0 = Math.floor(fi);
  const f1 = Math.min(f0 + 1, ROPE_STEPS);
  const k = fi - f0;
  const w = 1 - easeOut(seg(q, 0, 0.15));
  const xs: number[] = [];
  const ys: number[] = [];
  for (let n = 0; n < ROPE_N; n++) {
    const a = f0 * ROPE_N * 2 + n * 2;
    const b = f1 * ROPE_N * 2 + n * 2;
    const u = n / (ROPE_N - 1);
    xs.push(mix(rope[a], rope[b], k) + b0 * u * u * u * w);
    ys.push(mix(rope[a + 1], rope[b + 1], k) + s0 * u * w);
  }
  return { x: xs, y: ys };
};
