/**
 * Every number the motion uses. Springs are (duration ms, damping ratio); a finger always ends in a spring, the
 * slot's sequence after a release is one clock so the loop repeats exactly. Ease-out enters, ease-in-out moves across;
 * nothing eases in.
 */
export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;

export const DRAG = {
  /** Movement that decides between a swipe (carousel) and a pull (into the slot). */
  decide: 8,
  /** Pull at which the slot will take the card, and the pull at which the card has fully leaned into it. */
  commit: 150,
  full: 210,
  /** The card shrinks a little and leans back as it nears the slot, so its bottom edge meets the opening. */
  minScale: 0.8,
  leanDeg: 46,
  lift: 1.03,
  upResist: 0.2,
  sideResist: 0.25,
  swipeVelocity: 600,
} as const;

export const SPRINGS = {
  snap: { duration: 420, dampingRatio: 0.86 },
  back: { duration: 380, dampingRatio: 0.8 },
  lift: { duration: 200, dampingRatio: 1 },
} as const;

/** After a release, ms from the release. */
export const SEQ = {
  /** The card slides the rest of the way into the slot. */
  sink: 380,
  /** The shelf reads the card: its light breathes twice. */
  read: 1_000,
  /** The activated gift rises out of the slot (spring-like ease-out), then turns over to its code. */
  rise: 700,
  pause: 140,
  flip: 560,
  /** Steps and button come in after the flip, 60 ms apart. */
  stagger: 60,
  detailsIn: 260,
} as const;

/** Closing: the gift leaves faster than it came, then the cards come back. */
export const CLOSE = { out: 220, back: 420 } as const;

/** Titles change out-then-in: the old line leaves up, the new one arrives from below once it has gone. */
export const TITLE = { out: 140, in: 220, rise: 6 } as const;

export const PRESS = { scale: 0.97, inMs: 100, outMs: 160 } as const;
export const REDUCED_MS = 200;

/** Autoplay: one scripted finger on one clock (ms). */
export const LOOP_MS = 10_000;
export const RESUME_MS = 2_000;
export const SCRIPT = {
  /** Swipe to Fernhouse, and back to Northline at the end. */
  swipes: [
    { at: 500, ms: 460, dx: -220 },
    { at: 8_950, ms: 460, dx: 220 },
  ],
  pullDown: 1_450,
  pullUp: 2_500,
  pullBy: 196,
  done: 7_900,
} as const;
