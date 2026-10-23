// Application-wide design tokens. Feature-specific palettes stay inside their feature.
export const colors = {
  background: '#09090d',
  backgroundRaised: '#111118',
  surface: '#17171f',
  surfaceSoft: 'rgba(255, 255, 255, 0.055)',
  border: 'rgba(255, 255, 255, 0.12)',
  borderStrong: 'rgba(255, 255, 255, 0.2)',
  text: '#f8f7fb',
  textMuted: '#a7a5b2',
  textSubtle: '#73717f',
  accent: '#d8ff67',
  accentInk: '#111506',
  white: '#ffffff',
  black: '#000000',
} as const;

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  huge: 48,
} as const;

export const radii = {
  sm: 10,
  md: 16,
  lg: 24,
  pill: 999,
} as const;

export const typeScale = {
  display: 40,
  title: 25,
  heading: 18,
  body: 15,
  label: 12,
  micro: 10,
} as const;

export const motion = {
  touchSpring: {
    duration: 400,
    dampingRatio: 0.8,
  },
  settleDelay: 260,
} as const;
