/**
 * Event Pass Share — layout tokens. Points of the pin's 402 pt screen, measured on the pin at 3×
 * (pt = px / 3) and kept to a third of a point. `y` values are absolute screen points; the
 * component maps them with top(y) = safeTop + (y − 62)·S, S = width / 402.
 * Border lines were located by their highlight's centre, text by its glyph box.
 */

export const sheet = {
  top: 61,
  radius: 44,
  handle: { left: 180.5, top: 69, width: 41, height: 4 },
  /** "Share Event" title: 44 pt row shared with the close button. */
  headerTop: 78,
  headerFontSize: 17.4,
  close: { left: 341, size: 44, glyph: 30 },
} as const;

export const link = {
  /** Top of the 44 pt touch target; the 34 pt pill sits centred in it (pill 131 → 165). */
  top: 126,
  hitHeight: 44,
  width: 158,
  height: 34,
  /** Inset of the label and the icon from the pill's outline (pin: 13.5 left, 13 right). */
  padding: 13.5,
  gap: 7.5,
  fontSize: 16,
  icon: 15,
} as const;

export const stage = {
  left: 20.5,
  top: 194,
  width: 361,
  height: 540,
  radius: 14,
  /** Wordmark centre line and the two actions share the stage's bottom band. */
  wordmarkTop: 682,
  actionCenterY: 710,
  copyCenterX: 318.2,
  downloadCenterX: 355,
  actionHit: 44,
  actionIcon: 16.6,
} as const;

/** The frosted card holding the poster and the caption: ~66 pt from each side, 270 × 382 (border centres at 66.7 / 335.7 / 273.3 / 654.7). */
export const pass = {
  left: 66.35,
  top: 273.3,
  width: 270.3,
  height: 382,
  radius: 23,
  /** Poster: ~10 pt from the card's outline (the 1 pt border is inside the card), 250 × 250. */
  poster: { left: 9.1, top: 9.5, size: 250.4 },
  /** Caption block, relative to the card's top-left. Text boxes, not glyph boxes. */
  titleLeft: 16.7,
  titleTop: 272,
  titleWidth: 238,
  titleFontSize: 22.7,
  titleLineHeight: 27,
  dateLeft: 17.3,
  dateTop: 345,
  dateWidth: 210,
  dateFontSize: 13,
  dateLineHeight: 18,
} as const;

export const shareButton = {
  left: 20.5,
  top: 775.5,
  width: 361,
  height: 51,
  fontSize: 18,
} as const;

/**
 * Fallback foil when the poster cannot be read back for the shader (no RuntimeEffect, or the
 * snapshot failed): the green title is drawn again with a static spectral gradient, faded in by the
 * same foil amount, so a tilt or a flip still catches the light.
 */
export const foilFallback = {
  stops: ['#7FD9C2', '#B7A6F2', '#F2B8D0', '#F3E3A0', '#8FD3E8'],
  angle: { x1: 0, y1: 0, x2: 1, y2: 0.6 },
  opacity: 0.85,
} as const;
