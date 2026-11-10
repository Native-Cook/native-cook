// Perfume Ink Bloom — the loop. Helpers first: a worklet captures only what is declared above it.
//
// press 0 … 1 pushes the atomiser down; a spray starts with it (age: s since), the mist condenses
// into the perfume's notes (condense 0 … 1) and evaporates again (evaporate 0 … 1). tilt (rad) leans
// the bottle under a finger, and the liquid inside sloshes after it (slosh, rad, a damped spring).

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export const LOOP = 10;
/** the liquid's spring: its natural frequency (rad/s) and damping ratio */
export const SLOSH_W = 9;
export const SLOSH_Z = 0.16;

/* ---------------------------------------------------------------- the loop (10 s)
 0.75 a finger presses the atomiser: a spray     1.9 the mist condenses into the notes, read by 3.2
 5.6  the notes evaporate                        6.9 a finger leans the bottle left, then right
 8.4  lets go: it rocks back, the liquid settles by 10   10 = 0 */
export const T = {
  press: [0.75, 0.95, 1.2],
  condense: [1.9, 3.2],
  evaporate: [5.6, 6.8],
  lean: [7.1, 7.7, 8.4],
} as const;

/** the bottle's lean in the loop (rad): left, right, and a damped rock back from where it was let go */
function leanAt(t: number) {
  'worklet';
  if (t < T.lean[0]) return 0;
  if (t < T.lean[1]) return -0.22 * easeInOut(seg(t, T.lean[0], T.lean[1]));
  if (t < T.lean[2]) return -0.22 + 0.4 * easeInOut(seg(t, T.lean[1], T.lean[2]));
  const u = t - T.lean[2];
  return 0.18 * Math.exp(-3.2 * u) * Math.cos(7 * u);
}

// the slosh, integrated once over the loop at 240 Hz (it is a spring driven by the lean's acceleration)
const RATE = 240;
const SLOSH: number[] = (() => {
  const n = LOOP * RATE;
  const dt = 1 / RATE;
  const out: number[] = [];
  let x = 0;
  let v = 0;
  for (let pass = 0; pass < 2; pass++) {
    for (let i = 0; i < n; i++) {
      const t = i * dt;
      const a = (leanAt(t + dt) - 2 * leanAt(t) + leanAt(Math.max(0, t - dt))) / (dt * dt);
      v += (-SLOSH_W * SLOSH_W * x - 2 * SLOSH_Z * SLOSH_W * v - a * 0.06) * dt;
      x += v * dt;
      if (pass === 1) out.push(x);
    }
  }
  return out;
})();

export type Frame = {
  press: number;
  age: number; // s since the spray (−1 none)
  condense: number;
  evaporate: number;
  tilt: number;
  slosh: number;
  finger: number;
  fingerAt: number; // 0 the atomiser, 1 the bottle
  fingerU: number; // along the lean, −1 … 1
  press2: number;
};

export function frameAt(t: number): Frame {
  'worklet';
  const press = easeOut(seg(t, T.press[0], T.press[1])) * (1 - easeOut(seg(t, T.press[1], T.press[2])));
  const age = t >= T.press[0] && t < T.evaporate[1] + 0.4 ? t - T.press[0] : -1;
  const condense = easeInOut(seg(t, T.condense[0], T.condense[1]));
  const evaporate = seg(t, T.evaporate[0], T.evaporate[1]);
  const tilt = leanAt(t);
  const slosh = SLOSH[Math.min(SLOSH.length - 1, Math.max(0, Math.round(t * RATE)))];
  let finger = 0;
  let fingerAt = 0;
  let fingerU = 0;
  let press2 = 0;
  const f0 = easeOut(seg(t, T.press[0] - 0.2, T.press[0] - 0.04)) * (1 - easeOut(seg(t, T.press[2], T.press[2] + 0.16)));
  if (f0 > finger) {
    finger = f0;
    press2 = press;
  }
  const f1 = easeOut(seg(t, T.lean[0] - 0.22, T.lean[0] - 0.04)) * (1 - easeOut(seg(t, T.lean[2], T.lean[2] + 0.16)));
  if (f1 > finger) {
    finger = f1;
    fingerAt = 1;
    fingerU = tilt / 0.22;
    press2 = f1;
  }
  return { press, age, condense, evaporate, tilt, slosh, finger, fingerAt, fingerU, press2 };
}
