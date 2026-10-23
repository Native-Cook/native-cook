import { Easing } from 'react-native-reanimated';

// The pin plays the prototype: profile picker, a quick dissolve into the home feed, then the
// feed scrolled section by section to its end. The loop adds the way back (scroll to top,
// back arrow) so it can start again from the picker.
export const LOOP_MS = 14000;
export const IDLE_RESUME_MS = 3500;

export const EASE_OUT = Easing.bezierFn(0.23, 1, 0.32, 1);
// A released flick decelerates like a scroll view; scroll-to-top is the system's ease-in-out.
export const FLICK = Easing.bezierFn(0.3, 0.1, 0.12, 1);
export const HOME_EASE = Easing.bezierFn(0.45, 0, 0.2, 1);
export const DISSOLVE_EASE = EASE_OUT;

export const PICK = { pressAt: 1300, pressIn: 110, pressOut: 150 };
export const DISSOLVE = { inAt: 1460, outAt: 11640, ms: 240 };
export const BACK_PRESS = { at: 11420, ms: 220 };

// Scroll script, in content offsets (pt). The pin rests at 520, 936 and 1144 between its cuts;
// -1 is the end of the feed (the pin's last frame), 0 the top.
export type ScrollStep = { at: number; to: number; ms: number; ease: 'flick' | 'home' };
export const SCROLL_STEPS: ScrollStep[] = [
  { at: 2900, ms: 720, to: 520, ease: 'flick' },
  { at: 4100, ms: 600, to: 936, ease: 'flick' },
  { at: 5100, ms: 520, to: 1144, ease: 'flick' },
  { at: 5900, ms: 720, to: 1664, ease: 'flick' },
  { at: 6800, ms: 720, to: 2184, ease: 'flick' },
  { at: 7700, ms: 760, to: -1, ease: 'flick' },
  { at: 8950, ms: 1150, to: 0, ease: 'home' },
];

export const PRESS_SCALE = 0.97;
export const PRESS_MS = 120;
export const MANUAL_DISSOLVE_MS = 240;
export const REDUCED_FADE_MS = 200;
// Where the pin's last frame leaves the last section label: its centre 739 pt from the top.
export const END_LABEL_Y = 739;
