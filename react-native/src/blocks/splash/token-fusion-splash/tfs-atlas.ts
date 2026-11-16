import { AlphaType, ColorType, Skia, type SkPath } from '@shopify/react-native-skia';
import { sdfPixels } from './tfs-sdf';
import { comp } from './tfs-theme';

const cache = new Map<string, Uint8Array>();
/** CPU-backed mount-time rasterisation avoids a GPU readback and has no WebGPU dependency. */
export function makeGlyphAtlas(paths: (SkPath | string | null)[]) {
  const { cell, cells, padding, spread } = comp.atlas;
  const atlas = new Uint8Array(cell * cells * cell * 4);
  atlas.fill(255);
  for (let slot = 0; slot < Math.min(paths.length, cells); slot++) {
    const input = paths[slot];
    if (!input) continue;
    const path = typeof input === 'string' ? Skia.Path.MakeFromSVGString(input) : input;
    if (!path) continue;
    const key = path.toSVGString();
    let pixels = cache.get(key);
    if (!pixels) {
      const surface = Skia.Surface.Make(cell, cell);
      if (!surface) continue;
      const canvas = surface.getCanvas();
      canvas.clear(Skia.Color('transparent'));
      const b = path.getBounds();
      const scale = (cell - padding * 2) / Math.max(b.width, b.height, 1);
      canvas.translate(cell / 2, cell / 2);
      canvas.scale(scale, scale);
      canvas.translate(-b.x - b.width / 2, -b.y - b.height / 2);
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      paint.setColor(Skia.Color('white'));
      canvas.drawPath(path, paint);
      const image = surface.makeImageSnapshot();
      const rgba = image.readPixels(0, 0, { width: cell, height: cell, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul });
      if (rgba instanceof Uint8Array) {
        const alpha = new Uint8Array(cell * cell);
        for (let i = 0; i < alpha.length; i++) alpha[i] = rgba[i * 4 + 3];
        pixels = sdfPixels(alpha, cell, cell, spread);
        if (cache.size >= 48) cache.delete(cache.keys().next().value as string);
        cache.set(key, pixels);
      }
      image.dispose();
      paint.dispose();
      surface.dispose();
    }
    if (!pixels) continue;
    for (let y = 0; y < cell; y++) atlas.set(pixels.subarray(y * cell * 4, (y + 1) * cell * 4), (y * cell * cells + slot * cell) * 4);
  }
  return Skia.Image.MakeImage(
    { width: cell * cells, height: cell, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Opaque },
    Skia.Data.fromBytes(atlas),
    cell * cells * 4,
  );
}
