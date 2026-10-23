// Day One Medal — colours, measurements and the numbers the motion runs on.
//
// Every length was measured on the reference recording, a 430 × 956 pt phone, from the sheet's top
// edge, and is multiplied by the layout's scale so the composition holds on a smaller screen.

export const tone = {
  page: '#000000',
  sheet: '#0E0D10',
  ink: '#FFFFFF',
  // "Achievement": present, but never competing with the medal under it.
  dim: '#5F5E64',
  quiet: '#1C1B1F',
  chip: '#2B2A2F',
  slot: '#111013',
  hairline: '#26252A',
  locked: '#4A4950',
  hint: '#77767D',
  gold: '#DDA85A',
} as const;

export const D = {
  width: 430,
  // The sheet on the reference phone, top edge to the bottom of the screen.
  sheetHeight: 878,
  sheetRadius: 38,
  // From the safe area's top to the sheet, and to the card left peeking behind it.
  sheetGap: 19,
  backGap: 8,
  backScale: 0.92,
  inset: 22,
  header: { centre: 34, size: 17 },
  chip: { top: 81, size: 37, radius: 9, icon: 20 },
  medal: { centre: 273, width: 190, height: 355 },
  title: { centre: 530, size: 41 },
  body: { centre: 606, size: 15, line: 20 },
  wear: { top: 657 },
  share: { top: 732 },
  button: { height: 55, radius: 9, label: 17, icon: 26, gap: 8 },
  // The collectibles grid the sheet rises from.
  grid: { bar: 52, inset: 16, gap: 19, slot: { width: 86, height: 127 }, title: 17, section: 17 },
} as const;

// Screen feedback only; the simulation's physical constants live in medal-motion-constants.
export const S = { press: 0.97, strike: { punch: 0.05 } } as const;

export const T = {
  // The sheet: iOS's own curve and length.
  present: 500,
  dismiss: 380,
  sheetCurve: [0.32, 0.72, 0, 1] as const,
  uiCurve: [0.23, 1, 0.32, 1] as const,
  hint: 4500,
  press: 120,
  // After a touch the self-playing loop waits this long, then resumes.
  resume: 3500,
  // The loop, in milliseconds from its start.
  loop: {
    open: 900, forge: 1700, back: 5000, front: 10400, flick: 13100,
    wear: 17500, swing: 18600, unwear: 21900, day7: 22700, day30: 24200,
    close: 26700, end: 27700,
  },
} as const;

/** Absolute positions snap to four points; reserved lines keep every milestone equally spaced. */
export function medalLayout(scale: number) {
  const grid = (value: number) => Math.round(value / 4) * 4;
  const titleTop = grid(486 * scale);
  const bodyTop = grid(558 * scale);
  const bodyHeight = grid(40 * scale);
  const dotsTop = bodyTop + bodyHeight + 16;
  const hintTop = dotsTop + 4 + 8;
  const hintHeight = 16;
  const wearTop = hintTop + hintHeight + 16;
  const shareTop = wearTop + grid(D.button.height * scale) + 16;
  return { titleTop, bodyTop, bodyHeight, dotsTop, hintTop, hintHeight, wearTop, shareTop };
}
