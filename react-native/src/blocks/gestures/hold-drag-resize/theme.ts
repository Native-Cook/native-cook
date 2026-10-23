/**
 * Make room for deep work, at night, in Liquid Glass. A dark wallpaper with one warm glow low on the screen, the hour
 * lines and a dotted rail drawn on it, and every surface above it is glass that refracts what is under it: the day's
 * blocks, the week strip, the focus total, the bottom bar. Colour means one thing each: amber is focus (the block,
 * the edge's time, the day's total), sky is a task of yours, white glass is someone else's meeting, red is now.
 * Inter for words, JetBrains Mono for times. Sizes on a 4 pt grid, 16 pt edges.
 */
export const COLOR = {
  page: '#06080D',
  ink: '#F5F6F8',
  mute: 'rgba(235,238,245,0.56)', // 6.4:1 over the darkest glass
  faint: 'rgba(235,238,245,0.34)',
  focus: '#FFB547',
  focusInk: '#FFD08A',
  task: '#7CC4FF',
  meeting: '#D9DEE8',
  now: '#FF5A4E',
  onAccent: '#1A1206',
  finger: 'rgba(255,255,255,0.14)',
  fingerEdge: 'rgba(255,255,255,0.42)',
} as const;

/** Glass tints as linear [r, g, b, strength]: what each kind of block mixes into the light it refracts. */
export const TINT = {
  meeting: [0.86, 0.89, 0.95, 0.07],
  task: [0.49, 0.77, 1.0, 0.1],
  focus: [1.0, 0.71, 0.28, 0.2],
  chrome: [0.9, 0.92, 0.97, 0.06],
  selected: [1.0, 1.0, 1.0, 0.16],
  pill: [1.0, 0.71, 0.28, 0.62],
} as const;

/** Shadows under glass fall on a dark page: near-black, a little blue. */
export const SHADOW_RGB = [0.0, 0.01, 0.03] as const;

/** Font sizes (pt) by role; the faces are made into SkFonts once, see `makeScene`. */
export const TYPE = {
  title: { face: 'semibold', size: 30 },
  date: { face: 'regular', size: 15 },
  chipLabel: { face: 'medium', size: 11 },
  total: { face: 'semibold', size: 17 },
  totalUnit: { face: 'medium', size: 13 },
  weekday: { face: 'medium', size: 11 },
  weekdate: { face: 'semibold', size: 17 },
  hour: { face: 'mono', size: 11 },
  now: { face: 'monoBold', size: 11 },
  pill: { face: 'monoBold', size: 11 },
  delta: { face: 'semibold', size: 12 },
  event: { face: 'medium', size: 14 },
  eventSub: { face: 'mono', size: 11 },
  eventNote: { face: 'regular', size: 12 }, // where a meeting is: words, so Inter
  eventNow: { face: 'monoBold', size: 11 }, // a pushed task's new time
  bar: { face: 'medium', size: 15 },
  barMute: { face: 'regular', size: 15 },
} as const;
export type TypeRole = keyof typeof TYPE;

export const LAYOUT = {
  edge: 16,
  /** Header baselines below the safe area: the title, then the date. */
  line1: 38,
  line2: 60,
  /** The focus total's glass chip, right-aligned with the title block. */
  chip: { y: 10, h: 48, w: 116 },
  /** The week strip under the header. */
  week: { y: 76, h: 60, radius: 30, cell: 44 },
  /** The 9:00 line sits this far below the safe area. */
  gridTop: 160,
  labelRight: 50, // hour labels end here
  railX: 63, // the dotted rail and the blocks' capsules
  capsule: 6,
  cardX: 76, // blocks start here
  cardInset: 2, // a block stops 2 pt short of its start and end times, so neighbours keep a 4 pt gap
  radius: 12,
  padX: 12,
  padY: 8,
  check: 13, // the task's circle
  pillH: 22,
  pillPad: 7,
  grip: { w: 28, h: 4, inset: 6 },
  /** The bottom bar floats this far above the home indicator. */
  bar: { h: 52, gap: 8, plus: 52 },
  finger: 44,
} as const;

export const DAY = { start: 9 * 60, end: 18 * 60, now: 9 * 60 + 41, quarter: 15 } as const;

/** Where things sit for a screen `width` × `height` with safe-area insets `top` / `bottom`. */
export function makeLayout(width: number, height: number, top: number, bottom: number) {
  const gridTop = top + LAYOUT.gridTop;
  const barTop = height - Math.max(bottom, LAYOUT.edge) - LAYOUT.bar.gap - LAYOUT.bar.h;
  const room = barTop - 16 - gridTop;
  // An hour is up to 72 pt when the day fits; a phone gives the day the room between the week strip and the bar.
  const ppm = Math.min(1.2, room / (DAY.end - DAY.start));
  return {
    width,
    height,
    top,
    bottom,
    gridTop,
    barTop,
    ppm,
    cardX: LAYOUT.cardX,
    cardW: width - LAYOUT.cardX - LAYOUT.edge,
    right: width - LAYOUT.edge,
  };
}
export type Layout = ReturnType<typeof makeLayout>;

/** y of a time of day (minutes since midnight). */
export function yOf(lay: Layout, min: number): number {
  'worklet';
  return lay.gridTop + (min - DAY.start) * lay.ppm;
}
