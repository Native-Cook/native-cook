import { AlphaType, BlurStyle, ColorType, FilterMode, MipmapMode, PaintStyle, PathOp, type SkCanvas, type SkFont, type SkImage, Skia, StrokeCap } from '@shopify/react-native-skia';

/*
 Paper Plane Steps — the writing paper, drawn once into an atlas: the three onboarding sheets side
 by side and, fourth, the back of the paper. Each sheet: cream laid paper with grain, faint ruled
 lines and a red margin, a perforated stamp in the top-right corner (a small landscape in its own
 palette per step) under a wavy postmark, the step number, a title and three lines of copy.
*/

export const STEP_COPY = [
  { kicker: 'No. 1 · Write', title: 'Write it slow', lines: ['Notes travel at the pace', 'of paper. Take your time,', 'nobody is watching you type.'] },
  { kicker: 'No. 2 · Fold', title: 'Fold it up', lines: ['Every letter is folded by', 'hand and leaves the post', 'office at nine each evening.'] },
  { kicker: 'No. 3 · Send', title: 'Let it fly', lines: ['Friends far away get it', 'the next day, the way a', 'letter should arrive.'] },
];

const STAMPS = [
  ['#F6B26B', '#E07A4F', '#8E3B46', '#3E2A4F'],
  ['#9ED8D1', '#4FA3A5', '#2C6E7F', '#1D3B52'],
  ['#D9C2F0', '#9C7CC8', '#5E4B8B', '#2E2A4F'],
];

const INK = '#2B2F45';
const PAPER = ['#FBF6EA', '#F5EEDD', '#EFE6D2'];

export type PlaneFonts = { kicker: SkFont; title: SkFont; body: SkFont; mark: SkFont };

function textWidth(font: SkFont, text: string) {
  let w = 0;
  for (const v of font.getGlyphWidths(font.getGlyphIDs(text))) w += v;
  return w;
}

function stamp(c: SkCanvas, x: number, y: number, w: number, h: number, pal: string[], fonts: PlaneFonts) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  // Shadow, then the perforated stamp paper: a white rectangle with half-circle bites along its edges.
  p.setColor(Skia.Color('rgba(60,40,20,0.18)'));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
  c.drawRect(Skia.XYWHRect(x + 1, y + 2, w, h), p);
  p.setMaskFilter(null);
  const body = Skia.PathBuilder.Make();
  body.addRect(Skia.XYWHRect(x, y, w, h));
  const bites = Skia.PathBuilder.Make();
  const r = 2.2;
  for (let i = 0; i <= Math.round(w / 6); i += 1) {
    const bx = x + (i * w) / Math.round(w / 6);
    bites.addCircle(bx, y, r);
    bites.addCircle(bx, y + h, r);
  }
  for (let i = 0; i <= Math.round(h / 6); i += 1) {
    const by = y + (i * h) / Math.round(h / 6);
    bites.addCircle(x, by, r);
    bites.addCircle(x + w, by, r);
  }
  const cut = Skia.Path.MakeFromOp(body.build(), bites.build(), PathOp.Difference) ?? body.build();
  p.setColor(Skia.Color('#FFFCF4'));
  c.drawPath(cut, p);
  // The picture: sky in three stops, a sun, two hills.
  const ix = x + 5;
  const iy = y + 5;
  const iw = w - 10;
  const ih = h - 16;
  p.setShader(Skia.Shader.MakeLinearGradient({ x: ix, y: iy }, { x: ix, y: iy + ih }, [pal[0], pal[1], pal[2]].map((v) => Skia.Color(v)), [0, 0.6, 1], 0));
  c.drawRect(Skia.XYWHRect(ix, iy, iw, ih), p);
  p.setShader(null);
  p.setColor(Skia.Color('rgba(255,246,220,0.9)'));
  c.drawCircle(ix + iw * 0.66, iy + ih * 0.42, iw * 0.14, p);
  c.save();
  c.clipRect(Skia.XYWHRect(ix, iy, iw, ih), 1, true);
  const hill = Skia.PathBuilder.Make();
  hill.moveTo(ix - 2, iy + ih * 0.78);
  hill.quadTo(ix + iw * 0.3, iy + ih * 0.5, ix + iw * 0.62, iy + ih * 0.8);
  hill.lineTo(ix + iw + 2, iy + ih + 2);
  hill.lineTo(ix - 2, iy + ih + 2);
  hill.close();
  p.setColor(Skia.Color(pal[2]));
  c.drawPath(hill.build(), p);
  const hill2 = Skia.PathBuilder.Make();
  hill2.moveTo(ix + iw * 0.3, iy + ih + 2);
  hill2.quadTo(ix + iw * 0.7, iy + ih * 0.56, ix + iw + 2, iy + ih * 0.74);
  hill2.lineTo(ix + iw + 2, iy + ih + 2);
  hill2.close();
  p.setColor(Skia.Color(pal[3]));
  c.drawPath(hill2.build(), p);
  c.restore();
  p.setColor(Skia.Color(INK));
  c.drawText('40', x + 6, y + h - 4, p, fonts.mark);
}

function postmark(c: SkCanvas, x: number, y: number, fonts: PlaneFonts) {
  const s = Skia.Paint();
  s.setAntiAlias(true);
  s.setStyle(PaintStyle.Stroke);
  s.setStrokeWidth(1.2);
  s.setColor(Skia.Color('rgba(52,62,96,0.55)'));
  c.drawCircle(x, y, 19, s);
  c.drawCircle(x, y, 14.5, s);
  for (let k = 0; k < 4; k += 1) {
    const wave = Skia.PathBuilder.Make();
    const wy = y - 9 + k * 6;
    wave.moveTo(x + 20, wy);
    for (let i = 1; i <= 8; i += 1) wave.quadTo(x + 20 + i * 7 - 3.5, wy + (i % 2 ? -2.5 : 2.5), x + 20 + i * 7, wy);
    c.drawPath(wave.build(), s);
  }
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color('rgba(52,62,96,0.6)'));
  const a = '25 OCT';
  c.drawText(a, x - textWidth(fonts.mark, a) / 2, y + 3, p, fonts.mark);
}

/** The atlas: 4 × SW wide, SH tall, at `scale` pixels per point. */
export function buildPaper(sw: number, sh: number, scale: number, fonts: PlaneFonts): SkImage {
  const AW = sw * 4;
  const surface = Skia.Surface.Make(Math.ceil(AW * scale), Math.ceil(sh * scale))!;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  for (let k = 0; k < 4; k += 1) {
    const x0 = k * sw;
    const back = k === 3;
    p.setShader(
      Skia.Shader.MakeLinearGradient({ x: x0, y: 0 }, { x: x0 + sw * 0.4, y: sh }, (back ? ['#F3ECDC', '#EDE4D0', '#E6DCC6'] : PAPER).map((v) => Skia.Color(v)), [0, 0.55, 1], 0),
    );
    c.drawRect(Skia.XYWHRect(x0, 0, sw, sh), p);
    p.setShader(null);
    if (back) continue;
    const copy = STEP_COPY[k];
    // Ruled lines and the margin.
    const line = Skia.Paint();
    line.setAntiAlias(true);
    line.setStyle(PaintStyle.Stroke);
    line.setStrokeWidth(0.7);
    line.setColor(Skia.Color('rgba(90,130,190,0.22)'));
    for (let y = 200; y < sh - 18; y += 24) c.drawLine(x0 + 14, y, x0 + sw - 14, y, line);
    line.setColor(Skia.Color('rgba(200,80,80,0.28)'));
    c.drawLine(x0 + 30, 0, x0 + 30, sh, line);
    stamp(c, x0 + sw - 66, 18, 48, 58, STAMPS[k], fonts);
    postmark(c, x0 + sw - 92, 74, fonts);
    // Copy, measured so it always fits the sheet.
    const ink = Skia.Paint();
    ink.setAntiAlias(true);
    ink.setColor(Skia.Color('#B4553E'));
    c.drawText(copy.kicker.toUpperCase(), x0 + 40, 118, ink, fonts.kicker);
    ink.setColor(Skia.Color(INK));
    c.drawText(copy.title, x0 + 40, 154, ink, fonts.title);
    const room = sw - 40 - 16;
    copy.lines.forEach((l, i) => {
      const w = textWidth(fonts.body, l);
      c.save();
      if (w > room) {
        c.translate(x0 + 40, 0);
        c.scale(room / w, 1);
        c.drawText(l, 0, 196 + i * 24, ink, fonts.body);
      } else c.drawText(l, x0 + 40, 196 + i * 24, ink, fonts.body);
      c.restore();
    });
    // A signature flourish at the foot of the copy.
    const sig = Skia.PathBuilder.Make();
    const sy = 196 + 3 * 24 + 18;
    sig.moveTo(x0 + 40, sy);
    sig.cubicTo(x0 + 56, sy - 18, x0 + 66, sy + 10, x0 + 80, sy - 4);
    sig.cubicTo(x0 + 92, sy - 14, x0 + 100, sy + 6, x0 + 118, sy - 2);
    const pen = Skia.Paint();
    pen.setAntiAlias(true);
    pen.setStyle(PaintStyle.Stroke);
    pen.setStrokeWidth(1.4);
    pen.setStrokeCap(StrokeCap.Round);
    pen.setColor(Skia.Color('rgba(43,47,69,0.7)'));
    c.drawPath(sig.build(), pen);
  }
  // Paper grain at 1 px per pt, upscaled.
  const gw = Math.ceil(AW);
  const gh = Math.ceil(sh);
  const grain = new Uint8Array(gw * gh * 4);
  let seed = 98765;
  for (let i = 0; i < gw * gh; i += 1) {
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    const v = seed / 0x7fffffff;
    const dark = v < 0.5;
    grain[i * 4] = dark ? 70 : 255;
    grain[i * 4 + 1] = dark ? 55 : 252;
    grain[i * 4 + 2] = dark ? 30 : 240;
    grain[i * 4 + 3] = Math.floor(Math.abs(v - 0.5) * 2 * 26);
  }
  const g = Skia.Image.MakeImage({ width: gw, height: gh, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul }, Skia.Data.fromBytes(grain), gw * 4);
  if (g) c.drawImageRectOptions(g, Skia.XYWHRect(0, 0, gw, gh), Skia.XYWHRect(0, 0, AW, sh), FilterMode.Nearest, MipmapMode.None, p);
  surface.flush();
  return surface.makeImageSnapshot();
}
