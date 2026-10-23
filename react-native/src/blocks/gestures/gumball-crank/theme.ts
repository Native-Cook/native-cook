/**
 * A daily treat from a real gumball machine: cherry enamel over a cast body, chrome lid, collar, plate and foot, a
 * glass globe full of gumballs, on an oak table against a warm plaster wall. One light, high on the left: every
 * highlight, shadow and reflection agrees with it. Instrument Serif for the title and the prize, Inter for the rest.
 * Sizes are points on a 402 × 778 stage (the screen inside its safe area), scaled to fit.
 */
export const FONT = {
  serif: 'GC-InstrumentSerif',
  regular: 'GC-Inter-Regular',
  medium: 'GC-Inter-Medium',
  semibold: 'GC-Inter-SemiBold',
} as const;

export const COLOR = {
  wallTop: '#F1E9DC',
  wallBottom: '#E5D8C4',
  tableTop: '#C79B6B',
  tableEdge: '#E2BE92',
  tableFront: '#A87B4D',
  tableDeep: '#8A6038',
  ink: '#2A211B',
  mute: '#7A6B5E',
  enamel: '#B4232C',
  enamelLight: '#E0484E',
  enamelDeep: '#6E1016',
  chromeHi: '#FBFBF9',
  chromeMid: '#B9BCC1',
  chromeLow: '#6B6F76',
  chromeDeep: '#3E4248',
  slot: '#1C1514',
  card: '#FFFCF6',
  cardEdge: 'rgba(42,33,27,0.08)',
  button: '#2A211B',
  buttonInk: '#FFFCF6',
  coin: '#E9B640',
  coinLight: '#FFE08A',
  coinDeep: '#A8761C',
  pill: 'rgba(255,252,246,0.86)',
  scrim: 'rgba(42,26,18,0.42)',
  finger: 'rgba(42,33,27,0.14)',
  fingerEdge: 'rgba(42,33,27,0.36)',
} as const;

/** Gumballs: the colours of a real jar, a little dusty so the enamel stays the loudest red on the screen. */
export const BALLS = ['#E8433F', '#F6C744', '#7CC46A', '#F08A3C', '#F4EFE6', '#4C8FD6', '#EE8DB5', '#8B5FBF', '#47B9A8'];

/** The capsule: a clear top over a mint bottom. */
export const CAPSULE = { r: 30, mint: '#4FBFA5', mintDeep: '#2B8C75', mintLight: '#9BE3CF' };

export const STAGE = { w: 402, h: 778 };

export const LAYOUT = {
  edge: 16,
  titleBase: 40,
  subBase: 64,
  wallet: { y: 12, h: 40, pad: 12, coin: 9 },
  globe: { y: 254, r: 122, glass: 5 },
  lid: { h: 34, w: 150, knob: 12 },
  collar: { y: 370, h: 22, w: 196 },
  body: { top: 392, bottom: 600, topW: 172, bottomW: 228, r: 30 },
  plate: { y: 462, r: 42 },
  hub: 13,
  arm: 34,
  grip: 10,
  door: { top: 532, h: 50, w: 100, r: 18 },
  foot: { y: 600, h: 14, w: 244 },
  table: 614,
  tray: { y: 690 },
  card: { w: 318, h: 236, bottomGap: 34, r: 24 },
  hintBase: 752,
  finger: 44,
} as const;

export const TYPE = {
  title: { font: 'serif', size: 34 },
  sub: { font: 'regular', size: 15 },
  wallet: { font: 'semibold', size: 15 },
  hint: { font: 'medium', size: 15 },
  eyebrow: { font: 'semibold', size: 11 },
  prize: { font: 'serif', size: 64 },
  prizeUnit: { font: 'serif', size: 26 },
  cardSub: { font: 'regular', size: 14 },
  button: { font: 'semibold', size: 16 },
} as const;
export type TypeRole = keyof typeof TYPE;

export const COPY = {
  title: 'Daily treat',
  sub: 'Day 7 of your streak',
  hint: 'Turn the handle once around',
  hintBusy: 'Here it comes',
  hintDone: 'Come back tomorrow for day 8',
  eyebrow: 'DAY 7 REWARD',
  prize: '+50',
  unit: 'coins',
  cardSub: 'Spend them on themes and streak shields.',
  collect: 'Collect',
};

/** The wallet's balance before and after today's treat. */
export const WALLET = { before: 1240, prize: 50 };

/** Where the stage sits on a screen `width` × `height` with safe-area insets. */
export function makeLayout(width: number, height: number, top: number, bottom: number) {
  const room = height - top - Math.max(bottom, 16);
  const s = Math.min(width / STAGE.w, room / STAGE.h);
  const ox = (width - STAGE.w * s) / 2;
  const oy = top + (room - STAGE.h * s) / 2;
  return { width, height, top, bottom, s, ox, oy, cx: width / 2 };
}
export type Layout = ReturnType<typeof makeLayout>;
