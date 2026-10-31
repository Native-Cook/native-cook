// Puffy Sticker — the loop and the motion helpers. Helpers first: worklets capture what is above them.

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
/** Damped wobble after an impulse at t = 0 (0 before it). */
export function wobble(t: number, freq: number, decay: number) {
  'worklet';
  if (t <= 0) return 0;
  return Math.sin(2 * Math.PI * freq * t) * Math.exp(-decay * t);
}
/** How a held press inflates: fast at first, easing into full (critically damped approach). */
export function inflateAfter(held: number) {
  'worklet';
  if (held <= 0) return 0;
  const k = 4.2;
  return 1 - (1 + k * held) * Math.exp(-k * held);
}

export const LOOP = 9.6;
/** Scripted taps on the tray: [time, target shape]. Each step of shape takes MORPH s. */
export const MORPH = 0.65;
export const T = {
  hold0: 0.6,
  hold1: 1.7,
  star: 2.4,
  cloud: 4.0,
  smiley: 5.6,
  flat: 7.0,
  heart: 7.9,
} as const;

export type Frame = {
  puff: number;
  shape: number; // 0 heart … 3 smiley, continuous
  squish: number; // + wide/flat, − tall
  touch: number; // 0…1, the scripted finger on the sticker
  tapAt: number; // which tray thumb the scripted finger is on (−1 none)
  tapPress: number; // 0…1 its press
  flatPress: number;
};

function shapeAt(t: number) {
  'worklet';
  let s = 0;
  s += easeInOut(seg(t, T.star + 0.1, T.star + 0.1 + MORPH));
  s += easeInOut(seg(t, T.cloud + 0.1, T.cloud + 0.1 + MORPH));
  s += easeInOut(seg(t, T.smiley + 0.1, T.smiley + 0.1 + MORPH));
  // back to the heart through the cloud and the star, while flat
  s -= 3 * easeInOut(seg(t, T.heart + 0.1, T.heart + 0.1 + MORPH * 1.6));
  return s;
}

export function frameAt(t: number): Frame {
  'worklet';
  const held = Math.min(t, T.hold1) - T.hold0;
  const up = inflateAfter(held);
  const down = easeInOut(seg(t, T.flat + 0.1, T.flat + 0.75));
  const puff = up * (1 - down);
  // squash: a small press-in when the finger lands, the wobble on release, a wobble after each
  // morph lands, and the deflate's flop
  let squish = -0.03 * easeOut(seg(t, T.hold0, T.hold0 + 0.12)) * (1 - seg(t, T.hold1, T.hold1 + 0.05));
  squish += 0.09 * wobble(t - T.hold1, 2.6, 4.2);
  squish += 0.05 * wobble(t - (T.star + 0.1 + MORPH), 3.2, 5.5);
  squish += 0.05 * wobble(t - (T.cloud + 0.1 + MORPH), 3.2, 5.5);
  squish += 0.05 * wobble(t - (T.smiley + 0.1 + MORPH), 3.2, 5.5);
  squish += 0.06 * wobble(t - (T.flat + 0.75), 3.0, 6.0);
  const touch = easeOut(seg(t, T.hold0 - 0.12, T.hold0)) * (1 - easeOut(seg(t, T.hold1, T.hold1 + 0.18)));
  let tapAt = -1;
  let tapPress = 0;
  const taps = [
    [T.star, 1],
    [T.cloud, 2],
    [T.smiley, 3],
    [T.heart, 0],
  ] as const;
  for (let i = 0; i < taps.length; i++) {
    const a = taps[i][0];
    if (t >= a - 0.15 && t < a + 0.35) {
      tapAt = taps[i][1];
      tapPress = easeOut(seg(t, a, a + 0.08)) * (1 - easeOut(seg(t, a + 0.12, a + 0.3)));
    }
  }
  const flatPress = easeOut(seg(t, T.flat, T.flat + 0.08)) * (1 - easeOut(seg(t, T.flat + 0.12, T.flat + 0.3)));
  return { puff, shape: shapeAt(t), squish, touch, tapAt, tapPress, flatPress };
}
