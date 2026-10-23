// Scalar interpolation shared by physics and drawing. Positions are measured in months.
import { VALUES } from './growth-theme';

const LAST_VALUE_INDEX = 12;
const LAST_INTERVAL_INDEX = 11;
export function clamp(value: number, lower: number, upper: number) {
  'worklet';
  return Math.max(lower, Math.min(upper, value));
}

export function smooth(lower: number, upper: number, value: number) {
  'worklet';
  const progress = clamp((value - lower) / (upper - lower), 0, 1);
  return progress * progress * (3 - 2 * progress);
}

export function valueAt(position: number) {
  'worklet';
  const scaledPosition = clamp(position, 0, LAST_VALUE_INDEX);
  const index = Math.min(LAST_INTERVAL_INDEX, Math.floor(scaledPosition));
  return VALUES[index] + (VALUES[index + 1] - VALUES[index]) * (scaledPosition - index);
}
