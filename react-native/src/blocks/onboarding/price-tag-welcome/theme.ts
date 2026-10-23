/**
 * Price Tag Welcome — every colour, size and position of the screen.
 *
 * Positions are in "ref px": pixels of the pin's screen rectified to 1206 × 2622 (an iPhone 17 Pro
 * at 3×, 402 pt wide). The component multiplies them by `screenWidth / 1206`, so on a 402 pt screen
 * they land on the pin's pixels and on a 393 pt screen everything shrinks by the same factor
 * (nothing reflows, nothing wraps). Font sizes are in pt for that 402 pt screen and scale the same way.
 * The top content is measured from the screen's top, the button from its bottom.
 */

export const REF_W = 1206;
export const REF_H = 2622;
/** Font scale reference: 1206 ref px = 402 pt. */
export const REF_PT = 402;
/** SF Pro: the baseline sits this many em under the top of a Text with no lineHeight. */
export const ASCENT = 0.953;

export const colors = {
  page: '#FCFBFC', // base under the glow (251.8, 251.2, 251.5)
  ink: '#1C1C1E',
  sub: '#5E6260',
  price: '#111113',
  tag: '#FFFFFF',
  button: '#0387F5',
  buttonRim: 'rgba(215,250,255,0.85)',
  buttonText: '#FFFFFF',
  /** Etched into the frost: the groove (icy navy) and the highlight under its lower edge. */
  etch: 'rgba(38,78,112,0.72)',
  etchLight: 'rgba(255,255,255,0.95)',
  /** The countdown sits on the product (often dark): scraped-white figures with a navy edge below. */
  scrape: 'rgba(248,252,255,0.96)',
  scrapeEdge: 'rgba(28,58,92,0.6)',
  shadow: '#000000',
} as const;

/**
 * The pastel glow: three gaussian blobs fitted to the pin (rms error 0.95 / 255 per channel).
 * cx, cy and sigma in ref px; `peak` is the colour at the blob's centre (sRGB).
 */
export const glow = [
  { cx: 306.8, cy: 576.4, sigma: 267, peak: [233.2, 242.4, 253.7] }, // blue, upper left
  { cx: 964.0, cy: 737.3, sigma: 244, peak: [252.7, 238.5, 228.3] }, // peach, right
  { cx: 430.9, cy: 1329.8, sigma: 302, peak: [234.3, 246.4, 238.8] }, // green, under the copy
] as const;
export const GLOW_BASE = [251.8, 251.2, 251.5] as const;

/** Text styles: size in pt (at 402 pt), weight, colour, extra letter spacing in pt. */
export const type = {
  headline: { size: 38.5, weight: '600', color: colors.ink, ls: 0 },
  sub: { size: 18.5, weight: '400', color: colors.sub, ls: 0 },
  price: { size: 21, weight: '500', color: colors.price, ls: 0 },
  wait: { size: 15, weight: '600', color: colors.etch, ls: 0 },
  timer: { size: 14, weight: '600', color: colors.scrape, ls: 0.2 },
  button: { size: 21.8, weight: '600', color: colors.buttonText, ls: 0 },
} as const;
export type TextStyleSpec = (typeof type)[keyof typeof type];

/**
 * Spacing scale, derived from the pin (ref px; pt = ÷ 3 at 402 pt). The pin is built on a 4 pt
 * (12 px) step: the sticker outline, the gaps between stickers and the copy blocks all fall on it
 * within ±1 pt. Every layout number below is one of these or a measured position.
 */
export const space = {
  outline: 12, // 4 pt — white die-cut rim around every product
  canvasPad: 24, // 8 pt — transparent margin of every render (room for the outline)
  stickerGap: 96, // 32 pt — gap between neighbouring stickers (laptop→shirt, shirt→bag)
  screenInsetButton: 98, // 32.7 pt — the button's side inset (pin: 98 / 1108)
  headlineLine: 143, // 47.7 pt — line pitch of the two-line headline
  subLine: 75, // 25 pt — line pitch of the subtitle
  tagPad: 18, // 6 pt — least side padding of the etched "Wait 24h" inside a tag
  frostMargin: 42, // 14 pt — room around a sticker for the rime halo
  etchDrop: 9, // 3 pt — how far etched text sinks as it melts
  hit: 132, // 44 pt — minimum touch target
} as const;

/**
 * The frost (sRGB 0–255; the shader converts them to Display P3). Cell sizes in pt at 402 pt.
 * iceHi: rime white · iceMid: crystal blue · iceDeep: cell borders, drops · coolTint: × luminance
 * for the frozen product.
 */
export const frost = {
  iceHi: [236, 245, 252],
  iceMid: [186, 214, 238],
  iceDeep: [74, 122, 168],
  coolTint: [200, 224, 255],
  cell: 6.5,
  tagCell: 3.5,
  tagAlpha: 0.86,
  halo: 0.55,
} as const;

export const radius = {
  icon: 43, // 14.3 pt — the card icon's corners (23 % of 189)
  // Tags and the button are pills: radius = height / 2.
} as const;

/** One product sticker: its render canvas (ref px), the price tag on it, and copy. */
export type Item = {
  key: 'headphones' | 'laptop' | 'shirt' | 'bag' | 'camera';
  /** Canvas origin at rest (ref px) and size. */
  x: number;
  y: number;
  w: number;
  h: number;
  /** The tag: centre (ref px, absolute at rest), size, tilt (deg, clockwise). */
  tag: { cx: number; cy: number; w: number; h: number; tilt: number };
  price: string;
  name: string;
};

export const items: readonly Item[] = [
  { key: 'headphones', x: 24, y: 1616, w: 288, h: 324, tag: { cx: 225, cy: 1870, w: 210, h: 88, tilt: -4.5 }, price: '$579', name: 'Headphones' },
  { key: 'laptop', x: 330, y: 1606, w: 324, h: 312, tag: { cx: 553, cy: 1882, w: 240, h: 92, tilt: 5.5 }, price: '$1599', name: 'Laptop' },
  { key: 'shirt', x: 702, y: 1620, w: 300, h: 312, tag: { cx: 947, cy: 1870, w: 160, h: 90, tilt: -4 }, price: '$54', name: 'Polo shirt' },
  { key: 'bag', x: 1050, y: 1620, w: 324, h: 312, tag: { cx: 1318, cy: 1870, w: 186, h: 90, tilt: 3 }, price: '$238', name: 'Tote bag' },
  { key: 'camera', x: 1410, y: 1626, w: 324, h: 300, tag: { cx: 1672, cy: 1862, w: 200, h: 90, tilt: -3.5 }, price: '$849', name: 'Camera' },
] as const;

/**
 * The conveyor: the row repeats every PERIOD ref px (five stickers, the camera → headphones gap
 * kept at the pin's sticker gap). An item is drawn at its origin + offset, wrapped into
 * [WRAP_LEFT, WRAP_LEFT + PERIOD), so it leaves on one side as it enters on the other, always
 * off screen.
 */
export const PERIOD = 1764;
export const WRAP_LEFT = -360;
/** The row's touch band (ref px): the stickers and their tags. */
export const row = { top: 1590, bottom: 1990 } as const;

/** Pendulum pivot of a tag, in its own frame (fraction of w and h from the centre): top edge, left of centre. */
export const PIVOT = { x: -0.2, y: -0.5 } as const;

export const layout = {
  icon: { x: 486, y: 656, w: 237, h: 236, box: { x: 510, y: 680, w: 189, h: 188 } },
  headline: [{ base: 1044 }, { base: 1187 }],
  sub: [{ base: 1339 }, { base: 1414 }],
  /** The button from the screen's bottom: top edge 379 px above it, 211 px tall. */
  button: { x: 98, w: 1010, h: 211, fromBottom: 379, base: 131 },
  /**
   * The etched countdown on a frozen product: its centre as a fraction of the sticker canvas — on
   * the laptop's screen (measured on the render), elsewhere the product's middle.
   */
  timer: { laptop: { fx: 0.56, fy: 0.33 }, other: { fx: 0.5, fy: 0.44 } },
} as const;

export const copy = {
  appName: 'Interpause',
  headline: ['Welcome to', 'Interpause'],
  sub: ['Stop impulse purchases, understand', 'the urge, and decide with clarity.'],
  wait: 'Wait 24h',
  /** The countdown starts here and ticks down once a second. */
  countdown: 24 * 3600,
  button: 'Get started',
  a11yItemHint: 'Double-tap to freeze the price for 24 hours, again to thaw it. Drag sideways to browse.',
  a11yFrozen: 'frozen, wait 24 hours before buying',
} as const;

/** Width of "Wait 24h" in SF Pro Semibold, in em: the etched label shrinks to fit narrow tags. */
export const WAIT_EM = 4.15;
