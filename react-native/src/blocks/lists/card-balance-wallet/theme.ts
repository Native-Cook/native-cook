// Measured from the pin (phone screen 610 px wide = 402 pt, 1.517 px/pt). Vertical values assume the
// safe top of 62 pt; `topOffset()` moves the layout for another safe area.

export const COLOR = {
  screen: '#030303',
  white: '#FFFFFF',
  ink: '#0B0B0C',
  tile: '#1F1F1F',
  muted: '#8B8B8F',
  accent: '#6E5BFF',
  dot: '#1F1F21',
  tabBand: '#1E1D1D',
  tabTrack: '#2B2B2D',
  tabCircle: '#8E8E93',
  tabIcon: '#2A2A2C',
  badge: '#FFFFFF',
  markCoral: '#E52A22',
  markAmber: '#F89C14',
} as const;

export const FAMILY = {
  regular: 'CBW-Poppins-Regular',
  medium: 'CBW-Poppins-Medium',
  semibold: 'CBW-Poppins-SemiBold',
  /** Android only: iOS draws the balance in its system face (see BALANCE). */
  display: 'CBW-InterDisplay-Bold',
} as const;

export const SCREEN = { safeTop: 62, inset: 15.8 } as const;

// Measured on the pin rectified to 402 x 874 pt (screen 614 x 1336 px in the pin, 1.528 px/pt).
export const HEADER = { y: 109.2, avatar: 47, nameX: 74.5, bellX: 373.5, bell: 24 } as const;

export const CARD = {
  top: 166.3, height: 239.7, radius: 24,
  padX: 17, labelY: 25.8, badge: { right: 15.9, top: 45.7, w: 33.8, h: 23.8, r: 5 },
} as const;

/**
 * The balance. The pin sets it in SF Pro Display Heavy (iOS's system face at weight 800, which
 * cannot be bundled), with spacing that no single tracking value gives: '$' sits 2.5 pt closer to
 * '1' and the figures after the comma 3 pt further right than the face's own advances. Each glyph is
 * therefore placed at the left edge of its ink in the pin, card-relative (pin x - 15.8 pt), and the
 * text box is moved left by that glyph's side bearing. Android uses Inter Display Bold, bundled, the
 * closest open face, with its own bearings. Proportional figures, as in the pin.
 */
export const BALANCE = {
  text: '$12,567',
  inkLeft: [18.5, 57.2, 88.9, 130.5, 150.5, 191.5, 233.9],
  baseline: 207.4, // card-relative
  ios: { size: 65.5, weight: '800', bearing: [3.53, 2.75, 2.49, 1.18, 3.67, 2.49, 1.83] },
  android: { size: 63.5, bearing: [2.16, 1.27, 2.29, 1.52, 1.78, 1.91, 1.4] },
} as const;

export const ACTIONS = { top: 419, height: 82.4, radius: 24, gap: 12.9, padX: 17 } as const;

export const DOTS = { x: 183.7, y: 528, active: 22, dot: 3.4, gap: 2.6, height: 3.6 } as const;

export const TILES = { top: 563.3, size: 88.4, gap: 5, radius: 20 } as const;

export const SPENT = { top: 685.3, radius: 24, titleY: 28.8, barY: 82.5, barHeight: 16.3, barGap: 3 } as const;

export const TABBAR = { band: 780, trackTop: 785.2, trackHeight: 62.6, trackX: 32.8, pillW: 117, pillH: 57, circle: 57.3, xs: [195.6, 266, 335.7] } as const;

export const FONT = {
  name: 12.5,
  label: 15.6,
  action: 16.4,
  spentTitle: 16.8,
  spentAmount: 21,
  tab: 15,
} as const;

export function topOffset(safeTop: number) {
  return safeTop > 0 ? safeTop - SCREEN.safeTop : -40;
}
