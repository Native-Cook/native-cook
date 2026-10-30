// Frost Card Controls — layout, colour and copy, measured on the pin (a 1080 × 1920 px screenshot
// without its status bar; its top sits 50 pt down a 402 × 874 pt screen: y → 50 + px · 402/1080).

export const REF_W = 402;
export const REF_H = 874;

export const C = {
  bg: '#F2F2F7',
  ink: '#111114',
  grey: '#8C8C93',
  blue: '#2B3BE0',
  tile: '#FFFFFF',
  button: '#1C1C1F',
  brandA: '#FF4E1A',
  brandB: '#FF6E2B',
  ice: '#DDEFFF',
};

export const COPY = {
  balance: '−$3,116.58',
  noLimit: 'No spending limit',
  setLimit: 'Set limit',
  brand: 'ARIVA',
  physical: 'Physical card',
  last4: '5371',
  hint: 'Swipe or tap to see details',
  wallet: 'Add to Wallet',
  tiles: ['Freeze', 'Reissue', 'Discard'],
  unfreeze: 'Unfreeze',
  frozen: 'Frozen',
  transactions: 'Transactions',
  nothing: 'Nothing spent in April',
};

export const UI = {
  balance: { x: 41.5, y: 91.5, size: 25 },
  noLimit: { x: 41.5, y: 122, size: 16 },
  setLimit: { x: 362.7, y: 122 },
  card: { x: 41.5, y: 182.2, w: 321.2, h: 207.6, r: 14 },
  hint: { x: 202, y: 427 },
  wallet: { x: 20.8, y: 487.5, w: 363.2, h: 57.9, r: 16 },
  tiles: { x: [20.8, 149.1, 277.5], y: 567.2, w: 106.5, h: 89, r: 16, iconY: 599, labelY: 631.7 },
  tx: { x: 20.8, y: 678, w: 363.2, h: 220, r: 20 },
};

/** Inside the card (card space). */
export const CARD = { physical: { x: 21.9, y: 157.3 }, last4: { x: 21.9, y: 177.5 }, mark: { x: 264.4, y: 162.8 } };
