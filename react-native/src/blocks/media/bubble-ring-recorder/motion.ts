/*
 Everything that moves in the recorder is a function of a few times, so the loop and a finger
 share the same drawing. All times in seconds. A port of BubbleRingRecorderMotion.swift.
 */

/** autoPlay: tap at 1.2 s, record for 5.2 s, stop at 6.4 s, settled by 7.4 s, rest to 9 s. */
export const LOOP = 9;
export const RECORD_AT = 1.2;
export const TAKE = 5.2;
export const STOP_AT = RECORD_AT + TAKE;
/** The orb's glow breathes four times a loop. */
export const BREATH = 2.25;
/** Collapse of the orb into the dot, and the ring's arrival. */
export const COLLAPSE = 0.5;
export const RING_IN = 0.45;
export const RING_OUT = 0.6;
/** The red dot while recording. */
export const DOT_RADIUS = 9;
/** The grey ghost of the outline trails the white one by this much. */
export const GHOST_LAG = 0.22;

export function clamp(x: number, a = 0, b = 1) {
  'worklet';
  return Math.min(Math.max(x, a), b);
}

export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}

export function easeInOut(x: number) {
  'worklet';
  const t = clamp(x);
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

export function easeOut(x: number) {
  'worklet';
  const t = clamp(x);
  return 1 - Math.pow(1 - t, 3);
}

/** A spring from 0 to 1 (stiffness 170, damping 16, mass 1): about 8 % overshoot, settled in 0.8 s. */
export function spring(t: number) {
  'worklet';
  if (t <= 0) return 0;
  const w0 = Math.sqrt(170);
  const zeta = 16 / (2 * w0);
  const wd = w0 * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w0 * t) * (Math.cos(wd * t) + ((zeta * w0) / wd) * Math.sin(wd * t));
}

/** The jelly kick the ring gets when a take stops: up, down, gone in about a second. */
export function kick(u: number) {
  'worklet';
  return u <= 0 ? 0 : Math.exp(-4.5 * u) * Math.sin(11 * u);
}

/** 0…1, four times a loop; 0 at the loop's start. */
export function breathing(clock: number) {
  'worklet';
  return 0.5 - 0.5 * Math.cos((2 * Math.PI * clock) / BREATH);
}

/** Syllables of the scripted voice: centre, height, width (s). */
const SYLLABLES = [
  [0.75, 0.55, 0.16],
  [1.05, 0.85, 0.2],
  [1.5, 0.6, 0.14],
  [1.95, 1.0, 0.22],
  [2.35, 0.7, 0.16],
  [3.0, 0.5, 0.14],
  [3.3, 0.9, 0.2],
  [3.75, 0.75, 0.18],
  [4.3, 0.6, 0.16],
];

/**
 * The input level s seconds into a take, 0…1. It repeats every TAKE seconds, so a longer take
 * made by hand keeps talking; it is at its quiet floor at both ends of the pattern.
 */
export function level(s: number) {
  'worklet';
  if (s < 0) return 0;
  const p = s - Math.floor(s / TAKE) * TAKE;
  let v = 0.12;
  for (let i = 0; i < SYLLABLES.length; i++) {
    const d = (p - SYLLABLES[i][0]) / SYLLABLES[i][2];
    v += SYLLABLES[i][1] * Math.exp(-d * d);
  }
  // A fast tremble so the voice never sounds like a sine.
  v *= 1 + 0.12 * Math.sin(37 * s) + 0.08 * Math.sin(23 * s + 1);
  return clamp(v) * smoothstep(0.1, 0.5, s);
}

/** The ring's shape at angle θ: a few low harmonics drifting at their own speeds, -1…1. */
export function shape(theta: number, s: number) {
  'worklet';
  return (
    0.5 * Math.sin(2 * theta + 1.7 * s + 0.4) +
    0.3 * Math.sin(3 * theta - 2.3 * s + 1.1) +
    0.2 * Math.sin(5 * theta + 3.1 * s + 2.0) +
    0.12 * Math.sin(7 * theta - 4.2 * s)
  );
}

/** When the take being shown started and stopped (seconds ago, -1 when not). */
export type Clock = { sinceStart: number; sinceStop: number; lastTake: number; clock: number };

export type Frame = {
  orbRadius: number;
  orbness: number;
  presence: number;
  ringScale: number;
  wobble: number;
  ghostWobble: number;
  shapeTime: number;
  level: number;
  glow: number;
  /** Ripple progress 0…1, or -1 when none. */
  ripple: number;
  rippleIsStop: boolean;
  seconds: number;
  recording: number;
};

/** The radius of the orb a recording started s seconds ago, before it stops. */
export function recordingOrb(s: number, R: number, reduced: boolean) {
  'worklet';
  if (reduced) return DOT_RADIUS;
  const c = easeInOut(s / COLLAPSE);
  const dot = DOT_RADIUS * (1 + 0.35 * level(s));
  return R + (dot - R) * c;
}

export function frameAt(k: Clock, R: number, reduced: boolean): Frame {
  'worklet';
  const b = reduced ? 0 : breathing(k.clock);
  const f: Frame = {
    orbRadius: R * (1 + 0.012 * b),
    orbness: 1,
    presence: 0,
    ringScale: 1,
    wobble: 0,
    ghostWobble: 0,
    shapeTime: 0,
    level: 0,
    glow: b,
    ripple: -1,
    rippleIsStop: false,
    seconds: 0,
    recording: 0,
  };
  const s = k.sinceStart;
  if (s < 0) return f;
  const u = k.sinceStop;
  if (u >= 0) {
    // Stopping: the orb springs back out of the dot, the ring jiggles and fades to the faint circle.
    const len = s - u;
    const from = recordingOrb(len, R, reduced);
    const presAtStop = reduced ? 1 : easeInOut(len / RING_IN);
    if (reduced) {
      const x = clamp(u / 0.25);
      f.orbRadius = R;
      f.orbness = x;
      f.presence = presAtStop * (1 - x);
      return f;
    }
    f.orbRadius = from + (R * (1 + 0.012 * b) - from) * spring(u);
    f.orbness = smoothstep(DOT_RADIUS * 2, R, f.orbRadius);
    f.presence = presAtStop * (1 - easeInOut(u / RING_OUT));
    f.ringScale = 1 - 0.07 * kick(u);
    f.wobble = 0.08 * kick(u);
    f.ghostWobble = 0.08 * kick(u - GHOST_LAG);
    f.shapeTime = len;
    f.glow = b * smoothstep(0.3, 1.0, u);
    f.ripple = u < 0.6 ? u / 0.6 : -1;
    f.rippleIsStop = true;
    f.seconds = 0;
    return f;
  }
  // Recording.
  const lv = level(s);
  f.level = lv;
  f.recording = 1;
  f.seconds = s;
  f.orbRadius = recordingOrb(s, R, reduced);
  f.orbness = reduced ? 0 : smoothstep(DOT_RADIUS * 2, R, f.orbRadius);
  f.presence = reduced ? 1 : easeInOut(s / RING_IN);
  if (!reduced) {
    f.ringScale = 1 + 0.05 * lv;
    f.wobble = 0.14 * lv * f.presence;
    f.ghostWobble = 0.14 * level(s - GHOST_LAG) * f.presence;
    f.shapeTime = s;
    f.ripple = s < 0.7 ? s / 0.7 : -1;
  }
  f.glow = 0;
  return f;
}

/** Clock for autoPlay at loop time e. */
export function autoClock(e: number, clock: number, lastTake: number, afterFirstStop: boolean): Clock {
  'worklet';
  if (e >= STOP_AT) return { sinceStart: e - RECORD_AT, sinceStop: e - STOP_AT, lastTake: TAKE, clock };
  const last = afterFirstStop ? TAKE : lastTake;
  if (e >= RECORD_AT) return { sinceStart: e - RECORD_AT, sinceStop: -1, lastTake: last, clock };
  return { sinceStart: -1, sinceStop: -1, lastTake: last, clock };
}

/**
 * The take's waveform for the list row: n bars spread over 5.2 s, filled left to right while a
 * take of `length` seconds grows (a longer one scrolls); bars not recorded yet are -1.
 */
export function bars(n: number, length: number) {
  'worklet';
  const from = Math.max(0, length - TAKE);
  const out: number[] = [];
  for (let i = 0; i < n; i++) {
    const t = from + (i / (n - 1)) * TAKE;
    out.push(t <= length + 0.0001 ? level(t) : -1);
  }
  return out;
}

/** A recording's static waveform, for the older rows. */
export function staticBar(j: number, seed: number) {
  'worklet';
  return 0.15 + 0.85 * Math.abs(Math.sin(j * 0.61 + seed)) * (0.35 + 0.65 * Math.pow(Math.sin(j * 0.23 + seed * 2), 2));
}
