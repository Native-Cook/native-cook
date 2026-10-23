import type { SkPoint } from '@shopify/react-native-skia';

import { PREVIEW_MESH_COLUMNS, PREVIEW_MESH_ROWS } from './geometry';
import { FOLD_ALPHA_TOP, FOLD_PROFILES } from './fold-profile';
import { clamp, interpolateKeyframes, type Rect } from './model';

/** Gesture-driven projection of the measured curved fold; the cut stays fixed. */
export function deformPreviewFoil(
  base: readonly SkPoint[],
  pack: Rect,
  progress: number,
  direction: 1 | -1,
): SkPoint[] {
  'worklet';
  if (base.length === 0) return [];
  const p = clamp(progress, 0, 1);
  if (p <= 0.06) return base.map((point) => ({ ...point }));
  let upper = 1;
  while (upper < FOLD_PROFILES.length-1 && FOLD_PROFILES[upper].progress < p) upper++;
  const a = FOLD_PROFILES[upper-1];
  const b = FOLD_PROFILES[upper];
  const mix = clamp((p-a.progress)/(b.progress-a.progress), 0, 1);
  const tipShift = (a.tipShift+(b.tipShift-a.tipShift)*mix)*pack.width;
  const shear = pack.width*interpolateKeyframes(p,
    [0.06, 0.29, 0.44, 0.52, 0.72, 0.95, 1],
    [0, 0.003, 0.0062, 0.0062, 0.0101, 0.0148, 0.0148]);
  const positions: SkPoint[] = [];
  const exponent = 1.7;
  const firstFalloff = Math.pow(1-1/(PREVIEW_MESH_ROWS-1), exponent);
  for (let columnStart = 0; columnStart < base.length; columnStart += PREVIEW_MESH_ROWS) {
    const column = columnStart/PREVIEW_MESH_ROWS;
    const columnEnd = Math.min(columnStart+PREVIEW_MESH_ROWS, base.length);
    const profileColumn = direction === 1 ? column : PREVIEW_MESH_COLUMNS-1-column;
    const bottom = base[columnStart+PREVIEW_MESH_ROWS-1];
    const height = bottom ? Math.max(bottom.y-pack.y, 0) : 0;
    if (!bottom || profileColumn < 0 || profileColumn >= PREVIEW_MESH_COLUMNS || height < 0.001) {
      for (let index = columnStart; index < columnEnd; index++) {
        positions.push({ ...base[index] });
      }
      continue;
    }

    // The depth solution depends on the column's cut height, not its row.
    const edge = (a.edge[profileColumn]+(b.edge[profileColumn]-a.edge[profileColumn])*mix)*pack.width;
    const firstHeight = height/(PREVIEW_MESH_ROWS-1);
    const alphaFraction = clamp(FOLD_ALPHA_TOP[column]*pack.height/firstHeight, 0, 1);
    const minRowHeight = height*0.0005;
    // Solve for the underlying vertex: the texture's rounded alpha corner
    // lies inside the first row, while measurements describe the visible edge.
    let depth = (edge-alphaFraction*firstHeight)
      /(1+alphaFraction*(firstFalloff-1));
    if (firstHeight+depth*(firstFalloff-1) < minRowHeight) {
      depth = edge-alphaFraction*minRowHeight;
    }
    depth = clamp(depth, 0, height*0.92);
    const projectedHeight = Math.max(height-depth, 0.001);

    for (let index = columnStart; index < columnEnd; index++) {
      const point = base[index];
      const row = index-columnStart;
      if (row === PREVIEW_MESH_ROWS-1) {
        positions.push({ ...point });
        continue;
      }
      const u = clamp((point.x-pack.x)/pack.width, 0, 1);
      const fromStart = direction === 1 ? u : 1-u;
      const v = row/(PREVIEW_MESH_ROWS-1);
      const desiredY = point.y+depth*Math.pow(1-v, exponent);
      const previousY = row === 0 ? pack.y+depth : positions[index-1].y+minRowHeight;
      const y = Math.min(bottom.y, Math.max(previousY, desiredY));
      const projectedV = clamp((y-pack.y-depth)/projectedHeight, 0, 1);
      // The curled side stays almost vertical; return to the fixed tear edge
      // close to the seam instead of drawing the side inward along its full height.
      const curveShift = tipShift*Math.pow(clamp(1-fromStart/0.6, 0, 1), 2)
        *(1-Math.pow(projectedV, 3));
      const printShear = -shear*Math.exp(-Math.pow((fromStart-0.25)/0.085, 2))
        *Math.sin(Math.PI*projectedV);
      positions.push({
        x: point.x+direction*(curveShift+printShear),
        y,
      });
    }
  }
  return positions;
}
