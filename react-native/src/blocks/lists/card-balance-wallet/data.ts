export const CARD = {
  last4: '6745',
  balance: '$12,567',
} as const;

export const SPENT = {
  month: 'May',
  amount: '$1,789',
  /** Share of the month's spending per category, in the bar's order (pink, blue, lilac, yellow), as in the pin. */
  split: [0.3, 0.15, 0.22, 0.33],
  colors: ['#EAB4BB', '#B4E2EC', '#C9B6EF', '#E8EAB3'],
} as const;

export const PROFILE = { name: 'Carmen', photo: require('./assets/carmen.jpg') } as const;
