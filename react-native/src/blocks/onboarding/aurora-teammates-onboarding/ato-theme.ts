// Aurora Teammates Onboarding — every measured value, in the pin's own pixels.
//
// The pin (Crevio Agency, three phones on one 1080 px board) shows each screen 280 px wide and 600 px
// tall. Everything here is measured on that board and converted with S = screen width / 280, so on a
// 402 pt iPhone 1 pin px = 1.4357 pt. Positions are from the screen's top-left (y top-anchored), except
// the button, which keeps the pin's distance from the bottom edge. Colours are sampled from the pin.

export const PIN_W = 280;
export const PIN_H = 600;

/** The three pages and the dots the pin shows (four: the fourth page is not in the pin). */
export const PAGES = 3;
export const DOTS = 4;

export const C = {
  // page, top to bottom (the screen under the aurora)
  navy: '#0B0D45',
  navyDeep: '#0A0B33',
  // the sheet: near black under its edge, navy at the bottom
  sheetTop: '#0E0C12',
  sheetMid: '#0F0E22',
  sheetLow: '#101037',
  sheetBottom: '#11124F',
  rim: '#4B53B8',
  title: '#FFFFFF',
  titleShade: '#A9A9AF',
  subtitle: '#BEBDC4',
  dotsTrack: '#1F1E3E',
  dot: '#3A3959',
  dotActive: '#AEAFCC',
  buttonLight: '#FFFFFF',
  buttonShade: '#BDBDBF',
  buttonText: '#0E0E18',
  cardText: '#FFFFFF',
  cardSub: 'rgba(255,255,255,0.62)',
} as const;

/** The gradient line of the titles, sampled along "AI TEAMMATES" (x as a fraction of the word). */
export const RAINBOW = {
  colors: ['#9F5AEE', '#A966EB', '#CA9FBE', '#DFC89A', '#F3F274', '#EDF877', '#B8E698', '#88D2BA', '#49B3E5', '#0180F7'],
  positions: [0, 0.17, 0.27, 0.38, 0.5, 0.57, 0.65, 0.77, 0.85, 1],
};

/** Per page: where the sheet's flat top edge sits, and the aurora's palette. */
export const SHEET_TOP_PX = [238.5, 305.5, 305.5];
/** The notch in the sheet's top edge: a dip of NOTCH_DEPTH, two cubic halves (fitted to the pin). */
export const NOTCH = { half: 78, c1: 26, c2: 50, depth: 21 };
/** Corner radius where the sheet's top edge meets the screen's side. */
export const SHEET_CORNER_PX = 11.7;

/** Title: Inter Display Bold, cap height 21 px, 30 px between baselines, tracking -3.6 %. */
export const TITLE = {
  size: 28.85,
  tracking: -0.036,
  capHeight: 21,
  lineStep: 30,
  /** first cap top below the sheet's flat top */
  top: 54.5,
};

/** Subtitle: Inter Regular 13 pt, 16 px between baselines, first baseline below the sheet's top. */
export const SUBTITLE = {
  size: 9.06,
  lineStep: 16,
  baseline: [166.4, 136.4, 136.4],
};

/** Dots: track 55 x 15 px; dots 4 px, the current one a 13 x 5 px pill; centre below the sheet top. */
export const DOTS_UI = {
  trackW: 55,
  trackH: 15,
  dot: 4,
  pillW: 13,
  pillH: 5,
  gap: 5.8,
  pad: 6.75,
  centre: [223, 176.5, 176.5],
};

/** Button: 229 x 39 px pill, its bottom this far above the screen's bottom edge per page. */
export const BUTTON = { w: 229, h: 39, bottom: [35, 22, 22], label: 9.75 };

/** The light's centre: the notch, a little under the sheet's edge. */
export const CORE_DY = 6;

// ------------------------------------------------------------------ page 1: two acrylic icons
/** Page-1 tiles (Blender blockout, refinished by an image model): the square image's size and centre
 * in pin px, fitted so each tile's silhouette lands on the pin's box. */
export const ICON_RENDER = 1;
export const ICONS = [
  // purple sunburst, turned ~30 deg clockwise, partly under the slate one
  { key: 'purple', cx: 100.8, cy: 173.3, face: 117.5, rot: 0 },
  // slate sparkles, turned ~14 deg the other way, a little closer
  { key: 'slate', cx: 180.9, cy: 144.3, face: 111.6, rot: 0 },
] as const;

// ------------------------------------------------------------------ page 2: five agent cards
export const CARD = {
  w: 218.75,
  h: 43,
  r: 7,
  tile: 30.5,
  tileR: 7,
  tileX: 5.5,
  titleX: 45.75,
  titleBase: 20,
  subBase: 35.75,
  pillW: 37.5,
  pillH: 15,
  pillRight: 8.5,
};

/** Card centres, in-plane turn (deg) and how far back they sit (0 front .. 1 back), measured. */
export const CARDS = [
  { cx: 135.5, cy: 108.8, rot: 2.4, depth: 1.0, alpha: 0.24 },
  { cx: 152.1, cy: 143.9, rot: -2.6, depth: 0.75, alpha: 0.82 },
  { cx: 126.5, cy: 184.3, rot: 3.1, depth: 0.5, alpha: 0.9 },
  { cx: 153, cy: 228.6, rot: -1.3, depth: 0.25, alpha: 0.95 },
  { cx: 128, cy: 274.4, rot: 8.4, depth: 0.0, alpha: 1.0 },
] as const;

// ------------------------------------------------------------------ page 3: six integration tiles
export const TILE = {
  w: 116.5,
  h: 75,
  r: 9,
  logo: 22,
  logoX: 8,
  logoY: 8,
  nameBase: 54,
  subBase: 66,
};

export const TILES = [
  { cx: 83.4, cy: 128.9, rot: -13, alpha: 0.55 },
  { cx: 203.9, cy: 131.4, rot: 0, alpha: 0.52 },
  { cx: 81.4, cy: 196.4, rot: 14.5, alpha: 0.78 },
  { cx: 198.9, cy: 193.4, rot: 23, alpha: 0.82 },
  { cx: 78.0, cy: 266.6, rot: -12, alpha: 1.0 },
  { cx: 201.0, cy: 263.1, rot: 0, alpha: 1.0 },
] as const;

export type Layout = ReturnType<typeof makeLayout>;

/** Pin px to points for this screen; the button keeps its distance from the bottom edge. */
export function makeLayout(width: number, height: number) {
  const S = width / PIN_W;
  const px = (v: number) => v * S;
  return {
    W: width,
    H: height,
    S,
    px,
    cx: width / 2,
    sheetTop: SHEET_TOP_PX.map(px),
    notch: {
      half: px(NOTCH.half),
      c1: px(NOTCH.c1),
      c2: px(NOTCH.c2),
      depth: px(NOTCH.depth),
    },
    sheetCorner: px(SHEET_CORNER_PX),
    titleSize: px(TITLE.size),
    titleTracking: TITLE.tracking * px(TITLE.size),
    titleCap: px(TITLE.capHeight),
    titleStep: px(TITLE.lineStep),
    titleTop: px(TITLE.top),
    subtitleSize: px(SUBTITLE.size),
    subtitleStep: px(SUBTITLE.lineStep),
    subtitleBase: SUBTITLE.baseline.map(px),
    dots: {
      trackW: px(DOTS_UI.trackW),
      trackH: px(DOTS_UI.trackH),
      dot: px(DOTS_UI.dot),
      pillW: px(DOTS_UI.pillW),
      pillH: px(DOTS_UI.pillH),
      gap: px(DOTS_UI.gap),
      pad: px(DOTS_UI.pad),
      centre: DOTS_UI.centre.map(px),
    },
    button: {
      w: px(BUTTON.w),
      h: px(BUTTON.h),
      bottom: BUTTON.bottom.map(px),
      label: px(BUTTON.label),
    },
    coreDy: px(CORE_DY),
  };
}
