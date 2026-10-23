/**
 * Colours, type, layout and content. One accent hue (cobalt): the card's anodised metal, the chosen person's ring,
 * the Undo countdown and the Activity link. Everything else is ink on a cool off-white, and the island is black.
 * Screen: 402 × 874 pt (iPhone 18 Pro). Vertical positions sit on a 4 pt grid (the card's height follows the ISO
 * card ratio, 1.586 : 1); nothing but the island and its receipt sits above y 76.
 */
import type { ImageSourcePropType } from 'react-native';

export const COLORS = {
  page: '#F4F5F7',
  ink: '#0B0C0F',
  /** 5.6 : 1 on the page. */
  mute: '#5E636D',
  hairline: 'rgba(11,12,15,0.08)',
  accent: '#2B50F0',
  island: '#000000',
  onIsland: '#FFFFFF',
  /** 7 : 1 on the island. */
  onIslandMute: 'rgba(255,255,255,0.64)',
  undo: 'rgba(255,255,255,0.16)',
  onCard: '#FFFFFF',
  onCardMute: 'rgba(255,255,255,0.66)',
  /** Card shadows are tinted with the card's own hue, never grey. */
  cardShadow: 'rgba(10,18,70,1)',
} as const;

/** Linear-light colours (0..1) for the shaders; kept beside their sRGB names so the hue stays one decision. */
export const MATERIAL = {
  /** Midnight anodised aluminium: a deep cobalt dye under a clear oxide layer. */
  cobaltDeep: [0.0035, 0.006, 0.042],
  cobalt: [0.012, 0.024, 0.16],
  cobaltLift: [0.1, 0.17, 0.75],
  /** Champagne-gold chip. */
  chipLight: [0.74, 0.6, 0.4],
  chipDark: [0.34, 0.25, 0.14],
  /** The liquid: black with a slight cool cast, glossy. */
  ink: [0.003, 0.0035, 0.005],
  /** What the liquid reflects: the light page around it and a softbox above-left. */
  room: [0.78, 0.8, 0.84],
  softbox: [1.0, 1.0, 1.0],
} as const;

export const TYPE = {
  largeTitle: { fontSize: 32, lineHeight: 40, fontWeight: '700', letterSpacing: -0.7 },
  section: { fontSize: 19, lineHeight: 24, fontWeight: '600', letterSpacing: -0.4 },
  hint: { fontSize: 15, lineHeight: 20, fontWeight: '400', letterSpacing: -0.2 },
  name: { fontSize: 13, lineHeight: 16, fontWeight: '500', letterSpacing: -0.1 },
  row: { fontSize: 16, lineHeight: 20, fontWeight: '600', letterSpacing: -0.3 },
  meta: { fontSize: 13, lineHeight: 16, fontWeight: '400', letterSpacing: -0.1 },
  cardLabel: { fontSize: 12, lineHeight: 16, fontWeight: '500', letterSpacing: 0.2 },
  tier: { fontSize: 11, lineHeight: 16, fontWeight: '600', letterSpacing: 1.6 },
  balance: { fontSize: 32, lineHeight: 36, fontWeight: '700', letterSpacing: -0.8 },
  pan: { fontSize: 14, lineHeight: 20, fontWeight: '600', letterSpacing: 0.6 },
  /** The amount inside the drop, at the drop's largest; it scales with the drop. */
  amount: { fontSize: 26, lineHeight: 32, fontWeight: '700', letterSpacing: -0.5 },
  receiptMeta: { fontSize: 13, lineHeight: 16, fontWeight: '500', letterSpacing: -0.1 },
  receiptAmount: { fontSize: 22, lineHeight: 26, fontWeight: '700', letterSpacing: -0.5 },
  undo: { fontSize: 15, lineHeight: 20, fontWeight: '600', letterSpacing: -0.2 },
} as const;

/** The hardware island of iPhone 18 Pro, as the Simulator records it; the liquid's rest shape sits just inside it. */
export const ISLAND = { width: 94, height: 36, top: 14.35 } as const;

/**
 * The receipt the island becomes after a send: an expanded Live Activity as wide as the card, its content only in
 * the two sides of the hardware cut-out (who and how much on the left, Undo with its countdown on the right).
 */
export const RECEIPT = {
  top: 10, height: 64, avatar: 40, badge: 18, pad: 12, gap: 10, undoWidth: 92, undoHeight: 36, ring: 16, ringStroke: 2,
} as const;

export const LAYOUT = {
  edge: 16,
  headerTop: 84,
  headerHeight: 40,
  headerAvatar: 36,
  /** Header to card, card to "Send to", people to "Activity". */
  gapHeader: 16,
  gapSection: 32,
  cardRatio: 1.586,
  cardRadius: 18,
  cardPad: 24,
  chip: { x: 24, y: 80, w: 46, h: 35, r: 6 },
  /** How far the card's canvas reaches past the card for its shadow. */
  shadowPad: 96,
  sectionHeight: 24,
  avatarGap: 16,
  avatar: 52,
  nameGap: 8,
  nameHeight: 16,
  ringGap: 4,
  ringStroke: 2.5,
  rowsGap: 8,
  rowHeight: 56,
  rowIcon: 40,
  rows: 4,
} as const;

export type Person = { id: string; name: string; full: string; photo: ImageSourcePropType };

/* Portraits: Unsplash (photo ids in the file names' order: 1438761681033, 1507003211169, 1488426862026,
   1539571696357, 1494790108377, 1580489944761), face-cropped to 168 px. */
export const PHOTOS = {
  maya: require('./assets/maya.jpg') as ImageSourcePropType,
  theo: require('./assets/theo.jpg') as ImageSourcePropType,
  priya: require('./assets/priya.jpg') as ImageSourcePropType,
  jonas: require('./assets/jonas.jpg') as ImageSourcePropType,
  ana: require('./assets/ana.jpg') as ImageSourcePropType,
  jules: require('./assets/jules.jpg') as ImageSourcePropType,
};

/** Recent people, left to right; the one nearest the finger is chosen while the card is pulled. */
export const PEOPLE: Person[] = [
  { id: 'maya', name: 'Maya', full: 'Maya Lindqvist', photo: PHOTOS.maya },
  { id: 'theo', name: 'Theo', full: 'Theo Brandt', photo: PHOTOS.theo },
  { id: 'priya', name: 'Priya', full: 'Priya Shah', photo: PHOTOS.priya },
  { id: 'jonas', name: 'Jonas', full: 'Jonas Weber', photo: PHOTOS.jonas },
  { id: 'ana', name: 'Ana', full: 'Ana Costa', photo: PHOTOS.ana },
];

/** Dollar amounts, one detent each: fine steps where most payments are, coarser above $50. */
export const STEPS = [5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 90, 100, 125, 150, 175, 200] as const;

export const START_BALANCE = 248120; // cents: $2,481.20
export const CARD_LAST4 = '4821';

export type Merchant = 'coffee' | 'grocer' | 'transit';
/** A row is a person (by index in PEOPLE) or a merchant with its own mark. */
export type Row = { id: string; name: string; meta: string; cents: number; person?: number; merchant?: Merchant };

export const ROWS: Row[] = [
  { id: 'r1', name: 'Arlo’s Coffee', meta: 'Today, 8:12 AM', cents: -480, merchant: 'coffee' },
  { id: 'r2', name: 'Priya Shah', meta: 'Yesterday · Concert tickets', cents: 3200, person: 2 },
  { id: 'r3', name: 'Northside Grocer', meta: 'Mon, Oct 19', cents: -6317, merchant: 'grocer' },
  { id: 'r4', name: 'City Transit', meta: 'Sun, Oct 18 · Monthly pass', cents: -8900, merchant: 'transit' },
];

/** Positions that depend on the screen width. Pure, so the offline renders use the same numbers. */
export function makeLayout(width: number) {
  const L = LAYOUT;
  const cardW = width - L.edge * 2;
  const cardH = Math.round(cardW / L.cardRatio);
  const cardTop = L.headerTop + L.headerHeight + L.gapHeader;
  const sectionTop = Math.ceil((cardTop + cardH + L.gapSection) / 4) * 4;
  const avatarTop = sectionTop + L.sectionHeight + L.avatarGap;
  const activityTop = avatarTop + L.avatar + L.nameGap + L.nameHeight + L.gapSection;
  const rowTop = activityTop + L.sectionHeight + L.rowsGap;
  // The faces span the content width: the first starts at the left edge, the last ends at the right one.
  const pitch = (cardW - L.avatar) / (PEOPLE.length - 1);
  return {
    width,
    cardW,
    cardH,
    cardTop,
    sectionTop,
    avatarTop,
    activityTop,
    rowTop,
    pitch,
    avatarX: PEOPLE.map((_, i) => L.edge + L.avatar / 2 + pitch * i),
    avatarY: avatarTop + L.avatar / 2,
    island: { cx: width / 2, cy: ISLAND.top + ISLAND.height / 2, hw: ISLAND.width / 2, hh: ISLAND.height / 2 },
    receipt: { cx: width / 2, cy: RECEIPT.top + RECEIPT.height / 2, hw: cardW / 2, hh: RECEIPT.height / 2 },
  };
}
export type Layout = ReturnType<typeof makeLayout>;

/** Glyphs, in a 24 pt box. The contactless mark: three arcs swelling right, as printed on cards. */
const arc = (r: number) => {
  const a = (38 * Math.PI) / 180;
  const p = (s: number) => `${(4 + r * Math.cos(s * a)).toFixed(2)} ${(12 + r * Math.sin(s * a)).toFixed(2)}`;
  return `M${p(-1)} A${r} ${r} 0 0 1 ${p(1)}`;
};
export const GLYPH = {
  contactless: [6, 11, 16].map(arc).join(' '),
  check: 'M7.2 12.4 L10.6 15.6 L17 8.8',
} as const;

export function formatMoney(cents: number, withCents = true) {
  const sign = cents < 0 ? '−' : '';
  const v = Math.abs(cents) / 100;
  const digits = withCents ? 2 : 0;
  return `${sign}$${v.toLocaleString('en-US', { minimumFractionDigits: digits, maximumFractionDigits: digits })}`;
}
