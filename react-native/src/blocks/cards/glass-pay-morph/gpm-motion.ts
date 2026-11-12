// Glass Pay Morph — the motion. One loop, LOOP = 4.4 s; every frame of autoPlay is a pure function of
// the loop time τ (no state carried between frames), so the loop records frame-exact.
//
// Loop time = pin time + 0.30 s. Phases (loop time):
//   0.00–0.60  rest (drift)                 3.90–4.40  rest (drift)
//   0.60–1.30  collapse: card → pill, the lights travel half-way round it and dock as the mark
//   1.30–2.80  hold: captions swap, the pay pulse at 1.60
//   2.80–3.90  expand: the lights whip out as crescents, the glass turns half a revolution
//
// Each channel is one monotone cubic (PCHIP) through the keys measured on the pin's 30 fps sheets,
// so nothing overshoots between keys and a run of equal keys holds exactly. Offsets are from the
// hero centre (gpm-theme HERO).

import { CARD, HERO, PERSPECTIVE, PILL } from './gpm-theme';

export const LOOP = 4.4;
export const T = {
  collapse0: 0.3, // the lights set off (pin 0.0); the card follows from 0.60
  collapse1: 1.3,
  dock: 1.34, // the lights meet on the pill
  pulse: 1.6,
  paidHold: 2.2, // a tap that collapses plays to here and holds (the pulse has ended)
  expand0: 2.8,
  expand1: 3.9,
};
/** Trail samples per light (the stretch of a moving light). */
export const TRAIL = 8;

type Track = { t: number[]; v: number[]; m: number[] };

// Fritsch–Carlson monotone slopes (module init, JS thread).
function pchip(keys: number[][]): Track {
  const t = keys.map((k) => k[0]);
  const v = keys.map((k) => k[1]);
  const n = t.length;
  const d: number[] = [];
  for (let i = 0; i < n - 1; i++) d.push((v[i + 1] - v[i]) / (t[i + 1] - t[i]));
  const m: number[] = new Array(n).fill(0);
  for (let i = 1; i < n - 1; i++) {
    if (d[i - 1] * d[i] <= 0) m[i] = 0;
    else {
      const h0 = t[i] - t[i - 1];
      const h1 = t[i + 1] - t[i];
      const w1 = 2 * h1 + h0;
      const w2 = h1 + 2 * h0;
      m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i]);
    }
  }
  m[0] = 0;
  m[n - 1] = 0;
  return { t, v, m };
}

// Key times shared by most channels: rest, collapse, hold, expand, rest.
const KC = [0, 0.6, 0.77, 0.83, 0.9, 1.0, 1.1, 1.17, 1.23, 1.3];
const KE = [2.8, 3.0, 3.1, 3.17, 3.23, 3.3, 3.37, 3.47, 3.57, 3.67, 3.9, LOOP];
const KT = [...KC, ...KE];
const zip = (vals: number[]) => pchip(KT.map((t, i) => [t, vals[i]]));

const AX = PILL.amber.x - HERO.x;
const AY = PILL.amber.y - HERO.y;
const RX = PILL.red.x - HERO.x;
const RY = PILL.red.y - HERO.y;
const PX = PILL.x - HERO.x;

// Rest pose of the lights (the pin's frame at pin 0.0): amber (−116, −47) r 86, red (131, 72) r 58.
// Light keys: [loop t, x, y, r] of each light's visible centroid, circle-fitted on every 30 fps frame
// of the pin (fit_pin.py). Beans and crescents are not keyed: they come from the trail (`span`).
// The docked mark is ours, so from 1.17 (collapse) and until 3.03 (expand) the keys bend from the
// pin's path into our dock: amber (8, 2) r 44, red (−40, −14) r 30.
const A_KEYS = [
  [0, -116, -47, 86], [0.3, -116, -47, 86], [0.4, -110, -51, 86], [0.5, -105, -55, 86], [0.57, -98, -59, 85],
  [0.63, -83, -63, 83], [0.7, -54, -66, 81], [0.73, -30, -64, 81], [0.77, 5, -60, 81], [0.8, 39, -49, 70],
  [0.83, 84, -58, 36], [0.87, 112, -52, 25], [0.9, 132, -45, 24], [0.93, 141, -35, 24], [0.97, 145, -24, 25],
  [1.0, 141, -15, 25], [1.03, 135, -10, 26], [1.07, 128, -7, 28], [1.1, 119, -5, 30], [1.13, 106, -4, 33],
  [1.17, 85, -3, 36], [1.23, 44, 0, 40], [1.3, 15, 2, 43], [1.4, AX, AY, PILL.amber.r],
  [2.8, AX, AY, PILL.amber.r], [2.97, AX + 2, AY + 1, 43], [3.03, 20, 7, 35], [3.07, 31, 12, 31], [3.13, 36, 26, 29],
  [3.17, 37, 38, 25], [3.2, 35, 52, 23], [3.23, 24, 70, 24], [3.27, 2, 88, 34], [3.3, -24, 93, 40],
  [3.33, -46, 95, 46], [3.37, -72, 88, 62], [3.4, -96, 70, 76], [3.43, -110, 53, 80], [3.47, -118, 35, 84],
  [3.53, -128, 8, 86], [3.57, -131, -4, 86], [3.63, -130, -23, 86], [3.7, -126, -34, 86], [3.8, -120, -44, 86],
  [3.9, -116, -47, 86], [LOOP, -116, -47, 86],
];
const R_KEYS = [
  [0, 131, 72, 58], [0.3, 131, 72, 58], [0.4, 124, 80, 57], [0.5, 109, 91, 57], [0.57, 92, 99, 57],
  [0.63, 70, 108, 55], [0.7, 40, 116, 46], [0.73, 14, 124, 38], [0.77, -22, 123, 30], [0.8, -55, 118, 27],
  [0.83, -98, 101, 25], [0.87, -124, 86, 25], [0.9, -141, 69, 25], [0.93, -150, 54, 26], [0.97, -152, 41, 28],
  [1.0, -151, 31, 30], [1.03, -148, 23, 32], [1.07, -144, 17, 35], [1.1, -136, 11, 37], [1.13, -122, 4, 39],
  [1.17, -106, -1, 39], [1.23, -76, -7, 36], [1.3, -52, -12, 32], [1.4, RX, RY, PILL.red.r],
  [2.8, RX, RY, PILL.red.r], [2.93, RX - 1, RY - 2, 30], [3.0, -42, -24, 27], [3.03, -44, -34, 25],
  [3.07, -36, -56, 24], [3.1, -18, -72, 26], [3.13, 8, -84, 27], [3.17, 36, -90, 27], [3.2, 62, -89, 27],
  [3.23, 92, -82, 28], [3.27, 130, -64, 38], [3.3, 152, -40, 46], [3.33, 160, -18, 46], [3.37, 162, -5, 50],
  [3.4, 164, 10, 52], [3.47, 160, 34, 52], [3.53, 153, 49, 56], [3.57, 150, 56, 58], [3.67, 142, 65, 58],
  [3.8, 134, 69, 58], [3.9, 131, 72, 58], [LOOP, 131, 72, 58],
];
// how far back in time each light's trail reaches (its stretch), seconds
const A_SPAN = pchip([[0, 0.06], [1.0, 0.06], [1.12, 0.02], [2.9, 0.02], [2.98, 0.06], [3.1, 0.11], [3.28, 0.14], [3.47, 0.06], [LOOP, 0.06]]);
const R_SPAN = pchip([[0, 0.075], [1.0, 0.075], [1.12, 0.025], [2.88, 0.025], [2.95, 0.075], [3.06, 0.16], [3.2, 0.16], [3.32, 0.07], [LOOP, 0.075]]);
// head keys: the trail lies behind the head, so the head runs ahead of the measured centroid
function heads(keys: number[][], span: Track, k: number) {
  const sh = keys.map((q) => [q[0] - 0.25 * evJS(span, q[0]), q[1 + k]]);
  for (let i = 1; i < sh.length; i++) if (sh[i][0] <= sh[i - 1][0]) sh[i][0] = sh[i - 1][0] + 1e-3;
  return pchip(sh);
}
function evJS(tr: Track, x: number) {
  const t = tr.t;
  const n = t.length;
  if (x <= t[0]) return tr.v[0];
  if (x >= t[n - 1]) return tr.v[n - 1];
  let i = 0;
  while (i < n - 2 && x > t[i + 1]) i++;
  const h = t[i + 1] - t[i];
  const u = (x - t[i]) / h;
  return (2 * u ** 3 - 3 * u ** 2 + 1) * tr.v[i] + (u ** 3 - 2 * u ** 2 + u) * h * tr.m[i] + (-2 * u ** 3 + 3 * u ** 2) * tr.v[i + 1] + (u ** 3 - u ** 2) * h * tr.m[i + 1];
}
const rad = (keys: number[][]) => pchip(keys.map((q) => [q[0], q[3]]));

//                     rest  .60   .77   .83   .90  1.00  1.10  1.17  1.23  1.30 | 2.80  3.00  3.10  3.17  3.23  3.30  3.37  3.47  3.57  3.67  3.90  4.40
const TR = {
  cardX: zip([0, 0, 0, 0, 0, -1, -2, -4, -5, PX, PX, PX, PX, -7, -10, -12, 6, 7, 5, 2, 0, 0]),
  cardW: zip([CARD.w, CARD.w, 265, 233, 218, 208, 206, 204, 196, PILL.w, PILL.w, PILL.w, 196, 200, 205, 240, 250, 262, 280, CARD.w, CARD.w, CARD.w]),
  cardH: zip([CARD.h, CARD.h, 160, 141, 132, 126, 124, 123, 123, PILL.h, PILL.h, PILL.h, 123, 125, 135, 143, 150, 158, 167, CARD.h, CARD.h, CARD.h]),
  cardR: zip([CARD.r, CARD.r, 16, 18, 20, 23, 28, 45, 60, PILL.h / 2, PILL.h / 2, PILL.h / 2, 61, 58, 44, 28, 18, 15, CARD.r, CARD.r, CARD.r, CARD.r]),
  roll: zip([CARD.roll, CARD.roll, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.5, -1.5, -2.5, CARD.roll, CARD.roll, CARD.roll]),
  aX: heads(A_KEYS, A_SPAN, 0),
  aY: heads(A_KEYS, A_SPAN, 1),
  aR: rad(A_KEYS),
  rX: heads(R_KEYS, R_SPAN, 0),
  rY: heads(R_KEYS, R_SPAN, 1),
  rR: rad(R_KEYS),
  aSpan: A_SPAN,
  rSpan: R_SPAN,
  // 1 = behind the glass (frosted where it covers it), 0 = on top of it (crisp). Swaps happen while
  // the light is clear of the glass, or (expand) while the slab is turning.
  frostA: pchip([[0, 1], [0.94, 1], [1.0, 0], [3.19, 0], [3.27, 1], [LOOP, 1]]),
  frostR: pchip([[0, 1], [0.94, 1], [1.0, 0], [3.4, 0], [3.49, 1], [LOOP, 1]]),
  // half-revolution about the vertical axis: 180 = blank side, 90 = edge-on, 0 = printed face
  turn: pchip([[2.8, 180], [3.2, 180], [3.23, 162], [3.27, 140], [3.3, 114], [3.315, 90], [3.33, 74], [3.37, 55], [3.4, 47], [3.47, 33], [3.57, 21], [3.67, 12], [3.77, 5], [3.9, 0], [LOOP, 0]]),
};

export function ev(tr: Track, x: number): number {
  'worklet';
  const t = tr.t;
  const n = t.length;
  if (x <= t[0]) return tr.v[0];
  if (x >= t[n - 1]) return tr.v[n - 1];
  let i = 0;
  while (i < n - 2 && x > t[i + 1]) i++;
  const h = t[i + 1] - t[i];
  const s = (x - t[i]) / h;
  const s2 = s * s;
  const s3 = s2 * s;
  return (
    (2 * s3 - 3 * s2 + 1) * tr.v[i] + (s3 - 2 * s2 + s) * h * tr.m[i] + (-2 * s3 + 3 * s2) * tr.v[i + 1] + (s3 - s2) * h * tr.m[i + 1]
  );
}

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}
export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function smooth(a: number, b: number, x: number) {
  'worklet';
  const k = clamp01((x - a) / (b - a));
  return k * k * (3 - 2 * k);
}
export function wrap(t: number) {
  'worklet';
  const m = t % LOOP;
  return m < 0 ? m + LOOP : m;
}

/** A spring released at u = 0 with unit initial speed, normalised to peak at 1 (closed form). */
export function springBump(u: number, response: number, damping: number) {
  'worklet';
  if (u <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const wd = w * Math.sqrt(1 - damping * damping);
  const tp = Math.atan(wd / (damping * w)) / wd;
  const peak = Math.exp(-damping * w * tp) * Math.sin(wd * tp);
  return (Math.exp(-damping * w * u) * Math.sin(wd * u)) / peak;
}

// 1 while the scene rests, 0 while it morphs: weights the idle drift so every residual is zero at
// the phase boundaries.
function restWeight(t: number) {
  'worklet';
  return 1 - smooth(0.12, 0.34, t) + smooth(3.6, 3.9, t);
}

const W = (2 * Math.PI) / LOOP;
/** Idle drift of a light (pt), whole cycles per loop. */
function driftA(t: number) {
  'worklet';
  return [4 * Math.sin(W * t), 3 * Math.sin(2 * W * t)];
}
function driftR(t: number) {
  'worklet';
  return [5 * Math.sin(2 * W * t), 4 * (1 - Math.cos(2 * W * t))];
}

/** The dock: the lights overshoot into each other by ~6 pt and settle (jelly), from τ = 1.30. */
function dockJelly(t: number) {
  'worklet';
  if (t < T.dock || t > T.expand0) return 0;
  return 3 * springBump(t - T.dock, 0.4, 0.55) * (1 - smooth(2.5, 2.8, t));
}

export type Mods = {
  /** τ velocity (τ seconds per second): 1 in autoPlay, the scrub speed under a finger. */
  nu: number;
  driftGain: number;
  yaw: number; // deg, added to the turn
  press: number; // card scale
  parallax: number; // pt, the lights shift against the yaw drag
  aOff: number[]; // [x, y, vx, vy]: a dragged light's offset and velocity
  rOff: number[];
};

export const AUTO_MODS: Mods = { nu: 1, driftGain: 1, yaw: 0, press: 1, parallax: 0, aOff: [0, 0, 0, 0], rOff: [0, 0, 0, 0] };

export type Scene = {
  t: number;
  cx: number;
  cy: number;
  w: number;
  h: number;
  r: number;
  roll: number;
  turn: number;
  scale: number;
  a: number[]; // TRAIL × (x, y, r)
  rd: number[];
  frostA: number;
  frostR: number;
  dock: number; // 0 rest colours … 1 the mark's flat colours
  tint: number; // glass whiteness
  face: number; // the printed face's visibility (turn), multiplies everything below
  textName: number;
  textNumber: number;
  textChip: number; // chip and rings alpha
  chipLines: number; // chip inner lines, trim 0..1
  rings: number; // rings, trim 0..1
  contactless: number;
  caption: number; // 0 "Hold near reader" … 1 "Paid $24.80"
  pulse: number; // seconds since the pay pulse (−1 = none)
  flash: number; // lens flash 0..1
  bright: number; // page brighten 0..1
  sheen: number; // specular band across the printed face, −1 none, else 0..1 travel
  glint: number;
  crawl: number; // specular crawl along the top rim, 0..1 per loop
};

function lightAt(tr: number, xs: Track, ys: Track, drift: (t: number) => number[], gain: number, sign: number, dirX: number, dirY: number) {
  'worklet';
  const tw = wrap(tr);
  const d = drift(tw);
  const k = restWeight(tw) * gain;
  const j = dockJelly(tw) * sign;
  return [HERO.x + ev(xs, tw) + d[0] * k + j * dirX, HERO.y + ev(ys, tw) + d[1] * k + j * dirY];
}

/** The whole frame at loop time τ. */
export function sceneAt(tau: number, m: Mods): Scene {
  'worklet';
  const t = wrap(tau);
  const rw = restWeight(t) * m.driftGain;
  const float = Math.sin(W * t);

  // glass
  let turn = t < T.collapse1 ? 0 : t < T.expand0 ? 180 : ev(TR.turn, t);
  turn += m.yaw;
  const pulse = t >= T.pulse && t < T.expand0 ? t - T.pulse : -1;
  const breath = pulse >= 0 ? 0.04 * springBump(pulse, 0.4, 0.6) : 0;

  // the mark's dock direction (amber minus red, unit)
  const ddx = AX - RX;
  const ddy = AY - RY;
  const dl = Math.hypot(ddx, ddy);
  const ux = ddx / dl;
  const uy = ddy / dl;

  const a: number[] = [];
  const rd: number[] = [];
  const aR = ev(TR.aR, t);
  const rR = ev(TR.rR, t);
  const aSpan = ev(TR.aSpan, t) * m.nu;
  const rSpan = ev(TR.rSpan, t) * m.nu;
  for (let k = 0; k < TRAIL; k++) {
    const f = k / (TRAIL - 1);
    const taper = 1 - 0.3 * f;
    const pa = lightAt(t - f * aSpan, TR.aX, TR.aY, driftA, m.driftGain, -1, ux, uy);
    const pr = lightAt(t - f * rSpan, TR.rX, TR.rY, driftR, m.driftGain, 1, ux, uy);
    const ta = f * 0.056;
    a.push(pa[0] + m.parallax + m.aOff[0] - m.aOff[2] * ta, pa[1] + m.aOff[1] - m.aOff[3] * ta, aR * taper);
    rd.push(pr[0] + m.parallax * 1.3 + m.rOff[0] - m.rOff[2] * ta, pr[1] + m.rOff[1] - m.rOff[3] * ta, rR * taper);
  }

  // card contents: `face` fades the printed face (collapse: in order name, contactless, chip and
  // rings, number; expand: as the face comes round), the trims draw the icons back on
  let face = 1;
  let textName = 1;
  let textNumber = 1;
  let textChip = 1;
  let chipLines = 1;
  let rings = 1;
  let contactless = 1;
  if (t >= T.collapse0 && t < T.expand0) {
    textName = 1 - seg(t, 1.06, 1.15);
    contactless = 1 - seg(t, 1.08, 1.17);
    textChip = 1 - seg(t, 1.1, 1.19);
    textNumber = 1 - seg(t, 1.14, 1.25);
  } else if (t >= T.expand0 && t < T.expand1) {
    face = turn < 90 ? clamp01((Math.cos((turn * Math.PI) / 180) - 0.05) / 0.25) : 0;
    rings = easeInOutCubic(seg(t, 3.37, 3.56));
    chipLines = easeInOutCubic(seg(t, 3.5, 3.72));
    contactless = easeOutCubic(seg(t, 3.6, 3.76));
  }
  if (m.yaw !== 0 && t < T.collapse0) face = clamp01((Math.cos((turn * Math.PI) / 180) - 0.15) / 0.4);

  // captions
  const caption = t < T.expand0 ? easeOutCubic(seg(t, 1.3, 1.5)) : 1 - easeOutCubic(seg(t, 3.57, 3.77));

  // colours: the lights' rest gradients flatten into the mark's colours as they dock
  const dock = t < T.expand0 ? smooth(1.0, 1.3, t) : 1 - smooth(3.0, 3.45, t);
  const morph = t < T.expand0 ? smooth(0.6, 1.3, t) : 1 - smooth(3.1, 3.6, t);

  return {
    t,
    cx: HERO.x + ev(TR.cardX, t),
    cy: HERO.y + 2 * float * rw,
    w: ev(TR.cardW, t),
    h: ev(TR.cardH, t),
    r: ev(TR.cardR, t),
    roll: ev(TR.roll, t) + 0.5 * Math.sin(W * t) * rw,
    turn,
    scale: m.press * (1 + breath),
    a,
    rd,
    frostA: ev(TR.frostA, t),
    frostR: ev(TR.frostR, t),
    dock,
    tint: 0.062 + 0.045 * morph,
    face,
    textName,
    textNumber,
    textChip,
    chipLines,
    rings,
    contactless,
    caption,
    pulse,
    flash: pulse >= 0 ? Math.exp(-pulse / 0.09) * seg(pulse, -0.001, 0.03) : 0,
    bright: pulse >= 0 ? Math.exp(-pulse / 0.08) : 0,
    sheen: t >= 3.37 && t <= 3.67 ? (t - 3.37) / 0.3 : -1,
    glint: t >= 3.62 && t <= 3.8 ? Math.sin(Math.PI * seg(t, 3.62, 3.8)) : 0,
    crawl: t / LOOP,
  };
}

/**
 * The glass's projective map, card-local (origin top-left, pt) → screen pt, row-major 3 × 3.
 * Turn about the vertical axis with perspective, then the roll in the screen plane.
 */
export function cardMatrix(s: Scene): number[] {
  'worklet';
  const th = (s.turn * Math.PI) / 180;
  const ct = Math.cos(th);
  const st = Math.sin(th);
  const ro = (s.roll * Math.PI) / 180;
  const cr = Math.cos(ro);
  const sr = Math.sin(ro);
  const g = st / PERSPECTIVE;
  // centred local (x, y) → screen
  const a = cr * ct + s.cx * g;
  const b = -sr;
  const d = sr * ct + s.cy * g;
  const e = cr;
  const k = s.scale;
  const hx = -k * (s.w / 2);
  const hy = -k * (s.h / 2);
  // H = Hc · [[k,0,hx],[0,k,hy],[0,0,1]]
  return [a * k, b * k, a * hx + b * hy + s.cx, d * k, e * k, d * hx + e * hy + s.cy, g * k, 0, g * hx + 1];
}

/** Screen pt → centred card-local pt (the inverse of cardMatrix with the centring folded in). */
export function cardInverse(s: Scene): number[] {
  'worklet';
  const th = (s.turn * Math.PI) / 180;
  const ct = Math.cos(th);
  const st = Math.sin(th);
  const ro = (s.roll * Math.PI) / 180;
  const cr = Math.cos(ro);
  const sr = Math.sin(ro);
  const g = st / PERSPECTIVE;
  const k = s.scale;
  // centred local with scale: H = [[a k, b k, cx],[d k, e k, cy],[g k, 0, 1]]
  const m00 = (cr * ct + s.cx * g) * k;
  const m01 = -sr * k;
  const m02 = s.cx;
  const m10 = (sr * ct + s.cy * g) * k;
  const m11 = cr * k;
  const m12 = s.cy;
  const m20 = g * k;
  const m21 = 0;
  const m22 = 1;
  const c00 = m11 * m22 - m12 * m21;
  const c01 = m02 * m21 - m01 * m22;
  const c02 = m01 * m12 - m02 * m11;
  const c10 = m12 * m20 - m10 * m22;
  const c11 = m00 * m22 - m02 * m20;
  const c12 = m02 * m10 - m00 * m12;
  const c20 = m10 * m21 - m11 * m20;
  const c21 = m01 * m20 - m00 * m21;
  const c22 = m00 * m11 - m01 * m10;
  const det = m00 * c00 + m01 * c10 + m02 * c20;
  const inv = 1 / det;
  return [c00 * inv, c01 * inv, c02 * inv, c10 * inv, c11 * inv, c12 * inv, c20 * inv, c21 * inv, c22 * inv];
}

/** The morph progress p (0 card … 1 pill) a timeline position reads as. */
export function progressOf(tau: number) {
  'worklet';
  const t = wrap(tau);
  if (t < T.collapse0 || t >= T.expand1) return 0;
  if (t <= T.collapse1) return (t - T.collapse0) / (T.collapse1 - T.collapse0);
  if (t < T.expand0) return 1;
  return 1 - (t - T.expand0) / (T.expand1 - T.expand0);
}
