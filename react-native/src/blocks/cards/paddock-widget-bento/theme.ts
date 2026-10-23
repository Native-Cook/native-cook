import { Platform } from 'react-native';

/**
 * Every colour, size and string of Paddock Widget Bento. Reference units are points on a 402-wide screen (the pin's
 * iPhone): the screen scales them by `width / REFERENCE_WIDTH`. Round 2 (2026-10-21) re-measured the pin
 * (`qa/ref-09.png`, 1206 px = 402 pt): tile footprints, a 12 pt row rhythm, Montserrat type at the pin's sizes and
 * baselines, and the Liquid Glass dock. Text is placed by its baseline: a line's top is `baseline - ASCENT * size`.
 */
export const REFERENCE_WIDTH = 402;

/** Montserrat's hhea ascender (968 / 1000): the distance from a Text's top edge to its baseline, per point of size. */
export const ASCENT = 0.968;

/** Colours sampled from the pin (median of a 2 × 2 pt patch away from text). */
export const colors = {
  background: '#000000',
  violet: 'rgb(50,33,252)',
  orange: 'rgb(254,63,3)',
  yellow: 'rgb(255,218,38)',
  cream: 'rgb(242,238,223)',
  statsGlow: 'rgb(42,40,40)',
  statsShade: 'rgb(20,18,21)',
  green: 'rgb(5,208,130)',
  blue: 'rgb(62,157,255)',
  red: 'rgb(247,26,33)',
  rose: 'rgb(253,121,120)',
  guideYellow: 'rgb(252,254,31)',
  micInk: 'rgb(255,196,178)',
  creamInk: 'rgb(242,238,223)',
  bannerShade: 'rgba(110,0,6,0.92)',
  bannerGlow: 'rgba(196,8,18,0.62)',
  bannerClear: 'rgba(220,20,28,0.38)',
  bannerTrail: 'rgb(167,0,9)',
  statsTrail: 'rgb(70,66,68)',
  white: '#FFFFFF',
  black: '#000000',
  ink78Black: 'rgba(0,0,0,0.78)',
  ink55Black: 'rgba(0,0,0,0.55)',
  ink75White: 'rgba(255,255,255,0.75)',
  closeFill: 'rgba(0,0,0,0.5)',
  sealStroke: 'rgba(247,26,33,0.18)',
  /** Back of the peeled lettering: sticker paper, darker where it bends at the fold. */
  stickerBack: 'rgb(246,236,232)',
  stickerFold: 'rgb(196,172,168)',
  stickerShadow: 'rgb(60,0,4)',
  slapLine: 'rgba(255,255,255,0.9)',
  dockGlassTint: 'rgba(30,8,10,0.18)',
  dockFallback: 'rgba(28,20,22,0.62)',
  dockStroke: 'rgba(255,255,255,0.22)',
  dockLens: 'rgba(255,255,255,0.20)',
  dockLensStroke: 'rgba(255,255,255,0.38)',
  dockIcon: 'rgba(255,255,255,0.94)',
  dockIconActive: 'rgb(255,226,224)',
  shadow: '#000000',
  // Native-control tokens (iOS dark system colours the sheets and lists render with).
  secondary: 'rgba(235,235,245,0.6)',
  panel08: 'rgba(255,255,255,0.08)',
  panel10: 'rgba(255,255,255,0.10)',
  panel12: 'rgba(255,255,255,0.12)',
  hairline: 'rgba(255,255,255,0.12)',
  accent: 'rgb(10,132,255)',
  accentTint: 'rgba(10,132,255,0.15)',
  selectedCircuit: 'rgb(255,214,10)',
  sheet: 'rgb(28,28,30)',
  sheetRow: 'rgb(44,44,46)',
  sheetSeparator: 'rgba(84,84,88,0.65)',
  pinnedLabel: 'rgba(235,235,245,0.6)',
  disabledPin: 'rgba(255,255,255,0.5)',
} as const;

/** Montserrat (OFL, bundled and subset to Latin) is the pin's face; `cast` keeps the system serif italic. */
export const FONT_FILES = {
  'PaddockMontserrat-Medium': require('./assets/PaddockWidgetBento-Montserrat-Medium.ttf'),
  'PaddockMontserrat-SemiBold': require('./assets/PaddockWidgetBento-Montserrat-SemiBold.ttf'),
  'PaddockMontserrat-Bold': require('./assets/PaddockWidgetBento-Montserrat-Bold.ttf'),
  'PaddockMontserrat-ExtraBold': require('./assets/PaddockWidgetBento-Montserrat-ExtraBold.ttf'),
  'PaddockMontserrat-Black': require('./assets/PaddockWidgetBento-Montserrat-Black.ttf'),
} as const;

export const fonts = {
  medium: 'PaddockMontserrat-Medium',
  semibold: 'PaddockMontserrat-SemiBold',
  bold: 'PaddockMontserrat-Bold',
  extraBold: 'PaddockMontserrat-ExtraBold',
  black: 'PaddockMontserrat-Black',
  rounded: Platform.select({ ios: 'ui-rounded', default: undefined }),
  serif: Platform.select({ ios: 'ui-serif', default: 'serif' }),
};

/**
 * The pin's spacing, measured in points. Base unit 2: every inset and gap is a measured value within 1 pt of the pin.
 * Columns: 10 · 186 · 10 · 186 · 10. Rows: 62 / 62 / 62 | 144 | 62 | 128 | 110 | 170 with a 12 pt rhythm.
 */
export const space = {
  screenInset: 10,
  columnGap: 10,
  rowGap: 12,
  /** Quick and action tiles: text after a leading mark, or flush at the tile's inset when there is none. */
  tileInset: 16,
  tileTextAfterMark: 60.5,
  tileMarkCenter: 31,
  /** Pitcast card. */
  pitcastInset: 14.5,
  pitcastArrowInset: 12.5,
  /** Stats card. */
  statsTextX: 85.3,
  statsCaptionX: 21.3,
  /** Hero lettering and seal, card-local. */
  heroFirstX: 15.7,
  heroSecondX: 58.4,
  /** Guide card. */
  guideInset: 14.3,
  guideCloseInset: 12.7,
  bannerPad: 20,
  /** Below the dock's top edge, so the strip of banner above the dock shows only the photo, as on the pin. */
  bannerTop: 28,
  bannerGap: 8,
  dockPad: 7,
  dockLensInset: 5,
  handleTrailing: 14,
  pinnedGap: 8,
  pinnedInset: 18,
  // Secondary pages (unchanged from round 1).
  pageGap: 18,
  pageInset: 24,
  pageTop: 28,
  rowInset: 16,
  resultRow: 58,
  circuitGap: 10,
  circuitRow: 52,
  customizeGap: 12,
  customizeInset: 14,
  studioGap: 16,
  previewGap: 10,
  studioInset: 24,
  lapsGap: 20,
  statsGap: 18,
  reactionGap: 22,
  sheetPad: 16,
  sheetRowInset: 16,
  sheetRowGap: 12,
  hairline: 1,
} as const;

const COL = 186;
const FULL = 382;
const TOP = 54.5;
const RIGHT_COL = space.screenInset + COL + space.columnGap;

/** Geometry in reference points; `top` and `left` are on the 402-point screen (the scroll content starts at y = 0). */
export const layout = {
  slotY: [TOP, TOP + 74, TOP + 148],
  quickCard: { width: COL, height: 62, x: space.screenInset, top: TOP },
  pitcast: { width: COL, height: 210, left: RIGHT_COL, top: TOP, mic: 50, arrow: 18.5, glyphRow: 179.8, glyphX: [25.7, 62, 99], glyph: 20 },
  stats: {
    width: FULL, height: 144, left: space.screenInset, top: 276.5,
    art: { x: 254, y: 15, width: 106, height: 110 },
    icon: { x: 25, y: 27.5, width: 41, height: 51 },
  },
  glossary: { left: space.screenInset, top: 432.5 },
  education: { left: RIGHT_COL, top: 432.5 },
  actionTile: { width: COL, height: 62 },
  hero: { width: FULL, height: 128, left: space.screenInset, top: 506.5, seal: { size: 40.5, centerX: 33, centerY: 91.2 } },
  guide: { width: FULL, height: 110, left: space.screenInset, top: 646.5, close: { size: 18.5, centerY: 21.5 } },
  banner: { width: FULL, height: 170, left: space.screenInset, top: 768.5 },
  grid: { width: 402, height: 938.5 },
  /** Baselines (card-local) of every text line, measured on the pin. */
  baseline: {
    eyebrow: 25.8, title: 44.8,
    violet: [32, 47.5], violetX: 71.7,
    pitcastTitle: 106.8, pitcastSubtitle: 131.4, pitcastNames: 152.5,
    statsEyebrow: 18.3, statsWord: 54.8, caption: [123.4, 138.2],
    heroFirst: 54, heroSecond: 108.8,
    guideTitle: 46.8, guideHelper: [70.7, 87.9],
  },
  violetBadge: { width: 47.5, height: 28.5, centerX: 38.5, stroke: 2.5 },
  orangeChip: { width: 30, height: 19, trailing: 14.7 },
  /** Left of the pitcast card's arrow badge (x 361–379.5), 8 pt clear of it, and right of the mic tile (to x 270.5). */
  done: { width: 72, centerX: 317, centerY: 85.5 },
  pinned: { top: 950.5, left: space.screenInset, width: FULL, rowHeight: 64, rowPitch: 72 },
  content: { height: 950.5, minHeight: 1010, bottomClearance: 118 },
  dock: { width: 358, height: 62, bottom: 22, lensWidth: 74, lensHeight: 52, icon: 22 },
  touchSurface: { width: COL, height: 210, x: space.screenInset, top: TOP, pickRadius: 38 },
  unscaled: { tapTarget: 44, closeButton: 44, doneHeight: 44, outlineWidth: 80, outlineHeight: 44, rowMinHeight: 44, previewHeight: 160, pinHeight: 48 },
} as const;

/** Corner radii. Large cards use iOS continuous corners (`borderCurve: 'continuous'`), as the pin does. */
export const radius = {
  quickCard: 31,
  card: 28,
  mic: 13.5,
  chip: 5.5,
  pinned: 22,
  row: 18,
  customizeRow: 16,
  preview: 24,
  dock: 31,
  lens: 26,
} as const;

/** Type sizes (points) and tracking, Montserrat unless noted. */
export const type = {
  eyebrow: 12.5,
  eyebrowTracking: -0.3,
  tileTitle: 16.5,
  tileTitleTracking: -0.2,
  violet: 19.5,
  violetTracking: -0.9,
  badge: 13,
  cardIcon: 24,
  chipIcon: 13,
  handle: 16,
  pitcastIcon: 26,
  pitcastArrow: 8,
  pitcastTitle: 32,
  pitcastSubtitle: 12.2,
  pitcastNames: 10.2,
  statsIcon: 50,
  statsEyebrow: 16,
  statsWord: 32.5,
  caption: 12,
  tileIcon: 26,
  heroGlyph: 61,
  heroSeal: 13,
  guideTitle: 28.2,
  guideHelper: 12,
  guideClose: 7.5,
  bannerTitle: 32,
  bannerSubtitle: 16,
  done: 15,
  dockIcon: 22,
  pageTitle: 30,
  body: 17,
  headline: 17,
  title2: 22,
  title3: 20,
  largeTitle: 34,
  studioIcon: 42,
} as const;

/**
 * Values the SwiftUI sheets get from the system (grouped lists, segmented control, bordered buttons, HStack's default
 * spacing). They are not in the Swift source; these are the iOS dark-appearance values the stand-in controls use.
 */
export const native = {
  stackSpacing: 8,
  listRadius: 20,
  listInset: 16,
  listRowMinHeight: 44,
  listRowInsetY: 11,
  segmentedHeight: 32,
  segmentedRadius: 9,
  segmentedThumbRadius: 7,
  segmentedTrack: 'rgba(118,118,128,0.24)',
  segmentedThumb: 'rgb(99,99,102)',
  searchHeight: 36,
  searchRadius: 10,
  searchIcon: 17,
  searchGap: 4,
  buttonHeight: 44,
  buttonInsetX: 20,
  smallText: 13,
  switchOn: 'rgb(48,209,88)',
  headerHeight: 52,
  grabberGap: 8,
} as const;

export const shadow = {
  rest: { radius: 4, opacity: 0.16, offsetY: 4 },
  lifted: { opacity: 0.28, offsetY: 4 },
  strokeWidth: 1,
  sealStroke: 1,
} as const;

/** Opacity tokens that are not colours. */
export const alpha = {
  pitcastNames: 0.9,
  captionInk: 1,
  pressed: 0.8,
  /** Glue left on the card where the sticker lifted, and the lifted flap's shadow. */
  glueGhost: 0.1,
  stickerShadow: 0.42,
  disabled: 0.5,
} as const;

/** Copy, verbatim from the Swift block. */
export const copy = {
  cards: {
    laps: { eyebrow: 'every lap', title: 'counts', label: 'every lap counts', name: 'Every lap counts' },
    reflex: { eyebrow: 'Race day', title: 'Reflex test', label: 'Race day Reflex test', name: 'Race day reflex test' },
    homeCards: { eyebrow: 'custom', title: 'home cards', label: 'custom home cards', name: 'Custom home cards' },
  },
  pitcast: { title: ['pit', 'cast'], subtitle: 'Every Friday at 6pm', names: 'Alex Vale / Sam Ives', label: 'Read the weekend brief, Alex Vale and Sam Ives' },
  stats: { race: 'Turn Nine', word: 'Stats', caption: ['Stats, records & stories', 'from Turn Nine'], label: 'Race Stats. Switch between latest and season records' },
  glossary: 'Race glossary',
  education: 'Race education',
  brand: 'Turn Nine',
  glossaryTitle: 'Glossary',
  educationTitle: 'Education',
  badge: 'TN',
  hero: { first: 'exclusive', second: 'widgets', seal: 'TN', label: 'Exclusive widgets. Open Widget Studio' },
  guide: { title: 'how to move cards?', helper: ['Hold a quick card and drag it to a new slot,', 'then tap Done to keep the new order.'], close: 'Close arrangement guide' },
  banner: { title: 'Your race-day space', subtitle: 'Build a dashboard that feels like yours.', label: 'Your race-day space. Open Widget Studio' },
  done: { label: 'Done', hint: 'Finish arranging quick cards' },
  moveUp: 'Move up',
  moveDown: 'Move down',
  pinned: 'Pinned',
  weekends: ['Harbor Circuit', 'Juniper Park'],
  racers: [['Mira Chen', 42], ['Alex Vale', 38], ['Sam Ives', 34], ['Noah Reed', 29]] as const,
  circuits: ['Harbor', 'Juniper', 'Mesa'],
  widgets: ['Lap count', 'Race weekend', 'Circuit'],
  glossaryTerms: [
    ['Apex', "The innermost point of a corner's racing line."],
    ['Braking point', 'The place where a driver begins slowing for a turn.'],
    ['Kerb', 'The raised painted edge beside the racing surface.'],
    ['Slipstream', 'Reduced air resistance behind another car.'],
    ['Sector', 'One timed section of a complete lap.'],
    ['Stint', 'A continuous run between pit stops.'],
    ['Undercut', 'Pitting earlier to gain time on fresher tyres.'],
    ['Warm-up lap', 'The preparation lap before the timed start.'],
  ] as const,
  lessons: {
    'Find the apex': 'Look through the corner and choose the inside point that leaves a clean exit.',
    'Leave room': 'Keep a predictable lane when another car is already alongside.',
    'Read the weather': 'Watch surface colour and wind before choosing a braking reference.',
  } as Record<string, string>,
  lessonOrder: ['Find the apex', 'Leave room', 'Read the weather'],
  pitcastNotes: [
    'A cleaner first lap — Leave room before the first turn.',
    'Watch the weather — Coastal cloud can change quickly.',
    'One thing to notice — How the field settles after the restart.',
  ],
  pitcastHeading: 'The weekend brief · Alex Vale / Sam Ives',
  guideSteps: ['1. Hold a quick card for a moment.', '2. Move it to a new slot.', '3. Release, then tap Done.'],
  laps: { title: 'Every lap counts', week: 'This week: 18.4 km', logLap: 'Log a lap' },
  studio: { heading: 'Pin to your dashboard', body: 'Choose a card for your Turn Nine dashboard.', pin: 'Pin to dashboard', pinned: 'Pinned' },
  statsSheet: { latest: 'Harbor · 8 laps · 42 points', season: 'Season · 64 laps · 286 points' },
  localContent: 'Local Turn Nine content',
  customize: { heading: 'Quick card order', arrange: 'Arrange cards', done: 'Done', restoreOrder: 'Restore order', restoreGuide: 'Restore guide', up: 'Up', down: 'Down' },
  reaction: { ready: 'Ready', getReady: 'Get ready…', go: 'GO', tooSoon: 'Too soon. Try again', start: 'Start test', again: 'Try again', tap: 'Tap when ready' },
} as const;

/**
 * SF Symbol per use, with the Material icon the Android build draws instead. `fit` scales the SymbolView frame so the
 * drawn glyph matches SwiftUI's `.font(.system(size:))` rendering: SymbolView fits the symbol into a square frame,
 * which draws wide and heavy symbols smaller than a font-sized Image. Measured on the iPhone 18 Pro simulator at 402 pt
 * (ink width of the glyph in the Swift recording divided by the one in the React Native recording, both at fit 1).
 */
export const symbols = {
  laps: { ios: 'figure.walk', fallback: 'run', fit: 1.08 },
  reflex: { ios: 'gamecontroller.fill', fallback: 'gamepad-variant', fit: 1.1 },
  homeCards: { ios: 'square.grid.2x2.fill', fallback: 'view-grid', fit: 1.05 },
  handle: { ios: 'line.3.horizontal', fallback: 'menu', fit: 1.25 },
  mic: { ios: 'mic.fill', fallback: 'microphone', fit: 1 },
  arrowUpRight: { ios: 'arrow.up.right', fallback: 'arrow-top-right', fit: 1 },
  document: { ios: 'doc.text.fill', fallback: 'file-document', fit: 1.17 },
  glossary: { ios: 'books.vertical.fill', fallback: 'bookshelf', fit: 1.15 },
  education: { ios: 'circle.lefthalf.filled', fallback: 'circle-half-full', fit: 1.2 },
  close: { ios: 'xmark', fallback: 'close', fit: 1 },
  home: { ios: 'house.fill', fallback: 'home', fit: 1.4 },
  calendar: { ios: 'calendar', fallback: 'calendar-month', fit: 1.35 },
  results: { ios: 'trophy', fallback: 'trophy-outline', fit: 1.4 },
  circuits: { ios: 'globe', fallback: 'web', fit: 1.35 },
  customize: { ios: 'plus.rectangle.on.rectangle', fallback: 'card-plus-outline', fit: 1.4 },
  podcastListen: { ios: 'headphones.circle.fill', fallback: 'headphones', fit: 1.05 },
  podcastWatch: { ios: 'play.rectangle.fill', fallback: 'play-box', fit: 1.05 },
  podcastFeed: { ios: 'antenna.radiowaves.left.and.right', fallback: 'access-point', fit: 1 },
  check: { ios: 'checkmark.circle.fill', fallback: 'check-circle', fit: 1 },
} as const;
