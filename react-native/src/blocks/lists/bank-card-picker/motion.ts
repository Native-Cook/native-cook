import { Easing } from 'react-native-reanimated';

// The motion system (emil-design-eng / animate-expo).
export const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
export const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);

// Picking a design: the front card sinks one plate back into the stack while the new
// design rises out of the plate behind it. One progress 0 → 1, on-screen movement.
export const SWAP = { duration: 420, easing: EASE_IN_OUT, outEnd: 0.55, inStart: 0.35 };
// The selection ring and the segment pill are moved by a tap: settle without overshoot.
export const RING = { duration: 300, dampingRatio: 1 };
export const PILL = { duration: 300, dampingRatio: 1 };
export const TEXT_SWAP = { out: 120, in: 200, rise: 4 }; // labels leave fast, arrive slower
export const PRESS = { scale: 0.97, in: 120, out: 150 };
export const CHECK = { from: 0.9, duration: 200 };

// Self-playing loop (ms): try three designs, compare the plans, come back to the first.
export const LOOP_MS = 12000;
export const SCRIPT: { at: number; act: 'design' | 'plan'; to: number }[] = [
  { at: 1200, act: 'design', to: 1 },
  { at: 3000, act: 'design', to: 2 },
  { at: 4800, act: 'design', to: 3 },
  { at: 6800, act: 'plan', to: 1 },
  { at: 8600, act: 'plan', to: 0 },
  { at: 10200, act: 'design', to: 0 },
];
export const RESUME_AFTER_MS = 3500;

export type FrozenPickerState = {
  design: number; plan: number; slotA: number; slotB: number; swap: number; ring: number; pill: number;
};

function cubicBezier(x: number, x1: number, y1: number, x2: number, y2: number) {
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  const sample = (t: number, a: number, b: number) => 3 * (1 - t) ** 2 * t * a + 3 * (1 - t) * t * t * b + t ** 3;
  let low = 0;
  let high = 1;
  for (let i = 0; i < 12; i++) {
    const middle = (low + high) / 2;
    if (sample(middle, x1, x2) < x) low = middle; else high = middle;
  }
  return sample((low + high) / 2, y1, y2);
}

function settledSpring(x: number) {
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  const frequency = 2 * Math.PI;
  return 1 - Math.exp(-frequency * x) * (1 + frequency * x);
}

/** Discrete selection plus every moving selector/card pose at a loop timestamp in milliseconds. */
export function pickerStateAt(milliseconds: number): FrozenPickerState {
  const time = ((milliseconds % LOOP_MS) + LOOP_MS) % LOOP_MS;
  let design = 0;
  let plan = 0;
  let slotA = 0;
  let slotB = 0;
  let swap = 0;
  let ring = 0;
  let pill = 0;
  for (const cue of SCRIPT) {
    if (time < cue.at) continue;
    const elapsed = time - cue.at;
    if (cue.act === 'design') {
      const from = Math.round(swap);
      const target = from < 0.5 ? 1 : 0;
      if (target > 0.5) slotB = cue.to; else slotA = cue.to;
      const previous = design;
      design = cue.to;
      swap = elapsed < SWAP.duration
        ? from + (target - from) * cubicBezier(elapsed / SWAP.duration, 0.77, 0, 0.175, 1)
        : target;
      ring = elapsed < RING.duration
        ? previous + (cue.to - previous) * settledSpring(elapsed / RING.duration)
        : cue.to;
    } else {
      const previous = plan;
      plan = cue.to;
      pill = elapsed < PILL.duration
        ? previous + (cue.to - previous) * settledSpring(elapsed / PILL.duration)
        : cue.to;
    }
  }
  return { design, plan, slotA, slotB, swap, ring, pill };
}
