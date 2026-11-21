// Measured on the reference's left phone, on a 402 × 874 pt canvas.
export const C = {
  page: '#EBEBEA', pageTop: '#F1F1F0', ink: '#0B0B0B', secondary: '#A8A8A8',
  albumSecondary: '#C8C8C8', card: '#F9F9F8', control: '#F3F3F2',
  needle: '#EF3B2D', tick: '#CDCDCC', shadow: '#3A3A38',
} as const;
export const G = { w: 402, h: 874, panelX: 15, panelY: 374, panelW: 375, panelH: 374, slats: 9, distance: 170 } as const;
export const FONT = { bold: 'ARC-InterTight-Bold', extraBold: 'ARC-InterTight-ExtraBold' } as const;
export type Direction = 'right' | 'down' | 'left' | 'up';
export const headingFor = (direction: Direction) => {
  'worklet';
  return direction === 'right' ? 0 : direction === 'down' ? 90 : direction === 'left' ? 180 : 270;
};
export const DECK = [
  { album: ['Paper', 'Lanterns'], title: ['Slow', 'Meridian'], artist: 'Odessa Vale', panel: '#17B15A', muted: '#0E7C3D', direction: 'right' as Direction, rank: '4', chips: [{ label: 'A', color: '#DDF53F' }, { label: 'K', color: '#DDB9F6' }] },
  { album: ['Glass', 'Harbour'], title: ['Asked The', 'Weather'], artist: 'June Arlo', panel: '#C9C4BE', muted: '#736D68', direction: 'down' as Direction, rank: '5', chips: [{ label: 'A', color: '#DDF53F' }, { label: 'E', color: '#C9F6C1' }, { label: 'G', color: '#3FE8B2' }] },
  { album: ['Voltage', 'Garden'], title: ['Motel', 'Carousel'], artist: 'Nico Brava', panel: '#F54A35', muted: '#9B291D', direction: 'left' as Direction, rank: '2', chips: [{ label: 'H', color: '#FF9F9F' }, { label: 'G', color: '#3FE8B2' }] },
] as const;
