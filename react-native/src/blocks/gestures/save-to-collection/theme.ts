/**
 * Atlas, a quiet photo browser at night: a black page, a masonry of places in two columns, and the chrome as glass
 * floating over the photos (the tab bar, the saved button, the collection sheet). The photos carry all the colour;
 * the interface is white ink and greys. Inter only. Sizes on a 4 pt grid, 16 pt edges, 8 pt between tiles.
 */
import type { TextStyle } from 'react-native';

export const FONT = {
  regular: 'STC-Inter-Regular',
  medium: 'STC-Inter-Medium',
  semibold: 'STC-Inter-SemiBold',
} as const;

export const COLOR = {
  page: '#0A0A0B',
  pageClear: 'rgba(10,10,11,0)',
  ink: '#F4F4F2',
  mute: 'rgba(244,244,242,0.56)',
  faint: 'rgba(244,244,242,0.32)',
  tile: '#18181A',
  hairline: 'rgba(255,255,255,0.12)',
  rim: 'rgba(255,255,255,0.22)',
  glassFill: 'rgba(30,30,32,0.42)',
  sheetFill: 'rgba(22,22,24,0.72)',
  chipOn: '#F4F4F2',
  chipOnInk: '#0A0A0B',
  badge: '#F4F4F2',
  badgeInk: '#0A0A0B',
  scrim: 'rgba(0,0,0,0.5)',
  ghost: 'rgba(255,255,255,0.10)',
  finger: 'rgba(255,255,255,0.16)',
  fingerEdge: 'rgba(255,255,255,0.4)',
} as const;

const TNUM: TextStyle['fontVariant'] = ['tabular-nums'];

export const TYPE = {
  title: { fontFamily: FONT.semibold, fontSize: 28, lineHeight: 34, letterSpacing: -0.6 },
  chip: { fontFamily: FONT.medium, fontSize: 14, lineHeight: 18, letterSpacing: -0.1 },
  tab: { fontFamily: FONT.medium, fontSize: 10, lineHeight: 12, letterSpacing: 0.1 },
  badge: { fontFamily: FONT.semibold, fontSize: 11, lineHeight: 14, fontVariant: TNUM },
  toast: { fontFamily: FONT.medium, fontSize: 14, lineHeight: 18 },
  toastAction: { fontFamily: FONT.semibold, fontSize: 14, lineHeight: 18 },
  sheetTitle: { fontFamily: FONT.semibold, fontSize: 22, lineHeight: 28, letterSpacing: -0.4 },
  sheetMeta: { fontFamily: FONT.regular, fontSize: 13, lineHeight: 18, fontVariant: TNUM },
  caption: { fontFamily: FONT.medium, fontSize: 12, lineHeight: 16 },
  new: { fontFamily: FONT.semibold, fontSize: 11, lineHeight: 14, letterSpacing: 0.4 },
} as const;

export const LAYOUT = {
  edge: 16,
  gap: 8,
  radius: 14,
  /** Header: the title row and the chips under it, below the safe area. */
  titleH: 44,
  chipsTop: 52,
  chipH: 32,
  gridTop: 100,
  /** The floating chrome: a tab capsule on the left, the saved button on the right, 8 pt above the home indicator. */
  barH: 60,
  barGap: 8,
  saveSize: 60,
  /** The sheet covers the screen from this far below the safe area. */
  sheetTop: 72,
  sheetRadius: 32,
  finger: 44,
} as const;

/** Grid tiles in two columns, as heights (pt at 402 wide; scaled with the column width). */
export const COLUMN_HEIGHTS = [[236, 196, 252], [196, 268, 224]] as const;

export type Frame = { x: number; y: number; w: number; h: number };

export function makeLayout(width: number, height: number, top: number, bottom: number) {
  const colW = (width - LAYOUT.edge * 2 - LAYOUT.gap) / 2;
  const k = colW / 181;
  const gridTop = top + LAYOUT.gridTop;
  const tiles: Frame[] = [];
  for (let c = 0; c < 2; c++) {
    let y = gridTop;
    for (const h of COLUMN_HEIGHTS[c]) {
      tiles.push({ x: LAYOUT.edge + c * (colW + LAYOUT.gap), y, w: colW, h: Math.round(h * k) });
      y += Math.round(h * k) + LAYOUT.gap;
    }
  }
  const barY = height - Math.max(bottom, LAYOUT.edge) - LAYOUT.barGap - LAYOUT.barH;
  const save = { x: width - LAYOUT.edge - LAYOUT.saveSize / 2, y: barY + LAYOUT.barH / 2 };
  const sheetY = top + LAYOUT.sheetTop;
  return { width, height, top, bottom, colW, gridTop, tiles, barY, save, sheetY };
}
export type Layout = ReturnType<typeof makeLayout>;
