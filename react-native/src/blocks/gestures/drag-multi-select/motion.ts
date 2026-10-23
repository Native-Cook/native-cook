/** Every motion value of the block. Distances in pt, times in ms unless named in seconds. */
import { Easing } from 'react-native-reanimated';

/** Animation review (20 Oct): the animate-expo table curves; Reanimated's built-ins and the default in-out quad are weaker. */
export const EASE = {
  out: Easing.bezier(0.23, 1, 0.32, 1),
  inOut: Easing.bezier(0.77, 0, 0.175, 1),
} as const;
export const SELECT = {
  longPress: 320,
  dragSlop: 8, // vertical travel on the circles column before a range drag starts
  autoScrollMax: 720, // pt/s at the very edge
  coastDecay: 3.5, // 1/s: momentum after a drag released while auto-scrolling
  coastStop: 20, // pt/s
} as const;

export const SPRING = {
  band: { damping: 22, stiffness: 380, mass: 0.7 },
  circles: { damping: 24, stiffness: 300, mass: 0.8 },
  toolbar: { damping: 22, stiffness: 260, mass: 1 },
  collapse: { damping: 26, stiffness: 260, mass: 1 },
  trash: { damping: 7, stiffness: 380, mass: 0.6 },
  ripple: { damping: 20, stiffness: 200, mass: 1 },
} as const;

export const TIMING = {
  corner: 140,
  bandFade: 120,
  circleSlide: 16,
  circleStagger: 18,
  toolbarRise: 72,
  fab: 200,
  roll: 160,
  rollLift: 10,
  reduced: 180,
  scrollHome: 600,
  press: 120,
  trashUp: 80,
  toolStagger: 0.05, // morph progress between tools appearing
  breathPeriod: 3.25, // s; four per 13 s loop
  gloss: 460, // a light sweep across a band when its row joins
  ripple: 340, // the circle's ring when a row is picked
  streamOut: 300, // a deleted row slides out
  streamStagger: 55,
  restore: 320,
} as const;

/** Autoplay: one scripted finger on one clock, seconds. Rows are indices into TASKS. */
/** QA only: hides the demo's finger for the pixel gate. Ship false. */
export const QA_HIDE_FINGER = false;

export const LOOP_MS = 13_000;
export const RESUME_MS = 3_500;
export const DEMO = {
  fingerFade: 0.15,
  hold: { row: 5, down: 0.6 }, // long press; selection starts at down + longPress
  drag: [
    { at: 1.9, row: 5 }, // holds a moment first, so the toolbar has settled (the source's state a)
    { at: 2.6, row: 9 },
    { at: 3.0, row: 7 },
    { at: 3.55, row: 12 },
  ],
  release: 3.75,
  tap: { row: 1, at: 4.1 },
  edge: { row: 14, down: 4.9, reach: 5.6, up: 6.9 }, // drag on the circles into the bottom edge and hold
  done: { at: 7.2 },
  scrollHome: 7.5,
  remove: { from: 8, to: 9, down: 8.7, up: 9.5, trash: 10.1, restore: 11.8 }, // select Train and Tetto, delete them, put them back
  tidy: 0.6,
} as const;
