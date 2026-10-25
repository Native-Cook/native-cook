/*
 Music Box Player — the motion. The cylinder's angle is the only clock: pins ride on it, a pin
 that passes the comb's tips plucks its tooth (the tip bends back as the pin nears, then rings,
 decaying), and each pluck lets a note float up. The crank turns 4 times per turn of the cylinder,
 the governor's fan 40 times.

 autoPlay (LOOP = 8 s):
   0.0 – 1.2   rest: pins at the start of the song, playhead at 0:00.
   1.2 – 6.4   the crank turns: speeds up in 0.4 s, steady, then slows to a stop over the last 1 s
               (the cylinder makes exactly one turn, so the song plays once: 8 bars of 3/4).
   6.15        the last chord; it rings until 7.0.
   7.0 – 7.6   the playhead rolls back to 0:00. 8.0 is the same frame as 0.0.
*/

export const LOOP = 8;
export const TEETH = 18;
/** Beats in one turn of the cylinder. */
const SLOTS = 24.5;
/** Pin angle where the comb's tips are (0 faces the viewer, +90° is the bottom silhouette). */
export const CONTACT = (55 * Math.PI) / 180;
export const CRANK_RATIO = 4;
export const SONG_SECONDS = 52;
const PUSH = 0.2;

/** Beat and tooth of every pin. */
function makePins() {
  const melody = [9, 11, 13, 14, 13, 11, 12, 10, 9, 11, 9, 8, 9, 11, 13, 16, 14, 13, 12, 11, 10, 9];
  const bass = [2, 4, 0, 3, 2, 5, 1, 2];
  const pins: number[] = [];
  for (let b = 0; b < melody.length; b += 1) pins.push(b, melody[b]);
  for (let k = 0; k < bass.length; k += 1) pins.push(k * 3, bass[k]);
  // The final chord, late in the turn, while the crank slows.
  pins.push(23, 9, 23, 14, 23, 2);
  return pins;
}
export const PINS = makePins();
export const PIN_COUNT = PINS.length / 2;

/** A pin's angle on the cylinder at rest (the cylinder at angle 0). */
export function pinAngle(p: number) {
  'worklet';
  return CONTACT - (2 * Math.PI * (PINS[p * 2] + 0.5)) / SLOTS;
}

function smooth(x: number) {
  const k = Math.min(1, Math.max(0, x));
  return k * k * (3 - 2 * k);
}

/** The crank's share of one turn done at loop time t, tabulated (0.01 s). */
function makeTurns() {
  const n = 801;
  const v: number[] = [];
  for (let i = 0; i < n; i += 1) {
    const t = (i / (n - 1)) * LOOP;
    v.push(smooth((t - 1.2) / 0.4) * (1 - smooth((t - 5.4) / 1.0)));
  }
  const s: number[] = [0];
  for (let i = 1; i < n; i += 1) s.push(s[i - 1] + (v[i] + v[i - 1]) / 2);
  const total = s[n - 1];
  return s.map((x) => x / total);
}
const TURNS = makeTurns();

export function turnsAt(t: number) {
  'worklet';
  const f = (Math.min(LOOP, Math.max(0, t)) / LOOP) * (TURNS.length - 1);
  const i = Math.min(TURNS.length - 2, Math.floor(f));
  return TURNS[i] + (TURNS[i + 1] - TURNS[i]) * (f - i);
}

/** When each pin plucks in the loop (the inverse of turnsAt). */
function makePlucks() {
  const out: number[] = [];
  for (let p = 0; p < PINS.length / 2; p += 1) {
    const want = (PINS[p * 2] + 0.5) / SLOTS;
    let i = 0;
    while (i < TURNS.length - 1 && TURNS[i + 1] < want) i += 1;
    const f = TURNS[i + 1] - TURNS[i] > 1e-9 ? (want - TURNS[i]) / (TURNS[i + 1] - TURNS[i]) : 0;
    out.push(((i + f) / (TURNS.length - 1)) * LOOP);
  }
  return out;
}
export const PLUCKS = makePlucks();

export type Geo = {
  w: number;
  h: number;
  /** Cylinder: left, right, centre y, radius. Comb tips y, base y. Crank hub, arm. */
  cylX0: number;
  cylX1: number;
  cylY: number;
  cylR: number;
  tipY: number;
  combY: number;
  hubX: number;
  hubY: number;
  arm: number;
  fanX: number;
  fanY: number;
  barX: number;
  barY: number;
  barW: number;
};

export function toothX(g: Geo, j: number) {
  'worklet';
  const m = 12;
  return g.cylX0 + m + ((g.cylX1 - g.cylX0 - m * 2) * (j + 0.5)) / TEETH;
}

export type Pose = {
  /** Cylinder angle (rad), crank angle, fan angle. */
  angle: number;
  crank: number;
  fan: number;
  /** Per tooth: tip offset (pt), glow 0..1. */
  tips: number[];
  glow: number[];
  /** Notes: x, y, scale, alpha, tilt. */
  notes: number[];
  progress: number;
};

function seg01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
  return s - Math.floor(s);
}

/** How far (rad) pin p still has to go to reach the comb at cylinder angle a; in 0..2π. */
function toGo(p: number, a: number) {
  'worklet';
  const d = CONTACT - (pinAngle(p) + a);
  const k = 2 * Math.PI;
  return ((d % k) + k) % k;
}

/** Tooth j rings e seconds after its pluck. */
export function ring(j: number, e: number) {
  'worklet';
  if (e < 0 || e > 1.8) return 0;
  const f = 7 + (TEETH - j) * 0.35;
  return 2.6 * Math.exp(-e / 0.42) * Math.cos(2 * Math.PI * f * e);
}

/** One floating note, e seconds after the pluck of a tooth at x. */
function pushNote(out: number[], g: Geo, x: number, e: number, seed: number) {
  'worklet';
  if (e < 0 || e > 1.7) return;
  const y = g.tipY - 14 - 118 * e + 8 * e * e;
  const sway = 10 * Math.sin(2 * Math.PI * (0.7 * e + hash(seed))) * Math.min(1, e * 2);
  const a = Math.min(1, e / 0.12) * (1 - Math.max(0, (e - 1.1) / 0.6));
  out.push(x + sway + (hash(seed + 3) - 0.5) * 30 * e, y, 0.75 + 0.35 * Math.min(1, e / 0.4), a, (hash(seed + 5) - 0.5) * 30);
}

/**
 * A frame from the cylinder angle `a` (rad, unwrapped), the crank angle, the time and each tooth's
 * last pluck (`last[j]`, -1 for none; `notes` is the recent plucks as tooth, time pairs).
 */
export function poseFrom(g: Geo, a: number, crank: number, now: number, last: number[], recent: number[], progress: number): Pose {
  'worklet';
  const tips: number[] = [];
  const glow: number[] = [];
  for (let j = 0; j < TEETH; j += 1) {
    const e = last[j] >= 0 ? now - last[j] : -1;
    let off = ring(j, e);
    // A pin closing in pushes the tip away from the cylinder before it lets go.
    for (let p = 0; p < PIN_COUNT; p += 1) {
      if (PINS[p * 2 + 1] !== j) continue;
      const d = toGo(p, a);
      if (d < PUSH) off += 3 * (1 - d / PUSH);
    }
    tips.push(off);
    glow.push(e >= 0 && e < 1.5 ? Math.exp(-e / 0.5) : 0);
  }
  const notes: number[] = [];
  for (let i = 0; i < recent.length; i += 2) pushNote(notes, g, toothX(g, recent[i]), now - recent[i + 1], recent[i] * 7 + recent[i + 1] * 3.1);
  return { angle: a, crank, fan: a * 40, tips, glow, notes, progress };
}

export function loopPose(g: Geo, t: number): Pose {
  'worklet';
  const turns = turnsAt(t);
  const a = turns * 2 * Math.PI;
  const last: number[] = [];
  for (let j = 0; j < TEETH; j += 1) last.push(-1);
  const recent: number[] = [];
  for (let p = 0; p < PIN_COUNT; p += 1) {
    const tp = PLUCKS[p];
    if (tp <= t) {
      const j = PINS[p * 2 + 1];
      if (tp > last[j]) last[j] = tp;
      if (t - tp < 1.7) recent.push(j, tp);
    }
  }
  const back = seg01((t - 7.0) / 0.6);
  const progress = turns * (1 - back * back * (3 - 2 * back));
  return poseFrom(g, a, a * CRANK_RATIO, t, last, recent, progress);
}

/** Pins that pass the comb between cylinder angles a0 and a1 (a1 > a0): their indices. */
export function passed(a0: number, a1: number, out: number[]) {
  'worklet';
  for (let p = 0; p < PIN_COUNT; p += 1) {
    const d = toGo(p, a0);
    if (d > 0 && d <= a1 - a0) out.push(p);
  }
}
