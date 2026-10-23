// Chrome Zero — deterministic, seconds-based choreography and shared gesture physics.
import { projectZero, REST } from './zero-contract';
import { ZERO_DRAG_POINTS, ZERO_DURATION } from './zero-theme';

export const TAU = Math.PI * 2;
/** Rain ends at 3.4 s; a 460 ms bead sweep precedes the solid's own sweep. */
export const ZERO_ANTICIPATION = 3.4;
export const ZERO_FUSE = 3.86;
export const ZERO_SETTLE = 5.54;
// The 140 ms longer entrance comes out of the final rest, preserving every later beat.
const DRAG_START = 5.94;
const HOLD_START = 8.64;
const BUTTON_START = 10.24;
/** Exponential drag in seconds, independent of refresh rate. */
export const COAST_FRICTION = 2.6;
export const COAST_SECONDS = 0.95;
export const COAST_SETTLE = 0.93;

export type ZeroFrame = {
  yaw: number; pitch: number; scale: number; build: number; drain: number; fuse: number;
  ripple: number; rippleX: number; rippleY: number; sweep: number; percent: number; flash: number; dim: number; drift: number;
  card: number; cardOpacity: number; cardExit: number;
  sparkles: number[]; sparkleRotation: number[]; sparkleOpacity: number[]; twinkle: number[];
  farDustOpacity: number[]; nearDustOpacity: number[];
  copy: number[]; copyY: number[];
  buttonPress: number; arrow: number; glint: number;
  markerX: number; markerY: number; markerDrag: number; markerOpacity: number; markerPress: number; markerButton: number;
};

export function clamp01(n: number) { 'worklet'; return Math.max(0, Math.min(1, n)); }
export function progress(t: number, start: number, duration: number) {
  'worklet'; return clamp01((t - start) / duration);
}
export function lerp(a: number, b: number, p: number) { 'worklet'; return a + (b - a) * p; }
export function cubicOut(p: number) { 'worklet'; const q = 1 - clamp01(p); return 1 - q * q * q; }

/** Solve cubic-bezier x before evaluating y; fixed iterations are worklet-safe. */
export function bezier(p: number, x1 = 0.23, y1 = 1, x2 = 0.32, y2 = 1) {
  'worklet';
  if (p <= 1e-9) return 0;
  if (p >= 1 - 1e-9) return 1;
  let lo = 0; let hi = 1; let u = p;
  for (let i = 0; i < 15; i++) {
    const inv = 1 - u;
    const x = 3 * inv * inv * u * x1 + 3 * inv * u * u * x2 + u * u * u;
    if (x < p) lo = u; else hi = u;
    u = (lo + hi) / 2;
  }
  const inv = 1 - u;
  return 3 * inv * inv * u * y1 + 3 * inv * u * u * y2 + u * u * u;
}

/** Closed-form underdamped spring, unit mass, including initial velocity. */
export function spring(t: number, from: number, to: number, stiffness: number, damping: number, velocity = 0) {
  'worklet';
  if (t <= 0) return from;
  const decay = damping / 2;
  const frequency = Math.sqrt(Math.max(0.001, stiffness - decay * decay));
  const displacement = from - to;
  return to + Math.exp(-decay * t) * (displacement * Math.cos(frequency * t)
    + (velocity + decay * displacement) / frequency * Math.sin(frequency * t));
}

/** Gently remove the residual tail so an authored beat has an exact endpoint. */
export function landedSpring(t: number, from: number, to: number, stiffness: number, damping: number, end = 1.2, velocity = 0) {
  'worklet';
  if (t >= end) return to;
  return lerp(spring(t, from, to, stiffness, damping, velocity), to,
    cubicOut(progress(t, end - 0.18, 0.18)));
}
export function nearestRest(yaw: number) {
  'worklet'; return REST.yaw + Math.round((yaw - REST.yaw) / TAU) * TAU;
}
export function decayTravel(velocity: number, elapsed: number) {
  'worklet'; return velocity / COAST_FRICTION * (1 - Math.exp(-COAST_FRICTION * Math.max(0, elapsed)));
}
export function coastYaw(yaw: number, velocity: number, elapsed: number) {
  'worklet';
  if (elapsed < COAST_SECONDS) return yaw + decayTravel(velocity, elapsed);
  const at = yaw + decayTravel(velocity, COAST_SECONDS);
  const speed = velocity * Math.exp(-COAST_FRICTION * COAST_SECONDS);
  return landedSpring(elapsed - COAST_SECONDS, at, nearestRest(at), 100, 18, COAST_SETTLE, speed);
}
export function rubberPitch(pitch: number) {
  'worklet';
  const excess = Math.max(0, Math.abs(pitch) - 0.35);
  return Math.sign(pitch) * (Math.min(Math.abs(pitch), 0.35) + 0.12 * (1 - Math.exp(-excess / 0.12)));
}
export function loosen(t: number) { 'worklet'; return 1 - 0.78 * (1 - Math.exp(-Math.max(t, 0) / 0.26)); }
export function glintPulse(t: number) {
  'worklet'; if (t < 0 || t >= 0.3) return 0;
  return Math.sin(Math.PI * t / 0.3);
}

/** Finger coordinates to the face; cap foreshortening before clamping to its bounds. */
export function touchRipple(touchX: number, touchY: number, yaw: number,
  centreX: number, centreY: number, unit: number) {
  'worklet';
  return {
    x: Math.max(-0.64, Math.min(0.64, (touchX - centreX) / (unit * Math.max(Math.cos(yaw), 0.3)))),
    y: Math.max(-1, Math.min(1, -(touchY - centreY) / unit)),
  };
}

export function zeroRest(t = 0, reduced = false): ZeroFrame {
  'worklet';
  const twinkle = [1, 1, 1];
  for (let i = 0; i < 3; i++) {
    const wave = Math.sin(Math.PI * t / 2.4 + i * Math.PI / 3);
    twinkle[i] = reduced ? 1 : 1 - 0.1 * wave * wave;
  }
  return {
    yaw: REST.yaw + (reduced ? 0 : 0.17 * Math.sin(TAU * t / 6)), pitch: REST.pitch,
    scale: 1, build: 1, drain: 0, fuse: 1, ripple: 0, rippleX: 0, rippleY: 0, sweep: -1, percent: 1, flash: 0,
    dim: 0, drift: reduced ? 0 : Math.sin(TAU * t / 12),
    card: 1, cardOpacity: 1, cardExit: 0, sparkles: [1, 1, 1], sparkleRotation: [0, 0, 0],
    sparkleOpacity: [1, 1, 1], twinkle,
    farDustOpacity: [0.14, 0.10, 0.18, 0.12, 0.25], nearDustOpacity: [0.12, 0.10, 0.18],
    copy: [1, 1, 1, 1, 1], copyY: [0, 0, 0, 0, 0],
    buttonPress: 0, arrow: 0, glint: 0,
    markerX: 214, markerY: 286, markerDrag: 0, markerOpacity: 0, markerPress: 0, markerButton: 0,
  };
}

/** All screen values, with an exact 12-second periodic boundary. No timers or mutable state. */
export function zeroLoop(seconds: number): ZeroFrame {
  'worklet';
  // Nanosecond quantization removes floating-point boundary drift after repeated frame deltas.
  const t = (Math.round(((seconds % ZERO_DURATION + ZERO_DURATION) % ZERO_DURATION) * 1e9) / 1e9) % ZERO_DURATION;
  const f = zeroRest(t);
  if (t >= 0.9 && t < 2) {
    f.fuse = 1 - bezier(progress(t, 0.9, 0.26));
    f.drain = progress(t, 1.16, 0.62);
    f.percent = 1 - bezier(progress(t, 0.9, 0.32));
    f.dim = bezier(progress(t, 0.9, 0.4));
    f.yaw = lerp(f.yaw, 0.38, bezier(progress(t, 1.16, 0.62)));
    // The glass stays on stage while the numeral drains; a six-point dip settles before rain.
    f.cardExit = Math.sin(Math.PI * progress(t, 0.9, 0.9));
    for (let i = 0; i < 3; i++) {
      f.sparkleOpacity[i] = 1 - progress(t, 0.9, 0.18);
      f.sparkles[i] = 1 - 0.4 * bezier(progress(t, 0.9, 0.18));
    }
    for (let i = 0; i < 5; i++) {
      const p = progress(t, 0.9 + (4 - i) * 0.03, 0.2);
      f.copy[i] = 1 - p; f.copyY[i] = 8 * bezier(p);
    }
  } else if (t >= 2 && t < ZERO_SETTLE) {
    f.build = progress(t, 2, 1.4); f.fuse = 0; f.percent = 0; f.dim = 1; f.yaw = 0.38;
    f.scale = lerp(1, 0.955, bezier(progress(t, ZERO_ANTICIPATION, 0.46)));
    f.sweep = lerp(-1, 1, progress(t, ZERO_ANTICIPATION, 0.46));
    for (let i = 0; i < 3; i++) {
      f.sparkles[i] = 0.4; f.sparkleRotation[i] = -Math.PI / 2; f.sparkleOpacity[i] = 0;
    }
    for (let i = 0; i < 5; i++) { f.copy[i] = 0; f.copyY[i] = 16; }
    if (t >= ZERO_FUSE) {
      const p = t - ZERO_FUSE;
      // Slow enough for the mercury stage, balls sinking into the growing body, to be seen.
      const pour = 1 - Math.min(1, p / 0.72);
      f.fuse = 1 - pour * pour;
      f.ripple = p < 0.76 ? cubicOut(p / 0.76) : 0;
      f.flash = 0.55 * (p < 0.09 ? p / 0.09 : 1 - cubicOut((p - 0.09) / 0.6));
      f.dim = 1 - bezier(p / 0.6);
      f.scale = landedSpring(p, 0.955, 1, 260, 14);
      f.yaw = landedSpring(p, 0.38, REST.yaw, 60, 9, 1.5)
        + 0.17 * Math.sin(TAU * t / 6) * bezier(p / 1.68);
      f.sweep = p < 0.9 ? lerp(-1, 1, p / 0.9) : -1;
      f.percent = p < 0.16 ? 0 : landedSpring(p - 0.16, 0, 1, 170, 15);
      f.glint = Math.max(glintPulse(p), glintPulse(p - 0.3));
      for (let i = 0; i < 3; i++) {
        const at = p - 0.42 - i * 0.08;
        const pop = at < 0 ? 0 : landedSpring(at, 0, 1, 180, 15, 0.9);
        f.sparkles[i] = 0.4 + 0.6 * pop;
        f.sparkleRotation[i] = -(1 - pop) * Math.PI / 2;
        f.sparkleOpacity[i] = clamp01(at / 0.16);
      }
      for (let i = 0; i < 5; i++) {
        const enter = bezier(progress(p, 0.36 + i * 0.06, 0.52));
        f.copy[i] = enter; f.copyY[i] = 16 * (1 - enter);
      }
    }
  } else if (t >= DRAG_START && t < DRAG_START + 2.4) {
    const at = t - DRAG_START;
    const dragged = progress(at, 0.1, 0.42);
    const initialYaw = REST.yaw + 0.17 * Math.sin(TAU * DRAG_START / 6);
    const releaseYaw = initialYaw + 150 / ZERO_DRAG_POINTS;
    f.markerDrag = 150 * dragged;
    f.markerX = 214 + f.markerDrag;
    f.markerOpacity = Math.min(progress(at, 0, 0.08), 1 - progress(at, 0.52, 0.16));
    f.markerPress = 1 - progress(at, 0.52, 0.1);
    f.yaw = at <= 0.52 ? initialYaw + dragged * 150 / ZERO_DRAG_POINTS
      : coastYaw(releaseYaw, 2800 / ZERO_DRAG_POINTS, at - 0.52);
    // Whole turns are equivalent poses; remove them only after the spring has landed.
    if (at > 2.1) f.yaw += 0.17 * Math.sin(TAU * t / 6) * bezier(progress(at, 2.1, 0.3));
  } else if (t >= HOLD_START && t < HOLD_START + 1.4) {
    // Keep the release frame exact after subtracting the authored decimal start.
    const at = Math.round((t - HOLD_START) * 1e9) / 1e9;
    f.markerOpacity = Math.min(progress(at, 0, 0.08), 1 - progress(at, 0.7, 0.16));
    f.markerPress = 1 - progress(at, 0.7, 0.1);
    if (at < 0.7) { f.fuse = loosen(at); f.scale = lerp(1, 0.97, bezier(at / 0.22)); }
    else {
      const release = at - 0.7;
      f.fuse = Math.min(1, landedSpring(release, loosen(0.7), 1, 220, 18, 0.6));
      f.scale = landedSpring(release, 0.97, 1, 260, 14, 0.6);
      f.ripple = cubicOut(release / 0.7);
      const releaseYaw = zeroRest(HOLD_START + 0.7).yaw;
      const origin = projectZero(-0.42, -0.2, 0.26, releaseYaw, REST.pitch, 0.97, 214, 286, 158);
      const rippleOrigin = touchRipple(origin.x, origin.y, releaseYaw, 214, 286, 158);
      f.rippleX = rippleOrigin.x; f.rippleY = rippleOrigin.y;
      f.flash = 0.35 * (1 - cubicOut(release / 0.6)); f.glint = glintPulse(release);
    }
    // Pin the finger to the left stroke, including pitch, scale and perspective.
    const marker = projectZero(-0.42, -0.2, 0.26, f.yaw, f.pitch, f.scale, 214, 286, 158);
    f.markerX = marker.x; f.markerY = marker.y;
  } else if (t >= BUTTON_START && t < BUTTON_START + 1.4) {
    const at = t - BUTTON_START;
    f.markerButton = 1; f.markerX = 201; f.markerY = 788;
    f.markerOpacity = Math.min(progress(at, 0, 0.06), 1 - progress(at, 0.16, 0.16));
    f.markerPress = 1 - progress(at, 0.16, 0.1);
    f.buttonPress = at < 0.16 ? bezier(at / 0.12) : landedSpring(at - 0.16, 1, 0, 240, 20, 0.65);
    if (at >= 0.16) {
      const p = progress(at, 0.16, 1.1);
      f.yaw += TAU * bezier(p, 0.77, 0, 0.175, 1);
      f.sweep = p < 1 ? lerp(-1, 1, p) : -1;
      f.arrow = 4 * Math.sin(Math.PI * progress(at, 0.16, 0.5));
      f.glint = glintPulse(at - 0.53);
      for (let i = 0; i < 3; i++) f.twinkle[i] += 0.12 * Math.sin(Math.PI * p);
    }
  }
  for (let i = 0; i < 5; i++) f.farDustOpacity[i] *= 1 - f.dim;
  for (let i = 0; i < 3; i++) f.nearDustOpacity[i] *= 1 - f.dim;
  return f;
}

/** Mount-only prelude: decoded glass enters at 200 ms, settles, then the rain starts. */
export const ZERO_MOUNT_START = 0.9;
export function zeroEntrance(playhead: number): ZeroFrame {
  'worklet';
  if (playhead >= 2) return zeroLoop(playhead);
  const f = zeroLoop(2);
  const at = Math.round((playhead - ZERO_MOUNT_START - 0.2) * 1e9) / 1e9;
  f.card = at <= 0 ? 0 : landedSpring(at, 0, 1, 140, 17, 0.9);
  f.cardOpacity = at < 0 ? 0 : 1;
  return f;
}
