/**
 * Health Age Dial — timing. Every value of the autoPlay loop is a pure function of the loop
 * time t (seconds, 0 ≤ t < LOOP); t = LOOP equals t = 0 (the pin's resting screen).
 *
 *   0.00–1.60  rest = the pin (dial at −3.9 years)
 *   1.60–2.15  the dot climbs back to the tick (actual age 30); the number rolls to 0.0
 *   2.35–3.50  rewind: the dot springs back to −3.9 with a comet trail; the number rolls with it
 *   3.90–5.30  the page scrolls to the history; the dial shrinks and flies into the Health Age chip
 *              (scroll-linked, so a finger does the same); the chip's ring pops as it lands
 *   5.00–6.20  the history line draws into its dot; the factor cards count up, badges pop (40 ms)
 *   6.20–7.20  hold = the pin's second screen (the counts finish at 6.1)
 *   7.20–8.60  scroll back; the dial flies out of the chip to its place
 *   8.60–10.0  rest (with 0–1.6: 3.0 s, 30 % of the loop)
 *
 * Springs are closed-form (natural frequency ω in rad/s, damping ratio ζ) so the Swift and
 * Compose ports can match frame for frame.
 */

export const LOOP = 10;

export const T = {
  retractStart: 1.6,
  retractEnd: 2.15,
  rewindStart: 2.35,
  scrollDownStart: 3.9,
  scrollDownEnd: 5.3,
  revealStart: 5.0,
  scrollUpStart: 7.2,
  scrollUpEnd: 8.6,
  revealFadeStart: 8.1,
  revealFadeEnd: 8.5,
} as const;

/** After the last touch, wait this long, then glide back to rest and restart the loop. */
export const RESUME_AFTER = 2;
/** The glide back to the top after a touch session. */
export const RETURN_DURATION = 0.8;

export const SPRINGS = {
  /** The dot's rewind and its release after a drag: settles in ~1 s; 0.6 % overshoot keeps "-3.9" on its digit. */
  dot: { omega: 11, zeta: 0.85 },
  /** Badge pop on the factor cards. */
  pop: { omega: 16, zeta: 0.55 },
} as const;

/** Comet trail: degrees of arc per degree-per-second of the dot, and its cap. */
export const TRAIL = { seconds: 0.14, max: 64, fade: 0.47 } as const;

/** History reveal, in seconds after it starts. */
export const REVEAL = {
  line: 0.9,
  guidesAt: 0.65,
  guides: 0.3,
  countAt: [0.25, 0.31],
  count: 0.8,
  badgeAt: [0.25, 0.29],
  fade: 0.25,
} as const;

/** The chip's ring when the dial lands in it: scale bump amplitude. */
export const LAND = { amp: 0.1, decay: 6, freq: 14, peak: 0.5577 } as const;

/** Flight of the dial into the chip, as a fraction of the landing scroll offset. */
export const FLIGHT = { numberFadeEnd: 0.32, crossfadeStart: 0.86 } as const;

/** Odometer: a digit holds still for most of its step and rolls over this fraction of it. */
export const ROLL = { window: 0.36, travel: 0.55, falloff: 1.9 } as const;

/** The sign shows only when the number does not read 0.0: fades in between 0.03 and 0.06 years. */
export const SIGN = { from: 0.03, to: 0.06 } as const;

/** Press feedback: quick in, slower release. */
export const PRESS = { scale: 0.97, pressIn: 100, release: 200 } as const;

export function clamp01(v: number): number {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function seg(t: number, a: number, b: number): number {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function lerp(a: number, b: number, p: number): number {
  'worklet';
  return a + (b - a) * p;
}

export function smoothstep(p: number): number {
  'worklet';
  const x = clamp01(p);
  return x * x * (3 - 2 * x);
}

/** Column position with a crisp step: integer + eased fraction. */
export function rollStep(x: number): number {
  'worklet';
  const n = Math.floor(x);
  const f = x - n;
  return n + smoothstep((f - 0.5 + ROLL.window / 2) / ROLL.window);
}

/** Solve a cubic-bezier easing for x, then sample y. */
export function bezier(p: number, x1: number, y1: number, x2: number, y2: number): number {
  'worklet';
  const x = clamp01(p);
  if (x === 0 || x === 1) return x;
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 20; i++) {
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
export function easeOut(p: number): number {
  'worklet';
  return bezier(p, 0.23, 1, 0.32, 1);
}

/** On-screen movement: cubic-bezier(0.77, 0, 0.175, 1). */
export function easeInOut(p: number): number {
  'worklet';
  return bezier(p, 0.77, 0, 0.175, 1);
}

/**
 * Displacement of a damped spring released at `d0` with velocity `v0` (units/s),
 * `tau` seconds later. Under-damped closed form; reaches 0 as tau grows.
 */
export function springDisplacement(
  tau: number,
  d0: number,
  v0: number,
  s: { omega: number; zeta: number },
): number {
  'worklet';
  if (tau <= 0) return d0;
  const { omega, zeta } = s;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const env = Math.exp(-zeta * omega * tau);
  if (env < 1e-5) return 0;
  return env * (d0 * Math.cos(wd * tau) + ((v0 + zeta * omega * d0) / wd) * Math.sin(wd * tau));
}

/** A spring from 0 to 1 that starts at rest. */
export function spring(tau: number, s: { omega: number; zeta: number }): number {
  'worklet';
  if (tau <= 0) return 0;
  return 1 - springDisplacement(tau, 1, 0, s);
}

/** Badge pop: scale 0.85 → 1 on a playful spring, opacity in 150 ms. */
export function pop(tau: number): { scale: number; opacity: number } {
  'worklet';
  if (tau <= 0) return { scale: 0.85, opacity: 0 };
  return { scale: 0.85 + 0.15 * spring(tau, SPRINGS.pop), opacity: easeOut(tau / 0.15) };
}

/** The chip ring's bump when the dial lands: 0 → 1 → 0 in about half a second. */
export function landBump(tau: number): number {
  'worklet';
  if (tau <= 0 || tau > 1.2) return 0;
  return (Math.exp(-LAND.decay * tau) * Math.sin(LAND.freq * tau)) / LAND.peak;
}

/** Scroll offset of the loop (pt), towards `historyOffset`. */
export function scrollAt(t: number, historyOffset: number): number {
  'worklet';
  if (t < T.scrollDownStart) return 0;
  if (t < T.scrollDownEnd) return historyOffset * easeInOut(seg(t, T.scrollDownStart, T.scrollDownEnd));
  if (t < T.scrollUpStart) return historyOffset;
  if (t < T.scrollUpEnd) return historyOffset * (1 - easeInOut(seg(t, T.scrollUpStart, T.scrollUpEnd)));
  return 0;
}

/** Years shown by the dial at loop time t (0 = the actual age, the tick). */
export function yearsAt(t: number, rest: number): number {
  'worklet';
  if (t < T.retractStart) return rest;
  if (t < T.rewindStart) return rest * (1 - easeInOut(seg(t, T.retractStart, T.retractEnd)));
  return rest * spring(t - T.rewindStart, SPRINGS.dot);
}

/** Comet trail length in degrees (signed: positive trails clockwise of the dot). */
export function trailFor(velocityDegPerSecond: number): number {
  'worklet';
  const len = Math.min(TRAIL.max, Math.abs(velocityDegPerSecond) * TRAIL.seconds);
  return velocityDegPerSecond > 0 ? -len : len;
}

/** Loop time at which the scroll crosses `offset` on the way down (the dial lands in the chip). */
export function landingTime(offset: number, historyOffset: number): number {
  let lo: number = T.scrollDownStart;
  let hi: number = T.scrollDownEnd;
  for (let i = 0; i < 40; i++) {
    const m = (lo + hi) / 2;
    if (scrollAt(m, historyOffset) < offset) lo = m;
    else hi = m;
  }
  return (lo + hi) / 2;
}

export type LoopFrame = {
  scroll: number;
  years: number;
  /** Degrees per second of the dot along the arc. */
  velocity: number;
  /** Seconds since the history reveal started, or −1 before it. */
  reveal: number;
  /** Opacity of the revealed history (fades as it leaves at the end of the loop). */
  revealOpacity: number;
};

export function loopFrame(t: number, rest: number, degPerYear: number, historyOffset: number): LoopFrame {
  'worklet';
  const dt = 1 / 240;
  const years = yearsAt(t, rest);
  const velocity = ((years - yearsAt(Math.max(0, t - dt), rest)) / dt) * degPerYear;
  const reveal = t >= T.revealStart && t < T.scrollUpEnd ? t - T.revealStart : -1;
  return {
    scroll: scrollAt(t, historyOffset),
    years,
    velocity,
    reveal,
    // Fades while the history scrolls away; reset once it is gone, so t = LOOP equals t = 0.
    revealOpacity: t < T.scrollUpEnd ? 1 - seg(t, T.revealFadeStart, T.revealFadeEnd) : 1,
  };
}
