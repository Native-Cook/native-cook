import type { ImageSourcePropType } from 'react-native';

// The card designs a customer can pick. The first is drawn (flat flowers, as in the pin);
// the others are illustrations made for this block in the pin's painted style.
export type Design = {
  id: string;
  art: ImageSourcePropType | null; // null = the drawn flower pattern
  thumb: ImageSourcePropType; // the picker's tile, painted to match the pin's tile for this design
  designer: string;
  price: string;
  label: string; // for VoiceOver
};

export const DESIGNS: Design[] = [
  { id: 'blossom', thumb: require('./assets/thumb-blossom.jpg'), art: null, designer: 'Olga Paperova', price: '$4.99', label: 'Pink blossoms on green' },
  { id: 'meadow', thumb: require('./assets/thumb-meadow.jpg'), art: require('./assets/card-meadow.jpg'), designer: 'Kenta Hayashi', price: '$4.99', label: 'A cat watching the sea' },
  { id: 'ridge', thumb: require('./assets/thumb-ridge.jpg'), art: require('./assets/card-ridge.jpg'), designer: 'Inês Duarte', price: '$6.99', label: 'Mountains under a setting planet' },
  { id: 'valley', thumb: require('./assets/thumb-valley.jpg'), art: require('./assets/card-valley.jpg'), designer: 'Tomasz Wilk', price: '$6.99', label: 'A green valley with a striped moon' },
  { id: 'silk', thumb: require('./assets/thumb-silk.jpg'), art: require('./assets/card-silk.jpg'), designer: 'Ana Lucía Ríos', price: 'Free', label: 'Silver silk' },
];

export const PRODUCT = 'Bloom Card';

// The two plans of the segmented control and what the card costs on each.
export const PLANS = [
  { id: 'basic', label: 'Basic', fee: '$0', feeUnit: 'per month', categories: '3' },
  { id: 'prime', label: 'BloomPrime', fee: '$0', feeUnit: 'with Prime', categories: '5' },
] as const;
