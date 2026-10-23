import { Easing } from 'react-native-reanimated';

import { GEO } from './theme';

/**
 * Every motion value of the block. Lever angles are screen angles in degrees, 0 pointing right and −90 straight
 * up (y down), so pulling the handle down makes the angle grow. Times in ms unless named.
 */

export const PUMP = {
  strokes: 3, // three strokes to 9 bar
  barPerStroke: 3,
  rest: -82, // the lever stands almost upright against its upper stop
  bottom: -6, // a stroke ends against the lower stop, just past level
  strokeAt: -16, // past this the piston has drawn its charge: one stroke counted
  rearmAt: -58, // the lever must come back above this before the next stroke counts
  overDown: 6, // past a stop the lever gives at most this much more (rubber band)
  overUp: 5,
  overSoftness: 40, // finger travel past a stop that gives half of the extra
  degPerPt: 180 / (Math.PI * GEO.fingerAt), // a finger far from the lever still moves it 1:1 at the handle
  grabFrom: 0.3, // a grab this far out along the lever (share of its length) or more turns it by the finger's angle
} as const;

export const SPRING = {
  home: { damping: 26, stiffness: 300, mass: 1 }, // between strokes the spring takes the lever back up: ζ ≈ 0.75
  needle: { damping: 12, stiffness: 190, mass: 1 }, // the needle steps to the new pressure, overshoots, settles: ζ ≈ 0.44
} as const;

/** The shot, once the lever is let go at 9 bar. */
export const BREW = {
  pour: 4600, // the lever rises and the shot pours, a time-lapse of…
  seconds: 27, // …a 27 s shot
  grams: 36,
  bar: 9,
  endBar: 1.4, // the pressure left when the lever is back up
  // The flow, as shares of the pour: first drips, then two steady streams, then they thin and blond and stop.
  drip: 0.05,
  flowing: 0.17,
  thinning: 0.76,
  dry: 0.95,
  lever: Easing.bezier(0.25, 0.1, 0.25, 1), // the spring pushes hard at first, then the puck holds it back
} as const;

export const TIMING = {
  zone: 360, // the brew zone lights at 9 bar
  ready: 700, // the warm light of a finished shot comes up
  readyOut: 420,
  settle: 900, // the needle falls to 0 after the shot
  cupOut: 380, // the full cup slides out…
  cupIn: 460, // …and an empty one slides in
  cupTravel: 46, // pt to the right
  timerReset: 160, // the cup is on its way out when the timer's reels roll back to 00
  puff: 700, // life of the little vent of steam each stroke lets out of the group
  subRoll: 160, // the caption's sub line rolls to its next step
  reduced: 180, // Reduce Motion: every change is a short fade or a short move
  easeOut: Easing.bezier(0.23, 1, 0.32, 1),
  easeInOut: Easing.bezier(0.77, 0, 0.175, 1),
} as const;

/** The steam over a finished shot. Times in s. */
export const STEAM = {
  rise: 18, // pt/s
  in: 0.9, // fades in over this once the cup is full
} as const;

/** Autoplay: one scripted finger on one clock, in seconds. */
export const LOOP_MS = 12_000;
export const RESUME_MS = 3_500;
const STROKE = { press: 0.46, hold: 0.1 } as const;
const strokeAt = (down: number, hold: number = STROKE.hold) =>
  ({ down, moveEnd: down + STROKE.press, up: down + STROKE.press + hold });
export const DEMO = {
  // Three strokes; the third holds a beat at 9 bar ("Release to brew"), then lets go and the shot pours.
  strokes: [strokeAt(0.7), strokeAt(1.75), strokeAt(2.8, 0.75)] as const,
  swap: 10.6, // after the ready beat the cup is swapped and the machine is back where the loop began
} as const;
