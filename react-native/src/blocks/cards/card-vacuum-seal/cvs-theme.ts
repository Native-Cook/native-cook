// Card Vacuum Seal — every colour and box, measured on the pin (Dribbble 27250589, its open frame at 2.9 s
// and its sealed still, rectified face-on onto the 402 × 874 pt canvas). Row ids refer to
// .work/card-vacuum-seal/design.md. Only this file holds literal colours and sizes.

export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;

export const ref = {
  color: {
    page: '#0A0A0C', // pg: #020203 measured, lifted to the nearest tinted value (DESIGN-SYSTEM §4)
    bloom: '#6B3FD8', // pg: the violet light the pin's room throws on the screen's upper left
    tile: '#1C1B20', // tl: #161817 / #1F1E24 / #262233 → median
    tilePressed: '#25242A',
    ink: '#F4F3F7', // tx: white type, tinted cool
    inkMute: '#8E8D96', // tx2: "Money receive" grey
    cardLight: '#171C2E', // cd: pin's open-state #1D233A, #21273E; under the film the pin's card is near black, so ~0.75x of that
    cardMid: '#0D111F', // cd: centre #171E30 (x 0.75)
    cardDark: '#06080F', // cd: right #0D1323, #0F141A (x 0.55: the pin's right half under the film is almost black)
    letter: '#E9ECF4', // cd: the wordmark's silver white
    red: '#DA3540', // pr: Protect disc #D53141 (pin), concept #E5352B
    redFlash: '#F7B8C0', // pr: the pale pink flash on press (pin 3.9 s)
    amber: '#D8B43E', // pr2: Protected disc #D4B23F
    amberInk: '#2A2208', // pr2: the tick in the amber shield
    violet: '#8A6FFB', // sd: Show details disc, upper left
    magenta: '#E04DEE', // sd: lower right
    blue: '#A9C2EA', // st: Settings disc #A6BDE3
    blueDeep: '#8FA8DA',
    lilac: '#E08BEF', // cl: close disc #DB83E7
    lilacLight: '#F0B2F6',
    gold: '#D8B43E', // rv: the "$" badge
    green: '#35D27E', // tr: "Completed"
    film: '#C9B8FF', // the film's faint lilac tint (4 %)
    gel: '#D2BCFF', // the pin's lilac-white room light, reflected in the film's left third (spec #E08BEF, shifted to the pin's lavender)
    shadow: '#050308', // the film's contact shadow on the page and its far-side line
  },
  type: {
    title: { family: 'CvsSemiBold', size: 17, tracking: -0.013 }, // hd: "Cards"
    tile: { family: 'CvsMedium', size: 14, tracking: -0.004 }, // tl: tile labels
    label: { family: 'CvsMedium', size: 15, tracking: -0.006 }, // rv: "Revenue", "See all"
    figure: { family: 'CvsMedium', size: 40, tracking: -0.022 }, // rv: "42,220"
    heading: { family: 'CvsSemiBold', size: 19, tracking: -0.017 }, // tr: "Recent transactions"
    name: { family: 'CvsMedium', size: 16, tracking: -0.01 }, // tr: names
    amount: { family: 'CvsSemiBold', size: 16, tracking: -0.01 }, // tr: amounts
    small: { family: 'CvsMedium', size: 13, tracking: 0 }, // tr: "Money received", "Completed"
    // drawn into the card's map (Skia), not React text
    wordmark: { size: 28, tracking: -0.02 }, // cd: medium grotesk; pin "Revolut" ≈ 120 pt wide on the rectified card (Halden here ≈ 89 pt, at the lead's request)
    holder: { size: 18, tracking: 0.04 }, // cd: medium caps, ≈ 135 pt wide (pin "FANCY.DESIGN" ≈ 178 pt)
  },
  radius: { card: 16, tile: 26, badge: 6 },
  light: { az: 315, el: 40 }, // one soft key, upper left (the film's highlights sit on its upper and left edges)
} as const;

export const sys = {
  surface: { page: ref.color.page, tile: ref.color.tile, tilePressed: ref.color.tilePressed },
  ink: { primary: ref.color.ink, secondary: ref.color.inkMute, success: ref.color.green },
} as const;

/** every box in design pt on the 402 × 874 canvas; `y` anchors are top-of-screen (`yt`) */
export const comp = {
  header: { cy: 84, arrowX: 24, arrowSize: 22, closeX: 346, closeD: 32 }, // hd
  card: { x: 24, y: 128, w: 354, h: 223, r: 16, padX: 20, wordBase: 50, holderBase: 199 }, // cd (ISO ID-1 ratio)
  tiles: { y: 367, w: 112, h: 112, gap: 9, discD: 56, discTop: 14, labelBase: 94 }, // tl
  revenue: {
    y: 491,
    h: 116,
    padX: 20,
    badge: 22,
    badgeY: 22,
    labelY: 33,
    figureBase: 92, // rv
    bars: { x0: 190, x1: 356, base: 92, n: 24 },
  },
  list: { y: 619, h: 300, padX: 20, headBase: 42, rowY: 66, rowH: 64, avatar: 44, amountW: 82, textGap: 12 }, // tr
  // the film, in design units at a 354 pt wide card (they scale with the card's width): how far the sealed skin reaches
  // past the card (lip) and how long the ears are; the draped sheet is the card plus sheetPad on every side, lowered by
  // sheetDy; it falls `fall` before it lies on the card; `dome` is the sealed face's crown.
  // Creases use the authorized two-octave shader fallback: no raster map is shipped.
  film: {
    lip: 6,
    ear: 14,
    sheetPad: [34, 38.5],
    sheetDy: 14.5,
    sheetR: 44,
    fall: 60,
    dome: 22,
  },
  // what the film canvas covers past the card (design units): the sheet falls from above and the bubble leaves below
  // (the top covers the whole fall: at 40 the falling sheet was cut by the canvas's top edge, a hard horizontal line)
  canvasPad: { l: 40, t: 100, r: 40, b: 80 },
} as const;

export const COPY = {
  title: 'Cards',
  bank: 'Halden',
  holder: 'NORTH.STUDIO',
  tiles: { details: 'Show details', protect: 'Protect Card', protected: 'Protected', settings: 'Settings' },
  revenue: 'Revenue',
  revenueValue: '42,220',
  list: 'Recent transactions',
  seeAll: 'See all',
  rows: [
    { name: 'Tidewell Prints', note: 'Money received', amount: '$739.65', status: 'Completed' },
    { name: 'Oren Calloway', note: 'Money received', amount: '$943.65', status: 'Completed' },
  ],
  a11y: {
    back: 'Back',
    close: 'Close',
    protect: 'Protect card',
    protectHint: 'Seals the card so it cannot be used. Hold to watch the film tighten.',
    sealed: 'Card protected',
    open: 'Card protection off',
    card: (sealed: boolean) => `Halden card, North Studio${sealed ? ', protected' : ''}`,
  },
} as const;

/** bar heights of the revenue chart, design pt: dots that grow into bars (pin) */
export const BARS = [3, 3, 3, 3, 3, 3, 3, 3, 4, 6, 9, 12, 15, 22, 18, 30, 26, 38, 33, 46, 40, 56, 50, 64] as const;

/** '#RRGGBB' mixed toward another by t */
function mixHex(a: string, b: string, t: number) {
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const ch = (shift: number) => Math.round(((pa >> shift) & 255) * (1 - t) + ((pb >> shift) & 255) * t);
  return `#${((1 << 24) | (ch(16) << 16) | (ch(8) << 8) | ch(0)).toString(16).slice(1)}`;
}

/** the lower page sits in shade (the pin's light falls off): the page colour over the tile fill, 0 at design y 470 → 0.30 at 874.
 *  Applied to the panels' fills, not over their text, so every label keeps its contrast. */
export function panelShade(yTop: number, yBottom: number): [string, string] {
  const at = (y: number) => Math.min(1, Math.max(0, (y - 470) / 404)) * 0.3;
  return [mixHex(ref.color.tile, ref.color.page, at(yTop)), mixHex(ref.color.tile, ref.color.page, at(yBottom))];
}

export type Layout = {
  W: number;
  H: number;
  S: number;
  top: number;
  bottom: number;
  x: (v: number) => number;
  yt: (v: number) => number;
};

/** design pt → screen pt: x scales with width, y keeps its distance from the top inset */
export function makeLayout(W: number, H: number, insetTop: number, insetBottom: number): Layout {
  const S = W / REF.w;
  return {
    W,
    H,
    S,
    top: insetTop,
    bottom: insetBottom,
    x: (v: number) => v * S,
    yt: (v: number) => insetTop + (v - REF.safeTop) * S,
  };
}
