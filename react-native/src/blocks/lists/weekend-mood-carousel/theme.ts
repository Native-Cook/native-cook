import { Platform } from 'react-native';

// Every value is measured on the pin's screen scaled to 402 pt wide (1 px = 1.0075 pt).

export const SCREEN_W = 402;

export const COLORS = {
  ink: '#101718',
  white: '#FFFFFF',
  buttonInk: '#16191A',
  buttonRing: 'rgba(0,0,0,0.4)',
  chipRing: 'rgba(255,255,255,0.3)',
  chipText: '#F2F4F4',
  chipTextSelected: '#24201F',
  faceInk: '#1C1B1F',
};

export const PANEL = {
  height: 461,
  radius: 13.5,
};

export const TOP_BUTTONS = {
  top: 77,
  size: 54,
  backLeft: 22,
  saveRight: 20,
  saveWidth: 84,
  arrow: 18,
  check: 14,
  /** Save / Saved labels slide this far as they swap. */
  labelTravel: 8,
  saveFont: 15,
  saveWeight: '400' as const,
  ring: 0.67,
  arrowStroke: 1.7,
  arrowPath: 'M16 9H2.5M8.5 2.8 2.3 9l6.2 6.2',
};

/** Character carousel: where each character's centre sits along the strip (pt), measured pair by pair on the pin. */
export const CHARACTER = {
  positions: [0, 326, 614, 910, 1182, 1456, 1794],
  /** Half-width used to skip characters that are off screen. */
  box: 400,
  /** Average spacing, used to turn a finger's travel into carousel travel. */
  spacing: 292,
};

/** Times New Roman Bold is the pin's face and ships with iOS; SF is its UI face. */
export const FONTS = {
  title: Platform.select({
    ios: 'TimesNewRomanPS-BoldMT',
    default: 'WeekendLiberationSerif-Bold',
  }),
  titleBundled: 'WeekendLiberationSerif-Bold',
  /** The pin's chips are set in a Myriad-like humanist sans; Source Sans 3 is its open twin. */
  chip: 'WeekendSourceSans3',
  ui: Platform.select({ ios: 'HelveticaNeue', default: 'sans-serif' }),
};

export const TITLE = {
  top: 511.3,
  /** The pin's centred title sits 2.7 pt left of the screen centre. */
  shiftX: -2.7,
  fontSize: 47,
  lineHeight: 45,
  letterSpacing: 0,
};

export const RULER = {
  top: 626,
  height: 104,
  pitch: 12.25,
  tickWidth: 2,
  tickSoftness: 0.9,
  baseline: 87, // tick bottoms, from RULER.top
  indicatorTop: 6.7,
  indicatorBottom: 95.7,
  indicatorWidth: 2.5,
  /** Tick height by distance from the centre, in pitches (d = 0 sits under the indicator). */
  heights: [82, 77.3, 67.7, 59.7, 51.7, 44.7, 38.3, 33.3, 28.3, 22.7, 19, 16, 11.7, 7.3, 3, 1, 0],
  /** White alpha at a tick's top by distance; each tick fades to nothing at its foot. */
  alphas: [0.3, 0.24, 0.23, 0.2, 0.19, 0.17, 0.15, 0.13, 0.11, 0.1, 0.08, 0.07, 0.06, 0.04, 0.03, 0.01, 0.01],
};

export const CHIPS_ROW = {
  top: 760,
  height: 49,
  /** The selected chip's centre (the pin's strip sits 4 pt right of the screen centre). */
  centerX: 201,
  gap: 10,
  minWidth: 111,
  padding: 16,
  border: 1,
  fontSize: 16.4,
  weight: '400' as const,
  letterSpacing: -0.4,
};
