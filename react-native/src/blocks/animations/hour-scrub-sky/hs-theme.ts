// Hour Scrub Sky: tokens. Positions are points on the 402 × 874 pt canvas and scale with the screen width.

/** Inks (the colour a text or control takes from the light under it). */
export const INK = {
  dark: '#0B0D12',
  light: '#F7F8FA',
  cream: '#F3ECC0',
  red: '#E5484D',
  blue: '#1F6FEB',
  blueNight: '#5B9BFF',
  pillDark: '#16181D',
  glassNight: '#1A1D26',
} as const;

/**
 * Sky keyframes: four stops (zenith at y 0, mid at 35 %, horizon at 62 %, haze from 80 % to the bottom), by hours since
 * 12:00. `DAY` is the light below the twilight front, `NIGHT` the dark above it. Interpolated in OKLab.
 */
export type Stops = [string, string, string, string];
export const DAY: [number, Stops][] = [
  [0, ['#3D7FE6', '#6EA2EE', '#B0C8EA', '#F7F9FC']],
  [3.0, ['#3A7BE2', '#74A6EE', '#B8CCEA', '#F8F6F4']],
  [4.5, ['#3B74D6', '#86AEEA', '#C9C6D4', '#FBE7D2']],
  [5.0, ['#5468BC', '#B08CB4', '#EBAE9C', '#FCE2CC']],
  [6.0, ['#83213A', '#E83E2D', '#F49D7F', '#FFF3E6']],
  [6.5, ['#7A1F36', '#E0392A', '#F08A6C', '#FBD9C2']],
  [7.0, ['#5E1A30', '#B8322B', '#D9684F', '#E89A7E']],
  [7.25, ['#0D1220', '#121725', '#171B27', '#2A3350']],
  [17.25, ['#3E3A56', '#5A5674', '#6E6A86', '#8C86A2']],
  [17.9, ['#4A4664', '#6E6A86', '#8A85A2', '#A69FBA']],
  [18.4, ['#4F7FD6', '#8AAEE6', '#BCCBE6', '#EEEAF0']],
  [19.0, ['#4A86E4', '#86B2EE', '#B9D0F0', '#F2F5FA']],
  [24, ['#3D7FE6', '#6EA2EE', '#B0C8EA', '#F7F9FC']],
];
export const NIGHT: [number, Stops][] = [
  [0, ['#0D1220', '#121725', '#171B27', '#2A3350']],
  [7.25, ['#0D1220', '#121725', '#171B27', '#2A3350']],
  [15, ['#0A0E1A', '#0F1420', '#151924', '#262E48']],
  [17.25, ['#0C111D', '#121724', '#181C28', '#2A3350']],
  [24, ['#0D1220', '#121725', '#171B27', '#2A3350']],
];

/** Cloud colours: lit rim and shaded belly, by hours since 12:00 (day side of the front) and at night. */
export const CLOUD_DAY: [number, [string, string]][] = [
  [0, ['#FFFFFF', '#BCCBE6']],
  [3.0, ['#FFFFFF', '#B3C3E0']],
  [4.5, ['#FFD3A8', '#9A98B4']],
  [6.0, ['#FF8E5E', '#B95E5E']],
  [6.5, ['#F57A50', '#A04E58']],
  [7.0, ['#C04A3A', '#4A3442']],
  [7.25, ['#4A5266', '#2A2F3D']],
  [17.25, ['#9C96B4', '#4E4A64']],
  [17.9, ['#F2B8A0', '#5E5A78']],
  [18.4, ['#FFF1EA', '#A9B6D6']],
  [19.0, ['#FFFFFF', '#B8C9E6']],
  [24, ['#FFFFFF', '#BCCBE6']],
];
export const CLOUD_NIGHT: [string, string] = ['#4A5266', '#2A2F3D'];

/** Sun disc colour by hours since 12:00. */
export const SUN: [number, string][] = [
  [0, '#FFF6D8'],
  [4.5, '#FFF0C8'],
  [5.5, '#FFD27A'],
  [6.0, '#FFB45C'],
  [18.0, '#FFB45C'],
  [18.6, '#FFD27A'],
  [19.6, '#FFF6D8'],
  [24, '#FFF6D8'],
];

/** Design-system springs (damping, stiffness, mass 1). */
export const SPRING = {
  pop: { d: 19, k: 195 },
  snap: { d: 30.5, k: 322.3 },
  glide: { d: 22.1, k: 157.9 },
  detent: { d: 40.4, k: 503.5 },
} as const;

/** Layout (pt on the 402 × 874 canvas). */
export const L = {
  chip: { x: 15, y: 63, w: 84, h: 42 },
  sentenceTop: 150, // cap top of line 1
  linePitch: 46,
  textX: 22,
  tempTop: 549, // cap top of the numeral
  arrow: { x: 313, y: 583, w: 66, h: 44 },
  locY: 644, // location row (its cap centre sits 7 pt lower)
  detailY: 669, // detail row (cap 670.5–679.6)
  pillCy: 705, // hour pill (690–720): its own lane, ≥ 7 pt below the detail row's descenders and 8 pt above the ticks
  disc: { cx: 39, cy: 745, r: 16 },
  ticks: { x0: 80, x1: 369, n: 49, bottom: 756, h: 20, hPeak: 28 },
  tab: { x: 12, y: 792, w: 378, h: 66 },
  sel: { x: 16, y: 798, w: 86, h: 54 },
  tabCx: [59, 153, 247, 341],
  hazeY: 540,
  pole: [60, -120],
} as const;

/** Points per hour along the tick row. */
export const PT_PER_H = (L.ticks.x1 - L.ticks.x0) / 24;

/** The one family (registered with Skia under this name, weights 300 / 400 / 500 / 600). */
export const FAMILY = 'HSFigtree';

/** Copy (invented). */
export const COPY = {
  city: 'Port Ansel',
  lines: [['Dry', 'until', 'evening,'], ['a', 'calm,', 'clear'], ['window', 'ahead']] as const,
  conditions: ['Mostly Cloudy', 'Partly Cloudy', 'Cloudy'] as const,
  detail: 'H 30° · L 22° · 56% Humidity · UV 6',
  tabs: ['Nearby', 'Cities', 'Rankings', 'More'] as const,
};

/** Temperature (°C) by hours since 12:00, linear between. */
export const TEMPS: [number, number][] = [
  [0, 29],
  [3, 30],
  [6.6, 28],
  [7.4, 27],
  [9, 26],
  [12, 24],
  [15, 22],
  [18, 22],
  [20, 24],
  [22, 26],
  [24, 29],
];

/** Condition label by hours since 12:00: [from, index into COPY.conditions]. Changes sit between the half-hour detents. */
export const CONDITIONS: [number, number][] = [
  [0, 0],
  [3.25, 1],
  [5.25, 0],
  [9.25, 1],
  [14.25, 0],
  [17.25, 2],
  [19.25, 0],
];
