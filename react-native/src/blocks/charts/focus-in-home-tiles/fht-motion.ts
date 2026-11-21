// Focus-In Home Tiles: the autoplay timeline (creative spec §4 as reshaped by the Pro addendum) as a pure function of
// the loop time, so every frame is reproducible and the three stacks can match it frame for frame. Times are in
// seconds, distances in points on the 402 × 874 pt canvas.
//
// One orange light never dies. It is a 200 pt bokeh lamp in the dark at the head of the loop, racks down to the live
// dot on the Calling pill, drops into the chart as bar 5's seed, stands up and floods, walks out of the chart to become
// the budget bar, and on the dark turn swells back into the lamp.

import { BAR_FOOT, BAR_W, BARS, G, GROUPS, ORANGE, PITCH, PLANE_DEFOCUS, PICK_REST, SPRING } from './fht-theme';

export const LOOP = 10;

export const clamp01 = (v: number) => {
  'worklet';
  return Math.min(Math.max(v, 0), 1);
};
/** 0 before a, 1 after b. */
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
  const s = Math.sqrt(z * z - 1);
  const r1 = -w0 * (z - s);
  const r2 = -w0 * (z + s);
  const c2 = (v0 - r1 * x0) / (r2 - r1);
  const c1 = x0 - c2;
  return to + c1 * Math.exp(r1 * t) + c2 * Math.exp(r2 * t);
};

/** Everything the screen draws, for one moment. */
export type Frame = {
  s: number; // scroll offset (pt)
  fp: number; // focal plane (0 near … 2 far)
  front: number; // cloud front position along the diagonal (−0.22 … 1.32; 2 = no front)
  dir: number; // 1: paper behind the front (opening), −1: forest behind it (dark turn)
  grow: [number, number, number, number, number]; // bars rise 0..1 (overshoot allowed)
  flood: number; // bar 5's orange pouring down it 0..1
  dip: number; // the dotted limit line's flinch (pt, down)
  degA: number; // strip temperature before the current step
  degB: number; // … after it
  degK: number; // step progress 0..1
  pick: number; // room picker: index of the centre row (float)
  alert: number; // 1: alert in focus (budget reached) … 0: out of focus
  budget: number; // budget used, % (40 … 120; 120 fills the tile)
  ox: number; // the orange: centre, size, corner radius (screen pt)
  oy: number;
  ow: number;
  oh: number;
  or: number;
  oth: number; // gradient turn 0 (orange end up, chart bar) … 1 (orange end down, budget bar)
  olamp: number; // 0 flat bar … 1 bokeh lamp
  glyph: number; // budget bar's power glyph drawn 0..1
  haze: number; // extra defocus on every tile as the room goes dark (pt)
  sharp: number; // 1: reduced motion, no defocus
};

// ---- geometry shared by the timeline, the live state and the renderer ----

/** Paper-ness at a screen point for a given cloud front (no noise; the shader adds the cloud's edge). */
export const paperAt = (x: number, y: number, front: number, dir: number) => {
  'worklet';
  if (front >= 1.9) return 1;
  const diag = (0.35 * x) / 402 + (0.65 * y) / 874;
  const m = smooth(clamp01((front - diag + 0.14) / 0.28));
  return dir > 0 ? m : 1 - m;
};

/** A focus group's screen transform: scale about its centre (focus breathing), then a page offset with parallax. */
export const groupGeo = (g: number, s: number, fp: number) => {
  'worklet';
  const grp = GROUPS[g];
  const cx = grp.rect[0] + grp.rect[2] / 2;
  const cy = grp.rect[1] + grp.rect[3] / 2;
  const u = s / PITCH;
  const par = Math.sin(Math.PI * (u - Math.floor(u)));
  const near = (2 - grp.plane) / 2;
  const dy = grp.page * PITCH - s - 6 * near * par;
  // tiles in front of the focal plane are magnified up to 3 % (they are nearer the lens)
  const scale = 1 + 0.03 * clamp01(fp - grp.plane);
  return { cx, cy, dy, scale };
};

/** Page-local point → screen through a group's transform. */
export const toScreen = (g: number, s: number, fp: number, x: number, y: number) => {
  'worklet';
  const q = groupGeo(g, s, fp);
  return { x: q.cx + (x - q.cx) * q.scale, y: q.cy + (y - q.cy) * q.scale + q.dy, k: q.scale };
};

/** A group's circle-of-confusion radius (pt) and alpha. */
export const groupFocus = (g: number, f: Frame) => {
  'worklet';
  if (f.sharp > 0.5) return { r: 0, a: 1 };
  const grp = GROUPS[g];
  const q = groupGeo(g, f.s, f.fp);
  const m = paperAt(q.cx, q.cy + q.dy, f.front, f.dir);
  let r = PLANE_DEFOCUS * Math.abs(grp.plane - f.fp) + 22 * (1 - m) + f.haze;
  if (g === G.alert) r += 12 * (1 - f.alert);
  // a tile never dims under 0.85: defocus, not a fade
  const a = 1 - 0.15 * smooth(clamp01(r / 18));
  return { r, a };
};

const BAR5_FULL = BAR_FOOT - BARS[4].top; // 316

export const barHeight = (i: number, grow: number) => {
  'worklet';
  const full = BAR_FOOT - BARS[i].top;
  return BAR_W + (full - BAR_W) * grow;
};

export const budgetHeight = (pct: number) => {
  'worklet';
  return (ORANGE.budget.h * pct) / 120;
};

/**
 * The orange as a function of the scroll: the live dot (page 1) → bar 5 (page 2) → the budget bar (page 3). Both ends
 * are anchored to their tiles, so the light holds still on screen while the page flows under it.
 */
export const chainAt = (s: number, fp: number, grow5: number, budget: number) => {
  'worklet';
  const h5 = BAR_W + (BAR5_FULL - BAR_W) * grow5;
  const top5 = BAR_FOOT - h5;
  const b0 = toScreen(G.chart, s, fp, BARS[4].x + BAR_W / 2, top5 + h5 / 2);
  const bw = BAR_W * b0.k;
  const bh = h5 * b0.k;
  if (s <= PITCH) {
    const q = s / PITCH;
    const d = toScreen(G.chat, s, fp, ORANGE.dot.x, ORANGE.dot.y);
    const k = smooth(seg(q, 0.04, 0.96));
    const ks = smooth(seg(q, 0.3, 0.92));
    const w = mix(ORANGE.dot.d, bw, ks);
    const h = mix(ORANGE.dot.d, bh, ks);
    return { x: mix(d.x, b0.x, k), y: mix(d.y, b0.y, k), w, h, r: Math.min(w, h) / 2, th: 0 };
  }
  const o = clamp01((s - PITCH) / PITCH);
  const k = smooth(o);
  const hb = budgetHeight(budget);
  const B = ORANGE.budget;
  const bx = B.x + B.w / 2;
  const by = B.y + B.h - hb / 2 + 2 * PITCH - s;
  const w = mix(bw, B.w, k);
  const h = mix(bh, hb, k);
  return {
    // an arc 18 pt right of the straight path, kept inside the page margin
    x: Math.min(mix(b0.x, bx, k) + 18 * Math.sin(Math.PI * k), 390 - w / 2),
    y: mix(b0.y, by, k),
    w,
    h,
    r: mix(bw / 2, Math.min(B.r, hb / 2), k),
    th: k,
  };
};

// ---- the autoplay timeline ----

const scrollAt = (t: number) => {
  'worklet';
  if (t < 1.8) return 0;
  if (t < 3.2) return PITCH * easeInOut(seg(t, 1.8, 3.2));
  if (t < 4.9) return PITCH;
  if (t < 6.3) return PITCH * (1 + easeInOut(seg(t, 4.9, 6.3)));
  if (t < 9.65) return 2 * PITCH;
  return 0; // under the forest
};

/** The lens: racks to the tile where the action is (550 ms inOut). */
const focusAt = (t: number) => {
  'worklet';
  if (t < 0.75) return 0; // header
  if (t < 1.3) return 2 * easeInOut(seg(t, 0.75, 1.3)); // through Bedroom to Live Chat, where the light seats
  if (t < 1.9) return 2;
  if (t < 2.45) return 2 - 2 * easeInOut(seg(t, 1.9, 2.45)); // the strip, as the degrees step
  if (t < 3.0) return 0;
  if (t < 3.55) return easeInOut(seg(t, 3.0, 3.55)); // the chart, as the bars stand up
  if (t < 7.55) return 1; // through the hand-over the picker arrives on the chart's plane
  if (t < 8.1) return 1 + easeInOut(seg(t, 7.55, 8.1)); // the alert
  if (t < 9.65) return 2;
  return 0;
};

const frontAt = (t: number) => {
  'worklet';
  if (t < 1.4) {
    const k = seg(t, 0, 1.4);
    return { front: mix(-0.22, 1.32, mix(k, smooth(k), 0.35)), dir: 1 };
  }
  if (t < 8.4) return { front: 2, dir: 1 };
  const k = seg(t, 8.4, 9.6);
  return { front: mix(-0.22, 1.32, mix(k, k * k, 0.4)), dir: -1 };
};

const DEG_STEPS = [2.3, 2.6, 2.9];
const DEG_DUR = 0.26;

const degAt = (t: number) => {
  'worklet';
  if (t >= 9.65) return { a: 19, b: 19, k: 0 };
  for (let i = DEG_STEPS.length - 1; i >= 0; i--) {
    const t0 = DEG_STEPS[i];
    if (t >= t0) return { a: 19 + i, b: 20 + i, k: easeOut(seg(t, t0, t0 + DEG_DUR)) };
  }
  return { a: 19, b: 19, k: 0 };
};

const pickAt = (t: number) => {
  'worklet';
  if (t < 6.4 || t >= 9.65) return 1;
  if (t < 7.05) return springAt(t - 6.4, 1, 2, 0, SPRING.detent.d, SPRING.detent.k);
  return springAt(t - 7.05, 2, 3, 0, SPRING.detent.d, SPRING.detent.k);
};

const growAt = (t: number, i: number) => {
  'worklet';
  if (t >= 9.65) return 0;
  const t0 = i < 4 ? 3.0 + 0.09 * i : 3.42;
  return springAt(t - t0, 0, 1, 0, SPRING.pop.d, SPRING.pop.k);
};

const RACK_A = 0.75;
const RACK_B = 1.35;
const SWELL_A = 8.5;
const SWELL_B = 9.4;

/** The frame at loop time t (s). */
export const frameAt = (t: number, viewportHeight = 874): Frame => {
  'worklet';
  const s = scrollAt(t);
  const fp = focusAt(t);
  const { front, dir } = frontAt(t);
  const grow: [number, number, number, number, number] = [growAt(t, 0), growAt(t, 1), growAt(t, 2), growAt(t, 3), growAt(t, 4)];
  const flood = t >= 9.65 ? 0 : easeOut(seg(t, 3.66, 4.5));
  const dip = 2 * Math.sin(Math.PI * seg(t, 3.66, 3.96)) * (t < 9.65 ? 1 : 0);
  const deg = degAt(t);
  const budget = 120;

  // the orange
  const L = ORANGE.lamp;
  // Preserve the lamp's bottom inset on compact viewports as it leaves and returns to rest.
  const lampY = L.y - Math.max(0, 874 - viewportHeight);
  let ox: number = L.x;
  let oy: number = lampY;
  let ow: number = L.d;
  let oh: number = L.d;
  let or: number = L.d / 2;
  let oth = 1;
  let olamp = 1;
  if (t >= RACK_A && t < SWELL_A) {
    const c = chainAt(s, fp, grow[4], budget);
    ox = c.x;
    oy = c.y;
    ow = c.w;
    oh = c.h;
    or = c.r;
    oth = c.th;
    olamp = 0;
    if (t < RACK_B) {
      // the lamp racks through focus: its circle of confusion shrinks to the dot as it travels to the pill
      const k = seg(t, RACK_A, RACK_B);
      const kp = easeInOut(k);
      const dd = mix(L.d, ORANGE.dot.d, k * (2 - k));
      ox = mix(L.x, c.x, kp);
      oy = mix(lampY, c.y, kp);
      ow = dd;
      oh = dd;
      or = dd / 2;
      olamp = 1 - smooth(seg(k, 0.55, 1));
      oth = 0;
    } else if (t < 1.9) {
      // seats with a pop
      const bump = springAt(t - RACK_B, 0, 0, 70, SPRING.pop.d, SPRING.pop.k);
      ow += bump;
      oh += bump;
      or = ow / 2;
    }
    if (t >= 6.3) {
      // lands as the budget bar: 6 pt past its seat, downward
      oy += springAt(t - 6.3, 0, 0, 150, SPRING.pop.d, SPRING.pop.k);
    }
  } else if (t >= SWELL_A && t < SWELL_B) {
    // the budget bar swells into the lamp
    const c = chainAt(s, fp, grow[4], budget);
    const k = easeInOut(seg(t, SWELL_A, SWELL_B));
    ox = mix(c.x, L.x, k);
    oy = mix(c.y, lampY, k);
    ow = mix(c.w, L.d, k);
    oh = mix(c.h, L.d, k);
    or = mix(c.r, L.d / 2, smooth(k));
    oth = 1;
    olamp = smooth(seg(k, 0.05, 0.5));
  }
  const glyph = t < 6.3 ? 0 : t < SWELL_A ? easeOut(seg(t, 6.32, 6.8)) : 1 - seg(t, SWELL_A, SWELL_A + 0.25);

  return {
    s,
    fp,
    front,
    dir,
    grow,
    flood,
    dip,
    degA: deg.a,
    degB: deg.b,
    degK: deg.k,
    pick: pickAt(t),
    alert: 1,
    budget,
    ox,
    oy,
    ow,
    oh,
    or,
    oth,
    olamp,
    glyph,
    haze: t >= 8.4 && t < 9.65 ? 18 * smooth(seg(t, 8.4, 9.3)) : 0,
    sharp: 0,
  };
};

/** The finger's state: the scroll, the lens, the budget, and the stepper's darkness with the lamp under the finger. */
export type Live = {
  s: number;
  fp: number;
  budget: number;
  alert: number;
  dark: number; // 0..1
  lx: number;
  ly: number;
  sharp: number;
};

/** The frame for a live state: everything is a function of the scroll, so dragging scrubs the chain both ways. */
export const liveFrameAt = (L: Live): Frame => {
  'worklet';
  const s = L.s;
  const sp = Math.min(Math.max(s, 0), PITCH);
  const grow: [number, number, number, number, number] = [
    easeOut(seg(sp, 420, 700)),
    easeOut(seg(sp, 450, 720)),
    easeOut(seg(sp, 480, 740)),
    easeOut(seg(sp, 510, 760)),
    smooth(seg(sp, 560, 780)),
  ];
  const flood = smooth(seg(sp, 640, 780));
  const c = chainAt(Math.min(Math.max(s, 0), 2 * PITCH), L.fp, grow[4], L.budget);
  const k = easeInOut(clamp01(L.dark));
  const lamp = ORANGE.lamp.d;
  const onBudget = clamp01((s - PITCH) / PITCH);
  return {
    s,
    fp: L.fp,
    front: L.dark > 0.001 ? mix(-0.22, 1.32, L.dark) : 2,
    dir: -1,
    grow,
    flood,
    dip: 0,
    degA: 22,
    degB: 22,
    degK: 0,
    pick: PICK_REST,
    alert: L.alert,
    budget: L.budget,
    ox: mix(c.x, L.lx, k),
    oy: mix(c.y, L.ly, k),
    ow: mix(c.w, lamp, k),
    oh: mix(c.h, lamp, k),
    or: mix(c.r, lamp / 2, smooth(k)),
    oth: c.th,
    olamp: smooth(seg(k, 0.15, 0.85)),
    glyph: onBudget > 0.98 ? 1 - seg(k, 0, 0.3) : 0,
    haze: 18 * k,
    sharp: L.sharp,
  };
};

/** Autoplay times that rest on each page, for handing the loop back after a touch. */
export const PAGE_REST_T: readonly number[] = [1.75, 4.4, 8.15];
