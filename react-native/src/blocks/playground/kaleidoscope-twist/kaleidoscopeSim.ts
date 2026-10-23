/**
 * The object cell of the kaleidoscope: glass beads and shards loose in a round chamber.
 *
 * The chamber turns with the user's hand (or the script), so gravity, seen from inside it, turns
 * the other way and the pieces tumble. Each piece is a circle for the physics: gravity, a little
 * air drag, then pairwise contacts (overlap pushed apart, an impulse with some bounce and friction)
 * and the chamber's round wall, over SUBSTEPS fixed substeps per tick. Shards also spin as they
 * roll. Everything is plain arithmetic with a seeded random, so it runs the same, step for step, on
 * any platform. The same clock drives the self-playing script: the turn, two shakes, the number of
 * mirror segments and the iris that closes over the seam of the loop.
 */

// One tick is 1/60 s; a slow frame runs at most MAX_TICKS of them (a 50 ms cap).
export const TICK = 1 / 60;
export const MAX_TICKS = 3;
const SUBSTEPS = 4;
const PASSES = 2;

// The chamber's radius, in cell units (drawn about 1:1 with pt).
export const CELL_R = 120;
// Gravity (pt/s²), air drag (1/s), bounce between pieces and on the wall, and friction. The wall
// grips hard, so the pile is carried up the side as the chamber turns and then tumbles down.
const GRAVITY = 820;
const AIR = 0.7;
const BOUNCE = 0.28;
const WALL_BOUNCE = 0.22;
const FRICTION = 0.3;
const WALL_GRIP = 0.3;
// Spin follows rolling at this rate (1/s).
const ROLL_RATE = 14;

// Pieces at the start, and the most a hand can add (the oldest added one goes first).
const START_PIECES = 52;
export const MAX_PIECES = 64;
// A tapped bead grows in over this long (s).
const GROW_TIME = 0.35;

// Kinds of piece: a round glass bead, a flat shard, a tiny seed bead.
export const BEAD = 0;
export const SHARD = 1;
export const SEED = 2;
// Jewel tones the pieces are made in.
export const TONES = 8;

// The turn: free spin slows by this rate (1/s); under the script a spring (stiffness, damping)
// follows the script's angle.
const SPIN_DRAG = 1.6;
const TURN_STIFFNESS = 26;
const TURN_DAMPING = 10.2;
// Segment count: a spring towards the chosen even count.
const SEG_STIFFNESS = 90;
const SEG_DAMPING = 15;
export const SEG_MIN = 6;
export const SEG_MAX = 12;
// The iris opens at this rate (1/s) when nothing scripts it.
const IRIS_RATE = 3.2;

// autoPlay (seconds): the iris opens, the chamber turns 200°, is shaken, turns on to 420° with
// the segments going 8 → 12 → 6 → 8, is shaken again, and the iris closes while it all resets.
export const LOOP = 14;
const IRIS_OPEN = 0.9;
const IRIS_CLOSE_FROM = 12.9;
const IRIS_CLOSE_TO = 13.5;
const RESET_AT = 13.7;
// After a touch the script picks up here, past the iris opening.
const RESUME_AT = 1.0;
const TURN_KEYS = [
  [0, 0],
  [1.0, 0],
  [4.2, 200],
  [6.2, 200],
  [9.6, 420],
  [14, 420],
];
const SHAKES = [4.6, 10.3];
const SHAKE_SPEED = 300;
const SEG_KEYS = [
  [5.2, 12],
  [8.4, 6],
  [11.2, 8],
];
export const SEG_START = 8;

export type KaleidoState = {
  count: number;
  x: number[];
  y: number[];
  vx: number[];
  vy: number[];
  r: number[];
  ang: number[];
  spin: number[];
  kind: number[];
  tone: number[];
  /** Shape seed of each piece (a shard's outline), and when it was added (s). */
  shape: number[];
  born: number[];
  /** The settled start, restored at the loop's seam. */
  rest: number[][];
  restCount: number;
  seed: number;
  time: number;
  clock: number;
  hold: number;
  looping: boolean;
  reduced: boolean;
  /** The chamber's turn (rad, clockwise) and its speed. */
  angle: number;
  angVel: number;
  /** The script turns the chamber relative to where it was when the script (re)started. */
  turnBase: number;
  dragging: boolean;
  /** Segment count, continuous while it morphs, its speed and the even count it settles on. */
  seg: number;
  segVel: number;
  segTarget: number;
  pinching: boolean;
  /** 0 = iris closed, 1 = open. */
  iris: number;
  /** Next slot an added piece replaces once the cell is full. */
  nextSlot: number;
};

/** A small, fast, seeded random in [0, 1): xorshift32 on the state's seed. */
export function random(st: KaleidoState) {
  'worklet';
  let x = st.seed | 0;
  x ^= x << 13;
  x ^= x >>> 17;
  x ^= x << 5;
  st.seed = x;
  return (x >>> 0) / 4294967296;
}

function snapshot(st: KaleidoState) {
  'worklet';
  st.rest = [st.x.slice(), st.y.slice(), st.r.slice(), st.ang.slice(), st.kind.slice(), st.tone.slice(), st.shape.slice()];
  st.restCount = st.count;
}

/** Puts every piece back where it settled at the start, still. */
export function restore(st: KaleidoState) {
  'worklet';
  const [x, y, r, ang, kind, tone, shape] = st.rest;
  st.count = st.restCount;
  st.x = x.slice();
  st.y = y.slice();
  st.r = r.slice();
  st.ang = ang.slice();
  st.kind = kind.slice();
  st.tone = tone.slice();
  st.shape = shape.slice();
  st.vx = x.map(() => 0);
  st.vy = x.map(() => 0);
  st.spin = x.map(() => 0);
  st.born = x.map(() => -10);
  st.angle = 0;
  st.angVel = 0;
  st.turnBase = 0;
  st.seg = SEG_START;
  st.segVel = 0;
  st.segTarget = SEG_START;
  st.nextSlot = st.restCount;
}

function pushPiece(st: KaleidoState, x: number, y: number, kind: number) {
  'worklet';
  const r = kind === BEAD ? 8 + random(st) * 7 : kind === SHARD ? 10 + random(st) * 7 : 3.6 + random(st) * 1.6;
  st.x.push(x);
  st.y.push(y);
  st.vx.push(0);
  st.vy.push(0);
  st.r.push(r);
  st.ang.push(random(st) * Math.PI * 2);
  st.spin.push(0);
  st.kind.push(kind);
  st.tone.push(Math.floor(random(st) * TONES));
  st.shape.push(Math.floor(random(st) * 100000));
  st.born.push(-10);
  st.count += 1;
}

/** One tick of the pieces: SUBSTEPS of gravity, drag, contacts and the wall. */
function physics(st: KaleidoState) {
  'worklet';
  const h = TICK / SUBSTEPS;
  const gx = GRAVITY * Math.sin(st.angle);
  const gy = GRAVITY * Math.cos(st.angle);
  const drag = 1 - AIR * h;
  const n = st.count;
  const { x, y, vx, vy, r, spin, ang } = st;
  for (let s = 0; s < SUBSTEPS; s += 1) {
    for (let i = 0; i < n; i += 1) {
      vx[i] = (vx[i] + gx * h) * drag;
      vy[i] = (vy[i] + gy * h) * drag;
      x[i] += vx[i] * h;
      y[i] += vy[i] * h;
    }
    for (let pass = 0; pass < PASSES; pass += 1) {
      for (let i = 0; i < n; i += 1) {
        for (let j = i + 1; j < n; j += 1) {
          const dx = x[j] - x[i];
          const dy = y[j] - y[i];
          const rr = r[i] + r[j];
          const d2 = dx * dx + dy * dy;
          if (d2 >= rr * rr || d2 < 1e-6) continue;
          const d = Math.sqrt(d2);
          const nx = dx / d;
          const ny = dy / d;
          // Mass goes with area.
          const mi = r[i] * r[i];
          const mj = r[j] * r[j];
          const wi = mj / (mi + mj);
          const wj = mi / (mi + mj);
          const overlap = rr - d;
          x[i] -= nx * overlap * wi;
          y[i] -= ny * overlap * wi;
          x[j] += nx * overlap * wj;
          y[j] += ny * overlap * wj;
          if (pass > 0) continue;
          const rvx = vx[j] - vx[i];
          const rvy = vy[j] - vy[i];
          const vn = rvx * nx + rvy * ny;
          if (vn >= 0) continue;
          const imp = -(1 + BOUNCE) * vn;
          vx[i] -= nx * imp * wi;
          vy[i] -= ny * imp * wi;
          vx[j] += nx * imp * wj;
          vy[j] += ny * imp * wj;
          // Friction along the contact, at most FRICTION of the push.
          const tx = -ny;
          const ty = nx;
          const vt = rvx * tx + rvy * ty;
          const f = Math.max(-FRICTION * imp, Math.min(FRICTION * imp, vt * 0.5));
          vx[i] += tx * f * wi;
          vy[i] += ty * f * wi;
          vx[j] -= tx * f * wj;
          vy[j] -= ty * f * wj;
        }
      }
      for (let i = 0; i < n; i += 1) {
        const d = Math.hypot(x[i], y[i]);
        const lim = CELL_R - r[i];
        if (d <= lim || d < 1e-6) continue;
        const nx = x[i] / d;
        const ny = y[i] / d;
        x[i] = nx * lim;
        y[i] = ny * lim;
        const vn = vx[i] * nx + vy[i] * ny;
        if (vn > 0) {
          vx[i] -= (1 + WALL_BOUNCE) * vn * nx;
          vy[i] -= (1 + WALL_BOUNCE) * vn * ny;
        }
        // Rolling along the wall: the spin follows the speed along it.
        const vt = -vx[i] * ny + vy[i] * nx;
        vx[i] -= -ny * vt * WALL_GRIP;
        vy[i] -= nx * vt * WALL_GRIP;
        spin[i] += (vt / r[i] - spin[i]) * Math.min(1, ROLL_RATE * h);
      }
    }
    for (let i = 0; i < n; i += 1) ang[i] += spin[i] * h;
  }
}

export function createKaleidoState(seed: number, looping: boolean, reduced: boolean): KaleidoState {
  'worklet';
  const st: KaleidoState = {
    count: 0,
    x: [],
    y: [],
    vx: [],
    vy: [],
    r: [],
    ang: [],
    spin: [],
    kind: [],
    tone: [],
    shape: [],
    born: [],
    rest: [],
    restCount: 0,
    seed: seed | 0 || 1,
    time: 0,
    clock: 0,
    hold: 0,
    looping,
    reduced,
    angle: 0,
    angVel: 0,
    turnBase: 0,
    dragging: false,
    seg: SEG_START,
    segVel: 0,
    segTarget: SEG_START,
    pinching: false,
    iris: 0,
    nextSlot: 0,
  };
  // Scattered through the chamber, then left to fall and settle for three seconds.
  for (let i = 0; i < START_PIECES; i += 1) {
    const kind = i % 4 === 3 ? SHARD : i % 5 === 1 ? SEED : i % 7 === 2 ? SHARD : BEAD;
    const a = random(st) * Math.PI * 2;
    const d = Math.sqrt(random(st)) * (CELL_R - 22);
    pushPiece(st, Math.cos(a) * d, Math.sin(a) * d, kind);
  }
  for (let k = 0; k < 180; k += 1) physics(st);
  for (let i = 0; i < st.count; i += 1) {
    st.vx[i] = 0;
    st.vy[i] = 0;
    st.spin[i] = 0;
  }
  snapshot(st);
  st.nextSlot = st.count;
  return st;
}

/** Drops a new piece at (x, y) in the chamber's frame; once full, it replaces the oldest added. */
export function addPiece(st: KaleidoState, x: number, y: number) {
  'worklet';
  const d = Math.hypot(x, y);
  const lim = CELL_R - 20;
  const px = d > lim ? (x / d) * lim : x;
  const py = d > lim ? (y / d) * lim : y;
  const kind = random(st) > 0.6 ? SHARD : BEAD;
  if (st.count < MAX_PIECES) {
    pushPiece(st, px, py, kind);
    st.born[st.count - 1] = st.time;
    return;
  }
  const i = st.nextSlot;
  st.nextSlot = i + 1 >= MAX_PIECES ? st.restCount : i + 1;
  st.x[i] = px;
  st.y[i] = py;
  st.vx[i] = 0;
  st.vy[i] = 0;
  st.kind[i] = kind;
  st.r[i] = kind === BEAD ? 8 + random(st) * 7 : 10 + random(st) * 7;
  st.tone[i] = Math.floor(random(st) * TONES);
  st.shape[i] = Math.floor(random(st) * 100000);
  st.born[i] = st.time;
}

/** Gives every piece a random kick, mostly against gravity. */
export function shake(st: KaleidoState, speed: number) {
  'worklet';
  const gx = Math.sin(st.angle);
  const gy = Math.cos(st.angle);
  for (let i = 0; i < st.count; i += 1) {
    const a = random(st) * Math.PI * 2;
    const s = speed * (0.4 + 0.6 * random(st));
    st.vx[i] += Math.cos(a) * s * 0.6 - gx * s;
    st.vy[i] += Math.sin(a) * s * 0.6 - gy * s;
    st.spin[i] += (random(st) - 0.5) * 18;
  }
}

/** Size of piece `i` now: added pieces grow in. */
export function grownRadius(st: KaleidoState, i: number) {
  'worklet';
  const age = (st.time - st.born[i]) / GROW_TIME;
  if (age >= 1) return st.r[i];
  const t = Math.max(0, age);
  // An ease-out with a small overshoot.
  const s = 1 + 2.2 * Math.pow(t - 1, 3) + 1.2 * Math.pow(t - 1, 2);
  return st.r[i] * (0.55 + 0.45 * s);
}

function smooth(t: number) {
  'worklet';
  const u = Math.max(0, Math.min(1, t));
  return u * u * (3 - 2 * u);
}

/** The script's angle for the chamber at `t` (rad): eased between keys. */
function scriptAngle(t: number) {
  'worklet';
  // Past the reset the chamber is back at its start, hidden by the closed iris.
  if (t >= RESET_AT) return 0;
  for (let k = 1; k < TURN_KEYS.length; k += 1) {
    const [t1, a1] = TURN_KEYS[k];
    if (t <= t1) {
      const [t0, a0] = TURN_KEYS[k - 1];
      return ((a0 + (a1 - a0) * smooth((t - t0) / (t1 - t0))) * Math.PI) / 180;
    }
  }
  return (TURN_KEYS[TURN_KEYS.length - 1][1] * Math.PI) / 180;
}

/** The script's iris at `t`: opens at the start of the loop, closes at its end. */
function scriptIris(t: number) {
  'worklet';
  if (t < IRIS_OPEN) return 1 - Math.pow(1 - t / IRIS_OPEN, 3);
  if (t < IRIS_CLOSE_FROM) return 1;
  return 1 - smooth((t - IRIS_CLOSE_FROM) / (IRIS_CLOSE_TO - IRIS_CLOSE_FROM));
}

function crossed(before: number, after: number, at: number) {
  'worklet';
  return before < at && after >= at;
}

/** Sets the segment count the spring heads for (an even number in range). */
export function setSegments(st: KaleidoState, count: number) {
  'worklet';
  st.segTarget = Math.max(SEG_MIN, Math.min(SEG_MAX, Math.round(count / 2) * 2));
}

/** One tick: the script, the chamber's turn, the segment spring, the iris, then the pieces. */
export function stepKaleido(st: KaleidoState) {
  'worklet';
  st.time += TICK;
  let scripted = false;
  if (st.hold > 0) {
    // A hand still on the glass keeps the script paused; the hold counts down once it lets go.
    if (!st.dragging && !st.pinching) st.hold -= TICK;
    if (st.hold <= 0) {
      st.clock = RESUME_AT;
      st.turnBase = st.angle - scriptAngle(RESUME_AT);
    }
  } else if (st.looping) {
    scripted = true;
    const before = st.clock;
    let after = before + TICK;
    if (after >= LOOP) after -= LOOP;
    for (let k = 0; k < SHAKES.length; k += 1) {
      if (crossed(before, after, SHAKES[k]) && !st.reduced) shake(st, SHAKE_SPEED);
    }
    for (let k = 0; k < SEG_KEYS.length; k += 1) {
      if (crossed(before, after, SEG_KEYS[k][0])) setSegments(st, SEG_KEYS[k][1]);
    }
    if (crossed(before, after, RESET_AT)) restore(st);
    st.clock = after;
  }

  // The turn.
  if (!st.dragging) {
    if (scripted) {
      // The script's angle is measured from where it (re)started.
      const goal = st.turnBase + scriptAngle(st.clock);
      const acc = TURN_STIFFNESS * (goal - st.angle) - TURN_DAMPING * st.angVel;
      st.angVel += acc * TICK;
    } else {
      st.angVel *= Math.exp(-SPIN_DRAG * TICK);
    }
    st.angle += st.angVel * TICK;
  }

  // The segments.
  if (!st.pinching) {
    if (st.reduced) {
      st.seg = st.segTarget;
      st.segVel = 0;
    } else {
      const acc = SEG_STIFFNESS * (st.segTarget - st.seg) - SEG_DAMPING * st.segVel;
      st.segVel += acc * TICK;
      st.seg += st.segVel * TICK;
    }
  }

  // The iris.
  st.iris = scripted ? scriptIris(st.clock) : st.iris + (1 - st.iris) * (1 - Math.exp(-IRIS_RATE * TICK));

  physics(st);
}

/**
 * The view: every point of the round window is folded into one wedge of the object cell, mirrored
 * from one segment to the next. While the count morphs, two counts are drawn and blended, with a
 * swirl that peaks halfway. `cell` is the object cell as drawn this frame, in cell units.
 */
export const VIEW_SKSL = `
uniform shader cell;
uniform float2 center;
uniform float viewR;
uniform float2 apex;
uniform float span;
uniform float segA;
uniform float segB;
uniform float mixB;
uniform float swirl;
uniform float2 rot;

// Folds a point of the window into the wedge that looks straight down into the chamber, where
// gravity keeps the pieces.
float2 fold(float2 d, float segs) {
  float r = length(d);
  float w = 6.28318530718 / segs;
  float a = atan(d.y, d.x) + swirl * r;
  float p = 2.0 * w;
  float m = a - p * floor(a / p);
  if (m > w) { m = p - m; }
  m += 1.57079632679 - 0.5 * w;
  return float2(cos(m), sin(m)) * r;
}

float2 toCell(float2 q) {
  float2 c = apex + q * span;
  return float2(rot.x * c.x + rot.y * c.y, -rot.y * c.x + rot.x * c.y);
}

half4 main(float2 p) {
  float2 d = (p - center) / viewR;
  half4 a = cell.eval(toCell(fold(d, segA)));
  half4 b = cell.eval(toCell(fold(d, segB)));
  half4 c = mix(a, b, half(mixB));
  float edge = clamp((1.0 - length(d)) * viewR, 0.0, 1.0);
  return c * half(edge);
}
`;
