/**
 * The tangram: the seven classic pieces cut from one square, four silhouettes made of them, and
 * the springs that move the pieces.
 *
 * Lengths are in units of the small triangle's leg (u). Cut from a square of side 2√2:
 * two large triangles (legs 2), one medium (legs √2), two small (legs 1), a square (side 1)
 * and a parallelogram (sides 1 and √2, angles 45° and 135°). Areas 2+2+1+1+1+½+½ = 8.
 *
 * A pose is a centre (the mean of the corners), a turn in steps of 45° and a mirror flag
 * (mirrored across the local y axis before turning). Screen y points down, so a positive turn is
 * clockwise. Each silhouette is written as the corners of its seven pieces; its slots (one pose
 * per piece) are solved from those corners, which also proves the corners are an exact tangram.
 */

const S2 = Math.SQRT2;
const H2 = S2 / 2;

export type Kind = 'large' | 'medium' | 'small' | 'square' | 'parallelogram';

/** The pieces in a fixed order; slots of every silhouette follow the same order. */
export const PIECES: Kind[] = ['large', 'large', 'medium', 'square', 'parallelogram', 'small', 'small'];

/** Each kind's corners, centred on their mean, unit = small triangle's leg, y down. */
const RAW: Record<Kind, number[]> = {
  large: [0, 0, 2, 0, 0, -2],
  medium: [0, 0, 2, 0, 1, -1],
  small: [0, 0, 1, 0, 0, -1],
  square: [0, 0, 1, 0, 1, -1, 0, -1],
  parallelogram: [0, 0, 1, 0, 2, -1, 1, -1],
};

function centre(points: number[]) {
  let x = 0;
  let y = 0;
  const n = points.length / 2;
  for (let i = 0; i < n; i += 1) {
    x += points[i * 2];
    y += points[i * 2 + 1];
  }
  return { x: x / n, y: y / n };
}

function centred(points: number[]) {
  const c = centre(points);
  return points.map((v, i) => (i % 2 === 0 ? v - c.x : v - c.y));
}

export const SHAPES: number[][] = PIECES.map((kind) => centred(RAW[kind]));

/** A shape's corners at a pose (turn in radians, flip 0/1), around (x, y), scaled by `unit`. */
export function placed(shape: number[], x: number, y: number, angle: number, flip: number, unit: number) {
  'worklet';
  const c = Math.cos(angle);
  const s = Math.sin(angle);
  const out: number[] = [];
  for (let i = 0; i < shape.length; i += 2) {
    const lx = (flip > 0.5 ? -shape[i] : shape[i]) * unit;
    const ly = shape[i + 1] * unit;
    out.push(x + lx * c - ly * s, y + lx * s + ly * c);
  }
  return out;
}

/** Whether two corner lists are the same set of points, within `eps`. */
function sameCorners(a: number[], b: number[], eps: number) {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i += 2) {
    let found = false;
    for (let j = 0; j < b.length; j += 2) {
      if (Math.abs(a[i] - b[j]) < eps && Math.abs(a[i + 1] - b[j + 1]) < eps) {
        found = true;
        break;
      }
    }
    if (!found) return false;
  }
  return true;
}

export type Slot = {
  /** Centre in units, relative to the silhouette's centre. */
  x: number;
  y: number;
  /** Every (turn step 0…7, flip) that lays the piece exactly on this slot. */
  turns: number[];
  flips: number[];
};

export type Silhouette = {
  name: string;
  /** The solid colour it fills with when solved. */
  color: string;
  slots: Slot[];
  /** Half its width and height (units), for layout. */
  halfW: number;
  halfH: number;
};

/** Solves the slots of a silhouette given as corners (y up, as drawn on squared paper). */
function solve(name: string, color: string, pieces: number[][]): Silhouette {
  // To screen space: y down.
  const down = pieces.map((p) => p.map((v, i) => (i % 2 === 1 ? -v : v)));
  let minX = Infinity;
  let maxX = -Infinity;
  let minY = Infinity;
  let maxY = -Infinity;
  for (const p of down) {
    for (let i = 0; i < p.length; i += 2) {
      minX = Math.min(minX, p[i]);
      maxX = Math.max(maxX, p[i]);
      minY = Math.min(minY, p[i + 1]);
      maxY = Math.max(maxY, p[i + 1]);
    }
  }
  const mx = (minX + maxX) / 2;
  const my = (minY + maxY) / 2;
  const slots = down.map((corners, k) => {
    const c = centre(corners);
    const turns: number[] = [];
    const flips: number[] = [];
    for (let flip = 0; flip < 2; flip += 1) {
      for (let turn = 0; turn < 8; turn += 1) {
        const at = placed(SHAPES[k], c.x, c.y, (turn * Math.PI) / 4, flip, 1);
        if (sameCorners(at, corners, 1e-6)) {
          turns.push(turn);
          flips.push(flip);
        }
      }
    }
    if (turns.length === 0) throw new Error(`Tangram ${name}: piece ${k} is not a ${PIECES[k]}`);
    return { x: c.x - mx, y: c.y - my, turns, flips };
  });
  return { name, color, slots, halfW: (maxX - minX) / 2, halfH: (maxY - minY) / 2 };
}

// The silhouettes, piece by piece in PIECES order: large, large, medium, square, parallelogram,
// small, small. Corners in units, y up.
const CAT = solve('The Cat', '#e2603a', [
  // Body: two large triangles make one big right triangle, chest upright, back sloping to the tail.
  [0, 0, 0, 2 * S2, S2, S2],
  [0, 0, 2 * S2, 0, S2, S2],
  // Front paws, against the chest.
  [0, 0, -S2, 0, 0, S2],
  // The head: a diamond on the shoulder.
  [0.5, 2 * S2 - 0.5, 0.5 + H2, 2 * S2 - 0.5 + H2, 0.5, 2 * S2 - 0.5 + S2, 0.5 - H2, 2 * S2 - 0.5 + H2],
  // The tail, along the ground.
  [2 * S2, 0, 2 * S2 + 1, 0, 2 * S2 + 2, 1, 2 * S2 + 1, 1],
  // Ears.
  [0.5 - H2, 2 * S2 - 0.5 + H2, 0.5, 2 * S2 - 0.5 + S2, 0.5 - H2, 2 * S2 - 0.5 + H2 + S2],
  [0.5 + H2, 2 * S2 - 0.5 + H2, 0.5, 2 * S2 - 0.5 + S2, 0.5 + H2, 2 * S2 - 0.5 + H2 + S2],
]);

const BOAT = solve('The Boat', '#2f6db5', [
  // Mainsail, then the jib before the mast.
  [1, 1, 1, 3, 3, 1],
  [1, 1, 1, 1 + 2 * S2, 1 - S2, 1 + S2],
  // A pennant at the masthead.
  [1, 1 + 2 * S2, 1 + S2, 1 + 2 * S2, 1, 1 + 3 * S2],
  // The hull: a square between two small triangles.
  [1, 0, 2, 0, 2, 1, 1, 1],
  // The keel, raked aft.
  [1, 0, 2, 0, 3, -1, 2, -1],
  [0, 1, 1, 1, 1, 0],
  [2, 0, 2, 1, 3, 1],
]);

const RUNNER = solve('The Runner', '#e3a52c', [
  // Torso, shoulders up.
  [0, 4, 2 * S2, 4, S2, 4 - S2],
  // The back leg, kicked out behind.
  [S2, 4 - S2, -S2, 4 - S2, 0, 4 - 2 * S2],
  // The front thigh.
  [S2, 4 - S2, 2 * S2, 4 - 2 * S2, 2 * S2, 4 - S2],
  // The head.
  [S2 + 0.5, 4, S2 + 0.5 + H2, 4 + H2, S2 + 0.5, 4 + S2, S2 + 0.5 - H2, 4 + H2],
  // The front arm, reaching up.
  [2 * S2, 4, 2 * S2 + 1, 4, 2 * S2 + 2, 5, 2 * S2 + 1, 5],
  // The front foot, and the back arm.
  [2 * S2, 4 - 2 * S2, 2 * S2, 3 - 2 * S2, 2 * S2 + 1, 3 - 2 * S2],
  [0, 4, -1, 4, 0, 3],
]);

const SWAN = solve('The Swan', '#efe6d6', [
  // The body: a large triangle on the water, the wing raised over its back.
  [0, 0, 2 * S2, 0, S2, S2],
  [2 * S2, S2, 2 * S2, S2 + 2, 2 * S2 - 2, S2],
  // The tail end of the body.
  [2 * S2, 0, 2 * S2, S2, S2, S2],
  // The head, a diamond on the neck.
  [0, 2 * S2, H2, 2 * S2 + H2, 0, 3 * S2, -H2, 2 * S2 + H2],
  // The neck, upright.
  [0, S2, H2, S2 + H2, H2, 2 * S2 + H2, 0, 2 * S2],
  // The breast.
  [0, 0, 0, S2, H2, H2],
  [0, S2, S2, S2, H2, H2],
]);

export const SILHOUETTES: Silhouette[] = [CAT, BOAT, RUNNER, SWAN];

/** The widest and tallest any silhouette gets (units), for sizing the board. */
export const MAX_HALF_W = Math.max(...SILHOUETTES.map((s) => s.halfW));
export const MAX_HALF_H = Math.max(...SILHOUETTES.map((s) => s.halfH));

/**
 * Where each piece rests off the board, around the centre of the loose area: centre (units),
 * turn (steps of 45°) and flip. Spread so none overlap; together 7.3 × 4.3 units.
 */
export const LOOSE_HALF_W = 3.7;
export const LOOSE_HALF_H = 2.2;
export const LOOSE: [number, number, number, number][] = [
  [-2.3, -0.45, 3, 0],
  [2.2, 0.6, 7, 0],
  [0, -1.05, 4, 0],
  [-0.45, 1.35, 1, 0],
  [1.45, -1.2, 2, 1],
  [-2, 1.6, 6, 0],
  [2.85, -1.5, 5, 0],
];

// Springs (1/s², 1/s): a free piece, a piece held by the finger, the snap into a slot (a little
// overshoot: the magnet), the turn, the flip and the lift.
export const SPRING_FREE = [210, 26];
export const SPRING_HELD = [900, 52];
export const SPRING_SNAP = [420, 24];
export const SPRING_TURN = [260, 28];
export const SPRING_FLIP = [180, 26.8];
export const SPRING_LIFT = [380, 36];

export type Pieces = {
  x: Float64Array;
  y: Float64Array;
  angle: Float64Array;
  /** 0…1: 0 face up, 1 mirrored; drawn as a horizontal squeeze through zero. */
  flip: Float64Array;
  lift: Float64Array;
  vx: Float64Array;
  vy: Float64Array;
  va: Float64Array;
  vf: Float64Array;
  vl: Float64Array;
  tx: Float64Array;
  ty: Float64Array;
  ta: Float64Array;
  tf: Float64Array;
  tl: Float64Array;
  /** Which spring moves the piece: 0 free, 1 held, 2 snapping. */
  mode: Int32Array;
  /** The slot it sits in, or -1. */
  slot: Int32Array;
  /** Drawing order, bottom first. */
  order: Int32Array;
};

export function createPieces(): Pieces {
  'worklet';
  const n = PIECES.length;
  const order = new Int32Array(n);
  for (let i = 0; i < n; i += 1) order[i] = i;
  return {
    x: new Float64Array(n),
    y: new Float64Array(n),
    angle: new Float64Array(n),
    flip: new Float64Array(n),
    lift: new Float64Array(n),
    vx: new Float64Array(n),
    vy: new Float64Array(n),
    va: new Float64Array(n),
    vf: new Float64Array(n),
    vl: new Float64Array(n),
    tx: new Float64Array(n),
    ty: new Float64Array(n),
    ta: new Float64Array(n),
    tf: new Float64Array(n),
    tl: new Float64Array(n),
    mode: new Int32Array(n),
    slot: new Int32Array(n).fill(-1),
    order,
  };
}

export function copyPieces(from: Pieces, to: Pieces) {
  'worklet';
  to.x.set(from.x);
  to.y.set(from.y);
  to.angle.set(from.angle);
  to.flip.set(from.flip);
  to.lift.set(from.lift);
  to.vx.set(from.vx);
  to.vy.set(from.vy);
  to.va.set(from.va);
  to.vf.set(from.vf);
  to.vl.set(from.vl);
  to.tx.set(from.tx);
  to.ty.set(from.ty);
  to.ta.set(from.ta);
  to.tf.set(from.tf);
  to.tl.set(from.tl);
  to.mode.set(from.mode);
  to.slot.set(from.slot);
  to.order.set(from.order);
}

/** Moves piece `i` to the top of the drawing order. */
export function raise(p: Pieces, i: number) {
  'worklet';
  let at = 0;
  for (let k = 0; k < p.order.length; k += 1) if (p.order[k] === i) at = k;
  for (let k = at; k < p.order.length - 1; k += 1) p.order[k] = p.order[k + 1];
  p.order[p.order.length - 1] = i;
}

function spring(value: number, velocity: number, target: number, k: number, c: number, dt: number) {
  'worklet';
  const v = velocity + (k * (target - value) - c * velocity) * dt;
  return { value: value + v * dt, velocity: v };
}

/** One tick of every piece's springs. */
export function stepPieces(p: Pieces, dt: number, calm: boolean) {
  'worklet';
  for (let i = 0; i < p.x.length; i += 1) {
    const mode = p.mode[i];
    const pos = mode === 1 ? SPRING_HELD : mode === 2 ? SPRING_SNAP : SPRING_FREE;
    // Reduced motion: the snap settles without overshoot.
    const damp = calm && mode === 2 ? 2 * Math.sqrt(pos[0]) : pos[1];
    let r = spring(p.x[i], p.vx[i], p.tx[i], pos[0], damp, dt);
    p.x[i] = r.value;
    p.vx[i] = r.velocity;
    r = spring(p.y[i], p.vy[i], p.ty[i], pos[0], damp, dt);
    p.y[i] = r.value;
    p.vy[i] = r.velocity;
    r = spring(p.angle[i], p.va[i], p.ta[i], SPRING_TURN[0], SPRING_TURN[1], dt);
    p.angle[i] = r.value;
    p.va[i] = r.velocity;
    r = spring(p.flip[i], p.vf[i], p.tf[i], SPRING_FLIP[0], SPRING_FLIP[1], dt);
    p.flip[i] = r.value;
    p.vf[i] = r.velocity;
    r = spring(p.lift[i], p.vl[i], p.tl[i], SPRING_LIFT[0], SPRING_LIFT[1], dt);
    p.lift[i] = r.value;
    p.vl[i] = r.velocity;
  }
}

/** `a` moved by whole turns to the nearest of `b`. */
export function nearestTurn(a: number, b: number) {
  'worklet';
  const full = Math.PI * 2;
  return a + full * Math.round((b - a) / full);
}

/**
 * The pose that lays piece `i` on `slot` with the least turning from its angle `angle`, keeping
 * its flip `flip` unless `anyFlip`. Returns null when the slot needs the other face up.
 */
export function slotPose(slot: Slot, angle: number, flip: number, anyFlip: boolean) {
  'worklet';
  let best = -1;
  let bestTurn = 0;
  let bestCost = Infinity;
  for (let k = 0; k < slot.turns.length; k += 1) {
    if (!anyFlip && slot.flips[k] !== flip) continue;
    const turn = nearestTurn((slot.turns[k] * Math.PI) / 4, angle);
    const cost = Math.abs(turn - angle) + (slot.flips[k] !== flip ? 1.2 : 0);
    if (cost < bestCost) {
      bestCost = cost;
      best = k;
      bestTurn = turn;
    }
  }
  if (best < 0) return null;
  return { angle: bestTurn, flip: slot.flips[best] };
}

/** Whether a piece turned to `angle` (any real angle) matches the slot's turn within `slack` rad. */
export function turnMatches(slot: Slot, angle: number, flip: number, slack: number) {
  'worklet';
  for (let k = 0; k < slot.turns.length; k += 1) {
    if (slot.flips[k] !== flip) continue;
    const turn = nearestTurn((slot.turns[k] * Math.PI) / 4, angle);
    if (Math.abs(turn - angle) <= slack) return true;
  }
  return false;
}

/** A point is inside a convex polygon given as corners (either winding). */
export function insideConvex(points: number[], x: number, y: number) {
  'worklet';
  let sign = 0;
  const n = points.length / 2;
  for (let i = 0; i < n; i += 1) {
    const j = (i + 1) % n;
    const cross = (points[j * 2] - points[i * 2]) * (y - points[i * 2 + 1]) - (points[j * 2 + 1] - points[i * 2 + 1]) * (x - points[i * 2]);
    if (cross !== 0) {
      const s = cross > 0 ? 1 : -1;
      if (sign === 0) sign = s;
      else if (s !== sign) return false;
    }
  }
  return true;
}

/** A convex polygon pulled in by `d` along every edge (corners where the moved edges meet). */
export function inset(points: number[], d: number) {
  'worklet';
  const n = points.length / 2;
  // Winding: positive area means the inside is to the left of each edge (y down).
  let area = 0;
  for (let i = 0; i < n; i += 1) {
    const j = (i + 1) % n;
    area += points[i * 2] * points[j * 2 + 1] - points[j * 2] * points[i * 2 + 1];
  }
  const side = area > 0 ? 1 : -1;
  const out: number[] = [];
  for (let i = 0; i < n; i += 1) {
    const h = (i + n - 1) % n;
    const j = (i + 1) % n;
    // Inward normals of the edges before and after corner i.
    let ax = points[i * 2] - points[h * 2];
    let ay = points[i * 2 + 1] - points[h * 2 + 1];
    let la = Math.hypot(ax, ay);
    let bx = points[j * 2] - points[i * 2];
    let by = points[j * 2 + 1] - points[i * 2 + 1];
    const lb = Math.hypot(bx, by);
    // A degenerate corner (a piece squeezed edge-on): keep the corner rather than divide by zero.
    if (!(la > 1e-6) || !(lb > 1e-6)) {
      out.push(points[i * 2], points[i * 2 + 1]);
      continue;
    }
    ax /= la;
    ay /= la;
    bx /= lb;
    by /= lb;
    const nax = -ay * side;
    const nay = ax * side;
    const nbx = -by * side;
    const nby = bx * side;
    // Corner of the two moved edges: along the bisector by d / cos(half the turn).
    const mx = nax + nbx;
    const my = nay + nby;
    la = mx * nax + my * nay;
    if (!(la > 1e-3)) {
      out.push(points[i * 2], points[i * 2 + 1]);
      continue;
    }
    out.push(points[i * 2] + (mx * d) / la, points[i * 2 + 1] + (my * d) / la);
  }
  return out;
}

// autoPlay (s): pieces are set one after another, the figure fills, turns into the swan with its
// pieces, holds, and comes apart back to the cat's empty outline.
export const LOOP = 13;
export const PLACE_FROM = 0.8;
export const PLACE_EVERY = 0.74;
export const PLACE_TIME = 0.58;
export const SOLVED_AT = PLACE_FROM + PLACE_EVERY * 6 + PLACE_TIME;
export const MORPH_AT = 7.9;
export const RESET_AT = 10.3;
export const MORPH_TIME = 1.2;
/** The silhouette the loop morphs into once the cat is done. */
export const AUTO_SECOND = 3;
