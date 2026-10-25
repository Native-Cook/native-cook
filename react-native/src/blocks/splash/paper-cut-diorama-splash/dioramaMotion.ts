import { DEPTH, LAYERS } from './dioramaArt';

/*
 Paper Cut Diorama Splash — the timeline (LOOP = 9 s), a pure function of the scene time t.
 The rest pose (and the poster) is the finished diorama with the logo lit, so t = 0 and t = LOOP
 are the same frame and the screen is never blank.

   0.0 – 0.4   rest: the lit diorama, the lamp breathing ±6 % (three whole sines a loop).
   0.4 – 1.8   the scene sways once as if the phone were tilted (one whole sine, parallax by depth).
   1.8 – 3.0   the camera flies through the logo's door: every layer scales about the door, the
               front frame fastest (×40) and the sky slowest (×17), ease-in-out on a log scale so
               the speed feels even; the lamp behind the door fills the screen and the home screen
               fades in over it (2.55 – 3.0, scale 1.04 → 1).
   3.0 – 5.0   home.
   5.0 – 5.6   home recedes (opacity 1 → 0, scale 1 → 0.96, ease-in-out) while the diorama
               rebuilds: the six layers rise from below as one piece (5.0 – 6.2, ease-out-back,
               4 % overshoot), each layer behind the frame trailing it by up to 40 pt × (1 − depth)
               mid-rise (a sine bump, 0 at both ends), so the arch carries the scene up inside it
               and nothing shows outside the window.
   6.4 – 7.1   the lamp comes back on behind the door and the wordmark (ease-out).
   7.1 – 9.0   rest.
 */

export const LOOP = 9;
const SWAY: [number, number] = [0.4, 1.8];
const ZOOM: [number, number] = [1.8, 3.0];
const ZOOM_MAX = 40;
const HOME_IN: [number, number] = [2.55, 3.0];
const HOME_OUT: [number, number] = [5.0, 5.6];
const RISE_AT = 5.0;
const RISE_LEN = 1.2;
const RISE_LAG = 40;
const LIGHT: [number, number] = [6.4, 7.1];
/** Where a finger takes the scene: the rest pose (t = 0, the same frame as t = LOOP). */
export const REST_SCENE = 0;
/** After this, a finger plays the rebuild forward to LOOP rather than back through home. */
export const REBUILD_AT = RISE_AT;

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

function easeOutCubic(u: number) {
  'worklet';
  const v = 1 - u;
  return 1 - v * v * v;
}

function easeInOutCubic(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

function easeOutBack(u: number, c1: number) {
  'worklet';
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2);
}

/** A finger's rubber band: follows 1:1 near zero, never past ±1.6. */
export function rubber(v: number) {
  'worklet';
  return v / (1 + Math.abs(v) / 1.6);
}

export type ScenePose = {
  /** Per layer: rise (pt still below its place), sway x, sway y, scale. */
  layers: number[];
  light: number;
  /** Layers drawn at all (0 while the home screen covers them). */
  visible: number;
  home: number;
  homeScale: number;
};

export function scenePose(t: number, riseDist: number): ScenePose {
  'worklet';
  const rebuilding = t >= RISE_AT;
  const zu = rebuilding ? 0 : easeInOutCubic(seg(t, ZOOM[0], ZOOM[1]));
  const S = Math.exp(Math.log(ZOOM_MAX) * zu);
  const sway = Math.sin(2 * Math.PI * seg(t, SWAY[0], SWAY[1]));
  const layers: number[] = [];
  for (let i = 0; i < LAYERS; i += 1) {
    // The whole diorama rises on one curve; each layer behind the frame trails it a little (up
    // to 23 pt for the sky, the same as a ~70 ms stagger at that speed) and catches up as it
    // settles, so the window is never empty.
    const d = DEPTH[i];
    const u = seg(t, RISE_AT, RISE_AT + RISE_LEN);
    const rise = rebuilding ? (1 - easeOutBack(u, 0.9)) * riseDist + (1 - d) * RISE_LAG * Math.sin(Math.PI * u) : 0;
    layers.push(rise, 16 * sway * (d - 0.66) * 2.2, -3 * sway * sway * (d - 0.4), 1 + (S - 1) * d);
  }
  const breath = 1 + 0.06 * Math.sin((2 * Math.PI * 3 * t) / LOOP);
  const on = rebuilding ? easeOutCubic(seg(t, LIGHT[0], LIGHT[1])) : 1;
  const light = on * breath * (rebuilding ? 1 : 1 + 1.5 * seg(t, 2.0, 2.8));
  const homeIn = easeOutCubic(seg(t, HOME_IN[0], HOME_IN[1]));
  const out = easeInOutCubic(seg(t, HOME_OUT[0], HOME_OUT[1]));
  return {
    layers,
    light,
    visible: t < 3.05 || rebuilding ? 1 : 0,
    home: homeIn * (1 - out),
    homeScale: rebuilding ? 1 - 0.04 * out : 1.04 - 0.04 * homeIn,
  };
}
