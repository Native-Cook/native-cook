/** Seconds of one autoPlay loop, and when in it each tab is chosen: Search, Friends, Chats, Home. */
export const LOOP = 9;
export const STEPS = [
  [1.2, 1],
  [2.8, 2],
  [4.4, 3],
  [6.4, 0],
];
/** Seconds after a touch before the loop starts again. */
export const USER_HOLD = 3;

/** Seconds a tab change runs for: the new icon's wiggle. The clock that drives it is linear. */
export const SWITCH = 0.5;
/** Seconds an icon takes to change drawing, and a page to fade in. */
export const ICON_FADE = 0.15;
export const PAGE_FADE = 0.2;
/** The wiggle: degrees of the first swing, how fast it dies, and how much the icon grows. */
export const WIGGLE = 16;
export const WIGGLE_DECAY = 0.8;
export const WIGGLE_GROW = 0.15;

/** Seconds the button takes to shrink away, to pop in, and to slide between its two places. */
export const SHRINK = 0.2;
export const POP = 0.4;
export const SLIDE = 0.45;
/** The same slide under reduced motion, where it is a fade out and a fade in. */
export const REDUCED_SLIDE = 0.3;
/** Control points of the curves: the shrink eases in, the pop passes its end by a tenth, the slide by less. */
export const SHRINK_EASE = [0.4, 0, 1, 1] as const;
export const POP_EASE = [0.34, 1.56, 0.64, 1] as const;
export const SLIDE_EASE = [0.34, 1.25, 0.64, 1] as const;
/** How high the button rises on its way across, and how much a press shrinks it. */
export const ARC = 10;
export const PRESS = 0.08;
export const PRESS_IN = 0.08;
export const PRESS_OUT = 0.16;

/** The tab bar above the bottom inset, the least that inset counts for, and the icons' middle below the bar's top. */
export const BAR = 56;
export const BAR_BOTTOM = 28;
export const ICON_Y = 30;
/** Half the square a tab answers to a touch in. */
export const TAB_HIT = 28;
/** The button on the first tab and on the last: its size and its middle above the bar's top. */
export const HOME_SIZE = 58;
export const HOME_RISE = 52;
export const CHAT_SIZE = 50;
export const CHAT_RISE = 46;
/** The last tab's button, from the right edge to its middle. */
export const CHAT_INSET = 49;
/** The button's shadow: its offset, its blur as a standard deviation, and its strength. */
export const SHADOW_Y = 6;
export const SHADOW_BLUR = 7;
export const SHADOW_ALPHA = 0.3;
export const GLYPH_STROKE = 2.4;
/** The bubble of the last tab's button is the tab's own, a little smaller. */
export const COMPOSE_SCALE = 0.92;

export const MARGIN = 20;
export const TITLE_TOP = 17;
export const TITLE_LINE = 41;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function mix(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

/** Degrees the chosen icon is turned by, `t` of the way through its wiggle: three swings that die away. */
export function wiggle(t: number) {
  'worklet';
  return -WIGGLE * Math.sin(3 * Math.PI * t) * Math.pow(1 - clamp01(t), WIGGLE_DECAY);
}

export function swell(t: number) {
  'worklet';
  return 1 + WIGGLE_GROW * Math.sin(Math.PI * clamp01(t));
}

// What the icons are drawn from, in points around the icon's middle. A piece is a path (M, L, Q, C
// and Z with absolute numbers) or a circle (x, y, radius), stroked or filled in the icon's colour or,
// for a detail cut out of a solid shape, in the page's.
export type Piece = { d?: string; o?: [number, number, number]; paint: 'stroke' | 'fill' | 'cutStroke' | 'cutFill'; width?: number };
export type Icon = { pivot: [number, number]; off: Piece[]; on: Piece[] };

const HOUSE = 'M-8.5 -1.6L0 -9L8.5 -1.6L8.5 6.6Q8.5 9 6.1 9L-6.1 9Q-8.5 9 -8.5 6.6Z';
const DOOR = 'M-2.3 9L-2.3 4.2Q-2.3 2.2 0 2.2Q2.3 2.2 2.3 4.2L2.3 9Z';
const HANDLE = 'M3.2 3.2L8.6 8.6';
const GLINT = 'M-4.9 -1.9Q-4.9 -4.9 -1.9 -4.9';
const SHOULDERS = 'M-9.5 8.5C-9.5 3.2 -6 1.4 -2 1.4C0 1.4 1.8 1.9 3.2 2.8';
const ADD = 'M6.5 2.3L6.5 8.7M3.3 5.5L9.7 5.5';
const SMILE = 'M-4.4 -0.4Q-1.7 2.6 1 -0.4';
const SHOULDER = 'M-8.8 9.8Q-8.4 7 -6.2 5.4';
const BADGE_PLUS = 'M6.8 4L6.8 8.4M4.6 6.2L9 6.2';
export const BUBBLE = 'M-2.5 -8.5L2.5 -8.5Q9.5 -8.5 9.5 -1.5L9.5 -0.5Q9.5 6.5 2.5 6.5L-3.5 6.5L-8.6 9.2L-7.6 4.6Q-9.5 2.6 -9.5 -0.5L-9.5 -1.5Q-9.5 -8.5 -2.5 -8.5Z';
const BARS = 'M-3.4 -3L-3.4 1M0 -4.2L0 2.2M3.4 -3L3.4 1';
export const PLUS = 'M-9 0L9 0M0 -9L0 9';
export const PENCIL = 'M-2.8 1.8L3.4 -4.4';

/** The four tabs: a house, a magnifier that swings on the end of its handle, a person who nods, a bubble. */
export const ICONS: Icon[] = [
  {
    pivot: [0, 0],
    off: [{ d: HOUSE, paint: 'stroke', width: 2 }],
    on: [
      { d: HOUSE, paint: 'fill' },
      { d: HOUSE, paint: 'stroke', width: 2 },
      { d: DOOR, paint: 'cutFill' },
    ],
  },
  {
    pivot: [8.6, 8.6],
    off: [
      { o: [-1.6, -1.6, 6.4], paint: 'stroke', width: 2 },
      { d: HANDLE, paint: 'stroke', width: 2 },
    ],
    on: [
      { o: [-1.6, -1.6, 6.4], paint: 'stroke', width: 3.4 },
      { d: HANDLE, paint: 'stroke', width: 3.4 },
      { d: GLINT, paint: 'stroke', width: 1.6 },
    ],
  },
  {
    pivot: [0, 10],
    off: [
      { o: [-2, -5, 3.8], paint: 'stroke', width: 2 },
      { d: SHOULDERS, paint: 'stroke', width: 2 },
      { d: ADD, paint: 'stroke', width: 2 },
    ],
    on: [
      { o: [-1.5, -2.5, 7.2], paint: 'stroke', width: 2.2 },
      { o: [-4, -4, 1.1], paint: 'fill' },
      { o: [0.6, -4, 1.1], paint: 'fill' },
      { d: SMILE, paint: 'stroke', width: 1.6 },
      { d: SHOULDER, paint: 'stroke', width: 2.2 },
      { o: [6.8, 6.2, 6.2], paint: 'cutFill' },
      { o: [6.8, 6.2, 4.6], paint: 'fill' },
      { d: BADGE_PLUS, paint: 'cutStroke', width: 1.6 },
    ],
  },
  {
    pivot: [0, 0],
    off: [
      { d: BUBBLE, paint: 'stroke', width: 2 },
      { d: BARS, paint: 'stroke', width: 1.8 },
    ],
    on: [
      { d: BUBBLE, paint: 'fill' },
      { d: BUBBLE, paint: 'stroke', width: 2 },
      { d: BARS, paint: 'cutStroke', width: 1.8 },
    ],
  },
];
