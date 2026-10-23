/** Every motion value of the block, timed on the source (4 fps frames, 12.75 s). */

export const PRESS = {
  // Long-press opens the menu (frames 2–3); the pressed card lifts.
  longPressMs: 380,
  menuIn: { damping: 20, stiffness: 320, mass: 1 },
  menuOutMs: 140,
  cardLift: 1.03,
} as const;

export const SCREEN = {
  // Grid out and slot in (frames 7–8); the way back is RETRACT's hand-over (frames 44–47).
  gridOutMs: 260,
  slotInMs: 220,
} as const;

/** The Dynamic Island opening into the printer and closing again: the system's own feel, one soft overshoot. */
export const ISLAND_SPRING = { damping: 17, stiffness: 230, mass: 1 } as const;
/** The paper waits this long for the island to open (most of the spring) before the feed starts; the feed still ends before the first scroll. */
export const ISLAND_LEAD_MS = 220;

export const FEED = {
  // The printer feeds in jerks: N steps, each a quick push then a rest (frames 9–16, ~2 s for a screen of paper).
  ms: 2300,
  steps: 14,
  push: 0.55,
  // The paper swings a little after every push and settles (sag and flex of thin paper).
  swing: 0.35,
  swingDamping: 7,
  swingFreq: 9,
} as const;

export const SCROLL = {
  // Dragging the paper: 1:1, rubber band past its ends, then momentum with friction.
  rubber: 0.55,
  deceleration: 0.997,
  // Pulled down past its top by this much (shown, after the rubber band), the paper goes back into the slot.
  dismissBy: 90,
  dismissVelocity: 1000,
  // The action bar comes up within this distance of the end (frames 27–31).
  barWithin: 40,
  barMs: 220,
} as const;

export const RETRACT = {
  // The paper is drawn back into the slot (frames 42–44), ending the moment its torn foot passes under the island.
  ms: 520,
  // The hand-over: the list is revealed under the rising paper (it starts a quarter into the pull and ends as the
  // island has nearly closed), so no frame shows the open island over an empty page.
  gridLeadMs: 130,
  gridInMs: 680,
  // Reduce Motion: the paper and the list cross-fade instead.
  reducedMs: 200,
} as const;

// One autoplay period; it starts and ends at rest on the grid.
export const LOOP_MS = 10000;

export const AUTOPLAY = {
  resumeDelayMs: 2000,
  pressAt: 500,
  pickAt: 1300,
  // Down to the end, back a little, to the end again (frames 21–41); the feed ends at 3.6 s.
  scrolls: [
    { start: 4100, end: 5300, by: 700 },
    { start: 5500, end: 6000, by: -200 },
    { start: 6100, end: 6600, by: 220 },
  ],
  tearAt: 7600,
} as const;

/** Upgrade, added on top of the replica (docs/gestures-design/receipt-printer.md → Upgrade options). */
export const UPGRADE = {
  // The picked card shrinks into the printer's slot (container transform), 420 ms ease-out cubic.
  morphMs: 420,
  // Fresh thermal print develops: a white veil under the slot fades the newest 72 pt of print.
  developH: 72,
  developOpacity: 0.7,
  developFadeMs: 260,
  // The cutter at the end of the feed: a 5 pt tug down, then a stiff spring back.
  cutDrop: 5,
  cutDownMs: 90,
  cutSpring: { damping: 11, stiffness: 420, mass: 0.6 },
  // Status light on the slot: blinks at 6 Hz while printing, glows dim otherwise.
  ledBlinkHz: 6,
  ledIdle: 0.35,
  // Held paper lifts: its shadow deepens.
  liftShadow: [0.16, 0.3],
  liftMs: 160,
  // While read, the hanging slip sways ±0.12° over 5 s.
  swayDeg: 0.12,
  swayMs: 5000,
  // Before it is drawn back, the paper is tugged 10 pt down (anticipation).
  tug: 10,
  tugMs: 120,
} as const;

// QA only: slows the autoplay script, the feed and the morph by this factor for slow-motion review; 1 in the shipped block.
export const QA_SLOWMO = 1;

// QA only: holds the autoplay at this time (ms); -1 in the shipped block.
export const DEBUG_FREEZE_MS = -1;

/** Animation workflow pass (20 October): find-animation-opportunities → animate-expo → review-animations. */
export const POLISH = {
  // Curves from the animate-expo tables (Reanimated's built-in cubic is weaker).
  easeOut: [0.23, 1, 0.32, 1],
  easeInOut: [0.77, 0, 0.175, 1],
  // Press and hold a tile: it sinks to 0.97 over the long-press time (the iOS context-menu hold), and springs back.
  holdScale: 0.97,
  releaseSpring: { duration: 300, dampingRatio: 0.8 },
  // Bar buttons: scale 0.97 on press-in in 100 ms, back in 160 ms.
  pressScale: 0.97,
  pressInMs: 100,
  pressOutMs: 160,
  // Pulled past the dismiss point: one tick and the status light goes full bright, so the release is predictable.
  armedLed: 1,
} as const;
