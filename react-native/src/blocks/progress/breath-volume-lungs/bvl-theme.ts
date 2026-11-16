// Breath Volume Lungs — every colour, size and string, measured on the pin.
//
// The pin is a 9 s brand reel; its "Expiratory Volume" screen is on screen for about 1.5 s, tilted and half covered, and the
// same screen is the hero of the studio's case-study still (Pinterest 969540626070638090). Both were rectified to the
// 402 × 874 pt reference screen (iPhone 17 Pro) by fitting a homography to the screen's own dot lattice (1328 dots, mean
// residual 1.5 px); the numbers below are read off that rectified image (.work/breath-volume-lungs/design.md).
//
// The screen is one square lattice of dots, 32 columns across (pitch W / 32 = 12.5625 pt on the reference screen). The
// lung is lit cells of that lattice; the digits and the pile sit on it too, the digits on a lattice of 0.4657 × that pitch.
// Heights are in lattice rows, so the instrument keeps its proportions on every phone.

export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;

/** Lattice columns across the screen (the pin: 32 dots between the screen edges, centred). */
export const COLS = 32;
/** The digits' own lattice, as a fraction of the dot lattice's pitch (5.85 pt / 12.5625 pt on the pin). */
export const DIGIT_PITCH = 0.4657;

/** The shared data texture covers lattice rows J0 … J0 + ROWS − 1 (row j of the pin's measurements = rest of the cloud 7…20). */
export const J0 = 2;
export const ROWS = 36;
/** The cloud's first lit row on the pin (its top edge sits at `heroTop`). */
export const J_CLOUD = 7;

export const ref = {
  color: {
    ground: '#030305', // the pin's screen is #000 on the photo; a touch of blue-black keeps OLED smear out
    grid: '#08080C', // additive on ground: lum 11 / 255 on the pin
    dot: '#F4F4F8', // lit lung cells (pin: saturated 255)
    digit: '#FBFBFD', // the readout's dots
    halo: '#9A9AC8', // grid cells lit by proximity to the lung (addition 4)
    title: '#F7F7FA',
    label: '#D8D8E2', // "Liter"
    // card (sampled on the rectified case-study still: rim #675D99, side #32336C, centre #02031A, bottom-left #484382)
    cardBase: '#02031A',
    cardRim: '#8A80E0', // the 1 pt lilac rim on the top edge (concept #7C6CF0, lifted to hold 3:1 on the glow)
    cardGlow: '#4A44A6', // glow tone at the edges before heat
    cardText: '#F3F3FA',
    cardDim: '#8089DC', // "CO2" / "O2": 4.6 : 1 on the card's deep centre
    knobLight: '#CFCFD6',
    knobHigh: '#F4F4F8',
    knobDark: '#17171F',
    knobRing: '#D5D5E8',
    line: '#D8E270', // the yellow-green line (pin #C3D26E at the dot, #818955 mid-line)
    track: '#8C93E0',
    pill: '#B4BBFF',
    xDisc: '#E9E9F0',
    xInk: '#1F1F2B',
    reset: '#191B38',
    finish: '#1B1C37',
    // heat ramp (OKLCH): indigo → magenta → ember (concept / pin's other cards)
    heat0: '#3A2F8F',
    heat1: '#B5308F',
    heat2: '#E0382B',
  },
  /** dot radii in pt on the reference screen (rectified area of 1328 dots) */
  dot: { core: 1.28, edge: 0.8, grid: 0.95, digit: 2.15 },
  font: { title: 28.5, label: 13.5, card: 15, cardSmall: 11.5, caption: 15, pill: 15 },
  /** the type is Urbanist (OFL), the pin's soft geometric face at weights 400–500 */
  tracking: { title: -0.012, card: 0 },
} as const;

export const COPY = {
  title: 'Expiratory Volume',
  unit: 'Liter',
  caption: 'Empty your lungs',
  captionDone: 'Well done',
  finish: 'Finish Test',
  restart: 'Start again',
  left: { value: '0.00', label: 'Seconds' },
  right: { value: '0.00', label: 'L/s' },
  a11yLabel: 'Expiratory volume',
  a11yHint: 'Drag the knob, or press and hold, to breathe out. Double tap to play a full exhale.',
  a11yStart: 'Start a full exhale',
} as const;

export const sys = {
  surface: { page: ref.color.ground },
  ink: { primary: ref.color.title, secondary: ref.color.label },
  hero: { dot: ref.color.dot, grid: ref.color.grid },
  font: { regular: 'BVLUrbanist', medium: 'BVLUrbanistMedium' },
} as const;

/** The card, relative to the reference screen (pt). */
export const card = {
  x: 8,
  y: 581,
  w: 386,
  h: 271,
  r: 50,
  inset: 44, // 0.24 / 1.13 from the card's side edge
  leftTop: 51, // baselines, from the card's top
  labelTop: 72,
  captionTop: 75,
  iconTop: 46,
  iconPitch: 3.5,
  dotMark: {
    cx: 204,
    pitch: 5.6,
    r: 1.35,
    ys: [37.1, 42.8, 47.5, 53.1],
    xs: [[0], [-0.5, 0.5], [-1.5, -0.5, 0.5, 1.5], [-1.5, -0.5, 0.5, 1.5]],
  },
  knob: { x0: 88, x1: 338, y: 716, r: 17, ring: 20.5 },
  lineLen: 93,
  hair: { x: 32 },
  track: { end: 375, step: 6.6 },
  pill: { x: 137, w: 194, y: 785, h: 58 },
  refresh: { cx: 103, cy: 814, r: 30 },
  xDisc: { cx: 301, cy: 814, r: 18 },
  controlsGap: 4,
  iconDrop: 3, // optical centres from the source; controls stay above the home indicator
} as const;

/** Positions in lattice rows from the top of the cloud (row J_CLOUD) — the pin's rest frame. */
export const hero = {
  digitCy: 16.86, // 362.5 pt on the reference screen
  labelBase: 20.8, // 412 pt
  pileBase: J_CLOUD + 29, // j = 36, the pile's bottom row
};

export type Layout = {
  W: number;
  H: number;
  S: number; // pt per reference pt (width based)
  pitchX: number;
  pitchY: number;
  sy: number; // vertical squeeze of the lattice on short phones (1 = square)
  safeBottom: number;
  heroTop: number; // y of the top edge of row J_CLOUD
  rowY: (j: number) => number; // y of the centre of lattice row j
  title: { cx: number; base: number; size: number };
  digits: { cx: number; cy: number; dx: number; dy: number; r: number };
  label: { cx: number; base: number; size: number };
  card: { x: number; y: number; w: number; h: number; r: number; k: number };
};

/** Lay the instrument out for a W × H screen with the given safe-area insets. */
export function makeLayout(W: number, H: number, safeTop: number, safeBottom = 0): Layout {
  const S = W / REF.w;
  const pitchX = W / COLS;
  const titleBase = safeTop + 49.5 * S;
  // the card hugs the bottom; on short phones its type stays and its padding gives a little
  const k = Math.max(0.9, Math.min(1, (H - 140 * S) / (REF.h - 140)));
  const cardH = card.h * S * k;
  // Glass can extend into the bottom safe area; the controls are independently clamped above it.
  const cardBottom = H - 22 * S;
  const cardTop = cardBottom - cardH;
  const heroTop0 = titleBase + 39.2 * S; // ref: 150.7 − 111.5
  const natural = 30 * pitchX; // rows J_CLOUD … pileBase
  const avail = cardTop - 24 * S - heroTop0;
  const sy = Math.max(0.45, Math.min(1, avail / natural));
  const pitchY = pitchX * sy;
  const used = 30 * pitchY;
  // spare height (taller phones) is shared out, the pin's own spare (29 pt) stays below the pile
  const spare = Math.max(0, avail - used - 29.3 * S);
  const heroTop = heroTop0 + spare * 0.5;
  const rowY = (j: number) => heroTop + (j - J_CLOUD + 0.5) * pitchY;
  const dp = DIGIT_PITCH;
  return {
    W,
    H,
    S,
    pitchX,
    pitchY,
    sy,
    safeBottom: Math.max(0, safeBottom),
    heroTop,
    rowY,
    // Optical offsets measured on the pin at 1206 px: the title sits 3 pt lower, the readout 2 pt and its label 3 pt right.
    title: { cx: W / 2, base: titleBase + 3 * S, size: ref.font.title * S },
    digits: { cx: W / 2 + 2 * S, cy: heroTop + hero.digitCy * pitchY, dx: pitchX * dp, dy: pitchY * dp, r: ref.dot.digit * S },
    label: { cx: W / 2 + 3 * S, base: heroTop + hero.labelBase * pitchY, size: ref.font.label * S },
    card: { x: card.x * S, y: cardTop, w: W - 2 * card.x * S, h: cardH, r: card.r * S, k },
  };
}

export const comp = { card, hero };

/** Shader colours are plain sRGB triples, converted once at the output on iOS. */
export function rgb(hex: string): [number, number, number] {
  const s = hex.replace('#', '');
  const h =
    s.length === 3
      ? s
          .split('')
          .map((c) => c + c)
          .join('')
      : s;
  if (!/^[0-9a-fA-F]{6}$/.test(h)) return [0.95, 0.95, 0.97];
  return [parseInt(h.slice(0, 2), 16) / 255, parseInt(h.slice(2, 4), 16) / 255, parseInt(h.slice(4, 6), 16) / 255];
}
