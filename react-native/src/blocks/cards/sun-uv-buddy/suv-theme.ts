// Sun UV Buddy — layout, colour, copy and the forecast, measured on the pin (a screen recording,
// the phone's screen 570 px across: x → (px − 83) · S, y → (px − 70) · S, S = 402 / 570).

export const REF_W = 402;
export const REF_H = 874;

export const C = {
  page: '#F4F4F6',
  map: '#C9CDD2',
  cardTop: '#FFF8D9',
  cardBottom: '#FFE9A6',
  ink: '#1E1F24',
  grey: '#8E9099',
  chip: '#FFFFFF',
  chipOn: '#24252B',
  sun: '#FFD800',
  sunDeep: '#FFC400',
  barLow: '#EDEDF2',
  barMid: '#FFE14D',
  barHigh: '#FFB21E',
  close: 'rgba(30, 31, 36, 0.06)',
  green: '#3CC665',
  yellow: '#F7CF39',
};

export const COPY = {
  title: 'UV index',
  place: 'Riverside village',
  days: ['Today', 'Tomorrow', 'Thu, 2', 'Fri, 3', 'Sat, 4'],
  learn: 'What the UV index means',
};

export const HOURS = ['09', '10', '11', '12', '13', '14', '15', '16', '17', '18', '19'];

/** UV by hour (09 … 19) for each day. */
export const DAYS: number[][] = [
  [1, 2, 3, 4, 5, 5, 4, 3, 1, 0, 0],
  [1, 1, 2, 3, 4, 4, 3, 2, 1, 0, 0],
  [1, 1, 2, 3, 3, 3, 2, 1, 1, 0, 0],
  [0, 1, 1, 2, 3, 3, 2, 1, 1, 0, 0],
  [0, 1, 1, 1, 2, 2, 1, 1, 0, 0, 0],
];

/** What the sun says for a UV value: headline (with the hour), and its line under the number. */
export function words(uv: number) {
  'worklet';
  if (uv <= 0) return { level: 'no UV at all', line: ['The sun is going to bed.', 'Nothing to worry about now'] };
  if (uv <= 2) return { level: 'low', line: ['Barely any sun. Enjoy the day', 'without a second thought'] };
  if (uv <= 4) return { level: 'moderate', line: ['The sun is calm. But sunscreen', "and a cap won't hurt"] };
  return { level: 'high', line: ['Strong sun. Sunscreen, a cap', 'and some shade, please'] };
}

export const UI = {
  sheet: { y: 61.3, r: 34, bottom: 613, bottomR: 30 },
  title: { y: 84.6, size: 19 },
  place: { y: 105, size: 15 },
  close: { x: 370, y: 95.2, r: 20 },
  chips: { y: 148, h: 35, x: [-6, 74, 178, 262, 336], w: [74, 98, 78, 68, 72] },
  sun: { x: 198.8, y: 327.8, r: 84 },
  headline: { y: 446, size: 24 },
  badge: { x: 200.9, y: 476.6, r: 12.7 },
  line: { y: [512.5, 534.4], size: 16.5 },
  icons: { y: 569 },
  bars: { x0: 20.4, w: 29.6, gap: 3.5, bottom: 765.6, h: [5, 26.8, 37.4, 48, 58, 68], labelY: 787.5 },
  stemX: 135.4,
  learn: { y: 860 },
};
