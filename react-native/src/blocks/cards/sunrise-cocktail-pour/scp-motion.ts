// Sunrise Cocktail Pour — the loop. Helpers first: a worklet captures only what is declared above it.

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
export const LEVEL = 0.55;
export const LEVEL_FULL = 0.62;
/** a pour: the stream, the red sinking to the bottom, the sunrise climbing */
export function pourAt(tau: number) {
  'worklet';
  const stream = tau > 0 && tau < 1.2 ? tau / 1.2 : 0;
  const red = easeOut(clamp01((tau - 0.4) / 1.1));
  const rise = easeInOut(clamp01((tau - 0.7) / 2.2));
  const level = LEVEL + (LEVEL_FULL - LEVEL) * easeOut(clamp01((tau - 0.3) / 0.9));
  return { stream, red, rise, level };
}
/** stirring: the colours blend while the ice turns about the glass */
export function stirAt(tau: number) {
  'worklet';
  const mix = easeInOut(clamp01(tau / 1.3));
  const swirl = 2 * Math.PI * 1.2 * easeOut(clamp01(tau / 1.6));
  return { mix, swirl };
}

export const T = { pour: 1.0, stir: 4.6, serve: 6.8, out: [6.9, 7.6], in: [7.6, 8.5] } as const;

export type Frame = { level: number; red: number; rise: number; mix: number; stream: number; swirl: number; shift: number; step: number; finger: number; fingerPress: number };

export function frameAt(t: number): Frame {
  'worklet';
  let level = LEVEL;
  let red = 0;
  let rise = 0;
  let mix = 0;
  let stream = 0;
  let swirl = 0;
  let step = 0;
  let shift = 0;
  const p = pourAt(t - T.pour - 0.1);
  if (t > T.pour + 0.1 && t < T.out[1]) {
    level = p.level;
    red = p.red;
    rise = p.rise;
    stream = p.stream;
    step = 1;
  }
  if (t > T.stir + 0.1 && t < T.out[1]) {
    const s = stirAt(t - T.stir - 0.1);
    mix = s.mix;
    swirl = s.swirl;
    step = 2;
  }
  // served: the glass slides out to the left and a fresh one comes in from the right
  if (t >= T.out[0] && t < T.out[1]) shift = -3.4 * easeInOut(seg(t, T.out[0], T.out[1]));
  else if (t >= T.out[1] && t < T.in[1]) {
    shift = 3.4 * (1 - easeOut(seg(t, T.in[0], T.in[1])));
    step = 0;
  }
  if (t >= T.out[1]) step = 0;
  let finger = 0;
  let fingerPress = 0;
  for (const at of [T.pour, T.stir, T.serve]) {
    const f = easeOut(seg(t, at - 0.15, at - 0.02)) * (1 - easeOut(seg(t, at + 0.14, at + 0.3)));
    if (f > finger) {
      finger = f;
      fingerPress = easeOut(seg(t, at - 0.02, at + 0.08)) * (1 - easeOut(seg(t, at + 0.12, at + 0.26)));
    }
  }
  return { level, red, rise, mix, stream, swirl, shift, step, finger, fingerPress };
}
