import { Easing } from 'react-native-reanimated';

import { EASE_PIN, LOOP_MS, type Span, T } from './motion';

const pin = Easing.bezierFn(...EASE_PIN);

export function seg(t: number, s: Span) {
  'worklet';
  const v = (t - s[0]) / (s[1] - s[0]);
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export type Scene = {
  /** Deck travel in cards: 0 = first five, 5 = next five. */
  deck: number;
  /** Card → detail morph. */
  open: number;
  /** Detail → list (the list returns zoomed and settles). */
  leave: number;
  /** Detail scrolled up (0..1). */
  scroll: number;
  /** Week row → month grid with the class cards stacked under it. */
  month: number;
  /** Day 8 → day 9. */
  day: number;
  /** Category the detail belongs to. */
  cat: number;
};

/** The autoplay as a pure function of the loop clock. */
export function sampleScene(tIn: number, crossfit: number): Scene {
  'worklet';
  const t = ((tIn % LOOP_MS) + LOOP_MS) % LOOP_MS;
  const leave = pin(seg(t, T.back));
  const done = leave >= 1;
  return {
    // the list under the detail is reset to its first five while hidden
    deck: t < T.open[1] ? 5 * pin(seg(t, T.scroll)) : 0,
    open: done ? 0 : pin(seg(t, T.open)),
    leave: done ? 0 : leave,
    scroll: done ? 0 : pin(seg(t, T.detailScroll)),
    month: done ? 0 : pin(seg(t, T.month)),
    day: done ? 0 : pin(seg(t, T.day)),
    cat: crossfit,
  };
}
