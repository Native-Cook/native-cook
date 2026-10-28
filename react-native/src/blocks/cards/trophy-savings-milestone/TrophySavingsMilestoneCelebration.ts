import { clamp, loopProgress } from './TrophySavingsMilestoneMotion';

// Shared UI-thread scene contract. All starts use this same event-local clock, in seconds.
export type TrophyApplauseFrame = {
  clock: number;
  progress: number;
  wakeStarts: number[];
  contactStart: number;
  generation: number;
  reduced: boolean;
  active: boolean;
  cadence?: number;
};

export const TROPHY_WAKE_THRESHOLDS = [0.12, 0.23, 0.08, 0.18, 0.76, 0.91, 0.28, 0.4, 0.2, 0.62, 0.74, 0.54, 0.11, 0.83, 0.04, 0.84, 0.29, 0.16];
export const TROPHY_APPLAUSE_SETTLE = 0.96;

export function restApplause(generation = 0, reduced = false): TrophyApplauseFrame {
  'worklet';
  return { clock: 0, progress: 0, wakeStarts: Array(18).fill(-1), contactStart: -1, generation, reduced, active: false, cadence: 1 };
}

export function applauseIsSettled(scene: TrophyApplauseFrame) {
  'worklet';
  if (!scene.active || scene.reduced) return true;
  if (scene.progress > 0.000001) return false;
  let last = scene.contactStart;
  for (const start of scene.wakeStarts) last = Math.max(last, start);
  return last < 0 || scene.clock >= last + TROPHY_APPLAUSE_SETTLE * (scene.cadence ?? 1);
}

function thresholdTime(threshold: number) {
  'worklet';
  // Inverse of the same monotone gallery feed, solved once per scene evaluation.
  let low = 0.6;
  let high = 1.7;
  for (let index = 0; index < 15; index += 1) {
    const mid = (low + high) * 0.5;
    if (loopProgress(mid) < threshold) low = mid;
    else high = mid;
  }
  return (low + high) * 0.5;
}

const LOOP_WAKE_STARTS = TROPHY_WAKE_THRESHOLDS.map(thresholdTime);

export function galleryApplause(time: number, generation: number, reduced = false): TrophyApplauseFrame {
  'worklet';
  if (reduced) return restApplause(generation, true);
  const clock = ((time % 5) + 5) % 5;
  const progress = loopProgress(clock);
  return { clock, progress, wakeStarts: LOOP_WAKE_STARTS.map(start => clock >= start ? start : -1),
    contactStart: clock >= 1.53 ? 1.53 : -1, generation, reduced: false, active: clock >= 0.6 && clock < 4.4, cadence: 2.4 };
}

export function advanceApplause(previous: TrophyApplauseFrame, clock: number, progress: number, generation: number): TrophyApplauseFrame {
  'worklet';
  const p = clamp(progress);
  const forward = p > previous.progress + 0.000001;
  let wakeStarts = previous.wakeStarts;
  if (forward) for (let index = 0; index < 18; index += 1) {
    if (wakeStarts[index] < 0 && p >= TROPHY_WAKE_THRESHOLDS[index]) {
      if (wakeStarts === previous.wakeStarts) wakeStarts = previous.wakeStarts.slice();
      wakeStarts[index] = clock;
    }
  }
  return { ...previous, clock, progress: p, generation, wakeStarts,
    contactStart: previous.contactStart < 0 && forward && p >= 0.88 ? clock : previous.contactStart,
    reduced: false, active: true, cadence: previous.cadence ?? 1 };
}
