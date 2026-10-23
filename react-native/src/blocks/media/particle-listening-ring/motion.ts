/*
 The particles of the listening ring. Every value is a function of the loop time and a few seeded
 numbers per particle, so the loop is seamless and the stacks draw the same ring. A port of
 ParticleListeningRingMotion.swift.
 */

export const LOOP = 6;
/** Particles on the ring, and particles that fall into the middle when the voice is loud. */
export const RING_COUNT = 96;
export const INNER_COUNT = 16;
export const COUNT = RING_COUNT + INNER_COUNT;

/** Palette: coral, violet, teal, sun (0…1 RGB). */
export const COLORS = [
  [1.0, 0.42, 0.341],
  [0.424, 0.298, 0.961],
  [0.098, 0.702, 0.604],
  [1.0, 0.69, 0.125],
];

/**
 * Three swells of the scripted voice: centre (s), width (s), height, and how much each colour
 * leads it (violet, then teal with sun, then coral).
 */
const BURSTS = [
  { c: 1.1, w: 0.42, h: 0.9, mix: [0.1, 1.0, 0.15, 0.1] },
  { c: 2.45, w: 0.5, h: 1.0, mix: [0.1, 0.1, 1.0, 0.8] },
  { c: 3.85, w: 0.52, h: 0.9, mix: [1.0, 0.15, 0.1, 0.2] },
];

export function clamp(x: number, a = 0, b = 1) {
  'worklet';
  return Math.min(Math.max(x, a), b);
}

export function smoothstep(a: number, b: number, x: number) {
  'worklet';
  const t = clamp((x - a) / (b - a));
  return t * t * (3 - 2 * t);
}

export function easeOut(x: number) {
  'worklet';
  const t = clamp(x);
  return 1 - Math.pow(1 - t, 3);
}

/** A seeded number in 0..<1 for particle i and channel k (the same in every stack). */
export function rnd(i: number, k: number) {
  'worklet';
  const v = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453;
  return v - Math.floor(v);
}

/** 2π · cycles · t / loop: a phase that comes back to itself at the end of the loop. */
export function wave(cycles: number, t: number) {
  'worklet';
  return (2 * Math.PI * cycles * t) / LOOP;
}

/** The voice's envelope: silent at both ends of the loop. */
export function windowAt(t: number) {
  'worklet';
  return smoothstep(0.3, 0.8, t) * (1 - smoothstep(4.7, 5.4, t));
}

/** A quick flutter on the level, so the swells sound like a voice. */
export function flutter(t: number) {
  'worklet';
  return 1 + 0.18 * Math.sin(wave(11, t)) + 0.1 * Math.sin(wave(17, t) + 1);
}

function bump(b: number, t: number) {
  'worklet';
  const d = (t - BURSTS[b].c) / BURSTS[b].w;
  return BURSTS[b].h * Math.exp(-d * d);
}

/** The scripted level at loop time t, 0…~1. */
export function level(t: number) {
  'worklet';
  let a = 0;
  for (let b = 0; b < BURSTS.length; b++) a += bump(b, t);
  return a * windowAt(t) * flutter(t);
}

/** How much a particle of colour c grows from the scripted voice at loop time t. */
export function growth(c: number, t: number) {
  'worklet';
  let g = 0;
  for (let b = 0; b < BURSTS.length; b++) g += bump(b, t) * (0.25 + 0.75 * BURSTS[b].mix[c]);
  return g * windowAt(t) * flutter(t);
}

/** Weights of the four colours for a finger at angle a: the colour of that quarter leads. */
export function touchMix(a: number, c: number) {
  'worklet';
  const q = a / (2 * Math.PI) + 1;
  const quarter = (q - Math.floor(q)) * 4;
  let d = Math.abs(quarter - (c + 0.5));
  d = Math.min(d, 4 - d);
  return clamp(1.2 - d);
}

/** The quarter of the ring (0…3) at angle a. */
export function quarterOf(a: number) {
  'worklet';
  const q = a / (2 * Math.PI) + 1;
  return Math.floor((q - Math.floor(q)) * 4) % 4;
}

/** Everything that drives the ring at a moment. */
export type Drive = { t: number; gain: number; touch: number; touchAngle: number };

/** The colour that leads at a moment, as weights of the four (for the glow behind the ring). */
export function lead(d: Drive, c: number) {
  'worklet';
  return d.gain * growth(c, d.t) + d.touch * (0.25 + 0.75 * touchMix(d.touchAngle, c));
}

/** Particle i: [x, y, radius, alpha, colour]. */
export function dot(i: number, d: Drive, cx: number, cy: number, R: number) {
  'worklet';
  const t = d.t;
  const inner = i >= RING_COUNT;
  const theta = inner ? 2 * Math.PI * rnd(i, 1) : (2 * Math.PI * (i + 0.6 * (rnd(i, 1) - 0.5))) / RING_COUNT;
  const c = Math.floor(rnd(i, 2) * 4) % 4;
  const restSize = 1.0 + 1.4 * rnd(i, 3);
  const bigSize = 2.6 + 4.6 * Math.pow(rnd(i, 4), 1.6);
  const k = 2 + Math.floor(rnd(i, 5) * 4);
  const phi = 2 * Math.PI * rnd(i, 6);
  const j = rnd(i, 7) - 0.5;
  const j2 = rnd(i, 8) * 2 - 1;

  const a = d.gain * level(t) + d.touch;
  const swell = 1 + 0.12 * Math.min(a, 1.2);
  const n = 0.6 * Math.sin(3 * theta + wave(2, t) + 0.3) + 0.4 * Math.sin(5 * theta - wave(3, t) + 1.7);
  const angle = theta + 0.18 * Math.sin(wave(1, t)) + 0.05 * Math.sin(wave(k, t) + phi);

  // The finger pushes the dots near it outward.
  let da = angle - d.touchAngle;
  da = Math.atan2(Math.sin(da), Math.cos(da));
  const push = d.touch * Math.exp(-(da / 0.6) * (da / 0.6));

  let r = R * (1 + 0.03 * j) * swell + R * Math.min(a, 1.2) * (0.16 * n + 0.12 * j2) + R * 0.22 * push;
  if (inner) {
    const fall = smoothstep(0.2, 0.8, a) * (0.75 - 0.5 * rnd(i, 9));
    r *= 1 - fall;
  }
  const pulse = 0.75 + 0.25 * Math.sin(wave(k, t) + phi);
  const grow = d.gain * growth(c, t) + d.touch * (0.25 + 0.75 * touchMix(d.touchAngle, c));
  const size = restSize * (1 - 0.35 * Math.min(a, 1)) * (1 + 0.15 * Math.sin(wave(k, t) + phi)) + grow * bigSize * pulse;

  // In the quiet dips of the voice, half the ring empties, the empty half turning once a loop.
  const quiet = d.gain * windowAt(t) * (1 - smoothstep(0.15, 0.55, level(t)));
  const sector = smoothstep(-0.1, 0.5, Math.sin(angle - wave(1, t) - 0.6));
  const alpha = 1 - 0.9 * quiet * sector;

  return [cx + r * Math.cos(angle), cy + r * Math.sin(angle), size * (0.2 + 0.8 * alpha), alpha, c];
}
