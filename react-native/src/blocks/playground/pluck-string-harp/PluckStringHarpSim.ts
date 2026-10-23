/**
 * The harp's physics, kept free of React and Skia so it ports step for step to SwiftUI and
 * Compose. Every function is a worklet that mutates one plain `HarpSim` object; the component
 * calls `stepHarp` once a frame and draws what it finds.
 *
 * Each string is three standing waves (harmonics 1, 2, 3), each a damped oscillator. A finger
 * that crosses a string catches it and drags it sideways into a triangle; past `SLIP` the string
 * slips free and the triangle is handed to the harmonics, which ring and die away. Neighbours are
 * driven by each other's fundamental, so a plucked string sets the strings beside it shimmering.
 */

export const STRINGS = 8;
export const MODES = 3;
export const NOTE_NAMES = ['C', 'D', 'E', 'F', 'G', 'A', 'B', 'C'];
// Semitones above the lowest string: a C major scale.
const SEMITONES = [0, 2, 4, 5, 7, 9, 11, 12];

// Visible fundamental of the lowest string (Hz). Real strings move too fast to see; this keeps
// the wave readable at 60 fps while the scale still doubles across the octave.
const BASE_HZ = 5.2;
// Decay rate of the fundamental (1/s); higher harmonics die faster by HARMONIC_DAMP per step.
const DECAY = 1.3;
const HARMONIC_DAMP = 0.9;
// How hard a string's fundamental drives its neighbours (one and two strings away).
const SYMPATHY = 0.022;
const SYMPATHY_FAR = 0.4;

// Catching: a finger crossing a string drags it up to SLIP pt sideways before it slips free.
export const SLIP = 18;
// A touch that lands this close to a string catches it straight away.
const TOUCH_CATCH = 11;
// Letting go of a string held less than this far still plucks it this hard.
const TAP_PULL = 9;
// The catch point stays this far from either end of the string (share of its length).
const END_GUARD = 0.07;

// Glow: brightens with amplitude, then fades over HEAT_TAU seconds after the string quiets.
const HEAT_TAU = 0.55;
const HEAT_GAIN = 1.25;

// Note names that float up from each pluck.
export const NOTE_POOL = 12;
export const NOTE_LIFE = 1.7;
// The finger's light trail: positions kept for TRAIL_LIFE seconds.
export const TRAIL = 18;
export const TRAIL_LIFE = 0.26;

// Clock: one capped step drives both the physics and the autoPlay script.
const DT_CAP = 1 / 30;
const SUBSTEPS = 8;

// autoPlay: an arpeggio up and back, a strum down, a strum up, one low note, then rest.
export const LOOP = 9.5;
// After a touch the loop waits this long, then starts over.
const USER_HOLD = 3;

/** Where the strings are on screen. Strings are vertical: `x`, and their two ends. */
export type HarpGeometry = {
  x: number[];
  top: number[];
  bottom: number[];
};

export type HarpSim = {
  time: number;
  clock: number;
  hold: number;
  looping: boolean;
  still: boolean;
  // The finger (real or scripted) and where it was last frame.
  down: boolean;
  scripted: boolean;
  justDown: boolean;
  fx: number;
  fy: number;
  px: number;
  py: number;
  // Harmonic displacement (pt) and speed per string: index string * MODES + mode.
  q: number[];
  qv: number[];
  heat: number[];
  // A caught string: side it is pulled to (+1 right, -1 left, 0 free), where, and how far.
  held: number[];
  heldAt: number[];
  pull: number[];
  // Floating note names.
  noteString: number[];
  noteX: number[];
  noteY: number[];
  noteAge: number[];
  nextNote: number;
  // Finger trail, a ring of positions and when they were seen.
  trailX: number[];
  trailY: number[];
  trailT: number[];
  trailHead: number;
  // Counts plucks made by a real finger; the component sends a haptic when it grows.
  userPlucks: number;
};

function filled(n: number, v: number) {
  'worklet';
  const a: number[] = [];
  for (let i = 0; i < n; i += 1) a.push(v);
  return a;
}

export function makeHarpSim(looping: boolean, still: boolean): HarpSim {
  'worklet';
  return {
    time: 0,
    clock: 0,
    hold: 0,
    looping,
    still,
    down: false,
    scripted: false,
    justDown: false,
    fx: 0,
    fy: 0,
    px: 0,
    py: 0,
    q: filled(STRINGS * MODES, 0),
    qv: filled(STRINGS * MODES, 0),
    heat: filled(STRINGS, 0),
    held: filled(STRINGS, 0),
    heldAt: filled(STRINGS, 0.5),
    pull: filled(STRINGS, 0),
    noteString: filled(NOTE_POOL, 0),
    noteX: filled(NOTE_POOL, 0),
    noteY: filled(NOTE_POOL, 0),
    noteAge: filled(NOTE_POOL, -1),
    nextNote: 0,
    trailX: filled(TRAIL, 0),
    trailY: filled(TRAIL, 0),
    trailT: filled(TRAIL, -10),
    trailHead: 0,
    userPlucks: 0,
  };
}

/** Angular frequency of a string's fundamental. */
export function omega(string: number) {
  'worklet';
  return 2 * Math.PI * BASE_HZ * Math.pow(2, SEMITONES[string] / 12);
}

function clamp(v: number, lo: number, hi: number) {
  'worklet';
  return v < lo ? lo : v > hi ? hi : v;
}

/**
 * Displacement of string `i` at `s` (0 at the top, 1 at the bottom), in pt. A caught string is a
 * sharp triangle through the finger; a free one is the sum of its harmonics.
 */
export function displacement(sim: HarpSim, i: number, s: number) {
  'worklet';
  if (sim.held[i] !== 0) {
    const at = sim.heldAt[i];
    return s <= at ? (sim.pull[i] * s) / at : (sim.pull[i] * (1 - s)) / (1 - at);
  }
  let d = 0;
  for (let k = 0; k < MODES; k += 1) d += sim.q[i * MODES + k] * Math.sin((k + 1) * Math.PI * s);
  return sim.still ? 0 : d;
}

/** How far string `i` swings at most at `s`: the outline of its vibration, in pt. */
export function envelope(sim: HarpSim, i: number, s: number) {
  'worklet';
  if (sim.held[i] !== 0 || sim.still) return 0;
  const w = omega(i);
  let e = 0;
  for (let k = 0; k < MODES; k += 1) {
    const j = i * MODES + k;
    const wk = w * (k + 1);
    const a = Math.sqrt(sim.q[j] * sim.q[j] + (sim.qv[j] / wk) * (sim.qv[j] / wk));
    e += a * Math.abs(Math.sin((k + 1) * Math.PI * s));
  }
  return e;
}

/**
 * Hands a triangle of height `pull` peaking at `at` to the harmonics. The plucked-string series
 * b_k = 2·pull·sin(kπ·at) / (k²π²·at·(1−at)) is cut at three terms, then scaled so the curve
 * still passes through the finger, so nothing jumps on release.
 */
function release(sim: HarpSim, g: HarpGeometry, i: number, pull: number, sounded: boolean, user: boolean) {
  'worklet';
  const at = sim.heldAt[i];
  sim.held[i] = 0;
  sim.pull[i] = 0;
  let sum = 0;
  const b: number[] = [0, 0, 0];
  for (let k = 0; k < MODES; k += 1) {
    const n = k + 1;
    b[k] = (2 * Math.sin(n * Math.PI * at)) / (n * n * Math.PI * Math.PI * at * (1 - at));
    sum += b[k] * Math.sin(n * Math.PI * at);
  }
  const scale = Math.abs(sum) > 1e-4 ? pull / sum : 0;
  for (let k = 0; k < MODES; k += 1) {
    sim.q[i * MODES + k] = b[k] * scale;
    sim.qv[i * MODES + k] = 0;
  }
  if (!sounded) return;
  sim.heat[i] = Math.max(sim.heat[i], Math.min(1, (Math.abs(pull) / SLIP) * HEAT_GAIN));
  // A note name rises from where the string was plucked.
  const n = sim.nextNote;
  sim.nextNote = (n + 1) % NOTE_POOL;
  sim.noteString[n] = i;
  sim.noteX[n] = g.x[i];
  sim.noteY[n] = g.top[i] + (g.bottom[i] - g.top[i]) * at;
  sim.noteAge[n] = 0;
  if (user) sim.userPlucks += 1;
}

/**
 * Where the finger is along string `i`, as a share of its length, kept off the ends. A string with
 * no length yet (before layout, or after a relayout mid-catch) is caught in the middle, never NaN.
 */
function catchAt(sim: HarpSim, g: HarpGeometry, i: number) {
  'worklet';
  const length = g.bottom[i] - g.top[i];
  if (!(length > 1)) return 0.5;
  const at = (sim.fy - g.top[i]) / length;
  return Number.isFinite(at) ? clamp(at, END_GUARD, 1 - END_GUARD) : 0.5;
}

/** Catches string `i` at the finger, pulled to `side`. */
function grab(sim: HarpSim, g: HarpGeometry, i: number, side: number) {
  'worklet';
  sim.held[i] = side;
  sim.heldAt[i] = catchAt(sim, g, i);
  sim.pull[i] = sim.fx - g.x[i];
  for (let k = 0; k < MODES; k += 1) {
    sim.q[i * MODES + k] = 0;
    sim.qv[i * MODES + k] = 0;
  }
}

/** Catches, drags and releases strings as the finger moves across them. */
function touchStrings(sim: HarpSim, g: HarpGeometry) {
  'worklet';
  const user = !sim.scripted;
  for (let i = 0; i < STRINGS; i += 1) {
    const x = g.x[i];
    if (sim.held[i] !== 0) {
      const side = sim.held[i];
      if (!sim.down) {
        // Let go: a string barely touched still sounds.
        const pull = Math.abs(sim.pull[i]) < TAP_PULL ? side * TAP_PULL : sim.pull[i];
        release(sim, g, i, pull, true, user);
        continue;
      }
      const pull = sim.fx - x;
      if (pull * side < -2) {
        // The finger went back across the string: it is let go without a sound.
        release(sim, g, i, 0, false, user);
      } else if (Math.abs(pull) >= SLIP) {
        release(sim, g, i, side * SLIP, true, user);
      } else {
        sim.pull[i] = pull;
        sim.heldAt[i] = catchAt(sim, g, i);
      }
      continue;
    }
    if (!sim.down) continue;
    // Is the finger on this string's length (a little past either end counts)?
    const within = sim.fy > g.top[i] + 6 && sim.fy < g.bottom[i] - 6;
    if (!within) continue;
    if (sim.justDown) {
      if (Math.abs(sim.fx - x) < TOUCH_CATCH) grab(sim, g, i, sim.fx >= x ? 1 : -1);
      continue;
    }
    const before = sim.px - x;
    const now = sim.fx - x;
    if ((before < 0 && now >= 0) || (before > 0 && now <= 0)) {
      grab(sim, g, i, now >= before ? 1 : -1);
      // A fast finger has already passed the slip point.
      if (Math.abs(sim.pull[i]) >= SLIP) release(sim, g, i, sim.held[i] * SLIP, true, user);
    }
  }
  sim.justDown = false;
}

/** Integrates every free string's harmonics over `dt`, with fixed substeps. */
function ring(sim: HarpSim, dt: number) {
  'worklet';
  const h = dt / SUBSTEPS;
  for (let step = 0; step < SUBSTEPS; step += 1) {
    for (let i = 0; i < STRINGS; i += 1) {
      if (sim.held[i] !== 0) continue;
      const w = omega(i);
      // Sympathy: the neighbours' fundamentals pull on this one's.
      let drive = 0;
      for (let d = -2; d <= 2; d += 1) {
        const j = i + d;
        if (d === 0 || j < 0 || j >= STRINGS) continue;
        drive += sim.q[j * MODES] * (Math.abs(d) === 1 ? 1 : SYMPATHY_FAR);
      }
      for (let k = 0; k < MODES; k += 1) {
        const n = i * MODES + k;
        const wk = w * (k + 1);
        const gamma = DECAY * (1 + HARMONIC_DAMP * k);
        const target = k === 0 ? SYMPATHY * drive : 0;
        const accel = -wk * wk * (sim.q[n] - target) - 2 * gamma * sim.qv[n];
        // Semi-implicit Euler: stable for these frequencies at this step.
        sim.qv[n] += accel * h;
        sim.q[n] += sim.qv[n] * h;
      }
    }
  }
}

/** A point on the scripted finger's path: x in string spacings from the first string, y as a share of the string's length. */
function scriptPoint(g: HarpGeometry, index: number, along: number) {
  'worklet';
  const spacing = (g.x[STRINGS - 1] - g.x[0]) / (STRINGS - 1);
  const k = clamp(Math.floor(index), 0, STRINGS - 2);
  const f = clamp(index - k, 0, 1);
  const top = g.top[k] + (g.top[k + 1] - g.top[k]) * f;
  const bottom = g.bottom[k] + (g.bottom[k + 1] - g.bottom[k]) * f;
  return { x: g.x[0] + index * spacing, y: top + (bottom - top) * along };
}

// Scripted strokes: [start (s), duration (s), from index, to index, from along, to along].
const SCRIPT: number[][] = [
  // Arpeggio: C, E, G, C, G, E, each a short pluck to the right.
  [0.5, 0.22, -0.4, 0.75, 0.52, 0.55],
  [0.95, 0.22, 1.6, 2.75, 0.5, 0.53],
  [1.4, 0.22, 3.6, 4.75, 0.5, 0.53],
  [1.85, 0.24, 6.6, 7.8, 0.48, 0.52],
  [2.4, 0.22, 3.6, 4.75, 0.55, 0.58],
  [2.85, 0.22, 1.6, 2.75, 0.56, 0.59],
  // A strum down across all eight, then back up.
  [3.7, 0.42, -0.6, 7.7, 0.42, 0.56],
  [4.75, 0.46, 7.7, -0.6, 0.6, 0.46],
  // One low C to ring out the loop.
  [5.9, 0.26, -0.45, 0.8, 0.62, 0.64],
];

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
}

/** Puts the scripted finger where the script says at `clock`, or lifts it. */
function playScript(sim: HarpSim, g: HarpGeometry) {
  'worklet';
  for (let n = 0; n < SCRIPT.length; n += 1) {
    const [start, duration, i0, i1, a0, a1] = SCRIPT[n];
    const t = (sim.clock - start) / duration;
    if (t < 0 || t > 1) continue;
    const e = easeInOut(t);
    const p = scriptPoint(g, i0 + (i1 - i0) * e, a0 + (a1 - a0) * e);
    if (!sim.down) {
      sim.down = true;
      sim.justDown = true;
      sim.scripted = true;
      sim.px = p.x;
      sim.py = p.y;
    }
    sim.fx = p.x;
    sim.fy = p.y;
    return;
  }
  if (sim.down && sim.scripted) sim.down = false;
}

/** A real finger touched down, moved or lifted. */
export function fingerDown(sim: HarpSim, x: number, y: number) {
  'worklet';
  sim.down = true;
  sim.justDown = true;
  sim.scripted = false;
  sim.fx = x;
  sim.fy = y;
  sim.px = x;
  sim.py = y;
  sim.hold = USER_HOLD;
}

export function fingerMove(sim: HarpSim, x: number, y: number) {
  'worklet';
  if (!sim.down || sim.scripted) return;
  sim.fx = x;
  sim.fy = y;
  sim.hold = USER_HOLD;
}

export function fingerUp(sim: HarpSim) {
  'worklet';
  if (sim.scripted) return;
  sim.down = false;
  sim.hold = USER_HOLD;
}

/** One frame: the script, the strings under the finger, the waves, the glows, the notes. */
export function stepHarp(sim: HarpSim, g: HarpGeometry, frameDt: number) {
  'worklet';
  const dt = Math.min(Math.max(frameDt, 0), DT_CAP);
  if (dt <= 0) return;
  sim.time += dt;

  if (sim.hold > 0) {
    // A real touch pauses the loop; it starts over once the hand has been away a while.
    if (!sim.down) sim.hold -= dt;
    if (sim.hold <= 0) sim.clock = 0;
  } else if (sim.looping) {
    sim.clock += dt;
    if (sim.clock >= LOOP) sim.clock -= LOOP;
    playScript(sim, g);
  }

  touchStrings(sim, g);
  ring(sim, dt);

  for (let i = 0; i < STRINGS; i += 1) {
    const level = sim.held[i] !== 0 ? Math.abs(sim.pull[i]) / SLIP : envelope(sim, i, 0.5) / SLIP;
    const fade = sim.heat[i] * Math.exp(-dt / HEAT_TAU);
    sim.heat[i] = Math.max(fade, Math.min(1, level * HEAT_GAIN));
  }

  for (let n = 0; n < NOTE_POOL; n += 1) {
    if (sim.noteAge[n] < 0) continue;
    sim.noteAge[n] += dt;
    if (sim.noteAge[n] > NOTE_LIFE) sim.noteAge[n] = -1;
  }

  if (sim.down) {
    const h = (sim.trailHead + 1) % TRAIL;
    sim.trailHead = h;
    sim.trailX[h] = sim.fx;
    sim.trailY[h] = sim.fy;
    sim.trailT[h] = sim.time;
  }
  sim.px = sim.fx;
  sim.py = sim.fy;
}
