// Measured from the pin (a 402 × 874 pt screen; pin pixels / 2.368). Vertical values assume the
// iPhone safe top of 62 pt; `topOffset()` shifts the layout for another safe area.

export const COLOR = {
  screen: '#F0F0F0',
  ink: '#1D1D1F',
  inkSoft: '#6B6B6F',
  muted: '#8E8E93',
  white: '#FFFFFF',
  button: '#FBFBFB',
  tile: '#E4E4E4',
  divider: '#C4C4C4',
  accent: '#5A46E0',
  red: '#CF2E2E',
  green: '#2E8B3A',
  fabTop: '#7B80FA',
  fabBottom: '#5C4BE3',
} as const;

export const SCREEN = { width: 402, safeTop: 62 } as const;


export const HEADER = { y: 102, button: 48, inset: 20, titleY: 93, subtitleY: 113 } as const;

/** The wallet, all values relative to its own top-left corner (screen x 38, y 201). */
export const WALLET = {
  left: 37.5, top: 201, width: 332, height: 195,
  flapRadius: 26, rimTop: '#B994E6', flapTop: '#7C5AB4', flapMid: '#5A3A90', flapBottom: '#2A1462',
  pocketSide: 30, pocketDip: 45, pocketRadius: 34, cornerRadius: 12,
  flapShort: 24, // the back panel ends inside the pocket, so none of it shows round the pocket's bottom corners
  flapStops: [0, 0.1, 0.5, 1],
  flapColors: ['#6E4FA9', '#61429C', '#4E3289', '#3B257A'],
  pocketStops: [0, 0.03, 0.1, 0.17, 0.25, 0.33, 0.4, 0.48, 0.55, 0.63, 0.7, 0.78, 0.86, 1],
  pocketColors: ['#563788', '#563788', '#B48DDC', '#AD85D6', '#9F79CB', '#8F6ABE', '#7E59AC', '#714EA0', '#684596', '#61408F',
    '#583A88', '#4C3180', '#3F2878', '#3C2782'],
  pocketGlow: { cx: 0.35, cy: 0.66, r: 0.42, color: 'rgba(255, 255, 255, 0.13)' },
  pocketLeft: '#A37DCF', pocketMid: '#8361B4', pocketRight: '#33207A', pocketFoot: '#24104F', pocketInset: 2, pocketBottom: 1,
  stitchInset: 6.5, stitchRise: 6, stitch: 'rgba(222, 200, 252, 0.55)',
  shadowY: 202,
} as const;

export const CARDS = {
  back: { x: 38, y: -43, width: 255, height: 150, radius: 14 },
  front: { x: 21, y: -32, width: 290, height: 180, radius: 20 },
  padX: 18, nameY: -2, numberY: 18, brandY: 2, validY: 22,
} as const;

export const POCKET_UI = {
  labelY: 80, balanceY: 105, pillY: 130.5, pillX: 24, pillW: 142, pillH: 31,
  iconY: 145.5, icon: 32, iconXs: [248, 292],
} as const;

export const TOPUP = { titleY: 431, rowY: 487, avatar: 53, addX: 47, dividerX: 92, xs: [139, 211, 283, 355], labelY: 527 } as const;

export const LATEST = { titleY: 580, top: 615, width: 103, height: 142, radius: 18, xs: [25, 149, 273], avatar: 48 } as const;

export const TABBAR = { top: 779, iconY: 803, labelY: 827, xs: [38, 111, 279, 358], fab: 72, fabY: 795 } as const;

export function topOffset(safeTop: number) {
  return safeTop > 0 ? safeTop - SCREEN.safeTop : -40;
}
