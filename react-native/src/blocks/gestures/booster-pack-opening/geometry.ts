import {
  Skia,
  type SkPath,
  type SkPathBuilder,
  type SkPoint,
} from '@shopify/react-native-skia';

import { clamp, type Point, type Rect } from './model';

export const MESH_COLUMNS = 28;
export const MOUTH_MESH_COLUMNS = 56;
export const MOUTH_MESH_INDICES = Array.from(
  { length: MOUTH_MESH_COLUMNS - 1 }, (_, column) => {
    const a = column*2;
    return [a, a+1, a+2, a+2, a+1, a+3];
  },
).flat();
export const MOUTH_WALL_ROWS = 5;
export const MOUTH_WALL_INDICES = Array.from(
  { length: (MOUTH_MESH_COLUMNS-1)*(MOUTH_WALL_ROWS-1) }, (_, index) => {
    const column = Math.floor(index/(MOUTH_WALL_ROWS-1));
    const row = index%(MOUTH_WALL_ROWS-1);
    const a = column*MOUTH_WALL_ROWS+row;
    const b = a+MOUTH_WALL_ROWS;
    return [a, a+1, b, b, a+1, b+1];
  },
).flat();
export const PREVIEW_MESH_COLUMNS = 64;
export const PREVIEW_MESH_ROWS = 9;

// Concentrate vertices at both curling ends while retaining a symmetric mesh
// for either swipe direction. The detached strip has its own coarser topology.
export function previewColumnU(column: number) {
  'worklet';
  return (1-Math.cos(Math.PI*column/(PREVIEW_MESH_COLUMNS-1)))*0.5;
}

export function makeEmptyPath() {
  'worklet';
  return Skia.PathBuilder.Make().build();
}

export function makeSmoothPath(points: readonly Point[]): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  if (points.length === 0) return builder.build();

  builder.moveTo(points[0].x, points[0].y);
  if (points.length === 1) return builder.build();

  for (let index = 1; index < points.length - 1; index += 1) {
    const current = points[index];
    const next = points[index + 1];
    builder.quadTo(
      current.x,
      current.y,
      (current.x + next.x) / 2,
      (current.y + next.y) / 2,
    );
  }

  const beforeLast = points[points.length - 2];
  const last = points[points.length - 1];
  builder.quadTo(beforeLast.x, beforeLast.y, last.x, last.y);
  return builder.build();
}

export function trimTraceEnd(
  points: readonly Point[],
  trimDistance: number,
): Point[] {
  'worklet';
  if (points.length < 2 || trimDistance <= 0) return [...points];

  const trimmed = [...points];
  let remaining = trimDistance;

  while (trimmed.length > 1) {
    const endIndex = trimmed.length - 1;
    const end = trimmed[endIndex];
    const previous = trimmed[endIndex - 1];
    const dx = end.x - previous.x;
    const dy = end.y - previous.y;
    const length = Math.sqrt(dx * dx + dy * dy);

    if (length > remaining && length > 0.0001) {
      const retained = (length - remaining) / length;
      trimmed[endIndex] = {
        x: previous.x + dx * retained,
        y: previous.y + dy * retained,
      };
      return trimmed;
    }

    remaining -= length;
    trimmed.pop();
  }

  // A just-started gesture can be shorter than the cap radius. Keep a tiny
  // segment so Skia can still draw a stable clipped onset instead of a dot.
  const first = points[0];
  const last = points[points.length - 1];
  const dx = last.x - first.x;
  const dy = last.y - first.y;
  const length = Math.sqrt(dx * dx + dy * dy);
  if (length <= 0.0001) return [first];
  const retained = Math.min(0.5 / length, 1);
  return [
    first,
    {
      x: first.x + dx * retained,
      y: first.y + dy * retained,
    },
  ];
}

export function completeCutPoints(
  inputPoints: readonly Point[],
  pack: Rect,
  direction: 1 | -1,
): Point[] {
  'worklet';
  if (inputPoints.length === 0) return [];

  const ordered = direction === 1 ? [...inputPoints] : [...inputPoints].reverse();
  const first = ordered[0];
  const last = ordered[ordered.length - 1];
  const left = pack.x;
  const right = pack.x + pack.width;

  const completed: Point[] = [];

  // The gesture arms before the finger reaches the opposite edge. Preserve the
  // measured exit tangent through that final gap, then ease it flat at the pack
  // boundary. A single horizontal append made every rim layer kink apart here.
  if (direction === -1 && first.x > left) {
    const next = ordered[Math.min(1, ordered.length - 1)];
    const gap = first.x - left;
    const slope = clamp(
      (next.y - first.y) / Math.max(next.x - first.x, 0.0001),
      -0.35,
      0.35,
    );
    const endY = first.y - slope * gap * 0.35;
    for (const t of [1, 2 / 3, 1 / 3]) {
      const y =
        (2 * t * t * t - 3 * t * t + 1) * first.y +
        (t * t * t - 2 * t * t + t) * -gap * slope +
        (-2 * t * t * t + 3 * t * t) * endY +
        (t * t * t - t * t) * -gap * slope * 0.75;
      completed.push({ x: first.x - gap * t, y });
    }
  } else if (first.x > left) {
    completed.push({ x: left, y: first.y });
  }

  completed.push(...ordered);

  if (direction === 1 && last.x < right) {
    const previous = ordered[Math.max(0, ordered.length - 2)];
    const gap = right - last.x;
    const slope = clamp(
      (last.y - previous.y) / Math.max(last.x - previous.x, 0.0001),
      -0.35,
      0.35,
    );
    const endY = last.y + slope * gap * 0.35;
    for (const t of [1 / 3, 2 / 3, 1]) {
      const y =
        (2 * t * t * t - 3 * t * t + 1) * last.y +
        (t * t * t - 2 * t * t + t) * gap * slope +
        (-2 * t * t * t + 3 * t * t) * endY +
        (t * t * t - t * t) * gap * slope * 0.75;
      completed.push({ x: last.x + gap * t, y });
    }
  } else if (last.x < right) {
    completed.push({ x: right, y: last.y });
  }
  return completed;
}

export function completePreviewCutPoints(
  inputPoints: readonly Point[],
  pack: Rect,
  direction: 1 | -1,
  seamY: number,
): Point[] {
  'worklet';
  if (inputPoints.length === 0) return [];

  const ordered = direction === 1 ? [...inputPoints] : [...inputPoints].reverse();
  const left = pack.x;
  const right = pack.x + pack.width;
  const transitionWidth = pack.width * 0.045;

  if (direction === 1) {
    const first = ordered[0];
    const head = ordered[ordered.length - 1];
    const completed: Point[] = [];
    if (first.x > left) completed.push({ x: left, y: first.y });
    completed.push(...ordered);
    if (head.x < right) {
      const transitionX = Math.min(right, head.x + transitionWidth);
      if (transitionX > head.x + 0.5) {
        completed.push({ x: transitionX, y: seamY });
      }
      if (transitionX < right) completed.push({ x: right, y: seamY });
    }
    return completed;
  }

  const head = ordered[0];
  const completed: Point[] = [{ x: left, y: seamY }];
  const transitionX = Math.max(left, head.x - transitionWidth);
  if (transitionX > left + 0.5) {
    completed.push({ x: transitionX, y: seamY });
  }
  completed.push(...ordered);
  const last = ordered[ordered.length - 1];
  if (last.x < right) completed.push({ x: right, y: last.y });
  return completed;
}

export function makeTopMask(points: readonly Point[], pack: Rect): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  if (points.length < 2) return builder.build();

  const left = pack.x;
  const right = pack.x + pack.width;
  builder.moveTo(left, pack.y);
  builder.lineTo(right, pack.y);
  builder.lineTo(points[points.length - 1].x, points[points.length - 1].y);

  for (let index = points.length - 2; index > 0; index -= 1) {
    const current = points[index];
    const next = points[index - 1];
    builder.quadTo(
      current.x,
      current.y,
      (current.x + next.x) / 2,
      (current.y + next.y) / 2,
    );
  }
  const afterFirst = points[1];
  const first = points[0];
  builder.quadTo(afterFirst.x, afterFirst.y, first.x, first.y);
  builder.close();
  return builder.build();
}

export function makeBottomMask(points: readonly Point[], pack: Rect): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  if (points.length < 2) return builder.build();

  builder.moveTo(points[0].x, points[0].y);
  for (let index = 1; index < points.length - 1; index += 1) {
    const current = points[index];
    const next = points[index + 1];
    builder.quadTo(
      current.x,
      current.y,
      (current.x + next.x) / 2,
      (current.y + next.y) / 2,
    );
  }
  const beforeLast = points[points.length - 2];
  const last = points[points.length - 1];
  builder.quadTo(beforeLast.x, beforeLast.y, last.x, last.y);
  builder.lineTo(pack.x + pack.width, pack.y + pack.height);
  builder.lineTo(pack.x, pack.y + pack.height);
  builder.close();
  return builder.build();
}

function yAtX(points: readonly Point[], x: number) {
  'worklet';
  if (points.length === 0) return 0;
  if (x <= points[0].x) return points[0].y;
  const last = points.length - 1;
  if (x >= points[last].x) return points[last].y;

  for (let index = 1; index < points.length; index += 1) {
    const right = points[index];
    if (x <= right.x) {
      const left = points[index - 1];
      const width = Math.max(0.0001, right.x - left.x);
      const progress = clamp((x - left.x) / width, 0, 1);
      return left.y + (right.y - left.y) * progress;
    }
  }
  return points[last].y;
}

export function makeFoilMesh(
  points: readonly Point[],
  pack: Rect,
): { vertices: SkPoint[]; textures: SkPoint[] } {
  'worklet';
  const vertices: SkPoint[] = [];
  const textures: SkPoint[] = [];

  for (let column = 0; column < MESH_COLUMNS; column += 1) {
    const u = column / (MESH_COLUMNS - 1);
    const x = pack.x + u * pack.width;
    const bottomY = yAtX(points, x);
    const top = { x, y: pack.y };
    const bottom = { x, y: bottomY };
    vertices.push(top, bottom);
    textures.push(top, bottom);
  }
  return { vertices, textures };
}

export function makePreviewFoilMesh(
  points: readonly Point[],
  pack: Rect,
): { vertices: SkPoint[]; textures: SkPoint[] } {
  'worklet';
  const vertices: SkPoint[] = [];
  const textures: SkPoint[] = [];

  for (let column = 0; column < PREVIEW_MESH_COLUMNS; column += 1) {
    const u = previewColumnU(column);
    const x = pack.x + u * pack.width;
    const bottomY = yAtX(points, x);

    for (let row = 0; row < PREVIEW_MESH_ROWS; row += 1) {
      const v = row / (PREVIEW_MESH_ROWS - 1);
      const y = pack.y + (bottomY - pack.y) * v;
      const point = { x, y };
      vertices.push(point);
      textures.push(point);
    }
  }
  return { vertices, textures };
}

export function makeCollapsedFoilMesh(
  pack: Rect,
): { vertices: SkPoint[]; textures: SkPoint[] } {
  'worklet';
  const vertices: SkPoint[] = [];
  const textures: SkPoint[] = [];
  for (let index = 0; index < MESH_COLUMNS * 2; index += 1) {
    vertices.push({ x: pack.x, y: pack.y });
    textures.push({ x: pack.x, y: pack.y });
  }
  return { vertices, textures };
}

export function makeCollapsedPreviewFoilMesh(
  pack: Rect,
): { vertices: SkPoint[]; textures: SkPoint[] } {
  'worklet';
  const vertices: SkPoint[] = [];
  const textures: SkPoint[] = [];
  for (let index = 0; index < PREVIEW_MESH_COLUMNS * PREVIEW_MESH_ROWS; index += 1) {
    vertices.push({ x: pack.x, y: pack.y });
    textures.push({ x: pack.x, y: pack.y });
  }
  return { vertices, textures };
}

function cavityDepthAt(
  point: Point,
  pack: Rect,
  depth: number,
  direction: 1 | -1,
  exponent: number,
) {
  'worklet';
  const u = clamp((point.x - pack.x) / Math.max(pack.width, 0.0001), 0, 1);
  const directionalU = direction === 1 ? u : 1 - u;
  // The physical tear has a shallow entry cuff and opens continuously toward
  // the swipe exit. This profile fits the measured frame-150/162/170 section
  // depths and mirrors automatically for a right-to-left swipe.
  const entryBump = 0.09 * Math.pow(1 - directionalU, 4);
  return depth *
    Math.min(
      1,
      0.18 + 0.82 * Math.pow(directionalU, exponent) + entryBump,
    );
}

function frontLipDepthAt(
  point: Point,
  pack: Rect,
  depth: number,
  direction: 1 | -1,
) {
  'worklet';
  const u = clamp((point.x - pack.x) / Math.max(pack.width, 0.0001), 0, 1);
  const directionalU = direction === 1 ? u : 1 - u;
  // The laminated front lip stays narrow at the tear entry and fans out into
  // the exit cuff. Keeping this on the same directional coordinate makes the
  // full cross-section mirror cleanly for right-to-left gestures.
  return depth * (0.56 + 0.44 * directionalU);
}

function liftFrontLipPoints(
  points: readonly Point[],
  pack: Rect,
  lift: number,
  direction: 1 | -1,
  middleLift = 0,
): Point[] {
  'worklet';
  if (lift <= 0) return [...points];

  return points.map((point) => {
    const u = clamp(
      (point.x - pack.x) / Math.max(pack.width, 0.0001),
      0,
      1,
    );
    const directionalU = direction === 1 ? u : 1 - u;
    // The reference cuff rises as one laminate near the swipe exit.  A
    // smoothstep keeps the measured center section untouched and gives every
    // front layer the same tangent instead of stacking disconnected patches.
    const t = clamp((directionalU - 0.89) / 0.11, 0, 1);
    const eased = t * t * (3 - 2 * t);
    // The last few percent curl more sharply than the long approach. Keeping
    // this as a high-order terminal term preserves the measured center line,
    // while the very end rises as one bonded laminate instead of closing as a
    // flat capsule.
    const exitCurl = 0.78 * Math.pow(t, 6);
    let middleProfile = 0;
    if (directionalU >= 0.25 && directionalU < 0.43) {
      const middleT = clamp((directionalU - 0.25) / 0.18, 0, 1);
      middleProfile = middleT * middleT * (3 - 2 * middleT);
    } else if (directionalU >= 0.43 && directionalU < 0.58) {
      const middleT = clamp((directionalU - 0.43) / 0.15, 0, 1);
      middleProfile = 1 - middleT * 0.25;
    } else if (directionalU >= 0.58 && directionalU < 0.66) {
      const middleT = clamp((directionalU - 0.58) / 0.08, 0, 1);
      const middleEased = middleT * middleT * (3 - 2 * middleT);
      middleProfile = 0.75 * (1 - middleEased);
    }
    return {
      x: point.x,
      y:
        point.y -
        lift * (eased + exitCurl) -
        middleLift * middleProfile,
    };
  });
}

function normalAt(points: readonly Point[], index: number): Point {
  'worklet';
  const point = points[index];
  let previousIndex = Math.max(0, index - 1);
  let nextIndex = Math.min(points.length - 1, index + 1);
  while (
    previousIndex > 0 &&
    Math.abs(points[previousIndex].x - point.x) +
      Math.abs(points[previousIndex].y - point.y) <
      0.0001
  ) {
    previousIndex -= 1;
  }
  while (
    nextIndex < points.length - 1 &&
    Math.abs(points[nextIndex].x - point.x) +
      Math.abs(points[nextIndex].y - point.y) <
      0.0001
  ) {
    nextIndex += 1;
  }
  const previous = points[previousIndex];
  const next = points[nextIndex];
  const dx = next.x - previous.x;
  const dy = next.y - previous.y;
  const length = Math.sqrt(dx * dx + dy * dy);
  if (length < 0.0001) return { x: 0, y: -1 };
  return { x: dy / length, y: -dx / length };
}

function offsetCavityPoints(
  points: readonly Point[],
  pack: Rect,
  depth: number,
  direction: 1 | -1,
  side: 1 | -1,
  exponent: number,
): Point[] {
  'worklet';
  return points.map((point, index) => {
    const normal = normalAt(points, index);
    const pointDepth =
      cavityDepthAt(point, pack, depth, direction, exponent) * side;
    const u = clamp(
      (point.x - pack.x) / Math.max(pack.width, 0.0001),
      0,
      1,
    );
    const directionalU = direction === 1 ? u : 1 - u;
    // The exposed back wall is not parallel at the exit: it settles, rises to
    // a small apex, then wraps back into the terminal cuff. Scale this by the
    // requested offset depth so the main wall receives the full profile while
    // the near edge of the shadow band stays almost unchanged.
    let exitArchFactor = 0;
    if (directionalU >= 0.7 && directionalU < 0.8) {
      const t = clamp((directionalU - 0.7) / 0.1, 0, 1);
      exitArchFactor = 0.125 * t * t * (3 - 2 * t);
    } else if (directionalU < 0.86) {
      exitArchFactor = directionalU >= 0.8 ? 0.125 : 0;
    } else if (directionalU < 0.94) {
      const t = clamp((directionalU - 0.86) / 0.08, 0, 1);
      exitArchFactor = 0.125 * (1 - t * t * (3 - 2 * t));
    } else if (directionalU < 0.965) {
      const t = clamp((directionalU - 0.94) / 0.025, 0, 1);
      exitArchFactor = -0.085 * t * t * (3 - 2 * t);
    } else if (directionalU < 0.98) {
      const t = clamp((directionalU - 0.965) / 0.015, 0, 1);
      const eased = t * t * (3 - 2 * t);
      exitArchFactor = -0.085 + 0.125 * eased;
    } else {
      const t = clamp((directionalU - 0.98) / 0.02, 0, 1);
      exitArchFactor = 0.04 * (1 - t * t * (3 - 2 * t));
    }
    const exitArch = Math.abs(depth) * exitArchFactor;
    const isEntry = direction === 1 ? index === 0 : index === points.length - 1;
    return {
      // The dark cavity closes first at the entry, then the brighter laminate
      // opens from that same nose. Keeping x pinned here avoids stair-steps;
      // the small vertical span matches the compressed three-pixel dark tip.
      x: isEntry ? point.x : point.x + normal.x * pointDepth,
      y: isEntry
        ? point.y - Math.abs(pointDepth) * 0.45
        : point.y + normal.y * pointDepth + exitArch,
    };
  });
}

function monotoneSlopeAt(previous: Point, point: Point, next: Point) {
  'worklet';
  const previousDelta = point.x - previous.x;
  const nextDelta = next.x - point.x;
  const previousWidth = Math.abs(previousDelta);
  const nextWidth = Math.abs(nextDelta);
  if (previousWidth < 0.0001 && nextWidth < 0.0001) return 0;
  if (previousWidth < 0.0001) {
    return nextWidth < 0.0001 ? 0 : (next.y - point.y) / nextDelta;
  }
  if (nextWidth < 0.0001) {
    return (point.y - previous.y) / previousDelta;
  }

  const previousSlope = (point.y - previous.y) / previousDelta;
  const nextSlope = (next.y - point.y) / nextDelta;
  if (previousSlope * nextSlope <= 0) return 0;

  const previousWeight = 2 * nextWidth + previousWidth;
  const nextWeight = nextWidth + 2 * previousWidth;
  const denominator =
    previousWeight / previousSlope + nextWeight / nextSlope;
  if (Math.abs(denominator) < 0.0001) return 0;
  return (previousWeight + nextWeight) / denominator;
}

function appendCubicSpline(
  builder: SkPathBuilder,
  points: readonly Point[],
) {
  'worklet';

  for (let index = 0; index < points.length - 1; index += 1) {
    const p0 = points[Math.max(0, index - 1)];
    const p1 = points[index];
    const p2 = points[index + 1];
    const p3 = points[Math.min(points.length - 1, index + 2)];
    const segmentWidth = p2.x - p1.x;
    if (Math.abs(segmentWidth) < 0.0001) {
      builder.lineTo(p2.x, p2.y);
      continue;
    }
    const startSlope = monotoneSlopeAt(p0, p1, p2);
    const endSlope = monotoneSlopeAt(p1, p2, p3);
    builder.cubicTo(
      p1.x + segmentWidth / 3,
      p1.y + (startSlope * segmentWidth) / 3,
      p2.x - segmentWidth / 3,
      p2.y - (endSlope * segmentWidth) / 3,
      p2.x,
      p2.y,
    );
  }
}

function makeCubicSplinePath(points: readonly Point[]): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  if (points.length === 0) return builder.build();
  builder.moveTo(points[0].x, points[0].y);
  appendCubicSpline(builder, points);
  return builder.build();
}

function appendRoundedSide(
  builder: SkPathBuilder,
  from: Point,
  to: Point,
  outward: Point,
  normal: Point,
  depth: number,
  reverse: boolean,
) {
  'worklet';
  const bulge = depth * 0.16;
  if (reverse) {
    builder.cubicTo(
      from.x + outward.x * bulge - normal.x * depth * 0.18,
      from.y + outward.y * bulge - normal.y * depth * 0.18,
      to.x + outward.x * bulge + normal.x * depth * 0.18,
      to.y + outward.y * bulge + normal.y * depth * 0.18,
      to.x,
      to.y,
    );
    return;
  }
  builder.cubicTo(
    from.x + outward.x * bulge + normal.x * depth * 0.18,
    from.y + outward.y * bulge + normal.y * depth * 0.18,
    to.x + outward.x * bulge - normal.x * depth * 0.18,
    to.y + outward.y * bulge - normal.y * depth * 0.18,
    to.x,
    to.y,
  );
}

function appendFrontLipSide(
  builder: SkPathBuilder,
  from: Point,
  to: Point,
  outward: Point,
  normal: Point,
  depth: number,
  reverse: boolean,
) {
  'worklet';
  // The laminate wraps farther around the terminal cuff than the cavity wall.
  // A slightly broader shared bulge lets every nested band meet the same outer
  // skin instead of fading one pixel before it.
  const bulge = depth * 0.26;
  if (reverse) {
    builder.cubicTo(
      from.x + outward.x * bulge + normal.x * depth * 0.18,
      from.y + outward.y * bulge + normal.y * depth * 0.18,
      to.x + outward.x * bulge - normal.x * depth * 0.18,
      to.y + outward.y * bulge - normal.y * depth * 0.18,
      to.x,
      to.y,
    );
    return;
  }
  builder.cubicTo(
    from.x + outward.x * bulge - normal.x * depth * 0.18,
    from.y + outward.y * bulge - normal.y * depth * 0.18,
    to.x + outward.x * bulge + normal.x * depth * 0.18,
    to.y + outward.y * bulge + normal.y * depth * 0.18,
    to.x,
    to.y,
  );
}

function appendExitOuterCurve(
  builder: SkPathBuilder,
  lower: Point,
  side: Point,
  back: Point,
  outward: Point,
  normal: Point,
  bulge: number,
  lipDepth: number,
  cavityDepth: number,
) {
  'worklet';
  builder.cubicTo(
    lower.x + outward.x * bulge * 0.9,
    lower.y + outward.y * bulge * 0.9,
    side.x - normal.x * lipDepth * 0.3,
    side.y - normal.y * lipDepth * 0.3,
    side.x,
    side.y,
  );
  builder.cubicTo(
    side.x + normal.x * cavityDepth * 0.3,
    side.y + normal.y * cavityDepth * 0.3,
    back.x + outward.x * bulge * 0.9,
    back.y + outward.y * bulge * 0.9,
    back.x,
    back.y,
  );
}

export function relaxMouthPoints(
  points: readonly Point[],
  pack: Rect,
  entryOffset: number,
  exitOffset: number,
  exponent: number,
  centerLift: number,
  direction: 1 | -1,
): Point[] {
  'worklet';
  return points.map((point) => {
    const u = clamp((point.x - pack.x) / Math.max(pack.width, 0.0001), 0, 1);
    const directionalU = direction === 1 ? u : 1 - u;
    const spatialOffset =
      entryOffset +
      (exitOffset - entryOffset) * Math.pow(directionalU, exponent) +
      centerLift *
        Math.pow(Math.max(0, Math.sin(Math.PI * directionalU)), 1.4);
    return { x: point.x, y: point.y + spatialOffset };
  });
}

export function makeMouthRibbon(
  points: readonly Point[],
  pack: Rect,
  depth: number,
  direction: 1 | -1,
  exponent: number,
): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  if (points.length < 2) return builder.build();
  const front = [...points];
  const back = offsetCavityPoints(points, pack, depth, direction, 1, exponent);
  const lastIndex = points.length - 1;

  builder.moveTo(front[0].x, front[0].y);
  appendCubicSpline(builder, front);
  const rightNormal = normalAt(points, lastIndex);
  const rightTangent = {
    x: (front[lastIndex].x - front[lastIndex - 1].x),
    y: (front[lastIndex].y - front[lastIndex - 1].y),
  };
  const rightLength = Math.max(
    Math.sqrt(rightTangent.x * rightTangent.x + rightTangent.y * rightTangent.y),
    0.0001,
  );
  const rightOutward = {
    x: rightTangent.x / rightLength,
    y: rightTangent.y / rightLength,
  };
  appendRoundedSide(
    builder,
    front[lastIndex],
    back[lastIndex],
    rightOutward,
    rightNormal,
    Math.sqrt(
      Math.pow(back[lastIndex].x - front[lastIndex].x, 2) +
        Math.pow(back[lastIndex].y - front[lastIndex].y, 2),
    ),
    false,
  );

  const reversedBack = [...back].reverse();
  appendCubicSpline(builder, reversedBack);

  const leftNormal = normalAt(points, 0);
  const leftTangent = {
    x: front[0].x - front[1].x,
    y: front[0].y - front[1].y,
  };
  const leftLength = Math.max(
    Math.sqrt(leftTangent.x * leftTangent.x + leftTangent.y * leftTangent.y),
    0.0001,
  );
  const leftOutward = {
    x: leftTangent.x / leftLength,
    y: leftTangent.y / leftLength,
  };
  appendRoundedSide(
    builder,
    back[0],
    front[0],
    leftOutward,
    leftNormal,
    Math.sqrt(
      Math.pow(back[0].x - front[0].x, 2) +
        Math.pow(back[0].y - front[0].y, 2),
    ),
    true,
  );
  builder.close();
  return builder.build();
}

export function makeCavityBackEdge(
  points: readonly Point[],
  pack: Rect,
  depth: number,
  direction: 1 | -1,
  exponent: number,
): SkPath {
  'worklet';
  return makeCubicSplinePath(
    offsetCavityPoints(points, pack, depth, direction, 1, exponent),
  );
}

export function makeCavityFrontEdge(points: readonly Point[]): SkPath {
  'worklet';
  return makeCubicSplinePath(points);
}

function mouthYAtX(points: readonly Point[], x: number) {
  'worklet';
  if (points.length < 2 || x <= points[0].x || x >= points[points.length-1].x) {
    return yAtX(points, x);
  }
  for (let i = 1; i < points.length; i += 1) {
    if (x > points[i].x) continue;
    const left = points[i-1];
    const right = points[i];
    const dx = Math.max(right.x-left.x, 0.0001);
    const t = clamp((x-left.x)/dx, 0, 1);
    const a = monotoneSlopeAt(points[Math.max(0,i-2)], left, right);
    const b = monotoneSlopeAt(left, right, points[Math.min(points.length-1,i+1)]);
    return (2*t*t*t-3*t*t+1)*left.y + (t*t*t-2*t*t+t)*dx*a
      + (-2*t*t*t+3*t*t)*right.y + (t*t*t-t*t)*dx*b;
  }
  return points[points.length-1].y;
}

// Resample before curling so even a sparse gesture has a smooth terminal
// bend. This same front contour drives the body mask, laminate and wall.
export function curlMouthPoints(
  points: readonly Point[], pack: Rect, lift: number, direction: 1 | -1,
): Point[] {
  'worklet';
  if (points.length < 2) return [...points];
  return Array.from({ length: MOUTH_MESH_COLUMNS }, (_, index) => {
    const u = index/(MOUTH_MESH_COLUMNS-1);
    const x = pack.x + u*pack.width;
    // A broad turn ends in a rising crease, as the foil does in frames
    // 162–178. A smoothstep here flattens at the tip into a rounded lip.
    const t = clamp(((direction === 1 ? u : 1-u)-0.8)/0.2, 0, 1);
    return { x, y: mouthYAtX(points, x)-lift*Math.pow(t, 1.7) };
  });
}

// Worklet helpers must be initialized before factories that capture them.
function mouthColumnU(index: number) {
  'worklet';
  if (index < 12) return 0.06*(1-Math.cos(index/11*Math.PI/2));
  if (index >= MOUTH_MESH_COLUMNS-12) {
    return 1-0.06*(1-Math.cos((MOUTH_MESH_COLUMNS-1-index)/11*Math.PI/2));
  }
  return 0.06+(index-11)/(MOUTH_MESH_COLUMNS-23)*0.88;
}

// Material coordinates remain attached to the curved laminate as its opening
// and the user's tear path change. Both surfaces use the foil strip topology.
export function makeMouthMaterialMesh(
  points: readonly Point[],
  pack: Rect,
  depth: number,
  direction: 1 | -1,
  exponent: number,
  frontLip = false,
  exitLift = 0,
  backPoints: readonly Point[] = points,
) {
  'worklet';
  if (points.length < 2) return {
    vertices: Array.from({ length: MOUTH_MESH_COLUMNS*2 }, () => ({ x: pack.x, y: pack.y })),
    textures: Array.from({ length: MOUTH_MESH_COLUMNS*2 }, () => ({ x: 0, y: 0 })),
  };
  const samples = Array.from({ length: MOUTH_MESH_COLUMNS }, (_, index) => {
    const u = frontLip ? mouthColumnU(index) : index/(MOUTH_MESH_COLUMNS-1);
    const x = pack.x + u*pack.width;
    return { x, y: mouthYAtX(points, x) };
  });
  const backSamples = frontLip ? [] : samples.map((point) => ({
    x: point.x, y: mouthYAtX(backPoints, point.x),
  }));
  const front = frontLip
    ? liftFrontLipPoints(samples, pack, exitLift, direction).map((point) => ({
        x: point.x, y: point.y - depth * 0.15,
      }))
    : samples;
  const outer = frontLip
    ? front.map((point, index) => {
        const normal = normalAt(samples, index);
        const d = frontLipDepthAt(point, pack, depth, direction);
        return { x: point.x - normal.x * d, y: point.y - normal.y * d };
      })
    : backSamples.map((point, index) => {
        const normal = normalAt(backSamples, index);
        const d = cavityDepthAt(point, pack, depth, direction, exponent);
        return { x: point.x + normal.x*d, y: point.y + normal.y*d };
      });
  const vertices: SkPoint[] = [];
  const textures: SkPoint[] = [];
  for (let index = 0; index < MOUTH_MESH_COLUMNS; index += 1) {
    const u = (samples[index].x-pack.x)/pack.width;
    vertices.push(frontLip ? front[index] : outer[index]);
    vertices.push(frontLip ? outer[index] : front[index]);
    textures.push({ x: direction === 1 ? u : 1 - u, y: 0 });
    textures.push({ x: direction === 1 ? u : 1 - u, y: 1 });
  }
  return { vertices, textures };
}

export function makeMouthWallMesh(
  frontPoints: readonly Point[], backPoints: readonly Point[], pack: Rect,
  depth: number, direction: 1 | -1, exponent: number,
) {
  'worklet';
  const vertices: SkPoint[] = [];
  const textures: SkPoint[] = [];
  const valid = frontPoints.length > 1 && backPoints.length > 1;
  for (let column = 0; column < MOUTH_MESH_COLUMNS; column += 1) {
    const u = mouthColumnU(column);
    const directionalU = direction === 1 ? u : 1-u;
    const x = pack.x+u*pack.width;
    const entry = Math.pow(clamp(1-directionalU/0.07,0,1),2);
    const exit = Math.pow(clamp(1-(1-directionalU)/0.07,0,1),2);
    const inset = direction*(Math.min(depth*0.22,pack.width*0.025)*entry
      -Math.min(depth*0.14,pack.width*0.014)*exit);
    const rearX = x+inset;
    const frontY = mouthYAtX(frontPoints,x);
    const rearPoint = { x, y: mouthYAtX(backPoints,rearX) };
    const rearY = rearPoint.y-cavityDepthAt(rearPoint,pack,depth,direction,exponent);
    for (let row = 0; row < MOUTH_WALL_ROWS; row += 1) {
      const v = row/(MOUTH_WALL_ROWS-1);
      // The rear sheet turns outward toward the front silhouette. A cubic
      // side return replaces the former vertical, cut-off end of the strip.
      const turn = 1-Math.pow(1-v,3);
      vertices.push(valid ? {
        x: rearX+(x-rearX)*turn, y: rearY+(frontY-rearY)*v,
      } : { x: pack.x, y: pack.y });
      textures.push({ x: directionalU, y: v });
    }
  }
  return { vertices, textures };
}

// Skia's triangle silhouette does not antialias like a Path. Draw a small
// overscan behind a shared cubic clip so metallic edges stay clean at 1x too.
export function expandMouthMesh(vertices: readonly SkPoint[], rows: number, amount: number) {
  'worklet';
  const columns = Math.floor(vertices.length/rows);
  return vertices.map((point,index) => {
    const column = Math.floor(index/rows);
    const row = index%rows;
    return { x: point.x+(column===0 ? -amount : column===columns-1 ? amount : 0),
      y: point.y+(row===0 ? -amount : row===rows-1 ? amount : 0) };
  });
}

export function makeMouthMeshOutline(vertices: readonly SkPoint[], rows: number, tipRadius = 0) {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  const columns = Math.floor(vertices.length/rows);
  if (columns < 2) return builder.build();
  const top = Array.from({ length: columns }, (_,i) => vertices[i*rows]);
  const bottom = Array.from({ length: columns }, (_,i) => vertices[i*rows+rows-1]);
  builder.moveTo(top[0].x,top[0].y);
  appendCubicSpline(builder,top);
  const rightTop = top[columns-1];
  const rightBottom = bottom[columns-1];
  if (tipRadius > 0) {
    builder.cubicTo(rightTop.x+tipRadius,rightTop.y,
      rightBottom.x+tipRadius,rightBottom.y,rightBottom.x,rightBottom.y);
  } else {
    builder.cubicTo(rightBottom.x, rightTop.y+(rightBottom.y-rightTop.y)/3,
      rightBottom.x, rightTop.y+(rightBottom.y-rightTop.y)*2/3,
      rightBottom.x,rightBottom.y);
  }
  appendCubicSpline(builder,[...bottom].reverse());
  const leftTop = top[0];
  const leftBottom = bottom[0];
  if (tipRadius > 0) {
    builder.cubicTo(leftBottom.x-tipRadius,leftBottom.y,
      leftTop.x-tipRadius,leftTop.y,leftTop.x,leftTop.y);
  } else {
    builder.cubicTo(leftBottom.x,leftTop.y+(leftBottom.y-leftTop.y)*2/3,
      leftBottom.x,leftTop.y+(leftBottom.y-leftTop.y)/3,leftTop.x,leftTop.y);
  }
  builder.close();
  return builder.build();
}

const BODY_ROWS = [0, 0.025, 0.06, 0.12, 0.22, 0.38, 0.6, 0.8, 1];
export const BODY_MESH_INDICES = Array.from(
  { length: (MESH_COLUMNS - 1) * (BODY_ROWS.length - 1) }, (_, index) => {
    const column = Math.floor(index / (BODY_ROWS.length - 1));
    const row = index % (BODY_ROWS.length - 1);
    const a = column * BODY_ROWS.length + row;
    const b = a + BODY_ROWS.length;
    return [a, a + 1, b, b, a + 1, b + 1];
  },
).flat();

export function makeBodyMaterialMesh(
  points: readonly Point[], pack: Rect, sourceTop: number,
  warp: number, textureOffset: number,
) {
  'worklet';
  const vertices: SkPoint[] = [];
  const textures: SkPoint[] = [];
  for (let col = 0; col < MESH_COLUMNS; col += 1) {
    const x = pack.x + col / (MESH_COLUMNS - 1) * pack.width;
    const top = points.length > 1 ? yAtX(points, x) : sourceTop;
    for (const v of BODY_ROWS) {
      const y = top + (pack.y + pack.height - top) * v;
      // Only substrate below the manufactured seal belongs to the opened
      // front panel. Warp its UVs onto the user's curved cut; never expose the
      // baked straight magenta seal as a second, intersecting mouth edge.
      const delta = sourceTop - top - textureOffset;
      const bodyHeight = Math.max(pack.y + pack.height - top, 0.0001);
      const reach = Math.min(bodyHeight, Math.max(pack.height*0.11, Math.max(0,delta)*3.1));
      // Confine seal removal to the first few rows. Extending it over the
      // whole front panel bends the artwork as the mouth relaxes.
      // The reach/derivative bound also keeps texture rows in their order.
      const power = delta > 0 && reach < delta*3.1 ? 1 : 3;
      const local = clamp(1-(y-top)/reach, 0, 1);
      const offset = delta * Math.pow(local, power) * warp;
      vertices.push({ x, y });
      textures.push({ x, y: y + textureOffset + offset });
    }
  }
  return { vertices, textures };
}

export function makeCavityFoilBand(
  points: readonly Point[],
  pack: Rect,
  depth: number,
  direction: 1 | -1,
  exponent: number,
): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  if (points.length < 2) return builder.build();

  const front = [...points];
  const inner = points.map((point, index) => {
    const u = clamp(
      (point.x - pack.x) / Math.max(pack.width, 0.0001),
      0,
      1,
    );
    const directionalU = direction === 1 ? u : 1 - u;
    // The exposed inner foil occupies only a hairline at the tear entry, then
    // fans out into the violet lower wall seen immediately before the cuff.
    const foilFraction = 0.18 + 0.44 * Math.pow(directionalU, 4);
    const normal = normalAt(points, index);
    const foilDepth =
      cavityDepthAt(point, pack, depth, direction, exponent) * foilFraction;
    const isEntry = direction === 1 ? index === 0 : index === points.length - 1;
    return {
      x: isEntry ? point.x : point.x + normal.x * foilDepth,
      y: isEntry
        ? point.y - Math.abs(foilDepth) * 0.65
        : point.y + normal.y * foilDepth,
    };
  });
  const lastIndex = points.length - 1;

  builder.moveTo(front[0].x, front[0].y);
  appendCubicSpline(builder, front);

  const rightNormal = normalAt(points, lastIndex);
  const rightDelta = {
    x: front[lastIndex].x - front[lastIndex - 1].x,
    y: front[lastIndex].y - front[lastIndex - 1].y,
  };
  const rightLength = Math.max(
    Math.sqrt(rightDelta.x * rightDelta.x + rightDelta.y * rightDelta.y),
    0.0001,
  );
  const rightDepth = Math.sqrt(
    Math.pow(inner[lastIndex].x - front[lastIndex].x, 2) +
      Math.pow(inner[lastIndex].y - front[lastIndex].y, 2),
  );
  appendRoundedSide(
    builder,
    front[lastIndex],
    inner[lastIndex],
    { x: rightDelta.x / rightLength, y: rightDelta.y / rightLength },
    rightNormal,
    rightDepth,
    false,
  );

  appendCubicSpline(builder, [...inner].reverse());

  const leftNormal = normalAt(points, 0);
  const leftDelta = {
    x: front[0].x - front[1].x,
    y: front[0].y - front[1].y,
  };
  const leftLength = Math.max(
    Math.sqrt(leftDelta.x * leftDelta.x + leftDelta.y * leftDelta.y),
    0.0001,
  );
  const leftDepth = Math.sqrt(
    Math.pow(inner[0].x - front[0].x, 2) +
      Math.pow(inner[0].y - front[0].y, 2),
  );
  appendRoundedSide(
    builder,
    inner[0],
    front[0],
    { x: leftDelta.x / leftLength, y: leftDelta.y / leftLength },
    leftNormal,
    leftDepth,
    true,
  );
  builder.close();
  return builder.build();
}

export function makeCavityShadowBand(
  points: readonly Point[],
  pack: Rect,
  depth: number,
  direction: 1 | -1,
  exponent: number,
): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  if (points.length < 2) return builder.build();

  // Keep the accurate back-edge highlight untouched. The shadow lives only
  // across the lower-middle cavity wall and therefore follows any curved cut.
  const fullDepth = offsetCavityPoints(
    points,
    pack,
    depth,
    direction,
    1,
    exponent,
  );
  const near = points.map((point, index) => {
    const u = clamp(
      (point.x - pack.x) / Math.max(pack.width, 0.0001),
      0,
      1,
    );
    const directionalU = direction === 1 ? u : 1 - u;
    let fraction = 0.12;
    if (directionalU >= 0.25 && directionalU < 0.43) {
      const t = clamp((directionalU - 0.25) / 0.18, 0, 1);
      fraction = 0.12 + 0.16 * t * t * (3 - 2 * t);
    } else if (directionalU >= 0.43 && directionalU < 0.6) {
      fraction = 0.28;
    } else if (directionalU >= 0.6 && directionalU < 0.72) {
      const t = clamp((directionalU - 0.6) / 0.12, 0, 1);
      fraction = 0.28 - 0.16 * t * t * (3 - 2 * t);
    }
    const full = fullDepth[index];
    return {
      x: point.x + (full.x - point.x) * fraction,
      y: point.y + (full.y - point.y) * fraction,
    };
  });
  const far = points.map((point, index) => {
    const u = clamp(
      (point.x - pack.x) / Math.max(pack.width, 0.0001),
      0,
      1,
    );
    const directionalU = direction === 1 ? u : 1 - u;
    const t = clamp((directionalU - 0.62) / 0.1, 0, 1);
    const fraction = 0.86 + 0.14 * t * t * (3 - 2 * t);
    const full = fullDepth[index];
    return {
      x: point.x + (full.x - point.x) * fraction,
      y: point.y + (full.y - point.y) * fraction,
    };
  });
  const lastIndex = points.length - 1;

  builder.moveTo(near[0].x, near[0].y);
  appendCubicSpline(builder, near);

  const rightDelta = {
    x: near[lastIndex].x - near[lastIndex - 1].x,
    y: near[lastIndex].y - near[lastIndex - 1].y,
  };
  const rightLength = Math.max(
    Math.sqrt(rightDelta.x * rightDelta.x + rightDelta.y * rightDelta.y),
    0.0001,
  );
  const rightDepth = Math.sqrt(
    Math.pow(far[lastIndex].x - near[lastIndex].x, 2) +
      Math.pow(far[lastIndex].y - near[lastIndex].y, 2),
  );
  appendRoundedSide(
    builder,
    near[lastIndex],
    far[lastIndex],
    { x: rightDelta.x / rightLength, y: rightDelta.y / rightLength },
    normalAt(points, lastIndex),
    rightDepth,
    false,
  );

  appendCubicSpline(builder, [...far].reverse());

  const leftDelta = {
    x: near[0].x - near[1].x,
    y: near[0].y - near[1].y,
  };
  const leftLength = Math.max(
    Math.sqrt(leftDelta.x * leftDelta.x + leftDelta.y * leftDelta.y),
    0.0001,
  );
  const leftDepth = Math.sqrt(
    Math.pow(far[0].x - near[0].x, 2) +
      Math.pow(far[0].y - near[0].y, 2),
  );
  appendRoundedSide(
    builder,
    far[0],
    near[0],
    { x: leftDelta.x / leftLength, y: leftDelta.y / leftLength },
    normalAt(points, 0),
    leftDepth,
    true,
  );
  builder.close();
  return builder.build();
}

export function makeCavityBottomMask(
  points: readonly Point[],
  pack: Rect,
): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  if (points.length < 2) return builder.build();
  builder.moveTo(points[0].x, points[0].y);
  appendCubicSpline(builder, points);
  builder.lineTo(pack.x + pack.width, pack.y + pack.height);
  builder.lineTo(pack.x, pack.y + pack.height);
  builder.close();
  return builder.build();
}

export function makeEntryBodyWedge(
  points: readonly Point[],
  pack: Rect,
  direction: 1 | -1,
): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  if (points.length < 2) return builder.build();

  const entry = direction === 1 ? points[0] : points[points.length - 1];
  const inward = direction;
  builder.moveTo(
    entry.x + inward * pack.width * 0.003,
    entry.y + pack.width * 0.008,
  );
  builder.cubicTo(
    entry.x + inward * pack.width * 0.007,
    entry.y + pack.width * 0.035,
    entry.x + inward * pack.width * 0.014,
    entry.y + pack.width * 0.085,
    entry.x + inward * pack.width * 0.022,
    entry.y + pack.width * 0.13,
  );
  builder.lineTo(
    entry.x + inward * pack.width * 0.075,
    entry.y + pack.width * 0.13,
  );
  builder.lineTo(
    entry.x + inward * pack.width * 0.075,
    entry.y + pack.width * 0.008,
  );
  builder.close();
  return builder.build();
}

export function makeFrontLipShade(
  points: readonly Point[],
  pack: Rect,
  depth: number,
  direction: 1 | -1,
  exitLift = 0,
  middleLift = 0,
): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  if (points.length < 2) return builder.build();
  const front = liftFrontLipPoints(
    points,
    pack,
    exitLift,
    direction,
    middleLift,
  );
  const lower = front.map((point, index) => {
    // Offset every nested layer from the same physical cut normal. Recomputing
    // the normal after the terminal lift rotates it at the exit and pushes the
    // deeper bands outward into a disconnected-looking purple sidewall.
    const normal = normalAt(points, index);
    const pointDepth = frontLipDepthAt(point, pack, depth, direction);
    const isEntry = direction === 1 ? index === 0 : index === points.length - 1;
    return {
      // All layers share the cut nose, but deeper layers begin progressively
      // farther inside the packet. This gives a sealed asymmetric taper with
      // no detached one-pixel corners for either swipe direction.
      x: isEntry
        ? point.x + direction * pointDepth * 0.25
        : point.x - normal.x * pointDepth,
      y: isEntry
        ? point.y + pointDepth * 0.82
        : point.y - normal.y * pointDepth,
    };
  });
  builder.moveTo(front[0].x, front[0].y);
  appendCubicSpline(builder, front);
  const lastIndex = points.length - 1;
  const rightNormal = normalAt(points, lastIndex);
  const rightDelta = {
    x: front[lastIndex].x - front[lastIndex - 1].x,
    y: front[lastIndex].y - front[lastIndex - 1].y,
  };
  const rightLength = Math.max(
    Math.sqrt(rightDelta.x * rightDelta.x + rightDelta.y * rightDelta.y),
    0.0001,
  );
  appendFrontLipSide(
    builder,
    front[lastIndex],
    lower[lastIndex],
    { x: rightDelta.x / rightLength, y: rightDelta.y / rightLength },
    rightNormal,
    Math.sqrt(
      Math.pow(lower[lastIndex].x - front[lastIndex].x, 2) +
        Math.pow(lower[lastIndex].y - front[lastIndex].y, 2),
    ),
    false,
  );
  appendCubicSpline(builder, [...lower].reverse());
  const leftNormal = normalAt(points, 0);
  const leftDelta = {
    x: front[0].x - front[1].x,
    y: front[0].y - front[1].y,
  };
  const leftLength = Math.max(
    Math.sqrt(leftDelta.x * leftDelta.x + leftDelta.y * leftDelta.y),
    0.0001,
  );
  appendFrontLipSide(
    builder,
    lower[0],
    front[0],
    { x: leftDelta.x / leftLength, y: leftDelta.y / leftLength },
    leftNormal,
    Math.sqrt(
      Math.pow(lower[0].x - front[0].x, 2) +
        Math.pow(lower[0].y - front[0].y, 2),
    ),
    true,
  );
  builder.close();
  return builder.build();
}

export function makeMouthExitCap(
  points: readonly Point[],
  pack: Rect,
  depth: number,
  lipDepth: number,
  direction: 1 | -1,
  exponent: number,
  frontLift = 0,
): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  if (points.length < 2) return builder.build();
  const exitIndex = direction === 1 ? points.length - 1 : 0;
  const adjacentIndex = direction === 1 ? exitIndex - 1 : 1;
  const lipPoints = liftFrontLipPoints(points, pack, frontLift, direction);
  const originalFront = points[exitIndex];
  const front = lipPoints[exitIndex];
  const adjacent = lipPoints[adjacentIndex];
  const baseNormal = normalAt(points, exitIndex);
  const pointDepth = cavityDepthAt(
    originalFront,
    pack,
    depth,
    direction,
    exponent,
  );
  const pointLipDepth = frontLipDepthAt(front, pack, lipDepth, direction);
  const back = {
    x: originalFront.x + baseNormal.x * pointDepth,
    y: originalFront.y + baseNormal.y * pointDepth,
  };
  const lower = {
    x: front.x - baseNormal.x * pointLipDepth,
    y: front.y - baseNormal.y * pointLipDepth,
  };
  const tangentX = front.x - adjacent.x;
  const tangentY = front.y - adjacent.y;
  const tangentLength = Math.max(
    Math.sqrt(tangentX * tangentX + tangentY * tangentY),
    0.0001,
  );
  const outward = { x: tangentX / tangentLength, y: tangentY / tangentLength };
  const totalSpan = Math.sqrt(
    Math.pow(back.x - lower.x, 2) + Math.pow(back.y - lower.y, 2),
  );
  const bulge = Math.min(totalSpan * 0.13, pack.width * 0.01);
  const side = {
    x: front.x + outward.x * bulge,
    y: front.y + outward.y * bulge,
  };
  // Only the rolled outer skin should overlap the mouth. A deep inset turns
  // this into a pasted-on purple block and hides the laminate stripes.
  const inset = Math.min(totalSpan * 0.18, pack.width * 0.02);
  const innerLower = {
    x: lower.x - outward.x * inset * 0.18,
    y: lower.y - outward.y * inset * 0.18,
  };
  const innerBack = {
    x: back.x - outward.x * inset * 0.9,
    y: back.y - outward.y * inset * 0.9,
  };

  builder.moveTo(innerLower.x, innerLower.y);
  builder.lineTo(lower.x, lower.y);
  appendExitOuterCurve(
    builder,
    lower,
    side,
    back,
    outward,
    baseNormal,
    bulge,
    pointLipDepth,
    pointDepth,
  );
  builder.lineTo(innerBack.x, innerBack.y);
  builder.quadTo(
    (innerBack.x + innerLower.x) / 2 - outward.x * inset * 0.18,
    (innerBack.y + innerLower.y) / 2 - outward.y * inset * 0.18,
    innerLower.x,
    innerLower.y,
  );
  builder.close();
  return builder.build();
}

export function makeMouthExitRim(
  points: readonly Point[],
  pack: Rect,
  depth: number,
  lipDepth: number,
  direction: 1 | -1,
  exponent: number,
  frontLift = 0,
): SkPath {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  if (points.length < 2) return builder.build();
  const exitIndex = direction === 1 ? points.length - 1 : 0;
  const adjacentIndex = direction === 1 ? exitIndex - 1 : 1;
  const lipPoints = liftFrontLipPoints(points, pack, frontLift, direction);
  const originalFront = points[exitIndex];
  const front = lipPoints[exitIndex];
  const adjacent = lipPoints[adjacentIndex];
  const baseNormal = normalAt(points, exitIndex);
  const pointDepth = cavityDepthAt(
    originalFront,
    pack,
    depth,
    direction,
    exponent,
  );
  const pointLipDepth = frontLipDepthAt(front, pack, lipDepth, direction);
  const back = {
    x: originalFront.x + baseNormal.x * pointDepth,
    y: originalFront.y + baseNormal.y * pointDepth,
  };
  const lower = {
    x: front.x - baseNormal.x * pointLipDepth,
    y: front.y - baseNormal.y * pointLipDepth,
  };
  const tangentX = front.x - adjacent.x;
  const tangentY = front.y - adjacent.y;
  const tangentLength = Math.max(
    Math.sqrt(tangentX * tangentX + tangentY * tangentY),
    0.0001,
  );
  const outward = { x: tangentX / tangentLength, y: tangentY / tangentLength };
  const totalSpan = Math.sqrt(
    Math.pow(back.x - lower.x, 2) + Math.pow(back.y - lower.y, 2),
  );
  const bulge = Math.min(totalSpan * 0.13, pack.width * 0.01);
  const side = {
    x: front.x + outward.x * bulge,
    y: front.y + outward.y * bulge,
  };

  builder.moveTo(lower.x, lower.y);
  appendExitOuterCurve(
    builder,
    lower,
    side,
    back,
    outward,
    baseNormal,
    bulge,
    pointLipDepth,
    pointDepth,
  );
  return builder.build();
}

export const FOIL_MESH_INDICES = Array.from(
  { length: (MESH_COLUMNS - 1) * 6 },
  (_, index) => {
    const cell = Math.floor(index / 6);
    const offset = index % 6;
    const topLeft = cell * 2;
    const bottomLeft = topLeft + 1;
    const topRight = topLeft + 2;
    const bottomRight = topLeft + 3;
    return [topLeft, bottomLeft, topRight, topRight, bottomLeft, bottomRight][offset];
  },
);

export const PREVIEW_FOIL_MESH_INDICES = Array.from(
  { length: (PREVIEW_MESH_COLUMNS - 1) * (PREVIEW_MESH_ROWS - 1) * 6 },
  (_, index) => {
    const triangleOffset = index % 6;
    const cell = Math.floor(index / 6);
    const column = Math.floor(cell / (PREVIEW_MESH_ROWS - 1));
    const row = cell % (PREVIEW_MESH_ROWS - 1);
    const topLeft = column * PREVIEW_MESH_ROWS + row;
    const bottomLeft = topLeft + 1;
    const topRight = topLeft + PREVIEW_MESH_ROWS;
    const bottomRight = topRight + 1;
    return [topLeft, bottomLeft, topRight, topRight, bottomLeft, bottomRight][
      triangleOffset
    ];
  },
);

/** Where the finger of the reference video is, from 0 at touch-down to 1 at its last point. */
export function referenceTracePoint(
  progress: number,
  width: number,
  height: number,
): Point {
  'worklet';
  const p0 = { x: 0.125 * width, y: 0.568 * height };
  const p1 = { x: 0.31 * width, y: 0.576 * height };
  const p2 = { x: 0.58 * width, y: 0.565 * height };
  const p3 = { x: 0.765 * width, y: 0.544 * height };
  const t = clamp(progress, 0, 1);
  const inverse = 1 - t;
  return {
    x:
      inverse * inverse * inverse * p0.x +
      3 * inverse * inverse * t * p1.x +
      3 * inverse * t * t * p2.x +
      t * t * t * p3.x,
    y:
      inverse * inverse * inverse * p0.y +
      3 * inverse * inverse * t * p1.y +
      3 * inverse * t * t * p2.y +
      t * t * t * p3.y,
  };
}
