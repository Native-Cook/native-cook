// Elastic String Quiz: tokens. Positions are points on the 402 × 874 pt canvas and scale with the screen width
// (S = width / 402); the tree is centred vertically, the HUD sits under the status bar.

export const C = {
  fieldCentre: '#752C48',
  fieldMid: '#371737',
  fieldEdge: '#2B112C',
  fieldCool: '#5A1E3A',
  accent: '#FD5082',
  heat: '#FFF8FC',
  hud: '#E8476F',
  ink: '#FFF6FA',
  ink2: '#8E6F86',
  ink3: '#7A5670',
  /** cords and beads run from magenta on the left to rose on the right */
  cord: ['#9C3A7E', '#A8396B', '#B83A5E'],
  bead: ['#BC4388', '#C0437A', '#BC4269'],
} as const;

/** Tree geometry (pt). */
export const G = {
  hubX: 201,
  hubY: 300,
  wordY: 239, // visual centre of the prompt word
  wordBase: 257, // Barlow 52 pt, visual centre at y 239
  shoulderY: 343,
  beadY: 640,
  labelY: 692,
  labelBase: 700,
  bx: [81, 201, 321],
  corner: 10,
  hudY: 74, // HUD row centre on an 874 pt screen
  fieldX: 201,
  fieldY: 400,
} as const;

/** Rest lengths of the three cords (hub → shoulder → bead), for the pulse's travel time. */
const diag = Math.hypot(G.bx[2] - G.hubX, G.shoulderY - G.hubY);
export const LREST = [diag + (G.beadY - G.shoulderY), G.beadY - G.hubY, diag + (G.beadY - G.shoulderY)];
export const PULSE_SPEED = 1400; // pt/s

/** Design-system springs (damping, stiffness, mass 1) and the cord's own elastic recoil. */
export const SPRING = {
  pop: { d: 19, k: 195 },
  snap: { d: 30.5, k: 322.3 },
  cord: { d: 9, k: 420 },
} as const;

export const FONT = {
  medium: 'ESQ-Barlow-Medium',
  semi: 'ESQ-Barlow-SemiBold',
  bold: 'ESQ-Barlow-Bold',
} as const;


export type Prompt = { word: string; answer: number };

export const DEFAULT_ANSWERS = ['anchor', 'gather', 'wander'];
export const DEFAULT_PROMPTS: Prompt[] = [
  { word: 'drift', answer: 2 },
  { word: 'muster', answer: 1 },
  { word: 'roam', answer: 2 },
  { word: 'moor', answer: 0 },
  { word: 'amass', answer: 1 },
  { word: 'stray', answer: 2 },
  { word: 'fix', answer: 0 },
  { word: 'collect', answer: 1 },
];
