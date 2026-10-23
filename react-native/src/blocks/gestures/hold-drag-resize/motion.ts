/**
 * Every number the motion uses. Springs are (duration ms, damping ratio), stepped by the model each frame so the Skia
 * picture, the haptics and the header read the same values. Ease-out enters, ease-in-out moves across; nothing eases in.
 */
export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;

export type Spring = { duration: number; dampingRatio: number };

export const SPRINGS = {
  /** The dragged edge clicking from quarter to quarter under a continuous finger (a small state change, 200 ms). */
  detent: { duration: 200, dampingRatio: 0.8 },
  /** A pushed task going back to its own time after the drag; each one further down the chain takes `rippleStep` longer. */
  ripple: { duration: 400, dampingRatio: 0.8 },
  /** The held block rising off the page: no momentum behind it, so no overshoot. */
  lift: { duration: 400, dampingRatio: 1 },
  /** Letting go: it settles faster than it rose, without a bounce. */
  drop: { duration: 300, dampingRatio: 1 },
  /** The header's focus total: done before the next quarter of a steady drag lands. */
  roll: { duration: 200, dampingRatio: 1 },
  /** Reduce Motion: everything settles critically in 200 ms. */
  reduced: { duration: 200, dampingRatio: 1 },
} as const;

export const RESIZE = {
  holdMs: 300,
  quarter: 15, // minutes per detent
  minLength: 15, // a block is never shorter than this
  rippleStep: 70, // ms added per task down the chain when they go home
  rubber: 0.35, // past the last free minute, or under the shortest length
  rubberMax: 15, // minutes
  squashBulge: 14, // pt of width per unit of squash (height lost / height), up to bulgeMax
  bulgeMax: 3,
} as const;

/** Elevation in pt: shadows are computed from it. */
export const ELEVATION = { rest: 1, pressed: 0.35, held: 12 } as const;
export const LIFT = { scale: 0.012, rise: 2 } as const;

/**
 * Tweens (s): a change of goal restarts from where the value is, on EASE_OUT; the press-in alone is linear, because it
 * is the hold's progress and ends with it.
 */
export const TIMING = {
  lockIn: 0.2, // the meeting's lock, tint and give when the chain hits it
  lockOut: 0.16,
  fadeIn: 0.2, // a pushed task's old time; the "+1 h" beside the grip
  fadeOut: 0.16,
  /** A task going home drops its old time on the frame its own time comes back: fading, it would read "11:15 11:15". */
  wasOut: 0,
  fingerIn: 0.15, // the demo's finger
  fingerOut: 0.12,
  pressOut: 0.15, // a press let go before the hold completes
} as const;

/** Autoplay, seconds from rest; the screen returns to rest before the loop ends. */
export const LOOP_MS = 10_000;
export const RESUME_MS = 2_000;
export const DEMO = {
  /** Stretch: hold the memo at 10:45, drag its end to 12:00, over-push into lunch, back off, let go. */
  grow: {
    down: 0.5, lifted: 0.8, grab: 645,
    path: [
      [0.9, 0], [2.1, 60], [2.5, 92], [2.75, 92], [3.0, 62],
    ] as const, // [s, minutes of finger travel below the grab]
    up: 3.2,
  },
  /** Shrink: hold it again at 11:45 and drag back to 11:00; the tasks go home one after another. */
  shrink: {
    down: 5.0, lifted: 5.3, grab: 705,
    path: [
      [5.4, 0], [6.8, -60],
    ] as const,
    up: 7.1,
  },
  /** Where the finger sits across the block, as a share of its width. */
  fingerX: 0.62,
  /** Back to the plan before the script takes over again after a touch (s). */
  tidy: 0.8,
} as const;
