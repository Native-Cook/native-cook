// Every figure is the same RINGS ellipses in a different arrangement, so one figure can turn into
// any other: each ellipse travels from its place in the one to its place in the other.

/** Number of ellipses in a figure. */
export const RINGS = 24;
/**
 * One ellipse: x and y of its centre from the centre of the artwork, width, height, opacity, and
 * how much it takes part in the turning (1 for a meridian of a sphere, whose width follows the turn).
 */
export type Ring = number[];
export type Figure = Ring[];

export type Page = {
  title: string;
  /** What the page shows when it arrives. */
  figure: Figure;
  /** What that turns into a moment later, if anything. */
  settled?: Figure;
};

/** A change of page: the rings travel for MOVE seconds, the texts for TEXT_MOVE. */
export const MOVE = 0.9;
export const TEXT_MOVE = 0.5;
export const TEXT_SLIDE = 60;
/** A page that has a settled figure waits SETTLE_AFTER, then takes SETTLE to turn into it. */
export const SETTLE_AFTER = 0.6;
export const SETTLE = 0.9;
/** Each ring starts this share of a change after the one before it. */
export const STAGGER = 0.016;
/** A drag of this many points is a whole change; let go past COMMIT of it and the page changes. */
export const DRAG = 260;
export const COMMIT = 0.4;

// At rest the artwork breathes, and the spheres turn about their vertical axis. Both come back to
// where they started every LOOP seconds.
export const BREATH = 0.02;
export const BREATH_PERIOD = 2.5;
/** Degrees a second. */
export const SPIN = 36;
/** The spheres' meridians are not caught edge-on at rest. */
export const SPIN_START = 0.12;

/** autoPlay: next page at each of NEXT_AT, back to the first at HOME_AT, every LOOP seconds. */
export const LOOP = 10;
export const NEXT_AT = [2.3, 4.5, 7.2];
export const HOME_AT = LOOP - MOVE;
/** After a touch autoPlay waits this long before it starts over from the first page. */
export const USER_HOLD = 3;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function ease(t: number) {
  'worklet';
  const u = clamp01(t);
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}

/** How far ring `k` is through a change that is `t` of the way: the rings leave one after another. */
export function share(t: number, k: number) {
  'worklet';
  return ease((t - k * STAGGER) / (1 - (RINGS - 1) * STAGGER));
}

/** How long a run to a figure and on to its settled form takes. */
export function runLength(settles: boolean) {
  'worklet';
  return settles ? MOVE + SETTLE_AFTER + SETTLE : MOVE;
}

/**
 * Value `i` of ring `k`, `r` seconds into a run: first from where the rings were (`from`, a flat
 * list of RINGS × 6 numbers) to `first`, then, after a wait, from `first` to `second`.
 */
export function ringValue(from: number[], first: Figure, second: Figure, r: number, k: number, i: number) {
  'worklet';
  if (r < MOVE) {
    const start = from[k * 6 + i];
    return start + (first[k][i] - start) * share(r / MOVE, k);
  }
  const start = first[k][i];
  return start + (second[k][i] - start) * share((r - MOVE - SETTLE_AFTER) / SETTLE, k);
}

/** The turn of ring `k` about the vertical axis: the meridians of a sphere are an eighth of a half turn apart. */
export function phase(k: number) {
  'worklet';
  return (Math.floor(k / 2) * Math.PI) / 8 + (k % 2) * (Math.PI / 16);
}

/** The width a turning ring shows, as a share of its own, `clock` seconds after the block appeared. */
export function turned(k: number, spin: number, clock: number) {
  'worklet';
  return 1 - spin * (1 - Math.abs(Math.cos(SPIN_START + (SPIN * Math.PI * clock) / 180 + phase(k))));
}

export function breath(clock: number) {
  'worklet';
  return 1 + BREATH * Math.sin((2 * Math.PI * clock) / BREATH_PERIOD);
}

/** Texts of a page that is leaving (-1), staying (0) or arriving (1): opacity and offset. */
export function textShift(r: number, role: number, calm: boolean) {
  'worklet';
  const u = ease(r / TEXT_MOVE);
  if (role === 0) return { opacity: 1, y: 0 };
  const slide = calm ? 0 : TEXT_SLIDE;
  return role < 0 ? { opacity: 1 - u, y: -slide * u } : { opacity: u, y: slide * (1 - u) };
}

// The figures.

function figure(ring: (k: number) => Ring): Figure {
  return Array.from({ length: RINGS }, (_, k) => ring(k));
}

/** The opacity that makes `count` rings lying on each other read as one line of opacity `total`. */
function stacked(count: number, total: number) {
  return 1 - Math.pow(1 - total, 1 / count);
}

/** One small circle: every ring lies on it. */
export const DOT = figure(() => [0, 0, 54, 54, stacked(RINGS, 0.6), 0]);

/** Fourteen circles from 24 to 190 points, each a little higher than the one inside it: a look into a cone. */
export const CONE = figure((k) => {
  const j = Math.min(k, 13);
  const size = 24 + (166 * j) / 13;
  return [0, 13 - 2 * j, size, size, k < 14 ? 0.7 - (0.35 * j) / 13 : 0, 0];
});

/** Two wire spheres, one on the other: eight meridians each, and their outlines. */
export const SPHERES = figure((k) => {
  const y = k % 2 === 0 ? -95 : 95;
  if (k < 16) return [0, y, 190, 190, 0.5, 1];
  return [0, y, 190, 190, k < 18 ? 0.35 : 0, 0];
});

/** Three sets of eight circles, 40 to 200 points, 30 points apart. */
export const TREE_RINGS = figure((k) => {
  const j = Math.floor(k / 3);
  const size = 40 + (160 * j) / 7;
  return [0, ((k % 3) - 1) * 30, size, size, 0.6 - (0.3 * j) / 7, 0];
});

/** The same rings squashed into a lens: the middle reads as a dark almond. */
export const LENS = figure((k) => {
  const j = Math.floor(k / 3);
  return [0, ((k % 3) - 1) * 10, 260, 8 + (112 * j) / 7, 0.6 - (0.25 * j) / 7, 0];
});

/** Thirteen ellipses on the vertical axis, wide at the ends and narrow in the middle. */
export const HOURGLASS = figure((k) => {
  const j = k % 13;
  const waist = Math.abs((2 * j) / 12 - 1);
  return [0, -100 + (200 * j) / 12, 60 + 200 * waist, 16 + 54 * waist, k < 13 ? 0.55 : 0, 0];
});

/** The hourglass without its middle: two bowls. */
export const BOWLS = figure((k) => {
  const j = k % 13;
  const ring = HOURGLASS[k];
  const y = ring[1] + (j < 5 ? -14 : j > 7 ? 14 : 0);
  return [0, y, ring[2], ring[3], k < 13 && (j < 5 || j > 7) ? 0.55 : 0, 0];
});

/** Four by six ellipses that touch their neighbours, like chain mail. */
export const LATTICE = figure((k) => [((k % 4) - 1.5) * 46, (Math.floor(k / 4) - 2.5) * 30, 46, 30, 0.5, 0]);

/** Three circles side by side, eight rings on each. */
export const TRIO = figure((k) => [((k % 3) - 1) * 54, 0, 54, 54, stacked(RINGS / 3, 0.6), 0]);

/** One large, faint circle. */
export const HALO = figure(() => [0, 0, 170, 170, stacked(RINGS, 0.2), 0]);

export const PAGES: Page[] = [
  { title: 'Promotion strategy\nand development', figure: DOT, settled: CONE },
  { title: 'Rebate fulfillment', figure: SPHERES },
  { title: 'Loyalty programs', figure: TREE_RINGS, settled: LENS },
  { title: 'Sweepstakes\nand Contests', figure: HOURGLASS, settled: BOWLS },
  { title: 'Customer service', figure: LATTICE, settled: TRIO },
  { title: 'Channel Incentives', figure: HALO },
];
