// Louver Theme Switch — the loop, the slats' numbers and the scripted finger, in closed form.
//
// A theme change is one number, `amount`: 0 is the light screen, 1 the dark one. `dir` says which way
// the turn left its rest (+1 towards dark, −1 towards light), so the top slat leads both ways.
// lts-shader.ts turns those two numbers into the picture; a Swift or Kotlin port needs only this file.

export const LOOP = 8;

export const LOUVER = {
  /** slats across the screen's height */
  slats: 14,
  /** how far the last slat starts behind the first, in units of one slat's own turn */
  spread: 0.6,
  /** ease-in-out-back overshoot of a slat's turn (a small wind-up and settle) */
  back: 1.3725,
  /** the camera's distance from the screen, in screen heights */
  camera: 1.35,
  /** slats overlap by this much at rest, pt, so no seam shows */
  overlap: 0.5,
  /** the light, pointing at the viewer from above: y, z (normalised in the shader) */
  lightY: -0.52,
  lightZ: 0.85,
  /** how dark a face turned fully away from the light gets */
  ambient: 0.46,
  /** the moving highlight on a turning slat */
  gloss: 0.08,
  glossPower: 48,
  /** the lit edge of a turning slat: strength, width (pt) */
  edge: 0.07,
  edgeWidth: 1.2,
  /** the contact shadow between slats: strength, e-fold (pt) */
  contact: 0.38,
  contactFold: 3.2,
} as const;

/** the loop's phases, seconds */
export const T = {
  toDark: [0.5, 2.0],
  toLight: [5.0, 6.5],
  /** the ghost finger: shows, presses, leaves, relative to the start of each turn */
  ghostIn: -0.42,
  ghostPress: -0.12,
  ghostOut: 0.3,
} as const;

/** a tap's turn takes this long, seconds, for the whole way */
export const TAP_TURN = 1.25;
/** a drag of this fraction of the screen's height is a whole turn */
export const SCRUB = 0.62;
/** a release projects the drag this far ahead, seconds, to decide commit or spring back */
export const PROJECT = 0.18;
/** the critically damped return after a release, rad/s */
export const OMEGA = 9;
/** a touch hands the screen back this long after it ends, seconds */
export const IDLE = 2;
/** below this distance from a rest the turn counts as finished */
export const REST = 0.0005;

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

/** 0 before a, 1 after b */
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function smooth(v: number) {
  'worklet';
  return v * v * (3 - 2 * v);
}

/** a slat's turn 0…1 → 0…1, with a small wind-up and settle */
export function easeBack(u: number) {
  'worklet';
  const c = LOUVER.back;
  const v = 2 * u;
  if (u < 0.5) return 0.5 * v * v * ((c + 1) * v - c);
  const w = v - 2;
  return 0.5 * (w * w * ((c + 1) * w + c) + 2);
}

/** slat `index` (0 at the top)'s angle in radians: 0 shows the light face, π the dark one */
export function slatAngle(amount: number, dir: number, index: number) {
  'worklet';
  const k = index / (LOUVER.slats - 1);
  const p = dir > 0 ? amount : 1 - amount;
  const e = easeBack(clamp01(p * (1 + LOUVER.spread) - LOUVER.spread * k));
  return Math.PI * (dir > 0 ? e : 1 - e);
}

/** the film's amount at loop time t */
export function filmAmount(t: number) {
  'worklet';
  return seg(t, T.toDark[0], T.toDark[1]) - seg(t, T.toLight[0], T.toLight[1]);
}

/** the film's direction at loop time t */
export function filmDir(t: number) {
  'worklet';
  return t < (T.toDark[1] + T.toLight[0]) / 2 ? 1 : -1;
}

/** how much of a ghost tap shows at `s` seconds from its turn's start */
function tapAlpha(s: number) {
  'worklet';
  return smooth(seg(s, T.ghostIn, T.ghostIn + 0.2)) * (1 - smooth(seg(s, T.ghostOut, T.ghostOut + 0.22)));
}

function tapPress(s: number) {
  'worklet';
  return smooth(seg(s, T.ghostPress - 0.1, T.ghostPress)) * (1 - smooth(seg(s, 0.04, 0.2)));
}

/** the ghost finger: opacity, press 0…1, and which segment it is on (1 Dark, 0 Light) */
export function ghostAt(t: number) {
  'worklet';
  const first = t < (T.toDark[1] + T.toLight[0]) / 2;
  const s = t - (first ? T.toDark[0] : T.toLight[0]);
  return { alpha: tapAlpha(s), press: tapPress(s), segment: first ? 1 : 0 };
}

/** a critically damped approach from x0 with speed v0 to `to` */
export function settleAt(x0: number, v0: number, to: number, tau: number) {
  'worklet';
  const a = x0 - to;
  const b = v0 + OMEGA * a;
  return to + (a + b * tau) * Math.exp(-OMEGA * tau);
}
