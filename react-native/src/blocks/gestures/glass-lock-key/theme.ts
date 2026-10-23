// Every colour, size and string of the block. Sizes are points on a 402 pt wide screen, scaled by width / 402.

export const STAGE_WIDTH = 402;

export const COLORS = {
  // Measured from the source: flat pale grey, frosted blue glass.
  bgTop: '#EEEEF0',
  bgBottom: '#F6F6F7',
  shadow: 'rgba(40, 46, 120, 0.16)',
  bodyTop: '#4A54EC',
  bodyBottom: '#2F37C9',
  bodyLight: 'rgba(150, 165, 255, 0.85)',
  bodyLightFar: 'rgba(150, 165, 255, 0)',
  side: '#7F8BFF',
  sideDeep: '#5862F0',
  rim: 'rgba(255, 255, 255, 0.85)',
  rimSoft: 'rgba(255, 255, 255, 0.35)',
  glass: 'rgba(132, 146, 255, 0.85)',
  glassDeep: 'rgba(84, 98, 238, 0.9)',
  glassEdge: 'rgba(255, 255, 255, 0.9)',
  plug: '#2A31B0',
  slot: '#151A6E',
  glow: 'rgba(130, 150, 255, 0.9)',
  glint: 'rgba(255, 255, 255, 0.75)',
  ink: '#16182E',
  mute: 'rgba(22, 24, 46, 0.55)',
  error: '#D6304A',
  sheet: '#FFFFFF',
  sheetLine: 'rgba(22, 24, 46, 0.08)',
  rowIcon: '#E9EBFF',
  rowIconInk: '#3D46E0',
  button: '#16182E',
  buttonInk: '#FFFFFF',
};

export const LOCK = {
  topRatio: 0.36, // body top, of the height
  face: 150,
  height: 180,
  side: 22, // the 3/4 side face, as in the source
  radius: 26,
  shackle: 30, // thickness: 0.2 of the face, as in the source
  opening: 68,
  shackleRise: 100, // locked: top of the shackle above the body
  shortLeg: 8, // the free leg's end below the body top when locked
  longLeg: 60,
  openLift: 58,
  openTilt: 0.12,
  plugWidth: 28,
  plugHeight: 9,
  slot: 3,
  glowRadius: 34,
};

export const KEY = {
  restRatio: 0.70, // tip at rest, of the height
  ring: 64, // outer diameter
  rim: 11,
  shaft: 11,
  blade: 100, // tip to the ring
  bitWidth: 18,
  bitHeight: 30,
  bitFromTip: 8,
  collar: 18,
  hit: 88,
};

export const TYPE = {
  side: 32,
  top: 24,
  eyebrowSize: 12,
  eyebrowTracking: 1.8,
  titleSize: 40,
  titleLine: 46,
  titleFamily: 'Georgia',
  hintSize: 15,
  hintLine: 20,
  gap: 8,
  bottom: 32,
};

export const SHEET = {
  heightRatio: 0.5,
  radius: 28,
  pad: 24,
  row: 64,
  icon: 40,
  iconRadius: 12,
  titleSize: 17,
  subSize: 13,
  headSize: 22,
  button: 52,
  rise: 0.075, // lock group rises this share of the height when open (it sits between the title and the sheet)
  shrink: 0.62,
};

// Spacing scale for everything the upgrade adds (the source shows only the key and padlock, so the scale is the
// 4 pt grid its iOS frame sits on). Radii nest: inner = outer − padding.
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

export const COPY = {
  eyebrow: 'VAULT',
  locked: 'Locked',
  unlocked: 'Unlocked',
  hint: 'Slide the key in, then turn it right',
  wrongWay: 'The other way',
  meta: '3 items · end-to-end encrypted',
  head: 'Personal vault',
  sheetMeta: 'Opened just now',
  rows: [
    { title: 'Recovery codes', sub: '12 codes · added 3 days ago', icon: '#', symbol: 'number' },
    { title: 'Passport scan', sub: 'PDF · 2.4 MB', icon: 'P', symbol: 'doc.text' },
    { title: 'Home Wi-Fi', sub: 'Password · shared with 2', icon: 'W', symbol: 'wifi' },
  ],
  lock: 'Lock vault',
  unlock: 'Unlock vault',
  keyLabel: 'Key',
  keyHint: 'Swipe up to insert and turn the key',
};

// Replica step (owner's fidelity gate): the source's render as Blender layers registered to the full screen
// (1206 × 2622 px = the source frame scaled to the screen width, centred). Off after the lead accepts.
export const REPLICA = false;
export const REPLICA_BG = '#EFEFEF'; // the source's studio grey

// Upgrade layout (points on a 402 pt wide screen) for the replica's registered layers.
export const UPG = {
  keyFront: { x: 200.7, y: 436.3 }, // the key-front layer's own centre (602, 1309 px)
  keyRest: { x: 200.7, y: 436.3 }, // the loose key rests where the source shows it, alone (key-front's own centre)
  plug: { x: 145.3, y: 461 }, // key-in's centre (436, 1383 px): carrying the key here seats it
  ring: { x: 107, y: 443 }, // key-in's ring, the turn handle
  insertAngle: -1.19, // rad: key-front turned into key-in's axis
  lockCentre: { x: 201, y: 445 },
  darkCentre: { x: 220, y: 470, r: 120 }, // the body's deep-blue centre (the source's)
  darkColor: 'rgba(20, 24, 120, 0.55)',
  glow: { r: 260, color: 'rgba(80, 96, 255, 0.35)' }, // Liquid Glass hue field behind the lock
  bg: ['#F2F2F6', '#E6E8F3'],
};

// Per-layer registration to the source, in source px (1206 × 2622), fitted by edge chamfer against the reference
// frames: [scaleX, scaleY, rotation° (counter-clockwise on screen), dx, dy] about a centre.
export const REPLICA_FIT = {
  lockCentre: [660, 1416] as const, // shadow, shackles and body
  keyCentre: [436, 1383] as const, // the key in the keyhole
  frontCentre: [602, 1309] as const, // the lone key
  lock: [1.01, 1.01, 0, 0, 0] as const,
  shackleClosed: [3, 0] as const, // px, on top of `lock`
  shackleOpen: [-4, 1] as const,
  keyClosed: [1, 1, 0, 0, 0, 0] as const, // assets/key-in-a.png: the key as in ref a, rendered in that pose
  keyOpen: [0.95, 0.885, 0, 39, 25.5, -0.075] as const, // assets/key-in.png turned, shackle up (ref b); last value: shear x per y
  front: [1.01, 0.99, 0, 0, 2] as const,
};
