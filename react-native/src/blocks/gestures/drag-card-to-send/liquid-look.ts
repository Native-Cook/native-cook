/**
 * The liquid as numbers: island, hanging drop, falling drop, the neck between them and the lip the neck leaves.
 * Pure worklets from a few animated values (radius, flight clock, receipt), so the screen and the offline renders
 * draw the same frame. Geometry is in screen points; the shader (shaders.ts) turns it into one glossy surface.
 */
import { DROP, EASE_IN_OUT, EASE_OUT, FLIGHT, dropRadius } from './motion';
import { LAYOUT, STEPS, type Layout } from './theme';

export const clamp01 = (v: number) => {
  'worklet';
  return Math.min(1, Math.max(0, v));
};
const mix = (a: number, b: number, t: number) => {
  'worklet';
  return a + (b - a) * t;
};

/** CSS cubic-bezier(x1, y1, x2, y2) at x = t (Newton, then bisection). */
export function cubic(x1: number, y1: number, x2: number, y2: number, t: number) {
  'worklet';
  if (t <= 0) return 0;
  if (t >= 1) return 1;
  const bx = (s: number, a: number, b: number) => 3 * a * s * (1 - s) * (1 - s) + 3 * b * s * s * (1 - s) + s * s * s;
  let s = t;
  for (let i = 0; i < 6; i += 1) {
    const x = bx(s, x1, x2) - t;
    const dx = 3 * x1 * (1 - s) * (1 - s) + 6 * (x2 - x1) * s * (1 - s) + 3 * (1 - x2) * s * s;
    if (Math.abs(x) < 1e-5 || Math.abs(dx) < 1e-6) break;
    s -= x / dx;
  }
  if (s < 0 || s > 1 || Math.abs(bx(s, x1, x2) - t) > 1e-3) {
    let lo = 0;
    let hi = 1;
    for (let i = 0; i < 24; i += 1) {
      s = (lo + hi) / 2;
      if (bx(s, x1, x2) < t) lo = s; else hi = s;
    }
  }
  return bx(s, y1, y2);
}
export const easeInOut = (t: number) => {
  'worklet';
  return cubic(EASE_IN_OUT[0], EASE_IN_OUT[1], EASE_IN_OUT[2], EASE_IN_OUT[3], t);
};
export const easeOutCurve = (t: number) => {
  'worklet';
  return cubic(EASE_OUT[0], EASE_OUT[1], EASE_OUT[2], EASE_OUT[3], t);
};

/** Detent index for a pull of `d` pt (-1 = no amount yet). */
export function stepAt(d: number, dead: number, step: number) {
  'worklet';
  if (d < dead) return -1;
  return Math.min(STEPS.length - 1, Math.floor((d - dead) / step));
}

/** Where a drop of radius r hangs from the island. */
export function hangAt(lay: Layout, r: number) {
  'worklet';
  return { x: lay.island.cx, y: lay.island.cy + lay.island.hh + DROP.hang * r };
}

export type Flight = { fromX: number; fromY: number; r: number; toX: number; toY: number };

export type LiquidFrame = {
  /** Radius of the drop hanging from the island (springs per detent). */
  r: number;
  /** Flight clock 0..1, or -1 when nothing is falling. */
  t: number;
  flight: Flight;
  /** 0 = island, 1 = receipt (a spring: it may overshoot a little). */
  receipt: number;
};

export type LiquidLook = {
  island: number[];
  bulb: number[];
  drop: number[];
  dir: number[];
  neck: number[];
  lip: number;
  tail: number;
  shadow: number;
  /** Where the amount is written and how big, riding the drop. */
  label: { x: number; y: number; scale: number; opacity: number };
  /** The lowest point anything liquid reaches (for the canvas rect). */
  bottom: number;
};

const AVATAR_HIDE = 0.26;
const LABEL_DROP = 0.2;
const AVATAR_R = LAYOUT.avatar / 2;

/** The flight's path: it leaves the island straight down, then bends toward the person. */
function pathAt(f: Flight, u: number) {
  'worklet';
  const cx = mix(f.fromX, f.toX, 0.12);
  const cy = mix(f.fromY, f.toY, 0.55);
  const a = (1 - u) * (1 - u);
  const b = 2 * (1 - u) * u;
  const c = u * u;
  return { x: a * f.fromX + b * cx + c * f.toX, y: a * f.fromY + b * cy + c * f.toY };
}

/** How far the drop must travel before the neck breaks (its overlap with the island plus the blend). */
function snapDistance(r: number) {
  'worklet';
  return (1 - DROP.hang) * r + DROP.blend * 1.6;
}

/** The clock time at which the neck breaks for this flight. */
export function snapTime(f: Flight) {
  'worklet';
  const need = snapDistance(f.r);
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 16; i += 1) {
    const m = (lo + hi) / 2;
    const p = pathAt(f, easeInOut(clamp01(m / FLIGHT.travel)));
    if (Math.hypot(p.x - f.fromX, p.y - f.fromY) < need) lo = m; else hi = m;
  }
  return hi;
}

/** The amount's type scales with the drop, from about 69 % at $5 to 92 % at the largest amount. */
export function labelScale(r: number) {
  'worklet';
  const max = dropRadius(STEPS[STEPS.length - 1]);
  return 0.4 + 0.52 * clamp01(r / max);
}

// Worklets called by liquidLook are declared above it: the worklets plugin does not hoist them, so one declared
// below would be captured as undefined.
export function liquidLook(lay: Layout, fr: LiquidFrame): LiquidLook {
  'worklet';
  const isl = lay.island;
  const rc = lay.receipt;
  const k = Math.max(0, fr.receipt);
  const hw = mix(isl.hw, rc.hw, k);
  const hh = mix(isl.hh, rc.hh, k);
  const cy = mix(isl.cy, rc.cy, k);
  const island = [isl.cx, cy, hw, hh];
  const lipY = isl.cy + isl.hh;
  const none = [0, 0, 0, 1];
  let bulb = [0, 0, 0];
  let drop = none;
  let dir = [0, 1];
  let neck = [0, 0, 0, 0, 0];
  let lip = 0;
  let tail = 0;
  let shadow = 0;
  let label = { x: isl.cx, y: lipY, scale: 0.6, opacity: 0 };
  let bottom = cy + hh;

  if (fr.t < 0) {
    if (fr.r > 0.5) {
      const p = hangAt(lay, fr.r);
      bulb = [p.x, p.y, fr.r];
      shadow = clamp01(fr.r / 20);
      // Written a little below the centre: the top of the drop is under the island's lip, so this is the middle of what shows.
      label = { x: p.x, y: p.y + LABEL_DROP * fr.r, scale: labelScale(fr.r), opacity: clamp01((fr.r - 12) / 6) };
      bottom = p.y + fr.r;
    }
  } else {
    const f = fr.flight;
    const t = clamp01(fr.t);
    const u = easeInOut(clamp01(t / FLIGHT.travel));
    const p = pathAt(f, u);
    // Speed along the path, for the stretch (finite difference on the same curve).
    const u2 = easeInOut(clamp01((t + 0.01) / FLIGHT.travel));
    const p2 = pathAt(f, u2);
    const vx = p2.x - p.x;
    const vy = p2.y - p.y;
    const v = Math.hypot(vx, vy);
    const total = Math.hypot(f.toX - f.fromX, f.toY - f.fromY);
    const speed = clamp01(v / Math.max(1, (total * 0.01) / FLIGHT.travel * 2.2));
    dir = v > 1e-4 ? [vx / v, vy / v] : [0, 1];
    // It drains into the face as it meets it (by distance: the eased path arrives early), and in any case by the end.
    const dist = Math.hypot(f.toX - p.x, f.toY - p.y);
    const meet = AVATAR_R + f.r * 0.6;
    const n = clamp01((meet - dist) / (meet - AVATAR_R * 0.3));
    const near = n * n * (3 - 2 * n);
    const absorb = Math.max(near, easeOutCurve(clamp01((t - FLIGHT.absorbFrom) / (1 - FLIGHT.absorbFrom))));
    const r = f.r * (1 - (1 - AVATAR_HIDE) * absorb);
    drop = [p.x, p.y, r, 1 + FLIGHT.stretch * speed * (1 - absorb)];

    // The neck: a filament from the island's lip to the drop, thinning as the drop leaves, gone at the snap.
    const travelled = Math.hypot(p.x - f.fromX, p.y - f.fromY);
    const snap = clamp01(travelled / snapDistance(f.r));
    if (snap < 1) {
      const thin = (1 - snap) * (1 - snap);
      neck = [isl.cx, lipY - 2, p.x - dir[0] * r * 0.5, p.y - dir[1] * r * 0.5, Math.max(0.6, f.r * 0.42 * thin)];
    } else {
      // Time since the snap, in ms (the clock is linear, the path is eased: invert it).
      const ts = snapTime(f);
      const ms = Math.max(0, (t - ts) * FLIGHT.ms);
      const w = 2 * Math.PI * FLIGHT.recoilHz * (ms / 1000);
      lip = Math.max(0, 9 * Math.exp(-ms / 150) * Math.cos(w));
      tail = Math.exp(-ms / 90);
    }
    // In the air the drop throws a softer, further shadow; it fades as the drop drains away.
    shadow = 1 - absorb;
    label = {
      x: p.x, y: p.y + LABEL_DROP * f.r, scale: labelScale(f.r),
      // The amount lets go as the neck stretches, before the drop gathers speed: the label is a view and the drop a
      // canvas, and at full speed they would part by a frame. The hint and the receipt carry the amount from here.
      opacity: 1 - clamp01(travelled / snapDistance(f.r)),
    };
    bottom = Math.max(cy + hh, p.y + r * drop[3]);
  }
  return { island, bulb, drop, dir, neck, lip, tail, shadow, label, bottom: bottom + 24 };
}
