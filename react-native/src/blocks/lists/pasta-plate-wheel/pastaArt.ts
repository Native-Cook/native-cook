/*
 Pasta Plate Wheel — the menu and its artwork. Every dish is drawn in code, top-down, once, into
 three images: the plate (its light never turns), the food (it spins with the wheel) and a light
 layer laid over the food (a soft highlight and a darker rim, fixed, so the food turns under the
 light). Built on the JS thread with Skia raster surfaces; ported 1:1 from PastaPlateWheelArt.swift.
 */
import { AlphaType, ColorType, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkImage, type SkPath } from '@shopify/react-native-skia';

export type Dish = { name: string; line: string; price: string; bowl: boolean };

export const DISHES: Dish[] = [
  { name: 'Spaghetti Pomodoro', line: 'Slow tomato, torn basil, olive oil', price: '$12.50', bowl: false },
  { name: 'Penne Arrabbiata', line: 'Fiery tomato, garlic, chilli flakes', price: '$13.00', bowl: true },
  { name: 'Farfalle al Pesto', line: 'Basil pesto, pine nuts, pecorino', price: '$14.50', bowl: false },
  { name: 'Fettuccine Bianca', line: 'Butter, cream, cracked pepper', price: '$15.00', bowl: true },
  { name: 'Baked Mac & Cheese', line: 'Three cheeses, crisp crumb top', price: '$11.50', bowl: false },
  { name: 'Ricotta Ravioli', line: 'Brown butter, sage, lemon zest', price: '$16.00', bowl: true },
  { name: 'Lasagna al Forno', line: 'Beef ragù, béchamel, golden top', price: '$17.50', bowl: false },
  { name: 'Shells & Peas', line: 'Cream, sweet peas, smoked ham', price: '$13.50', bowl: true },
];

/** The largest plate's diameter (design units); images are drawn at this size and only scaled down. */
export const PLATE_SIZE = 164;
const SCALE = 3;

export const BOWL_FLAGS = DISHES.map((d) => d.bowl);

export function foodFraction(bowl: boolean) {
  'worklet';
  return bowl ? 0.76 : 0.75;
}

export function hash(n: number) {
  const v = Math.sin(n * 12.9898) * 43758.5453;
  return v - Math.floor(v);
}

function color(hex: number, a = 1) {
  return Float32Array.of(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, Math.min(1, Math.max(0, a)));
}

function mix(a: number, b: number, t: number) {
  const u = Math.min(1, Math.max(0, t));
  const ch = (s: number) => {
    const x = (a >> s) & 255;
    const y = (b >> s) & 255;
    return Math.round(x + (y - x) * u);
  };
  return (ch(16) << 16) | (ch(8) << 8) | ch(0);
}

/** A gradient stop: [hex, alpha, location]. */
type Stop = [number, number, number];

/** The few primitives every dish is drawn with. */
class Pen {
  readonly c: SkCanvas;
  private fill = Skia.Paint();
  private line = Skia.Paint();

  constructor(c: SkCanvas) {
    this.c = c;
    this.fill.setAntiAlias(true);
    this.line.setAntiAlias(true);
    this.line.setStyle(PaintStyle.Stroke);
    this.line.setStrokeCap(StrokeCap.Round);
    this.line.setStrokeJoin(StrokeJoin.Round);
  }

  circle(x: number, y: number, r: number, hex: number, a = 1) {
    this.fill.setColor(color(hex, a));
    this.c.drawCircle(x, y, r, this.fill);
  }

  ellipse(x: number, y: number, rx: number, ry: number, angle: number, hex: number, a = 1) {
    this.c.save();
    this.c.translate(x, y);
    this.c.rotate((angle * 180) / Math.PI, 0, 0);
    this.fill.setColor(color(hex, a));
    this.c.drawOval(Skia.XYWHRect(-rx, -ry, 2 * rx, 2 * ry), this.fill);
    this.c.restore();
  }

  /** An open smooth line through the points (quadratic curves between midpoints), round caps. */
  stroke(p: number[], width: number, hex: number, a = 1) {
    this.line.setColor(color(hex, a));
    this.line.setStrokeWidth(width);
    this.c.drawPath(smooth(p, false), this.line);
  }

  blob(p: number[], hex: number, a = 1) {
    this.fill.setColor(color(hex, a));
    this.c.drawPath(smooth(p, true), this.fill);
  }

  poly(p: number[], hex: number, a = 1) {
    const path = Skia.PathBuilder.Make();
    path.moveTo(p[0], p[1]);
    for (let i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
    path.close();
    this.fill.setColor(color(hex, a));
    this.c.drawPath(path.detach(), this.fill);
  }

  roundRect(x: number, y: number, w: number, h: number, r: number, hex: number, a = 1) {
    this.fill.setColor(color(hex, a));
    this.c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r), this.fill);
  }

  radial(x: number, y: number, r: number, stops: Stop[]) {
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setShader(
      Skia.Shader.MakeRadialGradient({ x, y }, r, stops.map((s) => color(s[0], s[1])), stops.map((s) => s[2]), TileMode.Clamp),
    );
    this.c.drawCircle(x, y, r, paint);
  }

  /** An arc stroke from `from` to `to` (radians, increasing angle). */
  arc(x: number, y: number, r: number, from: number, to: number, width: number, hex: number, a = 1) {
    this.line.setColor(color(hex, a));
    this.line.setStrokeWidth(width);
    const path = Skia.PathBuilder.Make();
    path.addArc(Skia.XYWHRect(x - r, y - r, 2 * r, 2 * r), (from * 180) / Math.PI, ((to - from) * 180) / Math.PI);
    this.c.drawPath(path.detach(), this.line);
  }

  ring(x: number, y: number, r: number, width: number, hex: number, a: number) {
    this.line.setColor(color(hex, a));
    this.line.setStrokeWidth(width);
    this.c.drawCircle(x, y, r, this.line);
  }

  push(x: number, y: number, angle: number, s = 1) {
    this.c.save();
    this.c.translate(x, y);
    this.c.rotate((angle * 180) / Math.PI, 0, 0);
    this.c.scale(s, s);
  }

  pop() {
    this.c.restore();
  }
}

function smooth(p: number[], closed: boolean): SkPath {
  const n = p.length / 2;
  const path = Skia.PathBuilder.Make();
  const mx = (i: number, j: number) => (p[2 * i] + p[2 * j]) / 2;
  const my = (i: number, j: number) => (p[2 * i + 1] + p[2 * j + 1]) / 2;
  if (closed) {
    path.moveTo(mx(n - 1, 0), my(n - 1, 0));
    for (let i = 0; i < n; i++) {
      const j = (i + 1) % n;
      path.quadTo(p[2 * i], p[2 * i + 1], mx(i, j), my(i, j));
    }
    path.close();
  } else {
    path.moveTo(p[0], p[1]);
    for (let i = 1; i < n - 1; i++) path.quadTo(p[2 * i], p[2 * i + 1], mx(i, i + 1), my(i, i + 1));
    path.lineTo(p[2 * (n - 1)], p[2 * (n - 1) + 1]);
  }
  return path.detach();
}

// Shared shapes

function strand(r: number, a0: number, sweep: number, wobble: number, freq: number, phase: number, steps = 12) {
  const p: number[] = [];
  for (let s = 0; s <= steps; s++) {
    const a = a0 + (sweep * s) / steps;
    const rr = r + wobble * Math.sin(a * freq + phase);
    p.push(rr * Math.cos(a), rr * Math.sin(a));
  }
  return p;
}

function blobPoints(x: number, y: number, r: number, k1: number, k2: number, phase: number, count = 16) {
  const p: number[] = [];
  for (let i = 0; i < count; i++) {
    const a = (2 * Math.PI * i) / count;
    const rr = r * (1 + k1 * Math.sin(3 * a + phase) + k2 * Math.sin(5 * a + 2 * phase));
    p.push(x + rr * Math.cos(a), y + rr * Math.sin(a));
  }
  return p;
}

const shift = (p: number[], dx: number, dy: number) => p.map((v, i) => v + (i % 2 === 0 ? dx : dy));

function leaf(pen: Pen, x: number, y: number, len: number, w: number, angle: number, hex: number, vein: number) {
  pen.push(x, y, angle);
  pen.blob([-len / 2, 0, -len * 0.18, -w / 2, len * 0.22, -w * 0.42, len / 2, 0, len * 0.22, w * 0.42, -len * 0.18, w / 2], hex);
  pen.ellipse(-len * 0.05, -w * 0.16, len * 0.3, w * 0.16, 0, 0xffffff, 0.12);
  pen.stroke([-len * 0.48, 0, 0, -w * 0.03, len * 0.44, 0], len * 0.035, vein, 0.75);
  pen.pop();
}

function specks(pen: Pen, seed: number, count: number, reach: number, size: number, colors: number[], alpha: number) {
  for (let k = 0; k < count; k++) {
    const rr = reach * Math.sqrt(hash(seed + k * 4.1));
    const a = 2 * Math.PI * hash(seed + k * 4.1 + 1.3);
    const s = size * (0.6 + 0.8 * hash(seed + k * 4.1 + 2.7));
    pen.ellipse(rr * Math.cos(a), rr * Math.sin(a), s, s * 0.62, 6.28 * hash(seed + k * 4.1 + 3.9), colors[k % colors.length], alpha);
  }
}

// Dishes (unit food disc: radius 1)

function spaghetti(pen: Pen, seed: number) {
  pen.radial(0, 0, 1, [[0xe8c374, 1, 0], [0xd9ae5c, 1, 0.78], [0xd9ae5c, 0, 1]]);
  const strands: number[][] = [];
  for (let k = 0; k < 96; k++) {
    const s = seed + k * 3;
    strands.push(strand(0.16 + 0.76 * Math.sqrt(hash(s + 1)), 2 * Math.PI * hash(s + 2), 1.0 + 1.8 * hash(s + 3), 0.035, 2.5, 6 * hash(s + 4)));
  }
  for (const p of strands) pen.stroke(p, 0.046, 0xa2722f, 0.5);
  strands.forEach((p, k) => {
    const h = hash(seed + k * 3 + 5);
    pen.stroke(p, 0.03, h < 0.33 ? 0xf3d791 : h < 0.66 ? 0xebc877 : 0xddb25e);
    if (k % 2 === 0) pen.stroke(shift(p, -0.006, -0.006), 0.01, 0xfff4cf, 0.55);
  });
  pen.blob(blobPoints(0.04, -0.02, 0.42, 0.1, 0.07, 1.2), 0xa92816);
  pen.radial(-0.02, -0.08, 0.4, [[0xe5502f, 1, 0], [0xd23d24, 0.9, 0.6], [0xc7341f, 0, 1]]);
  for (let k = 0; k < 18; k++) {
    const s = seed + 300 + k * 5;
    const rr = 0.33 * Math.sqrt(hash(s));
    const a = 2 * Math.PI * hash(s + 1);
    const big = hash(s + 2);
    pen.circle(0.04 + rr * Math.cos(a), -0.02 + rr * Math.sin(a), 0.02 + 0.035 * big, k % 3 === 0 ? 0x8e1e12 : 0xe95e3d, k % 3 === 0 ? 0.4 : 0.5);
  }
  for (let k = 0; k < 5; k++) {
    const s = seed + 400 + k * 7;
    pen.stroke(strand(0.36 + 0.08 * hash(s), 2 * Math.PI * hash(s + 1), 0.7, 0.03, 3, 2, 6), 0.034, 0xebc877);
  }
  for (let k = 0; k < 7; k++) {
    const s = seed + 500 + k * 3;
    pen.circle(-0.14 + 0.2 * hash(s), -0.2 + 0.2 * hash(s + 1), 0.008 + 0.01 * hash(s + 2), 0xffffff, 0.5);
  }
  leaf(pen, 0.06, -0.14, 0.36, 0.18, -0.6, 0x3d8a36, 0x86c76a);
  leaf(pen, 0.2, 0.08, 0.28, 0.15, 0.9, 0x357d30, 0x79be5e);
  leaf(pen, -0.12, 0.1, 0.26, 0.13, 2.4, 0x418f39, 0x86c76a);
  specks(pen, seed + 600, 18, 0.85, 0.026, [0xfff5d8, 0xfbebc0], 0.95);
}

function penne(pen: Pen, seed: number) {
  pen.radial(0, 0, 1, [[0xb83e24, 1, 0], [0x8e2716, 1, 0.88], [0x8e2716, 0, 1]]);
  const count = 52;
  for (let k = 0; k < count; k++) {
    const s = seed + k * 5;
    const rr = 0.8 * Math.sqrt(hash(s + 1));
    const a = 2 * Math.PI * hash(s + 2);
    const len = 0.3 + 0.06 * hash(s + 4);
    const w = 0.13;
    const cut = 0.055;
    const shade = 0.4 + (0.6 * k) / count;
    pen.push(rr * Math.cos(a), rr * Math.sin(a), 2 * Math.PI * hash(s + 3));
    const body = [-len / 2 - cut, -w / 2, len / 2 - cut, -w / 2, len / 2 + cut, w / 2, -len / 2 + cut, w / 2];
    pen.poly(shift(body, 0.016, 0.024), 0x4a1208, 0.5);
    pen.poly(body, mix(0x9a3a1a, 0xd8743a, shade));
    pen.stroke([-len / 2 + 0.03, 0, len / 2 - 0.03, 0], w * 0.6, mix(0xbe5428, 0xf29a52, shade), 0.9);
    for (const y of [-0.38, -0.18, 0.18, 0.38]) {
      pen.stroke([-len / 2 + 0.012 + cut * y * 2, w * y, len / 2 - 0.012 + cut * y * 2, w * y], 0.006, 0x6e2410, 0.3);
    }
    pen.stroke([-len / 2 + 0.04, -w * 0.13, len / 2 - 0.05, -w * 0.13], w * 0.18, 0xffd7a0, 0.5);
    pen.ellipse(len / 2 - 0.012, 0, 0.022, w * 0.4, -Math.atan((2 * cut) / w), 0x4e1508, 0.85);
    pen.circle(-len * 0.18, w * 0.22, 0.03, 0xc23a20, 0.75);
    pen.circle(len * 0.12, -w * 0.25, 0.022, 0xb0301a, 0.6);
    pen.pop();
  }
  specks(pen, seed + 400, 6, 0.7, 0.04, [0xf3e6c4], 0.95);
  specks(pen, seed + 500, 34, 0.88, 0.018, [0x7e1a0c, 0xe0502e], 0.9);
  specks(pen, seed + 600, 26, 0.85, 0.02, [0x4c8b36, 0x6fae4a], 0.95);
}

const FARFALLE = [
  0, -0.035, 0.19, -0.11, 0.215, -0.075, 0.195, -0.04, 0.215, 0, 0.195, 0.04, 0.215, 0.075, 0.19, 0.11,
  0, 0.035, -0.19, 0.11, -0.215, 0.075, -0.195, 0.04, -0.215, 0, -0.195, -0.04, -0.215, -0.075, -0.19, -0.11,
];

function farfalle(pen: Pen, seed: number) {
  pen.radial(0, 0, 1, [[0x88b04a, 1, 0], [0x6a9236, 1, 0.88], [0x6a9236, 0, 1]]);
  const count = 44;
  for (let k = 0; k < count; k++) {
    const s = seed + k * 5;
    const rr = 0.82 * Math.sqrt(hash(s + 1));
    const a = 2 * Math.PI * hash(s + 2);
    const shade = 0.45 + (0.55 * k) / count;
    pen.push(rr * Math.cos(a), rr * Math.sin(a), 2 * Math.PI * hash(s + 3), 1.0 + 0.2 * hash(s + 4));
    pen.poly(shift(FARFALLE, 0.012, 0.018), 0x2f4a16, 0.45);
    pen.poly(FARFALLE, mix(0xc2c26a, 0xf2e8a8, shade));
    pen.ellipse(0.12, -0.03, 0.05, 0.022, 0.4, 0x5f8f2e, 0.5);
    pen.ellipse(-0.11, 0.04, 0.045, 0.02, -0.3, 0x5f8f2e, 0.45);
    for (const sx of [-1, 1]) {
      pen.stroke([sx * 0.05, -0.03, sx * 0.17, -0.085], 0.007, 0x8e9a46, 0.5);
      pen.stroke([sx * 0.05, 0.03, sx * 0.17, 0.085], 0.007, 0x8e9a46, 0.5);
    }
    pen.ellipse(0, 0, 0.032, 0.046, 0, 0x9da552);
    pen.stroke([-0.012, -0.03, -0.012, 0.03], 0.007, 0x7c8438, 0.7);
    pen.stroke([0.012, -0.03, 0.012, 0.03], 0.007, 0x7c8438, 0.7);
    pen.pop();
  }
  for (let k = 0; k < 14; k++) {
    const s = seed + 400 + k * 4;
    const rr = 0.8 * Math.sqrt(hash(s));
    const a = 2 * Math.PI * hash(s + 1);
    const ang = 6.28 * hash(s + 2);
    pen.ellipse(rr * Math.cos(a) + 0.008, rr * Math.sin(a) + 0.01, 0.04, 0.022, ang, 0x3a4a18, 0.35);
    pen.ellipse(rr * Math.cos(a), rr * Math.sin(a), 0.04, 0.022, ang, 0xf2e3ba);
    pen.ellipse(rr * Math.cos(a) - 0.008, rr * Math.sin(a) - 0.006, 0.022, 0.01, ang, 0xfff9e8, 0.9);
  }
  specks(pen, seed + 500, 9, 0.75, 0.06, [0xfbf4df], 0.95);
  leaf(pen, -0.1, -0.12, 0.34, 0.17, 0.5, 0x3d8a36, 0x86c76a);
  leaf(pen, 0.14, 0.02, 0.28, 0.14, 2.1, 0x357d30, 0x79be5e);
}

function fettuccine(pen: Pen, seed: number) {
  pen.radial(0, 0, 1, [[0xf7eed6, 1, 0], [0xead8ac, 1, 0.88], [0xead8ac, 0, 1]]);
  const ribbons: number[][] = [];
  for (let k = 0; k < 30; k++) {
    const s = seed + k * 3;
    ribbons.push(strand(0.18 + 0.7 * hash(s + 1), 2 * Math.PI * hash(s + 2), 1.4 + 1.6 * hash(s + 3), 0.05, 2, 6 * hash(s + 4)));
  }
  for (const p of ribbons) pen.stroke(p, 0.13, 0x9c7a3a, 0.6);
  ribbons.forEach((p, k) => {
    pen.stroke(p, 0.105, mix(0xe3bc6e, 0xf3d695, hash(seed + k * 3 + 5)));
    pen.stroke(shift(p, -0.016, -0.016), 0.022, 0xfff8e2, 0.65);
  });
  for (let k = 0; k < 5; k++) {
    const s = seed + 300 + k * 4;
    const rr = 0.7 * Math.sqrt(hash(s));
    const a = 2 * Math.PI * hash(s + 1);
    pen.ellipse(rr * Math.cos(a), rr * Math.sin(a), 0.1, 0.06, 6.28 * hash(s + 2), 0xfbf4e2, 0.55);
  }
  specks(pen, seed + 400, 70, 0.9, 0.01, [0x3a332b, 0x5a5046], 0.85);
  specks(pen, seed + 500, 20, 0.8, 0.022, [0x5a9a3e, 0x3f7f2e], 0.95);
}

function macAndCheese(pen: Pen, seed: number) {
  pen.radial(0, 0, 1, [[0xf3b847, 1, 0], [0xdd962d, 1, 0.88], [0xdd962d, 0, 1]]);
  const count = 90;
  for (let k = 0; k < count; k++) {
    const s = seed + k * 5;
    const rr = 0.86 * Math.sqrt(hash(s + 1));
    const a = 2 * Math.PI * hash(s + 2);
    const shade = 0.4 + (0.6 * k) / count;
    pen.push(rr * Math.cos(a), rr * Math.sin(a), 2 * Math.PI * hash(s + 3));
    pen.push(0.01, 0.014, 0);
    pen.arc(0, 0, 0.075, -1.3, 1.3, 0.085, 0xa9661a, 0.5);
    pen.pop();
    pen.arc(0, 0, 0.075, -1.3, 1.3, 0.07, mix(0xefae3e, 0xfbd772, shade));
    pen.arc(0, 0, 0.066, -1.0, 0.9, 0.018, 0xfff0b8, 0.7);
    pen.pop();
  }
  for (let k = 0; k < 7; k++) {
    const s = seed + 300 + k * 4;
    const rr = 0.65 * Math.sqrt(hash(s));
    const a = 2 * Math.PI * hash(s + 1);
    pen.radial(rr * Math.cos(a), rr * Math.sin(a), 0.12 + 0.08 * hash(s + 2), [[0xa0521a, 0.5, 0], [0xa0521a, 0, 1]]);
  }
  specks(pen, seed + 400, 110, 0.9, 0.022, [0xc98633, 0xa9621f, 0xe9b455, 0x8e4e17], 0.9);
  for (let k = 0; k < 14; k++) {
    const s = seed + 600 + k * 4;
    const rr = 0.75 * Math.sqrt(hash(s));
    const a = 2 * Math.PI * hash(s + 1);
    const ang = 6.28 * hash(s + 2);
    const x = rr * Math.cos(a);
    const y = rr * Math.sin(a);
    pen.stroke([x - 0.035 * Math.cos(ang), y - 0.035 * Math.sin(ang), x + 0.035 * Math.cos(ang), y + 0.035 * Math.sin(ang)], 0.015, 0x4f8e33);
  }
}

function ravioli(pen: Pen, seed: number) {
  pen.radial(0, 0, 1, [[0xdda752, 1, 0], [0xb5752c, 0.92, 0.74], [0xb5752c, 0, 1]]);
  const spots: number[][] = [];
  for (let k = 0; k < 5; k++) {
    const a = (k * 2 * Math.PI) / 5 + 0.18;
    spots.push([0.53 * Math.cos(a), 0.53 * Math.sin(a), a + 0.3]);
  }
  spots.push([0, 0, 0.2]);
  const side = 0.46;
  spots.forEach((sp, k) => {
    pen.push(sp[0], sp[1], sp[2]);
    pen.roundRect(-side / 2 + 0.02, -side / 2 + 0.03, side, side, 0.07, 0x4a2a0e, 0.5);
    pen.roundRect(-side / 2, -side / 2, side, side, 0.07, 0xe6cc92);
    pen.radial(-0.02, -0.03, 0.2, [[0xfff4d6, 0.95, 0], [0xf2dead, 0.7, 0.6], [0xf0dcaa, 0, 1]]);
    pen.stroke([-0.13, 0.12, 0, 0.16, 0.13, 0.12], 0.012, 0xd3b87c, 0.5);
    const inset = side / 2 - 0.035;
    for (let j = 0; j < 7; j++) {
      const u = -inset + (2 * inset * j) / 6;
      pen.circle(u, -inset, 0.011, 0xc9aa6a, 0.8);
      pen.circle(u, inset, 0.011, 0xc9aa6a, 0.8);
      pen.circle(-inset, u, 0.011, 0xc9aa6a, 0.8);
      pen.circle(inset, u, 0.011, 0xc9aa6a, 0.8);
    }
    pen.ellipse(-0.06, -0.08, 0.06, 0.025, -0.5, 0xffffff, 0.35 + 0.1 * (k % 2));
    pen.pop();
  });
  leaf(pen, 0.0, -0.3, 0.26, 0.12, 0.3, 0x56703a, 0x8ba45e);
  leaf(pen, 0.3, 0.22, 0.24, 0.11, 2.2, 0x4e6833, 0x8ba45e);
  leaf(pen, -0.34, 0.12, 0.25, 0.12, -1.9, 0x56703a, 0x8ba45e);
  leaf(pen, -0.1, 0.36, 0.22, 0.1, 1.2, 0x4e6833, 0x8ba45e);
  for (let k = 0; k < 16; k++) {
    const s = seed + 500 + k * 4;
    const rr = 0.85 * Math.sqrt(hash(s));
    const a = 2 * Math.PI * hash(s + 1);
    const ang = 6.28 * hash(s + 2);
    const x = rr * Math.cos(a);
    const y = rr * Math.sin(a);
    pen.stroke([x, y, x + 0.03 * Math.cos(ang), y + 0.03 * Math.sin(ang), x + 0.05 * Math.cos(ang + 0.9), y + 0.05 * Math.sin(ang + 0.9)], 0.012, 0xf0cf3a);
  }
  specks(pen, seed + 600, 20, 0.9, 0.01, [0x3a2a1b], 0.8);
}

function lasagna(pen: Pen, seed: number) {
  pen.blob(blobPoints(0, 0, 0.9, 0.06, 0.04, 0.7), 0xa42c19);
  pen.radial(-0.1, -0.12, 0.85, [[0xc8432a, 1, 0], [0xb43722, 0.6, 0.7], [0xa42c19, 0, 1]]);
  specks(pen, seed + 50, 16, 0.85, 0.03, [0x7e1e10, 0xd9573a], 0.7);
  pen.push(0, 0, -0.14);
  const side = 1.1;
  const h = side / 2;
  pen.roundRect(-h + 0.07, -h + 0.09, side, side, 0.1, 0x5e1e0e, 0.5);
  pen.roundRect(-h + 0.06, -h + 0.075, side, side, 0.1, 0xefd7a4);
  pen.roundRect(-h + 0.042, -h + 0.052, side, side, 0.1, 0x8e3218);
  pen.roundRect(-h + 0.022, -h + 0.028, side, side, 0.1, 0xf1ddb0);
  pen.roundRect(-h, -h, side, side, 0.1, 0xe3a443);
  for (let k = 0; k < 34; k++) {
    const s = seed + 100 + k * 4;
    const x = -h + 0.06 + (side - 0.12) * hash(s);
    const y = -h + 0.06 + (side - 0.12) * hash(s + 1);
    pen.radial(x, y, 0.05 + 0.08 * hash(s + 2), [[0xb0612a, 0.6, 0], [0xb0612a, 0, 1]]);
  }
  for (let k = 0; k < 20; k++) {
    const s = seed + 300 + k * 4;
    const x = -h + 0.05 + (side - 0.1) * hash(s);
    const y = -h + 0.05 + (side - 0.1) * hash(s + 1);
    pen.circle(x, y, 0.016 + 0.03 * hash(s + 2), 0x84401a, 0.5);
  }
  for (let k = 0; k < 30; k++) {
    const s = seed + 500 + k * 4;
    const x = -h + 0.05 + (side - 0.1) * hash(s);
    const y = -h + 0.05 + (side - 0.1) * hash(s + 1);
    const r = 0.014 + 0.026 * hash(s + 2);
    pen.circle(x, y, r, 0xf7d27c, 0.85);
    pen.circle(x - r * 0.3, y - r * 0.3, r * 0.35, 0xffffff, 0.6);
  }
  for (let k = 0; k < 5; k++) {
    const s = seed + 700 + k * 4;
    const edge = Math.trunc(hash(s) * 4);
    const u = -h + 0.12 + (side - 0.24) * hash(s + 1);
    const pos = [[u, -h + 0.04], [h - 0.04, u], [u, h - 0.04], [-h + 0.04, u]][edge];
    pen.circle(pos[0], pos[1], 0.05 + 0.03 * hash(s + 2), 0xb83a20, 0.85);
  }
  leaf(pen, 0.16, -0.12, 0.3, 0.15, -0.8, 0x3d8a36, 0x86c76a);
  leaf(pen, 0.28, 0.02, 0.24, 0.12, 0.4, 0x357d30, 0x79be5e);
  pen.pop();
}

function shells(pen: Pen, seed: number) {
  pen.radial(0, 0, 1, [[0xf5e8c8, 1, 0], [0xe6cf9c, 1, 0.88], [0xe6cf9c, 0, 1]]);
  const peas = (from: number, to: number) => {
    for (let k = from; k < to; k++) {
      const s = seed + 400 + k * 4;
      const rr = 0.82 * Math.sqrt(hash(s));
      const a = 2 * Math.PI * hash(s + 1);
      const x = rr * Math.cos(a);
      const y = rr * Math.sin(a);
      pen.circle(x + 0.01, y + 0.014, 0.05, 0x2e4a12, 0.4);
      pen.circle(x, y, 0.05, 0x5e9c2a);
      pen.radial(x - 0.015, y - 0.015, 0.04, [[0xc2e88a, 0.95, 0], [0x8cc44e, 0.5, 0.5], [0x8cc44e, 0, 1]]);
    }
  };
  peas(0, 10);
  const count = 52;
  for (let k = 0; k < count; k++) {
    const s = seed + k * 5;
    const rr = 0.82 * Math.sqrt(hash(s + 1));
    const a = 2 * Math.PI * hash(s + 2);
    const shade = 0.45 + (0.55 * k) / count;
    pen.push(rr * Math.cos(a), rr * Math.sin(a), 2 * Math.PI * hash(s + 3), 1.0 + 0.25 * hash(s + 4));
    pen.ellipse(0.012, 0.018, 0.12, 0.095, 0, 0x8f6e35, 0.45);
    pen.ellipse(0, 0, 0.12, 0.095, 0, mix(0xe2b565, 0xf7da95, shade));
    for (let j = 0; j < 7; j++) {
      const ang = -1.15 + (2.3 * j) / 6;
      pen.stroke([-0.09, 0, -0.09 + 0.2 * Math.cos(ang), 0.088 * Math.sin(ang) * 1.05], 0.007, 0xc99a4c, 0.55);
    }
    pen.ellipse(0.025, 0, 0.055, 0.04, 0, 0xc58f45, 0.7);
    pen.ellipse(0.03, 0, 0.035, 0.022, 0, 0xfbf0d6);
    pen.circle(-0.1, 0, 0.02, 0xd9ab5c);
    pen.pop();
  }
  peas(10, 28);
  for (let k = 0; k < 10; k++) {
    const s = seed + 600 + k * 4;
    const rr = 0.78 * Math.sqrt(hash(s));
    const a = 2 * Math.PI * hash(s + 1);
    pen.push(rr * Math.cos(a), rr * Math.sin(a), 6.28 * hash(s + 2));
    pen.roundRect(-0.04, -0.035, 0.08, 0.07, 0.015, 0xe4978a);
    pen.roundRect(-0.04, -0.012, 0.08, 0.018, 0.008, 0xf6c9be, 0.9);
    pen.pop();
  }
  specks(pen, seed + 700, 30, 0.9, 0.01, [0x3a332b], 0.8);
}

const RECIPES = [spaghetti, penne, farfalle, fettuccine, macAndCheese, ravioli, lasagna, shells];

// Images

function render(side: number, draw: (pen: Pen) => void): SkImage | null {
  const px = Math.round(side * SCALE);
  const surface = Skia.Surface.Make(px, px);
  if (!surface) return null;
  const canvas = surface.getCanvas();
  canvas.clear(Float32Array.of(0, 0, 0, 0));
  canvas.scale(SCALE, SCALE);
  draw(new Pen(canvas));
  surface.flush();
  const snapshot = surface.makeImageSnapshot();
  return snapshot.makeNonTextureImage() ?? snapshot;
}

function plateImage(bowl: boolean) {
  return render(PLATE_SIZE, (pen) => {
    const c = PLATE_SIZE / 2;
    const r = c - 1;
    if (bowl) {
      pen.radial(c, c, r, [[0x1d1b19, 1, 0], [0x1d1b19, 1, 0.77], [0x37332e, 1, 0.85], [0x24211e, 1, 0.94], [0x0e0d0c, 1, 1]]);
      pen.radial(c, c, r * 0.78, [[0x2a2723, 1, 0], [0x1b1917, 1, 0.9], [0x121110, 1, 1]]);
      pen.arc(c, c, r * 0.88, Math.PI * 1.08, Math.PI * 1.55, r * 0.035, 0xffffff, 0.14);
      pen.arc(c, c, r * 0.795, Math.PI * 0.1, Math.PI * 0.6, 1.5, 0xffffff, 0.1);
      pen.ring(c, c, r - 0.5, 1, 0x000000, 0.55);
    } else {
      pen.radial(c, c, r, [[0xf6f4ef, 1, 0], [0xf6f4ef, 1, 0.75], [0xffffff, 1, 0.82], [0xf1eee8, 1, 0.94], [0xd9d5cc, 1, 1]]);
      pen.radial(c, c, r * 0.8, [[0x000000, 0, 0.86], [0x6b5e48, 0.1, 0.95], [0x6b5e48, 0, 1]]);
      pen.arc(c, c, r * 0.885, Math.PI * 1.12, Math.PI * 1.5, r * 0.03, 0xffffff, 0.7);
      pen.arc(c, c, r * 0.89, Math.PI * 0.15, Math.PI * 0.55, r * 0.05, 0xc9c3b8, 0.18);
      pen.ring(c, c, r - 0.5, 1, 0xc7c1b6, 0.8);
    }
  });
}

function foodImage(i: number) {
  const fr = (PLATE_SIZE / 2) * foodFraction(DISHES[i].bowl);
  const side = fr * 2 * 1.08;
  return render(side, (pen) => {
    pen.c.translate(side / 2, side / 2);
    pen.c.scale(fr, fr);
    RECIPES[i](pen, i * 1000 + 17);
  });
}

function lightImage(bowl: boolean) {
  const fr = (PLATE_SIZE / 2) * foodFraction(bowl);
  const side = fr * 2 * 1.08;
  return render(side, (pen) => {
    const c = side / 2;
    pen.radial(c, c, fr * 0.98, [[0x000000, 0, 0], [0x000000, 0, 0.55], [0x2a1608, 0.22, 0.9], [0x2a1608, 0, 1]]);
    pen.radial(c - fr * 0.32, c - fr * 0.38, fr * 0.8, [[0xffffff, 0.2, 0], [0xffffff, 0.06, 0.5], [0xffffff, 0, 1]]);
  });
}

/** 128 × 128 tile of static grain (a hash per pixel), premultiplied RGBA. */
function grainImage() {
  const n = 128;
  const bytes = new Uint8Array(n * n * 4);
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) {
      const v = hash(x * 1.7 + y * 113.3 + 0.5);
      const o = (y * n + x) * 4;
      const a = Math.trunc(Math.abs(v - 0.5) * 2 * 255);
      const p = v > 0.5 ? a : 0;
      bytes[o] = p;
      bytes[o + 1] = p;
      bytes[o + 2] = p;
      bytes[o + 3] = a;
    }
  }
  return Skia.Image.MakeImage({ width: n, height: n, colorType: ColorType.RGBA_8888, alphaType: AlphaType.Premul }, Skia.Data.fromBytes(bytes), n * 4);
}

export type Atlas = { plates: SkImage[]; lights: SkImage[]; foods: SkImage[]; grain: SkImage };

export function buildAtlas(): Atlas | null {
  const plates = [plateImage(false), plateImage(true)];
  const lights = [lightImage(false), lightImage(true)];
  const foods = DISHES.map((_, i) => foodImage(i));
  const grain = grainImage();
  if (plates.some((p) => !p) || lights.some((p) => !p) || foods.some((p) => !p) || !grain) return null;
  return { plates: plates as SkImage[], lights: lights as SkImage[], foods: foods as SkImage[], grain };
}
