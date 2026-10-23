import { Easing } from 'react-native-reanimated';

import { CHAR, CHIP } from './moods';

// Timings stepped frame by frame from the pin (30 fps). One curve fits all its scrolls.
export const SCROLL_BEZIER = [0.3, 0.05, 0.4, 1] as const;
export const scrollEasing = Easing.bezier(...SCROLL_BEZIER);

export const FILL_IN_MS = 200;
export const FILL_OUT_MS = 170;
export const USER_SCROLL_MS = 1270;
/** Snap back after a drag (velocity is added at the call). */
export const SNAP_SPRING = { duration: 400, dampingRatio: 0.8 };
export const RESUME_AFTER_MS = 3500;
export const RETURN_TO_REST_MS = 650;
export const REDUCED_FADE_MS = 180;

/**
 * Autoplay: the pin's 13.2 s take, with its long last hold cut to the others' length and one more scroll back
 * to Sleepy so the loop closes on the frame it opens with (Sleepy selected).
 */
export const LOOP_MS = 13800;

/** Expressions, timed from the pin. */
export const EXPRESSION = {
  zzzTilt: -12,
  zzzStaggerMs: 110,
  zzzPopMs: 220,
  mouthMs: 200,
  mouthClosed: 0.08,
  droolDelayMs: 100,
  droolMs: 200,
  crossColor: '#F92105',
  crossRest: '#FBCFC4',
  bingeBody: '#CB7EF4',
  glassesFlickerMs: 130,
  heartGain: 0.12,
  heartMs: 1150,
  wingMin: 0.45,
  wingMs: 165,
  wingLag: 0,
  /** Share of the eye the red lid covers: at rest and once the mood is selected. */
  lidRest: 0.45,
  lidSelected: 0.04,
};

export type Step = {
  chip: number;
  /** Where the pin's scroll came to rest: the chip's centre sits this far (pt) right of CHIPS_ROW.centerX. */
  dx: number;
  char: number;
  outAt: number;
  outMs: number;
  moveAt: number;
  moveMs: number;
  fillAt: number;
  fillMs: number;
};

export const START = { chip: CHIP.sleepy, dx: 2.8, char: CHAR.sleepy };

export const STEPS: readonly Step[] = [
  {
    chip: CHIP.foodie,
    dx: -3.2,
    char: CHAR.foodie,
    outAt: 400,
    outMs: 170,
    moveAt: 500,
    moveMs: 1333,
    fillAt: 1700,
    fillMs: 200,
  },
  {
    chip: CHIP.binge,
    dx: -3.8,
    char: CHAR.binge,
    outAt: 2400,
    outMs: 170,
    moveAt: 2530,
    moveMs: 1267,
    fillAt: 3730,
    fillMs: 240,
  },
  {
    chip: CHIP.shopping,
    dx: 0,
    char: CHAR.shopping,
    outAt: 4470,
    outMs: 170,
    moveAt: 4470,
    moveMs: 1267,
    fillAt: 5600,
    fillMs: 300,
  },
  // Scroll Mode lets its colour drain slowly before the next scroll, as in the pin.
  {
    chip: CHIP.scroll,
    dx: 1.3,
    char: CHAR.scroll,
    outAt: 6530,
    outMs: 200,
    moveAt: 6570,
    moveMs: 1200,
    fillAt: 7600,
    fillMs: 230,
  },
  {
    chip: CHIP.social,
    dx: 2.2,
    char: CHAR.social,
    outAt: 7830,
    outMs: 740,
    moveAt: 8430,
    moveMs: 1633,
    fillAt: 9970,
    fillMs: 130,
  },
  {
    chip: CHIP.recovery,
    dx: -3.0,
    char: CHAR.recovery,
    outAt: 10570,
    outMs: 100,
    moveAt: 10570,
    moveMs: 1167,
    fillAt: 11730,
    fillMs: 170,
  },
  {
    chip: CHIP.sleepy,
    dx: 2.8,
    char: CHAR.sleepy,
    outAt: 12450,
    outMs: 170,
    moveAt: 12500,
    moveMs: 1200,
    fillAt: 13600,
    fillMs: 200,
  },
];
