/*
 Timing, copy, colours, icons and the frame of the Oracle Glass Grid (a port of OracleGlassGridModel.swift).
 Every value is a function of the story time t (0 until LOOP) and the shimmer clock, so the three stacks match.
 Every function here runs on the UI thread; helpers are declared above their callers.
*/

export const LOOP = 10;

// Copy
export const TITLE_GRID = 'What answers do you seek?';
export const TITLE_FIELD = 'Type your question';
export const PLACEHOLDER = 'Tap to type';
export const SAMPLE = 'Should I take the leap?';
export const ASK = 'ASK FOR ME';
export const LABELS: string[][] = [
  ['School'],
  ['Friends and', 'Family'],
  ['Love'],
  ['Career'],
  ["Life's Greatest", 'Mysteries'],
  ['My Future'],
  ['Myself'],
  ['Style'],
  ['Lunch'],
];
export const AUTO_CHOICE = 3;

// Colours
export const WHITE = 0xffffff;
export const CARD_WHITE = 0xf6f3ee;
export const INK = 0x1e2a23;
export const BACKDROP = 0x1d3a2b;

// Layout (pt)
export const CARD_W = 100;
export const CARD_H = 120;
export const CARD_R = 14;
export const GAP = 7;
export const CENTER_SCALE = 1.22;
export const FIELD_H = 56;
export const FIELD_MARGIN = 26.5;
export const CARET_W = 2;
export const CARET_H = 24;
export const CAMERA = 600;
export const ICON_SCALE = 1.18;
export const ICON_LIFT = 13;
export const ASK_W = 236;
export const ASK_H = 44;

export function slotX(i: number, gx: number) {
  'worklet';
  return gx + ((i % 3) - 1) * (CARD_W + GAP);
}

export function slotY(i: number, gy: number) {
  'worklet';
  return gy + (Math.floor(i / 3) - 1) * (CARD_H + GAP);
}

export function ranks(chosen: number): number[] {
  'worklet';
  const d: number[] = [];
  for (let i = 0; i < 9; i++) {
    const dx = (i % 3) - (chosen % 3);
    const dy = Math.floor(i / 3) - Math.floor(chosen / 3);
    d.push(Math.sqrt(dx * dx + dy * dy));
  }
  const order: number[] = [];
  for (let i = 0; i < 9; i++) if (i !== chosen) order.push(i);
  order.sort((a, b) => (d[a] === d[b] ? a - b : d[a] - d[b]));
  const r = [0, 0, 0, 0, 0, 0, 0, 0, 0];
  for (let k = 0; k < order.length; k++) r[order[k]] = k + 1;
  return r;
}

// Curves
export function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(Math.max((t - a) / (b - a), 0), 1);
}
export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
export function easeIn(x: number) {
  'worklet';
  return x * x * x;
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOutBack(x: number, s: number) {
  'worklet';
  const c3 = s + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + s * Math.pow(x - 1, 2);
}
export function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}
export function wave(t: number, k: number, phase: number) {
  'worklet';
  return 0.5 + 0.5 * Math.sin((2 * Math.PI * k * t) / LOOP + phase);
}
export function blink(s: number) {
  'worklet';
  return Math.min(Math.max(0.5 + 3 * Math.cos(2 * Math.PI * s), 0), 1);
}

// Typing
export function typeTime(k: number) {
  'worklet';
  return 5.6 + 0.056 * k + (k === 0 ? 0 : 0.018 * Math.sin(k * 2.3));
}

export function typedAt(t: number) {
  'worklet';
  if (t >= 7.5) return 0;
  let n = 0;
  for (let k = 0; k < SAMPLE.length; k++) if (typeTime(k) <= t) n = k + 1;
  return n;
}

export type Frame = {
  clock: number;
  chosen: number;
  ranks: number[];
  press: number;
  pick: number;
  gather: number[];
  center: number;
  vanish: number;
  flip: number;
  line: number;
  open: number;
  halo: number;
  lineAlpha: number;
  caret: number;
  typed: number;
  textAlpha: number;
  placeholder: number;
  toPlaceholder: number;
  rings: number;
  titleGrid: number;
  titleGridY: number;
  titleField: number;
  titleFieldY: number;
  askAlpha: number;
  sheen: number;
  cardAlpha: number;
  fieldAlpha: number;
  reducedField: boolean;
};

/** The story at time t. typedOverride < 0: the self-playing sample; blinkClock < 0: blink from t. */
export function frameAt(t: number, chosen: number, typedOverride: number, blinkClock: number, clock: number): Frame {
  'worklet';
  const rank = ranks(chosen);
  const gather = [0, 0, 0, 0, 0, 0, 0, 0, 0];
  for (let i = 0; i < 9; i++) {
    if (i === chosen) continue;
    const a = 1.7 + 0.045 * (rank[i] - 1);
    const b = 8.6 + 0.04 * (rank[i] - 1);
    gather[i] = easeInOut(seg(t, a, a + 0.6)) * (1 - easeOutBack(seg(t, b, b + 0.55), 1.4));
  }
  const flip = easeIn(seg(t, 3.5, 4.0)) * (1 - easeOut(seg(t, 8.2, 8.55)));
  const n = typedOverride >= 0 ? typedOverride : typedAt(t);
  let caret = 1;
  if (t >= 4.9 && t < 7.3) {
    if (blinkClock >= 0) caret = blink(blinkClock);
    else if (t < 5.6) caret = blink(t - 4.9);
    else if (t >= 6.95) caret = blink(t - 6.95);
    caret = Math.max(caret, seg(t, 7.2, 7.3));
  }
  return {
    clock,
    chosen,
    ranks: rank,
    press: easeOut(seg(t, 1.3, 1.45)) * (1 - easeOutBack(seg(t, 1.45, 1.85), 1.2)),
    pick: easeOut(seg(t, 1.45, 1.85)) * (1 - easeInOut(seg(t, 8.5, 8.9))),
    gather,
    center: easeInOut(seg(t, 2.4, 3.3)) * (1 - easeInOut(seg(t, 8.5, 9.3))),
    vanish: easeIn(seg(t, 3.1, 3.5)) * (1 - easeOut(seg(t, 8.45, 8.65))),
    flip,
    line: easeInOut(seg(t, 4.0, 4.25)) * (1 - easeInOut(seg(t, 7.95, 8.2))),
    open: easeOutBack(seg(t, 4.25, 4.9), 1.1) * (1 - easeInOut(seg(t, 7.45, 7.95))),
    halo: easeOut(seg(t, 1.45, 1.85)) * (1 - flip) * (1 - easeInOut(seg(t, 8.5, 8.9))),
    lineAlpha: seg(flip, 0.86, 1),
    caret,
    typed: n,
    textAlpha: 1 - easeIn(seg(t, 7.3, 7.5)),
    placeholder: n === 0 ? easeInOut(seg(t, 4.75, 5.0)) * (1 - easeIn(seg(t, 7.3, 7.45))) : 0,
    toPlaceholder: easeInOut(seg(t, 4.75, 5.0)) * (1 - easeIn(seg(t, 7.3, 7.5))),
    rings: easeOut(seg(t, 4.66, 4.9)) * (1 - easeIn(seg(t, 7.45, 7.6))),
    titleGrid: 1 - easeIn(seg(t, 1.7, 2.0)) + easeOut(seg(t, 9.0, 9.4)),
    titleGridY: t < 5 ? -8 * easeIn(seg(t, 1.7, 2.0)) : -8 * (1 - easeOut(seg(t, 9.0, 9.4))),
    titleField: easeOut(seg(t, 4.5, 4.9)) * (1 - easeIn(seg(t, 7.4, 7.7))),
    titleFieldY: t < 7.7 ? 8 * (1 - easeOut(seg(t, 4.5, 4.9))) : 8,
    askAlpha:
      1 - easeIn(seg(t, 1.7, 2.0)) + easeOut(seg(t, 4.7, 5.05)) * (1 - easeIn(seg(t, 7.4, 7.6))) + easeOut(seg(t, 9.1, 9.45)),
    sheen: t < 1.15 ? seg(t, 0.15, 1.15) : 0,
    cardAlpha: 1,
    fieldAlpha: 1,
    reducedField: false,
  };
}

/** Reduced motion: the grid (s = 0) or the open field (s = 1), crossfaded. */
export function reducedFrame(s: number, chosen: number): Frame {
  'worklet';
  const f = frameAt(0, chosen, -1, -1, 0);
  const open = frameAt(5.2, chosen, 0, 0.1, 0);
  f.cardAlpha = 1 - s;
  f.titleGrid = 1 - s;
  f.titleField = s;
  f.titleFieldY = 0;
  f.open = open.open;
  f.line = 1;
  f.lineAlpha = 1;
  f.fieldAlpha = s;
  f.placeholder = 1;
  f.toPlaceholder = 1;
  f.rings = 1;
  f.caret = 1;
  f.sheen = 0;
  f.reducedField = true;
  return f;
}

// Icons — 56 × 56 boxes, absolute SVG paths, drawn with 1.3 pt round strokes.
export type Mark = { d: string; fill: boolean; glow: boolean; k: number; ph: number; dim: number };

function mk(d: string, fill = false, glow = false, k = 0, ph = 0, dim = 1): Mark {
  return { d, fill, glow, k, ph, dim };
}

export const ICONS: Mark[][] = [
  [
    mk('M28 41C22 37 14 37 8 39L8 23C14 21 22 21 28 25Z'),
    mk('M28 41C34 37 42 37 48 39L48 23C42 21 34 21 28 25Z'),
    mk('M28 25L28 41'),
    mk('M19.5 19.9L13.9 17.9', false, true, 4, 0.0),
    mk('M21.1 17.2L16.5 13.4', false, true, 4, -0.6),
    mk('M23.5 15.2L20.5 10', false, true, 4, -1.2),
    mk('M26.4 14.1L25.4 8.2', false, true, 4, -1.8),
    mk('M29.6 14.1L30.6 8.2', false, true, 4, -2.4),
    mk('M32.5 15.2L35.5 10', false, true, 4, -3.0),
    mk('M34.9 17.2L39.5 13.4', false, true, 4, -3.6),
    mk('M36.5 19.9L42.1 17.9', false, true, 4, -4.2),
  ],
  [
    mk('M14 36L28 15L42 32L24 46Z', false, false, 0, 0, 0.45),
    mk('M14 32L14.8 35.2L18 36L14.8 36.8L14 40L13.2 36.8L10 36L13.2 35.2Z', true, true, 3, 0.4),
    mk('M28 10.5L28.8 14.2L32.5 15L28.8 15.8L28 19.5L27.2 15.8L23.5 15L27.2 14.2Z', true, true, 4, 2.1),
    mk('M42 28L42.8 31.2L46 32L42.8 32.8L42 36L41.2 32.8L38 32L41.2 31.2Z', true, true, 5, 4.0),
    mk('M24 43.4L24.6 45.4L26.6 46L24.6 46.6L24 48.6L23.4 46.6L21.4 46L23.4 45.4Z', true, true, 6, 1.3),
  ],
  [
    mk('M28 46C14 36 9 28 13 21C17 14 25 15 28 21C31 15 39 14 43 21C47 28 42 36 28 46Z'),
    mk('M28 36C22 32 20 28 22 25C24 22 27 23 28 26C29 23 32 22 34 25C36 28 34 32 28 36Z', true, true, 5, 0.9),
    mk('M12 8.5L12.6 11.4L15.5 12L12.6 12.6L12 15.5L11.4 12.6L8.5 12L11.4 11.4Z', true, true, 3, 2.6),
    mk('M45 8L45.6 10.4L48 11L45.6 11.6L45 14L44.4 11.6L42 11L44.4 10.4Z', true, true, 4, 5.0),
  ],
  [
    mk('M19 40L19.2 38.2L19.7 36.6L20.5 35L21.6 33.6L23 32.5L24.6 31.7L26.2 31.2L28 31L29.8 31.2L31.4 31.7L33 32.5L34.4 33.6L35.5 35L36.3 36.6L36.8 38.2L37 40', false, true, 3, 0.0),
    mk('M13 40L13.3 37.1L14.1 34.3L15.5 31.7L17.4 29.4L19.7 27.5L22.3 26.1L25.1 25.3L28 25L30.9 25.3L33.7 26.1L36.3 27.5L38.6 29.4L40.5 31.7L41.9 34.3L42.7 37.1L43 40', false, true, 3, -0.9),
    mk('M7 40L7.4 35.9L8.6 32L10.5 28.3L13.2 25.2L16.3 22.5L20 20.6L23.9 19.4L28 19L32.1 19.4L36 20.6L39.7 22.5L42.8 25.2L45.5 28.3L47.4 32L48.6 35.9L49 40', false, true, 3, -1.8),
    mk('M28 29.5L29.1 33.9L33.5 35L29.1 36.1L28 40.5L26.9 36.1L22.5 35L26.9 33.9Z', true, true, 5, 1.5),
  ],
  [
    mk('M7 30C15 19 41 19 49 30C41 41 15 41 7 30Z'),
    mk('M34 30C34 33.3 31.3 36 28 36C24.7 36 22 33.3 22 30C22 26.7 24.7 24 28 24C31.3 24 34 26.7 34 30Z'),
    mk('M30.6 30C30.6 31.4 29.4 32.6 28 32.6C26.6 32.6 25.4 31.4 25.4 30C25.4 28.6 26.6 27.4 28 27.4C29.4 27.4 30.6 28.6 30.6 30Z', true, true, 4, 3.1),
    mk('M19 21L16.5 15.5', false, true, 6, 0.2),
    mk('M28 19L28 12.5', false, true, 6, -0.8),
    mk('M37 21L39.5 15.5', false, true, 6, -1.6),
  ],
  [
    mk('M6 40L50 40'),
    mk('M35 36C35 39.9 31.9 43 28 43C24.1 43 21 39.9 21 36C21 32.1 24.1 29 28 29C31.9 29 35 32.1 35 36Z', true, true, 3, 0.6),
    mk('M16.2 33.9L9.3 32.7', false, true, 5, 0.0),
    mk('M17.6 30L11.5 26.5', false, true, 5, 2.4),
    mk('M20.3 26.8L15.8 21.4', false, true, 5, 0.9),
    mk('M23.9 24.7L21.5 18.1', false, true, 5, 3.6),
    mk('M28 24L28 17', false, true, 5, 1.7),
    mk('M32.1 24.7L34.5 18.1', false, true, 5, 4.4),
    mk('M35.7 26.8L40.2 21.4', false, true, 5, 0.3),
    mk('M38.4 30L44.5 26.5', false, true, 5, 2.9),
    mk('M39.8 33.9L46.7 32.7', false, true, 5, 5.2),
  ],
  [
    mk('M28 7C35 7 39 14 39 21C39 28 35 35 28 35C21 35 17 28 17 21C17 14 21 7 28 7Z'),
    mk('M28 35L28 50'),
    mk('M24.5 38L31.5 38'),
    mk('M21.5 17C22.3 14.2 24 12.4 26.4 11.6', false, true, 4, 1.0),
    mk('M45 8L45.6 10.4L48 11L45.6 11.6L45 14L44.4 11.6L42 11L44.4 10.4Z', true, true, 3, 3.7),
  ],
  [
    mk('M28 22C28 18 32 17.5 32 14C32 11 29.5 10 27.5 10.6C26 11.1 25 12.3 25 13.6'),
    mk('M28 22L8 36L48 36Z'),
    mk('M44 12L44.6 14.4L47 15L44.6 15.6L44 18L43.4 15.6L41 15L43.4 14.4Z', true, true, 4, 0.5),
    mk('M12 19.4L12.5 21.5L14.6 22L12.5 22.5L12 24.6L11.5 22.5L9.4 22L11.5 21.5Z', true, true, 6, 2.8),
  ],
  [
    mk('M17 9L17 21C17 25 19 27 21 27L21 49'),
    mk('M21 9L21 21'),
    mk('M25 9L25 21C25 25 23 27 21 27'),
    mk('M36 49L36 9C41 13 42 22 40 29L36 29'),
    mk('M41 8.2L41.5 10.5L43.8 11L41.5 11.5L41 13.8L40.5 11.5L38.2 11L40.5 10.5Z', true, true, 5, 4.2),
  ],
];

export function markAlpha(m: Mark, clock: number) {
  'worklet';
  if (m.k <= 0) return m.dim;
  return m.dim * (0.45 + 0.55 * wave(clock, m.k, m.ph));
}

// Driver: plays the story, hands it to the finger, and returns to the loop 2 s after the last touch.

const OPENING = 0;
const HOLD = 1;
const CLOSING = 2;
const REST = 3;

export type Driver = {
  epoch: number;
  storyEpoch: number;
  manual: boolean;
  phase: number;
  tm0: number;
  phaseStart: number;
  chosen: number;
  frozenTyped: number;
  lastTouch: number;
  shown: number;
  fadeFrom: number;
  fadeStart: number;
};

export function makeDriver(): Driver {
  'worklet';
  return {
    epoch: -1,
    storyEpoch: -1,
    manual: false,
    phase: REST,
    tm0: 0,
    phaseStart: 0,
    chosen: AUTO_CHOICE,
    frozenTyped: 0,
    lastTouch: -100,
    shown: 0,
    fadeFrom: 0,
    fadeStart: -100,
  };
}

function clockOf(d: Driver, now: number) {
  'worklet';
  if (d.epoch < 0) d.epoch = now;
  return (now - d.epoch) % LOOP;
}

function storyTime(d: Driver, now: number) {
  'worklet';
  if (d.storyEpoch < 0) d.storyEpoch = now;
  return (now - d.storyEpoch) % LOOP;
}

function startClosing(d: Driver, now: number) {
  'worklet';
  d.phase = CLOSING;
  d.tm0 = 7.3;
  d.phaseStart = now;
}

function manualTime(d: Driver, now: number, auto: boolean) {
  'worklet';
  if (d.phase === OPENING) {
    const t = Math.min(d.tm0 + (now - d.phaseStart), 4.9);
    if (t >= 4.9) {
      d.phase = HOLD;
      d.tm0 = 4.9;
      d.phaseStart = now;
      d.frozenTyped = 0;
    }
    return t;
  }
  if (d.phase === HOLD) {
    if (auto && now - d.lastTouch > 2 && now - d.phaseStart > 2) {
      startClosing(d, now);
      return 7.3;
    }
    return d.tm0;
  }
  if (d.phase === CLOSING) {
    const t = Math.min(d.tm0 + (now - d.phaseStart), 9.45);
    if (t >= 9.45) d.phase = REST;
    return t;
  }
  return 0;
}

export function shownAmount(d: Driver, now: number) {
  'worklet';
  const k = seg(now, d.fadeStart, d.fadeStart + 0.25);
  return d.fadeFrom + (d.shown - d.fadeFrom) * k;
}

export function driverFrame(d: Driver, now: number, auto: boolean, reduced: boolean): Frame {
  'worklet';
  const c = clockOf(d, now);
  if (reduced) return reducedFrame(shownAmount(d, now), d.chosen);
  if (d.manual) {
    const t = manualTime(d, now, auto);
    if (d.phase === REST && auto && now - d.lastTouch > 2) {
      d.manual = false;
      d.storyEpoch = now;
      return frameAt(0, AUTO_CHOICE, -1, -1, c);
    }
    if (d.phase === REST) return frameAt(0, d.chosen, -1, -1, c);
    const blinkClock = d.phase === HOLD ? now - d.phaseStart : -1;
    return frameAt(t, d.chosen, d.frozenTyped, blinkClock, c);
  }
  if (!auto) return frameAt(0, d.chosen, -1, -1, c);
  return frameAt(storyTime(d, now), AUTO_CHOICE, -1, -1, c);
}

export function isRest(d: Driver, now: number, auto: boolean) {
  'worklet';
  if (d.manual) return d.phase === REST;
  if (!auto) return true;
  const t = storyTime(d, now);
  return t < 1.3 || t >= 9.45;
}

export function pick(d: Driver, i: number, now: number) {
  'worklet';
  d.lastTouch = now;
  d.manual = true;
  d.chosen = i;
  d.phase = OPENING;
  d.tm0 = 1.3;
  d.phaseStart = now;
  d.frozenTyped = 0;
}

export function takeOver(d: Driver, now: number, auto: boolean) {
  'worklet';
  d.lastTouch = now;
  if (d.manual || !auto) return;
  const t = storyTime(d, now);
  d.manual = true;
  d.chosen = AUTO_CHOICE;
  d.frozenTyped = typedAt(t);
  d.phaseStart = now;
  d.tm0 = t;
  if (t >= 1.3 && t < 4.9) d.phase = OPENING;
  else if (t >= 4.9 && t < 7.3) d.phase = HOLD;
  else if (t >= 7.3 && t < 9.45) d.phase = CLOSING;
  else d.phase = REST;
}

export function closeField(d: Driver, now: number) {
  'worklet';
  d.lastTouch = now;
  if (!d.manual || d.phase !== HOLD) return false;
  startClosing(d, now);
  return true;
}

export function setReduced(d: Driver, open: boolean, chosen: number, now: number) {
  'worklet';
  d.fadeFrom = shownAmount(d, now);
  d.shown = open ? 1 : 0;
  if (chosen >= 0) d.chosen = chosen;
  d.fadeStart = now;
}
