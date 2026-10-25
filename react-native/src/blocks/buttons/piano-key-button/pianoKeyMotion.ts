/*
 Piano Key Button — timing. Every frame of the self-playing loop is a pure function of the loop
 time t (seconds); under a finger the same pose is built from per-key press times instead.

 LOOP = 8 s:
   0.0 – 1.3   rest (the poster: three ivory keys, streak 12 days)
   1.3 – 2.0   key 1 (C) down and up      2.1 – 2.8  key 2 (E)      2.9 – 3.6  key 3 (G)
   4.2         chord C + G down, held to 6.4; 5.2 – 5.6 the streak rolls 12 → 13 and the chip pulses
   6.4 – 8.0   release and settle; 7.0 – 7.4 the streak rolls back to 12 so 8.0 equals 0.0
*/

export const LOOP = 8;
export const KEY_COUNT = 3;

/** [key, down, up] in loop seconds. */
export const SCRIPT: readonly (readonly [number, number, number])[] = [
  [0, 1.3, 2.0],
  [1, 2.1, 2.8],
  [2, 2.9, 3.6],
  [0, 4.2, 6.4],
  [2, 4.2, 6.4],
];
export const STREAK_FROM = 12;
const STREAK_UP: readonly [number, number] = [5.2, 5.6];
const STREAK_BACK: readonly [number, number] = [7.0, 7.4];

/** The key's spring: ω 40 rad/s, damping ratio 0.62 (≈ 8 % overshoot, settled in ~0.16 s). */
export const KEY_OMEGA = 40;
export const KEY_ZETA = 0.62;
/** The same spring for Reanimated's withSpring (mass 1). */
export const KEY_SPRING = { mass: 1, stiffness: KEY_OMEGA * KEY_OMEGA, damping: 2 * KEY_ZETA * KEY_OMEGA };

/** A ring wave lives this long (s); three rings leave each press 0.12 s apart. */
export const RING_LIFE = 1.3;
export const RING_DELAY = 0.12;
/** After the last touch the loop waits this long, then eases back to rest in BACK_FOR. */
export const IDLE_BEFORE_BACK = 2;
export const BACK_FOR = 0.6;

export type KeyState = {
  /** Seconds since this key last went down (large when it never did). */
  downAge: number;
  /** Seconds since it came back up, or -1 while it is held. */
  upAge: number;
  /** Key travel 0 (rest) … 1 (on the keybed), may overshoot a little. */
  depth: number;
  /** 0 = the CTA label shows, 1 = the note name shows. */
  label: number;
};

export type Pose = {
  keys: KeyState[];
  /** Staff note heads lit 0…1: C, E, G, chord. */
  notes: number[];
  /** The streak as a number that rests on whole days. */
  streak: number;
  /** Chip pulse 0…1. */
  pulse: number;
};

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
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
  const v = clamp01(x);
  return v < 0.5 ? 4 * v * v * v : 1 - Math.pow(-2 * v + 2, 3) / 2;
}

/** Unit step response of an underdamped spring, 0 before τ = 0. */
export function springStep(tau: number, omega: number, zeta: number) {
  'worklet';
  if (tau <= 0) return 0;
  const wd = omega * Math.sqrt(1 - zeta * zeta);
  const e = Math.exp(-zeta * omega * tau);
  return 1 - e * (Math.cos(wd * tau) + ((zeta * omega) / wd) * Math.sin(wd * tau));
}

/** The hammer behind a key: flicks up in 60 ms, falls back with one small bounce. */
export function hammerLift(downAge: number) {
  'worklet';
  if (downAge < 0 || downAge > 1) return 0;
  if (downAge < 0.06) return easeOut(downAge / 0.06);
  const a = downAge - 0.06;
  return Math.max(-0.12, Math.exp(-a * 9) * Math.cos(a * 20));
}

/** How hard the key's strings ring: decays while held, damped fast once the key is up. */
export function stringRing(k: KeyState) {
  'worklet';
  if (k.downAge > 6) return 0;
  const ring = Math.exp(-k.downAge * 1.4);
  return k.upAge < 0 ? ring : ring * Math.exp(-k.upAge * 9);
}

/** A soft glow on the key, strongest at the strike. */
export function keyGlow(k: KeyState) {
  'worklet';
  if (k.downAge > 6) return 0;
  const strike = Math.exp(-k.downAge * 5) * 0.5;
  const held = k.upAge < 0 ? 0.12 : 0.12 * Math.exp(-k.upAge * 6);
  return strike + held;
}

function labelAt(t: number, down: number, up: number) {
  'worklet';
  return easeInOut(seg(t, down + 0.02, down + 0.26)) - easeInOut(seg(t, up + 0.35, up + 0.62));
}

/** The pose of the self-playing loop at loop time t. */
export function loopPose(t: number): Pose {
  'worklet';
  const keys: KeyState[] = [];
  for (let i = 0; i < KEY_COUNT; i += 1) keys.push({ downAge: 99, upAge: 99, depth: 0, label: 0 });
  const notes = [0, 0, 0, 0];
  let pulse = 0;
  for (let n = 0; n < SCRIPT.length; n += 1) {
    const [key, down, up] = SCRIPT[n];
    const k = keys[key];
    // Superposed steps are the exact response of the linear spring to "down, then up".
    k.depth += springStep(t - down, KEY_OMEGA, KEY_ZETA) - springStep(t - up, KEY_OMEGA, KEY_ZETA);
    k.label += labelAt(t, down, up);
    if (t >= down) {
      k.downAge = t - down;
      k.upAge = t >= up ? t - up : -1;
      pulse = Math.max(pulse, 0.3 * Math.exp(-(t - down) * 6));
    }
    const light = seg(t, down, down + 0.08) * (1 - seg(t, up + 0.5, up + 1.1));
    const note = n < 3 ? n : 3;
    notes[note] = Math.max(notes[note], light);
  }
  const streak = STREAK_FROM + easeInOut(seg(t, STREAK_UP[0], STREAK_UP[1])) - easeInOut(seg(t, STREAK_BACK[0], STREAK_BACK[1]));
  const p = seg(t, STREAK_UP[0], STREAK_UP[0] + 0.6);
  pulse = Math.max(pulse, Math.sin(Math.PI * p) * (1 - p * 0.4));
  return { keys, notes, streak, pulse };
}

/** The resting pose (also what reduced motion shows). */
export function restPose(): Pose {
  'worklet';
  return loopPose(0);
}
