import { FOLDER, ROW } from './burst-contract';

export type Box = { x: number; y: number; w: number; h: number };
export type FolderPlacement = { cx: number; cy: number; scale: number };
export type BurstLayout = {
  width: number; height: number; headerTop: number; listTop: number; dockTop: number; n: number;
  rest: FolderPlacement; docked: FolderPlacement;
  header: { disc: { cx: number; cy: number; r: number }; title: Box; subtitle: Box };
  label: { title: Box; subtitle: Box };
  slots: Box[];
};

export function burstLayout(width: number, height: number, insetTop: number, insetBottom: number): BurstLayout {
  'worklet';
  const headerTop = Math.max(insetTop, 20) + 12;
  const listTop = headerTop + 56;
  const bottom = height - Math.max(insetBottom, 16) - 24;
  const dockTop = bottom - FOLDER.h;
  const n = Math.max(5, Math.min(9, Math.floor((dockTop - 32 - listTop + 8) / 60)));
  const restY = Math.round(height * 0.50);
  const slots: Box[] = [];
  for (let i = 0; i < n; i++) slots.push({ x: ROW.margin, y: listTop + 60 * i, w: width - 48, h: ROW.h });
  return {
    width, height, headerTop, listTop, dockTop, n, slots,
    rest: { cx: width / 2, cy: restY, scale: FOLDER.restScale },
    docked: { cx: width / 2, cy: bottom - FOLDER.h / 2, scale: 1 },
    header: {
      disc: { cx: 44, cy: headerTop + 20, r: 20 },
      title: { x: 76, y: headerTop, w: width - 100, h: 24 },
      subtitle: { x: 76, y: headerTop + 24, w: width - 100, h: 16 },
    },
    label: {
      title: { x: 24, y: restY - 154, w: width - 48, h: 24 },
      subtitle: { x: 24, y: restY - 126, w: width - 48, h: 16 },
    },
  };
}
