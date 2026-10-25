import {
  BlurStyle,
  ClipOp,
  PaintStyle,
  Skia,
  StrokeCap,
  TileMode,
  type SkCanvas,
  type SkFont,
  type SkImage,
} from '@shopify/react-native-skia';

import { FILINGS, type Geo } from './ironMotion';

/*
 Drawing for Iron Filings Magnet: the walnut desk and the sheet of graph paper with its printed
 header and footer labels (recorded once), the two bar magnets seen faintly through the paper and
 the live readings (per frame), and the filing sprite the Atlas stamps 900 times.
 */

export type Fonts = { kicker: SkFont; title: SkFont; sub: SkFont; label: SkFont; value: SkFont; pole: SkFont };

export const INK = '#2A2622';
export const INK_SOFT = '#6F665B';
/** The filing sprite: 12 × 3 pt drawn at 4 px per pt; row 0 the filing, row 1 its shadow. */
export const SPRITE_W = 48;
export const SPRITE_H = 12;

function fill(color: string) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}

function widthOf(font: SkFont, text: string) {
  'worklet';
  const ws = font.getGlyphWidths(font.getGlyphIDs(text));
  let s = 0;
  for (let i = 0; i < ws.length; i += 1) s += ws[i];
  return s;
}

/** A sliver of iron: tapered, gunmetal with a bright ridge; and a soft shadow. */
export function makeFilingSprite(): SkImage | null {
  const surface = Skia.Surface.Make(SPRITE_W, SPRITE_H * 2);
  if (!surface) return null;
  const c = surface.getCanvas();
  c.clear(Skia.Color('transparent'));
  const body = Skia.PathBuilder.Make();
  body.moveTo(1.5, 6);
  body.cubicTo(9, 1.2, 36, 0.8, 46.5, 4.6);
  body.lineTo(46.5, 7.2);
  body.cubicTo(36, 11.2, 9, 10.8, 1.5, 6);
  body.close();
  const metal = fill('#34353A');
  metal.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, 2), Skia.Point(0, 10), ['#8E939C', '#3A3C42', '#17181C'].map((h) => Skia.Color(h)), [0, 0.45, 1], TileMode.Clamp),
  );
  const path = body.build();
  c.drawPath(path, metal);
  const ridge = Skia.Paint();
  ridge.setAntiAlias(true);
  ridge.setStyle(PaintStyle.Stroke);
  ridge.setStrokeWidth(0.9);
  ridge.setStrokeCap(StrokeCap.Round);
  ridge.setColor(Skia.Color('rgba(225,230,238,0.75)'));
  const line = Skia.PathBuilder.Make();
  line.moveTo(8, 4.2);
  line.lineTo(38, 3.8);
  c.drawPath(line.build(), ridge);
  c.save();
  c.translate(0, SPRITE_H);
  const shadow = fill('rgba(40,30,18,0.55)');
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.6, true));
  c.drawPath(path, shadow);
  c.restore();
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}

/** Sprite rectangles for the Atlas (all filings share one, the shadows another). */
export function spriteRects(row: number) {
  const r = Skia.XYWHRect(0, row * SPRITE_H, SPRITE_W, SPRITE_H);
  return new Array(FILINGS).fill(0).map(() => r);
}

export function drawPaper(c: SkCanvas, g: Geo, fonts: Fonts) {
  const desk = Skia.Paint();
  desk.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, 0), Skia.Point(g.w, g.h), ['#3B2C22', '#2A1F18', '#1E1612'].map((h) => Skia.Color(h)), [0, 0.5, 1], TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), desk);
  const grainD = Skia.Paint();
  grainD.setShader(Skia.Shader.MakeFractalNoise(0.02, 0.6, 3, 11, 0, 0));
  grainD.setAlphaf(0.1);
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), grainD);

  const sheet = Skia.RRectXY(Skia.XYWHRect(g.paperX, g.paperY, g.paperW, g.paperH), 6, 6);
  const amb = fill('rgba(0,0,0,0.45)');
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  c.save();
  c.translate(0, 10);
  c.drawRRect(sheet, amb);
  c.restore();
  const contact = fill('rgba(0,0,0,0.5)');
  contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.5, true));
  c.save();
  c.translate(0, 1.5);
  c.drawRRect(sheet, contact);
  c.restore();
  const paper = fill('#EFE6D2');
  paper.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(g.w * 0.45, g.paperY + g.paperH * 0.4),
      g.paperH * 0.75,
      ['#F4ECDA', '#EDE3CE', '#E2D5BB'].map((h) => Skia.Color(h)),
      [0, 0.6, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRRect(sheet, paper);
  c.save();
  c.clipRRect(sheet, ClipOp.Intersect, true);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.8, 0.8, 2, 5, 0, 0));
  grain.setAlphaf(0.06);
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), grain);
  // Graph ruling, every fifth line a little darker, and the red margin.
  const step = 9 * g.u;
  let k = 0;
  for (let x = g.paperX + 6; x < g.paperX + g.paperW; x += step, k += 1) {
    c.drawRect(Skia.XYWHRect(x, g.paperY, 0.6, g.paperH), fill(k % 5 === 0 ? 'rgba(70,110,160,0.13)' : 'rgba(70,110,160,0.06)'));
  }
  k = 0;
  for (let y = g.paperY + 6; y < g.paperY + g.paperH; y += step, k += 1) {
    c.drawRect(Skia.XYWHRect(g.paperX, y, g.paperW, 0.6), fill(k % 5 === 0 ? 'rgba(70,110,160,0.13)' : 'rgba(70,110,160,0.06)'));
  }
  c.drawRect(Skia.XYWHRect(g.paperX + 22, g.paperY, 0.9, g.paperH), fill('rgba(196,74,70,0.35)'));
  c.restore();

  // Printed header.
  const x = g.paperX + 34;
  c.drawText('FIELD NOTES  ·  NO. 7', x, g.paperY + 34 * g.u, fill(INK_SOFT), fonts.kicker);
  c.drawText('Magnetism', x, g.paperY + 72 * g.u, fill(INK), fonts.title);
  c.drawText('Exp. 04 — A bar magnet under the page', x, g.paperY + 94 * g.u, fill(INK_SOFT), fonts.sub);
  const date = 'Oct 25';
  c.drawText(date, g.paperX + g.paperW - 18 - widthOf(fonts.sub, date), g.paperY + 34 * g.u, fill(INK_SOFT), fonts.sub);
  // Footer labels (values are drawn live).
  const fy = g.footerY;
  c.drawRect(Skia.XYWHRect(x, fy - 14 * g.u, g.paperX + g.paperW - 18 - x, 0.8), fill('rgba(42,38,34,0.25)'));
  const cols = [x, x + (g.paperW - 52) * 0.42, x + (g.paperW - 52) * 0.72];
  const labels = ['FIELD AT CENTRE', 'POLES', 'FILINGS'];
  for (let i = 0; i < 3; i += 1) c.drawText(labels[i], cols[i], fy + 6 * g.u, fill(INK_SOFT), fonts.label);
  c.drawText(`${FILINGS}`, cols[2], fy + 30 * g.u, fill(INK), fonts.value);
}

export function footerColumns(g: Geo) {
  'worklet';
  const x = g.paperX + 34;
  return [x, x + (g.paperW - 52) * 0.42];
}

/** A bar magnet seen through the paper: faint, soft-edged, red north and blue south. */
function magnet(c: SkCanvas, g: Geo, x: number, y: number, a: number, label: SkFont, alpha: number) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate((a * 180) / Math.PI, 0, 0);
  const L = g.half + 8 * g.u;
  const T = g.thick;
  const halves: [string[], number][] = [
    [['#7EA2D6', '#3E6AAE', '#2A4C86'], -1],
    [['#E58A80', '#C8443B', '#9A2F29'], 1],
  ];
  for (const [stops, side] of halves) {
    const p = fill('#ffffff');
    p.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(0, -T), Skia.Point(0, T), stops.map((s) => Skia.Color(s)), [0, 0.45, 1], TileMode.Clamp));
    p.setAlphaf(alpha);
    p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.9, false));
    const r = side < 0 ? Skia.XYWHRect(-L, -T, L, 2 * T) : Skia.XYWHRect(0, -T, L, 2 * T);
    c.drawRRect(Skia.RRectXY(r, side < 0 ? 4 : 0.5, side < 0 ? 4 : 0.5), p);
    if (side > 0) c.drawRRect(Skia.RRectXY(Skia.XYWHRect(L - 8, -T, 8, 2 * T), 4, 4), p);
  }
  const t = fill('rgba(255,255,255,0.9)');
  t.setAlphaf(alpha * 1.6 > 1 ? 1 : alpha * 1.6);
  c.drawText('N', L - 16 * g.u - widthOf(label, 'N') / 2, 4 * g.u, t, label);
  c.drawText('S', -L + 16 * g.u - widthOf(label, 'S') / 2, 4 * g.u, t, label);
  c.restore();
}

/** Magnets (under the filings), B's pull tab while it is parked, and the live readings. */
export function drawUnder(c: SkCanvas, g: Geo, m: number[], fonts: Fonts) {
  'worklet';
  magnet(c, g, m[0], m[1], m[2], fonts.pole, 0.66);
  magnet(c, g, m[3], m[4], m[5], fonts.pole, 0.66);
  const parked = Math.max(0, Math.min(1, (m[3] - (g.w - 40 * g.u)) / (40 * g.u)));
  if (parked > 0.01) {
    const tx = g.paperX + g.paperW - 30;
    const p = fill('#2A2622');
    p.setAlphaf(0.85 * parked);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(tx, m[4] - 22, 26, 44), 13, 13), p);
    const chev = Skia.Paint();
    chev.setAntiAlias(true);
    chev.setStyle(PaintStyle.Stroke);
    chev.setStrokeWidth(2);
    chev.setStrokeCap(StrokeCap.Round);
    chev.setColor(Skia.Color('#EFE6D2'));
    chev.setAlphaf(parked);
    const pb = Skia.PathBuilder.Make();
    pb.moveTo(tx + 15, m[4] - 7);
    pb.lineTo(tx + 9, m[4]);
    pb.lineTo(tx + 15, m[4] + 7);
    c.drawPath(pb.build(), chev);
  }
}

export function drawReadings(c: SkCanvas, g: Geo, millitesla: number, poles: number, fonts: Fonts) {
  'worklet';
  const [x0, x1] = footerColumns(g);
  const y = g.footerY + 30 * g.u;
  const v = `${Math.round(millitesla)}`;
  c.drawText(v, x0, y, fill(INK), fonts.value);
  c.drawText(' mT', x0 + widthOf(fonts.value, v), y, fill(INK_SOFT), fonts.sub);
  c.drawText(`${poles}`, x1, y, fill(INK), fonts.value);
}
