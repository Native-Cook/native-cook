/**
 * Spark Sphere Score — tokens and layout.
 *
 * Measured on the pin (756182593731923720.jpg): the phone's display is 402 pt wide = 523 px, so 1.3 px per pt, origin at pin
 * px (107, 323). Every y below is the pin's y plus SHIFT (25.4 pt): the pin crops the status bar away, a real screen keeps it,
 * so the whole screen sits 25.4 pt lower (ours). Numbers are pt on the 402 × 874 canvas; `makeLayout` scales them to the
 * device. Only this file knows raw numbers; the component reads `comp`, `sys` and the layout.
 *
 * The three pieces of art are procedural (no raster): the spark field and the membrane are SkSL (sss-shaders.ts), the ruler is
 * SkSL, the icons are Skia paths. Typefaces: Figtree (the pin's Gotham-style UI face, narrower than Montserrat) and Barlow
 * Semi Condensed (the pin's DIN-style numerals), both OFL, bundled in assets/fonts.
 */
export const REF = { w: 402, h: 874, safeTop: 62, safeBottom: 34 } as const;
export const SHIFT = 25.4;

/** '#RRGGBB' → [r, g, b] in 0 … 1. */
export function hexRgb(hex: string): [number, number, number] {
  const n = parseInt(hex.replace('#', ''), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

export const ref = {
  color: {
    ground0: '#08080A', // pin: the page from the top to the middle of the disc
    ground1: '#111518', // pin: the page below the disc (ruler, card surround)
    ink: '#FDFEFE',
    inkSoft: '#C4C2C5', // VITAL AGE
    mute: '#8C8C8E', // subtitle
    muteIcon: '#949496', // info ring
    label: '#93979A', // Slow / Fast / tick labels (the pin's #95999C)
    dim: '#3F3F41', // the disabled next-week chevron
    card: '#272B2E',
    cardTitle: '#F9FBFB',
    cardBody: '#C6CACD',
    tick: '#32373A', // a rest tick on #111518
    tickMark: '#464B4F', // the 0.0x landmark tick
    cursor: '#F7FBFC',
    good: '#36E3AA', // the pin's delta text, at rest
    slowDot: '#606467',
    fastBlob: '#6E7275',
  },
  type: {
    // size pt, tracking em, line height pt
    title: { family: 'SssFigtreeBold', size: 13, tracking: 0.083, lineHeight: 18 },
    subtitle: { family: 'SssFigtreeMedium', size: 11, tracking: 0.005, lineHeight: 16 },
    date: { family: 'SssFigtreeBold', size: 12, tracking: 0.091, lineHeight: 16 },
    score: { family: 'SssBarlowBold', size: 44.3, tracking: 0, lineHeight: 48 },
    scoreLabel: { family: 'SssFigtreeBold', size: 15.4, tracking: 0.094, lineHeight: 20 },
    deltaNum: { family: 'SssBarlowBold', size: 16, tracking: 0, lineHeight: 20 },
    deltaText: { family: 'SssBarlowSemi', size: 14.5, tracking: 0.01, lineHeight: 20 },
    pace: { family: 'SssFigtreeBold', size: 12, tracking: 0.082, lineHeight: 16 },
    end: { family: 'SssFigtreeMedium', size: 12, tracking: -0.01, lineHeight: 16 },
    cursor: { family: 'SssBarlowBold', size: 22, tracking: 0, lineHeight: 26 },
    tickLabel: { family: 'SssFigtreeMedium', size: 10.5, tracking: 0, lineHeight: 14 },
    cardTitle: { family: 'SssFigtreeSemi', size: 15, tracking: -0.015, lineHeight: 20 },
    cardBody: { family: 'SssFigtreeMedium', size: 14.5, tracking: 0.018, lineHeight: 20 },
  },
  space: [0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64],
  radius: { card: 12, notch: 3 },
  // The key light: screen top-left (10 o'clock) and a weaker one from 4 o'clock, read from the rim of the pin's membrane.
  light: { az: 300, el: 40, kelvin: 6500, color: '#EFFFF8', intensity: 1 },
} as const;

export const sys = {
  surface: { ground0: ref.color.ground0, ground1: ref.color.ground1, card: ref.color.card },
  ink: { primary: ref.color.ink, secondary: ref.color.inkSoft, tertiary: ref.color.mute, label: ref.color.label },
  stroke: { tick: ref.color.tick, mark: ref.color.tickMark },
  hero: { light: ref.light },
  type: ref.type,
} as const;

/** Measured boxes on the 402 canvas (pin y + SHIFT). Row ids are the pin's elements. */
export const comp = {
  // nav row
  back: { x: 27.7, cy: 83.1, w: 12.3, h: 23.1, stroke: 2.4 },
  titleBase: 79.2,
  subtitleBase: 93.9,
  info: { cx: 365.8, cy: 82.8, d: 28.5, stroke: 1.5 },
  // date range
  date: { base: 132.3, textW: 116.9, chevCy: 127.8, chevL: 121.2, chevR: 279.6, chevW: 7.5, chevH: 11.5 },
  // the disc (fitted mean-radius circle of the pin's outline; the outline's harmonics live in sss-motion REST_MEM)
  disc: { cx: 201, cy: 345.8, r: 163.6 },
  // centre text, baselines relative to the disc centre (pin: 29.9 at 337.7, VITAL AGE at 363.9, delta at 386.6)
  centre: { scoreBase: -8.1, labelBase: 18.1, deltaBase: 40.8 },
  // pace of aging
  pace: { x: 21.5, base: 571.6 },
  row: { base: 595.4, cy: 590.4, dotX: 28.1, dotD: 14.6, slowX: 40, fastRight: 360, blobCx: 374.3, blobW: 13.1, blobH: 14.6 },
  ruler: { x0: 33.9, pitch: 4.174, count: 81, cy: 617.8, tickH: 24, tickW: 1.5, cursorH: 32.3, cursorW: 2.4, cursorBase: 594.6 },
  tickLabels: { base: 646.9, x: [23.1, 189.2, 358.5] },
  // insight card
  card: {
    x: 16.9,
    w: 368.5,
    top: 685.4,
    r: 12,
    notchH: 11,
    notchHalf: 12,
    notchOffset: 16,
    titleBase: 721.3,
    bodyBase: 748.6,
    pitch: 20,
    padX: 16.9,
    bottom: 810,
  },
} as const;

export const COPY = {
  title: 'LONGEVITY',
  subtitle: 'Next update in 6 days',
  date: 'AUG 19  -  AUG 26',
  scoreLabel: 'VITAL AGE',
  pace: 'PACE OF AGING',
  slow: 'Slow',
  fast: 'Fast',
  ends: ['-1.0x', '1.0x', '3.0x'],
  younger: 'years younger',
  older: 'years older',
  back: 'Back',
  info: 'About vital age',
  rulerLabel: 'Pace of aging',
  rulerHint: 'Swipe up or down to change the pace by a tenth.',
  prevWeek: 'Previous week',
  nextWeek: 'Next week, not available',
} as const;

/** The insight card's copy by pace band; the card is as tall as the longest of them, so scrubbing never shifts the page. */
export const BANDS = [
  {
    from: -Infinity,
    title: 'Turning back the clock',
    body: 'At this pace your body is rewinding. Sleep, recovery and training would all have to line up for weeks.',
  },
  {
    from: 0,
    title: 'Steady and Healthy',
    body: 'Your pace of aging is slower than the calendar, mostly thanks to steadier sleep and recovery. Keep your current habits to stay here.',
  },
  {
    from: 1,
    title: 'Running fast',
    body: 'At this pace the years add up quicker than they should. Shorter nights and a heavy training load are the usual cause.',
  },
  {
    from: 2,
    title: 'Burning the candle',
    body: 'Held for long, this pace costs you years. A few quieter days and an earlier night would bring it down fast.',
  },
] as const;
export type SssBand = (typeof BANDS)[number];

export type SssThemeTokens = {
  ground?: string;
  ink?: string;
  mute?: string;
  card?: string;
  numeralFont?: string;
  font?: string;
};

export type SssFontMetrics = { a: number; d: number; cap: number };
export const FONT_METRICS: Record<string, SssFontMetrics> = {
  SssFigtreeBold: { a: 0.95, d: 0.25, cap: 0.7 },
  SssFigtreeSemi: { a: 0.95, d: 0.25, cap: 0.7 },
  SssFigtreeMedium: { a: 0.95, d: 0.25, cap: 0.7 },
  SssBarlowBold: { a: 1.0, d: 0.2, cap: 0.7 },
  SssBarlowSemi: { a: 1.0, d: 0.2, cap: 0.7 },
};

/** Top of a text box that puts the first baseline at `baseline` (the line box is centred around the font's ascent + descent). */
export function topForBaseline(baseline: number, family: string, size: number, lineHeight: number): number {
  const m = FONT_METRICS[family] ?? { a: 0.95, d: 0.25, cap: 0.7 };
  return baseline - ((lineHeight - (m.a + m.d) * size) / 2 + m.a * size);
}

/**
 * The screen's geometry for a device. The top group (nav, dates, disc) is anchored under the status bar, the card to the bottom
 * margin; a tall phone spends the extra height in the gaps, a short one first closes the gaps, then shrinks the disc (never
 * below 80 %), then the card's copy to two lines. Heights are in screen pt.
 */
export function makeLayout(width: number, height: number, insetTop = 62, insetBottom = 34, fontScale = 1) {
  const S = width / REF.w;
  const fs = Math.min(Math.max(fontScale, 1), 1.2);
  const topPad = 4 * S; // the title's cap top sits 8 pt under the status bar, as the pin's header does
  const botPad = 16 * S;
  const avail = height - insetTop - insetBottom - topPad - botPad;
  const natTop = 66; // cap top of the title minus 4
  const natGapB = comp.disc.cy - comp.disc.r - (comp.date.base + 2) - 0; // header + dates → disc top
  const natGapD = comp.pace.base - 8.4 - (comp.disc.cy + comp.disc.r); // disc bottom → pace label cap top
  const natGapF = comp.card.top - comp.card.notchH - (comp.tickLabels.base + 4); // tick labels → notch apex
  // large Dynamic Type (up to 1.2×) needs a fourth line in the card
  const bodyLinesNat = fs > 1.08 ? 4 : 3;
  const cardHNat = comp.card.bottom - comp.card.top + (bodyLinesNat - 3) * comp.card.pitch * fs;
  const natTotal = comp.card.bottom - natTop + (bodyLinesNat - 3) * comp.card.pitch * fs;
  let gapB = natGapB;
  let gapD = natGapD;
  let gapF = natGapF;
  let cd = 1;
  let lines = bodyLinesNat;
  let cardH = cardHNat;
  let sc = S;
  let need = natTotal * S;
  if (need <= avail) {
    // a phone as tall as the reference (402 × 874) keeps the pin's spacing; only a taller one spends its extra height in the gaps
    const extra = Math.min(Math.max((avail - need) / S - 14, 0), 40);
    gapB += extra * 0.3;
    gapD += extra * 0.45;
    gapF += extra * 0.1;
  } else {
    let deficit = (need - avail) / S;
    const take = (room: number) => {
      const t = Math.min(room, Math.max(deficit, 0));
      deficit -= t;
      return t;
    };
    gapB -= take(natGapB - 24);
    gapD -= take(natGapD - 26);
    const dr = take(comp.disc.r * 2 * 0.22);
    cd = 1 - dr / (comp.disc.r * 2);
    gapF -= take(natGapF - 12);
    if (deficit > 0 && lines > 2) {
      const drop = lines - 2;
      deficit -= comp.card.pitch * drop;
      lines = 2;
      cardH -= comp.card.pitch * drop;
    }
    if (deficit > 0) {
      const k = (natTotal - deficit - (lines === 2 ? comp.card.pitch : 0) - (natGapB - gapB) - (natGapD - gapD)) / natTotal;
      sc = S * Math.max(0.78, Math.min(1, k + 0.0));
    }
  }
  const ts = Math.max(0.86, cd); // the centre text follows a shrunken disc
  const oy = insetTop + topPad - natTop * sc; // natural y → screen: oy + y·sc (for the top group)
  const yA = (y: number) => oy + y * sc;
  // disc
  const discTop = yA(comp.date.base + 2) + gapB * sc;
  const R = comp.disc.r * cd * sc;
  const discCy = discTop + R;
  const discCx = width / 2;
  // pace block: origin = pace label cap top
  const paceTop = discCy + R + gapD * sc;
  const yP = (y: number) => paceTop + (y - (comp.pace.base - 8.4)) * sc;
  // card: apex is gapF below the tick labels
  const notchApex = yP(comp.tickLabels.base + 4) + gapF * sc;
  const cardTop = notchApex + comp.card.notchH * sc;
  const cardYOf = (y: number) => cardTop + (y - comp.card.top) * sc;
  const cardHeight = cardH * sc;
  const k = sc / S;
  return {
    width,
    height,
    S,
    sc,
    k,
    ts,
    cd,
    lines,
    fontScale: fs,
    insetTop,
    insetBottom,
    x: (v: number) => (v - REF.w / 2) * S + width / 2,
    yA,
    yP,
    disc: { cx: discCx, cy: discCy, r: R, scale: (comp.disc.r * cd * sc) / comp.disc.r },
    // ruler
    ruler: {
      x0: (comp.ruler.x0 - REF.w / 2) * S + width / 2,
      pitch: comp.ruler.pitch * S,
      cy: yP(comp.ruler.cy),
      tickH: comp.ruler.tickH * sc,
      cursorH: comp.ruler.cursorH * sc,
      tickW: comp.ruler.tickW * S,
      cursorW: comp.ruler.cursorW * S,
    },
    card: {
      x: (comp.card.x - REF.w / 2) * S + width / 2,
      w: comp.card.w * S,
      top: cardTop,
      h: cardHeight,
      r: comp.card.r * S,
      notchH: comp.card.notchH * sc,
      notchHalf: comp.card.notchHalf * S,
      titleBase: cardYOf(comp.card.titleBase),
      bodyBase: cardYOf(comp.card.bodyBase),
      pitch: comp.card.pitch * sc,
      padX: comp.card.padX * S,
    },
    paceBase: yP(comp.pace.base),
    rowBase: yP(comp.row.base),
    rowCy: yP(comp.row.cy),
    cursorBase: yP(comp.ruler.cursorBase),
    tickLabelBase: yP(comp.tickLabels.base),
    centreBase: (off: number) => discCy + off * ts * sc,
    notchApex,
  };
}
export type SssLayout = ReturnType<typeof makeLayout>;
