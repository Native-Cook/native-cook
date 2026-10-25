/*
 Wind Chime List — the sessions, the scripted scroll and every tube's swing, as pure functions
 of the loop time, plus the small oscillator a real scroll drives.

 LOOP = 9 s
   0.0 – 1.5   rest at the top of the list.
   1.5 – 4.0   a fling down to 420 pt (fast start, exponential slow-down). The tubes lag the
               scroll: each one is a damped pendulum (ζ 0.22, 1.0–2.4 Hz by its length) kicked
               when the scroll speeds up and slows down, 40 ms later per row, neighbours swinging
               opposite ways, so at the peaks they touch and ring.
   4.2         row 7 (mid-screen) is struck: it swings hard (ζ 0.12) and its waveform plays for 2.4 s.
   5.2 – 7.4   the list scrolls back up (ease-in-out), kicking the tubes again.
   7.4 – 9.0   the swings die out (a window closes the last 0.6 s so t = 9 equals t = 0).
*/

export const LOOP = 9;
export const ROW_H = 92;
export const TUBE_H = 64;
export const STRING_H = ROW_H - TUBE_H;
export const ROW_DELAY = 0.04;
const FLING_AT = 1.5;
const FLING_END = 4.0;
const FLING_TO = 420;
const BACK_AT = 5.2;
const BACK_END = 7.4;
/** The struck row: the seventh, which sits mid-screen at the end of the fling. */
export const STRIKE_ROW = 6;
export const STRIKE_AT = 4.2;
export const PLAY_LEN = 2.4;
/**
 A row turns by VISUAL_TURN of its swing angle and sways sideways SWAY pt per radian, so two
 neighbours swinging apart by TOUCH close the string gap to within a few points: they touch.
*/
export const VISUAL_TURN = 0.35;
export const SWAY = 26;
export const TOUCH = 0.37;
export const RING_LEN = 0.55;

export type Session = { title: string; meta: string; minutes: number; brass: boolean; shrink: number; seed: number };

export const SESSIONS: Session[] = [
  { title: 'Low Tide Bowls', meta: 'Singing bowls', minutes: 24, brass: true, shrink: 0, seed: 1 },
  { title: 'Cedar Hollow', meta: 'Bamboo chimes', minutes: 18, brass: false, shrink: 22, seed: 2 },
  { title: 'Slow Lantern', meta: 'Gong & drone', minutes: 32, brass: true, shrink: 10, seed: 3 },
  { title: 'Morning Kelp', meta: 'Crystal bowls', minutes: 15, brass: true, shrink: 30, seed: 4 },
  { title: 'Rain on Slate', meta: 'Field recording', minutes: 40, brass: false, shrink: 6, seed: 5 },
  { title: 'Night Ferry', meta: 'Drone', minutes: 28, brass: true, shrink: 18, seed: 6 },
  { title: 'Moss Garden', meta: 'Bamboo chimes', minutes: 12, brass: false, shrink: 36, seed: 7 },
  { title: 'Ember Circle', meta: 'Frame drum', minutes: 22, brass: true, shrink: 4, seed: 8 },
  { title: 'Still Harbour', meta: 'Singing bowls', minutes: 35, brass: true, shrink: 26, seed: 9 },
  { title: 'Paper Lanterns', meta: 'Kalimba', minutes: 16, brass: false, shrink: 14, seed: 10 },
  { title: 'Fog Bell', meta: 'Bells & breath', minutes: 20, brass: true, shrink: 32, seed: 11 },
  { title: 'Riverstone', meta: 'Water & bowls', minutes: 45, brass: false, shrink: 8, seed: 12 },
  { title: 'Quiet Orchard', meta: 'Chimes', minutes: 14, brass: true, shrink: 20, seed: 13 },
  { title: 'Last Light', meta: 'Gong bath', minutes: 50, brass: true, shrink: 2, seed: 14 },
];

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** Row i's pendulum: angular frequency (rad/s, shorter tubes ring faster) and swing direction. */
export function rowOmega(i: number) {
  'worklet';
  const shrink = SESSIONS[i % SESSIONS.length].shrink;
  return 2 * Math.PI * (1.0 + 1.4 * (shrink / 36) * 0.7 + 0.25 * hash(i + 7));
}

export function rowDir(i: number) {
  'worklet';
  return (i % 2 === 0 ? 1 : -1) * (0.8 + 0.4 * hash(i * 3 + 1));
}

/** The scripted scroll offset (pt). */
export function scrollAt(t: number) {
  'worklet';
  if (t < FLING_AT) return 0;
  if (t < BACK_AT) {
    const u = seg(t, FLING_AT, FLING_END);
    return (FLING_TO * (1 - Math.exp(-4.2 * u))) / (1 - Math.exp(-4.2));
  }
  return FLING_TO * (1 - easeInOutCubic(seg(t, BACK_AT, BACK_END)));
}

/** Damped impulse response, normalised to a unit first peak. */
function pulse(tau: number, w: number, zeta: number) {
  'worklet';
  if (tau <= 0) return 0;
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return Math.exp(-zeta * w * tau) * Math.sin(wd * tau);
}

// Kicks the script gives every row: [time, strength] — the fling starting and braking, the
// return starting and stopping.
const KICKS = [
  [1.5, 0.22],
  [2.15, -0.13],
  [3.3, -0.07],
  [5.2, -0.15],
  [6.3, 0.07],
  [7.25, 0.13],
];

/** Row i's swing angle (rad) at loop time t. */
export function swingAt(t: number, i: number) {
  'worklet';
  const w = rowOmega(i);
  const d = rowDir(i);
  let a = 0;
  for (let k = 0; k < KICKS.length; k += 1) a += KICKS[k][1] * d * pulse(t - KICKS[k][0] - i * ROW_DELAY, w, 0.22);
  if (i === STRIKE_ROW) a += 0.32 * pulse(t - STRIKE_AT, w, 0.12);
  // Tiny idle sway, whole cycles of the loop, so the rest still breathes.
  a += 0.012 * Math.sin((2 * Math.PI * 2 * t) / LOOP + i * 0.7);
  return a * (1 - easeInOutCubic(seg(t, 8.3, 8.95)));
}

/** Seconds since rows i and i + 1 last started touching (−1: not within RING_LEN), and the side (±1). */
export function ringAt(t: number, i: number): number[] {
  'worklet';
  const step = 1 / 30;
  let touching = false;
  let side = 0;
  for (let k = 0; k * step <= RING_LEN + step; k += 1) {
    const at = t - k * step;
    if (at < 0) break;
    const delta = swingAt(at, i) - swingAt(at, i + 1);
    const now = Math.abs(delta) > TOUCH;
    if (now) {
      touching = true;
      side = delta > 0 ? 1 : -1;
    } else if (touching) {
      return [(k - 1) * step, side];
    }
  }
  return [-1, 0];
}

/** The strike row's waveform level τ seconds after a strike (0…1). */
export function playEnvelope(tau: number) {
  'worklet';
  if (tau < 0 || tau > PLAY_LEN) return 0;
  return clamp01(tau / 0.06) * Math.pow(1 - tau / PLAY_LEN, 1.6);
}

/** Bar k of a waveform (static height 0…1). */
export function barHeight(seed: number, k: number) {
  'worklet';
  return 0.25 + 0.75 * Math.abs(Math.sin(k * 0.9 + seed * 1.7) * 0.6 + Math.sin(k * 0.37 + seed) * 0.4);
}
