// Wheel geometry, measured by tracking the stamps through the pin's swipes.
// p = signed distance from the centre in items (neighbour = 1). x is linear in p;
// rise, tilt and scale follow these curves (tilt is less than the arc angle and the
// stamps shrink as they climb, so it is not a rigid wheel).
export const WHEEL = {
  stepX: 251, // pt per item (450 px in the pin)
  table: [0, 0.25, 0.5, 0.75, 1, 1.5, 2.2],
  rise: [0, 9.5, 38, 84, 159, 300, 480], // pt upward
  tilt: [0, 7, 14, 21, 31, 44, 58], // degrees, towards the outside
  scale: [1, 0.875, 0.755, 0.69, 0.62, 0.55, 0.5],
  hideBeyond: 2.2,
};

// Snap after a release by hand, carrying the release velocity.
export const SNAP = { duration: 400, dampingRatio: 0.8 };
export const RUBBER = 0.35;
export const PROJECT_S = 0.2; // release target = position + velocity x this

// The pin's swipes, tracked frame by frame (frame n at (n - 1) / 30 s): the finger drags the
// wheel for 100-133 ms, speeding up a little (u -> 0.6u + 0.4u^2), lets go, and the rest of the
// way is a pure exponential with tau = 100 ms (the gap closes by 0.72 a frame), no overshoot.
const TAU_MS = 100;
const SETTLE_MS = 700;
const K = SETTLE_MS / TAU_MS;
function settle(u: number) {
  'worklet';
  return (1 - Math.exp(-K * u)) / (1 - Math.exp(-K));
}
function grabEase(u: number) {
  'worklet';
  return 0.6 * u + 0.4 * u * u;
}

// The nudge at 1.6 s: a touch that pushes the wheel 0.047 item for two frames.
export const NUDGE = { at: 1567, items: 0.047, inMs: 33, holdMs: 67, outMs: 33 };

export const START_INDEX = 2; // Games
export const LOOP_MS = 11000;
export const RESUME_MS = 3500;
export const COUNT = 4;

// dir -1: the finger moves right and the previous stamp comes in; +1 the next one.
export type Swipe = { at: number; dir: -1 | 1; dragMs: number; dragItems: number };
export const SWIPES: Swipe[] = [
  { at: 2333, dir: -1, dragMs: 100, dragItems: 0.46 }, // -> Mixed
  { at: 3167, dir: 1, dragMs: 100, dragItems: 0.43 }, // -> Games
  { at: 4100, dir: -1, dragMs: 100, dragItems: 0.42 }, // -> Mixed
  { at: 4867, dir: -1, dragMs: 133, dragItems: 0.35 }, // -> Organisation (the pin ends here)
  { at: 7300, dir: 1, dragMs: 100, dragItems: 0.43 }, // -> Mixed
  { at: 8200, dir: 1, dragMs: 100, dragItems: 0.43 }, // -> Games, rest until the loop restarts
];

// The wheel's position at loop time t: a pure function, so the loop is exact and any
// moment can be frozen for a pixel comparison.
export function wheelAt(t: number) {
  'worklet';
  let index = START_INDEX;
  let pos = START_INDEX;
  const n = t - NUDGE.at;
  if (n > 0 && n < NUDGE.inMs + NUDGE.holdMs + NUDGE.outMs) {
    const k =
      n < NUDGE.inMs ? n / NUDGE.inMs : n < NUDGE.inMs + NUDGE.holdMs ? 1 : 1 - (n - NUDGE.inMs - NUDGE.holdMs) / NUDGE.outMs;
    return START_INDEX - NUDGE.items * k;
  }
  for (let i = 0; i < SWIPES.length; i++) {
    const s = SWIPES[i];
    if (s.at > t) break;
    const local = t - s.at;
    const target = Math.max(0, Math.min(COUNT - 1, index + s.dir));
    const grab = index + s.dir * s.dragItems;
    if (local < s.dragMs) pos = index + (grab - index) * grabEase(local / s.dragMs);
    else if (local < s.dragMs + SETTLE_MS) pos = grab + (target - grab) * settle((local - s.dragMs) / SETTLE_MS);
    else pos = target;
    index = target;
  }
  return pos;
}

// Mascot blinks: [blob index, start ms]; each closes for BLINK_MS.
export const BLINK_MS = 130;
export const BLINKS: [number, number][] = [
  [0, 120],
  [2, 2050],
  [0, 2400],
  [2, 2400],
  [1, 3000],
  [1, 4930],
  [0, 5500],
  [2, 6000],
  [1, 8300],
  [0, 9800],
  [2, 9800],
];
export function eyesAt(t: number) {
  return [0, 1, 2].map((b) => BLINKS.some(([i, at]) => i === b && t >= at && t < at + BLINK_MS));
}

export const FADE_MS = 180; // Reduce Motion cross-fade
