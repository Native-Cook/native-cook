/**
 * Owl Alarm Ruler — motion. Every value lives here so the Swift and Compose ports can copy it
 * value for value.
 *
 * The ruler's position `pos` is in ticks (one tick = five minutes); 0 is the pin's 1:05 PM.
 * In autoPlay every value on screen is a pure function of the loop time `t` (0 ≤ t < LOOP):
 *
 *   0.00–1.30  rest = the pin (a blink at 0.75)
 *   1.30–1.85  drag 1: the finger pulls the ruler left, 1:05 → 1:35, and lets go at 15.6 ticks/s
 *   1.85–3.00  momentum glide to 2:05 PM; the hour wheel carries 1 → 2
 *   3.45–3.90  drag 2: a fast flick back (−52 ticks/s)
 *   3.90–5.05  glide through noon to 11:45 AM; PM flips to AM; the light band swings
 *   5.55–6.45  drag 3: a slow drag forward; AM flips back to PM
 *   6.45–7.58  glide home to 1:05 PM
 *   7.95–8.05  the button is pressed; released at 8.05
 *   8.05–9.20  the owl startles: crouch, jump with an alarm rattle, land squash, relief blink
 *   9.20–10.0  rest
 *
 * The owl's lean, its pupils and the needle's bend follow the ruler's velocity through one
 * damped spring (FILTER). In autoPlay that spring is evaluated as a convolution of the script's
 * velocity with the spring's impulse response, so it stays a pure function of `t`; under a
 * finger the same spring is stepped exactly once per frame.
 */

import { REST_MINUTES, STEP_MINUTES, TICKS_PER_DAY } from './theme';

export const LOOP = 10;
/** After the last touch, the screen waits this long, eases home, and the loop restarts. */
export const RESUME_AFTER = 2;

/** Release: the glide lands on round(pos + v · PROJECTION) and takes 3·|distance|/|v| seconds. */
export const PROJECTION = 0.385;
export const GLIDE = { min: 0.25, max: 1.2, still: 0.28 } as const;
/** Easing home after RESUME_AFTER: duration = base + perTick · |distance|, capped. */
export const RETURN = { base: 0.55, perTick: 0.012, max: 1.3 } as const;

/** The velocity spring behind the owl's lean, its eyes and the needle (ω rad/s, ζ). */
export const FILTER = { omega: 9, zeta: 0.55 } as const;
/** Lean of the owl (deg) = −max · tanh(vel / soft). Velocity in ticks/s. */
export const LEAN = { max: 5.5, soft: 30 } as const;
/** Pupils (pt): sideways with velocity, down towards the ruler with attention. */
export const EYES = { x: 5, soft: 26, attention: 3 } as const;
/** The needle bends with the ticks passing under it (deg). */
export const NEEDLE = { max: 7, soft: 32 } as const;
/** Attention eases in and out with this time constant under a finger (s). */
export const ATTENTION_TAU = 0.16;
/** The light band turns with the time of day: amp · sin(2π · pos / ticksPerDay) degrees. */
export const SUNDIAL = { amp: 9 } as const;
/** AM / PM card flip: a spring from one face to the other. */
export const FLIP = { omega: 17, zeta: 0.68 } as const;
/** Digit wheels roll while the ruler is within ±riser/2 of a half tick. */
export const RISER = 0.36;
/** Button press: in fast, out slower (asymmetric). */
export const PRESS = { scale: 0.97, in: 0.1, out: 0.2 } as const;
/** The button's label and the a11y value update at most this often while the ruler runs (s). */
export const LABEL_GAP = 0.09;
/** Haptic ticks never closer than this (s). */
export const HAPTIC_GAP = 0.045;

/** The startle after the button: times in s from the release, distances in pt. */
export const STARTLE = {
  crouch: 0.1, // anticipation
  air: 0.4, // airtime
  height: 30,
  crouchSy: 0.07,
  crouchSx: 0.05,
  stretch: 0.07,
  rattleDeg: 4,
  rattleHz: 14,
  landSquash: 0.08,
  landDecay: 5,
  landFreq: 18,
  pupil: 0.28, // pupils shrink by this much in shock
  blinkAt: 0.98, // relief blink after landing
  shadowScale: 0.3,
  shadowFade: 0.45,
  arrow: 6, // the button's arrow nudges forward (pt)
  end: 1.15,
} as const;

export const BLINK = { close: 0.07, hold: 0.03, open: 0.1, period: 3.9, jitter: 1.4 } as const;

// ─── helpers ────────────────────────────────────────────────────────────────

/** The one clock every frame, gesture and button reads (UI runtime), in seconds. */
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

/** Solve the curve's x coordinate before sampling y; shared by all native ports. */
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

export function smoothstep(p: number) {
  'worklet';
  const x = clamp01(p);
  return x * x * (3 - 2 * x);
}

/** Cubic Hermite from p0 to p1 with end tangents m0, m1 (already multiplied by the duration). */
export function hermite(p0: number, p1: number, m0: number, m1: number, u: number) {
  'worklet';
  const u2 = u * u;
  const u3 = u2 * u;
  return (2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * p1 + (u3 - u2) * m1;
}

/** d/du of `hermite`. */
export function hermiteSlope(p0: number, p1: number, m0: number, m1: number, u: number) {
  'worklet';
  const u2 = u * u;
  return (6 * u2 - 6 * u) * p0 + (3 * u2 - 4 * u + 1) * m0 + (-6 * u2 + 6 * u) * p1 + (3 * u2 - 2 * u) * m1;
}

/** A damped spring from 0 to 1, `tau` seconds after it starts. */
export function spring(tau: number, omega: number, zeta: number) {
  'worklet';
  if (tau <= 0) return 0;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const env = Math.exp(-zeta * omega * tau);
  if (env < 1e-4) return 1;
  return 1 - env * (Math.cos(wd * tau) + ((zeta * omega) / wd) * Math.sin(wd * tau));
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

// ─── release and glide ──────────────────────────────────────────────────────

/** Where a release at `pos` with velocity `vel` (ticks/s) lands, and how long the glide takes. */
export function glidePlan(pos: number, vel: number) {
  'worklet';
  const target = Math.round(pos + vel * PROJECTION);
  const dist = target - pos;
  let dur: number = GLIDE.still;
  if (Math.abs(vel) > 0.5) dur = Math.min(GLIDE.max, Math.max(GLIDE.min, (3 * Math.abs(dist)) / Math.abs(vel)));
  return { target, dur };
}

// ─── the autoPlay script ────────────────────────────────────────────────────

/** A drag: from → to over a..b, let go at velocity v (ticks/s). The glide that follows is glidePlan's. */
const DRAGS = [
  { a: 1.3, b: 1.85, from: 0, to: 6, v: 15.6 },
  { a: 3.45, b: 3.9, from: 12, to: 4, v: -52 },
  { a: 5.55, b: 6.45, from: -16, to: -3, v: 8 },
] as const;

type Piece = { a: number; b: number; p0: number; p1: number; m0: number; m1: number };

function buildScript(): Piece[] {
  const out: Piece[] = [];
  for (const d of DRAGS) {
    const T = d.b - d.a;
    out.push({ a: d.a, b: d.b, p0: d.from, p1: d.to, m0: 0, m1: d.v * T });
    const g = glidePlan(d.to, d.v);
    out.push({ a: d.b, b: d.b + g.dur, p0: d.to, p1: g.target, m0: d.v * g.dur, m1: 0 });
  }
  return out;
}

export const SCRIPT: Piece[] = buildScript();

/** The button: pressed at PRESS_AT, released at RELEASE_AT, which also starts the startle. */
export const PRESS_AT = 7.95;
export const RELEASE_AT = 8.05;
export const BLINKS_AT = [0.75, 5.2] as const;
export const ATTENTION_IN = [1.3, 1.6] as const;
export const ATTENTION_OUT = [7.6, 7.95] as const;

/** Ruler position and its velocity (ticks, ticks/s) at loop time t. */
export function scriptPos(t: number) {
  'worklet';
  const s = mod(t, LOOP);
  let p = 0;
  for (let i = 0; i < SCRIPT.length; i++) {
    const k = SCRIPT[i];
    if (s < k.a) return { p, v: 0 };
    if (s < k.b) {
      const T = k.b - k.a;
      const u = (s - k.a) / T;
      return { p: hermite(k.p0, k.p1, k.m0, k.m1, u), v: hermiteSlope(k.p0, k.p1, k.m0, k.m1, u) / T };
    }
    p = k.p1;
  }
  return { p, v: 0 };
}

// The velocity spring as a convolution kernel (weights sum to 1).
const KERNEL_DT = 1 / 60;
const KERNEL_N = 61;

function buildKernel(): number[] {
  const { omega, zeta } = FILTER;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const w: number[] = [];
  let sum = 0;
  for (let n = 0; n < KERNEL_N; n++) {
    const tau = n * KERNEL_DT;
    const h = ((omega * omega) / wd) * Math.exp(-zeta * omega * tau) * Math.sin(wd * tau);
    w.push(h);
    sum += h;
  }
  return w.map((h) => h / sum);
}

export const KERNEL: number[] = buildKernel();

/** The velocity spring's output at loop time t (ticks/s). */
export function scriptVel(t: number) {
  'worklet';
  let x = 0;
  for (let n = 0; n < KERNEL.length; n++) x += KERNEL[n] * scriptPos(t - n * KERNEL_DT).v;
  return x;
}

/** Meridiem of a ruler position: 0 = PM side of the pin's day, flips every 12 h. */
export function meridiem(pos: number) {
  'worklet';
  const m = mod(REST_MINUTES / STEP_MINUTES + Math.round(pos), TICKS_PER_DAY);
  return m < TICKS_PER_DAY / 2 ? 1 : 0; // 1 = AM
}

/** Every AM/PM crossing of the script: loop time and direction (+1 forward in time). */
function buildCrossings(): { t: number; dir: number }[] {
  const out: { t: number; dir: number }[] = [];
  let prev = Math.round(scriptPos(0).p);
  for (let i = 1; i <= LOOP * 1000; i++) {
    const t = i / 1000;
    const r = Math.round(scriptPos(t).p);
    if (r !== prev) {
      if (meridiem(r) !== meridiem(prev)) out.push({ t, dir: r > prev ? 1 : -1 });
      prev = r;
    }
  }
  return out;
}

export const CROSSINGS = buildCrossings();

/** The AM/PM card's angle in degrees: 0 = PM face, ±180 = AM face. */
export function scriptFlip(t: number) {
  'worklet';
  let a = 0;
  for (let i = 0; i < CROSSINGS.length; i++) {
    const c = CROSSINGS[i];
    if (t >= c.t) a += c.dir * 180 * spring(t - c.t, FLIP.omega, FLIP.zeta);
  }
  return a;
}

/** One blink, `tau` seconds after it starts: 0 open → 1 closed → 0. */
export function blinkAt(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  const { close, hold, open } = BLINK;
  if (tau < close) return smoothstep(tau / close);
  if (tau < close + hold) return 1;
  return 1 - easeOut(seg(tau, close + hold, close + hold + open));
}

/** Seeded blinks under a finger: one every BLINK.period ± jitter, from `tau` seconds. */
export function liveBlink(tau: number) {
  'worklet';
  const k = Math.floor(tau / BLINK.period);
  const start = k * BLINK.period + 1 + hash(k) * BLINK.jitter;
  return blinkAt(tau - start);
}

export function scriptPress(t: number) {
  'worklet';
  if (t < RELEASE_AT) return easeOut(seg(t, PRESS_AT, PRESS_AT + PRESS.in));
  return 1 - easeOut(seg(t, RELEASE_AT, RELEASE_AT + PRESS.out));
}

export function scriptAttention(t: number) {
  'worklet';
  return easeOut(seg(t, ATTENTION_IN[0], ATTENTION_IN[1])) * (1 - easeInOut(seg(t, ATTENTION_OUT[0], ATTENTION_OUT[1])));
}

// ─── frames ─────────────────────────────────────────────────────────────────

/** Everything that moves, as numbers. */
export type Frame = {
  pos: number; // ruler, ticks (0 = 1:05 PM)
  vel: number; // the velocity spring's output, ticks/s
  attention: number; // 0..1 the owl looks down at the ruler
  blink: number; // 0 open .. 1 closed
  startle: number; // s since the button's release; < 0 = none
  press: number; // 0..1 the button is held
  flip: number; // AM/PM card angle, deg
};

export const REST_FRAME: Frame = { pos: 0, vel: 0, attention: 0, blink: 0, startle: -1, press: 0, flip: 0 };

export function frameAt(t: number): Frame {
  'worklet';
  const s = mod(t, LOOP);
  const startle = s >= RELEASE_AT && s < RELEASE_AT + STARTLE.end ? s - RELEASE_AT : -1;
  let blink = 0;
  for (let i = 0; i < BLINKS_AT.length; i++) blink = Math.max(blink, blinkAt(s - BLINKS_AT[i]));
  return {
    pos: scriptPos(s).p,
    vel: scriptVel(s),
    attention: scriptAttention(s),
    blink,
    startle,
    press: scriptPress(s),
    flip: scriptFlip(s),
  };
}

// ─── poses derived from a frame ─────────────────────────────────────────────

/** The startle's pose, `tau` seconds after the button's release. Distances in pt. */
export function startlePose(tau: number) {
  'worklet';
  const S = STARTLE;
  if (tau < 0 || tau >= S.end) return { y: 0, sx: 1, sy: 1, rot: 0, pupil: 1, blink: 0, lift: 0, arrow: 0 };
  let y = 0;
  let sx = 1;
  let sy = 1;
  let rot = 0;
  if (tau < S.crouch) {
    const c = easeOut(tau / S.crouch);
    sy = 1 - S.crouchSy * c;
    sx = 1 + S.crouchSx * c;
  } else if (tau < S.crouch + S.air) {
    const u = (tau - S.crouch) / S.air;
    y = -S.height * 4 * u * (1 - u);
    // Release from the crouch into a stretch, normal at the apex, a little stretch falling.
    const out = easeOut(seg(u, 0, 0.18));
    const rise = u < 0.5 ? S.stretch * (1 - 2 * u) : 0.4 * S.stretch * (2 * u - 1);
    sy = lerp(1 - S.crouchSy, 1 + rise, out);
    sx = lerp(1 + S.crouchSx, 1 - rise * 0.6, out);
    rot = S.rattleDeg * Math.sin(2 * Math.PI * S.rattleHz * (tau - S.crouch)) * Math.sin(Math.PI * u);
  } else {
    const l = tau - S.crouch - S.air;
    const sq = S.landSquash * Math.exp(-S.landDecay * l) * Math.cos(S.landFreq * l);
    sy = 1 - sq;
    sx = 1 + sq * 0.7;
  }
  const shock = easeOut(seg(tau, 0.04, 0.16)) * (1 - easeInOut(seg(tau, 0.6, 0.95)));
  const arrow = S.arrow * (easeOut(seg(tau, 0, 0.14)) - easeInOut(seg(tau, 0.14, 0.5)));
  return {
    y,
    sx,
    sy,
    rot,
    pupil: 1 - S.pupil * shock,
    blink: blinkAt(tau - S.blinkAt),
    lift: -y / S.height,
    arrow,
  };
}

/** Everything the views need, from a frame. `still` = Reduce Motion: no lean, eyes, bend or jump. */
export function poseOf(f: Frame, still: boolean) {
  'worklet';
  const k = still ? 0 : 1;
  const st = startlePose(still ? -1 : f.startle);
  return {
    lean: -LEAN.max * Math.tanh(f.vel / LEAN.soft) * k + st.rot,
    jumpY: st.y,
    sx: st.sx,
    sy: st.sy,
    pupilDX: -EYES.x * Math.tanh(f.vel / EYES.soft) * k,
    pupilDY: EYES.attention * f.attention * k,
    pupilScale: st.pupil,
    blink: still ? 0 : Math.max(f.blink, st.blink),
    needle: -NEEDLE.max * Math.tanh(f.vel / NEEDLE.soft) * k,
    lift: st.lift,
    arrow: st.arrow,
    sundial: still ? 0 : SUNDIAL.amp * Math.sin((2 * Math.PI * f.pos) / TICKS_PER_DAY),
  };
}

// ─── the clock: digit wheels ────────────────────────────────────────────────

/**
 * The digit wheels at a ruler position. Each wheel holds still for most of a tick and rolls
 * within ±RISER/2 of the half tick, carrying like an odometer (minutes 55 → 00 turn the hour).
 * Values are continuous; wheel `ones` cycles 0/5, `tens` 0..5, `hour` 0..11 (0 shows 12).
 */
export function wheels(pos: number) {
  'worklet';
  const k = Math.floor(pos);
  const fr = pos - k;
  const g = k + smoothstep((fr - 0.5) / RISER + 0.5);
  const m = mod(REST_MINUTES / STEP_MINUTES + g, TICKS_PER_DAY);
  const mi = Math.floor(m);
  const mf = m - mi;
  const ones = m;
  const tens = Math.floor(mi / 2) + (mi % 2 === 1 ? mf : 0);
  const hour = Math.floor(mi / 12) + (mi % 12 === 11 ? mf : 0);
  return { ones: mod(ones, 2), tens: mod(tens, 6), hour: mod(hour, 12) };
}

/** The label at the nearest tick, e.g. "1:05 PM". */
export function timeLabel(pos: number) {
  'worklet';
  const m = mod(REST_MINUTES / STEP_MINUTES + Math.round(pos), TICKS_PER_DAY) * STEP_MINUTES;
  const h24 = Math.floor(m / 60);
  const mm = m % 60;
  const h = h24 % 12 === 0 ? 12 : h24 % 12;
  return `${h}:${mm < 10 ? '0' : ''}${mm} ${h24 < 12 ? 'AM' : 'PM'}`;
}

// ─── ruler ticks ────────────────────────────────────────────────────────────

/** Tick height (pt) by distance from the needle, measured on the pin (px ÷ 3); monotone cubic between. */
const TICK_D = [0, 1, 2, 3, 4, 5];
const TICK_H = [59, 50, 36, 24, 16, 15].map((px) => px / 3);

function buildSlopes(): number[] {
  // Fritsch–Carlson tangents: no overshoot between the measured heights.
  const n = TICK_H.length;
  const d: number[] = [];
  for (let i = 0; i < n - 1; i++) d.push(TICK_H[i + 1] - TICK_H[i]);
  const m: number[] = [d[0]];
  for (let i = 1; i < n - 1; i++) m.push(d[i - 1] * d[i] <= 0 ? 0 : (2 * d[i - 1] * d[i]) / (d[i - 1] + d[i]));
  m.push(0);
  return m;
}

export const TICK_SLOPE = buildSlopes();

export function tickHeight(dist: number) {
  'worklet';
  const d = Math.abs(dist);
  if (d >= TICK_D[TICK_D.length - 1]) return TICK_H[TICK_H.length - 1];
  const i = Math.floor(d);
  const u = d - i;
  return hermite(TICK_H[i], TICK_H[i + 1], TICK_SLOPE[i], TICK_SLOPE[i + 1], u);
}

/** Tick ink: dark up to 2 ticks from the needle, linear to light grey at 9. Returns 0..1 (0 = dark). */
export function tickShade(dist: number) {
  'worklet';
  return clamp01((Math.abs(dist) - 2) / 7);
}

// ─── the live engine (under a finger) ───────────────────────────────────────

export const AUTO = 0;
export const LIVE = 1;
export const HOME = 2; // easing back to rest before the loop restarts

export type Engine = {
  mode: number;
  autoStart: number; // clock time of loop t = 0
  autoPlay: boolean;
  still: boolean; // Reduce Motion
  pos: number;
  dragging: boolean;
  dragFrom: number;
  dragVel: number; // ticks/s, from the gesture
  glideAt: number; // < 0: no glide
  glideFrom: number;
  glideTo: number;
  glideV: number;
  glideDur: number;
  fx: number; // velocity spring
  fv: number;
  attention: number;
  lastTouch: number;
  liveSince: number;
  handoffT: number; // loop time when the finger took over (to finish a blink in progress)
  startleAt: number;
  pressInAt: number;
  releaseAt: number;
  pressFrom: number;
  flipFrom: number;
  flipTo: number;
  flipAt: number;
  lastClock: number;
};

export function createEngine(now: number, autoPlay: boolean, still: boolean): Engine {
  'worklet';
  return {
    mode: autoPlay && !still ? AUTO : LIVE,
    autoStart: now,
    autoPlay,
    still,
    pos: 0,
    dragging: false,
    dragFrom: 0,
    dragVel: 0,
    glideAt: -1,
    glideFrom: 0,
    glideTo: 0,
    glideV: 0,
    glideDur: 1,
    fx: 0,
    fv: 0,
    attention: 0,
    lastTouch: -1e9,
    liveSince: now,
    handoffT: -1,
    startleAt: -1e9,
    pressInAt: -1e9,
    releaseAt: -1e9,
    pressFrom: 0,
    flipFrom: 0,
    flipTo: 0,
    flipAt: -1e9,
    lastClock: now,
  };
}

function flipNow(e: Engine, now: number) {
  'worklet';
  return lerp(e.flipFrom, e.flipTo, spring(now - e.flipAt, FLIP.omega, FLIP.zeta));
}

function pressNow(e: Engine, now: number) {
  'worklet';
  if (e.releaseAt < e.pressInAt) return easeOut(seg(now - e.pressInAt, 0, PRESS.in));
  return e.pressFrom * (1 - easeOut(seg(now - e.releaseAt, 0, PRESS.out)));
}

/** Position and velocity of the ruler from a running glide. */
function glideNow(e: Engine, now: number) {
  'worklet';
  const u = clamp01((now - e.glideAt) / e.glideDur);
  const m0 = e.glideV * e.glideDur;
  return {
    p: hermite(e.glideFrom, e.glideTo, m0, 0, u),
    v: u >= 1 ? 0 : hermiteSlope(e.glideFrom, e.glideTo, m0, 0, u) / e.glideDur,
    done: u >= 1,
  };
}

/** The resting angle nearest `angle` that shows the given face (0 = PM: multiples of 360; 1 = AM: odd multiples of 180). */
function faceAngle(angle: number, face: number) {
  'worklet';
  const k = Math.round((angle - face * 180) / 360);
  return k * 360 + face * 180;
}

/** The finger takes over: freeze whatever autoPlay shows at this instant into live state. */
export function takeOver(e: Engine, now: number): Engine {
  'worklet';
  if (e.mode !== AUTO) return { ...e, mode: LIVE, lastTouch: now };
  const t = mod(now - e.autoStart, LOOP);
  const f = frameAt(t);
  const prev = scriptVel(t - 1 / 240);
  const inStartle = f.startle >= 0;
  const flip = mod(f.flip + 180, 360) - 180;
  // A scripted drag or glide in progress carries on as a real glide to its tick.
  const s = scriptPos(t);
  const moving = Math.abs(s.v) > 0.01 || Math.abs(s.p - Math.round(s.p)) > 1e-3;
  const plan = glidePlan(s.p, s.v);
  return {
    ...e,
    mode: LIVE,
    pos: f.pos,
    glideAt: moving ? now : -1,
    glideFrom: s.p,
    glideTo: plan.target,
    glideV: s.v,
    glideDur: plan.dur,
    fx: f.vel,
    fv: (f.vel - prev) * 240,
    attention: f.attention,
    lastTouch: now,
    liveSince: now,
    handoffT: t,
    startleAt: inStartle ? now - f.startle : -1e9,
    pressInAt: -1e9,
    releaseAt: now,
    pressFrom: f.press,
    flipFrom: flip,
    flipTo: faceAngle(flip, meridiem(f.pos)),
    flipAt: now,
  };
}

/** Touch down on the ruler: catch a glide where it is. */
export function rulerDown(e0: Engine, now: number): Engine {
  'worklet';
  const e = takeOver(e0, now);
  let pos = e.pos;
  if (e.glideAt >= 0) pos = glideNow(e, now).p;
  return { ...e, mode: LIVE, pos, glideAt: -1, dragging: false, dragFrom: pos, dragVel: 0, lastTouch: now };
}

export function dragStart(e: Engine, now: number): Engine {
  'worklet';
  return { ...e, dragging: true, dragFrom: e.pos, dragVel: 0, lastTouch: now };
}

/** `dx` and `vx`: finger translation (pt) and velocity (pt/s); `spacing`: pt per tick. Left = later. */
export function dragMove(e: Engine, now: number, dx: number, vx: number, spacing: number): Engine {
  'worklet';
  if (!e.dragging) return e;
  return { ...e, pos: e.dragFrom - dx / spacing, dragVel: -vx / spacing, lastTouch: now };
}

/** Let go (or a touch that never dragged): glide to the projected tick. */
export function release(e: Engine, now: number, vx: number, spacing: number): Engine {
  'worklet';
  const v = e.dragging ? -vx / spacing : 0;
  const plan = glidePlan(e.pos, e.still ? 0 : v);
  return {
    ...e,
    dragging: false,
    dragVel: 0,
    glideAt: now,
    glideFrom: e.pos,
    glideTo: plan.target,
    glideV: e.still ? 0 : v,
    glideDur: plan.dur,
    lastTouch: now,
  };
}

/** Accessibility: one tick later (+1) or earlier (−1). */
export function stepBy(e0: Engine, now: number, dir: number): Engine {
  'worklet';
  const e = takeOver(e0, now);
  const from = e.glideAt >= 0 ? glideNow(e, now).p : e.pos;
  return { ...e, mode: LIVE, pos: from, glideAt: now, glideFrom: from, glideTo: Math.round(from) + dir, glideV: 0, glideDur: GLIDE.still, lastTouch: now };
}

export function pressIn(e0: Engine, now: number): Engine {
  'worklet';
  const e = takeOver(e0, now);
  return { ...e, pressInAt: now, lastTouch: now };
}

export function pressOut(e: Engine, now: number, commit: boolean): Engine {
  'worklet';
  const from = pressNow(e, now);
  return {
    ...e,
    releaseAt: now,
    pressFrom: from,
    startleAt: commit && !e.still ? now : e.startleAt,
    lastTouch: now,
  };
}

export function setAutoPlay(e: Engine, autoPlay: boolean, still: boolean, now: number): Engine {
  'worklet';
  // First call after mount, or Reduce Motion turned on: start from the rest frame.
  if (e.lastClock === 0 || (still && !e.still)) return createEngine(now, autoPlay, still);
  if (autoPlay && !still) {
    if (e.mode === AUTO && e.autoPlay && !e.still) return e;
    return { ...e, autoPlay, still, mode: LIVE, lastTouch: now - RESUME_AFTER };
  }
  if (e.mode === AUTO) return { ...takeOver({ ...e, autoPlay, still }, now), lastTouch: -1e9 };
  return { ...e, autoPlay, still };
}

/** Exact step of the velocity spring over dt with input u held constant. */
function filterStep(x: number, v: number, u: number, dt: number) {
  'worklet';
  const { omega, zeta } = FILTER;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const a = zeta * omega;
  const e0 = x - u;
  const env = Math.exp(-a * dt);
  const c = Math.cos(wd * dt);
  const s = Math.sin(wd * dt);
  const B = (v + a * e0) / wd;
  const e1 = env * (e0 * c + B * s);
  const v1 = env * (-a * (e0 * c + B * s) + (-e0 * wd * s + B * wd * c));
  return { x: u + e1, v: v1 };
}

/**
 * Advance the engine to `now` and return the frame to draw. `tick`: nearest tick (for haptics),
 * `haptic`: whether ticks crossing now come from a finger (not autoPlay or the way home).
 */
export function step(e0: Engine, now: number): { e: Engine; f: Frame; haptic: boolean } {
  'worklet';
  if (e0.mode === AUTO) {
    const f = frameAt(now - e0.autoStart);
    return { e: { ...e0, lastClock: now }, f, haptic: false };
  }
  let e = e0;
  const dt = Math.min(0.05, Math.max(0, now - e.lastClock));

  // Ruler position and the velocity that drives the spring.
  let pos = e.pos;
  let u = 0;
  let moving = e.dragging;
  if (e.dragging) {
    u = e.dragVel;
  } else if (e.glideAt >= 0) {
    const g = glideNow(e, now);
    pos = g.p;
    u = g.v;
    moving = !g.done;
    if (g.done) e = { ...e, glideAt: -1 };
  }

  // Idle long enough: ease home (the nearest copy of the rest tick), then hand back to autoPlay.
  const startleLeft = e.startleAt + STARTLE.end - now;
  if (e.mode === LIVE && e.autoPlay && !e.still && !e.dragging && !moving && now - e.lastTouch >= RESUME_AFTER && startleLeft <= 0) {
    const from = mod(pos + TICKS_PER_DAY / 2, TICKS_PER_DAY) - TICKS_PER_DAY / 2;
    const dur = Math.min(RETURN.max, RETURN.base + RETURN.perTick * Math.abs(from));
    e = { ...e, mode: HOME, glideAt: now, glideFrom: from, glideTo: 0, glideV: 0, glideDur: dur };
    pos = from;
    moving = true;
  }

  // AM/PM: a crossing starts a flip from wherever the card is.
  const before = meridiem(e.pos);
  const after = meridiem(pos);
  if (before !== after) {
    const dir = Math.round(pos) > Math.round(e.pos) ? 1 : -1;
    const cur = flipNow(e, now);
    e = { ...e, flipFrom: cur, flipTo: e.flipTo + dir * 180, flipAt: now };
  }

  const fs = filterStep(e.fx, e.fv, e.still ? 0 : u, dt);
  const engaged = e.dragging || moving;
  const target = engaged || now - e.lastTouch < 0.5 ? 1 : 0;
  const attention = lerp(target, e.attention, Math.exp(-dt / ATTENTION_TAU));

  // A blink in progress when the finger took over finishes; then seeded blinks.
  const since = now - e.liveSince;
  let blink = liveBlink(since);
  if (e.handoffT >= 0 && since < 0.3) {
    for (let i = 0; i < BLINKS_AT.length; i++) blink = Math.max(blink, blinkAt(e.handoffT + since - BLINKS_AT[i]));
  }

  e = { ...e, pos, fx: fs.x, fv: fs.v, attention, lastClock: now };

  const startle = now - e.startleAt;
  const f: Frame = {
    pos,
    vel: fs.x,
    attention,
    blink,
    startle: startle >= 0 && startle < STARTLE.end ? startle : -1,
    press: pressNow(e, now),
    flip: flipNow(e, now),
  };

  // Home and settled: the loop restarts at its rest frame.
  if (
    e.mode === HOME &&
    e.glideAt < 0 &&
    Math.abs(e.fx) < 0.05 &&
    Math.abs(e.fv) < 0.5 &&
    attention < 0.01 &&
    Math.abs(mod(f.flip + 180, 360) - 180) < 0.5 &&
    f.press < 0.001
  ) {
    return { e: { ...e, mode: AUTO, autoStart: now, pos: 0, fx: 0, fv: 0, attention: 0 }, f: REST_FRAME, haptic: false };
  }
  return { e, f, haptic: e.mode === LIVE && engaged };
}
