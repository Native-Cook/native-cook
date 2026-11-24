/**
 * Rotating Word Headline — the timeline. Every frame of the autoplay loop comes from `posAt(t)`, a word position
 * (0 = first word, 1 = second, …) that only moves during four short transitions. The letters, the width of the word,
 * the underline and the ghost tap all read that one number, so a finger that scrubs `pos` drives the same effect.
 */

/** Loop length in seconds (Swift `static let loop`, Kotlin `LOOP`). */
export const LOOP = 8;
/** Number of words in the cycle (the demo's four). */
export const WORDS = 4;
/** When each word change starts (s); each one lasts TRANSITION. The last ends exactly at LOOP. */
export const STARTS = [1.3, 3.3, 5.3, 7.3];
export const TRANSITION = 0.7;
/** Delay between neighbouring letters, as a fraction of one transition (≈ 49 ms at 0.7 s). */
export const STAGGER = 0.07;
/** Letters in the longest word ("wedding"): the stagger is spread over this many. */
export const MAX_LETTERS = 7;
/** How far a letter travels on its wheel, as a fraction of the font size, and how far it turns. */
export const ROLL_RISE = 0.62;
export const ROLL_DEG = 72;
/** The width of the word starts a little after the letters, the underline a little after the width. */
export const WIDTH_LAG = 0.08;
export const LINE_LAG = 0.16;
/** Ghost tap: appears, presses on the word, lifts. Seconds relative to each start. */
export const GHOST_IN = -0.42;
export const GHOST_PRESS = -0.12;
export const GHOST_OUT = 0.34;
/** Touch: idle before the scene eases back to rest, and the length of that return. */
export const IDLE = 2;
export const RETURN_LEN = 0.9;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

/** 0 before a, 1 after b, linear between. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function mix(a: number, b: number, u: number) {
  'worklet';
  return a + (b - a) * u;
}

/** Whole-number modulo that is never negative. */
export function wrapIndex(i: number, n: number) {
  'worklet';
  return ((i % n) + n) % n;
}

/** Word position at loop time t: 0 at rest, +1 per transition (linear inside a transition; letters ease locally). */
export function posAt(t: number) {
  'worklet';
  let p = 0;
  for (let i = 0; i < STARTS.length; i += 1) p += seg(t, STARTS[i], STARTS[i] + TRANSITION);
  return p;
}

/** Signed distance from word k to the position, wrapped into (−WORDS/2, WORDS/2]. */
export function wordDistance(pos: number, k: number) {
  'worklet';
  let d = (((pos - k) % WORDS) + WORDS) % WORDS;
  if (d > WORDS / 2) d -= WORDS;
  return d;
}

/** One letter's own progress through a transition u (0..1), staggered by its index and eased. */
export function letterProgress(u: number, i: number) {
  'worklet';
  const span = 1 - STAGGER * (MAX_LETTERS - 1);
  return easeInOutCubic(clamp01((u - i * STAGGER) / span));
}

/**
 * Where letter i of word k sits on its wheel: −1 rolled out above, 0 in place, +1 waiting below.
 * Outgoing letters roll up and away, incoming ones roll up into place (like an odometer turning forward).
 */
export function letterWheel(pos: number, k: number, i: number) {
  'worklet';
  const d = wordDistance(pos, k);
  if (d <= -1 || d >= 1) return 1;
  if (d >= 0) return -letterProgress(d, i);
  return 1 - letterProgress(d + 1, i);
}

/** Width of the word slot between two measured widths, lagging the letters slightly. */
export function morphAmount(pos: number, lag: number) {
  'worklet';
  const u = pos - Math.floor(pos);
  return easeInOutCubic(clamp01((u - lag) / (1 - lag)));
}

/** Ghost finger over the word: [opacity, scale]. Only during autoplay. */
export function ghostAt(t: number) {
  'worklet';
  let o = 0;
  let s = 1;
  for (let i = 0; i < STARTS.length; i += 1) {
    const a = STARTS[i];
    const show = easeOutCubic(seg(t, a + GHOST_IN, a + GHOST_IN + 0.2)) * (1 - easeInOutCubic(seg(t, a + GHOST_OUT - 0.2, a + GHOST_OUT)));
    if (show > o) {
      o = show;
      const press = seg(t, a + GHOST_PRESS, a) * (1 - seg(t, a, a + 0.18));
      s = 1 - 0.18 * easeInOutCubic(press);
    }
  }
  return [o, s];
}
