// Glass Pay Morph — layout, colour and copy. Reference screen 402 × 874 pt (iPhone 17 Pro).
//
// Measured on the pin's rest frame (`frames/t0.00.jpg`, 1440 × 1040 px = 2× the clip's window):
// 0.3275 pt per frame px, so the two discs span 5 → 397 pt; the card's centre is the hero centre.

export const REF_W = 402;
export const REF_H = 874;

/**
 * The stage (card, lights, pill, pulse, every motion path) is one group, measured on the pin around a
 * card centre at y 372 and moved down by STAGE_DY so the stage and its captions sit balanced on the
 * portrait screen under the top bar. Nothing is scaled.
 */
export const STAGE_DY = 44;

/** Hero centre: the card's centre at rest. Motion offsets are measured from it. */
export const HERO = { x: 207, y: 372 + STAGE_DY };

export const C = {
  page: '#1B2135',
  // the light colours at rest (a gradient from upper-left to lower-right across each disc)
  amberHi: '#F4BB58',
  amberLo: '#EEA635',
  redHi: '#E5284F',
  redLo: '#D60433',
  // the same lights docked as the mark on the pill (flat, a touch deeper)
  amberDock: '#E89F1F',
  redDockL: '#D6002B',
  redDockR: '#DE0B3A',
  lensCore: '#FFB23A',
  ink: 'rgba(255,255,255,0.92)',
  inkMute: 'rgba(255,255,255,0.5)',
};

/** Card at rest (card-local pt, origin = its top-left before the roll). */
export const CARD = {
  w: 282,
  h: 168,
  r: 15,
  roll: -3,
  stroke: 1.3,
  contactless: { x: 21, y: 38, radii: [4.2, 7.7, 11.2], spread: 0.86 },
  chip: { x: 43.5, y: 23.5, s: 29.5, r: 8.5 },
  // the Solune mark as two rings: the large one (amber's place) and the small one on its upper-left
  rings: { big: { x: 229, y: 38.5, r: 15.5 }, small: { x: 212, y: 32.9, r: 10.6 } },
  number: { x: 37.5, base: 110, size: 18.4, gap: 8.4 },
  name: { x: 37.5, base: 135, size: 12, tracking: 0.4 },
};

/** The pill (state 2) and the mark docked on it, absolute pt. */
export const PILL = { x: 201, y: 372 + STAGE_DY, w: 192, h: 123, amber: { x: 215, y: 374 + STAGE_DY, r: 44 }, red: { x: 167, y: 358 + STAGE_DY, r: 30 } };

export const COPY = {
  brand: 'solune',
  last4: '•••• 4417',
  number: ['8104', '2267', '5390', '4417'],
  name: 'NOA LINDGREN',
  ready: ['Hold near reader', 'or tap the card'],
  paid: ['Paid $24.80', 'Juniper & Rye · 09:41'],
};

/** Where the mark's lens sits on the pill (absolute pt): the number's dots run into it. */
export const LENS = { x: 184, y: 364 + STAGE_DY };

/** U6: the number condensing into the lens, and rolling back in (loop s). */
export const NUMBER_FX = { condense0: 1.04, stagger: 0.025, lastHold: 0.05, condense: 0.13, run: 0.12, roll0: 3.31, rollStagger: 0.008, roll: 0.13 };

/** Screen chrome. `top` is below the safe area. */
export const CHROME = {
  side: 24,
  brandSize: 18,
  last4Size: 14,
  markR: 6,
  barTop: 22, // baseline of the top row, from the safe area's top
  // 76 pt below the lowest resting light (red, bottom ≈ y 554 with its drift) to the first caption's
  // cap height; clear of every phase's light paths (lowest: the red bean at 0.73, ≈ y 578)
  cap1: { base: 642, size: 17 },
  cap2: { base: 666, size: 14 },
  rise: 6,
};

/** Perspective distance of the turn, pt. */
export const PERSPECTIVE = 900;
