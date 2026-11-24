/*
 Floating Label Field — the script and the clock of the demo.

 Everything the loop shows is a pure function of the loop time `t` (seconds, 0 ..< LOOP): the
 three fields' poses, the button and the ghost finger. The same poses are produced from shared
 values when a person types, so the field component never knows which of the two is driving.
*/

export const LOOP = 11;
/** Seconds after the last touch before the screen eases back and the loop restarts. */
export const IDLE = 2;
/** How long the ease back to the empty form takes before the loop restarts. */
export const RETURN_LEN = 0.75;

export const NAME = 'Mara Lindqvist';
export const EMAIL_TYPO = 'mara@larchco';
export const EMAIL = 'mara@larch.co';
export const PASSWORD = 'Tamarack42#';

// ---- Timeline (seconds) ---------------------------------------------------------------------
/** A field takes the focus / gives it up. Email keeps it through its error and the fix. */
export const FOCUS_AT = [0.85, 2.25, 5.05];
export const BLUR_AT = [2.25, 5.05, 8.35];
const NAME_TYPE_AT = 1.05;
const EMAIL_TYPE_AT = 2.5;
const KEY_GAP = 0.075;
/** Return is pressed on the mistyped address: shake, red outline, helper line. */
export const SUBMIT_AT = 3.55;
/** First backspace of the fix; the error clears with it. */
const FIX_AT = 4.35;
const PASSWORD_TYPE_AT = 5.3;
const PASSWORD_KEY_GAP = 0.15;
const READY_AT = 7.0;
const GLEAM_AT = 7.25;
const GLEAM_LEN = 0.8;
const TAP_AT = 8.35;
const DONE_AT = 8.5;
/** The form empties: values fade, then the labels sink back, 60 ms apart. */
const RESET_AT = 9.3;
const CLEAR_AT = 9.55;
const SINK_AT = 9.5;
/** Loop time of the filled form with nothing moving: what reduced motion shows. */
export const T_FILLED = 8.1;

// ---- Motion constants shared by the loop and by real input -----------------------------------
export const FOCUS_MS = 200;
export const FLOAT_MS = 420;
export const DRAW_MS = 600;
export const RETRACT_MS = 450;
export const CHECK_MS = 350;
export const ERROR_MS = 300;
export const ERROR_CLEAR_MS = 220;
export const SHAKE_MS = 450;
export const DOT_MS = 140;
export const STRENGTH_MS = 300;
export const READY_MS = 400;
export const GLEAM_MS = GLEAM_LEN * 1000;
/** Peak of the shake, pt, and its number of swings. */
const SHAKE_AMP = 9;
const SHAKE_SWINGS = 3;

export type FieldPose = {
  /** 0 → 1 while the field has the focus: accent label. */
  focus: number;
  /** 0 = the label is the placeholder, 1 = it sits in the notch. */
  float: number;
  /** The accent outline is the stretch of the border between these two (0 ..1 of its length). */
  lineStart: number;
  lineEnd: number;
  /** 0 → 1: the check is drawn. */
  valid: number;
  /** 0 → 1: error colour, helper line, badge. */
  error: number;
  /** Progress of the one shake, 0 → 1 (0 and 1 are both at rest). */
  shake: number;
  /** Index into the script's values while the loop types; -1 when a person does. */
  frame: number;
  /** Characters in a secure field, fractional while a dot arrives. */
  count: number;
  /** Opacity of the drawn caret (the loop's, and a secure field's). */
  caret: number;
  /** Password strength, 0 ..4, fractional while a segment fills. */
  strength: number;
  /** Opacity of the value while the form empties. */
  text: number;
};

export type ButtonPose = {
  /** 0 = disabled, 1 = enabled. */
  ready: number;
  /** Progress of the gleam across the enabled button, 0 → 1. */
  gleam: number;
  press: number;
  /** 0 → 1: the label gives way to the welcome line. */
  done: number;
};

export type DotPose = {
  /** Which target the ghost finger is over: 0–2 the fields, 3 the button. */
  target: number;
  alpha: number;
  /** 1 at rest, smaller while it presses. */
  scale: number;
};

// ---- Validation: the same rules for the script and for a person --------------------------------
export function nameOk(v: string) {
  return /^\s*\S{2,}\s+\S{2,}/.test(v);
}

export function emailOk(v: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v.trim());
}

/** 0 empty, 1 weak, 2 fair, 3 good, 4 strong. */
export function passwordLevel(v: string) {
  if (v.length === 0) return 0;
  let points = 0;
  if (v.length >= 6) points += 1;
  if (v.length >= 10) points += 1;
  if (/[a-z]/.test(v) && /[A-Z]/.test(v)) points += 1;
  if (/\d/.test(v)) points += 1;
  if (/[^A-Za-z0-9]/.test(v)) points += 1;
  if (points <= 1) return 1;
  if (points === 2) return 2;
  if (points <= 4) return 3;
  return 4;
}

export function fieldOk(i: number, v: string) {
  if (i === 0) return nameOk(v);
  if (i === 1) return emailOk(v);
  return passwordLevel(v) >= 3;
}

export function fieldMessage(i: number, v: string) {
  if (i === 0) return v.trim().length === 0 ? 'Enter your name' : 'Add your first and last name';
  if (i === 1) {
    if (v.trim().length === 0) return 'Enter your email';
    if (!v.includes('@')) return 'An email address needs an @';
    return 'That address needs a domain, like larch.co';
  }
  return v.length === 0 ? 'Choose a password' : 'Too easy to guess. Add a number or a symbol';
}

// ---- The script: every value a field holds, and when it starts to hold it -----------------------
function typed(text: string, at: number, gap: number): [string, number][] {
  const out: [string, number][] = [];
  for (let k = 1; k <= text.length; k += 1) out.push([text.slice(0, k), at + (k - 1) * gap]);
  return out;
}

const STEPS: [string, number][][] = [
  typed(NAME, NAME_TYPE_AT, KEY_GAP),
  [
    ...typed(EMAIL_TYPO, EMAIL_TYPE_AT, KEY_GAP),
    ['mara@larchc', FIX_AT],
    ['mara@larch', FIX_AT + 0.12],
    ['mara@larch.', FIX_AT + 0.29],
    ['mara@larch.c', FIX_AT + 0.39],
    [EMAIL, FIX_AT + 0.49],
  ],
  typed(PASSWORD, PASSWORD_TYPE_AT, PASSWORD_KEY_GAP),
];

/** VALUES[field][frame] and the loop time that frame starts at (frame 0 is the empty field). */
export const VALUES: string[][] = STEPS.map((s) => ['', ...s.map((x) => x[0])]);
export const TIMES: number[][] = STEPS.map((s) => [-1, ...s.map((x) => x[1])]);

/** When each field's value becomes valid for good. */
const VALID_AT: number[] = VALUES.map((values, i) => {
  let k = values.length - 1;
  if (!fieldOk(i, values[k])) return 1e9;
  while (k > 0 && fieldOk(i, values[k - 1])) k -= 1;
  return TIMES[i][k];
});

/** When the password reaches each strength level (index 0 unused). */
const LEVEL_AT: number[] = [0, 1, 2, 3, 4].map((level) => {
  const k = VALUES[2].findIndex((v) => passwordLevel(v) >= level);
  return k < 0 ? 1e9 : TIMES[2][k];
});

// ---- Curves ------------------------------------------------------------------------------------
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

export function easeOutBack(x: number) {
  'worklet';
  const c = 1.70158;
  const u = x - 1;
  return 1 + (c + 1) * u * u * u + c * u * u;
}

/** Sideways offset of the shake, pt, from its progress: three swings that die out. */
export function shakeX(p: number) {
  'worklet';
  if (p <= 0 || p >= 1) return 0;
  const u = 1 - p;
  return SHAKE_AMP * Math.sin(p * Math.PI * 2 * SHAKE_SWINGS) * u * u;
}

/** The script frame a field shows at loop time t. */
export function frameAt(i: number, t: number) {
  'worklet';
  if (t >= CLEAR_AT) return 0;
  const times = TIMES[i];
  let k = 0;
  for (let j = 1; j < times.length; j += 1) if (t >= times[j]) k = j;
  return k;
}

// ---- The loop ------------------------------------------------------------------------------------
export function autoField(i: number, t: number): FieldPose {
  'worklet';
  const f0 = FOCUS_AT[i];
  const b0 = BLUR_AT[i];
  const times = TIMES[i];
  const k = frameAt(i, t);
  const gone = 1 - seg(t, RESET_AT, RESET_AT + 0.25);
  const sink = 1 - easeInOutCubic(seg(t, SINK_AT + i * 0.06, SINK_AT + i * 0.06 + FLOAT_MS / 1000));

  let error = 0;
  let shake = 0;
  if (i === 1) {
    error = easeOutCubic(seg(t, SUBMIT_AT, SUBMIT_AT + ERROR_MS / 1000)) * (1 - seg(t, FIX_AT, FIX_AT + ERROR_CLEAR_MS / 1000));
    shake = seg(t, SUBMIT_AT, SUBMIT_AT + SHAKE_MS / 1000);
  }

  let count = 0;
  let strength = 0;
  if (i === 2) {
    for (let j = 1; j <= k; j += 1) count += easeOutCubic(seg(t, times[j], times[j] + DOT_MS / 1000));
    for (let level = 1; level <= 4; level += 1) strength += easeOutCubic(seg(t, LEVEL_AT[level], LEVEL_AT[level] + STRENGTH_MS / 1000));
    strength *= 1 - easeInOutCubic(seg(t, RESET_AT, RESET_AT + 0.3));
  }

  // The caret holds while keys land and blinks once they stop.
  const since = t - Math.max(times[k], f0);
  const blink = since < 0.5 ? 1 : clamp01(0.5 + 1.6 * Math.cos((2 * Math.PI * (since - 0.5)) / 1.06));
  const caret = seg(t, f0 + 0.22, f0 + 0.34) * (1 - seg(t, b0 - 0.04, b0 + 0.04)) * blink;

  return {
    focus: easeOutCubic(seg(t, f0, f0 + FOCUS_MS / 1000)) * (1 - seg(t, b0, b0 + FOCUS_MS / 1000)),
    float: easeOutCubic(seg(t, f0, f0 + FLOAT_MS / 1000)) * sink,
    lineStart: easeInOutCubic(seg(t, b0, b0 + RETRACT_MS / 1000)),
    lineEnd: easeInOutCubic(seg(t, f0, f0 + DRAW_MS / 1000)),
    valid: easeOutCubic(seg(t, VALID_AT[i], VALID_AT[i] + CHECK_MS / 1000)) * gone,
    error,
    shake,
    frame: k,
    count,
    caret,
    strength,
    // Once the field is empty its (now blank) value is opaque again, so the loop ends as it starts.
    text: t >= CLEAR_AT ? 1 : gone,
  };
}

export function autoButton(t: number): ButtonPose {
  'worklet';
  return {
    ready: easeInOutCubic(seg(t, READY_AT, READY_AT + READY_MS / 1000)) * (1 - easeInOutCubic(seg(t, RESET_AT + 0.15, RESET_AT + 0.55))),
    gleam: seg(t, GLEAM_AT, GLEAM_AT + GLEAM_LEN),
    press: seg(t, TAP_AT, TAP_AT + 0.1) * (1 - seg(t, TAP_AT + 0.12, TAP_AT + 0.3)),
    done: easeOutCubic(seg(t, DONE_AT, DONE_AT + 0.35)) * (1 - seg(t, RESET_AT, RESET_AT + 0.3)),
  };
}

const TAPS = [FOCUS_AT[0], FOCUS_AT[1], FOCUS_AT[2], TAP_AT];

/** The ghost finger: it fades in over a target, presses when the tap lands and fades out. */
export function autoDot(t: number): DotPose {
  'worklet';
  let target = 0;
  let alpha = 0;
  let scale = 1;
  for (let k = 0; k < TAPS.length; k += 1) {
    const at = TAPS[k];
    const a = seg(t, at - 0.24, at - 0.06) * (1 - seg(t, at + 0.12, at + 0.36));
    if (a > alpha) {
      alpha = a;
      target = k;
      scale = 1 - 0.22 * Math.sin(Math.PI * seg(t, at - 0.06, at + 0.16));
    }
  }
  return { target, alpha, scale };
}
