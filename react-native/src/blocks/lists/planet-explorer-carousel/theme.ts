// Measured on the pin (a still with two screens), each screen scaled from 278 px to 402 pt (x 1.446).

export const COLORS = {
  skyTop: '#1F6DB3',
  skyMid: '#185CA3',
  skyLow: '#272D79',
  skyBottom: '#2D1E6B',
  white: '#FFFFFF',
  navy: '#11406E',
  body: '#747684',
  numeral: '#E0E0E7',
  side: '#B9B8F4',
  sideText: '#5D66B8',
  sideBody: '#8D90CF',
  caption: '#A9B0E0',
  green: '#2FB66A',
  yellow: '#FFC21A',
  orange: '#F7A600',
  blue: '#2F9BEA',
  dotBlue: '#2A6FE0',
  detailTop: '#FFFFFF',
  detailBottom: '#FFFFFF',
  sun: '#22A866',
  block: '#00A86B',
  orbit: 'rgba(255,255,255,0.22)',
  orbitDark: 'rgba(17,64,110,0.14)',
};

export const FONT = {
  regular: 'PlanetPoppins-Regular',
  medium: 'PlanetPoppins-Medium',
  semibold: 'PlanetPoppins-SemiBold',
};

export const LIST = {
  menu: { top: 42, left: 22 },
  avatar: { top: 34, right: 26, size: 40 },
  title: { top: 92, left: 96, size: 52, lineHeight: 64 },
  orbit: { cx: 201, cy: 560, r: 300 },
  card: { top: 395, w: 234, h: 289, radius: 30, spacing: 234, backgroundLift: 0 },
  side: { top: 420, h: 235 },
  planet: { size: 200, top: 288 },
  sidePlanet: { size: 120, dy: 14 },
  name: { top: 100, left: 26, size: 34 },
  numeral: { right: 18, top: 87, size: 150 },
  desc: { top: 152, left: 25, size: 15, lineHeight: 24 },
  button: { cy: 689, ring: 86, size: 72 },
  friends: { cy: 789, size: 37.5, overlap: 11 },
  caption: { top: 815.5, size: 12 },
};

export const DETAIL = {
  back: { top: 46, left: 26 },
  earth: { cx: 257, cy: 147, size: 366 },
  numeral: { left: 36, top: 222, size: 210 },
  rocket: { left: 174, top: 252, size: 138 },
  name: { top: 414, left: 40, size: 48 },
  desc: { top: 479, left: 40, right: 36, size: 14.5, lineHeight: 26 },
  video: { top: 631, left: 35, right: 35, h: 204, radius: 30, play: 66 },
  block: { top: 745, w: 145 },
  dots: { green: { x: 40, y: 145, r: 9 }, yellow: { x: 330, y: 436, r: 13 }, blue: { x: 220, y: 589, r: 9 } },
};

/** Coloured dots and stars on the list screen (pt, radius). */
export const DECOR = {
  dots: [
    { x: 285, y: 86, r: 11, c: '#2FD07A' },
    { x: 307, y: 123, r: 9, c: '#FFB12B' },
    { x: 85, y: 214, r: 9, c: '#2F9BEA' },
    { x: 0, y: 270, r: 9, c: '#FFC21A' },
    { x: 335, y: 872, r: 12, c: '#2F9BEA' },
  ],
  stars: [
    [48, 152], [201, 245], [345, 242], [358, 272], [120, 370], [48, 280], [372, 330], [96, 708], [112, 690],
    [300, 722], [366, 740], [70, 790], [330, 770], [185, 860], [26, 640], [380, 610], [250, 300], [140, 250],
  ] as const,
};
