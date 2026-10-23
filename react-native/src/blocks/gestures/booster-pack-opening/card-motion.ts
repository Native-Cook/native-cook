import {
  CARD_KEYFRAMES,
  POST_CUT_DURATION_MS,
  REFERENCE_LAYOUT,
  clamp,
  interpolateKeyframes,
} from './model';

export const CARD_CENTERING_DURATION_MS = 650;
const TIMES = CARD_KEYFRAMES.map(({ ms }) => ms);
const TOPS = CARD_KEYFRAMES.map(({ top }) => top);
const CENTERED_TOP = (1 - REFERENCE_LAYOUT.card.height) / 2;
const LAST = CARD_KEYFRAMES[CARD_KEYFRAMES.length - 1];
const PREVIOUS = CARD_KEYFRAMES[CARD_KEYFRAMES.length - 2];
const REVEAL_VELOCITY = (LAST.top - PREVIOUS.top) / (LAST.ms - PREVIOUS.ms);

/** Normalized top; preserve the measured reveal, then ease into the scene center. */
export function cardTopAtTime(ms: number, reducedMotion = false) {
  'worklet';
  // A fade at the destination replaces the upward travel for reduced motion.
  if (reducedMotion) return CENTERED_TOP;
  if (ms <= POST_CUT_DURATION_MS) {
    return interpolateKeyframes(ms, TIMES, TOPS);
  }

  const t = clamp((ms - POST_CUT_DURATION_MS) / CARD_CENTERING_DURATION_MS, 0, 1);
  // Hermite interpolation carries the reveal's final velocity into this move,
  // then settles with zero velocity, avoiding a stop at the reference video's endpoint.
  const t2 = t * t;
  const t3 = t2 * t;
  return (2 * t3 - 3 * t2 + 1) * LAST.top +
    (t3 - 2 * t2 + t) * REVEAL_VELOCITY * CARD_CENTERING_DURATION_MS +
    (-2 * t3 + 3 * t2) * CENTERED_TOP;
}
