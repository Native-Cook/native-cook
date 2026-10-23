/**
 * Wallet First Deposit — every colour, size and position of the two screens.
 *
 * Positions are in "ref px": pixels of the two Pinterest screens rectified to 1206 × 2622
 * (an iPhone 17 Pro at 3×, 402 pt wide). The component multiplies them by `screenWidth / 1206`,
 * so on a 402 pt screen they land on the same pixels as the pins. Font sizes are in pt for that
 * 402 pt screen and scale the same way. State A is the empty wallet (pin 15), state B the funded
 * one (pin 14).
 */

export const REF_W = 1206;
/** The pins were taken on a phone whose top safe area is 62 pt (186 ref px). */
export const REF_INSET = 186;
/** Font scale reference: 1206 ref px = 402 pt. */
export const REF_PT = 402;

/** SF Pro: the baseline sits 0.953 em under the top of a Text with no lineHeight. */
export const ASCENT = 0.953;

export const colors = {
  page: '#FFFFFF',
  card: '#F6F6F8',
  pill: '#EEEEEF',
  ink: '#000000',
  secondary: '#8E8E93',
  pillText: '#6D6D72',
  label: '#9C9CA0',
  axis: '#C5C5C9',
  decimals: '#BEBEC0',
  green: '#1F9F3B',
  white: '#FFFFFF',
  nut: '#C4C4C6',
  track: '#DADADC',
  barStart: '#1455BA',
  barEnd: '#1A98E6',
  dot: '#D9474C',
  hairline: '#E7E7EA',
  orange: '#EF8838',
  peach: '#F7DCC8',
  plotTop: '#E3E3E5',
  forecastTop: '#E5E5E7',
  dash: '#CFCFD3',
  cafe: '#0B7449',
} as const;

/** Text styles: size in pt (at 402 pt), weight, colour, extra letter spacing in pt. */
export const type = {
  title: { size: 13.3, weight: '500', color: colors.ink, ls: -0.2 },
  body: { size: 13.3, weight: '500', color: colors.ink, ls: -0.2 },
  bodyGrey: { size: 13.3, weight: '400', color: colors.secondary, ls: -0.35 },
  banner: { size: 12.7, weight: '500', color: colors.ink, ls: 0 },
  bannerGrey: { size: 12.7, weight: '400', color: colors.secondary, ls: 0 },
  sub: { size: 13.3, weight: '400', color: colors.secondary, ls: 0 },
  pill: { size: 13.3, weight: '500', color: colors.pillText, ls: 0 },
  button: { size: 13.3, weight: '500', color: colors.white, ls: 0 },
  merchant: { size: 16.4, weight: '500', color: colors.ink, ls: 0 },
  amount: { size: 17.4, weight: '500', color: colors.ink, ls: -1.2 },
  spent: { size: 13.8, weight: '400', color: colors.secondary, ls: 0 },
  spentValue: { size: 19.4, weight: '500', color: colors.ink, ls: -1.2 },
  labelA: { size: 13.1, weight: '400', color: colors.label, ls: -1.2 },
  labelB: { size: 10.5, weight: '400', color: colors.label, ls: -0.4 },
  axis: { size: 11.8, weight: '400', color: colors.axis, ls: 0 },
  balance: { size: 49, weight: '500', color: colors.ink, ls: -3.4 },
} as const;

export type TextStyleSpec = (typeof type)[keyof typeof type];

/** A rounded rectangle in ref px. */
export type Box = { x: number; y: number; w: number; h: number; r: number };

const M = 22.25; // card margin
const CW = 1159.25; // card width

export const layout = {
  // Header (both states).
  avatar: { cx: 100, cy: 268, d: 109 },
  dot: { cx: 141.5, cy: 226.5, d: 11 },
  settings: { cx: 1097, cy: 269, d: 120 },
  nut: { cx: 1098, cy: 267.5, r: 30.5, hole: 9.5, ring: 19 },
  total: { x: 45, base: 474 },
  balance: { x: 50, base: 635 },

  // The debit card: A in the banner, B in the header (sizes are the card's at A).
  card: { w: 232, h: 147, r: 18, a: { cx: 1005.5, cy: 882 }, b: { cx: 595.5, cy: 268.5, s: 0.752 }, ctrl: { x: 960, y: 190 } },

  // Banner (A only).
  banner: { x: M, y: 745, w: CW, h: 313.8, r: 48 } as Box,
  bannerLines: [
    { x: 57, base: 842 },
    { x: 56, base: 898 },
    { x: 55, base: 953 },
  ],
  bar: { x0: 57, x1: 1148, cy: 1000.5, h: 12, rest: 0.428, fade: 80 },

  // Receive / Send (B only).
  pills: [
    { x: 46.3, y: 722.4, w: 330.9, h: 127.2, icon: { cx: 125.5, cy: 786.5 }, text: { x: 172, base: 800 } },
    { x: 394.2, y: 722.4, w: 330.5, h: 127.2, icon: { cx: 500.5, cy: 786.5 }, text: { x: 546, base: 800 } },
  ],

  // Transactions (B only).
  transTitle: { x: 45, base: 1002 },
  seeAll: { x: 984.8, y: 942.9, w: 168.2, h: 85.1, text: { x: 1015, base: 1000 } },
  txCard: { x: M, y: 1051.7, w: CW, h: 436.2, r: 72 } as Box,
  hairline: 1265.5,
  txRows: [
    { logo: { cx: 130, cy: 1160, d: 121 }, title: { x: 237, base: 1150 }, sub: { x: 239, base: 1207 }, amount: { right: 1132, base: 1180 } },
    { logo: { cx: 129.5, cy: 1380, d: 122 }, title: { x: 239, base: 1369 }, sub: { x: 239, base: 1427 }, amount: { right: 1132, base: 1400 } },
  ],

  // Recent payees: the title in both states, three action cards in A, four payee cards in B.
  recent: { a: { x: 51, base: 1173 }, b: { x: 46, base: 1615 } },
  seeAll2: { x: 983.3, y: 1555.3, w: 169.5, h: 84.7, text: { x: 1014, base: 1612 } },
  actions: {
    y: 1234.8,
    h: 414.6,
    w: 381.6,
    gap: 7.25,
    r: 81,
    iconCy: 1335.5,
    iconDx: [98.25, 98, 98.5],
    plus: 80,
    arrow: 66,
    stroke: 10,
    title: { dx: 56.5, base: 1519 },
    sub: { dx: 56.5, base: 1573 },
  },
  payees: {
    y: 1665,
    h: 307,
    w: 285.3,
    gap: 6,
    r: 72,
    orbCy: 1785.5,
    orbD: 122,
    badgeD: 46,
    badgeOff: 34,
    name: { base: 1901 },
  },

  // Analytics: the header and the chart card move down by `shift` from A to B.
  analytics: { a: { x: 45, base: 1773 }, b: { x: 45, base: 2097 } },
  more: { x: 1007, w: 146, h: 84, a: { y: 1715.5 }, b: { y: 2039.4 }, text: { x: 1038, a: 1771, b: 2096 } },
  chart: {
    x: M,
    w: CW,
    h: 582.3,
    r: 78,
    a: { y: 1823.2 },
    b: { y: 2147.5 },
    // Inside the card, relative to its top.
    spent: { a: { x: 76, dy: 88.8 }, b: { x: 69, dy: 87.5 } },
    value: { a: { x: 74, dy: 164.8 }, b: { x: 68, dy: 156.5 } },
    label: { a: { right: 1123, dy: 256.8 }, b: { right: 1128, dy: 157.5 } },
    plotA: { x0: 72, x1: 1128, top: 294.8, bottom: 476.8 },
    plotB: { x0: 72, x1: 1134, bottom: 427.5 },
    axis: { xs: [81, 251, 425.5, 599, 774.5, 947.5, 1120], a: 519.8, b: 470.5 },
    // Funded chart, in absolute ref px of pin 14 (card top 2147.5).
    line: [
      [72, 2569], [146, 2569], [244, 2486], [262, 2475], [282, 2466], [314, 2466], [350, 2430],
      [366, 2423], [390, 2421], [400, 2418], [416, 2418], [464, 2408], [470, 2407],
    ],
    lineBottom: 2575,
    forecast: [
      [470, 2434], [608, 2433], [638, 2420], [722, 2422], [736, 2411], [758, 2403], [810, 2399],
      [826, 2395], [854, 2384], [950, 2383], [962, 2375], [998, 2355], [1058, 2344], [1070, 2336], [1134, 2330],
    ],
    trend: [[72, 2569], [146, 2569], [250, 2492], [318, 2484], [352, 2442], [470, 2434]],
  },
} as const;

/** Card rectangles of the action row (A) and the payee row (B). */
export function actionBox(i: number): Box {
  'worklet';
  const a = layout.actions;
  return { x: M + i * (a.w + a.gap), y: a.y, w: a.w, h: a.h, r: a.r };
}
export function payeeBox(i: number): Box {
  'worklet';
  const p = layout.payees;
  return { x: M + i * (p.w + p.gap), y: p.y, w: p.w, h: p.h, r: p.r };
}

/** Orbs: a diagonal gradient from the lower left to the upper right, five stops. */
export const orbs = {
  avatar: ['#0A0596', '#2336B5', '#4196D9', '#70C4E7', '#C3EBFC'],
  dieter: ['#0A63A8', '#1A82C1', '#55B9D0', '#97D6C5', '#EAF8B4'],
  payees: [
    ['#08A606', '#5E9E78', '#BB8DDC', '#DEAEDB', '#F7E5C1'],
    ['#7FA012', '#77B07A', '#63BBC6', '#94D7BD', '#E5FAB6'],
    ['#B02BAF', '#8C3CB9', '#3F60C9', '#759EBE', '#D8EEB5'],
    ['#A70300', '#C01D0D', '#F84535', '#F28C4B', '#E8EA7E'],
  ],
} as const;

export const copy = {
  total: 'Total balance',
  banner: ['You’ll be able to set up your debit card once', 'your verification is completed.', ' This usually', 'takes a few hours. Please wait…'],
  verified: 'Verified. Your card is ready.',
  receive: 'Receive',
  send: 'Send',
  transactions: 'Transactions',
  seeAll: 'See all',
  recent: 'Recent payees',
  actions: [
    ['Invite friends', 'Share the app'],
    ['Send money', 'Start transfer'],
    ['Receive funds', 'Share details'],
  ],
  tx: [
    ['Hearth Coffee', 'Purchase. Yesterday 8:10 am', '$4.85'],
    ['Dieter E.', 'Received. 12 Nov 9:20 am', '+ $109.25'],
  ],
  payees: ['Konstantin', 'Vasya', 'Aleksa', 'Joshua'],
  analytics: 'Analytics',
  more: 'More',
  spent: 'Spent this week',
  labelA: '$312',
  labelB: '$1,92k',
  axis: ['1', '2', '3', '4', '5', '6', '7'],
} as const;
