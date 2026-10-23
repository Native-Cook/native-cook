import { clamp, type Point, type Rect } from './model';

const TRACE_INTERVALS = 80;

/** Advance the uncut edge while keeping the last point attached to the finger. */
export function advanceTearPoints(
  current: readonly Point[],
  position: Point,
  pack: Rect,
  direction: 1 | -1,
): Point[] | null {
  'worklet';
  if (current.length === 0 || pack.width <= 0) return null;

  const last = current[current.length - 1];
  const rawTravel = direction * (position.x - last.x);
  // Moving over material already torn cannot move the cut's frontier,
  // including its height while the finger is behind that frontier.
  if (rawTravel < 0) return null;

  const entryX = direction === 1 ? pack.x : pack.x + pack.width;
  const x = clamp(position.x, pack.x, pack.x + pack.width);
  const corridorTop = pack.y + pack.height * 0.08;
  const corridorBottom = pack.y + pack.height * 0.27;
  const fraction = rawTravel > 0 ? direction * (x - last.x) / rawTravel : 1;
  // A fast event can land outside the pack. Keep the actual segment's
  // intersection with the edge, instead of using the outside finger's y.
  const y = clamp(
    last.y + (position.y - last.y) * fraction,
    corridorTop,
    corridorBottom,
  );
  if (x === last.x && y === last.y) return null;

  // The entry and historical anchors are permanent; only the live endpoint
  // is replaced. At most 79 interior grid anchors survive any number of events.
  const next = current.length > 1 ? current.slice(0, -1) : [current[0]];
  if (rawTravel > 0) {
    for (let index = 1; index < TRACE_INTERVALS; index += 1) {
      const anchorX = entryX + direction * ((pack.width * index) / TRACE_INTERVALS);
      const fromLast = direction * (anchorX - last.x);
      if (fromLast < 0 || direction * (x - anchorX) <= 0) continue;
      next.push({
        x: anchorX,
        y: clamp(
          last.y + (position.y - last.y) * (fromLast / rawTravel),
          corridorTop,
          corridorBottom,
        ),
      });
    }
  }
  next.push({ x, y });
  return next;
}
