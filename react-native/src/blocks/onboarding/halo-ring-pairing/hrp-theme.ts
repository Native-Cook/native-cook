// Halo Ring Pairing — colours, type, the words of each step, the dashboard and the layout.

export const C = {
  page: '#06080A',
  ink: '#F2F4F3',
  mute: '#8A918E',
  card: '#121618',
  line: '#20262A',
  green: '#3BE38A',
  greenInk: '#04140B',
} as const;

export const FONT = {
  regular: 'HrpInter-Regular',
  medium: 'HrpInter-Medium',
  semibold: 'HrpInter-SemiBold',
} as const;

export const WORDS = {
  searchTitle: 'Looking for your ring',
  searchSub: 'Hold it close to your phone',
  foundTitle: 'Found Halo Ring',
  foundSub: 'Titanium',
  pair: 'Pair ring',
  sizeLabel: 'Ring size',
};

export const STATS = [
  { label: 'Readiness', value: '86', unit: '' },
  { label: 'Sleep', value: '7h 42m', unit: '' },
  { label: 'Resting heart', value: '58', unit: 'bpm' },
  { label: 'Steps', value: '4,210', unit: '' },
];

export const RING_S = 148; // the ring's unit size on the stage (pt)
export const CHIP_S = 13; // … in the header chip

export function makeLayout(w: number, h: number, top: number, bottom: number) {
  const stageY = top + Math.round((h - top - bottom) * 0.33);
  const titleTop = stageY + 168;
  const sizesTop = titleTop + 84;
  const buttonTop = h - Math.max(bottom, 16) - 8 - 56;
  const chip = { x: 20 + 22, y: top + 10 + 22 };
  return { w, h, top, stageY, titleTop, sizesTop, buttonTop, chip };
}
