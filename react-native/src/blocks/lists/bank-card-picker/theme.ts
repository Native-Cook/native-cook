// Measured from the pin (iPhone screen scaled to 402 pt wide; 2.883 px per pt).

export const FONT = {
  regular: 'Inter-Regular',
  medium: 'Inter-Medium',
  semibold: 'Inter-SemiBold',
};

export const C = {
  page: '#f1f1f1',
  white: '#ffffff',
  ink: '#000000',
  dark: '#31373b', // selected segment and the main button
  grey: '#989898',
  hairline: '#dadada',
  lime: '#64ff52', // price pill, selection ring, Prime flag
  heroTop: '#13282a',
  // glass plates, sampled on the pin: light green at the front of the stack, deep teal at the back
  plateFront: [124, 193, 142] as const,
  plateBack: [27, 94, 95] as const,
  plateEdge: 'rgba(236, 255, 244, 0.55)',
  plateShade: 'rgba(10, 60, 48, 0.55)',
};

export const HERO = { height: 538, radius: 33 };

export const NAV = { y: 67, hit: 44, backLeft: 11.3, back: { w: 11.3, h: 19.7 }, shareRight: 18.3, share: { w: 19, h: 15 } };

// Front card: centre, size and the pin's oblique view (verticals stay vertical, the top
// edge falls 41 pt over the card: a skew, plus a little foreshortening).
export const CARD = {
  cx: 197.5,
  cy: 204,
  w: 260,
  h: 167,
  radius: 14,
  skewY: 10,
  scaleX: 0.952,
  logo: { x: 25, y: 25, r: 12.5 },
  chip: { x: 26, y: 63, w: 32, h: 25, r: 5 },
  mark: { x: 182, y: 128, w: 64, h: 25 },
};

// Glass plates stacked behind the front card, each one step down and to the left.
export const STACK = { count: 14, dx: -16.5, dy: 15, alpha: 0.94 };

export const TITLE = { y: 370, size: 30, track: -0.5 };
export const SUB = { y: 407, size: 17, pillH: 21, pillPad: 10, pillGap: 9.7, pillSize: 13 };

// The ring takes the thumbnail's own box; the selected thumbnail shrinks inside it.
export const THUMBS = { y: 471.7, size: 65, step: 81.4, left: 33.3, radius: 16, ring: 3, inset: 5.4, inner: 2 };

export const SEG = { top: 556, h: 51, side: 25, radius: 16, inset: 2, pillRadius: 14, size: 15, flag: 13 };

export const INFO = {
  top: 621,
  side: 8,
  pad: 17,
  radius: 24,
  labelSize: 17,
  labelLine: 22,
  row1: 646,
  valueSize: 22,
  unitSize: 15,
  divider: 709,
  row2: 727,
  divider2: 790,
  row3: 810,
};

// The button sits on a frosted white bar with rounded top corners, as in the pin.
export const CTA = { top: 772.5, h: 55, side: 17, radius: 20, size: 18, barTop: 752, barRadius: 30 };
