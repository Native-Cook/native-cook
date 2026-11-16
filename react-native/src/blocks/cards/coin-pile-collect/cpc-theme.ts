// Coin Pile Collect — every measured number and colour of the screen (402 × 874 pt canvas, 1 pt = 3 px on the
// recording device). Component code reads `sys` and `comp`; only this file reads `ref`.
//
// Source: Dribbble 27254466 (video). The pin is a perspective render of an "offers" screen; the block copies the
// screen face-on. Colours are 5×5 px means of the pin's flat surfaces (the empty teal tile, the lime and magenta
// tiles); sizes are the pin's tiles flattened to the 402 grid; the coins were measured on the full-pile frame
// (rectified tile, 522 px = 174 pt wide): a flat-lying face is about 70 pt across, the wall about a seventh of the
// face. Brands are invented: the bank is "Halden", the coin's mark a plain H.

export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34, contentH: 714 } as const;

export const ref = {
  color: {
    page: '#07040C', // pin: near black with an aubergine glow low left
    pageGlow: '#22103C',
    pageGlow2: '#04040C',
    ink: '#F7F4EE', // title, tile copy
    teal: '#1E547A', // sampled 5×5, three spots
    lime: '#6CA824',
    limeInk: '#F7FBEA',
    magentaTop: '#A94696',
    magentaBottom: '#8F3A84',
    navyTop: '#2B3868',
    navyBottom: '#10173D',
    amber: '#F4C13A', // pill + amount
    amberLit: '#F8D878', // the amount while coins pass
    pillInk: '#3A2600', // the pin's cream label fails contrast on amber (1.6:1): contrast fix, 8.5:1
    coinFaceA: '#1D5666', // emblem face, lit
    coinFaceB: '#0A242D', // mesh face
    coinRim: '#2F7C96',
    cyan: '#3FE9FF', // the pin's inner glow
    pink: '#C8689F', // the exchange coin's two faces
    copper: '#E6AE8E',
    pinkRim: '#E2A0B8',
    discA: '#B7F04A',
    discB: '#F2B92E',
    discC: '#7C6CF0',
    discInk: '#14131C',
    cardTop: '#0B1450',
    cardBottom: '#1D2D86',
    shadow: '#03141B',
  },
  type: {
    title: { size: 56, lineHeight: 56, tracking: -0.035 },
    tile: { size: 20, lineHeight: 24, tracking: -0.012 },
    copy: { size: 15, lineHeight: 18, tracking: -0.004 },
    amount: { size: 30, lineHeight: 32, tracking: -0.01 },
    pill: { size: 17, lineHeight: 20, tracking: -0.006 },
    small: { size: 17, lineHeight: 20, tracking: -0.008 },
  },
  space: [0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64] as const,
  radius: { teal: 40, tile: 36, disc: 20, pill: 30 },
  light: { az: 315, el: 40 }, // key light: upper left; shadows fall to the lower right
} as const;

/** boxes, pt on the 402 canvas; y is the top edge, measured from the status bar's bottom (safe top = 62) */
export const comp = {
  avatars: { r: 20, pitch: 28, right: 20, y: 66 },
  back: { x: 24, y: 118, size: 24 },
  title: { x: 24, capTop: 158 },
  lime: { x: 24, y: 290, w: 172, h: 180 },
  teal: { x: 204, y: 250, w: 174, h: 340 },
  magenta: { x: 24, y: 478, w: 172, h: 162 },
  navy: { x: 204, y: 598, w: 174, h: 162 },
  premium: { x: 24, y: 664 },
  // inside the teal tile (tile-local)
  copy: { x: 18, earnedBase: 34, amountBase: 68, periodBase: 88 },
  pill: { x: 24, y: 268, w: 150, h: 60 }, // right inset 0, bottom inset 12
  mouth: { x: 99, y: 282 }, // where a coin goes into the pill
  keepOut: { w: 174, h: 84 }, // the heap does not settle over the copy
  coin: { r: 35, h: 4.2, count: 26 }, // face radius and half thickness, pt
  exchange: { cx: 88, cy: 54, r: 50, h: 5 }, // the magenta tile's coin
  seed: 2,
} as const;

/** "Get your / Premium / card": display type, three lines (fits the 172 pt column at the 1.12 font cap) */
export const PREMIUM = { size: 34, lineHeight: 36, tracking: -0.03 } as const;

export const COPY = {
  title: ['Today’s', 'offers'],
  earned: 'You earned',
  period: 'in September',
  action: 'Collect',
  protect: ['Protect your', 'funds'],
  exchange: ['Exchange', 'currency'],
  premium: ['Get your', 'Premium card'],
  bank: 'halden',
  cardUrl: 'HALDEN.CARD',
  monograms: ['A', 'M', 'K'],
  amountCents: 22034,
  a11yTitle: 'Today’s offers',
  a11yCollect: (a: string) => `Collect ${a}`,
  a11yEmpty: 'Nothing left to collect',
  a11yAmount: (a: string) => `You earned ${a} in September`,
  hintCollect: 'Hold to pour the coins into the button, or tap to collect everything.',
} as const;

/** one font scale for text that is drawn: follows the system's, capped so the fixed bento keeps its shape */
export function fontScaleOf(systemScale: number) {
  return Math.min(Math.max(systemScale, 1), 1.12);
}

/** layout for any window: the 402-wide design is scaled by S and centred; S also shrinks so the column fits the height */
export function makeLayout(W: number, H: number, insetTop: number, insetBottom: number) {
  const availH = Math.max(H - insetTop - insetBottom, 1);
  const S = Math.min(W / REF.w, availH / REF.contentH);
  const ox = (W - REF.w * S) / 2;
  const oy = insetTop - REF.safeTop * S;
  return { W, H, S, ox, oy, insetTop, insetBottom };
}
export type CpcLayout = ReturnType<typeof makeLayout>;

/** the teal tile's rectangle in window points */
export function hopperRect(L: CpcLayout) {
  return { x: L.ox + comp.teal.x * L.S, y: L.oy + comp.teal.y * L.S, w: comp.teal.w * L.S, h: comp.teal.h * L.S };
}
