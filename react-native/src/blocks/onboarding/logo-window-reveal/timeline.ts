// The show as a function of its time `t`, in seconds: the mark, the flight through its window
// into the photo, the photo's fall into a list of cards, the list's zoom out into three columns,
// and the last row left hanging over the name. Nothing is stored per frame.

/** autoPlay runs the show every LOOP seconds. By hand it stops at END, with everything in place. */
export const LOOP = 11;
export const SWELL_AT = 0.6;
const FLY_AT = 1.0;
const HERO_AT = 1.7;
export const SHRINK_AT = 2.7;
export const LIST_AT = 3.4;
const ZOOM_AT = 6.4;
export const HANG_AT = 8.2;
export const END = 9.6;
export const CLOSE_AT = 10.3;
/** After a touch autoPlay waits this long before it closes the show and starts over. */
export const USER_HOLD = 3;
/** A list that was scrolled by hand goes on by itself this long after the finger left. */
export const SCROLL_HOLD = 2;

// The mark: a disc with a rounded window cut out, a little right of its centre.
export const MARK = 68;
const WINDOW = { width: 26, height: 30, radius: 9, dx: 2 };
// It swells with a grey halo behind it, then grows until the window has swallowed the screen.
const SWELL = 1.9;
const FLY_OVER = 1.25;
const FLY_POWER = 1.6;
export const HALO = 1.2;
const HALO_OPACITY = 0.5;
// The photo shows through the window from the first moment of the swell.
const PHOTO_FAINT = 0.2;
const PHOTO_FULL_AT = 0.6;
/** The hero photo is asked for this tall, so that it covers the screen and the card with its whole width. */
export const HERO_ASPECT = 0.4;

// The hero's two lines rise into place one after the other, and leave when the photo shrinks.
const TEXT_IN = 0.4;
const TEXT_GAP = 0.08;
export const TEXT_RISE = 20;
const TEXT_OUT = 0.25;

// The list: square cards one under the other. The photo lands as the second one.
export const CARD = 330;
const GAP = 18;
const PITCH = CARD + GAP;
export const CARD_RADIUS = 22;
export const HERO = 1;
const LAND = { stiffness: 170, damping: 20 };
// Its neighbours come in from above and below, from this far.
const SLIDE = 420;
const CARD_TEXT_IN = 0.3;
// The list scrolls by itself at this speed, in points a second, after a short run-up.
const SPEED = 230;
const RUN_UP = 0.3;
// Zoom out: the column shrinks to this scale about the middle of the screen while the scroll
// slows to a stop, and a column fades in on each side, half a card higher.
const ZOOM = 0.4;
const ZOOM_FOR = HANG_AT - ZOOM_AT;
const SIDE_IN = 0.6;
/** Rows in a column; the side columns have no first row. */
export const ROWS = 8;
// The end: the grid runs up until its last row hangs at the top, the middle card larger.
const HANG_FOR = 1;
const HUNG = { size: 154, y: 140 };
const HUNG_SIDE = { size: 105, x: 40.5 };
// The name's letters come in one after the other, then the line under it.
const NAME_AFTER = 0.5;
const LETTER_GAP = 0.04;
const LETTER_IN = 0.3;
export const LETTER_RISE = 10;
const TAGLINE_AFTER = 0.9;
const TAGLINE_IN = 0.4;
// Closing: the hanging cards leave upwards, the name shrinks into the middle and the mark takes its place.
const LEAVE = 260;
const LEAVE_FOR = 0.4;
const NAME_OUT = 0.3;
export const NAME_SHRINK = 0.6;
const MARK_AFTER = 0.2;
const MARK_IN = 0.35;
// Reduced motion: the mark and the end fade into each other.
const STILL_IN = 1;
const STILL_FADE = 0.4;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

function easeOut(t: number) {
  'worklet';
  return 1 - (1 - t) * (1 - t) * (1 - t);
}

function easeInOut(t: number) {
  'worklet';
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

/** How far a spring of mass 1 has come from 0 to 1 after `t` seconds; it overshoots a little. */
function spring(t: number, { stiffness, damping }: { stiffness: number; damping: number }) {
  'worklet';
  if (t <= 0) return 0;
  const w = Math.sqrt(stiffness);
  const z = damping / (2 * w);
  const wd = w * Math.sqrt(1 - z * z);
  return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + ((z * w) / wd) * Math.sin(wd * t));
}

/** Reduced motion shows two pictures, the mark and the end: how much of the end at `t`. */
export function stillEnd(t: number) {
  'worklet';
  return smooth(t, STILL_IN, STILL_IN + STILL_FADE) * (1 - smooth(t, CLOSE_AT, CLOSE_AT + STILL_FADE));
}

/** The mark: its scale, and how much of it and of its halo shows. */
export function mark(t: number, width: number, height: number) {
  'worklet';
  if (t >= CLOSE_AT) {
    const u = clamp01((t - CLOSE_AT - MARK_AFTER) / MARK_IN);
    return { scale: NAME_SHRINK + (1 - NAME_SHRINK) * easeOut(u), opacity: u, halo: 0 };
  }
  if (t >= HERO_AT) return { scale: 1, opacity: 0, halo: 0 };
  if (t < SWELL_AT) return { scale: 1, opacity: 1, halo: 0 };
  if (t < FLY_AT) {
    const u = (t - SWELL_AT) / (FLY_AT - SWELL_AT);
    return { scale: 1 + (SWELL - 1) * easeInOut(u), opacity: 1, halo: HALO_OPACITY * smooth(u, 0, 0.5) };
  }
  // Each moment multiplies the size by the same factor, so the flight feels even, after a slow start.
  const u = (t - FLY_AT) / (HERO_AT - FLY_AT);
  const full = Math.max(width / WINDOW.width, height / WINDOW.height) * FLY_OVER;
  return { scale: SWELL * Math.pow(full / SWELL, Math.pow(u, FLY_POWER)), opacity: 1, halo: HALO_OPACITY * (1 - smooth(u, 0, 0.3)) };
}

/** The window of a mark of that scale, as a box round its centre. */
export function windowOf(scale: number, width: number, height: number) {
  'worklet';
  return { cx: width / 2 + WINDOW.dx * scale, cy: height / 2, w: WINDOW.width * scale, h: WINDOW.height * scale, r: WINDOW.radius * scale };
}

/** The hero photo: the box it is seen through, the scale of the photo behind it, and how much shows. */
export function hero(t: number, width: number, height: number) {
  'worklet';
  if (t < HERO_AT) {
    const box = windowOf(mark(t, width, height).scale, width, height);
    const opacity = t < SWELL_AT ? 0 : t < FLY_AT ? (PHOTO_FAINT * (t - SWELL_AT)) / (FLY_AT - SWELL_AT) : PHOTO_FAINT + (1 - PHOTO_FAINT) * clamp01((t - FLY_AT) / (HERO_AT - FLY_AT) / PHOTO_FULL_AT);
    return { ...box, k: 1, opacity };
  }
  if (t >= LIST_AT) return { cx: width / 2, cy: height / 2, w: CARD, h: CARD, r: CARD_RADIUS, k: CARD / width, opacity: 0 };
  const p = t < SHRINK_AT ? 0 : spring(t - SHRINK_AT, LAND);
  const w = width + (CARD - width) * p;
  return { cx: width / 2, cy: height / 2, w, h: height + (CARD - height) * p, r: CARD_RADIUS * clamp01(p), k: w / width, opacity: 1 };
}

/** One of the hero's two lines: how much shows, and how far under its place it is. */
export function heroLine(t: number, line: number) {
  'worklet';
  const u = clamp01((t - HERO_AT - line * TEXT_GAP) / TEXT_IN);
  return { opacity: u * (1 - smooth(t, SHRINK_AT, SHRINK_AT + TEXT_OUT)), rise: TEXT_RISE * (1 - easeOut(u)) };
}

/** How far the list has scrolled. `extra` is what a finger added; the end ignores it. */
function scroll(t: number, height: number, extra: number) {
  'worklet';
  const start = PITCH * HERO + CARD / 2 - height / 2;
  const run = Math.max(0, Math.min(t, ZOOM_AT) - LIST_AT);
  const slow = clamp01((t - ZOOM_AT) / ZOOM_FOR) * ZOOM_FOR;
  const auto =
    start +
    SPEED * (run < RUN_UP ? (run * run) / (2 * RUN_UP) : run - RUN_UP / 2) +
    SPEED * (slow - (slow * slow) / (2 * ZOOM_FOR));
  // Kept between the first card and the last in the middle of the screen.
  const free = Math.min(Math.max(auto + extra, start - PITCH), start + PITCH * (ROWS - 1 - HERO));
  const end = PITCH * (ROWS - 1) + CARD / 2 - height / 2 + (height / 2 - HUNG.y) / ZOOM;
  return free + (end - free) * easeInOut(clamp01((t - HANG_AT) / HANG_FOR));
}

/** Where a card is: `column` -1, 0 or 1, `row` from the top. */
export function place(column: number, row: number, t: number, width: number, height: number, extra: number) {
  'worklet';
  if (t < SHRINK_AT || (column !== 0 && t < ZOOM_AT) || (column === 0 && row === HERO && t < LIST_AT)) {
    return { x: width / 2, y: -CARD, scale: 1, opacity: 0, text: 0 };
  }
  const z = 1 + (ZOOM - 1) * easeInOut(clamp01((t - ZOOM_AT) / ZOOM_FOR));
  const inList = row * PITCH + CARD / 2 - (column === 0 ? 0 : PITCH / 2);
  let x = width / 2 + column * PITCH * z;
  let y = height / 2 + (inList - scroll(t, height, extra) - height / 2) * z;
  let scale = z;
  if (t < LIST_AT) y += (row < HERO ? -1 : 1) * SLIDE * (1 - spring(t - SHRINK_AT, LAND));
  if (row === ROWS - 1 && t > HANG_AT) {
    const p = easeInOut(clamp01((t - HANG_AT) / HANG_FOR));
    scale = z + ((column === 0 ? HUNG.size : HUNG_SIDE.size) / CARD - z) * p;
    x += column * (width / 2 - HUNG_SIDE.x - PITCH * z) * p;
  }
  if (t > CLOSE_AT) {
    const u = clamp01((t - CLOSE_AT) / LEAVE_FOR);
    y -= LEAVE * u * u;
  }
  return {
    x,
    y,
    scale,
    opacity: column === 0 ? 1 : smooth(t, ZOOM_AT, ZOOM_AT + SIDE_IN),
    // The landed photo gets its card's lines a moment later.
    text: column === 0 && row === HERO ? smooth(t, LIST_AT, LIST_AT + CARD_TEXT_IN) : 1,
  };
}

/** A letter of the name: how much shows, and how far under its place it is. */
export function letter(t: number, index: number) {
  'worklet';
  const u = clamp01((t - HANG_AT - NAME_AFTER - index * LETTER_GAP) / LETTER_IN);
  return { opacity: u, rise: LETTER_RISE * (1 - easeOut(u)) };
}

/** The name as a whole while it closes: how much shows, and its scale. */
export function nameOut(t: number) {
  'worklet';
  const u = smooth(t, CLOSE_AT, CLOSE_AT + NAME_OUT);
  return { opacity: 1 - u, scale: 1 + (NAME_SHRINK - 1) * u };
}

export function tagline(t: number) {
  'worklet';
  return smooth(t, HANG_AT + TAGLINE_AFTER, HANG_AT + TAGLINE_AFTER + TAGLINE_IN) * (1 - smooth(t, CLOSE_AT, CLOSE_AT + NAME_OUT));
}

/** The mark of a given scale as one path: the disc, and the window as a hole in it. */
export function markPath(scale: number, width: number, height: number) {
  'worklet';
  const cx = width / 2;
  const cy = height / 2;
  const R = (MARK / 2) * scale;
  const { cx: wx, w, h, r } = windowOf(scale, width, height);
  const l = wx - w / 2;
  const t = cy - h / 2;
  return (
    `M${cx - R} ${cy}A${R} ${R} 0 1 0 ${cx + R} ${cy}A${R} ${R} 0 1 0 ${cx - R} ${cy}Z` +
    `M${l + r} ${t}H${l + w - r}A${r} ${r} 0 0 1 ${l + w} ${t + r}V${t + h - r}A${r} ${r} 0 0 1 ${l + w - r} ${t + h}` +
    `H${l + r}A${r} ${r} 0 0 1 ${l} ${t + h - r}V${t + r}A${r} ${r} 0 0 1 ${l + r} ${t}Z`
  );
}
