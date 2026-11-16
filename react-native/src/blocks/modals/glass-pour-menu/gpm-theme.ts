// Glass Pour Menu — every colour, size and copy, measured on the pin (Dribbble 27773244, 800 × 600 px video).
// The pin is a desktop composition: it is laid on the phone at 1.14 pt per pin px (Pro native menu width: 250 pt)
// with the flower's centre (pin 400, 300) at (201, 392) on the 402 × 874 reference screen. Every number below is pt
// on that screen, relative to the flower's centre O unless it says otherwise. Rows: .work/glass-pour-menu/design.md.

export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;

/** pt per pin px (design.md "scale"). */
export const PIN_PT = 1.14;

/** The flower's centre and the button's rest centre on the reference screen (pin 400, 300). */
export const ORIGIN = { x: 201, y: 392 } as const;

export const ref = {
  color: {
    // page: the flower (sampled as medians of 5 × 5 px, design.md "palette")
    cream: '#F4E1DE', // gaps between petals (#F5E1DE #F6E4E0 #F6E3E0)
    creamDeep: '#F6DAD6', // cream next to a soft petal edge
    petalCore: '#EF77BA', // one-petal colour at r 44 (under the button's halo)
    petalLilac: '#F5A7D1', // r 100: the lilac ring round the centre (#F6A1CF #F6A9CF)
    petalRose: '#F491A9', // r 180 (#F595B8 #F593AD)
    petalCoral: '#F5ABA7', // r 260 (#F5A4A5 #F5ABA8)
    petalPeach: '#F6BEBC', // r 380+ (#F6BCBA #F6BEBC)
    foldLight: '#F6A4CA', // two petals over each other, near the centre (r 100)
    foldDeep: '#F46789', // r 200, the dark pink slivers (#F46B8E #F46A86)
    foldEnd: '#F27B81', // r 250
    edge: '#8E3A30', // the crisp petal outline (brown-red, 1.4 px)
    fold: '#C2306A', // the thin crimson fold line near the centre
    // glass
    dropTint: '#F276CD', // the button at rest, body (#F478D0 #F377CF #F579CD)
    dropDeep: '#B9579D', // its upper inner rim (#C96AAC #BD599F)
    halo: '#C25CA3', // the magenta shade round the button on the petals (#BB579D #C25CA3)
    projectedCaustic: '#FFE3F6', // 46 × 20 pt patch, 12 pt below the lower rim
    caustic: '#FFB8F6', // the lit lower rim inside the glass (#FF9FF4 #FFADFF #FCC5FC)
    panelTint: '#FFA6D2', // the open panel, top half (#FEA5D1 #FFA6D1 #FEA7D1)
    panelLow: '#FFC0E7', // the open panel, bottom (#FEC0E7 #FFC1E8)
    hot: '#FFF3FA', // the white-hot glass mid-pour (the pin's 1.3–1.6 s frames)
    shadow: '#7A2A5C', // glass shadow tint (page L × 0.25, magenta: never black)
    rim: '#FFFFFF', // Fresnel rim light (used at ≤ 55 %)
    // content
    ink: '#3A0F2C', // row labels, icons, ✕ (darkest pin pixels #3F132D #460D31)
    glyph: '#3A0A30', // the button's ≡ (#1E0019 through the magenta glass)
    danger: '#921E40', // Delete (pin #AF4C5C, darkened: 4.7 : 1 on the panel's #F9ABD2 behind it)
    rule: '#FFFFFF', // separator over Delete, at 35 %
  },
  type: {
    // Pin face: SF Pro Text. Pro native menu uses bundled OFL Inter Medium at 17 pt.
    row: { family: 'GpmInterMedium', size: 17, tracking: -0.004 }, // Native context-menu label weight
    capRatio: 0.727,
  },
  /** One key light: from above (bright caustic at the glass's lower rim, darker upper rim). */
  light: { az: 0, el: 55 },
  /** Five discs whose circles all pass through O: the pin's petals (fitted to its edge lines, error < 0.75 px). */
  discs: [
    // soft: how far (pt) the far, blurred side of the petal reaches past its circle, and its blur (fitted to frame 0)
    { x: -316.7, y: -268.5, soft: 2, blur: 30 }, // pin (52, 5) r 458 — up-left
    { x: 98.3, y: -360.4, soft: 44, blur: 17 }, // pin (508, -96) r 410 — up
    { x: 352.2, y: 6.4, soft: 20, blur: 25 }, // pin (787, 307) r 388 — right
    { x: 119.2, y: 402.2, soft: 17, blur: 19 }, // pin (531, 742) r 462 — down
    { x: -262.1, y: 210.2, soft: 46, blur: 19 }, // pin (112, 531) r 370 — down-left
  ],
} as const;

export const sys = {
  surface: { page: ref.color.cream },
  ink: { primary: ref.color.ink, danger: ref.color.danger, glyph: ref.color.glyph },
} as const;

/** Component boxes, pt, relative to O (design.md table). */
export const comp = {
  button: { r: 39 },
  bead: { y: -107.76, r: 38.22 },
  panel: { top: -54.75, bottom: 161.25, half: 125, r: 21.3 },
  rows: [-27.75, 20.25, 68.25, 116.25],
  rowPitch: 48,
  rule: { y: 92.25, inset: 15 },
  labelX: 20,
  iconX: 27.5,
  icon: 17,
  stroke: 1.4,
  menuGlyph: { w: 23.8, gap: 6.9, stroke: 2.25 },
  close: { size: 17.5, stroke: 2.5 },
  lens: { overhang: 5, half: 18, r: 18 },
  hitSlop: 10,
};
export type MenuGeometry = typeof comp;

/** Geometry stays in real points: narrow phones do not shrink the 48 pt hit bands. */
export function menuGeometry(count = 4, size?: { drop?: number; width?: number; rowHeight?: number }, fontSize = 17): MenuGeometry {
  const radius = Math.max(22, size?.drop ?? 78) / 2;
  const half = Math.max(160, size?.width ?? 250) / 2;
  const rowPitch = Math.max(48, size?.rowHeight ?? 48, fontSize * 1.35 + 16);
  const top = (comp.panel.top * radius) / 39;
  const rows = Array.from({ length: count }, (_, i) => top + 27 + i * rowPitch);
  return {
    ...comp,
    button: { r: radius },
    bead: { y: (comp.bead.y * radius) / 39, r: radius * 0.98 },
    panel: { ...comp.panel, half, top, bottom: rows[count - 1] + rowPitch / 2 + 21 },
    rows,
    rowPitch,
    rule: { ...comp.rule, y: rows[count - 1] - rowPitch / 2 },
  };
}

export const COPY = {
  button: 'More actions',
  close: 'Close menu',
  rows: [
    { key: 'share', label: 'Share', icon: 'share' },
    { key: 'duplicate', label: 'Duplicate', icon: 'duplicate' },
    { key: 'rename', label: 'Rename', icon: 'rename' },
    { key: 'delete', label: 'Delete', icon: 'delete', destructive: true },
  ],
  page: 'Blossom canvas',
} as const;

export type Layout = {
  W: number;
  H: number;
  /** pt per reference pt */
  S: number;
  /** O on screen */
  ox: number;
  oy: number;
};

/** O keeps the pin's optical centre (y 392 of 874) and the whole menu scales by S = W / 402. */
export function makeLayout(W: number, H: number, insetTop: number, insetBottom: number): Layout {
  const S = 1;
  // centre between the safe edges at the reference ratio, never so high that the bead meets the status bar
  const usable = H - insetTop - insetBottom;
  const oy = Math.max(insetTop + (ORIGIN.y - REF.safeTop) * (usable / (REF.h - REF.safeTop - REF.safeBottom)), insetTop + 130 * S);
  return { W, H, S, ox: W / 2, oy };
}
