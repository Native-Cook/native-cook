// The orbs, their comet tails and the sparks: one additive Skia Atlas draw per depth pass.
// Every sprite of the contract owns three atlas entries: the bead itself and two tail pieces
// that only show while it travels (an RSXform scales uniformly, so a long tail is two overlapping comets).
import {
  Atlas, FilterMode, MipmapMode, useColorBuffer, useImage, useRectBuffer, useRSXformBuffer,
} from '@shopify/react-native-skia';

import { MAX_SPRITES, SPRITE_STRIDE, type OrbsLayerProps } from './gem-contract';
import { ORB_TILES, orbAlpha, orbTileIndex } from './orb-atlas';

/** Atlas entries per sprite: bead, near tail, far tail. */
const PIECES = 3;
const COUNT = MAX_SPRITES * PIECES;
/** A sprite's `size` is its core diameter; the atlas core is a quarter of a 128 px tile. */
const CORE_PIXELS = 32;
/** How much of the comet tile's length lies behind its head, in tile pixels. */
const TAIL_PIXELS = 210;
/** Above this stretch the sprite is travelling and grows a tail. */
const COMET_FROM = 1.15;

/** Opacity of one piece of one sprite in this pass; 0 hides it. */
function pieceAlpha(data: number[], slot: number, piece: number, front: boolean): number {
  'worklet';
  const alpha = orbAlpha(data, slot, front);
  if (alpha === 0 || piece === 0) return alpha;
  const stretch = data[slot * SPRITE_STRIDE + 6] ?? 1;
  if (stretch <= COMET_FROM) return 0;
  if (piece === 1) return alpha * 0.85 * Math.min(1, (stretch - 1) / 1.2);
  return alpha * 0.6 * Math.max(0, Math.min(1, (stretch - 2.2) / 2));
}

export function OrbsLayer({ sprites, pass }: OrbsLayerProps) {
  const image = useImage(require('./assets/orb-atlas.png'));
  const front = pass === 'front';

  const rects = useRectBuffer(COUNT, (rect, i) => {
    'worklet';
    const data = sprites.get();
    const slot = Math.floor(i / PIECES);
    const piece = i % PIECES;
    const offset = slot * SPRITE_STRIDE;
    const tint = Math.max(0, Math.min(2, Math.round(data[offset + 5] ?? 0)));
    const tile = piece === 0 ? ORB_TILES[orbTileIndex(data[offset + 2] ?? 0, tint, 1)] : ORB_TILES[tint * 4 + 3];
    rect.setXYWH(tile.x, tile.y, tile.width, tile.height);
  });

  const transforms = useRSXformBuffer(COUNT, (transform, i) => {
    'worklet';
    const data = sprites.get();
    const slot = Math.floor(i / PIECES);
    const piece = i % PIECES;
    const offset = slot * SPRITE_STRIDE;
    if (pieceAlpha(data, slot, piece, front) === 0) {
      transform.set(0, 0, 0, 0);
      return;
    }
    const x = data[offset];
    const y = data[offset + 1];
    const scale = Math.max(0, data[offset + 3]) / CORE_PIXELS;
    if (piece === 0) {
      transform.set(scale, 0, x - scale * 64, y - scale * 64);
      return;
    }
    const stretch = data[offset + 6];
    const angle = data[offset + 7];
    const cosine = Math.cos(angle);
    const sine = Math.sin(angle);
    // A faster orb drags a longer (and, the scale being uniform, slightly fuller) tail.
    const tail = scale * Math.max(0.7, Math.min(1.3, 0.55 + 0.17 * stretch)) * (piece === 1 ? 1 : 0.86);
    const back = piece === 1 ? 0 : 0.5 * TAIL_PIXELS * tail;
    const headX = x - cosine * back;
    const headY = y - sine * back;
    const sc = tail * cosine;
    const ss = tail * sine;
    transform.set(sc, ss, headX - (sc * TAIL_PIXELS - ss * 64), headY - (ss * TAIL_PIXELS + sc * 64));
  });

  const colors = useColorBuffer(COUNT, (color, i) => {
    'worklet';
    const alpha = pieceAlpha(sprites.get(), Math.floor(i / PIECES), i % PIECES, front);
    color[0] = 1;
    color[1] = 1;
    color[2] = 1;
    color[3] = alpha;
  });

  if (!image) return null;
  return (
    <Atlas
      image={image}
      sprites={rects}
      transforms={transforms}
      colors={colors}
      colorBlendMode="modulate"
      blendMode="plus"
      sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.None }}
    />
  );
}
