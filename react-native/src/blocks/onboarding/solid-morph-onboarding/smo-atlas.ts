import { AlphaType, ColorType, Skia, TextAlign, type SkTypefaceFontProvider, type SkImage, type SkPath } from '@shopify/react-native-skia';
import type { ImageSourcePropType } from 'react-native';
import { Image, PixelRatio } from 'react-native';

import { DEFAULT_GLYPHS, GLYPH_STROKE, pageCommands, type PageContent } from './smo-pages';
import { COPY, type SolidLayout, type SolidMorphTokens } from './smo-theme';

export type GlyphSource = string | SkPath | SkImage | ImageSourcePropType;
export type SolidMorphPage = PageContent & { glyph: GlyphSource };
export function drawPageAtlas(
  pages: SolidMorphPage[],
  l: SolidLayout,
  fonts: SkTypefaceFontProvider,
  tokens: SolidMorphTokens,
  primary: string = COPY.primary,
  secondary: string | null = COPY.secondary,
  footer = true,
) {
  const density = Math.min(3, PixelRatio.get(), 8190 / Math.max(l.W, l.H * pages.length));
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(l.W * density), Math.ceil(l.H * pages.length * density));
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.clear(Skia.Color('transparent'));
  canvas.scale(density, density);
  pages.forEach((page, i) => {
    canvas.save();
    canvas.translate(0, i * l.H);
    pageCommands(page, i, pages.length, l, tokens, primary, secondary, footer).forEach((c) => {
      if (c.kind === 'rect') {
        const paint = Skia.Paint();
        paint.setAntiAlias(true);
        paint.setColor(Skia.Color(c.color));
        if (c.stroke) {
          paint.setStyle(1);
          paint.setStrokeWidth(c.stroke);
        }
        canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(c.x, c.y, c.w, c.h), c.r, c.r), paint);
      } else {
        const builder = Skia.ParagraphBuilder.Make({ textAlign: c.align === 'center' ? TextAlign.Center : TextAlign.Left }, fonts);
        builder.pushStyle({
          fontFamilies: [c.family],
          fontSize: c.size,
          color: Skia.Color(c.color),
          letterSpacing: c.tracking ?? 0,
          heightMultiplier: c.lineHeight ?? 1.2,
        });
        builder.addText(c.text);
        builder.pop();
        const paragraph = builder.build();
        paragraph.layout(c.w);
        paragraph.paint(canvas, c.x, c.y + (c.valign === 'center' ? Math.max(0, (c.h - paragraph.getHeight()) / 2) : 0));
        paragraph.dispose();
        // Skia 2.6.2's ParagraphBuilder has no dispose().
        builder.dispose?.();
      }
    });
    canvas.restore();
  });
  surface.flush();
  // Skia 2.6: a snapshot is a texture of this offscreen context, which the canvas thread cannot sample; copy it to the CPU.
  const snap = surface.makeImageSnapshot();
  const image = snap.makeNonTextureImage() ?? snap;
  surface.dispose();
  return image;
}
/** Glyph field cell: 256² per page, 4 levels per texel around 128, so one level step is 1/8 of a 1/128 cell unit (as before). */
export const GLYPH_CELL = 256;
const FAR = 1e20;
// Exact 1-D squared distance (Felzenszwalb & Huttenlocher).
function edt1(f: Float64Array, n: number, out: Float64Array, v: Int32Array, z: Float64Array) {
  let k = 0;
  v[0] = 0;
  z[0] = -FAR;
  z[1] = FAR;
  for (let q = 1; q < n; q++) {
    let s = (f[q] + q * q - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
    while (s <= z[k]) {
      k--;
      s = (f[q] + q * q - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
    }
    k++;
    v[k] = q;
    z[k] = s;
    z[k + 1] = FAR;
  }
  k = 0;
  for (let q = 0; q < n; q++) {
    while (z[k + 1] < q) k++;
    out[q] = (q - v[k]) * (q - v[k]) + f[v[k]];
  }
}
/** Euclidean distance from every texel to the nearest seed texel. */
function distanceTo(seed: (i: number) => boolean) {
  const w = GLYPH_CELL;
  const grid = new Float64Array(w * w);
  for (let i = 0; i < grid.length; i++) grid[i] = seed(i) ? 0 : FAR;
  const f = new Float64Array(w);
  const out = new Float64Array(w);
  const v = new Int32Array(w);
  const z = new Float64Array(w + 1);
  for (let x = 0; x < w; x++) {
    for (let y = 0; y < w; y++) f[y] = grid[y * w + x];
    edt1(f, w, out, v, z);
    for (let y = 0; y < w; y++) grid[y * w + x] = out[y];
  }
  for (let y = 0; y < w; y++) {
    for (let x = 0; x < w; x++) f[x] = grid[y * w + x];
    edt1(f, w, out, v, z);
    for (let x = 0; x < w; x++) grid[y * w + x] = Math.sqrt(out[x]);
  }
  return grid;
}
/** Antialiased coverage of a path, rasterised on the CPU (no GPU texture to hand across threads). */
function coverage(path: SkPath, box: { x: number; y: number; size: number }) {
  const w = GLYPH_CELL;
  const surface = Skia.Surface.Make(w, w);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.clear(Skia.Color('transparent'));
  const k = (w * (94 / 128)) / box.size;
  canvas.translate(w / 2, w / 2);
  canvas.scale(k, k);
  canvas.translate(-box.x - box.size / 2, -box.y - box.size / 2);
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color('white'));
  canvas.drawPath(path, paint);
  surface.flush();
  const pixels = surface
    .makeImageSnapshot()
    .readPixels(0, 0, { width: w, height: w, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul });
  surface.dispose();
  if (!pixels) return null;
  const a = new Float32Array(w * w);
  for (let i = 0; i < a.length; i++) a[i] = Number(pixels[i * 4 + 3]) / (pixels instanceof Float32Array ? 1 : 255);
  return a;
}
function signedField(a: Float32Array) {
  const w = GLYPH_CELL;
  const toInside = distanceTo((i) => a[i] >= 0.5);
  const toOutside = distanceTo((i) => a[i] < 0.5);
  const bytes = new Uint8Array(w * w * 4);
  for (let i = 0; i < a.length; i++) {
    // Texels, positive inside; edge texels use their coverage for a sub-texel edge.
    let s = a[i] >= 0.5 ? toOutside[i] - 0.5 : 0.5 - toInside[i];
    if (a[i] > 0.02 && a[i] < 0.98) s = a[i] - 0.5;
    const v = Math.max(0, Math.min(255, Math.round(128 + s * 4)));
    bytes[i * 4] = v;
    bytes[i * 4 + 1] = v;
    bytes[i * 4 + 2] = v;
    bytes[i * 4 + 3] = 255;
  }
  return bytes;
}
export async function glyphAtlas(pages: SolidMorphPage[]) {
  const G = GLYPH_CELL;
  const surface = Skia.Surface.MakeOffscreen(G, G * pages.length);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.clear(Skia.Color('#010101'));
  for (let page = 0; page < pages.length; page++) {
    const src = pages[page].glyph;
    if (typeof src === 'object' && src !== null && 'width' in src && typeof src.width === 'function') {
      const img = src as SkImage;
      canvas.drawImageRect(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(0, page * G, G, G), Skia.Paint());
      continue;
    }
    if (typeof src === 'number' || (typeof src === 'object' && src !== null && 'uri' in src)) {
      const resolved = Image.resolveAssetSource(src as ImageSourcePropType);
      if (resolved?.uri && (typeof src === 'number' || !/^https?:/i.test(resolved.uri))) {
        try {
          const data = await Skia.Data.fromURI(resolved.uri);
          const image = Skia.Image.MakeImageFromEncoded(data);
          if (image)
            canvas.drawImageRect(image, Skia.XYWHRect(0, 0, image.width(), image.height()), Skia.XYWHRect(0, page * G, G, G), Skia.Paint());
        } catch {
          // A missing local field leaves this glyph blank; the pager still renders.
        }
      }
      continue;
    }
    let path: SkPath | null = typeof src === 'string' ? Skia.Path.MakeFromSVGString(src) : (src as SkPath);
    if (!path) continue;
    path = path.copy();
    // The built-in marks share one 100-unit box and stroke, so every mark has the same engraved line weight.
    const builtIn = typeof src === 'string' && DEFAULT_GLYPHS.some((v) => v === src);
    if (builtIn) path = Skia.Path.Stroke(path, { width: GLYPH_STROKE, cap: 1, join: 1 }) ?? path;
    const bounds = path.getBounds();
    const size = Math.max(bounds.width, bounds.height);
    const box = builtIn
      ? { x: 0, y: 0, size: 100 }
      : { x: bounds.x + bounds.width / 2 - size / 2, y: bounds.y + bounds.height / 2 - size / 2, size };
    const a = coverage(path, box);
    if (!a) continue;
    const image = Skia.Image.MakeImage(
      { width: G, height: G, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Opaque },
      Skia.Data.fromBytes(signedField(a)),
      G * 4,
    );
    if (image) canvas.drawImage(image, 0, page * G);
  }
  surface.flush();
  // Skia 2.6: a snapshot is a texture of this offscreen context, which the canvas thread cannot sample; copy it to the CPU.
  const snap = surface.makeImageSnapshot();
  const image = snap.makeNonTextureImage() ?? snap;
  surface.dispose();
  return image;
}
