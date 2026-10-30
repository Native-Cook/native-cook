// Lucky Task Hub — layout, colour and copy, measured on the pin (a 720 × 1280 screen recording,
// scrolled so its header is off the top). Everything is drawn in the pin's pixels: one point of the
// 402-point screen is 1 / S pixels, and the pin's y = 0 sits DY points down, under a header of ours.

export const REF_W = 402;
export const REF_H = 874;
export const S = 402 / 720;
export const DY = 96;

export const C = {
  bgTop: '#E6F6C3',
  bgMid: '#F6FBEF',
  page: '#FFFFFF',
  ink: '#1B1C22',
  ink2: '#2A2C33',
  grey: '#7B7F87',
  line: '#E2E8DA',
  yellow: '#FFD21F',
  yellowDeep: '#F6B800',
  panel: '#FFF6C2',
  podium: '#FFE15A',
  redCard: '#FF4A2E',
  orange: '#FF8A1F',
  pink: '#F07ED3',
  pinkDeep: '#D95BBE',
  green: '#7ED321',
  greenDeep: '#56B000',
  wheelA: '#B9EE6B',
  wheelB: '#8FDB3A',
  cardBg: '#F3F7EE',
  cardEdge: '#E1E8D6',
  pill: '#E7EDE0',
  timerBg: '#FFF1D8',
  bolt: '#FFB000',
  white: '#FFFFFF',
  black: '#17181C',
  cash: '#5FCB3A',
};

export const COPY = {
  header: 'Rewards',
  ranking: 'Ranking',
  timer: '05:06:26',
  tiers: ['Bronze', 'Silver', 'Gold'],
  flip: 'Lucky Flip',
  spin: 'Lucky Spin',
  go: 'Go',
  newbie: 'Newbie Tasks',
  newbieTimer: '6D:23:21:10',
  node: 'Rs27.00',
  goal: '500',
  offers: 'Super Offers',
  seeAll: 'See All',
  games: [
    { name: 'Tiger Clash', reward: '1,605' },
    { name: 'Zap Rush', reward: '270' },
    { name: 'Fruit Tiles', reward: '14K' },
  ],
  progress: ['You have ', '2', ' offer(s) in progress'],
  chest: '02:48',
  casual: 'Casual Games Tasks',
  row: 'Play Fruit Tiles for 10 minutes',
  rowReward: '120',
};

/** The balance in the header before anything is won. */
export const BALANCE = 12480;
export const BILL_VALUE = 100;
export const STEPS = 14;
export const FLIPS = 30;

/** Every rectangle below is in the pin's pixels: x, y, w, h (and r, the corner radius). */
export const UI = {
  header: { titleY: -48, balance: { x: 498, y: -78, w: 190, h: 60 } },
  ranking: {
    card: { x: 40, y: 50, w: 310, h: 395, r: 30 },
    back: { x: 34, y: 38, w: 300, h: 410, r: 30, rot: -4 },
    title: { x: 62, y: 92 },
    timer: { x: 62, y: 118, w: 124, h: 32 },
    panel: { x: 56, y: 196, w: 282, h: 170, r: 26 },
    trophy: { x: 197, y: 248, size: 150 },
    podium: { x: 195, y: 318, rx: 96, ry: 22 },
    label: { x: 197, y: 337, w: 118, h: 30 },
    mascot: { x: 128, y: 266 },
    arrow: { x: 262, y: 250 },
    go: { x: 66, y: 388, w: 86, h: 36 },
  },
  flip: {
    card: { x: 368, y: 50, w: 320, h: 193, r: 30 },
    title: { x: 390, y: 92 },
    remaining: { x: 392, y: 126 },
    go: { x: 392, y: 180, w: 86, h: 36 },
    back: { x: 600, y: 182, size: 96, rot: -16 },
    front: { x: 634, y: 170, size: 104, rot: 9 },
    fire: { x: 664, y: 72, r: 23 },
  },
  spin: {
    card: { x: 368, y: 258, w: 320, h: 192, r: 30 },
    title: { x: 390, y: 298 },
    remaining: { x: 392, y: 332 },
    go: { x: 392, y: 387, w: 86, h: 36 },
    wheel: { x: 626, y: 398, r: 62 },
    eyes: { x: 626, y: 346 },
  },
  newbie: { titleX: 32, titleY: 524, timer: { x: 505, y: 507, w: 183, h: 34 } },
  progress: {
    card: { x: 32, y: 573, w: 656, h: 138, r: 32 },
    count: { x: 62, y: 632 },
    bar: { x: 168, y: 614, w: 462, h: 28 },
    node: { x: 316, y: 627, r: 27 },
    nodeLabel: { y: 675 },
    goal: { x: 592, y: 622 },
    arrow: { x: 640, y: 607, w: 60, h: 48 },
  },
  offers: { titleY: 786, cards: [33, 247, 461, 677], y: 852, w: 190, h: 203, icon: 92 },
  inProgress: { avatar: { x: 62, y: 1122, r: 42 }, bubble: { x: 118, y: 1090, w: 560, h: 64 }, chest: { x: 624, y: 1060, w: 96, h: 96 } },
  casual: { titleY: 1241, row: { x: 32, y: 1282, w: 656, h: 120 } },
};

/** The bar's fill (0…1 of its width) at a number of finished steps: the first node is step 5. */
export function fillAt(done: number) {
  'worklet';
  const nodeK = (UI.progress.node.x - UI.progress.bar.x) / UI.progress.bar.w;
  if (done <= 5) return (done / 5) * nodeK;
  return nodeK + ((done - 5) / (STEPS - 5)) * (1 - nodeK);
}
