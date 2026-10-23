/** Geometry shared by the drag and the flights. */
export type Rect = { x: number; y: number; w: number; h: number };

/** Point on the quadratic curve from a to b, bowed sideways by `arc` of the distance. */
export function curve(ax: number, ay: number, bx: number, by: number, arc: number, t: number) {
  'worklet';
  const mx = (ax + bx) / 2;
  const my = (ay + by) / 2;
  const dx = bx - ax;
  const dy = by - ay;
  // Bow away from the screen's centre line so cards from both columns sweep in from the sides.
  const side = ax < bx ? -1 : 1;
  const cx = mx + side * Math.abs(dy) * arc;
  const cy = my - Math.abs(dx) * arc * 0.5;
  const u = 1 - t;
  return { x: u * u * ax + 2 * u * t * cx + t * t * bx, y: u * u * ay + 2 * u * t * cy + t * t * by };
}
