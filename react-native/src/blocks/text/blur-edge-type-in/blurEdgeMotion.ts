/** One letter of BlurEdgeTypeIn, shared so other blocks can type and erase text the same way. */

/** Characters behind the moving front that are still blurred. */
export const BLUR_EDGE = 4;
/** In ems of the text size. */
export const BLUR_EDGE_MAX_BLUR = 0.16;
export const BLUR_EDGE_MAX_SHIFT = 0.3;

/**
 * `d` is how far the front has passed the letter, in characters. A letter is not drawn at d <= 0;
 * for 0 < d < BLUR_EDGE it fades in (`opacity`) while blur and shift shrink with `k` = (1 - u)²;
 * from d >= BLUR_EDGE it is sharp and in place.
 */
export function blurEdgeLetter(d: number) {
  'worklet';
  if (d <= 0) return { opacity: 0, k: 1 };
  const u = Math.max(0, Math.min(1, d / BLUR_EDGE));
  return { opacity: u, k: (1 - u) * (1 - u) };
}
