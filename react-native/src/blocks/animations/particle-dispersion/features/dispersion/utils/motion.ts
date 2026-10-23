import {
  DUST_END_SECONDS,
  SUBJECT_MOTION_RATE,
  SUBJECT_REFERENCE_ANCHOR_SECONDS,
} from '../constants';

function clamp01Worklet(value: number) {
  'worklet';
  return Math.min(Math.max(value, 0), 1);
}

export function smoothstepWorklet(value: number) {
  'worklet';
  const bounded = clamp01Worklet(value);
  return bounded * bounded * (3 - 2 * bounded);
}

export function easeOutCubicWorklet(value: number) {
  'worklet';
  const bounded = clamp01Worklet(value);
  return 1 - Math.pow(1 - bounded, 3);
}

export function subjectTimeSecondsWorklet(globalTimeSeconds: number) {
  'worklet';

  if (globalTimeSeconds <= SUBJECT_REFERENCE_ANCHOR_SECONDS) {
    return globalTimeSeconds;
  }

  return (
    SUBJECT_REFERENCE_ANCHOR_SECONDS +
    Math.max(globalTimeSeconds - DUST_END_SECONDS, 0) * SUBJECT_MOTION_RATE
  );
}
