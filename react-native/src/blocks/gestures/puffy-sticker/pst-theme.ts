// Puffy Sticker — tokens and layout (402 × 874 pt design, 4 pt grid).

export const COLOR = {
  page: '#0E0E12',
  surface: '#1B1B21',
  surfaceOn: '#2A2A33',
  line: '#2A2A33',
  ink: '#F4F4F6',
  mute: '#8C8C96',
  accent: '#FFD43B',
  accentInk: '#1A1600',
} as const;

export const FONT = { regular: 'PstInter-Regular', medium: 'PstInter-Medium', semibold: 'PstInter-SemiBold' } as const;

export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32, x3: 48 } as const;

export type Shape = 'heart' | 'star' | 'cloud' | 'smiley';
export const SHAPES: { id: Shape; label: string; colour: string }[] = [
  { id: 'heart', label: 'Heart', colour: '#FF3D6E' },
  { id: 'star', label: 'Star', colour: '#FFC21A' },
  { id: 'cloud', label: 'Cloud', colour: '#4FB3FF' },
  { id: 'smiley', label: 'Smiley', colour: '#FF8A1F' },
];

export type Layout = {
  w: number;
  h: number;
  barTop: number;
  stageX: number;
  stageY: number;
  sticker: number; // sticker radius, pt
  hintTop: number;
  switchTop: number;
  trayTop: number;
  thumb: number;
};

export function makeLayout(w: number, h: number, top: number, bottom: number): Layout {
  const sticker = Math.min(w * 0.36, 150);
  const thumb = 64;
  const trayTop = h - bottom - 24 - thumb;
  const switchTop = trayTop - 24 - 40; // the controls group: switch, 24 pt, tray
  const barTop = top + 8;
  // the sticker sits in the middle of the space between the bar and the controls, a little high so
  // the hint below it belongs to it
  const stageY = (barTop + 44 + switchTop) / 2 - 18;
  return {
    w,
    h,
    barTop,
    stageX: w / 2,
    stageY,
    sticker,
    hintTop: stageY + sticker * 1.12 + 20,
    switchTop,
    trayTop,
    thumb,
  };
}
