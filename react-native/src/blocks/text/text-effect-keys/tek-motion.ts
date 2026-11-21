// Text Effect Keys: the eight per-glyph effects and the autoplay timeline (creative spec §4 with the Pro addendum),
// as pure functions of the loop time, so every frame is reproducible and the three stacks can match it frame for
// frame. Times are in seconds, lengths in points on the 402 × 874 canvas.

import { EF, G, SPRING, type Spring, TYPE } from './tek-theme';

export const LOOP = 8;
/** Every key label runs its own effect on this cycle: four per loop, at rest at t = 0. */
export const CYCLE = 2;
/** Gravity of the thrown glyphs (pt/s²) and their restitution on a key top. */
export const GRAV = 2200;
export const REST = 0.3;

// ---------------------------------------------------------------------------------------------------------------
// helpers

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
export const easeInOut = (k: number) => {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2;
};
export const smooth = (a: number, b: number, x: number) => {
  'worklet';
  const k = seg(x, a, b);
  return k * k * (3 - 2 * k);
};

/**
 * A damped spring (mass 1) released at time 0 from `from` towards `to` with initial velocity v0 (units/s): its
 * position at time t. Closed form, so the loop stays a pure function of time.
 */
export const springAt = (t: number, from: number, to: number, v0: number, sp: Spring) => {
  'worklet';
  if (t <= 0) return from;
  const x0 = from - to;
  const w0 = Math.sqrt(sp.k);
  const z = sp.d / (2 * w0);
  if (z < 1) {
    const wd = w0 * Math.sqrt(1 - z * z);
    const e = Math.exp(-z * w0 * t);
    return to + e * (x0 * Math.cos(wd * t) + ((v0 + z * w0 * x0) / wd) * Math.sin(wd * t));
  }
  const e = Math.exp(-w0 * t);
  return to + e * (x0 + (v0 + w0 * x0) * t);
};

/** The effects' shared envelope: identity for p < 0.05 and p > 0.95. */
const env = (p: number) => {
  'worklet';
  return smooth(0.05, 0.15, p) * (1 - smooth(0.8, 0.95, p));
};
const hash = (i: number) => {
  'worklet';
  const s = Math.sin(i * 127.1 + 311.7) * 43758.5453;
  return s - Math.floor(s);
};

/** Explode's opacity: the letters fade as they fly off (gone by p 0.46) and are back as they return. */
export const explodeAlpha = (p: number) => {
  'worklet';
  return p < 0.8 ? 1 - smooth(0.26, 0.46, p) : smooth(0.8, 0.9, p);
};

// ---------------------------------------------------------------------------------------------------------------
// the eight effects

/** One glyph's offset from rest: translate (pt), rotate (deg), scale delta, faux weight (pt), blur σ (pt). */
export type Pose = { dx: number; dy: number; rot: number; sc: number; w: number; blur: number };

/**
 * Effect `e` at phase p (identity at p = 0 and 1) for glyph i of n. `u` scales the translations (1 = the 15 pt the
 * reference was measured at); `staged` gives the word lifted into the thread its larger Bloom.
 */
export const effectPose = (e: number, p: number, i: number, n: number, u: number, staged: boolean): Pose => {
  'worklet';
  const f = n > 1 ? i / (n - 1) : 0;
  const en = env(p);
  if (e === EF.big) {
    return { dx: 0, dy: 0, rot: 0, sc: 0.75 * smooth(0.08, 0.3, p) * (1 - smooth(0.72, 0.9, p)), w: 0, blur: 0 };
  }
  if (e === EF.small) {
    const q = p - 0.07;
    return { dx: 0, dy: 0, rot: 0, sc: -0.35 * smooth(0.08, 0.3, q) * (1 - smooth(0.72, 0.88, q)), w: 0, blur: 0 };
  }
  if (e === EF.shake) {
    const s = Math.sin(2 * Math.PI * 3 * p) * en;
    return { dx: 5 * u * s, dy: 0, rot: 3 * s, sc: 0, w: 0.25 * u * Math.abs(s), blur: 0 };
  }
  if (e === EF.nod) {
    return { dx: 0, dy: 4 * u * Math.sin(2 * Math.PI * 3 * p + Math.PI / 2) * en, rot: 0, sc: 0, w: 0, blur: 0 };
  }
  if (e === EF.ripple) {
    // two crests run left → right; a letter rises on the crest and leans into it
    let dy = 0;
    let rot = 0;
    for (let j = 0; j < 2; j++) {
      const q = (p - 0.08 - 0.43 * j) / 0.4;
      if (q < -0.3 || q > 1.3) continue;
      const z = (q * 1.5 - 0.25 - f) / 0.2;
      const b = Math.exp(-z * z);
      dy -= 6 * u * b;
      rot += (12 * z * b) / 0.43;
    }
    return { dx: 0, dy: dy * en, rot: rot * en, sc: 0, w: 0, blur: 0 };
  }
  if (e === EF.explode) {
    // out on an upward fan (p 0.20–0.44), gone from the key until p 0.80, back by p 0.96 with a pop
    const out = easeOut(seg(p, 0.2, 0.44));
    const back = seg(p, 0.8, 0.96);
    const k = p < 0.8 ? out : (1 - back) * (1 - back) * (1 - back) - 0.08 * Math.sin(Math.PI * back) * (1 - back);
    const a = ((-150 + 120 * f) * Math.PI) / 180;
    const R = 60 * u * k;
    const spin = (hash(i) > 0.5 ? 1 : -1) * (40 + 25 * hash(i + 9));
    const moving = p < 0.8 ? Math.sin(Math.PI * seg(p, 0.2, 0.5)) : Math.sin(Math.PI * back);
    return { dx: Math.cos(a) * R, dy: Math.sin(a) * R, rot: spin * k, sc: 0, w: 0, blur: 3 * moving };
  }
  if (e === EF.bloom) {
    const c = 0.18 + 0.64 * f;
    const z = (p - c) / 0.12;
    const b = Math.exp(-z * z) * smooth(0, 0.08, p) * (1 - smooth(0.92, 1, p));
    return { dx: 0, dy: 0, rot: 0, sc: (staged ? 0.45 : 0.22) * b, w: (staged ? 3.5 : 0.9 * u) * b, blur: 0 };
  }
  // jitter: noise on a circle (whole cycles per phase), so it closes on itself
  const tau = 2 * Math.PI * p;
  const nx = 0.6 * Math.sin(7 * tau + 1.3 * i) + 0.4 * Math.sin(11 * tau + 2.1 * i + 0.7);
  const ny = 0.6 * Math.sin(9 * tau + 0.7 * i + 2.0) + 0.4 * Math.sin(13 * tau + 1.9 * i);
  const nr = 0.6 * Math.sin(8 * tau + 2.7 * i + 1.1) + 0.4 * Math.sin(12 * tau + 0.9 * i);
  return { dx: 1.1 * u * nx * en, dy: 1.1 * u * ny * en, rot: 2.5 * nr * en, sc: 0, w: 0, blur: 0 };
};

// ---------------------------------------------------------------------------------------------------------------
// geometry of the word (pen positions come from the shaped run at runtime; the defaults are Geist's advances)

export type Geo = {
  n: number;
  /** glyph centre x in the composer field / in the new bubble / on the stage (pt) */
  fieldX: number[];
  bubbleX: number[];
  stageX: number[];
  /** advance widths (15 pt, Medium) and ink heights (15 pt) */
  w: number[];
  h: number[];
  /** the new bubble's box */
  bubbleL: number;
  bubbleW: number;
  /** spans of the bubbles a staged crest can press (x0, x1, y top before the rise) */
  spans: number[][];
};

export const FIELD_BASE = G.field.y + G.field.h / 2 + (0.71 * 15) / 2;
export const BUBBLE_BASE = G.oldY + G.bubbleH / 2 + (0.71 * 15) / 2;

const MED = [14.52, 8.87, 8.47, 8.47, 8.47, 8.47, 8.47, 6.15];
const REG = [14.17, 8.71, 8.27, 8.27, 8.27, 8.27, 8.27, 5.88];

export const makeGeo = (fieldAdv: number[], bubbleAdv: number[], inkH: number[], spans: number[][]): Geo => {
  const n = bubbleAdv.length;
  const centres = (adv: number[], x0: number, k: number) => {
    const out: number[] = [];
    let pen = x0;
    for (let i = 0; i < adv.length; i++) {
      out.push(pen + (adv[i] * k) / 2);
      pen += adv[i] * k;
    }
    return out;
  };
  const wB = bubbleAdv.reduce((a, b) => a + b, 0);
  const bubbleW = wB + 2 * G.bubblePad;
  const bubbleL = G.bubbleRight - bubbleW;
  return {
    n,
    fieldX: centres(fieldAdv, G.fieldTextX, 1),
    bubbleX: centres(bubbleAdv, bubbleL + G.bubblePad, 1),
    stageX: centres(bubbleAdv, 201 - (wB * TYPE.stage) / 2, TYPE.stage),
    w: bubbleAdv,
    h: inkH,
    bubbleL,
    bubbleW,
    spans,
  };
};

export const DEFAULT_GEO = makeGeo(REG, MED, [10.65, 10.65, 8.16, 8.16, 8.16, 8.16, 8.16, 9.86], [
  [16, 230, 300],
  [16, 120, 340],
  [282, 384, 443],
]);

// ---------------------------------------------------------------------------------------------------------------
// the throw: each letter's arc is designed by its apex and the key it lands on (Pro addendum (b), (c)1)

/**
 * apex centre x, apex baseline y, key it lands on, lean (deg), whole turns, landing centre x, extra launch delay (s).
 * The apexes spell the word as an arch; each letter then drifts on the way down so it lands beside its key's label,
 * not on it (`W` drifts inward, clear of the + button). The `t` leaves last, a beat late, so the poster shows it
 * coming out of its bubble.
 */
const DESIGN: [number, number, number, number, number, number, number][] = [
  [28, 360, EF.bloom, -26, 0, 165, 0],
  [98, 250, EF.big, 18, 0, 62, 0],
  [146, 180, EF.shake, 0, 1, 154, 0],
  [188, 146, EF.explode, -12, 0, 170, 0],
  [228, 142, EF.small, 14, 0, 234, 0],
  [270, 170, EF.nod, 0, -1, 256, 0],
  [318, 236, EF.ripple, -20, 0, 342, 0],
  [372, 330, EF.jitter, 28, 0, 366, 0.2],
];

/** A landed letter stands on its keycap's face: baseline this far below the key's top edge (pt). */
export const LAND = 37;

/** the sideways drag of a thrown letter: its time constant is its time to apex / XK, so it has covered 92 % of
 * its sideways travel at the apex and drifts the rest on the way down (never past its landing x) */
export const XK = 2.53;
export const BURST = 0.9;
export const LAUNCH_STAGGER = 0.03;

export type Throw = {
  xa: number;
  ya: number;
  xl: number;
  key: number;
  lean: number;
  turns: number;
  vy0: number;
  ta: number;
  tl: number;
  top: number;
  vb: number;
  tb: number;
  launch: number;
  hit: number;
};

export const keyRect = (k: number) => {
  'worklet';
  return { x: G.keyX[k % 2], y: G.keyY[Math.floor(k / 2)] };
};

export const THROWS: Throw[] = DESIGN.map(([xa, ya, key, lean, turns, xl, late], i) => {
  const d = BUBBLE_BASE - ya;
  const vy0 = Math.sqrt(2 * GRAV * d);
  const ta = vy0 / GRAV;
  const top = G.keyY[Math.floor(key / 2)] + LAND;
  const tl = ta + Math.sqrt((2 * (top - ya)) / GRAV);
  const vb = REST * GRAV * (tl - ta);
  const tb = (2 * vb) / GRAV;
  const launch = BURST + LAUNCH_STAGGER * i + late;
  return { xa, ya, xl, key, lean, turns, vy0, ta, tl, top, vb, tb, launch, hit: launch + tl };
});

export const RESEAT = 2.46;
export const RESEAT_STAGGER = 0.035;

// ---------------------------------------------------------------------------------------------------------------
// keys: presses and hits of the autoplay

/** [key, down, up] */
const PRESSES: [number, number, number][] = [
  [EF.explode, 0.82, 1.65],
  [EF.ripple, 3.17, 3.32],
  [EF.bloom, 4.6, 6.0],
];
/** [key, time, strength]: each landing letter sinks its key; the second, smaller bounce taps it again */
const HITS: [number, number, number][] = THROWS.flatMap((h) => [
  [h.key, h.hit, 1] as [number, number, number],
  [h.key, h.hit + h.tb, 0.45] as [number, number, number],
]);
/** [key, from, to]: the active key's label is lime */
const ACTIVE: [number, number, number][] = [
  [EF.explode, 0.84, 3.17],
  [EF.ripple, 3.17, 4.6],
  [EF.bloom, 4.6, 6.72],
];

const pressDepth = (t: number, a: number, b: number) => {
  'worklet';
  if (t < a) return 0;
  if (t < a + 0.06) return easeOut(seg(t, a, a + 0.06));
  if (t < b) return 1;
  return Math.max(0, springAt(t - b, 1, 0, 0, SPRING.snap));
};

// ---------------------------------------------------------------------------------------------------------------
// the frame

export type Hero = {
  x: number; // centre of the advance
  y: number; // baseline
  rot: number; // deg, about the ink centre
  s: number; // scale of the 15 pt glyph
  lime: number; // 0 its home ink, 1 lime
  home: number; // home ink: 0 the field's light ink, 1 the bubble's dark ink
  font: number; // 0 Regular (field), 1 Medium (bubble and beyond)
  w: number; // faux weight (pt, at its drawn size)
  vx: number;
  vy: number;
  blur: number; // extra σ from an effect
  mb: number; // 1 while thrown (velocity blur on), 0 at home and on the stage
};

export type Frame = {
  rise: number; // thread rise 0..1 (× 44 pt)
  body: number; // the new bubble: 0 a dot in the send disc, 1 full
  squash: number;
  blip: number;
  shell: number; // the new bubble's fill alpha
  sendS: number;
  arrowY: number;
  placeholder: number; // mask reveal of `Message`
  select: number; // mask reveal of the selection box
  hero: Hero[];
  depth: number[]; // key press 0..1 (× 4 pt)
  kick: number[]; // phase of a key's kick, -1 when none
  active: number[]; // 0..1 lime label
  amp: number; // key label amplitude
  keyP: number; // key label cycle phase
  dips: number[]; // the bubbles under the crest (pt)
  still?: number; // reduced motion: each key label holds one pose of its effect
};

const flight = (h: Throw, tau: number, sx: number) => {
  'worklet';
  const x = sx + (h.xa - sx) * (1 - Math.exp((-tau * XK) / h.ta)) + (h.xl - h.xa) * smooth(h.ta + 0.2 * (h.tl - h.ta), h.tl, tau);
  let y: number;
  let rot: number;
  if (tau < h.tl) {
    y = BUBBLE_BASE - h.vy0 * tau + 0.5 * GRAV * tau * tau;
    const u = tau / h.tl;
    rot = h.lean * Math.sin(Math.PI * u) + h.turns * 360 * easeInOut(seg(u, 0.4, 1));
  } else {
    const tp = tau - h.tl;
    y = tp < h.tb ? h.top - h.vb * tp + 0.5 * GRAV * tp * tp : h.top;
    // the impact tips the letter, and it rocks back upright
    rot = h.turns * 360 + (h.lean >= 0 ? 1 : -1) * 9 * Math.exp(-7 * tp) * Math.sin(16 * tp);
  }
  const s = 1 + (TYPE.hero - 1) * easeOut(seg(tau, 0, 0.16));
  return { x, y, rot, s };
};

/** Bloom's phase while the key is held: forward, back, forward (4.66 → 6.0 s). */
export const bloomScrub = (t: number) => {
  'worklet';
  if (t < 4.66) return 0;
  if (t < 5.2) return 0.62 * easeInOut(seg(t, 4.66, 5.2));
  if (t < 5.6) return mix(0.62, 0.3, easeInOut(seg(t, 5.2, 5.6)));
  if (t < 6.02) return mix(0.3, 1, easeInOut(seg(t, 5.6, 6.02)));
  return 1;
};

/** Letter i at loop time t, without its velocity. */
const heroAt = (t: number, i: number, geo: Geo, depth: number[]): Hero => {
  'worklet';
  const hero: Hero = { x: geo.fieldX[i], y: FIELD_BASE, rot: 0, s: 1, lime: 0, home: 0, font: 0, w: 0, vx: 0, vy: 0, blur: 0, mb: 0 };
  const bx = geo.bubbleX[i];
  const h = THROWS[i];
  const tSend = 0.28 + 0.018 * i;
  const tReseat = RESEAT + RESEAT_STAGGER * i;
  const tLift = 3.23 + 0.012 * i;
  const tBack = 6.05 + 0.02 * i;
  const tHome = 6.7 + 0.045 * i;
  if (t < tSend) return hero;
  if (t < h.launch) {
    // send: from the field into the new bubble on a short arc
    const k = springAt(t - tSend, 0, 1, 0, SPRING.glide);
    hero.x = mix(geo.fieldX[i], bx, k);
    hero.y = mix(FIELD_BASE, BUBBLE_BASE, k) - 22 * Math.sin(Math.PI * clamp01(k));
    hero.s = 1 + 0.12 * Math.sin(Math.PI * clamp01(k));
    hero.home = smooth(0.35, 0.75, k);
    hero.font = k > 0.5 ? 1 : 0;
    return hero;
  }
  hero.font = 1;
  hero.home = 1;
  if (t < tReseat) {
    const f = flight(h, t - h.launch, bx);
    hero.x = f.x;
    hero.y = f.y + (t - h.launch > h.tl ? 4 * depth[h.key] : 0);
    hero.rot = f.rot;
    hero.s = f.s;
    hero.lime = seg(t - h.launch, 0, 0.08);
    hero.mb = 1;
    return hero;
  }
  if (t < tLift) {
    // pulled back into the bubble in reading order
    const r0 = tReseat - h.launch;
    const a = flight(h, r0, bx);
    const b = flight(h, r0 - 0.004, bx);
    const tr = t - tReseat;
    hero.x = springAt(tr, a.x, bx, (a.x - b.x) / 0.004, SPRING.pop);
    hero.y = springAt(tr, a.y, BUBBLE_BASE, (a.y - b.y) / 0.004, SPRING.pop);
    hero.rot = springAt(tr, a.rot, h.turns * 360, 0, SPRING.pop);
    hero.s = Math.max(0.8, springAt(tr, a.s, 1, 0, SPRING.pop));
    hero.lime = 1 - smooth(0.28, 0.42, tr);
    hero.mb = 1;
    return hero;
  }
  if (t < tHome) {
    // lifted onto the stage for Ripple and Bloom, then back
    let k = springAt(t - tLift, 0, 1, 0, SPRING.glide);
    if (t >= tBack) k = springAt(t - tBack, 1, 0, 0, SPRING.pop);
    const sx = geo.stageX[i];
    hero.x = mix(bx, sx, k);
    hero.y = mix(BUBBLE_BASE, G.stageY, k);
    hero.s = mix(1, TYPE.stage, k);
    hero.lime = t < tBack ? smooth(0, 0.3, k) : smooth(0.1, 0.5, k);
    hero.rot = 0;
    // Ripple: crest 28 pt, two passes
    const pr = seg(t, 3.42, 4.72);
    if (pr > 0 && pr < 1) {
      const e = effectPose(EF.ripple, pr, i, geo.n, 28 / 6, true);
      hero.y += e.dy;
      hero.rot += e.rot;
    }
    // Bloom: scrubbed forward, back, forward; a swelling letter pushes its neighbours aside
    const pb = bloomScrub(t);
    if (pb > 0 && pb < 1) {
      let push = 0;
      let total = 0;
      for (let j = 0; j < geo.n; j++) {
        const ej = effectPose(EF.bloom, pb, j, geo.n, 1, true);
        const grow = ej.sc * geo.w[j] * TYPE.stage;
        if (j < i) push += grow;
        if (j === i) push += grow / 2;
        total += grow;
      }
      const e = effectPose(EF.bloom, pb, i, geo.n, 1, true);
      hero.x += push - total / 2;
      hero.s *= 1 + e.sc;
      hero.w = e.w;
    }
    return hero;
  }
  // unsent: back into the field, where the next loop starts
  const k = springAt(t - tHome, 0, 1, 0, SPRING.glide);
  hero.x = mix(bx, geo.fieldX[i], k);
  hero.y = mix(BUBBLE_BASE, FIELD_BASE, k) - 14 * Math.sin(Math.PI * clamp01(k));
  hero.home = 1 - smooth(0.25, 0.65, k);
  hero.font = k > 0.5 ? 0 : 1;
  return hero;
};

export const keysAt = (t: number) => {
  'worklet';
  const depth = [0, 0, 0, 0, 0, 0, 0, 0];
  const kick = [-1, -1, -1, -1, -1, -1, -1, -1];
  const active = [0, 0, 0, 0, 0, 0, 0, 0];
  for (let j = 0; j < PRESSES.length; j++) {
    const [k, a, b] = PRESSES[j];
    depth[k] = Math.max(depth[k], pressDepth(t, a, b));
  }
  for (let j = 0; j < HITS.length; j++) {
    const [k, a, s] = HITS[j];
    if (t < a) continue;
    const d = t < a + 0.06 ? s * easeOut(seg(t, a, a + 0.06)) : Math.max(0, springAt(t - a - 0.06, s, 0, 0, SPRING.snap));
    depth[k] = Math.max(depth[k], d);
    if (s === 1 && t < a + 0.8) kick[k] = seg(t, a, a + 0.8);
  }
  for (let j = 0; j < ACTIVE.length; j++) {
    const [k, a, b] = ACTIVE[j];
    active[k] = Math.max(active[k], seg(t, a, a + 0.12) * (1 - seg(t, b, b + 0.12)));
  }
  return { depth, kick, active };
};

export const frameAt = (tIn: number, geo: Geo): Frame => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const keys = keysAt(t);

  // the thread rises when the new bubble arrives, and settles back when it is unsent
  const rise = t < 0.28 ? 0 : t < 6.7 ? springAt(t - 0.28, 0, 1, 0, SPRING.glide) : springAt(t - 6.7, 1, 0, 0, SPRING.glide);
  const body = t < 0.3 ? 0 : t < 6.72 ? springAt(t - 0.3, 0, 1, 0, SPRING.pop) : Math.max(0, springAt(t - 6.72, 1, 0, 0, SPRING.glide));
  const squash = t < 0.62 ? 0 : springAt(t - 0.62, 0.04, 0, 0, SPRING.pop);
  const blip = t < RESEAT + 0.37 ? 0 : springAt(t - RESEAT - 0.37, 0, 0, 0.7, SPRING.pop);

  // the bubble empties to a lime shell while its letters are away
  let shell = 1;
  if (t >= BURST && t < RESEAT + 0.6) {
    // it empties letter by letter as they leave, and fills again as each one seats
    let away = 0;
    for (let i = 0; i < geo.n; i++) {
      away += smooth(THROWS[i].launch, THROWS[i].launch + 0.06, t) * (1 - smooth(0.28, 0.42, t - RESEAT - RESEAT_STAGGER * i));
    }
    shell = 1 - (0.6 * away) / geo.n;
  } else if (t >= 3.23 && t < 6.6) {
    shell = 1 - 0.6 * smooth(3.23, 3.42, t) * (1 - smooth(6.15, 6.45, t));
  }

  // send press: 1 → 0.88 → 1.06 → 1, the arrow nudges up
  let sendS = 1;
  if (t >= 0.2 && t < 0.26) sendS = mix(1, 0.88, easeOut(seg(t, 0.2, 0.26)));
  else if (t >= 0.26 && t < 0.4) sendS = mix(0.88, 1.06, easeOut(seg(t, 0.26, 0.4)));
  else if (t >= 0.4 && t < 0.52) sendS = mix(1.06, 1, easeInOut(seg(t, 0.4, 0.52)));
  const arrowY = -2 * Math.sin(Math.PI * seg(t, 0.2, 0.42));

  const placeholder = t < 6.7 ? easeOut(seg(t, 0.46, 0.66)) : 1 - easeInOut(seg(t, 6.7, 6.9));
  const select = t < 1 ? 1 - easeInOut(seg(t, 0.2, 0.3)) : easeInOut(seg(t, 7.3, 7.5));

  const hero: Hero[] = [];
  for (let i = 0; i < geo.n; i++) {
    const a = heroAt(t, i, geo, keys.depth);
    const b = heroAt(t - 1 / 240, i, geo, keys.depth);
    a.vx = (a.x - b.x) * 240;
    a.vy = (a.y - b.y) * 240;
    hero.push(a);
  }

  // the bubbles under a staged crest dip 3 pt
  const dips = [0, 0, 0];
  const pr = seg(t, 3.42, 4.72);
  if (pr > 0 && pr < 1) {
    for (let i = 0; i < geo.n; i++) {
      const e = effectPose(EF.ripple, pr, i, geo.n, 1, true);
      const lift = -e.dy / 6;
      for (let s = 0; s < geo.spans.length; s++) {
        if (hero[i].x >= geo.spans[s][0] && hero[i].x <= geo.spans[s][1]) dips[s] = Math.max(dips[s], 3 * lift);
      }
    }
  }

  const amp = 1 - 0.6 * smooth(0.82, 1.02, t) * (1 - smooth(6.3, 6.5, t));
  const keyP = (t % CYCLE) / CYCLE;
  return { rise, body, squash, blip, shell, sendS, arrowY, placeholder, select, hero, depth: keys.depth, kick: keys.kick, active: keys.active, amp, keyP, dips };
};
