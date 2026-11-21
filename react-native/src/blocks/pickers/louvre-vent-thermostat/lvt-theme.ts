// Louvre Vent Thermostat: tokens. Positions are points on the 402 × 874 pt canvas (iPhone 17 Pro) and scale with
// the screen width. Geometry follows the Pro addendum (frame 246 × 484, 22 blades of 20.8 pt pitch, 0.5 °C a blade).

export const C = {
  page0: '#0D0101',
  page1: '#160102',
  ink: '#FBF6F2',
  unit: '#5A4A48',
  mute: '#8A7C7A',
  faint: '#4A3C3A',
  near: '#B7A9A7',
  red: '#E0453A',
  readA: '#F5EDE4',
  readB: '#C9BDB9',
} as const;

/** Geometry (pt). */
export const G = {
  frame: { x: 78, y: 246, w: 246, h: 484, r: 26 },
  vent: { x: 86, y: 254, w: 230, h: 468, r: 18 },
  lineY: 488,
  pitch: 20.8,
  chord: 19,
  perC: 41.6, // pt per °C (two blades)
  line: { x0: 69, x1: 332, w: 2.5 },
  tri: { x: 62, w: 7, h: 8 },
  labelX: 337,
  num: { capTop: 123, size: 69, cap: 55.2, unitSize: 24 },
  modeY: 210,
  readY: 787,
} as const;

export const T_MIN = 16;
export const T_MAX = 32;
export const T_REST = 24;

/** Design-system springs (damping, stiffness, mass 1). */
export const SPRING = {
  glide: { d: 22.1, k: 157.9 },
  fling: { d: 26, k: 120 },
  snap: { d: 30.5, k: 322.3 },
} as const;

export const FONT = { mono: 'LVT-MartianMono' } as const;
