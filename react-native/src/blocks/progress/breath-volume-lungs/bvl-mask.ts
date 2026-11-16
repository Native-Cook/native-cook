import { AlphaType, ColorType, Skia, type SkImage, type SkPath } from '@shopify/react-native-skia';
import type { FieldGrid } from './bvl-fields';

/** Rasterise on a CPU surface, at cell resolution, once per changed input. SVG coordinates may use any view box. */
export function sampleMask(input: SkPath | SkImage, cols: number, rows: number, pile = false): number[] | undefined {
  const surface = Skia.Surface.Make(cols, rows);
  if (!surface) return undefined;
  const canvas = surface.getCanvas();
  canvas.clear(Skia.Color('transparent'));
  const paint = Skia.Paint();
  paint.setColor(Skia.Color('#FFFFFF'));
  paint.setAntiAlias(true);
  const target = Skia.XYWHRect(cols * 0.08, rows * (pile ? 0.625 : 0.175), cols * 0.84, rows * (pile ? 0.275 : 0.35));
  if (input.__typename__ === 'Path') {
    const bounds = input.getBounds();
    if (bounds.width > 0 && bounds.height > 0) {
      canvas.translate(target.x, target.y);
      canvas.scale(target.width / bounds.width, target.height / bounds.height);
      canvas.translate(-bounds.x, -bounds.y);
      canvas.drawPath(input, paint);
    }
  } else {
    canvas.drawImageRect(input, Skia.XYWHRect(0, 0, input.width(), input.height()), target, paint);
  }
  const snap = surface.makeImageSnapshot();
  const raster = snap.makeNonTextureImage() ?? snap;
  const pixels = raster.readPixels(0, 0, { width: cols, height: rows, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul });
  const out = pixels ? Array.from({ length: cols * rows }, (_, i) => Number(pixels[i * 4 + 3]) / 255) : undefined;
  if (raster !== snap) raster.dispose();
  snap.dispose();
  paint.dispose();
  surface.dispose();
  return out;
}

export function gridImage(grid: FieldGrid) {
  return Skia.Image.MakeImage(
    { width: grid.width, height: grid.rows + 1, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Opaque },
    Skia.Data.fromBytes(grid.bytes),
    grid.width * 4,
  );
}
