/*
 Timing, copy, colours and the frame the Inline Pill Reveal draws (a port of
 InlinePillRevealModel.swift). Every value is a function of the loop time (autoPlay) or of the
 reveal amount q (touch), so the three stacks match. Helpers sit above the worklets that call them.
*/

export const LOOP = 8.0;

export const LINE1 = ['Slow', 'mornings,'];
export const LINE2 = ['made', 'yours'];
export const BRAND = 'slowfield';
export const SKIP = 'Skip';
export const CTA = 'Find your stay';

export const PAGE = 0xb9715a;
export const CREAM = 0xfbf3ea;
export const INK = 0x3a2420;

export const FONT_SIZE = 44;
export const TRACKING = -0.4;
export const BASELINE_DROP = 15;
export const CARET_W = 2.5;
export const CARET_H = 44;
export const PILL_H = 76;
export const PILL_MARGIN = 14;
export const WORD_GAP = 12;
export const CARET_PART = 3;
export const DRAG_SPAN = 0.75;

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

export function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

export type Frame = {
  t: number; // loop time, for the scene's idle life
  part: number; // the words part around the caret
  caret: number; // caret opacity
  open: number; // inline pill 0 … 1
  full: number; // the pill growing to full bleed
  wordIn: number[];
  wordOut: number[];
  ctaIn: number;
  ctaOut: number;
  dots: number;
};

export function emptyFrame(t: number): Frame {
  'worklet';
  return { t, part: 0, caret: 0, open: 0, full: 0, wordIn: [0, 0], wordOut: [0, 0], ctaIn: 0, ctaOut: 0, dots: 0 };
}

/** The autoPlay timeline (seconds in 0 ..< LOOP). Frame 0 and frame LOOP are both the text line at rest. */
export function timeline(t: number): Frame {
  'worklet';
  const f = emptyFrame(t);
  f.part = easeOut(seg(t, 0.7, 0.9)) * (1 - easeInOut(seg(t, 7.3, 7.55)));
  const blinkA = seg(t, 0.7, 0.78) - seg(t, 0.98, 1.06);
  const blinkB = seg(t, 1.14, 1.22) * (1 - seg(t, 1.4, 1.47));
  const closing = seg(t, 7.36, 7.4) * (1 - seg(t, 7.46, 7.56));
  f.caret = Math.max(blinkA, blinkB, closing);
  f.open = easeInOut(seg(t, 1.4, 2.6)) * (1 - easeInOut(seg(t, 6.4, 7.4)));
  f.full = easeInOut(seg(t, 2.6, 3.3)) * (1 - easeInOut(seg(t, 5.7, 6.4)));
  for (let i = 0; i < 2; i++) {
    f.wordIn[i] = easeOut(seg(t, 3.25 + 0.1 * i, 3.7 + 0.1 * i));
    f.wordOut[i] = easeIn(seg(t, 5.46 - 0.06 * i, 5.76 - 0.06 * i));
  }
  f.ctaIn = easeOut(seg(t, 3.45, 3.9));
  f.ctaOut = easeIn(seg(t, 5.4, 5.7));
  f.dots = f.full;
  return f;
}

/** The same screen driven by the reveal amount q (0 = text line, 1 = full scene). */
export function reveal(q: number, t: number): Frame {
  'worklet';
  const f = emptyFrame(t);
  f.open = easeInOut(seg(q, 0, 0.6));
  f.full = easeInOut(seg(q, 0.6, 1));
  for (let i = 0; i < 2; i++) f.wordIn[i] = easeOut(seg(q, 0.84 + 0.05 * i, 0.95 + 0.05 * i));
  f.ctaIn = easeOut(seg(q, 0.9, 1));
  f.dots = f.full;
  return f;
}

function inverseEaseInOut(y: number) {
  'worklet';
  if (y <= 0) return 0;
  if (y >= 1) return 1;
  return y < 0.5 ? Math.cbrt(y / 4) : 1 - Math.cbrt(2 * (1 - y)) / 2;
}

/** The reveal amount that shows the same pill as f (open and full never move at the same time). */
export function qOf(f: Frame) {
  'worklet';
  return f.full > 0 ? 0.6 + 0.4 * inverseEaseInOut(f.full) : 0.6 * inverseEaseInOut(f.open);
}

/** Plays the timeline, hands over to the finger, and eases home 2 s after the last touch. */
export type Driver = {
  epoch: number;
  manual: boolean;
  dragging: boolean;
  q: number;
  q0: number;
  animating: boolean;
  animFrom: number;
  animTo: number;
  animStart: number;
  animDur: number;
  lastTouch: number;
  returning: boolean;
  shown: number;
  fadeFrom: number;
  fadeStart: number;
};

export function makeDriver(): Driver {
  'worklet';
  return {
    epoch: -1,
    manual: false,
    dragging: false,
    q: 0,
    q0: 0,
    animating: false,
    animFrom: 0,
    animTo: 0,
    animStart: 0,
    animDur: 0,
    lastTouch: -100,
    returning: false,
    shown: 1,
    fadeFrom: 1,
    fadeStart: -100,
  };
}

function animated(d: Driver, now: number) {
  'worklet';
  if (!d.animating) return d.q;
  const k = seg(now, d.animStart, d.animStart + d.animDur);
  const v = d.animFrom + (d.animTo - d.animFrom) * easeInOut(k);
  if (k >= 1) {
    d.q = d.animTo;
    d.animating = false;
  }
  return v;
}

function animate(d: Driver, target: number, from: number, now: number) {
  'worklet';
  d.q = from;
  d.animFrom = from;
  d.animTo = target;
  d.animStart = now;
  d.animDur = 0.3 + 1.2 * Math.abs(target - from);
  d.animating = true;
}

export function driverFrame(d: Driver, now: number, auto: boolean, reduced: boolean): Frame {
  'worklet';
  if (d.epoch < 0) d.epoch = now;
  const t = (now - d.epoch) % LOOP;
  if (reduced) return reveal(1, 0);
  if (d.manual) {
    const v = animated(d, now);
    if (auto && !d.dragging && now - d.lastTouch > 2) {
      if (!d.returning) {
        d.returning = true;
        animate(d, 0, v, now);
      } else if (!d.animating) {
        d.manual = false;
        d.returning = false;
        d.epoch = now;
        return timeline(0);
      }
    }
    return reveal(v, t);
  }
  return auto ? timeline(t) : reveal(0, t);
}

export function shownAmount(d: Driver, now: number) {
  'worklet';
  const k = seg(now, d.fadeStart, d.fadeStart + 0.25);
  return d.fadeFrom + (d.shown - d.fadeFrom) * k;
}

export function toggleReduced(d: Driver, now: number) {
  'worklet';
  d.fadeFrom = shownAmount(d, now);
  d.shown = d.shown > 0.5 ? 0 : 1;
  d.fadeStart = now;
}

export function accepts(d: Driver, y: number, cy: number, now: number, auto: boolean) {
  'worklet';
  const f = driverFrame(d, now, auto, false);
  return qOf(f) > 0.02 || Math.abs(y - cy) < 70;
}

export function begin(d: Driver, now: number, auto: boolean) {
  'worklet';
  const f = driverFrame(d, now, auto, false);
  d.q = d.manual ? animated(d, now) : qOf(f);
  d.q0 = d.q;
  d.animating = false;
  d.manual = true;
  d.dragging = true;
  d.returning = false;
  d.lastTouch = now;
}

export function drag(d: Driver, dx: number, width: number, now: number) {
  'worklet';
  const dir = d.q0 < 0.5 ? 1 : -1;
  d.q = Math.min(Math.max(d.q0 + (dir * Math.abs(dx)) / (DRAG_SPAN * width), 0), 1);
  d.lastTouch = now;
}

/** Ends the drag or tap; true when the reveal commits to the other state. */
export function end(d: Driver, dx: number, vx: number, tap: boolean, width: number, now: number) {
  'worklet';
  d.dragging = false;
  d.lastTouch = now;
  const wasOpen = d.q0 >= 0.5;
  let target = 0;
  if (tap) {
    target = wasOpen ? 0 : 1;
  } else {
    const dir = wasOpen ? -1 : 1;
    const projected = d.q0 + (dir * Math.abs(dx + 0.25 * vx)) / (DRAG_SPAN * width);
    target = projected > 0.5 ? 1 : 0;
  }
  animate(d, target, d.q, now);
  return target > 0.5 !== wasOpen;
}
