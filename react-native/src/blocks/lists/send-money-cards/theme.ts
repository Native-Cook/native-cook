// Every number measured on the pin's frames rectified to the 402 × 874 pt screen (docs/lists-qa/ref.py rectify).
// Colours are sampled from the same frames, so they include the camera's exposure.
// Vertical values are for an iPhone with the Dynamic Island at y 11 (safe top 62);
// `islandOffset()` moves the whole layout with a different safe area.

export const COLOR = {
  screen: '#D5DAE6',
  ink: '#121214',
  inkSoft: '#3C3C43',
  muted: '#6E6E76',
  chip: '#C3C6D0',
  row: '#C2C5D2',
  rowIcon: '#B4B5C1',
  blue: '#0016E9',
  blueLift: '#003BEF',
  blueText: '#0A2CF0',
  cardChip: '#B6CFE5',
  white: '#FFFFFF',
  black: '#000000',
  pillMuted: '#B6B6BC',
  sheetMuted: '#8E8E93',
  addNew: '#E9E9EE',
} as const;

export const SCREEN = { width: 402, height: 874, safeTop: 62 } as const;

/** The black "Send to" pill that wraps the Dynamic Island at rest. */
export const PILL = { width: 305, height: 45, top: 8, radius: 22.5, padX: 19.3, padRight: 16, avatar: 28, avatarStep: 18 } as const;
/** The hardware island the pill shrinks back to while the sheet is open. */
export const ISLAND = { width: 126, height: 37, top: 11 } as const;
/** The tab the pill grows under itself while the card is held. */
export const TAB = { width: 184, depth: 35, overlap: 22, radius: 36, arrowY: 61, bottom: 87, arrowFollow: 0.33 } as const;

export const HEADER = { buttonY: 97.7, button: 48, inset: 48, gridGlyph: 21, plusGlyph: 20 } as const;

export const CARD = {
  inset: 50, top: 163, height: 275, radius: 24,
  padX: 20, padRight: 17.5, validRight: 18, brandY: 45, chipY: 89, chip: { w: 26, h: 18, r: 3 },
  numberY: 120, last4X: 112.3, dots: { x: 19.6, cy: 120.9, size: 2.7, pitch: 7, groupPitch: 31.1 }, validY: 93, nameY: 158, actionY: 210, action: 46, actionGlyph: 22, labelGap: 4.4,
} as const;

export const HINT = { y: 109 } as const;

export const LIST = {
  titleY: 494, top: 573, rowHeight: 60, pitch: 78, inset: 49, icon: 48, iconPad: 5, nameGap: 9, padRight: 10, glyph: 23,
  arrowButton: 46, arrowX: 330.5, arrowY: 521, arrowGlyph: 24,
} as const;

/** Black send sheet, inset inside the screen with corners concentric to the display's. */
export const SHEET = {
  left: 30, right: 26, top: 79, bottom: 16, radius: 44,
  titleX: 60.4, titleY: 119, closeX: 70.3, closeY: 133, closeGlyph: 18,
  avatarsY: 255.7, avatar: 39, avatarX: 79.5, avatarStep: 76, addNewX: 307.5,
  contactsTitleY: 340, contactsTop: 446.5, contactPitch: 59.6, contactX: 79.2, nameGap: 15.1,
  buttonX: 62, buttonWidth: 283, buttonTop: 753, buttonHeight: 52,
}

/** The sheet's content, timed on the pin (ms into the bloom / the fold): it appears small and grows, and shrinks before it goes. */
export const SHEET_GROW = {
  originY: 30, // scaled about the island
  open: { fadeFrom: 60, fadeTo: 150, scaleFrom: 0.5, scaleStart: 40, scaleEnd: 230 },
  // Closing, the finger drags the content up with the sheet: scaled about a point above the screen
  // (pin 10.46 s: "Money To" 188 → 120 pt, first contact 446 → 334 pt, x pulled toward the centre).
  close: { fadeFrom: 150, fadeTo: 250, originY: -212, t: [0, 53, 187, 290], scale: [1, 0.83, 0.55, 0.45] },
} as const;

export const FONT = {
  pill: 15.5,
  hint: 15.3,
  brand: 21,
  balance: 34.8,
  number: 16,
  valid: 15,
  tiny: 8,
  action: 11.7,
  sectionTitle: 24,
  sectionLead: 25, // "Last" is set a touch larger and lighter in the pin // SF (the system face): the pin sets this heading in SF Pro, the rest in Poppins
  rowName: 14,
  rowMeta: 12,
  rowAmount: 11,
  sheetTitle: 41.2,
  sheetTitleLine: 46.7,
  contactsTitle: 22,
  contactsLine: 27,
  contact: 14,
  label: 12,
} as const;

/** Rows are laid out for the 874 pt screen; on a shorter one they simply run under the bottom edge. */
export function islandOffset(safeTop: number) {
  return safeTop > 0 ? safeTop - SCREEN.safeTop : -40;
}

/** Poppins (OFL, bundled): the pin's typeface. */
export const FAMILY = {
  regular: 'SMC-Poppins-Regular',
  medium: 'SMC-Poppins-Medium',
  semibold: 'SMC-Poppins-SemiBold',
} as const;
