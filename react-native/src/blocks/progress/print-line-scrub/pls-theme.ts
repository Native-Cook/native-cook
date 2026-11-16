// Print Line Scrub — every colour, size and copy token. Measured on the pin (Dribbble 27100885, a photographed phone) and
// on the concept's rectified numbers (`concepts3-B.md` §0): the pin is a tilted mock-up, so the 402 × 874 pt values below
// come from the concept's measurements, not from my own perspective warp (see `.work/print-line-scrub/codex-report.md`).
// Only this file holds literal colours and sizes; the component, the shaders and the motion read them from here.

export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;

export const ref = {
  color: {
    ground: '#ECE9E6', // page, 5 × 5 means at three spots
    groundHi: '#F4F1EE', // the lighter floor in front of the base
    grid: '#DDD9D5', // perspective grid lines
    rose: '#F3DAD3', // bounce tint on the floor round the base
    filament: '#D8433A', // matte red PLA
    filamentShadow: '#A82E2A',
    filamentLight: '#EE6A5C',
    ghost: '#C9D1CC', // unprinted mesh preview
    ghostLine: '#9FAAA4',
    heatHot: '#FFE08A', // the hot band: wall of the fresh layer
    heatMid: '#FF6A3A',
    yellow: '#FFD60A',
    yellowFlash: '#FFF3B0',
    ink: '#111111',
    inkSoft: '#4B4846',
    white: '#FBFAF8',
    glassTint: '#FFFFFF',
    glassRim: '#FFFFFF',
    glassShadow: '#3A2420', // page L × 0.25
    arrow: '#3F8CFF', // the transform gizmo at the seam
    axisRed: '#F04A52',
    axisGreen: '#4FD27A',
    handBlue: '#CFE0F7', // the pale-blue hand button
    iconInk: '#17161A',
  },
  // the model: one figure, 1 world unit = 500 pt of height along the axis (h 0 = base top at y 690, h 1 = head top at y 190)
  stage: { cx: 201, yBase: 690, yHead: 190, unit: 500 },
  // top and bottom chrome
  header: { x: 16, y: 62, w: 370, h: 56, r: 28 },
  logo: { x: 4, y: 6, d: 44 }, // inside the header strip
  file: { x: 62, size: 12, tracking: 0.04 }, // x inside the strip; mono caps in the pin, drawn with Urbanist here
  workspace: { w: 108, h: 44, r: 22, size: 15 }, // right edge 4 pt inside the strip
  dock: { x: 16, y: 780, w: 370, h: 56, r: 28 },
  dockBtn: { d: 44, x0: 6, gap: 8 },
  // the progress capsule, resting on the print line
  capsule: { w: 340, h: 104, r: 52 },
  capLabel: { x: 28, y: 33, size: 15 }, // baseline of "Printing Progress", from the capsule's top-left
  bar: { x: 20, y: 46, w: 300, h: 40 }, // dotted bar box inside the capsule
  pill: { d: 44 },
  dots: { pitch: 5, r: 1.6, loose: 40 },
  // gizmo at the seam (the pin's blue arrow with red and green stubs)
  gizmo: { rest: 96, touch: 116, head: 10, shaft: 2, stub: 14, max: 140 },
  // the capsule's glass: a lens, frost and a rim
  glass: { lens: 1.05, refraction: 9, frost: 2.5, tint: 0.35, rimBand: 11 },
  // layout limits
  rangeInset: 12, // the capsule stays this far inside the header and the dock
  copy: {
    title: 'Printing Progress',
    layer: 'Layer',
    of: '/ 480',
    file: 'MSBK_KAIJU_01',
    workspace: 'Workspace',
    layers: 480,
    a11yLabel: 'Layer preview',
  },
  // the model's colour, light and layer geometry
  light: { az: 315, el: 52 },
  layers: 480,
  bandRest: 14,
  band: { min: 14, max: 40 },
  gl: { yawMax: 4, yawPerPt: 0.03 },
  figure: { top: 180, height: 600 }, // width follows the registered assets, centred on the existing axis
} as const;

export const COPY = ref.copy;

/** Scales the 402 × 874 canvas to the screen: the stage is centred between the safe areas, header and dock follow them. */
export function makeLayout(W: number, H: number, insetTop: number, insetBottom: number) {
  const availH = H - insetTop - insetBottom;
  const S = Math.min(W / REF.w, availH / (REF.h - REF.safeTop - REF.safeBottom));
  const ox = (W - REF.w * S) / 2;
  const oy = (availH - (REF.h - REF.safeTop - REF.safeBottom) * S) / 2;
  return {
    W,
    H,
    S,
    insetTop,
    insetBottom,
    /** x on the 402 canvas → screen x */
    x: (x: number) => ox + x * S,
    /** y on the canvas → screen y, content centred between the safe areas (the stage, the capsule) */
    y: (y: number) => insetTop + oy + (y - REF.safeTop) * S,
    /** top chrome keeps the safe-area edge */
    yt: (y: number) => insetTop + (y - REF.safeTop) * S,
    /** bottom chrome keeps the home-indicator edge */
    yb: (y: number) => H - insetBottom - (REF.h - REF.safeBottom - y) * S,
  };
}

export type Layout = ReturnType<typeof makeLayout>;
