/**
 * The page under the glass, drawn once at mount into two images: as it is (the wallpaper, a fine grain, the hour
 * lines, the dotted rail, the now line) and frosted, which is what every piece of glass refracts. Nothing on it moves,
 * so the glass costs one shader pass a box and no live blur.
 */
import { BlendMode, Skia, TileMode, type SkCanvas, type SkImage } from '@shopify/react-native-skia';

import { COLOR, DAY, LAYOUT, yOf, type Layout } from './theme';

const GRAIN = Skia.RuntimeEffect.Make(`
uniform float uScale;
float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 xy) {
  float g = hash(floor(xy * uScale)) - 0.5;
  return half4(half3(g * 0.05), 0.0);
}
`);

function radial(c: SkCanvas, x: number, y: number, r: number, colors: string[], pos: number[], w: number, h: number) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r, colors.map((k) => Skia.Color(k)), pos, TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), p);
}

/** The page at screen points, onto a canvas already scaled to pixels. */
function drawPage(c: SkCanvas, lay: Layout, scale: number) {
  const { width: w, height: h } = lay;
  const base = Skia.Paint();
  base.setShader(Skia.Shader.MakeLinearGradient(
    { x: 0, y: 0 }, { x: 0, y: h }, [Skia.Color('#080B13'), Skia.Color('#06080D'), Skia.Color('#0A0A0E')], [0, 0.45, 1], TileMode.Clamp,
  ));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), base);
  // One warm light low on the page, like a city at night below the window: a horizon across the bottom half, brightest
  // off the lower right corner; and a cold ambient high on the left.
  const horizon = Skia.Paint();
  horizon.setShader(Skia.Shader.MakeLinearGradient(
    { x: 0, y: h * 0.38 }, { x: 0, y: h },
    [Skia.Color('rgba(255,120,50,0)'), Skia.Color('rgba(230,104,48,0.12)'), Skia.Color('rgba(255,140,60,0.3)')], [0, 0.55, 1], TileMode.Clamp,
  ));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), horizon);
  radial(c, w * 0.92, h * 0.98, w * 1.15,
    ['rgba(255,170,80,0.42)', 'rgba(236,100,46,0.18)', 'rgba(0,0,0,0)'], [0, 0.4, 1], w, h);
  radial(c, w * 0.08, h * 0.2, w * 0.9, ['rgba(88,120,200,0.14)', 'rgba(0,0,0,0)'], [0, 1], w, h);
  if (GRAIN) {
    const g = Skia.Paint();
    g.setShader(GRAIN.makeShader([scale]));
    g.setBlendMode(BlendMode.Plus);
    c.drawRect(Skia.XYWHRect(0, 0, w, h), g);
  }

  // Hour lines across the blocks' column, faint enough to read only through the gaps.
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setColor(Skia.Color('rgba(255,255,255,0.065)'));
  for (let hr = DAY.start / 60; hr <= DAY.end / 60; hr++) {
    const y = yOf(lay, hr * 60);
    c.drawRect(Skia.XYWHRect(LAYOUT.railX, y - 0.25, lay.right - LAYOUT.railX, 0.5), line);
  }
  // The rail: a dot every 6 pt.
  const dot = Skia.Paint();
  dot.setAntiAlias(true);
  dot.setColor(Skia.Color('rgba(255,255,255,0.2)'));
  for (let y = yOf(lay, DAY.start) - 6; y <= yOf(lay, DAY.end) + 6; y += 6) c.drawCircle(LAYOUT.railX, y, 0.9, dot);
  // Now: a red line across, from a dot on the rail.
  const ny = yOf(lay, DAY.now);
  const red = Skia.Paint();
  red.setAntiAlias(true);
  red.setColor(Skia.Color(COLOR.now));
  c.drawRect(Skia.XYWHRect(LAYOUT.railX, ny - 0.75, lay.right - LAYOUT.railX, 1.5), red);
  c.drawCircle(LAYOUT.railX, ny, 4, red);
}

/** `sharpScale` / `frostScale`: pixels per point of each image. */
export type Backdrop = { sharp: SkImage; frosted: SkImage; sharpScale: number; frostScale: number };

/** A GPU surface when there is one (the grain shader is slow on the CPU), else a raster one. */
function surface(w: number, h: number) {
  return Skia.Surface.MakeOffscreen(w, h) ?? Skia.Surface.Make(w, h);
}

/** The surface's picture as an image any thread can draw. */
function snapshot(s: NonNullable<ReturnType<typeof surface>>): SkImage {
  s.flush();
  const img = s.makeImageSnapshot();
  return img.makeNonTextureImage() ?? img;
}

export function makeBackdrop(lay: Layout, scale: number): Backdrop | null {
  const W = Math.round(lay.width * scale);
  const H = Math.round(lay.height * scale);
  const s1 = surface(W, H);
  if (!s1) return null;
  const c1 = s1.getCanvas();
  c1.scale(scale, scale);
  drawPage(c1, lay, scale);
  const sharp = snapshot(s1);

  // Frosted at a third of the size: a 24 pt blur leaves nothing finer than that to keep.
  const k = scale / 3;
  const w2 = Math.round(lay.width * k);
  const h2 = Math.round(lay.height * k);
  const s2 = surface(w2, h2);
  if (!s2) return null;
  const c2 = s2.getCanvas();
  const p = Skia.Paint();
  p.setImageFilter(Skia.ImageFilter.MakeBlur(24 * k, 24 * k, TileMode.Clamp, null));
  c2.drawImageRect(sharp, Skia.XYWHRect(0, 0, W, H), Skia.XYWHRect(0, 0, w2, h2), p);
  const frosted = snapshot(s2);
  return { sharp, frosted, sharpScale: scale, frostScale: k };
}
