// Snow Globe Gift — the loop. Helpers first: a worklet captures only what is declared above it.

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
export const T = {
  shake: [0.9, 1.6],
  settle: [1.6, 5.2],
  open: 5.6,
  lid: [5.7, 6.2],
  shut: [8.3, 8.9],
} as const;

export type Frame = { wobble: number; swirl: number; spin: number; lid: number; glow: number; amount: number; finger: number; fingerAt: number; fingerX: number; fingerPress: number };

export function frameAt(t: number): Frame {
  'worklet';
  // the shake: three quick tilts, the globe rocking back after
  let wobble = 0;
  if (t >= T.shake[0] && t < T.shake[0] + 2.4) {
    const tau = t - T.shake[0];
    const drive = 1 - seg(t, T.shake[0], T.shake[1]);
    wobble = 0.16 * Math.sin(tau * 2 * Math.PI * 2.6) * (0.4 + 0.6 * drive) * Math.exp(-1.6 * Math.max(0, t - T.shake[1]));
    wobble *= 1 - seg(t, T.shake[0] + 1.9, T.shake[0] + 2.4);
  }
  // the snow lifts into the water and settles over three and a half seconds
  const up = easeOut(seg(t, T.shake[0] + 0.1, T.shake[1]));
  const down = seg(t, T.settle[0], T.settle[1]);
  const swirl = up * (1 - easeInOut(down));
  // the water's turn: fast while shaken, slowing as it settles (an integral of the speed)
  const k = seg(t, T.shake[0], T.settle[1]);
  const spin = 9 * (1 - (1 - k) * (1 - k));
  const lid = easeOut(seg(t, T.lid[0], T.lid[1])) * (1 - easeInOut(seg(t, T.shut[0], T.shut[1])));
  const glow = lid;
  const amount = seg(t, T.lid[0] + 0.2, T.lid[0] + 0.6) * (1 - seg(t, T.shut[0] - 0.2, T.shut[0] + 0.1));
  let finger = 0;
  let fingerAt = 0;
  let fingerX = 0;
  let fingerPress = 0;
  if (t < 3) {
    finger = easeOut(seg(t, T.shake[0] - 0.15, T.shake[0])) * (1 - easeOut(seg(t, T.shake[1], T.shake[1] + 0.18)));
    fingerX = 0.5 * Math.sin((t - T.shake[0]) * 2 * Math.PI * 2.6) * (t > T.shake[0] ? 1 : 0);
    fingerPress = finger;
  } else {
    fingerAt = 1;
    finger = easeOut(seg(t, T.open - 0.15, T.open - 0.02)) * (1 - easeOut(seg(t, T.open + 0.14, T.open + 0.3)));
    fingerPress = easeOut(seg(t, T.open - 0.02, T.open + 0.08)) * (1 - easeOut(seg(t, T.open + 0.12, T.open + 0.26)));
  }
  return { wobble, swirl, spin, lid, glow, amount, finger, fingerAt, fingerX, fingerPress };
}
