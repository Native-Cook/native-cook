import {
  ClipOp,
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
  type SkPicture,
} from '@shopify/react-native-skia';

import {
  PAGES,
  clamp,
  copyIn,
  hash,
  ingredientIn,
  jarIn,
  titleIn,
  titleSpeed,
  trioJarIn,
  wave,
  type Scene,
} from './wipeMotion';

/* Pages, layout and drawing of Pickle Flavour Wipe, ported from PickleFlavourWipeArt.swift. */

export const JAR_IMAGES = [require('./assets/jar-mango.webp'), require('./assets/jar-lime.webp'), require('./assets/jar-garlic.webp')];
export const FOOD_IMAGES = [require('./assets/carrot.webp'), require('./assets/lime.webp'), require('./assets/garlic.webp')];

const WIDTH = 393;
const CREAM = 0xfff4e0;
const GOLD = 0xffc93c;
const LEAF_LIGHT = 0x6bbf4a;
const LEAF_DARK = 0x2e7a2c;
const WHITE = 0xffffff;
const BLACK = 0x000000;

export function hexColor(hex: number, alpha?: number) {
  'worklet';
  const a = alpha === undefined ? 1 : Math.min(Math.max(alpha, 0), 1);
  const r = (hex >> 16) & 0xff;
  const g = (hex >> 8) & 0xff;
  const b = hex & 0xff;
  return `rgba(${r}, ${g}, ${b}, ${a})`;
}

function skColor(hex: number, alpha: number) {
  'worklet';
  return Skia.Color(hexColor(hex, alpha));
}

function freshPaint() {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  return p;
}

// Pages

const CARROT = 0;
const LIME = 1;
const GARLIC = 2;

/** An ingredient around the jar: offset from the jar's centre (pt), drawn width, turn (degrees). */
type Item = { food: number; x: number; y: number; w: number; turn: number; flip: boolean };
/** A small leaf drawn in code, placed like an item. */
type Leaf = { x: number; y: number; len: number; turn: number };

export type Page = {
  title: string;
  subtitle: string;
  line: string;
  price: string;
  weight: string;
  cta: string;
  top: number;
  mid: number;
  low: number;
  cut: number;
  jar: number; // index into the jar images; −1 for the line-up page
  pickle: string; // the label's last line
  items: Item[];
  leaves: Leaf[];
};

function item(food: number, x: number, y: number, w: number, turn: number, flip?: boolean): Item {
  return { food, x, y, w, turn, flip: flip === true };
}

function leaf(x: number, y: number, len: number, turn: number): Leaf {
  return { x, y, len, turn };
}

export const PAGE_LIST: Page[] = [
  {
    title: 'Mango',
    subtitle: 'Mixed Pickle',
    line: 'Green mango, carrot & lime in mustard oil',
    price: '$6.90',
    weight: '350 g jar',
    cta: 'Add to basket',
    top: 0x8a3bc4,
    mid: 0x6e22a8,
    low: 0x4e1588,
    cut: 0xd2a6f5,
    jar: 0,
    pickle: 'MIXED PICKLE',
    items: [item(CARROT, -118, 40, 172, -18), item(LIME, 124, 72, 146, 8)],
    leaves: [leaf(-150, -46, 34, -38), leaf(146, -30, 30, 42), leaf(-46, 150, 28, 196)],
  },
  {
    title: 'Lime',
    subtitle: 'Chilli Pickle',
    line: 'Sun-cured lime & green chilli, nice and sharp',
    price: '$6.50',
    weight: '350 g jar',
    cta: 'Add to basket',
    top: 0xea3b4c,
    mid: 0xd21f35,
    low: 0xa3112b,
    cut: 0xffb0b8,
    jar: 1,
    pickle: 'CHILLI PICKLE',
    items: [item(LIME, -122, 66, 154, -10), item(CARROT, 120, 38, 164, 18, true)],
    leaves: [leaf(-146, -40, 32, -30), leaf(150, -44, 34, 50), leaf(58, 152, 26, 160)],
  },
  {
    title: 'Garlic',
    subtitle: 'Whole Clove Pickle',
    line: 'Slow-pickled cloves with red chilli & fennel',
    price: '$7.20',
    weight: '350 g jar',
    cta: 'Add to basket',
    top: 0x5daae8,
    mid: 0x3d8fd6,
    low: 0x2869b3,
    cut: 0xbfe2ff,
    jar: 2,
    pickle: 'GARLIC PICKLE',
    items: [item(GARLIC, -120, 68, 156, -8), item(GARLIC, 128, 88, 108, 20, true)],
    leaves: [leaf(-148, -36, 30, -42), leaf(144, -26, 32, 36), leaf(-60, 152, 26, 200)],
  },
  {
    title: 'Trio',
    subtitle: 'Tasting Box',
    line: 'Order your favourites now!',
    price: '$18.50',
    weight: '3 × 350 g',
    cta: 'Add the box',
    top: 0x35a36b,
    mid: 0x1f8050,
    low: 0x125f3a,
    cut: 0xa5ebc4,
    jar: -1,
    pickle: '',
    items: [item(CARROT, -112, 156, 150, -22), item(LIME, 18, 178, 100, 12), item(GARLIC, 132, 156, 100, -10)],
    leaves: [leaf(-156, -120, 30, -30), leaf(158, -108, 28, 60), leaf(-40, 96, 24, 170)],
  },
];

// Layout

export type Layout = {
  height: number;
  top: number;
  barY: number;
  titleY: number;
  jarY: number;
  ctaTop: number;
  lineY: number;
  dotsY: number;
};

export function makeLayout(height: number, top: number, bottom: number): Layout {
  const ctaTop = height - bottom - 16 - 56;
  return { height, top, barY: top + 26, titleY: top + 150, jarY: top + 366, ctaTop, lineY: ctaTop - 64, dotsY: ctaTop - 30 };
}

const JAR_H = 272;
const TRIO_H = 140;
const TRIO_GAP = 118;
const PATTERN_W = 440;
const PATTERN_H = 1000;

// Text (measured per glyph, so tracking and centring match SwiftUI's Text)

export type Fonts = {
  title: SkFont;
  mark: SkFont;
  labelMark: SkFont;
  labelSerif: SkFont;
  labelSmall: SkFont;
  sub: SkFont;
  line: SkFont;
  lineBold: SkFont;
  price: SkFont;
  weight: SkFont;
  cta: SkFont;
};

function textWidth(font: SkFont, s: string, tracking: number) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(s));
  let total = 0;
  for (let i = 0; i < widths.length; i++) total += widths[i] + (i < widths.length - 1 ? tracking : 0);
  return total;
}

/** Draws `s` with its line box centred on `cy`; `align` 0 = left at x, 0.5 = centred, 1 = right. */
function drawText(c: SkCanvas, font: SkFont, s: string, x: number, cy: number, hex: number, alpha: number, tracking: number, align: number) {
  'worklet';
  const paint = freshPaint();
  paint.setColor(skColor(hex, alpha));
  const ids = font.getGlyphIDs(s);
  const widths = font.getGlyphWidths(ids);
  let total = 0;
  for (let i = 0; i < widths.length; i++) total += widths[i] + (i < widths.length - 1 ? tracking : 0);
  const m = font.getMetrics();
  const baseline = cy - (m.ascent + m.descent) / 2;
  let pen = x - total * align;
  const positions = [];
  for (let i = 0; i < ids.length; i++) {
    positions.push(Skia.Point(pen, 0));
    pen += widths[i] + tracking;
  }
  c.drawGlyphs(ids, positions, 0, baseline, font, paint);
}

function drawImage(c: SkCanvas, img: SkImage, x: number, y: number, w: number, h: number, alpha: number) {
  'worklet';
  const p = freshPaint();
  p.setAlphaf(alpha);
  c.drawImageRectOptions(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(x, y, w, h), FilterMode.Linear, MipmapMode.Linear, p);
}

// Artwork (recorded once)

/** Where each jar's blank label band sits, as a share of the jar's height (centre). */
const LABEL_CENTRE = [0.597, 0.558, 0.625];

export type JarArt = { picture: SkPicture; aspect: number };

/** Each jar with its label printed once, so a frame draws one picture. */
export function makeJar(jar: SkImage, index: number, fonts: Fonts): JarArt {
  const aspect = jar.width() / jar.height();
  const h = JAR_H;
  const w = h * aspect;
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, w, h));
  drawImage(c, jar, 0, 0, w, h, 1);
  const page = PAGE_LIST[index];
  const cx = w / 2;
  const cy = h * LABEL_CENTRE[index];
  drawText(c, fonts.labelMark, 'jarful', cx, cy - 23, CREAM, 1, 0, 0.5);
  const rule = freshPaint();
  rule.setStyle(PaintStyle.Stroke);
  rule.setStrokeWidth(0.8);
  rule.setColor(skColor(GOLD, 0.9));
  c.drawLine(cx - 16, cy - 12, cx + 16, cy - 12, rule);
  drawText(c, fonts.labelSerif, page.title, cx, cy + 5, CREAM, 1, 0, 0.5);
  drawText(c, fonts.labelSmall, page.pickle, cx, cy + 25, GOLD, 1, 1.6, 0.5);
  return { picture: recorder.finishRecordingAsPicture(), aspect };
}

/** The backdrop's tonal marks (leaves and chilli curves on a jittered grid), 440 × 1000 pt. */
export function makePattern(): SkPicture {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, PATTERN_W, PATTERN_H));
  const leafPath = Skia.PathBuilder.Make().moveTo(-15, 0).quadTo(0, -13, 15, 0).quadTo(0, 13, -15, 0).close().detach();
  const arc = Skia.PathBuilder.Make()
    .addArc(Skia.XYWHRect(-14, -14, 28, 28), (-2.2 * 180) / Math.PI, (1.7 * 180) / Math.PI)
    .detach();
  const step = 58;
  for (let row = 0; row < 18; row++) {
    for (let col = 0; col < 8; col++) {
      const seed = row * 31 + col * 7 + 0.5;
      const x = col * step + (row % 2 === 0 ? 0 : step / 2) + (hash(seed) - 0.5) * 22;
      const y = row * step + (hash(seed + 1.3) - 0.5) * 22;
      const turn = hash(seed + 2.1) * 360;
      const light = hash(seed + 3.7) > 0.62;
      const p = freshPaint();
      p.setColor(light ? skColor(WHITE, 0.07) : skColor(BLACK, 0.085));
      c.save();
      c.translate(x, y);
      c.rotate(turn, 0, 0);
      if (hash(seed + 5.9) > 0.5) {
        c.drawPath(leafPath, p);
      } else {
        // A chilli curve: a thick arc with round ends.
        p.setStyle(PaintStyle.Stroke);
        p.setStrokeWidth(8);
        p.setStrokeCap(StrokeCap.Round);
        c.drawPath(arc, p);
      }
      c.restore();
    }
  }
  return recorder.finishRecordingAsPicture();
}

/** Each page's backdrop gradient, recorded once. */
export function makeBackdrops(l: Layout): SkPicture[] {
  const out: SkPicture[] = [];
  for (let i = 0; i < PAGES; i++) {
    const page = PAGE_LIST[i];
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(-2, -2, WIDTH + 4, l.height + 4));
    const p = freshPaint();
    p.setShader(
      Skia.Shader.MakeLinearGradient(
        Skia.Point(0, 0),
        Skia.Point(0, l.height),
        [skColor(page.top, 1), skColor(page.mid, 1), skColor(page.low, 1)],
        [0, 0.46, 1],
        TileMode.Clamp,
      ),
    );
    c.drawRect(Skia.XYWHRect(-2, -2, WIDTH + 4, l.height + 4), p);
    out.push(recorder.finishRecordingAsPicture());
  }
  return out;
}

/** Each page's price and Add button, recorded once. */
export function makeCtas(l: Layout, fonts: Fonts): SkPicture[] {
  const out: SkPicture[] = [];
  for (let i = 0; i < PAGES; i++) {
    const page = PAGE_LIST[i];
    const recorder = Skia.PictureRecorder();
    const c = recorder.beginRecording(Skia.XYWHRect(0, l.ctaTop - 4, WIDTH, 70));
    drawText(c, fonts.price, page.price, 24, l.ctaTop + 19, WHITE, 1, 0, 0);
    drawText(c, fonts.weight, page.weight, 24, l.ctaTop + 43, CREAM, 0.72, 0, 0);
    let p = freshPaint();
    p.setColor(skColor(BLACK, 0.14));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(179, l.ctaTop + 3, 190, 56), 28, 28), p);
    p = freshPaint();
    p.setColor(skColor(CREAM, 1));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(179, l.ctaTop, 190, 56), 28, 28), p);
    drawText(c, fonts.cta, page.cta, 274, l.ctaTop + 28, page.low, 1, 0, 0.5);
    out.push(recorder.finishRecordingAsPicture());
  }
  return out;
}

export type Art = {
  jars: (JarArt | null)[];
  foods: (SkImage | null)[];
  pattern: SkPicture;
  backdrops: SkPicture[];
  ctas: SkPicture[];
  fonts: Fonts;
};

// Page

function drawShadow(c: SkCanvas, x: number, y: number, w: number, h: number, alpha: number) {
  'worklet';
  if (alpha <= 0.005) return;
  c.save();
  c.translate(x, y);
  c.scale(1, h / w);
  const p = freshPaint();
  p.setShader(Skia.Shader.MakeRadialGradient(Skia.Point(0, 0), w / 2, [skColor(BLACK, alpha), skColor(BLACK, 0)], [0, 1], TileMode.Clamp));
  c.drawCircle(0, 0, w / 2, p);
  c.restore();
}

function drawItem(c: SkCanvas, art: Art, it: Item, i: number, jx: number, jy: number, age: number, a: number) {
  'worklet';
  const v = ingredientIn(age, i);
  if (v <= 0.001) return;
  const img = art.foods[it.food];
  if (!img) return;
  const h = (it.w * img.height()) / img.width();
  const x = jx + it.x * v;
  const y = jy + it.y * v + 5 * wave(a, 5, 1.3 + i * 2.1);
  const turn = it.turn - 30 * (1 - v) + 3 * wave(a, 4, i * 1.7);
  const scale = 0.2 + 0.8 * v;
  c.save();
  c.translate(x, y);
  c.rotate(turn, 0, 0);
  c.scale(it.flip ? -scale : scale, scale);
  drawImage(c, img, -it.w / 2, -h / 2, it.w, h, clamp(v * 3, 0, 1));
  c.restore();
}

function drawLeaf(c: SkCanvas, lf: Leaf, i: number, jx: number, jy: number, age: number, a: number) {
  'worklet';
  const v = ingredientIn(age, i + 2);
  if (v <= 0.001) return;
  const alpha = clamp(v * 3, 0, 1);
  c.save();
  c.translate(jx + lf.x * v, jy + lf.y * v + 6 * wave(a, 5, 2.4 + i * 1.9));
  c.rotate(lf.turn + 40 * (1 - v) + 8 * wave(a, 3, i * 2.3), 0, 0);
  const s = lf.len * (0.3 + 0.7 * v);
  const path = Skia.PathBuilder.Make()
    .moveTo(-s / 2, 0)
    .quadTo(-s * 0.05, -s * 0.46, s / 2, 0)
    .quadTo(s * 0.05, s * 0.4, -s / 2, 0)
    .close()
    .detach();
  const p = freshPaint();
  p.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(0, -s * 0.25), Skia.Point(0, s * 0.2), [skColor(LEAF_LIGHT, 1), skColor(LEAF_DARK, 1)], [0, 1], TileMode.Clamp),
  );
  p.setAlphaf(alpha);
  c.drawPath(path, p);
  const rib = Skia.PathBuilder.Make().moveTo(-s * 0.45, 0).quadTo(0, -s * 0.08, s * 0.42, -s * 0.02).detach();
  const rp = freshPaint();
  rp.setStyle(PaintStyle.Stroke);
  rp.setStrokeWidth(0.9);
  rp.setColor(skColor(0x1f5a1e, 0.55 * alpha));
  c.drawPath(rib, rp);
  c.restore();
}

function drawTitle(c: SkCanvas, l: Layout, fonts: Fonts, page: Page, age: number) {
  'worklet';
  const v = titleIn(age);
  const width = textWidth(fonts.title, page.title, 0);
  const fit = Math.min(1, 330 / Math.max(width, 1));
  const speed = titleSpeed(age) * 300; // pt/s
  c.save();
  c.translate(196 - 300 * (1 - v), l.titleY);
  c.rotate(-7, 0, 0);
  c.save();
  c.scale(fit, fit);
  // A soft drop shadow, then the smear ghosts trailing behind, then the title.
  drawText(c, fonts.title, page.title, 0, 4, BLACK, 0.16 * clamp(v * 2, 0, 1), 0, 0.5);
  if (speed > 40) {
    const ghosts = [0.32, 0.2, 0.12, 0.06];
    for (let k = 1; k <= 4; k++) {
      drawText(c, fonts.title, page.title, (-speed * 0.011 * k) / fit, 0, WHITE, ghosts[k - 1] * clamp(speed / 600, 0, 1), 0, 0.5);
    }
  }
  drawText(c, fonts.title, page.title, 0, 0, WHITE, 1, 0, 0.5);
  c.restore();
  const copy = copyIn(age);
  if (copy > 0.001) {
    drawText(c, fonts.sub, page.subtitle, (width * fit) / 2 - 4, 60 * fit + 8 * (1 - copy), CREAM, 0.92 * copy, 0, 1);
  }
  c.restore();
}

function drawLine(c: SkCanvas, l: Layout, fonts: Fonts, page: Page, age: number) {
  'worklet';
  const copy = copyIn(age);
  if (copy <= 0.001) return;
  const y = l.lineY + 10 * (1 - copy);
  if (page.jar < 0) {
    const a = 'Order your ';
    const b = 'favourites';
    const e = ' now!';
    const wa = textWidth(fonts.lineBold, a, 0);
    const wb = textWidth(fonts.lineBold, b, 0);
    const we = textWidth(fonts.lineBold, e, 0);
    let x = 196 - (wa + wb + we) / 2;
    drawText(c, fonts.lineBold, a, x, y, WHITE, copy, 0, 0);
    x += wa;
    drawText(c, fonts.lineBold, b, x, y, GOLD, copy, 0, 0);
    x += wb;
    drawText(c, fonts.lineBold, e, x, y, WHITE, copy, 0, 0);
  } else {
    drawText(c, fonts.line, page.line, 196, y, CREAM, 0.88 * copy, 0, 0.5);
  }
}

function drawPage(c: SkCanvas, l: Layout, art: Art, index: number, age: number, push: number, bgPush: number, a: number, alpha: number) {
  'worklet';
  if (alpha <= 0.001) return;
  const page = PAGE_LIST[index];
  const layered = alpha < 0.999;
  if (layered) {
    const lp = Skia.Paint();
    lp.setAlphaf(alpha);
    c.saveLayer(lp);
  }
  c.drawPicture(art.backdrops[index]);
  c.save();
  c.translate(-24 + bgPush, 0);
  c.drawPicture(art.pattern);
  c.restore();
  // A soft light behind the hero.
  const gx = 196 + push;
  const gy = l.jarY - 10;
  const glow = freshPaint();
  glow.setShader(Skia.Shader.MakeRadialGradient(Skia.Point(gx, gy), 230, [skColor(WHITE, 0.17), skColor(WHITE, 0)], [0, 1], TileMode.Clamp));
  c.drawCircle(gx, gy, 230, glow);

  c.save();
  c.translate(push, 0);
  const jx = 196;
  const jy = l.jarY;
  if (page.jar >= 0) {
    // Items behind the jar, the jar's shadow, the jar, then the leaves in front.
    for (let i = 0; i < page.items.length; i++) drawItem(c, art, page.items[i], i, jx, jy, age, a);
    const jar = art.jars[page.jar];
    if (jar) {
      const v = jarIn(age);
      const dy = 300 * (1 - v) + 3 * wave(a, 5, 0.4);
      const turn = -6 + 20 * (1 - v) + 0.8 * wave(a, 4, 1.1);
      const scale = 0.9 + 0.1 * v;
      const w = JAR_H * jar.aspect;
      drawShadow(c, jx + 10, jy + JAR_H * 0.43 + dy, 230, 66, 0.3 * clamp(v, 0, 1));
      c.save();
      c.translate(jx, jy + dy);
      c.rotate(turn, 0, 0);
      c.scale(scale, scale);
      c.translate(-w / 2, -JAR_H / 2);
      c.drawPicture(jar.picture);
      c.restore();
    }
  } else {
    for (let i = 0; i < page.items.length; i++) drawItem(c, art, page.items[i], i, jx, jy, age, a);
    // The line-up: left, right, then the centre jar in front.
    const order = [0, 2, 1];
    const jarOf = [2, 1, 0];
    for (let n = 0; n < 3; n++) {
      const j = order[n];
      const jar = art.jars[jarOf[j]];
      if (!jar) continue;
      const v = trioJarIn(age, j);
      const dx = (j - 1) * TRIO_GAP;
      const dy = 260 * (1 - v) + 2.5 * wave(a, 5, j * 0.9);
      const turn = (j - 1) * 4 + 16 * (1 - v);
      const w = TRIO_H * jar.aspect;
      drawShadow(c, jx + dx + 6, jy - 14 + TRIO_H * 0.44 + dy, 140, 40, 0.28 * clamp(v, 0, 1));
      c.save();
      c.translate(jx + dx, jy - 14 + dy);
      c.rotate(turn, 0, 0);
      const s = TRIO_H / JAR_H;
      c.scale(s, s);
      c.translate(-w / s / 2, -JAR_H / 2);
      c.drawPicture(jar.picture);
      c.restore();
    }
  }
  for (let i = 0; i < page.leaves.length; i++) drawLeaf(c, page.leaves[i], i, jx, jy, age, a);

  drawTitle(c, l, art.fonts, page, age);
  drawLine(c, l, art.fonts, page, age);
  c.drawPicture(art.ctas[index]);
  c.restore();
  if (layered) c.restore();
}

// Scene

/** The slash: a straight "/" edge at 64° that sweeps across the screen. */
function edgeOf(l: Layout, r: number, dir: number) {
  'worklet';
  const w = WIDTH;
  const h = l.height;
  const s = h / 2 / Math.tan((64 * Math.PI) / 180);
  const travel = w + 2 * s + 48;
  const ex = dir > 0 ? w + s + 24 - r * travel : -s - 24 + r * travel;
  const topX = ex + s;
  const topY = -2;
  const bottomX = ex - s;
  const bottomY = h + 2;
  const len = Math.hypot(2 * s, h);
  const b = Skia.PathBuilder.Make();
  if (dir > 0) {
    const right = Math.max(w, topX) + 60;
    b.moveTo(topX, topY).lineTo(right, topY).lineTo(right, bottomY).lineTo(bottomX, bottomY);
  } else {
    const left = Math.min(0, bottomX) - 60;
    b.moveTo(left, topY).lineTo(topX, topY).lineTo(bottomX, bottomY).lineTo(left, bottomY);
  }
  b.close();
  return { topX, topY, bottomX, bottomY, nx: h / len, ny: (2 * s) / len, region: b.detach() };
}

/** The page `from`, then page `to` revealed by the slash edge (or cross-faded under reduced motion). */
export function drawScene(c: SkCanvas, l: Layout, art: Art, scene: Scene, a: number) {
  'worklet';
  const r = scene.reveal;
  if (scene.fade >= 0) {
    drawPage(c, l, art, scene.from, scene.fromAge, 0, 0, a, 1);
    drawPage(c, l, art, scene.to, scene.toAge, 0, 0, a, scene.fade);
    return;
  }
  if (scene.from === scene.to || r <= 0) {
    drawPage(c, l, art, scene.from, scene.fromAge, 0, 0, a, 1);
    return;
  }
  const dir = scene.dir;
  const cr = Math.min(Math.max(r, 0), 1.2);
  drawPage(c, l, art, scene.from, scene.fromAge, -36 * dir * cr, -14 * dir * cr, a, 1);

  const e = edgeOf(l, r, dir);
  // The new page casts a soft shadow onto the old one.
  const nx = e.nx * -dir;
  const ny = e.ny * -dir;
  const shadow = Skia.PathBuilder.Make()
    .moveTo(e.topX, e.topY)
    .lineTo(e.bottomX, e.bottomY)
    .lineTo(e.bottomX + nx * 30, e.bottomY + ny * 30)
    .lineTo(e.topX + nx * 30, e.topY + ny * 30)
    .close()
    .detach();
  const midX = (e.topX + e.bottomX) / 2;
  const midY = (e.topY + e.bottomY) / 2;
  let p = freshPaint();
  p.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(midX, midY), Skia.Point(midX + nx * 30, midY + ny * 30), [skColor(BLACK, 0.26), skColor(BLACK, 0)], [0, 1], TileMode.Clamp),
  );
  c.drawPath(shadow, p);

  c.save();
  c.clipPath(e.region, ClipOp.Intersect, true);
  const rest = 1 - Math.min(Math.max(r, 0), 1);
  drawPage(c, l, art, scene.to, scene.toAge, 24 * dir * rest, 14 * dir * rest, a, 1);
  // A sheen just inside the new page.
  p = freshPaint();
  p.setShader(
    Skia.Shader.MakeLinearGradient(Skia.Point(midX, midY), Skia.Point(midX - nx * 46, midY - ny * 46), [skColor(WHITE, 0.16), skColor(WHITE, 0)], [0, 1], TileMode.Clamp),
  );
  c.drawPath(e.region, p);
  c.restore();

  // The cut line itself.
  p = freshPaint();
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(3.2);
  p.setColor(skColor(PAGE_LIST[scene.to].cut, 1));
  c.drawLine(e.topX, e.topY, e.bottomX, e.bottomY, p);
  p.setStrokeWidth(1);
  p.setColor(skColor(WHITE, 0.75));
  c.drawLine(e.topX, e.topY, e.bottomX, e.bottomY, p);
}

// Chrome (not wiped)

/** Menu, wordmark and bag, recorded once. */
export function makeBar(l: Layout, fonts: Fonts): SkPicture {
  const recorder = Skia.PictureRecorder();
  const c = recorder.beginRecording(Skia.XYWHRect(0, 0, WIDTH, l.barY + 30));
  const y = l.barY;
  let p = freshPaint();
  p.setColor(skColor(WHITE, 1));
  for (let i = 0; i < 3; i++) {
    const w = i === 1 ? 12 : 18;
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(24, y - 6.5 + i * 5.5, w, 2.2), 1.1, 1.1), p);
  }
  const markW = textWidth(fonts.mark, 'jarful', 0);
  drawText(c, fonts.mark, 'jarful', 196 - 5, y, WHITE, 1, 0, 0.5);
  p = freshPaint();
  p.setColor(skColor(GOLD, 1));
  c.drawCircle(196 - 5 + markW / 2 + 3 + 3.5, y + 6.5, 3.5, p);

  const bx = 349;
  p = freshPaint();
  p.setColor(skColor(WHITE, 0.16));
  c.drawCircle(bx, y, 20, p);
  const bag = Skia.PathBuilder.Make()
    .addRRect(Skia.RRectXY(Skia.XYWHRect(bx - 7.5, y - 4, 15, 12.5), 2.5, 2.5))
    .moveTo(bx - 4, y - 4)
    .cubicTo(bx - 4, y - 10.5, bx + 4, y - 10.5, bx + 4, y - 4)
    .detach();
  p = freshPaint();
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(1.7);
  p.setStrokeCap(StrokeCap.Round);
  p.setStrokeJoin(StrokeJoin.Round);
  p.setColor(skColor(WHITE, 1));
  c.drawPath(bag, p);
  return recorder.finishRecordingAsPicture();
}

/** Page dots: the active capsule slides with the edge. */
export function drawDots(c: SkCanvas, l: Layout, scene: Scene) {
  'worklet';
  const r = scene.fade >= 0 ? scene.fade : clamp(scene.reveal, 0, 1);
  const gap = 16;
  const wide = 22;
  const total = (PAGES - 1) * gap + wide;
  let x = 196 - total / 2;
  for (let i = 0; i < PAGES; i++) {
    const share = (i === scene.from ? 1 - r : 0) + (i === scene.to ? r : 0);
    const w = 8 + (wide - 8) * share;
    const p = freshPaint();
    p.setColor(skColor(WHITE, 0.4 + 0.55 * share));
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(x, l.dotsY - 4, w, 8), 4, 4), p);
    x += w + gap - 8;
  }
}
