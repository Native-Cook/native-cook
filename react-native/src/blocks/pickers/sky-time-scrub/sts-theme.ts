/** Measured against pin/r1/001.png. Units are points on the rectified display. */
export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;
export const ref = {
  color: {
    page: '#020203',
    ink: '#F3F5F8',
    muted: '#AEB3C1',
    control: '#151619',
    outline: '#292B30',
    accent: '#EEFA3C',
    onAccent: '#15180A',
    tick: '#6B83D8',
    chip: '#223C63',
    location: '#44C2F5',
    panel: '#121222',
    noon: '#75D9C7',
    sunset: '#FFA34E',
    night: '#EF7897',
  },
  type: {
    body: { family: 'StsInter', size: 13, lineHeight: 18, tracking: -0.05 },
    label: { family: 'StsMedium', size: 13, lineHeight: 18, tracking: -0.1 },
    time: { family: 'StsMedium', size: 14, lineHeight: 20, tracking: 0 },
    value: { family: 'StsSemibold', size: 26, lineHeight: 32, tracking: -0.5 },
  },
  space: [0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64],
  radius: { capsule: 31, panel: 32, hint: 8, chip: 14 },
  light: { az: 0, el: 62, kelvin: 5800, color: '#FFF6DA', intensity: 1 },
  material: { atmosphere: { rayleighHeight: 8, mieHeight: 1.2, ozoneHeight: 25, samples: 8 } },
} as const;
export const sys = {
  surface: { page: ref.color.page, control: ref.color.control, panel: ref.color.panel },
  ink: { primary: ref.color.ink, secondary: ref.color.muted, onAccent: ref.color.onAccent },
  accent: { base: ref.color.accent },
  stroke: { outline: ref.color.outline },
  hero: { light: ref.light },
  type: ref.type,
  color: ref.color,
} as const;
export const comp = {
  sky: { horizon: 358, feather: 6, reflection: 30, rasterScale: 3 }, // sky
  arc: { centre: 351, amplitude: 87, period: 402 }, // path
  ruler: { y: 481, x: 26, right: 24, pitch: 12, width: 2, height: 14, lensW: 28, lensH: 52 }, // ruler
  hint: { x: 67, w: 268, h: 28, tipGap: 22, tipR: 5, stem: 2, close: 16, radius: 8 }, // hint: in the time chip's slot, pointing down at the ruler
  chip: { y: 405, h: 28, minW: 76, maxW: 220, padding: 12, target: 48 }, // chip
  controls: { y: 765, h: 62, left: 31, cityX: 114, cityW: 175, rightX: 309, icon: 20 }, // controls
  schedule: { x: 24, y: 525, w: 354, h: 236, rowH: 78, pad: 16 }, // source panel, revealed by grid
  feedback: { pressed: 0.97, duration: 120 },
  rim: { width: 1, alpha: 0.35, arrow: 0.2, chip: 0.25 }, // sky tint on the controls, arrow and chip
} as const;
/** Colours and the typeface a host app may override; everything else is derived from the day. */
export type StsThemeProps = { accent?: string; tick?: string; chip?: string; page?: string; font?: string };
export function hexToRgb(hex: string): [number, number, number] {
  const m = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(hex.trim());
  if (!m) return [0, 0, 0];
  const h =
    m[1].length === 3
      ? m[1]
          .split('')
          .map((c) => c + c)
          .join('')
      : m[1];
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}
export function resolveTheme(theme?: StsThemeProps) {
  const accent = theme?.accent ?? sys.accent.base;
  const tick = theme?.tick ?? ref.color.tick;
  const chip = theme?.chip ?? ref.color.chip;
  const page = theme?.page ?? sys.surface.page;
  const family = (fallback: string) => theme?.font ?? fallback;
  return {
    accent,
    tick,
    chip,
    page,
    tickRgb: hexToRgb(tick).map((v) => v / 255),
    pageRgb: hexToRgb(page).map((v) => v / 255),
    chipRgb: hexToRgb(chip),
    accentRgb: hexToRgb(accent),
    customFont: !!theme?.font,
    body: family(sys.type.body.family),
    label: family(sys.type.label.family),
    time: family(sys.type.time.family),
    value: family(sys.type.value.family),
  };
}
export type StsResolvedTheme = ReturnType<typeof resolveTheme>;
/** The skyline cue: the three schedule rows and the hint pill trade places while the sky is scrubbed. */
export const ROW_ACCENTS = [ref.color.noon, ref.color.sunset, ref.color.night] as const;

export const COPY = {
  city: 'Velora',
  hint: 'Long press and slide to time travel',
  ruler: 'Time of day',
  rulerHint: 'Hold and slide to preview the light. Swipe up or down to adjust by fifteen minutes.',
  return: 'Return to now',
  sunrise: 'Sunrise',
  noon: 'Solar noon',
  sunset: 'Sunset',
  night: 'Night starts',
  now: 'Now',
} as const;

/** Top content keeps its reference anchor; short screens compress the instrument, never text. */
export function makeLayout(width: number, height: number, insetTop = 62, insetBottom = 34, fontScale = 1) {
  const s = width / REF.w;
  const topShift = Math.max(0, insetTop - REF.safeTop * s);
  const available = Math.max(0, height - insetBottom - 16 * s);
  const largeText = fontScale > 1.5;
  const controlsH = largeText ? (comp.controls.icon + 24 + sys.type.body.lineHeight * fontScale) * s : comp.controls.h * s;
  const controlsY = Math.min(comp.controls.y * s + topShift, available - controlsH);
  const compact = Math.min(1, Math.max(0.64, (controlsY - insetTop) / ((comp.controls.y - REF.safeTop) * s)));
  const instrumentY = (y: number) => insetTop + (y - REF.safeTop) * s * compact;
  const hintX = largeText ? 16 * s : comp.hint.x * s;
  const hintW = width - hintX * 2;
  const hintLines = largeText ? Math.ceil((238 * s * fontScale) / (hintW - 48 * s)) : 1;
  const hintH = Math.max(comp.hint.h * s, (hintLines * sys.type.label.lineHeight * fontScale + 8) * s);
  // The ruler leaves room for three rows (48 pt minimum) between it and the controls.
  const rulerY = Math.min(instrumentY(comp.ruler.y), controlsY - (3 * 48 + 44) * s);
  const horizon = Math.min(instrumentY(comp.sky.horizon), rulerY - 123 * s);
  const arcCentre = horizon - 7 * s;
  const arcAmplitude = Math.min(comp.arc.amplitude * s * compact, rulerY - 43 * s - arcCentre);
  // The rows now wake to full strength while the sky is scrubbed, so on short phones they start below the lens (never under it)
  // and share what is left down to the controls.
  const scheduleY = largeText
    ? rulerY + 52 * s
    : Math.max(Math.min(instrumentY(comp.schedule.y), controlsY - comp.schedule.h * s - 8 * s), rulerY + 36 * s);
  const scheduleH = Math.max(48 * s, Math.min(comp.schedule.h * s, controlsY - scheduleY - 8 * s));
  const rowH = largeText ? comp.schedule.rowH * s : Math.max(40 * s, Math.min(comp.schedule.rowH * s, scheduleH / 3));
  return {
    width,
    height,
    s,
    topShift,
    controlsY,
    controlsH,
    largeText,
    rulerY,
    hintX,
    hintW,
    hintH,
    horizon,
    arcCentre,
    arcAmplitude,
    chipY: rulerY - (comp.ruler.y - comp.chip.y) * s,
    /** The hint sits where the time chip appears while scrubbing (bottom-aligned with it) and turns into it. */
    hintY: rulerY - (comp.ruler.y - comp.chip.y - comp.chip.h) * s - hintH,
    tipY: rulerY - comp.hint.tipGap * s,
    scheduleY,
    scheduleH,
    rowH,
    textScale: Math.max(1, fontScale),
  };
}
export type StsLayout = ReturnType<typeof makeLayout>;
