// Every number the morph uses. Swift and Kotlin are ported from this file.

/** Seconds. One autoPlay loop; rest at both ends. */
export const LOOP = 9;

/** The frame's spring: response 0.42 s, damping ratio 0.82 (mass 1). */
export const SPRING_RESPONSE = 0.42;
export const SPRING_DAMPING_RATIO = 0.82;
export const STIFFNESS = (2 * Math.PI / SPRING_RESPONSE) ** 2; // ≈ 223.8
export const DAMPING = 2 * SPRING_DAMPING_RATIO * Math.sqrt(STIFFNESS); // ≈ 24.5
/** The spring is stepped in slices no longer than this, so a slow frame cannot make it unstable. */
export const SUBSTEP = 1 / 240;

/** Follow-through: title and "+" trail the frame by 45 ms, the subtitle by 75 ms (first-order lag). */
export const FOLLOW_TITLE = 0.045;
export const FOLLOW_SUBTITLE = 0.075;

/** The detail content has its own clock: 0 → 1 in CONTENT_IN while open, back in CONTENT_OUT. */
export const CONTENT_IN = 0.62;
export const CONTENT_OUT = 0.16;
/** Where on that clock body, facts and button start, and how long each takes (share of the clock). */
export const CONTENT_START = [0.1, 0.22, 0.34];
export const CONTENT_SPAN = 0.6;
export const CONTENT_RISE = 14; // pt
export const CONTENT_BLUR = 8; // pt
/** The content is also tied to the frame, so a drag that shrinks the dialog fades it. */
export const CONTENT_GATE = [0.62, 0.92];

/** Corner radii. */
export const RADIUS_CARD = 20;
export const RADIUS_DIALOG = 28;

/** Behind the dialog. */
export const SCENE_SCALE = 0.96;
export const SCENE_DIM = 0.34;
export const SCENE_BLUR = 14; // BlurView intensity

/** Drag: the finger's travel is rubber-banded, then shrinks the dialog toward its card. */
export const DRAG_RUBBER = 260; // pt; travel d becomes d / (1 + d / DRAG_RUBBER)
export const DRAG_SPAN = 360; // pt of banded travel for the whole way back to the card
export const DRAG_FOLLOW = 0.55; // share of the banded travel the frame moves with the finger
export const DRAG_SIDE_RUBBER = 120;
export const DRAG_UP_RUBBER = 90;
export const DRAG_CLOSE_DISTANCE = 120; // pt of raw travel down
export const DRAG_CLOSE_VELOCITY = 650; // pt/s down

/** Press feedback. */
export const PRESS_SCALE = 0.97;
export const PRESS_MS = 120;

/** autoPlay: 2 s after the last touch the dialog closes and the loop restarts at 0. */
export const RESUME_AFTER = 2;

/** The loop's events, in seconds. A and B are the two cards the ghost finger opens. */
export const CARD_A = 1;
export const CARD_B = 4;
export const T = {
  tapA: 1.3, // finger lifts on card A, the dialog opens
  dragStart: 3.55, // finger lands on the photo (already resting there since 3.25)
  dragEnd: 4.15, // lets go: the dialog falls back into its card
  tapB: 5.7,
  tapClose: 7.5, // finger lifts on the "×"
  // 8.3 – 9.0 and 0 – 0.9: rest
};
/** A ghost tap: the dot fades in, presses, lifts at `at`, fades out. Offsets from `at`. */
export const TAP = { appear: -0.4, shown: -0.22, down: -0.15, pressed: -0.07, up: 0.1, gone: 0.24 };
/** The ghost drag: raw finger travel in pt, eased so it lets go while still moving. */
export const GHOST_PULL = 170;
export const GHOST_DRIFT = 14;
export const GHOST_APPEAR = 0.3; // s before dragStart

export function clamp(value: number, low: number, high: number) {
  'worklet';
  return Math.min(Math.max(value, low), high);
}

export function mix(from: number, to: number, amount: number) {
  'worklet';
  return from + (to - from) * amount;
}

/** 0 before a, 1 after b. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeOutCubic(u: number) {
  'worklet';
  const k = 1 - u;
  return 1 - k * k * k;
}

/** Rubber band: follows at first, then resists. */
export function rubber(distance: number, limit: number) {
  'worklet';
  return distance / (1 + distance / limit);
}

/** How fast the banded travel changes with the raw travel, at `distance`. */
export function rubberSlope(distance: number, limit: number) {
  'worklet';
  const k = 1 + distance / limit;
  return 1 / (k * k);
}

/** The ghost drag's raw travel (0…1 of GHOST_PULL) at u = 0…1: it speeds up, so the release carries velocity. */
export function ghostPull(u: number) {
  'worklet';
  return 0.35 * u + 0.65 * u * u;
}

/** d(ghostPull)/du. */
export function ghostPullSpeed(u: number) {
  'worklet';
  return 0.35 + 1.3 * u;
}

/** Opacity of the ghost dot for a tap that lifts at `at`. */
export function tapGhost(t: number, at: number) {
  'worklet';
  return seg(t, at + TAP.appear, at + TAP.shown) * (1 - seg(t, at + 0.02, at + TAP.gone));
}

/** 0…1: how far the ghost finger is pressed in for a tap that lifts at `at`. */
export function tapPress(t: number, at: number) {
  'worklet';
  return seg(t, at + TAP.down, at + TAP.pressed) * (1 - seg(t, at, at + TAP.up));
}
