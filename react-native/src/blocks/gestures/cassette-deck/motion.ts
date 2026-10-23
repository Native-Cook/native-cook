/**
 * Every number the motion uses. The tape is one number, u (how much of side A has wound onto the right reel); the
 * reels' angles, their packs and the counter are all functions of u, so the deck rests exactly as it started whenever
 * the tape is back at the counter's 000.
 */
export type Spring = { duration: number; dampingRatio: number };

export const SPRINGS = {
  /** A key going down under the finger: quick, it bottoms out on the chassis. */
  down: { duration: 110, dampingRatio: 0.9 },
  /** A key springing back up: a metal spring, a small rebound at the top. */
  up: { duration: 220, dampingRatio: 0.55 },
  reduced: { duration: 160, dampingRatio: 1 },
};

export const TAPE = {
  start: 0.18, // u at the counter's 000
  end: 0.97,
  play: 0.01, // u per second at play speed (a side would take ~80 s; enough to see the packs move)
  wind: 0.12, // fast forward and rewind
  motor: 0.22, // s: the capstan motor's spin-up and the reels' run-down
  length: 13200, // pt of tape per unit of u: sets how fast the reels turn (~0.7 turns a second at play)
  /** A latched key rests this far down: caught by its latch, just above the bottom. */
  latch: 0.78,
};

export const METER = { attack: 0.06, release: 0.35, segments: 12 };

export const HAPTIC = { key: 1, latch: 2, memo: 3 } as const;

/** Autoplay, seconds from rest. The deck is back at 000 and still long before the loop ends. */
export const LOOP_MS = 10_000;
export const RESUME_MS = 2_000;
export const DEMO = {
  /** [key, finger down, finger up] */
  presses: [
    [1, 0.8, 1.0], // play
    [2, 4.0, 4.2], // fast forward
    [0, 5.3, 5.5], // rewind: the counter's memory stops it at 000
  ] as const,
};
