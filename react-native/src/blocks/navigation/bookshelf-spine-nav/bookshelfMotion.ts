// Timeline and poses for the Bookshelf Spine Nav. All of it runs on the UI thread: every helper
// is a named worklet declared above its callers.

import { BOOKS } from './bookshelfData';

/** Loop period in seconds. */
export const LOOP = 9;
/** Length of the pull-and-open sequence, in seconds of "open time" τ. */
export const OPEN = 2.6;

/** The design box (393 × 759) and the shelf inside it. */
export const DESIGN_W = 393;
export const DESIGN_H = 759;
/** Top of the shelf board: every book stands on it. */
export const BOARD_Y = 700;
const GAP = 2;
const ROW = BOOKS.reduce((sum, b) => sum + b.w, 0) + GAP * (BOOKS.length - 1);
/** Left edge of each spine on the shelf. */
export const BOOK_X: number[] = BOOKS.reduce<number[]>((xs, b, i) => {
  xs.push(i === 0 ? (DESIGN_W - ROW) / 2 : xs[i - 1] + BOOKS[i - 1].w + GAP);
  return xs;
}, []);
/** Front cover width of each book (a share of its height). */
export const COVER_W: number[] = BOOKS.map((b) => Math.round(b.h * 0.64));

/** Where the pulled book is held, cover out, before it opens, and how much bigger it is there. */
export const HOLD = { x: DESIGN_W / 2, y: 282, scale: 1.48 };
/** The section page the open book becomes. */
export const PAGE = { x: 16, y: 102, w: DESIGN_W - 32, h: 364 };

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Back-out: overshoots a few per cent, then settles (for the tilt's anticipation and the lift). */
export function easeOutBack(x: number) {
  'worklet';
  const c = 1.4;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
}

/**
 * Open time τ at loop time t: rest 0–1.2 s; pull and open 1.2–3.8 s (τ 0 → 2.6); hold 3.8–5.4 s;
 * close and slide back 5.4–7.2 s (τ runs back); the books settle 7.2–8.0 s; rest.
 */
export function autoTau(t: number) {
  'worklet';
  if (t < 1.2) return 0;
  if (t < 3.8) return t - 1.2;
  if (t < 5.4) return OPEN;
  return OPEN * (1 - seg(t, 5.4, 7.2));
}

/** The settling wobble of the neighbours once the book is back, 7.2–8.6 s; zero elsewhere. */
export function autoWobble(t: number) {
  'worklet';
  if (t < 7.2 || t > 8.6) return 0;
  const u = t - 7.2;
  return Math.exp(-u * 4.2) * Math.sin(u * 15) * (1 - seg(t, 8.0, 8.6));
}

export type BookPose = {
  /** Forward tilt of the spine, degrees (negative: top toward the reader). */
  tilt: number;
  /** Turn of the book from spine-out (0) to cover-out (90). */
  turn: number;
  /** Opening of the front cover, 0 closed … 1 wide open. */
  open: number;
  dx: number;
  dy: number;
  scale: number;
  /** Opacity of the book itself (it hands over to the page at the end). */
  alpha: number;
  /** Growth of the page from the cover's rectangle (0) to the full page (1), and its text's opacity. */
  grow: number;
  text: number;
  /** How far the neighbours lean into the gap, 0 … 1. */
  gap: number;
};

/** The pulled book at open time τ (0 … OPEN). */
export function bookPose(tau: number, index: number): BookPose {
  'worklet';
  const b = BOOKS[index];
  // Tip out with a small anticipation: 0–0.5 s.
  const tip = easeOutBack(seg(tau, 0, 0.5));
  // Slide out of the shelf, up and toward the middle: 0.4–1.6 s.
  const lift = easeInOutCubic(seg(tau, 0.4, 1.6));
  // Turn to show the cover: 1.0–1.6 s. Open the cover: 1.6–2.2 s.
  const turn = 90 * easeInOutCubic(seg(tau, 1.0, 1.6));
  const open = easeInOutCubic(seg(tau, 1.6, 2.25));
  const tilt = -16 * tip * (1 - easeInOutCubic(seg(tau, 0.6, 1.2)));
  const scale = 1 + (HOLD.scale - 1) * lift;
  // Once turned, the cover is centred on HOLD: its centre sits (w / 2 + cw / 2, -h / 2) from the
  // spine's bottom middle, the transform origin.
  const cw = Math.round(b.h * 0.64);
  const ox = BOOK_X[index] + b.w / 2;
  const oy = BOARD_Y;
  const dx = (HOLD.x - (ox + HOLD.scale * (b.w / 2 + cw / 2))) * lift;
  const dy = (HOLD.y - (oy - HOLD.scale * (b.h / 2))) * lift - 10 * tip * (1 - lift);
  const grow = easeInOutCubic(seg(tau, 1.9, 2.6));
  const text = seg(tau, 2.25, 2.6);
  const alpha = 1 - seg(tau, 2.3, 2.55);
  const gap = easeOutCubic(seg(tau, 0.35, 1.1));
  return { tilt, turn, open, dx, dy, scale, alpha, grow, text, gap };
}

/** How a book on the shelf leans when the book `active` is out: toward the gap, about its bottom corner. */
export function neighbourLean(index: number, active: number, gap: number, wobble: number) {
  'worklet';
  const d = index - active;
  if (d === 0) return 0;
  const reach = Math.abs(d) === 1 ? 1 : Math.abs(d) === 2 ? 0.28 : 0;
  // Positive degrees lean right (toward a gap on the right).
  const dir = d < 0 ? 1 : -1;
  return dir * reach * (6.5 * gap + 2.6 * wobble);
}

/** Where the page starts, on the open book's cover, in the design box. */
export function coverRect(index: number) {
  'worklet';
  const b = BOOKS[index];
  const cw = Math.round(b.h * 0.64) * HOLD.scale;
  const ch = b.h * HOLD.scale;
  return { x: HOLD.x - cw / 2, y: HOLD.y - ch / 2, w: cw, h: ch };
}
