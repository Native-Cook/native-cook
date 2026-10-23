/** Colours, sizes and type measured from the source's video editor (1 px = 1.426 pt). */
export const COLOR = {
  background: '#000000',
  chrome: '#1C1C1E',
  chromeHi: '#2C2C2E',
  ink: '#FFFFFF',
  mute: '#8E8E93',
  divider: '#2A2A2C',
  toolDim: '#5C5C60',
  faint: '#5A5A5E',
  blue: '#1E8FFF',
  strip: '#151517',
  playhead: '#FFFFFF',
  scrim: 'rgba(0,0,0,0.72)',
  touch: 'rgba(255,255,255,0.35)',
  outline: 'rgba(255,255,255,0.9)',
  caption: '#FFFFFF',
  captionShadow: 'rgba(0,0,0,0.6)',
  finger: 'rgba(255,255,255,0.35)',
  fingerEdge: 'rgba(255,255,255,0.7)',
  sticker: '#3FA34D',
  stickerRing: '#B8E986',
  stickerInk: '#FFFFFF',
  overlayButton: 'rgba(28,28,30,0.72)',
  badge: 'rgba(20,20,22,0.55)',
  badgeDot: 'rgba(255,255,255,0.35)',
  rim: '#FFFFFF',
} as const;

/** The preview keeps the screen's own aspect, so the largest size is the full screen with no jump. */
export const ASPECT = 402 / 874;

export const LAYOUT = {
  gutter: 20,
  barY: 14, // top bar centre below the safe area
  barButton: 32,
  previewTop: 6, // below the safe area
  previewRadius: 14,
  controlsGap: 29, // preview bottom → controls centre (compact)
  control: 32,
  dividerGap: 29, // controls centre → the hairline over the ruler
  rulerGap: 46, // controls centre → ruler centre (compact)
  rulerPitch: 85, // dot, label, dot, label
  rulerLabelW: 30,
  ruler: 12,
  stripGap: 24, // ruler bottom → strip top (compact)
  strip: 60,
  track: 34,
  trackFirstGap: 4,
  trackGap: 8,
  trackIndent: 83, // icon and label start here, as in the source
  trimHandle: { x: 73, width: 10, height: 30 },
  sticker: { x: 125, size: 26 },
  hintGap: 51,
  hint: 16,
  thumb: 340 / 12, // timeline thumbnail width: three per clip
  hintBottom: 118, // above the bottom of the screen
  tools: 78, // tool row height above the safe bottom
  tool: 52, // tile width (height toolH), pitch 58.8 from x = 11
  toolH: 41,
  toolGap: 6.8,
  toolInset: 11,
  toolRadius: 10,
  coachButton: { width: 84, height: 36 },
  finger: 44,
} as const;

/** Spacing scale derived from the source's measured gaps (gutter 20, tool gap 6.8 ≈ 8, track gap 8, track inset 4,
 * button pair gap 8): a 4 pt base. Everything the upgrade adds sits on it; the source's own measured values stay in LAYOUT. */
export const SPACE = { xxs: 2, xs: 4, sm: 8, md: 12, lg: 16, xl: 20, xxl: 24 } as const;

export const BADGE = { height: 26, bottom: SPACE.md, dot: 5, padX: SPACE.md, gap: SPACE.sm, blur: 30 } as const;

/** The coach mark, measured on the rectified f001 (points on a 402 × 874 screen). */
export const COACH = {
  hand: { x: 169, y: 267, width: 44, height: 70 },
  touch: { x: 10, y: -7, size: 24 }, // relative to the hand box: on the fingertip
  textTop: 489,
  button: { x: 160, y: 571, width: 79, height: 30 },
} as const;

export const TYPE = {
  time: { fontSize: 13, lineHeight: 16, fontWeight: '500' as const, fontVariant: ['tabular-nums'] as ('tabular-nums')[] },
  ruler: { fontSize: 9, lineHeight: 12, fontWeight: '600' as const },
  track: { fontSize: 13, lineHeight: 16, fontWeight: '500' as const },
  hint: { fontSize: 12, lineHeight: 16, fontWeight: '400' as const },
  tool: { fontSize: 10.5, lineHeight: 13, fontWeight: '600' as const },
  caption: { fontSize: 17, lineHeight: 22, fontWeight: '800' as const },
  coach: { fontSize: 22, lineHeight: 27, fontWeight: '700' as const },
  coachButton: { fontSize: 13, lineHeight: 16, fontWeight: '600' as const },
  pill: { fontSize: 15, lineHeight: 20, fontWeight: '600' as const },
} as const;

/** The hint stays put above the tool row (the tracks slide under it); the source moves the rows under the preview a little closer together as it grows: offsets at the large size, in pt. */
export const SQUEEZE = { controls: -2, ruler: -3, below: -14, hint: -22, tools: -23 } as const;
