/*
 Drawing for Cheese Drip Burger, ported from CheeseDripBurgerArt.swift. The cheese is analytic: one
 path per slice (band, drips, drops, puddle; every subpath clockwise on screen, so the nonzero fill
 unites them), filled in a layer: the rim colour first, then the gradient body nudged up-left with
 SrcATop, the right-side shade and a light crescent on the upper-left edges. Helpers are declared
 above their callers (worklets).
 */
import {
  AlphaType,
  BlendMode,
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
  type SkFont,
  type SkImage,
  type SkPathBuilder,
} from '@shopify/react-native-skia';

import {
  IMAGE_SIZE,
  LAYERS,
  LOOP,
  PUDDLE_CX,
  PUDDLE_Y,
  ROWS,
  clamp01,
  edge,
  hash,
  lerp,
  type Bump,
  type CheeseFrame,
  type DripShape,
  type DropShape,
} from './cheeseMotion';

export const BURGER = require('./assets/burger.webp');
export const ANTON = require('./assets/Anton-Regular.ttf');

export const RED = 0xaf0002;
const RED_GLOW = 0xc70d08;
const RED_DEEP = 0x6e0002;
const ROW_FAINT = 0xbc0805;
const YELLOW = 0xf9c93c;
export const CREAM = 0xfff2d6;
export const SAND = 0xffe9c2;

const CHEESE_POS = [0, 0.22, 0.6, 1];
const CHEESE_COLORS = [0xf0a010, 0xffb71a, 0xffcb30, 0xffda5c];
const RIM = 0xc86a04;
const SHADE = 0xd9730a;
const SHADOW = 0x3b1200;
const GLOSS = 0xfffbea;

export const WORD_SIZE = 128;
export const ROW_SIZE = 150;
const ROW_PITCH = 133;
const IMAGE_DRAW = 350;
export const BURGER_SCALE = IMAGE_DRAW / IMAGE_SIZE;
/** Baseline below a line's centre, in em: Anton's (ascent − descent) / 2, like a SwiftUI Text frame. */
const ANTON_MID = 0.42359;

export function hexColor(hex: number, alpha?: number) {
  'worklet';
  const a = alpha === undefined ? 1 : Math.min(Math.max(alpha, 0), 1);
  const r = (hex >> 16) & 0xff;
  const g = (hex >> 8) & 0xff;
  const b = hex & 0xff;
  return `rgba(${r}, ${g}, ${b}, ${a})`;
}

function skColor(hex: number, alpha?: number) {
  'worklet';
  return Skia.Color(hexColor(hex, alpha));
}

export type Layout = {
  width: number;
  height: number;
  top: number;
  brandY: number;
  topWordY: number;
  bottomWordY: number;
  labelY: number;
  productY: number;
  burgerCY: number;
  imageX: number;
  imageY: number;
  baseY: number;
};

export function makeLayout(height: number, top: number, bottom: number): Layout {
  const productY = height - Math.max(bottom, 16) - 20;
  const labelY = productY - 40;
  const bottomWordY = labelY - 76;
  const topWordY = top + 101;
  const burgerCY = (topWordY + bottomWordY) / 2 + 6;
  const imageY = burgerCY - IMAGE_DRAW / 2;
  return {
    width: 393,
    height,
    top,
    brandY: top + 22,
    topWordY,
    bottomWordY,
    labelY,
    productY,
    burgerCY,
    imageX: 196.5 - IMAGE_DRAW / 2,
    imageY,
    baseY: imageY + PUDDLE_Y * BURGER_SCALE,
  };
}

/** A static 128 px grain tile (seeded), drawn tiled at 5 %. */
export function makeGrain(): SkImage | null {
  const n = 128;
  const bytes = new Uint8Array(n * n * 4);
  for (let i = 0; i < n * n; i++) {
    const v = Math.floor(hash(i * 0.731 + 0.5) * 255);
    bytes[i * 4] = v;
    bytes[i * 4 + 1] = v;
    bytes[i * 4 + 2] = v;
    bytes[i * 4 + 3] = 255;
  }
  return Skia.Image.MakeImage(
    { width: n, height: n, alphaType: AlphaType.Opaque, colorType: ColorType.RGBA_8888 },
    Skia.Data.fromBytes(bytes),
    n * 4,
  );
}

// Text

function glyphWidth(font: SkFont, s: string) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(s));
  let w = 0;
  for (let i = 0; i < widths.length; i++) w += widths[i];
  return w;
}

/** Width of `s` with `tracking` pt after each letter (SwiftUI's .tracking). */
export function wordWidth(font: SkFont, s: string, tracking: number) {
  'worklet';
  return glyphWidth(font, s) + tracking * s.length;
}

/** Draws `s` in Anton centred on (cx, cy) like a SwiftUI Text, with `tracking` after each letter. */
export function drawWord(c: SkCanvas, font: SkFont, s: string, cx: number, cy: number, color: number, alpha: number, tracking: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(skColor(color, alpha));
  const total = wordWidth(font, s, tracking);
  const baseline = cy + ANTON_MID * font.getSize();
  let x = cx - total / 2;
  if (tracking === 0) {
    c.drawText(s, x, baseline, paint, font);
    return;
  }
  const widths = font.getGlyphWidths(font.getGlyphIDs(s));
  for (let i = 0; i < s.length; i++) {
    c.drawText(s.charAt(i), x, baseline, paint, font);
    x += widths[i] + tracking;
  }
}

/** A line of system text centred on (cx, cy), its line box centred like a SwiftUI Text. */
export function drawLine(c: SkCanvas, font: SkFont, s: string, cx: number, cy: number, color: number, alpha: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(skColor(color, alpha));
  const metrics = font.getMetrics();
  const w = glyphWidth(font, s);
  c.drawText(s, cx - w / 2, cy - (metrics.ascent + metrics.descent) / 2, paint, font);
}

// Background

export function drawBackground(c: SkCanvas, l: Layout, grain: SkImage | null) {
  'worklet';
  const rect = Skia.XYWHRect(0, 0, l.width, l.height);
  const p = Skia.Paint();
  p.setColor(skColor(RED));
  c.drawRect(rect, p);
  const glow = Skia.Paint();
  glow.setShader(Skia.Shader.MakeRadialGradient({ x: 196.5, y: l.burgerCY }, 330, [skColor(RED_GLOW), skColor(RED_GLOW, 0)], null, TileMode.Clamp));
  c.drawRect(rect, glow);
  const vignette = Skia.Paint();
  vignette.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: 196.5, y: l.height * 0.48 },
      Math.max(l.height * 0.62, 420),
      [skColor(RED_DEEP, 0), skColor(RED_DEEP, 0), skColor(RED_DEEP, 0.6)],
      [0, 0.55, 1],
      TileMode.Clamp,
    ),
  );
  c.drawRect(rect, vignette);
  if (grain) {
    const g = Skia.Paint();
    g.setShader(grain.makeShaderOptions(TileMode.Repeat, TileMode.Repeat, FilterMode.Nearest, MipmapMode.None));
    g.setAlphaf(0.05);
    g.setBlendMode(BlendMode.Overlay);
    c.drawRect(rect, g);
  }
}

export function drawRows(c: SkCanvas, l: Layout, font: SkFont, lit: number[], slide: number[], word: string) {
  'worklet';
  for (let i = 0; i < ROWS; i++) {
    const cy = l.burgerCY + (i - 1) * ROW_PITCH;
    drawWord(c, font, word, 196.5, cy, ROW_FAINT, 1, 0);
    if (lit[i] > 0.002) drawWord(c, font, word, 196.5 + slide[i], cy, YELLOW, lit[i], 0);
  }
}

export function drawFloorShadow(c: SkCanvas, l: Layout, lift: number) {
  'worklet';
  const f = clamp01(1 - lift / 420);
  const w = 300 * (0.55 + 0.45 * f);
  c.save();
  c.translate(196.5, l.baseY + 8);
  c.scale(1, 0.16);
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeRadialGradient({ x: 0, y: 0 }, w / 2, [skColor(0x3a0000, 0.9 * 0.55 * f), skColor(0x3a0000, 0)], null, TileMode.Clamp));
  c.drawOval(Skia.XYWHRect(-w / 2, -w / 2, w, w), p);
  c.restore();
}

/** The brand mark: bun dome, patty, bun base (22 × 15), at (x, y) top-left. */
export function drawMark(c: SkCanvas, x: number, y: number, tint: number) {
  'worklet';
  c.save();
  c.translate(x, y);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(skColor(tint));
  const dome = Skia.PathBuilder.Make();
  dome.moveTo(1, 7);
  dome.cubicTo(1, -1.5, 21, -1.5, 21, 7);
  dome.close();
  c.drawPath(dome.detach(), p);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, 8.4, 22, 2.6), 1.3, 1.3), p);
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(1.5, 12, 19, 3), 1.5, 1.5), p);
  const seed = Skia.Paint();
  seed.setAntiAlias(true);
  seed.setColor(skColor(RED));
  c.drawOval(Skia.XYWHRect(6.1, 3, 1.8, 1.2), seed);
  c.drawOval(Skia.XYWHRect(10.1, 1.8, 1.8, 1.2), seed);
  c.drawOval(Skia.XYWHRect(14.1, 3, 1.8, 1.2), seed);
  c.restore();
}

// Steam (burger space)

export function drawSteam(c: SkCanvas, ambient: number, strength: number) {
  'worklet';
  for (let i = 0; i < 3; i++) {
    const x0 = 140 + i * 46;
    const k = i + 1;
    const phase = (2 * Math.PI * k * ambient) / LOOP;
    const p = Skia.PathBuilder.Make();
    for (let j = 0; j <= 22; j++) {
      const s = j / 22;
      const y = 34 - s * 120;
      const x = x0 + (4 + 10 * s) * Math.sin(s * 5.2 - phase + i * 1.9);
      if (j === 0) p.moveTo(x, y);
      else p.lineTo(x, y);
    }
    const breathe = 0.6 + 0.4 * Math.sin(((2 * Math.PI * ambient) / LOOP) * 2 + i * 2.1);
    const paint = Skia.Paint();
    paint.setAntiAlias(true);
    paint.setStyle(PaintStyle.Stroke);
    paint.setStrokeCap(StrokeCap.Round);
    paint.setStrokeJoin(StrokeJoin.Round);
    paint.setStrokeWidth(11 - i * 1.5);
    paint.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: 0, y: 34 },
        { x: 0, y: -86 },
        [skColor(0xffffff, 0), skColor(0xffffff, 0.22 * breathe * strength), skColor(0xffffff, 0)],
        [0, 0.35, 1],
        TileMode.Clamp,
      ),
    );
    paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));
    c.drawPath(p.detach(), paint);
  }
}

// Cheese (burger space)

function bumpAt(bumps: Bump[], layer: number, x: number) {
  'worklet';
  let s = 0;
  for (let i = 0; i < bumps.length; i++) {
    const b = bumps[i];
    if (b.layer !== layer) continue;
    const d = (x - b.x) / 14;
    s += b.amp * Math.exp(-d * d);
  }
  return s;
}

function bandThick(li: number, x: number, melt: number, bumps: Bump[]) {
  'worklet';
  const layer = LAYERS[li];
  const u = Math.abs((x - layer.cx) / layer.half);
  const taper = 0.3 + 0.7 * Math.sqrt(clamp01((1 - u) * 10));
  const wave = 1.6 * Math.sin(x * 0.11 + li * 1.7) + 1.0 * Math.sin(x * 0.27 + li);
  return (layer.band * (1 + 0.45 * melt) + wave) * taper + bumpAt(bumps, li, x);
}

function addBand(p: SkPathBuilder, li: number, melt: number, bumps: Bump[]) {
  'worklet';
  const layer = LAYERS[li];
  const x0 = layer.cx - layer.half;
  const x1 = layer.cx + layer.half;
  const steps = 48;
  p.moveTo(x0, edge(layer, x0) - 6);
  for (let s = 1; s <= steps; s++) {
    const x = x0 + ((x1 - x0) * s) / steps;
    p.lineTo(x, edge(layer, x) - 6);
  }
  for (let s = steps; s >= 0; s--) {
    const x = x0 + ((x1 - x0) * s) / steps;
    p.lineTo(x, edge(layer, x) + bandThick(li, x, melt, bumps));
  }
  p.close();
}

type Geo = { x: number; w: number; wn: number; r: number; yb: number; yn: number; y1: number; top: number; fillet: number };

function geometry(d: DripShape, yj: number): Geo {
  'worklet';
  const wn = lerp(d.w * 0.8, d.w * 0.18, d.neck);
  const r = Math.max(d.r, wn * 0.5 + 0.5);
  const yb = d.y0 + d.length;
  const top = yj - 5;
  const fillet = 0.35 * d.w + 3;
  const yn = Math.max(yb - r * 0.72, top + 1);
  const y1 = Math.min(top + fillet * 0.9, yn);
  return { x: d.x, w: d.w, wn, r, yb, yn, y1, top, fillet };
}

function addDrip(p: SkPathBuilder, g: Geo) {
  'worklet';
  const x = g.x;
  const hw = g.w / 2;
  const hn = g.wn / 2;
  const r = g.r;
  const yb = g.yb;
  const yn = g.yn;
  const y1 = g.y1;
  const top = g.top;
  const fr = g.fillet;
  p.moveTo(x + hw + fr, top);
  p.cubicTo(x + hw + fr * 0.35, top, x + hw, top + fr * 0.4, x + hw, y1);
  p.cubicTo(x + hw, lerp(y1, yn, 0.5), x + hn, lerp(y1, yn, 0.55), x + hn, yn);
  p.cubicTo(x + hn, yn + r * 0.22, x + r, yb - r * 0.5, x + r, yb);
  p.cubicTo(x + r, yb + r * 0.552, x + r * 0.552, yb + r, x, yb + r);
  p.cubicTo(x - r * 0.552, yb + r, x - r, yb + r * 0.552, x - r, yb);
  p.cubicTo(x - r, yb - r * 0.5, x - hn, yn + r * 0.22, x - hn, yn);
  p.cubicTo(x - hn, lerp(y1, yn, 0.55), x - hw, lerp(y1, yn, 0.5), x - hw, y1);
  p.cubicTo(x - hw, top + fr * 0.4, x - hw - fr * 0.35, top, x - hw - fr, top);
  p.close();
}

function addBlob(p: SkPathBuilder, x: number, y: number, rx: number, ry: number) {
  'worklet';
  const k = 0.552;
  p.moveTo(x, y - ry);
  p.cubicTo(x + rx * k, y - ry, x + rx, y - ry * k, x + rx, y);
  p.cubicTo(x + rx, y + ry * k, x + rx * k, y + ry, x, y + ry);
  p.cubicTo(x - rx * k, y + ry, x - rx, y + ry * k, x - rx, y);
  p.cubicTo(x - rx, y - ry * k, x - rx * k, y - ry, x, y - ry);
  p.close();
}

function puddleHalfWidth(melt: number, bumps: Bump[]) {
  'worklet';
  return 4 + 94 * melt + bumpAt(bumps, 2, PUDDLE_CX) * 2;
}

function addPuddle(p: SkPathBuilder, melt: number, bumps: Bump[]) {
  'worklet';
  const hw = puddleHalfWidth(melt, bumps);
  const h = 1.5 + 8.5 * melt;
  const steps = 40;
  const la = PUDDLE_CX - hw * 0.45;
  const lb = PUDDLE_CX + hw * 0.4;
  for (let s = 0; s <= steps; s++) {
    const f = s / steps;
    const x = PUDDLE_CX - hw + 2 * hw * f;
    const e = Math.sqrt(Math.max(0, 1 - Math.pow(2 * f - 1, 2)));
    const a = (x - la) / 16;
    const b = (x - lb) / 20;
    const lump = 2.4 * Math.exp(-a * a) + 1.8 * Math.exp(-b * b);
    const y = PUDDLE_Y - (h * Math.pow(e, 0.7) + lump * e + bumpAt(bumps, 2, x) * e);
    if (s === 0) p.moveTo(x, y);
    else p.lineTo(x, y);
  }
  for (let s = steps; s >= 0; s--) {
    const f = s / steps;
    const x = PUDDLE_CX - hw + 2 * hw * f;
    const e = Math.sqrt(Math.max(0, 1 - Math.pow(2 * f - 1, 2)));
    p.lineTo(x, PUDDLE_Y + h * 0.75 * Math.pow(e, 0.8));
  }
  p.close();
}

function drawGloss(c: SkCanvas, li: number, geos: Geo[], drops: DropShape[], melt: number, bumps: Bump[]) {
  'worklet';
  const blur = Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 0.6, true);
  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeCap(StrokeCap.Round);
  line.setColor(skColor(GLOSS, 0.62));
  line.setMaskFilter(blur);
  const dot = Skia.Paint();
  dot.setAntiAlias(true);
  dot.setColor(skColor(GLOSS, 0.9));
  dot.setMaskFilter(blur);
  for (let i = 0; i < geos.length; i++) {
    const g = geos[i];
    if (g.yb - g.y1 > 8) {
      const streak = Skia.PathBuilder.Make();
      streak.moveTo(g.x - g.w * 0.22, g.y1 + 1);
      streak.quadTo(g.x - g.wn * 0.28, g.yn, g.x - g.r * 0.5, g.yb - g.r * 0.45);
      line.setStrokeWidth(Math.max(1.2, g.w * 0.1));
      c.drawPath(streak.detach(), line);
    }
    const gw = g.r * 0.4;
    const gh = g.r * 0.55;
    c.drawOval(Skia.XYWHRect(g.x - g.r * 0.48 - gw / 2, g.yb - g.r * 0.34 - gh / 2, gw, gh), dot);
  }
  for (let i = 0; i < drops.length; i++) {
    const d = drops[i];
    if (d.rx <= 1) continue;
    const gw = d.rx * 0.42;
    const gh = d.ry * 0.5;
    c.drawOval(Skia.XYWHRect(d.x - d.rx * 0.46 - gw / 2, d.y - d.ry * 0.38 - gh / 2, gw, gh), dot);
  }
  if (li === 0 && melt > 0.15) {
    const hw = puddleHalfWidth(melt, bumps);
    const arc = Skia.PathBuilder.Make();
    arc.moveTo(PUDDLE_CX - hw * 0.7, PUDDLE_Y - 2 - 2 * melt);
    arc.quadTo(PUDDLE_CX - hw * 0.4, PUDDLE_Y - 6 - 4 * melt, PUDDLE_CX - hw * 0.05, PUDDLE_Y - 3.5 - 3 * melt);
    line.setColor(skColor(GLOSS, 0.6));
    line.setStrokeWidth(1.8);
    c.drawPath(arc.detach(), line);
  }
}

/** Both slices, lower first so the upper one runs over it. The canvas is in burger space. */
export function drawCheese(c: SkCanvas, f: CheeseFrame) {
  'worklet';
  const bounds = Skia.XYWHRect(-40, -40, 460, 480);
  for (let li = 0; li < LAYERS.length; li++) {
    const layer = LAYERS[li];
    const builder = Skia.PathBuilder.Make();
    addBand(builder, li, f.melt, f.bumps);
    const geos: Geo[] = [];
    const shapes = f.drips[li];
    for (let i = 0; i < shapes.length; i++) {
      const d = shapes[i];
      const g = geometry(d, edge(layer, d.x) + bandThick(li, d.x, f.melt, f.bumps));
      geos.push(g);
      addDrip(builder, g);
    }
    if (li === 0) {
      if (f.melt > 0.02) addPuddle(builder, f.melt, f.bumps);
      for (let i = 0; i < f.drops.length; i++) {
        const d = f.drops[i];
        addBlob(builder, d.x, d.y, d.rx, d.ry);
      }
    }
    const goo = builder.detach();

    // Soft shadow on the patty below.
    c.save();
    c.translate(2, 4);
    const shadow = Skia.Paint();
    shadow.setAntiAlias(true);
    shadow.setColor(skColor(SHADOW, 0.45));
    shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3.5, true));
    c.drawPath(goo, shadow);
    c.restore();

    c.saveLayer(undefined, bounds);
    const rim = Skia.Paint();
    rim.setAntiAlias(true);
    rim.setColor(skColor(RIM));
    c.drawPath(goo, rim);
    // Body nudged up-left over the rim colour.
    c.save();
    c.translate(-1.1, -1.7);
    const body = Skia.Paint();
    body.setAntiAlias(true);
    body.setBlendMode(BlendMode.SrcATop);
    const colors = [];
    for (let i = 0; i < CHEESE_COLORS.length; i++) colors.push(skColor(CHEESE_COLORS[i]));
    body.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: layer.y - 6 }, { x: 0, y: layer.y + 110 }, colors, CHEESE_POS, TileMode.Clamp));
    c.drawPath(goo, body);
    c.restore();
    // Right-side shade on each drip.
    const shade = Skia.Paint();
    shade.setAntiAlias(true);
    shade.setStyle(PaintStyle.Stroke);
    shade.setStrokeCap(StrokeCap.Round);
    shade.setStrokeJoin(StrokeJoin.Round);
    shade.setColor(skColor(SHADE, 0.38));
    shade.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 2.4, true));
    shade.setBlendMode(BlendMode.SrcATop);
    for (let i = 0; i < geos.length; i++) {
      const g = geos[i];
      if (g.yb + g.r <= g.top + 10) continue;
      const sp = Skia.PathBuilder.Make();
      sp.moveTo(g.x + g.w * 0.36, g.y1);
      sp.lineTo(g.x + g.wn * 0.32, g.yn);
      sp.lineTo(g.x + g.r * 0.6, g.yb + g.r * 0.25);
      shade.setStrokeWidth(Math.max(2, g.w * 0.26));
      c.drawPath(sp.detach(), shade);
    }
    // A thin light crescent on every upper-left edge.
    const atop = Skia.Paint();
    atop.setBlendMode(BlendMode.SrcATop);
    c.saveLayer(atop, bounds);
    const light = Skia.Paint();
    light.setAntiAlias(true);
    light.setColor(skColor(GLOSS, 0.7));
    c.drawPath(goo, light);
    c.save();
    c.translate(1.2, 1.8);
    const cut = Skia.Paint();
    cut.setAntiAlias(true);
    cut.setColor(skColor(0x000000));
    cut.setBlendMode(BlendMode.DstOut);
    c.drawPath(goo, cut);
    c.restore();
    c.restore();
    c.restore();

    drawGloss(c, li, geos, li === 0 ? f.drops : [], f.melt, f.bumps);
  }
}

export function drawBurgerImage(c: SkCanvas, img: SkImage | null) {
  'worklet';
  if (!img) return;
  const p = Skia.Paint();
  p.setAntiAlias(true);
  c.drawImageRectOptions(
    img,
    Skia.XYWHRect(0, 0, img.width(), img.height()),
    Skia.XYWHRect(0, 0, IMAGE_SIZE, IMAGE_SIZE),
    FilterMode.Linear,
    MipmapMode.Linear,
    p,
  );
}
