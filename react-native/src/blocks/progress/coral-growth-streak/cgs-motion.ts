// Coral Growth Streak — the loop, the simulation's constants and the scripted week, in closed form.
//
// Everything a Swift or Kotlin port needs is a named number here. The coral itself is the one thing
// that is not a function of time: it is a Gray-Scott field stepped at SIM.stepHz from the seeds below.
// It is seeded on exact step numbers, so every loop grows the same reef, and it is dissolved in the
// view and then cleared at t = 0, so frame 10 s equals frame 0.

export const LOOP = 10;

/** the reaction-diffusion simulation (gpu/cgs-wgsl.ts runs it; CgsSkiaFallback only imitates its look) */
export const SIM = {
  /** grid cells per side; the reef window shows the inscribed circle */
  grid: 256,
  /** fixed steps a second, however fast the display refreshes (20 a frame at 60 Hz) */
  stepHz: 1200,
  /** feed and kill rates: the "coral growth" corner of the Gray-Scott map, a labyrinth that stops growing once it has filled its room */
  feed: 0.0545,
  kill: 0.062,
  /** diffusion of u and v per step, on the 9-point kernel (0.2 edges, 0.05 corners, -1 centre) */
  du: 1.0,
  dv: 0.5,
  /** outside the open radius the kill rate is raised by this much, which nothing survives */
  wallKill: 0.012,
  /** half width of that wall's edge, cells */
  wallWidth: 5,
  /** a seed: v += exp(-r^2 / radius^2), u -= half of it */
  seedRadius: 3,
  /** most steps one frame may run (a stall is caught up over the next frames, not in one spike) */
  maxSteps: 40,
  /** seeds one frame can carry */
  seedSlots: 8,
} as const;

export const DAYS = 7;

/** the loop's phases, seconds */
export const T = {
  /** day i is completed at first + i * every */
  first: 0.6,
  every: 0.9,
  /** a day's dot fills over this long (ease-out-back), the count rolls over the same time (ease-in-out) */
  dot: 0.42,
  /** the reef's open radius eases to the day's ring over this long in the fallback and the hint ring */
  ring: 0.7,
  /** the seventh day's bloom: a band of light crossing the reef from the centre */
  bloom: [7.0, 8.2],
  /** the coral dissolves from the rim inwards, the dots drain and the count rolls back */
  fade: [8.5, 9.35],
} as const;

/** a touch hands the reef back this long after it ends */
export const IDLE = 2;
/** then the coral dissolves over `ease` and the loop restarts at `total` */
export const RETURN = { ease: 0.85, total: 1.0 } as const;

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

/** 0 before a, 1 after b */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smooth(v: number) {
  'worklet';
  return v * v * (3 - 2 * v);
}

export function easeOutCubic(v: number) {
  'worklet';
  const u = 1 - v;
  return 1 - u * u * u;
}

/** overshoots to 1.1 at 0.7, settles on 1 */
export function easeOutBack(v: number) {
  'worklet';
  const u = v - 1;
  return 1 + 2.70158 * u * u * u + 1.70158 * u * u;
}

/** when day i (0…6) is completed, seconds */
export function dayTime(i: number) {
  'worklet';
  return T.first + i * T.every;
}

/** the streak shown at t, a real number that rolls from one count to the next */
export function countAt(t: number) {
  'worklet';
  let n = 0;
  for (let i = 0; i < DAYS; i++) n += smooth(seg(t, dayTime(i), dayTime(i) + T.dot));
  return n;
}

/** the open radius after d completed days, as a fraction of the reef's radius: equal areas a day, 6 % past the rim at seven */
export function ringFrac(d: number) {
  'worklet';
  return Math.sqrt(d / DAYS) * 1.06;
}

/** the same radius, eased from ring to ring (what the fallback draws) */
export function ringSoftAt(t: number) {
  'worklet';
  let r = 0;
  for (let i = 0; i < DAYS; i++) r += (ringFrac(i + 1) - ringFrac(i)) * smooth(seg(t, dayTime(i), dayTime(i) + T.ring));
  return r;
}

/** the bloom's travel 0…1 at t */
export function bloomAt(t: number) {
  'worklet';
  return seg(t, T.bloom[0], T.bloom[1]);
}

/** how much of the coral is shown at t: 1, then 0 after the fade */
export function levelAt(t: number) {
  'worklet';
  return 1 - smooth(seg(t, T.fade[0], T.fade[1]));
}

function hash1(n: number) {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
}

/** the seeds of day i (0…6) as x, y pairs in 0…1 of the grid: two at the centre, then a ring of 4…8 in the day's new band */
function seedsFor(day: number) {
  if (day === 0) return [0.5, 0.5, 0.59, 0.45];
  const out: number[] = [];
  const n = Math.min(SIM.seedSlots, 3 + day);
  const mid = ((ringFrac(day) + ringFrac(day + 1)) / 2 / 1.06) * 0.5 * 0.97;
  for (let j = 0; j < n; j++) {
    const a = (2 * Math.PI * (j + hash1(day * 7.3 + j))) / n + day * 0.9;
    const r = mid * (0.94 + 0.1 * hash1(day * 3.1 + j * 5.7));
    out.push(0.5 + r * Math.cos(a), 0.5 + r * Math.sin(a));
  }
  return out;
}

/** SEEDS[day] = x0, y0, x1, y1, … */
export const SEEDS: number[][] = [0, 1, 2, 3, 4, 5, 6].map(seedsFor);
/** the step each day's seeds go in on */
export const DAY_STEP: number[] = [0, 1, 2, 3, 4, 5, 6].map((i) => Math.round((T.first + i * T.every) * SIM.stepHz));
