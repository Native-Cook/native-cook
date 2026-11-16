import {
  BlurStyle, ClipOp, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode,
  type SkCanvas, type SkColor, type SkPath,
} from '@shopify/react-native-skia';

import { type Frame, type Layout, type Pose, out, radius, ramp } from './heatShapeModel';

function color(hex: number, alpha = 1): SkColor {
  'worklet';
  return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, alpha);
}

function paint(c: SkColor) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true); p.setColor(c);
  return p;
}

function stroke(canvas: SkCanvas, path: SkPath, c: SkColor, width: number, blur: number) {
  'worklet';
  const p = paint(c);
  p.setStyle(PaintStyle.Stroke); p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round); p.setStrokeJoin(StrokeJoin.Round);
  // The native mask filter used by Skia's declarative BlurMask component.
  if (blur > 0) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  canvas.drawPath(path, p);
}

export function outline(pose: Pose, cx: number, cy: number, scale: number, time: number | null, bulge = 0, direction = 0): SkPath {
  'worklet';
  const p = Skia.PathBuilder.Make();
  for (let j = 0; j < 240; j++) {
    const theta = j * 2 * Math.PI / 240;
    const delta = Math.atan2(Math.sin(theta - direction), Math.cos(theta - direction));
    const r = radius(theta, pose.p, pose.c, pose.k, pose.phi, time) * scale
      * (1 + 0.10 * bulge * Math.exp(-Math.pow(delta / 0.6, 2)));
    const x = cx + r * Math.sin(theta), y = cy - r * Math.cos(theta);
    if (j === 0) p.moveTo(x, y); else p.lineTo(x, y);
  }
  p.close();
  return p.detach();
}

export function drawBody(canvas: SkCanvas, frame: Frame, layout: Layout, reduced: boolean,
  dx: number, dy: number, grip: number, direction: number) {
  'worklet';
  const cx = layout.cx + (reduced ? 0 : dx), cy = layout.cy + (reduced ? 0 : dy);
  const scale = layout.sx * (reduced ? 1 : 1 + 0.08 * grip);
  for (const layer of frame.layers) {
    if (layer.opacity <= 0.001) continue;
    const pose = layer.pose, alpha = layer.opacity;
    // One 240-point path is shared by halo, bloom, fill, clipped volume, and rim.
    const path = outline(pose, cx, cy, scale, reduced ? null : frame.time, reduced ? 0 : grip, direction);
    const r = 110 * layout.sx, glow = frame.glow * (1 + 0.4 * grip);
    const backdrop = paint(color(0xFFFFFF));
    backdrop.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, 250,
      [ramp(pose.h + 0.5, 0.22 * alpha), ramp(pose.h + 0.5, 0)], [0, 1], TileMode.Clamp));
    canvas.drawCircle(cx, cy, 250, backdrop);
    stroke(canvas, path, color(0x5546FF, Math.min(1, 0.80 * glow) * alpha), 34, 20);
    stroke(canvas, path, color(0xFF4A1E, Math.min(1, 0.90 * glow) * alpha), 14, 8);
    const fill = paint(color(0xFFFFFF));
    fill.setShader(Skia.Shader.MakeLinearGradient({ x: cx, y: cy - 1.15 * r }, { x: cx, y: cy + 1.15 * r },
      [0.20, 0.10, 0, -0.10, -0.20].map((v) => ramp(pose.h + v, alpha)), [0, 0.25, 0.5, 0.75, 1], TileMode.Clamp));
    canvas.drawPath(path, fill);
    canvas.save();
    canvas.clipPath(path, ClipOp.Intersect, true);
    const highlight = paint(color(0xFFFFFF));
    highlight.setShader(Skia.Shader.MakeRadialGradient({ x: cx - 0.22 * r, y: cy - 0.30 * r }, 0.95 * r,
      [color(0xFFFFFF, 0.26 * alpha), color(0xFFFFFF, 0)], [0, 1], TileMode.Clamp));
    canvas.drawPath(path, highlight);
    stroke(canvas, path, ramp(pose.h - 0.25, 0.55 * alpha), 22, 12);
    canvas.restore();
    stroke(canvas, path, color(0xFF2A12, alpha), 2.2, 0.8);
    const hot = Math.max(0, Math.min(1, 1 - Math.abs(pose.h - Math.floor(pose.h) - 0.56) / 0.2));
    stroke(canvas, path, color(0xFFFFFF, 0.55 * hot * alpha), 1, 0);
    if (!reduced && frame.morph > 0 && frame.morph < 1) {
      const sparks = paint(ramp(pose.h + 0.1, Math.sin(Math.PI * frame.morph) * 0.9 * alpha));
      sparks.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 1.5, true));
      for (let j = 0; j < 16; j++) {
        const theta = j * Math.PI / 8 + Math.PI / 20;
        const delta = Math.atan2(Math.sin(theta - direction), Math.cos(theta - direction));
        const edge = radius(theta, pose.p, pose.c, pose.k, pose.phi, frame.time) * scale
          * (1 + 0.10 * grip * Math.exp(-Math.pow(delta / 0.6, 2)));
        const distance = edge + 6 + 70 * out(frame.morph);
        const seed = Math.sin(j * 12.9898 + 3.1) * 43758.5453;
        const dot = 1.4 + 1.2 * (seed - Math.floor(seed));
        canvas.drawCircle(cx + distance * Math.sin(theta), cy - distance * Math.cos(theta), dot, sparks);
      }
    }
  }
}

/** Small static glyphs use the exact same radial function without wobble. */
export function glyphPath(pose: Pose) {
  let path = '';
  for (let j = 0; j < 240; j++) {
    const theta = j * 2 * Math.PI / 240;
    const r = radius(theta, pose.p, pose.c, pose.k, pose.phi, null) * 15 / 110;
    path += `${j === 0 ? 'M' : 'L'}${38 + r * Math.sin(theta)},${34 - r * Math.cos(theta)}`;
  }
  return `${path}Z`;
}
