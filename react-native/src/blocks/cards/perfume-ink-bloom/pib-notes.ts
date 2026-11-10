// Perfume Ink Bloom — the points the mist condenses onto: the perfume's notes, spelled out in the
// block's type, drawn once into a small raster surface and sampled where the letters are inked.

import { AlphaType, ColorType, type SkFont, Skia } from '@shopify/react-native-skia';

import { COPY, D, type Layout } from './pib-theme';

function rand(n: number) {
  const s = Math.sin(n * 91.3458) * 47453.5453;
  return s - Math.floor(s);
}

/** count points (x, y in screen points) on the inked pixels of the notes, or null if it cannot draw */
export function makeNotes(L: Layout, font: SkFont, count: number): Float32Array | null {
  const S = 3;
  const x0 = Math.floor((D.notes.x - 90) * L.sx);
  const y0 = Math.floor(L.Y(D.notes.y) - 30);
  const W = Math.ceil(180 * L.sx) * S;
  const H = Math.ceil(D.notes.line * COPY.notes.length + 40) * S;
  const surface = Skia.Surface.Make(W, H);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  const paint = Skia.Paint();
  paint.setColor(Skia.Color('white'));
  const big = Skia.Font(font.getTypeface() ?? undefined, D.notes.size * L.sx * S);
  COPY.notes.forEach((word, i) => {
    const tw = big.getTextWidth(word);
    const cx = (D.notes.x * L.sx - x0) * S;
    const cy = (L.Y(D.notes.y) + i * D.notes.line - y0) * S;
    canvas.drawText(word, cx - tw / 2, cy + D.notes.size * 0.36 * S, paint, big);
  });
  surface.flush();
  const px = surface.makeImageSnapshot().readPixels(0, 0, { width: W, height: H, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Unpremul });
  if (!px) return null;
  const inked: number[] = [];
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (px[(y * W + x) * 4 + 3] > 128) inked.push(y * W + x);
  if (!inked.length) return null;
  const out = new Float32Array(count * 2);
  for (let i = 0; i < count; i++) {
    const j = inked[Math.floor(rand(i + 7) * inked.length)];
    out[i * 2] = x0 + (j % W) / S;
    out[i * 2 + 1] = y0 + Math.floor(j / W) / S;
  }
  return out;
}
