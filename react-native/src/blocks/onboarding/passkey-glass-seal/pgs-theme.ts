// Passkey Glass Seal — tokens and layout, measured from the reference (Pinterest pin
// 506303183093434291, the "Scanning…" screen of a Touch ID sign-in). The pin's screen (375 × 667 pt, a
// 20 pt status bar, no home indicator) was rectified onto a 402 × 874 device (top inset 62); D holds
// those device points and makeLayout maps them onto other devices. Type is the system font, light, as
// in the pin; the brand, Orvane, and its mark are invented.

export const COLOR = {
  top: '#D2668A',
  mid: '#8F5076',
  bottom: '#4D3B5D',
  card: '#93537A',
  cardBottom: '#784A6C',
  ink: '#FFFFFF',
  scan: '#FF8D5C',
  mute: 'rgba(255,255,255,0.56)',
} as const;

export const COPY = {
  brand: 'ORVANE',
  scanning: 'Scanning…',
  hold: 'Hold still…',
  read: 'Verified',
  verified: 'Unlocked',
  welcome: 'Welcome back',
  name: 'Mira Okafor',
  saved: 'Passkey saved on this iPhone',
  done: 'Continue',
} as const;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  mark: { x: 120, y: 170, size: 54 },
  brand: { x: 176, y: 168, size: 19, spacing: 10.5 },
  card: { x: 35, y: 276, w: 327, h: 324, r: 12 },
  /** the fingerprint's centre and the scan line, inside the card */
  print: { x: 165, y: 161, h: 120 },
  scan: { x0: 20, x1: 310, y: 187 },
  status: { y: 730, size: 20 },
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
