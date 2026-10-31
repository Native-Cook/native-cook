// Clay Relight Settings — the colours of the screen at noon, at sunset and at night, and its layout.

/** Each token at noon, at sunset and at night. Noon → sunset follows the light (m); the step to night
 * is its own short fade (`theme`, see crs-motion). */
export const PALETTE = {
  page: ['#EEF0F4', '#F4E6DC', '#0B0E19'],
  card: ['#FFFFFF', '#FFF8F2', '#161B2B'],
  ink: ['#14161C', '#2B1E1A', '#EEF0F7'],
  mute: ['#646A79', '#7A6058', '#8F95AA'],
  line: ['#E7E9EE', '#F1E2D8', '#232A3E'],
  trackOff: ['#D9DCE3', '#E9D6CB', '#2B3247'],
  pill: ['rgba(255,255,255,0.72)', 'rgba(255,244,235,0.72)', 'rgba(14,18,34,0.62)'],
  pillInk: ['#1E2230', '#3A2620', '#E6E9F5'],
} as const;

export const TRACK_ON = '#5B5BD6';

export const FONT = {
  regular: 'CrsInter-Regular',
  medium: 'CrsInter-Medium',
  semibold: 'CrsInter-SemiBold',
} as const;

export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

export type Row = { title: string; day: string; night: string };

// the list under the card; each row's value changes as its icon's lamp comes on
export const ROWS: Row[] = [
  { title: 'Notifications', day: 'Allowed', night: 'Silenced' },
  { title: 'Focus', day: 'Off', night: 'Sleep' },
  { title: 'Display & Brightness', day: 'Light', night: 'Dark' },
  { title: 'Battery', day: '86%', night: 'Charging' },
  { title: 'Privacy & Security', day: '', night: '' },
];

/** m → the time on the card's clock, in minutes after midnight (2:40 PM … 11:40 PM). */
export const CLOCK = { from: 14 * 60 + 40, span: 540 } as const;

export const SCENE_H = 232;
export const BAND_H = 76;
export const ROW_H = 60;
export const TILE = 40;
export const ICON_COL = 64;
export const SWITCH = { w: 51, h: 31, knob: 27 } as const;

export type Layout = {
  w: number;
  side: number;
  cardW: number;
  titleTop: number;
  heroTop: number;
  heroH: number;
  sectionTop: number;
  listTop: number;
  listH: number;
};

export function makeLayout(w: number, top: number): Layout {
  const side = SPACE.l;
  const titleTop = top + SPACE.s;
  const heroTop = titleTop + 44 + SPACE.l;
  const heroH = SCENE_H + BAND_H;
  const sectionTop = heroTop + heroH + SPACE.xl;
  const listTop = sectionTop + 18 + SPACE.s;
  return { w, side, cardW: w - 2 * side, titleTop, heroTop, heroH, sectionTop, listTop, listH: ROWS.length * ROW_H };
}
