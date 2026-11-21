// Focus-In Home Tiles: tokens and the replica layout. Every position is in points on the 402 × 874 pt canvas
// (iPhone 17 Pro), page-local: page p sits 780 pt below page p − 1, and the screen shows page content at
// y − scroll. The canvas scales with the screen width.

export const C = {
  paper: '#E4E3D3',
  tile: '#F7FAE8',
  forest: '#1E2D23',
  chip: '#E3E4D2',
  ink: '#1F2A22',
  label: '#A9AB9B',
  barGrey: '#E2E1CF',
  stepper: '#B8BAA6',
  modeDisc: '#EEF0DF',
  orange: '#F25A05',
  orangeLight: '#F8A35E',
} as const;

export const PITCH = 780;
export const PAGES = 3;

/** Design-system springs (damping, stiffness, mass 1). */
export const SPRING = {
  glide: { d: 22.1, k: 157.9 },
  pop: { d: 19, k: 195 },
  snap: { d: 30.5, k: 322.3 },
  detent: { d: 26, k: 420 },
} as const;

/** Defocus per depth plane (pt of circle-of-confusion radius). */
export const PLANE_DEFOCUS = 9;

export type Rect = readonly [number, number, number, number];

/**
 * The tiles. Each is one focus group on one depth plane: plane 0 is nearest the lens. The planes step down each page
 * like a table seen at an angle, so a rack from the top tile to the bottom one reads as the lens travelling into the
 * page.
 */
export const GROUPS = [
  { id: 'header', page: 0, plane: 0, rect: [16, 70, 370, 117] },
  { id: 'bedroom', page: 0, plane: 1, rect: [16, 209, 370, 307] },
  { id: 'chat', page: 0, plane: 2, rect: [16, 540, 370, 176] },
  { id: 'strip', page: 1, plane: 0, rect: [16, 70, 370, 117] },
  { id: 'chart', page: 1, plane: 1, rect: [16, 233, 370, 480] },
  { id: 'mode', page: 2, plane: 0, rect: [16, 70, 370, 120] },
  { id: 'picker', page: 2, plane: 1, rect: [16, 218, 370, 222] },
  { id: 'alert', page: 2, plane: 2, rect: [16, 470, 252, 222] },
] as const satisfies readonly { id: string; page: number; plane: number; rect: Rect }[];

export const G = {
  header: 0,
  bedroom: 1,
  chat: 2,
  strip: 3,
  chart: 4,
  mode: 5,
  picker: 6,
  alert: 7,
} as const;
export const NGROUPS = GROUPS.length;

/** Shape kinds for the scene shader. */
export const K = { paper: 0, dark: 1, ring: 2 } as const;

export type Shape = { g: number; rect: Rect; r: number; color: string; kind: number; bar?: number };

const disc = (cx: number, cy: number, d: number) => [cx - d / 2, cy - d / 2, d, d] as const;

/** Bars of the usage chart: x, top at full value; all stand on y 697. */
export const BARS = [
  { x: 90, top: 470 },
  { x: 149, top: 499 },
  { x: 208, top: 529 },
  { x: 268, top: 440 },
  { x: 327, top: 381 },
] as const;
export const BAR_FOOT = 697;
export const BAR_W = 44;
export const LIMIT_Y = 424;

/** Every flat shape, in paint order. Bars carry their index: their top is animated. */
export const SHAPES: Shape[] = [
  // page 1
  { g: G.header, rect: [16, 70, 370, 117], r: 40, color: C.forest, kind: K.dark },
  { g: G.header, rect: [326, 84, 44, 89], r: 22, color: C.stepper, kind: K.paper },
  { g: G.bedroom, rect: [16, 209, 370, 307], r: 40, color: C.tile, kind: K.paper },
  { g: G.bedroom, rect: disc(56, 477, 46), r: 23, color: C.forest, kind: K.dark },
  { g: G.bedroom, rect: disc(110, 477, 46), r: 23, color: C.chip, kind: K.paper },
  { g: G.chat, rect: [16, 540, 370, 176], r: 40, color: C.tile, kind: K.paper },
  { g: G.chat, rect: [33, 648, 154, 44], r: 22, color: C.forest, kind: K.dark },
  { g: G.chat, rect: [264, 648, 107, 44], r: 22, color: C.chip, kind: K.paper },
  // page 2
  { g: G.strip, rect: [16, 70, 370, 117], r: 40, color: C.tile, kind: K.paper },
  { g: G.strip, rect: [33, 77, 44, 104], r: 22, color: C.forest, kind: K.dark },
  { g: G.chart, rect: [16, 233, 370, 480], r: 40, color: C.tile, kind: K.paper },
  { g: G.chart, rect: disc(349, 269, 44), r: 22, color: C.chip, kind: K.paper },
  ...BARS.map((b, i) => ({ g: G.chart, rect: [b.x, b.top, BAR_W, BAR_FOOT - b.top] as const, r: 22, color: C.barGrey, kind: K.paper, bar: i })),
  // page 3
  { g: G.mode, rect: [16, 70, 370, 120], r: 40, color: C.tile, kind: K.paper },
  { g: G.mode, rect: disc(85, 130, 46), r: 23, color: C.modeDisc, kind: K.paper },
  { g: G.mode, rect: disc(161, 130, 46), r: 23, color: C.modeDisc, kind: K.paper },
  { g: G.mode, rect: disc(239, 130, 46), r: 23, color: C.forest, kind: K.dark },
  { g: G.mode, rect: disc(316, 130, 46), r: 23, color: C.modeDisc, kind: K.paper },
  { g: G.picker, rect: [16, 218, 370, 222], r: 40, color: C.tile, kind: K.paper },
  { g: G.alert, rect: [16, 470, 252, 222], r: 40, color: C.tile, kind: K.paper },
  { g: G.alert, rect: disc(231, 506, 44), r: 22, color: C.chip, kind: K.paper },
  { g: G.alert, rect: disc(53, 669, 40), r: 20, color: C.ink, kind: K.ring },
  { g: G.alert, rect: [149, 647, 104, 44], r: 22, color: C.chip, kind: K.paper },
];
export const NSHAPES = SHAPES.length;

/** The orange's anchors. */
export const ORANGE = {
  dot: { x: 170, y: 670, d: 8 }, // live dot on the Calling pill (page 1)
  seed: 44, // bar 5 as a dot (page 2)
  budget: { x: 282, y: 470, w: 104, h: 222, r: 40 }, // page 3
  lamp: { x: 292, y: 690, d: 200 }, // screen space, in the dark
} as const;

/** Room picker rows: name, value glyph, value. Bedroom (index 3) is the centre row at rest. */
export const ROOMS = [
  { name: 'Entrance', icon: 'timer', value: '2 HR' },
  { name: 'Kitchen', icon: 'hourglassSmall', value: '12 MIN' },
  { name: 'Living room', icon: 'hourglassSmall', value: '28 MIN' },
  { name: 'Bedroom', icon: 'boltSmall', value: '1.1 KWH' },
  { name: 'Hallway', icon: 'timer', value: '4 HR' },
] as const;
export const PICK_ROW = 44.5;
export const PICK_CENTRE_Y = 329;
export const PICK_REST = 3;

export const FONT = { text: 'FHT-Figtree' } as const;
