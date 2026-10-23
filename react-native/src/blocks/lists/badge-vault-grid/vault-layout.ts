export type Box = { x: number; y: number; width: number; height: number };
export const halfPoint = (n: number) => { 'worklet'; return Math.round(n * 2) / 2; };

/** Quantise edges independently so three columns still occupy the entire available width. */
export function vaultLayout(width: number, height: number, safeTop: number, safeBottom: number) {
  'worklet';
  const k = Math.min(1, width / 402);
  const compact = height < 760;
  const headerHeight = safeTop + 125;
  const pitch = halfPoint(196 * k);
  const columns: Box[] = [];
  for (let i = 0; i < 3; i++) {
    const x = halfPoint(16 + (width - 32) * i / 3);
    columns.push({ x, y: 0, width: halfPoint(16 + (width - 32) * (i + 1) / 3) - x, height: pitch });
  }
  const footerY = headerHeight + 6 * pitch;
  const footerHeight = 96 + safeBottom;
  const statColumns: Box[] = [];
  for (let i = 0; i < 3; i++) {
    const x = halfPoint(32 + (width - 64) * i / 3);
    statColumns.push({ x, y: compact ? 452 : 632,
      width: halfPoint(32 + (width - 64) * (i + 1) / 3) - x, height: 56 });
  }
  const full = { x: 0, y: 0, width, height };
  return {
    width, height, safeTop, safeBottom, k, compact, pitch, columns, statColumns,
    radius: halfPoint(50 * k), badgeY: halfPoint(64 * k), canvasHeight: halfPoint(136 * k),
    nameY: halfPoint(132 * k), dateY: halfPoint(152 * k), barY: halfPoint(176 * k),
    headerHeight, footerY, footerHeight, contentHeight: footerY + footerHeight,
    scrollEnd: Math.max(0, footerY + footerHeight - height), full,
    header: { x: 24, y: safeTop + 12, width: width - 48, height: 89 },
    eyebrow: { x: 24, y: safeTop + 12, width: width - 48, height: 16 },
    heading: { x: 24, y: safeTop + 32, width: width - 48, height: 41 },
    subtitle: { x: 24, y: safeTop + 81, width: width - 48, height: 20 },
    footer: { x: 0, y: footerY, width, height: footerHeight },
    footerCopy: { x: 24, y: footerY + 24, width: width - 204, height: 48 },
    footerButton: { x: width - 156, y: footerY + 24, width: 132, height: 48 },
    heldX: halfPoint(width / 2), heldY: compact ? 170 : 300, heldR: compact ? 96 : 120,
    titleSize: compact ? 30 : 32,
    close: { x: 16, y: safeTop + 4, width: 36, height: 36 },
    chip: { x: 24, y: compact ? 292 : 456, width: width - 48, height: 28 },
    title: { x: 16, y: compact ? 328 : 496, width: width - 32, height: 38 },
    earned: { x: 24, y: compact ? 368 : 538, width: width - 48, height: 20 },
    blurb: { x: halfPoint((width - 300) / 2), y: compact ? 392 : 566, width: 300, height: 44 },
    stats: { x: 32, y: compact ? 452 : 632, width: width - 64, height: 56 },
    hint: compact ? null : { x: 16, y: 724, width: width - 32, height: 16 },
    share: { x: 24, y: height - safeBottom - 72, width: width - 48, height: 56 },
  };
}
export type VaultLayout = ReturnType<typeof vaultLayout>;

export function cellHome(layout: VaultLayout, index: number, scrollY: number, recede: number) {
  'worklet';
  const col = layout.columns[index % 3];
  const scale = 1 - 0.06 * recede;
  const x = halfPoint(col.x + col.width / 2);
  const rowY = layout.headerHeight + Math.floor(index / 3) * layout.pitch - scrollY;
  return {
    x: layout.width / 2 + (x - layout.width / 2) * scale,
    y: layout.height / 2 + (rowY + layout.badgeY - layout.height / 2) * scale,
    r: layout.radius * scale,
    nameY: layout.height / 2 + (rowY + layout.nameY + 9 - layout.height / 2) * scale,
    scale,
  };
}

export function cellLayout(layout: VaultLayout, index: number) {
  'worklet';
  const col = layout.columns[index % 3];
  const y = layout.headerHeight + Math.floor(index / 3) * layout.pitch;
  return {
    cell: { x: col.x, y, width: col.width, height: layout.pitch },
    canvas: { x: col.x, y, width: col.width, height: layout.canvasHeight },
    name: { x: col.x, y: y + layout.nameY, width: col.width, height: 18 },
    date: { x: col.x, y: y + layout.dateY, width: col.width, height: 16 },
    bar: { x: halfPoint(col.x + (col.width - 72) / 2), y: y + layout.barY, width: 72, height: 4 },
  };
}
