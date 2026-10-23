import type { TextStyle } from 'react-native';

/**
 * Offline for the flight, at night: a near-black screen, a flight card in the manner of a flight tracker on top and
 * the episode list as the flight's timeline under it. Two accents only, and both mean something: the timeline's
 * segments alternate orange and violet so neighbouring episodes read apart, violet is also the action, and green is
 * reserved for "on the phone" and "lands". Inter Light for what you read, Inter for the UI, Doto (a dot-matrix face)
 * for the boarding pass. Sizes on a 4 pt grid; content keeps 16 pt from the screen edge.
 */
export const FONT = {
  light: 'DMS-Inter-Light',
  regular: 'DMS-Inter-Regular',
  medium: 'DMS-Inter-Medium',
  semibold: 'DMS-Inter-SemiBold',
  dots: 'DMS-Doto-Black',
} as const;

export const COLOR = {
  page: '#0B0B0C',
  pageClear: 'rgba(11,11,12,0)',
  card: '#161618',
  line: 'rgba(255,255,255,0.08)',
  ink: '#F4F2EE',
  mute: '#8F8C87', // 6.1:1 on the page
  dim: '#5E5C58',
  dot: 'rgba(255,255,255,0.2)',
  orange: '#FF8A1F',
  violet: '#A57BFF',
  violetInk: '#170B30',
  /** The button's track while a download fills it. */
  violetTrack: '#2D2340',
  green: '#3DDC84',
  greenSoft: 'rgba(61,220,132,0.14)',
  buttonOff: '#1E1E21',
  glyph: 'rgba(255,255,255,0.36)',
  ring: 'rgba(165,123,255,0.25)',
  scrim: 'rgba(0,0,0,0.6)',
  pass: '#050506',
  passEdge: 'rgba(255,255,255,0.07)',
  passMute: 'rgba(255,255,255,0.56)',
  passText: 'rgba(255,255,255,0.78)',
  chip: 'rgba(255,255,255,0.94)',
  chipInk: '#111113',
  finger: 'rgba(255,255,255,0.16)',
  fingerEdge: 'rgba(255,255,255,0.32)',
} as const;

/** Segment colours in order: the k-th episode on the timeline takes SEGMENT[k % 2]. */
export const SEGMENT = [COLOR.orange, COLOR.violet] as const;

export const LAYOUT = {
  edge: 16,
  nav: 44,
  /** The flight card, under the nav. */
  cardGap: 4,
  card: 148,
  cardRadius: 24,
  cardPad: 20,
  /** "IN-FLIGHT LISTENING", 20 below the card, and the gap before the first row. */
  sectionGap: 20,
  section: 16,
  sectionAfter: 8,
  row: 64,
  /** The time column, the rail and the text. */
  numberX: 16,
  numberW: 52,
  railX: 76,
  rail: 4,
  textX: 96,
  trailRight: 20,
  glyph: 22,
  /** The bottom: two figures and the button. */
  cells: 72,
  cellsGap: 12,
  button: 52,
  buttonRadius: 16,
  panelHeight: 136,
  panelPad: 0,
  panelSide: 16,
  /** Auto-scroll zone at either end of the list. */
  edgeZone: 48,
  finger: 44,
} as const;

/** The route on the card, in card coordinates: from just after "SFO" to just before "JFK", on the codes' middle. */
export const ROUTE = { x0: 120, x1: 250, y: 56, lift: 18 } as const;

const TNUM: TextStyle['fontVariant'] = ['tabular-nums'];

export const TYPE = {
  nav: { fontFamily: FONT.medium, fontSize: 17, lineHeight: 22, letterSpacing: -0.2 },
  cardMeta: { fontFamily: FONT.regular, fontSize: 12, lineHeight: 16, letterSpacing: 0.1 },
  cardMetaStrong: { fontFamily: FONT.medium, fontSize: 12, lineHeight: 16, letterSpacing: 0.1 },
  code: { fontFamily: FONT.light, fontSize: 44, lineHeight: 48, letterSpacing: -0.9 },
  city: { fontFamily: FONT.regular, fontSize: 12, lineHeight: 16 },
  time: { fontFamily: FONT.medium, fontSize: 17, lineHeight: 22, fontVariant: TNUM },
  timeAp: { fontFamily: FONT.regular, fontSize: 12, lineHeight: 22 },
  section: { fontFamily: FONT.medium, fontSize: 12, lineHeight: 16, letterSpacing: 1.4 },
  sectionMeta: { fontFamily: FONT.regular, fontSize: 13, lineHeight: 16 },
  rowTime: { fontFamily: FONT.medium, fontSize: 13, lineHeight: 16, fontVariant: TNUM },
  rowAp: { fontFamily: FONT.regular, fontSize: 11, lineHeight: 14 },
  title: { fontFamily: FONT.light, fontSize: 19, lineHeight: 24, letterSpacing: -0.2 },
  meta: { fontFamily: FONT.regular, fontSize: 12, lineHeight: 16, fontVariant: TNUM },
  land: { fontFamily: FONT.medium, fontSize: 12, lineHeight: 16 },
  figure: { fontFamily: FONT.light, fontSize: 26, lineHeight: 30, letterSpacing: -0.3, fontVariant: TNUM },
  figureLabel: { fontFamily: FONT.regular, fontSize: 12, lineHeight: 16 },
  button: { fontFamily: FONT.semibold, fontSize: 16, lineHeight: 20, letterSpacing: -0.2, fontVariant: TNUM },
  passKey: { fontFamily: FONT.medium, fontSize: 10, lineHeight: 12, letterSpacing: 0.8 },
  passValue: { fontFamily: FONT.medium, fontSize: 13, lineHeight: 18 },
  passCode: { fontFamily: FONT.dots, fontSize: 84, lineHeight: 88, letterSpacing: 1 },
  passCity: { fontFamily: FONT.regular, fontSize: 11, lineHeight: 14 },
  passShow: { fontFamily: FONT.semibold, fontSize: 12, lineHeight: 16, letterSpacing: 0.7 },
  passNote: { fontFamily: FONT.regular, fontSize: 12, lineHeight: 16 },
  chip: { fontFamily: FONT.semibold, fontSize: 12, lineHeight: 16 },
} as const;

/** Where things sit, for a screen `width` wide with `top` / `bottom` safe-area insets. */
export function makeLayout(width: number, height: number, top: number, bottom: number) {
  const cardTop = top + LAYOUT.nav + LAYOUT.cardGap;
  const sectionTop = cardTop + LAYOUT.card + LAYOUT.sectionGap;
  const listTop = sectionTop + LAYOUT.section + LAYOUT.sectionAfter;
  const panelBottom = Math.max(bottom, LAYOUT.edge);
  const panelTop = height - panelBottom - LAYOUT.panelHeight;
  return {
    width,
    height,
    safeTop: top,
    cardTop,
    sectionTop,
    listTop,
    panelTop,
    panelBottom,
    panelWidth: width - LAYOUT.panelSide * 2,
    cardWidth: width - LAYOUT.edge * 2,
  };
}
export type Layout = ReturnType<typeof makeLayout>;
