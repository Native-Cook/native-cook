// Rain Window Forecast — tokens and layout, measured from the reference (Pinterest pin
// 985232855968876122, a weather app's home screen). The pin is a 390 × 840 pt screen with a status bar;
// it was rectified onto a 402 × 874 device (top inset 62, bottom 34), and the positions below (D) are
// in that device's points. makeLayout maps them onto other devices.

export const COLOR = {
  page: '#121423',
  ink: '#FFFFFF',
  text: '#F1F1F3',
  mute: '#CECCD2',
  card: 'rgba(43,45,66,0.62)',
  cardTop: 'rgba(52,54,76,0.5)',
  cardOn: 'rgba(72,76,108,0.75)',
  tab: '#1E2033',
  tabIcon: '#C9C8CF',
  home: '#DBF3FF',
} as const;

export const FONT = {
  regular: 'RwfOutfit-Regular',
  medium: 'RwfOutfit-Medium',
  semibold: 'RwfOutfit-SemiBold',
} as const;

/** the hours in the cards, and what the large icon, the temperature and the line say for each */
export const HOURS = [
  { label: 'Now', temp: 29, sun: 1, moon: 0, rain: 0.5, line: 'Expect high rain today.' },
  { label: '5pm', temp: 28, sun: 1, moon: 0, rain: 0.5, line: 'Showers through the evening.' },
  { label: '6pm', temp: 28, sun: 0, moon: 0, rain: 1, line: 'Heavy rain until eight.' },
  { label: '7pm', temp: 27, sun: 0, moon: 1, rain: 0.6, line: 'Easing after dark.' },
] as const;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  menu: { x: 19, y: 85 },
  calendar: { x: 362, y: 77, w: 22, h: 24 },
  city: { y: 138 },
  hero: { x: 201.2, y: 284, s: 1 },
  temp: { y: 423 },
  line: { y: 460 },
  stats: { y: 515, xs: [38, 192, 313] },
  hourly: { x: 18, y: 579 },
  cards: { x: 17, y: 607, w: 94, h: 136, gap: 9, r: 14, icon: { dy: 40, s: 0.2 }, label: 78, temp: 106 },
  tab: { x: 17, y: 764, w: 368, h: 77, icons: [72, 161, 246, 331] },
} as const;

export type Layout = {
  w: number;
  h: number;
  sx: number;
  k: number;
  Y: (y: number) => number;
};

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const sx = w / 402;
  const k = (h - top - bottom) / (874 - 62 - 34);
  return { w, h, sx, k, Y: (y: number) => (y < 62 ? (y * top) / 62 : top + (y - 62) * k) };
}
