// Everything the bar, the button and the pill do is a function of one number, the pose: 0 with the
// button resting in its bowl, 1 with the pill open. It moves at a constant speed, so the numbers
// below are fractions of DURATION as well as of the way.

/** Seconds of one autoPlay loop. */
export const LOOP = 9;
export const OPEN_AT = 1;
export const CLOSE_AT = 4;
export const PROFILE_AT = 6;
export const HOME_AT = 7.5;
/** Seconds after a touch before the loop starts again. */
export const USER_HOLD = 3;

/** Seconds from the button to the open pill, and back. */
export const DURATION = 1;
/** The same under reduced motion, where it is a fade out and a fade in. */
export const REDUCED_DURATION = 0.3;
/** Seconds the bowl takes to change over when the pill is turned round half way. */
export const TURN = 0.15;
/** Seconds of the cross-fade between the pages. */
export const TAB_FADE = 0.25;

export const BAR_HEIGHT = 70;
export const BAR_RADIUS = 26;
export const BAR_MARGIN = 24;
/** The least room under the bar; a larger safe area replaces it. */
export const BAR_BOTTOM = 34;
/** From the edges of the bar to the middle of its tabs. */
export const TAB_INSET = 54;

/** The bowl in the bar's top edge: half its width, its depth, and how much deeper a press makes it. */
export const BOWL_HALF = 54;
export const BOWL_DEPTH = 28;
export const BOWL_PRESS = 5;
/** The bump the edge throws up after the button lets go of it. */
export const PEAK = -7;
export const PEAK_HALF = 26;
/** Where the control points of the bowl's two curves sit, as fractions of its half width. */
export const BOWL_SHOULDER = 0.66;
export const BOWL_FLOOR = 0.46;
/** A quarter circle as a cubic curve. */
export const KAPPA = 0.5523;

export const BUTTON = 52;
/** The button's middle above the bar's top edge at rest. */
export const BUTTON_RAISE = 6;
/** How far a press pushes the button into the bowl, and how far it rises from rest to the pill's place. */
export const PRESS_SINK = 6;
export const RISE = 34;
export const PILL_WIDTH = 232;
/** How much thinner the pill gets while it stretches. */
export const SQUEEZE = 4;
export const ICON_GAP = 54;
export const ICON = 20;
export const TAB_ICON = 24;
/** Half an arm of the plus, and the radius of the dot it shrinks into and of the four it splits into. */
export const PLUS_ARM = 7;
export const SEED = 5;
export const DOT = 6;
/** Two dots stay joined until their middles are this far apart. */
export const NECK_SNAP = 34;
/** Radians from the line between two dots to where a neck leaves them, when they touch. */
export const NECK_SPREAD = 1.3;

// The opening, in fractions of the way.
export const SINK_END = 0.2;
export const RISE_END = 0.45;
export const PEAK_AT = 0.34;
export const FLAT_AT = 0.52;
export const PLUS_GONE = 0.38;
export const SEED_FROM = 0.24;
export const SEED_FULL = 0.42;
export const STRETCH_FROM = 0.42;
export const STRETCH_END = 0.74;
export const PAIR_FROM = 0.46;
export const PAIR_END = 0.72;
export const SPLIT_FROM = 0.54;
export const SPLIT_END = 0.8;
export const ICONS_FROM = 0.74;
export const ICON_STAGGER = 0.04;
export const ICON_FOR = 0.14;
/** How far into its fall the button is when the bowl has opened under it again. */
export const REFORM = 0.36;

const BACK = 1.70158;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** How far `p` is from `a` to `b`, 0 to 1. */
export function span(p: number, a: number, b: number) {
  'worklet';
  return clamp01((p - a) / (b - a));
}

export function smooth(t: number) {
  'worklet';
  return t * t * (3 - 2 * t);
}

export function easeOut(t: number) {
  'worklet';
  return 1 - (1 - t) * (1 - t) * (1 - t);
}

/** Leaves fast, passes the end by a tenth and comes back to it. */
export function backOut(t: number) {
  'worklet';
  return 1 + (BACK + 1) * (t - 1) * (t - 1) * (t - 1) + BACK * (t - 1) * (t - 1);
}

export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** The button's middle below its resting place: a press down, then the jump up to the pill's place. */
export function lift(p: number) {
  'worklet';
  if (p < SINK_END) return PRESS_SINK * smooth(p / SINK_END);
  return PRESS_SINK - (PRESS_SINK + RISE) * backOut(span(p, SINK_END, RISE_END));
}

/**
 * The bowl's depth. `opening` is 1 on the way up, where the edge follows the button, overshoots
 * into a bump and lies flat, and 0 on the way down, where the bowl opens just ahead of the button.
 */
export function bowlDepth(p: number, opening: number) {
  'worklet';
  const pressed = BOWL_DEPTH + BOWL_PRESS;
  if (p < SINK_END) return BOWL_DEPTH + BOWL_PRESS * smooth(p / SINK_END);
  const up = p < PEAK_AT ? mix(pressed, PEAK, easeOut(span(p, SINK_END, PEAK_AT))) : mix(PEAK, 0, smooth(span(p, PEAK_AT, FLAT_AT)));
  const down = pressed * (1 - smooth(span(span(p, SINK_END, RISE_END), 0, REFORM)));
  return mix(down, up, opening);
}

export function bowlHalf(p: number, opening: number) {
  'worklet';
  return mix(BOWL_HALF, mix(BOWL_HALF, PEAK_HALF, smooth(span(p, SINK_END, PEAK_AT))), opening);
}

export function pillWidth(p: number) {
  'worklet';
  return mix(BUTTON, PILL_WIDTH, smooth(span(p, STRETCH_FROM, STRETCH_END)));
}

export function pillHeight(p: number) {
  'worklet';
  return BUTTON - SQUEEZE * Math.sin(Math.PI * span(p, STRETCH_FROM, STRETCH_END));
}

/** The plus: 1 whole, 0 gone into the dot. */
export function plusScale(p: number) {
  'worklet';
  return 1 - smooth(span(p, SINK_END, PLUS_GONE));
}

export function dotRadius(p: number) {
  'worklet';
  return mix(SEED * easeOut(span(p, SEED_FROM, SEED_FULL)), DOT, span(p, PAIR_FROM, SPLIT_FROM));
}

/** Where dot `index` is, from the middle: one dot parts into two halves and each half into two. */
export function dotX(p: number, index: number) {
  'worklet';
  const half = ICON_GAP * smooth(span(p, PAIR_FROM, PAIR_END));
  const quarter = (ICON_GAP / 2) * smooth(span(p, SPLIT_FROM, SPLIT_END));
  return (index < 2 ? -half : half) + (index % 2 === 0 ? -quarter : quarter);
}

/** How far dot `index` has turned into its icon. */
export function iconIn(p: number, index: number) {
  'worklet';
  const from = ICONS_FROM + ICON_STAGGER * index;
  return smooth(span(p, from, from + ICON_FOR));
}
