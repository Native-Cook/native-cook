// Pure, UI-thread-safe rules behind the console's motion and readout. No
// React Native imports, so they can be unit-tested with plain Node.

function clamp(value: number, min: number, max: number) {
  "worklet";
  return Math.max(min, Math.min(max, value));
}

/** Width, in drawing units, of the right-aligned "NN%" glyph run. */
export function glyphRun(value: number, size: number, advance = 0.62) {
  "worklet";
  const digits = value >= 100 ? 3 : value >= 10 ? 2 : 1;
  return (digits + 1) * advance * size;
}

/** Share (0…1) of a right-aligned glyph run that the light's edge covers. */
export function runCoverage(edge: number, boxRight: number, run: number) {
  "worklet";
  if (run <= 0) return 0;
  return clamp((edge - (boxRight - run)) / run, 0, 1);
}

/**
 * Whether the readout should be dark (1) or light (0). Hysteresis keeps it
 * from flickering while the light's edge rests inside the number: it turns
 * dark above `on` and only turns light again below `off`.
 */
export function inkTarget(
  coverage: number,
  dark: boolean,
  on = 0.5,
  off = 0.4,
) {
  "worklet";
  return dark ? (coverage < off ? 0 : 1) : coverage > on ? 1 : 0;
}

/**
 * Whether a readout step should roll. A channel swap never rolls (it happens
 * under the fade). While dragging, a step that changes the tens or hundreds
 * digit is applied instantly, since two digits rolling at once read as blur.
 */
export function rollsOnStep(
  previous: number | null,
  next: number,
  dragging: boolean,
  inPlace: boolean,
) {
  "worklet";
  if (!inPlace) return false;
  if (!dragging || previous === null) return true;
  return Math.floor(next / 10) === Math.floor(previous / 10);
}

/** Meniscus target: how far the leading edge leans for a given light speed. */
export function sloshTarget(speed: number) {
  "worklet";
  return clamp(speed * 0.02, -18, 24);
}

/**
 * One semi-implicit Euler step of the meniscus spring. The step is clamped
 * to 50ms so a hitch (or a backgrounded frame) cannot blow the spring up.
 */
export function springStep(
  position: number,
  velocity: number,
  target: number,
  dt: number,
  stiffness = 360,
  damping = 16,
) {
  "worklet";
  const h = clamp(dt, 0, 0.05);
  const acceleration = stiffness * (target - position) - damping * velocity;
  const v = velocity + acceleration * h;
  return { position: position + v * h, velocity: v };
}

/** True once the meniscus spring is visually at rest. */
export function springSettled(position: number, velocity: number) {
  "worklet";
  return Math.abs(position) < 0.05 && Math.abs(velocity) < 0.5;
}

/**
 * Lifted console released with `velocity` (rad/s): the half turn it comes
 * to rest on (0 = face, ±1 = back, ±2 = face again…). The flick projects the
 * yaw forward by `reach` seconds before rounding to the nearest face.
 */
export function restingHalfTurn(yaw: number, velocity: number, reach = 0.2) {
  "worklet";
  return Math.round((yaw + velocity * reach) / Math.PI);
}

/** Yaw to set the console down at: the nearest full turn, face up. */
export function faceUpYaw(yaw: number) {
  "worklet";
  return Math.round(yaw / (Math.PI * 2)) * Math.PI * 2;
}
