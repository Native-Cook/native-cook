// Split Amount Pad — the loop, the springs and the amount's rules, as named numbers and pure worklets.
//
// A port needs nothing else: the script below is the whole 9 s loop (the ghost finger presses the
// same keys a finger would, at these times), and the functions are the amount's arithmetic.

export const LOOP = 9;

/** key codes: 0…9 the digits, then */
export const KEY_DOT = 10;
export const KEY_BACK = 11;
export const KEY_CLEAR = 12;

/** most a bill may be, in cents */
export const LIMIT_CENTS = 250000;
export const MAX_INT_DIGITS = 4;
export const MAX_DECIMALS = 2;
export const FRIENDS = 4;

/** script rows: time (s), kind, argument, the time the ghost finger lifts */
export const DO_KEY = 0;
export const DO_FRIEND = 1;
/** fired by the long press the row before it began: no finger move of its own */
export const DO_CLEAR = 2;
export const SCRIPT = [
  [0.7, DO_KEY, 1, 0.7],
  [1.02, DO_KEY, 2, 1.02],
  [1.34, DO_KEY, 8, 1.34],
  [1.72, DO_KEY, KEY_DOT, 1.72],
  [2.04, DO_KEY, 4, 2.04],
  [2.36, DO_KEY, 0, 2.36],
  [4.1, DO_FRIEND, 2, 4.1],
  [5.9, DO_KEY, KEY_BACK, 6.32],
  [6.32, DO_CLEAR, 0, 6.32],
  [6.95, DO_FRIEND, 2, 6.95],
] as const;

/** a touch hands the pad back this long after it ends; then the amount clears and the loop restarts */
export const IDLE = 2;
export const RETURN = 0.7;
/** a held backspace clears the amount after this long */
export const HOLD_MS = 420;

/** springs (Reanimated: stiffness, damping, mass 1) and timings (ms) */
export const SPRING = {
  /** a digit dropping in: response 0.39 s, damping ratio 0.53 */
  drop: { stiffness: 260, damping: 17, mass: 1 },
  /** the amount re-centring: response 0.34 s, damping ratio 0.81 */
  slide: { stiffness: 340, damping: 30, mass: 1 },
  /** a share's digit rolling: response 0.42 s, damping ratio 0.81 */
  roll: { stiffness: 220, damping: 24, mass: 1 },
  /** an avatar toggling */
  toggle: { stiffness: 300, damping: 20, mass: 1 },
} as const;

export const TIME = {
  keyDown: 70,
  keyUp: 170,
  /** a digit flicked away */
  flick: 230,
  /** between digits leaving together (a clear), last first */
  flickStagger: 28,
  /** between digits arriving together ("0" and "." from one press) */
  dropStagger: 45,
  /** between friends' shares starting to roll, and between a share's digits (cents first) */
  rollFriend: 45,
  rollPlace: 18,
  /** one leg of the shake */
  shake: 55,
} as const;

/** a dropping digit starts this far above its place, this small and turned up to this much */
export const DROP = { rise: -24, scale: 0.6, turnMin: 6, turnSpan: 7 } as const;
/** a flicked digit ends this far to the right, having risen and fallen, turned and shrunk */
export const FLICK = { x: 26, up: -34, fall: 60, turn: 38, shrink: 0.35 } as const;
/** the shake's legs, pt: over the limit, and a softer "no" for a key that does nothing */
export const SHAKE_HARD = [-11, 9, -6, 3, 0] as const;
export const SHAKE_SOFT = [-5, 4, -2, 0] as const;

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

/** where the dot is in the typed codes, or -1 */
export function dotAt(codes: number[]) {
  'worklet';
  for (let i = 0; i < codes.length; i++) if (codes[i] === KEY_DOT) return i;
  return -1;
}

/** the typed codes as cents */
export function centsOf(codes: number[]) {
  'worklet';
  const dot = dotAt(codes);
  const whole = dot < 0 ? codes.length : dot;
  let cents = 0;
  for (let i = 0; i < whole; i++) cents = cents * 10 + codes[i];
  cents *= 100;
  if (dot >= 0) {
    if (dot + 1 < codes.length) cents += codes[dot + 1] * 10;
    if (dot + 2 < codes.length) cents += codes[dot + 2];
  }
  return cents;
}

/** split cents between the friends that are in: the first ones take the odd cents; -1 for one who sits out */
export function splitShares(cents: number, active: number[]) {
  'worklet';
  let count = 0;
  for (let i = 0; i < active.length; i++) if (active[i] > 0) count += 1;
  const out: number[] = [];
  const base = count > 0 ? Math.floor(cents / count) : 0;
  let odd = count > 0 ? cents - base * count : 0;
  for (let i = 0; i < active.length; i++) {
    if (active[i] > 0) {
      out.push(base + (odd > 0 ? 1 : 0));
      if (odd > 0) odd -= 1;
    } else {
      out.push(-1);
    }
  }
  return out;
}

/** which pad cell a key code sits in (rows of three: 1 2 3 / 4 5 6 / 7 8 9 / . 0 back) */
export function cellOf(code: number) {
  'worklet';
  if (code === 0) return 10;
  if (code === KEY_DOT) return 9;
  if (code === KEY_BACK) return 11;
  return code - 1;
}

/** which script row the ghost finger is heading for or pressing at t (DO_CLEAR rows are skipped) */
export function ghostRow(t: number) {
  'worklet';
  let last = 0;
  for (let i = 0; i < SCRIPT.length; i++) {
    if (SCRIPT[i][1] === DO_CLEAR) continue;
    last = i;
    if (t < SCRIPT[i][3] + 0.3) return i;
  }
  return last;
}

/** the row before it that has a finger move, or itself */
export function ghostPrev(row: number) {
  'worklet';
  for (let i = row - 1; i >= 0; i--) if (SCRIPT[i][1] !== DO_CLEAR) return i;
  return row;
}

/** how far the ghost has glided from the previous row's target to this one's, 0…1 */
export function ghostGlide(t: number, row: number) {
  'worklet';
  const at = SCRIPT[row][0];
  return smooth(seg(t, at - 0.27, at - 0.05));
}

export function ghostAlpha(t: number) {
  'worklet';
  let a = 0;
  for (let i = 0; i < SCRIPT.length; i++) {
    if (SCRIPT[i][1] === DO_CLEAR) continue;
    const v = smooth(seg(t, SCRIPT[i][0] - 0.32, SCRIPT[i][0] - 0.12)) * (1 - smooth(seg(t, SCRIPT[i][3] + 0.12, SCRIPT[i][3] + 0.36)));
    if (v > a) a = v;
  }
  return a;
}

/** how hard the ghost presses row i at t, 0…1 */
export function ghostPress(t: number, row: number) {
  'worklet';
  return smooth(seg(t, SCRIPT[row][0] - 0.07, SCRIPT[row][0])) * (1 - smooth(seg(t, SCRIPT[row][3] + 0.03, SCRIPT[row][3] + 0.19)));
}
