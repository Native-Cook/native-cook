// Particle Ribbon Voice — every colour, size and copy token, measured on the pin (Dribbble 27107072, a phone
// mock-up of a voice assistant; poster `th/d27107072.jpg` and the 8.5 s video, rectified onto the 402 × 874 pt canvas:
// screen x 357 … 833 px, y 68.5 px, 1.184 px per pt). Row ids in the comments refer to `.work/particle-ribbon-voice/design.md`.
// Only this file holds literal colours and sizes; the component, the shader and the motion read them from here.

export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;

export const ref = {
  color: {
    page: '#030304', // row page: #000000 on the pin, tinted a hair off pure black (DESIGN-SYSTEM §4)
    ink: '#FDFDFE', // row line2: white
    greeting: '#95959A', // row line1: the pin's thin grey line (strokes peak at 224, a 200 weight lit at about 64 %)
    gold: '#E7B94A', // row ramp: left stop; the sparks measure #CDBE91 at their brightest 2 %, the dust #968253
    green: '#6EDC80', // row ramp: middle stop; sparks #ABD7B0, dust #559269
    cyan: '#27D3E6', // row ramp: right stop; sparks #34B9D0, dust #1C7A83
  },
  // the two lines (rows line1, line2): cap height 25.3 pt → 35 pt in Inter Tight (cap 0.7275 em); the pin's face is
  // tighter than Inter Tight, so the tracking is −0.024 em; line pitch 44 pt; line 2's baseline sits at y 367
  type: {
    greeting: { family: 'InterTight-ExtraLight', size: 35, lineHeight: 44, tracking: -0.024 },
    body: { family: 'InterTight-Light', size: 35, lineHeight: 44, tracking: -0.024 },
  },
  text: { bottom: 376, gap: 0 }, // the text block's bottom edge (line 2's box ends 9 pt under its baseline)
  // the ribbon (rows ribbon, edge, hump)
  ribbon: {
    y0: 476, // centre line's mid height
    amp: 24, // sine amplitude at rest; the pin's hump measures 38 pt on the lower edge
    wavelength: 402,
    waveP0: -0.07, // hump at x = 173 (the poster: 0.43 W)
    wdIdle: 26, // half width of the sheet at rest (the dust above the lower edge reaches about 2.4 × this)
    wdLevel: 20, // + at level 1
    wdThread: 6, // thinking: a thread (the beads of light are its face-on stretches)
    wdSpeak: 7, // speaking: the base the reply's level adds to
    ampLevel: 4.5, // + at level 1
    top: 330, // the canvas strip: design pt from … to
    bottom: 600,
  },
  // the twist field at rest: the pin's frame 1.0 s — a thin gold tail on the left, one long face-on stretch
  twist: { amp: 0.55, length: 300, phase: 1.0, slope: 0.4, tailLeft: -1.4, tailRight: 0, tailWidth: 55 },
  // the lower half (not on the pin, whose screen is cropped under the ribbon): the request as it is heard, and the
  // talk control it came from. Type stays in the pin's family; the control is a hairline ring, filled while held.
  ask: { y: 598, size: 19, lineHeight: 25, tracking: -0.01 }, // the transcript's top, design pt
  control: { d: 60, ring: 0.22, gap: 14, hint: 15, bottom: 14 }, // ring alpha; hint size; pt above the safe area
  // dust and bloom
  look: { dust: 1, bloom: 0.7, gain: 1, loose: 0.3 },
} as const;

export const sys = {
  surface: { page: ref.color.page },
  ink: { primary: ref.color.ink, secondary: ref.color.greeting },
  ramp: [ref.color.gold, ref.color.green, ref.color.cyan] as readonly string[],
} as const;

export const COPY = {
  greeting: 'Hey Mara,',
  idle: 'How can I help you?',
  listening: 'I’m listening',
  reply: ['Booked', 'for', '8', 'pm.'] as readonly string[], // one line: the old 'It’s booked for / eight.' left a widow
  ask: ['Book', 'a', 'table', 'for', 'two', 'at', 'eight'] as readonly string[], // the request, shown under the ribbon
  hold: 'Hold to talk',
  release: 'Release to send',
  label: 'Voice assistant',
  hint: 'Press and hold to talk. Drag along the ribbon to part it.',
  hintToggle: 'Double tap to start or stop listening',
  states: { idle: 'Ready', listening: 'Listening', thinking: 'Thinking', speaking: 'Answering' },
  start: 'Start listening',
  stop: 'Stop listening',
} as const;

/** What a host can change; every field falls back to the pin's value. */
export type RibbonColors = readonly [string, string, string];

export type RibbonLayout = {
  S: number;
  /** design pt (402 wide) → screen pt */
  x: (x: number) => number;
  /** design y → screen y of the centre group (text and ribbon keep their proportions on any height) */
  yc: (y: number) => number;
  /** the top of the canvas strip, screen pt, and its height */
  stripTop: number;
  stripH: number;
  /** the bottom of the text block, screen pt */
  textBottom: number;
};

/** The ribbon and its two lines are one group, anchored by its centre (y 414 of 874); insets keep it clear of the island. */
export function makeLayout(W: number, H: number, insetTop: number, centerY = 476): RibbonLayout {
  const S = W / REF.w;
  const cy = 414;
  const shiftC = (centerY - ref.ribbon.y0) * S;
  let top = (y: number) => H * (cy / REF.h) + (y - cy) * S + shiftC;
  // a short phone: never let the greeting climb under the status bar
  const minTop = insetTop + 16;
  const textTop = top(ref.text.bottom) - 2 * ref.type.body.lineHeight * S;
  if (textTop < minTop) {
    const d = minTop - textTop;
    const prev = top;
    top = (y: number) => prev(y) + d;
  }
  const stripTop = top(ref.ribbon.top);
  return {
    S,
    x: (x) => x * S,
    yc: top,
    stripTop,
    stripH: (ref.ribbon.bottom - ref.ribbon.top) * S,
    textBottom: top(ref.text.bottom),
  };
}

function hexRgb(hex: string, fallback: string): [number, number, number] {
  const h = /^#[0-9a-fA-F]{6}$/.test(hex) ? hex : fallback;
  return [parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255];
}

export function rampRgb(colors?: readonly string[]) {
  return [
    hexRgb(colors?.[0] ?? ref.color.gold, ref.color.gold),
    hexRgb(colors?.[1] ?? ref.color.green, ref.color.green),
    hexRgb(colors?.[2] ?? ref.color.cyan, ref.color.cyan),
  ] as const;
}

export function bgRgb(bg?: string) {
  return hexRgb(bg ?? ref.color.page, ref.color.page);
}
