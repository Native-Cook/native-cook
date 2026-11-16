import type { Scene } from './tfs-motion';
export type Palette = { base: number[]; shade: number[]; hot: number[]; page: number[]; ring: number[]; ink: number[] };
export type ShaderLayout = { width: number; height: number; scale: number; offsetX: number; offsetY: number };
/** Shared by the app and the offline CanvasKit renderer. */
export function uniformsFor(
  scene: Scene,
  lag: Scene,
  layout: ShaderLayout,
  colors: number[][],
  palette: Palette,
  grain: number,
  outputP3 = 0,
) {
  'worklet';
  const bodies: number[] = [],
    lagBodies: number[] = [],
    flatColors: number[] = [],
    dyes: number[] = [],
    leans: number[] = [];
  for (let i = 0; i < 6; i++) {
    const c = scene.coins[i],
      old = lag.coins[i] ?? c;
    bodies.push(c?.x ?? 0, c?.y ?? 0, c?.r ?? 0, c?.reach ?? 0);
    lagBodies.push(old?.x ?? 0, old?.y ?? 0, old?.r ?? 0, old?.reach ?? 0);
    flatColors.push(...(colors[i] ?? [0, 0, 0]));
    dyes.push(c?.dye ?? 0);
    leans.push(c?.lean ?? 0);
  }
  return {
    viewport: [layout.width, layout.height],
    mapping: [layout.scale, layout.offsetX, layout.offsetY],
    core: [scene.x, scene.y, scene.r, scene.sphere],
    bodies,
    lagBodies,
    colors: flatColors,
    dyes,
    leans,
    meta: [scene.stretch, scene.axis, scene.flood, grain],
    logo: [scene.mark, scene.markSize, scene.cut, scene.ringScale],
    baseColor: palette.base,
    shadeColor: palette.shade,
    hotColor: palette.hot,
    pageColor: palette.page,
    ringColor: palette.ring,
    inkColor: palette.ink,
    count: scene.coins.length,
    outputP3,
  };
}
