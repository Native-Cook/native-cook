// Glass Aperture Camera — tokens and layout, measured from the reference (Pinterest pin
// 389420699016707976, the street screen of a social camera). The pin's screen (375 × 667 pt, a 20 pt
// status bar, no home indicator) was rectified onto a 402 × 874 device (top inset 62); D holds those
// device points and makeLayout maps them onto other devices. Type is the system font, as in the pin;
// the app, its people and the copy are invented.

export const COLOR = {
  page: '#000000',
  footer: '#161616',
  ink: '#FFFFFF',
  mute: 'rgba(255,255,255,0.62)',
  star: '#1FD3F2',
  well: 'rgba(16,16,16,0.72)',
  shutter: '#F4F4F2',
} as const;

export const COPY = {
  to: 'Friends @mira',
  share: 'Sharable with other apps',
  sent: 'Sent to 4 friends',
} as const;

/** the f-numbers, widest first; the camera rests at REST */
export const F_MIN = 1.4;
export const F_MAX = 16;
export const F_REST = 2.8;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  view: { bottom: 770 },
  tools: { y: 607, star: 48, dot: 148, grid: 250, pen: 349 },
  shutter: { y: 710, x: 199, r: 26 },
  flash: { x: 82, r: 20 },
  flip: { x: 316, r: 20 },
  footer: { top: 770, to: 795, share: 820 },
  /** the iris: its centre and outer radius */
  iris: { x: 201, y: 360, r: 128 },
} as const;

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
