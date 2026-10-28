import { PaintStyle, Skia, StrokeCap, TileMode, type SkCanvas, type SkColor, type SkPath, type SkShader } from '@shopify/react-native-skia';

/*
 Prism Beam Folder — the artwork, drawn in code (a port of PrismBeamFolderArt.swift, same paths):
 the folder (back panel with its tab, a front panel that tilts open) and the four items (peach,
 cherries, aubergine, chili), each in a 48-unit box centred on (0, 0). Worklets; helpers first.
*/

export function hex(v: number, a = 1): SkColor {
  'worklet';
  return Float32Array.of(((v >> 16) & 0xff) / 255, ((v >> 8) & 0xff) / 255, (v & 0xff) / 255, a);
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: SkColor[], pos: number[] | null = null): SkShader {
  'worklet';
  return Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors, pos, TileMode.Clamp);
}

export function radial(cx: number, cy: number, r: number, colors: SkColor[], pos: number[] | null = null): SkShader {
  'worklet';
  return Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, r, colors, pos, TileMode.Clamp);
}

export function fillShader(c: SkCanvas, p: SkPath, shader: SkShader, alpha = 1) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setShader(shader);
  paint.setAlphaf(alpha);
  c.drawPath(p, paint);
}

export function fillColor(c: SkCanvas, p: SkPath, color: SkColor) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(color);
  c.drawPath(p, paint);
}

export function strokeColor(c: SkCanvas, p: SkPath, color: SkColor, width: number, round = true) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(color);
  paint.setStyle(PaintStyle.Stroke);
  paint.setStrokeWidth(width);
  if (round) paint.setStrokeCap(StrokeCap.Round);
  c.drawPath(p, paint);
}

export function ellipse(x: number, y: number, w: number, h: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.addOval(Skia.XYWHRect(x, y, w, h));
  return p;
}

function line(x0: number, y0: number, x1: number, y1: number): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.moveTo(x0, y0);
  p.lineTo(x1, y1);
  return p;
}

// Folder (104 × 84, centred on (0, 0); the tab rises to y = -50)

function backPath(): SkPath {
  'worklet';
  const p = Skia.Path.Make();
  p.moveTo(-52, -44);
  p.quadTo(-52, -50, -46, -50);
  p.lineTo(-22, -50);
  p.quadTo(-19.5, -50, -17, -47.5);
  p.lineTo(-13, -43.5);
  p.quadTo(-11.2, -42, -9, -42);
  p.lineTo(42, -42);
  p.quadTo(52, -42, 52, -32);
  p.lineTo(52, 32);
  p.quadTo(52, 42, 42, 42);
  p.lineTo(-42, 42);
  p.quadTo(-52, 42, -52, 32);
  p.close();
  return p;
}

function frontPath(o: number): SkPath {
  'worklet';
  const top = -30 + 8 * o;
  const half = 52 + 3 * o;
  const p = Skia.Path.Make();
  p.moveTo(-half, top + 7);
  p.quadTo(-half, top, -half + 7, top);
  p.lineTo(half - 7, top);
  p.quadTo(half, top, half, top + 7);
  p.lineTo(52, 32);
  p.quadTo(52, 42, 42, 42);
  p.lineTo(-42, 42);
  p.quadTo(-52, 42, -52, 32);
  p.close();
  return p;
}

export function drawBack(c: SkCanvas) {
  'worklet';
  fillShader(c, backPath(), linear(0, -50, 0, 42, [hex(0x3a9ad3), hex(0x2c7fb8)]));
  const inside = Skia.Path.Make();
  inside.addRect(Skia.XYWHRect(-48, -40, 96, 12));
  fillShader(c, inside, linear(0, -40, 0, -28, [hex(0x0b2a44, 0), hex(0x0b2a44, 0.45)]));
}

export function drawFront(c: SkCanvas, o: number, light: number) {
  'worklet';
  const path = frontPath(o);
  const top = -30 + 8 * o;
  fillShader(c, path, linear(0, top, 0, 42, [hex(0x8edcf9), hex(0x56bdec)]));
  if (light > 0.001) {
    fillShader(c, path, linear(0, top, 0, top + 40, [hex(0xffe9f6, 0.55 * light), hex(0xffe9f6, 0)]));
  }
  strokeColor(c, line(-52 - 3 * o + 7, top + 0.75, 52 + 3 * o - 7, top + 0.75), hex(0xffffff, 0.55), 1.5);
  strokeColor(c, line(-42, 41.25, 42, 41.25), hex(0x2c7fb8, 0.7), 1.5, false);
}

// Items

function leaf(c: SkCanvas, p: SkPath, x0: number, y0: number, x1: number, y1: number) {
  'worklet';
  fillShader(c, p, linear(x0, y0, x1, y1, [hex(0x7bd36b), hex(0x2e8b57)]));
}

function peach(c: SkCanvas) {
  'worklet';
  fillShader(
    c,
    ellipse(-19, -15, 38, 36),
    radial(-6, -6, 28, [hex(0xffd08a), hex(0xff8a5b), hex(0xe2474f)], [0, 0.55, 1]),
  );
  const crease = Skia.Path.Make();
  crease.moveTo(1, -13);
  crease.quadTo(-8, -4, -3, 6);
  strokeColor(c, crease, hex(0xc93a44, 0.5), 1.6);
  fillColor(c, ellipse(-13, -9, 8, 6), hex(0xffffff, 0.35));
  strokeColor(c, line(1, -13, -1, -19), hex(0x6b4b2a), 2);
  const l = Skia.Path.Make();
  l.moveTo(2, -14);
  l.quadTo(6, -24, 16, -22);
  l.quadTo(14, -13, 2, -14);
  l.close();
  leaf(c, l, 6, -22, 12, -14);
}

function cherries(c: SkCanvas) {
  'worklet';
  const stems = Skia.Path.Make();
  stems.moveTo(-8, 2);
  stems.quadTo(-6, -12, 6, -20);
  stems.moveTo(9, 4);
  stems.quadTo(12, -8, 6, -20);
  strokeColor(c, stems, hex(0x4e7d2b), 2);
  const l = Skia.Path.Make();
  l.moveTo(6, -20);
  l.quadTo(14, -25, 18, -18);
  l.quadTo(12, -14, 6, -20);
  l.close();
  leaf(c, l, 8, -24, 16, -16);
  const balls = [
    [-9, 10],
    [9, 12],
  ];
  for (let i = 0; i < balls.length; i++) {
    const x = balls[i][0];
    const y = balls[i][1];
    fillShader(
      c,
      ellipse(x - 10, y - 10, 20, 20),
      radial(x - 3, y - 3, 14, [hex(0xff6a7a), hex(0xd0142f), hex(0x7a0019)], [0, 0.6, 1]),
    );
    fillColor(c, ellipse(x - 6, y - 6.5, 4.5, 3.2), hex(0xffffff, 0.7));
  }
}

function aubergine(c: SkCanvas) {
  'worklet';
  const body = Skia.Path.Make();
  body.moveTo(-2, -14);
  body.cubicTo(5, -15, 9, -4, 10, 8);
  body.cubicTo(11, 18, 8, 25, 2, 25);
  body.cubicTo(-5, 25, -8, 14, -9, 4);
  body.cubicTo(-10, -6, -8, -13, -2, -14);
  body.close();
  fillShader(c, body, radial(-3, 2, 24, [hex(0xa779e0), hex(0x5b2a86), hex(0x2e1247)], [0, 0.55, 1]));
  const hi = Skia.Path.Make();
  hi.moveTo(-5, -4);
  hi.quadTo(-7, 8, -2, 16);
  strokeColor(c, hi, hex(0xffffff, 0.4), 2.5);
  const stem = Skia.Path.Make();
  stem.addRRect(Skia.RRectXY(Skia.XYWHRect(-1.5, -22, 3, 8), 1.5, 1.5));
  fillColor(c, stem, hex(0x4e8a3a));
  const cap = Skia.Path.Make();
  cap.moveTo(-9, -11);
  cap.quadTo(-4, -6, 0, -8);
  cap.quadTo(4, -6, 9, -11);
  cap.quadTo(6, -17, 0, -17);
  cap.quadTo(-6, -17, -9, -11);
  cap.close();
  fillShader(c, cap, linear(0, -17, 0, -8, [hex(0x6cc46a), hex(0x2f7d3a)]));
}

function chili(c: SkCanvas) {
  'worklet';
  const pod = Skia.Path.Make();
  pod.moveTo(-8, -14);
  pod.cubicTo(10, -14, 16, 8, 12, 22);
  pod.cubicTo(6, 12, -2, -4, -12, -8);
  pod.quadTo(-12, -14, -8, -14);
  pod.close();
  fillShader(c, pod, linear(-8, -14, 12, 22, [hex(0xff6a45), hex(0xe0201e), hex(0xa50f18)], [0, 0.5, 1]));
  const hi = Skia.Path.Make();
  hi.moveTo(-2, -10);
  hi.quadTo(8, -6, 9, 8);
  strokeColor(c, hi, hex(0xffffff, 0.45), 2.2);
  fillColor(c, ellipse(-15, -18, 11, 7), hex(0x4e9a3a));
  const stem = Skia.Path.Make();
  stem.moveTo(-9, -16);
  stem.quadTo(-8, -23, -3, -24);
  strokeColor(c, stem, hex(0x3e7d2b), 2.5);
}

export function drawItem(c: SkCanvas, kind: number) {
  'worklet';
  if (kind === 0) peach(c);
  else if (kind === 1) cherries(c);
  else if (kind === 2) aubergine(c);
  else chili(c);
}
