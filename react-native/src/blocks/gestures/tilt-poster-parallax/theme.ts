/**
 * A streaming app's film page at night. The poster (drawn in code, see poster-art.ts) is the only colour: its dusk
 * spills into the page behind it as a blurred backdrop, and gold is kept for the foil title and the saved state.
 * Instrument Serif for the film's name, Inter for everything else. 4 pt grid, 16 pt edges.
 */
export const COLOR = {
  bg: '#0E0C10',
  ink: '#F5F1EA',
  inkSoft: 'rgba(245,241,234,0.62)',
  inkFaint: 'rgba(245,241,234,0.42)',
  glass: 'rgba(255,255,255,0.1)',
  glassBorder: 'rgba(255,255,255,0.14)',
  play: '#F5F1EA',
  playInk: '#16131A',
  trophy: '#E9C47A',
  gold: ['#8A5A17', '#F3D27A', '#FFF4C8', '#C8902E', '#7A4B10'],
  foil: 'rgba(255,255,255,0.85)',
  embossDark: 'rgba(20,10,20,0.55)',
  embossLight: 'rgba(255,236,200,0.35)',
  posterInk: 'rgba(246,226,200,0.72)',
  rim: 'rgba(255,255,255,0.2)',
  glare: 'rgba(255,240,220,0.5)',
  shadow: '#000000',
  /** The poster's warm horizon, spilled onto the page around a lifted card. */
  glow: 'rgba(230,140,90,0.4)',
  /** The print's edge: cream board, darker towards the back. */
  edge: ['#E8DCC6', '#B9A88C'],
  back: ['#221A22', '#120E14'],
  backRule: 'rgba(243,210,122,0.5)',
  backInk: 'rgba(255,244,226,0.92)',
  backMute: 'rgba(255,244,226,0.52)',
  pillSaved: 'rgba(243,210,122,0.14)',
  pillSavedBorder: 'rgba(243,210,122,0.5)',
  goldInk: '#F3D27A',
  // Holographic foil hues (pale, so the gold stays gold under them).
  holo: ['#FF9FD2', '#FFE7A0', '#A9FFD0', '#9FD8FF', '#C8A8FF', '#FF9FD2'],
  backdropFade: ['rgba(14,12,16,0.15)', 'rgba(14,12,16,0.55)', '#0E0C10'],
  finger: 'rgba(255,255,255,0.14)',
  fingerRing: 'rgba(255,255,255,0.42)',
} as const;

export const FONT = { serif: 'TPP-InstrumentSerif', regular: 'TPP-Inter-Regular', medium: 'TPP-Inter-Medium', semibold: 'TPP-Inter-SemiBold' } as const;

export const TYPE = {
  title: { fontFamily: FONT.serif, fontSize: 36, lineHeight: 44, color: COLOR.ink, textAlign: 'center' as const, letterSpacing: -0.2 },
  meta: { fontFamily: FONT.regular, fontSize: 14, lineHeight: 20, color: COLOR.inkFaint, textAlign: 'center' as const },
  pill: { fontFamily: FONT.semibold, fontSize: 15, lineHeight: 20, color: COLOR.ink },
  award: { fontFamily: FONT.medium, fontSize: 13, lineHeight: 18, color: COLOR.inkSoft },
  awardStrong: { fontFamily: FONT.semibold, color: COLOR.ink },
  body: { fontFamily: FONT.regular, fontSize: 15, lineHeight: 22, color: COLOR.inkSoft },
  more: { fontFamily: FONT.semibold, fontSize: 14, lineHeight: 20, color: COLOR.ink },
  chip: { fontFamily: FONT.semibold, fontSize: 11, lineHeight: 14, color: COLOR.inkSoft, letterSpacing: 0.4 },
  // The poster's type (Skia), in card points: the foil title's size and line step, the back's title, the credits.
  posterTitle: 50,
  posterLine: 46,
  backTitle: 30,
  credit: 8,
} as const;

export const LAYOUT = {
  gutter: 16,
  /** Under the safe area: the top bar, then the card. */
  topBar: 4,
  close: 44,
  awardHeight: 32,
  cardTop: 64,
  // The backdrop covers the top of the screen: the blurred poster, this share of the height.
  backdropShare: 0.62,
  cardWidthRatio: 0.6,
  cardAspect: 1.48,
  cardRadius: 16,
  titleGap: 24,
  pillHeight: 52,
  pillGap: 12,
  bodyGap: 20,
  // Where the poster's title sits (share of the card's height, first baseline).
  titleY: 0.2,
  fingerRadius: 22,
} as const;

// The back of the card, in card shares and points.
export const BACK = {
  titleY: 0.26,
  ruleY: 0.31,
  rule: 28,
  creditsY: 0.41,
  lineStep: 13,
} as const;

export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 20, x2: 24 } as const;

export const COPY = {
  title: 'The Long Light',
  posterTitle: ['The Long', 'Light'],
  posterCredit: 'A FILM BY ILSE VARGA',
  meta: '2026  ·  Drama  ·  1 h 52 min  ·  12',
  award: ['3', ' festival awards'],
  watchlist: 'Watchlist',
  added: 'Added',
  play: 'Play trailer',
  body: 'After her father’s funeral, a lighthouse keeper’s daughter walks the old coast path north to scatter his ashes, and finds the people he never mentioned waiting at every stop along the way.',
  credits: [
    { text: 'A FILM BY', strong: false },
    { text: 'ILSE VARGA', strong: true },
    { text: 'STARRING', strong: false, gap: 10 },
    { text: 'MARA OKAFOR   TEO BRANDT', strong: true, gap: 10 },
    { text: 'Drama · 1 h 52 min', strong: false, gap: 20 },
    { text: 'Northlight Pictures', strong: false, gap: 20 },
  ] as { text: string; strong: boolean; gap?: number }[],
  formats: ['4K', 'HDR', 'Dolby Atmos', 'AD'],
  more: 'Show more',
  less: 'Show less',
  posterLabel: 'Poster of The Long Light: a walker on a ridge at dusk, a low sun behind the hills',
} as const;
