/**
 * Every number the motion uses. Springs are (duration ms, damping ratio) and are stepped by the model each frame, so
 * the slab, the rows and the gauge read the same values. Ease-out enters, ease-in-out moves across, nothing eases in.
 */
export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;

export type Spring = { duration: number; dampingRatio: number };

export const SPRINGS = {
  /** A row joining the slab: one soft overshoot, like paper settling. */
  join: { duration: 460, dampingRatio: 0.72 },
  /** A row letting go: no bounce. */
  leave: { duration: 260, dampingRatio: 1 },
  /** The slab lifting while a finger holds it. */
  lift: { duration: 520, dampingRatio: 0.85 },
  /** Selection chrome (number to disk). */
  mode: { duration: 360, dampingRatio: 1 },
  /** Reduce Motion: everything settles critically in 200 ms. */
  reduced: { duration: 200, dampingRatio: 1 },
} as const;

/** Leaving selection, the whole sheet fades as one piece (ms). */
export const SHEET_FADE_MS = 240;

export const SELECT = {
  longPress: 300,
  /** Vertical travel on the number column before a range drag starts (selection mode). */
  dragSlop: 8,
  autoScrollMax: 720, // pt/s at the very edge
  coastDecay: 3.5, // 1/s
  coastStop: 20, // pt/s
  /** Lift while selecting but not holding. */
  restLift: 0.35,
} as const;

export const DOWNLOAD = {
  /** One row's fill, and the gap before the next starts (s). */
  row: 0.9,
  stagger: 0.11,
  /** After the last episode lands: its check draws in, then selection ends by itself (s). */
  settle: 0.4,
  /** Check marks fading in or out (s); the ring leaves twice as fast. */
  mark: 0.24,
} as const;

export const TIMING = {
  fingerFade: 0.15,
  press: 120,
  swap: 200, // panel text
  reduced: 180,
  /** A control changing state (the button's colour, Cancel hiding while downloading), and a press letting go (ms). */
  state: 200,
} as const;

/** The plane on the card: its glyph scale (24 pt grid → 18 pt), and its fade back to the gate when the loop resets. */
export const PLANE = { scale: 0.75, back: 300 } as const;

/**
 * The boarding pass that ends a download: it rises from the button on a spring (no bounce past its place; a light
 * object settling), the scrim fades in under it; it leaves faster than it came (ease-out, 220 ms) and drops 24 pt.
 */
export const PASS = {
  rise: { duration: 520, dampingRatio: 0.86 },
  from: 120,
  scale: 0.96,
  scrimIn: 260,
  out: 220,
  drop: 24,
} as const;

/** The timeline: a row's time fades in once its segment is half grown; the landing marker's fade (ms). */
export const TIMELINE = { timeFrom: 0.4, timeSpan: 0.4, land: 200 } as const;

/** Press feedback on the panel's button: scale on press-in; Reduce Motion dims instead of moving. */
export const PRESS = { scale: 0.97, dim: 0.85, ms: 120 } as const;

/** Autoplay: one scripted finger on one clock, in seconds from rest. Rows index EPISODES. */
export const LOOP_MS = 10_000;
export const RESUME_MS = 2_000;
export const DEMO = {
  hold: { row: 0, down: 0.6 }, // long press; selection starts at down + longPress
  // Five episodes are 4h 43m: 42 minutes short of the flight. One more tap covers it and the landing drops in.
  drag: { from: 0, to: 4, start: 1.05, end: 2.3 },
  release: 2.52,
  taps: [{ row: 5, at: 3.6 }],
  download: { at: 4.8 },
  reset: { at: 9.1 },
  /** Scroll home when the script takes over again after a touch (s). */
  tidy: 0.6,
} as const;
