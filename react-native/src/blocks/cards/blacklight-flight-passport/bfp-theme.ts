// Blacklight Flight Passport — layout, colour, copy and the routes, measured on the pin's two
// screenshots (402 × 874 pt): the passport in daylight and under the blacklight.

export const REF_W = 402;
export const REF_H = 874;

export const COPY = {
  title: 'Your All-Time Passport',
  sub: 'View and Share',
  brand: 'MY CONTRAIL PASSPORT',
  kinds: 'PASSPORT • PASS • PASAPORTE',
  flights: 'flights',
  rows: [
    ['Authority', 'Contrail'],
    ['Place of issue', 'SFO'],
    ['Date of issue', '28 JUN 26'],
    ['Member since', '26 JUN 26'],
  ],
  stats: ['Distance', 'Flight Time', 'Airports', 'Airlines'],
  mrz: ['ALLTIME<<SAM<<RIVERA<<MEMBER26JUN26<<@CONTRAIL<<<<', 'ISSUED28JUN26SFO<<<<<<<<<<<<<<<<<<<<<<CONTRAIL.APP'],
  button: 'Blacklight',
  share: ['Messages', 'Photos', 'More'],
  stamp: 'VERIFIED TRAVELLER · 2026',
};

/** The totals the counters roll up to. */
export const TOTALS = { flights: 27, km: 128714, days: 7, hours: 1, airports: 21, airlines: 14 };

export const LIGHT = {
  bg: '#F3F2EE',
  cover: '#13245E',
  page: '#EEF2EC',
  page2: '#E6EEEA',
  map: '#A6D2BA',
  arc: '#B4642E',
  airport: '#D8433A',
  ink: '#1C2C6E',
  inkSoft: '#2F43A0',
  text: '#202226',
  grey: '#6B6F7A',
  button: '#FFFFFF',
  buttonInk: '#16171A',
  chrome: '#FFFFFF',
  chromeInk: '#16171A',
  header: '#16171A',
  headerSub: '#7A7D86',
};

export const UV = {
  bg: '#090A2A',
  bgGlow: '#3A23B8',
  cover: '#1A1460',
  page: '#B9B9F6',
  page2: '#E6C8F6',
  map: '#F27FE6',
  arc: '#3F2AA3',
  airport: '#7A3CF0',
  ink: '#1D2470',
  inkSoft: '#3A3FB0',
  text: '#20214A',
  grey: '#4E4F8A',
  button: '#3B1F98',
  buttonInk: '#FFFFFF',
  chrome: 'rgba(255, 255, 255, 0.12)',
  chromeInk: '#FFFFFF',
  header: '#FFFFFF',
  headerSub: '#B9B8E8',
  glowLine: '#5FF3FF',
  stamp: '#FF7AD9',
};

export const UI = {
  close: { x: 40, y: 89, r: 23 },
  layout: { x: 362, y: 89, r: 23 },
  titleY: 81,
  subY: 99,
  card: { x: 31, y: 188, w: 341, h: 452, r: 14, band: 10, bandBottom: 12 },
  button: { x: 149, y: 666, w: 102, h: 31 },
  share: { y: 796, labelY: 820, x: [86, 201, 315], s: 26 },
};

/** Inside the card (card space, origin at its top-left corner). */
export const PAGE = {
  planesY: 26,
  map: { y: 34, h: 176 },
  flagsY: 214,
  ruleY: 228,
  brand: { x: 13, y: 248 },
  kinds: { x: 26, y: 263 },
  logo: { x: 316, y: 248, s: 22 },
  big: { x: 13, y: 299, size: 34 },
  word: { x: 13, y: 333, size: 27 },
  rows: { x: 124, y: [291, 308, 325, 342], size: 10.5 },
  stats: { x: [13, 140, 225, 300], labelY: 367, valueY: 384 },
  mrz: { x: 13, y: [414, 426], size: 10.3 },
};

/** Map projection on the card: longitude −170 … 190 across its width, latitude 78 … −46 down. */
export const MAP = { lon0: -170, lonSpan: 360, lat0: 78, ptPerLat: 1.42 };

export const AIRPORTS: { [code: string]: [number, number] } = {
  SFO: [-122.4, 37.6],
  LAX: [-118.4, 33.9],
  SEA: [-122.3, 47.4],
  JFK: [-73.8, 40.6],
  MEX: [-99.1, 19.4],
  GRU: [-46.5, -23.4],
  HNL: [-157.9, 21.3],
  LHR: [-0.45, 51.5],
  CDG: [2.5, 49.0],
  FCO: [12.2, 41.8],
  IST: [28.8, 41.3],
  DXB: [55.4, 25.2],
  DEL: [77.1, 28.6],
  BKK: [100.7, 13.7],
  SIN: [103.9, 1.3],
  HKG: [113.9, 22.3],
  ICN: [126.4, 37.5],
  NRT: [140.4, 35.8],
  SYD: [151.2, -33.9],
  JNB: [28.2, -26.1],
  KEF: [-22.6, 64.0],
};

/** Routes in the order they are drawn: from, to, how much the arc bows north. */
export const ROUTES: [string, string, number][] = [
  ['SFO', 'NRT', 0.22],
  ['SFO', 'ICN', 0.2],
  ['SFO', 'JFK', 0.18],
  ['JFK', 'LHR', 0.22],
  ['LHR', 'CDG', 0.4],
  ['CDG', 'FCO', 0.3],
  ['FCO', 'IST', 0.3],
  ['IST', 'DXB', 0.25],
  ['DXB', 'DEL', 0.25],
  ['DEL', 'BKK', 0.25],
  ['BKK', 'SIN', 0.3],
  ['SIN', 'SYD', 0.22],
  ['HKG', 'NRT', 0.3],
  ['SFO', 'HNL', 0.2],
  ['LAX', 'MEX', 0.25],
  ['MEX', 'GRU', 0.22],
  ['JFK', 'KEF', 0.25],
  ['DXB', 'JNB', 0.18],
];

/** Simplified round flags along the bottom of the map: stripes (horizontal or vertical) or a disc. */
export const FLAGS: { kind: 'h' | 'v' | 'dot'; colors: string[] }[] = [
  { kind: 'h', colors: ['#B22234', '#FFFFFF', '#B22234', '#3C3B6E'] },
  { kind: 'v', colors: ['#009246', '#FFFFFF', '#CE2B37'] },
  { kind: 'dot', colors: ['#FFFFFF', '#BC002D'] },
  { kind: 'dot', colors: ['#FFFFFF', '#C60C30'] },
  { kind: 'h', colors: ['#000000', '#DD0000', '#FFCE00'] },
  { kind: 'v', colors: ['#002395', '#FFFFFF', '#ED2939'] },
  { kind: 'h', colors: ['#00732F', '#FFFFFF', '#000000'] },
  { kind: 'v', colors: ['#006847', '#FFFFFF', '#CE1126'] },
  { kind: 'dot', colors: ['#009C3B', '#FFDF00'] },
  { kind: 'h', colors: ['#ED1C24', '#FFFFFF', '#241D4F', '#FFFFFF', '#ED1C24'] },
  { kind: 'dot', colors: ['#E30A17', '#FFFFFF'] },
  { kind: 'h', colors: ['#012169', '#C8102E', '#012169'] },
  { kind: 'dot', colors: ['#00247D', '#FFFFFF'] },
];
