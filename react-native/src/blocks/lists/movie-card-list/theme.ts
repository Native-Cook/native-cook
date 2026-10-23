// Measured from the pin (phone straightened, screen scaled to 402 x 874 pt).
// The pin's rounded geometric sans; Poppins (OFL) is bundled.
export const SANS = { regular: 'MCL-Poppins', medium: 'MCL-Poppins-Medium', semibold: 'MCL-Poppins-SemiBold' } as const;
export const DISPLAY = 'MCL-Anton';
export const SERIF_DISPLAY = 'MCL-DMSerifDisplay';

export const COLORS = {
  paper: '#FFFFFF',
  ink: '#151515',
  inkSoft: '#4A4A4A',
  inkMute: '#9A9A9A',
  chip: '#C9C9C9',
  star: '#E8423F',
  starOff: '#C4C4C4',
  button: '#262626',
  night: '#000000',
  seatFree: '#E9E9EE',
  seatTaken: '#3A3A3E',
  seatPicked: '#E8262B',
  red: '#E8262B',
  redHot: '#F2552F',
  legend: '#B9B9BE',
} as const;

export const TOP = { iconY: 60.8, iconX: 21.8, icon: 24.4, targetX: 377.7, targetY: 61, target: 26.2 }; // centres and sizes measured on the pin rectified from its screen edges

export const CAROUSEL = {
  cardW: 291, // re-measured 2026-10-15 on the pin rectified from its own screen edges (card 53.4-344.6 pt)
  cardTop: 245,
  cardH: 640,
  cardRadius: 43,
  posterInset: 35, // poster 88.5-309.5 pt
  posterTop: 27,
  posterH: 326, // 272.3-598.2 pt; 221 x 326 is the key art's own 27:40
  posterRadius: 26,
  pitch: 287, // side cards' near edges at 35 / 362 pt
  sideScale: 0.85,
  sideWash: 0.85,
  backdropH: 600,
  fadeFrom: 0.5, // backdrop melts into white from here
  titleY: 639,
  titleSize: 30,
  chipsY: 673.3,
  chipH: 22,
  chipSize: 9.5,
  ratingY: 703.4,
  dotsY: 733.8,
  buttonY: 800.1,
  buttonW: 254.7,
  buttonH: 58.8,
};

export const DETAIL = {
  sheetTop: 257,
  contentX: 33, // the sheet's text and cast start here (measured)
  sheetInset: 10,
  sheetRadius: 28,
  posterTop: 79, // top edge of the centre poster once open (measured): the posters gather at the top, behind the sheet
  posterScale: 0.94, // centre poster 194 pt wide on the pin
  sideScale: 0.65, // the neighbours sit smaller and lower once open (their tops ~175 pt on the pin)
  pitch: 0.565, // fraction of the carousel pitch between posters once open
  titleY: 87, // from the sheet top
  chipsY: 122,
  ratingY: 152,
  directorY: 174,
  actorsLabelY: 240,
  actorsTop: 258,
  actorW: 100,
  actorH: 110,
  actorGap: 18,
  actorRadius: 10,
  introLabelY: 445,
  introTop: 466,
  buttonY: 521, // centre, from the sheet top
  buttonW: 300,
  buttonH: 52, // the sheet's button is longer and thinner than the card's (5.7:1 on the video)
};

export const SEATS = {
  screenTop: 112,
  screenH: 128,
  screenInset: 26,
  gridTop: 313.5, // rows centred 328 + 28 n on the rectified pin (homography from PAY, the time chip and the two top icons)
  seat: 20,
  pitchX: 26.3,
  pitchY: 28,
  aisle: 32.4, // one aisle, before the right-hand pair (centre gap 58.7 on the pin)
  legendY: 622,
  dateY: 690,
  payY: 774,
  payW: 300,
  payH: 52,
};

