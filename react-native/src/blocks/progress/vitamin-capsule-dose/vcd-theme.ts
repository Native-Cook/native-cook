// Vitamin Capsule Dose — tokens and layout, measured from the reference (Pinterest pin
// 660692207860966897, "Meditask - Medication Reminder", its medication detail screen). The pin's screen
// (393 × 852 pt, a 54 pt status bar) was rectified onto a 402 × 874 device (top inset 62, bottom 34);
// D holds those device points and makeLayout maps them onto other devices. Type is Poppins, as in the
// pin; the medicines are invented.

export const COLOR = {
  top: '#000000',
  behind: '#B8B9C0',
  sheet: '#FFFFFF',
  ink: '#0B0C14',
  sub: '#5B6280',
  fill: '#EEF0F4',
  pink: '#EC5F7E',
  pinkText: '#EA4D70',
  pinkDeep: '#D9446A',
  ring: '#474F74',
  green: '#3EAA2D',
  pill: '#EE3A8F',
  clock: '#1D74DB',
  white: '#FFFFFF',
} as const;

export const FONT = {
  regular: 'VcdPoppins-Regular',
  medium: 'VcdPoppins-Medium',
  semibold: 'VcdPoppins-SemiBold',
  bold: 'VcdPoppins-Bold',
} as const;

/** the medicines the hero morphs between; shape: 0 capsule, 1 tablet, 2 drop */
export const MEDS = [
  { name: 'Lumivit', kind: 'Dietary supplement', dose: '20mg pills', daily: '60 mg', each: '3x Pills', shape: 0, times: ['10:00 AM', '3:00 PM', '6:00 PM'] },
  { name: 'Calmora', kind: 'Antacid suspension', dose: '200ml', daily: '30 ml', each: '3x Spoons', shape: 2, times: ['8:00 AM', '1:00 PM', '8:00 PM'] },
  { name: 'Mentab', kind: 'Pain relief', dose: '500mg tablets', daily: '1000 mg', each: '2x Pills', shape: 1, times: ['9:00 AM', '2:00 PM', '9:00 PM'] },
] as const;
export const DEFAULT_MED = 0;

/** the reference's positions, in device points (402 × 874, top inset 62) */
export const D = {
  behind: { x: 14, y: 72, w: 374, r: 26 },
  sheet: { y: 88, r: 26 },
  back: { x: 35, y: 124, textX: 51, size: 16 },
  hero: { x: 101, y: 221, unit: 76, shadowY: 294 },
  name: { x: 25, y: 338, size: 32 },
  sub: { x: 25, y: 374, size: 14.3 },
  edit: { x: 353, y: 349, r: 24 },
  chips: { y: 410, h: 66, r: 14, xs: [25, 210], w: 168, iconDx: 27, textDx: 51, valueDy: 23, labelDy: 47 },
  schedule: { y: 512, size: 20, logsRight: 350, iconX: 368 },
  rows: { x: 25, y: 540, w: 353, h: 132, r: 16, ys: [568, 606.5, 645], textX: 41, checkX: 351, checkR: 10 },
  export: { y: 689, h: 49, r: 14 },
  log: { y: 777, h: 56 },
  side: { x: 25, w: 353 },
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
