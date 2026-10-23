// Measured from the pin's phone (390 pt design scaled to 402 pt); every value is in points.

export const COLORS = {
  bg: '#2B2930',
  tabBar: '#26262C',
  card: '#1B1B1F',
  ink: '#FFFFFF',
  inkSoft: '#D9DCE0',
  inkMute: '#B5B5BB',
  star: '#F3E30F',
  starOff: '#858DA0',
  progress: '#FC0001',
  progressTrack: '#DCD8D6',
  plus: '#565152',
  posterPlaceholder: '#35333B',
  badge: '#E2231A',
  // Profile picker: violet top, teal glow left, magenta glow right, dusk bottom.
  pickerTop: '#2A136B',
  pickerBottom: '#221C3E',
  pickerTeal: '#1A5D6E',
  pickerMagenta: '#8A2A4E',
  pickerPlum: '#4C184C',
} as const;

export const FONT = {
  regular: 'PGC-Poppins-Regular',
  medium: 'PGC-Poppins-Medium',
  semibold: 'PGC-Poppins-SemiBold',
  display: 'PGC-Anton',
  // Poster faces: each one-sheet gets its own treatment, as real posters do.
  bebas: 'PGC-BebasNeue',
  dmserif: 'PGC-DMSerifDisplay',
  archivo: 'PGC-ArchivoBlack',
  marcellus: 'PGC-Marcellus',
  abril: 'PGC-AbrilFatface',
  italiana: 'PGC-Italiana',
} as const;

/** Type treatments for the posters' titles (size and tracking at the 108 pt poster). */
export const POSTER_TYPE = {
  bebas: { fontFamily: FONT.bebas, fontSize: 23, lineHeight: 22, letterSpacing: 1, upper: true },
  anton: { fontFamily: FONT.display, fontSize: 17, lineHeight: 19, letterSpacing: 0.2, upper: true },
  dmserif: { fontFamily: FONT.dmserif, fontSize: 17, lineHeight: 18, letterSpacing: -0.2, upper: false },
  archivo: { fontFamily: FONT.archivo, fontSize: 12.5, lineHeight: 14, letterSpacing: -0.2, upper: true },
  marcellus: { fontFamily: FONT.marcellus, fontSize: 11.5, lineHeight: 14, letterSpacing: 2.4, upper: true },
  abril: { fontFamily: FONT.abril, fontSize: 17, lineHeight: 18, letterSpacing: 0, upper: false },
  italiana: { fontFamily: FONT.italiana, fontSize: 14, lineHeight: 16, letterSpacing: 2.6, upper: true },
} as const;
export type PosterFace = keyof typeof POSTER_TYPE;

export const PICKER = {
  editRight: 25,
  editY: 162.5,
  editSize: 12.7,
  titleY: 209,
  titleSize: 30,
  avatar: 135,
  colX: [108, 293],
  rowY: [335, 530.5],
  nameGap: [95, 99], // avatar centre to name centre, per row
  nameDx: 5, // the pin's names sit 5 pt right of their avatars' centres
  titleDx: 5.7,
  nameSize: 20,
  plusArm: 56,
  plusStroke: 7,
  footerFromBottom: 27,
  footerSize: 14.6,
} as const;

export const HOME = {
  side: 12,
  heroHeight: 512,
  heroFadeFrom: 0.72, // fraction of the hero where the art starts melting into the background
  chromeY: 18, // back arrow and gear, below the status bar inset
  chromeX: 42,
  chromeSize: 20,
  titleY: 433,
  titleSize: 17.4,
  genreY: 455,
  genreX: 32,
  genreSize: 14,
  starsY: 471,
  starsX: 36,
  star: 16.5,
  starPitch: 21,
  rating: 3.5,
  playX: 358.5,
  playY: 455.3,
  play: 41,
  // Hero title lockup drawn on the art.
  badgeY: 263,
  subtitleY: 280,
  lockupY: 302,
  lockupSize: 33,
  firstLabelY: 529,
  labelSize: 15.7,
  labelNudge: 4.8, // ink centre of a label vs its line box, measured
  labelToContent: 28,
  sectionGap: 30, // previous row's bottom to the next label's centre
  poster: { w: 108.5, h: 168, gap: 11.5, radius: 14 },
  wide: { w: 330, image: 180, info: 85, gap: 19, radius: 13, progress: 3, progressInset: 5.7, progressLift: 2, titleSize: 19.4, titleNudge: 5, metaSize: 12.5 },
  tall: { w: 203, h: 307, gap: 8.5, radius: 14, wordSize: 31, wordFromBottom: 29 },
  tabBar: 64, // above the bottom inset
  tabIconY: 31,
  tabX: [44, 119, 198.5, 284, 345],
  tabIcon: [30, 24, 32, 27, 35], // per icon: the pin's glyphs differ in size
} as const;

