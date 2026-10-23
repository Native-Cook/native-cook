// The ferrofluid's magnets: up to two of them, each following a finger (or the autoPlay script)
// and carrying the springs the shader reads: how far the fluid has heaped up toward it, how tall
// its crown of spikes stands, and which way the spikes lean. Plain arithmetic on a plain object,
// stepped in fixed substeps on the UI thread, so it ports to Swift and Kotlin line for line.
//
// Units: points and seconds. Positions are on the table ("ground"), not on the screen: the dish
// is seen from above at an angle, so a screen point maps to the ground through the camera in
// FerrofluidBlob.tsx. The dish's centre is the ground origin.

/** How many magnets (fingers) can raise a crown at once. */
export const MAGNETS = 2;

// Fixed substep and the most frame time one frame may consume (a long hitch is cut short).
const SUBSTEP = 1 / 120;
export const MAX_FRAME = 1 / 30;

// The magnet glides after its finger on a nearly critical spring (rad/s, damping ratio), so the
// crown trails a fast swipe a little and catches up without overshoot.
const FOLLOW_W = 15;
const FOLLOW_Z = 0.86;
// Field: how far the fluid heaps up under the magnet (0…1). It rises over ~0.3 s.
const FIELD_W = 8;
const FIELD_Z = 0.62;
// Spikes: their height (0…1, a little more during a pulse). Growing is well damped; once the
// field is gone they melt back underdamped, so they wobble and briefly dimple below the surface.
const SPIKE_W = 11;
const SPIKE_RISE_Z = 0.5;
const SPIKE_FALL_Z = 0.2;
const SPIKE_MIN = -0.35;
const SPIKE_MAX = 1.7;
// The spikes lean against the magnet's motion, as if dragged through the fluid: LEAN_PER_SPEED pt
// of tip offset per pt/s, at most LEAN_MAX pt, eased at LEAN_RATE per second.
const LEAN_PER_SPEED = 0.03;
const LEAN_MAX = 9;
const LEAN_RATE = 12;
// A tap pulses the magnet: the spikes get a kick of PULSE spike-heights per second and the field
// a smaller one, and the magnet stays on for TAP_LINGER s so the flinch can be seen.
const PULSE_SPIKE = 7.5;
const PULSE_FIELD = 2.2;
const TAP_LINGER = 0.42;
// A touch shorter than TAP_TIME s that moves less than TAP_SLOP pt is a tap.
const TAP_TIME = 0.24;
const TAP_SLOP = 12;

// Events for haptics, read and cleared by the component each frame.
export const EVENT_RISE = 1;
export const EVENT_PULSE = 2;

export type Magnet = {
  /** Touch id holding it, or -1. Scripted fingers use ids from SCRIPT_ID up. */
  touch: number;
  scripted: boolean;
  /** Where its finger is, and how strong it wants to be (0…1). */
  tx: number;
  ty: number;
  power: number;
  /** Seconds it stays on after its finger lifts (after a tap). */
  linger: number;
  downAt: number;
  travel: number;
  lastX: number;
  lastY: number;
  /** The magnet itself, gliding after the finger. */
  x: number;
  y: number;
  vx: number;
  vy: number;
  field: number;
  fieldV: number;
  spike: number;
  spikeV: number;
  leanX: number;
  leanY: number;
};

export type FerroSim = {
  m: Magnet[];
  time: number;
  reduced: boolean;
  events: number;
  eventsByUser: boolean;
};

// Negative, so it can never meet a real touch id (Gesture Handler's count up from 0).
export const SCRIPT_ID = -1000;

function makeMagnet(): Magnet {
  'worklet';
  return {
    touch: -1,
    scripted: false,
    tx: 0,
    ty: 0,
    power: 0,
    linger: 0,
    downAt: 0,
    travel: 0,
    lastX: 0,
    lastY: 0,
    x: 0,
    y: 0,
    vx: 0,
    vy: 0,
    field: 0,
    fieldV: 0,
    spike: 0,
    spikeV: 0,
    leanX: 0,
    leanY: 0,
  };
}

export function createFerro(reduced: boolean): FerroSim {
  'worklet';
  const m: Magnet[] = [];
  for (let i = 0; i < MAGNETS; i += 1) m.push(makeMagnet());
  return { m, time: 0, reduced, events: 0, eventsByUser: false };
}

/** One step of a damped spring toward `target`: returns [value, velocity]. */
function springStep(value: number, velocity: number, target: number, w: number, z: number, h: number) {
  'worklet';
  const a = w * w * (target - value) - 2 * z * w * velocity;
  const v = velocity + a * h;
  return [value + v * h, v];
}

/** The magnet a new finger takes: a free one, preferring the one whose crown has melted most. */
function freeMagnet(s: FerroSim) {
  'worklet';
  let best = -1;
  let bestLeft = Infinity;
  for (let i = 0; i < s.m.length; i += 1) {
    const g = s.m[i];
    if (g.touch !== -1) continue;
    const left = Math.abs(g.field) + Math.abs(g.spike) + (g.linger > 0 ? 2 : 0);
    if (left < bestLeft) {
      bestLeft = left;
      best = i;
    }
  }
  return best;
}

export function ferroTouchDown(s: FerroSim, id: number, x: number, y: number, scripted: boolean, power: number) {
  'worklet';
  const i = freeMagnet(s);
  if (i < 0) return;
  const g = s.m[i];
  // A magnet whose crown has melted starts right under the finger instead of gliding over.
  if (Math.abs(g.field) + Math.abs(g.spike) < 0.08 && g.linger <= 0) {
    g.x = x;
    g.y = y;
    g.vx = 0;
    g.vy = 0;
    g.leanX = 0;
    g.leanY = 0;
  }
  g.touch = id;
  g.scripted = scripted;
  g.tx = x;
  g.ty = y;
  g.power = power;
  g.linger = 0;
  g.downAt = s.time;
  g.travel = 0;
  g.lastX = x;
  g.lastY = y;
  if (!scripted) {
    s.events |= EVENT_RISE;
    s.eventsByUser = true;
  }
}

export function ferroTouchMove(s: FerroSim, id: number, x: number, y: number, power: number) {
  'worklet';
  for (let i = 0; i < s.m.length; i += 1) {
    const g = s.m[i];
    if (g.touch !== id) continue;
    g.travel += Math.hypot(x - g.lastX, y - g.lastY);
    g.lastX = x;
    g.lastY = y;
    g.tx = x;
    g.ty = y;
    g.power = power;
  }
}

/** Kicks one magnet's crown up: the flinch of a tap. */
function pulse(s: FerroSim, g: Magnet, byUser: boolean) {
  'worklet';
  g.spikeV += PULSE_SPIKE * (s.reduced ? 0.4 : 1);
  g.fieldV += PULSE_FIELD;
  g.linger = Math.max(g.linger, TAP_LINGER);
  s.events |= EVENT_PULSE;
  if (byUser) s.eventsByUser = true;
}

export function ferroTouchUp(s: FerroSim, id: number) {
  'worklet';
  for (let i = 0; i < s.m.length; i += 1) {
    const g = s.m[i];
    if (g.touch !== id) continue;
    const tap = s.time - g.downAt < TAP_TIME && g.travel < TAP_SLOP;
    g.touch = -1;
    if (tap) pulse(s, g, !g.scripted);
  }
}

/** A scripted tap on whichever magnet a scripted finger holds. */
export function ferroPulseScripted(s: FerroSim, id: number) {
  'worklet';
  for (let i = 0; i < s.m.length; i += 1) {
    const g = s.m[i];
    if (g.touch === id) pulse(s, g, false);
  }
}

/** A real finger takes over: the script's fingers let go where they are. */
export function ferroCancelScripted(s: FerroSim) {
  'worklet';
  for (let i = 0; i < s.m.length; i += 1) {
    if (s.m[i].scripted && s.m[i].touch !== -1) s.m[i].touch = -1;
  }
}

export function ferroStep(s: FerroSim, frame: number) {
  'worklet';
  const dt = Math.min(frame, MAX_FRAME);
  const n = Math.max(1, Math.ceil(dt / SUBSTEP - 1e-6));
  const h = dt / n;
  const calm = s.reduced;
  for (let k = 0; k < n; k += 1) {
    s.time += h;
    for (let i = 0; i < s.m.length; i += 1) {
      const g = s.m[i];
      if (g.linger > 0) g.linger = Math.max(0, g.linger - h);
      const on = g.touch !== -1 || g.linger > 0;
      const power = on ? g.power : 0;
      if (g.touch !== -1) {
        if (calm) {
          g.x = g.tx;
          g.y = g.ty;
          g.vx = 0;
          g.vy = 0;
        } else {
          const [nx, nvx] = springStep(g.x, g.vx, g.tx, FOLLOW_W, FOLLOW_Z, h);
          const [ny, nvy] = springStep(g.y, g.vy, g.ty, FOLLOW_W, FOLLOW_Z, h);
          g.x = nx;
          g.vx = nvx;
          g.y = ny;
          g.vy = nvy;
        }
      } else {
        // Let go, the magnet coasts to a stop where it was.
        const keep = Math.exp(-8 * h);
        g.vx *= keep;
        g.vy *= keep;
        g.x += g.vx * h;
        g.y += g.vy * h;
      }
      const [f, fv] = springStep(g.field, g.fieldV, power, FIELD_W, calm ? 1 : FIELD_Z, h);
      g.field = f;
      g.fieldV = fv;
      const z = calm ? 1 : power > g.spike ? SPIKE_RISE_Z : SPIKE_FALL_Z;
      const [sp, spv] = springStep(g.spike, g.spikeV, power, SPIKE_W, z, h);
      g.spike = Math.min(SPIKE_MAX, Math.max(SPIKE_MIN, sp));
      g.spikeV = sp === g.spike ? spv : 0;
      // Lean against the motion, eased.
      let lx = -g.vx * LEAN_PER_SPEED;
      let ly = -g.vy * LEAN_PER_SPEED;
      const len = Math.hypot(lx, ly);
      if (len > LEAN_MAX) {
        lx *= LEAN_MAX / len;
        ly *= LEAN_MAX / len;
      }
      const e = 1 - Math.exp(-LEAN_RATE * h);
      g.leanX += (lx - g.leanX) * e;
      g.leanY += (ly - g.leanY) * e;
    }
  }
}

// ---------------------------------------------------------------------------------------------
// autoPlay. Two scripted fingers, placed in units of the dish's radius from its centre (ground
// coordinates; negative y is the far side). A key holds the finger's place from its time on and
// eases (smoothstep) into the next key; `on` keys put the finger down, off keys lift it.

/** Seconds the script takes before it starts over. */
export const LOOP = 12;

type Key = { t: number; x: number; y: number; on: boolean; power: number };

/** The first magnet sweeps an arc over the far side, flinches, glides to the near right and lifts. */
const KEYS_A: Key[] = [
  { t: 0, x: -0.46, y: 0.06, on: false, power: 0 },
  { t: 0.9, x: -0.46, y: 0.06, on: true, power: 0.85 },
  { t: 1.6, x: -0.42, y: -0.08, on: true, power: 1 },
  { t: 2.5, x: -0.18, y: -0.3, on: true, power: 1 },
  { t: 3.4, x: 0.16, y: -0.3, on: true, power: 1 },
  { t: 4.3, x: 0.38, y: -0.06, on: true, power: 1 },
  { t: 5.6, x: 0.38, y: -0.06, on: true, power: 1 },
  { t: 6.8, x: 0.3, y: 0.22, on: true, power: 1 },
  { t: 8.4, x: 0.22, y: 0.16, on: true, power: 1 },
  { t: 9.6, x: 0.22, y: 0.16, on: false, power: 0 },
];
/** The second comes down on the left while the first is still there: two crowns. */
const KEYS_B: Key[] = [
  { t: 0, x: -0.34, y: 0.1, on: false, power: 0 },
  { t: 6.6, x: -0.34, y: 0.1, on: true, power: 0.9 },
  { t: 7.6, x: -0.3, y: -0.02, on: true, power: 1 },
  { t: 8.6, x: -0.22, y: 0.12, on: true, power: 1 },
  { t: 10.3, x: -0.22, y: 0.12, on: false, power: 0 },
];
/** Taps: [time, finger]. */
const PULSES: [number, number][] = [
  [5.0, 0],
  [8.9, 0],
  [9.1, 1],
];

function smooth01(p: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, p));
  return t * t * (3 - 2 * t);
}

/** Where a scripted finger is at time t: [x, y, on, power], x and y in dish radii. */
function keyAt(keys: Key[], t: number) {
  'worklet';
  let i = 0;
  while (i + 1 < keys.length && keys[i + 1].t <= t) i += 1;
  const a = keys[i];
  if (i + 1 >= keys.length) return [a.x, a.y, a.on ? 1 : 0, a.power];
  const b = keys[i + 1];
  const e = smooth01((t - a.t) / (b.t - a.t));
  return [a.x + (b.x - a.x) * e, a.y + (b.y - a.y) * e, a.on ? 1 : 0, a.power + (b.power - a.power) * e];
}

/** True when the loop's clock went past `at` between `before` and `now` (wrapping at LOOP). */
function passed(before: number, now: number, at: number) {
  'worklet';
  return now >= before ? before < at && now >= at : at > before || at <= now;
}

/**
 * Drives the scripted fingers from the loop's clock, which moved from `before` to `now`.
 * `radius` is the dish's radius in points.
 */
export function ferroScript(s: FerroSim, before: number, now: number, radius: number) {
  'worklet';
  const tracks = [KEYS_A, KEYS_B];
  for (let f = 0; f < tracks.length; f += 1) {
    const [x, y, on, power] = keyAt(tracks[f], now);
    const id = SCRIPT_ID + f;
    let held = false;
    for (let i = 0; i < s.m.length; i += 1) if (s.m[i].touch === id) held = true;
    const px = x * radius;
    const py = y * radius;
    if (on > 0.5 && !held) ferroTouchDown(s, id, px, py, true, power);
    else if (on > 0.5) ferroTouchMove(s, id, px, py, power);
    else if (held) {
      ferroTouchMove(s, id, px, py, power);
      // A scripted lift is never a tap; taps are listed in PULSES.
      for (let i = 0; i < s.m.length; i += 1) if (s.m[i].touch === id) s.m[i].touch = -1;
    }
  }
  for (let p = 0; p < PULSES.length; p += 1) {
    if (passed(before, now, PULSES[p][0])) ferroPulseScripted(s, SCRIPT_ID + PULSES[p][1]);
  }
}
