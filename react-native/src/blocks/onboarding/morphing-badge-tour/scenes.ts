// The artwork is one cast of PIECES shapes. Every scene gives each of them a place, a size, a
// corner radius and two colours, so a change of scene never cuts: each shape travels and turns
// into what it is in the next scene.

/** A piece in a scene: x, y (from the stage's centre), width, height, corner radius, opacity,
 * the two colours of its gradient (0xRRGGBB), the gradient's direction (0 down, 1 across) and the
 * opacity of its glyph. */
export type Piece = number[];
export type Scene = Piece[];

export const FIELDS = 10;
const [X, Y, W, H, RADIUS] = [0, 1, 2, 3, 4];
const [COLOR_A, COLOR_B] = [6, 7];

// The cast, by place in a scene.
export const CAPSULE = 0;
export const TOKEN = 1;
export const TOKENS = 8;
export const EXTRA = 9;
export const EXTRAS = 10;
export const HERO = 19;
export const SPARK = 20;
export const SPARKS = 3;
export const DIAMOND = 23;
export const OUTLINE = 24;
/** Not a shape: how much the discs bob (points) and breathe (share of their size) in a scene. */
export const IDLE = 25;
export const PIECES = 26;

/** A change of scene takes MOVE seconds; the pieces start STAGGER apart and land on a spring. */
export const MOVE = 0.9;
export const STAGGER = 0.03;
export const STIFFNESS = 170;
export const DAMPING = 15.6;
/** The spring is taken as settled after this long. */
export const SETTLE = 0.62;
/** Opacity, colours and glyphs change over this long, without a bounce. */
export const BLEND = 0.45;
/** From the last scene to the first, everything first leaves the stage: this long. */
export const LEAVE = 0.4;
export const LEAVE_STAGGER = 0.01;
/** The caption fades out in the first half of CAPTION and the new one in over the second. */
export const CAPTION = 0.5;
export const CAPTION_SHIFT = 8;
/** A swipe further than this goes to the next or the previous scene. */
export const SWIPE = 40;

// At rest the discs bob or breathe and the sparkles pulse. The periods divide LOOP.
export const BOB_PERIODS = [2.5, 2, 10 / 3];
export const BREATH_PERIOD = 2;
export const PULSE_PERIOD = 1.25;

/** autoPlay: next scene at each of NEXT_AT, every LOOP seconds; the last one leads back to the first. */
export const LOOP = 10;
export const NEXT_AT = [2, 4.2, 6.2, 8.6];
/** After a touch autoPlay waits this long before it starts over from the first scene. */
export const USER_HOLD = 3;

export const BACKGROUND = 0x0e0d10;
const GREEN = 0x78ca4c;
const MINT = 0x02ff9b;
const SKY = 0x90d4f4;
const BLUE = 0x0f79fc;
const ROYAL = 0x1f3dff;
const MAGENTA = 0xd91efd;
const VIOLET = 0xc586f9;
const YELLOW = 0xffd22d;
const LEMON = 0xffe543;
const ORANGE = 0xde9458;
const LEAF = 0x32b951;
const CYAN = 0x35d7e6;
export const SPARKLE = 0xffa62b;
export const HERO_RING = ROYAL;

/** The two colours of each token, and whether its glyph is white (1) or black (0). */
const TOKEN_INKS = [
  [GREEN, MINT, 0],
  [MAGENTA, BLUE, 1],
  [SKY, LEAF, 0],
  [LEMON, MINT, 0],
  [YELLOW, ORANGE, 1],
  [BLUE, MINT, 1],
  [ROYAL, SKY, 1],
  [VIOLET, ORANGE, 0],
];
export const GLYPH_WHITE = TOKEN_INKS.map((ink) => ink[2]);
const EXTRA_INKS = [
  [MAGENTA, VIOLET],
  [BLUE, SKY],
  [LEMON, ORANGE],
  [GREEN, LEAF],
  [ROYAL, MAGENTA],
  [MINT, BLUE],
  [VIOLET, SKY],
  [YELLOW, GREEN],
  [ORANGE, MAGENTA],
  [SKY, ROYAL],
];

function disc(x: number, y: number, size: number, a: number, b: number, alpha = 1, glyph = 0): Piece {
  return [x, y, size, size, size / 2, alpha, a, b, 0.5, glyph];
}

function plain(x: number, y: number, w: number, h: number, radius: number, alpha: number): Piece {
  return [x, y, w, h, radius, alpha, 0, 0, 0, 0];
}

/** A scene from its parts: the capsule, where the tokens and the extras are, the hero and the rest. */
function scene(parts: {
  capsule: Piece;
  tokens: (index: number) => Piece;
  extras: (index: number) => Piece;
  hero: number[];
  sparks: number[][];
  diamond: number;
  outline: number;
  idle: number[];
}): Scene {
  const pieces: Scene = [parts.capsule];
  for (let i = 0; i < TOKENS; i++) pieces.push(parts.tokens(i));
  for (let i = 0; i < EXTRAS; i++) pieces.push(parts.extras(i));
  pieces.push(plain(parts.hero[0], parts.hero[1], parts.hero[2], parts.hero[2], parts.hero[2] / 2, parts.hero[3]));
  parts.sparks.forEach(([x, y, size, alpha]) => pieces.push(plain(x, y, size, size, 0, alpha)));
  // The diamond waits at the top of the beam and drops to its foot in the last scene.
  pieces.push(plain(0, parts.diamond ? -42 : -140, 60, 60, 0, parts.diamond));
  pieces.push(plain(0, 60, 172, 122, 6, parts.outline));
  pieces.push([parts.idle[0], parts.idle[1], 0, 0, 0, 0, 0, 0, 0, 0]);
  return pieces;
}

const token = (index: number, x: number, y: number, size: number) => disc(x, y, size, TOKEN_INKS[index][0], TOKEN_INKS[index][1], 1, 1);
const extra = (index: number, x: number, y: number, size: number, alpha: number) => disc(x, y, size, EXTRA_INKS[index][0], EXTRA_INKS[index][1], alpha);

/** Where the eight badges float round the capsule. */
const FLOATING = [
  [-118, -120],
  [-22, -192],
  [122, -150],
  [-142, 12],
  [134, 24],
  [-190, -62],
  [188, -74],
  [150, -218],
];
const SPARKS_WELCOME = [
  [-92, -44, 18],
  [98, 84, 22],
  [66, -98, 14],
];
/** The grid's cells, as column and row from the middle one, which is the hero's. */
const CELLS = [
  [-1, -1],
  [0, -1],
  [1, -1],
  [-1, 0],
  [1, 0],
  [-1, 1],
  [1, 1],
  [0, 1],
];
const CELL = 88;
const PITCH = 100;
const GRID_Y = -10;
const SCATTERED = [
  [-108, -118, 56],
  [-18, -150, 44],
  [104, -128, 60],
  [-138, -22, 40],
  [118, -20, 48],
  [-112, 92, 58],
  [112, 96, 52],
  [10, 128, 36],
];
const SCATTERED_EXTRAS = [
  [-60, -70, 22],
  [52, -66, 18],
  [-70, 30, 26],
  [66, 40, 20],
  [-10, -78, 14],
  [0, 70, 16],
  [-150, -90, 18],
  [150, -70, 16],
  [-45, 150, 20],
  [150, 30, 24],
];
const WALLET_Y = 60;
const STRIPE = 37.5;
const STRIPE_INKS = [
  [VIOLET, YELLOW],
  [YELLOW, LEAF],
  [LEAF, CYAN],
  [CYAN, VIOLET],
];

/** The hero badge on a capsule, badges floating round it. */
const WELCOME = scene({
  capsule: [0, 20, 78, 150, 39, 1, 0xb7c523, YELLOW, 0, 0],
  tokens: (i) => token(i, FLOATING[i][0], FLOATING[i][1], 44),
  extras: (i) => extra(i, 0, -16, 10, 0),
  hero: [0, -16, 78, 1],
  sparks: SPARKS_WELCOME.map(([x, y, size]) => [x, y, size, 1]),
  diamond: 0,
  outline: 0,
  idle: [6, 0],
});

/** Three by three: the badges are the cells round the hero. */
const GRID = scene({
  capsule: [0, GRID_Y, 78, 0, 0, 0, 0xb7c523, YELLOW, 0, 0],
  tokens: (i) => token(i, CELLS[i][0] * PITCH, GRID_Y + CELLS[i][1] * PITCH, CELL),
  extras: (i) => extra(i, 0, GRID_Y, 10, 0),
  hero: [0, GRID_Y, CELL, 1],
  sparks: SPARKS_WELCOME.map(([x, y]) => [x, y, 0, 0]),
  diamond: 0,
  outline: 0,
  idle: [0, 0.03],
});

/** A constellation: the badges and ten smaller discs scattered round a small hero. */
const CONSTELLATION = scene({
  capsule: [0, 0, 78, 0, 0, 0, 0xb7c523, YELLOW, 0, 0],
  tokens: (i) => token(i, SCATTERED[i][0], SCATTERED[i][1], SCATTERED[i][2]),
  extras: (i) => extra(i, SCATTERED_EXTRAS[i][0], SCATTERED_EXTRAS[i][1], SCATTERED_EXTRAS[i][2], 1),
  hero: [0, 0, 40, 1],
  sparks: SPARKS_WELCOME.map(([x, y]) => [x, y, 0, 0]),
  diamond: 0,
  outline: 0,
  idle: [3, 0],
});

/** A wallet: four badges are its stripes, a fifth its clasp, and the capsule is the beam a diamond drops along. */
const WALLET = scene({
  capsule: [0, -70, 66, 110, 0, 1, 0xff8a1f, YELLOW, 0, 0],
  tokens: (i) => {
    if (i < 4) return [0, WALLET_Y + (i - 1.5) * STRIPE, 200, STRIPE, 4, 1, STRIPE_INKS[i][0], STRIPE_INKS[i][1], 1, 0];
    if (i === 4) return [88, WALLET_Y, 36, 24, 12, 1, BACKGROUND, BACKGROUND, 0.5, 1];
    return disc(0, WALLET_Y, 10, TOKEN_INKS[i][0], TOKEN_INKS[i][1], 0);
  },
  extras: (i) => extra(i, 0, WALLET_Y, 6, 0),
  hero: [0, WALLET_Y, 20, 0],
  sparks: [
    [-88, -62, 20, 1],
    [72, -24, 14, 1],
    [-58, -132, 12, 1],
  ],
  diamond: 1,
  outline: 1,
  idle: [0, 0],
});

/** Off the stage: the capsule as a tall bar under it, the badges out past its edges. */
const OFFSTAGE = scene({
  capsule: [0, 540, 78, 330, 39, 1, 0x8a3ffc, CYAN, 0, 0],
  tokens: (i) => token(i, FLOATING[i][0] * 2.6, FLOATING[i][1] * 2.6, 44),
  extras: (i) => extra(i, 0, 414, 10, 0),
  hero: [0, 414, 78, 1],
  sparks: SPARKS_WELCOME.map(([x, y]) => [x, y, 0, 0]),
  diamond: 0,
  outline: 0,
  idle: [0, 0],
});

/** The scenes in order, and after them the place everything leaves to on the way back to the first. */
export const SCENES = [WELCOME, GRID, CONSTELLATION, WALLET, OFFSTAGE];
export const COUNT = 4;
export const AWAY = 4;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function ease(t: number) {
  'worklet';
  const u = clamp01(t);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** Where a spring of mass 1 that was let go at 0 is after `t` seconds, on its way to 1. */
export function spring(t: number) {
  'worklet';
  if (t <= 0) return 0;
  if (t >= SETTLE) return 1;
  const w = Math.sqrt(STIFFNESS);
  const z = DAMPING / (2 * w);
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + ((z * w) / wd) * Math.sin(wd * t));
}

function mixColor(a: number, b: number, t: number) {
  'worklet';
  const channel = (shift: number) => Math.round(((a >> shift) & 0xff) + (((b >> shift) & 0xff) - ((a >> shift) & 0xff)) * t);
  return (channel(16) << 16) | (channel(8) << 8) | channel(0);
}

/** How long the run to scene `to` takes when it starts by leaving the stage (`away`) or not. */
export function runLength(away: boolean) {
  'worklet';
  return away ? LEAVE + MOVE : MOVE;
}

/**
 * Field `i` of piece `k`, `r` seconds into a run from where the pieces were (`from`, a flat list
 * of PIECES × FIELDS numbers) to the scene `to`. With `away` set, everything first leaves the stage.
 */
export function pieceValue(from: number[], to: number, away: boolean, r: number, k: number, i: number) {
  'worklet';
  const start = from[k * FIELDS + i];
  // The hero rides on the capsule: they leave together.
  const order = k === HERO ? 0 : k % 9;
  if (away && r < LEAVE) {
    // Leaving speeds up: nothing lands.
    const u = clamp01((r - order * LEAVE_STAGGER) / (LEAVE - 8 * LEAVE_STAGGER));
    const end = SCENES[AWAY][k][i];
    return i === COLOR_A || i === COLOR_B ? mixColor(start, end, u * u) : start + (end - start) * u * u * u;
  }
  const base = away ? SCENES[AWAY][k][i] : start;
  const end = SCENES[to][k][i];
  const t = (away ? r - LEAVE : r) - order * STAGGER;
  if (i === COLOR_A || i === COLOR_B) return mixColor(base, end, ease(t / BLEND));
  // Places and sizes land on the spring; the rest blends.
  const landing = i === X || i === Y || i === W || i === H || i === RADIUS;
  return base + (end - base) * (landing && k !== IDLE ? spring(t) : ease(t / BLEND));
}

/** How far up a bobbing disc is, `clock` seconds after the block appeared. */
export function bob(k: number, amount: number, clock: number) {
  'worklet';
  return amount * Math.sin((2 * Math.PI * clock) / BOB_PERIODS[k % 3] + k * 1.7);
}

/** The size of a breathing disc at (x, y): the breath runs across the grid as a diagonal wave. */
export function breathe(x: number, y: number, amount: number, clock: number) {
  'worklet';
  return 1 + amount * (0.5 + 0.5 * Math.sin((2 * Math.PI * clock) / BREATH_PERIOD - (x + y) * 0.006));
}

export function pulse(index: number, clock: number) {
  'worklet';
  return 0.8 + 0.2 * Math.sin((2 * Math.PI * clock) / PULSE_PERIOD + index * 2.1);
}

/** The caption that leaves (`leaving`) or arrives, `r` seconds into a run: opacity and offset down. */
export function captionShift(r: number, leaving: boolean, calm: boolean) {
  'worklet';
  const half = CAPTION / 2;
  const shift = calm ? 0 : CAPTION_SHIFT;
  if (leaving) {
    const u = clamp01(r / half);
    return { opacity: 1 - u, y: shift * u };
  }
  const u = clamp01((r - half) / half);
  return { opacity: u, y: shift * (1 - u) };
}

// The glyphs, in a box from -1 to 1. A part is a polygon (0, then its points), a rounded
// rectangle (1: x, y, width, height, radius) or a circle (2: x, y, radius).
export const GLYPHS: number[][][] = [
  // A diamond.
  [[0, 0, -1, 0.85, 0, 0, 1, -0.85, 0]],
  // Three bars.
  [
    [1, -0.9, -0.85, 1.8, 0.42, 0.21],
    [1, -0.9, -0.21, 1.8, 0.42, 0.21],
    [1, -0.9, 0.43, 1.8, 0.42, 0.21],
  ],
  // A face: two eyes and a mouth.
  [
    [2, -0.45, -0.3, 0.24],
    [2, 0.45, -0.3, 0.24],
    [1, -0.6, 0.3, 1.2, 0.3, 0.15],
  ],
  // A star with four points.
  [[0, 0, -1, 0.25, -0.25, 1, 0, 0.25, 0.25, 0, 1, -0.25, 0.25, -1, 0, -0.25, -0.25]],
  // A coin, which is the dot of the wallet's clasp.
  [[2, 0, 0, 0.42]],
  // Two arrows, one up and one down.
  [
    [0, -0.45, -0.95, -0.9, -0.25, -0.6, -0.25, -0.6, 0.9, -0.3, 0.9, -0.3, -0.25, 0, -0.25],
    [0, 0.45, 0.95, 0.9, 0.25, 0.6, 0.25, 0.6, -0.9, 0.3, -0.9, 0.3, 0.25, 0, 0.25],
  ],
  // A lightning bolt.
  [[0, 0.2, -1, -0.6, 0.15, -0.05, 0.15, -0.25, 1, 0.6, -0.2, 0.05, -0.2]],
  // A speech bubble.
  [
    [1, -0.9, -0.75, 1.8, 1.2, 0.4],
    [0, -0.45, 0.3, -0.05, 0.3, -0.5, 0.95],
  ],
];
/** A glyph is this share of its disc's size, from its middle to its edge. */
export const GLYPH_SCALE = 0.27;

/** A sparkle: a star with four thin points, in a box from -1 to 1. */
export const SPARK_SHAPE = [0, -1, 0.18, -0.18, 1, 0, 0.18, 0.18, 0, 1, -0.18, 0.18, -1, 0, -0.18, -0.18];

/** The diamond's facets in a box from -1 to 1, back to front: a colour, then the points. */
export const DIAMOND_FACETS = [
  [BLUE, -1, -0.35, -0.55, -0.85, 0.55, -0.85, 1, -0.35, 0, 1],
  [SKY, -1, -0.35, -0.55, -0.85, 0.55, -0.85, 1, -0.35],
  [ROYAL, -0.4, -0.35, 0.4, -0.35, 0, 1],
  [0xddf1ff, -0.2, -0.85, 0.2, -0.85, 0.4, -0.35, -0.4, -0.35],
];
