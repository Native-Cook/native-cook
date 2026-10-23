/** Every motion value of the block. Springs move mass (the page); timings move light, opacity and the chips. */

// Curves (animate-expo): strong ease-out for things arriving, the iOS sheet curve for travel, ease-in-out for
// on-screen scrolls. Kept as numbers so the worklet solver in geometry.ts and Reanimated's Easing share them.
export const CURVE = {
  out: [0.23, 1, 0.32, 1],
  sheet: [0.32, 0.72, 0, 1],
  inOut: [0.77, 0, 0.175, 1],
} as const;

export const PINCH = {
  // Finger distance ratio at which the page has fully become the card.
  fullIn: 0.5,
  // Finger distance ratio at which the card has fully become the page again.
  fullOut: 1.8,
  // Release thresholds on progress p (0 = page, 1 = card), decided with momentum projection.
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
  // The page crops late (p^2.2), so the whole article stays readable while the fingers pick sentences.
  cropPow: 2.2,
} as const;

// After a committed pinch the page waits here, still readable, while the summary is made.
export const HOLD = { min: 0.42, max: 0.6 } as const;

/** The highlighter. Progress g = p / span; sentence i sweeps from g = 0.2·i and locks at 0.2·(i + 1). */
export const MARK = {
  span: 0.45,
  step: 0.2,
  count: 4,
  // Non-key text washes out under a white veil as the pinch deepens.
  dimMax: 0.62,
  // On commit the sentences not yet locked finish their sweep this fast each.
  autoLockMs: 150,
  // A sentence's band while it is still sweeping, once locked, and the pop at the lock.
  toneSweep: 0.5,
  toneLocked: 0.82,
  popMs: 180,
  popScale: 0.1,
  // The tapped point's sentence flashes: its band rises this fast (ease-out, no bounce: a tap carries no momentum)
  // and grows this much, then fades over fadeMs.
  flashMs: 200,
  flashScale: 0.2,
  // While the model works the locked sentences pulse in turn, after a short grace so a fast answer shows nothing.
  pulseDelay: 300,
  pulseMs: 200,
  // When the article is back, the highlights stay this long, then fade.
  fadeDelay: 400,
  fadeMs: 400,
  // Release without commit: the highlights fade at once.
  cancelMs: 260,
} as const;

/** Summary depth follows the pinch: past these progress values the summary gets shorter. */
export const DEPTH = {
  brief: 0.62,
  tldr: 0.85,
  hysteresis: 0.03,
  // The depth label appears once the pinch means it.
  showFrom: 0.12,
  showSpan: 0.08,
  // The lens follows the fingers' depth on a spring, so a fast pinch moves it at once (a sheet-like settle, 300 ms).
  lens: { duration: 300, dampingRatio: 0.8 },
} as const;

/** The Distill flight: highlighted sentences detach and travel into their summary lines. */
export const FLIGHT = {
  ms: 420,
  stagger: 60,
  // The curved path bows sideways: a fixed bow plus a share of the vertical travel.
  bow: 28,
  bowPerDy: 0.14,
  // Turns on a chip's own progress u: ink → white (and its paper backing → the field-coloured pill), marker off.
  // The marker stays under the words until the chip is clear of the white page, so it never reads white on white.
  inkToWhite: [0.2, 0.5],
  markerOut: [0.15, 0.55],
  // A chip and the line it becomes swap only in place: over the last 110 ms of its trip (the sheet curve has it
  // within 1 % of its line by then), and on the way back over the first 110 ms, before any chip moves. No line
  // shows while a chip is still travelling to it, so two texts are never read on top of each other.
  swapMs: 110,
  // Inside that swap the chip clears first and the line comes in after it, so neither is strong while both show.
  swapChip: [0, 0.65],
  swapLine: [0.4, 1],
  // On the way back the chip's paper comes first, covering its line and the page opening under it, then its words.
  backPaper: [0, 0.4],
  backWords: [0.35, 1],
  // Home, a chip hands over to the page's copy of its sentence in 40 ms: the same words in the same place, while the
  // article's scroll still settles by a point or two (a longer cross-fade shows them doubled).
  homeMs: 40,
  // "Summarizing for you…" clears this fast when the answer lands, before the first chip can rise through it.
  labelOutMs: 40,
  // A chip caught mid-flight by a new gesture fades where it is while its sentence comes back on the page.
  handoffMs: 120,
  // Several sentences that become one line: the ones that do not own it melt away on the way (u 0.45 → 0.8).
  merge: [0.45, 0.8],
  // A chip that starts outside the visible page fades in over this share of its trip.
  hiddenIn: 0.2,
  // A chip that the chosen depth leaves out dissolves where it is.
  dissolve: 0.7,
  dissolveScale: 0.03,
  // Lines without a chip (titles, discs) rise in when the chip of the point under them has landed: a landing chip is
  // taller than its line and would otherwise sit over the title above it.
  lineMs: 300,
  lineRise: 6,
  // Entity chips rise in after the last chip has landed.
  entityAfter: 120,
  entityStagger: 40,
  entityMs: 220,
  entityRise: 6,
  // Facts land as live data: the meter fills after its line lands.
  meterAfter: 120,
  meterStagger: 40,
  meterMs: 180,
  priceRollMs: 520,
  // Footer: the credit rises in and its reading time rolls once the last chip has landed (ms after that landing),
  // so a chip flying up from below never crosses it; the progress bar on the card fills once the card has settled.
  creditAfter: 0,
  creditMs: 260,
  rollAfter: 60,
  rollMs: 600,
  barAt: 560,
  barMs: 600,
  // The article's scroll travels with the page: it is derived from p (`scrollLink`), back to its top as the page
  // becomes the card and to the reader's place as it opens again, so it never runs on its own clock over a full-screen
  // page. This timed scroll is only the fallback when p has no distance left to travel.
  scrollMs: 600,
  // The wash over the page holds while the article rewinds to its top inside the shrinking card, then clears.
  veilHold: 300,
  veilOut: 300,
} as const;

/** Springs in Apple's two parameters (animate-expo's table); the release velocity is added where a finger let go. */
export const SPRING = {
  // The page settles at its hold after a committed pinch: a reposition after a drag.
  hold: { duration: 400, dampingRatio: 0.8 },
  // The page (and the card's lift) becomes the card. Critically damped: 90 % of the way at ~330 ms, settled by ~560 ms,
  // no overshoot. (A {300, 0.8} spring covered 93 % in 83 ms and overshot to 1.008: the page snapped into the card in
  // five frames while its article rewound 726 pt under it, which read as a jump, not a move.) The article's scroll is
  // tied to this same value (`scrollLink`), so the card and its content move as one motion.
  collapse: { duration: 700, dampingRatio: 1 },
  // Back to the full page: the same shape, and never past p = 0 (the page would outgrow the screen).
  restore: { duration: 600, dampingRatio: 1, overshootClamping: true },
  // The page's follow of the focal point returns home.
  drift: { duration: 400, dampingRatio: 0.8 },
  // Long-press: the summary lifts as a card (a sheet rising under a held finger).
  share: { duration: 300, dampingRatio: 0.8 },
} as const;

export const TIMING = {
  // Stand-in for the model call: how long the summary takes to arrive after a committed pinch.
  thinkMs: 1100,
  thinkIn: 260,
  // One frame: the shortest rest of a wait.
  tick: 16,
  rimOut: 420,
  rimFlashIn: 120,
  rimFlashOut: 520,
  rimLetGo: 300,
  toolbarFadeSpan: 0.3,
  rimTurnMs: 1600,
  // The waiting page breathes ±0.8 % over 1.6 s.
  breathe: 0.008,
  breathePeriod: 1600,
  // Arrival payoff: a ring of light leaves the card; the credit pill catches a sweep.
  bloomMs: 900,
  bloomReach: 70,
  sweepMs: 700,
  sweepDelay: 520,
  // Icon discs pop from 0.8 (never from 0).
  discFrom: 0.8,
  discBack: 1.9,
  motes: 22,
  moteSpeedMin: 18,
  moteSpeedMax: 40,
  // Press feedback: scale 0.97 on the strong ease-out, 150 ms down (the finger deciding) and 100 ms back (the answer
  // snaps); under Reduce Motion a dim instead of the scale.
  pressScale: 0.97,
  pressInMs: 150,
  pressOutMs: 100,
  pressDim: 0.6,
  fieldBreath: 0.015,
  fieldPeriod: 11000,
  fingerFade: 140,
  // Labels under the page (depth, "Summarizing for you…").
  labelFade: 160,
  // Tapping an entity chip lights its words in the summary for 600 ms.
  termIn: 120,
  termHold: 360,
  termOut: 120,
  // Longest frame step the clocks accept, so a stalled frame cannot jump the script.
  maxFrameMs: 250,
  // Reduce Motion: cross-fades only.
  reducedFade: 180,
  longPressMs: 350,
  shareRise: 12,
  shareScale: 1.03,
  // Tapping away drops the card faster than it lifted (the system answers, the finger decided).
  dropMs: 150,
  // The summary comes back to full strength when the article returns.
  summaryBack: 120,
} as const;

export const RIM = {
  pinchMax: 0.55,
  armedBoost: 0.3,
  think: 1,
} as const;

// One autoplay period; the last frame equals the first, so the preview loops without a jump.
export const LOOP_MS = 10000;

// QA only (pixel gate): freeze the autoplay at rest. null in shipped code.
export const QA_HOLD: 'rest' | null = null;

export const AUTOPLAY = {
  resumeDelayMs: 2000,
  // A short read: the article scrolls to its first key sentence.
  readStart: 400,
  readMs: 1100,
  // The scripted fingers: when they press, travel and lift (ms on the loop clock).
  pinchInStart: 1700,
  pinchInEnd: 2600,
  // 0.76 → p ≈ 0.48: all four sentences lock, the depth stays at Key points.
  pinchInScale: 0.76,
  pinchOutStart: 6800,
  pinchOutEnd: 7650,
  // Exactly PINCH.fullOut: the fingers open the page to full size and stop there. (1.85 pulled it 3 % past the screen
  // on the rubber band and sprang back at release, a bump at the end of every return.)
  pinchOutScale: 1.8,
  // Back to the top of the article, so the loop ends where it began.
  topStart: 8300,
  topMs: 1100,
  // Where the script resumes after a touch, by state.
  resumeReading: 0,
  resumeSummary: 6400,
  spreadIn: 150,
  spreadOut: 70,
} as const;
