// Every colour, size and string of the block. Sizes are points on a 402 pt wide screen, scaled by width / 402.
// A solid brass padlock with a steel shackle and a nickel key, in a dark vault: Instrument Serif for the state, Inter
// for the rest.

export const STAGE_WIDTH = 402;

export const COLORS = {
  // A dark vault, one cool light from above pooling where the padlock hangs.
  bgTop: '#101614',
  bgBottom: '#0A0D0C',
  pool: 'rgba(206, 226, 214, 0.10)',
  shadow: 'rgba(0, 0, 0, 0.7)',
  // Solid brass: lit face, the side in its own shade, a warm specular.
  brassHigh: '#F3D58C',
  brass: '#C9973E',
  brassDeep: '#8A5E1F',
  brassSide: '#9A6A26',
  brassSideDeep: '#5E3E12',
  brassSpec: 'rgba(255, 246, 214, 0.9)',
  brassEdge: 'rgba(255, 236, 190, 0.75)',
  engrave: 'rgba(70, 44, 10, 0.55)',
  engraveLight: 'rgba(255, 238, 196, 0.45)',
  // Hardened steel shackle and a nickel key.
  steelHigh: '#F4F6F8',
  steel: '#A9B0B8',
  steelDeep: '#596069',
  steelEdge: 'rgba(255, 255, 255, 0.85)',
  plug: '#B98A36',
  slot: '#1A1208',
  glow: 'rgba(255, 214, 140, 0.55)',
  glint: 'rgba(255, 250, 230, 0.8)',
  ink: '#ECF1EE',
  mute: 'rgba(236, 241, 238, 0.56)',
  faint: 'rgba(236, 241, 238, 0.34)',
  error: '#F08A7A',
  sheet: '#141A18',
  sheetEdge: 'rgba(255, 255, 255, 0.08)',
  sheetLine: 'rgba(255, 255, 255, 0.07)',
  rowIcon: 'rgba(233, 205, 140, 0.12)',
  rowIconInk: '#E9CD8C',
  button: '#ECF1EE',
  buttonInk: '#101614',
};

export const LOCK = {
  topRatio: 0.36, // body top, of the height
  face: 150,
  height: 180,
  side: 22, // the 3/4 side face, as in the source
  radius: 18,
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
  titleSize: 44,
  titleLine: 50, // Instrument Serif reaches above its size: 6 pt of room
  titleFamily: 'VaultInstrumentSerif',
  ui: 'VaultInter',
  uiMedium: 'VaultInterMedium',
  uiBold: 'VaultInterSemiBold',
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
  eyebrow: 'PERSONAL VAULT · 3 ITEMS',
  locked: 'Locked',
  unlocked: 'Unlocked',
  hint: 'Slide the key in, then turn it right',
  wrongWay: 'The other way',
  meta: '3 items · end-to-end encrypted',
  head: 'Opened just now',
  sheetMeta: 'Opened just now',
  rows: [
    { title: 'Recovery codes', sub: '12 codes · added 3 days ago', icon: 'codes' },
    { title: 'Passport scan', sub: 'PDF · 2.4 MB · expires 2031', icon: 'doc' },
    { title: 'Home Wi-Fi', sub: 'Password · shared with 2', icon: 'wifi' },
  ],
  lock: 'Lock vault',
  unlock: 'Unlock vault',
  keyLabel: 'Key',
  keyHint: 'Swipe up to insert and turn the key',
};

