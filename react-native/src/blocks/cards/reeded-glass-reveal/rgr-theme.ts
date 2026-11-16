// Reeded Glass Reveal — every colour, size and copy, measured on the pin (design.md rows in the comments).
// Points on the 402 × 874 reference screen; the pin's card (402 × 681) sits at y 120 on it (CARD_Y).

export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;

/** The pin's card top on the reference screen (rectification, design.md). */
export const CARD_Y = 120;

export const ref = {
  color: {
    wall: '#E5E4EC', // glass: wall left/mid (also the page under the image while it loads)
    ink: '#F7F7F9', // title1, title2, rows (pin #FFFEFF, tinted)
    mute: '#959598', // stars, num, followers
    panel: '#161619', // panel / #161619 #151618 #171719
    panelLow: '#141417', // panel, bottom of its soft fall-off
    well: '#222224', // well1, well2
    plusWell: '#242328', // plus
    capsuleTop: '#1F1D20', // capsule body near the top (#2C2A2D next to the button is its light)
    capsuleLow: '#121013', // capsule body at the bottom (#141215)
    capsuleEnd: '#ACACB4', // capsule left end (the right end reads #999BA8)
    shadow: '#050407', // capsule shadow tint (page L × 0.25)
    swap: '#FFFFFD', // swap
    swapGlyph: '#202124', // swapGlyph
    thumbA: '#C9C9BC', // thumb, top-left (light frosted sage)
    thumbB: '#8E927E', // thumb, middle
    thumbC: '#5B7052', // thumb, bottom-right
    thumbInk: '#F4F5EF',
    thumbMute: '#DADDD2',
    netA: '#E9473A', // invented network mark (was the Mastercard pair): coral
    netB: '#F2B33D', // and amber
    vidly: '#EE2A2F', // mark1
    loopDisc: '#0B0B0D', // mark2
    loopCyan: '#2CF4EF',
    loopPink: '#FE2C55',
  },
  type: {
    // Urbanist (OFL), fitted to the pin's widths and cap heights (capRatio 0.70, figures 0.713)
    title: { family: 'RgrUrbanistRegular', size: 17, tracking: -0.01 },
    num: { family: 'RgrUrbanistLight', size: 15, tracking: 0 },
    row: { family: 'RgrUrbanistLight', size: 14.5, tracking: 0 },
    rowValue: { family: 'RgrUrbanistRegular', size: 14.5, tracking: 0 },
    thumbBrand: { family: 'RgrUrbanistRegular', size: 4.6 },
    thumbNum: { family: 'RgrUrbanistRegular', size: 5.4 },
    thumbSmall: { family: 'RgrUrbanistRegular', size: 3.5 },
  },
  /** Urbanist Light advances in em (hmtx), for laying the number out glyph by glyph in every stack. */
  advanceLight: {
    '0': 0.593,
    '1': 0.245,
    '2': 0.5365,
    '3': 0.4855,
    '4': 0.5355,
    '5': 0.547,
    '6': 0.535,
    '7': 0.53,
    '8': 0.5205,
    '9': 0.52,
    '*': 0.3495,
    ' ': 0.24,
  } as Record<string, number>,
  capRatio: 0.7,
  ascent: 0.95, // Urbanist hhea
  descent: 0.25,
  light: { az: 300, el: 35 },
} as const;

export const sys = {
  surface: {
    page: ref.color.wall,
    panel: ref.color.panel,
    panelLow: ref.color.panelLow,
    well: ref.color.well,
    plusWell: ref.color.plusWell,
    // the ground the capsule and the panel stand on: the portrait's black sweater, sampled on device (12, 12, 16)
    ground: '#0C0C10',
    groundClear: 'rgba(12,12,16,0)',
  },
  ink: { primary: ref.color.ink, secondary: ref.color.mute, onLight: ref.color.swapGlyph },
  glass: { top: ref.color.capsuleTop, low: ref.color.capsuleLow, end: ref.color.capsuleEnd, shadow: ref.color.shadow },
} as const;

/** Component boxes on the reference screen (design.md table). */
export const comp = {
  // glass (Pro upgrade 1): 22 flutes of 15.5-21 pt, each a wide-angle cylinder lens (rgr-shaders.ts GLASS).
  glass: {
    ribs: 22,
    smear: 2.6, // pt: cached defocus of the photo before the lens (x)
    smearY: 3.2, // pt: (y)
    kappa: -2.3, // how far a flute reads across the scene, in half-widths (1 = it joins its neighbours)
    tau: 1.15, // lens profile steepness: tan(tau u) / tan(tau); flank / crest magnification ~3.4
    slice: 0.0, // per-flute lateral offset of the view, in half-widths (the sliced look)
    spread: 0.4, // sideways spread of a deep flute about the face
    sag: 0.12, // vertical stretch at the seams (fraction), about the face's centre line
    edgeK: 0.9, // the outer flutes read this much further across the scene (x edge distance squared)
    blur: 1.4, // pt: screen-space softness of the lens image
    mag: 0.015,
    chroma: 1.6, // pt: red / blue split where the depth changes quickly from flute to flute
    glintW: 0.75, // pt: glint half width
    seam: 0.7, // pt: seam line half width
  },
  capsule: { x: 16, y: 472, w: 370, h: 99, r: 28 },
  capsuleHaze: 7, // pt: blur of the portrait the capsule's thick ends refract (sharp edges through 5 taps band into a fan)
  thumb: { x: 36, y: 491.3, w: 84.2, h: 59.2, r: 4 },
  title1: { x: 144.5, base: 510.8 },
  stars: { x: 143.4, drop: 3 },
  num: { x: 143.4, base: 543.3, restX2644: 171.9 },
  swap: { cx: 334.5, cy: 520.9, d: 60, glyphW: 18, glyphH: 12, stroke: 1.4 },
  panel: { x: 16, y: 584.5, w: 370, r: 32 },
  title2: { x: 37.6, base: 639.7 },
  plus: { cx: 334.5, cy: 633.9, d: 60, glyph: 12.3, stroke: 1.4 },
  rows: [
    { cy: 718.2, base: 723.2 },
    { cy: 787.2, base: 792.2 },
  ],
  row: { wellCx: 66.9, wellD: 60, name: 122.1, followers: 200.5, valueRight: 356.5 },
  mark1: { w: 23, h: 16, r: 4.5 },
  mark2: { d: 22 },
  shadow: { dy: 10, blur: 22, alpha: 0.45 },
} as const;

export const COPY = {
  title1: 'Bank Account',
  stars: '****',
  last4: '2644',
  full: ['4829', '1730', '2644'] as const,
  title2: 'Authenticated platform',
  rows: [
    { name: 'Vidly', followers: '122k  followers', value: '+ 120' },
    { name: 'Loop', followers: '834k  followers', value: '+ 220' },
  ],
  thumb: {
    brand: 'Sable',
    groups: '****  ****',
    last4: '2644', // three groups, like the revealed 4829 1730 2644
    expLabel: 'Expiry date',
    exp: '10/28',
  },
  a11y: {
    glass: 'Portrait behind reeded glass. Drag across it to clear the glass and show the account number.',
    hidden: 'Bank Account, number hidden, ending 2644',
    shown: 'Bank Account, number 4829 1730 2644',
    reveal: 'Show account number',
    hide: 'Hide account number',
    add: 'Add a platform',
  },
} as const;

/** Seeded generator, so every stack and every run cuts the same glass (mulberry32). */
function mulberry32(seed: number) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** The 22 flutes of the pin are not a ruler's width: 15.5 to 21 pt on the 402 pt screen (Pro upgrade 1), summing to the screen. */
const WIDTHS = (() => {
  const rnd = mulberry32(462604);
  const raw = Array.from({ length: comp.glass.ribs }, () => 0.87 + 0.25 * rnd());
  const sum = raw.reduce((a, b) => a + b, 0);
  return raw.map((v) => v / sum);
})();

/** Per-flute random in 0..1 (phase of its glints, size of its lens). Passed to the shader, whose own hash is not stable across GPUs. */
export const RIB_SEEDS: number[] = (() => {
  const rnd = mulberry32(2644);
  return Array.from({ length: comp.glass.ribs }, () => 0.04 + 0.92 * rnd());
})();

/** The three flutes a soft glint runs down at rest (non-adjacent, upgrade 6). */
export const RUN_RIBS = [4, 11, 17] as const;

/** Flute edges for a screen width: 23 numbers, first 0 and last W. */
export function ribEdges(W: number): number[] {
  const edges = [0];
  let acc = 0;
  for (let i = 0; i < WIDTHS.length; i++) {
    acc += WIDTHS[i];
    edges.push(i === WIDTHS.length - 1 ? W : acc * W);
  }
  return edges;
}

export type Layout = {
  W: number;
  H: number;
  S: number;
  x: (x: number) => number;
  /** y anchored to the bottom inset (capsule, panel and everything in them) */
  yb: (y: number) => number;
  /** the portrait image's rect, anchored to the capsule */
  img: { x: number; y: number; w: number; h: number };
  ribW: number;
  /** 23 flute edges (pt) and the 22 centres between them */
  edges: number[];
  centres: number[];
  /** front positions (screen pt) for progress 0 and 1: one rib width outside each edge */
  front0: number;
  front1: number;
};

export function makeLayout(W: number, H: number, insetBottom: number): Layout {
  const S = W / REF.w;
  const bottom = Math.max(insetBottom, 0);
  const yb = (y: number) => H - bottom - (REF.h - REF.safeBottom - y) * S;
  const capTop = yb(comp.capsule.y);
  const ribW = W / comp.glass.ribs;
  const edges = ribEdges(W);
  const centres = edges.slice(0, -1).map((e, i) => (e + edges[i + 1]) / 2);
  return {
    W,
    H,
    S,
    x: (x) => x * S,
    yb,
    img: { x: 0, y: capTop - comp.capsule.y * S, w: REF.w * S, h: REF.h * S },
    ribW,
    edges,
    centres,
    front0: -1.2 * ribW,
    front1: W + 1.2 * ribW,
  };
}

/** Layout of a glass of any size over any image (the generic component; the account screen passes its own image rect). */
export type GlassLayout = {
  W: number;
  H: number;
  S: number;
  ribW: number;
  edges: number[];
  centres: number[];
  front0: number;
  front1: number;
  img: { x: number; y: number; w: number; h: number };
  /** magnification centre (pt) */
  magC: number[];
};

export function glassLayout(
  W: number,
  H: number,
  imageRect?: { x: number; y: number; width: number; height: number },
  focus?: { x: number; y: number },
  imageAspect = 1206 / 2622,
): GlassLayout {
  const ribW = W / comp.glass.ribs;
  const edges = ribEdges(W);
  const centres = edges.slice(0, -1).map((e, i) => (e + edges[i + 1]) / 2);
  let img = imageRect ? { x: imageRect.x, y: imageRect.y, w: imageRect.width, h: imageRect.height } : null;
  if (!img) {
    // cover the box with the photo, centred
    const w = Math.max(W, H * imageAspect);
    const h = w / imageAspect;
    img = { x: (W - w) / 2, y: (H - h) / 2, w, h };
  }
  return {
    W,
    H,
    S: W / REF.w,
    ribW,
    edges,
    centres,
    front0: -1.2 * ribW,
    front1: W + 1.2 * ribW,
    img,
    magC: focus ? [focus.x, focus.y] : [W / 2, img.y + img.h * 0.34],
  };
}
