// Trail Parallax Tracker: tokens. Canvas is 402 × 874 pt, scaled by S = width / 402; the data panel keeps its size and
// sits on the bottom edge, the scene takes what is left above it.

export const C = {
  panel: '#060606',
  card: '#111610',
  ink: '#FCFCFA',
  mute: '#8C8C8C',
  body: '#8F938C',
  tabOn: '#FFFFFF',
  tabOff: '#B5B5B5',
  bubble: '#FFFFFF',
  bubbleLabel: '#6B6B6B',
  bubbleValue: '#0A0A0A',
};

export const FONT = {
  display: 'TptOutfitBold',
  medium: 'TptInterMedium',
  semibold: 'TptInterSemiBold',
  regular: 'TptInterRegular',
};

export const G = {
  sceneH: 530, // the scene's bottom edge on the 874 canvas (the black hill hides it)
  // the strips' top edge: the scene is taller than the 440 pt art, whose top rows are extended upwards
  artTop: 90,
  hikerX: 200,
  // lead review: the hiker's cell is drawn 66 × 93 pt (the figure itself ≈ 84 pt tall; cells are 204 × 288 px)
  hikerW: 93 * (204 / 288),
  hikerH: 93,
  // the common sole anchor of the sprite cells, as a fraction of the cell
  hikerAx: 105 / 204,
  hikerAy: 280 / 288,
  sheetFrames: 19,
  groundBase: 500,
  // contour ±26 pt (the pin's hill is about 45 pt peak to valley)
  groundAmp: [17, 6, 3] as const,
  // Pro bar: nothing meaningful under 13 pt, so the bubble is larger than the pin's 9/11 pt one
  bubble: { w: 84, h: 50, r: 12, gap: 16 },
  // hill valley (≤ 526) → 24 pt → Day; the number is the second hero
  dayBase: 562,
  daySize: 17,
  numBase: 630,
  numSize: 76,
  numClipTop: 560,
  numClipBottom: 648,
  numStep: 84,
  milesBase: 661,
  card: { x: 14, y: 676, w: 374, h: 95, r: 14 },
  tabs: { y: 813, xs: [77, 201, 325], size: 24 },
};

/** Spring presets (damping, stiffness), mass 1. */
export const SPRING = {
  drift: { d: 14, k: 48.7 },
  snap: { d: 30.5, k: 322.3 },
  glide: { d: 22.1, k: 157.9 },
  detent: { d: 40.4, k: 503.5 },
  pop: { d: 19, k: 195 },
};

/** One mile of trail is 24 pt of travel at layer rate 1.0. */
export const MI = 24;
export const TOTAL = 150;
export const TO_GO = 825.7;

/** Biome borders in miles: canyon | granite | grove | dusk. A foreground occluder covers the frame at each. */
export const BORDERS = [26, 58.7, 2900 / MI];

/** Parallax rates, far → near (sky 0, ground 1.0). */
export const RATES = [0.12, 0.35, 0.7];

export type Biome = {
  /** strip width in pt (PNG width / 3) */
  w: number;
  /** image x offset (pt) per depth, so that the strip's best part is on screen where the walk holds */
  off: [number, number, number];
  /** sky: top, 60 %, horizon */
  sky: [string, string, string];
  /** the biome's flat fills (≤ 6), shared by its three strips */
  fills: string[];
  /** the hour row the art is painted at (0 07:00, 1 10:30, 2 15:00, 3 19:10, 4 20:30) */
  native: number;
};

export const BIOMES: Biome[] = [
  {
    w: 1440,
    off: [560, 620, 484],
    sky: ['#5E9FE6', '#8FC0F0', '#B5D6F5'],
    fills: ['#C9D8EE', '#A9BEDF', '#8AA3CF', '#F1F5FB'],
    native: 0,
  },
  {
    w: 1560,
    off: [490, 516, 430],
    sky: ['#5E9FE6', '#8FC0F0', '#B5D6F5'],
    fills: ['#A9BEDF', '#C1A6AB', '#9A8AA5', '#6E6C96', '#4B5980'],
    native: 1,
  },
  {
    w: 2800,
    off: [460, 420, 0],
    sky: ['#E6EFAE', '#EEF3B0', '#F3F6C2'],
    fills: ['#EEF3B0', '#BFE3A0', '#5FAF8F', '#2F7D6B', '#8E2A22', '#5E1A1C'],
    native: 2,
  },
  {
    w: 1400,
    off: [446, 450, -40],
    sky: ['#F5DFB6', '#FBECC5', '#FCF1D8'],
    fills: ['#B9B7B0', '#D1A696', '#E3B79A', '#C98F78', '#3E3A3C', '#262325'],
    native: 3,
  },
];

/**
 * Time of day as a light in OKLab, relative to the hour a biome is painted at: lightness shift, a/b shift, chroma
 * scale. Rows: 07:00, 10:30, 15:00, 19:10, 20:30 (blue hour).
 */
export const HOURS: [number, number, number, number][] = [
  [-0.035, -0.004, -0.024, 0.9],
  [0, 0, 0, 1],
  [0.012, 0.006, 0.02, 1.05],
  [-0.02, 0.016, 0.034, 0.92],
  [-0.3, -0.004, -0.075, 0.45],
];

/** Hour row along the trail (miles → row). The pin's dusk (row 3) holds from the dusk border to the end. */
export const HOUR_KEYS: [number, number][] = [
  [-20, 0],
  [0, 0],
  [26, 1],
  [58.7, 1.5],
  [76, 2],
  [105, 2.3],
  [2900 / MI, 3],
  // addendum: the fling brakes at 148.5 and the last stroll runs the palette into blue hour
  [148.5, 3],
  [150, 4],
];

export type Waypoint = { at: number; name: string; note: string };

/** Sawback Crest Trail (invented). `at` = miles walked where the section starts. */
export const WAYPOINTS: Waypoint[] = [
  { at: 0, name: 'Ember Canyon', note: 'A deep canyon cut by the Ember River. The trail keeps to the rim for nine miles, then drops to the water.' },
  { at: 29.7, name: 'Mount Hallam', note: 'A granite massif of fins and spires. The trail threads the saddle under its north face, with snow into July.' },
  { at: 52, name: 'Needle Col', note: 'A narrow notch between two spires and the high point of the section. The wind picks up fast after noon.' },
  { at: 57.5, name: 'Giants Grove', note: 'Sequoias older than any map of the range. The trail runs between their trunks for forty miles of shade.' },
  { at: 105.7, name: 'Tarn Meadow', note: 'A string of small lakes in a clearing still ringed by the big trees. The best water on the whole stretch.' },
  { at: 120.7, name: 'Chimney Rock', note: 'A lone stack of dark rock above the dunes. Hikers leave a stone on the cairn at its foot for luck.' },
  { at: 131.7, name: 'Sawtooth Saddle', note: 'A long, sandy ridge between two ranges, warm until late in the evening and cold right after.' },
  { at: 142.7, name: 'Owl Peak', note: 'The last summit before the desert. Camp below it and watch the light leave the hills one by one.' },
];
