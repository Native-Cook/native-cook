import { BlendMode, BlurStyle, ClipOp, PaintStyle, Skia, TileMode, type SkCanvas, type SkImage, type SkPath } from '@shopify/react-native-skia';

/*
 Hot Air Balloon Welcome — the scenery: screen geometry, and the sky, hills and cloud sprites that
 are baked once (at up to 2x) and then only moved with parallax.
 */

/** Screen geometry. */
export type BalloonGeo = {
  w: number;
  h: number;
  /** The meadow's ridge where the basket rests, the screen rise per step and the world scroll. */
  ground: number;
  rise: number;
  scroll: number;
  /** Envelope height, widest radius, mouth radius. */
  E: number;
  R: number;
  rm: number;
};

/** Mouth to basket bottom (pt). */
export const BELOW = 66;

export function makeBalloonGeo(w: number, h: number, ground: number, ceiling: number): BalloonGeo {
  const E = Math.min(190, h * 0.22);
  const top = ground - BELOW - E;
  return { w, h, ground, rise: Math.max(30, (top - ceiling) / 2), scroll: 120, E, R: E * 0.44, rm: E * 0.085 };
}

// Baked layers ---------------------------------------------------------------------------------

export type Layer = { image: SkImage; y: number; height: number; par: number };
export type Cloud = { x: number; y: number; par: number; scale: number; alpha: number; front: boolean; amp: number; phase: number; sprite: number };
export type BalloonArt = { sky: SkImage; hills: Layer[]; clouds: SkImage[] };

export const CLOUDS: Cloud[] = [
  { x: 0.12, y: 150, par: 0.25, scale: 0.7, alpha: 0.5, front: false, amp: 10, phase: 0.3, sprite: 1 },
  { x: 0.78, y: 215, par: 0.32, scale: 0.85, alpha: 0.6, front: false, amp: 12, phase: 2.1, sprite: 0 },
  { x: 0.02, y: 40, par: 0.9, scale: 1.15, alpha: 0.85, front: false, amp: 14, phase: 4.0, sprite: 2 },
  { x: 0.7, y: -60, par: 1.1, scale: 1.35, alpha: 0.9, front: false, amp: 16, phase: 1.2, sprite: 0 },
  { x: 0.3, y: -150, par: 1.25, scale: 1.5, alpha: 0.72, front: true, amp: 18, phase: 5.1, sprite: 1 },
  { x: 0.92, y: -230, par: 1.3, scale: 1.05, alpha: 0.8, front: true, amp: 12, phase: 3.3, sprite: 2 },
];

function hash(n: number) {
  const x = Math.sin(n * 12.9898 + 78.233) * 43758.5453;
  return x - Math.floor(x);
}

function bake(w: number, h: number, scale: number, draw: (c: SkCanvas) => void): SkImage {
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(w * scale), Math.ceil(h * scale)) ?? Skia.Surface.Make(Math.ceil(w * scale), Math.ceil(h * scale));
  if (!surface) throw new Error('Hot Air Balloon Welcome: no surface to bake on.');
  const c = surface.getCanvas();
  c.clear(Skia.Color('transparent'));
  c.scale(scale, scale);
  draw(c);
  surface.flush();
  const snap = surface.makeImageSnapshot();
  return snap.makeNonTextureImage() ?? snap;
}

function drawSky(c: SkCanvas, w: number, h: number, horizon: number) {
  const sky = Skia.Paint();
  sky.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: 0, y: horizon + 40 },
      [Skia.Color('#262B5A'), Skia.Color('#4B3F7E'), Skia.Color('#9A5F93'), Skia.Color('#E58A98'), Skia.Color('#F8C49A')],
      [0, 0.32, 0.6, 0.84, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, 0, w, h), sky);
  // The sun, low on the right, with a wide warm bloom.
  const sx = w * 0.72;
  const sy = horizon - 6;
  const bloom = Skia.Paint();
  bloom.setShader(Skia.Shader.MakeRadialGradient({ x: sx, y: sy }, w * 0.9, [Skia.Color('rgba(255, 214, 160, 0.55)'), Skia.Color('rgba(255, 170, 140, 0.18)'), Skia.Color('rgba(255, 150, 140, 0)')], [0, 0.35, 1], TileMode.Clamp));
  c.drawRect(Skia.XYWHRect(0, 0, w, h), bloom);
  const disc = Skia.Paint();
  disc.setAntiAlias(true);
  disc.setShader(Skia.Shader.MakeRadialGradient({ x: sx, y: sy }, 26, [Skia.Color('#FFF6DE'), Skia.Color('#FFE2A6'), Skia.Color('rgba(255, 200, 140, 0)')], [0, 0.75, 1], TileMode.Clamp));
  c.drawCircle(sx, sy, 26, disc);
  // The last stars near the top.
  const star = Skia.Paint();
  star.setAntiAlias(true);
  for (let i = 0; i < 40; i++) {
    const y = hash(i * 3.7) * horizon * 0.42;
    star.setColor(Skia.Color(`rgba(255, 246, 230, ${0.15 + 0.45 * hash(i * 1.3) * (1 - y / (horizon * 0.42))})`));
    c.drawCircle(hash(i * 9.1) * w, y, 0.5 + hash(i * 5.3) * 0.8, star);
  }
  const grain = Skia.Paint();
  grain.setShader(Skia.Shader.MakeFractalNoise(0.8, 0.8, 2, 11, 0, 0));
  grain.setAlphaf(0.06);
  grain.setBlendMode(BlendMode.Overlay);
  c.drawRect(Skia.XYWHRect(0, 0, w, h), grain);
}

function ridgePath(w: number, top: number, amp: number, seed: number, bottom: number, n: number): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-12, bottom);
  let px = -12;
  let py = top + amp * hash(seed);
  b.lineTo(px, py);
  for (let i = 1; i <= n; i++) {
    const x = -12 + ((w + 24) * i) / n;
    const y = top + amp * (0.5 + 0.5 * Math.sin(i * 1.7 + seed) * hash(seed + i)) + amp * 0.3 * hash(seed + i * 2.3);
    b.quadTo((px + x) / 2, (py + y) / 2 - amp * 0.25, x, y);
    px = x;
    py = y;
  }
  b.lineTo(w + 12, bottom);
  b.close();
  return b.build();
}

/** One layer of hills: a ridge filled with a three-stop gradient, a rim of dawn light on top,
 * trees on the nearer ones. Drawn with its ridge at y = 60 of the layer. */
function drawHill(c: SkCanvas, w: number, h: number, i: number) {
  const tops = [['#C291A4', '#A57D9B', '#8A6D90'], ['#93708F', '#735C82', '#5A4C72'], ['#4D5672', '#3D4A62', '#2C3850'], ['#33463F', '#283A34', '#1C2A26']];
  const amps = [46, 34, 26, 14];
  const path = ridgePath(w, 60, amps[i], 3 + i * 11, h, 6 + i * 2);
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 40 }, { x: 0, y: 60 + 220 }, [Skia.Color(tops[i][0]), Skia.Color(tops[i][1]), Skia.Color(tops[i][2])], [0, 0.4, 1], TileMode.Clamp));
  c.drawPath(path, fill);
  // Dawn light along the ridge, warmer to the right where the sun is.
  c.save();
  c.clipPath(path, ClipOp.Intersect, true);
  const rim = Skia.Paint();
  rim.setAntiAlias(true);
  rim.setStyle(PaintStyle.Stroke);
  rim.setStrokeWidth(3 + i);
  rim.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: w, y: 0 }, [Skia.Color('rgba(255, 200, 170, 0.05)'), Skia.Color('rgba(255, 200, 160, 0.22)'), Skia.Color('rgba(255, 214, 160, 0.45)')], [0, 0.6, 1], TileMode.Clamp));
  rim.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2, true));
  c.drawPath(path, rim);
  if (i >= 2) {
    // Tree clusters and hedgerows.
    const tree = Skia.Paint();
    tree.setAntiAlias(true);
    tree.setColor(Skia.Color(i === 2 ? 'rgba(30, 40, 58, 0.75)' : 'rgba(16, 28, 24, 0.85)'));
    for (let k = 0; k < (i === 2 ? 26 : 18); k++) {
      const x = hash(k * 7.1 + i) * w;
      const y = 60 + amps[i] * 0.9 + hash(k * 3.3 + i) * 40;
      const r = (i === 2 ? 4 : 6) + hash(k * 1.7) * 5;
      c.drawCircle(x, y, r, tree);
      c.drawCircle(x + r * 0.8, y + 1, r * 0.8, tree);
    }
    const field = Skia.Paint();
    field.setAntiAlias(true);
    field.setStyle(PaintStyle.Stroke);
    field.setStrokeWidth(1);
    field.setColor(Skia.Color('rgba(255, 220, 190, 0.06)'));
    for (let k = 0; k < 6; k++) {
      const b = Skia.PathBuilder.Make();
      const y = 60 + amps[i] + 30 + k * 18;
      b.moveTo(0, y);
      b.quadTo(w / 2, y + 10 * Math.sin(k), w, y - 6);
      c.drawPath(b.build(), field);
    }
  }
  c.restore();
}

function drawCloudSprite(c: SkCanvas, k: number) {
  const puffs = [
    [[40, 44, 22], [66, 34, 28], [96, 40, 24], [120, 48, 18], [24, 52, 14]],
    [[30, 46, 18], [56, 38, 24], [86, 34, 26], [114, 44, 20], [138, 52, 12]],
    [[36, 48, 16], [60, 40, 22], [84, 44, 18], [104, 50, 12]],
  ][k];
  const path = Skia.PathBuilder.Make();
  for (const p of puffs) path.addCircle(p[0], p[1], p[2]);
  path.addRRect(Skia.RRectXY(Skia.XYWHRect(18, 44, 128, 16), 8, 8));
  const shape = path.build();
  const body = Skia.Paint();
  body.setAntiAlias(true);
  body.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: 10 }, { x: 0, y: 62 }, [Skia.Color('#FFF1E4'), Skia.Color('#F7C9C2'), Skia.Color('#B898B8')], [0, 0.5, 1], TileMode.Clamp));
  body.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3.5, false));
  c.drawPath(shape, body);
  const lit = Skia.Paint();
  lit.setAntiAlias(true);
  lit.setBlendMode(BlendMode.Screen);
  lit.setShader(Skia.Shader.MakeRadialGradient({ x: 120, y: 24 }, 70, [Skia.Color('rgba(255, 226, 180, 0.6)'), Skia.Color('rgba(255, 226, 180, 0)')], [0, 1], TileMode.Clamp));
  c.save();
  c.clipPath(shape, ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(0, 0, 160, 70), lit);
  c.restore();
}

export const HILL_TOPS = [-150, -112, -80, -36];
export const HILL_PAR = [0.18, 0.4, 0.7, 1.0];

/** Bakes everything static at up to 2× (all of it is in the first frame). */
export function bakeArt(g: BalloonGeo, scale: number): BalloonArt {
  const s = Math.min(2, scale);
  const horizon = g.ground + HILL_TOPS[0] + 60;
  const sky = bake(g.w, g.h, s, (c) => drawSky(c, g.w, g.h, horizon));
  const hills: Layer[] = [];
  for (let i = 0; i < 4; i++) {
    const y = g.ground + HILL_TOPS[i] - 60;
    const height = g.h - y + g.scroll * 2 * HILL_PAR[i] + 40;
    hills.push({ image: bake(g.w, height, s, (c) => drawHill(c, g.w, height, i)), y, height, par: HILL_PAR[i] });
  }
  const clouds: SkImage[] = [];
  for (let k = 0; k < 3; k++) clouds.push(bake(160, 70, s, (c) => drawCloudSprite(c, k)));
  return { sky, hills, clouds };
}

