// Drums spin at a bounded speed, then finish a forward runway before spring tracking.
import { smooth } from './growth-math';
import {
  SETTLE_SPEED,
  SETTLE_POSITION,
  DRUM_RATE_TIME_CONSTANT_SECONDS,
  DRUM_DIRECTION_EPSILON,
  DRUM_SPIN_THRESHOLD,
  DRUM_SPIN_TIME_CONSTANT_SECONDS,
  DRUM_RUNOUT_DELAY_SECONDS,
  DRUM_RUNWAY_CELLS,
  DRUM_STAGGER_CELLS,
  DT,
  DRUM_STIFFNESS,
  DRUM_DAMPING_RATIO,
  DRUM_MAX_SPEED,
  DRUM_CARRY_WINDOW
} from './growth-physics';
export type Drum = {
  theta: number; // digit cells, unwrapped
  omega: number; // digit cells/second
  rate: number;
  dir: number;
  previous: number;
  wraps: number;
  spinning: boolean;
  runout: number; // seconds left before the spring engages; -1 means unstarted
};

export function drumTarget(value: number, drumIndex: number) {
  'worklet';
  if (drumIndex === 0) return value;
  const scaledPosition = value / Math.pow(10, drumIndex);
  const whole = Math.floor(scaledPosition);
  return whole + smooth(1 - DRUM_CARRY_WINDOW[drumIndex], 1, scaledPosition - whole);
}

export function drumStep(drum: Drum, target: number, drumIndex: number, landing = false) {
  'worklet';
  const delta = target - drum.previous;
  drum.previous = target;
  drum.rate += (Math.abs(delta) / DT - drum.rate) * (1 - Math.exp(-DT / DRUM_RATE_TIME_CONSTANT_SECONDS));
  if (Math.abs(delta) > DRUM_DIRECTION_EPSILON) drum.dir = Math.sign(delta);
  if (!landing && drum.rate > DRUM_SPIN_THRESHOLD * DRUM_MAX_SPEED[drumIndex]) {
    drum.runout = -1;
    drum.spinning = true;
    drum.omega += (drum.dir * DRUM_MAX_SPEED[drumIndex] - drum.omega)
      * (1 - Math.exp(-DT / DRUM_SPIN_TIME_CONSTANT_SECONDS));
  } else {
    if (drum.spinning && drumIndex < 4) {
      // Low drums finish their carry in sequence before engaging the spring.
      if (drum.runout < 0) drum.runout = drumIndex < 3 ? drumIndex * DRUM_RUNOUT_DELAY_SECONDS : 0;
      if (drum.runout > 0) {
        drum.runout = Math.max(0, drum.runout - DT);
        drum.omega += (drum.dir * DRUM_MAX_SPEED[drumIndex] - drum.omega)
      * (1 - Math.exp(-DT / DRUM_SPIN_TIME_CONSTANT_SECONDS));
        drum.theta += drum.omega * DT;
        return;
      }
      const runway = DRUM_RUNWAY_CELLS + (drumIndex < 3 ? DRUM_STAGGER_CELLS * drumIndex : 0);
      drum.wraps = drum.dir > 0 ?
        Math.ceil((drum.theta + runway - target) / 10) : Math.floor((drum.theta - runway - target) / 10);
    }
    // Keep this landing's full runway. Re-picking the nearest wrap on the next
    // substep would erase the stagger or reverse a drum with >6 cells to go.
    const error = target + 10 * drum.wraps - drum.theta;
    drum.spinning = false;
    drum.omega += (DRUM_STIFFNESS[drumIndex] * error
      - 2 * DRUM_DAMPING_RATIO[drumIndex] * Math.sqrt(DRUM_STIFFNESS[drumIndex]) * drum.omega) * DT;
    if (Math.abs(error) < SETTLE_POSITION && Math.abs(drum.omega) < SETTLE_SPEED) {
      drum.theta = target + 10 * drum.wraps;
      drum.omega = 0;
      return;
    }
  }
  drum.theta += drum.omega * DT;
}
