/**
 * The Letterpress Headline's clock. Every value is a closed-form function of the loop time t
 * (seconds), so t = LOOP draws the frame of t = 0.
 *
 *   0.0 – 1.4   rest: blank cotton paper.
 *   1.4 – 4.0   the headline is printed letter by letter, one every 0.21 s; each letter's metal
 *               type block comes down (0.2 s, ease-in-out: it shrinks toward the paper and its
 *               shadow tightens), presses (0.1 s: the deboss and the ink go in with a 6 %
 *               overshoot) and lifts away (0.2 s, ease-out, fading).
 *   4.0 – 4.9   an ink roller sweeps the second line on, left to right (ease-in-out).
 *   4.8 – 6.0   the lamp swings across and back, so the deboss's light and shade move.
 *   6.0 – 6.8   hold.
 *   6.8 – 8.2   the impression and the ink fade out (ease-in-out), back to blank paper.
 *   8.2 – 9.0   rest.
 */

export const LOOP = 9;

/** Letters printed at most (the headline "Made by hand." has 11). */
export const MAX_GLYPHS = 12;
const FIRST = 1.4;
const EVERY = 0.21;
const DOWN = 0.2;
const PRESS = 0.1;
const UP = 0.2;

export const LIGHT_AZ = -2.2;
export const LIGHT_EL = 0.75;

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOut(x: number) {
  'worklet';
  const u = 1 - clamp01(x);
  return 1 - u * u * u;
}

export function easeInOut(x: number) {
  'worklet';
  const u = clamp01(x);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

export function rubber(x: number, limit: number) {
  'worklet';
  const s = x < 0 ? -1 : 1;
  return s * limit * (1 - 1 / (1 + Math.abs(x) / limit));
}

export type Pose = {
  /** Each letter's impression (0 … 1, a little more at the moment of pressing). */
  depth: number[];
  /** Each letter's type block: 0 away … 1 on the paper. */
  block: number[];
  /** True while a block is coming down (false: lifting away, which also fades it). */
  falling: boolean[];
  /** The second line: roller position (0 … 1 across), ink shown up to it, the ink's opacity. */
  roller: number;
  rollerAlpha: number;
  ink: number;
  inkAlpha: number;
  /** The lamp. */
  light: number;
  elevation: number;
};

/** One letter's print at loop time t: [depth, block, falling]. */
function letter(i: number, t: number): [number, number, boolean] {
  'worklet';
  const s = FIRST + i * EVERY;
  const down = easeInOut(seg(t, s, s + DOWN));
  const pressed = seg(t, s + DOWN, s + DOWN + PRESS);
  const up = easeOut(seg(t, s + DOWN + PRESS, s + DOWN + PRESS + UP));
  // The deboss goes in with the press, overshooting a little as the paper gives, then settles.
  const depth = t < s + DOWN ? 0 : easeOut(pressed) + 0.06 * Math.sin(Math.PI * pressed);
  return [depth, down * (1 - up), up === 0];
}

export function loopPose(t: number, count: number): Pose {
  'worklet';
  const fade = 1 - easeInOut(seg(t, 6.8, 8.2));
  const depth: number[] = [];
  const block: number[] = [];
  const falling: boolean[] = [];
  for (let i = 0; i < MAX_GLYPHS; i += 1) {
    if (i >= count) {
      depth.push(0);
      block.push(0);
      falling.push(true);
      continue;
    }
    const [d, b, f] = letter(i, t);
    depth.push(d * fade);
    block.push(b);
    falling.push(f);
  }
  const roll = easeInOut(seg(t, 4.0, 4.9));
  const rollerAlpha = seg(t, 3.9, 4.05) * (1 - seg(t, 4.85, 5.0));
  const swing = Math.sin(Math.PI * easeInOut(seg(t, 4.8, 6.0)));
  return {
    depth,
    block,
    falling,
    roller: roll,
    rollerAlpha,
    ink: t >= 4.0 ? roll : 0,
    inkAlpha: fade,
    light: LIGHT_AZ + 1.5 * swing,
    elevation: LIGHT_EL - 0.2 * swing,
  };
}

/** Printed and still (reduced motion). */
export function printedPose(count: number): Pose {
  'worklet';
  const depth: number[] = [];
  const block: number[] = [];
  const falling: boolean[] = [];
  for (let i = 0; i < MAX_GLYPHS; i += 1) {
    depth.push(i < count ? 1 : 0);
    block.push(0);
    falling.push(true);
  }
  return { depth, block, falling, roller: 1, rollerAlpha: 0, ink: 1, inkAlpha: 1, light: LIGHT_AZ, elevation: LIGHT_EL };
}
