/** Colours, type, layout and the habits. Positions are points on a 402 × 874 screen, measured on the source. */
import type { ComponentProps } from 'react';
import type { Ionicons } from '@expo/vector-icons';

type IconName = ComponentProps<typeof Ionicons>['name'];

export const COLOR = {
  bg: '#0E1013',
  ink: '#F3F4F2',
  inkSoft: 'rgba(243,244,242,0.6)',
  inkFaint: 'rgba(243,244,242,0.36)',
  hairline: 'rgba(255,255,255,0.1)',
  selected: '#F3F4F2',
  selectedInk: '#111316',
  finger: 'rgba(255,255,255,0.14)',
  fingerRing: 'rgba(255,255,255,0.42)',
} as const;

/**
 * Calm, mineral colours instead of neon: each is what a bubble's glass is tinted with (linear rgb) and the liquid
 * that fills it as the habit is held, from its surface to its bottom, and the ink drawn on the filled glass.
 */
export const HUE = {
  purple: { tint: [0.66, 0.73, 0.86], top: '#B9C6DF', bottom: '#8193B5', ink: '#1C2333', icon: '#E4EAF5' },
  green: { tint: [0.62, 0.78, 0.66], top: '#B7D3BD', bottom: '#7FA38A', ink: '#17261C', icon: '#E3F0E6' },
  yellow: { tint: [0.88, 0.79, 0.6], top: '#E8D5AE', bottom: '#BFA372', ink: '#2B2213', icon: '#F6EEDD' },
  orange: { tint: [0.86, 0.66, 0.54], top: '#E3BBA4', bottom: '#B98468', ink: '#2D1C13', icon: '#F6E6DC' },
  red: { tint: [0.82, 0.62, 0.62], top: '#DDB2B0', bottom: '#AE7C7B', ink: '#2C1717', icon: '#F5E3E2' },
  glass: { tint: [0.8, 0.82, 0.85], top: '#D3D7DD', bottom: '#9CA3AD', ink: '#1C1F24', icon: '#EEF0F3' },
} as const;
export type Hue = keyof typeof HUE;

export type Habit = { id: string; name: string; icon: IconName; hue: Hue; r: number; x: number; y: number };

/** The cluster's centre on the source screen; habit x/y are measured from it. */
export const CLUSTER = { x: 190, y: 380 } as const;

const at = (x: number, y: number) => ({ x: x - CLUSTER.x, y: y - CLUSTER.y });

export const SOCIAL: Habit[] = [
  { id: 'wave', name: 'Say hi to a neighbour', icon: 'hand-left-outline', hue: 'purple', r: 78, ...at(150, 225) },
  { id: 'star', name: 'Thank someone', icon: 'star-outline', hue: 'purple', r: 78, ...at(309, 329) },
  { id: 'gift', name: 'Send a small gift', icon: 'basket-outline', hue: 'red', r: 28, ...at(241, 157) },
  { id: 'ball', name: 'Team sport', icon: 'basketball-outline', hue: 'orange', r: 33, ...at(268, 218) },
  { id: 'friend', name: 'Call a friend', icon: 'person-outline', hue: 'orange', r: 34, ...at(50, 280) },
  { id: 'art', name: 'Draw together', icon: 'color-palette-outline', hue: 'green', r: 53, ...at(83, 365) },
  { id: 'host', name: 'Host friends', icon: 'bed-outline', hue: 'yellow', r: 43, ...at(183, 347) },
  { id: 'magic', name: 'Plan a surprise', icon: 'sparkles-outline', hue: 'yellow', r: 43, ...at(86, 467) },
  { id: 'home', name: 'Family dinner', icon: 'home-outline', hue: 'green', r: 50, ...at(300, 465) },
  { id: 'tea', name: 'Tea with mum', icon: 'cafe-outline', hue: 'red', r: 28, ...at(60, 536) },
  { id: 'heart', name: 'Date night', icon: 'heart-outline', hue: 'green', r: 53, ...at(143, 552) },
  { id: 'wallet', name: 'Split the bill', icon: 'wallet-outline', hue: 'orange', r: 33, ...at(234, 535) },
  { id: 'ride', name: 'Ride to work', icon: 'bicycle-outline', hue: 'yellow', r: 43, ...at(311, 568) },
  { id: 'note', name: 'Write a note', icon: 'mail-outline', hue: 'glass', r: 25, ...at(255, 384) },
];

// The Personal set: its own cluster, built like the social one (two large purple anchors, greens and yellows in the
// middle sizes, small orange and red accents, one clear glass bubble), packed with the same 3–4 pt gaps above the
// larger Personal date. Positions come from a relaxation run of the same packing; every pair keeps >= 3 pt apart.
export const PERSONAL: Habit[] = [
  { id: 'read', name: 'Read 20 pages', icon: 'book-outline', hue: 'purple', r: 70, ...at(176, 250) },
  { id: 'sleep', name: 'In bed by 23:00', icon: 'moon-outline', hue: 'purple', r: 60, ...at(289, 322) },
  { id: 'meditate', name: 'Meditate', icon: 'leaf-outline', hue: 'green', r: 53, ...at(86, 340) },
  { id: 'run', name: 'Morning run', icon: 'walk-outline', hue: 'green', r: 50, ...at(187, 373) },
  { id: 'cook', name: 'Cook at home', icon: 'restaurant-outline', hue: 'green', r: 46, ...at(270, 431) },
  { id: 'spanish', name: 'Spanish lesson', icon: 'language-outline', hue: 'yellow', r: 43, ...at(288, 215) },
  { id: 'water', name: 'Two litres of water', icon: 'water-outline', hue: 'yellow', r: 40, ...at(114, 432) },
  { id: 'guitar', name: 'Practise guitar', icon: 'musical-notes-outline', hue: 'yellow', r: 38, ...at(189, 465) },
  { id: 'stretch', name: 'Stretch', icon: 'body-outline', hue: 'orange', r: 34, ...at(68, 250) },
  { id: 'phone', name: 'No phone in bed', icon: 'phone-portrait-outline', hue: 'orange', r: 31, ...at(356, 254) },
  { id: 'vitamins', name: 'Vitamins', icon: 'medkit-outline', hue: 'red', r: 28, ...at(247, 505) },
  { id: 'plants', name: 'Water the plants', icon: 'flower-outline', hue: 'red', r: 26, ...at(47, 413) },
  { id: 'journal', name: 'Journal', icon: 'create-outline', hue: 'glass', r: 24, ...at(132, 498) },
];

/** The time-window dial in the middle of the social set (not a habit; it does not take touches). */
export const QUIET = { r: 55, ...at(190, 447), title: 'QUIET HOURS', hours: '22:00–07:00' } as const;

export const FONT = { regular: 'HBC-Inter-Regular', medium: 'HBC-Inter-Medium', semibold: 'HBC-Inter-SemiBold' } as const;

export const TYPE = {
  segment: { fontFamily: FONT.semibold, fontSize: 14, lineHeight: 18 },
  date: { fontFamily: FONT.semibold, fontSize: 24, lineHeight: 30, color: COLOR.ink, textAlign: 'center' as const, letterSpacing: -0.3 },
  now: { fontFamily: FONT.regular, fontSize: 14, lineHeight: 18, color: COLOR.inkSoft, textAlign: 'center' as const },
  count: { fontFamily: FONT.semibold, fontSize: 15, lineHeight: 20, color: COLOR.ink, fontVariant: ['tabular-nums'] as ('tabular-nums')[] },
  countMute: { fontFamily: FONT.regular, fontSize: 15, lineHeight: 20, color: COLOR.inkSoft },
  monogram: { fontFamily: FONT.semibold, fontSize: 14, lineHeight: 18, color: COLOR.ink },
  /** Canvas text sizes (pt): the label a tap shows, the quiet dial's arcs. */
  label: 14,
  labelSub: 12,
  quiet: 9,
} as const;

/** 4 pt grid. */
export const SPACE = { xs: 4, s: 8, m: 12, l: 16, xl: 24 } as const;

export const LAYOUT = {
  gutter: 16,
  control: 44,
  segmentWidth: 196,
  /** The date sits this far below the cluster's centre; the count pill and the corner buttons sit on the bottom row. */
  dateDrop: 252,
  pill: 44,
  // Space kept between bubbles when packing.
  gap: 3,
  iconScale: 0.3,
  ring: 2,
  ringGap: 6,
  fingerRadius: 22,
  /** The name a tap shows: a glass label above the bubble. */
  labelH: 52,
  labelGap: 10,
} as const;

export const COPY = {
  tabs: ['Social', 'Personal'] as const,
  date: 'Tuesday, 14 October',
  now: 'Hold a habit to mark it done',
  monogram: 'AM',
} as const;
