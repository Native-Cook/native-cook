// Frost Chest Opening — every colour, size and copy string of the block.
// Layout numbers are points on the 402 × 874 pt screen the design was measured on; the block scales
// them with the screen's width (x, sizes) and its height (y), and pins the header to the safe area.

export const REF_W = 402;
export const REF_H = 874;

export const COPY = {
  balance: '2,953',
  eyebrow: 'A BIG SURPRISE',
  title: 'AWAITS!',
  drop: '1X RARE DROP',
  hint1: 'HOLD TO OPEN',
  hint2: 'CHEST!',
  plate1: 'RARE',
  plate2: 'BOX',
  reward1: 'WINTER SPECIAL',
  reward2: 'COSTUME',
  rarity: 'SUPER RARE',
  others: 'Other Rewards',
  items: ['XP Boost', 'Winter Boots', 'Frozen Shrine'],
  claim: 'CLAIM',
} as const;

// Colours as 0xRRGGBB (read with `hex()` / `rgba()`).
export const C = {
  skyTop: 0x8cb4ef,
  skyMid: 0xc3d7f6,
  skyLow: 0xeaf1fb,
  floor: 0xf6f8fd,
  rayLight: 0xf4f8ff,
  rayDeep: 0x6f9be6,
  frostTop: 0xd9e5f8,
  frostLow: 0xe9eff9,
  ink: 0x3a4766, // pill text, coin balance
  inkSoft: 0x4c5a7a, // "Other Rewards", item labels
  hint: 0x97a4bf,
  title: 0xffffff,
  titleGlow: 0x9ccaff,
  deepBlue: 0x0e5c9e, // reward name
  glass: 0xffffff,
  cyan: 0x37d6ff,
  cyanDeep: 0x1597ea,
  orbDeep: 0x0b6fd6,
  orbLight: 0x6fe3ff,
  streak: 0x22bdf0,
  claimTop: 0x55c4ff,
  claimMid: 0x1a8cff,
  claimLow: 0x0b62e6,
  claimInk: 0xeaf7ff,
  plus: 0x1d8bf7,
  starEdge: 0x94eefd, // the sparkles' cyan rim (the core is white), sampled
  starEdgeSoft: 0xc2f1fb, // the burst's paler rims
  chip: 0x5fd2f4, // ice chips of the burst
} as const;

// Measured on the reference (pt). Text is placed by its baseline and fitted to the measured width.
export const L = {
  backX: 44.5, backY: 45, backD: 56, // y from the safe-area top
  coinX: 237.7, coinY: 21, coinW: 149, coinH: 47, coinIcon: 31, plusD: 38,
  balanceBase: 52, balanceW: 49,
  eyebrowBase: 213.5, eyebrowW: 262,
  titleBase: 275.7, titleW: 298,
  chestCX: 203.5, chestCY: 434.4, chestW: 329, chestSY: 1.03, // closed chest: sprite centre, drawn width, extra height
  openCX: 198.5, openCY: 408, openW: 344, mouthY: 400, // the open chest after the reveal, and its glowing mouth
  rayCX: 201, rayCY: 420,
  pillX: 92, pillY: 583.7, pillW: 219, pillH: 76, pillIcon: 27, pillTextX: 152, dropBase: 630.7, dropW: 136,
  // Even rhythm: chest → pill → hint all 34 pt apart.
  handY: 708, handSize: 28,
  hintBase: 752, hintBase2: 774.5, hintW: 126, hintW2: 62,
  cardCX: 201.5, cardCY: 226, cardW: 219, cardH: 300, cardR: 24,
  jacketCX: 206, jacketCY: 185.4, jacketW: 183,
  rewardBase: 300.4, rewardBase2: 320, rewardW: 159, rewardW2: 94,
  rarityCY: 354, rarityW: 102, rarityH: 28, rarityTextW: 68,
  panelX: 12, panelY: 568, panelW: 378, panelH: 171, panelR: 22,
  othersBase: 596.6, othersW: 114,
  tileCY: 655, tileD: 81, tileR: 22, tileXs: [87, 201, 315], tileArt: 64,
  itemBase: 716.4, itemW: [48.5, 72, 78],
  claimX: 13, claimY: 759, claimW: 376, claimH: 69, claimBase: 802, claimTextW: 56,
} as const;

export function hex(v: number) {
  'worklet';
  return `#${v.toString(16).padStart(6, '0')}`;
}

export function rgba(v: number, a: number) {
  'worklet';
  return `rgba(${(v >> 16) & 255},${(v >> 8) & 255},${v & 255},${a})`;
}
