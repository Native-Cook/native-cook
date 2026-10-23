/**
 * Price Tag Welcome — motion. Every value lives here so the Swift and Compose ports can copy it
 * value for value. Distances are in ref px (1206 px = the screen's width), times in seconds.
 *
 * The hero — freeze the impulse: a tap on a product freezes its price. Ice crystals grow from the
 * tag over the whole sticker (a runtime shader, `frost-shader.ts`), the product cools and greys,
 * the price blurs under the rime, and "Wait 24h" is etched into the tag while a countdown etched
 * over the product ticks down from 24:00:00. The tag, swinging from the tap, is frozen mid-swing.
 * A second tap thaws it: the outer cells melt first, drops run down, the price comes back and the
 * tag, released, swings home. The row is still a conveyor the finger flings (tags swing as damped
 * pendulums driven by its acceleration); a frozen tag stays rigid and the row drags heavier.
 *
 * autoPlay (every value a pure function of the loop time t, 0 ≤ t < LOOP):
 *
 *   0.00–0.80  rest = the pin
 *   0.80–0.90  a finger presses the laptop (press 1 → 0.97); 0.90 release = FREEZE (τ = 0)
 *   0.90–2.15  frost grows from the tag over the sticker; the tag kicked by the tap locks at τ 0.19
 *   0.95–1.30  price blurs out (a real blur, to a 22 % ghost) · 1.30–1.70 "Wait 24h" etched · 1.75–2.20 countdown etched (24:00:00)
 *   2.75       first tick: every digit rolls at once to 23:59:59 · 3.75 :58
 *   4.30–4.40  press again; 4.40 THAW: cells melt outside-in over 1.4 s, drops run, tag released
 *   4.70–7.00  the tag swings home and rings down; a seam window fades the last ~0.5° to zero
 *   7.00–8.00  rest
 *
 * The pendulums are integrated at 240 Hz from the script once, at load, into a table (the ports do
 * the same integration).
 */

import { copy, items, PERIOD, PIVOT, REF_W, space, WRAP_LEFT } from './theme';

export const LOOP = 8;
export const N = 5;
/** After the last touch the screen waits this long, eases home (thawing), and the loop restarts. */
export const RESUME_AFTER = 2;

/** The scripted taps (s). */
export const SCRIPT = {
  pressIn: 0.8,
  freeze: 0.9, // release of the first tap
  tapItem: 1, // the laptop
  thawPress: 4.3,
  thaw: 4.4,
  seam: [6.6, 7.4] as const, // pendulum residue faded to exactly zero
} as const;

/**
 * The freeze, in seconds after the tap (τ). `grow`: time for the front to cover the sticker
 * (cubic ease-out of the radius); `lockAt`: the frost holds the tag (it was kicked by the tap and is
 * near the top of its swing); `unlockAt`: seconds after the thaw tap that release it; `melt`: thaw
 * duration; `kick`: rad/s the tap gives the tag; `drag`: momentum time constant × this while
 * anything is frozen (the row drags heavier).
 */
export const FREEZE = { grow: 1.25, lockAt: 0.19, unlockAt: 0.3, melt: 1.4, kick: 0.9, drag: 0.55 } as const;
/** Text in and out of the frost, τ windows (s). Melt windows are seconds after the thaw tap. */
export const ETCH = {
  priceOut: [0.05, 0.4] as const,
  wait: [0.4, 0.8] as const,
  timer: [0.85, 1.3] as const,
  firstTick: 1.85,
  out: [0.0, 0.4] as const,
  priceIn: [0.35, 0.7] as const,
  /** What is left of the price under the ice (opacity). */
  priceGhost: 0.22,
  /** The frozen price's blur radius (pt at 402 pt): a real Gaussian blur of the price, bounded by the tag. */
  priceBlur: 3.5,
} as const;
/** A digit rolls to the next on each tick: underdamped spring 0 → 1. */
export const TICK = { omega: 22, zeta: 0.62 } as const;
/** Reduce Motion: the frost and the text cross-fade in this long. */
export const STILL_FADE = 0.2;

/** The tag pendulums: natural frequency (Hz), damping ratio, per-tag tuning, drive gain. */
export const SWING = {
  hz: 1.35,
  zeta: 0.13,
  tune: [1.0, 0.94, 1.06, 0.97, 1.03] as const,
  /** rad/s² of angular acceleration per ref px/s² of row acceleration. */
  gain: 0.00095,
  /** Soft limit: displayed deviation = MAX · tanh(dev / MAX), degrees. */
  maxDeg: 24,
} as const;

/** Press feedback on a sticker and on the button: in fast, out slower. */
export const PRESS = { scale: 0.97, in: 0.1, out: 0.2 } as const;
/** Momentum after a fling (s): v · e^(−t/τ). */
export const DECAY_TAU = 0.36;
/** Easing home after RESUME_AFTER: duration = base + perPx · |distance|, capped; thaws staggered. */
export const HOME = { base: 0.45, perPx: 0.0005, max: 1.1, thawStagger: 0.06 } as const;
/** The card's glint: visible while the row moves; full at this speed (ref px/s). */
export const GLINT = { fullSpeed: 1500, alpha: 0.5 } as const;
/** Tap vs drag: a touch that moves less than this (ref px) is a tap. */
export const TAP_SLOP = 30;
/**
 * The frost canvases of an item are mounted only while it is on screen: its sticker plus the rime
 * halo (`space.frostMargin`), plus this lead (ref px) so an item fed in by the conveyor mounts just
 * before it shows. An unfrozen frost canvas draws nothing, so a mount a frame late is invisible.
 */
export const MOUNT_LEAD = 60;

const TABLE_HZ = 120;
const SIM_HZ = 240;

// ─── helpers (declared before every caller: worklets cannot see later declarations) ───

export function clockNow() {
  'worklet';
  return performance.now() / 1000;
}
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
export function mod(a: number, n: number) {
  'worklet';
  return ((a % n) + n) % n;
}
export function smoothstep(p: number) {
  'worklet';
  const x = clamp01(p);
  return x * x * (3 - 2 * x);
}
export function easeInOut(p: number) {
  'worklet';
  const x = clamp01(p);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
/** Where item i is drawn: its rest origin + offset, wrapped into the conveyor's window (ref px). */
export function wrapX(restX: number, offset: number) {
  'worklet';
  return WRAP_LEFT + mod(restX + offset - WRAP_LEFT, PERIOD);
}
/** Step response of an underdamped spring 0 → 1 after τ seconds, and its velocity. */
export function springStep(tau: number, omega: number, zeta: number) {
  'worklet';
  if (tau <= 0) return { x: 0, v: 0 };
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const e = Math.exp(-zeta * omega * tau);
  const c = Math.cos(wd * tau);
  const s = Math.sin(wd * tau);
  return { x: 1 - e * (c + ((zeta * omega) / wd) * s), v: e * ((omega * omega) / wd) * s };
}

// ─── the freeze (shared by autoPlay and touch) ─────────────────────────────

/**
 * What a tag and its sticker show, from τ = seconds since the freeze tap (−1: not frozen) and
 * μ = seconds since the thaw tap (−1: not thawing). `grow` 0 … 1 of the frost's reach, `melt` 0 … 1,
 * `fade` the frost's opacity (1 except under Reduce Motion), and the text layers: `priceA` the
 * price's opacity, `lost` 0 … 1 how far it has gone under the ice (its blur and the cross-fade from
 * the crisp price to the blurred one).
 */
export function frostLook(ft: number, mt: number, still: boolean) {
  'worklet';
  if (ft < 0) return { on: false, grow: 0, melt: 0, fade: 0, priceA: 1, lost: 0, wait: 0, timer: 0, out: 0 };
  const melt = mt < 0 ? 0 : clamp01(mt / FREEZE.melt);
  if (still) {
    const inA = clamp01(ft / STILL_FADE) * (mt < 0 ? 1 : 1 - clamp01(mt / STILL_FADE));
    return { on: true, grow: 1, melt: 0, fade: inA, priceA: lerp(1, ETCH.priceGhost, inA), lost: inA, wait: inA, timer: inA, out: 0 };
  }
  const g = clamp01(ft / FREEZE.grow);
  const grow = 1 - (1 - g) * (1 - g) * (1 - g);
  const gone = smoothstep(seg(ft, ETCH.priceOut[0], ETCH.priceOut[1]));
  const back = mt < 0 ? 0 : smoothstep(seg(mt, ETCH.priceIn[0], ETCH.priceIn[1]));
  const lost = gone * (1 - back);
  // `lost` drives the price's blur (0 … ETCH.priceBlur) as it fades to a ghost under the tag frost.
  return {
    on: true,
    grow,
    melt,
    fade: 1,
    priceA: lerp(1, ETCH.priceGhost, lost),
    lost,
    wait: seg(ft, ETCH.wait[0], ETCH.wait[1]),
    timer: seg(ft, ETCH.timer[0], ETCH.timer[1]),
    out: mt < 0 ? 0 : smoothstep(seg(mt, ETCH.out[0], ETCH.out[1])),
  };
}

/** The countdown at τ: seconds left now and before the last tick, and the roll between them (0 … 1+). */
export function countdownAt(ft: number, still: boolean) {
  'worklet';
  if (ft < ETCH.firstTick) return { left: copy.countdown, prev: copy.countdown, roll: 1 };
  const n = Math.floor(ft - ETCH.firstTick) + 1;
  const since = ft - ETCH.firstTick - (n - 1);
  return { left: copy.countdown - n, prev: copy.countdown - n + 1, roll: still ? 1 : springStep(since, TICK.omega, TICK.zeta).x };
}

/** Digit `col` (0 … 5 of hh mm ss) of a number of seconds. */
export function digitOf(secs: number, col: number) {
  'worklet';
  const hh = Math.floor(secs / 3600);
  const mm = Math.floor(secs / 60) % 60;
  const ss = secs % 60;
  const v = col < 2 ? hh : col < 4 ? mm : ss;
  return col % 2 === 0 ? Math.floor(v / 10) % 10 : v % 10;
}

/**
 * Where a rolling digit column stands, as a line of the strip "0 1 2 … 9 0" (line 10 is a second 0).
 * A countdown digit rolls downwards: the new digit enters from above. From 0 it rolls from line 10.
 */
export function stripLine(from: number, to: number, roll: number) {
  'worklet';
  if (from === to) return to;
  const a = from === 0 ? 10 : from;
  return a + (to - a) * roll;
}

// ─── the script (autoPlay) ─────────────────────────────────────────────────

/** Freeze clocks of item i in autoPlay: τ and μ (−1 when not frozen / not thawing). */
export function scriptFrost(i: number, t: number) {
  'worklet';
  if (i !== SCRIPT.tapItem || t < SCRIPT.freeze) return { ft: -1, mt: -1 };
  const mt = t >= SCRIPT.thaw ? t - SCRIPT.thaw : -1;
  if (mt >= FREEZE.melt) return { ft: -1, mt: -1 };
  return { ft: t - SCRIPT.freeze, mt };
}

/** Press depth of item i in autoPlay (0 … 1). */
export function scriptPress(i: number, t: number) {
  'worklet';
  if (i !== SCRIPT.tapItem) return 0;
  const hold = (a: number, b: number) => smoothstep(seg(t, a, a + PRESS.in)) * (1 - smoothstep(seg(t, b, b + PRESS.out)));
  return Math.max(hold(SCRIPT.pressIn, SCRIPT.freeze), hold(SCRIPT.thawPress, SCRIPT.thaw));
}

function omegaOf(i: number) {
  'worklet';
  return 2 * Math.PI * SWING.hz * SWING.tune[i];
}

/** One semi-implicit Euler step of a tag's pendulum (deviation from its rest tilt, radians). */
export function swingStep(th: number, w: number, i: number, a: number, dt: number) {
  'worklet';
  const om = omegaOf(i);
  const acc = -om * om * th - 2 * SWING.zeta * om * w + SWING.gain * a;
  const w2 = w + acc * dt;
  return { th: th + w2 * dt, w: w2 };
}

/** Is a tag held by its frost (frozen, the lock reached, not yet released by the thaw)? */
export function lockedBy(ft: number, mt: number) {
  'worklet';
  return ft >= FREEZE.lockAt && (mt < 0 || mt < FREEZE.unlockAt);
}

/** Integrates the five pendulums over one loop of the script; returns deviation tables (rad). */
function buildSwingTable(): number[][] {
  const steps = Math.round(LOOP * SIM_HZ);
  const every = SIM_HZ / TABLE_HZ;
  const out: number[][] = [];
  for (let i = 0; i < N; i++) {
    let th = 0;
    let w = 0;
    const row: number[] = [0];
    for (let s = 1; s <= steps; s++) {
      const t = s / SIM_HZ;
      if (i === SCRIPT.tapItem && t - 1 / SIM_HZ < SCRIPT.freeze && t >= SCRIPT.freeze) w += FREEZE.kick;
      const f = scriptFrost(i, t);
      if (lockedBy(f.ft, f.mt)) w = 0;
      else {
        const r = swingStep(th, w, i, 0, 1 / SIM_HZ);
        th = r.th;
        w = r.w;
      }
      if (s % every === 0) row.push(th);
    }
    out.push(row);
  }
  return out;
}
export const SWING_TABLE: number[][] = buildSwingTable();

/** Deviation (rad) and angular velocity of tag i in autoPlay at loop time t. */
export function scriptSwing(i: number, t: number) {
  'worklet';
  const tb = SWING_TABLE[i];
  const f = t * TABLE_HZ;
  const k = Math.min(Math.floor(f), tb.length - 2);
  const p = f - k;
  const seam = 1 - smoothstep(seg(t, SCRIPT.seam[0], SCRIPT.seam[1]));
  return { th: lerp(tb[k], tb[k + 1], p) * seam, w: (tb[k + 1] - tb[k]) * TABLE_HZ * seam };
}

// ─── frame ────────────────────────────────────────────────────────────────

/** Everything the screen draws, per frame. Per-item arrays have N entries. */
export type Frame = {
  x: number; // row offset, ref px
  v: number; // row velocity, ref px/s
  th: number[]; // tag deviation from its rest tilt, degrees (after the soft limit)
  ft: number[]; // seconds since the freeze tap, −1 when not frozen
  mt: number[]; // seconds since the thaw tap, −1 when not thawing
  press: number[]; // 0 … 1
  clock: number; // s, for the twinkle (loop time in autoPlay)
  glint: number; // 0 … 1 across the card
  glintA: number; // 0 … 1
};

const ZEROS = [0, 0, 0, 0, 0];
const NONE = [-1, -1, -1, -1, -1];
export const REST_FRAME: Frame = { x: 0, v: 0, th: ZEROS, ft: NONE, mt: NONE, press: ZEROS, clock: 0, glint: 0, glintA: 0 };

function softDeg(rad: number) {
  'worklet';
  const d = (rad * 180) / Math.PI;
  return SWING.maxDeg * Math.tanh(d / SWING.maxDeg);
}

function glintOf(x: number, v: number) {
  'worklet';
  return { glint: mod(-x, PERIOD) / PERIOD, glintA: smoothstep(Math.abs(v) / GLINT.fullSpeed) };
}

/** The autoPlay frame at loop time t (the row stands still; the freeze is the show). */
export function frameAt(t: number): Frame {
  'worklet';
  const th: number[] = [];
  const ft: number[] = [];
  const mt: number[] = [];
  const press: number[] = [];
  for (let i = 0; i < N; i++) {
    th.push(softDeg(scriptSwing(i, t).th));
    const f = scriptFrost(i, t);
    ft.push(f.ft);
    mt.push(f.mt);
    press.push(scriptPress(i, t));
  }
  return { x: 0, v: 0, th, ft, mt, press, clock: t, glint: 0, glintA: 0 };
}

// ─── engine (autoPlay ⇄ touch) ──────────────────────────────────────────────

export const AUTO = 0;
export const LIVE = 1;
export const RETURN = 2; // easing home (and thawing) before the loop restarts

export type Engine = {
  mode: number;
  autoPlay: boolean;
  still: boolean; // Reduce Motion
  autoStart: number; // clock at loop time 0
  last: number; // clock of the previous step
  lastTouch: number;
  // row
  x: number;
  v: number;
  a: number; // filtered acceleration
  dragging: boolean;
  dragX0: number; // row offset when the drag began
  dragTx: number; // latest translation (ref px)
  // per tag
  th: number[]; // rad
  w: number[];
  frozeAt: number[]; // clock of the freeze tap, −1
  thawAt: number[]; // clock of the thaw tap, −1
  press: number[];
  pressTarget: number[];
  // return home
  homeAt: number;
  homeFrom: number;
  homeTo: number;
  homeDur: number;
};

export function createEngine(now: number, autoPlay: boolean, still: boolean): Engine {
  'worklet';
  return {
    mode: autoPlay && !still ? AUTO : LIVE,
    autoPlay,
    still,
    autoStart: now,
    last: now,
    lastTouch: -1e9,
    x: 0,
    v: 0,
    a: 0,
    dragging: false,
    dragX0: 0,
    dragTx: 0,
    th: [0, 0, 0, 0, 0],
    w: [0, 0, 0, 0, 0],
    frozeAt: [-1, -1, -1, -1, -1],
    thawAt: [-1, -1, -1, -1, -1],
    press: [0, 0, 0, 0, 0],
    pressTarget: [0, 0, 0, 0, 0],
    homeAt: -1,
    homeFrom: 0,
    homeTo: 0,
    homeDur: 0,
  };
}

/** Leave autoPlay (or the return home) for touch: carry over every value on screen as it is. */
export function takeOver(e0: Engine, now: number): Engine {
  'worklet';
  if (e0.mode === LIVE) return { ...e0, lastTouch: now };
  if (e0.mode === RETURN) return { ...e0, mode: LIVE, homeAt: -1, lastTouch: now };
  const t = mod(now - e0.autoStart, LOOP);
  const th: number[] = [];
  const w: number[] = [];
  const frozeAt: number[] = [];
  const thawAt: number[] = [];
  const press: number[] = [];
  for (let i = 0; i < N; i++) {
    const s = scriptSwing(i, t);
    th.push(s.th);
    w.push(s.w);
    const f = scriptFrost(i, t);
    frozeAt.push(f.ft < 0 ? -1 : now - f.ft);
    thawAt.push(f.mt < 0 ? -1 : now - f.mt);
    press.push(scriptPress(i, t));
  }
  return { ...e0, mode: LIVE, lastTouch: now, last: now, x: 0, v: 0, a: 0, th, w, frozeAt, thawAt, press, pressTarget: [0, 0, 0, 0, 0] };
}

/** Finger down on the row (on item `hit`, or −1 between stickers). */
export function touchDown(e0: Engine, now: number, hit: number): Engine {
  'worklet';
  const e = takeOver(e0, now);
  const pressTarget = [0, 0, 0, 0, 0];
  if (hit >= 0) pressTarget[hit] = 1;
  // A finger on a gliding row stops it, as on any scroll view.
  return { ...e, v: 0, pressTarget };
}

export function dragBegin(e0: Engine, now: number): Engine {
  'worklet';
  const e = takeOver(e0, now);
  return { ...e, dragging: true, dragX0: e.x, dragTx: 0, v: 0, pressTarget: [0, 0, 0, 0, 0] };
}

export function dragMove(e: Engine, now: number, tx: number): Engine {
  'worklet';
  return { ...e, dragTx: tx, lastTouch: now };
}

/** Finger up after a drag: momentum from the gesture's velocity (ref px/s). */
export function dragEnd(e: Engine, now: number, vx: number): Engine {
  'worklet';
  return { ...e, dragging: false, v: vx, lastTouch: now, pressTarget: [0, 0, 0, 0, 0] };
}

/** Is item i frozen and not thawing? */
export function isFrozen(e: Engine, i: number) {
  'worklet';
  return e.frozeAt[i] >= 0 && e.thawAt[i] < 0;
}

/** A tap on item i: freeze its price, or thaw it. */
export function tapItem(e0: Engine, now: number, i: number): Engine {
  'worklet';
  const e = takeOver(e0, now);
  const frozeAt = e.frozeAt.slice();
  const thawAt = e.thawAt.slice();
  const w = e.w.slice();
  if (isFrozen(e, i)) thawAt[i] = now;
  else if (thawAt[i] >= 0 && !e.still) {
    // Refrozen mid-thaw: the front restarts at the edge of the ice that is still there (the melt
    // runs outside-in), so the remaining cells stay and the melted ones grow back.
    const m = clamp01((now - thawAt[i]) / FREEZE.melt);
    const q = clamp01(1 - (m - 0.16) / 0.62);
    frozeAt[i] = now - (1 - Math.cbrt(1 - q)) * FREEZE.grow;
    thawAt[i] = -1;
  } else {
    frozeAt[i] = now;
    thawAt[i] = -1;
    if (!e.still) w[i] += FREEZE.kick;
  }
  return { ...e, frozeAt, thawAt, w, pressTarget: [0, 0, 0, 0, 0] };
}

/** Releases a press that did not become a tap or a drag. */
export function touchCancel(e0: Engine, now: number): Engine {
  'worklet';
  return { ...e0, pressTarget: [0, 0, 0, 0, 0], lastTouch: now };
}

export function setAutoPlay(e: Engine, autoPlay: boolean, still: boolean, now: number): Engine {
  'worklet';
  if (still) {
    // Reduce Motion: no loop; the pin's frame, tags straight; frozen prices stay frozen.
    const frozeAt = [-1, -1, -1, -1, -1];
    for (let i = 0; i < N; i++) if (isFrozen(e, i)) frozeAt[i] = now - 1;
    return { ...createEngine(now, autoPlay, true), mode: LIVE, frozeAt };
  }
  if (autoPlay && e.mode === LIVE && !e.dragging) return { ...e, autoPlay, still, lastTouch: now };
  if (!autoPlay && e.mode === AUTO) return { ...takeOver(e, now), autoPlay, still };
  return { ...e, autoPlay, still };
}

function anyMoving(e: Engine) {
  'worklet';
  if (e.dragging || Math.abs(e.v) > 2) return true;
  for (let i = 0; i < N; i++) {
    if (Math.abs(e.th[i]) > 0.0015 || Math.abs(e.w[i]) > 0.01) return true;
    if (Math.abs(e.press[i] - e.pressTarget[i]) > 0.002) return true;
    if (e.frozeAt[i] >= 0) return true; // the countdown ticks, the facets twinkle
  }
  return false;
}

/** Advances the engine to `now` and returns the frame to draw, plus whether anything still moves. */
export function step(e0: Engine, now: number): { e: Engine; f: Frame; moving: boolean } {
  'worklet';
  if (e0.mode === AUTO) {
    const t = mod(now - e0.autoStart, LOOP);
    return { e: { ...e0, last: now }, f: frameAt(t), moving: true };
  }
  const e = { ...e0, th: e0.th.slice(), w: e0.w.slice(), frozeAt: e0.frozeAt.slice(), thawAt: e0.thawAt.slice(), press: e0.press.slice() };
  const dt = Math.min(Math.max(now - e0.last, 0), 1 / 20);
  e.last = now;

  // A thawed item is done once its melt has run.
  let heavy = false;
  for (let i = 0; i < N; i++) {
    if (e.thawAt[i] >= 0 && now - e.thawAt[i] >= (e.still ? STILL_FADE : FREEZE.melt)) {
      e.frozeAt[i] = -1;
      e.thawAt[i] = -1;
    }
    if (isFrozen(e, i)) heavy = true;
  }

  // Row: finger, momentum (heavier while something is frozen), or the way home.
  const v0 = e.v;
  if (e.dragging) {
    const x = e.dragX0 + e.dragTx;
    const v = dt > 0 ? (x - e.x) / dt : e.v;
    e.v = lerp(e.v, v, dt > 0 ? 1 - Math.exp(-dt / 0.03) : 0);
    e.x = x;
  } else if (e.mode === RETURN) {
    const p = seg(now, e.homeAt, e.homeAt + e.homeDur);
    const x = lerp(e.homeFrom, e.homeTo, easeInOut(p));
    e.v = dt > 0 ? (x - e.x) / dt : 0;
    e.x = x;
  } else if (e.v !== 0) {
    const tau = heavy ? DECAY_TAU * FREEZE.drag : DECAY_TAU;
    e.v = Math.abs(e.v) < 4 ? 0 : e.v * Math.exp(-dt / tau);
    e.x += e.v * dt;
  }
  const aRaw = dt > 0 ? (e.v - v0) / dt : 0;
  e.a = lerp(e.a, aRaw, dt > 0 ? 1 - Math.exp(-dt / 0.025) : 0);
  // x stays unwrapped (a wrap here would read as a PERIOD-long jump in velocity); wrapX draws it.

  // Pendulums (held while frozen) and presses: fixed 1/240 s sub-steps.
  const n = Math.max(1, Math.ceil(dt * SIM_HZ));
  const h = dt / n;
  for (let i = 0; i < N; i++) {
    const ft = e.frozeAt[i] < 0 ? -1 : now - e.frozeAt[i];
    const mt = e.thawAt[i] < 0 ? -1 : now - e.thawAt[i];
    if (e.still) {
      e.th[i] = 0;
      e.w[i] = 0;
    } else if (lockedBy(ft, mt)) {
      e.w[i] = 0;
    } else {
      for (let s = 0; s < n; s++) {
        const r = swingStep(e.th[i], e.w[i], i, e.a, h);
        e.th[i] = r.th;
        e.w[i] = r.w;
      }
    }
    const pt = e.pressTarget[i];
    const k = pt > e.press[i] ? PRESS.in : PRESS.out;
    e.press[i] = lerp(e.press[i], pt, 1 - Math.exp((-dt * 3) / k));
  }

  // Idle: after RESUME_AFTER, ease home and thaw, then restart the loop from its first frame.
  if (e.autoPlay && !e.still && e.mode === LIVE && !e.dragging && now - e.lastTouch > RESUME_AFTER && Math.abs(e.v) < 40) {
    // Home is the nearest offset that draws the rest frame (a whole number of periods away).
    const to = Math.round(e.x / PERIOD) * PERIOD;
    e.mode = RETURN;
    e.homeAt = now;
    e.homeFrom = e.x;
    e.homeTo = to;
    e.homeDur = Math.min(HOME.max, HOME.base + HOME.perPx * Math.abs(e.x - to));
    e.v = 0;
  }
  if (e.mode === RETURN) {
    // Frozen prices thaw, left to right, thawStagger apart.
    for (let i = 0; i < N; i++) if (isFrozen(e, i) && now >= e.homeAt + HOME.thawStagger * i) e.thawAt[i] = now;
  }
  if (e.mode === RETURN && now >= e.homeAt + e.homeDur) {
    e.x = 0;
    e.v = 0;
    let settled = true;
    for (let i = 0; i < N; i++) {
      if (Math.abs(e.th[i]) > 0.002 || Math.abs(e.w[i]) > 0.02 || e.frozeAt[i] >= 0) settled = false;
    }
    if (settled) {
      const fresh = createEngine(now, true, false);
      return { e: fresh, f: frameAt(0), moving: true };
    }
  }

  const th: number[] = [];
  const ft: number[] = [];
  const mt: number[] = [];
  for (let i = 0; i < N; i++) {
    th.push(softDeg(e.th[i]));
    ft.push(e.frozeAt[i] < 0 ? -1 : now - e.frozeAt[i]);
    mt.push(e.thawAt[i] < 0 ? -1 : now - e.thawAt[i]);
  }
  const g = glintOf(e.x, e.v);
  const f: Frame = { x: e.x, v: e.v, th, ft, mt, press: e.press.slice(), clock: e.still ? 0 : mod(now, 1000), glint: g.glint, glintA: g.glintA };
  return { e, f, moving: e.mode !== LIVE || anyMoving(e) || (e.autoPlay && !e.still) };
}

// ─── geometry ───────────────────────────────────────────────────────────────

/** Which item's sticker is under a point of the row (ref px), or −1. */
export function hitItem(px: number, py: number, offset: number) {
  'worklet';
  for (let i = 0; i < N; i++) {
    const it = items[i];
    const ox = wrapX(it.x, offset);
    const t = it.tag;
    const inSticker = px >= ox + 12 && px <= ox + it.w - 12 && py >= it.y + 12 && py <= it.y + it.h - 12;
    const tx = t.cx - it.x + ox;
    const inTag = Math.abs(px - tx) <= t.w / 2 + 12 && Math.abs(py - t.cy) <= t.h / 2 + 12;
    if (inSticker || inTag) return i;
  }
  return -1;
}

/** Bit i: item i's frost canvases (sticker + halo, with MOUNT_LEAD) overlap the screen at row offset `offset`. */
export function visibleMask(offset: number) {
  'worklet';
  const reach = space.frostMargin + MOUNT_LEAD;
  let m = 0;
  for (let i = 0; i < N; i++) {
    const it = items[i];
    const x = wrapX(it.x, offset);
    if (x + it.w + reach > 0 && x - reach < REF_W) m |= 1 << i;
  }
  return m;
}

/** The pivot of tag i relative to its centre (ref px), in the tag's own (tilted) frame. */
export function pivotOf(i: number) {
  'worklet';
  const t = items[i].tag;
  return { x: PIVOT.x * t.w, y: PIVOT.y * t.h };
}
