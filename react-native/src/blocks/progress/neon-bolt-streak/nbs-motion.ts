// Neon Bolt Streak: the autoplay timeline (spec §4 with the Pro addendum) as a pure function of the loop time, and the
// live state a finger drives, built from the same envelopes. Times are in seconds.

export const LOOP = 9.5;

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
export const outQuad = (k: number) => {
  'worklet';
  return 1 - (1 - k) * (1 - k);
};
export const easeIn = (k: number) => {
  'worklet';
  return k * k * k;
};
export const easeInOut = (k: number) => {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
};
/** Ease out with a 6 % overshoot (the count's pop). */
export const backOut = (k: number) => {
  'worklet';
  const c1 = 1.25;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(k - 1, 3) + c1 * Math.pow(k - 1, 2);
};
/** A half-sine bump over [a, a + w]. */
export const bump = (t: number, a: number, w: number) => {
  'worklet';
  if (t <= a || t >= a + w) return 0;
  return Math.sin(((t - a) / w) * Math.PI);
};

/** Everything the screen draws, for one moment. Arrays hold one value per day (Mon … Sun) or per ghost bolt. */
export type Frame = {
  time: number; // clock for the idle life (shimmer, breathing)
  life: number; // idle life amplitude 0..1
  cam: number; // 0 wide, 1 close-up on today
  lit: number[]; // tube gain 0..1.3
  fill: number[]; // discharge front along the outline from the electrodes 0..1.02 (gates the light)
  burn: number[]; // how far the zinc has been consumed 0..1.02 (gates the dead metal)
  head: number[]; // white-hot comet heads and burn edge 0..1
  after: number[]; // amber afterglow 0..1
  pop: number[]; // current wave scale (+0..0.12)
  dy: number[]; // bolt offset (pt): anticipation dip, tremble
  label: number[]; // day label grey → yellow
  ign: number[]; // seconds since that day's strike began, -1 none (lets a finger continue an ignition)
  ignLong: number[]; // 1 when that ignition is the cinematic one
  count: number; // drum position
  countV: number; // drum speed (digits / s)
  flood: number; // 7 / 7 charge of the hero 0..1
  level: number; // hero gradient level 0..1
  flash: number; // hero flash alpha 0..1
  flashR: number; // hero flash radius (pt)
  flashAge: number; // seconds since the dominant flash
  flashS: number; // its strength
  ghost: number[]; // 4 ghost bolt alphas
  ghostTint: number; // white → yellow
  words: number; // `days streak` lift
  border: number; // travelling head along the panel edge 0..1.01 (clockwise from the top-left)
  borderLit: number;
  borderAfter: number;
  bloom: number; // hero bloom on the page
  sDay: number; // strike bolt: target day, -1 hidden
  sDrop: number; // fall 0..1
  sAbsorb: number; // absorbed into the tip 0..1
  sV: number; // fall speed (pt / s) for the streak
  sH: number; // fall height (pt)
  arc: number; // hold-to-charge arc brightness 0..1 (live only)
  jolt: number; // panel jolt (pt)
};

const HERO_REST = (0.86 * 3) / 7;

export const restFrame = (): Frame => {
  'worklet';
  return {
    time: 0,
    life: 0,
    cam: 0,
    lit: [1, 1, 1, 0, 0, 0, 0],
    fill: [1.02, 1.02, 1.02, 0, 0, 0, 0],
    burn: [1.02, 1.02, 1.02, 0, 0, 0, 0],
    head: [0, 0, 0, 0, 0, 0, 0],
    after: [0, 0, 0, 0, 0, 0, 0],
    pop: [0, 0, 0, 0, 0, 0, 0],
    dy: [0, 0, 0, 0, 0, 0, 0],
    label: [1, 1, 1, 0, 0, 0, 0],
    ign: [-1, -1, -1, -1, -1, -1, -1],
    ignLong: [0, 0, 0, 0, 0, 0, 0],
    count: 3,
    countV: 0,
    flood: 0,
    level: HERO_REST,
    flash: 0,
    flashR: 60,
    flashAge: 9,
    flashS: 0,
    ghost: [0.07, 0.07, 0.07, 0.07],
    ghostTint: 0,
    words: 0,
    border: 0,
    borderLit: 0,
    borderAfter: 0,
    bloom: 0,
    sDay: -1,
    sDrop: 0,
    sAbsorb: 0,
    sV: 0,
    sH: 80,
    arc: 0,
    jolt: 0,
  };
};

export type Ign = {
  lit: number;
  fill: number;
  burn: number;
  head: number;
  label: number;
  dy: number;
  drop: number; // -1 not falling
  dropV: number;
  absorb: number;
  jolt: number;
  contact: number; // seconds after the contact, < 0 before
  caught: boolean;
};

/** Contact time and catch time (both from the strike's start) of the two kinds of ignition. */
export const ignTimes = (long: boolean) => {
  'worklet';
  const c = long ? 0.25 : 0.15;
  return { c, kc: c + (long ? 0.24 : 0.08), dur: long ? 0.4 : 0.3 };
};

/**
 * One day's ignition, s seconds after its strike began: the strike bolt falls onto the top tip, the gas stutters at
 * the electrodes (failed strikes, light only), then catches and burns up both sides of the outline; the heads meet at
 * the top in a flare and the tube overshoots to 115 % (+20 % with a full charge). `long` is the close-up version.
 */
export const igniteAt = (s: number, long: boolean, charge: number): Ign => {
  'worklet';
  const { c, kc, dur } = ignTimes(long);
  const o: Ign = { lit: 0, fill: 0, burn: 0, head: 0, label: 0, dy: 0, drop: -1, dropV: 0, absorb: 0, jolt: 0, contact: s - c, caught: false };
  if (s < -0.05) return o;
  o.dy = s < c ? 2 * seg(s, -0.05, 0) : 2 * (1 - easeOut(seg(s, c, c + 0.12)));
  if (s < c) {
    const k = clamp01(s / c);
    o.drop = k * k;
    o.dropV = (2 * k) / c;
    return o;
  }
  if (s < c + 0.08) {
    o.drop = 1;
    o.absorb = (s - c) / 0.08;
  }
  o.jolt = (long ? 1.5 : 0.8) * (1 - easeOut(seg(s, c, c + 0.12)));
  if (s < kc) {
    // failed strikes: [from, to, lit, fill] after the contact
    const a = s - c;
    if (long) {
      if (a < 0.04) {
        o.lit = 0.35;
        o.fill = 0.07;
      } else if (a < 0.09) o.fill = 0.07;
      else if (a < 0.12) {
        o.lit = 0.15;
        o.fill = 0.05;
      } else if (a < 0.16) o.fill = 0.05;
      else if (a < 0.21) {
        o.lit = 0.6;
        o.fill = 0.2;
      } else o.fill = 0.2;
    } else if (a < 0.04) {
      o.lit = 0.35;
      o.fill = 0.08;
    } else o.fill = 0.08;
    o.head = o.lit > 0 ? 1 : 0;
    return o;
  }
  o.caught = true;
  const f = s - kc;
  const f0 = long ? 0.2 : 0.15;
  const k = seg(f, 0, dur);
  o.fill = f0 + (1.02 - f0) * (long ? outQuad(k) : easeOut(k));
  o.burn = o.fill;
  o.head = 1 - seg(f, dur, dur + 0.1);
  const g = f - dur;
  const peak = 1.15 + 0.2 * charge;
  let lit = mix(0.6, 0.9, k);
  if (g >= 0) lit = g < 0.06 ? mix(0.9, peak, easeOut(g / 0.06)) : mix(peak, 1, easeInOut(seg(g, 0.06, 0.32)));
  // the newest tube dips 15 % twice in its first 600 ms
  lit *= 1 - 0.15 * (bump(g, 0.3, 0.07) + bump(g, 0.46, 0.07));
  o.lit = lit;
  o.label = easeOut(seg(f, 0.08, 0.38));
  return o;
};

/** A lit tube switching off: the core dies in 80 ms, amber afterglow cools to grey while the zinc grows back. */
export const resetAt = (s: number, l0: number, f0: number, lab0: number) => {
  'worklet';
  const lit = s < 0.08 ? mix(l0, 0.25, easeIn(s / 0.08)) : 0.25 * (1 - easeOut(seg(s, 0.08, 0.8)));
  const after = s < 0.08 ? s / 0.08 : 1 - easeOut(seg(s, 0.08, 0.8));
  const fill = f0 * (1 - easeInOut(seg(s, 0.08, 0.7)));
  const label = lab0 * (1 - easeInOut(seg(s, 0.08, 0.5)));
  return { lit, after, fill, label };
};

/** A lit day touched again: two failed strikes and a pop, no change of the count. */
export const flickerAt = (s: number) => {
  'worklet';
  if (s < 0) return 1;
  if (s < 0.05) return 0.1;
  if (s < 0.09) return 0.5;
  if (s < 0.14) return 0.08;
  if (s < 0.2) return 0.7;
  return s < 0.26 ? mix(0.7, 1.15, easeOut(seg(s, 0.2, 0.26))) : mix(1.15, 1, easeInOut(seg(s, 0.26, 0.5)));
};

/** The hero's yellow flash: alpha and radius s seconds after it began. */
export const flashAt = (s: number, str: number) => {
  'worklet';
  if (s < 0 || s > 0.6) return { a: 0, r: 60 };
  const k = s / 0.6;
  return { a: str * (1 - k) * (1 - k), r: 60 + 200 * easeOut(k) };
};

const ghostEnv = (s: number) => {
  'worklet';
  if (s < 0) return 0.07;
  if (s < 0.08) return mix(0.07, 0.3, s / 0.08);
  return mix(0.3, 0.12, easeOut(seg(s, 0.08, 0.48)));
};

/** 7 / 7 closes the circuit, s seconds after it began: wave, border, ghosts outward, hero flood, second wave. */
export const circuitAt = (s: number) => {
  'worklet';
  const litAdd = [0, 0, 0, 0, 0, 0, 0];
  const pop = [0, 0, 0, 0, 0, 0, 0];
  for (let k = 0; k < 7; k++) {
    const b1 = bump(s, 0.1 * k, 0.25);
    const b2 = bump(s, 1.1 + 0.06 * k, 0.22);
    litAdd[k] = 0.3 * b1 + 0.18 * b2;
    pop[k] = 0.12 * b1 + 0.07 * b2;
  }
  const border = s < 0.1 ? 0 : 1.01 * easeInOut(seg(s, 0.1, 1.1));
  const inner = ghostEnv(s - 1.0);
  const outer = ghostEnv(s - 1.15);
  const flood = easeInOut(seg(s, 1.05, 1.65));
  return { litAdd, pop, border, borderLit: s >= 0.1 ? 1 : 0, ghost: [outer, inner, inner, outer], flood, closed: s >= 1.1 };
};

// ---- the autoplay schedule ----
/** [day, strike start, cinematic] */
export const STRIKES: [number, number, number][] = [
  [3, 1.15, 1],
  [4, 3.6, 0],
  [5, 3.75, 0],
  [6, 3.9, 0],
];
export const CIRCUIT = 4.55;
export const RESET = 7.4;

const countAt = (t: number) => {
  'worklet';
  let p = 3 + backOut(seg(t, 2.75, 3.05));
  for (let j = 1; j < 4; j++) {
    const kc = STRIKES[j][1] + ignTimes(false).kc;
    p += backOut(seg(t, kc + 0.05, kc + 0.35));
  }
  return p - 4 * easeInOut(seg(t, RESET + 0.1, RESET + 0.7));
};

export const frameAt = (t: number): Frame => {
  'worklet';
  const f = restFrame();
  f.time = t;
  f.life = seg(t, 0, 0.4) * (1 - seg(t, LOOP - 0.4, LOOP));
  f.cam = t < 2.25 ? easeInOut(seg(t, 0.3, 1.0)) : 1 - easeInOut(seg(t, 2.25, 2.85));

  // ignitions, then the reset of Thu … Sun
  for (let j = 0; j < STRIKES.length; j++) {
    const d = STRIKES[j][0];
    const long = STRIKES[j][2] === 1;
    const s = t - STRIKES[j][1];
    if (t < RESET) {
      const g = igniteAt(s, long, 0);
      f.lit[d] = g.lit;
      f.fill[d] = g.fill;
      f.burn[d] = g.burn;
      f.head[d] = g.head;
      f.label[d] = g.label;
      f.dy[d] = g.dy;
      f.jolt = Math.max(f.jolt, g.jolt);
      if (s >= -0.05) {
        f.ign[d] = s;
        f.ignLong[d] = long ? 1 : 0;
      }
      if (g.drop >= 0 && g.absorb < 1) {
        f.sDay = d;
        f.sDrop = g.drop;
        f.sAbsorb = g.absorb;
        f.sV = g.dropV * (long ? 170 : 60);
        f.sH = long ? 170 : 60;
      }
    } else {
      const r = resetAt(t - RESET, 1, 1.02, 1);
      f.lit[d] = r.lit;
      f.after[d] = r.after;
      f.fill[d] = r.fill;
      f.burn[d] = r.fill;
      f.label[d] = r.label;
    }
  }

  // the count's drum
  f.count = countAt(t);
  f.countV = (countAt(t + 0.01) - countAt(t - 0.01)) / 0.02;
  f.flood = 0;

  // hero flashes: the strongest wins, they never stack
  const fl: [number, number][] = [
    [2.75, 1],
    [STRIKES[1][1] + ignTimes(false).kc + 0.05, 0.3],
    [STRIKES[2][1] + ignTimes(false).kc + 0.05, 0.3],
    [STRIKES[3][1] + ignTimes(false).kc + 0.05, 1],
  ];
  for (let j = 0; j < fl.length; j++) {
    const e = flashAt(t - fl[j][0], fl[j][1]);
    if (e.a > f.flash) {
      f.flash = e.a;
      f.flashR = e.r;
      f.flashAge = t - fl[j][0];
      f.flashS = fl[j][1];
    }
  }
  f.words = f.flash;
  for (let g = 0; g < 4; g++) f.ghost[g] = 0.07 + 0.05 * f.flash;
  f.ghostTint = f.flash;

  // 7 / 7
  if (t >= CIRCUIT && t < RESET) {
    const c = circuitAt(t - CIRCUIT);
    for (let k = 0; k < 7; k++) {
      f.lit[k] += c.litAdd[k];
      f.pop[k] = c.pop[k];
    }
    f.border = c.border;
    f.borderLit = c.borderLit;
    for (let g = 0; g < 4; g++) f.ghost[g] = Math.max(f.ghost[g], c.ghost[g]);
    f.flood = c.flood;
    f.ghostTint = Math.max(f.ghostTint, seg(t - CIRCUIT, 0.85, 1.05));
  } else if (t >= RESET) {
    const s = t - RESET;
    const r = resetAt(s, 1, 1, 0);
    f.border = 1.01;
    f.borderLit = r.lit;
    f.borderAfter = r.after;
    f.flood = 1 - easeInOut(seg(s, 0.05, 0.75));
    for (let g = 0; g < 4; g++) f.ghost[g] = mix(0.12, 0.07, easeInOut(seg(s, 0.1, 0.7)));
    f.ghostTint = f.flood;
    if (s > 0.8) {
      f.border = 0;
      f.borderLit = 0;
      f.borderAfter = 0;
    }
  }
  f.level = (0.86 * Math.min(Math.max(f.count, 0), 7)) / 7 + 0.14 * f.flood;
  f.bloom = 0.35 * f.flood;
  return f;
};

// ---- live (a finger has taken over) ----

/** Day events: 0 none (snapshot), 1 strike, 5 cinematic strike (continued from autoplay), 2 flicker, 3 complete, 4 reset. */
export type Live = {
  t0: number; // takeover (now clock)
  time0: number; // idle-life clock at the takeover
  snap: Frame;
  kind: number[];
  ev: number[];
  chg: number[];
  l0: number[];
  f0: number[];
  lab0: number[];
  cFrom: number;
  cTo: number;
  cT: number;
  flT: number;
  flS: number;
  circT: number; // -1 none
  circR: number; // circuit reset start, -1 none
  bl0: number;
  flood0: number;
  restT: number; // -1 none
  chargeT: number; // -1 none
  chargeDay: number;
  cancelT: number; // -1 none
};

export const makeLive = (fr: Frame, t: number, n: number): Live => {
  'worklet';
  const L: Live = {
    t0: n,
    time0: fr.time,
    snap: fr,
    kind: [0, 0, 0, 0, 0, 0, 0],
    ev: [0, 0, 0, 0, 0, 0, 0],
    chg: [0, 0, 0, 0, 0, 0, 0],
    l0: [0, 0, 0, 0, 0, 0, 0],
    f0: [0, 0, 0, 0, 0, 0, 0],
    lab0: [0, 0, 0, 0, 0, 0, 0],
    cFrom: fr.count,
    cTo: fr.count,
    cT: n - 10,
    flT: n - fr.flashAge,
    flS: fr.flashS,
    circT: -1,
    circR: -1,
    bl0: 1,
    flood0: 1,
    restT: -1,
    chargeT: -1,
    chargeDay: -1,
    cancelT: -1,
  };
  for (let j = 0; j < STRIKES.length; j++) {
    const d = STRIKES[j][0];
    const s = t - STRIKES[j][1];
    if (t < RESET && s >= -0.05 && s < 1.6) {
      L.kind[d] = STRIKES[j][2] === 1 ? 5 : 1;
      L.ev[d] = n - s;
    } else if (t >= RESET && t - RESET < 0.85) {
      L.kind[d] = 4;
      L.ev[d] = n - (t - RESET);
      L.l0[d] = 1;
      L.f0[d] = 1.02;
      L.lab0[d] = 1;
    }
  }
  if (t >= CIRCUIT && t < RESET) L.circT = n - (t - CIRCUIT);
  if (t >= RESET && t - RESET < 0.85) L.circR = n - (t - RESET);
  return L;
};

/** Whether a day counts towards the streak in the live state. */
export const caughtAt = (L: Live, k: number, n: number) => {
  'worklet';
  const kd = L.kind[k];
  if (kd === 4) return false;
  if (kd === 1 || kd === 5) return n - L.ev[k] >= ignTimes(kd === 5).kc;
  if (kd === 2 || kd === 3) return true;
  return L.snap.burn[k] > 0.5 && L.snap.after[k] === 0;
};

export const liveFrameAt = (L: Live, n: number, reduced: boolean): Frame => {
  'worklet';
  const sn = L.snap;
  const f = restFrame();
  const s0 = n - L.t0;
  f.time = L.time0 + s0;
  const lifeTo = reduced ? 0 : L.restT >= 0 ? 1 - seg(n, L.restT, L.restT + 0.6) : 1;
  f.life = mix(sn.life, lifeTo, seg(s0, 0, 0.4));
  f.cam = sn.cam * (1 - easeInOut(seg(s0, 0, 0.6)));
  for (let k = 0; k < 7; k++) {
    const kd = L.kind[k];
    const s = n - L.ev[k];
    f.lit[k] = sn.lit[k];
    f.fill[k] = sn.fill[k];
    f.burn[k] = sn.burn[k];
    f.after[k] = sn.after[k];
    f.label[k] = sn.label[k];
    f.head[k] = 0;
    f.dy[k] = sn.dy[k] * (1 - seg(s0, 0, 0.15));
    // a snapshot taken during the current wave already holds its pulse; a settled lit tube is 1
    if (kd === 0 && sn.burn[k] > 1 && sn.after[k] === 0) f.lit[k] = 1;
    if (kd === 1 || kd === 5) {
      const g = igniteAt(s, kd === 5, L.chg[k]);
      f.lit[k] = g.lit;
      f.fill[k] = g.fill;
      f.burn[k] = g.burn;
      f.head[k] = g.head;
      f.label[k] = g.label;
      f.dy[k] = g.dy;
      f.after[k] = 0;
      f.jolt = Math.max(f.jolt, g.jolt);
      if (g.drop >= 0 && g.absorb < 1) {
        f.sDay = k;
        f.sDrop = g.drop;
        f.sAbsorb = g.absorb;
        const h = kd === 5 ? 170 : L.chg[k] > 0 ? 40 : 60;
        f.sH = h;
        f.sV = g.dropV * h;
      }
    } else if (kd === 2) {
      f.lit[k] = flickerAt(s);
    } else if (kd === 3) {
      const k3 = seg(s, 0, reduced ? 0.2 : 0.08);
      f.fill[k] = mix(L.f0[k], 1.02, k3);
      f.burn[k] = f.fill[k];
      f.head[k] = reduced ? 0 : 1 - seg(s, 0.08, 0.18);
      f.after[k] = 0;
      f.lit[k] = reduced ? 1 : s < 0.08 ? mix(Math.max(L.l0[k], 0.6), 1.15, k3) : mix(1.15, 1, easeInOut(seg(s, 0.08, 0.33)));
      f.label[k] = mix(L.lab0[k], 1, k3);
    } else if (kd === 4) {
      const r = resetAt(s, L.l0[k], L.f0[k], L.lab0[k]);
      f.lit[k] = r.lit;
      f.after[k] = r.after;
      f.fill[k] = r.fill;
      f.burn[k] = r.fill;
      f.label[k] = r.label;
    }
  }

  // hold to charge: the strike bolt hovers 40 pt above today's tip, an arc joins them and the dead bolt trembles
  if (L.chargeT >= 0 && L.chargeDay >= 0) {
    const hold = n - L.chargeT;
    if (hold > 0.12) {
      const charge = Math.min(hold / 1.2, 1);
      f.sDay = L.chargeDay;
      f.sDrop = 0;
      f.sH = 40;
      f.sAbsorb = 0;
      f.arc = 0.3 + 0.7 * charge;
      f.dy[L.chargeDay] += 0.6 * charge * Math.sin(n * 71);
    }
  } else if (L.cancelT >= 0 && n - L.cancelT < 0.15 && L.chargeDay >= 0 && f.sDay < 0) {
    // cancelled: the arc snaps and the strike bolt is drawn back up into nothing
    f.sDay = L.chargeDay;
    f.sDrop = 0;
    f.sH = 40;
    f.sAbsorb = (n - L.cancelT) / 0.15;
    f.arc = 0;
  }

  // the count's drum: up with a pop, down with a detent
  const kc = seg(n, L.cT, L.cT + (L.cTo >= L.cFrom ? 0.3 : 0.6));
  const ease = (x: number) => {
    'worklet';
    return L.cTo >= L.cFrom ? backOut(x) : easeInOut(x);
  };
  f.count = reduced ? L.cTo : L.cFrom + (L.cTo - L.cFrom) * ease(kc);
  const k2 = seg(n + 0.01, L.cT, L.cT + (L.cTo >= L.cFrom ? 0.3 : 0.6));
  f.countV = reduced ? 0 : ((L.cTo - L.cFrom) * (ease(k2) - ease(kc))) / 0.01;

  const fe = flashAt(n - L.flT, L.flS);
  f.flash = reduced ? 0 : fe.a;
  f.flashR = fe.r;
  f.flashAge = n - L.flT;
  f.flashS = L.flS;
  f.words = f.flash;
  for (let g = 0; g < 4; g++) f.ghost[g] = 0.07 + 0.05 * f.flash;
  f.ghostTint = f.flash;

  // the circuit: running, reset, or as the snapshot left it
  if (L.circR >= 0) {
    const s = n - L.circR;
    const r = resetAt(s, L.bl0, 1, 0);
    f.border = s > 0.8 ? 0 : 1.01;
    f.borderLit = s > 0.8 ? 0 : r.lit;
    f.borderAfter = s > 0.8 ? 0 : r.after;
    f.flood = L.flood0 * (1 - easeInOut(seg(s, 0.05, 0.75)));
    for (let g = 0; g < 4; g++) f.ghost[g] = Math.max(f.ghost[g], mix(0.07 + 0.05 * L.flood0, 0.07, easeInOut(seg(s, 0.1, 0.7))));
    f.ghostTint = Math.max(f.ghostTint, f.flood);
  } else if (L.circT >= 0) {
    const c = circuitAt(n - L.circT);
    if (reduced) {
      f.border = 1.01;
      f.borderLit = seg(n - L.circT, 0, 0.2);
      f.flood = f.borderLit;
    } else {
      for (let k = 0; k < 7; k++) {
        f.lit[k] += c.litAdd[k];
        f.pop[k] = c.pop[k];
      }
      f.border = c.border;
      f.borderLit = c.borderLit;
      for (let g = 0; g < 4; g++) f.ghost[g] = Math.max(f.ghost[g], c.ghost[g]);
      f.flood = c.flood;
      f.ghostTint = Math.max(f.ghostTint, seg(n - L.circT, 0.85, 1.05));
    }
    f.ghostTint = Math.max(f.ghostTint, f.flood);
  } else {
    f.border = sn.border;
    f.borderLit = sn.borderLit;
    f.borderAfter = sn.borderAfter;
    f.flood = sn.flood;
  }
  f.level = (0.86 * Math.min(Math.max(f.count, 0), 7)) / 7 + 0.14 * f.flood;
  f.bloom = 0.35 * f.flood;
  return f;
};
