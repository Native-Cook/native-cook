/** The screen the surface lives on: its size, the top safe-area inset and the bar's whole height. */
export type Frame = { width: number; height: number; top: number; bar: number };

export const LIST = 290;
export const RAIL = 64;
export const LIST_RADIUS = 28;
export const RAIL_RADIUS = 24;
export const BAR_RADIUS = 28;
// The bar's height above the bottom safe-area inset.
export const BAR = 62;

export const MARK = 28;
// The mark's centre and the first row's centre, below the top safe-area inset.
export const MARK_Y = 24;
export const ROW_Y = 96;
export const PITCH = 56;
export const ICON = 24;
export const BAR_ICON = 26;
// The centre of the icon column in the list and in the rail.
export const LIST_X = 42;
export const RAIL_X = 32;
export const LABEL_X = 72;

// The rail becomes the bar in DOCK_IN seconds and the bar becomes the list in DOCK_OUT; each of
// the bar's icons sets off STAGGER seconds after the one below it.
export const DOCK_IN = 0.7;
export const DOCK_OUT = 0.8;
export const STAGGER = 0.025;
// The icons that do not go to the bar are gone after this part of the way.
export const SPARE_OUT = 0.3;

export function clamp(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

export function mix(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

export function easeInOut(t: number) {
  'worklet';
  const c = clamp(t);
  return c < 0.5 ? 4 * c * c * c : 1 - (-2 * c + 2) ** 3 / 2;
}

export function easeOut(t: number) {
  'worklet';
  return 1 - (1 - clamp(t)) ** 3;
}

/**
 * How far the icon that sets off in place `order` is along its flight to the bar, as a quarter
 * turn in radians. `docked` icons set off one after another within the clock `dock`.
 */
export function turnAt(order: number, docked: number, dock: number) {
  'worklet';
  const lag = STAGGER / DOCK_IN;
  return (easeInOut((dock - order * lag) / (1 - (docked - 1) * lag)) * Math.PI) / 2;
}

/**
 * The white surface. `wide` is 1 for the list and 0 for the rail; `dock` is the clock of the rail
 * becoming the bar. The left and bottom edges stay where they are. The right edge goes out with
 * the first icon to leave and the top edge comes down with the last one, so that every icon stays
 * on the surface all the way; the rounding moves from the right corners to the top corners.
 */
export function panelAt(wide: number, dock: number, docked: number, frame: Frame) {
  'worklet';
  const e = easeInOut(dock);
  const strip = mix(RAIL, LIST, wide);
  const corner = mix(RAIL_RADIUS, LIST_RADIUS, clamp(wide));
  return {
    top: (1 - Math.cos(turnAt(docked - 1, docked, dock))) * (frame.height - frame.bar),
    width: mix(strip, frame.width, Math.sin(turnAt(0, docked, dock))),
    topLeft: BAR_RADIUS * e,
    topRight: mix(corner, BAR_RADIUS, e),
    bottomRight: corner * (1 - e),
  };
}

/**
 * Where icon `index` is. In the list and the rail the icons are a column; the first `docked` of
 * them go to the bar, the lowest first, each along a quarter of an ellipse: out to the right, then
 * down, so that mid-way they lie on a diagonal curve in their order. The others fade where they are.
 */
export function iconAt(index: number, docked: number, wide: number, dock: number, frame: Frame) {
  'worklet';
  const x = mix(RAIL_X, LIST_X, wide);
  const y = frame.top + ROW_Y + index * PITCH;
  if (index >= docked) return { x, y, scale: 1, alpha: 1 - clamp(dock / SPARE_OUT), way: 0 };
  const turn = turnAt(docked - 1 - index, docked, dock);
  const way = turn / (Math.PI / 2);
  const toX = (frame.width * (index + 0.5)) / docked;
  const toY = frame.height - frame.bar + BAR / 2;
  return {
    x: x + (toX - x) * Math.sin(turn),
    y: y + (toY - y) * (1 - Math.cos(turn)),
    scale: mix(1, BAR_ICON / ICON, way),
    alpha: 1,
    way,
  };
}

/** The mark rides above the icon column and flies to the top centre as the bar forms. */
export function markAt(wide: number, dock: number, frame: Frame) {
  'worklet';
  return { x: mix(mix(RAIL_X, LIST_X, wide), frame.width / 2, easeInOut(dock)), y: frame.top + MARK_Y };
}
