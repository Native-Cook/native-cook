// App Icon Atelier — tokens, the five glasses and the layout (402 × 874 pt design, 4 pt grid).

export const COLOR = {
  pageTop: '#F7F5FB',
  pageBottom: '#EEEAF6',
  ink: '#1C1630',
  mute: '#8A8399',
  hair: 'rgba(28,22,48,0.14)',
  chipRing: '#1C1630',
  cta: '#1C1630',
  ctaInk: '#FBFAFE',
  onWall: '#FFFFFF',
} as const;

export const FONT = {
  display: 'AiaInstrumentSerif',
  regular: 'AiaInter-Regular',
  medium: 'AiaInter-Medium',
  semibold: 'AiaInter-SemiBold',
} as const;

export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

/**
 * A glass, as the shader reads it: `tint` is the light that survives one unit of glass (per channel),
 * `dens` how thick the colour gets, `frost` how milky the body is, `film` the strength of the thin-film
 * rainbow on the rim, `glow` how much the heart lights itself from inside.
 */
export type Glass = {
  id: string;
  name: string;
  note: string;
  tint: [number, number, number];
  dens: number;
  frost: number;
  film: number;
  glow: number;
  /** the chip: light and deep stop of its swatch */
  chip: [string, string];
};

export const GLASSES: Glass[] = [
  {
    id: 'lilac',
    name: 'Lilac Glass',
    note: 'The original. Clear, cool, a touch of violet.',
    tint: [0.58, 0.4, 0.98],
    dens: 1.1,
    frost: 0,
    film: 0.55,
    glow: 0,
    chip: ['#E4DAFB', '#9B7BEA'],
  },
  {
    id: 'pearl',
    name: 'Pearl',
    note: 'Milky glass with a mother-of-pearl sheen.',
    tint: [0.97, 0.95, 0.99],
    dens: 0.6,
    frost: 0.75,
    film: 1,
    glow: 0,
    chip: ['#FFFFFF', '#D9D3E6'],
  },
  {
    id: 'mint',
    name: 'Mint Frost',
    note: 'Sandblasted glass the colour of sea mist.',
    tint: [0.6, 0.92, 0.8],
    dens: 1,
    frost: 0.6,
    film: 0.3,
    glow: 0,
    chip: ['#E2F7EE', '#7FCDB0'],
  },
  {
    id: 'amber',
    name: 'Amber',
    note: 'Warm honey glass, poured thick.',
    tint: [1, 0.72, 0.32],
    dens: 1.5,
    frost: 0.05,
    film: 0.25,
    glow: 0,
    chip: ['#FCE6C4', '#E39A3B'],
  },
  {
    id: 'smoke',
    name: 'Smoke',
    note: 'Dark glass, with the heart lit from within.',
    tint: [0.36, 0.33, 0.46],
    dens: 2,
    frost: 0.1,
    film: 0.6,
    glow: 0.8,
    chip: ['#8C849E', '#2B2638'],
  },
];
export const LAST_GLASS = GLASSES.length - 1;

/** The other apps on the Home Screen preview, drawn flat (the glass belongs to Amora alone). */
export const APPS = [
  { name: 'Notes', from: '#FFE7A3', to: '#F4C04E', glyph: 'notes' },
  { name: 'Tides', from: '#BFE3FF', to: '#5E9EF0', glyph: 'tides' },
  { name: 'Amora', from: '', to: '', glyph: 'self' },
  { name: 'Ledger', from: '#CDEFD9', to: '#4FB37E', glyph: 'ledger' },
] as const;
export const SELF = 2;

export const BAR_H = 44;
export const CHIP = 52;
export const CHIP_GAP = 16;
export const CARD_H = 168;
export const APP_ICON = 60;
/** the pebble's half width in a Home Screen slot: its outline reads as wide as a 60 pt icon */
export const SLOT_S = 31.5;
export const CTA_H = 56;

export type Layout = {
  w: number;
  h: number;
  barTop: number;
  heroX: number;
  heroY: number;
  heroS: number; // half the pebble's width, pt
  eyebrowTop: number;
  nameTop: number;
  noteTop: number;
  chipsTop: number;
  chipsLeft: number;
  homeLabelTop: number;
  cardX: number;
  cardY: number;
  cardW: number;
  appsY: number; // centre of the icons
  appX: number[]; // centres of the icons
  ctaTop: number;
};

export function chipCentre(L: Layout, i: number) {
  'worklet';
  return L.chipsLeft + CHIP / 2 + i * (CHIP + CHIP_GAP);
}

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const barTop = top;
  const ctaTop = h - Math.max(bottom, 16) - 8 - CTA_H;
  const cardY = ctaTop - SPACE.xl - CARD_H;
  const homeLabelTop = cardY - 12 - 18;
  const chipsTop = homeLabelTop - SPACE.xxl - CHIP;
  const noteTop = chipsTop - SPACE.xl - 20;
  const nameTop = noteTop - SPACE.xs - 42;
  const eyebrowTop = nameTop - SPACE.xs - 16;
  const room0 = barTop + BAR_H;
  const room = eyebrowTop - room0;
  const heroS = Math.min(w * 0.25, room * 0.36, 104);
  // the pebble sits a little above the middle of its room, its shadow below it
  const heroY = room0 + room * 0.44;
  const cardX = SPACE.l;
  const cardW = w - 2 * SPACE.l;
  const gap = (cardW - 2 * 28 - 4 * APP_ICON) / 3;
  const appX = [0, 1, 2, 3].map((i) => cardX + 28 + APP_ICON / 2 + i * (APP_ICON + gap));
  const chipsW = GLASSES.length * CHIP + (GLASSES.length - 1) * CHIP_GAP;
  return {
    w,
    h,
    barTop,
    heroX: w / 2,
    heroY,
    heroS,
    eyebrowTop,
    nameTop,
    noteTop,
    chipsTop,
    chipsLeft: (w - chipsW) / 2,
    homeLabelTop,
    cardX,
    cardY,
    cardW,
    appsY: cardY + 30 + 26 + APP_ICON / 2,
    appX,
    ctaTop,
  };
}
