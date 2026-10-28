import {
  AlphaType,
  BlurStyle,
  ColorType,
  FilterMode,
  MipmapMode,
  PaintStyle,
  Skia,
  StrokeCap,
  StrokeJoin,
  TileMode,
  type SkCanvas,
  type SkColor,
  type SkImage,
  type SkPaint,
  type SkPath,
} from '@shopify/react-native-skia';

import { LOOP, lerp } from './inlinePillModel';

/*
 The sunrise scene inside the pill, drawn in code (a port of InlinePillRevealArt.swift): sky, sun,
 birds, three ridges with cypresses, a farmhouse with a lit window, a road and mist. The scene is
 small inside the inline pill and zooms to 1 at full bleed while the sun climbs over the ridge.
 Helpers sit above the worklets that call them.
*/

export function hex(v: number, a: number): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, Math.min(1, Math.max(0, a)));
}

export function solid(v: number, a: number): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(hex(v, a));
  return p;
}

/** SwiftUI's blur radius as the sigma Android's BlurMaskFilter gives (1.5 × radius), so the stacks soften alike. */
export function blurred(p: SkPaint, radius: number): SkPaint {
  'worklet';
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.57735 * radius * 1.5 + 0.5, true));
  return p;
}

function vertical(top: number, bottom: number, a: number, b: number): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: top }, { x: 0, y: bottom }, [hex(a, 1), hex(b, 1)], null, TileMode.Clamp));
  return p;
}

function radial(x: number, y: number, r: number, colors: SkColor[], pos: number[] | null): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, Math.max(1e-4, r), colors, pos, TileMode.Clamp));
  return p;
}

function farY(x: number) {
  'worklet';
  return -40 + 10 * Math.sin(2 * Math.PI * 1.3 * x + 0.6) + 6 * Math.sin(2 * Math.PI * 3.1 * x + 1.9);
}

function midY(x: number) {
  'worklet';
  return 10 + 16 * Math.sin(2 * Math.PI * 0.9 * x + 2.2) + 7 * Math.sin(2 * Math.PI * 2.4 * x + 0.3);
}

function nearY(x: number) {
  'worklet';
  return 120 + 34 * Math.sin(2 * Math.PI * 0.6 * x + 4.0) + 10 * Math.sin(2 * Math.PI * 1.7 * x + 1.0);
}

function foreY(x: number) {
  'worklet';
  return 250 - 60 * x + 12 * Math.sin(2 * Math.PI * 1.1 * x);
}

/** A ridge filled down past the bottom, its edge sampled every 8 pt. kind: 0 far, 1 mid, 2 near, 3 fore. */
function ridge(kind: number, w: number, vx0: number, vx1: number, vBottom: number, cy: number, ox: number, oy: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  const x0 = Math.floor(vx0 / 8) * 8;
  p.moveTo(x0, vBottom + 300);
  for (let x = x0; x <= vx1 + 0.1; x += 8) {
    const u = (x - ox) / w;
    const y = kind === 0 ? farY(u) : kind === 1 ? midY(u) : kind === 2 ? nearY(u) : foreY(u);
    p.lineTo(x, cy + y + oy);
  }
  p.lineTo(vx1, vBottom + 300);
  p.close();
  return p;
}

/** A soft radial blob squashed vertically (mist, the scrim). */
function squashedGlow(c: SkCanvas, x: number, y: number, r: number, sy: number, color: number, alpha: number) {
  'worklet';
  if (alpha <= 0.001) return;
  c.save();
  c.translate(x, y);
  c.scale(1, sy);
  c.drawCircle(0, 0, r, radial(0, 0, r, [hex(color, alpha), hex(color, 0)], null));
  c.restore();
}

/** A road winding down the foreground, widening towards the viewer. */
function road(w: number, cy: number, ox: number, oy: number): SkPath {
  'worklet';
  const x0 = 0.42;
  const p0x = x0 * w + ox;
  const p0y = cy + foreY(x0) + oy + 3;
  const p1x = 0.3 * w + ox;
  const p1y = p0y + 80;
  const p2x = 0.8 * w + ox;
  const p2y = p0y + 150;
  const p3x = 0.58 * w + ox;
  const p3y = p0y + 260;
  const n = 24;
  const lx: number[] = [];
  const ly: number[] = [];
  const rx: number[] = [];
  const ry: number[] = [];
  for (let i = 0; i <= n; i++) {
    const s = i / n;
    const a = (1 - s) * (1 - s) * (1 - s);
    const b = 3 * (1 - s) * (1 - s) * s;
    const c = 3 * (1 - s) * s * s;
    const d = s * s * s;
    const px = a * p0x + b * p1x + c * p2x + d * p3x;
    const py = a * p0y + b * p1y + c * p2y + d * p3y;
    const da = -3 * (1 - s) * (1 - s);
    const db = 3 * (1 - s) * (1 - s) - 6 * (1 - s) * s;
    const dc = 6 * (1 - s) * s - 3 * s * s;
    const dd = 3 * s * s;
    const tx = da * p0x + db * p1x + dc * p2x + dd * p3x;
    const ty = da * p0y + db * p1y + dc * p2y + dd * p3y;
    const len = Math.max(0.001, Math.hypot(tx, ty));
    const hw = 0.8 + 13 * s * s;
    lx.push(px - (ty / len) * hw);
    ly.push(py + (tx / len) * hw);
    rx.push(px + (ty / len) * hw);
    ry.push(py - (tx / len) * hw);
  }
  const p = Skia.Path.Make();
  p.moveTo(lx[0], ly[0]);
  for (let i = 1; i <= n; i++) p.lineTo(lx[i], ly[i]);
  for (let i = n; i >= 0; i--) p.lineTo(rx[i], ry[i]);
  p.close();
  return p;
}

const MIST_X = [-0.18, 0.18, 0.55, 0.92, 1.36];
const MIST_Y = [0, 6, -4, 8, 2];
const TREE_X = [-0.36, -0.31, 0.1, 0.14, 0.56, 0.6, 0.64, 0.9, 1.24, 1.29, 1.52];
const TREE_H = [30, 22, 34, 24, 42, 30, 36, 26, 38, 28, 32];
const BIRD_X = [0.4, 0.46, 0.35];
const BIRD_Y = [-205, -192, -186];
const BIRD_K = [9, 10, 11];
const BIRD_PH = [0, 1.7, 3.1];

/** The whole scene at full bleed; open and full place the layers, t drives the idle life. */
export function scene(c: SkCanvas, w: number, h: number, cy: number, open: number, full: number, t: number) {
  'worklet';
  const k = 1 - (0.1 * open + 0.9 * full);
  const side = (1 - open) * 26;
  const phase = (2 * Math.PI * t) / LOOP;

  // Zoom about the line: screen = cy + (natural − (cy + look)) · zoom.
  const zp = 0.12 * open + 0.88 * full;
  const zoom = lerp(0.42, 1, zp);
  const look = -60 * (1 - zp);
  const vx0 = w / 2 - w / 2 / zoom - 40;
  const vx1 = w / 2 + w / 2 / zoom + 40;
  const vy0 = cy + look - cy / zoom - 40;
  const vy1 = vy0 + h / zoom + 80;

  c.save();
  c.translate(w / 2, cy);
  c.scale(zoom, zoom);
  c.translate(-w / 2, -(cy + look));

  // Sky
  const sky = Skia.Paint();
  sky.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: 0 },
      { x: 0, y: cy - 30 },
      [hex(0x4f6f96, 1), hex(0x8ea3c0, 1), hex(0xe4b8a2, 1), hex(0xf7d3a8, 1), hex(0xfbe2bd, 1)],
      [0, 0.45, 0.78, 0.93, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(vx0, vy0, vx1 - vx0, vy1 - vy0), sky);

  // Sun and its glow
  const sx = 0.7 * w + side * 0.15;
  const sy = cy - 175 + k * 85;
  const glowR = 150 * (1 + 0.06 * Math.sin(2 * phase));
  c.drawCircle(sx, sy, glowR, radial(sx, sy, glowR, [hex(0xffe2a8, 0.55), hex(0xffe2a8, 0.18), hex(0xffe2a8, 0)], null));
  c.drawCircle(sx, sy, 24, solid(0xfff5de, 1));

  // Birds
  const birdPaint = solid(0x4e3436, 0.7);
  birdPaint.setStyle(PaintStyle.Stroke);
  birdPaint.setStrokeWidth(1.4);
  birdPaint.setStrokeCap(StrokeCap.Round);
  birdPaint.setStrokeJoin(StrokeJoin.Round);
  for (let i = 0; i < 3; i++) {
    const bx = BIRD_X[i] * w + side * 0.2 + 8 * Math.sin(phase + i);
    const by = cy + BIRD_Y[i] + k * 40 + 3 * Math.sin(2 * phase + i * 2);
    const wing = Math.sin(BIRD_K[i] * phase + BIRD_PH[i]);
    const p = Skia.Path.Make();
    p.moveTo(bx - 7, by - 2 - 3 * wing);
    p.quadTo(bx - 3, by - 4 - 2 * wing, bx, by);
    p.quadTo(bx + 3, by - 4 - 2 * wing, bx + 7, by - 2 - 3 * wing);
    c.drawPath(p, birdPaint);
  }

  // Far ridge and mist
  const fx = side * 0.3;
  c.drawPath(ridge(0, w, vx0, vx1, vy1, cy, fx, 0), vertical(cy - 52, cy + 60, 0xc48f88, 0xa9736d));
  for (let i = 0; i < 5; i++) {
    squashedGlow(c, MIST_X[i] * w + fx + 14 * Math.sin(phase + i * 2.1), cy - 12 + MIST_Y[i], 120, 0.14, 0xfff1e4, 0.3);
  }

  // Mid ridge with cypresses
  const mx = side * 0.55;
  const my = k * 10;
  const treePaint = solid(0x5a322c, 1);
  for (let i = 0; i < TREE_X.length; i++) {
    const tx = TREE_X[i] * w + mx;
    const th = TREE_H[i];
    const base = cy + midY(TREE_X[i]) + my + 5;
    const tw = th * 0.24;
    const p = Skia.Path.Make();
    p.moveTo(tx - tw / 2, base);
    p.quadTo(tx - tw * 0.62, base - th * 0.62, tx, base - th);
    p.quadTo(tx + tw * 0.62, base - th * 0.62, tx + tw / 2, base);
    p.close();
    c.drawPath(p, treePaint);
  }
  c.drawPath(ridge(1, w, vx0, vx1, vy1, cy, mx, my), vertical(cy - 10 + my, cy + 120 + my, 0xa35e54, 0x86483f));

  // Near hill with the farmhouse
  const nx = side * 0.8;
  const ny = k * 24;
  c.drawPath(ridge(2, w, vx0, vx1, vy1, cy, nx, ny), vertical(cy + 80 + ny, cy + 240 + ny, 0x7a4036, 0x5d2e27));
  const hx = 0.3 * w + nx;
  const hb = cy + nearY(0.3) + ny + 7;
  const wx = hx + 4;
  const wy = hb - 6;
  c.drawCircle(wx, wy, 22, radial(wx, wy, 22, [hex(0xffd58f, 0.45), hex(0xffd58f, 0)], null));
  c.drawRect(Skia.XYWHRect(hx - 11, hb - 13, 22, 13), solid(0xe6bfa2, 1));
  const roof = Skia.Path.Make();
  roof.moveTo(hx - 14, hb - 12);
  roof.lineTo(hx - 4, hb - 21);
  roof.lineTo(hx + 14, hb - 21);
  roof.lineTo(hx + 15, hb - 12);
  roof.close();
  c.drawPath(roof, solid(0x5a2b24, 1));
  c.drawRect(Skia.XYWHRect(wx - 2, wy - 2.5, 4, 5), solid(0xffd58f, 1));

  // Foreground and the road
  const gx = side;
  const gy = k * 40;
  c.drawPath(ridge(3, w, vx0, vx1, vy1, cy, gx, gy), vertical(cy + 200 + gy, h + gy, 0x4a2622, 0x2f1714));
  c.drawPath(road(w, cy, gx, gy), solid(0xf2c9a4, 0.32));
  c.restore();

  // Vignette and a soft scrim behind the second line
  const vr = Math.max(w, h) * 0.62;
  c.drawRect(
    Skia.XYWHRect(0, 0, w, h),
    radial(w / 2, cy, vr, [hex(0, 0), hex(0, 0), hex(0x1a0c08, 0.26)], [0, 0.5, 1]),
  );
  squashedGlow(c, w / 2, cy, 210, 0.45, 0x2a1410, 0.22 * full);
}

/** A 128 × 128 tile of seeded noise (the same hash as the Swift tile), made once on the JS thread. */
export function makeGrain(): SkImage | null {
  const n = 128;
  const px = new Uint8Array(n * n * 4);
  for (let i = 0; i < n * n; i++) {
    const v = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
    const r = v - Math.floor(v);
    const g = r > 0.5 ? 255 : 0;
    const a = Math.floor(Math.abs(r - 0.5) * 2 * 255);
    const pm = Math.floor((g * a) / 255);
    px[i * 4] = pm;
    px[i * 4 + 1] = pm;
    px[i * 4 + 2] = pm;
    px[i * 4 + 3] = a;
  }
  return Skia.Image.MakeImage(
    { width: n, height: n, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Premul },
    Skia.Data.fromBytes(px),
    n * 4,
  );
}

export function grainPaint(image: SkImage, amount: number): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setShader(image.makeShaderOptions(TileMode.Repeat, TileMode.Repeat, FilterMode.Nearest, MipmapMode.None));
  p.setAlphaf(amount);
  return p;
}
