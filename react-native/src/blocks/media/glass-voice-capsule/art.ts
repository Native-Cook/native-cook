import {
  BlendMode, BlurStyle, ClipOp, PaintStyle, Skia, StrokeCap, TileMode,
  type SkCanvas, type SkImage, type SkRect,
} from '@shopify/react-native-skia';

import { clamp, type Frame, LOOP } from './motion';

type Geometry = { width: number; height: number; cx: number; cy: number; angle: number };

function color(hex: number, alpha = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, clamp(alpha));
}
function fill(hex: number, alpha = 1, blur = 0) {
  'worklet';
  const p = Skia.Paint(); p.setAntiAlias(true); p.setColor(color(hex, alpha));
  if (blur > 0) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  return p;
}
function shape(r: SkRect, inset = 0) {
  'worklet';
  const rect = Skia.XYWHRect(r.x + inset, r.y + inset, r.width - inset * 2, r.height - inset * 2);
  return Skia.RRectXY(rect, rect.height / 2, rect.height / 2);
}
function sample(c: SkCanvas, image: SkImage, w: number, h: number, f: Frame, g: Geometry, zoom: number, localX: number, droplet: boolean, blur = 0) {
  'worklet';
  c.save();
  const extra = droplet ? f.droplet : 1, inv = 1 / Math.max(0.01, f.scale * extra);
  c.scale(inv, inv); c.rotate(-g.angle, 0, 0);
  const radians = g.angle * Math.PI / 180;
  const cx = g.cx + localX * Math.cos(radians) * f.scale, cy = g.cy + localX * Math.sin(radians) * f.scale;
  const cover = Math.max(w / 1024, h / 1536), iw = 1024 * cover, ih = 1536 * cover;
  const p = fill(0xFFFFFF);
  if (blur > 0) p.setImageFilter(Skia.ImageFilter.MakeBlur(blur, blur, TileMode.Clamp, null));
  c.drawImageRect(image, Skia.XYWHRect(0, 0, image.width(), image.height()),
    Skia.XYWHRect(((w - iw) / 2 - cx) * zoom, ((h - ih) / 2 - cy) * zoom, iw * zoom, ih * zoom), p);
  c.restore();
}
function glass(c: SkCanvas, image: SkImage, w: number, h: number, f: Frame, g: Geometry, rect: SkRect, zoom: number, droplet: boolean, localX: number) {
  'worklet';
  const outline = shape(rect);
  for (const [blur, dy, alpha] of [[22, 12, 0.30], [6, 3, 0.18]]) {
    c.save(); c.translate(0, dy); c.drawRRect(outline, fill(0x0B1830, alpha, blur)); c.restore();
  }
  c.save(); c.clipRRect(outline, ClipOp.Intersect, true);
  // The detail in the image itself is blurred; a blurred shape alone cannot make refraction.
  sample(c, image, w, h, f, g, zoom, localX, droplet, 1.0);
  // A second, more strongly magnified sample is revealed by a feathered nine-point rim mask.
  c.saveLayer(undefined, rect);
  sample(c, image, w, h, f, g, 1.42, localX, droplet);
  const mask = fill(0xFFFFFF); mask.setBlendMode(BlendMode.DstIn);
  c.saveLayer(mask, rect);
  const stroke = fill(0xFFFFFF, 1, 1.5); stroke.setStyle(PaintStyle.Stroke); stroke.setStrokeWidth(9);
  c.drawRRect(shape(rect, 4.5), stroke); c.restore(); c.restore();
  c.drawRRect(outline, fill(0xFFFFFF, 0.07));
  const sheen = fill(0xFFFFFF);
  sheen.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: rect.y }, { x: 0, y: rect.y + rect.height * 0.46 },
    [color(0xFFFFFF, 0.26), color(0xFFFFFF, 0)], null, TileMode.Clamp));
  c.drawRRect(outline, sheen);
  if (!droplet) {
    const r = clamp(f.amount), colors = [0xFF8A3D, 0xF23FA6, 0x3D8BFF];
    for (let i = 0; i < 3; i++) {
      const strength = i === 1 ? 1 : r, drift = 10 * Math.sin(4 * Math.PI * f.time / LOOP + i * 2.1) * r;
      const x = rect.x + rect.width * (0.22 + i * 0.28) + drift, glowH = 14 + 46 * f.level;
      c.drawOval(Skia.XYWHRect(x - rect.width * 0.21, rect.y + rect.height - 10 - glowH / 2, rect.width * 0.42, glowH),
        fill(colors[i], (0.25 + 0.65 * f.level) * strength, 20));
    }
    c.drawOval(Skia.XYWHRect(-rect.width * 0.21, rect.y + rect.height - 42, rect.width * 0.42, 40), fill(0x8E5BFF, 0.30 * f.level * r, 20));
    if (f.sheen >= 0) {
      const x = rect.x - 30 + (rect.width + 60) * f.sheen;
      c.drawRect(Skia.XYWHRect(x - 15, rect.y, 30, rect.height), fill(0xFFFFFF, 0.35 * Math.sin(Math.PI * f.sheen), 5));
    }
  }
  if (!droplet) {
    const inner = fill(0x0B1830, 0.18, 6); inner.setStyle(PaintStyle.Stroke); inner.setStrokeWidth(6);
    c.drawRRect(shape(Skia.XYWHRect(rect.x, rect.y - 3, rect.width, rect.height)), inner);
  }
  c.save(); c.translate(rect.x + 26, rect.y + 13); c.rotate(-18, 0, 0);
  c.drawOval(Skia.XYWHRect(-10, -3.5, 20, 7), fill(0xFFFFFF, 0.75, 1)); c.restore();
  c.drawOval(Skia.XYWHRect(rect.x + rect.width - 36, rect.y + rect.height - 14.5, 12, 5), fill(0xFFFFFF, 0.35));
  c.restore();
  const rim = fill(0xFFFFFF); rim.setStyle(PaintStyle.Stroke); rim.setStrokeWidth(1.5);
  rim.setShader(Skia.Shader.MakeLinearGradient({ x: rect.x, y: rect.y }, { x: rect.x + rect.width, y: rect.y + rect.height },
    [color(0xFFFFFF, 0.88), color(0xFFFFFF, 0.12), color(0xFFFFFF, 0.46)], [0, 0.5, 1], TileMode.Clamp));
  c.drawRRect(shape(rect, 0.75), rim);
  const hairline = fill(0xFFFFFF, 0.22); hairline.setStyle(PaintStyle.Stroke); hairline.setStrokeWidth(0.75);
  c.drawRRect(shape(rect, 2), hairline);
}
function mic(c: SkCanvas, red: number) {
  'worklet';
  const ink = fill(0xFFFFFF); ink.setColor(Float32Array.of(1, 1 + (77 / 255 - 1) * red, 1 + (94 / 255 - 1) * red, 1));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-5, -14, 10, 18), 5, 5), ink);
  const p = Skia.Path.Make(); p.moveTo(-9, -3); p.lineTo(-9, 0); p.cubicTo(-9, 12, 9, 12, 9, 0);
  p.lineTo(9, -3); p.moveTo(0, 9); p.lineTo(0, 14); p.moveTo(-5, 14); p.lineTo(5, 14);
  ink.setStyle(PaintStyle.Stroke); ink.setStrokeWidth(2); ink.setStrokeCap(StrokeCap.Round); c.drawPath(p, ink);
}
export function drawGlassVoiceCapsule(c: SkCanvas, image: SkImage, w: number, h: number, f: Frame, g: Geometry) {
  'worklet';
  c.save(); c.translate(g.cx, g.cy); c.rotate(g.angle, 0, 0); c.scale(f.scale, f.scale);
  glass(c, image, w, h, f, g, Skia.XYWHRect(-g.width / 2, -g.height / 2, g.width, g.height), 1.30, false, 0);
  c.save(); c.translate(-109 * f.amount, 0); c.scale(f.micPulse, f.micPulse); mic(c, f.micRed); c.restore();
  c.restore();
}
/** This layer sits above the native timer, matching Swift's overlap during the reverse morph. */
export function drawGlassVoiceCapsuleDroplet(c: SkCanvas, image: SkImage, w: number, h: number, f: Frame, g: Geometry) {
  'worklet';
  if (f.content > 0.001) {
    c.save(); c.translate(g.cx, g.cy); c.rotate(g.angle, 0, 0); c.scale(f.scale, f.scale);
    const opacity = fill(0xFFFFFF, f.content);
    c.saveLayer(opacity);
    const x = g.width / 2 - 50;
    c.translate(x, 0); c.scale(f.droplet, f.droplet);
    glass(c, image, w, h, f, g, Skia.XYWHRect(-34, -34, 68, 68), 1.28, true, x);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-12, -12, 24, 24), 7, 7), fill(0xFFFFFF));
    c.restore();
    c.restore();
  }
}
