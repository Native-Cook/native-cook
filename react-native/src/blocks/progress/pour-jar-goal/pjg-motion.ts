// UI-thread solver and deterministic 120 Hz recording reference. All times are seconds.
export const LOOP = 8.0;
export const T = {
  restEnd: 0.5,
  holdEnd: 1.6,
  liftEnd: 2.8,
  settleEnd: 3.9,
  toastIn: 3.3,
  toastOut: 5.4,
  drainStart: 5.6,
  drainEnd: 7.2,
} as const;
export const SPRING = {
  snap: { damping: 30.5, stiffness: 322.3, mass: 1 },
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 },
  toast: { damping: 16, stiffness: 220, mass: 1 },
} as const;
export const PHYSICS = {
  columns: 64,
  rows: 128,
  hz: 120,
  speed: 520,
  damping: 3.2,
  lag: 0.09,
  fingerSigma: 22,
  fingerReach: 34,
  releaseCap: 900,
  skipThreshold: 1400,
  skipHeight: 40,
  resumeDelay: 2,
  returnSeconds: 0.6,
  callbackInterval: 0.1,
  rubberBand: 0.25,
  step: 10,
  start: 2240,
  max: 3100,
} as const;
export const EASING = { out: [0.2, 0.8, 0.2, 1], inOut: [0.65, 0, 0.35, 1] } as const;

export function clamp(x: number, lo = 0, hi = 1) {
  'worklet';
  return Math.min(hi, Math.max(lo, x));
}
export function smooth(x: number) {
  'worklet';
  const a = clamp(x);
  return a * a * (3 - 2 * a);
}
export function segment(t: number, a: number, b: number) {
  'worklet';
  return smooth((t - a) / (b - a));
}
export function springAt(t: number, stiffness = 220, damping = 16) {
  'worklet';
  if (t <= 0) return 0;
  const w = Math.sqrt(stiffness),
    z = damping / (2 * w),
    wd = w * Math.sqrt(Math.max(0.001, 1 - z * z));
  return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + ((z * w) / wd) * Math.sin(wd * t));
}
export function timeline(t: number) {
  'worklet';
  const s = ((t % LOOP) + LOOP) % LOOP;
  const rise = segment(s, T.holdEnd, T.liftEnd);
  // C1 at both ends, with the $2,31x / 75% payoff centred at 2.2 seconds.
  const lift = (1 - (1 - rise) * (1 - rise)) * (1 - segment(s, T.drainStart, T.drainEnd));
  const grip = segment(s, T.restEnd, T.restEnd + 0.14) * (1 - segment(s, T.liftEnd, T.liftEnd + 0.16));
  const toast = clamp(springAt(s - T.toastIn)) * (1 - segment(s, T.toastOut, T.toastOut + 0.34));
  return {
    t: s,
    lift,
    grip,
    toast,
    active: s > T.restEnd && s < T.drainEnd,
    bubbleTime: segment(s, 0.5, 0.8) * Math.min(Math.max(s - 0.5, 0), 4.8) * (1 - segment(s, 5.6, 7.2)),
    impact: Math.exp(-Math.pow((s - 2.94) / 0.2, 2)),
  };
}

export type WaveField = { h: number[]; v: number[]; work: number[]; velocityWork: number[]; output: number[]; n: number; energy: number };
export function makeField(): WaveField {
  return {
    h: Array(64).fill(0),
    v: Array(64).fill(0),
    work: Array(64).fill(0),
    velocityWork: Array(64).fill(0),
    output: Array(64).fill(0),
    n: 64,
    energy: 0,
  };
}

// Reflecting walls; a zero-mean displacement conserves the volume selected by the LUT.
export function stepField(
  f: WaveField,
  dt: number,
  width: number,
  damping: number,
  fingerX = -1000,
  mound = 0,
  secondX = -1000,
  columns = 64,
  secondMound = mound,
) {
  'worklet';
  const count = Math.round(clamp(columns, 32, 64));
  if (f.n !== count) {
    for (let i = 0; i < count; i++) {
      const u = (i / (count - 1)) * (f.n - 1),
        a = Math.floor(u),
        b = Math.min(f.n - 1, a + 1);
      f.work[i] = f.h[a] + (f.h[b] - f.h[a]) * (u - a);
      f.velocityWork[i] = f.v[a] + (f.v[b] - f.v[a]) * (u - a);
    }
    for (let i = 0; i < count; i++) {
      f.h[i] = f.work[i];
      f.v[i] = f.velocityWork[i];
    }
    f.n = count;
  }
  const dx = Math.max(8, width) / (count - 1),
    c2 = (PHYSICS.speed * PHYSICS.speed) / (dx * dx);
  // CFL-safe microsteps under the fixed 120 Hz outer clock.
  const substeps = Math.max(1, Math.ceil((PHYSICS.speed * dt) / dx / 0.75)),
    hdt = dt / substeps;
  for (let sub = 0; sub < substeps; sub++) {
    let mean = 0,
      velocityMean = 0,
      energy = 0;
    for (let i = 0; i < count; i++) {
      const l = f.h[i === 0 ? 1 : i - 1],
        r = f.h[i === count - 1 ? count - 2 : i + 1];
      const x = i * dx;
      const g = Math.exp(-Math.pow((x - fingerX) / PHYSICS.fingerSigma, 2)) + Math.exp(-Math.pow((x - secondX) / PHYSICS.fingerSigma, 2));
      const secondG = Math.exp(-Math.pow((x - secondX) / PHYSICS.fingerSigma, 2));
      const force = mound * (g - secondG) + secondMound * secondG;
      f.v[i] +=
        ((l + r - 2 * f.h[i]) * c2 -
          (damping + 18 * g * Math.min(1, Math.abs(mound))) * f.v[i] +
          (force - f.h[i]) * (mound || secondMound ? 1800 : 0)) *
        hdt;
      f.work[i] = clamp(f.h[i] + f.v[i] * hdt, -48, 48);
      mean += f.work[i];
      velocityMean += f.v[i];
    }
    mean /= count;
    velocityMean /= count;
    for (let i = 0; i < count; i++) {
      f.h[i] = f.work[i] - mean;
      f.v[i] = clamp(f.v[i] - velocityMean, -1200, 1200);
      energy = Math.max(energy, Math.abs(f.v[i]));
    }
    f.energy = energy;
  }
  // A stable 64-sample renderer interface, including a narrow-neck 32-column solver.
  let outputMean = 0;
  for (let i = 0; i < 64; i++) {
    const u = (i / 63) * (count - 1),
      a = Math.floor(u),
      b = Math.min(count - 1, a + 1);
    f.output[i] = f.h[a] + (f.h[b] - f.h[a]) * (u - a);
    outputMean += f.output[i];
  }
  for (let i = 0; i < 64; i++) f.output[i] -= outputMean / 64;
}
export function impulse(f: WaveField, x: number, velocity: number, width: number) {
  'worklet';
  for (let i = 0; i < f.n; i++)
    f.v[i] += clamp(velocity, -PHYSICS.releaseCap, PHYSICS.releaseCap) * Math.exp(-Math.pow(((i * width) / (f.n - 1) - x) / 14, 2));
}

export function makeLoopFields(width = 216) {
  const f = makeField(),
    frames: number[][] = [];
  for (let n = 0; n <= LOOP * PHYSICS.hz; n++) {
    const t = n / PHYSICS.hz,
      k = timeline(t);
    if (n === Math.round(T.liftEnd * PHYSICS.hz)) impulse(f, width / 2, 280, width);
    stepField(f, 1 / PHYSICS.hz, width, PHYSICS.damping, width / 2, k.grip * 24);
    const fade = 1 - segment(t, 6.6, 7.2);
    frames.push(f.output.map((v) => v * fade));
  }
  frames[0] = Array(64).fill(0);
  frames[960] = Array(64).fill(0);
  return frames;
}
export const LOOP_FIELDS = makeLoopFields();

export function rollingDigit(value: number, place: number, stagger = 0) {
  'worklet';
  const n = Math.max(0, value + stagger),
    q = n / place,
    base = Math.floor(q);
  // Only the final unit of the lower place rolls a higher place; 9 → 0 stays continuous.
  const phase = smooth(clamp((n % place) - (place - 1)));
  return (base % 10) + phase;
}
export function rubber(value: number, lower: number, upper: number) {
  'worklet';
  if (value < lower) return lower + ((value - lower) * PHYSICS.rubberBand) / (1 + Math.abs(value - lower) / 120);
  if (value > upper) return upper + ((value - upper) * 0.3) / (1 + Math.abs(value - upper) / 120);
  return value;
}
