export const LOOP = 10;
export const WIDTH = 393;

export function clamp(value: number, low = 0, high = 1) {
  'worklet';
  return Math.max(low, Math.min(high, value));
}

export function mix(a: number, b: number, progress: number) {
  'worklet';
  return a + (b - a) * progress;
}

function curve(x: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  let low = 0;
  let high = 1;
  let t = x;
  for (let index = 0; index < 17; index += 1) {
    const q = 1 - t;
    const position = 3 * q * q * t * x1 + 3 * q * t * t * x2 + t * t * t;
    if (position < x) low = t;
    else high = t;
    t = (low + high) / 2;
  }
  const q = 1 - t;
  return 3 * q * q * t * y1 + 3 * q * t * t * y2 + t * t * t;
}

export function ramp(time: number, start: number, end: number, moving = false) {
  'worklet';
  const t = clamp((time - start) / (end - start));
  return moving ? curve(t, 0.77, 0, 0.175, 1) : curve(t, 0.23, 1, 0.32, 1);
}

export function sceneTime(progress: number, reduced: boolean) {
  'worklet';
  const p = clamp(progress);
  const t = p < 0.84 ? p : ((1 - p) / 0.16) * 0.68;
  return reduced ? (t < 0.22 ? 0 : t < 0.575 ? 0.4 : 0.72) : t;
}

export function stepAt(time: number): 1 | 2 | 3 {
  'worklet';
  return time < 0.22 ? 1 : time < 0.575 ? 2 : 3;
}

export function actorAt(time: number) {
  'worklet';
  const hero = ramp(time, 0.12, 0.32, true);
  const pinned = ramp(time, 0.47, 0.68, true);
  return {
    x: mix(mix(205, 77, hero), 172, pinned),
    y: mix(mix(157, 200, hero), 42, pinned),
    scale: mix(mix(0.455, 0.835, hero), 0.16, pinned),
    clip: 310 - 79 * (1 - ramp(time, 0.12, 0.185)),
  };
}

/** A cancelled interaction always settles to a complete, readable composition. */
export function nearestHold(progress: number, velocity = 0): 0 | 0.4 | 0.72 {
  'worklet';
  const projectedDistance = ((velocity / 1000) * 0.998) / (1 - 0.998);
  const projected = clamp(sceneTime(progress, false) + projectedDistance, 0, 0.72);
  return projected < 0.20 ? 0 : projected < 0.56 ? 0.4 : 0.72;
}
