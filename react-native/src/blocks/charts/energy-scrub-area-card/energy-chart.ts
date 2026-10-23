import { Skia, type SkPath } from '@shopify/react-native-skia';

// The autoPlay film, in seconds from the start of a loop. A finger goes down on Monday, glides to
// Sunday and back to Wednesday, lifts, then Eco mode is switched on and off again.
export const LOOP = 9;
export const PRESS_AT = 0.8;
export const GLIDE_AT = 1.0;
export const GLIDE_FOR = 3.2;
export const BACK_FOR = 0.8;
export const LIFT_AT = 5.6;
export const ECO_ON_AT = 6.0;
export const ECO_OFF_AT = 7.6;
/** The line morphs over this long; the knob is quicker. */
export const MORPH_FOR = 0.8;
export const KNOB_FOR = 0.35;
export const DOT_IN = 0.3;
export const DOT_OUT = 0.25;
/** After the last touch the card waits this long, settles for RETURN_FOR, then plays again. */
export const USER_HOLD = 3;
export const RETURN_FOR = 0.45;
/** The days the autoPlay finger starts on, turns at and rests on (Monday is 0). */
export const START_DAY = 0;
export const TURN_DAY = 6;
export const REST_DAY = 2;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function easeInOut(t: number) {
  'worklet';
  const u = clamp01(t);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** Ease out with a small overshoot: the knob and the dot land like a spring. */
export function backOut(t: number) {
  'worklet';
  const u = clamp01(t);
  const c = 1.4;
  return 1 + (c + 1) * Math.pow(u - 1, 3) + c * Math.pow(u - 1, 2);
}

/** The x of a day's tick, as a fraction of the chart's width: days sit in the middle of seven columns. */
export function dayX(day: number) {
  'worklet';
  return (day + 0.5) / 7;
}

export type Film = {
  /** Where the cursor is, as a fraction of the chart's width. */
  cursor: number;
  /** The cursor's dot: 0 hidden, 1 shown (overshoots as it lands). */
  held: number;
  /** 1 while the finger is down: the header shows the scrubbed value. */
  down: number;
  /** The line between its own values (0) and the Eco values (1). */
  eco: number;
  knob: number;
};

/** The state of the card `t` seconds into a loop. Reduced motion jumps instead of gliding. */
export function filmAt(t: number, still: boolean): Film {
  'worklet';
  const down = t >= PRESS_AT && t < LIFT_AT ? 1 : 0;
  let cursor = dayX(START_DAY);
  let held = 0;
  if (still) {
    cursor = dayX(REST_DAY);
    held = down;
  } else {
    const glide = easeInOut((t - GLIDE_AT) / GLIDE_FOR);
    const back = easeInOut((t - GLIDE_AT - GLIDE_FOR) / BACK_FOR);
    cursor = dayX(START_DAY) + (dayX(TURN_DAY) - dayX(START_DAY)) * glide + (dayX(REST_DAY) - dayX(TURN_DAY)) * back;
    held = t < LIFT_AT ? (t < PRESS_AT ? 0 : backOut((t - PRESS_AT) / DOT_IN)) : 1 - clamp01((t - LIFT_AT) / DOT_OUT);
  }
  const on = still ? (t >= ECO_ON_AT && t < ECO_OFF_AT ? 1 : 0) : easeInOut((t - ECO_ON_AT) / MORPH_FOR) - easeInOut((t - ECO_OFF_AT) / MORPH_FOR);
  const knob = still ? on : backOut((t - ECO_ON_AT) / KNOB_FOR) - backOut((t - ECO_OFF_AT) / KNOB_FOR);
  return { cursor, held, down, eco: on, knob };
}

/** Values between two series of the same length, `t` of the way from `a` to `b`. */
export function mixSeries(a: number[], b: number[], t: number) {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < a.length; i++) out.push(a[i] + (b[i] - a[i]) * t);
  return out;
}

/**
 * The slope at each point, in value per step: the mean of its two neighbours' differences
 * (Catmull-Rom). It is linear in the values, so morphing the values morphs the curve exactly, and
 * the cursor can read the same curve the path draws.
 */
function slope(values: number[], i: number) {
  'worklet';
  const last = values.length - 1;
  if (i === 0) return values[1] - values[0];
  if (i === last) return values[last] - values[last - 1];
  return (values[i + 1] - values[i - 1]) / 2;
}

/** The curve's value at `x`, a fraction of the chart's width. */
export function valueAt(values: number[], x: number) {
  'worklet';
  const steps = values.length - 1;
  const at = clamp01(x) * steps;
  const i = Math.min(steps - 1, Math.floor(at));
  const t = at - i;
  const t2 = t * t;
  const t3 = t2 * t;
  return (
    (2 * t3 - 3 * t2 + 1) * values[i] +
    (t3 - 2 * t2 + t) * slope(values, i) +
    (-2 * t3 + 3 * t2) * values[i + 1] +
    (t3 - t2) * slope(values, i + 1)
  );
}

export type Frame = {
  width: number;
  /** The y of the range's top and bottom. */
  top: number;
  bottom: number;
  min: number;
  max: number;
};

export function yOf(value: number, frame: Frame) {
  'worklet';
  return frame.bottom - ((value - frame.min) / (frame.max - frame.min)) * (frame.bottom - frame.top);
}

/** The curve through the values as cubic Béziers; `floor` closes it down to that y for the fill. */
export function curvePath(values: number[], frame: Frame, floor: number | null): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  const steps = values.length - 1;
  const dx = frame.width / steps;
  builder.moveTo(0, yOf(values[0], frame));
  for (let i = 0; i < steps; i++) {
    const a = slope(values, i) / 3;
    const b = slope(values, i + 1) / 3;
    builder.cubicTo(
      i * dx + dx / 3,
      yOf(values[i] + a, frame),
      (i + 1) * dx - dx / 3,
      yOf(values[i + 1] - b, frame),
      (i + 1) * dx,
      yOf(values[i + 1], frame),
    );
  }
  if (floor !== null) builder.lineTo(frame.width, floor).lineTo(0, floor).close();
  return builder.build();
}
