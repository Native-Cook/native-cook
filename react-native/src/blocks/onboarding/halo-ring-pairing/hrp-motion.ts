// Halo Ring Pairing — the pairing's story and the autoPlay loop as pure functions of time. Helpers
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
/** A flick that slows down: fast at first, coming to rest at 1 (exponential-like). */
export function glide(x: number) {
  'worklet';
  const k = 4.5;
  return (1 - Math.exp(-k * clamp01(x))) / (1 - Math.exp(-k));
}
/** A small bounce that starts and ends at 0. */
export function bump(u: number) {
  'worklet';
  if (u <= 0 || u >= 0.7) return 0;
  return Math.exp(-u * 7) * Math.sin(u * 20);
}

export const SIZES = [7, 8, 9, 10, 11, 12];
export const START_SIZE = 9;
export const SEARCH_S = 1.8; // how long the search takes before the ring is found
export const YAW0 = 0.3;

export function scaleOf(size: number) {
  'worklet';
  return 1 + (size - START_SIZE) * 0.025;
}

export const LOOP = 11;
export const T = {
  found: [1.8, 3.0] as const, // the light condenses into the ring
  spin: [3.6, 5.2] as const, // a finger spins it (a drag, then it glides)
  drag: 4.0,
  size: 5.4, // a tap on size 10
  pair: 6.3, // a tap on Pair
  fly: [6.5, 7.3] as const, // the ring flies into the header
  back: [9.2, 9.9] as const, // the loop's return: the ring comes back…
  fade: [9.9, 10.7] as const, // …and dissolves into light
} as const;

export type Frame = {
  condense: number;
  search: number; // ripples
  yaw: number;
  scale: number;
  size: number; // the chosen size (a step)
  fly: number; // 0 on the stage … 1 in the header
  paired: number; // the dashboard's presence
  found: number; // the "found" words and controls
  sizeBump: number;
  pressPair: number;
  finger: number;
  fingerAt: number; // 0 the ring, 1 the size chip, 2 the Pair button
  fingerDx: number; // along the spin drag, pt
};

export function frameAt(t: number): Frame {
  'worklet';
  const condense = easeInOut(seg(t, T.found[0], T.found[1])) * (1 - easeInOut(seg(t, T.fade[0], T.fade[1])));
  const search = (1 - smooth(T.found[0], T.found[0] + 0.6, t)) + smooth(T.fade[0] + 0.3, LOOP, t);
  // the spin: dragged a quarter turn in 0.4 s, then gliding a full turn more and coming to rest
  const dragU = seg(t, T.spin[0], T.drag);
  const yaw = YAW0 + 2 * Math.PI * (0.2 * dragU + 0.8 * glide(seg(t, T.drag, T.spin[1])));
  const size = t >= T.size && t < T.back[0] ? 10 : START_SIZE;
  const fly = easeInOut(seg(t, T.fly[0], T.fly[1])) * (1 - easeInOut(seg(t, T.back[0], T.back[1])));
  const paired = smooth(T.fly[0] + 0.3, T.fly[1] + 0.2, t) * (1 - smooth(T.back[0] - 0.1, T.back[0] + 0.3, t));
  // the found words and controls leave when Pair is tapped; the search's words come back with the light
  const found = smooth(T.found[0] + 0.7, T.found[1] + 0.2, t) * (1 - smooth(T.pair + 0.1, T.pair + 0.35, t));
  const tapPress = (a: number) => easeOut(seg(t, a - 0.08, a)) * (1 - easeOut(seg(t, a + 0.06, a + 0.2)));
  const near = (a: number, b: number) => easeOut(seg(t, a - 0.25, a)) * (1 - easeOut(seg(t, b, b + 0.2)));
  const onRing = near(T.spin[0], T.drag + 0.05);
  const onSize = near(T.size - 0.05, T.size + 0.15);
  const onPair = near(T.pair - 0.05, T.pair + 0.15);
  return {
    condense,
    search: clamp01(search),
    yaw: t < T.spin[0] ? YAW0 : t > T.back[0] ? YAW0 + 2 * Math.PI : yaw,
    scale: scaleOf(size),
    size,
    fly,
    paired,
    found,
    sizeBump: bump(t - T.size),
    pressPair: tapPress(T.pair),
    finger: clamp01(onRing + onSize + onPair),
    fingerAt: onRing > 0.001 ? 0 : onSize > 0.001 ? 1 : 2,
    fingerDx: 160 * easeInOut(dragU),
  };
}
