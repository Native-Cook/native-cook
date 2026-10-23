// The whole screen is one function of a scene time `s`, in seconds. 0 is the splash at rest. The
// logo fades and the tide rises to AT_LOW, where it waits for a breath; holding runs s to AT_TOP
// (the inhale), and from there it runs by itself through the exhale to AT_C, the welcome at rest.
// Going back runs it on to END, which is the splash again (s jumps to 0).

/** The splash rests this long before the tide comes. */
export const SPLASH = 0.5;
export const LOGO_OUT = 0.4;
export const RISE = 0.4;
export const AT_LOW = LOGO_OUT + RISE;
/** The inhale: how long the hold lasts. The exhale is slower, or the breath feels mechanical. */
export const INHALE = 2;
export const AT_TOP = AT_LOW + INHALE;
export const HOLD = 0.6;
export const EXHALE = 3.3;
export const AT_OUT = AT_TOP + HOLD + EXHALE;
/** The welcome fades in over the tide, which sinks out of view. */
export const ARRIVE = 0.6;
export const AT_C = AT_OUT + ARRIVE;
/** Back to the splash: a cross-fade. */
export const BACK = 0.5;
export const END = AT_C + BACK;
/** An instruction fades in or out in this long. */
export const WORD = 0.3;
/** The yellow panel comes down this far as it fades in. */
export const PANEL_SLIDE = 30;

// Where the tide's crest is, as fractions of the screen's height, and how much lower its ends are
// at the screen's edges, in points: the body swells as it rises.
export const LOW = 0.751;
export const TOP = 0.317;
export const DROP_LOW = 34;
export const DROP_TOP = 50;
/** The crest is this far under the screen's bottom edge when the tide is away. */
export const AWAY = 70;
/** The smile is this much wider at the top of the breath. */
export const SMILE_GROW = 0.06;
/** The ornaments float this far up and down, each on its own time. */
export const FLOAT = 4;
export const FLOAT_PERIODS = [2.5, 2, 10 / 3];

/** The checkbox lands on a spring (mass 1). */
export const TICK_STIFFNESS = 300;
export const TICK_DAMPING = 14;

/** autoPlay: the tide comes at ENTER_AT of every LOOP seconds, the breath starts at PRESS_AT, the
 * box is ticked at TICK_AT and the splash returns from BACK_AT. */
export const LOOP = 10;
export const ENTER_AT = SPLASH;
export const PRESS_AT = ENTER_AT + AT_LOW;
export const TICK_AT = ENTER_AT + AT_C + 0.7;
export const BACK_AT = ENTER_AT + AT_C + 1.5;
/** After a touch autoPlay waits this long before it starts over from the splash. */
export const USER_HOLD = 3;

export function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

/** Slow at both ends, like a breath. */
export function sine(t: number) {
  'worklet';
  return 0.5 - 0.5 * Math.cos(Math.PI * clamp01(t));
}

export function easeOut(t: number) {
  'worklet';
  const u = 1 - clamp01(t);
  return 1 - u * u * u;
}

/** How full the breath is: 0 at the low tide, 1 at the top. */
export function breath(s: number) {
  'worklet';
  if (s <= AT_TOP) return sine((s - AT_LOW) / INHALE);
  return 1 - sine((s - AT_TOP - HOLD) / EXHALE);
}

/** How much of the tide is on the screen: it rises from under it, and sinks away as the welcome comes. */
export function ashore(s: number) {
  'worklet';
  if (s <= AT_LOW) return easeOut((s - LOGO_OUT) / RISE);
  if (s <= AT_OUT) return 1;
  const u = clamp01((s - AT_OUT) / ARRIVE);
  return s <= AT_C ? 1 - u * u : 0;
}

/** The crest's y on a screen `height` tall. Under reduced motion the tide stays half way. */
export function crest(s: number, height: number, calm: boolean) {
  'worklet';
  if (calm) return height * (LOW + (TOP - LOW) * 0.5);
  const there = ashore(s);
  const low = height * LOW;
  return height + AWAY + (low - height - AWAY) * there + (height * TOP - low) * breath(s);
}

export function drop(s: number, calm: boolean) {
  'worklet';
  return DROP_LOW + (DROP_TOP - DROP_LOW) * (calm ? 0.5 : breath(s));
}

/** The tide's opacity: under reduced motion it fades in and out instead of travelling, and the breath shows as a glow. */
export function tideAlpha(s: number, calm: boolean) {
  'worklet';
  if (!calm) return 1;
  return ashore(s) * (0.6 + 0.4 * breath(s));
}

export function logoAlpha(s: number) {
  'worklet';
  return s <= AT_C ? 1 - clamp01(s / LOGO_OUT) : clamp01((s - AT_C) / BACK);
}

/** "Breathe in": there from the rise to the top of the breath. */
export function inAlpha(s: number) {
  'worklet';
  return clamp01((s - LOGO_OUT) / RISE) * (1 - clamp01((s - AT_TOP) / WORD));
}

/** "Breathe out": there from the hold to the end of the breath. */
export function outAlpha(s: number) {
  'worklet';
  return clamp01((s - AT_TOP - WORD) / WORD) * (1 - clamp01((s - AT_OUT) / WORD));
}

/** How much of the welcome is there. */
export function welcome(s: number) {
  'worklet';
  if (s <= AT_C) {
    const u = clamp01((s - AT_OUT) / ARRIVE);
    return u * u * (3 - 2 * u);
  }
  return 1 - clamp01((s - AT_C) / BACK);
}

/** How far up an ornament has floated, `clock` seconds after the block appeared. */
export function floated(index: number, clock: number) {
  'worklet';
  return FLOAT * Math.sin((2 * Math.PI * clock) / FLOAT_PERIODS[index % 3] + index * 1.9);
}

// The picture on the welcome, in points from its centre. A part is a colour (0xRRGGBB) and a
// shape: a polygon (0, then its points), a rounded rectangle (1: x, y, width, height, radius) or
// a circle (2: x, y, radius).
export const PANEL = 0xfdb703;
export const TIDE = 0xf47d35;
export const FACE = 0x3d3531;
export const FACE_DISC = 0xfd7203;
const WHITE = 0xffffff;
const SKY = 0x5aa9f8;
const PINK = 0xf9a8c4;
const PLUM = 0x6b4fd8;
const NIGHT = 0x3b2a8c;
const NOTE = 0x2f6bff;
const RED = 0xf0503c;

/** A quarter of a ring from the left round to the top: a band of a rainbow. */
function band(radius: number, width: number) {
  const points: number[] = [];
  for (let i = 0; i <= 6; i++) {
    const angle = Math.PI + (i / 6) * (Math.PI / 2);
    points.push((radius + width / 2) * Math.cos(angle), (radius + width / 2) * Math.sin(angle));
  }
  for (let i = 6; i >= 0; i--) {
    const angle = Math.PI + (i / 6) * (Math.PI / 2);
    points.push((radius - width / 2) * Math.cos(angle), (radius - width / 2) * Math.sin(angle));
  }
  return points;
}

/** A star with four thin points. */
function star(size: number) {
  const waist = size * 0.2;
  return [0, -size, waist, -waist, size, 0, waist, waist, 0, size, -waist, waist, -size, 0, -waist, -waist];
}

/** The ornaments round the phone: where each is, and its parts. */
export const ORNAMENTS: { x: number; y: number; parts: number[][] }[] = [
  // A cloud.
  { x: -98, y: -34, parts: [[2, SKY, -8, 2, 8], [2, SKY, 2, -3, 10], [2, SKY, 12, 3, 7], [1, SKY, -16, 2, 35, 9, 4.5]] },
  // A flower.
  {
    x: -76,
    y: -92,
    parts: [[2, WHITE, 0, -8, 6], [2, WHITE, 7.6, -2.5, 6], [2, WHITE, 4.7, 6.5, 6], [2, WHITE, -4.7, 6.5, 6], [2, WHITE, -7.6, -2.5, 6], [2, PINK, 0, 0, 4.5]],
  },
  // A moon.
  { x: -84, y: 52, parts: [[2, PLUM, 0, 0, 15], [2, NIGHT, 5, -4, 11]] },
  // A music note.
  { x: 86, y: 84, parts: [[2, NOTE, -5, 8, 5.5], [1, NOTE, -1.5, -10, 3, 19, 1.5], [0, NOTE, 1.5, -10, 11, -6, 11, -1, 1.5, -5]] },
  // A rainbow.
  { x: 112, y: -10, parts: [[0, RED, ...band(16, 5)], [0, PINK, ...band(9, 5)]] },
  // A play button.
  { x: 88, y: -94, parts: [[2, FACE_DISC, 0, 0, 14], [0, WHITE, -4, -6.5, 7, 0, -4, 6.5]] },
  // Two sparkles.
  { x: 112, y: 36, parts: [[0, WHITE, ...star(9)]] },
  { x: -112, y: 6, parts: [[0, WHITE, ...star(6)]] },
];

/** The phone in the picture, and the face disc that overlaps its right half. */
export const PHONE = { width: 118, height: 215, radius: 18 };
export const DISC = { x: 22, y: 6, size: 150 };
