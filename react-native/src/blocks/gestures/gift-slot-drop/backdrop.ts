/**
 * The page under the glass, drawn once at mount into two images: as it is (warm charcoal, a fine grain, the light that
 * comes up out of the slot) and frosted, which every piece of glass refracts. Nothing on it moves.
 */
import { BlendMode, Skia, TileMode, type SkCanvas, type SkImage } from '@shopify/react-native-skia';

import type { Layout } from './theme';

const GRAIN = Skia.RuntimeEffect.Make(`
uniform float uScale;
float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 xy) {
  float g = hash(floor(xy * uScale)) - 0.5;
  return half4(half3(g * 0.045), 0.0);
}
`);

function radial(c: SkCanvas, x: number, y: number, rx: number, ry: number, colors: string[], pos: number[], w: number, h: number) {
  const p = Skia.Paint();
  const m = Skia.Matrix();
  m.translate(x, y);
  m.scale(1, ry / rx);
  m.translate(-x, -y);
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, rx, colors.map((k) => Skia.Color(k)), pos, TileMode.Clamp, m));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), p);
}

function drawPage(c: SkCanvas, lay: Layout, scale: number) {
  const { width: w, height: h } = lay;
  const base = Skia.Paint();
  base.setShader(Skia.Shader.MakeLinearGradient(
    { x: 0, y: 0 }, { x: 0, y: h }, [Skia.Color('#100D0A'), Skia.Color('#0C0A08'), Skia.Color('#120E0A')], [0, 0.5, 1], TileMode.Clamp,
  ));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), base);
  // The one light: warm, out of the slot, spreading up the page in a wide ellipse.
  radial(c, w / 2, lay.slotY + 10, w * 0.95, w * 0.78,
    ['rgba(255,170,80,0.34)', 'rgba(230,110,50,0.14)', 'rgba(90,40,20,0.05)', 'rgba(0,0,0,0)'], [0, 0.35, 0.7, 1], w, h);
  // A faint cold fill high up, so the header's glass has something to bend.
  radial(c, w * 0.15, lay.top, w * 0.8, w * 0.6, ['rgba(120,140,190,0.08)', 'rgba(0,0,0,0)'], [0, 1], w, h);
  if (GRAIN) {
    const g = Skia.Paint();
    g.setShader(GRAIN.makeShader([scale]));
    g.setBlendMode(BlendMode.Plus);
    c.drawRect(Skia.XYWHRect(0, 0, w, h), g);
  }
}

function surface(w: number, h: number) {
  return Skia.Surface.MakeOffscreen(w, h) ?? Skia.Surface.Make(w, h);
}

function snapshot(s: NonNullable<ReturnType<typeof surface>>): SkImage {
  s.flush();
  const img = s.makeImageSnapshot();
  return img.makeNonTextureImage() ?? img;
}

/** `sharpScale` / `frostScale`: pixels per point of each image. */
export type Backdrop = { sharp: SkImage; frosted: SkImage; sharpScale: number; frostScale: number };

export function makeBackdrop(lay: Layout, scale: number): Backdrop | null {
  const W = Math.round(lay.width * scale);
  const H = Math.round(lay.height * scale);
  const s1 = surface(W, H);
  if (!s1) return null;
  const c1 = s1.getCanvas();
  c1.scale(scale, scale);
  drawPage(c1, lay, scale);
  const sharp = snapshot(s1);
  const k = scale / 3;
  const w2 = Math.round(lay.width * k);
  const h2 = Math.round(lay.height * k);
  const s2 = surface(w2, h2);
  if (!s2) return null;
  const p = Skia.Paint();
  p.setImageFilter(Skia.ImageFilter.MakeBlur(24 * k, 24 * k, TileMode.Clamp, null));
  s2.getCanvas().drawImageRect(sharp, Skia.XYWHRect(0, 0, W, H), Skia.XYWHRect(0, 0, w2, h2), p);
  const frosted = snapshot(s2);
  return { sharp, frosted, sharpScale: scale, frostScale: k };
}
