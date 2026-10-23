import { ClipOp, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode, matchFont, type SkCanvas, type SkColor, type SkFont, type SkPaint, type SkPath, type SkPicture } from '@shopify/react-native-skia';

import { MOODS, blend, clamp, ease, itemWidth, menuTrack, mix, nearest, radius, segment, title, type Shown } from './mood-model';
import { ROOM_PATH } from './room-path';

const PAPER = 0xECE7DB, INK = 0x211F1D;
type Fonts = Record<string, SkFont>;
export type Art = { room: SkPicture; fonts: Fonts };
function color(hex: number, alpha = 1): SkColor { 'worklet'; return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, alpha); }
function paint(hex: number, alpha = 1): SkPaint { 'worklet'; const p = Skia.Paint(); p.setAntiAlias(true); p.setColor(color(hex, alpha)); return p; }
function rect(c: SkCanvas, x: number, y: number, w: number, h: number, hex: number, r = 0, alpha = 1) { 'worklet'; c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r), paint(hex, alpha)); }
function oval(c: SkCanvas, x: number, y: number, w: number, h: number, hex: number, alpha = 1) { 'worklet'; c.drawOval(Skia.XYWHRect(x, y, w, h), paint(hex, alpha)); }
function stroke(c: SkCanvas, path: SkPath, hex = 0x8F8A7F, width = 1, alpha = 1) {
  'worklet'; const p = paint(hex, alpha); p.setStyle(PaintStyle.Stroke); p.setStrokeWidth(width); p.setStrokeCap(StrokeCap.Round); p.setStrokeJoin(StrokeJoin.Round); c.drawPath(path, p);
}
function label(c: SkCanvas, fonts: Fonts, text: string, x: number, y: number, size: number, weight = 400, hex = 0x211F1D, tracking = 0, alpha = 1) {
  'worklet'; if (!text) return;
  const font = fonts[`${size}-${weight}`], glyphs = font.getGlyphIDs(text), widths = font.getGlyphWidths(glyphs), metrics = font.getMetrics();
  const width = widths.reduce((a, b) => a + b, 0) + Math.max(0, glyphs.length - 1) * tracking;
  let left = 0;
  const positions = widths.map(advance => { const point = { x: left, y: 0 }; left += advance + tracking; return point; });
  c.drawGlyphs(glyphs, positions, x - width / 2, y - (metrics.ascent + metrics.descent) / 2, font, paint(hex, alpha));
}
// Source eye spacing, eye y/diameter, mouth half-width/y/depth/stroke and tiredness.
const FACES = [[10.4, -1.5, 7.2, 10.9, 12, -11, 3.2, 0], [9.3, -3, 6.2, 10, 10, -4, 2.3, 1], [9, -5, 6.2, 6.8, 4.8, -1.3, 2.5, 0], [9, -5.3, 6.2, 7, 4.5, 0, 2.5, 0], [8.9, -3, 6.2, 6.5, 6.8, 3, 2.5, 0], [8.2, -5.4, 5.7, 7.8, 5.5, 8.2, 2.5, 0], [7.8, -4.5, 5.6, 8.6, 6.3, 8.5, 2.4, 0]];
function faceMetric(m: number, feature: number) {
  'worklet'; const value = clamp(m), i = Math.floor(value);
  return mix(FACES[i][feature], FACES[Math.min(i + 1, 6)][feature], ease(value - i));
}
function blob(c: SkCanvas, x: number, y: number, r: number, m: number, alpha = 1) {
  'worklet'; const b = blend(m), p = paint(0xffffff), s = r / 72;
  const channel = (shift: number) => { 'worklet'; return mix((b.from.color >> shift) & 255, (b.to.color >> shift) & 255, b.f) / 255; };
  p.setColor(Float32Array.of(channel(16), channel(8), channel(0), alpha)); c.drawOval(Skia.XYWHRect(x - r, y - r, r * 2, r * 2), p);
  const path = Skia.PathBuilder.Make();
  for (let i = 0; i < 120; i++) {
    const a = i / 120 * 2 * Math.PI, rr = mix(radius(b.from.radii, a), radius(b.to.radii, a), b.f) * 44 * s;
    if (i === 0) path.moveTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr); else path.lineTo(x + Math.cos(a) * rr, y + Math.sin(a) * rr);
  }
  c.drawPath(path.close().detach(), paint(0xffffff, alpha));
  const tired = faceMetric(m, 7), eyeHalf = faceMetric(m, 0), eyeY = faceMetric(m, 1), eyeSize = faceMetric(m, 2);
  for (let side = -1; side <= 1; side += 2) {
    oval(c, x + (side * eyeHalf - eyeSize / 2) * s, y + (eyeY - eyeSize / 2) * s, eyeSize * s, eyeSize * s, INK, alpha);
    if (tired > 0.01) stroke(c, Skia.PathBuilder.Make().moveTo(x + (side * eyeHalf - 4) * s, y + 3 * s).quadTo(x + side * eyeHalf * s, y + 1.5 * s, x + (side * eyeHalf + 4) * s, y + 3 * s).detach(), INK, 1.5 * s, tired * 0.4 * alpha);
  }
  const half = faceMetric(m, 3), my = faceMetric(m, 4), depth = faceMetric(m, 5);
  stroke(c, Skia.PathBuilder.Make().moveTo(x - half * s, y + my * s).quadTo(x, y + (my + depth) * s, x + half * s, y + my * s).detach(), INK, faceMetric(m, 6) * s, (1 - tired * 0.3) * alpha);
}

function icon(c: SkCanvas, index: number, x: number, y: number, hex: number, alpha = 1) {
  'worklet'; const p = Skia.PathBuilder.Make();
  if (index === 0) {
    [11, 7, 3].forEach(r => p.addOval(Skia.XYWHRect(x - r, y - r, r * 2, r * 2)));
  } else if (index === 1) {
    p.moveTo(x - 12, y - 10).lineTo(x, y - 7).lineTo(x + 12, y - 10).lineTo(x + 12, y + 10).lineTo(x, y + 12).lineTo(x - 12, y + 10).close().moveTo(x, y - 7).lineTo(x, y + 12);
  } else if (index === 2) {
    p.addOval(Skia.XYWHRect(x - 11, y - 11, 22, 22));
    [-5, 4].forEach(offset => p.moveTo(x - 10, y + offset).quadTo(x, y + offset - 5, x + 10, y + offset + 2));
  } else if (index === 3) {
    for (let i = 0; i < 4; i++) { const a = i * Math.PI / 4; p.moveTo(x - 13 * Math.cos(a), y - 13 * Math.sin(a)).lineTo(x + 13 * Math.cos(a), y + 13 * Math.sin(a)); }
  } else {
    p.arcToOval(Skia.XYWHRect(x - 11, y - 11, 22, 22), -50, 280, true).quadTo(x + 1, y + 4, x + 7, y - 8).close().addOval(Skia.XYWHRect(x + 4, y - 7, 2, 2));
  }
  stroke(c, p.detach(), hex, 1.5, alpha);
}
function drawRoom(c: SkCanvas, fonts: Fonts, line: SkPath) {
  rect(c, 0, 0, 393, 852, PAPER); rect(c, 0, -20, 103, 215, 0x9ABBF5); rect(c, 114, -20, 142, 215, 0x9ABBF5);
  oval(c, 40, 69, 42, 42, 0xF1F8FA); oval(c, 167, 113, 14, 14, 0xFFF24A); stroke(c, line);
  const clock = Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(15, 355, 288, 207), 34, 34)).detach();
  c.drawPath(clock, paint(0xE9E5DB)); stroke(c, clock); rect(c, 28, 371, 260, 180, 0x242323, 24);
  oval(c, 47, 389, 9, 9, 0xF3EED9); oval(c, 50, 387, 8, 8, 0x242323);
  label(c, fonts, 'PM', 68, 394, 12, 500, 0xF3EED9); label(c, fonts, 'Tuesday', 242, 394, 12, 500, 0xF3EED9);
  [1, 3, 1, 4].forEach((digit, i) => {
    c.save(); c.translate([43, 96, 165, 218][i] + 23.5, 468); c.rotate([-3, 1, -4, 2][i], 0, 0);
    rect(c, -23.5, -50, 47, 100, 0xE6E6D8, 2);
    const g = Skia.PathBuilder.Make();
    if (digit === 1) g.moveTo(-10, -25).quadTo(2, -45, 1, -34).lineTo(-2, 34);
    else if (digit === 3) g.moveTo(-13, -28).cubicTo(12, -48, 18, -13, 5, -1).cubicTo(28, 14, 5, 48, -13, 31);
    else g.moveTo(1, -35).lineTo(-12, 9).lineTo(15, 9).moveTo(9, -21).lineTo(7, 34);
    stroke(c, g.detach(), 0x242323, 6); stroke(c, Skia.PathBuilder.Make().moveTo(-23.5, 0).lineTo(23.5, 0).detach(), 0x242323); c.restore();
  });
  const handle = Skia.PathBuilder.Make().moveTo(91, 330).lineTo(99, 317).lineTo(177, 317).lineTo(189, 331).close().detach(); c.drawPath(handle, paint(PAPER)); stroke(c, handle);
  const power = Skia.PathBuilder.Make().arcToOval(Skia.XYWHRect(32, 779, 20, 20), -55, 290, true).moveTo(42, 777).lineTo(42, 787).detach(); stroke(c, power, INK, 2);
  const sliders = Skia.PathBuilder.Make();
  for (let i = 0; i < 3; i++) { const y = 781 + i * 8; sliders.moveTo(341, y).lineTo(366, y).addOval(Skia.XYWHRect(346 + ((i + 1) % 2) * 9, y - 2.5, 5, 5)); }
  stroke(c, sliders.detach(), INK, 1.5);
}
export function makeArt(): Art {
  const fonts: Fonts = {};
  const specs = [[10, '600'], [11, '400'], [12, '500'], [12, '600'], [13, '600'], [14, '700'], [19, '700']] as const;
  specs.forEach(([size, weight]) => {
    const font = matchFont({ fontFamily: process.env.EXPO_OS === 'ios' ? 'Helvetica' : 'sans-serif', fontSize: size, fontWeight: weight });
    font.setLinearMetrics(true); fonts[`${size}-${weight}`] = font;
  });
  const recorder = Skia.PictureRecorder(), canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, 852));
  const line = Skia.Path.MakeFromSVGString(ROOM_PATH); if (line) drawRoom(canvas, fonts, line);
  return { room: recorder.finishRecordingAsPicture(), fonts };
}
function inline(c: SkCanvas, fonts: Fonts, m: number, origin: number, alpha: number) {
  'worklet'; blob(c, mix(247 - itemWidth(m) / 2, 171, origin), mix(686, 790, origin), mix(17, 13, origin), m, alpha);
  label(c, fonts, MOODS[nearest(m)].name, mix(216, 220, origin), mix(686, 790, origin), origin > 0.5 ? 10 : 13, 600, origin > 0.5 ? INK : 0x77746E, 0, alpha);
  const arrowX = 168 + itemWidth(m) / 2;
  stroke(c, Skia.PathBuilder.Make().moveTo(arrowX - 5, 682).lineTo(arrowX, 687).lineTo(arrowX + 5, 682).detach(), 0xAAA69D, 1.5, alpha * (1 - origin));
}
function morphLabel(c: SkCanvas, fonts: Fonts, text: string, x: number, y: number, size: number, alpha: number) {
  'worklet'; const scale = size / 19;
  c.save(); c.translate(x, y); c.scale(scale, scale); label(c, fonts, text, 0, 0, 19, 700, INK, 0.2 / scale, alpha); c.restore();
}
function picker(c: SkCanvas, art: Art, s: Shown, p: number, reduced: boolean, alpha: number, bodyAlpha = 1) {
  'worklet'; if (alpha <= 0) return;
  const origin = s.closing ? 0 : s.origin;
  const x = mix(mix(196.5 - itemWidth(s.m) / 2, 127.5, origin), 10.2, p), y = mix(mix(652, 765, origin), 418, p);
  const w = mix(mix(itemWidth(s.m), 138, origin), 372.6, p), h = mix(mix(68, 50, origin), 306, p), r = Math.min(h / 2, 56);
  const content = ease(segment(p, 0.35, 0.96)), q = 1 - Math.pow(1 - p, 3);
  c.save(); const bounds = Skia.PathBuilder.Make().addRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r)).detach();
  c.clipPath(bounds, ClipOp.Intersect, true); c.drawPath(bounds, paint(0xffffff, alpha * bodyAlpha));
  inline(c, art.fonts, s.m, origin, alpha * (1 - content));
  const details = alpha * content;
  rect(c, x, y + h - 68, w, 68, 0xFAF7F5, 0, details * bodyAlpha);
  blob(c, mix(mix(247 - itemWidth(s.m) / 2, 171, origin), 196.5, q), mix(mix(686, 790, origin), 563, q), mix(mix(17, 13, origin), 72, q), s.m, details);
  oval(c, 54 + s.m * 47.8 - 14 * content, y + h - 35 - 14 * content, 28 * content, 28 * content, 0xffffff, details); c.restore();
  // The source title remains above the growing card; face and footer retain the rounded clip.
  morphLabel(c, art.fonts, title(s.m, Math.floor(s.clock * 24), true, reduced), 198.5, 464, 19, details * 0.18);
  morphLabel(c, art.fonts, title(s.m, Math.floor(s.clock * 24), false, reduced), 196.5, 459, 19, details);

}
export function drawStage(c: SkCanvas, art: Art, s: Shown, reduced: boolean) {
  'worklet'; c.drawPicture(art.room);
  // GraphicsContext.opacity affects each Swift primitive, including overlapping face layers.
  // A saveLayer alpha would instead fade an already-composited capsule or face as one image.
  const roomAlpha = (1 - s.chooser) * (s.origin > 0.5 && s.panel > 0 ? 0 : 1), chooserAlpha = s.chooser;
  rect(c, 127.5, 765, 138, 50, 0xffffff, 25, roomAlpha);
  blob(c, 171, 790, 13, s.m, roomAlpha);
  label(c, art.fonts, MOODS[nearest(s.m)].name, 220, 790, 10, 600, INK, 0, roomAlpha);
  const wash = paint(0xffffff, chooserAlpha); wash.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: 852 }, [color(PAPER, 0.45), color(PAPER), color(PAPER)], [0, 0.18, 1], TileMode.Clamp)); c.drawRect(Skia.XYWHRect(0, 0, 393, 852), wash);
  const menuAlpha = chooserAlpha * 0.58 * (1 - s.panel * 0.4);
  const widths = [183.4, 209.6, 181.9, 186.3, 177.6], firstY = menuTrack(s.panel, 158.7, 186.3, 215.4);
  const spacing = menuTrack(s.panel, 89, 67.15, 36.2), scale = menuTrack(s.panel, 1, 0.96, 0.55);
  for (let i = 0; i < 5; i++) {
    c.save(); c.translate(196.5, firstY + i * spacing); c.scale(scale, scale);
    rect(c, -widths[i] / 2, -33.5, widths[i], 67, 0xffffff, 33.5, 0.65 * menuAlpha);
    icon(c, i, -45.5, 0, [0x7EC9D2, 0xEACD73, 0xDB92B5, 0xA5A0E0, 0xB59ACF][i], menuAlpha);
    label(c, art.fonts, ['FOCUS', 'STUDYING', 'RELAX', 'WORK', 'SLEEP'][i], 21.5, 0, 12, 600, 0xAAA69C, 0.2, menuAlpha); c.restore();
  }
  const questionAlpha = chooserAlpha * (1 - 0.9 * ease(segment(s.panel, 0.355, 0.8)));
  label(c, art.fonts, 'HOW ARE YOU FEELING?', 196.5, menuTrack(s.panel, 599, 507, 374), 14, 700, INK, 0, questionAlpha);
  label(c, art.fonts, 'Automatically match sounds based on mood', 196.5, menuTrack(s.panel, 624, 531, 396), 11, 400, 0x99958A, 0, questionAlpha);
  oval(c, 172.5, 760, 48, 48, 0xE7E1D5, 0.6 * chooserAlpha);
  stroke(c, Skia.PathBuilder.Make().moveTo(189.5, 777).lineTo(203.5, 791).moveTo(203.5, 777).lineTo(189.5, 791).detach(), INK, 1.5, chooserAlpha);
  if (s.closing) {
    const body = ease(segment(s.panel, 0.15, 1)), itemAlpha = chooserAlpha * (1 - body);
    rect(c, 196.5 - itemWidth(s.m) / 2, 652, itemWidth(s.m), 68, 0xffffff, 34, itemAlpha); inline(c, art.fonts, s.m, 0, itemAlpha);
    picker(c, art, s, 1, reduced, s.panel, s.panel > 0.001 ? body / s.panel : 0);
  } else picker(c, art, s, s.panel, reduced, s.origin > 0.5 && s.panel > 0 ? 1 : chooserAlpha);
}
