// Bell Permission Primer — the WebGPU layer's frame (FrameU in gpu/bpp-wgsl.ts) from the block's state.

import { REST } from './bpp-motion';
import { D, type Layout } from './bpp-theme';

export type Rect = [number, number, number, number];
export type Geo = { px: number; py: number; unit: number; island: Rect; slab: Rect };

/** the Dynamic Island of the iPhone screens that have one (125 × 36 pt, 14 pt from the top), and the
 * notification it opens into. A phone without an island (a notch or a plain status bar, a top inset
 * under 51 pt) gets the same capsule just under its status bar, so it never sits on the clock. */
export function makeGeo(L: Layout, safeTop: number): Geo {
  const y = safeTop >= 51 ? D.island.y : safeTop + 6;
  const island: Rect = [L.w / 2 - D.island.w / 2, y, D.island.w, D.island.h];
  return {
    px: D.disc.x * L.sx,
    py: L.Y(D.bell.pivotY),
    unit: D.bell.unit * L.sx,
    island,
    slab: [D.banner.x * L.sx, y, L.w - 2 * D.banner.x * L.sx, D.banner.h],
  };
}

export type BellState = { swing: number; clap: number; suck: number; expand: number; liftX: number; liftY: number; bob: number; rings: number[]; t: number };

/** 32 floats: view, hero, pose, lift, island, slab, rings, look */
export function makeFrame(g: Geo, w: number, h: number, pr: number, s: BellState) {
  'worklet';
  return [
    w, h, pr, s.t,
    g.px, g.py, g.unit, s.bob * g.unit,
    s.swing, s.clap, s.suck, s.expand,
    s.liftX, s.liftY, 0, 0,
    g.island[0], g.island[1], g.island[2], g.island[3],
    g.slab[0], g.slab[1], g.slab[2], g.slab[3],
    s.rings[0], s.rings[1], s.rings[2], 0,
    REST.yaw, REST.pitch, 0, 0,
  ];
}
