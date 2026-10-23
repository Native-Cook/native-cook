import { Easing } from 'react-native-reanimated';

// Timed from the pin's video (60 fps, read at 10 fps): the hand flicks the cards back and forth,
// opens the film, books two seats and pays; the app returns to the cards. One loop = the video.
export const LOOP_MS = 15800;
export const IDLE_RESUME_MS = 3500;

export type Cue =
  | { at: number; kind: 'page'; to: number }
  | { at: number; kind: 'open' | 'buy' | 'pay' | 'reset' }
  | { at: number; kind: 'cast'; dx: number }
  | { at: number; kind: 'seat'; seat: string };

export const SCRIPT: Cue[] = [
  { at: 780, kind: 'page', to: 0 },
  { at: 1480, kind: 'page', to: 1 },
  { at: 1940, kind: 'page', to: 0 },
  { at: 2360, kind: 'page', to: 1 },
  { at: 2860, kind: 'page', to: 2 },
  { at: 4100, kind: 'page', to: 1 },
  { at: 4760, kind: 'open' },
  { at: 6050, kind: 'cast', dx: -72 },
  { at: 6950, kind: 'cast', dx: 0 },
  { at: 7650, kind: 'buy' },
  { at: 11300, kind: 'seat', seat: '7-6' },
  { at: 11900, kind: 'seat', seat: '7-7' },
  { at: 13100, kind: 'pay' },
  { at: 15100, kind: 'reset' },
];

// A flick lands on a spring: quick, a hair of overshoot, settled in about 380 ms.
export const PAGE_SPRING = { damping: 22, stiffness: 210, mass: 0.9 };
export const CAST_SPRING = { damping: 24, stiffness: 140, mass: 1 };

export const OPEN = { ms: 680, easing: Easing.bezier(0.32, 0.72, 0, 1) };
export const COVER = { delay: 40, ms: 560, easing: Easing.bezier(0.55, 0, 0.35, 1) }; // black circle from the button
export const BUILD = { delay: 620, rowStagger: 70, rowMs: 300, chromeAt: 600, chromeMs: 360 };
export const FOOTAGE = { inAt: 600, inMs: 400, outAt: 1820, outMs: 420, drift: 2200 };
export const SEAT_POP = { damping: 11, stiffness: 320, mass: 0.6 };
export const PAY = {
  shrinkMs: 340, blackAt: 260, blackMs: 240,
  faceAt: 520, faceMs: 560, spinAt: 1100, spinMs: 420, checkAt: 1520, checkMs: 380,
};
export const RESET = { ms: 420 };
export const PRESS_SCALE = 0.97;
export const REDUCED_FADE_MS = 200;
