/** Colours, type, layout and copy. Layout in points on a 402 × 874 screen. */
import { Platform } from 'react-native';

export const COLOR = {
  bg: '#1B1A17',
  bgDeep: '#0B0A09',
  ink: '#FFFFFF',
  inkSoft: 'rgba(255,255,255,0.6)',
  inkFaint: 'rgba(255,255,255,0.42)',
  inkGhost: 'rgba(255,255,255,0.2)',
  pill: '#1B1A16',
  pillGhost: 'rgba(255,255,255,0.04)',
  glass: 'rgba(255,255,255,0.12)',
  glassBorder: 'rgba(255,255,255,0.08)',
  trophy: 'rgba(245,192,74,0.6)',
  // The source's stage light: a cool glow from the top-left corner over the backdrop.
  stageLight: 'rgba(140,168,235,0.42)',
  gold: ['#8A5A17', '#F3D27A', '#FFF4C8', '#C8902E', '#7A4B10'],
  foil: 'rgba(255,255,255,0.85)',
  rim: 'rgba(255,255,255,0.22)',
  glare: 'rgba(255,240,220,0.55)',
  shadow: '#000000',
  // The poster's red-gold, spilled onto the page around a lifted card.
  glow: 'rgba(214,104,44,0.42)',
  back: ['rgba(38,24,16,0.88)', 'rgba(16,11,8,0.96)'],
  backRule: 'rgba(243,210,122,0.5)',
  backInk: 'rgba(255,244,226,0.92)',
  backMute: 'rgba(255,244,226,0.52)',
  pillSaved: 'rgba(243,210,122,0.14)',
  pillSavedBorder: 'rgba(243,210,122,0.5)',
  goldInk: '#F3D27A',
  // Holographic foil hues (pale, so the gold stays gold under them).
  holo: ['#FF9FD2', '#FFE7A0', '#A9FFD0', '#9FD8FF', '#C8A8FF', '#FF9FD2'],
  backdropSide: ['rgba(27,26,23,0)', 'rgba(27,26,23,0.4)'],
  backdropFade: ['rgba(27,26,23,0)', 'rgba(27,26,23,0.35)', '#1B1A17'],
  finger: 'rgba(255,255,255,0.32)',
  fingerRing: 'rgba(255,255,255,0.7)',
} as const;

export const SCRIPT_FONT = Platform.select({ ios: 'Snell Roundhand', default: 'cursive' }) as string;
export const UI_FONT = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' }) as string;

export const TYPE = {
  title: { fontSize: 26, lineHeight: 32, fontWeight: '500' as const, color: COLOR.ink, textAlign: 'center' as const },
  date: { fontSize: 14, lineHeight: 18, fontWeight: '500' as const, color: COLOR.inkFaint, textAlign: 'center' as const },
  pill: { fontSize: 15, fontWeight: '600' as const, color: COLOR.ink },
  award: { fontSize: 13, fontWeight: '500' as const, color: COLOR.inkFaint },
  awardStrong: { fontWeight: '800' as const },
  body: { fontSize: 15, lineHeight: 17.5, fontWeight: '400' as const, color: COLOR.ink },
  more: { fontSize: 14, fontWeight: '600' as const, color: COLOR.inkFaint },
  // The poster's script title (Skia), in card points.
  posterTitle: 44,
  backTitle: 28,
  credit: 7.5,
} as const;

export const LAYOUT = {
  gutter: 25,
  topBar: -5,
  close: 36,
  awardHeight: 30,
  cardTop: 69,
  stageLight: 230,
  // The backdrop photo covers the top ~0.6 of the screen (402 × 520 asset).
  backdropAspect: 520 / 402,
  // Measured on the source: a small card, ~0.42 of the width at rest, centred under the top bar.
  cardWidthRatio: 0.343,
  cardAspect: 1.486,
  cardRadius: 18,
  titleGap: 18,
  pillHeight: 51,
  pillGap: 13,
  bodyGap: 20,
  // The plate is larger than the card so it can slide without showing an edge.
  plateOverscan: 1.24,
  // Where the title sits on the card (share of its height) and how far it leans.
  titleY: 0.66,
  titleSkew: -0.08,
  fingerRadius: 22,
} as const;

// The back of the card, in card shares and points.
export const BACK = {
  plateOpacity: 0.5,
  plateBlur: 10,
  titleY: 0.27,
  ruleY: 0.33,
  rule: 28,
  creditsY: 0.43,
  lineStep: 11,
} as const;

// The spacing scale the block uses (pt). Source values that sit off it stay as measured (see the design doc).
export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 20, x2: 24 } as const;

export const COPY = {
  title: 'Solenne',
  date: 'April 24, 2026 · In 2 days',
  award: ['1', ' nomination, ', '2', ' wins'],
  watchlist: 'Watchlist',
  added: 'Added',
  watched: 'Watch',
  body: 'Discover the story of Solenne Marchetti, the voice that filled arenas from a single silver microphone, and the life she lived beyond the music: the towns, the losses and the songs she never released.',
  credits: [
    { text: 'A FILM BY', strong: false },
    { text: 'ILSE VARGA', strong: true },
    { text: 'NARRATED BY', strong: false, gap: 8 },
    { text: 'TEO BRANDT', strong: true, gap: 8 },
    { text: 'Documentary · 1 h 48 min', strong: false, gap: 16 },
    { text: 'Marchetti Pictures', strong: false, gap: 16 },
  ] as { text: string; strong: boolean; gap?: number }[],
  more: 'Show more',
  less: 'Show less',
  posterLabel: 'Poster of Solenne: a singer in a red stage jacket under warm arena lights',
} as const;
