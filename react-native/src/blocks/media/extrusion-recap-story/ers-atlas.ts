import { AlphaType, ColorType, Skia, type SkImage, type SkPath } from '@shopify/react-native-skia';
import { exactBand, makeDistanceField, SDF_PAD, SDF_SIZE } from './ers-distance';
import type { ExtrusionPage } from './ers-types';

/** Outline of a path as line segments (x0, y0, x1, y1 …), mapped by `map`; curves are flattened finely. */
function outlineOf(path: SkPath, map: (x: number, y: number) => [number, number]) {
  const out: number[] = [];
  let cx = 0,
    cy = 0,
    sx = 0,
    sy = 0;
  const line = (x: number, y: number) => {
    const a = map(cx, cy),
      b = map(x, y);
    out.push(a[0], a[1], b[0], b[1]);
    cx = x;
    cy = y;
  };
  for (const cmd of path.toCmds()) {
    const v = cmd[0];
    if (v === 0) {
      cx = sx = cmd[1];
      cy = sy = cmd[2];
    } else if (v === 1) line(cmd[1], cmd[2]);
    else if (v === 2 || v === 3) {
      const [x0, y0] = [cx, cy];
      for (let k = 1; k <= 24; k++) {
        const t = k / 24,
          u = 1 - t;
        line(u * u * x0 + 2 * u * t * cmd[1] + t * t * cmd[3], u * u * y0 + 2 * u * t * cmd[2] + t * t * cmd[4]);
      }
    } else if (v === 4) {
      const [x0, y0] = [cx, cy];
      for (let k = 1; k <= 32; k++) {
        const t = k / 32,
          u = 1 - t;
        line(
          u * u * u * x0 + 3 * u * u * t * cmd[1] + 3 * u * t * t * cmd[3] + t * t * t * cmd[5],
          u * u * u * y0 + 3 * u * u * t * cmd[2] + 3 * u * t * t * cmd[4] + t * t * t * cmd[6],
        );
      }
    } else if (v === 5 && (cx !== sx || cy !== sy)) line(sx, sy);
  }
  return out;
}

/** CPU distance fields, kept across mounts so reopening a story is instant (GPU images are still per mount). */
const FIELDS = new Map<string, Uint8Array>();

export function createAtlas(pages: ExtrusionPage[]): SkImage | null {
  const n = SDF_SIZE,
    atlas = new Uint8Array(n * n * 4 * pages.length * 2);
  const cache = FIELDS;
  for (let i = 0; i < pages.length; i++) {
    const input = pages[i].glyph;
    const key = typeof input === 'string' ? input : input.toSVGString();
    let pixels = cache.get(key);
    if (!pixels) {
      const path = typeof input === 'string' ? Skia.Path.MakeFromSVGString(input) : input;
      const surface = Skia.Surface.Make(n, n);
      if (!path || !surface) return null;
      const bounds = path.getBounds();
      if (bounds.width <= 0 || bounds.height <= 0) {
        surface.dispose();
        return null;
      }
      const c = surface.getCanvas(),
        paint = Skia.Paint();
      c.clear(Skia.Color('transparent'));
      const scale = (n - 2 * SDF_PAD) / Math.max(bounds.width, bounds.height);
      c.translate(n / 2, n / 2);
      c.scale(scale, scale);
      c.translate(-bounds.x - bounds.width / 2, -bounds.y - bounds.height / 2);
      paint.setColor(Skia.Color('#ffffff'));
      paint.setAntiAlias(true);
      c.drawPath(path, paint);
      surface.flush();
      const image = surface.makeImageSnapshot();
      const raw = image.readPixels(0, 0, { width: n, height: n, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul });
      if (!raw) {
        image.dispose();
        surface.dispose();
        paint.dispose();
        return null;
      }
      const mask = new Uint8Array(n * n);
      for (let j = 0; j < mask.length; j++) mask[j] = raw[j * 4 + 3];
      const cx = bounds.x + bounds.width / 2,
        cy = bounds.y + bounds.height / 2;
      const segments = outlineOf(path, (x, y) => [(x - cx) * scale + n / 2, (y - cy) * scale + n / 2]);
      pixels = makeDistanceField(mask, exactBand(segments));
      if (cache.size > 12) cache.clear();
      cache.set(key, pixels);
      image.dispose();
      surface.dispose();
      paint.dispose();
    }
    // High byte as grey in the top tile row, low byte as grey below it: grey survives the sRGB -> P3 colour management
    // applied when the shader samples the image, whereas a byte in R and another in G would be mixed by it.
    const row = n * pages.length * 4;
    for (let y = 0; y < n; y++)
      for (let x = 0; x < n; x++) {
        const src = (y * n + x) * 4,
          hi = (y * n * pages.length + i * n + x) * 4,
          lo = hi + n * row;
        atlas[hi] = atlas[hi + 1] = atlas[hi + 2] = pixels[src];
        atlas[lo] = atlas[lo + 1] = atlas[lo + 2] = pixels[src + 1];
        atlas[hi + 3] = atlas[lo + 3] = 255;
      }
  }
  return Skia.Image.MakeImage(
    { width: n * pages.length, height: 2 * n, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul },
    Skia.Data.fromBytes(atlas),
    n * pages.length * 4,
  );
}
