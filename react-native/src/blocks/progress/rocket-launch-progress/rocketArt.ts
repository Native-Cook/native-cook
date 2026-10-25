/*
 Rocket Launch Progress — layout and the art drawn once:
 - the back: a dusk sky (four stops), stars, a moon, two ridgelines, the ground, the pad with its
   flame trench, the service tower, floodlights, the fuel tank's shell and its line to the pad —
   one screen-sized image at the screen's pixel ratio;
 - the rocket: a handful of static paths in its own frame (nozzle exit at the origin, nose up),
   drawn under a transform that the loop moves.
*/
import { BlurStyle, PaintStyle, Skia, TileMode, matchFont, type SkCanvas, type SkImage, type SkPaint, type SkPath } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

export const PAL = {
  ink: '#F3EEF8',
  inkSoft: 'rgba(243,238,248,0.64)',
  inkFaint: 'rgba(243,238,248,0.4)',
  amber: '#FFA94D',
  amberDeep: '#E8772A',
  night: '#0B0D26',
  card: '#17142A',
};

export type Layout = {
  W: number;
  H: number;
  top: number;
  groundY: number;
  padX: number;
  rocketH: number;
  /** Scale of the rocket's paths (designed 136 pt tall). */
  k: number;
  tank: { x: number; y: number; w: number; h: number };
  cardTop: number;
  cardH: number;
  rowH: number;
};

export function makeLayout(W: number, H: number, top: number, bottom: number): Layout {
  const rowH = H < 740 ? 46 : 52;
  const cardH = rowH * 4 + 62;
  const cardTop = H - bottom - 12 - cardH;
  const groundY = cardTop - 30;
  const rocketH = Math.max(96, Math.min(150, (groundY - (top + 120)) * 0.5));
  const padX = Math.round(W * 0.46);
  const tankH = rocketH * 0.78;
  return {
    W,
    H,
    top,
    groundY,
    padX,
    rocketH,
    k: rocketH / 136,
    tank: { x: padX + 70 * (rocketH / 136) + 10, y: groundY - 12 - tankH, w: 30, h: tankH },
    cardTop,
    cardH,
    rowH,
  };
}

function paint(color: string, alpha = 1): SkPaint {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(alpha);
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos: number[] | null): SkPaint {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp));
  return p;
}

function radial(x: number, y: number, r: number, colors: string[], pos: number[] | null): SkPaint {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp));
  return p;
}

function blur(p: SkPaint, sigma: number) {
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function stroke(p: SkPaint, w: number) {
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  return p;
}

function hash(n: number) {
  const v = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return v - Math.floor(v);
}

function ridge(c: SkCanvas, W: number, base: number, amp: number, seed: number, color: string, alpha: number) {
  const b = Skia.PathBuilder.Make();
  b.moveTo(0, base + 40);
  for (let x = 0; x <= W + 20; x += 20) {
    const h = Math.sin(x * 0.012 + seed) * 0.5 + Math.sin(x * 0.031 + seed * 2.1) * 0.3 + hash(Math.floor(x / 20) + seed * 10) * 0.2;
    b.lineTo(x, base - amp * (0.5 + h));
  }
  b.lineTo(W + 20, base + 40);
  b.close();
  c.drawPath(b.build(), paint(color, alpha));
}

/** The whole still scene behind the rocket, at px pixels per point. */
export function bakeBack(L: Layout, px: number): SkImage | null {
  const { W, H, groundY: g, padX, tank, k } = L;
  const surface = Skia.Surface.MakeOffscreen(Math.ceil(W * px), Math.ceil(H * px)) ?? Skia.Surface.Make(Math.ceil(W * px), Math.ceil(H * px));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(px, px);
  // Sky: night overhead, violet, a warm band at the horizon.
  c.drawRect(Skia.XYWHRect(0, 0, W, g), linear(0, 0, 0, g, ['#090B22', '#1C1844', '#4E2C5E', '#C46A58'], [0, 0.42, 0.78, 1]));
  for (let i = 0; i < 170; i++) {
    const x = hash(i * 3.7) * W;
    const y = hash(i * 5.1) * g * 0.62;
    const r = 0.4 + hash(i * 9.3) * 0.9;
    c.drawCircle(x, y, r, paint('#FFF6E6', (0.25 + hash(i * 2.2) * 0.65) * (1 - y / (g * 0.7))));
  }
  const mx = W * 0.82;
  const my = L.top + 150;
  c.drawCircle(mx, my, 46, radial(mx, my, 46, ['rgba(255,236,200,0.28)', 'rgba(255,220,180,0)'], null));
  c.drawCircle(mx, my, 13, radial(mx - 4, my - 4, 16, ['#FFF8E8', '#EADBBE', '#BFAE90'], [0, 0.6, 1]));
  ridge(c, W, g - 34, 46, 1.3, '#3B2550', 0.85);
  ridge(c, W, g - 8, 30, 4.1, '#22183A', 1);
  // Ground: dark, warm under the pad, a horizon haze.
  c.drawRect(Skia.XYWHRect(0, g - 4, W, H - g + 4), linear(0, g - 4, 0, H, ['#211A2E', '#140F1F', '#0D0A15'], [0, 0.3, 1]));
  c.drawRect(Skia.XYWHRect(0, g - 26, W, 30), blur(paint('#E39A7A', 0.14), 10));
  // Floodlights: two poles, their pools of light on the pad.
  for (const side of [-1, 1]) {
    const lx = padX + side * 150;
    c.drawRect(Skia.XYWHRect(lx - 1, g - 120 * k, 2, 120 * k), paint('#3A3150'));
    c.drawCircle(lx, g - 120 * k, 3, paint('#FFF4D6'));
    c.drawCircle(lx, g - 120 * k, 16, radial(lx, g - 120 * k, 16, ['rgba(255,240,200,0.5)', 'rgba(255,240,200,0)'], null));
  }
  c.drawOval(Skia.XYWHRect(padX - 150, g - 18, 300, 36), radial(padX, g, 150, ['rgba(255,226,180,0.24)', 'rgba(255,200,150,0.06)', 'rgba(0,0,0,0)'], [0, 0.6, 1]));

  // The service tower: two rails, X braces, arms, a red beacon.
  const tx0 = padX - 52 * k;
  const tx1 = padX - 34 * k;
  const tTop = g - 14 - L.rocketH * 1.12;
  const rail = linear(tx0, 0, tx1, 0, ['#2E2742', '#5A4E72', '#2A2340'], [0, 0.5, 1]);
  c.drawRect(Skia.XYWHRect(tx0 - 1.5, tTop, 3, g - tTop), rail);
  c.drawRect(Skia.XYWHRect(tx1 - 1.5, tTop, 3, g - tTop), rail);
  const brace = stroke(paint('#4A3F62'), 1.2);
  const step = 14 * k;
  for (let y = tTop; y < g - 14; y += step) {
    c.drawLine(tx0, y, tx1, y + step, brace);
    c.drawLine(tx1, y, tx0, y + step, brace);
    c.drawLine(tx0, y, tx1, y, brace);
  }
  for (const f of [0.25, 0.6]) {
    const ay = g - 14 - L.rocketH * f;
    c.drawRect(Skia.XYWHRect(tx1, ay - 1.5, padX - 15 * k - tx1, 3), paint('#4E4366'));
  }
  c.drawCircle(tx0 + 9 * k, tTop - 3, 8, radial(tx0 + 9 * k, tTop - 3, 8, ['rgba(255,80,60,0.7)', 'rgba(255,60,40,0)'], null));
  c.drawCircle(tx0 + 9 * k, tTop - 3, 2, paint('#FF6A50'));

  // The pad: a concrete deck with a lit top edge and the trench under the nozzle.
  const pw = 128 * k;
  c.drawRect(Skia.XYWHRect(padX - pw / 2 - 6, g - 2, pw + 12, 10), blur(paint('#000000', 0.5), 5));
  c.drawRect(Skia.XYWHRect(padX - pw / 2, g - 14, pw, 14), linear(0, g - 14, 0, g, ['#6C6280', '#463D58', '#2C2539'], [0, 0.3, 1]));
  c.drawRect(Skia.XYWHRect(padX - pw / 2, g - 14, pw, 1), paint('#B8AEC9', 0.8));
  c.drawRect(Skia.XYWHRect(padX - 12 * k, g - 14, 24 * k, 14), linear(0, g - 14, 0, g, ['#120E1A', '#2A2238'], null));

  // The fuel line from the tank's foot to the pad.
  const line = Skia.PathBuilder.Make();
  line.moveTo(tank.x + tank.w / 2, tank.y + tank.h);
  line.quadTo(tank.x + tank.w / 2, g - 8, padX + pw / 2 - 4, g - 8);
  c.drawPath(line.build(), stroke(paint('#5B5070'), 4));
  // The tank's cradle and its dark well (the fuel is drawn over it each frame).
  c.drawRect(Skia.XYWHRect(tank.x - 4, g - 14, tank.w + 8, 14), linear(0, g - 14, 0, g, ['#5E5474', '#2E273D'], null));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(tank.x - 3, tank.y - 3, tank.w + 6, tank.h + 6), tank.w / 2 + 3, tank.w / 2 + 3), linear(tank.x, 0, tank.x + tank.w, 0, ['#4C4462', '#9C93B4', '#3A3350'], [0, 0.4, 1]));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(tank.x, tank.y, tank.w, tank.h), tank.w / 2, tank.w / 2), linear(0, tank.y, 0, tank.y + tank.h, ['#1A1526', '#110D1A'], null));
  const label = matchFont({ fontFamily: Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' }), fontSize: 8, fontWeight: 'bold' });
  c.drawText('FUEL', tank.x + tank.w + 7, tank.y + 10, paint('#CFC6E2', 0.7), label);

  // Grain over the sky and ground.
  for (let i = 0; i < 5000; i++) c.drawRect(Skia.XYWHRect(hash(i * 1.9) * W, hash(i * 2.6 + 3) * H, 0.7, 0.7), paint(hash(i * 4.4) > 0.5 ? '#FFFFFF' : '#000000', 0.035));
  surface.flush();
  const img = surface.makeImageSnapshot();
  return img.makeNonTextureImage() ?? img;
}

/** The rocket's parts, nozzle exit at (0, 0), nose up, 136 pt tall; scaled by k when drawn. */
export function rocketPaths(): Record<'nozzle' | 'body' | 'nose' | 'finL' | 'finR' | 'finC' | 'band', SkPath> {
  const pb = () => Skia.PathBuilder.Make();
  const nozzle = pb().moveTo(-8, 0).lineTo(8, 0).quadTo(6, -6, 5, -12).lineTo(-5, -12).quadTo(-6, -6, -8, 0).close().build();
  const body = pb().moveTo(-15, -12).lineTo(15, -12).lineTo(15, -96).lineTo(-15, -96).close().build();
  const nose = pb().moveTo(-15, -96).cubicTo(-15, -114, -8, -128, 0, -136).cubicTo(8, -128, 15, -114, 15, -96).close().build();
  const finL = pb().moveTo(-15, -16).lineTo(-28, -6).quadTo(-30, -24, -15, -46).close().build();
  const finR = pb().moveTo(15, -16).lineTo(28, -6).quadTo(30, -24, 15, -46).close().build();
  const finC = pb().moveTo(-2.5, -8).lineTo(2.5, -8).lineTo(2.5, -40).lineTo(-2.5, -40).close().build();
  const band = pb().moveTo(-15, -58).lineTo(15, -58).lineTo(15, -64).lineTo(-15, -64).close().build();
  return { nozzle, body, nose, finL, finR, finC, band };
}
