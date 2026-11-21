// Shape Shift Viewfinder: the autoplay timeline (Fable spec §4) as a pure function of the loop time, so every frame
// of the loop is reproducible and the three stacks can match it frame for frame. Times are in seconds; positions are
// stage points, the shape's centre as an offset from its rest position.

import { QN, QSTEP, restWeights } from './ssv-shapes';
import { BAND_SPRINGS, C, JELLY, SPRING } from './ssv-theme';

export const LOOP = 10;

/** 0 before a, 1 after b. */
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
  const e = Math.exp(-w0 * t);
  return to + e * (x0 + (v0 + w0 * x0) * t);
};

/**
 * One exact step of an underdamped spring: x is the offset from the target, v the velocity. Returns [x, v] after dt.
 * Used for the live band springs, so a retarget keeps every band's velocity.
 */
export const springStep = (x: number, v: number, d: number, k: number, dt: number): [number, number] => {
  'worklet';
  const w0 = Math.sqrt(k);
  const z = Math.min(d / (2 * w0), 0.999);
  const wd = w0 * Math.sqrt(1 - z * z);
  const e = Math.exp(-z * w0 * dt);
  const c = Math.cos(wd * dt);
  const s = Math.sin(wd * dt);
  return [e * (x * c + ((v + z * w0 * x) / wd) * s), e * (v * c - ((k * x + z * w0 * v) / wd) * s)];
};

// ---------- colours ----------

export const rgbOf = (hex: string): [number, number, number] => {
  'worklet';
  if (hex.charAt(0) === '#') {
    return [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
  }
  const m = hex.replace(/[^0-9.,]/g, '').split(',');
  return [Number(m[0]), Number(m[1]), Number(m[2])];
};
const h2 = (v: number) => {
  'worklet';
  const s = Math.round(clamp(v, 0, 255)).toString(16);
  return s.length < 2 ? '0' + s : s;
};
export const hexOf = (r: number, g: number, b: number) => {
  'worklet';
  return '#' + h2(r) + h2(g) + h2(b);
};
export const mixHex = (a: string, b: string, k: number) => {
  'worklet';
  if (k <= 0) return a;
  if (k >= 1) return b;
  const x = rgbOf(a);
  const y = rgbOf(b);
  return hexOf(mix(x[0], y[0], k), mix(x[1], y[1], k), mix(x[2], y[2], k));
};
export const scaleHex = (a: string, f: number) => {
  'worklet';
  const x = rgbOf(a);
  return hexOf(x[0] * f, x[1] * f, x[2] * f);
};
export const lightenHex = (a: string, f: number) => {
  'worklet';
  const x = rgbOf(a);
  return hexOf(x[0] + (255 - x[0]) * f, x[1] + (255 - x[1]) * f, x[2] + (255 - x[2]) * f);
};

// ---------- the script ----------

/** Six morph beats; custom collections cycle in order, then return to their initial shape. */
const MORPHS = [0.7, 1.5, 2.3, 6.0, 7.7, 8.8];
/** Where each autoplay tap lands on the rim (rad, clockwise from the top): 300°, 60°, 180°, 240°, 120°, 0°. */
export const TAPS = [300, 60, 180, 240, 120, 0].map((d) => (d * Math.PI) / 180);
/** [start, from, to] of the colour floods; the second one launches at the wall contact of the throw. */
const FLOODS: [number, string, string][] = [
  [2.4, C.teal, C.forest],
  [4.27, C.forest, C.clay],
  [7.15, C.clay, C.pink],
  [8.95, C.pink, C.teal], // 150 ms into morph 6, so the thumbnail (≈ 9.05 s) has its contours over the pink
];
export const FLOOD_DUR = 0.58;
export const FLOOD_REACH = 560;

/** A flood's leading edge (pt from the outline) at progress u. The solid colour trails it by up to 52 pt until it is past 260. */
export const floodFront = (u: number) => {
  'worklet';
  // a burst: fastest at launch, so colour leaves the outline the instant it is hit or tapped
  return FLOOD_REACH * (1 - Math.pow(1 - clamp(u, 0, 1), 1.6));
};
export const floodFill = (front: number) => {
  'worklet';
  return Math.max(front - 52 * (1 - seg(front, 120, 260)), 0);
};
/** The three crisp contours of a flood: offset behind the front (pt), stroke (pt), alpha. Gone by 260 pt. */
export const ECHOES = [
  { off: 0, w: 3, a: 0.7 },
  { off: 26, w: 2.5, a: 0.4 },
  { off: 52, w: 2, a: 0.2 },
] as const;
export const echoFade = (front: number) => {
  'worklet';
  return 1 - seg(front, 190, 260);
};

/** Picture framings: plate point (u, v) at the shape's centre, plate size (pt), rotation (deg). */
export const FRAMINGS = {
  A: [0.473, 0.42, 615, 0],
  B: [0.6, 0.62, 500, 0],
  C: [0.43, 0.5, 560, -35],
} as const;

export const mixFraming = (a: readonly number[], b: readonly number[], k: number): [number, number, number, number] => {
  'worklet';
  return [mix(a[0], b[0], k), mix(a[1], b[1], k), mix(a[2], b[2], k), mix(a[3], b[3], k)];
};

export const framingAt = (t: number) => {
  'worklet';
  if (t < 1.5) return mixFraming(FRAMINGS.A, FRAMINGS.A, 0);
  if (t < 4.4) return mixFraming(FRAMINGS.A, FRAMINGS.B, easeInOut(seg(t, 1.5, 3.0)));
  if (t < 8.8) return mixFraming(FRAMINGS.B, FRAMINGS.C, easeInOut(seg(t, 4.4, 5.4)));
  return mixFraming(FRAMINGS.C, FRAMINGS.A, easeInOut(seg(t, 8.8, 9.6)));
};

/** The hand-held sway of the picture: whole cycles of the loop, so it repeats. */
export const swayAt = (t: number) => {
  'worklet';
  const p = (2 * Math.PI * t) / LOOP;
  // every term is 0 at t = 0, so the live screen can fade the sway out and hand back to the loop without a jump
  return [3 * Math.sin(2 * p) + 1 * Math.sin(5 * p), 3 * Math.sin(3 * p) + 0.8 * Math.sin(4 * p), 0.4 * Math.sin(2 * p)];
};

/** The leaf shadow drifting over the picture (whole cycles of the loop): x, y (pt) and rotation (deg). */
export const shadeAt = (t: number): [number, number, number] => {
  'worklet';
  const p = (2 * Math.PI * t) / LOOP;
  return [26 * Math.sin(p) + 9 * Math.sin(2 * p + 1.3), 12 * Math.cos(p), 2.5 * Math.sin(p + 0.9)];
};

/**
 * The throw (replaces the first drag): the shape, at k 0.72, is flung from the rest centre toward (318, 330) (screen) at
 * 1150 pt/s and hits the stage's right wall at 4.27 s, flattens about 34 pt against it for 50 ms and rebounds, as a
 * ζ 0.35 spring, to (262, 340). Positions are offsets from the rest centre, which is (201, 382) on the 402 × 874 screen.
 */
const THROW_LEN = Math.hypot(117, -52);
const THROW_DIR = [117 / THROW_LEN, -52 / THROW_LEN] as const;
const THROW_C0 = 100.7; // along the ray, rest → the outline touching the wall (cookie, k 0.72 × 1.03 lifted)
const THROW_PEN = 37.2; // along the ray: 34 pt across the wall
const THROW_BACK = 12; // the wind-up, pulling away from the wall
const REBOUND = [61, -42] as const;
const RB_W = (2 * Math.PI) / 0.3;
const RB = { d: 2 * 0.35 * RB_W, k: RB_W * RB_W };
const DRAG_B = [-71, 88];

const reboundAt = (t: number): [number, number] => {
  'worklet';
  const c = THROW_C0 + THROW_PEN;
  return [
    springAt(t - 4.35, THROW_DIR[0] * c, REBOUND[0], 0, RB.d, RB.k),
    springAt(t - 4.35, THROW_DIR[1] * c, REBOUND[1], 0, RB.d, RB.k),
  ];
};

/** The drag in the loop: the shape's centre as an offset from rest. */
export const dragAt = (t: number): [number, number] => {
  'worklet';
  if (t < 4.0 || t >= 5.55) return [0, 0];
  if (t < 4.35) {
    let s: number;
    if (t < 4.1) s = -THROW_BACK * easeInOut(seg(t, 4.0, 4.1));
    else if (t < 4.27) s = -THROW_BACK + (THROW_C0 + THROW_BACK) * Math.pow(seg(t, 4.1, 4.27), 1.69);
    else if (t < 4.3) s = THROW_C0 + THROW_PEN * seg(t, 4.27, 4.3);
    else s = THROW_C0 + THROW_PEN;
    return [THROW_DIR[0] * s, THROW_DIR[1] * s];
  }
  if (t < 4.85) return reboundAt(t);
  if (t < 5.25) {
    const p0 = reboundAt(4.85);
    const k = easeInOut(seg(t, 4.85, 5.25));
    return [mix(p0[0], DRAG_B[0], k), mix(p0[1], DRAG_B[1], k)];
  }
  const k = easeInOut(seg(t, 5.25, 5.55));
  return [mix(DRAG_B[0], 0, k), mix(DRAG_B[1], 0, k)];
};

/**
 * The jelly through the loop, integrated once here (a spring that follows the drag's velocity) and stored at 120 Hz:
 * [Jx, Jy, Dx, Dy] per sample. J is the deformation vector (stretch along it), D the latched direction of motion.
 */
const JT_HZ = 120;
const J_TAB: number[] = (() => {
  const out: number[] = [];
  const dt = 1 / 2400;
  let jx = 0;
  let jy = 0;
  let vx = 0;
  let vy = 0;
  let dx = 1;
  let dy = 0;
  let prev = dragAt(0);
  const per = Math.round(2400 / JT_HZ);
  for (let i = 0; i <= LOOP * 2400; i++) {
    const t = i * dt;
    if (i % per === 0) out.push(jx, jy, dx, dy);
    const p = dragAt(t + dt);
    let tx = ((p[0] - prev[0]) / dt) * JELLY.perSpeed;
    let ty = ((p[1] - prev[1]) / dt) * JELLY.perSpeed;
    prev = p;
    const m = Math.hypot(tx, ty);
    if (m > JELLY.max) {
      tx = (tx / m) * JELLY.max;
      ty = (ty / m) * JELLY.max;
    }
    if (m > 0.01) {
      dx = tx / m;
      dy = ty / m;
    }
    vx += (JELLY.k * (tx - jx) - JELLY.d * vx) * dt;
    vy += (JELLY.k * (ty - jy) - JELLY.d * vy) * dt;
    jx += vx * dt;
    jy += vy * dt;
  }
  return out;
})();

const jellyAt = (t: number): [number, number, number, number] => {
  'worklet';
  const f = t * JT_HZ;
  const i = Math.min(Math.floor(f), LOOP * JT_HZ - 1);
  const u = f - i;
  const a = i * 4;
  const b = (i + 1) * 4;
  const dx = mix(J_TAB[a + 2], J_TAB[b + 2], u);
  const dy = mix(J_TAB[a + 3], J_TAB[b + 3], u);
  const dm = Math.hypot(dx, dy) || 1;
  const jx = mix(J_TAB[a], J_TAB[b], u);
  const jy = mix(J_TAB[a + 1], J_TAB[b + 1], u);
  const strength = (jx * dx + jy * dy) / dm;
  const cap = Math.min(1, JELLY.max / Math.max(Math.abs(strength), JELLY.max));
  return [jx * cap, jy * cap, dx / dm, dy / dm];
};

/**
 * The iris blink at time s since the shutter fired, never empty: [radius factor, twist (deg), flash alpha (white inside),
 * picture dim, disc scale, contour distance (pt), contour alpha]. The aperture closes to 0.24 of its radius with a 40° twist
 * in 110 ms, flashes white (α 0.85) for 50 ms, sends one white contour 0 → 70 pt in 260 ms, then springs open.
 */
export const IRIS_MIN = 0.24;
export const irisAt = (s: number): [number, number, number, number, number, number, number] => {
  'worklet';
  if (s < 0) return [1, 0, 0, 1, 1, 0, 0];
  const cu = clamp((s - 0.11) / 0.26, 0, 1);
  const contour = s < 0.11 ? 0 : 70 * easeOut(cu);
  const contourA = s < 0.11 || s > 0.37 ? 0 : 0.85 * (1 - cu);
  if (s < 0.11) {
    const k = easeIn(s / 0.11);
    return [1 - (1 - IRIS_MIN) * k, 40 * k, 0, 1 - 0.4 * easeOut(s / 0.11), mix(1, 0.86, easeOut(s / 0.11)), 0, 0];
  }
  if (s < 0.16) return [IRIS_MIN, 40, 0.85, 0.6, 0.86, contour, contourA];
  const o = s - 0.16;
  return [
    IRIS_MIN + (1 - IRIS_MIN) * springAt(o, 0, 1, 0, SPRING.pop.d, SPRING.pop.k),
    springAt(o, 40, 0, 0, SPRING.pop.d, SPRING.pop.k),
    0,
    mix(0.6, 1, easeOut(seg(o, 0, 0.18))),
    springAt(o, 0.86, 1, 0, SPRING.snap.d, SPRING.snap.k),
    contour,
    contourA,
  ];
};
export const IRIS_DUR = 0.62;

/** Everything the screen draws, for one moment. */
export type Frame = {
  w: number[]; // band weights, QN delay copies × 6 bands × shape count
  tapA: number; // where the last tap landed on the rim (rad, clockwise from the top)
  dent: number; // the rim's dent under the finger (pt)
  rings: number[]; // touch rings: [alpha, angle, scale] × 2
  press: number; // tap press scale
  x: number; // centre offset from rest (stage pt)
  y: number;
  k: number; // shape scale
  rot: number; // shape rotation (deg), lean excluded
  jx: number; // jelly vector
  jy: number;
  dx: number; // latched motion direction
  dy: number;
  lift: number; // 0 flat, 1 lifted (e2), 2 dragged (e4)
  fr: [number, number, number, number]; // framing
  sx: number; // sway
  sy: number;
  sr: number;
  lx: number; // the picture lagging the outline while it moves (pt)
  ly: number;
  shx: number; // the leaf shadow's drift
  shy: number;
  shr: number;
  flip: number; // 0 back camera, 1 front
  iris: number;
  twist: number;
  flash: number;
  blinkD: number;
  blinkA: number;
  dim: number;
  disc: number; // shutter disc scale
  base: string; // stage colour under the fronts
  f1c: string; // older front
  f1u: number; // its progress 0..1, < 0 when none
  f2c: string; // newer front
  f2u: number;
  dot: string; // colour button's dot
  sheet: number; // 0 hidden .. 1 open
  seg: number; // 0 Dynamic .. 1 Solid
  ring: number; // solid row: ring position (swatch index)
  dring: number; // dynamic row: ring position
  pressIdx: number; // solid swatch being pressed, -1 none
  pressS: number;
  hint: [number, number, number]; // alpha of the three hints
};

/** The poster size: the cut-out as wide as the stage allows (R 150 × 1.26 = a 378 pt circle on the 390 pt stage). */
export const K_POSTER = 1.26;
/** The thumbnail (≈ 9.0 s): the clover, narrower than the circle, opens to the creative director's k 1.36 (a 368 pt wide hero). */
export const K_THUMB = 1.36;
/** How far the picture trails the outline: velocity × 60 ms, at most 10 pt. */
export const lagOf = (vx: number, vy: number): [number, number] => {
  'worklet';
  const m = Math.hypot(vx, vy);
  if (m < 1e-3) return [0, 0];
  const l = Math.min(m * 0.06, 10);
  return [(-vx / m) * l, (-vy / m) * l];
};

export const frameAt = (tIn: number, shapeCount = 6, initialShape = 0): Frame => {
  'worklet';
  const phase = ((tIn % LOOP) + LOOP) % LOOP;
  const t = phase >= 9.6 ? 0 : phase;

  // the outline: superposed band springs, one step response per morph (exact retargeting); QN copies, each QSTEP later
  const one = (tt: number, out: number[], at: number) => {
    'worklet';
    const rest = restWeights(initialShape, shapeCount);
    const n6 = 6 * shapeCount;
    for (let i = 0; i < n6; i++) out[at + i] = rest[i];
    for (let m = 0; m < MORPHS.length; m++) {
      const s0 = MORPHS[m];
      if (tt < s0) continue;
      const from = (initialShape + m) % shapeCount;
      const to = m === MORPHS.length - 1 ? initialShape : (initialShape + m + 1) % shapeCount;
      for (let b = 0; b < 6; b++) {
        const sp = BAND_SPRINGS[b];
        const s = springAt(tt - s0, 0, 1, 0, sp.d, sp.k);
        out[at + b * shapeCount + from] -= s;
        out[at + b * shapeCount + to] += s;
      }
    }
  };
  const w: number[] = new Array(QN * 6 * shapeCount);
  for (let q = 0; q < QN; q++) one(t - q * QSTEP, w, q * 6 * shapeCount);

  // tap: the press (dent + ring) and the squash of the whole shape
  let press = 1;
  let tapIdx = -1;
  for (let m = 0; m < MORPHS.length; m++) {
    const s0 = MORPHS[m];
    if (t >= s0 - 0.1) tapIdx = m;
    if (t >= s0 - 0.1 && t < s0) press -= 0.04 * easeOut(seg(t, s0 - 0.1, s0));
    if (t >= s0) press -= springAt(t - s0, 0.04, 0, 0, SPRING.pop.d, SPRING.pop.k);
  }
  let tapA = TAPS[0];
  let dent = 0;
  const rings = [0, 0, 1, 0, 0, 1];
  if (tapIdx >= 0) {
    const s0 = MORPHS[tapIdx];
    tapA = TAPS[tapIdx];
    dent = 10 * (t < s0 ? easeOut(seg(t, s0 - 0.1, s0 - 0.01)) : Math.max(0, springAt(t - s0, 1, 0, 0, SPRING.snap.d, SPRING.snap.k)));
    rings[0] = t < s0 ? 0.45 : 0.45 * (1 - seg(t, s0, s0 + 0.24));
    rings[1] = tapA;
    rings[2] = mix(0.6, 1, easeOut(seg(t, s0 - 0.1, s0 - 0.04)));
  }
  // the pinch: two rings on opposite sides of the rim, held while the fingers move, then 240 ms out
  if (t >= 3.1 && t < 4.0) {
    const a = t < 3.75 ? 0.45 : 0.45 * (1 - seg(t, 3.75, 3.99));
    rings[3] = a;
    rings[4] = Math.PI / 4;
    rings[5] = mix(0.6, 1, easeOut(seg(t, 3.1, 3.16)));
    if (rings[0] <= 0) {
      rings[0] = a;
      rings[1] = (5 * Math.PI) / 4;
      rings[2] = rings[5];
    }
  }

  // pinch: 1 → 0.70, settles at 0.72 (never below ~0.7×); the poster beat opens the crop wide, holds through the last morph
  let k = 1;
  if (t >= 3.1 && t < 3.7) k = mix(1, 0.7, easeInOut(seg(t, 3.1, 3.7)));
  else if (t >= 3.7 && t < 5.5) k = springAt(t - 3.7, 0.7, 0.72, 0, SPRING.pop.d, SPRING.pop.k);
  else if (t >= 5.5 && t < 7.6) k = springAt(t - 5.5, 0.72, 1, 0, SPRING.pop.d, SPRING.pop.k);
  else if (t >= 7.6 && t < 8.8) k = mix(1, K_POSTER, easeInOut(seg(t, 7.6, 7.95)));
  // morph 6 grows into the thumbnail: the clover opens to K_THUMB as it overshoots, then the loop settles back to 1
  else if (t >= 8.8 && t < 9.15) k = mix(K_POSTER, K_THUMB, easeInOut(seg(t, 8.8, 8.98)));
  else if (t >= 9.15) k = mix(K_THUMB, 1, easeInOut(seg(t, 9.15, 9.55)));

  // lift: e2 from the pinch, e4 while dragged, back down after the drag
  let lift = 0;
  if (t >= 3.1 && t < 4.0) lift = easeOut(seg(t, 3.1, 3.22));
  else if (t >= 4.0 && t < 5.3) lift = 1 + easeOut(seg(t, 4.0, 4.12));
  else if (t >= 5.3 && t < 5.9) lift = 2 * (1 - easeInOut(seg(t, 5.3, 5.9)));

  const p = dragAt(t);
  const pa = dragAt(t + 0.004);
  const pb = dragAt(t - 0.004);
  const lag = lagOf((pa[0] - pb[0]) / 0.008, (pa[1] - pb[1]) / 0.008);
  const j = jellyAt(t);
  const fr = framingAt(t);
  const sw = swayAt(t);
  const sh = shadeAt(phase);
  const restEnvelope = 1 - easeInOut(seg(t, 9.4, 9.6));
  const ir = irisAt(t - 8.3);

  // colour floods
  let base: string = C.teal;
  let f1c: string = C.teal;
  let f1u = -1;
  let dot: string = C.teal;
  for (let i = 0; i < FLOODS.length; i++) {
    const s0 = FLOODS[i][0];
    if (t < s0) break;
    const u = (t - s0) / FLOOD_DUR;
    if (u < 1) {
      base = FLOODS[i][1];
      f1c = FLOODS[i][2];
      f1u = u;
    } else {
      base = FLOODS[i][2];
    }
    dot = mixHex(FLOODS[i][1], FLOODS[i][2], seg(t, s0 + 0.2, s0 + 0.4));
  }

  // the Background Color sheet
  let sheet = 0;
  if (t >= 6.5 && t < 7.6) sheet = springAt(t - 6.5, 0, 1, 0, SPRING.glide.d, SPRING.glide.k);
  else if (t >= 7.6) sheet = Math.max(0, springAt(t - 7.6, 1, 0, 0, SPRING.glide.d, SPRING.glide.k));
  const segP = t >= 6.9 && t < 8.6 ? springAt(t - 6.9, 0, 1, 0, SPRING.snap.d, SPRING.snap.k) : 0;
  const ring = t < 7.1 || t >= 8.6 ? 1 : springAt(t - 7.1, 1, 0, 0, SPRING.snap.d, SPRING.snap.k);
  let pressS = 1;
  if (t >= 7.1 && t < 7.18) pressS = mix(1, 0.92, easeOut(seg(t, 7.1, 7.18)));
  else if (t >= 7.18 && t < 7.8) pressS = springAt(t - 7.18, 0.92, 1, 0, SPRING.snap.d, SPRING.snap.k);

  // hints
  const h0 = t < 3.0 ? 1 : t < 3.2 ? 1 - easeOut(seg(t, 3.0, 3.2)) : t < 9.3 ? 0 : easeOut(seg(t, 9.3, 9.5));
  const h1 = t < 3.0 ? 0 : t < 3.9 ? easeOut(seg(t, 3.0, 3.2)) : 1 - easeOut(seg(t, 3.9, 4.1));
  const h2v = t < 3.9 ? 0 : t < 5.5 ? easeOut(seg(t, 3.9, 4.1)) : 1 - easeOut(seg(t, 5.5, 5.7));

  return {
    w,
    tapA,
    dent,
    rings,
    press,
    x: p[0],
    y: p[1],
    k,
    rot: 0,
    jx: j[0],
    jy: j[1],
    dx: j[2],
    dy: j[3],
    lift,
    fr,
    sx: sw[0] * restEnvelope,
    sy: sw[1] * restEnvelope,
    sr: sw[2] * restEnvelope,
    lx: lag[0],
    ly: lag[1],
    shx: sh[0],
    shy: sh[1],
    shr: sh[2],
    flip: 0,
    iris: ir[0],
    twist: ir[1],
    flash: ir[2],
    blinkD: ir[5],
    blinkA: ir[6],
    dim: ir[3],
    disc: ir[4],
    base,
    f1c,
    f1u,
    f2c: base,
    f2u: -1,
    dot,
    sheet,
    seg: segP,
    ring,
    dring: 0,
    pressIdx: t >= 7.1 && t < 7.8 ? 0 : -1,
    pressS,
    hint: [h0, h1, h2v],
  };
};

/** The shape on screen at loop time t (its index once the morph has landed). */
export const shapeAt = (t: number, shapeCount = 6, initialShape = 0) => {
  'worklet';
  let s = initialShape;
  for (let m = 0; m < MORPHS.length; m++) {
    if (t >= MORPHS[m]) s = m === MORPHS.length - 1 ? initialShape : (initialShape + m + 1) % shapeCount;
  }
  return s;
};
