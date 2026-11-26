/**
 * The clocks of the screen, stepped on the UI thread once per frame.
 *
 * In the loop every value is a pure function of the loop time t (`loopFrame`): the rotor speed ω, its angle (the
 * integral of ω in closed form), the blur arc and the boost. A hold on the pill or the fan takes over at the frame on
 * screen: ω climbs to MAX along the loop's ease-in-out over 2 s from wherever it is, and on release coasts back along
 * 1 − ease-out cubic over 1.5 s. 2 s after the last touch, with the fan back at idle, the loop restarts at t = 0; the
 * rotor carries on from its angle, so the restart is seamless. Reduce Motion: no rotation and no blur; the boost and
 * the percent go 0 → 1 over 0.4 s while held and back on release.
 */

/** Loop period, s. */
export const LOOP = 5.0;
const TAU = Math.PI * 2;
/** Rotor speed at rest and at full boost, rad/s. */
const IDLE = 0.15 * TAU;
const MAX = 9 * TAU;
const RAMP = 2.0;
const COAST = 1.5;
const RAMP_FROM = 0.5;
const MAX_FROM = 2.5;
const COAST_FROM = 3.3;
const REST_FROM = 4.8;
/** One blade spacing (22 blades); the blur arc is capped at two. */
const BLADE = TAU / 22;
/** A half-frame shutter at 30 fps. */
const SHUTTER = 0.033;
const IDLE_WAIT = 2.0;
/** Reduce Motion: the boost's fade in or out, s. */
const STILL_FADE = 0.4;

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
export function io(x: number) {
  'worklet';
  return x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2;
}
/** The inverse of `io`. */
function ioInv(y: number) {
  'worklet';
  const v = clamp01(y);
  return v < 0.5 ? Math.sqrt(v / 2) : 1 - Math.sqrt((1 - v) / 2);
}
/** ∫₀ˣ io(u) du. */
function ioInt(x: number) {
  'worklet';
  if (x <= 0.5) return (2 * x * x * x) / 3;
  const r = 2 - 2 * x;
  return 1 / 12 + (x - 0.5) - (1 - r * r * r) / 12;
}

/** Boost 0…1 from the speed's share s, and the speed from s. */
export function boostOf(s: number) {
  'worklet';
  const v = clamp01(s);
  return v * v * (3 - 2 * v);
}
function omegaOf(s: number) {
  'worklet';
  return IDLE + (MAX - IDLE) * s;
}
function arcOf(s: number) {
  'worklet';
  return Math.min(omegaOf(s) * SHUTTER, 2 * BLADE);
}

/** The speed's share s = (ω − IDLE) / (MAX − IDLE) at loop time t. */
export function shareAt(t: number) {
  'worklet';
  if (t < RAMP_FROM) return 0;
  if (t < MAX_FROM) return io(seg(t, RAMP_FROM, MAX_FROM));
  if (t < COAST_FROM) return 1;
  if (t < REST_FROM) {
    const r = 1 - seg(t, COAST_FROM, REST_FROM);
    return r * r * r;
  }
  return 0;
}

/** ∫₀ᵗ ω dt in closed form, rad. */
function turned(t: number) {
  'worklet';
  const D = MAX - IDLE;
  let a = IDLE * t;
  if (t > RAMP_FROM) a += D * RAMP * ioInt(seg(t, RAMP_FROM, MAX_FROM));
  if (t > MAX_FROM) a += D * (Math.min(t, COAST_FROM) - MAX_FROM);
  if (t > COAST_FROM) {
    const r = 1 - seg(t, COAST_FROM, REST_FROM);
    a += (D * COAST * (1 - r * r * r * r)) / 4;
  }
  return a;
}
/** Scales the turn so one loop is exactly 20 revolutions (440 blade spacings) and the loop closes (#50, answer 1). */
const K = (20 * TAU) / turned(LOOP);

/** The rotor's angle at loop time t, rad. */
export function angleAt(t: number) {
  'worklet';
  return K * turned(t);
}

export type Frame = {
  /** Rotor angle (0…2π), blur arc (rad) and boost 0…1. */
  A: number;
  arc: number;
  B: number;
  /** The speed's share 0…1: the percent shown is round(100 s). */
  s: number;
  /** The pill is held. */
  held: number;
};

function wrap(a: number) {
  'worklet';
  return a - TAU * Math.floor(a / TAU);
}

export function loopFrame(t: number, from = 0): Frame {
  'worklet';
  const s = shareAt(t);
  return {
    A: wrap(from + angleAt(t)), arc: arcOf(s), B: boostOf(s), s, held: t >= RAMP_FROM && t < COAST_FROM ? 1 : 0,
  };
}

export const MODE = { auto: 0, live: 1, still: 2 } as const;

export type Core = {
  mode: number;
  last: number;
  start: number;
  /** Where the rotor was when the loop restarted, rad. */
  from: number;
  /** Live: the rotor's angle and the speed's share. */
  A: number;
  s: number;
  /** Ramp progress (s = io(x)) while held, coast progress (s = (1 − y)³) after. */
  x: number;
  y: number;
  /** Fingers (or a VoiceOver hold) on the pill or the fan. */
  holding: number;
  /** An accessibility activation: boost to full, then let go. */
  once: boolean;
  /** The last quarter of s ticked while held. */
  quarter: number;
  idle: number;
  /** Events for the JS side: ticks counts every quarter of s crossed while held; full is set on reaching MAX. */
  ticks: number;
  full: boolean;
  out: Frame;
};

export function newCore(): Core {
  return {
    mode: MODE.auto, last: -1, start: -1, from: 0, A: 0, s: 0, x: 0, y: 1, holding: 0, once: false, quarter: 0, idle: 0,
    ticks: 0, full: false, out: loopFrame(0),
  };
}

/** Leave the loop at the frame on screen. */
function takeOver(c: Core) {
  'worklet';
  if (c.mode !== MODE.auto) return;
  const t = c.start < 0 ? 0 : ((c.last - c.start) / 1000) % LOOP;
  c.mode = MODE.live;
  c.A = wrap(c.from + angleAt(t));
  c.s = shareAt(t);
  c.x = ioInv(c.s);
  c.y = 1 - Math.cbrt(c.s);
}

function liveFrame(c: Core) {
  'worklet';
  return { A: c.A, arc: arcOf(c.s), B: boostOf(c.s), s: c.s, held: c.holding > 0 ? 1 : 0 };
}

/** A finger (or a VoiceOver hold) on the pill or the fan: spool up from the speed on screen. */
export function hold(c: Core) {
  'worklet';
  takeOver(c);
  c.holding += 1;
  c.idle = 0;
  if (c.mode === MODE.still) return;
  c.x = ioInv(c.s);
  c.quarter = Math.min(3, Math.floor(c.s * 4 + 1e-9));
}

/** The finger lifts: coast down from the speed on screen. */
export function release(c: Core) {
  'worklet';
  if (c.holding <= 0) return;
  c.holding -= 1;
  c.idle = 0;
  if (c.holding === 0 && c.mode !== MODE.still) c.y = 1 - Math.cbrt(c.s);
}

export function step(c: Core, now: number, autoPlay: boolean) {
  'worklet';
  const dt = c.last < 0 ? 0 : Math.min(Math.max((now - c.last) / 1000, 0), 0.05);
  c.last = now;

  if (c.mode === MODE.still) {
    const before = c.s;
    c.s = c.holding > 0 ? Math.min(1, c.s + dt / STILL_FADE) : Math.max(0, c.s - dt / STILL_FADE);
    if (before < 1 && c.s >= 1) {
      c.full = true;
      if (c.once) c.holding = Math.max(0, c.holding - 1);
      c.once = false;
    }
    c.out = { A: 0, arc: 0, B: boostOf(c.s), s: c.s, held: c.holding > 0 ? 1 : 0 };
    return;
  }
  if (c.mode === MODE.auto) {
    if (autoPlay) {
      if (c.start < 0) c.start = now;
      c.out = loopFrame(((now - c.start) / 1000) % LOOP, c.from);
      return;
    }
    takeOver(c);
  }

  if (c.holding > 0) {
    if (c.s < 1) {
      c.x = Math.min(1, c.x + dt / RAMP);
      c.s = io(c.x);
      // A light tick at each quarter of the way up; the last one is the success at full boost.
      const q = Math.min(3, Math.floor(c.s * 4 + 1e-9));
      if (q > c.quarter) {
        c.ticks += q - c.quarter;
        c.quarter = q;
      }
      if (c.x >= 1) {
        c.s = 1;
        c.full = true;
        if (c.once) {
          c.once = false;
          release(c);
        }
      }
    }
  } else if (c.s > 0) {
    c.y = Math.min(1, c.y + dt / COAST);
    const r = 1 - c.y;
    c.s = c.y >= 1 ? 0 : r * r * r;
  }
  c.A = wrap(c.A + omegaOf(c.s) * dt);
  c.out = liveFrame(c);

  // The idle clock waits for the fan to coast back to rest.
  if (c.holding > 0 || c.s > 0) {
    c.idle = 0;
    return;
  }
  c.idle += dt;
  if (!autoPlay || c.idle < IDLE_WAIT) return;
  // Back to the loop at t = 0 (rest, idle speed), the rotor going on from where it is.
  c.mode = MODE.auto;
  c.start = now;
  c.from = c.A;
  c.out = loopFrame(0, c.from);
}

/** VoiceOver / TalkBack activation without a hold: one full boost, then coast. */
export function boostOnce(c: Core) {
  'worklet';
  if (c.once) return;
  hold(c);
  c.once = true;
  if (c.s >= 1) {
    c.once = false;
    c.full = true;
    release(c);
  }
}

export function setStill(c: Core, still: boolean) {
  'worklet';
  if (still && c.mode !== MODE.still) {
    c.mode = MODE.still;
    c.s = 0;
    c.out = { A: 0, arc: 0, B: 0, s: 0, held: c.holding > 0 ? 1 : 0 };
  } else if (!still && c.mode === MODE.still) {
    c.mode = MODE.auto;
    c.start = -1;
    c.from = 0;
    c.s = 0;
    c.holding = 0;
    c.once = false;
  }
}
