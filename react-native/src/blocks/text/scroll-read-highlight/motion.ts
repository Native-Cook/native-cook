// The scripted scroll and the reading front, as pure functions. Every stack reads these numbers.
export const LOOP = 10;
export const RESUME_AFTER = 2;
export const RETURN_SECONDS = 1.1;

/** Three flicks, each to a share of the article's scroll range. */
export const FLICKS = [
  { start: 1.0, end: 2.6, to: 0.2 },
  { start: 2.7, end: 4.3, to: 0.42 },
  { start: 4.4, end: 6.2, to: 0.62 },
] as const;
export const SCRIPT_RETURN = [7.0, 8.6] as const;

/** A word takes this many words of travel to ink in: the soft leading edge. */
export const FADE_WORDS = 4;
export const WORDS_PER_MINUTE = 140;

export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}

export function mix(a: number, b: number, k: number) {
  'worklet';
  return a + (b - a) * k;
}

export function easeOutCubic(k: number) {
  'worklet';
  return 1 - (1 - k) ** 3;
}

export function easeInOutCubic(k: number) {
  'worklet';
  return k < 0.5 ? 4 * k * k * k : 1 - (-2 * k + 2) ** 3 / 2;
}

/** Share of the scroll range (0..1) at loop time t. */
export function scriptAt(t: number) {
  'worklet';
  let at = 0;
  for (let i = 0; i < FLICKS.length; i += 1) {
    at = mix(at, FLICKS[i].to, easeOutCubic(seg(t, FLICKS[i].start, FLICKS[i].end)));
  }
  return at * (1 - easeInOutCubic(seg(t, SCRIPT_RETURN[0], SCRIPT_RETURN[1])));
}

export function ghostOpacityAt(t: number) {
  'worklet';
  let opacity = 0;
  for (let i = 0; i < FLICKS.length; i += 1) {
    const start = FLICKS[i].start;
    opacity = Math.max(opacity, seg(t, start - 0.2, start - 0.04) * (1 - seg(t, start + 0.34, start + 0.5)));
  }
  return opacity;
}

/** The ghost finger's travel, 0 (low on the screen) to 1 (high), during each flick. */
export function ghostTravelAt(t: number) {
  'worklet';
  let travel = 0;
  for (let i = 0; i < FLICKS.length; i += 1) {
    const start = FLICKS[i].start;
    if (t >= start - 0.2 && t <= start + 0.5) travel = easeOutCubic(seg(t, start, start + 0.4));
  }
  return travel;
}

/** Index of the last line whose top is at or above y, or -1. */
export function lineAt(y: number, tops: number[]) {
  'worklet';
  let low = 0;
  let high = tops.length - 1;
  let found = -1;
  while (low <= high) {
    const middle = (low + high) >> 1;
    if (tops[middle] <= y) {
      found = middle;
      low = middle + 1;
    } else {
      high = middle - 1;
    }
  }
  return found;
}

/** The reading front in words (fractional), from the article's y of the reading line. */
export function frontAt(y: number, tops: number[], heights: number[], firsts: number[], counts: number[]) {
  'worklet';
  const line = lineAt(y, tops);
  if (line < 0) return 0;
  const along = Math.min(Math.max((y - tops[line]) / heights[line], 0), 1);
  return firsts[line] + along * counts[line];
}

export function wordInk(front: number, word: number) {
  'worklet';
  const k = Math.min(Math.max((front - word) / FADE_WORDS, 0), 1);
  return k * k * (3 - 2 * k);
}
