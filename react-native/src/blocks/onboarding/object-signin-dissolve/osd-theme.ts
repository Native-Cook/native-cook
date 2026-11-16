// Object Sign-In Dissolve — every colour, size and string, measured off the pin (Dribbble 27667369) on the 402 x 874 pt
// canvas, then translated: the banana and its wordmark are an invented pear called "Pome". Tokens are [dark, light].
// Layout anchors follow DESIGN-SYSTEM §3: the top group keeps its distance from the safe top, the bottom group from the
// bottom inset, the pear stays centred on the screen's middle.

export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;

export const ref = {
  color: {
    ground: ['#0A0610', '#FBF7EE'],
    pool: ['#231B2C', '#FFF1D6'], // the lighter pool behind the pear
    poolGlow: ['#FF3EA5', '#FFC2E0'], // what the shell tints it with (shell's own colour, never a new hue)
    ink: ['#F7F3FA', '#14101A'],
    mute: ['#7C7585', '#8E8579'], // tagline
    legal: ['#5E5867', '#A39A8C'],
    pill: ['#FBFAFC', '#15101B'],
    pillInk: ['#0F0B14', '#FBF7EE'],
    half: ['#1A1620', '#EFE9DD'],
    halfInk: ['#D8D3DF', '#2A2430'],
    themeBtn: ['#150F1B', '#FFFFFF'],
    sun: '#F5A623',
    moon: '#F2B86A',
    shell: '#FF3EA5',
    seam: '#FFD23E',
    seamCore: '#FFF6D8',
    shellWord: '#FF8CC8',
    shellPill: '#FFB3DA',
  },
  type: {
    // Instrument Serif Italic (OFL) stands in for the pin's condensed italic serif; set at 90 % width by tracking
    wordmark: { family: 'OsdInstrumentSerif', size: 54, tracking: -0.015 },
    tagline: { family: 'OsdInter', size: 13, tracking: 0 },
    button: { family: 'OsdInterSemi', size: 16, tracking: -0.01 },
    half: { family: 'OsdInterMed', size: 14, tracking: -0.005 },
    legal: { family: 'OsdInter', size: 11, tracking: 0 },
  },
  // rest layout (pt, 402 canvas); y of the pear is its centre
  pear: { cy: 338, h: 344, w: 168 },
  theme: { cx: 360, cy: 81, d: 40 },
  wordmark: { baseline: 598 },
  tagline: { baseline: 627 },
  pill: { x: 28, y: 662, w: 346, h: 56, r: 16 },
  half: { y: 731, h: 48, w: 165, gap: 16, r: 14 },
  legal: { y1: 817, y2: 834 },
  light: { az: 325, el: 40, kelvin: 4800 },
  // the pool, radius 190 pt, centre on the pear
  pool: { r: 190 },
  wipe: { cx: -80, cy: 954, max: 1100 }, // centre 80 pt outside the bottom-left corner, grows to cover the screen
} as const;

export const COPY = {
  wordmark: 'Pome',
  tagline: 'Taste comes first',
  continue: 'Continue with email',
  google: 'Passkey',
  wallet: 'Phone',
  legalA: 'By continuing you agree to our ',
  terms: 'Terms',
  and: ' and ',
  privacy: 'Privacy Policy',
  legalB: 'Ripe is a decision. Make it.',
  toDark: 'Switch to dark theme',
  toLight: 'Switch to light theme',
} as const;

/** Layout for a W x H screen: x scales with width, the top group hangs from the inset, the bottom group from the bottom. */
export function makeLayout(W: number, H: number, insetTop: number, insetBottom: number) {
  const S = Math.min(W / REF.w, 1.12);
  const x = (v: number) => (W - REF.w * S) / 2 + v * S;
  // the pear's group is centred on the room between the header and the wordmark
  const yt = (y: number) => insetTop + (y - REF.safeTop) * S;
  const yb = (y: number) => H - insetBottom - (REF.h - REF.safeBottom - y) * S;
  const yc = (y: number) => H * (ref.pear.cy / REF.h) + (y - ref.pear.cy) * S;
  return { W, H, S, x, yt, yb, yc };
}
export type Layout = ReturnType<typeof makeLayout>;
