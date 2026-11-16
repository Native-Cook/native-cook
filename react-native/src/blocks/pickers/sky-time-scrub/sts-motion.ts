import { COPY } from './sts-theme';

export const LOOP = 10;

/** One solar day, minutes since local midnight. Pass your own from `solarDay()` or a weather API. */
export type SolarModel = { sunrise: number; noon: number; sunset: number; maxElevation: number };
/** A solar day prepared for the UI thread. Build it once with `makeDay`; every worklet below reads it by value. */
export type Day = SolarModel & { lift: number; night: number };
export type Exposure = { adapt: boolean; up: number; down: number };
export type Timeline = {
  day: Day;
  now: number;
  /** Unwrapped minutes at the loop's keyframes: now, first horizon, deep night/day, second horizon, now a day later. */
  k: number[];
  /** Fixed-rate pre-integrated exposure (EV) response, sampled by every port. */
  ev: number[];
  /** Loop times at which the scrub crosses each schedule row's time. */
  ticks: number[][];
};

/** The invented place's day: sunrise 06:12, solar noon 12:31, sunset 18:18. The loop opens on golden hour at 18:06. */
export const DEMO_SOLAR: SolarModel = { sunrise: 372, noon: 751, sunset: 1098, maxElevation: 62 };
export const DEMO_NOW = 1086;
/** The 62° arch the dotted path and the sun's height were drawn for; a day with another noon height is scaled onto it. */
export const REF_MAX_ELEVATION = 62;
export const NIGHT_DEPTH = 45;
export const SOLAR = { ...DEMO_SOLAR, night: 1169, now: DEMO_NOW } as const;

export function makeDay(solar: SolarModel): Day {
  const rise = Math.min(Math.max(solar.sunrise, 1), 1437);
  const set = Math.min(Math.max(solar.sunset, rise + 2), 1439);
  const noon = Math.min(Math.max(solar.noon, rise + 1), set - 1);
  const maxElevation = Math.min(89, Math.max(1, solar.maxElevation));
  return {
    sunrise: rise,
    noon,
    sunset: set,
    maxElevation,
    lift: Math.sin((REF_MAX_ELEVATION * Math.PI) / 180) / Math.sin((maxElevation * Math.PI) / 180),
    night: set + 71,
  };
}
export const DEMO_DAY = makeDay(DEMO_SOLAR);

export const T = {
  press: 0.6,
  scrub: 0.9,
  sunset: 1.7,
  hold: 2.7,
  night: 3.5,
  dawn: 4.1,
  sunrise: 5.6,
  now: 7.6,
  rest: 8,
} as const;
export const SPRINGS = {
  snap: { damping: 30.5, stiffness: 322.3, mass: 1 },
  glide: { damping: 22.1, stiffness: 157.9, mass: 1 },
  detent: { damping: 22, stiffness: 260, mass: 1 },
} as const;
export const GESTURE = {
  minutesPerPoint: 2.5,
  longPress: 0.28,
  slop: 8,
  resumeAfter: 2,
  returnDuration: 0.6,
  decay: 6.018,
  detentDistance: 35,
  captureSpeed: 200,
  projection: 0.12,
  maxSpeed: 3600,
  exposureUp: 0.18,
  exposureDown: 0.6,
  step: 15,
  hapticGap: 0.1,
  holdRise: 0.08,
  holdFall: 0.12,
  maxFrameDelta: 0.05,
  stopSpeed: 2,
  hiddenThreshold: 0.001,
  accessibilityUpdate: 0.25,
  /** Grab radius around the sun disc or its night marker, points. */
  grabRadius: 24,
} as const;
/** The schedule rows and the hint pill trade places while the sky is scrubbed (seconds, except the alphas). */
export const ROWS = {
  max: 0.6,
  rise: 1,
  fall: 2,
  stagger: 0.04,
  fade: 0.22,
  rest: 0.55,
  pill: 0.18,
  tick: 0.3,
  tickScale: 0.04,
  crossing: 0.75,
  release: 7.8,
} as const;
/** Apparent semi-height of the sun disc in points, used for the limb haptics. */
export const SUN = { radius: 7.7, dip: 4, scale: 103, flatten: 0.72 } as const;

export function clamp(v: number, lo = 0, hi = 1) {
  'worklet';
  return Math.min(hi, Math.max(lo, v));
}
export function wrapMinutes(v: number) {
  'worklet';
  return ((v % 1440) + 1440) % 1440;
}
export function smooth(v: number) {
  'worklet';
  const x = clamp(v);
  return x * x * x * (x * (x * 6 - 15) + 10);
}
export function mix(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}
export function nearestDetent(m: number, detents: number[]) {
  'worklet';
  let best = m,
    distance = 1e9;
  for (let i = 0; i < detents.length; i++) {
    const d = detents[i];
    const candidate = d + Math.round((m - d) / 1440) * 1440;
    if (Math.abs(candidate - m) < distance) {
      best = candidate;
      distance = Math.abs(candidate - m);
    }
  }
  return { value: best, distance };
}
/** Day fraction along the dotted arch: 0 at sunrise, 0.5 at noon, 1 at sunset, continuing past both ends at the same rate. */
export function dayParam(minutes: number, day: Day) {
  'worklet';
  const m = wrapMinutes(minutes);
  return m <= day.noon ? ((m - day.sunrise) / (day.noon - day.sunrise)) * 0.5 : 0.5 + ((m - day.noon) / (day.sunset - day.noon)) * 0.5;
}
/** Inverse of `dayParam` on the unwrapped line; the sun's screen x is `86 + 230 * dayParam`. */
export function minutesFromDayParam(d: number, day: Day) {
  'worklet';
  return d <= 0.5 ? day.sunrise + (d / 0.5) * (day.noon - day.sunrise) : day.noon + ((d - 0.5) / 0.5) * (day.sunset - day.noon);
}
/** Minutes per unit of day fraction at a given fraction: the derivative of `minutesFromDayParam`. */
export function minutesPerDay(d: number, day: Day) {
  'worklet';
  return d <= 0.5 ? 2 * (day.noon - day.sunrise) : 2 * (day.sunset - day.noon);
}
export function solarAt(minutes: number, day: Day) {
  'worklet';
  const m = wrapMinutes(minutes);
  const d = dayParam(m, day);
  const x = 86 + 230 * d;
  let elevation: number;
  if (m >= day.sunrise && m <= day.sunset) {
    elevation = day.maxElevation * Math.sin(Math.PI * clamp(d));
  } else {
    const length = 1440 - (day.sunset - day.sunrise);
    const n = (m > day.sunset ? m - day.sunset : m + 1440 - day.sunset) / length;
    elevation = -NIGHT_DEPTH * Math.sin(Math.PI * clamp(n));
  }
  return { elevation, x, day: d };
}
/** The disc's centre height above the horizon in points: the arch's own scale, a dip for refraction near the horizon. */
export function sunHeight(elevation: number, day: Day, projection: number) {
  'worklet';
  const sy = Math.sin((elevation * Math.PI) / 180);
  return SUN.scale * Math.max(sy * day.lift, -0.5) * projection + SUN.dip * Math.exp(-Math.pow(elevation / 3, 2));
}
/** The flattened disc's semi-height at this elevation. */
export function sunSemiHeight(elevation: number) {
  'worklet';
  const x = clamp(elevation / 2);
  return SUN.radius * mix(SUN.flatten, 1, x * x * (3 - 2 * x));
}
/** Source arch height at x (reference points): rounded crest, flatter and steeper tails than a Gaussian. */
export function archY(x: number, centre: number, amplitude: number) {
  'worklet';
  const q = clamp((x - 201) / 201, -1, 1);
  const q2 = q * q;
  const exponent = 1.75 - 0.75 * q2 + 1.63 * q2 * q2;
  const base = Math.max(0.0001, Math.cos(q * 1.57079633));
  return centre + amplitude - 2 * amplitude * Math.pow(base, exponent);
}
/** Where the iris is heading for a sun at this elevation: dim in daylight, open to +0.9 EV in the dark. */
export function evTargetAt(elevation: number) {
  'worklet';
  return mix(-0.35, 0.9, smooth((-elevation + 1) / 15));
}
export function targetEV(minutes: number, day: Day) {
  'worklet';
  return evTargetAt(solarAt(minutes, day).elevation);
}
/** Shortest distance between two clock minutes around midnight. */
export function circularDiff(a: number, b: number) {
  'worklet';
  return Math.abs(((((a - b + 720) % 1440) + 1440) % 1440) - 720);
}

/** Resolves detent names to minutes on the given day. */
export function resolveDetents(day: Day, now: number, list?: ('sunrise' | 'noon' | 'sunset' | 'now' | number)[]) {
  const names = list ?? ['sunrise', 'noon', 'sunset', 'now'];
  const out: number[] = [];
  for (const entry of names) {
    const value =
      entry === 'sunrise' ? day.sunrise : entry === 'noon' ? day.noon : entry === 'sunset' ? day.sunset : entry === 'now' ? now : entry;
    if (Number.isFinite(value)) out.push(((value % 1440) + 1440) % 1440);
  }
  return out;
}

/** Keyframes of the autoplay loop for any day: now, the next horizon, deep into the dark or light, the one after, now again. */
function keyframes(day: Day, now: number) {
  const events: number[] = [];
  for (let k = 0; k < 3; k++) events.push(day.sunrise + 1440 * k, day.sunset + 1440 * k);
  events.sort((a, b) => a - b);
  const next = events.filter((e) => e > now + 0.5);
  const c1 = next[0],
    c2 = next[1];
  const mid = Math.min(c1 + 172, (c1 + c2) / 2);
  return [now, c1, mid, c2, now + 1440];
}

export function minutesAt(t: number, tl: Timeline) {
  'worklet';
  const k = tl.k;
  if (t <= T.scrub) return k[0];
  // After the coast the minute stays a day ahead (no false crossing at the seam); every consumer wraps it.
  if (t >= T.now) return k[4];
  if (t < T.sunset) return mix(k[0], k[1], smooth((t - T.scrub) / (T.sunset - T.scrub)));
  if (t < T.hold) return k[1];
  if (t < T.night) return mix(k[1], k[2], smooth((t - T.hold) / (T.night - T.hold)));
  if (t < T.dawn) return k[2];
  if (t < T.sunrise) {
    // A finger gathering speed: ease in, so the sun reaches the horizon at full speed and the iris cannot keep up.
    const u = (t - T.dawn) / (T.sunrise - T.dawn);
    return mix(k[2], k[3], u * u);
  }
  // The coast: the same speed the drag ended at, decaying to a stop on today's poster minute.
  const u = (t - T.sunrise) / (T.now - T.sunrise);
  return mix(k[3], k[4], 1 - (1 - u) * (1 - u));
}
export function heldAt(t: number) {
  'worklet';
  return smooth((t - T.press) / (T.scrub - T.press)) * (1 - smooth((t - T.now) / (T.rest - T.now)));
}
/** Seconds the schedule rows have been waking, 0 at rest up to `ROWS.max`; the pill and needle fade out as it grows. */
export function wakeAt(t: number) {
  'worklet';
  const up = clamp(t - ROWS.crossing, 0, ROWS.max);
  const down = clamp((t - ROWS.release) * ROWS.fall, 0, ROWS.max);
  return Math.max(0, up - down);
}
/** The hint: 1 at rest, 0 while the sky is scrubbed. It folds into the time chip as the press lands and unfolds
 * out of the ruler again once the sky is back at now (seconds). */
export const HINT = { leave: 0.35, arrive: 0.6, back: 8.1 } as const;
export function hintAt(t: number) {
  'worklet';
  return t < HINT.back ? 1 - smooth((t - T.press) / HINT.leave) : smooth((t - HINT.back) / HINT.arrive);
}
/** The row times the scrub ticks over, on any day. */
export function rowTimes(day: Day) {
  'worklet';
  return [day.noon, day.sunset, day.night];
}
function crossingTimes(day: Day, now: number, k: number[]) {
  const times = rowTimes(day);
  const probe: Timeline = { day, now, k, ev: [], ticks: [] };
  const out: number[][] = [[], [], []];
  const step = 1 / 240;
  let prev = minutesAt(0, probe);
  for (let i = 1; i <= LOOP / step; i++) {
    const t = i * step;
    const cur = minutesAt(t, probe);
    for (let r = 0; r < 3; r++) {
      if (Math.floor((prev - times[r]) / 1440) !== Math.floor((cur - times[r]) / 1440)) out[r].push(t);
    }
    prev = cur;
  }
  return out;
}
export function createTimeline(day: Day, now: number, exposure: Exposure): Timeline {
  const k = keyframes(day, wrapMinutes(now));
  const probe: Timeline = { day, now, k, ev: [], ticks: [] };
  const step = 1 / 120;
  const ev: number[] = [targetEV(now, day)];
  for (let i = 1; i <= LOOP / step; i++) {
    const target = targetEV(minutesAt(i * step, probe), day);
    const prev = ev[i - 1];
    const tau = target < prev ? exposure.up : exposure.down;
    ev.push(exposure.adapt ? prev + (target - prev) * (1 - Math.exp(-step / Math.max(tau, 1e-3))) : target);
  }
  return { day, now, k, ev, ticks: crossingTimes(day, now, k) };
}
export function exposureAt(t: number, tl: Timeline) {
  'worklet';
  const rest = targetEV(tl.now, tl.day);
  if (t >= T.rest || t <= T.press) return rest;
  const step = 1 / 120;
  const p = clamp(t / step, 0, tl.ev.length - 1),
    i = Math.floor(p);
  const ev = mix(tl.ev[i], tl.ev[Math.min(i + 1, tl.ev.length - 1)], p - i);
  const target = targetEV(minutesAt(t, tl), tl.day);
  return clamp(mix(ev, rest, smooth((t - T.now) / (T.rest - T.now))), target - 0.8, target + 1.2);
}
/** Seconds since the scrub last crossed row `r`'s time at loop time `t`; large when it has not yet. */
export function tickAge(t: number, tl: Timeline, r: number) {
  'worklet';
  const list = tl.ticks[r];
  let age = 99;
  for (let i = 0; i < list.length; i++) {
    if (list[i] <= t) age = t - list[i];
  }
  return age;
}
export function sampleTimeline(time: number, tl: Timeline) {
  'worklet';
  const t = ((time % LOOP) + LOOP) % LOOP;
  return {
    minutes: minutesAt(t, tl),
    ev: exposureAt(t, tl),
    held: heldAt(t),
    wake: wakeAt(t),
    tick0: tickAge(t, tl, 0),
    tick1: tickAge(t, tl, 1),
    tick2: tickAge(t, tl, 2),
    press: 0,
  };
}
export function timeLabel(minutes: number, day: Day, now: number, named = true) {
  'worklet';
  const m = wrapMinutes(Math.round(minutes));
  const h = Math.floor(m / 60)
    .toString()
    .padStart(2, '0');
  const clock = `${h}:${(m % 60).toString().padStart(2, '0')}`;
  if (!named) return clock;
  if (circularDiff(m, day.sunset) < 3) return `${COPY.sunset} · ${clock}`;
  if (circularDiff(m, day.sunrise) < 3) return `${COPY.sunrise} · ${clock}`;
  if (circularDiff(m, day.noon) < 3) return `${COPY.noon} · ${clock}`;
  if (circularDiff(m, now) < 3) return COPY.now;
  return clock;
}
