// QR Joy Postcard — the timeline, the frame it gives at any moment, and the touch driver.
// Every value on screen is a pure function of the loop time t (plus the touch extras), so frame
// LOOP equals frame 0 and any moment can be pinned for a still.

import { BEAR, type Module, QR } from './qjp-theme';

/** One period of autoPlay, seconds. */
export const LOOP = 9.0;
/** Seconds after the last touch before autoPlay takes over again. */
export const RESUME = 2;
/** The postcard has finished decorating itself: where a tap plays to. */
export const PEAK = 5.35;

export const T = {
  press: [1.25, 1.42], // Decorate QR code sinks
  commit: 1.42,
  liftAt: 1.42, // the card rises and straightens (spring)
  swirlAt: 1.52, // first module leaves; the last leaves SWIRL_WINDOW later
  face: [1.85, 2.95], // the postcard face spreads out from the middle
  finders: [2.0, 2.55], // the three corner squares round off
  cta: [2.15, 2.6], // the button's label turns into "Send postcard"
  check: [2.55, 2.95], // step 1 is ticked
  heartAt: 3.0, // the heart lands in the middle
  burst: [3.0, 3.75], // sparkles fly out of it
  sticker: [3.15, 3.42], // the tulip sticker slaps on
  hop: [3.5, 3.95], // the bear hops for joy
  bubbleAt: 3.62, // "for you" bubble pops out of the bear
  write: [3.75, 4.6], // and writes itself
  wave: [3.75, 5.15], // the bear waves twice
  sheen: [4.7, 5.35], // a light runs over the card
  back: 6.5, // the way home starts
  backSwirlAt: 6.65,
  lower: [7.55, 8.15], // the card settles back down
} as const;

const SWIRL_WINDOW = 0.62; // spread of departures, by distance from the middle
const SWIRL_JITTER = 0.14;
const FLIGHT = 0.82;
const BACK_WINDOW = 0.42;
const BACK_FLIGHT = 0.62;
/** Outside these windows every module is at rest (squares, or dots), so the code is one path. */
export const SWIRL_DONE = T.swirlAt + SWIRL_WINDOW + SWIRL_JITTER + FLIGHT;
export const BACK_DONE = T.backSwirlAt + BACK_WINDOW + 0.1 + BACK_FLIGHT;
/** pt the card rises, and its turn at rest (pin) and lifted. */
export const LIFT = 34;
export const TURN_LIFTED = -4 * (Math.PI / 180);

// ------------------------------------------------------------------ easing (all worklets)

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function mix(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}
/** Strong ease-out, close to cubic-bezier(0.23, 1, 0.32, 1). */
export function easeOut(x: number) {
  'worklet';
  const u = 1 - clamp01(x);
  return 1 - u * u * u * u * u;
}
/** On-screen movement, close to cubic-bezier(0.77, 0, 0.175, 1). */
export function easeInOut(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 16 * p * p * p * p * p : 1 - Math.pow(-2 * p + 2, 5) / 2;
}
/** Gentler in-out for long journeys (the module flights). */
export function easeInOutCubic(x: number) {
  'worklet';
  const p = clamp01(x);
  return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
}
export function smooth(x: number) {
  'worklet';
  const p = clamp01(x);
  return p * p * (3 - 2 * p);
}
/** An underdamped spring from 0 to 1, `tau` seconds after release (Apple response/damping). */
export function spring(tau: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return 0;
  const w = (2 * Math.PI) / response;
  const zw = damping * w;
  const wd = w * Math.sqrt(1 - damping * damping);
  return 1 - Math.exp(-zw * tau) * (Math.cos(wd * tau) + (zw / wd) * Math.sin(wd * tau));
}
/** A damped wobble that starts at 1 and dies out. */
export function wobble(tau: number, period: number, decay: number) {
  'worklet';
  if (tau <= 0) return 0;
  return Math.exp(-tau / decay) * Math.cos((2 * Math.PI * tau) / period);
}

// ------------------------------------------------------------------ the frame

export type Frame = {
  t: number;
  press: number; // CTA pressed 0 … 1
  lift: number; // card raised 0 … 1 (springs a little past 1)
  face: number; // postcard face radius share 0 … 1
  finders: number; // square → rounded 0 … 1
  cta: number; // label A → B 0 … 1
  check: number; // step 1 number → tick 0 … 1
  heart: number; // heart scale (spring, 0 hidden)
  burst: number; // sparkle burst progress (−1 none)
  sticker: number; // 0 above the card … 1 stuck; >1 not used
  stickerSquash: number; // −1 … 1 wobble after the slap
  stickerPeel: number; // 0 stuck … 1 peeled away (the way home)
  bubble: number; // bubble scale (spring)
  write: number; // share of "for you" written
  hop: number; // bear hop height, pt
  squash: number; // bear squash (+ wider/shorter)
  wave: number; // atlas frame 0 … 8
  sheen: number; // light across the card (−1 none)
  landSquash: number; // the card's settle wobble when it lands up top
};

function liftAt(t: number) {
  'worklet';
  if (t < T.back) return spring(t - T.liftAt, 0.62, 0.72);
  return 1 - easeInOut(seg(t, T.lower[0], T.lower[1]));
}

/** How far a module is through its flight (0 square on the card … 1 dot on the postcard). */
export function moduleU(m: Module, t: number) {
  'worklet';
  if (t < T.back) {
    const s0 = T.swirlAt + SWIRL_WINDOW * m.d + SWIRL_JITTER * m.h;
    return easeInOutCubic(seg(t, s0, s0 + FLIGHT));
  }
  const s1 = T.backSwirlAt + BACK_WINDOW * (1 - m.d) + 0.1 * m.h;
  return 1 - easeInOutCubic(seg(t, s1, s1 + BACK_FLIGHT));
}
/** Raw (un-eased) flight progress, for the trail. */
export function moduleRaw(m: Module, t: number) {
  'worklet';
  if (t < T.back) {
    const s0 = T.swirlAt + SWIRL_WINDOW * m.d + SWIRL_JITTER * m.h;
    return seg(t, s0, s0 + FLIGHT);
  }
  const s1 = T.backSwirlAt + BACK_WINDOW * (1 - m.d) + 0.1 * m.h;
  return 1 - seg(t, s1, s1 + BACK_FLIGHT);
}

/** Where a module flies: once round the middle of the code, swinging out and rising toward you. */
export function modulePose(m: Module, u: number) {
  'worklet';
  const mid = QR.x + QR.size / 2;
  const midY = QR.y + QR.size / 2;
  const x0 = QR.x + (m.c + 0.5) * QR.m - mid;
  const y0 = QR.y + (m.r + 0.5) * QR.m - midY;
  const r0 = Math.hypot(x0, y0);
  const a0 = Math.atan2(y0, x0);
  const z = Math.sin(Math.PI * u);
  const a = a0 + 2 * Math.PI * u;
  const r = r0 + (10 + 26 * m.h) * z;
  // in flight a petal lies along its path (the tangent), turning in from the grid as it leaves
  const turn = Math.min(1, u / 0.3);
  return { x: mid + r * Math.cos(a), y: midY + r * Math.sin(a) - 12 * z, z, spin: (a + Math.PI / 2) * turn * turn };
}

function waveFrame(t: number) {
  'worklet';
  const p = seg(t, T.wave[0], T.wave[1]);
  if (p <= 0 || p >= 1) return BEAR.rest;
  const env = Math.min(1, p * 5, (1 - p) * 5);
  return BEAR.rest + 4 * Math.sin(2 * Math.PI * 2 * p) * env;
}

export function frameAt(t: number): Frame {
  'worklet';
  const fwd = t < T.back;
  const pressIn = seg(t, T.press[0], T.press[0] + 0.12);
  const pressOut = seg(t, T.commit, T.commit + 0.16);
  const press = t >= T.press[0] && t < T.commit + 0.16 ? easeOut(pressIn) * (1 - easeOut(pressOut)) : 0;

  const face = fwd ? easeInOut(seg(t, T.face[0], T.face[1])) : 1 - easeInOut(seg(t, 6.8, 7.6));
  const finders = fwd ? easeInOut(seg(t, T.finders[0], T.finders[1])) : 1 - easeInOut(seg(t, 6.75, 7.2));
  const cta = fwd ? easeInOut(seg(t, T.cta[0], T.cta[1])) : 1 - easeInOut(seg(t, 6.6, 6.95));
  const check = fwd ? easeOut(seg(t, T.check[0], T.check[1])) : 1 - easeOut(seg(t, 6.6, 6.85));
  const heart = fwd ? spring(t - T.heartAt, 0.5, 0.55) : 1 - easeInOut(seg(t, 6.55, 6.8));
  const burstP = seg(t, T.burst[0], T.burst[1]);
  const burst = t >= T.burst[0] && t < T.burst[1] ? burstP : -1;

  const sticker = fwd ? seg(t, T.sticker[0], T.sticker[1]) : 1;
  const stickerSquash = fwd ? wobble(t - T.sticker[1], 0.24, 0.16) : 0;
  const stickerPeel = fwd ? 0 : easeInOut(seg(t, 6.5, 6.9));

  const bubble = fwd ? spring(t - T.bubbleAt, 0.42, 0.6) : 1 - easeInOut(seg(t, 6.5, 6.68));
  const write = fwd ? seg(t, T.write[0], T.write[1]) : 1 - seg(t, 6.5, 6.6);

  const hp = seg(t, T.hop[0], T.hop[1]);
  const hop = hp > 0 && hp < 1 ? 13 * Math.sin(Math.PI * hp) : 0;
  // squash: crouch before the hop, stretch in the air, splat on landing; also as the card lands up top
  const crouch = t > T.hop[0] - 0.12 && t < T.hop[0] ? Math.sin(Math.PI * seg(t, T.hop[0] - 0.12, T.hop[0])) : 0;
  const land = wobble(t - T.hop[1], 0.22, 0.14);
  const cardLand = fwd ? wobble(t - (T.liftAt + 0.48), 0.26, 0.16) * 0.6 : wobble(t - T.lower[1], 0.26, 0.14) * 0.6;
  const squash = 0.07 * crouch - 0.05 * Math.sin(Math.PI * hp) * (hp > 0 && hp < 1 ? 1 : 0) + 0.08 * land + 0.05 * cardLand;

  const sp = seg(t, T.sheen[0], T.sheen[1]);
  return {
    t,
    press,
    lift: liftAt(t),
    face,
    finders,
    cta,
    check,
    heart,
    burst,
    sticker,
    stickerSquash,
    stickerPeel,
    bubble,
    write,
    hop,
    squash,
    wave: waveFrame(t),
    sheen: sp > 0 && sp < 1 ? sp : -1,
    landSquash: cardLand,
  };
}

/** Rest A covers the start and the end of the loop. */
export function inRestA(t: number) {
  'worklet';
  return t < T.press[0] || t >= T.lower[1];
}
/** The postcard is up: from the heart landing until the way home. */
export function inPostcard(t: number) {
  'worklet';
  return t >= T.heartAt && t < T.back;
}

// ------------------------------------------------------------------ the driver (touch on top of the loop)

export type Driver = {
  t: number;
  mode: 0 | 1 | 2; // 0 autoPlay, 1 playing to `stop`, 2 parked
  stop: number;
  rate: number; // playback speed while playing to `stop`
  last: number; // clock time of the last touch
  amb: number; // ambient clock (sparkles, gift, breath), wraps at LOOP so autoPlay loops exactly
  ctaAim: number; // 1 while a finger is on the button
  ctaP: number;
  tiltX: number; // −1 … 1
  tiltY: number;
  aimX: number;
  aimY: number;
  lean: number; // the bear lags the tilt (spring)
  leanV: number;
  bearAt: number; // clock time of a tap on the bear (−1 none)
  jiggleAt: number; // clock time of "Send postcard" (−1 none)
};

export function makeDriver(t = 0): Driver {
  'worklet';
  return {
    t,
    mode: 0,
    stop: 0,
    rate: 1,
    last: -10,
    amb: t,
    ctaAim: 0,
    ctaP: 0,
    tiltX: 0,
    tiltY: 0,
    aimX: 0,
    aimY: 0,
    lean: 0,
    leanV: 0,
    bearAt: -1,
    jiggleAt: -1,
  };
}

function follow(cur: number, aim: number, k: number, dt: number) {
  'worklet';
  return cur + (aim - cur) * (1 - Math.exp(-dt * k));
}

/** Advance the driver by dt seconds; returns the loop time to draw. */
export function stepDriver(d: Driver, dt: number, now: number, auto: boolean) {
  'worklet';
  if (d.mode === 1) {
    d.t += dt * d.rate;
    if (d.t >= d.stop) {
      d.t = d.stop >= LOOP ? 0 : d.stop;
      d.mode = 2;
      d.rate = 1;
      d.last = now; // let the result be seen for RESUME seconds before the loop moves on
    }
  } else if (d.mode === 2) {
    if (auto && now - d.last >= RESUME) d.mode = 0;
  } else if (auto) {
    d.t = (d.t + dt) % LOOP;
  }
  d.amb = (d.amb + dt) % LOOP;
  d.tiltX = follow(d.tiltX, d.aimX, 11, dt);
  d.tiltY = follow(d.tiltY, d.aimY, 11, dt);
  // the bear sits on the card and lags behind its turn: a loose spring toward the tilt
  const k = 120;
  const c = 2 * 0.35 * Math.sqrt(k);
  d.leanV += (-k * (d.lean - d.tiltX) - c * d.leanV) * dt;
  d.lean += d.leanV * dt;
  d.ctaP = follow(d.ctaP, d.ctaAim, d.ctaAim > 0 ? 40 : 18, dt);
  return d.t;
}

/** A finger came down: autoPlay stops; a transition already under way finishes where it leads. */
export function touched(d: Driver, now: number) {
  'worklet';
  d.last = now;
  if (d.mode !== 0) return;
  d.rate = 1;
  if (d.t >= T.press[0] && d.t < PEAK) {
    d.mode = 1;
    d.stop = PEAK;
  } else if (d.t >= T.back && d.t < T.lower[1]) {
    d.mode = 1;
    d.stop = LOOP;
  } else d.mode = 2;
}

/** A finger on the card or the button in A: the button sinks and waits for the release. */
export function pressA(d: Driver) {
  'worklet';
  if (!inRestA(d.t)) return false;
  d.t = T.press[0];
  d.mode = 1;
  d.rate = 1;
  d.stop = T.commit - 0.001;
  return true;
}
/** Released on target: decorate. Released elsewhere: back to rest. */
export function releaseA(d: Driver, onTarget: boolean) {
  'worklet';
  if (onTarget) {
    d.mode = 1;
    d.rate = 1;
    d.stop = PEAK;
  } else {
    d.t = 0;
    d.mode = 2;
  }
}
/** × on the postcard: play the way home, 1.5× faster than the demo. */
export function closeB(d: Driver) {
  'worklet';
  if (!inPostcard(d.t) && !(d.t >= PEAK && d.t < T.back)) return false;
  d.t = T.back;
  d.mode = 1;
  d.rate = 1.5;
  d.stop = LOOP;
  return true;
}

/** The bear after a tap: a hop and one wave, from the clock. */
export function bearExtra(d: Driver, now: number) {
  'worklet';
  if (d.bearAt < 0) return { hop: 0, squash: 0, wave: 0, on: 0 };
  const p = (now - d.bearAt) / 1.25;
  if (p >= 1) {
    d.bearAt = -1;
    return { hop: 0, squash: 0, wave: 0, on: 0 };
  }
  const hp = clamp01(p / 0.36);
  const hop = hp < 1 ? 11 * Math.sin(Math.PI * hp) : 0;
  const squash = hp < 1 ? -0.045 * Math.sin(Math.PI * hp) : 0.07 * wobble((p - 0.36) * 1.25, 0.22, 0.14);
  const env = Math.min(1, p * 6, (1 - p) * 4);
  return { hop, squash, wave: 4 * Math.sin(2 * Math.PI * 1.5 * p) * env, on: 1 };
}
/** "Send postcard": the card does a happy shake and the sparkles twinkle. */
export function jiggle(d: Driver, now: number) {
  'worklet';
  if (d.jiggleAt < 0) return 0;
  const p = now - d.jiggleAt;
  if (p > 0.9) {
    d.jiggleAt = -1;
    return 0;
  }
  return wobble(p, 0.16, 0.2);
}
