// Every number the stack uses. Swift and Kotlin are ported from this file.

/** Seconds. One autoPlay loop; rest at both ends (0–1.0 and 7.85–9, 24 %). */
export const LOOP = 9;

/** autoPlay: 2 s after the last touch it eases to rest and restarts at t = 0. */
export const RESUME_AFTER = 2;

/** The loop's events, in seconds. */
export const T = {
  ghostIn: 0.55,
  tapOpen: 1.0, // finger lifts on the stack
  expand: 1.05,
  swipeStart: 2.55, // finger down on the first card, drags left
  swipeEnd: 3.15, // finger lets go past the threshold
  flingSpan: 0.24,
  gap: 3.3, // the rest close the gap
  tapLess: 4.55, // finger lifts on Show less
  collapse: 4.6,
  peekStart: 5.75, // finger down on the stack, drags down
  peekRelease: 6.45,
  arrive: 7.25, // the cleared message comes back as a new one on top
  arriveSpan: 0.5,
  rest: 7.85,
} as const;

/** Card geometry, in points. */
export const CARD_H = 84;
export const GAP = 8;
/** Collapsed: each card behind peeks this far below the one in front, and is this much narrower. */
export const PEEK = 9;
export const DEPTH_SCALE = 0.05;
export const MAX_DEPTH = 2;
/** The group header (title, count, Show less, clear) that the expanded list makes room for. */
export const HEADER_H = 44;
export const RADIUS = 20;
export const MAX_CARDS = 4;

/** Fan-out: a spring per card, 50 ms apart from the top; restack: the bottom card leads. */
export const EXPAND_SPRING = { response: 0.5, ratio: 0.74 };
export const COLLAPSE_SPRING = { response: 0.42, ratio: 0.86 };
export const STAGGER = 0.05;
export const GAP_SPRING = { response: 0.42, ratio: 0.8 };
export const PUSH_SPRING = { response: 0.45, ratio: 0.8 };
export const SWIPE_SPRING = { response: 0.38, ratio: 0.82 };
export const PEEK_SPRING = { response: 0.38, ratio: 0.62 };

/** Swipe to clear. */
export const SWIPE_DRAG = 150; // pt the ghost finger drags before letting go
export const CLEAR_W = 84; // the Clear button when a card rests open
export const FLING_FRACTION = 0.42; // of the card's width
export const FLING_VELOCITY = 900; // pt/s
export const OPEN_FRACTION = 0.6; // of CLEAR_W: released past this, the card stays open
export const RUBBER_RIGHT = 0.3;

/** Drag down on the collapsed stack. */
export const PEEK_MAX = 0.32; // share of the full fan-out a drag can show
export const PEEK_RANGE = 150; // pt for most of it
export const PEEK_OPEN_DISTANCE = 110;
export const PEEK_OPEN_VELOCITY = 700;
export const GHOST_PEEK = 64; // pt the ghost finger drags down

/** The cleared message's return: drops 22 pt onto the top of the stack. */
export const ARRIVE_DROP = 22;
export const ARRIVE_SCALE = 0.03;

export function springConfig(s: { response: number; ratio: number }) {
  'worklet';
  const stiffness = (2 * Math.PI / s.response) ** 2;
  return { stiffness, damping: 2 * s.ratio * Math.sqrt(stiffness), mass: 1 };
}

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function mix(u: number, a: number, b: number) {
  'worklet';
  return a + (b - a) * u;
}

export function seg(t: number, start: number, span: number) {
  'worklet';
  return clamp01((t - start) / span);
}

export function easeOutCubic(x: number) {
  'worklet';
  const u = 1 - x;
  return 1 - u * u * u;
}

export function easeInCubic(x: number) {
  'worklet';
  return x * x * x;
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - (-2 * x + 2) ** 3 / 2;
}

/** The closed-form step response of the springs above, so autoPlay is a pure function of t. */
export function springAt(t: number, s: { response: number; ratio: number }) {
  'worklet';
  if (t <= 0) return 0;
  const w = (2 * Math.PI) / s.response;
  const wd = w * Math.sqrt(1 - s.ratio * s.ratio);
  return 1 - Math.exp(-s.ratio * w * t) * (Math.cos(wd * t) + ((s.ratio * w) / wd) * Math.sin(wd * t));
}

/** Collapsed: the top of a card `depth` places back (scaled about its centre so its bottom edge peeks). */
export function collapsedY(depth: number) {
  'worklet';
  const d = depth < 0 ? 0 : depth > MAX_DEPTH ? MAX_DEPTH : depth;
  const k = 1 - DEPTH_SCALE * d;
  return CARD_H + d * PEEK - (CARD_H * (1 + k)) / 2;
}

/** Expanded: the top of the card in list slot `slot`, under the header. */
export function expandedY(slot: number) {
  'worklet';
  return HEADER_H + slot * (CARD_H + GAP);
}

/** How tall the group stands for `count` cards (fractional while one leaves) at fan-out `e`. */
export function groupHeight(e: number, count: number) {
  'worklet';
  if (count <= 0) return 0;
  const n = count;
  const closed = n < 1 ? n * CARD_H : CARD_H + Math.min(n - 1, MAX_DEPTH) * PEEK;
  const open = n < 1 ? n * (HEADER_H + CARD_H) : HEADER_H + n * CARD_H + (n - 1) * GAP;
  return mix(e, closed, open);
}

/** Pulling down on the stack: most of PEEK_MAX within PEEK_RANGE, never past it. */
export function peekFromDrag(dy: number) {
  'worklet';
  if (dy <= 0) return 0;
  return PEEK_MAX * (1 - Math.exp(-dy / PEEK_RANGE));
}

/** Past the right edge the card follows the finger less and less. */
export function rubberRight(x: number) {
  'worklet';
  if (x <= 0) return x;
  return (x * RUBBER_RIGHT) / (1 + x / 220);
}
