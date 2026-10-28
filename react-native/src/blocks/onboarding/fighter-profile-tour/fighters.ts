import type { ImageSourcePropType } from 'react-native';

export type FighterKey = 'villager' | 'lucas' | 'puff' | 'rosa' | 'bowser';
export type Rect = { x: number; y: number; width: number; height: number };
export type Fighter = {
  key: FighterKey;
  name: string;
  tier: 'B' | 'C' | 'D' | 'E';
  series: string;
  seriesIcon: 'leaf' | 'musical-notes' | 'sparkles' | 'star' | 'flame';
  art: ImageSourcePropType;
  /** Art size in pixels, for fitting the full figure. */
  artSize: { width: number; height: number };
  /** The bust a roster card shows: the art's rect inside the 149×112 art box at the card's right. */
  rowFit: Rect;
  /** Weight, run speed, dash speed, air speed, fall speed: displayed value and 0–1 bar. */
  stats: readonly { value: string; amount: number }[];
  /** Matchup metrics Weight, Air, Run, Dash (0–1), compared against the selected fighter. */
  metrics: readonly [number, number, number, number];
  counters: readonly [FighterKey, FighterKey];
  /** Page colour (top, middle, bottom) and the light accent used for icons and secondary text. */
  page: readonly [string, string, string];
  accent: string;
};

export const STAT_LABELS = ['Weight', 'Run speed', 'Dash speed', 'Air speed', 'Fall speed'] as const;
export const STAT_ICONS = ['barbell', 'walk', 'chevron-forward', 'paper-plane', 'arrow-down'] as const;
export const METRIC_LABELS = ['Weight', 'Air', 'Run', 'Dash'] as const;
const TIER_NOTE = { B: 'above average', C: 'average', D: 'below average', E: 'well below average' } as const;

export const FIGHTERS: readonly Fighter[] = [
  { key: 'villager', name: 'Villager', tier: 'E', series: 'Animal Crossing', seriesIcon: 'leaf',
    art: require('./assets/villager.png'), artSize: { width: 1254, height: 1254 },
    rowFit: { x: -18, y: -3, width: 157, height: 157 },
    stats: [{ value: '92', amount: 0.43 }, { value: '1.40', amount: 0.30 }, { value: '1.70', amount: 0.61 }, { value: '0.99', amount: 0.46 }, { value: '1.42', amount: 0.40 }],
    metrics: [0.43, 0.46, 0.30, 0.61], counters: ['bowser', 'puff'],
    page: ['#2544c5', '#233fc0', '#213aaf'], accent: '#82b5ff' },
  { key: 'lucas', name: 'Lucas', tier: 'C', series: 'EarthBound', seriesIcon: 'musical-notes',
    art: require('./assets/lucas.png'), artSize: { width: 1254, height: 1254 },
    rowFit: { x: -13, y: -2, width: 175, height: 188 },
    stats: [{ value: '82', amount: 0.38 }, { value: '1.55', amount: 0.45 }, { value: '1.82', amount: 0.68 }, { value: '1.03', amount: 0.50 }, { value: '1.58', amount: 0.52 }],
    metrics: [0.38, 0.50, 0.45, 0.68], counters: ['rosa', 'bowser'],
    page: ['#c4473b', '#b63e34', '#9e332b'], accent: '#ffc0b5' },
  { key: 'puff', name: 'Jigglypuff', tier: 'D', series: 'Pokémon', seriesIcon: 'sparkles',
    art: require('./assets/jigglypuff.png'), artSize: { width: 890, height: 854 },
    rowFit: { x: 0, y: 0, width: 150, height: 144 },
    stats: [{ value: '68', amount: 0.12 }, { value: '1.10', amount: 0.18 }, { value: '1.43', amount: 0.45 }, { value: '1.35', amount: 1 }, { value: '0.98', amount: 0.10 }],
    metrics: [0.12, 1, 0.18, 0.45], counters: ['bowser', 'lucas'],
    page: ['#c24a96', '#b4428a', '#9c3776'], accent: '#ffc6ea' },
  { key: 'rosa', name: 'Rosalina', tier: 'D', series: 'Super Mario Galaxy', seriesIcon: 'star',
    art: require('./assets/rosalina.png'), artSize: { width: 1376, height: 1606 },
    rowFit: { x: -22, y: -8, width: 193, height: 225 },
    stats: [{ value: '82', amount: 0.38 }, { value: '1.80', amount: 0.62 }, { value: '2.00', amount: 0.78 }, { value: '1.24', amount: 0.80 }, { value: '1.17', amount: 0.22 }],
    metrics: [0.38, 0.80, 0.62, 0.78], counters: ['lucas', 'villager'],
    page: ['#238f9c', '#208491', '#1a707c'], accent: '#a2eef5' },
  { key: 'bowser', name: 'Bowser', tier: 'C', series: 'Super Mario', seriesIcon: 'flame',
    art: require('./assets/bowser.png'), artSize: { width: 1386, height: 1384 },
    rowFit: { x: -12, y: -8, width: 174, height: 174 },
    stats: [{ value: '135', amount: 1 }, { value: '1.80', amount: 0.62 }, { value: '2.05', amount: 0.80 }, { value: '1.10', amount: 0.63 }, { value: '1.86', amount: 0.75 }],
    metrics: [1, 0.63, 0.62, 0.80], counters: ['puff', 'rosa'],
    page: ['#2f7f52', '#2a744b', '#226240'], accent: '#aef0c4' },
];

export const FIGHTER_INDEX: Record<FighterKey, number> = { villager: 0, lucas: 1, puff: 2, rosa: 3, bowser: 4 };

export function fighterSummary(fighter: Fighter) {
  return `Super Smash Bros. Ultimate ${fighter.name} is from the ${fighter.series} series and ranks as ${fighter.tier === 'E' ? 'an' : 'a'} ${fighter.tier} Tier pick (${TIER_NOTE[fighter.tier]}).`;
}

/** The full figure, contained in `box` and standing on its bottom edge. */
export function standIn(fighter: Fighter, box: Rect): Rect {
  const ratio = fighter.artSize.width / fighter.artSize.height;
  const width = Math.min(box.width, box.height * ratio);
  const height = width / ratio;
  return { x: box.x + (box.width - width) / 2, y: box.y + box.height - height, width, height };
}
