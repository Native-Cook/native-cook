// Measured from d27863828-screen.jpg, inner screen (45, 61)–(493, 1039).
export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;
export const ref = {
  color: {
    page: '#FBF1EE',
    pageMid: '#F7F8FA',
    pageBottom: '#F7F8FC',
    ink: '#101D29',
    mute: '#647180',
    tick: '#687587',
    surface: '#FF9A7A',
    depth: '#FF4A30',
    lip: '#FFC4B0',
    white: '#FDFEFE',
    outline: '#DFE3E8',
    toast: '#2E3844',
    warmInk: '#2A1512',
  },
  type: {
    display: { family: 'PJG-Instrument', size: 80, lineHeight: 84, tracking: -0.8 },
    body: { family: 'PJG-Inter', size: 15, lineHeight: 20, tracking: -0.1 },
    label: { family: 'PJG-InterMedium', size: 12, lineHeight: 16, tracking: 0 },
    title: { family: 'PJG-InterSemiBold', size: 17, lineHeight: 22, tracking: -0.22 },
    stat: { family: 'PJG-InterSemiBold', size: 23, lineHeight: 28, tracking: -0.7 },
    mono: { family: 'PJG-Mono', size: 10, lineHeight: 14, tracking: -0.1 },
  },
  space: [0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64],
  radius: { card: 20, pill: 22, disc: 19 },
  elevation: { contact: [0, 1, 2, 0.035], ambient: [0, 5, 12, 0.04] },
  light: { az: 315, el: 40, kelvin: 5600, color: '#FDFEFE', intensity: 0.7 },
  material: {
    // x / width, fraction of the filled column from floor to surface, radius in pt.
    // Nine visible bubbles measured on the source; three sub-point background bubbles.
    bubbleSeeds: [
      [0.541, 0.75, 4.5],
      [0.384, 0.572, 8.9],
      [0.623, 0.484, 7.2],
      [0.246, 0.395, 4.5],
      [0.493, 0.307, 2.7],
      [0.715, 0.216, 8.9],
      [0.323, 0.125, 7.2],
      [0.594, 0.036, 5.4],
      [0.809, 0.661, 2.7],
      [0.42, 0.84, 0.8],
      [0.29, 0.45, 0.7],
      [0.76, 0.37, 0.8],
    ],
  },
} as const;
export const sys = {
  surface: { page: ref.color.page, raised: ref.color.white },
  ink: { primary: ref.color.ink, secondary: ref.color.mute, onAccent: ref.color.white },
  accent: { base: ref.color.depth, pressed: '#ED4B32' },
  hero: { rim: ref.color.white, shadowTint: '#BF6958', light: ref.light },
  stroke: { hairline: ref.color.outline },
} as const;
export const comp = {
  header: { x: 22, y: 54, w: 358, h: 44, disc: 38 },
  jar: { x: 90, y: 117, w: 222, h: 419, topRadius: 68, bottomRadius: 70 },
  scale: { x: 328, top: 130, bottom: 532, w: 52, h: 18 },
  amount: { y: 550, baseline: 614, h: 82 },
  subtitle: { y: 634, baseline: 647, h: 24 },
  cards: { x: 22, y: 672, w: 172, h: 81, gap: 14, labelY: 696, valueBaseline: 728 },
  buttons: { x: 22, y: 774, w: 358, h: 57, primaryW: 234, gap: 10 },
  toast: { x: 90, y: 135, w: 222, h: 44, r: 24 },
  material: { wall: 3, meniscus: 10, specular: 8, specularH: 150, poolW: 260, poolH: 46, poolY: 548 },
} as const;
export const COPY = {
  title: 'Sora, spring 2027',
  goal: 'Sora',
  remaining: 'Left to go',
  weekly: 'At $40 a week',
  pour: 'Pour in',
  auto: 'Auto',
  autoOn: 'Auto on',
  back: 'Back to goals',
  more: 'Goal options',
  adjustable: 'Savings goal. Drag the liquid to add money. Swipe up or down to adjust, then activate to pour.',
} as const;

export function makeLayout(W: number, H: number, insetTop: number, insetBottom: number, fontScale = 1) {
  const S = Math.min(W / REF.w, 1);
  const x0 = (W - REF.w * S) / 2;
  const headerY = Math.max(insetTop < 40 ? insetTop + 8 : 0, (comp.header.y - REF.safeTop) * S + insetTop);
  const largestType = fontScale > 2;
  const buttonH = (comp.buttons.h + Math.max(0, fontScale - 2) * 24) * S;
  const buttonY = H - Math.max(insetBottom, 12) - 9 - buttonH;
  const largeType = fontScale > 1.35;
  const cardH = (largeType ? 2 * Math.max(94, 24 + fontScale * (12 * 0.9 + 23 * 0.95)) + 8 : comp.cards.h) * S;
  const cardsY = buttonY - 21 * S - cardH;
  const subtitleY = cardsY - (largestType ? ref.type.body.size * fontScale * 2.6 + 16 : 38) * S;
  const amountBaseline = subtitleY - (largeType ? 28 : 20) * S;
  const jarBottom = amountBaseline - 78 * S;
  const headerH = (largestType ? ref.type.title.size * fontScale * 3.3 + 16 : 60) * S;
  const jarTop = Math.max(headerY + headerH, jarBottom - comp.jar.h * S);
  const jarH = Math.max(140, jarBottom - jarTop);
  const jarW = comp.jar.w * S;
  const jar = { x: W / 2 - jarW / 2, y: jarTop, w: jarW, h: jarH };
  const x = (n: number) => x0 + n * S;
  return {
    W,
    H,
    S,
    x0,
    x,
    headerY,
    buttonY,
    buttonH,
    cardsY,
    cardH,
    subtitleY,
    amountBaseline,
    jar,
    largeType,
    typeScale: fontScale,
    largestType,
    scaleTop: jarTop + 12 * S,
    scaleBottom: jarBottom - 8 * S,
  };
}
export type PJGLayout = ReturnType<typeof makeLayout>;
