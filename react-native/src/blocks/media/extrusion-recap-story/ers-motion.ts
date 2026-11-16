export const LOOP = 8;
export const T = {
  noteRest: [0, 0.7],
  noteHeart: [0.7, 2.1],
  heartRest: [2.1, 3.5],
  heartBolt: [3.5, 4.7],
  boltRest: [4.7, 6],
  boltNote: [6, 7.2],
  seamRest: [7.2, 8],
} as const;
export const SPRING = {
  page: { damping: 24, stiffness: 200, mass: 1 },
  steer: { damping: 14, stiffness: 120, mass: 1 },
  band: { damping: 12, stiffness: 200, mass: 1 },
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 },
} as const;
export const GESTURE = {
  distance: 300,
  intent: 10,
  steerDistance: 40,
  steerAngle: (12 * Math.PI) / 180,
  hold: 200,
  resume: 2000,
  projection: 0.12,
  threshold: 0.4,
  velocity: 600,
  rubber: 0.25,
} as const;
export function clamp(v: number, a = 0, b = 1) {
  'worklet';
  return Math.min(b, Math.max(a, v));
}
export function smooth(a: number, b: number, v: number) {
  'worklet';
  const x = clamp((v - a) / (b - a));
  return x * x * (3 - 2 * x);
}
export function springAt(t: number, damping = 12, stiffness = 200) {
  'worklet';
  if (t <= 0) return 0;
  const w = Math.sqrt(Math.max(0.0001, stiffness - (damping * damping) / 4));
  return 1 - Math.exp((-damping * t) / 2) * (Math.cos(w * t) + (damping / (2 * w)) * Math.sin(w * t));
}
export function timeline(t: number) {
  'worklet';
  const x = ((t % LOOP) + LOOP) % LOOP;
  if (x < 0.7) return 0;
  if (x < 1.25) return 0.5 * smooth(0.7, 1.25, x);
  if (x < 1.45) return 0.5;
  if (x < 2.1) return 0.5 + 0.5 * smooth(1.45, 2.1, x);
  if (x < 3.5) return 1;
  if (x < 4.7) return 1 + smooth(3.5, 4.7, x);
  if (x < 6) return 2;
  if (x < 7.2) return 2 + smooth(6, 7.2, x);
  return 3;
}
export function pageAlpha(progress: number, i: number, count: number) {
  'worklet';
  const p = ((progress % count) + count) % count;
  const base = Math.floor(p),
    q = p - base;
  if (i === base) return 1 - smooth(0, 0.3, q);
  if (i === (base + 1) % count) return smooth(0.72, 1, q);
  return 0;
}
/** The peek label leaves upward and arrives from below, riding the next card's landing rise. */
export function peekShift(progress: number, i: number, count: number, age: number) {
  'worklet';
  const p = ((progress % count) + count) % count,
    base = Math.floor(p),
    a = pageAlpha(progress, i, count);
  const rise = age >= 0 && age < 0.65 ? (8 * Math.exp(-6 * age) * Math.sin(Math.sqrt(164) * age)) / 0.532647813016 : 0;
  return (i === base ? -8 : 8) * (1 - a) - rise;
}
export function landingAt(t: number) {
  'worklet';
  const x = ((t % LOOP) + LOOP) % LOOP;
  return x >= 7.2 ? x - 7.2 : x >= 4.7 ? x - 4.7 : x >= 2.1 ? x - 2.1 : 10;
}
export function landingPulse(age: number) {
  'worklet';
  return age >= 0 && age < 0.65 ? (Math.exp(-6 * age) * Math.sin(Math.sqrt(164) * age)) / 0.532647813016 : 0;
}
export function bandRise(age: number) {
  'worklet';
  return 8 * landingPulse(age);
}

/** The fixed three-page film is the showcase. Arbitrary data uses the same read/travel grammar. */
export function storyAt(t: number, count: number) {
  'worklet';
  if (count <= 1) return 0;
  if (count === 3) return timeline(t);
  const duration = 2.6,
    x = ((t % (duration * count)) + duration * count) % (duration * count);
  return Math.floor(x / duration) + smooth(1.2, duration, x % duration);
}
export function copyShift(alpha: number, age: number, hasStat: boolean) {
  'worklet';
  const bounce = 4 * landingPulse(age);
  return -12 * (1 - alpha) + (hasStat ? bounce : 0);
}
export function highlightPass(age: number) {
  'worklet';
  return age >= 0 && age < 0.3 ? Math.sin((Math.PI * age) / 0.3) : 0;
}
export function digitArrival(progress: number, page: number, count: number, digit: number) {
  'worklet';
  const p = ((progress % count) + count) % count,
    i = Math.floor(p),
    q = p - i;
  if (i === page) return 1;
  if ((i + 1) % count === page) return smooth(0.7 + digit * 0.025, 0.95 + digit * 0.025, q);
  return 0;
}
/** Live gesture time only; never sampled by the deterministic film. */
export function touchTime() {
  'worklet';
  return Date.now();
}
