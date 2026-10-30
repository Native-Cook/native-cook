// QR Joy Postcard — layout, colour, copy and the QR code itself.
//
// Every position was measured on the pin (one 1179 × 2358 px screenshot of a 1179 × 2556 px
// screen, 78 px cut from the top) and is written in those pin pixels, then mapped to a
// 402 × 874 pt reference screen:
//   x → px · S        y → (px + 78) · S + 1.25
// The page under the sheet hangs from the top of the screen; the sheet stands on its bottom edge.

export const REF_W = 402;
export const REF_H = 874;
export const S = REF_W / 1179;

export const X = (px: number) => px * S;
export const Y = (px: number) => (px + 78) * S + 1.25;

export type Box = { x: number; y: number; w: number; h: number };
const box = (x0: number, y0: number, x1: number, y1: number): Box => ({ x: X(x0), y: Y(y0), w: X(x1) - X(x0), h: Y(y1) - Y(y0) });

export const COPY = {
  orders: 'Orders',
  purchases: 'Purchases',
  promo: 'Decorate QR code',
  pick1: 'Pick up',
  pick2: 'by Mar 17, Tue',
  point: 'Pickup point · 3A Quiet Lane, Riverside',
  title: 'Share the joy',
  sub: 'Turn any order into a reason to smile!',
  steps: [
    ['Decorate a plain QR code: add a', 'backdrop and a few warm words to', 'turn it into a lovely postcard.'],
    ['Send it to whoever picks it up: even', 'the most ordinary parcel becomes a', 'little nicer to collect.'],
  ],
  ctaA: 'Decorate QR code',
  ctaB: 'Send postcard',
  bubble: 'for you',
  // the code points at an invented page on the reserved example domain
  qrText: 'example.com/joy/7Q2K',
};

export const C = {
  page: '#F6F6FA',
  card: '#FFFFFF',
  ink: '#252527',
  grey: '#8E8E93',
  violet: '#C252FF', // promo link and icons on the page
  dim: 'rgba(0, 0, 0, 0.5)',
  promo: '#FEE6FF',
  chip: '#F2F2F7',
  heroTop: '#FFC2F7',
  heroMid: '#FFC9F9',
  heroLight: '#FFE0FB',
  closeX: '#FFAEF2',
  cta: '#B62DFF',
  ctaPress: '#A01FEA',
  stepChip: '#F6F5FA',
  qrInk: '#252429',
  // the postcard (B)
  postTop: '#FFE6F7',
  postBottom: '#FFC4EC',
  dotA: '#C21FD6', // dots run from magenta (top left) …
  dotB: '#5B21D9', // … to violet (bottom right)
  finder: '#D61FC4',
  finderCore: '#5B1A8E',
  petal: '#FF6FD8', // a module in flight
  heart: '#F0219F',
  bubbleInk: '#7A1FA8',
  sparkle: '#FFFFFF',
};

// ------------------------------------------------------------------ the page (hangs from the top)

export const PAGE = {
  header: { y0: 0, y1: Y(220), r: 26 },
  back: { x: X(75), y: Y(150) },
  ordersY: Y(152),
  row: { ...box(48, 255, 1130, 430), r: 18 },
  bag: { x: X(130), y: Y(338) },
  purchasesX: X(205),
  chevron: { x: X(1048), y: Y(342) },
  promo: { ...box(0, 465, 1179, 640), r: 26 },
  tulips: box(292, 418, 472, 548),
  promoText: { x: X(468), y: Y(505) },
  promoChevron: { x: X(868), y: Y(505) },
  order: { ...box(0, 552, 1179, 1000), r: 26 },
  pick1Y: Y(630),
  pick2Y: Y(705),
  qrButton: { ...box(975, 590, 1130, 745), r: 18 },
  pointY: Y(797),
  grabber: box(537, 845, 643, 857),
};

// ------------------------------------------------------------------ the sheet (stands on the bottom)

export const SHEET = {
  top: Y(876),
  r: 24,
  heroBottom: Y(1506),
  close: { x: X(1095), y: Y(952), r: X(33) },
  titleX: X(50),
  titleY: Y(1587), // visual centre of the cap height
  subY: Y(1688),
  stepChip: { x: X(88), r: X(37) },
  stepTextX: X(152),
  stepLineY: [
    [Y(1781), Y(1841), Y(1901)],
    [Y(2005), Y(2065), Y(2125)],
  ],
  stepChipY: [Y(1787), Y(2010)],
  cta: { ...box(24, 2204, 1155, 2350), r: 17 },
};

export const SIZE = {
  orders: 17.4,
  row: 17.4,
  promo: 17.4,
  pick: 23.4,
  point: 16.4,
  title: 23.4,
  body: 16.2,
  stepNo: 15,
  cta: 16.8,
  bubble: 22,
};

// ------------------------------------------------------------------ the card, the bear, the gift

/** The QR card is turned −9.6° (counter-clockwise); its top-left corner sits at pin (195, 1255). */
export const CARD = (() => {
  const angle = (-9.6 * Math.PI) / 180;
  const w = 190;
  const h = 232;
  const tl = { x: X(195), y: Y(1255) };
  const cos = Math.cos(angle);
  const sin = Math.sin(angle);
  const cx = tl.x + (cos * w) / 2 - (sin * h) / 2;
  const cy = tl.y + (sin * w) / 2 + (cos * h) / 2;
  return { cx, cy, w, h, angle, r: 19 };
})();

/** A point on the screen (pt) in the card's own space, origin at its top-left corner. */
export function toCard(x: number, y: number) {
  const dx = x - CARD.cx;
  const dy = y - CARD.cy;
  const cos = Math.cos(-CARD.angle);
  const sin = Math.sin(-CARD.angle);
  return { x: dx * cos - dy * sin + CARD.w / 2, y: dx * sin + dy * cos + CARD.h / 2 };
}

export const QR_N = 29;
/** The code, in the card's space: 29 modules over 136 pt with a quiet margin round them. */
export const QR = { x: 27, y: 23, size: 136, m: 136 / QR_N };

/** The bear on its cushion (a 3 × 3 atlas of Blender renders, the paw swinging −16° … 16°). */
export const BEAR = (() => {
  const base = toCard(X(450), Y(1302)); // bottom centre of the cushion
  return { x: base.x, y: base.y, w: 141, h: 141 * (345 / 360), frames: 9, cols: 3, fw: 360, fh: 345, rest: 4 };
})();

export const GIFT = { x: X(838), y: Y(1238), w: 128, h: 128 * (242 / 192), rot: -7, blur: 4.2 };

/** The tulip sticker, stuck over the card's right edge (card space). */
export const STICKER = { x: 196, y: 64, w: 58, h: 58 * (500 / 420), rot: 13, border: 4.5 };

/** The speech bubble the bear says "for you" in (screen space, its tail points at the bear). */
export const BUBBLE = { x: X(700), y: Y(968), w: 92, h: 40, r: 20, tail: { x: X(585), y: Y(1030) } };

/** Four-point sparkles on the hero: x, y (pt), half-size, phase. */
export const SPARKLES = [
  { x: X(268), y: Y(1000), s: 26, ph: 0.0 },
  { x: X(772), y: Y(918), s: 13, ph: 0.35 },
  { x: X(655), y: Y(962), s: 6, ph: 0.7 },
  { x: X(58), y: Y(1262), s: 8, ph: 0.15 },
  { x: X(1125), y: Y(1238), s: 7, ph: 0.55 },
  { x: X(150), y: Y(1445), s: 6, ph: 0.85 },
];

// ------------------------------------------------------------------ the QR code

/** example.com/joy/7Q2K at error correction H (the heart in the middle of B hides 7 × 7 modules). */
const QR_ROWS = [
  '11111110010110000111001111111',
  '10000010111010011000101000001',
  '10111010001101011111101011101',
  '10111010011110110001001011101',
  '10111010010111011010001011101',
  '10000010111000001011001000001',
  '11111110101010101010101111111',
  '00000000111011001111100000000',
  '00001111011001010010001100010',
  '11011101101101001101111010011',
  '10111011110110011000100001101',
  '01001101100010001010011101001',
  '01011011000101111011110000010',
  '01111100101000110010001111001',
  '11110010011110000100111100001',
  '00101101000010111101010101010',
  '10011011111001110101110111001',
  '10001101001011000011000111001',
  '00001010101000011011001011001',
  '00101100100100000101101000011',
  '11001111100001101010111111010',
  '00000000101111111001100011101',
  '11111110111110101011101011001',
  '10000010111110011101100011000',
  '10111010100000101111111111011',
  '10111010011010000001000100010',
  '10111010011001001011110001111',
  '10000010000110000010010000011',
  '11111110001101111101111100010',
];

function inFinder(r: number, c: number) {
  const n = QR_N;
  return (r < 7 && c < 7) || (r < 7 && c >= n - 7) || (r >= n - 7 && c < 7);
}

export type Module = { r: number; c: number; d: number; h: number; heart: boolean };

/** The dark modules outside the three finder patterns: these are the ones that fly. */
export const MODULES: Module[] = (() => {
  const out: Module[] = [];
  const mid = (QR_N - 1) / 2;
  const maxD = Math.hypot(mid, mid);
  for (let r = 0; r < QR_N; r++) {
    for (let c = 0; c < QR_N; c++) {
      if (QR_ROWS[r][c] !== '1' || inFinder(r, c)) continue;
      // a seeded hash per module, so every loop is the same
      const s = Math.sin((r * 29 + c) * 12.9898) * 43758.5453;
      out.push({
        r,
        c,
        d: Math.hypot(r - mid, c - mid) / maxD,
        h: s - Math.floor(s),
        heart: Math.abs(r - mid) <= 3 && Math.abs(c - mid) <= 3,
      });
    }
  }
  return out;
})();

/** Top-left module of each finder pattern. */
export const FINDERS = [
  { r: 0, c: 0 },
  { r: 0, c: QR_N - 7 },
  { r: QR_N - 7, c: 0 },
];
