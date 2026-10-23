/** Colours, sizes and type, measured from the source's finance screen. */
export const COLOR = {
  // Fitted on f017 in flat coordinates: the slab darkens toward its top (Y 528 → 763 pt sampled).
  gradient: ['#A8448F', '#C65F9E', '#D977A5', '#DD81A8', '#DF86A7', '#E290A7', '#E89CA7'] as const,
  desk: ['#3F0F46', '#4E1C54'] as const, // the surface the phone lies on
  gradientStops: [0, 0.596, 0.694, 0.75, 0.816, 0.861, 1] as const,
  plum: '#3B1D5E',
  plumInk: '#2A1638',
  cardInk: '#2D2B35', // the source's balance digits are a near-black grey
  white: '#FFFFFF',
  pink: '#F26293',
  pinkDeep: '#E8447A',
  title: '#FFFFFF',
  subtitle: 'rgba(255,214,236,0.85)',
  cardLabel: '#E0679E',
  cardCurrency: '#B9B2BE',
  slab: 'rgba(255,255,255,0.18)',
  slabEdge: 'rgba(255,255,255,0.36)',
  slabShadow: '#5A1F55',
  panel: '#F9C0C4',
  panelEdge: '#E494A7',
  deskEdge: '#49184F', // the desk beside the screen's bottom-left corner (sampled on f017)
  panelIcon: '#D08AA2', // the source's glyphs are soft at its resolution; a touch lighter keeps their edge as weak
  panelIconActive: '#FFFFFF',
  menuInk: '#141414', // the source's menu labels are near-black
  menuPlus: '#8E6A9A',
  menuScrim: 'rgba(250,215,230,0.06)', // the source barely lifts the slab behind its menu
  income: '#2FBF71',
  transfer: '#8B6CF6',
  scrim: 'rgba(59,29,94,0.18)',
  finger: 'rgba(255,255,255,0.5)',
  lens: 'rgba(255,255,255,0.32)', // the active tab's glass lens
  lensRim: 'rgba(255,255,255,0.7)',
  glassEdge: 'rgba(255,255,255,0.9)',
  pinkGlow: 'rgba(242,98,147,0.35)',
  sheen: ['rgba(255,255,255,0)', 'rgba(255,255,255,0.45)', 'rgba(255,255,255,0)'] as const,
  fingerEdge: 'rgba(255,255,255,0.8)',
} as const;

export const ICON_TINT = {
  yellow: '#F2C14E',
  lilac: '#B9A4FF',
  peach: '#FFB38A',
  mint: '#8FE3C0',
  sky: '#8CC8FF',
  rose: '#FF9DBB',
} as const;

/**
 * The source shows the app on a phone lying at an angle. The app is laid out flat (402 × 874 pt, measured on
 * the frame rectified by the same mapping) and drawn through one affine matrix that puts it where the frame does.
 * flat (X, Y) → frame px: P0 + u·X + v·(Y − 874); frame px → screen pt: (px − cropX) · scale.
 */
export const TILT_VIEW = {
  p0: [275, 543], // the screen's bottom-left corner in the 800 × 600 frame
  u: [1.112, -0.699], // frame px per pt along the screen's width
  v: [1.085, 0.717], // frame px per pt along the screen's height
  cropX: 262, // the portrait window the reference shows of the frame
  scale: 1.4567, // screen pt per frame px
  screenRadius: 44,
} as const;

/** The flat app, measured on the rectified f017 (pt). */
export const FLAT = {
  card: { left: 27, top: 345, right: 27, height: 150, radius: 18 },
  section: { left: 58, centreY: 598 },
  rowsTop: 628.7, // pixel gate: re-measured on f017 against Montserrat
  rowLeft: 61,
  rowRight: 26.8,
  // Measured on f017 rectified at 4 px/pt: the screen's edge is at X 14, its bottom at Y 887.5, corner radius 26.
  panel: { left: 14, top: 808, radiusTop: 30, radiusBottom: 23 },
  tabs: [85, 167, 247] as const,
  tabY: 850.2,
  fab: { left: 318, top: 787, size: 80, radius: 22 },
  // The open menu (rectified state a): rows 65 pt apart, labels at X 167, icons centred at X 357.
  menu: { left: 126, top: 583.3, width: 270, height: 220.7, radius: 33, padTop: 13.1, padLeft: 41, padRight: 27, item: 65, icon: 24 },
  // Overview tab (rectified f005)
  cards: { left: 60, pitch: 250, width: 210, top: 463.5, height: 219.3, radius: 18, badge: 100, badgeLeft: 28, badgeTop: 440 },
} as const;

/**
 * Spacing scale (spacing-system-designer), from the source's own gaps: rows 82 pt apart with 12 pt inside the
 * icon circle, the menu's 65 pt rows and 41 pt label inset, the panel's 30 pt top radius. A 4 pt base; the UI the
 * upgrade adds (the Undo pill, its action, the glass lens) uses only these steps.
 */
export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

export const LAYOUT = {
  gutter: 20,
  cardTop: 12,
  cardHeight: 112,
  cardRadius: 28,
  cardBadge: 56,
  cardBadgeRadius: 18,
  sectionGap: 32,
  sectionTitle: 30,
  sectionBottom: 8,
  row: 82,
  circle: 58,
  textLeft: 135.95,
  rowRadius: 22,
  tile: 60,
  tileHeight: 56,
  tileRadius: 18,
  tileGap: 8,
  panel: 84,
  panelRadius: 32,
  fab: 64,
  fabRadius: 22,
  fabRight: 24,
  pillHeight: 52,
  pillGap: 16,
  menuItem: 48,
  lens: 52,
  menuWidth: 248,
  menuRadius: 28,
  finger: 44,
} as const;

/** Where a row rests when its actions are open: two tiles, their gap and the gutter. */
export const OPEN_X = -(LAYOUT.tile * 2 + LAYOUT.tileGap + LAYOUT.gutter + LAYOUT.tileGap);

/** Montserrat (SIL OFL, `assets/Montserrat-OFL.txt`): the source's geometric sans. Names are this block's own. */
export const FONT = {
  medium: 'TiltSwipe-Montserrat-Medium',
  semibold: 'TiltSwipe-Montserrat-SemiBold',
  bold: 'TiltSwipe-Montserrat-Bold',
} as const;

export const TYPE = {
  cardLabel: { fontSize: 14, lineHeight: 18, fontFamily: FONT.semibold },
  balance: { fontSize: 44, lineHeight: 52, fontFamily: FONT.semibold, letterSpacing: -0.5 },
  currency: { fontSize: 40, lineHeight: 52, fontFamily: FONT.medium },
  section: { fontSize: 24.3, lineHeight: 30, fontFamily: FONT.semibold, letterSpacing: 0.2 },
  rowTitle: { fontSize: 18.5, lineHeight: 24, fontFamily: FONT.semibold },
  rowSub: { fontSize: 16.6, lineHeight: 21, fontFamily: FONT.medium },
  amount: { fontSize: 18.5, lineHeight: 24, fontFamily: FONT.semibold },
  tile: { fontSize: 11, lineHeight: 13, fontFamily: FONT.semibold, letterSpacing: 0.2 },
  pill: { fontSize: 15, lineHeight: 20, fontFamily: FONT.medium },
  pillAction: { fontSize: 15, lineHeight: 20, fontFamily: FONT.bold },
  menu: { fontSize: 20, lineHeight: 24, fontFamily: FONT.semibold },
} as const;
