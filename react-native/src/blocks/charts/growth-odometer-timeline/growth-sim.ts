// One fixed substep owns event ordering, wheel/drum integration and the periodic reset.
import { clamp, smooth, valueAt } from './growth-math';
import { drumTarget, drumStep } from './growth-drums';
import { type GrowthState, initialState, copyState } from './growth-state';
import { goTo, wheelStep } from './growth-wheel';
import { autoStep } from './growth-autoplay';
import {
  LAST_MONTH,
  CHEVRON_IDLE_HZ,
  CHEVRON_CYCLES_PER_MONTH,
  AUTO_RESUME_DELAY_SECONDS,
  HERO_REARM_MONTH,
  HERO_TRIGGER_MONTH,
  HAPTIC_INTERVAL_SECONDS,
  END_IMPACT_SPEED,
  GRIP_ATTACK_SECONDS,
  GRIP_BEZIER_X_COEFFICIENT,
  GRIP_BEZIER_Y_COEFFICIENT,
  GRIP_BEZIER_DERIVATIVE_CROSS,
  GRIP_BEZIER_DERIVATIVE_SQUARED,
  GRIP_DERIVATIVE_FLOOR,
  GRIP_RELEASE_STIFFNESS,
  GRIP_RELEASE_DAMPING,
  PRESS_STIFFNESS,
  PRESS_DAMPING,
  REDUCED_SWAP_SECONDS,
  FINAL_FOLLOWERS,
  INITIAL_FOLLOWERS,
  FOLLOWER_RANGE,
  REDUCED_HALO_FADE_SECONDS,
  HALO_TIME_CONSTANT_SECONDS,
  HALO_REST_EPSILON,
  AUTO_PERIOD_STEPS,
  MAX_FRAME_DELTA_SECONDS,
  STEP_ACCUMULATOR_EPSILON,
  DT
} from './growth-physics';
export { clamp, smooth, valueAt } from './growth-math';
export { type Drum, drumTarget, drumStep } from './growth-drums';
export { type GrowthState, initialState, copyState } from './growth-state';
export { rubber, inverseRubber, goTo, wheelStep } from './growth-wheel';
export { chevronPhase, autoStep } from './growth-autoplay';
export { touch, drag, release, nextMonth } from './growth-interaction';
export { heroEnvelope, visuals } from './growth-visuals';
export { DT } from './growth-physics';
export function substep(wheel: GrowthState) {
  'worklet';
  wheel.steps++;
  wheel.clock = wheel.steps * DT;
  const playing = wheel.auto && !wheel.paused;
  if (playing) {
    wheel.loopStep++;
    autoStep(wheel);
  }
  wheelStep(wheel);
  if (wheel.auto &&
    wheel.paused &&
    !wheel.down &&
    wheel.mode === 'idle' &&
    wheel.clock - wheel.lastTouch >= AUTO_RESUME_DELAY_SECONDS) {
    if (!wheel.returning) {
      wheel.returning = true;
      goTo(wheel, 0);
    } else {
      wheel.paused = false;
      wheel.returning = false;
      wheel.loopStep = 0;
    }
  }
  if (wheel.p < HERO_REARM_MONTH) wheel.armed = true;
  const hero = wheel.armed && wheel.p >= HERO_TRIGGER_MONTH;
  if (hero) {
    wheel.heroStart = wheel.clock;
    wheel.armed = false;
    if (!playing) wheel.events |= 4;
  }
  const month = clamp(Math.round(wheel.p), 0, LAST_MONTH);
  if (month !== wheel.month) {
    wheel.month = month;
    wheel.tickMonth = month;
    wheel.tickStart = wheel.clock;
    if (!playing &&
      !hero &&
      month !== LAST_MONTH &&
      wheel.clock - wheel.lastHaptic >= HAPTIC_INTERVAL_SECONDS) {
      wheel.events |= 1;
      wheel.lastHaptic = wheel.clock;
    }
  }
  const end = wheel.p > LAST_MONTH ? 1 : wheel.p < 0 ? -1 : 0;
  if (end &&
    end !== wheel.endLatch &&
    wheel.vel * end > END_IMPACT_SPEED &&
    !playing &&
    !hero) wheel.events |= 2;
  wheel.endLatch = end;
  if (wheel.mode === 'idle' && wheel.announced !== month) {
    wheel.announced = month;
    wheel.events |= 8;
  }
  if (wheel.down) {
    // Cubic-bezier (0.23,1,0.32,1), solve its x parameter for the simulation's own clock.
    const value = clamp((wheel.clock - wheel.touchStart) / GRIP_ATTACK_SECONDS, 0, 1);
    let progress = value;
    for (let index = 0; index < 5; index++) {
      const remaining = 1 - progress;
      const bezierX = GRIP_BEZIER_X_COEFFICIENT * remaining * remaining * progress
        + GRIP_BEZIER_Y_COEFFICIENT * remaining * progress * progress + progress * progress * progress;
      const derivativeX = GRIP_BEZIER_X_COEFFICIENT * remaining * remaining
        + GRIP_BEZIER_DERIVATIVE_CROSS * remaining * progress
        + GRIP_BEZIER_DERIVATIVE_SQUARED * progress * progress;
      progress = clamp(progress - (bezierX - value) / Math.max(GRIP_DERIVATIVE_FLOOR, derivativeX), 0, 1);
    }
    wheel.grip = wheel.gripFrom + (1 - wheel.gripFrom) * (1 - Math.pow(1 - progress, 3));
    wheel.gripVel = 0;
  } else {
    wheel.gripVel += (-GRIP_RELEASE_STIFFNESS * wheel.grip - GRIP_RELEASE_DAMPING * wheel.gripVel) * DT;
    wheel.grip += wheel.gripVel * DT;
  }
  wheel.pressVel += (PRESS_STIFFNESS * ((wheel.pressed ?
    1 : 0) - wheel.press) - PRESS_DAMPING * wheel.pressVel) * DT;
  wheel.press += wheel.pressVel * DT;
  if (!wheel.reduced) wheel.phase += (CHEVRON_IDLE_HZ + CHEVRON_CYCLES_PER_MONTH * wheel.vel) * DT;
  if (wheel.reduced && month !== wheel.reducedMonth) {
    wheel.reducedMonth = month;
    wheel.fadeStart = wheel.clock;
    wheel.haloFrom = wheel.halo;
  }
  if (wheel.reduced &&
    wheel.clock - wheel.fadeStart >= REDUCED_SWAP_SECONDS) wheel.displayMonth = wheel.reducedMonth;
  const value = valueAt(wheel.reduced ? wheel.displayMonth : wheel.p);
  // Once the final detent has crossed, its wheel rebound must not start another
  // carry. A touch/rewind releases this latch immediately; the wheel is unchanged.
  const landing = !wheel.down &&
    !wheel.armed &&
    wheel.target === LAST_MONTH &&
    (wheel.mode === 'goto' ||
      wheel.mode === 'idle');
  const drumValue = landing && !wheel.reduced ? FINAL_FOLLOWERS : value;
  for (let drumIndex = 0; drumIndex < 5; drumIndex++) {
    const target = drumTarget(drumValue, drumIndex);
    const drum = wheel.drums[drumIndex];
    if (wheel.reduced) {
      drum.theta = target;
      drum.omega = 0;
      drum.previous = target;
    } else drumStep(drum, target, drumIndex, landing);
  }
  const haloLevel = ((wheel.reduced ?
    valueAt(wheel.reducedMonth) : value) - INITIAL_FOLLOWERS) / FOLLOWER_RANGE;
  if (wheel.reduced) {
    wheel.halo = wheel.haloFrom + (haloLevel - wheel.haloFrom)
      * smooth(0, REDUCED_HALO_FADE_SECONDS, wheel.clock - wheel.fadeStart);
  } else {
    wheel.halo += (haloLevel - wheel.halo) * (1 - Math.exp(-DT / HALO_TIME_CONSTANT_SECONDS));
  }
  if (haloLevel === 0 && wheel.halo < HALO_REST_EPSILON) wheel.halo = 0;
  // Reset only the local film state every 2880 steps (12 s); retain the absolute event clock.
  // Halo, drums and chevrons all restart from the same state, so autoPlay has no accumulating drift.
  if (playing && wheel.loopStep >= AUTO_PERIOD_STEPS) {
    const clean = initialState(true, false);
    clean.steps = wheel.steps;
    clean.clock = wheel.clock;
    clean.accumulator = wheel.accumulator;
    clean.announced = wheel.announced;
    clean.events = wheel.events;
    Object.assign(wheel, clean);
  }
}

export function advance(previousState: GrowthState, frameDelta: number) {
  'worklet';
  const wheel = copyState(previousState);
  wheel.events = 0;
  wheel.accumulator += clamp(frameDelta, 0, MAX_FRAME_DELTA_SECONDS);
  while (wheel.accumulator >= DT - STEP_ACCUMULATOR_EPSILON) {
    wheel.accumulator -= DT;
    substep(wheel);
  }
  return wheel;
}
