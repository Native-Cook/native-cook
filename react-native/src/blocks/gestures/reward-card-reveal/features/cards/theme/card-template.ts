import type { CardFinish } from '../models/card';

export const CARD_ASPECT_RATIO = 63 / 88;
export const CARD_HEIGHT_RATIO = 88 / 63;

export const COLLECTION_CARD_BACK = {
  background: '#e5e9ef',
  patternDark: '#cfd5df',
  edge: 'rgba(255, 255, 255, 0.76)',
  edgeInner: 'rgba(255, 255, 255, 0.48)',
  seal: '#737b89',
  markerBorder: 'rgba(105, 115, 132, 0.19)',
  markerInk: 'rgba(105, 115, 132, 0.22)',
} as const;

export const COLLECTION_GALLERY = {
  background: '#e9edf3',
  text: '#202631',
} as const;

export const COLLECTION_CARD_BACKDROP = {
  background: COLLECTION_CARD_BACK.background,
  pattern: '#b9c3cf',
  seal: COLLECTION_CARD_BACK.seal,
} as const;

export type CardTemplatePalette = {
  frame: string;
  frameLight: string;
  header: string;
  panel: string;
  panelDeep: string;
  ink: string;
  mutedInk: string;
  artBorder: string;
  energy: string;
  energyInk: string;
};

export const CARD_TEMPLATE_PALETTES: Record<CardFinish, CardTemplatePalette> = {
  'rare-holo': {
    frame: '#b6b7b2',
    frameLight: '#f1eee6',
    header: '#ef7e55',
    panel: '#efaa7b',
    panelDeep: '#d45f3f',
    ink: '#17140f',
    mutedInk: '#4f342b',
    artBorder: '#7d3c2b',
    energy: '#d85333',
    energyInk: '#fff2df',
  },
  'double-rare': {
    frame: '#b7bab4',
    frameLight: '#f5f2e8',
    header: '#b5d74c',
    panel: '#d6ea85',
    panelDeep: '#8bad3e',
    ink: '#171b0d',
    mutedInk: '#3f4d24',
    artBorder: '#59752d',
    energy: '#356b32',
    energyInk: '#f4ffe0',
  },
  'special-illustration': {
    frame: '#c5d2da',
    frameLight: '#f7fbfc',
    header: '#75c4e4',
    panel: '#a9dded',
    panelDeep: '#4b9ec7',
    ink: '#10202b',
    mutedInk: '#315467',
    artBorder: '#3a7295',
    energy: '#2f83b0',
    energyInk: '#effcff',
  },
  'hyper-rare': {
    frame: '#b88832',
    frameLight: '#fff0a8',
    header: '#d9a441',
    panel: '#edcd75',
    panelDeep: '#a97222',
    ink: '#23170a',
    mutedInk: '#5e421a',
    artBorder: '#83551a',
    energy: '#4f2d72',
    energyInk: '#fff2bc',
  },
};
