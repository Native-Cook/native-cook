// Sticker Peel Profile — the stickers' art and the peel, drawn in Skia. Helpers above callers.
//
// A sticker is a silhouette (its die-cut) with a white border and its art inside. Peeling folds a
// corner over along a line: the flat part is clipped to one side of the line, the flap is the corner
// part reflected across the line and drawn as the sticker's pale backing, with a soft shadow under
// it.
import { type SkCanvas, type SkFont, type SkPath, Skia } from '@shopify/react-native-skia';

export const KINDS = ['avatar', 'heart', 'slay', 'fire'] as const;
/** Half sizes (x, y) of each sticker's box, pt. */
export const HALF = [
  [52, 48],
  [27, 25],
  [46, 22],
  [25, 31],
] as const;

const svg = (d: string) => {
  'worklet';
  return Skia.Path.MakeFromSVGString(d) ?? Skia.PathBuilder.Make().build();
};

/** The die-cut silhouette of sticker k, centred on 0, 0. */
export const silhouette = (k: number): SkPath => {
  'worklet';
  if (k === 0) {
    // a loaf with two ears
    const p = Skia.PathBuilder.Make();
    p.addRRect(Skia.RRectXY(Skia.XYWHRect(-48, -36, 96, 82), 30, 30));
    p.moveTo(-40, -18);
    p.lineTo(-30, -46);
    p.lineTo(-12, -30);
    p.close();
    p.moveTo(40, -18);
    p.lineTo(30, -46);
    p.lineTo(12, -30);
    p.close();
    return p.build();
  }
  if (k === 1) return svg('M0 -10 C -9 -24 -28 -20 -26 -2 C -24 10 -10 18 0 24 C 10 18 24 10 26 -2 C 28 -20 9 -24 0 -10 Z');
  if (k === 2) {
    const p = Skia.PathBuilder.Make();
    p.addRRect(Skia.RRectXY(Skia.XYWHRect(-42, -18, 84, 36), 18, 18));
    return p.build();
  }
  return svg('M2 -28 C 10 -16 24 -10 22 8 C 20 22 10 28 0 28 C -12 28 -22 20 -22 6 C -22 -6 -13 -10 -11 -20 C -6 -12 -4 -8 -2 -5 C 2 -13 1 -20 2 -28 Z');
};

const lin = (x0: number, y0: number, x1: number, y1: number, cols: string[]) => {
  'worklet';
  const cs: Float32Array[] = [];
  for (let i = 0; i < cols.length; i++) cs.push(Skia.Color(cols[i]));
  return Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, cs, null, 0);
};

/** The sticker face: a white border round the die-cut, then the art. */
export const drawFace = (c: SkCanvas, k: number, font: { bold: SkFont | null }) => {
  'worklet';
  const sil = silhouette(k);
  const border = Skia.Paint();
  border.setAntiAlias(true);
  border.setStyle(1);
  border.setStrokeWidth(8);
  border.setStrokeJoin(1);
  border.setColor(Skia.Color('#ffffff'));
  c.drawPath(sil, border);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  if (k === 0) {
    fill.setShader(lin(0, -46, 0, 46, ['#f8cf6a', '#efb24a', '#d9892a']));
    c.drawPath(sil, fill);
    const crust = Skia.Paint();
    crust.setAntiAlias(true);
    crust.setColor(Skia.Color('#b8661c'));
    crust.setAlphaf(0.35);
    c.drawOval(Skia.XYWHRect(-40, -34, 80, 26), crust);
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(Skia.Color('#3a2412'));
    c.drawOval(Skia.XYWHRect(-22, -2, 9, 12), ink);
    c.drawOval(Skia.XYWHRect(13, -2, 9, 12), ink);
    const mouth = Skia.Paint();
    mouth.setAntiAlias(true);
    mouth.setStyle(1);
    mouth.setStrokeWidth(2.4);
    mouth.setStrokeCap(1);
    mouth.setColor(Skia.Color('#3a2412'));
    const m = Skia.PathBuilder.Make();
    m.moveTo(-7, 14);
    m.quadTo(-3.5, 19, 0, 14);
    m.quadTo(3.5, 19, 7, 14);
    c.drawPath(m.build(), mouth);
    const blush = Skia.Paint();
    blush.setAntiAlias(true);
    blush.setColor(Skia.Color('#ff8a8a'));
    blush.setAlphaf(0.55);
    c.drawOval(Skia.XYWHRect(-34, 12, 14, 8), blush);
    c.drawOval(Skia.XYWHRect(20, 12, 14, 8), blush);
    for (let i = 0; i < 3; i++) {
      c.drawLine(-46, 4 + i * 5, -36, 6 + i * 4, mouth);
      c.drawLine(46, 4 + i * 5, 36, 6 + i * 4, mouth);
    }
  } else if (k === 1) {
    fill.setShader(lin(-20, -20, 20, 24, ['#ff6f84', '#f2304d', '#c4122f']));
    c.drawPath(sil, fill);
  } else if (k === 2) {
    fill.setShader(lin(-42, 0, 42, 0, ['#ff7ac8', '#f2489e', '#c93cd8']));
    c.drawPath(sil, fill);
    if (font.bold) {
      const t = Skia.Paint();
      t.setAntiAlias(true);
      t.setColor(Skia.Color('#ffffff'));
      const ids = font.bold.getGlyphIDs('slay');
      const ws = font.bold.getGlyphWidths(ids);
      let tw = 0;
      for (let i = 0; i < ws.length; i++) tw += ws[i];
      c.save();
      c.skew(-0.2, 0);
      c.drawText('slay', -tw / 2 + 2, 8, t, font.bold);
      c.restore();
    }
  } else {
    fill.setShader(lin(0, -28, 0, 28, ['#ffe066', '#ff9a2e', '#f2501f']));
    c.drawPath(sil, fill);
    const inner = Skia.Paint();
    inner.setAntiAlias(true);
    inner.setShader(lin(0, -4, 0, 26, ['#fff2a8', '#ffc33a']));
    c.drawPath(svg('M1 -4 C 6 4 12 8 11 16 C 10 23 5 26 0 26 C -6 26 -11 22 -11 15 C -11 9 -6 6 -5 1 C -2 5 -1 7 0 8 C 2 4 1 0 1 -4 Z'), inner);
  }
  // a glossy vinyl highlight
  const gl = Skia.Paint();
  gl.setAntiAlias(true);
  gl.setShader(lin(-30, -30, 10, 10, ['rgba(255,255,255,0.45)', 'rgba(255,255,255,0)']));
  c.save();
  c.clipPath(sil, 1, true);
  c.drawRect(Skia.XYWHRect(-60, -60, 120, 120), gl);
  c.restore();
};

/** The half plane on the corner's side of the fold line (through P, normal n), as a big quad. */
const halfPlane = (px: number, py: number, nx: number, ny: number) => {
  'worklet';
  const tx = -ny;
  const ty = nx;
  const B = 400;
  const p = Skia.PathBuilder.Make();
  p.moveTo(px + tx * B, py + ty * B);
  p.lineTo(px + tx * B + nx * B, py + ty * B + ny * B);
  p.lineTo(px - tx * B + nx * B, py - ty * B + ny * B);
  p.lineTo(px - tx * B, py - ty * B);
  p.close();
  return p.build();
};

/**
 * A sticker with corner `corner` (0..3: top-left, top-right, bottom-right, bottom-left) peeled back by
 * `f` (0 flat .. 1 the fold through the middle).
 */
export const drawPeeled = (c: SkCanvas, k: number, corner: number, f: number, font: { bold: SkFont | null }) => {
  'worklet';
  if (f < 0.002) {
    drawFace(c, k, font);
    return;
  }
  const hx = HALF[k][0] + 4;
  const hy = HALF[k][1] + 4;
  const cx = corner === 0 || corner === 3 ? -hx : hx;
  const cy = corner === 0 || corner === 1 ? -hy : hy;
  const dl = Math.hypot(cx, cy);
  const nx = cx / dl;
  const ny = cy / dl;
  const d = f * dl;
  const px = cx - nx * d;
  const py = cy - ny * d;
  // the flat part
  c.save();
  const keep = halfPlane(px, py, -nx, -ny);
  c.clipPath(keep, 1, true);
  drawFace(c, k, font);
  c.restore();
  // the flap: the corner part reflected across the fold, its backing showing
  const r00 = 1 - 2 * nx * nx;
  const r01 = -2 * nx * ny;
  const r11 = 1 - 2 * ny * ny;
  const s = 2 * (px * nx + py * ny);
  const M = Skia.Matrix([r00, r01, s * nx, r01, r11, s * ny, 0, 0, 1]);
  const flapClip = halfPlane(px, py, nx, ny);
  const sil = silhouette(k);
  // its shadow on the flat part
  c.save();
  c.translate(2, 4);
  c.concat(M);
  c.clipPath(flapClip, 1, true);
  const sh = Skia.Paint();
  sh.setAntiAlias(true);
  sh.setColor(Skia.Color('#000000'));
  sh.setAlphaf(0.28);
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(0, 5, true));
  c.drawPath(sil, sh);
  c.restore();
  c.save();
  c.concat(M);
  c.clipPath(flapClip, 1, true);
  const back = Skia.Paint();
  back.setAntiAlias(true);
  back.setShader(lin(px, py, px + nx * d, py + ny * d, ['#d9d6dd', '#f6f4f8', '#ffffff']));
  const outline = Skia.Paint();
  outline.setAntiAlias(true);
  outline.setStyle(1);
  outline.setStrokeWidth(8);
  outline.setStrokeJoin(1);
  outline.setShader(lin(px, py, px + nx * d, py + ny * d, ['#d9d6dd', '#f6f4f8', '#ffffff']));
  c.drawPath(sil, outline);
  c.drawPath(sil, back);
  c.restore();
};
