// Serializable UI-thread state. p is months; vel is months/second; clocks are seconds.
import { type Drum, drumTarget } from './growth-drums';
import { INITIAL_FOLLOWERS } from './growth-physics';

const INACTIVE_TIME_SECONDS = -100;
export type GrowthState = {
  p: number;
  vel: number;
  mode: 'idle' | 'held' | 'drag' | 'coast' | 'goto' | 'auto';
  target: number;
  drums: Drum[];
  clock: number;
  steps: number;
  accumulator: number;
  loopStep: number;
  auto: boolean;
  reduced: boolean;
  paused: boolean;
  returning: boolean;
  lastTouch: number;
  raw0: number;
  grabTarget: number;
  grip: number;
  gripVel: number;
  down: boolean;
  pressed: boolean;
  press: number;
  pressVel: number;
  touchStart: number;
  gripFrom: number;
  halo: number;
  phase: number;
  heroStart: number;
  armed: boolean;
  tickStart: number;
  tickMonth: number;
  month: number;
  announced: number;
  events: number;
  lastHaptic: number;
  endLatch: number;
  reducedFrom: number;
  reducedStart: number;
  displayMonth: number;
  reducedMonth: number;
  fadeStart: number;
  haloFrom: number;
};

export function initialState(auto: boolean, reduced: boolean): GrowthState {
  'worklet';
  const drums: Drum[] = [];
  for (let drumIndex = 0; drumIndex < 5; drumIndex++) {
    const target = drumTarget(INITIAL_FOLLOWERS, drumIndex);
    drums.push({
      theta: target,
      omega: 0,
      rate: 0,
      dir: 1,
      previous: target,
      wraps: 0,
      spinning: false,
      runout: -1
    });
  }
  return {
    p: 0,
    vel: 0,
    mode: 'idle',
    target: 0,
    drums,
    clock: 0,
    steps: 0,
    accumulator: 0,
    loopStep: 0,
    auto: auto && !reduced,
    reduced,
    paused: false,
    returning: false,
    lastTouch: INACTIVE_TIME_SECONDS,
    raw0: 0,
    grabTarget: 0,
    grip: 0,
    gripVel: 0,
    down: false,
    pressed: false,
    press: 0,
    pressVel: 0,
    touchStart: 0,
    gripFrom: 0,
    halo: 0,
    phase: 0,
    heroStart: INACTIVE_TIME_SECONDS,
    armed: true,
    tickStart: INACTIVE_TIME_SECONDS,
    tickMonth: 0,
    month: 0,
    announced: 0,
    events: 0,
    lastHaptic: INACTIVE_TIME_SECONDS,
    endLatch: 0,
    reducedFrom: 0,
    reducedStart: 0,
    displayMonth: 0,
    reducedMonth: 0,
    fadeStart: INACTIVE_TIME_SECONDS,
    haloFrom: 0
  };
}

export function copyState(wheel: GrowthState): GrowthState {
  'worklet';
  const drums: Drum[] = [];
  for (let index = 0; index < 5; index++) drums.push({
    ...wheel.drums[index]
  });
  return {
    ...wheel,
    drums
  };
}
