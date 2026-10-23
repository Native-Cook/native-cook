// Everything on the screen is a function of the time since the step last changed, so a touch and
// the loop that plays by itself make exactly the same pictures. Times are in seconds.

/** autoPlay: one pass through the three steps every LOOP seconds, the footer pressed before each change. */
export const LOOP = 10;
export const AUTO_TAPS = [3.4, 6, 9];
export const PRESS_FOR = 0.15;
/** After a touch autoPlay waits this long before it goes back to the first step and starts over. */
export const USER_HOLD = 3;

// Back to the first step: the tags leave, the colour drains, and RETURN later the wave is grey and
// still. The colour then sets off again by itself.
export const RETURN = 1;
export const SHRINK = 0.2;
export const DRAIN_AFTER = 0.1;
export const DRAIN = 0.6;
export const FILL_AFTER = 0.4;
export const FILL = 1.9;
// A tap before the colour has arrived: it runs the rest of the way in this time.
export const FILL_FINISH = 0.3;

export const TEXT_OUT = 0.2;
export const TEXT_IN_AFTER = 0.12;
export const TEXT_IN = 0.3;
export const TEXT_SHIFT = 12;

// The tags pop out of the tip of their tail one after another, on a spring that overshoots by an eighth.
export const TAG_AFTER = 0.15;
export const TAG_STAGGER = 0.1;
export const TAG_MASS = 1;
export const TAG_STIFFNESS = 260;
export const TAG_DAMPING = 17.7;
// As they arrive the crests rise by FLEX points and settle with one soft bounce.
export const FLEX = 6;
export const FLEX_DECAY = 7;
export const FLEX_SPEED = 11;
// The gain that makes the first swing of exp(-FLEX_DECAY t) · sin(FLEX_SPEED t) exactly FLEX high.
const FLEX_PEAK = Math.atan(FLEX_SPEED / FLEX_DECAY) / FLEX_SPEED;
const FLEX_GAIN = FLEX / (Math.exp(-FLEX_DECAY * FLEX_PEAK) * Math.sin(FLEX_SPEED * FLEX_PEAK));

// Third step: each tag squashes flat and comes back with its new emoji, and the second one moves
// along to the wave's last slope.
export const FLIP = 0.25;
export const FLIP_STAGGER = 0.08;
export const SLIDE = 0.45;
export const SLIDE_X = 40;
export const SLIDE_Y = 10;

export const BOB = 2;
export const BOB_PERIOD = 2.5;
// A tapped tag hops and its emoji turns once.
export const JUMP = 0.45;
export const JUMP_HEIGHT = 12;
export const SPIN = 0.55;

export const FLASH_IN = 0.05;
export const FLASH_OUT = 0.25;

/** Half of a tag with its tail: a squash about the tag's middle is a scale about the tip plus this much travel. */
export const TAG_HALF = 23;

/** Where the story is: the step shown, the one before it, when it changed, and how far the colour had got then. */
export type Scene = { step: number; from: number; at: number; fill: number };

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

export function easeIn(t: number) {
  'worklet';
  const u = clamp01(t);
  return u * u;
}

export function easeOut(t: number) {
  'worklet';
  const u = 1 - clamp01(t);
  return 1 - u * u * u;
}

export function easeInOut(t: number) {
  'worklet';
  const u = clamp01(t);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** The tags' spring from 0 to 1, t seconds after it was let go. */
export function spring(t: number) {
  'worklet';
  if (t <= 0) return 0;
  const omega = Math.sqrt(TAG_STIFFNESS / TAG_MASS);
  const zeta = TAG_DAMPING / (2 * Math.sqrt(TAG_STIFFNESS * TAG_MASS));
  const damped = omega * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * omega * t) * (Math.cos(damped * t) + ((zeta * omega) / damped) * Math.sin(damped * t));
}

/** How much of the wave is coloured, 0 to 1 along its length. */
export function fillAt(scene: Scene, t: number) {
  'worklet';
  const e = t - scene.at;
  if (scene.step !== 0) return scene.fill + (1 - scene.fill) * easeInOut(e / FILL_FINISH);
  if (e < RETURN) return scene.fill * (1 - easeInOut((e - DRAIN_AFTER) / DRAIN));
  return easeInOut((e - RETURN - FILL_AFTER) / FILL);
}

/** How many points the crests stand above their place. */
export function flexAt(scene: Scene, t: number, still: boolean) {
  'worklet';
  const e = t - scene.at;
  if (still || scene.step !== 1 || e <= 0) return 0;
  return FLEX_GAIN * Math.exp(-FLEX_DECAY * e) * Math.sin(FLEX_SPEED * e);
}

/** When nothing moves any more; never, while the tags bob. */
export function settledAt(scene: Scene, still: boolean) {
  'worklet';
  if (scene.step === 0) return scene.at + RETURN + FILL_AFTER + FILL;
  return still ? scene.at + 1 : Infinity;
}

/** The text of one step: it leaves upwards and the next one comes in from below. */
export function textPose(index: number, scene: Scene, t: number, still: boolean) {
  'worklet';
  const e = t - scene.at;
  if (scene.step === scene.from) return { opacity: index === scene.step ? 1 : 0, y: 0 };
  if (index === scene.step) {
    const p = easeOut((e - TEXT_IN_AFTER) / TEXT_IN);
    return { opacity: p, y: still ? 0 : TEXT_SHIFT * (1 - p) };
  }
  if (index === scene.from) {
    const p = easeIn(e / TEXT_OUT);
    return { opacity: 1 - p, y: still ? 0 : -TEXT_SHIFT * p };
  }
  return { opacity: 0, y: 0 };
}

/**
 * One tag: its offset from its place, its scale about the tip of its tail, and whether it shows
 * the emoji of the last step (`late`). `jumped` is when it was last tapped.
 */
export function tagPose(index: number, scene: Scene, t: number, jumped: number, still: boolean) {
  'worklet';
  const e = t - scene.at;
  let show = 0;
  let squash = 1;
  let slide = 0;
  let late = false;
  if (scene.step === 1) {
    const since = e - TAG_AFTER - index * TAG_STAGGER;
    show = still ? clamp01(since / SHRINK) : spring(since);
  } else if (scene.step === 2) {
    const p = clamp01((e - index * FLIP_STAGGER) / FLIP);
    show = 1;
    squash = Math.abs(Math.cos(Math.PI * p));
    late = p >= 0.5;
    slide = still ? (late ? 1 : 0) : easeInOut(e / SLIDE);
  } else if (scene.from !== 0) {
    show = 1 - easeIn(e / SHRINK);
    late = scene.from === 2;
    slide = late ? 1 : 0;
  }
  const x = index === 1 ? slide * SLIDE_X : 0;
  const y = index === 1 ? slide * SLIDE_Y : 0;
  // Without motion a tag fades where the others scale, and changes its emoji at the bottom of a dip.
  if (still) return { x, y, scaleX: 1, scaleY: 1, opacity: Math.min(1, show) * squash, late, spin: 0 };
  const bob = BOB * Math.sin((t / BOB_PERIOD + index / 3) * 2 * Math.PI) * Math.min(1, show);
  const hop = clamp01((t - jumped) / JUMP);
  // The tag under the wave has its tail on top, so its middle is below the tip.
  const middle = (index === 2 ? 1 : -1) * TAG_HALF;
  return {
    x,
    y: y + bob - JUMP_HEIGHT * 4 * hop * (1 - hop) + middle * (1 - squash) * show,
    scaleX: show,
    scaleY: show * squash,
    opacity: smooth(show, 0, 0.1),
    late,
    spin: 360 * easeOut((t - jumped) / SPIN),
  };
}

/** The grey pill behind the footer while it is pressed. */
export function flashAt(press: { at: number; until: number }, t: number) {
  'worklet';
  return smooth(t, press.at, press.at + FLASH_IN) * (1 - smooth(t, press.until, press.until + FLASH_OUT));
}
