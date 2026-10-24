import { flow, type FlowKey } from './motion';
import { SIGNATURE, signatureClamp, signatureEase } from './BuzzStreaksSignature';

// RN's expressive Signature revision. The accepted source profile and shared v2
// contract remain available; this choreography uses the same scene clock.
const REWARD_TIME: readonly FlowKey[] = [[0, 0], [2.58, 2.58], [2.9, 2.58], [3.4, 3.4], [10.699, 10.699]];
const PULL: readonly FlowKey[] = [[2.98, 0], [3.24, 1], [3.36, 1], [3.59, -.12], [3.68, 0]];

/** Signal arrives before reward creation; the local source clock rejoins at3.4s. */
export function heroRewardTime(time: number, enabled: boolean) {
  'worklet';
  if (!enabled || time <= 2.58 || time >= 3.4) return time;
  return flow(time, REWARD_TIME);
}

/** A visible outward pull, short weight-bearing hold and one controlled settle. */
export function heroRewardPull(time: number, enabled: boolean) {
  'worklet';
  return enabled ? flow(time, PULL) : 0;
}

export function heroDayOneBadge(time: number, enabled: boolean) {
  'worklet';
  return enabled ? signatureEase(signatureClamp((time - 3.34) / .21), SIGNATURE.easing.contact) : 1;
}

/** Translation-only images, with zero velocity at contact and at rest. */
export function heroDomino(time: number, day: number, enabled: boolean) {
  'worklet';
  if (!enabled || day < 5 || day > 7) return 0;
  const start = 8.82 + (day - 5) * .1;
  if (time <= start || time >= start + .34) return 0;
  const wave = Math.sin(Math.PI * (time - start) / .34);
  return wave * wave;
}
