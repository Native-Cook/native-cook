/** Every motion value of the block. */

export const PHYSICS = {
  // Fixed step of the simulation (s); frames are split into these so 60 and 120 Hz behave the same.
  step: 1 / 120,
  // Home spring per unit mass, and its damping.
  stiffness: 120,
  damping: 14,
  // Radius spring (a bubble growing or shrinking).
  radiusStiffness: 260,
  radiusDamping: 18,
  // How much of an overlap is resolved per step (position-based separation).
  separation: 0.5,
  // Packing relaxation for homes.
  relaxIterations: 240,
  relaxGravity: 0.04,
  // Fling: velocity handed over on release, capped.
  maxFling: 2400,
  maxFrameMs: 250,
} as const;

export const HOLD = {
  // Press time before the ring starts (lets drags win).
  delayMs: 120,
  chargeMs: 900,
  drainMs: 220,
  moveTolerance: 10,
  squeeze: 0.94,
  // Selection haptics at these shares of the charge.
  ticks: [1 / 3, 2 / 3],
  doneScale: 0.62,
  tapMs: 220,
} as const;

export const BURST = {
  flashMs: 240,
  sparkMs: 620,
  sparkFrom: 6,
  sparkNear: 28,
  sparkFar: 44,
} as const;

export const IDLE = {
  // Breathing: ±share of the radius; each bubble breathes 2, 3 or 4 times per autoplay loop.
  breathe: 0.015,
  cyclesMin: 2,
} as const;

export const HINT = {
  inMs: 180,
  holdMs: 1400,
  outMs: 260,
} as const;

export const LIQUID = {
  // The meniscus: two sines (ms per radian) and amplitudes (pt), damped to flat when empty or full.
  period1: 140,
  period2: 230,
  amp1: 3,
  amp2: 1.6,
  // Alpha of the liquid at its surface and at the bottom.
  top: 0.75,
  bottom: 0.5,
} as const;

export const WAVE = {
  // Completion shock wave: outward speed (pt/s) at the bubble, falling off with distance (pt).
  speed: 520,
  reach: 120,
} as const;

export const STAGGER = {
  // Set switch: each bubble's turn comes 30 ms later per 60 pt from the centre (Emil: 30–80 ms stagger).
  stepMs: 30,
  per: 60,
} as const;

/** The name a tap shows above a bubble: in, held, out (ms). */
export const LABEL = { inMs: 180, holdMs: 1600, outMs: 220, rise: 6 } as const;

/** Completion: a ring of light leaves the bubble and fades (calm, no sparks). */
export const RIPPLE = { ms: 700, reach: 26 } as const;

export const SWITCH = {
  fadeMs: 260,
  stagger: 22,
  reducedMs: 200,
} as const;

// One autoplay period; it ends where it started, so the preview loops without a jump.
export const LOOP_MS = 10000;

// QA only (pixel gate): freeze the autoplay at a time (ms). null in shipped code.
export const QA_HOLD: number | null = null;

export const AUTOPLAY = {
  resumeDelayMs: 2000,
  // Hold the star until its glass fills (the cluster ripples and re-packs), tap a bubble to read its name, switch to
  // Personal and back, then tap the star to reopen it, so the loop ends where it started.
  holdId: 'star',
  holdStart: 400,
  holdEnd: 1700,
  nameId: 'home',
  nameAt: 2700,
  personalAt: 4200,
  socialAt: 6400,
  tapAt: 8000,
  tapMs: 120,
} as const;
