// Potion Vial Upload — palette and geometry, in points of a 402 × 874 reference screen.
// Colours were sampled from the reference video (a pixel-art drop zone on a near-black page).

export const REF_W = 402;
export const REF_H = 874;

/** One "art pixel" of the pixel-art layer, in points (4 device pixels on a 3× phone). */
export const ART_PX = 4 / 3;

export const C = {
  page: '#0a080d',
  star: '#f3ecd2',
  starGreen: '#5fe34f',
  // glass
  glass: '#a29aaa',
  glassDark: '#525060',
  glassFill: 'rgba(196, 190, 210, 0.035)',
  glare: '#e9e4f0',
  // liquid
  liquidTop: '#d2fb92',
  liquid: '#a2f863',
  liquidDeep: '#6fd23c',
  liquidBottom: '#3f9a24',
  meniscus: '#2c7c1f',
  glow: '#4fe02f',
  smoke: '#2f6a22',
  mist: '#7cf05a',
  // brass and steel of the stopper
  brassHi: '#ffd277',
  brass: '#e9a944',
  brassLo: '#a35424',
  steelHi: '#d4d0dc',
  steel: '#918d9b',
  steelLo: '#55525e',
  // emerald
  gemHi: '#8ff58a',
  gemLight: '#63ea63',
  gem: '#33b24b',
  gemDark: '#1b7a3a',
  gemDeep: '#12592a',
  // spider
  spider: '#16141c',
  spiderHi: '#4a4656',
  spiderLeg: '#5d5a68',
  spiderEye: '#9cf26a',
  hourglass: '#e8302f',
  // text and chrome
  ink: '#f2f1ec',
  mute: '#8b8a93',
  faint: '#5a5962',
  ready: '#c6f7a8',
  done: '#9be27a',
  card: '#141219',
  cardLine: '#2a2733',
  cardLineHot: '#4fae36',
  paper: '#f4f2ea',
  paperShade: '#c9c6bd',
  zip: '#3fbf4a',
} as const;

/** The vial, measured from the video and scaled so that vial + gem span the phone's width. */
export const V = {
  x0: 22, // left end of the glass (outer)
  x1: 282, // end of the straight body
  shoulder: 302, // start of the neck
  neck: 311, // end of the neck, where the stopper starts
  half: 23, // half the body's outer height
  neckHalf: 11,
  wall: 2.6, // glass thickness (outer to inner)
  // the stopper: brass band, steel disc, brass band
  band1: [311, 320, 15] as const,
  disc: [320, 327, 19] as const,
  band2: [327, 337, 15] as const,
  chain: 80, // length of the gem's chain
  gemEdge: 33, // cube edge
} as const;

/** Vertical placement: the vial's centre line sits at this fraction of the free height. */
export const LAYOUT = {
  vialY: 0.4,
  counterGap: 52, // vial centre → top of the digits
  digit: 38,
  sub: 13,
  cardW: 250,
  cardH: 68,
  cardBottom: 34, // above the safe area
} as const;

export const FILE = { name: 'spells.zip', mb: 48.2 } as const;
