import type { SkCanvas, SkImage } from '@shopify/react-native-skia';

import { type Geo, crayonPoses, drawBox, pathPoint } from './crayonArt';
import { LOOP_PICKS, loopSwatch, pickPhase } from './crayonMotion';

/*
 Crayon Box Picker — one frame of the loop (shared by the component, free of React so it can be
 checked offline).
 */

export type Path = { pts: number[]; len: number[] };
export type Art = { desk: SkImage; front: SkImage; crayons: SkImage[] };

/** Which pick is under way at t and its phase. */
export function loopPick(t: number): number[] {
  'worklet';
  for (let k = 0; k < LOOP_PICKS.length; k += 1) {
    const ph = pickPhase(LOOP_PICKS[k], t);
    if (ph[0] !== 0) return [LOOP_PICKS[k].crayon, ph[0], ph[1], ph[2]];
  }
  return [-1, 0, 0, 0];
}

export function drawPick(canvas: SkCanvas, g: Geo, art: Art, path: Path, total: number, crayon: number, phase: number[], swatch: number[], alpha: number) {
  'worklet';
  const pen0 = [path.pts[0], path.pts[1]];
  const penEnd = [path.pts[path.pts.length - 2], path.pts[path.pts.length - 1]];
  const pen = phase[0] === 3 ? pathPoint(path, total * phase[1]) : pen0;
  drawBox(canvas, g, art, crayonPoses(g, crayon, phase, pen0, pen, penEnd), swatch, path, total, alpha);
}

export function drawLoopFrame(canvas: SkCanvas, g: Geo, art: Art, path: Path, t: number, alpha: number) {
  'worklet';
  const p = loopPick(t);
  const total = path.len[path.len.length - 1];
  drawPick(canvas, g, art, path, total, p[0], [p[1], p[2], p[3]], loopSwatch(t), alpha);
}
