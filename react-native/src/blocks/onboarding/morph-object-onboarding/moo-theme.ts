// Morph Object Onboarding — layout, colour and copy.
//
// Measured on the pin's first phone (a 1200 × 900 px mock-up of three phones; the first one's
// screen spans x 101 … 392, y 156 … 790) and mapped to a 402 × 874 pt reference screen:
// x → (px − 101) · S, y → (px − 156) · S.

export const REF_W = 402;
export const REF_H = 874;
export const S = REF_W / 291;
export const X = (px: number) => (px - 101) * S;
export const Y = (px: number) => (px - 156) * S;

export const PAGES = [
  {
    title: ['We really value', 'your time'],
    sub: ['The classes last for 60 minutes,', 'you can schedule for any day'],
    glow: '#FFB300',
  },
  {
    title: ['You will', 'learn online'],
    sub: ["You won't need to spend money", 'and time going to school.'],
    glow: '#7A5CFF',
  },
  {
    title: ['You will learn', 'correctly'],
    sub: ['Our teachers hold a special', 'international certificate'],
    glow: '#FF6A3D',
  },
];

export const COPY = { skip: 'Skip', next: 'Continue Now', start: "Let's start" };

export const C = {
  bg: '#18171F',
  card: '#FFFFFF',
  ink: '#111014',
  sub: '#8B8A92',
  button: '#18171F',
  buttonInk: '#FFFFFF',
  dot: '#4A4954',
  pill: '#FFFFFF',
  skip: '#FFFFFF',
  sparkle: '#FF8FC0',
};

export const SIZE = { title: 26, sub: 15.6, button: 16, skip: 13.5 };

export const UI = {
  // the pin's top row sits where a real phone's status bar is; it moves just below it (and stays tappable)
  dots: { x: X(122), y: 68, pill: 25, dot: 3.8, gap: 8.2, h: 3.6 },
  skip: { x: X(369), y: 68 },
  card: { y: Y(530), r: 40 },
  title: [Y(577), Y(604)],
  sub: [Y(642), Y(661)],
  button: { x: X(162), y: Y(715), w: X(330) - X(162), h: Y(757) - Y(715) },
  sparkle: { x: X(183), y: Y(268), s: 30 },
};

/**
 * The morph: 33 Blender frames (A clock 0, B laptop 16, C book 32) in a 6 × 6 atlas. `box` is
 * where a frame is drawn (pt), chosen so the clock's face in frame 0 sits where the pin has it.
 */
export const MORPH = { frames: 33, cols: 6, fw: 256, fh: 256, perPage: 16, box: { x: 28, y: 136, w: 305, h: 305 } };
