export const LOOP = 8;
export const PEAK_START = 2.0225737763349363;
export const T = [
  [0, 0.4, 'Summary / rest'],
  [0.4, 0.8, 'Scrub to start / cool'],
  [0.8, 2.3, 'Climb to hill'],
  [2.3, 3.1, 'Hold white-hot hill'],
  [3.1, 4, 'Continue / cool'],
  [4, 4.6, 'Reverse once'],
  [4.6, 5, 'Coast'],
  [5, 5.6, 'Park'],
  [5.6, 6.8, 'Return to summary'],
  [6.8, 8, 'Rest / exact seam'],
] as const;
export const MOTION = {
  returnAfterMs: 3000,
  return: { damping: 24, stiffness: 70, mass: 1.2 },
  shortcut: { damping: 20, stiffness: 220, mass: 1 },
  snap: { damping: 30.5, stiffness: 322.3, mass: 1 },
  peak: { damping: 14, stiffness: 320, mass: 1 },
  ring: { damping: 18, stiffness: 380, mass: 0.8 },
  decayPerMs: 0.992,
  rubber: 0.25,
  rubberPt: 10,
  routeSamples: 256,
  routeWindow: 0.12,
  stridePt: 12,
  dragPt: 300,
  grainHz: 12,
  fingerprintMs: 1600,
  peakMs: 700,
  flareLag: 0.08,
  maxCallbacksHz: 10,
  hapticGapMs: 100,
  projection: 0.12,
} as const;
export function clamp(v: number, lo = 0, hi = 1) {
  'worklet';
  return Math.min(hi, Math.max(lo, v));
}
export function smooth(a: number, b: number, v: number) {
  'worklet';
  const x = clamp((v - a) / (b - a));
  return x * x * (3 - 2 * x);
}
export function mix(a: number, b: number, v: number) {
  'worklet';
  return a + (b - a) * v;
}
export function springAt(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  if (zeta >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}
export function progressAt(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  if (t < 0.4 || t >= 6.8) return 1;
  if (t < 0.8) return mix(1, 0.05, smooth(0.4, 0.8, t));
  if (t < 2.3) return mix(0.05, 0.62, smooth(0.8, 2.3, t));
  if (t < 3.1) return 0.62;
  if (t < 4) return mix(0.62, 0.98, smooth(3.1, 4, t));
  if (t < 4.6) return mix(0.98, 0.75, smooth(4, 4.6, t));
  if (t < 5) return mix(0.75, 0.8, 1 - Math.pow(1 - clamp((t - 4.6) / 0.4), 3));
  if (t < 5.6) return 0.8;
  return mix(0.8, 1, smooth(5.6, 6.8, t));
}
export function strideAt(p: number, time: number, reduced = false) {
  'worklet';
  if (reduced) return 0.72;
  // Slow motion is windowed to a whole cycle; the resting pose matches at both cut points.
  const slow = 0.25 * Math.sin((2 * Math.PI * time) / LOOP) * smooth(0, 0.2, time) * (1 - smooth(6.6, 6.8, time));
  return 0.72 + 2 * Math.PI * (((p - 1) * MOTION.dragPt) / MOTION.stridePt + slow);
}
export function rubberProgress(v: number) {
  'worklet';
  const edge = clamp(v);
  const over = v - edge;
  return edge + (over * MOTION.rubber) / (1 + (Math.abs(over) * MOTION.dragPt) / MOTION.rubberPt);
}
export function peakAt(t: number) {
  'worklet';
  return smooth(PEAK_START, PEAK_START + 0.08, t) * (1 - smooth(PEAK_START + 0.62, PEAK_START + 0.7, t));
}

/** Field clock returns around the noise circle before the still tail; the final 1.2s is the exact poster. */
export function sceneTimeAt(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  if (t < 0.4 || t >= 6.8) return 0;
  if (t < 5.6) return t - 0.4;
  return mix(5.2, 8, smooth(5.6, 6.8, t));
}

/** Analytic response of the specified {damping:14,stiffness:320,mass:1} numeral spring. */
export function bpmPulseAt(age: number) {
  'worklet';
  if (age <= 0 || age >= 0.7) return 1;
  const w = Math.sqrt(MOTION.peak.stiffness / MOTION.peak.mass);
  const zeta = MOTION.peak.damping / (2 * Math.sqrt(MOTION.peak.stiffness * MOTION.peak.mass));
  return 1 + 0.06 * clamp(springAt(age, (2 * Math.PI) / w, zeta)) * (1 - smooth(0.24, 0.7, age));
}

/** Exact 0.992-per-ms coast; independent of Reanimated's pixel velocity cutoff and frame rate. */
export function coastAt(origin: number, velocity: number, age: number) {
  'worklet';
  const lambda = -Math.log(MOTION.decayPerMs) * 1000;
  const attenuation = Math.exp(-lambda * Math.max(0, age));
  return { position: origin + (velocity * (1 - attenuation)) / lambda, velocity: velocity * attenuation };
}
