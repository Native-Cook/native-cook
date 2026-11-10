// Pin Drop Location — tokens and layout, measured from the reference (Pinterest pin
// 542613455106454309, a dark "Location" permission screen). The pin's screen (375 × 812 pt, a 44 pt
// status bar) was rectified onto a 402 × 874 device (top inset 62, bottom 34); D holds those device
// points and makeLayout maps them onto other devices. Type is the system font, as in the pin; the
// app, Wander, is invented.

export const COLOR = {
  page: '#0A071A',
  glow: '#17112F',
  skipBg: '#1E1B33',
  skip: '#B9B3E0',
  title: '#ECE9F7',
  body: '#DEDBEA',
  allowBg: '#16132A',
  allow: '#ABA6D6',
  pink: '#FF4A78',
  discA: '#2A2147',
  discB: '#4F4B9E',
} as const;

export const COPY = {
  title: 'LOCATION',
  body: 'Please enable location access so\nwe could provide you accurate results of\nnearest restaurants and bars',
  allow: 'Allow',
  done: 'Location on',
  skip: 'Skip',
} as const;

/** places that rise round the pin once allowed, in disc radii from the disc's centre */
export const PLACES = [
  { name: 'Corner Coffee', x: -0.58, y: 0.3, side: -1 },
  { name: 'Juniper Books', x: 0.6, y: -0.25, side: 1 },
  { name: 'Night Market', x: 0.35, y: 0.72, side: 1 },
] as const;

/** where the pin stands (home) and where the loop's drag takes it, in disc radii */
export const HOME = { x: 0, y: -0.05 } as const;
export const SPOT = { x: -0.32, y: 0.3 } as const;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  skip: { x: 312, y: 73, w: 69, h: 42 },
  disc: { x: 201, y: 333, r: 92 },
  pin: { size: 46 },
  title: { y: 482, size: 15, spacing: 2.2 },
  body: { y: 539, size: 17, line: 28, w: 380 },
  allow: { x: 20, y: 763, w: 362, h: 59, r: 14 },
} as const;
export const ZOOM = 1.6;

export type Layout = { w: number; h: number; sx: number; k: number; Y: (y: number) => number };

export function makeLayout(w: number, h: number, safeTop: number, safeBottom: number): Layout {
  // a phone without a notch (a 20 pt status bar) or without a home indicator still keeps the reference's
  // room at its edges, so the header clears the status bar and the bottom row stays off the edge
  const top = Math.max(safeTop, 44);
  const bottom = Math.max(safeBottom, 20);
  const sx = w / 402;
  const k = (h - top - bottom) / (874 - 62 - 34);
  return { w, h, sx, k, Y: (y: number) => (y < 62 ? (y * top) / 62 : top + (y - 62) * k) };
}
