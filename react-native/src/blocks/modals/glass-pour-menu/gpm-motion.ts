// Glass Pour Menu — the motion. One progress value p (0 closed … 1 open, rubber-banded past both ends) drives the
// glass's whole shape and light through fixed curves (`glassAt`), never time: the finger scrubs exactly what the
// loop plays. The loop (`timeline`) is a pure function of t in [0, LOOP): the press, a slow pull down to the neck,
// a hold there (the gasp), the release into an analytic spring, the lens bar touring the rows, a pick, the pour back.
//
// Measured on the pin's video (60 fps): open 1.20 → 1.97 s (lift 0.07 s, pour 0.35 s, cool 0.35 s), close
// 7.30 → 7.97 s. The pull, the hold and the lens tour are ours (the video's cursor hovered the rows 2.5–7.0 s).

import { comp, type MenuGeometry } from './gpm-theme';

export const LOOP = 7.2;

/** Phase starts (s into the loop); `dur` values are the timed segments. */
export const T = {
  press: 0.5, // finger down on the drop (snap spring)
  pull: 0.55, // the drop is drawn down to the neck…
  pullDur: 0.9, // …over 0.9 s, easeInOut (a finger's drag)
  release: 2.45, // let go after the one-second 1.45–2.45 neck hold
  pinch: 2.510094355743, // analytic open spring crosses p .70
  merge: 5.539456060903, // analytic close spring crosses p .70
  lensIn: 3.15, // the clear bar appears on Share (0.22 s)
  lensInDur: 0.22,
  lensSteps: [3.5, 3.85, 4.2, 4.6] as const, // → Duplicate, Rename, Delete, Duplicate
  lensRows: [1, 2, 3, 1] as const,
  pick: 5.1, // Duplicate is chosen: the bar flashes clear (0.35 s)
  pickDur: 0.35,
  close: 5.45, // the menu pours back into the drop (close spring), settled by 6.11
} as const;

/** p the pull holds at: the explicit capsule has a 9 pt waist and bridges a 28 pt gap. */
export const P_HOLD = 0.62;
/** p where the bead pinches off (the explicit bridge reaches zero radius): haptic on the way up and down. */
export const P_PINCH = 0.7;
/** The sheet is fully frosted here; row content must never appear on the clear, hot pour. */
export const P_FROSTED = 0.95;

// ---- springs (DESIGN-SYSTEM §8 names; the pour's own two from the concept) ----
export const SPRING = {
  snap: { damping: 30.5, stiffness: 322.3, mass: 1 }, // press, response 0.35 ζ 0.85
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 }, // the return to rest after a touch
  open: { damping: 18, stiffness: 170, mass: 1 }, // the pour: response 0.48 s, ζ 0.69 (3 % overshoot: the panel dips and levels)
  close: { damping: 18.3, stiffness: 130.5, mass: 1 }, // the pour back: response 0.55 s, ζ 0.8 (the pin's close takes 0.67 s)
  recoil: { damping: 12, stiffness: 300, mass: 0.6 },
  dimple: { damping: 14, stiffness: 260, mass: 0.8 },
  twist: { damping: 12, stiffness: 120, mass: 1 },
  satellite: { damping: 16, stiffness: 240, mass: 0.5 },
  lens: { damping: 26, stiffness: 420, mass: 0.6 }, // the bar follows the finger: response 0.24 s, ζ 0.82
} as const;

/** response (s) and damping ratio of a { damping, stiffness, mass } spring. */
export function springShape(s: { damping: number; stiffness: number; mass: number }) {
  const w = Math.sqrt(s.stiffness / s.mass);
  return { response: (2 * Math.PI) / w, zeta: s.damping / (2 * Math.sqrt(s.stiffness * s.mass)) };
}
const OPEN = springShape(SPRING.open);
const CLOSE = springShape(SPRING.close);
const LENS = springShape(SPRING.lens);
const SNAP = springShape(SPRING.snap);

// ---- gesture constants ----
export const GESTURE = {
  ptPerP: 175, // finger travel for p 0 → 1
  rubber: 0.35, // pt of panel per pt of finger past open
  projection: 0.12, // s: release target = nearest end to p + v · τ
  flick: 300, // pt/s downward: opens whatever p is
  tapSlop: 8, // pt
  resumeAfter: 2, // s after the last touch the menu settles and the loop restarts
  sideOff: 40, // pt off the panel sideways: the bar clears and a release closes
  snapRow: 6, // pt: the bar snaps to a row centre when the finger is this close
} as const;

// ---- helpers (worklets declared above their callers) ----
export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = clamp01((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}
export function easeInOut(x: number) {
  'worklet';
  // inOut (0.65, 0, 0.35, 1) is close to a quintic smoothstep; this one has the same slopes at both ends
  const t = clamp01(x);
  return t * t * t * (t * (t * 6 - 15) + 10);
}
export function easeOut(x: number) {
  'worklet';
  const t = clamp01(x);
  return 1 - (1 - t) * (1 - t) * (1 - t);
}
export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}
/** 0 → 1 step response of a spring (DESIGN-SYSTEM §8), analytic so the loop never accumulates state. */
export function springAt(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  if (zeta >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}

export type Glass = {
  beadX: number;
  beadY: number;
  beadR: number;
  boxX: number;
  boxY: number;
  boxW: number;
  boxH: number;
  boxR: number;
  k: number;
  thick: number;
  hot: number;
  frost: number;
  open: number;
  menuGlyph: number;
  menuLen: number;
  close: number;
  closeTurn: number;
  glyphK: number;
  rows: number[];
  panelBottom: number;
  rim: number;
  neckA: number;
  neckB: number;
  neckR: number;
};
export type PourLife = { sag?: number; breath?: number; bob?: number; recoil?: number; dimple?: number; dip?: number };

/** A third SDF bridges two smooth bodies. No per-part optical centre is used. */
export function glassAt(pIn: number, press: number, geometry: MenuGeometry = comp, life: PourLife = {}): Glass {
  'worklet';
  const pc = clamp01(pIn);
  const r = geometry.button.r;
  const scale = r / 39;
  const lift = easeInOut(seg(pc, 0, 0.62));
  const sag = (life.sag ?? 0) * (1 - smooth(0.05, 0.35, pc));
  const beadR = mix(r, geometry.bead.r, lift) * (1 - 0.06 * press * (1 - lift));
  const earlyBead = -6 * scale - beadR;
  const beadBaseY = pc < 0.35 ? earlyBead * easeInOut(seg(pc, 0, 0.35)) : mix(earlyBead, geometry.bead.y, easeOut(seg(pc, 0.35, 0.62)));
  const beadY = beadBaseY + sag + (life.bob ?? 0) + (life.recoil ?? 0);
  // 0–.35: a sagging round volume, r39→52 at y46. Then sides level before corners.
  const gather = easeInOut(seg(pc, 0, 0.35));
  const level = easeInOut(seg(pc, 0.35, 1));
  const sagR = mix(r, 52 * scale, gather);
  const sagY = 46 * scale * gather + sag;
  const bottom = mix(sagY + sagR, geometry.panel.bottom, level) + Math.max(pIn - 1, 0) * 175 * 0.35 + (life.dip ?? 0);
  const gap = scale * (28 * easeOut(seg(pc, 0.35, 0.62)) + 6 * smooth(0.62, 0.7, pc));
  const linkedTop = beadY + beadR + gap;
  const top =
    mix(sagY - sagR, linkedTop, smooth(0.18, 0.35, pc)) * (1 - smooth(0.7, 1, pc)) +
    geometry.panel.top * smooth(0.7, 1, pc) +
    (life.dimple ?? 0);
  const boxY = (top + bottom) / 2;
  const boxH = Math.max(1, (bottom - top) / 2);
  const boxW = mix(sagR, geometry.panel.half, easeOut(seg(pc, 0.35, 0.94)));
  const boxR = mix(Math.min(boxW, boxH), geometry.panel.r, smooth(0.7, 1, pc));
  const neckR =
    (mix(22 * scale, 4.5, easeOut(seg(pc, 0.35, 0.62))) + (life.breath ?? 0)) * smooth(0.18, 0.35, pc) * (1 - smooth(0.68, 0.7, pc));
  const area = 4 * boxW * boxH - (4 - Math.PI) * boxR * boxR + Math.PI * beadR * beadR * lift;
  const thick = Math.min((Math.PI * r * r) / Math.max(area, 1), 1);
  const hot = 0.55 * smooth(0.04, 0.3, pc) * (1 - smooth(0.7, 1, pc));
  const frost = smooth(0.84, P_FROSTED, pc);
  const rows = geometry.rows.map((_, i) =>
    frost >= 1
      ? smooth(
          P_FROSTED + (0.028 * i) / Math.max(1, geometry.rows.length - 1),
          0.985 + (0.015 * i) / Math.max(1, geometry.rows.length - 1),
          pc,
        )
      : 0,
  );
  return {
    beadX: 0,
    beadY,
    beadR,
    boxX: 0,
    boxY,
    boxW,
    boxH,
    boxR,
    k: 16 * smooth(0, 0.12, pc),
    thick,
    hot,
    frost,
    open: smooth(0.15, 0.95, pc),
    menuGlyph: 1,
    menuLen: 1,
    close: 0,
    closeTurn: 0,
    glyphK: smooth(0.3, 0.9, pc),
    rows,
    panelBottom: bottom,
    rim: mix(r, 16, smooth(0.6, 1, pc)),
    neckA: beadY + beadR - 9,
    neckB: top + 9,
    neckR,
  };
}

/** One-shot displacement starts at zero, preserving continuity at either threshold crossing. */
export function impulseAt(t: number, spring: { damping: number; stiffness: number; mass: number }) {
  'worklet';
  if (t <= 0) return 0;
  const decay = spring.damping / (2 * spring.mass);
  const wd = Math.sqrt(spring.stiffness / spring.mass - decay * decay);
  const peakT = Math.atan(wd / decay) / wd;
  const peak = Math.exp(-decay * peakT) * Math.sin(wd * peakT);
  return (Math.exp(-decay * t) * Math.sin(wd * t)) / peak;
}

/** Live hold details fade on/off without changing the finger's progress clock. */
export function lifeAt(t: number, p: number) {
  'worklet';
  const held = smooth(0.55, 0.6, p) * (1 - smooth(0.64, 0.7, p));
  return {
    sag: 1 - Math.cos((2 * Math.PI * t) / 4.8),
    breath: 0.6 * Math.sin(2 * Math.PI * 1.5 * t) * held,
    bob: Math.sin(2 * Math.PI * 1.5 * t) * held,
  };
}

export type Frame = { p: number; press: number; lensY: number; lensA: number; flash: number };

/** The loop at t (s): the pull, the gasp, the pour, the lens tour, the pick, the pour back. */
export function timeline(tIn: number): Frame {
  'worklet';
  const t = ((tIn % LOOP) + LOOP) % LOOP;
  // press: down at T.press, up at the release
  const press = springAt(t - T.press, SNAP.response, SNAP.zeta) * (1 - springAt(t - T.release, SNAP.response, SNAP.zeta));
  // p: pulled to P_HOLD, held, released into the open spring, then the close spring
  let p = P_HOLD * easeInOut(seg(t, T.pull, T.pull + T.pullDur));
  if (t >= T.release) p = P_HOLD + (1 - P_HOLD) * springAt(t - T.release, OPEN.response, OPEN.zeta);
  if (t >= T.close) p = 1 - springAt(t - T.close, CLOSE.response, CLOSE.zeta);
  if (t >= 6.55) p = 0;
  // the lens bar: in on Share, then four steps (spring), out as the menu closes
  const rows = comp.rows;
  let lensY = rows[0];
  let from = 0;
  for (let i = 0; i < T.lensSteps.length; i++) {
    const to = T.lensRows[i];
    lensY += (rows[to] - rows[from]) * springAt(t - T.lensSteps[i], LENS.response, LENS.zeta);
    from = to;
  }
  const lensA = easeOut(seg(t, T.lensIn, T.lensIn + T.lensInDur)) * (1 - seg(t, T.close, T.close + 0.16));
  const f = seg(t, T.pick, T.pick + T.pickDur);
  const flash = f <= 0 || f >= 1 ? 0 : Math.sin(Math.PI * Math.sqrt(f));
  return { p, press, lensY, lensA, flash };
}

/** Rows' hit band and the row under a finger (pt from O), or -1. */
export function rowAt(y: number, scale: number) {
  'worklet';
  const rows = comp.rows;
  const half = (comp.rowPitch / 2) * scale;
  for (let i = 0; i < rows.length; i++) {
    if (Math.abs(y - rows[i] * scale) <= half) return i;
  }
  return -1;
}
