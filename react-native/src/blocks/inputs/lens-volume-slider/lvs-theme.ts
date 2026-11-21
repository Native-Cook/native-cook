// Lens Volume Slider: tokens. Positions are points on the 402 × 874 pt canvas (iPhone 17 Pro) and scale with the
// screen width; the control keeps its distance from the bottom edge on taller or shorter screens.
//
// Geometry follows the Pro addendum (`.work/upgrade/addendum-lens-volume-slider.md`), which overrules the spec's
// replica row: no round neighbour, a 354 × 104 track whose centre stays at y 745, an 88 pt drop, a 30 pt glyph and a
// 28 pt numeral, 1.5 pt rims.

export const C = {
  smoke: '#3C3431', // the track's warm smoked glass
  fill: '#3E3E3F', // the drop's neutral clear glass
  fillLeft: '#4F5357',
  trackRimTop: '#9EABB1',
  fillRim: '#C9D2D6',
  fringeBlue: '#597691',
  fringeWarm: '#B08A6A',
  ink: '#DFDFDF',
} as const;

/** Geometry (pt) on the 402 × 874 canvas. */
export const G = {
  track: { x: 24, w: 354, h: 104, r: 52 },
  /** how far the track's top sits above the bottom edge: y 693 on an 874 pt screen */
  rowFromBottom: 181,
  inset: 8,
  glyph: { x: 48, size: 30 },
  num: { x: 88, size: 28, track: -0.013 },
  /** body of the drop: a cylinder in y that bends the photo by at most this much at the rim (pt) */
  bodyBend: 8,
  /** the end of the drop is a glass ball (the cap's whole 88 pt disc): inverted ×1.8 at its centre (×2.1 lifted)
   *  easing to ×0.85 at its rim; R/B dispersion 0.6–1.25 pt each way (fading out over the inner 40 %); a 2.75 pt blue
   *  fringe on its rim; a dark meniscus ring where it meets the body (shader constants in lvs-shaders.ts) */
  lens: { mag: 1.8, magLifted: 2.1, magRim: 0.85, chroma: 1.25, fringe: 2.75 },
  /** the drop's end leads its value by speed × 0.06 s, at most 16 pt */
  leadTime: 0.06,
  maxStretch: 16,
  /** finger down lifts the drop to 96 pt, past the inset */
  liftH: 96,
  dimple: 28, // dimple radius under the finger (pt)
  caustic: { w: 26, h: 56, ahead: 8 },
  trackStretchMax: 10,
  /** what the mute marble sees, inverted: the pale wall above the speaker at its centre (behind the muted glyph), the
   *  speaker's top and bright rim turned over into its upper half (centre, radius pt) */
  room: { x: 200, y: 70, r: 175 },
  /** the photo's base scale, drift amplitude (pt), zoom at 100 and a fixed sideways shift (plate pt). The shift brings
   *  the plate's edges into the lens's travel along the capsule: the bright seam line at x ≈ 224 (v ≈ 59), the mesh /
   *  shadow silhouette at ≈ 262 (v ≈ 75) and the shadow / wall edge at ≈ 346–351 (seen in the ball from v ≈ 85). The
   *  photo tiles mirrored, and 26 pt keeps the mirrored strip on the right inside plain wall at every drift. */
  plate: { scale: 1.05, driftX: 8, driftY: 5, zoom: 0.04, shiftX: -26 },
} as const;

/** Inner fill height and radius at rest. */
export const FILL_H = G.track.h - 2 * G.inset; // 88
export const FILL_MIN = FILL_H; // a circle at 0
export const FILL_MAX = G.track.w - 2 * G.inset; // 338
export const PT_PER_UNIT = (FILL_MAX - FILL_MIN) / 100; // 2.5
/** Fill width (pt) for a value 0..100. */
export const fillW = (v: number) => {
  'worklet';
  return FILL_MIN + PT_PER_UNIT * v;
};

/** Design-system springs (damping, stiffness, mass 1). `release` is softer than `pop`: two visible swings. */
export const SPRING = {
  glide: { d: 22.1, k: 157.9 },
  pop: { d: 19, k: 195 },
  snap: { d: 30.5, k: 322.3 },
  weighty: { d: 18, k: 120 },
  release: { d: 5.5, k: 170 },
  zoom: { d: 26, k: 170 }, // ~400 ms, no overshoot: the photo leans in with the volume
  sink: { d: 12, k: 220 }, // a lifted drop settles back with one dip below rest
} as const;

export const FONT = { display: 'LVS-InterDisplay-SemiBold' } as const;
