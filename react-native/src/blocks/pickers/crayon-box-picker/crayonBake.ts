import { BlendMode, BlurStyle, ClipOp, type SkFont, type SkImage, Skia, TileMode } from '@shopify/react-native-skia';

import type { Geo } from './crayonArt';
import { CRAYONS } from './crayonMotion';

/* Crayon Box Picker — desk, paper, box and crayons, baked once on the JS thread. */

function hexColor(hex: string, a = 1) {
  const v = parseInt(hex.slice(1), 16);
  return Float32Array.of(((v >> 16) & 255) / 255, ((v >> 8) & 255) / 255, (v & 255) / 255, a);
}

function rgbTone(c: number[], k: number, a = 1) {
  if (k < 0) return Float32Array.of(c[0] * (1 + k), c[1] * (1 + k), c[2] * (1 + k), a);
  return Float32Array.of(c[0] + (1 - c[0]) * k, c[1] + (1 - c[1]) * k, c[2] + (1 - c[2]) * k, a);
}

/** The desk, the sheet of paper, and the open box's back and inside. */
export function bakeDesk(g: Geo, scale: number): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(g.w * scale), Math.ceil(g.h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const desk = Skia.Paint();
  desk.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(0, 0), Skia.Point(g.w * 0.3, g.h), [hexColor('#BFD8D2'), hexColor('#A9C9C2'), hexColor('#93B5AE')], [0, 0.55, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), desk);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.8, 0.8, 3, 5, 0, 0));
  grain.setAlphaf(0.06);
  grain.setBlendMode(BlendMode.Multiply);
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), grain);
  // Paper with two shadows and a slight tooth.
  const px = g.cx - (g.w - 24) / 2;
  const pw = g.w - 24;
  const paper = Skia.RRectXY(Skia.XYWHRect(px, g.paperY, pw, g.paperH), 6, 6);
  const amb = Skia.Paint();
  amb.setColor(hexColor('#2F4A44', 0.22));
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 12, true));
  c.save();
  c.translate(0, 8);
  c.drawRRect(paper, amb);
  c.restore();
  const contact = Skia.Paint();
  contact.setColor(hexColor('#2F4A44', 0.3));
  contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
  c.save();
  c.translate(0, 1.5);
  c.drawRRect(paper, contact);
  c.restore();
  const sheet = Skia.Paint();
  sheet.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(0, g.paperY), Skia.Point(0, g.paperY + g.paperH), [hexColor('#FFFDF8'), hexColor('#FBF6EC'), hexColor('#F3ECDF')], [0, 0.6, 1], TileMode.Clamp));
  c.drawRRect(paper, sheet);
  const tooth = Skia.Paint();
  tooth.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, 13, 0, 0));
  tooth.setAlphaf(0.06);
  tooth.setBlendMode(BlendMode.Multiply);
  c.save();
  c.clipRRect(paper, ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(px, g.paperY, pw, g.paperH), tooth);
  c.restore();
  // The box: its back flap, open and leaning away, and the dark inside above the crayons' feet.
  const backTop = g.rimY - g.showBack - 18;
  const flap = Skia.PathBuilder.Make();
  flap.moveTo(g.boxX + 4, g.rimY);
  flap.lineTo(g.boxX + 14, backTop);
  flap.lineTo(g.boxX + g.boxW - 14, backTop);
  flap.lineTo(g.boxX + g.boxW - 4, g.rimY);
  flap.close();
  const flapPath = flap.build();
  const fsh = Skia.Paint();
  fsh.setColor(hexColor('#2F4A44', 0.25));
  fsh.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10, true));
  c.save();
  c.translate(0, 6);
  c.drawPath(flapPath, fsh);
  c.restore();
  const fp = Skia.Paint();
  fp.setAntiAlias(true);
  fp.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(0, backTop), Skia.Point(0, g.rimY), [hexColor('#2F6B52'), hexColor('#245641'), hexColor('#173B2C')], [0, 0.5, 1], TileMode.Clamp));
  c.drawPath(flapPath, fp);
  const dots = Skia.Paint();
  dots.setAntiAlias(true);
  dots.setColor(hexColor('#F4C64E', 0.22));
  for (let k = 0; k < 9; k += 1) c.drawCircle(g.boxX + 36 + k * ((g.boxW - 72) / 8), backTop + 12, 3, dots);
  const inside = Skia.Paint();
  inside.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(0, g.rimY - 46), Skia.Point(0, g.rimY + 4), [hexColor('#173B2C', 0), hexColor('#0F271D', 0.7), hexColor('#0B1D16', 0.9)], [0, 0.6, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(g.boxX + 6, g.rimY - 46, g.boxW - 12, 50), inside);
  surface.flush();
  return surface.makeImageSnapshot();
}

/** The printed box front: an image of (boxW + 40) × (frontH + 40) pt drawn at (boxX − 20, rimY − 20). */
export function bakeFront(g: Geo, scale: number, brand: SkFont, small: SkFont): SkImage | null {
  const W = g.boxW + 40;
  const H = g.frontH + 40;
  const surface = Skia.Surface.Make(Math.ceil(W * scale), Math.ceil(H * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const r = Skia.RRectXY(Skia.XYWHRect(20, 20, g.boxW, g.frontH), 6, 6);
  const amb = Skia.Paint();
  amb.setColor(hexColor('#2F4A44', 0.3));
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 9, true));
  c.save();
  c.translate(0, 8);
  c.drawRRect(r, amb);
  c.restore();
  const card = Skia.Paint();
  card.setAntiAlias(true);
  card.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(0, 20), Skia.Point(0, 20 + g.frontH), [hexColor('#FAD465'), hexColor('#F2C04A'), hexColor('#DFA834')], [0, 0.5, 1], TileMode.Clamp));
  c.drawRRect(r, card);
  c.save();
  c.clipRRect(r, ClipOp.Intersect, true);
  // A green band, a row of printed colour bars, the brand.
  const band = Skia.Paint();
  band.setAntiAlias(true);
  band.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(0, 20 + g.frontH * 0.42), Skia.Point(0, 20 + g.frontH * 0.82), [hexColor('#2F7356'), hexColor('#24604A'), hexColor('#1B4E3B')], [0, 0.5, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(20, 20 + g.frontH * 0.42, g.boxW, g.frontH * 0.4), band);
  const barW = (g.boxW - 40) / 12;
  const bar = Skia.Paint();
  bar.setAntiAlias(true);
  for (let i = 0; i < 12; i += 1) {
    bar.setColor(hexColor(CRAYONS[i].hex));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(40 + i * barW + 1.5, 20 + g.frontH * 0.16, barW - 3, g.frontH * 0.14), 2, 2), bar);
  }
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  const title = 'DOODLEBUG';
  const tw = brand.getGlyphWidths(brand.getGlyphIDs(title)).reduce((s, x) => s + x, 0) + 2.5 * (title.length - 1);
  let x = 20 + (g.boxW - tw) / 2;
  const by = 20 + g.frontH * 0.62 + brand.getSize() * 0.36;
  for (const ch of title) {
    ink.setColor(hexColor('#0F2C21', 0.5));
    c.drawText(ch, x + 1, by + 1.5, ink, brand);
    ink.setColor(hexColor('#FFF4D6'));
    c.drawText(ch, x, by, ink, brand);
    x += brand.getGlyphWidths(brand.getGlyphIDs(ch))[0] + 2.5;
  }
  const line = '12 WAX CRAYONS · WASHABLE';
  const lw = small.getGlyphWidths(small.getGlyphIDs(line)).reduce((s, v) => s + v, 0);
  ink.setColor(hexColor('#6B4A12', 0.85));
  c.drawText(line, 20 + (g.boxW - lw) / 2, 20 + g.frontH * 0.93, ink, small);
  const tooth = Skia.Paint();
  tooth.setShader(Skia.Shader.MakeFractalNoise(0.7, 0.7, 3, 6, 0, 0));
  tooth.setAlphaf(0.08);
  tooth.setBlendMode(BlendMode.Multiply);
  c.drawRect(Skia.XYWHRect(20, 20, g.boxW, g.frontH), tooth);
  // The folds into the side panels and the lit top edge.
  const fold = Skia.Paint();
  fold.setShader(Skia.Shader.MakeLinearGradient(Skia.Point(20, 0), Skia.Point(20 + g.boxW, 0), [hexColor('#7A5410', 0.28), hexColor('#7A5410', 0), hexColor('#7A5410', 0), hexColor('#7A5410', 0.3)], [0, 0.06, 0.94, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(20, 20, g.boxW, g.frontH), fold);
  c.restore();
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setColor(hexColor('#FFF5D2', 0.85));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(21, 20.5, g.boxW - 2, 2), 1, 1), edge);
  surface.flush();
  return surface.makeImageSnapshot();
}

/** Crayon i, standing, tip up: an image of cw × cl pt. */
export function bakeCrayon(g: Geo, i: number, scale: number, font: SkFont): SkImage | null {
  const W = g.cw;
  const H = g.cl;
  const surface = Skia.Surface.Make(Math.ceil(W * scale), Math.ceil(H * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const rgb = CRAYONS[i].rgb;
  // Each tip is worn its own way: height, flat top and lean.
  const h1 = (Math.sin(i * 12.9898) * 43758.5453) % 1;
  const h2 = (Math.sin(i * 78.233) * 43758.5453) % 1;
  const tipH = W * (0.95 + 0.35 * Math.abs(h1));
  const flat = W * (0.12 + 0.22 * Math.abs(h2));
  const lean = W * 0.12 * (h1 > 0 ? 1 : -1) * Math.abs(h2);
  const wax = Skia.Paint();
  wax.setAntiAlias(true);
  wax.setColor(rgbTone(rgb, 0));
  const tip = Skia.PathBuilder.Make();
  tip.moveTo(W / 2 - flat / 2 + lean, 1.2);
  tip.quadTo(W / 2 + lean, 0, W / 2 + flat / 2 + lean, 1.2);
  tip.lineTo(W * 0.9, tipH);
  tip.lineTo(W * 0.1, tipH);
  tip.close();
  c.drawPath(tip.build(), wax);
  c.drawRect(Skia.XYWHRect(W * 0.04, tipH - 0.5, W * 0.92, H - tipH - 2), wax);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(W * 0.04, H - 8, W * 0.92, 8), 2, 2), wax);
  // Wrapper: printed paper in the crayon's colour with two black bands and the name.
  const top = tipH + W * 0.4;
  const bottom = H - W * 0.5;
  const paper = Skia.Paint();
  paper.setAntiAlias(true);
  paper.setColor(rgbTone(rgb, 0.12));
  c.drawRect(Skia.XYWHRect(0, top, W, bottom - top), paper);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(hexColor('#16120E', 0.85));
  for (const y of [top + 4, top + 8.5, bottom - 10, bottom - 5.5]) c.drawRect(Skia.XYWHRect(0, y, W, 1.6), ink);
  const name = CRAYONS[i].name.toUpperCase();
  const nw = font.getGlyphWidths(font.getGlyphIDs(name)).reduce((s, v) => s + v, 0);
  c.save();
  c.translate(W / 2 + font.getSize() * 0.36, top + 16 + nw);
  c.rotate(-90, 0, 0);
  ink.setColor(hexColor('#16120E', 0.9));
  c.drawText(name, 0, 0, ink, font);
  c.restore();
  // Round it: dark edges, a soft highlight a third of the way across, a crisp specular line.
  const shade = Skia.Paint();
  shade.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, 0),
      Skia.Point(W, 0),
      [hexColor('#000000', 0.42), hexColor('#000000', 0), hexColor('#FFFFFF', 0.22), hexColor('#000000', 0.05), hexColor('#000000', 0.5)],
      [0, 0.18, 0.34, 0.62, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, tipH * 0.4, W, H - tipH * 0.4), shade);
  const tipShade = Skia.Paint();
  tipShade.setAntiAlias(true);
  tipShade.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, 0), Skia.Point(W, 0), [hexColor('#000000', 0.35), hexColor('#FFFFFF', 0.25), hexColor('#000000', 0.4)], [0, 0.35, 1], TileMode.Clamp),
  );
  c.drawPath(tip.build(), tipShade);
  const spec = Skia.Paint();
  spec.setAntiAlias(true);
  spec.setColor(hexColor('#FFFFFF', 0.4));
  c.drawRect(Skia.XYWHRect(W * 0.3, tipH + 2, W * 0.06, H - tipH - 6), spec);
  surface.flush();
  return surface.makeImageSnapshot();
}
