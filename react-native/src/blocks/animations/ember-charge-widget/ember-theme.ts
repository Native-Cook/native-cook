// Ember — an activity widget lit from below like a bed of coals.
// Geometry, colors and glyphs of the card. Every number is in points inside
// the 340 × 340 card, origin at its top-left corner.
//
// Pure data so node tests can import it without React Native.

// Gaps come from one four-point scale. A gap is never a size: the things
// below have their own dimensions, and only the space between them is a token.
// Corner copy needs more room than a straight edge (the outline's corner
// starts 96 pt along each edge), hence corner and cornerText.
export const space = { hair: 4, tight: 12, column: 16, near: 20, edge: 28, corner: 32, cornerText: 36 } as const;

// What things are: sizes and line boxes.
const size = 340;
const dim = {
  disc: 56,
  glyph: 16,
  barH: 56,
  countH: 60,
  heart: { w: 56, h: 52 },
  // Line boxes of the copy, shared with the styles.
  line: { title: 32, subtitle: 20, time: 16, value: 28, unit: 16, hint: 18, bpm: 20 },
} as const;

// Each band starts after the previous band and its relationship gap.
const header = { top: space.corner, h: dim.disc };
const metrics = { top: header.top + header.h + space.corner, h: dim.line.value + dim.line.unit };
const bar = { top: metrics.top + metrics.h + space.near, h: dim.barH };
const footer = { top: bar.top + bar.h + space.tight, h: dim.countH };
const contentWidth = size - space.edge * 2;
const columnWidth = (contentWidth - space.column * 2) / 3;
const titleTop = header.top + (header.h - (dim.line.title + dim.line.subtitle)) / 2;

export const E = {
  size,
  space,
  bands: { header, metrics, bar, footer },
  // Cubic squircle corners; the circular radius is only the tube's clip.
  corner: 96,
  radius: 76,
  bleed: 60,
  inset: space.edge,
  // The screen keeps this much around the card; narrower screens scale it down.
  pageMargin: 16,
  line: dim.line,
  disc: { x: space.corner, y: header.top, d: dim.disc },
  // Centre the title and subtitle lines on the disc; leave near after the disc.
  title: { x: space.corner + dim.disc + space.near, top: titleTop, sub: titleTop + dim.line.title },
  time: { right: space.cornerText, top: titleTop + space.hair },
  metrics: {
    x: [space.edge, space.edge + columnWidth + space.column, space.edge + 2 * (columnWidth + space.column)],
    w: columnWidth,
    text: dim.glyph + space.hair,
    glyph: dim.glyph,
    // The glyph is centred on the value's line.
    glyphTop: metrics.top + (dim.line.value - dim.glyph) / 2,
    valueTop: metrics.top,
    unitTop: metrics.top + dim.line.value,
  },
  bar: { x: space.edge, y: bar.top, w: contentWidth, h: bar.h, r: 14, bloom: 12, core: 4 },
  // The 2 pt optical overhang puts the count's ink on the straight inset.
  count: { x: space.edge - 2, size: 54, h: footer.h, top: footer.top, unitGap: space.tight, unitDrop: 8 },
  // Align the heart's right and bottom edges with the content and footer.
  // Native glyph-core bounds calibrated at 3 px/pt on iOS, within one
  // device pixel of the path's area centroid (28, 23.10571).
  heart: { x: size - space.edge - dim.heart.w, y: footer.top + footer.h - dim.heart.h, ...dim.heart, rateOffset: 12.72 },
  // Rising embers: the first one's x, their pitch, and how far under the footer they start.
  embers: { x: 44, pitch: 36, below: 20 },
  // Soft bands on the tube: half of each 28 pt, leaning 21 degrees.
  stripe: { period: 28 },
  restSteps: 4151,
  // 4,151 of 6,400 is the reference's 65 % of the bar.
  goal: 6400,
  restBpm: 80,
  goalBpm: 96,
} as const;

// The floor opens gradually toward the bar and the heart, with no box edge.
export const countFootprint = {
  x: E.inset + E.metrics.w,
  y: E.count.top + E.count.h / 2,
  rx: E.heart.x - E.inset,
  ry: E.count.h + space.corner,
} as const;

export function countLight(x: number, y: number) {
  "worklet";
  const dx = (x - countFootprint.x) / countFootprint.rx;
  const dy = (y - countFootprint.y) / countFootprint.ry;
  const radius2 = dx * dx + dy * dy;
  return 1 - Math.exp(-radius2 * radius2);
}

export const emberTone = {
  page: "#000000",
  // Ink follows the surface it sits on, not the copy beside it.
  // On the dark face: white, and a grey secondary at 6.0:1.
  ink: "#ffffff",
  sub: "#8d8d8d",
  // On the ember floor the grey has the floor's own luminance (1.2:1 measured)
  // and disappears, and a tinted white stops at 3.7:1. Only white clears
  // 4.5:1 there, so the unit keeps its rank by size, not by colour.
  onEmber: "#ffffff",
  // The header disc: neutral smoked glass, lit from just above its centre.
  discTop: "#454543",
  discBottom: "#222220",
  spark: "#ffd23f",
  sparkCool: "#ff7a00",
  wave: "#ffd98a",
};

export type Glyph = { fill: readonly string[]; stroke: readonly string[]; strokeWidth: number };

// Version 2 pictograms on a 24 × 24 grid, round caps and joins. At 16 pt,
// the route and clock's 1.8 grid stroke becomes 1.2 pt on screen.
export const emberGlyph = {
  walk: {
    fill: [
      "M 11.55 3.75 C11.55 2.34 12.69 1.2 14.1 1.2 C15.51 1.2 16.65 2.34 16.65 3.75 C16.65 5.16 15.51 6.3 14.1 6.3 C12.69 6.3 11.55 5.16 11.55 3.75 Z",
      "M10.9 7.05 C12.45 6.82 13.7 7.32 14.95 8.43 C16.18 9.52 16.98 9.89 18.48 9.12 C19.22 8.73 19.95 9.02 20.28 9.72 C20.62 10.45 20.3 11.12 19.62 11.52 C17.66 12.65 16.13 12.25 14.42 11.35 L13.82 14.9 C15.67 15.98 17.05 17.23 17.66 19.16 L18.42 21.79 C18.69 22.72 18.18 23.38 17.32 23.5 C16.46 23.62 15.86 23.17 15.61 22.3 L14.93 20.05 C14.5 18.67 13.49 18 12.13 17.38 L10.54 19.93 C9.53 21.47 7.28 22.49 5.14 22.9 C4.17 23.09 3.46 22.61 3.35 21.79 C3.23 20.95 3.69 20.31 4.58 20.09 C6.24 19.69 7.6 19.05 8.12 18.12 L9.26 15.28 L9.79 10.52 C8.49 11.1 8.14 12.19 7.75 13.88 C7.57 14.7 7.02 15.08 6.31 14.95 C5.56 14.82 5.25 14.18 5.37 13.4 C5.89 9.99 7.64 7.65 10.9 7.05 Z",
    ],
    stroke: [],
    strokeWidth: 0,
  },
  route: {
    fill: [
      "M16.95 4.15 C16.95 2.69 18.14 1.5 19.6 1.5 C21.06 1.5 22.25 2.69 22.25 4.15 C22.25 5.61 21.06 6.8 19.6 6.8 C18.14 6.8 16.95 5.61 16.95 4.15 Z",
      "M2.29 18.38 C2.05 18.11 2.23 17.7 2.6 17.7 L4.05 17.7 L4.05 16.75 L5.85 16.75 L5.85 17.7 L7.3 17.7 C7.67 17.7 7.85 18.11 7.61 18.38 L5.28 21.02 C5.1 21.22 4.8 21.22 4.62 21.02 Z",
    ],
    stroke: [
      "M4.95 18.1 V6.2 C4.95 3.9 6.46 2.2 8.55 2.2 C10.64 2.2 12.15 3.9 12.15 6.2 V17.5 C12.15 19.84 13.77 21.5 15.87 21.5 C17.97 21.5 19.6 19.84 19.6 17.5 V5.5",
    ],
    strokeWidth: 1.8,
  },
  clock: {
    fill: [],
    stroke: [
      "M21.65 12 A9.65 9.65 0 1 1 2.35 12 A9.65 9.65 0 1 1 21.65 12 Z",
      "M12 5.35 V12.05 L15.35 15.35",
    ],
    strokeWidth: 1.8,
  },
  bolt: {
    fill: [
      "M13.61 1.25 C13.72 1.08 13.94 1.16 13.92 1.36 L13.05 10.32 L19.3 9.81 C19.49 9.79 19.58 9.96 19.46 10.13 L10.4 22.75 C10.28 22.91 10.07 22.83 10.1 22.62 L10.75 13.65 L4.7 14.05 C4.51 14.07 4.42 13.89 4.54 13.73 Z",
    ],
    stroke: [],
    strokeWidth: 0,
  },
} satisfies Record<string, Glyph>;

// Original cubic outline: circular shoulders, a ≥2.56 pt cleft and 3.97 pt tip.
// Bounds (0.25,3)–(55.75,50); area centroid (28,23.11), on a 56 × 52 box.
export const emberHeart =
  "M28 9.5 C26.1 9.5 25.65 8.15 24 6.8 C21.36 4.64 18.15 3 14 3 C6.41 3 0.25 9.16 0.25 16.75 C0.25 29 14 40.5 25.6 49.2 C26.32 49.74 27.17 50 28 50 C28.83 50 29.68 49.74 30.4 49.2 C42 40.5 55.75 29 55.75 16.75 C55.75 9.16 49.59 3 42 3 C37.85 3 34.64 4.64 32 6.8 C30.35 8.15 29.9 9.5 28 9.5 Z";

export const emberHeartTone = {
  // Floor contact shadow beneath the heart.
  contactShadow: "#1f0b02",
  // Vessel light spilling onto the floor beneath the tip.
  contactCaustic: "#ffc35d",
  // Smoked vessel body at the shoulders.
  bodyTop: "#2b1406",
  // Smoked vessel body at 55 percent of its height.
  bodyMiddle: "#4b2504",
  // Smoked vessel body at the tip.
  bodyTip: "#6d3803",
  // Transparent surface of the vessel's liquid light.
  poolSurface: "rgba(202,104,22,0)",
  // Warm middle of the vessel's liquid light.
  poolMiddle: "#fa9527",
  // Bright liquid light at the vessel's tip.
  poolTip: "#ffc965",
  // Inner wall shade along the upper edge.
  depthTop: "rgba(31,11,2,0.40)",
  // Transparent end of the inner wall shade.
  depthClear: "rgba(31,11,2,0)",
  // Ember-lit rim on the lower flanks and tip.
  rimEmber: "#ffd79a",
  // Transparent upper end of the ember-lit rim.
  rimEmberClear: "rgba(255,215,154,0)",
  // Faint upper-left key rim.
  rimKey: "#fef1d0",
  // Transparent end of the upper-left key rim.
  rimKeyClear: "rgba(254,241,208,0)",
  // Crisp crescent reflection on the upper-left lobe.
  specularCrescent: "#fef1d0",
  // The heart rate on the smoked glass: white, like the count beside it.
  ink: "#ffffff",
} as const;

// Must match KEY_ANGLE in ember-shader.ts; do not import the shader into the theme.
export const emberHeartKeyAngle = -2.356194;
