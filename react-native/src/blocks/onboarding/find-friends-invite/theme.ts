/**
 * Find Friends Invite — every colour, size and position of the screen.
 *
 * All numbers are points on the pin's 402 pt wide phone (an iPhone 17 Pro, 1206 px at 3×),
 * measured from the rectified pin (`qa/ref-18.png`). `y` is from the top of the screen, status
 * bar included; the component moves it by `insets.top - REF_TOP` on other devices. Widths that are
 * margins (cards, buttons) are insets from the screen edge, so wider phones keep the margins.
 */

export const REF_W = 402;
/** The pin was taken on a phone whose top safe area is 62 pt. */
export const REF_TOP = 62;

/** Anybody's ascent / descent (hhea): the baseline of a Text sits `ASCENT` em under its box top. */
export const ASCENT = 0.795;
export const DESCENT = 0.24;

export const FONT = {
  medium: 'AnybodyNC-Medium', // wght 500, wdth 108
  semi: 'AnybodyNC-SemiBold', // wght 600, wdth 100
  bold: 'AnybodyNC-Bold', // wght 700, wdth 100
  extra: 'AnybodyNC-ExtraBold', // wght 800, wdth 100
} as const;

export const colors = {
  ink: '#000000',
  white: '#FFFFFF',
  card1: '#FFFFFF',
  card2: '#F6F6F7',
  placeholder: '#C4C4C4',
  buttonBlack: '#000000',
  /** The plane's fold line on the button, and the plane's colour once it flies over the white card. */
  planeFold: '#1E5BEA',
  planeInk: '#0262FD',
  /** Page background: a grey that darkens toward the cards and the bottom. y in pt → grey level. */
  pageStops: [
    [0, '#F6F6F6'],
    [67, '#F6F6F6'],
    [100, '#F4F4F4'],
    [133, '#EFEFEF'],
    [200, '#EBEBEB'],
    [267, '#EAEAEA'],
    [350, '#ECECEC'],
    [400, '#EFEFEF'],
    [600, '#EFEFEF'],
    [700, '#EDEDED'],
    [767, '#ECECEC'],
    [833, '#EAEAEA'],
    [874, '#E7E7E7'],
  ] as readonly (readonly [number, string])[],
  /** invite button: blue until 60 %, then violet. position 0..1 → colour. */
  inviteStops: [
    [0, '#0262FD'],
    [0.6, '#0160FE'],
    [0.8, '#2F52FE'],
    [0.9, '#6F3BFF'],
    [0.98, '#9239F1'],
    [1, '#9638EE'],
  ] as readonly (readonly [number, string])[],
  avatarPink: '#F298B9',
  avatarGreen: '#ADEEA9',
  avatarPurple: '#CCC0FB',
  avatarGrey: '#D5D5D8',
} as const;

/** Spacing tokens (4 pt grid where the pin is within 1 pt of it, otherwise the measured value). */
export const space = {
  cardMargin: 9, // screen edge → card
  cardGap: 21, // card 1 bottom → card 2 top (20.7)
  cardPadInvite: 32, // card 1 content inset
  cardPadCheck: 14.7, // card 2 button inset
  buttonGap: 8, // plane → label
} as const;

export const radius = {
  card: 38,
  pill: 24,
  invite: 26.5,
  check: 21,
} as const;

/** Text styles: size in pt, font, extra tracking in pt (the pin's grotesk is wider than Archivo). */
export const type = {
  placeholder: { size: 13.6, font: FONT.medium, ls: 0, color: colors.placeholder },
  h1: { size: 18, font: FONT.extra, ls: 0, color: colors.ink },
  h2: { size: 20.5, font: FONT.extra, ls: 0, color: colors.ink },
  body: { size: 12.35, font: FONT.medium, ls: 0, color: colors.ink, lineHeight: 17 },
  bodyStrong: { size: 12.6, font: FONT.semi, ls: 0, color: colors.ink },
  invite: { size: 15.5, font: FONT.bold, ls: 0, color: colors.white },
  check: { size: 12.85, font: FONT.semi, ls: 0, color: colors.white },
} as const;

/** Boxes in pt. `baseline` is the y of the text's baseline. */
export const box = {
  pill: { x: 23.3, y: 61.3, w: 310.3, h: 48 },
  pillShadow: { dy: 4, blur: 6, opacity: 0.4 },
  close: { cx: 368.2, cy: 85.3, size: 12.4, stroke: 2 },
  placeholder: { x: 38.7, baseline: 90.7 },
  card1: { y: 125.3, h: 274.3 },
  /** Blender render (cup.png, 260×342 px at 3×, tilt baked in), registered to the pin: its frame is pin px (471, 456). */
  cup: { cx: 157 + 260 / 6, cy: 152 + 342 / 6, w: 260 / 3, h: 342 / 3 },
  h1: { baseline: 286.7 },
  invite: { y: 314.7, h: 53 },
  plane: { x: 138.3, y: 333, size: 17.7 },
  inviteLabel: { x: 167.7, baseline: 347.3 },
  card2: { y: 420.7, h: 293.3 },
  /** diameter, centre. Drawn in this order (later on top). */
  avatars: [
    { d: 36.3, cx: 136, cy: 485.2, bg: colors.avatarPink, src: 'h3', head: 0.6, dy: -0.01 },
    { d: 67, cx: 182.7, cy: 470, bg: colors.avatarGreen, src: 'h1', head: 0.62, dy: 0 },
    { d: 49, cx: 236.5, cy: 478.7, bg: colors.avatarPurple, src: 'h2', head: 0.85, dy: 0 },
    { d: 25.5, cx: 271.3, cy: 485.2, bg: colors.avatarGrey, src: 'h5', head: 0.77, dy: -0.04 },
  ],
  h2: { baseline: 548.7 },
  bodyBaselines: [594.7, 611.7, 628.7],
  lock: { x: 228.3, size: 14 },
  check: { y: 657, h: 41.7 },
  checkLabel: { baseline: 683 },
} as const;

export const copy = {
  placeholder: 'search for friends',
  h1: 'have friends with good taste?',
  invite: 'invite them',
  h2: 'find your friends',
  /** Hand-broken to the pin's three lines; "mellow" stands in for the pin's app name. */
  body: [
    [["see who's already on mellow by checking your", false]],
    [['contacts. ', false], ['we never upload or store your', true]],
    [['contacts. ', true]],
  ] as readonly (readonly (readonly [string, boolean])[])[],
  lock: '\u{1F512}',
  check: 'check contacts',
} as const;
