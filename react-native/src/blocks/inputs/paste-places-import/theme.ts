/**
 * Paste Places Import — every colour, size and position of the screen.
 *
 * Positions are "ref px": pixels of the pin rectified to 1206 × 2622 (an iPhone 17 Pro at 3×,
 * 402 pt wide), measured from `qa/ref-24.png`. The component multiplies them by `width / 1206`,
 * so on a 402 pt screen they land on the pin's pixels. `y` is from the top of the screen, status
 * bar included; the component moves it by `insets.top - REF_INSET` on other devices. Font sizes
 * and tracking are in pt for the 402 pt screen and scale the same way.
 */

export const REF_W = 1206;
/** Font scale reference: 1206 ref px = 402 pt. */
export const REF_PT = 402;
/** The pin was taken on a phone whose top safe area is 62 pt (186 ref px). */
export const REF_INSET = 186;

/** Anybody's ascent / descent (hhea): the baseline of a one-line Text sits `ASCENT` em under its top. */
export const ASCENT = 0.795;
export const DESCENT = 0.24;
/** SF Pro: ascent / descent of the system font. */
export const SF_ASCENT = 0.953;
export const SF_DESCENT = 0.241;

export const FONT = {
  text: 'AnybodyNC-Text', // wght 500, wdth 104
  extra: 'AnybodyNC-ExtraBold', // wght 800, wdth 100
} as const;

export const colors = {
  ink: '#000000',
  white: '#FFFFFF',
  hint: '#505050',
  counter: '#5E5E5E',
  placeholder: '#C2C2C6',
  glyph: '#444444',
  cardBorder: '#B8B8B8',
  /** The disabled button is a translucent grey over the page (its edge shows the blue glow). */
  buttonFill: 'rgba(49,49,50,0.38)',
  /** Page background: a vertical grey. y in ref px → colour. */
  pageStops: [
    [0, '#F7F7F7'],
    [100, '#F5F5F5'],
    [200, '#F1F1F1'],
    [300, '#ECECEC'],
    [500, '#ECECEB'],
    [800, '#EBEBEB'],
    [1100, '#EDEDED'],
    [1400, '#EDEDED'],
    [1600, '#EBEBEB'],
    [2000, '#EBEBEB'],
    [2300, '#E9EAE9'],
    [2622, '#E8E9E8'],
  ] as readonly (readonly [number, string])[],
  /** Two soft blue glows on the page: a band behind the counter and a corner at the bottom left. */
  glowBand: { cx: 603, cy: 2270, rx: 960, ry: 340, color: '#D9E5EE', opacity: 0.8 },
  glowCorner: { cx: 0, cy: 2650, rx: 600, ry: 440, color: '#D3E0E9', opacity: 0.95 },
} as const;

/** Spacing tokens in ref px (3 ref px = 1 pt). Snapped to the 4 pt grid only where the pin is within 1 pt of it. */
export const space = {
  cardEdge: 98, // screen edge → text area (32.7 pt)
  buttonEdge: 90, // screen edge → start button (30 pt)
  cardPad: 89, // text area edge → placeholder (29.7 pt)
  counterLeft: 102, // screen edge → counter ink
  pasteGap: 33, // clipboard glyph → "paste"
} as const;

export const radius = {
  card: 101, // 33.7 pt
  button: 94.5, // pill
} as const;

export const hairline = 2; // ref px (0.67 pt)

/** Boxes in ref px. */
export const box = {
  card: { x: 98, y: 1271, w: 1010, h: 868 },
  button: { x: 90, y: 2337, w: 1026, h: 189 },
  /** Close × centre line: round caps, 6 px stroke. */
  close: { x1: 1080, y1: 206, x2: 1116, y2: 243, stroke: 6 },
  /** The three app icons: ink box of the group on the pin. */
  apps: { x: 401, y: 371, w: 403, h: 239 },
  /** Clipboard glyph (SVG units are ref px): body and clip, 3.8 px stroke. */
  clip: { x: 508, y: 2010.5, w: 33.5, h: 41.5 },
} as const;

/**
 * Text styles: size in pt (at 402 pt), baselines in ref px. Every word is placed on its own: `x` is the ref px of
 * its text origin and `ls` its tracking in pt, both fitted so that each word's ink box lands on the pin's (the
 * pin's face has other letter widths than the nearest OFL match, so one tracking per line leaves words 5–15 px off).
 */
export type Word = { t: string; x: number; ls: number };

export const type = {
  h1: { font: FONT.extra, size: 25.1, color: colors.ink, lines: [{ base: 811.1, words: [{ t: 'import', x: 175.1, ls: 0.77 }, { t: 'places', x: 498.0, ls: 0.5 }, { t: 'from', x: 802.1, ls: 0.86 }] }, { base: 915.4, words: [{ t: 'notes', x: 293.0, ls: 0.42 }, { t: 'or', x: 561.1, ls: 0.42 }, { t: 'texts', x: 676.2, ls: 0.0 }] }] },
  hint: {
    font: FONT.text,
    size: 12.6,
    color: colors.hint,
    lines: [
      { base: 1039.6, words: [{ t: 'works', x: 194.8, ls: -0.04 }, { t: 'best', x: 331.1, ls: -0.79 }, { t: 'when', x: 426.8, ls: 0.21 }, { t: 'the', x: 546.5, ls: -0.95 }, { t: 'places', x: 620.1, ls: -0.05 }, { t: 'in', x: 760.5, ls: -0.12 }, { t: 'the', x: 807.5, ls: -1.12 }, { t: 'list', x: 880.5, ls: -0.45 }, { t: 'are', x: 950.6, ls: -0.45 }, { t: 'all', x: 1029.6, ls: 0.55 }] },
      { base: 1089.1, words: [{ t: 'from', x: 383.4, ls: -1.06 }, { t: 'the', x: 489.5, ls: -0.84 }, { t: 'same', x: 562.5, ls: 0.05 }, { t: 'region.', x: 681.1, ls: 0.22 }] },
    ],
  },
  tip: { font: FONT.text, size: 12.6, color: colors.hint, base: 1189.3, words: [{ t: 'tip', x: 338.5, ls: -0.73 }, { t: 'include', x: 446.5, ls: 0.27 }, { t: 'the', x: 602.5, ls: -1.07 }, { t: 'city', x: 676.0, ls: -0.34 }, { t: 'name', x: 758.1, ls: 0.1 }] },
  /** The dash of the tip: an em dash is 20 px wide in Anybody and 38 px on the pin, so it is a rule. */
  tipDash: { x: 398.5, y: 1176.7, w: 38, h: 3.9 },
  placeholder: { font: FONT.text, size: 14.5, color: colors.placeholder, base: 1412, words: [{ t: 'paste', x: 184.8, ls: -0.63 }, { t: 'your', x: 322.3, ls: -0.08 }, { t: 'note', x: 440.8, ls: -0.74 }, { t: 'or', x: 553.6, ls: -1.3 }, { t: 'text', x: 616.4, ls: -1.52 }, { t: 'here...', x: 715.8, ls: 0.14 }] },
  counter: { font: FONT.text, size: 15.3, color: colors.counter, base: 2220.5, words: [{ t: '0/10,000', x: 100.4, ls: 1.38 }, { t: 'characters', x: 329.8, ls: -1.07 }] },
  paste: { size: 16.6, weight: '800', ls: -0.75, color: colors.ink, text: 'paste', base: 2047.5, left: 571 },
  start: { font: FONT.extra, size: 16.6, ls: -0.08, color: colors.white, text: 'start import', base: 2448.9 },
  pin: { size: 14.6, text: '\u{1F4CD}', left: 128, top: 998.8 },
} as const;

/* ───────────── Motion layers (not on the pin; same tokens and radius family) ───────────── */

export const motionColors = {
  noteInk: '#262626',
  /** Marker stroke under a place: the notes icon's yellow, translucent. */
  marker: 'rgba(255, 213, 46, 0.5)',
  /** Detection blue (the document icon's blue): scan line, city and tip underlines. */
  detect: '#3B78E7',
  detectTrail: 'rgba(59, 120, 231, 0.10)',
  /** The enabled start button. */
  buttonSolid: '#1C1C1E',
  chipFill: '#FFFFFF',
  chipBorder: colors.cardBorder,
  chipShadow: '#000000',
  /** Light on the folding paper panels. */
  shade: '#000000',
} as const;

/**
 * The pasted note: one place per line after the first, which names the city. `place` is the part of the
 * line that is detected (the rest is plain text). Invented places in an invented city.
 */
export const NOTE: readonly { pre: string; place: string; post: string; city?: boolean }[] = [
  { pre: 'weekend in ', place: 'velmora', post: '', city: true },
  { pre: 'brunch at ', place: 'oriel café', post: '' },
  { pre: 'sunset at ', place: 'alto point', post: '' },
  { pre: '', place: 'tide market', post: ', sat only' },
  { pre: 'dinner at ', place: 'fennel & fig', post: '' },
  { pre: 'drinks at ', place: 'lantern bar', post: '' },
];
export const NOTE_TEXT = NOTE.map((l) => l.pre + l.place + l.post).join('\n');

/** The editable text area (a real TextInput over the card), ref px: the note's left edge and first line, the card's
 * right padding, and a bottom edge above the paste control. `max` is the counter's limit (0/10,000). */
export const input = { left: 184.8, right: 98 + 1010 - 89, bottom: 1930, max: 10000, label: 'Note to import' } as const;

/** Note layout, ref px: same face, size and left edge as the placeholder; one line every 66 px (22 pt). */
export const note = {
  font: FONT.text,
  size: 14.5,
  left: 184.8,
  firstBase: 1412,
  pitch: 66,
  /** The scan line runs from `top` to `bottom` (ref px). */
  scanTop: 1355,
  scanBottom: 1790,
  scanTrail: 90,
  scanStroke: 4,
  /** Marker stroke: from 30 % of the line box down to 88 %, overhanging the word by `over` each side. */
  marker: { top: 0.42, bottom: 0.9, over: 6, radius: 6 },
  underline: { gap: 7, stroke: 4 },
} as const;

/** Chips: lift off their words, then sit on the map as pins (label pills). ref px unless marked pt. */
export const chip = {
  height: 72,
  radius: 36,
  padLeft: 24,
  padRight: 30,
  pinGap: 9,
  font: FONT.text,
  size: 12.6,
  pinSize: 11, // pt
  /** Scale of a chip once it sits on the map. */
  mapScale: 0.8,
} as const;

/* ───────────── The map printed inside the folded paper (card-local ref px: 0..1010 × 0..868) ───────────── */

/** Palette from the screen: the page greys, the glows' blue for water, the chat icon's green washed out. */
export const mapColors = {
  land: '#F3F0E9',
  park: '#DEE9D4',
  water: '#D3E2EE',
  shore: '#C3D5E4',
  street: '#FFFFFF',
  casing: '#E3DED4',
  minor: '#FBFAF6',
  creaseDark: 'rgba(0,0,0,0.07)',
  creaseLight: 'rgba(255,255,255,0.85)',
  route: '#3B78E7',
  routeCasing: '#FFFFFF',
  dot: '#3B78E7',
  dotRing: '#FFFFFF',
  shadow: '#000000',
} as const;

export const MAP = {
  /** Sea in the top right; the headland of "alto point" juts into it. */
  sea: 'M 430 0 C 470 80 560 120 640 130 C 700 138 760 118 792 150 C 818 176 782 214 802 246 C 832 292 920 322 1010 382 L 1010 0 Z',
  river: 'M 990 370 C 860 420 760 400 660 470 C 560 540 430 500 300 560 C 200 605 90 580 -10 600',
  parks: [
    { cx: 330, cy: 340, rx: 120, ry: 62 },
    { cx: 860, cy: 720, rx: 96, ry: 58 },
  ],
  major: [
    'M -10 250 C 220 230 420 310 620 300 C 780 292 900 470 1020 520',
    'M 230 -10 C 250 300 330 560 420 880',
    'M -10 770 C 300 700 620 800 1020 690',
    'M 600 120 C 610 400 680 640 760 880',
  ],
  minorAngle: -12, // degrees
  minorGap: 70,
  minorW: 6,
  majorW: 13,
  casingW: 18,
  riverW: 28,
  shoreW: 34,
  seaShoreW: 4,
  creaseW: 2,
} as const;

/** The five places on the map (dot centres), in the note's order. */
export const SPOTS: readonly (readonly [number, number])[] = [
  [165, 290], // oriel café
  [770, 192], // alto point
  [640, 452], // tide market
  [150, 640], // fennel & fig
  [560, 760], // lantern bar
];

/** Pins on the map, pt at 402 pt wide. */
export const pin = { dotR: 4.4, coreR: 2.7, gap: 3, shadowRx: 9, shadowRy: 2.6, shadowBlur: 2.2, route: 2.4, routeCasing: 4.6 } as const;

/** The tip's underline under "include the city name". ref px. */
export const tipUnderline = { x: 446.5, w: 420, y: 1199, h: 4 } as const;

/** "found N places": the label of the start button once places are found. */
export const found = { pre: 'found ', post: ' place', plural: 's' } as const;

/** Digit advances (font units of 2000) — the roller's column is the widest digit plus the word's tracking. */
export const DIGIT_ADV = { text: 1186 / 2000, extra: 1309 / 2000 } as const;

/** The three app icons (Blender renders, one layer each so they can fan apart): box of each PNG in ref px. */
export const ICONS = {
  notes: { x: 383, y: 358, w: 213, h: 212 }, // incl. its soft drop shadow
  chat: { x: 499, y: 418, w: 214, h: 213 }, // incl. its soft drop shadow
  doc: { x: 665, y: 373, w: 142, h: 190 },
} as const;
