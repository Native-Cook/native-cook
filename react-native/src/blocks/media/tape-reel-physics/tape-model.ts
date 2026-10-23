/**
 * The tape's mechanics, all in closed form so the self-playing loop is exact: the reels' radii and
 * angles are functions of how much tape has been played, the slack tape is a damped spring driven by
 * steps, and the plucked span is a sum of string modes.
 */

// Reels

/** Seconds of tape on a side, and where the loop rests (12:41). */
export const TOTAL = 1800;
export const HOME = 761;
/** Pack radii (design points): an empty hub's 20, a full reel's 50; r_supply² + r_takeup² stays constant. */
const R_MIN = 20;
const R_MAX = 50;
/** Points of tape that pass the head per second of play (sets how fast the hubs turn). */
const FEED = 37;
const AREA = (R_MAX * R_MAX - R_MIN * R_MIN) / TOTAL;

export function takeUp(s: number) {
  'worklet';
  return Math.sqrt(R_MIN * R_MIN + AREA * s);
}
export function supply(s: number) {
  'worklet';
  return Math.sqrt(R_MAX * R_MAX - AREA * s);
}
/** The hubs' angles (radians, clockwise) after s seconds of tape: ∫ feed ds / r, which is closed form. */
export function takeUpAngle(s: number) {
  'worklet';
  return ((2 * FEED) / AREA) * (takeUp(s) - R_MIN);
}
export function supplyAngle(s: number) {
  'worklet';
  return ((2 * FEED) / AREA) * (R_MAX - supply(s));
}
/** Angular speed of a pack of radius r for a tape speed v (seconds of tape per second). */
export function spin(v: number, r: number) {
  'worklet';
  return (FEED * v) / r;
}

// Transport

export const STOP = 0;
export const PLAY = 1;
export const FF = 2;
export const REW = 3;
export const PAUSE = 4;
export const SEEK = 5;

export function moving(m: number) {
  'worklet';
  return m === PLAY || m === FF || m === REW || m === SEEK;
}

/** A segment: [start, mode, s, to, duration] — from start on, the transport is in mode with the tape at s; a seek glides to `to` in `duration`. */
export type Segment = number[];

export function easeInOut(x: number) {
  'worklet';
  const u = Math.min(Math.max(x, 0), 1);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}
function easeInOutSlope(x: number) {
  'worklet';
  const u = Math.min(Math.max(x, 0), 1);
  return u < 0.5 ? 12 * u * u : 3 * Math.pow(-2 * u + 2, 2);
}
export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const u = Math.min(Math.max((x - a) / (b - a), 0), 1);
  return u * u * (3 - 2 * u);
}

/**
 * Tape position and speed τ seconds into a segment. Play runs at 1×, wind at 320×, each reaching its
 * speed with a linear ramp (0.3 s, 0.35 s); stop and pause hold the tape.
 */
export function position(g: Segment, tau: number): [number, number] {
  'worklet';
  const [, mode, s0, to, duration] = g;
  if (mode === STOP || mode === PAUSE) return [s0, 0];
  if (mode === SEEK) {
    const u = tau / duration;
    if (u >= 1) return [to, 0];
    return [s0 + (to - s0) * easeInOut(u), ((to - s0) * easeInOutSlope(u)) / duration];
  }
  const speed = mode === PLAY ? 1 : mode === FF ? 320 : -320;
  const ramp = mode === PLAY ? 0.3 : 0.35;
  const d = tau < ramp ? (speed * tau * tau) / (2 * ramp) : speed * (tau - ramp / 2);
  const v = tau < ramp ? (speed * tau) / ramp : speed;
  const s = s0 + d;
  if (s <= 0) return [0, 0];
  if (s >= TOTAL) return [TOTAL, 0];
  return [s, v];
}

// Slack

/** How far the slack tape bows out when the transport stops (points), and its spring. */
export const SLACK = 7;
const GAMMA = 5;
const OMEGA = 16;

/** A unit step response of the damped spring: 0 at the step, settling at 1 with one overshoot. */
export function step(tau: number) {
  'worklet';
  if (tau <= 0) return 0;
  return 1 - Math.exp(-GAMMA * tau) * (Math.cos(OMEGA * tau) + (GAMMA / OMEGA) * Math.sin(OMEGA * tau));
}

// The span between the guides

/** Pulled at x (0…1 of the span) by d points: a triangle. */
function pulled(u: number, x: number, d: number) {
  'worklet';
  return u < x ? (d * u) / x : (d * (1 - u)) / (1 - x);
}

/** Let go: the triangle's first four string modes, each ringing and dying away (fundamental 5 Hz). */
export function ringing(u: number, x: number, d: number, tau: number) {
  'worklet';
  let y = 0;
  for (let n = 1; n <= 4; n++) {
    const a = ((2 * d) / (n * n * Math.PI * Math.PI * x * (1 - x))) * Math.sin(n * Math.PI * x);
    y += a * Math.sin(n * Math.PI * u) * Math.cos(2 * Math.PI * 5 * n * tau) * Math.exp(-(2.5 + 0.8 * n) * tau);
  }
  return y;
}

/** The hum of the span while the tape winds fast (a ≤ 1). */
function hum(u: number, a: number, clock: number) {
  'worklet';
  return a * (1.2 * Math.sin(Math.PI * u) * Math.sin(2 * Math.PI * 18 * clock) + 0.6 * Math.sin(3 * Math.PI * u) * Math.sin(2 * Math.PI * 27 * clock + 1));
}

/** A pluck: [x, d, releasedAt] — pulled at x by d now (releasedAt < 0), or let go from d at releasedAt. Empty: none. */
export type Pluck = number[];

// The self-playing loop

export const LOOP = 9;

/** 0.6 play · 1.6–1.95 a finger pulls the tape 22 pt at 38 % · 2.1 lets go · 3.2 fast forward · 4.6 stop · 5.2 rewind back to 12:41 in 2.2 s · 7.4 stop · rest to 9.0. */
const SCRIPT: Segment[] = (() => {
  const g: Segment[] = [[0, STOP, HOME, 0, 0]];
  g.push([0.6, PLAY, HOME, 0, 0]);
  const atFF = position(g[1], 3.2 - 0.6)[0];
  g.push([3.2, FF, atFF, 0, 0]);
  const atStop = position(g[2], 4.6 - 3.2)[0];
  g.push([4.6, STOP, atStop, 0, 0]);
  g.push([5.2, SEEK, atStop, HOME, 2.2]);
  g.push([7.4, STOP, HOME, 0, 0]);
  return g;
})();
const SCRIPT_STEPS = [
  [0.6, -SLACK],
  [4.6, SLACK],
  [5.2, -SLACK],
  [7.4, SLACK],
];

function scriptPluck(t: number): Pluck {
  'worklet';
  if (t < 1.6) return [];
  if (t < 2.1) return [0.38, 22 * (1 - Math.pow(1 - Math.min((t - 1.6) / 0.35, 1), 3)), -1];
  return [0.38, 22, 2.1];
}

// A moment

export type Frame = {
  s: number;
  v: number;
  mode: number;
  /** The key shown pressed (index into the key row), or -1. */
  key: number;
  sag: number;
  pluck: Pluck;
  /** The span's hum while winding (0…1) and the meters' level envelope (0…1). */
  hum: number;
  meters: number;
  /** A scripted finger on the tape: x on the span, d below it, and its opacity (0: none). */
  fingerX: number;
  fingerD: number;
  fingerAlpha: number;
};

/** Evaluates a timeline (segments, slack steps on a base) at time t. */
export function frameAt(t: number, g: Segment[], sagBase: number, steps: number[][], pluck: Pluck): Frame {
  'worklet';
  let i = 0;
  for (let k = 0; k < g.length; k++) if (g[k][0] <= t) i = k;
  const seg = g[i];
  const tau = t - seg[0];
  const [s, v] = position(seg, tau);
  const previous = i > 0 ? g[i - 1][1] : -1;
  const mode = seg[1];
  let key = -1;
  if (mode === PLAY) key = 1;
  else if (mode === FF) key = 2;
  else if (mode === REW || mode === SEEK) key = 0;
  else if (mode === PAUSE) key = 3;
  else if (previous >= 0 && previous !== STOP && tau < 0.2) key = 4;
  let sag = sagBase;
  for (const [at, delta] of steps) sag += delta * step(t - at);
  let meters = 0;
  if (mode === PLAY) meters = smoothstep(0, 0.3, tau);
  else if (previous === PLAY) meters = 1 - smoothstep(0, 0.3, tau);
  return {
    s,
    v,
    mode: mode === SEEK ? REW : mode,
    key,
    sag,
    pluck,
    hum: Math.min(1, Math.abs(v) / 200),
    meters,
    fingerX: 0,
    fingerD: 0,
    fingerAlpha: 0,
  };
}

export function auto(t: number): Frame {
  'worklet';
  const p = scriptPluck(t);
  const f = frameAt(t, SCRIPT, SLACK, SCRIPT_STEPS, p);
  if (t >= 1.45 && t < 2.35) {
    f.fingerX = 0.38;
    f.fingerD = p.length === 0 ? 0 : p[2] < 0 ? p[1] : 22;
    f.fingerAlpha = smoothstep(1.45, 1.6, t) * (1 - smoothstep(2.1, 2.35, t));
  }
  return f;
}

/** The span's displacement at u (0…1) for a frame, with time t on the frame's clock. */
export function span(u: number, f: Frame, t: number, still: boolean) {
  'worklet';
  let y = 0;
  const p = f.pluck;
  if (p.length === 3) {
    if (p[2] >= 0) {
      if (!still) y += ringing(u, p[0], p[1], t - p[2]);
    } else {
      y += pulled(u, p[0], p[1]);
    }
  }
  if (!still) y += hum(u, f.hum, t);
  return y;
}

/** The meters: a level 0…1 per channel, from a fixed mix of sines. */
export function level(t: number, channel: number) {
  'worklet';
  const p = channel * 0.9;
  return (
    0.55 +
    0.2 * Math.sin(2 * Math.PI * 1.7 * t + p) +
    0.12 * Math.sin(2 * Math.PI * 4.3 * t + 1.1 + p) +
    0.08 * Math.sin(2 * Math.PI * 7.9 * t + 2.3 + 2 * p)
  );
}

export function clock(s: number) {
  'worklet';
  const n = Math.floor(s);
  return `${String(Math.floor(n / 60)).padStart(2, '0')}:${String(n % 60).padStart(2, '0')}`;
}
