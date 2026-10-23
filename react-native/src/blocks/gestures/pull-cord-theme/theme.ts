// Every colour and size of the block. Sizes are points on a 402 pt wide screen and are scaled by width / 402.

export type Palette = {
  wallTop: string;
  wallBottom: string;
  panelShadow: string;
  panelLight: string;
  spill: string;
  spillFar: string;
  halo: string;
  glassTint: string;
  glassEdge: string;
  glassEdgeLit: string;
  glassSpecular: string;
  filamentCore: string;
  filamentGlow: string;
  support: string;
  wire: string;
  metal: string[];
  metalStops: number[];
  thread: string;
  cord: string;
  cordLit: string;
  brass: string[];
  brassStops: number[];
  ink: string;
  mute: string;
};

export const DARK: Palette = {
  // Measured from the source: wall #0C0C0C–#0E1215, blue spill #19498F on the panel edges.
  wallTop: '#0B0C0E',
  wallBottom: '#0D1116',
  panelShadow: '#030405',
  panelLight: '#3C7EF2',
  spill: 'rgba(36, 104, 230, 0.34)',
  spillFar: 'rgba(36, 104, 230, 0)',
  halo: 'rgba(52, 132, 255, 0.55)',
  glassTint: 'rgba(40, 112, 255, 0.22)',
  glassEdge: 'rgba(120, 186, 255, 0.55)',
  glassEdgeLit: '#BFE0FF',
  glassSpecular: 'rgba(235, 246, 255, 0.85)',
  filamentCore: '#E4F3FF',
  filamentGlow: '#2F86FF',
  support: 'rgba(170, 210, 255, 0.35)',
  wire: '#30343A',
  metal: ['#2A2D31', '#7D838C', '#E9EDF2', '#9AA0A9', '#3A3E44', '#1C1E21'],
  metalStops: [0, 0.22, 0.42, 0.62, 0.84, 1],
  thread: 'rgba(0, 0, 0, 0.55)',
  cord: '#3A414B',
  cordLit: 'rgba(110, 170, 255, 0.55)',
  brass: ['#2E2414', '#7A5E2E', '#D6B676', '#8E6C34', '#2A2012'],
  brassStops: [0, 0.25, 0.45, 0.7, 1],
  ink: '#EEF3FA',
  mute: 'rgba(238, 243, 250, 0.56)',
};

export const LIGHT: Palette = {
  wallTop: '#F3EEE6',
  wallBottom: '#E7DFD2',
  panelShadow: '#CBBFAE',
  panelLight: '#FFFFFF',
  spill: 'rgba(255, 196, 110, 0.5)',
  spillFar: 'rgba(255, 196, 110, 0)',
  halo: 'rgba(255, 184, 92, 0.6)',
  glassTint: 'rgba(255, 228, 176, 0.3)',
  glassEdge: 'rgba(120, 104, 82, 0.5)',
  glassEdgeLit: '#FFFFFF',
  glassSpecular: 'rgba(255, 255, 255, 0.95)',
  filamentCore: '#FFF8E6',
  filamentGlow: '#FFA733',
  support: 'rgba(120, 96, 60, 0.35)',
  wire: '#2F2A25',
  metal: ['#4A4D52', '#9DA2AA', '#F6F7F9', '#B4B9C0', '#5A5E64', '#3A3C40'],
  metalStops: [0, 0.22, 0.42, 0.62, 0.84, 1],
  thread: 'rgba(40, 32, 20, 0.45)',
  cord: '#3A332B',
  cordLit: 'rgba(255, 255, 255, 0.5)',
  brass: ['#5E461E', '#B48A42', '#F6DFA4', '#C29A52', '#5A4320'],
  brassStops: [0, 0.25, 0.45, 0.7, 1],
  ink: '#1D1A16',
  mute: 'rgba(29, 26, 22, 0.58)',
};

// Warm light that rises inside the glass like a liquid (the alt source).
export const LIQUID = {
  top: 'rgba(255, 249, 232, 0.96)',
  bottom: 'rgba(255, 186, 92, 0.92)',
  meniscus: '#FFFFFF',
  lightAlpha: 0.5, // in the light room the warm sprite already glows; the liquid only tints it
  glow: '#FFA733',
  filament: '#FFC261',
  core: '#FFF8E6',
  spill: 'rgba(255, 190, 100, 0.42)',
  spillFar: 'rgba(255, 190, 100, 0)',
  halo: 'rgba(255, 184, 92, 0.6)',
  haloFar: 'rgba(255, 184, 92, 0)',
};

export const STAGE_WIDTH = 402;

/**
 * Spacing scale (step 0, 20 Oct): 4 pt base, from the source's panel bevel (3–4 pt) and the stiles' 12.5 % inset.
 * The upgrade's additions sit on it: the copy block 32 pt from the left and 48 pt from the foot with 8 pt between
 * its lines, and the cord 32 pt from the right edge, so the two margins mirror each other.
 */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

// Bulb, measured from the source frame f012 at 0.558 pt per px. Local frame: origin at the socket's top centre.
export const BULB = {
  socketTopRatio: 0.265, // of the screen height
  wire: 700, // pivot above the socket top
  socketWidth: 56,
  socketHeight: 50,
  capWidth: 40,
  capHeight: 6,
  threads: 5,
  neckTop: 46,
  neckHalf: 25,
  radius: 68.5,
  centerY: 156.5,
  bottom: 225,
  stemHalf: 8,
  stemEnd: 108,
  armHalf: 30,
  foot: 146,
  wireWidth: 2.6,
  halo: 170,
};

export const CORD = {
  xRatio: 1 - SPACE.xxl / STAGE_WIDTH, // of the width: 32 pt from the right edge (was 0.925, 30 pt), mirroring the copy's left margin
  top: -14,
  restYRatio: 0.56, // bead top at rest, of the height
  points: 18,
  width: 2.6,
  beadWidth: 15,
  beadHeight: 36,
  beadRadius: 7.5,
  crimpWidth: 7,
  crimpHeight: 6,
  hit: 56,
};

// Neon glow of the filament: wide soft bloom, tight bloom, core.
export const GLOW = {
  wide: 12,
  wideBlur: 10,
  wideAlpha: 0.6,
  tight: 5,
  tightBlur: 3,
  core: 2.2,
  innerAlpha: 0.5, // blue light filling the dark scene's glass
};

export const PANEL = {
  insetRatio: 0.125, // the source's panel edges at 12.5 % and 87.5 %
  bottomFromEnd: 36,
  bevel: 3,
  line: 1.5,
  lip: 4, // the lit lip of each stile
  lipBlur: 3,
};

export const TYPE = {
  margin: SPACE.xxl,
  bottom: SPACE.xxl + SPACE.lg,
  eyebrowSize: 12,
  eyebrowTracking: 1.8,
  titleSize: 48,
  titleFamily: 'Georgia',
  titleLine: 54,
  hintSize: 15,
  hintLine: 20,
  gap: SPACE.sm,
};

export const COPY = {
  eyebrow: 'APPEARANCE',
  dark: 'Dark',
  light: 'Light',
  hint: 'Pull the cord to switch',
  a11yLabel: 'Light cord',
  a11yHint: 'Switches the app between dark and light theme',
};

// Replica step (owner's fidelity gate): show only what the source shows, framed like ref.py fit
// (the 720 × 1280 source scaled ×2.048 to 2622 px tall, centre-cropped to 1206 px). Off after the lead accepts.
export const REPLICA = false;
export const FIT = { k: 2.048 / 3, cropX: -134 / 3 }; // source px → pt, and the crop's x offset in pt

export const REPLICA_ART = {
  // assets/wall.jpg was generated with its stiles at 148 / 1056 px; the source's are at 58 / 1148 px.
  wallX: (58 - 148 * 1.2) / 3,
  wallW: (1206 * 1.2) / 3,
  // assets/bulb-*.png: 480 × 720 px, socket top at (240, 30) px, glass 408 px wide; the source's glass is
  // 245 source px wide, i.e. 502 px on screen.
  spriteScale: 1.26 / 3, // template-matched against the three refs (1.25–1.27 sprite px per screen px)
  spriteW: 480,
  spriteH: 720,
  anchorX: 240,
  anchorY: 30,
  socketTopSrc: 370.6, // source px: socket top at 759 screen px in the refs
  glassCenterSrc: 205, // below the socket top, source px
  swingSrc: 82, // ± source px at the glass: ±169.5 screen px at the socket in the refs (measured on device)
  centreSrc: 367.9, // swing centre at 619.5 screen px
  halo: 170, // pt: a tight soft glow close to the bulb, as in the source
  haloColor: 'rgba(40, 120, 240, 0.12)',
  spill: 'rgba(20, 90, 255, 0.24)',
  spillR: 0.8, // of the width
  spillFall: ['rgba(30, 95, 235, 0.2)', 'rgba(30, 95, 235, 0.1)', 'rgba(30, 95, 235, 0)'], // slower falloff, as in the refs
  spillStops: [0, 0.55, 1],
  ambient: ['rgba(40, 90, 210, 0.05)', 'rgba(40, 90, 210, 0.025)'], // the lit room's faint blue reaching the far wall
  ambientR: 1.6, // of the width
  grabRadius: 110, // a touch this close to the glass centre swings the bulb
  wire: 2000, // pt: the wire hangs from far above the frame, as in the source
  wireColor: '#2E3136',
  wireWidth: 6,
  // The sprite's bright pixels already match the source (BGR 247,184,63 vs 243,179,62). Bloom: a bright-pass copy
  // (only what is above half brightness survives), blurred and screened, so the filament and rim glow, not the glass.
  // The glass interior must stay a clear dark blue: a contrast curve (×1.5 − 0.3) sinks the sprite's mid-tones.
  interior: [1.12, 0, 0, 0, -0.05, 0, 1.12, 0, 0, -0.05, 0, 0, 1.12, 0, -0.05, 0, 0, 0, 1, 0],
  // Tinted toward #1F7DFF so the bloom outside the glass is blue, not a white haze.
  brightPass: [0.25, 0, 0, 0, -0.12, 0, 1, 0, 0, -0.5, 0, 0, 2, 0, -1, 0, 0, 0, 1, 0],
  bloom: 10,
  bloomOpacity: 0.55,
  // The refs' dark wall is blue-black (BGR 19/12/8); the generated wall is neutral.
  lightMap: 1, // QA: 0 while baking the light maps, 1 in use
  wallTint: [0.48, 0, 0, 0, 0, 0, 0.6, 0, 0, 0, 0, 0, 0.6, 0, 0, 0, 0, 0, 1, 0], // the refs' unlit wall is ~10/10/10
  // Door stiles lit by the bulb, measured in the fitted reference (px): lips at 50–75 and 1131–1162, brightest at
  // y 1300, gone by 850 and 1800; the bottom rail's lit line at 2500–2522.
  stileLeft: [28, 78], // the door's outer edge and its stile lip, both lit
  stileRight: [1128, 1182],
  stileGlow: [850, 1300, 1800],
  stileColor: 'rgba(36, 104, 210, 0.85)',
  railY: [2498, 2524],
  railColor: 'rgba(18, 52, 120, 0.3)',
};
