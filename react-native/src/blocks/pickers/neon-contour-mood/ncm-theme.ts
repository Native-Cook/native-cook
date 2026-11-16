// Measured from pin/rect.png, rectified to 402 × 874 pt. See .work/neon-contour-mood/design.md.
export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;
export const ref = {
  color: {
    page: '#110713',
    sheetTop: '#10091C',
    core: '#1C0104',
    ink: '#FBF9FC',
    secondary: '#B9ADBD',
    outline: '#8E6F93',
    track: '#211525',
    dot: '#130B19',
    grabber: '#2A1A32',
    button: '#FCFBFC',
    buttonInk: '#160D19',
    close: '#D2C7DF',
  },
  type: {
    display: { family: 'NcmNunito', size: 54, tracking: 1.4, baseline: 347 },
    title: { family: 'NcmMedium', size: 18, lineHeight: 24 },
    question: { family: 'NcmSemiBold', size: 28, lineHeight: 34 },
    tag: { family: 'NcmMedium', size: 13, tracking: 1.25, lineHeight: 18 },
    endpoint: { family: 'NcmRegular', size: 12, lineHeight: 17 },
    button: { family: 'NcmSemiBold', size: 18, lineHeight: 24 },
  },
  space: [0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64],
  radius: { sheet: 54, pill: 999 },
  light: { az: 0, el: 90, kelvin: 2200, color: '#FF4538', intensity: 1 },
  material: { coreWidth: 132.134746, coreHeight: 107.983216, corner: 61.268647, notch: 49.46817, notchWidth: 50.063416 },
} as const;
export const sys = {
  surface: { page: ref.color.page, sheet: ref.color.sheetTop, track: ref.color.track },
  ink: { primary: ref.color.ink, secondary: ref.color.secondary, onAccent: ref.color.buttonInk },
  stroke: { hairline: ref.color.outline },
  hero: { core: ref.color.core, light: ref.light },
} as const;
export const COPY = {
  title: 'Market Feelings',
  question: "What's your market mood?",
  left: 'EXTREME FEAR',
  right: 'EXTREME GREED',
  next: 'Next',
  close: 'Close market feelings',
} as const;
export const comp = {
  sheet: { y: 56, radius: 54 },
  grabber: { y: 72, w: 36, h: 4 },
  title: { baseline: 111 },
  close: { x: 341, y: 82, size: 44, glyph: 17, stroke: 1.5 },
  hero: { x: 201, y: 328, cutoff: 656, fade: 140, wordBaseline: 347, wordGlow: 2.4 },
  tag: { centerY: 536, h: 22, px: 9, stroke: 1, r: 11 },
  question: { baseline1: 584, baseline2: 618 },
  slider: { x: 37, y: 650, w: 336, h: 60, r: 30, first: 70, last: 340, thumb: 25, dot: 4 },
  endpoints: { baseline: 733 },
  button: { x: 32, y: 768, w: 342, h: 60, r: 30, baseline: 805 },
  // The source has three prominent ridges within a seven-layer light field; the intermediate layers are soft bands.
  rings: {
    offsets: [0, 8, 12.864303, 32, 49.385498, 86, 110.118761],
    strength: [0.12, 0.06, 1, 0.05, 0.75, 0.02, 0.55],
    width: 0.65,
    halo: 9,
  },
  field: {
    hollowBase: 0.5,
    hollowSlope: 0,
    hollowWidthGain: 0.08,
    verticalRatios: [1, 1, 1, 0.45, 0.3, 0.1, 0.07],
    hotWeight: 3.058259,
    midWeight: 1.197484,
    outerWeight: 0.223609,
    otherWeight: 0.714657,
    warmStrength: 2.0,
    warmWidth: 67.192482,
    warmY: 118.436709,
    warmSpread: 41.771969,
    gain: 0.3,
  },
  feedback: { pressScale: 0.97, thumbScale: 1.12 },
  // Touch the light: a pan on the hero scrubs the mood and squeezes the rings; a tap sends one pulse outward.
  touch: {
    pointsPerStop: 56,
    squeezePerPoint: 1 / 6,
    squeezeMax: 0.12,
    leanMax: 10,
    leanReach: 64,
    pulseStep: 0.026,
    heroTop: 14,
    heroBottom: 18,
  },
  // The neon lights the sheet: every tint is a function of the mood under the thumb and is capped to keep text above 4.5 : 1.
  lit: {
    rimHeight: 1.5,
    rimAlpha: 0.55,
    rimFade: 0.4,
    inkMix: 0.1,
    thumbPool: 46,
    thumbPoolAlpha: 0.22,
    endReach: 1.2,
    closeMix: 0.3,
    dotPassed: 0.3,
    dotClear: 4,
  },
} as const;

/** half-width, half-height, corner radius and the notch pairs of the shared notched rounded-box field (reference pt). */
export type MoodShape = {
  w: number;
  h: number;
  corner: number;
  notchY?: number;
  notchX?: number;
  notchWidth?: number;
  notchXWidth?: number;
};
export type Mood = {
  key: string;
  word: string;
  tag?: string;
  shape: MoodShape;
  /** core, hot ridge, mid band, outer glow (sRGB hex). */
  ramp: [core: string, hot: string, mid: string, outer: string];
  /** 0..1: how much of the warm shoulder at the notch this mood carries (the pin's rest state has 1). */
  warmth?: number;
};
export type NcmTheme = {
  page?: string;
  ink?: string;
  track?: string;
  button?: string;
  /** a font file (module id from require(), or a file URI); the default is Nunito ExtraBold. */
  displayFont?: string | number;
  /** a font file for every UI role; the default is Inter Medium / SemiBold / Regular. */
  textFont?: string | number;
};

// Every shape is mirror-symmetric on both axes, so every in-between of two neighbours is too.
export const MOODS: readonly Mood[] = [
  {
    key: 'panic',
    word: 'PANIC',
    tag: 'CAPITULATION',
    shape: { w: 132, h: 116, corner: 45, notchY: 76, notchWidth: 39 },
    ramp: ['#FFE7DE', '#FF3344', '#B0125E', '#51208D'],
  },
  {
    key: 'fear',
    word: 'FEAR',
    tag: 'TENSION',
    shape: { w: 132.134746, h: 107.983216, corner: 61.268647, notchY: 49.46817, notchWidth: 50.063416 },
    ramp: ['#FF2738', '#FF2B24', '#F81872', '#0D0ACC'],
    warmth: 1,
  },
  {
    key: 'doubt',
    word: 'DOUBT',
    tag: 'CAUTION',
    shape: { w: 132, h: 90, corner: 80, notchY: 30, notchWidth: 50 },
    ramp: ['#FF3484', '#E33DA9', '#9F29CB', '#4822AB'],
  },
  {
    key: 'calm',
    word: 'CALM',
    tag: 'BALANCE',
    shape: { w: 106, h: 106, corner: 106, notchY: 0, notchWidth: 50 },
    ramp: ['#C4A8FF', '#9A7BFF', '#6945DF', '#3524B0'],
  },
  {
    key: 'hope',
    word: 'HOPE',
    tag: 'APPETITE',
    shape: { w: 89, h: 127, corner: 89, notchY: 0, notchWidth: 50 },
    ramp: ['#8AD8FF', '#48CFEA', '#2485D7', '#252B91'],
  },
  {
    key: 'greed',
    word: 'GREED',
    tag: 'MOMENTUM',
    shape: { w: 104, h: 118, corner: 89, notchY: -18, notchWidth: 70 },
    ramp: ['#9AFFD0', '#3DDBA4', '#199E9E', '#214B9F'],
  },
  {
    // A four-point star grown symmetrically, no rotation to turn FEAR into a lump halfway. The brief's (52, 39) dent on both
    // axes squeezes the word out of the core (M and the last A land on the ring), so the side dents are shallower and wider.
    key: 'mania',
    word: 'MANIA',
    tag: 'EUPHORIA',
    shape: { w: 119, h: 119, corner: 48, notchY: 52, notchWidth: 39, notchX: 24, notchXWidth: 60 },
    ramp: ['#EEFF92', '#B3F94B', '#52BF74', '#197D88'],
  },
];

const hex = (c: string) => [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16));
const toHex = (v: number[]) =>
  `#${v
    .map((x) =>
      Math.round(Math.min(255, Math.max(0, x)))
        .toString(16)
        .padStart(2, '0'),
    )
    .join('')}`;
export function mixHex(a: string, b: string, t: number) {
  const x = hex(a),
    y = hex(b);
  return toHex(x.map((v, i) => v + (y[i] - v) * t));
}
/** Resolved colours of a theme: ink-derived tones follow a custom ink, everything else falls back to the pin's values. */
export function resolveTheme(theme?: NcmTheme) {
  const page = theme?.page ?? ref.color.page;
  const ink = theme?.ink ?? ref.color.ink;
  const track = theme?.track ?? ref.color.track;
  const button = theme?.button ?? ref.color.button;
  const custom = theme?.ink !== undefined || theme?.page !== undefined;
  return {
    page,
    ink,
    track,
    button,
    buttonInk: ref.color.buttonInk,
    secondary: custom ? mixHex(ink, page, 0.3) : ref.color.secondary,
    grabber: theme?.page ? mixHex(page, ink, 0.12) : ref.color.grabber,
    dot: theme?.track ? mixHex(track, page, 0.5) : ref.color.dot,
    close: theme?.ink ? mixHex(ink, page, 0.16) : ref.color.close,
    outline: theme?.ink ? mixHex(ink, page, 0.45) : ref.color.outline,
    core: theme?.page ? mixHex(page, '#000000', 0.6) : ref.color.core,
  };
}
export type NcmColors = ReturnType<typeof resolveTheme>;

/** Balanced wrap: split the words into at most `lines` runs, the longest run as short as it can be, earlier runs longer on a tie. */
export function splitLines(text: string, lines: number) {
  const words = text.split(/\s+/).filter(Boolean);
  const count = Math.max(1, Math.min(lines, words.length));
  if (count <= 1) return [words.join(' ')];
  let best: string[] = [];
  let bestMax = Infinity;
  let bestLens: number[] = [];
  const longerFirst = (a: number[], b: number[]) => {
    for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return a[i] > b[i];
    return false;
  };
  const walk = (start: number, left: number, acc: string[]) => {
    if (left === 1) {
      const run = [...acc, words.slice(start).join(' ')];
      const lens = run.map((r) => r.length);
      const max = Math.max(...lens);
      if (max < bestMax || (max === bestMax && longerFirst(lens, bestLens))) {
        best = run;
        bestMax = max;
        bestLens = lens;
      }
      return;
    }
    for (let end = start + 1; end <= words.length - (left - 1); end++) walk(end, left - 1, [...acc, words.slice(start, end).join(' ')]);
  };
  walk(0, count, []);
  return best;
}

export type NcmCopy = { title: string; question: string; left: string; right: string };
export const DEFAULT_COPY: NcmCopy = { title: COPY.title, question: COPY.question, left: COPY.left, right: COPY.right };

/** Full-size layout is source-exact; short frames reduce the hero, never squeeze the controls. Large text scrolls. */
export function makeLayout(W: number, H: number, insetTop: number, insetBottom: number, fontScale = 1, copy: NcmCopy = DEFAULT_COPY) {
  const S = W / REF.w;
  const fs = Math.max(1, fontScale);
  const expanded = fs > 1.2;
  const extra = expanded ? (fs - 1) * 380 * S : 0;
  const stageH = expanded ? Math.max(H, 735 * S + insetTop + insetBottom + extra) : H;
  const bottomShift = stageH - REF.h * S - (insetBottom - REF.safeBottom * S);
  const titleY = insetTop + (comp.title.baseline - REF.safeTop) * S + (fs > 1.6 ? 48 * S : 0);
  const buttonH = comp.button.h * S * Math.max(1, fs * 0.7);
  const buttonY = comp.button.y * S + bottomShift - (buttonH - comp.button.h * S);
  const questionLines = splitLines(copy.question, fs > 1.6 ? 4 : fs > 1.2 ? 3 : 2);
  const titleLines = splitLines(copy.title, fs > 1.6 ? 2 : 1);
  const endpointStacked = fs > 2.4;
  const endpointLines = [
    splitLines(copy.left, fs > 1.2 && !endpointStacked ? 2 : 1),
    splitLines(copy.right, fs > 1.2 && !endpointStacked ? 2 : 1),
  ];
  const endpointY = buttonY - 35 * S - (endpointStacked ? 1 : endpointLines[0].length - 1) * ref.type.endpoint.lineHeight * S * fs;
  const sliderY = expanded ? endpointY - 32 * S - comp.slider.h * S : comp.slider.y * S + bottomShift;
  const questionY = expanded
    ? sliderY - 38 * S - (questionLines.length - 1) * ref.type.question.lineHeight * S * fs
    : comp.question.baseline1 * S + bottomShift;
  const tagY = expanded ? questionY - ref.type.question.size * S * fs - 36 * S : comp.tag.centerY * S + bottomShift;
  const heroS = Math.max(0.25, Math.min(S, (tagY - titleY + 28 * S) / 431));
  const heroY = tagY - (comp.tag.centerY - comp.hero.y) * heroS;
  return {
    W,
    H: stageH,
    S,
    fs,
    sheetY: insetTop - 6 * S,
    titleY,
    titleLines,
    questionLines,
    endpointLines,
    endpointStacked,
    heroY,
    heroS,
    tagY,
    questionY,
    questionLine: ref.type.question.lineHeight * S * fs,
    sliderY,
    sliderX: comp.slider.x * S,
    sliderW: comp.slider.w * S,
    sliderH: comp.slider.h * S,
    firstX: comp.slider.first * S,
    lastX: comp.slider.last * S,
    endpointY: expanded ? endpointY : comp.endpoints.baseline * S + bottomShift,
    buttonY,
    buttonH,
    closeX: comp.close.x * S,
    closeY: insetTop + (comp.close.y - REF.safeTop) * S,
    heroTouchTop: titleY + comp.touch.heroTop * S,
    heroTouchBottom: tagY - comp.touch.heroBottom * S,
  };
}
export type NcmLayout = ReturnType<typeof makeLayout>;
