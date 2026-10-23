/** Colours, sizes and type measured from the source's to-do list (1 px = 1.426 pt). */
export const COLOR = {
  background: '#FBFAFD',
  ink: '#3A393E', // the source's titles read softer than pure ink
  mute: '#AEAEB2',
  check: '#B8B8BD',
  note: '#B4B4B9',
  nav: '#8E8E93',
  tray: '#E3C454',
  trayLine: '#9C7F22',
  blue: '#5B9BEE',
  blueDeep: '#3F86E6',
  band: '#D6E8FC', // sampled at row centres on the rectified f017
  bandSeam: 'rgba(255,255,255,0.45)', // the soft 1–2 px seam between stacked selected rows
  gloss: ['rgba(255,255,255,0)', 'rgba(255,255,255,0.85)', 'rgba(255,255,255,0)'] as const,
  circle: '#C7C7CC',
  toolbar: '#2C2C2E',
  toolbarInk: '#FFFFFF',
  white: '#FFFFFF',
  finger: 'rgba(60,60,67,0.22)',
  fingerEdge: 'rgba(60,60,67,0.35)',
} as const;

/**
 * Spacing scale (step 0, 20 Oct), derived from the source's measured gaps: 4 pt base; 8 = band inset and list top,
 * 12 = circle inset, 20 = gutter, 48 = row pitch. Source elements keep their measured positions; the upgrade's
 * additions (the selecting row's text stop, the auto-scroll zone, ripple and gloss inside the band) use this scale.
 */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, gutter: 20, xl: 24, xxl: 32 } as const;

export const LAYOUT = {
  gutter: 20,
  nav: 44,
  navTop: 6,
  row: 48,
  listTop: 8,
  check: 19,
  checkRadius: 5,
  textLeft: 48,
  circle: 26,
  circleRight: 12,
  circleColumn: 64, // touch area of the circles, from the right edge
  bandInset: 8,
  bandRadius: 10,
  done: { width: 68, height: 36, radius: 8 },
  more: 32,
  toolbar: { width: 328, height: 46, radius: 14, bottom: 1.3 }, // measured on the rectified f017: 37–365 pt, bottom at 838.7 pt
  /** Tool centres from the toolbar's left edge (When and Move groups, trash, more), measured on f017. */
  tools: [51, 164.5, 243.7, 300] as const,
  toolIcon: 22,
  fab: 56,
  fabBottom: 18,
  edge: SPACE.xxl * 2 + SPACE.sm, // auto-scroll zone, 72 pt (a row and a half)
  finger: 44,
} as const;

export const TYPE = {
  row: { fontSize: 20, lineHeight: 24, fontWeight: '400' as const }, // overlaid on the source's rows
  nav: { fontSize: 18, lineHeight: 22, fontWeight: '600' as const },
  done: { fontSize: 17, lineHeight: 22, fontWeight: '600' as const },
  tool: { fontSize: 17.5, lineHeight: 22, fontWeight: '600' as const },
} as const;
