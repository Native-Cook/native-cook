// Island Glass Orb: the autoplay timeline (creative spec §4) as a pure function of the loop time, so every frame of
// the loop is reproducible and the three stacks can match it frame for frame. Times are in seconds, lengths in pt.

import { G, SPRING, TIMES } from './igo-theme';

export const LOOP = 10;

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
/** The design system's emphasised curve: a quick start that glides in (≈ cubic-bezier(0.2, 0, 0, 1)). */
export const emph = (k: number) => {
  'worklet';
  return 1 - Math.pow(1 - k, 4);
};
export const smooth = (a: number, b: number, v: number) => {
  'worklet';
  const k = clamp01((v - a) / (b - a));
  return k * k * (3 - 2 * k);
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
  const s = Math.sqrt(Math.max(z * z - 1, 1e-6));
  const r1 = -w0 * (z - s);
  const r2 = -w0 * (z + s);
  const c2 = (v0 - r1 * x0) / (r2 - r1);
  const c1 = x0 - c2;
  return to + c1 * Math.exp(r1 * t) + c2 * Math.exp(r2 * t);
};

/** Everything the screen draws, for one moment. All numbers, so two frames can be blended field by field. */
export type Frame = {
  m: number; // drop amount: 0 island → 1 drop (spring overshoot allowed)
  pill: number; // island → thinking pill 0..1
  ban: number; // → reply banner 0..1
  antic: number; // the island gathering before it pours 0..1
  dx: number; // drop centre offset from rest (pt)
  dy: number;
  e: number; // squash/stretch: w × (1 + e), h × (1 − e)
  amp: number; // voice amplitude 0..1
  ampLag: number; // amplitude 60 ms ago (the rim lags the line)
  causticAmp: number; // amplitude 80 ms ago (the sculpture answers the drop)
  lineLen: number; // spectrum line length (pt)
  lineX: number;
  lineY: number;
  mic: number; // orange mic dot alpha
  spinA: number; // spinner alpha
  spinT: number; // spinner head, in turns
  spinBlur: number;
  pillWob: number; // thinking pill width wobble (pt)
  clock: number; // status bar time alpha
  textT: number; // time since the first word started (s), < 0 before
  textOut: number; // reply text exit 0..1
  arcA: number;
  arcW: number;
  arcHue: number;
  bandY: number; // the reply's relight band on the ring: centre (pt) and strength
  bandA: number;
  floorY: number; // the same band reaching the floor 120 ms later
  floorA: number;
  bandHue: number; // spectrum position of the band's light (violet → magenta → red-orange → amber) and of the floor's, 120 ms behind
  floorHue: number;
  rimL: number; // widget rim highlight 0..1 (left, right)
  rimR: number;
  row: number; // calendar row highlight 0..1 (overshoot allowed)
  pluckX: number;
  pluckD: number;
  free: number; // 0 hanging drop → 1 the free clear circle Ø 182
  cap: number; // black cap fraction of the drop's height (0.46 while it hangs, 0 once torn off)
  neck: number; // thread Ø at its thinnest (pt), 0 = no thread
  kJoin: number; // smooth-union radius between island, thread and drop
  iw: number; // the island's width and height (pt)
  ih: number;
};

export const FRAME_KEYS: (keyof Frame)[] = [
  'causticAmp', 'm', 'pill', 'ban', 'antic', 'dx', 'dy', 'e', 'amp', 'ampLag', 'lineLen', 'lineX', 'lineY', 'mic', 'spinA', 'spinT',
  'spinBlur', 'pillWob', 'clock', 'textT', 'textOut', 'arcA', 'arcW', 'arcHue', 'bandY', 'bandA', 'floorY', 'floorA', 'bandHue', 'floorHue', 'rimL', 'rimR',
  'row', 'pluckX', 'pluckD', 'free', 'cap', 'neck', 'kJoin', 'iw', 'ih',
];

/** Fields carried across a hand-over (live → sequence) so the shape never jumps. */
export const BLEND_KEYS: (keyof Frame)[] = ['causticAmp', 'm', 'pill', 'ban', 'dx', 'dy', 'e', 'amp', 'ampLag', 'lineLen', 'lineX', 'lineY', 'mic', 'free', 'cap', 'iw', 'ih'];

export const mixFrames = (a: Frame, b: Frame, k: number): Frame => {
  'worklet';
  const o = { ...a };
  for (let i = 0; i < FRAME_KEYS.length; i++) {
    const key = FRAME_KEYS[i];
    o[key] = a[key] + (b[key] - a[key]) * k;
  }
  return o;
};

/** Interpolate a voice sample at a fixed delay, on the UI runtime. */
export const sampleAmpAt = (history: { t: number; a: number }[], target: number, fallback: number) => {
  'worklet';
  if (!history.length) return fallback;
  let a = history[0];
  for (let i = 1; i < history.length; i++) {
    const b = history[i];
    if (b.t >= target) return mix(a.a, b.a, clamp01((target - a.t) / Math.max(b.t - a.t, 0.0001)));
    a = b;
  }
  return a.a;
};

/**
 * Catmull-Rom through the voice keys. Spec v3: the four syllable bursts now fill 1.5–3.0 s (the loudest at 2.2 s), the voice
 * swells to 0.9 as the thread thins (3.42 s), and runs 0.3 → 0.9 → 0.4 while the bead drifts (3.95–4.5 s).
 */
const KT = [1.5, 1.7, 1.88, 2.2, 2.45, 2.7, 3.0, 3.2, 3.42, 3.7, 3.95, 4.2, 4.5, 4.9];
const KV = [0.15, 0.9, 0.4, 1.0, 0.35, 0.8, 0.45, 0.6, 0.9, 0.45, 0.3, 0.9, 0.4, 0.0];
export const ampAt = (t: number) => {
  'worklet';
  if (t < 1.1) return 0;
  if (t < 1.5) return 0.15 * easeInOut(seg(t, 1.1, 1.5));
  if (t < 4.9) {
    let i = 0;
    while (i < KT.length - 2 && t >= KT[i + 1]) i++;
    const p0 = KV[Math.max(i - 1, 0)];
    const p1 = KV[i];
    const p2 = KV[i + 1];
    const p3 = KV[Math.min(i + 2, KV.length - 1)];
    const u = (t - KT[i]) / (KT[i + 1] - KT[i]);
    const v = 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u);
    return Math.min(Math.max(v, 0), 1.05);
  }
  return 0;
};

/** What the tear-off does to the drop's shape, thread and island, from how far it is from home (and since when it broke). */
export const dropShape = (travel: number, broken: number, ts: number, antic: number) => {
  'worklet';
  const u = clamp01(travel / G.tear.travel);
  let free = smooth(40, 230, travel);
  // the black stays with the island: the drop's cap is gone by 60 pt, so the clear lens shows as soon as it clears the island
  // (the spec had 0.46 → 0 between 80 and 230 pt; that left a wide black dome with shoulders under the narrowing island)
  let cap = 0.46 * (1 - smooth(0, 60, travel));
  let neck = travel <= 120 ? mix(28, 20, travel / 120) : mix(20, 6, clamp01((travel - 120) / 120)); // Ø 20 at 120 pt, Ø 6 at 240
  // a wide meniscus while the funnel is drawn out (smooth, no shoulders), tightening to a hairline thread by 240 pt
  let kJoin = travel < 80 ? mix(G.k, 44, travel / 80) : mix(44, 12, smooth(80, 240, travel));
  const base = mix(G.island.w, G.islandAntic.w, antic);
  let iw = mix(base, 58, smooth(0.25, 1, u)); // the island narrows to 58 pt
  if (broken > 0.5) {
    iw = springAt(ts, 58, G.island.w, 0, SPRING.recoil.d, SPRING.recoil.k); // it recoils (d 12, k 260)
    if (ts < TIMES.pinchDur) {
      const r = 1 - ts / TIMES.pinchDur; // the thread pinches in 80 ms
      neck *= r;
      kJoin *= r;
    } else {
      const r = smooth(160, 90, travel); // and re-forms under 150 pt on the way home
      neck *= r;
      kJoin *= r;
    }
    // a torn-off bead stays a clear circle until it is close enough to be pulled back in
    const near = smooth(160, 90, travel);
    free = mix(1, free, near);
    cap *= near;
  }
  const ih = mix(G.island.h, G.islandAntic.h, antic) + 0.05 * (G.island.w - iw);
  return { free, cap, neck, kJoin, iw, ih };
};

const SH = TIMES.shift;
const START_X = G.tear.drift.x - G.drop.cx;
const START_Y = G.tear.drift.y - G.drop.cy;
const START_D = Math.hypot(START_X, START_Y);
const flyF = (t: number) => {
  'worklet';
  return springAt(t - TIMES.fly0, 0, 1, 0, SPRING.fly.d, SPRING.fly.k);
};

/** The drop's offset from its resting place: tear-off, settle on the ring, drift, flight home. */
export const dropOffsetAt = (t: number) => {
  'worklet';
  if (t < TIMES.tear0) return { dx: 0, dy: 0 };
  if (t < TIMES.pinch) {
    const u = easeInOut(seg(t, TIMES.tear0, TIMES.pinch));
    return { dx: (G.tear.to.x - G.drop.cx) * u, dy: (G.tear.to.y - G.drop.cy) * u };
  }
  if (t < TIMES.fly0) {
    const y = springAt(t - TIMES.pinch, G.tear.to.y, G.tear.rest.y, 0, SPRING.glide.d, SPRING.glide.k);
    const u = easeInOut(seg(t, TIMES.settled, TIMES.fly0));
    return {
      dx: G.tear.to.x - G.drop.cx + (G.tear.drift.x - G.tear.to.x) * u,
      dy: y - G.drop.cy + (G.tear.drift.y - G.tear.rest.y) * u,
    };
  }
  const f = flyF(t);
  return { dx: START_X * (1 - f), dy: START_Y * (1 - f) };
};

/** The reply's light band: 140 pt tall, centre y 300 → 660 over 700 ms; its strength (0 outside the run). */
const BAND0 = 7.25;
const BAND_DUR = 0.7;
export const bandAt = (t: number) => {
  'worklet';
  const u = seg(t, BAND0, BAND0 + BAND_DUR);
  const inside = t >= BAND0 && t <= BAND0 + BAND_DUR;
  const y = inside ? mix(300, 660, easeInOut(u)) : 300;
  const a = inside ? smooth(0, 0.12, u) * (1 - smooth(0.8, 1.0, u)) : 0;
  // the arc's colours only: violet-pink → magenta → red-orange → amber (spectrum positions 0.76 → 1.18), never the cyan/blue half
  return { y, a, hue: 0.76 + 0.42 * u };
};

/** How much the reply's light lights a widget's top rim, a short pulse as the band leaves the banner. */
const rimAt = (t: number) => {
  'worklet';
  return Math.exp(-Math.pow((t - 7.38) / 0.2, 2));
};

export const frameAt = (tIn: number): Frame => {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  const arrive = TIMES.arrive;

  // shapes
  const antic = t < 0.5 ? 0 : t < 0.62 ? easeOut(seg(t, 0.5, 0.62)) : 1 - easeInOut(seg(t, 0.62, 0.9));
  let m = 0;
  if (t >= 0.62 && t < arrive) m = springAt(t - 0.62, 0, 1, 0, SPRING.pop.d, SPRING.pop.k);
  else if (t >= arrive) m = Math.max(0, springAt(t - arrive, 1, 0, 0, SPRING.snap.d, SPRING.snap.k)); // the bead is swallowed
  if (t > 5.6) m = 0;
  const pill = t < TIMES.pill || t >= 8.8 + SH ? 0 : Math.max(0, springAt(t - TIMES.pill, 0, 1, 0, SPRING.snap.d, SPRING.snap.k));
  let ban = 0;
  if (t >= 5.7 + SH && t < 8.8 + SH) ban = springAt(t - 5.7 - SH, 0, 1, 0, SPRING.glide.d, SPRING.glide.k);
  else if (t >= 8.8 + SH && t < 9.9) ban = Math.max(0, springAt(t - 8.8 - SH, 1, 0, 0, SPRING.glide.d, SPRING.glide.k));

  // the drop: intro squash, then the tear-off, the bead's squash on the ring, the flight home
  const { dx, dy } = dropOffsetAt(t);
  const travel = Math.hypot(dx, dy);
  const broken = t >= TIMES.pinch && t < arrive ? 1 : 0;
  const ts = t - TIMES.pinch;
  let e = 0;
  if (t >= 0.78 && t < 1.4) {
    const k = 1 - seg(t, 0.78, 1.4);
    e = 0.06 * Math.sin(2 * Math.PI * 5 * (t - 0.78)) * k * k;
  } else if (t >= TIMES.tear0 && t < TIMES.pinch) {
    e = -0.07 * clamp01(travel / G.tear.travel);
  } else if (t >= TIMES.pinch && t < TIMES.fly0) {
    e = -0.07 * Math.exp(-ts / 0.04) + 0.06 * Math.sin(2 * Math.PI * 3.5 * ts) * Math.exp(-ts / 0.2); // ±6 % squash
  } else if (t >= TIMES.fly0 && t < arrive) {
    const v = Math.abs(flyF(t + 0.004) - flyF(t - 0.004)) / 0.008;
    e = -0.22 * clamp01((v * START_D) / 2400); // stretch ≤ 1.22 along the flight
  }
  const shape = dropShape(travel, broken, ts, antic);
  const bump = t >= arrive - 0.03 && t < arrive + 0.27 ? Math.sin(Math.PI * seg(t, arrive - 0.03, arrive + 0.27)) : 0; // the merge: h 37 → 47 → 37

  // the voice and its line
  const amp = ampAt(t);
  const ampLag = ampAt(t - 0.06);
  const mc = clamp01(m);
  let lineLen = 0;
  let lineX = G.drop.cx + dx * mc;
  let lineY = mix(G.dropSmall.cy, G.drop.cy, mc) + dy * mc;
  if (t >= 1.1 && t < 1.5) lineLen = G.lineLen * easeOut(seg(t, 1.1, 1.5));
  else if (t >= 1.5 && t < 4.8) lineLen = G.lineLen;
  else if (t >= 4.8 && t < 5.3) {
    const k = easeInOut(seg(t, 4.8, 5.3));
    lineLen = mix(G.lineLen, 0, k);
    lineX = mix(lineX, G.spinner.x + G.spinner.r, k);
    lineY = mix(lineY, G.spinner.y, k);
  }
  const mic = t < 0.7 ? 0 : t < TIMES.pill ? easeOut(seg(t, 0.7, 0.85)) : 1 - easeOut(seg(t, TIMES.pill, TIMES.pill + 0.15));

  // thinking: the pill 4950–5250, the spinner to 5950
  const spinA = t < 5.2 || t >= 6.1 ? 0 : easeOut(seg(t, 5.2, 5.4)) * (1 - seg(t, 5.95, 6.1));
  const spinT = t - 5.2;
  const spinBlur = 6 * seg(t, 5.95, 6.1);
  const pillWob = t >= 5.35 && t < 5.95 ? 3 * Math.sin(2 * Math.PI * (t - 5.35)) : 0;

  // reply (v2 timing + 250 ms)
  const clock = t < 5.7 + SH ? 1 : t < 9.0 + SH ? 1 - easeOut(seg(t, 5.7 + SH, 5.9 + SH)) : easeOut(seg(t, 9.0 + SH, 9.2 + SH));
  const textT = t - 6.0 - SH;
  const textOut = easeIn(seg(t, 8.7 + SH, 8.9 + SH));
  let arcA = easeOut(seg(t, 6.2 + SH, 6.8 + SH)) * (1 - seg(t, 8.95 + SH, 9.2 + SH));
  if (t < 6.2 + SH || t >= 9.2 + SH) arcA = 0;
  const arcW = mix(140, 220, easeOut(seg(t, 6.2 + SH, 6.8 + SH))) * (1 - easeIn(seg(t, 8.8 + SH, 9.2 + SH)));
  const arcHue = (t - 6.2 - SH) / 2;

  // payoff: the band of arc-coloured light over the ring (the floor 120 ms behind), the widget rims and the calendar row
  const band = bandAt(t);
  const floor = bandAt(t - 0.12);
  let row = 0;
  if (t >= 7.4 + SH && t < 7.7 + SH) row = springAt(t - 7.4 - SH, 0, 1, 0, SPRING.pop.d, SPRING.pop.k);
  else if (t >= 7.7 + SH && t < 8.6 + SH) {
    const r0 = springAt(0.3, 0, 1, 0, SPRING.pop.d, SPRING.pop.k);
    row = springAt(t - 7.7 - SH, r0, 0, 0, SPRING.glide.d, SPRING.glide.k) * (1 - seg(t, 8.3 + SH, 8.6 + SH));
  }

  return {
    m, pill, ban, antic, dx, dy, e, amp, ampLag, causticAmp: ampAt(t - 0.08), lineLen, lineX, lineY, mic, spinA, spinT, spinBlur, pillWob, clock,
    textT, textOut, arcA, arcW, arcHue, bandY: band.y, bandA: band.a, floorY: floor.y, floorA: floor.a, bandHue: band.hue, floorHue: floor.hue, rimL: rimAt(t), rimR: rimAt(t - 0.06), row,
    pluckX: 0, pluckD: 0, free: shape.free, cap: shape.cap, neck: shape.neck, kJoin: shape.kJoin, iw: shape.iw, ih: shape.ih + 10 * bump,
  };
};

/** The values a finger and the live state machine own. */
export type Live = {
  m: number;
  pill: number;
  ban: number;
  dx: number;
  dy: number;
  amp: number;
  ampLag: number;
  causticAmp: number;
  e: number;
  mic: number;
  spin: number;
  pluckX: number;
  pluckD: number;
  broken: number; // 1 from the pinch until the bead has merged again
  ts: number; // seconds since the pinch
  tm: number; // seconds since the merge, < 0 before
  row: number; // the calendar row, pre-lit while the lens is over it
  clockT: number; // a free-running clock for the lobes, spinner and arc hue
};

/** The frame drawn while a finger (or the live flow) owns the island. */
export const liveFrameAt = (L: Live): Frame => {
  'worklet';
  const f = frameAt(0);
  const mc = clamp01(L.m);
  const ban = L.ban;
  const shape = dropShape(Math.hypot(L.dx, L.dy), L.broken, L.ts, 0);
  const bump = L.tm >= 0 && L.tm < 0.3 ? Math.sin(Math.PI * (L.tm / 0.3)) : 0;
  return {
    ...f,
    m: L.m,
    pill: L.pill,
    ban,
    antic: 0,
    dx: L.dx,
    dy: L.dy,
    e: L.e,
    amp: L.amp,
    ampLag: L.ampLag,
    causticAmp: L.causticAmp,
    lineLen: G.lineLen * easeOut(seg(L.m, 0.55, 1.0)),
    lineX: G.drop.cx + L.dx * mc,
    lineY: mix(G.dropSmall.cy, G.drop.cy, mc) + L.dy * mc,
    mic: L.mic,
    spinA: L.spin,
    spinT: L.clockT,
    spinBlur: 0,
    clock: 1 - smooth(0.15, 0.6, ban),
    textT: 10,
    textOut: 1 - smooth(0.55, 0.95, ban),
    arcA: smooth(0.45, 1, ban),
    arcW: 220 * smooth(0.2, 1, ban),
    arcHue: (L.clockT - 6.2) / 2,
    pluckX: L.pluckX,
    pluckD: L.pluckD,
    row: L.row,
    free: shape.free,
    cap: shape.cap,
    neck: shape.neck,
    kJoin: shape.kJoin,
    iw: shape.iw,
    ih: shape.ih + 10 * bump,
  };
};

/** Rest frames for reduced motion: the states change by a 200 ms linear morph, nothing else moves. */
export const thinkRest = (): Frame => {
  'worklet';
  const f = frameAt(5.6);
  return { ...f, pillWob: 0, spinT: 0.25 };
};
export const replyRest = (): Frame => {
  'worklet';
  const f = frameAt(8.75);
  return { ...f, row: 0, bandA: 0, floorA: 0, rimL: 0, rimR: 0, arcHue: 0.15 };
};

/** Live reply sequence, including its continuous handover and reduced-motion states. */
export const sequenceFrameAt = (s: number, start: number, from: Frame, blend: number[], reduced: boolean): Frame => {
  'worklet';
  const a = TIMES.arrive;
  if (reduced) {
    const think = thinkRest();
    const rep = replyRest();
    if (s < a + 0.2) return mixFrames(from, think, seg(s, a, a + 0.2));
    if (s < 5.7 + SH) return think;
    if (s < 5.9 + SH) return mixFrames(think, rep, seg(s, 5.7 + SH, 5.9 + SH));
    if (s < 8.7 + SH) return rep;
    if (s < 8.9 + SH) return mixFrames(rep, frameAt(0), seg(s, 8.7 + SH, 8.9 + SH));
    return frameAt(0);
  }
  const f = frameAt(s);
  const k = 1 - easeOut(seg(s - start, 0, 0.35));
  if (k > 0) {
    for (let i = 0; i < BLEND_KEYS.length; i++) f[BLEND_KEYS[i]] += blend[i] * k;
  }
  return f;
};

/** Geometry of the black shape for a frame: the rounded box (island → pill → banner) and the drop. */
export const shapesAt = (f: Frame, oy: number) => {
  'worklet';
  const ix = 201 - f.iw / 2;
  const pw = G.pill.w + f.pillWob;
  const px = G.pill.x - f.pillWob / 2;
  const x1 = mix(ix, px, f.pill);
  const y1 = mix(G.island.y, G.pill.y, f.pill);
  const w1 = mix(f.iw, pw, f.pill);
  const h1 = mix(f.ih, G.pill.h, f.pill);
  const box = {
    x: mix(x1, G.banner.x, f.ban),
    y: mix(y1, G.banner.y, f.ban) + oy,
    w: mix(w1, G.banner.w, f.ban),
    h: mix(h1, G.banner.h, f.ban),
  };
  const mc = clamp01(f.m);
  const grow = 1 + 0.08 * f.amp;
  const wFull = mix(G.drop.w, 2 * G.tear.R, f.free);
  const hFull = mix(G.drop.h, 2 * G.tear.R, f.free);
  const drop = {
    cx: G.drop.cx + f.dx * mc,
    cy: mix(G.dropSmall.cy, G.drop.cy, Math.min(Math.max(f.m, 0), 1.15)) + f.dy * mc + oy,
    a: (mix(G.dropSmall.w, wFull, f.m) / 2) * grow * (1 + f.e),
    b: (mix(G.dropSmall.h, hFull, f.m) / 2) * grow * (1 - f.e),
    n: mix(G.drop.n, 2, f.free),
  };
  return { box, drop };
};
