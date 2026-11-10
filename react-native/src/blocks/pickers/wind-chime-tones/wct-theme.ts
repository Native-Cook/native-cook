// Wind Chime Tones — tokens and layout, measured from the reference (Pinterest pin
// 190769734203774879, a speaker's page in a smart-home app). The pin's screen (375 × 812 pt, a 44 pt
// status bar, a 34 pt home indicator) was rectified onto a 402 × 874 device (top inset 62, bottom 34);
// D holds those device points and makeLayout maps them onto other devices. The speaker, its maker,
// the track and the copy are invented; type is Inter, close to the pin's sans.

export const COLOR = {
  page: '#2A2A2B',
  card: '#3A3A3B',
  ink: '#FFFFFF',
  mute: 'rgba(255,255,255,0.32)',
  track: 'rgba(255,255,255,0.18)',
  power: '#FFFFFF',
  red: '#F0453A',
} as const;

export const FONT = { regular: 'WctInter-Regular', medium: 'WctInter-Medium', semibold: 'WctInter-SemiBold' } as const;

export const COPY = {
  maker: 'Halden Audio',
  kind: 'Speaker',
  playing: 'Now playing',
  track: 'Harbour Chimes – Tones at Dusk',
  tools: ['AirPlay', 'EQ', 'Add to pair'],
} as const;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  back: { x: 45, y: 103 },
  more: { x: 356, y: 104 },
  title: { y: 90, sub: 117, size: 14.5 },
  /** the speaker's disc: centre and radius */
  speaker: { x: 200, y: 290, r: 101 },
  power: { x: 317, y: 292, r: 32 },
  tools: { y: 510, h: 90, xs: [37, 152, 270], w: 94, r: 16, icon: 546, label: 577 },
  player: { x: 37, y: 625, w: 327, h: 195, r: 20 },
  now: { x: 128, y: 664, size: 14.5, sub: 694 },
  bar: { x0: 63, x1: 338, y: 724 },
  controls: { y: 776, xs: [108, 203, 290] },
  icon: { x: 83, y: 676 },
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
