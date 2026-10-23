import type { ImageSourcePropType } from 'react-native';

import type { StampTone } from './theme';

export type StampItem = {
  id: string;
  label: string;
  art: ImageSourcePropType;
  tone: StampTone;
};

// Order on the wheel, left to right. The pin opens on Games; the orange stamp only ever
// shows its corner there, so its name is ours.
export const STAMPS: StampItem[] = [
  {
    id: 'organisation',
    label: 'Organisation',
    art: require('./assets/art-organisation.png'),
    tone: {
      tint: '#EEF9F4',
      glow: '#A9DCC6',
      border: '#2FB47C',
      label: '#006539',
      shade: '#1E8A5E',
    },
  },
  {
    id: 'mixed',
    label: 'Mixed',
    art: require('./assets/art-mixed.png'),
    tone: {
      tint: '#FEECE9',
      glow: '#EDB9B4',
      border: '#F8544D',
      label: '#490000',
      shade: '#4A1820',
    },
  },
  {
    id: 'games',
    label: 'Games',
    art: require('./assets/art-games.png'),
    tone: {
      tint: '#E8F0FE',
      glow: '#B3C9F5',
      border: '#304FD9',
      label: '#0F35B5',
      shade: '#3A5596',
    },
  },
  {
    id: 'daily',
    label: 'Daily',
    art: require('./assets/art-daily.png'),
    tone: {
      tint: '#FEF9E8',
      glow: '#F5D9A2',
      border: '#E58A00',
      label: '#8F4E00',
      shade: '#9A6A20',
    },
  },
];
