/** Every colour, size and type style of the block. Monochrome, measured from the source artboard. */
export const COLOR = {
  background: '#1C1C1C',
  track: '#2C2C2C',
  trackDot: '#232323',
  fill: '#FFFFFF',
  fillDot: '#1C1C1C',
  pivot: '#1C1C1C',
  ink: '#FFFFFF',
  mute: '#8C8C8C',
  finger: 'rgba(82,82,82,0.92)', // the source's touch circle
  dimFill: '#2C2C2C', // the lever at rest after a refresh: the white goes out
  arcOff: '#333333',
  shadow: '#0B0B0B',
  shaftLight: ['rgba(255,255,255,0)', 'rgba(255,255,255,0.9)'] as const,
  specular: 'rgba(255,255,255,0.22)', // a soft highlight on the grey knob, kept subtle so the knob reads as the source's
  knobLit: '#BDBDBD',
} as const;

/** The source artboard (720 × 540 px) scaled to the screen's width: 1 px = 0.558 pt. */
export const ARTBOARD = { height: 301, leverLeft: 115, leverCentreY: 150 } as const;

/** The lever exactly as drawn in the source: 307 × 30 px, pivot dot 12 px, nine dots on a 30 px pitch. */
export const LEVER = {
  length: 171,
  thickness: 17,
  pivotDot: 7,
  dot: 2.2,
  pitch: 16.75,
  dots: 9,
  knob: 33,
} as const;

/**
 * Spacing scale (step 0, 20 Oct): 4 pt base. The source's own rhythm is the lever's 16.75 pt dot pitch and 17 pt
 * thickness; the upgrade's additions use this scale or that rhythm: the caption 8 pt from the cap, the tick arc
 * two lever thicknesses (34 pt) round the pivot.
 */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

export const LAYOUT = {
  captionGap: SPACE.sm, // between the caption's end and the pivot cap
  arcRadius: 2 * LEVER.thickness, // the tick arc round the pivot
} as const;

export const TYPE = {
  caption: { fontSize: 12, lineHeight: 16, fontWeight: '500' as const, letterSpacing: 0.1 },
} as const;
