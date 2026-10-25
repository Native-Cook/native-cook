/*
 Compass Needle Nav — the needle in its liquid and the pages swinging round the compass.

 The needle is an underdamped spring (ω 7 rad/s, ζ 0.4: it swings 25 % past a new heading and
 settles in about 1.4 s, as a needle floating in damping fluid does). The pages hang on a wheel
 centred on the compass, one every 90°; the wheel follows the same target critically damped
 (ω 5.5 rad/s), so a page swings in from the side its tab sits on, without wobbling.

 LOOP = 8 s: rest on N (Map) 0 – 1.2; to E (Trips) at 1.2, settled by 2.6; hold; to S (Gear) at
 3.4, settled by 4.8; hold; on round through W to N at 5.8 (a half turn), settled by 7.2; 7.4 – 7.9
 the last hundredth of a degree is eased out so 8.0 equals 0.0; rest.
*/

export const LOOP = 8;
export const NEEDLE_OMEGA = 7;
export const NEEDLE_ZETA = 0.4;
export const WHEEL_OMEGA = 5.5;
export const IDLE_BEFORE_BACK = 2;
export const BACK_FOR = 0.9;

/** [time, degrees added to the heading]. */
const STEPS: readonly (readonly [number, number])[] = [
  [1.2, 90],
  [3.4, 90],
  [5.8, 180],
];

export type Pose = {
  /** Needle heading and the page wheel's angle, degrees (continuous, 0 = N, clockwise). */
  needle: number;
  wheel: number;
  /** Needle angular velocity, degrees per second (moves the bubble). */
  spin: number;
  /** The finger's angle on the bezel while dragging, or NaN. */
  finger: number;
  /** The heading being steered to (lights its mark on the bezel). */
  target: number;
};

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function easeInOut(x: number) {
  'worklet';
  const v = clamp01(x);
  return v < 0.5 ? 4 * v * v * v : 1 - Math.pow(-2 * v + 2, 3) / 2;
}

/** Wraps degrees to -180 … 180. */
export function wrap(d: number) {
  'worklet';
  return d - 360 * Math.round(d / 360);
}

function under(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = NEEDLE_OMEGA;
  const z = NEEDLE_ZETA;
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * tau) * (Math.cos(wd * tau) + ((z * w) / wd) * Math.sin(wd * tau));
}

function critical(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = WHEEL_OMEGA;
  return 1 - Math.exp(-w * tau) * (1 + w * tau);
}

function headingAt(t: number) {
  'worklet';
  let needle = 0;
  let wheel = 0;
  let target = 0;
  for (let i = 0; i < STEPS.length; i += 1) {
    needle += STEPS[i][1] * under(t - STEPS[i][0]);
    wheel += STEPS[i][1] * critical(t - STEPS[i][0]);
    if (t >= STEPS[i][0]) target += STEPS[i][1];
  }
  // Ease out the spring's last residue so the loop closes exactly on 360°.
  const k = easeInOut((t - 7.4) / 0.5);
  return { needle: needle + (360 - needle) * k, wheel: wheel + (360 - wheel) * k, target };
}

/** The pose of the self-playing loop at loop time t. */
export function loopPose(t: number): Pose {
  'worklet';
  const h = headingAt(t);
  const h2 = headingAt(Math.max(0, t - 1 / 60));
  return { needle: h.needle, wheel: h.wheel, spin: (h.needle - h2.needle) * 60, finger: NaN, target: h.target };
}

/** The finger's simulation, mutated in place on the UI thread. */
export type Sim = {
  needle: number;
  vel: number;
  wheel: number;
  wheelVel: number;
  target: number;
  finger: number;
  /** Easing back: start time, and where it started from. */
  backAt: number;
  from: number[];
};

export function makeSim(): Sim {
  'worklet';
  return { needle: 0, vel: 0, wheel: 0, wheelVel: 0, target: 0, finger: NaN, backAt: -1, from: [0, 0, 0] };
}

/** Steps both springs toward the target (fixed 1/240 s substeps, so it is stable at any rate). */
export function stepSim(s: Sim, dt: number) {
  'worklet';
  const n = Math.max(1, Math.ceil(dt * 240));
  const h = dt / n;
  const w = NEEDLE_OMEGA;
  const z = NEEDLE_ZETA;
  const ww = WHEEL_OMEGA;
  for (let i = 0; i < n; i += 1) {
    s.vel += (-w * w * (s.needle - s.target) - 2 * z * w * s.vel) * h;
    s.needle += s.vel * h;
    s.wheelVel += (-ww * ww * (s.wheel - s.target) - 2 * ww * s.wheelVel) * h;
    s.wheel += s.wheelVel * h;
  }
}

/** The pose under (or after) a finger. */
export function simPose(s: Sim, now: number): Pose {
  'worklet';
  if (s.backAt >= 0) {
    const k = easeInOut((now - s.backAt) / BACK_FOR);
    return {
      needle: s.from[0] + (s.target - s.from[0]) * k,
      wheel: s.from[1] + (s.target - s.from[1]) * k,
      spin: 0,
      finger: NaN,
      target: s.target,
    };
  }
  return { needle: s.needle, wheel: s.wheel, spin: s.vel, finger: s.finger, target: s.target };
}
