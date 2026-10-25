import { BlendMode, BlurStyle, type SkCanvas, type SkFont, type SkImage, Skia, PaintStyle, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

/** Small drawing helpers shared by the dice table's baked art and its per-frame layer. */

export function rgba(hex: number, a = 1) {
  'worklet';
  return Float32Array.of(((hex >> 16) & 0xff) / 255, ((hex >> 8) & 0xff) / 255, (hex & 0xff) / 255, a);
}

export function fill(hex: number, a = 1, blur = 0) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(rgba(hex, a));
  if (blur > 0) p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, blur, true));
  return p;
}

export function stroke(hex: number, width: number, a = 1, blur = 0) {
  'worklet';
  const p = fill(hex, a, blur);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  return p;
}

export function linear(x0: number, y0: number, x1: number, y1: number, colors: Float32Array[], stops: number[] | null) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors, stops, TileMode.Clamp));
  return p;
}

export function radial(x: number, y: number, r: number, colors: Float32Array[], stops: number[] | null) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r, colors, stops, TileMode.Clamp));
  return p;
}

export function textWidth(font: SkFont, str: string) {
  'worklet';
  const w = font.getGlyphWidths(font.getGlyphIDs(str));
  let sum = 0;
  for (let i = 0; i < w.length; i += 1) sum += w[i];
  return sum;
}

/** Text with extra tracking; align 0 = left, 0.5 = centre, 1 = right. */
export function spaced(canvas: SkCanvas, str: string, x: number, y: number, font: SkFont, paint: ReturnType<typeof fill>, track: number, align = 0) {
  'worklet';
  const ids = font.getGlyphIDs(str);
  const w = font.getGlyphWidths(ids);
  let total = 0;
  for (let i = 0; i < w.length; i += 1) total += w[i] + (i < w.length - 1 ? track : 0);
  let cx = x - total * align;
  for (let i = 0; i < str.length && i < w.length; i += 1) {
    canvas.drawText(str[i], cx, y, paint, font);
    cx += w[i] + track;
  }
  return total;
}

/** Faint static grain over a rect (fractal noise, soft-light). */
export function grain(canvas: SkCanvas, x: number, y: number, w: number, h: number, alpha: number, seed: number) {
  'worklet';
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeFractalNoise(0.9, 0.9, 2, seed, 0, 0));
  p.setAlphaf(alpha);
  p.setBlendMode(BlendMode.SoftLight);
  canvas.drawRect(Skia.XYWHRect(x, y, w, h), p);
}

/** Draws into a raster image once (on the JS thread), at `scale` pixels per point. */
export function bake(width: number, height: number, scale: number, draw: (canvas: SkCanvas) => void): SkImage | null {
  const surface = Skia.Surface.Make(Math.max(1, Math.ceil(width * scale)), Math.max(1, Math.ceil(height * scale)));
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.scale(scale, scale);
  draw(canvas);
  surface.flush();
  const image = surface.makeImageSnapshot();
  return image;
}
