// Icon Era Scrubber — type, the eras' words and the layout.

export const C = {
  ink: '#F3F4F8',
  mute: '#8C8FA0',
  faint: '#3A3C4A',
  tick: '#5A5D6E',
  accent: '#FFFFFF',
} as const;

export const FONT = {
  regular: 'IesInter-Regular',
  medium: 'IesInter-Medium',
  semibold: 'IesInter-SemiBold',
} as const;

export const ERAS = [
  { name: 'Glossy', line: 'Gloss, bevels and airmail paper' },
  { name: 'Flat', line: 'Thin lines and clean colour' },
  { name: 'Soft', line: 'Rounded light and gentle depth' },
  { name: 'Glass', line: 'Clear glass that bends the light' },
];

export const APP = { name: 'Postbox', sub: 'App icon history' };

export const STEP = 28; // pt of ruler per year
export const ICON_HALF = 110;

export function makeLayout(w: number, h: number, top: number, bottom: number) {
  const titleTop = top + 12;
  const iconY = Math.round(top + (h - top - bottom) * 0.36);
  const yearTop = iconY + ICON_HALF + 44;
  const rulerTop = h - Math.max(bottom, 16) - 96;
  return { w, h, titleTop, iconX: w / 2, iconY, yearTop, rulerTop };
}
