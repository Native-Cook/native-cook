// Measured from the pin: the phone screen is 600 x 1300 px in the video (1 px = 0.67 pt).
export const colors = {
  bg: '#FCFCFC',
  ink: '#111111',
  mute: '#707070',
  meta: '#555555',
  cta: '#00CC46',
  ctaInk: '#FFFFFF',
  second: '#F7F7F7',
  legal: '#7A7A7A',
  splashMark: '#E2E2E2',
  card: '#FFFFFF',
};

export const title = { top: 140, height: 112, size: 37.7, line1: 51.7, line2: 94 };

// Card geometry at the call-to-action rest (the title state is the same row 185 pt lower).
export const card = {
  width: 214,
  height: 385,
  radius: 22,
  inset: 5,
  imageHeight: 230,
  imageRadius: 18,
  icon: 125,
  glowR: 96, // the light behind the icon
  glowBlur: 26,
  iconRadius: 30,
  pad: 17,
  believeTop: 252.6,
  nameTop: 276.3,
  dateTop: 323.7,
  byTop: 350.7,
};

export const row = {
  topCta: 139.6,
  shiftTitle: 185, // the row sits this much lower before the buttons come in
  titleTravel: 0.5, // the headline rises at half the row's speed while it fades
  // centre x of the card at p = -1, 0, 1 (the row is not centred in the pin)
  x: [-34, 180.4, 389.3],
  y: [15.6, 0, 29.4],
  scale: [0.985, 1, 1],
  rotate: [2.5, -3, 1.7],
  // The pin's centre picture (not its text) is drawn in perspective: wider at the top (647 px)
  // than at the bottom (586 px), its right side taller (681 vs 657 px). Measured corners, fitted here.
  picture: { dx: 2, dy: 6.2, perspective: 800, rotate: -2.6, rotateX: -19, rotateY: -11, skewX: -5.5, scale: 1.08 },
  // the pin's cards slide their content inside the frame (parallax with the row), measured per card
  iconShift: [35, 6, 15],
  textShift: [45, 0, -10],
  textDy: [-4, 0, 4],
};

export const cta = { top: 652.6, height: 56, side: 25, gap: 13.4, legalTop: 803 };

export const splash = { width: 90, height: 105, centerY: 435 };

// The pin is an iOS app set in SF Pro; Inter (and Inter Display for the headline) is its
// closest open equivalent, bundled so every weight matches.
export const fonts = {
  regular: 'BelieveInter-Regular',
  medium: 'BelieveInter-Medium',
  semibold: 'BelieveInter-SemiBold',
  extrabold: 'BelieveInter-ExtraBold',
};
