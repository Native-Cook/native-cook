/*
 Magnetic Letters Name — the timeline and the flight of one magnet, as pure functions of time.

 A magnet's pose is where its centre is (pt), its turn (rad), how far it is lifted off the door
 (0 = flat on the steel, 1 = held a finger's width away) and a uniform scale (the snap's bounce).
 autoPlay asks `autoPose(t)` for every magnet; under touch each magnet keeps a flight record
 (from, to, start, kind) and `flightPose` is asked with the time since that flight began, so both
 paths share the same curves.
*/

/** The loop, in seconds. */
export const LOOP = 9;
/** The name the loop spells. */
export const SCRIPT = 'NOAH';
/** Typing: the first letter leaves the pile at 1.3 s, one more every 0.42 s, each flight 0.38 s. */
export const TYPE_START = 1.3;
export const TYPE_STAGGER = 0.42;
export const FLY = 0.38;
/** "Continue" lights up at 3.6 s (0.25 s) and goes out with the first backspace. */
export const READY_AT = 3.6;
/** Backspace: the last letter pops off at 5.2 s, one more every 0.45 s, each flight 0.45 s. */
export const POP_START = 5.2;
export const POP_STAGGER = 0.45;
export const POP = 0.45;
/** The longest name the line holds. */
export const MAX_LETTERS = 10;
/** A drop after a drag: 0.2 s to fall back onto the steel. */
export const DROP = 0.2;

export const KIND_REST = 0;
export const KIND_SNAP = 1;
export const KIND_LAND = 2;
export const KIND_DROP = 3;
export const KIND_HELD = 4;

export type Pose = { x: number; y: number; rot: number; lift: number; scale: number };

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
  const x = clamp01(v);
  return 1 - Math.pow(1 - x, 3);
}

/** The tiny bounce after contact: the scale dips ~5 % within 40 ms, overshoots ~2 %, and settles. */
export function contactBounce(since: number) {
  'worklet';
  if (since <= 0 || since > 0.7) return 1;
  return 1 - 0.06 * Math.exp(-since * 11) * Math.sin(since * 38);
}

/** How a magnet's turn approaches 0 so it never spins the long way round. */
function shortAngle(a: number) {
  'worklet';
  let v = a;
  while (v > Math.PI) v -= Math.PI * 2;
  while (v < -Math.PI) v += Math.PI * 2;
  return v;
}

/**
 Magnetic snap from `from` onto `to` (a slot on the name line). For the first 80 % of the flight the
 magnet lifts and glides in an arc, slowing as it nears; in the last 20 pt the door's pull takes it
 and it accelerates into place, then bounces. `u` is seconds since the flight began.
*/
export function snapPose(from: Pose, to: Pose, u: number, dur: number, out: Pose) {
  'worklet';
  const tau = u / dur;
  if (tau <= 0) {
    out.x = from.x; out.y = from.y; out.rot = from.rot; out.lift = from.lift; out.scale = from.scale;
    return out;
  }
  if (tau >= 1) {
    const b = contactBounce(u - dur);
    out.x = to.x; out.y = to.y + (1 - b) * 20; out.rot = to.rot; out.lift = 0; out.scale = b;
    return out;
  }
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const dist = Math.max(1, Math.sqrt(dx * dx + dy * dy));
  const pull = Math.min(20, dist * 0.3) / dist;
  const kA = 0.8;
  let s;
  if (tau < kA) {
    const x = tau / kA;
    // Mostly an ease-in-out, kept 30 % linear so it is still moving when the pull takes over.
    s = (1 - pull) * (0.7 * x * x * (3 - 2 * x) + 0.3 * x);
  } else {
    const q = (tau - kA) / (1 - kA);
    const vA = ((1 - pull) * 0.3) / kA;
    const v0 = clamp01((vA * (1 - kA)) / Math.max(pull, 0.0001));
    s = 1 - pull + pull * (v0 * q + (1 - v0) * q * q);
  }
  const arc = Math.min(64, dist * 0.28);
  const lift = tau < 0.2 ? easeOutCubic(tau / 0.2) : tau < kA ? 1 : 1 - (tau - kA) / (1 - kA);
  const rot = shortAngle(from.rot) * (1 - easeOutCubic(tau / 0.85)) + to.rot * easeOutCubic(tau / 0.85);
  out.x = from.x + dx * s;
  out.y = from.y + dy * s - arc * Math.sin(Math.PI * s);
  out.rot = rot;
  out.lift = Math.max(lift, from.lift * (1 - tau));
  out.scale = 1 + 0.06 * out.lift;
  return out;
}

/**
 Popping off: the magnet presses in (60 ms), jumps free of the line, flies back in an arc
 (ease-in-out) and lands on the pile with the same bounce.
*/
export function landPose(from: Pose, to: Pose, u: number, dur: number, out: Pose) {
  'worklet';
  const tau = u / dur;
  if (tau <= 0) {
    out.x = from.x; out.y = from.y; out.rot = from.rot; out.lift = from.lift; out.scale = from.scale;
    return out;
  }
  if (tau >= 1) {
    const b = contactBounce(u - dur);
    out.x = to.x; out.y = to.y + (1 - b) * 20; out.rot = to.rot; out.lift = 0; out.scale = b;
    return out;
  }
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const dist = Math.sqrt(dx * dx + dy * dy);
  const press = 0.13;
  if (tau < press) {
    const k = Math.sin((Math.PI * tau) / press);
    out.x = from.x; out.y = from.y + k * 1.5; out.rot = from.rot; out.lift = from.lift;
    out.scale = 1 - 0.03 * k;
    return out;
  }
  const x = (tau - press) / (1 - press);
  const s = easeInOutCubic(x);
  const arc = Math.min(70, 26 + dist * 0.22);
  const lift = x < 0.25 ? easeOutCubic(x / 0.25) : x < 0.8 ? 1 : 1 - (x - 0.8) / 0.2;
  out.x = from.x + dx * s;
  out.y = from.y + dy * s - arc * Math.sin(Math.PI * Math.min(1, s * 1.08));
  out.rot = from.rot + (to.rot - from.rot) * s + Math.sin(Math.PI * s) * 0.35;
  out.lift = Math.max(lift, from.lift * (1 - x));
  out.scale = 1 + 0.07 * out.lift;
  return out;
}

/** Let go away from the line: the magnet falls back onto the steel (accelerating, like a drop) and bounces. */
export function dropPose(from: Pose, u: number, out: Pose) {
  'worklet';
  const tau = u / DROP;
  out.x = from.x; out.rot = from.rot;
  if (tau >= 1) {
    const b = contactBounce(u - DROP);
    out.y = from.y + (1 - b) * 20; out.lift = 0; out.scale = b;
  } else {
    out.y = from.y;
    out.lift = from.lift * (1 - tau * tau);
    out.scale = 1 + 0.06 * out.lift;
  }
  return out;
}

/** Where script letter k is in the loop: 0 on the pile, 1 flying in / on the line, 2 popping off. */
export function autoPhase(k: number, t: number) {
  'worklet';
  const typeAt = TYPE_START + TYPE_STAGGER * k;
  const popAt = POP_START + POP_STAGGER * (SCRIPT.length - 1 - k);
  if (t < typeAt) return { phase: 0, since: 0 };
  if (t < popAt) return { phase: 1, since: t - typeAt };
  return { phase: 2, since: t - popAt };
}

/** "Continue" in the loop: lit from 3.6 s to the first backspace. */
export function autoReady(t: number) {
  'worklet';
  return easeOutCubic((t - READY_AT) / 0.25) * (1 - easeOutCubic((t - POP_START) / 0.2));
}

/** How many letters the loop's line holds at t (counted when each lands). */
export function autoCount(t: number) {
  'worklet';
  let n = 0;
  for (let k = 0; k < SCRIPT.length; k++) {
    const p = autoPhase(k, t);
    if (p.phase === 1 && p.since >= FLY) n += 1;
  }
  return n;
}
