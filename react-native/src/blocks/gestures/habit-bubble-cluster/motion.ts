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

export const SCATTER = {
  // Detail view: the other bubbles fly this far outward while they shrink away.
  distance: 220,
} as const;

export const DETAIL = {
  // Where the tapped bubble sits in the detail view (share of the height) and its radius.
  y: 0.333,
  radius: 70,
  fadeMs: 240,
  fadeOutMs: 160,
  ease: [0.23, 1, 0.32, 1] as [number, number, number, number],
} as const;

export const SWITCH = {
  fadeMs: 260,
  stagger: 22,
  reducedMs: 200,
} as const;

// One autoplay period; it ends where it started, so the preview loops without a jump.
export const LOOP_MS = 12000;

// QA only (pixel gate): freeze the autoplay in one state. null in shipped code.
//   'rest' — Social cluster; 'a' — Personal; 'b' — detail view.
export const QA_HOLD: 'rest' | 'a' | 'b' | null = null;
export const QA_HOLD_AT = { rest: 0, a: 8300, b: 4600 } as const;

export const AUTOPLAY = {
  resumeDelayMs: 3500,
  // The upgrade's sequence on top of the source's: hold the star until its glass fills (burst, ripple), tap a
  // bubble into its detail and back, switch to Personal and back, then tap the star to reopen it, so the loop
  // ends where it started.
  holdId: 'star',
  holdStart: 600,
  holdEnd: 1900,
  openId: 'home',
  openAt: 3200,
  backAt: 5400,
  personalAt: 6800,
  socialAt: 8800,
  tapAt: 10400,
  tapMs: 120,
} as const;
