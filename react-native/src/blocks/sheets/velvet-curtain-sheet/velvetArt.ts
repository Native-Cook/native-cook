import { BlurStyle, PaintStyle, type SkCanvas, type SkFont, type SkPicture, Skia, StrokeCap } from '@shopify/react-native-skia';

import { PER_ROW, ROWS, SCREEN_Y, SEAT_H, SEAT_W, isTaken, seatAt, seatsWidth } from './velvetMotion';

/*
 Velvet Curtain Sheet — the static art, recorded once: the poster of "The Glass Orchard" (a dusk
 orchard of glass trees, drawn in code with seeded shapes) and the sheet's seat map (screen arc,
 eight rows of twelve seats with an aisle, row letters, legend).
*/

export const PAGE = '#140A0F';
export const INK = '#F3E9E4';
export const INK_SOFT = 'rgba(243,233,228,0.62)';
export const GOLD = '#F2C060';

function rnd(seed: number) {
  let s = seed;
  return () => {
    s = (s * 16807) % 2147483647;
    return s / 2147483647;
  };
}

function textWidth(font: SkFont, text: string) {
  let w = 0;
  for (const v of font.getGlyphWidths(font.getGlyphIDs(text))) w += v;
  return w;
}

function glassTree(c: SkCanvas, x: number, base: number, h: number, r: () => number) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  // Trunk and two limbs, tapered, in smoked glass.
  const trunk = Skia.PathBuilder.Make();
  trunk.moveTo(x - 3, base);
  trunk.quadTo(x - 1.5, base - h * 0.4, x - 0.8, base - h * 0.62);
  trunk.lineTo(x + 0.8, base - h * 0.62);
  trunk.quadTo(x + 1.5, base - h * 0.4, x + 3, base);
  trunk.close();
  p.setColor(Skia.Color('rgba(150,190,220,0.30)'));
  c.drawPath(trunk.build(), p);
  const limb = Skia.Paint();
  limb.setAntiAlias(true);
  limb.setStyle(PaintStyle.Stroke);
  limb.setStrokeCap(StrokeCap.Round);
  limb.setStrokeWidth(1.4);
  limb.setColor(Skia.Color('rgba(170,210,235,0.32)'));
  c.drawLine(x, base - h * 0.45, x - h * 0.2, base - h * 0.72, limb);
  c.drawLine(x, base - h * 0.5, x + h * 0.22, base - h * 0.78, limb);
  // Crown: overlapping glass discs, each with a cool rim and a bright glint.
  const cy = base - h * 0.78;
  for (let i = 0; i < 16; i += 1) {
    const a = r() * Math.PI * 2;
    const d = Math.sqrt(r()) * h * 0.3;
    const px = x + Math.cos(a) * d * 1.15;
    const py = cy + Math.sin(a) * d * 0.8;
    const rad = h * (0.07 + r() * 0.08);
    const pink = r() > 0.72;
    p.setShader(
      Skia.Shader.MakeRadialGradient(
        { x: px - rad * 0.3, y: py - rad * 0.35 },
        rad * 1.2,
        [Skia.Color(pink ? 'rgba(246,176,206,0.42)' : 'rgba(190,232,255,0.40)'), Skia.Color(pink ? 'rgba(180,90,150,0.10)' : 'rgba(90,140,210,0.08)'), Skia.Color('rgba(60,90,150,0.0)')],
        [0, 0.7, 1],
        0,
      ),
    );
    c.drawCircle(px, py, rad, p);
    p.setShader(null);
    limb.setStrokeWidth(0.8);
    limb.setColor(Skia.Color(pink ? 'rgba(255,200,225,0.35)' : 'rgba(210,240,255,0.35)'));
    c.drawCircle(px, py, rad, limb);
    p.setColor(Skia.Color('rgba(255,255,250,0.8)'));
    c.drawCircle(px - rad * 0.35, py - rad * 0.4, Math.max(0.6, rad * 0.12), p);
  }
}

export function recordPoster(width: number, height: number): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const p = Skia.Paint();
  p.setAntiAlias(true);
  const horizon = height * 0.66;
  p.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: horizon }, ['#0A1424', '#1C2A46', '#47324F', '#7A4A5C'].map((x) => Skia.Color(x)), [0, 0.45, 0.85, 1], 0),
  );
  c.drawRect(Skia.XYWHRect(0, 0, width, height), p);
  p.setShader(null);
  const r = rnd(11);
  // Stars.
  for (let i = 0; i < 70; i += 1) {
    p.setColor(Skia.Color(`rgba(240,232,220,${(0.25 + r() * 0.6).toFixed(2)})`));
    c.drawCircle(r() * width, r() * horizon * 0.7, 0.4 + r() * 0.9, p);
  }
  // Moon with its halo.
  const mx = width * 0.74;
  const my = height * 0.2;
  p.setColor(Skia.Color('rgba(246,226,196,0.22)'));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 26, true));
  c.drawCircle(mx, my, 44, p);
  p.setMaskFilter(null);
  p.setShader(Skia.Shader.MakeRadialGradient({ x: mx - 8, y: my - 8 }, 34, ['#FFF4DE', '#F1DDB8', '#C9AE8A'].map((x) => Skia.Color(x)), [0, 0.6, 1], 0));
  c.drawCircle(mx, my, 24, p);
  p.setShader(null);
  // Far hills, then the orchard on a low rise.
  const hills = Skia.PathBuilder.Make();
  hills.moveTo(0, horizon - 20);
  hills.cubicTo(width * 0.25, horizon - 48, width * 0.45, horizon - 10, width * 0.62, horizon - 30);
  hills.cubicTo(width * 0.8, horizon - 46, width * 0.92, horizon - 22, width, horizon - 30);
  hills.lineTo(width, height);
  hills.lineTo(0, height);
  hills.close();
  p.setColor(Skia.Color('#1A1830'));
  c.drawPath(hills.build(), p);
  const trees = [0.12, 0.3, 0.5, 0.68, 0.86];
  trees.forEach((tx, i) => glassTree(c, width * tx, horizon + 8 + (i % 2) * 10, 120 + (i === 2 ? 40 : (i % 2) * 18), r));
  // Low mist across the orchard.
  p.setColor(Skia.Color('rgba(214,196,230,0.12)'));
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
  c.drawRect(Skia.XYWHRect(-20, horizon - 14, width + 40, 36), p);
  p.setMaskFilter(null);
  const ground = Skia.PathBuilder.Make();
  ground.moveTo(0, horizon + 14);
  ground.quadTo(width * 0.5, horizon - 4, width, horizon + 16);
  ground.lineTo(width, height);
  ground.lineTo(0, height);
  ground.close();
  p.setColor(Skia.Color('#100A14'));
  c.drawPath(ground.build(), p);
  // Fade into the page so the title reads.
  p.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: height * 0.38 },
      { x: 0, y: height },
      [Skia.Color('rgba(20,10,15,0)'), Skia.Color('rgba(20,10,15,0.72)'), Skia.Color(PAGE)],
      [0, 0.55, 1],
      0,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, width, height), p);
  return rec.finishRecordingAsPicture();
}

export function recordSeats(width: number, pitch: number, label: SkFont): SkPicture {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, width, 900));
  const p = Skia.Paint();
  p.setAntiAlias(true);
  const x0 = (width - seatsWidth()) / 2;
  const x1 = x0 + seatsWidth();
  // The screen: a lit arc with its glow.
  const arc = Skia.PathBuilder.Make();
  arc.moveTo(x0 + 10, SCREEN_Y + 8);
  arc.quadTo(width / 2, SCREEN_Y - 6, x1 - 10, SCREEN_Y + 8);
  const s = Skia.Paint();
  s.setAntiAlias(true);
  s.setStyle(PaintStyle.Stroke);
  s.setStrokeCap(StrokeCap.Round);
  s.setStrokeWidth(8);
  s.setColor(Skia.Color('rgba(240,214,180,0.22)'));
  s.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 8, false));
  c.drawPath(arc.build(), s);
  s.setMaskFilter(null);
  s.setStrokeWidth(2.5);
  s.setColor(Skia.Color('#EBD6B8'));
  c.drawPath(arc.build(), s);
  p.setColor(Skia.Color(INK_SOFT));
  const tag = 'SCREEN';
  c.drawText(tag, width / 2 - textWidth(label, tag) / 2, SCREEN_Y + 22, p, label);
  // Seats.
  for (let i = 0; i < ROWS.length * PER_ROW; i += 1) {
    const [x, y] = seatAt(i, width, pitch);
    const taken = isTaken(i);
    const rr = Skia.RRectXY(Skia.XYWHRect(x, y, SEAT_W, SEAT_H), 5, 5);
    p.setShader(
      Skia.Shader.MakeLinearGradient(
        { x, y },
        { x, y: y + SEAT_H },
        (taken ? ['#2A1C24', '#1F141B', '#180F15'] : ['#5A3F4C', '#432E39', '#35232D']).map((v) => Skia.Color(v)),
        [0, 0.5, 1],
        0,
      ),
    );
    c.drawRRect(rr, p);
    p.setShader(null);
    // Backrest seam and a lit top edge.
    p.setColor(Skia.Color(taken ? 'rgba(0,0,0,0.25)' : 'rgba(10,4,8,0.35)'));
    c.drawRect(Skia.XYWHRect(x + 2, y + 7, SEAT_W - 4, 1), p);
    p.setColor(Skia.Color(taken ? 'rgba(255,230,230,0.05)' : 'rgba(255,225,230,0.18)'));
    c.drawRect(Skia.XYWHRect(x + 4, y + 1, SEAT_W - 8, 1), p);
  }
  p.setColor(Skia.Color('rgba(243,233,228,0.42)'));
  for (let r = 0; r < ROWS.length; r += 1) {
    const [, y] = seatAt(r * PER_ROW, width, pitch);
    const w = textWidth(label, ROWS[r]);
    c.drawText(ROWS[r], x0 - 16 - w / 2, y + SEAT_H - 5, p, label);
    c.drawText(ROWS[r], x1 + 16 - w / 2, y + SEAT_H - 5, p, label);
  }
  // Legend.
  const ly = seatAt(ROWS.length * PER_ROW - 1, width, pitch)[1] + SEAT_H + 18;
  const items: [string, string][] = [
    ['#4A3440', 'Available'],
    ['#22171E', 'Taken'],
    [GOLD, 'Selected'],
  ];
  let total = 0;
  for (const [, t] of items) total += 14 + textWidth(label, t) + 20;
  let lx = width / 2 - (total - 20) / 2;
  for (const [col, t] of items) {
    p.setColor(Skia.Color(col));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(lx, ly - 9, 10, 10), 3, 3), p);
    p.setColor(Skia.Color(INK_SOFT));
    c.drawText(t, lx + 14, ly, p, label);
    lx += 14 + textWidth(label, t) + 20;
  }
  return rec.finishRecordingAsPicture();
}
