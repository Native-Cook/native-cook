/*
 Barometer Needle Chart — data, timeline and needle physics. Everything here is a pure function of
 its inputs, so the loop is exact and the ports can match it frame for frame.

 The chart spans 48 hours: −24 h (yesterday) to +24 h (forecast), "now" in the middle. The dial
 maps 960 → 1060 hPa onto a 270° arc, 1010 hPa at twelve o'clock.

 autoPlay (LOOP = 9 s):
   0.0 – 1.4   rest: the cursor at now (1012 hPa, Change).
   1.4 – 4.0   scrub back to −14 h through the storm dip (985 hPa, Stormy), ease-in-out.
   4.0 – 4.8   hold.
   4.8 – 7.0   scrub forward to +24 h (1024 hPa, Fair), ease-in-out.
   7.0 – 8.4   back to now, ease-in-out.
   8.4 – 9.0   rest (the needle settles on its spring; the table below is periodic, so t = 9 = t = 0).
 The needle follows the cursor's reading on a spring (ω 10 rad/s, ζ 0.38: ~25 % overshoot on a
 step) plus a faint jitter while it moves, like a real aneroid movement.
 */

export const LOOP = 9;
/** The chart's range in hours either side of now. */
export const SPAN = 24;
export const NOW_HPA = 1012;
export const SCALE_MIN = 960;
export const SCALE_MAX = 1060;
/** Degrees of dial arc for the whole scale; 0° is twelve o'clock, clockwise positive. */
export const SWEEP = 270;

/** Needle spring: natural frequency (rad/s) and damping ratio. */
export const NEEDLE_OMEGA = 10;
export const NEEDLE_ZETA = 0.38;
/** The needle table's sample rate (samples per second). */
const TABLE_RATE = 120;

// Hourly readings every 2 h from −24 h to +24 h (25 knots), hPa. The storm bottoms out at −14 h.
const KNOTS = [
  1005, 1002, 998, 993, 988, 985, 986, 990, 995, 1000, 1004.5, 1008.5, 1012, 1014.5, 1016.5, 1018, 1019.5, 1020.5, 1021.5,
  1022.2, 1022.8, 1023.3, 1023.7, 1024, 1024,
];

// Words printed on the dial and their bands (hPa): each word owns the span up to the next boundary.
export const WORDS = ['Stormy', 'Rain', 'Change', 'Fair', 'Very Dry'];
export const WORD_AT = [974, 997.5, 1011.5, 1026.5, 1047.5];
export const BOUNDS = [990, 1005, 1018, 1035];

export function clamp(v: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max(v, a), b);
}

export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** 0 before a, 1 after b, linear between. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp((t - a) / (b - a), 0, 1);
}

export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function smooth(a: number, b: number, x: number) {
  'worklet';
  const t = clamp((x - a) / (b - a), 0, 1);
  return t * t * (3 - 2 * t);
}

/** The reading at hour h (−24 … 24), a Catmull-Rom spline through the knots. */
export function pressureAt(h: number) {
  'worklet';
  const x = (clamp(h, -SPAN, SPAN) + SPAN) / 2;
  const i = Math.min(Math.floor(x), KNOTS.length - 2);
  const f = x - i;
  const p0 = KNOTS[Math.max(i - 1, 0)];
  const p1 = KNOTS[i];
  const p2 = KNOTS[i + 1];
  const p3 = KNOTS[Math.min(i + 2, KNOTS.length - 1)];
  const f2 = f * f;
  const f3 = f2 * f;
  return 0.5 * (2 * p1 + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f2 + (-p0 + 3 * p1 - 3 * p2 + p3) * f3);
}

/** Dial angle (degrees from twelve o'clock, clockwise) of a reading. */
export function angleOf(hpa: number) {
  'worklet';
  return ((hpa - (SCALE_MIN + SCALE_MAX) / 2) / (SCALE_MAX - SCALE_MIN)) * SWEEP;
}

/** The reading a needle angle points at. */
export function hpaOf(angle: number) {
  'worklet';
  return (SCALE_MIN + SCALE_MAX) / 2 + (angle / SWEEP) * (SCALE_MAX - SCALE_MIN);
}

/** The chart cursor (hours from now) at loop time t. */
export function cursorAt(t: number) {
  'worklet';
  if (t < 1.4) return 0;
  if (t < 4.0) return -14 * easeInOut(seg(t, 1.4, 4.0));
  if (t < 4.8) return -14;
  if (t < 7.0) return mix(-14, SPAN, easeInOut(seg(t, 4.8, 7.0)));
  if (t < 8.4) return mix(SPAN, 0, easeInOut(seg(t, 7.0, 8.4)));
  return 0;
}

/** A faint tremor of the movement while the reading changes (whole frequencies of the loop). */
export function jitterAt(t: number) {
  'worklet';
  const speed = clamp(Math.abs(cursorAt(t + 0.02) - cursorAt(t - 0.02)) / 0.04 / 30, 0, 1);
  const w = (2 * Math.PI) / LOOP;
  return 0.45 * speed * (Math.sin(37 * w * t) + 0.6 * Math.sin(53 * w * t + 1.3));
}

/**
 The needle angle over one loop, sampled at TABLE_RATE. The spring is integrated over four loops
 and the last one kept: a linear system driven by a periodic input settles on a periodic answer,
 so sample 0 follows the last sample without a jump.
 */
export function buildNeedleTable() {
  const n = LOOP * TABLE_RATE;
  const sub = 4;
  const dt = 1 / (TABLE_RATE * sub);
  let x = angleOf(pressureAt(0));
  let v = 0;
  const out: number[] = new Array(n).fill(0);
  for (let loop = 0; loop < 4; loop += 1) {
    for (let i = 0; i < n; i += 1) {
      for (let s = 0; s < sub; s += 1) {
        const t = (i * sub + s) * dt;
        const target = angleOf(pressureAt(cursorAt(t)));
        const a = -NEEDLE_OMEGA * NEEDLE_OMEGA * (x - target) - 2 * NEEDLE_ZETA * NEEDLE_OMEGA * v;
        v += a * dt;
        x += v * dt;
      }
      if (loop === 3) out[i] = x;
    }
  }
  return out;
}

/** The needle's spring angle at loop time t, read from the table (wrapping). */
export function sampleTable(table: number[], t: number) {
  'worklet';
  const n = table.length;
  const x = (((t % LOOP) + LOOP) % LOOP) * TABLE_RATE;
  const i = Math.floor(x);
  const f = x - i;
  return mix(table[i % n], table[(i + 1) % n], f);
}

/** One step of the touch-mode needle spring: state = [angle, velocity]. */
export function stepNeedle(state: number[], target: number, dt: number) {
  'worklet';
  const steps = Math.max(1, Math.ceil(dt * 240));
  const h = dt / steps;
  let x = state[0];
  let v = state[1];
  for (let s = 0; s < steps; s += 1) {
    const a = -NEEDLE_OMEGA * NEEDLE_OMEGA * (x - target) - 2 * NEEDLE_ZETA * NEEDLE_OMEGA * v;
    v += a * h;
    x += v * h;
  }
  return [x, v];
}

/**
 The forecast word for a reading as a continuous index: whole numbers inside a band, rolling to
 the next word over ±2 hPa around each boundary.
 */
export function wordIndex(hpa: number) {
  'worklet';
  let k = 0;
  for (let i = 0; i < BOUNDS.length; i += 1) k += smooth(BOUNDS[i] - 2, BOUNDS[i] + 2, hpa);
  return k;
}

/** Trend against the set hand: 0 falling, 1 steady, 2 rising, continuous between. */
export function trendIndex(hpa: number) {
  'worklet';
  return smooth(NOW_HPA - 4, NOW_HPA - 1.5, hpa) + smooth(NOW_HPA + 1.5, NOW_HPA + 4, hpa);
}

/** A reading rounded softly: rests on whole hPa, rolls to the next across the middle 30 %. */
export function softRound(v: number) {
  'worklet';
  const f = Math.floor(v);
  return f + smooth(0.35, 0.65, v - f);
}

/** Clock label for an hour offset from now (now = Sat 14:00). */
export function timeLabel(h: number) {
  'worklet';
  const DAYS = ['Fri', 'Sat', 'Sun'];
  const total = 24 + 14 + Math.round(h);
  const day = clamp(Math.floor(total / 24), 0, 2);
  const hour = ((total % 24) + 24) % 24;
  if (Math.round(h) === 0) return 'Now';
  return `${DAYS[day]} ${hour < 10 ? '0' : ''}${hour}:00`;
}
