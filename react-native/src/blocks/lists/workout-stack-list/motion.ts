import { Easing } from 'react-native-reanimated';

// Timeline stepped from the pin's 60 fps video (ms from the loop start).

export const EASE_OUT = [0.23, 1, 0.32, 1] as const;
export const EASE_IN_OUT = [0.77, 0, 0.175, 1] as const;
/** The pin's own transitions: a soft standard curve. */
export const EASE_PIN = [0.4, 0, 0.2, 1] as const;
export const easeOut = Easing.bezier(...EASE_OUT);
export const easePin = Easing.bezier(...EASE_PIN);

export type Span = readonly [number, number];

export const T = {
  scroll: [1000, 1500] as Span, // the deck runs forward five cards
  open: [3200, 3750] as Span, // Crossfit card → detail
  detailScroll: [4800, 5300] as Span,
  month: [6000, 6600] as Span,
  dayTap: 8400,
  day: [8450, 8950] as Span,
  back: [10400, 11000] as Span,
};
export const LOOP_MS = 11750;

/** Touch durations (same curves). */
export const TOUCH = { scrollSnap: { duration: 400, dampingRatio: 0.8 }, openMs: 550, monthMs: 600, dayMs: 500, backMs: 600 };
/** One card of deck travel per this many points of finger travel. */
export const DECK_DRAG = 150;
export const RESUME_AFTER_MS = 3500;
export const REDUCED_FADE_MS = 200;
export const PRESS_SCALE = 0.97;
export const PRESS_IN_MS = 120;
export const PRESS_OUT_MS = 150;
/** Back to the list: the deck arrives slightly zoomed and settles, as in the pin. */
export const BACK_ZOOM = 1.12;
