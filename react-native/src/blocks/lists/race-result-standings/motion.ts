/**
 * Race Result Standings — the timeline. Every animated value on screen is a pure function of a few
 * scalars (see `Frame`); in autoPlay those scalars are a pure function of the loop time t
 * (0 ≤ t < LOOP), so t = LOOP equals t = 0 and one period cut from a recording loops without a jump.
 *
 * autoPlay is the race replay (r = t − REPLAY_AT, seconds of the replay's own clock):
 *   t 0.00–0.80  rest = the pin (Race results)
 *   r 0.00–0.85  the circuit lifts out of its corner and flies to the middle of the header (×2.35, a
 *                10° tilt in flight); the header dims; the standings rewind to the grid order
 *   r 0.55–0.90  six team-coloured cars fade in on the grid; r 0.85–1.57 five red lights come on
 *   r 1.95       lights out: the cars launch and race two laps with comet trails
 *   r 2.55–5.30  six overtakes on the circuit; at each crossing the two rows swap in the standings
 *                (the overtaker lifts on its plate, leans, trails its team colour; a code tag pops on the map)
 *   r 5.90       the leader takes the flag (ring flash on the line); each row's points roll in as its car crosses
 *   r 6.15–7.10  the cars fade, the circuit flies home and the header lights up again (all still by 7.00);
 *                the replay clock stops at REPLAY.end = 7.10, i.e. t 7.90
 *   t 7.90–10.0  rest (with 0–0.80: 2.9 s, 29 % of the loop)
 *
 * The Race ⇄ Sprint switch behind the ⇅ title (and its comet lap) is touch only.
 *
 * Helpers are declared before the worklets that call them (a worklet calling one declared further
 * down is undefined on the UI thread). No callbacks inside worklets: index loops only.
 */

export const LOOP = 10;
/** After the last touch, wait this long, ease back to Race, then restart the loop at 0. */
export const RESUME_AFTER = 2000;

/** One switch, in seconds of its own clock τ = u · SWITCH. */
export const SWITCH = 1.7;
export const LAP = 1.3; // one lap of the comet (lap units 0 → 1)
export const LAP_END = 1.15; // the tail catches the head 0.15 lap after the line
/** The tail: five overlapping strokes that start further back and get thinner and fainter (lag in lap units, width in pt). */
export const TAIL = [
  { lag: 0.03, width: 2.6, alpha: 1 },
  { lag: 0.06, width: 2.1, alpha: 0.8 },
  { lag: 0.09, width: 1.6, alpha: 0.6 },
  { lag: 0.12, width: 1.15, alpha: 0.4 },
  { lag: 0.15, width: 0.7, alpha: 0.22 },
] as const;
export const GLOW_LAG = 0.08; // the blurred glow under the first part of the tail
export const FL_ROLL = 0.3; // fastest-lap text roll

export const PRESS = { down: 0.1, up: 0.12, scale: 0.97, chevronOpen: 1.5 } as const;

/** Per-element windows inside a switch (seconds of τ). */
export const WINDOWS = {
  word: { start: 0, dur: 0.3 },
  podium: { start: 0.06, step: 0.07, dur: 0.32 },
  points: { start: 0.3, step: 0.07, dur: 0.32 },
  row: { start: 0.08, step: 0.07, dur: 1.25 },
  badge: { from: 0.35, to: 0.65 }, // of the row's own progress
} as const;

/** A driver's travel: a closed-form damped spring, ω = 7.2 rad/s, ζ = 0.78 (≈ 2 % overshoot), settled in 1.25 s. */
export const ROW_SPRING = { omega: 7.2, zeta: 0.78 } as const;

export const ROW = {
  laneRise: 10, // pt: an overtaker swings out to the right…
  laneFall: -4, // …and the driver losing places gives way to the left
  leanMax: 1.6, // degrees
  leanSpeed: 600, // pt/s at which the lean saturates
  dimFall: 0.6, // a driver losing places dims while it is passed…
  sinkFall: 0.03, // …and sinks back (scale)
  liftRise: 0.025, // an overtaker lifts (scale) on a plate that hides what it passes
  trailPerSpeed: 0.05, // trail length (pt) per pt/s of speed
  trailAlphaSpeed: 500, // pt/s at full trail opacity
  trailAlpha: 0.85,
  roll: 0.62, // number roll distance, × font size
} as const;

/** Swipe scrub: a drag of this fraction of the width plays a whole switch. */
export const DRAG = {
  span: 0.6,
  commitAt: 0.5,
  commitVelocity: 500, // pt/s
  activeOffset: 14,
  failOffset: 12,
  rubber: 0.55,
  rubberFalloff: 120,
} as const;

/** Spring for letting go of an over-stretched drag (Reanimated duration + damping ratio). */
export const SETTLE = { duration: 400, dampingRatio: 0.8 } as const;
export const REDUCED_FADE = 100; // ms out, then in: 200 ms in all

export function clamp01(v: number) {
  'worklet';
  return v < 0 ? 0 : v > 1 ? 1 : v;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function lerp(a: number, b: number, p: number) {
  'worklet';
  return a + (b - a) * p;
}

/** cubic-bezier(x1, y1, x2, y2) at x = p: solve x by bisection, then sample y. */
function bezier(p: number, x1: number, y1: number, x2: number, y2: number) {
  'worklet';
  const x = clamp01(p);
  if (x === 0 || x === 1) return x;
  let lo = 0;
  let hi = 1;
  for (let i = 0; i < 18; i++) {
    const m = (lo + hi) / 2;
    const u = 1 - m;
    const at = 3 * u * u * m * x1 + 3 * u * m * m * x2 + m * m * m;
    if (at < x) lo = m;
    else hi = m;
  }
  const m = (lo + hi) / 2;
  const u = 1 - m;
  return 3 * u * u * m * y1 + 3 * u * m * m * y2 + m * m * m;
}

/** Strong ease-out, cubic-bezier(0.23, 1, 0.32, 1): entrances, rolls, wipes. */
export function easeOut(p: number) {
  'worklet';
  return bezier(p, 0.23, 1, 0.32, 1);
}

/** A damped spring from 0 to 1, `tau` seconds after it starts. */
export function spring(tau: number, s: { omega: number; zeta: number }) {
  'worklet';
  if (tau <= 0) return 0;
  const wd = s.omega * Math.sqrt(1 - s.zeta * s.zeta);
  const env = Math.exp(-s.zeta * s.omega * tau);
  return 1 - env * (Math.cos(wd * tau) + ((s.zeta * s.omega) / wd) * Math.sin(wd * tau));
}

/** A driver's progress (0 → 1, may pass 1) towards the slot it is heading for. */
export function rowProgress(u: number, dest: number) {
  'worklet';
  const local = (u * SWITCH - WINDOWS.row.start - WINDOWS.row.step * dest) / WINDOWS.row.dur;
  if (local <= 0) return 0;
  if (local >= 1) return 1;
  return spring(local * WINDOWS.row.dur, ROW_SPRING);
}

/** d(rowProgress)/dτ in 1/s, for the lean and the trail. */
export function rowSpeed(u: number, dest: number) {
  'worklet';
  const local = (u * SWITCH - WINDOWS.row.start - WINDOWS.row.step * dest) / WINDOWS.row.dur;
  if (local <= 0 || local >= 1) return 0;
  const tau = local * WINDOWS.row.dur;
  const h = 0.004;
  return (spring(tau + h, ROW_SPRING) - spring(Math.max(0, tau - h), ROW_SPRING)) / (tau + h - Math.max(0, tau - h));
}

function windowed(u: number, start: number, dur: number) {
  'worklet';
  return easeOut(seg(u * SWITCH, start, start + dur));
}

export function wordProgress(u: number) {
  'worklet';
  return windowed(u, WINDOWS.word.start, WINDOWS.word.dur);
}

export function podiumProgress(u: number, slot: number) {
  'worklet';
  return windowed(u, WINDOWS.podium.start + WINDOWS.podium.step * slot, WINDOWS.podium.dur);
}

export function pointsProgress(u: number, slot: number) {
  'worklet';
  return windowed(u, WINDOWS.points.start + WINDOWS.points.step * slot, WINDOWS.points.dur);
}

/** How far an element shows the Sprint state, given the switch's origin and its own progress. */
export function towards(from: number, p: number) {
  'worklet';
  return from === 0 ? p : 1 - p;
}

/** The comet's place along the lap: zero speed at the line, fastest down the back straight. */
export function lapPlace(lap: number) {
  'worklet';
  if (lap <= 0) return 0;
  if (lap >= 1) return 1;
  return lap - Math.sin(2 * Math.PI * lap) / (2 * Math.PI);
}

/** The finish-line flash: 0 → 1 over the tail-catch phase (lap 1 … LAP_END), 0 otherwise. */
export function lineFlash(lap: number) {
  'worklet';
  if (lap < 1 || lap >= LAP_END) return 0;
  return (lap - 1) / (LAP_END - 1);
}

export const FLASH = { from: 2, to: 13, stroke: 1.4 } as const; // ring radius (pt) and width

export function cometAlpha(lap: number) {
  'worklet';
  if (lap <= 0 || lap >= LAP_END) return 0;
  return clamp01(lap / 0.06) * (1 - clamp01((lap - 1) / (LAP_END - 1)));
}

/** d(spring)/dτ in 1/s, closed form: ω²/ω_d · e^(−ζωτ) · sin(ω_d τ). */
export function springVelocity(tau: number, s: { omega: number; zeta: number }) {
  'worklet';
  if (tau <= 0) return 0;
  const wd = s.omega * Math.sqrt(1 - s.zeta * s.zeta);
  return ((s.omega * s.omega) / wd) * Math.exp(-s.zeta * s.omega * tau) * Math.sin(wd * tau);
}

export function smoothstep(p: number) {
  'worklet';
  const x = clamp01(p);
  return x * x * (3 - 2 * x);
}

// ---------------------------------------------------------------------------------------------
// The race replay
// ---------------------------------------------------------------------------------------------

/** autoPlay starts the replay this long into the loop. */
export const REPLAY_AT = 0.8;

/** Replay clock (seconds). */
export const REPLAY = {
  dotsIn: [0.55, 0.9],
  lightsOn: 0.85, // first light; the others follow LIGHTS.step apart
  go: 1.95, // lights out
  finish: 5.9, // the leader crosses the line at the end of lap 2
  close: 6.15, // the circuit starts home
  dotsOut: 0.25, // fade of the cars from `close`
  dim: { in: 0.35, outFrom: 0.4, out: 0.45 }, // header dims over `in`; lights up `outFrom` after close, over `out`
  end: 7.1, // the replay is over: everything is at rest
} as const;

/** The circuit's flight: image centre (reference pt) at rest and open, scale, tilt in flight. */
export const MAP = {
  scale: 2.35,
  from: { x: 340, y: 194 }, // = layout.track centre
  to: { x: 201, y: 240 },
  tilt: 10, // degrees of rotateX at mid-flight
  perspective: 700,
  spring: { omega: 9, zeta: 0.8 }, // ≈ 1.5 % overshoot
  settle: 0.85, // s: the spring is taken as home after this
  hiResFrom: 0.25, // the 9× render takes over from the 3× one over this much extra scale
  press: 0.97, // the circuit under a finger
} as const;

/** Start lights: a pill of five lights above the open circuit (reference pt). */
export const LIGHTS = {
  count: 5,
  step: 0.18,
  on: 0.06, // s to light up
  off: 0.08, // s to go out (all together at REPLAY.go)
  cx: 201,
  cy: 124,
  pitch: 15,
  r: 4.5,
  pill: { w: 84, h: 17 },
  glow: { radius: 2, blur: 4, alpha: 0.45 }, // × r, pt, alpha
  housingIn: [0.55, 0.8],
  housingOut: [0.35, 0.65], // after go
} as const;

/** The race model, in laps of the circuit. */
export const RACE = {
  laps: 2,
  launch: 0.35, // s: time constant of the launch from the grid
  gridGap: 0.03, // laps between grid slots
  raceGap: 0.022, // laps between cars once the pack settles
  gapSettle: 1.2, // s after go
  pass: 0.35, // s: half the window in which an overtaker draws alongside and through
  lateral: 1, // pt (rest scale): the overtaker swings out, the car it passes gives way by half
  gridStagger: 0.8, // pt (rest scale): the grid's left/right stagger, gone 0.6 s after go
  staggerOut: 0.6,
  coolDelay: 0.25, // s after the leader's flag before the pack lifts off
  coolDown: 0.5, // s: time constant of the slow-down lap
  trailLag: 0.09, // laps of trail at full speed
} as const;

/** Car on the map (pt on screen, whatever the scale). */
export const CAR = { core: 2.9, centre: 1.2, glow: 7, glowBlur: 4, glowAlpha: 0.55 } as const;
/** Trail strokes: share of the trail, width (screen pt), alpha. */
export const TRAIL = [
  { part: 1, width: 3.4, alpha: 0.22 },
  { part: 0.6, width: 2.6, alpha: 0.45 },
  { part: 0.28, width: 2, alpha: 0.9 },
] as const;
/** The overtake tag above a car: font size, padding, offset above the car (screen pt). */
export const TAG = { size: 7.5, padX: 4, h: 12, above: 9, rise: 3, radius: 6, show: [0.3, 0.14], hide: [0.55, 0.7] } as const;
export const FINISH_FLASH = { dur: 0.35, from: 2, to: 13, stroke: 1.4 } as const; // reference pt

/** The standings rewind to the grid as the replay opens… (driver indices: `up` moves up a place, `down` gives it) */
export const REWIND = [
  { at: 0.15, up: 2, down: 1 }, // Sam Ives started ahead of Alex Vale
  { at: 0.22, up: 4, down: 3 }, // Theo Marsh started ahead of Noah Reed
] as const;
/** …and every overtake of the race swaps two rows at the instant the cars cross on the circuit. */
export const PASSES = [
  { at: 2.55, up: 1, down: 2 }, // Alex Vale passes Sam Ives
  { at: 3.05, up: 3, down: 4 }, // Noah Reed passes Theo Marsh
  { at: 3.6, up: 1, down: 0 }, // Alex Vale takes the lead from Mira Chen
  { at: 4.15, up: 5, down: 4 }, // Kai Lindgren passes Theo Marsh
  { at: 4.75, up: 0, down: 1 }, // Mira Chen takes the lead back
  { at: 5.3, up: 4, down: 5 }, // Theo Marsh passes Kai Lindgren back: the Race result
] as const;

/** Grid slot per driver (Race order after the rewind): Mira, Sam, Alex, Theo, Noah, Kai. */
export const GRID_SLOT = [0, 2, 1, 4, 3, 5] as const;

const RACE_T = REPLAY.finish - REPLAY.go;
const RACE_V = RACE.laps / (RACE_T - RACE.launch * (1 - Math.exp(-RACE_T / RACE.launch)));
const COOL_AT = RACE_T + RACE.coolDelay;
const COOL_D = RACE_V * (COOL_AT - RACE.launch * (1 - Math.exp(-COOL_AT / RACE.launch)));
const COOL_V = RACE_V * (1 - Math.exp(-COOL_AT / RACE.launch));

/** The leader's distance in laps (0 = on the line at lights out). */
export function leaderLaps(r: number) {
  'worklet';
  const tau = r - REPLAY.go;
  if (tau <= 0) return 0;
  if (tau <= COOL_AT) return RACE_V * (tau - RACE.launch * (1 - Math.exp(-tau / RACE.launch)));
  return COOL_D + COOL_V * RACE.coolDown * (1 - Math.exp(-(tau - COOL_AT) / RACE.coolDown));
}

/** Speed as a share of race pace (0 … 1). */
export function leaderPace(r: number) {
  'worklet';
  const tau = r - REPLAY.go;
  if (tau <= 0) return 0;
  if (tau <= COOL_AT) return 1 - Math.exp(-tau / RACE.launch);
  return (COOL_V / RACE_V) * Math.exp(-(tau - COOL_AT) / RACE.coolDown);
}

/** A car's running place on the circuit (0 = leader), crossing over smoothly during a pass. */
export function trackRank(i: number, r: number) {
  'worklet';
  let rank: number = GRID_SLOT[i];
  for (let k = 0; k < PASSES.length; k++) {
    const e = PASSES[k];
    const d = i === e.up ? -1 : i === e.down ? 1 : 0;
    if (d !== 0) rank += d * smoothstep((r - e.at + RACE.pass) / (2 * RACE.pass));
  }
  return rank;
}

export function raceGap(r: number) {
  'worklet';
  return lerp(RACE.gridGap, RACE.raceGap, smoothstep(seg(r, REPLAY.go, REPLAY.go + RACE.gapSettle)));
}

/** A car's distance in laps along the circuit (negative = behind the line on the grid). */
export function carLaps(i: number, r: number) {
  'worklet';
  return leaderLaps(r) - raceGap(r) * trackRank(i, r);
}

/** Sideways offset (pt at rest scale, + = outside): grid stagger, then swinging out to pass. */
export function carLateral(i: number, r: number) {
  'worklet';
  const grid = GRID_SLOT[i] % 2 === 0 ? RACE.gridStagger : -RACE.gridStagger;
  let side = grid * (1 - smoothstep(seg(r, REPLAY.go, REPLAY.go + RACE.staggerOut)));
  for (let k = 0; k < PASSES.length; k++) {
    const e = PASSES[k];
    const w = Math.sin(Math.PI * seg(r, e.at - RACE.pass, e.at + RACE.pass));
    if (i === e.up) side += RACE.lateral * w;
    else if (i === e.down) side -= RACE.lateral * 0.5 * w;
  }
  return side;
}

export function carAlpha(r: number) {
  'worklet';
  return smoothstep(seg(r, REPLAY.dotsIn[0], REPLAY.dotsIn[1])) * (1 - smoothstep(seg(r, REPLAY.close, REPLAY.close + REPLAY.dotsOut)));
}

/** When the car in each finishing slot crosses the line (seconds of replay): its points roll in then. */
function finishTimes() {
  const out: number[] = [];
  for (let slot = 0; slot < 6; slot++) {
    let lo: number = REPLAY.finish - 0.01;
    let hi: number = REPLAY.close;
    for (let k = 0; k < 40; k++) {
      const m = (lo + hi) / 2;
      // at the flag every pass is over: the car in `slot` runs `slot` gaps behind the leader
      if (leaderLaps(m) - RACE.raceGap * slot >= RACE.laps) hi = m;
      else lo = m;
    }
    out.push(hi);
  }
  return out;
}
export const FINISH_AT = finishTimes();

/** The overtake tag of driver i: 0 … 1. */
export function tagAlpha(i: number, r: number) {
  'worklet';
  let a = 0;
  for (let k = 0; k < PASSES.length; k++) {
    const e = PASSES[k];
    if (e.up !== i) continue;
    const v = easeOut(seg(r, e.at - TAG.show[0], e.at - TAG.show[1])) * (1 - easeOut(seg(r, e.at + TAG.hide[0], e.at + TAG.hide[1])));
    if (v > a) a = v;
  }
  return a;
}

/** The rewind is a flat, quick slide (no lift, lean or trail): it is not an overtake. ω 12, ζ 1, home in 0.6 s. */
export const REWIND_SPRING = { omega: 12, zeta: 0.999 } as const;
export const REWIND_DUR = 0.6;

/**
 * A standings row during the replay: slot (float), speed (slots/s, overtakes only), bump (0 … 1 … 0 while
 * overtaking), dir (−1 up, +1 down; also set while rewinding so the plate hides the crossing).
 */
export function replayRow(i: number, r: number) {
  'worklet';
  let y = i;
  let speed = 0;
  let bump = 0;
  let dir = 0;
  for (let k = 0; k < REWIND.length + PASSES.length; k++) {
    const e = k < REWIND.length ? REWIND[k] : PASSES[k - REWIND.length];
    const d = i === e.up ? -1 : i === e.down ? 1 : 0;
    if (d === 0) continue;
    const tau = r - e.at;
    if (tau <= 0) continue;
    if (k < REWIND.length) {
      if (tau >= REWIND_DUR) y += d;
      else {
        y += d * spring(tau, REWIND_SPRING);
        dir = d;
      }
      continue;
    }
    if (tau >= WINDOWS.row.dur) {
      y += d;
      continue;
    }
    const p = spring(tau, ROW_SPRING);
    y += d * p;
    speed += d * springVelocity(tau, ROW_SPRING);
    bump = Math.sin(Math.PI * clamp01(p));
    dir = d;
  }
  return { y, speed, bump, dir };
}

/** The circuit's flight out (0 → 1) and home (→ 0): closed-form springs. */
export function mapOpen(r: number) {
  'worklet';
  if (r <= 0 || r >= REPLAY.end) return 0;
  const out = r >= MAP.settle ? 1 : spring(r, MAP.spring);
  const back = r <= REPLAY.close ? 0 : r - REPLAY.close >= MAP.settle ? 1 : spring(r - REPLAY.close, MAP.spring);
  return out - back;
}

/** How dim the header copy is (0 = as the pin, 1 = dimmed under the open circuit). */
export function headerDim(r: number) {
  'worklet';
  if (r <= 0 || r >= REPLAY.end) return 0;
  const from = REPLAY.close + REPLAY.dim.outFrom;
  return easeOut(seg(r, 0, REPLAY.dim.in)) * (1 - easeOut(seg(r, from, from + REPLAY.dim.out)));
}

/** Light k of the start sequence: 0 … 1. */
export function lightOn(k: number, r: number) {
  'worklet';
  const at = REPLAY.lightsOn + LIGHTS.step * k;
  return easeOut(seg(r, at, at + LIGHTS.on)) * (1 - seg(r, REPLAY.go, REPLAY.go + LIGHTS.off));
}

export function lightsHousing(r: number) {
  'worklet';
  return smoothstep(seg(r, LIGHTS.housingIn[0], LIGHTS.housingIn[1])) *
    (1 - smoothstep(seg(r, REPLAY.go + LIGHTS.housingOut[0], REPLAY.go + LIGHTS.housingOut[1])));
}

/** Points of a finishing slot during the replay: hidden as it opens, rolled in as its car takes the flag. */
export const POINTS_HIDE = [0.1, 0.35] as const;
export function replayPoints(slot: number, r: number) {
  'worklet';
  if (r <= 0) return { alpha: 1, rise: 0 };
  const at = FINISH_AT[slot];
  if (r < at) return { alpha: 1 - easeOut(seg(r, POINTS_HIDE[0], POINTS_HIDE[1])), rise: 0 };
  const q = easeOut(seg(r, at, at + WINDOWS.points.dur));
  return { alpha: q, rise: 1 - q };
}

/** Added to `replayPlace` once the last car has taken the flag (all points shown). */
export const REPLAY_FLAGGED = 6;

/**
 * Driver i's running place during the replay, for its accessibility label: the rank of its row's y
 * (ties, at the very instant of a crossing, broken by index so places stay distinct), + REPLAY_FLAGGED
 * after the last car's flag; −1 when no replay runs. It changes only at a swap or at the flag.
 */
export function replayPlace(i: number, r: number) {
  'worklet';
  if (r <= 0) return -1;
  const yi = replayRow(i, r).y;
  let place = 0;
  for (let j = 0; j < 6; j++) {
    if (j === i) continue;
    const yj = replayRow(j, r).y;
    if (yj < yi || (yj === yi && j < i)) place++;
  }
  return r >= FINISH_AT[5] ? place + REPLAY_FLAGGED : place;
}

export function finishFlash(r: number) {
  'worklet';
  const p = seg(r, REPLAY.finish, REPLAY.finish + FINISH_FLASH.dur);
  return p <= 0 || p >= 1 ? 0 : p;
}

export type Frame = {
  from: number; // 0 = the switch starts from Race, 1 = from Sprint
  u: number; // progress of the switch, 0 … 1
  lap: number; // comet lap clock, 0 = hidden, 0 … LAP_END
  flFrom: number; // fastest-lap line: which session it shows before its roll
  flU: number; // its roll, 0 … 1 (linear; eased where drawn)
  press: number; // title press, 0 … 1
  rp: number; // replay clock, 0 = off, 0 … REPLAY.end
};

export function frameAt(t: number): Frame {
  'worklet';
  const r = t - REPLAY_AT;
  const rp = r > 0 && r < REPLAY.end ? r : 0;
  return { from: 0, u: 0, lap: 0, flFrom: 0, flU: 0, press: 0, rp };
}
