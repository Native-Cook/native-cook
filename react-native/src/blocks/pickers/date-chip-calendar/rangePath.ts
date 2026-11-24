// One continuous rounded shape through the weeks of a month.
import { Skia, type SkPath } from '@shopify/react-native-skia';

type Pt = { x: number; y: number };

function roundedPolygon(points: Pt[], radius: number): SkPath {
  'worklet';
  const b = Skia.PathBuilder.Make();
  const n = points.length;
  for (let i = 0; i < n; i += 1) {
    const p = points[i];
    const prev = points[(i + n - 1) % n];
    const next = points[(i + 1) % n];
    const inX = p.x - prev.x;
    const inY = p.y - prev.y;
    const outX = next.x - p.x;
    const outY = next.y - p.y;
    const inLen = Math.hypot(inX, inY) || 1;
    const outLen = Math.hypot(outX, outY) || 1;
    const r = Math.min(radius, inLen / 2, outLen / 2);
    const ax = p.x - (inX / inLen) * r;
    const ay = p.y - (inY / inLen) * r;
    const cx = p.x + (outX / outLen) * r;
    const cy = p.y + (outY / outLen) * r;
    if (i === 0) b.moveTo(ax, ay);
    else b.lineTo(ax, ay);
    b.quadTo(p.x, p.y, cx, cy);
  }
  b.close();
  return b.detach();
}

function addPolygon(target: ReturnType<typeof Skia.PathBuilder.Make>, points: Pt[], radius: number) {
  'worklet';
  target.addPath(roundedPolygon(points, radius));
}

/** Drops repeated points and points on a straight line, which would round into nothing. */
function clean(points: Pt[]) {
  'worklet';
  const out: Pt[] = [];
  for (let i = 0; i < points.length; i += 1) {
    const p = points[i];
    const prev = points[(i + points.length - 1) % points.length];
    const next = points[(i + 1) % points.length];
    const same = Math.abs(p.x - prev.x) < 0.01 && Math.abs(p.y - prev.y) < 0.01;
    const straight = (Math.abs(prev.x - p.x) < 0.01 && Math.abs(p.x - next.x) < 0.01)
      || (Math.abs(prev.y - p.y) < 0.01 && Math.abs(p.y - next.y) < 0.01);
    if (!same && !straight) out.push(p);
  }
  return out;
}

function pill(x0: number, x1: number, top: number, bottom: number): Pt[] {
  'worklet';
  return [{ x: x0, y: top }, { x: x1, y: top }, { x: x1, y: bottom }, { x: x0, y: bottom }];
}

/**
 * The range from day `start` to the (fractional) day `head`, clipped to one month.
 * `first`, `offset`, `days` describe the month; `cell` and `row` are the grid's pitch; `inset` trims each row's band.
 */
export function buildRange(
  start: number,
  head: number,
  first: number,
  offset: number,
  days: number,
  cell: number,
  row: number,
  inset: number,
): SkPath {
  'worklet';
  const empty = Skia.PathBuilder.Make();
  if (start < 0) return empty.detach();
  const lo = Math.min(start, head);
  const hi = Math.max(start, head);
  const last = first + days - 1;
  if (hi < first || lo > last) return empty.detach();

  const from = Math.max(lo, first) - first + offset;
  const to = Math.min(hi, last) - first + offset + 1; // exclusive end, fractional while the head travels
  const rowA = Math.floor(from / 7);
  const rowB = Math.floor((to - 0.0001) / 7);
  const xA = (from - rowA * 7) * cell;
  const xB = (to - rowB * 7) * cell;
  const width = cell * 7;
  const radius = (row - inset * 2) / 2;
  const topA = rowA * row + inset;
  const bottomA = (rowA + 1) * row - inset;
  const topB = rowB * row + inset;
  const bottomB = (rowB + 1) * row - inset;

  if (rowA === rowB) {
    addPolygon(empty, pill(xA, xB, topA, bottomA), radius);
    return empty.detach();
  }
  // Two weeks that do not overlap cannot be joined: two pills.
  if (rowB === rowA + 1 && xB <= xA + radius) {
    addPolygon(empty, pill(xA, width, topA, bottomA), radius);
    addPolygon(empty, pill(0, xB, topB, bottomB), radius);
    return empty.detach();
  }
  addPolygon(
    empty,
    clean([
      { x: xA, y: topA },
      { x: width, y: topA },
      { x: width, y: topB - inset * 2 },
      { x: xB, y: topB - inset * 2 },
      { x: xB, y: bottomB },
      { x: 0, y: bottomB },
      { x: 0, y: bottomA + inset * 2 },
      { x: xA, y: bottomA + inset * 2 },
    ]),
    radius,
  );
  return empty.detach();
}
