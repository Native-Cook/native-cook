/** Every motion value of the block. Springs move mass (the page); timings move light and opacity. */

export const PINCH = {
  // Finger distance ratio at which the page has fully become the card.
  fullIn: 0.5,
  // Finger distance ratio at which the card has fully become the page again.
  fullOut: 1.8,
  // Release thresholds on progress p (0 = page, 1 = card) and on pinch velocity (scale per second).
  commitAt: 0.32,
  restoreAt: 0.7,
  // Momentum projection's deceleration rate (Apple's scroll view value).
  deceleration: 0.998,
  // Past either end the page resists (Apple's rubber-band constant).
  rubber: 0.55,
  // How much of the focal point's travel the page follows while pinching.
  follow: 0.6,
  // Converts pinch velocity (scale/s) into progress velocity for the release spring.
  velocityToProgress: 1.6,
} as const;

export const SPRING = {
  collapse: { damping: 22, stiffness: 210, mass: 1 },
  restore: { damping: 30, stiffness: 230, mass: 1 },
  lift: { damping: 24, stiffness: 180, mass: 1 },
  drift: { damping: 20, stiffness: 240, mass: 1 },
  pop: { damping: 12, stiffness: 260, mass: 0.7 },
} as const;

export const TIMING = {
  // Stand-in for the model call: how long the card "thinks" before the summary arrives.
  thinkMs: 1400,
  thinkIn: 260,
  rimOut: 420,
  // The rim's flash along the growing edge on restore, and its fade when a pinch is let go.
  rimFlashIn: 120,
  rimFlashOut: 520,
  rimLetGo: 300,
  // One frame: the think timer's last tick.
  tick: 16,
  // Summary lines stream in one after another.
  lineStagger: 70,
  lineMs: 420,
  lineRise: 8,
  summaryOutStart: 0.95,
  toolbarFadeSpan: 0.3,
  rimTurnMs: 1600,
  // The thinking card breathes ±1.2 % over 1.6 s.
  breathe: 0.012,
  breathePeriod: 1600,
  // Arrival payoff: a ring of light leaves the card; the credit pill catches a sweep.
  bloomMs: 900,
  bloomReach: 70,
  sweepMs: 700,
  sweepDelay: 200,
  // Ghosted lines: two copies offset ±6 pt converge as the line lands.
  ghostOffset: 6,
  ghostAlpha: 0.35,
  // Icon discs pop from 0.8 (never from 0).
  discFrom: 0.8,
  // Motes rising in the field.
  motes: 22,
  moteSpeedMin: 18,
  moteSpeedMax: 40,
  // Press feedback.
  pressScale: 0.97,
  scanMs: 1100,
  shimmerMs: 1300,
  // Drift speed of the gradient field's blobs (shader time units per second).
  fieldBreath: 0.015,
  fieldPeriod: 11000,
  fingerFade: 140,
  // Longest frame step the clocks accept, so a stalled frame cannot jump the script.
  maxFrameMs: 250,
  // Reduce Motion: cross-fades only.
  reducedFade: 180,
} as const;

export const RIM = {
  // Rim opacity while pinching (scales with p) and while thinking.
  pinchMax: 0.55,
  armedBoost: 0.3,
  // Replica step: the source has no rim or scan while thinking (the upgrade turns these on).
  think: 1,
  scan: 0.9,
} as const;

// One autoplay period; the last frame equals the first, so the preview loops without a jump.
export const LOOP_MS = 11000;

// QA only (pixel gate): freeze the autoplay in one state. null in shipped code.
//   'rest' — the page; 'a' — the card thinking; 'b' — the summary settled.
export const QA_HOLD: 'rest' | 'a' | 'b' | null = null;

export const AUTOPLAY = {
  resumeDelayMs: 3500,
  // QA hold point on the loop clock (after the pinch in, before the pinch out).
  qaHoldAt: 6500,
  // The scripted fingers: when they press, travel and lift (ms on the loop clock).
  pinchInStart: 1000,
  pinchInEnd: 1900,
  pinchInScale: 0.52,
  pinchOutStart: 8200,
  pinchOutEnd: 9050,
  pinchOutScale: 1.85,
  // Where the script resumes after a touch, by state.
  resumeReading: 0,
  resumeSummary: 7000,
  // Finger spread at the start of the pinch in, and at the start of the pinch out (points).
  spreadIn: 150,
  spreadOut: 70,
} as const;
