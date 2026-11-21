// Race Globe Calendar: design tokens (creative spec §2, §6 and the Pro addendum). All geometry is in points on the
// 402 × 874 design canvas; the component scales it by S = width / 402.

/** Colours (sRGB hex). The Earth's own colours are in the textures. */
export const C = {
  space: '#020308',
  glass: '#10141A', // event card, 58 %
  glassLow: '#0B0E13', // the card's darker lower half
  callout: '#14161A', // 72 %
  button: '#1B1E24', // 55 %
  pin: '#4B4FE0',
  pinRim: '#7F83FF',
  dot: '#6E72F2',
  sheenUp: '#3E7BFF', // upcoming round
  sheenNight: '#FFB25A', // night race
  ink: '#FFFFFF',
  chipLine: '#2A2D33',
} as const;

/** Layout. The card row is anchored to the bottom edge, the globe and the button to the top. */
export const G = {
  globe: { cx: 201, cy: 372, r: 300 },
  /** the camera always brings the selected round to this point (pt) at rest zoom */
  pinY: 330,
  /** camera distance in globe radii (perspective, fov about 30°) */
  camDist: 4,
  card: { x: 24, y: 650, w: 354, h: 128, r: 28, pitch: 366 },
  chip: { dx: 17, dy: 17, h: 26, r: 13 },
  titleBase: 82, // from the card's top
  dateBase: 108,
  shuffle: { cx: 41, cy: 90, r: 22 },
  pin: { r: 15, selR: 17, tail: 6, dot: 3 },
  callout: { h: 54, r: 12, gap: 9, pad: 12, chip: 30 },
} as const;

/** Type sizes (pt): addendum (d) "title 24, date 14, chip 13, callout 15 / 13". */
export const TYPE = { title: 24, date: 14, chip: 13, name: 15, place: 13, pin: 13 } as const;

/** Design-system springs (damping, stiffness, mass 1). */
export const SPRING = {
  glide: { d: 22.1, k: 157.9 },
  pop: { d: 19, k: 195 },
  snap: { d: 30.5, k: 322.3 },
  weighty: { d: 15.1, k: 93.4 },
  drift: { d: 14, k: 48.7 },
} as const;

export const hex3 = (h: string) => [parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255];
