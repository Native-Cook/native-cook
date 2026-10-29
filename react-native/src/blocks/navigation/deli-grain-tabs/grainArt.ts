/*
 Look of Deli Grain Tabs, ported from DeliGrainTabsArt.swift: the palette, the twelve silhouettes
 (paths in a 100-unit box, bottom on y = 96, non-zero fill with reversed holes), the layout in points
 of a 393-wide screen, the grains each silhouette is sampled into, and the drawing.
*/

import { ClipOp, Skia, type SkCanvas, type SkFont, type SkPaint, type SkPath, type SkPicture } from '@shopify/react-native-skia';

import {
  GRAIN_COUNT,
  GRID,
  HERO_SCALE,
  ICON_SCALE,
  SIFT,
  easeInCubic,
  easeInOutCubic,
  easeOutCubic,
  positions,
  seg,
  hash,
  heroX,
  heroY,
  stirEnergy,
  tabX,
  tabY,
  type Frame,
  type Grains,
  type Layout,
} from './grainMotion';

export const CREAM = '#F5F1E1';
const INK = 0x16130f;
const IDLE = 0xdcd5c3;
const MUTED = 0x8c8473;

export function mixColor(a: number, b: number, t: number, alpha: number) {
  'worklet';
  const r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
  const g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
  const bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
  return Skia.Color(`rgba(${r}, ${g}, ${bl}, ${Math.max(0, Math.min(1, alpha))})`);
}

type Category = { name: string; count: number; path: string };

/** Top row, then bottom row. */
export const CATEGORIES: Category[] = [
  { name: 'Balsamico', count: 24, path: 'M43 4 L57 4 L57 9 L59 9 Q60 9 60 10 L60 12 Q60 13 59 13 L56 13 L56 27 L71 38 Q73 39.5 73 42 L73 94 Q73 96 71 96 L29 96 Q27 96 27 94 L27 42 Q27 39.5 29 38 L44 27 L44 13 L41 13 Q40 13 40 12 L40 10 Q40 9 41 9 L43 9 L43 4 Z' },
  { name: 'Wine & Spirits', count: 112, path: 'M29.5 6 L70.5 6 C77.6 18.3 80.7 34.6 74.5 49 C69.4 60.2 58.2 63.3 54.1 67.4 L54.1 83.7 C54.1 86.8 62.3 87.8 68.4 89.9 Q72.5 91.9 70.5 95 Q69.4 96 66.4 96 L33.6 96 Q30.6 96 29.5 95 Q27.5 91.9 31.6 89.9 C37.7 87.8 45.9 86.8 45.9 83.7 L45.9 67.4 C41.8 63.3 30.6 60.2 25.5 49 C19.3 34.6 22.4 18.3 29.5 6 Z' },
  { name: 'Flour & Pizza', count: 36, path: 'M12.4 30.7 C32.1 16.4 67.9 16.4 87.6 30.7 Q90.2 33.4 88.4 36.1 L53.6 93.3 Q50 98.7 46.4 93.3 L36.6 77.2 C41.1 73.6 39.3 67.4 33.9 67.4 L28.5 58.4 C33 55.8 32.1 50.4 26.8 51.3 L11.6 36.1 Q9.8 33.4 12.4 30.7 Z M48.4 46.8 C48.4 43.8 45.9 41.3 42.8 41.3 C39.8 41.3 37.3 43.8 37.3 46.8 C37.3 49.9 39.8 52.4 42.8 52.4 C45.9 52.4 48.4 49.9 48.4 46.8 Z M67.3 51.3 C67.3 48.6 65.2 46.5 62.5 46.5 C59.9 46.5 57.7 48.6 57.7 51.3 C57.7 54 59.9 56.1 62.5 56.1 C65.2 56.1 67.3 54 67.3 51.3 Z M55.2 68.3 C55.2 65.4 52.9 63.1 50 63.1 C47.1 63.1 44.8 65.4 44.8 68.3 C44.8 71.1 47.1 73.5 50 73.5 C52.9 73.5 55.2 71.1 55.2 68.3 Z M56.3 26.3 C54.5 26.3 54.5 29.8 56.3 30.3 C65.2 31.2 73.2 33.4 80 36.1 C81.3 36.5 82.2 35.2 81.3 34.3 C75 29.8 66.1 27.2 56.3 26.3 Z' },
  { name: 'Sweets & Coffee', count: 58, path: 'M8 50 L76 50 Q78 50 78 52 C78 68 72 80 60 88 L58 90 L62 90 Q64 90 64 92 L64 94 Q64 96 62 96 L24 96 Q22 96 22 94 L22 92 Q22 90 24 90 L28 90 L26 88 C14 80 8 68 8 52 Q8 50 10 50 Z M92 62 C92 68.6 86.6 74 80 74 C73.4 74 68 68.6 68 62 C68 55.4 73.4 50 80 50 C86.6 50 92 55.4 92 62 Z M85.6 62 C85.6 58.9 83.1 56.4 80 56.4 C76.9 56.4 74.4 58.9 74.4 62 C74.4 65.1 76.9 67.6 80 67.6 C83.1 67.6 85.6 65.1 85.6 62 Z' },
  { name: 'Olive Oil', count: 41, path: 'M35 6 L59 6 Q61 6 61 8 L61 30 C71 34 77 46 77 58 L77 93 Q77 96 74 96 L24 96 Q21 96 21 93 L21 58 C21 46 27 34 37 30 L37 18 L35 6 Z M59 16 L65 16 C75 16 79 22 79 32 C79 42 75 48 67 52 L63 46 C69 42 71 38 71 32 C71 26 69 23 65 23 L59 23 L59 16 Z' },
  { name: 'Sauces & Pesto', count: 67, path: 'M35.7 14 L64.3 14 Q66 14 66 15.8 L66 18.5 Q66 20.2 64.3 20.2 L35.7 20.2 Q34 20.2 34 18.5 L34 15.8 Q34 14 35.7 14 Z M30.4 31.8 Q30.4 22.9 39.3 22.9 L60.7 22.9 Q69.6 22.9 69.6 31.8 L69.6 49.7 L30.4 49.7 L30.4 31.8 Z M30.4 53.2 L69.6 53.2 L69.6 69.3 L30.4 69.3 L30.4 53.2 Z M30.4 72.8 L69.6 72.8 L69.6 87.1 Q69.6 96 60.7 96 L39.3 96 Q30.4 96 30.4 87.1 L30.4 72.8 Z' },
  { name: 'Pasta & Risotto', count: 83, path: 'M50 58.3 C58 48.3 72.1 38.2 88.2 40.3 C83.2 46.3 92.2 50.3 88.2 57.3 C84.2 63.4 94.2 68.4 89.2 75.4 C85.2 81.4 93.2 86.5 87.2 95.5 C72.1 98.5 58 88.5 50 78.4 C42 88.5 27.9 98.5 12.8 95.5 C6.8 86.5 14.8 81.4 10.8 75.4 C5.8 68.4 15.8 63.4 11.8 57.3 C7.8 50.3 16.8 46.3 11.8 40.3 C27.9 38.2 42 48.3 50 58.3 Z M49 54.3 L51 54.3 Q57 54.3 57 60.4 L57 76.4 Q57 82.5 51 82.5 L49 82.5 Q43 82.5 43 76.4 L43 60.4 Q43 54.3 49 54.3 Z' },
  { name: 'Specialty Cheeses', count: 49, path: 'M10.4 69 L66.2 31.2 Q70.7 28.5 74.3 32.1 L88.7 51 Q89.6 52.8 89.6 55.5 L89.6 93.3 Q89.6 96 86.9 96 L13.1 96 Q10.4 96 10.4 93.3 L10.4 69 Z M17.6 70.8 L21.2 73.5 L86 61.8 L86 57.3 L17.6 70.8 Z' },
  { name: 'Salami & Ham', count: 31, path: 'M45.8 29.3 L54.2 29.3 C55 40.9 58.3 49.3 63.3 59.3 C70 71 73.4 82.7 69.2 90.2 Q65 96 50 96 Q35 96 30.8 90.2 C26.6 82.7 30 71 36.7 59.3 C41.7 49.3 45 40.9 45.8 29.3 Z M55 25.9 C55 28.7 52.8 30.9 50 30.9 C47.2 30.9 45 28.7 45 25.9 C45 23.2 47.2 20.9 50 20.9 C52.8 20.9 55 23.2 55 25.9 Z M47.9 21.3 C47.9 23.2 46.4 24.7 44.6 24.7 C42.7 24.7 41.2 23.2 41.2 21.3 C41.2 19.5 42.7 18 44.6 18 C46.4 18 47.9 19.5 47.9 21.3 Z M58.8 21.3 C58.8 23.2 57.3 24.7 55.4 24.7 C53.6 24.7 52.1 23.2 52.1 21.3 C52.1 19.5 53.6 18 55.4 18 C57.3 18 58.8 19.5 58.8 21.3 Z' },
  { name: 'Gifts', count: 22, path: 'M16 30.2 L84 30.2 Q86.1 30.2 86.1 32.3 L86.1 41.9 Q86.1 44 84 44 L16 44 Q13.9 44 13.9 41.9 L13.9 32.3 Q13.9 30.2 16 30.2 Z M21.3 47.2 L78.7 47.2 Q80.8 47.2 80.8 49.3 L80.8 93.9 Q80.8 96 78.7 96 L21.3 96 Q19.2 96 19.2 93.9 L19.2 49.3 Q19.2 47.2 21.3 47.2 Z M48.9 19.6 C48.9 24.8 43.7 29.1 37.3 29.1 C30.8 29.1 25.6 24.8 25.6 19.6 C25.6 14.3 30.8 10 37.3 10 C43.7 10 48.9 14.3 48.9 19.6 Z M74.4 19.6 C74.4 24.8 69.2 29.1 62.7 29.1 C56.3 29.1 51.1 24.8 51.1 19.6 C51.1 14.3 56.3 10 62.7 10 C69.2 10 74.4 14.3 74.4 19.6 Z M55.3 27 C55.3 29.9 52.9 32.3 50 32.3 C47.1 32.3 44.7 29.9 44.7 27 C44.7 24.1 47.1 21.7 50 21.7 C52.9 21.7 55.3 24.1 55.3 27 Z M42 19.6 C42 17.2 39.4 15.3 36.2 15.3 C33 15.3 30.4 17.2 30.4 19.6 C30.4 21.9 33 23.8 36.2 23.8 C39.4 23.8 42 21.9 42 19.6 Z M69.6 19.6 C69.6 17.2 67 15.3 63.8 15.3 C60.6 15.3 58 17.2 58 19.6 C58 21.9 60.6 23.8 63.8 23.8 C67 23.8 69.6 21.9 69.6 19.6 Z' },
  { name: 'Antipasti', count: 45, path: 'M78 56 C78 78.1 65.5 96 50 96 C34.5 96 22 78.1 22 56 C22 33.9 34.5 16 50 16 C65.5 16 78 33.9 78 56 Z M61.5 40.9 L54.5 34 L61.5 27.1 L56.9 22.5 L50 29.5 L43.1 22.5 L38.5 27.1 L45.5 34 L38.5 40.9 L43.1 45.5 L50 38.5 L56.9 45.5 L61.5 40.9 Z' },
  { name: 'Bread & Spreads', count: 60, path: 'M8 86 C4 66 22 52 50 52 C78 52 96 66 92 86 Q90 96 80 96 L20 96 Q10 96 8 86 Z M28.5 58 L32.5 75.5 L36.5 75 L32.5 57.5 L28.5 58 Z M44.5 55 L48.5 72.5 L52.5 72 L48.5 54.5 L44.5 55 Z M60.5 58 L64.5 75.5 L68.5 75 L64.5 57.5 L60.5 58 Z' },
];

// Layout (points of a 393-wide screen)

// Grains

export function makePaths(): SkPath[] {
  return CATEGORIES.map((c) => Skia.Path.MakeFromSVGString(c.path) ?? Skia.Path.Make());
}

/**
 Samples a shape once (JS thread): a jittered 110 × 110 grid kept where the path fills (row by row),
 then 1,400 picks spread evenly through that list. Seeded, so every run gets the same grains.
 */
export function sampleGrains(path: SkPath, index: number): Grains {
  const ix: number[] = [];
  const iy: number[] = [];
  const cell = 100 / GRID;
  for (let row = 0; row < GRID; row += 1) {
    for (let col = 0; col < GRID; col += 1) {
      const n = row * GRID + col + index * 7919;
      const px = (col + 0.15 + 0.7 * hash(n * 0.731)) * cell;
      const py = (row + 0.15 + 0.7 * hash(n * 0.731 + 0.5)) * cell;
      if (path.contains(px, py)) {
        ix.push(px);
        iy.push(py);
      }
    }
  }
  const m = Math.max(ix.length, 1);
  const x: number[] = [];
  const y: number[] = [];
  let top = 100;
  let bottom = 0;
  for (let i = 0; i < GRAIN_COUNT; i += 1) {
    const k = Math.min(m - 1, Math.floor(((i + hash(i * 0.37 + index * 3.1)) * m) / GRAIN_COUNT));
    const gx = ix.length ? ix[k] : 50;
    const gy = iy.length ? iy[k] : 50;
    x.push(gx);
    y.push(gy);
    top = Math.min(top, gy);
    bottom = Math.max(bottom, gy);
  }
  const span = Math.max(bottom - top, 1);
  return { x, y, r: y.map((v) => (v - top) / span), top, bottom };
}

// Static art

/** Paper speckle over the cream, seeded; drawn once (canvas in points). */
export function makePaper(width: number, height: number): SkPicture {
  const recorder = Skia.PictureRecorder();
  const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
  const dark = Skia.Paint();
  dark.setAntiAlias(true);
  dark.setColor(Skia.Color('rgba(22, 19, 15, 0.06)'));
  const light = Skia.Paint();
  light.setAntiAlias(true);
  light.setColor(Skia.Color('rgba(255, 255, 255, 0.5)'));
  for (let i = 0; i < 900; i += 1) {
    const x = hash(i * 1.37 + 11) * width;
    const y = hash(i * 2.11 + 23) * height;
    const r = 0.3 + 0.4 * hash(i * 3.7 + 5);
    canvas.drawCircle(x, y, r, i % 3 === 0 ? light : dark);
  }
  return recorder.finishRecordingAsPicture();
}

export type Fonts = { name: SkFont; count: SkFont };

/** Each label's two lines and their widths (measured once on the JS thread). */
export type Labels = { name: string[]; nameWidth: number[]; count: string[]; countWidth: number[] };

function textWidth(font: SkFont, text: string) {
  return font.getGlyphWidths(font.getGlyphIDs(text)).reduce((sum, n) => sum + n, 0);
}

export function makeLabels(fonts: Fonts): Labels {
  const name = CATEGORIES.map((c) => c.name);
  const count = CATEGORIES.map((c) => `${c.count} products`);
  return {
    name,
    count,
    // Tracking −0.2 pt per glyph, as in the other stacks.
    nameWidth: name.map((t) => textWidth(fonts.name, t) - 0.2 * t.length),
    countWidth: count.map((t) => textWidth(fonts.count, t)),
  };
}

// Drawing (canvas already scaled to 393-wide points)

export type Art = {
  paths: SkPath[];
  grains: Grains[];
  seeds: number[];
  fonts: Fonts | null;
  labels: Labels | null;
};

function fillPaint(color: number, t: number, alpha: number, base: number): SkPaint {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(mixColor(base, color, t, alpha));
  return p;
}

function inHero(canvas: SkCanvas, l: Layout) {
  'worklet';
  canvas.save();
  canvas.translate(196.5 - 50 * HERO_SCALE, l.baseline - 96 * HERO_SCALE);
  canvas.scale(HERO_SCALE, HERO_SCALE);
}

function drawTabs(canvas: SkCanvas, l: Layout, f: Frame, still: boolean, art: Art) {
  'worklet';
  const change = f.fromTab !== f.to;
  const swap = still ? seg(f.p, 0, 0.3) : easeOutCubic(seg(f.p, 0, 0.25));
  for (let i = 0; i < art.paths.length; i += 1) {
    let amount = 0;
    if (i === f.to) amount = change ? swap : 1;
    if (change && i === f.fromTab) amount = 1 - swap;
    const pop = !still && change && i === f.to ? 1 + 0.14 * Math.sin(Math.PI * seg(f.p, 0, 0.35)) : 1;
    const s = ICON_SCALE * pop;
    canvas.save();
    canvas.translate(tabX(i), tabY(l, i));
    canvas.scale(s, s);
    canvas.translate(-50, -50);
    canvas.drawPath(art.paths[i], fillPaint(INK, amount, 1, IDLE));
    canvas.restore();
  }
  // The indicator: slides within a row, hands over between rows.
  const ink = fillPaint(INK, 1, 1, INK);
  const ax = tabX(f.fromTab);
  const ay = tabY(l, f.fromTab);
  const bx = tabX(f.to);
  const by = tabY(l, f.to);
  const dot = (x: number, y: number, scale: number) => {
    if (scale > 0.01) canvas.drawCircle(x, y + 25, 2 * scale, ink);
  };
  if (!change) {
    dot(bx, by, 1);
  } else if (still) {
    dot(ax, ay, 1 - swap);
    dot(bx, by, swap);
  } else if (ay === by) {
    const e = easeInOutCubic(seg(f.p, 0, 0.45));
    dot(ax + (bx - ax) * e, by, 1);
  } else {
    dot(ax, ay, 1 - easeInCubic(seg(f.p, 0, 0.22)));
    dot(bx, by, easeOutCubic(seg(f.p, 0.22, 0.45)));
  }
}

/** A silhouette at rest (reduced motion): its fill and its grains. */
function drawRest(canvas: SkCanvas, l: Layout, shape: number, alpha: number, art: Art) {
  'worklet';
  if (alpha <= 0.001) return;
  const paint = fillPaint(INK, 1, alpha, INK);
  inHero(canvas, l);
  canvas.drawPath(art.paths[shape], paint);
  canvas.restore();
  const g = art.grains[shape];
  for (let i = 0; i < GRAIN_COUNT; i += 1) canvas.drawCircle(heroX(g.x[i]), heroY(l, g.y[i]), 1.9, paint);
}

function drawHero(canvas: SkCanvas, l: Layout, f: Frame, art: Art, xs: number[], ys: number[], fly: number[]) {
  'worklet';
  const calm = 1 - stirEnergy(f.stirs, f.clock);
  // The solid fill: drains bottom-up from the old shape, fills bottom-up into the new one.
  if (calm > 0.01) {
    const paint = fillPaint(INK, 1, calm, INK);
    if (f.p >= SIFT) {
      inHero(canvas, l);
      canvas.drawPath(art.paths[f.to], paint);
      canvas.restore();
    } else {
      if (f.from >= 0) {
        const g = art.grains[f.from];
        const r = 1 - (f.p - 0.05) / 0.36 - 0.03;
        if (r > 0) {
          const y = heroY(l, g.top + r * (g.bottom - g.top));
          canvas.save();
          canvas.clipRect(Skia.XYWHRect(0, -2000, 393, y + 2000), ClipOp.Intersect, true);
          inHero(canvas, l);
          canvas.drawPath(art.paths[f.from], paint);
          canvas.restore();
          canvas.restore();
        }
      }
      const g = art.grains[f.to];
      const r = 1 - (f.p - 1.04) / 0.4 + 0.08;
      if (r < 1) {
        const y = heroY(l, g.top + Math.max(r, -1) * (g.bottom - g.top));
        canvas.save();
        canvas.clipRect(Skia.XYWHRect(0, y, 393, 4000), ClipOp.Intersect, true);
        inHero(canvas, l);
        canvas.drawPath(art.paths[f.to], paint);
        canvas.restore();
        canvas.restore();
      }
    }
  }
  // The grains.
  positions(f, l, art.grains, art.seeds, xs, ys, fly);
  const ink = fillPaint(INK, 1, 1, INK);
  for (let i = 0; i < GRAIN_COUNT; i += 1) canvas.drawCircle(xs[i], ys[i], 1.9 * (1 - 0.3 * fly[i]), ink);
}

function drawLabels(canvas: SkCanvas, l: Layout, f: Frame, still: boolean, art: Art) {
  'worklet';
  const fonts = art.fonts;
  const labels = art.labels;
  if (!fonts || !labels) return;
  const nameMetrics = fonts.name.getMetrics();
  const countMetrics = fonts.count.getMetrics();
  const label = (i: number, alpha: number, dy: number) => {
    if (alpha <= 0.001) return;
    // Text is placed by its visual centre, as SwiftUI's centred anchor does.
    const nameBase = l.nameY + dy - (nameMetrics.ascent + nameMetrics.descent) / 2;
    const ink = fillPaint(INK, 1, alpha, INK);
    let x = 196.5 - labels.nameWidth[i] / 2;
    const ids = fonts.name.getGlyphIDs(labels.name[i]);
    const widths = fonts.name.getGlyphWidths(ids);
    for (let k = 0; k < ids.length; k += 1) {
      canvas.drawText(labels.name[i].charAt(k), x, nameBase, ink, fonts.name);
      x += widths[k] - 0.2;
    }
    const countBase = l.countY + dy - (countMetrics.ascent + countMetrics.descent) / 2;
    canvas.drawText(labels.count[i], 196.5 - labels.countWidth[i] / 2, countBase, fillPaint(MUTED, 1, alpha, MUTED), fonts.count);
  };
  if (f.fromTab === f.to) {
    label(f.to, 1, 0);
  } else if (still) {
    const q = seg(f.p, 0, 0.3);
    label(f.fromTab, 1 - q, 0);
    label(f.to, q, 0);
  } else {
    const out = easeInCubic(seg(f.p, 0, 0.3));
    const into = easeOutCubic(seg(f.p, 1.05, 1.4));
    label(f.fromTab, 1 - out, 6 * out);
    label(f.to, into, 6 * (1 - into));
  }
}

export function drawScene(canvas: SkCanvas, l: Layout, f: Frame, still: boolean, art: Art, xs: number[], ys: number[], fly: number[]) {
  'worklet';
  drawTabs(canvas, l, f, still, art);
  if (still) {
    const q = easeOutCubic(seg(f.p, 0, 0.3));
    if (q < 1) drawRest(canvas, l, f.fromTab, 1 - q, art);
    drawRest(canvas, l, f.to, q, art);
  } else {
    drawHero(canvas, l, f, art, xs, ys, fly);
  }
  drawLabels(canvas, l, f, still, art);
}
