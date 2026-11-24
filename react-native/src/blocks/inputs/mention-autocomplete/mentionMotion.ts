/*
 Mention Autocomplete — the loop as a pure function of time.

 The React side follows the script's steps (what the composer holds: text, chips, which is
 selected); everything that moves between steps (the popover, the rows, the chip morph, the caret,
 the ghost finger) comes from `autoPose(t)` on the UI thread.

 Timeline (s)          what happens
 0.00 – 0.85           rest: empty composer
 0.55                  ghost tap on the composer
 0.85 – 1.21           "cc @" typed; the "@" opens the popover, which grows out of the caret
 1.60 – 1.96           "may" typed one letter at a time; rows filter and re-rank on springs
 2.45                  ghost tap on Maya Okafor
 2.50 – 2.95           the typed "@may" morphs into the chip "@Maya Okafor" and a space after it; the popover
                       shrinks into the caret
 3.10 – 4.00           "for review" typed after the space
 4.00 – 5.00           hold
 5.00 – 5.50           Backspace deletes "for review", then the space
 5.75                  Backspace on the chip selects it (filled accent)
 6.30 – 6.75           Backspace again breaks it back into text
 6.90 – 7.75           the line is deleted, faster and faster
 7.75 – 9.00           rest
*/

import { type Doc, EMPTY_DOC, PEOPLE, mention, rank } from './mentionData';

export const LOOP = 9;
export const IDLE = 2;
export const RETURN_LEN = 0.7;
/** Loop time shown, still, under reduced motion: the composed message with its chip. */
export const T_STILL = 4.5;

const PICKED = 2; // Maya Okafor
const PREFIX = 'cc ';
const TOKEN = '@may';
const SUFFIX = 'for review';
export const LABEL = mention(PEOPLE[PICKED]);
export const CHIP_START = PREFIX.length;
/** The typed letters the chip grows out of. */
export const MORPH_FROM = TOKEN;

// ---- Timeline -----------------------------------------------------------------------------------
export const FIELD_TAP = 0.55;
const FOCUS_AT = 0.6;
const TYPE_AT = 0.85;
const TYPE_GAP = 0.12;
const AT_AT = TYPE_AT + 3 * TYPE_GAP;
const QUERY_AT = 1.6;
const QUERY_GAP = 0.18;
export const PICK_AT = 2.45;
const CHIP_AT = 2.5;
export const MORPH_LEN = 0.45;
const MORPH_DONE = 3.0;
const SUFFIX_AT = 3.1;
const SUFFIX_GAP = 0.09;
const DELETE_AT = 5.0;
const DELETE_GAP = 0.05;
const SELECT_AT = 5.75;
export const SELECT_LEN = 0.16;
const BREAK_AT = 6.3;
export const BREAK_LEN = 0.45;
const CLEAR_AT = 6.9;
const CLEAR_END = 7.75;
const BLUR_AT = 7.95;

export const ROW_H = 54;
export const POP_HEAD = 30;
export const POP_PAD = 6;
const ROW_MOVE = 0.36;

/** How the chip is drawn at a step: grown from letters, a chip, selected, or breaking into text. */
export type ChipMode = 'none' | 'morph' | 'chip' | 'selected' | 'break';

export type Step = {
  doc: Doc;
  /** The mention being typed, for the popover; null while it is closed. */
  query: string | null;
  chip: ChipMode;
};

const STEPS: Step[] = [{ doc: EMPTY_DOC, query: null, chip: 'none' }];
const TIMES: number[] = [-1];
function push(at: number, step: Step) {
  STEPS.push(step);
  TIMES.push(at);
}
const typed = PREFIX + TOKEN;
for (let k = 1; k <= typed.length; k += 1) {
  const text = typed.slice(0, k);
  const at = k <= PREFIX.length + 1 ? TYPE_AT + (k - 1) * TYPE_GAP : QUERY_AT + (k - PREFIX.length - 2) * QUERY_GAP;
  const open = k >= PREFIX.length + 1;
  push(at, { doc: { text, chips: [], selected: -1 }, query: open ? text.slice(PREFIX.length + 1) : null, chip: 'none' });
}
const chipDoc = (text: string, selected: number): Doc => ({ text, chips: [{ start: CHIP_START, len: LABEL.length, person: PICKED }], selected });
const base = PREFIX + LABEL;
// A pick puts a space after the chip, so the caret waits after "@Maya Okafor " and the next word stands apart.
const spaced = base + ' ';
push(CHIP_AT, { doc: chipDoc(spaced, -1), query: null, chip: 'morph' });
push(MORPH_DONE, { doc: chipDoc(spaced, -1), query: null, chip: 'chip' });
for (let k = 1; k <= SUFFIX.length; k += 1) push(SUFFIX_AT + (k - 1) * SUFFIX_GAP, { doc: chipDoc(spaced + SUFFIX.slice(0, k), -1), query: null, chip: 'chip' });
// Backspace takes the words, then the space; only the Backspace after that reaches the chip.
for (let k = SUFFIX.length; k >= 0; k -= 1) {
  const text = k === 0 ? base : spaced + SUFFIX.slice(0, k - 1);
  push(DELETE_AT + (SUFFIX.length - k) * DELETE_GAP, { doc: chipDoc(text, -1), query: null, chip: 'chip' });
}
push(SELECT_AT, { doc: chipDoc(base, 0), query: null, chip: 'selected' });
push(BREAK_AT, { doc: { text: base, chips: [], selected: -1 }, query: null, chip: 'break' });
// The line goes, one character at a time, each gap shorter than the last.
for (let k = 1; k <= base.length; k += 1) {
  const u = k / base.length;
  const at = CLEAR_AT + (CLEAR_END - CLEAR_AT) * (1 - Math.pow(1 - u, 1.6));
  push(at, { doc: { text: base.slice(0, base.length - k), chips: [], selected: -1 }, query: null, chip: 'none' });
}
export const SCRIPT = STEPS;
export const SCRIPT_TIMES = TIMES;

// Popover rows per step: slot (kept while hidden) and visibility.
const N = PEOPLE.length;
const LISTS: number[][] = STEPS.map((s) => (s.query === null ? [] : rank(s.query)));
// After the pick the rows keep where they were; the popover itself closes.
let lastOpen: number[] = [];
const HELD: number[][] = LISTS.map((l) => {
  if (l.length > 0) lastOpen = l;
  return l.length > 0 ? l : lastOpen;
});
const ROW_Y: number[][] = [];
const ROW_A: number[][] = [];
for (let i = 0; i < N; i += 1) {
  const ys: number[] = [];
  const as: number[] = [];
  for (let k = 0; k < STEPS.length; k += 1) {
    ys.push(HELD[k].indexOf(i));
    as.push(LISTS[k].indexOf(i) >= 0 ? 1 : 0);
  }
  const known = ys.slice();
  for (let k = 0; k < ys.length; k += 1) {
    if (known[k] >= 0) continue;
    let fill = -1;
    for (let j = k - 1; j >= 0 && fill < 0; j -= 1) if (known[j] >= 0) fill = known[j];
    for (let j = k + 1; j < ys.length && fill < 0; j += 1) if (known[j] >= 0) fill = known[j];
    ys[k] = fill < 0 ? 0 : fill;
  }
  ROW_Y.push(ys);
  ROW_A.push(as);
}
// The popover keeps its last size while it closes; it is rest-sized again before it next opens.
const COUNTS = HELD.map((l) => l.length);
const OPEN_FLAGS = STEPS.map((s) => (s.query === null ? 0 : 1));
const TEXT_FLAGS = STEPS.map((s) => (s.doc.text.length > 0 ? 1 : 0));

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
  const c = 1.5;
  const u = x - 1;
  return 1 + (c + 1) * u * u * u + c * u * u;
}

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

/** Spring-like settle for row slots: a little overshoot, as the person's springs have. */
function superposeSpring(values: number[], t: number, len: number) {
  'worklet';
  let v = values[0];
  for (let j = 1; j < values.length; j += 1) {
    const d = values[j] - values[j - 1];
    if (d === 0) continue;
    v += d * easeOutBack(seg(t, TIMES[j], TIMES[j] + len));
  }
  return v;
}

export function stepAt(t: number) {
  'worklet';
  let k = 0;
  for (let j = 1; j < TIMES.length; j += 1) if (t >= TIMES[j]) k = j;
  return k;
}

// ---- The pose -------------------------------------------------------------------------------------
export type Pose = {
  focus: number;
  caret: number;
  placeholder: number;
  /** The send button wakes when there is text. */
  send: number;
  /** Popover: 0 → 1 grows out of the caret (overshoots a little), back to 0 when it closes. */
  pop: number;
  count: number;
  rowY: number[];
  rowA: number[];
  /** Chip: grown from the typed letters 0 → 1; selected 0 → 1; breaking into text 0 → 1. */
  morph: number;
  select: number;
  breakUp: number;
};

export type Dot = { target: number; alpha: number; scale: number };

export function autoPose(t: number): Pose {
  'worklet';
  const k = stepAt(t);
  const rowY: number[] = [];
  const rowA: number[] = [];
  for (let i = 0; i < N; i += 1) {
    rowY.push(superposeSpring(ROW_Y[i], t, ROW_MOVE));
    rowA.push(superpose(ROW_A[i], t, ROW_MOVE * 0.7, 0));
  }
  const open = easeOutBack(seg(t, AT_AT, AT_AT + 0.36));
  const close = easeInOutCubic(seg(t, CHIP_AT, CHIP_AT + 0.25));
  const lastKey = TIMES[k] > 0 ? TIMES[k] : 0;
  const since = t - lastKey;
  const blink = since < 0.5 ? 1 : clamp01(0.5 + 1.6 * Math.cos((2 * Math.PI * (since - 0.5)) / 1.06));
  const focus = easeOutCubic(seg(t, FOCUS_AT, FOCUS_AT + 0.2)) * (1 - seg(t, BLUR_AT, BLUR_AT + 0.25));
  return {
    focus,
    caret: seg(t, FOCUS_AT + 0.15, FOCUS_AT + 0.25) * (1 - seg(t, BLUR_AT - 0.05, BLUR_AT)) * blink,
    placeholder: 1 - superpose(TEXT_FLAGS, t, 0.12, 0),
    send: superpose(TEXT_FLAGS, t, 0.2, 0),
    pop: t < CHIP_AT ? open : 1 - close,
    count: superpose(COUNTS, t, ROW_MOVE, 0),
    rowY,
    rowA,
    morph: t < CHIP_AT + MORPH_LEN + 0.6 ? seg(t, CHIP_AT, CHIP_AT + MORPH_LEN) : 1,
    select: easeOutCubic(seg(t, SELECT_AT, SELECT_AT + SELECT_LEN)),
    breakUp: seg(t, BREAK_AT, BREAK_AT + BREAK_LEN),
  };
}

/** Whether the popover is open at a step (for touches while the loop plays). */
export function openAt(k: number) {
  return OPEN_FLAGS[k] === 1;
}

/** The ghost finger: taps the composer, then Maya's row. */
export function autoDot(t: number): Dot {
  'worklet';
  let target = 0;
  let alpha = 0;
  let scale = 1;
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
  return { target, alpha, scale };
}
