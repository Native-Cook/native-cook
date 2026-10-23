import type { SkImage } from '@shopify/react-native-skia';

import { DEFAULT_SUBJECT_BOUNDS } from '../constants';
import type { DispersionViewport, NormalizedPoint, PixelBounds, SubjectBounds } from '../types';

export const canvasStyle = (viewport: DispersionViewport) => ({
  width: viewport.width,
  height: viewport.height,
});

function clamp(value: number, lower: number, upper: number) {
  return Math.min(Math.max(value, lower), upper);
}

function isNormalizedPoint(value: NormalizedPoint): boolean {
  return Number.isFinite(value.x) && Number.isFinite(value.y);
}

function boundsFromContour(
  contourPoints: readonly NormalizedPoint[] | null | undefined
): SubjectBounds | null {
  const points = contourPoints?.filter(isNormalizedPoint);
  if (!points || points.length < 2) {
    return null;
  }

  let minX = points[0].x;
  let maxX = points[0].x;
  let minY = points[0].y;
  let maxY = points[0].y;

  for (const point of points.slice(1)) {
    minX = Math.min(minX, point.x);
    maxX = Math.max(maxX, point.x);
    minY = Math.min(minY, point.y);
    maxY = Math.max(maxY, point.y);
  }

  return {
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY,
  };
}

export function resolveSubjectBounds(
  subjectBounds: SubjectBounds | null | undefined,
  contourPoints: readonly NormalizedPoint[] | null | undefined
): SubjectBounds {
  const candidate =
    subjectBounds &&
    Number.isFinite(subjectBounds.x) &&
    Number.isFinite(subjectBounds.y) &&
    Number.isFinite(subjectBounds.width) &&
    Number.isFinite(subjectBounds.height) &&
    subjectBounds.width > 0 &&
    subjectBounds.height > 0
      ? subjectBounds
      : boundsFromContour(contourPoints) ?? DEFAULT_SUBJECT_BOUNDS;

  const x = clamp(candidate.x, 0, 0.98);
  const y = clamp(candidate.y, 0, 0.98);

  return {
    x,
    y,
    width: clamp(candidate.width, 0.02, 1 - x),
    height: clamp(candidate.height, 0.02, 1 - y),
  };
}

type CoverMapping = Readonly<{
  scale: number;
  offsetX: number;
  offsetY: number;
  sourceWidth: number;
  sourceHeight: number;
}>;

export function makeCoverMapping(frame: SkImage, viewport: DispersionViewport): CoverMapping {
  const sourceWidth = Math.max(frame.width(), 1);
  const sourceHeight = Math.max(frame.height(), 1);
  const scale = Math.max(
    viewport.width / sourceWidth,
    viewport.height / sourceHeight
  );

  return {
    scale,
    offsetX: (viewport.width - sourceWidth * scale) / 2,
    offsetY: (viewport.height - sourceHeight * scale) / 2,
    sourceWidth,
    sourceHeight,
  };
}

function mapPoint(point: NormalizedPoint, mapping: CoverMapping): NormalizedPoint {
  return {
    x: mapping.offsetX + point.x * mapping.sourceWidth * mapping.scale,
    y: mapping.offsetY + point.y * mapping.sourceHeight * mapping.scale,
  };
}

export function mapBounds(bounds: SubjectBounds, mapping: CoverMapping): PixelBounds {
  const topLeft = mapPoint(bounds, mapping);
  const width = bounds.width * mapping.sourceWidth * mapping.scale;
  const height = bounds.height * mapping.sourceHeight * mapping.scale;

  return {
    x: topLeft.x,
    y: topLeft.y,
    width,
    height,
    centerX: topLeft.x + width / 2,
    centerY: topLeft.y + height / 2,
  };
}
