// Serum Dropper Routine — the loop. Helpers first: a worklet captures only what is declared above it.
//
// Held, the dropper rises out of the bottle and leans over the card (lift 0 … 1); squeezed (squeeze
// 0 … 1), its bulb gives and drops of oil form at the tip, neck and fall to the card's floor, where they
// spread and merge into a glossy pool that soaks in over a few seconds (soak 0 … 1). The step is ticked
// off once the oil is on.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export const LOOP = 10;
/** a drop: it swells for GROW s at the tip, necks for NECK s, then falls */
export const GROW = 0.55;
export const NECK = 0.16;
export const DROPS = 3;

/* ---------------------------------------------------------------- the loop (10 s)
 0.5  a tap on the step's tick: it clears                1.2 a finger holds the bottle: the dropper rises (1.3 … 1.9)
 2.0  the bulb is squeezed: drops at 2.1, 2.9, 3.7        4.6 the step ticks itself       5.2 let go: the dropper goes back (5.2 … 5.8)
 5.8  the pool soaks in (5.8 … 9.6)                       10 = 0 */
export const T = {
  untick: 0.5,
  hold: 1.2,
  lift: [1.3, 1.9],
  squeeze: [2.0, 4.4],
  drops: [2.1, 2.9, 3.7],
  tick: 4.6,
  drop: [5.2, 5.8],
  soak: [5.8, 9.6],
} as const;

export type Frame = {
  lift: number;
  squeeze: number;
  drops: number[]; // each drop's age, s (−1 not yet)
  soak: number;
  done: number; // the step's tick 0 … 1
  finger: number;
  fingerAt: number; // 0 the tick, 1 the bottle
  press: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  const lift = easeInOut(seg(t, T.lift[0], T.lift[1])) * (1 - easeInOut(seg(t, T.drop[0], T.drop[1])));
  const sq = seg(t, T.squeeze[0], T.squeeze[0] + 0.25) * (1 - seg(t, T.squeeze[1], T.squeeze[1] + 0.3));
  // the bulb pulses with each drop it lets go
  let pulse = 0;
  const drops: number[] = [];
  for (let i = 0; i < DROPS; i++) {
    const a = t - T.drops[i];
    drops.push(a >= 0 && t < T.soak[1] + 0.3 ? a : -1);
    if (a >= 0 && a < GROW + NECK) pulse = Math.max(pulse, Math.sin((Math.PI * a) / (GROW + NECK)));
  }
  const squeeze = clamp01(sq * (0.55 + 0.45 * pulse));
  const soak = seg(t, T.soak[0], T.soak[1]);
  const done = t < T.untick ? 1 : t < T.tick ? 1 - easeOut(seg(t, T.untick, T.untick + 0.25)) : easeOut(seg(t, T.tick, T.tick + 0.35));
  let finger = 0;
  let fingerAt = 0;
  let press = 0;
  const f0 = easeOut(seg(t, T.untick - 0.18, T.untick - 0.03)) * (1 - easeOut(seg(t, T.untick + 0.08, T.untick + 0.24)));
  if (f0 > finger) {
    finger = f0;
    press = f0;
  }
  const f1 = easeOut(seg(t, T.hold - 0.18, T.hold - 0.03)) * (1 - easeOut(seg(t, T.drop[0], T.drop[0] + 0.16)));
  if (f1 > finger) {
    finger = f1;
    fingerAt = 1;
    press = 0.6 + 0.4 * squeeze;
  }
  return { lift, squeeze, drops, soak, done, finger, fingerAt, press };
}
