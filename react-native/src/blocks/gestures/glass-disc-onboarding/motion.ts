/**
 * Every number the motion uses. Springs are (duration ms, damping ratio); the finger's release hands its speed to them.
 * Ease-out enters, ease-in-out moves across; nothing eases in.
 */
export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;

export const SPRING = {
  /** The magnifier let go short of the threshold drops back into the dome, carrying the finger's speed. */
  home: { duration: 520, dampingRatio: 0.78 },
  /** The lens settling under a still finger. */
  follow: { duration: 160, dampingRatio: 1 },
  reduced: { duration: 200, dampingRatio: 1 },
} as const;

export const TIMING = {
  /** Opening out into the map: the disc grows past the corners while the enlargement and the rim fade. */
  open: 680,
  /** Closing: the map gathers back into a magnifier, then drops into the dome. */
  gather: 420,
  /** The route drawing itself once the map is open. */
  route: 900,
  routeDelay: 120,
  hint: 200,
  finger: 150,
} as const;

export const PULL = {
  /** Pulled this far up (share of the way to the lens's resting height), or flicked up, it opens out. */
  commit: 0.6,
  flick: 650, // pt/s upward
  /** Under the finger the dome rubber-bands below its own rest. */
  rubber: 0.3,
} as const;

/** Autoplay, seconds from rest; the first screen is back before the loop ends. */
export const LOOP_MS = 10_000;
export const RESUME_MS = 2_000;
export const DEMO = {
  fingerIn: 0.8,
  down: 0.95,
  /** The pull, as the finger's path: [s, x share of width, y share of height]. */
  path: [[1.05, 0.5, 0.9], [2.3, 0.5, 0.5], [2.75, 0.62, 0.42], [3.05, 0.56, 0.46]] as const,
  release: 3.15,
  tapIn: 6.7,
  tap: 6.9,
  tidy: 0.6,
} as const;
