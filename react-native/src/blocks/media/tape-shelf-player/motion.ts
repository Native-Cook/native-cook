/** The block's clock: inserting and ejecting a tape as functions of time. Ported from TapeShelfPlayerMotion.swift. */

export const LOOP = 10;
/** The tape the loop plays (the middle of the grid). */
export const LOOP_TAPE = 4;
/** The loop inserts at 0.8 s and ejects at 6.6 s. */
export const INSERT_AT = 0.8;
export const EJECT_AT = 6.6;
/** How long the two sequences take. */
export const INSERT_LENGTH = 2.2;
export const EJECT_LENGTH = 2.2;
/** When, inside the insert sequence, the screen starts to light up. */
export const POWER_ON_AT = 1.7;

export function clamp01(x: number) {
  'worklet';
  return Math.min(Math.max(x, 0), 1);
}
export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp01((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}
function easeInOut(x: number) {
  'worklet';
  const t = clamp01(x);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(x), 3);
}
function easeIn(x: number) {
  'worklet';
  return Math.pow(clamp01(x), 3);
}
export function ramp(a: number, b: number, x: number) {
  'worklet';
  return clamp01((x - a) / (b - a));
}

export type Pose = {
  tape: number;
  /** The sleeve rising off the shelf to let its cassette out (or in): 0 to 1. */
  lift: number;
  /** The cassette sliding out of the sleeve to the right: 0 in, 1 out. */
  slide: number;
  /** The flight from beside the sleeve to above the slot: 0 to 1. */
  flight: number;
  /** Going into the slot (squashed flat as it disappears): 0 to 1. */
  squash: number;
  cassetteAlpha: number;
  /** The screen: 0 off, 1 on; the power line effect runs between. */
  screen: number;
  /** 1 while powering on, −1 while powering off (which picture the transition shows). */
  powering: number;
  led: number;
  /** The player's jolt when the tape seats, in points. */
  bounce: number;
  transport: number;
  pill: number;
  ejectPress: number;
  /** Seconds the tape has been playing (drives the picture on the screen). */
  video: number;
};

export function restPose(): Pose {
  'worklet';
  return {
    tape: LOOP_TAPE,
    lift: 0,
    slide: 0,
    flight: 0,
    squash: 0,
    cassetteAlpha: 0,
    screen: 0,
    powering: 1,
    led: 0,
    bounce: 0,
    transport: 0,
    pill: 1,
    ejectPress: 0,
    video: 0,
  };
}

/** e seconds into inserting tape; after INSERT_LENGTH the tape plays on. */
export function insertPose(e: number, tape: number): Pose {
  'worklet';
  const p = restPose();
  p.tape = tape;
  p.lift = easeOut(e / 0.25) * (1 - smoothstep(0.6, 0.9, e));
  p.slide = easeInOut((e - 0.15) / 0.45);
  p.flight = easeInOut((e - 0.6) / 0.75);
  p.squash = easeIn((e - 1.35) / 0.3);
  p.cassetteAlpha = (e > 0.1 ? 1 : 0) * (1 - ramp(1.55, 1.65, e));
  const tau = e - 1.65;
  p.bounce = tau > 0 ? 3 * Math.exp(-8 * tau) * Math.sin(25 * tau) : 0;
  p.led = e >= 1.65 ? 1 : 0;
  p.screen = ramp(POWER_ON_AT, POWER_ON_AT + 0.35, e);
  p.powering = 1;
  p.transport = easeOut((e - 1.8) / 0.4);
  p.pill = 1 - easeInOut((e - 1.6) / 0.3);
  p.video = Math.max(0, e - POWER_ON_AT);
  return p;
}

/** e seconds into ejecting tape (played for `played` seconds); after EJECT_LENGTH all is at rest. */
export function ejectPose(e: number, tape: number, played: number): Pose {
  'worklet';
  const p = restPose();
  p.tape = tape;
  p.ejectPress = smoothstep(0, 0.06, e) * (1 - smoothstep(0.12, 0.25, e));
  p.screen = 1 - ramp(0, 0.3, e);
  p.powering = -1;
  p.transport = 1 - easeInOut((e - 0.1) / 0.4);
  p.pill = easeOut((e - 0.4) / 0.4);
  p.led = e < 0.3 ? 1 : 0;
  p.squash = 1 - easeOut((e - 0.3) / 0.3);
  p.cassetteAlpha = e >= 1.8 ? 0 : ramp(0.3, 0.4, e);
  p.flight = 1 - easeInOut((e - 0.6) / 0.75);
  p.lift = easeOut((e - 1.2) / 0.25) * (1 - smoothstep(1.8, 2.2, e));
  p.slide = 1 - easeInOut((e - 1.35) / 0.45);
  p.video = played + e;
  return p;
}

/** The loop at time t. */
export function loopPose(t: number): Pose {
  'worklet';
  if (t >= INSERT_AT && t < EJECT_AT) return insertPose(t - INSERT_AT, LOOP_TAPE);
  if (t >= EJECT_AT && t < EJECT_AT + EJECT_LENGTH) return ejectPose(t - EJECT_AT, LOOP_TAPE, EJECT_AT - INSERT_AT - POWER_ON_AT);
  return restPose();
}

/** A small integer hash (PCG), the same in every stack: 0 to 1. */
export function hash(x: number, salt: number) {
  'worklet';
  let h = (Math.imul(x, 747796405) + Math.imul(salt, 2891336453 | 0) + 1) | 0;
  h = Math.imul((h >>> ((h >>> 28) + 4)) ^ h, 277803737);
  h = (h >>> 22) ^ h;
  return (h >>> 0) / 4294967295;
}
