/**
 * The coupon's clock and geometry. Every value on screen is a pure function of the loop time (or of
 * the finger's state), so the frame at t = LOOP is the frame at 0. Lengths are pt, angles radians,
 * times seconds. Coordinates are card-local: (0, 0) is the coupon's top-left corner at rest.
 */

export const LOOP = 9;

// Script beats (s).
const GRAB_AT = 1.4;
const TEAR_END = 3.6;
const SWING_END = 4.6;
export const FLY_START = SWING_END;
const FLY_END = 5.4;
export const STAMP_AT = 5.0;
const STAMP_LEN = 0.38;
const SINK_START = 6.8;
const SINK_END = 7.4;
const EXIT_END = 7.6;
/** The fresh coupon starts sliding in while the redeemed one is still leaving. */
const ENTER_START = 7.1;
const ENTER_END = 8.2;

/** Card height; the stub is STUB_W wide on the right, joined by the perforation. */
export const CARD_H = 200;
export const STUB_W = 116;
export const CORNER = 20;
export const NOTCH_R = 11;
export const HOLE_R = 2.7;
export const HOLE_GAP = 10;
/** The stub leans at most this far (rad) about the tear front while it rips (about 13°). */
const TEAR_LEAN = 0.22;
/** Where the stub hangs from the finger once it is free (rad). */
const HANG = -0.09;
/** Swing: decay rate (1/s) and frequency (Hz) of the free stub's pendulum. */
const SWING_DECAY = 3.2;
const SWING_HZ = 1.45;
/** The stub sits in the wallet at this angle and scale. */
export const POCKET_TILT = 0.12;
export const POCKET_SCALE = 0.6;
/** A filed stub sinks this far below its peek before the next coupon arrives. */
const SINK_DEPTH = 96;

export type Geom = {
  cw: number;
  px: number;
  /** The stub's grab point, in card coordinates. */
  gx: number;
  gy: number;
  /** Where the grab point lands in the wallet, in card coordinates. */
  pocketX: number;
  pocketY: number;
  /** Width of the screen: how far the coupon slides out and in. */
  travel: number;
  /** Top of the wallet's front panel, in card coordinates. */
  front: number;
};

export type Pose = {
  /** Tear progress 0…1 and where the tear front is (pt from the top). */
  tear: number;
  front: number;
  /** World position of the grab point (card coordinates), stub angle and scale. */
  sx: number;
  sy: number;
  phi: number;
  scale: number;
  /** 0 lying flat … 1 lifted (shadow). */
  lift: number;
  /** Ink stamp on the body, 0…1 (with overshoot inside). */
  stamp: number;
  /** Horizontal offset of the whole coupon (the swap). */
  slide: number;
  /** Whether the stub is drawn in the wallet layer (behind the pocket's front). */
  filed: number;
  /** The fresh coupon arriving while the redeemed one leaves: its offset, and 1 while it shows. */
  fresh: number;
  freshOn: number;
};

export function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
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
  return 1 - Math.pow(1 - x, 3);
}

/** Ease-out that passes the target by about 4 % and settles. */
function easeOutBack(x: number) {
  'worklet';
  const c = 0.9;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** Number of perforation holes down the card. */
export function holeCount() {
  'worklet';
  return Math.floor((CARD_H - 2 * NOTCH_R - 8) / HOLE_GAP) + 1;
}

/** The tear front travels hole by hole: each gap rips quickly, then the next hole holds a moment. */
export function frontAt(tear: number) {
  'worklet';
  const n = holeCount();
  const x = clamp01(tear) * n;
  const k = Math.floor(x);
  const f = x - k;
  const q = Math.min(n, k + (f < 0.3 ? 0 : (f - 0.3) / 0.7)) / n;
  return NOTCH_R + (CARD_H - 2 * NOTCH_R) * q;
}

/** The stub while still joined: it turns about the tear front by up to TEAR_LEAN. */
export function attachedPose(g: Geom, tear: number, nudge: number) {
  'worklet';
  const front = frontAt(tear);
  const theta = TEAR_LEAN * tear + nudge;
  const dx = g.gx - g.px;
  const dy = g.gy - front;
  const c = Math.cos(theta);
  const s = Math.sin(theta);
  return { front, sx: g.px + dx * c - dy * s, sy: front + dx * s + dy * c, phi: theta };
}

/** The free stub's pendulum, tau seconds after it came loose. */
function swingAngle(tau: number) {
  'worklet';
  const from = TEAR_LEAN;
  return HANG + (from - HANG) * Math.exp(-SWING_DECAY * tau) * Math.cos(2 * Math.PI * SWING_HZ * tau);
}

/** Free stub held by the script's finger, which drifts down and back in toward the wallet. */
function swingPose(g: Geom, tau: number) {
  'worklet';
  const end = attachedPose(g, 1, 0);
  const drift = easeOut(clamp01(tau));
  return { sx: end.sx - 46 * drift, sy: end.sy + 34 * drift, phi: swingAngle(tau) };
}

export type Fly = { x: number; y: number; phi: number; on: number };

/** Where the scripted flight starts unless a finger let go of the stub somewhere else. */
export function defaultFly(g: Geom): Fly {
  'worklet';
  const p = swingPose(g, SWING_END - TEAR_END);
  return { x: p.sx, y: p.sy, phi: p.phi, on: 0 };
}

function rest(): Pose {
  'worklet';
  return { tear: 0, front: NOTCH_R, sx: 0, sy: 0, phi: 0, scale: 1, lift: 0, stamp: 0, slide: 0, filed: 0, fresh: 0, freshOn: 0 };
}

/** Offset of the fresh coupon sliding in from the right (0 once it has settled). */
function arriving(g: Geom, t: number) {
  'worklet';
  return g.travel * (1 - easeOutBack(seg(t, ENTER_START, ENTER_END)));
}

/** The whole loop, as a function of t in [0, LOOP). `fly` is where the flight to the wallet starts. */
export function scriptPose(g: Geom, t: number, fly: Fly): Pose {
  'worklet';
  const p = rest();
  if (t < TEAR_END) {
    const grab = seg(t, GRAB_AT, GRAB_AT + 0.25);
    // Anticipation: the finger presses the stub back a hair before it pulls.
    const nudge = -0.035 * Math.sin(Math.PI * seg(t, GRAB_AT, GRAB_AT + 0.32));
    const u = seg(t, GRAB_AT + 0.2, TEAR_END);
    // Slow first holes, then the rip runs away.
    const tear = 0.22 * u + 0.78 * u * u * u;
    const a = attachedPose(g, tear, nudge);
    p.tear = tear;
    p.front = a.front;
    p.sx = a.sx;
    p.sy = a.sy;
    p.phi = a.phi;
    p.lift = easeOut(grab);
    if (t < GRAB_AT) p.lift = 0;
  } else if (t < FLY_START) {
    const s = swingPose(g, t - TEAR_END);
    p.tear = 1;
    p.front = CARD_H;
    p.sx = s.sx;
    p.sy = s.sy;
    p.phi = s.phi;
    p.lift = 1;
  } else if (t < EXIT_END) {
    const from = fly.on > 0 ? fly : defaultFly(g);
    const u = easeInOut(seg(t, FLY_START, FLY_END));
    // A quadratic arc that swings out to the right and drops into the wallet's slot.
    const cx = (from.x + g.pocketX) / 2 + 28;
    const cy = from.y + (g.pocketY - from.y) * 0.3 - 24;
    const a = (1 - u) * (1 - u);
    const b = 2 * (1 - u) * u;
    const c = u * u;
    p.tear = 1;
    p.front = CARD_H;
    p.sx = a * from.x + b * cx + c * g.pocketX;
    p.sy = a * from.y + b * cy + c * g.pocketY + SINK_DEPTH * easeInOut(seg(t, SINK_START, SINK_END));
    p.phi = from.phi + (POCKET_TILT - from.phi) * u;
    p.scale = 1 + (POCKET_SCALE - 1) * u;
    p.lift = 1 - seg(t, FLY_END - 0.2, FLY_END);
    // The stub moves behind the pocket's front the moment its lower edge reaches it.
    const bottom = p.sy + p.scale * (CARD_H - g.gy) * Math.cos(p.phi);
    p.filed = u > 0.3 && bottom > g.front - 1 ? 1 : 0;
    const st = seg(t, STAMP_AT, STAMP_AT + STAMP_LEN);
    p.stamp = t < STAMP_AT ? 0 : 1 + 0.35 * (1 - easeOutBack(st));
    // The redeemed coupon leaves to the left.
    p.slide = -g.travel * easeInOut(seg(t, SINK_START, EXIT_END));
    p.fresh = arriving(g, t);
    p.freshOn = t >= ENTER_START ? 1 : 0;
  } else {
    // The fresh coupon (stub joined again) is now the live one; it settles with a small overshoot.
    p.slide = arriving(g, t);
  }
  if (p.tear === 0 && p.sx === 0) {
    const a = attachedPose(g, 0, 0);
    p.sx = a.sx;
    p.sy = a.sy;
  }
  return p;
}

/** The finger's version: joined with tear progress, or free and held at (x, y, phi). */
export function userPose(g: Geom, tear: number, free: number, x: number, y: number, phi: number): Pose {
  'worklet';
  const p = rest();
  if (free > 0) {
    p.tear = 1;
    p.front = CARD_H;
    p.sx = x;
    p.sy = y;
    p.phi = phi;
    p.lift = 1;
    return p;
  }
  const a = attachedPose(g, Math.max(0, tear), 0);
  p.tear = Math.max(0, tear);
  p.front = a.front;
  p.sx = a.sx;
  p.sy = a.sy;
  p.phi = a.phi;
  p.lift = clamp01(tear * 6);
  return p;
}
