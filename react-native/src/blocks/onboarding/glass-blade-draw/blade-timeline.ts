/** Seconds throughout. No native imports: this exact timeline also runs in the proof. */
export interface BladeState {
  pos: number;
  charge: number;
  flare: number;
  press: number;
  ctaEnergy: number;
  intro: number;
  still: number;
}

export const LOOP_SECONDS = 10;
export const DRAW_POUR_SECONDS = 0.7;
export const DRAW_RETURN_SECONDS = 0.38;
export const STEP_SECONDS = 0.7;
/** Light a landed blade holds before the pour, and the extra a blade catches as it whips through the hero slot. */
export const DRAW_LOW = 0.2;
export const DRAW_FLASH = 0.5;

export function clamp01(value: number): number {
  'worklet';
  return Math.max(0, Math.min(1, value));
}

/** Invert the x coordinate of a unit cubic; bounded work, no captured callbacks. */
export function bezierProgress(x: number, x1: number, y1: number, x2: number, y2: number): number {
  'worklet';
  if (x <= 0) return 0;
  if (x >= 1) return 1;
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 24; i++) {
    const u = (lo + hi) / 2;
    const v = 1 - u;
    const at = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u;
    if (at < x) lo = u;
    else hi = u;
  }
  const u = (lo + hi) / 2;
  const v = 1 - u;
  return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u;
}

export function entrance(t: number): number {
  'worklet';
  return bezierProgress(t, 0.23, 1, 0.32, 1);
}

export function move(t: number): number {
  'worklet';
  // A step turns the whole fan, as the reference does: an even in-out, not the sharper UI move curve.
  return bezierProgress(t, 0.62, 0, 0.3, 1);
}

export function drawScale(n: number): number {
  'worklet';
  return Math.sqrt(Math.max(0.01, Math.abs(n)) / 4);
}

/** Seconds a four-blade spin takes; other distances scale with the square root of the distance. */
export const SPIN_SECONDS = 1.2;

/**
 * A wheel that is flicked and left to slow: it leaves at 3n/T slots per second and loses speed all the way,
 * so the last blade creeps into the slot. A small bump carries it 0.04 of a slot past and back, like a detent.
 */
export function spinOffset(tau: number, n: number): number {
  'worklet';
  if (tau <= 0) return 0;
  const t = tau / (SPIN_SECONDS * drawScale(n));
  if (t >= 1) return n;
  const left = 1 - t;
  const s = clamp01((t - 0.62) / 0.38);
  const bump = Math.sin(Math.PI * s);
  return n * (1 - left * left * left) + Math.sign(n) * 0.07 * bump * bump;
}

export function pourStart(n: number): number {
  'worklet';
  return 0.8 * SPIN_SECONDS * drawScale(n);
}

/** The pour is slow enough to read the count climbing; it still arrives without easing in. */
export function pourEase(t: number): number {
  'worklet';
  return bezierProgress(t, 0.3, 0.7, 0.4, 1);
}

/** The moment the pour takes the blade past its full light: solved on the pour's own curve, not guessed. */
export function flareStart(n: number): number {
  'worklet';
  const level = (1 - DRAW_LOW) / (1.15 - DRAW_LOW);
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 24; i++) {
    const mid = (lo + hi) / 2;
    if (pourEase(mid) < level) lo = mid;
    else hi = mid;
  }
  return pourStart(n) + DRAW_POUR_SECONDS * (lo + hi) / 2;
}

export function drawDuration(n: number): number {
  'worklet';
  return Math.max(pourStart(n) + DRAW_POUR_SECONDS + DRAW_RETURN_SECONDS, flareStart(n) + 0.74);
}

/** Analytic response of the bar's 380 / 26 / 1 release spring. */
export function releasedPress(tau: number, from = 1): number {
  'worklet';
  if (tau <= 0) return from;
  if (tau >= 0.8) return 0;
  const w = Math.sqrt(380 - 13 * 13);
  return from * Math.exp(-13 * tau) * (Math.cos(w * tau) + 13 / w * Math.sin(w * tau));
}

/** Position is an offset from launch. Optional starting lights preserve interrupted gestures. */
export function drawState(tau: number, n: number, flareGain = 1,
  initialCharge = 1, initialEnergy = 1, initialPress = 1, initialFlare = 0): BladeState {
  'worklet';
  const t = Math.max(0, tau);
  if (t >= drawDuration(n)) return { pos: n, charge: 1, flare: 0, press: 0, ctaEnergy: 1, intro: 1, still: 0 };
  const pour = pourStart(n);
  const spark = flareStart(n);
  // The faster the wheel, the more light each passing blade catches; a stopped wheel holds only DRAW_LOW.
  const speed = Math.abs(spinOffset(t + 0.008, n) - spinOffset(t - 0.008, n)) / 0.016;
  const spinning = DRAW_LOW + DRAW_FLASH * clamp01((speed - 0.5) / 8);
  let charge = initialCharge + (spinning - initialCharge) * entrance(t / 0.22);
  if (t >= pour) charge = DRAW_LOW + (1.15 - DRAW_LOW) * pourEase((t - pour) / DRAW_POUR_SECONDS);
  if (t >= pour + DRAW_POUR_SECONDS) {
    charge = 1.15 - 0.15 * entrance((t - pour - DRAW_POUR_SECONDS) / DRAW_RETURN_SECONDS);
  }
  let flare = initialFlare * (1 - entrance(t / 0.22));
  let ctaEnergy = initialEnergy + (0.12 - initialEnergy) * entrance(t / 0.28);
  if (t >= spark) {
    flare = flareGain * entrance((t - spark) / 0.09);
    if (t >= spark + 0.09) flare = flareGain * (1 - entrance((t - spark - 0.09) / 0.65));
    ctaEnergy = 0.12 + 0.88 * entrance((t - spark) / 0.52);
  }
  return { pos: spinOffset(t, n), charge, flare, press: releasedPress(t, initialPress), ctaEnergy, intro: 1, still: 0 };
}

export function restState(pos: number): BladeState {
  'worklet';
  return { pos, charge: 1, flare: 0, press: 0, ctaEnergy: 1, intro: 1, still: 0 };
}

/** Includes period rollover so the function itself proves the seamless 9-card advance. */
export function loopState(time: number, base = 0): BladeState {
  'worklet';
  const cycles = Math.floor(Math.max(0, time) / LOOP_SECONDS);
  const t = Math.max(0, time) - cycles * LOOP_SECONDS;
  const origin = base + cycles * 9;
  if (t < 3.44) {
    let pos = origin;
    if (t >= 1) pos += move((t - 1) / STEP_SECONDS);
    if (t >= 2.25) pos += move((t - 2.25) / STEP_SECONDS);
    const state = restState(pos);
    if (t >= 3.3) state.press = entrance((t - 3.3) / 0.11);
    return state;
  }
  if (t < 5.74) {
    const state = drawState(t - 3.44, 4);
    state.pos += origin + 2;
    if (t >= 5.6) state.press = entrance((t - 5.6) / 0.11);
    return state;
  }
  const state = drawState(t - 5.74, 3, 0.55);
  state.pos += origin + 6;
  return state;
}
