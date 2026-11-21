// Island Glass Orb: tokens. Positions are points on the 402 × 874 pt canvas (iPhone 17 Pro) and scale with the
// screen width; the island is the hardware cut-out and keeps its place under the status bar.

export const C = {
  sage: '#919E90',
  sageLit: '#A3AEA2',
  sageShade: '#7E8A7E',
  black: '#060707',
  ink: '#F7F7F5',
  cyan: '#64F4F9',
  blue: '#2B5BFF',
  magenta: '#FF2D8A',
  red: '#FF5A1F',
  warm: '#F5CD96',
  mic: '#FF7A1A',
  shadowTint: '#2A332B',
} as const;

/** Geometry of the island's three shapes and of the page (pt). */
export const G = {
  island: { x: 138, y: 11, w: 126, h: 37 },
  islandAntic: { w: 120, h: 40 },
  pill: { x: 82, y: 11, w: 237, h: 58 },
  banner: { x: 11, y: 10, w: 380, h: 126 },
  drop: { cx: 200, cy: 58, w: 196, h: 152, n: 2.6 },
  dropSmall: { cy: 30, w: 96, h: 24 },
  k: 28,
  lineLen: 152,
  spinner: { x: 112, y: 40, r: 10.5, dot: 5 },
  wL: { x: 28, y: 79, w: 162, h: 161, r: 24 },
  wR: { x: 212, y: 79, w: 162, h: 161, r: 24 },
  ask: { x: 46, y: 106, w: 127, h: 44, r: 22 },
  circles: [
    { x: 74, y: 195 },
    { x: 142, y: 195 },
  ],
  circleD: 58,
  textX: 232,
  row: { x: 232, barY: 172, barH: 35 },
  labelsY: 254,
  reply: { x: 45, y: 66 }, // Two 24 pt lines below the hardware cut-out, inside the taller reply.
  micDot: { x: 390, y: 26, d: 6 },
  shaderH: 330, // height of the blurred copy the banner's smoked glass reads (the shader itself covers the screen)
  dock: { x: 16, y: 762, w: 370, h: 84, r: 34 },
  // The photographed ring, uniformly ×1.15 (v2 stretched it 1.28 × 0.955) with its centre on (201, 490): canvas = off + s·photo.
  // The three mattes in assets/ are warped with exactly this transform, so they register to the pixel at 1206 × 2622.
  wallpaper: { s: 1.15, ox: -14.2455, oy: 15.4295 },
  // The tear-off (spec v3): the drop is drawn 250 pt away, the thread pinches, the bead settles on the ring, drifts, flies home.
  tear: { travel: 250, to: { x: 201, y: 308 }, rest: { x: 201, y: 440 }, drift: { x: 150, y: 372 }, R: 91 },
} as const;

export const ART_TOP = 276;

export const dockTopAt = (height: number) => G.dock.y + Math.min(0, height - 874);

/** Match the photographed sculpture, its mattes, and the detached orb on compact screens. */
export const artworkAt = (height: number) => {
  const scale = Math.min(1, Math.max(0.35, (dockTopAt(height) - 16 - ART_TOP) / (710 - ART_TOP)));
  return { scale, x: 201 * (1 - scale), y: ART_TOP * (1 - scale) };
};

/** Design-system springs (damping, stiffness, mass 1). */
export const SPRING = {
  glide: { d: 22.1, k: 157.9 },
  pop: { d: 19, k: 195 },
  snap: { d: 30.5, k: 322.3 },
  honey: { d: 10, k: 220 },
  recoil: { d: 12, k: 260 }, // the island after the pinch
  fly: { d: 16, k: 210 }, // the bead flying home
} as const;

/** Autoplay beats of the tear-off (s). The reply that follows keeps its v2 timing shifted by SHIFT. */
export const TIMES = {
  tear0: 3.0,
  pinch: 3.5,
  pinchDur: 0.08,
  settled: 3.95,
  fly0: 4.5,
  arrive: 4.678, // first time the spring {d 16, k 210} reaches home
  pill: 4.95,
  shift: 0.25,
  loop: 10,
} as const;

export const COPY = {
  time: '12:01',
  assistant: 'Nori',
  ask: 'Ask',
  day: 'THURSDAY',
  date: '12',
  tomorrow: 'TOMORROW',
  events: '2 Events',
  range: '6:00 – 7:30',
  calendar: 'Calendar',
  reply: 'Two events tomorrow. First at 6:00.',
} as const;

export const FAMILY = 'IGO-Inter';
/** Inter's display cut (opsz 32, Light) for the widget's big date. */
export const FAMILY_DISPLAY = 'IGO-InterDisplay';
