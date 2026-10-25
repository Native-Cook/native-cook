import {
  BlendMode,
  BlurStyle,
  ClipOp,
  FilterMode,
  MipmapMode,
  PaintStyle,
  PathOp,
  type SkCanvas,
  type SkFont,
  type SkImage,
  type SkPaint,
  Skia,
  StrokeCap,
  TileMode,
} from '@shopify/react-native-skia';

import { type Pose, clamp01, wrap } from './compassMotion';

/*
 Compass Needle Nav — the drawing. One Skia canvas: a baked background (night-green ground, the
 dock plate), four baked pages (Map, Trips, Gear, Me) that hang on a wheel centred on the compass
 and are drawn rotated each frame, and the compass: a baked base (housing, bezel with degrees,
 the liquid well and its card), the needle drawn each frame with its shadow, an air bubble, and a
 baked glass dome over it.
*/

export const TABS = ['Map', 'Trips', 'Gear', 'Me'];
const ACCENT = '#F07A3A';
const INK = '#EEF1E8';
const MUTE = 'rgba(238,241,232,0.62)';
const FAINT = 'rgba(238,241,232,0.38)';
const CARD = ['#1E2F28', '#1A2923', '#16231E'];

export type Fonts = { title: SkFont; body: SkFont; bodyBold: SkFont; small: SkFont; tiny: SkFont; degree: SkFont; cardinal: SkFont; big: SkFont };

export type Scene = {
  w: number;
  h: number;
  cx: number;
  cy: number;
  r: number;
  pageTop: number;
  pageH: number;
  /** The compass images are drawn in a box of this size around (cx, cy). */
  box: number;
};

export function makeScene(w: number, h: number, top: number, bottom: number): Scene {
  const r = Math.min(104, w * 0.27, (h - top - bottom) * 0.13);
  const cy = h - bottom - 46 - r;
  const pageTop = top + 44;
  const pageBottom = cy - r - 72;
  return { w, h, cx: w / 2, cy, r, pageTop, pageH: pageBottom - pageTop, box: Math.ceil(r * 2 + 48) };
}

/** Draws a baked image with linear filtering (it moves, rotates and lands between pixels). */
function drawImg(c: SkCanvas, img: SkImage, src: ReturnType<typeof Skia.XYWHRect>, dst: ReturnType<typeof Skia.XYWHRect>, p: SkPaint) {
  'worklet';
  c.drawImageRectOptions(img, src, dst, FilterMode.Linear, MipmapMode.None, p);
}

function paint(color: string, alpha = 1) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(Math.max(0, alpha));
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeLinearGradient(Skia.Point(x0, y0), Skia.Point(x1, y1), colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp);
}

function radial(x: number, y: number, r: number, colors: string[], pos: number[] | null = null) {
  'worklet';
  return Skia.Shader.MakeRadialGradient(Skia.Point(x, y), r, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp);
}

function shaded(shader: ReturnType<typeof linear>) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(shader);
  return p;
}

function blurred(color: string, alpha: number, sigma: number) {
  'worklet';
  const p = paint(color, alpha);
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function stroke(color: string, alpha: number, width: number) {
  'worklet';
  const p = paint(color, alpha);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  p.setStrokeCap(StrokeCap.Round);
  return p;
}

function grain(alpha: number, fx: number, fy: number, seed: number) {
  const p = Skia.Paint();
  p.setShader(Skia.Shader.MakeFractalNoise(fx, fy, 2, seed, 0, 0));
  p.setColorFilter(Skia.ColorFilter.MakeMatrix([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, alpha]));
  p.setBlendMode(BlendMode.Overlay);
  return p;
}

function textWidth(font: SkFont, text: string) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let sum = 0;
  for (let i = 0; i < widths.length; i += 1) sum += widths[i];
  return sum;
}

function bake(w: number, h: number, scale: number, draw: (c: SkCanvas) => void): SkImage | null {
  const surface = Skia.Surface.Make(Math.ceil(w * scale), Math.ceil(h * scale));
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(scale, scale);
  draw(c);
  surface.flush();
  return surface.makeImageSnapshot();
}

/** Ground and the dock plate the compass sits on. */
export function bakeGround(s: Scene, scale: number) {
  return bake(s.w, s.h, scale, (c) => {
    const { w, h } = s;
    c.drawRect(Skia.XYWHRect(0, 0, w, h), shaded(linear(0, 0, 0, h, ['#13221D', '#0F1C18', '#0C1714', '#0A1310'], [0, 0.4, 0.75, 1])));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), shaded(radial(w * 0.5, s.pageTop + s.pageH * 0.4, h * 0.6, ['rgba(120,160,130,0.10)', 'rgba(0,0,0,0)'])));
    c.drawRect(Skia.XYWHRect(0, 0, w, h), grain(0.1, 0.9, 0.9, 5));
    const dockY = s.cy - s.r - 62;
    const dock = Skia.RRectXY(Skia.XYWHRect(0, dockY, w, h - dockY + 40), 34, 34);
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0, dockY - 6, w, h - dockY + 40), 34, 34), blurred('#000000', 0.45, 18));
    c.drawRRect(dock, shaded(linear(0, dockY, 0, h, ['#1B2B25', '#15231E', '#101B17'])));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(0.5, dockY + 0.5, w - 1, h - dockY + 40), 34, 34), stroke('#D8F0E0', 0.1, 1));
  });
}

function cardRect(c: SkCanvas, x: number, y: number, w: number, h: number, r = 20) {
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y + 8, w, h), r, r), blurred('#000000', 0.35, 12));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r), shaded(linear(0, y, 0, y + h, CARD)));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 0.5, y + 0.5, w - 1, h - 1), r - 0.5, r - 0.5), stroke('#D8F0E0', 0.08, 1));
}

function header(c: SkCanvas, fonts: Fonts, title: string, sub: string) {
  c.drawText(title, 20, 34, paint(INK), fonts.title);
  c.drawText(sub, 20, 58, paint(MUTE), fonts.body);
}

function hash(n: number) {
  const v = Math.sin(n * 12.9898 + 4.1414) * 43758.5453;
  return v - Math.floor(v);
}

function drawMapPage(c: SkCanvas, W: number, H: number, fonts: Fonts) {
  header(c, fonts, 'Map', 'Larch Ridge · day 2 of 3');
  const x = 16;
  const y = 74;
  const w = W - 32;
  const h = H - y - 8;
  cardRect(c, x, y, w, h, 24);
  const card = Skia.RRectXY(Skia.XYWHRect(x, y, w, h), 24, 24);
  c.save();
  c.clipRRect(card, ClipOp.Intersect, true);
  c.drawRect(Skia.XYWHRect(x, y, w, h), shaded(linear(x, y, x + w, y + h, ['#22352C', '#1D2E26', '#192820'])));
  // A tarn in the lower left.
  const lake = Skia.PathBuilder.Make();
  lake.moveTo(x - 10, y + h * 0.72);
  lake.cubicTo(x + w * 0.2, y + h * 0.62, x + w * 0.34, y + h * 0.8, x + w * 0.24, y + h + 10);
  lake.lineTo(x - 10, y + h + 10);
  lake.close();
  c.drawPath(lake.build(), shaded(linear(x, y + h * 0.65, x + w * 0.3, y + h, ['#2E5966', '#244955', '#1C3A44'])));
  // Contours round two peaks; every fourth line is an index line.
  const peaks = [
    [x + w * 0.66, y + h * 0.36, 1],
    [x + w * 0.28, y + h * 0.3, 0.7],
  ];
  for (let p = 0; p < peaks.length; p += 1) {
    const [px, py, sz] = peaks[p];
    for (let k = 0; k < 11; k += 1) {
      const b = Skia.PathBuilder.Make();
      for (let i = 0; i <= 72; i += 1) {
        const a = (i / 72) * Math.PI * 2;
        const r = (10 + k * 13) * sz + 6 * Math.sin(3 * a + k * 0.5 + p) + 4 * Math.sin(5 * a + k * 0.9) + 3 * Math.sin(2 * a - k * 0.3);
        const px2 = px + Math.cos(a) * r * 1.15;
        const py2 = py + Math.sin(a) * r * 0.85;
        if (i === 0) b.moveTo(px2, py2);
        else b.lineTo(px2, py2);
      }
      b.close();
      c.drawPath(b.build(), stroke('#9CC4A6', k % 4 === 0 ? 0.32 : 0.16, k % 4 === 0 ? 1.2 : 0.8));
    }
  }
  // The trail: dashed, from the trailhead to the ridge, with a glow.
  const trail = Skia.PathBuilder.Make();
  trail.moveTo(x + w * 0.12, y + h * 0.92);
  trail.cubicTo(x + w * 0.38, y + h * 0.82, x + w * 0.3, y + h * 0.56, x + w * 0.48, y + h * 0.5);
  trail.cubicTo(x + w * 0.62, y + h * 0.45, x + w * 0.58, y + h * 0.3, x + w * 0.66, y + h * 0.36);
  const tp = trail.build();
  const glow = stroke(ACCENT, 0.35, 7);
  glow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 4, true));
  c.drawPath(tp, glow);
  const dash = stroke(ACCENT, 1, 2.6);
  dash.setPathEffect(Skia.PathEffect.MakeDash([7, 6], 0));
  c.drawPath(tp, dash);
  c.drawCircle(x + w * 0.12, y + h * 0.92, 6, paint('#EEF1E8'));
  c.drawCircle(x + w * 0.12, y + h * 0.92, 3, paint(ACCENT));
  // You are here: a halo and a dot on the trail.
  c.drawCircle(x + w * 0.48, y + h * 0.5, 16, paint('#7FB8FF', 0.18));
  c.drawCircle(x + w * 0.48, y + h * 0.5, 7, paint('#EEF1E8'));
  c.drawCircle(x + w * 0.48, y + h * 0.5, 5, paint('#3D8BFF'));
  // Summit flag.
  const fx = x + w * 0.66;
  const fy = y + h * 0.36;
  c.drawLine(fx, fy, fx, fy - 18, stroke('#EEF1E8', 0.9, 1.4));
  const flag = Skia.PathBuilder.Make();
  flag.moveTo(fx, fy - 18);
  flag.lineTo(fx + 12, fy - 14);
  flag.lineTo(fx, fy - 10);
  flag.close();
  c.drawPath(flag.build(), paint(ACCENT));
  c.drawText('Larch Peak 2,184 m', fx + 6, fy + 16, paint(INK, 0.8), fonts.tiny);
  c.restore();
  // Route chip over the map.
  const chipW = Math.min(w - 24, 236);
  const cy = y + h - 64;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 12, cy + 6, chipW, 52), 16, 16), blurred('#000000', 0.4, 8));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 12, cy, chipW, 52), 16, 16), paint('#0F1A16', 0.86));
  c.drawText('Larch Ridge Loop', x + 26, cy + 22, paint(INK), fonts.bodyBold);
  c.drawText('11.4 km  ·  +640 m  ·  4 h 20', x + 26, cy + 40, paint(MUTE), fonts.small);
}

function drawTripsPage(c: SkCanvas, W: number, H: number, fonts: Fonts) {
  header(c, fonts, 'Trips', '3 planned this month');
  const trips = [
    { name: 'Larch Ridge Loop', meta: 'Sat 14 Jun · 11.4 km · +640 m', seed: 1 },
    { name: 'Cinder Falls', meta: 'Sun 22 Jun · 6.2 km · +310 m', seed: 2 },
    { name: 'Mirror Tarns overnight', meta: 'Fri 27 Jun · 18.9 km · +1,020 m', seed: 3 },
  ];
  const top = 76;
  const gap = 12;
  const ch = Math.min(104, (H - top - 8 - gap * 2) / 3);
  for (let i = 0; i < trips.length; i += 1) {
    const y = top + i * (ch + gap);
    cardRect(c, 16, y, W - 32, ch);
    const t = trips[i];
    c.drawText(t.name, 32, y + ch / 2 - 4, paint(INK), fonts.bodyBold);
    c.drawText(t.meta, 32, y + ch / 2 + 16, paint(MUTE), fonts.small);
    // Elevation profile on the right.
    const px = W - 32 - 84;
    const py = y + ch / 2 + 18;
    const b = Skia.PathBuilder.Make();
    b.moveTo(px, py);
    for (let k = 0; k <= 14; k += 1) {
      const v = 0.3 + 0.7 * Math.abs(Math.sin(k * 0.45 + t.seed) * 0.6 + Math.sin(k * 0.21 + t.seed * 2) * 0.4);
      b.lineTo(px + (84 * k) / 14, py - v * 34);
    }
    b.lineTo(px + 84, py);
    b.close();
    c.drawPath(b.build(), shaded(linear(0, py - 34, 0, py, ['rgba(240,122,58,0.55)', 'rgba(240,122,58,0.05)'])));
    if (i === 0) {
      // A "next" pill after the name, on its line.
      const px0 = 32 + textWidth(fonts.bodyBold, t.name) + 8;
      const py0 = y + ch / 2 - 18;
      c.drawRRect(Skia.RRectXY(Skia.XYWHRect(px0, py0, 42, 18), 9, 9), paint(ACCENT, 0.18));
      c.drawText('NEXT', px0 + 21 - textWidth(fonts.tiny, 'NEXT') / 2, py0 + 12.5, paint(ACCENT), fonts.tiny);
    }
  }
}

function drawGearPage(c: SkCanvas, W: number, H: number, fonts: Fonts) {
  header(c, fonts, 'Gear', 'Pack weight 7.4 of 9.0 kg');
  const y0 = 78;
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(20, y0, W - 40, 8), 4, 4), paint('#EEF1E8', 0.1));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(20, y0, (W - 40) * 0.82, 8), 4, 4), shaded(linear(20, 0, W - 20, 0, ['#F5A15E', ACCENT, '#D85A24'])));
  const items = [
    ['Rain shell', '320 g', 1],
    ['Headlamp', '86 g', 1],
    ['Water filter', '140 g', 0],
    ['First-aid kit', '210 g', 1],
    ['Trekking poles', '480 g', 0],
    ['Down jacket', '390 g', 1],
  ] as const;
  const top = y0 + 24;
  cardRect(c, 16, top, W - 32, H - top - 8);
  const rowH = Math.min(52, (H - top - 16) / items.length);
  for (let i = 0; i < items.length; i += 1) {
    const [name, weight, done] = items[i];
    const y = top + 8 + i * rowH;
    const cyy = y + rowH / 2;
    if (done) {
      c.drawCircle(36, cyy, 10, paint(ACCENT));
      const tick = Skia.PathBuilder.Make();
      tick.moveTo(31.5, cyy);
      tick.lineTo(35, cyy + 3.5);
      tick.lineTo(41, cyy - 3.5);
      c.drawPath(tick.build(), stroke('#14211C', 1, 2));
    } else c.drawCircle(36, cyy, 9.5, stroke('#EEF1E8', 0.35, 1.5));
    c.drawText(name, 58, cyy + 5, paint(done ? INK : MUTE), fonts.body);
    const ww = textWidth(fonts.small, weight);
    c.drawText(weight, W - 32 - ww, cyy + 5, paint(FAINT), fonts.small);
    if (i < items.length - 1) c.drawLine(58, y + rowH, W - 32, y + rowH, stroke('#EEF1E8', 0.07, 1));
  }
}

function drawMePage(c: SkCanvas, W: number, H: number, fonts: Fonts) {
  header(c, fonts, 'Me', 'Joined 2023 · Cascade Club');
  const top = 76;
  cardRect(c, 16, top, W - 32, 92);
  c.drawCircle(56, top + 46, 26, shaded(linear(30, top + 20, 82, top + 72, ['#F5A15E', ACCENT, '#9C3A16'])));
  c.drawText('AK', 56 - textWidth(fonts.bodyBold, 'AK') / 2, top + 52, paint('#FFF4EA'), fonts.bodyBold);
  c.drawText('Ari Kestrel', 96, top + 42, paint(INK), fonts.bodyBold);
  c.drawText('Next summit: Larch Peak', 96, top + 62, paint(MUTE), fonts.small);
  const stats = [
    ['312', 'km hiked'],
    ['41', 'summits'],
    ['18,920', 'm climbed'],
  ];
  const sy = top + 108;
  const sw = (W - 32 - 16) / 3;
  const sh = Math.min(96, H - sy - 8);
  for (let i = 0; i < 3; i += 1) {
    const x = 16 + i * (sw + 8);
    cardRect(c, x, sy, sw, sh, 18);
    const [v, l] = stats[i];
    c.drawText(v, x + 14, sy + sh / 2 + 2, paint(INK), fonts.big);
    c.drawText(l, x + 14, sy + sh / 2 + 22, paint(MUTE), fonts.small);
  }
  const by = sy + sh + 16;
  if (by + 60 < H) {
    c.drawText('BADGES', 20, by + 12, paint(FAINT), fonts.tiny);
    for (let i = 0; i < 5; i += 1) {
      const x = 40 + i * 58;
      const y = by + 44;
      if (y + 24 > H) break;
      c.drawCircle(x, y, 22, shaded(radial(x - 6, y - 8, 30, ['#2E4A3E', '#1C2E26'])));
      c.drawCircle(x, y, 22, stroke(i < 3 ? ACCENT : '#EEF1E8', i < 3 ? 0.7 : 0.2, 1.5));
      const m = Skia.PathBuilder.Make();
      m.moveTo(x - 11, y + 7);
      m.lineTo(x - 3, y - 6 - hash(i) * 3);
      m.lineTo(x + 2, y + 1);
      m.lineTo(x + 6, y - 4);
      m.lineTo(x + 12, y + 7);
      m.close();
      c.drawPath(m.build(), paint(i < 3 ? '#EEF1E8' : '#EEF1E8', i < 3 ? 0.85 : 0.3));
    }
  }
}

/** The four pages, each a full-width image. */
export function bakePages(s: Scene, fonts: Fonts, scale: number) {
  const draws = [drawMapPage, drawTripsPage, drawGearPage, drawMePage];
  return draws.map((d) => bake(s.w, s.pageH, scale, (c) => d(c, s.w, s.pageH, fonts)));
}

/** Housing, bezel with degrees and letters, and the liquid well with its card (no needle). */
export function bakeCompass(s: Scene, fonts: Fonts, scale: number) {
  const B = s.box;
  return bake(B, B, scale, (c) => {
    const cx = B / 2;
    const cy = B / 2;
    const R = s.r;
    c.drawCircle(cx, cy + 10, R + 12, blurred('#000000', 0.5, 14));
    c.drawCircle(cx, cy + 2, R + 12, blurred('#000000', 0.6, 2));
    // Housing: dark anodised metal with a knurled rim and a bright chamfer.
    c.drawCircle(cx, cy, R + 12, shaded(Skia.Shader.MakeSweepGradient(cx, cy, ['#3A4440', '#8E9893', '#2F3835', '#6E7873', '#2C3431', '#7E8883', '#3A4440'].map((x) => Skia.Color(x)), null, TileMode.Clamp)));
    const knurl = stroke('#0E1412', 0.5, 1);
    for (let i = 0; i < 120; i += 1) {
      const a = (i / 120) * Math.PI * 2;
      c.drawLine(cx + Math.cos(a) * (R + 8), cy + Math.sin(a) * (R + 8), cx + Math.cos(a) * (R + 12), cy + Math.sin(a) * (R + 12), knurl);
    }
    c.drawCircle(cx, cy, R + 1, shaded(linear(cx, cy - R, cx, cy + R, ['#C9D2CD', '#5E6864', '#2A322F'])));
    // Bezel: matte black ring with ticks every 5°, numbers every 30°, the cardinals.
    c.drawCircle(cx, cy, R, shaded(radial(cx, cy - R * 0.3, R * 1.2, ['#26302C', '#1A2220', '#121816'])));
    for (let d = 0; d < 360; d += 5) {
      const a = ((d - 90) * Math.PI) / 180;
      const major = d % 30 === 0;
      const r0 = R - 3;
      const r1 = R - (major ? 12 : 7);
      c.drawLine(cx + Math.cos(a) * r0, cy + Math.sin(a) * r0, cx + Math.cos(a) * r1, cy + Math.sin(a) * r1, stroke('#E9EEE6', major ? 0.85 : 0.4, major ? 1.4 : 0.8));
    }
    for (let d = 30; d < 360; d += 30) {
      if (d % 90 === 0) continue;
      const a = ((d - 90) * Math.PI) / 180;
      const label = String(d);
      const r = R - 21;
      c.save();
      c.translate(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
      c.rotate(d, 0, 0);
      c.drawText(label, -textWidth(fonts.degree, label) / 2, 3.5, paint('#E9EEE6', 0.7), fonts.degree);
      c.restore();
    }
    const cards = ['N', 'E', 'S', 'W'];
    for (let i = 0; i < 4; i += 1) {
      const a = ((i * 90 - 90) * Math.PI) / 180;
      const r = R - 22;
      c.save();
      c.translate(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
      c.rotate(i * 90, 0, 0);
      c.drawText(cards[i], -textWidth(fonts.cardinal, cards[i]) / 2, 6, paint(i === 0 ? '#F0563F' : '#F2F4EE'), fonts.cardinal);
      c.restore();
    }
    // The liquid well: deep tinted glass, an inner shadow at its rim, a faint rose.
    const wr = R * 0.66;
    c.drawCircle(cx, cy, wr + 2, paint('#0A100E'));
    c.drawCircle(cx, cy, wr, shaded(radial(cx - wr * 0.25, cy - wr * 0.3, wr * 1.4, ['#24453F', '#16302C', '#0D1E1C'])));
    const rose = stroke('#BFE3D6', 0.16, 0.8);
    for (let d = 0; d < 360; d += 10) {
      const a = (d * Math.PI) / 180;
      const r1 = d % 90 === 0 ? wr * 0.7 : wr * 0.86;
      c.drawLine(cx + Math.cos(a) * wr * 0.92, cy + Math.sin(a) * wr * 0.92, cx + Math.cos(a) * r1, cy + Math.sin(a) * r1, rose);
    }
    c.drawCircle(cx, cy, wr * 0.5, stroke('#BFE3D6', 0.1, 0.8));
    const inner = blurred('#000000', 0.7, 6);
    inner.setStyle(PaintStyle.Stroke);
    inner.setStrokeWidth(8);
    c.save();
    c.clipRRect(Skia.RRectXY(Skia.XYWHRect(cx - wr, cy - wr, wr * 2, wr * 2), wr, wr), ClipOp.Intersect, true);
    c.drawCircle(cx, cy + 2, wr + 3, inner);
    c.restore();
  });
}

/** The glass dome over the well: a crescent highlight and a sharp glint. */
export function bakeGlass(s: Scene, scale: number) {
  const B = s.box;
  return bake(B, B, scale, (c) => {
    const cx = B / 2;
    const cy = B / 2;
    const wr = s.r * 0.66;
    c.save();
    c.clipRRect(Skia.RRectXY(Skia.XYWHRect(cx - wr, cy - wr, wr * 2, wr * 2), wr, wr), ClipOp.Intersect, true);
    const cres = Skia.PathBuilder.Make();
    cres.addCircle(cx, cy, wr);
    const cut = Skia.PathBuilder.Make();
    cut.addCircle(cx + wr * 0.22, cy + wr * 0.26, wr * 1.02);
    const crescent = Skia.Path.MakeFromOp(cres.build(), cut.build(), PathOp.Difference);
    if (crescent) c.drawPath(crescent, shaded(linear(cx - wr, cy - wr, cx, cy, ['rgba(255,255,255,0.32)', 'rgba(255,255,255,0.06)'])));
    c.drawOval(Skia.XYWHRect(cx - wr * 0.55, cy - wr * 0.72, wr * 0.32, wr * 0.14), blurred('#FFFFFF', 0.55, 2));
    c.restore();
    c.drawCircle(cx, cy, wr, stroke('#DDEDE6', 0.22, 1));
  });
}

export type Layers = { ground: SkImage; pages: SkImage[]; compass: SkImage; glass: SkImage };

function drawNeedle(c: SkCanvas, cx: number, cy: number, len: number, angle: number) {
  'worklet';
  c.save();
  c.rotate(angle, cx, cy);
  const wdt = len * 0.13;
  const tail = len * 0.86;
  // Four facets: north red (lit side and shade side), south cream.
  const facet = (x0: number, y0: number, x1: number, y1: number, color: string) => {
    const b = Skia.PathBuilder.Make();
    b.moveTo(cx, cy);
    b.lineTo(x0, y0);
    b.lineTo(x1, y1);
    b.close();
    c.drawPath(b.build(), paint(color));
  };
  facet(cx - wdt, cy, cx, cy - len, '#E5533F');
  facet(cx + wdt, cy, cx, cy - len, '#A82F22');
  facet(cx - wdt, cy, cx, cy + tail, '#F1ECE0');
  facet(cx + wdt, cy, cx, cy + tail, '#B8B1A2');
  c.drawLine(cx, cy - len + 2, cx, cy - 4, stroke('#FFD2C8', 0.35, 0.6));
  c.restore();
}

/** One frame; `bob` (radians) drives the needle's slight bob in the liquid. */
export function drawFrame(c: SkCanvas, s: Scene, layers: Layers, pose: Pose, bob: number) {
  'worklet';
  const full = Skia.XYWHRect(0, 0, s.w, s.h);
  const ip = Skia.Paint();
  drawImg(c, layers.ground, Skia.XYWHRect(0, 0, layers.ground.width(), layers.ground.height()), full, ip);

  // Pages on the wheel: each hangs at k × 90° and swings round the compass's centre.
  c.save();
  c.clipRect(Skia.XYWHRect(0, s.pageTop - 6, s.w, s.pageH + 12), ClipOp.Intersect, true);
  for (let k = 0; k < layers.pages.length; k += 1) {
    const d = wrap(k * 90 - pose.wheel);
    if (Math.abs(d) >= 89) continue;
    const img = layers.pages[k];
    const fade = Skia.Paint();
    fade.setAlphaf(clamp01(1 - Math.pow(Math.abs(d) / 80, 1.5)));
    c.save();
    c.rotate(d, s.cx, s.cy);
    drawImg(c, img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(0, s.pageTop, s.w, s.pageH), fade);
    c.restore();
  }
  c.restore();

  const B = s.box;
  const boxRect = Skia.XYWHRect(s.cx - B / 2, s.cy - B / 2, B, B);
  drawImg(c, layers.compass, Skia.XYWHRect(0, 0, layers.compass.width(), layers.compass.height()), boxRect, ip);
  // The heading being steered to glows on the bezel.
  const ta = ((pose.target - 90) * Math.PI) / 180;
  const glow = blurred('#F07A3A', 0.65, 6);
  glow.setBlendMode(BlendMode.Plus);
  c.drawCircle(s.cx + Math.cos(ta) * (s.r - 8), s.cy + Math.sin(ta) * (s.r - 8), 9, glow);

  // The needle floats: a shadow offset on the card below it, a slight bob.
  const len = s.r * 0.6;
  const lift = 1 + 0.012 * Math.sin(bob);
  c.save();
  c.translate(3, 5);
  c.rotate(pose.needle, s.cx, s.cy);
  const sh = Skia.PathBuilder.Make();
  sh.moveTo(s.cx - len * 0.13, s.cy);
  sh.lineTo(s.cx, s.cy - len);
  sh.lineTo(s.cx + len * 0.13, s.cy);
  sh.lineTo(s.cx, s.cy + len * 0.86);
  sh.close();
  c.drawPath(sh.build(), blurred('#000000', 0.45, 3));
  c.restore();
  drawNeedle(c, s.cx, s.cy, len * lift, pose.needle);
  c.drawCircle(s.cx, s.cy, 7, shaded(radial(s.cx - 2, s.cy - 2, 8, ['#F7E4B0', '#B68C42', '#5E4219'])));
  // An air bubble under the glass drifts against the spin.
  const lag = Math.max(-50, Math.min(50, -pose.spin * 0.12));
  const ba = ((225 + lag) * Math.PI) / 180;
  const br = s.r * 0.48;
  const bx = s.cx + Math.cos(ba) * br;
  const by = s.cy + Math.sin(ba) * br;
  c.drawCircle(bx, by, 4.2, paint('#CFEDE4', 0.18));
  c.drawCircle(bx, by, 4.2, stroke('#E8FFF6', 0.5, 0.8));
  c.drawCircle(bx - 1.3, by - 1.3, 1.1, paint('#FFFFFF', 0.8));
  drawImg(c, layers.glass, Skia.XYWHRect(0, 0, layers.glass.width(), layers.glass.height()), boxRect, ip);
  // The finger's bead on the bezel while dragging.
  if (!Number.isNaN(pose.finger)) {
    const fa = ((pose.finger - 90) * Math.PI) / 180;
    const fx = s.cx + Math.cos(fa) * (s.r + 4);
    const fy = s.cy + Math.sin(fa) * (s.r + 4);
    c.drawCircle(fx, fy, 12, blurred('#F07A3A', 0.5, 6));
    c.drawCircle(fx, fy, 6, paint('#F07A3A'));
    c.drawCircle(fx, fy, 6, stroke('#FFE3D0', 0.8, 1.2));
  }
}
