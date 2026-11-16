/** Measured from the inner 322 × 698 screen in d27730037.jpg, normalized to 402 × 874. */
export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;
export const ref = {
  color: {
    ink: '#0A0A0B',
    paper: '#FCFCFB',
    clay: '#F4F4F2',
    clayShade: '#77777D',
    glaze: '#080809',
    bodyDark: '#B6B6BB',
    bodyLight: '#424246',
  },
  type: {
    display: { family: 'SmoSemibold', size: 40, lineHeight: 48, tracking: -0.8 },
    body: { family: 'SmoRegular', size: 16, lineHeight: 22 },
    label: { family: 'SmoRegular', size: 17 },
    chip: { family: 'SmoRegular', size: 13.5 },
  },
  space: [0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64],
  radius: { chip: 8, pill: 28 },
  light: { az: -40, el: 40, kelvin: 5600, intensity: 1 },
  material: { ceramic: { roughness: 0.6, bevel: 0.045 }, glaze: { roughness: 0.12 } },
} as const;
export const sys = {
  surface: { page: ref.color.ink, raised: '#262627', sheet: ref.color.paper },
  ink: { primary: ref.color.paper, secondary: ref.color.bodyDark, onAccent: ref.color.ink },
  accent: { base: ref.color.paper, pressed: '#DFDFDE' },
  hero: { clay: ref.color.clay, shadow: ref.color.clayShade, glaze: ref.color.glaze, light: ref.light },
  stroke: { outline: '#555556', hairline: '#333335' },
} as const;
export const comp = {
  margin: 17,
  // On the 4/8 grid from the safe area (iPhone 18 Pro: progress 80, chip 144–176, title 184, body 292, buttons 760–816).
  progress: { y: 80, w: 91, h: 4, gap: 4 },
  skip: { x: 338, y: 66, w: 48, h: 44 },
  chip: { y: 144, h: 32, pad: 12 },
  title: { y: 184, w: 368, gap: 8 },
  body: { y: 292, w: 366, gap: 12 },
  hero: { x: 201, y: 521, radius: 128, bounds: 184 },
  button: { y: 760, w: 179, h: 56, gap: 8, r: 28, bottom: 24 },
  accessibility: { minTarget: 48, largeTypeThreshold: 1.35 },
} as const;
export const COPY = { primary: 'Create account', secondary: 'Log in', skip: 'Skip', page: 'Page' } as const;
export type SolidSpec = { sides: number; roundness?: number; split?: 'none' | 'wedge'; base?: 'dish' | 'disc' | 'match' };
export type SolidMorphTokens = {
  ink?: string;
  paper?: string;
  clay?: string;
  clayShade?: string;
  glaze?: string;
  font?: string;
  /** A bundled require() for a custom display family; no network font requests. */
  fontAsset?: number;
  titleSize?: number;
  radius?: number;
};
export function makeLayout(W: number, H: number, insetTop: number, insetBottom: number, fontScale = 1) {
  const S = W / REF.w;
  const top = Math.max(insetTop, 24);
  const compact = H < 740 || fontScale > comp.accessibility.largeTypeThreshold;
  const progressY = top + 18 * S;
  const chipY = top + (compact ? 64 : 82) * S;
  const largeText = fontScale > comp.accessibility.largeTypeThreshold;
  const titleY = chipY + (largeText ? 34 * fontScale + 10 : comp.chip.h + comp.title.gap) * S;
  const titleSize = (compact ? 31 : ref.type.display.size) * S * fontScale;
  // Large text reflows onto a scrollable stage rather than squeezing the hero or clipping copy.
  const titleHeight = titleSize * (largeText ? Math.ceil(2 * fontScale) * 1.26 : (2 * ref.type.display.lineHeight) / ref.type.display.size);
  const bodyY = titleY + titleHeight + (largeText ? 6 : comp.body.gap) * S;
  const bodySize = ref.type.body.size * S * fontScale;
  const bodyHeight = bodySize * (largeText ? Math.ceil(3 * fontScale + 1) * 1.35 : 3.1);
  const buttonHeight = Math.max(comp.button.h * S, (fontScale > 2.5 ? 17 * fontScale * 2.4 + 24 : 26 * fontScale + 24) * S);
  const footerHeight = largeText ? buttonHeight * 2 + comp.button.gap * S : buttonHeight;
  const stageHeight = Math.max(H, bodyY + bodyHeight + (largeText ? 240 : 0) * S + footerHeight + insetBottom + 52 * S);
  const footerY = stageHeight - Math.max(insetBottom, 20) - comp.button.bottom * S - footerHeight;
  const heroTop = bodyY + bodyHeight + 12 * S;
  const heroBottom = footerY - 18 * S;
  const heroRadius = Math.max(0, Math.min(comp.hero.radius * S, (heroBottom - heroTop) / 1.9));
  const heroY = compact
    ? (heroTop + heroBottom) / 2
    : Math.max(heroTop + heroRadius * 0.7, Math.min(H * (521 / 874), heroBottom - heroRadius * 0.7));
  return {
    W,
    H: stageHeight,
    S,
    fontScale,
    top,
    progressY,
    chipY,
    titleY,
    titleSize,
    titleHeight,
    bodyY,
    bodySize,
    bodyHeight,
    footerY,
    buttonHeight,
    footerHeight,
    skipWidth: Math.max(48, 30 * fontScale + 18) * S,
    heroX: W / 2,
    heroY,
    heroRadius,
    compact,
    largeText,
  };
}
export type SolidLayout = ReturnType<typeof makeLayout>;
