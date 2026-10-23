/** Colours, type, layout and the habits. Positions are points on a 402 × 874 screen, measured on the source. */
import type { ComponentProps } from 'react';
import type { Ionicons } from '@expo/vector-icons';

type IconName = ComponentProps<typeof Ionicons>['name'];

export const COLOR = {
  bg: '#141313',
  dust: 'rgba(255,255,255,0.05)',
  ink: '#FFFFFF',
  inkSoft: 'rgba(255,255,255,0.55)',
  inkFaint: 'rgba(255,255,255,0.35)',
  control: '#1E1E21',
  controlSelected: '#FFFFFF',
  controlInkSelected: '#0A0A0B',
  disc: '#FFFFFF',
  discDim: 'rgba(255,255,255,0.1)',
  personalTop: '#363637',
  personalBottom: '#252526',
  card: '#141415',
  hairline: 'rgba(255,255,255,0.14)',
  good: '#5FBF5A',
  discInk: '#0A0A0B',
  quiet: '#E0565B',
  quietCore: '#2A1012',
  badge: '#3B6CF6',
  spark: '#FFFFFF',
  finger: 'rgba(255,255,255,0.32)',
  fingerRing: 'rgba(255,255,255,0.7)',
} as const;

/** Neon hues: rim (bright), core (dark tint), halo (glow). */
export const HUE = {
  purple: { light: '#C9A2EE', inner: '#7B2BD6', rim: '#8A52C4', core: '#1A0F2B', halo: 'rgba(138,82,196,0.45)', icon: '#A94FE0' },
  green: { light: '#A6EDBF', inner: '#3FCB72', rim: '#5BE38E', core: '#0C1F14', halo: 'rgba(80,220,140,0.36)', icon: '#5CD487' },
  yellow: { light: '#F6DE93', inner: '#E8B530', rim: '#F7C948', core: '#211A08', halo: 'rgba(245,195,70,0.36)', icon: '#EFC04A' },
  orange: { light: '#F6BC8E', inner: '#EE7A2A', rim: '#F58A3C', core: '#22130A', halo: 'rgba(245,130,60,0.36)', icon: '#EE9049' },
  red: { light: '#E0888E', inner: '#A92A33', rim: '#C83A44', core: '#220C0E', halo: 'rgba(232,82,90,0.34)', icon: '#E25860' },
  glass: { light: '#A8A8B0', inner: '#6C6C74', rim: '#7C7C86', core: '#141416', halo: 'rgba(160,160,175,0.14)', icon: 'rgba(180,180,190,0.3)' },
} as const;
export type Hue = keyof typeof HUE;

/** The bubbles' glass: interior tint, edge falloff, highlight (alphas). */
export const QUIET_GLASS = { body: 0.22, wedge: 0.08 } as const;

export const GLASS = {
  interiorSmall: 0.46, interiorLarge: 0.15, interiorFrom: 45, interiorSlope: 0.009,
  bigRadius: 60, bandLarge: 0.95, bandSmall: 0.72, rimLight: 0.8, lightX: 0.06, lightY: -0.08, lightR: 1.02, edge: 0.9, halo: 0.45, chargeLift: 0.3, highlight: 0.06, spriteEdge: 0.45, spriteEdgeGlass: 0.12,
} as const;

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

// The source's Personal view is a quiet, near-empty state: one dim glass bubble above the date.
export const PERSONAL: Habit[] = [
  { id: 'journal', name: 'Journal', icon: 'create-outline', hue: 'glass', r: 22, ...at(247, 476) },
];

/** The time-window dial in the middle of the social set (not a habit; it does not take touches). */
export const QUIET = { r: 55, ...at(190, 447), title: 'QUIET HOURS', hours: '22:00–07:00' } as const;

/** The detail view's category per habit (the source shows "Category / Personal Life"). */
export const CATEGORY: Record<string, string> = {
  home: 'Personal Life', heart: 'Personal Life', art: 'Creativity', wave: 'Neighbours', star: 'Gratitude', host: 'Friends',
  magic: 'Friends', ride: 'Health', friend: 'Friends', ball: 'Health', gift: 'Friends', tea: 'Family Life', wallet: 'Money', note: 'Friends',
  journal: 'Personal Life',
};

export const TYPE = {
  segment: { fontSize: 15, fontWeight: '700' as const },
  date: { fontSize: 24, lineHeight: 30, fontWeight: '500' as const, color: COLOR.ink, textAlign: 'center' as const },
  now: { fontSize: 14, lineHeight: 18, fontWeight: '500' as const, color: COLOR.inkSoft, textAlign: 'center' as const },
  count: { fontSize: 17, fontWeight: '800' as const, color: COLOR.discInk, fontVariant: ['tabular-nums'] as ('tabular-nums')[] },
  category: { fontSize: 12, fontWeight: '500' as const, color: COLOR.inkSoft, textAlign: 'center' as const },
  categoryTitle: { fontSize: 20, fontWeight: '700' as const, color: COLOR.ink, textAlign: 'center' as const },
  balance: { fontSize: 17, fontWeight: '500' as const, color: COLOR.inkSoft },
  good: { fontSize: 14, fontWeight: '700' as const, color: COLOR.ink },
  sectionTitle: { fontSize: 24, lineHeight: 30, fontWeight: '600' as const, color: COLOR.ink },
  body: { fontSize: 17, lineHeight: 25, fontWeight: '400' as const, color: COLOR.inkSoft },
  personalNow: { fontSize: 20, lineHeight: 26, fontWeight: '500' as const, color: COLOR.inkSoft, textAlign: 'center' as const },
  personalDate: { fontSize: 36, lineHeight: 42, fontWeight: '500' as const, color: COLOR.ink, textAlign: 'center' as const },
  quiet: 9,
} as const;

/**
 * Spacing scale, derived from the source's measured gaps (pt): 2 (badge ring), 4 (date ↔ time), 6 (segment inset ×2),
 * 12 (pill padding), 16 (screen inset), 18 (header controls), 20 (card padding / rhythm), 27 (card top, measured).
 */
export const SPACE = { hair: 2, xs: 3, s: 4, m: 6, l: 12, xl: 16, x2: 18, x3: 20, cardTop: 27 } as const;

export const LAYOUT = {
  gutter: 17,
  headerTop: 1,
  control: 40,
  segmentWidth: 185,
  iconButton: 40,
  clusterTop: 0,
  dateY: 640,
  discY: 792,
  disc: 74,
  dot: 22,
  dotOffset: 60,
  corner: 52,
  cornerInset: 16,
  cornerY: 830,
  // Space kept between bubbles when packing.
  gap: 3,
  iconScale: 0.3,
  ring: 3,
  ringGap: 7,
  fingerRadius: 22,
  sparks: 12,
  // Personal view (measured): date block at 0.66 H, the dot above the disc.
  personalDateY: 0.654,
  personalDot: { x: 0.53, y: 0.84 },
  // Detail view (measured).
  detailBalanceY: 0.513,
  detailCardY: 0.6,
  rowHeight: 60,
  iconGlow: 10,
} as const;

export const COPY = {
  tabs: ['Social', 'Personal'] as const,
  date: '14 October',
  now: 'Now - 08:12',
  personalDate: '26 October',
  category: 'Category',
  balance: 'Balance',
  good: 'Good',
  description: 'Description',
  body: [
    'Small rituals with the people you love keep a week from slipping by. This habit counts the evenings you sit down together, phones away, and asks nothing else of you.',
    'We look at when you usually manage it and nudge you on the days that fit, never on the busy ones.',
  ],
  avatar: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=160&h=160&q=80',
} as const;
