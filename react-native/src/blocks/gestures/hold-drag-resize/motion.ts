/** Every motion value of the block. Sizes are preview heights in pt; times in ms unless named in seconds. */
export const SIZES = [401, 476, 874] as const; // preview heights: compact (rest ref), large (f043), full screen (f034)
/** Preview width and top at each size, measured on the rectified frames. */
export const FRAMES = [
  { width: 227, top: 61 },
  { width: 268, top: 61 },
  { width: 402, top: 0 },
] as const;

export const RESIZE = {
  holdMs: 260, // long press before the preview lifts
  gain: 1.15, // pt of height per pt of drag (the preview grows from its top, so a little more than 1 feels attached)
  rubber: 0.3, // past the smallest or largest size
  rubberMax: 60,
  project: 0.18, // seconds of release velocity added before picking the nearest size
  lift: 0.97, // preview scale while held
} as const;

export const SPRING = {
  lift: { damping: 18, stiffness: 340, mass: 0.7 },
  drop: { damping: 22, stiffness: 460, mass: 0.6 }, // the release settles faster than the lift (asymmetric)
  snap: { damping: 24, stiffness: 210, mass: 1 }, // takes the release velocity
  coach: { damping: 20, stiffness: 200, mass: 1 },
} as const;

export const TIMING = {
  fadeChrome: 160,
  coachFade: 240,
  reduced: 200,
  clip: 3000, // each clip of the reel
  crossfade: 400,
  kenBurns: 0.06, // scale added over a clip
  // pt per second under the playhead: one loop is exactly one period of the ruler (dot, 10s, dot, 30s at 85 pt).
  stripSpeed: 340 / 12,
};

/** Autoplay, seconds. The reel's own clock is the loop clock, so playback loops with it. */
export const LOOP_MS = 12_000;
export const RESUME_MS = 3_500;
export const DEMO = {
  fingerFade: 0.15,
  coachHand: { down: 0.4, bottom: 1.1, up: 1.9, travel: 36, grow: 44 },
  gotIt: { show: 2.15, tap: 2.45 },
  grow: { down: 3.0, lifted: 3.26, mid: 3.9, hold: 4.5, end: 5.0, up: 5.15 }, // pauses on the large size (f043)
  shrink: { down: 7.1, lifted: 7.36, end: 8.5, up: 8.6 },
  coachBack: 11.6,
  tidy: 0.6,
} as const;

/** Upgrade (docs/gestures-design/hold-drag-resize.md → Upgrade options), added on top of the replica. */
export const UPGRADE = {
  buttonPress: 0.96, // editor buttons
  press: 0.99, // scale on touch-down, before the hold lifts it (anticipation within a frame)
  pressInMs: 100,
  pressOutMs: 160,
  magnet: 18, // pt around a size where the drag is pulled toward it
  magnetPull: 0.45, // share of the remaining distance pulled at the size itself
  follow: 16, // 1/s: how fast the rows under the preview catch up with its edge (follow-through)
  liftShadow: { opacity: 0.55, radius: 28, y: 14 },
  rim: 0.22, // white rim opacity while held
  badgeInMs: 140, // size badge: in fast, out slower
  badgeOutMs: 220,
  badgeRise: 6,
  stagger: 0.025, // share of fullness between full-screen controls coming in
} as const;

/** QA only: slows the demo script by this factor for slow-motion review; 1 in the shipped block. */
export const QA_SLOWMO = 1;

/** QA only: -1 in the shipped block. 0 holds the compact editor (clip at 9.5 s), 1 the coach mark (t = 0), 2 the large size (clip at 4.5 s). */
export const QA_STATE = -1 as -1 | 0 | 1 | 2;
const QA_TIMES = [9.5, 0, 4.5] as const;
export const QA_T: number = QA_STATE >= 0 ? QA_TIMES[QA_STATE as 0 | 1 | 2] : 0;
