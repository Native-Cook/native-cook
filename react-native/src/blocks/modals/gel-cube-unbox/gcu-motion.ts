/** All motion is authored (source is a still). Seconds, points, radians. */
export const LOOP = 7;
export const T = { rest: 0, gather: 0.8, lift: 1.1, gasp: 1.6, open: 2.7, holdEnd: 5.2, closeEnd: 6.3, end: LOOP } as const;
export const SPRING = {
  seal: { damping: 18, stiffness: 380, mass: 0.8 },
  lid: { damping: 14, stiffness: 120, mass: 1.4 },
  flick: { damping: 10, stiffness: 120, mass: 1.4 },
  close: { damping: 16, stiffness: 150, mass: 1.4 },
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 },
  core: { damping: 30.5, stiffness: 322.3, mass: 1 },
} as const;
export const MOTION = {
  drag: 150,
  seal: 6,
  resistance: 0.35,
  projection: 0.15,
  threshold: 0.45,
  flick: 900,
  angle: 58,
  overshoot: 66 / 58,
  rubber: 120,
  resume: 2000,
  reset: 600,
  causticLag: 0.05,
  fanLead: 0.04,
  flash: 2 / 60,
  flashGain: 0.2,
  callbackMs: 100,
  fadeMs: 200,
  maxDelta: 0.05,
  pressCore: 1.25,
  contact: 0.004,
  hairline: 0.03,
  cueInterval: 60,
  light: { headline: 0.1, subtitle: 0.16, pill: 0.28, sheet: 0.41, chip: 0.52, sheetTitle: 0.62, sheetBody: 0.73 },
} as const;
export function clamp01(x: number) {
  'worklet';
  return Math.max(0, Math.min(1, x));
}
export function smooth(a: number, b: number, x: number) {
  'worklet';
  const u = clamp01((x - a) / Math.max(0.00001, b - a));
  return u * u * (3 - 2 * u);
}
export function springAt(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  if (zeta >= 1) return 1 - Math.exp(-w * t) * (1 + w * t);
  const wd = w * Math.sqrt(Math.max(0, 1 - zeta * zeta));
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}
export function rubber(x: number) {
  'worklet';
  if (x < 0) return -((-x * MOTION.drag) / (1 + (-x * MOTION.drag) / MOTION.rubber)) / MOTION.drag;
  if (x > 1) return 1 + ((x - 1) * MOTION.drag) / (1 + ((x - 1) * MOTION.drag) / MOTION.rubber) / MOTION.drag;
  return x;
}
/** A deliberate 1.6 s lift; p=.14 at 1.6 s; the last 22% carries a soft gel spring tail. */
export function progressAt(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  if (t < T.lift) return 0.016 * Math.sin(Math.PI * smooth(T.gather, 1.05, t)) * (1 - smooth(1.02, T.lift, t));
  if (t < T.gasp) return 0.14 * smooth(T.lift, T.gasp, t);
  if (t < 2.35) return 0.14 + 0.64 * smooth(T.gasp, 2.35, t);
  if (t < T.open) return 0.78 + 0.22 * springAt((t - 2.35) * 2, 0.65, 0.78);
  if (t < T.holdEnd) return 1;
  if (t < T.closeEnd) return 1 - smooth(T.holdEnd, T.closeEnd, t);
  return 0;
}
export function coreAt(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  return 1 + 0.03 * (1 - Math.cos((4 * Math.PI * t) / LOOP)) + 0.19 * smooth(T.gather, 1.05, t) * (1 - smooth(1.1, 1.65, t));
}
export function fanAt(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  return progressAt(t >= T.holdEnd ? Math.min(T.closeEnd, t + MOTION.fanLead) : t);
}
export function flashAt(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  return t >= T.closeEnd - MOTION.flash && t < T.closeEnd ? MOTION.flashGain : 0;
}
export function contentAt(p: number) {
  'worklet';
  return smooth(0.8, 0.98, p);
}
export function lightAt(p: number, threshold: number) {
  'worklet';
  return smooth(threshold - 0.04, threshold + 0.04, p);
}
export function angleAt(p: number) {
  'worklet';
  return (Math.max(-0.02, Math.min(MOTION.overshoot, p)) * MOTION.angle * Math.PI) / 180;
}
