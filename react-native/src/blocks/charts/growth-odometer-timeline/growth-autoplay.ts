// An integer 240 Hz loop counter closes the 12-second cycle without clock drift.
import { smooth } from './growth-math';
import { type GrowthState } from './growth-state';
import { goTo } from './growth-wheel';
import {
  LAST_MONTH,
  AUTO_START_SECONDS,
  AUTO_CAPTURE_SECONDS,
  AUTO_RAMP_SECONDS,
  AUTO_SPEED_MONTHS_PER_SECOND,
  AUTO_RAMP_DENOMINATOR_SECONDS,
  AUTO_RAMP_OFFSET_SECONDS,
  AUTO_CAPTURE_MONTH,
  AUTO_DETENT_RIPPLE,
  AUTO_REWIND_SECONDS,
  AUTO_REWIND_STEP,
  CHEVRON_IDLE_HZ,
  CHEVRON_CYCLES_PER_MONTH,
  CHEVRON_SEAM_CORRECTION_CYCLES,
  AUTO_REWIND_FINISH_SECONDS,
  FULL_TURN_RADIANS,
  DT
} from './growth-physics';
export function chevronPhase(wheel: GrowthState) {
  'worklet';
  const time = wheel.loopStep * DT;
  // Close 0.2 cycle during rewind, preserving 0.9 Hz idle motion at the loop seam.
  if (wheel.reduced) return 0;
  if (!wheel.auto || wheel.paused) return wheel.phase;
  return CHEVRON_IDLE_HZ * time + CHEVRON_CYCLES_PER_MONTH * wheel.p
    + CHEVRON_SEAM_CORRECTION_CYCLES * smooth(AUTO_REWIND_SECONDS, AUTO_REWIND_FINISH_SECONDS, time);
}

export function autoStep(wheel: GrowthState) {
  'worklet';
  const time = wheel.loopStep * DT;
  if (time < AUTO_START_SECONDS) {
    wheel.p = 0;
    wheel.vel = 0;
    wheel.mode = 'auto';
  } else if (time < AUTO_CAPTURE_SECONDS) {
    const elapsed = time - AUTO_START_SECONDS;
    const raw = elapsed < AUTO_RAMP_SECONDS
      ? AUTO_SPEED_MONTHS_PER_SECOND * elapsed * elapsed / AUTO_RAMP_DENOMINATOR_SECONDS
      : AUTO_SPEED_MONTHS_PER_SECOND * (elapsed - AUTO_RAMP_OFFSET_SECONDS);
    if (raw < AUTO_CAPTURE_MONTH) {
      wheel.p = raw - AUTO_DETENT_RIPPLE * Math.sin(FULL_TURN_RADIANS * raw) / FULL_TURN_RADIANS;
      wheel.vel = Math.min(AUTO_SPEED_MONTHS_PER_SECOND,
        AUTO_SPEED_MONTHS_PER_SECOND * elapsed / AUTO_RAMP_SECONDS)
        * (1 - AUTO_DETENT_RIPPLE * Math.cos(FULL_TURN_RADIANS * raw));
      wheel.mode = 'auto';
    } else if (wheel.mode === 'auto') goTo(wheel, LAST_MONTH);
  }
  if (time >= AUTO_CAPTURE_SECONDS &&
    time < AUTO_REWIND_SECONDS &&
    wheel.mode === 'auto') goTo(wheel,
      LAST_MONTH);
  if (wheel.loopStep === AUTO_REWIND_STEP) goTo(wheel, 0);
}
