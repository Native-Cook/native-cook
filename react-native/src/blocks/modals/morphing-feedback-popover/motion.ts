// Every number the popover's motion uses. Swift and Kotlin are ported from this file.

/** Seconds. One autoPlay loop; rest at both ends. */
export const LOOP = 8;

/** Two springs, one per axis (mass 1). Width is the bouncier one and leads; height follows AXIS_STAGGER later. */
export const WIDTH_RESPONSE = 0.4;
export const WIDTH_DAMPING_RATIO = 0.66;
export const HEIGHT_RESPONSE = 0.44;
export const HEIGHT_DAMPING_RATIO = 0.78;
export const WIDTH_STIFFNESS = (2 * Math.PI / WIDTH_RESPONSE) ** 2; // ≈ 246.7
export const WIDTH_DAMPING = 2 * WIDTH_DAMPING_RATIO * Math.sqrt(WIDTH_STIFFNESS); // ≈ 20.7
export const HEIGHT_STIFFNESS = (2 * Math.PI / HEIGHT_RESPONSE) ** 2; // ≈ 203.9
export const HEIGHT_DAMPING = 2 * HEIGHT_DAMPING_RATIO * Math.sqrt(HEIGHT_STIFFNESS); // ≈ 22.3
/** Opening: width first, height 50 ms later. Closing: height first, width 50 ms later. */
export const AXIS_STAGGER = 0.05;
export const SUBSTEP = 1 / 240;

/** The label trails the frame by 60 ms (first-order lag) on its way to the placeholder's place. */
export const FOLLOW_LABEL = 0.06;
/** As a placeholder the label is this opaque. */
export const PLACEHOLDER_ALPHA = 0.42;

/** The popover's content: its own clock, 0 → 1 in CONTENT_IN while open, back in CONTENT_OUT. */
export const CONTENT_IN = 0.5;
export const CONTENT_OUT = 0.12;
/** Field, faces, Send: where each starts on that clock and how long it takes. */
export const CONTENT_START = [0.06, 0.24, 0.38];
export const CONTENT_SPAN = 0.6;
export const CONTENT_RISE = 10; // pt
/** The content is tied to the height too, so a collapse or a drag fades it. */
export const CONTENT_GATE = [0.5, 0.9];

/** Radii: the pill is a capsule, the popover a rounded card. */
export const RADIUS_OPEN = 26;
/** Behind the popover. */
export const SCRIM = 0.14;

/** Drag down: rubber-banded travel squashes the popover toward the pill. */
export const DRAG_RUBBER = 220; // pt; travel d becomes d / (1 + d / DRAG_RUBBER)
export const DRAG_SPAN = 260; // pt of banded travel for the height to reach the pill's
export const DRAG_WIDTH_SHARE = 0.5; // the width gives way half as fast
export const DRAG_FOLLOW = 0.3; // share of the banded travel the whole frame moves down
export const DRAG_UP_RUBBER = 70;
export const DRAG_CLOSE_DISTANCE = 90; // pt of raw travel
export const DRAG_CLOSE_VELOCITY = 600; // pt/s

/** "Thanks!": seconds after Send. The check is two strokes drawn one after the other. */
export const THANKS = {
  labelOut: 0.15, // the label is gone by then
  in: [0.22, 0.42], // check and word fade in as the pill closes up
  short: [0.3, 0.42], // the check's short stroke
  long: [0.42, 0.62], // its long stroke
  pulse: [0.62, 0.9], // the pill swells 4 % and settles
  out: [1.5, 1.68], // 1.2 s after the check began
  labelIn: [1.62, 1.85],
  end: 1.9,
};
export const THANKS_PULSE = 0.04;

/** Press feedback. */
export const PRESS_SCALE = 0.96;
export const PRESS_MS = 120;
/** A face that is picked pops to this and springs back. */
export const FACE_POP = 1.28;
export const FACE_POP_MS = 110;
export const FACE_SPRING = { stiffness: 380, damping: 14, mass: 1 };

/** autoPlay: 2 s after the last touch the popover closes and the loop restarts at 0. */
export const RESUME_AFTER = 2;

/** The loop's events, in seconds. */
export const T = {
  tapPill: 0.8, // the ghost finger lifts, the pill opens
  typeStart: 1.5,
  typeEnd: 3.7,
  tapFace: 4.05,
  tapSend: 4.8, // collapse, then THANKS until 6.7
  // 6.7 – 8 and 0 – 0.8: rest
};
/** The face the ghost finger picks (0 = worst … 3 = best). */
export const GHOST_FACE = 3;
/** A ghost tap: the dot fades in, presses, lifts at `at`, fades out. Offsets from `at`. */
export const TAP = { appear: -0.4, shown: -0.22, down: -0.15, pressed: -0.07, up: 0.1, gone: 0.24 };

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

export function easeInOutCubic(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
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
