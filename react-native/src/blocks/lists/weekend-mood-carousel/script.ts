import { Easing } from 'react-native-reanimated';

import { LOOP_MS, SCROLL_BEZIER, START, STEPS } from './motion';

export type Frame = {
  chipX: number;
  charPos: number;
  fill: number;
  selChip: number;
  selChar: number;
  age: number;
  veil: number;
  /** Selected chip's text: dark only while its fill comes in; it turns light as soon as the chip lets go. */
  ink: number;
};

const ease = Easing.bezierFn(...SCROLL_BEZIER);

function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

/**
 * The autoplay, as a pure function of the loop clock: same t, same frame. `centers` are the chips' centres
 * in the strip. Under Reduce Motion each scroll becomes a jump hidden in a short cross-fade (`veil`).
 */
export function sampleScript(tIn: number, centers: number[], reduced: boolean, fadeMs: number): Frame {
  'worklet';
  const t = ((tIn % LOOP_MS) + LOOP_MS) % LOOP_MS;
  let chipFrom: number = START.chip;
  let charFrom: number = START.char;
  let chipX = (centers[START.chip] ?? 0) - START.dx;
  let dxFrom = START.dx;
  let charPos: number = START.char;
  let fill = 1;
  let selChip: number = START.chip;
  let selChar: number = START.char;
  // Sleepy was selected at the end of the previous loop.
  let age = t + (LOOP_MS - STEPS[STEPS.length - 1].fillAt);
  let veil = 0;
  let ink = 1;
  for (let i = 0; i < STEPS.length; i++) {
    const s = STEPS[i];
    if (t < s.outAt) break;
    fill = 1 - clamp01((t - s.outAt) / s.outMs);
    ink = 0;
    if (t >= s.moveAt) {
      const lin = clamp01((t - s.moveAt) / s.moveMs);
      let p = ease(lin);
      if (reduced) {
        const mid = s.moveAt + s.moveMs / 2;
        p = t < mid ? 0 : 1;
        veil = Math.max(veil, 1 - clamp01(Math.abs(t - mid) / fadeMs));
      }
      const a = (centers[chipFrom] ?? 0) - dxFrom;
      const b = (centers[s.chip] ?? 0) - s.dx;
      chipX = a + (b - a) * p;
      charPos = charFrom + (s.char - charFrom) * p;
    }
    if (t >= s.fillAt) {
      fill = clamp01((t - s.fillAt) / s.fillMs);
      ink = fill;
      selChip = s.chip;
      selChar = s.char;
      age = t - s.fillAt;
    }
    chipFrom = s.chip;
    dxFrom = s.dx;
    charFrom = s.char;
  }
  return { chipX, charPos, fill, selChip, selChar, age, veil, ink };
}
