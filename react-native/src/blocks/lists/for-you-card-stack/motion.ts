import { Easing } from 'react-native-reanimated';

// Phases stepped from the pin's 30 fps video (ms from the loop start).

export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;
/** iOS push / pop. */
export const EASE_SHEET = [0.32, 0.72, 0, 1] as const;
/** The pin's own morphs (cover shrink, wave, book) read as a standard material-like curve. */
export const EASE_PIN = [0.4, 0, 0.2, 1] as const;

export const easeOut = Easing.bezier(...EASE_OUT);
export const easeInOut = Easing.bezier(...EASE_IN_OUT);
export const easeSheet = Easing.bezier(...EASE_SHEET);
export const easePin = Easing.bezier(...EASE_PIN);

export type Span = readonly [number, number];

// Approximate progress measured from the recovered clip's interactive edge-back pan.
export const POP_TIMES = [11900, 12000, 12100, 12200, 12300, 12400, 12500];
export const POP_PROGRESS = [0, 0.05, 0.15, 0.3, 0.5, 0.8, 1];

/** The pin's timeline, then one deck push-back so the loop closes on its first frame. */
export const T = {
  deck: [800, 1100] as Span,
  push: [1650, 2050] as Span,
  playTap: 3450,
  /** Play → the cover shrinks under a rising wave, opens and riffles to the reading spread. */
  open: [3500, 5000] as Span,
  /** Reading progress in lines, keyed from the pin's frames (the voice reads unevenly). */
  readT: [4000, 4200, 5000, 6000, 7000, 8300],
  readLines: [0, 0.6, 1.4, 2, 3.4, 5.2],
  pauseTap: 8450,
  /** Pause → the spread grows back, riffles to the cover and closes. */
  close: [8500, 10375] as Span,
  deckBack: [12950, 13300] as Span,
};
export const LOOP_MS = 13700;

/** Touch: the same morphs at UI speed. */
export const TOUCH = {
  deckMs: 300,
  pushMs: 400,
  playMs: 1500, // the pin's open sequence
  closeMs: 1600,
};
export const RESUME_AFTER_MS = 3500;
export const REDUCED_FADE_MS = 200;
export const PRESS_SCALE = 0.97;
export const PRESS_IN_MS = 120;
export const PRESS_OUT_MS = 150;
