/*
 The tear, as geometry (a port of TornPhotoDeleteModel.swift; the numbers are the same). Everything is in
 the card's own coordinates in points (origin at its top-left corner). All functions are worklets.

 A tear starts at S on the card's edge and runs along the unit vector u for `length`; n is u turned a
 quarter turn (u = down gives n = left). The cut is a jagged line: a point every 6 pt along u, pushed
 sideways along n by a seeded noise (±3 pt grain over a ±5 pt wander), tapering to nothing at the tip T.

 The card is cut into three regions:
   side +1   the +n side of the cut, behind the line through T across u (the left flap when u points down);
   side −1   the −n side;
   rest      everything past that line (the part not yet torn), drawn unchanged.
 A flap is sheared away from the cut: a point p moves by side · open · n · (length − u·(p − S)), so it moves
 most at the card's edge and not at all on the line through T. Once the cut has crossed the card
 (commit), each flap also falls: a small pop, gravity, a drift outwards and a spin the same way.
 */

export type Tear = {
  /** Start on the card's edge and direction (degrees, 90 = down), in card points. */
  sx: number;
  sy: number;
  angle: number;
  length: number;
  /** How far the flaps are sheared apart (0 = shut). */
  open: number;
  seed: number;
  /** Seconds since the tear crossed the card while its pieces fall, or -1. */
  fall: number;
};

export type CutPoint = { x: number; y: number; core: number };

export const STEP = 6;
export const LEAD = 24;

export function dir(t: Tear) {
  'worklet';
  const a = (t.angle * Math.PI) / 180;
  const ux = Math.cos(a);
  const uy = Math.sin(a);
  return { ux, uy, nx: -uy, ny: ux };
}

export function tipOf(t: Tear) {
  'worklet';
  const { ux, uy } = dir(t);
  return { x: t.sx + ux * t.length, y: t.sy + uy * t.length };
}

/** Seeded noise: the same numbers as the Swift and Kotlin versions (32-bit integer hash). */
export function hash(i: number, seed: number) {
  'worklet';
  let x = (Math.imul(i, 374761393) + Math.imul(seed, 668265263)) | 0;
  x = Math.imul(x ^ (x >>> 13), 1274126177);
  x ^= x >>> 16;
  return (x >>> 0) / 4294967296;
}

/** The sideways offset of the cut's i-th point: ±3 pt grain over a ±5 pt wander that turns every 5 points. */
export function noiseOffset(i: number, seed: number) {
  'worklet';
  const j = Math.floor(i / 5);
  const f = (i % 5) / 5;
  const s = f * f * (3 - 2 * f);
  const a = hash(j + 9000, seed);
  const b = hash(j + 9001, seed);
  const wander = (a + (b - a) * s - 0.5) * 10;
  return wander + (hash(i, seed) - 0.5) * 6;
}

/** The cut's points from just outside the edge to the tip, with the width of the white paper core. */
export function cutOf(t: Tear): CutPoint[] {
  'worklet';
  const { ux, uy, nx, ny } = dir(t);
  const pts: CutPoint[] = [{ x: t.sx - ux * LEAD, y: t.sy - uy * LEAD, core: 0 }];
  for (let i = 0; i * STEP < t.length; i++) {
    const d = i * STEP;
    const taper = Math.min(1, (t.length - d) / 14);
    const off = noiseOffset(i, t.seed) * taper;
    const core = (1.6 + 3.4 * hash(i + 500, t.seed)) * Math.min(1, (t.length - d) / 30);
    pts.push({ x: t.sx + ux * d + nx * off, y: t.sy + uy * d + ny * off, core });
  }
  const tip = tipOf(t);
  pts.push({ x: tip.x, y: tip.y, core: 0 });
  return pts;
}

/** The region of one side (+1 or −1) behind the tip, as a polygon. */
export function regionOf(t: Tear, side: number, cut: CutPoint[]): number[] {
  'worklet';
  const far = 2000;
  const { ux, uy, nx, ny } = dir(t);
  const tip = tipOf(t);
  const first = cut[0];
  const out: number[] = [];
  for (const c of cut) out.push(c.x, c.y);
  out.push(tip.x + nx * far * side, tip.y + ny * far * side);
  out.push(first.x - ux * far + nx * far * side, first.y - uy * far + ny * far * side);
  out.push(first.x - ux * far, first.y - uy * far);
  return out;
}

/** The part past the tip, not torn yet. */
export function restOf(t: Tear): number[] {
  'worklet';
  const far = 2000;
  const { ux, uy, nx, ny } = dir(t);
  const tip = tipOf(t);
  return [
    tip.x - nx * far, tip.y - ny * far,
    tip.x + nx * far, tip.y + ny * far,
    tip.x + nx * far + ux * far, tip.y + ny * far + uy * far,
    tip.x - nx * far + ux * far, tip.y - ny * far + uy * far,
  ];
}

/** The white paper core along the cut on one side (the −n side shows 0.55 of it). */
export function coreOf(t: Tear, side: number, cut: CutPoint[]): number[] {
  'worklet';
  const { nx, ny } = dir(t);
  const k = side > 0 ? 1 : 0.55;
  const out: number[] = [];
  for (const c of cut) out.push(c.x, c.y);
  for (let j = cut.length - 1; j >= 0; j--) {
    const c = cut[j];
    out.push(c.x + nx * c.core * k * side, c.y + ny * c.core * k * side);
  }
  return out;
}

/**
 * The shear of one side, then its fall (pivot: the middle of that half of the card), as a 3 × 3 row-major
 * matrix [a c tx, b d ty, 0 0 1].
 */
export function transformOf(t: Tear, side: number, w: number, h: number): number[] {
  'worklet';
  const { ux, uy, nx, ny } = dir(t);
  const m = side * t.open;
  const c = t.length + ux * t.sx + uy * t.sy;
  let a = 1 - m * nx * ux;
  let b = -m * ny * ux;
  let cc = -m * nx * uy;
  let d = 1 - m * ny * uy;
  let tx = m * nx * c;
  let ty = m * ny * c;
  if (t.fall >= 0) {
    const f = t.fall;
    const px = w / 2 + nx * side * w * 0.25;
    const py = h / 2 + ny * side * w * 0.25;
    const out = side * nx >= 0 ? 1 : -1;
    const dx = out * 90 * f;
    const dy = -150 * f + 1300 * f * f;
    const spin = (out * (38 * f + 30 * f * f) * Math.PI) / 180;
    const cs = Math.cos(spin);
    const sn = Math.sin(spin);
    // p'' = R (S p) + (P + D − R P).
    const ftx = px + dx - (cs * px - sn * py);
    const fty = py + dy - (sn * px + cs * py);
    const na = cs * a - sn * b;
    const nb = sn * a + cs * b;
    const nc = cs * cc - sn * d;
    const nd = sn * cc + cs * d;
    const ntx = cs * tx - sn * ty + ftx;
    const nty = sn * tx + cs * ty + fty;
    a = na; b = nb; cc = nc; d = nd; tx = ntx; ty = nty;
  }
  return [a, cc, tx, b, d, ty, 0, 0, 1];
}

/** How far the cut can run from the start along `angle` before it leaves the card. */
export function spanOf(sx: number, sy: number, angle: number, w: number, h: number) {
  'worklet';
  const ux = Math.cos((angle * Math.PI) / 180);
  const uy = Math.sin((angle * Math.PI) / 180);
  let best = Infinity;
  if (ux > 1e-6) best = Math.min(best, (w - sx) / ux);
  if (ux < -1e-6) best = Math.min(best, -sx / ux);
  if (uy > 1e-6) best = Math.min(best, (h - sy) / uy);
  if (uy < -1e-6) best = Math.min(best, -sy / uy);
  return Number.isFinite(best) ? Math.max(0, best) : 0;
}

export function clamp(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}
export function easeInOut(x: number) {
  'worklet';
  const t = clamp(x);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp(x), 3);
}
export function easeOutBack(x: number) {
  'worklet';
  const t = clamp(x);
  const c1 = 1.70158;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
}
/** Critically damped return of an opening to 0 (ω = 16 /s). */
export function heal(open: number, t: number) {
  'worklet';
  if (t <= 0) return open;
  const w = 16;
  const v = open * (1 + w * t) * Math.exp(-w * t);
  return v < 0.0005 ? 0 : v;
}

/** The opening for a cut of this length: it grows over the first 60 pt. */
export function opening(length: number) {
  'worklet';
  return 0.07 * smoothstep(0, 60, length);
}

/** What the screen shows at one moment. Points are screen points. */
export type Scene = {
  tear: Tear | null;
  cardAlpha: number;
  cardScale: number;
  cardLift: number;
  dotX: number;
  dotY: number;
  dotAlpha: number;
  dotPress: number;
  toast: number;
  undoPress: number;
  hint: number;
  empty: number;
};

export function restScene(): Scene {
  'worklet';
  return { tear: null, cardAlpha: 1, cardScale: 1, cardLift: 0, dotX: 0, dotY: 0, dotAlpha: 0, dotPress: 0, toast: 0, undoPress: 0, hint: 1, empty: 0 };
}

/*
 autoPlay timeline (LOOP = 9 s), the same as TornPhotoDeleteModel.swift:
   0.00–0.70  rest.
   0.70       the finger lands on the top edge; 0.85–1.75 tear A runs down 42 % of the card (ease in-out);
              1.95 the finger lifts and the tear heals (critically damped, ~0.35 s).
   2.55       the finger lands on the right edge; 2.70–3.55 tear B runs left and a little down, 58 % across;
              3.70 lift, heal.
   4.30       the finger lands on the top edge again; 4.45–5.45 tear C rips to the bottom, speeding up;
              5.45 commit. 5.45–6.45 the two halves fall away.
   5.45–5.75  the hint fades; 5.80–6.20 the toast rises (ease-out-back); 6.00–6.40 the empty note fades in.
   6.90       the finger lands on Undo; 7.10–7.28 Undo is pressed (released by 7.43); 7.43–7.68 the toast drops.
   7.50–7.70  the empty note fades; 7.68–8.48 the card comes back (fade, scale 0.92 → 1 with a small
              overshoot, 14 pt lift); 8.20–8.60 the hint returns. 8.60–9.00 rest, identical to t = 0.
 */
export const LOOP = 9;

type Script = { fx: number; fy: number; angle: number; reach: number; seed: number; land: number; from: number; to: number; lift: number };

const TEAR_A: Script = { fx: 0.57, fy: 0, angle: 96, reach: 0.42, seed: 11, land: 0.7, from: 0.85, to: 1.75, lift: 1.95 };
const TEAR_B: Script = { fx: 1, fy: 0.3, angle: 168, reach: 0.58, seed: 23, land: 2.55, from: 2.7, to: 3.55, lift: 3.7 };
const TEAR_C: Script = { fx: 0.38, fy: 0, angle: 80, reach: 1.06, seed: 37, land: 4.3, from: 4.45, to: 5.45, lift: 5.45 };
export const COMMIT = 5.45;
export const UNDO_LAND = 6.9;
export const UNDO_PRESS = 7.1;
export const UNDO_UP = 7.28;
export const TOAST_IN = 5.8;
export const TOAST_OUT = 7.43;
export const RESTORE = 7.68;

export type CardBox = { x: number; y: number; w: number; h: number };

export function autoScene(t: number, card: CardBox, undoX: number, undoY: number, quiet: boolean): Scene {
  'worklet';
  const s = restScene();
  const run = (sc: Script, curve: (x: number) => number): Tear => {
    'worklet';
    const sx = sc.fx * card.w;
    const sy = sc.fy * card.h;
    const span = spanOf(sx, sy, sc.angle, card.w, card.h);
    const len = sc.reach * span * curve((t - sc.from) / (sc.to - sc.from));
    return { sx, sy, angle: sc.angle, length: len, open: opening(len), seed: sc.seed, fall: -1 };
  };
  const dot = (sc: Script, tear: Tear) => {
    'worklet';
    if (t < sc.land || t >= sc.lift + 0.2) return;
    const { ux, uy } = dir(tear);
    const tip = tipOf(tear);
    const px = tear.length > 0 ? tip.x : tear.sx - ux * 6;
    const py = tear.length > 0 ? tip.y : tear.sy - uy * 6;
    s.dotX = card.x + px;
    s.dotY = card.y + py;
    s.dotAlpha = smoothstep(sc.land, sc.land + 0.15, t) * (1 - smoothstep(sc.lift, sc.lift + 0.2, t));
    s.dotPress = smoothstep(sc.land, sc.land + 0.15, t);
  };

  if (t < TEAR_B.land) {
    const tear = run(TEAR_A, easeInOut);
    const tr = { ...tear };
    if (t > TEAR_A.lift) tr.open = heal(opening(tear.length), t - TEAR_A.lift);
    if (t >= TEAR_A.from && tr.open > 0) s.tear = tr;
    dot(TEAR_A, tear);
  } else if (t < TEAR_C.land) {
    const tear = run(TEAR_B, easeInOut);
    const tr = { ...tear };
    if (t > TEAR_B.lift) tr.open = heal(opening(tear.length), t - TEAR_B.lift);
    if (t >= TEAR_B.from && tr.open > 0) s.tear = tr;
    dot(TEAR_B, tear);
  } else if (t < RESTORE) {
    // Tear C speeds up like a real rip: 0.35 x + 0.65 x².
    const tear = run(TEAR_C, (x) => {
      'worklet';
      const c = clamp(x);
      return 0.35 * c + 0.65 * c * c;
    });
    if (t >= COMMIT) tear.fall = t - COMMIT;
    if (t >= TEAR_C.from && Math.max(0, tear.fall) < 1.2) s.tear = tear;
    if (t >= COMMIT + 1.2) s.cardAlpha = 0;
    dot(TEAR_C, tear);
  }

  if (t >= TOAST_IN && t < TOAST_OUT + 0.3) {
    s.toast = easeOutBack((t - TOAST_IN) / 0.4) * (1 - smoothstep(TOAST_OUT, TOAST_OUT + 0.25, t));
  }
  if (t >= UNDO_PRESS && t < UNDO_UP + 0.15) {
    s.undoPress = smoothstep(UNDO_PRESS, UNDO_PRESS + 0.08, t) * (1 - smoothstep(UNDO_UP, UNDO_UP + 0.15, t));
  }
  if (!quiet && t >= UNDO_LAND && t < UNDO_UP + 0.25) {
    s.dotX = undoX;
    s.dotY = undoY;
    s.dotAlpha = smoothstep(UNDO_LAND, UNDO_LAND + 0.15, t) * (1 - smoothstep(UNDO_UP + 0.05, UNDO_UP + 0.25, t));
    s.dotPress = smoothstep(UNDO_LAND, UNDO_LAND + 0.15, t);
  }
  s.empty = smoothstep(6.0, 6.4, t) * (1 - smoothstep(7.5, 7.7, t));
  s.hint = 1 - smoothstep(5.45, 5.75, t) + smoothstep(8.2, 8.6, t);
  if (t >= RESTORE) {
    const r = t - RESTORE;
    s.cardAlpha = easeOut(r / 0.4);
    s.cardScale = 0.92 + 0.08 * easeOutBack(r / 0.8);
    s.cardLift = 14 * (1 - easeOut(r / 0.8));
  }
  return s;
}
