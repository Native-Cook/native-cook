// Chrome Prize Wheel — searched launch parameters and a fixed-step, UI-clock-only demo script.
import { H } from './wheel-motion-constants';

/** Measured with stepWheel: chest 33.5455476078 rad, 5.733333 s including drag/still beat;
 * last peg 36.503575deg, no rollback. Box 18.8500670950 rad, 4.827083 s, last peg
 * 29.223993deg, rollback 15.244232deg, home error +0.029288deg. The second speed was
 * bracketed then root-searched; the residual is the fixed-step impact discontinuity.
 * The measured complete loop is recorded in proofs-motion/demo.txt. */
export const DEMO = {
  periodSteps: 10265, // 21.3854166667 seconds, measured over two unchanged complete passes.
  rest: 0.9,
  fingerWait: 0.27, // 150 ms appear + 120 ms held before accelerating.
  first: { x: 0.62, y: -0.42, drag: 70 * Math.PI / 180, duration: 0.3, omega: 16.17000000000024, admire: 3.4 },
  between: 0.7,
  second: { x: -0.55, y: 0.5, drag: -50 * Math.PI / 180, duration: 0.32, omega: -9.888019148781598, admire: 2.4 },
  press: 0.14,
  tail: 0.8,
} as const;

export function demoSteps(seconds: number): number {
  'worklet';
  return Math.round(seconds / H);
}
/** Cubic acceleration keeps the initial finger still and has no touch-down angle discontinuity. */
export function demoDrag(step: number, duration: number, angle: number): number {
  'worklet';
  const u = Math.min(1, Math.max(0, step / demoSteps(duration)));
  return angle * u * u * u;
}
