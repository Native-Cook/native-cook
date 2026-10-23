/**
 * A cassette deck that is the whole screen: a brushed aluminium faceplate lit from above, a recessed well behind a
 * smoked acrylic door with a mixtape in it (charcoal shell, a paper label written by hand, brown tape winding from reel
 * to reel), a mechanical counter and two level meters, and a row of five piano keys. Inter for the engraving, a
 * handwriting face for the label. Sizes are points on a 402 × 778 stage (the screen inside its safe area).
 */
export const FONT = {
  regular: 'CD-Inter-Regular',
  medium: 'CD-Inter-Medium',
  semibold: 'CD-Inter-SemiBold',
  hand: 'CD-CoveredByYourGrace',
} as const;

export const COLOR = {
  plate: '#CFD2D5',
  plateLight: '#E6E8EA',
  plateDeep: '#A9ADB2',
  engrave: '#4A4E54',
  engraveMute: '#7B8086',
  well: '#121314',
  shell: '#2A2B2E',
  shellLight: '#3A3B3F',
  label: '#F1E8D4',
  labelLine: 'rgba(160,120,90,0.35)',
  labelBand: '#D9573B',
  pen: '#1F2A63',
  tape: '#3B2418',
  tapeLight: '#6B4430',
  hub: '#F4F2EE',
  counter: '#0E0F10',
  digit: '#F2F0EA',
  led: '#FF8A2A',
  ledOff: '#3A2A20',
  meterOn: '#7BE38E',
  meterHot: '#FFB34A',
  meterPeak: '#FF5A3C',
  meterOff: '#23262A',
  keyTop: '#E9EBED',
  keyFront: '#9EA3A9',
  keyDeep: '#6E737A',
  playMark: '#E2462F',
  finger: 'rgba(30,32,36,0.16)',
  fingerEdge: 'rgba(30,32,36,0.4)',
} as const;

export const STAGE = { w: 402, h: 778 };

export const LAYOUT = {
  edge: 16,
  brandBase: 46,
  modelBase: 66,
  power: { x: 370, y: 40 },
  well: { x: 16, y: 98, w: 370, h: 300, r: 18 },
  cassette: { x: 36, y: 132, w: 330, h: 226, r: 12 },
  label: { x: 52, y: 146, w: 298, h: 128, r: 6 },
  window: { x: 116, y: 210, w: 170, h: 52, r: 26 },
  reels: [{ x: 148, y: 236 }, { x: 254, y: 236 }],
  hub: 14,
  pack: { min: 17, max: 44 },
  readout: { y: 436, h: 64 },
  counter: { x: 16, w: 146 },
  meters: { x: 178, w: 208 },
  keys: { y: 552, h: 140, gap: 8, top: 22 },
  hintBase: 748,
  finger: 44,
} as const;

export const TYPE = {
  brand: { font: 'semibold', size: 22 },
  model: { font: 'medium', size: 10 },
  small: { font: 'medium', size: 9 },
  digit: { font: 'semibold', size: 26 },
  hand: { font: 'hand', size: 27 },
  handSmall: { font: 'hand', size: 18 },
  hint: { font: 'medium', size: 14 },
} as const;
export type TypeRole = keyof typeof TYPE;

/** The keys, left to right. */
export const KEYS = ['rew', 'play', 'ff', 'stop', 'pause'] as const;
export const KEY = { rew: 0, play: 1, ff: 2, stop: 3, pause: 4 } as const;

export const COPY = {
  brand: 'VELOR',
  model: 'TC-7  STEREO CASSETTE DECK',
  side: 'A',
  title: 'Glass Hours',
  sub: 'for Jun, summer mix',
  counter: 'TAPE COUNTER',
  memo: 'MEMO',
  left: 'L',
  right: 'R',
  hint: ['Press play', 'Playing side A', 'Winding', 'Rewinding to 000', 'Paused'],
  keys: ['Rewind', 'Play', 'Fast forward', 'Stop', 'Pause'],
};

/** Where the stage sits on a screen `width` × `height` with safe-area insets. */
export function makeLayout(width: number, height: number, top: number, bottom: number) {
  const room = height - top - Math.max(bottom, 16);
  const s = Math.min(width / STAGE.w, room / STAGE.h);
  const ox = (width - STAGE.w * s) / 2;
  const oy = top + (room - STAGE.h * s) / 2;
  return { width, height, top, bottom, s, ox, oy };
}
export type Layout = ReturnType<typeof makeLayout>;
