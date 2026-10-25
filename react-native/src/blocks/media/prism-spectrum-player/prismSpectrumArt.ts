import { BlurStyle, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkFont } from '@shopify/react-native-skia';

import { type Geo, type Pose, TRACK_LENGTH, formatTime } from './prismSpectrumMotion';

/*
 The player's controls, drawn in the same Canvas as the hero. The static ones (header icons,
 heart, previous / next / forward) are recorded once into a Picture; the moving ones (scrubber,
 times, back-5 press, play button with its play ↔ pause morph) are recorded every frame from the
 pose, which is a handful of draws.
 */

export type Fonts = { mono: SkFont; small: SkFont };

const INK = '#EEEAF8';
const INK_DIM = 'rgba(238,234,248,0.55)';
const TRACK = 'rgba(238,234,248,0.16)';

function stroke(color: string, width: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  p.setColor(Skia.Color(color));
  return p;
}

function fill(color: string) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  return p;
}

function textWidth(font: SkFont, text: string) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (let i = 0; i < widths.length; i += 1) sum += widths[i];
  return sum;
}

/** A skip icon: a bar and a triangle, pointing left (dir −1) or right (dir 1). */
function skip(c: SkCanvas, x: number, y: number, dir: number, s: number) {
  'worklet';
  const p = Skia.PathBuilder.Make();
  p.moveTo(x - dir * 7 * s, y - 9 * s);
  p.lineTo(x + dir * 6 * s, y);
  p.lineTo(x - dir * 7 * s, y + 9 * s);
  p.close();
  const paint = fill(INK);
  c.drawPath(p.build(), paint);
  c.drawPath(p.build(), stroke(INK, 2.2 * s));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + dir * 8.5 * s - 1.6 * s, y - 9 * s, 3.2 * s, 18 * s), 1.6 * s, 1.6 * s), paint);
}

/** A circular "5 seconds" arrow: back (dir −1) or forward (dir 1). */
function jump(c: SkCanvas, x: number, y: number, dir: number, s: number, font: SkFont, scale: number) {
  'worklet';
  c.save();
  c.translate(x, y);
  c.scale(scale, scale);
  const r = 12 * s;
  // The ring is open at the top, on the side the arrow points to.
  const start = dir < 0 ? -110 : -70;
  const sweep = dir < 0 ? 300 : -300;
  const arc = Skia.PathBuilder.Make();
  arc.addArc(Skia.XYWHRect(-r, -r, 2 * r, 2 * r), start, sweep);
  c.drawPath(arc.build(), stroke(INK, 2 * s));
  const a = (start * Math.PI) / 180;
  const px = Math.cos(a) * r;
  const py = Math.sin(a) * r;
  // Tangent against the sweep: the head points where the ring was opened.
  const tx = dir < 0 ? Math.sin(a) : -Math.sin(a);
  const ty = dir < 0 ? -Math.cos(a) : Math.cos(a);
  const head = Skia.PathBuilder.Make();
  head.moveTo(px + tx * 5.5 * s, py + ty * 5.5 * s);
  head.lineTo(px - ty * 4 * s - tx * 0.5 * s, py + tx * 4 * s - ty * 0.5 * s);
  head.lineTo(px + ty * 4 * s - tx * 0.5 * s, py - tx * 4 * s - ty * 0.5 * s);
  head.close();
  c.drawPath(head.build(), fill(INK));
  c.drawText('5', -textWidth(font, '5') / 2, 3.6 * s, fill(INK), font);
  c.restore();
}

/** Heart outline. */
function heart(c: SkCanvas, x: number, y: number, s: number) {
  'worklet';
  const p = Skia.PathBuilder.Make();
  p.moveTo(x, y + 8 * s);
  p.cubicTo(x - 12 * s, y, x - 10.5 * s, y - 9.5 * s, x - 5 * s, y - 9 * s);
  p.cubicTo(x - 2.2 * s, y - 8.8 * s, x - 0.6 * s, y - 6.8 * s, x, y - 5.2 * s);
  p.cubicTo(x + 0.6 * s, y - 6.8 * s, x + 2.2 * s, y - 8.8 * s, x + 5 * s, y - 9 * s);
  p.cubicTo(x + 10.5 * s, y - 9.5 * s, x + 12 * s, y, x, y + 8 * s);
  p.close();
  c.drawPath(p.build(), stroke(INK, 1.8 * s));
}

/** Button centres along the controls row. */
export function controlX(g: Geo, i: number) {
  'worklet';
  const step = Math.min(70, g.w * 0.178);
  return g.w / 2 + (i - 2) * step * (i === 0 || i === 4 ? 0.98 : 1);
}

export const PLAY_R = 34;

/** Everything that never moves: header icons, heart, previous / next / forward 5, queue icon. */
export function drawStatic(c: SkCanvas, g: Geo, top: number, bottom: number, fonts: Fonts) {
  'worklet';
  const s = Math.max(0.85, Math.min(1, g.u));
  // Header: chevron down on the left, ellipsis on the right.
  const hy = top + 24;
  const chev = Skia.PathBuilder.Make();
  chev.moveTo(24, hy - 3.5);
  chev.lineTo(31, hy + 3.5);
  chev.lineTo(38, hy - 3.5);
  c.drawPath(chev.build(), stroke(INK, 2.2));
  for (let i = 0; i < 3; i += 1) c.drawCircle(g.w - 38 + i * 7, hy, 1.8, fill(INK));
  heart(c, g.w - 36, g.titleTop + 20, 1);
  skip(c, controlX(g, 1), g.controlsY, -1, s);
  skip(c, controlX(g, 3), g.controlsY, 1, s);
  jump(c, controlX(g, 4), g.controlsY, 1, s, fonts.small, 1);
  // The "up next" card: a glassy row with a small cover (a spectrum in a dark square).
  const cardY = g.h - bottom - 76;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(16, cardY, g.w - 32, 64), 18, 18), fill('rgba(238,234,248,0.06)'));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(16.5, cardY + 0.5, g.w - 33, 63), 17.5, 17.5), stroke('rgba(238,234,248,0.10)', 1));
  const cover = Skia.RRectXY(Skia.XYWHRect(26, cardY + 10, 44, 44), 10, 10);
  const coverPaint = fill('#1A1838');
  coverPaint.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(26, cardY + 10), Skia.Point(70, cardY + 54), ['#2B2160', '#151530', '#0E1A2E'].map((h) => Skia.Color(h)), [0, 0.5, 1], TileMode.Clamp),
  );
  c.drawRRect(cover, coverPaint);
  const bands = ['#ff6b8b', '#ffb36b', '#f6ef7a', '#7af0a6', '#6bc8ff', '#a18bff'];
  for (let i = 0; i < bands.length; i += 1) {
    const b = Skia.PathBuilder.Make();
    b.moveTo(34, cardY + 34);
    b.lineTo(66, cardY + 22 + i * 4.2);
    c.drawPath(b.build(), stroke(bands[i], 2));
  }
  // Queue icon on the card.
  const qy = cardY + 32;
  for (let i = 0; i < 3; i += 1) {
    c.drawCircle(g.w - 44, qy - 6 + i * 6, 1.4, fill(INK_DIM));
    const line = Skia.PathBuilder.Make();
    line.moveTo(g.w - 39, qy - 6 + i * 6);
    line.lineTo(g.w - 26, qy - 6 + i * 6);
    c.drawPath(line.build(), stroke(INK_DIM, 1.6));
  }
}

/** Play ↔ pause: two quads, each vertex moving from the play triangle's half to a pause bar. */
function playPause(c: SkCanvas, x: number, y: number, m: number, s: number) {
  'worklet';
  const play = [
    [-7, -11, 1.5, -5.5, 1.5, 5.5, -7, 11],
    [1.5, -5.5, 10, 0, 10, 0, 1.5, 5.5],
  ];
  const pause = [
    [-9, -10, -3, -10, -3, 10, -9, 10],
    [3, -10, 9, -10, 9, 10, 3, 10],
  ];
  const paint = fill('#1B1830');
  const edge = stroke('#1B1830', 2.4 * s);
  for (let q = 0; q < 2; q += 1) {
    const p = Skia.PathBuilder.Make();
    for (let v = 0; v < 4; v += 1) {
      const ax = play[q][v * 2] + 1.5;
      const ay = play[q][v * 2 + 1];
      const bx = pause[q][v * 2];
      const by = pause[q][v * 2 + 1];
      const px = x + (ax + (bx - ax) * m) * s;
      const py = y + (ay + (by - ay) * m) * s;
      if (v === 0) p.moveTo(px, py);
      else p.lineTo(px, py);
    }
    p.close();
    c.drawPath(p.build(), paint);
    c.drawPath(p.build(), edge);
  }
}

/** Scrubber, times, back-5 and the play button, from the pose. */
export function drawDynamic(c: SkCanvas, g: Geo, pose: Pose, pressPlay: number, fonts: Fonts) {
  'worklet';
  const s = Math.max(0.85, Math.min(1, g.u));
  // Scrubber.
  const x0 = 24;
  const x1 = g.w - 24;
  const frac = Math.min(1, Math.max(0, pose.elapsed / TRACK_LENGTH));
  const kx = x0 + (x1 - x0) * frac;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0, g.scrubY - 2, x1 - x0, 4), 2, 2), fill(TRACK));
  const done = fill(INK);
  done.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(x0, 0),
      Skia.Point(kx, 0),
      [Skia.Color('rgba(196,186,255,0.75)'), Skia.Color('rgba(238,234,248,0.95)')],
      null,
      TileMode.Clamp,
    ),
  );
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x0, g.scrubY - 2, Math.max(4, kx - x0), 4), 2, 2), done);
  const knobShadow = fill('rgba(6,6,24,0.45)');
  knobShadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.5, true));
  c.drawCircle(kx, g.scrubY + 1.5, 6.5, knobShadow);
  c.drawCircle(kx, g.scrubY, 6.5, fill('#F4F1FC'));
  const elapsed = Math.floor(pose.elapsed);
  const left = formatTime(elapsed);
  const right = `-${formatTime(TRACK_LENGTH - elapsed)}`;
  const ty = g.scrubY + 24;
  c.drawText(left, x0, ty, fill(INK_DIM), fonts.mono);
  c.drawText(right, x1 - textWidth(fonts.mono, right), ty, fill(INK_DIM), fonts.mono);

  // Back 5 s, which dips when pressed.
  jump(c, controlX(g, 0), g.controlsY, -1, s, fonts.small, 1 - 0.1 * pose.pressBack);

  // The play button: two shadows, a three-stop face, a top edge highlight, a rainbow rim while playing.
  const px = g.w / 2;
  const py = g.controlsY;
  const r = PLAY_R * s * (1 - 0.08 * pressPlay);
  const ambient = fill('rgba(70,58,170,0.42)');
  ambient.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 14, true));
  c.drawCircle(px, py + 10 * (1 - 0.5 * pressPlay), r * 0.92, ambient);
  const contact = fill('rgba(6,6,24,0.5)');
  contact.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
  c.drawCircle(px, py + 1.5, r, contact);
  if (pose.play > 0.01) {
    const ring = stroke('#ffffff', 2.4);
    ring.setShader(
      Skia.Shader.MakeSweepGradient(
        px,
        py,
        ['#ff6b8b', '#ffb36b', '#f6ef7a', '#7af0a6', '#6bc8ff', '#a18bff', '#ff6b8b'].map((h) => Skia.Color(h)),
        null,
        TileMode.Clamp,
      ),
    );
    ring.setAlphaf(0.55 * pose.play);
    ring.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, false));
    c.drawCircle(px, py, r + 3, ring);
  }
  const face = fill('#E8E4F6');
  face.setShader(
    Skia.Shader.MakeLinearGradient(
      Skia.Point(px, py - r),
      Skia.Point(px, py + r),
      [Skia.Color('#F7F5FD'), Skia.Color('#DCD7EF'), Skia.Color('#BDB5DC')],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  c.drawCircle(px, py, r, face);
  c.drawCircle(px, py, r - 0.6, stroke('rgba(255,255,255,0.7)', 1.2));
  playPause(c, px, py, pose.icon, s * (1 - 0.08 * pressPlay));
}
