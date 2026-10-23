/** Colours, type, layout and copy, measured on the source (rectified to 402 × 874 pt). Shares of the screen or points. */

export const COLOR = {
  // The blush backdrop as the source shows it: warmer and lighter towards the bottom left (four corners, sampled).
  bgTL: '#EAC5BE',
  bgTR: '#E0B8B3',
  bgBL: '#E9CDBE',
  bgBR: '#F5D2C2',
  ink: '#2A1A1C',
  // Secondary lines: the same warm ink, lighter, so the name leads.
  inkSoft: '#2E1E20',
  hint: '#BE9C8F',
  lensInk: '#2A2226',
  shadow: '#5A3A36',
  buttonLight: '#F9E1D5',
  buttonLightEdge: 'rgba(90,58,54,0.10)',
  buttonLightShadow: '#8A5A52',
  buttonLightInk: '#3A3236',
  buttonDark: '#1E0A0E',
  buttonDarkInk: '#FFFFFF',
  finger: 'rgba(255,255,255,0.45)',
  fingerRing: 'rgba(35,29,32,0.30)',
  // The iridescent band of the pulled dome (inner → core → outer), as in the source.
  ringInner: '#7FE0FF',
  ringCore: '#1E3BFF',
  ringOuter: '#B05CFF',
  ringBlush: '#FF8FCB',
  pulse: '#FFFFFF',
  // The glow field behind the second screen, in the band's hues.
  fieldA: 'rgba(70,90,255,0.16)',
  fieldB: 'rgba(176,92,255,0.10)',
} as const;

// The system face (SF Pro), as the source; Skia resolves 'System' to the same face.
export const FONT = 'System';

export const TYPE = {
  // 'A new era of / software is here.' — 30.7 pt measured, 37 pt line pitch.
  intro: { fontSize: 31, lineHeight: 37, fontWeight: '400' as const, letterSpacing: -0.3, color: COLOR.ink, textAlign: 'center' as const },
  hint: { fontSize: 14.5, fontWeight: '500' as const, letterSpacing: -0.2, color: COLOR.hint, textAlign: 'center' as const },
  // 'Meet … / The first personal / software platform.' — 36 pt measured, 43.5 pt pitch.
  headline: 37,
  headlineLine: 43.5,
  headlineTracking: -0.5,
  button: { fontSize: 15, letterSpacing: -0.2 },
  // The next headline as the lens sees it (points), fitted to frame 018 with the gate's edge metric (.work/gest-e/opt/lens_fit.py).
  lens: 23.5,
} as const;

/**
 * Spacing scale (step 0, 20 Oct), derived from the source's measured gaps: 4 pt base; 14 = the buttons' gap,
 * 22 = the icon inset, 32 = the buttons' bottom margin, 34 = the gutter (all kept as measured). The upgrade's
 * additions use this scale: the touch-down lift (4), the second screen's line rise and glow drift (8), the light
 * button's top rim inset (24, where the pill's top edge turns flat).
 */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

export const LAYOUT = {
  // Dome at rest: radius share of the width; its top share of the height.
  domeRadius: 0.6,
  domeTop: 0.72,
  // Pull keyframes: progress → disc radius (share of W) and centre (share of H), from frames 1, 8, 13, 18, 24.
  pullT: [0, 0.4, 0.6, 0.75, 1],
  pullR: [0.6, 0.53, 0.43, 0.358, 0.29],
  pullY: [0.996, 0.884, 0.79, 0.721, 0.655],
  // The disc drifts a little left as it climbs (frame 018: centre at 0.477 W).
  pullX: [0.5, 0.5, 0.5, 0.5, 0.5],
  // The lens: fold radius (share of R) where the image turns over, how fast it turns back, the vertical and
  // horizontal reach (share of R), where the hidden headline sits relative to the lens centre, and the fringe.
  lens: { fold: 0.73, foldBack: 6.04, foldShape: 2.0, kY: 0.556, kX: 0.75, squeezeX: 1.091, tx: 0.017, ty: -0.113, spread: 0.5, fringeFrom: 0.35, fade: 3.19, mirrorFloor: 0.3 },
  // Inner rim highlight and the contact shadow just outside the glass (points).
  rimLight: 0.04,
  rimWidth: 7,
  shadowAt: 4,
  shadowWidth: 6,
  shadowAlpha: 0.07,
  // Below beadFrom pt of radius the glass turns frosted, fully at beadFrom − beadSpan.
  bead: { from: 90, span: 45, frost: 0.18 },
  domeFrost: 0.12,
  // The iridescent band's radial span (share of R), measured on frame 008.
  ringFrom: 0.5,
  ringTo: 0.9,
  hintY: 0.889,
  // Baselines (share of the height).
  introBaseline: 0.4842,
  headlineBaseline: 0.5099,
  // The bead lands at frame 028's place and size, then rises and tightens while the bubbles leave (frame 040).
  beadY: 0.397,
  beadRadius: 46,
  beadRestY: 0.3715,
  beadRestRadius: 39.5,
  // Back drag: the bead grows to this radius share of W as it is pulled down.
  backRadius: 0.37,
  // Buttons (frame 040, pose-fitted): light 52 pt and dark 54 pt pills, 14 pt apart, 32 pt above the edge, 34 pt gutters.
  buttonsBottom: 32,
  buttonHeight: 52,
  buttonHeightDark: 54,
  buttonGap: 14,
  gutter: 34,
  buttonIcon: 19,
  buttonIconInset: 22,
  fingerRadius: 22,
} as const;

/**
 * The bubble cloud, placed where the source's bubbles settle (frame 040, points on a 402 × 874 screen): x, y,
 * radius, atlas cell. Scaled to the screen; drawn in this order, so later bubbles sit in front.
 */
export const CLOUD: readonly (readonly [number, number, number, number])[] = [
  [375, 38, 25, 9], [120, 25, 18, 3], [84, 42, 35, 12], [250, 52, 27, 4], [203, 88, 35, 14],
  [145, 95, 22, 13], [94, 84, 33, 2], [350, 100, 25, 15], [292, 148, 20, 7], [322, 132, 32, 10],
  [67, 127, 13, 20], [140, 143, 28, 0], [261, 158, 25, 23], [200, 165, 26, 5], [207, 207, 19, 18],
  [147, 229, 17, 21], [172, 240, 29, 8], [195, 272, 22, 16], [239, 264, 11, 1], [247, 226, 8, 17],
  [279, 224, 8, 11], [243, 240, 4, 19], [225, 241, 4, 22], [270, 263, 2.5, 6], [60, 195, 1.8, 15], [80, 220, 1.8, 7],
];
export const CLOUD_FRAME = { width: 402, height: 874 } as const;

// Baked glass bubbles: 6 × 4 cells of 192 px.
export const ATLAS = { cell: 192, columns: 6, rows: 4 } as const;

export const COPY = {
  intro: ['A new era of', 'software is here.'],
  hint: 'Swipe up to enter',
  lens: 'Meet Wavo.',
  headline: 'Meet Wavo.',
  sub: ['The first personal', 'software platform.'],
  email: 'Continue with email',
  phone: 'Continue with phone',
  back: 'Start over',
  enter: 'Enter',
} as const;
