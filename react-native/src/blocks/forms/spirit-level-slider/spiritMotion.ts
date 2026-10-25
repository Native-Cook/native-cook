/*
 Spirit Level Slider — the tilt timeline and the bubble's physics. Pure functions of their inputs.

 autoPlay (LOOP = 8 s), level angle θ (degrees, right end up is positive):
   0.0 – 1.2   rest at 0°.
   1.2 – 3.0   tilt to 28° (ease-in-out, a 3 % dip the other way first: anticipation).
   3.0 – 4.4   hold 28° (6/12, Standard).
   4.4 – 5.4   overshoot up to 40° (ease-in-out).
   5.4 – 6.4   back down to 18° (ease-in-out).
   6.4 – 7.6   return to 0° (ease-in-out).
   7.6 – 8.0   rest.
 The bubble runs to the higher end of the vial on a spring (ω 7 rad/s, ζ 0.42): it lags the level,
 overshoots and settles; it is stretched along the vial by its speed and squashed against the
 vial's ends. The spring is integrated over four loops and the last kept, so the loop is exact.
 */

export const LOOP = 8;
/** The tilt range a finger can set (rubber-band beyond). */
export const MIN_ANGLE = -6;
export const MAX_ANGLE = 48;
export const BUBBLE_OMEGA = 7;
export const BUBBLE_ZETA = 0.42;
const TABLE_RATE = 120;

export const PITCH_WORDS = ['Flat', 'Low slope', 'Standard', 'Steep'];
const PITCH_BOUNDS = [5, 18, 37];

export function clamp(v: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max(v, a), b);
}

export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = clamp((x - a) / (b - a), 0, 1);
  return t * t * (3 - 2 * t);
}

/** The level's angle at loop time t. */
export function angleAt(t: number) {
  'worklet';
  if (t < 1.2) return 0;
  if (t < 3.0) {
    const u = seg(t, 1.2, 3.0);
    // A small dip before the lift (anticipation), gone by a quarter of the move.
    const dip = -0.9 * Math.sin(Math.PI * clamp(u / 0.25, 0, 1)) * (1 - u);
    return 28 * easeInOut(u) + dip;
  }
  if (t < 4.4) return 28;
  if (t < 5.4) return mix(28, 40, easeInOut(seg(t, 4.4, 5.4)));
  if (t < 6.4) return mix(40, 18, easeInOut(seg(t, 5.4, 6.4)));
  if (t < 7.6) return mix(18, 0, easeInOut(seg(t, 6.4, 7.6)));
  return 0;
}

/** Where the bubble wants to sit for a tilt, as a share of its travel (−1 … 1, past 1 = pinned). */
export function bubbleGoal(angle: number) {
  'worklet';
  return clamp(Math.sin((angle * Math.PI) / 180) / Math.sin((42 * Math.PI) / 180), -1.15, 1.15);
}

/** The bubble's travel (share) over one loop: [position, velocity] pairs, periodic. */
export function buildBubbleTable() {
  const n = LOOP * TABLE_RATE;
  const sub = 4;
  const dt = 1 / (TABLE_RATE * sub);
  let x = 0;
  let v = 0;
  const out: number[] = new Array(n * 2).fill(0);
  for (let loop = 0; loop < 4; loop += 1) {
    for (let i = 0; i < n; i += 1) {
      for (let s = 0; s < sub; s += 1) {
        const target = bubbleGoal(angleAt((i * sub + s) * dt));
        const a = -BUBBLE_OMEGA * BUBBLE_OMEGA * (x - target) - 2 * BUBBLE_ZETA * BUBBLE_OMEGA * v;
        v += a * dt;
        x += v * dt;
      }
      if (loop === 3) {
        out[i * 2] = x;
        out[i * 2 + 1] = v;
      }
    }
  }
  return out;
}

/** [position, velocity] of the bubble at loop time t. */
export function sampleBubble(table: number[], t: number) {
  'worklet';
  const n = table.length / 2;
  const x = (((t % LOOP) + LOOP) % LOOP) * TABLE_RATE;
  const i = Math.floor(x);
  const f = x - i;
  const a = (i % n) * 2;
  const b = ((i + 1) % n) * 2;
  return [mix(table[a], table[b], f), mix(table[a + 1], table[b + 1], f)];
}

/** One step of the touch-mode bubble spring: state = [position, velocity]. */
export function stepBubble(state: number[], target: number, dt: number) {
  'worklet';
  const steps = Math.max(1, Math.ceil(dt * 240));
  const h = dt / steps;
  let x = state[0];
  let v = state[1];
  for (let s = 0; s < steps; s += 1) {
    const a = -BUBBLE_OMEGA * BUBBLE_OMEGA * (x - target) - 2 * BUBBLE_ZETA * BUBBLE_OMEGA * v;
    v += a * h;
    x += v * h;
  }
  return [x, v];
}

/** The roof's rise per 12 of run for a tilt (0 … 13). */
export function riseOf(angle: number) {
  'worklet';
  return 12 * Math.tan((clamp(angle, 0, 50) * Math.PI) / 180);
}

/** The pitch word as a continuous index, rolling over ±1.5° around each boundary. */
export function pitchIndex(angle: number) {
  'worklet';
  let k = 0;
  for (let i = 0; i < PITCH_BOUNDS.length; i += 1) k += smooth(PITCH_BOUNDS[i] - 1.5, PITCH_BOUNDS[i] + 1.5, angle);
  return k;
}

/** Rests on whole numbers; rolls to the next across the middle 30 %. */
export function softRound(v: number) {
  'worklet';
  const f = Math.floor(v);
  return f + smooth(0.35, 0.65, v - f);
}

/** Rubber band past the tilt limits. */
export function rubber(angle: number) {
  'worklet';
  if (angle > MAX_ANGLE) return MAX_ANGLE + (angle - MAX_ANGLE) / (1 + (angle - MAX_ANGLE) / 8);
  if (angle < MIN_ANGLE) return MIN_ANGLE - (MIN_ANGLE - angle) / (1 + (MIN_ANGLE - angle) / 8);
  return angle;
}
