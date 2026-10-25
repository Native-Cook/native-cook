/*
 Tide Gauge Chart — the numbers and pure functions shared by the screen, the shader and the buoy.

 Everything the loop shows is a function of the loop time t (seconds, 0 ..< LOOP):

   0.0 – 1.6   rest: the buoy floats at "now" (10:40), ripples travel along the surface.
   1.6 – 2.5   scrub back to 06:00 (ease-in-out); the water leans against the move.
   2.5 – 5.0   scrub forward through the high tide to 18:00; the surface sloshes at the turn.
   5.0 – 5.5   back to the high tide at 14:12 (ease-in-out), the slosh rings out.
   5.5 – 6.6   hold on the high tide: the reading glows.
   6.6 – 8.4   return to now (ease-in-out).
   8.4 – 9.0   rest; the last ripple of the slosh is faded out by 8.6 – 9.0 so t = 9 equals t = 0.
*/

export const LOOP = 9;

/** The chart spans one day, in hours. */
export const DAY = 24;
/** "Now" on the chart: 10:40. */
export const NOW = 10 + 40 / 60;
/** Water levels the plot shows, in metres (bottom and top of the plot). */
export const H_MIN = -0.4;
export const H_MAX = 3.0;

/** A semi-diurnal tide: high 2.4 m at 14:12, low 0.3 m at 08:05 (half a period earlier). */
export const TIDE_MEAN = 1.35;
export const TIDE_AMP = 1.05;
export const TIDE_HIGH_AT = 14.2;
export const TIDE_PERIOD = 12.23;

/** Extremes of the day (hours), where scrubbing past gives a selection tick. */
export const EXTREMES = [TIDE_HIGH_AT - TIDE_PERIOD, TIDE_HIGH_AT - TIDE_PERIOD / 2, TIDE_HIGH_AT, TIDE_HIGH_AT + TIDE_PERIOD / 2];

export const SUNRISE = 6 + 12 / 60;
export const SUNSET = 18 + 47 / 60;

/** Slosh: a damped oscillator (rad/s, damping ratio) driven by the scrub speed (pt of tilt per pt/s). */
export const SLOSH_OMEGA = 9;
export const SLOSH_ZETA = 0.3;
export const SLOSH_GAIN = 0.018;
export const SLOSH_MAX = 14;

/** After a touch: wait, then ease back to the loop's rest (s). */
export const IDLE_BEFORE_RETURN = 2;
export const RETURN_FOR = 0.8;

export type Plot = { x: number; y: number; w: number; h: number };

export function clamp(v: number, lo: number, hi: number) {
  'worklet';
  return Math.min(hi, Math.max(lo, v));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeInOutSine(x: number) {
  'worklet';
  return -(Math.cos(Math.PI * x) - 1) / 2;
}

export function tide(hour: number) {
  'worklet';
  return TIDE_MEAN + TIDE_AMP * Math.cos((2 * Math.PI * (hour - TIDE_HIGH_AT)) / TIDE_PERIOD);
}

/** Rising (+1) or falling (-1) at an hour. */
export function tideTrend(hour: number) {
  'worklet';
  return Math.sin((2 * Math.PI * (hour - TIDE_HIGH_AT)) / TIDE_PERIOD) < 0 ? 1 : -1;
}

/** The scrubbed hour of the loop at t. */
export function scriptHour(t: number) {
  'worklet';
  if (t < 1.6) return NOW;
  if (t < 2.5) return NOW + (6 - NOW) * easeInOut(seg(t, 1.6, 2.5));
  if (t < 5.0) return 6 + 12 * easeInOutSine(seg(t, 2.5, 5.0));
  if (t < 5.5) return 18 + (TIDE_HIGH_AT - 18) * easeInOut(seg(t, 5.0, 5.5));
  if (t < 6.6) return TIDE_HIGH_AT;
  if (t < 8.4) return TIDE_HIGH_AT + (NOW - TIDE_HIGH_AT) * easeInOut(seg(t, 6.6, 8.4));
  return NOW;
}

/** The glow on the reading while the loop holds on the high tide. */
export function scriptGlow(t: number) {
  'worklet';
  return easeInOutSine(seg(t, 5.3, 5.8)) * (1 - easeInOutSine(seg(t, 6.3, 6.9)));
}

/**
 * The loop's slosh at t: [tilt pt, chop 0..1]. Integrated from t = 0 with a fixed step every
 * frame, so it is a pure function of t; the tail is faded out before the loop closes.
 */
export function scriptSlosh(t: number, plotW: number): [number, number] {
  'worklet';
  const STEP = 1 / 60;
  const pxPerHour = plotW / DAY;
  let tilt = 0;
  let v = 0;
  let prev = scriptHour(0);
  let speed = 0;
  const n = Math.floor(t / STEP);
  for (let i = 1; i <= n; i += 1) {
    const h = scriptHour(i * STEP);
    speed = ((h - prev) / STEP) * pxPerHour;
    prev = h;
    const drive = clamp(-speed * SLOSH_GAIN, -SLOSH_MAX, SLOSH_MAX);
    const a = -SLOSH_OMEGA * SLOSH_OMEGA * (tilt - drive) - 2 * SLOSH_ZETA * SLOSH_OMEGA * v;
    v += a * STEP;
    tilt += v * STEP;
  }
  const fade = 1 - easeInOutSine(seg(t, 8.5, 9.0));
  return [tilt * fade, clamp(Math.abs(speed) / 700, 0, 1) * fade];
}

export function hourToX(hour: number, plot: Plot) {
  'worklet';
  return plot.x + (hour / DAY) * plot.w;
}

export function levelToY(h: number, plot: Plot) {
  'worklet';
  return plot.y + (1 - (h - H_MIN) / (H_MAX - H_MIN)) * plot.h;
}

/**
 * The water surface at screen x, exactly as the shader draws it: the tide curve, three travelling
 * ripples (whole numbers of cycles per loop), and the slosh tilt about the scrubber.
 */
export function surfaceY(x: number, plot: Plot, phase: number, scrubX: number, tilt: number, chop: number) {
  'worklet';
  const hour = ((x - plot.x) / plot.w) * DAY;
  let y = levelToY(tide(hour), plot);
  const T = 2 * Math.PI * phase;
  const amp = 1 + 1.6 * chop;
  y += amp * (1.1 * Math.sin(x * 0.071 - 3 * T) + 0.7 * Math.sin(x * 0.13 + 5 * T + 1.3) + 0.4 * Math.sin(x * 0.29 - 8 * T + 2.1));
  const d = clamp((x - scrubX) / (plot.w * 0.5), -1, 1);
  y += tilt * d;
  return y;
}

/** "14:12" for an hour of the day. */
export function clockText(hour: number) {
  'worklet';
  const m = Math.round(clamp(hour, 0, DAY - 1 / 60) * 60);
  const hh = Math.floor(m / 60);
  const mm = m % 60;
  return `${hh < 10 ? '0' : ''}${hh}:${mm < 10 ? '0' : ''}${mm}`;
}
