// Coin Drop Reward — the reward's story as pure functions of time, and the autoPlay loop. Helpers
// first: worklets capture what is above them.

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function smooth(a: number, b: number, x: number) {
  'worklet';
  const u = clamp01((x - a) / (b - a));
  return u * u * (3 - 2 * u);
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
export function easeIn(x: number) {
  'worklet';
  return x * x * x;
}
/** Ease-out with a small overshoot (about 8 %), landing exactly on 1. */
export function backOut(x: number) {
  'worklet';
  const c1 = 1.5;
  const u = clamp01(x) - 1;
  return 1 + (c1 + 1) * u * u * u + c1 * u * u;
}
/** A jelly wobble that starts and ends at 0. */
export function wobbleAt(u: number) {
  'worklet';
  if (u <= 0 || u >= 1) return 0;
  return Math.exp(-u * 5) * Math.sin(u * 26);
}

/** How far the coin is pulled toward the wallet (a share of the way) before it turns to a drop. */
export const PULL_FULL = 0.32;
export const COOLDOWN = 4; // s before a mission can be collected again
export const REWARD = 5;

/** The coin's shape while it is pulled: 0 coin … 0.8 drop (it finishes the change in flight). */
export function meltOf(pull: number) {
  'worklet';
  return 0.8 * smooth(0, 1, pull / PULL_FULL);
}

export const LOOP = 9;
export const T = {
  tap: 0.8,
  launch: 0.95,
  flip: [1.6, 2.4] as const,
  grab: 2.55,
  pull: [2.7, 3.5] as const,
  release: 3.8,
  absorb: 4.35,
} as const;
export const LAUNCH_S = 0.6;
export const FLY_S = T.absorb - T.release;

export type Frame = {
  launch: number; // 0 on the row … 1 in the toast (overshoots while it pops)
  flip: number; // 0 … 1, one turn
  pull: number; // the share of the way to the wallet the finger has pulled it
  melt: number; // 0 coin … 1 drop
  fly: number; // 0 … 1, into the wallet
  trail: number; // the droplet that trails it and lets go
  toast: number; // the tile's presence
  text: number; // its line
  wobbleU: number; // the wallet's jelly, as progress (wobbleAt)
  plus: number; // the "+5" rising from the wallet, as progress (shown as sin π·plus)
  coinVis: number; // the row's own coin (hidden while the reward is out, dim while it cools down)
  cooldown: number; // seconds left before the row can be collected again
  press: number; // the row pressed
  finger: number;
  onCoin: number; // 0 on the row, 1 on the coin
  fingerPull: number; // how far the finger has pulled, as pull
};

export function frameAt(t: number): Frame {
  'worklet';
  const launch = t < T.launch ? 0 : backOut(seg(t, T.launch, T.launch + LAUNCH_S));
  const flip = easeInOut(seg(t, T.flip[0], T.flip[1]));
  const held = PULL_FULL * easeInOut(seg(t, T.pull[0], T.pull[1]));
  const fly = easeIn(seg(t, T.release, T.absorb)) * 0.55 + 0.45 * easeInOut(seg(t, T.release, T.absorb));
  const pull = held * (1 - fly);
  const melt = t < T.release ? meltOf(held) : 0.8 + 0.2 * easeOut(seg(t, T.release, T.release + 0.3));
  const trail = smooth(T.release, T.absorb - 0.1, t);
  const toast = smooth(T.launch, T.launch + 0.35, t) * (1 - smooth(T.release + 0.1, T.release + 0.45, t));
  const text = smooth(T.launch + 0.3, T.launch + 0.6, t) * (1 - smooth(T.release, T.release + 0.25, t));
  const after = t - T.absorb;
  const cooldown = after > 0 ? Math.max(0, COOLDOWN - after) : 0;
  const out = t >= T.launch && t < T.absorb ? 1 : 0;
  const coinVis = out ? 0 : after > 0 ? 0.35 + 0.65 * smooth(COOLDOWN, COOLDOWN + 0.25, after) : 1;
  const tapPress = easeOut(seg(t, T.tap - 0.08, T.tap)) * (1 - easeOut(seg(t, T.tap + 0.06, T.tap + 0.22)));
  const near = (a: number, b: number) => easeOut(seg(t, a - 0.25, a)) * (1 - easeOut(seg(t, b, b + 0.2)));
  return {
    launch,
    flip,
    pull,
    melt,
    fly,
    trail,
    toast,
    text,
    wobbleU: t < T.absorb ? 1 : seg(t, T.absorb, T.absorb + 0.7),
    plus: t < T.absorb ? 1 : seg(t, T.absorb, T.absorb + 0.9),
    coinVis,
    cooldown,
    press: tapPress,
    finger: clamp01(near(T.tap - 0.05, T.tap + 0.15) + near(T.grab, T.release)),
    onCoin: t > (T.tap + T.grab) / 2 ? 1 : 0,
    fingerPull: held,
  };
}
