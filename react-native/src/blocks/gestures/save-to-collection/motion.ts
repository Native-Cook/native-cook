/**
 * Every number the motion uses. Springs are (duration ms, damping ratio); a gesture's release hands its velocity to
 * them. Ease-out enters, ease-in-out moves across the screen; nothing eases in.
 */
export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;

export const SPRING = {
  /** The photo leaving the page under a held finger: no momentum behind it, so no overshoot. */
  lift: { duration: 380, dampingRatio: 1 },
  /** A photo let go away from the saved button goes home; it carries the finger's speed, so a little give. */
  home: { duration: 420, dampingRatio: 0.82 },
  /** The saved button swallowing the photo: pressed in by it, back with a small bounce. */
  gulp: { duration: 360, dampingRatio: 0.55 },
  /** The collection sheet rising from the button, like a system sheet. */
  sheet: { duration: 480, dampingRatio: 0.88 },
  /** Reduce Motion: everything settles critically in 200 ms. */
  reduced: { duration: 200, dampingRatio: 1 },
} as const;

export const TIMING = {
  /** The photo shrinking into the button once it is let go over it. */
  drop: 260,
  /** The badge's figure rolling to the new count. */
  roll: 220,
  toastIn: 220,
  toastOut: 160,
  /** The sheet leaving: faster than it came, ease-out. */
  sheetOut: 300,
  /** Inside the sheet: the new photo settles into the first cell once the sheet is up. */
  arriveDelay: 380,
  arrive: 420,
  /** The saved mark on a photo in the feed. */
  mark: 200,
  /** The demo's finger. */
  finger: 150,
  pressOut: 150,
} as const;

export const SAVE = {
  holdMs: 300,
  /** Lifted: 4 % larger and 4 pt up. */
  liftScale: 0.04,
  liftRise: 4,
  /** The magnet: from 150 pt off the button's centre the photo is pulled toward it and shrinks. */
  magnetFar: 150,
  magnetNear: 36,
  magnetPull: 0.38,
  magnetShrink: 0.55,
  /** Let go within this far of the button's centre (or with the magnet past half) and it saves. */
  dropRadius: 64,
  /** Lean: degrees per pt/s of sideways speed, at most 10°. */
  lean: 0.012,
  leanMax: 10,
  /** The badge counts the collection's places. */
  countBefore: 12,
} as const;

/** Autoplay, seconds from rest; the screen is back at rest before the loop ends. */
export const LOOP_MS = 10_000;
export const RESUME_MS = 2_000;
export const DEMO = {
  /** The photo carried in: the lake, the middle of the right column. */
  tile: 4,
  fingerIn: 0.9,
  down: 1.0,
  lift: 1.35,
  /** The carry, from the photo's centre to the saved button, eased; [s, share of the path]. */
  carry: [[1.45, 0], [2.75, 1]] as const,
  release: 2.85,
  /** Tap the saved button and the collection opens with the new place on top. */
  tapIn: 3.85,
  tap: 4.05,
  close: 7.3,
  /** The feed's mark and the badge go back to the start for the next loop. */
  reset: 8.6,
  /** Back to rest before the script takes over again after a touch (s). */
  tidy: 0.6,
} as const;
