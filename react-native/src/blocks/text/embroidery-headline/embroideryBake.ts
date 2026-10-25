import {
  BlendMode,
  BlurStyle,
  ClipOp,
  PaintStyle,
  type SkFont,
  type SkImage,
  Skia,
  TileMode,
} from '@shopify/react-native-skia';

import { ANGLES, type Geo, INDIGO, LIGHT, MADDER, type RGB, SPACING, type Satin, type SatinGlyph } from './embroideryArt';

/* Embroidery Headline — the satin, cut from the rasterised headline, and the table, hoop and linen; built once on the JS thread. */

function hexColor(hex: string, a = 1) {
  const v = parseInt(hex.slice(1), 16);
  return Float32Array.of(((v >> 16) & 255) / 255, ((v >> 8) & 255) / 255, (v & 255) / 255, a);
}

function advance(font: SkFont, text: string) {
  const w = font.getGlyphWidths(font.getGlyphIDs(text));
  let s = 0;
  for (const x of w) s += x;
  return s;
}

/**
 * Turns the two lines into satin. `make(size)` returns the headline font at a size. The text is
 * fitted into the hoop; returns the satin and the font size used.
 */
export function buildSatin(g: Geo, make: (size: number) => SkFont, lines: [string, string]): Satin {
  const probe = make(100);
  const widest = Math.max(advance(probe, lines[0]), advance(probe, lines[1] + '.'));
  const size = Math.min(112, (100 * g.fabricR * 1.5) / widest);
  const font = make(size);
  const px = 2;
  const big = make(size * px);
  const base1 = g.cy - size * 0.14;
  const base2 = base1 + size * 1.0;
  const glyphs: SatinGlyph[] = [];
  let knot = [0, 0, 0];
  lines.forEach((line, li) => {
    const widths = font.getGlyphWidths(font.getGlyphIDs(line));
    const full = advance(font, line) + (li === 1 ? size * 0.3 : 0);
    let x = g.cx - full / 2;
    const base = li === 0 ? base1 : base2;
    const chars = Array.from(line);
    chars.forEach((ch, i) => {
      const pad = size * 0.25;
      const wpt = widths[i] + pad * 2;
      const hpt = size * 1.3;
      const top = base - size * 0.95;
      const surface = Skia.Surface.Make(Math.ceil(wpt * px), Math.ceil(hpt * px));
      if (surface) {
        const c = surface.getCanvas();
        const ink = Skia.Paint();
        ink.setAntiAlias(true);
        ink.setColor(Float32Array.of(1, 1, 1, 1));
        c.drawText(ch, pad * px, (base - top) * px, ink, big);
        surface.flush();
        const image = surface.makeImageSnapshot();
        const pixels = image.readPixels();
        const iw = image.width();
        const ih = image.height();
        const angle = ANGLES[ch] ?? 50;
        glyphs.push(cutSatin(pixels, iw, ih, px, x - pad, top, angle, li === 0 ? MADDER : INDIGO));
      }
      x += widths[i];
    });
    if (li === 1) knot = [x + size * 0.14, base - size * 0.095, size * 0.088];
  });
  return { glyphs, counts: glyphs.map((gl) => gl.count), spacing: SPACING, knot, knotRGB: INDIGO };
}

function cutSatin(
  pixels: Float32Array | Uint8Array | null,
  iw: number,
  ih: number,
  px: number,
  ox: number,
  oy: number,
  angleDeg: number,
  rgb: RGB,
): SatinGlyph {
  const segs: number[] = [];
  if (!pixels) return { segs, count: 0, rgb, sheen: 0, angle: angleDeg };
  const scale = pixels instanceof Float32Array ? 1 : 255;
  const inside = (x: number, y: number) => {
    const i = Math.round(x * px);
    const j = Math.round(y * px);
    if (i < 0 || j < 0 || i >= iw || j >= ih) return false;
    return pixels[(j * iw + i) * 4 + 3] > 0.45 * scale;
  };
  const a = (angleDeg * Math.PI) / 180;
  const dx = Math.cos(a);
  const dy = -Math.sin(a);
  const nx = Math.sin(a);
  const ny = Math.cos(a);
  const w = iw / px;
  const h = ih / px;
  const cx = w / 2;
  const cy = h / 2;
  const R = Math.hypot(w, h) / 2;
  const step = 0.5;
  for (let o = -R; o <= R; o += SPACING) {
    let runStart = -1;
    for (let s = -R; s <= R + step; s += step) {
      const x = cx + nx * o + dx * s;
      const y = cy + ny * o + dy * s;
      const isIn = s <= R && inside(x, y);
      if (isIn && runStart === -1) runStart = s;
      if (!isIn && runStart !== -1) {
        const s0 = runStart - 0.35;
        const s1 = s - step + 0.35;
        if (s1 - s0 > 1.2) {
          segs.push(ox + cx + nx * o + dx * s0, oy + cy + ny * o + dy * s0, ox + cx + nx * o + dx * s1, oy + cy + ny * o + dy * s1);
        }
        runStart = -1;
      }
    }
  }
  const d = ((angleDeg - LIGHT) * Math.PI) / 180;
  return { segs, count: segs.length / 4, rgb, sheen: Math.pow(Math.cos(d), 2), angle: angleDeg };
}

/** Table, hoop and linen at `scale` px per pt. */
export function bakeScene(g: Geo, scale: number): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(g.w * scale), Math.ceil(g.h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  // A dark sage tablecloth with a vignette and grain.
  const bg = Skia.Paint();
  bg.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(g.cx, g.cy - g.hoopR * 0.2),
      Math.max(g.w, g.h) * 0.75,
      [hexColor('#3A4A3F'), hexColor('#2C3A31'), hexColor('#1B241F')],
      [0, 0.5, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), bg);
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.8, 0.8, 3, 5, 0, 0));
  grain.setAlphaf(0.07);
  grain.setBlendMode(BlendMode.Overlay);
  c.drawRect(Skia.XYWHRect(0, 0, g.w, g.h), grain);

  const amb = Skia.Paint();
  amb.setAntiAlias(true);
  amb.setColor(hexColor('#0B100D', 0.45));
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 22, true));
  c.drawCircle(g.cx, g.cy + 18, g.hoopR + 4, amb);
  const contact = Skia.Paint();
  contact.setAntiAlias(true);
  contact.setColor(hexColor('#0B100D', 0.5));
  contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  c.drawCircle(g.cx, g.cy + 2.5, g.hoopR, contact);

  // Linen: warm oatmeal with slubby weft and warp threads.
  const fr = g.fabricR + 3;
  c.save();
  c.clipPath(Skia.PathBuilder.Make().addCircle(g.cx, g.cy, fr).build(), ClipOp.Intersect, true);
  const cloth = Skia.Paint();
  cloth.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(g.cx - fr * 0.3, g.cy - fr * 0.5),
      fr * 1.7,
      [hexColor('#F1E8D8'), hexColor('#E6DAC4'), hexColor('#D4C5AC')],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(g.cx, g.cy, fr, cloth);
  const thread = Skia.Paint();
  thread.setAntiAlias(true);
  thread.setStyle(PaintStyle.Stroke);
  let seed = 1;
  const rnd = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  for (let k = -fr; k <= fr; k += 1.15) {
    for (const vertical of [false, true]) {
      thread.setStrokeWidth(0.4 + rnd() * 0.45);
      const light = rnd() > 0.5;
      thread.setColor(light ? hexColor('#FFFAF0', 0.12 + rnd() * 0.14) : hexColor('#9C8A6C', 0.05 + rnd() * 0.08));
      if (vertical) c.drawLine(g.cx + k, g.cy - fr, g.cx + k, g.cy + fr, thread);
      else c.drawLine(g.cx - fr, g.cy + k, g.cx + fr, g.cy + k, thread);
    }
  }
  const slub = Skia.Paint();
  slub.setShader(Skia.Shader.MakeFractalNoise(0.04, 0.6, 3, 9, 0, 0));
  slub.setAlphaf(0.1);
  slub.setBlendMode(BlendMode.Multiply);
  c.drawCircle(g.cx, g.cy, fr, slub);
  const rim = Skia.Paint();
  rim.setShader(
    Skia.Shader.MakeRadialGradient(
      Skia.Point(g.cx, g.cy),
      fr,
      [hexColor('#4A3A26', 0), hexColor('#4A3A26', 0), hexColor('#4A3A26', 0.3)],
      [0, 0.8, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(g.cx, g.cy, fr, rim);
  c.restore();

  const ring = (r0: number, r1: number, stops: string[]) => {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(r1 - r0);
    p.setShader(
      Skia.Shader.MakeRadialGradient(
        Skia.Point(g.cx, g.cy),
        r1,
        stops.map((s) => hexColor(s)),
        [r0 / r1, (r0 + (r1 - r0) * 0.3) / r1, (r0 + (r1 - r0) * 0.7) / r1, 1],
        TileMode.Clamp,
      ),
    );
    c.drawCircle(g.cx, g.cy, (r0 + r1) / 2, p);
  };
  ring(g.fabricR - 1, g.fabricR + 4, ['#8F6235', '#C29257', '#D3A970', '#A87540']);
  ring(g.fabricR + 3, g.hoopR, ['#7F5329', '#D2A46A', '#E3BC86', '#9E6A37']);
  const woodGrain = Skia.Paint();
  woodGrain.setAntiAlias(true);
  woodGrain.setStyle(PaintStyle.Stroke);
  woodGrain.setStrokeWidth(g.hoopR - g.fabricR - 3);
  woodGrain.setShader(Skia.Shader.MakeFractalNoise(0.02, 0.45, 3, 11, 0, 0));
  woodGrain.setAlphaf(0.2);
  woodGrain.setBlendMode(BlendMode.Multiply);
  c.drawCircle(g.cx, g.cy, (g.fabricR + 3 + g.hoopR) / 2, woodGrain);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(1);
  edge.setColor(hexColor('#FFF1DA', 0.4));
  c.drawCircle(g.cx, g.cy - 0.5, g.hoopR - 1.2, edge);
  edge.setColor(hexColor('#4E3218', 0.5));
  c.drawCircle(g.cx, g.cy + 0.5, g.fabricR + 3.2, edge);
  // Brass screw clamp.
  const top = g.cy - g.hoopR;
  const lug = Skia.Paint();
  lug.setAntiAlias(true);
  lug.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, top - 12), Skia.Point(0, top + 12), [hexColor('#E2B880'), hexColor('#BD8A52'), hexColor('#936234')], [0, 0.55, 1], TileMode.Clamp),
  );
  for (const dx of [-11, 3]) c.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.cx + dx, top - 13, 8, 22), 2.5, 2.5), lug);
  const brass = Skia.Paint();
  brass.setAntiAlias(true);
  brass.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(0, top - 9),
      Skia.Point(0, top - 1),
      [hexColor('#FFF0B8'), hexColor('#D9A93E'), hexColor('#8D6418'), hexColor('#C99733')],
      [0, 0.35, 0.75, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.cx - 22, top - 9, 44, 8), 3, 3), brass);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.cx + 20, top - 12, 9, 14), 3, 3), brass);
  surface.flush();
  return surface.makeImageSnapshot();
}
