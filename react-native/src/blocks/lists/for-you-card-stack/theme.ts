import { Platform } from 'react-native';

// Measured on the pin's screens, rectified from the hand-held video to 402 x 874 pt.

export const COLORS = {
  page: '#ECD7D4',
  ink: '#161010',
  placeholder: '#CDB9B5',
  design: '#4E42F8',
  nextCard: '#E9488F',
  grabber: 'rgba(255,255,255,0.4)',
  player: '#4A44F5',
  reading: '#C26CF2',
  white: '#FFFFFF',
  dim: 'rgba(255,255,255,0.4)',
  faint: 'rgba(255,255,255,0.28)',
  textUnread: 'rgba(255,255,255,0.42)',
};

export const FONTS = {
  poster: 'ForYouAnton',
  /** The pin's UI face: Inter (SIL OFL), matched by glyph widths. */
  medium: 'ForYouInter-Medium',
  regular: 'ForYouInter-Regular',
  heavy: 'ForYouInter-ExtraBold',
  serif: Platform.select({ ios: 'Georgia', default: 'serif' }),
  reading: Platform.select({ ios: 'Charter-Bold', default: 'serif' }),
};

/** Covers are designed once at this size and scaled. */
export const COVER = { w: 200, h: 300 };

export const HOME = {
  titleTop: 81.6,
  titleLeft: 28.6,
  titleSize: 29.4,
  avatar: 52,
  avatarTop: 75,
  avatarRight: 30,
  searchTop: 160,
  searchLeft: 34,
  searchIcon: 21,
  searchText: 15.4,
  placeholderLeft: 74,
  placeholderTop: 162,
  card: { top: 240, height: 600, radius: 32 },
  nextCard: { top: 830 },
  heading: { top: 274.7, left: 28, size: 29.4 },
  grabber: { top: 288.5, right: 30, width: 40 },
  label: { size: 13.7, newTop: 328.7, chartsTop: 586.4, left: 29, right: 34, tracking: 0 },
  row: { top: 366, coverW: 124.3, coverH: 186, gap: 11.3, deckLeft: 40.7 },
  /** Cards waiting in the deck, peeking out to the left of the front card. */
  deck: { peek: 9.5, inset: 4, count: 4 },
  charts: { top: 625, left: 28, step: 132 },
};

export const PLAYER = {
  back: { left: 36.7, top: 83.3, w: 13, h: 12.7 },
  bookIcon: { left: 350, top: 83.3, w: 16.7, h: 14.7 },
  /** The full-size book: page box, spine (left edge) and vertical centre. */
  page: { w: 190, h: 285 },
  spineX: 100,
  centerY: 291.5,
  neighbour: { scale: 0.87, leftEdge: 22, rightEdge: 348 },
  author: { top: 477.8, size: 12.4 },
  title: { top: 494.1, size: 27, lineHeight: 40 },
  chapter: { top: 576.6, size: 13 },
  controls: { y: 686, play: 46, skipX: 57, icon: 22 },
  progress: { y: 800, left: 34.7, right: 35, played: 0.012, timesTop: 804, timeSize: 9.5 },
};

export const READING = {
  /** Wave boundary: y at the left edge, the quadratic control point and y at the right edge. */
  wave: { left: 443, cx: 230, cy: 412, right: 430 },
  text: { top: 141, left: 36.6, size: 19.3, lineHeight: 33, dim: 0.42 },
  /** The book while reading: scale against the full size, spine x and centre y. */
  book: { scale: 0.4737, spineX: 200, centerY: 442.5 },
  /** The open spread is centred on this spine x while it grows back. */
  spreadSpineX: 200,
  titleShift: 64,
};
