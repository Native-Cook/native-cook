// Pure gesture transitions; simultaneous pan finalization preserves a queued knob target.
import { clamp } from './growth-math';
import { type GrowthState, copyState } from './growth-state';
import { rubber, inverseRubber, goTo } from './growth-wheel';
import { chevronPhase } from './growth-autoplay';
import { MONTH_SPACING, WHEEL_MAX_SPEED } from './growth-physics';
export function touch(previousState: GrowthState) {
  'worklet';
  const wheel = copyState(previousState);
  wheel.phase = chevronPhase(wheel);
  wheel.grabTarget = wheel.mode === 'goto' ? wheel.target : Math.round(wheel.p);
  wheel.paused = true;
  wheel.returning = false;
  wheel.lastTouch = wheel.clock;
  wheel.raw0 = inverseRubber(wheel.p);
  wheel.mode = 'held';
  wheel.vel = 0;
  wheel.down = true;
  wheel.touchStart = wheel.clock;
  wheel.gripFrom = wheel.grip;
  return wheel;
}

export function drag(previousState: GrowthState, translationY: number, velocityY = 0) {
  'worklet';
  const wheel = copyState(previousState);
  wheel.p = rubber(wheel.raw0 - translationY / MONTH_SPACING);
  wheel.vel = clamp(-velocityY / MONTH_SPACING, -WHEEL_MAX_SPEED, WHEEL_MAX_SPEED);
  wheel.mode = 'drag';
  wheel.lastTouch = wheel.clock;
  return wheel;
}

export function release(previousState: GrowthState, velocityY: number) {
  'worklet';
  const wheel = copyState(previousState);
  wheel.down = false;
  wheel.lastTouch = wheel.clock;
  // A simultaneous tap may have already set goto. Finalization must not overwrite it.
  if (wheel.mode === 'drag' || wheel.mode === 'held') {
    wheel.vel = wheel.mode === 'held' ?
      0 : clamp(-velocityY / MONTH_SPACING,
        -WHEEL_MAX_SPEED,
        WHEEL_MAX_SPEED);
    if (wheel.reduced) goTo(wheel, Math.round(wheel.p)); else wheel.mode = 'coast';
  }
  return wheel;
}

export function nextMonth(previousState: GrowthState,
  direction: number,
  rewind: boolean,
  fromGrab = false) {
  'worklet';
  const wheel = copyState(previousState);
  wheel.phase = chevronPhase(wheel);
  const from = fromGrab ||
    wheel.down ?
    wheel.grabTarget : wheel.mode === 'goto' ?
      wheel.target : Math.round(wheel.p);
  wheel.paused = true;
  wheel.returning = false;
  wheel.lastTouch = wheel.clock;
  goTo(wheel, rewind && from >= 12 ? 0 : from + direction);
  return wheel;
}
