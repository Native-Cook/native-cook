/**
 * Owl Alarm Ruler — every colour, size and position of the screen, in pt.
 *
 * Measured on the pin's screen (1206 × 2622 px, an iPhone 17 Pro at 3×, 402 × 874 pt, 62 pt top
 * safe area): px ÷ 3 = pt. Insets and gaps are fixed tokens; horizontal positions are taken from
 * the screen edges or its centre line, vertical ones from the top safe-area edge (header, mascot,
 * copy, clock, ruler) or the bottom safe-area edge (the button), so a 393 pt or 411 dp screen keeps
 * every gap and only the flexible widths change.
 */

/** The pin's top safe area (pt): reference y values below are "pin y − this". */
export const PIN_INSET = 62;

/**
 * Spacing tokens, from the pin's own measurements (pt). The pin is not on a strict 4 pt grid; a
 * value snaps only where the snap is under 1 pt (see design.md for the table).
 */
export const space = {
  screenInset: 26, // ruler ends and button sides (pin: 25.8 / 26.2)
  backInset: 24, // back button from the left edge (pin: 24.0)
  headerTop: 10, // back button below the safe area (pin: 10.0)
  ctaPadStart: 19, // label box from the button's left edge (pin ink: 19.3 incl. the S's side bearing)
  ctaPadEnd: 22.3, // arrow tip from the button's right edge (pin: 22.3)
  ctaBottom: 20, // button above the bottom safe area (pin: 20.0)
  hit: 44, // minimum tap target
} as const;

/** Radii: one family of fully rounded ends plus the button's corner. */
export const radius = {
  cta: 18, // pin: 18.1
  pill: 999,
} as const;

export const colors = {
  page: '#EDEDED', // the light lower part of the backdrop
  band: '#E3E3E3', // the darker upper part, above the diagonal
  shadow: '#C4C4C5', // the mascot's ground shadow
  backFill: '#DADADA',
  ink: '#000000',
  track: '#CCCCCC',
  caption: '#5F5F5F',
  cta: '#BC4A20',
  white: '#FFFFFF',
  needle: '#CC3F20',
  tickDark: [44, 41, 38], // ticks next to the needle
  tickLight: [194, 193, 192], // ticks far from the needle
  lid: '#F9F8F6', // the mascot's feathers (eyelids)
  lash: '#3A3A3A',
} as const;

export const copy = {
  line1: 'You chose your goal.',
  line2: 'Now set the alarm.',
  caption: 'We’ll begin with one alarm. No backups.',
  cta: 'Set alarm for',
  a11yRuler: 'Alarm time',
  a11yRulerHint: 'Swipe up or down to change by five minutes.',
  a11yBack: 'Back',
} as const;

/** SF Pro / SF Pro Rounded: the baseline sits 0.953 em under the top of a Text with no lineHeight. */
export const ASCENT = 0.953;

/** Text styles: size in pt, weight, colour, extra letter spacing in pt. */
export const type = {
  headline: { size: 30, weight: '600', color: colors.ink, ls: -1.25 },
  time: { size: 78, weight: '800', color: colors.ink, ls: 0 },
  meridiem: { size: 25, weight: '600', color: colors.ink, ls: 0 },
  caption: { size: 14.2, weight: '400', color: colors.caption, ls: 0 },
  cta: { size: 20, weight: '500', color: colors.white, ls: 0.36 },
} as const;

export type TextStyleSpec = (typeof type)[keyof typeof type];

/** px of the pin → pt. */
const P = (px: number) => px / 3;
/** A y on the pin → pt below the top safe-area edge. */
const Y = (px: number) => px / 3 - PIN_INSET;

export const layout = {
  // Backdrop: the darker band lies above a line at this slope through (0, y0).
  diagonal: { y0: Y(584), slope: 400 / 1206 },
  /** The light line turns around this point (x from the centre line), under the mascot. */
  sundialPivot: { dx: 0, y: Y(584 + 603 * (400 / 1206)) },

  back: { d: P(126), arrow: { tipX: P(110.5 - 72), armX: P(129.5 - 72), armDy: P(19.5), endX: P(158.9 - 72), stroke: P(6.3) } },
  /** The progress track is centred on the screen, on the back button's centre line. */
  track: { w: 180, h: P(33), fill: P(196) },

  // Mascot artwork: 660 × 576 px drawn at 1:1 on the pin, centred (+0.67 pt) on the screen.
  mascot: { w: P(660), h: P(576), dx: P(275) + P(660) / 2 - 201, y: Y(414), pivotY: 563 / 576 },
  /** Eye whites inside the artwork (pt from its top-left corner): centre and radius. */
  eyes: [
    { cx: P(224), cy: P(246), r: P(70.5) },
    { cx: P(454), cy: P(248), r: P(72.5) },
  ],
  /** Pupils (pt): diameter, and the rest offset that looks down towards the beak. */
  pupil: { d: P(60), rest: [{ x: P(22), y: P(24) }, { x: P(-22), y: P(24) }] },
  lash: P(12),
  groundShadow: { dx: P(588) - 201, cy: Y(947), rx: P(307), ry: P(50) },

  headline: [{ base: Y(1155) }, { base: Y(1253) }],
  /**
   * The clock: the pin sets the digits tight (about −4.8 pt between minute digits) and centres the
   * colon on the digits, so the wheels overlap their neighbours by `tight` and the colon is raised.
   */
  time: { base: Y(1564), dx: P(17.5), colonGap: 1.3, tight: -5.4, colonRise: P(21) },
  meridiem: { base: Y(1692), dx: -0.5, w: 60 },

  ruler: {
    spacing: P(34.7), // one tick = five minutes
    base: Y(1849), // bottom of every tick
    tickW: 3,
    fade: 0.6, // ticks fade out over this many ticks beyond the screen inset
    needle: { w: 7, h: P(93), cy: Y(1816) },
    /** The canvas band that holds the ruler (room for the needle's bend), and the touch band. */
    band: { top: Y(1730), h: P(170) },
    touch: { top: Y(1700), h: P(230) },
  },

  caption: { base: Y(2046) },
  cta: { h: P(196), base: P(2384 - 2265) },
  /** Arrow in the button, from the button's right edge (pt). */
  ctaArrow: { shaft: P(1036 - 997.5), stroke: P(7.5), headW: P(30.5), headH: P(37.5), headR: 1 },
} as const;

/** The time the pin shows: 1:05 PM, in minutes after midnight. */
export const REST_MINUTES = 13 * 60 + 5;
export const STEP_MINUTES = 5;
export const TICKS_PER_DAY = (24 * 60) / STEP_MINUTES;
