// Flip Focus Badge — every colour, size and copy token, measured on the pin (Pinterest 242912973648061939, a phone
// mock-up rectified onto the 402 × 874 pt canvas: `.work/flip-focus-badge/pin/pin-rectified-1206.png`).
// Row ids in the comments refer to the measurement table in `.work/flip-focus-badge/design.md`.
// Only this file holds literal colours and sizes; the component, the shader and the motion read them from here.

export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;

export const ref = {
  color: {
    // page (row pg): near black with a film grain; measured 3.2 / 255 at the top, 8 mid, 13 right of mid, 9.4 low
    pageTop: '#030303',
    pageMid: '#080808',
    pageRight: '#0D0D0D',
    sheet: '#1F1F1F', // (300, 700) → 31, 31, 31
    chip: '#181818', // flame / trophy capsules (24, 24, 24)
    disc: '#1B1B1B', // help / settings (27)
    seg: '#2D2D2D', // segmented control (45.6)
    segOn: '#404040', // selected D (64.4)
    segOff: '#303030', // W M Y (48)
    ink: '#FFFFFF', // numerals, the selected segment, the counts
    inkDate: '#ACACAC', // "Sunday, Mar 8" (172)
    inkMute: '#A4A4A4', // "total flip time" (164)
    inkHint: '#646464', // the hint (100)
    inkLabel: '#676767', // 10h / 11h (103)
    inkSeg: '#6D6D6D', // W M Y (109)
    grabber: '#606060',
    track: '#262626', // progress track (38)
    fill: '#898989', // progress fill (136.6)
    flame: '#F5821F', // the flame (200, 104, 30 at its lit edge; the body is #F27B17)
    // the badge (rows plate, num, ring)
    plate: '#1A1A1C', // black glass (26.7 at the top of the plate)
    edge: '#F1F3FA', // the 1.5 pt enamel edge
    periwinkle: '#8FADF3', // ring (143, 173, 243)
    numeral: '#8FA6FF', // the numeral's base before its reflections (concept) — the face reads #C5D3FF
    numeralSide: '#5A6FE0', // the side walls (concept)
    outline: '#10143A', // the thin navy line round the glyphs
  },
} as const;

export const sys = {
  surface: { page: ref.color.pageMid, sheet: ref.color.sheet },
  ink: { primary: ref.color.ink, date: ref.color.inkDate, mute: ref.color.inkMute, hint: ref.color.inkHint },
  badge: { tint: ref.color.numeral, plate: ref.color.plate, edge: ref.color.edge },
} as const;

/** Font families, loaded from `assets/fonts` (Inter, SIL OFL: OFL.txt next to the files). */
export const FONT = {
  regular: 'FFB-Inter-Regular',
  medium: 'FFB-Inter-Medium',
  semibold: 'FFB-Inter-SemiBold',
  bold: 'FFB-Inter-Bold',
} as const;

/** Type roles, pt on the 402 canvas (size from the measured width of the pin's text, Inter metrics). */
export const TYPE = {
  count: { family: FONT.semibold, size: 17, lineHeight: 22 },
  total: { family: FONT.bold, size: 48, lineHeight: 52, tracking: -0.01 }, // "10h 24m": width 195, h 37
  date: { family: FONT.medium, size: 13.3, lineHeight: 18 },
  caption: { family: FONT.medium, size: 12.4, lineHeight: 16 },
  hint: { family: FONT.medium, size: 15.4, lineHeight: 20 },
  tick: { family: FONT.medium, size: 11.5, lineHeight: 14 },
  seg: { family: FONT.semibold, size: 15, lineHeight: 20 },
} as const;

/** The measured boxes (pt on the 402 canvas). `y` is the pin's own value; makeLayout moves it per anchor. */
export const comp = {
  chipFlame: { x: 19.3, y: 82.3, w: 64.4, h: 33.7 }, // row cap1
  chipTrophy: { x: 94.3, y: 82.0, w: 65.4, h: 34.7 }, // row cap2
  discHelp: { x: 287.7, y: 77.3, w: 44.3, h: 44.7 }, // row disc1
  discGear: { x: 339.0, y: 76.0, w: 44.0, h: 45.7 }, // row disc2
  progress: { x: 109.3, y: 468.0, w: 183, h: 3.7, fill: 0.4 }, // rows trk, fil; "10h"/"11h" at y 453–461
  tickLabel: { y: 450, h: 14 },
  hint: { y: 507, h: 20, w: 301 }, // centre y 516.7
  sheet: { x: 17, y: 628, w: 368, h: 220, r: 40 }, // row sheet
  grabber: { x: 183, y: 644.3, w: 36, h: 4.5 },
  date: { y: 660, h: 18 },
  total: { y: 686, h: 52 },
  caption: { y: 748, h: 16 },
  seg: { x: 39, y: 780, w: 325, h: 50, r: 25, pad: 5, gap: 6, itemH: 38, itemR: 19 },
} as const;

/** The badge scene (design pt, plate-centred, y up) — rows plate, ring, num, hr, plus. */
export const SCENE = {
  centre: { x: 206.5, y: 293.5 }, // the plate's centre on the 402 canvas
  eye: 1000, // camera distance: weak perspective, the pin is almost orthographic
  bound: 176, // bounding sphere
  plate: { lobes: 6, rBase: 73.3, rAmp: 11.1, half: 4.0, dome: 2.2, bevel: 2.4, r0: 84 },
  num: { baseY: -42.8, cap: 90.5, height: 12, rim: 9, pitchLeft: -0.85, advance: 0.68 },
  hr: { h: { x: 44.65, y: -9.0 }, r: { x: 58.5, y: -11.4 }, height: 4.2 },
  plus: { x: -56.5, y: 14.2, height: 3.6 },
  dot: { x: -38, y: 29.8 },
  // the ring: a circle of radius R and a round tube rt, fitted to the pin's ellipse (centre (199.9, 317.8), a 139.4,
  // b 35.3, −14.7°) with the perspective above; α is the tilt of its plane, ρ its roll in the screen
  ring: { R: 138.1, rt: 7.3, alpha: -76.4, roll: 14.5, cx: -7.53, cy: -19.62, tailDeg: 239.4, headDeg: 222.3, satRestDeg: 31.4 },
  spark: { head: 0.93, sat: 0.66 },
} as const;

/** Glyph atlas (assets/ffb-glyphs.png, written by .work/flip-focus-badge/tools/gen_atlas.py). Ink boxes in pt, glyph frame. */
export const ATLAS = {
  digit: { cw: 224, ch: 246, cols: 5, range: 4, ox: 32, oy: 214, scale: 2, domeY: 588 },
  small: { cw: 96, rowY: 492, range: 4 },
  // ink left / ink right of the digits 0..9
  inkL: [0.62, 0.5, 0, 0.12, 0, 0, 0, 0, 0, 0.12],
  inkR: [76.25, 66.12, 90.75, 92.38, 93.88, 96.75, 91.12, 84.75, 91.88, 91.62],
  small_ids: { h: 0, r: 1, star: 2, dot: 3, colon: 4, F: 5, O: 6, C: 7, U: 8, S: 9 },
} as const;

export const COPY = {
  label: 'Flip Focus Badge',
  hintIdle: 'flip your phone to start a focus session…',
  hintFocus: 'focusing… flip back to finish',
  date: 'Sunday, Mar 8',
  caption: 'total flip time',
  streak: 75,
  trophies: 17,
  segments: ['D', 'W', 'M', 'Y'],
  caps: 'FOCUS',
  unit: 'hr',
  helpA11y: 'Help',
  settingsA11y: 'Settings',
  badgeA11y: (hours: number) => `${hours} hour focus badge. Flip your phone, or drag the badge, to start a focus session.`,
  badgeA11yRunning: (clock: string) => `Focus session running, ${clock}. Flip back to finish.`,
  startA11y: 'Start focus session',
  stopA11y: 'Finish focus session',
} as const;

export function makeLayout(W: number, H: number, insetTop: number, insetBottom: number) {
  const S = W / REF.w;
  // the top row keeps its distance from the status bar; the sheet stands clear of the home indicator (the bottom safe
  // area plus 10 pt; at least the pin's 26 pt on a phone without one); the badge group (plate top 208 … hint 527)
  // centres between them
  const yt = (y: number) => insetTop + (y - REF.safeTop) * S;
  const sheetBottom = H - Math.max(26 * S, insetBottom + 10);
  const sheetTop = sheetBottom - comp.sheet.h * S;
  const topRowBottom = yt(122);
  const free0 = topRowBottom;
  const free1 = sheetTop;
  const groupTop = 208;
  const groupBottom = 530;
  const groupMid = (groupTop + groupBottom) / 2; // 369 in the pin
  const mid = (free0 + free1) / 2 - 6 * S; // at the pin's 402 × 874 this is exact: yc(y) = y
  const yc = (y: number) => mid + (y - groupMid) * S;
  const ys = (y: number) => sheetTop + (y - comp.sheet.y) * S;
  return { S, W, H, x: (v: number) => v * S, yt, yc, ys, sheetTop, sheetBottom, insetTop, insetBottom };
}

export type Layout = ReturnType<typeof makeLayout>;
