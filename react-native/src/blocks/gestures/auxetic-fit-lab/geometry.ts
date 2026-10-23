import { EDGE, type Point } from './motion';

const BOUNDARY = [[0, 0], [0, 3], [1, 1], [2, 0], [3, 1], [3, 2], [4, 0], [7, 1], [8, 2], [8, 3], [9, 3], [10, 2], [11, 2], [11, 3]];
export function geometry(angle: number) { 'worklet'; const radians = angle * Math.PI / 180, cosine = Math.cos(radians), sine = Math.sin(radians), spacing = EDGE * (cosine + sine); return { cosine, sine, spacing, bounds: { x: 196.5 - 2 * spacing, y: 399 - 1.5 * spacing, width: 4 * spacing, height: 3 * spacing } }; }
export type Geometry = ReturnType<typeof geometry>;
export function center(g: Geometry, column: number, row: number): Point { 'worklet'; return { x: 196.5 + (column - 1.5) * g.spacing, y: 399 + (row - 1) * g.spacing }; }
function vertex(g: Geometry, cell: number, corner: number): Point { 'worklet'; const column = cell % 4, row = Math.floor(cell / 4), sign = (column + row) % 2 === 0 ? 1 : -1, half = EDGE / 2, x = corner === 0 || corner === 3 ? -half : half, y = corner === 0 || corner === 1 ? -half : half, c = center(g, column, row); return { x: c.x + g.cosine * x - sign * g.sine * y, y: c.y + sign * g.sine * x + g.cosine * y }; }
/** Stable structural identities:9 horizontal,8 vertical,14 boundary; no proximity deduplication. */
export function pins(g: Geometry): Point[] {
  'worklet'; const result: Point[] = [];
  for (let row = 0; row < 3; row++) for (let col = 0; col < 3; col++) result.push(vertex(g, row * 4 + col, (col + row) % 2 === 0 ? 1 : 2));
  for (let row = 0; row < 2; row++) for (let col = 0; col < 4; col++) result.push(vertex(g, row * 4 + col, (col + row) % 2 === 0 ? 2 : 3));
  for (const pair of BOUNDARY) result.push(vertex(g, pair[0], pair[1])); return result;
}
