import { ISLAND, PILL, SHEET, TAB } from './theme';

export type Box = { x: number; y: number; w: number; h: number; r: number };

// The bloom, measured on the pin's frames rectified to the screen (pt, 30 fps), as time tables.
// Open: ms after the card lets go → sheet width, top and bottom. It grows out of the tab as a drop,
// stays narrow while it drops, widens last and squares its corners in the final frames.
const OPEN_T = [0, 17, 50, 83, 117, 150, 183, 250];
const OPEN_W = [120, 190, 215, 240, 262, 285, 330, 346];
const OPEN_TOP = [18, 18, 22, 26, 30, 40, 60, 79];
const OPEN_BOTTOM = [50, 134, 232, 333, 397, 569, 639, 858];
// Close: ms after the swipe → the sheet pulls its bottom up fast, narrows, and ends as the tab.
const CLOSE_T = [0, 20, 53, 120, 187, 220, 253, 290];
const CLOSE_W = [346, 330, 300, 280, 258, 230, 200, 160];
const CLOSE_TOP = [79, 66, 58, 48, 35, 25, 18, 18];
const CLOSE_BOTTOM = [858, 720, 660, 470, 276, 161, 121, 68];
export const OPEN_MS = OPEN_T[OPEN_T.length - 1];
export const FOLD_MS = CLOSE_T[CLOSE_T.length - 1];
const PIN_HEIGHT = 874; // the tables are in the pin's screen; the bottom follows the device's height

function lerpTable(t: number, ts: number[], vs: number[]) {
  'worklet';
  if (t <= ts[0]) return vs[0];
  for (let i = 1; i < ts.length; i++) {
    if (t <= ts[i]) return vs[i - 1] + (vs[i] - vs[i - 1]) * ((t - ts[i - 1]) / (ts[i] - ts[i - 1]));
  }
  return vs[vs.length - 1];
}

function mix(a: number, b: number, u: number) {
  'worklet';
  return a + (b - a) * u;
}

function smooth(u: number) {
  'worklet';
  const v = Math.min(1, Math.max(0, u));
  return v * v * (3 - 2 * v);
}

/** p: 0 (closed) .. 1 (open). Opening runs the open table forward, closing runs the close table. */
export function sheetBox(p: number, width: number, height: number, oy: number, closing: boolean): Box {
  'worklet';
  const t = closing ? (1 - p) * FOLD_MS : p * OPEN_MS;
  const w = closing ? lerpTable(t, CLOSE_T, CLOSE_W) : lerpTable(t, OPEN_T, OPEN_W);
  const top = (closing ? lerpTable(t, CLOSE_T, CLOSE_TOP) : lerpTable(t, OPEN_T, OPEN_TOP)) + oy;
  const pinBottom = closing ? lerpTable(t, CLOSE_T, CLOSE_BOTTOM) : lerpTable(t, OPEN_T, OPEN_BOTTOM);
  const bottom = pinBottom + oy + (height - oy - PIN_HEIGHT) * (pinBottom / OPEN_BOTTOM[OPEN_BOTTOM.length - 1]);
  const h = Math.max(1, bottom - top);
  const round = Math.min(w, h) / 2;
  // Corners stay round while it moves; they square off to the sheet's radius only when it lands.
  const square = closing ? 1 - smooth(t / 45) : smooth((t - 183) / 67);
  const r = mix(round, SHEET.radius, square);
  // The pin's sheet sits 2 pt right of centre (30 pt left, 26 pt right); the bloom drifts there as it widens.
  const fullW = width - SHEET.left - SHEET.right;
  const x = (width - w) / 2 + (SHEET.left - (width - fullW) / 2) * Math.min(1, w / fullW);
  return { x, y: top, w, h, r };
}

export function pillBox(p: number, width: number, oy: number): Box {
  'worklet';
  // The pill keeps its full width for the first frames of the bloom (pin 9.20 s), then pulls in to the island.
  const u = smooth((p - 0.2) / 0.4);
  const w = mix(PILL.width, ISLAND.width, u);
  const h = mix(PILL.height, ISLAND.height, u);
  return { x: (width - w) / 2, y: mix(PILL.top, ISLAND.top, u) + oy, w, h, r: h / 2 };
}

export function tabBox(arm: number, p: number, pill: Box): Box {
  'worklet';
  const depth = TAB.depth * arm * (1 - Math.min(1, p * 2.5));
  const w = Math.min(TAB.width, pill.w - 12);
  const y = pill.y + pill.h - TAB.overlap;
  const h = TAB.overlap + depth;
  return { x: pill.x + (pill.w - w) / 2, y, w, h, r: Math.min(TAB.radius, h / 2) };
}
