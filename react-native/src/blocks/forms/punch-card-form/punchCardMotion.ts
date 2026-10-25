// Geometry, the Hollerith code, the self-playing script and the pure timing functions of the Punch
// Card Form. Everything a frame needs is a named worklet declared above its callers.

/** Loop period in seconds. */
export const LOOP = 9;

/** The design box (393 × 759); the screen scales it to fit. */
export const DESIGN_W = 393;
export const DESIGN_H = 759;

/** The card: 80 columns of 12 rows, drawn 716 × 315 pt (the real card's 7⅜ × 3¼ in). */
export const COLS = 80;
export const ROWS = 12;
export const PITCH = 8.4;
export const ROW_PITCH = 22.4;
export const MARGIN_X = 22;
export const MARGIN_TOP = 34;
export const CARD_W = MARGIN_X * 2 + COLS * PITCH;
export const CARD_H = MARGIN_TOP + ROWS * ROW_PITCH + 12;
export const HOLE_W = 4.6;
export const HOLE_H = 11;

/** The canvas (design coordinates) and, inside it, the card's top, the punch station and the reader's slot. */
export const CANVAS_Y = 164;
export const CANVAS_H = 456;
export const CARD_Y = 22;
export const STATION_X = 112;
export const SLOT_Y = CARD_Y + CARD_H + 12;
/** Fields on the card: first column (1-based) and width. */
export const FIELDS = [
  { label: 'NAME', first: 1, size: 20 },
  { label: 'YEAR', first: 21, size: 4 },
  { label: 'WING', first: 25, size: 10 },
];

/** The demo types these, one per field. */
export const SCRIPT = ['ADA', '2026', 'WEST'];

export function clamp01(x: number) {
  'worklet';
  return x < 0 ? 0 : x > 1 ? 1 : x;
}

export function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}

export function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}

export function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Ease-out with a small overshoot, for the card advancing a column (a spring, sampled). */
export function easeOutBack(x: number) {
  'worklet';
  const c = 1.6;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
}

export function hash(n: number) {
  'worklet';
  const s = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return s - Math.floor(s);
}

/**
 * The rows punched for a character, as a bit mask over rows 0–11, top to bottom
 * (12, 11, 0, 1 … 9). A–I: 12 + 1–9; J–R: 11 + 1–9; S–Z: 0 + 2–9; digits: one row.
 */
export function holeMask(code: number) {
  'worklet';
  if (code >= 48 && code <= 57) return 1 << (code - 48 + 2);
  if (code >= 65 && code <= 73) return (1 << 0) | (1 << (code - 65 + 1 + 2));
  if (code >= 74 && code <= 82) return (1 << 1) | (1 << (code - 74 + 1 + 2));
  if (code >= 83 && code <= 90) return (1 << 2) | (1 << (code - 83 + 2 + 2));
  if (code === 45) return 1 << 1; // "-" is an 11 punch
  return 0;
}

/** Centre of a column (1-based) and of a row (0-based), in card points. */
export function colX(col: number) {
  'worklet';
  return MARGIN_X + (col - 0.5) * PITCH;
}

export function rowY(row: number) {
  'worklet';
  return MARGIN_TOP + (row + 0.5) * ROW_PITCH;
}

/** The card's left edge on the canvas when column `col` (fractional) sits under the punch. */
export function cardLeft(col: number) {
  'worklet';
  return STATION_X - colX(col);
}

// The demo's keystrokes: [time, column, character code]. "ADA" in NAME, a tab to YEAR, "2026",
// then "WEST" in WING; the card moves on one column after each punch.
type Key = [number, number, number];
const KEYS: Key[] = [];
{
  const times = [
    [1.5, 2.2, 2.9],
    [3.62, 3.82, 4.02, 4.22],
    [4.46, 4.64, 4.82, 5.0],
  ];
  SCRIPT.forEach((word, f) => {
    for (let i = 0; i < word.length; i += 1) KEYS.push([times[f][i], FIELDS[f].first + i, word.charCodeAt(i)]);
  });
}
export const AUTO_KEYS = KEYS;

/** Per column (index col - 1): the character the demo punches there and when (or -1). */
export const AUTO_CHAR: number[] = new Array(COLS).fill(0);
export const AUTO_TIME: number[] = new Array(COLS).fill(-1);
for (const [time, col, code] of KEYS) {
  AUTO_CHAR[col - 1] = code;
  AUTO_TIME[col - 1] = time;
}

/** How long the card takes to move on one column after a punch, and the tab's slide. */
const STEP = 0.16;

/** The column under the punch at loop time t (fractional while the card moves). */
export function autoColumn(t: number) {
  'worklet';
  let col = 1;
  for (let i = 0; i < AUTO_KEYS.length; i += 1) {
    const k = AUTO_KEYS[i];
    col += easeOutBack(seg(t, k[0] + 0.07, k[0] + 0.07 + STEP));
    // After the last letter of NAME the card tabs on to YEAR.
    if (i === 2) col += (FIELDS[1].first - 4) * easeInOutCubic(seg(t, 3.15, 3.5));
  }
  return col;
}

/** How many of the demo's keys have been typed by t, per field. */
export function autoTyped(t: number) {
  'worklet';
  let n = 0;
  for (let i = 0; i < AUTO_KEYS.length; i += 1) if (t >= AUTO_KEYS[i][0]) n += 1;
  return t >= 6.0 ? 0 : n;
}

/**
 * The card's life after Submit: `feed` 0 → 1 slides it down into the reader (5.25–6.1 s), and
 * meanwhile a fresh blank card drops from the hopper and registers from the right, `register`
 * 1 → 0 (5.7–7.2 s, easing out with a small overshoot), so the bed is never empty.
 */
export function autoFeed(t: number) {
  'worklet';
  return easeInOutCubic(seg(t, 5.25, 6.1));
}

export function autoRegister(t: number) {
  'worklet';
  if (t < 5.7) return 1;
  return 1 - easeOutBack(seg(t, 5.7, 7.2));
}

/** How far, in points, a registering card starts to the right of its place. */
export const REGISTER_FROM = 430;

/** The reader is busy (lights running, a faint whirr) from the feed until the new card is out. */
export function autoBusy(t: number) {
  'worklet';
  return seg(t, 5.25, 5.4) * (1 - seg(t, 6.6, 6.9));
}

/** The button's label: 0 "Submit card", 1 "Reading…", 2 "Welcome, member" (fractional while rolling). */
export function autoLabel(t: number) {
  'worklet';
  return easeInOutCubic(seg(t, 5.2, 5.45)) + easeInOutCubic(seg(t, 6.1, 6.35)) - 2 * easeInOutCubic(seg(t, 7.3, 7.6));
}

/** Which field the caret is in during the demo (0, 1, 2), or -1 before typing starts and after the feed. */
export function autoField(t: number) {
  'worklet';
  if (t < 1.2 || t >= 5.2) return -1;
  if (t < 3.3) return 0;
  if (t < 4.35) return 1;
  return 2;
}
