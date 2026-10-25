import {
  BlurStyle,
  ClipOp,
  PaintStyle,
  Skia,
  StrokeCap,
  TileMode,
  vec,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPath,
  type SkRuntimeEffect,
} from '@shopify/react-native-skia';

import { hash } from './chalkboardSearchMotion';

/*
 Chalkboard Search — the board's artwork and the chalk.

 The slate (dark green with old eraser swirls, grain and a vignette), the oak frame and ledge,
 the café's name and "What's cooking?" in chalk, the magnifier, the search line's underline and
 a small doodle are drawn once into an image. Live chalk (the query and the suggestions) is a
 glyph outline filled with CHALK_SKSL: an off-white whose coverage breaks up with two noises
 (one stretched along the stroke direction, like chalk dragged over slate) and a fine grain.
*/

export const CHALK_SKSL = `
uniform float p3;
uniform float seed;

float hash21(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }
float vnoise(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), f.x), mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), f.x), f.y);
}
float3 toP3(float3 s) {
  float3 l = pow(max(s, float3(0.0)), float3(2.2));
  float3 q = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return pow(max(q, float3(0.0)), float3(1.0 / 2.2));
}
half4 main(float2 p) {
  float n1 = vnoise(p * float2(0.5, 0.85) + seed);
  float n2 = vnoise(p * float2(2.2, 0.6) + seed * 1.7);
  float g = hash21(floor(p * 2.0) + seed);
  float a = smoothstep(0.2, 0.66, n1 * 0.5 + n2 * 0.32 + g * 0.34);
  a = 0.3 + 0.7 * a;
  float3 c = float3(0.945, 0.94, 0.905);
  if (p3 > 0.5) c = toP3(c);
  return half4(half3(c * a), a);
}
`;

const SLATE_SKSL = `
uniform float2 res;

float hash21(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }
float vnoise(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), f.x), mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), f.x), f.y);
}
float fbm(float2 q) {
  float s = 0.0;
  float a = 0.5;
  for (int i = 0; i < 3; i++) { s += a * vnoise(q); q = q * 2.03 + 7.1; a *= 0.5; }
  return s;
}
half4 main(float2 p) {
  float2 uv = p / res;
  float w = fbm(p * 0.008);
  // Old eraser swirls: arcs bent by the noise, faint and patchy.
  float arc = sin(length(p - res * float2(0.3, 0.7)) * 0.045 + w * 5.0);
  float swirl = smoothstep(0.55, 1.0, arc) * smoothstep(0.35, 0.75, fbm(p * 0.004 + 3.0));
  float smudge = smoothstep(0.42, 0.85, w);
  float3 base = float3(0.118, 0.169, 0.145);
  float3 lift = float3(0.22, 0.27, 0.245);
  float3 c = mix(base, lift, smudge * 0.32 + swirl * 0.22);
  c *= 1.0 + (hash21(floor(p * 1.5)) - 0.5) * 0.07;
  float v = smoothstep(1.0, 0.25, length((uv - 0.5) * float2(1.1, 1.0)));
  c *= mix(0.62, 1.0, v);
  return half4(half3(c), 1.0);
}
`;

export type Geo = {
  W: number;
  H: number;
  /** Outer board and the slate inside its frame. */
  boardX: number;
  boardY: number;
  boardW: number;
  boardH: number;
  slateX: number;
  slateY: number;
  slateW: number;
  slateH: number;
  /** Query: left edge and baseline; suggestion lines: left edge and baselines. */
  qx: number;
  qy: number;
  sx: number;
  sy: number[];
  doodle: boolean;
};

export const FRAME = 14;

export function makeGeo(W: number, H: number, insetTop: number): Geo {
  const boardX = 12;
  const boardY = insetTop + 4;
  const boardW = W - 24;
  const boardH = Math.max(400, Math.min(520, Math.round(H * 0.58)));
  const slateX = boardX + FRAME;
  const slateY = boardY + FRAME;
  const slateW = boardW - FRAME * 2;
  const slateH = boardH - FRAME * 2 - 10;
  return {
    W, H, boardX, boardY, boardW, boardH, slateX, slateY, slateW, slateH,
    qx: slateX + 52,
    qy: slateY + 146,
    sx: slateX + 36,
    sy: [slateY + 212, slateY + 256, slateY + 300],
    doodle: boardH >= 470,
  };
}

export type ChalkLine = {
  path: SkPath;
  /** Each glyph's left and right edge (pt), for the pen's reveal and the dust. */
  lefts: number[];
  rights: number[];
  /** Right edge of the whole line. */
  end: number;
};

/** A line of text as one chalk outline, each glyph nudged off the baseline and tilted a little. */
export function chalkLine(text: string, font: SkFont, x: number, baseline: number, seed: number): ChalkLine {
  const path = Skia.PathBuilder.Make();
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  const lefts: number[] = [];
  const rights: number[] = [];
  let pen = x;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    const w = widths[i] ?? font.getSize() * 0.5;
    lefts.push(pen);
    rights.push(pen + w);
    if (ch !== ' ') {
      const g = Skia.Path.MakeFromText(ch, 0, 0, font);
      if (g) {
        const m = Skia.Matrix();
        const dy = (hash(seed * 13 + i * 7.1) - 0.5) * 2.4;
        const rot = (hash(seed * 7 + i * 3.3) - 0.5) * 6;
        m.translate(pen + w / 2, baseline + dy);
        m.rotate((rot * Math.PI) / 180);
        m.translate(-w / 2, 0);
        path.addPath(g, m);
      }
    }
    pen += w * 1.02;
  }
  return { path: path.build(), lefts, rights, end: pen };
}

function paint(color: string, alpha = 1) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  p.setAlphaf(alpha);
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos?: number[]) {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient(vec(x0, y0), vec(x1, y1), colors.map((c) => Skia.Color(c)), pos ?? null, TileMode.Clamp));
  return p;
}

/** Fills a path with chalk: a soft haze, the chalk itself, and a rough second pass along the edge. */
export function drawChalk(c: SkCanvas, path: SkPath, chalk: SkRuntimeEffect, seed: number, alpha = 1) {
  const haze = paint('#eef0e8', 0.07 * alpha);
  haze.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
  c.drawPath(path, haze);
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(chalk.makeShader([0, seed]));
  p.setAlphaf(alpha);
  c.drawPath(path, p);
  const edge = Skia.Paint();
  edge.setAntiAlias(true);
  edge.setStyle(PaintStyle.Stroke);
  edge.setStrokeWidth(0.9);
  edge.setShader(chalk.makeShader([0, seed + 5]));
  edge.setAlphaf(0.45 * alpha);
  c.save();
  c.translate(0.6, 0.5);
  c.drawPath(path, edge);
  c.restore();
}

/** A hand-drawn chalk stroke (for the underline, icons and the doodle). */
function chalkStroke(c: SkCanvas, path: SkPath, chalk: SkRuntimeEffect, width: number, seed: number) {
  for (let k = 0; k < 2; k += 1) {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStyle(PaintStyle.Stroke);
    p.setStrokeWidth(width * (k === 0 ? 1 : 0.6));
    p.setStrokeCap(StrokeCap.Round);
    p.setShader(chalk.makeShader([0, seed + k * 9]));
    p.setAlphaf(k === 0 ? 0.85 : 0.5);
    c.save();
    c.translate(k * 0.7, k * -0.5);
    c.drawPath(path, p);
    c.restore();
  }
}

export type BoardFonts = { cafe: SkFont; title: SkFont; small: SkFont };

/** Renders the board once (GPU offscreen at the screen's density) into an image. */
export function bakeBoard(g: Geo, fonts: BoardFonts, chalk: SkRuntimeEffect, density: number): SkImage | null {
  const slate = Skia.RuntimeEffect.Make(SLATE_SKSL);
  const w = Math.ceil(g.W * density);
  const h = Math.ceil((g.boardY + g.boardH + 24) * density);
  const surface = Skia.Surface.MakeOffscreen(w, h) ?? Skia.Surface.Make(w, h);
  if (!surface || !slate) return null;
  const c = surface.getCanvas();
  c.scale(density, density);

  // Frame: two shadows on the wall, then oak with grain.
  const outer = Skia.RRectXY(Skia.XYWHRect(g.boardX, g.boardY, g.boardW, g.boardH), 10, 10);
  const amb = paint('#000000', 0.45);
  amb.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 18, true));
  c.save();
  c.translate(0, 14);
  c.drawRRect(outer, amb);
  c.restore();
  c.drawRRect(outer, linear(g.boardX, 0, g.boardX + g.boardW, g.boardH, ['#7a4c2a', '#a8723f', '#8a5a33', '#5e3a1f'], [0, 0.3, 0.7, 1]));
  const grain = paint('#2f1b0c', 0.18);
  grain.setStyle(PaintStyle.Stroke);
  grain.setStrokeWidth(0.8);
  for (let i = 0; i < 40; i += 1) {
    const y = g.boardY + hash(i * 2.7) * g.boardH;
    const path = Skia.PathBuilder.Make();
    path.moveTo(g.boardX, y);
    path.cubicTo(g.boardX + g.boardW * 0.3, y + (hash(i + 3) - 0.5) * 8, g.boardX + g.boardW * 0.7, y + (hash(i + 5) - 0.5) * 8, g.boardX + g.boardW, y);
    c.drawPath(path.build(), grain);
  }
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.boardX + 1, g.boardY + 1, g.boardW - 2, 1.2), 1, 1), paint('#ffe2b8', 0.35));

  // Slate, inset into the frame.
  const slateRect = Skia.XYWHRect(g.slateX, g.slateY, g.slateW, g.slateH);
  c.save();
  c.clipRRect(Skia.RRectXY(slateRect, 4, 4), ClipOp.Intersect, true);
  c.translate(g.slateX, g.slateY);
  const sp = Skia.Paint();
  sp.setShader(slate.makeShader([g.slateW, g.slateH]));
  c.drawRect(Skia.XYWHRect(0, 0, g.slateW, g.slateH), sp);
  c.restore();
  const inner = paint('#000000', 0.55);
  inner.setStyle(PaintStyle.Stroke);
  inner.setStrokeWidth(3);
  inner.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, false));
  c.save();
  c.clipRect(slateRect, ClipOp.Intersect, true);
  c.drawRect(slateRect, inner);
  c.restore();

  // Ledge with a stick of chalk.
  const ly = g.slateY + g.slateH;
  c.drawRect(Skia.XYWHRect(g.slateX - 4, ly, g.slateW + 8, 12), linear(0, ly, 0, ly + 12, ['#5a3519', '#b37b45', '#6b4325']));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(g.slateX + g.slateW * 0.66, ly - 3, 34, 6), 3, 3), linear(0, ly - 3, 0, ly + 3, ['#fbfaf4', '#e2e0d6', '#bdbab0']));

  // Chalk copy.
  const cafe = chalkLine('Corner Café', fonts.cafe, g.slateX + 24, g.slateY + 38, 2);
  drawChalk(c, cafe.path, chalk, 3, 0.85);
  const title = chalkLine("What's cooking?", fonts.title, g.slateX + 24, g.slateY + 84, 4);
  drawChalk(c, title.path, chalk, 5);

  // Magnifier and the search line.
  const mx = g.slateX + 34;
  const my = g.qy - 10;
  const glass = Skia.PathBuilder.Make();
  glass.addCircle(mx, my, 8.5);
  glass.moveTo(mx + 6.2, my + 6.2);
  glass.lineTo(mx + 13, my + 13);
  chalkStroke(c, glass.build(), chalk, 2.4, 11);
  const under = Skia.PathBuilder.Make();
  under.moveTo(g.slateX + 22, g.qy + 14);
  under.cubicTo(g.slateX + g.slateW * 0.35, g.qy + 12, g.slateX + g.slateW * 0.65, g.qy + 17, g.slateX + g.slateW - 22, g.qy + 13);
  chalkStroke(c, under.build(), chalk, 1.6, 13);

  const today = chalkLine('Today: rhubarb crumble · $6', fonts.small, g.slateX + 24, g.slateY + g.slateH - 22, 6);
  drawChalk(c, today.path, chalk, 7, 0.7);

  if (g.doodle) {
    // A cup of coffee with steam, bottom right.
    const dx = g.slateX + g.slateW - 76;
    const dy = g.slateY + g.slateH - 82;
    const cup = Skia.PathBuilder.Make();
    cup.moveTo(dx, dy);
    cup.lineTo(dx + 4, dy + 30);
    cup.cubicTo(dx + 6, dy + 36, dx + 30, dy + 36, dx + 32, dy + 30);
    cup.lineTo(dx + 36, dy);
    cup.close();
    cup.moveTo(dx + 35, dy + 7);
    cup.cubicTo(dx + 48, dy + 6, dx + 48, dy + 22, dx + 33, dy + 22);
    cup.moveTo(dx - 8, dy + 40);
    cup.cubicTo(dx + 8, dy + 44, dx + 30, dy + 44, dx + 46, dy + 40);
    for (let k = 0; k < 3; k += 1) {
      const sxk = dx + 9 + k * 9;
      cup.moveTo(sxk, dy - 6);
      cup.cubicTo(sxk - 5, dy - 12, sxk + 5, dy - 18, sxk, dy - 26);
    }
    chalkStroke(c, cup.build(), chalk, 2, 17);
  }

  surface.flush();
  const image = surface.makeImageSnapshot();
  return image.makeNonTextureImage() ?? image;
}
