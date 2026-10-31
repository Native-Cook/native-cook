// Tap Earn Coin — every motion value of the block.
// Helpers come first: a worklet captures what is defined above it when it is created.

/* ---------------------------------------------------------------- curves */

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function easeOut(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}
export function easeIn(x: number) {
  'worklet';
  return x * x * x;
}
export function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}
/** Closed-form spring from 0 to 1 (zeta < 1), t in seconds: lets the loop be a pure function of time. */
export function springTo(t: number, response: number, zeta: number) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}
/** Damped kick: 0 at t = 0, rings and dies away. */
export function ring(t: number, freq: number, decay: number) {
  'worklet';
  if (t <= 0) return 0;
  return Math.sin(2 * Math.PI * freq * t) * Math.exp(-decay * t);
}
export function hash(n: number) {
  'worklet';
  const s = Math.sin(n * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/* ---------------------------------------------------------------- the loop */

/** autoPlay period, seconds; frame LOOP equals frame 0. */
export const LOOP = 10.0;
export const TAPS = 3;
export const BASE = 438;
/** Press-in times of the three scripted taps, their payouts, and when each releases. */
export const TAP_AT = [0.9, 1.65, 2.4] as const;
export const TAP_PAY = [7, 6, 5] as const;
/** Where the scripted finger lands on the cap (lean of the cap, radians about z and x). */
export const TAP_LEAN = [[0.05, -0.04], [-0.06, 0.03], [0.02, 0.05]] as const;
/** Spray power of each scripted tap: the third comes quickly after the second, a small combo. */
export const TAP_POWER = [0, 0, 1] as const;
export const PRESS_IN = 0.09;
export const RELEASE = 0.11;
/** Coins and the +¢ pill live this long after a release. */
export const BURST_LIFE = 1.7;
export const COINS = 9;
export const COINS_PER_POWER = 3;
/** Burst records are flat [age or start, seed, pay, power] quadruples. */
export const STRIDE = 4;
/** When a homing coin drops into its box, seconds after release. */
export const LAND = 0.62;

export const T = {
  squash: 3.0, // anticipation before the button turns into the bell
  lift0: 3.16, // the button rises to the middle of the page, full size
  lift1: 3.62,
  morph0: 3.3, // and melts into the bell there, big enough to watch
  morph1: 4.42,
  ringAt: 4.42, // the bell rings in the open
  travel0: 4.78, // then shrinks into the rising sheet
  travel1: 5.52,
  dim0: 4.62,
  dim1: 5.1,
  sheet0: 4.78,
  rewind0: 5.7,
  rewind1: 6.4,
  laterIn: 7.0,
  laterOut: 7.14,
  close0: 7.2,
  back0: 7.26, // the bell rises out of the sheet and grows back to full size
  back1: 7.9,
  unmorph0: 7.72, // melts back into the button in the open
  unmorph1: 8.62,
  drop0: 8.5, // and drops into its seat
  drop1: 8.95,
  land: 8.95,
  refill: 9.3,
} as const;

export type Scene = {
  press: number; // cap travel 0…1
  hop: number; // base squash (+) / stretch (−)
  morph: number; // 0 button … 1 bell
  lift: number; // 0 in its seat … 1 in the middle of the page, full size
  travel: number; // 0 on the page … 1 in the sheet
  dim: number;
  sheet: number; // 0 hidden … 1 up
  swing: number; // bell angle, radians
  amount: number; // continuous ¢ shown by the odometer
  later: number; // "Maybe later" press
  label: number; // 0…3 taps left, 4 refill countdown
  leanX: number;
  leanZ: number;
};

function payBefore(t: number) {
  'worklet';
  // Each payout rolls in over 0.5 s, starting 0.12 s after its release (when the coins land).
  let a = 0;
  for (let i = 0; i < 3; i++) {
    const r = TAP_AT[i] + RELEASE + 0.12;
    a += TAP_PAY[i] * easeOut(seg(t, r, r + 0.5));
  }
  return a;
}

function leanAt(t: number, axis: number) {
  'worklet';
  let l = 0;
  for (let i = 0; i < 3; i++) {
    const a = TAP_AT[i];
    const v = TAP_LEAN[i][axis];
    if (t >= a && t < a + RELEASE) l = v * easeOut(seg(t, a, a + PRESS_IN));
    else if (t >= a + RELEASE && t < a + RELEASE + 0.7) l = v * (1 - springTo(t - a - RELEASE, 0.3, 0.45));
  }
  return l;
}

function pressAt(t: number) {
  'worklet';
  let p = 0;
  for (let i = 0; i < 3; i++) {
    const a = TAP_AT[i];
    if (t >= a && t < a + PRESS_IN) p = easeOut(seg(t, a, a + PRESS_IN));
    else if (t >= a + PRESS_IN && t < a + RELEASE) p = 1;
    else if (t >= a + RELEASE && t < a + RELEASE + 0.7) p = 1 - springTo(t - a - RELEASE, 0.32, 0.42);
  }
  return p;
}

export function sceneAt(t: number, base: number): Scene {
  'worklet';
  // anticipation: the button squats, then lifts out of its seat to the middle of the page
  const squat = seg(t, T.squash, T.lift0);
  const hopIn = Math.sin(Math.PI * squat) * 0.55;
  const landT = t - T.land;
  const hop = hopIn + (landT > 0 ? ring(landT, 2.6, 7) * 0.9 : 0);

  const lift = easeOut(seg(t, T.lift0, T.lift1)) * (1 - easeInOut(seg(t, T.drop0, T.drop1)));
  const morph = easeInOut(seg(t, T.morph0, T.morph1)) * (1 - easeInOut(seg(t, T.unmorph0, T.unmorph1)));
  const travel = easeInOut(seg(t, T.travel0, T.travel1)) * (1 - easeInOut(seg(t, T.back0, T.back1)));
  const dim = easeOut(seg(t, T.dim0, T.dim1)) * (1 - easeInOut(seg(t, T.close0, T.close0 + 0.5)));
  const up = springTo(t - T.sheet0, 0.62, 0.82);
  const down = easeInOut(seg(t, T.close0, T.close0 + 0.42));
  const sheet = t < T.close0 ? up : (1 - down) * Math.min(1, up);
  const swing = ring(t - T.ringAt, 2.2, 3.6) * 0.32;

  const roll = payBefore(t);
  const rewind = easeInOut(seg(t, T.rewind0, T.rewind1));
  const amount = base + roll * (1 - rewind);

  const later = easeOut(seg(t, T.laterIn, T.laterIn + 0.08)) * (1 - easeOut(seg(t, T.laterOut, T.laterOut + 0.2)));

  let label = TAPS;
  for (let i = 0; i < 3; i++) if (t >= TAP_AT[i] + RELEASE) label = TAPS - 1 - i;
  if (t >= T.close0 + 0.6) label = 4;
  if (t >= T.refill) label = TAPS;

  return { press: pressAt(t), hop, morph, lift, travel, dim, sheet, swing, amount, later, label, leanX: leanAt(t, 0), leanZ: leanAt(t, 1) };
}

/** Gleam sweep after the latest release: −1 (off) … 1 across the cap. */
export function gleamAt(t: number) {
  'worklet';
  let g = -1;
  for (let i = 0; i < 3; i++) {
    const r = TAP_AT[i] + RELEASE;
    if (t >= r && t < r + 0.75) g = -1 + 2 * easeOut(seg(t, r, r + 0.75));
  }
  return g;
}

/** The scripted bursts alive at loop time t: [age, seed, pay, power] records. */
export function loopBursts(t: number) {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < 3; i++) {
    const age = t - TAP_AT[i] - RELEASE;
    if (age >= 0 && age < BURST_LIFE) out.push(age, 11 + i * 7, TAP_PAY[i], TAP_POWER[i]);
  }
  return out;
}

/** Warm bloom behind the button: rises fast after each payout, dies in about half a second. */
export function glowOf(bursts: number[]) {
  'worklet';
  let g = 0;
  for (let b = 0; b + 3 < bursts.length; b += STRIDE) {
    const age = bursts[b];
    if (age < 0) continue;
    g = Math.max(g, (1 - Math.exp(-age / 0.04)) * Math.exp(-age / 0.42) * (0.8 + 0.2 * bursts[b + 3]));
  }
  return g;
}

/** Bump of digit box k (0 = left) when a homing coin drops into it. */
export function boxBump(bursts: number[], k: number) {
  'worklet';
  let v = 0;
  for (let b = 0; b + 3 < bursts.length; b += STRIDE) {
    const age = bursts[b] - LAND;
    if (age < 0 || age > 0.8) continue;
    const seed = bursts[b + 1];
    for (let i = 0; i < 2; i++) {
      if ((i + Math.floor(seed)) % 3 === k) v += Math.sin(Math.min(age / 0.09, 1) * Math.PI * 0.5) * Math.exp(-age / 0.16) * (1 + ring(age, 3.2, 9) * 0.4);
    }
  }
  return v;
}

/* ---------------------------------------------------------------- odometer */

/** Continuous position of digit column k (0 = ones) for a continuous amount: a carry rolls the next
 * column only while the column below goes from 9 to 10. */
export function columnPos(amount: number, k: number) {
  'worklet';
  const unit = Math.pow(10, k);
  const whole = Math.floor(amount / unit);
  if (k === 0) return amount;
  const below = amount - whole * unit; // 0 … unit
  const carry = clamp01(below - (unit - 1));
  return whole + carry;
}

/* ---------------------------------------------------------------- coins */

export type CoinPose = {
  x: number;
  y: number;
  r: number;
  tilt: number; // −1…1: how much of the face shows (cos of the roll)
  spin: number; // in-plane angle
  alpha: number;
  lit: number; // specular 0…1
  roll: number; // tumble angle, radians (picks the atlas frame)
};

/**
 * Pose of coin i of a burst `age` seconds after release. Plain ballistics from the cap's rim, so a
 * frame is a pure function of age: no state, nothing to reset. Coins 0 and 1 home into the
 * odometer instead and vanish in its boxes.
 */
export function coinPose(
  age: number, seed: number, i: number, power: number,
  ox: number, oy: number, R: number, tx: number, ty: number, floor: number,
): CoinPose {
  'worklet';
  const h1 = hash(seed * 17.0 + i * 3.1);
  const h2 = hash(seed * 5.3 + i * 11.7);
  const h3 = hash(seed * 9.1 + i * 1.9);
  const h4 = hash(seed * 2.7 + i * 23.3);
  const r = R * (0.085 + 0.035 * h3);
  const sx = ox + (h1 - 0.5) * R * 0.55;
  const sy = oy - R * 0.2;
  const emerge = easeOut(seg(age, 0, 0.08));
  const spinRate = 7 + 11 * h2;
  const roll = age * spinRate + h4 * 6.28;
  if (i < 2) {
    // homing coin: a quadratic arc over the button into the boxes
    const u = easeIn(seg(age, 0, LAND));
    const cx = mix(sx, tx, 0.5) + (i === 0 ? -1 : 1) * R * 0.5;
    const cy = Math.min(sy, ty) - R * (0.95 + 0.25 * h1);
    const a = 1 - u;
    const x = a * a * sx + 2 * a * u * cx + u * u * tx;
    const y = a * a * sy + 2 * a * u * cy + u * u * ty;
    const shrink = 1 - easeIn(seg(age, LAND - 0.12, LAND + 0.02));
    return { x, y, r: r * emerge * (0.75 + 0.25 * shrink), tilt: Math.cos(roll), spin: roll * 0.3, alpha: shrink, lit: 0.5 + 0.5 * Math.sin(roll * 1.3), roll };
  }
  const boost = 1 + 0.18 * power;
  const vx = (h1 - 0.5) * R * 4.2 * boost;
  const vy = -R * (3.6 + 2.6 * h2) * boost;
  const g = R * 13.5;
  // ballistic until the coin meets the floor (the share button's top edge), one soft bounce, rest
  const disc = vy * vy - 2 * g * (sy - floor);
  const tHit = disc > 0 ? (-vy + Math.sqrt(disc)) / g : 99;
  let x: number;
  let y: number;
  let tilt = Math.cos(roll);
  let landed = 0;
  if (age < tHit) {
    x = sx + vx * age;
    y = sy + vy * age + 0.5 * g * age * age;
  } else {
    const a = age - tHit;
    const up = -(vy + g * tHit) * 0.3;
    const tRest = (-2 * up) / g;
    const xHit = sx + vx * tHit;
    x = xHit + vx * 0.5 * Math.min(a, tRest + 0.15);
    y = a < tRest ? floor + up * a + 0.5 * g * a * a : floor;
    landed = seg(a, tRest, tRest + 0.12);
    tilt = mix(Math.cos(roll), 0.22, landed);
  }
  return {
    x, y: y - r * 0.25 * landed, r: r * emerge,
    tilt, spin: mix((h3 - 0.5) * 1.4 + age * (h1 - 0.5) * 3, 0, landed),
    alpha: 1 - seg(age, Math.min(BURST_LIFE - 0.25, tHit + 0.55), Math.min(BURST_LIFE, tHit + 0.8)),
    lit: 0.5 + 0.5 * Math.sin(roll * 1.3 + h3 * 4),
    roll: landed > 0.99 ? Math.PI * 0.5 : roll,
  };
}

/** "+N¢" pill: rises from above the cap, pops in, fades out. */
export function pillPose(age: number, seed: number) {
  'worklet';
  const pop = springTo(age, 0.34, 0.55);
  const rise = easeOut(seg(age, 0, 0.9));
  const alpha = clamp01(age / 0.06) * (1 - seg(age, 0.75, 1.0));
  return { dx: (hash(seed * 3.3) - 0.5) * 0.9, rise, scale: 0.7 + 0.3 * pop, alpha };
}

/* ---------------------------------------------------------------- interaction */

/** Time after the last touch before the loop eases back in (AGENTS: 2 s). */
export const RESUME_AFTER = 2;
export const SHEET_DISMISS = 120; // pt of drag
export const SHEET_FLICK = 800; // pt/s
export const REFILL_SECONDS = 3;
