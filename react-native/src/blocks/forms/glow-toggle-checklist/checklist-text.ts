/** Font-independent layout shared by the native canvas and CanvasKit proofs. */
export type InkMetric = {
  id: number; advance: number; x: number; y: number; width: number; height: number;
};
export type InkGlyph = { id: number; pos: { x: number; y: number }; centreX: number };

/** Centres actual ink, never the line box or a trailing tracking space. Runs on font load only. */
export function layoutChecklistText(metrics: InkMetric[], tracking: number, centred: boolean) {
  let cursor = 0; let left = Infinity; let right = -Infinity;
  let top = Infinity; let bottom = -Infinity;
  const glyphs: InkGlyph[] = [];
  for (let i = 0; i < metrics.length; i++) {
    const m = metrics[i];
    left = Math.min(left, cursor + m.x); right = Math.max(right, cursor + m.x + m.width);
    top = Math.min(top, m.y); bottom = Math.max(bottom, m.y + m.height);
    glyphs.push({ id: m.id, pos: { x: cursor, y: 0 }, centreX: cursor + m.x + m.width / 2 });
    cursor += m.advance + tracking;
  }
  const shiftX = centred ? -(left + right) / 2 : -left;
  const baseline = -(top + bottom) / 2;
  for (let i = 0; i < glyphs.length; i++) {
    glyphs[i].pos.x += shiftX; glyphs[i].pos.y = baseline; glyphs[i].centreX += shiftX;
  }
  return { glyphs, width: right - left, height: bottom - top, baseline };
}

export function checklistLetterState(progress: number) {
  'worklet';
  const p = Math.max(0, Math.min(1, progress));
  return { scale: 0.25 + 0.75 * p, opacity: Math.min(1, p / 0.4) };
}
