export const LOOP = 5;
export const PEAK_HOLD = 0.22;

export type TrophyMotionState = {
  mode: 'idle' | 'loop' | 'drag' | 'event' | 'waiting' | 'paused';
  generation: number;
  progress: number;
  pose: number;
  epoch: number;
  direction: number;
  contactOrigin: number;
  contactPoseOrigin: number;
  contactDirection: number;
  contactVelocity: number;
  translationOrigin: number;
  eventOrigin: number;
  eventPoseOrigin: number;
  eventStarted: number;
  eventDemo: boolean;
  eventVelocity: number;
  eventUserPeak: boolean;
  hapticSent: boolean;
};

export function clamp(value: number, low = 0, high = 1) {
  'worklet';
  return Math.min(high, Math.max(low, value));
}

export function smooth(value: number) {
  'worklet';
  const x = clamp(value);
  return x * x * x * (10 - 15 * x + 6 * x * x);
}

export function openDuration(origin: number) {
  'worklet';
  return Math.max(0.08, 0.58 * (1 - clamp(origin)));
}

export function closeDuration(origin: number) {
  'worklet';
  return 0.18 + 0.30 * clamp(origin);
}

export function closeProgress(origin: number, age: number, velocity = 0) {
  'worklet';
  const p = clamp(origin);
  const duration = closeDuration(p);
  if (age >= duration) return 0;
  const u = clamp(age / duration);
  // Winding clutch arrests outward travel. Inward momentum is carried by a monotone bounded Hermite return.
  const v = clamp(velocity, -3 * p / duration, 0);
  return (2 * u * u * u - 3 * u * u + 1) * p + (u * u * u - 2 * u * u + u) * duration * v;
}

export function demoProgress(origin: number, age: number) {
  'worklet';
  const p = clamp(origin);
  const opening = openDuration(p);
  if (age < opening) return p + (1 - p) * smooth(age / opening);
  if (age < opening + PEAK_HOLD) return 1;
  return closeProgress(1, age - opening - PEAK_HOLD);
}

export function loopProgress(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  if (t < 0.6) return 0;
  if (t < 1.7) return smooth((t - 0.6) / 1.1);
  if (t < 2.6) return 1;
  if (t < 4.4) return 1 - smooth((t - 2.6) / 1.8);
  return 0;
}

export function loopPose(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  const p = loopProgress(t);
  return t < 2.6 ? p : 2 - p;
}

export function makeMotion(generation: number, epoch: number, mode: TrophyMotionState['mode'] = 'idle'): TrophyMotionState {
  'worklet';
  return {
    mode, generation, epoch, progress: 0, pose: 0, direction: 0,
    contactOrigin: 0, contactPoseOrigin: 0, contactDirection: 0, contactVelocity: 0, translationOrigin: 0,
    eventOrigin: 0, eventPoseOrigin: 0, eventStarted: 0, eventDemo: false, eventVelocity: 0, eventUserPeak: false, hapticSent: false,
  };
}

export function presentedPose(state: TrophyMotionState, now: number) {
  'worklet';
  if (state.mode === 'loop') return loopPose(now - state.epoch);
  if (state.mode === 'event') {
    const age = Math.max(0, now - state.eventStarted);
    const origin = state.eventPoseOrigin ?? state.eventOrigin;
    if (state.eventDemo) {
      const opening = openDuration(state.eventOrigin);
      if (age < opening) return origin + (1 - origin) * smooth(age / opening);
      if (age < opening + PEAK_HOLD) return 1;
      return 2 - closeProgress(1, age - opening - PEAK_HOLD);
    }
    const p = closeProgress(state.eventOrigin, age, state.eventVelocity);
    return origin >= 1 ? 2 - p : p;
  }
  return state.pose ?? state.progress;
}

export function presentedProgress(state: TrophyMotionState, now: number) {
  'worklet';
  const pose = presentedPose(state, now);
  return pose <= 1 ? pose : 2 - pose;
}

export function peakTime(state: TrophyMotionState) {
  'worklet';
  return state.eventDemo ? openDuration(state.eventOrigin) : 0;
}

export function endTime(state: TrophyMotionState) {
  'worklet';
  return state.eventDemo ? peakTime(state) + PEAK_HOLD + closeDuration(1) : closeDuration(state.eventOrigin);
}

export function beginEvent(state: TrophyMotionState, progress: number, demo: boolean, userPeak: boolean, now: number, velocity = 0): TrophyMotionState {
  'worklet';
  const p = clamp(progress);
  const currentPose = presentedPose(state, now);
  const pose = p <= 0.000001 && currentPose >= 1.999999 ? 0 : currentPose;
  return {
    ...state,
    mode: 'event', generation: state.generation + 1, progress: p, pose,
    eventOrigin: p, eventPoseOrigin: pose, eventStarted: now, eventDemo: demo, eventVelocity: demo ? 0 : velocity,
    eventUserPeak: userPeak && (demo || p >= 0.985), hapticSent: false,
  };
}
