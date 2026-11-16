/** All times are seconds. The source is still; this pressure choreography is ours. */
export const LOOP = 8.5;
export const T = { exhale: 0.7, empty: 3.9, result: 4.3, refill: 6.3, full: 7.9 } as const;
export const SPRING = {
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 },
  snap: { damping: 30.5, stiffness: 322.3, mass: 1 },
  inhale: { damping: 10, stiffness: 180, mass: 1 },
} as const;
export const TOUCH = { idle: 2, coast: 0.6, ramp: 0.25, rate: 0.42, rubber: 0.3, maxOver: 14, flowPeak: 1.13 } as const;

export function clamp(x: number, lo = 0, hi = 1) {
  'worklet';
  return Math.max(lo, Math.min(hi, Number.isFinite(x) ? x : lo));
}
export function smooth(x: number) {
  'worklet';
  const a = clamp(x);
  return a * a * (3 - 2 * a);
}
export function springAt(t: number, response: number, dampingRatio: number) {
  'worklet';
  if (t <= 0) return 0;
  const omega = (2 * Math.PI) / response;
  const wd = omega * Math.sqrt(Math.max(0.0001, 1 - dampingRatio * dampingRatio));
  return 1 - Math.exp(-dampingRatio * omega * t) * (Math.cos(wd * t) + ((dampingRatio * omega) / wd) * Math.sin(wd * t));
}
export function progressAt(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  if (t < T.exhale) return 0;
  if (t < T.empty) {
    const a = (t - T.exhale) / (T.empty - T.exhale);
    // Fast first second, zero velocity at either end; 0.62 at 2.2 s.
    return 1 - Math.pow(1 - smooth(a), 1.45);
  }
  if (t < T.refill) return 1;
  if (t < T.full) return 1 - smooth((t - T.refill) / (T.full - T.refill));
  return 0;
}
export function flowAt(t: number, capacity = 5.5) {
  'worklet';
  return clamp(((progressAt(t + 0.001) - progressAt(t - 0.001)) / 0.002) * capacity, -TOUCH.flowPeak, TOUCH.flowPeak);
}
export function elapsedAt(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  return clamp(t - T.exhale, 0, T.empty - T.exhale) * (1 - smooth((t - T.refill) / (T.full - T.refill)));
}
export function resultAt(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  return smooth((t - T.result) / 0.2) * (1 - smooth((t - T.refill) / 0.2));
}
export function lastAirAt(time: number) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  return smooth((t - (T.empty - 0.15)) / 0.15) * (1 - smooth((t - T.empty) / (T.result - T.empty)));
}
export function breatheAt(t: number) {
  'worklet';
  // Two complete breaths per loop. Twinkle uses the same loop circle, no seam.
  return 0.5 - 0.5 * Math.cos((4 * Math.PI * t) / LOOP);
}
export function thresholdAt(p: number, breath = 0) {
  'worklet';
  return 0.04 + 0.9 * Math.pow(clamp(p), 0.8) + 0.03 * breath * (1 - smooth(p * 8));
}
export function heldAt(start: number, elapsed: number) {
  'worklet';
  const integral = elapsed < TOUCH.ramp ? (elapsed * elapsed) / (2 * TOUCH.ramp) : elapsed - TOUCH.ramp / 2;
  return clamp(1 - Math.pow(Math.max(0, Math.pow(1 - clamp(start), 0.4) - 0.4 * TOUCH.rate * integral), 2.5));
}
export function coastAt(start: number, velocity: number, elapsed: number) {
  'worklet';
  const travel = velocity * TOUCH.coast * (1 - Math.exp(-elapsed / TOUCH.coast));
  if (velocity < 0) return clamp(start + travel);
  return clamp(1 - Math.pow(Math.max(0, Math.pow(1 - clamp(start), 0.4) - 0.4 * travel), 2.5));
}
export function rubberAt(p: number, travel: number) {
  'worklet';
  const bound = clamp(p);
  const over = (p - bound) * travel;
  return bound + (TOUCH.maxOver * Math.tanh((over * TOUCH.rubber) / TOUCH.maxOver)) / travel;
}
export function packPulse(elapsed: number) {
  'worklet';
  return elapsed >= 0 && elapsed < 0.24 ? Math.sin((Math.PI * elapsed) / 0.24) : 0;
}
/** Last integer-litre crossing on the deterministic exhale, found once at mount. */
export function litreTimes(capacity: number) {
  const out: number[] = [];
  for (let n = 1; n <= capacity; n++) {
    let a: number = T.exhale;
    let b: number = T.empty;
    for (let k = 0; k < 24; k++) {
      const m = (a + b) / 2;
      if (progressAt(m) * capacity < n) a = m;
      else b = m;
    }
    out.push((a + b) / 2);
  }
  return out;
}
