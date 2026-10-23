// Chrome Zero — container-relative geometry, palette and authored spacing.
export const ZERO_TONE = {
  page: '#02030F', headline: '#B9C8FF', body: 'rgba(255,255,255,0.86)',
  buttonTop: '#DCE4FF', buttonBottom: '#A9BAFF', ink: '#0A1038',
  chipTop: '#8EA2FF', chipBottom: '#6E84F5', glow: '#3D4BFF',
} as const;

/** The only switch needed to remove the demonstration finger. */
export const SHOW_TOUCH_MARKER = false;
export const ZERO_DURATION = 12;
export const ZERO_RESUME = 3.5;
export const ZERO_DRAG_POINTS = 110;

export function zeroLayout(width: number, height: number, safeTop = 0, safeBottom = 0) {
  const k = Math.min(width / 402, height / 874);
  const typeScale = Math.max(k, 0.86);
  const offsetX = (width - 402 * k) / 2;
  const grid = (n: number) => Math.round(n / 4) * 4;
  const buttonHeight = height < 700 ? 48 : 56;
  const buttonTop = height - safeBottom - 24 - buttonHeight;
  // Preserve the authored 538 pt pill anchor; surrounding gaps follow the grid.
  const pillTop = 538 * k;
  const pillHeight = 28;
  const headlineTop = Math.max(grid(584 * k), grid(pillTop + pillHeight + 16));
  const headlineLine = 40 * typeScale;
  const bodyTop = Math.max(grid(676 * k), grid(headlineTop + 2 * headlineLine + 12));
  return {
    width, height, k, typeScale, offsetX, safeTop, safeBottom,
    heroHeight: 536 * k, centreX: offsetX + 214 * k, centreY: 286 * k, unit: 158 * k,
    card: { x: offsetX + 8 * k, y: 296 * k, width: 324 * k, height: 216 * k },
    sparkles: [
      { x: offsetX + 254 * k, y: 494 * k, size: 56 * k },
      { x: offsetX + 56 * k, y: 232 * k, size: 28 * k },
      { x: offsetX + 360 * k, y: 172 * k, size: 18 * k },
    ],
    pillTop, pillHeight, headlineTop, headlineLine, headlineSize: 34 * typeScale,
    bodyTop, bodyLine: 24 * typeScale, bodySize: 17 * typeScale,
    button: { x: 24, y: buttonTop, width: width - 48, height: buttonHeight, radius: 16 },
  };
}
export type ZeroLayout = ReturnType<typeof zeroLayout>;
