// Every number the annotations use. Swift and Kotlin are ported from this file.

/** Seconds. One autoPlay loop: clean page, six marks draw in order, hold, lift off in reverse, clean page. */
export const LOOP = 9;

/** The marks, in the order they are drawn. */
export const MARK = { circle: 0, highlight: 1, underline: 2, bracket: 3, note: 4, arrow: 5 } as const;
export const MARK_COUNT = 6;

/** When each mark draws (ease-in-out cubic from its start to its end), in seconds. */
export const DRAW: readonly (readonly [number, number])[] = [
  [0.9, 1.7], // circle round the key term
  [1.95, 2.75], // highlighter swipe (the ghost finger drags it)
  [2.95, 3.7], // wavy underline
  [3.9, 4.35], // margin bracket
  [4.45, 5.4], // the handwritten note
  [5.4, 5.8], // arrow from the note to the bracket
];
/** When each mark lifts off: the stroke retracts from its end back to its start. Last drawn lifts first. */
export const LIFT: readonly (readonly [number, number])[] = [
  [7.75, 8.1],
  [7.6, 7.9],
  [7.45, 7.75],
  [7.3, 7.55],
  [7.12, 7.47],
  [7.0, 7.25],
];
/** Everything is on the page between these two; a touch runs the clock to HOLD and parks it there. */
export const HOLD = 6.4;
export const LIFT_START = 7.0;
/** While a finger has the page, unfinished marks finish at this multiple of the loop's speed. */
export const CATCH_UP = 2.5;
/** autoPlay: 2 s after the last touch the finger's marks lift off and the loop carries on into its own lift-off. */
export const RESUME_AFTER = 2;

/** The ghost finger that drags the highlighter in the loop. */
export const GHOST = { appear: 1.62, shown: 1.82, lift: 2.78, gone: 3.0, size: 40 };

/** A finger's own highlight: seconds to swipe on after a tap, and to lift off. */
export const USER_DRAW = 0.42;
export const USER_LIFT = 0.26;
/** A touch that travels less than this is a tap (toggle the sentence); further and it marks words. */
export const TAP_SLOP = 8;
/** At most this many finger-made highlights are kept; the oldest lifts off when one more is made. */
export const USER_MARKS = 8;

/** Pen. Widths in points; a stroke swells from `TAPER_IN` of its width and thins to `TAPER_OUT` at the flick. */
export const PEN_WIDTH = 2.1;
export const NOTE_WIDTH = 1.9;
export const TAPER_IN = 0.55;
export const TAPER_OUT = 0.32;
export const TAPER_IN_SPAN = 0.12; // share of the stroke's length
export const TAPER_OUT_SPAN = 0.22;
export const JITTER = 0.55; // pt, across the stroke
export const WIDTH_NOISE = 0.14; // share of the width
export const STEP = 1.6; // pt between the points a stroke is drawn through
export const PEN_LIFT = 10; // pt of "air" between two strokes of one mark, so the pen visibly hops

/** Circle round the term: 1.1 turns from the upper left, spiralling out so the end overlaps the start. */
export const CIRCLE = { padX: 10, ry: 0.86, turns: 1.1, start: -2.5, spiral: 0.09, tilt: -0.07 };
/** Wavy underline: amplitude and wavelength in points, below the baseline. */
export const WAVE = { drop: 5.5, amp: 2.1, length: 13, overshoot: 3, rise: 1.2 };
/** Highlighter band, as shares of the font size above and below the baseline; multiply over the text. */
export const BAND = { up: 0.92, down: 0.3, padX: 3, wobble: 1.1, step: 9, slant: 3, opacity: 0.62, streak: 0.2 };
/** Margin bracket: a brace, in points from the text's right edge. */
export const BRACE = { gap: 7, depth: 6, nose: 6, curl: 9 };
/** The note: x-height in points and its tilt in radians. */
export const NOTE = { xHeight: 12.5, tilt: -0.1, gap: 0.28 };
/** Arrow head: two strokes of this length at this angle off the shaft. */
export const HEAD = { length: 8.5, spread: 0.5 };

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

export function easeInOutCubic(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

export function easeOutCubic(u: number) {
  'worklet';
  const k = 1 - u;
  return 1 - k * k * k;
}

/** Seeded 0…1. */
export function hash(n: number) {
  'worklet';
  const s = Math.sin(n * 12.9898) * 43758.5453;
  return s - Math.floor(s);
}

/**
 * How much of mark `index` is on the page at loop time t: 0 none, 1 all of it, drawn from its start.
 * `cap` freezes the drawing at an earlier time (a finger took the page mid-loop) while the lift-off still runs
 * on t; pass LOOP for no cap.
 */
export function markAt(t: number, cap: number, index: number) {
  'worklet';
  const draw = DRAW[index];
  const lift = LIFT[index];
  return Math.max(0, easeInOutCubic(seg(Math.min(t, cap), draw[0], draw[1])) - easeInOutCubic(seg(t, lift[0], lift[1])));
}

/** Where the clock parks when a finger takes the page at t: the pen finishes the mark it is on, and stops. */
export function parkAt(t: number) {
  'worklet';
  for (let i = 0; i < MARK_COUNT; i += 1) {
    if (t > DRAW[i][0] && t < DRAW[i][1]) return DRAW[i][1];
  }
  return t;
}

/** Opacity of the ghost finger. */
export function ghostAt(t: number) {
  'worklet';
  return seg(t, GHOST.appear, GHOST.shown) * (1 - seg(t, GHOST.lift, GHOST.gone));
}
