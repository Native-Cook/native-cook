/** Every motion value of the block. */

const DEG = Math.PI / 180;

export const TILT = {
  // Finger offset of one half-card gives this tilt; beyond it the card resists.
  perHalfCard: 14 * DEG,
  max: 18 * DEG,
  rubber: 0.35,
  // Device rotation (radians from the pose the screen opened in) → tilt, capped, low-passed per frame.
  sensorGain: 0.9,
  sensorMax: 14 * DEG,
  sensorSmoothing: 0.12,
  // Idle drift so a still phone still shows depth.
  driftAmplitude: 1.2 * DEG,
  driftPeriodMs: 12000,
  perspective: 420,
  // The source's card grows while it is being turned.
  lift: 1.34,
  reducedMax: 4 * DEG,
} as const;

export const PARALLAX = {
  // Points per radian of tilt for each layer (negative = against the tilt).
  // At the 18° limit: plate −12 pt, figure +8 pt, wordmark +13 pt, so the three layers part clearly.
  plate: -38,
  figure: 26,
  title: 42,
  backdrop: -30,
  // Glare travel (share of the card per radian) and foil band travel.
  glare: -2.2,
  foil: 2.6,
} as const;

export const FLIP = {
  // A tap: shorter than this, moving less than `slop` pt and slower than `tapSpeed` pt/s.
  tapMs: 260,
  slop: 8,
  tapSpeed: 400,
  // Extra scale at the edge-on moment of a flip.
  rise: 0.05,
} as const;

// The idle float of the card (pt), on a period that divides the loop.
// Holographic sheen on the wordmark: travel in title-widths per radian, band period in title-widths.
export const HOLO = { travelX: 2.4, travelY: 1.6, flipTravel: 0.5, period: 0.7, rest: 0.18, max: 0.75, gain: 2.2 } as const;

// The block's thickness (pt, before the lift) and the angles over which a side face fades in.
export const EDGE = { depth: 4.5, fromDeg: 6 * (Math.PI / 180), toDeg: 10 * (Math.PI / 180) } as const;

export const FLOAT = { amplitude: 2, periodMs: 6000 } as const;

export const SPRING = {
  // The flip: a heavy card, one small overshoot (dampingRatio ≈ 0.75).
  flip: { damping: 18, stiffness: 140, mass: 1.2 },
  release: { damping: 9, stiffness: 120, mass: 1 },
  lift: { damping: 16, stiffness: 260, mass: 1 },
  // Watchlist morph and press: quick, one small overshoot.
  morph: { damping: 14, stiffness: 220, mass: 1 },
  press: { damping: 20, stiffness: 420, mass: 1 },
} as const;

export const TIMING = {
  reducedMs: 200,
  maxFrameMs: 250,
} as const;

// One autoplay period; it ends at rest, so the preview loops without a jump.
export const LOOP_MS = 12000;

// QA only (pixel gate): freeze the autoplay at a time on the loop clock. null in shipped code.
//   'rest' — card at rest; 'a', 'b' — the scripted finger turning it.
export const QA_HOLD: 'rest' | 'a' | 'b' | null = null;
export const QA_HOLD_AT = { rest: 0, a: 2200, b: 3800 } as const;

export const AUTOPLAY = {
  resumeDelayMs: 3500,
  // A scripted finger draws a slow figure-eight on the card, then lets go.
  holdStart: 800,
  holdEnd: 5200,
  // Finger travel as a share of the half-card on each axis.
  ampX: 1.1,
  ampY: 0.75,
  // Ramp in/out so the path starts and ends at the centre (ms).
  ramp: 600,
  // Then taps, on the same clock: turn the card over and back, add it to the watchlist and take it off.
  taps: [
    { at: 6000, on: 'card' },
    { at: 8200, on: 'card' },
    { at: 9600, on: 'watch' },
    { at: 10400, on: 'watch' },
  ] as const,
  pressMs: 110,
  fingerLeadMs: 280,
} as const;
