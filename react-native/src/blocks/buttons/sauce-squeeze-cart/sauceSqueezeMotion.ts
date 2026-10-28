/*
 Sauce Squeeze Cart — the timeline, the springs and the touch driver (a port of
 SauceSqueezeCartMotion.swift). Every function is a worklet; helpers are declared above their callers.

 Every frame is a pure function of time. Before the release, the squeeze `s` (0…1) comes from the
 script (autoPlay) or from how long the finger has held (touch). After the release, everything is a
 function of τ, the time since the release, so the touch-driven shot replays the loop's own show.

 autoPlay: LOOP = 8 s
   0.0 – 1.2   rest: the bottle bobs gently on the pink page
   1.1 – 2.6   the "Hold to add" button is held: the bottle squashes (wider, shorter, the waist
               pinched), trembles, a bead of sauce swells at the nozzle
   2.6         release (τ = 0): the bottle snaps into a stretch and recoils up, a thick glossy stream
               whips out of the cap and down the screen
   2.9 – 3.8   a red pool rises from the bottom with a wavy surface until the screen is red
   3.95 – 5.7  stickers pop on the red ("IN YOUR CART", a smiley, a cart, "+1 ADDED", an arrow, a sparkle)
   5.5 – 6.4   the red retracts up the screen, drips hanging from it, and reveals the page
   6.4 – 7.3   the cart in the header bumps and a "+1" floats up
   7.3 – 8.0   rest (the frame at 8 is the frame at 0)
*/

export const LOOP = 8;
export const RELEASE_AT = 2.6;
const SQUEEZE_FROM = 1.2;
const SQUEEZE_TO = 2.45;
/** When a shot has played out (rest again), in τ. */
export const SHOT_END = 4.8;
/** The screen is fully red from here, in τ. */
export const COVERED_AT = 1.2;
export const THRESHOLD = 0.4;
const IDLE_BEFORE_BACK = 2;
/** 60 cycles per loop = 7.5 Hz, zero at the release. */
const TREMBLE_K = 60;
/** No shot: τ is this. */
export const NO_TAU = -1;

export type Frame = {
  /** Hold pressure, 0…1: the button's fill and the bead at the nozzle. */
  s: number;
  /** Squash (+) or stretch (−) of the bottle. */
  q: number;
  /** The button pressed in, 0…1. */
  press: number;
  /** Recoil of the bottle, pt (negative is up). */
  lift: number;
  /** Extra rotation of the bottle, degrees. */
  spin: number;
  /** Idle bob, −1…1 (and its sway). */
  bob: number;
  sway: number;
  /** Tremble of a hard squeeze, pt. */
  shake: number;
  /** Time since the release when a shot is running, else NO_TAU. */
  tau: number;
  /** The squeeze the shot was released with (scales the recoil). */
  shotS: number;
  reduced: boolean;
};

export function emptyFrame(): Frame {
  'worklet';
  return { s: 0, q: 0, press: 0, lift: 0, spin: 0, bob: 0, sway: 0, shake: 0, tau: NO_TAU, shotS: 1, reduced: false };
}

// Curves

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function mix(a: number, b: number, u: number) {
  'worklet';
  return a + (b - a) * u;
}
export function smooth(x: number) {
  'worklet';
  const c = clamp01(x);
  return c * c * (3 - 2 * c);
}
export function smoothRange(a: number, b: number, x: number) {
  'worklet';
  return smooth((x - a) / (b - a));
}
export function easeOut(u: number) {
  'worklet';
  const c = 1 - clamp01(u);
  return 1 - c * c * c;
}
export function easeInOut(u: number) {
  'worklet';
  const c = clamp01(u);
  return c < 0.5 ? 4 * c * c * c : 1 - Math.pow(-2 * c + 2, 3) / 2;
}
function easeInOutQuad(u: number) {
  'worklet';
  const c = clamp01(u);
  return c < 0.5 ? 2 * c * c : 1 - Math.pow(-2 * c + 2, 2) / 2;
}
function easeInBack(u: number) {
  'worklet';
  const c = clamp01(u);
  const k = 1.70158;
  return (k + 1) * c * c * c - k * c * c;
}
export function hash(i: number) {
  'worklet';
  const v = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return v - Math.floor(v);
}

/** An analytic damped spring (damping < 1) at time τ after it starts from x0 with velocity v0. */
export function spring(tau: number, x0: number, v0: number, target: number, response: number, damping: number) {
  'worklet';
  if (tau <= 0) return x0;
  const z = Math.min(damping, 0.999);
  const w0 = (2 * Math.PI) / response;
  const wd = w0 * Math.sqrt(1 - z * z);
  const a = x0 - target;
  const b = (v0 + z * w0 * a) / wd;
  return target + Math.exp(-z * w0 * tau) * (a * Math.cos(wd * tau) + b * Math.sin(wd * tau));
}

// The bottle

/** The bottle after a release with squeeze s0, τ seconds ago. */
export function shot(f: Frame, tau: number, s0: number) {
  'worklet';
  f.tau = tau;
  f.shotS = s0;
  f.s = s0 * (1 - easeOut(seg(tau, 0, 0.2)));
  f.q = Math.max(-1, spring(tau, s0, -14 * s0, 0, 0.55, 0.38));
  f.lift = spring(tau, 0, -560 * s0, 0, 0.6, 0.5);
  f.spin = spring(tau, 0, 60 * s0, 0, 0.6, 0.45);
  f.press = 1 - easeOut(seg(tau, 0, 0.15));
}

export function autoFrame(t: number): Frame {
  'worklet';
  const f = emptyFrame();
  const phase = (2 * Math.PI * 2 * t) / LOOP;
  f.bob = Math.sin(phase);
  f.sway = Math.sin(phase + 1.1);
  if (t < RELEASE_AT) {
    const s = easeInOut(seg(t, SQUEEZE_FROM, SQUEEZE_TO));
    const tr = Math.sin((2 * Math.PI * TREMBLE_K * t) / LOOP);
    f.s = s;
    f.q = s + 0.03 * s * s * tr;
    f.shake = 1.6 * s * s * tr;
    f.press = easeOut(seg(t, 1.1, 1.25));
  } else {
    shot(f, t - RELEASE_AT, 1);
  }
  return f;
}

// The show after the release (functions of τ)

/** How far the stream's head has travelled, 0…1. */
export function streamHead(tau: number) {
  'worklet';
  return easeOut(seg(tau, 0, 0.35));
}
export function streamWidth(tau: number) {
  'worklet';
  return 44 * easeOut(seg(tau, 0, 0.12)) + 106 * easeInOut(seg(tau, 0.35, 1.0));
}
export function streamSway(tau: number) {
  'worklet';
  return 26 * easeOut(seg(tau, 0, 0.35)) + 54 * easeInOut(seg(tau, 0.35, 1.0));
}
export function streamVisible(tau: number) {
  'worklet';
  return tau >= 0 && tau < 1.25;
}

/** How far the drain has gone, 0…1 (drives the drips). */
export function drain(tau: number) {
  'worklet';
  return seg(tau, 2.9, 3.8);
}

/** The red's top edge before its waves: it floods up from below the screen. */
function floodLevel(tau: number, h: number) {
  'worklet';
  return mix(h + 30, -80, easeInOut(seg(tau, 0.3, COVERED_AT)));
}

/** The red's bottom edge before its waves and drips: it retracts up towards the cart. */
function drainLevel(tau: number, h: number) {
  'worklet';
  return mix(h + 60, -140, easeInOutQuad(drain(tau)));
}

const DRIP_X = [0.14, 0.36, 0.58, 0.8, 0.93];
const DRIP_W = [16, 22, 13, 19, 14];
const DRIP_L = [110, 170, 80, 140, 95];

function waves(x: number, tau: number) {
  'worklet';
  return 10 * Math.sin(2 * Math.PI * (x / 220 + 1.3 * tau)) + 6 * Math.sin(2 * Math.PI * (x / 97 - 2.1 * tau) + 1.3);
}

/** The red's top edge at x (the flood's wavy surface). */
export function redTop(x: number, tau: number, h: number) {
  'worklet';
  return floodLevel(tau, h) + waves(x, tau);
}

/** The red's bottom edge at x: a wavy line with drips hanging from it while it rises. */
export function redBottom(x: number, tau: number, w: number, h: number) {
  'worklet';
  const u = drain(tau);
  const y = drainLevel(tau, h) + waves(x, tau + 0.4);
  if (u <= 0 || u >= 1) return y;
  const grow = Math.pow(Math.sin(Math.PI * u), 0.7);
  let hang = 0;
  for (let i = 0; i < DRIP_X.length; i++) {
    const dx = Math.abs(x - DRIP_X[i] * w);
    const hw = DRIP_W[i];
    const len = DRIP_L[i] * grow;
    let d = 0;
    if (dx < hw) {
      const cap = Math.sqrt(hw * hw - dx * dx);
      d = len > hw ? len - hw + cap : (len * cap) / hw;
    }
    const fillet = Math.min(len, 40) * 0.5 * Math.exp(-Math.pow(dx / (hw * 1.9), 2));
    hang = Math.max(hang, Math.max(d, fillet));
  }
  return y + hang;
}

/** Reduced motion: the red fades in and out instead of flooding. */
export function redFade(tau: number) {
  'worklet';
  return smooth(seg(tau, 0, 0.3)) * (1 - smooth(seg(tau, 2.7, 3.1)));
}

/** A sticker's scale: pops in on a playful spring, pops out with a little anticipation. */
export function stickerScale(i: number, tau: number) {
  'worklet';
  const start = 1.35 + 0.07 * i;
  if (tau < start) return 0;
  const pop = spring(tau - start, 0, 0, 1, 0.5, 0.55);
  const out = seg(tau, 2.7 + 0.04 * (5 - i), 2.92 + 0.04 * (5 - i));
  return Math.max(0, pop * (1 - easeInBack(out)));
}

/** The cart button's bump, around 0 (scale = 1 + 0.6 × bump). */
export function cartBump(tau: number) {
  'worklet';
  return spring(tau - 3.8, 0, 7, 0, 0.42, 0.4);
}

/** The "+1" over the cart: scale, rise (pt) and opacity. */
export function plusOne(tau: number) {
  'worklet';
  const c = tau - 3.85;
  if (c <= 0) return { scale: 0, rise: 0, alpha: 0 };
  const scale = spring(c, 0, 0, 1, 0.45, 0.55);
  return { scale: Math.max(0, scale), rise: 10 * easeOut(seg(c, 0, 0.85)), alpha: 1 - smooth(seg(c, 0.5, 0.85)) };
}

// The touch session and the loop clock

export type Driver = {
  autoStart: number;
  manual: boolean;
  holdStart: number;
  holdS0: number;
  softAt: number;
  softS: number;
  shotAt: number;
  shotS: number;
  lastTouch: number;
  bob0: number;
  sway0: number;
  manualAt: number;
  /** The finger is down but arrived while a shot was running; it only delays the restart. */
  ignoring: boolean;
};

/** "Not set" for the driver's times. */
const NONE = -1e9;

export function makeDriver(): Driver {
  'worklet';
  return {
    autoStart: NONE,
    manual: false,
    holdStart: NONE,
    holdS0: 0,
    softAt: NONE,
    softS: 0,
    shotAt: NONE,
    shotS: 1,
    lastTouch: NONE,
    bob0: 0,
    sway0: 0,
    manualAt: 0,
    ignoring: false,
  };
}

export function driverHolding(d: Driver) {
  'worklet';
  return d.holdStart !== NONE;
}

export function driverTouching(d: Driver) {
  'worklet';
  return d.holdStart !== NONE || d.ignoring;
}

export function driverShotRunning(d: Driver, now: number) {
  'worklet';
  return d.shotAt !== NONE && now - d.shotAt < SHOT_END;
}

function holdLevel(d: Driver, held: number) {
  'worklet';
  return d.holdS0 + (1 - d.holdS0) * (1 - Math.exp(-held / 0.6));
}

function manualFrame(d: Driver, now: number): Frame {
  'worklet';
  const f = emptyFrame();
  const decay = Math.exp(-(now - d.manualAt) / 0.35);
  f.bob = d.bob0 * decay;
  f.sway = d.sway0 * decay;
  if (d.shotAt !== NONE) {
    shot(f, now - d.shotAt, d.shotS);
  } else if (d.holdStart !== NONE) {
    const s = holdLevel(d, now - d.holdStart);
    const tr = Math.sin(2 * Math.PI * 7.5 * now);
    f.s = s;
    f.q = s + 0.03 * s * s * tr;
    f.shake = 1.6 * s * s * tr;
    f.press = easeOut(seg(now - d.holdStart, 0, 0.15));
  } else if (d.softAt !== NONE) {
    const u = now - d.softAt;
    f.s = d.softS * (1 - easeOut(seg(u, 0, 0.2)));
    f.q = spring(u, d.softS, 0, 0, 0.45, 0.5);
    f.press = 1 - easeOut(seg(u, 0, 0.15));
  }
  return f;
}

export function driverFrame(d: Driver, now: number, auto: boolean, reduced: boolean): Frame {
  'worklet';
  if (auto && d.manual && !driverTouching(d)) {
    let resume = d.lastTouch + IDLE_BEFORE_BACK;
    if (d.shotAt !== NONE) resume = Math.max(resume, d.shotAt + SHOT_END);
    if (now >= resume) {
      d.manual = false;
      d.autoStart = now;
      d.shotAt = NONE;
      d.softAt = NONE;
    }
  }
  let f: Frame;
  if (auto && !d.manual) {
    if (d.autoStart === NONE) d.autoStart = now;
    f = autoFrame((now - d.autoStart) % LOOP);
  } else {
    f = manualFrame(d, now);
  }
  f.reduced = reduced;
  if (reduced) {
    f.q = 0;
    f.lift = 0;
    f.spin = 0;
    f.bob = 0;
    f.sway = 0;
    f.shake = 0;
  }
  return f;
}

/** The finger goes down. Returns false when a shot is running and the touch is ignored. */
export function driverBegin(d: Driver, now: number, auto: boolean, reduced: boolean): boolean {
  'worklet';
  const current = driverFrame(d, now, auto, reduced);
  d.lastTouch = now;
  if (!d.manual) {
    d.manual = true;
    d.manualAt = now;
    d.bob0 = current.bob;
    d.sway0 = current.sway;
    if (current.tau !== NO_TAU && current.tau < SHOT_END) {
      d.shotAt = now - current.tau;
      d.shotS = current.shotS;
      d.ignoring = true;
      return false;
    }
  }
  if (d.shotAt !== NONE && now - d.shotAt < SHOT_END) {
    d.ignoring = true;
    return false;
  }
  d.shotAt = NONE;
  d.softAt = NONE;
  d.holdS0 = clamp01(current.tau === NO_TAU ? current.s : 0);
  d.holdStart = now;
  return true;
}

/** Shoots at once (the accessibility action), unless a shot is already running. */
export function driverShoot(d: Driver, now: number, auto: boolean, reduced: boolean) {
  'worklet';
  if (!driverBegin(d, now, auto, reduced)) {
    d.ignoring = false;
    return;
  }
  d.holdStart = NONE;
  d.lastTouch = now;
  d.shotAt = now;
  d.shotS = 1;
}

/** The finger lifts. Returns true when the squeeze was strong enough to shoot. */
export function driverEnd(d: Driver, now: number): boolean {
  'worklet';
  d.lastTouch = now;
  if (d.ignoring) {
    d.ignoring = false;
    return false;
  }
  if (d.holdStart === NONE) return false;
  const s = holdLevel(d, now - d.holdStart);
  d.holdStart = NONE;
  if (s >= THRESHOLD) {
    d.shotAt = now;
    d.shotS = s;
    return true;
  }
  d.softAt = now;
  d.softS = s;
  return false;
}
