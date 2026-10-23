import { Easing } from 'react-native-reanimated';

/** Every motion value of the block. Distances in pt, velocities in pt/s, times in ms unless named in seconds. */
// animate-expo: never ease-in on UI.
export const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
export const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

export const SWIPE = {
  activeOffsetX: 12, // horizontal travel before a row takes the touch
  failOffsetY: 10, // vertical travel that hands the touch to the list
  fullAt: 0.55, // fraction of the width that arms a full-swipe delete
  fullRubber: 0.35, // finger-to-row ratio past the full-swipe line
  rightRubber: 0.25, // resistance when dragging the wrong way
  openAt: 78, // strip width that opens on release
  openFlick: -500, // velocity that opens from any distance
  deleteFlick: -1400, // velocity that deletes when the strip is past deleteFlickAt
  deleteFlickAt: 0.4,
} as const;

export const TILT = {
  perVelocity: 0.018, // degrees per pt/s
  max: 14,
  perspective: 700,
  lift: 1.02, // scale while the finger holds the row
} as const;

export const SPRING = {
  follow: { damping: 18, stiffness: 220, mass: 0.6 }, // tilt chasing the velocity
  snap: { damping: 26, stiffness: 300, mass: 1 }, // open and close, takes the finger's velocity
  lift: { damping: 20, stiffness: 320, mass: 0.8 },
  tileStretch: { damping: 22, stiffness: 420, mass: 0.7 },
  collapse: { damping: 28, stiffness: 260, mass: 1 },
  restore: { damping: 24, stiffness: 230, mass: 1 },
  pill: { damping: 20, stiffness: 260, mass: 1 },
  lens: { damping: 18, stiffness: 260, mass: 0.8 },
  menu: { damping: 24, stiffness: 300, mass: 0.8 }, // ζ ≈ 0.77: settles with a hint of overshoot, no wobble
} as const;

export const TIMING = {
  flyOut: 260, // deleted row leaves the screen
  tileFade: 120,
  colour: 160, // Delete tile white → pink
  undoVisible: 4000,
  reduced: 180,
  reducedGap: 200,
  pressScale: 0.94,
  pressIn: 100, // press feedback, asymmetric (review-animations)
  pressOut: 160,
  menuStagger: 0.12, // fraction of the menu's spring between its rows
  tab: 260, // tab content cross-fade (ease-out, under 300 ms)
  roll: 700, // the balance rolls to its new value
  breath: 3.33, // s; the FAB's glow, three per 10 s loop
  sheen: 0.6, // the slab's specular moves this much against the row
  tabSlide: 24,
} as const;

/** QA only (pixel gate): freeze the demo's clock at this second; open the FAB menu or show the Overview cards. Ship null. */
export const QA_HOLD_AT: number | null = null;
export const QA_STATE: 'menu' | 'cards' | null = null;

/** Autoplay: one scripted finger on one clock, seconds. */
export const LOOP_MS = 10_000;
export const RESUME_MS = 3_500;
export const DEMO = {
  fingerFade: 0.15,
  open: { row: 1, down: 0.6, moveEnd: 1.5, up: 1.7, x: 300, to: -170 },
  remove: { row: 0, down: 2.5, moveEnd: 2.95, up: 3.25, x: 320, to: -290 },
  undo: { down: 5.5, up: 5.62 },
  lean: { row: 2, down: 7.2, turn: 7.55, moveEnd: 7.9, up: 7.95, x: 300, to: -96 },
  tidy: 0.8,
} as const;
