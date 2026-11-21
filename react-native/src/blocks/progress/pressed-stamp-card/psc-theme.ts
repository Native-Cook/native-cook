// Pressed Stamp Card: tokens. Positions are points on the 402 × 874 pt canvas and scale with the screen width.

export const C = {
  page: '#F4EDE0',
  paperRow: '#FBF6EA',
  hairline: '#EADFC8',
  ink: '#2A1B0C',
  caramel: '#B8813B',
  caramelDark: '#C79A5B',
  gold: '#E8C272',
  stampLabel: '#BFA783',
  sentence: '#CDBB9E',
  wellNumber: '#5A4326',
  button: '#2A1B0C',
  buttonShadow: '#3D2A12',
  buttonText: '#F8F1E4',
  disc: '#EDE2C9',
  cup: '#8A6A3A',
  rowMute: '#8E8273',
  cardShadow: '#5A4326',
  shadow: '#0C0703',
} as const;

/** The card and its wells (card coordinates are relative to the card's top-left corner). */
// The batch Pro bar takes precedence over the measured 248 pt card: 35.4% hero area.
export const CARD = { x: 20, y: 118, w: 362, h: 344, r: 24 } as const;
export const WELL_R = 36;
export const SEAL_R = 34;
export const wellX = (i: number) => {
  'worklet';
  return 60 + 77 * (i % 4);
};
export const wellY = (i: number) => {
  'worklet';
  return 114 + 94 * Math.floor(i / 4);
};

/** Each seal is pressed by hand: its own angle (deg) and a small offset from the well's centre (pt). */
export const ROT = [-6, 4, -3, 7, -5, 2, -7, 5];
export const OFF: [number, number][] = [
  [0.6, -0.4],
  [-0.8, 0.5],
  [0.3, 0.9],
  [-0.5, -0.7],
  [1.0, 0.2],
  [-0.3, -1.0],
  [0.7, 0.6],
  [-1.0, 0.3],
];

/** Seal centre in card coordinates. */
export const sealCX = (i: number) => {
  'worklet';
  return wellX(i) + OFF[i][0];
};
export const sealCY = (i: number) => {
  'worklet';
  return wellY(i) + OFF[i][1];
};

/** The list. */
export const LIST = { top: 610, pitch: 76, rowH: 68, x: 20, w: 362 } as const;
/** "+1 stamp" in a row: its right edge is 16 pt in from the row's, its cap top 40 pt below the row's top. */
export const LABEL = { right: 16, capTop: 40, cx: 340, cy: 44.4 } as const;

/** Design-system springs (damping, stiffness, mass 1). */
export const SPRING = {
  glide: { d: 22.1, k: 157.9 },
  weighty: { d: 15.1, k: 93.4 },
  pop: { d: 19, k: 195 },
  snap: { d: 30.5, k: 322.3 },
  drift: { d: 14, k: 48.7 },
} as const;

export const FONT = {
  serif: 'PSC-Serif-SemiBold',
  regular: 'PSC-Inter-Regular',
  medium: 'PSC-Inter-Medium',
  semibold: 'PSC-Inter-SemiBold',
  bold: 'PSC-Inter-Bold',
} as const;

/** Cap-top offsets: the distance from a Text box's top to its cap top (iOS default line height), per em. */
export const CAP_OFF = { inter: 0.969 - 0.7275, serif: 1.036 - 0.67 } as const;

type Order = { name: string; time: string; price: string };
const HISTORY: Order[] = [
  { name: 'Doppio', time: '4 days ago', price: '€2.80' },
  { name: 'Oat latte', time: '3 days ago', price: '€4.20' },
  { name: 'Espresso', time: '2 days ago', price: '€2.60' },
  { name: 'Latte macchiato', time: 'Yesterday · 08:40', price: '€3.80' },
  { name: 'Americano', time: 'Today · 09:41', price: '€3.00' },
];
const NEW: Order[] = [
  { name: 'Flat white', time: 'Just now', price: '€3.90' },
  { name: 'Cortado', time: 'Just now', price: '€3.40' },
  { name: 'Cappuccino', time: 'Just now', price: '€3.60' },
];

/** The order that brought stamp j (j = 1 is the oldest; 5 is the newest at rest). */
export const orderAt = (j: number): Order => {
  if (j >= 1 && j <= 5) return HISTORY[j - 1];
  if (j < 1) return HISTORY[((j % 5) + 5 + 4) % 5];
  return NEW[(j - 6) % 3];
};
