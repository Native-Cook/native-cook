export const G = {
  width: 402,
  height: 874,
  px: 64,
  py: 236,
  pw: 274,
  ph: 274,
  ay: 510,
  fy: 522,
  labelY: 204,
  tracking: 2.6,
  switchY: 792,
} as const;
export const C = {
  green: [40, 224, 122],
  amber: [230, 198, 90],
  red: [255, 45, 61],
  flash: [182, 255, 228],
} as const;
export const T = {
  period: 12,
  rewind: 3.6,
  draw: 4.15,
  crash: 6.35,
  mRest: 6.9,
  collapse: 7.05,
  home: 7.57,
  gather: 7.99,
  climb: 8.07,
  breakout: 9.23,
  payoff: 9.41,
  dRest: 10.45,
  resume: 3.5,
  maxDt: 0.05,
} as const;
export const RISERS = [0.2, 0.405, 0.558, 0.688, 0.97];
export const EXTREMA = [0.28, 0.455, 0.595, 0.682, 0.75];
export const TREADS = [0.13, 0.3, 0.482, 0.624, 0.8];
export const SHARDS = [
  [332, 196, 64, 20, 1],
  [352, 266, 44, -35, 0.9],
  [300, 168, 30, 70, 0.8],
  [372, 150, 24, 10, 0.85],
  [318, 228, 18, -60, 0.8],
  [120, 214, 14, 30, 0.7],
];
export const SPRING = { damping: 22, stiffness: 240 };
export const THUMB_SPRING = { damping: 20, stiffness: 260 };
