// A detent is the magnetic resting notch at each integer month on the wheel.
import { clamp, smooth } from './growth-math';
import { type GrowthState } from './growth-state';
import {
  RUBBER_LIMIT_MONTHS,
  RUBBER_RESISTANCE,
  LAST_MONTH,
  DETENT_DRAG_STRENGTH,
  INVERSE_RUBBER_LIMIT_MONTHS,
  INVERSE_NEWTON_ITERATIONS,
  WHEEL_MAX_SPEED,
  REDUCED_STEP_DURATION_SECONDS,
  GOTO_STIFFNESS,
  GOTO_DAMPING,
  COAST_BASE_DAMPING,
  COAST_CAPTURE_DAMPING,
  COAST_SLOW_SPEED,
  COAST_FAST_SPEED,
  DETENT_STIFFNESS,
  DETENT_SPEED_RELIEF,
  DETENT_SLOW_SPEED,
  DETENT_FAST_SPEED,
  END_STOP_STIFFNESS,
  END_STOP_DAMPING,
  GOTO_MAX_SPEED,
  SETTLE_SPEED,
  SETTLE_POSITION,
  FULL_TURN_RADIANS,
  DT
} from './growth-physics';
export function rubber(raw: number) {
  'worklet';
  if (raw < 0) return -RUBBER_LIMIT_MONTHS * (1 - 1 / (-raw * RUBBER_RESISTANCE / RUBBER_LIMIT_MONTHS + 1));
  if (raw > LAST_MONTH) {
    return LAST_MONTH + RUBBER_LIMIT_MONTHS
      * (1 - 1 / ((raw - LAST_MONTH) * RUBBER_RESISTANCE / RUBBER_LIMIT_MONTHS + 1));
  }
  return raw - DETENT_DRAG_STRENGTH * Math.sin(FULL_TURN_RADIANS * raw) / FULL_TURN_RADIANS;
}

export function inverseRubber(position: number) {
  'worklet';
  if (position < 0 || position > LAST_MONTH) {
    const excess = Math.min(INVERSE_RUBBER_LIMIT_MONTHS,
      position < 0 ?
        -position : position - LAST_MONTH);
    const raw = excess / (RUBBER_RESISTANCE * (1 - excess / RUBBER_LIMIT_MONTHS));
    return position < 0 ? -raw : LAST_MONTH + raw;
  }
  let raw = position;
  for (let index = 0; index < INVERSE_NEWTON_ITERATIONS; index++) {
    raw -= (rubber(raw) - position) / (1 - DETENT_DRAG_STRENGTH * Math.cos(FULL_TURN_RADIANS * raw));
  }
  return raw;
}

export function goTo(wheel: GrowthState, target: number) {
  'worklet';
  wheel.target = clamp(target, 0, LAST_MONTH);
  wheel.mode = 'goto';
  wheel.reducedFrom = wheel.p;
  wheel.reducedStart = wheel.clock;
}

export function wheelStep(wheel: GrowthState) {
  'worklet';
  if (wheel.mode !== 'goto' && wheel.mode !== 'coast') return;
  if (wheel.reduced) {
    const progress = smooth(0, REDUCED_STEP_DURATION_SECONDS, wheel.clock - wheel.reducedStart);
    wheel.p = wheel.reducedFrom + (wheel.target - wheel.reducedFrom) * progress;
    wheel.vel = 0;
    if (progress === 1) wheel.mode = 'idle';
    return;
  }
  let acceleration = 0;
  if (wheel.mode === 'goto') {
    acceleration = -GOTO_STIFFNESS * (wheel.p - wheel.target) - GOTO_DAMPING * wheel.vel;
  } else {
    const speed = Math.abs(wheel.vel);
    const damping = COAST_BASE_DAMPING + COAST_CAPTURE_DAMPING * (1 - smooth(COAST_SLOW_SPEED,
      COAST_FAST_SPEED,
      speed));
    const detentStrength = DETENT_STIFFNESS * (1 - DETENT_SPEED_RELIEF * smooth(DETENT_SLOW_SPEED,
      DETENT_FAST_SPEED,
      speed));
    acceleration = -damping * wheel.vel
      - detentStrength * Math.sin(FULL_TURN_RADIANS * wheel.p) / FULL_TURN_RADIANS;
    if (wheel.p > LAST_MONTH) acceleration += -END_STOP_STIFFNESS * Math.min(1,
      wheel.p - LAST_MONTH) - END_STOP_DAMPING * wheel.vel;
    if (wheel.p < 0) acceleration += -END_STOP_STIFFNESS * Math.max(-1,
      wheel.p) - END_STOP_DAMPING * wheel.vel;
  }
  wheel.vel = clamp(wheel.vel + acceleration * DT,
    wheel.mode === 'goto' ?
      -GOTO_MAX_SPEED : -WHEEL_MAX_SPEED,
    wheel.mode === 'goto' ?
      GOTO_MAX_SPEED : WHEEL_MAX_SPEED);
  wheel.p += wheel.vel * DT;
  const stop = wheel.mode === 'goto' ? wheel.target : clamp(Math.round(wheel.p), 0, LAST_MONTH);
  if (Math.abs(wheel.vel) < SETTLE_SPEED && Math.abs(wheel.p - stop) < SETTLE_POSITION) {
    wheel.p = stop;
    wheel.vel = 0;
    wheel.mode = 'idle';
  }
}
