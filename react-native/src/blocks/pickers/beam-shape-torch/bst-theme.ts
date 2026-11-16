// Beam Control — every colour, size and copy token, measured on the pin (pin 735916395435313288, a 2025
// render of a phone, rectified onto the 402 × 874 pt canvas: `.work/beam-shape-torch/pin-rectified-1206.png`).
// Row ids in the comments refer to the measurement table in `.work/beam-shape-torch/design.md`.
// Only this file holds literal colours and sizes; the component, the shader and the motion read them from here.

export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;

export const ref = {
  color: {
    // wallpaper (row wp): shown while the image decodes so nothing shifts; the image is drawn over it
    wallTop: '#6E1F4E', // top-left wine, 5 × 5 mean
    wallMid: '#B8538B', // right mid
    wallLow: '#982168', // below the ring ridge
    // capsule (row cap): black glass, lit only by the beam
    capTop: '#020203', // (176, 60) → #000000, tinted off pure black
    capMid: '#09080B', // the body between the walls the beam reaches
    capLow: '#08080A', // (95, 430) → #0A0D0B
    wallLight: '#C9C4D6', // light the beam scatters onto the inner walls (lit wall #393437 at (120, 300) = this × 0.22)
    // the beam (row beam)
    core: '#FDFDFB', // lens core (176, 305)
    coneBody: '#D5D1F2', // cone half-way (176, 260) #827E94 is this at 0.75 intensity
    // the arc, the sun and the torch (rows arc, sun, glyph)
    arc: '#B4B2BE',
    dots: '#8E8C99',
    sun: '#B6B4C0',
    glyphTop: '#FDFCFE', // (176, 395)
    glyphLow: '#E9E5F6', // the lilac the beam's tint leaves on the body
    glyphOff: '#5D5A68', // 45 % luminance when the lamp is off
    glyphGap: '#1A191E', // the dark band under the head's cap
    // shadow on the wallpaper (page L × 0.25)
    shadow: '#1E0815',
    rim: '#FFFFFF',
    spill: '#FFFFFF',
    ceiling: '#F6C9E6',
    afterimage: '#2A2440',
  },
  // the capsule: the Dynamic Island grown into a panel (rows cap, island)
  capsule: { x: 82, y: 11, w: 240, h: 430, r: 56 },
  island: { w: 126, h: 37, y: 11 },
  // inside the capsule, from its top-left corner (rows lens, glyph, arc, sun)
  lens: { x: 119, y: 276 },
  glyph: { x: 105, y: 294, w: 28, h: 79 },
  // the arc at the pin's state (b 1, w 1): apex 135 pt above the lens, ends ± 72 pt, 34 pt below the apex
  arc: { reach: 135, halfWidth: 72, sag: 34, stroke: 1.5 },
  dots: { size: 1.7, pitch: 5.5 },
  sun: { along: 19, ring: 3.8, rayIn: 6.6, rayOut: 7.6, stroke: 1.1, rays: 8 },
  // the shader's look
  beam: {
    thetaWide: 40, // half-angle, degrees, measured on the cone's rainbow edges (41.6 / 39.5)
    thetaNarrow: 4, // the pencil (motion reference, narrow frames)
    fringeWidth: 3.2, // degrees at the narrow end, + 4.6 × w at the wide end
    fringeSat: 0.6, // DESIGN-SYSTEM §6: dispersion saturation ≤ 0.6
  },
  // shadows of the floating capsule on the wallpaper (DESIGN-SYSTEM §6, e3, tinted)
  elevation: { contact: [0, 2, 4, 0.32], ambient: [0, 18, 40, 0.42] },
  light: { az: 0, el: 90, color: '#FDFDFB' }, // the only light is the torch, inside the capsule
} as const;

export const sys = {
  surface: { page: ref.color.wallTop },
  hero: { core: ref.color.core, body: ref.color.coneBody, wall: ref.color.wallLight },
  ink: { arc: ref.color.arc, dots: ref.color.dots, sun: ref.color.sun },
} as const;

export const COPY = {
  label: 'Beam control',
  hintOpen: 'Drag up or down to set the brightness, toward or away from the beam to focus it, or drag the sun. Tap to switch it on or off.',
  hintClosed: 'Opens the beam control',
  narrow: 'Focus the beam',
  widen: 'Widen the beam',
  close: 'Close the beam control',
} as const;

/** What a host can change (the `theme` prop); every field falls back to the pin's value. */
export type BeamTheme = {
  /** the glass's colour at the middle of its gradient (the pin: #09080B) */
  glass?: string;
  /** the light's colour at the lens (the pin: #FDFDFB) */
  light?: string;
  /** the arc, its dots and the sun (the pin: #B4B2BE) */
  arc?: string;
  /** dispersion at the cone's edge, 0 none … 1 the pin … 1.5 (default 1) */
  fringe?: number;
  /** the page's share of the light, 0 none … 1 the pin's glow (default 1) */
  spill?: number;
};

export type ResolvedTheme = { glass: number[]; light: number[]; arc: string; fringe: number; spill: number };

function rgb(hex: string, fallback: string): number[] {
  const h = /^#[0-9a-fA-F]{6}$/.test(hex) ? hex : fallback;
  return [parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255];
}

export function resolveTheme(t?: BeamTheme): ResolvedTheme {
  return {
    glass: rgb(t?.glass ?? ref.color.capMid, ref.color.capMid),
    light: rgb(t?.light ?? ref.color.core, ref.color.core),
    arc: t?.arc ?? ref.color.arc,
    fringe: Math.min(Math.max(t?.fringe ?? 1, 0), 1.5),
    spill: Math.min(Math.max(t?.spill ?? 1, 0), 1),
  };
}

/** Where the open capsule sits: under the Dynamic Island (the default), or in a rectangle of the host's choosing. */
export type BeamAnchor = 'island' | { x: number; y: number; width: number; height: number };

export type BstLayout = {
  W: number;
  H: number;
  S: number;
  /** the open capsule, px */
  cap: { x: number; y: number; w: number; h: number; r: number };
  /** the island the capsule closes into, px */
  isl: { x: number; y: number; w: number; h: number; r: number };
  /** a point inside the open capsule (design pt from its top-left) → px */
  ix: (x: number) => number;
  iy: (y: number) => number;
};

/**
 * The capsule hangs from the island, so it is anchored to the top of the screen, not to the safe area: on a phone
 * with a Dynamic Island (top inset ≥ 47) it starts at the island's top (11 pt); without one it starts 6 pt under
 * the status bar. Its size and everything inside scale with the width (S = W / 402). Given a rectangle instead, the
 * glass fills it and the content (240 × 430 pt at S = 1) is centred in it at the scale that fits; it closes into a
 * pill at the rectangle's top, as wide as the island is to the capsule.
 */
export function makeLayout(W: number, H: number, insetTop: number, anchor: BeamAnchor = 'island'): BstLayout {
  if (anchor !== 'island') {
    const S = Math.max(Math.min(anchor.width / ref.capsule.w, anchor.height / ref.capsule.h), 0.05);
    const ox = (anchor.width - ref.capsule.w * S) / 2;
    const oy = (anchor.height - ref.capsule.h * S) / 2;
    const cap = { x: anchor.x, y: anchor.y, w: anchor.width, h: anchor.height, r: ref.capsule.r * S };
    const iw = ref.island.w * S;
    const ih = ref.island.h * S;
    const isl = { x: anchor.x + (anchor.width - iw) / 2, y: anchor.y, w: iw, h: ih, r: ih / 2 };
    return { W, H, S, cap, isl, ix: (x) => cap.x + ox + x * S, iy: (y) => cap.y + oy + y * S };
  }
  const S = W / REF.w;
  const hasIsland = insetTop >= 47;
  const top = hasIsland ? ref.island.y : insetTop + 6;
  const w = ref.capsule.w * S;
  const h = Math.min(ref.capsule.h * S, H - top - 120 * S);
  const cap = { x: (W - w) / 2, y: top, w, h, r: ref.capsule.r * S };
  const isl = {
    x: (W - ref.island.w) / 2,
    y: top,
    w: ref.island.w,
    h: ref.island.h,
    r: ref.island.h / 2,
  };
  return { W, H, S, cap, isl, ix: (x) => cap.x + x * S, iy: (y) => cap.y + y * S };
}
