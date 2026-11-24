// Scrub Number Field: the fields, the film and the odometer, as pure worklet functions.
//
// A field's value is one number, `pos`: the value counted in steps (Exposure +0.35 at 0.05 a step is
// pos 7). While a finger scrubs, pos is continuous; on release a spring snaps it to a whole step.
// The photo's shader, the ruler under the number and the rolling digits are all drawn from pos.
//
// No React Native imports here, so the module also runs in Node.

export const LOOP = 9; // seconds

// ---------------------------------------------------------------- the fields

export const FIELD_COUNT = 3;
export const EXPOSURE = 0;
export const WARMTH = 1;
export const VIGNETTE = 2;

export type FieldSpec = {
  label: string;
  symbol: string; // SF Symbol
  step: number; // value of one step
  unitsPerStep: number; // one step in units of the last digit shown (0.05 = 5 hundredths)
  decimals: number;
  signed: boolean;
  min: number; // in steps
  max: number;
  rest: number; // in steps: what the screen opens on, and what a double tap returns to
  peak: number; // in steps: where the film scrubs it to
  accessibilityUnit: string;
};

export const FIELDS: FieldSpec[] = [
  { label: 'Exposure', symbol: 'plusminus.circle', step: 0.05, unitsPerStep: 5, decimals: 2, signed: true, min: -40, max: 40, rest: 7, peak: 23, accessibilityUnit: 'stops' },
  { label: 'Warmth', symbol: 'thermometer.medium', step: 1, unitsPerStep: 1, decimals: 0, signed: true, min: -100, max: 100, rest: 12, peak: 46, accessibilityUnit: '' },
  { label: 'Vignette', symbol: 'circle.dashed', step: 1, unitsPerStep: 1, decimals: 0, signed: false, min: 0, max: 100, rest: 40, peak: 62, accessibilityUnit: 'percent' },
];
export const REST = FIELDS.map((f) => f.rest);
const PEAK = FIELDS.map((f) => f.peak);

/** The value a field shows at a whole step, e.g. "+0.35". */
export function formatValue(spec: FieldSpec, pos: number) {
  const v = Math.round(pos) * spec.step;
  const body = Math.abs(v).toFixed(spec.decimals);
  if (!spec.signed || Math.round(pos) === 0) return body;
  return `${v > 0 ? '+' : '−'}${body}`;
}

// ---------------------------------------------------------------- scrubbing

export const TICK = 8; // pt of ruler per step; at a slow drag the ruler follows the finger 1:1
export const MAJOR_EVERY = 5; // a longer tick every five steps
export const GAIN_MAX = 4; // a fast drag covers up to four steps per tick of travel
export const GAIN_FROM = 250; // pt/s where the drag starts to get coarser
export const GAIN_TO = 1100; // pt/s where it is coarsest
export const RUBBER = 5; // steps: how far past a limit the value can be pulled
export const PROJECT = 0.1; // s of release velocity carried into the snap
export const STRETCH_SPEED = 900; // pt/s of finger speed for the full stretch
export const STRETCH_MAX = 0.045; // the field is at most 4.5 % wider
export const SQUASH_MAX = 0.012; // and 1.2 % flatter
export const BLUR_FROM = 14; // steps/s where the digits start to smear
export const BLUR_TO = 90; // steps/s where the smear is full
export const BLUR_MAX = 7; // pt, vertical sigma
export const SPEED_TAU = 0.06; // s, smoothing of the measured speed
export const LIFT = 8; // pt the number rises while the ruler shows

// The snap on release (SwiftUI: .spring(response: 0.38, dampingFraction: 0.72)).
export const SNAP_RESPONSE = 0.38;
export const SNAP_DAMPING = 0.72;
const OMEGA = (2 * Math.PI) / SNAP_RESPONSE;
const OMEGA_D = OMEGA * Math.sqrt(1 - SNAP_DAMPING * SNAP_DAMPING);
const DECAY = SNAP_DAMPING * OMEGA;
/** The same spring for withSpring. */
export const SNAP_SPRING = { mass: 1, stiffness: OMEGA * OMEGA, damping: 2 * DECAY };

export const RESET_S = 0.8; // a double tap rolls the digits back, ease in-out cubic
export const RESET_STAGGER = 0.06; // s between the fields when all three go back

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function clamp(x: number, lo: number, hi: number) {
  'worklet';
  return x < lo ? lo : x > hi ? hi : x;
}

/** 0 before a, 1 after b. */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smooth(x: number) {
  'worklet';
  const c = clamp01(x);
  return c * c * (3 - 2 * c);
}

export function easeInOutCubic(x: number) {
  'worklet';
  const c = clamp01(x);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}

/** The snap spring from `from` towards `to`, s seconds after a release at rest. */
export function snapTo(from: number, to: number, s: number) {
  'worklet';
  if (s <= 0) return from;
  const a = from - to;
  const b = (DECAY * a) / OMEGA_D;
  return to + Math.exp(-DECAY * s) * (a * Math.cos(OMEGA_D * s) + b * Math.sin(OMEGA_D * s));
}

/** How many steps one tick of finger travel is worth at this finger speed (pt/s). */
export function scrubGain(speed: number) {
  'worklet';
  return 1 + (GAIN_MAX - 1) * smooth((speed - GAIN_FROM) / (GAIN_TO - GAIN_FROM));
}

/** A value pulled past its limits gives way less and less. */
export function rubber(raw: number, min: number, max: number) {
  'worklet';
  if (raw > max) {
    const over = raw - max;
    return max + over / (1 + over / RUBBER);
  }
  if (raw < min) {
    const over = min - raw;
    return min - over / (1 + over / RUBBER);
  }
  return raw;
}

/** Vertical blur of the rolling digits (pt) for a value moving at `speed` steps per second. */
export function digitBlur(speed: number) {
  'worklet';
  return BLUR_MAX * clamp01((speed - BLUR_FROM) / (BLUR_TO - BLUR_FROM));
}

// ---------------------------------------------------------------- the odometer

/**
 * Position of the last digit's strip for |value| = x (in units of that digit): continuous, in glyphs.
 * Its strip holds 10 / unitsPerStep glyphs (0 and 5 for Exposure, 0-9 otherwise).
 */
export function odoLow(x: number, unitsPerStep: number) {
  'worklet';
  const n = 10 / unitsPerStep;
  const p = x / unitsPerStep;
  return p - Math.floor(p / n) * n;
}

/**
 * Position of the strip of digit k >= 1 (tens of the last digit, hundreds, ...), unwrapped: it rests
 * on a whole digit and only turns during the last step before the digits below it wrap.
 */
export function odoHigh(x: number, k: number, unitsPerStep: number) {
  'worklet';
  const m = Math.pow(10, k);
  const base = Math.floor(x / m);
  const rem = x - base * m;
  const start = m - unitsPerStep;
  return base + (rem > start ? (rem - start) / unitsPerStep : 0);
}

export const ROLL_FROM = 0.25; // a digit strip turns while its position's fraction goes
export const ROLL_TO = 0.75; //   from ROLL_FROM to ROLL_TO, and rests on a whole digit otherwise

/** A strip position with the turn squeezed into the middle of each step. */
export function rollEase(v: number) {
  'worklet';
  const base = Math.floor(v);
  return base + smooth((v - base - ROLL_FROM) / (ROLL_TO - ROLL_FROM));
}

// ---------------------------------------------------------------- the film

// The ghost finger presses field i at DRAG_A[i] - PRESS_LEAD, drags from DRAG_A[i] to DRAG_B[i]
// (ease in-out cubic) and lifts; it lets go RELEASE_OFF steps past a detent, and the spring snaps back.
export const DRAG_A = [0.95, 3.0, 4.75];
export const DRAG_B = [2.35, 3.75, 5.75];
export const DRAG_PX = [128, 170, 140]; // pt the finger travels
export const PRESS_LEAD = 0.14; // s
export const RELEASE_OFF = 0.4; // steps
export const TAP_1 = 6.75; // the double tap on the photo
export const TAP_2 = 6.95;
export const RESET_T = 7.0; // the digits start rolling back

/** pos of field i at loop time t. */
export function filmPos(i: number, t: number) {
  'worklet';
  const b = DRAG_B[i];
  let p: number;
  if (t <= b) p = REST[i] + (PEAK[i] + RELEASE_OFF - REST[i]) * easeInOutCubic(seg(t, DRAG_A[i], b));
  else p = snapTo(PEAK[i] + RELEASE_OFF, PEAK[i], t - b);
  const start = RESET_T + i * RESET_STAGGER;
  const back = easeInOutCubic(seg(t, start, start + RESET_S));
  return p + (REST[i] - p) * back;
}

/** 0..1: field i is pressed (ruler shown, number lifted). */
export function filmActive(i: number, t: number) {
  'worklet';
  const a = DRAG_A[i];
  const b = DRAG_B[i];
  return smooth(seg(t, a - PRESS_LEAD, a - 0.02)) * (1 - smooth(seg(t, b, b + 0.3)));
}

/** pt the ghost finger has travelled on field i. */
export function filmFinger(i: number, t: number) {
  'worklet';
  return DRAG_PX[i] * easeInOutCubic(seg(t, DRAG_A[i], DRAG_B[i]));
}

/** -1..1: the field's stretch, from the ghost finger's speed. */
export function filmStretch(i: number, t: number) {
  'worklet';
  const dt = 1 / 120;
  const v = (filmFinger(i, t) - filmFinger(i, t - dt)) / dt;
  return clamp(v / STRETCH_SPEED, -1, 1);
}

/** Opacity of the ghost finger over field i. */
export function filmDotAlpha(i: number, t: number) {
  'worklet';
  const a = DRAG_A[i] - PRESS_LEAD;
  const b = DRAG_B[i];
  return smooth(seg(t, a - 0.2, a)) * (1 - smooth(seg(t, b + 0.05, b + 0.3)));
}

/** Opacity of the ghost finger over the photo, for the double tap. */
export function filmTapAlpha(t: number) {
  'worklet';
  return smooth(seg(t, TAP_1 - 0.3, TAP_1 - 0.08)) * (1 - smooth(seg(t, TAP_2 + 0.1, TAP_2 + 0.35)));
}

function tapBump(t: number, e: number) {
  'worklet';
  return smooth(seg(t, e - 0.07, e)) * (1 - smooth(seg(t, e + 0.03, e + 0.12)));
}

/** 0..1: how far the ghost finger is pressed during the double tap. */
export function filmTapPress(t: number) {
  'worklet';
  return Math.max(tapBump(t, TAP_1), tapBump(t, TAP_2));
}

export const MODE_AUTO = 0; // the film
export const MODE_MANUAL = 1; // a finger
export const MODE_RETURN = 2; // easing back to rest before the film restarts
