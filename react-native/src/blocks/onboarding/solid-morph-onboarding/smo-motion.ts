/** All times are seconds. The hover is observed; the lathe and page changes are ours. */
export const LOOP = 9;
export const T = {
  rest: 0,
  start: 0.6,
  half: 1,
  holdEnd: 1.8,
  pageTwo: 2.3,
  next: 2.8,
  pageThree: 5.3,
  return: 6,
  home: 8.55,
  end: 9,
} as const;
export const MOTION = {
  drag: 320,
  projection: 0.12,
  resumeAfter: 2,
  resetDuration: 0.6,
  spin: 1.5,
  tilt: 24,
  tiltLimit: 12,
  endTilt: 8,
  endSpin: 20,
  gap: 22,
  pressGap: 4,
  pressSlop: 10,
  edgeGuard: 20,
  velocityFade: 80,
  touchResume: 2600,
  precessionVelocity: 0.02,
  precessionTau: 1.2,
  glyphRate: 0.5,
  splitRate: 1,
  returnFade: 220,
} as const;
export const SPRING = {
  lathe: { damping: 20, stiffness: 140, mass: 1 },
  cushion: { damping: 8, stiffness: 170, mass: 1 },
  base: { damping: 14, stiffness: 200, mass: 1 },
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 },
  // Critical return, omega = 1/tau; release velocity is converted to degrees/s.
  precession: { damping: 2 / 1.2, stiffness: 1 / (1.2 * 1.2), mass: 1 },
} as const;
export function clamp(x: number, a = 0, b = 1) {
  'worklet';
  return Math.max(a, Math.min(b, x));
}
export function smooth(a: number, b: number, x: number) {
  'worklet';
  const u = clamp((x - a) / Math.max(0.00001, b - a));
  return u * u * (3 - 2 * u);
}
export function ease(x: number) {
  'worklet';
  const u = clamp(x);
  return u * u * u * (u * (u * 6 - 15) + 10);
}
export function progressAt(t: number) {
  'worklet';
  const x = ((t % LOOP) + LOOP) % LOOP;
  if (x < T.start) return 0;
  if (x < T.half) return 0.5 * ease((x - T.start) / (T.half - T.start));
  if (x < T.holdEnd) return 0.5;
  if (x < T.pageTwo) return 0.5 + 0.5 * ease((x - T.holdEnd) / (T.pageTwo - T.holdEnd));
  if (x < T.next) return 1;
  if (x < T.pageThree) return 1 + ease((x - T.next) / (T.pageThree - T.next));
  if (x < T.return) return 2;
  if (x < T.home) return 2 * (1 - ease((x - T.return) / (T.home - T.return)));
  return 0;
}
export function rateAt(t: number) {
  'worklet';
  return (progressAt(t + 0.001) - progressAt(t - 0.001)) / 0.002;
}
export function damped(t: number, damping: number, stiffness: number) {
  'worklet';
  if (t <= 0) return 0;
  return Math.exp((-damping * t) / 2) * Math.sin(Math.sqrt(Math.max(0.01, stiffness - (damping * damping) / 4)) * t);
}
export function bobAt(t: number) {
  'worklet';
  return 4 * (damped(t - T.pageTwo, 10, 180) + damped(t - T.pageThree, 10, 180)) * (1 - smooth(T.return, T.home, t));
}
export function baseAt(t: number) {
  'worklet';
  return 1.5 * (damped(t - T.pageTwo - 0.3, 14, 200) + damped(t - T.pageThree - 0.3, 14, 200)) * (1 - smooth(T.return, T.home, t));
}
export function hoverAt(t: number) {
  'worklet';
  return Math.sin(((((t % LOOP) + LOOP) % LOOP) / LOOP) * Math.PI * 4);
}
export function rubber(x: number, end: number) {
  'worklet';
  if (x < 0) return (x * 0.25) / (1 + Math.abs(x));
  if (x > end) return end + ((x - end) * 0.25) / (1 + x - end);
  return x;
}
export function pageMix(progress: number, count: number) {
  'worklet';
  const p = clamp(progress, 0, Math.max(0, count - 1));
  const from = Math.min(Math.floor(p), Math.max(0, count - 2));
  return { from, to: Math.min(from + 1, count - 1), t: p - from };
}

/** Lathe rate is the derivative of its eased angle, not raw page velocity. */
export function spinRate(progress: number, pageRate: number, turns = MOTION.spin as number) {
  'worklet';
  const u = clamp(progress - Math.floor(progress));
  return pageRate * turns * 30 * u * u * (1 - u) * (1 - u);
}
export function pressAt(distance: number) {
  'worklet';
  const down = Math.max(0, distance);
  // 0.5 pt/pt at first, stiffening to 0.2; the dish is a real 4 pt stop.
  return clamp((down <= 20 ? down * 0.5 : 10 + (down - 20) * 0.2) / 18);
}
export function pulseAt(t: number, at: number, duration: number) {
  'worklet';
  return t < at ? 0 : 1 - smooth(0, duration, t - at);
}
export function arrivalAt(t: number) {
  'worklet';
  return Math.max(pulseAt(t, T.pageTwo, 0.1), pulseAt(t, T.pageThree, 0.1));
}
export function basePulseAt(t: number) {
  'worklet';
  return Math.max(pulseAt(t, T.pageTwo + 0.3, 0.2), pulseAt(t, T.pageThree + 0.3, 0.2));
}
