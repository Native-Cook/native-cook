// Every colour and size of the block. Sizes are points on the source's 402 × 874 pt screen,
// measured from the rectified frames (`.work/ref/cassette-deck-*.png`, 3 px per point), and scaled by width / 402.

export const SCREEN = { w: 402, h: 874 };

export const COLOR = {
  // Measured: flat #242225 with a faint lift above the cassette and a darker pool under it.
  bg: '#242225',
  bgLift: 'rgba(255, 252, 255, 0.045)',
  bgPool: 'rgba(0, 0, 0, 0.16)',
  grille: '#19181B',
  grilleRim: 'rgba(255, 255, 255, 0.05)',
  ledOff: '#3A393D',
  ledOn: '#35E39A',
  ledGlow: 'rgba(53, 227, 154, 0.55)',
  glyphOff: '#2D2C30',
  glyphOn: '#E9E8EC',
  glyphGlow: 'rgba(255, 255, 255, 0.35)',
  labelInk: '#1C1B1D',
  labelMute: '#5E5C60',
  wordmark: '#ECEAE4',
  pillTop: '#A9A9AB',
  pillBottom: '#7E7E81',
  pillInk: '#3A3A3C',
  tape: '#2B2421',
  tapeEdge: 'rgba(150, 118, 92, 0.35)',
  // A narrow band of the key light, soft at both ends (expo-linear-gradient stops).
  sheen: ['rgba(255,255,255,0)', 'rgba(255,255,255,0.06)', 'rgba(255,255,255,0.13)', 'rgba(255,255,255,0.06)', 'rgba(255,255,255,0)'] as const,
  // The shell's side faces: lit from the top left, so the left face is brighter.
  edgeL: ['#2A292C', '#141316'] as const,
  edgeR: ['#111013', '#08080A'] as const,
  // The room takes the hue of the loaded cassette's cover (track 1 keeps the source's neutral lift).
  tints: [[255, 252, 255, 0.045], [150, 140, 255, 0.085], [80, 220, 190, 0.075]],
  redSpill: 'rgba(230, 40, 64, 0.32)',
  ledSpill: 'rgba(53, 227, 154, 0.10)',
};

/**
 * Spacing scale (step 0, 20 Oct), derived from the source's measured gaps: 4 pt base; 16 ≈ the side margin of the
 * cassette (16.7) and the pill (17.7), 32 ≈ half a key (32.5). Source elements keep their measured positions; only
 * the upgrade's additions sit on this scale.
 */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

// Absolute positions in points on the 402 × 874 screen.
export const LAYOUT = {
  grille: { cx: 44.3, cy: 88.7, pitch: 6.6, dot: 2.1, radius: 20.5 },
  led: { cx: 374.3, cy: 77.7, r: 2.1 },
  cassette: { x: 16.67, y: 223.33, w: 370, h: 240 },
  // Inside the cassette frame.
  reels: [{ cx: 119.7, cy: 106.3 }, { cx: 248, cy: 106.3 }],
  reelSize: 70,
  // The dark band through the label, where the tape packs show around the hubs.
  window: { x: 95, y: 80.7, w: 179, h: 49.3 },
  tapePack: { min: 26, max: 62 },
  cover: { x: 38.3, y: 35, size: 37.3, radius: 1 },
  title: { x: 86, y: 36.5, size: 16, tracking: -0.6 },
  artist: { x: 86, y: 56, size: 11.2, tracking: -0.1 },
  wordmark: { right: 43.3, y: 145.5, size: 11.6, tracking: 1.6 },
  keys: { x0: 60, pitch: 53.75, y: 588.3, w: 65, h: 96.67, travel: 3.3 },
  glyphs: { y: 580.7, size: 10, x0: 92.5 },
  pill: { x: 17.7, y: 805.7, w: 31.3, h: 20 },
};

export const TRACKS = [
  { title: 'Glass Hours', artist: 'Northern Pilots', cover: 0 },
  { title: 'Afterglow', artist: 'Northern Pilots', cover: 1 },
  { title: 'Undertow', artist: 'Northern Pilots', cover: 2 },
];

// An invented wordmark in the source's bold extended style.
export const BRAND = 'VELOR';

export const KEY_LABELS = ['Play', 'Pause', 'Next track', 'Rewind', 'Fast forward'];
