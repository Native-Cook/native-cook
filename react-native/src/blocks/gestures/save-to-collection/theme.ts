/** Colours, sizes and type, from the source's black splash with white bookmark. */
export const COLOR = {
  background: '#000000',
  ink: '#FFFFFF',
  mute: '#8E8E93',
  card: '#1C1C1E',
  cardEdge: 'rgba(255,255,255,0.18)',
  sheen: ['rgba(255,255,255,0.10)', 'rgba(255,255,255,0)'] as const,
  badge: '#FFFFFF',
  badgeInk: '#000000',
  check: '#FFFFFF',
  checkRing: 'rgba(255,255,255,0.85)',
  dim: 'rgba(0,0,0,0.55)',
  glow: 'rgba(255,255,255,0.18)',
  fade: ['rgba(0,0,0,0)', 'rgba(0,0,0,0.92)'] as const,
  finger: 'rgba(255,255,255,0.35)',
  glassEdge: 'rgba(255,255,255,0.35)',
  fingerEdge: 'rgba(255,255,255,0.75)',
} as const;

/** Glow (Liquid Glass language): pre-blurred art, never an animated blur. */
export const GLOW = {
  under: 28, // the lifted card's hue glow
  underOpacity: 0.75,
  underDrop: 18,
} as const;

export const GLASS = { intensity: 40 } as const;

/**
 * Spacing scale (spacing-system-designer), derived from the source's measured gaps: the pile overlaps cards by
 * 8–16 pt, the glyph sits 24 pt clear of the last card's shadow, card corners are 12 pt. A 4 pt base; every element
 * the upgrade adds (the count pill, its edge, its gap under the glyph) uses these steps only.
 */
export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

export const LAYOUT = {
  gutter: 16,
  gap: 8,
  headerTop: 8,
  header: 44,
  cardRadius: 12,
  bookmark: { width: 62, height: 75 }, // measured in f017
  bookmarkY: 766,
  bookmarkRiseY: 393, // f031: the glyph's centre once it has risen // the source's glyph centre on an 874 pt screen (f017)
  dockFade: 160,
  badge: 20,
  corner: 22, // saved mark on a card
  check: 26,
  saveButton: { height: 36, radius: 18 },
  finger: 44,
} as const;

export const TYPE = {
  title: { fontSize: 28, lineHeight: 34, fontWeight: '700' as const, letterSpacing: 0.2 },
  action: { fontSize: 17, lineHeight: 22, fontWeight: '600' as const },
  badge: { fontSize: 12, lineHeight: 14, fontWeight: '700' as const, fontVariant: ['tabular-nums'] as ('tabular-nums')[] },
  count: { fontSize: 15, lineHeight: 20, fontWeight: '600' as const, letterSpacing: -0.2, fontVariant: ['tabular-nums'] as ('tabular-nums')[] },
  save: { fontSize: 15, lineHeight: 20, fontWeight: '600' as const },
} as const;
