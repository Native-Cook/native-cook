/*
 Stream Fade Answer — the clocks. Two of them:

 The stream clock S (seconds) is the only thing the answer depends on: every word, row and the
 chip has a start time on it and is a pure function of S. S < 0 is "nothing yet", S ≥ end is the
 finished answer. The self-playing loop and a scrubbing finger both just move S.

 The loop clock t (LOOP = 10 s) maps to S:
   0.00 – 0.70   rest: the finished answer (S = end).
   0.70 – 1.00   the answer fades out (ease-in), the question stays.
   0.90 – 1.50   the thread scrolls back so the question sits where a new turn starts.
   1.25 – 2.60   "thinking": one grey line with a light band sweeping across it twice
                 (0.7 s a sweep), in over 0.2 s and out over the last 0.25 s.
   2.60 – 8.14   the stream: S = t − 2.6, linear. Words arrive 28 ms apart on average; with the
                 demo's copy the heading starts at S = 0, the paragraphs at 0.35 and 1.59, the rows
                 at 2.60, 2.94 and 3.28, the tip at 3.85, the chip at 4.59 and S = 5.54 is the end.
   8.14 – 10.0   rest with the finished answer; t = 10 is t = 0.
*/

export const LOOP = 10;

/** Loop phases, in loop seconds. */
export const HOLD_END = 0.7;
export const FADE_END = 1.0;
export const SCROLL_BACK_A = 0.9;
export const SCROLL_BACK_B = 1.5;
export const THINK_A = 1.25;
export const THINK_IN = 0.2;
export const STREAM_AT = 2.6;
export const THINK_OUT = 0.25;
/** One sweep of the thinking band, and how many fit between THINK_A and the stream. */
export const THINK_SWEEP = 0.7;

/** A word: 28 ms after the one before on average, 0.42 s to arrive from 6 pt below and a 4 pt blur. */
export const WORD_GAP = 0.028;
export const WORD_RATE = 1 / WORD_GAP;
export const WORD_DUR = 0.42;
export const WORD_RISE = 6;
export const WORD_BLUR = 4;
export const BLUR_LEVELS = 8;
/** The newest line fades toward the front of the stream over this many points. */
export const FEATHER = 96;

/** Between two blocks of the answer the stream pauses, as a model does at a paragraph. */
export const BLOCK_PAUSE = 0.28;
/** Itinerary rows: one every ROW_GAP, each sliding in over ROW_DUR behind its hairline (LINE_DUR). */
export const ROW_GAP = 0.34;
export const ROW_DUR = 0.46;
export const ROW_RISE = 10;
export const LINE_DUR = 0.4;
/** The cost chip pops in over CHIP_DUR and counts up over CHIP_COUNT. */
export const CHIP_DUR = 0.36;
export const CHIP_COUNT = 0.7;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeOutQuad(x: number) {
  'worklet';
  return 1 - (1 - x) * (1 - x);
}

export function easeInQuad(x: number) {
  'worklet';
  return x * x;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/**
 When each of `count` words starts, in seconds from the first. Tokens do not arrive evenly: each
 gap is 0.4–1.6 × the mean (seeded), so the mean stays 1 / rate and the order never changes.
*/
export function wordStarts(count: number, rate: number, seed: number): number[] {
  const out: number[] = [];
  let at = 0;
  for (let i = 0; i < count; i += 1) {
    out.push(at);
    at += (0.4 + 1.2 * hash(i * 1.37 + seed * 17.1 + 3)) / rate;
  }
  return out;
}

/** S for the self-playing loop at loop time t; `end` is the S of the finished answer. */
export function streamAt(t: number, end: number) {
  'worklet';
  return t < FADE_END ? end : t - STREAM_AT;
}

/** Opacity of the whole answer in the loop: it fades out before the next turn. */
export function answerAlphaAt(t: number) {
  'worklet';
  return t < FADE_END ? 1 - easeInQuad(seg(t, HOLD_END, FADE_END)) : 1;
}

/** The thinking line in the loop: [opacity, band position 0…1 across the line]. */
export function thinkingAt(t: number): number[] {
  'worklet';
  const alpha = seg(t, THINK_A, THINK_A + THINK_IN) * (1 - seg(t, STREAM_AT - THINK_OUT, STREAM_AT));
  const u = (t - THINK_A) / THINK_SWEEP;
  const band = easeInOutCubic(clamp01(u - Math.floor(Math.max(0, u))));
  return [alpha, band];
}

/** Rubber band past the ends of the stream while scrubbing: `over` seconds become fewer. */
export function rubber(over: number) {
  'worklet';
  return (over * 0.55) / (1 + Math.abs(over) / 0.6);
}
