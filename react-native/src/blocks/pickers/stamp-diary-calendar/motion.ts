/**
 * Stamp Diary Calendar — motion. Every value on screen is a pure function of the story time τ
 * (seconds); autoPlay runs τ = (clock − start) mod LOOP, so τ = LOOP equals τ = 0 (the pin).
 * Everything here is a worklet with no captured state, so Swift and Kotlin can port it value for value.
 *
 *   0.00–0.90  rest = the pin
 *   0.90–2.00  PEEL: the May 12 stamp rises off its cell (1 → 1.7) while its bottom-right corner
 *              curls back along the path of an invisible finger, showing the gummed back
 *   2.00–3.00  FLIGHT: it lets go, the curl springs flat, and it flutters up an S-curve
 *              (3D tilt, bend, sheen) growing into the shape of its copy in the collage
 *   3.00       IMPACT: it slaps down on that copy — squash, the collage gives way a little
 *   3.02–4.10  INK: a postmark ("MAY 12 2026") spreads from the ring, bleeds into the paper, dries
 *   …–5.50     hold; the collage is dimmed, the day's number shows in the emptied cell
 *   5.50–6.62  RETURN: the ink lifts, the stamp flies back on a lower arc
 *   6.62–7.12  RESTICK: it lays down corner last and presses flat
 *   7.12–8.60  rest (with 0–0.9 s: 28 % of the loop)
 */

import { grid, HERO_BOTTOM, MEMORIES, PILE, FREE_SPOT, type StampQuad } from './theme';

export const LOOP = 8.6;

export const T = {
  peelStart: 0.9,
  detach: 2.0,
  land: 3.0,
  ink: 3.02,
  holdEnd: 5.5,
  returnFlight: 5.58,
  restick: 6.62,
  rest: 7.12,
} as const;

/** Peel: how far the stamp rises towards the viewer, and how much of it curls. */
export const PEEL = {
  lift: 1.7,
  liftShare: 0.5,
  rise: 3,
  amount: 0.8,
  /** The finger travels this many stamp diagonals at full peel. */
  reach: 1.2,
  wobble: 0.12,
  /** Curl radius = 0.22 × finger distance, at most 0.16 × the stamp's longer side. */
  radiusPerDistance: 0.22,
  radiusMax: 0.16,
  shadow: 0.55,
  /** The curl lets go over this long after the detach. */
  release: 0.28,
} as const;

/** Flight: time curve, size bump, flutter (degrees), bend and sheen. */
export const FLIGHT = {
  ease: [0.4, 0, 0.55, 0.92] as const,
  sizeEase: [0.3, 0, 0.3, 1] as const,
  bump: 0.16,
  rotZ: 10,
  rotX: 24,
  rotY: 20,
  bend: 0.8,
  bendDepth: 0.3,
  bendRadius: 0.55,
  sheen: 0.32,
  /** S-curve controls, relative to the start and to the landing. */
  out: { x: 70, y: -150 },
  in: { x: 110, y: 40 },
  perspective: 520,
} as const;

/** Impact squash, a damped wobble (seconds). */
export const IMPACT = { attack: 0.045, decay: 0.09, period: 0.32, squashX: 0.05, squashY: 0.07, nudge: 0.012 } as const;

export const INK = {
  spread: 0.34,
  bleed: 1.1,
  thin: 0.5,
  bleedBy: 0.13,
  dryAfter: 0.25,
  dry: 1.4,
  lift: 0.34,
  liftBy: 0.62,
} as const;

export const RETURN = {
  preLift: 1.04,
  lift: 1.3,
  flutter: 0.55,
  out: { x: -80, y: 70 },
  in: { x: -70, y: -130 },
  corner: 0.3,
  cornerFrom: 0.7,
  undershoot: 0.025,
} as const;

export const PILE_DIM = { to: 0.5, inAt: T.land - 0.45, inFor: 0.6, outAt: T.holdEnd + 0.05, outFor: 0.55 } as const;

export const REVEAL = { inAt: T.detach - 0.05, inFor: 0.25, outAt: 6.55, outFor: 0.25 } as const;

/** Touch. */
export const TOUCH = {
  /** Release flies when the peel passed this share of the stamp's diagonal, or on an upward flick. */
  commitShare: 0.42,
  commitVelocity: -500,
  slop: 6,
  liftIn: 0.22,
  snapTau: 0.07,
  snapFor: 0.5,
  resumeAfter: 2,
  /** A tap sends a landed stamp home a little faster than the loop does. */
  returnRate: 1.3,
  /** Reduced motion: a crossfade at the landing spot. */
  fade: 0.2,
} as const;

export const STAMP_PAD = 0.55;

// ---------------------------------------------------------------------------------------------
// Small pure helpers (declared before every worklet that calls them).

export function clamp01(v: number): number {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function mix(a: number, b: number, t: number): number {
  'worklet';
  return a + (b - a) * t;
}

/** Smoothstep on 0…1. */
export function ease01(v: number): number {
  'worklet';
  const x = clamp01(v);
  return x * x * (3 - 2 * x);
}

export function easeOut3(v: number): number {
  'worklet';
  const x = 1 - clamp01(v);
  return 1 - x * x * x;
}

/** CSS-style cubic-bezier(x1, y1, x2, y2) evaluated at x (Newton, then bisection). */
export function bezier(x1: number, y1: number, x2: number, y2: number, x: number): number {
  'worklet';
  const t0 = clamp01(x);
  if (t0 === 0 || t0 === 1) return t0;
  const cx = 3 * x1;
  const bx = 3 * (x2 - x1) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * y1;
  const by = 3 * (y2 - y1) - cy;
  const ay = 1 - cy - by;
  let t = t0;
  for (let i = 0; i < 6; i += 1) {
    const fx = ((ax * t + bx) * t + cx) * t - t0;
    const d = (3 * ax * t + 2 * bx) * t + cx;
    if (Math.abs(fx) < 1e-5 || Math.abs(d) < 1e-6) break;
    t -= fx / d;
  }
  if (t < 0 || t > 1) {
    let lo = 0;
    let hi = 1;
    t = t0;
    for (let i = 0; i < 24; i += 1) {
      const fx = ((ax * t + bx) * t + cx) * t;
      if (fx < t0) lo = t; else hi = t;
      t = (lo + hi) / 2;
    }
  }
  return ((ay * t + by) * t + cy) * t;
}

function cubicPoint(p0: number, p1: number, p2: number, p3: number, t: number): number {
  'worklet';
  const u = 1 - t;
  return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
}

/** 0 at both ends, 1 in the middle, smooth (sin²). */
function envelope(u: number): number {
  'worklet';
  const s = Math.sin(Math.PI * clamp01(u));
  return s * s;
}

/** Impact wobble k(t), t = seconds since contact: a fast attack then a damped cosine. */
export function impact(t: number): number {
  'worklet';
  if (t < 0) return 0;
  if (t < IMPACT.attack) return Math.sin((Math.PI / 2) * (t / IMPACT.attack));
  const s = t - IMPACT.attack;
  if (s > 0.9) return 0;
  return Math.exp(-s / IMPACT.decay) * Math.cos((2 * Math.PI * s) / IMPACT.period);
}

/** Critically damped return 1 → 0 (snap back after a cancelled peel). */
export function settle(t: number): number {
  'worklet';
  if (t <= 0) return 1;
  const x = t / TOUCH.snapTau;
  return (1 + x) * Math.exp(-x);
}

// ---------------------------------------------------------------------------------------------
// Calendar (Gregorian, Sunday first). May 2026 holds the memories.

export type Month = { readonly year: number; readonly month: number };
export const MAY_2026: Month = { year: 2026, month: 5 };

export function isMay2026(m: Month): boolean {
  'worklet';
  return m.year === 2026 && m.month === 5;
}

export function addMonths(m: Month, delta: number): Month {
  'worklet';
  const index = m.year * 12 + (m.month - 1) + delta;
  return { year: Math.floor(index / 12), month: (index % 12 + 12) % 12 + 1 };
}

export function daysIn(m: Month): number {
  'worklet';
  if (m.month === 2) {
    const leap = (m.year % 4 === 0 && m.year % 100 !== 0) || m.year % 400 === 0;
    return leap ? 29 : 28;
  }
  return m.month === 4 || m.month === 6 || m.month === 9 || m.month === 11 ? 30 : 31;
}

/** Weekday of the 1st, 0 = Sunday (Sakamoto). */
export function firstWeekday(m: Month): number {
  'worklet';
  const table = [0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4];
  const y = m.month < 3 ? m.year - 1 : m.year;
  return (y + Math.floor(y / 4) - Math.floor(y / 100) + Math.floor(y / 400) + table[m.month - 1] + 1) % 7;
}

export function cellOf(day: number, m: Month): { column: number; row: number } {
  'worklet';
  const index = firstWeekday(m) + day - 1;
  return { column: index % 7, row: Math.floor(index / 7) };
}

export function cellCenter(day: number, m: Month): { x: number; y: number } {
  'worklet';
  const c = cellOf(day, m);
  return { x: (c.column + 0.5) * grid.columnWidth, y: grid.top + (c.row + 0.5) * grid.rowHeight };
}

export function dayAt(x: number, y: number, m: Month): number {
  'worklet';
  if (x < 0 || x >= 402 || y < grid.top || y >= grid.top + grid.rowHeight * grid.rows) return 0;
  const column = Math.min(6, Math.floor(x / grid.columnWidth));
  const row = Math.min(grid.rows - 1, Math.floor((y - grid.top) / grid.rowHeight));
  const day = row * 7 + column - firstWeekday(m) + 1;
  return day >= 1 && day <= daysIn(m) ? day : 0;
}

export function memoryIndex(day: number, m: Month): number {
  'worklet';
  if (!isMay2026(m)) return -1;
  for (let i = 0; i < MEMORIES.length; i += 1) if (MEMORIES[i].day === day) return i;
  return -1;
}

/** Where a memory lands: on its copy in the collage when that copy is sharp, otherwise the free spot. */
export function landingQuad(memory: number): StampQuad {
  'worklet';
  const twin = MEMORIES[memory].twin;
  return twin >= 0 && PILE[twin].blur[1] === 0 ? PILE[twin].quad : FREE_SPOT;
}

/** Perforation holes along the width and down the side of a memory (fixed from cell to collage). */
export function holesOf(memory: number): { across: number; down: number } {
  'worklet';
  const q = landingQuad(memory);
  return { across: 10, down: Math.max(4, Math.round((10 * q.h) / q.w)) };
}

export function holesOfQuad(q: StampQuad): { across: number; down: number } {
  'worklet';
  return { across: 10, down: Math.max(4, Math.round((10 * q.h) / q.w)) };
}

// ---------------------------------------------------------------------------------------------
// Curl: the paper rolls around a cylinder of radius r whose axis is the line dot(p, n) = a in the
// stamp's own centred coordinates; the side where dot(p, n) > a is lifted and folded back.

export type Curl = { nx: number; ny: number; a: number; r: number };

const FLAT: Curl = { nx: 1, ny: 0, a: 1e4, r: 0 };

/** A corner c pulled to the finger f: the fold is the bisector, so the corner lands on the finger. */
export function curlFromFinger(cx: number, cy: number, fx: number, fy: number, w: number, h: number): Curl {
  'worklet';
  const dx = cx - fx;
  const dy = cy - fy;
  const d = Math.hypot(dx, dy);
  if (d < 0.01) return FLAT;
  const nx = dx / d;
  const ny = dy / d;
  const r = Math.min(PEEL.radiusMax * Math.max(w, h), PEEL.radiusPerDistance * d);
  const sc = (d + Math.PI * r) / 2;
  return { nx, ny, a: cx * nx + cy * ny - sc, r };
}

/** The autoplay finger: from the bottom-right corner towards the top left, with a slight drift. */
export function peelCurl(amount: number, w: number, h: number): Curl {
  'worklet';
  const base = Math.atan2(-h, -w) + PEEL.wobble * Math.sin(Math.PI * amount);
  const travel = amount * PEEL.reach * Math.hypot(w, h);
  const cx = w / 2;
  const cy = h / 2;
  return curlFromFinger(cx, cy, cx + Math.cos(base) * travel, cy + Math.sin(base) * travel, w, h);
}

/** Paper bending in the air: a wide roll along an edge whose direction turns during the flight. */
export function bendCurl(angle: number, bend: number, w: number, h: number): Curl {
  'worklet';
  if (bend <= 0.001) return FLAT;
  const nx = Math.cos(angle);
  const ny = Math.sin(angle);
  const extent = Math.abs(nx) * w * 0.5 + Math.abs(ny) * h * 0.5;
  return { nx, ny, a: extent - bend * FLIGHT.bendDepth * Math.min(w, h), r: FLIGHT.bendRadius * Math.max(w, h) };
}

/** Blend two curls (direction by angle, then offset and radius). A flat curl blends by its offset. */
export function blendCurl(from: Curl, to: Curl, t: number, w: number, h: number): Curl {
  'worklet';
  if (t <= 0) return from;
  if (t >= 1) return to;
  const fromFlat = from.r <= 0;
  const toFlat = to.r <= 0;
  if (fromFlat && toFlat) return FLAT;
  // A flat side keeps the other side's direction with its axis pushed past the paper.
  const ref = fromFlat ? to : from;
  const out = toFlat ? from : to;
  const refExtent = Math.abs(ref.nx) * w * 0.5 + Math.abs(ref.ny) * h * 0.5;
  const outExtent = Math.abs(out.nx) * w * 0.5 + Math.abs(out.ny) * h * 0.5;
  const f = fromFlat ? { nx: ref.nx, ny: ref.ny, a: refExtent + 1, r: ref.r } : from;
  const g = toFlat ? { nx: out.nx, ny: out.ny, a: outExtent + 1, r: out.r } : to;
  let a0 = Math.atan2(f.ny, f.nx);
  const a1 = Math.atan2(g.ny, g.nx);
  let da = a1 - a0;
  while (da > Math.PI) da -= 2 * Math.PI;
  while (da < -Math.PI) da += 2 * Math.PI;
  a0 += da * t;
  return { nx: Math.cos(a0), ny: Math.sin(a0), a: mix(f.a, g.a, t), r: mix(f.r, g.r, t) };
}

// ---------------------------------------------------------------------------------------------
// The pose of the moving stamp, plus everything that reacts to it.

export type Pose = {
  /** 1 while the moving stamp replaces its cell stamp. */
  active: number;
  memory: number;
  /** Centre in reference points and the stamp's own size (its perforation grows with it). */
  cx: number;
  cy: number;
  w: number;
  h: number;
  /** Shear: how much lower the right edge sits, per point of width. */
  skew: number;
  lift: number;
  sx: number;
  sy: number;
  rotX: number;
  rotY: number;
  rotZ: number;
  curl: Curl;
  peelShadow: number;
  sheenPos: number;
  sheenAmt: number;
  /** 0 on the paper, 1 at the top of the flight (drives the drop shadow). */
  height: number;
  inkSpread: number;
  inkThreshold: number;
  inkWet: number;
  inkOn: number;
  opacity: number;
  pileDim: number;
  pileNudge: number;
  /** Day number and selection ring in the emptied cell. */
  reveal: number;
};

export function restPose(memory: number): Pose {
  'worklet';
  const m = MEMORIES[memory];
  const c = cellCenter(m.day, MAY_2026);
  return {
    active: 0, memory, cx: c.x, cy: c.y, w: m.w, h: m.h, skew: 0, lift: 1, sx: 1, sy: 1,
    rotX: 0, rotY: 0, rotZ: 0, curl: FLAT, peelShadow: 0, sheenPos: 0, sheenAmt: 0, height: 0,
    inkSpread: 0, inkThreshold: 1, inkWet: 0, inkOn: 0, opacity: 1, pileDim: 1, pileNudge: 0, reveal: 0,
  };
}

function quadCenter(q: StampQuad): { x: number; y: number } {
  'worklet';
  return { x: q.x + q.w / 2, y: q.y + q.h / 2 + q.dy / 2 };
}

/** The peel's curl at the moment it lets go (autoPlay), the default start of the release blend. */
export function detachCurl(memory: number): Curl {
  'worklet';
  const m = MEMORIES[memory];
  return peelCurl(PEEL.amount, m.w, m.h);
}

function applyInk(pose: Pose, tau: number): void {
  'worklet';
  if (tau < T.ink) return;
  const t = tau - T.ink;
  pose.inkOn = 1;
  pose.inkSpread = easeOut3(t / INK.spread);
  pose.inkThreshold = INK.thin - INK.bleedBy * easeOut3(t / INK.bleed) + INK.liftBy * ease01((tau - T.holdEnd) / INK.lift);
  pose.inkWet = 1 - ease01((t - INK.dryAfter) / INK.dry);
  if (pose.inkThreshold > 1.05) pose.inkOn = 0;
}

/**
 * The story pose at τ. `release` is the curl the stamp had when it let go (the finger's, after a
 * touch); autoPlay passes detachCurl().
 */
export function storyPose(memory: number, tau: number, release: Curl): Pose {
  'worklet';
  const pose = restPose(memory);
  if (tau < T.peelStart || tau >= T.rest) return pose;
  const m = MEMORIES[memory];
  const cell = cellCenter(m.day, MAY_2026);
  const q = landingQuad(memory);
  const land = quadCenter(q);
  const landSkew = q.dy / q.w;
  pose.active = 1;
  pose.reveal = ease01((tau - REVEAL.inAt) / REVEAL.inFor) * (1 - ease01((tau - REVEAL.outAt) / REVEAL.outFor));
  pose.pileDim = mix(1, PILE_DIM.to, ease01((tau - PILE_DIM.inAt) / PILE_DIM.inFor) * (1 - ease01((tau - PILE_DIM.outAt) / PILE_DIM.outFor)));

  if (tau < T.detach) {
    const p = (tau - T.peelStart) / (T.detach - T.peelStart);
    const rise = easeOut3(p / PEEL.liftShare);
    pose.lift = mix(1, PEEL.lift, rise);
    pose.cy = cell.y - PEEL.rise * rise;
    pose.curl = peelCurl(PEEL.amount * bezier(0.5, 0, 0.6, 1, p), m.w, m.h);
    pose.peelShadow = PEEL.shadow;
    pose.height = 0.25 * rise;
    return pose;
  }

  if (tau < T.land) {
    const u = (tau - T.detach) / (T.land - T.detach);
    const e = bezier(FLIGHT.ease[0], FLIGHT.ease[1], FLIGHT.ease[2], FLIGHT.ease[3], u);
    const se = bezier(FLIGHT.sizeEase[0], FLIGHT.sizeEase[1], FLIGHT.sizeEase[2], FLIGHT.sizeEase[3], u);
    const env = envelope(u);
    const x0 = cell.x;
    const y0 = cell.y - PEEL.rise;
    pose.cx = cubicPoint(x0, x0 + FLIGHT.out.x, land.x + FLIGHT.in.x, land.x, e);
    pose.cy = cubicPoint(y0, y0 + FLIGHT.out.y, land.y + FLIGHT.in.y, land.y, e);
    pose.w = mix(m.w, q.w, se);
    pose.h = mix(m.h, q.h, se);
    pose.skew = landSkew * se;
    pose.lift = mix(PEEL.lift, 1, se) * (1 + FLIGHT.bump * env);
    pose.rotZ = FLIGHT.rotZ * env * Math.sin(2 * Math.PI * 1.15 * u);
    pose.rotX = FLIGHT.rotX * env * Math.sin(2 * Math.PI * 0.9 * u + 0.5);
    pose.rotY = -FLIGHT.rotY * env * Math.sin(2 * Math.PI * 1.3 * u + 1.3);
    const bend = bendCurl(0.7 + 4.5 * u, FLIGHT.bend * env, pose.w, pose.h);
    pose.curl = blendCurl(release, bend, ease01((tau - T.detach) / PEEL.release), pose.w, pose.h);
    pose.peelShadow = PEEL.shadow * (1 - ease01((tau - T.detach) / PEEL.release));
    pose.sheenAmt = FLIGHT.sheen * env;
    pose.sheenPos = 0.5 + 0.9 * Math.sin(2 * Math.PI * 0.9 * u + 0.5);
    pose.height = Math.max(0.25 * (1 - e), env);
    return pose;
  }

  if (tau < T.holdEnd) {
    const k = impact(tau - T.land);
    pose.cx = land.x;
    pose.cy = land.y;
    pose.w = q.w;
    pose.h = q.h;
    pose.skew = landSkew;
    pose.sx = 1 + IMPACT.squashX * k;
    pose.sy = 1 - IMPACT.squashY * k;
    pose.pileNudge = IMPACT.nudge * k;
    applyInk(pose, tau);
    return pose;
  }

  if (tau < T.restick) {
    const pre = ease01((tau - T.holdEnd) / (T.returnFlight - T.holdEnd));
    const u = clamp01((tau - T.returnFlight) / (T.restick - T.returnFlight));
    const e = bezier(0.45, 0, 0.3, 1, u);
    const env = envelope(u) * RETURN.flutter;
    const x3 = cell.x;
    const y3 = cell.y - PEEL.rise;
    pose.cx = cubicPoint(land.x, land.x + RETURN.out.x, x3 + RETURN.in.x, x3, e);
    pose.cy = cubicPoint(land.y, land.y + RETURN.out.y, y3 + RETURN.in.y, y3, e);
    pose.w = mix(q.w, m.w, e);
    pose.h = mix(q.h, m.h, e);
    pose.skew = landSkew * (1 - e);
    pose.lift = mix(mix(1, RETURN.preLift, pre), RETURN.lift, e) * (1 + FLIGHT.bump * env);
    pose.rotZ = -FLIGHT.rotZ * env * Math.sin(2 * Math.PI * 1.0 * u + 0.4);
    pose.rotX = FLIGHT.rotX * env * Math.sin(2 * Math.PI * 0.8 * u + 2.1);
    pose.rotY = FLIGHT.rotY * env * Math.sin(2 * Math.PI * 1.1 * u + 0.2);
    const bend = bendCurl(3.6 - 3.5 * u, FLIGHT.bend * envelope(u) * RETURN.flutter, pose.w, pose.h);
    const corner = peelCurl(RETURN.corner, pose.w, pose.h);
    pose.curl = blendCurl(bend, corner, ease01((u - RETURN.cornerFrom) / (1 - RETURN.cornerFrom)), pose.w, pose.h);
    pose.peelShadow = PEEL.shadow * ease01((u - RETURN.cornerFrom) / (1 - RETURN.cornerFrom));
    pose.sheenAmt = FLIGHT.sheen * env;
    pose.sheenPos = 0.5 + 0.9 * Math.sin(2 * Math.PI * 0.8 * u + 2.1);
    pose.height = Math.max(0.3 * pre * (1 - u), envelope(u), 0.3 * e);
    applyInk(pose, tau);
    return pose;
  }

  // Restick: the corner lays down last, the stamp sinks to the paper and presses in.
  const r = clamp01((tau - T.restick) / (T.rest - T.restick));
  const e = easeOut3(r);
  pose.curl = peelCurl(RETURN.corner * (1 - e), m.w, m.h);
  pose.peelShadow = PEEL.shadow * (1 - e);
  pose.lift = 1 + (RETURN.lift - 1) * (1 - e) - RETURN.undershoot * Math.sin(Math.PI * e);
  pose.cy = cell.y - PEEL.rise * (1 - e);
  pose.height = 0.3 * (1 - e);
  return pose;
}

/** A finger peeling a memory in its cell: lift by time since touch-down, curl from the finger. */
export function fingerPose(memory: number, held: number, cornerX: number, cornerY: number, fingerX: number, fingerY: number): Pose {
  'worklet';
  const pose = restPose(memory);
  const m = MEMORIES[memory];
  const rise = easeOut3(held / TOUCH.liftIn);
  pose.active = 1;
  pose.lift = mix(1, PEEL.lift, rise);
  pose.cy -= PEEL.rise * rise;
  pose.curl = curlFromFinger(cornerX, cornerY, fingerX, fingerY, m.w, m.h);
  pose.peelShadow = PEEL.shadow;
  pose.height = 0.25 * rise;
  return pose;
}

/** Reduced motion: the stamp fades in on its landing spot, postmarked; v = 0…1 shown. */
export function quietPose(memory: number, v: number): Pose {
  'worklet';
  const pose = restPose(memory);
  if (v <= 0) return pose;
  const q = landingQuad(memory);
  const land = quadCenter(q);
  pose.active = 1;
  pose.cx = land.x;
  pose.cy = land.y;
  pose.w = q.w;
  pose.h = q.h;
  pose.skew = q.dy / q.w;
  pose.opacity = v;
  pose.inkOn = 1;
  pose.inkSpread = 1;
  pose.inkThreshold = INK.thin - INK.bleedBy;
  pose.reveal = v;
  pose.pileDim = mix(1, PILE_DIM.to, v);
  return pose;
}

/** Share of the diagonal a finger has peeled (for the commit test). */
export function peelShare(memory: number, dx: number, dy: number): number {
  'worklet';
  const m = MEMORIES[memory];
  return Math.hypot(dx, dy) / Math.hypot(m.w, m.h);
}

export { HERO_BOTTOM };
