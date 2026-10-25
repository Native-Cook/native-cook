/**
 * The Wax Seal Invite's clock. Every value is a closed-form function of the loop time t
 * (seconds), so t = LOOP draws the frame of t = 0. Lengths in points, angles in radians.
 *
 *   0.0 – 1.6   rest: the sealed envelope; a gleam of light slides across the wax (0.2 – 1.4).
 *   1.6 – 2.4   a thumb presses the seal (scale 0.95) and pulls it down 22 pt (rubber-banded).
 *   2.4 – 3.2   seven cracks run out from the middle, staggered 40 ms.
 *   3.2 – 4.2   the seal breaks: its seven pieces pop out and fall, turning, under gravity.
 *   4.2 – 5.2   the flap opens about its top edge (ease-in-out), dark as it goes edge-on, its red
 *               liner lit once it is open.
 *   5.2 – 6.0   the card rises out of the envelope (5.2 – 5.7), comes forward over it and settles,
 *               growing to 1.1× with a small overshoot (5.7 – 6.0); the RSVP buttons fade in.
 *   6.0 – 7.6   hold.
 *   7.6 – 10    in reverse, one step after another: the buttons fade (7.6 – 7.8); the card lifts
 *               off the envelope and goes behind it (7.7 – 8.1), then slides down into it
 *               (8.1 – 8.6); the flap closes (8.6 – 9.2); only then the pieces fly back onto the
 *               closed flap along their own paths (9.2 – 9.75) and the cracks heal
 *               (9.65 – 9.9); rest (9.9 – 10).
 */

export const LOOP = 10;
const TAU = Math.PI * 2;

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

/** Ease-out with a small overshoot (about 5 %). */
export function easeOutBack(x: number) {
  'worklet';
  const u = clamp01(x) - 1;
  const s = 1.2;
  return 1 + u * u * ((s + 1) * u + s);
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 4.37) * 43758.5453;
  return s - Math.floor(s);
}

export function rubber(x: number, limit: number) {
  'worklet';
  return limit * (1 - 1 / (1 + Math.max(0, x) / limit));
}

/** How long the pieces fall before they are gone (s). */
export const FALL = 1.0;
/** The scripted pull on the seal (pt, before the rubber band). */
const PULL = 70;
export const PULL_LIMIT = 30;

export type Pose = {
  /** Seal pressed (0…1) and pulled down (pt, already rubber-banded). */
  press: number;
  pull: number;
  /** Cracks grown (0…1), and seconds the pieces have been falling (0 = whole). */
  crack: number;
  fall: number;
  /** The flap's angle (0 closed … π open). */
  flap: number;
  /** The card: 0 inside, 1 just clear above the envelope, 2 settled in front. */
  card: number;
  /** RSVP buttons shown (0…1). */
  buttons: number;
  /** The gleam's place across the seal (−1.5 … 1.5; off the seal at both ends). */
  gleam: number;
};

export function restPose(): Pose {
  'worklet';
  return { press: 0, pull: 0, crack: 0, fall: 0, flap: 0, card: 0, buttons: 0, gleam: -1.5 };
}

export function loopPose(t: number): Pose {
  'worklet';
  const gleam = -1.5 + 3 * easeInOut(seg(t, 0.2, 1.4));
  // Press and pull; let go as the pieces fall, and carried back to rest by the reform.
  const grip = easeInOut(seg(t, 1.6, 2.4));
  const back = easeInOut(seg(t, 9.2, 9.75));
  const press = (grip - easeOut(seg(t, 3.2, 3.5)) * grip) * 1;
  const pull = rubber(PULL * grip, PULL_LIMIT) * (1 - back);
  // Cracks out, then healed.
  const crack = easeOut(seg(t, 2.4, 3.1)) * (1 - easeInOut(seg(t, 9.65, 9.9)));
  // The fall, and the same path flown backwards.
  let fall = 0;
  if (t >= 3.2 && t < 9.2) fall = Math.min(FALL, t - 3.2);
  else if (t >= 9.2) fall = FALL * (1 - back);
  const flap = Math.PI * (easeInOut(seg(t, 4.2, 5.2)) - easeInOut(seg(t, 8.6, 9.2)));
  let card = 0;
  if (t >= 5.2 && t < 7.7) card = easeInOut(seg(t, 5.2, 5.7)) + easeOutBack(seg(t, 5.7, 6.05));
  else if (t >= 7.7) card = 2 - easeInOut(seg(t, 7.7, 8.1)) - easeInOut(seg(t, 8.1, 8.6));
  const buttons = easeOut(seg(t, 5.85, 6.2)) * (1 - easeOut(seg(t, 7.6, 7.8)));
  return { press, pull, crack, fall, flap, card, buttons, gleam };
}

/** The invitation open and still (reduced motion). */
export function openPose(): Pose {
  'worklet';
  return { press: 0, pull: 0, crack: 1, fall: FALL, flap: Math.PI, card: 2, buttons: 1, gleam: -1.5 };
}

// The cracks: CRACKS polylines of CRACK_POINTS points from the seal's centre outward, past the
// wax's edge. Seeded, so they are the same every loop.
export const CRACKS = 7;
export const CRACK_POINTS = 6;

/** The cracks' points (seal-centred), crack by crack, sorted by angle. */
export function makeCracks(R: number): number[][] {
  const out: number[][] = [];
  for (let i = 0; i < CRACKS; i += 1) {
    const a = (i / CRACKS) * TAU + (hash(i + 1) - 0.5) * 0.5 - Math.PI / 2;
    const pts: number[] = [];
    for (let j = 0; j < CRACK_POINTS; j += 1) {
      const f = j / (CRACK_POINTS - 1);
      const r = R * 1.3 * f;
      const side = j === 0 || j === CRACK_POINTS - 1 ? 0 : (hash(i * 7 + j + 3) - 0.5) * R * 0.16;
      pts.push(Math.cos(a) * r - Math.sin(a) * side, Math.sin(a) * r + Math.cos(a) * side);
    }
    out.push(pts);
  }
  return out;
}

/** Piece i's middle direction (radians). */
export function pieceAngle(cracks: number[][], i: number) {
  'worklet';
  const a = cracks[i];
  const b = cracks[(i + 1) % CRACKS];
  const a0 = Math.atan2(a[a.length - 1], a[a.length - 2]);
  let a1 = Math.atan2(b[b.length - 1], b[b.length - 2]);
  if (a1 < a0) a1 += TAU;
  return (a0 + a1) / 2;
}

/** Piece i after falling `fall` s: [dx, dy, rotation (rad), pivot x, pivot y], seal-centred. */
export function pieceMotion(i: number, mid: number, fall: number, R: number): number[] {
  'worklet';
  const px = Math.cos(mid) * R * 0.55;
  const py = Math.sin(mid) * R * 0.55;
  if (fall <= 0) return [0, 0, 0, px, py];
  const speed = 45 + hash(i + 20) * 55;
  const vx = Math.cos(mid) * speed;
  const vy = Math.sin(mid) * speed * 0.6 - 90 - hash(i + 21) * 50;
  const g = 1500;
  return [vx * fall, vy * fall + 0.5 * g * fall * fall, (hash(i + 22) - 0.5) * 7 * fall, px, py];
}
