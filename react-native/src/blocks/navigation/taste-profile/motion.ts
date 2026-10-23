/**
 * Taste Profile — the motion. Every animated value is a pure function of time: in autoPlay, of the
 * loop time `t` (seconds, 0 ≤ t < LOOP, t = LOOP equals t = 0); after a touch, of the time since the
 * touch that started it. No state is accumulated between frames.
 *
 * Hero: picking a city turns the stats and the photo strip into a split-flap departure board. Every
 * digit of the four stats becomes a flap card that clatters through its drum (blank, 0…9) to the
 * city's number, tile after tile; the four photos fall flap by flap through two other photos of the
 * city to its first four; the photo count flaps too; the falling halves carry motion blur and
 * shading, the last flap of each cell lands with a bounce. The rank coin flips (Blender frames), the
 * selection pill stretches to the chip, the rank label rolls.
 * Interactions: swipe the photo strip and it becomes a 3D cover-flow with momentum (flat again at
 * rest); hold a stat tile and it grows into a six-month bar chart for the city (hold to read, let go
 * to close). "follow" ⇄ "following" as before.
 *
 *   0.00–0.70  rest = pin 19 ("all")
 *   0.70–0.82  the autoPlay finger presses "london"; 0.82 commit: the board flaps (settled by 2.42)
 *   2.55–3.85  a swipe on the strip: cover-flow, momentum to the third photo, flat again (flatAt + COVER.out)
 *   4.35–6.25  hold "favorites": the tile grows into London's bar chart (4.70), let go at 6.00
 *   6.60–6.72  press "all"; 6.72 commit: the board flaps back (settled by 8.32)
 *   8.32–10.0  rest (with 0–0.70: 2.38 s = 24 %)
 */

import { box, chart } from './theme';

export const LOOP = 10;

/** The autoPlay script: [press-in time, commit time, target city]. */
export const SCRIPT = [
  { press: 0.7, commit: 0.82, city: 2 },
  { press: 6.6, commit: 6.72, city: 0 },
] as const;

/** The autoPlay swipe: finger down, drag, release with velocity, snap to the third photo, flat again. */
export const STRIP_SCRIPT = {
  down: 2.55,
  release: 2.9,
  dragTo: 0.55, // photos dragged before release
  to: 2,
  flatAt: 3.55,
} as const;

/** After the last touch, autoPlay waits this long, then returns to "all" and the loop carries on. */
export const RESUME_AFTER = 2;
/** A city change has settled this long after its commit (every channel at rest). */
export const SETTLE = 1.6;

/** Cities: index 0 is "all" (the pin's state). */
export const CITIES = ['all', 'new york', 'london', 'paris'] as const;
/** The rank coin per city: which face and which number on the coin's back. */
export const RANK = [
  { face: 0, word: 0, back: 0 }, // all: the user's home city, New York, rank 3
  { face: 0, word: 0, back: 0 }, // new york: rank 3
  { face: 1, word: 1, back: 0 }, // london: rank 7 (sky-blue face)
  { face: 1, word: 2, back: 1 }, // paris: rank 1 (gold face)
] as const;
export const RANK_WORDS = ['new york', 'london', 'paris'] as const;
/** Stats per city: favorites, recommend, okay, disliked. The three cities add up to "all". */
export const STATS = [
  ['125', '764', '183', '17'],
  ['92', '518', '120', '11'],
  ['21', '146', '40', '4'],
  ['12', '100', '23', '2'],
] as const;

export const PRESS = {
  scale: 0.97,
  in: 0.12, // s, ease-out
  out: 0.16, // s, ease-out (release is the system's answer, but the press is deliberate: slower out)
} as const;

/** Selection pill: the edge that leads the move springs faster than the one that trails. */
export const PILL = {
  lead: { omega: 21, zeta: 0.84 },
  trail: { omega: 12.5, zeta: 0.92 },
} as const;

export const COIN = {
  delay: 0.06,
  flip: 0.56, // s for half a turn, ease-in-out on the angle
  lift: 12, // ref px up at the top of the flip
  grow: 0.08, // scale added at the top of the flip
  shadow: 0.16, // contact-shadow opacity at the top of the flip
  step: 10, // degrees between Blender frames
  frames: 19, // 0°…180° of one sequence
} as const;

export const LABEL = {
  delay: 0.18,
  dur: 0.36,
  travel: 14, // ref px
} as const;

/** The gem's sheen is kept for the ports but no longer plays in the loop (one hero at a time). */
export const SHEEN = {
  from: -1,
  to: -1,
  frames: 12,
} as const;

export const FOLLOW = {
  spring: { duration: 420, dampingRatio: 0.82 },
  grow: 54, // ref px the pill grows to the left ("ing" at 12.24 pt SemiBold)
} as const;

export const REDUCED = {
  fade: 0.2, // s: the changing values dip and swap, nothing moves
  dip: 0.45, // opacity at the middle of the dip
} as const;

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function lerp(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}

/** Solve the curve's x before sampling y; ports use the same bisection. */
function bezier(p: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  const x = clamp01(p);
  if (x === 0 || x === 1) return x;
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 18; i++) {
    const m = (lo + hi) / 2;
    const u = 1 - m;
    const at = 3 * u * u * m * x1 + 3 * u * m * m * x2 + m * m * m;
    if (at < x) lo = m;
    else hi = m;
  }
  const m = (lo + hi) / 2;
  const u = 1 - m;
  return 3 * u * u * m * y1 + 3 * u * m * m * y2 + m * m * m;
}

/** Strong ease-out: cubic-bezier(0.23, 1, 0.32, 1). */
export function easeOut(p: number) {
  'worklet';
  return bezier(p, 0.23, 1, 0.32, 1);
}

/** On-screen movement: cubic-bezier(0.77, 0, 0.175, 1). */
export function easeInOut(p: number) {
  'worklet';
  return bezier(p, 0.77, 0, 0.175, 1);
}

/** A damped spring from 0 to 1, `tau` seconds after it starts (closed form, no state). */
export function spring(tau: number, s: { omega: number; zeta: number }) {
  'worklet';
  if (tau <= 0) return 0;
  const { omega, zeta } = s;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const env = Math.exp(-zeta * omega * tau);
  const v = 1 - env * (Math.cos(wd * tau) + ((zeta * omega) / wd) * Math.sin(wd * tau));
  return env < 1e-4 ? 1 : v;
}

/**
 * The clock's answer for one moment: a city change `from` → `to` that committed `tau` seconds ago
 * (tau < 0: not yet), the pill's and the coin's start values (so a change that interrupts another
 * starts where the screen is), and the press and sheen channels.
 */
export type Change = {
  from: number;
  to: number;
  tau: number;
  /** Pill edges in ref px and coin angle (0…180) / back number when the change started. */
  l0: number;
  r0: number;
  a0: number;
  back0: number;
  /** Photo strip offset (in photos) when the change started: the board flaps from there. */
  x0: number;
  /** Chip being pressed (-1 none) and how far (0…1). */
  pressChip: number;
  press: number;
  /** Gem sheen phase in frames (0…SHEEN.frames, 0 and SHEEN.frames are the rest frame). */
  sheen: number;
  reduced: boolean;
};

/** Press amount for a press that went in at `t0` and was released at `t1`, at time t. */
export function pressAt(t: number, t0: number, t1: number) {
  'worklet';
  if (t < t0) return 0;
  if (t < t1) return easeOut(seg(t, t0, t0 + PRESS.in));
  const held = easeOut(seg(t1, t0, t0 + PRESS.in));
  return held * (1 - easeOut(seg(t, t1, t1 + PRESS.out)));
}

/** Chip i's left and right edge in ref px. */
export function chipEdges(i: number): [number, number] {
  'worklet';
  const c = box.chips[i];
  return [c.x, c.x + c.w];
}

/** The autoPlay loop as a Change. */
export function loopChange(t: number): Change {
  'worklet';
  const first = SCRIPT[0];
  const second = SCRIPT[1];
  const back = t >= second.commit;
  const s = back ? second : first;
  const fromCity = back ? first.city : 0;
  const [l0, r0] = chipEdges(fromCity);
  const f = RANK[fromCity];
  let pressChip = -1;
  let press = 0;
  const p1 = pressAt(t, first.press, first.commit);
  const p2 = pressAt(t, second.press, second.commit);
  if (p2 > 0) {
    pressChip = second.city;
    press = p2;
  } else if (p1 > 0) {
    pressChip = first.city;
    press = p1;
  }
  const sheen = t > SHEEN.from && t < SHEEN.to ? seg(t, SHEEN.from, SHEEN.to) * SHEEN.frames : 0;
  return {
    from: fromCity,
    to: s.city,
    tau: t - s.commit,
    l0,
    r0,
    a0: f.face * 180,
    back0: f.back,
    x0: back ? STRIP_SCRIPT.to : 0,
    pressChip,
    press,
    sheen,
    reduced: false,
  };
}

/** Pill edges at a moment of a change. */
export function pillAt(c: Change): [number, number] {
  'worklet';
  const [l1, r1] = chipEdges(c.to);
  if (c.reduced) return c.tau >= REDUCED.fade / 2 ? [l1, r1] : [c.l0, c.r0];
  const right = l1 + r1 >= c.l0 + c.r0;
  const lead = spring(c.tau, PILL.lead);
  const trail = spring(c.tau, PILL.trail);
  const l = lerp(c.l0, l1, right ? trail : lead);
  const r = lerp(c.r0, r1, right ? lead : trail);
  return [l, Math.max(r, l + 1)];
}

/** The coin at a moment: angle (0…180, the Blender frame), which back number shows, lift 0…1. */
export function coinAt(c: Change) {
  'worklet';
  const target = RANK[c.to];
  const a1 = target.face * 180;
  const back1 = target.back;
  if (c.reduced) {
    const swap = c.tau >= REDUCED.fade / 2;
    return { angle: swap ? a1 : c.a0, back: swap ? back1 : c.back0, lift: 0 };
  }
  const p = easeInOut(seg(c.tau, COIN.delay, COIN.delay + COIN.flip));
  const lift = Math.sin(Math.PI * seg(c.tau, COIN.delay, COIN.delay + COIN.flip));
  if (c.a0 === 180 && a1 === 180 && c.back0 !== back1) {
    // Back to back (london ⇄ paris): turn to the edge and back, swapping the number at the edge.
    const first = p < 0.5;
    return { angle: first ? lerp(180, 90, p * 2) : lerp(90, 180, p * 2 - 1), back: first ? c.back0 : back1, lift };
  }
  if (c.a0 === a1) return { angle: a1, back: back1, lift: 0 };
  return { angle: lerp(c.a0, a1, p), back: a1 === 180 ? back1 : c.back0, lift };
}

/** Coin sprite index: 0–18 are the 3 | 7 flip, 19–27 the 1 face from 100° to 180°. */
export function coinFrame(angle: number, back: number) {
  'worklet';
  const k = Math.round(angle / COIN.step);
  if (k <= 9 || back === 0) return k;
  return COIN.frames + (k - 10);
}

/**
 * A roll (odometer or label): how far glyph `slot` (0 = rightmost) has gone, 0…1 (eased), `local`
 * seconds after its roll started. The leaving glyph moves by -dir·q·travel and fades to 0; the
 * entering one comes from dir·(1 − q)·travel and fades in (dir +1: the value grows, digits go up).
 */
export function rollQ(local: number, slot: number, dur: number, stagger: number) {
  'worklet';
  return easeOut(clamp01((local - slot * stagger) / dur));
}

/** Opacity dip of a value that changes under Reduce Motion (1 → dip → 1 over REDUCED.fade). */
export function reducedDip(tau: number) {
  'worklet';
  if (tau <= 0 || tau >= REDUCED.fade) return 1;
  return 1 - (1 - REDUCED.dip) * Math.sin((Math.PI * tau) / REDUCED.fade);
}

/* ───────────────────────── Departure board ───────────────────────── */

/** A flap's drum: blank, then 0…9 (it only turns one way). */
export const DRUM = ' 0123456789';

export const BOARD = {
  /** Camera distance for the flaps' perspective, in pt. */
  persp: { digit: 140, photo: 520 },
  digit: {
    delay: 0.06, // first tile, after the commit
    tileStagger: 0.07,
    slotStagger: 0.03, // left to right inside a tile
    step: 0.075, // s per flap (a 54 ref px card falls fast)
    cardIn: 0.09, // the flap card fades in before its first flap
    cardOut: 0.24, // and out after its landing
    /** The split-flap card a digit turns into (ref px): black like the selection pill, the digit white. */
    card: { w: 42, padY: 10, r: 7, spread: 13 },
  },
  // The count sits between "view all" and "photos": its cards do not spread, they stand edge to edge.
  count: { delay: 0.32, slotStagger: 0.03, step: 0.075, card: { w: 25, padY: 6, r: 4, spread: 0 } },
  /** Card colours (top half, bottom half) and the gap across its hinge (the tile's fill). */
  cardTop: '#202020',
  cardBottom: '#141414',
  cardGap: '#F8F8F8',
  photo: {
    delay: 0.1,
    cellStagger: 0.08, // left to right
    step: 0.17, // a 140 pt flap falls slower than a digit's
    steps: 3, // old → two photos of the city → the city's photo
  },
  /** The last flap of a cell lands and bounces back up a little (degrees, closed-form damped). */
  bounce: { amp: 16, omega: 34, zeta: 0.34, dur: 0.28 },
  /** Motion blur: trailing copies of a falling flap over one 60 Hz shutter. */
  blur: { shutter: 1 / 60, alpha: [0.34, 0.2, 0.09] },
  /** Shading of a flap and of what it uncovers (black / white overlay alpha). */
  shade: { flapDark: 0.42, flapLight: 0.16, glint: 0.22, uncover: 0.32, cover: 0.36, seam: 0.38 },
} as const;

/** Photo strip contents: indices into the block's photo list (see TasteProfile IMG.photos). */
export const STRIP = [
  [0, 1, 2, 3, 4, 10, 16, 5], // all: the pin's four, then a few from each city
  [4, 5, 6, 7, 8, 9], // new york
  [10, 11, 12, 13, 14, 15], // london
  [16, 17, 18, 19, 20, 21], // paris
] as const;
/** "view all N photos" per city (right-aligned against "photos"). */
export const PHOTO_COUNT = ['1470', '958', '312', '200'] as const;

/** Flip angle of one flap `u` (0…1) into its fall: it falls under gravity, 180·u². */
export function fallAngle(u: number) {
  'worklet';
  return 180 * u * u;
}

/** Angular speed (deg/s) of the fall at phase u for a step of `step` s. */
export function fallSpeed(u: number, step: number) {
  'worklet';
  return (360 * u) / step;
}

/** The landing bounce (degrees lifted off the stop) `t` s after the last flap hit it. */
export function bounceAt(t: number) {
  'worklet';
  if (t <= 0 || t >= BOARD.bounce.dur) return 0;
  const { amp, omega, zeta } = BOARD.bounce;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  return amp * Math.exp(-zeta * omega * t) * Math.abs(Math.sin(wd * t));
}

/**
 * One flap cell `local` s after its first flap was due, turning `n` flaps: how many flaps are done,
 * the falling flap's angle (0…180, -1 none) and the landing bounce of the last one.
 */
export function flapAt(local: number, n: number, step: number) {
  'worklet';
  if (n <= 0 || local < 0) return { k: 0, angle: -1, speed: 0, bounce: 0 };
  const k = Math.floor(local / step);
  if (k >= n) return { k: n, angle: -1, speed: 0, bounce: bounceAt(local - n * step) };
  const u = local / step - k;
  return { k, angle: fallAngle(u), speed: fallSpeed(u, step), bounce: 0 };
}

/** Drum steps from character a to b. */
export function drumSteps(a: string, b: string) {
  'worklet';
  const i = DRUM.indexOf(a);
  const j = DRUM.indexOf(b);
  return (j - i + DRUM.length) % DRUM.length;
}

/** The character `k` flaps after `a` on the drum. */
export function drumChar(a: string, k: number) {
  'worklet';
  return DRUM[(DRUM.indexOf(a) + k) % DRUM.length];
}

/** End of the board (s after the commit): everything has landed and faded. */
export const BOARD_END = SETTLE;

/* ───────────────────────── Cover-flow (photo strip) ───────────────────────── */

export const COVER = {
  rotate: 52, // deg at the edge of the view
  span: 1.6, // photos from the centre where the rotation is full
  squeeze: 0.2, // side photos move in by this share of a photo per photo from the centre
  shrink: 0.1, // side photos shrink by this much
  dim: 0.3, // side photos darken by this much
  radius: 0.7, // corner radius of a separated photo, share of the strip's
  inset: 0.05, // a separated photo is this much narrower (gaps between photos)
  grow: 60, // ref px the strip's clip grows vertically when separated
  /** Separation in/out (the strip becomes cards while a finger is on it or it is still moving). */
  in: 0.22,
  out: 0.3,
  /** Snap spring after release (a finger was involved: velocity carried in). */
  snap: { omega: 10, zeta: 0.82 },
  /** Release projects the position by velocity × this (s), then snaps to the nearest photo. */
  project: 0.22,
  rubber: 0.35,
} as const;

/** A spring from `from` to `to` with initial velocity v0 (units/s), `tau` s after it started. */
export function springV(tau: number, from: number, to: number, v0: number, s: { omega: number; zeta: number }) {
  'worklet';
  if (tau <= 0) return from;
  const { omega, zeta } = s;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const A = from - to;
  const B = (v0 + zeta * omega * A) / wd;
  const env = Math.exp(-zeta * omega * tau);
  if (env < 1e-4) return to;
  return to + env * (A * Math.cos(wd * tau) + B * Math.sin(wd * tau));
}

/** The strip in the loop: offset x (photos) and separation k (0 flat … 1 cover-flow). */
export function stripAt(t: number) {
  'worklet';
  const s = STRIP_SCRIPT;
  if (t < s.down || t >= SCRIPT[1].commit) return { x: 0, k: 0 };
  const k = t < s.flatAt ? easeOut(seg(t, s.down, s.down + COVER.in)) : 1 - easeInOut(seg(t, s.flatAt, s.flatAt + COVER.out));
  if (t < s.release) {
    // The finger speeds up into a flick (x = d·p²), so the release velocity is 2d / drag time.
    const p = seg(t, s.down, s.release);
    return { x: s.dragTo * p * p, k };
  }
  const v0 = (2 * s.dragTo) / (s.release - s.down);
  return { x: springV(t - s.release, s.dragTo, s.to, v0, COVER.snap), k };
}

/* ───────────────────────── Hold a tile: bar chart ───────────────────────── */

export const EXPAND = {
  hold: 0.35, // s of pressing before the tile grows
  spring: { omega: 17, zeta: 0.84 }, // ≈ { duration: 420, dampingRatio: 0.84 }
  out: 0.24, // s, ease-out, closing
  bars: { delay: 0.1, stagger: 0.045, spring: { omega: 19, zeta: 0.72 } },
  /** Card height in ref px (two tiles wide); the veil is `chart.veil` in theme.ts. */
  h: chart.h,
  shadow: { from: 0.05, to: 0.16 },
} as const;

export const MONTHS = ['may', 'jun', 'jul', 'aug', 'sep', 'oct'] as const;
/** Monthly share of a stat (per stat: favorites, recommend, okay, disliked); each row adds up to 1. */
const SHARE = [
  [0.11, 0.14, 0.12, 0.18, 0.2, 0.25],
  [0.13, 0.12, 0.17, 0.16, 0.19, 0.23],
  [0.2, 0.18, 0.15, 0.16, 0.14, 0.17],
  [0.24, 0.12, 0.2, 0.14, 0.18, 0.12],
] as const;

/** Bars for stat k in city c: whole numbers that add up to the tile's number. */
export function monthBars(c: number, k: number) {
  const total = Number(STATS[c][k]);
  const out: number[] = [];
  let used = 0;
  for (let i = 0; i < 6; i++) {
    const v = i === 5 ? total - used : Math.round(total * SHARE[k][i]);
    out.push(v);
    used += v;
  }
  return out;
}

/** A hold on a tile: pressed at t0, grown at t1 (-1: let go before), let go at t2. */
export type Hold = { tile: number; t0: number; t1: number; t2: number };
export const NO_HOLD: Hold = { tile: -1, t0: -1, t1: -1, t2: -1 };
export const HOLD_SCRIPT: Hold = { tile: 0, t0: 4.35, t1: 4.7, t2: 6.0 };

/** The hold at time t: press (0…1), growth e (0…1), seconds since it grew (for the bars). */
export function holdAt(t: number, h: Hold) {
  'worklet';
  if (h.tile < 0 || t < h.t0) return { tile: -1, press: 0, e: 0, since: 0 };
  const commit = h.t1 >= 0 ? h.t1 : h.t2;
  const press = pressAt(t, h.t0, commit);
  if (h.t1 < 0 || t < h.t1) return { tile: press > 0 ? h.tile : -1, press, e: 0, since: 0 };
  let e = spring(Math.min(t, h.t2) - h.t1, EXPAND.spring);
  if (t > h.t2) e *= 1 - easeOut(seg(t, h.t2, h.t2 + EXPAND.out));
  return { tile: e > 0.0005 || press > 0 ? h.tile : -1, press, e: t >= h.t2 + EXPAND.out ? 0 : e, since: t - h.t1 };
}
