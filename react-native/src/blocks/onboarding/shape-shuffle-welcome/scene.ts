// The still life: eleven pieces that keep their identity while they are rebuilt into four
// buildings. A composition gives every piece a place, a size, a turn, a shape and a colour; a
// piece it has no use for shrinks to nothing behind a neighbour.

/** A composition rests for REST seconds and takes CHANGE to become the next; four of them make LOOP. */
export const REST = 1.4;
export const CHANGE = 0.8;
export const STEP = REST + CHANGE;
export const LOOP = STEP * 4;
/** After a touch autoPlay waits this long before it goes back to the first composition and starts over. */
export const USER_HOLD = 3;

// Each piece sets off a little after the one on its left, up to DELAY late, and lands on a spring
// that carries it a few points past its place.
const DELAY = 0.12;
const LAND = { stiffness: 150, damping: 17 };
// Shapes and colours do not overshoot: they blend over BLEND seconds.
const BLEND = 0.3;
// A piece that is thrown follows an arc that takes this long.
const ARC = 0.55;
// A piece that was dragged away springs back.
const RETURN = { stiffness: 220, damping: 16 };
const RETURN_FOR = 0.8;
// The scene is this wide; pieces are placed from its middle, and from the ground up.
const SCENE = 220;

const LIGHTEST = '#f4f7fc';
const LIGHT = '#d4d8db';
const MID = '#adb1b4';
const DARK = '#858b93';
const INDIGO = '#4f46e5';
const AMBER = '#f59e0b';
const TEAL = '#14b8a6';
const CORAL = '#f9735b';

/**
 * A piece in one composition: centre x from the scene's middle, centre y above the ground, width,
 * height, turn in degrees, radius of the top corners and of the bottom ones, taper (1 brings the
 * top edge to a point: a roof), cut (1 cuts a block into four steps), how high it is thrown on
 * its way here, colour.
 */
type Spot = readonly [number, number, number, number, number, number, number, number, number, number, string];

// The pieces, back to front. `turn` is what a whole loop adds to a piece's rotation.
export const PIECES: readonly { name: string; steps: boolean; turn: number; spots: readonly [Spot, Spot, Spot, Spot] }[] = [
  {
    name: 'base',
    steps: true,
    turn: 360,
    spots: [
      [-80, 29, 58, 58, 0, 0, 0, 0, 0, 40, MID],
      [-82, 29, 58, 58, 90, 0, 0, 0, 0, 0, MID],
      [-61.5, 29, 87, 58, 180, 0, 0, 0, 0, 0, MID],
      [-76, 29, 58, 58, 270, 0, 0, 0, 1, 0, MID],
    ],
  },
  {
    name: 'backing',
    steps: false,
    turn: 360,
    spots: [
      [-22, 29, 58, 58, 0, 0, 0, 0, 0, 50, LIGHTEST],
      [-24, 29, 0, 0, 90, 0, 0, 0, 0, 0, LIGHTEST],
      [33, 58, 0, 0, 90, 0, 0, 0, 0, 0, LIGHTEST],
      [-18, 58, 0, 0, 90, 0, 0, 0, 0, 0, LIGHTEST],
    ],
  },
  {
    name: 'stairs',
    steps: true,
    turn: 360,
    spots: [
      [-22, 29, 58, 58, 0, 0, 0, 0, 1, 60, INDIGO],
      [-24, 29, 58, 58, 90, 0, 0, 0, 0, 0, LIGHT],
      [33, 58, 58, 116, 180, 0, 0, 0, 0, 0, LIGHT],
      [-18, 58, 58, 116, 180, 0, 0, 0, 0, 0, LIGHT],
    ],
  },
  {
    name: 'upper left',
    steps: false,
    turn: 360,
    spots: [
      [-22, 87, 58, 58, 0, 0, 0, 0, 0, 70, LIGHT],
      [-24, 29, 0, 0, 90, 0, 0, 0, 0, 0, LIGHT],
      [33, 58, 0, 0, 90, 0, 0, 0, 0, 0, LIGHT],
      [-18, 58, 0, 0, 90, 0, 0, 0, 0, 0, LIGHT],
    ],
  },
  {
    name: 'upper right',
    steps: false,
    turn: 360,
    spots: [
      [36, 87, 58, 58, 0, 0, 0, 0, 0, 80, LIGHTEST],
      [86, 29, 0, 0, 90, 0, 0, 0, 0, 0, LIGHT],
      [33, 58, 0, 0, 90, 0, 0, 0, 0, 0, LIGHT],
      [40, 29, 0, 0, 90, 0, 0, 0, 0, 0, DARK],
    ],
  },
  {
    name: 'front',
    steps: false,
    turn: 0,
    spots: [
      [36, 29, 58, 58, 0, 0, 0, 0, 0, 50, LIGHT],
      [86, 29, 58, 58, 90, 0, 0, 0, 0, 0, LIGHT],
      [-11, 43.5, 58, 87, 0, 29, 0, 0, 0, 0, DARK],
      [40, 29, 58, 58, 0, 0, 0, 0, 0, 0, DARK],
    ],
  },
  {
    name: 'window',
    steps: false,
    turn: 0,
    spots: [
      [36, 87, 30, 30, 0, 15, 15, 0, 0, 60, MID],
      [86, 22, 0, 0, 0, 15, 15, 0, 0, 0, MID],
      [33, 92, 0, 0, 0, 15, 15, 0, 0, 0, LIGHTEST],
      [-18, 92, 30, 30, 0, 15, 15, 0, 0, 0, LIGHTEST],
    ],
  },
  {
    name: 'roof',
    steps: false,
    turn: 360,
    spots: [
      [7, 137, 116, 42, 0, 0, 0, 1, 0, 80, MID],
      [-53, 79, 116, 42, 0, 0, 0, 1, 0, 0, DARK],
      [33, 130.5, 58, 29, 0, 29, 0, 0, 0, 0, MID],
      [66, 65, 110, 14, 0, 0, 0, 0, 0, 0, MID],
    ],
  },
  {
    name: 'accent',
    steps: false,
    turn: 0,
    spots: [
      [91, 11, 44, 22, 0, 22, 0, 0, 0, 40, AMBER],
      [31, 59, 30, 30, 0, 15, 15, 0, 0, 0, TEAL],
      [-40, 100, 30, 30, 0, 15, 15, 0, 0, 40, INDIGO],
      [95, 11, 44, 22, 0, 22, 0, 0, 0, 0, CORAL],
    ],
  },
  {
    name: 'ball',
    steps: false,
    turn: 0,
    spots: [
      [91, 44, 44, 44, 0, 22, 22, 0, 0, 60, AMBER],
      [31, 22, 44, 44, 0, 22, 22, 0, 0, 0, TEAL],
      [89, 22, 44, 44, 0, 22, 22, 0, 0, 0, MID],
      [95, 11, 0, 0, 0, 22, 22, 0, 0, 0, CORAL],
    ],
  },
  {
    name: 'bowl',
    steps: false,
    turn: 360,
    spots: [
      [-80, 69, 44, 22, 0, 0, 22, 0, 0, 50, LIGHT],
      [86, 22, 29, 44, 180, 0, 14.5, 0, 0, 0, MID],
      [-11, 30, 0, 0, 180, 0, 14.5, 0, 0, 0, DARK],
      [40, 29, 0, 0, 180, 0, 14.5, 0, 0, 0, DARK],
    ],
  },
];

export const COUNT = 4;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/** How far a spring of mass 1 has come from 0 to 1 after `t` seconds; it overshoots. */
export function spring(t: number, { stiffness, damping }: { stiffness: number; damping: number }) {
  'worklet';
  if (t <= 0) return 0;
  const w = Math.sqrt(stiffness);
  const z = damping / (2 * w);
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + ((z * w) / wd) * Math.sin(wd * t));
}

/** How much of a drag is left `t` seconds after the piece was let go. */
export function dragLeft(t: number) {
  'worklet';
  return t < 0 ? 1 : t >= RETURN_FOR ? 0 : 1 - spring(t, RETURN);
}

/** A piece `e` seconds into the change from composition `from` to composition `to`. */
export function pieceAt(index: number, from: number, to: number, e: number) {
  'worklet';
  const piece = PIECES[index];
  const a = piece.spots[from];
  const b = piece.spots[to];
  const since = e - DELAY * clamp01((b[0] + SCENE / 2) / SCENE);
  // Places, sizes and turns land on the spring; shapes and colours blend.
  const p = from === to ? 1 : spring(since, LAND);
  const q = from === to ? 1 : smooth(since, 0, BLEND);
  const mix = (from: number, to: number, by: number) => from + (to - from) * by;
  // A loop's last change brings the rotation round to where it started.
  const turned = b[4] + (to < from ? piece.turn : 0);
  return {
    x: mix(a[0], b[0], p),
    y: mix(a[1], b[1], p) + (from === to ? 0 : b[9] * Math.sin(Math.PI * clamp01(since / ARC))),
    w: Math.max(0, mix(a[2], b[2], p)),
    h: Math.max(0, mix(a[3], b[3], p)),
    rotation: mix(a[4], turned, p),
    top: mix(a[5], b[5], q),
    bottom: mix(a[6], b[6], q),
    taper: mix(a[7], b[7], q),
    cut: mix(a[8], b[8], q),
    blend: q,
  };
}

// A quarter circle as one curve.
const K = 0.5523;

/**
 * The outline of a piece round its own centre. A block is a four-cornered shape whose top edge
 * narrows with `taper` and whose corners are rounded as far as its sides allow; a stepped block
 * is a rectangle whose upper right is cut away in four steps, as deep as `cut`.
 */
export function outline(steps: boolean, w: number, h: number, top: number, bottom: number, taper: number, cut: number) {
  'worklet';
  const l = -w / 2;
  const t = -h / 2;
  if (steps) {
    let d = `M${l} ${-t}L${l} ${t}`;
    for (let i = 1; i <= 4; i++) {
      const x = l + (w * i) / 4;
      d += `L${x} ${t + ((h * (i - 1)) / 4) * cut}`;
      if (i < 4) d += `L${x} ${t + ((h * i) / 4) * cut}`;
    }
    return `${d}L${-l} ${-t}Z`;
  }
  const inset = (w / 2) * taper;
  const xs = [l + inset, -l - inset, -l, l];
  const ys = [t, t, -t, -t];
  const radii = [top, top, bottom, bottom];
  let d = '';
  for (let i = 0; i < 4; i++) {
    const before = (i + 3) % 4;
    const after = (i + 1) % 4;
    const toBefore = Math.hypot(xs[before] - xs[i], ys[before] - ys[i]);
    const toAfter = Math.hypot(xs[after] - xs[i], ys[after] - ys[i]);
    // Two corners share a side in proportion to their radii when it is too short for both.
    const shareBefore = radii[i] + radii[before] > toBefore ? toBefore / (radii[i] + radii[before]) : 1;
    const shareAfter = radii[i] + radii[after] > toAfter ? toAfter / (radii[i] + radii[after]) : 1;
    const r = radii[i] * Math.min(shareBefore, shareAfter);
    const fb = toBefore > 0 ? r / toBefore : 0;
    const fa = toAfter > 0 ? r / toAfter : 0;
    const ax = xs[i] + (xs[before] - xs[i]) * fb;
    const ay = ys[i] + (ys[before] - ys[i]) * fb;
    const bx = xs[i] + (xs[after] - xs[i]) * fa;
    const by = ys[i] + (ys[after] - ys[i]) * fa;
    d += `${i === 0 ? 'M' : 'L'}${ax} ${ay}C${ax + (xs[i] - ax) * K} ${ay + (ys[i] - ay) * K} ${bx + (xs[i] - bx) * K} ${by + (ys[i] - by) * K} ${bx} ${by}`;
  }
  return `${d}Z`;
}
