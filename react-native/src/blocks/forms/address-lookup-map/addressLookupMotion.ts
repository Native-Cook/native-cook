/*
 Address Lookup Map — the loop as a pure function of time.

 `autoPose(t, near)` gives every value the screen draws at loop time t (seconds, 0 ..< LOOP): the
 field, the suggestion rows, the camera over the drawn map, the pin, the filled fields and the
 ghost finger. A person drives the same values from shared values (AddressLookupMap.tsx), so the
 drawing code never knows which of the two is in charge.

 Timeline (s)            what happens
 0.00 – 0.90             rest: empty field, the town from above
 0.85                    ghost tap on the field, it takes the focus
 1.15 – 2.05             "48 Lark" typed, one key every 0.15 s; the suggestions rise in and re-rank
 2.85                    ghost tap on the first suggestion
 2.90 – 3.35             the row flies up into the field, the list folds away
 3.05 – 4.50             the map pans and zooms to the address (log-scale zoom, ease-in-out)
 4.40 – 5.00             the pin drops and bounces; two soft rings at 4.73 and 5.15
 4.95                    callout "Front door"
 5.00 – 5.58             unit, postcode, city fill in, 90 ms apart; the button wakes at 5.45
 5.95 – 7.25             ghost drag on the map: the map slides, the pin lifts and lands again
 7.30                    callout "Side gate · 9 m"
 8.40 – 9.40             everything empties, the map flies back out
 9.40 – 10.0             rest
*/

import { ADDRESSES, rank } from './addressLookupData';

export const LOOP = 10;
/** Seconds after the last touch before the screen eases back and the loop restarts. */
export const IDLE = 2;
export const RETURN_LEN = 0.8;
/** Loop time shown, without motion, under reduced motion. */
export const T_STILL = 6.18;

export const QUERY = '48 Lark';
/** The suggestion the loop picks (index into ADDRESSES). */
export const PICK = 0;

// ---- Timeline -----------------------------------------------------------------------------------
export const FIELD_TAP = 0.85;
const FOCUS_AT = 0.9;
const TYPE_AT = 1.15;
const KEY_GAP = 0.15;
export const PICK_AT = 2.85;
const CHOOSE_AT = 2.9;
export const CHOOSE_LEN = 0.45;
const FLY_AT = 3.05;
export const FLY_LEN = 1.45;
const DROP_AT = 4.4;
export const DROP_LEN = 0.6;
const RING_AT = [4.73, 5.15];
export const RING_LEN = 1.0;
const CALL_A_AT = 4.95;
const FILL_AT = 5.0;
export const FILL_GAP = 0.09;
export const FILL_LEN = 0.4;
const READY_AT = 5.45;
const DRAG_IN = 5.95;
const DRAG_AT = 6.2;
const DRAG_END = 7.0;
const CALL_B_AT = 7.3;
const RESET_AT = 8.4;
const CLEAR_AT = 8.75;
const BACK_AT = 8.55;
const BACK_LEN = 0.85;

/** Suggestion rows: height and how long a re-rank takes. */
export const ROW_H = 58;
const ROW_MOVE = 0.32;
const ROW_STAGGER = 0.035;

/** The ghost finger's drag on the map, pt on screen. */
export const DRAG_DX = -46;
export const DRAG_DY = -26;

// ---- Map camera -------------------------------------------------------------------------------
/** The map is turned a little so the grid does not read as a spreadsheet. */
export const MAP_ROT = (-8 * Math.PI) / 180;
export const OVERVIEW_X = 600;
export const OVERVIEW_Y = 650;
/** Overview: this many world units across the map card. */
export const OVERVIEW_SPAN = 1080;
/** Close-up scale (screen pt per world unit). */
export const NEAR = 1.05;
export const MIN_SCALE_K = 0.85;
export const MAX_SCALE = 2.2;

// ---- The script the React side follows (field text, chosen address) ---------------------------------
export type Step = { query: string; selected: number | null; filled: boolean };

const STEPS: Step[] = [{ query: '', selected: null, filled: false }];
const TIMES: number[] = [-1];
for (let k = 1; k <= QUERY.length; k += 1) {
  STEPS.push({ query: QUERY.slice(0, k), selected: null, filled: false });
  TIMES.push(TYPE_AT + (k - 1) * KEY_GAP);
}
STEPS.push({ query: ADDRESSES[PICK].street, selected: PICK, filled: false });
TIMES.push(CHOOSE_AT);
STEPS.push({ query: ADDRESSES[PICK].street, selected: PICK, filled: true });
TIMES.push(FILL_AT - 0.02);
STEPS.push({ query: '', selected: null, filled: false });
TIMES.push(CLEAR_AT);
export const SCRIPT = STEPS;
export const SCRIPT_TIMES = TIMES;
/** Script steps while the suggestions are open: 1 ..TYPED. */
export const TYPED = QUERY.length;

// Rows per script step: slot (row index, kept while hidden) and visibility. After the pick the list
// keeps its last rows (the pick folds them away); the clear empties it.
const N = ADDRESSES.length;
const LAST = STEPS.length - 1;
const LISTS: number[][] = STEPS.map((s, k) => (k === 0 || k === LAST ? [] : rank(k > TYPED ? QUERY : s.query)));
const ROW_Y: number[][] = [];
const ROW_A: number[][] = [];
for (let i = 0; i < N; i += 1) {
  const ys: number[] = [];
  const as: number[] = [];
  for (let k = 0; k < STEPS.length; k += 1) {
    const at = LISTS[k].indexOf(i);
    as.push(at >= 0 ? 1 : 0);
    ys.push(at);
  }
  // A hidden row keeps its last slot, or waits in the slot it will appear in.
  const known = ys.slice();
  for (let k = 0; k < ys.length; k += 1) {
    if (known[k] >= 0) continue;
    let prev = -1;
    for (let j = k - 1; j >= 0; j -= 1) {
      if (known[j] >= 0) {
        prev = known[j];
        break;
      }
    }
    let next = -1;
    for (let j = k + 1; j < ys.length; j += 1) {
      if (known[j] >= 0) {
        next = known[j];
        break;
      }
    }
    ys[k] = prev >= 0 ? prev : next >= 0 ? next : 0;
  }
  ROW_Y.push(ys);
  ROW_A.push(as);
}
const COUNTS = LISTS.map((l) => l.length);

// ---- Curves -----------------------------------------------------------------------------------
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
  const c = 1.4;
  const u = x - 1;
  return 1 + (c + 1) * u * u * u + c * u * u;
}

/** Height of the falling pin above the ground, pt (negative = up), from drop progress 0 ..1. */
export function dropY(p: number) {
  'worklet';
  if (p <= 0) return -84;
  if (p < 0.5) {
    const u = p / 0.5;
    return -84 * (1 - u * u);
  }
  if (p >= 1) return 0;
  // Two bounces, the second a third of the first.
  const u = (p - 0.5) / 0.5;
  if (u < 0.62) return -15 * Math.sin((Math.PI * u) / 0.62);
  return -5 * Math.sin((Math.PI * (u - 0.62)) / 0.38);
}

/** Squash of the pin's head as it touches down, 0 ..1. */
export function dropSquash(p: number) {
  'worklet';
  const d1 = Math.abs(p - 0.5) / 0.06;
  const d2 = Math.abs(p - 0.81) / 0.05;
  return Math.max(0, 1 - d1) + 0.35 * Math.max(0, 1 - d2);
}

/** Superposes the script's per-step targets: each change eases in from its own start time. */
function superpose(values: number[], t: number, len: number, stagger: number) {
  'worklet';
  let v = values[0];
  for (let j = 1; j < values.length; j += 1) {
    const d = values[j] - values[j - 1];
    if (d === 0) continue;
    const at = TIMES[j] + stagger;
    v += d * easeOutCubic(seg(t, at, at + len));
  }
  return v;
}

/** The filled fields empty in reverse order at the end of the loop. */
function fillOut(t: number, i: number) {
  'worklet';
  const at = RESET_AT + (2 - i) * 0.05;
  return 1 - seg(t, at, at + 0.25);
}

/** Index of the script step showing at loop time t. */
export function stepAt(t: number) {
  'worklet';
  let k = 0;
  for (let j = 1; j < TIMES.length; j += 1) if (t >= TIMES[j]) k = j;
  return k;
}

// ---- The pose ---------------------------------------------------------------------------------------
export type Pose = {
  focus: number;
  caret: number;
  /** The field's value and placeholder opacities. */
  text: number;
  placeholder: number;
  /** The suggestion card: open 0 ..1, rows it holds (fractional while it resizes). */
  list: number;
  count: number;
  /** 0 → 1 while the picked row flies into the field and the list folds. */
  choose: number;
  chosen: number;
  rowY: number[];
  rowA: number[];
  camX: number;
  camY: number;
  camS: number;
  /** Drop progress of the pin 0 ..1 (see dropY), and its opacity. */
  drop: number;
  pin: number;
  ring1: number;
  ring2: number;
  /** 0 → 1 while the map is held: the pin lifts off its spot. */
  lift: number;
  callA: number;
  callB: number;
  fill0: number;
  fill1: number;
  fill2: number;
  ready: number;
};

export type Dot = { target: number; alpha: number; scale: number; dx: number; dy: number };

export function overviewScale(mapW: number) {
  'worklet';
  return mapW / OVERVIEW_SPAN;
}

/** World offset of the camera when the map is dragged by (dx, dy) pt at scale s. */
export function dragToWorld(dx: number, dy: number, s: number) {
  'worklet';
  const c = Math.cos(-MAP_ROT);
  const n = Math.sin(-MAP_ROT);
  return [-(dx * c - dy * n) / s, -(dx * n + dy * c) / s];
}

export function autoPose(t: number, s0: number): Pose {
  'worklet';
  const target = ADDRESSES[PICK];
  const c = t < CLEAR_AT + 0.4 ? easeInOutCubic(seg(t, CHOOSE_AT, CHOOSE_AT + CHOOSE_LEN)) : 0;

  const rowY: number[] = [];
  const rowA: number[] = [];
  for (let i = 0; i < N; i += 1) {
    const y = superpose(ROW_Y[i], t, ROW_MOVE, 0);
    rowY.push(y);
    rowA.push(superpose(ROW_A[i], t, ROW_MOVE * 0.8, ROW_STAGGER * y));
  }

  // Camera: fly in, follow the ghost drag, fly back out.
  const fly = easeInOutCubic(seg(t, FLY_AT, FLY_AT + FLY_LEN));
  const back = easeInOutCubic(seg(t, BACK_AT, BACK_AT + BACK_LEN));
  const drag = easeInOutCubic(seg(t, DRAG_AT, DRAG_END));
  const off = dragToWorld(DRAG_DX * drag, DRAG_DY * drag, NEAR);
  const k = fly * (1 - back);
  const camX = OVERVIEW_X + (target.x + off[0] - OVERVIEW_X) * k;
  const camY = OVERVIEW_Y + (target.y + off[1] - OVERVIEW_Y) * k;
  const camS = s0 * Math.pow(NEAR / s0, k);

  const resetPin = seg(t, RESET_AT + 0.1, RESET_AT + 0.4);
  const typing = t - TIMES[Math.min(stepAt(t), QUERY.length)];
  const blink = typing < 0.5 ? 1 : clamp01(0.5 + 1.6 * Math.cos((2 * Math.PI * (typing - 0.5)) / 1.06));
  const textOut = t >= RESET_AT + 0.05 && t < CLEAR_AT + 0.15 ? 1 - seg(t, RESET_AT + 0.05, RESET_AT + 0.3) : 1;

  return {
    focus: easeOutCubic(seg(t, FOCUS_AT, FOCUS_AT + 0.2)) * (1 - seg(t, CHOOSE_AT + 0.1, CHOOSE_AT + 0.3)),
    caret: seg(t, FOCUS_AT + 0.15, FOCUS_AT + 0.25) * (1 - seg(t, CHOOSE_AT, CHOOSE_AT + 0.05)) * blink,
    text: (c > 0 && t < CLEAR_AT ? seg(c, 0.45, 0.95) : 1) * textOut,
    placeholder: t < CLEAR_AT ? 1 : seg(t, CLEAR_AT + 0.1, CLEAR_AT + 0.35),
    list: t < CLEAR_AT ? easeOutCubic(seg(t, TYPE_AT, TYPE_AT + 0.3)) : 0,
    count: superpose(COUNTS, t, ROW_MOVE, 0),
    choose: c,
    chosen: PICK,
    rowY,
    rowA,
    camX,
    camY,
    camS,
    drop: seg(t, DROP_AT, DROP_AT + DROP_LEN),
    pin: seg(t, DROP_AT, DROP_AT + 0.08) * (1 - resetPin),
    ring1: seg(t, RING_AT[0], RING_AT[0] + RING_LEN),
    ring2: seg(t, RING_AT[1], RING_AT[1] + RING_LEN),
    lift: easeOutCubic(seg(t, DRAG_AT, DRAG_AT + 0.18)) * (1 - easeOutBack(seg(t, DRAG_END, DRAG_END + 0.35))),
    callA: easeOutCubic(seg(t, CALL_A_AT, CALL_A_AT + 0.3)) * (1 - seg(t, DRAG_AT, DRAG_AT + 0.18)),
    callB: easeOutCubic(seg(t, CALL_B_AT, CALL_B_AT + 0.3)) * (1 - seg(t, RESET_AT, RESET_AT + 0.2)),
    fill0: easeOutCubic(seg(t, FILL_AT, FILL_AT + FILL_LEN)) * fillOut(t, 0),
    fill1: easeOutCubic(seg(t, FILL_AT + FILL_GAP, FILL_AT + FILL_GAP + FILL_LEN)) * fillOut(t, 1),
    fill2: easeOutCubic(seg(t, FILL_AT + FILL_GAP * 2, FILL_AT + FILL_GAP * 2 + FILL_LEN)) * fillOut(t, 2),
    ready: easeInOutCubic(seg(t, READY_AT, READY_AT + 0.4)) * (1 - seg(t, RESET_AT, RESET_AT + 0.35)),
  };
}

/** The ghost finger: taps the field, taps the first suggestion, then drags the map. */
export function autoDot(t: number): Dot {
  'worklet';
  let target = 0;
  let alpha = 0;
  let scale = 1;
  let dx = 0;
  let dy = 0;
  const taps = [FIELD_TAP, PICK_AT];
  for (let k = 0; k < taps.length; k += 1) {
    const at = taps[k];
    const a = seg(t, at - 0.24, at - 0.06) * (1 - seg(t, at + 0.12, at + 0.36));
    if (a > alpha) {
      alpha = a;
      target = k;
      scale = 1 - 0.22 * Math.sin(Math.PI * seg(t, at - 0.06, at + 0.16));
    }
  }
  const d = seg(t, DRAG_IN, DRAG_IN + 0.2) * (1 - seg(t, DRAG_END + 0.05, DRAG_END + 0.3));
  if (d > alpha) {
    alpha = d;
    target = 2;
    const p = easeInOutCubic(seg(t, DRAG_AT, DRAG_END));
    dx = DRAG_DX * p;
    dy = DRAG_DY * p;
    scale = 1 - 0.18 * seg(t, DRAG_AT - 0.12, DRAG_AT) * (1 - seg(t, DRAG_END, DRAG_END + 0.12));
  }
  return { target, alpha, scale, dx, dy };
}

/** Where a suggestion row sits for a query, for the person's own typing. */
export function slotsFor(query: string) {
  const list = rank(query);
  return ADDRESSES.map((_, i) => list.indexOf(i));
}
