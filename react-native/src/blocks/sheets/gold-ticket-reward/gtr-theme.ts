// Gold Ticket Reward — layout, colour and copy.
//
// Every position below was measured on the pin (two 274 × 581 px phones, "before" and "after")
// and is written in those pin pixels, then mapped to a 402 × 874 pt reference screen:
//   x            → px · S
//   top content  → px · S                    (the page under the sheet hangs from the top)
//   sheet        → REF_H − (PIN_H − px) · S  (the sheet stands on the bottom edge)

export const REF_W = 402;
export const REF_H = 874;
export const PIN_W = 274;
export const PIN_H = 581;
export const S = REF_W / PIN_W;

export const X = (px: number) => px * S;
export const YT = (px: number) => px * S;
export const YB = (px: number) => REF_H - (PIN_H - px) * S;

export type Box = { x: number; y: number; w: number; h: number };

/** A box from pin pixels, hanging from the top (`top`) or standing on the bottom edge. */
function box(x0: number, y0: number, x1: number, y1: number, anchor: 'top' | 'bottom'): Box {
  const y = anchor === 'top' ? YT : YB;
  return { x: X(x0), y: y(y0), w: X(x1) - X(x0), h: y(y1) - y(y0) };
}

export const COPY = {
  title: 'Inspection details',
  help: 'Get Help',
  status: 'Expert Assigned',
  expert: 'Arjun Mehta assigned',
  expertRef: 'Rajesh Kumar assigned', // the pin's string, used only to size the font
  verified: 'Verified inspector',
  progress: 'Inspection in Progress',
  section: 'YOU CAN MAKE A DIFFERENCE',
  pros: 'Our professionals work hard',
  prosSub: 'Small gestures keep them going all day',
  water: 'Offer a glass of water',
  thanks: 'THANKS FOR YOUR INTEREST!',
  callback: 'Our Car loan expert team will call you shortly',
  win: 'Book Test Drive to Win',
  prize: '1 Gm Gold Coin',
  benefits: ['Benefits upto 10 Lakhs', '200+ Car Models', 'Compare on the spot'] as const,
  cta: 'Check Now',
};

/** Pin-pixel widths of the strings, used to size each font (state A and B). */
export const WIDTH = {
  title: X(100),
  help: X(43),
  status: X(91),
  expert: X(110),
  verified: X(67),
  progress: X(91),
  section: X(98),
  pros: X(134),
  prosSub: X(122),
  water: X(77),
  thanks: X(132),
  callback: X(228),
  winA: X(130),
  prizeA: X(131),
  winB: X(109),
  prizeB: X(129),
  benefitA: X(83), // "Benefits upto 10 Lakhs" in A; B is 1.33× larger
  ctaA: X(65),
  ctaB: X(62),
};

export const BENEFIT_B_SCALE = 110 / 83;

// ------------------------------------------------------------------ the page under the sheet
export const PAGE = {
  back: { x: X(20), y: YT(54) },
  title: { x: X(42), y: YT(54) },
  help: { x: X(253), y: YT(54), icon: X(204) },
  check: { x: X(136), y: YT(116), r: X(19.5), stroke: X(2.3) },
  status: { x: X(136.5), y: YT(155) },
  card: box(9, 178, 265, 262, 'top'),
  avatar: { x: X(30), y: YT(204), r: X(8) },
  expert: { x: X(48), y: YT(195) },
  badge: box(54, 207.5, 66, 214.5, 'top'),
  verified: { x: X(70), y: YT(211.5) },
  phone: { x: X(242), y: YT(205) },
  divider: YT(232),
  rowIcon: { x: X(25), y: YT(248) },
  progress: { x: X(42), y: YT(248) },
  chevron: { x: X(247), y: YT(248) },
  section: { x: X(10), y: YT(289) },
  card2: box(9, 300, 265, 362, 'top'),
  pros: { x: X(18), y: YT(312) },
  prosSub: { x: X(18), y: YT(323) },
  waterIcon: { x: X(22), y: YT(347) },
  water: { x: X(30), y: YT(347) },
};

// ------------------------------------------------------------------ state A: the white sheet
export const A = {
  // the golden light rising behind the cars, and the stage they stand on
  glowTop: YB(168),
  glowMid: YB(256),
  stageTop: YB(276),
  stageEdge: YB(294), // where the stage meets the screen's sides
  stageInset: X(37), // the stage's top edge starts this far in from each side
  cars: box(37, 240, 240, 291, 'bottom'),
  sparkles: [
    { x: X(79), y: YB(212), s: X(7.5), k: 7, ph: 0.1 },
    { x: X(38), y: YB(251), s: X(3.5), k: 9, ph: 0.55 },
    { x: X(219), y: YB(244), s: X(4), k: 8, ph: 0.3 },
  ],
  thanks: { x: X(137), y: YB(308) },
  callback: { x: X(136), y: YB(327) },
  divider: { x0: X(23), x1: X(250), y: YB(347) },
  ticket: box(21.5, 385.5, 252.5, 466.5, 'bottom'),
  notchR: X(5.2),
  coin: { x: X(136.5), y: YB(384.5), r: X(21) },
  halo: X(26.5),
  win: { x: X(137), y: YB(419.5) },
  prize: { x: X(136.5), y: YB(445) },
  band: box(0, 479, 274, 500, 'bottom'),
  benefits: [
    { x: X(54.5), y: YB(489.5) },
    { x: X(139.5), y: YB(489.5) },
    { x: X(222), y: YB(489.5) },
  ],
  seps: [X(102), X(178)],
  sepH: X(10),
  button: box(10, 522, 264, 558.5, 'bottom'),
  radius: X(6),
};

// ------------------------------------------------------------------ state B: the gold ticket
export const B = {
  ticket: box(40, 380, 232.5, 456.5, 'bottom'),
  notchR: X(5.4),
  coin: { x: X(136), y: YB(370.5), r: X(24) },
  win: { x: X(136.5), y: YB(411.5) },
  prize: { x: X(136.5), y: YB(431.5) },
  benefits: [
    { x: X(85), y: YB(488) },
    { x: X(199.5), y: YB(488) },
  ],
  sep: X(148),
  sepH: X(13),
  button: box(12, 524.5, 261.5, 559.5, 'bottom'),
  close: { x: X(249.3), y: YB(310.7), s: X(9) },
  // the purple rises from nothing at `fadeTop` to its full colour at `full`
  fadeTop: YB(352),
  full: YB(468),
  // stars left by the coin's landing: position, half-size, gold or white, twinkle rate (k per loop)
  sparkles: [
    { x: X(27), y: YB(377.5), s: X(9), gold: true, k: 6, ph: 0.0 },
    { x: X(247), y: YB(436), s: X(9), gold: true, k: 7, ph: 0.4 },
    { x: X(246), y: YB(337.5), s: X(5), gold: true, k: 9, ph: 0.7 },
    { x: X(197), y: YB(345), s: X(6.5), gold: false, k: 8, ph: 0.2 },
    { x: X(174.5), y: YB(361), s: X(2.6), gold: false, k: 11, ph: 0.85 },
    { x: X(24.5), y: YB(451.5), s: X(6.5), gold: false, k: 5, ph: 0.6 },
    { x: X(83.5), y: YB(343.5), s: X(2.6), gold: false, k: 10, ph: 0.15 },
  ],
};

export const C = {
  page: '#f3f3f6',
  card: '#ffffff',
  cardLine: '#e4e4ec',
  ink: '#1e1d2b',
  mute: '#77768a',
  violet: '#3f3ab9',
  // the dim over the page: A is a violet slate, B a deeper graphite
  dimA: [31, 29, 46, 0.75] as const,
  dimB: [24, 23, 30, 0.85] as const,
  glow: '#d8b464',
  glowHi: '#f1e1ac',
  stageTop: '#f8efd0',
  stageMid: '#fdf8ea',
  sheet: '#ffffff',
  band: '#fbf6e8',
  bandInk: '#2a2933',
  ctaA: '#5835d1',
  ctaAText: '#f6f1ff',
  ctaB: '#ffffff',
  ctaBText: '#4b33b8',
  // ticket A text
  winA: '#1c1b22',
  prizeA: ['#9c6a18', '#e3c16e', '#e6c98a'] as const,
  // the purple of B, from the fade to the bottom edge
  purpleFade: [82, 51, 174] as const,
  purpleMid: '#5233ae',
  purpleLow: '#2c0e8d',
  purpleBottom: '#211060',
  // ticket B
  goldHi: '#f5d677',
  gold: '#e3a23f',
  goldDeep: '#d68e33',
  bronze: '#7a4a18',
  winB: '#fff1d6',
  prizeB: '#ffffff',
  starGold: '#e8b24a',
  starWhite: '#ffffff',
};
