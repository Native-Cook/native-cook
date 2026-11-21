// Island Print Camera: tokens. Positions are points on the 402 × 874 pt canvas (iPhone 17 Pro) and scale with the
// screen width; the island is the hardware cut-out and does not scale.

export const C = {
  shell: '#E82822',
  shellLit: '#F4544A',
  shellShade: '#B9221E',
  shadowTint: '#7A0F0C',
  panel: '#2C1C26',
  ink: '#D9CFD5',
  ink2: '#8A7A84',
  icon: '#6E5F68',
  bezel: '#0A0607',
  orange: '#F57602',
  ledOff: '#2F3815',
  ledRim: '#4B4F1B',
  ledOn: '#A6D11F',
  ledBusy: '#E0301E',
  ledFlash: '#F5A623',
  flipRing: '#B81F1C',
  paper: '#FCFBFA',
  paperEdge: '#E9E6E2',
  photoBase: '#E0E0E2',
  wordmark: '#FFF9F6',
} as const;

/** Header geometry (pt). */
export const G = {
  islandW: 126,
  islandH: 37,
  islandTop: 11,
  islandR: 18.5,
  bodyW: 260,
  bodyH: 260,
  bodyR: 64,
  lensInset: 35,
  slotW: 230,
  panelX: 16,
  panelTop: 165,
  panelW: 370,
  panelH: 660,
  panelR: 6,
  stripTop: 825,
  pull: 346,
  rowY: 362,
  led: { x: 73, d: 48 },
  shutter: { x: 201, d: 82 },
  flip: { x: 329, d: 50 },
  wordmarkW: 79,
  wordmarkH: 45,
  wordmarkY: 113,
  wordmarkOpenY: 484,
  wordmarkOpenScale: 0.62,
  print: { x: 98, w: 206, h: 247, landTop: 152, photoX: 13, photoY: 22, photo: 180 },
  headerH: 430,
} as const;

/** Design-system springs (damping, stiffness, mass 1). */
export const SPRING = {
  glide: { d: 22.1, k: 157.9 },
  pop: { d: 19, k: 195 },
  snap: { d: 30.5, k: 322.3 },
  weighty: { d: 18, k: 120 },
} as const;

export const FONT = { regular: 'IPC-Inter-Regular' } as const;
