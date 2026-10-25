import { Skia, type SkPath } from '@shopify/react-native-skia';

/*
 Sand PIN Code — digit strokes, the loop script and the grains, as pure functions of time.

 LOOP = 10 s
   0.0 – 1.25   rest: raked sand, six empty slots.
   1.25 – 3.81  the wrong code 4 8 2 7 1 5: each pebble squashes, a bamboo stylus carves the
                digit along its stroke in 0.26 s (ease-in-out), 0.46 s apart.
   3.95 – 4.40  the tray shakes (damped, 9 pt, 9 Hz).
   4.35 – 5.15  a seven-tine rake sweeps left to right over the slots; as it reaches each digit
                the groove crumbles into 50 grains that are pushed ahead, slide down and sink
                (0.9 s); fresh rake lines stay behind it.
   5.65 – 7.81  the right code 2 6 0 5 1 9, 0.38 s apart.
   7.85 – 8.85  unlock: a ring packet travels out through the sand from the slots' centre, the
                tray brightens, the grooves glow, the title turns to "Welcome back".
   8.80 – 9.50  the sand smooths over (grooves and rake lines fade), the light and title return.
   9.50 – 10    rest.
*/

export const LOOP = 10;
export const CODE = [2, 6, 0, 5, 1, 9];
const WRONG = [4, 8, 2, 7, 1, 5];
const WRONG_AT = 1.25;
const WRONG_STEP = 0.46;
const RIGHT_AT = 5.65;
const RIGHT_STEP = 0.38;
export const CARVE = 0.26;
const PRESS_LEAD = 0.08;
export const PRESS_LEN = 0.2;
export const SHAKE_LEN = 0.45;
const SHAKE_AT = 3.95;
export const RAKE_LEN = 0.8;
const RAKE_AT = 4.35;
export const GRAIN_LIFE = 0.9;
const UNLOCK_AT = 7.85;
const SMOOTH_AT = 8.8;
const SMOOTH_END = 9.5;

/** Slots: six, SLOT_GAP apart; a digit's stroke box is 24 × 34 scaled by DIGIT_SCALE. */
export const SLOTS = 6;
export const SLOT_GAP = 50;
export const SLOT_HALF_H = 30;
export const DIGIT_SCALE = 1.05;
export const SAMPLES = 50;
/** The rake starts and ends this far outside the slot row. */
export const RAKE_OVER = 80;

// Single-stroke digits in a 24 × 34 box, written the way a finger would draw them in sand.
const DIGITS = [
  'M12 2 C4.5 2 2 10 2 17 C2 25 5 32 12 32 C19 32 22 25 22 17 C22 9 19.5 2 12 2',
  'M6.5 7.5 L13 2 L13 32',
  'M3 9 C3 4.5 7 2 12 2 C17 2 21 5 21 10 C21 16 15.5 20.5 3 32 L22 32',
  'M4 5 C7 2.8 10 2 12.5 2 C17.5 2 20.5 5 20.5 9 C20.5 13 17 16 11 16 C18 16 22 19.5 22 24.5 C22 29.5 17.5 32 12 32 C8 32 5 31 2.5 28.5',
  'M16 32 L16 2 L2.5 23 L23 23',
  'M20.5 2 L6.5 2 L4.5 15 C7 13.6 9.5 13 12 13 C18 13 22 16.5 22 22.5 C22 28.5 17.5 32 12 32 C8 32 5 30.6 3 28',
  'M19 3.6 C17 2.6 15 2 13 2 C6 2 3 9 3 19 C3 27 6.5 32 12.5 32 C18 32 21.5 28 21.5 22.5 C21.5 17 18 13.5 12.5 13.5 C8 13.5 4.6 16 3.2 19.5',
  'M2 2 L22 2 C15 11 11 20 9 32',
  'M12 16 C7 16 4 13 4 9 C4 5 7.5 2 12 2 C16.5 2 20 5 20 9 C20 13 17 16 12 16 C6 16 2.5 19.5 2.5 24 C2.5 28.5 6.5 32 12 32 C17.5 32 21.5 28.5 21.5 24 C21.5 19.5 18 16 12 16',
  'M21 14 C19.5 17.5 16 20 11.5 20 C6 20 2.5 16.5 2.5 11 C2.5 5.5 6.5 2 12 2 C18 2 21.5 6.5 21.5 14 C21.5 24 17.5 32 10 32 C8 32 6 31.6 4.5 30.6',
];

export type DigitArt = {
  /** Each digit's stroke, centred on (0, 0). */
  paths: SkPath[];
  /** SAMPLES points evenly spaced along each stroke: [x0, y0, x1, y1, …]. */
  samples: number[][];
};

export function makeDigitArt(): DigitArt {
  const paths: SkPath[] = [];
  const samples: number[][] = [];
  for (const d of DIGITS) {
    const m = Skia.Matrix();
    m.scale(DIGIT_SCALE, DIGIT_SCALE);
    m.translate(-12, -17);
    const p = Skia.PathBuilder.MakeFromPath(Skia.Path.MakeFromSVGString(d)!).transform(m).build();
    paths.push(p);
    const it = Skia.ContourMeasureIter(p, false, 1);
    const contour = it.next();
    const pts: number[] = [];
    if (contour) {
      const len = contour.length();
      for (let i = 0; i < SAMPLES; i += 1) {
        const [pos] = contour.getPosTan((len * i) / (SAMPLES - 1));
        pts.push(pos.x, pos.y);
      }
    }
    samples.push(pts);
  }
  return { paths, samples };
}

/** The screen's state at one moment; arrays have one entry per slot. */
export type Sand = {
  digits: number[];
  carveAt: number[];
  /** When the rake swept over each slot's digit (−1: not swept). */
  passAt: number[];
  /** The digits that crumble into grains, and when (kept apart so a new code can be drawn while grains still settle). */
  grainDigits: number[];
  grainAt: number[];
  rakeX: number;
  rakeOn: number;
  rakeFrom: number;
  linesOn: number;
  shake: number;
  ripple: number;
  bright: number;
  unlock: number;
  /** Groove opacity (the sand smoothing over). */
  fade: number;
  /** Which key is squashed (−1 none) and how much. */
  key: number;
  press: number;
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

/** The tray's damped shake τ seconds after a wrong code. */
export function shakeAt(tau: number) {
  'worklet';
  if (tau < 0 || tau > SHAKE_LEN) return 0;
  return 9 * Math.sin(2 * Math.PI * 9 * tau) * Math.exp(-tau * 6.5) * (1 - tau / SHAKE_LEN);
}

/** A key's squash τ seconds after it is pressed: in fast, out on a soft bounce. */
export function pressAt(tau: number) {
  'worklet';
  if (tau < 0 || tau > PRESS_LEN + 0.25) return 0;
  if (tau < 0.06) return easeOutCubic(tau / 0.06);
  const u = (tau - 0.06) / (PRESS_LEN + 0.19);
  return Math.exp(-u * 4) * Math.cos(u * 7.5);
}

export function keyIndex(d: number) {
  'worklet';
  return d === 0 ? 10 : d - 1;
}

export function emptySand(): Sand {
  'worklet';
  return {
    digits: [-1, -1, -1, -1, -1, -1],
    carveAt: [-1, -1, -1, -1, -1, -1],
    passAt: [-1, -1, -1, -1, -1, -1],
    grainDigits: [-1, -1, -1, -1, -1, -1],
    grainAt: [-1, -1, -1, -1, -1, -1],
    rakeX: -999,
    rakeOn: 0,
    rakeFrom: 0,
    linesOn: 0,
    shake: 0,
    ripple: -1,
    bright: 0,
    unlock: 0,
    fade: 1,
    key: -1,
    press: 0,
  };
}

/** The loop at time t. `x0` is the first slot's centre x. */
export function autoSand(t: number, x0: number): Sand {
  'worklet';
  const s = emptySand();
  const from = x0 - RAKE_OVER;
  const to = x0 + (SLOTS - 1) * SLOT_GAP + RAKE_OVER;
  const passOf = (i: number) => RAKE_AT + (RAKE_LEN * (x0 + i * SLOT_GAP - from)) / (to - from);

  const right = t >= RIGHT_AT - 0.1;
  for (let i = 0; i < SLOTS; i += 1) {
    if (!right) {
      s.digits[i] = WRONG[i];
      s.carveAt[i] = WRONG_AT + i * WRONG_STEP;
      s.passAt[i] = passOf(i);
    } else if (t < SMOOTH_END) {
      s.digits[i] = CODE[i];
      s.carveAt[i] = RIGHT_AT + i * RIGHT_STEP;
    }
    s.grainDigits[i] = WRONG[i];
    s.grainAt[i] = passOf(i);
  }
  // Pebble presses.
  const at = right ? RIGHT_AT : WRONG_AT;
  const step = right ? RIGHT_STEP : WRONG_STEP;
  const code = right ? CODE : WRONG;
  for (let i = 0; i < SLOTS; i += 1) {
    const p = pressAt(t - (at + i * step - PRESS_LEAD));
    if (p !== 0) {
      s.key = keyIndex(code[i]);
      s.press = p;
    }
  }
  s.shake = shakeAt(t - SHAKE_AT);
  const r = seg(t, RAKE_AT, RAKE_AT + RAKE_LEN);
  s.rakeX = from + (to - from) * easeInOutCubic(r);
  s.rakeFrom = from;
  s.rakeOn = t < RAKE_AT ? 0 : t < RAKE_AT + RAKE_LEN ? clamp01((t - RAKE_AT) / 0.12) : 1 - seg(t, RAKE_AT + RAKE_LEN, RAKE_AT + RAKE_LEN + 0.25);
  if (t > RAKE_AT + RAKE_LEN + 0.25) s.rakeX = to + 200;
  s.linesOn = t >= RAKE_AT ? 1 : 0;
  s.ripple = t >= UNLOCK_AT ? t - UNLOCK_AT : -1;
  s.bright = easeOutCubic(seg(t, UNLOCK_AT, UNLOCK_AT + 0.4)) * (1 - easeInOutCubic(seg(t, SMOOTH_AT + 0.1, SMOOTH_END)));
  s.unlock = easeOutCubic(seg(t, UNLOCK_AT + 0.1, UNLOCK_AT + 0.5)) * (1 - easeInOutCubic(seg(t, SMOOTH_AT + 0.15, SMOOTH_AT + 0.6)));
  s.fade = 1 - easeInOutCubic(seg(t, SMOOTH_AT, SMOOTH_END));
  if (t >= SMOOTH_END) s.linesOn = 0;
  return s;
}

/** The user's take on the tray: digits and the times things happened, on the `now` clock. */
export type User = {
  sand: Sand;
  entered: number[];
  keyAt: number;
  shakeAt: number;
  rakeStart: number;
  fingerRake: number;
  checkAt: number;
  unlockAt: number;
  returnAt: number;
};

export function emptyUser(): User {
  'worklet';
  return { sand: emptySand(), entered: [], keyAt: -9, shakeAt: -9, rakeStart: -1, fingerRake: 0, checkAt: -1, unlockAt: -1, returnAt: -1 };
}

export function cloneUser(u: User): User {
  'worklet';
  const s = u.sand;
  return {
    ...u,
    entered: u.entered.slice(),
    sand: {
      ...s,
      digits: s.digits.slice(),
      carveAt: s.carveAt.slice(),
      passAt: s.passAt.slice(),
      grainDigits: s.grainDigits.slice(),
      grainAt: s.grainAt.slice(),
    },
  };
}
