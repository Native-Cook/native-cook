import { Easing } from 'react-native-reanimated';

// The numbers, the layout and the timeline of Scatter Grid Album, the same in the three stacks.
// Every pose is a pure function of the clock, the layout and the times of the events. Every function
// here runs on the UI thread too (they are worklets, and hold no callbacks React Compiler could hoist).

/** One loop of the autoPlay, in seconds. Every drift period divides it, so the loop has no jump. */
export const LOOP = 8;
// The autoPlay's timeline, in seconds from the start of a loop: open and drifting, folded into the
// card, the heart tapped on and off, unfolded, open again.
export const FOLD_TAP = 1.2;
export const HEART_ON_TAP = 3.0;
export const HEART_OFF_TAP = 4.6;
export const UNFOLD_TAP = 5.6;
/** Seconds after the last touch before the autoPlay starts again (unfolding first if folded). */
export const USER_HOLD = 4.0;
/** The autoPlay waits for the photos, but never longer than this after the block appears. */
export const PRELOAD_WAIT = 3.0;

// MARK: Layout (points; the reference screen is 393 × 852, everything follows the safe area and width)

/** A photo lying open, before its tilt: 3:4, a little taller than wide, as in the design. */
export const PHOTO_WIDTH = 132;
export const PHOTO_HEIGHT = 176;
/** Corner radius of a photo open and in its cell; the frame morphs between. */
export const PHOTO_RADIUS_OPEN = 18;
export const PHOTO_RADIUS_CELL = 15;
/** The band the open photos lie in: from this far under the safe top to this far over the safe bottom. */
export const SCATTER_TOP = 70;
export const SCATTER_BOTTOM = 10;

/**
 * Where a photo lies open: its centre as shares of the screen's width and of the band's height, its
 * tilt in degrees, and its drift (periods in seconds, each dividing LOOP, and where each sine starts).
 */
export type Slot = {
  x: number;
  y: number;
  tilt: number;
  periodX: number;
  periodY: number;
  periodTilt: number;
  phase: number;
};

/**
 * The eight photos in grid order (row by row, skipping the centre). Each lies open on the side of the
 * screen its cell is on, as in the design: the second one (top middle cell) is the one tucked in the
 * middle, behind its neighbours; the seventh (bottom middle) lies right of centre, low.
 */
export const SLOTS: Slot[] = [
  { x: 0.24, y: 0.135, tilt: -4, periodX: 8, periodY: 4, periodTilt: 8, phase: 0.0 },
  { x: 0.5, y: 0.315, tilt: -1.5, periodX: 4, periodY: 8, periodTilt: 8, phase: 2.1 },
  { x: 0.69, y: 0.155, tilt: 4, periodX: 8, periodY: 4, periodTilt: 4, phase: 4.0 },
  { x: 0.235, y: 0.48, tilt: 3.5, periodX: 4, periodY: 8, periodTilt: 8, phase: 1.3 },
  { x: 0.78, y: 0.46, tilt: -3.5, periodX: 8, periodY: 4, periodTilt: 8, phase: 5.2 },
  { x: 0.26, y: 0.775, tilt: -5.5, periodX: 8, periodY: 8, periodTilt: 4, phase: 3.3 },
  { x: 0.61, y: 0.635, tilt: -5, periodX: 4, periodY: 4, periodTilt: 8, phase: 0.7 },
  { x: 0.715, y: 0.86, tilt: 4, periodX: 8, periodY: 4, periodTilt: 8, phase: 4.6 },
];
export const COUNT = SLOTS.length;
/** Back to front: the tucked one first, then the rest in order. */
export const DRAW_ORDER = [1, 0, 2, 3, 4, 5, 6, 7];
/** Drift amplitudes: points each way and degrees each way. */
export const DRIFT_X = 2;
export const DRIFT_Y = 3;
export const DRIFT_TILT = 0.6;

/**
 * The folded card: a square this far in from each side, its centre at this share of the screen height;
 * its corners round like the outer cells' outer corners, so they line up.
 */
export const CARD_SIDE = 20;
export const CARD_CENTER = 0.46;
export const CARD_RADIUS = 15;
/**
 * The 3×3 grid on the card: no outer padding; the columns are equal, the middle row taller (as in the
 * design, so the name has room), the top and bottom rows equal.
 */
export const GAP_X = 16;
export const GAP_Y = 12;
export const MIDDLE_ROW_SHARE = 0.43;

/** The heading open: the year's top this far under the safe top, the name's line box NAME_GAP under it. */
export const YEAR_TOP = 10;
export const YEAR_SIZE = 11;
export const YEAR_LINE = 14;
export const NAME_SIZE = 52;
export const NAME_LINE = 60;
export const NAME_TRACKING = -1;
export const NAME_GAP = 7;
/**
 * The heading folded: the name at most this size (smaller when it must, to fit the middle cell with
 * NAME_INSET each side), its centre this far from the cell's centre, the year's centre that far.
 */
export const NAME_FOLDED_SIZE = 32;
export const NAME_INSET = 4;
export const NAME_FOLDED_DY = -6;
export const YEAR_FOLDED_SIZE = 10;
export const YEAR_FOLDED_DY = 15;

/** The icon row: centres this far under the card, ICON_SPACING (share of the card side) apart, centred on the card. */
export const ICON_DROP = 46;
export const ICON_SPACING = 0.215;
export const ICON_SIZE = 22;
export const ICON_STROKE = 1.6;
/** How far an icon rises as it comes in. */
export const ICON_RISE = 8;

// MARK: Motion

export type Spring = { mass: number; stiffness: number; damping: number };

/** A photo flying between its open place and its cell: lands with a slight overshoot. */
export const PHOTO_SPRING: Spring = { mass: 1, stiffness: 210, damping: 24 };
/** The gradient's frame and the heading: settles without a visible bounce. */
export const CARD_SPRING: Spring = { mass: 1, stiffness: 180, damping: 26 };
/** A pressed icon coming back up. */
export const RELEASE: Spring = { mass: 1, stiffness: 380, damping: 20 };

/**
 * Seconds between photos leaving, by distance from the card's centre (nearest first when folding,
 * farthest first when unfolding).
 */
export const STAGGER = 0.018;
/** The year over the name fades out over this share of the heading's travel; the one under it in the card fades in over the last share. */
export const YEAR_OUT_SHARE = 0.25;
export const YEAR_IN_SHARE = 0.3;
/** The gradient's edges go soft while it moves (as in the design's frames): up to this blur radius, at the middle of its travel. */
export const FEATHER = 8;

// Icons: each comes in over ICON_IN, ICON_STEP after the one before, the first ICON_DELAY after the
// fold; on an unfold they all leave over ICON_OUT, and the photos and the card start back
// UNFOLD_DELAY after the tap (the design's card is already moving while its icons vanish).
export const ICON_DELAY = 0.35;
export const ICON_STEP = 0.04;
export const ICON_IN = 0.25;
export const ICON_OUT = 0.12;
export const UNFOLD_DELAY = 0.05;

// The heart: a pop (an impulse on a spring: scale 1 → 1.35 → a small dip → 1), a ring of six dots,
// and the fill (in fast, out over HEART_OFF).
export const POP_SPRING: Spring = { mass: 1, stiffness: 400, damping: 12 };
export const POP_PEAK = 0.35;
export const HEART_IN = 0.06;
export const HEART_OFF = 0.2;
export const BURST_DOTS = 6;
export const BURST_FOR = 0.4;
/** Dots travel from this radius (just inside the heart's edge) out to the other. */
export const BURST_FROM = 6;
export const BURST_TO = 20;
export const BURST_DOT = 3.5;

/** Presses on the icons: down over PRESS_IN to PRESS_SCALE, back on RELEASE. */
export const PRESS_IN = 0.1;
export const PRESS_SCALE = 0.85;

/** Reduced motion: fold and unfold are a cross-fade of this length; nothing drifts, pops or bursts. */
export const FADE = 0.25;

/** Seconds from a fold or an unfold until everything is still. */
export const SETTLE = UNFOLD_DELAY + STAGGER * 7 + 1.0;

export const EASE_OUT = Easing.bezierFn(0.23, 1, 0.32, 1);

export const NEVER = -1e6;

// MARK: Events

/** Every channel's progress (0 open, 1 folded) when the last fold or unfold began. */
export type Snapshot = { photos: number[]; card: number; icons: number[] };

export function filled(count: number, value: number): number[] {
  'worklet';
  const out: number[] = [];
  for (let i = 0; i < count; i++) out.push(value);
  return out;
}

export function snapshotAt(value: number): Snapshot {
  'worklet';
  return { photos: filled(COUNT, value), card: value, icons: filled(4, value) };
}

/** Everything that happened, as times on the clock in seconds. */
export type Events = {
  folded: boolean;
  at: number;
  /** Where every channel was at `at`: a fold or an unfold starts from what is on screen. */
  from: Snapshot;
  heartOn: boolean;
  heartAt: number;
  downAt: number[];
  upAt: number[];
};

export function restEvents(): Events {
  'worklet';
  return {
    folded: false,
    at: NEVER,
    from: snapshotAt(0),
    heartOn: false,
    heartAt: NEVER,
    downAt: filled(4, NEVER),
    upAt: filled(4, NEVER),
  };
}

/** The time of an event at `offset` in a loop: this loop's if it has happened, else the last loop's. */
function last(base: number, tau: number, offset: number) {
  'worklet';
  return tau >= offset ? base + offset : base + offset - LOOP;
}

/**
 * The events of the autoPlay at `now` for a loop that started at `start`. Each fold or unfold starts
 * from rest, since the one before it has long settled.
 */
export function autoEvents(now: number, start: number): Events {
  'worklet';
  const tau = (now - start) % LOOP;
  const base = now - tau;
  const fold = last(base, tau, FOLD_TAP);
  const unfold = last(base, tau, UNFOLD_TAP);
  const on = last(base, tau, HEART_ON_TAP);
  const off = last(base, tau, HEART_OFF_TAP);
  const e = restEvents();
  e.folded = fold > unfold;
  e.at = Math.max(fold, unfold);
  e.from = snapshotAt(e.folded ? 0 : 1);
  e.heartOn = on > off;
  e.heartAt = Math.max(on, off);
  return e;
}

// MARK: Curves

export function clamp01(value: number) {
  'worklet';
  return Math.min(1, Math.max(0, value));
}

export function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** A spring let go from 0 towards 1 at rest, `t` seconds later (the closed form, so every stack agrees). */
export function spring(t: number, s: Spring) {
  'worklet';
  if (t <= 0) return 0;
  const w0 = Math.sqrt(s.stiffness / s.mass);
  const zeta = s.damping / (2 * Math.sqrt(s.stiffness * s.mass));
  if (zeta >= 1) return 1 - Math.exp(-w0 * t) * (1 + w0 * t);
  const wd = w0 * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w0 * t) * (Math.cos(wd * t) + ((zeta * w0) / wd) * Math.sin(wd * t));
}

/** A spring struck at rest, `t` seconds later: up to 1 at its peak, back through a small dip to 0. */
export function impulse(t: number, s: Spring) {
  'worklet';
  if (t <= 0) return 0;
  const w0 = Math.sqrt(s.stiffness / s.mass);
  const zeta = s.damping / (2 * Math.sqrt(s.stiffness * s.mass));
  const wd = w0 * Math.sqrt(1 - zeta * zeta);
  const peakT = Math.atan(wd / (zeta * w0)) / wd;
  const peak = Math.exp(-zeta * w0 * peakT) * Math.sin(wd * peakT);
  return (Math.exp(-zeta * w0 * t) * Math.sin(wd * t)) / peak;
}

// MARK: Progress (0 open, 1 folded; a photo overshoots 1 a little as it lands)

/** Where a fold or an unfold takes a channel that started at `from`, through `curve` (0 → 1). */
function toward(e: Events, from: number, curve: number) {
  'worklet';
  return lerp(from, e.folded ? 1 : 0, curve);
}

/** Reduced motion: the cross-fade between the two layouts. */
export function fadeAmount(e: Events, now: number) {
  'worklet';
  return toward(e, e.from.card, clamp01((now - e.at) / FADE));
}

/** Photo `i`, whose turn (by distance from the card's centre, nearest first) is `rank`. */
export function photo(i: number, rank: number, e: Events, now: number) {
  'worklet';
  const delay = e.folded ? STAGGER * rank : UNFOLD_DELAY + STAGGER * (COUNT - 1 - rank);
  return toward(e, e.from.photos[i], spring(now - e.at - delay, PHOTO_SPRING));
}

/** The gradient's frame and the heading. */
export function card(e: Events, now: number) {
  'worklet';
  return toward(e, e.from.card, spring(now - e.at - (e.folded ? 0 : UNFOLD_DELAY), CARD_SPRING));
}

/** Icon `k`: its opacity, and how far it has risen. */
export function icon(k: number, e: Events, now: number) {
  'worklet';
  const t = now - e.at;
  if (e.folded) {
    return toward(e, e.from.icons[k], EASE_OUT(clamp01((t - ICON_DELAY - ICON_STEP * k) / ICON_IN)));
  }
  return toward(e, e.from.icons[k], clamp01(t / ICON_OUT));
}

/** An icon's opacity, by the clock or (reduced motion) by the cross-fade. */
export function iconShown(k: number, e: Events, now: number, still: boolean) {
  'worklet';
  return still ? fadeAmount(e, now) : icon(k, e, now);
}

/** What is on screen now, for the next fold or unfold to start from. */
export function snapshot(e: Events, ranks: number[], now: number, still: boolean): Snapshot {
  'worklet';
  if (still) return snapshotAt(fadeAmount(e, now));
  const photos: number[] = [];
  for (let i = 0; i < COUNT; i++) photos.push(photo(i, ranks[i], e, now));
  const icons: number[] = [];
  for (let k = 0; k < 4; k++) icons.push(icon(k, e, now));
  return { photos, card: card(e, now), icons };
}

/** The order photos leave in: by distance of their open place from the card's centre, nearest first. */
export function ranksFor(open: Point[], center: Point): number[] {
  const order = open.map((_, i) => i);
  const distance = (i: number) => Math.hypot(open[i].x - center.x, open[i].y - center.y);
  order.sort((a, b) => distance(a) - distance(b));
  const ranks = filled(open.length, 0);
  order.forEach((i, rank) => {
    ranks[i] = rank;
  });
  return ranks;
}

// MARK: Drift

/** A photo's drift at `t` seconds of the drift clock: x and y in points, tilt in degrees. */
export function drift(slot: Slot, t: number) {
  'worklet';
  return {
    x: DRIFT_X * Math.sin((2 * Math.PI * t) / slot.periodX + slot.phase),
    y: DRIFT_Y * Math.sin((2 * Math.PI * t) / slot.periodY + slot.phase * 1.7),
    tilt: DRIFT_TILT * Math.sin((2 * Math.PI * t) / slot.periodTilt + slot.phase * 0.6),
  };
}

// MARK: Layout and poses

export type Point = { x: number; y: number };
export type Rect = { x: number; y: number; width: number; height: number };

/** Where everything goes on a screen of this size and safe area. */
export type Layout = {
  width: number;
  height: number;
  /** The photos' centres lying open (before drift), in grid order. */
  open: Point[];
  card: Rect;
  /** The eight cells, row by row, skipping the middle one. */
  cells: Rect[];
  middle: Rect;
  /** The centre of the name's line box open and folded, and its scale folded. */
  nameOpen: Point;
  nameFolded: Point;
  nameFoldedScale: number;
  /** The centre of the year under the name in the card. */
  yearFolded: Point;
  icons: Point[];
  /** Each photo's turn when folding (nearest the card's centre first). */
  ranks: number[];
};

/** `nameWidth` is the name's width set at NAME_SIZE with NAME_TRACKING. */
export function layoutFor(width: number, height: number, top: number, bottom: number, nameWidth: number): Layout {
  const bandTop = top + SCATTER_TOP;
  const band = height - bottom - SCATTER_BOTTOM - bandTop;
  const open = SLOTS.map((slot) => ({ x: slot.x * width, y: bandTop + slot.y * band }));

  const side = width - CARD_SIDE * 2;
  const card = { x: CARD_SIDE, y: height * CARD_CENTER - side / 2, width: side, height: side };
  const column = (side - GAP_X * 2) / 3;
  const rows = side - GAP_Y * 2;
  const edgeRow = (rows * (1 - MIDDLE_ROW_SHARE)) / 2;
  const heights = [edgeRow, rows * MIDDLE_ROW_SHARE, edgeRow];
  const grid: Rect[] = [];
  let y = card.y;
  for (let r = 0; r < 3; r++) {
    for (let c = 0; c < 3; c++) {
      grid.push({ x: card.x + c * (column + GAP_X), y, width: column, height: heights[r] });
    }
    y += heights[r] + GAP_Y;
  }
  const middle = grid[4];
  const cells = grid.filter((_, i) => i !== 4);

  const nameOpen = { x: width / 2, y: top + YEAR_TOP + YEAR_LINE + NAME_GAP + NAME_LINE / 2 };
  const scale = Math.min(NAME_FOLDED_SIZE / NAME_SIZE, (column - NAME_INSET * 2) / Math.max(1, nameWidth));
  const icons = [0, 1, 2, 3].map((k) => ({
    x: card.x + side / 2 + (k - 1.5) * ICON_SPACING * side,
    y: card.y + side + ICON_DROP,
  }));
  return {
    width,
    height,
    open,
    card,
    cells,
    middle,
    nameOpen,
    nameFolded: { x: middle.x + middle.width / 2, y: middle.y + middle.height / 2 + NAME_FOLDED_DY },
    nameFoldedScale: scale,
    yearFolded: { x: middle.x + middle.width / 2, y: middle.y + middle.height / 2 + YEAR_FOLDED_DY },
    icons,
    ranks: ranksFor(open, { x: card.x + side / 2, y: card.y + side / 2 }),
  };
}

export function inCard(layout: Layout, x: number, y: number) {
  'worklet';
  const c = layout.card;
  return x >= c.x && x <= c.x + c.width && y >= c.y && y <= c.y + c.height;
}

/** Every channel the scene is drawn from: the card's progress, each photo's, and the drift's clock (-1: none). */
export type Progress = { card: number; photos: number[]; driftT: number };

export function progressAt(e: Events, ranks: number[], now: number, driftT: number): Progress {
  'worklet';
  const photos: number[] = [];
  for (let i = 0; i < COUNT; i++) photos.push(photo(i, ranks[i], e, now));
  return { card: card(e, now), photos, driftT };
}

/** A whole layout at rest: open (0) or folded (1), not drifting. */
export function restProgress(value: number): Progress {
  'worklet';
  return { card: value, photos: filled(COUNT, value), driftT: -1 };
}

export type PhotoPose = {
  x: number;
  y: number;
  width: number;
  height: number;
  radius: number;
  /** Degrees. */
  tilt: number;
  /** 1 open, 0 in its cell: scales the photo's shadow. */
  lift: number;
};

/** Photo `i` at progress `u` (0 open, 1 in its cell), drifting while open when `driftT` ≥ 0. */
export function photoPose(i: number, u: number, layout: Layout, driftT: number): PhotoPose {
  'worklet';
  const slot = SLOTS[i];
  const cell = layout.cells[i];
  const away = 1 - clamp01(u);
  let ox = layout.open[i].x;
  let oy = layout.open[i].y;
  let tilt = slot.tilt;
  if (driftT >= 0) {
    const d = drift(slot, driftT);
    ox += d.x * away;
    oy += d.y * away;
    tilt += d.tilt * away;
  }
  return {
    x: lerp(ox, cell.x + cell.width / 2, u),
    y: lerp(oy, cell.y + cell.height / 2, u),
    width: Math.max(1, lerp(PHOTO_WIDTH, cell.width, u)),
    height: Math.max(1, lerp(PHOTO_HEIGHT, cell.height, u)),
    radius: lerp(PHOTO_RADIUS_OPEN, PHOTO_RADIUS_CELL, clamp01(u)),
    tilt: lerp(tilt, 0, u),
    lift: away,
  };
}

export type CardPose = {
  rect: Rect;
  radius: number;
  feather: number;
  /** 0 open (no shadow: it fills the screen), 1 folded. */
  shadow: number;
};

/** The gradient's frame at progress `c`: the whole screen open, the card folded. */
export function cardPose(c: number, layout: Layout): CardPose {
  'worklet';
  const target = layout.card;
  const k = clamp01(c);
  return {
    rect: {
      x: lerp(0, target.x, c),
      y: lerp(0, target.y, c),
      width: lerp(layout.width, target.width, c),
      height: lerp(layout.height, target.height, c),
    },
    radius: CARD_RADIUS * k,
    feather: FEATHER * 4 * k * (1 - k),
    shadow: k,
  };
}

export type HeadingPose = {
  name: Point;
  scale: number;
  /** The year over the name (open) and under it (folded). */
  yearTop: Point;
  yearTopOpacity: number;
  yearCell: Point;
  yearCellOpacity: number;
};

/** The name and the year at progress `c`. */
export function headingPose(c: number, layout: Layout): HeadingPose {
  'worklet';
  const name = {
    x: lerp(layout.nameOpen.x, layout.nameFolded.x, c),
    y: lerp(layout.nameOpen.y, layout.nameFolded.y, c),
  };
  const scale = lerp(1, layout.nameFoldedScale, c);
  // The top year rides above the name, the cell's year below it, each at its own place at its end.
  const above = (NAME_LINE / 2 + NAME_GAP + YEAR_LINE / 2) * scale;
  const below = layout.yearFolded.y - layout.nameFolded.y;
  return {
    name,
    scale,
    yearTop: { x: name.x, y: name.y - above },
    yearTopOpacity: 1 - clamp01(c / YEAR_OUT_SHARE),
    yearCell: { x: name.x, y: name.y + below },
    yearCellOpacity: clamp01((c - (1 - YEAR_IN_SHARE)) / YEAR_IN_SHARE),
  };
}

// MARK: Heart

/** How filled the heart is. */
export function heartFill(e: Events, now: number) {
  'worklet';
  const t = now - e.heartAt;
  return e.heartOn ? clamp01(t / HEART_IN) : 1 - clamp01(t / HEART_OFF);
}

/** The heart's scale: the pop after it is filled. */
export function heartScale(e: Events, now: number, still: boolean) {
  'worklet';
  if (!e.heartOn || still) return 1;
  return 1 + POP_PEAK * impulse(now - e.heartAt, POP_SPRING);
}

/** The burst's progress (0 → 1 over BURST_FOR), or -1 when there is none. */
export function burst(e: Events, now: number, still: boolean) {
  'worklet';
  const t = now - e.heartAt;
  if (!e.heartOn || still || t < 0 || t >= BURST_FOR) return -1;
  return t / BURST_FOR;
}

/** An icon's scale while pressed and as it springs back. */
export function pressScale(k: number, e: Events, now: number) {
  'worklet';
  const down = e.downAt[k];
  const up = e.upAt[k];
  const press =
    down > up
      ? EASE_OUT(clamp01((now - down) / PRESS_IN))
      : EASE_OUT(clamp01((up - down) / PRESS_IN)) * (1 - spring(now - up, RELEASE));
  return 1 - (1 - PRESS_SCALE) * press;
}

export function anyPressed(e: Events) {
  'worklet';
  for (let k = 0; k < 4; k++) if (e.downAt[k] > e.upAt[k]) return true;
  return false;
}

/** The last time anything was set going by hand; the clock runs until SETTLE after it. */
export function lastEvent(e: Events) {
  'worklet';
  let t = Math.max(e.at, e.heartAt);
  for (let k = 0; k < 4; k++) t = Math.max(t, e.downAt[k], e.upAt[k]);
  return t;
}

// MARK: Touch (each returns the new events)

/** Folds (`fold` true) or unfolds from what is on screen. */
export function toggled(e: Events, fold: boolean, ranks: number[], now: number, still: boolean): Events {
  'worklet';
  return { ...e, from: snapshot(e, ranks, now, still), folded: fold, at: now };
}

export function liked(e: Events, now: number): Events {
  'worklet';
  return { ...e, heartOn: !e.heartOn, heartAt: now };
}

export function pressed(e: Events, k: number, down: boolean, now: number): Events {
  'worklet';
  const downAt = e.downAt.slice();
  const upAt = e.upAt.slice();
  if (down) downAt[k] = now;
  else upAt[k] = now;
  return { ...e, downAt, upAt };
}

// MARK: Burst dots

/** Dot `k` of the burst at progress `p`: its centre from the heart's centre, and its diameter. */
export function burstDot(k: number, p: number) {
  'worklet';
  const eased = EASE_OUT(clamp01(p));
  const radius = BURST_FROM + (BURST_TO - BURST_FROM) * eased;
  const a = (k / BURST_DOTS) * 2 * Math.PI - Math.PI / 2;
  return { x: Math.cos(a) * radius, y: Math.sin(a) * radius, size: BURST_DOT * (1 - 0.4 * clamp01(p)) };
}
