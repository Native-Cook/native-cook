/*
 Typewriter Sign Up — the loop script, as pure functions of the loop time.

 Every frame of autoPlay is `autoPose(t)` with t = elapsed % LOOP, so the frame at t = LOOP is
 the frame at t = 0 (a blank sheet in the carriage, carriage at column 0, line 0).

 LOOP = 10 s
   0.0 – 1.2   rest: blank sheet, typebar fan still.
   1.2 – 2.6   types "Mara Quill" (10 strikes, humanised spacing).
   2.55        the margin bell rings; 2.6 – 3.1 carriage return (ease-in-out) + line feed.
   3.1 – 5.0   types "mara@inkwell.co".
   4.95        bell; 5.0 – 5.4 carriage return + line feed.
   5.4 – 6.6   stamps eight password dots.
   6.75 – 6.95 the CTA is pressed.
   7.0 – 8.2   the sheet is pulled: 4 pt anticipation down, then up and out with a flex.
   8.2 – 8.4   empty carriage (form clears).
   8.4 – 9.4   a blank sheet feeds back in from above (ease-out, 3 pt settle).
   9.4 – 10    rest.
*/

export const LOOP = 10;

/** What the three fields type in the loop (the password is stamped as dots). */
export const SCRIPT = ['Mara Quill', 'mara@inkwell.co', '••••••••'] as const;
const LINE_START = [1.2, 3.1, 5.4];
const LINE_DUR = [1.4, 1.9, 1.2];
const RETURN_AT = [2.6, 5.0];
const RETURN_DUR = [0.5, 0.4];
const BELL_LEAD = 0.05;
export const PULL_START = 7.0;
const ANTICIPATION = 0.14;
export const PULL_END = 8.2;
const BACK_START = 8.4;
const BACK_END = 9.4;
const PRESS_AT = 6.75;
const PRESS_LEN = 0.2;
/** How far the sheet travels to leave the screen (pt). */
export const SHEET_AWAY = 680;

/** Typebar timing: up in 70 ms (ease-out), back in 110 ms. */
export const STRIKE_UP = 0.07;
export const STRIKE_DOWN = 0.11;
/** Number of typebars drawn in the fan. */
export const BARS = 9;

export type Pose = {
  /** Glyphs typed on each of the three lines. */
  n0: number;
  n1: number;
  n2: number;
  /** Line feed (0…2, fractional while the paper advances) and carriage column (glyph units). */
  line: number;
  col: number;
  /** Typebar extension 0…1 and which bar of the fan strikes. */
  strike: number;
  bar: number;
  /** Seconds since the newest glyph landed (ink spread), large when none. */
  inkAge: number;
  /** Bell wobble −1…1 and glow 0…1. */
  bell: number;
  bellGlow: number;
  /** Sheet offset upward in pt (negative = pushed down), and flex −1…1. */
  pull: number;
  flex: number;
  /** CTA press 0…1, the "pulled" label 0…1, the field text opacity. */
  press: number;
  done: number;
  fields: number;
};

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** A carriage step: a stiff, lightly underdamped spring from 0 to 1 (τ in seconds). */
export function carriageStep(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  return 1 - Math.exp(-30 * tau) * Math.cos(48 * tau);
}

/** The typebar's extension τ seconds after a key: up in 70 ms, back in 110 ms. */
export function strikeCurve(tau: number) {
  'worklet';
  if (tau < 0) return 0;
  if (tau < STRIKE_UP) return easeOutCubic(tau / STRIKE_UP);
  if (tau < STRIKE_UP + STRIKE_DOWN) return 1 - easeInOutCubic((tau - STRIKE_UP) / STRIKE_DOWN);
  return 0;
}

/** The bell τ seconds after it is hit: a damped wobble and a glow. */
export function bellCurve(tau: number): [number, number] {
  'worklet';
  if (tau < 0 || tau > 1.2) return [0, 0];
  const env = Math.exp(-5 * tau);
  return [Math.sin(tau * 42) * env, Math.exp(-3.2 * tau) * clamp01(tau / 0.04)];
}

/** Which typebar strikes for glyph k of line l. */
export function barFor(l: number, k: number) {
  'worklet';
  return Math.floor(hash(l * 97 + k * 13 + 5) * BARS);
}

/** When glyph k of line l is struck in the loop (humanised, still monotonic). */
function keyTime(l: number, k: number) {
  'worklet';
  const n = SCRIPT[l].length;
  return LINE_START[l] + (LINE_DUR[l] * (k + 0.5 + 0.38 * (hash(l * 31 + k) - 0.5))) / n;
}

function typedAt(l: number, t: number) {
  'worklet';
  const n = SCRIPT[l].length;
  let c = 0;
  for (let k = 0; k < n; k += 1) if (keyTime(l, k) <= t) c = k + 1;
  return c;
}

export function restPose(): Pose {
  'worklet';
  return {
    n0: 0, n1: 0, n2: 0, line: 0, col: 0, strike: 0, bar: 4, inkAge: 9,
    bell: 0, bellGlow: 0, pull: 0, flex: 0, press: 0, done: 0, fields: 1,
  };
}

/** The legible end state shown under reduced motion: all three lines typed. */
export function filledPose(): Pose {
  'worklet';
  const p = restPose();
  p.n0 = SCRIPT[0].length;
  p.n1 = SCRIPT[1].length;
  p.n2 = SCRIPT[2].length;
  p.line = 2;
  p.col = SCRIPT[2].length;
  return p;
}

export function autoPose(t: number): Pose {
  'worklet';
  const p = restPose();
  if (t >= PULL_END && t < BACK_START) {
    p.pull = SHEET_AWAY;
    p.done = 1;
    return p;
  }
  if (t >= BACK_START) {
    // A fresh blank sheet feeds in from above; the label returns.
    const u = seg(t, BACK_START, BACK_END);
    const e = easeOutCubic(u);
    const settle = u >= 1 ? 0 : -3 * Math.sin(Math.PI * clamp01((u - 0.7) / 0.3));
    p.pull = SHEET_AWAY * (1 - e) + settle;
    p.done = 1 - easeInOutCubic(seg(t, BACK_START, BACK_START + 0.6));
    return p;
  }
  p.n0 = typedAt(0, t);
  p.n1 = typedAt(1, t);
  p.n2 = typedAt(2, t);

  // Which line the carriage is on and where it stands.
  let l = 0;
  if (t >= RETURN_AT[1]) l = 2;
  else if (t >= RETURN_AT[0]) l = 1;
  const typedHere = l === 0 ? p.n0 : l === 1 ? p.n1 : p.n2;
  let lastKey = -1;
  if (typedHere > 0) lastKey = keyTime(l, typedHere - 1);

  let col = 0;
  let line = l;
  for (let r = 0; r < 2; r += 1) {
    const a = RETURN_AT[r];
    const d = RETURN_DUR[r];
    if (t >= a && t < a + d) {
      const u = easeInOutCubic(seg(t, a, a + d));
      col = SCRIPT[r].length * (1 - u);
      line = r + u;
      lastKey = -1;
    } else if (t >= a + d && l === r + 1) {
      // A little knock as the carriage hits its stop, then the new line's keys.
      const tau = t - (a + d);
      col = -0.32 * Math.exp(-12 * tau) * Math.sin(30 * tau);
      line = r + 1;
    }
  }
  if (lastKey >= 0) col = typedHere - 1 + carriageStep(t - lastKey);
  else if (l === 0) col = 0;
  p.col = col;
  p.line = line;

  if (lastKey >= 0) {
    const tau = t - lastKey;
    p.strike = strikeCurve(tau);
    p.inkAge = tau;
    p.bar = barFor(l, typedHere - 1);
  }
  for (let r = 0; r < 2; r += 1) {
    const b = bellCurve(t - (RETURN_AT[r] - BELL_LEAD));
    if (b[1] > p.bellGlow) {
      p.bell = b[0];
      p.bellGlow = b[1];
    }
  }

  // CTA press and the pull.
  const pr = seg(t, PRESS_AT, PRESS_AT + PRESS_LEN);
  p.press = Math.sin(Math.PI * pr);
  if (t >= PULL_START) {
    const ua = seg(t, PULL_START, PULL_START + ANTICIPATION);
    const up = seg(t, PULL_START + ANTICIPATION, PULL_END);
    p.pull = -4 * Math.sin(Math.PI * ua) * (up > 0 ? 0 : 1) + SHEET_AWAY * easeInOutCubic(up);
    p.flex = Math.sin(Math.PI * up);
    p.fields = 1 - easeOutCubic(seg(t, PULL_START, PULL_START + 0.5));
    p.done = easeOutCubic(seg(t, PULL_START + 0.4, PULL_START + 0.9));
  }
  return p;
}
