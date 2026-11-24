// Compare Reveal Slider: the divider's physics and the 8 s film, as pure worklet functions.
//
// The divider's place is one number, `pos`: 0 at the photo's left edge, 1 at its right. Left of the
// divider is the room before, right of it the room after. A finger sets pos directly (rubber-banded
// past the stops); on release an underdamped spring carries the finger's velocity to where the
// throw would land, so the handle overshoots and settles.
//
// No React Native imports here, so the module also runs in Node.

export const LOOP = 8; // seconds

// ---------------------------------------------------------------- the divider

export const REST = 0.5; // where the screen opens, and where the film ends
export const STOP_MIN = 0.07; // the handle stays inside the photo
export const STOP_MAX = 0.93;
export const RUBBER = 0.085; // how far past a stop the divider can be pulled, in widths
export const PROJECT = 0.17; // s of release velocity added to the landing place

// The release spring (SwiftUI: .spring(response: 0.5, dampingFraction: 0.6)).
export const SPRING_RESPONSE = 0.5;
export const SPRING_DAMPING = 0.6;
const OMEGA = (2 * Math.PI) / SPRING_RESPONSE;
const OMEGA_D = OMEGA * Math.sqrt(1 - SPRING_DAMPING * SPRING_DAMPING);
const DECAY = SPRING_DAMPING * OMEGA;
/** The same spring for withSpring. */
export const RELEASE_SPRING = { mass: 1, stiffness: OMEGA * OMEGA, damping: 2 * DECAY };

// The handle (pt)
export const HANDLE_W = 46;
export const HANDLE_H = 62;
export const HANDLE_Y = 0.6; // of the photo's height
export const PRESS_SQUASH_X = 0.12; // pressed: 12 % wider
export const PRESS_SQUASH_Y = 0.1; // and 10 % flatter
export const STRETCH_SPEED = 2.6; // widths/s of divider speed for the full stretch
export const STRETCH_X = 0.2; // moving fast: up to 20 % wider
export const STRETCH_Y = 0.08; // and 8 % flatter
export const LEAN = 5; // pt the handle trails behind the line at full speed
export const SPEED_TAU = 0.07; // s, smoothing of the measured speed

// The light on the line
export const GLINT_LAPS = 2; // top-to-bottom runs per loop (a whole number, so the loop closes)
export const GLINT_LEN = 150; // pt
export const LINE_W = 2;
export const GLOW_W = 26;

// Labels: "Before" fades as the left part gets too narrow for it, "After" as the right part does.
export const LABEL_BEFORE_FROM = 0.12;
export const LABEL_BEFORE_TO = 0.3;
export const LABEL_AFTER_FROM = 0.7;
export const LABEL_AFTER_TO = 0.88;

export const EVENING_S = 0.7; // the day/evening crossfade

export const MODE_AUTO = 0;
export const MODE_MANUAL = 1;
export const MODE_RETURN = 2;
export const IDLE_S = 2; // s without a touch before the film returns
export const RETURN_S = 0.7;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return x < lo ? lo : x > hi ? hi : x;
}

/** 0 before a, 1 after b. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smooth(x: number) {
  'worklet';
  const c = clamp01(x);
  return c * c * (3 - 2 * c);
}

export function easeInOutCubic(x: number) {
  'worklet';
  const c = clamp01(x);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}

/** A place past a stop gives way less and less: at most RUBBER beyond it. */
export function rubber(p: number) {
  'worklet';
  if (p < STOP_MIN) {
    const over = STOP_MIN - p;
    return STOP_MIN - over / (1 + over / RUBBER);
  }
  if (p > STOP_MAX) {
    const over = p - STOP_MAX;
    return STOP_MAX + over / (1 + over / RUBBER);
  }
  return p;
}

/** Where a throw lands: the place plus PROJECT seconds of its velocity, inside the stops. */
export function landing(p: number, velocity: number) {
  'worklet';
  return clamp(p + velocity * PROJECT, STOP_MIN, STOP_MAX);
}

/** The release spring s seconds after letting go at `from` with `velocity` (widths/s), heading for `to`. */
export function springAt(from: number, to: number, velocity: number, s: number) {
  'worklet';
  if (s <= 0) return from;
  const a = from - to;
  const b = (velocity + DECAY * a) / OMEGA_D;
  return to + Math.exp(-DECAY * s) * (a * Math.cos(OMEGA_D * s) + b * Math.sin(OMEGA_D * s));
}

/** How much the handle stretches for a divider speed in widths/s: -1 … 1, signed with the direction. */
export function stretchFor(velocity: number) {
  'worklet';
  const s = clamp(velocity / STRETCH_SPEED, -1, 1);
  return s;
}

// ---------------------------------------------------------------- the film
//
//  0.00  rest at the centre
//  0.55  a finger presses the handle
//  0.70  slow drag left: the furnished room takes the photo
//  1.90  a pause
//  2.20  a flick to the right, let go at speed
//  2.85  the spring carries it on, overshoots, settles: the old room takes the photo
//  4.20  Evening (crossfade)
//  4.75  press again, drag back with a wiggle across the sofa
//  6.00  let go: the spring brings it to the centre
//  6.15  Day (crossfade)
//  7.05  rest

export const PRESS_1 = 0.55;
export const DRAG_1_A = 0.7;
export const DRAG_1_B = 1.9;
export const LEFT = 0.15;
export const FLICK_A = 2.2;
export const FLICK_B = 2.85;
export const FLICK_TO = 0.6;
export const FLICK_V = (2 * (FLICK_TO - LEFT)) / (FLICK_B - FLICK_A); // widths/s when the finger lets go
export const RIGHT = landing(FLICK_TO, FLICK_V);
export const EVENING_AT = 4.2;
export const PRESS_2 = 4.75;
export const DRAG_2_A = 4.9;
export const DRAG_2_B = 6.0;
export const BACK = 0.4;
export const WIGGLE = 0.05; // widths
export const WIGGLE_TURNS = 2.5;
export const DAY_AT = 6.15;
export const SETTLED = 7.05;
export const PRESS_S = 0.15;

/** The divider's place in the film. */
export function filmPos(t: number) {
  'worklet';
  if (t <= DRAG_1_A) return REST;
  if (t <= DRAG_1_B) return REST + (LEFT - REST) * easeInOutCubic(seg(t, DRAG_1_A, DRAG_1_B));
  if (t <= FLICK_A) return LEFT;
  if (t <= FLICK_B) {
    const u = seg(t, FLICK_A, FLICK_B);
    return LEFT + (FLICK_TO - LEFT) * u * u;
  }
  if (t <= DRAG_2_A) return springAt(FLICK_TO, RIGHT, FLICK_V, t - FLICK_B);
  // the spring is long settled here: start the second drag from where it rests
  if (t <= DRAG_2_B) {
    const u = seg(t, DRAG_2_A, DRAG_2_B);
    return RIGHT + (BACK - RIGHT) * easeInOutCubic(u) + WIGGLE * Math.sin(2 * Math.PI * WIGGLE_TURNS * u) * Math.sin(Math.PI * u);
  }
  // the last spring, forced exactly onto the rest place before the loop closes
  const p = springAt(BACK, REST, 0, t - DRAG_2_B);
  return REST + (p - REST) * (1 - smooth(seg(t, SETTLED - 0.35, SETTLED)));
}

/** The divider's velocity in the film, widths/s. */
export function filmVelocity(t: number) {
  'worklet';
  const h = 1 / 240;
  return (filmPos(Math.min(LOOP, t + h)) - filmPos(Math.max(0, t - h))) / (2 * h);
}

/** 1 while the film's finger is down. */
export function filmPress(t: number) {
  'worklet';
  const first = smooth(seg(t, PRESS_1, PRESS_1 + PRESS_S)) * (1 - smooth(seg(t, FLICK_B, FLICK_B + PRESS_S)));
  const second = smooth(seg(t, PRESS_2, PRESS_2 + PRESS_S)) * (1 - smooth(seg(t, DRAG_2_B, DRAG_2_B + PRESS_S)));
  return Math.max(first, second);
}

/** The ghost finger's opacity: it arrives just before a press and leaves with the release. */
export function filmGhost(t: number) {
  'worklet';
  const first = smooth(seg(t, PRESS_1 - 0.2, PRESS_1)) * (1 - smooth(seg(t, FLICK_B, FLICK_B + 0.22)));
  const second = smooth(seg(t, PRESS_2 - 0.2, PRESS_2)) * (1 - smooth(seg(t, DRAG_2_B, DRAG_2_B + 0.22)));
  return Math.max(first, second);
}

/** 0 by day, 1 in the evening. */
export function filmEvening(t: number) {
  'worklet';
  return easeInOutCubic(seg(t, EVENING_AT, EVENING_AT + EVENING_S)) * (1 - easeInOutCubic(seg(t, DAY_AT, DAY_AT + EVENING_S)));
}

/** Where the light is on the line, 0 at the top … 1 at the bottom, for a clock in seconds. */
export function glintAt(seconds: number) {
  'worklet';
  const lap = (seconds * GLINT_LAPS) / LOOP;
  return lap - Math.floor(lap);
}

/** How bright the light on the line is: brighter while the divider moves. */
export function glintGain(stretch: number) {
  'worklet';
  return 0.55 + 0.45 * Math.min(1, Math.abs(stretch) * 1.6);
}

export function labelBefore(pos: number) {
  'worklet';
  return smooth(seg(pos, LABEL_BEFORE_FROM, LABEL_BEFORE_TO));
}

export function labelAfter(pos: number) {
  'worklet';
  return 1 - smooth(seg(pos, LABEL_AFTER_FROM, LABEL_AFTER_TO));
}
