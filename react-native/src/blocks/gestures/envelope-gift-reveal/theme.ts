/** Colours, type and layout measured from the source (402 pt wide). */
/** Wide display face for the headlines; the file and its licence are in assets/. */
export const DISPLAY_FONT = 'EnvelopeArchivoExpandedBlack';

export const COLORS = {
  lime: '#DAFF1C',
  limeHigh: '#F0FF72',
  limeLow: '#CCFA14',
  limeEdge: '#B2DB00',
  limeInside: '#A9CF00',
  /** Envelope paper, sampled from the source frame. */
  flapHigh: '#EAFF66',
  flapLow: '#CEFF14',
  sideHigh: '#DFFF30',
  sideLow: '#D5FF1B',
  sideRight: '#CBFF0D',
  bottomHigh: '#D4FF22',
  bottomLow: '#CCFF18',
  crease: 'rgba(120,160,0,0.28)',
  ink: '#0A0A0A',
  white: '#FFFFFF',
  sealMark: '#D4FF1C',
  message: '#FFFFFF',
  messageInk: '#151515',
  shadow: 'rgba(60,80,0,0.2)',
  cardPink: '#F2A9BC',
} as const;

/**
 * Spacing scale (step 0, 20 Oct), derived from the source's measured gaps: 4 pt base; 8 = the headline's minimum
 * gap under the status bar, 20 = the revealed screen's side margin, 32 ≈ the sealed screen's side (33) and the CTA
 * gap (34). Source elements keep their measured positions; the upgrade's additions (float, text rise, letter drop)
 * use this scale.
 */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, side: 20, xl: 24, xxl: 32 } as const;

export const LAYOUT = {
  side: 33,
  /** Wide extended black face (Archivo Expanded, OFL), sized as measured on the reference. */
  headline: 54,
  headlineLine: 50,
  headlineTop: 112,
  title: 38,
  titleLine: 36,
  envW: 336,
  envH: 258,
  envTop: 260,
  sealD: 60,
  sealY: 0.66,
  flapTip: 0.7,
  /** ~40 % of the screen width, as in the source. */
  buttonD: 134,
  buttonCentre: 638,
  cardW: 360,
  cardH: 186,
  cardR: 14,
  cardInScale: 0.9,
  titleGap: 30,
  finalTop: 125,
  finalSide: 20,
  cardGap: 106,
  ctaGap: 34,
  messageH: 125,
  buttonH: 52,
} as const;

/**
 * Vertical positions for this screen. Sealed: headline under the status bar, envelope under it,
 * the button centred in the space left below. Revealed: title, card, message and button as one
 * group placed as measured on the reference.
 */
export function screenLayout(height: number, top: number, bottom: number) {
  // Measured on the reference for an 874 pt screen; taller screens keep it centred.
  const shift = (height - 874) / 2;
  const headlineTop = Math.max(top + 8, LAYOUT.headlineTop + shift);
  const envTop = LAYOUT.envTop + shift;
  const buttonY = LAYOUT.buttonCentre + shift;
  // Measured on the reference (title ~160 pt from the top on an 874 pt screen); centred on taller ones.
  const titleTop = Math.max(top + 8, LAYOUT.finalTop + (height - 874) / 2);
  const cardTop = titleTop + LAYOUT.titleLine * 2 + LAYOUT.titleGap;
  const messageTop = cardTop + LAYOUT.cardH + LAYOUT.cardGap;
  const ctaTop = messageTop + LAYOUT.messageH + LAYOUT.ctaGap;
  return { headlineTop, envTop, buttonY, titleTop, cardTop, messageTop, ctaTop };
}
export type ScreenLayout = ReturnType<typeof screenLayout>;

export const GIFT = {
  amount: '$25',
  value: 25,
  message: 'Happy spring! From all of us at Petal :)',
  cta: 'Start shopping',
  title: 'gift\ncard',
  headline: 'wow,\na gift!',
} as const;
