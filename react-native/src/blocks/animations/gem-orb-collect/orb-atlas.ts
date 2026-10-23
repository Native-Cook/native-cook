// Atlas metadata: where each tile sits in assets/orb-atlas.png and which tile a sprite uses.
import { SPRITE_STRIDE } from './gem-contract';

export type OrbTile = {
  x: number; y: number; width: number; height: number;
  /** Bright core diameter divided by tile height; size is always that diameter. */
  coreFraction: number;
  /** Light centre within the tile, normalized. Comet heads point toward +x. */
  headX: number; headY: number;
};
export const ATLAS_WIDTH = 768;
export const ATLAS_HEIGHT = 512;
// Each tint owns a row: sharp, soft, bokeh, then a double-width comet.
export const ORB_TILES: readonly OrbTile[] = [
  { x: 0, y: 0, width: 128, height: 128, coreFraction: 0.25, headX: 0.5, headY: 0.5 },
  { x: 128, y: 0, width: 128, height: 128, coreFraction: 0.25, headX: 0.5, headY: 0.5 },
  { x: 256, y: 0, width: 128, height: 128, coreFraction: 0.25, headX: 0.5, headY: 0.5 },
  { x: 384, y: 0, width: 256, height: 128, coreFraction: 0.25, headX: 0.82, headY: 0.5 },
  { x: 0, y: 128, width: 128, height: 128, coreFraction: 0.25, headX: 0.5, headY: 0.5 },
  { x: 128, y: 128, width: 128, height: 128, coreFraction: 0.25, headX: 0.5, headY: 0.5 },
  { x: 256, y: 128, width: 128, height: 128, coreFraction: 0.25, headX: 0.5, headY: 0.5 },
  { x: 384, y: 128, width: 256, height: 128, coreFraction: 0.25, headX: 0.82, headY: 0.5 },
  { x: 0, y: 256, width: 128, height: 128, coreFraction: 0.25, headX: 0.5, headY: 0.5 },
  { x: 128, y: 256, width: 128, height: 128, coreFraction: 0.25, headX: 0.5, headY: 0.5 },
  { x: 256, y: 256, width: 128, height: 128, coreFraction: 0.25, headX: 0.5, headY: 0.5 },
  { x: 384, y: 256, width: 256, height: 128, coreFraction: 0.25, headX: 0.82, headY: 0.5 },
];
export const SPARK_TILE: OrbTile = {
  x: 0, y: 384, width: 128, height: 128, coreFraction: 0.25, headX: 0.5, headY: 0.5,
};

export function orbTileIndex(z: number, tint: number, stretch: number): number {
  'worklet';
  const focus = stretch > 1.15 ? 3 : (z > 1.6 ? 2 : (z < 0 || Math.abs(z) >= 0.6 ? 1 : 0));
  return Math.max(0, Math.min(2, Math.round(tint))) * 4 + focus;
}

export function orbAlpha(data: number[], slot: number, front: boolean): number {
  'worklet';
  const offset = slot * SPRITE_STRIDE;
  const alpha = data[offset + 4] ?? 0;
  const z = data[offset + 2] ?? 0;
  if (alpha <= 0 || (z >= 0) !== front) return 0;
  return Math.min(1, alpha) * (z < 0 ? 0.66 : 1);
}
