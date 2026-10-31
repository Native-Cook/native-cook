// Drone Unfold Connect — the drone's story and the autoPlay loop as pure functions of time. Helpers
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

export const LIFT = 0.9; // hover height (units)
export const STICK_R = 44; // the joystick's travel (pt)
export const TILT_MAX = 0.35; // rad at full stick
export const DRIFT = 0.35; // units the drone leans over at full stick
export const YAW = 0.5;

export const LOOP = 10;
export const T = {
  connect: 0.8,
  unfold: [1.0, 2.2] as const,
  spin: [2.2, 2.9] as const,
  lift: [2.9, 3.8] as const,
  land: [7.4, 8.4] as const,
  spinDown: [8.2, 8.8] as const,
  fold: [8.6, 9.6] as const,
} as const;

/** The scripted stick, −1 … 1 each way: right and back, then forward and back. */
export function stickAt(t: number) {
  'worklet';
  const x = easeInOut(seg(t, 4.2, 4.9)) - easeInOut(seg(t, 5.5, 6.0));
  const y = -(easeInOut(seg(t, 6.1, 6.6)) - easeInOut(seg(t, 6.9, 7.3)));
  return { x, y };
}

export type Frame = {
  unfold: number;
  spin: number;
  lift: number;
  pitch: number;
  roll: number;
  drift: number;
  light: number; // the status light, 0 red … 1 green
  connected: number; // the stick's presence
  stickX: number;
  stickY: number;
  pressConnect: number;
  finger: number;
  fingerAt: number; // 0 Connect, 1 the stick
};

export function frameAt(t: number): Frame {
  'worklet';
  const unfold = easeInOut(seg(t, T.unfold[0], T.unfold[1])) * (1 - easeInOut(seg(t, T.fold[0], T.fold[1])));
  const spin = smooth(T.spin[0], T.spin[1], t) * (1 - smooth(T.spinDown[0], T.spinDown[1], t));
  const flying = easeOut(seg(t, T.lift[0], T.lift[1])) * (1 - easeInOut(seg(t, T.land[0], T.land[1])));
  // a slow bob while it hovers, three times a loop
  const bob = 0.05 * Math.sin((2 * Math.PI * 3 * t) / LOOP) * flying;
  // the drone follows the stick a little late, like a mass
  const s = stickAt(t - 0.12);
  const light = smooth(T.spin[0] + 0.1, T.spin[0] + 0.3, t) * (1 - smooth(T.fold[1] - 0.2, T.fold[1], t));
  const connected = smooth(T.lift[0], T.lift[1], t) * (1 - smooth(T.land[0], T.land[0] + 0.3, t));
  const tap = (a: number) => easeOut(seg(t, a - 0.08, a)) * (1 - easeOut(seg(t, a + 0.06, a + 0.2)));
  const near = (a: number, b: number) => easeOut(seg(t, a - 0.25, a)) * (1 - easeOut(seg(t, b, b + 0.2)));
  const onConnect = near(T.connect - 0.05, T.connect + 0.15);
  const onStick = near(4.1, 7.35);
  const st = stickAt(t);
  return {
    unfold,
    spin,
    lift: LIFT * flying + bob,
    pitch: -s.y * TILT_MAX * flying,
    roll: -s.x * TILT_MAX * flying,
    drift: s.x * DRIFT * flying,
    light,
    connected,
    stickX: st.x,
    stickY: st.y,
    pressConnect: tap(T.connect),
    finger: clamp01(onConnect + onStick),
    fingerAt: onConnect > 0.001 ? 0 : 1,
  };
}
