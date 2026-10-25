/*
 Rocket Launch Progress — the deploy as pure functions of the loop time t (seconds).

 LOOP = 10 s: rest on the pad 0–1.4 · fuelling 1.4–4.4 (Build 0–25 %, Test –55 %, Upload –85 %,
 Launch prep –100 %, each stage easing in and out so the gauge breathes between steps) ·
 countdown 3-2-1 4.4–5.6 · ignition 5.6–6.2 (flame, smoke, the pad shakes) · lift-off 6.2–7.6
 (accelerating, pitching over to the right, a contrail behind) · deployed 7.6–8.4 · a new rocket
 rolls onto the pad 8.4–9.4 while the tank vents and the steps reset · rest 9.4–10.
*/

export const LOOP = 10;
export const FUEL_AT = 1.4;
export const COUNT_AT = 4.4;
export const IGNITE_AT = 5.6;
export const LIFT_AT = 6.2;
export const DONE_AT = 7.6;
export const ROLL_AT = 8.4;
export const ROLL_FOR = 1.0;
/** Fuel stages: [start, end, from %, to %]. */
export const STAGES: [number, number, number, number][] = [
  [1.4, 2.1, 0, 25],
  [2.1, 3.0, 25, 55],
  [3.0, 3.8, 55, 85],
  [3.8, 4.4, 85, 100],
];
/** Fuel % at which each step is done (Launch is done when the rocket is away). */
export const STEP_DONE = [25, 55, 85, 101];

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function easeInOutCubic(v: number) {
  'worklet';
  const x = clamp01(v);
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

export function easeOutCubic(v: number) {
  'worklet';
  return 1 - Math.pow(1 - clamp01(v), 3);
}

/** easeOutBack: a 4 % overshoot for things that land (a rocket rolling to a stop, a badge). */
export function easeOutBack(v: number) {
  'worklet';
  const x = clamp01(v);
  const c1 = 0.9;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
}

/** The deploy's progress (0–100) in the loop: filled by 4.4, held, back to 0 while the tank vents. */
export function autoFuel(t: number) {
  'worklet';
  if (t >= DONE_AT) return 100 * (1 - easeInOutCubic((t - DONE_AT) / 0.8));
  let f = 0;
  for (let i = 0; i < STAGES.length; i++) {
    const [a, b, p0, p1] = STAGES[i];
    if (t >= a) f = p0 + (p1 - p0) * easeInOutCubic((t - a) / (b - a));
  }
  return f;
}

/** Engine: 0 off → 1 full. Lights at 5.6 with a stutter, full by 6.2, out of sight after 7.6. */
export function throttle(t: number) {
  'worklet';
  if (t < IGNITE_AT || t > ROLL_AT) return 0;
  const k = t - IGNITE_AT;
  const stutter = 0.12 * Math.sin(k * 60) * Math.exp(-k * 5);
  return clamp01(easeOutCubic(k / 0.6) + stutter);
}

/** How hard the camera shakes: building with the engine, peaking at lift-off, gone by 7.3. */
export function shake(t: number) {
  'worklet';
  return easeOutCubic((t - IGNITE_AT) / 0.6) * (1 - easeOutCubic((t - 6.4) / 0.9));
}

/** The rocket's base (nozzle exit) and pitch, relative to its place on the pad. */
export function rocketPose(t: number, out: { x: number; y: number; rot: number; on: number }) {
  'worklet';
  out.on = 1;
  if (t < LIFT_AT) {
    out.x = 0; out.y = 0; out.rot = 0;
    return out;
  }
  if (t < ROLL_AT) {
    const k = t - LIFT_AT;
    // A slow first metre, then ~1100 pt/s²; it pitches over to the right as it climbs.
    out.y = -(550 * k * k + 18 * k * k * k * k * k);
    out.x = 70 * Math.pow(k, 3);
    out.rot = Math.atan2(210 * k * k, 1100 * k + 30) * 0.9;
    out.on = t < DONE_AT + 0.2 ? 1 : 0;
    return out;
  }
  // The next rocket rolls in from the left on its transporter and stops with a small rock.
  const p = easeOutBack((t - ROLL_AT) / ROLL_FOR);
  out.x = -420 * (1 - p);
  out.y = 0;
  out.rot = -0.03 * Math.sin(Math.PI * clamp01((t - ROLL_AT) / ROLL_FOR)) * (1 - p);
  return out;
}

/** The countdown digit k (3, 2, 1 → 0, 1, 2): its offset (pt, + = below) and opacity; rolls up through. */
export function countDigit(k: number, t: number) {
  'worklet';
  const a = COUNT_AT + k * 0.4;
  const b = a + 0.4;
  if (t < a - 0.01 || t > b + 0.2) return { y: 30, o: 0, s: 1 };
  const inn = easeOutCubic((t - a) / 0.16);
  const out = easeOutCubic((t - b + 0.04) / 0.16);
  return { y: 30 * (1 - inn) - 40 * out, o: inn * (1 - out), s: 0.92 + 0.08 * inn };
}

/** The "Deployed" badge: in at 7.6 with a little overshoot, out at 8.4. */
export function deployedBadge(t: number) {
  'worklet';
  const inn = easeOutBack((t - DONE_AT) / 0.4);
  const out = easeOutCubic((t - ROLL_AT) / 0.3);
  return { s: 0.9 + 0.1 * inn, o: clamp01((t - DONE_AT) / 0.2) * (1 - out) };
}

/**
 Step i's state: 0 waiting, 1 running, 2 done; from the fuel, and for Launch from the flight.
 All steps clear while the next rocket rolls in.
*/
export function stepState(i: number, t: number, fuel: number) {
  'worklet';
  if (t >= ROLL_AT + 0.2) return 0;
  if (i === 3) return t >= DONE_AT ? 2 : fuel >= 85 || t >= COUNT_AT ? 1 : 0;
  if (fuel >= STEP_DONE[i] || t >= DONE_AT) return 2;
  const start = i === 0 ? 0.01 : STEP_DONE[i - 1];
  return fuel >= start && (fuel > 0 || t >= FUEL_AT) ? 1 : 0;
}

/**
 The status line's phases. The last repeats the first, so the frame at t = LOOP (that line fully in)
 is the frame at 0.
*/
export const STATUS = ['Ready on the pad', 'Fuelling · build, test, upload', 'T-minus', 'Lift-off', 'Live in production', 'Next build rolling in', 'Ready on the pad'];
export const STATUS_AT = [0, FUEL_AT, COUNT_AT, IGNITE_AT, DONE_AT, ROLL_AT, ROLL_AT + ROLL_FOR];
/** Status line i's offset (pt, + = below) and opacity at t: in from below over 0.3 s, out upward. */
export function statusLine(i: number, t: number) {
  'worklet';
  const a = STATUS_AT[i];
  const b = i + 1 < STATUS_AT.length ? STATUS_AT[i + 1] : 1e9;
  const inn = i === 0 ? 1 : easeOutCubic((t - a) / 0.3);
  const out = easeOutCubic((t - b) / 0.3);
  if (t < a && i !== 0) return { y: 14, o: 0 };
  return { y: 14 * (1 - inn) - 14 * out, o: inn * (1 - out) };
}
