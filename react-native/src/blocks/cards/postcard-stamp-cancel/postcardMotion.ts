// Layout, timeline and poses of the Postcard Stamp Cancel. Every helper reached from the UI
// thread is a named worklet declared above its callers.

/** Loop period in seconds. */
export const LOOP = 9;

/** The design box (393 × 759); the screen scales it to fit. */
export const DESIGN_W = 393;
export const DESIGN_H = 759;

/** The postcard (writing side up), in design points. */
export const CARD = { x: 24, y: 116, w: 345, h: 230 };
/** The ink of the cancellation lives in this box on the card (card points). */
export const INK_BOX = { x: 196, y: 6, w: 146, h: 104 };
/** Centre of the date ring, in INK_BOX points; the wavy bars run to its right, over the stamp. */
export const RING = { x: 56, y: 52, r: 33, inner: 22 };
/** The duplex handstamp's rubber block and knob (design points, centred on the tool). */
export const TOOL = { w: 112, h: 72, knob: 26 };
/** The ink pad's centre: the tool rests here. */
export const PAD = { x: 116, y: 522, r: 70 };
/** Where the tool's centre sits when it prints (the middle of the ring and bars). */
export const TARGET = { x: CARD.x + INK_BOX.x + 76, y: CARD.y + INK_BOX.y + RING.y };

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** A damped wobble starting at t0: 0 before it, settling to 0 after about `len` seconds. */
export function kick(t: number, t0: number, len: number, freq: number) {
  'worklet';
  if (t < t0 || t > t0 + len) return 0;
  const u = t - t0;
  return Math.exp((-u * 5) / len) * Math.sin(u * freq) * (1 - seg(t, t0 + len * 0.7, t0 + len));
}

export type Pose = {
  /** Tool centre (design points) and height above the paper, 0 (resting) … 1 (carried). */
  x: number;
  y: number;
  h: number;
  /** Squash of the rubber while it presses (0 … 1). */
  squash: number;
  /** Ink transferred (0 … 1) and its opacity (the card is swapped for a fresh one under the flip). */
  ink: number;
  fade: number;
  /** Card flip in degrees (0 writing side, 180 photo side, 360 back) and its little tilt at the press. */
  flip: number;
  tilt: number;
  /** The trip counter: 0 shows 12, 1 shows 13 (fractional while rolling). */
  count: number;
};

/**
 * The self-playing loop. Rest 0–1.4 s (the tool on its pad); lift 1.4–1.75 s and carry it over the
 * stamp along an arc 1.6–2.4 s; press 2.4–3.2 s (a little rise, the drop, the squash, the ink
 * spreading); lift 3.2–3.55 s; carry it home 3.6–4.5 s and set it down 4.5–4.85 s; hold; the card
 * turns to its photo and back as a fresh card 5.8–7.6 s; rest.
 */
export function autoPose(t: number): Pose {
  'worklet';
  const go = easeInOutCubic(seg(t, 1.6, 2.4));
  const back = easeInOutCubic(seg(t, 3.6, 4.5));
  const u = go * (1 - back);
  const arc = Math.sin(Math.PI * (go < 1 ? go : back)) * 46;
  const x = PAD.x + (TARGET.x - PAD.x) * u;
  const y = PAD.y + (TARGET.y - PAD.y) * u - arc;
  // Height: up, a small extra rise before the drop (anticipation), down, up, home, down.
  let h = easeOutCubic(seg(t, 1.4, 1.75));
  h += 0.1 * Math.sin(Math.PI * seg(t, 2.4, 2.52));
  h -= easeInOutCubic(seg(t, 2.5, 2.72));
  h += easeOutCubic(seg(t, 3.2, 3.55));
  h -= easeInOutCubic(seg(t, 4.5, 4.85));
  const squash = Math.sin(Math.PI * seg(t, 2.72, 3.2)) * 0.9 + 0.35 * Math.sin(Math.PI * seg(t, 4.85, 5.1));
  const ink = easeOutCubic(seg(t, 2.72, 3.15));
  const fade = t < 6.5 ? 1 : 0;
  const turn = easeInOutCubic(seg(t, 5.8, 6.5)) + easeInOutCubic(seg(t, 6.9, 7.6));
  const tilt = -1.6 * kick(t, 2.72, 1.1, 16);
  const count = easeInOutCubic(seg(t, 3.6, 3.95)) * (1 - easeInOutCubic(seg(t, 6.55, 6.85)));
  return { x, y, h, squash, ink, fade, flip: 180 * turn, tilt, count };
}

/** Pose shown under reduced motion: the card stamped, the tool back on its pad. */
export function stillPose(): Pose {
  'worklet';
  return { x: PAD.x, y: PAD.y, h: 0, squash: 0, ink: 1, fade: 1, flip: 0, tilt: 0, count: 1 };
}
