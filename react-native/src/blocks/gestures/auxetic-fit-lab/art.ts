import { ClipOp, FilterMode, MipmapMode, PaintStyle, Skia, StrokeCap, type SkCanvas, type SkColor, type SkImage } from '@shopify/react-native-skia';

import { center, geometry, pins } from './geometry';
import { handles, type Frame, type Point } from './motion';
import type { Resources } from './resources';

function color(hex: number, alpha = 1): SkColor { 'worklet'; return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, alpha); }
function image(c: SkCanvas, im: SkImage, x: number, y: number, w: number, h: number) { 'worklet'; c.drawImageRectOptions(im, Skia.XYWHRect(0, 0, im.width(), im.height()), Skia.XYWHRect(x, y, w, h), FilterMode.Linear, MipmapMode.None); }
function transform(c: SkCanvas, point: Point, angle: number, yOffset: number) { 'worklet'; const a = angle * Math.PI / 180, co = Math.cos(a), si = Math.sin(a); c.concat([co, -si, point.x, si, co, point.y + yOffset, 0, 0, 1]); }
function arrow(c: SkCanvas, start: Point, tip: Point, tint: SkColor) {
  'worklet'; const distance = Math.hypot(tip.x - start.x, tip.y - start.y); if (distance <= .01) return; const ux = (tip.x - start.x) / distance, uy = (tip.y - start.y) / distance, head = Math.min(4, distance), base = { x: tip.x - ux * head, y: tip.y - uy * head }, line = Skia.PathBuilder.Make().moveTo(start.x, start.y).lineTo(base.x, base.y).detach(), paint = Skia.Paint(); paint.setAntiAlias(true); paint.setColor(tint); paint.setStyle(PaintStyle.Stroke); paint.setStrokeWidth(.8); paint.setStrokeCap(StrokeCap.Round); c.drawPath(line, paint);
  const triangle = Skia.PathBuilder.Make().moveTo(tip.x, tip.y).lineTo(base.x - uy * 2.5, base.y + ux * 2.5).lineTo(base.x + uy * 2.5, base.y - ux * 2.5).close().detach(); paint.setStyle(PaintStyle.Fill); c.drawPath(triangle, paint);
}
function housing(c: SkCanvas, point: Point, scale: number) {
  'worklet'; const paint = Skia.Paint(); paint.setAntiAlias(true); paint.setColor(color(0x2B9794)); c.drawCircle(point.x, point.y, 11 * scale, paint); paint.setColor(color(0xFFFFFF)); paint.setStyle(PaintStyle.Stroke); paint.setStrokeWidth(1.5 * scale); paint.setStrokeCap(StrokeCap.Round); c.drawLine(point.x - 1.5 * scale, point.y, point.x + 1.5 * scale, point.y, paint);
}
/** Twelve rigid copies; the union ambient opacity is applied exactly once. */
export function draw(frame: Frame, art: Resources | null, height: number) {
  'worklet'; const recorder = Skia.PictureRecorder(), c = recorder.beginRecording(Skia.XYWHRect(0, 0, 393, height)), g = geometry(frame.angle), fill = Skia.Paint(); fill.setAntiAlias(true); fill.setColor(color(0xF6F8F4)); c.drawRect(Skia.XYWHRect(0, 0, 393, height), fill);
  if (art) {
    c.save(); c.clipRect(Skia.XYWHRect(0, 0, 393, height), ClipOp.Intersect, true); image(c, art.paper, 0, 0, 393, 1024); c.restore();
    const ambient = Skia.Paint(); ambient.setAlphaf(.06); c.saveLayer(ambient);
    for (let row = 0; row < 3; row++) for (let col = 0; col < 4; col++) { c.save(); transform(c, center(g, col, row), (col + row) % 2 === 0 ? frame.angle : -frame.angle, 7); image(c, art.ambientShadow, -64, -64, 128, 128); c.restore(); }
    c.restore();
    for (let row = 0; row < 3; row++) for (let col = 0; col < 4; col++) { c.save(); transform(c, center(g, col, row), (col + row) % 2 === 0 ? frame.angle : -frame.angle, 1); image(c, art.tightShadow, -32, -32, 64, 64); c.restore(); }
  }
  for (let row = 0; row < 3; row++) for (let col = 0; col < 4; col++) {
    c.save(); transform(c, center(g, col, row), (col + row) % 2 === 0 ? frame.angle : -frame.angle, 0);
    if (art) image(c, art.plate, -32, -32, 64, 64);
    else { fill.setColor(color(0xE1E6E2)); c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-24.75, -24.75, 49.5, 49.5), .7, .7), fill); }
    c.restore();
  }
  fill.setColor(color(0x2B9794)); for (const pin of pins(g)) c.drawCircle(pin.x, pin.y, 1.7, fill);
  const h = handles(frame), d = g.spacing, vertical = color(0x2B9794, .60 * frame.arrowGain), horizontal = color(0xB88655, .78 * frame.arrowGain);
  arrow(c, { x: 196.5, y: 399 - (1.5 * d + 7) }, { x: 196.5, y: Math.max(.5, 399 - (1.5 * d + 23)) }, vertical);
  arrow(c, { x: 196.5, y: 399 + (1.5 * d + 7) }, { x: 196.5, y: Math.min(851.5, 399 + (1.5 * d + 23)) }, vertical);
  const leftEdge = h.leftCenter.x - 11 * frame.left.scale, rightEdge = h.rightCenter.x + 11 * frame.right.scale;
  arrow(c, { x: Math.max(.5, leftEdge - 2), y: 399 }, { x: Math.max(.5, leftEdge - 15), y: 399 }, horizontal);
  arrow(c, { x: Math.min(392.5, rightEdge + 2), y: 399 }, { x: Math.min(392.5, rightEdge + 15), y: 399 }, horizontal);
  housing(c, h.leftCenter, frame.left.scale); housing(c, h.rightCenter, frame.right.scale); return recorder.finishRecordingAsPicture();
}
