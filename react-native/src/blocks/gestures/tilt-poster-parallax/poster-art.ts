/**
 * The poster for "The Long Light", drawn in code once at mount, one image per depth so the card can slide them
 * against each other as it turns: a dusk sky with a low sun, three ridges going from haze to near-black, and on the
 * nearest one a single walker. Every layer is drawn larger than the card (OVERSCAN) so a slide never shows an edge.
 * A grain over each layer keeps the flat colours from reading as vector art. Also the page's backdrop: the whole
 * poster, blurred.
 */
import { BlendMode, ClipOp, Skia, TileMode, type SkCanvas, type SkImage, type SkPath } from '@shopify/react-native-skia';

/** How much larger than the card each layer is drawn, per layer (back to front). */
export const OVERSCAN = [1.24, 1.2, 1.18, 1.14, 1.12] as const;

const GRAIN = Skia.RuntimeEffect.Make(`
uniform float uScale;
uniform float uAmount;
float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 xy) {
  float g = hash(floor(xy * uScale)) - 0.5;
  return half4(half3(g * uAmount), 0.0);
}
`);

function surface(w: number, h: number) {
  return Skia.Surface.MakeOffscreen(w, h) ?? Skia.Surface.Make(w, h);
}

function snapshot(s: NonNullable<ReturnType<typeof surface>>): SkImage {
  s.flush();
  const img = s.makeImageSnapshot();
  return img.makeNonTextureImage() ?? img;
}

function grain(c: SkCanvas, w: number, h: number, scale: number, amount: number, clip?: SkPath) {
  if (!GRAIN) return;
  const g = Skia.Paint();
  g.setShader(GRAIN.makeShader([scale, amount]));
  g.setBlendMode(BlendMode.Plus);
  if (clip) {
    c.save();
    c.clipPath(clip, ClipOp.Intersect, true);
  }
  c.drawRect(Skia.XYWHRect(0, 0, w, h), g);
  if (clip) c.restore();
}

/** The ridge's height at u (0..1 across the layer): smooth noise of a few sines. */
function ridgeY(base: number, amp: number, seed: number, u: number): number {
  return base - amp * (0.55 * Math.sin(u * 5.1 + seed) + 0.3 * Math.sin(u * 11.7 + seed * 2.3) + 0.15 * Math.sin(u * 23.9 + seed * 0.7));
}

/** A ridge line across the layer, filled down to the bottom. */
function ridge(w: number, h: number, base: number, amp: number, seed: number, steps = 48): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(0, h);
  for (let k = 0; k <= steps; k++) b.lineTo((w * k) / steps, ridgeY(base, amp, seed, k / steps));
  b.lineTo(w, h).close();
  return b.build();
}

function fillPath(c: SkCanvas, path: SkPath, top: string, bottom: string, y0: number, y1: number) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: y0 }, { x: 0, y: y1 }, [Skia.Color(top), Skia.Color(bottom)], null, TileMode.Clamp));
  c.drawPath(path, p);
}

/** Layer k (0 sky … 4 near hill) drawn at `scale` px/pt for a card `cw` × `ch`; its size is the card's × OVERSCAN[k]. */
function drawLayer(c: SkCanvas, k: number, cw: number, ch: number, scale: number) {
  const w = cw * OVERSCAN[k];
  const h = ch * OVERSCAN[k];
  // The card's own coordinates inside this layer: the layer is centred on the card.
  const ox = (w - cw) / 2;
  const oy = (h - ch) / 2;
  const Y = (v: number) => oy + ch * v;
  if (k === 0) {
    const p = Skia.Paint();
    p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: h },
      [Skia.Color('#141A30'), Skia.Color('#2E2B4A'), Skia.Color('#7A4E5E'), Skia.Color('#D98C62'), Skia.Color('#F2B77E')],
      [0, Y(0.3) / h, Y(0.5) / h, Y(0.64) / h, 1], TileMode.Clamp));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), p);
    grain(c, w, h, scale, 0.05);
    return;
  }
  if (k === 1) {
    // The sun, low and soft, with a wide halo.
    const sx = ox + cw * 0.5;
    const sy = Y(0.6);
    const r = cw * 0.17;
    const halo = Skia.Paint();
    halo.setShader(Skia.Shader.MakeRadialGradient({ x: sx, y: sy }, r * 3.2,
      [Skia.Color('rgba(255,214,160,0.45)'), Skia.Color('rgba(255,170,110,0.12)'), Skia.Color('rgba(255,170,110,0)')], [0, 0.4, 1], TileMode.Clamp));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), halo);
    const disc = Skia.Paint();
    disc.setAntiAlias(true);
    disc.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: sy - r }, { x: 0, y: sy + r },
      [Skia.Color('#FFF1D6'), Skia.Color('#F7C98F')], null, TileMode.Clamp));
    c.drawCircle(sx, sy, r, disc);
    return;
  }
  if (k === 2) {
    const path = ridge(w, h, Y(0.64), ch * 0.05, 1.3);
    fillPath(c, path, '#9C6E78', '#7A5A6C', Y(0.58), Y(0.8));
    grain(c, w, h, scale, 0.04, path);
    return;
  }
  if (k === 3) {
    const path = ridge(w, h, Y(0.73), ch * 0.06, 4.1);
    fillPath(c, path, '#53405A', '#3A2E44', Y(0.66), Y(0.9));
    grain(c, w, h, scale, 0.04, path);
    return;
  }
  // The near hill and the walker on its crest, a little right of centre.
  const path = ridge(w, h, Y(0.84), ch * 0.045, 2.6);
  fillPath(c, path, '#211A28', '#120E17', Y(0.8), Y(1));
  grain(c, w, h, scale, 0.035, path);
  const fx = ox + cw * 0.62;
  const ground = ridgeY(Y(0.84), ch * 0.045, 2.6, fx / w) + 1;
  const s = ch / 250;
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(Skia.Color('#120E17'));
  // A walker mid-stride, small against the hills: head, a long coat flaring back, two legs.
  c.drawCircle(fx + 0.3 * s, ground - 22.5 * s, 1.9 * s, ink);
  const body = Skia.PathBuilder.Make()
    .moveTo(fx - 1.4 * s, ground - 20 * s).lineTo(fx + 1.8 * s, ground - 20 * s)
    .lineTo(fx + 2.6 * s, ground - 8.5 * s).lineTo(fx - 4.2 * s, ground - 7.5 * s).close()
    .moveTo(fx - 0.9 * s, ground - 8 * s).lineTo(fx - 3.2 * s, ground + 0.5 * s).lineTo(fx - 2.2 * s, ground + 0.5 * s).lineTo(fx + 0.1 * s, ground - 8 * s).close()
    .moveTo(fx + 0.7 * s, ground - 8 * s).lineTo(fx + 2.9 * s, ground + 0.5 * s).lineTo(fx + 3.9 * s, ground + 0.5 * s).lineTo(fx + 1.7 * s, ground - 8 * s).close()
    .build();
  c.drawPath(body, ink);
}

export type PosterArt = { layers: SkImage[]; backdrop: SkImage };

/** The five layers for a card `cw` × `ch` at `scale` px/pt, and a blurred whole poster for the page behind it. */
export function makePosterArt(cw: number, ch: number, scale: number): PosterArt | null {
  const layers: SkImage[] = [];
  for (let k = 0; k < OVERSCAN.length; k++) {
    const w = Math.round(cw * OVERSCAN[k] * scale);
    const h = Math.round(ch * OVERSCAN[k] * scale);
    const s = surface(w, h);
    if (!s) return null;
    const c = s.getCanvas();
    c.scale(scale, scale);
    drawLayer(c, k, cw, ch, scale);
    layers.push(snapshot(s));
  }
  // The backdrop: every layer stacked at rest, blurred hard, at a quarter of the size.
  const k = scale / 4;
  const bw = Math.round(cw * k);
  const bh = Math.round(ch * k);
  const s = surface(bw, bh);
  if (!s) return null;
  const c = s.getCanvas();
  const p = Skia.Paint();
  p.setImageFilter(Skia.ImageFilter.MakeBlur(14 * k, 14 * k, TileMode.Clamp, null));
  c.saveLayer(p);
  for (let i = 0; i < layers.length; i++) {
    const lw = cw * OVERSCAN[i] * k;
    const lh = ch * OVERSCAN[i] * k;
    c.drawImageRect(layers[i], Skia.XYWHRect(0, 0, layers[i].width(), layers[i].height()), Skia.XYWHRect((bw - lw) / 2, (bh - lh) / 2, lw, lh), Skia.Paint());
  }
  c.restore();
  return { layers, backdrop: snapshot(s) };
}
