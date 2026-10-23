/**
 * The page under the glass bubbles, drawn once at mount into two images: as it is (a calm dusk: deep slate, a sage
 * light high on the left, a sand light low on the right, a fine grain) and frosted, which every bubble refracts.
 */
import { BlendMode, Skia, TileMode, type SkCanvas, type SkImage } from '@shopify/react-native-skia';

const GRAIN = Skia.RuntimeEffect.Make(`
uniform float uScale;
float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 xy) {
  float g = hash(floor(xy * uScale)) - 0.5;
  return half4(half3(g * 0.04), 0.0);
}
`);

function radial(c: SkCanvas, x: number, y: number, r: number, colors: string[], pos: number[], w: number, h: number) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r, colors.map((k) => Skia.Color(k)), pos, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), p);
}

function drawPage(c: SkCanvas, w: number, h: number, scale: number) {
  const base = Skia.Paint();
  base.setShader(Skia.Shader.MakeLinearGradient(
    { x: 0, y: 0 }, { x: 0, y: h }, [Skia.Color('#101317'), Skia.Color('#0D0F12'), Skia.Color('#121110')], [0, 0.55, 1], TileMode.Clamp,
  ));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), base);
  radial(c, w * 0.12, h * 0.22, w * 0.95, ['rgba(130,170,150,0.2)', 'rgba(90,120,110,0.07)', 'rgba(0,0,0,0)'], [0, 0.5, 1], w, h);
  radial(c, w * 0.9, h * 0.7, w * 0.95, ['rgba(220,190,140,0.17)', 'rgba(160,120,80,0.06)', 'rgba(0,0,0,0)'], [0, 0.5, 1], w, h);
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

export function makeBackdrop(width: number, height: number, scale: number): Backdrop | null {
  const W = Math.round(width * scale);
  const H = Math.round(height * scale);
  const s1 = surface(W, H);
  if (!s1) return null;
  const c1 = s1.getCanvas();
  c1.scale(scale, scale);
  drawPage(c1, width, height, scale);
  const sharp = snapshot(s1);
  const k = scale / 3;
  const w2 = Math.round(width * k);
  const h2 = Math.round(height * k);
  const s2 = surface(w2, h2);
  if (!s2) return null;
  const p = Skia.Paint();
  p.setImageFilter(Skia.ImageFilter.MakeBlur(20 * k, 20 * k, TileMode.Clamp, null));
  s2.getCanvas().drawImageRect(sharp, Skia.XYWHRect(0, 0, W, H), Skia.XYWHRect(0, 0, w2, h2), p);
  const frosted = snapshot(s2);
  return { sharp, frosted, sharpScale: scale, frostScale: k };
}
