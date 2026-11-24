/*
 Ghost Complete Field — the script and the clock of the demo.

 The loop is a pure function of the loop time `t` (seconds, 0 ..< LOOP). It produces the same
 `GhostPose` a person's typing produces through `useGhostComplete`, so the field never knows
 which of the two is driving. Positions are glyph counts and caret stops, never points: the
 field turns them into geometry with its own layout.
*/

export const LOOP = 10;
/** Seconds after the last touch before the screen eases back and the loop restarts. */
export const IDLE = 2;
export const RETURN_LEN = 0.6;

// ---- The script -----------------------------------------------------------------------------------
export const CC_TYPED = 'theo@';
export const CC_DOMAIN = 'postbox.co';
export const CC_FULL = CC_TYPED + CC_DOMAIN;
export const CC_CHIPS = ['postbox.co', 'northmail.com'];
export const BODY_TYPED_1 = 'Hi Mara, tha';
export const BODY_GHOST_1 = 'nks for the notes — ';
export const BODY_TYPED_2 = 'can we ';
export const BODY_GHOST_2 = 'walk through them Thursday?';
export const BODY_FULL = BODY_TYPED_1 + BODY_GHOST_1 + BODY_TYPED_2 + BODY_GHOST_2;
/** Glyph counts at the four seams of the body. */
const B1 = BODY_TYPED_1.length;
const B2 = B1 + BODY_GHOST_1.length;
const B3 = B2 + BODY_TYPED_2.length;
const B4 = BODY_FULL.length;
export const BODY_GHOSTS: [number, number][] = [
  [B1, B2],
  [B3, B4],
];
export const CC_GHOSTS: [number, number][] = [[CC_TYPED.length, CC_FULL.length]];

// ---- Timeline (seconds) ------------------------------------------------------------------------------
const KEY_GAP = 0.07;
const CC_TAP = 0.55;
const CC_TYPE = 0.75;
const CHIPS_AT = 1.2;
const PICK_AT = 1.85;
const PICK_LEN = 0.55;
const BODY_TAP = 2.55;
const TYPE_1 = 2.75;
const TYPE_1_END = TYPE_1 + (B1 - 1) * KEY_GAP;
/** The suggestion arrives a short pause after the last key, as it does for a person. */
const GHOST_1 = TYPE_1_END + 0.26;
const KEYCAP_1 = GHOST_1 + 0.38;
const KEY_TAP = 4.8;
const ACCEPT_1 = 4.85;
const TYPE_2 = 5.55;
const TYPE_2_END = TYPE_2 + (B3 - B2 - 1) * KEY_GAP;
const GHOST_2 = TYPE_2_END + 0.26;
const KEYCAP_2 = GHOST_2 + 0.36;
const SWIPE_IN = 7.05;
const SWIPE_AT = 7.25;
const RELEASE = 7.75;
const CLEAR_AT = 8.5;
const CLEAR_LEN = 0.35;
/** Loop time of the finished message with nothing moving: what reduced motion shows. */
export const T_FILLED = 8.2;

// ---- Motion constants shared by the loop and by real input ----------------------------------------------
/** A suggestion's words arrive this far apart, each over WORD_MS from a blur of WORD_BLUR. */
export const WORD_GAP = 0.07;
export const WORD_MS = 280;
export const WORD_BLUR = 5;
/** A rejected suggestion dissolves: it blurs out and fades over this long. */
export const DISSOLVE_MS = 200;
export const DISSOLVE_BLUR = 4;
/** The ink sweep over an accepted suggestion, and the width of its coloured front, pt. */
export const SWEEP_MS = 550;
export const SWEEP_BAND = 48;
/** The caret's jump to the end of the accepted text; it overshoots by about 3 % (ease-out-back). */
export const JUMP_MS = 320;
export const JUMP_BACK = 0.9;
export const KEYCAP_MS = 280;
/** Pause after the last key before a suggestion is offered. */
export const DEBOUNCE_MS = 240;
/** How far a swipe travels to fill the whole suggestion, pt, and the share that accepts on release. */
export const SWIPE_DIST = 180;
export const SWIPE_ACCEPT = 0.45;
/** The demo finger's swipe, pt, and the share of the sweep it reaches before letting go. */
export const DEMO_SWIPE = 150;
const DEMO_SWIPE_SHARE = 0.8;
/** A domain chip stretching into the field. */
export const PICK_MS = PICK_LEN * 1000;
export const CHIP_GAP = 0.07;
export const CHIP_MS = 260;

export type GhostPose = {
  /** Glyphs in ink: what has been typed (or accepted). */
  n: number;
  /** Which suggestion of the layout shows, -1 for none. */
  run: number;
  /** Seconds since that suggestion began to arrive. */
  appear: number;
  /** 1, or falling to 0 while a rejected suggestion dissolves. */
  fade: number;
  /** 0 → 1: the ink front crossing the suggestion. */
  sweep: number;
  /** The caret stands between two stops of the layout; `caretMix` may overshoot 1. */
  caretFrom: number;
  caretTo: number;
  caretMix: number;
  /** Caret opacity (focus and blink). */
  caret: number;
  /** 0 → 1: the keycap chip has popped. */
  keycap: number;
  keycapPress: number;
  /** Opacity of the text (the form emptying) and of the placeholder. */
  ink: number;
  hint: number;
};

export type ChipsPose = {
  /** Seconds since the chips began to pop; 0 or less hides them. */
  show: number;
  /** The chip that was picked, -1 for none. */
  pick: number;
  /** 0 → 1: that chip stretching into the field while the others leave. */
  take: number;
};

export type DotPose = {
  /** 0 Cc row, 1 first domain chip, 2 body, 3 keycap, 4 the swipe over the suggestion. */
  target: number;
  alpha: number;
  scale: number;
  /** 0 → 1 along the swipe. */
  travel: number;
};

export type ScreenPose = {
  /** 0 → 1: the send button is live (the message has text). */
  send: number;
};

// ---- Curves ----------------------------------------------------------------------------------------------
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

export function easeOutBack(x: number, c: number) {
  'worklet';
  const u = x - 1;
  return 1 + (c + 1) * u * u * u + c * u * u;
}

/** Keys typed by loop time t: one every `gap` from `at`, `max` in all. */
function keys(t: number, at: number, gap: number, max: number) {
  'worklet';
  if (t < at) return 0;
  return Math.min(max, Math.floor((t - at) / gap + 1e-6) + 1);
}

/** The caret holds for half a second after anything happens, then blinks. */
function blink(since: number) {
  'worklet';
  return since < 0.5 ? 1 : clamp01(0.5 + 1.6 * Math.cos((2 * Math.PI * (since - 0.5)) / 1.06));
}

// ---- The loop -----------------------------------------------------------------------------------------------

/** The text fades as the reply clears; once it is gone (and empty) it is opaque again for the next loop. */
function inkAt(t: number) {
  'worklet';
  return t >= CLEAR_AT + CLEAR_LEN ? 1 : 1 - seg(t, CLEAR_AT, CLEAR_AT + CLEAR_LEN);
}

/** Glyphs of the body in ink at loop time t. */
export function bodyCount(t: number) {
  'worklet';
  if (t >= CLEAR_AT + CLEAR_LEN) return 0;
  if (t >= RELEASE + 0.2) return B4;
  if (t >= ACCEPT_1 + SWEEP_MS / 1000) return B2 + keys(t, TYPE_2, KEY_GAP, B3 - B2);
  return keys(t, TYPE_1, KEY_GAP, B1);
}

/** Glyphs of the Cc address in ink at loop time t. */
export function ccCount(t: number) {
  'worklet';
  if (t >= CLEAR_AT + CLEAR_LEN) return 0;
  if (t >= PICK_AT + PICK_LEN) return CC_FULL.length;
  return keys(t, CC_TYPE, KEY_GAP, CC_TYPED.length);
}

export function autoBody(t: number): GhostPose {
  'worklet';
  const n = bodyCount(t);
  const accept1End = ACCEPT_1 + SWEEP_MS / 1000;
  const accept2End = RELEASE + 0.2;
  let run = -1;
  let appear = 0;
  let sweep = 0;
  let keycap = 0;
  let keycapPress = 0;
  if (t >= GHOST_1 && t < accept1End) {
    run = 0;
    appear = t - GHOST_1;
    sweep = easeInOutCubic(seg(t, ACCEPT_1, accept1End));
    keycap = easeOutBack(seg(t, KEYCAP_1, KEYCAP_1 + KEYCAP_MS / 1000), 1.6) * (1 - seg(t, ACCEPT_1, ACCEPT_1 + 0.14));
    keycapPress = Math.sin(Math.PI * seg(t, KEY_TAP - 0.04, KEY_TAP + 0.14));
  } else if (t >= GHOST_2 && t < accept2End) {
    run = 1;
    appear = t - GHOST_2;
    sweep = DEMO_SWIPE_SHARE * easeInOutCubic(seg(t, SWIPE_AT, RELEASE)) + (1 - DEMO_SWIPE_SHARE) * easeOutCubic(seg(t, RELEASE, accept2End));
    keycap = easeOutBack(seg(t, KEYCAP_2, KEYCAP_2 + KEYCAP_MS / 1000), 1.6) * (1 - seg(t, RELEASE, RELEASE + 0.14));
  }

  // The caret follows the typing, and jumps ahead of the ink when a suggestion is accepted.
  let caretFrom = n;
  let caretTo = n;
  let caretMix = 1;
  if (t >= ACCEPT_1 && t < accept1End) {
    caretFrom = B1;
    caretTo = B2;
    caretMix = easeOutBack(seg(t, ACCEPT_1, ACCEPT_1 + JUMP_MS / 1000), JUMP_BACK);
  } else if (t >= RELEASE && t < CLEAR_AT + CLEAR_LEN) {
    caretFrom = B3;
    caretTo = B4;
    caretMix = easeOutBack(seg(t, RELEASE, RELEASE + JUMP_MS / 1000), JUMP_BACK);
  }

  let last = BODY_TAP;
  if (t >= TYPE_1) last = Math.min(t, TYPE_1_END);
  if (t >= ACCEPT_1) last = Math.min(t, ACCEPT_1 + JUMP_MS / 1000);
  if (t >= TYPE_2) last = Math.min(t, TYPE_2_END);
  if (t >= SWIPE_AT) last = Math.min(t, RELEASE + JUMP_MS / 1000);
  const caret = seg(t, BODY_TAP, BODY_TAP + 0.12) * (1 - seg(t, CLEAR_AT - 0.1, CLEAR_AT)) * blink(t - last);

  const ink = inkAt(t);
  const hint = t < TYPE_1 ? 1 : seg(t, CLEAR_AT + CLEAR_LEN, CLEAR_AT + CLEAR_LEN + 0.3);
  return { n, run, appear, fade: 1, sweep, caretFrom, caretTo, caretMix, caret, keycap, keycapPress, ink, hint };
}

export function autoCc(t: number): GhostPose {
  'worklet';
  const n = ccCount(t);
  const pickEnd = PICK_AT + PICK_LEN;
  const picking = t >= PICK_AT && t < pickEnd;
  let caretFrom = n;
  let caretTo = n;
  let caretMix = 1;
  if (picking) {
    caretFrom = CC_TYPED.length;
    caretTo = CC_FULL.length;
    caretMix = easeOutBack(seg(t, PICK_AT + 0.08, PICK_AT + 0.08 + JUMP_MS / 1000), JUMP_BACK);
  }
  let last = CC_TAP;
  if (t >= CC_TYPE) last = Math.min(t, CC_TYPE + (CC_TYPED.length - 1) * KEY_GAP);
  if (t >= PICK_AT) last = t;
  const caret = seg(t, CC_TAP, CC_TAP + 0.12) * (1 - seg(t, BODY_TAP - 0.1, BODY_TAP)) * blink(t - last);
  const ink = inkAt(t);
  const hint = t < CC_TYPE ? 1 : seg(t, CLEAR_AT + CLEAR_LEN, CLEAR_AT + CLEAR_LEN + 0.3);
  return {
    n,
    run: picking ? 0 : -1,
    appear: 9,
    fade: 1,
    sweep: easeInOutCubic(seg(t, PICK_AT + 0.08, pickEnd)),
    caretFrom,
    caretTo,
    caretMix,
    caret,
    keycap: 0,
    keycapPress: 0,
    ink,
    hint,
  };
}

export function autoChips(t: number): ChipsPose {
  'worklet';
  if (t < CHIPS_AT || t >= PICK_AT + PICK_LEN) return { show: 0, pick: -1, take: 0 };
  return { show: t - CHIPS_AT, pick: t >= PICK_AT ? 0 : -1, take: seg(t, PICK_AT, PICK_AT + PICK_LEN) };
}

export function autoScreen(t: number): ScreenPose {
  'worklet';
  return { send: easeOutCubic(seg(t, TYPE_1, TYPE_1 + 0.3)) * (1 - seg(t, CLEAR_AT, CLEAR_AT + CLEAR_LEN)) };
}

const TAPS = [CC_TAP, PICK_AT, BODY_TAP, KEY_TAP];

/** The ghost finger: four taps, then a swipe to the right over the second suggestion. */
export function autoDot(t: number): DotPose {
  'worklet';
  if (t >= SWIPE_IN - 0.02) {
    return {
      target: 4,
      alpha: seg(t, SWIPE_IN, SWIPE_IN + 0.16) * (1 - seg(t, RELEASE, RELEASE + 0.2)),
      scale: 1 - 0.16 * seg(t, SWIPE_AT - 0.08, SWIPE_AT) * (1 - seg(t, RELEASE - 0.02, RELEASE + 0.1)),
      travel: easeInOutCubic(seg(t, SWIPE_AT, RELEASE)),
    };
  }
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
  return { target, alpha, scale, travel: 0 };
}
