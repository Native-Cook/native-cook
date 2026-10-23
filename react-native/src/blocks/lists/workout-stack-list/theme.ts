import { Platform } from 'react-native';

// Measured on the pin's screen (396 x 860 px in the video) scaled to 402 x 874 pt.

export const COLORS = {
  bg: '#0C1217',
  white: '#FFFFFF',
  dim: '#5F666B',
  label: '#8E959B',
  red: '#FE223E',
  dotRed: '#F2273B',
  dotGreen: '#3DDC84',
  grabber: '#4A5257',
  tag: '#E8232F',
};

/** The pin's titles are Futura; it ships with iOS. */
export const FUTURA = Platform.select({ ios: 'Futura-Bold', default: 'sans-serif-medium' });
export const FUTURA_MEDIUM = Platform.select({ ios: 'Futura-Medium', default: 'sans-serif' });

export const FONT = {
  regular: 'WorkoutPoppins-Regular',
  medium: 'WorkoutPoppins-Medium',
  semibold: 'WorkoutPoppins-SemiBold',
};

export const HEADER = { top: 66, side: 22, icon: 24, avatar: 26 };

/** The category deck: a card's top, scale and title offset by its distance from the front (u = 0). */
export const DECK = {
  w: 391,
  h: 330,
  radius: 26,
  /** Futura Bold at the front card; back cards scale it with the card and by `titleScale`. */
  titleSize: 48,
  /** Ink top of a title below its text box top, as a share of the font size (Futura Bold). */
  titleAscent: 0.2,
  u: [-1, 0, 1, 2, 3, 4, 5],
  top: [930, 610.7, 445, 288.7, 170.7, 92.7, 40],
  scale: [1.05, 1, 0.919, 0.865, 0.781, 0.647, 0.5],
  /** Title ink top below the card top, in screen points. */
  title: [84.3, 84.3, 97.3, 89.6, 66.3, 37.6, 20],
  titleScale: [1, 1, 1, 0.946, 0.897, 0.866, 0.85],
  opacity: [0, 1, 1, 1, 1, 1, 0],
  /** Photos are framed larger than the card, centred, from just above the head. */
  photo: { zoom: 1.6, top: -45 },
  /** Shade over each card by depth (the cards in front cast it). */
  fog: [0, 0, 0.15, 0.3, 0.38, 0.45, 0.5],
};

export const TABBAR = {
  top: 759,
  left: 18,
  w: 366,
  h: 91,
  radius: 20,
  /** Icon centres (pt) and the row's centre line; the dot marks the current tab. */
  x: [67.5, 129, 200, 270, 332],
  iconY: 794,
  dotY: 813.5,
  icon: 23,
  plus: 45,
  plusIcon: 18,
  ink: '#3B3122',
  plusInk: '#9A9A9A',
  wash: 'rgba(255,248,226,0.38)',
  solid: 'rgba(250,236,200,0.94)',
  blur: 30,
};

export const DETAIL = {
  hero: { h: 412, scrollAway: 430 },
  title: { top: 372, inkTop: 368.3, left: 19, size: 42, tracking: 1.2 },
  week: { letters: 121, numbers: 158, collapsedShift: 330, side: 14, selected: 40, radius: 11, letterSize: 14, numberSize: 15 },
  monthRow: 57,
  grabber: { top: 190, w: 30 },
  /** Card list when scrolled: tops of the four class cards and the section label. */
  list: { liveRest: 545, liveTop: 215, liveScale: 0.867, quizTop: 245, labelTop: 452, mondayTop: 485, morningTop: 785, side: 23, radius: 16 },
  /** The same cards stacked under the month grid. */
  stack: { tops: [407, 482, 562, 683], scales: [0.8, 0.86, 0.92, 0.97] },
  cards: { quizH: 178, mondayH: 275, morningH: 275, liveH: 180 },
  /** Unscrolled, the cards after the live card sit this much lower. */
  listScroll: 501,
};
